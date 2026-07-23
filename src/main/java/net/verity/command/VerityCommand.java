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
                                builder.suggest(opt + " \u2014 " + getOptionStatus(opt));
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
                            builder.suggest("\u0430\u043B\u043C\u0430\u0437");
                            builder.suggest("\u0434\u043E\u043C");
                            builder.suggest("\u0434\u0435\u0440\u0435\u0432\u043D\u044F");
                            builder.suggest("\u0437\u043E\u043B\u043E\u0442\u043E");
                            builder.suggest("\u0436\u0435\u043B\u0435\u0437\u043E");
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
        source.sendSuccess(() -> Component.literal("\u00A76=== Verity Info ==="), false);
        source.sendSuccess(() -> Component.literal("\u00A77LLM: " + bool(VerityConfig.llmEnabled()) + 
            " \u00A77| Chat: " + bool(VerityConfig.chatEnabled())), false);
        source.sendSuccess(() -> Component.literal("\u00A77Models: \u00A7f" + String.join(", ", VerityConfig.openRouterModels())), false);
        source.sendSuccess(() -> Component.literal("\u00A77Sound: " + bool(VerityConfig.soundsEnabled()) + 
            " \u00A77| Vol: \u00A7f" + VerityConfig.soundVolume()), false);
        source.sendSuccess(() -> Component.literal("\u00A77Monster: " + bool(VerityConfig.monsterFormEnabled()) + 
            " \u00A77| Teleport: " + bool(VerityConfig.teleportEnabled())), false);
        source.sendSuccess(() -> Component.literal("\u00A77Eat villagers: " + bool(VerityConfig.villagerEatingEnabled()) + 
            " \u00A77| Doors: " + bool(VerityConfig.doorTelekinesisEnabled())), false);
        source.sendSuccess(() -> Component.literal("\u00A77\u0414\u043B\u044F \u043D\u0430\u0441\u0442\u0440\u043E\u0435\u043A: \u00A7e/verity toggle <\u043E\u043F\u0446\u0438\u044F>"), false);
        return 1;
    }

    // === PHASE ===
    private static int showPhase(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("\u00A7c\u0422\u043E\u043B\u044C\u043A\u043E \u0434\u043B\u044F \u0438\u0433\u0440\u043E\u043A\u043E\u0432!"));
            return 0;
        }
        VerityEntity nearest = findNearest(player);
        if (nearest == null) {
            source.sendSuccess(() -> Component.literal("\u00A7e\u0420\u044F\u0434\u043E\u043C \u043D\u0435\u0442 Verity"), false);
            return 0;
        }

        VerityPhase p = nearest.getVerityPhase();
        String pn = switch (p) {
            case DORMANT -> "\u00A77 DORMANT";
            case HELPER -> "\u00A7a HELPER";
            case OMNISCIENT -> "\u00A7e OMNISCIENT";
            case COUNTDOWN -> "\u00A76 COUNTDOWN";
            case MONSTER -> "\u00A74 MONSTER";
            case POSSESSIVE -> "\u00A7d POSSESSIVE";
            case HUNTER -> "\u00A7c HUNTER";
            case FINAL -> "\u00A74\u00A7l FINAL";
        };
        source.sendSuccess(() -> Component.literal("\u00A76[Verity] \u00A7f\u0424\u0430\u0437\u0430: " + pn), false);
        source.sendSuccess(() -> Component.literal("\u00A77Monster: " + (nearest.isMonsterForm() ? "\u00A7a\u0414\u0430" : "\u00A77\u041D\u0435\u0442") + 
            " \u00A77| Face: \u00A7f" + faceName(nearest.getFaceIndex())), false);
        return 1;
    }

    private static int setPhase(CommandSourceStack source, String phaseName) {
        if (!(source.getEntity() instanceof ServerPlayer player)) return 0;
        VerityEntity nearest = findNearest(player);
        if (nearest == null) {
            source.sendFailure(Component.literal("\u00A7c\u0420\u044F\u0434\u043E\u043C \u043D\u0435\u0442 Verity!"));
            return 0;
        }
        try {
            VerityPhase phase = VerityPhase.valueOf(phaseName.toUpperCase());
            nearest.setVerityPhase(phase);
            source.sendSuccess(() -> Component.literal("\u00A7a Verity -> " + phase.name()), true);
        } catch (Exception e) {
            source.sendFailure(Component.literal("\u00A7c\u0424\u0430\u0437\u044B: HELPER, OMNISCIENT, COUNTDOWN, MONSTER, POSSESSIVE, HUNTER"));
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
                source.sendFailure(Component.literal("\u00A7c\u041D\u0435\u0438\u0437\u0432\u0435\u0441\u0442\u043D\u0430\u044F \u043E\u043F\u0446\u0438\u044F! \u0414\u043E\u0441\u0442\u0443\u043F\u043D\u044B: " + String.join(", ", OPTIONS)));
                return 0;
            }
        }

        boolean newVal = !current;
        VerityConfig.setBool(configKey, newVal);
        VerityLLMClient.reloadFromConfig();

        if (newVal) {
            source.sendSuccess(() -> Component.literal("\u00A7a " + option + " ON"), true);
        } else {
            source.sendSuccess(() -> Component.literal("\u00A7c " + option + " OFF"), true);
        }
        return 1;
    }

    // === FACE ===
    private static int setFace(CommandSourceStack source, String indexStr) {
        if (!(source.getEntity() instanceof ServerPlayer player)) return 0;
        VerityEntity nearest = findNearest(player);
        if (nearest == null) {
            source.sendFailure(Component.literal("\u00A7c\u0420\u044F\u0434\u043E\u043C \u043D\u0435\u0442 Verity!"));
            return 0;
        }
        try {
            int face = Integer.parseInt(indexStr);
            if (face < 0 || face > 11) {
                source.sendFailure(Component.literal("\u00A7c\u041B\u0438\u0446\u043E \u043E\u0442 0 \u0434\u043E 11"));
                return 0;
            }
            nearest.setFaceIndex(face);
            source.sendSuccess(() -> Component.literal("\u00A7a \u041B\u0438\u0446\u043E: " + faceName(face)), true);
        } catch (NumberFormatException e) {
            source.sendFailure(Component.literal("\u00A7c\u0423\u043A\u0430\u0436\u0438 \u043D\u043E\u043C\u0435\u0440 \u043E\u0442 0 \u0434\u043E 11"));
        }
        return 1;
    }

    // === MONSTER TOGGLE ===
    private static int toggleMonster(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer player)) return 0;
        VerityEntity nearest = findNearest(player);
        if (nearest == null) {
            source.sendFailure(Component.literal("\u00A7c\u0420\u044F\u0434\u043E\u043C \u043D\u0435\u0442 Verity!"));
            return 0;
        }
        boolean newVal = !nearest.isMonsterForm();
        nearest.setMonsterForm(newVal);
        if (newVal) {
            nearest.setFaceIndex(VerityEntity.FACE_CREEPY_SMILE);
            source.sendSuccess(() -> Component.literal("\u00A74 MONSTER FORM: ON"), true);
        } else {
            nearest.setFaceIndex(VerityEntity.FACE_SMILE);
            source.sendSuccess(() -> Component.literal("\u00A7a MONSTER FORM: OFF"), true);
        }
        return 1;
    }

    // === TEAR ROOF ===
    private static int tearRoof(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer player)) return 0;
        VerityEntity nearest = findNearest(player);
        if (nearest == null) {
            source.sendFailure(Component.literal("\u00A7c\u0420\u044F\u0434\u043E\u043C \u043D\u0435\u0442 Verity!"));
            return 0;
        }
        nearest.tearOffRoofPublic(player);
        source.sendSuccess(() -> Component.literal("\u00A74 \u041A\u0440\u044B\u0448\u0430 \u0441\u043E\u0440\u0432\u0430\u043D\u0430!"), true);
        return 1;
    }

    // === SAY TEXT ===
    private static int sayText(CommandSourceStack source, String text) {
        if (!(source.getEntity() instanceof ServerPlayer player)) return 0;
        VerityEntity nearest = findNearest(player);
        if (nearest == null) {
            source.sendFailure(Component.literal("\u00A7c\u0420\u044F\u0434\u043E\u043C \u043D\u0435\u0442 Verity!"));
            return 0;
        }
        VerityPhase phase = nearest.getVerityPhase();
        String color = switch (phase) {
            case MONSTER, HUNTER -> "\u00A74<Verity>\u00A7r ";
            case COUNTDOWN -> "\u00A7c<Verity\u2122>\u00A7r ";
            default -> "\u00A7e<Verity\u2122>\u00A7r ";
        };
        player.sendSystemMessage(Component.literal(color + text));
        nearest.setTalkAnimTick(40);
        // TTS
        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player,
                new net.verity.net.TTSPayload(color + text));
        source.sendSuccess(() -> Component.literal("\u00A7a Verity \u0441\u043A\u0430\u0437\u0430\u043B: " + text), true);
        return 1;
    }

    // === TELEPORT TO PLAYER ===
    private static int teleportToPlayer(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer player)) return 0;
        VerityEntity nearest = findNearest(player);
        if (nearest == null) {
            source.sendFailure(Component.literal("\u00A7c\u0420\u044F\u0434\u043E\u043C \u043D\u0435\u0442 Verity!"));
            return 0;
        }
        nearest.teleportTo(player.getX(), player.getY(), player.getZ());
        source.sendSuccess(() -> Component.literal("\u00A7a Verity \u0442\u0435\u043B\u0435\u043F\u043E\u0440\u0442\u0438\u0440\u043E\u0432\u0430\u043D \u043A \u0442\u0435\u0431\u0435"), true);
        return 1;
    }

    // === PLAY MUSIC ===
    private static int playMusic(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer player)) return 0;
        VerityMod.playMusic(player, "mygal_normal", 1.2F, 1.0F);
        source.sendSuccess(() -> Component.literal("\u00A7e My Gal \u0438\u0433\u0440\u0430\u0435\u0442..."), true);
        return 1;
    }

    // === LEAD PLAYER ===
    private static int leadPlayer(CommandSourceStack source, String target) {
        if (!(source.getEntity() instanceof ServerPlayer player)) return 0;
        VerityEntity nearest = findNearest(player);
        if (nearest == null) {
            source.sendFailure(Component.literal("\u00A7c\u0420\u044F\u0434\u043E\u043C \u043D\u0435\u0442 Verity!"));
            return 0;
        }
        // Используем dialogue controller для поиска цели
        nearest.getDialogueController().onPlayerMessage(player.getName().getString(),
                "\u043F\u043E\u0448\u043B\u0438 \u0437\u0430 \u043C\u043D\u043E\u0439 \u043A " + target, 
                player.clientInformation().language().startsWith("ru") ? "ru" : "en");
        source.sendSuccess(() -> Component.literal("\u00A7a Verity \u0432\u0435\u0434\u0451\u0442 \u043A: " + target), true);
        return 1;
    }

    // === STOP LEADING ===
    private static int stopLeading(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer player)) return 0;
        VerityEntity nearest = findNearest(player);
        if (nearest == null) {
            source.sendFailure(Component.literal("\u00A7c\u0420\u044F\u0434\u043E\u043C \u043D\u0435\u0442 Verity!"));
            return 0;
        }
        nearest.setLeading(false);
        nearest.setLeadTarget(null);
        source.sendSuccess(() -> Component.literal("\u00A7a Verity \u043E\u0441\u0442\u0430\u043D\u043E\u0432\u043B\u0435\u043D"), true);
        return 1;
    }

    // === RESET PHASE ===
    private static int resetPhase(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer player)) return 0;
        VerityEntity nearest = findNearest(player);
        if (nearest == null) {
            source.sendFailure(Component.literal("\u00A7c\u0420\u044F\u0434\u043E\u043C \u043D\u0435\u0442 Verity!"));
            return 0;
        }
        nearest.setVerityPhase(VerityPhase.HELPER);
        nearest.setMonsterForm(false);
        nearest.setLeading(false);
        nearest.setLeadTarget(null);
        nearest.setFaceIndex(VerityEntity.FACE_SMILE);
        source.sendSuccess(() -> Component.literal("\u00A7a Verity \u0441\u0431\u0440\u043E\u0448\u0435\u043D \u0432 HELPER"), true);
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
        source.sendSuccess(() -> Component.literal("\u00A7c \u0423\u0434\u0430\u043B\u0435\u043D\u043E Verity: " + c), true);
        return 1;
    }

    // === SPAWN VERITY ===
    private static int spawnVerity(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer player)) return 0;
        // Проверяем что нет другого Verity
        if (!net.verity.entity.VerityEntity.ACTIVE_VERITIES.isEmpty()) {
            source.sendFailure(Component.literal("\u00A7cVerity \u0443\u0436\u0435 \u0441\u0443\u0449\u0435\u0441\u0442\u0432\u0443\u0435\u0442! \u0418\u0441\u043F\u043E\u043B\u044C\u0437\u0443\u0439 /verity clear \u0441\u043D\u0430\u0447\u0430\u043B\u0430"));
            return 0;
        }
        VerityEntity entity = new VerityEntity(VerityMod.VERITY_ENTITY, player.level());
        entity.setPos(player.getX() + 2, player.getY(), player.getZ());
        entity.setVerityPhase(VerityPhase.HELPER);
        entity.setFaceIndex(VerityEntity.FACE_SMILE);
        player.level().addFreshEntity(entity);
        source.sendSuccess(() -> Component.literal("\u00A7a Verity \u0441\u043E\u0437\u0434\u0430\u043D!"), true);
        return 1;
    }

    // === SET DAY ===
    private static int setDay(CommandSourceStack source, String dayStr) {
        if (!(source.getEntity() instanceof ServerPlayer player)) return 0;
        VerityEntity nearest = findNearest(player);
        if (nearest == null) {
            source.sendFailure(Component.literal("\u00A7c\u0420\u044F\u0434\u043E\u043C \u043D\u0435\u0442 Verity!"));
            return 0;
        }
        try {
            int day = Integer.parseInt(dayStr);
            if (day < 0 || day > 3) {
                source.sendFailure(Component.literal("\u00A7c\u0414\u0435\u043D\u044C \u043E\u0442 0 \u0434\u043E 3"));
                return 0;
            }
            nearest.setDayCounterPublic(day);
            if (nearest.getVerityPhase() == VerityPhase.COUNTDOWN) {
                nearest.setFaceIndex(nearest.getDefaultFaceForPhase(VerityPhase.COUNTDOWN));
            }
            source.sendSuccess(() -> Component.literal("\u00A7a \u0414\u0435\u043D\u044C \u043E\u0442\u0441\u0447\u0451\u0442\u0430: " + day), true);
        } catch (NumberFormatException e) {
            source.sendFailure(Component.literal("\u00A7c\u0423\u043A\u0430\u0436\u0438 \u0447\u0438\u0441\u043B\u043E 0-3"));
        }
        return 1;
    }

    // === HELP ===
    private static int showHelp(CommandSourceStack source) {
        boolean ru = source.getEntity() instanceof ServerPlayer sp
                && sp.clientInformation().language() != null
                && sp.clientInformation().language().toLowerCase().startsWith("ru");

        if (ru) {
            showHelpRu(source);
        } else {
            showHelpEn(source);
        }
        return 1;
    }

    private static void showHelpRu(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal("\u00A76\u00A7l=== Verity\u2122 ==="), false);
        source.sendSuccess(() -> Component.literal(
                "\u00A77Verity \u2014 \u0436\u0451\u043B\u0442\u044B\u0439 \u0448\u0430\u0440. \u041B\u0438\u0447\u043D\u044B\u0439 \u043F\u043E\u043C\u043E\u0449\u043D\u0438\u043A-\u0434\u0440\u0443\u0433. \u0417\u043D\u0430\u0435\u0442 \u0432\u0441\u0451."), false);
        source.sendSuccess(() -> Component.literal(
                "\u00A77\u041F\u043E\u0441\u0442\u0435\u043F\u0435\u043D\u043D\u043E \u043F\u0440\u0435\u0432\u0440\u0430\u0449\u0430\u0435\u0442\u0441\u044F \u0432 \u043C\u043E\u043D\u0441\u0442\u0440\u0430. \u041D\u0435 \u043B\u0436\u0451\u0442. \u041D\u0435 \u0443\u0431\u0438\u0432\u0430\u0435\u0442."), false);
        source.sendSuccess(() -> Component.literal(""), false);

        source.sendSuccess(() -> Component.literal("\u00A76\u00A7l\u041A\u043E\u043C\u0430\u043D\u0434\u044B:"), false);
        source.sendSuccess(() -> Component.literal("\u00A7e/verity info \u00A77\u2014 \u0441\u0442\u0430\u0442\u0443\u0441 \u0438 \u043D\u0430\u0441\u0442\u0440\u043E\u0439\u043A\u0438"), false);
        source.sendSuccess(() -> Component.literal("\u00A7e/verity phase \u00A77\u2014 \u0442\u0435\u043A\u0443\u0449\u0430\u044F \u0444\u0430\u0437\u0430"), false);
        source.sendSuccess(() -> Component.literal("\u00A7e/verity phase set <\u0444\u0430\u0437\u0430> \u00A77\u2014 HELPER/OMNISCIENT/COUNTDOWN/MONSTER/POSSESSIVE/HUNTER"), false);
        source.sendSuccess(() -> Component.literal("\u00A7e/verity toggle <opt> \u00A77\u2014 \u0432\u043A\u043B/\u0432\u044B\u043A\u043B \u043E\u043F\u0446\u0438\u0438"), false);
        source.sendSuccess(() -> Component.literal("\u00A7e/verity face <0-11> \u00A77\u2014 \u0441\u043C\u0435\u043D\u0430 \u043B\u0438\u0446\u0430"), false);
        source.sendSuccess(() -> Component.literal("\u00A7e/verity monster \u00A77\u2014 Monster Form"), false);
        source.sendSuccess(() -> Component.literal("\u00A7e/verity roof \u00A77\u2014 \u0441\u043E\u0440\u0432\u0430\u0442\u044C \u043A\u0440\u044B\u0448\u0443"), false);
        source.sendSuccess(() -> Component.literal("\u00A7e/verity say <\u0442\u0435\u043A\u0441\u0442> \u00A77\u2014 \u0437\u0430\u0441\u0442\u0430\u0432\u0438\u0442\u044C \u0441\u043A\u0430\u0437\u0430\u0442\u044C"), false);
        source.sendSuccess(() -> Component.literal("\u00A7e/verity teleport \u00A77\u2014 \u0442\u0435\u043B\u0435\u043F\u043E\u0440\u0442 \u043A \u0438\u0433\u0440\u043E\u043A\u0443"), false);
        source.sendSuccess(() -> Component.literal("\u00A7e/verity music \u00A77\u2014 \u0432\u043A\u043B\u044E\u0447\u0438\u0442\u044C My Gal"), false);
        source.sendSuccess(() -> Component.literal("\u00A7e/verity lead <\u0446\u0435\u043B\u044C> \u00A77\u2014 \u0432\u0435\u0441\u0442\u0438 \u0438\u0433\u0440\u043E\u043A\u0430 (\u0430\u043B\u043C\u0430\u0437\u044B/\u0434\u043E\u043C/\u0434\u0435\u0440\u0435\u0432\u043D\u044F)"), false);
        source.sendSuccess(() -> Component.literal("\u00A7e/verity stop \u00A77\u2014 \u043E\u0441\u0442\u0430\u043D\u043E\u0432\u0438\u0442\u044C \u0432\u0435\u0434\u0435\u043D\u0438\u0435"), false);
        source.sendSuccess(() -> Component.literal("\u00A7e/verity reset \u00A77\u2014 \u0441\u0431\u0440\u043E\u0441 \u0432 HELPER"), false);
        source.sendSuccess(() -> Component.literal("\u00A7e/verity clear \u00A77\u2014 \u0443\u0434\u0430\u043B\u0438\u0442\u044C Verity"), false);
        source.sendSuccess(() -> Component.literal("\u00A7e/verity spawn \u00A77\u2014 \u0441\u043E\u0437\u0434\u0430\u0442\u044C Verity"), false);
        source.sendSuccess(() -> Component.literal("\u00A7e/verity day <0-3> \u00A77\u2014 \u0434\u0435\u043D\u044C \u043E\u0442\u0441\u0447\u0451\u0442\u0430"), false);
        source.sendSuccess(() -> Component.literal("\u00A77\u041E\u043F\u0446\u0438\u0438 toggle: \u00A7fllm, chat, sounds, monster, teleport, eat, doors"), false);
        source.sendSuccess(() -> Component.literal(""), false);

        source.sendSuccess(() -> Component.literal("\u00A76\u00A7l\u0412\u0437\u0430\u0438\u043C\u043E\u0434\u0435\u0439\u0441\u0442\u0432\u0438\u0435:"), false);
        source.sendSuccess(() -> Component.literal("\u00A7a\u041B\u041A\u041C \u00A77\u2014 \u043F\u0438\u043D\u043E\u043A Verity (\u043A\u0430\u043A \u043C\u044F\u0447)"), false);
        source.sendSuccess(() -> Component.literal("\u00A7a\u041F\u041A\u041C \u00A77\u2014 \u043F\u043E\u0434\u043E\u0431\u0440\u0430\u0442\u044C Verity \u0432 \u0438\u043D\u0432\u0435\u043D\u0442\u0430\u0440\u044C"), false);
        source.sendSuccess(() -> Component.literal("\u00A7aQ \u00A77\u2014 \u0431\u0440\u043E\u0441\u0438\u0442\u044C Verity \u043A\u0430\u043A \u043C\u044F\u0447"), false);
        source.sendSuccess(() -> Component.literal("\u00A7a\u041F\u041A\u041C \u0432 \u0432\u043E\u0437\u0434\u0443\u0445\u0435 \u00A77\u2014 \u0431\u0440\u043E\u0441\u043E\u043A Verity \u0438\u0437 \u0440\u0443\u043A\u0438"), false);
        source.sendSuccess(() -> Component.literal("\u00A7a\u0413\u043E\u043B\u043E\u0441 \u00A77\u2014 \u043A\u043D\u043E\u043F\u043A\u0430 PTT (\u0433\u043E\u0432\u043E\u0440\u0438\u0442\u044C \u0441 Verity)"), false);
        source.sendSuccess(() -> Component.literal("\u00A7a\u0427\u0430\u0442 \u00A77\u2014 \u043D\u0430\u043F\u0438\u0448\u0438\u0442\u0435 Verity \u0432 \u0447\u0430\u0442\u0435"), false);
        source.sendSuccess(() -> Component.literal(""), false);

        showAiHelp(source, true);
        showPhaseHelp(source, true);
    }

    private static void showHelpEn(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal("\u00A76\u00A7l=== Verity\u2122 ==="), false);
        source.sendSuccess(() -> Component.literal(
                "\u00A77Verity \u2014 a yellow ball. Your personal helper-friend. Knows everything."), false);
        source.sendSuccess(() -> Component.literal(
                "\u00A77Gradually transforms into a monster. Never lies. Never kills."), false);
        source.sendSuccess(() -> Component.literal(""), false);

        source.sendSuccess(() -> Component.literal("\u00A76\u00A7lCommands:"), false);
        source.sendSuccess(() -> Component.literal("\u00A7e/verity info \u00A77\u2014 status & settings"), false);
        source.sendSuccess(() -> Component.literal("\u00A7e/verity phase \u00A77\u2014 current phase"), false);
        source.sendSuccess(() -> Component.literal("\u00A7e/verity phase set <phase> \u00A77\u2014 HELPER/OMNISCIENT/COUNTDOWN/MONSTER/POSSESSIVE/HUNTER"), false);
        source.sendSuccess(() -> Component.literal("\u00A7e/verity toggle <opt> \u00A77\u2014 on/off options"), false);
        source.sendSuccess(() -> Component.literal("\u00A7e/verity face <0-11> \u00A77\u2014 change face"), false);
        source.sendSuccess(() -> Component.literal("\u00A7e/verity monster \u00A77\u2014 Monster Form"), false);
        source.sendSuccess(() -> Component.literal("\u00A7e/verity roof \u00A77\u2014 tear off roof"), false);
        source.sendSuccess(() -> Component.literal("\u00A7e/verity say <text> \u00A77\u2014 make Verity say text"), false);
        source.sendSuccess(() -> Component.literal("\u00A7e/verity teleport \u00A77\u2014 teleport to player"), false);
        source.sendSuccess(() -> Component.literal("\u00A7e/verity music \u00A77\u2014 play My Gal"), false);
        source.sendSuccess(() -> Component.literal("\u00A7e/verity lead <target> \u00A77\u2014 guide player (diamonds/home/village)"), false);
        source.sendSuccess(() -> Component.literal("\u00A7e/verity stop \u00A77\u2014 stop leading"), false);
        source.sendSuccess(() -> Component.literal("\u00A7e/verity reset \u00A77\u2014 reset to HELPER"), false);
        source.sendSuccess(() -> Component.literal("\u00A7e/verity clear \u00A77\u2014 remove Verity"), false);
        source.sendSuccess(() -> Component.literal("\u00A7e/verity spawn \u00A77\u2014 create Verity"), false);
        source.sendSuccess(() -> Component.literal("\u00A7e/verity day <0-3> \u00A77\u2014 countdown day"), false);
        source.sendSuccess(() -> Component.literal("\u00A77Toggle options: \u00A7fllm, chat, sounds, monster, teleport, eat, doors"), false);
        source.sendSuccess(() -> Component.literal(""), false);

        source.sendSuccess(() -> Component.literal("\u00A76\u00A7lInteraction:"), false);
        source.sendSuccess(() -> Component.literal("\u00A7aLMB \u00A77\u2014 kick Verity (like a ball)"), false);
        source.sendSuccess(() -> Component.literal("\u00A7aRMB \u00A77\u2014 pick up Verity to inventory"), false);
        source.sendSuccess(() -> Component.literal("\u00A7aQ \u00A77\u2014 throw Verity as a ball"), false);
        source.sendSuccess(() -> Component.literal("\u00A7aRMB in air \u00A77\u2014 throw Verity from hand"), false);
        source.sendSuccess(() -> Component.literal("\u00A7aVoice \u00A77\u2014 PTT key (talk to Verity)"), false);
        source.sendSuccess(() -> Component.literal("\u00A7aChat \u00A77\u2014 write to Verity in chat"), false);
        source.sendSuccess(() -> Component.literal(""), false);

        showAiHelp(source, false);
        showPhaseHelp(source, false);
    }

    private static void showAiHelp(CommandSourceStack source, boolean ru) {
        source.sendSuccess(() -> Component.literal("\u00A76\u00A7lAI / LLM:"), false);
        source.sendSuccess(() -> Component.literal(
                "\u00A77" + (ru ? "\u041F\u0440\u043E\u0432\u0430\u0439\u0434\u0435\u0440" : "Provider") + ": \u00A7f" + VerityConfig.llmProvider()), false);
        source.sendSuccess(() -> Component.literal(
                "\u00A77OpenRouter " + (ru ? "\u043C\u043E\u0434\u0435\u043B\u044C" : "model") + ": \u00A7f" + VerityConfig.selectedModel()), false);
        source.sendSuccess(() -> Component.literal(
                "\u00A77Gemini " + (ru ? "\u043C\u043E\u0434\u0435\u043B\u044C" : "model") + ": \u00A7f" + VerityConfig.geminiModel()
                + " (\u00A7" + (VerityConfig.hasGeminiKey()
                        ? (ru ? "a\u043A\u043B\u044E\u0447 \u0432\u0432\u0435\u0434\u0451\u043D" : "akey set")
                        : (ru ? "c\u043D\u0435\u0442 \u043A\u043B\u044E\u0447\u0430" : "cno key")) + "\u00A77)"), false);
        source.sendSuccess(() -> Component.literal(
                "\u00A77OpenRouter " + (ru ? "\u043A\u043B\u044E\u0447\u0438" : "keys") + ": \u00A7f" + VerityConfig.openRouterApiKeys().size()
                + " (\u00A7" + (VerityConfig.useBuiltinKeys()
                        ? (ru ? "a\u0432\u0441\u0442\u0440\u043E\u0435\u043D\u043D\u044B\u0435" : "abuiltin")
                        : (ru ? "e\u0441\u0432\u043E\u0438" : "ecustom")) + "\u00A77)"), false);
        source.sendSuccess(() -> Component.literal(""), false);

        source.sendSuccess(() -> Component.literal(
                "\u00A7e" + (ru ? "\u041A\u0430\u043A \u043F\u043E\u043B\u0443\u0447\u0438\u0442\u044C \u043A\u043B\u044E\u0447 Gemini" : "How to get Gemini key") + ":"), false);
        source.sendSuccess(() -> Component.literal("\u00A7f1. \u00A77https://aistudio.google.com/apikey"), false);
        source.sendSuccess(() -> Component.literal(
                ru ? "\u00A7f2. \u00A77\u0421\u043E\u0437\u0434\u0430\u0442\u044C API key (\u0431\u0435\u0441\u043F\u043B\u0430\u0442\u043D\u043E)" : "\u00A7f2. \u00A77Create API key (free)"), false);
        source.sendSuccess(() -> Component.literal(
                ru ? "\u00A7f3. \u00A77\u0412\u0432\u0435\u0441\u0442\u0438 \u0432 \u043D\u0430\u0441\u0442\u0440\u043E\u0439\u043A\u0430\u0445 \u043C\u043E\u0434\u0430 (Mod Menu \u2192 Verity \u2192 Settings)" : "\u00A7f3. \u00A77Enter in mod settings (Mod Menu \u2192 Verity \u2192 Settings)"), false);
        source.sendSuccess(() -> Component.literal(
                ru ? "\u00A7f4. \u00A77\u0412\u044B\u0431\u0440\u0430\u0442\u044C provider: gemini" : "\u00A7f4. \u00A77Select provider: gemini"), false);
        source.sendSuccess(() -> Component.literal(""), false);
        source.sendSuccess(() -> Component.literal(
                "\u00A7e" + (ru ? "\u041A\u0430\u043A \u043F\u043E\u043B\u0443\u0447\u0438\u0442\u044C \u043A\u043B\u044E\u0447 OpenRouter" : "How to get OpenRouter key") + ":"), false);
        source.sendSuccess(() -> Component.literal("\u00A7f1. \u00A77https://openrouter.ai/keys"), false);
        source.sendSuccess(() -> Component.literal(
                ru ? "\u00A7f2. \u00A77\u0412\u0432\u0435\u0441\u0442\u0438 \u0432 \u043D\u0430\u0441\u0442\u0440\u043E\u0439\u043A\u0430\u0445 \u043C\u043E\u0434\u0430" : "\u00A7f2. \u00A77Enter in mod settings"), false);
        source.sendSuccess(() -> Component.literal(""), false);
    }

    private static void showPhaseHelp(CommandSourceStack source, boolean ru) {
        source.sendSuccess(() -> Component.literal(
                "\u00A76\u00A7l" + (ru ? "\u0424\u0430\u0437\u044B Verity" : "Verity Phases") + ":"), false);
        source.sendSuccess(() -> Component.literal(
                "\u00A7aHELPER \u00A77\u2014 " + (ru ? "\u0434\u0440\u0443\u0436\u0435\u043B\u044E\u0431\u043D\u044B\u0439 \u043F\u043E\u043C\u043E\u0449\u043D\u0438\u043A" : "friendly helper")), false);
        source.sendSuccess(() -> Component.literal(
                "\u00A7eOMNISCIENT \u00A77\u2014 " + (ru ? "\u0437\u043D\u0430\u0435\u0442 \u043F\u0440\u043E \u0440\u0435\u0430\u043B\u044C\u043D\u044B\u0439 \u043C\u0438\u0440 \u0438\u0433\u0440\u043E\u043A\u0430" : "knows player's real world")), false);
        source.sendSuccess(() -> Component.literal(
                "\u00A76COUNTDOWN \u00A77\u2014 " + (ru ? "\u043E\u0431\u0440\u0430\u0442\u043D\u044B\u0439 \u043E\u0442\u0441\u0447\u0451\u0442 \u00AB3 \u0434\u043D\u044F\u00BB" : "countdown \u00AB3 days\u00BB")), false);
        source.sendSuccess(() -> Component.literal(
                "\u00A74MONSTER \u00A77\u2014 " + (ru ? "12-\u0444\u0443\u0442\u043E\u0432\u0430\u044F Monster Form (\u043F\u0443\u0433\u0430\u0435\u0442, \u043D\u0435 \u0443\u0431\u0438\u0432\u0430\u0435\u0442)" : "12-foot Monster Form (scares, doesn't kill)")), false);
        source.sendSuccess(() -> Component.literal(
                "\u00A7dPOSSESSIVE \u00A77\u2014 " + (ru ? "\u00ABYou have me\u00BB, \u0438\u0437\u043E\u043B\u044F\u0446\u0438\u044F" : "\u00ABYou have me\u00BB, isolation")), false);
        source.sendSuccess(() -> Component.literal(
                "\u00A7cHUNTER \u00A77\u2014 " + (ru ? "\u043E\u0445\u043E\u0442\u0430 \u043D\u0430 \u0434\u0440\u0443\u0433\u0438\u0445 \u0438\u0433\u0440\u043E\u043A\u043E\u0432" : "hunts other players")), false);
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

    private static String bool(boolean v) { return v ? "\u00A7aON" : "\u00A7cOFF"; }
    private static String getOptionStatus(String opt) {
        return switch (opt) {
            case "llm" -> bool(VerityConfig.llmEnabled());
            case "chat" -> bool(VerityConfig.chatEnabled());
            case "sounds" -> bool(VerityConfig.soundsEnabled());
            case "monster" -> bool(VerityConfig.monsterFormEnabled());
            case "teleport" -> bool(VerityConfig.teleportEnabled());
            case "eat" -> bool(VerityConfig.villagerEatingEnabled());
            case "doors" -> bool(VerityConfig.doorTelekinesisEnabled());
            default -> "\u00A77?";
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
