package net.verity.client.render;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;
import net.verity.client.VerityModClient;
import net.verity.client.model.VerityMonsterModel;
import net.verity.entity.VerityMonsterEntity;

public class VerityMonsterRenderer extends MobRenderer<VerityMonsterEntity, VerityMonsterModel> {
    private static final ResourceLocation MONSTER_TEXTURE = ResourceLocation.parse("verity:textures/entity/verity_monster.png");

    public VerityMonsterRenderer(EntityRendererProvider.Context context) {
        super(context, new VerityMonsterModel(context.bakeLayer(VerityModClient.MODEL_MONSTER_LAYER)), 0.6F);
    }

    @Override
    public ResourceLocation getTextureLocation(VerityMonsterEntity entity) {
        return MONSTER_TEXTURE;
    }
}
