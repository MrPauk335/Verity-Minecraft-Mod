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
import net.minecraft.server.level.ServerPlayer;

public class ClosedBoxEntity extends Entity {
    
    private static final EntityDataAccessor<Integer> AGE = SynchedEntityData.defineId(ClosedBoxEntity.class, EntityDataSerializers.INT);

    public ClosedBoxEntity(EntityType<?> entityType, Level level) {
        super(entityType, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(AGE, 0);
    }

    private int muffledTimer = 0;

    @Override
    public void tick() {
        super.tick();
        // Stay perfectly still — no physics, no gravity, no shaking
        this.setDeltaMovement(Vec3.ZERO);
        this.setNoGravity(true);
        this.setPos(this.getX(), this.getY(), this.getZ());

        // Muffled voice from inside the box: "еей еей здесь есть кто"
        if (!this.level().isClientSide) {
            muffledTimer++;
            if (muffledTimer >= 80) { // ~4 seconds
                muffledTimer = 0;
                Player nearest = this.level().getNearestPlayer(this, 8.0D);
                if (nearest != null && nearest instanceof net.minecraft.server.level.ServerPlayer sp) {
                    // Muffled (low volume) box sound — represents Verity calling from inside
                    VerityMod.playSoundEffect(sp, "punchcardboardbox", "block", 0.35F, 0.6F);
                    // Speak the line via TTS on the client
                    net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(sp,
                            new net.verity.net.TTSPayload("\u0435\u0435\u0439 \u0435\u0435\u0439 \u0437\u0434\u0435\u0441\u044c \u0435\u0441\u0442\u044c \u043a\u0442\u043e"));
                }
            }
        }
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
        if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
            VerityMod.playSoundEffect(serverPlayer, "punchcardboardbox", "block", 1.2F, 1.0F);
        }

        // Remove the closed box
        this.discard();
    }
}