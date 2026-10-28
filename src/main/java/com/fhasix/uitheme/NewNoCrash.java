package com.fhasix.uitheme;

import net.minecraft.client.MinecraftClient;

/**
 * No Crash: when the game throws during a frame or tick (a texture/model mismatch, a broken
 * renderer, a bug in a mod...) the game does NOT close. It leaves the world, goes back to the title
 * screen and shows "Your game has crashed!" with a button to return.
 *
 * mixin/NewCrashMixin wraps the client's frame call and hands any exception to handle().
 * If handling itself fails (or the crash screen crashes) the normal crash behaviour takes over,
 * so this can never trap you in a loop. Out-of-memory errors are never caught.
 */
public final class NewNoCrash {

    static final String ID = "no_crash";

    private static boolean handling = false;

    private NewNoCrash() {
    }

    /** @return true if the exception was handled and the game should keep running. */
    public static boolean handle(MinecraftClient c, Throwable t) {
        if (handling || t instanceof OutOfMemoryError || !(fromFhasix(t) || NewModSettings.isEnabled(c, ID))) {
            return false;
        }
        // a Fhasix SCREEN (mod menu, store, ...) failed: close just that screen and stay in the world
        if (fromFhasix(t) && c.currentScreen != null && !(c.currentScreen instanceof NewCrashScreen)
                && c.currentScreen.getClass().getName().startsWith("com.fhasix.uitheme") && screenFixAllowed()) {
            t.printStackTrace();
            try {
                c.setScreen(null);
                return true;
            } catch (Throwable second) {
                // fall through to the normal handling below
            }
        }
        handling = true;
        t.printStackTrace(); // the full trace still goes to the log
        String detail = describe(t);
        try {
            NewCrashScreen screen = new NewCrashScreen(detail, NewModSettings.optBool(c, ID, "details"));
            if (c.world != null) {
                c.disconnect(screen, false);
            }
            c.setScreen(screen);
            return true;
        } catch (Throwable second) {
            handling = false;
            return false;
        }
    }

    private static final java.util.ArrayDeque<Long> SCREEN_FIXES = new java.util.ArrayDeque<>();

    /** At most 3 screen closes per 10 seconds, so a screen that keeps failing cannot trap the game in a loop. */
    private static boolean screenFixAllowed() {
        long now = System.currentTimeMillis();
        while (!SCREEN_FIXES.isEmpty() && now - SCREEN_FIXES.peekFirst() > 10_000L) {
            SCREEN_FIXES.pollFirst();
        }
        if (SCREEN_FIXES.size() >= 3) {
            return false;
        }
        SCREEN_FIXES.addLast(now);
        return true;
    }

    /** True when Fhasix code (or one of its mixin handlers) is in the stack trace of the error or its causes. */
    private static boolean fromFhasix(Throwable t) {
        int depth = 0;
        for (Throwable x = t; x != null && depth < 8; x = x.getCause(), depth++) {
            for (StackTraceElement e : x.getStackTrace()) {
                if (e.getClassName().startsWith("com.fhasix.uitheme") || e.getMethodName().contains("fhasix$")) {
                    return true;
                }
            }
            if (x.getCause() == x) {
                break;
            }
        }
        return false;
    }

    static void reset() {
        handling = false;
    }

    private static String describe(Throwable t) {
        Throwable root = t;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        String msg = root.getMessage();
        String s = root.getClass().getSimpleName() + (msg == null || msg.isBlank() ? "" : ": " + msg);
        s = s.replace('\n', ' ').replace('\r', ' ');
        return s.length() > 200 ? s.substring(0, 200) + "..." : s;
    }
}
