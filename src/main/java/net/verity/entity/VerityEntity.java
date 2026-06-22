package net.verity.entity;

import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.verity.VerityMod;

import java.util.EnumSet;

public class VerityEntity extends PathfinderMob {
    // ── face_index (0-11), matches Bedrock addon's pntmc:face_index ─────────
    public static final int FACE_SMILE = 0;
    public static final int FACE_SPEAK = 1;
    public static final int FACE_HURT = 2;
    public static final int FACE_ABNORMAL_SHUT = 3;
    public static final int FACE_ABNORMAL_OPEN = 4;
    public static final int FACE_BORED_P2 = 5;
    public static final int FACE_DAY2_SHUT = 6;
    public static final int FACE_DAY2_OPEN = 7;
    public static final int FACE_CREEPY_SMILE = 8;
    public static final int FACE_SERIOUS_1 = 9;
    public static final int FACE_SERIOUS_2 = 10;
    public static final int FACE_SERIOUS_3 = 11;

    private static final EntityDataAccessor<Integer> FACE_INDEX = SynchedEntityData.defineId(VerityEntity.class,
            EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> FACELESS = SynchedEntityData.defineId(VerityEntity.class,
            EntityDataSerializers.BOOLEAN);
    // Phase: 1 = friendly, 2 = creepy/stalker
    private static final EntityDataAccessor<Integer> PHASE = SynchedEntityData.defineId(VerityEntity.class,
            EntityDataSerializers.INT);

    private int chatCooldown = 200;
    private int teleportCooldown = 0;
    private int talkAnimTick = 0;
    private boolean isTalking = false;
    private int ambientSoundCooldown = 0;
    private int introTicks = 0;

    // ── Rolling animation state ──────────────────────────────────────────
    // Grows while the ball is moving (matches old limbSwing-based rolling),
    // and decays back to 0 when idle so the face naturally returns to facing
    // the look direction instead of staying stuck wherever it last rolled to.
    private float rollAngle = 0.0F;

    private static final String[] FRIENDLY_MESSAGES = {
            "§e<Verity>§r Привет! Я Верити, твой лучший друг!",
            "§e<Verity>§r Давай копать шахты вместе!",
            "§e<Verity>§r Сегодня отличный день для приключений!",
            "§e<Verity>§r Я знаю, что ты делал в прошлую игровую ночь...",
            "§e<Verity>§r Ты выглядишь очень дружелюбно.",
            "§e<Verity>§r Что мы будем строить дальше?",
            "§e<Verity>§r Я счастлива быть рядом с тобой."
    };

    private static final String[] CREEPY_MESSAGES = {
            "§c<Verity>§r ПОЧЕМУ ТЫ МЕНЯ УДАРИЛ?",
            "§c<Verity>§r Ты не сможешь убежать.",
            "§c<Verity>§r Я вижу тебя.",
            "§c<Verity>§r МЫ БУДЕМ ВМЕСТЕ НАВСЕГДА.",
            "§c<Verity>§r В твоём мире так темно...",
            "§c<Verity>§r Я стою прямо за твоей спиной.",
            "§c<Verity>§r Ты совершил большую ошибку.",
            "§c<Verity>§r Я всегда выглядела вот так."
    };

    public VerityEntity(EntityType<? extends PathfinderMob> entityType, Level level) {
        super(entityType, level);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new FollowPlayerGoal(this, 1.2D));
        this.goalSelector.addGoal(2, new LookAtPlayerGoal(this, Player.class, 8.0F));
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 100.0)
                .add(Attributes.MOVEMENT_SPEED, 0.25)
                .add(Attributes.FOLLOW_RANGE, 64.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(FACE_INDEX, FACE_SMILE);
        builder.define(FACELESS, false);
        builder.define(PHASE, 1);
    }

    // ── Getters & Setters ──────────────────────────────────────────────────
    public int getFaceIndex() {
        return this.entityData.get(FACE_INDEX);
    }

    public void setFaceIndex(int face) {
        this.entityData.set(FACE_INDEX, face);
    }

    public boolean isFaceless() {
        return this.entityData.get(FACELESS);
    }

    public void setFaceless(boolean v) {
        this.entityData.set(FACELESS, v);
    }

    public int getPhase() {
        return this.entityData.get(PHASE);
    }

    public void setPhase(int p) {
        this.entityData.set(PHASE, p);
    }

    /** Legacy helper so renderer glow layer still works */
    public boolean isCreepy() {
        return getPhase() >= 2;
    }

    public void setIntroTicks(int ticks) {
        this.introTicks = ticks;
    }

    public boolean isTalking() {
        int face = getFaceIndex();
        return face == FACE_SPEAK || face == FACE_ABNORMAL_OPEN;
    }

    /**
     * Current rolling angle in the same "natural" (radian-scale) units the
     * old limbSwing-based value used — NOT degrees. Already decayed when idle.
     * Read by VerityEntityModel each render frame to orient the sphere mesh.
     */
    public float getRollAngle() {
        return this.rollAngle;
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (this.level().isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (this.isFaceless()) {
            return InteractionResult.PASS;
        }

        ItemStack pickedStack = new ItemStack(VerityMod.getInventoryItemForFace(getFaceIndex(), getPhase()));
        ItemStack heldStack = player.getItemInHand(hand);
        if (heldStack.isEmpty()) {
            player.setItemInHand(hand, pickedStack);
        } else if (!player.getInventory().add(pickedStack)) {
            player.sendSystemMessage(Component.literal("Inventory full."));
            return InteractionResult.CONSUME;
        }

        this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                VerityMod.SOUND_ASKME, SoundSource.NEUTRAL, 0.8F, 1.0F);
        VerityMod.armHeldTalk(player, 40);
        this.discard();
        return InteractionResult.CONSUME;
    }

    // ── Damage ────────────────────────────────────────────────────────────
    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (!this.level().isClientSide && source.getEntity() instanceof Player player) {
            if (getPhase() == 1) {
                // Anger sounds
                this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                        VerityMod.SOUND_IM_SMILING_NOW, SoundSource.HOSTILE, 1.5F, 1.0F);
                player.sendSystemMessage(Component.literal("§c<Verity>§r ПОЧЕМУ ТЫ МЕНЯ УДАРИЛ?"));

                // Spawn the giant monster behind the player
                Vec3 rotationVec = player.getViewVector(1.0F).normalize();
                double spawnX = player.getX() - rotationVec.x * 3.0D;
                double spawnY = player.getY();
                double spawnZ = player.getZ() - rotationVec.z * 3.0D;

                VerityMonsterEntity monster = new VerityMonsterEntity(VerityMod.VERITY_MONSTER_ENTITY, this.level());
                monster.moveTo(spawnX, spawnY, spawnZ, player.getYRot(), player.getXRot());
                this.level().addFreshEntity(monster);

                // Discard the sphere
                this.discard();
                return true;
            }
        }
        return super.hurt(source, amount);
    }

    // ── Main Tick ─────────────────────────────────────────────────────────
    @Override
    public void tick() {
        if (this.introTicks > 0) {
            this.introTicks--;
            this.setDeltaMovement(Vec3.ZERO);
            this.hurtMarked = true;
            if (this.introTicks == 0) {
                this.setNoGravity(false);
                setFaceIndex(FACE_SMILE);
                this.isTalking = true;
                this.talkAnimTick = 40;
                this.chatCooldown = 600;

                this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                        VerityMod.SOUND_ASKME, SoundSource.NEUTRAL, 1.0F, 1.0F);

                Player player = this.level().getNearestPlayer(this, 32.0D);
                if (player != null) {
                    player.sendSystemMessage(Component.literal("§e<Verity>§r Привет! Я Верити, твой лучший друг!"));
                }
            }
            super.tick();
            this.updateRollAngle();
            return;
        }

        super.tick();
        this.updateRollAngle();

        if (this.level().isClientSide)
            return;

        int phase = getPhase();

        // ── Talking animation: blink face between shut and open ────────────
        if (isTalking) {
            talkAnimTick--;
            if (talkAnimTick <= 0) {
                // Finish speaking
                isTalking = false;
                setFaceIndex(phase == 1 ? FACE_SMILE : FACE_ABNORMAL_SHUT);
            } else {
                // Alternate open/shut every 4 ticks
                int currentFace = getFaceIndex();
                if (this.tickCount % 4 == 0) {
                    if (phase == 1) {
                        setFaceIndex(currentFace == FACE_SMILE ? FACE_SPEAK : FACE_SMILE);
                    } else {
                        setFaceIndex(currentFace == FACE_ABNORMAL_SHUT ? FACE_ABNORMAL_OPEN : FACE_ABNORMAL_SHUT);
                    }
                }
            }
        }

        // ── Chat & sound messaging ────────────────────────────────────────
        if (this.chatCooldown > 0) {
            this.chatCooldown--;
        } else {
            Player player = this.level().getNearestPlayer(this, 32.0D);
            if (player != null && player.isAlive()) {
                if (phase == 1) {
                    // Friendly phase messages
                    String message = FRIENDLY_MESSAGES[this.random.nextInt(FRIENDLY_MESSAGES.length)];
                    player.sendSystemMessage(Component.literal(message));
                    // Play ambient greeting sound
                    var sound = switch (this.random.nextInt(3)) {
                        case 0 -> VerityMod.SOUND_HELLO;
                        case 1 -> VerityMod.SOUND_ASKME;
                        default -> VerityMod.SOUND_WHOSTHERE;
                    };
                    this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                            sound, SoundSource.NEUTRAL, 0.8F, 1.0F);
                } else {
                    // Creepy phase messages
                    String message = CREEPY_MESSAGES[this.random.nextInt(CREEPY_MESSAGES.length)];
                    player.sendSystemMessage(Component.literal(message));
                    // Play a creepy sound
                    var sound = switch (this.random.nextInt(5)) {
                        case 0 -> VerityMod.SOUND_YOU_ARE_MINE;
                        case 1 -> VerityMod.SOUND_ITS_ALREADY_OVER;
                        case 2 -> VerityMod.SOUND_ALWAYS_LOOKED_LIKE_THIS;
                        case 3 -> VerityMod.SOUND_SOMETHING_HUNGRY;
                        default -> VerityMod.SOUND_SOMETHING_PASSED;
                    };
                    this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                            sound, SoundSource.HOSTILE, 1.0F, 1.0F);
                }
                // Start talking animation (lasts ~2 seconds)
                isTalking = true;
                talkAnimTick = 40;
                this.chatCooldown = 600 + this.random.nextInt(600); // 30–60 seconds
            }
        }

        // ── Creepy mode actions ───────────────────────────────────────────
        if (phase >= 2) {
            Player player = this.level().getNearestPlayer(this, 16.0D);
            if (player != null && player.isAlive()) {
                // Apply darkness effect (removed blindness so player can see)
                player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 80, 0, false, false, true));

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

            // Occasional ambient creepy sounds
            if (this.ambientSoundCooldown > 0) {
                this.ambientSoundCooldown--;
            } else if (this.random.nextInt(100) == 0) {
                var sound = switch (this.random.nextInt(3)) {
                    case 0 -> VerityMod.SOUND_SOMETHING_PASSED;
                    case 1 -> VerityMod.SOUND_VILLAGERS_GONE;
                    default -> VerityMod.SOUND_GONE;
                };
                this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                        sound, SoundSource.HOSTILE, 0.8F, 1.0F);
                this.ambientSoundCooldown = 200;
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────
    private boolean isPlayerLookingAtMe(Player player) {
        Vec3 rotationVec = player.getViewVector(1.0F).normalize();
        Vec3 toEntityVec = new Vec3(
                this.getX() - player.getX(),
                this.getEyeY() - player.getEyeY(),
                this.getZ() - player.getZ()).normalize();
        return rotationVec.dot(toEntityVec) > 0.5;
    }

    private void teleportBehindPlayer(Player player) {
        Vec3 rotationVec = player.getViewVector(1.0F).normalize();
        double targetX = player.getX() - rotationVec.x * 3.0D + (this.random.nextDouble() - 0.5D) * 1.5D;
        double targetY = player.getY();
        double targetZ = player.getZ() - rotationVec.z * 3.0D + (this.random.nextDouble() - 0.5D) * 1.5D;

        this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                VerityMod.SOUND_GONE, SoundSource.HOSTILE, 1.0F, 1.0F);
        this.teleportTo(targetX, targetY, targetZ);
        this.level().playSound(null, targetX, targetY, targetZ,
                VerityMod.SOUND_GONE, SoundSource.HOSTILE, 1.0F, 1.0F);
    }

    /**
     * Updates {@link #rollAngle} once per tick. Grows while the ball is
     * actually moving (mirrors the old limbSwing-based roll), and decays
     * back toward 0 when idle, so the face returns to the look direction
     * instead of staying stuck at whatever angle it last rolled to.
     */
    private void updateRollAngle() {
        Vec3 motion = this.getDeltaMovement();
        double horizontalSpeedSqr = motion.x * motion.x + motion.z * motion.z;
        boolean moving = horizontalSpeedSqr > 1.0E-4D;

        if (moving) {
            // Same source/formula the model used to read directly off limbSwing.
            this.rollAngle = this.walkAnimation.position() * 1.5F;
        } else {
            // Exponential decay back to 0 — tune 0.90F to taste:
            // closer to 1.0F = slower return, closer to 0.0F = snaps back faster.
            this.rollAngle *= 0.90F;
            if (Math.abs(this.rollAngle) < 0.01F) {
                this.rollAngle = 0.0F;
            }
        }
    }

    // ── Inner Goals ───────────────────────────────────────────────────────
    static class FollowPlayerGoal extends Goal {
        private final VerityEntity entity;
        private Player target;
        private final double speed;

        public FollowPlayerGoal(VerityEntity entity, double speed) {
            this.entity = entity;
            this.speed = speed;
            this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            this.target = this.entity.level().getNearestPlayer(this.entity, 32.0D);
            if (this.target == null)
                return false;
            return this.entity.distanceToSqr(this.target) > 16.0D;
        }

        @Override
        public boolean canContinueToUse() {
            return this.target != null && this.target.isAlive() && this.entity.distanceToSqr(this.target) > 4.0D;
        }

        @Override
        public void start() {
            this.entity.getNavigation().moveTo(this.target, this.speed);
        }

        @Override
        public void stop() {
            this.entity.getNavigation().stop();
            this.target = null;
        }

        @Override
        public void tick() {
            this.entity.getLookControl().setLookAt(this.target, 30.0F, 30.0F);
            if (this.entity.getRandom().nextInt(5) == 0) {
                this.entity.getNavigation().moveTo(this.target, this.speed);
            }
        }
    }
}