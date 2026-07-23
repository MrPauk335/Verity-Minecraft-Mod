package net.verity.client.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.verity.config.VerityConfig;
import net.verity.client.config.VerityClientConfig;
import net.verity.client.voice.VerityVoiceHandler;

import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;

public class VeritySettingsScreen extends Screen {

    private static final Path CONFIG_PATH = Path.of("config", "verity-server.properties");
    private static final Path CLIENT_CONFIG_PATH = Path.of("config", "verity-client.properties");

    private static final int BTN_W = 90;
    private static final int BTN_H = 18;
    private static final int LABEL_W = 130;
    private static final int MODEL_BTN_W = 220;
    private static final int PANEL_W = 340;
    private static final int ROW_STEP = 28;
    private static final int BOX_H = 18;

    private EditBox apiKeyBox;
    private EditBox geminiKeyBox;
    private EditBox sttKeyBox;
    private EditBox ttsKeyBox;
    private boolean llmEnabled;
    private boolean soundsEnabled;
    private boolean chatEnabled;
    private boolean alwaysRespond;
    private boolean monsterFormEnabled;
    private boolean sttEnabled;
    private boolean ttsEnabled;

    private int selectedModelIndex;
    private final Screen parent;

    private static final List<String> MODELS = VerityConfig.AVAILABLE_MODELS;
    private static final List<String> GEMINI_MODELS = java.util.List.of(
            "gemini-3.1-flash-live-preview",
            "gemini-3-flash-preview",
            "gemini-2.5-flash",
            "gemini-2.5-flash-lite",
            "gemma-4-31b-it"
    );
    private static String modelDisplayName(String modelId) {
        if (modelId.contains("owl-alpha")) return "Owl Alpha (free)";
        if (modelId.contains("openrouter/free")) return "Auto-Free-Router (free)";
        if (modelId.contains("gemma-2-9b")) return "Gemma 2 9B (free)";
        if (modelId.contains("llama-3.3-70b")) return "Llama 3.3 70B (free)";
        if (modelId.contains("qwen-2.5-72b")) return "Qwen 2.5 72B (free)";
        if (modelId.contains("nemotron-4-340b")) return "Nemotron 4 340B (free)";
        if (modelId.contains("hermes-3-llama")) return "Hermes 3 405B (free)";
        
        // OpenCode Zen models
        if (modelId.contains("deepseek-v4-flash-free")) return "DeepSeek V4 Flash (free)";
        if (modelId.contains("big-pickle")) return "Big Pickle (free)";
        if (modelId.contains("mimo-v2.5-free")) return "MiMo V2.5 (free)";
        if (modelId.contains("deepseek-v4-flash")) return "DeepSeek V4 Flash";
        if (modelId.contains("deepseek-v4-pro")) return "DeepSeek V4 Pro";
        if (modelId.equals("gemini-3-flash")) return "Gemini 3 Flash (Zen)";

        // Gemini models
        if (modelId.contains("gemini-3.1-flash-live")) return "Gemini 3.1 Flash Live";
        if (modelId.contains("gemini-3-flash")) return "Gemini 3 Flash";
        if (modelId.contains("gemini-2.5-flash-lite")) return "Gemini 2.5 Flash Lite";
        if (modelId.contains("gemini-2.5-flash")) return "Gemini 2.5 Flash";
        return modelId;
    }

    private boolean useBuiltinKeys;
    private String llmProvider;
    private int selectedGeminiModelIndex;
    private int voiceKeyCode;
    private String voiceMode;
    private boolean listeningForKey = false;
    private Button pttKeyButton;

    // Scroll support
    private int scrollY = 0;
    private int contentHeight = 0;

    // Stored Y positions (computed in init, used in render)
    private int yTitle, ySection1, yKeySrc, yKeyStatus, yApiKeyLabel, yApiKeyBox;
    private int yModelLabel, yModelBtn, yToggleStart;
    private int ySection2, yPttRow, yGroqLabel, yGroqBox, yVoiceToggle, yVoiceStatus;
    private int yTtsToggle, yTtsKeyLabel, yTtsKeyBox;
    private int yProvider, yGeminiKeyLabel, yGeminiKeyBox, yGeminiModelBtn;

    public VeritySettingsScreen(Screen parent) {
        super(Component.literal("Verity\u2122 \u2014 Settings"));
        this.parent = parent;
        this.llmEnabled         = VerityConfig.llmEnabled();
        this.soundsEnabled      = VerityConfig.soundsEnabled();
        this.chatEnabled        = VerityConfig.chatEnabled();
        this.alwaysRespond      = VerityConfig.alwaysRespond();
        this.monsterFormEnabled = VerityConfig.monsterFormEnabled();
        this.sttEnabled         = VerityClientConfig.sttEnabled();
        this.ttsEnabled         = VerityClientConfig.ttsEnabled();
        this.useBuiltinKeys     = VerityConfig.useBuiltinKeys();
        this.llmProvider        = VerityConfig.llmProvider();
        this.voiceKeyCode       = VerityClientConfig.voiceKey();
        this.voiceMode          = VerityClientConfig.voiceMode();

        String currentModel = VerityConfig.selectedModel();
        this.selectedModelIndex = 0;
        for (int i = 0; i < MODELS.size(); i++) {
            if (MODELS.get(i).equals(currentModel)) {
                this.selectedModelIndex = i;
                break;
            }
        }

        String currentGeminiModel = VerityConfig.geminiModel();
        this.selectedGeminiModelIndex = 0;
        for (int i = 0; i < GEMINI_MODELS.size(); i++) {
            if (GEMINI_MODELS.get(i).equals(currentGeminiModel)) {
                this.selectedGeminiModelIndex = i;
                break;
            }
        }
    }

    private int contentTop()    { return 34; }
    private int contentBottom() { return this.height - 30; }
    private int contentSpace()  { return contentBottom() - contentTop(); }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int boxW = Math.min(300, PANEL_W - 40);
        int s = -scrollY;

        // ── Compute all Y positions (single source of truth) ──────────────
        int y = contentTop() + s;
        ySection1     = y;  y += 14;
        yProvider     = y;  y += BTN_H + 6;
        yKeySrc       = y;  y += BTN_H + 6;
        yKeyStatus    = y;  y += 12;
        yApiKeyLabel  = y;  y += 12;
        yApiKeyBox    = y;  y += BOX_H + 10;
        yModelLabel   = y;  y += 12;
        yModelBtn     = y;  y += BTN_H + 10;
        yGeminiKeyLabel = y;  y += 12;
        yGeminiKeyBox   = y;  y += BOX_H + 8;
        yGeminiModelBtn = y;  y += BTN_H + 10;
        yToggleStart  = y;  y += ROW_STEP * 5 + 6;
        ySection2     = y;  y += 14;
        yPttRow       = y;  y += BTN_H + 10;
        yGroqLabel    = y;  y += 12;
        yGroqBox      = y;  y += BOX_H + 8;
        yVoiceToggle  = y;  y += BTN_H + 6;
        yVoiceStatus  = y;  y += 14;
        yTtsToggle    = y;  y += BTN_H + 6;
        yTtsKeyLabel  = y;  y += 12;
        yTtsKeyBox    = y;  y += BOX_H + 8;
        contentHeight = y - s - contentTop();

        // Clamp scroll
        int maxScroll = Math.max(0, contentHeight - contentSpace());
        if (scrollY > maxScroll) scrollY = maxScroll;
        if (scrollY < 0) scrollY = 0;

        // ── Provider selector (OpenRouter / Gemini / Groq / Cohere / OpenCode Zen) ──
        this.addRenderableWidget(CycleButton.<String>builder(m -> {
            switch (m) {
                case "gemini": return Component.literal("\u00a7bGemini (Google)");
                case "groq": return Component.literal("\u00a7aGroq (Ultra-Fast)");
                case "cohere": return Component.literal("\u00a7dCohere (Smart/Russian)");
                case "opencode": return Component.literal("\u00a7cOpenCode Zen (Free Models)");
                default: return Component.literal("\u00a7eOpenRouter");
            }
        })
                .withValues("openrouter", "gemini", "groq", "cohere", "opencode")
                .withInitialValue(llmProvider)
                .displayOnlyValue()
                .create(cx - 115, yProvider, 230, BTN_H,
                        Component.literal("Provider"),
                        (btn, val) -> {
                            llmProvider = val;
                            updateKeyBoxHints();
                        }));

        // ── Key Source toggle ──────────────────────────────────────────────
        this.addRenderableWidget(CycleButton.<String>builder(m -> Component.literal(
                        "builtin".equals(m)
                                ? "\u00a7a\u2713 Built-in keys (mod)"
                                : "\u00a7e\u26a0 Custom keys (own)"))
                .withValues("builtin", "custom")
                .withInitialValue(useBuiltinKeys ? "builtin" : "custom")
                .displayOnlyValue()
                .create(cx - 115, yKeySrc, 230, BTN_H,
                        Component.literal("Keys"),
                        (btn, val) -> {
                            useBuiltinKeys = "builtin".equals(val);
                            updateKeyBoxHints();
                        }));

        // ── API Key input ──────────────────────────────────────────────────
        this.apiKeyBox = new EditBox(this.font,
                cx - boxW / 2, yApiKeyBox, boxW, BOX_H,
                Component.literal("API Key"));
        this.apiKeyBox.setMaxLength(512);
        this.apiKeyBox.setValue(VerityConfig.customApiKey());
        maskEditBox(this.apiKeyBox);
        this.addRenderableWidget(this.apiKeyBox);

        // ── Model selector ─────────────────────────────────────────────────
        this.addRenderableWidget(CycleButton.<String>builder(m -> Component.literal(modelDisplayName(m)))
                .withValues(MODELS)
                .withInitialValue(MODELS.get(selectedModelIndex))
                .displayOnlyValue()
                .create(cx - MODEL_BTN_W / 2, yModelBtn, MODEL_BTN_W, BTN_H,
                        Component.literal("Model"),
                        (btn, val) -> selectedModelIndex = MODELS.indexOf(val)));

        // ── Gemini API Key input ────────────────────────────────────────────
        this.geminiKeyBox = new EditBox(this.font,
                cx - boxW / 2, yGeminiKeyBox, boxW, BOX_H,
                Component.literal("Gemini API Key"));
        this.geminiKeyBox.setMaxLength(512);
        this.geminiKeyBox.setValue(VerityConfig.geminiApiKey());
        maskEditBox(this.geminiKeyBox);
        this.geminiKeyBox.setHint(Component.literal("\u00a78Built-in keys included. Add your own (optional)"));
        this.addRenderableWidget(this.geminiKeyBox);

        // ── Gemini model selector ──────────────────────────────────────────
        this.addRenderableWidget(CycleButton.<String>builder(m -> Component.literal(modelDisplayName(m)))
                .withValues(GEMINI_MODELS)
                .withInitialValue(GEMINI_MODELS.get(selectedGeminiModelIndex))
                .displayOnlyValue()
                .create(cx - MODEL_BTN_W / 2, yGeminiModelBtn, MODEL_BTN_W, BTN_H,
                        Component.literal("Gemini Model"),
                        (btn, val) -> selectedGeminiModelIndex = GEMINI_MODELS.indexOf(val)));

        // ── Toggle rows ────────────────────────────────────────────────────
        int btnX = cx + 25;
        addToggle(btnX, yToggleStart,              "LLM Responses",  this.llmEnabled,        v -> this.llmEnabled = v);
        addToggle(btnX, yToggleStart + ROW_STEP,   "Sounds",         this.soundsEnabled,     v -> this.soundsEnabled = v);
        addToggle(btnX, yToggleStart + ROW_STEP*2, "Chat Responses", this.chatEnabled,       v -> this.chatEnabled = v);
        addToggle(btnX, yToggleStart + ROW_STEP*3, "Always Respond", this.alwaysRespond,     v -> this.alwaysRespond = v);
        addToggle(btnX, yToggleStart + ROW_STEP*4, "Monster Form",   this.monsterFormEnabled, v -> this.monsterFormEnabled = v);

        // ── Voice: PTT key + mode ──────────────────────────────────────────
        int pttBtnW = 105;
        this.pttKeyButton = Button.builder(
                Component.literal("\u00a7fPTT: \u00a7e" + VerityVoiceHandler.keyName(voiceKeyCode)),
                btn -> {
                    listeningForKey = true;
                    btn.setMessage(Component.literal("\u00a7ePress a key..."));
                })
            .bounds(cx - pttBtnW - 4, yPttRow, pttBtnW, BTN_H)
            .build();
        this.addRenderableWidget(this.pttKeyButton);

        this.addRenderableWidget(CycleButton.<String>builder(m -> Component.literal(
                        "push".equals(m) ? "\u00a7fPush-to-Talk" : "\u00a7fToggle"))
                .withValues("push", "toggle")
                .withInitialValue(voiceMode)
                .displayOnlyValue()
                .create(cx + 4, yPttRow, pttBtnW, BTN_H,
                        Component.literal("Mode"),
                        (btn, val) -> voiceMode = val));

        // ── Groq key box ───────────────────────────────────────────────────
        this.sttKeyBox = new EditBox(this.font,
                cx - boxW / 2, yGroqBox, boxW, BOX_H,
                Component.literal("Groq API Key"));
        this.sttKeyBox.setMaxLength(512);
        this.sttKeyBox.setValue(VerityClientConfig.sttApiKey());
        maskEditBox(this.sttKeyBox);
        this.addRenderableWidget(this.sttKeyBox);

        updateKeyBoxHints();

        // ── Voice toggle ───────────────────────────────────────────────────
        addToggle(btnX, yVoiceToggle, "Voice (Push-to-Talk)", this.sttEnabled, v -> this.sttEnabled = v);

        // ── TTS toggle + Fish Audio key ─────────────────────────────────────
        addToggle(btnX, yTtsToggle, "TTS (Voice Output)", this.ttsEnabled, v -> this.ttsEnabled = v);

        this.ttsKeyBox = new EditBox(this.font,
                cx - boxW / 2, yTtsKeyBox, boxW, BOX_H,
                Component.literal("Fish Audio API Key"));
        this.ttsKeyBox.setMaxLength(512);
        this.ttsKeyBox.setValue(VerityClientConfig.ttsApiKey());
        maskEditBox(this.ttsKeyBox);
        this.ttsKeyBox.setHint(Component.literal("\u00a78fish.audio \u2192 API Keys"));
        this.addRenderableWidget(this.ttsKeyBox);

        // ── Done button (fixed at bottom, not scrolled) ────────────────────
        int doneW = 140;
        int doneH = 20;
        this.addRenderableWidget(Button.builder(
                Component.literal("\u00a7a\u2714  Done"),
                btn -> this.onDone())
            .bounds(cx - doneW / 2, this.height - doneH - 8, doneW, doneH)
            .build());
    }

    private void updateKeyBoxHints() {
        if (apiKeyBox == null || sttKeyBox == null) return;
        if (useBuiltinKeys) {
            apiKeyBox.setHint(Component.literal("\u00a78Built-in keys \u2014 optional"));
            sttKeyBox.setHint(Component.literal("\u00a78Built-in Groq keys \u2014 optional"));
        } else {
            sttKeyBox.setHint(Component.literal("\u00a78gsk_...  (console.groq.com/keys \u2014 free)"));
            switch (String.valueOf(llmProvider).toLowerCase()) {
                case "groq":
                    apiKeyBox.setHint(Component.literal("\u00a78gsk_...  (console.groq.com/keys \u2014 free)"));
                    break;
                case "cohere":
                    apiKeyBox.setHint(Component.literal("\u00a78trial_...  (dashboard.cohere.com/api-keys)"));
                    break;
                case "gemini":
                    apiKeyBox.setHint(Component.literal("\u00a78AI...  (aistudio.google.com/apikey)"));
                    break;
                case "opencode":
                    apiKeyBox.setHint(Component.literal("\u00a78Zen API key (opencode.ai/auth \u2014 free models available)"));
                    break;
                default:
                    apiKeyBox.setHint(Component.literal("\u00a78sk-or-v1-...  (openrouter.ai/keys)"));
                    break;
            }
        }
    }

    private void addToggle(int btnX, int y, String label, boolean initial,
                           java.util.function.Consumer<Boolean> onChange) {
        this.addRenderableWidget(
            CycleButton.onOffBuilder(initial)
                .displayOnlyValue()
                .withCustomNarration(b -> Component.empty())
                .create(btnX, y, BTN_W, BTN_H,
                        Component.literal(label),
                        (btn, val) -> onChange.accept(val)));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int maxScroll = Math.max(0, contentHeight - contentSpace());
        int oldScroll = this.scrollY;
        this.scrollY = Math.max(0, Math.min(maxScroll, this.scrollY + (int)(-scrollY * 14)));
        if (this.scrollY != oldScroll) {
            this.rebuildWidgets();
        }
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (listeningForKey) {
            if (keyCode != GLFW.GLFW_KEY_ESCAPE) {
                voiceKeyCode = keyCode;
            }
            listeningForKey = false;
            if (pttKeyButton != null) {
                pttKeyButton.setMessage(Component.literal("\u00a7fPTT: \u00a7e" + VerityVoiceHandler.keyName(voiceKeyCode)));
            }
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void onDone() {
        saveServerConfig();
        saveClientConfig();
        VerityConfig.forceReload();
        VerityClientConfig.load();
        net.verity.ai.VerityLLMClient.reloadFromConfig();
        VerityVoiceHandler.updateKeyBinding(voiceKeyCode);
        if (this.minecraft != null) this.minecraft.setScreen(this.parent);
    }

    private static String encryptForKeyConfig(String plain) {
        if (plain == null || plain.isEmpty()) return plain;
        if (plain.startsWith("enc:")) return plain;
        byte[] encoded = net.verity.config.KeyVault.encode(plain);
        return "enc:" + java.util.Base64.getEncoder().encodeToString(encoded);
    }

    private static String decryptForKeyConfig(String stored) {
        if (stored == null || stored.isEmpty()) return stored;
        if (!stored.startsWith("enc:")) return stored;
        try {
            byte[] encoded = java.util.Base64.getDecoder().decode(stored.substring(4));
            return net.verity.config.KeyVault.decode(encoded);
        } catch (Exception e) {
            return stored;
        }
    }

    private static void maskEditBox(EditBox box) {
        box.setFormatter((text, pos) -> FormattedCharSequence.forward("*".repeat(text.length()), Style.EMPTY));
    }

    private void saveServerConfig() {
        try {
            Properties p = new Properties();
            if (Files.exists(CONFIG_PATH)) {
                try (var r = Files.newBufferedReader(CONFIG_PATH)) { p.load(r); }
            }
            p.setProperty("key_source",          useBuiltinKeys ? "builtin" : "custom");
            p.setProperty("llm_provider",        llmProvider);
            p.setProperty("custom_api_key",      encryptForKeyConfig(this.apiKeyBox.getValue().trim()));
            p.setProperty("selected_model",      MODELS.get(selectedModelIndex));
            p.setProperty("gemini_api_key",      encryptForKeyConfig(this.geminiKeyBox != null ? this.geminiKeyBox.getValue().trim() : ""));
            p.setProperty("gemini_model",        GEMINI_MODELS.get(selectedGeminiModelIndex));
            p.setProperty("llm_enabled",         String.valueOf(this.llmEnabled));
            p.setProperty("sounds_enabled",      String.valueOf(this.soundsEnabled));
            p.setProperty("chat_enabled",        String.valueOf(this.chatEnabled));
            p.setProperty("always_respond",      String.valueOf(this.alwaysRespond));
            p.setProperty("monster_form_enabled",String.valueOf(this.monsterFormEnabled));
            p.setProperty("config_version",      "6");
            Files.createDirectories(CONFIG_PATH.getParent());
            try (PrintWriter pw = new PrintWriter(
                    Files.newBufferedWriter(CONFIG_PATH, StandardCharsets.UTF_8))) {
                p.store(pw, "Verity Mod Config");
            }
        } catch (IOException e) {
            net.verity.VerityMod.LOGGER.error("Failed to save Verity config: {}", e.getMessage());
        }
    }

    private void saveClientConfig() {
        try {
            Properties p = new Properties();
            if (Files.exists(CLIENT_CONFIG_PATH)) {
                try (var r = Files.newBufferedReader(CLIENT_CONFIG_PATH)) { p.load(r); }
            }
            p.setProperty("stt_enabled",    String.valueOf(this.sttEnabled));
            p.setProperty("stt_key_source", useBuiltinKeys ? "builtin" : "custom");
            p.setProperty("stt_api_key",    this.sttKeyBox.getValue().trim());
            p.setProperty("voice_key",      String.valueOf(this.voiceKeyCode));
            p.setProperty("voice_mode",     this.voiceMode);
            p.setProperty("tts_enabled",    String.valueOf(this.ttsEnabled));
            p.setProperty("tts_api_key",    this.ttsKeyBox != null ? this.ttsKeyBox.getValue().trim() : "");
            if (!p.containsKey("tts_voice_id")) p.setProperty("tts_voice_id", "b3c51bf6029f4201a342b40827250784");
            if (!p.containsKey("tts_model")) p.setProperty("tts_model", "s2.1-pro-free");
            if (!p.containsKey("tts_speed")) p.setProperty("tts_speed", "1.0");
            if (!p.containsKey("stt_api_url")) p.setProperty("stt_api_url", "https://api.groq.com/openai/v1/audio/transcriptions");
            if (!p.containsKey("stt_model"))  p.setProperty("stt_model", "whisper-large-v3");
            if (!p.containsKey("stt_language")) p.setProperty("stt_language", "ru");
            Files.createDirectories(CLIENT_CONFIG_PATH.getParent());
            try (PrintWriter pw = new PrintWriter(
                    Files.newBufferedWriter(CLIENT_CONFIG_PATH, StandardCharsets.UTF_8))) {
                p.store(pw, "Verity Client Config");
            }
        } catch (IOException e) {
            net.verity.VerityMod.LOGGER.error("Failed to save client config: {}", e.getMessage());
        }
    }

    // ── Rendering ───────────────────────────────────────────────────────────
    @Override
    public void renderBackground(GuiGraphics gfx, int mouseX, int mouseY, float partial) {
        gfx.fill(0, 0, this.width, this.height, 0xFF0A0810);
        gfx.fill(0, 0, this.width, 4, 0xFFD4A017);
        gfx.fill(0, 4, this.width, 5, 0xFF8B6914);
        gfx.fill(0, this.height - 5, this.width, this.height, 0xFF6B5010);
        gfx.fill(0, this.height - 4, this.width, this.height, 0xFFD4A017);
    }

    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float delta) {
        this.renderBackground(gfx, mouseX, mouseY, delta);
        int cx = this.width / 2;
        int panelX = cx - PANEL_W / 2;
        int panelRight = cx + PANEL_W / 2;
        int labelX = cx + 25 - 8 - LABEL_W;

        // ── Panel ──────────────────────────────────────────────────────────
        gfx.fill(panelX - 2, 28, panelRight + 2, this.height - 22, 0xCC12101A);
        gfx.fill(panelX - 2, 28, panelRight + 2, 29, 0xFFD4A017);
        gfx.fill(panelX - 2, this.height - 23, panelRight + 2, this.height - 22, 0xFF6B5010);
        gfx.fill(panelX - 2, 28, panelX - 1, this.height - 22, 0xFF3A2E10);
        gfx.fill(panelRight + 1, 28, panelRight + 2, this.height - 22, 0xFF3A2E10);

        // ── Title (fixed, not scrolled) ────────────────────────────────────
        gfx.drawCenteredString(this.font,
                Component.literal("\u00a76\u00a7l\u2726 Verity\u2122 \u00a7r\u00a77Settings \u00a76\u00a7l\u2725"),
                cx, 14, 0xFFFFFF);

        // ── Scroll indicator ───────────────────────────────────────────────
        int maxScroll = Math.max(0, contentHeight - contentSpace());
        if (maxScroll > 0) {
            int barH = Math.max(20, contentSpace() * contentSpace() / contentHeight);
            int barY = contentTop() + (contentSpace() - barH) * scrollY / maxScroll;
            gfx.fill(panelRight + 4, barY, panelRight + 7, barY + barH, 0xFFD4A017);
            gfx.fill(panelRight + 4, contentTop(), panelRight + 5, contentBottom(), 0xFF333020);
        }

        // ── Clipped content ────────────────────────────────────────────────
        gfx.enableScissor(panelX - 2, contentTop() - 2, panelRight + 2, contentBottom());

        // Section 1 header
        drawSectionHeader(gfx, cx, panelX + 10, panelRight - 10, ySection1, "\u00a7e\u00a7lAI Configuration");

        // Key status
        String keyStatus = useBuiltinKeys
                ? "\u00a7a\u2713 Built-in keys (2 OpenRouter + 6 Groq)"
                : "\u00a7e\u26a0 Custom keys \u2014 enter your keys";
        gfx.drawCenteredString(this.font, Component.literal(keyStatus), cx, yKeyStatus, 0xAAAAAA);

        // API key label
        String apiKeyLabel = useBuiltinKeys
                ? "\u00a77Custom OpenRouter Key (optional)"
                : "\u00a77OpenRouter API Key (required)";
        gfx.drawCenteredString(this.font, Component.literal(apiKeyLabel), cx, yApiKeyLabel, 0xBBBBBB);

        // Model label
        gfx.drawCenteredString(this.font, Component.literal("\u00a77OpenRouter Model"), cx, yModelLabel, 0xBBBBBB);

        // Gemini key label
        gfx.drawCenteredString(this.font, Component.literal("\u00a77Gemini API Key (built-in included)"), cx, yGeminiKeyLabel, 0xBBBBBB);

        // Gemini model label is on the button itself

        // Toggle labels
        String[] labelNames = { "LLM Responses", "Sounds", "Chat Responses", "Always Respond", "Monster Form" };
        for (int i = 0; i < labelNames.length; i++) {
            int ty = yToggleStart + ROW_STEP * i + (BTN_H - this.font.lineHeight) / 2;
            gfx.drawString(this.font, "\u00a7f" + labelNames[i], labelX, ty, 0xDDDDDD);
        }

        // Section 2 header
        drawSectionHeader(gfx, cx, panelX + 10, panelRight - 10, ySection2, "\u00a7e\u00a7lVoice (Push-to-Talk)");

        // PTT / Mode labels
        int pttLabelY = yPttRow + (BTN_H - this.font.lineHeight) / 2;
        gfx.drawString(this.font, "\u00a77Key", cx - 108 - 30, pttLabelY, 0xBBBBBB);
        gfx.drawString(this.font, "\u00a77Mode", cx + 108 + 8, pttLabelY, 0xBBBBBB);

        // Groq key label
        String sttKeyLabel = useBuiltinKeys
                ? "\u00a77Custom Groq Key (optional)"
                : "\u00a77Groq API Key (required \u2014 console.groq.com/keys)";
        gfx.drawCenteredString(this.font, Component.literal(sttKeyLabel), cx, yGroqLabel, 0xBBBBBB);

        // Voice toggle label
        int sttLabelTextY = yVoiceToggle + (BTN_H - this.font.lineHeight) / 2;
        gfx.drawString(this.font, "\u00a7fVoice (Push-to-Talk)", labelX, sttLabelTextY, 0xDDDDDD);

        // Voice status
        boolean hasKeys = useBuiltinKeys || !this.sttKeyBox.getValue().trim().isEmpty();
        String modeLabel = "toggle".equals(voiceMode)
                ? "toggle " + VerityVoiceHandler.keyName(voiceKeyCode)
                : "hold " + VerityVoiceHandler.keyName(voiceKeyCode);
        String sttStatus = hasKeys
                ? "\u00a7a\u2713 Ready \u2014 " + modeLabel + " to talk"
                : "\u00a7c\u2717 No key \u2014 console.groq.com/keys";
        gfx.drawCenteredString(this.font, Component.literal(sttStatus),
                cx, yVoiceStatus, hasKeys ? 0x77FF77 : 0xFF7777);

        // TTS label
        int ttsLabelY = yTtsToggle + (BTN_H - this.font.lineHeight) / 2;
        gfx.drawString(this.font, "\u00a7fTTS (Voice Output)", labelX, ttsLabelY, 0xDDDDDD);

        // TTS key label
        gfx.drawCenteredString(this.font,
                Component.literal("\u00a77Fish Audio API Key (fish.audio \u2192 API Keys)"),
                cx, yTtsKeyLabel, 0xBBBBBB);

        gfx.disableScissor();

        // ── Bottom hint (fixed) ────────────────────────────────────────────
        gfx.drawCenteredString(this.font,
                Component.literal("\u00a78Groq \u2014 free STT \u00b7 OpenRouter \u2014 free LLM \u00b7 Say 'music' to Verity"),
                cx, this.height - 34, 0x666666);

        // ── Widgets ────────────────────────────────────────────────────────
        super.render(gfx, mouseX, mouseY, delta);
    }

    private void drawSectionHeader(GuiGraphics gfx, int cx, int leftX, int rightX, int y, String text) {
        gfx.fill(leftX, y + 4, rightX, y + 5, 0xFF444030);
        gfx.drawCenteredString(this.font, Component.literal(text), cx, y, 0xFFD4A017);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
