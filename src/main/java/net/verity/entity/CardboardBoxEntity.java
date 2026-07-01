package net.verity.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.verity.VerityMod;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.particles.ParticleTypes;

public class CardboardBoxEntity extends Entity {
    private static final EntityDataAccessor<Integer> AGE = SynchedEntityData.defineId(CardboardBoxEntity.class, EntityDataSerializers.INT);

    private static final double BOX_SIZE = 0.6D;

    private boolean spawned = false;

    public CardboardBoxEntity(EntityType<?> entityType, Level level) {
        super(entityType, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(AGE, 0);
    }

    public int getAge() {
        return this.entityData.get(AGE);
    }

    public void setAge(int age) {
        this.entityData.set(AGE, age);
    }

    public boolean isShaking() {
        return getAge() < 60;
    }

    @Override
    public void tick() {
        super.tick();

        int age = this.getAge();
        this.setAge(age + 1);

        if (age < 60) {
            // Float upwards slowly
            this.setPos(this.getX(), this.getY() + 0.03D, this.getZ());
        } else {
            if (!this.level().isClientSide && !spawned) {
                spawned = true;
                spawnVerity();
                this.discard();
            }
        }
    }

    private void spawnVerity() {
        VerityEntity verity = new VerityEntity(VerityMod.VERITY_ENTITY, this.level());
        verity.moveTo(this.getX(), this.getY() + 0.5D, this.getZ(), 0.0F, 0.0F);
        verity.setNoGravity(true);
        verity.setDeltaMovement(Vec3.ZERO);
        verity.setFaceIndex(VerityEntity.FACE_HURT);
        verity.setIntroTicks(60);
        this.level().addFreshEntity(verity);

        // Particles
        if (this.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(
                ParticleTypes.EXPLOSION,
                this.getX(), this.getY() + 0.5D, this.getZ(),
                15, 0.5D, 0.5D, 0.5D, 0.05D
            );
        }
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.contains("Age")) {
            setAge(tag.getInt("Age"));
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("Age", getAge());
    }
}
