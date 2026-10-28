package com.fhasix.uitheme;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.util.Identifier;

/**
 * Credit logo: the Fhasix logo from the title screen, drawn small in the bottom-right corner of the
 * vanilla screens (inventory, chests, shulker boxes, singleplayer, multiplayer, game menu, joining a
 * server, options ...). Not drawn on the title screen, chat, or any Fhasix screen (mod menu, launcher,
 * store, cosmetics ...).
 */
final class NewScreenLogo {

    private static final Identifier LOGO = Identifier.of("fhasixuitheme", "textures/gui/title/fhasix_logo.png");
    private static final int TEX_W = 1251;
    private static final int TEX_H = 323;
    private static final int MARGIN = 6;

    private NewScreenLogo() {
    }

    static void init() {
        ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
            if (shows(screen)) {
                ScreenEvents.afterRender(screen).register((s, g, mx, my, delta) -> NewSafe.run("screen_logo", () -> draw(g, s)));
            }
        });
    }

    private static boolean shows(Screen s) {
        return !(s instanceof TitleScreen) && !(s instanceof ChatScreen)
                && !s.getClass().getName().startsWith("com.fhasix.uitheme");
    }

    private static void draw(DrawContext g, Screen s) {
        int w = Math.max(60, Math.min(110, Math.round(s.width * 0.13f)));
        int h = Math.round(w * (float) TEX_H / TEX_W);
        g.drawTexture(RenderPipelines.GUI_TEXTURED, LOGO, s.width - w - MARGIN, s.height - h - MARGIN, 0, 0,
                w, h, TEX_W, TEX_H, TEX_W, TEX_H);
    }
}
