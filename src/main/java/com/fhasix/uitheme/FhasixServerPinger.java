package com.fhasix.uitheme;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.InitialDirContext;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Hashtable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * Self-contained Minecraft "Server List Ping" client used by the Discover cards.
 *
 * Why this exists instead of vanilla's MultiplayerServerListPinger: that class was the one part
 * of Discover that depended on multiplayer-networking internals whose behaviour/signatures moved
 * between versions, and when it failed the card could only say "offline" without any reason.
 * The status protocol itself is tiny and stable, so this speaks it directly over a plain socket:
 *
 *   handshake (next state = status)  ->  status request  ->  status JSON (players, favicon)
 *   then ping(long) -> pong(long)    ->  round-trip time = the number shown on the card
 *
 * No Minecraft classes are touched (only java.net, Gson which Minecraft already ships, and the
 * JDK's JNDI DNS provider for SRV records - the same one vanilla uses, wrapped in try/catch so
 * runtimes without it, e.g. some Android JVMs, simply connect to the typed host directly).
 * Everything runs on a small daemon pool, never on the render thread.
 */
public final class FhasixServerPinger {

    /** What one ping attempt found out about a server. */
    public static final class Result {
        public final boolean online;
        public final int pingMs;
        public final int playersOnline;
        public final int playersMax;
        /** Raw PNG bytes of the server icon (64x64), or null. */
        public final byte[] favicon;

        Result(boolean online, int pingMs, int playersOnline, int playersMax, byte[] favicon) {
            this.online = online;
            this.pingMs = pingMs;
            this.playersOnline = playersOnline;
            this.playersMax = playersMax;
            this.favicon = favicon;
        }

        static final Result OFFLINE = new Result(false, -1, 0, 0, null);
    }

    private static final int DEFAULT_PORT = 25565;
    /** Sent in the handshake; servers answer status pings for any protocol number. */
    private static final int PROTOCOL_VERSION = 774;
    private static final int CONNECT_TIMEOUT_MS = 4000;
    private static final int READ_TIMEOUT_MS = 4000;
    private static final int MAX_STRING_BYTES = 1 << 20;

    private static final ExecutorService POOL = Executors.newFixedThreadPool(6, r -> {
        Thread t = new Thread(r, "fhasix-server-ping");
        t.setDaemon(true);
        return t;
    });

    private FhasixServerPinger() {
    }

    /** Pings on the background pool and hands the result to the callback (on a pool thread). */
    public static void pingAsync(String address, Consumer<Result> callback) {
        POOL.execute(() -> {
            Result result;
            try {
                result = ping(address);
            } catch (Throwable t) {
                result = Result.OFFLINE;
            }
            try {
                callback.accept(result);
            } catch (Throwable ignored) {
                // a failing callback must never kill the pool thread
            }
        });
    }

    /** Blocking ping. Never throws: any failure is reported as an offline Result. */
    public static Result ping(String address) {
        String host = address.trim();
        int port = DEFAULT_PORT;
        int colon = host.lastIndexOf(':');
        if (colon > 0 && host.indexOf(':') == colon && colon < host.length() - 1) {
            try {
                port = Integer.parseInt(host.substring(colon + 1).trim());
                host = host.substring(0, colon).trim();
            } catch (NumberFormatException ignored) {
                // not a port after all - keep the whole string as the host
            }
        }

        String connectHost = host;
        int connectPort = port;
        if (port == DEFAULT_PORT) {
            // Same rule vanilla uses: only look for an SRV record when no custom port was given.
            String[] srv = resolveSrv(host);
            if (srv != null) {
                connectHost = srv[0];
                try {
                    connectPort = Integer.parseInt(srv[1]);
                } catch (NumberFormatException ignored) {
                    connectHost = host;
                }
            }
        }

        for (int attempt = 0; attempt < 2; attempt++) {
            try {
                return query(host, port, connectHost, connectPort);
            } catch (SocketTimeoutException e) {
                // slow or briefly busy server - one more try before calling it offline
            } catch (IOException | RuntimeException e) {
                break; // refused, unknown host, protocol error: retrying won't help
            }
        }
        return Result.OFFLINE;
    }

    private static Result query(String host, int port, String connectHost, int connectPort) throws IOException {
        try (Socket socket = new Socket()) {
            socket.setTcpNoDelay(true);
            socket.connect(new InetSocketAddress(connectHost, connectPort), CONNECT_TIMEOUT_MS);
            socket.setSoTimeout(READ_TIMEOUT_MS);
            DataOutputStream out = new DataOutputStream(socket.getOutputStream());
            DataInputStream in = new DataInputStream(socket.getInputStream());

            // Handshake: id 0x00, protocol, the host exactly as typed, its port, next state 1 (status).
            ByteArrayOutputStream handshake = new ByteArrayOutputStream();
            DataOutputStream h = new DataOutputStream(handshake);
            writeVarInt(h, 0x00);
            writeVarInt(h, PROTOCOL_VERSION);
            writeString(h, host);
            h.writeShort(port);
            writeVarInt(h, 1);
            writePacket(out, handshake.toByteArray());

            // Status request: just packet id 0x00.
            writePacket(out, new byte[]{0x00});
            out.flush();
            long statusSent = System.nanoTime();

            readVarInt(in); // packet length - the fields below are read by structure
            int id = readVarInt(in);
            if (id != 0x00) {
                throw new IOException("Unexpected status packet id " + id);
            }
            String json = readString(in);
            long statusRoundTrip = (System.nanoTime() - statusSent) / 1_000_000L;

            // Ping/pong gives the cleanest latency number; some proxies drop it, so fall back
            // to the status round trip rather than reporting a reachable server as offline.
            long pingMs = -1L;
            try {
                ByteArrayOutputStream ping = new ByteArrayOutputStream();
                DataOutputStream p = new DataOutputStream(ping);
                writeVarInt(p, 0x01);
                p.writeLong(System.nanoTime());
                long sent = System.nanoTime();
                writePacket(out, ping.toByteArray());
                out.flush();
                readVarInt(in);
                if (readVarInt(in) == 0x01) {
                    in.readLong();
                    pingMs = (System.nanoTime() - sent) / 1_000_000L;
                }
            } catch (IOException ignored) {
                // keep the status round trip
            }
            if (pingMs < 0L) {
                pingMs = statusRoundTrip;
            }

            int online = 0;
            int max = 0;
            byte[] favicon = null;
            try {
                JsonObject root = JsonParser.parseString(json).getAsJsonObject();
                if (root.has("players") && root.get("players").isJsonObject()) {
                    JsonObject players = root.getAsJsonObject("players");
                    online = intOf(players, "online");
                    max = intOf(players, "max");
                }
                if (root.has("favicon") && root.get("favicon").isJsonPrimitive()) {
                    String icon = root.get("favicon").getAsString();
                    int comma = icon.indexOf(',');
                    if (comma >= 0) {
                        icon = icon.substring(comma + 1);
                    }
                    favicon = Base64.getMimeDecoder().decode(icon);
                }
            } catch (RuntimeException ignored) {
                // The server did answer - odd JSON just means no player count / icon.
            }
            return new Result(true, (int) Math.max(1L, pingMs), online, max, favicon);
        }
    }

    private static int intOf(JsonObject obj, String key) {
        JsonElement e = obj.get(key);
        if (e != null && e.isJsonPrimitive()) {
            try {
                return e.getAsInt();
            } catch (NumberFormatException ignored) {
                return 0;
            }
        }
        return 0;
    }

    /** Looks up _minecraft._tcp.<host> via the JDK's DNS provider; null if none/unavailable. */
    private static String[] resolveSrv(String host) {
        try {
            Hashtable<String, String> env = new Hashtable<>();
            env.put("java.naming.factory.initial", "com.sun.jndi.dns.DnsContextFactory");
            env.put("java.naming.provider.url", "dns:");
            env.put("com.sun.jndi.dns.timeout.initial", "1500");
            env.put("com.sun.jndi.dns.timeout.retries", "1");
            InitialDirContext ctx = new InitialDirContext(env);
            Attributes attrs = ctx.getAttributes("_minecraft._tcp." + host, new String[]{"SRV"});
            Attribute srv = attrs.get("srv");
            if (srv != null && srv.size() > 0) {
                String[] parts = srv.get().toString().split(" "); // priority weight port target
                if (parts.length >= 4) {
                    String target = parts[3];
                    if (target.endsWith(".")) {
                        target = target.substring(0, target.length() - 1);
                    }
                    return new String[]{target, parts[2]};
                }
            }
        } catch (Throwable ignored) {
            // no SRV record, no DNS provider, offline resolver... connect to the typed host
        }
        return null;
    }

    // ---- wire format helpers --------------------------------------------------------------

    private static void writePacket(DataOutputStream out, byte[] payload) throws IOException {
        writeVarInt(out, payload.length);
        out.write(payload);
    }

    private static void writeString(DataOutputStream out, String s) throws IOException {
        byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
        writeVarInt(out, bytes.length);
        out.write(bytes);
    }

    private static void writeVarInt(DataOutputStream out, int value) throws IOException {
        while ((value & ~0x7F) != 0) {
            out.writeByte((value & 0x7F) | 0x80);
            value >>>= 7;
        }
        out.writeByte(value);
    }

    private static int readVarInt(DataInputStream in) throws IOException {
        int value = 0;
        int shift = 0;
        int b;
        do {
            b = in.readUnsignedByte();
            value |= (b & 0x7F) << shift;
            shift += 7;
            if (shift > 35) {
                throw new IOException("VarInt too long");
            }
        } while ((b & 0x80) != 0);
        return value;
    }

    private static String readString(DataInputStream in) throws IOException {
        int length = readVarInt(in);
        if (length < 0 || length > MAX_STRING_BYTES) {
            throw new IOException("Bad string length " + length);
        }
        byte[] bytes = new byte[length];
        in.readFully(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
