package com.fhasix.uitheme;

import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/** Shown by No Crash instead of closing the game. */
public final class NewCrashScreen extends Screen {

    private static final int DW = 1394;
    private static final int DH = 847;
    private static final int PW = 620;
    private static final int PH = 330;

    private final String detail;
    private final boolean showDetail;
    private float k;
    private int ox;
    private int oy;
    private int[] panel;
    private int[] button;

    NewCrashScreen(String detail, boolean showDetail) {
        super(Text.literal("Your game has crashed!"));
        this.detail = detail;
        this.showDetail = showDetail;
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
        button = rect(px + 40, py + PH - 40 - 72, PW - 80, 72);
    }

    private void toTitle() {
        NewNoCrash.reset();
        client.setScreen(new TitleScreen());
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        // own background in render()
    }

    @Override
    public void render(DrawContext c, int mx, int my, float delta) {
        c.fillGradient(0, 0, width, height, 0xFF0B0C0F, 0xFF190A0C);
        NewUi.frame(c, panel[0], panel[1], panel[2], panel[3], Math.max(3, sl(16)), NewUi.CARD_BORDER, NewUi.PANEL_T, NewUi.PANEL_B);

        int pad = sl(40);
        int y = panel[1] + sl(44);
        c.drawText(textRenderer, Text.literal("Your game has crashed!").formatted(Formatting.BOLD),
                panel[0] + pad, y, 0xFFE73C40, false);
        y += textRenderer.fontHeight + sl(14);
        c.drawText(textRenderer, "No Crash caught the error so the game stayed open.", panel[0] + pad, y, NewUi.TXT, false);
        y += textRenderer.fontHeight + sl(10);
        if (showDetail) {
            int max = panel[2] - pad * 2;
            String d = textRenderer.getWidth(detail) > max
                    ? textRenderer.trimToWidth(detail, Math.max(0, max - textRenderer.getWidth(".."))) + ".."
                    : detail;
            c.drawText(textRenderer, d, panel[0] + pad, y, NewUi.TXT_DIM, false);
            y += textRenderer.fontHeight + sl(6);
            c.drawText(textRenderer, "Full details are in the game log.", panel[0] + pad, y, NewUi.TXT_DIM, false);
        }

        boolean h = NewUi.in(mx, my, button[0], button[1], button[2], button[3]);
        NewUi.rrect(c, button[0], button[1], button[2], button[3], Math.max(3, sl(10)),
                h ? NewUi.RED_HT : NewUi.RED_T, h ? NewUi.RED_HB : NewUi.RED_B);
        Text label = Text.literal("RETURN TO TITLE SCREEN").formatted(Formatting.BOLD);
        c.drawText(textRenderer, label, button[0] + (button[2] - textRenderer.getWidth(label)) / 2,
                button[1] + (button[3] - textRenderer.fontHeight) / 2, NewUi.TXT, false);
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (click.buttonInfo().button() == 0
                && NewUi.in(click.x(), click.y(), button[0], button[1], button[2], button[3])) {
            toTitle();
        }
        return true;
    }

    @Override
    public boolean keyPressed(KeyInput keyEvent) {
        if (keyEvent.key() == 256 || keyEvent.key() == 257) { // Esc or Enter
            toTitle();
            return true;
        }
        return super.keyPressed(keyEvent);
    }

    @Override
    public void close() {
        toTitle();
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return true;
    }
}
