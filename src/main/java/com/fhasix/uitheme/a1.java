package com.fhasix.uitheme;

import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;

/**
 * Shared rounded-gradient rectangle rasterizer, used by b2,
 * c3, and d4 so they all share one identical corner/gradient
 * implementation. No textures, no shaders for the shape itself.
 */
public final class a1 {

    public static final float CORNER_RADIUS_FRACTION = 0.108f;
    public static final float GRADIENT_BIAS = 0.72f;

    // Navy theme - Singleplayer / Multiplayer / Partners / Cosmetics / icon row / Account.
    public static final int[] NAVY_NORMAL_TOP_LEFT     = {24, 28, 35};
    public static final int[] NAVY_NORMAL_BOTTOM_RIGHT = {14, 15, 19};
    public static final int[] NAVY_HOVER_TOP_LEFT      = {56, 67, 81};
    public static final int[] NAVY_HOVER_BOTTOM_RIGHT  = {18, 20, 26};

    // Red-to-black theme - Store.
    public static final int[] RED_NORMAL_LEFT  = {221, 53, 56};
    public static final int[] RED_NORMAL_RIGHT = {30, 18, 20};
    public static final int[] RED_HOVER_LEFT   = {235, 80, 83};
    public static final int[] RED_HOVER_RIGHT  = {45, 24, 26};

    /** Off-white used for icon glyphs drawn on top of the navy theme. */
    public static final int ICON_COLOR = 0xFFEDEDED;

    private a1() {
    }

    /** Fully opaque - used by the main center buttons and Store (unchanged look). */
    public static void drawRoundedGradientRect(DrawContext context, int x, int y, int width, int height,
                                                int[] topLeft, int[] bottomRight) {
        drawRoundedGradientRect(context, x, y, width, height, topLeft, bottomRight, 255);
    }

    /** alpha (0-255) scales the whole shape's opacity - used for the translucent top-bar widgets. */
    public static void drawRoundedGradientRect(DrawContext context, int x, int y, int width, int height,
                                                int[] topLeft, int[] bottomRight, int alpha) {
        int r = Math.round(Math.min(height * CORNER_RADIUS_FRACTION, height / 2f));
        drawRoundedGradientRectInternal(context, x, y, width, height, topLeft, bottomRight, alpha, r);
    }

    /** Same renderer with an explicit corner radius for individual UI surfaces. */
    public static void drawRoundedGradientRect(DrawContext context, int x, int y, int width, int height,
                                                int[] topLeft, int[] bottomRight, int alpha, int radius) {
        int r = Math.max(0, Math.min(radius, Math.min(width, height) / 2));
        drawRoundedGradientRectInternal(context, x, y, width, height, topLeft, bottomRight, alpha, r);
    }

    private static void drawRoundedGradientRectInternal(DrawContext context, int x, int y, int width, int height,
                                                        int[] topLeft, int[] bottomRight, int alpha, int r) {
        if (r <= 0) {
            fillGradientColumnRange(context, x, y, 0, width, 0, height, width, height, topLeft, bottomRight, alpha);
            return;
        }

        for (int col = r; col < width - r; col++) {
            fillGradientColumnRange(context, x, y, col, col + 1, 0, height, width, height, topLeft, bottomRight, alpha);
        }
        for (int col = 0; col < r; col++) {
            fillGradientColumnRange(context, x, y, col, col + 1, r, height - r, width, height, topLeft, bottomRight, alpha);
        }
        for (int col = width - r; col < width; col++) {
            fillGradientColumnRange(context, x, y, col, col + 1, r, height - r, width, height, topLeft, bottomRight, alpha);
        }

        drawCorner(context, x, y, 0, 0, r, r, r, width, height, topLeft, bottomRight, alpha);
        drawCorner(context, x, y, width - r, 0, width - r, r, r, width, height, topLeft, bottomRight, alpha);
        drawCorner(context, x, y, 0, height - r, r, height - r, r, width, height, topLeft, bottomRight, alpha);
        drawCorner(context, x, y, width - r, height - r, width - r, height - r, r, width, height, topLeft, bottomRight, alpha);
    }

    /** Gradient color at a given point of a button of this size - lets icon glyphs "erase" back to background. */
    public static int backgroundColorAt(int px, int py, int width, int height, int[] topLeft, int[] bottomRight) {
        float xFrac = px / (float) Math.max(1, width - 1);
        float yFrac = py / (float) height;
        int[] c = gradientAt(xFrac, yFrac, topLeft, bottomRight);
        return argb(255, c);
    }

    private static void fillGradientColumnRange(DrawContext context, int x, int y,
                                                 int colStart, int colEnd, int rowStart, int rowEnd,
                                                 int width, int height, int[] topLeft, int[] bottomRight, int alpha) {
        if (rowEnd <= rowStart || colEnd <= colStart) return;
        float xFrac = (colStart + (colEnd - colStart) / 2f) / Math.max(1, width - 1);
        int[] top = gradientAt(xFrac, rowStart / (float) height, topLeft, bottomRight);
        int[] bottom = gradientAt(xFrac, rowEnd / (float) height, topLeft, bottomRight);
        context.fillGradient(x + colStart, y + rowStart, x + colEnd, y + rowEnd,
                argb(alpha, top), argb(alpha, bottom));
    }

    private static void drawCorner(DrawContext context, int x, int y, int boxX, int boxY,
                                    float ccx, float ccy, int r, int width, int height,
                                    int[] topLeft, int[] bottomRight, int alpha) {
        for (int dy = 0; dy < r; dy++) {
            for (int dx = 0; dx < r; dx++) {
                int px = boxX + dx;
                int py = boxY + dy;
                float sampleX = px + 0.5f;
                float sampleY = py + 0.5f;
                float dist = (float) Math.sqrt((sampleX - ccx) * (sampleX - ccx) + (sampleY - ccy) * (sampleY - ccy));
                float coverage = clamp(r - dist + 0.5f, 0f, 1f);
                if (coverage <= 0f) continue;
                float xFrac = px / (float) Math.max(1, width - 1);
                float yFrac = py / (float) height;
                int[] c = gradientAt(xFrac, yFrac, topLeft, bottomRight);
                int a = Math.round(coverage * alpha);
                context.fill(x + px, y + py, x + px + 1, y + py + 1, argb(a, c));
            }
        }
    }

    public static int[] gradientAt(float xFrac, float yFrac, int[] topLeft, int[] bottomRight) {
        float t = GRADIENT_BIAS * xFrac + (1 - GRADIENT_BIAS) * yFrac;
        t = clamp(t, 0f, 1f);
        return new int[]{
                Math.round(topLeft[0] + (bottomRight[0] - topLeft[0]) * t),
                Math.round(topLeft[1] + (bottomRight[1] - topLeft[1]) * t),
                Math.round(topLeft[2] + (bottomRight[2] - topLeft[2]) * t)
        };
    }

    public static float clamp(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    public static int argb(int a, int[] rgb) {
        return (a << 24) | (rgb[0] << 16) | (rgb[1] << 8) | rgb[2];
    }

    /**
     * Draws a rectangular texture region (optionally a cropped/zoomed sub-window of it, for
     * a hover-zoom effect), fills a flat alpha overlay on top, then "erases" the four
     * rectangular corners back down to the surrounding panel's own gradient color using the
     * same analytic per-pixel coverage math as drawRoundedGradientRect's corners. The GPU
     * still does one plain rectangular blit - only the corner pixels get the extra per-pixel
     * pass, so this is the same cost class as the panel/tab corners already drawn elsewhere.
     *
     * cropU/cropV/cropW/cropH are 0-1 fractions of the source texture (0,0,1,1 = whole
     * image, no zoom). panelTopLeft/panelBottomRight + panelX/Y/W/H must describe the same
     * panel this card is drawn on top of, or the erased corners won't match its background.
     */
    public static void drawRoundedTexture(DrawContext context, Identifier texture,
                                           int x, int y, int width, int height,
                                           float cropU, float cropV, float cropW, float cropH,
                                           int textureW, int textureH,
                                           int overlayColor, int radius,
                                           int panelX, int panelY, int panelW, int panelH,
                                           int[] panelTopLeft, int[] panelBottomRight) {
        int u = Math.round(cropU * textureW);
        int v = Math.round(cropV * textureH);
        int regionW = Math.max(1, Math.round(cropW * textureW));
        int regionH = Math.max(1, Math.round(cropH * textureH));
        context.drawTexture(RenderPipelines.GUI_TEXTURED, texture, x, y, u, v, width, height,
                regionW, regionH, textureW, textureH);

        if (overlayColor != 0) {
            context.fill(x, y, x + width, y + height, overlayColor);
        }

        int r = Math.max(0, Math.min(radius, Math.min(width, height) / 2));
        if (r <= 0) return;
        eraseCorner(context, x, y, 0, 0, r, r, r, panelX, panelY, panelW, panelH, panelTopLeft, panelBottomRight);
        eraseCorner(context, x, y, width - r, 0, width - r, r, r, panelX, panelY, panelW, panelH, panelTopLeft, panelBottomRight);
        eraseCorner(context, x, y, 0, height - r, r, height - r, r, panelX, panelY, panelW, panelH, panelTopLeft, panelBottomRight);
        eraseCorner(context, x, y, width - r, height - r, width - r, height - r, r, panelX, panelY, panelW, panelH, panelTopLeft, panelBottomRight);
    }

    /**
     * Row-batched: consecutive fully-erased (coverage==1) pixels in a row are merged into
     * one wide fill() call instead of one call per pixel. Same exact per-pixel coverage/color
     * math as the original per-pixel version (this replaced) - only how many draw calls it
     * takes to paint the result changed. This is what was capping the Discover screen's
     * framerate: 8 cards x 4 corners x ~r^2 individual 1x1 fill() calls every single frame.
     */
    private static void eraseCorner(DrawContext context, int x, int y, int boxX, int boxY,
                                     float ccx, float ccy, int r,
                                     int panelX, int panelY, int panelW, int panelH,
                                     int[] panelTopLeft, int[] panelBottomRight) {
        for (int dy = 0; dy < r; dy++) {
            int py = boxY + dy;
            float sampleY = py + 0.5f;
            int runStart = -1;

            // dx runs one past r on purpose - that final out-of-range pass is what flushes
            // a run that extends all the way to the box's edge.
            for (int dx = 0; dx <= r; dx++) {
                boolean inRange = dx < r;
                float coverage = 0f;
                if (inRange) {
                    float sampleX = (boxX + dx) + 0.5f;
                    float dist = (float) Math.sqrt((sampleX - ccx) * (sampleX - ccx) + (sampleY - ccy) * (sampleY - ccy));
                    coverage = clamp(dist - r + 0.5f, 0f, 1f);
                }

                if (inRange && coverage >= 1f) {
                    if (runStart < 0) runStart = dx;
                    continue;
                }

                if (runStart >= 0) {
                    int worldX0 = x + boxX + runStart;
                    int worldXEnd = x + boxX + dx;
                    int worldY = y + py;
                    float xFracMid = clamp((((worldX0 + worldXEnd) / 2f) - panelX) / (float) Math.max(1, panelW - 1), 0f, 1f);
                    float yFrac = clamp((worldY - panelY) / (float) Math.max(1, panelH), 0f, 1f);
                    int[] c = gradientAt(xFracMid, yFrac, panelTopLeft, panelBottomRight);
                    context.fill(worldX0, worldY, worldXEnd, worldY + 1, argb(255, c));
                    runStart = -1;
                }

                if (inRange && coverage > 0f) {
                    int worldX = x + boxX + dx;
                    int worldY = y + py;
                    float xFrac = clamp((worldX - panelX) / (float) Math.max(1, panelW - 1), 0f, 1f);
                    float yFrac = clamp((worldY - panelY) / (float) Math.max(1, panelH), 0f, 1f);
                    int[] c = gradientAt(xFrac, yFrac, panelTopLeft, panelBottomRight);
                    int a = Math.round(coverage * 255);
                    context.fill(worldX, worldY, worldX + 1, worldY + 1, argb(a, c));
                }
            }
        }
    }
}
