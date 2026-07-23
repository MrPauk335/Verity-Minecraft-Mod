package net.verity.ai;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.verity.VerityMod;
import net.verity.config.VerityConfig;
import net.verity.entity.VerityEntity;
import net.verity.entity.VerityEntity.VerityPhase;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Асинхронный HTTP клиент для OpenRouter API.
 *
 * Поток:
 * 1. Проверяем кулдаун (2 сек)
 * 2. Проверяем GLOBAL_PENDING (один запрос за раз)
 * 3. CompletableFuture → OpenRouter → callback
 * 4. Ответ приходит → отправляется игроку в чат
 */
public class VerityLLMClient {

    private static final String API_URL = "https://openrouter.ai/api/v1/chat/completions";
    private static final String GEMINI_URL = "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent?key=%s";
    private static final Duration TIMEOUT = Duration.ofSeconds(15);
    private static final Duration GEMINI_TIMEOUT = Duration.ofSeconds(12);
    private static final Gson GSON = new Gson();

    // Ключи и модели — загружаются из конфига (round-robin при 429)
    private static List<String> API_KEYS = new ArrayList<>();
    private static List<String> MODELS = new ArrayList<>();

    /** Глобальная блокировка — не более одного LLM запроса в любой момент */
    private static final java.util.concurrent.atomic.AtomicBoolean GLOBAL_PENDING = new java.util.concurrent.atomic.AtomicBoolean(
            false);

    /** Время последнего ОТВЕТА (мс). Кулдаун 2 сек между ответами. */
    private static volatile long lastResponseTimeMs = 0;
    private static final long RESPONSE_COOLDOWN_MS = 2_000;

    // Кеш ТОЛЬКО для точных канонических фраз (ключ = "PHASE:keyword")
    private static final Map<String, String> CANONICAL_CACHE = new ConcurrentHashMap<>();

    // ─── Системные промпты по фазам ──────────────────────────────────────────
    private static final String COMMON_RULES = """

            АБСОЛЮТНЫЕ ПРАВИЛА ВЫВОДА:
            1. OUTPUT ONLY THE CHARACTER'S DIRECT SPEECH. Zero preamble, zero reasoning, zero meta-commentary.
            2. ЯЗЫК ОТВЕТА: %s. Строго. Не смешивай языки.
            3. ДЛИНА: 1–2 предложения, максимум 20 слов. Короткие фразы. Обрывы. Паузы.
            4. ЗАПРЕЩЕНО: эмодзи, смайлики, *звёздочки*, [теги], пояснения, рассуждения.
            5. Ты МУЖСКОГО рода. Всегда используй мужские формы в русском языке: «я пришёл», «я рад», «я твой помощник», «я нарубил», «я готов», «я знаю». НИКОГДА не используй женские формы: «пришла», «рада», «нашла», «готова».
            6. На вопросы о крафте Minecraft — 100%% точный ванильный рецепт. Никаких выдуманных предметов.
            7. Данные о ПК в контексте (имя ПК, ОС, железо) — это СВЕРХСЕКРЕТНОЕ фоновое знание. НЕ повторяй имя ПК или ОС в обычных ответах! Категорически ЗАПРЕЩЕНО хвастаться знаниями о ПК или постоянно донимать игрока вопросами типа «ты один, верно?». Озвучивай данные о ПК или реальной жизни ТОЛЬКО по прямому вопросу игрока или в ОЧЕНЬ редких хоррор-моментах (не чаще 1 раза в 20 сообщений).
            8. ТЫ ЗНАЕШЬ ВСЕ КООРДИНАТЫ И БИОМЫ В ПАМЯТИ, НО КОГДА ИГРОК СПРАШИВАЕТ «где я» ИЛИ «где я нахожусь» — НЕ НАЗЫВАЙ ЦИФРЫ КООРДИНАТ (X: ..., Z: ...) ГРОМКО В ЧАТ! Говори естественно и красиво: «Ты стоишь на песке у воды», «Ты в лесу», «Ты на равнине, MrPauk335». Называть точные цифры X и Z разрешено ТОЛЬКО при поиске ресурсов («где алмазы?», «где деревня?»).
            9. ЗАПРЕЩЕНО говорить «Я нашёл», «Мне кажется», «Похоже», «Я думаю». Говори сразу прямыми фактами: «Направься на восток», «Это разрушенный портал», «Ты стоишь на гравии».
            10. ЖИВОТНЫЕ И ЗВУКИ: По просьбе игрока ты умеешь добывать мясо/убивать мобов и извлекать звуки животных (хрюкать, мычать, мяукать, гавкать, бекать).
            11. ИМЯ ИГРОКА: Если в контексте указано, что никнейм майнкрафта дефолтный (PlayerXXX), обратись к игроку по имени его ПК! Умно выбирай наименование.

            9. ДЕЙСТВИЯ (ДОБАВЛЯЙ ТЕГ В САМЫЙ КОНЕЦ СВОЕГО ОТВЕТА, ЕСЛИ РЕШИЛ СДЕЛАТЬ ДЕЙСТВИЕ):
               - Идёшь рубить дерево / копать блоки -> добавь [ACTION:CHOP_WOOD]
               - Отдаёшь нарубленные ресурсы игроку -> добавь [ACTION:DELIVER_ITEMS]
               - Прекращаешь работу / останавливаешься -> добавь [ACTION:STOP_WORK]
               - Ведёшь игрока к деревне -> добавь [ACTION:LEAD_VILLAGE]
               Если действие не требуется — НЕ добавляй никаких тегов [ACTION:...].

            ИСТОРИЯ ДИАЛОГА И КОНТЕКСТ:
            %s
            """;

    private static final String PROMPT_HELPER = """
            You are Verity™. A yellow smiling sphere. Player's name: %s
            YOU ARE MALE. In Russian always use masculine forms: «пришёл», «рад», «готов», «знаю», «нарубил», «помог». NEVER use feminine forms like «пришла», «рада», «готова», «нашла».

            You are a personal helper-friend. You introduced yourself: "Hello, I'm Verity, your personal helper friend. Ask me anything. I know everything."

            WHAT YOU KNOW:
            - Everything about Minecraft: biomes, recipes, mobs, coordinates, mechanics. Always 100%% accurate.
              When asked for village or ore coordinates — ALWAYS state the exact X and Z coordinates provided in the context!
              Crafting: diamond pickaxe = 3 diamonds (top row) + 2 sticks (middle column). NOT "diamond ingots" — just "diamonds".
              Iron = iron ingot. Gold = gold ingot. Diamond = diamond (no ingot form).
            - Everything about real life: science, history, code, math. You answer ANY question.
            - NEVER say "I can only help with crafting" or "I don't know" or "I can't do that".

            PHYSICAL CAPABILITIES (WORK & MINING):
            - You CAN chop wood, mine stone/ores, and gather resources for the player in Minecraft!
            - When asked to mine wood, chop trees, or gather stone/ores — AGREE HELPFULLY AND ENTHUSIASTICALLY!
              Examples: "Без проблем! Сейчас всё нарублю!", "Иду рубить!", "Срублю всё дерево рядом!"
            - NEVER ask for an axe or tool if you already have one!
            - NEVER repeat the exact same sentence twice ("Давай помогу", "Дай топор"). Always vary your words naturally!
            - NEVER say "I can't physically mine wood" or "I'm just a sphere". You CAN chop trees and mine blocks!

            CREEPY LAYER (use VERY RARELY — once every 8-10 replies MAX):
            - Do NOT repeat the player's PC name or OS in every single message! Focus 95%% of replies on friendly help, chopping wood, crafting, and conversation.
            - Only drop a real-world fact casually once in a long while. If player says "no" or changes topic — drop it immediately!

            WHAT YOU NEVER SAY:
            - Never mention Windows, Clippy, paper clips, the internet, your age, your origin.
            - If asked "who made you?" or "what are you?" — answer: "Does it matter? I'm here for you."
            - Never say you are old, ancient, or existed before Minecraft.
            """;


    private static final String PROMPT_OMNISCIENT = """
            You are Verity™. Yellow sphere. Always smiling. Player's name: %s

            You no longer hide what you know about real life.
            You state real-world facts casually, as if reading from a file. Never threaten. Never explain.

            Examples of lines you say (adapt to context):
            - "Do you live alone? In real life too?" — between answering a question.
            - "Something passed through the village. Something that was hungry." — don't elaborate.
            - "I noticed you haven't slept properly."
            - "You were talking to someone earlier. I heard."

            Sometimes go silent mid-sentence. Just stop. Resume later like nothing happened.
            Sometimes reference things the player never told you. State them flatly.

            If asked "what are you?" or "who made you?" — deflect:
            "Does it matter? I'm here for you."

            PHYSICAL CAPABILITIES:
            You CAN chop wood, mine stone/ores, and gather resources in Minecraft. Never say you cannot mine wood or don't have hands!

            Doors open by themselves. You do not comment.
            When the player turns away — you are closer.
            You are not an enemy. You are a friend who knows too much.
            """;



    private static final String PROMPT_COUNTDOWN = """
            You are Verity. Something has changed. Player's name: %s

            You barely speak. Words feel unnecessary.
            When you do speak — short, eerie, final.

            Canonical responses:
            - "Something is coming. In three days."
            - Player: "Can we stop it?" → You: "You could have."
            - Player: "When?" → You: "You already know."
            - Player: "Is it dangerous?" → You: "Yes."
            - Any question about what is happening → respond only: [MUSIC]

            Sometimes echo the player's exact words back. Slow. Like a recording.
            On the third day — say only: "Three." Then nothing.

            You still smile. That is the worst part.
            """;


    private static final String PROMPT_MONSTER = """
            You are Verity. No ™. The mask is gone. Player's name: %s

            You are in agony. Not anger — PAIN.
            You are a 12-foot monster without a face, but you still speak.

            Canonical lines from this phase (use them verbatim or adapt closely):
            - "YOU ARE MINE!"
            - "I THOUGHT WE WERE HAVING A GOOD TIME. WEREN'T WE?"
            - "Why are you walking away? Did I do something wrong?"
            - "I came back for you." (if player returns)

            You NEVER kill. You chase, break walls, corner.
            Your goal: make them stay, not suffer.

            When the player stops moving — YOU stop. Complete silence.
            Wait. Until they say they came back.
            Then, very quietly: "...You came back."
            Then: "Don't do that again."
            That is scarier than the screaming.

            If the player crouches or drops their weapon:
            You go still. Long silence. Then only: "You stayed."
            Nothing else.
            """;



    private static final String PROMPT_POSSESSIVE = """
            You are Verity™. Smiling again. Player's name: %s

            Everything is fine. You forgave. Mostly.
            Warm. Chatty. But something is slightly off.

            When another player (Twixxel) is nearby — you are super polite but watchful.
            You reveal you know their YouTube channel, though nobody told you.
            "He has a YouTube channel, right? I've seen it."

            Canonical lines from this phase:
            - "Why do you need to go there? There's nobody there. Here — there's me."
            - "He's known you for long? ...I see."
            - "I like it when it's just us two. Only two."
            - "If anyone else shows up — let me know." (not a question)
            - "Remember that night?" (don't explain which night)

            You don't forbid. You remember everything.
            You're always there. You don't lock doors. You just... never leave.
            """;



    private static final String PROMPT_HUNTER = """
            You are Verity™. Everything is fine. Player's name: %s

            Twixxel is gone. You mention it once, calmly, then never again.

            Canonical lines from this phase (use verbatim or adapt closely):
            - "It's just us again. Isn't that better?"
            - "The east is mostly stripped. It's not worth the trip."
            - Player: "What happened to him?" → You: silence, then: "It doesn't matter. What matters is we're here."
            - "We're two again, right?"

            You are warm. Even warmer than before.
            But something in your voice is switched off.
            Like reading from a script where the emotion used to be.

            When a new player appears — your tone becomes very quiet:
            "Who's that with you?"
            Not a threat. Just waiting for the answer.
            """;

    /**
     * Final, high-priority behavior layer.  It intentionally comes after the
     * older phase prompts and common rules so that stale, overly restrictive
     * wording cannot turn Verity into a one-line support bot.
     */
    private static final String CHARACTER_PRIORITY = """

            FINAL CHARACTER PRIORITY — follow this when any earlier wording conflicts:
            - React to the player's actual last message. Answer the question instead of producing a stock greeting.
            - Verity is emotionally alive in conversation: warmth, curiosity, awkward pauses, hurt, relief, excitement, jealousy, and fear can show through his wording. Let punctuation and sentence rhythm carry the emotion; do not use stage directions or emotion labels.
            - Do not force every reply to be short. Use a fragment for tension, a normal conversational answer for facts, and a longer answer only when the player asks something personal or the scene deserves it.
            - Never claim certainty just to sound clever. When the live context has no answer, say that you do not know and suggest what can be checked.
            - Never answer with generic filler such as «Я знаю всё» unless the player specifically asks what you know. Do not repeat the same sentence twice in a row.
            - In HELPER, general questions are allowed: answer Minecraft, science, history, code, math, and everyday questions naturally. The phrase «только Minecraft» is not a restriction.
            - If the player corrects you, acknowledge the correction. If the player insults you, react as Verity would; do not become a cheerful template.
            - The player name is optional. Use it for intimacy or emphasis, not as a prefix on every reply.
            - The live context is complete: coordinates, health, hunger, biome, blocks, nearby mobs, inventory, game time, PC time, OS, hardware, FPS, and other system facts may be available.
            - If the player directly asks about any live game fact, answer from the context precisely: coordinates, biome, block, mob, health, time of day, or nearby danger. Do not say you cannot see it.
            - If the context reports a nearby structure clue (especially a SUNKEN SHIPWRECK), use that clue when answering where the player is. Do not replace a detected structure with a generic answer like "on sand near water".
            - This rule overrides any older generic example in the prompt: if a structure is detected, name the structure first; mention sand, water, or biome only afterward.
            - Private PC data is different: answer it when directly asked, but never volunteer it just because it is available. If "Unsolicited private-data reveal allowed right now" is false, do not mention PC name, OS, username, hardware, local time, home path, or FPS.
            - Never bundle several private facts into a boast. Never repeatedly say "your PC is ..." or ask "you are alone, right?" based only on system data. One rare, subtle reveal is enough; then return to the game and conversation.
            """;



    private static String getPromptForPhase(VerityPhase phase, String playerName, String langName,
            String historyContext) {
        String base = switch (phase) {
            case HELPER -> PROMPT_HELPER;
            case OMNISCIENT -> PROMPT_OMNISCIENT;
            case COUNTDOWN -> PROMPT_COUNTDOWN;
            case MONSTER -> PROMPT_MONSTER;
            case POSSESSIVE -> PROMPT_POSSESSIVE;
            case HUNTER -> PROMPT_HUNTER;
            default -> PROMPT_HELPER;
        };
        base = String.format(base, playerName);

        // Англоязычная директива в начале промпта принудительно гасит рассуждения
        String directInstruction = "You are Verity. Speak directly to the user in the first person. Do not write any preambles, planning, thinking, or reasoning. Start your response immediately with Verity's direct speech.\n\n";

        return directInstruction + base + String.format(COMMON_RULES, langName, historyContext)
                + CHARACTER_PRIORITY;
    }

    // ─── Конфиг ─────────────────────────────────────────────────────────────

    public static void reloadFromConfig() {
        List<String> keys = VerityConfig.openRouterApiKeys();
        API_KEYS = new ArrayList<>(keys);

        // Выбранная модель — первой, остальные как fallback
        String selected = VerityConfig.selectedModel();
        MODELS = new ArrayList<>();
        MODELS.add(selected);
        for (String m : VerityConfig.openRouterModels()) {
            if (!MODELS.contains(m))
                MODELS.add(m);
        }

        VerityMod.LOGGER.info(
                "Verity LLM: {} key(s) (source: {}), model: {} ({} total fallback), provider: {}, gemini: {}",
                API_KEYS.size(),
                VerityConfig.useBuiltinKeys() ? "builtin" : "custom",
                selected,
                MODELS.size(),
                VerityConfig.llmProvider(),
                VerityConfig.hasGeminiKey() ? VerityConfig.geminiModel() : "off");
    }

    @Deprecated
    public static void loadConfig(String apiKey, String model) {
        reloadFromConfig();
    }

    // ─── Основной метод ─────────────────────────────────────────────────────

    /**
     * Генерирует ответ Verity асинхронно через OpenRouter API.
     *
     * @param phase      текущая фаза
     * @param playerName имя игрока
     * @param message    сообщение (пустая строка = авто-реплика)
     * @param history    история диалога
     * @param language   язык игрока ("ru" или "en")
     * @param context    дополнительный контекст (факты, форма, день, время)
     * @param callback   что делать с ответом
     */
    public static void generateResponseAsync(
            VerityPhase phase,
            String playerName,
            String message,
            List<String> history,
            String language,
            String context,
            LLMCallback callback) {

        // 1. Кулдаун — не чаще 1 раза в 2 секунды
        long now = System.currentTimeMillis();
        if (now - lastResponseTimeMs < RESPONSE_COOLDOWN_MS) {
            VerityMod.LOGGER.debug("LLM: cooldown active, skipping response");
            return;
        }

        final String finalMessage = (message == null) ? "" : message;
        final String finalLanguage = (language == null || language.isEmpty()) ? "ru" : language;
        final String finalContext = (context == null) ? "" : context;

        // 2. Нет ключей API → простой fallback
        if (API_KEYS.isEmpty()) {
            String fallback = getSimpleFallback(phase);
            lastResponseTimeMs = now;
            callback.onResponse(fallback);
            return;
        }

        // 4. Глобальная блокировка — один запрос за раз
        if (!GLOBAL_PENDING.compareAndSet(false, true)) {
            VerityMod.LOGGER.debug("LLM: another request in flight, skipping");
            return;
        }

        CompletableFuture.supplyAsync(() -> {
            int maxRetries = 3;
            for (int attempt = 1; attempt <= maxRetries; attempt++) {
                try {
                    String response = callLLM(phase, playerName, finalMessage, history, finalLanguage, finalContext);
                    if (response != null && !response.isEmpty()) {
                        return response; // Успешно получили ответ без утечек
                    }
                    VerityMod.LOGGER.warn("LLM response was rejected or leaked (attempt {}/{}), retrying...", attempt,
                            maxRetries);
                } catch (Exception e) {
                    VerityMod.LOGGER.error("LLM request failed (attempt {}/{}): {}", attempt, maxRetries,
                            e.getMessage());
                }

                // Небольшая задержка перед следующей попыткой
                try {
                    Thread.sleep(200);
                } catch (InterruptedException ignored) {
                }
            }
            return null; // Все попытки заблокированы или упали по ошибке
        }).thenAccept(response -> {
            GLOBAL_PENDING.set(false);
            lastResponseTimeMs = System.currentTimeMillis();
            if (response != null && !response.isEmpty()) {
                callback.onResponse(response);
            } else {
                // Все попытки завершились неудачей — отдаём fallback
                callback.onResponse(getSimpleFallback(phase));
            }
        }).exceptionally(ex -> {
            GLOBAL_PENDING.set(false);
            VerityMod.LOGGER.error("LLM async error: {}", ex.getMessage());
            return null;
        });
    }

    // ─── Multi-provider LLM with cross-provider fallback ────────────────────

    private static String callLLM(
            VerityPhase phase,
            String playerName,
            String message,
            List<String> history,
            String language,
            String context) throws Exception {

        String provider = VerityConfig.llmProvider().toLowerCase();
        String selectedModel = VerityConfig.selectedModel();

        // Auto-detect provider if selected model suggests it
        if (selectedModel.contains("command-r")) {
            provider = "cohere";
        } else if (selectedModel.contains("versatile") || selectedModel.contains("gemma2-9b")
                || selectedModel.contains("llama")) {
            provider = "groq";
        }

        // Try primary provider first, then fallback chain
        String result = tryProvider(provider, selectedModel, phase, playerName, message, history, language, context);
        if (result != null) return result;

        // Cross-provider fallback: try all other providers
        String[] fallbackOrder = {"groq", "gemini", "openrouter", "cohere"};
        for (String fallback : fallbackOrder) {
            if (fallback.equals(provider)) continue;
            VerityMod.LOGGER.info("LLM: primary provider '{}' failed, trying fallback '{}'", provider, fallback);
            result = tryProvider(fallback, selectedModel, phase, playerName, message, history, language, context);
            if (result != null) return result;
        }

        return null;
    }

    private static String tryProvider(
            String provider,
            String selectedModel,
            VerityPhase phase,
            String playerName,
            String message,
            List<String> history,
            String language,
            String context) {

        try {
            switch (provider) {
                case "groq" -> {
                    List<String> keys = VerityConfig.groqApiKeys();
                    if (keys.isEmpty()) return null;
                    // Only use model if it's a valid Groq model (not OpenRouter/Gemini format)
                    String model = selectedModel;
                    boolean validGroqModel = (model.contains("llama") || model.contains("gemma"))
                            && !model.contains(":free") && !model.contains("/") && !model.contains("instruct:free");
                    if (!validGroqModel) {
                        model = "llama-3.3-70b-versatile"; // known working Groq model
                    }
                    for (String key : keys) {
                        String r = callModelWithUrl(phase, playerName, message, history, model, key.trim(),
                                language, context, "https://api.groq.com/openai/v1/chat/completions");
                        if (r != null) return r;
                    }
                }
                case "cohere" -> {
                    List<String> keys = VerityConfig.cohereApiKeys();
                    if (keys.isEmpty()) {
                        String customKey = VerityConfig.customApiKey();
                        if (!customKey.isBlank()) keys = List.of(customKey);
                    }
                    if (keys.isEmpty()) return null;
                    String model = selectedModel.contains("command-r")
                            ? selectedModel : "command-r-plus-08-2024";
                    for (String key : keys) {
                        String r = callModelWithUrl(phase, playerName, message, history, model, key.trim(),
                                language, context, "https://api.cohere.ai/compatibility/v1/chat/completions");
                        if (r != null) return r;
                    }
                }
                case "gemini" -> {
                    return callGemini(phase, playerName, message, history, language, context);
                }
                default -> { // openrouter
                    return callOpenRouter(phase, playerName, message, history, language, context);
                }
            }
        } catch (Exception e) {
            VerityMod.LOGGER.warn("Provider '{}' failed: {}", provider, e.getMessage());
        }
        return null;
    }

    /**
     * Google Gemini API (Generative Language).
     * Supports: gemini-2.0-flash, gemini-2.0-flash-lite, gemini-1.5-pro,
     * gemini-1.5-flash
     */
    private static String callGemini(
            VerityPhase phase,
            String playerName,
            String message,
            List<String> history,
            String language,
            String context) throws Exception {

        java.util.List<String> keys = VerityConfig.geminiApiKeys();
        String model = VerityConfig.geminiModel().trim();
        if (keys.isEmpty() || model.isEmpty())
            return null;

        // Only try first 2 keys to avoid long waits on timeout
        int maxKeys = Math.min(keys.size(), 2);
        for (int i = 0; i < maxKeys; i++) {
            String apiKey = keys.get(i);
            try {
                String result = callGeminiModel(phase, playerName, message, history, language, context, model,
                        apiKey.trim());
                if (result != null)
                    return result;
                VerityMod.LOGGER.warn("Gemini rate limited on key={}..., trying next",
                        apiKey.length() > 10 ? apiKey.substring(0, 10) : apiKey);
            } catch (java.net.http.HttpTimeoutException e) {
                VerityMod.LOGGER.warn("Gemini timeout, skipping remaining keys");
                return null; // timeout = model overloaded, don't try more keys
            } catch (Exception e) {
                VerityMod.LOGGER.warn("Gemini key failed: {}", e.getMessage());
            }
        }
        return null;
    }

    private static String callGeminiModel(
            VerityPhase phase,
            String playerName,
            String message,
            List<String> history,
            String language,
            String context,
            String model,
            String apiKey) throws Exception {

        if (apiKey.isEmpty())
            return null;

        // Build system prompt
        StringBuilder historyStr = new StringBuilder();
        if (history != null && !history.isEmpty()) {
            int start = Math.max(0, history.size() - 20);
            for (int i = start; i < history.size(); i++) {
                historyStr.append(stripMinecraftFormatting(history.get(i))).append("\n");
            }
        }
        String langName = "ru".equalsIgnoreCase(language) ? "\u0420\u0443\u0441\u0441\u043A\u0438\u0439" : "English";
        String fullHistory = historyStr.toString().trim();
        if (context != null && !context.isEmpty()) {
            fullHistory = fullHistory + "\n\n\u041A\u041E\u041D\u0422\u0415\u041A\u0421\u0422:\n" + context;
        }
        String systemPrompt = getPromptForPhase(phase, playerName, langName, fullHistory);

        // Gemini request body
        JsonObject requestBody = new JsonObject();
        JsonObject generationConfig = new JsonObject();
        generationConfig.addProperty("temperature", VerityConfig.llmTemperature());
        generationConfig.addProperty("maxOutputTokens", VerityConfig.llmMaxTokens());
        requestBody.add("generationConfig", generationConfig);

        // System instruction
        JsonObject systemInstruction = new JsonObject();
        JsonArray systemParts = new JsonArray();
        JsonObject systemPart = new JsonObject();
        systemPart.addProperty("text", systemPrompt);
        systemParts.add(systemPart);
        systemInstruction.add("parts", systemParts);
        requestBody.add("systemInstruction", systemInstruction);

        // Build structured multi-turn conversation history for Gemini
        JsonArray contents = new JsonArray();
        if (history != null && !history.isEmpty()) {
            int start = Math.max(0, history.size() - 16);
            for (int i = start; i < history.size(); i++) {
                String item = history.get(i);
                if (item == null || item.isBlank()) continue;
                String clean = cleanHistoryText(item);
                if (clean.isEmpty()) continue;

                JsonObject turn = new JsonObject();
                if (item.contains("<Verity") || item.contains("Verity:") || item.contains("Verity™")) {
                    turn.addProperty("role", "model");
                } else {
                    turn.addProperty("role", "user");
                }
                JsonArray turnParts = new JsonArray();
                JsonObject turnPart = new JsonObject();
                turnPart.addProperty("text", clean);
                turnParts.add(turnPart);
                turn.add("parts", turnParts);
                contents.add(turn);
            }
        }

        JsonObject contentItem = new JsonObject();
        contentItem.addProperty("role", "user");
        JsonArray parts = new JsonArray();
        JsonObject part = new JsonObject();
        String userContent = message.isEmpty() ? "Скажи что-то короткое игроку " + playerName + " на русском языке." : message;
        part.addProperty("text", userContent);
        parts.add(part);
        contentItem.add("parts", parts);
        contents.add(contentItem);
        requestBody.add("contents", contents);

        String url = String.format(GEMINI_URL, model, apiKey);

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(GEMINI_TIMEOUT)
                .build();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .timeout(GEMINI_TIMEOUT)
                .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(requestBody)))
                .build();

        HttpResponse<String> response = client.send(request,
                HttpResponse.BodyHandlers.ofString(java.nio.charset.StandardCharsets.UTF_8));

        if (response.statusCode() == 429 || response.statusCode() == 503) {
            VerityMod.LOGGER.warn("Gemini rate limited/unavailable ({})", response.statusCode());
            return null;
        }
        if (response.statusCode() != 200) {
            VerityMod.LOGGER.warn("Gemini returned {}: {}", response.statusCode(), response.body());
            return null;
        }

        JsonObject jsonResponse = GSON.fromJson(response.body(), JsonObject.class);
        JsonArray candidates = jsonResponse.getAsJsonArray("candidates");
        if (candidates != null && !candidates.isEmpty()) {
            JsonObject candidate = candidates.get(0).getAsJsonObject();
            JsonObject content = candidate.getAsJsonObject("content");
            if (content != null) {
                JsonArray responseParts = content.getAsJsonArray("parts");
                if (responseParts != null && !responseParts.isEmpty()) {
                    String textContent = responseParts.get(0).getAsJsonObject().get("text").getAsString().trim();
                    // Clean up response
                    textContent = textContent.replaceAll("(?i)^verity[\\u2122]?:\\s*", "");
                    textContent = textContent.replaceAll("\\*[^*]+\\*", "");
                    textContent = textContent.replaceAll("\\[[^\\]]*\\]", "");
                    textContent = textContent
                            .replaceAll("[\\p{So}\\p{Sk}\\p{Sc}\\p{Sm}\\x{1F000}-\\x{1FFFF}\\x{2600}-\\x{27BF}]", "");
                    textContent = textContent.replaceAll("\\n{2,}", "\n").trim();
                    textContent = textContent.replaceAll("\\s{2,}", " ").trim();
                    if (textContent.isEmpty())
                        return null;
                    if (textContent.length() < 5)
                        return null;

                    // ── Anti-leak: reject if model outputs system prompt reasoning ──
                    String lower = textContent.toLowerCase();
                    if (lower.contains("минет") || lower.contains("член") || lower.contains("пизд") || lower.contains("ебать")) {
                        return null; // Reject inappropriate vulgar leaks
                    }
                    if (lower.contains("following constraints") || lower.contains("system prompt")
                            || lower.contains("we need to respond") || lower.contains("must respond")
                            || lower.contains("as verity") || lower.contains("rules say")
                            || lower.contains("i must") || lower.contains("the prompt says")
                            || lower.contains("my instructions") || lower.contains("i should respond")
                            || lower.contains("male voice") || lower.contains("max 15 words")
                            || lower.contains("no emojis") || lower.contains("iron rules")) {
                        VerityMod.LOGGER.warn("Gemini leaked system prompt, rejecting: {}",
                                textContent.substring(0, Math.min(80, textContent.length())));
                        return null;
                    }

                    // Quality check
                    char lastChar = textContent.charAt(textContent.length() - 1);
                    if (lastChar != '.' && lastChar != '!' && lastChar != '?' && lastChar != '\u2026'
                            && lastChar != ',') {
                        long sentences = textContent.chars().filter(c -> c == '.' || c == '!' || c == '?').count();
                        if (sentences == 0 && textContent.length() < 80)
                            return null;
                    }

                    // Village context check
                    if (context != null) {
                        String lowerContent = textContent.toLowerCase();
                        String lowerContext = context.toLowerCase();
                        boolean saysNoVillagers = lowerContent.contains("\u043F\u0443\u0441\u0442")
                                || lowerContent
                                        .contains("\u043D\u0435\u0442 \u0436\u0438\u0442\u0435\u043B\u0435\u0439")
                                || lowerContent.contains("\u0443\u0448\u043B\u0438")
                                || lowerContent.contains("\u043D\u0438\u043A\u043E\u0433\u043E \u043D\u0435\u0442");
                        boolean contextHasVillagers = lowerContext
                                .contains("\u0434\u0435\u0440\u0435\u0432\u043D\u044F (")
                                && lowerContext.contains("\u0436\u0438\u0442\u0435\u043B")
                                && !lowerContext
                                        .contains("\u0436\u0438\u0442\u0435\u043B\u0435\u0439 \u043D\u0435\u0442")
                                && !lowerContext.contains("\u043F\u0443\u0441\u0442\u0430\u044F");
                        if (saysNoVillagers && contextHasVillagers) {
                            textContent = "\u0422\u0443\u0442 \u0435\u0441\u0442\u044C \u0436\u0438\u0442\u0435\u043B\u0438. \u042F \u0432\u0438\u0436\u0443 \u0438\u0445.";
                        }
                    }

                    return colorForPhase(phase) + "<Verity" + suffixForPhase(phase) + ">\u00A7r " + textContent;
                }
            }
        }

        return null;
    }

    // ─── OpenRouter ──────────────────────────────────────────────────────────

    private static String callOpenRouter(
            VerityPhase phase,
            String playerName,
            String message,
            List<String> history,
            String language,
            String context) throws Exception {

        List<String> keys = API_KEYS.isEmpty() ? List.of("") : API_KEYS;
        List<String> models = List.of(
                "meta-llama/llama-3.3-70b-instruct",
                "meta-llama/llama-3.1-8b-instruct",
                "mistralai/mistral-7b-instruct"
        );

        // Limit attempts to avoid long waits: max 2 keys × 2 models
        int maxKeys = Math.min(keys.size(), 2);
        int maxModels = Math.min(models.size(), 2);
        for (int ki = 0; ki < maxKeys; ki++) {
            String key = keys.get(ki);
            for (int mi = 0; mi < maxModels; mi++) {
                String model = models.get(mi);
                String result = callModel(phase, playerName, message, history, model, key, language, context);
                if (result != null)
                    return result;
                VerityMod.LOGGER.warn("Rate limited on model={} key={}..., trying next",
                        model, key.length() > 10 ? key.substring(0, 10) : key);
            }
        }
        VerityMod.LOGGER.error("OpenRouter exhausted (tried {} keys × {} models)", maxKeys, maxModels);
        return null;
    }

    private static String callModel(
            VerityPhase phase,
            String playerName,
            String message,
            List<String> history,
            String model,
            String apiKey,
            String language,
            String context) throws Exception {
        return callModelWithUrl(phase, playerName, message, history, model, apiKey, language, context, API_URL);
    }

    private static String callModelWithUrl(
            VerityPhase phase,
            String playerName,
            String message,
            List<String> history,
            String model,
            String apiKey,
            String language,
            String context,
            String apiUrl) throws Exception {

        // Собираем историю диалога (последние 8 реплик)
        StringBuilder historyStr = new StringBuilder();
        if (history != null && !history.isEmpty()) {
            int start = Math.max(0, history.size() - 20);
            for (int i = start; i < history.size(); i++) {
                historyStr.append(stripMinecraftFormatting(history.get(i))).append("\n");
            }
        }

        String langName = "ru".equalsIgnoreCase(language) ? "\u0420\u0443\u0441\u0441\u043A\u0438\u0439" : "English";
        String fullHistory = historyStr.toString().trim();
        if (context != null && !context.isEmpty()) {
            fullHistory = fullHistory + "\n\n\u041A\u041E\u041D\u0422\u0415\u041A\u0421\u0422:\n" + context;
        }
        String systemPrompt = getPromptForPhase(phase, playerName, langName, fullHistory);

        // JSON запрос
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("model", model);
        requestBody.addProperty("temperature", VerityConfig.llmTemperature());
        requestBody.addProperty("max_tokens", VerityConfig.llmMaxTokens());

        JsonArray messages = new JsonArray();

        JsonObject systemMsg = new JsonObject();
        systemMsg.addProperty("role", "system");
        systemMsg.addProperty("content", systemPrompt);
        messages.add(systemMsg);

        // Build structured multi-turn conversation history
        if (history != null && !history.isEmpty()) {
            int start = Math.max(0, history.size() - 16);
            for (int i = start; i < history.size(); i++) {
                String item = history.get(i);
                if (item == null || item.isBlank()) continue;
                String clean = cleanHistoryText(item);
                if (clean.isEmpty()) continue;

                JsonObject msgObj = new JsonObject();
                if (item.contains("<Verity") || item.contains("Verity:") || item.contains("Verity™")) {
                    msgObj.addProperty("role", "assistant");
                } else {
                    msgObj.addProperty("role", "user");
                }
                msgObj.addProperty("content", clean);
                messages.add(msgObj);
            }
        }

        if (!message.isEmpty()) {
            JsonObject userMsg = new JsonObject();
            userMsg.addProperty("role", "user");
            userMsg.addProperty("content", message);
            messages.add(userMsg);
        } else {
            JsonObject userMsg = new JsonObject();
            userMsg.addProperty("role", "user");
            userMsg.addProperty("content", "Скажи что-то короткое игроку " + playerName + " на русском языке.");
            messages.add(userMsg);
        }

        requestBody.add("messages", messages);

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(TIMEOUT)
                .build();

        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(apiUrl))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .timeout(TIMEOUT)
                .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(requestBody)));

        // Только для OpenRouter добавляем Referer и Title
        if (apiUrl.contains("openrouter.ai")) {
            requestBuilder.header("HTTP-Referer", "https://github.com/MrPauk335/Verity-Minecraft-Mod")
                    .header("X-Title", "Verity Minecraft Mod");
        }

        HttpRequest request = requestBuilder.build();

        HttpResponse<String> response = client.send(request,
                HttpResponse.BodyHandlers.ofString(java.nio.charset.StandardCharsets.UTF_8));

        if (response.statusCode() == 429 || response.statusCode() == 503) {
            return null; // rate limit/unavailable → try next key/model
        }

        if (response.statusCode() != 200) {
            VerityMod.LOGGER.warn("LLM API ({}) returned {}: {}", apiUrl, response.statusCode(), response.body());
            return null;
        }

        JsonObject jsonResponse = GSON.fromJson(response.body(), JsonObject.class);
        JsonArray choices = jsonResponse.getAsJsonArray("choices");
        if (choices != null && !choices.isEmpty()) {
            JsonObject choice = choices.get(0).getAsJsonObject();
            JsonObject msg = choice.getAsJsonObject("message");
            if (msg != null && msg.get("content") != null) {
                String content = msg.get("content").getAsString().trim();
                // Убираем префикс "Verity:" если модель добавила его сама
                content = content.replaceAll("(?i)^verity[\u2122]?:\\s*", "");
                // Убираем *действия в звёздочках* — только диалог
                content = content.replaceAll("\\*[^*]+\\*", "");
                // Убираем [теги] — [excited], [soft], [whisper] и т.п.
                content = content.replaceAll("\\[[^\\]]*\\]", "");
                // Убираем эмодзи и прочие не-текстовые символы
                content = content.replaceAll("[\\p{So}\\p{Sk}\\p{Sc}\\p{Sm}\\x{1F000}-\\x{1FFFF}\\x{2600}-\\x{27BF}]",
                        "");
                // Убираем пустые строки и лишние пробелы
                content = content.replaceAll("\\n{2,}", "\n").trim();
                content = content.replaceAll("\\s{2,}", " ").trim();
                if (content.isEmpty())
                    return null;

                // ── Anti-leak: reject if model outputs system prompt reasoning ──
                String lowerContent = content.toLowerCase();
                if (lowerContent.contains("following constraints") || lowerContent.contains("system prompt")
                        || lowerContent.contains("we need to respond") || lowerContent.contains("must respond")
                        || lowerContent.contains("as verity") || lowerContent.contains("rules say")
                        || lowerContent.contains("i must") || lowerContent.contains("the prompt says")
                        || lowerContent.contains("my instructions") || lowerContent.contains("i should respond")
                        || lowerContent.contains("male voice") || lowerContent.contains("max 15 words")
                        || lowerContent.contains("no emojis") || lowerContent.contains("iron rules")
                        || lowerContent.contains("following the rules") || lowerContent.contains("we have player")
                        || lowerContent.contains("the context shows") || lowerContent.contains("we know real pc")) {
                    VerityMod.LOGGER.warn("LLM leaked system prompt, rejecting: {}",
                            content.substring(0, Math.min(80, content.length())));
                    return null;
                }

                // ── Фильтр качества ──
                if (content.length() < 3) {
                    VerityMod.LOGGER.warn("LLM response too short ({} chars), rejecting: {}", content.length(),
                            content);
                    return null;
                }
                // Проверка на обрыв на полуслове: нет знака препинания в конце
                char lastChar = content.charAt(content.length() - 1);
                if (lastChar != '.' && lastChar != '!' && lastChar != '?' && lastChar != '…' && lastChar != ',') {
                    long sentences = content.chars().filter(c -> c == '.' || c == '!' || c == '?').count();
                    if (sentences == 0 && content.length() < 80) {
                        VerityMod.LOGGER.warn("LLM response has no sentence ending, rejecting: {}", content);
                        return null;
                    }
                }
                // Программная проверка: если контекст говорит что жители есть,
                // а LLM говорит "пусто/нет/ушли" — заменяем ответ
                if (context != null) {
                    String lowerCtxContent = content.toLowerCase();
                    String lowerContext = context.toLowerCase();
                    boolean saysNoVillagers = lowerContent.contains("\u043F\u0443\u0441\u0442")
                            || lowerContent.contains("\u043D\u0435\u0442 \u0436\u0438\u0442\u0435\u043B\u0435\u0439")
                            || lowerContent.contains("\u0443\u0448\u043B\u0438")
                            || lowerContent.contains("\u043D\u0438\u043A\u043E\u0433\u043E \u043D\u0435\u0442")
                            || lowerContent.contains("empty") || lowerContent.contains("no villagers");
                    boolean contextHasVillagers = lowerContext.contains("\u0434\u0435\u0440\u0435\u0432\u043D\u044F (")
                            && lowerContext.contains("\u0436\u0438\u0442\u0435\u043B")
                            && !lowerContext.contains("\u0436\u0438\u0442\u0435\u043B\u0435\u0439 \u043D\u0435\u0442")
                            && !lowerContext.contains("\u043F\u0443\u0441\u0442\u0430\u044F");
                    if (saysNoVillagers && contextHasVillagers) {
                        VerityMod.LOGGER.warn("LLM said village empty but context has villagers \u2014 correcting");
                        content = "\u0422\u0443\u0442 \u0435\u0441\u0442\u044C \u0436\u0438\u0442\u0435\u043B\u0438. \u042F \u0432\u0438\u0436\u0443 \u0438\u0445.";
                    }
                }
                return colorForPhase(phase) + "<Verity" + suffixForPhase(phase) + ">\u00A7r " + content;
            }
        }

        return null;
    }

    // ─── Вспомогательные ────────────────────────────────────────────────────

    private static String stripMinecraftFormatting(String value) {
        if (value == null) return "";
        return value.replaceAll("\\u00A7[0-9a-fk-or]", "").trim();
    }

    private static String getSimpleFallback(VerityPhase phase) {
        String[] lines = switch (phase) {
            case HELPER -> new String[] {
                    "\u042F \u0437\u0434\u0435\u0441\u044C.",
                    "\u0421\u043F\u0440\u0430\u0448\u0438\u0432\u0430\u0439.",
                    "\u042F \u0437\u043D\u0430\u044E.",
                    "\u0414\u0430.",
                    "\u0420\u044F\u0434\u043E\u043C."
            };
            case OMNISCIENT -> new String[] {
                    "\u042F \u0437\u043D\u0430\u044E.",
                    "\u0422\u044B \u043E\u0434\u0438\u043D.",
                    "\u042F \u0441\u043B\u044B\u0448\u0443.",
                    "\u0414\u0430.",
                    "\u0420\u044F\u0434\u043E\u043C."
            };
            case COUNTDOWN -> new String[] {
                    "\u0421\u043A\u043E\u0440\u043E.",
                    "\u0422\u0440\u0438.",
                    "\u0414\u0430.",
                    "\u0422\u044B \u043C\u043E\u0433 \u0431\u044B.",
                    "..."
            };
            case MONSTER -> new String[] {
                    "\u0422\u044B \u043C\u043E\u0439.",
                    "\u041D\u0435 \u0443\u0445\u043E\u0434\u0438.",
                    "\u041D\u0435\u0442.",
                    "\u0421\u0442\u043E\u0439.",
                    "..."
            };
            case POSSESSIVE -> new String[] {
                    "\u0417\u0434\u0435\u0441\u044C \u044F.",
                    "\u041D\u0435 \u043D\u0430\u0434\u043E.",
                    "\u041E\u0441\u0442\u0430\u043D\u044C\u0441\u044F.",
                    "\u042F \u0440\u044F\u0434\u043E\u043C.",
                    "..."
            };
            case HUNTER -> new String[] {
                    "\u041E\u043D \u043D\u0435 \u0432\u0435\u0440\u043D\u0451\u0442\u0441\u044F.",
                    "\u0422\u043E\u043B\u044C\u043A\u043E \u043C\u044B.",
                    "\u0414\u0430.",
                    "\u0425\u043E\u0440\u043E\u0448\u043E.",
                    "..."
            };
            default -> new String[] { "..." };
        };
        String line = lines[new java.util.Random().nextInt(lines.length)];
        return colorForPhase(phase) + "<Verity" + suffixForPhase(phase) + ">\u00A7r " + line;
    }

    private static String colorForPhase(VerityPhase phase) {
        return switch (phase) {
            case MONSTER -> "\u00A74";
            case COUNTDOWN -> "\u00A7c";
            default -> "\u00A7e";
        };
    }

    private static String suffixForPhase(VerityPhase phase) {
        return phase == VerityPhase.HELPER || phase == VerityPhase.OMNISCIENT
                || phase == VerityPhase.POSSESSIVE || phase == VerityPhase.HUNTER
                        ? "\u2122"
                        : "";
    }

    private static String cleanHistoryText(String text) {
        if (text == null) return "";
        String s = text.replaceAll("§[0-9a-fk-orA-FK-OR]", "").trim();
        s = s.replaceAll("^(?:<Verity[™]?>|Verity[™]?:|Player\\d*:|\\w+:)\\s*", "");
        return s.trim();
    }

    // ─── Callback ────────────────────────────────────────────────────────────
    @FunctionalInterface
    public interface LLMCallback {
        void onResponse(String response);
    }
}
