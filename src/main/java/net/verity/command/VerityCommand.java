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
 *   /verity                — помощь
 *   /verity info           — статус мода
 *   /verity phase          — фаза ближайшего Verity
 *   /verity phase set <ф>  — установить фазу
 *   /verity toggle <опция> — вкл/выкл опцию
 *   /verity face <номер>   — установить лицо
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
                .executes(ctx -> showHelp(ctx.getSource()))
        );
    }

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
            case DORMANT -> "§7💤 DORMANT";
            case HELPER -> "§a😊 HELPER";
            case OMNISCIENT -> "§e👁 OMNISCIENT";
            case COUNTDOWN -> "§6⏳ COUNTDOWN";
            case MONSTER -> "§4👹 MONSTER";
            case POSSESSIVE -> "§d💜 POSSESSIVE";
            case HUNTER -> "§c🗡 HUNTER";
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
            source.sendSuccess(() -> Component.literal("§a✓ Verity → " + phase.name()), true);
        } catch (Exception e) {
            source.sendFailure(Component.literal("§cФазы: HELPER, OMNISCIENT, COUNTDOWN, MONSTER, POSSESSIVE, HUNTER"));
        }
        return 1;
    }

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
            source.sendSuccess(() -> Component.literal("§a✓ " + option + " включено"), true);
        } else {
            source.sendSuccess(() -> Component.literal("§c✗ " + option + " выключено"), true);
        }
        return 1;
    }

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
            source.sendSuccess(() -> Component.literal("§a✓ Лицо: " + faceName(face)), true);
        } catch (NumberFormatException e) {
            source.sendFailure(Component.literal("§cУкажи номер от 0 до 11"));
        }
        return 1;
    }

    private static int showHelp(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal("§6=== Verity Commands ==="), false);
        source.sendSuccess(() -> Component.literal("§e/verity §7— помощь"), false);
        source.sendSuccess(() -> Component.literal("§e/verity info §7— статус"), false);
        source.sendSuccess(() -> Component.literal("§e/verity phase §7— фаза Verity"), false);
        source.sendSuccess(() -> Component.literal("§e/verity phase set <ф> §7— сменить фазу"), false);
        source.sendSuccess(() -> Component.literal("§e/verity toggle <opt> §7— вкл/выкл"), false);
        source.sendSuccess(() -> Component.literal("§e/verity face <0-11> §7— лицо"), false);
        source.sendSuccess(() -> Component.literal("§7Опции: llm, chat, sounds, monster, teleport, eat, doors"), false);
        return 1;
    }

    private static VerityEntity findNearest(ServerPlayer player) {
        var entities = player.level().getEntitiesOfClass(
            VerityEntity.class,
            player.getBoundingBox().inflate(64.0D),
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