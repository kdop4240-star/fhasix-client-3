package com.fhasix.uitheme;

import net.minecraft.client.MinecraftClient;
import org.joml.Vector4f;

/**
 * Custom Fog values, read by mixin/NewFogMixin: an own fog colour (colour picker), a fog distance
 * scale (below 100% = fog comes closer, above = further away) and "Remove fog".
 *
 * Only active while a world is loaded. While the mod is on the fog is always switched ON: if fog was
 * turned off elsewhere (the fog values arrive as "infinite"), real fog distances are put back so the
 * mod's colour / distance settings actually show.
 *
 * The sky is drawn with the same fog uniform (it fades into the fog colour out to "sky end"), so
 * skyEnd() pushes that distance out while a custom colour is on - only the world fog takes the
 * colour and the sky keeps its own.
 */
public final class NewFog {

    static final String ID = "custom_fog";

    /** Anything this big is "no fog" (the game uses huge values when fog is switched off). */
    private static final float NO_FOG = 1.0e7f;

    private NewFog() {
    }

    private static boolean active() {
        MinecraftClient c = MinecraftClient.getInstance();
        return c != null && c.world != null && c.options != null && NewModSettings.isEnabled(c, ID);
    }

    /** Fog colour; the original colour (with fog forced on) when "Custom color" is off. */
    public static Vector4f color(Vector4f original) {
        try {
            return colorImpl(original);
        } catch (Exception | LinkageError t) {
            NewSafe.failed("fog", t);
            return original;
        }
    }

    private static Vector4f colorImpl(Vector4f original) {
        if (!active()) {
            return original;
        }
        MinecraftClient c = MinecraftClient.getInstance();
        if (!NewModSettings.optBool(c, ID, "color")) {
            return original == null ? null : new Vector4f(original.x, original.y, original.z, 1f);
        }
        int rgb = NewModSettings.opt(c, ID, "rgb");
        return new Vector4f(((rgb >> 16) & 255) / 255f, ((rgb >> 8) & 255) / 255f, (rgb & 255) / 255f, 1f);
    }

    /** Sky fog distance: pushed out while a custom colour is on so the sky is not tinted by it. */
    public static float skyEnd(float original) {
        try {
            return skyEndImpl(original);
        } catch (Exception | LinkageError t) {
            NewSafe.failed("fog", t);
            return original;
        }
    }

    private static float skyEndImpl(float original) {
        if (!active()) {
            return original;
        }
        MinecraftClient c = MinecraftClient.getInstance();
        return NewModSettings.optBool(c, ID, "color") ? Float.MAX_VALUE : original;
    }

    /**
     * Fog start/end distance. kind: 0 = environmental start, 1 = environmental end, 2 = render
     * distance start, 3 = render distance end.
     */
    public static float distance(float original, int kind) {
        try {
            return distanceImpl(original, kind);
        } catch (Exception | LinkageError t) {
            NewSafe.failed("fog", t);
            return original;
        }
    }

    private static float distanceImpl(float original, int kind) {
        if (!active()) {
            return original;
        }
        MinecraftClient c = MinecraftClient.getInstance();
        if (NewModSettings.optBool(c, ID, "off")) {
            return 100000f;
        }
        float v = original;
        if (v > NO_FOG) {
            // fog was disabled elsewhere - bring it back so this mod has something to colour
            float r = Math.max(32f, c.options.getClampedViewDistance() * 16f);
            switch (kind) {
                case 0: v = r * 0.05f; break;
                case 1: v = r * 0.9f; break;
                case 2: v = r - Math.max(4f, Math.min(64f, r / 10f)); break;
                default: v = r; break;
            }
        }
        return v * (NewModSettings.opt(c, ID, "dist") / 100f);
    }
}
