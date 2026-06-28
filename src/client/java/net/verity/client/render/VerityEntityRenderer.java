package net.verity.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;
import net.verity.client.VerityModClient;
import net.verity.client.model.VerityEntityModel;
import net.verity.entity.VerityEntity;

public class VerityEntityRenderer extends MobRenderer<VerityEntity, VerityEntityModel> {
    // Original textures from Bedrock addon — 12 face states
    private static final ResourceLocation[] FACE_TEXTURES = new ResourceLocation[] {
        ResourceLocation.parse("verity:textures/entity/verity_face_smile.png"),
        ResourceLocation.parse("verity:textures/entity/verity_face_speak.png"),
        ResourceLocation.parse("verity:textures/entity/verity_face_hurt.png"),
        ResourceLocation.parse("verity:textures/entity/verity_face_abnormal_shut.png"),
        ResourceLocation.parse("verity:textures/entity/verity_face_abnormal_open.png"),
        ResourceLocation.parse("verity:textures/entity/verity_face_bored_p2.png"),
        ResourceLocation.parse("verity:textures/entity/verity_face_day2_shut.png"),
        ResourceLocation.parse("verity:textures/entity/verity_face_day2_open.png"),
        ResourceLocation.parse("verity:textures/entity/verity_face_creepysmile.png"),
        ResourceLocation.parse("verity:textures/entity/verity_face_serious1.png"),
        ResourceLocation.parse("verity:textures/entity/verity_face_serious2.png"),
        ResourceLocation.parse("verity:textures/entity/verity_face_serious3.png"),
    };
    private static final ResourceLocation BLANK_TEXTURE = ResourceLocation.parse("verity:textures/entity/verity_face_serious1.png");
    // Monster form на шаре: жуткое лицо (day2_open), не текстура 3D-модели монстра
    private static final ResourceLocation MONSTER_GLOW_TEXTURE = ResourceLocation.parse("verity:textures/entity/verity_face_day2_open.png");
    private static final ResourceLocation MONSTER_SPHERE_TEXTURE = ResourceLocation.parse("verity:textures/entity/verity_face_creepysmile.png");

    public VerityEntityRenderer(EntityRendererProvider.Context context) {
        super(context, new VerityEntityModel(context.bakeLayer(VerityModClient.MODEL_SPHERE_LAYER)), 0.25F);
        // Glow layer for creepy face states in sphere phase
        this.addLayer(new VerityGlowLayer(this));
    }

    @Override
    public ResourceLocation getTextureLocation(VerityEntity entity) {
        if (entity.isMonsterForm()) {
            // Шар в monster form — жуткая улыбка, не 3D-модель монстра
            return MONSTER_SPHERE_TEXTURE;
        }
        if (entity.isFaceless()) {
            return BLANK_TEXTURE;
        }
        int faceIndex = Math.max(0, Math.min(entity.getFaceIndex(), FACE_TEXTURES.length - 1));
        return FACE_TEXTURES[faceIndex];
    }

    /**
     * Glow layer using entity_translucent_emissive render type.
     */
    private static class VerityGlowLayer extends RenderLayer<VerityEntity, VerityEntityModel> {

        public VerityGlowLayer(RenderLayerParent<VerityEntity, VerityEntityModel> parent) {
            super(parent);
        }

        @Override
        public void render(PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, VerityEntity entity,
                           float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks,
                           float netHeadYaw, float headPitch) {
            if (entity.isMonsterForm()) {
                // Monster form на шаре: full-bright жуткое свечение
                int fullBright = 0xF000F0;
                this.getParentModel().renderToBuffer(poseStack,
                        bufferSource.getBuffer(RenderType.entityTranslucentEmissive(MONSTER_SPHERE_TEXTURE)),
                        fullBright, net.minecraft.client.renderer.entity.LivingEntityRenderer.getOverlayCoords(entity, 0.0F),
                        0xFFFFFFFF);
                return;
            }

            int face = entity.getFaceIndex();
            // Only glow for creepy states: 3 (abnormal shut), 4 (abnormal open), 6 (day2 shut), 7 (day2 open), 8 (creepy smile)
            if (face != 3 && face != 4 && face != 6 && face != 7 && face != 8) {
                return;
            }
            ResourceLocation glowTexture = switch (face) {
                case 6, 7 -> FACE_TEXTURES[face];
                case 8 -> FACE_TEXTURES[8];
                default -> FACE_TEXTURES[3];
            };
            int fullBright = 0xF000F0;
            this.getParentModel().renderToBuffer(poseStack,
                    bufferSource.getBuffer(RenderType.entityTranslucentEmissive(glowTexture)),
                    fullBright, net.minecraft.client.renderer.entity.LivingEntityRenderer.getOverlayCoords(entity, 0.0F),
                    0xFFFFFFFF);
        }
    }
}
