package com.fhasix.uitheme;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexRendering;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShapes;

import java.util.ArrayList;
import java.util.List;

/**
 * UHC Overlay - ore outlines: draws coloured lines around diamond / gold / emerald / iron ore.
 *
 * Fair-play note: only ores that are EXPOSED (at least one side touches air / a see-through block)
 * are outlined, and the lines are depth-tested like everything else in the world, so you never see
 * an ore through solid walls - this highlights what you could already see, it is not X-ray.
 *
 * The scan runs a few times per second in the client tick and keeps a short list; the world render
 * event only draws that list.
 *
 * Uses the 1.21.11 shape-outline renderer and the vanilla line layer exposed through a mixin invoker.
 */
final class NewOreHighlight {

    static final String ID = "uhc_overlay";

    private static volatile List<BlockPos> positions = new ArrayList<>();
    private static volatile List<Integer> colors = new ArrayList<>();

    private NewOreHighlight() {
    }

    static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(NewSafe.tick("ore_highlight", NewOreHighlight::scan));
        WorldRenderEvents.AFTER_ENTITIES.register(context -> NewSafe.run("ore_highlight_draw", () -> render(context)));
    }

    private static int colorFor(MinecraftClient c, BlockState st) {
        if (NewModSettings.optBool(c, ID, "o_diamond") && st.isIn(BlockTags.DIAMOND_ORES)) {
            return 0xFF22D3EE;
        }
        if (NewModSettings.optBool(c, ID, "o_gold") && st.isIn(BlockTags.GOLD_ORES)) {
            return 0xFFFFD43B;
        }
        if (NewModSettings.optBool(c, ID, "o_emerald") && st.isIn(BlockTags.EMERALD_ORES)) {
            return 0xFF3FBF4D;
        }
        if (NewModSettings.optBool(c, ID, "o_iron") && st.isIn(BlockTags.IRON_ORES)) {
            return 0xFFE0C0A0;
        }
        return 0;
    }

    private static void scan(MinecraftClient c) {
        if (c.world == null || c.player == null || !NewModSettings.isEnabled(c, ID)
                || !NewModSettings.optBool(c, ID, "ores")) {
            if (!positions.isEmpty()) {
                positions = new ArrayList<>();
                colors = new ArrayList<>();
            }
            return;
        }
        if (c.world.getTime() % 10 != 0) {
            return;
        }
        int r = NewModSettings.opt(c, ID, "range");
        BlockPos center = c.player.getBlockPos();
        List<BlockPos> foundPos = new ArrayList<>();
        List<Integer> foundCol = new ArrayList<>();
        BlockPos.Mutable m = new BlockPos.Mutable();
        BlockPos.Mutable n = new BlockPos.Mutable();
        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    m.set(center.getX() + dx, center.getY() + dy, center.getZ() + dz);
                    BlockState st = c.world.getBlockState(m);
                    if (st.isAir()) {
                        continue;
                    }
                    int col = colorFor(c, st);
                    if (col == 0) {
                        continue;
                    }
                    boolean exposed = false;
                    for (Direction d : Direction.values()) {
                        n.set(m, d);
                        if (!c.world.getBlockState(n).isOpaque()) {
                            exposed = true;
                            break;
                        }
                    }
                    if (exposed) {
                        foundPos.add(m.toImmutable());
                        foundCol.add(col);
                    }
                }
            }
        }
        positions = foundPos;
        colors = foundCol;
    }

    private static void render(WorldRenderContext context) {
        MinecraftClient c = MinecraftClient.getInstance();
        List<BlockPos> pos = positions;
        if (c.world == null || pos.isEmpty() || !NewModSettings.isEnabled(c, ID)) {
            return;
        }
        List<Integer> cols = colors;
        float alpha = NewModSettings.opt(c, ID, "oalpha") / 100f;
        MatrixStack ms = context.matrices();
        Vec3d cam = c.gameRenderer.getCamera().getPos();
        VertexConsumer vc = context.consumers().getBuffer(com.fhasix.uitheme.mixin.RenderLayersAccessor.fhasix$lines());
        final double e = 0.002;
        for (int i = 0; i < pos.size() && i < cols.size(); i++) {
            BlockPos p = pos.get(i);
            int col = cols.get(i);
            double x = p.getX() - cam.x;
            double y = p.getY() - cam.y;
            double z = p.getZ() - cam.z;
            int a = Math.max(0, Math.min(100, Math.round(alpha * 100.0f))) * 255 / 100;
            int argb = (a << 24) | (col & 0x00FFFFFF);
            VertexRendering.drawOutline(ms, vc, VoxelShapes.fullCube(), x - e, y - e, z - e, argb, 1.5f);
        }
    }
}
