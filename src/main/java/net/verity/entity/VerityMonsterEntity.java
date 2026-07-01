package net.verity.entity;

import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.verity.VerityMod;

import java.util.EnumSet;

public class VerityMonsterEntity extends Monster {

    private int ambientSoundCooldown = 0;
    private int teleportCooldown = 0;

    public VerityMonsterEntity(EntityType<? extends Monster> entityType, Level level) {
        super(entityType, level);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        // Follow player but DON'T attack — Verity scares, doesn't kill
        this.goalSelector.addGoal(1, new VerityScareGoal(this, 1.4D));
        this.goalSelector.addGoal(2, new LookAtPlayerGoal(this, Player.class, 16.0F));
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 10000.0)
                .add(Attributes.MOVEMENT_SPEED, 0.35)
                .add(Attributes.ATTACK_DAMAGE, 0.0) // No damage — Verity scares, doesn't kill
                .add(Attributes.FOLLOW_RANGE, 64.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false; // Immune to all damage!
    }

    @Override
    public void tick() {
        super.tick();

        if (this.level().isClientSide) return;

        // Apply darkness to nearby players
        Player player = this.level().getNearestPlayer(this, 16.0D);
        if (player != null && player.isAlive()) {
            player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 100, 0, false, false, true));

            // Teleport behind player when not being looked at
            if (this.teleportCooldown > 0) {
                this.teleportCooldown--;
            } else if (this.distanceToSqr(player) > 16.0D && this.random.nextInt(60) == 0) {
                if (!isPlayerLookingAtMe(player)) {
                    teleportBehindPlayer(player);
                    this.teleportCooldown = 100;
                }
            }
        }
    }

    private boolean isPlayerLookingAtMe(Player player) {
        Vec3 rotationVec = player.getViewVector(1.0F).normalize();
        Vec3 toEntityVec = new Vec3(
                this.getX() - player.getX(),
                this.getEyeY() - player.getEyeY(),
                this.getZ() - player.getZ()
        ).normalize();
        return rotationVec.dot(toEntityVec) > 0.5;
    }

    private void teleportBehindPlayer(Player player) {
        Vec3 rotationVec = player.getViewVector(1.0F).normalize();
        double startX = player.getX() - rotationVec.x * 3.0D + (this.random.nextDouble() - 0.5D) * 1.5D;
        double startY = player.getY();
        double startZ = player.getZ() - rotationVec.z * 3.0D + (this.random.nextDouble() - 0.5D) * 1.5D;

        Vec3 safePos = findSafeTeleportPos(this.level(), startX, startY, startZ, player);

        this.teleportTo(safePos.x, safePos.y, safePos.z);
    }

    private Vec3 findSafeTeleportPos(Level level, double startX, double startY, double startZ, Player player) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos((int) startX, (int) startY, (int) startZ);
        
        // Check surrounding blocks to ensure there is a 5-block high air column for the 5-block tall monster
        for (int dy = 0; dy <= 3; dy++) {
            for (int sign : new int[]{1, -1}) {
                pos.set(startX, startY + dy * sign, startZ);
                if (level.getBlockState(pos).isAir() && 
                    level.getBlockState(pos.above(1)).isAir() && 
                    level.getBlockState(pos.above(2)).isAir() && 
                    level.getBlockState(pos.above(3)).isAir() && 
                    level.getBlockState(pos.above(4)).isAir() && 
                    !level.getBlockState(pos.below()).getCollisionShape(level, pos.below()).isEmpty()) {
                    return new Vec3(startX, startY + dy * sign, startZ);
                }
            }
        }
        
        // Fallback: spawn closer to the player (1.5 blocks behind) where it is highly likely to be clear of walls
        Vec3 rotationVec = player.getViewVector(1.0F).normalize();
        double fallbackX = player.getX() - rotationVec.x * 1.5D;
        double fallbackY = player.getY();
        double fallbackZ = player.getZ() - rotationVec.z * 1.5D;
        return new Vec3(fallbackX, fallbackY, fallbackZ);
    }

    /**
     * Scare goal — follows player, makes creepy sounds, but NEVER attacks.
     * Verity scares, doesn't kill (canonical behavior).
     */
    static class VerityScareGoal extends Goal {
        private final VerityMonsterEntity entity;
        private Player target;
        private final double speed;
        private int soundCooldown = 0;

        public VerityScareGoal(VerityMonsterEntity entity, double speed) {
            this.entity = entity;
            this.speed = speed;
            this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            this.target = entity.level().getNearestPlayer(entity, 64.0D);
            return this.target != null && this.target.isAlive();
        }

        @Override
        public boolean canContinueToUse() {
            return this.target != null && this.target.isAlive();
        }

        @Override
        public void tick() {
            if (target == null) return;
            double dist = entity.distanceToSqr(target);

            // Follow player
            if (dist > 4.0D) {
                entity.getNavigation().moveTo(target, speed);
            } else {
                entity.getNavigation().stop();
            }

            // Look at player
            entity.getLookControl().setLookAt(target, 180.0F, 180.0F);

            // Creepy sounds when close
            if (soundCooldown > 0) {
                soundCooldown--;
            } else if (dist < 100.0D && entity.random.nextInt(60) == 0) {
                entity.level().playSound(null, target.getX(), target.getY(), target.getZ(),
                        SoundEvents.WARDEN_HEARTBEAT, SoundSource.HOSTILE, 0.8F, 0.5F);
                soundCooldown = 100;
            }
        }
    }
}
