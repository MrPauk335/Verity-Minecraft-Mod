package net.verity;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.verity.block.CardboardBoxBlock;
import net.verity.entity.CardboardBoxEntity;
import net.verity.entity.ClosedBoxEntity;
import net.verity.entity.VerityEntity;
import net.verity.entity.VerityMonsterEntity;
import net.verity.item.VerityInventoryItem;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.verity.ai.VerityLLMClient;
import net.verity.command.VerityCommand;
import net.verity.config.VerityConfig;
import net.verity.net.VoiceChatPayload;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VerityMod implements ModInitializer {
    public static final String MOD_ID = "verity";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    // Register Entity Types
    public static final EntityType<VerityEntity> VERITY_ENTITY = Registry.register(
            BuiltInRegistries.ENTITY_TYPE,
            ResourceLocation.parse(MOD_ID + ":verity"),
            EntityType.Builder.of(VerityEntity::new, MobCategory.CREATURE)
                    .sized(0.5F, 0.5F)
                    .build(MOD_ID + ":verity"));

    public static final EntityType<VerityMonsterEntity> VERITY_MONSTER_ENTITY = Registry.register(
            BuiltInRegistries.ENTITY_TYPE,
            ResourceLocation.parse(MOD_ID + ":verity_monster"),
            EntityType.Builder.of(VerityMonsterEntity::new, MobCategory.MONSTER)
                    .sized(1.2F, 5.0F) // Adjusted to match 5-block height model perfectly
                    .build(MOD_ID + ":verity_monster"));

    public static final EntityType<CardboardBoxEntity> CARDBOARD_BOX_ENTITY = Registry.register(
            BuiltInRegistries.ENTITY_TYPE,
            ResourceLocation.parse(MOD_ID + ":cardboard_box_entity"),
            EntityType.Builder.of(CardboardBoxEntity::new, MobCategory.MISC)
                    .sized(1.0F, 0.75F)
                    .build(MOD_ID + ":cardboard_box_entity"));

    public static final EntityType<ClosedBoxEntity> CLOSED_BOX_ENTITY = Registry.register(
            BuiltInRegistries.ENTITY_TYPE,
            ResourceLocation.parse(MOD_ID + ":closed_box_entity"),
            EntityType.Builder.of(ClosedBoxEntity::new, MobCategory.MISC)
                    .sized(0.8F, 0.8F)
                    .build(MOD_ID + ":closed_box_entity"));

    // Register Blocks
    public static final Block CARDBOARD_BOX = Registry.register(
            BuiltInRegistries.BLOCK,
            ResourceLocation.parse(MOD_ID + ":cardboard_box"),
            new CardboardBoxBlock(Block.Properties.of().strength(1.0F).sound(SoundType.WOOD)));

    // Register Items & Spawn Eggs
    public static final Item CARDBOARD_BOX_ITEM = Registry.register(
            BuiltInRegistries.ITEM,
            ResourceLocation.parse(MOD_ID + ":cardboard_box"),
            new BlockItem(CARDBOARD_BOX, new Item.Properties()));

    public static final Item VERITY_SPAWN_EGG = Registry.register(
            BuiltInRegistries.ITEM,
            ResourceLocation.parse(MOD_ID + ":verity_spawn_egg"),
            new SpawnEggItem(VERITY_ENTITY, 0xFFD700, 0x141414, new Item.Properties()) // Gold base, black spots
    );

    public static final Item VERITY_MONSTER_SPAWN_EGG = Registry.register(
            BuiltInRegistries.ITEM,
            ResourceLocation.parse(MOD_ID + ":verity_monster_spawn_egg"),
            new SpawnEggItem(VERITY_MONSTER_ENTITY, 0x141414, 0xFF0000, new Item.Properties()) // Black base, red spots
    );

    public static final Item VERITY_INVENTORY_1 = Registry.register(
            BuiltInRegistries.ITEM,
            ResourceLocation.parse(MOD_ID + ":verity_inventory_1"),
            new VerityInventoryItem(new Item.Properties().stacksTo(1), VerityEntity.FACE_SMILE, 1));

    public static final Item VERITY_INVENTORY_2 = Registry.register(
            BuiltInRegistries.ITEM,
            ResourceLocation.parse(MOD_ID + ":verity_inventory_2"),
            new VerityInventoryItem(new Item.Properties().stacksTo(1), VerityEntity.FACE_BORED_P2, 2));

    public static final Item VERITY_INVENTORY_3 = Registry.register(
            BuiltInRegistries.ITEM,
            ResourceLocation.parse(MOD_ID + ":verity_inventory_3"),
            new VerityInventoryItem(new Item.Properties().stacksTo(1), VerityEntity.FACE_ABNORMAL_OPEN, 2));

    // === Sounds ===
    public static final SoundEvent SOUND_MYGAL_NORMAL = registerSound("mygal_normal");
    public static final SoundEvent SOUND_PUNCH_BOX = registerSound("punchcardboardbox");

    private static SoundEvent registerSound(String name) {
        ResourceLocation id = ResourceLocation.parse(MOD_ID + ":" + name);
        return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
    }

    /** UUID текущего Verity в мире (синглтон — только один!) */
    private static UUID currentVerityUuid = null;

    private static final Map<UUID, Integer> INTRO_TIMERS = new HashMap<>();
    private static final Map<UUID, Integer> HELD_TALK_COOLDOWNS = new HashMap<>();

    // Статическое хранилище данных Verity когда он в руках (entity discarded)
    private static VerityEntity.VerityPhase heldPhase = VerityEntity.VerityPhase.HELPER;
    private static java.util.List<String> heldHistory = new java.util.ArrayList<>();
    private static java.util.List<String> heldFacts = new java.util.ArrayList<>();

    /** Сохранить данные Verity при поднятии в руки */
    public static void saveHeldData(VerityEntity.VerityPhase phase, java.util.List<String> history, java.util.List<String> facts) {
        heldPhase = phase;
        heldHistory = new java.util.ArrayList<>(history);
        heldFacts = new java.util.ArrayList<>(facts);
    }

    /** Восстановить данные Verity при выкидывании из рук */
    public static VerityEntity.VerityPhase getHeldPhase() { return heldPhase; }
    public static java.util.List<String> getHeldHistory() { return heldHistory; }
    public static java.util.List<String> getHeldFacts() { return heldFacts; }

    public static Item getInventoryItemForFace(int faceIndex, int phase) {
        return switch (faceIndex) {
            case VerityEntity.FACE_ABNORMAL_SHUT,
                    VerityEntity.FACE_ABNORMAL_OPEN,
                    VerityEntity.FACE_CREEPY_SMILE,
                    VerityEntity.FACE_SERIOUS_3 ->
                VERITY_INVENTORY_3;
            case VerityEntity.FACE_BORED_P2,
                    VerityEntity.FACE_DAY2_SHUT,
                    VerityEntity.FACE_DAY2_OPEN,
                    VerityEntity.FACE_SERIOUS_1,
                    VerityEntity.FACE_SERIOUS_2 ->
                VERITY_INVENTORY_2;
            default -> phase >= 2 ? VERITY_INVENTORY_2 : VERITY_INVENTORY_1;
        };
    }

    public static boolean isVerityInventoryItem(ItemStack stack) {
        if (stack.isEmpty())
            return false;
        Item item = stack.getItem();
        return item == VERITY_INVENTORY_1 || item == VERITY_INVENTORY_2 || item == VERITY_INVENTORY_3;
    }

    public static void armHeldTalk(Player player, int ticks) {
        if (!player.level().isClientSide) {
            HELD_TALK_COOLDOWNS.put(player.getUUID(), Math.max(0, ticks));
        }
    }

    private static void loadLLMConfig() {
        VerityConfig.load();
        VerityLLMClient.reloadFromConfig();
    }

    @Override
    public void onInitialize() {
        // Регистрируем команду /verity
        net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback.EVENT.register(
                (dispatcher, registryAccess, environment) -> VerityCommand.register(dispatcher));

        LOGGER.info("Initializing Verity Creepypasta Mod (Mojang Mappings)...");

        // Загружаем конфиг
        loadLLMConfig();
        LOGGER.info("Verity config: LLM={}, chat={}, sounds={}, villagerEat={}, teleport={}, telekinesis={}",
                VerityConfig.llmEnabled(), VerityConfig.chatEnabled(), VerityConfig.soundsEnabled(),
                VerityConfig.villagerEatingEnabled(), VerityConfig.teleportEnabled(),
                VerityConfig.doorTelekinesisEnabled());

        // Register Entity Attributes
        FabricDefaultAttributeRegistry.register(VERITY_ENTITY, VerityEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(VERITY_MONSTER_ENTITY, VerityMonsterEntity.createAttributes());

        // Add Spawn Eggs to Creative Menu
        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.SPAWN_EGGS).register(content -> {
            content.accept(VERITY_SPAWN_EGG);
            content.accept(VERITY_MONSTER_SPAWN_EGG);
            content.accept(CARDBOARD_BOX_ITEM);
            content.accept(VERITY_INVENTORY_1);
            content.accept(VERITY_INVENTORY_2);
            content.accept(VERITY_INVENTORY_3);
        });

        // Слушаем сообщения игроков в чате — Verity всегда отвечает (включая Monster
        // Form!)
        // По лору: Verity говорит даже в Monster Form («You are mine!», «Where is he?»)
        ServerMessageEvents.CHAT_MESSAGE.register((message, sender, params) -> {
            String msg = message.signedContent().toLowerCase();

            boolean alwaysRespond = VerityConfig.alwaysRespond();
            boolean isTriggered = alwaysRespond ||
                    msg.contains("verity") || msg.contains("верити") || msg.contains("шар") ||
                    msg.contains("?") || msg.contains("why") || msg.contains("почему") ||
                    msg.contains("кто") || msg.contains("who") || msg.contains("stop") ||
                    msg.contains("остановить") || msg.contains("danger") || msg.contains("опасно") ||
                    msg.contains("alone") || msg.contains("один") || msg.contains("pizza") ||
                    msg.contains("пицц") || msg.contains("sorry") || msg.contains("прости") ||
                    msg.contains("help") || msg.contains("помог") || msg.contains("friend") ||
                    msg.contains("друг") || msg.contains("leave") || msg.contains("уйди") ||
                    msg.contains("музык") || msg.contains("music") || msg.contains("my gal") ||
                    msg.contains("песн") || msg.contains("song") || msg.contains("спой") ||
                    msg.contains("gal") || msg.contains("мелод");

            if (!isTriggered) return;

            String playerMsg = message.signedContent();
            String lang = sender.clientInformation().language();
            if (lang == null || lang.isEmpty()) lang = "ru";
            else lang = lang.toLowerCase().startsWith("ru") ? "ru" : "en";

            // Сначала ищем entity Verity в мире
            var entities = sender.level().getEntitiesOfClass(
                    VerityEntity.class,
                    sender.getBoundingBox().inflate(64.0D),
                    e -> e.isAlive()
            );
            if (!entities.isEmpty()) {
                VerityEntity nearest = entities.get(0);

                // Музыка — мгновенно
                if (nearest.getDialogueController().isMusicRequestPublic(playerMsg)) {
                    nearest.getDialogueController().playMusicForPlayerPublic();
                    return;
                }

                nearest.getDialogueController().onPlayerMessage(sender.getName().getString(), playerMsg, lang);
                return;
            }

            // Verity нет в мире — может в руках?
            ItemStack heldStack = sender.getMainHandItem();
            if (isVerityInventoryItem(heldStack)) {
                // Музыка в руках
                String lower = playerMsg.toLowerCase();
                if (lower.contains("музык") || lower.contains("music") || lower.contains("my gal") ||
                    lower.contains("песн") || lower.contains("song") || lower.contains("спой") || lower.contains("мелод")) {
                    sender.level().playSound(null, sender.getX(), sender.getY(), sender.getZ(),
                            SOUND_MYGAL_NORMAL, SoundSource.RECORDS, 1.2F, 1.0F);
                    String prefix = (heldPhase == VerityEntity.VerityPhase.MONSTER || heldPhase == VerityEntity.VerityPhase.HUNTER)
                            ? "§4<Verity>" : "§e<Verity™>";
                    sender.sendSystemMessage(Component.literal(prefix + "§r ♪ My Gal..."));
                    return;
                }

                // LLM ответ через статические данные
                VerityEntity.VerityPhase phase = heldPhase;
                String playerName = sender.getName().getString();

                StringBuilder context = new StringBuilder();
                if (!heldFacts.isEmpty()) {
                    context.append("Известно об игроке: ").append(String.join(", ", heldFacts)).append(".\n");
                }
                context.append("Игрок держит тебя в руках. Ты в инвентаре.\n");

                net.verity.ai.VerityLLMClient.generateResponseAsync(
                        phase, playerName, playerMsg, heldHistory, lang, context.toString().trim(),
                        response -> {
                            sender.server.execute(() -> {
                                sender.sendSystemMessage(Component.literal(response));
                                heldHistory.add("§7" + playerName + "§r: " + playerMsg);
                                heldHistory.add(response);
                                while (heldHistory.size() > 40) heldHistory.remove(0);
                                net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(sender,
                                        new net.verity.net.TTSPayload(response));
                            });
                        });
            }
        });

        // ─── Voice Chat (STT) — регистрация пакета и обработчика ────────────
        PayloadTypeRegistry.playC2S().register(VoiceChatPayload.TYPE, VoiceChatPayload.STREAM_CODEC);
        ServerPlayNetworking.registerGlobalReceiver(VoiceChatPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            String text = payload.text();
            String lang = player.clientInformation().language();
            if (lang == null || lang.isEmpty()) lang = "ru";
            else lang = lang.toLowerCase().startsWith("ru") ? "ru" : "en";

            // Ищем entity Verity в мире
            var entities = player.level().getEntitiesOfClass(
                    VerityEntity.class,
                    player.getBoundingBox().inflate(64.0D),
                    e -> e.isAlive()
            );
            if (!entities.isEmpty()) {
                VerityEntity nearest = entities.get(0);
                nearest.getDialogueController().onPlayerMessage(
                        player.getName().getString(), text, lang);
                return;
            }

            // Verity в руках?
            ItemStack heldStack = player.getMainHandItem();
            if (isVerityInventoryItem(heldStack)) {
                // Музыка
                String lower = text.toLowerCase();
                if (lower.contains("музык") || lower.contains("music") || lower.contains("my gal") ||
                    lower.contains("песн") || lower.contains("song") || lower.contains("спой") || lower.contains("мелод")) {
                    player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                            SOUND_MYGAL_NORMAL, SoundSource.RECORDS, 1.2F, 1.0F);
                    String prefix = (heldPhase == VerityEntity.VerityPhase.MONSTER || heldPhase == VerityEntity.VerityPhase.HUNTER)
                            ? "§4<Verity>" : "§e<Verity™>";
                    player.sendSystemMessage(Component.literal(prefix + "§r ♪ My Gal..."));
                    return;
                }

                // LLM через статические данные
                String playerName = player.getName().getString();
                StringBuilder context2 = new StringBuilder();
                if (!heldFacts.isEmpty()) {
                    context2.append("Известно об игроке: ").append(String.join(", ", heldFacts)).append(".\n");
                }
                context2.append("Игрок держит тебя в руках. Ты в инвентаре.\n");

                net.verity.ai.VerityLLMClient.generateResponseAsync(
                        heldPhase, playerName, text, heldHistory, lang, context2.toString().trim(),
                        response -> {
                            player.server.execute(() -> {
                                player.sendSystemMessage(Component.literal(response));
                                heldHistory.add("§7" + playerName + "§r: " + text);
                                heldHistory.add(response);
                                while (heldHistory.size() > 40) heldHistory.remove(0);
                                net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player,
                                        new net.verity.net.TTSPayload(response));
                            });
                        });
            }
        });

        // ─── TTS — регистрация S2C пакета (сервер → клиент) ──────────────────
        PayloadTypeRegistry.playS2C().register(net.verity.net.TTSPayload.TYPE, net.verity.net.TTSPayload.STREAM_CODEC);

        ServerTickEvents.END_SERVER_TICK.register(VerityMod::tickHeldVerityItems);

        // Синглтон: только один Verity в мире. Следим каждый тик.
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            // Удаляем мёртвых/выгруженных из трекера
            net.verity.entity.VerityEntity.ACTIVE_VERITIES.removeIf(
                    e -> !e.isAlive() || e.isRemoved());

            // Если больше одного Verity — оставляем только первого (самого старого)
            if (net.verity.entity.VerityEntity.ACTIVE_VERITIES.size() > 1) {
                java.util.List<net.verity.entity.VerityEntity> all =
                        new java.util.ArrayList<>(net.verity.entity.VerityEntity.ACTIVE_VERITIES);
                net.verity.entity.VerityEntity keep = all.get(0);
                for (int i = 1; i < all.size(); i++) {
                    all.get(i).discard();
                }
                currentVerityUuid = keep.getUUID();
                net.verity.entity.VerityEntity.ACTIVE_VERITIES.removeIf(e -> e != keep && !e.isAlive());
            }
        });

        // Instant spawn cardboard box when player joins
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayer player = handler.getPlayer();
            if (!player.getTags().contains("verity_intro_complete") && !verityExists(server.overworld())) {
                // Spawn instantly on next tick
                INTRO_TIMERS.put(player.getUUID(), 1);
            }
        });

        // Monitor player join and place cardboard box based on PLAYER ROTATION (not
        // head look)
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (INTRO_TIMERS.isEmpty())
                return;

            Map<UUID, Integer> copy = new HashMap<>(INTRO_TIMERS);
            for (Map.Entry<UUID, Integer> entry : copy.entrySet()) {
                UUID uuid = entry.getKey();
                int ticks = entry.getValue();
                ServerPlayer player = server.getPlayerList().getPlayer(uuid);

                if (player == null || !player.isAlive()) {
                    INTRO_TIMERS.remove(uuid);
                    continue;
                }

                ticks--;
                if (ticks <= 0) {
                    INTRO_TIMERS.remove(uuid);

                    ServerLevel level = player.serverLevel();

                    // Use body Y-rotation so head turning doesn't break spawn
                    double yRad = Math.toRadians(player.getYRot());
                    double tx = player.getX() + Math.sin(yRad) * 4.0D;
                    double tz = player.getZ() - Math.cos(yRad) * 4.0D;
                    double ty = player.getY();

                    BlockPos spawnPos = findGround(level, BlockPos.containing(tx, ty, tz), player.blockPosition());
                    if (spawnPos == null) {
                        // Fallback: try again next tick (in case terrain changes)
                        INTRO_TIMERS.put(uuid, 1);
                        continue;
                    }

                    // Spawn CLOSED box entity (needs player click to open)
                    net.verity.entity.ClosedBoxEntity boxEntity = new net.verity.entity.ClosedBoxEntity(
                            CLOSED_BOX_ENTITY,
                            level);
                    boxEntity.setPos(spawnPos.getX() + 0.5D, spawnPos.getY() + 0.5D, spawnPos.getZ() + 0.5D);
                    level.addFreshEntity(boxEntity);

                    // Force look at box
                    Vec3 boxCenter = new Vec3(spawnPos.getX() + 0.5D, spawnPos.getY() + 0.5D, spawnPos.getZ() + 0.5D);
                    player.lookAt(EntityAnchorArgument.Anchor.EYES, boxCenter);

                    // Mark complete so it doesn't happen again
                    player.addTag("verity_intro_complete");
                } else {
                    INTRO_TIMERS.put(uuid, ticks);
                }
            }
        });
    }

    /**
     * Проверяет, существует ли уже Verity в любом измерении этого мира.
     * Использует in-memory трекер вместо медленного AABB-сканирования всего мира.
     */
    public static boolean verityExists(ServerLevel level) {
        // Сначала чистим мёртвых из трекера
        net.verity.entity.VerityEntity.ACTIVE_VERITIES.removeIf(
                e -> !e.isAlive() || e.isRemoved());
        return !net.verity.entity.VerityEntity.ACTIVE_VERITIES.isEmpty();
    }

    private static void tickHeldVerityItems(MinecraftServer server) {
        // Verity в руках НЕ говорит сам по себе — только отвечает на чат/голос
        // Убрано: авто-реплики и звуки — только чат/голос через LLM+TTS
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            ItemStack heldStack = player.getMainHandItem();
            if (!isVerityInventoryItem(heldStack)) {
                HELD_TALK_COOLDOWNS.remove(player.getUUID());
            }
        }
    }

    private static void speakFromHeldItem(ServerPlayer player, ItemStack stack) {
        boolean creepy = stack.getItem() == VERITY_INVENTORY_2 || stack.getItem() == VERITY_INVENTORY_3;

        // Verity в руках — entity нет в мире, используем статические данные
        String lang = player.clientInformation().language();
        if (lang == null || lang.isEmpty()) lang = "ru";
        else lang = lang.toLowerCase().startsWith("ru") ? "ru" : "en";

        // Создаём временный контекст и вызываем LLM напрямую
        VerityEntity.VerityPhase phase = heldPhase;
        String playerName = player.getName().getString();
        String message = "auto";

        // Простой контекст
        StringBuilder context = new StringBuilder();
        if (!heldFacts.isEmpty()) {
            context.append("Известно об игроке: ").append(String.join(", ", heldFacts)).append(".\n");
        }
        int px = player.getBlockX(), py = player.getBlockY(), pz = player.getBlockZ();
        context.append("Игрок на координатах: X=").append(px).append(" Y=").append(py).append(" Z=").append(pz).append(".\n");
        long time = player.level().getDayTime() % 24000;
        if (time < 6000) context.append("Сейчас утро.\n");
        else if (time < 12000) context.append("Сейчас день.\n");
        else if (time < 18000) context.append("Сейчас ночь.\n");
        else context.append("Сейчас глубокая ночь.\n");

        // Вызываем LLM напрямую
        net.verity.ai.VerityLLMClient.generateResponseAsync(
                phase, playerName, message, heldHistory, lang, context.toString().trim(),
                response -> {
                    player.server.execute(() -> {
                        player.sendSystemMessage(Component.literal(response));
                        heldHistory.add(response);
                        if (heldHistory.size() > 40) heldHistory.remove(0);
                        // TTS пакет
                        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player,
                                new net.verity.net.TTSPayload(response));
                    });
                });
    }

    private static BlockPos findGround(ServerLevel level, BlockPos targetPos, BlockPos fallbackCenter) {
        BlockPos exact = findGroundInColumn(level, targetPos);
        if (exact != null) {
            return exact;
        }

        for (int radius = 1; radius <= 4; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.abs(dx) != radius && Math.abs(dz) != radius) {
                        continue;
                    }

                    BlockPos candidate = findGroundInColumn(level, targetPos.offset(dx, 0, dz));
                    if (candidate != null) {
                        return candidate;
                    }
                }
            }
        }

        for (int radius = 1; radius <= 3; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    BlockPos candidate = findGroundInColumn(level, fallbackCenter.offset(dx, 0, dz));
                    if (candidate != null) {
                        return candidate;
                    }
                }
            }
        }

        return null;
    }

    private static BlockPos findGroundInColumn(ServerLevel level, BlockPos startPos) {
        // Strict scan: find TOP surface (top non-air block), then place BOX directly ON
        // TOP of it
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos(startPos.getX(), startPos.getY(),
                startPos.getZ());

        // Scan DOWN from high to find the highest non-air block
        BlockPos surface = null;
        for (int dy = 40; dy >= -64; dy--) {
            mutable.set(startPos.getX(), startPos.getY() + dy, startPos.getZ());
            BlockState state = level.getBlockState(mutable);
            if (!state.isAir() && !state.getCollisionShape(level, mutable).isEmpty()) {
                surface = mutable.immutable();
                break;
            }
        }

        if (surface != null) {
            // Place the box ON the surface block (not inside, not floating)
            // The box sits at surface.getY() + 1.0, on top of the block
            BlockPos boxPos = surface.above();
            // Verify the space above surface is clear (no blocks inside box hitbox)
            if (canPlaceIntroBoxAt(level, boxPos)) {
                return boxPos;
            }
        }

        // Fallback: try a few positions around startPos
        for (int dy = 0; dy >= -4; dy--) {
            mutable.set(startPos.getX(), startPos.getY() + dy, startPos.getZ());
            if (canPlaceIntroBoxAt(level, mutable)) {
                return mutable.immutable();
            }
        }

        return null;
    }

    private static boolean canPlaceIntroBoxAt(ServerLevel level, BlockPos pos) {
        if (!level.getWorldBorder().isWithinBounds(pos)) {
            return false;
        }

        BlockState state = level.getBlockState(pos);
        BlockState aboveState = level.getBlockState(pos.above());
        BlockPos belowPos = pos.below();
        BlockState belowState = level.getBlockState(belowPos);

        boolean replaceableSpace = state.getCollisionShape(level, pos).isEmpty();
        boolean headClear = aboveState.getCollisionShape(level, pos.above()).isEmpty();
        boolean solidGround = belowState.isFaceSturdy(level, belowPos, Direction.UP);

        return replaceableSpace && headClear && solidGround;
    }
}
