package com.fhasix.uitheme;

import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;

/**
 * NEW. The settings screen of one mod (opened from the gear button on its card in the Mod Menu).
 * Built from the option list in NewModOptions: toggles, sliders, choices, colour swatches and a full colour picker.
 * Every change applies instantly and is saved. Same layout approach as the Mod Menu: positions
 * come from the 1394x847 design scaled by NewUi.scale, drawn with the cheap NewUi helpers.
 */
public final class NewModOptionsScreen extends Screen {

    private static final int DW = 1394;
    private static final int DH = 847;
    private static final int PW = 700;
    private static final int HEAD_H = 100;
    private static final int MAX_ROW_H = 64;
    private static final int PAD_B = 28;

    private final NewModMenuScreen menu;
    private final NewModSettings.ModDef mod;
    private final List<NewModOptions.Opt> opts;

    private float k;
    private int ox;
    private int oy;
    private int[] panel;
    private int[] backBtn;
    private int[][] rowRect;
    private int[][] ctl;
    private int sliderDrag = -1;
    private int rowH = MAX_ROW_H;
    /** Colour picker rows are taller than a normal row (1 = one normal row). */
    private static final float PICKER_WEIGHT = 3.5f;
    private int[][] svRect;
    private int[][] hueRect;
    private int[][] swRect;
    private float[][] hsv;
    /** 0 = none, 1 = dragging the saturation/brightness square, 2 = dragging the hue bar. */
    private int pickDrag = 0;
    private int pickRow = -1;

    NewModOptionsScreen(NewModMenuScreen menu, NewModSettings.ModDef mod) {
        super(Text.literal(mod.name + " settings"));
        this.menu = menu;
        this.mod = mod;
        this.opts = NewModOptions.forMod(mod.id);
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
        int n = Math.max(1, opts.size());
        float totalW = 0f;
        for (NewModOptions.Opt o : opts) {
            totalW += o.type == NewModOptions.PICKER ? PICKER_WEIGHT : 1f;
        }
        totalW = Math.max(1f, totalW);
        rowH = Math.min(MAX_ROW_H, (int) ((DH - 40 - HEAD_H - PAD_B) / totalW));
        int body = 0;
        for (NewModOptions.Opt o : opts) {
            body += o.type == NewModOptions.PICKER ? Math.round(PICKER_WEIGHT * rowH) : rowH;
        }
        if (opts.isEmpty()) {
            body = rowH;
        }
        int ph = HEAD_H + body + PAD_B;
        int px = (DW - PW) / 2;
        int py = (DH - ph) / 2;
        panel = rect(px, py, PW, ph);
        backBtn = rect(px + PW - 28 - 120, py + 28, 120, 46);
        rowRect = new int[n][];
        ctl = new int[n][];
        svRect = new int[n][];
        hueRect = new int[n][];
        swRect = new int[n][];
        hsv = new float[n][];
        int right = px + PW - 48;
        int yOff = 0;
        for (int i = 0; i < n; i++) {
            int ry = py + HEAD_H + yOff;
            boolean pick = opts.get(i).type == NewModOptions.PICKER;
            int rh = pick ? Math.round(PICKER_WEIGHT * rowH) : rowH;
            yOff += rh;
            rowRect[i] = rect(px + 24, ry, PW - 48, rh - 10);
            switch (opts.get(i).type) {
                case NewModOptions.PICKER: {
                    int size = Math.max(40, rh - 10 - 24);
                    int hueW = 26;
                    int hueX = right - hueW;
                    int svX = hueX - 14 - size;
                    svRect[i] = rect(svX, ry + 12, size, size);
                    hueRect[i] = rect(hueX, ry + 12, hueW, size);
                    swRect[i] = rect(px + 48, ry + 62, 150, 56);
                    ctl[i] = svRect[i];
                    hsv[i] = rgbToHsv(NewModSettings.opt(client, mod.id, opts.get(i).key));
                    break;
                }
                case NewModOptions.TOGGLE:
                    ctl[i] = rect(right - 132, ry + (rowH - 10 - 38) / 2, 132, 38);
                    break;
                case NewModOptions.CHOICE:
                    ctl[i] = rect(right - 220, ry + (rowH - 10 - 38) / 2, 220, 38);
                    break;
                case NewModOptions.SLIDER:
                    ctl[i] = rect(right - 76 - 260, ry + (rowH - 10 - 12) / 2, 260, 12);
                    break;
                default:
                    ctl[i] = rect(right - 372, ry + (rowH - 10 - 30) / 2, 372, 30);
                    break;
            }
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

    private void back() {
        client.setScreen(menu);
    }

    private int value(NewModOptions.Opt o) {
        return NewModSettings.opt(client, mod.id, o.key);
    }

    private void set(NewModOptions.Opt o, int v, boolean save) {
        NewModSettings.setOpt(client, mod.id, o.key, v);
        if (save) {
            NewModSettings.save(client);
        }
    }

    // ---------------------------------------------------------------- render

    private void button(DrawContext c, int[] r, boolean hov, int rad) {
        NewUi.rrect(c, r[0], r[1], r[2], r[3], rad, hov ? NewUi.HOV_T : NewUi.BTN_T, hov ? NewUi.HOV_B : NewUi.BTN_B);
    }

    private void centered(DrawContext c, String s, int[] r, int color) {
        c.drawText(textRenderer, s, r[0] + (r[2] - textRenderer.getWidth(s)) / 2,
                r[1] + (r[3] - textRenderer.fontHeight) / 2, color, false);
    }

    @Override
    public void render(DrawContext c, int mx, int my, float delta) {
        if (menu.backdrop() != null) {
            menu.backdrop().render(c, -1000, -1000, delta);
        } else {
            safeBackground(c, mx, my, delta);
        }
        c.fill(0, 0, width, height, menu.backdrop() != null ? 0x42000000 : 0x30000000);

        NewUi.frame(c, panel[0], panel[1], panel[2], panel[3], Math.max(3, sl(14)), NewUi.CARD_BORDER, NewUi.PANEL_T, NewUi.PANEL_B);
        c.drawText(textRenderer, Text.literal(mod.name + " settings").formatted(Formatting.BOLD),
                sx((DW - PW) / 2 + 36), panel[1] + sl(36), NewUi.TXT, false);
        c.drawText(textRenderer, "Changes apply instantly", sx((DW - PW) / 2 + 36), panel[1] + sl(36) + textRenderer.fontHeight + sl(8),
                NewUi.TXT_DIM, false);

        boolean bh = NewUi.in(mx, my, backBtn[0], backBtn[1], backBtn[2], backBtn[3]);
        button(c, backBtn, bh, Math.max(3, sl(10)));
        centered(c, "< Back", backBtn, NewUi.TXT);

        for (int i = 0; i < opts.size(); i++) {
            NewModOptions.Opt o = opts.get(i);
            int[] rr = rowRect[i];
            NewUi.frame(c, rr[0], rr[1], rr[2], rr[3], Math.max(3, sl(10)), NewUi.CARD_BORDER, NewUi.CARD_T, NewUi.CARD_B);
            int labelY = o.type == NewModOptions.PICKER ? rr[1] + sl(20) : rr[1] + (rr[3] - textRenderer.fontHeight) / 2;
            c.drawText(textRenderer, o.label, rr[0] + sl(24), labelY, NewUi.TXT, false);
            drawControl(c, i, o, mx, my);
        }
    }

    private void drawControl(DrawContext c, int i, NewModOptions.Opt o, int mx, int my) {
        int[] r = ctl[i];
        int v = value(o);
        int rad = Math.max(3, sl(8));
        boolean hov = NewUi.in(mx, my, r[0], r[1], r[2], r[3]);
        switch (o.type) {
            case NewModOptions.PICKER: {
                drawPicker(c, i);
                break;
            }
            case NewModOptions.TOGGLE: {
                boolean on = v != 0;
                if (on) {
                    NewUi.rrect(c, r[0], r[1], r[2], r[3], rad, hov ? NewUi.ON_HT : NewUi.ON_T, hov ? NewUi.ON_HB : NewUi.ON_B);
                } else {
                    button(c, r, hov, rad);
                }
                centered(c, on ? "On" : "Off", r, on ? NewUi.TXT_ON : NewUi.TXT_DIM);
                break;
            }
            case NewModOptions.CHOICE: {
                button(c, r, hov, rad);
                centered(c, "< " + o.choices[Math.max(0, Math.min(o.choices.length - 1, v))] + " >", r, NewUi.TXT);
                break;
            }
            case NewModOptions.SLIDER: {
                float t = (v - o.min) / (float) Math.max(1, o.max - o.min);
                NewUi.rrect(c, r[0], r[1], r[2], r[3], Math.max(1, r[3] / 2), NewUi.BTN_T, NewUi.BTN_B);
                int fw = Math.max(r[3], Math.round(r[2] * t));
                NewUi.rrect(c, r[0], r[1], fw, r[3], Math.max(1, r[3] / 2), NewUi.RED_T, NewUi.RED_B);
                int ks = Math.max(6, sl(26));
                int kx = r[0] + Math.round((r[2] - ks) * t);
                NewUi.rrect(c, kx, r[1] + (r[3] - ks) / 2, ks, ks, ks / 2, 0xFFFFFFFF, 0xFFD8DADF);
                String vs = v + o.suffix;
                c.drawText(textRenderer, vs, r[0] + r[2] + sl(18), r[1] + (r[3] - textRenderer.fontHeight) / 2, NewUi.TXT, false);
                break;
            }
            default: {
                int n = NewModOptions.PALETTE.length;
                int sw = (r[2] - (n - 1) * sl(8)) / n;
                for (int j = 0; j < n; j++) {
                    int sxp = r[0] + j * (sw + sl(8));
                    int col = NewModOptions.PALETTE[j];
                    boolean sel = j == v;
                    boolean h = NewUi.in(mx, my, sxp, r[1], sw, r[3]);
                    int rr = Math.max(2, sl(7));
                    if (sel || h) {
                        NewUi.rrect(c, sxp - 1, r[1] - 1, sw + 2, r[3] + 2, rr, sel ? 0xFFFFFFFF : 0xFF7E838B, sel ? 0xFFFFFFFF : 0xFF7E838B);
                        NewUi.rrect(c, sxp + 1, r[1] + 1, sw - 2, r[3] - 2, Math.max(1, rr - 1), col, col);
                    } else {
                        NewUi.rrect(c, sxp, r[1], sw, r[3], rr, col, col);
                    }
                }
                break;
            }
        }
    }

    private void drawPicker(DrawContext c, int i) {
        float[] h = hsv[i];
        int[] sv = svRect[i];
        int[] hb = hueRect[i];
        int[] sw = swRect[i];
        int border = 0xFF2A2D34;

        // saturation (left -> right) / brightness (bottom -> top) square, drawn as vertical strips
        c.fill(sv[0] - 1, sv[1] - 1, sv[0] + sv[2] + 1, sv[1] + sv[3] + 1, border);
        int cw = Math.max(1, sv[2] / 96);
        for (int x = 0; x < sv[2]; x += cw) {
            float s = Math.min(1f, (x + cw / 2f) / sv[2]);
            c.fillGradient(sv[0] + x, sv[1], sv[0] + Math.min(sv[2], x + cw), sv[1] + sv[3], hsvToRgb(h[0], s, 1f), 0xFF000000);
        }
        int cur = hsvToRgb(h[0], h[1], h[2]);
        int mx0 = sv[0] + Math.round(h[1] * (sv[2] - 1));
        int my0 = sv[1] + Math.round((1f - h[2]) * (sv[3] - 1));
        int ring = Math.max(5, sl(18));
        NewUi.rrect(c, mx0 - ring / 2, my0 - ring / 2, ring, ring, ring / 2, 0xFFFFFFFF, 0xFFFFFFFF);
        NewUi.rrect(c, mx0 - ring / 2 + 2, my0 - ring / 2 + 2, ring - 4, ring - 4, Math.max(1, ring / 2 - 2), cur, cur);

        // hue bar: red -> yellow -> green -> cyan -> blue -> magenta -> red
        c.fill(hb[0] - 1, hb[1] - 1, hb[0] + hb[2] + 1, hb[1] + hb[3] + 1, border);
        for (int seg = 0; seg < 6; seg++) {
            int y0 = hb[1] + hb[3] * seg / 6;
            int y1 = hb[1] + hb[3] * (seg + 1) / 6;
            c.fillGradient(hb[0], y0, hb[0] + hb[2], y1, hsvToRgb(seg / 6f, 1f, 1f), hsvToRgb((seg + 1) / 6f, 1f, 1f));
        }
        int hy = hb[1] + Math.round(h[0] * (hb[3] - 1));
        int mh = Math.max(3, sl(6));
        c.fill(hb[0] - 3, hy - mh / 2 - 1, hb[0] + hb[2] + 3, hy - mh / 2 + mh + 1, 0xFF000000);
        c.fill(hb[0] - 2, hy - mh / 2, hb[0] + hb[2] + 2, hy - mh / 2 + mh, 0xFFFFFFFF);

        // current colour + hex
        NewUi.frame(c, sw[0], sw[1], sw[2], sw[3], Math.max(2, sl(8)), border, cur, cur);
        String hex = String.format(java.util.Locale.ROOT, "#%06X", cur & 0xFFFFFF);
        c.drawText(textRenderer, hex, sw[0], sw[1] + sw[3] + sl(14), NewUi.TXT_DIM, false);
    }

    private static float clamp01(double v) {
        return (float) Math.max(0.0, Math.min(1.0, v));
    }

    private static int hsvToRgb(float h, float s, float v) {
        h = h - (float) Math.floor(h);
        float hh = h * 6f;
        int i = (int) hh;
        float f = hh - i;
        float p = v * (1f - s);
        float q = v * (1f - s * f);
        float t = v * (1f - s * (1f - f));
        float r;
        float g;
        float b;
        switch (i % 6) {
            case 0: r = v; g = t; b = p; break;
            case 1: r = q; g = v; b = p; break;
            case 2: r = p; g = v; b = t; break;
            case 3: r = p; g = q; b = v; break;
            case 4: r = t; g = p; b = v; break;
            default: r = v; g = p; b = q; break;
        }
        return 0xFF000000 | (Math.round(r * 255f) << 16) | (Math.round(g * 255f) << 8) | Math.round(b * 255f);
    }

    private static float[] rgbToHsv(int rgb) {
        float r = ((rgb >> 16) & 255) / 255f;
        float g = ((rgb >> 8) & 255) / 255f;
        float b = (rgb & 255) / 255f;
        float max = Math.max(r, Math.max(g, b));
        float min = Math.min(r, Math.min(g, b));
        float d = max - min;
        float hue;
        if (d <= 0f) {
            hue = 0f;
        } else if (max == r) {
            hue = ((g - b) / d) % 6f;
        } else if (max == g) {
            hue = (b - r) / d + 2f;
        } else {
            hue = (r - g) / d + 4f;
        }
        hue /= 6f;
        if (hue < 0f) {
            hue += 1f;
        }
        return new float[]{hue, max <= 0f ? 0f : d / max, max};
    }

    private void applyPick(int i) {
        float[] h = hsv[i];
        set(opts.get(i), hsvToRgb(h[0], h[1], h[2]) & 0xFFFFFF, false);
    }

    private void pickSv(int i, double mx, double my) {
        int[] r = svRect[i];
        hsv[i][1] = clamp01((mx - r[0]) / Math.max(1, r[2] - 1));
        hsv[i][2] = 1f - clamp01((my - r[1]) / Math.max(1, r[3] - 1));
        applyPick(i);
    }

    private void pickHue(int i, double my) {
        int[] r = hueRect[i];
        hsv[i][0] = clamp01((my - r[1]) / Math.max(1, r[3] - 1));
        applyPick(i);
    }

    // ---------------------------------------------------------------- input

    private void sliderTo(int i, double mx) {
        NewModOptions.Opt o = opts.get(i);
        int[] r = ctl[i];
        float t = Math.max(0f, Math.min(1f, (float) ((mx - r[0]) / r[2])));
        int step = Math.max(1, o.step);
        int steps = Math.round(t * (o.max - o.min) / (float) step);
        set(o, Math.max(o.min, Math.min(o.max, o.min + steps * step)), false);
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (click.buttonInfo().button() != 0) {
            return true;
        }
        double mx = click.x();
        double my = click.y();
        if (NewUi.in(mx, my, backBtn[0], backBtn[1], backBtn[2], backBtn[3])) {
            back();
            return true;
        }
        for (int i = 0; i < opts.size(); i++) {
            NewModOptions.Opt o = opts.get(i);
            if (o.type == NewModOptions.PICKER) {
                int[] hb = hueRect[i];
                if (NewUi.in(mx, my, hb[0] - sl(8), hb[1] - sl(8), hb[2] + sl(16), hb[3] + sl(16))) {
                    pickDrag = 2;
                    pickRow = i;
                    pickHue(i, my);
                    return true;
                }
                int[] sv = svRect[i];
                if (NewUi.in(mx, my, sv[0] - sl(6), sv[1] - sl(6), sv[2] + sl(12), sv[3] + sl(12))) {
                    pickDrag = 1;
                    pickRow = i;
                    pickSv(i, mx, my);
                    return true;
                }
                continue;
            }
            int[] r = ctl[i];
            int pad = o.type == NewModOptions.SLIDER ? sl(14) : 0;
            if (!NewUi.in(mx, my, r[0] - pad, r[1] - pad, r[2] + pad * 2, r[3] + pad * 2)) {
                continue;
            }
            int v = value(o);
            switch (o.type) {
                case NewModOptions.TOGGLE:
                    set(o, v == 0 ? 1 : 0, true);
                    break;
                case NewModOptions.CHOICE:
                    set(o, (v + 1) % o.choices.length, true);
                    break;
                case NewModOptions.SLIDER:
                    sliderDrag = i;
                    sliderTo(i, mx);
                    break;
                default: {
                    int n = NewModOptions.PALETTE.length;
                    int sw = (r[2] - (n - 1) * sl(8)) / n;
                    for (int j = 0; j < n; j++) {
                        int sxp = r[0] + j * (sw + sl(8));
                        if (NewUi.in(mx, my, sxp - sl(4), r[1], sw + sl(8), r[3])) {
                            set(o, j, true);
                            break;
                        }
                    }
                    break;
                }
            }
            return true;
        }
        return true;
    }

    @Override
    public boolean mouseDragged(Click click, double offsetX, double offsetY) {
        if (pickDrag != 0 && pickRow >= 0) {
            if (pickDrag == 1) {
                pickSv(pickRow, click.x(), click.y());
            } else {
                pickHue(pickRow, click.y());
            }
            return true;
        }
        if (sliderDrag >= 0) {
            sliderTo(sliderDrag, click.x());
            return true;
        }
        return super.mouseDragged(click, offsetX, offsetY);
    }

    @Override
    public boolean mouseReleased(Click click) {
        if (pickDrag != 0) {
            pickDrag = 0;
            pickRow = -1;
            NewModSettings.save(client);
            return true;
        }
        if (sliderDrag >= 0) {
            sliderDrag = -1;
            NewModSettings.save(client);
            return true;
        }
        return super.mouseReleased(click);
    }

    @Override
    public boolean keyPressed(KeyInput keyEvent) {
        if (keyEvent.key() == 256) {
            back();
            return true;
        }
        return super.keyPressed(keyEvent);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
