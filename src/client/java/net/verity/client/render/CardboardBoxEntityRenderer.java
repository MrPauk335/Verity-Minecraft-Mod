package net.verity.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import net.verity.VerityMod;
import net.verity.entity.CardboardBoxEntity;

public class CardboardBoxEntityRenderer extends EntityRenderer<CardboardBoxEntity> {
    private final BlockRenderDispatcher dispatcher;

    public CardboardBoxEntityRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.dispatcher = context.getBlockRenderDispatcher();
    }

    @Override
    public void render(CardboardBoxEntity entity, float entityYaw, float partialTicks, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        poseStack.pushPose();

        // Shaking animation during the float-up phase
        if (entity.isShaking()) {
            float age = (float) entity.getAge() + partialTicks;
            float shakeScale = 0.03F;
            double offsetX = Math.sin(age * 1.5F) * shakeScale;
            double offsetZ = Math.cos(age * 1.5F) * shakeScale;
            poseStack.translate(offsetX, 0.0D, offsetZ);
        }

        // Offset block model so that it is centered on the entity's position
        poseStack.translate(-0.5D, 0.0D, -0.5D);

        BlockState state = VerityMod.CARDBOARD_BOX.defaultBlockState();
        this.dispatcher.renderSingleBlock(state, poseStack, buffer, packedLight, OverlayTexture.NO_OVERLAY);

        poseStack.popPose();
        super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(CardboardBoxEntity entity) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
