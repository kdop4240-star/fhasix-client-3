package com.fhasix.uitheme;

import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexRendering;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;

/** Custom target-block outline compatible with the 1.21.11 rendering API. */
final class NewBlockOverlay {
    static final String ID = "block_overlay";
    private NewBlockOverlay() {}

    static void init() {
        WorldRenderEvents.BEFORE_BLOCK_OUTLINE.register((context, outlineState) ->
                NewSafe.call("block_overlay", () -> onOutline(context), Boolean.TRUE));
    }

    private static boolean onOutline(WorldRenderContext context) {
        MinecraftClient c = MinecraftClient.getInstance();
        if (c.world == null || !NewModSettings.isEnabled(c, ID)) return true;
        HitResult hit = c.crosshairTarget;
        if (!(hit instanceof BlockHitResult bhr) || hit.getType() != HitResult.Type.BLOCK) return true;

        BlockPos pos = bhr.getBlockPos();
        BlockState state = c.world.getBlockState(pos);
        VoxelShape shape = state.getOutlineShape(c.world, pos);
        if (shape.isEmpty()) return true;

        int mode = NewModSettings.opt(c, ID, "mode"); // 0 border, 1 highlight, 2 both
        int border = NewModOptions.paletteColor(NewModSettings.opt(c, ID, "bcolor"));
        int fill = NewModOptions.paletteColor(NewModSettings.opt(c, ID, "fcolor"));
        int borderAlpha = Math.max(0, Math.min(100, NewModSettings.opt(c, ID, "balpha"))) * 255 / 100;
        MatrixStack ms = context.matrices();
        VertexConsumerProvider consumers = context.consumers();
        Vec3d cam = c.gameRenderer.getCamera().getPos();
        double ox = pos.getX() - cam.x;
        double oy = pos.getY() - cam.y;
        double oz = pos.getZ() - cam.z;
        final double e = 0.002;
        // 1.21.11 exposes the supported shape-outline renderer instead of the old drawBox helpers.
        // Keep the target's actual outline shape (including slabs/stairs), not a full-cube guess.
        if (mode != 0) {
            VertexConsumer fillConsumer = consumers.getBuffer(
                    com.fhasix.uitheme.mixin.RenderLayersAccessor.fhasix$debugFilledBox());
            float r = ((fill >> 16) & 255) / 255.0f;
            float g = ((fill >> 8) & 255) / 255.0f;
            float b = (fill & 255) / 255.0f;
            float a = Math.max(0, Math.min(100, NewModSettings.opt(c, ID, "falpha"))) / 100.0f;
            shape.forEachBox((x1, y1, z1, x2, y2, z2) -> drawFilledBox(ms, fillConsumer,
                    ox + x1 - e, oy + y1 - e, oz + z1 - e,
                    ox + x2 + e, oy + y2 + e, oz + z2 + e, r, g, b, a));
        }
        if (mode != 1) {
            VertexConsumer lineConsumer = consumers.getBuffer(
                    com.fhasix.uitheme.mixin.RenderLayersAccessor.fhasix$lines());
            int argb = (borderAlpha << 24) | (border & 0x00FFFFFF);
            VertexRendering.drawOutline(ms, lineConsumer, shape, ox, oy, oz, argb, 2.0f);
        }
        return false;
    }

    /** Emits six translucent quads into Minecraft's built-in debug-filled-box layer. */
    private static void drawFilledBox(MatrixStack matrices, VertexConsumer out,
                                      double x1, double y1, double z1,
                                      double x2, double y2, double z2,
                                      float r, float g, float b, float a) {
        MatrixStack.Entry entry = matrices.peek();
        face(out, entry, r, g, b, a, x1,y1,z1, x1,y2,z1, x2,y2,z1, x2,y1,z1);
        face(out, entry, r, g, b, a, x2,y1,z2, x2,y2,z2, x1,y2,z2, x1,y1,z2);
        face(out, entry, r, g, b, a, x1,y1,z2, x1,y2,z2, x1,y2,z1, x1,y1,z1);
        face(out, entry, r, g, b, a, x2,y1,z1, x2,y2,z1, x2,y2,z2, x2,y1,z2);
        face(out, entry, r, g, b, a, x1,y2,z1, x1,y2,z2, x2,y2,z2, x2,y2,z1);
        face(out, entry, r, g, b, a, x1,y1,z2, x1,y1,z1, x2,y1,z1, x2,y1,z2);
    }

    private static void face(VertexConsumer out, MatrixStack.Entry entry,
                             float r, float g, float b, float a,
                             double ax, double ay, double az,
                             double bx, double by, double bz,
                             double cx, double cy, double cz,
                             double dx, double dy, double dz) {
        out.vertex(entry, (float) ax, (float) ay, (float) az).color(r, g, b, a);
        out.vertex(entry, (float) bx, (float) by, (float) bz).color(r, g, b, a);
        out.vertex(entry, (float) cx, (float) cy, (float) cz).color(r, g, b, a);
        out.vertex(entry, (float) dx, (float) dy, (float) dz).color(r, g, b, a);
    }
}
