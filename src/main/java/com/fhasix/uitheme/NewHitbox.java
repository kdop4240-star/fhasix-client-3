package com.fhasix.uitheme;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.Entity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

/**
 * Hitbox: draws the hitbox of nearby entities as a wireframe box. Only two settings: colour (colour
 * picker) and size (percent of the real hitbox). Drawn as a HUD overlay from projected box corners,
 * so it needs no world-rendering hooks. First-person view only.
 */
final class NewHitbox {

    static final String ID = "hitbox";

    private static final double RANGE = 48.0;
    private static final int MAX_DRAWN = 30;
    private static final NewProjection PROJ = new NewProjection();

    private NewHitbox() {
    }

    static void init() {
        HudElementRegistry.attachElementAfter(
                VanillaHudElements.MISC_OVERLAYS,
                Identifier.of("fhasixuitheme", "hitbox_overlay"),
                NewSafe.hud("hitbox", (context, tickCounter) -> render(context, tickCounter.getTickProgress(false)))
        );
    }

    private static void render(DrawContext g, float tickProgress) {
        MinecraftClient c = MinecraftClient.getInstance();
        if (c.world == null || c.player == null || c.currentScreen != null || !NewModSettings.isEnabled(c, ID)
                || !PROJ.begin(c, g, tickProgress)) {
            return;
        }
        int cap = NewPerf.overlayCap(MAX_DRAWN);
        if (cap == 0) {
            return;
        }
        double range = NewPerf.overlayRange(RANGE);
        int color = 0xFF000000 | (NewModSettings.opt(c, ID, "rgb") & 0xFFFFFF);
        double scale = NewModSettings.opt(c, ID, "size") / 100.0;
        Entity camEntity = c.getCameraEntity() != null ? c.getCameraEntity() : c.player;

        int drawn = 0;
        for (Entity e : c.world.getEntities()) {
            if (drawn >= cap) {
                break;
            }
            if (e == camEntity || e == c.player || e.isRemoved()) {
                continue;
            }
            Vec3d p = e.getLerpedPos(tickProgress);
            if (PROJ.distanceSq(p.x, p.y, p.z) > range * range) {
                continue;
            }
            double hw = e.getWidth() / 2.0 * scale;
            double hh = e.getHeight() / 2.0 * scale;
            double cy = p.y + e.getHeight() / 2.0;
            int[][] pts = new int[8][];
            boolean ok = true;
            for (int i = 0; i < 8 && ok; i++) {
                pts[i] = PROJ.project(p.x + ((i & 1) == 0 ? -hw : hw), cy + ((i & 2) == 0 ? -hh : hh),
                        p.z + ((i & 4) == 0 ? -hw : hw));
                ok = pts[i] != null;
            }
            if (!ok) {
                continue;
            }
            for (int i = 0; i < 8; i++) {
                for (int bit = 1; bit <= 4; bit <<= 1) {
                    int j = i ^ bit;
                    if (j > i) {
                        line(g, pts[i][0], pts[i][1], pts[j][0], pts[j][1], color);
                    }
                }
            }
            drawn++;
        }
    }

    /** 1px line drawn as horizontal / vertical runs, so a whole edge costs only a handful of fills. */
    private static void line(DrawContext g, int x0, int y0, int x1, int y1, int color) {
        int dx = x1 - x0;
        int dy = y1 - y0;
        if (Math.abs(dx) >= Math.abs(dy)) {
            int steps = Math.abs(dy);
            if (steps == 0) {
                g.fill(Math.min(x0, x1), y0, Math.max(x0, x1) + 1, y0 + 1, color);
                return;
            }
            int sy = dy > 0 ? 1 : -1;
            for (int i = 0; i <= steps; i++) {
                int xa = x0 + Math.round(dx * (i - 0.5f) / steps);
                int xb = x0 + Math.round(dx * (i + 0.5f) / steps);
                xa = Math.max(Math.min(x0, x1), Math.min(Math.max(x0, x1), xa));
                xb = Math.max(Math.min(x0, x1), Math.min(Math.max(x0, x1), xb));
                g.fill(Math.min(xa, xb), y0 + sy * i, Math.max(xa, xb) + 1, y0 + sy * i + 1, color);
            }
        } else {
            int steps = Math.abs(dx);
            if (steps == 0) {
                g.fill(x0, Math.min(y0, y1), x0 + 1, Math.max(y0, y1) + 1, color);
                return;
            }
            int sx = dx > 0 ? 1 : -1;
            for (int i = 0; i <= steps; i++) {
                int ya = y0 + Math.round(dy * (i - 0.5f) / steps);
                int yb = y0 + Math.round(dy * (i + 0.5f) / steps);
                ya = Math.max(Math.min(y0, y1), Math.min(Math.max(y0, y1), ya));
                yb = Math.max(Math.min(y0, y1), Math.min(Math.max(y0, y1), yb));
                g.fill(x0 + sx * i, Math.min(ya, yb), x0 + sx * i + 1, Math.max(ya, yb) + 1, color);
            }
        }
    }
}
