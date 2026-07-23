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
import net.verity.block.VerityAnchorBlock;
import net.verity.block.VerityAnchorBlockEntity;
import net.verity.block.VerityTerminalBlock;
import net.verity.block.VerityTerminalBlockEntity;
import net.verity.entity.CardboardBoxEntity;
import net.verity.entity.ClosedBoxEntity;
import net.verity.entity.VerityEntity;
import net.verity.entity.VerityMonsterEntity;
import net.verity.item.VerityInventoryItem;
import net.verity.item.VerityGunItem;
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
                    .sized(1.2F, 3.2F) // Hitbox smaller than visual model — model is scaled 2x in renderer
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

    public static final Block VERITY_ANCHOR = Registry.register(
            BuiltInRegistries.BLOCK,
            ResourceLocation.parse(MOD_ID + ":verity_anchor"),
            new VerityAnchorBlock(Block.Properties.of()
                    .strength(-1.0F, 3600000.0F) // Bedrock-level hardness
                    .sound(SoundType.NETHERITE_BLOCK)
                    .lightLevel(state -> 15)));

    public static final Block VERITY_TERMINAL = Registry.register(
            BuiltInRegistries.BLOCK,
            ResourceLocation.parse(MOD_ID + ":verity_terminal"),
            new VerityTerminalBlock(Block.Properties.of()
                    .strength(2.0F, 3600000.0F)
                    .sound(SoundType.METAL)
                    .lightLevel(state -> 7)));

    public static final Item VERITY_TERMINAL_ITEM = Registry.register(
            BuiltInRegistries.ITEM,
            ResourceLocation.parse(MOD_ID + ":verity_terminal"),
            new BlockItem(VERITY_TERMINAL, new Item.Properties()));

    public static final Item VERITY_GUN_ITEM = Registry.register(
            BuiltInRegistries.ITEM,
            ResourceLocation.parse(MOD_ID + ":verity_gun"),
            new VerityGunItem(new Item.Properties()
                    .stacksTo(1)
                    .durability(1)));

    // Register Block Entity Types
    public static final net.minecraft.world.level.block.entity.BlockEntityType<VerityAnchorBlockEntity> VERITY_ANCHOR_ENTITY =
            Registry.register(
                    BuiltInRegistries.BLOCK_ENTITY_TYPE,
                    ResourceLocation.parse(MOD_ID + ":verity_anchor"),
                    net.minecraft.world.level.block.entity.BlockEntityType.Builder.of(
                            VerityAnchorBlockEntity::new, VERITY_ANCHOR).build(null));

    public static final net.minecraft.world.level.block.entity.BlockEntityType<VerityTerminalBlockEntity> VERITY_TERMINAL_ENTITY =
            Registry.register(
                    BuiltInRegistries.BLOCK_ENTITY_TYPE,
                    ResourceLocation.parse(MOD_ID + ":verity_terminal"),
                    net.minecraft.world.level.block.entity.BlockEntityType.Builder.of(
                            VerityTerminalBlockEntity::new, VERITY_TERMINAL).build(null));

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

    public static final Item VERITY_ANCHOR_ITEM = Registry.register(
            BuiltInRegistries.ITEM,
            ResourceLocation.parse(MOD_ID + ":verity_anchor"),
            new BlockItem(VERITY_ANCHOR, new Item.Properties()));

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
            new VerityInventoryItem(new Item.Properties().stacksTo(1), VerityEntity.FACE_ABNORMAL_SHUT, 2));

    // === Sounds ===
    public static final SoundEvent SOUND_MYGAL_NORMAL = registerSound("mygal_normal");
    public static final SoundEvent SOUND_PUNCH_BOX = registerSound("punchcardboardbox");

    private static SoundEvent registerSound(String name) {
        ResourceLocation id = ResourceLocation.parse(MOD_ID + ":" + name);
        return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
    }

    /** Send music packet to client — client plays via OpenAL (looping by default) */
    public static void playMusic(net.minecraft.server.level.ServerPlayer player, SoundEvent soundEvent, SoundSource category, float volume, float pitch) {
        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player,
                new net.verity.net.PlayMusicPayload(soundEvent.getLocation().getPath(), volume, pitch, true));
    }

    /** Play music shorthand (loops) */
    public static void playMusic(net.minecraft.server.level.ServerPlayer player, String soundName, float volume, float pitch) {
        playMusic(player, SOUND_MYGAL_NORMAL, SoundSource.RECORDS, volume, pitch);
    }

    /** Play one-shot sound effect (does NOT loop). Respects the requested sound name. */
    public static void playSoundEffect(net.minecraft.server.level.ServerPlayer player, String soundName, String category, float volume, float pitch) {
        SoundEvent ev = switch (soundName) {
            case "punchcardboardbox" -> SOUND_PUNCH_BOX;
            default -> SOUND_MYGAL_NORMAL;
        };
        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player,
                new net.verity.net.PlayMusicPayload(ev.getLocation().getPath(), volume, pitch, false));
    }

    /** Stop music on client */
    public static void stopMusic(net.minecraft.server.level.ServerPlayer player) {
        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player,
                new net.verity.net.StopMusicPayload());
    }

    /** UUID текущего Verity в мире (синглтон — только один!) */
    private static UUID currentVerityUuid = null;

    private static final Map<UUID, Integer> INTRO_TIMERS = new HashMap<>();
    private static final Map<UUID, Integer> HELD_TALK_COOLDOWNS = new HashMap<>();
    private static final Map<UUID, String> LAST_VOICE_TEXT = new HashMap<>();
    private static final Map<UUID, Long> LAST_VOICE_TEXT_MS = new HashMap<>();
    private static final long VOICE_DUPLICATE_WINDOW_MS = 1_500L;

    /** Время последнего выхода игрока из мира (для Save/Quit Reaction) */
    private static final Map<UUID, Long> LAST_LOGOUT_TIMES = new HashMap<>();

    // Статическое хранилище данных Verity когда он в руках (entity discarded)
    private static VerityEntity.VerityPhase heldPhase = VerityEntity.VerityPhase.HELPER;
    private static int heldFace = VerityEntity.FACE_SMILE;
    private static java.util.List<String> heldHistory = new java.util.ArrayList<>();
    private static java.util.List<String> heldFacts = new java.util.ArrayList<>();

    /** Сохранить данные Verity при поднятии в руки */
    public static void saveHeldData(VerityEntity.VerityPhase phase, int face, java.util.List<String> history, java.util.List<String> facts) {
        heldPhase = phase;
        heldFace = face;
        heldHistory = new java.util.ArrayList<>(history);
        heldFacts = new java.util.ArrayList<>(facts);
    }

    /** Восстановить данные Verity при выкидывании из рук */
    public static VerityEntity.VerityPhase getHeldPhase() { return heldPhase; }
    public static int getHeldFace() { return heldFace; }
    public static java.util.List<String> getHeldHistory() { return heldHistory; }
    public static java.util.List<String> getHeldFacts() { return heldFacts; }

    public static record PlayerSystemContext(
            String pcName,
            String osName,
            String osVersion,
            String osArch,
            String userName,
            String userHome,
            String cpuName,
            int cpuCores,
            int totalMemoryGB,
            int maxJvmMemoryMB,
            String gpuName,
            int screenWidth,
            int screenHeight,
            String gameDirectory,
            String localTime,
            String timezone,
            int fps,
            float masterVolume,
            String installedGames
    ) {}

    private static final Map<UUID, PlayerSystemContext> PLAYER_SYSTEM_CONTEXTS = new HashMap<>();

    public static void setPlayerSystemContext(UUID uuid, PlayerSystemContext context) {
        PLAYER_SYSTEM_CONTEXTS.put(uuid, context);
    }

    public static PlayerSystemContext getPlayerSystemContext(UUID uuid) {
        return PLAYER_SYSTEM_CONTEXTS.get(uuid);
    }

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

        // Инициализируем worldgen (Лаборатория Verity)
        net.verity.worldgen.VerityStructures.init();

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
            content.accept(VERITY_ANCHOR_ITEM);
            content.accept(VERITY_TERMINAL_ITEM);
            content.accept(VERITY_GUN_ITEM);
            content.accept(VERITY_INVENTORY_1);
            content.accept(VERITY_INVENTORY_2);
            content.accept(VERITY_INVENTORY_3);
        });

        // ─── Chat Interception: Verity перехватывает сообщения о его уничтожении ─
        ServerMessageEvents.ALLOW_CHAT_MESSAGE.register((message, sender, params) -> {
            String msg = message.signedContent().toLowerCase();
            // Проверяем ключевые слова о убийстве/удалении Verity
            boolean hostile = msg.contains("kill") || msg.contains("убь") || msg.contains("убит") ||
                    msg.contains("delete") || msg.contains("удал") || msg.contains("уничтож") ||
                    msg.contains("trap") || msg.contains("ловушк") || msg.contains("запереть") ||
                    msg.contains("izбав") || msg.contains("избав") || msg.contains("anchor") ||
                    msg.contains("якор") || msg.contains("anchor");
            if (!hostile) return true; // пропускаем

            // Ищем Verity в мире и проверяем фазу
            var verities = sender.level().getEntitiesOfClass(
                    VerityEntity.class,
                    sender.getBoundingBox().inflate(1024.0D),
                    e -> e.isAlive());
            if (!verities.isEmpty()) {
                VerityEntity verity = verities.get(0);
                VerityEntity.VerityPhase phase = verity.getVerityPhase();
                // Перехватываем только начиная с OMNISCIENT
                if (phase.ordinal() >= VerityEntity.VerityPhase.OMNISCIENT.ordinal()) {
                    // Блокируем сообщение (игрок не видит его в чате)
                    sender.sendSystemMessage(Component.literal(
                            "\u00a7c<Verity\u2122>\u00a7r \u042f \u0441\u043b\u044b\u0448\u0443."));
                    verity.setTalkAnimTick(40);
                    verity.getDialogueController().addFactPublic(
                            "\u0438\u0433\u0440\u043e\u043a \u043f\u043b\u0430\u043d\u0438\u0440\u0443\u0435\u0442 \u043f\u0440\u043e\u0442\u0438\u0432 Verity");
                    return false; // отменяем отправку
                }
            }
            return true;
        });

        // Слушаем сообщения игроков в чате — Verity всегда отвечает (включая Monster
        // Form!)
        // По лору: Verity говорит даже в Monster Form («You are mine!», «Where is he?»)
        ServerMessageEvents.CHAT_MESSAGE.register((message, sender, params) -> {
            String msg = message.signedContent().toLowerCase();

            boolean alwaysRespond = VerityConfig.alwaysRespond();
            boolean isTriggered = alwaysRespond ||
                    msg.contains("verity") || msg.contains("\u0432\u0435\u0440\u0438\u0442\u0438") || msg.contains("\u0448\u0430\u0440") ||
                    msg.contains("?") || msg.contains("why") || msg.contains("\u043F\u043E\u0447\u0435\u043C\u0443") ||
                    msg.contains("\u043A\u0442\u043E") || msg.contains("who") || msg.contains("stop") ||
                    msg.contains("\u043E\u0441\u0442\u0430\u043D\u043E\u0432\u0438\u0442\u044C") || msg.contains("danger") || msg.contains("\u043E\u043F\u0430\u0441\u043D\u043E") ||
                    msg.contains("alone") || msg.contains("\u043E\u0434\u0438\u043D") || msg.contains("pizza") ||
                    msg.contains("\u043F\u0438\u0446\u0446") || msg.contains("sorry") || msg.contains("\u043F\u0440\u043E\u0441\u0442\u0438") ||
                    msg.contains("help") || msg.contains("\u043F\u043E\u043C\u043E\u0433") || msg.contains("friend") ||
                    msg.contains("\u0434\u0440\u0443\u0433") || msg.contains("leave") || msg.contains("\u0443\u0439\u0434\u0438") ||
                    msg.contains("\u043C\u0443\u0437\u044B\u043A") || msg.contains("music") || msg.contains("my gal") ||
                    msg.contains("\u043F\u0435\u0441\u043D") || msg.contains("song") || msg.contains("\u0441\u043F\u043E\u0439") ||
                    msg.contains("gal") || msg.contains("\u043C\u0435\u043B\u043E\u0434") ||
                    msg.contains("\u0437\u043D\u0430\u0435\u0448\u044C") || msg.contains("\u0440\u0430\u0441\u0441\u043A\u0430\u0436\u0438") ||
                    msg.contains("\u043C\u043E\u0436\u0435\u0448\u044C") || msg.contains("\u0441\u043A\u0430\u0436\u0438") ||
                    msg.contains("\u043F\u043E\u0448\u043B\u0438") || msg.contains("\u0432\u0435\u0434\u0438") || msg.contains("follow") ||
                    msg.contains("\u043E\u0442\u0432\u0435\u0434\u0438") || msg.contains("\u0434\u043E\u0432\u0435\u0434\u0438") || msg.contains("\u043F\u0440\u043E\u0432\u0435\u0434\u0438") ||
                    msg.contains("\u0441\u0442\u043E\u0439") || msg.contains("\u0445\u0432\u0430\u0442\u0438\u0442") || msg.contains("show me");

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
                if (lower.contains("\u043C\u0443\u0437\u044B\u043A") || lower.contains("music") || lower.contains("my gal") ||
                    lower.contains("\u043F\u0435\u0441\u043D") || lower.contains("song") || lower.contains("\u0441\u043F\u043E\u0439") || lower.contains("\u043C\u0435\u043B\u043E\u0434")) {
                    playMusic(sender, "mygal_normal", 1.2F, 1.0F);
                    String prefix = (heldPhase == VerityEntity.VerityPhase.MONSTER || heldPhase == VerityEntity.VerityPhase.HUNTER)
                            ? "\u00A74<Verity>" : "\u00A7e<Verity\u2122>";
                    sender.sendSystemMessage(Component.literal(prefix + "\u00A7r \u266A My Gal..."));
                    return;
                }

                // LLM ответ через статические данные
                VerityEntity.VerityPhase phase = heldPhase;
                String playerName = sender.getName().getString();

                StringBuilder context = new StringBuilder();
                if (!heldFacts.isEmpty()) {
                    context.append("\u0418\u0437\u0432\u0435\u0441\u0442\u043D\u043E \u043E\u0431 \u0438\u0433\u0440\u043E\u043A\u0435: ").append(String.join(", ", heldFacts)).append(".\n");
                }
                context.append("\u0418\u0433\u0440\u043E\u043A \u0434\u0435\u0440\u0436\u0438\u0442 \u0442\u0435\u0431\u044F \u0432 \u0440\u0443\u043A\u0430\u0445. \u0422\u044B \u0432 \u0438\u043D\u0432\u0435\u043D\u0442\u0430\u0440\u0435.\n");

                net.verity.ai.VerityLLMClient.generateResponseAsync(
                        phase, playerName, playerMsg, heldHistory, lang, context.toString().trim(),
                        response -> {
                            sender.server.execute(() -> {
                                sender.sendSystemMessage(Component.literal(response));
                                heldHistory.add("\u00A77" + playerName + "\u00A7r: " + playerMsg);
                                heldHistory.add(response);
                                while (heldHistory.size() > 40) heldHistory.remove(0);
                                net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(sender,
                                        new net.verity.net.TTSPayload(response));
                            });
                        });
            }
        });

        // ─── Terminal Open — регистрация S2C пакета ─────────────────────────
        PayloadTypeRegistry.playS2C().register(net.verity.net.TerminalOpenPayload.TYPE, net.verity.net.TerminalOpenPayload.STREAM_CODEC);

        // ─── Trigger Final Phase — C2S пакет ───────────────────────────────
        PayloadTypeRegistry.playC2S().register(net.verity.net.TriggerFinalPhasePayload.TYPE, net.verity.net.TriggerFinalPhasePayload.STREAM_CODEC);
        ServerPlayNetworking.registerGlobalReceiver(net.verity.net.TriggerFinalPhasePayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            // Find nearest alive Verity entity and set it to FINAL phase
            var entities = player.level().getEntitiesOfClass(
                    net.verity.entity.VerityEntity.class,
                    player.getBoundingBox().inflate(64.0D),
                    e -> !e.isRemoved());
            if (!entities.isEmpty()) {
                net.verity.entity.VerityEntity verity = entities.get(0);
                verity.setVerityPhase(net.verity.entity.VerityEntity.VerityPhase.FINAL);
                LOGGER.info("Player {} triggered FINAL phase on Verity via terminal", player.getName().getString());
            }
        });

        // ─── Client PC Context — регистрация пакета и обработчика ───────────
        PayloadTypeRegistry.playC2S().register(net.verity.net.ClientContextPayload.TYPE, net.verity.net.ClientContextPayload.STREAM_CODEC);
        ServerPlayNetworking.registerGlobalReceiver(net.verity.net.ClientContextPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            PlayerSystemContext sysContext = new PlayerSystemContext(
                    payload.pcName(),
                    payload.osName(),
                    payload.osVersion(),
                    payload.osArch(),
                    payload.userName(),
                    payload.userHome(),
                    payload.cpuName(),
                    payload.cpuCores(),
                    payload.totalMemoryGB(),
                    payload.maxJvmMemoryMB(),
                    payload.gpuName(),
                    payload.screenWidth(),
                    payload.screenHeight(),
                    payload.gameDirectory(),
                    payload.localTime(),
                    payload.timezone(),
                    payload.fps(),
                    payload.masterVolume(),
                    payload.installedGames()
            );
            setPlayerSystemContext(player.getUUID(), sysContext);
            LOGGER.info("Received system context for player {}: OS={}, PC={}, GPU={}", 
                    player.getName().getString(), sysContext.osName(), sysContext.pcName(), sysContext.gpuName());
        });

        PayloadTypeRegistry.playC2S().register(net.verity.net.ClientClockPayload.TYPE,
                net.verity.net.ClientClockPayload.STREAM_CODEC);
        ServerPlayNetworking.registerGlobalReceiver(net.verity.net.ClientClockPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            PlayerSystemContext previous = getPlayerSystemContext(player.getUUID());
            if (previous != null) {
                setPlayerSystemContext(player.getUUID(), new PlayerSystemContext(
                        previous.pcName(), previous.osName(), previous.osVersion(), previous.osArch(),
                        previous.userName(), previous.userHome(), previous.cpuName(), previous.cpuCores(),
                        previous.totalMemoryGB(), previous.maxJvmMemoryMB(), previous.gpuName(),
                        previous.screenWidth(), previous.screenHeight(), previous.gameDirectory(),
                        payload.localTime(), previous.timezone(), previous.fps(), previous.masterVolume(),
                        previous.installedGames()));
            }
        });

        // ─── Voice Chat (STT) — регистрация пакета и обработчика ────────────
        PayloadTypeRegistry.playC2S().register(VoiceChatPayload.TYPE, VoiceChatPayload.STREAM_CODEC);
        ServerPlayNetworking.registerGlobalReceiver(VoiceChatPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            String rawText = payload.text();
            if (rawText == null || rawText.isBlank()) return;
            
            final String text = rawText.trim();

            long now = System.currentTimeMillis();
            UUID playerId = player.getUUID();
            String normalizedText = text.toLowerCase(java.util.Locale.ROOT);
            String lastText = LAST_VOICE_TEXT.get(playerId);
            long lastMs = LAST_VOICE_TEXT_MS.getOrDefault(playerId, 0L);
            if (normalizedText.equals(lastText) && now - lastMs < VOICE_DUPLICATE_WINDOW_MS) {
                LOGGER.debug("VoiceChat: duplicate '{}' ignored after {}ms", text, now - lastMs);
                return;
            }
            LAST_VOICE_TEXT.put(playerId, normalizedText);
            LAST_VOICE_TEXT_MS.put(playerId, now);

            String lang = player.clientInformation().language();
            if (lang == null || lang.isEmpty()) lang = "ru";
            else lang = lang.toLowerCase().startsWith("ru") ? "ru" : "en";

            // Ищем entity Verity в мире (увеличенный радиус слышимости до 1024 блоков)
            var entities = player.level().getEntitiesOfClass(
                    VerityEntity.class,
                    player.getBoundingBox().inflate(1024.0D),
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
                if (lower.contains("\u043C\u0443\u0437\u044B\u043A") || lower.contains("music") || lower.contains("my gal") ||
                    lower.contains("\u043F\u0435\u0441\u043D") || lower.contains("song") || lower.contains("\u0441\u043F\u043E\u0439") || lower.contains("\u043C\u0435\u043B\u043E\u0434")) {
                    playMusic(player, "mygal_normal", 1.2F, 1.0F);
                    String prefix = (heldPhase == VerityEntity.VerityPhase.MONSTER || heldPhase == VerityEntity.VerityPhase.HUNTER)
                            ? "\u00A74<Verity>" : "\u00A7e<Verity\u2122>";
                    player.sendSystemMessage(Component.literal(prefix + "\u00A7r \u266A My Gal..."));
                    return;
                }

                // LLM через статические данные
                String playerName = player.getName().getString();
                StringBuilder context2 = new StringBuilder();
                if (!heldFacts.isEmpty()) {
                    context2.append("\u0418\u0437\u0432\u0435\u0441\u0442\u043D\u043E \u043E\u0431 \u0438\u0433\u0440\u043E\u043A\u0435: ").append(String.join(", ", heldFacts)).append(".\n");
                }
                context2.append("\u0418\u0433\u0440\u043E\u043A \u0434\u0435\u0440\u0436\u0438\u0442 \u0442\u0435\u0431\u044F \u0432 \u0440\u0443\u043A\u0430\u0445. \u0422\u044B \u0432 \u0438\u043D\u0432\u0435\u043D\u0442\u0430\u0440\u0435.\n");

                net.verity.ai.VerityLLMClient.generateResponseAsync(
                        heldPhase, playerName, text, heldHistory, lang, context2.toString().trim(),
                        response -> {
                            player.server.execute(() -> {
                                player.sendSystemMessage(Component.literal(response));
                                heldHistory.add("\u00A77" + playerName + "\u00A7r: " + text);
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

        // ─── Music — регистрация S2C пакета (сервер → клиент) ──────────────
        PayloadTypeRegistry.playS2C().register(net.verity.net.PlayMusicPayload.TYPE, net.verity.net.PlayMusicPayload.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(net.verity.net.StopMusicPayload.TYPE, net.verity.net.StopMusicPayload.STREAM_CODEC);

        ServerTickEvents.END_SERVER_TICK.register(VerityMod::tickHeldVerityItems);

        // Q-drop detection: convert dropped Verity items into thrown Verity entities
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerLevel level : server.getAllLevels()) {
                for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                    if (player.level() != level) continue;
                    // Scan around each player for freshly dropped Verity items
                    var itemEntities = level.getEntitiesOfClass(
                            net.minecraft.world.entity.item.ItemEntity.class,
                            player.getBoundingBox().inflate(16.0D));
                    for (var itemEntity : itemEntities) {
                        ItemStack stack = itemEntity.getItem();
                        if (!isVerityInventoryItem(stack)) continue;
                        if (itemEntity.tickCount > 5) continue; // only freshly dropped
                        // Don't convert if thrower is too far
                        if (itemEntity.distanceToSqr(player) > 256.0D) continue;

                        // Spawn Verity entity above the item (player's eye level) to avoid collision with player
                        VerityEntity entity = new VerityEntity(VERITY_ENTITY, level);
                        entity.setVerityPhase(getHeldPhase());
                        entity.setFaceIndex(getHeldFace());
                        double spawnY = Math.max(itemEntity.getY(), player.getY() + player.getEyeHeight() + 0.5);
                        entity.moveTo(itemEntity.getX(), spawnY, itemEntity.getZ(),
                                player.getYRot(), 0.0F);

                        level.addFreshEntity(entity);
                        entity.getDialogueController().setDialogueHistory(getHeldHistory());
                        entity.getDialogueController().setKnownFacts(getHeldFacts());

                        // Throw in player's look direction
                        entity.throwVerity(player);

                        // Remove the dropped item
                        itemEntity.discard();

                        // Consume one item from stack
                        stack.shrink(1);
                    }
                }
            }
        });

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

        // ─── Save/Quit Reaction — запоминаем время выхода ─────────────────────
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            ServerPlayer player = handler.getPlayer();
            LAST_LOGOUT_TIMES.put(player.getUUID(), System.currentTimeMillis());
        });

        // ─── Anchor Block — detect pickaxe attack attempt → trigger Final Phase ──
        net.fabricmc.fabric.api.event.player.AttackBlockCallback.EVENT.register((player, level, hand, pos, direction) -> {
            if (!level.isClientSide && level.getBlockState(pos).getBlock() == VERITY_ANCHOR) {
                var verities = level.getEntitiesOfClass(
                        net.verity.entity.VerityEntity.class,
                        new net.minecraft.world.phys.AABB(pos).inflate(256.0),
                        e -> e.isAlive());
                if (!verities.isEmpty()) {
                    verities.get(0).setVerityPhase(net.verity.entity.VerityEntity.VerityPhase.FINAL);
                } else if (player instanceof ServerPlayer sp) {
                    // No Verity alive — still trigger for the speech if possible
                }
                return net.minecraft.world.InteractionResult.FAIL;
            }
            return net.minecraft.world.InteractionResult.PASS;
        });

        // Welcome message + spawn cardboard box when player joins
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayer player = handler.getPlayer();
            if (!player.getTags().contains("verity_intro_complete")) {
                String lang = player.clientInformation().language();
                boolean ru = lang != null && lang.toLowerCase().startsWith("ru");

                if (ru) {
                    player.sendSystemMessage(Component.literal(
                            "\u00A7e\u00A7l Verity\u2122 \u00A7r\u00A77\u2014 \u043F\u0441\u0438\u0445\u043E\u043B\u043E\u0433\u0438\u0447\u0435\u0441\u043A\u0438\u0439 \u0445\u043E\u0440\u0440\u043E\u0440 \u043C\u043E\u0434"));
                    player.sendSystemMessage(Component.literal(
                            "\u00A77\u0412\u0430\u043C \u0431\u044B\u043B \u043F\u0440\u0438\u0441\u043B\u0430\u043D \u043A\u0430\u0440\u0442\u043E\u043D\u043D\u044B\u0439 \u044F\u0449\u0438\u043A. \u041E\u0442\u043A\u0440\u043E\u0439\u0442\u0435 \u0435\u0433\u043E \u2014 \u0432\u043D\u0443\u0442\u0440\u0438 \u0436\u0434\u0451\u0442 Verity."));
                    player.sendSystemMessage(Component.literal(
                            "\u00A77Verity \u2014 \u0436\u0451\u043B\u0442\u044B\u0439 \u0448\u0430\u0440, \u043A\u043E\u0442\u043E\u0440\u044B\u0439 \u0437\u043D\u0430\u0435\u0442 \u0432\u0441\u0451. \u041E\u043D \u0431\u044B\u043B \u0434\u0440\u0443\u0433\u043E\u043C. \u041F\u043E\u043A\u0430."));
                    player.sendSystemMessage(Component.literal(
                            "\u00A7e\u0414\u043B\u044F \u043F\u043E\u043B\u043D\u043E\u0439 \u0438\u043D\u0444\u043E\u0440\u043C\u0430\u0446\u0438\u0438: \u00A7f/verity help"));
                } else {
                    player.sendSystemMessage(Component.literal(
                            "\u00A7e\u00A7l Verity\u2122 \u00A7r\u00A77\u2014 A Psychological Horror Mod"));
                    player.sendSystemMessage(Component.literal(
                            "\u00A77A cardboard box has been sent to you. Open it \u2014 Verity is waiting inside."));
                    player.sendSystemMessage(Component.literal(
                            "\u00A77Verity is a yellow ball that knows everything. He was a friend. For now."));
                    player.sendSystemMessage(Component.literal(
                            "\u00A7eFor full information: \u00A7f/verity help"));
                }
                if (!verityExists(server.overworld())) {
                    INTRO_TIMERS.put(player.getUUID(), 1);
                }
            } else {
                // ─── Save/Quit Reaction: игрок возвращается ───
                Long lastLogout = LAST_LOGOUT_TIMES.remove(player.getUUID());
                if (lastLogout != null && verityExists(server.overworld())) {
                    long diffMs = System.currentTimeMillis() - lastLogout;
                    long diffMin = diffMs / (1000 * 60);
                    long diffHours = diffMin / 60;

                    String timeStr;
                    if (diffHours > 0) {
                        timeStr = diffHours + " \u0447\u0430\u0441\u043e\u0432 " + (diffMin % 60) + " \u043c\u0438\u043d\u0443\u0442";
                    } else if (diffMin > 0) {
                        timeStr = diffMin + " \u043c\u0438\u043d\u0443\u0442";
                    } else {
                        timeStr = (diffMs / 1000) + " \u0441\u0435\u043a\u0443\u043d\u0434";
                    }

                    // Ищем ближайшего Verity в мире
                    var verities = server.overworld().getEntitiesOfClass(
                            VerityEntity.class,
                            player.getBoundingBox().inflate(1024.0D),
                            e -> e.isAlive());
                    if (!verities.isEmpty()) {
                        VerityEntity verity = verities.get(0);
                        VerityEntity.VerityPhase phase = verity.getVerityPhase();

                        String msg;
                        if (phase == VerityEntity.VerityPhase.HELPER || phase == VerityEntity.VerityPhase.OMNISCIENT) {
                            msg = "\u00a7e<Verity\u2122>\u00a7r \u0422\u044b \u0431\u044b\u043b \u0434\u0430\u043b\u0435\u043a\u043e " + timeStr + ". \u042f \u0436\u0434\u0430\u043b.";
                        } else {
                            msg = "\u00a7e<Verity\u2122>\u00a7r \u0422\u044b \u0443\u0448\u0451\u043b \u043d\u0430 " + timeStr + ". \u042f \u0441\u0447\u0438\u0442\u0430\u043b \u043a\u0430\u0436\u0434\u0443\u044e \u0441\u0435\u043a\u0443\u043d\u0434\u0443.";
                        }
                        player.sendSystemMessage(Component.literal(msg));
                        if (player instanceof net.minecraft.server.level.ServerPlayer sp) {
                            net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(sp,
                                    new net.verity.net.TTSPayload(msg));
                        }
                        verity.setTalkAnimTick(40);
                    }
                }
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
            context.append("\u0418\u0437\u0432\u0435\u0441\u0442\u043D\u043E \u043E\u0431 \u0438\u0433\u0440\u043E\u043A\u0435: ").append(String.join(", ", heldFacts)).append(".\n");
        }
        int px = player.getBlockX(), py = player.getBlockY(), pz = player.getBlockZ();
        context.append("\u0418\u0433\u0440\u043E\u043A \u043D\u0430 \u043A\u043E\u043E\u0440\u0434\u0438\u043D\u0430\u0442\u0430\u0445: X=").append(px).append(" Y=").append(py).append(" Z=").append(pz).append(".\n");
        long time = player.level().getDayTime() % 24000;
        if (time < 6000) context.append("\u0421\u0435\u0439\u0447\u0430\u0441 \u0443\u0442\u0440\u043E.\n");
        else if (time < 12000) context.append("\u0421\u0435\u0439\u0447\u0430\u0441 \u0434\u0435\u043D\u044C.\n");
        else if (time < 18000) context.append("\u0421\u0435\u0439\u0447\u0430\u0441 \u043D\u043E\u0447\u044C.\n");
        else context.append("\u0421\u0435\u0439\u0447\u0430\u0441 \u0433\u043B\u0443\u0431\u043E\u043A\u0430\u044F \u043D\u043E\u0447\u044C.\n");

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
