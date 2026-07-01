package net.verity.config;

import net.verity.VerityMod;
import net.verity.entity.VerityEntity.VerityPhase;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Центральная конфигурация Verity.
 * Загружается из config/verity-server.properties при старте сервера.
 * Все опции можно менять "на лету" — Verity перечитывает конфиг каждый тик (0.05 сек).
 */
public final class VerityConfig {

    private static final Path CONFIG_PATH = Path.of("config", "verity-server.properties");
    private static Properties props = new Properties();
    private static long lastModifiedMs = 0;
    private static boolean loaded = false;

    private static final byte[][] ENCODED_API_KEYS = new byte[][] {
        {(byte)0x29, (byte)0x57, (byte)0x52, (byte)0x4E, (byte)0x19, (byte)0x20, (byte)0x38, (byte)0xA2, (byte)0x77, (byte)0x08, (byte)0x4C, (byte)0x42, (byte)0x5B, (byte)0x68, (byte)0x2A, (byte)0xA0, (byte)0x6E, (byte)0x59, (byte)0x4C, (byte)0x12, (byte)0x0E, (byte)0x35, (byte)0x7A, (byte)0xA4, (byte)0x3E, (byte)0x5D, (byte)0x1D, (byte)0x43, (byte)0x53, (byte)0x6C, (byte)0x2C, (byte)0xA2, (byte)0x3F, (byte)0x59, (byte)0x4C, (byte)0x14, (byte)0x09, (byte)0x6C, (byte)0x7B, (byte)0xAB, (byte)0x3F, (byte)0x5E, (byte)0x4A, (byte)0x12, (byte)0x0E, (byte)0x3A, (byte)0x7C, (byte)0xA2, (byte)0x3B, (byte)0x59, (byte)0x1B, (byte)0x16, (byte)0x59, (byte)0x3A, (byte)0x2D, (byte)0xA1, (byte)0x62, (byte)0x0F, (byte)0x4A, (byte)0x13, (byte)0x08, (byte)0x6F, (byte)0x7C, (byte)0xA5, (byte)0x63, (byte)0x04, (byte)0x49, (byte)0x44, (byte)0x5B, (byte)0x3C, (byte)0x76, (byte)0xF7, (byte)0x63},
        {(byte)0x29, (byte)0x57, (byte)0x52, (byte)0x4E, (byte)0x19, (byte)0x20, (byte)0x38, (byte)0xA2, (byte)0x77, (byte)0x0F, (byte)0x4E, (byte)0x40, (byte)0x5C, (byte)0x69, (byte)0x2C, (byte)0xF7, (byte)0x38, (byte)0x0F, (byte)0x46, (byte)0x43, (byte)0x0D, (byte)0x69, (byte)0x7C, (byte)0xA7, (byte)0x62, (byte)0x0C, (byte)0x1E, (byte)0x18, (byte)0x0A, (byte)0x3B, (byte)0x2B, (byte)0xA5, (byte)0x6B, (byte)0x09, (byte)0x4F, (byte)0x10, (byte)0x0A, (byte)0x39, (byte)0x7A, (byte)0xA2, (byte)0x6F, (byte)0x0D, (byte)0x48, (byte)0x19, (byte)0x0D, (byte)0x3A, (byte)0x77, (byte)0xA1, (byte)0x38, (byte)0x04, (byte)0x4D, (byte)0x40, (byte)0x5F, (byte)0x69, (byte)0x7F, (byte)0xF0, (byte)0x3B, (byte)0x5F, (byte)0x48, (byte)0x16, (byte)0x52, (byte)0x6F, (byte)0x7B, (byte)0xF7, (byte)0x3F, (byte)0x08, (byte)0x4C, (byte)0x42, (byte)0x5D, (byte)0x35, (byte)0x2F, (byte)0xF1, (byte)0x3B},
    };
    private static final java.util.List<String> BUILTIN_API_KEYS = net.verity.config.KeyVault.decodeAll(ENCODED_API_KEYS);

    private static final byte[][] ENCODED_GROQ_KEYS = new byte[][] {
        {(byte)0x3D, (byte)0x4F, (byte)0x14, (byte)0x7E, (byte)0x21, (byte)0x68, (byte)0x04, (byte)0xE0, (byte)0x1B, (byte)0x4F, (byte)0x25, (byte)0x60, (byte)0x1D, (byte)0x79, (byte)0x7D, (byte)0xA7, (byte)0x31, (byte)0x53, (byte)0x37, (byte)0x6F, (byte)0x58, (byte)0x77, (byte)0x3C, (byte)0xE7, (byte)0x0D, (byte)0x7B, (byte)0x1B, (byte)0x58, (byte)0x09, (byte)0x3E, (byte)0x08, (byte)0xCA, (byte)0x18, (byte)0x7B, (byte)0x4B, (byte)0x40, (byte)0x3F, (byte)0x6E, (byte)0x07, (byte)0xFB, (byte)0x0C, (byte)0x6D, (byte)0x28, (byte)0x17, (byte)0x3F, (byte)0x4F, (byte)0x7C, (byte)0xC3, (byte)0x68, (byte)0x75, (byte)0x37, (byte)0x75, (byte)0x25, (byte)0x42, (byte)0x2D, (byte)0xFA},
        {(byte)0x3D, (byte)0x4F, (byte)0x14, (byte)0x7E, (byte)0x06, (byte)0x38, (byte)0x1B, (byte)0xD0, (byte)0x35, (byte)0x59, (byte)0x1B, (byte)0x4F, (byte)0x25, (byte)0x47, (byte)0x02, (byte)0xCB, (byte)0x32, (byte)0x6E, (byte)0x16, (byte)0x6E, (byte)0x39, (byte)0x57, (byte)0x08, (byte)0xD7, (byte)0x0D, (byte)0x7B, (byte)0x1B, (byte)0x58, (byte)0x09, (byte)0x3E, (byte)0x08, (byte)0xCA, (byte)0x6D, (byte)0x4C, (byte)0x08, (byte)0x78, (byte)0x59, (byte)0x5B, (byte)0x05, (byte)0xF4, (byte)0x0A, (byte)0x58, (byte)0x12, (byte)0x10, (byte)0x2A, (byte)0x44, (byte)0x0B, (byte)0xFE, (byte)0x36, (byte)0x69, (byte)0x49, (byte)0x49, (byte)0x0E, (byte)0x77, (byte)0x2B, (byte)0xD2},
        {(byte)0x3D, (byte)0x4F, (byte)0x14, (byte)0x7E, (byte)0x12, (byte)0x5D, (byte)0x05, (byte)0xD4, (byte)0x3D, (byte)0x6D, (byte)0x0A, (byte)0x46, (byte)0x13, (byte)0x63, (byte)0x2C, (byte)0xE5, (byte)0x19, (byte)0x5D, (byte)0x2A, (byte)0x40, (byte)0x38, (byte)0x46, (byte)0x1E, (byte)0xE4, (byte)0x0D, (byte)0x7B, (byte)0x1B, (byte)0x58, (byte)0x09, (byte)0x3E, (byte)0x08, (byte)0xCA, (byte)0x1B, (byte)0x7F, (byte)0x2B, (byte)0x77, (byte)0x05, (byte)0x57, (byte)0x2F, (byte)0xD7, (byte)0x6F, (byte)0x59, (byte)0x18, (byte)0x74, (byte)0x5D, (byte)0x67, (byte)0x27, (byte)0xDE, (byte)0x19, (byte)0x4B, (byte)0x26, (byte)0x10, (byte)0x1D, (byte)0x58, (byte)0x05, (byte)0xC3},
        {(byte)0x3D, (byte)0x4F, (byte)0x14, (byte)0x7E, (byte)0x11, (byte)0x5D, (byte)0x34, (byte)0xF8, (byte)0x20, (byte)0x78, (byte)0x31, (byte)0x50, (byte)0x39, (byte)0x6C, (byte)0x3F, (byte)0xF0, (byte)0x63, (byte)0x69, (byte)0x1B, (byte)0x73, (byte)0x5C, (byte)0x6B, (byte)0x14, (byte)0xF0, (byte)0x0D, (byte)0x7B, (byte)0x1B, (byte)0x58, (byte)0x09, (byte)0x3E, (byte)0x08, (byte)0xCA, (byte)0x0E, (byte)0x72, (byte)0x35, (byte)0x67, (byte)0x23, (byte)0x6F, (byte)0x16, (byte)0xD2, (byte)0x09, (byte)0x7B, (byte)0x1D, (byte)0x4F, (byte)0x3B, (byte)0x49, (byte)0x2A, (byte)0xC7, (byte)0x39, (byte)0x04, (byte)0x09, (byte)0x46, (byte)0x18, (byte)0x4A, (byte)0x2A, (byte)0xCA},
        {(byte)0x3D, (byte)0x4F, (byte)0x14, (byte)0x7E, (byte)0x1C, (byte)0x59, (byte)0x14, (byte)0xE7, (byte)0x6A, (byte)0x08, (byte)0x30, (byte)0x47, (byte)0x28, (byte)0x79, (byte)0x1F, (byte)0xF0, (byte)0x11, (byte)0x45, (byte)0x2D, (byte)0x49, (byte)0x06, (byte)0x5A, (byte)0x07, (byte)0xE7, (byte)0x0D, (byte)0x7B, (byte)0x1B, (byte)0x58, (byte)0x09, (byte)0x3E, (byte)0x08, (byte)0xCA, (byte)0x35, (byte)0x57, (byte)0x1C, (byte)0x55, (byte)0x39, (byte)0x3A, (byte)0x00, (byte)0xF0, (byte)0x2F, (byte)0x56, (byte)0x08, (byte)0x52, (byte)0x0F, (byte)0x5A, (byte)0x7E, (byte)0xE5, (byte)0x6C, (byte)0x72, (byte)0x36, (byte)0x76, (byte)0x24, (byte)0x41, (byte)0x06, (byte)0xDD},
        {(byte)0x3D, (byte)0x4F, (byte)0x14, (byte)0x7E, (byte)0x0F, (byte)0x5C, (byte)0x16, (byte)0xC1, (byte)0x2E, (byte)0x7B, (byte)0x0F, (byte)0x4B, (byte)0x3A, (byte)0x4C, (byte)0x20, (byte)0xA0, (byte)0x1B, (byte)0x52, (byte)0x32, (byte)0x40, (byte)0x18, (byte)0x48, (byte)0x07, (byte)0xC7, (byte)0x0D, (byte)0x7B, (byte)0x1B, (byte)0x58, (byte)0x09, (byte)0x3E, (byte)0x08, (byte)0xCA, (byte)0x08, (byte)0x65, (byte)0x05, (byte)0x18, (byte)0x2A, (byte)0x59, (byte)0x1C, (byte)0xA4, (byte)0x02, (byte)0x5A, (byte)0x25, (byte)0x69, (byte)0x38, (byte)0x62, (byte)0x3D, (byte)0xAA, (byte)0x0B, (byte)0x5D, (byte)0x08, (byte)0x74, (byte)0x08, (byte)0x5E, (byte)0x34, (byte)0xD6},
    };
    private static final java.util.List<String> BUILTIN_GROQ_KEYS = net.verity.config.KeyVault.decodeAll(ENCODED_GROQ_KEYS);

    // ─── Gemini keys (user-provided only) ───────────────────────────────────
    public static final java.util.List<String> BUILTIN_GEMINI_KEYS = java.util.List.of();

    // ─── Cohere keys ───
    private static final byte[][] ENCODED_COHERE_KEYS = new byte[][] {
        {(byte)0x39, (byte)0x53, (byte)0x17, (byte)0x44, (byte)0x19, (byte)0x68, (byte)0x11, (byte)0xE0, (byte)0x0F, (byte)0x4A, (byte)0x08, (byte)0x74, (byte)0x2D, (byte)0x63, (byte)0x07, (byte)0xFC, (byte)0x68, (byte)0x7E, (byte)0x35, (byte)0x6E, (byte)0x29, (byte)0x39, (byte)0x1E, (byte)0xD1, (byte)0x1F, (byte)0x76, (byte)0x11, (byte)0x43, (byte)0x0A, (byte)0x77, (byte)0x03, (byte)0xA7, (byte)0x0F, (byte)0x7D, (byte)0x10, (byte)0x13, (byte)0x1C, (byte)0x5A, (byte)0x76, (byte)0xA4, (byte)0x11, (byte)0x51, (byte)0x3C, (byte)0x51, (byte)0x59, (byte)0x45, (byte)0x22, (byte)0xA3, (byte)0x6C, (byte)0x53, (byte)0x3C, (byte)0x78, (byte)0x27},
        {(byte)0x39, (byte)0x53, (byte)0x17, (byte)0x44, (byte)0x19, (byte)0x68, (byte)0x11, (byte)0xDA, (byte)0x68, (byte)0x45, (byte)0x47, (byte)0x71, (byte)0x0F, (byte)0x77, (byte)0x09, (byte)0xE2, (byte)0x1C, (byte)0x6A, (byte)0x36, (byte)0x68, (byte)0x39, (byte)0x5B, (byte)0x0A, (byte)0xC2, (byte)0x2C, (byte)0x77, (byte)0x2A, (byte)0x58, (byte)0x20, (byte)0x6E, (byte)0x2B, (byte)0xA6, (byte)0x09, (byte)0x54, (byte)0x36, (byte)0x53, (byte)0x01, (byte)0x5C, (byte)0x2C, (byte)0xAA, (byte)0x62, (byte)0x6A, (byte)0x07, (byte)0x6D, (byte)0x3C, (byte)0x40, (byte)0x76, (byte)0xA0, (byte)0x3D, (byte)0x7D, (byte)0x34, (byte)0x12, (byte)0x39},
        {(byte)0x39, (byte)0x53, (byte)0x17, (byte)0x44, (byte)0x19, (byte)0x68, (byte)0x11, (byte)0xD6, (byte)0x38, (byte)0x7B, (byte)0x0D, (byte)0x63, (byte)0x08, (byte)0x42, (byte)0x78, (byte)0xE5, (byte)0x12, (byte)0x4B, (byte)0x2C, (byte)0x6B, (byte)0x1D, (byte)0x7A, (byte)0x26, (byte)0xFB, (byte)0x15, (byte)0x50, (byte)0x29, (byte)0x60, (byte)0x1C, (byte)0x58, (byte)0x78, (byte)0xC3, (byte)0x23, (byte)0x70, (byte)0x27, (byte)0x55, (byte)0x31, (byte)0x49, (byte)0x02, (byte)0xD9, (byte)0x28, (byte)0x52, (byte)0x33, (byte)0x43, (byte)0x09, (byte)0x3F, (byte)0x23, (byte)0xA1, (byte)0x00, (byte)0x54, (byte)0x08, (byte)0x5B, (byte)0x39},
        {(byte)0x39, (byte)0x53, (byte)0x17, (byte)0x44, (byte)0x19, (byte)0x68, (byte)0x11, (byte)0xF9, (byte)0x28, (byte)0x57, (byte)0x4E, (byte)0x75, (byte)0x5B, (byte)0x6F, (byte)0x04, (byte)0xF8, (byte)0x2F, (byte)0x49, (byte)0x11, (byte)0x69, (byte)0x5B, (byte)0x74, (byte)0x21, (byte)0xF9, (byte)0x1D, (byte)0x77, (byte)0x29, (byte)0x17, (byte)0x00, (byte)0x3E, (byte)0x21, (byte)0xFE, (byte)0x00, (byte)0x7B, (byte)0x48, (byte)0x50, (byte)0x31, (byte)0x6A, (byte)0x1D, (byte)0xE9, (byte)0x2B, (byte)0x4C, (byte)0x30, (byte)0x47, (byte)0x03, (byte)0x43, (byte)0x09, (byte)0xA3, (byte)0x31, (byte)0x70, (byte)0x1E, (byte)0x79, (byte)0x58}
    };
    private static final java.util.List<String> BUILTIN_COHERE_KEYS = net.verity.config.KeyVault.decodeAll(ENCODED_COHERE_KEYS);

    /** Источник ключей: "builtin" (от мода) или "custom" (свои) */
    public static String keySource()           { return getString("key_source", "builtin"); }
    public static void setKeySource(String v)  { setProperty("key_source", v); }
    public static boolean useBuiltinKeys()     { return "builtin".equalsIgnoreCase(keySource()); }

    // ─────── ДОСТУПНЫЕ МОДЕЛИ ────────────────────────────────────────────────
    public static final java.util.List<String> AVAILABLE_MODELS = java.util.List.of(
            "openrouter/owl-alpha",
            "openrouter/free",
            "google/gemma-2-9b-it:free",
            "meta-llama/llama-3.3-70b-instruct:free",
            "qwen/qwen-2.5-72b-instruct:free",
            "nvidia/nemotron-4-340b-instruct:free",
            "nousresearch/hermes-3-llama-3.1-405b:free"
    );

    // ─── Encrypted key fields (stored as enc:Base64 in config) ──────────────
    private static final java.util.Set<String> ENCRYPTED_KEYS = java.util.Set.of(
            "gemini_api_key", "custom_api_key", "openrouter_api_key", "openrouter_api_keys",
            "groq_api_key", "cohere_api_key"
    );

    /** Encrypt a plaintext key for storage: returns "enc:Base64" */
    private static String encryptKey(String plain) {
        if (plain == null || plain.isEmpty()) return plain;
        if (plain.startsWith("enc:")) return plain; // already encrypted
        byte[] encoded = net.verity.config.KeyVault.encode(plain);
        return "enc:" + java.util.Base64.getEncoder().encodeToString(encoded);
    }

    /** Decrypt a stored key: accepts "enc:Base64" or plaintext (for backward compat) */
    private static String decryptKey(String stored) {
        if (stored == null || stored.isEmpty()) return stored;
        if (!stored.startsWith("enc:")) return stored; // plaintext, return as-is
        try {
            byte[] encoded = java.util.Base64.getDecoder().decode(stored.substring(4));
            return net.verity.config.KeyVault.decode(encoded);
        } catch (Exception e) {
            return stored; // decode failed, return as-is
        }
    }

    /** Get a key field with automatic decryption */
    private static String getEncryptedString(String key, String defaultValue) {
        if (!loaded) reloadIfChanged();
        String val = props.getProperty(key);
        if (val == null || val.isEmpty()) return defaultValue;
        return decryptKey(val);
    }

    // ─────── LLM (OpenRouter + Gemini) ──────────────────────────────────────
    public static boolean llmEnabled()            { return getBool("llm_enabled", true); }
    public static String openRouterApiKey()       { return getEncryptedString("openrouter_api_key", ""); }

    /** Gemini API key (Google AI Studio) — builtin or custom */
    public static String geminiApiKey()           { return getEncryptedString("gemini_api_key", ""); }
    public static boolean hasGeminiKey()          { return !getEncryptedString("gemini_api_key", "").isBlank(); }

    /** All Gemini keys: builtin + custom */
    public static java.util.List<String> geminiApiKeys() {
        java.util.List<String> result = new java.util.ArrayList<>(BUILTIN_GEMINI_KEYS);
        String custom = geminiApiKey();
        if (!custom.isBlank() && !result.contains(custom.trim())) {
            result.add(custom.trim());
        }
        return result;
    }

    /** Gemini model (default: gemini-2.0-flash) */
    public static String geminiModel()            { return getString("gemini_model", "gemini-2.5-flash"); }

    // ─── Groq Configuration ───
    public static String groqApiKey()             { return getEncryptedString("groq_api_key", ""); }
    public static java.util.List<String> groqApiKeys() {
        if (!useBuiltinKeys()) {
            java.util.List<String> custom = new java.util.ArrayList<>();
            String gk = groqApiKey();
            if (!gk.isBlank()) custom.add(gk.trim());
            return custom;
        }
        java.util.List<String> result = new java.util.ArrayList<>(BUILTIN_GROQ_KEYS);
        String gk = groqApiKey();
        if (!gk.isBlank() && !result.contains(gk.trim())) {
            result.add(gk.trim());
        }
        return result;
    }
    public static String groqModel()              { return getString("groq_model", "llama-3.3-70b-versatile"); }

    // ─── Cohere Configuration ───
    public static String cohereApiKey()           { return getEncryptedString("cohere_api_key", ""); }
    public static java.util.List<String> cohereApiKeys() {
        if (!useBuiltinKeys()) {
            java.util.List<String> custom = new java.util.ArrayList<>();
            String ck = cohereApiKey();
            if (!ck.isBlank()) custom.add(ck.trim());
            return custom;
        }
        java.util.List<String> result = new java.util.ArrayList<>(BUILTIN_COHERE_KEYS);
        String ck = cohereApiKey();
        if (!ck.isBlank() && !result.contains(ck.trim())) {
            result.add(ck.trim());
        }
        return result;
    }
    public static String cohereModel()            { return getString("cohere_model", "command-r-plus"); }

    /** LLM provider: "openrouter", "gemini", "groq", or "cohere" */
    public static String llmProvider()            { return getString("llm_provider", "openrouter"); }

    /** Пользовательский API ключ (из настроек) */
    public static String customApiKey()           { return getEncryptedString("custom_api_key", ""); }

    /** Все API ключи: встроенные + пользовательский */
    public static java.util.List<String> openRouterApiKeys() {
        // Режим "custom" — только свои ключи
        if (!useBuiltinKeys()) {
            java.util.List<String> custom = new java.util.ArrayList<>();
            String ck = customApiKey();
            if (!ck.isBlank()) custom.add(ck.trim());
            String raw = getEncryptedString("openrouter_api_keys", "");
            if (!raw.isBlank()) {
                java.util.Arrays.stream(raw.split(","))
                        .map(String::trim).filter(s -> !s.isBlank() && !custom.contains(s))
                        .forEach(custom::add);
            }
            String single = openRouterApiKey();
            if (!single.isBlank() && !custom.contains(single)) custom.add(single);
            return custom;
        }

        // Режим "builtin" — встроенные ключи (+ пользовательский как бонус)
        java.util.List<String> result = new java.util.ArrayList<>(BUILTIN_API_KEYS);
        String custom = customApiKey();
        if (!custom.isBlank()) {
            result.add(custom.trim());
        }
        // Также поддерживаем legacy поле openrouter_api_keys
        String raw = getEncryptedString("openrouter_api_keys", "");
        if (!raw.isBlank()) {
            java.util.Arrays.stream(raw.split(","))
                    .map(String::trim).filter(s -> !s.isBlank() && !result.contains(s))
                    .forEach(result::add);
        }
        // Legacy single key
        String single = openRouterApiKey();
        if (!single.isBlank() && !result.contains(single)) {
            result.add(single);
        }
        return result;
    }

    /** Выбранная модель (из настроек или дефолтная — Owl Alpha) */
    public static String selectedModel() {
        return getString("selected_model", AVAILABLE_MODELS.get(0));
    }

    /** Установить выбранную модель */
    public static void setSelectedModel(String model) {
        setProperty("selected_model", model);
    }

    /** Comma-separated list of models for fallback */
    public static java.util.List<String> openRouterModels() {
        String raw = getString("openrouter_models", "");
        if (raw.isBlank()) {
            return java.util.List.of(selectedModel());
        }
        return java.util.Arrays.stream(raw.split(","))
                .map(String::trim).filter(s -> !s.isBlank())
                .toList();
    }
    public static float llmTemperature()          { return getFloat("llm_temperature", 0.8f); }
    public static int llmMaxTokens()              { return getInt("llm_max_tokens", 256); }

    // ─────── ПОВЕДЕНИЕ ФАЗ ──────────────────────────────────────────────────
    /** Через сколько тиков HELPER → OMNISCIENT (по умолч. 2400 = 2 мин) */
    public static int helperToOmniscientTicks()   { return getInt("helper_to_omniscient_ticks", 2400); }
    /** Через сколько тиков OMNISCIENT → COUNTDOWN (по умолч. 3600 = 3 мин) */
    public static int omniscientToCountdownTicks(){ return getInt("omniscient_to_countdown_ticks", 3600); }
    /** Длительность 1 "дня" в COUNTDOWN в тиках (по умолч. 1200 = 1 мин) */
    public static int countdownTicksPerDay()      { return getInt("countdown_ticks_per_day", 1200); }
    /** Сколько "дней" до трансформации в MONSTER (по умолч. 3) */
    public static int countdownDays()             { return getInt("countdown_days", 3); }

    // ─────── СКОРОСТЬ ПЕРЕДВИЖЕНИЯ ──────────────────────────────────────────
    public static double speedHelper()            { return getDouble("speed_helper", 0.25); }
    public static double speedCountdown()         { return getDouble("speed_countdown", 0.8); }
    public static double speedMonster()           { return getDouble("speed_monster", 0.35); }
    public static double speedHunter()            { return getDouble("speed_hunter", 0.4); }

    // ─────── ЗВУКИ ──────────────────────────────────────────────────────────
    public static boolean soundsEnabled()         { return getBool("sounds_enabled", true); }
    public static float soundVolume()             { return getFloat("sound_volume", 1.0f); }

    // ─────── МЕХАНИКИ ───────────────────────────────────────────────────────
    public static boolean villagerEatingEnabled() { return getBool("villager_eating_enabled", true); }
    public static boolean teleportEnabled()       { return getBool("teleport_enabled", true); }
    public static boolean doorTelekinesisEnabled(){ return getBool("door_telekinesis_enabled", true); }
    public static boolean chatEnabled()           { return getBool("chat_enabled", true); }
    /** Отвечать на ВСЕ сообщения, без необходимости упоминать Verity */
    public static boolean alwaysRespond()         { return getBool("always_respond", false); }
    public static boolean monsterFormEnabled()    { return getBool("monster_form_enabled", true); }

    /** Версия конфига (для миграций) */
    public static int configVersion()             { return getInt("config_version", 6); }

    // ─────── ЗАГРУЗКА ───────────────────────────────────────────────────────

    /**
     * Загружает/перезагружает конфиг, если файл изменился.
     * Безопасно вызывать каждый тик — проверяет lastModified.
     */
    public static void reloadIfChanged() {
        try {
            if (!Files.exists(CONFIG_PATH)) {
                createDefaultConfig();
                return;
            }
            long modified = Files.getLastModifiedTime(CONFIG_PATH).toMillis();
            if (modified == lastModifiedMs && loaded) return;

            try (var reader = Files.newBufferedReader(CONFIG_PATH)) {
                props.load(reader);
            }
            lastModifiedMs = modified;
            loaded = true;

            // ── Миграция: config_version < 5 → меняем owl-alpha (умер) на llama-3.3-70b
            int cv = configVersion();
            if (cv < 5) {
                String currentModel = props.getProperty("selected_model", "");
                if (currentModel.equals("openrouter/owl-alpha") ||
                        currentModel.equals("google/gemini-2.0-flash-exp:free") ||
                        currentModel.isEmpty()) {
                    props.setProperty("selected_model", "meta-llama/llama-3.3-70b-instruct:free");
                    VerityMod.LOGGER.info("Verity config: migrated selected_model \u2192 meta-llama/llama-3.3-70b-instruct:free");
                }
                // Заменяем owl-alpha в fallback-списке
                String models = props.getProperty("openrouter_models", "");
                if (models.contains("openrouter/owl-alpha")) {
                    models = models.replace("openrouter/owl-alpha", "meta-llama/llama-3.3-70b-instruct:free");
                    // Убираем дубликаты
                    String[] parts = models.split(",");
                    java.util.LinkedHashSet<String> seen = new java.util.LinkedHashSet<>();
                    for (String p : parts) {
                        String trimmed = p.trim();
                        if (!trimmed.isEmpty()) seen.add(trimmed);
                    }
                    models = String.join(",", seen);
                    props.setProperty("openrouter_models", models);
                    VerityMod.LOGGER.info("Verity config: migrated openrouter_models (removed owl-alpha)");
                }
                // Обновляем fallback-список на актуальный
                String oldModels = props.getProperty("openrouter_models", "");
                if (oldModels.contains("openrouter/owl-alpha") || oldModels.contains("meta-llama/llama-3.2-3b-instruct:free")) {
                    props.setProperty("openrouter_models",
                            "meta-llama/llama-3.3-70b-instruct:free,nvidia/nemotron-3-super-120b-a12b:free,qwen/qwen3-next-80b-a3b-instruct:free,google/gemma-4-26b-a4b-it:free");
                    VerityMod.LOGGER.info("Verity config: updated openrouter_models fallback list");
                }
                // Увеличиваем max_tokens если было 150
                String maxTokens = props.getProperty("llm_max_tokens", "256");
                if ("150".equals(maxTokens)) {
                    props.setProperty("llm_max_tokens", "256");
                    VerityMod.LOGGER.info("Verity config: increased llm_max_tokens 150 \u2192 256");
                }
                props.setProperty("config_version", "5");
                saveConfig();
            }

            // ── Миграция: config_version < 6 → добавляем Gemini настройки ──
            int cv6 = configVersion();
            if (cv6 < 6) {
                // Только если поле вообще отсутствует — ставим gemini по умолчанию
                if (!props.containsKey("llm_provider") || props.getProperty("llm_provider", "").isEmpty()) {
                    props.setProperty("llm_provider", "openrouter");
                    VerityMod.LOGGER.info("Verity config: set default llm_provider \u2192 openrouter");
                }
                if (!props.containsKey("gemini_model") || props.getProperty("gemini_model", "").isEmpty()
                        || props.getProperty("gemini_model", "").equals("gemini-2.0-flash")) {
                    props.setProperty("gemini_model", "gemini-2.5-flash");
                }
                props.setProperty("config_version", "6");
                saveConfig();
            }

            if (VerityMod.LOGGER != null) {
                VerityMod.LOGGER.info("Verity config reloaded ({} properties)", props.size());
            }
        } catch (Exception e) {
            if (VerityMod.LOGGER != null) {
                VerityMod.LOGGER.warn("Failed to load config: {}", e.getMessage());
            }
        }
    }

    /**
     * Принудительная загрузка (вызывается при старте мода).
     */
    public static void load() {
        reloadIfChanged();
    }

    /**
     * Принудительная перезагрузка, игнорируя lastModified.
     * Используется после сохранения настроек из экрана настроек.
     */
    public static void forceReload() {
        lastModifiedMs = 0;
        loaded = false;
        reloadIfChanged();
    }

    /**
     * Создаёт дефолтный конфиг с полной документацией.
     */
    private static void createDefaultConfig() throws IOException {
        String defaultConfig = """
                # \u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550
                # Verity \u2014 \u043A\u043E\u043D\u0444\u0438\u0433\u0443\u0440\u0430\u0446\u0438\u044F \u0441\u0435\u0440\u0432\u0435\u0440\u0430
                # \u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550
                # Config version (do not change)
                config_version=6
                
                # \u2500\u2500\u2500 LLM (OpenRouter + Gemini) \u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500
                # \u041F\u0440\u043E\u0432\u0430\u0439\u0434\u0435\u0440: openrouter \u0438\u043B\u0438 gemini
                llm_provider=openrouter
                
                # \u0418\u0441\u0442\u043E\u0447\u043D\u0438\u043A \u043A\u043B\u044E\u0447\u0435\u0439: builtin (\u043E\u0442 \u043C\u043E\u0434\u0430) \u0438\u043B\u0438 custom (\u0441\u0432\u043E\u0438)
                key_source=builtin
                
                # \u0412\u043A\u043B\u044E\u0447\u0438\u0442\u044C \u0433\u0435\u043D\u0435\u0440\u0430\u0446\u0438\u044E \u043E\u0442\u0432\u0435\u0442\u043E\u0432 \u0447\u0435\u0440\u0435\u0437 LLM?
                # false = \u0442\u043E\u043B\u044C\u043A\u043E \u043A\u0435\u0448\u0438\u0440\u043E\u0432\u0430\u043D\u043D\u044B\u0435/\u0445\u0430\u0440\u0434\u043A\u043E\u0434\u043D\u044B\u0435 \u043E\u0442\u0432\u0435\u0442\u044B
                llm_enabled=true
                
                # \u2500\u2500\u2500 Gemini (Google AI Studio) \u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500
                # \u041F\u043E\u043B\u0443\u0447\u0438\u0442\u044C \u0431\u0435\u0441\u043F\u043B\u0430\u0442\u043D\u044B\u0439 \u043A\u043B\u044E\u0447: https://aistudio.google.com/apikey
                gemini_api_key=
                # \u041C\u043E\u0434\u0435\u043B\u0438: gemini-2.0-flash, gemini-2.0-flash-lite, gemini-1.5-pro, gemini-1.5-flash
                gemini_model=gemini-2.5-flash
                
                # \u2500\u2500\u2500 OpenRouter \u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500
                # API \u043A\u043B\u044E\u0447\u0438 \u0447\u0435\u0440\u0435\u0437 \u0437\u0430\u043F\u044F\u0442\u0443\u044E (\u043F\u0440\u0438 429 \u0438\u0441\u043F\u043E\u043B\u044C\u0437\u0443\u0435\u0442\u0441\u044F \u0441\u043B\u0435\u0434\u0443\u044E\u0449\u0438\u0439)
                # \u041C\u043E\u0434 \u0443\u0436\u0435 \u0432\u043A\u043B\u044E\u0447\u0430\u0435\u0442 2 \u0432\u0441\u0442\u0440\u043E\u0435\u043D\u043D\u044B\u0445 \u043A\u043B\u044E\u0447\u0430 \u2014 \u043F\u043E\u043B\u0435 \u043C\u043E\u0436\u043D\u043E \u043E\u0441\u0442\u0430\u0432\u0438\u0442\u044C \u043F\u0443\u0441\u0442\u044B\u043C.
                # \u041F\u043E\u043B\u0443\u0447\u0438\u0442\u044C \u0441\u0432\u043E\u0439 \u0431\u0435\u0441\u043F\u043B\u0430\u0442\u043D\u044B\u0439 \u043A\u043B\u044E\u0447: https://openrouter.ai/keys
                openrouter_api_keys=
                
                # \u0421\u0432\u043E\u0439 API \u043A\u043B\u044E\u0447 (\u0434\u043E\u0431\u0430\u0432\u043B\u044F\u0435\u0442\u0441\u044F \u043A \u0432\u0441\u0442\u0440\u043E\u0435\u043D\u043D\u044B\u043C)
                custom_api_key=
                
                # \u0412\u044B\u0431\u0440\u0430\u043D\u043D\u0430\u044F \u043C\u043E\u0434\u0435\u043B\u044C (\u0438\u0437 \u0441\u043F\u0438\u0441\u043A\u0430 \u0434\u043E\u0441\u0442\u0443\u043F\u043D\u044B\u0445 \u0432 \u043D\u0430\u0441\u0442\u0440\u043E\u0439\u043A\u0430\u0445)
                selected_model=meta-llama/llama-3.3-70b-instruct:free
                
                # \u041C\u043E\u0434\u0435\u043B\u0438 \u0447\u0435\u0440\u0435\u0437 \u0437\u0430\u043F\u044F\u0442\u0443\u044E (fallback \u043F\u0440\u0438 429)
                openrouter_models=meta-llama/llama-3.3-70b-instruct:free,nvidia/nemotron-3-super-120b-a12b:free,qwen/qwen3-next-80b-a3b-instruct:free,google/gemma-4-26b-a4b-it:free
                
                # \u0422\u0435\u043C\u043F\u0435\u0440\u0430\u0442\u0443\u0440\u0430 LLM (0.0 = \u0441\u0442\u0440\u043E\u0433\u0438\u0439, 1.0 = \u043A\u0440\u0435\u0430\u0442\u0438\u0432\u043D\u044B\u0439)
                llm_temperature=0.8
                
                # \u041C\u0430\u043A\u0441\u0438\u043C\u0443\u043C \u0442\u043E\u043A\u0435\u043D\u043E\u0432 \u0432 \u043E\u0442\u0432\u0435\u0442\u0435 (1 \u0442\u043E\u043A\u0435\u043D \u2248 0.75 \u0441\u043B\u043E\u0432\u0430)
                llm_max_tokens=256
                
                # \u2500\u2500\u2500 \u0424\u0410\u0417\u042B \u041F\u041E\u0412\u0415\u0414\u0415\u041D\u0418\u042F \u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500
                # \u0412\u0440\u0435\u043C\u044F \u0434\u043E \u043F\u0435\u0440\u0435\u0445\u043E\u0434\u0430 HELPER \u2192 OMNISCIENT (\u0432 \u0442\u0438\u043A\u0430\u0445, 20 \u0442\u0438\u043A\u043E\u0432 = 1 \u0441\u0435\u043A)
                # 2400 \u0442\u0438\u043A\u043E\u0432 = 2 \u043C\u0438\u043D\u0443\u0442\u044B
                helper_to_omniscient_ticks=2400
                
                # \u0412\u0440\u0435\u043C\u044F \u0434\u043E \u043F\u0435\u0440\u0435\u0445\u043E\u0434\u0430 OMNISCIENT \u2192 COUNTDOWN (\u0432 \u0442\u0438\u043A\u0430\u0445)
                # 3600 \u0442\u0438\u043A\u043E\u0432 = 3 \u043C\u0438\u043D\u0443\u0442\u044B
                omniscient_to_countdown_ticks=3600
                
                # \u0414\u043B\u0438\u0442\u0435\u043B\u044C\u043D\u043E\u0441\u0442\u044C 1 "\u0434\u043D\u044F" \u0432 COUNTDOWN (\u0432 \u0442\u0438\u043A\u0430\u0445)
                # 1200 \u0442\u0438\u043A\u043E\u0432 = 1 \u043C\u0438\u043D\u0443\u0442\u0430 (\u0434\u043B\u044F \u0442\u0435\u0441\u0442\u0430)
                # 24000 \u0442\u0438\u043A\u043E\u0432 = 1 \u0438\u0433\u0440\u043E\u0432\u043E\u0439 \u0434\u0435\u043D\u044C (\u0434\u043B\u044F \u0440\u0435\u0430\u043B\u0438\u0437\u043C\u0430)
                countdown_ticks_per_day=1200
                
                # \u0421\u043A\u043E\u043B\u044C\u043A\u043E "\u0434\u043D\u0435\u0439" \u0434\u043E \u0442\u0440\u0430\u043D\u0441\u0444\u043E\u0440\u043C\u0430\u0446\u0438\u0438 \u0432 MONSTER
                countdown_days=3
                
                # \u2500\u2500\u2500 \u0421\u041A\u041E\u0420\u041E\u0421\u0422\u042C \u041F\u0415\u0420\u0415\u0414\u0412\u0418\u0416\u0415\u041D\u0418\u042F \u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500
                # \u041E\u0431\u044B\u0447\u043D\u0430\u044F \u0441\u043A\u043E\u0440\u043E\u0441\u0442\u044C (HELPER, OMNISCIENT, POSSESSIVE)
                speed_helper=0.25
                # \u0421\u043A\u043E\u0440\u043E\u0441\u0442\u044C \u0432 COUNTDOWN (\u0441\u0442\u0430\u043B\u043A\u0435\u0440)
                speed_countdown=0.8
                # \u0421\u043A\u043E\u0440\u043E\u0441\u0442\u044C Monster Form
                speed_monster=0.35
                # \u0421\u043A\u043E\u0440\u043E\u0441\u0442\u044C HUNTER (\u043E\u0445\u043E\u0442\u0430 \u043D\u0430 \u0434\u0440\u0443\u0433\u0438\u0445 \u0438\u0433\u0440\u043E\u043A\u043E\u0432)
                speed_hunter=0.4
                
                # \u2500\u2500\u2500 \u0417\u0412\u0423\u041A\u0418 \u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500
                # \u0412\u043A\u043B\u044E\u0447\u0438\u0442\u044C \u0437\u0432\u0443\u043A\u0438 Verity?
                sounds_enabled=true
                # \u0413\u0440\u043E\u043C\u043A\u043E\u0441\u0442\u044C \u0437\u0432\u0443\u043A\u043E\u0432 (0.0 = \u0442\u0438\u0445\u043E, 1.0 = \u043D\u043E\u0440\u043C\u0430, 2.0 = \u0433\u0440\u043E\u043C\u043A\u043E)
                sound_volume=1.0
                
                # \u2500\u2500\u2500 \u041C\u0415\u0425\u0410\u041D\u0418\u041A\u0418 \u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500
                # Verity \u043F\u043E\u0435\u0434\u0430\u0435\u0442 \u0436\u0438\u0442\u0435\u043B\u0435\u0439 \u0434\u0435\u0440\u0435\u0432\u0435\u043D\u044C? (\u043A\u0430\u043D\u043E\u043D)
                villager_eating_enabled=true
                # Verity \u0442\u0435\u043B\u0435\u043F\u043E\u0440\u0442\u0438\u0440\u0443\u0435\u0442\u0441\u044F \u0437\u0430 \u0441\u043F\u0438\u043D\u0443?
                teleport_enabled=true
                # Verity \u043E\u0442\u043A\u0440\u044B\u0432\u0430\u0435\u0442 \u0434\u0432\u0435\u0440\u0438 \u0441\u0438\u043B\u043E\u0439 \u043C\u044B\u0441\u043B\u0438 (\u0432 COUNTDOWN)?
                door_telekinesis_enabled=true
                # Verity \u043E\u0442\u0432\u0435\u0447\u0430\u0435\u0442 \u043D\u0430 \u0441\u043E\u043E\u0431\u0449\u0435\u043D\u0438\u044F \u0432 \u0447\u0430\u0442\u0435?
                chat_enabled=true
                # Verity \u043E\u0442\u0432\u0435\u0447\u0430\u0435\u0442 \u043D\u0430 \u0412\u0421\u0415 \u0441\u043E\u043E\u0431\u0449\u0435\u043D\u0438\u044F (\u0431\u0435\u0437 \u0443\u043F\u043E\u043C\u0438\u043D\u0430\u043D\u0438\u044F "\u0432\u0435\u0440\u0438\u0442\u0438/\u0448\u0430\u0440")?
                always_respond=false
                # Verity \u043F\u0435\u0440\u0435\u043A\u043B\u044E\u0447\u0430\u0435\u0442\u0441\u044F \u0432 Monster Form?
                monster_form_enabled=true
                """;
        Files.createDirectories(CONFIG_PATH.getParent());
        Files.writeString(CONFIG_PATH, defaultConfig);
    }

    // ─────── ЗАПИСЬ ────────────────────────────────────────────────────────

    /**
     * Записывает одно свойство в конфиг и сохраняет файл.
     */
    public static void setProperty(String key, String value) {
        if (!loaded) reloadIfChanged();
        props.setProperty(key, value);
        saveConfig();
    }

    public static void setBool(String key, boolean value) {
        setProperty(key, String.valueOf(value));
    }

    /**
     * Сохраняет текущие props в файл.
     */
    private static void saveConfig() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            // Encrypt key fields before saving
            Properties saveProps = new Properties();
            saveProps.putAll(props);
            for (String key : ENCRYPTED_KEYS) {
                String val = saveProps.getProperty(key);
                if (val != null && !val.isEmpty() && !val.startsWith("enc:")) {
                    saveProps.setProperty(key, encryptKey(val));
                }
            }
            try (var writer = Files.newBufferedWriter(CONFIG_PATH)) {
                saveProps.store(writer, "Verity Mod Config");
            }
            lastModifiedMs = Files.getLastModifiedTime(CONFIG_PATH).toMillis();
            loaded = true;
        } catch (Exception e) {
            if (VerityMod.LOGGER != null) {
                VerityMod.LOGGER.warn("Failed to save config: {}", e.getMessage());
            }
        }
    }

    // ─────── HELPER-МЕТОДЫ ──────────────────────────────────────────────────

    private static String getString(String key, String defaultValue) {
        if (!loaded) reloadIfChanged();
        return props.getProperty(key, defaultValue);
    }

    private static boolean getBool(String key, boolean defaultValue) {
        if (!loaded) reloadIfChanged();
        String val = props.getProperty(key);
        if (val == null) return defaultValue;
        return val.equalsIgnoreCase("true") || val.equals("1") || val.equalsIgnoreCase("yes");
    }

    private static int getInt(String key, int defaultValue) {
        if (!loaded) reloadIfChanged();
        String val = props.getProperty(key);
        if (val == null) return defaultValue;
        try {
            return Integer.parseInt(val.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private static float getFloat(String key, float defaultValue) {
        if (!loaded) reloadIfChanged();
        String val = props.getProperty(key);
        if (val == null) return defaultValue;
        try {
            return Float.parseFloat(val.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private static double getDouble(String key, double defaultValue) {
        if (!loaded) reloadIfChanged();
        String val = props.getProperty(key);
        if (val == null) return defaultValue;
        try {
            return Double.parseDouble(val.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}