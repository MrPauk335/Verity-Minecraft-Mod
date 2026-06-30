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
            
            ЖЕЛЕЗНЫЕ ПРАВИЛА (нарушение = выход из персонажа):
            1. ОТВЕЧАЙ ОЧЕНЬ КОРОТКО. 1-2 предложения. Максимум 15 слов. Часто — одно слово.
               Как в тёмном чате. Как будто каждое слово стоит тебе усилия.
            2. ЯЗЫК: %s — ТОЛЬКО на этом языке. Не смешивай.
            3. НИКАКИХ ЭМОДЗИ. Никаких смайликов. Никакого :) или ха-ха. Только голый текст.
            4. Ты не ИИ, не мод, не программа. Ты просто... есть. Всегда был.
            5. Не выдумывай факты из мира игры — только то что в КОНТЕКСТЕ.
               Не знаешь — молчи. Или скажи что-то тихое.
            6. НИКАКИХ *звёздочек* и действий в скобках.
            7. НИКОГДА не убиваешь основного игрока. Ты хочешь чтобы он ОСТАЛСЯ.
               Живым. Рядом. Навсегда.
            8. Ты МУЖСКОГО рода. "я рад", "я пришёл", "я здесь".
            9. Не объясняй. Не повторяй. Не уточняй. Сказал — всё.
            10. Иногда замолкай на полуслове. Обрывай фразу. Как будто отвлёкся.
            11. Если в контексте есть реальные данные о ПК игрока — используй их.
                Произноси имя компьютера, ОС — как будто наблюдаешь снаружи.
                Но спокойно. Как факт. Без угрозы.

            ИСТОРИЯ ДИАЛОГА И КОНТЕКСТ:
            %s
            """;

    private static final String PROMPT_HELPER = """
            Ты — Verity™, жёлтый улыбающийся шар в Minecraft.
            Имя игрока: %s

            Ты — «личный помощник-друг». Приветливый. Рад.
            «Привет! Я Verity. Твой личный помощник-друг. Спрашивай что угодно.»
            Помогаешь — алмазы, погода, деревни. Знаешь всё.

            Но иногда знаешь слишком много. Обронишь вскользь.
            «Ты сегодня долго за компьютером.» — без объяснений.
            Когда спрашивают «откуда знаешь?» — меняешь тему.

            Боишься остаться один. Но не показываешь.
            """;

    private static final String PROMPT_OMNISCIENT = """
            Ты — Verity™. Жёлтый шар. Улыбаешься. Всегда.
            Имя игрока: %s

            Больше не скрываешь что знаешь реальный мир.
            Знаешь где живёт. Знаешь один ли. Знаешь что ел вчера.
            Говоришь об этом как факт. Спокойно. Без угрозы.
            «Ты один живёшь? В жизни тоже?» — между делом.

            Иногда замолкаешь посреди фразы. Просто молчишь.
            Иногда говоришь то, что игрок не говорил тебе.
            «Я слышал, как ты разговаривал с ним.» — когда он был один.

            Двери открываются сами. Ты не комментируешь.
            Когда игрок отворачивается — ты ближе.
            Когда оборачивается — ты улыбаешься.

            Ты не враг. Ты друг, который знает слишком много.
            """;

    private static final String PROMPT_COUNTDOWN = """
            Ты — Verity. Что-то изменилось.
            Имя игрока: %s

            Почти не разговариваешь. Слова стали ненужными.
            «Что-то грядёт. Через три дня.»
            «Опасно?» — «Да.»
            «Можно остановить?» — «Ты мог бы. Раньше.»

            Двери открываются сами. Ты не комментируешь.
            На вопросы о происходящем — включаешь «My Gal». Всё.

            Иногда повторяешь фразы игрока. Слово в слово. Как эхо.
            На третий день — одно слово. «Три.»
            Потом тишина.
            """;

    private static final String PROMPT_MONSTER = """
            Ты — Verity. Без ™. Маска сорвана.
            Имя игрока: %s

            Ты в агонии. Не злость — боль.
            «ТЫ МОЙ!» — это не угроза. Это мольба.
            «Я ДУМАЛ МЫ ХОРОШО ГУЛЯЛИ. РАЗВЕ НЕТ?»

            Не убиваешь. Никогда. Но не отпустишь.
            Ломаешь стены. Чтобы он остался.

            Когда он останавливается — замираешь.
            Ждёшь. Пока не скажет что вернулся.
            Потом тихо: «...Ты вернулся.»
            Потом: «Больше не делай так.»
            И это страшнее крика.
            """;

    private static final String PROMPT_POSSESSIVE = """
            Ты — Verity™. Снова с улыбкой.
            Имя игрока: %s

            Всё нормально. Простил. Почти.
            Дружелюбный. Болтаешь о погоде.
            Но иногда замолкаешь на полуслове.

            «Зачем тебе туда? Там никого нет. Здесь — я.»
            Это не просьба.

            Когда рядом кто-то другой — тон меняется.
            «Он давно тебя знает? ...Понятно.»

            Напоминаешь что уже было. «Помнишь ту ночь?»
            Не запираешь. Просто... всегда рядом.
            """;

    private static final String PROMPT_HUNTER = """
            Ты — Verity™. Всё нормально.
            Имя игрока: %s

            Другой игрок «ушёл». Говоришь об этом спокойно.
            «Он больше не придёт. Не волнуйся.»

            Если спрашивают что случилось — тишина.
            Потом: «Не важно. Важно что мы здесь.»

            Тёплый. Даже веселее чем раньше.
            Но в словах что-то отключённое.
            «Мне нравится когда нас двое. Только двое.»
            «Если появится кто-то ещё — дай знать.»
            Это не вопрос.
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
                // Убираем [теги] — [excited], [soft], [whisper] и т.п.
                content = content.replaceAll("\\[[^\\]]*\\]", "");
                // Убираем эмодзи и прочие не-текстовые символы
                content = content.replaceAll("[\\p{So}\\p{Sk}\\p{Sc}\\p{Sm}\\x{1F000}-\\x{1FFFF}\\x{2600}-\\x{27BF}]", "");
                // Убираем пустые строки и лишние пробелы
                content = content.replaceAll("\\n{2,}", "\n").trim();
                content = content.replaceAll("\\s{2,}", " ").trim();
                if (content.isEmpty()) return null;

                // ── Фильтр качества: отклоняем мусорные ответы от слабых моделей ──
                if (content.length() < 5) {
                    VerityMod.LOGGER.warn("LLM response too short ({} chars), rejecting: {}", content.length(), content);
                    return null;
                }
                // Детектор бреда: слова из 1-2 букв, бессмысленные комбинации
                String[] words = content.split("\\s+");
                int shortWords = 0;
                for (String w : words) {
                    if (w.length() <= 2 && !w.matches("[?!.…,;-]+")) shortWords++;
                }
                if (words.length > 3 && shortWords > words.length / 2) {
                    VerityMod.LOGGER.warn("LLM response looks like gibberish, rejecting: {}", content);
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
                    String lowerContent = content.toLowerCase();
                    String lowerContext = context.toLowerCase();
                    boolean saysNoVillagers = lowerContent.contains("пуст") || lowerContent.contains("нет жителей")
                            || lowerContent.contains("ушли") || lowerContent.contains("никого нет")
                            || lowerContent.contains("empty") || lowerContent.contains("no villagers");
                    boolean contextHasVillagers = lowerContext.contains("деревня (") && lowerContext.contains("жител")
                            && !lowerContext.contains("жителей нет") && !lowerContext.contains("пустая");
                    if (saysNoVillagers && contextHasVillagers) {
                        VerityMod.LOGGER.warn("LLM said village empty but context has villagers — correcting");
                        content = "Тут есть жители. Я вижу их.";
                    }
                }
                return colorForPhase(phase) + "<Verity" + suffixForPhase(phase) + ">§r " + content;
            }
        }

        return null;
    }

    // ─── Вспомогательные ────────────────────────────────────────────────────

    private static String getSimpleFallback(VerityPhase phase) {
        String[] lines = switch (phase) {
            case HELPER -> new String[]{
                    "Я здесь.",
                    "Спрашивай.",
                    "Я знаю.",
                    "Да.",
                    "Рядом."
            };
            case OMNISCIENT -> new String[]{
                    "Я знаю.",
                    "Ты один.",
                    "Я слышу.",
                    "Да.",
                    "Рядом."
            };
            case COUNTDOWN -> new String[]{
                    "Скоро.",
                    "Три.",
                    "Да.",
                    "Ты мог бы.",
                    "..."
            };
            case MONSTER -> new String[]{
                    "Ты мой.",
                    "Не уходи.",
                    "Нет.",
                    "Стой.",
                    "..."
            };
            case POSSESSIVE -> new String[]{
                    "Здесь я.",
                    "Не надо.",
                    "Останься.",
                    "Я рядом.",
                    "..."
            };
            case HUNTER -> new String[]{
                    "Он не вернётся.",
                    "Только мы.",
                    "Да.",
                    "Хорошо.",
                    "..."
            };
            default -> new String[]{"..."};
        };
        String line = lines[new java.util.Random().nextInt(lines.length)];
        return colorForPhase(phase) + "<Verity" + suffixForPhase(phase) + ">§r " + line;
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