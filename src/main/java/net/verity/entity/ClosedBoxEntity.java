package net.verity.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.BlockPos;
import net.verity.VerityMod;

public class ClosedBoxEntity extends Entity {
    
    private static final EntityDataAccessor<Integer> AGE = SynchedEntityData.defineId(ClosedBoxEntity.class, EntityDataSerializers.INT);

    public ClosedBoxEntity(EntityType<?> entityType, Level level) {
        super(entityType, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(AGE, 0);
    }

    @Override
    public void tick() {
        super.tick();
        // Stay perfectly still — no physics, no gravity, no shaking
        this.setDeltaMovement(Vec3.ZERO);
        this.setNoGravity(true);
        this.setPos(this.getX(), this.getY(), this.getZ());
    }

    @Override
    public boolean isPickable() {
        return true;
    }

    @Override
    public boolean canBeCollidedWith() {
        return true;
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (!this.level().isClientSide && this.isAlive()) {
            openBox(player);
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.contains("Age")) {
            this.entityData.set(AGE, tag.getInt("Age"));
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("Age", this.entityData.get(AGE));
    }

    private void openBox(Player player) {
        // Spawn CardboardBoxEntity at this position
        CardboardBoxEntity boxEntity = new CardboardBoxEntity(VerityMod.CARDBOARD_BOX_ENTITY, this.level());
        boxEntity.moveTo(this.getX(), this.getY(), this.getZ(), 0.0F, 0.0F);
        this.level().addFreshEntity(boxEntity);

        // Play cardboard box punch/activation sound
        this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                VerityMod.SOUND_PUNCH_BOX, net.minecraft.sounds.SoundSource.BLOCKS, 1.2F, 1.0F);

        // Remove the closed box
        this.discard();
    }
}