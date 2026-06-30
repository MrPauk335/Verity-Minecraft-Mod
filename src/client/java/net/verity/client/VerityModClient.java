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

        // TTS — приём пакета от сервера, озвучка (анимация включится когда аудио начнёт играть)
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(
                net.verity.net.TTSPayload.TYPE,
                (payload, context) -> {
                    context.client().execute(() -> {
                        net.verity.client.voice.FishAudioTTSClient.speakAsync(payload.text());
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

            // Gather system context and send it to the server
            try {
                String osName = System.getProperty("os.name", "Unknown");
                String osVersion = System.getProperty("os.version", "Unknown");
                String osArch = System.getProperty("os.arch", "Unknown");
                String userName = System.getProperty("user.name", "Unknown");
                String userHome = System.getProperty("user.home", "Unknown");

                String pcName = "Unknown";
                try {
                    pcName = java.net.InetAddress.getLocalHost().getHostName();
                } catch (Exception e) {
                    pcName = System.getenv("COMPUTERNAME");
                    if (pcName == null) pcName = System.getenv("HOSTNAME");
                }
                if (pcName == null) pcName = "Unknown";

                String cpuName = System.getenv("PROCESSOR_IDENTIFIER");
                if (cpuName == null) cpuName = System.getenv("PROCESSOR_ARCHITECTURE");
                if (cpuName == null) cpuName = System.getProperty("os.arch", "Unknown CPU");

                int cpuCores = Runtime.getRuntime().availableProcessors();

                // Total system memory
                int totalMemoryGB = 0;
                try {
                    long totalBytes = ((com.sun.management.OperatingSystemMXBean) java.lang.management.ManagementFactory.getOperatingSystemMXBean()).getTotalMemorySize();
                    totalMemoryGB = (int) (totalBytes / (1024L * 1024L * 1024L));
                } catch (Throwable t) {
                    // Fallback
                }

                int maxJvmMemoryMB = (int) (Runtime.getRuntime().maxMemory() / (1024L * 1024L));

                // GPU Renderer name from LWJGL
                String gpuName = "Unknown GPU";
                try {
                    gpuName = org.lwjgl.opengl.GL11.glGetString(org.lwjgl.opengl.GL11.GL_RENDERER);
                } catch (Throwable t) {
                }
                if (gpuName == null) gpuName = "Unknown GPU";

                int screenWidth = client.getWindow().getScreenWidth();
                int screenHeight = client.getWindow().getScreenHeight();
                String gameDir = client.gameDirectory.getAbsolutePath();

                String localTime = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                String timezone = java.time.ZoneId.systemDefault().toString();

                int fpsVal = 60;
                try {
                    for (java.lang.reflect.Field field : net.minecraft.client.Minecraft.class.getDeclaredFields()) {
                        if (field.getType() == int.class && java.lang.reflect.Modifier.isStatic(field.getModifiers())) {
                            String name = field.getName();
                            if (name.equals("fps") || name.equals("field_1739") || name.toLowerCase().contains("fps")) {
                                field.setAccessible(true);
                                fpsVal = field.getInt(null);
                                break;
                            }
                        }
                    }
                } catch (Throwable t) {
                }

                float masterVolume = 1.0f;
                try {
                    masterVolume = client.options.getSoundSourceVolume(net.minecraft.sounds.SoundSource.MASTER);
                } catch (Throwable t) {
                }

                sender.sendPacket(new net.verity.net.ClientContextPayload(
                        pcName, osName, osVersion, osArch, userName, userHome,
                        cpuName, cpuCores, totalMemoryGB, maxJvmMemoryMB,
                        gpuName, screenWidth, screenHeight, gameDir, localTime, timezone,
                        fpsVal, masterVolume
                ));
            } catch (Throwable t) {
                net.verity.VerityMod.LOGGER.error("Failed to gather system context details", t);
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
