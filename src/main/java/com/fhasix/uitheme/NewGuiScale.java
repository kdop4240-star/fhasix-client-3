package com.fhasix.uitheme;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.util.Window;

/**
 * Locks the GUI scale to 2 while a Fhasix screen (title screen, mod menu, store, cosmetics, discover,
 * screenshots, skin pop-up, ...) is open, so buttons AND text look exactly like they do at GUI scale 2
 * whatever scale is chosen in the video settings. The player's own scale is restored for every other
 * screen and in-game.
 *
 * Two tiny mixins feed this: NewSetScreenMixin (tells us which screen is about to open, before it is
 * laid out) and NewWindowMixin (makes Window.calculateScaleFactor return 2 while the lock is on, so a
 * window resize or a GUI-scale change keeps the lock). Not locked: the HUD editor (its preview has to
 * match the real HUD), the crash screen and the world-only banner.
 */
public final class NewGuiScale {

    public static final int LOCKED = 2;

    /** True while the Fhasix title screen is the active title mode (set by the title screen mixin). */
    public static volatile boolean titleFhasix = true;

    private static volatile boolean wantLock = false;

    private NewGuiScale() {
    }

    public static boolean lockWanted() {
        return wantLock;
    }

    private static boolean lockable(MinecraftClient c, Screen s) {
        if (s == null) {
            return c.world == null && titleFhasix; // closing everything on the main menu -> title screen
        }
        if (s instanceof TitleScreen) {
            return titleFhasix;
        }
        if (s instanceof NewLauncherScreen || s instanceof NewCrashScreen || s instanceof WorldOnlyBannerScreen) {
            return false;
        }
        return s.getClass().getName().startsWith("com.fhasix.uitheme");
    }

    /** Called at the very start of MinecraftClient.setScreen, before the new screen is initialised. */
    public static void onSetScreen(MinecraftClient c, Screen next) {
        NewSafe.run("gui_scale", () -> setScreen(c, next));
    }

    private static void setScreen(MinecraftClient c, Screen next) {
        if (c == null || c.options == null || c.getWindow() == null) {
            return;
        }
        boolean want = lockable(c, next);
        if (want == wantLock) {
            return;
        }
        wantLock = want;
        Window w = c.getWindow();
        int f = w.calculateScaleFactor(c.options.getGuiScale().getValue(), c.options.getForceUnicodeFont().getValue());
        if (Math.abs(w.getScaleFactor() - f) > 0.001) {
            w.setScaleFactor(f);
        }
    }
}
