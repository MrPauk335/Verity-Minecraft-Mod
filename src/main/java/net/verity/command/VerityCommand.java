package net.verity.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.verity.VerityMod;
import net.verity.config.VerityConfig;
import net.verity.ai.VerityLLMClient;
import net.verity.entity.VerityEntity;
import net.verity.entity.VerityEntity.VerityPhase;

import java.util.List;

/**
 * Команда /verity — управление Verity прямо из игры.
 * 
 * Использование:
 *   /verity                     — помощь
 *   /verity info                — статус мода
 *   /verity phase               — фаза ближайшего Verity
 *   /verity phase set <ф>       — установить фазу
 *   /verity toggle <опция>      — вкл/выкл опцию
 *   /verity face <номер>        — установить лицо
 *   /verity monster             — включить/выключить monster form
 *   /verity roof                — сорвать крышу над игроком
 *   /verity say <текст>         — заставить Verity сказать текст (через LLM)
 *   /verity teleport            — телепортировать Verity к игроку
 *   /verity music               — включить "My Gal"
 *   /verity lead <алмаз/дом/деревня> — вести игрока
 *   /verity stop                — остановить ведение
 *   /verity reset               — сбросить фазу в HELPER
 *   /verity clear               — удалить Verity из мира
 *   /verity spawn               — создать нового Verity
 *   /verity day <0-3>           — установить день отсчёта (COUNTDOWN)
 */
public class VerityCommand {

    private static final String[] OPTIONS = {
        "llm", "chat", "sounds", "monster", "teleport", "eat", "doors"
    };

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("verity")
                .then(Commands.literal("info")
                    .executes(ctx -> showInfo(ctx.getSource()))
                )
                .then(Commands.literal("phase")
                    .executes(ctx -> showPhase(ctx.getSource()))
                    .then(Commands.literal("set")
                        .then(Commands.argument("phase", StringArgumentType.word())
                            .suggests((ctx, builder) -> {
                                for (VerityPhase p : VerityPhase.values()) {
                                    builder.suggest(p.name().toLowerCase());
                                }
                                return builder.buildFuture();
                            })
                            .executes(ctx -> setPhase(ctx.getSource(), 
                                StringArgumentType.getString(ctx, "phase")))
                        )
                    )
                )
                .then(Commands.literal("toggle")
                    .then(Commands.argument("option", StringArgumentType.word())
                        .suggests((ctx, builder) -> {
                            for (String opt : OPTIONS) {
                                builder.suggest(opt + " — " + getOptionStatus(opt));
                            }
                            return builder.buildFuture();
                        })
                        .executes(ctx -> toggleOption(ctx.getSource(),
                            StringArgumentType.getString(ctx, "option")))
                    )
                )
                .then(Commands.literal("face")
                    .then(Commands.argument("index", StringArgumentType.word())
                        .suggests((ctx, builder) -> {
                            for (int i = 0; i <= 11; i++) {
                                builder.suggest(String.valueOf(i));
                            }
                            return builder.buildFuture();
                        })
                        .executes(ctx -> setFace(ctx.getSource(),
                            StringArgumentType.getString(ctx, "index")))
                    )
                )
                .then(Commands.literal("monster")
                    .executes(ctx -> toggleMonster(ctx.getSource()))
                )
                .then(Commands.literal("roof")
                    .executes(ctx -> tearRoof(ctx.getSource()))
                )
                .then(Commands.literal("say")
                    .then(Commands.argument("text", StringArgumentType.greedyString())
                        .executes(ctx -> sayText(ctx.getSource(),
                            StringArgumentType.getString(ctx, "text")))
                    )
                )
                .then(Commands.literal("teleport")
                    .executes(ctx -> teleportToPlayer(ctx.getSource()))
                )
                .then(Commands.literal("music")
                    .executes(ctx -> playMusic(ctx.getSource()))
                )
                .then(Commands.literal("lead")
                    .then(Commands.argument("target", StringArgumentType.word())
                        .suggests((ctx, builder) -> {
                            builder.suggest("алмаз");
                            builder.suggest("дом");
                            builder.suggest("деревня");
                            builder.suggest("золото");
                            builder.suggest("железо");
                            return builder.buildFuture();
                        })
                        .executes(ctx -> leadPlayer(ctx.getSource(),
                            StringArgumentType.getString(ctx, "target")))
                    )
                )
                .then(Commands.literal("stop")
                    .executes(ctx -> stopLeading(ctx.getSource()))
                )
                .then(Commands.literal("reset")
                    .executes(ctx -> resetPhase(ctx.getSource()))
                )
                .then(Commands.literal("clear")
                    .executes(ctx -> clearVerity(ctx.getSource()))
                )
                .then(Commands.literal("spawn")
                    .executes(ctx -> spawnVerity(ctx.getSource()))
                )
                .then(Commands.literal("day")
                    .then(Commands.argument("day", StringArgumentType.word())
                        .suggests((ctx, builder) -> {
                            builder.suggest("0");
                            builder.suggest("1");
                            builder.suggest("2");
                            builder.suggest("3");
                            return builder.buildFuture();
                        })
                        .executes(ctx -> setDay(ctx.getSource(),
                            StringArgumentType.getString(ctx, "day")))
                    )
                )
                .executes(ctx -> showHelp(ctx.getSource()))
        );
    }

    // === INFO ===
    private static int showInfo(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal("§6=== Verity Info ==="), false);
        source.sendSuccess(() -> Component.literal("§7LLM: " + bool(VerityConfig.llmEnabled()) + 
            " §7| Chat: " + bool(VerityConfig.chatEnabled())), false);
        source.sendSuccess(() -> Component.literal("§7Models: §f" + String.join(", ", VerityConfig.openRouterModels())), false);
        source.sendSuccess(() -> Component.literal("§7Sound: " + bool(VerityConfig.soundsEnabled()) + 
            " §7| Vol: §f" + VerityConfig.soundVolume()), false);
        source.sendSuccess(() -> Component.literal("§7Monster: " + bool(VerityConfig.monsterFormEnabled()) + 
            " §7| Teleport: " + bool(VerityConfig.teleportEnabled())), false);
        source.sendSuccess(() -> Component.literal("§7Eat villagers: " + bool(VerityConfig.villagerEatingEnabled()) + 
            " §7| Doors: " + bool(VerityConfig.doorTelekinesisEnabled())), false);
        source.sendSuccess(() -> Component.literal("§7Для настроек: §e/verity toggle <опция>"), false);
        return 1;
    }

    // === PHASE ===
    private static int showPhase(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("§cТолько для игроков!"));
            return 0;
        }
        VerityEntity nearest = findNearest(player);
        if (nearest == null) {
            source.sendSuccess(() -> Component.literal("§eРядом нет Verity"), false);
            return 0;
        }

        VerityPhase p = nearest.getVerityPhase();
        String pn = switch (p) {
            case DORMANT -> "§7 DORMANT";
            case HELPER -> "§a HELPER";
            case OMNISCIENT -> "§e OMNISCIENT";
            case COUNTDOWN -> "§6 COUNTDOWN";
            case MONSTER -> "§4 MONSTER";
            case POSSESSIVE -> "§d POSSESSIVE";
            case HUNTER -> "§c HUNTER";
        };
        source.sendSuccess(() -> Component.literal("§6[Verity] §fФаза: " + pn), false);
        source.sendSuccess(() -> Component.literal("§7Monster: " + (nearest.isMonsterForm() ? "§aДа" : "§7Нет") + 
            " §7| Face: §f" + faceName(nearest.getFaceIndex())), false);
        return 1;
    }

    private static int setPhase(CommandSourceStack source, String phaseName) {
        if (!(source.getEntity() instanceof ServerPlayer player)) return 0;
        VerityEntity nearest = findNearest(player);
        if (nearest == null) {
            source.sendFailure(Component.literal("§cРядом нет Verity!"));
            return 0;
        }
        try {
            VerityPhase phase = VerityPhase.valueOf(phaseName.toUpperCase());
            nearest.setVerityPhase(phase);
            source.sendSuccess(() -> Component.literal("§a Verity -> " + phase.name()), true);
        } catch (Exception e) {
            source.sendFailure(Component.literal("§cФазы: HELPER, OMNISCIENT, COUNTDOWN, MONSTER, POSSESSIVE, HUNTER"));
        }
        return 1;
    }

    // === TOGGLE ===
    private static int toggleOption(CommandSourceStack source, String option) {
        String opt = option.toLowerCase();
        boolean current;
        String configKey;

        switch (opt) {
            case "llm" -> { current = VerityConfig.llmEnabled(); configKey = "llm_enabled"; }
            case "chat" -> { current = VerityConfig.chatEnabled(); configKey = "chat_enabled"; }
            case "sounds" -> { current = VerityConfig.soundsEnabled(); configKey = "sounds_enabled"; }
            case "monster" -> { current = VerityConfig.monsterFormEnabled(); configKey = "monster_form_enabled"; }
            case "teleport" -> { current = VerityConfig.teleportEnabled(); configKey = "teleport_enabled"; }
            case "eat" -> { current = VerityConfig.villagerEatingEnabled(); configKey = "villager_eating_enabled"; }
            case "doors" -> { current = VerityConfig.doorTelekinesisEnabled(); configKey = "door_telekinesis_enabled"; }
            default -> {
                source.sendFailure(Component.literal("§cНеизвестная опция! Доступны: " + String.join(", ", OPTIONS)));
                return 0;
            }
        }

        boolean newVal = !current;
        VerityConfig.setBool(configKey, newVal);
        VerityLLMClient.reloadFromConfig();

        if (newVal) {
            source.sendSuccess(() -> Component.literal("§a " + option + " ON"), true);
        } else {
            source.sendSuccess(() -> Component.literal("§c " + option + " OFF"), true);
        }
        return 1;
    }

    // === FACE ===
    private static int setFace(CommandSourceStack source, String indexStr) {
        if (!(source.getEntity() instanceof ServerPlayer player)) return 0;
        VerityEntity nearest = findNearest(player);
        if (nearest == null) {
            source.sendFailure(Component.literal("§cРядом нет Verity!"));
            return 0;
        }
        try {
            int face = Integer.parseInt(indexStr);
            if (face < 0 || face > 11) {
                source.sendFailure(Component.literal("§cЛицо от 0 до 11"));
                return 0;
            }
            nearest.setFaceIndex(face);
            source.sendSuccess(() -> Component.literal("§a Лицо: " + faceName(face)), true);
        } catch (NumberFormatException e) {
            source.sendFailure(Component.literal("§cУкажи номер от 0 до 11"));
        }
        return 1;
    }

    // === MONSTER TOGGLE ===
    private static int toggleMonster(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer player)) return 0;
        VerityEntity nearest = findNearest(player);
        if (nearest == null) {
            source.sendFailure(Component.literal("§cРядом нет Verity!"));
            return 0;
        }
        boolean newVal = !nearest.isMonsterForm();
        nearest.setMonsterForm(newVal);
        if (newVal) {
            nearest.setFaceIndex(VerityEntity.FACE_CREEPY_SMILE);
            source.sendSuccess(() -> Component.literal("§4 MONSTER FORM: ON"), true);
        } else {
            nearest.setFaceIndex(VerityEntity.FACE_SMILE);
            source.sendSuccess(() -> Component.literal("§a MONSTER FORM: OFF"), true);
        }
        return 1;
    }

    // === TEAR ROOF ===
    private static int tearRoof(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer player)) return 0;
        VerityEntity nearest = findNearest(player);
        if (nearest == null) {
            source.sendFailure(Component.literal("§cРядом нет Verity!"));
            return 0;
        }
        nearest.tearOffRoofPublic(player);
        source.sendSuccess(() -> Component.literal("§4 Крыша сорвана!"), true);
        return 1;
    }

    // === SAY TEXT ===
    private static int sayText(CommandSourceStack source, String text) {
        if (!(source.getEntity() instanceof ServerPlayer player)) return 0;
        VerityEntity nearest = findNearest(player);
        if (nearest == null) {
            source.sendFailure(Component.literal("§cРядом нет Verity!"));
            return 0;
        }
        VerityPhase phase = nearest.getVerityPhase();
        String color = switch (phase) {
            case MONSTER, HUNTER -> "§4<Verity>§r ";
            case COUNTDOWN -> "§c<Verity\u2122>§r ";
            default -> "§e<Verity\u2122>§r ";
        };
        player.sendSystemMessage(Component.literal(color + text));
        nearest.setTalkAnimTick(40);
        // TTS
        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player,
                new net.verity.net.TTSPayload(color + text));
        source.sendSuccess(() -> Component.literal("§a Verity сказал: " + text), true);
        return 1;
    }

    // === TELEPORT TO PLAYER ===
    private static int teleportToPlayer(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer player)) return 0;
        VerityEntity nearest = findNearest(player);
        if (nearest == null) {
            source.sendFailure(Component.literal("§cРядом нет Verity!"));
            return 0;
        }
        nearest.teleportTo(player.getX(), player.getY(), player.getZ());
        source.sendSuccess(() -> Component.literal("§a Verity телепортирован к тебе"), true);
        return 1;
    }

    // === PLAY MUSIC ===
    private static int playMusic(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer player)) return 0;
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                VerityMod.SOUND_MYGAL_NORMAL, net.minecraft.sounds.SoundSource.RECORDS, 1.2F, 1.0F);
        source.sendSuccess(() -> Component.literal("§e My Gal играет..."), true);
        return 1;
    }

    // === LEAD PLAYER ===
    private static int leadPlayer(CommandSourceStack source, String target) {
        if (!(source.getEntity() instanceof ServerPlayer player)) return 0;
        VerityEntity nearest = findNearest(player);
        if (nearest == null) {
            source.sendFailure(Component.literal("§cРядом нет Verity!"));
            return 0;
        }
        // Используем dialogue controller для поиска цели
        nearest.getDialogueController().onPlayerMessage(player.getName().getString(),
                "пошли за мной к " + target, 
                player.clientInformation().language().startsWith("ru") ? "ru" : "en");
        source.sendSuccess(() -> Component.literal("§a Verity ведёт к: " + target), true);
        return 1;
    }

    // === STOP LEADING ===
    private static int stopLeading(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer player)) return 0;
        VerityEntity nearest = findNearest(player);
        if (nearest == null) {
            source.sendFailure(Component.literal("§cРядом нет Verity!"));
            return 0;
        }
        nearest.setLeading(false);
        nearest.setLeadTarget(null);
        source.sendSuccess(() -> Component.literal("§a Verity остановлен"), true);
        return 1;
    }

    // === RESET PHASE ===
    private static int resetPhase(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer player)) return 0;
        VerityEntity nearest = findNearest(player);
        if (nearest == null) {
            source.sendFailure(Component.literal("§cРядом нет Verity!"));
            return 0;
        }
        nearest.setVerityPhase(VerityPhase.HELPER);
        nearest.setMonsterForm(false);
        nearest.setLeading(false);
        nearest.setLeadTarget(null);
        nearest.setFaceIndex(VerityEntity.FACE_SMILE);
        source.sendSuccess(() -> Component.literal("§a Verity сброшен в HELPER"), true);
        return 1;
    }

    // === CLEAR VERITY ===
    private static int clearVerity(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer player)) return 0;
        var entities = player.level().getEntitiesOfClass(
            VerityEntity.class,
            player.getBoundingBox().inflate(128.0D),
            e -> e.isAlive()
        );
        int count = 0;
        for (VerityEntity e : entities) {
            e.discard();
            count++;
        }
        net.verity.entity.VerityEntity.ACTIVE_VERITIES.clear();
        final int c = count;
        source.sendSuccess(() -> Component.literal("§c Удалено Verity: " + c), true);
        return 1;
    }

    // === SPAWN VERITY ===
    private static int spawnVerity(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer player)) return 0;
        // Проверяем что нет другого Verity
        if (!net.verity.entity.VerityEntity.ACTIVE_VERITIES.isEmpty()) {
            source.sendFailure(Component.literal("§cVerity уже существует! Используй /verity clear сначала"));
            return 0;
        }
        VerityEntity entity = new VerityEntity(VerityMod.VERITY_ENTITY, player.level());
        entity.setPos(player.getX() + 2, player.getY(), player.getZ());
        entity.setVerityPhase(VerityPhase.HELPER);
        entity.setFaceIndex(VerityEntity.FACE_SMILE);
        player.level().addFreshEntity(entity);
        source.sendSuccess(() -> Component.literal("§a Verity создан!"), true);
        return 1;
    }

    // === SET DAY ===
    private static int setDay(CommandSourceStack source, String dayStr) {
        if (!(source.getEntity() instanceof ServerPlayer player)) return 0;
        VerityEntity nearest = findNearest(player);
        if (nearest == null) {
            source.sendFailure(Component.literal("§cРядом нет Verity!"));
            return 0;
        }
        try {
            int day = Integer.parseInt(dayStr);
            if (day < 0 || day > 3) {
                source.sendFailure(Component.literal("§cДень от 0 до 3"));
                return 0;
            }
            nearest.setDayCounterPublic(day);
            if (nearest.getVerityPhase() == VerityPhase.COUNTDOWN) {
                nearest.setFaceIndex(nearest.getDefaultFaceForPhase(VerityPhase.COUNTDOWN));
            }
            source.sendSuccess(() -> Component.literal("§a День отсчёта: " + day), true);
        } catch (NumberFormatException e) {
            source.sendFailure(Component.literal("§cУкажи число 0-3"));
        }
        return 1;
    }

    // === HELP ===
    private static int showHelp(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal("§6=== Verity Commands ==="), false);
        source.sendSuccess(() -> Component.literal("§e/verity §7— помощь"), false);
        source.sendSuccess(() -> Component.literal("§e/verity info §7— статус"), false);
        source.sendSuccess(() -> Component.literal("§e/verity phase §7— фаза Verity"), false);
        source.sendSuccess(() -> Component.literal("§e/verity phase set <ф> §7— сменить фазу"), false);
        source.sendSuccess(() -> Component.literal("§e/verity toggle <opt> §7— вкл/выкл"), false);
        source.sendSuccess(() -> Component.literal("§e/verity face <0-11> §7— лицо"), false);
        source.sendSuccess(() -> Component.literal("§e/verity monster §7— monster form вкл/выкл"), false);
        source.sendSuccess(() -> Component.literal("§e/verity roof §7— сорвать крышу"), false);
        source.sendSuccess(() -> Component.literal("§e/verity say <текст> §7— заставить сказать"), false);
        source.sendSuccess(() -> Component.literal("§e/verity teleport §7— к игроку"), false);
        source.sendSuccess(() -> Component.literal("§e/verity music §7— My Gal"), false);
        source.sendSuccess(() -> Component.literal("§e/verity lead <цель> §7— вести игрока"), false);
        source.sendSuccess(() -> Component.literal("§e/verity stop §7— остановить ведение"), false);
        source.sendSuccess(() -> Component.literal("§e/verity reset §7— сброс в HELPER"), false);
        source.sendSuccess(() -> Component.literal("§e/verity clear §7— удалить Verity"), false);
        source.sendSuccess(() -> Component.literal("§e/verity spawn §7— создать Verity"), false);
        source.sendSuccess(() -> Component.literal("§e/verity day <0-3> §7— день отсчёта"), false);
        source.sendSuccess(() -> Component.literal("§7Опции toggle: llm, chat, sounds, monster, teleport, eat, doors"), false);
        return 1;
    }

    // === UTILS ===
    private static VerityEntity findNearest(ServerPlayer player) {
        var entities = player.level().getEntitiesOfClass(
            VerityEntity.class,
            player.getBoundingBox().inflate(128.0D),
            e -> e.isAlive()
        );
        if (entities.isEmpty()) return null;
        VerityEntity nearest = null;
        double minDist = Double.MAX_VALUE;
        for (VerityEntity e : entities) {
            double d = e.distanceToSqr(player);
            if (d < minDist) { minDist = d; nearest = e; }
        }
        return nearest;
    }

    private static String bool(boolean v) { return v ? "§aON" : "§cOFF"; }
    private static String getOptionStatus(String opt) {
        return switch (opt) {
            case "llm" -> bool(VerityConfig.llmEnabled());
            case "chat" -> bool(VerityConfig.chatEnabled());
            case "sounds" -> bool(VerityConfig.soundsEnabled());
            case "monster" -> bool(VerityConfig.monsterFormEnabled());
            case "teleport" -> bool(VerityConfig.teleportEnabled());
            case "eat" -> bool(VerityConfig.villagerEatingEnabled());
            case "doors" -> bool(VerityConfig.doorTelekinesisEnabled());
            default -> "§7?";
        };
    }

    private static String faceName(int i) {
        return switch (i) {
            case 0 -> "Smile"; case 1 -> "Speak"; case 2 -> "Hurt";
            case 3 -> "AbnormalShut"; case 4 -> "AbnormalOpen"; case 5 -> "Bored";
            case 6 -> "Day2Shut"; case 7 -> "Day2Open"; case 8 -> "CreepySmile";
            case 9 -> "Serious1"; case 10 -> "Serious2"; case 11 -> "Serious3";
            default -> "?";
        };
    }
}
