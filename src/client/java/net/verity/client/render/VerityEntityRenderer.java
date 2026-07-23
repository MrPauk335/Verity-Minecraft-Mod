package net.verity.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
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
    private static final ResourceLocation MONSTER_TEXTURE = ResourceLocation.parse("verity:textures/entity/verity_monster.png");

    public VerityEntityRenderer(EntityRendererProvider.Context context) {
        super(context, new VerityEntityModel(context.bakeLayer(VerityModClient.MODEL_SPHERE_LAYER)), 0.25F);
        this.addLayer(new VerityHeldItemLayer(this, context.getItemInHandRenderer()));
        this.addLayer(new VerityGlowLayer(this));
    }

    @Override
    public ResourceLocation getTextureLocation(VerityEntity entity) {
        if (entity.isMonsterForm()) {
            // 3D-модель монстра — текстура монстра
            return MONSTER_TEXTURE;
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
                // Monster form: full-bright glow на 3D-модели
                int fullBright = 0xF000F0;
                this.getParentModel().renderToBuffer(poseStack,
                        bufferSource.getBuffer(RenderType.entityTranslucentEmissive(MONSTER_TEXTURE)),
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

    /**
     * Точное позиционирование топора/киркой у правого бока лица Verity.
     */
    private static class VerityHeldItemLayer extends RenderLayer<VerityEntity, VerityEntityModel> {
        private final ItemInHandRenderer itemInHandRenderer;

        public VerityHeldItemLayer(RenderLayerParent<VerityEntity, VerityEntityModel> parent, ItemInHandRenderer itemInHandRenderer) {
            super(parent);
            this.itemInHandRenderer = itemInHandRenderer;
        }

        @Override
        public void render(PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, VerityEntity entity,
                           float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks,
                           float netHeadYaw, float headPitch) {
            ItemStack mainHand = entity.getMainHandItem();
            if (mainHand.isEmpty() || entity.isMonsterForm() || entity.isInvisible()) {
                return;
            }

            poseStack.pushPose();

            // 1. Привязываем к трансформации сферы (yaw + roll)
            this.getParentModel().root().translateAndRotate(poseStack);
            this.getParentModel().getSphere().translateAndRotate(poseStack);

            // Позиция и повороты из конфига — меняются командой /veritydev toolpos <tx> <ty> <tz> <rx> <rz>
            poseStack.translate(
                net.verity.client.config.VerityClientConfig.toolTX(),
                net.verity.client.config.VerityClientConfig.toolTY(),
                net.verity.client.config.VerityClientConfig.toolTZ()
            );
            poseStack.mulPose(com.mojang.math.Axis.XP.rotationDegrees(net.verity.client.config.VerityClientConfig.toolRX()));
            poseStack.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(net.verity.client.config.VerityClientConfig.toolRZ()));

            float scale = 0.65F;
            poseStack.scale(scale, scale, scale);

            this.itemInHandRenderer.renderItem(
                    entity,
                    mainHand,
                    ItemDisplayContext.THIRD_PERSON_RIGHT_HAND,
                    false,
                    poseStack,
                    bufferSource,
                    packedLight
            );

            poseStack.popPose();
        }
    }
}