package com.fhasix.uitheme;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Path;

/**
 * Capture side of the screenshot manager: a rebindable "Take Screenshot" key (shows up in
 * Options > Controls under "Fhasix UI Theme") that saves through vanilla's own
 * ScreenshotRecorder, so files land in <game dir>/screenshots exactly like F2 does.
 * ScreenshotsScreen (the title-screen chat icon) lists whatever is in that folder.
 *
 * VERIFIED against the 1.21.11 Yarn javadocs:
 *  - ScreenshotRecorder.saveScreenshot(File, Framebuffer, Consumer<Text>)
 *  - KeyBinding.Category.create(Identifier)  (1.21.9+ replaced the String category)
 */
public final class ScreenshotManager {

    private static KeyBinding captureKey;

    private ScreenshotManager() {
    }

    /** Call once from the client entrypoint. */
    public static void init() {
        if (captureKey != null) {
            return;
        }

        KeyBinding.Category category = KeyBinding.Category.create(Identifier.of("fhasixuitheme", "main"));
        captureKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.fhasixuitheme.screenshot",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_F8,
                category
        ));

        ClientTickEvents.END_CLIENT_TICK.register(NewSafe.tick("screenshots", client -> {
            boolean fire = false;
            while (captureKey.wasPressed()) {
                fire = true;
            }
            if (fire) {
                capture(client);
            }
        }));
    }

    public static void capture(MinecraftClient client) {
        // While the startup/reload overlay is up, the frame is the full-screen loading art -
        // and if that texture hasn't resolved yet, the saved "screenshot" is a full-frame
        // magenta/black missing-texture checkerboard. Skip the press instead of saving junk.
        if (client.getOverlay() != null) {
            return;
        }
        try {
            ScreenshotRecorder.saveScreenshot(
                    client.runDirectory,
                    client.getFramebuffer(),
                    message -> client.execute(() -> client.inGameHud.getChatHud().addMessage(message))
            );
        } catch (Throwable ignored) {
            // A failed capture must never take the client down.
        }
    }

    public static Path screenshotsDir(MinecraftClient client) {
        return client.runDirectory.toPath().resolve("screenshots");
    }

    /** The key's current label for UI hints, e.g. "F8" - follows whatever the player rebinds it to. */
    public static String keyLabel() {
        if (captureKey == null) {
            return "F8";
        }
        try {
            return captureKey.getBoundKeyLocalizedText().getString();
        } catch (Throwable t) {
            return "F8";
        }
    }
}
