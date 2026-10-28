package com.fhasix.uitheme;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.TntEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

/**
 * TNT Timer: a small countdown above every lit TNT showing the seconds left until it blows up.
 * Drawn as a HUD overlay from the TNT's projected position. First-person view only. No settings.
 */
final class NewTntTimer {

    static final String ID = "tnt_timer";

    private static final double RANGE = 64.0;
    private static final int MAX_DRAWN = 24;
    private static final NewProjection PROJ = new NewProjection();

    private NewTntTimer() {
    }

    static void init() {
        HudElementRegistry.attachElementAfter(
                VanillaHudElements.MISC_OVERLAYS,
                Identifier.of("fhasixuitheme", "tnt_timer"),
                NewSafe.hud("tnt_timer", (context, tickCounter) -> render(context, tickCounter.getTickProgress(false)))
        );
    }

    private static void render(DrawContext g, float tickProgress) {
        MinecraftClient c = MinecraftClient.getInstance();
        if (c.world == null || c.player == null || c.currentScreen != null || !NewModSettings.isEnabled(c, ID)
                || !PROJ.begin(c, g, tickProgress)) {
            return;
        }
        int cap = NewPerf.overlayCap(MAX_DRAWN);
        double range = NewPerf.overlayRange(RANGE);
        int drawn = 0;
        for (Entity e : c.world.getEntities()) {
            if (drawn >= cap) {
                break;
            }
            if (!(e instanceof TntEntity tnt) || tnt.isRemoved()) {
                continue;
            }
            Vec3d p = tnt.getLerpedPos(tickProgress).add(0.0, tnt.getHeight() + 0.5, 0.0);
            if (PROJ.distanceSq(p.x, p.y, p.z) > range * range) {
                continue;
            }
            int[] s = PROJ.project(p.x, p.y, p.z);
            if (s == null) {
                continue;
            }
            int fuse = Math.max(0, tnt.getFuse());
            String text = String.format(java.util.Locale.ROOT, "%.1fs", fuse / 20f);
            int col = fuse > 40 ? NewUi.lerp(0xFFFFD43B, 0xFF3FBF4D, Math.min(1f, (fuse - 40) / 40f))
                    : NewUi.lerp(0xFFE73C40, 0xFFFFD43B, fuse / 40f);
            int tw = c.textRenderer.getWidth(text);
            int bw = tw + 10;
            int bh = c.textRenderer.fontHeight + 6;
            NewUi.frame(g, s[0] - bw / 2, s[1] - bh, bw, bh, 4, 0xFF3A3F4A, 0xD00C0D11, 0xD008090C);
            g.drawText(c.textRenderer, text, s[0] - tw / 2, s[1] - bh + 4, col, false);
            drawn++;
        }
    }
}
