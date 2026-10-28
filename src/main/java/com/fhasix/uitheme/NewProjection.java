package com.fhasix.uitheme;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;

/**
 * NEW. Projects world positions onto the scaled GUI screen (first-person camera), so overlays can
 * be drawn with plain DrawContext calls from the HUD. Used by Hitbox.
 */
final class NewProjection {

    private double ex;
    private double ey;
    private double ez;
    private double fx;
    private double fy;
    private double fz;
    private double rx;
    private double rz;
    private double ux;
    private double uy;
    private double uz;
    private double tanHalf;
    private double aspect;
    private int sw;
    private int sh;

    /** Sets the camera up for this frame. False when no projection is possible (third person, no world). */
    boolean begin(MinecraftClient c, DrawContext g, float tickProgress) {
        return begin(c, g, tickProgress, false);
    }

    /**
     * Same, but with allowThirdPerson the camera is also worked out for F5 views: it sits 4 blocks
     * behind the eyes along the view direction (that is how the vanilla third-person camera is placed).
     */
    boolean begin(MinecraftClient c, DrawContext g, float tickProgress, boolean allowThirdPerson) {
        if (c.world == null || c.player == null) {
            return false;
        }
        boolean first = c.options.getPerspective().isFirstPerson();
        if (!first && !allowThirdPerson) {
            return false;
        }
        Entity cam = c.getCameraEntity() != null ? c.getCameraEntity() : c.player;
        Vec3d eye = cam.getLerpedPos(tickProgress).add(0.0, cam.getStandingEyeHeight(), 0.0);
        double yaw = Math.toRadians(c.gameRenderer.getCamera().getYaw());
        double pitch = Math.toRadians(c.gameRenderer.getCamera().getPitch());
        double cosP = Math.cos(pitch);
        fx = -Math.sin(yaw) * cosP;
        fy = -Math.sin(pitch);
        fz = Math.cos(yaw) * cosP;
        double back = first ? 0.0 : 4.0;
        ex = eye.x - fx * back;
        ey = eye.y - fy * back;
        ez = eye.z - fz * back;
        rx = -Math.cos(yaw);
        rz = -Math.sin(yaw);
        ux = -fy * rz;
        uy = rz * fx - rx * fz;
        uz = rx * fy;
        sw = g.getScaledWindowWidth();
        sh = g.getScaledWindowHeight();
        tanHalf = Math.tan(Math.toRadians(c.options.getFov().getValue()) / 2.0);
        aspect = sw / (double) sh;
        return true;
    }

    /** {x, y} in scaled GUI pixels, or null when the point is behind the camera or far off screen. */
    int[] project(double x, double y, double z) {
        double dx = x - ex;
        double dy = y - ey;
        double dz = z - ez;
        double depth = dx * fx + dy * fy + dz * fz;
        if (depth < 0.1) {
            return null;
        }
        double nx = (dx * rx + dz * rz) / (depth * tanHalf * aspect);
        double ny = (dx * ux + dy * uy + dz * uz) / (depth * tanHalf);
        if (Math.abs(nx) > 4.0 || Math.abs(ny) > 4.0) {
            return null;
        }
        return new int[]{Math.round((float) (sw / 2.0 * (1.0 + nx))), Math.round((float) (sh / 2.0 * (1.0 - ny)))};
    }

    double distanceSq(double x, double y, double z) {
        double dx = x - ex;
        double dy = y - ey;
        double dz = z - ez;
        return dx * dx + dy * dy + dz * dz;
    }
}
