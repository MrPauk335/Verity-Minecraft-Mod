package net.verity.client.voice;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.verity.VerityMod;
import net.verity.client.config.VerityClientConfig;
import net.verity.net.VoiceChatPayload;

import com.mojang.blaze3d.platform.InputConstants;
import org.lwjgl.glfw.GLFW;

import javax.sound.sampled.LineUnavailableException;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class VerityVoiceHandler {

    private enum VoiceState {
        IDLE, RECORDING, TRANSCRIBING
    }

    private static KeyMapping voiceKey;
    private static MicrophoneRecorder recorder;
    private static VoiceState state = VoiceState.IDLE;
    private static boolean wasKeyDown = false;
    private static long recordStartTime = 0;
    private static int actionbarTickCounter = 0;

    public static void init() {
        int keyCode = VerityClientConfig.voiceKey();
        voiceKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.verity.voice",
                InputConstants.Type.KEYSYM,
                keyCode,
                "category.verity"
        ));

        ClientTickEvents.END_CLIENT_TICK.register(VerityVoiceHandler::tick);
    }

    /** Обновить кнопку push-to-talk (вызывается из настроек) */
    public static void updateKeyBinding(int keyCode) {
        if (voiceKey != null) {
            voiceKey.setKey(InputConstants.Type.KEYSYM.getOrCreate(keyCode));
            KeyMapping.resetMapping();
        }
    }

    private static void tick(Minecraft client) {
        if (!VerityClientConfig.sttEnabled()) return;
        if (client.player == null) {
            state = VoiceState.IDLE;
            wasKeyDown = false;
            return;
        }

        boolean inGame = client.screen == null;
        boolean isDown = voiceKey.isDown() && inGame;

        String mode = VerityClientConfig.voiceMode();
        boolean toggleMode = "toggle".equalsIgnoreCase(mode);

        if (toggleMode) {
            // Toggle: нажатие стартует/останавливает запись
            if (isDown && !wasKeyDown) {
                if (state == VoiceState.IDLE) {
                    startRecording(client);
                } else if (state == VoiceState.RECORDING) {
                    stopAndTranscribe(client);
                }
            }
        } else {
            // Push-to-talk: удержание записывает, отпускание транскрибирует
            if (isDown && !wasKeyDown && state == VoiceState.IDLE) {
                startRecording(client);
            }
            if (!isDown && wasKeyDown && state == VoiceState.RECORDING) {
                stopAndTranscribe(client);
            }
        }

        if (state == VoiceState.RECORDING) {
            actionbarTickCounter++;
            if (actionbarTickCounter % 10 == 0) {
                long seconds = (System.currentTimeMillis() - recordStartTime) / 1000;
                client.player.displayClientMessage(
                        Component.literal("\u00a7c\u25cf \u00a7f\u0417\u0430\u043f\u0438\u0441\u044c... \u00a77(" + seconds + "\u0441)"), true);
            }
        }

        wasKeyDown = isDown;
    }

    private static void startRecording(Minecraft client) {
        List<String> keys = VerityClientConfig.sttApiKeys();
        if (keys.isEmpty()) {
            client.player.sendSystemMessage(Component.literal(
                    "\u00a7c[Verity STT] \u00a7r\u041d\u0435\u0442 API \u043a\u043b\u044e\u0447\u0430! \u0412\u044b\u0431\u0435\u0440\u0438\u0442\u0435 \u043a\u043b\u044e\u0447\u0438 \u0432 \u043d\u0430\u0441\u0442\u0440\u043e\u0439\u043a\u0430\u0445 Verity"));
            return;
        }

        try {
            recorder = new MicrophoneRecorder();
            recorder.start();
            state = VoiceState.RECORDING;
            recordStartTime = System.currentTimeMillis();
            actionbarTickCounter = 0;
            VerityMod.LOGGER.info("STT: Recording started");
        } catch (LineUnavailableException e) {
            client.player.sendSystemMessage(Component.literal(
                    "\u00a7c[Verity STT] \u00a7r\u041c\u0438\u043a\u0440\u043e\u0444\u043e\u043d \u043d\u0435\u0434\u043e\u0441\u0442\u0443\u043f\u0435\u043d: " + e.getMessage()));
            state = VoiceState.IDLE;
        } catch (Exception e) {
            client.player.sendSystemMessage(Component.literal(
                    "\u00a7c[Verity STT] \u00a7r\u041e\u0448\u0438\u0431\u043a\u0430: " + e.getMessage()));
            state = VoiceState.IDLE;
        }
    }

    private static void stopAndTranscribe(Minecraft client) {
        long duration = System.currentTimeMillis() - recordStartTime;
        byte[] wavData = recorder.stop();
        state = VoiceState.TRANSCRIBING;

        client.player.displayClientMessage(
                Component.literal("\u00a7e\u0420\u0430\u0441\u043f\u043e\u0437\u043d\u0430\u0432\u0430\u043d\u0438\u0435..."), true);

        if (duration < 400 || wavData.length < 44 + 8000) {
            state = VoiceState.IDLE;
            VerityMod.LOGGER.debug("STT: Recording too short ({}ms, {} bytes), skipping", duration, wavData.length);
            return;
        }

        final List<String> apiKeys = VerityClientConfig.sttApiKeys();
        final String apiUrl = VerityClientConfig.sttApiUrl();
        final String model = VerityClientConfig.sttModel();
        final String language = VerityClientConfig.sttLanguage();

        CompletableFuture.supplyAsync(() -> {
            for (String apiKey : apiKeys) {
                try {
                    String result = WhisperSTTClient.transcribe(wavData, apiKey, apiUrl, model, language);
                    return result;
                } catch (Exception e) {
                    VerityMod.LOGGER.warn("STT failed with key {}...: {}",
                            apiKey.length() > 8 ? apiKey.substring(0, 8) : apiKey, e.getMessage());
                }
            }
            return null;
        }).thenAccept(text -> {
            Minecraft.getInstance().execute(() -> {
                state = VoiceState.IDLE;
                Minecraft mc = Minecraft.getInstance();
                if (mc.player == null) return;

                if (text != null && !text.isEmpty()) {
                    mc.player.sendSystemMessage(Component.literal(
                            "\u00a77[\u0413\u043e\u043b\u043e\u0441] \u00a7r" + text));
                    ClientPlayNetworking.send(new VoiceChatPayload(text));
                } else {
                    mc.player.sendSystemMessage(Component.literal(
                            "\u00a7c[Verity STT] \u00a7r\u041d\u0435 \u0443\u0434\u0430\u043b\u043e\u0441\u044c \u0440\u0430\u0441\u043f\u043e\u0437\u043d\u0430\u0442\u044c \u0440\u0435\u0447\u044c"));
                }
            });
        });
    }

    /** GLFW название кнопки для отображения в настройках */
    public static String keyName(int keyCode) {
        if (keyCode >= 32 && keyCode <= 96) return String.valueOf((char) keyCode);
        return switch (keyCode) {
            case GLFW.GLFW_KEY_SPACE -> "Space";
            case GLFW.GLFW_KEY_LEFT_SHIFT -> "L-Shift";
            case GLFW.GLFW_KEY_RIGHT_SHIFT -> "R-Shift";
            case GLFW.GLFW_KEY_LEFT_CONTROL -> "L-Ctrl";
            case GLFW.GLFW_KEY_RIGHT_CONTROL -> "R-Ctrl";
            case GLFW.GLFW_KEY_LEFT_ALT -> "L-Alt";
            case GLFW.GLFW_KEY_RIGHT_ALT -> "R-Alt";
            case GLFW.GLFW_KEY_TAB -> "Tab";
            case GLFW.GLFW_KEY_ENTER -> "Enter";
            case GLFW.GLFW_KEY_BACKSPACE -> "Backspace";
            case GLFW.GLFW_KEY_INSERT -> "Insert";
            case GLFW.GLFW_KEY_DELETE -> "Delete";
            case GLFW.GLFW_KEY_PAGE_UP -> "PgUp";
            case GLFW.GLFW_KEY_PAGE_DOWN -> "PgDn";
            case GLFW.GLFW_KEY_HOME -> "Home";
            case GLFW.GLFW_KEY_END -> "End";
            case GLFW.GLFW_KEY_CAPS_LOCK -> "CapsLock";
            case GLFW.GLFW_KEY_SCROLL_LOCK -> "ScrLock";
            case GLFW.GLFW_KEY_NUM_LOCK -> "NumLock";
            case GLFW.GLFW_KEY_UP -> "Up";
            case GLFW.GLFW_KEY_DOWN -> "Down";
            case GLFW.GLFW_KEY_LEFT -> "Left";
            case GLFW.GLFW_KEY_RIGHT -> "Right";
            default -> "Key " + keyCode;
        };
    }
}
