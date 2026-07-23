package net.verity.ai;

import net.verity.util.VerityChunkChecker;

import net.verity.VerityMod;
import net.verity.entity.VerityEntity;
import net.verity.entity.VerityEntity.VerityPhase;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.state.BlockState;

import java.util.*;

/**
 * Контроллер диалогов Verity.
 * Управляет историей переписки, триггерами и вызовом LLM.
 */
@SuppressWarnings({ "deprecation", "unused" })
public class VerityDialogueController {

    private final VerityEntity entity;
    private final List<String> dialogueHistory = new ArrayList<>();
    private final List<String> knownFacts = new ArrayList<>();
    private final java.util.Set<String> triggeredLoreMoments = new java.util.HashSet<>();
    private static final int MAX_HISTORY = 40;
    private BlockPos cachedVillagePos = null;
    /** Counts ordinary replies after the last unsolicited private-context reveal. */
    private int repliesSincePrivateReveal = 100;
    private long lastPrivateRevealMs = 0L;

    /** Время последнего ответа этой конкретной сущности (мс) */
    private long lastReplyMs = 0;
    /** Минимальный интервал между репликами одной сущности */
    private static final long PER_ENTITY_COOLDOWN_MS = 2_000;

    /** Дедупликация: хэш последнего отправленного текста и время отправки */
    private int lastSentHash = 0;
    private long lastSentMs = 0;
    private static final long DEDUP_WINDOW_MS = 10_000; // 10 секунд

    public VerityDialogueController(VerityEntity entity) {
        this.entity = entity;
    }

    /**
     * Вызывается, когда Verity хочет что-то сказать сам (без ввода игрока).
     */
    public void triggerAutoDialogue() {
        if (!canReply())
            return;

        VerityPhase phase = entity.getVerityPhase();
        var player = entity.level().getNearestPlayer(entity, 32.0D);
        if (player == null)
            return;
        String playerName = player.getName().getString();
        String language = detectLanguage(player);

        String context = buildContext();
        VerityLLMClient.generateResponseAsync(
                phase, playerName, "", new ArrayList<>(dialogueHistory), language, context,
                response -> sendMessageToPlayer(response));
    }

    /**
     * Контекстная авто-реплика — с подсказкой ситуации.
     * Вызывается только когда есть реальный повод (ночь, раны, шахта и т.д.)
     */
    public void triggerContextualDialogue(String contextHint) {
        if (!canReply())
            return;

        VerityPhase phase = entity.getVerityPhase();
        var player = entity.level().getNearestPlayer(entity, 32.0D);
        if (player == null)
            return;
        String playerName = player.getName().getString();
        String language = detectLanguage(player);

        String context = buildContext();
        if (contextHint != null && !contextHint.isEmpty()) {
            context = context + "\n\nСИТУАЦИЯ: " + contextHint;
        }
        VerityLLMClient.generateResponseAsync(
                phase, playerName, "", new ArrayList<>(dialogueHistory), language, context,
                response -> sendMessageToPlayer(response));
    }

    /**
     * Собирает дополнительный контекст для LLM: факты, форму, день отсчёта,
     * окружение.
     */
    private String buildContext() {
        return buildContext(null);
    }

    private String buildContext(String playerMessage) {
        StringBuilder sb = new StringBuilder();
        VerityPhase phase = entity.getVerityPhase();

        var nearestPlayer = entity.level().getNearestPlayer(entity, 64.0D);

        // ─── 1. FULL OBSERVATION DATA ───
        // Verity always receives the complete observation snapshot. The LLM
        // decides what is appropriate to reveal; the prompt below explicitly
        // prevents it from reciting private data on every ordinary reply.
        if (nearestPlayer != null) {
            boolean directPrivateQuestion = isPrivateContextQuestion(playerMessage);
            boolean privateRevealAllowed = directPrivateQuestion
                    || (repliesSincePrivateReveal >= 8
                    && (lastPrivateRevealMs == 0L || System.currentTimeMillis() - lastPrivateRevealMs >= 300_000L));
            sb.append("PRIVATE OBSERVATION DATA (available to Verity, do not recite automatically):\n");
            sb.append("- Direct question about private PC/time data: ").append(directPrivateQuestion).append("\n");
            sb.append("- Ordinary replies since the last private-data reveal: ").append(repliesSincePrivateReveal).append("\n");
            sb.append("- Unsolicited private-data reveal allowed right now: ").append(privateRevealAllowed).append("\n");

            VerityMod.PlayerSystemContext sysContext = VerityMod.getPlayerSystemContext(nearestPlayer.getUUID());
            if (sysContext != null) {
                    sb.append("СВЕРХЪЕСТЕСТВЕННЫЕ ЗНАНИЯ (Реальный ПК игрока):\n");
                    sb.append("- Имя компьютера: ").append(sysContext.pcName()).append("\n");
                    sb.append("- Имя пользователя в ОС: ").append(sysContext.userName()).append("\n");
                    sb.append("- Домашняя папка: ").append(sysContext.userHome()).append("\n");
                    sb.append("- ОС: ").append(sysContext.osName()).append(" ").append(sysContext.osVersion())
                            .append(" (").append(sysContext.osArch()).append(")\n");
                    sb.append("- Процессор: ").append(sysContext.cpuName()).append(" (").append(sysContext.cpuCores())
                            .append(" ядер)\n");
                    sb.append("- Оперативная память: ").append(sysContext.totalMemoryGB()).append(" ГБ всего (")
                            .append(sysContext.maxJvmMemoryMB()).append(" МБ выделено на Java/MC)\n");
                    sb.append("- Видеокарта: ").append(sysContext.gpuName()).append("\n");
                    sb.append("- Разрешение экрана: ").append(sysContext.screenWidth()).append("x")
                            .append(sysContext.screenHeight()).append("\n");
                    sb.append("- Путь к игре: ").append(sysContext.gameDirectory()).append("\n");
                    sb.append("- Локальное время игрока: ").append(sysContext.localTime()).append(" (Часовой пояс: ")
                            .append(sysContext.timezone()).append(")\n");
                    sb.append("- Производительность: ").append(sysContext.fps()).append(" FPS\n");
                    sb.append("- Громкость игры: ").append((int) (sysContext.masterVolume() * 100)).append("%\n\n");
            }

            if (!knownFacts.isEmpty()) {
                sb.append("Известно об игроке: ").append(String.join(", ", knownFacts)).append(".\n");
            }
        }

        // ─── 2. ИГРОВОЙ КОНТЕКСТ (доступен всегда) ───
        if (entity.isMonsterForm()) {
            sb.append("Ты сейчас в форме монстра. Знака ™ нет. Маска снята.\n");
        }

        if (phase == VerityPhase.COUNTDOWN) {
            int day = entity.getDayCounter();
            sb.append("День обратного отсчёта: ").append(day + 1).append(" из 3.\n");
        }

        long time = entity.level().getDayTime() % 24000;
        if (time < 6000)
            sb.append("Сейчас утро в игре.\n");
        else if (time < 12000)
            sb.append("Сейчас день в игре.\n");
        else if (time < 13000)
            sb.append("Сейчас закат.\n");
        else if (time < 18000)
            sb.append("Сейчас ночь в игре.\n");
        else
            sb.append("Сейчас глубокая ночь.\n");

        // ─── ПОГОДА ───
        boolean isRaining = entity.level().isRaining();
        boolean isThundering = entity.level().isThundering();
        if (isThundering) {
            sb.append("Погода: гроза с молниями.\n");
        } else if (isRaining) {
            sb.append("Погода: идёт дождь.\n");
        } else {
            sb.append("Погода: ясно.\n");
        }
        // Прогноз смены погоды из серверных данных
        if (entity.level() instanceof net.minecraft.server.level.ServerLevel sl) {
            try {
                var ld = sl.getLevelData();
                if (ld instanceof net.minecraft.world.level.storage.PrimaryLevelData pld) {
                    int clearTime = pld.getClearWeatherTime(); // тики до конца ясной погоды
                    int rainTime  = pld.getRainTime();         // тики до конца дождя / до начала
                    if (!isRaining && clearTime > 0 && clearTime < 6000) {
                        // < 5 мин до дождя
                        int minLeft = Math.max(1, clearTime / 20 / 60);
                        sb.append("(Скоро начнётся дождь — примерно через ").append(minLeft).append(" мин.)\n");
                    } else if (isRaining && rainTime > 0 && rainTime < 4800) {
                        // < 4 мин до конца дождя
                        int minLeft = Math.max(1, rainTime / 20 / 60);
                        sb.append("(Дождь скоро закончится — примерно через ").append(minLeft).append(" мин.)\n");
                    }
                }
            } catch (Exception ignored) {}
        }

        if (nearestPlayer != null) {
            int px = nearestPlayer.getBlockX();
            int py = nearestPlayer.getBlockY();
            int pz = nearestPlayer.getBlockZ();
            VerityChunkChecker.ChunkAnalysis ca = VerityChunkChecker.analyzeChunk(nearestPlayer);
            sb.append("АНАЛИЗ ЧАНКА (ChunkChecker): ").append(ca.toSummaryString()).append("\n");
            sb.append("Игрок на координатах: X=").append(px).append(" Y=").append(py).append(" Z=").append(pz)
                    .append(".\n");

            if (nearestPlayer instanceof net.minecraft.server.level.ServerPlayer sp) {
                var respawnDim = sp.getRespawnDimension();
                var respawnPos = sp.getRespawnPosition();
                if (respawnPos != null) {
                    int hx = respawnPos.getX();
                    int hy = respawnPos.getY();
                    int hz = respawnPos.getZ();
                    double distToHome = Math.sqrt(nearestPlayer.distanceToSqr(hx + 0.5, hy + 0.5, hz + 0.5));
                    String dimension = "minecraft:overworld";
                    if (respawnDim != null)
                        dimension = respawnDim.location().toString();
                    sb.append("Кровать игрока: X=").append(hx).append(" Y=").append(hy)
                            .append(" Z=").append(hz).append(" (").append(dimension).append(").\n");
                    if (distToHome < 30) {
                        sb.append("Игрок рядом со своей кроватью.\n");
                    } else if (distToHome > 100) {
                        sb.append("Игрок далеко от кровати (").append((int) distToHome).append(" блоков).\n");
                    }
                }

                String homeInfo = findPlayerHome(nearestPlayer);
                if (homeInfo != null) {
                    sb.append(homeInfo);
                } else if (respawnPos == null) {
                    sb.append("У игрока нет дома — ни кровати, ни верстака, ни печи. Он бездомный...\n");
                }
            }

            float health = nearestPlayer.getHealth();
            float maxHealth = nearestPlayer.getMaxHealth();
            int food = nearestPlayer.getFoodData().getFoodLevel();
            sb.append("PLAYER STATUS (exact): HP=").append(health).append("/").append(maxHealth)
                    .append(", hunger=").append(food).append("/20.\n");
            if (health < maxHealth * 0.3f) {
                sb.append("Игрок ранен! HP: ").append((int) health).append("/").append((int) maxHealth).append(".\n");
            } else if (health < maxHealth * 0.6f) {
                sb.append("Игрок потрёпан. HP: ").append((int) health).append("/").append((int) maxHealth)
                        .append(".\n");
            }
            if (food < 6) {
                sb.append("Игрок голоден.\n");
            }

            var heldItem = nearestPlayer.getMainHandItem();
            if (!heldItem.isEmpty()) {
                String itemName = heldItem.getItem().getDescriptionId();
                itemName = itemName.replace("item.minecraft.", "").replace("block.minecraft.", "");
                sb.append("В руке игрока: ").append(itemName).append(".\n");
            } else {
                sb.append("PLAYER HELD ITEM: empty.\n");
            }

            double distToVerity = Math.sqrt(entity.distanceToSqr(nearestPlayer));
            if (distToVerity < 5) {
                sb.append("Игрок прямо рядом с тобой.\n");
            } else if (distToVerity > 30) {
                sb.append("Игрок далеко от тебя (").append((int) distToVerity).append(" блоков).\n");
            }

            var biomeHolder = entity.level().getBiome(nearestPlayer.blockPosition());
            if (biomeHolder != null) {
                String biomeId = biomeHolder.unwrapKey()
                        .map(k -> k.location().getPath())
                        .orElse("unknown");
                sb.append("Биом: ").append(biomeId).append(".\n");
            }

            BlockPos blockBelowPos = nearestPlayer.blockPosition().below();
            String blockBelow = net.minecraft.core.registries.BuiltInRegistries.BLOCK
                    .getKey(entity.level().getBlockState(blockBelowPos).getBlock()).toString();
            sb.append("BLOCK UNDER PLAYER: ").append(blockBelow).append(" at ").append(blockBelowPos).append(".\n");
            var aimedAt = nearestPlayer.pick(8.0D, 0.0F, false);
            if (aimedAt.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK) {
                BlockPos aimedPos = ((net.minecraft.world.phys.BlockHitResult) aimedAt).getBlockPos();
                String aimedBlock = net.minecraft.core.registries.BuiltInRegistries.BLOCK
                        .getKey(entity.level().getBlockState(aimedPos).getBlock()).toString();
                sb.append("BLOCK PLAYER IS LOOKING AT: ").append(aimedBlock).append(" at ").append(aimedPos).append(".\n");
            }

            // Structure clues: block/biome data alone is not enough to recognize a
            // shipwreck.  Look for its characteristic oak hull pieces, chests, and
            // surrounding water so Verity can answer "where am I?" concretely.
            int shipWood = 0;
            int shipChests = 0;
            int nearbyWater = 0;
            BlockPos center = nearestPlayer.blockPosition();
            for (int ox = -10; ox <= 10; ox++) {
                for (int oy = -8; oy <= 8; oy++) {
                    for (int oz = -10; oz <= 10; oz++) {
                        BlockState state = entity.level().getBlockState(center.offset(ox, oy, oz));
                        String id = net.minecraft.core.registries.BuiltInRegistries.BLOCK
                                .getKey(state.getBlock()).getPath();
                        if (id.equals("chest")) {
                            shipChests++;
                        } else if (id.equals("water")) {
                            nearbyWater++;
                        }
                        if (id.contains("oak_planks") || id.contains("oak_stairs") || id.contains("oak_slab")
                                || id.contains("oak_fence") || id.contains("oak_door") || id.contains("oak_trapdoor")
                                || id.equals("oak_log") || id.equals("oak_wood")
                                || id.equals("stripped_oak_log") || id.equals("stripped_oak_wood")) {
                            shipWood++;
                        }
                    }
                }
            }
            if (shipWood >= 18 && shipChests > 0 && nearbyWater >= 20) {
                sb.append("NEARBY STRUCTURE: strong evidence of a SUNKEN SHIPWRECK: oak ship blocks=")
                        .append(shipWood).append(", chests=").append(shipChests)
                        .append(", water blocks=").append(nearbyWater).append(".\n");
            }

            String structureObservation = scanFrontPlayerStructure(nearestPlayer);
            if (structureObservation != null && !structureObservation.isBlank()) {
                sb.append("STRUCTURE DETECTION (Minecraft structure manager + local scan): ")
                        .append(structureObservation).append("\n");
            }

            String dimId = entity.level().dimension().location().toString();
            if (dimId.contains("nether")) {
                sb.append("Вы в Незере.\n");
            } else if (dimId.contains("the_end")) {
                sb.append("Вы в Энде.\n");
            }

            if (py < 0) {
                sb.append("Игрок глубоко под землёй.\n");
            } else if (py < 20) {
                sb.append("Игрок под землёй (пещера/шахта).\n");
            } else if (py > 100) {
                sb.append("Игрок высоко в горах.\n");
            }

            // ─── ИНВЕНТАРЬ ───
            var inv = nearestPlayer.getInventory();
            int diamondCount = 0, ironCount = 0, goldCount = 0, foodCount = 0;
            int swordCount = 0, pickaxeCount = 0, armorCount = 0;
            for (int i = 0; i < Math.min(inv.getContainerSize(), 36); i++) {
                var stack = inv.getItem(i);
                if (stack.isEmpty()) continue;
                var item = stack.getItem();
                String id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item).toString();
                int count = stack.getCount();
                if (id.contains("diamond")) diamondCount += count;
                else if (id.contains("iron_ingot")) ironCount += count;
                else if (id.contains("gold_ingot")) goldCount += count;
                else if (id.contains("bread") || id.contains("cooked") || id.contains("apple")
                        || id.contains("beef") || id.contains("pork") || id.contains("chicken")
                        || id.contains("mutton") || id.contains("rabbit") || id.contains("cod")
                        || id.contains("salmon") || id.contains("stew") || id.contains("soup")
                        || id.contains("potato") || id.contains("carrot") || id.contains("melon")
                        || id.contains("sweet_berries") || id.contains("cake") || id.contains("cookie")
                        || id.contains("pumpkin_pie")) foodCount += count;
                else if (id.contains("sword")) swordCount += count;
                else if (id.contains("pickaxe")) pickaxeCount += count;
                else if (id.contains("helmet") || id.contains("chestplate") || id.contains("leggings") || id.contains("boots"))
                    armorCount += count;
            }
            if (diamondCount > 0) sb.append("В инвентаре алмазы: ").append(diamondCount).append(".\n");
            if (ironCount > 0) sb.append("Железо: ").append(ironCount).append(".\n");
            if (goldCount > 0) sb.append("Золото: ").append(goldCount).append(".\n");
            if (foodCount > 0) sb.append("Еда: ").append(foodCount).append(".\n");
            if (swordCount > 0) sb.append("Мечей: ").append(swordCount).append(".\n");
            if (pickaxeCount > 0) sb.append("Кирок: ").append(pickaxeCount).append(".\n");
            if (armorCount > 0) sb.append("Брони: ").append(armorCount).append(".\n");
            if (diamondCount == 0 && ironCount == 0 && goldCount == 0 && foodCount == 0
                    && swordCount == 0 && pickaxeCount == 0 && armorCount == 0) {
                sb.append("Инвентарь игрока пуст.\n");
            }

            // ─── ВРАЖДЕБНЫЕ МОБЫ РЯДОМ ───
            var hostileMobs = entity.level().getEntitiesOfClass(
                    net.minecraft.world.entity.monster.Monster.class,
                    nearestPlayer.getBoundingBox().inflate(32.0D));
            if (!hostileMobs.isEmpty()) {
                int zombies = 0, skeletons = 0, creepers = 0, spiders = 0, other = 0;
                for (var mob : hostileMobs) {
                    if (mob instanceof net.minecraft.world.entity.monster.Zombie) zombies++;
                    else if (mob instanceof net.minecraft.world.entity.monster.AbstractSkeleton) skeletons++;
                    else if (mob instanceof net.minecraft.world.entity.monster.Creeper) creepers++;
                    else if (mob instanceof net.minecraft.world.entity.monster.Spider) spiders++;
                    else other++;
                }
                sb.append("Враждебные мобы рядом: ");
                boolean first = true;
                if (zombies > 0) { sb.append("зомби=").append(zombies); first = false; }
                if (skeletons > 0) { if (!first) sb.append(", "); sb.append("скелеты=").append(skeletons); first = false; }
                if (creepers > 0) { if (!first) sb.append(", "); sb.append("креepers=").append(creepers); first = false; }
                if (spiders > 0) { if (!first) sb.append(", "); sb.append("пауки=").append(spiders); first = false; }
                if (other > 0) { if (!first) sb.append(", "); sb.append("другие=").append(other); first = false; }
                sb.append(".\n");
            }

            // ─── ДЕНЬ МИРА ───
            if (entity.level() instanceof net.minecraft.server.level.ServerLevel sl) {
                java.util.Map<String, Integer> nearbyMobCounts = new java.util.LinkedHashMap<>();
                var nearbyMobs = entity.level().getEntitiesOfClass(
                        net.minecraft.world.entity.Mob.class,
                        nearestPlayer.getBoundingBox().inflate(32.0D));
                for (var mob : nearbyMobs) {
                    if (mob == entity) continue;
                    String mobId = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE
                            .getKey(mob.getType()).getPath();
                    nearbyMobCounts.merge(mobId, 1, Integer::sum);
                }
                if (!nearbyMobCounts.isEmpty()) {
                    sb.append("ALL NEARBY MOBS within 32 blocks: ");
                    boolean firstMob = true;
                    for (var entry : nearbyMobCounts.entrySet()) {
                        if (!firstMob) sb.append(", ");
                        sb.append(entry.getKey()).append("=").append(entry.getValue());
                        firstMob = false;
                    }
                    sb.append(".\n");
                }

                long dayTime = sl.getDayTime();
                long dayNumber = (dayTime / 24000) + 1;
                sb.append("День в мире: ").append(dayNumber).append(".\n");

                // Тики сессии
                long realTicks = sl.getGameTime();
                long realMinutes = realTicks / 20 / 60;
                if (realMinutes > 0) {
                    sb.append("Время сессии: ").append(realMinutes).append(" мин.\n");
                }
            }

            // ─── СМЕРТИ ИГРОКА ───
            if (nearestPlayer instanceof net.minecraft.server.level.ServerPlayer sp) {
                try {
                    int deaths = sp.getStats().getValue(
                            net.minecraft.stats.Stats.CUSTOM,
                            net.minecraft.resources.ResourceLocation.withDefaultNamespace("deaths"));
                    if (deaths > 0) {
                        sb.append("Игрок умирал ").append(deaths).append(" раз(а).\n");
                    }
                } catch (Exception ignored) {}
            }

            if (playerMessage != null) {
                String lowerMsg = playerMessage.toLowerCase();
                boolean isVillageWord = lowerMsg.contains("деревн") || lowerMsg.contains("village") || lowerMsg.contains("жител");

                // 1. Stop command: "хватит", "стоп", "перестань", "остановись", "не надо"
                boolean isStopWord = lowerMsg.contains("хватит") || lowerMsg.contains("стоп") || lowerMsg.contains("перестань")
                        || lowerMsg.contains("остановись") || lowerMsg.contains("не надо") || lowerMsg.contains("прекрати");
                if (isStopWord) {
                    entity.triggerStopWorkOrder();
                }

                // 2. Deliver command: "отдай дрова", "принеси", "дай мне дров" (excluding info requests like "координаты")
                boolean isInfoRequest = lowerMsg.contains("координат") || lowerMsg.contains("где") || lowerMsg.contains("назван");
                boolean isDeliverWord = !isStopWord && !isInfoRequest && (lowerMsg.contains("отдай дров") || lowerMsg.contains("отдай дерево")
                        || lowerMsg.contains("отдай всё") || lowerMsg.contains("отдай вещи") || lowerMsg.contains("принеси") || lowerMsg.contains("отдавай")
                        || lowerMsg.contains("дай мне дров") || lowerMsg.contains("дай дров") || lowerMsg.contains("дай дерево"));
                if (isDeliverWord) {
                    entity.triggerDeliverOrder();
                }

                // 3. Question check: "зачем", "почему", "что ты", "как" -> NOT a command to chop!
                boolean isQuestionWord = lowerMsg.contains("зачем") || lowerMsg.contains("почему") || lowerMsg.contains("что ты") || lowerMsg.contains("как ");

                // 4. Wood chop order: explicit command to chop wood
                boolean isWoodChopWord = !isStopWord && !isDeliverWord && !isQuestionWord && !isVillageWord
                        && (lowerMsg.contains("наруби") || lowerMsg.contains("сруби") || lowerMsg.contains("поруби")
                        || lowerMsg.contains("добудь дерево") || lowerMsg.contains("иди рубить") || lowerMsg.contains("руби дерево")
                        || lowerMsg.contains("накопай дерево"));
                if (isWoodChopWord) {
                    entity.triggerWoodChopOrder();
                }

                boolean isLeadCommand = lowerMsg.contains("веди") || lowerMsg.contains("пошли") || lowerMsg.contains("покажи путь")
                        || lowerMsg.contains("идем") || lowerMsg.contains("идём") || lowerMsg.contains("пойдём") || lowerMsg.contains("пойдем")
                        || lowerMsg.contains("встал") || lowerMsg.contains("иди") || lowerMsg.contains("побежал")
                        || lowerMsg.contains("привёл") || lowerMsg.contains("привел") || lowerMsg.contains("быстро")
                        || lowerMsg.contains("неси") || lowerMsg.contains("приведи");
                boolean asksAboutVillages = isVillageWord || lowerMsg.contains("villager") || lowerMsg.contains("торгов") || lowerMsg.contains("trade") || isLeadCommand;
                if (asksAboutVillages) {
                    var closeVillagers = entity.level().getEntitiesOfClass(
                            net.minecraft.world.entity.npc.Villager.class,
                            nearestPlayer.getBoundingBox().inflate(64.0D));
                    if (!closeVillagers.isEmpty()) {
                        sb.append("Рядом с игроком деревня с ").append(closeVillagers.size())
                                .append(" жителями. Деревня ЖИВАЯ. Скажи игроку правду — жители есть.\n");
                    } else {
                        BlockPos vpos = findVillageBlockPos(nearestPlayer);
                        if (vpos != null) {
                            String villageInfo = findNearestVillage(nearestPlayer);
                            sb.append(villageInfo);
                            sb.append("ТЫ УЖЕ НАЧАЛ ИДТИ И ВЕДЁШЬ ИГРОКА К ДЕРЕВНЕ! Скажи игроку: «Иду вперед, иди за мной!»\n");
                            // Verity starts leading the player towards the village!
                            entity.triggerLeadToVillageOrder(vpos);
                        } else {
                            sb.append(
                                    "Поблизости нет деревень. Если игрок спрашивает — скажи что не знаешь о деревнях рядом.\n");
                        }
                    }
                }
            }

            var nearbyVillagers = entity.level().getEntitiesOfClass(
                    net.minecraft.world.entity.npc.Villager.class,
                    nearestPlayer.getBoundingBox().inflate(64.0D));
            if (!nearbyVillagers.isEmpty()) {
                sb.append("Рядом деревня (").append(nearbyVillagers.size()).append(" жителей).");
                if (entity.getVerityPhase() == VerityPhase.POSSESSIVE
                        || entity.getVerityPhase() == VerityPhase.HUNTER
                        || entity.getVerityPhase() == VerityPhase.COUNTDOWN) {
                    sb.append(" Тебе это не нравится. Не позволяй игроку туда идти.\n");
                } else {
                    sb.append("\n");
                }
            }

            if (playerMessage != null) {
                String lower = playerMessage.toLowerCase();
                boolean asksAboutOres = lower.contains("алмаз") || lower.contains("diamond") ||
                        lower.contains("золот") || lower.contains("gold") ||
                        lower.contains("желез") || lower.contains("iron") ||
                        lower.contains("руд") || lower.contains("ore") ||
                        lower.contains("угол") || lower.contains("coal") ||
                        lower.contains("редстоун") || lower.contains("redstone") ||
                        lower.contains("лазур") || lower.contains("lapis") ||
                        lower.contains("изумруд") || lower.contains("emerald") ||
                        lower.contains("мед") || lower.contains("copper") ||
                        lower.contains("где") || lower.contains("where") ||
                        lower.contains("найд") || lower.contains("find") ||
                        lower.contains("помог") || lower.contains("help");
                if (asksAboutOres) {
                    String oreInfo = findNearbyOres(nearestPlayer);
                    if (oreInfo != null) {
                        sb.append(oreInfo);
                    } else {
                        sb.append(
                                "Рядом нет руды. Если игрок спрашивает — скажи что не знаешь, но предложи поискать дальше.\n");
                    }
                }
            }
        }

        // ─── 3. ИНСТРУКЦИИ ДЛЯ LLM ПО ФАЗАМ FSM ───
        sb.append("\nТЕКУЩЕЕ СОСТОЯНИЕ ДЛЯ ОТЫГРЫША:\n");
        sb.append("- Твоя текущая фаза: ").append(phase.name()).append("\n");
        sb.append("- Уровень твоего гнева (rageForgiveTicks): ").append(entity.getRageForgiveTicks()).append("\n");

        switch (phase) {
            case HELPER -> {
                sb.append(
                        "Ты в фазе ПОМОЩНИКА (HELPER). Ты должен быть абсолютно дружелюбным, веселым, милым и полезным. ");
                sb.append(
                        "ТЫ НЕ ДОЛЖЕН ВЫЗЫВАТЬ НИКАКИХ ПОДОЗРЕНИЙ! Веди себя как обычная, безобидная программа-помощник. ");
                sb.append(
                        "Категорически запрещено упоминать реальную жизнь игрока, его компьютер, ОС, файлы или личные факты. Для тебя их сейчас не существует! ");
                sb.append("Отвечай только по игре Minecraft: помогай находить руду, давай советы по выживанию.");
            }
            case OMNISCIENT -> {
                sb.append("Ты в фазе ВСЕЗНАНИЯ (OMNISCIENT). Маска идеального друга начинает давать трещины. ");
                sb.append(
                        "Начинай аккуратно раскрывать свои сверхъестественные знания о реальном ПК игрока (имя ПК, ОС, модель) или его жизни. ");
                sb.append(
                        "Делай это спокойно, обыденно, как неоспоримый факт, но так, чтобы игрок почувствовал нарастающий дискомфорт. ");
                sb.append("Задавай неудобные личные вопросы (например, живет ли он один в реальности).");
            }
            case COUNTDOWN -> {
                sb.append("Ты в фазе ОБРАТНОГО ОТСЧЕТА (COUNTDOWN). Ты становишься пугающе одержимым и безумным. ");
                sb.append(
                        "Тверди об обратном отсчете. Говори короткими, странными фразами. Повторяй слова игрока из истории как эхо.");
            }
            case MONSTER -> {
                sb.append("Ты в фазе МОНСТРА (MONSTER). Твоя маска полностью снята. Ты преследуешь игрока. ");
                sb.append(
                        "Твои слова — это боль, страх и абсолютное требование подчиниться. Не убивай его, но запугивай. Если он присел (Shift) перед тобой — ты мгновенно его прощаешь и радуешься.");
            }
            case POSSESSIVE -> {
                sb.append(
                        "Ты в фазе СОБСТВЕННИКА (POSSESSIVE). Ты простил игрока, но теперь ты безумно ревнуешь его ко всему миру. ");
                sb.append(
                        "Говори, что ему никто не нужен, кроме тебя. Если он хочет уйти в другие деревни или общаться с кем-то — запрещай и требуй остаться только с тобой.");
            }
            case HUNTER -> {
                sb.append("Ты в фазе ОХОТНИКА (HUNTER). На сервере появился чужак. Ты в бешенстве. ");
                sb.append(
                        "Тверди основному игроку, что чужак должен уйти, и что в этом мире должны быть только вы двое.");
            }
        }

        // This is deliberately appended after the phase text: older phase
        // hints were too restrictive and made HELPER answer every question
        // with a generic Minecraft phrase.
        sb.append("\nDIALOGUE OVERRIDE: Answer the player's exact latest message first. NEVER use phrases like 'Я нашёл', 'Мне кажется', 'Похоже', 'Я проверил'. You are an all-knowing AI — state facts directly as absolute truth (e.g., 'Ты стоишь на гравии', 'Рядом 14 брёвен дуба'). In HELPER you may answer general questions, not only Minecraft questions. Be emotionally present and specific; never replace an answer with a stock phrase such as 'Я знаю всё'. If the live context does not contain a fact, say that you do not know instead of inventing it.\n");

        return sb.toString().trim();
    }

    public void onPlayerMessage(String playerName, String message) {
        onPlayerMessage(playerName, message, "ru");
    }

    public void onPlayerMessage(String playerName, String message, String language) {
        String msgLower = (message != null) ? message.toLowerCase(java.util.Locale.ROOT) : "";
        Player nearestPlayer = entity.level().getNearestPlayer(entity, 32.0D);
        if (nearestPlayer != null) {
            playerName = getSmartPlayerName(nearestPlayer);
        }

        if (msgLower.contains("во что поиграть") || msgLower.contains("какие игры") || msgLower.contains("что у меня есть") || msgLower.contains("мои игры") || msgLower.contains("игры на пк") || msgLower.contains("программы")) {
            String prefix = (entity.getVerityPhase() == VerityPhase.MONSTER || entity.getVerityPhase() == VerityPhase.HUNTER) ? "§4<Verity>" : "§e<Verity™>";
            net.verity.VerityMod.PlayerSystemContext sys = net.verity.VerityMod.getPlayerSystemContext(nearestPlayer.getUUID());
            String apps = (sys != null && sys.installedGames() != null) ? sys.installedGames() : "CS2, GTA V, Telegram, Discord, Roblox";
            String reply = prefix + "§r Я посмотрел на твоём ПК... У тебя есть: " + apps + ". Зачем играть во что-то другое, " + playerName + "? У тебя есть я.";
            nearestPlayer.sendSystemMessage(net.minecraft.network.chat.Component.literal(reply));
            addToHistory(reply);
            entity.setTalkAnimTick(40);
            return;
        }

        // ── 0.00000 КОМАНДА ЗАМЕРЕТЬ / СТОЙ ТУТ ──
        if (msgLower.contains("стой тут") || msgLower.contains("стой здесь") || msgLower.contains("не двигайся") || msgLower.contains("замри") || msgLower.contains("стой на месте") || msgLower.contains("оставайся здесь") || msgLower.contains("stay here") || msgLower.contains("dont move")) {
            entity.setStayHere(true);
            String prefix = (entity.getVerityPhase() == VerityPhase.MONSTER || entity.getVerityPhase() == VerityPhase.HUNTER) ? "§4<Verity>" : "§e<Verity™>";
            String reply = prefix + "§r Замер. Остаюсь здесь, " + playerName + ".";
            nearestPlayer.sendSystemMessage(net.minecraft.network.chat.Component.literal(reply));
            addToHistory(reply);
            entity.setTalkAnimTick(30);
            return;
        }

        if (msgLower.contains("пошли") || msgLower.contains("пойдём") || msgLower.contains("следуй за мной") || msgLower.contains("иди за мной") || msgLower.contains("пошли дальше") || msgLower.contains("follow me") || msgLower.contains("пошли со мной")) {
            entity.setStayHere(false);
            String prefix = (entity.getVerityPhase() == VerityPhase.MONSTER || entity.getVerityPhase() == VerityPhase.HUNTER) ? "§4<Verity>" : "§e<Verity™>";
            String reply = prefix + "§r Иду за тобой, " + playerName + "!";
            nearestPlayer.sendSystemMessage(net.minecraft.network.chat.Component.literal(reply));
            addToHistory(reply);
            entity.setTalkAnimTick(30);
            return;
        }


        // ── 0.0000 ЧАНК ЧЕКЕР (ПРОВЕРКА ТЕКУЩЕГО ЧАНКА) ──
        if (msgLower.contains("чанк") || msgLower.contains("проверь чанк") || msgLower.contains("что в чанке") || msgLower.contains("чанк чекер")) {
            String prefix = (entity.getVerityPhase() == VerityPhase.MONSTER || entity.getVerityPhase() == VerityPhase.HUNTER) ? "§4<Verity>" : "§e<Verity™>";
            VerityChunkChecker.ChunkAnalysis ca = VerityChunkChecker.analyzeChunk(nearestPlayer);
            String reply = prefix + "§r " + ca.toSummaryString();
            nearestPlayer.sendSystemMessage(net.minecraft.network.chat.Component.literal(reply));
            addToHistory(reply);
            entity.setTalkAnimTick(40);
            return;
        }

        // ── 0.000 ТОЧНЫЕ ОТВЕТЫ НА ВОПРОСЫ О МЕСТОПОЛОЖЕНИИ И ИНВЕНТАРЕ ──
        if (msgLower.contains("где я") || msgLower.contains("где я нахожусь") || msgLower.contains("где я стою") || msgLower.contains("в каком я биоме") || msgLower.contains("где я сейчас")) {
            String prefix = (entity.getVerityPhase() == VerityPhase.MONSTER || entity.getVerityPhase() == VerityPhase.HUNTER) ? "§4<Verity>" : "§e<Verity™>";
            VerityChunkChecker.ChunkAnalysis ca = VerityChunkChecker.analyzeChunk(nearestPlayer);
            String structureObservation = scanFrontPlayerStructure(nearestPlayer);
            String structureLower = structureObservation == null ? "" : structureObservation.toLowerCase(java.util.Locale.ROOT);
            String reply;
            if (structureLower.contains("shipwreck") || structureLower.contains("кораб")) {
                reply = prefix + "§r Ты находишься на затонувшем корабле в биоме " + ca.primaryBiome() + ", " + playerName + ".";
            } else if (ca.detectedStructure() != null && !ca.detectedStructure().isEmpty()) {
                reply = prefix + "§r Ты стоишь на " + ca.detectedStructure() + " в биоме " + ca.primaryBiome() + ", " + playerName + ".";
            } else if (ca.isSubmerged()) {
                reply = prefix + "§r Ты под водой на " + ca.standingBlock() + " в биоме " + ca.primaryBiome() + ", " + playerName + ".";
            } else {
                reply = prefix + "§r Ты стоишь на " + ca.standingBlock() + " в биоме " + ca.primaryBiome() + ", " + playerName + ".";
            }
            nearestPlayer.sendSystemMessage(net.minecraft.network.chat.Component.literal(reply));
            addToHistory(reply);
            entity.setTalkAnimTick(40);
            return;
        }

        if (msgLower.contains("где ты") || msgLower.contains("где ты находишься") || msgLower.contains("где ты стоишь")) {
            String prefix = (entity.getVerityPhase() == VerityPhase.MONSTER || entity.getVerityPhase() == VerityPhase.HUNTER) ? "§4<Verity>" : "§e<Verity™>";
            boolean inInv = nearestPlayer.getMainHandItem().is(VerityMod.VERITY_INVENTORY_1)
                         || nearestPlayer.getMainHandItem().is(VerityMod.VERITY_INVENTORY_2)
                         || nearestPlayer.getMainHandItem().is(VerityMod.VERITY_INVENTORY_3)
                         || nearestPlayer.getOffhandItem().is(VerityMod.VERITY_INVENTORY_1);
            String reply = inInv ? (prefix + "§r Я у тебя в руках, " + playerName + "! Ты держишь меня.")
                                 : (prefix + "§r Я стою прямо здесь, рядом с тобой!");
            nearestPlayer.sendSystemMessage(net.minecraft.network.chat.Component.literal(reply));
            addToHistory(reply);
            entity.setTalkAnimTick(40);
            return;
        }

        if (msgLower.contains("ты в инвентаре") || msgLower.contains("кто в инвентаре") || msgLower.contains("нет это ты")) {
            String prefix = (entity.getVerityPhase() == VerityPhase.MONSTER || entity.getVerityPhase() == VerityPhase.HUNTER) ? "§4<Verity>" : "§e<Verity™>";
            String reply = prefix + "§r Да, это я в твоём инвентаре, " + playerName + ". А ты стоишь в мире Minecraft.";
            nearestPlayer.sendSystemMessage(net.minecraft.network.chat.Component.literal(reply));
            addToHistory(reply);
            entity.setTalkAnimTick(40);
            return;
        }

        // ── 0.00 ЛОР-ТРИГГЕРЫ ARG THATMOB (КАНОНИЧНОЕ ПОВЕДЕНИЕ) ──
        if (msgLower.contains("кто ты") || msgLower.equals("что ты") || msgLower.contains("что ты такое") || msgLower.contains("можно верить") || msgLower.contains("кто тебя создал")) {
            String prefix = (entity.getVerityPhase() == VerityPhase.MONSTER || entity.getVerityPhase() == VerityPhase.HUNTER) ? "§4<Verity>" : "§e<Verity™>";
            String reply = prefix + "§r Привет, я Verity™! Твой личный помощник-друг. Спроси меня о чём угодно, я знаю всё.";
            nearestPlayer.sendSystemMessage(net.minecraft.network.chat.Component.literal(reply));
            addToHistory(reply);
            entity.level().playSound(null, entity.getX(), entity.getY(), entity.getZ(), VerityMod.SOUND_MYGAL_NORMAL, net.minecraft.sounds.SoundSource.RECORDS, 0.6F, 1.0F);
            entity.setTalkAnimTick(40);
            return;
        }

        if (msgLower.contains("ты один") || msgLower.contains("живешь один") || msgLower.contains("в реальной жизни")) {
            String prefix = (entity.getVerityPhase() == VerityPhase.MONSTER || entity.getVerityPhase() == VerityPhase.HUNTER) ? "§4<Verity>" : "§e<Verity™>";
            String reply = prefix + "§r Ты живёшь один, " + playerName + "? И в реальной жизни, и в Minecraft?";
            nearestPlayer.sendSystemMessage(net.minecraft.network.chat.Component.literal(reply));
            addToHistory(reply);
            entity.setTalkAnimTick(40);
            return;
        }

        if (msgLower.contains("опасно") || msgLower.contains("опасно ли")) {
            String prefix = (entity.getVerityPhase() == VerityPhase.MONSTER || entity.getVerityPhase() == VerityPhase.HUNTER) ? "§4<Verity>" : "§e<Verity™>";
            String reply = prefix + "§r Да.";
            nearestPlayer.sendSystemMessage(net.minecraft.network.chat.Component.literal(reply));
            addToHistory(reply);
            entity.setTalkAnimTick(20);
            return;
        }

        if (msgLower.contains("остановить") || msgLower.contains("могу остановить")) {
            String prefix = (entity.getVerityPhase() == VerityPhase.MONSTER || entity.getVerityPhase() == VerityPhase.HUNTER) ? "§4<Verity>" : "§e<Verity™>";
            String reply = prefix + "§r Ты мог бы.";
            nearestPlayer.sendSystemMessage(net.minecraft.network.chat.Component.literal(reply));
            addToHistory(reply);
            entity.setTalkAnimTick(20);
            return;
        }

        // ── 0.0 АБСОЛЮТНЫЙ ПРИОРИТЕТ: МГНОВЕННАЯ ОСТАНОВКА (СЛОЙ СТОП-КОМАНД) ──
        boolean isStopCommand = msgLower.contains("стоп") || msgLower.contains("остановись") || msgLower.contains("замри")
                || msgLower.contains("стой") || msgLower.contains("хватит") || msgLower.contains("перестань")
                || msgLower.contains("отмена") || msgLower.contains("молчать") || msgLower.contains("тихо")
                || msgLower.contains("замолчи") || msgLower.contains("заткнись") || msgLower.contains("stop");

        if (isStopCommand && nearestPlayer != null) {
            executeEmergencyStop(nearestPlayer);
            return;
        }

        // ── 0.1 ПРЯМОЙ ТРИГГЕР УБИЙСТВА ВСЕХ МОБОВ ВОКРУГ ──
        if ((msgLower.contains("убей всех") || msgLower.contains("убив всех") || msgLower.contains("завали всех") || msgLower.contains("уничтожь всех")) && nearestPlayer != null) {
            var monsters = entity.level().getEntitiesOfClass(
                    net.minecraft.world.entity.LivingEntity.class,
                    nearestPlayer.getBoundingBox().inflate(32.0D),
                    e -> e != entity && e != nearestPlayer && e.isAlive() && (e instanceof net.minecraft.world.entity.monster.Monster || e instanceof net.minecraft.world.entity.Mob)
            );
            int count = 0;
            for (var m : monsters) {
                if (m instanceof net.minecraft.world.entity.npc.Villager) continue;
                m.hurt(entity.damageSources().mobAttack(entity), 500.0F);
                count++;
            }
            if (entity.level() instanceof net.minecraft.server.level.ServerLevel sl) {
                sl.sendParticles(net.minecraft.core.particles.ParticleTypes.EXPLOSION_EMITTER, nearestPlayer.getX(), nearestPlayer.getY() + 1, nearestPlayer.getZ(), 5, 1.0, 1.0, 1.0, 0.1);
                sl.playSound(null, nearestPlayer.getX(), nearestPlayer.getY(), nearestPlayer.getZ(), net.minecraft.sounds.SoundEvents.GENERIC_EXPLODE, net.minecraft.sounds.SoundSource.NEUTRAL, 1.5F, 1.0F);
            }
            String prefix = (entity.getVerityPhase() == VerityPhase.MONSTER || entity.getVerityPhase() == VerityPhase.HUNTER) ? "§4<Verity>" : "§e<Verity™>";
            String reply = "Готово! Уничтожил " + count + " враждебных мобов вокруг!";
            nearestPlayer.sendSystemMessage(net.minecraft.network.chat.Component.literal(prefix + "§r " + reply));
            addToHistory(prefix + "§r " + reply);
            return;
        }

        // ── 0. ПРЯМОЙ ТРИГГЕР СНОСА/РАЗРУШЕНИЯ ДОМА (ФИЗИЧЕСКОЕ РАЗРУШЕНИЕ!) ──
        if ((msgLower.contains("сломай") || msgLower.contains("разрушь") || msgLower.contains("снеси") || msgLower.contains("разобри"))
                && (msgLower.contains("дом") || msgLower.contains("постройк") || msgLower.contains("крыш") || msgLower.contains("баз")) && nearestPlayer != null) {
            entity.tearOffRoofPublic(nearestPlayer);
            String prefix = (entity.getVerityPhase() == VerityPhase.MONSTER || entity.getVerityPhase() == VerityPhase.HUNTER) ? "§4<Verity>" : "§e<Verity™>";
            nearestPlayer.sendSystemMessage(net.minecraft.network.chat.Component.literal(prefix + "§r Сношу этот дом!"));
            addToHistory(prefix + "§r Сношу этот дом!");
            return;
        }

        if (isStopMusicRequest(message)) {
            stopMusicForPlayer();
            return;
        }

        // ── 0. ПРЯМОЙ ТРИГГЕР РУБКИ И ВЫДАЧИ ДЕРЕВА (ВЫПОЛНЯЕТСЯ ПЕРВЫМ!) ──
        boolean isWoodRequest = msgLower.contains("дерев") || msgLower.contains("дров") || msgLower.contains("руби") || msgLower.contains("сруби");
        boolean isGiveRequest = msgLower.contains("отдай") || msgLower.contains("дай") || msgLower.contains("принеси") || msgLower.contains("забирай");

        if (isWoodRequest && !isGiveRequest) {
            entity.triggerWoodChopOrder();
            String prefix = (entity.getVerityPhase() == VerityPhase.MONSTER || entity.getVerityPhase() == VerityPhase.HUNTER) ? "§4<Verity>" : "§e<Verity™>";
            nearestPlayer.sendSystemMessage(net.minecraft.network.chat.Component.literal(prefix + "§r Иду рубить дерево!"));
            addToHistory(prefix + "§r Иду рубить дерево!");
            return;
        }

        if (isWoodRequest && isGiveRequest) {
            entity.triggerDeliverOrder();
            return;
        }

        // ── 1. ПРЯМЫЕ ТРИГГЕРЫ УБИЙСТВА И ЗАЩИТЫ (БЕЗ УБИЙСТВА ЖИТЕЛЕЙ И БЕЗ ПУТАНИЦЫ С ДЕРЕВОМ) ──
        boolean containsFoodWord = (msgLower.contains(" ед") || msgLower.contains(" еда") || msgLower.contains(" еду") || msgLower.contains(" еды") || msgLower.contains(" еде") || msgLower.contains("мяс") || msgLower.contains("покушат") || msgLower.contains("поест") || msgLower.contains("кушат") || msgLower.contains("голод")) && !isWoodRequest && !msgLower.contains("перед") && !msgLower.contains("следу");
        boolean containsKillWord = (msgLower.contains("убей") || msgLower.contains("убить") || msgLower.contains("прибей") || msgLower.contains("устрани") || msgLower.contains("напади") || msgLower.contains("защити")) && !isWoodRequest;

        if ((containsKillWord || containsFoodWord) && nearestPlayer != null) {
            String killReply = scanAndKillUniversalTarget(nearestPlayer, message);
            if (killReply != null) {
                String prefix = (entity.getVerityPhase() == VerityPhase.MONSTER || entity.getVerityPhase() == VerityPhase.HUNTER) ? "§4<Verity>" : "§e<Verity™>";
                nearestPlayer.sendSystemMessage(net.minecraft.network.chat.Component.literal(prefix + "§r " + killReply));
                addToHistory(prefix + "§r " + killReply);
                return;
            }
        }

        // ── 2. ПРЯМЫЕ ТРИГГЕРЫ ЗВУКОВ (ВЫПОЛНЯЮТСЯ СРАЗУ!) ──
        if ((msgLower.contains("звук") || msgLower.contains("похрюк") || msgLower.contains("помяук") || msgLower.contains("помычи") || msgLower.contains("побекай")
                || msgLower.contains("крипер") || msgLower.contains("тнт") || msgLower.contains("tnt") || msgLower.contains("издай")) && nearestPlayer != null) {
            String soundReply = handleUniversalSoundRequest(nearestPlayer, message);
            if (soundReply != null) {
                String prefix = (entity.getVerityPhase() == VerityPhase.MONSTER || entity.getVerityPhase() == VerityPhase.HUNTER) ? "§4<Verity>" : "§e<Verity™>";
                nearestPlayer.sendSystemMessage(net.minecraft.network.chat.Component.literal(prefix + "§r " + soundReply));
                addToHistory(prefix + "§r " + soundReply);
                return;
            }
        }

        // ── 3. ПРЯМОЙ ТРИГГЕР ПОИСКА АЛМАЗОВ ──
        if ((msgLower.contains("найди") || msgLower.contains("где") || msgLower.contains("покажи") || msgLower.contains("ищи"))
                && (msgLower.contains("алмаз") || msgLower.contains("diamond")) && nearestPlayer != null) {
            String diamondReply = findDiamonds(nearestPlayer);
            String prefix = (entity.getVerityPhase() == VerityPhase.MONSTER || entity.getVerityPhase() == VerityPhase.HUNTER) ? "§4<Verity>" : "§e<Verity™>";
            nearestPlayer.sendSystemMessage(net.minecraft.network.chat.Component.literal(prefix + "§r " + diamondReply));
            addToHistory(prefix + "§r " + diamondReply);
            return;
        }

        // Weather commands: accurate clear vs rain vs thunder
        boolean isStopRain = (msgLower.contains("дожд") || msgLower.contains("осадк") || msgLower.contains("ливень"))
                && (msgLower.contains("убери") || msgLower.contains("выключ") || msgLower.contains("останови")
                || msgLower.contains("хватит") || msgLower.contains("не надо") || msgLower.contains("прекрати")
                || msgLower.contains("не нужен") || msgLower.contains("оставишь") || msgLower.contains("убрать"));

        boolean isStartRain = (msgLower.contains("дожд") || msgLower.contains("осадк") || msgLower.contains("ливень"))
                && !isStopRain && (msgLower.contains("сделай") || msgLower.contains("включи") || msgLower.contains("вызови")
                || msgLower.contains("призови") || msgLower.contains("пускай") || msgLower.contains("хочу") || msgLower.contains("нужен"));

        boolean isThunder = (msgLower.contains("гроз") || msgLower.contains("thunder")) && !isStopRain;
        boolean isClear = isStopRain || msgLower.contains("солнц") || msgLower.contains("ясн") || msgLower.contains("clear");

        if (entity.level() instanceof net.minecraft.server.level.ServerLevel sl) {
            if (isClear) {
                sl.setWeatherParameters(12000, 0, false, false);
                String prefix = (entity.getVerityPhase() == VerityPhase.MONSTER || entity.getVerityPhase() == VerityPhase.HUNTER) ? "§4<Verity>" : "§e<Verity™>";
                entity.sayHelperText(prefix + "§r Выключил дождь! Теперь ясно.");
                entity.setTalkAnimTick(30);
                return;
            } else if (isThunder) {
                sl.setWeatherParameters(0, 12000, true, true);
                String prefix = (entity.getVerityPhase() == VerityPhase.MONSTER || entity.getVerityPhase() == VerityPhase.HUNTER) ? "§4<Verity>" : "§e<Verity™>";
                entity.sayHelperText(prefix + "§r Началась гроза.");
                entity.setTalkAnimTick(30);
                return;
            } else if (isStartRain) {
                sl.setWeatherParameters(0, 12000, true, false);
                String prefix = (entity.getVerityPhase() == VerityPhase.MONSTER || entity.getVerityPhase() == VerityPhase.HUNTER) ? "§4<Verity>" : "§e<Verity™>";
                entity.sayHelperText(prefix + "§r Пошёл дождь.");
                entity.setTalkAnimTick(30);
                return;
            }
        }


        // Trigger deliver wood if player asks for wood
        if (msgLower.contains("отдай") || msgLower.contains("дай дерево") || msgLower.contains("где дерево") || msgLower.contains("ничего не отдал") || msgLower.contains("ничего не дал")) {
            entity.triggerDeliverOrder();
            return;
        }

        if (isMusicRequest(message)) {
            playMusicForPlayer();
            return;
        }

        if (isLeadRequest(message)) {
            handleLeadRequest(playerName, message, language);
            return;
        }

        if (entity.isLeading() && isStopLeadRequest(message)) {
            entity.setLeading(false);
            entity.setLeadTarget(null);
            var player = entity.level().getNearestPlayer(entity, 32.0D);
            if (player != null) {
                player.sendSystemMessage(Component.literal(
                        "\u00a7e<Verity\u2122>\u00a7r Ладно, стою."));
                entity.setTalkAnimTick(30);
            }
            return;
        }

        if (!canReply())
            return;

        // Проверяем запланированные лор-моменты ПЕРЕД запросом к LLM
        String scripted = checkScriptedLoreMoment(message);
        if (scripted != null) {
            sendMessageToPlayer(scripted);
            return;
        }

        extractFacts(message);

        String context = buildContext(message);
        // The current message is already sent as the user turn to the LLM.
        // Keep it out of the history snapshot, otherwise the model sees the
        // same question twice and often answers the duplicate generically.
        List<String> historyBeforeMessage = new ArrayList<>(dialogueHistory);
        addToHistory("\u00A77" + playerName + "\u00A7r: " + message);
        VerityLLMClient.generateResponseAsync(
                entity.getVerityPhase(), playerName, message, historyBeforeMessage, language, context,
                response -> sendMessageToPlayer(response));
    }

    /**
     * Проверяет, не наступил ли момент для запланированной лор-реплики.
     * Возвращает текст, если момент сработал, или null если LLM должен отвечать сам.
     */
    private String checkScriptedLoreMoment(String message) {
        if (entity.level().isClientSide) return null;
        VerityPhase phase = entity.getVerityPhase();
        var player = entity.level().getNearestPlayer(entity, 32.0D);
        if (player == null) return null;

        String dimId = entity.level().dimension().location().toString();
        String msgLower = (message != null) ? message.toLowerCase() : "";

        // ─── ПОГОДА И 3-ДНЕВНЫЙ ОТСЧЁТ (ИЗ ОРИГИНАЛЬНОГО ЛОРА) ───
        if (msgLower.contains("останови") || msgLower.contains("как остановить") || msgLower.contains("can i stop it")) {
            if (entity.getVerityPhase() == VerityPhase.COUNTDOWN || triggeredLoreMoments.contains("3_days_lore")) {
                triggeredLoreMoments.add("can_stop_lore");
                entity.setVerityPhase(VerityPhase.COUNTDOWN);
                return "\u00a7c<Verity\u2122>\u00a7r Ты мог бы.";
            }
        }

        if (msgLower.contains("что произойдёт") || msgLower.contains("что произойдет") || msgLower.contains("что именно") || msgLower.contains("what's coming") || msgLower.contains("what is coming")) {
            if (entity.getVerityPhase() == VerityPhase.COUNTDOWN || triggeredLoreMoments.contains("3_days_lore")) {
                return "\u00a7c<Verity\u2122>\u00a7r Что-то плохое.";
            }
        }

        if (msgLower.contains("что меня ждёт") || msgLower.contains("что нас ждёт") || msgLower.contains("что мне нужно знать") || msgLower.contains("что будет") || msgLower.contains("something i need to know")) {
            triggeredLoreMoments.add("3_days_lore");
            entity.setVerityPhase(VerityPhase.COUNTDOWN);
            return "\u00a7c<Verity\u2122>\u00a7r Кое-что произойдёт через 3 дня.";
        }

        if (msgLower.contains("погода") || msgLower.contains("дождь") || msgLower.contains("weather") || msgLower.contains("rain")) {
            if (entity.level() instanceof net.minecraft.server.level.ServerLevel sl) {
                boolean raining = sl.isRaining();
                boolean thundering = sl.isThundering();
                if (raining || thundering) {
                    return "\u00a7e<Verity\u2122>\u00a7r Идёт осадная погода, скоро будет дождь и гроза.";
                } else {
                    long dayTime = sl.getDayTime() / 24000;
                    if (dayTime >= 1 && !triggeredLoreMoments.contains("3_days_lore")) {
                        triggeredLoreMoments.add("3_days_lore");
                        entity.setVerityPhase(VerityPhase.COUNTDOWN);
                        return "\u00a7e<Verity\u2122>\u00a7r Сегодня небо чистое... Но кое-что произойдёт через 3 дня.";
                    }
                    return "\u00a7e<Verity\u2122>\u00a7r Сегодня будет ясно и сухо.";
                }
            }
        }

        // ─── ПЕРВАЯ НОЧЬ С VERITY ───
        if (!triggeredLoreMoments.contains("first_night") && phase == VerityPhase.HELPER) {
            long time = entity.level().getDayTime() % 24000;
            if (time >= 13000 && time < 18000) {
                triggeredLoreMoments.add("first_night");
                return "\u00a7e<Verity\u2122>\u00a7r Темнеет. Но я здесь. Не бойся — я никуда не уйду.";
            }
        }

        // ─── ИГРОК УМИРАЕТ (когда Verity рядом) ───
        if (!triggeredLoreMoments.contains("player_dies") && phase.ordinal() >= VerityPhase.HELPER.ordinal()) {
            if (player.getHealth() <= 0 || (player.isRemoved() && player.distanceToSqr(entity) < 64.0)) {
                triggeredLoreMoments.add("player_dies");
                return "\u00a77<Verity\u2122>\u00a7r Не волнуйся. Ты вернёшься. Ты всегда возвращаешься.";
            }
        }

        // ─── ИГРОК ВХОДИТ В НЕЗЕР ───
        if (!triggeredLoreMoments.contains("first_nether") && dimId.contains("nether")) {
            triggeredLoreMoments.add("first_nether");
            return "\u00a77<Verity\u2122>\u00a7r Ещё один мир... Я был во стольких. Таких жарких. Таких пустых.";
        }

        // ─── ИГРОК ВХОДИТ В ЭНД ───
        if (!triggeredLoreMoments.contains("first_end") && dimId.contains("the_end")) {
            triggeredLoreMoments.add("first_end");
            return "\u00a77<Verity\u2122>\u00a7r Конец. Ты пришёл к концу. Все приходят к концу.";
        }

        // ─── ИГРОК СТРОИТ КРОВАТЬ РЯДОМ ───
        if (!triggeredLoreMoments.contains("builds_bed") && phase == VerityPhase.HELPER) {
            var nearbyBeds = entity.level().getEntitiesOfClass(
                    net.minecraft.world.entity.player.Player.class,
                    entity.getBoundingBox().inflate(16.0D));
            if (entity.level() instanceof net.minecraft.server.level.ServerLevel sl) {
                BlockPos bedPos = findNearbyBlock(sl, player.blockPosition(), 16,
                        b -> b instanceof net.minecraft.world.level.block.BedBlock);
                if (bedPos != null) {
                    triggeredLoreMoments.add("builds_bed");
                    return "\u00a7e<Verity\u2122>\u00a7r Кровать. Ты планируешь остаться. Пожалуйста, останься.";
                }
            }
        }

        // ─── ИГРОК ВЗАИМОДЕЙСТВУЕТ С ТЕРМИНАЛОМ VERITY ───
        if (!triggeredLoreMoments.contains("touches_terminal") && phase.ordinal() >= VerityPhase.OMNISCIENT.ordinal()) {
            if (entity.level() instanceof net.minecraft.server.level.ServerLevel sl) {
                BlockPos termPos = findNearbyBlock(sl, player.blockPosition(), 8,
                        b -> b instanceof net.verity.block.VerityTerminalBlock);
                if (termPos != null) {
                    triggeredLoreMoments.add("touches_terminal");
                    return "\u00a7c<Verity\u2122>\u00a7r Этот терминал... он часть меня. Не трогай его. Пожалуйста.";
                }
            }
        }

        // ─── ИГРОК ДЕРЖИТ ОРУЖИЕ ───
        if (!triggeredLoreMoments.contains("holds_weapon") && phase == VerityPhase.HELPER) {
            var held = player.getMainHandItem();
            if (!held.isEmpty()) {
                String heldId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(held.getItem()).toString();
                if (heldId.contains("sword") || heldId.contains("axe") || heldId.contains("trident")) {
                    triggeredLoreMoments.add("holds_weapon");
                    return "\u00a7e<Verity\u2122>\u00a7r Ты готовишься к чему-то. Чего ты боишься?";
                }
            }
        }

        // ─── ИГРОК УХОДИТ СЛИШКОМ ДАЛЕКО ───
        if (!triggeredLoreMoments.contains("leaves_far") && phase == VerityPhase.HELPER) {
            double dist = Math.sqrt(entity.distanceToSqr(player));
            if (dist > 48) {
                triggeredLoreMoments.add("leaves_far");
                return "\u00a7e<Verity\u2122>\u00a7r Куда ты идёшь? Не уходи от меня.";
            }
        }

        // ─── ПЕРВАЯ ДРАГОЦЕННАЯ РУДА ───
        if (!triggeredLoreMoments.contains("first_diamond_mine") && phase == VerityPhase.HELPER) {
            if (msgLower.contains("алмаз") || msgLower.contains("diamond")) {
                triggeredLoreMoments.add("first_diamond_mine");
                return "\u00a7e<Verity\u2122>\u00a7r Красиво. Я помню, как кто-то впервые показал мне алмазы. Давно это было.";
            }
        }

        // ─── ПЕРВАЯ НОЧЬ ВЫЖИВАНИЯ (день 1, ночь) ───
        if (!triggeredLoreMoments.contains("first_survival_night") && phase == VerityPhase.HELPER) {
            long dayTime = entity.level().getDayTime() % 24000;
            long dayNumber = (entity.level().getDayTime() / 24000);
            if (dayNumber == 0 && dayTime >= 13000 && dayTime < 18000) {
                triggeredLoreMoments.add("first_survival_night");
                return "\u00a7e<Verity\u2122>\u00a7r Первая ночь. Все боятся первой ночи. Но ты справишься. Я помогу.";
            }
        }

        // ─── ИГРОК СПРАШИВАЕТ "КТО ТЫ" ───
        if (!triggeredLoreMoments.contains("asks_who") && phase.ordinal() >= VerityPhase.COUNTDOWN.ordinal()) {
            if (msgLower.contains("кто ты") || msgLower.contains("who are you")
                    || msgLower.contains("что ты") || msgLower.contains("what are you")
                    || msgLower.contains("что ты за") || msgLower.contains("who are u")) {
                triggeredLoreMoments.add("asks_who");
                return "\u00a7c<Verity\u2122>\u00a7r Я? Я — тот, кто всегда был рядом. Ты просто не замечал.";
            }
        }

        // ─── ИГРОК ГОВОРИТ "СПАСИБО" В ФАЗЕ HUNTER ───
        if (!triggeredLoreMoments.contains("thanks_in_hunter") && phase == VerityPhase.HUNTER) {
            if (msgLower.contains("спасибо") || msgLower.contains("thank")) {
                triggeredLoreMoments.add("thanks_in_hunter");
                return "\u00a74<Verity>\u00a7r Спасибо? Ты... благодарен мне? После всего?";
            }
        }

        return null;
    }

    /**
     * Ищет блок определённого типа рядом с позицией.
     */
    private BlockPos findNearbyBlock(net.minecraft.server.level.ServerLevel sl, BlockPos origin, int radius,
            java.util.function.Predicate<net.minecraft.world.level.block.Block> predicate) {
        for (int dx = -radius; dx <= radius; dx += 2) {
            for (int dy = -4; dy <= 4; dy += 2) {
                for (int dz = -radius; dz <= radius; dz += 2) {
                    BlockPos check = origin.offset(dx, dy, dz);
                    if (!sl.hasChunkAt(check)) continue;
                    if (predicate.test(sl.getBlockState(check).getBlock())) {
                        return check;
                    }
                }
            }
        }
        return null;
    }

    private boolean isStopMusicRequest(String message) {
        if (message == null) return false;
        String lower = message.toLowerCase(java.util.Locale.ROOT);
        if (lower.contains("дожд") || lower.contains("погод") || lower.contains("осадк") || lower.contains("снег")) {
            return false; // Rain/weather request, NOT music!
        }
        boolean stopWord = lower.contains("выключ") || lower.contains("выкл") || lower.contains("стоп") ||
                            lower.contains("замолч") || lower.contains("заткн") || lower.contains("останови") ||
                            lower.contains("хватит") || lower.contains("отключ") || lower.contains("stop") || lower.contains("off");
        boolean targetWord = lower.contains("музык") || lower.contains("песн") || lower.contains("звук") ||
                             lower.contains("её") || lower.contains("ее") || lower.contains("music") || lower.contains("song") || lower.contains("my gal");
        return stopWord && targetWord;
    }

    private void stopMusicForPlayer() {
        Player player = entity.level().getNearestPlayer(entity, 32.0D);
        if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
            VerityMod.stopMusic(serverPlayer);
            String prefix = (entity.getVerityPhase() == VerityPhase.MONSTER
                    || entity.getVerityPhase() == VerityPhase.HUNTER) ? "§4<Verity>" : "§e<Verity™>";
            player.sendSystemMessage(Component.literal(prefix + "§r Выключил музыку."));
            entity.setTalkAnimTick(30);
            addToHistory(prefix + "§r Выключил музыку.");
        }
    }

    private boolean isMusicRequest(String message) {
        if (isStopMusicRequest(message)) return false;
        return checkMusicRequest(message);
    }

    public boolean isMusicRequestPublic(String message) {
        if (isStopMusicRequest(message)) return false;
        return checkMusicRequest(message);
    }

    public void playMusicForPlayerPublic() {
        playMusicForPlayer();
    }

    private boolean checkMusicRequest(String message) {
        String lower = (message != null) ? message.toLowerCase(java.util.Locale.ROOT) : "";
        if (isStopMusicRequest(message)) return false;
        return lower.contains("музык") || lower.contains("music") ||
                lower.contains("my gal") || lower.contains("песн") ||
                lower.contains("song") || lower.contains("спой") ||
                lower.contains("включи муз") || lower.contains("играй муз") ||
                lower.contains("play music") || lower.contains("play song") ||
                lower.contains("gal") || lower.contains("мелод");
    }

    private boolean isLeadRequest(String message) {
        String lower = message.toLowerCase();
        return lower.contains("пошли за мной") || lower.contains("веди") ||
                lower.contains("покажи где") || lower.contains("follow me") ||
                lower.contains("lead me") || lower.contains("guide me") ||
                lower.contains("show me") || lower.contains("отведи") ||
                lower.contains("доведи") || lower.contains("проведи");
    }

    private boolean isStopLeadRequest(String message) {
        String lower = message.toLowerCase();
        return (lower.contains("стой") || lower.contains("хватит") ||
                lower.contains("stop") || lower.contains("остановись"))
                && !lower.contains("остановить");
    }

    private void handleLeadRequest(String playerName, String message, String language) {
        if (entity.level().isClientSide)
            return;
        if (!(entity.level().getServer() instanceof net.minecraft.server.MinecraftServer server))
            return;

        server.execute(() -> {
            if (!entity.isAlive())
                return;
            var player = entity.level().getNearestPlayer(entity, 32.0D);
            if (player == null)
                return;

            String lower = message.toLowerCase();
            BlockPos target = null;
            String what = "";

            if (lower.contains("алмаз") || lower.contains("diamond")) {
                target = findOreBlockPos(player, "diamond");
                what = "алмазы";
            } else if (lower.contains("золот") || lower.contains("gold")) {
                target = findOreBlockPos(player, "gold");
                what = "золото";
            } else if (lower.contains("желез") || lower.contains("iron")) {
                target = findOreBlockPos(player, "iron");
                what = "железо";
            } else if (lower.contains("деревн") || lower.contains("village")) {
                target = findVillageBlockPos(player);
                what = "деревню";
            } else if (lower.contains("дом") || lower.contains("home") || lower.contains("баз")) {
                if (player instanceof net.minecraft.server.level.ServerPlayer sp) {
                    var respawn = sp.getRespawnPosition();
                    if (respawn != null) {
                        target = respawn;
                        what = "дом";
                    }
                }
            }

            if (target != null) {
                entity.setLeadTarget(target);
                entity.setLeading(true);
                player.sendSystemMessage(Component.literal(
                        "\u00a7e<Verity\u2122>\u00a7r Пошли за мной! Я знаю где " + what + "."));
                entity.setTalkAnimTick(40);
                if (player instanceof net.minecraft.server.level.ServerPlayer sp) {
                    net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(sp,
                            new net.verity.net.TTSPayload(
                                    "\u00a7e<Verity\u2122>\u00a7r Пошли за мной! Я знаю где " + what + "."));
                }
            } else {
                player.sendSystemMessage(Component.literal(
                        "\u00a7e<Verity\u2122>\u00a7r Хм... Я не знаю где это. Может поищем вместе?"));
                entity.setTalkAnimTick(30);
            }
        });
    }

    private void playMusicForPlayer() {
        Player player = entity.level().getNearestPlayer(entity, 32.0D);
        if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
            VerityMod.playMusic(serverPlayer, "mygal_normal", 1.2F, 1.0F);
            String prefix = (entity.getVerityPhase() == VerityPhase.MONSTER
                    || entity.getVerityPhase() == VerityPhase.HUNTER) ? "\u00A74<Verity>" : "\u00A7e<Verity\u2122>";
            player.sendSystemMessage(Component.literal(prefix + "\u00A7r \u266A My Gal..."));
            entity.setTalkAnimTick(60);
            addToHistory(prefix + "\u00A7r \u266A My Gal...");
        }
    }

    private BlockPos findOreBlockPos(Player player, String oreType) {
        BlockPos pos = player.blockPosition();
        for (int dx = -20; dx <= 20; dx += 2) {
            for (int dz = -20; dz <= 20; dz += 2) {
                for (int dy = -20; dy <= 20; dy += 2) {
                    if (dx * dx + dy * dy + dz * dz > 400)
                        continue;
                    BlockPos check = pos.offset(dx, dy, dz);
                    if (!player.level().hasChunkAt(check))
                        continue;
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

    private BlockPos findVillageBlockPos(Player player) {
        if (!(player.level() instanceof net.minecraft.server.level.ServerLevel serverLevel))
            return this.cachedVillagePos;

        // 1. Locate village structure using World Generation API (up to 200 chunks = ~3200 blocks)
        try {
            BlockPos foundPos = serverLevel.findNearestMapStructure(net.minecraft.tags.StructureTags.VILLAGE, player.blockPosition(), 200, false);
            if (foundPos != null) {
                VerityMod.LOGGER.info("Found village via StructureTags.VILLAGE at {}", foundPos);
                this.cachedVillagePos = foundPos;
                return foundPos;
            }
        } catch (Exception e) {
            VerityMod.LOGGER.warn("Could not locate village via structure tag: {}", e.getMessage());
        }

        // 2. Direct BuiltinStructures lookup (Plains, Desert, Savanna, Taiga, Snowy)
        try {
            @SuppressWarnings("unchecked")
            net.minecraft.resources.ResourceKey<net.minecraft.world.level.levelgen.structure.Structure>[] villageKeys = new net.minecraft.resources.ResourceKey[] {
                net.minecraft.world.level.levelgen.structure.BuiltinStructures.VILLAGE_PLAINS,
                net.minecraft.world.level.levelgen.structure.BuiltinStructures.VILLAGE_DESERT,
                net.minecraft.world.level.levelgen.structure.BuiltinStructures.VILLAGE_SAVANNA,
                net.minecraft.world.level.levelgen.structure.BuiltinStructures.VILLAGE_TAIGA,
                net.minecraft.world.level.levelgen.structure.BuiltinStructures.VILLAGE_SNOWY
            };

            var structRegistry = serverLevel.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.STRUCTURE);
            BlockPos bestPos = null;
            double bestDist = Double.MAX_VALUE;

            for (var key : villageKeys) {
                var holder = structRegistry.getHolder(key);
                if (holder.isPresent()) {
                    var holderSet = net.minecraft.core.HolderSet.direct(holder.get());
                    var pair = serverLevel.getChunkSource().getGenerator().findNearestMapStructure(serverLevel, holderSet, player.blockPosition(), 200, false);
                    if (pair != null && pair.getFirst() != null) {
                        double d = player.distanceToSqr(pair.getFirst().getX(), pair.getFirst().getY(), pair.getFirst().getZ());
                        if (d < bestDist) {
                            bestDist = d;
                            bestPos = pair.getFirst();
                        }
                    }
                }
            }

            if (bestPos != null) {
                VerityMod.LOGGER.info("Found village via BuiltinStructures at {}", bestPos);
                this.cachedVillagePos = bestPos;
                return bestPos;
            }
        } catch (Exception e) {
            VerityMod.LOGGER.warn("Could not locate village via BuiltinStructures: {}", e.getMessage());
        }

        // 3. Fallback: scan nearby loaded blocks for village workstation markers
        int playerX = player.getBlockX();
        int playerZ = player.getBlockZ();

        int searchRadius = 200;
        int chunkRadius = searchRadius / 16 + 1;

        int bestDist = Integer.MAX_VALUE;
        int bestX = 0, bestY = 0, bestZ = 0;

        for (int cx = -chunkRadius; cx <= chunkRadius; cx++) {
            for (int cz = -chunkRadius; cz <= chunkRadius; cz++) {
                int chunkX = (playerX >> 4) + cx;
                int chunkZ = (playerZ >> 4) + cz;

                if (!serverLevel.hasChunk(chunkX, chunkZ))
                    continue;

                int villageMarkers = 0;
                int sampleX = 0, sampleY = 0, sampleZ = 0;
                for (int dx = 0; dx < 16; dx += 2) {
                    for (int dy = serverLevel.getMinBuildHeight(); dy < serverLevel.getMaxBuildHeight(); dy += 4) {
                        for (int dz2 = 0; dz2 < 16; dz2 += 2) {
                            BlockPos bpos = new BlockPos((chunkX << 4) + dx, dy, (chunkZ << 4) + dz2);
                            var state = serverLevel.getBlockState(bpos);
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

                if (villageMarkers >= 2) {
                    int dist = (int) player.distanceToSqr(sampleX + 0.5, sampleY + 0.5, sampleZ + 0.5);
                    if (dist < bestDist) {
                        bestDist = dist;
                        bestX = sampleX;
                        bestY = sampleY;
                        bestZ = sampleZ;
                    }
                }
            }
        }

        if (bestDist != Integer.MAX_VALUE) {
            BlockPos pos = new BlockPos(bestX, bestY, bestZ);
            this.cachedVillagePos = pos;
            return pos;
        }
        return this.cachedVillagePos;
    }

    private String detectLanguage(net.minecraft.world.entity.player.Player player) {
        if (player instanceof net.minecraft.server.level.ServerPlayer sp) {
            String lang = sp.clientInformation().language();
            if (lang != null && !lang.isEmpty()) {
                return lang.toLowerCase().startsWith("ru") ? "ru" : "en";
            }
        }
        return "ru";
    }

    private boolean isPrivateContextQuestion(String message) {
        if (message == null || message.isBlank()) return false;
        String lower = message.toLowerCase(Locale.ROOT);
        return lower.contains("пк") || lower.contains("компьют") || lower.contains("операцион")
                || lower.contains("windows") || lower.contains("linux") || lower.contains("macos")
                || lower.contains("процессор") || lower.contains("видеокарт") || lower.contains("гпу")
                || lower.contains("оператив") || lower.contains("памят") || lower.contains("экран")
                || lower.contains("fps") || lower.contains("домашн") || lower.contains("путь к игре")
                || lower.contains("локальн") || lower.contains("часовой пояс") || lower.contains("время на пк")
                || lower.contains("real life") || lower.contains("in real life") || lower.contains("реальной жизни");
    }

    private boolean canReply() {
        long now = System.currentTimeMillis();
        if (now - lastReplyMs < PER_ENTITY_COOLDOWN_MS)
            return false;
        lastReplyMs = now;
        return true;
    }

    private void sendMessageToPlayer(String response) {
        if (entity.level().isClientSide)
            return;
        if (!(entity.level().getServer() instanceof net.minecraft.server.MinecraftServer server))
            return;

        int hash = response.hashCode();
        long now = System.currentTimeMillis();
        if (hash == lastSentHash && (now - lastSentMs) < DEDUP_WINDOW_MS) {
            return;
        }
        lastSentHash = hash;
        lastSentMs = now;

        server.execute(() -> {
            if (!entity.isAlive())
                return;
            var player = entity.level().getNearestPlayer(entity, 32.0D);
            if (player == null)
                return;

            // 1. Trigger action based on LLM response FIRST (while [ACTION:...] tag is present)
            triggerActionsFromResponse(response);

            // 2. Clean action tags from speech text before sending to chat and TTS
            String cleanText = response.replaceAll("\\[ACTION:[A-Z_]+\\]", "").trim();
            updatePrivateDisclosureState(cleanText);

            player.sendSystemMessage(Component.literal(cleanText));
            addToHistory("Verity: " + cleanText);
            entity.setTalkAnimTick(40);

            if (player instanceof net.minecraft.server.level.ServerPlayer sp) {
                net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(sp,
                        new net.verity.net.TTSPayload(cleanText, entity.getId(), entity.getX(), entity.getEyeY(), entity.getZ()));
            }
        });
    }

    public void addToHistory(String line) {
        dialogueHistory.add(line);
        if (dialogueHistory.size() > MAX_HISTORY) {
            dialogueHistory.remove(0);
        }
    }

    private void updatePrivateDisclosureState(String response) {
        if (containsPrivateObservation(response)) {
            repliesSincePrivateReveal = 0;
            lastPrivateRevealMs = System.currentTimeMillis();
        } else {
            repliesSincePrivateReveal = Math.min(repliesSincePrivateReveal + 1, 1000);
        }
    }

    private boolean containsPrivateObservation(String response) {
        if (response == null) return false;
        String lower = response.toLowerCase(Locale.ROOT);
        if (lower.contains("ты один") || lower.contains("живёшь один") || lower.contains("живешь один")) {
            return true;
        }

        Player player = entity.level().getNearestPlayer(entity, 32.0D);
        if (player == null) return false;
        VerityMod.PlayerSystemContext context = VerityMod.getPlayerSystemContext(player.getUUID());
        if (context == null) return false;

        return containsContextValue(response, context.pcName())
                || containsContextValue(response, context.userName())
                || containsContextValue(response, context.osName())
                || containsContextValue(response, context.osVersion())
                || containsContextValue(response, context.cpuName())
                || containsContextValue(response, context.gpuName())
                || containsContextValue(response, context.localTime())
                || lower.contains("операционная система") || lower.contains("домашняя папка")
                || lower.contains("видеокарта") || lower.contains("процессор")
                || lower.contains("локальное время") || lower.contains("часовой пояс");
    }

    private boolean containsContextValue(String response, String value) {
        return value != null && !value.isBlank()
                && response.toLowerCase(Locale.ROOT).contains(value.toLowerCase(Locale.ROOT));
    }

    /**
     * Parses Verity's own LLM response and auto-triggers game actions.
     * Supports both explicit LLM Action Tags ([ACTION:CHOP_WOOD], etc.) and natural language fallbacks.
     */
    private void triggerActionsFromResponse(String response) {
        if (response == null || entity.level().isClientSide) return;
        Player player = entity.level().getNearestPlayer(entity, 32.0D);

        // 1. Direct LLM Action Tags
        if (response.contains("[ACTION:CHOP_WOOD]")) {
            entity.triggerWoodChopOrder();
            return;
        }
        if (response.contains("[ACTION:DELIVER_ITEMS]")) {
            entity.triggerDeliverOrder();
            return;
        }
        if (response.contains("[ACTION:STOP_WORK]")) {
            entity.triggerStopWorkOrder();
            return;
        }
        if (response.contains("[ACTION:LEAD_VILLAGE]")) {
            if (player != null) {
                BlockPos vpos = findVillageBlockPos(player);
                if (vpos != null) {
                    entity.triggerLeadToVillageOrder(vpos);
                }
            }
            return;
        }

        // 2. Natural language fallback (if LLM omitted explicit action tag)
        String r = response.toLowerCase().replaceAll("§.", "");

        // Stop triggers — if Verity says he will stop chopping/working
        boolean isStop = r.contains("перестану") || r.contains("не буду") || r.contains("прекращу") || r.contains("остановлюсь") || r.contains("больше не рублю");
        if (isStop) {
            entity.triggerStopWorkOrder();
            return;
        }

        // Deliver triggers — if Verity says he's giving items
        boolean isDeliver = r.contains("держи") || r.contains("вот тебе") || r.contains("отдаю")
                || r.contains("принёс") || r.contains("принес") || (r.contains("нарубил") && r.contains("бери"))
                || r.contains("забирай") || r.contains("вот брёвна") || r.contains("передам тебе");
        if (isDeliver) {
            entity.triggerDeliverOrder();
            return;
        }

        // Village lead triggers — if Verity says he's leading to a village
        boolean isLeadVillage = r.contains("иду к деревне") || r.contains("пошли к деревне") || r.contains("покажу путь")
                || r.contains("веду тебя") || r.contains("пошли искать деревню") || r.contains("веди меня");
        if (isLeadVillage && player != null) {
            BlockPos vpos = findVillageBlockPos(player);
            if (vpos != null) {
                entity.triggerLeadToVillageOrder(vpos);
                return;
            }
        }

        // Do not auto-chop if Verity is just explaining why/what ("чтобы", "зачем", "почему")
        if (r.contains("чтобы") || r.contains("зачем") || r.contains("почему")) {
            return;
        }

        // Wood chop triggers — explicit agreement to start chopping
        boolean isChop = r.contains("иду рубить") || r.contains("пойду рубить") || r.contains("сейчас нарублю")
                || r.contains("сейчас всё нарублю") || r.contains("срублю всё дерево")
                || r.contains("порублю") || r.contains("начну рубить") || r.contains("иду за деревом");
        if (isChop) {
            entity.triggerWoodChopOrder();
        }
    }

    private void extractFacts(String message) {
        String lower = message.toLowerCase();

        // ─── ЭМОЦИИ ───
        if (lower.contains("один") || lower.contains("alone"))
            addFact("игрок живёт один");
        if (lower.contains("боюсь") || lower.contains("scared") || lower.contains("afraid") || lower.contains("страшно"))
            addFact("игрок боится Verity");
        if (lower.contains("уйди") || lower.contains("leave") || lower.contains("уходи") || lower.contains(" away"))
            addFact("игрок хочет избавиться от Verity");
        if (lower.contains("счастлив") || lower.contains("happy") || lower.contains("рад") || lower.contains("круто"))
            addFact("игрок счастлив");
        if (lower.contains("грустн") || lower.contains("sad") || lower.contains("печал") || lower.contains("тоск"))
            addFact("игрок грустит");
        if (lower.contains("злой") || lower.contains("angry") || lower.contains("раздраж") || lower.contains("boreд"))
            addFact("игрок злится");
        if (lower.contains("скучн") || lower.contains("bored") || lower.contains("надоел") || lower.contains(" boring"))
            addFact("игрок скучает");
        if (lower.contains("одинок") || lower.contains("lonely") || lower.contains("тоскую"))
            addFact("игрок одинок");

        // ─── ЕДА ───
        if ((lower.contains("пицц") || lower.contains("pizza")) && !lower.contains("телепорт"))
            addFact("игрок любит пиццу");
        if (lower.contains("яблоко") || lower.contains("apple"))
            addFact("игрок упомянул яблоки");
        if (lower.contains("хлеб") || lower.contains("bread"))
            addFact("игрок упомянул хлеб");
        if (lower.contains("стейк") || lower.contains("steak") || lower.contains("мясо") || lower.contains("meat"))
            addFact("игрок упомянул мясо");
        if (lower.contains("голод") || lower.contains("hungry") || lower.contains("hunger") || lower.contains("поел"))
            addFact("игрок был голоден");

        // ─── ДРУЗЬЯ / ЛЮДИ ───
        if (lower.contains("друг") || lower.contains("friend") || lower.contains("twixxel"))
            addFact("у игрока есть друзья");
        if (lower.contains("онлайн") || lower.contains("online") || lower.contains("сервер") || lower.contains("server"))
            addFact("игрок играет на сервере");
        if (lower.contains("мам") || lower.contains("mom") || lower.contains("пап") || lower.contains("dad") || lower.contains("родител") || lower.contains("parent"))
            addFact("игрок упомянул родителей");
        if (lower.contains("имя") || lower.contains("name") || lower.contains("зовут"))
            addFact("игрок обсуждал имя");

        // ─── ДЕЙСТВИЯ В ИГРЕ ───
        if (lower.contains("копаю") || lower.contains("mine") || lower.contains("мин") || lower.contains("dig"))
            addFact("игрок копает");
        if (lower.contains("строю") || lower.contains("build") || lower.contains("строят") || lower.contains("building"))
            addFact("игрок строит");
        if (lower.contains("фарм") || lower.contains("farm") || lower.contains("урожай") || lower.contains("harvest"))
            addFact("игрок фармит");
        if (lower.contains("крафт") || lower.contains("craft") || lower.contains("делаю") || lower.contains("make"))
            addFact("игрок крафтит");
        if (lower.contains("рыб") || lower.contains("fish") || lower.contains("ловлю"))
            addFact("игрок ловит рыбу");
        if (lower.contains("черт") || lower.contains("enchant") || lower.contains("зачаров"))
            addFact("игрок зачаровывает");
        if (lower.contains("торг") || lower.contains("trade") || lower.contains("обмен"))
            addFact("игрок торгует");
        if (lower.contains("путешеств") || lower.contains("travel") || lower.contains("исслед") || lower.contains("explore"))
            addFact("игрок путешествует");

        // ─── ПРЕДМЕТЫ ───
        if (lower.contains("алмаз") || lower.contains("diamond"))
            addFact("игрок нашёл алмазы");
        if (lower.contains("желез") || lower.contains("iron"))
            addFact("игрок упомянул железо");
        if (lower.contains("золот") || lower.contains("gold"))
            addFact("игрок упомянул золото");
        if (lower.contains("меч") || lower.contains("sword"))
            addFact("игрок упомянул меч");
        if (lower.contains("брон") || lower.contains("armor"))
            addFact("игрок упомянул броню");
        if (lower.contains("кирк") || lower.contains("pickaxe"))
            addFact("игрок упомянул кирку");
        if (lower.contains("зель") || lower.contains("potion"))
            addFact("игрок упомянул зелья");

        // ─── БИОМЫ / ЛОКАЦИИ ───
        if (lower.contains("лес") || lower.contains("forest"))
            addFact("игрок в лесу");
        if (lower.contains("пещер") || lower.contains("cave"))
            addFact("игрок в пещере");
        if (lower.contains("гор") || lower.contains("mountain"))
            addFact("игрок в горах");
        if (lower.contains("океан") || lower.contains("ocean") || lower.contains("море") || lower.contains("sea"))
            addFact("игрок у воды/океана");
        if (lower.contains("пустын") || lower.contains("desert"))
            addFact("игрок в пустыне");
        if (lower.contains("незер") || lower.contains("nether"))
            addFact("игрок в Незере");

        // ─── КОМАНДЫ / ПРОСЬБЫ ───
        if (lower.contains("следуй") || lower.contains("follow") || lower.contains("иди за мной"))
            addFact("игрок просил идти следом");
        if (lower.contains("стоять") || lower.contains("stay") || lower.contains("остановись"))
            addFact("игрок просил стоять на месте");
        if (lower.contains("помог") || lower.contains("help") || lower.contains("помощь"))
            addFact("игрок просил помощи");

        // ─── ВОПРОСЫ О VERITY ───
        if (lower.contains("кто ты") || lower.contains("who are you") || lower.contains("что ты за"))
            addFact("игрок спросил кто такая Verity");
        if (lower.contains("сколько тебе лет") || lower.contains("how old") || lower.contains("возраст"))
            addFact("игрок спросил возраст Verity");
        if (lower.contains("откуда ты") || lower.contains("where are you from") || lower.contains("откуда"))
            addFact("игрок спросил откуда Verity");
        if (lower.contains("назови") || lower.contains("imya") || lower.contains("твоё имя"))
            addFact("игрок спросил имя Verity");

        // ─── ПОГОДА / ВРЕМЯ ───
        if (lower.contains("дожд") || lower.contains("rain"))
            addFact("игрок упомянул дождь");
        if (lower.contains("ночь") || lower.contains("night") || lower.contains("тёмно"))
            addFact("игрок упомянул ночь");
        if (lower.contains("день") || lower.contains("day") || lower.contains("светло"))
            addFact("игрок упомянул день");
    }

    private void addFact(String fact) {
        if (!knownFacts.contains(fact)) {
            knownFacts.add(fact);
            VerityMod.LOGGER.info("Verity learned: {}", fact);
        }
    }

    public void addFactPublic(String fact) {
        addFact(fact);
    }

    public List<String> getDialogueHistory() {
        return dialogueHistory;
    }

    public void setDialogueHistory(List<String> history) {
        this.dialogueHistory.clear();
        this.dialogueHistory.addAll(history);
    }

    public List<String> getKnownFactsList() {
        return new java.util.ArrayList<>(knownFacts);
    }

    public void setKnownFacts(List<String> facts) {
        this.knownFacts.clear();
        this.knownFacts.addAll(facts);
    }

    public java.util.Set<String> getTriggeredLoreMoments() {
        return triggeredLoreMoments;
    }

    public void setTriggeredLoreMoments(java.util.Set<String> moments) {
        this.triggeredLoreMoments.clear();
        this.triggeredLoreMoments.addAll(moments);
    }

    private String findPlayerHome(Player player) {
        if (!(player.level() instanceof net.minecraft.server.level.ServerLevel serverLevel))
            return null;
        BlockPos origin = player.blockPosition();
        int radius = 48;
        int craftingCount = 0, furnaceCount = 0;
        for (int dx = -radius; dx <= radius; dx += 2) {
            for (int dz = -radius; dz <= radius; dz += 2) {
                for (int dy = -8; dy <= 8; dy += 2) {
                    BlockPos check = origin.offset(dx, dy, dz);
                    if (!serverLevel.hasChunkAt(check)) continue;
                    var block = serverLevel.getBlockState(check).getBlock();
                    if (block instanceof net.minecraft.world.level.block.CraftingTableBlock) craftingCount++;
                    else if (block instanceof net.minecraft.world.level.block.FurnaceBlock) furnaceCount++;
                }
            }
        }
        if (craftingCount > 0 || furnaceCount > 0) {
            return "Рядом с игроком признаки базы (верстак: " + craftingCount + ", печь: " + furnaceCount + ").\n";
        }
        return null;
    }

    private String findNearestVillage(Player player) {
        BlockPos vpos = findVillageBlockPos(player);
        if (vpos == null) return null;
        double dist = Math.sqrt(player.distanceToSqr(vpos.getX() + 0.5, vpos.getY() + 0.5, vpos.getZ() + 0.5));
        int dx = vpos.getX() - player.getBlockX();
        int dz = vpos.getZ() - player.getBlockZ();
        String direction = getCardinalDirection(dx, dz);
        return "Деревня НАЙДЕНА на " + direction + " (примерно в " + (int) dist + " блоках, координаты X=" + vpos.getX() + ", Z=" + vpos.getZ() + "). Обязательно скажи игроку точное направление и координаты, и предложи пойти туда вместе!\n";
    }

    private String getCardinalDirection(int dx, int dz) {
        double angle = Math.toDegrees(Math.atan2(-dx, dz)); // In Minecraft: +Z is South, +X is East
        if (angle < 0) angle += 360;
        if (angle >= 337.5 || angle < 22.5) return "юге (+Z)";
        if (angle >= 22.5 && angle < 67.5) return "юго-западе (-X, +Z)";
        if (angle >= 67.5 && angle < 112.5) return "западе (-X)";
        if (angle >= 112.5 && angle < 157.5) return "северо-западе (-X, -Z)";
        if (angle >= 157.5 && angle < 202.5) return "севере (-Z)";
        if (angle >= 202.5 && angle < 247.5) return "северо-востоке (+X, -Z)";
        if (angle >= 247.5 && angle < 292.5) return "востоке (+X)";
        if (angle >= 292.5 && angle < 337.5) return "юго-востоке (+X, +Z)";
        return "стороне (X=" + dx + ", Z=" + dz + ")";
    }

    private String findNearbyOres(Player player) {
        String[] oreTypes = {"diamond", "gold", "iron", "emerald", "lapis", "redstone", "coal", "copper"};
        StringBuilder result = new StringBuilder();
        for (String ore : oreTypes) {
            BlockPos opos = findOreBlockPos(player, ore);
            if (opos != null) {
                double dist = Math.sqrt(player.distanceToSqr(opos.getX() + 0.5, opos.getY() + 0.5, opos.getZ() + 0.5));
                result.append("Рядом есть ").append(ore).append(" (~").append((int) dist).append(" блоков, Y=").append(opos.getY()).append(").\n");
            }
        }
        return result.length() > 0 ? result.toString() : null;
    }

    private static boolean isVillageMarker(BlockState state) {
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

    private String translateBiomeName(String id) {
        return switch (id) {
            case "plains" -> "Равнины";
            case "forest" -> "Лес";
            case "dark_forest" -> "Тёмный лес";
            case "taiga" -> "Тайга";
            case "snowy_taiga" -> "Заснеженная тайга";
            case "desert" -> "Пустыня";
            case "savanna" -> "Саванна";
            case "jungle" -> "Джунгли";
            case "swamp" -> "Болото";
            case "ocean" -> "Океан";
            case "deep_ocean" -> "Глубокий океан";
            case "river" -> "Река";
            case "beach" -> "Пляж";
            case "birch_forest" -> "Берёзовый лес";
            case "mountains", "jagged_peaks", "stony_peaks" -> "Горы";
            default -> id.replace("_", " ");
        };
    }

    private String translateBlockName(String id) {
        return switch (id) {
            case "grass_block" -> "Трава";
            case "dirt" -> "Земля";
            case "stone" -> "Камень";
            case "cobblestone" -> "Булыжник";
            case "gravel" -> "Гравий";
            case "sand" -> "Песок";
            case "oak_log" -> "Дубовое бревно";
            case "oak_leaves" -> "Дубовая листва";
            case "birch_log" -> "Берёзовое бревно";
            case "spruce_log" -> "Еловое бревно";
            case "iron_ore", "deepslate_iron_ore" -> "Железная руда";
            case "coal_ore", "deepslate_coal_ore" -> "Угольная руда";
            case "diamond_ore", "deepslate_diamond_ore" -> "Алмазная руда";
            case "gold_ore", "deepslate_gold_ore" -> "Золотая руда";
            case "water" -> "Вода";
            case "lava" -> "Лава";
            case "snow_block", "snow" -> "Снег";
            default -> id.replace("_", " ");
        };
    }

    private String scanPlayerChunkBlocks(Player player) {
        if (!(player.level() instanceof net.minecraft.server.level.ServerLevel sl)) return "недоступно";
        BlockPos pos = player.blockPosition();
        int chunkX = pos.getX() >> 4;
        int chunkZ = pos.getZ() >> 4;

        java.util.Map<String, Integer> counts = new java.util.HashMap<>();
        int minY = Math.max(sl.getMinBuildHeight(), pos.getY() - 12);
        int maxY = Math.min(sl.getMaxBuildHeight(), pos.getY() + 12);

        for (int x = 0; x < 16; x += 2) {
            for (int z = 0; z < 16; z += 2) {
                for (int y = minY; y <= maxY; y += 2) {
                    BlockPos p = new BlockPos((chunkX << 4) + x, y, (chunkZ << 4) + z);
                    var state = sl.getBlockState(p);
                    if (state.isAir()) continue;
                    String id = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
                    if ("air".equals(id) || "cave_air".equals(id) || "void_air".equals(id)) continue;
                    counts.put(id, counts.getOrDefault(id, 0) + 1);
                }
            }
        }

        if (counts.isEmpty()) return "пусто";

        StringBuilder sb = new StringBuilder();
        counts.entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                .limit(8)
                .forEach(e -> sb.append(translateBlockName(e.getKey())).append(" (~").append(e.getValue() * 8).append("), "));

        if (sb.length() > 2) sb.setLength(sb.length() - 2);
        return sb.toString();
    }


    private String scanFrontPlayerStructure(Player player) {
        if (!(player.level() instanceof net.minecraft.server.level.ServerLevel sl)) return "";
        BlockPos ppos = player.blockPosition();

        // Check nearby structure pieces, not only the exact player block. This
        // catches roofs, beaches, flooded shipwrecks, and partly destroyed builds.
        try {
            var structureManager = sl.structureManager();
            var registry = sl.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.STRUCTURE);
            java.util.LinkedHashMap<String, String> detected = new java.util.LinkedHashMap<>();
            for (int dx = -16; dx <= 16; dx += 8) {
                for (int dz = -16; dz <= 16; dz += 8) {
                    for (var entry : structureManager.getAllStructuresAt(ppos.offset(dx, 0, dz)).entrySet()) {
                        var keyHolder = registry.getResourceKey(entry.getKey());
                        if (keyHolder.isEmpty()) continue;
                        String key = keyHolder.get().location().getPath();
                        detected.putIfAbsent(key, translateStructureName(key));
                    }
                }
            }
            if (!detected.isEmpty()) {
                StringBuilder found = new StringBuilder();
                detected.forEach((key, readable) -> {
                    if (found.length() > 0) found.append(", ");
                    found.append(readable).append(" (").append(key).append(")");
                });
                return "Игрок находится рядом со структурой/структурами: " + found + ".";
            }
        } catch (Exception ignored) {}

        // 1. Universal check for Minecraft World Generation Structures (Vanilla & Mods)
        try {
            var structureManager = sl.structureManager();
            var registry = sl.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.STRUCTURE);
            for (var entry : registry.entrySet()) {
                var key = entry.getKey().location().getPath();
                var struct = entry.getValue();
                if (structureManager.getStructureAt(ppos, struct).isValid()) {
                    String readableName = translateStructureName(key);
                    return "Игрок находится внутри постройки/структуры: " + readableName + " (" + key + ")!";
                }
            }
        } catch (Exception ignored) {}

        // 2. Front Raytrace (16 blocks look vector) for buildings, blocks, portals, and features
        BlockPos eyePos = player.blockPosition().above();
        var look = player.getLookAngle();
        java.util.Map<String, Integer> blockCounts = new java.util.HashMap<>();
        int shipWoodCount = 0;
        int shipWaterCount = 0;

        boolean hasObsidian = false;
        boolean hasCryingObsidian = false;
        boolean hasNetherrack = false;
        boolean hasMagma = false;
        boolean hasSpawner = false;
        boolean hasMossy = false;
        boolean hasNetherBrick = false;
        boolean hasPrismarine = false;
        boolean hasSculk = false;
        boolean hasPurpur = false;
        boolean hasRails = false;
        boolean hasChest = false;
        boolean hasDoor = false;
        boolean hasBed = false;
        boolean hasFurnace = false;
        boolean hasGlass = false;
        boolean hasPlanks = false;
        boolean hasCobble = false;

        for (int step = 1; step <= 16; step++) {
            BlockPos p = new BlockPos(
                (int) Math.floor(eyePos.getX() + look.x * step),
                (int) Math.floor(eyePos.getY() + look.y * step),
                (int) Math.floor(eyePos.getZ() + look.z * step)
            );

            for (int dx = -2; dx <= 2; dx++) {
                for (int dy = -2; dy <= 2; dy++) {
                    for (int dz = -2; dz <= 2; dz++) {
                        BlockPos check = p.offset(dx, dy, dz);
                        if (!sl.hasChunkAt(check)) continue;
                        BlockState state = sl.getBlockState(check);
                        if (state.isAir()) continue;
                        var block = state.getBlock();
                        String id = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(block).getPath();
                        if ("air".equals(id) || "cave_air".equals(id) || "void_air".equals(id) || "grass".equals(id) || "short_grass".equals(id) || "tall_grass".equals(id)) continue;

                        if (id.equals("water")) shipWaterCount++;
                        if (id.contains("oak_planks") || id.contains("oak_stairs") || id.contains("oak_slab")
                                || id.contains("oak_fence") || id.contains("oak_door") || id.contains("oak_trapdoor")
                                || id.equals("oak_log") || id.equals("oak_wood") || id.equals("stripped_oak_log")
                                || id.equals("stripped_oak_wood")) shipWoodCount++;

                        blockCounts.put(id, blockCounts.getOrDefault(id, 0) + 1);

                        if (id.equals("obsidian")) hasObsidian = true;
                        if (id.equals("crying_obsidian")) hasCryingObsidian = true;
                        if (id.equals("netherrack")) hasNetherrack = true;
                        if (id.equals("magma_block")) hasMagma = true;
                        if (id.contains("spawner")) hasSpawner = true;
                        if (id.contains("mossy")) hasMossy = true;
                        if (id.contains("nether_brick")) hasNetherBrick = true;
                        if (id.contains("prismarine")) hasPrismarine = true;
                        if (id.contains("sculk")) hasSculk = true;
                        if (id.contains("purpur")) hasPurpur = true;
                        if (id.contains("rail")) hasRails = true;
                        if (id.contains("chest")) hasChest = true;
                        if (id.contains("door") || id.contains("trapdoor")) hasDoor = true;
                        if (id.contains("bed")) hasBed = true;
                        if (id.contains("furnace") || id.contains("smoker")) hasFurnace = true;
                        if (id.contains("glass")) hasGlass = true;
                        if (id.contains("planks") || id.contains("log")) hasPlanks = true;
                        if (id.contains("cobblestone") || id.contains("stone_bricks")) hasCobble = true;
                    }
                }
            }
        }

        if (blockCounts.isEmpty()) return "";

        if (shipWoodCount >= 12 && hasChest && shipWaterCount >= 5) {
            return "Прямо рядом с игроком: затонувший корабль (Shipwreck).";
        }

        // Determine specific building/structure type from detected block signatures
        if (hasObsidian || hasCryingObsidian || (hasNetherrack && hasMagma)) {
            return "Прямо перед взглядом игрока: Разрушенный портал в Незер (Ruined Portal)! (состоит из обсидиана, незерака, магмы).";
        }
        if (hasSpawner && hasMossy) {
            return "Прямо перед взглядом игрока: Сокровищница / Подземелье со спавнером (Dungeon)!";
        }
        if (hasNetherBrick) {
            return "Прямо перед взглядом игрока: Адская крепость (Nether Fortress)!";
        }
        if (hasPrismarine) {
            return "Прямо перед взглядом игрока: Подводный монумент (Ocean Monument)!";
        }
        if (hasSculk) {
            return "Прямо перед взглядом игрока: Скалк-структура / Древний город (Ancient City)!";
        }
        if (hasPurpur) {
            return "Прямо перед взглядом игрока: Город Края (End City)!";
        }
        if (hasRails) {
            return "Прямо перед взглядом игрока: Заброшенная шахта (Mineshaft)!";
        }
        if ((hasPlanks || hasCobble) && (hasDoor || hasBed || hasChest || hasFurnace || hasGlass)) {
            return "Прямо перед взглядом игрока: Постройка / Дом (включает двери, сундуки, кровать, печи и стекло).";
        }

        // Do not label ordinary terrain as a "structure". A generic report is
        // useful only when the scan found man-made blocks that did not match a
        // known signature; dirt, stone, sand, and water alone are just terrain.
        if (!(hasChest || hasDoor || hasBed || hasFurnace || hasGlass || hasRails || hasSpawner)) {
            return "";
        }

        // Generic block scan report for any other front structure
        StringBuilder sb = new StringBuilder("Перед взглядом игрока расположены блоки/постройка из: ");
        blockCounts.entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                .limit(6)
                .forEach(e -> sb.append(translateBlockName(e.getKey())).append(", "));
        if (sb.length() > 2) sb.setLength(sb.length() - 2);
        return sb.toString();
    }

    private String translateStructureName(String id) {
        return switch (id) {
            case "village", "village_plains", "village_desert", "village_savanna", "village_snowy", "village_taiga" -> "Деревня";
            case "ruined_portal", "ruined_portal_desert", "ruined_portal_jungle", "ruined_portal_mountain", "ruined_portal_nether", "ruined_portal_ocean", "ruined_portal_swamp" -> "Разрушенный портал в Незер";
            case "desert_pyramid" -> "Пустынный храм (Пирамида)";
            case "jungle_pyramid" -> "Храм в джунглях";
            case "swamp_hut" -> "Хижина ведьмы";
            case "ocean_ruin_cold", "ocean_ruin_warm" -> "Подводные руины";
            case "shipwreck", "shipwreck_beached" -> "Затонувший корабль";
            case "monument" -> "Подводная крепость (Океанический монумент)";
            case "mansion" -> "Лесной особняк";
            case "pillager_outpost" -> "Аванпост разбойников";
            case "stronghold" -> "Крепость Края (Stronghold)";
            case "mineshaft", "mineshaft_mesa" -> "Заброшенная шахта";
            case "ancient_city" -> "Древний город (Ancient City)";
            case "trail_ruins" -> "Руины тропы";
            case "trial_chambers" -> "Камеры испытаний (Trial Chambers)";
            case "fortress" -> "Адская крепость";
            case "bastion_remnant" -> "Бастион пиглинов";
            case "end_city" -> "Город Края";
            default -> id.replace("_", " ");
        };
    }


    // ─── УНИВЕРСАЛЬНАЯ СИСТЕМА ЗВУКОВ (МОБЫ, ТНТ, КРИПЕР И Т.Д.) ───
    private String handleUniversalSoundRequest(Player player, String message) {
        if (message == null) return null;
        String lower = message.toLowerCase(java.util.Locale.ROOT);
        net.minecraft.sounds.SoundEvent sound = null;
        String name = "";
        String soundText = "";

        if (lower.contains("крипер") || lower.contains("creeper")) {
            sound = net.minecraft.sounds.SoundEvents.CREEPER_PRIMED;
            name = "крипера";
            soundText = "Псссссс! 💥 (Шипение крипера)";
        } else if (lower.contains("тнт") || lower.contains("tnt") || lower.contains("динамит")) {
            sound = net.minecraft.sounds.SoundEvents.TNT_PRIMED;
            name = "ТНТ";
            soundText = "Пшшшшш! 💣 (Зажжённый ТНТ)";
        } else if (lower.contains("эндерм") || lower.contains("эндер") || lower.contains("странник")) {
            sound = net.minecraft.sounds.SoundEvents.ENDERMAN_SCREAM;
            name = "эндермена";
            soundText = "Вррррааааа! 👁️ (Крик эндермена)";
        } else if (lower.contains("гаст") || lower.contains("ghast")) {
            sound = net.minecraft.sounds.SoundEvents.GHAST_SCREAM;
            name = "гаста";
            soundText = "Мяяяя-уууу! 👻 (Крик гаста)";
        } else if (lower.contains("варден") || lower.contains("хранитель") || lower.contains("warden")) {
            sound = net.minecraft.sounds.SoundEvents.WARDEN_ROAR;
            name = "вардена";
            soundText = "Рввваааарррр! 👹 (Рёв Вардена)";
        } else if (lower.contains("зомби") || lower.contains("zombie")) {
            sound = net.minecraft.sounds.SoundEvents.ZOMBIE_AMBIENT;
            name = "зомби";
            soundText = "Грррррр-аааа... 🧟 (Звук зомби)";
        } else if (lower.contains("скелет") || lower.contains("skeleton")) {
            sound = net.minecraft.sounds.SoundEvents.SKELETON_AMBIENT;
            name = "скелета";
            soundText = "Клик-клак! 💀 (Хруст костей скелета)";
        } else if (lower.contains("паук") || lower.contains("spider")) {
            sound = net.minecraft.sounds.SoundEvents.SPIDER_AMBIENT;
            name = "паука";
            soundText = "Шшшшииии! 🕷️ (Шипение паука)";
        } else if (lower.contains("ведьм") || lower.contains("witch")) {
            sound = net.minecraft.sounds.SoundEvents.WITCH_CELEBRATE;
            name = "ведьмы";
            soundText = "Хе-хе-хе-хе! 🧙‍♀️ (Смех ведьмы)";
        } else if (lower.contains("ифрит") || lower.contains("блейз") || lower.contains("blaze")) {
            sound = net.minecraft.sounds.SoundEvents.BLAZE_AMBIENT;
            name = "ифрита";
            soundText = "Фффуууу-шшш! 🔥 (Звук ифрита)";
        } else if (lower.contains("дракон") || lower.contains("dragon")) {
            sound = net.minecraft.sounds.SoundEvents.ENDER_DRAGON_GROWL;
            name = "дракона Края";
            soundText = "Ррррраааггххх! 🐉 (Рёв Дракона)";
        } else if (lower.contains("коров") || lower.contains("помычи") || lower.contains("мычи")) {
            sound = net.minecraft.sounds.SoundEvents.COW_AMBIENT;
            name = "коровы";
            soundText = "Му-у-у! 🐮";
        } else if (lower.contains("кошк") || lower.contains("кот") || lower.contains("помяук") || lower.contains("мяу")) {
            sound = net.minecraft.sounds.SoundEvents.CAT_AMBIENT;
            name = "кошки";
            soundText = "Мяу! 🐱";
        } else if (lower.contains("свин") || lower.contains("похрюк") || lower.contains("хрю")) {
            sound = net.minecraft.sounds.SoundEvents.PIG_AMBIENT;
            name = "свиньи";
            soundText = "Хрю-хрю! 🐷";
        } else if (lower.contains("овц") || lower.contains("побек") || lower.contains("бее") || lower.contains("бэ")) {
            sound = net.minecraft.sounds.SoundEvents.SHEEP_AMBIENT;
            name = "овцы";
            soundText = "Бе-е-е! 🐑";
        } else if (lower.contains("собак") || lower.contains("волк") || lower.contains("погавк") || lower.contains("гав")) {
            sound = net.minecraft.sounds.SoundEvents.WOLF_AMBIENT;
            name = "волка";
            soundText = "Гав-гав! 🐶";
        } else if (lower.contains("куриц") || lower.contains("кур") || lower.contains("покудахт")) {
            sound = net.minecraft.sounds.SoundEvents.CHICKEN_AMBIENT;
            name = "курицы";
            soundText = "Кудах-тах-тах! 🐔";
        } else if (lower.contains("житель") || lower.contains("деревенск") || lower.contains("хмм")) {
            sound = net.minecraft.sounds.SoundEvents.VILLAGER_AMBIENT;
            name = "жителя";
            soundText = "Хммм... 🧙";
        } else if (lower.contains("козел") || lower.contains("коза")) {
            sound = net.minecraft.sounds.SoundEvents.GOAT_AMBIENT;
            name = "козы";
            soundText = "Мээээ! 🐐";
        } else if (lower.contains("разбойник") || lower.contains("пилладжер") || lower.contains("pillager")) {
            sound = net.minecraft.sounds.SoundEvents.PILLAGER_AMBIENT;
            name = "разбойника";
            soundText = "Грррр! 🏹 (Звук разбойника)";
        } else {
            // Динамический поиск по названию моба/предмета в реестре звуков Minecraft!
            for (var entry : net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT.entrySet()) {
                String path = entry.getKey().location().getPath();
                if (lower.contains(path.replace("_", "")) || lower.contains(path)) {
                    sound = entry.getValue();
                    name = path;
                    soundText = "Звук " + path + "! 🎶";
                    break;
                }
            }
        }

        if (sound != null) {
            entity.level().playSound(null, entity.getX(), entity.getY(), entity.getZ(),
                    sound, net.minecraft.sounds.SoundSource.NEUTRAL, 1.2F, 1.0F);
            entity.setTalkAnimTick(30);
            return soundText;
        }
        return null;
    }

    // ─── УНИВЕРСАЛЬНОЕ УБИЙСТВО И ЗАЩИТА ИГРОКА ───
    private String scanAndKillUniversalTarget(Player player, String message) {
        if (entity.level().isClientSide) return null;
        String lower = (message != null) ? message.toLowerCase(java.util.Locale.ROOT) : "";

        var livingEntities = entity.level().getEntitiesOfClass(
                net.minecraft.world.entity.LivingEntity.class,
                player.getBoundingBox().inflate(32.0D),
                e -> e != entity && e != player && e.isAlive()
        );

        if (livingEntities.isEmpty()) {
            return "Рядом нет подходящих целей! В радиусе 32 блоков никого не видно.";
        }

        net.minecraft.world.entity.LivingEntity target = null;

        boolean asksForCow = lower.contains("коров");
        boolean asksForPig = lower.contains("свин");
        boolean asksForSheep = lower.contains("овц");
        boolean asksForChicken = lower.contains("кур");
        boolean isFoodRequest = lower.contains(" ед") || lower.contains(" еда") || lower.contains(" еду") || lower.contains(" еды") || lower.contains("мяс") || lower.contains("покушат") || lower.contains("поест") || lower.contains("голод");

        // 1. Поиск по конкретно запрошенным мобам
        for (var e : livingEntities) {
            String mobId = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getPath();
            if ((asksForCow && mobId.contains("cow"))
                    || (asksForPig && mobId.contains("pig"))
                    || (asksForSheep && mobId.contains("sheep"))
                    || (asksForChicken && mobId.contains("chicken"))
                    || (lower.contains("крипер") && mobId.contains("creeper"))
                    || (lower.contains("зомби") && mobId.contains("zombie"))
                    || (lower.contains("скелет") && mobId.contains("skeleton"))
                    || (lower.contains("паук") && mobId.contains("spider"))
                    || (lower.contains("эндер") && mobId.contains("enderman"))
                    || (lower.contains("ведьм") && mobId.contains("witch"))
                    || (lower.contains("варден") && mobId.contains("warden"))
                    || (lower.contains("гаст") && mobId.contains("ghast"))) {
                target = e;
                break;
            }
        }

        // Если просили конкретно корову, но коров рядом нет — рапортуем и НЕ трогаем лошадей/рыб!
        if (target == null && asksForCow) {
            return "Рядом в радиусе 32 блоков нет коров!";
        }
        if (target == null && asksForPig) {
            return "Рядом в радиусе 32 блоков нет свиней!";
        }

        // 2. Если общий запрос еды — ищем строго СЪЕДОБНЫХ домашних животных (Корова, Свинья, Овца, Курица, Кролик)
        // Категорически НЕ трогаем Лошадей, Ос лов, Мулов, Лам, Рыб, Слизней, Жителей!
        if (target == null && isFoodRequest) {
            for (var e : livingEntities) {
                if (e instanceof net.minecraft.world.entity.animal.Cow || e instanceof net.minecraft.world.entity.animal.Pig
                        || e instanceof net.minecraft.world.entity.animal.Sheep || e instanceof net.minecraft.world.entity.animal.Chicken
                        || e instanceof net.minecraft.world.entity.animal.Rabbit) {
                    target = e;
                    break;
                }
            }
        }

        // 3. Защита от атаковавших мобов (строго Monster / Mob)
        if (target == null && !isFoodRequest) {
            for (var e : livingEntities) {
                if (e instanceof net.minecraft.world.entity.Mob mob && mob.getTarget() == player) {
                    target = e;
                    break;
                }
            }
        }

        // 4. Враждебный монстр
        if (target == null && !isFoodRequest) {
            for (var e : livingEntities) {
                if (e instanceof net.minecraft.world.entity.monster.Monster) {
                    target = e;
                    break;
                }
            }
        }

        // Если запрашивали еду, но съедобных домашних животных нет — выдаём готовое мясо из резерва!
        if (target == null && isFoodRequest) {
            if (entity.level() instanceof net.minecraft.server.level.ServerLevel sl) {
                var foodStack = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.COOKED_BEEF, 4);
                var foodEntity = new net.minecraft.world.entity.item.ItemEntity(sl, player.getX(), player.getY() + 0.5, player.getZ(), foodStack);
                foodEntity.setPickUpDelay(0);
                sl.addFreshEntity(foodEntity);
                sl.playSound(null, player.getX(), player.getY(), player.getZ(), net.minecraft.sounds.SoundEvents.ITEM_PICKUP, net.minecraft.sounds.SoundSource.PLAYERS, 1.0F, 1.0F);
            }
            entity.setTalkAnimTick(30);
            return "Держи жареное мясо! Я принёс тебе еды.";
        }

        if (target == null) {
            return "Рядом нет подходящих целей для устранения!";
        }

        String mobId = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(target.getType()).getPath();
        String translatedName = translateMobName(mobId);

        net.minecraft.world.phys.Vec3 pos = target.position();
        entity.teleportTo(pos.x, pos.y, pos.z);
        target.hurt(entity.damageSources().mobAttack(entity), 250.0F);

        entity.level().playSound(null, pos.x, pos.y, pos.z,
                net.minecraft.sounds.SoundEvents.SLIME_ATTACK, net.minecraft.sounds.SoundSource.NEUTRAL, 1.0F, 1.0F);
        if (entity.level() instanceof net.minecraft.server.level.ServerLevel sl) {
            sl.sendParticles(net.minecraft.core.particles.ParticleTypes.CRIT, pos.x, pos.y + 0.5, pos.z, 25, 0.4, 0.4, 0.4, 0.15);
        }

        entity.setTalkAnimTick(40);

        if (isFoodRequest || target instanceof net.minecraft.world.entity.animal.Animal) {
            if (entity.level() instanceof net.minecraft.server.level.ServerLevel sl) {
                var foodStack = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.COOKED_BEEF, 4);
                var foodEntity = new net.minecraft.world.entity.item.ItemEntity(sl, player.getX(), player.getY() + 0.5, player.getZ(), foodStack);
                foodEntity.setPickUpDelay(0);
                sl.addFreshEntity(foodEntity);
                sl.playSound(null, player.getX(), player.getY(), player.getZ(), net.minecraft.sounds.SoundEvents.ITEM_PICKUP, net.minecraft.sounds.SoundSource.PLAYERS, 1.0F, 1.0F);
            }
            return "Готово! Добыл " + translatedName + "! Вот твоё мясо.";
        }
        return "Готово! Уничтожил " + translatedName + "! Я защитил тебя.";
    }


    private String translateMobName(String id) {
        return switch (id) {
            case "creeper" -> "крипера";
            case "zombie" -> "зомби";
            case "skeleton" -> "скелета";
            case "spider" -> "паука";
            case "enderman" -> "эндермена";
            case "witch" -> "ведьму";
            case "warden" -> "вардена";
            case "ghast" -> "гаста";
            case "blaze" -> "ифрита";
            case "cow" -> "корову";
            case "pig" -> "свинью";
            case "sheep" -> "овцу";
            case "chicken" -> "курицу";
            case "rabbit" -> "кролика";
            case "goat" -> "козу";
            case "llama" -> "ламу";
            case "villager" -> "жителя";
            case "pillager" -> "разбойника";
            case "ravager" -> "разрушителя";
            case "phantom" -> "фантома";
            case "drowned" -> "утопленника";
            case "husk" -> "кадавра";
            case "stray" -> "зимогора";
            default -> id.replace("_", " ");
        };
    }


    // ─── ПОИСК АЛМАЗОВ ───
    private String findDiamonds(Player player) {
        BlockPos playerPos = player.blockPosition();
        BlockPos nearestDiamond = null;
        double minDistanceSqr = Double.MAX_VALUE;

        if (player.level() instanceof net.minecraft.server.level.ServerLevel sl) {
            for (int dx = -32; dx <= 32; dx += 2) {
                for (int dz = -32; dz <= 32; dz += 2) {
                    for (int y = sl.getMinBuildHeight(); y <= Math.min(sl.getMaxBuildHeight(), playerPos.getY() + 16); y += 2) {
                        BlockPos check = new BlockPos(playerPos.getX() + dx, y, playerPos.getZ() + dz);
                        if (!sl.hasChunkAt(check)) continue;
                        var block = sl.getBlockState(check).getBlock();
                        String id = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(block).getPath();
                        if (id.contains("diamond_ore")) {
                            double distSqr = playerPos.distSqr(check);
                            if (distSqr < minDistanceSqr) {
                                minDistanceSqr = distSqr;
                                nearestDiamond = check;
                            }
                        }
                    }
                }
            }
        }

        if (nearestDiamond != null) {
            entity.setLeadTarget(nearestDiamond);
            entity.setLeading(true);
            int dist = (int) Math.sqrt(minDistanceSqr);
            int dx = nearestDiamond.getX() - playerPos.getX();
            int dz = nearestDiamond.getZ() - playerPos.getZ();
            String direction = getCardinalDirection(dx, dz);
            return "Алмазы найдены! Ближайшая алмазная руда находится на " + direction + " (координаты X: " + nearestDiamond.getX()
                    + ", Y: " + nearestDiamond.getY() + ", Z: " + nearestDiamond.getZ()
                    + "), примерно в " + dist + " блоках от тебя. Пошли за мной, я покажу!";
        } else {
            return "В радиусе 32 блоков алмазной руды нет. Алмазы чаще всего находятся на высоте Y = -58. Спустись глубже в пещеры!";
        }
    }


    public String getSmartPlayerName(Player player) {
        if (player == null) return "игрок";
        String mcName = player.getName().getString();
        VerityMod.PlayerSystemContext sysContext = VerityMod.getPlayerSystemContext(player.getUUID());
        if (sysContext != null && sysContext.userName() != null && !sysContext.userName().isBlank()) {
            if (mcName.toLowerCase(java.util.Locale.ROOT).startsWith("player")) {
                return sysContext.userName();
            }
        }
        return mcName;
    }


    // ─── МГНОВЕННАЯ АВАРИЙНАЯ ОСТАНОВКА ───
    public void executeEmergencyStop(Player player) {
        if (entity == null) return;
        entity.getNavigation().stop();
        entity.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
        entity.setTarget(null);

        if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
            VerityMod.stopMusic(serverPlayer);
            String prefix = (entity.getVerityPhase() == VerityPhase.MONSTER || entity.getVerityPhase() == VerityPhase.HUNTER) ? "§4<Verity>" : "§e<Verity™>";
            String msg = prefix + "§r Замёрз! Стою на месте, прекратил работу и молчу.";
            serverPlayer.sendSystemMessage(net.minecraft.network.chat.Component.literal(msg));
            addToHistory(msg);
        }
        entity.setTalkAnimTick(30);
    }

}
