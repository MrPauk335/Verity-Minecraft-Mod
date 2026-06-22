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

public class CardboardBoxEntity extends Entity {
    private static final EntityDataAccessor<Integer> AGE = SynchedEntityData.defineId(CardboardBoxEntity.class, EntityDataSerializers.INT);

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

        int age = getAge();
        if (!this.level().isClientSide) {
            setAge(age + 1);
        }

        if (age < 60) {
            // Levitating/floating up slowly
            this.setDeltaMovement(0.0D, 0.02D, 0.0D);
            this.setPos(this.getX(), this.getY() + 0.02D, this.getZ());
        } else if (age == 60 && !this.level().isClientSide) {
            // Time to open!
            VerityEntity sphere = new VerityEntity(VerityMod.VERITY_ENTITY, this.level());
            sphere.moveTo(this.getX(), this.getY(), this.getZ(), 0.0F, 0.0F);
            
            // Hang in the air for 1.5 seconds (30 ticks)
            sphere.setNoGravity(true);
            sphere.setDeltaMovement(Vec3.ZERO);
            sphere.setFaceIndex(VerityEntity.FACE_HURT);
            sphere.setIntroTicks(30);
            
            this.level().addFreshEntity(sphere);

            // Play opening burst sound
            this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                    VerityMod.SOUND_GONE, SoundSource.BLOCKS, 1.2F, 1.0F);
            
            // Spawn explosion smoke particles on the server level
            if (this.level() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
                serverLevel.sendParticles(
                        net.minecraft.core.particles.ParticleTypes.EXPLOSION,
                        this.getX(), this.getY() + 0.5D, this.getZ(),
                        15, 0.5D, 0.5D, 0.5D, 0.05D
                );
            }

            // Discard the box entity
            this.discard();
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
