package com.fhasix.uitheme;

import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

import java.util.List;

/**
 * The launcher that Right Shift opens while you are in a world (redesigned).
 *
 *   +--------------------------------------+
 *   | [F]  FHASIX                      [X] |
 *   |      Mod launcher                    |
 *   |--------------------------------------|
 *   | [  OPEN MOD MENU  ]                  |
 *   | [ ] [ ] [ ] [ ]   (placeholders)     |
 *   +--------------------------------------+
 *
 * The world behind is blurred and darkened (vanilla menu blur). Every enabled HUD mod is drawn
 * crisp on top and can be click-dragged to a new place; positions save on release.
 * Sizes come from the same 1394x847 design as the Mod Menu, scaled by NewUi.scale.
 */
public final class NewLauncherScreen extends Screen {

    private static final int DW = 1394;
    private static final int DH = 847;
    private static final int PW = 500;
    private static final int PH = 308;

    private static final Identifier MARK =
            Identifier.of("fhasixuitheme", "textures/gui/title/new_fhasix_mark.png");
    private static final int MARK_TEX = 256;
    private static final String[] ICONS = {"archive", "gem", "chat", "list"};
    private static final Identifier[] ICON_TEX = new Identifier[ICONS.length];

    static {
        for (int i = 0; i < ICONS.length; i++) {
            ICON_TEX[i] = Identifier.of("fhasixuitheme", "textures/gui/icons/" + ICONS[i] + ".png");
        }
    }

    private static boolean markFilterApplied = false;

    private float k;
    private int ox;
    private int oy;
    private int[] panel;
    private int[] mark;
    private int[] closeBtn;
    private int[] primary;
    private final int[][] iconBtn = new int[ICONS.length][];
    private int dividerY;
    private int dividerX1;
    private int dividerX2;

    private String dragId = null;
    private int grabX;
    private int grabY;
    private int dragW;
    private int dragH;

    public NewLauncherScreen() {
        super(Text.literal("FHASIX"));
    }

    private int sx(int v) {
        return ox + Math.round(v * k);
    }

    private int sy(int v) {
        return oy + Math.round(v * k);
    }

    private int sl(int v) {
        return Math.max(1, Math.round(v * k));
    }

    private int[] rect(int x, int y, int w, int h) {
        int x0 = sx(x);
        int y0 = sy(y);
        return new int[]{x0, y0, sx(x + w) - x0, sy(y + h) - y0};
    }

    @Override
    protected void init() {
        k = NewUi.scale(width, height);
        ox = (width - Math.round(DW * k)) / 2;
        oy = (height - Math.round(DH * k)) / 2;
        int px = (DW - PW) / 2;
        int py = (DH - PH) / 2;

        panel = rect(px, py, PW, PH);
        mark = rect(px + 32, py + 30, 64, 64);
        closeBtn = rect(px + PW - 32 - 46, py + 34, 46, 46);
        dividerY = sy(py + 112);
        dividerX1 = sx(px + 28);
        dividerX2 = sx(px + PW - 28);
        primary = rect(px + 32, py + 132, PW - 64, 68);
        for (int i = 0; i < ICONS.length; i++) {
            iconBtn[i] = rect(px + 32 + i * (100 + 12), py + 226, 100, 54);
        }
    }

    /** Intentionally empty: the blur is applied from render() so it happens exactly once, in order. */

    /**
     * Vanilla background (panorama / blur / darkening) drawn once. The game allows only ONE blur per
     * frame and throws "Can only blur once per frame" if a launcher or the loading overlay has already
     * drawn one - that is caught here so it can never crash the game.
     */
    private void safeBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        try {
            super.renderBackground(context, mouseX, mouseY, delta);
        } catch (IllegalStateException alreadyBlurred) {
            context.fill(0, 0, width, height, 0xB0000000);
        }
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
    }

    @Override
    public void render(DrawContext c, int mx, int my, float delta) {
        safeBackground(c, mx, my, delta); // vanilla blur + darkening (needs Menu Background Blur > 0)
        c.fill(0, 0, width, height, 0x30000000);
        if (!markFilterApplied) {
            markFilterApplied = true;
        }
        int rad = Math.max(3, sl(10));

        NewUi.frame(c, panel[0], panel[1], panel[2], panel[3], Math.max(3, sl(16)), NewUi.CARD_BORDER, NewUi.PANEL_T, NewUi.PANEL_B);

        // header
        c.drawTexture(RenderPipelines.GUI_TEXTURED, MARK, mark[0], mark[1], 0, 0, mark[2], mark[3],
                MARK_TEX, MARK_TEX, MARK_TEX, MARK_TEX);
        int tx = mark[0] + mark[2] + sl(18);
        c.drawText(textRenderer, Text.literal("FHASIX").formatted(Formatting.BOLD), tx,
                mark[1] + mark[3] / 2 - textRenderer.fontHeight - sl(2), NewUi.TXT, false);
        c.drawText(textRenderer, "Mod launcher", tx, mark[1] + mark[3] / 2 + sl(4), NewUi.TXT_DIM, false);

        boolean ch = NewUi.in(mx, my, closeBtn[0], closeBtn[1], closeBtn[2], closeBtn[3]);
        NewUi.frame(c, closeBtn[0], closeBtn[1], closeBtn[2], closeBtn[3], rad, NewUi.BTN_BORDER,
                ch ? NewUi.HOV_T : NewUi.BTN_T, ch ? NewUi.HOV_B : NewUi.BTN_B);
        int cx = closeBtn[0] + closeBtn[2] / 2;
        int cy = closeBtn[1] + closeBtn[3] / 2;
        int s = Math.max(2, closeBtn[3] / 6);
        for (int t = -s; t <= s; t++) {
            c.fill(cx + t, cy + t, cx + t + 2, cy + t + 1, NewUi.TXT);
            c.fill(cx + t, cy - t, cx + t + 2, cy - t + 1, NewUi.TXT);
        }
        c.fill(dividerX1, dividerY, dividerX2, dividerY + 1, NewUi.BTN_BORDER);

        // primary button
        boolean ph = NewUi.in(mx, my, primary[0], primary[1], primary[2], primary[3]);
        NewUi.rrect(c, primary[0], primary[1], primary[2], primary[3], rad,
                ph ? NewUi.RED_HT : NewUi.RED_T, ph ? NewUi.RED_HB : NewUi.RED_B);
        Text label = Text.literal("OPEN MOD MENU").formatted(Formatting.BOLD);
        int ico = Math.max(9, sl(28));
        int lw = textRenderer.getWidth(label);
        int total = ico + sl(10) + lw;
        int gx = primary[0] + (primary[2] - total) / 2;
        NewUi.icon(c, NewUi.ICON_GRID, gx, primary[1] + (primary[3] - ico) / 2, ico);
        c.drawText(textRenderer, label, gx + ico + sl(10), primary[1] + (primary[3] - textRenderer.fontHeight) / 2, NewUi.TXT, false);

        // placeholder icon buttons (hover only)
        for (int i = 0; i < ICONS.length; i++) {
            int[] r = iconBtn[i];
            boolean h = NewUi.in(mx, my, r[0], r[1], r[2], r[3]);
            NewUi.frame(c, r[0], r[1], r[2], r[3], rad, NewUi.BTN_BORDER, h ? NewUi.HOV_T : NewUi.BTN_T, h ? NewUi.HOV_B : NewUi.BTN_B);
            int is = Math.max(8, Math.round(r[3] * 0.5f));
            NewUi.whole(c, ICON_TEX[i], r[0] + (r[2] - is) / 2, r[1] + (r[3] - is) / 2, is);
        }

        // HUD elements: crisp on top of the blur, with an outline so they read as draggable
        List<NewHud.Box> boxes = NewHud.boxes(client, width, height);
        for (NewHud.Box b : boxes) {
            NewHud.drawBox(c, client, b);
            boolean active = b.id.equals(dragId) || (dragId == null && b.contains(mx, my));
            outline(c, b.x - 1, b.y - 1, b.w + 2, b.h + 2, active ? 0xFFFFFFFF : 0x88FFFFFF);
        }

        String hint = boxes.isEmpty()
                ? "Enable a HUD mod to place it on screen   -   Right Shift to close"
                : "Drag the HUD boxes to move them   -   Right Shift to close";
        c.drawText(textRenderer, hint, (width - textRenderer.getWidth(hint)) / 2,
                panel[1] + panel[3] + sl(26), NewUi.TXT_DIM, false);
    }

    private static void outline(DrawContext c, int x, int y, int w, int h, int color) {
        c.fill(x, y, x + w, y + 1, color);
        c.fill(x, y + h - 1, x + w, y + h, color);
        c.fill(x, y, x + 1, y + h, color);
        c.fill(x + w - 1, y, x + w, y + h, color);
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (click.buttonInfo().button() != 0) {
            return true;
        }
        double mx = click.x();
        double my = click.y();

        // HUD boxes are drawn on top, so they get the click first
        List<NewHud.Box> boxes = NewHud.boxes(client, width, height);
        for (int i = boxes.size() - 1; i >= 0; i--) {
            NewHud.Box b = boxes.get(i);
            if (b.contains(mx, my)) {
                dragId = b.id;
                grabX = (int) mx - b.x;
                grabY = (int) my - b.y;
                dragW = b.w;
                dragH = b.h;
                return true;
            }
        }
        if (NewUi.in(mx, my, closeBtn[0], closeBtn[1], closeBtn[2], closeBtn[3])) {
            client.setScreen(null);
            return true;
        }
        if (NewUi.in(mx, my, primary[0], primary[1], primary[2], primary[3])) {
            client.setScreen(new NewModMenuScreen(null));
            return true;
        }
        return true;
    }

    @Override
    public boolean mouseDragged(Click click, double offsetX, double offsetY) {
        if (dragId != null) {
            int x = Math.max(0, Math.min(width - dragW, (int) click.x() - grabX));
            int y = Math.max(0, Math.min(height - dragH, (int) click.y() - grabY));
            NewModSettings.setPosition(client, dragId, x / (float) width, y / (float) height);
            return true;
        }
        return super.mouseDragged(click, offsetX, offsetY);
    }

    @Override
    public boolean mouseReleased(Click click) {
        if (dragId != null) {
            dragId = null;
            NewModSettings.save(client);
            return true;
        }
        return super.mouseReleased(click);
    }

    @Override
    public boolean keyPressed(KeyInput keyEvent) {
        // 256 = Escape, 344 = Right Shift
        if (keyEvent.key() == 256 || keyEvent.key() == 344) {
            client.setScreen(null);
            return true;
        }
        return super.keyPressed(keyEvent);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
