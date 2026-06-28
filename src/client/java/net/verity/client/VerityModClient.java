package net.verity.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.network.chat.Component;
import net.verity.VerityMod;
import net.verity.config.VerityConfig;
import net.verity.client.render.ClosedBoxEntityRenderer;
import net.verity.client.render.CardboardBoxEntityRenderer;
import net.verity.client.render.VerityEntityRenderer;
import net.verity.client.render.VerityItemRenderer;
import net.verity.client.render.VerityMonsterRenderer;
import net.verity.client.model.VerityEntityModel;
import net.verity.client.model.VerityMonsterModel;
import net.verity.client.screen.VeritySettingsScreen;
import net.verity.client.voice.VerityVoiceHandler;
import net.verity.client.config.VerityClientConfig;

public class VerityModClient implements ClientModInitializer {
    public static final net.minecraft.client.model.geom.ModelLayerLocation MODEL_SPHERE_LAYER = new net.minecraft.client.model.geom.ModelLayerLocation(
            net.minecraft.resources.ResourceLocation.parse("verity:verity"), "sphere"
    );
    public static final net.minecraft.client.model.geom.ModelLayerLocation MODEL_MONSTER_LAYER = new net.minecraft.client.model.geom.ModelLayerLocation(
            net.minecraft.resources.ResourceLocation.parse("verity:verity_monster"), "monster"
    );

    @Override
    public void onInitializeClient() {
        // Load client config and init voice (STT / push-to-talk)
        VerityClientConfig.load();
        VerityVoiceHandler.init();

        // TTS — приём пакета от сервера и озвучка + анимация в руках
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(
                net.verity.net.TTSPayload.TYPE,
                (payload, context) -> {
                    context.client().execute(() -> {
                        net.verity.client.voice.FishAudioTTSClient.speakAsync(payload.text());
                        net.verity.client.render.VerityItemRenderer.setTalking(true);
                    });
                });

        // Register Model Layers
        EntityModelLayerRegistry.registerModelLayer(
                MODEL_SPHERE_LAYER,
                VerityEntityModel::getTexturedModelData
        );
        EntityModelLayerRegistry.registerModelLayer(
                MODEL_MONSTER_LAYER,
                VerityMonsterModel::getTexturedModelData
        );

        // Register Entity Renderers
        EntityRendererRegistry.register(
                VerityMod.CARDBOARD_BOX_ENTITY,
                CardboardBoxEntityRenderer::new
        );
        EntityRendererRegistry.register(
                VerityMod.CLOSED_BOX_ENTITY,
                ClosedBoxEntityRenderer::new
        );
        EntityRendererRegistry.register(
                VerityMod.VERITY_ENTITY,
                VerityEntityRenderer::new
        );
        EntityRendererRegistry.register(
                VerityMod.VERITY_MONSTER_ENTITY,
                VerityMonsterRenderer::new
        );

        // Register hybrid item renderer — 2D icon in GUI, 3D ball in hand
        BuiltinItemRendererRegistry.INSTANCE.register(
                VerityMod.VERITY_INVENTORY_1,
                new VerityItemRenderer(VerityItemRenderer.TEX3D_SMILE, VerityItemRenderer.TEX2D_1));
        BuiltinItemRendererRegistry.INSTANCE.register(
                VerityMod.VERITY_INVENTORY_2,
                new VerityItemRenderer(VerityItemRenderer.TEX3D_BORED, VerityItemRenderer.TEX2D_2));
        BuiltinItemRendererRegistry.INSTANCE.register(
                VerityMod.VERITY_INVENTORY_3,
                new VerityItemRenderer(VerityItemRenderer.TEX3D_ABNORMAL, VerityItemRenderer.TEX2D_3));

        // Open settings screen on join if LLM is disabled and no custom key
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            if (!VerityConfig.llmEnabled() && VerityConfig.customApiKey().isEmpty()) {
                client.execute(() -> client.setScreen(new VeritySettingsScreen(null)));
            }
        });

        // Inject "Verity Settings" button into the Pause Screen
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (!(screen instanceof PauseScreen)) return;

            int btnW = 130;
            int btnH = 20;
            int btnX = scaledWidth / 2 - btnW / 2;
            // Below all vanilla buttons (last is ~scaledHeight/4 + 144)
            int btnY = scaledHeight / 4 + 168;

            Screens.getButtons(screen).add(Button.builder(
                    Component.literal("\u00a76\u2726 Verity\u2122 Settings \u00a7r\u00a78\u00a7o"),
                    btn -> client.setScreen(new VeritySettingsScreen(screen)))
                .bounds(btnX, btnY, btnW, btnH)
                .build());
        });
    }
}
