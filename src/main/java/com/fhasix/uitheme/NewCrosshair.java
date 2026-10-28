package com.fhasix.uitheme;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.hit.HitResult;

/**
 * Custom Crosshair: while enabled, the vanilla crosshair is replaced by this one.
 * Default look = the Minecraft crosshair theme (white, a bit transparent). Settings: style (Cross /
 * Plus / Dot / Circle), size, gap, thickness, opacity, color, and "Indicator": when on, the crosshair
 * shrinks while you are aiming at an entity.
 *
 * Note: the vanilla crosshair element also contains the vanilla attack-cooldown indicator, so while
 * Custom Crosshair is on that small indicator is not drawn - use the Attack Indicator "Fhasix meter".
 */
final class NewCrosshair {

    static final String ID = "custom_crosshair";

    private NewCrosshair() {
    }

    static void init() {
        HudElementRegistry.replaceElement(VanillaHudElements.CROSSHAIR, original -> NewSafe.replace("crosshair", original, (context, tickCounter) -> {
            MinecraftClient c = MinecraftClient.getInstance();
            if (!NewModSettings.isEnabled(c, ID)) {
                original.render(context, tickCounter);
                return;
            }
            if (c.options.getPerspective().isFirstPerson()) {
                draw(context, c);
            }
        }));
    }

    private static void draw(DrawContext g, MinecraftClient c) {
        int style = NewModSettings.opt(c, ID, "style");
        float size = NewModSettings.opt(c, ID, "size");
        float gap = NewModSettings.opt(c, ID, "gap");
        int t = NewModSettings.opt(c, ID, "thick");
        int alpha = Math.round(255f * NewModSettings.opt(c, ID, "opacity") / 100f) & 255;
        int col = (alpha << 24) | (NewModOptions.paletteColor(NewModSettings.opt(c, ID, "color")) & 0xFFFFFF);

        if (NewModSettings.optBool(c, ID, "indicator") && c.crosshairTarget != null
                && c.crosshairTarget.getType() == HitResult.Type.ENTITY) {
            size *= 0.6f;
            gap *= 0.6f;
        }
        int cx = g.getScaledWindowWidth() / 2;
        int cy = g.getScaledWindowHeight() / 2;
        int h = t / 2;
        int sz = Math.max(1, Math.round(size));
        int gp = Math.round(gap);

        switch (style) {
            case 1: { // Plus: unbroken lines through the middle
                int len = sz + gp;
                g.fill(cx - len, cy - h, cx + len + 1, cy - h + t, col);
                g.fill(cx - h, cy - len, cx - h + t, cy - h, col);
                g.fill(cx - h, cy - h + t, cx - h + t, cy + len + 1, col);
                break;
            }
            case 2: { // Dot
                int r = Math.max(1, sz / 3);
                g.fill(cx - r, cy - r, cx + r + 1, cy + r + 1, col);
                break;
            }
            case 3: { // Circle (ring)
                float r = sz;
                int ir = (int) Math.ceil(r + t);
                for (int dy = -ir; dy <= ir; dy++) {
                    for (int dx = -ir; dx <= ir; dx++) {
                        double d = Math.sqrt(dx * dx + dy * dy);
                        if (d >= r - t / 2.0 && d <= r + t / 2.0) {
                            g.fill(cx + dx, cy + dy, cx + dx + 1, cy + dy + 1, col);
                        }
                    }
                }
                break;
            }
            default: { // Cross: four arms with a gap in the middle
                g.fill(cx - h, cy - gp - sz, cx - h + t, cy - gp, col);
                g.fill(cx - h, cy + gp + 1, cx - h + t, cy + gp + 1 + sz, col);
                g.fill(cx - gp - sz, cy - h, cx - gp, cy - h + t, col);
                g.fill(cx + gp + 1, cy - h, cx + gp + 1 + sz, cy - h + t, col);
                break;
            }
        }
    }
}
