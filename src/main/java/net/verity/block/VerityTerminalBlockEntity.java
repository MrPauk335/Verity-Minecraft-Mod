package net.verity.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Block entity for Verity's Terminal.
 */
public class VerityTerminalBlockEntity extends BlockEntity {

    public VerityTerminalBlockEntity(BlockPos pos, BlockState state) {
        super(net.verity.VerityMod.VERITY_TERMINAL_ENTITY, pos, state);
    }
}
