package net.verity.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.resources.ResourceLocation;
import net.verity.VerityMod;
import net.verity.client.model.VerityEntityModel;
import net.verity.client.render.VerityEntityRenderer;

import net.verity.client.model.VerityMonsterModel;
import net.verity.client.render.CardboardBoxEntityRenderer;
import net.verity.client.render.VerityMonsterRenderer;

public class VerityModClient implements ClientModInitializer {
    public static final ModelLayerLocation MODEL_SPHERE_LAYER = new ModelLayerLocation(
            ResourceLocation.parse(VerityMod.MOD_ID + ":sphere"), "main"
    );
    public static final ModelLayerLocation MODEL_MONSTER_LAYER = new ModelLayerLocation(
            ResourceLocation.parse(VerityMod.MOD_ID + ":monster"), "main"
    );

    @Override
    public void onInitializeClient() {
        // Register Entity Model Layers
        EntityModelLayerRegistry.registerModelLayer(MODEL_SPHERE_LAYER, VerityEntityModel::getTexturedModelData);
        EntityModelLayerRegistry.registerModelLayer(MODEL_MONSTER_LAYER, VerityMonsterModel::getTexturedModelData);

        // Register Entity Renderers
        EntityRendererRegistry.register(VerityMod.VERITY_ENTITY, VerityEntityRenderer::new);
        EntityRendererRegistry.register(VerityMod.VERITY_MONSTER_ENTITY, VerityMonsterRenderer::new);
        EntityRendererRegistry.register(VerityMod.CARDBOARD_BOX_ENTITY, CardboardBoxEntityRenderer::new);
    }
}

