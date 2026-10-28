package com.fhasix.uitheme;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/**
 * The "Quit Game" button. Same rounded corners as everything else, but its
 * own look: a translucent dark red-brown base (matching the reference's
 * subtle, semi-transparent style) that a solid red wash fades in over on
 * hover - and fades back out on mouse-leave. This is the one button in the
 * mod that still animates its hover (explicitly requested); everything
 * else uses the instant swap.
 */
public class d4 extends ButtonWidget {

    private static final long FADE_MS = 300L;

    private static final int[] BASE_TOP_LEFT     = {44, 27, 27};
    private static final int[] BASE_BOTTOM_RIGHT = {34, 20, 20};
    private static final int BASE_ALPHA = 217; // ~85% - translucent, matches the reference

    private static final int[] HOVER_RED = {180, 40, 45};
    private static final int HOVER_MAX_ALPHA = 130;

    private static final int TEXT_COLOR = 0xFFE13B3E;

    private float hoverProgress = 0f;
    private long lastFrameMs = -1L;

    public d4(int x, int y, int width, int height, net.minecraft.text.Text message, PressAction onPress) {
        super(x, y, width, height, message, onPress, DEFAULT_NARRATION_SUPPLIER);
    }

    @Override
    public void drawIcon(DrawContext context, int mouseX, int mouseY, float delta) {
        long now = System.currentTimeMillis();
        if (lastFrameMs < 0) lastFrameMs = now;
        long dtMs = now - lastFrameMs;
        lastFrameMs = now;

        float target = this.isHovered() ? 1f : 0f;
        float step = dtMs / (float) FADE_MS;
        if (hoverProgress < target) hoverProgress = Math.min(target, hoverProgress + step);
        else if (hoverProgress > target) hoverProgress = Math.max(target, hoverProgress - step);

        int x = this.getX(), y = this.getY(), w = this.getWidth(), h = this.getHeight();
        a1.drawRoundedGradientRect(context, x, y, w, h, BASE_TOP_LEFT, BASE_BOTTOM_RIGHT, BASE_ALPHA);
        if (hoverProgress > 0f) {
            int overlayAlpha = Math.round(hoverProgress * HOVER_MAX_ALPHA);
            a1.drawRoundedGradientRect(context, x, y, w, h, HOVER_RED, HOVER_RED, overlayAlpha);
        }
    }

}
