package net.verity.entity;

import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.BreakDoorGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.verity.VerityMod;

import java.util.EnumSet;

public class VerityMonsterEntity extends Monster {

    private int ambientSoundCooldown = 0;
    private int teleportCooldown = 0;
    private VerityEntity linkedVerity = null;
    // Stuck detection for glass/thin-wall breaking
    private Vec3 lastPos = null;
    private int stuckTicks = 0;
    private int glassBreakCooldown = 0;

    public VerityMonsterEntity(EntityType<? extends Monster> entityType, Level level) {
        super(entityType, level);
    }

    public void setLinkedVerity(VerityEntity linkedVerity) {
        this.linkedVerity = linkedVerity;
    }

    public VerityEntity getLinkedVerity() {
        return this.linkedVerity;
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        // Vanilla BreakDoorGoal — handles wooden and iron doors properly (like a zombie)
        this.goalSelector.addGoal(1, new BreakDoorGoal(this, difficulty -> true));
        // Follow player but DON'T attack — Verity scares, doesn't kill
        this.goalSelector.addGoal(2, new VerityScareGoal(this, 1.55D));
        this.goalSelector.addGoal(3, new LookAtPlayerGoal(this, Player.class, 20.0F));
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

        // Spawn effects on first tick
        if (this.tickCount == 1) {
            this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                    SoundEvents.ENDER_DRAGON_GROWL, SoundSource.HOSTILE, 2.0F, 0.5F);
            // Darkness to all nearby players on spawn
            for (Player p : this.level().players()) {
                if (p.isAlive() && this.distanceToSqr(p) < 1024.0D) {
                    p.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 200, 0, false, false, true));
                }
            }
        }

        // Forgiveness logic: player shift keys nearby
        Player nearestShiftPlayer = this.level().getNearestPlayer(this, 8.0D);
        if (nearestShiftPlayer != null && nearestShiftPlayer.isAlive()
                && nearestShiftPlayer.isShiftKeyDown() && this.distanceToSqr(nearestShiftPlayer) < 16.0D) {
            nearestShiftPlayer.sendSystemMessage(Component.literal(
                    "\u00a7e<Verity\u2122>\u00a7r ...\u0422\u044b \u0432\u0435\u0440\u043d\u0443\u043b\u0441\u044f. \u0425\u043e\u0440\u043e\u0448\u043e."));
            nearestShiftPlayer.sendSystemMessage(Component.literal(
                    "\u00a7e<Verity\u2122>\u00a7r \u042f \u043f\u0440\u043e\u0449\u0430\u044e \u0442\u0435\u0431\u044f."));
            if (this.linkedVerity != null) {
                this.linkedVerity.setVerityPhase(VerityEntity.VerityPhase.POSSESSIVE);
            }
            this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                    SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 1.5F, 0.5F);
            this.discard();
            return;
        }

        // Apply darkness + teleport logic for nearest player
        Player player = this.level().getNearestPlayer(this, 16.0D);
        if (player != null && player.isAlive()) {
            player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 100, 0, false, false, true));
            if (this.teleportCooldown > 0) {
                this.teleportCooldown--;
            } else if (this.distanceToSqr(player) > 16.0D && this.random.nextInt(60) == 0) {
                if (!isPlayerLookingAtMe(player)) {
                    teleportBehindPlayer(player);
                    this.teleportCooldown = 100;
                }
            }
        }

        // ── Stuck detection: break glass / soft blocks when movement is blocked ──
        if (glassBreakCooldown > 0) glassBreakCooldown--;

        // Sample position every 10 ticks
        if (this.tickCount % 10 == 0) {
            Vec3 currentPos = this.position();
            Player glassTarget = this.level().getNearestPlayer(this, 32.0D);
            if (lastPos != null && glassTarget != null && this.distanceToSqr(glassTarget) > 9.0D) {
                double moved = currentPos.distanceToSqr(lastPos);
                if (moved < 0.01D) {
                    stuckTicks += 10;
                } else {
                    stuckTicks = 0;
                }
                // Been stuck >1 second → break soft blocks in facing direction
                if (stuckTicks >= 20 && glassBreakCooldown <= 0) {
                    breakSoftBlocksAhead();
                    stuckTicks = 0;
                    glassBreakCooldown = 20;
                }
            }
            lastPos = currentPos;
        }
    }

    /** Breaks glass, panes, and other soft blocks directly ahead of the monster */
    private void breakSoftBlocksAhead() {
        net.minecraft.core.Direction facing = this.getDirection();
        for (int dy = 0; dy <= 2; dy++) {
            BlockPos front = this.blockPosition().relative(facing).above(dy);
            if (!this.level().hasChunkAt(front)) continue;
            net.minecraft.world.level.block.state.BlockState state = this.level().getBlockState(front);
            if (state.isAir()) continue;
            float hardness = state.getDestroySpeed(this.level(), front);
            if (hardness >= 0 && hardness < 3.0f) {
                this.level().destroyBlock(front, true);
                this.level().playSound(null, front.getX(), front.getY(), front.getZ(),
                        SoundEvents.GLASS_BREAK, SoundSource.BLOCKS, 1.5F, 0.8F);
                return; // break one block per attempt
            }
        }
        // Also try one block to the side if directly ahead is clear
        for (net.minecraft.core.Direction side : new net.minecraft.core.Direction[]{
                facing.getClockWise(), facing.getCounterClockWise()}) {
            for (int dy = 0; dy <= 2; dy++) {
                BlockPos sidePos = this.blockPosition().relative(side).above(dy);
                if (!this.level().hasChunkAt(sidePos)) continue;
                net.minecraft.world.level.block.state.BlockState sideState = this.level().getBlockState(sidePos);
                if (sideState.isAir()) continue;
                float hardness = sideState.getDestroySpeed(this.level(), sidePos);
                if (hardness >= 0 && hardness < 3.0f) {
                    this.level().destroyBlock(sidePos, true);
                    this.level().playSound(null, sidePos.getX(), sidePos.getY(), sidePos.getZ(),
                            SoundEvents.GLASS_BREAK, SoundSource.BLOCKS, 1.5F, 0.8F);
                    return;
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
            // Note: glass/window breaking is handled in tick() via stuck detection.
            // Door breaking is handled by the vanilla BreakDoorGoal.
        }
    }
}
