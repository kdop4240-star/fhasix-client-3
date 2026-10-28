package com.fhasix.uitheme;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.entity.player.PlayerSkinType;
import net.minecraft.entity.player.SkinTextures;
import net.minecraft.util.AssetInfo;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * Five custom-skin slots, stored in <game dir>/fhasix_skins/ as slot1.png .. slot5.png plus a
 * small skins.properties (which slot is equipped, and slim/default per slot). Slot index 0..4
 * everywhere in code; the UI shows 1..5.
 *
 * The equipped slot is applied to the LOCAL player by mixin/skin.java through activeSkin() -
 * client-side only, other players keep seeing the account skin. An empty slot equips nothing
 * (the account skin shows), so "slot empty" and "no custom skin" are the same state.
 *
 * Getting a skin in: assign() takes a PNG path (from the native file picker, or any file),
 * validates it is a real 64x64 PNG and stores it as slotN.png. importStray() covers platforms
 * with no file dialog (Android launchers): any other PNG dropped into the folder is picked up,
 * validated, stored in a slot, and moved to imported/ (or rejected/).
 *
 * All file IO and PNG decoding runs on a small daemon pool; every state change and every GPU
 * texture registration happens on the client thread. API facts (1.21.11 Yarn): SkinTextures(
 * AssetInfo.TextureAsset body, cape, elytra, PlayerSkinType model, boolean secure), AssetInfo.
 * TextureAssetInfo(Identifier id, Identifier texturePath), PlayerSkinType.SLIM / WIDE,
 * NativeImageBackedTexture(Supplier<String>, NativeImage).
 */
public final class FhasixSkins {

    public static final int SLOTS = 5;

    public enum Result { OK, NOT_PNG, WRONG_SIZE, IO_ERROR }

    public static final class ImportResult {
        public final List<Integer> filled;
        public final int rejected;
        /** Valid candidates left in the folder because every slot was already taken. */
        public final int skipped;

        ImportResult(List<Integer> filled, int rejected, int skipped) {
            this.filled = filled;
            this.rejected = rejected;
            this.skipped = skipped;
        }
    }

    private static final class Slot {
        boolean present;
        boolean slim;
        Identifier tex;
        boolean loading;
        int version;
        SkinTextures preview;
        SkinTextures active;
        AssetInfo.TextureAsset activeCape;
        AssetInfo.TextureAsset activeElytra;
        boolean activeSecure;
    }

    static final ExecutorService EXEC = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "fhasix-skins-io");
        t.setDaemon(true);
        return t;
    });

    private static final Slot[] SLOT = new Slot[SLOTS];
    private static boolean loaded = false;
    private static int selected = 0;
    private static int texCounter = 0;
    /** True while a folder scan is in flight, so overlapping scans can't import the same file twice. */
    private static boolean importRunning = false;

    private FhasixSkins() {
    }

    // =========================================================================================
    // paths + persistence
    // =========================================================================================

    public static Path dir(MinecraftClient c) {
        return c.runDirectory.toPath().resolve("fhasix_skins");
    }

    private static Path slotFile(MinecraftClient c, int i) {
        return dir(c).resolve("slot" + (i + 1) + ".png");
    }

    private static void ensureLoaded(MinecraftClient c) {
        if (loaded) {
            return;
        }
        loaded = true;
        for (int i = 0; i < SLOTS; i++) {
            SLOT[i] = new Slot();
        }
        Properties p = new Properties();
        Path cfg = dir(c).resolve("skins.properties");
        if (Files.isRegularFile(cfg)) {
            try (InputStream in = Files.newInputStream(cfg)) {
                p.load(in);
            } catch (IOException ignored) {
                // unreadable config - start from defaults
            }
        }
        try {
            selected = Math.max(0, Math.min(SLOTS - 1, Integer.parseInt(p.getProperty("selected", "0").trim())));
        } catch (NumberFormatException ignored) {
            selected = 0;
        }
        for (int i = 0; i < SLOTS; i++) {
            SLOT[i].slim = "true".equals(p.getProperty("slim." + i));
            SLOT[i].present = Files.isRegularFile(slotFile(c, i));
        }
    }

    private static void save(MinecraftClient c) {
        try {
            Path dir = dir(c);
            Files.createDirectories(dir);
            Properties p = new Properties();
            p.setProperty("selected", String.valueOf(selected));
            for (int i = 0; i < SLOTS; i++) {
                p.setProperty("slim." + i, String.valueOf(SLOT[i].slim));
            }
            try (OutputStream out = Files.newOutputStream(dir.resolve("skins.properties"))) {
                p.store(out, "Fhasix skin slots");
            }
        } catch (IOException ignored) {
            // not being able to remember the choice must never break anything
        }
    }

    // =========================================================================================
    // state queries / changes (client thread)
    // =========================================================================================

    public static int selected(MinecraftClient c) {
        ensureLoaded(c);
        return selected;
    }

    public static void select(MinecraftClient c, int slot) {
        ensureLoaded(c);
        selected = Math.max(0, Math.min(SLOTS - 1, slot));
        save(c);
    }

    public static boolean hasSkin(MinecraftClient c, int slot) {
        ensureLoaded(c);
        return SLOT[slot].present;
    }

    public static boolean isSlim(MinecraftClient c, int slot) {
        ensureLoaded(c);
        return SLOT[slot].slim;
    }

    public static void setSlim(MinecraftClient c, int slot, boolean slim) {
        ensureLoaded(c);
        Slot s = SLOT[slot];
        s.slim = slim;
        s.preview = null;
        s.active = null;
        save(c);
    }

    /** Filled, but its texture hasn't finished loading yet. */
    public static boolean isLoading(MinecraftClient c, int slot) {
        ensureLoaded(c);
        return SLOT[slot].present && SLOT[slot].tex == null;
    }

    /** SkinTextures for drawing a face in the UI; null while empty or still loading. */
    public static SkinTextures preview(MinecraftClient c, int slot) {
        ensureLoaded(c);
        Slot s = SLOT[slot];
        if (!s.present) {
            return null;
        }
        if (s.tex == null) {
            requestTexture(c, slot);
            return null;
        }
        if (s.preview == null) {
            s.preview = build(s, null, null, false);
        }
        return s.preview;
    }

    /**
     * Called by mixin/skin.java for the local player on every getSkin(). Returns the equipped
     * slot's skin (carrying over the account's cape/elytra), or null to leave the account skin.
     */
    public static SkinTextures activeSkin(MinecraftClient c, SkinTextures orig) {
        ensureLoaded(c);
        Slot s = SLOT[selected];
        if (!s.present) {
            return null;
        }
        if (s.tex == null) {
            requestTexture(c, selected);
            return null;
        }
        AssetInfo.TextureAsset cape = orig != null ? orig.cape() : null;
        AssetInfo.TextureAsset elytra = orig != null ? orig.elytra() : null;
        boolean secure = orig != null && orig.secure();
        if (s.active != null && s.activeCape == cape && s.activeElytra == elytra && s.activeSecure == secure) {
            return s.active;
        }
        s.active = build(s, cape, elytra, secure);
        s.activeCape = cape;
        s.activeElytra = elytra;
        s.activeSecure = secure;
        return s.active;
    }

    private static SkinTextures build(Slot s, AssetInfo.TextureAsset cape, AssetInfo.TextureAsset elytra,
                                      boolean secure) {
        AssetInfo.TextureAsset body = new AssetInfo.TextureAssetInfo(s.tex, s.tex);
        return new SkinTextures(body, cape, elytra, s.slim ? PlayerSkinType.SLIM : PlayerSkinType.WIDE, secure);
    }

    public static void clear(MinecraftClient c, int slot) {
        ensureLoaded(c);
        try {
            Files.deleteIfExists(slotFile(c, slot));
        } catch (IOException ignored) {
            // leave it - it will simply still show as filled
            return;
        }
        invalidate(c, slot);
        SLOT[slot].present = false;
        save(c);
    }

    // =========================================================================================
    // textures
    // =========================================================================================

    private static void invalidate(MinecraftClient c, int slot) {
        Slot s = SLOT[slot];
        if (s.tex != null) {
            c.getTextureManager().destroyTexture(s.tex);
            s.tex = null;
        }
        s.version++;
        s.loading = false;
        s.preview = null;
        s.active = null;
    }

    private static void markFilled(MinecraftClient c, int slot) {
        ensureLoaded(c);
        invalidate(c, slot);
        SLOT[slot].present = true;
        save(c);
    }

    private static void requestTexture(MinecraftClient c, int slot) {
        Slot s = SLOT[slot];
        if (s.loading || s.tex != null || !s.present) {
            return;
        }
        s.loading = true;
        final int ver = s.version;
        final Path file = slotFile(c, slot);
        EXEC.execute(() -> {
            NativeImage img = null;
            try (InputStream in = Files.newInputStream(file)) {
                img = NativeImage.read(in);
                if (img.getWidth() != 64 || img.getHeight() != 64) {
                    img.close();
                    img = null;
                }
            } catch (Throwable t) {
                if (img != null) {
                    img.close();
                    img = null;
                }
            }
            final NativeImage ready = img;
            c.execute(() -> installTexture(c, slot, ver, ready));
        });
    }

    private static void installTexture(MinecraftClient c, int slot, int ver, NativeImage img) {
        Slot s = SLOT[slot];
        s.loading = false;
        if (img == null) {
            s.present = false; // the file on disk isn't a usable skin - treat the slot as empty
            return;
        }
        if (ver != s.version || !s.present) {
            img.close();
            return;
        }
        Identifier id = Identifier.of("fhasixuitheme", "skins/slot" + (slot + 1) + "_" + (texCounter++));
        NativeImageBackedTexture tex = new NativeImageBackedTexture(() -> "fhasix_skin_slot" + (slot + 1), img);
        c.getTextureManager().registerTexture(id, tex);
        s.tex = id;
        s.preview = null;
        s.active = null;
    }

    // =========================================================================================
    // getting skins in
    // =========================================================================================

    /** Validates source as a 64x64 PNG and stores it in the slot. cb runs on the client thread. */
    public static void assign(MinecraftClient c, int slot, Path source, Consumer<Result> cb) {
        EXEC.execute(() -> {
            Result r = validateAndStore(c, slot, source);
            c.execute(() -> {
                if (r == Result.OK) {
                    markFilled(c, slot);
                }
                cb.accept(r);
            });
        });
    }

    private static Result validateAndStore(MinecraftClient c, int slot, Path source) {
        byte[] bytes;
        try {
            bytes = Files.readAllBytes(source);
        } catch (IOException e) {
            return Result.IO_ERROR;
        }
        NativeImage img = null;
        try {
            img = NativeImage.read(new ByteArrayInputStream(bytes));
            if (img.getWidth() != 64 || img.getHeight() != 64) {
                return Result.WRONG_SIZE;
            }
        } catch (IOException | RuntimeException e) {
            return Result.NOT_PNG;
        } finally {
            if (img != null) {
                img.close();
            }
        }
        try {
            Path dir = dir(c);
            Files.createDirectories(dir);
            Path tmp = dir.resolve("slot" + (slot + 1) + ".png.tmp");
            Files.write(tmp, bytes);
            Files.move(tmp, slotFile(c, slot), StandardCopyOption.REPLACE_EXISTING);
            return Result.OK;
        } catch (IOException e) {
            return Result.IO_ERROR;
        }
    }

    /**
     * Picks up PNGs dropped into the folder by hand (the path for platforms with no file dialog).
     * The first one goes to preferredSlot (only if that slot is empty, unless overwritePreferred),
     * the rest into empty slots; originals are moved to imported/ or rejected/. Files modified in
     * the last 1.5s are skipped so a copy still in progress isn't read half-written. cb is only
     * called if something was found, on the client thread.
     */
    public static void importStray(MinecraftClient c, int preferredSlot, boolean overwritePreferred,
                                   Consumer<ImportResult> cb) {
        ensureLoaded(c);
        if (importRunning) {
            return;
        }
        importRunning = true;
        final boolean[] present = new boolean[SLOTS];
        for (int i = 0; i < SLOTS; i++) {
            present[i] = SLOT[i].present;
        }
        EXEC.execute(() -> {
            try {
                scanAndImport(c, preferredSlot, overwritePreferred, present, cb);
            } finally {
                c.execute(() -> importRunning = false);
            }
        });
    }

    private static void scanAndImport(MinecraftClient c, int preferredSlot, boolean overwritePreferred,
                                      boolean[] present, Consumer<ImportResult> cb) {
        Path dir = dir(c);
        List<Path> strays = new ArrayList<>();
        if (Files.isDirectory(dir)) {
            try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir, "*.png")) {
                for (Path p : ds) {
                    String n = p.getFileName().toString().toLowerCase(Locale.ROOT);
                    if (n.matches("slot[1-5]\\.png") || !Files.isRegularFile(p)) {
                        continue;
                    }
                    try {
                        if (System.currentTimeMillis() - Files.getLastModifiedTime(p).toMillis() < 1500L) {
                            continue;
                        }
                    } catch (IOException e) {
                        continue;
                    }
                    strays.add(p);
                }
            } catch (IOException ignored) {
                // folder vanished - nothing to import
            }
        }
        if (strays.isEmpty()) {
            return;
        }
        strays.sort(Comparator.comparing((Path p) -> p.getFileName().toString()));

        final List<Integer> filled = new ArrayList<>();
        int rejected = 0;
        int skipped = 0;
        boolean preferredUsed = false;
        for (int idx = 0; idx < strays.size(); idx++) {
            Path p = strays.get(idx);
            int target = -1;
            if (!preferredUsed && (overwritePreferred || !present[preferredSlot])) {
                target = preferredSlot;
            } else {
                for (int i = 0; i < SLOTS; i++) {
                    if (!present[i] && i != preferredSlot) {
                        target = i;
                        break;
                    }
                }
            }
            if (target < 0) {
                skipped = strays.size() - idx; // no room - the rest stay in the folder untouched
                break;
            }
            if (validateAndStore(c, target, p) == Result.OK) {
                present[target] = true;
                filled.add(target);
                if (target == preferredSlot) {
                    preferredUsed = true;
                }
                moveAway(dir, p, "imported");
            } else {
                rejected++;
                moveAway(dir, p, "rejected");
            }
        }
        final int rej = rejected;
        final int skip = skipped;
        c.execute(() -> {
            for (int slot : filled) {
                markFilled(c, slot);
            }
            cb.accept(new ImportResult(filled, rej, skip));
        });
    }

    private static void moveAway(Path dir, Path file, String sub) {
        try {
            Path d = dir.resolve(sub);
            Files.createDirectories(d);
            Path target = d.resolve(file.getFileName());
            if (Files.exists(target)) {
                target = d.resolve(System.currentTimeMillis() + "_" + file.getFileName());
            }
            Files.move(file, target);
        } catch (IOException ignored) {
            // can't move it - it stays, and will be looked at again on the next scan
        }
    }

    public static void openFolder(MinecraftClient c) {
        try {
            Path d = dir(c);
            Files.createDirectories(d);
            Util.getOperatingSystem().open(d.toFile());
        } catch (Throwable ignored) {
            // no desktop shell to open it with (e.g. some Android launchers)
        }
    }

    /**
     * Native "choose a skin" dialog via LWJGL's tinyfd, called reflectively so a runtime without
     * it (Android launchers) fails softly instead of at class-load. BLOCKS until the dialog
     * closes - call from a worker thread, never the render thread.
     *
     * @return the chosen file path, or null if the dialog was cancelled
     * @throws Exception if the dialog isn't available here - the caller falls back to the folder
     */
    public static String chooseFile(MinecraftClient c) throws Exception {
        Class<?> tfd = Class.forName("org.lwjgl.util.tinyfd.TinyFileDialogs");
        Method open = tfd.getMethod("tinyfd_openFileDialog",
                CharSequence.class, CharSequence.class, PointerBuffer.class, CharSequence.class, boolean.class);
        Path start = dir(c);
        try {
            Files.createDirectories(start);
        } catch (IOException ignored) {
            // the dialog just opens somewhere else
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer filters = stack.mallocPointer(1);
            filters.put(stack.UTF8("*.png")).flip();
            Object result = open.invoke(null, "Choose a skin (64x64 PNG)", start + File.separator,
                    filters, "PNG skin files", Boolean.FALSE);
            return result == null ? null : result.toString();
        }
    }
}
