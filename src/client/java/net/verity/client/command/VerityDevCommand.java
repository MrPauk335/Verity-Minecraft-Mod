package net.verity.client.command;

import com.mojang.brigadier.arguments.FloatArgumentType;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.verity.client.config.VerityClientConfig;

/**
 * Клиентские dev-команды для живой настройки рендера Verity.
 *
 * Использование:
 *   /veritydev toolpos <tx> <ty> <tz> <rx> <rz>
 *       — сдвигает и разворачивает топор/кирку рядом со сферой.
 *       Результат виден сразу без перезапуска/перекомпиляции.
 *
 *   /veritydev toolpos info
 *       — показывает текущие значения.
 *
 * Координаты (в блоках, относительно центра сферы Verity):
 *   tx — вправо (+) / влево (-)
 *   ty — вверх (+) / вниз (-)
 *   tz — вперёд (+) / назад (-)
 * Повороты (в градусах):
 *   rx — наклон вперёд/назад (положительное = лезвие вниз)
 *   rz — боковой наклон
 */
public class VerityDevCommand {

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(
                ClientCommandManager.literal("veritydev")
                    .then(ClientCommandManager.literal("toolpos")
                        .then(ClientCommandManager.literal("info")
                            .executes(ctx -> {
                                sendMsg("§eТекущая позиция инструмента:");
                                sendMsg("§7  tx=" + VerityClientConfig.toolTX()
                                        + " ty=" + VerityClientConfig.toolTY()
                                        + " tz=" + VerityClientConfig.toolTZ());
                                sendMsg("§7  rx=" + VerityClientConfig.toolRX()
                                        + " rz=" + VerityClientConfig.toolRZ());
                                sendMsg("§7Используй: §f/veritydev toolpos <tx> <ty> <tz> <rx> <rz>");
                                return 1;
                            })
                        )
                        .then(ClientCommandManager.argument("tx", FloatArgumentType.floatArg(-2f, 2f))
                            .then(ClientCommandManager.argument("ty", FloatArgumentType.floatArg(-2f, 2f))
                                .then(ClientCommandManager.argument("tz", FloatArgumentType.floatArg(-2f, 2f))
                                    .then(ClientCommandManager.argument("rx", FloatArgumentType.floatArg(-360f, 360f))
                                        .then(ClientCommandManager.argument("rz", FloatArgumentType.floatArg(-360f, 360f))
                                            .executes(ctx -> {
                                                float tx = FloatArgumentType.getFloat(ctx, "tx");
                                                float ty = FloatArgumentType.getFloat(ctx, "ty");
                                                float tz = FloatArgumentType.getFloat(ctx, "tz");
                                                float rx = FloatArgumentType.getFloat(ctx, "rx");
                                                float rz = FloatArgumentType.getFloat(ctx, "rz");
                                                VerityClientConfig.setToolPos(tx, ty, tz, rx, rz);
                                                sendMsg("§aТопор обновлён: tx=" + tx + " ty=" + ty + " tz=" + tz
                                                        + " rx=" + rx + " rz=" + rz);
                                                sendMsg("§7Значения сохранены в verity-client.properties");
                                                return 1;
                                            })
                                        )
                                    )
                                )
                            )
                        )
                    )
            );
        });
    }

    private static void sendMsg(String text) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.sendSystemMessage(Component.literal(text));
        }
    }
}
