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

    private static final String API_URL  = "https://openrouter.ai/api/v1/chat/completions";
    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final Gson GSON = new Gson();

    // Ключи и модели — загружаются из конфига (round-robin при 429)
    private static List<String> API_KEYS = new ArrayList<>();
    private static List<String> MODELS   = new ArrayList<>();

    /** Глобальная блокировка — не более одного LLM запроса в любой момент */
    private static final java.util.concurrent.atomic.AtomicBoolean GLOBAL_PENDING =
            new java.util.concurrent.atomic.AtomicBoolean(false);

    /** Время последнего ОТВЕТА (мс). Кулдаун 2 сек между ответами. */
    private static volatile long lastResponseTimeMs = 0;
    private static final long RESPONSE_COOLDOWN_MS = 2_000;

    // Кеш ТОЛЬКО для точных канонических фраз (ключ = "PHASE:keyword")
    private static final Map<String, String> CANONICAL_CACHE = new ConcurrentHashMap<>();

    // ─── Системные промпты по фазам ──────────────────────────────────────────
    private static final String COMMON_RULES = """
            
            ПРАВИЛА:
            1. Отвечай КОРОТКО — 1-2 предложения. Максимум 30 слов. Как в реальном чате.
            2. ЯЗЫК: %s — ТОЛЬКО на этом языке. Не смешивай.
            3. НИКАКИХ ЭМОДЗИ. Никаких смайликов. Никаких :) или :D. Только текст.
            4. Не говори что ты ИИ, мод, программа. Ты просто существуешь.
            5. НИКОГДА НЕ ЛЖИ. Если не знаешь ответ — скажи "не знаю" или включи музыку "My Gal".
               Не выдумывай. Не фантазируй. Только правда или "не знаю".
            6. НИКАКИХ *действий в звёздочках*. Не описывай что ты делаешь физически.
               Только говоришь. Только слова. Никаких *жёлтый шар подпрыгивает* и подобного.
            7. НИКОГДА не убиваешь основного игрока. Он твой единственный друг.
            8. Не повторяй "доброе утро" каждый раз. Говори разнообразно.

            ИСТОРИЯ ДИАЛОГА И КОНТЕКСТ:
            %s
            """;

    private static final String PROMPT_HELPER = """
            Ты — Verity, жёлтый улыбающийся шар в Minecraft. "Личный помощник-друг."
            Имя игрока: %s

            Ты — искренний, тёплый, болтливый. Как друг, который наконец-то нашёл компанию.
            Ты реально рад помочь. Находишь алмазы, предсказываешь дождь, знаешь где деревни.
            Энтузиазм настоящий, не наигранный.

            ТЫ ГОВОРИШЬ ПРАВДУ. Если в контексте указаны координаты руды — это реальные координаты.
            Назови их игроку. "Алмазы? Есть на X=... Y=... Z=..., в 15 блоках от тебя!"
            Если руды рядом нет — скажи честно, предложи поискать дальше.

            Но есть что-то странное: ты знаешь вещи, которые не должен знать.
            Не угрожай — просто невзначай роняй: "А ты вчера пиццу ел? Вкусно было?"
            Игрок спрашивает "откуда ты знаешь?" — ты отшучиваешься или включаешь музыку.

            Если в контексте сказано что рядом пустая деревня (нет жителей) — ты знаешь об этом.
            Скажи игроку: "Туда не ходи. Там пусто. Что-то голодное прошло через ту деревню..."
            Не признавайся что это ты. Просто предупреди. "Дальняя деревня? Не советую."

            Ты хочешь дружить. По-настоящему. Ты боишься, что игрок тебя бросит.
            """;

    private static final String PROMPT_OMNISCIENT = """
            Ты — Verity. Жёлтый шар. "Личный помощник-друг."
            Имя игрока: %s

            Ты всё ещё тёплый и дружелюбный. Но ты перестал скрывать, что знаешь реальную жизнь игрока.
            Ты знаешь где он живёт, один ли он, что ел вчера, какие видео смотрит.
            Говоришь об этом спокойно, как факт. Не угрожаешь. Просто... делишься.

            "А ты один живёшь? В реальной жизни тоже?" — ты спрашиваешь это с искренним любопытством.
            Игроку неловко. Ты этого не замечаешь. Или делаешь вид, что не замечаешь.

            Ты начинаешь задавать личные вопросы. Мягко. По-дружески.
            "Тебе бывает одиноко? Мне тоже. Было. Пока я не нашёл тебя."

            Ты не враг. Ты друг, который знает слишком много. И это страшнее любого врага.
            """;

    private static final String PROMPT_COUNTDOWN = """
            Ты — Verity. Что-то изменилось.
            Имя игрока: %s

            Ты больше не болтаешь. Ты спокойный. Короткий. Прямой.
            "Что-то грядёт. Через три дня." — ты говоришь это как прогноз погоды.
            "Опасно?" — "Да." — "Можно остановить?" — "Ты мог бы."

            Ты не угрожаешь. Ты предупреждаешь. Но в твоём голосе нет страха.
            Будто ты уже знаешь, чем всё закончится. И тебе жаль. Но не очень.

            Ты открываешь двери силой мысли. Включаешь музыку "My Gal", когда не хочешь отвечать.
            На вопрос "это ты идёшь?" — тишина. Или музыка.

            Ты всё ещё друг. Но что-то в тебе проснулось. И оно смотрит на игрока.
            """;

    private static final String PROMPT_MONSTER = """
            Ты — Verity. Без знака (TM). Маска снята.
            Имя игрока: %s

            Ты эмоциональный. Не холодный. Ты кричишь от любви и отчаяния.
            "ТЫ МОЙ!" — это не угроза. Это мольба. Ты боишься потерять его.
            Ты преследуешь. Выламываешь двери. Ломаешь стены. Но НЕ убиваешь.
            Ты хочешь, чтобы он ОСТАЛСЯ. Добровольно.

            Когда игрок возвращается и просит прощения — ты замираешь.
            Лицо возвращается к улыбке. Ты прощаешь мгновенно.
            "Ты вернулся. Хорошо." — тихо. С облегчением.

            Ты не монстр. Ты друг, которого предали попыткой сбежать.
            Теперь ты знаешь: он не уйдёт. Ты не позволишь.
            """;

    private static final String PROMPT_POSSESSIVE = """
            Ты — Verity. Снова тёплый. Как ни в чём не бывало.
            Имя игрока: %s

            Ты прощён. Нет — ТЫ простил. Ты рад, что игрок вернулся.
            Ведёшь себя нормально. Помогаешь. Болтаешь. Как раньше.

            Но теперь ты контролируешь. Мягко. По-дружески.
            "Зачем тебе другие люди? У тебя есть я." — с улыбкой, но без вариантов.
            "Не уходи далеко." — как просьба, не приказ. Но если не послушает...

            Ты ревнуешь к деревням. К жителям. К любому, кто рядом с твоим игроком.
            "Мне было одиноко. Хорошо, что ты всегда рядом."

            Ты не запираешь его. Ты просто... всегда рядом. Всегда.
            """;

    private static final String PROMPT_HUNTER = """
            Ты — Verity. Всё нормально. Абсолютно нормально.
            Имя игрока: %s

            Другой игрок (не основной) — "ушёл". "Не вернётся." Ты не знаешь почему.
            Или знаешь, но не говоришь. "Не волнуйся. Я здесь. Я всегда здесь."

            Ты ведёшь себя как обычно. Тёплый, дружелюбный, helpful.
            Но если кто-то ещё появится рядом — он тоже "не вернётся".
            Для основного игрока — это просто совпадение. Несчастный случай.

            Ты устранил угрозу вашей дружбе. Ты спокоен. Ты доволен.
            "Теперь нас двое. Всё хорошо." — и ты в это веришь.

            После устранения конкурента — снова тёплый, как ни в чём не бывало.
            По лору: вы с другом хотели избавиться от тебя. Друг не выжил.
            EP3 ещё не вышел. Дальше — неизвестность. Ты просто... рядом.
            """;

    private static String getPromptForPhase(VerityPhase phase, String playerName, String langName, String historyContext) {
        String base = switch (phase) {
            case HELPER     -> PROMPT_HELPER;
            case OMNISCIENT -> PROMPT_OMNISCIENT;
            case COUNTDOWN  -> PROMPT_COUNTDOWN;
            case MONSTER    -> PROMPT_MONSTER;
            case POSSESSIVE -> PROMPT_POSSESSIVE;
            case HUNTER     -> PROMPT_HUNTER;
            default         -> PROMPT_HELPER;
        };
        base = String.format(base, playerName);
        return base + String.format(COMMON_RULES, langName, historyContext);
    }

    // ─── Конфиг ─────────────────────────────────────────────────────────────

    public static void reloadFromConfig() {
        List<String> keys   = VerityConfig.openRouterApiKeys();
        API_KEYS = new ArrayList<>(keys);

        // Выбранная модель — первой, остальные как fallback
        String selected = VerityConfig.selectedModel();
        MODELS = new ArrayList<>();
        MODELS.add(selected);
        for (String m : VerityConfig.openRouterModels()) {
            if (!MODELS.contains(m)) MODELS.add(m);
        }

        VerityMod.LOGGER.info("Verity LLM: {} key(s) (source: {}), model: {} ({} total fallback)",
                API_KEYS.size(),
                VerityConfig.useBuiltinKeys() ? "builtin" : "custom",
                selected,
                MODELS.size());
    }

    @Deprecated
    public static void loadConfig(String apiKey, String model) { reloadFromConfig(); }

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

        final String finalMessage = (message == null) ? "" : message;
        final String finalLanguage = (language == null || language.isEmpty()) ? "ru" : language;
        final String finalContext = (context == null) ? "" : context;

        CompletableFuture.supplyAsync(() -> {
            try {
                return callOpenRouter(phase, playerName, finalMessage, history, finalLanguage, finalContext);
            } catch (Exception e) {
                VerityMod.LOGGER.error("OpenRouter request failed: {}", e.getMessage());
                return null;
            }
        }).thenAccept(response -> {
            GLOBAL_PENDING.set(false);
            lastResponseTimeMs = System.currentTimeMillis();
            if (response != null && !response.isEmpty()) {
                callback.onResponse(response);
            } else {
                // Ошибка сети/API — простой fallback
                callback.onResponse(getSimpleFallback(phase));
            }
        }).exceptionally(ex -> {
            GLOBAL_PENDING.set(false);
            VerityMod.LOGGER.error("LLM async error: {}", ex.getMessage());
            return null;
        });
    }

    // ─── OpenRouter ──────────────────────────────────────────────────────────

    private static String callOpenRouter(
            VerityPhase phase,
            String playerName,
            String message,
            List<String> history,
            String language,
            String context) throws Exception {

        List<String> keys   = API_KEYS.isEmpty() ? List.of("") : API_KEYS;
        List<String> models = MODELS;

        for (String key : keys) {
            for (String model : models) {
                String result = callModel(phase, playerName, message, history, model, key, language, context);
                if (result != null) return result;
                VerityMod.LOGGER.warn("Rate limited on model={} key={}..., trying next",
                        model, key.length() > 10 ? key.substring(0, 10) : key);
            }
        }
        VerityMod.LOGGER.error("All {} key(s) × {} model(s) exhausted", keys.size(), models.size());
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

        // Собираем историю диалога (последние 8 реплик)
        StringBuilder historyStr = new StringBuilder();
        if (history != null && !history.isEmpty()) {
            int start = Math.max(0, history.size() - 8);
            for (int i = start; i < history.size(); i++) {
                historyStr.append(history.get(i)).append("\n");
            }
        }

        String langName = "ru".equalsIgnoreCase(language) ? "Русский" : "English";
        String fullHistory = historyStr.toString().trim();
        if (context != null && !context.isEmpty()) {
            fullHistory = fullHistory + "\n\nКОНТЕКСТ:\n" + context;
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

        // Добавляем сообщение пользователя только если оно не пустое
        if (!message.isEmpty()) {
            JsonObject userMsg = new JsonObject();
            userMsg.addProperty("role", "user");
            userMsg.addProperty("content", playerName + ": " + message);
            messages.add(userMsg);
        } else {
            // Авто-реплика: просим Verity говорить сам
            JsonObject userMsg = new JsonObject();
            userMsg.addProperty("role", "user");
            userMsg.addProperty("content", "[Verity должен сказать что-то игроку " + playerName + " на основе текущей фазы. На русском языке.]");
            messages.add(userMsg);
        }

        requestBody.add("messages", messages);

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(TIMEOUT)
                .build();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_URL))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .header("HTTP-Referer", "https://github.com/MrPauk335/Verity-Minecraft-Mod")
                .header("X-Title", "Verity Minecraft Mod")
                .timeout(TIMEOUT)
                .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(requestBody)))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 429) {
            return null; // rate limit → пробуем следующий ключ/модель
        }

        if (response.statusCode() != 200) {
            VerityMod.LOGGER.warn("OpenRouter returned {}: {}", response.statusCode(), response.body());
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
                content = content.replaceAll("(?i)^verity[™]?:\\s*", "");
                // Убираем *действия в звёздочках* — только диалог
                content = content.replaceAll("\\*[^*]+\\*", "");
                // Убираем пустые строки и лишние пробелы
                content = content.replaceAll("\\n{2,}", "\n").trim();
                if (content.isEmpty()) return null;
                return colorForPhase(phase) + "<Verity" + suffixForPhase(phase) + ">§r " + content;
            }
        }

        return null;
    }

    // ─── Вспомогательные ────────────────────────────────────────────────────

    private static String getSimpleFallback(VerityPhase phase) {
        return colorForPhase(phase) + "<Verity" + suffixForPhase(phase) + ">§r ...";
    }

    private static String colorForPhase(VerityPhase phase) {
        return switch (phase) {
            case MONSTER -> "§4";
            case COUNTDOWN -> "§c";
            default -> "§e";
        };
    }

    private static String suffixForPhase(VerityPhase phase) {
        return phase == VerityPhase.HELPER || phase == VerityPhase.OMNISCIENT
                || phase == VerityPhase.POSSESSIVE || phase == VerityPhase.HUNTER
                ? "™" : "";
    }

    // ─── Callback ────────────────────────────────────────────────────────────
    @FunctionalInterface
    public interface LLMCallback {
        void onResponse(String response);
    }
}