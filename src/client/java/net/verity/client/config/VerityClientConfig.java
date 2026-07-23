package net.verity.client.config;

import net.verity.VerityMod;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

public final class VerityClientConfig {

    private static final Path CONFIG_PATH = getConfigDir().resolve("verity-client.properties");

    private static Path getConfigDir() {
        try {
            return net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir();
        } catch (Exception e) {
            return Path.of("config");
        }
    }
    private static Properties props = new Properties();
    private static boolean loaded = false;

    private static final byte[][] ENCODED_STT_KEYS = new byte[][] {
        {(byte)0x3D, (byte)0x4F, (byte)0x14, (byte)0x7E, (byte)0x21, (byte)0x68, (byte)0x04, (byte)0xE0, (byte)0x1B, (byte)0x4F, (byte)0x25, (byte)0x60, (byte)0x1D, (byte)0x79, (byte)0x7D, (byte)0xA7, (byte)0x31, (byte)0x53, (byte)0x37, (byte)0x6F, (byte)0x58, (byte)0x77, (byte)0x3C, (byte)0xE7, (byte)0x0D, (byte)0x7B, (byte)0x1B, (byte)0x58, (byte)0x09, (byte)0x3E, (byte)0x08, (byte)0xCA, (byte)0x18, (byte)0x7B, (byte)0x4B, (byte)0x40, (byte)0x3F, (byte)0x6E, (byte)0x07, (byte)0xFB, (byte)0x0C, (byte)0x6D, (byte)0x28, (byte)0x17, (byte)0x3F, (byte)0x4F, (byte)0x7C, (byte)0xC3, (byte)0x68, (byte)0x75, (byte)0x37, (byte)0x75, (byte)0x25, (byte)0x42, (byte)0x2D, (byte)0xFA},
        {(byte)0x3D, (byte)0x4F, (byte)0x14, (byte)0x7E, (byte)0x06, (byte)0x38, (byte)0x1B, (byte)0xD0, (byte)0x35, (byte)0x59, (byte)0x1B, (byte)0x4F, (byte)0x25, (byte)0x47, (byte)0x02, (byte)0xCB, (byte)0x32, (byte)0x6E, (byte)0x16, (byte)0x6E, (byte)0x39, (byte)0x57, (byte)0x08, (byte)0xD7, (byte)0x0D, (byte)0x7B, (byte)0x1B, (byte)0x58, (byte)0x09, (byte)0x3E, (byte)0x08, (byte)0xCA, (byte)0x6D, (byte)0x4C, (byte)0x08, (byte)0x78, (byte)0x59, (byte)0x5B, (byte)0x05, (byte)0xF4, (byte)0x0A, (byte)0x58, (byte)0x12, (byte)0x10, (byte)0x2A, (byte)0x44, (byte)0x0B, (byte)0xFE, (byte)0x36, (byte)0x69, (byte)0x49, (byte)0x49, (byte)0x0E, (byte)0x77, (byte)0x2B, (byte)0xD2},
        {(byte)0x3D, (byte)0x4F, (byte)0x14, (byte)0x7E, (byte)0x12, (byte)0x5D, (byte)0x05, (byte)0xD4, (byte)0x3D, (byte)0x6D, (byte)0x0A, (byte)0x46, (byte)0x13, (byte)0x63, (byte)0x2C, (byte)0xE5, (byte)0x19, (byte)0x5D, (byte)0x2A, (byte)0x40, (byte)0x38, (byte)0x46, (byte)0x1E, (byte)0xE4, (byte)0x0D, (byte)0x7B, (byte)0x1B, (byte)0x58, (byte)0x09, (byte)0x3E, (byte)0x08, (byte)0xCA, (byte)0x1B, (byte)0x7F, (byte)0x2B, (byte)0x77, (byte)0x05, (byte)0x57, (byte)0x2F, (byte)0xD7, (byte)0x6F, (byte)0x59, (byte)0x18, (byte)0x74, (byte)0x5D, (byte)0x67, (byte)0x27, (byte)0xDE, (byte)0x19, (byte)0x4B, (byte)0x26, (byte)0x10, (byte)0x1D, (byte)0x58, (byte)0x05, (byte)0xC3},
        {(byte)0x3D, (byte)0x4F, (byte)0x14, (byte)0x7E, (byte)0x11, (byte)0x5D, (byte)0x34, (byte)0xF8, (byte)0x20, (byte)0x78, (byte)0x31, (byte)0x50, (byte)0x39, (byte)0x6C, (byte)0x3F, (byte)0xF0, (byte)0x63, (byte)0x69, (byte)0x1B, (byte)0x73, (byte)0x5C, (byte)0x6B, (byte)0x14, (byte)0xF0, (byte)0x0D, (byte)0x7B, (byte)0x1B, (byte)0x58, (byte)0x09, (byte)0x3E, (byte)0x08, (byte)0xCA, (byte)0x0E, (byte)0x72, (byte)0x35, (byte)0x67, (byte)0x23, (byte)0x6F, (byte)0x16, (byte)0xD2, (byte)0x09, (byte)0x7B, (byte)0x1D, (byte)0x4F, (byte)0x3B, (byte)0x49, (byte)0x2A, (byte)0xC7, (byte)0x39, (byte)0x04, (byte)0x09, (byte)0x46, (byte)0x18, (byte)0x4A, (byte)0x2A, (byte)0xCA},
        {(byte)0x3D, (byte)0x4F, (byte)0x14, (byte)0x7E, (byte)0x1C, (byte)0x59, (byte)0x14, (byte)0xE7, (byte)0x6A, (byte)0x08, (byte)0x30, (byte)0x47, (byte)0x28, (byte)0x79, (byte)0x1F, (byte)0xF0, (byte)0x11, (byte)0x45, (byte)0x2D, (byte)0x49, (byte)0x06, (byte)0x5A, (byte)0x07, (byte)0xE7, (byte)0x0D, (byte)0x7B, (byte)0x1B, (byte)0x58, (byte)0x09, (byte)0x3E, (byte)0x08, (byte)0xCA, (byte)0x35, (byte)0x57, (byte)0x1C, (byte)0x55, (byte)0x39, (byte)0x3A, (byte)0x00, (byte)0xF0, (byte)0x2F, (byte)0x56, (byte)0x08, (byte)0x52, (byte)0x0F, (byte)0x5A, (byte)0x7E, (byte)0xE5, (byte)0x6C, (byte)0x72, (byte)0x36, (byte)0x76, (byte)0x24, (byte)0x41, (byte)0x06, (byte)0xDD},
        {(byte)0x3D, (byte)0x4F, (byte)0x14, (byte)0x7E, (byte)0x0F, (byte)0x5C, (byte)0x16, (byte)0xC1, (byte)0x2E, (byte)0x7B, (byte)0x0F, (byte)0x4B, (byte)0x3A, (byte)0x4C, (byte)0x20, (byte)0xA0, (byte)0x1B, (byte)0x52, (byte)0x32, (byte)0x40, (byte)0x18, (byte)0x48, (byte)0x07, (byte)0xC7, (byte)0x0D, (byte)0x7B, (byte)0x1B, (byte)0x58, (byte)0x09, (byte)0x3E, (byte)0x08, (byte)0xCA, (byte)0x08, (byte)0x65, (byte)0x05, (byte)0x18, (byte)0x2A, (byte)0x59, (byte)0x1C, (byte)0xA4, (byte)0x02, (byte)0x5A, (byte)0x25, (byte)0x69, (byte)0x38, (byte)0x62, (byte)0x3D, (byte)0xAA, (byte)0x0B, (byte)0x5D, (byte)0x08, (byte)0x74, (byte)0x08, (byte)0x5E, (byte)0x34, (byte)0xD6},
    };
    private static final List<String> BUILTIN_STT_KEYS = net.verity.config.KeyVault.decodeAll(ENCODED_STT_KEYS);

    public static boolean sttEnabled()         { return getBool("stt_enabled", true); }

    /** Offset поворота лица (в градусах) относительно линии взгляда */
    public static float faceYawOffsetDegrees() { return getFloat("face_yaw_offset_degrees", 180.0f); }
    public static void setFaceYawOffsetDegrees(float v) { setProperty("face_yaw_offset_degrees", String.valueOf(v)); }

    /** Источник STT ключей: "builtin" или "custom" */
    public static String sttKeySource()        { return getString("stt_key_source", "builtin"); }
    public static boolean useBuiltinSttKeys()  { return "builtin".equalsIgnoreCase(sttKeySource()); }

    // ──────── Позиция/поворот инструмента (топора, кирки) рядом со сферой Verity ────────
    // Настраивается через /veritydev toolpos <tx> <ty> <tz> <rx> <rz>
    public static float toolTX() { return getFloat("tool_tx", 0.30f); }
    public static float toolTY() { return getFloat("tool_ty", 0.00f); }
    public static float toolTZ() { return getFloat("tool_tz", 0.20f); }
    public static float toolRX() { return getFloat("tool_rx", 180.0f); }
    public static float toolRZ() { return getFloat("tool_rz", 10.0f); }

    public static void setToolPos(float tx, float ty, float tz, float rx, float rz) {
        props.setProperty("tool_tx", String.valueOf(tx));
        props.setProperty("tool_ty", String.valueOf(ty));
        props.setProperty("tool_tz", String.valueOf(tz));
        props.setProperty("tool_rx", String.valueOf(rx));
        props.setProperty("tool_rz", String.valueOf(rz));
        saveConfig();
    }

    /** Все STT ключи (builtin или custom) для ротации при 429 */
    public static List<String> sttApiKeys() {
        if (!useBuiltinSttKeys()) {
            List<String> custom = new ArrayList<>();
            String ck = getString("stt_api_key", "");
            if (!ck.isBlank()) custom.add(ck.trim());
            return custom;
        }
        List<String> result = new ArrayList<>(BUILTIN_STT_KEYS);
        String ck = getString("stt_api_key", "");
        if (!ck.isBlank() && !result.contains(ck.trim())) result.add(ck.trim());
        return result;
    }

    /** Первый STT ключ (для совместимости) */
    public static String sttApiKey()           { 
        List<String> keys = sttApiKeys();
        return keys.isEmpty() ? "" : keys.get(0);
    }
    public static String sttApiUrl()           { return getString("stt_api_url", "https://api.groq.com/openai/v1/audio/transcriptions"); }
    public static String sttModel()            { return getString("stt_model", "whisper-large-v3"); }
    public static String sttLanguage()         { return getString("stt_language", "ru"); }

    // ─────── ГОЛОС: кнопка и режим ──────────────────────────────────────────
    /** GLFW код кнопки (по умолчанию V = 86) */
    public static int voiceKey()               { return getInt("voice_key", 86); }
    /** Режим: "push" (удержание) или "toggle" (переключение) */
    public static String voiceMode()           { return getString("voice_mode", "push"); }

    private static final byte[][] ENCODED_TTS_KEYS = new byte[][] {
        {(byte)0x3E, (byte)0x0E, (byte)0x1C, (byte)0x16, (byte)0x0F, (byte)0x3B, (byte)0x28, (byte)0xA7, (byte)0x6F, (byte)0x5F, (byte)0x48, (byte)0x12, (byte)0x5F, (byte)0x6B, (byte)0x78, (byte)0xA3, (byte)0x38, (byte)0x0C, (byte)0x19, (byte)0x45, (byte)0x5D, (byte)0x34, (byte)0x2F, (byte)0xA6, (byte)0x6D, (byte)0x09, (byte)0x4F, (byte)0x15, (byte)0x5F, (byte)0x3E, (byte)0x76, (byte)0xAA},
        {(byte)0x3C, (byte)0x5A, (byte)0x46, (byte)0x13, (byte)0x5E, (byte)0x69, (byte)0x7E, (byte)0xA4, (byte)0x3E, (byte)0x0E, (byte)0x1B, (byte)0x15, (byte)0x5F, (byte)0x3B, (byte)0x7E, (byte)0xA4, (byte)0x38, (byte)0x09, (byte)0x4B, (byte)0x13, (byte)0x08, (byte)0x3E, (byte)0x7A, (byte)0xA5, (byte)0x6D, (byte)0x08, (byte)0x49, (byte)0x10, (byte)0x5C, (byte)0x34, (byte)0x2D, (byte)0xA1},
        {(byte)0x68, (byte)0x0E, (byte)0x4B, (byte)0x42, (byte)0x5F, (byte)0x34, (byte)0x79, (byte)0xA5, (byte)0x6C, (byte)0x09, (byte)0x4C, (byte)0x47, (byte)0x5F, (byte)0x6F, (byte)0x28, (byte)0xF0, (byte)0x3B, (byte)0x5F, (byte)0x4E, (byte)0x15, (byte)0x5E, (byte)0x68, (byte)0x2A, (byte)0xF6, (byte)0x3B, (byte)0x5D, (byte)0x4F, (byte)0x15, (byte)0x53, (byte)0x3B, (byte)0x2A, (byte)0xF0},
    };
    private static final List<String> BUILTIN_TTS_KEYS = net.verity.config.KeyVault.decodeAll(ENCODED_TTS_KEYS);

    public static boolean ttsEnabled()         { return getBool("tts_enabled", true); }

    /** Источник TTS ключей: "builtin" или "custom" */
    public static String ttsKeySource()        { return getString("tts_key_source", "builtin"); }
    public static boolean useBuiltinTtsKeys()  { return "builtin".equalsIgnoreCase(ttsKeySource()); }

    /** Все TTS ключи (builtin или custom) для ротации */
    public static List<String> ttsApiKeys() {
        if (!useBuiltinTtsKeys()) {
            List<String> custom = new ArrayList<>();
            String ck = getString("tts_api_key", "");
            if (!ck.isBlank()) custom.add(ck.trim());
            return custom;
        }
        List<String> result = new ArrayList<>(BUILTIN_TTS_KEYS);
        String ck = getString("tts_api_key", "");
        if (!ck.isBlank() && !result.contains(ck.trim())) result.add(ck.trim());
        return result;
    }

    /** Первый TTS ключ (для совместимости) */
    public static String ttsApiKey() {
        List<String> keys = ttsApiKeys();
        return keys.isEmpty() ? "" : keys.get(0);
    }
    public static String ttsVoiceId()          { return getString("tts_voice_id", "b3c51bf6029f4201a342b40827250784"); }
    public static String ttsModel()            { return getString("tts_model", "s2.1-pro-free"); }
    public static float ttsSpeed()             { return getFloat("tts_speed", 1.0f); }
    public static float ttsVolume()            { return Math.max(0.1f, Math.min(100.0f, getFloat("tts_volume", 100.0f))); }

    public static void setTtsEnabled(boolean v)   { setProperty("tts_enabled", String.valueOf(v)); }
    public static void setTtsApiKey(String v)     { setProperty("tts_api_key", v); }
    public static void setTtsKeySource(String v)  { setProperty("tts_key_source", v); }
    public static void setTtsVoiceId(String v)    { setProperty("tts_voice_id", v); }

    public static void setSttEnabled(boolean v)   { setProperty("stt_enabled", String.valueOf(v)); }
    public static void setSttKeySource(String v)  { setProperty("stt_key_source", v); }
    public static void setSttApiKey(String v)     { setProperty("stt_api_key", v); }
    public static void setSttApiUrl(String v)     { setProperty("stt_api_url", v); }
    public static void setSttModel(String v)      { setProperty("stt_model", v); }
    public static void setSttLanguage(String v)   { setProperty("stt_language", v); }
    public static void setVoiceKey(int v)         { setProperty("voice_key", String.valueOf(v)); }
    public static void setVoiceMode(String v)     { setProperty("voice_mode", v); }

    public static void load() {
        try {
            if (!Files.exists(CONFIG_PATH)) {
                createDefaultConfig();
                return;
            }
            props = new Properties();
            try (var reader = Files.newBufferedReader(CONFIG_PATH)) {
                props.load(reader);
            }
            loaded = true;
            VerityMod.LOGGER.info("Verity client config loaded ({} properties)", props.size());
        } catch (Exception e) {
            VerityMod.LOGGER.warn("Failed to load client config: {}", e.getMessage());
        }
    }

    private static void createDefaultConfig() throws IOException {
        String defaultConfig = """
                # Verity \u2014 \u043A\u043B\u0438\u0435\u043D\u0442\u0441\u043A\u0438\u0439 \u043A\u043E\u043D\u0444\u0438\u0433 (STT / \u0433\u043E\u043B\u043E\u0441\u043E\u0432\u043E\u0439 \u0432\u0432\u043E\u0434)
                # \u041F\u043E\u043B\u0443\u0447\u0438\u0442\u044C \u0431\u0435\u0441\u043F\u043B\u0430\u0442\u043D\u044B\u0439 Groq API \u043A\u043B\u044E\u0447: https://console.groq.com/keys

                # \u0412\u043A\u043B\u044E\u0447\u0438\u0442\u044C \u0433\u043E\u043B\u043E\u0441\u043E\u0432\u043E\u0439 \u0432\u0432\u043E\u0434 (push-to-talk)
                stt_enabled=true

                # \u2500\u2500\u2500 \u0412\u0438\u0437\u0443\u0430\u043B \u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500
                # \u041E\u0442\u0432\u0435\u0440\u0442\u044B\u0432\u0430\u043D\u0438\u0435 \u043B\u0438\u0446\u0430 \u043E\u0442\u043D\u043E\u0441\u0438\u0442\u0435\u043B\u044C\u043D\u043E \u043B\u0438\u043D\u0438\u0438 \u0432\u0437\u0433\u043B\u044F\u0434\u0430 (F3+B)
                # \u041F\u043E\u043F\u0440\u043E\u0431\u0443\u0439\u0442\u0435: -180, -90, 0, 90, 180
                face_yaw_offset_degrees=180

                # \u0418\u0441\u0442\u043E\u0447\u043D\u0438\u043A STT \u043A\u043B\u044E\u0447\u0435\u0439: builtin (\u043E\u0442 \u043C\u043E\u0434\u0430) \u0438\u043B\u0438 custom (\u0441\u0432\u043E\u0438)
                stt_key_source=builtin

                # API \u043A\u043B\u044E\u0447 \u0434\u043B\u044F STT (\u0438\u0441\u043F\u043E\u043B\u044C\u0437\u0443\u0435\u0442\u0441\u044F \u0432 \u0440\u0435\u0436\u0438\u043C\u0435 custom)
                stt_api_key=

                # URL API (Groq \u043F\u043E \u0443\u043C\u043E\u043B\u0447\u0430\u043D\u0438\u044E, \u043C\u043E\u0436\u043D\u043E \u0437\u0430\u043C\u0435\u043D\u0438\u0442\u044C \u043D\u0430 OpenAI)
                stt_api_url=https://api.groq.com/openai/v1/audio/transcriptions

                # \u041C\u043E\u0434\u0435\u043B\u044C Whisper
                stt_model=whisper-large-v3

                # \u042F\u0437\u044B\u043A \u0440\u0430\u0441\u043F\u043E\u0437\u043D\u0430\u0432\u0430\u043D\u0438\u044F (ru, en, \u0438\u043B\u0438 \u043F\u0443\u0441\u0442\u043E \u0434\u043B\u044F \u0430\u0432\u0442\u043E)
                stt_language=ru

                # \u2500\u2500\u2500 \u0413\u043E\u043B\u043E\u0441: \u043A\u043D\u043E\u043F\u043A\u0430 \u0438 \u0440\u0435\u0436\u0438\u043C \u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500
                # GLFW \u043A\u043E\u0434 \u043A\u043D\u043E\u043F\u043A\u0438 (V=86, H=72, G=71, X=88, Z=90, L=76, C=67)
                voice_key=86

                # \u0420\u0435\u0436\u0438\u043C: push (\u0443\u0434\u0435\u0440\u0436\u0430\u043D\u0438\u0435) \u0438\u043B\u0438 toggle (\u043F\u0435\u0440\u0435\u043A\u043B\u044E\u0447\u0435\u043D\u0438\u0435)
                voice_mode=push

                # \u2500\u2500\u2500 TTS (Fish Audio \u2014 \u043E\u0437\u0432\u0443\u0447\u043A\u0430 \u0440\u0435\u043F\u043B\u0438\u043A Verity) \u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500
                # \u0412\u043A\u043B\u044E\u0447\u0438\u0442\u044C \u043E\u0437\u0432\u0443\u0447\u043A\u0443? (\u043A\u043B\u044E\u0447\u0438 \u0432\u0441\u0442\u0440\u043E\u0435\u043D\u044B)
                tts_enabled=true

                # \u0418\u0441\u0442\u043E\u0447\u043D\u0438\u043A TTS \u043A\u043B\u044E\u0447\u0435\u0439: builtin (\u043E\u0442 \u043C\u043E\u0434\u0430) \u0438\u043B\u0438 custom (\u0441\u0432\u043E\u0438)
                tts_key_source=builtin

                # API \u043A\u043B\u044E\u0447 Fish Audio (\u0432 \u0440\u0435\u0436\u0438\u043C\u0435 custom)
                tts_api_key=

                # ID \u0433\u043E\u043B\u043E\u0441\u0430 Verity (\u043F\u043E \u0443\u043C\u043E\u043B\u0447\u0430\u043D\u0438\u044E)
                tts_voice_id=b3c51bf6029f4201a342b40827250784

                # \u041C\u043E\u0434\u0435\u043B\u044C TTS
                tts_model=s2.1-pro-free

                # \u0421\u043A\u043E\u0440\u043E\u0441\u0442\u044C \u0440\u0435\u0447\u0438 (0.5-2.0, 1.0 = \u043D\u043E\u0440\u043C\u0430)
                tts_speed=1.0

                # \u0413\u0440\u043E\u043C\u043A\u043E\u0441\u0442\u044C \u043E\u0437\u0432\u0443\u0447\u043A\u0438 Verity (0.1-100.0)
                tts_volume=100.0
                """;
        Files.createDirectories(CONFIG_PATH.getParent());
        Files.writeString(CONFIG_PATH, defaultConfig);
        loaded = true;
    }

    private static void setProperty(String key, String value) {
        if (!loaded) load();
        props.setProperty(key, value);
        saveConfig();
    }

    private static void saveConfig() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            try (var writer = Files.newBufferedWriter(CONFIG_PATH)) {
                props.store(writer, "Verity Client Config");
            }
            loaded = true;
        } catch (Exception e) {
            VerityMod.LOGGER.warn("Failed to save client config: {}", e.getMessage());
        }
    }

    private static String getString(String key, String defaultValue) {
        if (!loaded) load();
        return props.getProperty(key, defaultValue);
    }

    private static boolean getBool(String key, boolean defaultValue) {
        if (!loaded) load();
        String val = props.getProperty(key);
        if (val == null) return defaultValue;
        return val.equalsIgnoreCase("true") || val.equals("1") || val.equalsIgnoreCase("yes");
    }

    private static int getInt(String key, int defaultValue) {
        if (!loaded) load();
        String val = props.getProperty(key);
        if (val == null) return defaultValue;
        try {
            return Integer.parseInt(val.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private static float getFloat(String key, float defaultValue) {
        if (!loaded) load();
        String val = props.getProperty(key);
        if (val == null) return defaultValue;
        try {
            return Float.parseFloat(val.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
