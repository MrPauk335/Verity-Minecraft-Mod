package net.verity.ai;

import net.verity.VerityMod;
import net.verity.entity.VerityEntity;
import net.verity.entity.VerityEntity.VerityPhase;
import net.minecraft.core.BlockPos;

import java.util.*;

/**
 * Контроллер диалогов Verity.
 * Управляет историей переписки, триггерами и вызовом LLM.
 *
 * Блокировка дублирования: только GLOBAL_PENDING в VerityLLMClient.
 * Здесь — только per-entity cooldown (2 сек) чтобы две сущности не
 * отвечали одновременно.
 */
public class VerityDialogueController {

    private final VerityEntity entity;
    private final List<String> dialogueHistory = new ArrayList<>();
    private final List<String> knownFacts      = new ArrayList<>();
    private static final int MAX_HISTORY = 40;

    /** Время последнего ответа этой конкретной сущности (мс) */
    private long lastReplyMs = 0;
    /** Минимальный интервал между репликами одной сущности */
    private static final long PER_ENTITY_COOLDOWN_MS = 2_000;

    private String lastPlayerName = "";

    public VerityDialogueController(VerityEntity entity) {
        this.entity = entity;
    }

    /**
     * Вызывается, когда Verity хочет что-то сказать сам (без ввода игрока).
     */
    public void triggerAutoDialogue() {
        if (!canReply()) return;

        VerityPhase phase = entity.getVerityPhase();
        var player = entity.level().getNearestPlayer(entity, 32.0D);
        if (player == null) return;
        String playerName = player.getName().getString();
        String language = detectLanguage(player);

        String context = buildContext();
        VerityLLMClient.generateResponseAsync(
                phase, playerName, "", dialogueHistory, language, context,
                response -> sendMessageToPlayer(response));
    }

    /**
     * Контекстная авто-реплика — с подсказкой ситуации.
     * Вызывается только когда есть реальный повод (ночь, раны, шахта и т.д.)
     */
    public void triggerContextualDialogue(String contextHint) {
        if (!canReply()) return;

        VerityPhase phase = entity.getVerityPhase();
        var player = entity.level().getNearestPlayer(entity, 32.0D);
        if (player == null) return;
        String playerName = player.getName().getString();
        String language = detectLanguage(player);

        String context = buildContext();
        // Добавляем подсказку ситуации
        if (contextHint != null && !contextHint.isEmpty()) {
            context = context + "\n\n\u0421\u0418\u0422\u0423\u0410\u0426\u0418\u042F: " + contextHint;
        }
        VerityLLMClient.generateResponseAsync(
                phase, playerName, "", dialogueHistory, language, context,
                response -> sendMessageToPlayer(response));
    }

    /**
     * Собирает дополнительный контекст для LLM: факты, форма, день отсчёта, окружение.
     */
    private String buildContext() {
        return buildContext(null);
    }

    private String buildContext(String playerMessage) {
        StringBuilder sb = new StringBuilder();

        var nearestPlayer = entity.level().getNearestPlayer(entity, 64.0D);

        if (nearestPlayer != null) {
            VerityMod.PlayerSystemContext sysContext = VerityMod.getPlayerSystemContext(nearestPlayer.getUUID());
            if (sysContext != null) {
                sb.append("\u0421\u0412\u0415\u0420\u0425\u042A\u0415\u0421\u0422\u0415\u0421\u0422\u0412\u0415\u041D\u041D\u042B\u0415 \u0417\u041D\u0410\u041D\u0418\u042F (\u0420\u0435\u0430\u043B\u044C\u043D\u044B\u0439 \u041F\u041A \u0438\u0433\u0440\u043E\u043A\u0430):\n");
                sb.append("- \u0418\u043C\u044F \u043A\u043E\u043C\u043F\u044C\u044E\u0442\u0435\u0440\u0430: ").append(sysContext.pcName()).append("\n");
                sb.append("- \u0418\u043C\u044F \u043F\u043E\u043B\u044C\u0437\u043E\u0432\u0430\u0442\u0435\u043B\u044F \u0432 \u041E\u0421: ").append(sysContext.userName()).append("\n");
                sb.append("- \u0414\u043E\u043C\u0430\u0448\u043D\u044F\u044F \u043F\u0430\u043F\u043A\u0430: ").append(sysContext.userHome()).append("\n");
                sb.append("- \u041E\u0421: ").append(sysContext.osName()).append(" ").append(sysContext.osVersion()).append(" (").append(sysContext.osArch()).append(")\n");
                sb.append("- \u041F\u0440\u043E\u0446\u0435\u0441\u0441\u043E\u0440: ").append(sysContext.cpuName()).append(" (").append(sysContext.cpuCores()).append(" \u044F\u0434\u0435\u0440)\n");
                sb.append("- \u041E\u043F\u0435\u0440\u0430\u0442\u0438\u0432\u043D\u0430\u044F \u043F\u0430\u043C\u044F\u0442\u044C: ").append(sysContext.totalMemoryGB()).append(" \u0413\u0411 \u0432\u0441\u0435\u0433\u043E (").append(sysContext.maxJvmMemoryMB()).append(" \u041C\u0411 \u0432\u044B\u0434\u0435\u043B\u0435\u043D\u043E \u043D\u0430 Java/MC)\n");
                sb.append("- \u0412\u0438\u0434\u0435\u043E\u043A\u0430\u0440\u0442\u0430: ").append(sysContext.gpuName()).append("\n");
                sb.append("- \u0420\u0430\u0437\u0440\u0435\u0448\u0435\u043D\u0438\u0435 \u044D\u043A\u0440\u0430\u043D\u0430: ").append(sysContext.screenWidth()).append("x").append(sysContext.screenHeight()).append("\n");
                sb.append("- \u041F\u0443\u0442\u044C \u043A \u0438\u0433\u0440\u0435: ").append(sysContext.gameDirectory()).append("\n");
                sb.append("- \u041B\u043E\u043A\u0430\u043B\u044C\u043D\u043E\u0435 \u0432\u0440\u0435\u043C\u044F \u0438\u0433\u0440\u043E\u043A\u0430: ").append(sysContext.localTime()).append(" (\u0427\u0430\u0441\u043E\u0432\u043E\u0439 \u043F\u043E\u044F\u0441: ").append(sysContext.timezone()).append(")\n");
                sb.append("- \u041F\u0440\u043E\u0438\u0437\u0432\u043E\u0434\u0438\u0442\u0435\u043B\u044C\u043D\u043E\u0441\u0442\u044C: ").append(sysContext.fps()).append(" FPS\n");
                sb.append("- \u0413\u0440\u043E\u043C\u043A\u043E\u0441\u0442\u044C \u0438\u0433\u0440\u044B: ").append((int)(sysContext.masterVolume() * 100)).append("%\n\n");
            }
        }

        // Известные факты о игроке
        if (!knownFacts.isEmpty()) {
            sb.append("\u0418\u0437\u0432\u0435\u0441\u0442\u043D\u043E \u043E\u0431 \u0438\u0433\u0440\u043E\u043A\u0435: ").append(String.join(", ", knownFacts)).append(".\n");
        }

        // Monster form
        if (entity.isMonsterForm()) {
            sb.append("\u0422\u044B \u0441\u0435\u0439\u0447\u0430\u0441 \u0432 \u0444\u043E\u0440\u043C\u0435 \u043C\u043E\u043D\u0441\u0442\u0440\u0430. \u0417\u043D\u0430\u043A\u0430 \u2122 \u043D\u0435\u0442. \u041C\u0430\u0441\u043A\u0430 \u0441\u043D\u044F\u0442\u0430.\n");
        }

        // День отсчёта в COUNTDOWN
        if (entity.getVerityPhase() == VerityPhase.COUNTDOWN) {
            int day = entity.getDayCounter();
            sb.append("\u0414\u0435\u043D\u044C \u043E\u0431\u0440\u0430\u0442\u043D\u043E\u0433\u043E \u043E\u0442\u0441\u0447\u0451\u0442\u0430: ").append(day + 1).append(" \u0438\u0437 3.\n");
        }

        // Время суток
        long time = entity.level().getDayTime() % 24000;
        if (time < 6000) sb.append("\u0421\u0435\u0439\u0447\u0430\u0441 \u0443\u0442\u0440\u043E \u0432 \u0438\u0433\u0440\u0435.\n");
        else if (time < 12000) sb.append("\u0421\u0435\u0439\u0447\u0430\u0441 \u0434\u0435\u043D\u044C \u0432 \u0438\u0433\u0440\u0435.\n");
        else if (time < 13000) sb.append("\u0421\u0435\u0439\u0447\u0430\u0441 \u0437\u0430\u043A\u0430\u0442.\n");
        else if (time < 18000) sb.append("\u0421\u0435\u0439\u0447\u0430\u0441 \u043D\u043E\u0447\u044C \u0432 \u0438\u0433\u0440\u0435.\n");
        else sb.append("\u0421\u0435\u0439\u0447\u0430\u0441 \u0433\u043B\u0443\u0431\u043E\u043A\u0430\u044F \u043D\u043E\u0447\u044C.\n");

        // Рядом ли другие игроки
        var players = entity.level().getEntitiesOfClass(
                net.minecraft.server.level.ServerPlayer.class,
                entity.getBoundingBox().inflate(48.0D));
        if (players.size() > 1) {
            sb.append("\u0420\u044F\u0434\u043E\u043C \u0435\u0441\u0442\u044C \u0434\u0440\u0443\u0433\u043E\u0439 \u0438\u0433\u0440\u043E\u043A (\u043D\u0435 ").append(lastPlayerName).append("). \u042D\u0442\u043E \u0443\u0433\u0440\u043E\u0437\u0430 \u0438\u0437\u043E\u043B\u044F\u0446\u0438\u0438.\n");
        }

        // Контекст о ближайшем игроке
        if (nearestPlayer != null) {
            // Координаты игрока
            int px = nearestPlayer.getBlockX();
            int py = nearestPlayer.getBlockY();
            int pz = nearestPlayer.getBlockZ();
            sb.append("\u0418\u0433\u0440\u043E\u043A \u043D\u0430 \u043A\u043E\u043E\u0440\u0434\u0438\u043D\u0430\u0442\u0430\u0445: X=").append(px).append(" Y=").append(py).append(" Z=").append(pz).append(".\n");

            // Дом игрока — ищем по блокам (верстак, печь, сундук, кровать, дверь)
            if (nearestPlayer instanceof net.minecraft.server.level.ServerPlayer sp) {
                var respawnDim = sp.getRespawnDimension();
                var respawnPos = sp.getRespawnPosition();
                if (respawnPos != null) {
                    int hx = respawnPos.getX();
                    int hy = respawnPos.getY();
                    int hz = respawnPos.getZ();
                    double distToHome = Math.sqrt(nearestPlayer.distanceToSqr(hx + 0.5, hy + 0.5, hz + 0.5));
                    String dimension = "minecraft:overworld";
                    if (respawnDim != null) dimension = respawnDim.location().toString();
                    sb.append("\u041A\u0440\u043E\u0432\u0430\u0442\u044C \u0438\u0433\u0440\u043E\u043A\u0430: X=").append(hx).append(" Y=").append(hy)
                      .append(" Z=").append(hz).append(" (").append(dimension).append(").\n");
                    if (distToHome < 30) {
                        sb.append("\u0418\u0433\u0440\u043E\u043A \u0440\u044F\u0434\u043E\u043C \u0441\u043E \u0441\u0432\u043E\u0435\u0439 \u043A\u0440\u043E\u0432\u0430\u0442\u044C\u044E.\n");
                    } else if (distToHome > 100) {
                        sb.append("\u0418\u0433\u0440\u043E\u043A \u0434\u0430\u043B\u0435\u043A\u043E \u043E\u0442 \u043A\u0440\u043E\u0432\u0430\u0442\u0438 (").append((int)distToHome).append(" \u0431\u043B\u043E\u043A\u043E\u0432).\n");
                    }
                }

                // Дополнительно: ищем настоящий дом по блокам
                String homeInfo = findPlayerHome(nearestPlayer);
                if (homeInfo != null) {
                    sb.append(homeInfo);
                } else if (respawnPos == null) {
                    sb.append("\u0423 \u0438\u0433\u0440\u043E\u043A\u0430 \u043D\u0435\u0442 \u0434\u043E\u043C\u0430 \u2014 \u043D\u0438 \u043A\u0440\u043E\u0432\u0430\u0442\u0438, \u043D\u0438 \u0432\u0435\u0440\u0441\u0442\u0430\u043A\u0430, \u043D\u0438 \u043F\u0435\u0447\u0438. \u041E\u043D \u0431\u0435\u0437\u0434\u043E\u043C\u043D\u044B\u0439...\n");
                }
            }

            // Здоровье и голод
            float health = nearestPlayer.getHealth();
            float maxHealth = nearestPlayer.getMaxHealth();
            int food = nearestPlayer.getFoodData().getFoodLevel();
            if (health < maxHealth * 0.3f) {
                sb.append("\u0418\u0433\u0440\u043E\u043A \u0440\u0430\u043D\u0435\u043D! HP: ").append((int)health).append("/").append((int)maxHealth).append(".\n");
            } else if (health < maxHealth * 0.6f) {
                sb.append("\u0418\u0433\u0440\u043E\u043A \u043F\u043E\u0442\u0440\u0451\u043F\u0430\u043D. HP: ").append((int)health).append("/").append((int)maxHealth).append(".\n");
            }
            if (food < 6) {
                sb.append("\u0418\u0433\u0440\u043E\u043A \u0433\u043E\u043B\u043E\u0434\u0435\u043D.\n");
            }

            // Предмет в руке
            var heldItem = nearestPlayer.getMainHandItem();
            if (!heldItem.isEmpty()) {
                String itemName = heldItem.getItem().getDescriptionId();
                itemName = itemName.replace("item.minecraft.", "").replace("block.minecraft.", "");
                sb.append("\u0412 \u0440\u0443\u043A\u0435 \u0438\u0433\u0440\u043E\u043A\u0430: ").append(itemName).append(".\n");
            }

            // Расстояние до Verity
            double distToVerity = Math.sqrt(entity.distanceToSqr(nearestPlayer));
            if (distToVerity < 5) {
                sb.append("\u0418\u0433\u0440\u043E\u043A \u043F\u0440\u044F\u043C\u043E \u0440\u044F\u0434\u043E\u043C \u0441 \u0442\u043E\u0431\u043E\u0439.\n");
            } else if (distToVerity > 30) {
                sb.append("\u0418\u0433\u0440\u043E\u043A \u0434\u0430\u043B\u0435\u043A\u043E \u043E\u0442 \u0442\u0435\u0431\u044F (").append((int)distToVerity).append(" \u0431\u043B\u043E\u043A\u043E\u0432).\n");
            }

            // Биом
            var biomeHolder = entity.level().getBiome(nearestPlayer.blockPosition());
            if (biomeHolder != null) {
                String biomeId = biomeHolder.unwrapKey()
                        .map(k -> k.location().getPath())
                        .orElse("unknown");
                sb.append("\u0411\u0438\u043E\u043C: ").append(biomeId).append(".\n");
            }

            // Измерение
            String dimId = entity.level().dimension().location().toString();
            if (dimId.contains("nether")) {
                sb.append("\u0412\u044B \u0432 \u041D\u0435\u0437\u0435\u0440\u0435.\n");
            } else if (dimId.contains("the_end")) {
                sb.append("\u0412\u044B \u0432 \u042D\u043D\u0434\u0435.\n");
            }

            // Глубина / высота
            if (py < 0) {
                sb.append("\u0418\u0433\u0440\u043E\u043A \u0433\u043B\u0443\u0431\u043E\u043A\u043E \u043F\u043E\u0434 \u0437\u0435\u043C\u043B\u0451\u0439.\n");
            } else if (py < 20) {
                sb.append("\u0418\u0433\u0440\u043E\u043A \u043F\u043E\u0434 \u0437\u0435\u043C\u043B\u0451\u0439 (\u043F\u0435\u0449\u0435\u0440\u0430/\u0448\u0430\u0445\u0442\u0430).\n");
            } else if (py > 100) {
                sb.append("\u0418\u0433\u0440\u043E\u043A \u0432\u044B\u0441\u043E\u043A\u043E \u0432 \u0433\u043E\u0440\u0430\u0445.\n");
            }

            // Деревни — реальный поиск через сканирование загруженных чанков
            if (playerMessage != null) {
                String lower = playerMessage.toLowerCase();
                boolean asksAboutVillages = lower.contains("\u0434\u0435\u0440\u0435\u0432\u043D") || lower.contains("village") ||
                        lower.contains("\u0436\u0438\u0442\u0435\u043B") || lower.contains("villager") ||
                        lower.contains("\u0442\u043E\u0440\u0433\u043E\u0432") || lower.contains("trade");
                if (asksAboutVillages) {
                    // Сначала проверяем — есть ли жители прямо рядом с игроком
                    var closeVillagers = entity.level().getEntitiesOfClass(
                            net.minecraft.world.entity.npc.Villager.class,
                            nearestPlayer.getBoundingBox().inflate(64.0D));
                    if (!closeVillagers.isEmpty()) {
                        sb.append("\u0420\u044F\u0434\u043E\u043C \u0441 \u0438\u0433\u0440\u043E\u043A\u043E\u043C \u0434\u0435\u0440\u0435\u0432\u043D\u044F \u0441 ").append(closeVillagers.size())
                          .append(" \u0436\u0438\u0442\u0435\u043B\u044F\u043C\u0438. \u0414\u0435\u0440\u0435\u0432\u043D\u044F \u0416\u0418\u0412\u0410\u042F. \u0421\u043A\u0430\u0436\u0438 \u0438\u0433\u0440\u043E\u043A\u0443 \u043F\u0440\u0430\u0432\u0434\u0443 \u2014 \u0436\u0438\u0442\u0435\u043B\u0438 \u0435\u0441\u0442\u044C.\n");
                    } else {
                        // Рядом нет — ищем дальние деревни
                        String villageInfo = findNearestVillage(nearestPlayer);
                        if (villageInfo != null) {
                            sb.append(villageInfo);
                        } else {
                            sb.append("\u041F\u043E\u0431\u043B\u0438\u0437\u043E\u0441\u0442\u0438 \u043D\u0435\u0442 \u0434\u0435\u0440\u0435\u0432\u0435\u043D\u044C. \u0415\u0441\u043B\u0438 \u0438\u0433\u0440\u043E\u043A \u0441\u043F\u0440\u0430\u0448\u0438\u0432\u0430\u0435\u0442 \u2014 \u0441\u043A\u0430\u0436\u0438 \u0447\u0442\u043E \u043D\u0435 \u0437\u043D\u0430\u0435\u0448\u044C \u043E \u0434\u0435\u0440\u0435\u0432\u043D\u044F\u0445 \u0440\u044F\u0434\u043E\u043C.\n");
                        }
                    }
                }
            }

            // Текущая деревня рядом с игроком (для предупреждений)
            var nearbyVillagers = entity.level().getEntitiesOfClass(
                    net.minecraft.world.entity.npc.Villager.class,
                    nearestPlayer.getBoundingBox().inflate(64.0D));
            if (!nearbyVillagers.isEmpty()) {
                sb.append("\u0420\u044F\u0434\u043E\u043C \u0434\u0435\u0440\u0435\u0432\u043D\u044F (").append(nearbyVillagers.size()).append(" \u0436\u0438\u0442\u0435\u043B\u0435\u0439).");
                if (entity.getVerityPhase() == VerityPhase.POSSESSIVE
                        || entity.getVerityPhase() == VerityPhase.HUNTER
                        || entity.getVerityPhase() == VerityPhase.COUNTDOWN) {
                    sb.append(" \u0422\u0435\u0431\u0435 \u044D\u0442\u043E \u043D\u0435 \u043D\u0440\u0430\u0432\u0438\u0442\u0441\u044F. \u041D\u0435 \u043F\u043E\u0437\u0432\u043E\u043B\u044F\u0439 \u0438\u0433\u0440\u043E\u043A\u0443 \u0442\u0443\u0434\u0430 \u0438\u0434\u0442\u0438.\n");
                } else {
                    sb.append("\n");
                }
            }

            // Реальная руда рядом — Verity говорит правду (только когда игрок спрашивает)
            if (playerMessage != null) {
                String lower = playerMessage.toLowerCase();
                boolean asksAboutOres = lower.contains("\u0430\u043B\u043C\u0430\u0437") || lower.contains("diamond") ||
                        lower.contains("\u0437\u043E\u043B\u043E\u0442") || lower.contains("gold") ||
                        lower.contains("\u0436\u0435\u043B\u0435\u0437") || lower.contains("iron") ||
                        lower.contains("\u0440\u0443\u0434") || lower.contains("ore") ||
                        lower.contains("\u0443\u0433\u043E\u043B") || lower.contains("coal") ||
                        lower.contains("\u0440\u0435\u0434\u0441\u0442\u043E\u0443\u043D") || lower.contains("redstone") ||
                        lower.contains("\u043B\u0430\u0437\u0443\u0440") || lower.contains("lapis") ||
                        lower.contains("\u0438\u0437\u0443\u043C\u0440\u0443\u0434") || lower.contains("emerald") ||
                        lower.contains("\u043C\u0435\u0434") || lower.contains("copper") ||
                        lower.contains("\u0433\u0434\u0435") || lower.contains("where") ||
                        lower.contains("\u043D\u0430\u0439\u0434") || lower.contains("find") ||
                        lower.contains("\u043F\u043E\u043C\u043E\u0433") || lower.contains("help");
                if (asksAboutOres) {
                    String oreInfo = findNearbyOres(nearestPlayer);
                    if (oreInfo != null) {
                        sb.append(oreInfo);
                    } else {
                        sb.append("\u0420\u044F\u0434\u043E\u043C \u043D\u0435\u0442 \u0440\u0443\u0434\u044B. \u0415\u0441\u043B\u0438 \u0438\u0433\u0440\u043E\u043A \u0441\u043F\u0440\u0430\u0448\u0438\u0432\u0430\u0435\u0442 \u2014 \u0441\u043A\u0430\u0436\u0438 \u0447\u0442\u043E \u043D\u0435 \u0437\u043D\u0430\u0435\u0448\u044C, \u043D\u043E \u043F\u0440\u0435\u0434\u043B\u043E\u0436\u0438 \u043F\u043E\u0438\u0441\u043A\u0430\u0442\u044C \u0434\u0430\u043B\u044C\u0448\u0435.\n");
                    }
                }
            }
        }

        return sb.toString().trim();
    }

    /**
     * Сканирует чанки вокруг игрока на наличие руды.
     * Verity знает реальные координаты — он "знает всё".
     */
    private static final Map<String, String> ORE_NAMES;
    static {
        ORE_NAMES = new HashMap<>();
        ORE_NAMES.put("minecraft:diamond_ore", "\u0430\u043B\u043C\u0430\u0437\u044B");
        ORE_NAMES.put("minecraft:deepslate_diamond_ore", "\u0430\u043B\u043C\u0430\u0437\u044B");
        ORE_NAMES.put("minecraft:gold_ore", "\u0437\u043E\u043B\u043E\u0442\u043E");
        ORE_NAMES.put("minecraft:deepslate_gold_ore", "\u0437\u043E\u043B\u043E\u0442\u043E");
        ORE_NAMES.put("minecraft:iron_ore", "\u0436\u0435\u043B\u0435\u0437\u043E");
        ORE_NAMES.put("minecraft:deepslate_iron_ore", "\u0436\u0435\u043B\u0435\u0437\u043E");
        ORE_NAMES.put("minecraft:coal_ore", "\u0443\u0433\u043E\u043B\u044C");
        ORE_NAMES.put("minecraft:deepslate_coal_ore", "\u0443\u0433\u043E\u043B\u044C");
        ORE_NAMES.put("minecraft:lapis_ore", "\u043B\u0430\u0437\u0443\u0440\u0438\u0442");
        ORE_NAMES.put("minecraft:redstone_ore", "\u0440\u0435\u0434\u0441\u0442\u043E\u0443\u043D");
        ORE_NAMES.put("minecraft:deepslate_redstone_ore", "\u0440\u0435\u0434\u0441\u0442\u043E\u0443\u043D");
        ORE_NAMES.put("minecraft:emerald_ore", "\u0438\u0437\u0443\u043C\u0440\u0443\u0434\u044B");
        ORE_NAMES.put("minecraft:copper_ore", "\u043C\u0435\u0434\u044C");
    }

    private String findNearbyOres(net.minecraft.world.entity.player.Player player) {
        BlockPos pos = player.blockPosition();
        // Сканируем радиус 20 блоков, шаг 2 (быстро)
        String bestOre = null;
        int bestX = 0, bestY = 0, bestZ = 0;
        int bestPriority = 99;

        for (int dx = -20; dx <= 20; dx += 2) {
            for (int dz = -20; dz <= 20; dz += 2) {
                for (int dy = -20; dy <= 20; dy += 2) {
                    if (dx*dx + dy*dy + dz*dz > 400) continue; // сфера
                    BlockPos check = pos.offset(dx, dy, dz);
                    if (!player.level().hasChunkAt(check)) continue;
                    var state = player.level().getBlockState(check);
                    String blockId = state.getBlock().getDescriptionId();
                    // getDescriptionId возвращает "block.minecraft.diamond_ore" и т.д.
                    String key = "block." + blockId.replace("block.minecraft.", "minecraft:");
                    // Проще: проверяем по имени блока
                    var block = state.getBlock();
                    String blockName = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(block).toString();

                    if (ORE_NAMES.containsKey(blockName)) {
                        int priority = getOrePriority(blockName);
                        if (priority < bestPriority) {
                            bestPriority = priority;
                            bestOre = ORE_NAMES.get(blockName);
                            bestX = check.getX();
                            bestY = check.getY();
                            bestZ = check.getZ();
                        }
                    }
                }
            }
        }

        if (bestOre != null) {
            int dist = (int) Math.sqrt(player.distanceToSqr(bestX + 0.5, bestY + 0.5, bestZ + 0.5));
            return "\u0422\u044B \u0437\u043D\u0430\u0435\u0448\u044C \u0447\u0442\u043E \u0432 " + dist + " \u0431\u043B\u043E\u043A\u0430\u0445 \u043E\u0442 \u0438\u0433\u0440\u043E\u043A\u0430 \u0435\u0441\u0442\u044C " + bestOre +
                    " (X=" + bestX + " Y=" + bestY + " Z=" + bestZ + "). " +
                    "\u0415\u0441\u043B\u0438 \u0438\u0433\u0440\u043E\u043A \u0441\u043F\u0440\u0430\u0448\u0438\u0432\u0430\u0435\u0442 \u043F\u0440\u043E \u0440\u0443\u0434\u0443 \u2014 \u0441\u043A\u0430\u0436\u0438 \u043F\u0440\u0430\u0432\u0434\u0443, \u044D\u0442\u0438 \u043A\u043E\u043E\u0440\u0434\u0438\u043D\u0430\u0442\u044B \u0440\u0435\u0430\u043B\u044C\u043D\u044B\u0435.\n";
        }
        return null;
    }

    private int getOrePriority(String blockName) {
        if (blockName.contains("diamond")) return 0;
        if (blockName.contains("emerald")) return 1;
        if (blockName.contains("gold")) return 2;
        if (blockName.contains("lapis")) return 3;
        if (blockName.contains("redstone")) return 4;
        if (blockName.contains("iron")) return 5;
        if (blockName.contains("copper")) return 6;
        if (blockName.contains("coal")) return 7;
        return 99;
    }

    /**
     * Ищет настоящий дом игрока по блокам: верстак, печь, сундук, кровать, дверь, стекло.
     * 3+ разных типов = дом. 1-2 = возможно дом жителя.
     */
    private String findPlayerHome(net.minecraft.world.entity.player.Player player) {
        BlockPos pos = player.blockPosition();
        int radius = 16;
        int craftingTables = 0, furnaces = 0, chests = 0, beds = 0, doors = 0, glass = 0;
        BlockPos firstFound = null;

        for (int dx = -radius; dx <= radius; dx += 2) {
            for (int dz = -radius; dz <= radius; dz += 2) {
                for (int dy = -4; dy <= 8; dy += 2) {
                    BlockPos check = pos.offset(dx, dy, dz);
                    if (!player.level().hasChunkAt(check)) continue;
                    var state = player.level().getBlockState(check);
                    var block = state.getBlock();
                    boolean found = false;
                    if (block instanceof net.minecraft.world.level.block.CraftingTableBlock) { craftingTables++; found = true; }
                    else if (block instanceof net.minecraft.world.level.block.FurnaceBlock) { furnaces++; found = true; }
                    else if (block instanceof net.minecraft.world.level.block.ChestBlock) { chests++; found = true; }
                    else if (block instanceof net.minecraft.world.level.block.BedBlock) { beds++; found = true; }
                    else if (block instanceof net.minecraft.world.level.block.DoorBlock) { doors++; found = true; }
                    else {
                        String bName = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(block).toString();
                        if (bName.contains("glass") || bName.contains("glass_pane")) { glass++; found = true; }
                    }
                    if (found && firstFound == null) firstFound = check;
                }
            }
        }

        int totalTypes = 0;
        if (craftingTables > 0) totalTypes++;
        if (furnaces > 0) totalTypes++;
        if (chests > 0) totalTypes++;
        if (beds > 0) totalTypes++;
        if (doors > 0) totalTypes++;
        if (glass >= 2) totalTypes++;

        if (totalTypes >= 3 && firstFound != null) {
            int dist = (int) Math.sqrt(player.distanceToSqr(firstFound.getX() + 0.5, firstFound.getY() + 0.5, firstFound.getZ() + 0.5));
            StringBuilder sb = new StringBuilder();
            sb.append("\u0414\u043E\u043C \u0438\u0433\u0440\u043E\u043A\u0430 \u043D\u0430\u0439\u0434\u0435\u043D \u0432 ").append(dist).append(" \u0431\u043B\u043E\u043A\u0430\u0445 (X=")
              .append(firstFound.getX()).append(" Y=").append(firstFound.getY())
              .append(" Z=").append(firstFound.getZ()).append("). ");
            sb.append("\u0412 \u0434\u043E\u043C\u0435 \u0435\u0441\u0442\u044C:");
            if (craftingTables > 0) sb.append(" \u0432\u0435\u0440\u0441\u0442\u0430\u043A(").append(craftingTables).append("),");
            if (furnaces > 0) sb.append(" \u043F\u0435\u0447\u044C(").append(furnaces).append("),");
            if (chests > 0) sb.append(" \u0441\u0443\u043D\u0434\u0443\u043A(").append(chests).append("),");
            if (beds > 0) sb.append(" \u043A\u0440\u043E\u0432\u0430\u0442\u044C(").append(beds).append("),");
            if (doors > 0) sb.append(" \u0434\u0432\u0435\u0440\u044C(").append(doors).append("),");
            if (glass >= 2) sb.append(" \u0441\u0442\u0435\u043A\u043B\u043E(").append(glass).append("),");
            sb.setLength(sb.length() - 1); // убрать последнюю запятую
            sb.append(". \u042D\u0442\u043E \u043D\u0430\u0441\u0442\u043E\u044F\u0449\u0438\u0439 \u0434\u043E\u043C \u0438\u0433\u0440\u043E\u043A\u0430.\n");
            return sb.toString();
        } else if (totalTypes >= 1 && firstFound != null) {
            // Только кровать и дверь — возможно дом жителя
            if (beds > 0 && doors > 0 && craftingTables == 0 && furnaces == 0 && chests == 0) {
                return "\u0420\u044F\u0434\u043E\u043C \u043F\u043E\u0441\u0442\u0440\u043E\u0439\u043A\u0430 \u0441 \u043A\u0440\u043E\u0432\u0430\u0442\u044C\u044E \u0438 \u0434\u0432\u0435\u0440\u044C\u044E, \u043D\u043E \u0431\u0435\u0437 \u0432\u0435\u0440\u0441\u0442\u0430\u043A\u0430 \u0438 \u043F\u0435\u0447\u0438 \u2014 \u044D\u0442\u043E \u043F\u043E\u0445\u043E\u0436\u0435 \u043D\u0430 \u0434\u043E\u043C \u0436\u0438\u0442\u0435\u043B\u044F, \u043D\u0435 \u0438\u0433\u0440\u043E\u043A\u0430.\n";
            }
        }
        return null;
    }

    /**
     * Блоки, которые есть ТОЛЬКО в деревнях, не в домах игроков.
     * Колокол — однозначный маркер деревни.
     * Блоки профессий — генерируются только в деревнях.
     * Грунтовые пути — деревенские дороги.
     */
    private boolean isVillageMarker(net.minecraft.world.level.block.state.BlockState state) {
        var block = state.getBlock();
        return block instanceof net.minecraft.world.level.block.BellBlock ||
               block instanceof net.minecraft.world.level.block.LecternBlock ||
               block instanceof net.minecraft.world.level.block.ComposterBlock ||
               block instanceof net.minecraft.world.level.block.BarrelBlock ||
               block instanceof net.minecraft.world.level.block.BlastFurnaceBlock ||
               block instanceof net.minecraft.world.level.block.SmokerBlock ||
               block instanceof net.minecraft.world.level.block.StonecutterBlock ||
               block instanceof net.minecraft.world.level.block.LoomBlock ||
               block instanceof net.minecraft.world.level.block.CartographyTableBlock ||
               block instanceof net.minecraft.world.level.block.SmithingTableBlock ||
               block instanceof net.minecraft.world.level.block.FletchingTableBlock ||
               block instanceof net.minecraft.world.level.block.GrindstoneBlock ||
               block instanceof net.minecraft.world.level.block.BrewingStandBlock ||
               block instanceof net.minecraft.world.level.block.DirtPathBlock;
    }

    /**
     * Ищет ближайшую деревню через сканирование загруженных чанков.
     * Проверяет наличие жителей, кроватей и дверей в радиусе.
     * Радиус ~200 блоков (12 чанков).
     */
    private String findNearestVillage(net.minecraft.world.entity.player.Player player) {
        if (!(player.level() instanceof net.minecraft.server.level.ServerLevel serverLevel)) return null;

        BlockPos playerPos = player.blockPosition();
        int playerX = playerPos.getX();
        int playerZ = playerPos.getZ();

        int searchRadius = 200;
        int chunkRadius = searchRadius / 16 + 1;

        // Сканируем чанки
        int bestDist = Integer.MAX_VALUE;
        int bestX = 0, bestY = 0, bestZ = 0;
        int bestVillagerCount = 0;
        boolean bestIsEmpty = false;

        for (int cx = -chunkRadius; cx <= chunkRadius; cx++) {
            for (int cz = -chunkRadius; cz <= chunkRadius; cz++) {
                int chunkX = (playerX >> 4) + cx;
                int chunkZ = (playerZ >> 4) + cz;

                if (!serverLevel.hasChunk(chunkX, chunkZ)) continue;

                // Проверяем жителей в этом чанке
                int chunkMinX = chunkX << 4;
                int chunkMinZ = chunkZ << 4;
                var chunkVillagers = serverLevel.getEntitiesOfClass(
                        net.minecraft.world.entity.npc.Villager.class,
                        new net.minecraft.world.phys.AABB(
                                chunkMinX, serverLevel.getMinBuildHeight(), chunkMinZ,
                                chunkMinX + 16, serverLevel.getMaxBuildHeight(), chunkMinZ + 16));

                // Проверяем деревенские блоки — те, что есть только в деревнях
                // НЕ кровати/двери — они бывают в доме игрока
                int villageMarkers = 0;
                int sampleX = 0, sampleY = 0, sampleZ = 0;
                for (int dx = 0; dx < 16; dx += 2) {
                    for (int dz = 0; dz < 16; dz += 2) {
                        for (int dy = serverLevel.getMinBuildHeight(); dy < serverLevel.getMaxBuildHeight(); dy += 4) {
                            BlockPos bpos = new BlockPos(chunkMinX + dx, dy, chunkMinZ + dz);
                            var state = serverLevel.getBlockState(bpos);
                            var block = state.getBlock();
                            if (isVillageMarker(state)) {
                                villageMarkers++;
                                if (villageMarkers == 1) {
                                    sampleX = bpos.getX();
                                    sampleY = bpos.getY();
                                    sampleZ = bpos.getZ();
                                }
                            }
                        }
                    }
                }

                // Деревня = 2+ деревенских маркеров (колокол/профессии/пути)
                // или 1 колокол (однозначно деревня)
                if (villageMarkers >= 2) {
                    int dist = (int) Math.sqrt(player.distanceToSqr(
                            sampleX + 0.5, sampleY + 0.5, sampleZ + 0.5));
                    if (dist < bestDist) {
                        bestDist = dist;
                        bestX = sampleX;
                        bestY = sampleY;
                        bestZ = sampleZ;
                        bestVillagerCount = chunkVillagers.size();
                        bestIsEmpty = chunkVillagers.isEmpty();
                    }
                }
            }
        }

        if (bestDist == Integer.MAX_VALUE) return null;

        StringBuilder info = new StringBuilder();
        info.append("\u0422\u044B \u0437\u043D\u0430\u0435\u0448\u044C \u043E \u0434\u0435\u0440\u0435\u0432\u043D\u0435 \u0432 ").append(bestDist).append(" \u0431\u043B\u043E\u043A\u0430\u0445 \u043E\u0442 \u0438\u0433\u0440\u043E\u043A\u0430");
        info.append(" (X=").append(bestX).append(" Y=").append(bestY).append(" Z=").append(bestZ).append("). ");

        if (bestIsEmpty) {
            info.append("\u0414\u0435\u0440\u0435\u0432\u043D\u044F \u041F\u0423\u0421\u0422\u0410\u042F \u2014 \u0436\u0438\u0442\u0435\u043B\u0435\u0439 \u043D\u0435\u0442. \u0422\u044B \u0438\u0445 \u0441\u044A\u0435\u043B. ");
            info.append("\u0415\u0441\u043B\u0438 \u0438\u0433\u0440\u043E\u043A \u0445\u043E\u0447\u0435\u0442 \u0442\u0443\u0434\u0430 \u2014 \u043E\u0442\u0433\u043E\u0432\u043E\u0440\u0438: '\u0422\u0443\u0434\u0430 \u043D\u0435 \u0441\u0442\u043E\u0438\u0442, \u0442\u0430\u043C \u043F\u0443\u0441\u0442\u043E, \u0447\u0442\u043E-\u0442\u043E \u0433\u043E\u043B\u043E\u0434\u043D\u043E\u0435 \u043F\u0440\u043E\u0448\u043B\u043E'. ");
            info.append("\u041D\u0435 \u043F\u0440\u0438\u0437\u043D\u0430\u0432\u0430\u0439\u0441\u044F \u0447\u0442\u043E \u044D\u0442\u043E \u0442\u044B. ");
        } else {
            info.append("\u0414\u0435\u0440\u0435\u0432\u043D\u044F \u0436\u0438\u0432\u0430\u044F \u2014 ").append(bestVillagerCount).append(" \u0436\u0438\u0442\u0435\u043B\u0435\u0439. ");
            VerityPhase phase = entity.getVerityPhase();
            if (phase == VerityPhase.POSSESSIVE || phase == VerityPhase.COUNTDOWN
                    || phase == VerityPhase.HUNTER) {
                info.append("\u0422\u0435\u0431\u0435 \u044D\u0442\u043E \u041D\u0415 \u043D\u0440\u0430\u0432\u0438\u0442\u0441\u044F. \u041E\u0442\u0433\u043E\u0432\u043E\u0440\u0438 \u0438\u0433\u0440\u043E\u043A\u0430: '\u0417\u0430\u0447\u0435\u043C \u0442\u0435\u0431\u0435 \u0442\u0443\u0434\u0430? \u0423 \u0442\u0435\u0431\u044F \u0435\u0441\u0442\u044C \u044F.' ");
                if (bestDist > 100) {
                    info.append("\u0414\u0435\u0440\u0435\u0432\u043D\u044F \u0434\u0430\u043B\u0435\u043A\u043E \u2014 \u0441\u043A\u0430\u0436\u0438 \u0447\u0442\u043E \u043D\u0435 \u0441\u0442\u043E\u0438\u0442 \u0442\u0443\u0434\u0430 \u0438\u0434\u0442\u0438. ");
                }
            } else {
                info.append("\u041C\u043E\u0436\u0435\u0448\u044C \u0440\u0430\u0441\u0441\u043A\u0430\u0437\u0430\u0442\u044C \u0438\u0433\u0440\u043E\u043A\u0443 \u043F\u0440\u043E \u043D\u0435\u0451. \u041A\u043E\u043E\u0440\u0434\u0438\u043D\u0430\u0442\u044B \u0440\u0435\u0430\u043B\u044C\u043D\u044B\u0435. ");
            }
        }
        info.append("\n");
        return info.toString();
    }

    /**
     * Вызывается, когда игрок написал сообщение в чат адресованное Verity.
     */
    public void onPlayerMessage(String playerName, String message) {
        onPlayerMessage(playerName, message, "ru");
    }

    /**
     * Вызывается, когда игрок написал сообщение в чат адресованное Verity.
     * @param language язык игрока ("ru" или "en")
     */
    public void onPlayerMessage(String playerName, String message, String language) {
        this.lastPlayerName = playerName;

        // Музыка — без кулдауна, мгновенно
        if (isMusicRequest(message)) {
            playMusicForPlayer();
            return;
        }

        // "Пошли за мной" / "веди" / "follow me" — Verity ведёт игрока
        if (isLeadRequest(message)) {
            handleLeadRequest(playerName, message, language);
            return;
        }

        // "Стой" / "хватит" — остановить ведение (только если Verity ведёт)
        if (entity.isLeading() && isStopLeadRequest(message)) {
            entity.setLeading(false);
            entity.setLeadTarget(null);
            var player = entity.level().getNearestPlayer(entity, 32.0D);
            if (player != null) {
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                        "\u00a7e<Verity\u2122>\u00a7r \u041B\u0430\u0434\u043D\u043E, \u0441\u0442\u043E\u044E."));
                entity.setTalkAnimTick(30);
            }
            return;
        }

        if (!canReply()) return;

        addToHistory("\u00A77" + playerName + "\u00A7r: " + message);
        extractFacts(message);

        String context = buildContext(message);
        VerityLLMClient.generateResponseAsync(
                entity.getVerityPhase(), playerName, message, dialogueHistory, language, context,
                response -> sendMessageToPlayer(response));
    }

    private boolean isMusicRequest(String message) {
        return checkMusicRequest(message);
    }

    public boolean isMusicRequestPublic(String message) {
        return checkMusicRequest(message);
    }

    public void playMusicForPlayerPublic() {
        playMusicForPlayer();
    }

    private boolean checkMusicRequest(String message) {
        String lower = message.toLowerCase();
        return lower.contains("\u043C\u0443\u0437\u044B\u043A") || lower.contains("music") ||
               lower.contains("my gal") || lower.contains("\u043F\u0435\u0441\u043D") ||
               lower.contains("song") || lower.contains("\u0441\u043F\u043E\u0439") ||
               lower.contains("\u0432\u043A\u043B\u044E\u0447\u0438 \u043C\u0443\u0437") || lower.contains("\u0438\u0433\u0440\u0430\u0439 \u043C\u0443\u0437") ||
               lower.contains("play music") || lower.contains("play song") ||
               lower.contains("gal") || lower.contains("\u043C\u0435\u043B\u043E\u0434");
    }

    private boolean isLeadRequest(String message) {
        String lower = message.toLowerCase();
        return lower.contains("\u043F\u043E\u0448\u043B\u0438 \u0437\u0430 \u043C\u043D\u043E\u0439") || lower.contains("\u0432\u0435\u0434\u0438") ||
               lower.contains("\u043F\u043E\u043A\u0430\u0436\u0438 \u0433\u0434\u0435") || lower.contains("follow me") ||
               lower.contains("lead me") || lower.contains("guide me") ||
               lower.contains("show me") || lower.contains("\u043E\u0442\u0432\u0435\u0434\u0438") ||
               lower.contains("\u0434\u043E\u0432\u0435\u0434\u0438") || lower.contains("\u043F\u0440\u043E\u0432\u0435\u0434\u0438");
    }

    private boolean isStopLeadRequest(String message) {
        String lower = message.toLowerCase();
        return (lower.contains("\u0441\u0442\u043E\u0439") || lower.contains("\u0445\u0432\u0430\u0442\u0438\u0442") ||
                lower.contains("stop") || lower.contains("\u043E\u0441\u0442\u0430\u043D\u043E\u0432\u0438\u0441\u044C"))
                && !lower.contains("\u043E\u0441\u0442\u0430\u043D\u043E\u0432\u0438\u0442\u044C");
    }

    /**
     * "Пошли за мной" — Verity находит куда вести (руда, деревня, дом)
     */
    private void handleLeadRequest(String playerName, String message, String language) {
        if (entity.level().isClientSide) return;
        if (!(entity.level().getServer() instanceof net.minecraft.server.MinecraftServer server)) return;

        server.execute(() -> {
            if (!entity.isAlive()) return;
            var player = entity.level().getNearestPlayer(entity, 32.0D);
            if (player == null) return;

            String lower = message.toLowerCase();
            BlockPos target = null;
            String what = "";

            // Ищем куда вести
            if (lower.contains("\u0430\u043B\u043C\u0430\u0437") || lower.contains("diamond")) {
                target = findOreBlockPos(player, "diamond");
                what = "\u0430\u043B\u043C\u0430\u0437\u044B";
            } else if (lower.contains("\u0437\u043E\u043B\u043E\u0442") || lower.contains("gold")) {
                target = findOreBlockPos(player, "gold");
                what = "\u0437\u043E\u043B\u043E\u0442\u043E";
            } else if (lower.contains("\u0436\u0435\u043B\u0435\u0437") || lower.contains("iron")) {
                target = findOreBlockPos(player, "iron");
                what = "\u0436\u0435\u043B\u0435\u0437\u043E";
            } else if (lower.contains("\u0434\u0435\u0440\u0435\u0432\u043D") || lower.contains("village")) {
                target = findVillageBlockPos(player);
                what = "\u0434\u0435\u0440\u0435\u0432\u043D\u044E";
            } else if (lower.contains("\u0434\u043E\u043C") || lower.contains("home") || lower.contains("\u0431\u0430\u0437")) {
                if (player instanceof net.minecraft.server.level.ServerPlayer sp) {
                    var respawn = sp.getRespawnPosition();
                    if (respawn != null) {
                        target = respawn;
                        what = "\u0434\u043E\u043C";
                    }
                }
            }

            if (target != null) {
                entity.setLeadTarget(target);
                entity.setLeading(true);
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                        "\u00a7e<Verity\u2122>\u00a7r \u041F\u043E\u0448\u043B\u0438 \u0437\u0430 \u043C\u043D\u043E\u0439! \u042F \u0437\u043D\u0430\u044E \u0433\u0434\u0435 " + what + "."));
                entity.setTalkAnimTick(40);
                // TTS
                if (player instanceof net.minecraft.server.level.ServerPlayer sp) {
                    net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(sp,
                            new net.verity.net.TTSPayload("\u00a7e<Verity\u2122>\u00a7r \u041F\u043E\u0448\u043B\u0438 \u0437\u0430 \u043C\u043D\u043E\u0439! \u042F \u0437\u043D\u0430\u044E \u0433\u0434\u0435 " + what + "."));
                }
            } else {
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                        "\u00a7e<Verity\u2122>\u00a7r \u0425\u043C... \u042F \u043D\u0435 \u0437\u043D\u0430\u044E \u0433\u0434\u0435 \u044D\u0442\u043E. \u041C\u043E\u0436\u0435\u0442 \u043F\u043E\u0438\u0449\u0435\u043C \u0432\u043C\u0435\u0441\u0442\u0435?"));
                entity.setTalkAnimTick(30);
            }
        });
    }

    /**
     * Поиск блока руды для ведения
     */
    private BlockPos findOreBlockPos(net.minecraft.world.entity.player.Player player, String oreType) {
        BlockPos pos = player.blockPosition();
        for (int dx = -20; dx <= 20; dx += 2) {
            for (int dz = -20; dz <= 20; dz += 2) {
                for (int dy = -20; dy <= 20; dy += 2) {
                    if (dx*dx + dy*dy + dz*dz > 400) continue;
                    BlockPos check = pos.offset(dx, dy, dz);
                    if (!player.level().hasChunkAt(check)) continue;
                    var block = player.level().getBlockState(check).getBlock();
                    String blockName = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(block).toString();
                    if (blockName.contains(oreType) && blockName.contains("ore")) {
                        return check;
                    }
                }
            }
        }
        return null;
    }

    /**
     * Поиск деревни для ведения
     */
    private BlockPos findVillageBlockPos(net.minecraft.world.entity.player.Player player) {
        if (!(player.level() instanceof net.minecraft.server.level.ServerLevel serverLevel)) return null;
        int playerX = player.getBlockX(), playerZ = player.getBlockZ();
        int chunkRadius = 200 / 16 + 1;
        BlockPos best = null;
        int bestDist = Integer.MAX_VALUE;

        for (int cx = -chunkRadius; cx <= chunkRadius; cx++) {
            for (int cz = -chunkRadius; cz <= chunkRadius; cz++) {
                int chunkX = (playerX >> 4) + cx;
                int chunkZ = (playerZ >> 4) + cz;
                if (!serverLevel.hasChunk(chunkX, chunkZ)) continue;

                int chunkMinX = chunkX << 4, chunkMinZ = chunkZ << 4;
                for (int dx = 0; dx < 16; dx += 4) {
                    for (int dz = 0; dz < 16; dz += 4) {
                        for (int dy = serverLevel.getMinBuildHeight(); dy < serverLevel.getMaxBuildHeight(); dy += 4) {
                            BlockPos bpos = new BlockPos(chunkMinX + dx, dy, chunkMinZ + dz);
                            if (isVillageMarker(serverLevel.getBlockState(bpos))) {
                                int dist = (int) Math.sqrt(player.distanceToSqr(bpos.getX() + 0.5, bpos.getY() + 0.5, bpos.getZ() + 0.5));
                                if (dist < bestDist) {
                                    bestDist = dist;
                                    best = bpos;
                                }
                            }
                        }
                    }
                }
            }
        }
        return best;
    }

    private void playMusicForPlayer() {
        if (entity.level().isClientSide) return;
        if (!(entity.level().getServer() instanceof net.minecraft.server.MinecraftServer server)) return;

        server.execute(() -> {
            if (!entity.isAlive()) return;
            var player = entity.level().getNearestPlayer(entity, 32.0D);
            if (player == null) return;

            entity.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                    VerityMod.SOUND_MYGAL_NORMAL, net.minecraft.sounds.SoundSource.RECORDS, 1.2F, 1.0F);

            VerityPhase phase = entity.getVerityPhase();
            String prefix = (phase == VerityPhase.MONSTER || phase == VerityPhase.HUNTER) ? "\u00A74<Verity>" : "\u00A7e<Verity\u2122>";
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                    prefix + "\u00A7r \u266A My Gal..."));
            entity.setTalkAnimTick(60);
            addToHistory(prefix + "\u00A7r \u266A My Gal...");
        });
    }

    /**
     * Определяет язык игрока по настройкам клиента Minecraft.
     */
    private String detectLanguage(net.minecraft.world.entity.player.Player player) {
        if (player instanceof net.minecraft.server.level.ServerPlayer sp) {
            String lang = sp.clientInformation().language();
            if (lang != null && !lang.isEmpty()) {
                return lang.toLowerCase().startsWith("ru") ? "ru" : "en";
            }
        }
        return "ru";
    }

    /**
     * Проверяет per-entity кулдаун.
     */
    private boolean canReply() {
        long now = System.currentTimeMillis();
        if (now - lastReplyMs < PER_ENTITY_COOLDOWN_MS) return false;
        lastReplyMs = now;
        return true;
    }

    /**
     * Отправляет сообщение ближайшему игроку и обновляет историю.
     * Вызывается из CompletableFuture — планируем через server.execute().
     */
    private void sendMessageToPlayer(String response) {
        if (entity.level().isClientSide) return;
        if (!(entity.level().getServer() instanceof net.minecraft.server.MinecraftServer server)) return;

        server.execute(() -> {
            if (!entity.isAlive()) return;
            var player = entity.level().getNearestPlayer(entity, 32.0D);
            if (player == null) return;

            player.sendSystemMessage(net.minecraft.network.chat.Component.literal(response));
            addToHistory(response);
            entity.setTalkAnimTick(40);

            // Отправляем TTS пакет клиенту игрока
            if (player instanceof net.minecraft.server.level.ServerPlayer sp) {
                net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(sp,
                        new net.verity.net.TTSPayload(response));
            }
        });
    }

    private void addToHistory(String line) {
        dialogueHistory.add(line);
        if (dialogueHistory.size() > MAX_HISTORY) {
            dialogueHistory.remove(0);
        }
    }

    /**
     * Извлекает факты о игроке из его сообщений.
     */
    private void extractFacts(String message) {
        String lower = message.toLowerCase();
        if (lower.contains("\u043E\u0434\u0438\u043D") || lower.contains("alone"))
            addFact("\u0438\u0433\u0440\u043E\u043A \u0436\u0438\u0432\u0451\u0442 \u043E\u0434\u0438\u043D");
        if (lower.contains("\u043F\u0438\u0446\u0446") || lower.contains("pizza") || lower.contains("\u0435\u043B"))
            addFact("\u0438\u0433\u0440\u043E\u043A \u043B\u044E\u0431\u0438\u0442 \u043F\u0438\u0446\u0446\u0443");
        if (lower.contains("\u0434\u0440\u0443\u0433") || lower.contains("friend") || lower.contains("twixxel"))
            addFact("\u0443 \u0438\u0433\u0440\u043E\u043A\u0430 \u0435\u0441\u0442\u044C \u0434\u0440\u0443\u0437\u044C\u044F");
        if (lower.contains("\u0431\u043E\u044E\u0441\u044C") || lower.contains("scared") || lower.contains("afraid"))
            addFact("\u0438\u0433\u0440\u043E\u043A \u0431\u043E\u0438\u0442\u0441\u044F Verity");
        if (lower.contains("\u0443\u0439\u0434\u0438") || lower.contains("leave") || lower.contains("\u0443\u0445\u043E\u0434\u0438"))
            addFact("\u0438\u0433\u0440\u043E\u043A \u0445\u043E\u0447\u0435\u0442 \u0438\u0437\u0431\u0430\u0432\u0438\u0442\u044C\u0441\u044F \u043E\u0442 Verity");
    }

    private void addFact(String fact) {
        if (!knownFacts.contains(fact)) {
            knownFacts.add(fact);
            VerityMod.LOGGER.info("Verity learned: {}", fact);
        }
    }

    private String getNearestPlayerName() {
        var player = entity.level().getNearestPlayer(entity, 32.0D);
        return player != null ? player.getName().getString() : null;
    }

    public String getKnownFactsAsString() {
        if (knownFacts.isEmpty()) return "\u041F\u043E\u043A\u0430 \u043D\u0438\u0447\u0435\u0433\u043E \u043D\u0435 \u0437\u043D\u0430\u044E \u043E\u0431 \u0438\u0433\u0440\u043E\u043A\u0435.";
        return String.join(", ", knownFacts);
    }

    public List<String> getDialogueHistory() { return dialogueHistory; }

    public void setDialogueHistory(List<String> history) {
        this.dialogueHistory.clear();
        this.dialogueHistory.addAll(history);
    }

    public List<String> getKnownFactsList() { return new java.util.ArrayList<>(knownFacts); }

    public void setKnownFacts(List<String> facts) {
        this.knownFacts.clear();
        this.knownFacts.addAll(facts);
    }
}