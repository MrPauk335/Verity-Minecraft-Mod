package net.verity.block;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.verity.VerityMod;
import net.minecraft.server.level.ServerPlayer;
import net.verity.entity.CardboardBoxEntity;
import net.verity.entity.VerityEntity;

public class CardboardBoxBlock extends Block {
    public CardboardBoxBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        triggerOpen(level, pos, player);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
        triggerOpen(level, pos, player);
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    private void triggerOpen(Level level, BlockPos pos, Player player) {
        if (!level.isClientSide) {
            // Play cardboard box punch/activation sound
            if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
                VerityMod.playSoundEffect(serverPlayer, "punchcardboardbox", "block", 1.2F, 1.0F);
            }

            // Spawn Cardboard Box Entity
            CardboardBoxEntity boxEntity = new CardboardBoxEntity(VerityMod.CARDBOARD_BOX_ENTITY, level);
            boxEntity.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, 0.0F, 0.0F);
            level.addFreshEntity(boxEntity);

            // Set block to air (we do it without destroyBlock to prevent double sound/particles,
            // as the entity will play its own explosion/opening effects later!)
            level.setBlockAndUpdate(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
        }
    }
}
