package net.verity.block;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.verity.VerityMod;
import net.verity.entity.VerityEntity;

/**
 * Block entity for Verity's Anchor.
 * Tracks linked Verity and handles respawn logic.
 */
public class VerityAnchorBlockEntity extends BlockEntity {

    private boolean triggeredFinalPhase = false;

    public VerityAnchorBlockEntity(BlockPos pos, BlockState state) {
        super(VerityMod.VERITY_ANCHOR_ENTITY, pos, state);
    }

    public void tickServer() {
        if (triggeredFinalPhase) return;
        if (level == null || level.isClientSide) return;

        // Check if any Verity is nearby and alive
        var verities = level.getEntitiesOfClass(
                VerityEntity.class,
                new net.minecraft.world.phys.AABB(worldPosition).inflate(256.0),
                e -> e.isAlive());

        if (verities.isEmpty()) {
            // No Verity alive — respawn one from the anchor
            VerityEntity verity = new VerityEntity(VerityMod.VERITY_ENTITY, level);
            verity.moveTo(worldPosition.getX() + 0.5, worldPosition.getY() + 1.5, worldPosition.getZ() + 0.5,
                    0, 0);
            verity.setVerityPhase(VerityEntity.VerityPhase.HELPER);
            level.addFreshEntity(verity);
        }
    }

    public void triggerFinalPhase() {
        triggeredFinalPhase = true;
    }

    public boolean hasTriggeredFinalPhase() {
        return triggeredFinalPhase;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putBoolean("FinalPhaseTriggered", triggeredFinalPhase);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        triggeredFinalPhase = tag.getBoolean("FinalPhaseTriggered");
    }
}
