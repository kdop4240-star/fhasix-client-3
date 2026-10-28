package com.fhasix.uitheme;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/**
 * A rounded-corner, gradient-filled text button. Rendering itself lives in
 * a1 (shared with c3/d4) - this class just picks
 * normal vs hover colors (instant swap, no fade) and draws the label on top.
 * Opacity defaults to fully solid; the Account pill uses the translucent
 * overload to match the icon row's 90%-opacity look.
 */
public class b2 extends ButtonWidget {

    public static final int[] NAVY_NORMAL_TOP_LEFT     = a1.NAVY_NORMAL_TOP_LEFT;
    public static final int[] NAVY_NORMAL_BOTTOM_RIGHT = a1.NAVY_NORMAL_BOTTOM_RIGHT;
    public static final int[] NAVY_HOVER_TOP_LEFT      = a1.NAVY_HOVER_TOP_LEFT;
    public static final int[] NAVY_HOVER_BOTTOM_RIGHT  = a1.NAVY_HOVER_BOTTOM_RIGHT;
    public static final int[] RED_NORMAL_LEFT  = a1.RED_NORMAL_LEFT;
    public static final int[] RED_NORMAL_RIGHT = a1.RED_NORMAL_RIGHT;
    public static final int[] RED_HOVER_LEFT   = a1.RED_HOVER_LEFT;
    public static final int[] RED_HOVER_RIGHT  = a1.RED_HOVER_RIGHT;

    private final int[] normalLeft;
    private final int[] normalRight;
    private final int[] hoverLeft;
    private final int[] hoverRight;
    private final int alpha;

    public b2(int x, int y, int width, int height, net.minecraft.text.Text message, PressAction onPress) {
        this(x, y, width, height, message, onPress,
                NAVY_NORMAL_TOP_LEFT, NAVY_NORMAL_BOTTOM_RIGHT, NAVY_HOVER_TOP_LEFT, NAVY_HOVER_BOTTOM_RIGHT, 255);
    }

    public b2(int x, int y, int width, int height, net.minecraft.text.Text message, PressAction onPress,
                                  int[] normalLeft, int[] normalRight, int[] hoverLeft, int[] hoverRight) {
        this(x, y, width, height, message, onPress, normalLeft, normalRight, hoverLeft, hoverRight, 255);
    }

    public b2(int x, int y, int width, int height, net.minecraft.text.Text message, PressAction onPress,
                                  int[] normalLeft, int[] normalRight, int[] hoverLeft, int[] hoverRight, int alpha) {
        super(x, y, width, height, message, onPress, DEFAULT_NARRATION_SUPPLIER);
        this.normalLeft = normalLeft;
        this.normalRight = normalRight;
        this.hoverLeft = hoverLeft;
        this.hoverRight = hoverRight;
        this.alpha = alpha;
    }

    @Override
    public void drawIcon(DrawContext context, int mouseX, int mouseY, float delta) {
        boolean hovered = this.isHovered();
        int[] topLeft = hovered ? hoverLeft : normalLeft;
        int[] bottomRight = hovered ? hoverRight : normalRight;
        a1.drawRoundedGradientRect(context, this.getX(), this.getY(), this.getWidth(), this.getHeight(),
                topLeft, bottomRight, alpha);
    }

}
