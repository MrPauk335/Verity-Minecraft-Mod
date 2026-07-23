package net.verity.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * The Gun item — used in the final phase to "end" the game.
 * When used on self, triggers the time loop ending.
 */
public class VerityGunItem extends Item {

    public VerityGunItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (!level.isClientSide && player instanceof ServerPlayer sp) {
            // Player used the gun on themselves
            // Send the final message and close the game
            sp.sendSystemMessage(Component.literal(
                    "\u00a74<Verity>\u00a7r ...").withStyle(ChatFormatting.RED));

            // Schedule game close after a short delay
            if (level.getServer() != null) {
                    level.getServer().tell(new net.minecraft.server.TickTask(
                            level.getServer().getTickCount() + 40, () -> {
                        // Send disconnect message
                        for (ServerPlayer online : level.getServer().getPlayerList().getPlayers()) {
                            online.sendSystemMessage(Component.literal(
                                    "\u00a74\u0412\u044b\u0441\u0442\u0440\u0435\u043b... \u0426\u0438\u043a\u043b \u043f\u043e\u0432\u0442\u043e\u0440\u044f\u0435\u0442\u0441\u044f.").withStyle(ChatFormatting.DARK_RED));
                        }
                        // Close the server (time loop)
                        level.getServer().close();
                    }));
            }

            return InteractionResultHolder.sidedSuccess(stack, false);
        }

        return InteractionResultHolder.pass(stack);
    }

    @Override
    public Component getDescription() {
        return Component.literal("???").withStyle(ChatFormatting.DARK_RED);
    }
}
