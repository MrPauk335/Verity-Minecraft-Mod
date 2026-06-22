package net.verity;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
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
import net.verity.entity.VerityEntity;
import net.verity.entity.VerityMonsterEntity;
import net.verity.item.VerityInventoryItem;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
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
                    .sized(0.6F, 0.6F)
                    .build(MOD_ID + ":verity")
    );

    public static final EntityType<VerityMonsterEntity> VERITY_MONSTER_ENTITY = Registry.register(
            BuiltInRegistries.ENTITY_TYPE,
            ResourceLocation.parse(MOD_ID + ":verity_monster"),
            EntityType.Builder.of(VerityMonsterEntity::new, MobCategory.MONSTER)
                    .sized(1.2F, 5.0F) // Adjusted to match 5-block height model perfectly
                    .build(MOD_ID + ":verity_monster")
    );

    public static final EntityType<CardboardBoxEntity> CARDBOARD_BOX_ENTITY = Registry.register(
            BuiltInRegistries.ENTITY_TYPE,
            ResourceLocation.parse(MOD_ID + ":cardboard_box_entity"),
            EntityType.Builder.of(CardboardBoxEntity::new, MobCategory.MISC)
                    .sized(1.0F, 0.75F)
                    .build(MOD_ID + ":cardboard_box_entity")
    );

    // Register Blocks
    public static final Block CARDBOARD_BOX = Registry.register(
            BuiltInRegistries.BLOCK,
            ResourceLocation.parse(MOD_ID + ":cardboard_box"),
            new CardboardBoxBlock(Block.Properties.of().strength(1.0F).sound(SoundType.WOOD))
    );

    // Register Items & Spawn Eggs
    public static final Item CARDBOARD_BOX_ITEM = Registry.register(
            BuiltInRegistries.ITEM,
            ResourceLocation.parse(MOD_ID + ":cardboard_box"),
            new BlockItem(CARDBOARD_BOX, new Item.Properties())
    );

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
            new VerityInventoryItem(new Item.Properties().stacksTo(1), VerityEntity.FACE_SMILE, 1)
    );

    public static final Item VERITY_INVENTORY_2 = Registry.register(
            BuiltInRegistries.ITEM,
            ResourceLocation.parse(MOD_ID + ":verity_inventory_2"),
            new VerityInventoryItem(new Item.Properties().stacksTo(1), VerityEntity.FACE_BORED_P2, 2)
    );

    public static final Item VERITY_INVENTORY_3 = Registry.register(
            BuiltInRegistries.ITEM,
            ResourceLocation.parse(MOD_ID + ":verity_inventory_3"),
            new VerityInventoryItem(new Item.Properties().stacksTo(1), VerityEntity.FACE_ABNORMAL_OPEN, 2)
    );

    // === Sounds (extracted from original Bedrock addon) ===
    public static final SoundEvent SOUND_HELLO = registerSound("hello");
    public static final SoundEvent SOUND_WHOSTHERE = registerSound("whosthere");
    public static final SoundEvent SOUND_ASKME = registerSound("askme");
    public static final SoundEvent SOUND_KNOW_EVERYTHING = registerSound("know_everything");
    public static final SoundEvent SOUND_OPENING = registerSound("opening");
    public static final SoundEvent SOUND_NO1 = registerSound("no1");
    public static final SoundEvent SOUND_NO2 = registerSound("no2");
    public static final SoundEvent SOUND_GONE = registerSound("gone");
    public static final SoundEvent SOUND_SOMETHING_PASSED = registerSound("something_passed");
    public static final SoundEvent SOUND_SOMETHING_HUNGRY = registerSound("something_hungry");
    public static final SoundEvent SOUND_IM_SMILING_NOW = registerSound("im_smiling_now");
    public static final SoundEvent SOUND_ALWAYS_LOOKED_LIKE_THIS = registerSound("always_looked_like_this");
    public static final SoundEvent SOUND_ITS_ALREADY_OVER = registerSound("its_already_over");
    public static final SoundEvent SOUND_YOU_ARE_MINE = registerSound("you_are_mine");
    public static final SoundEvent SOUND_VILLAGERS_GONE = registerSound("villagers_gone");
    public static final SoundEvent SOUND_YES_SOUTH = registerSound("yes_south");
    public static final SoundEvent SOUND_SOMETHING_COMING = registerSound("somethingiscomingin3days");
    public static final SoundEvent SOUND_LOUDMUSIC = registerSound("loudmusic");
    public static final SoundEvent SOUND_CHASE = registerSound("chase");
    public static final SoundEvent SOUND_MYGAL_FOREST = registerSound("mygal_forest");
    public static final SoundEvent SOUND_MYGAL_NORMAL = registerSound("mygal_normal");
    public static final SoundEvent SOUND_JUMPSCARE = registerSound("jumpscare");
    public static final SoundEvent SOUND_PUNCH_BOX = registerSound("punchcardboardbox");

    private static SoundEvent registerSound(String name) {
        ResourceLocation id = ResourceLocation.parse(MOD_ID + ":" + name);
        return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
    }

    private static final Map<UUID, Integer> INTRO_TIMERS = new HashMap<>();
    private static final Map<UUID, Integer> HELD_TALK_COOLDOWNS = new HashMap<>();
    private static final String[] HELD_FRIENDLY_MESSAGE_KEYS = {
            "message.verity.held.friendly.0",
            "message.verity.held.friendly.1",
            "message.verity.held.friendly.2"
    };
    private static final String[] HELD_CREEPY_MESSAGE_KEYS = {
            "message.verity.held.creepy.0",
            "message.verity.held.creepy.1",
            "message.verity.held.creepy.2"
    };

    public static Item getInventoryItemForFace(int faceIndex, int phase) {
        return switch (faceIndex) {
            case VerityEntity.FACE_ABNORMAL_SHUT,
                 VerityEntity.FACE_ABNORMAL_OPEN,
                 VerityEntity.FACE_CREEPY_SMILE,
                 VerityEntity.FACE_SERIOUS_3 -> VERITY_INVENTORY_3;
            case VerityEntity.FACE_BORED_P2,
                 VerityEntity.FACE_DAY2_SHUT,
                 VerityEntity.FACE_DAY2_OPEN,
                 VerityEntity.FACE_SERIOUS_1,
                 VerityEntity.FACE_SERIOUS_2 -> VERITY_INVENTORY_2;
            default -> phase >= 2 ? VERITY_INVENTORY_2 : VERITY_INVENTORY_1;
        };
    }

    public static boolean isVerityInventoryItem(ItemStack stack) {
        if (stack.isEmpty()) return false;
        Item item = stack.getItem();
        return item == VERITY_INVENTORY_1 || item == VERITY_INVENTORY_2 || item == VERITY_INVENTORY_3;
    }

    public static void armHeldTalk(Player player, int ticks) {
        if (!player.level().isClientSide) {
            HELD_TALK_COOLDOWNS.put(player.getUUID(), Math.max(0, ticks));
        }
    }

    @Override
    public void onInitialize() {
        LOGGER.info("Initializing Verity Creepypasta Mod (Mojang Mappings)...");

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

        ServerTickEvents.END_SERVER_TICK.register(VerityMod::tickHeldVerityItems);

        // Spawn intro cardboard box when player joins
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayer player = handler.getPlayer();
            if (!player.getTags().contains("verity_intro_complete")) {
                INTRO_TIMERS.put(player.getUUID(), 40); // 2 seconds delay to allow world loading
            }
        });

        // Monitor player join delays and place cardboard box block in front of them
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (INTRO_TIMERS.isEmpty()) return;

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
                    Vec3 view = player.getViewVector(1.0F).normalize();
                    double tx = player.getX() + view.x * 5.0D;
                    double tz = player.getZ() + view.z * 5.0D;
                    double ty = player.getY();

                    BlockPos spawnPos = findGround(level, new BlockPos((int) tx, (int) ty, (int) tz));

                    // Place the box
                    level.setBlockAndUpdate(spawnPos, CARDBOARD_BOX.defaultBlockState());

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

    private static void tickHeldVerityItems(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            UUID uuid = player.getUUID();
            ItemStack heldStack = player.getMainHandItem();
            if (!isVerityInventoryItem(heldStack)) {
                HELD_TALK_COOLDOWNS.remove(uuid);
                continue;
            }

            int cooldown = HELD_TALK_COOLDOWNS.getOrDefault(uuid, 200);
            if (cooldown > 0) {
                HELD_TALK_COOLDOWNS.put(uuid, cooldown - 1);
                continue;
            }

            speakFromHeldItem(player, heldStack);
            HELD_TALK_COOLDOWNS.put(uuid, 600 + player.getRandom().nextInt(600));
        }
    }

    private static void speakFromHeldItem(ServerPlayer player, ItemStack stack) {
        boolean creepy = stack.getItem() == VERITY_INVENTORY_2 || stack.getItem() == VERITY_INVENTORY_3;
        String[] keys = creepy ? HELD_CREEPY_MESSAGE_KEYS : HELD_FRIENDLY_MESSAGE_KEYS;
        player.sendSystemMessage(Component.translatable(keys[player.getRandom().nextInt(keys.length)]));

        SoundEvent sound;
        if (creepy) {
            sound = switch (player.getRandom().nextInt(4)) {
                case 0 -> SOUND_YOU_ARE_MINE;
                case 1 -> SOUND_SOMETHING_HUNGRY;
                case 2 -> SOUND_ALWAYS_LOOKED_LIKE_THIS;
                default -> SOUND_SOMETHING_PASSED;
            };
        } else {
            sound = switch (player.getRandom().nextInt(3)) {
                case 0 -> SOUND_HELLO;
                case 1 -> SOUND_ASKME;
                default -> SOUND_WHOSTHERE;
            };
        }

        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                sound, creepy ? SoundSource.HOSTILE : SoundSource.NEUTRAL, 0.85F, 1.0F);
    }

    private static BlockPos findGround(ServerLevel level, BlockPos startPos) {
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos(startPos.getX(), startPos.getY(), startPos.getZ());
        for (int i = 0; i < 20; i++) {
            if (level.getBlockState(mutable).isAir() && !level.getBlockState(mutable.below()).getCollisionShape(level, mutable.below()).isEmpty()) {
                return mutable.immutable();
            }
            mutable.move(0, -1, 0);
        }
        mutable.set(startPos.getX(), startPos.getY(), startPos.getZ());
        for (int i = 0; i < 20; i++) {
            if (level.getBlockState(mutable).isAir() && !level.getBlockState(mutable.below()).getCollisionShape(level, mutable.below()).isEmpty()) {
                return mutable.immutable();
            }
            mutable.move(0, 1, 0);
        }
        return startPos;
    }
}
