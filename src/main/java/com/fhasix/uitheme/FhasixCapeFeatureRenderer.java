package com.fhasix.uitheme;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.ModelData;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.model.ModelPartBuilder;
import net.minecraft.client.model.ModelPartData;
import net.minecraft.client.model.ModelTransform;
import net.minecraft.client.model.TexturedModelData;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;

/**
 * Renders the equipped FHASIX cape using the supplied texture and vanilla cape physics.
 *
 * PORTING NOTE (1.20.1 -> 1.21.11): this is the single most invasive change in the whole
 * port. Minecraft 1.21.2+ moved entity rendering onto a "render state" model:
 * FeatureRenderer no longer receives the live entity + tickDelta and interpolates
 * cape swing itself; instead PlayerEntityRenderState now carries three
 * already-computed floats - capeFlap, capeLean, capeLean2 - that vanilla's own
 * PlayerCapeModel.setAngles(state) assigns onto the cape ModelPart's own
 * pitch/yaw/roll fields (not applied as manual MatrixStack rotations by the
 * FeatureRenderer the way the old capeX/capeY/capeZ math was in 1.20.1).
 *
 * v18.4 CORRECTION: the v18-v18.3 builds applied these three values as manual
 * matrices.multiply(RotationAxis...) calls in a rotation order chosen by
 * guesswork, which does not necessarily match the order Minecraft's own
 * ModelPart.render() applies pitch/yaw/roll in - order matters for rotations,
 * so getting the individual values "right" while applying them in the wrong
 * order can still look wrong (reported: a static-looking diagonal offset
 * instead of a natural wave, and the cape missing entirely at some angles).
 * This version instead assigns the three values directly onto
 * CAPE_PART.pitch/yaw/roll and lets ModelPart's own render path apply them
 * exactly the way vanilla's cape does, removing rotation-order as a variable
 * entirely. The capeFlap/capeLean/capeLean2 -> pitch/roll/yaw pairing itself
 * is deliberately left unchanged from v18-v18.3 in this pass: it was never
 * ruled out as correct, only tested underneath the rotation-order bug above,
 * so changing the mechanism and the pairing in the same pass would make a
 * still-wrong result impossible to attribute to either. If the cape still
 * looks off after this fix, swapping the capeLean/capeLean2 <-> roll/yaw
 * pairing below (not the mechanism) is the next thing to try, since the
 * mechanism itself now matches vanilla's own.
 *
 * v18.5 CORRECTION: v18.4 fixed rotation order but still rendered the cape
 * flush against the FRONT of the body instead of the back. The 1.20.1
 * source's yaw term was never a bare dynamic value - it was
 * "180.0F - sideways / 2.0F", a half-turn baseline with a small swing
 * around it, and that baseline is what orients the cape's flat face to sit
 * against the back rather than the front. v18.4 assigned capeLean2 straight
 * onto yaw with no baseline, which is the same shape of bug as leaving out
 * that "180.0F -" - restored below.
 *
 * Separately, 1.21.9+ replaced VertexConsumerProvider with OrderedRenderCommandQueue
 * as the thing FeatureRenderer#render receives, and vanilla's own PlayerEntityModel
 * no longer exposes a renderCape(...) helper (cape rendering moved to its own
 * dedicated PlayerCapeModel class internal to CapeFeatureRenderer). Rather than
 * depend on that internal class, this renderer builds its own single-part
 * "cape" ModelPart with the same geometry Minecraft's cape has used since
 * Beta (10x16x1 box, 64x32 texture, uv(0,0)) and submits it through
 * OrderedRenderCommandQueue#submitModelPart.
 */
public class FhasixCapeFeatureRenderer extends FeatureRenderer<PlayerEntityRenderState, PlayerEntityModel> {
    private static final Identifier FIRST_OPTIX_CAPE_TEXTURE =
            Identifier.of("fhasixuitheme", "textures/gui/cosmetics/first_optix_cape.png");
    private static final Identifier RED_CHARMS_CAPE_TEXTURE =
            Identifier.of("fhasixuitheme", "textures/gui/cosmetics/red_charms_cape.png");

    /** Same geometry vanilla's cape has used since Beta: a 10w x 16h x 1d box on a 64x32 texture. */
    private static final ModelPart CAPE_PART = buildCapePart();

    private static ModelPart buildCapePart() {
        ModelData modelData = new ModelData();
        ModelPartData root = modelData.getRoot();
        root.addChild("cape", ModelPartBuilder.create()
                        .uv(0, 0)
                        .cuboid(-5.0F, 0.0F, -1.0F, 10.0F, 16.0F, 1.0F),
                ModelTransform.origin(0.0F, 0.0F, 0.0F));
        return TexturedModelData.of(modelData, 64, 32).createModel().getChild("cape");
    }

    /** Degrees -> radians. Written out with plain java.lang.Math rather than a Minecraft
     *  helper constant on purpose: this file already tripped once on assuming a helper
     *  existed where it didn't (see PORTING NOTE above), so this avoids repeating that
     *  risk for a one-line conversion. */
    private static final float DEG_TO_RAD = (float) (Math.PI / 180.0);

    public FhasixCapeFeatureRenderer(
            FeatureRendererContext<PlayerEntityRenderState, PlayerEntityModel> context) {
        super(context);
    }

    @Override
    public void render(MatrixStack matrices, OrderedRenderCommandQueue queue, int light,
                        PlayerEntityRenderState state, float limbAngle, float limbDistance) {
        MinecraftClient client = MinecraftClient.getInstance();
        if ((!FhasixCosmetics.isFirstOptixEquipped() && !FhasixCosmetics.isSecondCapeEquipped())
                || state.invisibleToPlayer
                || client.player == null
                // PORTING NOTE: render() only gets the render STATE now, not the entity, so the old
                // "player != MinecraftClient.getInstance().player" identity check (only show this
                // cosmetic on the client's own character) is done via the state's captured entity id.
                || state.id != client.player.getId()) {
            return;
        }

        Identifier capeTexture = FhasixCosmetics.isSecondCapeEquipped()
                ? RED_CHARMS_CAPE_TEXTURE
                : FIRST_OPTIX_CAPE_TEXTURE;

        matrices.push();
        matrices.translate(0.0F, 0.0F, 0.125F);

        // PORTING NOTE (v18.5): the 1.20.1 source's yaw term was never a small delta on its
        // own - it was "180.0F - sideways / 2.0F", a half-turn baseline with a small dynamic
        // swing around it. That 180 baseline is what keeps the cape's flat face sitting
        // against the character's BACK; without it the cape's rest orientation is flipped
        // roughly half a turn, which renders it flush against the FRONT of the body instead
        // (reported: cape appearing in front of / overlapping the player). v18.4 assigned
        // capeLean2 straight onto yaw with no baseline at all - restoring that baseline here,
        // in the same "180 minus the dynamic term" shape as the original.
        //
        // PORTING NOTE (v18.7): v18.6 froze roll/yaw to isolate the up-down flap per an
        // earlier request, but that request was actually for the normal vanilla cape wave
        // (which does move on all three axes - that's what makes a cape look like it's
        // catching air rather than swinging on a hinge). Reverted to the full v18.5 dynamics
        // below; the 180 yaw baseline itself is unchanged throughout.
        CAPE_PART.pitch = state.field_53536 * DEG_TO_RAD;
        CAPE_PART.roll = state.field_53538 * DEG_TO_RAD;
        CAPE_PART.yaw = (180.0F - state.field_53537) * DEG_TO_RAD;

        queue.submitModelPart(CAPE_PART, matrices, getContextModel().getLayer(capeTexture),
                light, OverlayTexture.DEFAULT_UV, null);

        matrices.pop();
    }
}

