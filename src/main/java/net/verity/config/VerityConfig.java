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

    // ─── Builtin Gemini keys (3 keys for rotation) ───────────────────────────
    public static final java.util.List<String> BUILTIN_GEMINI_KEYS = java.util.List.of(
            "AIzaSyCJxeV31u-q5AhlVaQ-URD4fspdsiV5UUw",
            "AIzaSyAovl0jPkpSp0TF-x8yRqO2OkCOunKeM88",
            "AIzaSyCQLBEpyq3sXPhhID5jnY9_HPBpWqraeFs"
    );

    /** Источник ключей: "builtin" (от мода) или "custom" (свои) */
    public static String keySource()           { return getString("key_source", "builtin"); }
    public static void setKeySource(String v)  { setProperty("key_source", v); }
    public static boolean useBuiltinKeys()     { return "builtin".equalsIgnoreCase(keySource()); }

    // ─────── ДОСТУПНЫЕ МОДЕЛИ ────────────────────────────────────────────────
    public static final java.util.List<String> AVAILABLE_MODELS = java.util.List.of(
            "meta-llama/llama-3.3-70b-instruct:free",
            "nvidia/nemotron-3-super-120b-a12b:free",
            "qwen/qwen3-next-80b-a3b-instruct:free",
            "qwen/qwen3-coder:free",
            "openai/gpt-oss-120b:free",
            "nousresearch/hermes-3-llama-3-3.1-405b:free",
            "google/gemma-4-26b-a4b-it:free"
    );

    // ─────── LLM (OpenRouter + Gemini) ──────────────────────────────────────
    public static boolean llmEnabled()            { return getBool("llm_enabled", true); }
    public static String openRouterApiKey()       { return getString("openrouter_api_key", ""); }

    /** Gemini API key (Google AI Studio) — builtin or custom */
    public static String geminiApiKey()           { return getString("gemini_api_key", ""); }
    public static boolean hasGeminiKey()          { return true; } // always have builtin keys

    /** All Gemini keys: builtin + custom */
    public static java.util.List<String> geminiApiKeys() {
        java.util.List<String> result = new java.util.ArrayList<>(BUILTIN_GEMINI_KEYS);
        String custom = getString("gemini_api_key", "");
        if (!custom.isBlank() && !result.contains(custom.trim())) {
            result.add(custom.trim());
        }
        return result;
    }

    /** Gemini model (default: gemini-2.0-flash) */
    public static String geminiModel()            { return getString("gemini_model", "gemini-2.5-flash"); }

    /** LLM provider: "openrouter" or "gemini" */
    public static String llmProvider()            { return getString("llm_provider", "gemini"); }

    /** Пользовательский API ключ (из настроек) */
    public static String customApiKey()           { return getString("custom_api_key", ""); }

    /** Все API ключи: встроенные + пользовательский */
    public static java.util.List<String> openRouterApiKeys() {
        // Режим "custom" — только свои ключи
        if (!useBuiltinKeys()) {
            java.util.List<String> custom = new java.util.ArrayList<>();
            String ck = customApiKey();
            if (!ck.isBlank()) custom.add(ck.trim());
            String raw = getString("openrouter_api_keys", "");
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
        String raw = getString("openrouter_api_keys", "");
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
                    VerityMod.LOGGER.info("Verity config: migrated selected_model → meta-llama/llama-3.3-70b-instruct:free");
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
                    VerityMod.LOGGER.info("Verity config: increased llm_max_tokens 150 → 256");
                }
                props.setProperty("config_version", "5");
                saveConfig();
            }

            // ── Миграция: config_version < 6 → переключаем на Gemini (встроенные ключи, без 429) ──
            int cv6 = configVersion();
            if (cv6 < 6) {
                String provider = props.getProperty("llm_provider", "");
                if (provider.isEmpty() || provider.equals("openrouter")) {
                    props.setProperty("llm_provider", "gemini");
                    VerityMod.LOGGER.info("Verity config: migrated llm_provider → gemini (builtin keys)");
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
                # ═══════════════════════════════════════════════════════════════
                # Verity — конфигурация сервера
                # ═══════════════════════════════════════════════════════════════
                # Config version (do not change)
                config_version=6
                
                # ─── LLM (OpenRouter + Gemini) ───────────────────────────────
                # Провайдер: openrouter или gemini
                llm_provider=gemini
                
                # Источник ключей: builtin (от мода) или custom (свои)
                key_source=builtin
                
                # Включить генерацию ответов через LLM?
                # false = только кешированные/хардкодные ответы
                llm_enabled=true
                
                # ─── Gemini (Google AI Studio) ───────────────────────────────
                # Получить бесплатный ключ: https://aistudio.google.com/apikey
                gemini_api_key=
                # Модели: gemini-2.0-flash, gemini-2.0-flash-lite, gemini-1.5-pro, gemini-1.5-flash
                gemini_model=gemini-2.5-flash
                
                # ─── OpenRouter ──────────────────────────────────────────────
                # API ключи через запятую (при 429 используется следующий)
                # Мод уже включает 2 встроенных ключа — поле можно оставить пустым.
                # Получить свой бесплатный ключ: https://openrouter.ai/keys
                openrouter_api_keys=
                
                # Свой API ключ (добавляется к встроенным)
                custom_api_key=
                
                # Выбранная модель (из списка доступных в настройках)
                selected_model=meta-llama/llama-3.3-70b-instruct:free
                
                # Модели через запятую (fallback при 429)
                openrouter_models=meta-llama/llama-3.3-70b-instruct:free,nvidia/nemotron-3-super-120b-a12b:free,qwen/qwen3-next-80b-a3b-instruct:free,google/gemma-4-26b-a4b-it:free
                
                # Температура LLM (0.0 = строгий, 1.0 = креативный)
                llm_temperature=0.8
                
                # Максимум токенов в ответе (1 токен ≈ 0.75 слова)
                llm_max_tokens=256
                
                # ─── ФАЗЫ ПОВЕДЕНИЯ ──────────────────────────────────────────
                # Время до перехода HELPER → OMNISCIENT (в тиках, 20 тиков = 1 сек)
                # 2400 тиков = 2 минуты
                helper_to_omniscient_ticks=2400
                
                # Время до перехода OMNISCIENT → COUNTDOWN (в тиках)
                # 3600 тиков = 3 минуты
                omniscient_to_countdown_ticks=3600
                
                # Длительность 1 "дня" в COUNTDOWN (в тиках)
                # 1200 тиков = 1 минута (для теста)
                # 24000 тиков = 1 игровой день (для реализма)
                countdown_ticks_per_day=1200
                
                # Сколько "дней" до трансформации в MONSTER
                countdown_days=3
                
                # ─── СКОРОСТЬ ПЕРЕДВИЖЕНИЯ ──────────────────────────────────
                # Обычная скорость (HELPER, OMNISCIENT, POSSESSIVE)
                speed_helper=0.25
                # Скорость в COUNTDOWN (сталкер)
                speed_countdown=0.8
                # Скорость Monster Form
                speed_monster=0.35
                # Скорость HUNTER (охота на других игроков)
                speed_hunter=0.4
                
                # ─── ЗВУКИ ──────────────────────────────────────────────────
                # Включить звуки Verity?
                sounds_enabled=true
                # Громкость звуков (0.0 = тихо, 1.0 = норма, 2.0 = громко)
                sound_volume=1.0
                
                # ─── МЕХАНИКИ ────────────────────────────────────────────────
                # Verity поедает жителей деревень? (канон)
                villager_eating_enabled=true
                # Verity телепортируется за спину?
                teleport_enabled=true
                # Verity открывает двери силой мысли (в COUNTDOWN)?
                door_telekinesis_enabled=true
                # Verity отвечает на сообщения в чате?
                chat_enabled=true
                # Verity отвечает на ВСЕ сообщения (без упоминания "верити/шар")?
                always_respond=false
                # Verity переключается в Monster Form?
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
            try (var writer = Files.newBufferedWriter(CONFIG_PATH)) {
                props.store(writer, "Verity Mod Config");
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