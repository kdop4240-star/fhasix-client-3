package com.fhasix.uitheme;

import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

import java.util.List;

/**
 * NEW. "What's your device?" - shown once, on the title screen, the first time the game is launched
 * (and again from Performance > Device). Three cards: High End, Medium, Low End (tagged "Best for Pojav
 * Launcher, Mojo Launcher, mobile"). Pick one, press Confirm - the button plays a smooth ripple - and
 * NewPerf.applyDevice sets Minecraft's video options and the FPS modes for that device.
 */
public final class NewDeviceSelectorScreen extends Screen {

    private static final int DW = 1394;
    private static final int DH = 847;
    private static final Identifier LOGO = Identifier.of("fhasixuitheme", "textures/gui/title/fhasix_logo.png");
    private static final int LOGO_W = 1251;
    private static final int LOGO_H = 323;

    private static final String[] NAMES = {"High End", "Medium", "Low End"};
    private static final String[] LINES = {
            "Gaming PC or a strong computer. Minecraft's default settings, with the FPS modes set to Small.",
            "Laptop or a mid-range phone. Render distance 6, lighter settings and the FPS modes set to Medium.",
            "Old PC or a budget phone. The lowest of everything and every FPS mode set to High for the most FPS."
    };
    private static final String[] TAGS = {"Default Minecraft settings", "Render distance 6", "Best for Pojav, Mojo & mobile"};

    private static final long RIPPLE_NS = 560_000_000L;
    private static final long APPLY_AT_NS = 430_000_000L;

    private final Screen parent;
    private float k;
    private int ox;
    private int oy;
    private final int[][] cardR = new int[3][];
    private int[] confirmR;
    private int selected = -1; // 0..2
    private final float[] selT = new float[3];
    private final float[] hovT = new float[3];
    private float confirmT = 0f;
    private long rippleStart = -1L;
    private int rippleX;
    private int rippleY;
    private boolean applied = false;
    private long lastNs = System.nanoTime();

    /** parent = where to go afterwards (null = the title screen, i.e. first launch). */
    NewDeviceSelectorScreen(Screen parent) {
        super(Text.literal("Device"));
        this.parent = parent;
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
        int cw = 380;
        int gap = 30;
        int x0 = (DW - (3 * cw + 2 * gap)) / 2;
        for (int i = 0; i < 3; i++) {
            cardR[i] = rect(x0 + i * (cw + gap), 226, cw, 420);
        }
        confirmR = rect((DW - 420) / 2, 676, 420, 70);
    }

    /** Intentionally empty: the background is drawn once from render() (a second blur in the same frame crashes). */

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
    public boolean shouldCloseOnEsc() {
        return parent != null; // the very first launch has to pick a device
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    private void finish() {
        client.setScreen(parent != null ? parent : new TitleScreen());
    }

    private void centerText(DrawContext c, Text t, int cx, int y, int color) {
        c.drawText(textRenderer, t, cx - textRenderer.getWidth(t) / 2, y, color, false);
    }

    @Override
    public void render(DrawContext c, int mx, int my, float delta) {
        long now = System.nanoTime();
        float dt = Math.min(0.1f, (now - lastNs) / 1_000_000_000f);
        lastNs = now;
        safeBackground(c, mx, my, delta);
        c.fill(0, 0, width, height, 0xD00A0B0E);

        // ripple finished -> apply the device and leave
        if (rippleStart >= 0 && !applied && now - rippleStart >= APPLY_AT_NS) {
            applied = true;
            NewPerf.applyDevice(client, selected + 1);
            finish();
            return;
        }

        int lw = sl(360);
        int lh = Math.round(lw * (float) LOGO_H / LOGO_W);
        c.drawTexture(RenderPipelines.GUI_TEXTURED, LOGO, (width - lw) / 2, sy(34), 0, 0, lw, lh, LOGO_W, LOGO_H, LOGO_W, LOGO_H);
        centerText(c, Text.literal("What's your device?").formatted(Formatting.BOLD), width / 2, sy(150), NewUi.TXT);
        centerText(c, Text.literal("This sets Minecraft's video settings and the Fhasix FPS modes for you."),
                width / 2, sy(150) + textRenderer.fontHeight + sl(10), NewUi.TXT_DIM);

        float ease = Math.min(1f, dt * 12f);
        for (int i = 0; i < 3; i++) {
            int[] r = cardR[i];
            boolean hov = rippleStart < 0 && NewUi.in(mx, my, r[0], r[1], r[2], r[3]);
            selT[i] += ((i == selected ? 1f : 0f) - selT[i]) * ease;
            hovT[i] += ((hov ? 1f : 0f) - hovT[i]) * ease;
            int lift = Math.round((selT[i] * 8f + hovT[i] * 3f) * k);
            int y = r[1] - lift;
            int border = NewUi.lerp(NewUi.CARD_BORDER, NewUi.RED_T, selT[i]);
            NewUi.frame(c, r[0], y, r[2], r[3], Math.max(3, sl(14)), border,
                    NewUi.lerp(NewUi.CARD_T, NewUi.HOV_T, Math.max(hovT[i] * 0.6f, selT[i] * 0.35f)),
                    NewUi.lerp(NewUi.CARD_B, NewUi.HOV_B, Math.max(hovT[i] * 0.6f, selT[i] * 0.35f)));

            int is = sl(120);
            NewUi.icon(c, 34 + i, r[0] + (r[2] - is) / 2, y + sl(30), is);
            int cx = r[0] + r[2] / 2;
            centerText(c, Text.literal(NAMES[i]).formatted(Formatting.BOLD), cx, y + sl(166), NewUi.TXT);
            List<OrderedText> lines = textRenderer.wrapLines(Text.literal(LINES[i]), r[2] - sl(44));
            for (int l = 0; l < Math.min(5, lines.size()); l++) {
                OrderedText ot = lines.get(l);
                c.drawText(textRenderer, ot, cx - textRenderer.getWidth(ot) / 2,
                        y + sl(196) + l * (textRenderer.fontHeight + 2), NewUi.TXT_DIM, false);
            }
            // tag pill at the bottom of the card
            String tag = TAGS[i];
            int tw = textRenderer.getWidth(tag) + sl(26);
            int th = textRenderer.fontHeight + sl(14);
            int tx = cx - tw / 2;
            int ty = y + r[3] - th - sl(24);
            if (i == 2) {
                NewUi.rrect(c, tx, ty, tw, th, th / 2, NewUi.ON_T, NewUi.ON_B);
                c.drawText(textRenderer, tag, tx + (tw - textRenderer.getWidth(tag)) / 2, ty + (th - textRenderer.fontHeight) / 2,
                        NewUi.TXT_ON, false);
            } else {
                NewUi.rrect(c, tx, ty, tw, th, th / 2, NewUi.BTN_T, NewUi.BTN_B);
                c.drawText(textRenderer, tag, tx + (tw - textRenderer.getWidth(tag)) / 2, ty + (th - textRenderer.fontHeight) / 2,
                        NewUi.TXT_DIM, false);
            }
            // selected check
            if (selT[i] > 0.05f) {
                int cs = sl(34);
                int a = Math.round(255 * Math.min(1f, selT[i]));
                NewUi.rrect(c, r[0] + r[2] - cs - sl(16), y + sl(16), cs, cs, cs / 2, (a << 24) | 0xE73C40, (a << 24) | 0xCD2E32);
                int ccx = r[0] + r[2] - cs / 2 - sl(16);
                int ccy = y + sl(16) + cs / 2;
                int u = Math.max(1, cs / 8);
                for (int t = 0; t < 3; t++) {
                    c.fill(ccx - 3 * u + t * u, ccy + t * u, ccx - 2 * u + t * u, ccy + u + t * u, (a << 24) | 0xFFFFFF);
                }
                for (int t = 0; t < 5; t++) {
                    c.fill(ccx + t * u, ccy + 2 * u - t * u, ccx + u + t * u, ccy + 3 * u - t * u, (a << 24) | 0xFFFFFF);
                }
            }
        }

        // confirm button with ripple
        boolean can = selected >= 0;
        confirmT += ((can ? 1f : 0f) - confirmT) * ease;
        boolean ch = can && rippleStart < 0 && NewUi.in(mx, my, confirmR[0], confirmR[1], confirmR[2], confirmR[3]);
        int rad = Math.max(3, sl(14));
        NewUi.rrect(c, confirmR[0], confirmR[1], confirmR[2], confirmR[3], rad,
                NewUi.lerp(NewUi.BTN_T, ch ? NewUi.RED_HT : NewUi.RED_T, confirmT), NewUi.lerp(NewUi.BTN_B, NewUi.RED_B, confirmT));
        if (rippleStart >= 0) {
            float t = Math.min(1f, (now - rippleStart) / (float) RIPPLE_NS);
            float e = 1f - (1f - t) * (1f - t) * (1f - t);
            int diag = (int) Math.hypot(confirmR[2], confirmR[3]) + 4;
            int r = Math.round(e * diag);
            int a = Math.round(120 * (1f - t));
            c.enableScissor(confirmR[0], confirmR[1], confirmR[0] + confirmR[2], confirmR[1] + confirmR[3]);
            NewUi.rrect(c, rippleX - r, rippleY - r, r * 2, r * 2, r, (a << 24) | 0xFFFFFF, (a << 24) | 0xFFFFFF);
            c.disableScissor();
        }
        String label = rippleStart >= 0 ? "Setting up..." : (can ? "Confirm" : "Choose a device");
        c.drawText(textRenderer, Text.literal(label).formatted(Formatting.BOLD),
                confirmR[0] + (confirmR[2] - textRenderer.getWidth(label)) / 2,
                confirmR[1] + (confirmR[3] - textRenderer.fontHeight) / 2, can ? NewUi.TXT : NewUi.TXT_DIM, false);
        centerText(c, Text.literal("You can change this later: Mod Menu > gear (top bar) > device icon"),
                width / 2, confirmR[1] + confirmR[3] + sl(24), NewUi.TXT_DIM);
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (click.buttonInfo().button() != 0 || rippleStart >= 0) {
            return true;
        }
        double mx = click.x();
        double my = click.y();
        for (int i = 0; i < 3; i++) {
            int[] r = cardR[i];
            if (NewUi.in(mx, my, r[0], r[1] - sl(10), r[2], r[3] + sl(10))) {
                selected = i;
                return true;
            }
        }
        if (selected >= 0 && NewUi.in(mx, my, confirmR[0], confirmR[1], confirmR[2], confirmR[3])) {
            rippleStart = System.nanoTime();
            rippleX = (int) mx;
            rippleY = (int) my;
        }
        return true;
    }

    @Override
    public boolean keyPressed(KeyInput keyEvent) {
        if (keyEvent.key() == 256 && parent != null) {
            finish();
            return true;
        }
        return super.keyPressed(keyEvent);
    }
}
