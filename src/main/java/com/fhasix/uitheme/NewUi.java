package com.fhasix.uitheme;

import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;

/**
 * NEW (v30 UI rewrite). Cheap drawing helpers for the Mod Menu.
 *
 * Why this exists: a1.drawRoundedGradientRect issues one fill PER PIXEL COLUMN (plus one per
 * corner pixel). That is fine for a few buttons but made the big Mod Menu lag. rrect() here
 * draws a rounded, vertically-graded rectangle with about 2r+1 fills no matter how wide it is.
 */
final class NewUi {

    /** Icon atlas: 5 columns x 10 rows of 128px cells (640x1280). */
    static final Identifier ATLAS = Identifier.of("fhasixuitheme", "textures/gui/new_mod_icons.png");
    static final int CELL = 128;
    static final int ATLAS_W = 640;
    static final int ATLAS_H = 1280;
    static final int COLS = 5;

    // atlas cell indexes
    static final int ICON_HEART_OFF = 14;
    static final int ICON_HEART_ON = 15;
    static final int ICON_GEAR = 16;
    static final int ICON_GRID = 17;

    private NewUi() {
    }

    /** Menu-wide scale: reference design 1394x847 fitted to the window, then shrunk a little so nothing hugs the edges. */
    static final float MENU_SCALE = 0.88f;

    static float scale(int w, int h) {
        return Math.min(w / 1394f, h / 847f) * MENU_SCALE;
    }

    // shared palette (ARGB)
    static final int PANEL_T = rgb(11, 12, 15);
    static final int PANEL_B = rgb(8, 9, 12);
    static final int BTN_BORDER = rgb(30, 33, 40);
    static final int BTN_T = rgb(20, 22, 28);
    static final int BTN_B = rgb(15, 17, 21);
    static final int HOV_T = rgb(54, 64, 78);
    static final int HOV_B = rgb(23, 26, 33);
    static final int CARD_BORDER = rgb(26, 29, 35);
    static final int CARD_T = rgb(16, 18, 22);
    static final int CARD_B = rgb(13, 14, 18);
    static final int RED_T = rgb(231, 60, 64);
    static final int RED_B = rgb(205, 46, 50);
    static final int RED_HT = rgb(240, 84, 88);
    static final int RED_HB = rgb(214, 58, 62);
    static final int ON_T = rgb(27, 78, 33);
    static final int ON_B = rgb(20, 60, 25);
    static final int ON_HT = rgb(36, 98, 43);
    static final int ON_HB = rgb(25, 72, 30);
    static final int TXT = 0xFFFFFFFF;
    static final int TXT_DIM = 0xFF8C8F96;
    static final int TXT_ON = 0xFF3FBF4D;

    static int rgb(int r, int g, int b) {
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    static int lerp(int a, int b, float t) {
        if (t <= 0f) {
            return a;
        }
        if (t >= 1f) {
            return b;
        }
        int aa = (int) (((a >>> 24) & 255) + (((b >>> 24) & 255) - ((a >>> 24) & 255)) * t);
        int ar = (int) (((a >> 16) & 255) + (((b >> 16) & 255) - ((a >> 16) & 255)) * t);
        int ag = (int) (((a >> 8) & 255) + (((b >> 8) & 255) - ((a >> 8) & 255)) * t);
        int ab = (int) ((a & 255) + ((b & 255) - (a & 255)) * t);
        return (aa << 24) | (ar << 16) | (ag << 8) | ab;
    }

    /** Rounded rectangle, vertical gradient top -> bottom. */
    static void rrect(DrawContext c, int x, int y, int w, int h, int r, int top, int bot) {
        if (w <= 0 || h <= 0) {
            return;
        }
        r = Math.max(0, Math.min(r, Math.min(w, h) / 2));
        if (r == 0) {
            c.fillGradient(x, y, x + w, y + h, top, bot);
            return;
        }
        c.fillGradient(x, y + r, x + w, y + h - r, lerp(top, bot, r / (float) h), lerp(top, bot, (h - r) / (float) h));
        for (int i = 0; i < r; i++) {
            float dy = r - i - 0.5f;
            int inset = r - Math.round((float) Math.sqrt(r * r - dy * dy));
            c.fill(x + inset, y + i, x + w - inset, y + i + 1, lerp(top, bot, (i + 0.5f) / h));
            c.fill(x + inset, y + h - 1 - i, x + w - inset, y + h - i, lerp(top, bot, (h - i - 0.5f) / h));
        }
    }

    /** Rounded rectangle with a 1px border. */
    static void frame(DrawContext c, int x, int y, int w, int h, int r, int border, int top, int bot) {
        rrect(c, x, y, w, h, r, border, border);
        rrect(c, x + 1, y + 1, w - 2, h - 2, Math.max(0, r - 1), top, bot);
    }

    /** Draws atlas cell {@code index} as a size x size square at x,y. */
    static void icon(DrawContext c, int index, int x, int y, int size) {
        int u = (index % COLS) * CELL;
        int v = (index / COLS) * CELL;
        c.drawTexture(RenderPipelines.GUI_TEXTURED, ATLAS, x, y, u, v, size, size, CELL, CELL, ATLAS_W, ATLAS_H);
    }

    /** Draws a whole standalone 128x128 texture (the top icon row) as size x size. */
    static void whole(DrawContext c, Identifier tex, int x, int y, int size) {
        c.drawTexture(RenderPipelines.GUI_TEXTURED, tex, x, y, 0, 0, size, size, 128, 128, 128, 128);
    }

    static boolean in(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }
}
