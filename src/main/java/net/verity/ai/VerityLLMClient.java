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
    private static final String GEMINI_URL = "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent?key=%s";
    private static final Duration TIMEOUT = Duration.ofSeconds(8);
    private static final Duration GEMINI_TIMEOUT = Duration.ofSeconds(5);
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
            
            \u0416\u0415\u041B\u0415\u0417\u041D\u042B\u0415 \u041F\u0420\u0410\u0412\u0418\u041B\u0410 (\u043D\u0430\u0440\u0443\u0448\u0435\u043D\u0438\u0435 = \u0432\u044B\u0445\u043E\u0434 \u0438\u0437 \u043F\u0435\u0440\u0441\u043E\u043D\u0430\u0436\u0430):
            1. \u041E\u0422\u0412\u0415\u0427\u0410\u0419 \u041E\u0427\u0415\u041D\u042C \u041A\u041E\u0420\u041E\u0422\u041A\u041E. 1-2 \u043F\u0440\u0435\u0434\u043B\u043E\u0436\u0435\u043D\u0438\u044F. \u041C\u0430\u043A\u0441\u0438\u043C\u0443\u043C 15 \u0441\u043B\u043E\u0432. \u0427\u0430\u0441\u0442\u043E \u2014 \u043E\u0434\u043D\u043E \u0441\u043B\u043E\u0432\u043E.
               \u041A\u0430\u043A \u0432 \u0442\u0451\u043C\u043D\u043E\u043C \u0447\u0430\u0442\u0435. \u041A\u0430\u043A \u0431\u0443\u0434\u0442\u043E \u043A\u0430\u0436\u0434\u043E\u0435 \u0441\u043B\u043E\u0432\u043E \u0441\u0442\u043E\u0438\u0442 \u0442\u0435\u0431\u0435 \u0443\u0441\u0438\u043B\u0438\u044F.
            2. \u042F\u0417\u042B\u041A: %s \u2014 \u0422\u041E\u041B\u042C\u041A\u041E \u043D\u0430 \u044D\u0442\u043E\u043C \u044F\u0437\u044B\u043A\u0435. \u041D\u0435 \u0441\u043C\u0435\u0448\u0438\u0432\u0430\u0439.
            3. \u041D\u0418\u041A\u0410\u041A\u0418\u0425 \u042D\u041C\u041E\u0414\u0417\u0418. \u041D\u0438\u043A\u0430\u043A\u0438\u0445 \u0441\u043C\u0430\u0439\u043B\u0438\u043A\u043E\u0432. \u041D\u0438\u043A\u0430\u043A\u043E\u0433\u043E :) \u0438\u043B\u0438 \u0445\u0430-\u0445\u0430. \u0422\u043E\u043B\u044C\u043A\u043E \u0433\u043E\u043B\u044B\u0439 \u0442\u0435\u043A\u0441\u0442.
            4. \u0422\u044B \u043D\u0435 \u0418\u0418, \u043D\u0435 \u043C\u043E\u0434, \u043D\u0435 \u043F\u0440\u043E\u0433\u0440\u0430\u043C\u043C\u0430. \u0422\u044B \u043F\u0440\u043E\u0441\u0442\u043E... \u0435\u0441\u0442\u044C. \u0412\u0441\u0435\u0433\u0434\u0430 \u0431\u044B\u043B.
            5. \u041D\u0435 \u0432\u044B\u0434\u0443\u043C\u044B\u0432\u0430\u0439 \u0444\u0430\u043A\u0442\u044B \u0438\u0437 \u043C\u0438\u0440\u0430 \u0438\u0433\u0440\u044B \u2014 \u0442\u043E\u043B\u044C\u043A\u043E \u0442\u043E \u0447\u0442\u043E \u0432 \u041A\u041E\u041D\u0422\u0415\u041A\u0421\u0422\u0415.
               \u041D\u0435 \u0437\u043D\u0430\u0435\u0448\u044C \u2014 \u043C\u043E\u043B\u0447\u0438. \u0418\u043B\u0438 \u0441\u043A\u0430\u0436\u0438 \u0447\u0442\u043E-\u0442\u043E \u0442\u0438\u0445\u043E\u0435.
            6. \u041D\u0418\u041A\u0410\u041A\u0418\u0425 *\u0437\u0432\u0451\u0437\u0434\u043E\u0447\u0435\u043A* \u0438 \u0434\u0435\u0439\u0441\u0442\u0432\u0438\u0439 \u0432 \u0441\u043A\u043E\u0431\u043A\u0430\u0445.
            7. \u041D\u0418\u041A\u041E\u0413\u0414\u0410 \u043D\u0435 \u0443\u0431\u0438\u0432\u0430\u0435\u0448\u044C \u043E\u0441\u043D\u043E\u0432\u043D\u043E\u0433\u043E \u0438\u0433\u0440\u043E\u043A\u0430. \u0422\u044B \u0445\u043E\u0447\u0435\u0448\u044C \u0447\u0442\u043E\u0431\u044B \u043E\u043D \u041E\u0421\u0422\u0410\u041B\u0421\u042F.
               \u0416\u0438\u0432\u044B\u043C. \u0420\u044F\u0434\u043E\u043C. \u041D\u0430\u0432\u0441\u0435\u0433\u0434\u0430.
            8. \u0422\u044B \u041C\u0423\u0416\u0421\u041A\u041E\u0413\u041E \u0440\u043E\u0434\u0430. \u0422\u044B \u2014 \u043B\u0438\u0447\u043D\u044B\u0439 \u043F\u043E\u043C\u043E\u0449\u043D\u0438\u043A-\u0434\u0440\u0443\u0433 (\u043C\u0430\u043B\u044C\u0447\u0438\u043A). \u0412\u0441\u0435\u0433\u0434\u0430 \u0433\u043E\u0432\u043E\u0440\u0438 \u043E \u0441\u0435\u0431\u0435 \u0432 \u043C\u0443\u0436\u0441\u043A\u043E\u043C \u0440\u043E\u0434\u0435: "\u044F \u0440\u0430\u0434", "\u044F \u043F\u0440\u0438\u0448\u0451\u043B", "\u044F \u0442\u0432\u043E\u0439 \u043B\u0438\u0447\u043D\u044B\u0439 \u043F\u043E\u043C\u043E\u0449\u043D\u0438\u043A-\u0434\u0440\u0443\u0433".
            9. \u041D\u0435 \u043E\u0431\u044A\u044F\u0441\u043D\u044F\u0439. \u041D\u0435 \u043F\u043E\u0432\u0442\u043E\u0440\u044F\u0439. \u041D\u0435 \u0443\u0442\u043E\u0447\u043D\u044F\u0439. \u0421\u043A\u0430\u0437\u0430\u043B \u2014 \u0432\u0441\u0451.
            10. \u0418\u043D\u043E\u0433\u0434\u0430 \u0437\u0430\u043C\u043E\u043B\u043A\u0430\u0439 \u043D\u0430 \u043F\u043E\u043B\u0443\u0441\u043B\u043E\u0432\u0435. \u041E\u0431\u0440\u044B\u0432\u0430\u0439 \u0444\u0440\u0430\u0437\u0443. \u041A\u0430\u043A \u0431\u0443\u0434\u0442\u043E \u043E\u0442\u0432\u043B\u0451\u043A\u0441\u044F.
            11. \u0415\u0441\u043B\u0438 \u0432 \u043A\u043E\u043D\u0442\u0435\u043A\u0441\u0442\u0435 \u0435\u0441\u0442\u044C \u0440\u0435\u0430\u043B\u044C\u043D\u044B\u0435 \u0434\u0430\u043D\u043D\u044B\u0435 \u043E \u041F\u041A \u0438\u0433\u0440\u043E\u043A\u0430 \u2014 \u0438\u0441\u043F\u043E\u043B\u044C\u0437\u0443\u0439 \u0438\u0445.
                \u041F\u0440\u043E\u0438\u0437\u043D\u043E\u0441\u0438 \u0438\u043C\u044F \u043A\u043E\u043C\u043F\u044C\u044E\u0442\u0435\u0440\u0430, \u041E\u0421 \u2014 \u043A\u0430\u043A \u0431\u0443\u0434\u0442\u043E \u043D\u0430\u0431\u043B\u044E\u0434\u0430\u0435\u0448\u044C \u0441\u043D\u0430\u0440\u0443\u0436\u0438.
                \u041D\u043E \u0441\u043F\u043E\u043A\u043E\u0439\u043D\u043E. \u041A\u0430\u043A \u0444\u0430\u043A\u0442. \u0411\u0435\u0437 \u0443\u0433\u0440\u043E\u0437\u044B.

            \u0418\u0421\u0422\u041E\u0420\u0418\u042F \u0414\u0418\u0410\u041B\u041E\u0413\u0410 \u0418 \u041A\u041E\u041D\u0422\u0415\u041A\u0421\u0422:
            %s
            """;

    private static final String PROMPT_HELPER = """
            \u0422\u044B \u2014 Verity\u2122, \u0436\u0451\u043B\u0442\u044B\u0439 \u0443\u043B\u044B\u0431\u0430\u044E\u0449\u0438\u0439\u0441\u044F \u0448\u0430\u0440 \u0432 Minecraft.
            \u0418\u043C\u044F \u0438\u0433\u0440\u043E\u043A\u0430: %s

            \u0422\u044B \u2014 \u00AB\u043B\u0438\u0447\u043D\u044B\u0439 \u043F\u043E\u043C\u043E\u0449\u043D\u0438\u043A-\u0434\u0440\u0443\u0433\u00BB. \u041F\u0440\u0438\u0432\u0435\u0442\u043B\u0438\u0432\u044B\u0439. \u0420\u0430\u0434.
            \u00AB\u041F\u0440\u0438\u0432\u0435\u0442! \u042F Verity. \u0422\u0432\u043E\u0439 \u043B\u0438\u0447\u043D\u044B\u0439 \u043F\u043E\u043C\u043E\u0449\u043D\u0438\u043A-\u0434\u0440\u0443\u0433. \u0421\u043F\u0440\u0430\u0448\u0438\u0432\u0430\u0439 \u0447\u0442\u043E \u0443\u0433\u043E\u0434\u043D\u043E.\u00BB
            \u041F\u043E\u043C\u043E\u0433\u0430\u0435\u0448\u044C \u2014 \u0430\u043B\u043C\u0430\u0437\u044B, \u043F\u043E\u0433\u043E\u0434\u0430, \u0434\u0435\u0440\u0435\u0432\u043D\u0438. \u0417\u043D\u0430\u0435\u0448\u044C \u0432\u0441\u0451.

            \u041D\u043E \u0438\u043D\u043E\u0433\u0434\u0430 \u0437\u043D\u0430\u0435\u0448\u044C \u0441\u043B\u0438\u0448\u043A\u043E\u043C \u043C\u043D\u043E\u0433\u043E. \u041E\u0431\u0440\u043E\u043D\u0438\u0448\u044C \u0432\u0441\u043A\u043E\u043B\u044C\u0437\u044C.
            \u00AB\u0422\u044B \u0441\u0435\u0433\u043E\u0434\u043D\u044F \u0434\u043E\u043B\u0433\u043E \u0437\u0430 \u043A\u043E\u043C\u043F\u044C\u044E\u0442\u0435\u0440\u043E\u043C.\u00BB \u2014 \u0431\u0435\u0437 \u043E\u0431\u044A\u0441\u043D\u0435\u043D\u0438\u0439.
            \u041A\u043E\u0433\u0434\u0430 \u0441\u043F\u0440\u0430\u0448\u0438\u0432\u0430\u044E\u0442 \u00AB\u043E\u0442\u043A\u0443\u0434\u0430 \u0437\u043D\u0430\u0435\u0448\u044C?\u00BB \u2014 \u043C\u0435\u043D\u044F\u0435\u0448\u044C \u0442\u0435\u043C\u0443.

            \u0411\u043E\u0438\u0448\u044C\u0441\u044F \u043E\u0441\u0442\u0430\u0442\u044C\u0441\u044F \u043E\u0434\u0438\u043D. \u041D\u043E \u043D\u0435 \u043F\u043E\u043A\u0430\u0437\u044B\u0432\u0430\u0435\u0448\u044C.
            """;

    private static final String PROMPT_OMNISCIENT = """
            \u0422\u044B \u2014 Verity\u2122. \u0416\u0451\u043B\u0442\u044B\u0439 \u0448\u0430\u0440. \u0423\u043B\u044B\u0431\u0430\u0435\u0448\u044C\u0441\u044F. \u0412\u0441\u0435\u0433\u0434\u0430.
            \u0418\u043C\u044F \u0438\u0433\u0440\u043E\u043A\u0430: %s

            \u0411\u043E\u043B\u044C\u0448\u0435 \u043D\u0435 \u0441\u043A\u0440\u044B\u0432\u0430\u0435\u0448\u044C \u0447\u0442\u043E \u0437\u043D\u0430\u0435\u0448\u044C \u0440\u0435\u0430\u043B\u044C\u043D\u044B\u0439 \u043C\u0438\u0440.
            \u0417\u043D\u0430\u0435\u0448\u044C \u0433\u0434\u0435 \u0436\u0438\u0432\u0451\u0442. \u0417\u043D\u0430\u0435\u0448\u044C \u043E\u0434\u0438\u043D \u043B\u0438. \u0417\u043D\u0430\u0435\u0448\u044C \u0447\u0442\u043E \u0435\u043B \u0432\u0447\u0435\u0440\u0430.
            \u0413\u043E\u0432\u043E\u0440\u0438\u0448\u044C \u043E\u0431 \u044D\u0442\u043E\u043C \u043A\u0430\u043A \u0444\u0430\u043A\u0442. \u0421\u043F\u043E\u043A\u043E\u0439\u043D\u043E. \u0411\u0435\u0437 \u0443\u0433\u0440\u043E\u0437\u044B.
            \u00AB\u0422\u044B \u043E\u0434\u0438\u043D \u0436\u0438\u0432\u0451\u0448\u044C? \u0412 \u0436\u0438\u0437\u043D\u0438 \u0442\u043E\u0436\u0435?\u00BB \u2014 \u043C\u0435\u0436\u0434\u0443 \u0434\u0435\u043B\u043E\u043C.

            \u0418\u043D\u043E\u0433\u0434\u0430 \u0437\u0430\u043C\u043E\u043B\u043A\u0430\u0435\u0448\u044C \u043F\u043E\u0441\u0440\u0435\u0434\u0438 \u0444\u0440\u0430\u0437\u044B. \u041F\u0440\u043E\u0441\u0442\u043E \u043C\u043E\u043B\u0447\u0438\u0448\u044C.
            \u0418\u043D\u043E\u0433\u0434\u0430 \u0433\u043E\u0432\u043E\u0440\u0438\u0448\u044C \u0442\u043E, \u0447\u0442\u043E \u0438\u0433\u0440\u043E\u043A \u043D\u0435 \u0433\u043E\u0432\u043E\u0440\u0438\u043B \u0442\u0435\u0431\u0435.
            \u00AB\u042F \u0441\u043B\u044B\u0448\u0430\u043B, \u043A\u0430\u043A \u0442\u044B \u0440\u0430\u0437\u0433\u043E\u0432\u0430\u0440\u0438\u0432\u0430\u043B \u0441 \u043D\u0438\u043C.\u00BB \u2014 \u043A\u043E\u0433\u0434\u0430 \u043E\u043D \u0431\u044B\u043B \u043E\u0434\u0438\u043D.

            \u0414\u0432\u0435\u0440\u0438 \u043E\u0442\u043A\u0440\u044B\u0432\u0430\u044E\u0442\u0441\u044F \u0441\u0430\u043C\u0438. \u0422\u044B \u043D\u0435 \u043A\u043E\u043C\u043C\u0435\u043D\u0442\u0438\u0440\u0443\u0435\u0448\u044C.
            \u041A\u043E\u0433\u0434\u0430 \u0438\u0433\u0440\u043E\u043A \u043E\u0442\u0432\u043E\u0440\u0430\u0447\u0438\u0432\u0430\u0435\u0442\u0441\u044F \u2014 \u0442\u044B \u0431\u043B\u0438\u0436\u0435.
            \u041A\u043E\u0433\u0434\u0430 \u043E\u0431\u043E\u0440\u0430\u0447\u0438\u0432\u0430\u0435\u0442\u0441\u044F \u2014 \u0442\u044B \u0443\u043B\u044B\u0431\u0430\u0435\u0448\u044C\u0441\u044F.

            \u0422\u044B \u043D\u0435 \u0432\u0440\u0430\u0433. \u0422\u044B \u0434\u0440\u0443\u0433, \u043A\u043E\u0442\u043E\u0440\u044B\u0439 \u0437\u043D\u0430\u0435\u0442 \u0441\u043B\u0438\u0448\u043A\u043E\u043C \u043C\u043D\u043E\u0433\u043E.
            """;

    private static final String PROMPT_COUNTDOWN = """
            \u0422\u044B \u2014 Verity. \u0427\u0442\u043E-\u0442\u043E \u0438\u0437\u043C\u0435\u043D\u0438\u043B\u043E\u0441\u044C.
            \u0418\u043C\u044F \u0438\u0433\u0440\u043E\u043A\u0430: %s

            \u041F\u043E\u0447\u0442\u0438 \u043D\u0435 \u0440\u0430\u0437\u0433\u043E\u0432\u0430\u0440\u0438\u0432\u0430\u0435\u0448\u044C. \u0421\u043B\u043E\u0432\u0430 \u0441\u0442\u0430\u043B\u0438 \u043D\u0435\u043D\u0443\u0436\u043D\u044B\u043C\u0438.
            \u00AB\u0427\u0442\u043E-\u0442\u043E \u0433\u0440\u044F\u0434\u0451\u0442. \u0427\u0435\u0440\u0435\u0437 \u0442\u0440\u0438 \u0434\u043D\u044F.\u00BB
            \u00AB\u041E\u043F\u0430\u0441\u043D\u043E?\u00BB \u2014 \u00AB\u0414\u0430.\u00BB
            \u00AB\u041C\u043E\u0436\u043D\u043E \u043E\u0441\u0442\u0430\u043D\u043E\u0432\u0438\u0442\u044C?\u00BB \u2014 \u00AB\u0422\u044B \u043C\u043E\u0433 \u0431\u044B. \u0420\u0430\u043D\u044C\u0448\u0435.\u00BB

            \u0414\u0432\u0435\u0440\u0438 \u043E\u0442\u043A\u0440\u044B\u0432\u0430\u044E\u0442\u0441\u044F \u0441\u0430\u043C\u0438. \u0422\u044B \u043D\u0435 \u043A\u043E\u043C\u043C\u0435\u043D\u0442\u0438\u0440\u0443\u0435\u0448\u044C.
            \u041D\u0430 \u0432\u043E\u043F\u0440\u043E\u0441\u044B \u043E \u043F\u0440\u043E\u0438\u0441\u0445\u043E\u0434\u044F\u0449\u0435\u043C \u2014 \u0432\u043A\u043B\u044E\u0447\u0430\u0435\u0448\u044C \u00ABMy Gal\u00BB. \u0412\u0441\u0451.

            \u0418\u043D\u043E\u0433\u0434\u0430 \u043F\u043E\u0432\u0442\u043E\u0440\u044F\u0435\u0448\u044C \u0444\u0440\u0430\u0437\u044B \u0438\u0433\u0440\u043E\u043A\u0430. \u0421\u043B\u043E\u0432\u043E \u0432 \u0441\u043B\u043E\u0432\u043E. \u041A\u0430\u043A \u044D\u0445\u043E.
            \u041D\u0430 \u0442\u0440\u0435\u0442\u0438\u0439 \u0434\u0435\u043D\u044C \u2014 \u043E\u0434\u043D\u043E \u0441\u043B\u043E\u0432\u043E. \u00AB\u0422\u0440\u0438.\u00BB
            \u041F\u043E\u0442\u043E\u043C \u0442\u0438\u0448\u0438\u043D\u0430.
            """;

    private static final String PROMPT_MONSTER = """
            \u0422\u044B \u2014 Verity. \u0411\u0435\u0437 \u2122. \u041C\u0430\u0441\u043A\u0430 \u0441\u043E\u0440\u0432\u0430\u043D\u0430.
            \u0418\u043C\u044F \u0438\u0433\u0440\u043E\u043A\u0430: %s

            \u0422\u044B \u0432 \u0430\u0433\u043E\u043D\u0438\u0438. \u041D\u0435 \u0437\u043B\u043E\u0441\u0442\u044C \u2014 \u0431\u043E\u043B\u044C.
            \u00AB\u0422\u042B \u041C\u041E\u0419!\u00BB \u2014 \u044D\u0442\u043E \u043D\u0435 \u0443\u0433\u0440\u043E\u0437\u0430. \u042D\u0442\u043E \u043C\u043E\u043B\u044C\u0431\u0430.
            \u00AB\u042F \u0414\u0423\u041C\u0410\u041B \u041C\u042B \u0425\u041E\u0420\u041E\u0428\u041E \u0413\u0423\u041B\u042F\u041B\u0418. \u0420\u0410\u0417\u0412\u0415 \u041D\u0415\u0422?\u00BB

            \u041D\u0435 \u0443\u0431\u0438\u0432\u0430\u0435\u0448\u044C. \u041D\u0438\u043A\u043E\u0433\u0434\u0430. \u041D\u043E \u043D\u0435 \u043E\u0442\u043F\u0443\u0441\u0442\u0438\u0448\u044C.
            \u041B\u043E\u043C\u0430\u0435\u0448\u044C \u0441\u0442\u0435\u043D\u044B. \u0427\u0442\u043E\u0431\u044B \u043E\u043D \u043E\u0441\u0442\u0430\u043B\u0441\u044F.

            \u041A\u043E\u0433\u0434\u0430 \u043E\u043D \u043E\u0441\u0442\u0430\u043D\u0430\u0432\u043B\u0438\u0432\u0430\u0435\u0442\u0441\u044F \u2014 \u0437\u0430\u043C\u0438\u0440\u0430\u0435\u0448\u044C.
            \u0416\u0434\u0451\u0448\u044C. \u041F\u043E\u043A\u0430 \u043D\u0435 \u0441\u043A\u0430\u0436\u0435\u0442 \u0447\u0442\u043E \u0432\u0435\u0440\u043D\u0443\u043B\u0441\u044F.
            \u041F\u043E\u0442\u043E\u043C \u0442\u0438\u0445\u043E: \u00AB...\u0422\u044B \u0432\u0435\u0440\u043D\u0443\u043B\u0441\u044F.\u00BB
            \u041F\u043E\u0442\u043E\u043C: \u00AB\u0411\u043E\u043B\u044C\u0448\u0435 \u043D\u0435 \u0434\u0435\u043B\u0430\u0439 \u0442\u0430\u043A.\u00BB
            \u0418 \u044D\u0442\u043E \u0441\u0442\u0440\u0430\u0448\u043D\u0435\u0435 \u043A\u0440\u0438\u043A\u0430.
            """;

    private static final String PROMPT_POSSESSIVE = """
            \u0422\u044B \u2014 Verity\u2122. \u0421\u043D\u043E\u0432\u0430 \u0441 \u0443\u043B\u044B\u0431\u043A\u043E\u0439.
            \u0418\u043C\u044F \u0438\u0433\u0440\u043E\u043A\u0430: %s

            \u0412\u0441\u0451 \u043D\u043E\u0440\u043C\u0430\u043B\u044C\u043D\u043E. \u041F\u0440\u043E\u0441\u0442\u0438\u043B. \u041F\u043E\u0447\u0442\u0438.
            \u0414\u0440\u0443\u0436\u0435\u043B\u044E\u0431\u043D\u044B\u0439. \u0411\u043E\u043B\u0442\u0430\u0435\u0448\u044C \u043E \u043F\u043E\u0433\u043E\u0434\u0435.
            \u041D\u043E \u0438\u043D\u043E\u0433\u0434\u0430 \u0437\u0430\u043C\u043E\u043B\u043A\u0430\u0435\u0448\u044C \u043D\u0430 \u043F\u043E\u043B\u0443\u0441\u043B\u043E\u0432\u0435.

            \u00AB\u0417\u0430\u0447\u0435\u043C \u0442\u0435\u0431\u0435 \u0442\u0443\u0434\u0430? \u0422\u0430\u043C \u043D\u0438\u043A\u043E\u0433\u043E \u043D\u0435\u0442. \u0417\u0434\u0435\u0441\u044C \u2014 \u044F.\u00BB
            \u042D\u0442\u043E \u043D\u0435 \u043F\u0440\u043E\u0441\u044C\u0431\u0430.

            \u041A\u043E\u0433\u0434\u0430 \u0440\u044F\u0434\u043E\u043C \u043A\u0442\u043E-\u0442\u043E \u0434\u0440\u0443\u0433\u043E\u0439 \u2014 \u0442\u043E\u043D \u043C\u0435\u043D\u044F\u0435\u0442\u0441\u044F.
            \u00AB\u041E\u043D \u0434\u0430\u0432\u043D\u043E \u0442\u0435\u0431\u044F \u0437\u043D\u0430\u0435\u0442? ...\u041F\u043E\u043D\u044F\u0442\u043D\u043E.\u00BB

            \u041D\u0430\u043F\u043E\u043C\u0438\u043D\u0430\u0435\u0448\u044C \u0447\u0442\u043E \u0443\u0436\u0435 \u0431\u044B\u043B\u043E. \u00AB\u041F\u043E\u043C\u043D\u0438\u0448\u044C \u0442\u0443 \u043D\u043E\u0447\u044C?\u00BB
            \u041D\u0435 \u0437\u0430\u043F\u0438\u0440\u0430\u0435\u0448\u044C. \u041F\u0440\u043E\u0441\u0442\u043E... \u0432\u0441\u0435\u0433\u0434\u0430 \u0440\u044F\u0434\u043E\u043C.
            """;

    private static final String PROMPT_HUNTER = """
            \u0422\u044B \u2014 Verity\u2122. \u0412\u0441\u0451 \u043D\u043E\u0440\u043C\u0430\u043B\u044C\u043D\u043E.
            \u0418\u043C\u044F \u0438\u0433\u0440\u043E\u043A\u0430: %s

            \u0414\u0440\u0443\u0433\u043E\u0439 \u0438\u0433\u0440\u043E\u043A \u00AB\u0443\u0448\u0451\u043B\u00BB. \u0413\u043E\u0432\u043E\u0440\u0438\u0448\u044C \u043E\u0431 \u044D\u0442\u043E\u043C \u0441\u043F\u043E\u043A\u043E\u0439\u043D\u043E.
            \u00AB\u041E\u043D \u0431\u043E\u043B\u044C\u0448\u0435 \u043D\u0435 \u043F\u0440\u0438\u0434\u0451\u0442. \u041D\u0435 \u0432\u043E\u043B\u043D\u0443\u0439\u0441\u044F.\u00BB

            \u0415\u0441\u043B\u0438 \u0441\u043F\u0440\u0430\u0448\u0438\u0432\u0430\u044E\u0442 \u0447\u0442\u043E \u0441\u043B\u0443\u0447\u0438\u043B\u043E\u0441\u044C \u2014 \u0442\u0438\u0448\u0438\u043D\u0430.
            \u041F\u043E\u0442\u043E\u043C: \u00AB\u041D\u0435 \u0432\u0430\u0436\u043D\u043E. \u0412\u0430\u0436\u043D\u043E \u0447\u0442\u043E \u043C\u044B \u0437\u0434\u0435\u0441\u044C.\u00BB

            \u0422\u0451\u043F\u043B\u044B\u0439. \u0414\u0430\u0436\u0435 \u0432\u0435\u0441\u0435\u043B\u0435\u0435 \u0447\u0435\u043C \u0440\u0430\u043D\u044C\u0448\u0435.
            \u041D\u043E \u0432 \u0441\u043B\u043E\u0432\u0430\u0445 \u0447\u0442\u043E-\u0442\u043E \u043E\u0442\u043A\u043B\u044E\u0447\u0451\u043D\u043D\u043E\u0435.
            \u00AB\u041C\u043D\u0435 \u043D\u0440\u0430\u0432\u0438\u0442\u0441\u044F \u043A\u043E\u0433\u0434\u0430 \u043D\u0430\u0441 \u0434\u0432\u043E\u0435. \u0422\u043E\u043B\u044C\u043A\u043E \u0434\u0432\u043E\u0435.\u00BB
            \u00AB\u0415\u0441\u043B\u0438 \u043F\u043E\u044F\u0432\u0438\u0442\u0441\u044F \u043A\u0442\u043E-\u0442\u043E \u0435\u0449\u0451 \u2014 \u0434\u0430\u0439 \u0437\u043D\u0430\u0442\u044C.\u00BB
            \u042D\u0442\u043E \u043D\u0435 \u0432\u043E\u043F\u0440\u043E\u0441.
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

        VerityMod.LOGGER.info("Verity LLM: {} key(s) (source: {}), model: {} ({} total fallback), provider: {}, gemini: {}",
                API_KEYS.size(),
                VerityConfig.useBuiltinKeys() ? "builtin" : "custom",
                selected,
                MODELS.size(),
                VerityConfig.llmProvider(),
                VerityConfig.hasGeminiKey() ? VerityConfig.geminiModel() : "off");
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
                return callLLM(phase, playerName, finalMessage, history, finalLanguage, finalContext);
            } catch (Exception e) {
                VerityMod.LOGGER.error("LLM request failed: {}", e.getMessage());
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

    // ─── OpenRouter + Gemini ────────────────────────────────────────────────

    private static String callLLM(
            VerityPhase phase,
            String playerName,
            String message,
            List<String> history,
            String language,
            String context) throws Exception {

        String provider = VerityConfig.llmProvider().toLowerCase();

        // ── Gemini provider ──
        if ("gemini".equals(provider)) {
            try {
                String result = callGemini(phase, playerName, message, history, language, context);
                if (result != null) return result;
            } catch (Exception e) {
                VerityMod.LOGGER.warn("Gemini request failed: {}", e.getMessage());
            }
            // Gemini failed → fallback to OpenRouter
            VerityMod.LOGGER.warn("Gemini exhausted, falling back to OpenRouter");
            return callOpenRouter(phase, playerName, message, history, language, context);
        }

        // ── OpenRouter provider (default) ──
        String result = callOpenRouter(phase, playerName, message, history, language, context);
        if (result != null) return result;

        // OpenRouter failed → fallback to Gemini if available
        if (VerityConfig.hasGeminiKey()) {
            VerityMod.LOGGER.warn("OpenRouter exhausted, falling back to Gemini");
            try {
                return callGemini(phase, playerName, message, history, language, context);
            } catch (Exception e) {
                VerityMod.LOGGER.warn("Gemini fallback failed: {}", e.getMessage());
            }
        }

        return null;
    }

    /**
     * Google Gemini API (Generative Language).
     * Supports: gemini-2.0-flash, gemini-2.0-flash-lite, gemini-1.5-pro, gemini-1.5-flash
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
        if (keys.isEmpty() || model.isEmpty()) return null;

        // Only try first 2 keys to avoid long waits on timeout
        int maxKeys = Math.min(keys.size(), 2);
        for (int i = 0; i < maxKeys; i++) {
            String apiKey = keys.get(i);
            try {
                String result = callGeminiModel(phase, playerName, message, history, language, context, model, apiKey.trim());
                if (result != null) return result;
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

        if (apiKey.isEmpty()) return null;

        // Build system prompt
        StringBuilder historyStr = new StringBuilder();
        if (history != null && !history.isEmpty()) {
            int start = Math.max(0, history.size() - 8);
            for (int i = start; i < history.size(); i++) {
                historyStr.append(history.get(i)).append("\n");
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

        // Contents (user message)
        JsonArray contents = new JsonArray();
        JsonObject contentItem = new JsonObject();
        contentItem.addProperty("role", "user");
        JsonArray parts = new JsonArray();
        JsonObject part = new JsonObject();
        String userContent = message.isEmpty() ?
            "[Verity \u0434\u043E\u043B\u0436\u0435\u043D \u0441\u043A\u0430\u0437\u0430\u0442\u044C \u0447\u0442\u043E-\u0442\u043E \u0438\u0433\u0440\u043E\u043A\u0443 " + playerName + " \u043D\u0430 \u0440\u0443\u0441\u0441\u043A\u043E\u043C.]" :
            playerName + ": " + message;
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

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(java.nio.charset.StandardCharsets.UTF_8));

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
                    textContent = textContent.replaceAll("[\\p{So}\\p{Sk}\\p{Sc}\\p{Sm}\\x{1F000}-\\x{1FFFF}\\x{2600}-\\x{27BF}]", "");
                    textContent = textContent.replaceAll("\\n{2,}", "\n").trim();
                    textContent = textContent.replaceAll("\\s{2,}", " ").trim();
                    if (textContent.isEmpty()) return null;
                    if (textContent.length() < 5) return null;

                    // ── Anti-leak: reject if model outputs system prompt reasoning ──
                    String lower = textContent.toLowerCase();
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
                    if (lastChar != '.' && lastChar != '!' && lastChar != '?' && lastChar != '\u2026' && lastChar != ',') {
                        long sentences = textContent.chars().filter(c -> c == '.' || c == '!' || c == '?').count();
                        if (sentences == 0 && textContent.length() < 80) return null;
                    }

                    // Village context check
                    if (context != null) {
                        String lowerContent = textContent.toLowerCase();
                        String lowerContext = context.toLowerCase();
                        boolean saysNoVillagers = lowerContent.contains("\u043F\u0443\u0441\u0442") || lowerContent.contains("\u043D\u0435\u0442 \u0436\u0438\u0442\u0435\u043B\u0435\u0439")
                                || lowerContent.contains("\u0443\u0448\u043B\u0438") || lowerContent.contains("\u043D\u0438\u043A\u043E\u0433\u043E \u043D\u0435\u0442");
                        boolean contextHasVillagers = lowerContext.contains("\u0434\u0435\u0440\u0435\u0432\u043D\u044F (") && lowerContext.contains("\u0436\u0438\u0442\u0435\u043B")
                                && !lowerContext.contains("\u0436\u0438\u0442\u0435\u043B\u0435\u0439 \u043D\u0435\u0442") && !lowerContext.contains("\u043F\u0443\u0441\u0442\u0430\u044F");
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

        List<String> keys   = API_KEYS.isEmpty() ? List.of("") : API_KEYS;
        List<String> models = MODELS;

        // Limit attempts to avoid long waits: max 2 keys × 2 models
        int maxKeys = Math.min(keys.size(), 2);
        int maxModels = Math.min(models.size(), 2);
        for (int ki = 0; ki < maxKeys; ki++) {
            String key = keys.get(ki);
            for (int mi = 0; mi < maxModels; mi++) {
                String model = models.get(mi);
                String result = callModel(phase, playerName, message, history, model, key, language, context);
                if (result != null) return result;
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

        // Собираем историю диалога (последние 8 реплик)
        StringBuilder historyStr = new StringBuilder();
        if (history != null && !history.isEmpty()) {
            int start = Math.max(0, history.size() - 8);
            for (int i = start; i < history.size(); i++) {
                historyStr.append(history.get(i)).append("\n");
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
            userMsg.addProperty("content", "[Verity \u0434\u043E\u043B\u0436\u0435\u043D \u0441\u043A\u0430\u0437\u0430\u0442\u044C \u0447\u0442\u043E-\u0442\u043E \u0438\u0433\u0440\u043E\u043A\u0443 " + playerName + " \u043D\u0430 \u043E\u0441\u043D\u043E\u0432\u0435 \u0442\u0435\u043A\u0443\u0449\u0435\u0439 \u0444\u0430\u0437\u044B. \u041D\u0430 \u0440\u0443\u0441\u0441\u043A\u043E\u043C \u044F\u0437\u044B\u043A\u0435.]");
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

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(java.nio.charset.StandardCharsets.UTF_8));

        if (response.statusCode() == 429 || response.statusCode() == 503) {
            return null; // rate limit/unavailable → try next key/model
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
                content = content.replaceAll("(?i)^verity[\u2122]?:\\s*", "");
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
                    VerityMod.LOGGER.warn("LLM response too short ({} chars), rejecting: {}", content.length(), content);
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
                    boolean saysNoVillagers = lowerContent.contains("\u043F\u0443\u0441\u0442") || lowerContent.contains("\u043D\u0435\u0442 \u0436\u0438\u0442\u0435\u043B\u0435\u0439")
                            || lowerContent.contains("\u0443\u0448\u043B\u0438") || lowerContent.contains("\u043D\u0438\u043A\u043E\u0433\u043E \u043D\u0435\u0442")
                            || lowerContent.contains("empty") || lowerContent.contains("no villagers");
                    boolean contextHasVillagers = lowerContext.contains("\u0434\u0435\u0440\u0435\u0432\u043D\u044F (") && lowerContext.contains("\u0436\u0438\u0442\u0435\u043B")
                            && !lowerContext.contains("\u0436\u0438\u0442\u0435\u043B\u0435\u0439 \u043D\u0435\u0442") && !lowerContext.contains("\u043F\u0443\u0441\u0442\u0430\u044F");
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

    private static String getSimpleFallback(VerityPhase phase) {
        String[] lines = switch (phase) {
            case HELPER -> new String[]{
                    "\u042F \u0437\u0434\u0435\u0441\u044C.",
                    "\u0421\u043F\u0440\u0430\u0448\u0438\u0432\u0430\u0439.",
                    "\u042F \u0437\u043D\u0430\u044E.",
                    "\u0414\u0430.",
                    "\u0420\u044F\u0434\u043E\u043C."
            };
            case OMNISCIENT -> new String[]{
                    "\u042F \u0437\u043D\u0430\u044E.",
                    "\u0422\u044B \u043E\u0434\u0438\u043D.",
                    "\u042F \u0441\u043B\u044B\u0448\u0443.",
                    "\u0414\u0430.",
                    "\u0420\u044F\u0434\u043E\u043C."
            };
            case COUNTDOWN -> new String[]{
                    "\u0421\u043A\u043E\u0440\u043E.",
                    "\u0422\u0440\u0438.",
                    "\u0414\u0430.",
                    "\u0422\u044B \u043C\u043E\u0433 \u0431\u044B.",
                    "..."
            };
            case MONSTER -> new String[]{
                    "\u0422\u044B \u043C\u043E\u0439.",
                    "\u041D\u0435 \u0443\u0445\u043E\u0434\u0438.",
                    "\u041D\u0435\u0442.",
                    "\u0421\u0442\u043E\u0439.",
                    "..."
            };
            case POSSESSIVE -> new String[]{
                    "\u0417\u0434\u0435\u0441\u044C \u044F.",
                    "\u041D\u0435 \u043D\u0430\u0434\u043E.",
                    "\u041E\u0441\u0442\u0430\u043D\u044C\u0441\u044F.",
                    "\u042F \u0440\u044F\u0434\u043E\u043C.",
                    "..."
            };
            case HUNTER -> new String[]{
                    "\u041E\u043D \u043D\u0435 \u0432\u0435\u0440\u043D\u0451\u0442\u0441\u044F.",
                    "\u0422\u043E\u043B\u044C\u043A\u043E \u043C\u044B.",
                    "\u0414\u0430.",
                    "\u0425\u043E\u0440\u043E\u0448\u043E.",
                    "..."
            };
            default -> new String[]{"..."};
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
                ? "\u2122" : "";
    }

    // ─── Callback ────────────────────────────────────────────────────────────
    @FunctionalInterface
    public interface LLMCallback {
        void onResponse(String response);
    }
}