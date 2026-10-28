package com.fhasix.uitheme;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.EnumMap;
import java.util.Map;

/**
 * A small square button using the same rounded-gradient navy theme as
 * b2, with an icon glyph drawn on top.
 *
 * v3 drew these icons as raw fill()/circle rectangles - readable, but
 * flat and blocky next to the reference's icon-font glyphs (double
 * gears, a faceted gem with sparkles, overlapping chat bubbles - real
 * curved vector art). Rectangle rasterization can't reproduce that
 * cleanly at 32-40px, so v4 renders each icon from a small pre-rendered
 * PNG instead (anti-aliased via the same supersample-then-downscale
 * technique already used for the button corners and the background
 * image) - the button's own rounded-gradient shape underneath is still
 * 100% procedural code, unchanged.
 */
public class c3 extends ButtonWidget {

    public enum Icon {GRID, CHAT, GEAR, GEM, ARCHIVE, LIST}

    private static final Map<Icon, Identifier> TEXTURES = new EnumMap<>(Icon.class);
    private static final Map<Icon, Boolean> FILTER_APPLIED = new EnumMap<>(Icon.class);

    static {
        for (Icon icon : Icon.values()) {
            TEXTURES.put(icon, Identifier.of("fhasixuitheme",
                    "textures/gui/icons/" + icon.name().toLowerCase() + ".png"));
            FILTER_APPLIED.put(icon, false);
        }
    }

    /** Icon textures are square PNGs at this native resolution. */
    private static final int ICON_TEX_SIZE = 128;
    /** Fraction of the button's size left as padding on each side around the icon - the glyph shrinks, not the button. */
    private static final float ICON_PADDING_FRACTION = 0.26f;
    /** 70% opacity per the "100% opacity to 70%" correction. */
    private static final int ICON_BG_ALPHA = 179;

    private final Icon icon;

    /** 0..1 eased progress of the hover label (fades/slides in on hover, out on leave). */
    private float tipT = 0f;
    private long lastTipNanos = 0L;

    public c3(int x, int y, int size, Icon icon, PressAction onPress) {
        super(x, y, size, size, net.minecraft.text.Text.literal(""), onPress, DEFAULT_NARRATION_SUPPLIER);
        this.icon = icon;
    }

    @Override
    public void drawIcon(DrawContext context, int mouseX, int mouseY, float delta) {
        boolean hovered = this.isHovered();
        int[] topLeft = hovered ? a1.NAVY_HOVER_TOP_LEFT : a1.NAVY_NORMAL_TOP_LEFT;
        int[] bottomRight = hovered ? a1.NAVY_HOVER_BOTTOM_RIGHT : a1.NAVY_NORMAL_BOTTOM_RIGHT;

        int x = this.getX();
        int y = this.getY();
        int size = this.getWidth();

        a1.drawRoundedGradientRect(context, x, y, size, size, topLeft, bottomRight, ICON_BG_ALPHA);

        Identifier texture = TEXTURES.get(icon);
        if (!FILTER_APPLIED.get(icon)) {
            FILTER_APPLIED.put(icon, true);
        }

        int pad = Math.round(size * ICON_PADDING_FRACTION);
        int inner = size - pad * 2;
        if (inner > 0) {
            context.drawTexture(RenderPipelines.GUI_TEXTURED, texture, x + pad, y + pad, 0, 0,
                    inner, inner, ICON_TEX_SIZE, ICON_TEX_SIZE, ICON_TEX_SIZE, ICON_TEX_SIZE);
        }

        // Hover label: a small pill under the icon that says what the button does.
        long now = System.nanoTime();
        float dt = lastTipNanos == 0L ? 0f : Math.min(0.1f, (now - lastTipNanos) / 1_000_000_000f);
        lastTipNanos = now;
        float target = this.isHovered() ? 1f : 0f;
        tipT += (target - tipT) * (1f - (float) Math.exp(-16f * dt));
        if (Math.abs(target - tipT) < 0.003f) {
            tipT = target;
        }
        drawTooltip(context, x, y, size);
    }

    /** Label text for each icon of the title-screen row (edit freely). */
    private static String tooltipFor(Icon icon) {
        switch (icon) {
            case GRID:
                return "MODS";
            case CHAT:
                return "SCREENSHOTS";
            case GEAR:
                return "SETTINGS";
            case GEM:
                return "SKINS";
            case ARCHIVE:
                return "FILES";
            case LIST:
                return "MINECRAFT";
            default:
                return "";
        }
    }

    private void drawTooltip(DrawContext context, int x, int y, int size) {
        String label = tooltipFor(icon);
        if (label.isEmpty() || tipT < 0.03f) {
            return;
        }
        var tr = MinecraftClient.getInstance().textRenderer;
        float e = 1f - (1f - tipT) * (1f - tipT) * (1f - tipT); // ease-out cubic

        int padX = 10;
        int w = tr.getWidth(label) + padX * 2;
        int h = tr.fontHeight + 12;
        int screenW = MinecraftClient.getInstance().getWindow().getScaledWidth();
        int px = Math.max(4, Math.min(screenW - w - 4, x + size / 2 - w / 2));
        int py = y + size + 8 - Math.round((1f - e) * 4f); // glides down into place

        int alpha = Math.round(235f * e);
        a1.drawRoundedGradientRect(context, px, py, w, h,
                new int[]{31, 32, 36}, new int[]{22, 23, 26}, alpha, 8);
        int textAlpha = Math.round(255f * e);
        if (textAlpha >= 8) {
            context.drawText(tr, label, px + padX, py + (h - tr.fontHeight) / 2,
                    (textAlpha << 24) | 0xE9E9EC, false);
        }
    }
}
