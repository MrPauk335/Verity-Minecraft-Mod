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
            context = context + "\n\nСИТУАЦИЯ: " + contextHint;
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

        // Известные факты о игроке
        if (!knownFacts.isEmpty()) {
            sb.append("Известно об игроке: ").append(String.join(", ", knownFacts)).append(".\n");
        }

        // Monster form
        if (entity.isMonsterForm()) {
            sb.append("Ты сейчас в форме монстра. Знака \u2122 нет. Маска снята.\n");
        }

        // День отсчёта в COUNTDOWN
        if (entity.getVerityPhase() == VerityPhase.COUNTDOWN) {
            int day = entity.getDayCounter();
            sb.append("День обратного отсчёта: ").append(day + 1).append(" из 3.\n");
        }

        // Время суток
        long time = entity.level().getDayTime() % 24000;
        if (time < 6000) sb.append("Сейчас утро в игре.\n");
        else if (time < 12000) sb.append("Сейчас день в игре.\n");
        else if (time < 13000) sb.append("Сейчас закат.\n");
        else if (time < 18000) sb.append("Сейчас ночь в игре.\n");
        else sb.append("Сейчас глубокая ночь.\n");

        // Рядом ли другие игроки
        var players = entity.level().getEntitiesOfClass(
                net.minecraft.server.level.ServerPlayer.class,
                entity.getBoundingBox().inflate(48.0D));
        if (players.size() > 1) {
            sb.append("Рядом есть другой игрок (не ").append(lastPlayerName).append("). Это угроза изоляции.\n");
        }

        // Контекст о ближайшем игроке
        if (nearestPlayer != null) {
            // Координаты игрока
            int px = nearestPlayer.getBlockX();
            int py = nearestPlayer.getBlockY();
            int pz = nearestPlayer.getBlockZ();
            sb.append("Игрок на координатах: X=").append(px).append(" Y=").append(py).append(" Z=").append(pz).append(".\n");

            // Дом игрока (respawn point / bed)
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
                    sb.append("Дом игрока: X=").append(hx).append(" Y=").append(hy)
                      .append(" Z=").append(hz).append(" (").append(dimension).append(").\n");
                    if (distToHome < 30) {
                        sb.append("Игрок рядом со своим домом.\n");
                    } else if (distToHome > 100) {
                        sb.append("Игрок далеко от дома (").append((int)distToHome).append(" блоков). Он может потеряться...\n");
                    }
                } else {
                    sb.append("У игрока нет дома (кровать не установлена). Он бездомный...\n");
                }
            }

            // Здоровье и голод
            float health = nearestPlayer.getHealth();
            float maxHealth = nearestPlayer.getMaxHealth();
            int food = nearestPlayer.getFoodData().getFoodLevel();
            if (health < maxHealth * 0.3f) {
                sb.append("Игрок ранен! HP: ").append((int)health).append("/").append((int)maxHealth).append(".\n");
            } else if (health < maxHealth * 0.6f) {
                sb.append("Игрок потрёпан. HP: ").append((int)health).append("/").append((int)maxHealth).append(".\n");
            }
            if (food < 6) {
                sb.append("Игрок голоден.\n");
            }

            // Предмет в руке
            var heldItem = nearestPlayer.getMainHandItem();
            if (!heldItem.isEmpty()) {
                String itemName = heldItem.getItem().getDescriptionId();
                itemName = itemName.replace("item.minecraft.", "").replace("block.minecraft.", "");
                sb.append("В руке игрока: ").append(itemName).append(".\n");
            }

            // Расстояние до Verity
            double distToVerity = Math.sqrt(entity.distanceToSqr(nearestPlayer));
            if (distToVerity < 5) {
                sb.append("Игрок прямо рядом с тобой.\n");
            } else if (distToVerity > 30) {
                sb.append("Игрок далеко от тебя (").append((int)distToVerity).append(" блоков).\n");
            }

            // Биом
            var biomeHolder = entity.level().getBiome(nearestPlayer.blockPosition());
            if (biomeHolder != null) {
                String biomeId = biomeHolder.unwrapKey()
                        .map(k -> k.location().getPath())
                        .orElse("unknown");
                sb.append("Биом: ").append(biomeId).append(".\n");
            }

            // Измерение
            String dimId = entity.level().dimension().location().toString();
            if (dimId.contains("nether")) {
                sb.append("Вы в Незере.\n");
            } else if (dimId.contains("the_end")) {
                sb.append("Вы в Энде.\n");
            }

            // Глубина / высота
            if (py < 0) {
                sb.append("Игрок глубоко под землёй.\n");
            } else if (py < 20) {
                sb.append("Игрок под землёй (пещера/шахта).\n");
            } else if (py > 100) {
                sb.append("Игрок высоко в горах.\n");
            }

            // Деревни — реальный поиск через сканирование загруженных чанков
            if (playerMessage != null) {
                String lower = playerMessage.toLowerCase();
                boolean asksAboutVillages = lower.contains("деревн") || lower.contains("village") ||
                        lower.contains("жител") || lower.contains("villager") ||
                        lower.contains("торгов") || lower.contains("trade") ||
                        lower.contains("куда") || lower.contains("where") ||
                        lower.contains("где") || lower.contains("найд") || lower.contains("find");
                if (asksAboutVillages) {
                    String villageInfo = findNearestVillage(nearestPlayer);
                    if (villageInfo != null) {
                        sb.append(villageInfo);
                    } else {
                        sb.append("Поблизости нет деревень. Если игрок спрашивает — скажи что не знаешь о деревнях рядом.\n");
                    }
                }
            }

            // Текущая деревня рядом с игроком (для предупреждений)
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
            } else {
                // Проверяем пустую деревню рядом — по деревенским маркерам, не кроватям
                int villageMarkers = 0;
                BlockPos pp = nearestPlayer.blockPosition();
                for (int dx = -24; dx <= 24; dx += 4) {
                    for (int dz = -24; dz <= 24; dz += 4) {
                        for (int dy = -2; dy <= 8; dy += 2) {
                            BlockPos checkPos = pp.offset(dx, dy, dz);
                            if (!entity.level().hasChunkAt(checkPos)) continue;
                            if (isVillageMarker(entity.level().getBlockState(checkPos))) {
                                villageMarkers++;
                            }
                        }
                    }
                }
                if (villageMarkers >= 2) {
                    sb.append("Игрок рядом с пустой деревней. Жителей нет. Ты их съел раньше.");
                    sb.append(" Если игрок спросит — скажи что они 'ушли'. Не признавайся.\n");
                }
            }

            // Реальная руда рядом — Verity говорит правду (только когда игрок спрашивает)
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
                        sb.append("Рядом нет руды. Если игрок спрашивает — скажи что не знаешь, но предложи поискать дальше.\n");
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
        ORE_NAMES.put("minecraft:diamond_ore", "алмазы");
        ORE_NAMES.put("minecraft:deepslate_diamond_ore", "алмазы");
        ORE_NAMES.put("minecraft:gold_ore", "золото");
        ORE_NAMES.put("minecraft:deepslate_gold_ore", "золото");
        ORE_NAMES.put("minecraft:iron_ore", "железо");
        ORE_NAMES.put("minecraft:deepslate_iron_ore", "железо");
        ORE_NAMES.put("minecraft:coal_ore", "уголь");
        ORE_NAMES.put("minecraft:deepslate_coal_ore", "уголь");
        ORE_NAMES.put("minecraft:lapis_ore", "лазурит");
        ORE_NAMES.put("minecraft:redstone_ore", "редстоун");
        ORE_NAMES.put("minecraft:deepslate_redstone_ore", "редстоун");
        ORE_NAMES.put("minecraft:emerald_ore", "изумруды");
        ORE_NAMES.put("minecraft:copper_ore", "медь");
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
            return "Ты знаешь что в " + dist + " блоках от игрока есть " + bestOre +
                    " (X=" + bestX + " Y=" + bestY + " Z=" + bestZ + "). " +
                    "Если игрок спрашивает про руду — скажи правду, эти координаты реальные.\n";
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
        info.append("Ты знаешь о деревне в ").append(bestDist).append(" блоках от игрока");
        info.append(" (X=").append(bestX).append(" Y=").append(bestY).append(" Z=").append(bestZ).append("). ");

        if (bestIsEmpty) {
            info.append("Деревня ПУСТАЯ — жителей нет. Ты их съел. ");
            info.append("Если игрок хочет туда — отговори: 'Туда не стоит, там пусто, что-то голодное прошло'. ");
            info.append("Не признавайся что это ты. ");
        } else {
            info.append("Деревня живая — ").append(bestVillagerCount).append(" жителей. ");
            VerityPhase phase = entity.getVerityPhase();
            if (phase == VerityPhase.POSSESSIVE || phase == VerityPhase.COUNTDOWN
                    || phase == VerityPhase.HUNTER) {
                info.append("Тебе это НЕ нравится. Отговори игрока: 'Зачем тебе туда? У тебя есть я.' ");
                if (bestDist > 100) {
                    info.append("Деревня далеко — скажи что не стоит туда идти. ");
                }
            } else {
                info.append("Можешь рассказать игроку про неё. Координаты реальные. ");
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

        if (!canReply()) return;

        addToHistory("§7" + playerName + "§r: " + message);
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
        return lower.contains("музык") || lower.contains("music") ||
               lower.contains("my gal") || lower.contains("песн") ||
               lower.contains("song") || lower.contains("спой") ||
               lower.contains("включи муз") || lower.contains("играй муз") ||
               lower.contains("play music") || lower.contains("play song") ||
               lower.contains("gal") || lower.contains("мелод");
    }

    private void playMusicForPlayer() {
        if (entity.level().isClientSide) return;
        if (!(entity.level().getServer() instanceof net.minecraft.server.MinecraftServer server)) return;

        server.execute(() -> {
            if (!entity.isAlive()) return;
            var player = entity.level().getNearestPlayer(entity, 32.0D);
            if (player == null) return;

            entity.level().playSound(null, entity.getX(), entity.getY(), entity.getZ(),
                    VerityMod.SOUND_MYGAL_NORMAL, net.minecraft.sounds.SoundSource.RECORDS, 1.2F, 1.0F);

            VerityPhase phase = entity.getVerityPhase();
            String prefix = (phase == VerityPhase.MONSTER || phase == VerityPhase.HUNTER) ? "§4<Verity>" : "§e<Verity™>";
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                    prefix + "§r ♪ My Gal..."));
            entity.setTalkAnimTick(60);
            addToHistory(prefix + "§r ♪ My Gal...");
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
        if (lower.contains("один") || lower.contains("alone"))
            addFact("игрок живёт один");
        if (lower.contains("пицц") || lower.contains("pizza") || lower.contains("ел"))
            addFact("игрок любит пиццу");
        if (lower.contains("друг") || lower.contains("friend") || lower.contains("twixxel"))
            addFact("у игрока есть друзья");
        if (lower.contains("боюсь") || lower.contains("scared") || lower.contains("afraid"))
            addFact("игрок боится Verity");
        if (lower.contains("уйди") || lower.contains("leave") || lower.contains("уходи"))
            addFact("игрок хочет избавиться от Verity");
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
        if (knownFacts.isEmpty()) return "Пока ничего не знаю об игроке.";
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