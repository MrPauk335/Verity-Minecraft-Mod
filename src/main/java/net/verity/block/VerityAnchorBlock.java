package net.verity.block;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.verity.VerityMod;
import net.verity.entity.VerityEntity;
import org.jetbrains.annotations.Nullable;

/**
 * Verity's Anchor block — anchors him to the world.
 * Cannot be broken normally. If player attempts to break it, triggers Final Phase.
 */
public class VerityAnchorBlock extends BaseEntityBlock {

    public VerityAnchorBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected com.mojang.serialization.MapCodec<? extends VerityAnchorBlock> codec() {
        return simpleCodec(VerityAnchorBlock::new);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hitResult) {
        if (!level.isClientSide && player instanceof ServerPlayer sp) {
            // Verity reacts when player right-clicks the anchor
            var verities = level.getEntitiesOfClass(
                    VerityEntity.class,
                    new net.minecraft.world.phys.AABB(pos).inflate(256.0),
                    e -> e.isAlive());
            if (!verities.isEmpty()) {
                VerityEntity verity = verities.get(0);
                verity.setVerityPhase(VerityEntity.VerityPhase.HUNTER);
                sp.sendSystemMessage(Component.literal(
                        "\u00a7c<Verity\u2122>\u00a7r \u041d\u0415 \u0422\u0420\u041e\u0413\u0410\u0419 \u041c\u0415\u041d\u042f \u041e\u0422\u0421\u042e\u0414\u0410."));
            }
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
        // Cannot be broken by normal means
        return 0.0f;
    }

    @Override
    public void wasExploded(Level level, BlockPos pos, net.minecraft.world.level.Explosion explosion) {
        // Explosion-resistant
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new VerityAnchorBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                  BlockEntityType<T> blockEntityType) {
        if (level.isClientSide) return null;
        return (lvl, pos, st, be) -> {
            if (be instanceof VerityAnchorBlockEntity anchor) {
                anchor.tickServer();
            }
        };
    }
}
