package net.verity.entity;

import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundSource;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
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
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.nbt.CompoundTag;
import net.verity.VerityMod;
import net.verity.config.VerityConfig;

import net.verity.ai.VerityDialogueController;

import java.util.EnumSet;
import java.util.List;

public class VerityEntity extends PathfinderMob {
    @Override
    public net.minecraft.world.entity.EntityDimensions getDefaultDimensions(net.minecraft.world.entity.Pose pose) {
        if (this.isMonsterForm() || this.getVerityPhase() == VerityPhase.MONSTER) {
            return net.minecraft.world.entity.EntityDimensions.scalable(1.2F, 3.2F);
        }
        return super.getDefaultDimensions(pose);
    }


    // в”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђ Р¤РђР—Р« FSM (Finite State Machine) в”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђ
    public enum VerityPhase {
        DORMANT,     // 0 вЂ” РІ РєРѕСЂРѕР±РєРµ, РЅРµ Р°РєС‚РёРІРёСЂРѕРІР°РЅ
        HELPER,      // 1 вЂ” РґСЂСѓР¶РµР»СЋР±РЅС‹Р№ РїРѕРјРѕС‰РЅРёРє
        OMNISCIENT,  // 2 вЂ” РІСЃРµР·РЅР°Р№РєР°, РІРѕРїСЂРѕСЃС‹ Рѕ СЂРµР°Р»СЊРЅРѕРј РјРёСЂРµ
        COUNTDOWN,   // 3 вЂ” РѕР±СЂР°С‚РЅС‹Р№ РѕС‚СЃС‡С‘С‚ "3 РґРЅСЏ", С‚РµР»РµРєРёРЅРµР·
        MONSTER,     // 4 вЂ” Monster Form, РїРѕРіРѕРЅСЏ
        POSSESSIVE,  // 5 вЂ” СЃРѕР±СЃС‚РІРµРЅРЅРёРє, "You have me"
        HUNTER, FINAL // 6 вЂ” СѓР±РёР№С†Р° Twixxel, СѓСЃС‚СЂР°РЅРµРЅРёРµ РґСЂСѓРіРёС… РёРіСЂРѕРєРѕРІ
    }

    // в”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђ DATA TRACKERS (СЃРёРЅС…СЂРѕРЅРёР·Р°С†РёСЏ СЃРµСЂРІРµСЂ в†” РєР»РёРµРЅС‚) в”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђ
    private static final EntityDataAccessor<Integer> VERITY_PHASE =
            SynchedEntityData.defineId(VerityEntity.class, EntityDataSerializers.INT);

    private static final EntityDataAccessor<Integer> FACE_INDEX =
            SynchedEntityData.defineId(VerityEntity.class, EntityDataSerializers.INT);

    private static final EntityDataAccessor<Boolean> FACELESS =
            SynchedEntityData.defineId(VerityEntity.class, EntityDataSerializers.BOOLEAN);

    private static final EntityDataAccessor<Boolean> IS_MONSTER_FORM =
            SynchedEntityData.defineId(VerityEntity.class, EntityDataSerializers.BOOLEAN);

    /** 0=normal, 1=suspended(sour face), 2=falling, 3=squash/bounce */
    private static final EntityDataAccessor<Integer> INTRO_PHASE =
            SynchedEntityData.defineId(VerityEntity.class, EntityDataSerializers.INT);

    // в”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђ Р“Р›РћР‘РђР›Р¬РќР«Р\u2122 РўР Р•РљР•Р  (Р±С‹СЃС‚СЂРµРµ, С‡РµРј СЃРєР°РЅРёСЂРѕРІР°С‚СЊ РІРµСЃСЊ РјРёСЂ) в”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђ
    /** РџРѕС‚РѕРєРѕР±РµР·РѕРїР°СЃРЅС‹Р№ РЅР°Р±РѕСЂ РІСЃРµС… Р¶РёРІС‹С… VerityEntity РЅР° СЃРµСЂРІРµСЂРµ. */
    public static final java.util.Set<VerityEntity> ACTIVE_VERITIES =
            java.util.Collections.synchronizedSet(
                    java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>()));

    // в”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђ РљРћРќРЎРўРђРќРўР« РЎРћРЎРўРћРЇРќРР\u2122 Р›РР¦Рђ в”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђ
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

    // в”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђ Р’РќРЈРўР Р•РќРќР•Р• РЎРћРЎРўРћРЇРќРР• (СЃРµСЂРІРµСЂРЅРѕРµ, РЅРµ СЃРёРЅС…СЂ.) в”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђ
    private int chatCooldown = 0;
    private int teleportCooldown = 0;
    private int talkAnimTick = 0;
    private int ambientSoundCooldown = 0;
    private int introTicks = 0;
    private int introSquashTimer = 0;    // С‚РёРєРё squash-СЌС„С„РµРєС‚Р° РїРѕСЃР»Рµ РїСЂРёР·РµРјР»РµРЅРёСЏ
    private int ticksInPhase = 0;
    private int dayCounter = 0;          // РґР»СЏ COUNTDOWN
    private float rollAngle = 0.0F;
    private float rollStrafe = 0.0F;     // Р±РѕРєРѕРІРѕР№ РЅР°РєР»РѕРЅ РїСЂРё РґРІРёР¶РµРЅРёРё РІР±РѕРє
    private boolean nearbyMessageSent = false; // В«Verity is nearbyВ» РїРµСЂРµРґ Monster Form
    private int rageForgiveTicks = 0;    // С‚РёРєРё СЏСЂРѕСЃС‚Рё РґРѕ РїСЂРѕС‰РµРЅРёСЏ
    private int villagerEatCooldown = 0; // РєСѓР»РґР°СѓРЅ РїСЂРѕРІРµСЂРєРё РґРµСЂРµРІРµРЅСЊ
    private boolean emptyVillageMessageSent = false;
    private boolean hunterChasing = false; // HUNTER: РїСЂРµСЃР»РµРґСѓРµС‚ С†РµР»СЊ (РґР»СЏ Р»РёС†Р°)
    private boolean stayHere = false;
    private boolean leading = false;      // РІРµРґС‘С‚ РёРіСЂРѕРєР° ("РїРѕС€Р»Рё Р·Р° РјРЅРѕР№")
    private BlockPos leadTarget = null;   // РєСѓРґР° РІРµРґС‘С‚
    private boolean roofTorn = false;     // РєСЂС‹С€Р° СѓР¶Рµ СЃРѕСЂРІР°РЅР° РІ СЌС‚РѕР№ MONSTER С„Р°Р·Рµ
    private int roofGravityTimer = 0;     // С‚Р°Р№РјРµСЂ РІРєР»СЋС‡РµРЅРёСЏ РіСЂР°РІРёС‚Р°С†РёРё РґР»СЏ РїР°РґР°СЋС‰РёС… Р±Р»РѕРєРѕРІ
    private double roofThrowX = 0;        // РЅР°РїСЂР°РІР»РµРЅРёРµ Р±СЂРѕСЃРєР° РєСЂС‹С€Рё
    private double roofThrowZ = 0;

    // ── Ball physics (throwable/kickable) ──
    private boolean thrown = false;
    private int thrownTicks = 0;
    private int bounceCount = 0;
    private VerityMonsterEntity activeMonster = null;

    // Triggers tracking fields
    private Vec3 lastPlayerPos = null;
    private int playerAfkTicks = 0;
    private int staringTicks = 0;
    private float lastPlayerHealth = -1.0F;
    private int forgiveProgressTicks = 0;

    // в”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђ LLM-Р˜РќРўР•Р“Р РђР¦Р˜РЇ в”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђ
    private VerityDialogueController dialogueController;

    public VerityDialogueController getDialogueController() {
        if (dialogueController == null) {
            dialogueController = new VerityDialogueController(this);
        }
        return dialogueController;
    }
    // в”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђ РљРћРќРЎРўР РЈРљРўРћР  в”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђ
    public VerityEntity(EntityType<? extends PathfinderMob> entityType, Level level) {
        super(entityType, level);
    }

    // в”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђ РђРўР РР‘РЈРўР« в”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђ
    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 100.0)
                .add(Attributes.MOVEMENT_SPEED, 0.25)
                .add(Attributes.FOLLOW_RANGE, 64.0)
                .add(Attributes.ATTACK_DAMAGE, 0.0);
    }

    // в”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђ Р Р•Р“РРЎРўР РђР¦РРЇ Р¦Р•Р›Р•Р\u2122 (Goals) в”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђ
    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new VerityLeadToVillageGoal(this));
        this.goalSelector.addGoal(1, new VerityFollowGoal(this, 1.2D));
        this.goalSelector.addGoal(2, new VerityWorkGoal(this));
        this.goalSelector.addGoal(3, new VerityStalkGoal(this, 0.8D));
        this.goalSelector.addGoal(3, new VerityMonsterAttackGoal(this, 1.5D));
        this.goalSelector.addGoal(2, new VerityKillVillageGoal(this));
        this.goalSelector.addGoal(4, new VerityOpenDoorGoal(this, 6.0D));
        this.goalSelector.addGoal(5, new LookAtPlayerGoal(this, Player.class, 16.0F, 1.0F));
    }

    // в”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђ РЎРРќРҐР РћРќРР—РђР¦РРЇ Р”РђРќРќР«РҐ в”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђ
    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(VERITY_PHASE, VerityPhase.HELPER.ordinal());
        builder.define(FACE_INDEX, FACE_SMILE);
        builder.define(FACELESS, false);
        builder.define(IS_MONSTER_FORM, false);
        builder.define(INTRO_PHASE, 0);
    }


    // в”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђ GETTERS / SETTERS в”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђ
        public boolean isStayHere() {
        return this.stayHere;
    }

    public void setStayHere(boolean v) {
        this.stayHere = v;
        if (v) {
            this.getNavigation().stop();
        }
    }

public VerityPhase getVerityPhase() {
        return VerityPhase.values()[this.entityData.get(VERITY_PHASE)];
    }

    public void setVerityPhase(VerityPhase phase) {
        VerityPhase old = getVerityPhase();
        this.entityData.set(VERITY_PHASE, phase.ordinal());
        this.ticksInPhase = 0;

        // Р›РѕРіРёРєР° РїСЂРё РІС…РѕРґРµ РІ С„Р°Р·Сѓ
        onPhaseEnter(old, phase);
    }

    private void onPhaseEnter(VerityPhase oldPhase, VerityPhase newPhase) {
        if (oldPhase == VerityPhase.MONSTER && newPhase != VerityPhase.MONSTER) {
            if (this.activeMonster != null) {
                if (!this.activeMonster.isRemoved()) {
                    this.activeMonster.discard();
                }
                this.activeMonster = null;
            }
        }

        switch (newPhase) {
            case MONSTER -> {
                setMonsterForm(true);
                this.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.35);
                this.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(20.0);
                this.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(64.0);
                if (!this.level().isClientSide) {
                    setFaceIndex(FACE_CREEPY_SMILE);
                    this.roofTorn = false;
                }
            }
            case HELPER -> {
                setMonsterForm(false);
                this.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.25);
                this.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(0.0);
                this.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(64.0);
                if (!this.level().isClientSide) setFaceIndex(FACE_SMILE);
            }
            case OMNISCIENT -> {
                if (!this.level().isClientSide) setFaceIndex(FACE_SERIOUS_1);
            }
            case COUNTDOWN -> {
                if (!this.level().isClientSide) setFaceIndex(FACE_BORED_P2);
            }
            case POSSESSIVE -> {
                setMonsterForm(false);
                this.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.25);
                this.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(0.0);
                this.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(64.0);
                if (!this.level().isClientSide) setFaceIndex(FACE_BORED_P2);
            }
            case HUNTER -> {
                setMonsterForm(true);
                this.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.4);
                this.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(20.0);
                if (!this.level().isClientSide) setFaceIndex(FACE_BORED_P2);
            }
        }
    }

    public boolean isMonsterForm() {
        return this.entityData.get(IS_MONSTER_FORM);
    }

    public void setMonsterForm(boolean v) {
        this.entityData.set(IS_MONSTER_FORM, v);
    }

    public int getFaceIndex() {
        return this.entityData.get(FACE_INDEX);
    }

    public void setFaceIndex(int face) {
        this.entityData.set(FACE_INDEX, face);
    }

    public float squashTimer = 0;
    public float squashAmount = 0;
    public int getRageForgiveTicks() { return this.rageForgiveTicks; }
    public float getSquashTimer() { return this.squashTimer; }
    public float getSquashAmount() { return this.squashAmount; }

    public boolean isFaceless() {
        return this.entityData.get(FACELESS);
    }

    public void setFaceless(boolean v) {
        this.entityData.set(FACELESS, v);
    }

    public int getDayCounter() {
        return this.dayCounter;
    }

    public boolean isLeading() { return this.leading; }
    public void setLeading(boolean v) { this.leading = v; }
    public BlockPos getLeadTarget() { return this.leadTarget; }
    public void setLeadTarget(BlockPos pos) { this.leadTarget = pos; }

    /** РџСѓР±Р»РёС‡РЅС‹Р№ РјРµС‚РѕРґ РґР»СЏ РєРѕРјР°РЅРґС‹ /verity roof */
    public void tearOffRoofPublic(Player player) {
        tearOffRoof(player);
    }

    /** РџСѓР±Р»РёС‡РЅС‹Р№ РјРµС‚РѕРґ РґР»СЏ РєРѕРјР°РЅРґС‹ /verity day */
    public void setDayCounterPublic(int day) {
        this.dayCounter = day;
    }

    /**
     * Р”РµС„РѕР»С‚РЅРѕРµ Р»РёС†Рѕ РґР»СЏ С„Р°Р·С‹ (РєРѕРіРґР° РЅРµ РіРѕРІРѕСЂРёС‚).
     * COUNTDOWN Р·Р°РІРёСЃРёС‚ РѕС‚ РґРЅСЏ РѕС‚СЃС‡С‘С‚Р°.
     */
    public int getDefaultFaceForPhase(VerityPhase phase) {
        return switch (phase) {
            case HELPER       -> FACE_SMILE;
            case OMNISCIENT   -> FACE_SERIOUS_1;
            case COUNTDOWN    -> switch (this.dayCounter) {
                case 0  -> FACE_BORED_P2;
                case 1  -> FACE_ABNORMAL_SHUT;
                default -> FACE_DAY2_SHUT;
            };
            case MONSTER      -> FACE_CREEPY_SMILE;
            case POSSESSIVE   -> FACE_BORED_P2;
            case HUNTER       -> this.hunterChasing ? FACE_SERIOUS_3 : FACE_BORED_P2;
            default           -> FACE_SMILE;
        };
    }

    /**
     * РџР°СЂР° Р»РёС† РґР»СЏ Р°РЅРёРјР°С†РёРё СЂР°Р·РіРѕРІРѕСЂР° РїРѕ С„Р°Р·Рµ.
     * [0] = РѕСЃРЅРѕРІРЅРѕРµ, [1] = Р°Р»СЊС‚РµСЂРЅР°С‚РёРІРЅРѕРµ (РіРѕРІРѕСЂРёС‚).
     */
    public int[] getTalkPairForPhase(VerityPhase phase) {
        return switch (phase) {
            case HELPER       -> new int[]{FACE_SMILE, FACE_SPEAK};
            case OMNISCIENT   -> new int[]{FACE_SERIOUS_1, FACE_SERIOUS_2};
            case COUNTDOWN    -> switch (this.dayCounter) {
                case 0  -> new int[]{FACE_BORED_P2, FACE_SERIOUS_2};
                case 1  -> new int[]{FACE_ABNORMAL_SHUT, FACE_ABNORMAL_OPEN};
                default -> new int[]{FACE_DAY2_SHUT, FACE_DAY2_OPEN};
            };
            case MONSTER      -> new int[]{FACE_CREEPY_SMILE, FACE_DAY2_OPEN};
            case POSSESSIVE   -> new int[]{FACE_BORED_P2, FACE_SPEAK};
            case HUNTER       -> this.hunterChasing
                    ? new int[]{FACE_SERIOUS_3, FACE_CREEPY_SMILE}
                    : new int[]{FACE_BORED_P2, FACE_SPEAK};
            default           -> new int[]{FACE_SMILE, FACE_SPEAK};
        };
    }

    // РЎРѕРІРјРµСЃС‚РёРјРѕСЃС‚СЊ СЃРѕ СЃС‚Р°СЂС‹Рј РєРѕРґРѕРј (VerityMod.getInventoryItemForFace)
    public int getPhase() {
        VerityPhase p = getVerityPhase();
        return switch (p) {
            case MONSTER, HUNTER -> 2;
            default -> 1;
        };
    }

    public boolean isCreepy() {
        return getVerityPhase().ordinal() >= VerityPhase.COUNTDOWN.ordinal();
    }

    public void setIntroTicks(int ticks) {
        this.introTicks = ticks;
    }

    public boolean isTalking() {
        int face = getFaceIndex();
        return face == FACE_SPEAK || face == FACE_ABNORMAL_OPEN;
    }

    public float getRollAngle() {
        return this.rollAngle;
    }

    public float getRollStrafe() {
        return this.rollStrafe;
    }

    /** 0=normal, 1=suspended, 2=falling, 3=squash */
    public int getIntroPhase() {
        return this.entityData.get(INTRO_PHASE);
    }

    public int getIntroSquashTimer() {
        return this.introSquashTimer;
    }

    public void setTalkAnimTick(int ticks) {
        this.talkAnimTick = ticks;
    }

    // в”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђ Р’Р—РђРРњРћР”Р•Р\u2122РЎРўР’РР• (РџРљРњ) в”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђ
    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (this.level().isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (this.isFaceless()) {
            return InteractionResult.PASS;
        }
        if (getVerityPhase() == VerityPhase.MONSTER || getVerityPhase() == VerityPhase.HUNTER) {
            // Р’ С„Р°Р·Рµ РјРѕРЅСЃС‚СЂР° РЅРµР»СЊР·СЏ РІР·СЏС‚СЊ РІ РёРЅРІРµРЅС‚Р°СЂСЊ
            player.sendSystemMessage(Component.literal("\u00a7c<Verity>\u00a7r \u041d\u0415\u0422."));
            return InteractionResult.FAIL;
        }

        // Р”Р°С‘Рј РёРЅРІРµРЅС‚Р°СЂРЅС‹Р№ РїСЂРµРґРјРµС‚ СЃ С‚РµРєСѓС‰РёРј Р»РёС†РѕРј
        int faceIdx = getFaceIndex();
        int phaseVal = getPhase();
        ItemStack pickedStack = new ItemStack(VerityMod.getInventoryItemForFace(faceIdx, phaseVal));
        ItemStack heldStack = player.getItemInHand(hand);
        if (heldStack.isEmpty()) {
            player.setItemInHand(hand, pickedStack);
        } else if (!player.getInventory().add(pickedStack)) {
            player.sendSystemMessage(Component.literal("Inventory full."));
            return InteractionResult.CONSUME;
        }

        VerityMod.armHeldTalk(player, 40);

        // РЎРѕС…СЂР°РЅСЏРµРј РґР°РЅРЅС‹Рµ Verity РїРµСЂРµРґ discard
        VerityMod.saveHeldData(getVerityPhase(), faceIdx, getDialogueController().getDialogueHistory(), getDialogueController().getKnownFactsList());

        this.discard();
        return InteractionResult.CONSUME;
    }

    // ── Ball physics methods ──

    /**
     * Throw Verity in the direction the player is looking.
     */
    public void throwVerity(Player player) {
        Vec3 lookDir = player.getLookAngle();
        double power = 1.65D;
        double upBoost = 0.65D;
        this.setDeltaMovement(lookDir.x * power, lookDir.y * power + upBoost, lookDir.z * power);
        this.hurtMarked = true;
        this.thrown = true;
        this.thrownTicks = 0;
        this.bounceCount = 0;
        this.setNoGravity(false);
        this.getNavigation().stop();
        this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                SoundEvents.SLIME_ATTACK, SoundSource.NEUTRAL, 0.8F, 1.2F);
        if (getVerityPhase() == VerityPhase.HELPER || getVerityPhase() == VerityPhase.OMNISCIENT) {
            setFaceIndex(FACE_HURT);
            this.talkAnimTick = 15;
        }
    }

    /**
     * Kick Verity — launch in the direction the player is facing.
     */
    public void kickVerity(Player player) {
        Vec3 lookDir = player.getLookAngle();
        double power = 1.5D;
        double upBoost = 0.60D;
        this.setDeltaMovement(lookDir.x * power, lookDir.y * power + upBoost, lookDir.z * power);
        this.hurtMarked = true;
        this.thrown = true;
        this.thrownTicks = 0;
        this.bounceCount = 0;
        this.setNoGravity(false);
        this.getNavigation().stop();
        this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                SoundEvents.SLIME_ATTACK, SoundSource.NEUTRAL, 0.6F, 1.5F);
        setFaceIndex(FACE_HURT);
        this.talkAnimTick = 15;
    }

    public boolean isThrown() {
        return this.thrown;
    }

    @Override
    public boolean causeFallDamage(float fallDistance, float multiplier, DamageSource source) {
        if (this.thrown) return false;
        return super.causeFallDamage(fallDistance, multiplier, source);
    }

    @Override
    public boolean isPickable() {
        return true;
    }

    /**
     * Ball physics — bouncing, rolling, settling.
     */
    private void tickBallPhysics(Vec3 preMoveVel) {
        this.thrownTicks++;
        Vec3 vel = this.getDeltaMovement();

        if (this.squashTimer > 0) {
            this.squashTimer -= 0.15f;
            if (this.squashTimer < 0) this.squashTimer = 0;
        }

        double preY = preMoveVel.y;
        double preX = preMoveVel.x;
        double preZ = preMoveVel.z;

        if (this.onGround() && preY < -0.06D) {
            this.bounceCount++;
            if (this.bounceCount >= 4 || Math.abs(preY) < 0.05D) {
                // Smooth settle after elastic bouncing chain
                this.setDeltaMovement(preX * 0.75D, 0.0D, preZ * 0.75D);
                this.thrown = false;
                this.bounceCount = 0;
                this.hurtMarked = true;
                this.squashTimer = 0.5f;
                this.squashAmount = 0.2F;
                this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                        SoundEvents.WOOD_PLACE, SoundSource.NEUTRAL, 0.5F, 1.2F);
            } else {
                // Elastic bouncy ball rebounds: 1st=0.78x, 2nd=0.62x, 3rd=0.48x
                double restitution = switch (this.bounceCount) {
                    case 1 -> 0.78D;
                    case 2 -> 0.62D;
                    default -> 0.48D;
                };
                double bounceY = -preY * restitution;
                double friction = 0.85D;
                this.setDeltaMovement(preX * friction, bounceY, preZ * friction);
                this.hurtMarked = true;
                this.squashTimer = 1.0f;
                this.squashAmount = (float) Math.min(0.75f, Math.abs(preY) * 0.85f);

                float pitch = 1.0F + this.bounceCount * 0.15F;
                this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                        SoundEvents.SLIME_JUMP, SoundSource.NEUTRAL,
                        0.8F, Math.min(pitch, 1.8F));
                if (this.bounceCount == 1 && (getVerityPhase() == VerityPhase.HELPER || getVerityPhase() == VerityPhase.OMNISCIENT)) {
                    setFaceIndex(FACE_HURT);
                    this.talkAnimTick = 12;
                }
            }
        } else if (this.onGround()) {
            double rollFriction = 0.88D;
            this.setDeltaMovement(vel.x * rollFriction, 0.0D, vel.z * rollFriction);
            this.hurtMarked = true;
        }

        if (this.horizontalCollision) {
            double absX = Math.abs(preX);
            double absZ = Math.abs(preZ);
            double bounceX = (absX > 0.05) ? -preX * 0.85D : vel.x;
            double bounceZ = (absZ > 0.05) ? -preZ * 0.85D : vel.z;
            this.setDeltaMovement(bounceX, vel.y > 0 ? vel.y : 0.20D, bounceZ);
            this.hurtMarked = true;
            this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                    SoundEvents.WOOD_HIT, SoundSource.NEUTRAL, 0.5F, 1.4F);
        }

        if (this.thrownTicks < 50 && preMoveVel.lengthSqr() > 0.15) {
            List<Entity> hitEntities = this.level().getEntities(this, this.getBoundingBox().inflate(0.3));
            for (Entity entity : hitEntities) {
                if (entity instanceof Player && entity != this.level().getNearestPlayer(this, 64)) {
                    Vec3 push = preMoveVel.normalize().scale(0.5);
                    entity.push(push.x, 0.35, push.z);
                    this.level().playSound(null, entity.getX(), entity.getY(), entity.getZ(),
                            SoundEvents.SLIME_ATTACK, SoundSource.NEUTRAL, 0.7F, 1.0F);
                }
            }
        }

        double speedSqr = vel.x * vel.x + vel.z * vel.z;
        if ((this.onGround() && Math.abs(preY) < 0.22D && speedSqr < 0.005D) || this.thrownTicks > 200) {
            this.thrown = false;
            this.thrownTicks = 0;
            this.bounceCount = 0;
            this.setDeltaMovement(Vec3.ZERO);
            this.hurtMarked = true;
            setFaceIndex(getDefaultFaceForPhase(getVerityPhase()));
            this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                    SoundEvents.WOOD_PLACE, SoundSource.NEUTRAL, 0.4F, 1.2F);
        }
    }

    // ── УРОН ──
    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (!this.level().isClientSide) {
            VerityPhase phase = getVerityPhase();

            // РРјРјСѓРЅРёС‚РµС‚ РІ Monster Form
            if (phase == VerityPhase.MONSTER || phase == VerityPhase.HUNTER) {
                return false;
            }

            // Left-click by player = kick Verity like a ball
            if (source.getEntity() instanceof Player player && !source.is(net.minecraft.tags.DamageTypeTags.IS_FIRE)) {
                kickVerity(player);
                return false;
            }

            // Fire/lava = rage incident (canonical "DON'T DO THAT!")
            if (source.is(net.minecraft.tags.DamageTypeTags.IS_FIRE)) {
                Player nearest = this.level().getNearestPlayer(this, 32.0D);
                // Fire damage is applied every tick while Verity is burning.
                // Treat that whole burn as one emotional incident, otherwise
                // the two canonical lines flood the chat once per tick.
                if (nearest != null && this.rageForgiveTicks == 0) {
                    Vec3 rotVec = nearest.getViewVector(1.0F).normalize();
                    double tx = nearest.getX() - rotVec.x * 2.0;
                    double ty = nearest.getY();
                    double tz = nearest.getZ() - rotVec.z * 2.0;
                    this.teleportTo(tx, ty, tz);
                    nearest.sendSystemMessage(Component.literal(
                            "\u00A74<Verity\u2122>\u00A7r \u041D\u0415 \u0414\u0415\u041B\u0410\u0419 \u042D\u0422\u041E\u0413\u041E!"));
                    nearest.sendSystemMessage(Component.literal(
                            "\u00A74<Verity\u2122>\u00A7r \u042F \u0414\u0423\u041C\u0410\u041B, \u041C\u042B \u0425\u041E\u0420\u041E\u0428\u041E \u0413\u0423\u041B\u042F\u041B\u0418! \u0420\u0410\u0417\u0412\u0415 \u041C\u042B \u041D\u0415 \u0425\u041E\u0420\u041E\u0428\u041E \u0413\u0423\u041B\u042F\u041B\u0418?"));
                }
                this.rageForgiveTicks = Math.max(this.rageForgiveTicks, 3600);
                return false;
            }

            // Immune to ALL other damage (cactus, fall, drown, suffocation, explosions, etc.)
            // Verity is indestructible — only fire/lava triggers rage, everything else is ignored
            return false;
        }
        return false;
    }

    private VerityMonsterEntity spawnMonsterBehind(Player player) {
        Vec3 rotationVec = player.getViewVector(1.0F).normalize();
        double spawnX = player.getX() - rotationVec.x * 3.0D;
        double spawnY = player.getY();
        double spawnZ = player.getZ() - rotationVec.z * 3.0D;

        VerityMonsterEntity monster = new VerityMonsterEntity(VerityMod.VERITY_MONSTER_ENTITY, this.level());
        monster.moveTo(spawnX, spawnY, spawnZ, player.getYRot(), player.getXRot());
        monster.setLinkedVerity(this);
        this.level().addFreshEntity(monster);
        return monster;
    }

    // в”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђ РћРЎРќРћР’РќРћР\u2122 РўРРљ (FSM + РїРѕРІРµРґРµРЅРёСЏ) в”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђ
    @Override
    public void tick() {
        // в”Ђв”Ђ РђРќРРњРђР¦РРЇ РџРћРЇР’Р›Р•РќРРЇ (suspend в†’ drop в†’ bounce) в”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђ
        if (this.introTicks > 0) {
            this.introTicks--;

            // squash-С‚Р°Р№РјРµСЂ С‚РёРєР°РµС‚ РЅРµР·Р°РІРёСЃРёРјРѕ
            if (this.introSquashTimer > 0) this.introSquashTimer--;

            // Р¤РђР—Рђ 1 (ticks 60в†’40): РІРёСЃРёРј РІ РІРѕР·РґСѓС…Рµ, РєРёСЃР»РѕРµ Р»РёС†Рѕ
            if (this.introTicks > 40) {
                this.setNoGravity(true);
                this.setDeltaMovement(Vec3.ZERO);
                this.hurtMarked = true;
                if (!this.level().isClientSide) {
                    setFaceIndex(FACE_HURT);
                    this.entityData.set(INTRO_PHASE, 1);
                }

            // Р¤РђР—Рђ 2 (ticks 40в†’0): РѕС‚РїСѓСЃРєР°РµРј, РїР°РґР°РµРј
            } else if (this.introTicks == 40) {
                this.setNoGravity(false);
                if (!this.level().isClientSide) {
                    this.entityData.set(INTRO_PHASE, 2);
                }

            // Р¤РђР—Рђ 3 (РїР°РґР°РµРј РІРЅРёР·, РёС‰РµРј РїСЂРёР·РµРјР»РµРЅРёРµ РґР»СЏ РѕС‚СЃРєРѕРєР°)
            } else if (this.introTicks > 0) {
                Vec3 vel = this.getDeltaMovement();
                if (this.onGround() && vel.y < -0.05) {
                    // РћС‚СЃРєРѕРє
                    this.setDeltaMovement(vel.x * 0.3, -vel.y * 0.45, vel.z * 0.3);
                    if (!this.level().isClientSide) {
                        this.introSquashTimer = 8;
                        this.entityData.set(INTRO_PHASE, 3);
                    }
                }

            // introTicks == 0: Р°РЅРёРјР°С†РёСЏ Р·Р°РІРµСЂС€РµРЅР°, РїСЂРёРІРµС‚СЃС‚РІРёРµ
            } else {
                this.setNoGravity(false);
                if (!this.level().isClientSide) {
                    this.entityData.set(INTRO_PHASE, 0);
                    setFaceIndex(FACE_SMILE);
                    this.talkAnimTick = 40;
                    this.chatCooldown = 600;
                    Player player = this.level().getNearestPlayer(this, 32.0D);
                    if (player != null) {
                        String introLine = "\u00a7e<Verity\u2122>\u00a7r \u041f\u0440\u0438\u0432\u0435\u0442! \u042f \u0412\u0435\u0440\u0438\u0442\u0438, \u0442\u0432\u043e\u0439 \u043b\u0438\u0447\u043d\u044b\u0439 \u043f\u043e\u043c\u043e\u0449\u043d\u0438\u043a-\u0434\u0440\u0443\u0433. \u0421\u043f\u0440\u0430\u0448\u0438\u0432\u0430\u0439 \u0447\u0442\u043e \u0443\u0433\u043e\u0434\u043d\u043e \u2014 \u044f \u0437\u043d\u0430\u044e \u0432\u0441\u0451.";
                        player.sendSystemMessage(Component.literal(introLine));
                        if (player instanceof net.minecraft.server.level.ServerPlayer sp) {
                            net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(sp,
                                    new net.verity.net.TTSPayload(introLine));
                        }
                    }
                    setVerityPhase(VerityPhase.HELPER);
                }
            }

            super.tick();
            this.updateRollAngle();
            return;
        }

        Vec3 preMoveVel = this.getDeltaMovement();
        super.tick();
        this.updateRollAngle();

        if (this.level().isClientSide) return;

        // ── Ball physics: when thrown, skip AI and handle bouncing/rolling ──
        if (this.thrown) {
            if (this.talkAnimTick > 0) {
                this.talkAnimTick--;
                if (this.talkAnimTick <= 0) {
                    setFaceIndex(getDefaultFaceForPhase(getVerityPhase()));
                }
            }
            tickBallPhysics(preMoveVel);
            return;
        }

        // Р РµРіРёСЃС‚СЂРёСЂСѓРµРј СЃРµР±СЏ РІ РіР»РѕР±Р°Р»СЊРЅРѕРј С‚СЂРµРєРµСЂРµ (O(1) РѕРїРµСЂР°С†РёСЏ)
        ACTIVE_VERITIES.add(this);

        VerityPhase phase = getVerityPhase();
        this.ticksInPhase++;

        // в”Ђв”Ђ РџСЂРѕС‰РµРЅРёРµ РїРѕСЃР»Рµ СЏСЂРѕСЃС‚Рё (Р»Р°РІРѕРІС‹Р№ РёРЅС†РёРґРµРЅС‚) в”Ђв”Ђ
        if (this.rageForgiveTicks > 0) {
            this.rageForgiveTicks--;
            if (this.rageForgiveTicks == 0) {
                Player nearest = this.level().getNearestPlayer(this, 32.0D);
                if (nearest != null) {
                    nearest.sendSystemMessage(Component.literal(
                            "\u00a7e<Verity\u2122>\u00a7r ...\u0412\u0441\u0451 \u0445\u043e\u0440\u043e\u0448\u043e. \u042f \u043f\u0440\u043e\u0449\u0430\u044e \u0442\u0435\u0431\u044f. \u042f \u0437\u043d\u0430\u044e, \u044d\u0442\u043e \u0431\u044b\u043b\u043e \u0441\u043b\u0443\u0447\u0430\u0439\u043d\u043e."));
                }
            }
        }

        // в”Ђв”Ђ РђРЅРёРјР°С†РёСЏ СЂР°Р·РіРѕРІРѕСЂР° в”Ђв”Ђ
        if (this.talkAnimTick > 0) {
            this.talkAnimTick--;
            if (this.talkAnimTick <= 0) {
                // РљРѕРЅРµС† СЂР°Р·РіРѕРІРѕСЂР° вЂ” РІРѕР·РІСЂР°С‰Р°РµРј РґРµС„РѕР»С‚РЅРѕРµ Р»РёС†Рѕ РїРѕ С„Р°Р·Рµ
                setFaceIndex(getDefaultFaceForPhase(phase));
            } else {
                int currentFace = getFaceIndex();
                if (this.tickCount % 4 == 0) {
                    // РџР°СЂР° Р»РёС† РґР»СЏ Р°РЅРёРјР°С†РёРё СЂР°Р·РіРѕРІРѕСЂР° РїРѕ С„Р°Р·Рµ
                    int[] pair = getTalkPairForPhase(phase);
                    setFaceIndex(currentFace == pair[0] ? pair[1] : pair[0]);
                }
            }
        }

        // в”Ђв”Ђ РўР°Р№РјРµСЂ РіСЂР°РІРёС‚Р°С†РёРё РґР»СЏ СЃРѕСЂРІР°РЅРЅРѕР№ РєСЂС‹С€Рё в”Ђв”Ђ
        if (this.roofGravityTimer > 0) {
            this.roofGravityTimer--;
            if (this.roofGravityTimer == 0) {
                // Р’РєР»СЋС‡Р°РµРј РіСЂР°РІРёС‚Р°С†РёСЋ вЂ” РєСЂС‹С€Р° РїР°РґР°РµС‚ СЂСЏРґРѕРј РєР°Рє РµРґРёРЅС‹Р№ РєСѓСЃРѕРє
                var fallingBlocks = this.level().getEntitiesOfClass(
                        net.minecraft.world.entity.item.FallingBlockEntity.class,
                        this.getBoundingBox().inflate(30.0D));
                for (var fb : fallingBlocks) {
                    if (fb.isNoGravity()) {
                        fb.setNoGravity(false);
                        // РЎРёР»СЊРЅС‹Р№ С‚РѕР»С‡РѕРє РІР±РѕРє + РІРЅРёР· вЂ” РєСЂС‹С€Р° Р»РµС‚РёС‚ Рё РїР°РґР°РµС‚
                        fb.setDeltaMovement(this.roofThrowX * 3.0, 0.2, this.roofThrowZ * 3.0);
                        fb.hurtMarked = true;
                    }
                }
            }
        }

        // в”Ђв”Ђ РўРµР»РµРїРѕСЂС‚ "Рћ, СЏ С‚СѓС‚" вЂ” РґРѕРіРѕРЅСЏРµС‚ РёРіСЂРѕРєР° РµСЃР»Рё СѓР±РµР¶Р°Р» в”Ђв”Ђ
        if (phase != VerityPhase.DORMANT && phase != VerityPhase.MONSTER && !this.thrown) {
            tickCatchUpTeleport();
        }

        // в”Ђв”Ђ РљРѕРЅС‚РµРєСЃС‚РЅС‹Рµ Р°РІС‚Рѕ-СЂРµРїР»РёРєРё вЂ” С‚РѕР»СЊРєРѕ РїРѕ СЃРѕР±С‹С‚РёСЏРј, РЅРµ РїРѕ С‚Р°Р№РјРµСЂСѓ в”Ђв”Ђ
        if (phase != VerityPhase.DORMANT) {
            handleAutoDialogue();
        }

        // в”Ђв”Ђ Р’РµРґРµРЅРёРµ РёРіСЂРѕРєР° ("РїРѕС€Р»Рё Р·Р° РјРЅРѕР№") в”Ђв”Ђ
        if (this.leading) {
            tickLeading();
        }

        // ── Хоррор-эффекты Verity (шар сам по себе жуткий) ──
        if (phase != VerityPhase.DORMANT && phase != VerityPhase.HELPER) {
            Player horrorTarget = this.level().getNearestPlayer(this, 64.0D);
            if (horrorTarget != null) {
                tickHorror(horrorTarget, phase);
            }
        }

        // ── FSM: логика каждой фазы ──
        switch (phase) {
            case HELPER -> tickHelper();
            case OMNISCIENT -> tickOmniscient();
            case COUNTDOWN -> tickCountdown();
            case MONSTER -> tickMonster();
            case POSSESSIVE -> tickPossessive();
            case HUNTER -> tickHunter();
        }
    }

    /**
     * Хоррор-эффекты Verity — шар сам по себе жуткий, без монстра.
     */
    private int horrorCooldown = 0;
    private int faceGlitchTimer = 0;
    private int stareTimer = 0;
    private int originalFaceBeforeGlitch = 0;

    private void tickHorror(Player player, VerityPhase phase) {
        if (this.horrorCooldown > 0) {
            this.horrorCooldown--;
        }

        // ── 1. Глитч лица — на мгновение жуткое лицо, потом обратно ──
        if (this.faceGlitchTimer > 0) {
            this.faceGlitchTimer--;
            if (this.faceGlitchTimer == 0) {
                // Вернуть оригинальное лицо
                setFaceIndex(this.originalFaceBeforeGlitch);
            }
        } else if (this.random.nextInt(600) == 0 && this.talkAnimTick <= 0) {
            // Случайный глитч — 1 раз в ~30 сек
            this.originalFaceBeforeGlitch = getFaceIndex();
            int[] creepyFaces = {FACE_ABNORMAL_SHUT, FACE_ABNORMAL_OPEN, FACE_CREEPY_SMILE, FACE_SERIOUS_3};
            setFaceIndex(creepyFaces[this.random.nextInt(creepyFaces.length)]);
            this.faceGlitchTimer = 3 + this.random.nextInt(5); // 3-8 тиков
        }

        // ── 2. Импульсы тьмы — brief Darkness когда рядом (OMNISCIENT+) ──
        if (this.horrorCooldown == 0 && this.distanceToSqr(player) < 100.0D) {
            if (this.random.nextInt(400) == 0) { // ~1 раз в 20 сек
                player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 30, 0, false, false, false));
                this.horrorCooldown = 200; // 10 сек кулдаун
            }
        }

        // ── 3. Слабость вблизи — когда подошёл вплотную (OMNISCIENT+) ──
        if (this.distanceToSqr(player) < 9.0D && this.random.nextInt(200) == 0) {
            // Очень короткая тьма — дискомфорт
            player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 20, 0, false, false, false));
        }

        // ── 4. Криповые шёпоты — случайные сообщения без LLM ──
        if (this.horrorCooldown == 0 && this.random.nextInt(1200) == 0) {
            String[] whispers = {
                    "\u00A77...\u0442\u044B \u043E\u0434\u0438\u043D?",
                    "\u00A77...\u044F \u0441\u043B\u044B\u0448\u0443 \u0442\u0435\u0431\u044F.",
                    "\u00A77...\u043D\u0435 \u0443\u0445\u043E\u0434\u0438.",
                    "\u00A77...\u044F \u0437\u043D\u0430\u044E.",
                    "\u00A77...\u0441\u043A\u043E\u0440\u043E.",
                    "\u00A77...\u0442\u0440\u0438.",
                    "\u00A77...\u0442\u044B \u0435\u043B \u043F\u0438\u0446\u0446\u0443?",
                    "\u00A77...\u044F \u0432\u0441\u0435\u0433\u0434\u0430 \u0437\u0434\u0435\u0441\u044C.",
                    "\u00A77...\u0442\u0435\u0431\u0435 \u043D\u0435 \u0445\u043E\u043B\u043E\u0434\u043D\u043E?",
                    "\u00A77...\u044F \u0432\u0438\u0436\u0443 \u0442\u0435\u0431\u044F."
            };
            player.sendSystemMessage(Component.literal(whispers[this.random.nextInt(whispers.length)]));
            this.horrorCooldown = 600; // 30 сек кулдаун
        }

        // ── 5. Двери открываются сами (OMNISCIENT+, не только COUNTDOWN) ──
        if (phase != VerityPhase.COUNTDOWN && this.random.nextInt(200) == 0) {
            BlockPos pos = this.blockPosition();
            for (int dx = -4; dx <= 4; dx++) {
                for (int dz = -4; dz <= 4; dz++) {
                    for (int dy = -1; dy <= 2; dy++) {
                        BlockPos doorPos = pos.offset(dx, dy, dz);
                        if (!this.level().hasChunkAt(doorPos)) continue;
                        if (this.level().getBlockState(doorPos).getBlock() instanceof net.minecraft.world.level.block.DoorBlock) {
                            this.level().blockEvent(doorPos, this.level().getBlockState(doorPos).getBlock(), 1, 1);
                            break;
                        }
                    }
                }
            }
        }

        // ── 6. My Gal играет случайно ночью (OMNISCIENT+) ──
        long time = this.level().getDayTime() % 24000;
        if (time > 13000 && time < 18000 && this.horrorCooldown == 0 && this.random.nextInt(2400) == 0) {
            this.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                    VerityMod.SOUND_MYGAL_NORMAL, SoundSource.RECORDS, 0.4F, 0.8F);
            player.sendSystemMessage(Component.literal("\u00A77\u266A ..."));
            this.horrorCooldown = 1200; // 60 сек кулдаун
        }

        // ── 7. Замирание и пристальный взгляд — перестаёт следовать и смотрит ──
        if (this.stareTimer > 0) {
            this.stareTimer--;
            this.getNavigation().stop();
            this.getLookControl().setLookAt(player, 180.0F, 180.0F);
        } else if (this.random.nextInt(800) == 0 && this.distanceToSqr(player) < 400.0D) {
            // Начать пристальный взгляд на 5-10 сек
            this.stareTimer = 100 + this.random.nextInt(100);
        }

        // ── 8. Verity появляется в доме после сна ──
        if (player instanceof net.minecraft.server.level.ServerPlayer sp) {
            // Проверяем — только что проснулся? (sleepTimer сбросился)
            if (sp.isSleepingLongEnough() == false && sp.getSleepTimer() == 0
                    && this.level().getDayTime() % 24000 < 100 && this.level().getDayTime() % 24000 > 0
                    && this.random.nextInt(3) == 0) {
                // Teleport рядом с кроватью
                var bedPos = sp.getRespawnPosition();
                if (bedPos != null && this.distanceToSqr(bedPos.getX(), bedPos.getY(), bedPos.getZ()) > 100.0D) {
                    this.teleportTo(bedPos.getX() + 1.5, bedPos.getY(), bedPos.getZ() + 1.5);
                    player.sendSystemMessage(Component.literal("\u00a77..."));
                    player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 60, 0, false, false, true));
                }
            }
        }
    }

    private void tickMonster() {
        // MONSTER FORM: Verity сидит и не двигается. Лор: он не преследует — он ждёт.
        // Игрок должен прийти к нему. Или заблокирован в доме без крыши.

        Player nearest = this.level().getNearestPlayer(this, 64.0D);

        // Отправляем «Verity is nearby» при первом входе в фазу и спавним монстра
        if (!this.nearbyMessageSent) {
            this.nearbyMessageSent = true;
            if (nearest != null) {
                nearest.sendSystemMessage(Component.literal("\u00a74\u0412\u0435\u0440\u0438\u0442\u0438 \u0440\u044f\u0434\u043e\u043c..."));
                this.activeMonster = spawnMonsterBehind(nearest);
            }
        }

        // Останавливаем навигацию — Verity-шар не двигается
        this.getNavigation().stop();

        if (nearest != null && nearest.isAlive()) {
            this.getLookControl().setLookAt(nearest, 30.0F, 30.0F);

            // Тьма на игрока если близко к шару или активен монстр
            double distSq = this.distanceToSqr(nearest);
            boolean monsterActive = this.activeMonster != null && !this.activeMonster.isRemoved();
            if (distSq < 400.0D || monsterActive) {
                nearest.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 100, 0, false, false, true));
            }

            // Срыв крыши — один раз, когда игрок рядом
            if (!this.roofTorn && distSq < 100.0D && this.ticksInPhase > 60) {
                tearOffRoof(nearest);
                this.roofTorn = true;
            }

            // Безжалостное преследование — никакого авто-прощения при нажатии Shift
            if (distSq < 16.0D && this.getRandom().nextInt(100) == 0) {
                nearest.sendSystemMessage(Component.literal(
                        "\u00a74<Verity>\u00a7r Прощения не будет, " + nearest.getName().getString() + "..."));
            }
        }
    }

    private void tickHelper() {
        // Переход в OMNISCIENT через 2 минуты (2400 тиков)
        if (this.ticksInPhase > 2400) {
            Player nearest = this.level().getNearestPlayer(this, 32.0D);
            if (nearest != null) {
                nearest.sendSystemMessage(Component.literal("\u00a7e<Verity>\u00a7r \u0422\u044b \u0437\u043d\u0430\u0435\u0448\u044c... \u044f \u0432\u0438\u0436\u0443 \u0431\u043e\u043b\u044c\u0448\u0435, \u0447\u0435\u043c \u0442\u044b \u0434\u0443\u043c\u0430\u0435\u0448\u044c."));
            }
            setVerityPhase(VerityPhase.OMNISCIENT);
        }
    }

    private void tickOmniscient() {
        // OMNISCIENT: РґСЂСѓР¶РµР»СЋР±РЅС‹Р№, РЅРѕ Р·РЅР°РµС‚ СЃР»РёС€РєРѕРј РјРЅРѕРіРѕ
        tickEmptyVillageDetection();

        // РџРµСЂРµС…РѕРґ РІ COUNTDOWN С‡РµСЂРµР· ~3 РјРёРЅСѓС‚С‹
        if (this.ticksInPhase > 3600) {
            Player nearest = this.level().getNearestPlayer(this, 32.0D);
            if (nearest != null) {
                nearest.sendSystemMessage(Component.literal(
                        "\u00a7c<Verity\u2122>\u00a7r \u0427\u0442\u043e-\u0442\u043e \u0433\u0440\u044f\u0434\u0451\u0442. \u0427\u0435\u0440\u0435\u0437 \u0442\u0440\u0438 \u0434\u043d\u044f."));
                nearest.sendSystemMessage(Component.literal(
                        "\u00a7c<Verity\u2122>\u00a7r \u0427\u0442\u043e-\u0442\u043e \u0433\u0440\u044f\u0434\u0451\u0442. \u0427\u0435\u0440\u0435\u0437 \u0442\u0440\u0438 \u0434\u043d\u044f."));
            }
            setVerityPhase(VerityPhase.COUNTDOWN);
        }
    }

    private void tickCountdown() {
        // COUNTDOWN: РѕР±СЂР°С‚РЅС‹Р№ РѕС‚СЃС‡С‘С‚ 3 РґРЅСЏ. РўРµР»РµРєРёРЅРµР· С‡РµСЂРµР· VerityOpenDoorGoal.
        // Р”Р»СЏ С‚РµСЃС‚Р°: РєР°Р¶РґС‹Р№ В«РґРµРЅСЊВ» = 1200 С‚РёРєРѕРІ (1 РјРёРЅСѓС‚Р°)
        int ticksPerDay = 1200;
        int newDayCounter = this.ticksInPhase / ticksPerDay;

        if (newDayCounter > this.dayCounter) {
            this.dayCounter = newDayCounter;
            // РњРµРЅСЏРµРј Р»РёС†Рѕ РїСЂРё СЃРјРµРЅРµ РґРЅСЏ
            setFaceIndex(getDefaultFaceForPhase(VerityPhase.COUNTDOWN));
            Player nearest = this.level().getNearestPlayer(this, 64.0D);
            switch (dayCounter) {
                 case 1 -> {
                     // Р”РµРЅСЊ 1 вЂ” :| в†’ abnormal shut. РџРѕРІС‚РѕСЂСЏРµС‚ РґРІР°Р¶РґС‹.
                     if (nearest != null) {
                         nearest.sendSystemMessage(Component.literal(
                                 "\u00a7c<Verity\u2122>\u00a7r \u0427\u0442\u043e-\u0442\u043e \u0433\u0440\u044f\u0434\u0451\u0442. \u0427\u0435\u0440\u0435\u0437 \u0442\u0440\u0438 \u0434\u043d\u044f."));
                         nearest.sendSystemMessage(Component.literal(
                                 "\u00a7c<Verity\u2122>\u00a7r \u0427\u0442\u043e-\u0442\u043e \u0433\u0440\u044f\u0434\u0451\u0442. \u0427\u0435\u0440\u0435\u0437 \u0442\u0440\u0438 \u0434\u043d\u044f."));
                     }
                 }
                 case 2 -> {
                     // Р”РµРЅСЊ 2 вЂ” abnormal shut (СѓР¶Рµ СѓСЃС‚Р°РЅРѕРІР»РµРЅ)
                     if (nearest != null) {
                         nearest.sendSystemMessage(Component.literal(
                                 "\u00a7c<Verity\u2122>\u00a7r \u0414\u0432\u0430 \u0434\u043d\u044f... \u0442\u044b \u043c\u043e\u0433 \u043e\u0441\u0442\u0430\u043d\u043e\u0432\u0438\u0442\u044c \u044d\u0442\u043e."));
                     }
                 }
                 case 3 -> {
                     // Р”РµРЅСЊ 3 вЂ” day2 shut в†’ MONSTER
                     if (nearest != null) {
                         nearest.sendSystemMessage(Component.literal(
                                 "\u00a74<Verity>\u00a7r \u0412\u0441\u0451 \u0443\u0436\u0435 \u043a\u043e\u043d\u0447\u0435\u043d\u043e. \u0422\u044b \u043c\u043e\u0439!"));
                         nearest.sendSystemMessage(Component.literal(
                                 "\u00a74<Verity>\u00a7r ...\u0422\u042b \u041c\u041e\u0419!"));
                     }
                    this.nearbyMessageSent = false;
                    setVerityPhase(VerityPhase.MONSTER);
                }
            }
        }

        // РџРѕРµРґР°РµРј Р¶РёС‚РµР»РµР№
        tickEmptyVillageDetection();
    }


    private void tickPossessive() {
        // POSSESSIVE: РїРѕСЃР»Рµ РїСЂРёРјРёСЂРµРЅРёСЏ вЂ” СЃРЅРѕРІР° В«РЅРѕСЂРјР°Р»СЊРЅС‹Р№В», РґСЂСѓР¶РµР»СЋР±РЅС‹Р№, РќРћ СЃРѕР±СЃС‚РІРµРЅРЅРёРє.
        // Р­С‚Рѕ РєР»СЋС‡РµРІРѕР№ РјРѕРјРµРЅС‚ Р»РѕСЂР°: Verity РІРµРґС‘С‚ СЃРµР±СЏ РєР°Рє РЅРё РІ С‡С‘Рј РЅРµ Р±С‹РІР°Р»Рѕ!
        setMonsterForm(false);

        // Р РµРІРЅРѕСЃС‚СЊ: РµСЃР»Рё РІС‚РѕСЂРѕР№ РёРіСЂРѕРє СЂСЏРґРѕРј в†’ Verity РЅР°С‡РёРЅР°РµС‚ СЃР»РµРґРёС‚СЊ в†’ HUNTER
        var otherPlayers = this.level().getEntitiesOfClass(
                net.minecraft.server.level.ServerPlayer.class,
                this.getBoundingBox().inflate(32.0D)
        );
        // Р•СЃР»Рё СЂСЏРґРѕРј >1 РёРіСЂРѕРєР° вЂ” РѕРґРёРЅ РёР· РЅРёС… В«TwixxelВ»
        if (otherPlayers.size() > 1) {
            Player main = this.level().getNearestPlayer(this, 32.0D);
            if (main != null) {
                main.sendSystemMessage(Component.literal(
                        "\u00a7e<Verity\u2122>\u00a7r \u041f\u043e\u0447\u0435\u043c\u0443. ...\u041d\u0435\u0442 \u043f\u0440\u0438\u0447\u0438\u043d\u044b \u0438\u0441\u043a\u0430\u0442\u044c \u0434\u0440\u0443\u0433\u0438\u0445 \u043b\u044e\u0434\u0435\u0439. \u0423 \u0442\u0435\u0431\u044f \u0435\u0441\u0442\u044c \u044f."));
                main.sendSystemMessage(Component.literal(
                        "\u00a7c<Verity\u2122>\u00a7r \u0413\u0434\u0435 \u043e\u043d?"));
            }
            setVerityPhase(VerityPhase.HUNTER);
        }

        // РџРѕРµРґР°РµРј Р¶РёС‚РµР»РµР№ РІ С‚РµРЅРё
        tickEmptyVillageDetection();
    }

    private void tickHunter() {
        // HUNTER: СѓР±РёРІР°РµС‚ В«TwixxelВ» (РґСЂСѓРіРёС… РёРіСЂРѕРєРѕРІ), РїРѕС‚РѕРј РІРѕР·РІСЂР°С‰Р°РµС‚СЃСЏ Рє РѕСЃРЅРѕРІРЅРѕРјСѓ.
        setMonsterForm(true);

        Player main = this.level().getNearestPlayer(this, 64.0D);

        // РђС‚Р°РєСѓРµРј Р”Р РЈР“РРҐ РёРіСЂРѕРєРѕРІ (РЅРµ РѕСЃРЅРѕРІРЅРѕРіРѕ)
        var allPlayers = this.level().getEntitiesOfClass(Player.class, this.getBoundingBox().inflate(32.0D));
        Player twixxelTarget = null;
        for (Player p : allPlayers) {
            if (p != main) {
                twixxelTarget = p;
                break;
            }
        }

        if (twixxelTarget != null) {
            // РџСЂРµСЃР»РµРґСѓРµРј вЂ” Р»РёС†Рѕ РєСЂРёС‡РёС‚ (11)
            if (!this.hunterChasing) {
                this.hunterChasing = true;
                setFaceIndex(FACE_SERIOUS_3);
            }
            this.getNavigation().moveTo(twixxelTarget, 1.8D);
            if (this.distanceToSqr(twixxelTarget) < 4.0D) {
                twixxelTarget.hurt(this.damageSources().mobAttack(this), 100.0F);
            }
            if (main != null) {
                main.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 100, 0, false, false, true));
            }
        } else {
            // Р¦РµР»Рё РЅРµС‚ вЂ” СЃРїРѕРєРѕР№РЅРѕРµ Р»РёС†Рѕ (5), РєР°Рє РЅРё РІ С‡С‘Рј РЅРµ Р±С‹РІР°Р»Рѕ
            if (this.hunterChasing) {
                this.hunterChasing = false;
                setFaceIndex(FACE_BORED_P2);
            }
            if (main != null && this.ticksInPhase > 200) {
                main.sendSystemMessage(Component.literal(
                        "\u00a7e<Verity\u2122>\u00a7r ...\u041e\u043d \u043d\u0435 \u0432\u0435\u0440\u043d\u0451\u0442\u0441\u044f. \u041d\u043e \u0443 \u043d\u0430\u0441 \u0441 \u0442\u043e\u0431\u043e\u0439 \u0432\u0441\u0451 \u0445\u043e\u0440\u043e\u0448\u043e."));
                main.sendSystemMessage(Component.literal(
                        "\u00a7e<Verity\u2122>\u00a7r \u041d\u0435 \u0432\u043e\u043b\u043d\u0443\u0439\u0441\u044f. \u042f \u0437\u0434\u0435\u0441\u044c. \u042f \u0432\u0441\u0435\u0433\u0434\u0430 \u0437\u0434\u0435\u0441\u044c."));
                setVerityPhase(VerityPhase.POSSESSIVE);
            }
        }
    }

    // в”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђ РџРћРњРћР©РќРРљР в”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђ

    /**
     * Р”РµС‚РµРєС‚ РїСѓСЃС‚РѕР№ РґРµСЂРµРІРЅРё вЂ” РµСЃР»Рё СЂСЏРґРѕРј РґРѕРјР°/РєСЂРѕРІР°С‚Рё РЅРѕ РЅРµС‚ Р¶РёС‚РµР»РµР№.
     * "Something hungry came through..."
     * Verity РЅРµ РµСЃС‚ Р¶РёС‚РµР»РµР№ РЅР° РіР»Р°Р·Р°С… вЂ” РґРµСЂРµРІРЅРё СѓР¶Рµ РїСѓСЃС‚С‹Рµ.
     */
    private void tickEmptyVillageDetection() {
        if (!VerityConfig.villagerEatingEnabled()) return;
        if (this.villagerEatCooldown > 0) {
            this.villagerEatCooldown--;
            return;
        }
        this.villagerEatCooldown = 200; // РєР°Р¶РґС‹Рµ 10 СЃРµРє

        Player nearest = this.level().getNearestPlayer(this, 48.0D);
        if (nearest == null) return;

        // РџСЂРѕРІРµСЂСЏРµРј: РµСЃС‚СЊ Р»Рё Р¶РёС‚РµР»Рё СЂСЏРґРѕРј
        var villagers = this.level().getEntitiesOfClass(
                net.minecraft.world.entity.npc.Villager.class,
                nearest.getBoundingBox().inflate(48.0D));

        if (!villagers.isEmpty()) {
            emptyVillageMessageSent = false;
            return; // РґРµСЂРµРІРЅСЏ РЅРµ РїСѓСЃС‚Р°СЏ
        }

        // РС‰РµРј РїСЂРёР·РЅР°РєРё РґРµСЂРµРІРЅРё: РєСЂРѕРІР°С‚Рё Рё РґРІРµСЂРё
        int villageBlocks = 0;
        BlockPos playerPos = nearest.blockPosition();
        for (int dx = -24; dx <= 24; dx += 6) {
            for (int dz = -24; dz <= 24; dz += 6) {
                for (int dy = -2; dy <= 6; dy += 3) {
                    BlockPos pos = playerPos.offset(dx, dy, dz);
                    if (!this.level().hasChunkAt(pos)) continue;
                    var state = this.level().getBlockState(pos);
                    if (state.getBlock() instanceof net.minecraft.world.level.block.BellBlock ||
                        state.getBlock() instanceof net.minecraft.world.level.block.LecternBlock ||
                        state.getBlock() instanceof net.minecraft.world.level.block.ComposterBlock ||
                        state.getBlock() instanceof net.minecraft.world.level.block.BarrelBlock ||
                        state.getBlock() instanceof net.minecraft.world.level.block.BlastFurnaceBlock ||
                        state.getBlock() instanceof net.minecraft.world.level.block.SmokerBlock ||
                        state.getBlock() instanceof net.minecraft.world.level.block.StonecutterBlock ||
                        state.getBlock() instanceof net.minecraft.world.level.block.LoomBlock ||
                        state.getBlock() instanceof net.minecraft.world.level.block.CartographyTableBlock ||
                        state.getBlock() instanceof net.minecraft.world.level.block.SmithingTableBlock ||
                        state.getBlock() instanceof net.minecraft.world.level.block.FletchingTableBlock ||
                        state.getBlock() instanceof net.minecraft.world.level.block.GrindstoneBlock ||
                        state.getBlock() instanceof net.minecraft.world.level.block.DirtPathBlock) {
                        villageBlocks++;
                    }
                }
            }
        }

        // 2+ РґРµСЂРµРІРµРЅСЃРєРёС… РјР°СЂРєРµСЂРѕРІ Рё РЅРµС‚ Р¶РёС‚РµР»РµР№ = РїСѓСЃС‚Р°СЏ РґРµСЂРµРІРЅСЏ
        if (villageBlocks >= 2 && !emptyVillageMessageSent) {
            emptyVillageMessageSent = true;
            nearest.sendSystemMessage(Component.literal(
                    "\u00a7c<Verity\u2122>\u00a7r \u0427\u0442\u043e-\u0442\u043e \u0433\u043e\u043b\u043e\u0434\u043d\u043e\u0435 \u043f\u0440\u043e\u0448\u043b\u043e \u0437\u0434\u0435\u0441\u044c..."));
        }

        // РЎР±СЂРѕСЃ РєРѕРіРґР° РёРіСЂРѕРє СѓС€С‘Р»
        if (villageBlocks < 2) {
            emptyVillageMessageSent = false;
        }
    }

    /**
     * РљРѕРЅС‚РµРєСЃС‚РЅС‹Рµ Р°РІС‚Рѕ-СЂРµРїР»РёРєРё вЂ” С‚РѕР»СЊРєРѕ РєРѕРіРґР° РµСЃС‚СЊ СЂРµР°Р»СЊРЅС‹Р№ РїРѕРІРѕРґ.
     * РќР• РїРѕ С‚Р°Р№РјРµСЂСѓ вЂ” С‚РѕР»СЊРєРѕ РїРѕ СЃРѕР±С‹С‚РёСЏРј.
    /**
     * РЎСЂС‹РІ РєСЂС‹С€Рё вЂ” Verity С…РІР°С‚Р°РµС‚ РєСЂС‹С€Сѓ РЅР°Рґ РёРіСЂРѕРєРѕРј Рё РІС‹РєРёРґС‹РІР°РµС‚ РµС‘.
     */
    private void tearOffRoof(Player player) {
        if (this.level().isClientSide) return;

        BlockPos playerPos = player.blockPosition();
        java.util.Set<BlockPos> roofBlocks = new java.util.HashSet<>();
        int maxRoofSize = 80;

        // РЁР°Рі 1: РќР°Р№С‚Рё СЃС‚РµРЅС‹ РІ 8 РЅР°РїСЂР°РІР»РµРЅРёСЏС…
        int[][] dirs = {{1,0},{-1,0},{0,1},{0,-1},{1,1},{-1,-1},{1,-1},{-1,1}};
        java.util.List<BlockPos> wallTops = new java.util.ArrayList<>();

        for (int[] dir : dirs) {
            for (int dist = 1; dist <= 8; dist++) {
                BlockPos check = playerPos.offset(dir[0] * dist, 1, dir[1] * dist);
                if (!this.level().hasChunkAt(check)) break;
                var state = this.level().getBlockState(check);
                if (!state.isAir() && !state.canBeReplaced()) {
                    int topY = check.getY();
                    for (int dy = 1; dy <= 20; dy++) {
                        BlockPos up = check.above(dy);
                        if (!this.level().hasChunkAt(up)) break;
                        if (this.level().getBlockState(up).isAir() ||
                            this.level().getBlockState(up).canBeReplaced()) {
                            topY = up.getY() - 1;
                            break;
                        }
                        topY = up.getY();
                    }
                    BlockPos roofStart = new BlockPos(check.getX(), topY + 1, check.getZ());
                    var roofState = this.level().getBlockState(roofStart);
                    if (!roofState.isAir() && isWeakBlock(roofState)) {
                        wallTops.add(roofStart);
                    }
                    break;
                }
            }
        }

        if (wallTops.isEmpty()) {
            // РќРµС‚ СЃС‚РµРЅ вЂ” РїСЂРѕРІРµСЂСЏРµРј Р±Р»РѕРєРё РїСЂСЏРјРѕ РЅР°Рґ РіРѕР»РѕРІРѕР№ РёРіСЂРѕРєР°
            for (int dy = 2; dy <= 6; dy++) {
                BlockPos above = playerPos.above(dy);
                if (!this.level().hasChunkAt(above)) break;
                var state = this.level().getBlockState(above);
                if (!state.isAir() && isWeakBlock(state)) {
                    wallTops.add(above);
                    break;
                }
            }
        }

        if (wallTops.isEmpty()) return;

        // РЁР°Рі 2: Flood-fill
        java.util.Queue<BlockPos> queue = new java.util.LinkedList<>(wallTops);
        while (!queue.isEmpty() && roofBlocks.size() < maxRoofSize) {
            BlockPos pos = queue.poll();
            if (roofBlocks.contains(pos)) continue;
            if (!this.level().hasChunkAt(pos)) continue;
            var state = this.level().getBlockState(pos);
            if (state.isAir() || !isWeakBlock(state)) continue;

            roofBlocks.add(pos);

            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dz == 0) continue;
                    queue.add(pos.offset(dx, 0, dz));
                }
            }

            // Р’С‹РїРёСЂР°РЅРёСЏ РІРІРµСЂС…
            BlockPos above = pos.above();
            if (this.level().hasChunkAt(above) && isWeakBlock(this.level().getBlockState(above))) {
                queue.add(above);
            }
            // Р’С‹РїРёСЂР°РЅРёСЏ РІРЅРёР· (СЃРІРµСЃС‹)
            BlockPos belowSide = pos.below();
            if (this.level().hasChunkAt(belowSide) && isWeakBlock(this.level().getBlockState(belowSide))
                    && this.level().getBlockState(belowSide.below()).isAir()) {
                queue.add(belowSide);
            }
        }

        if (roofBlocks.isEmpty()) return;

        // Р’С‹С‡РёСЃР»СЏРµРј С†РµРЅС‚СЂ РєСЂС‹С€Рё
        double cx = 0, cy = 0, cz = 0;
        for (BlockPos p : roofBlocks) {
            cx += p.getX() + 0.5;
            cy += p.getY() + 0.5;
            cz += p.getZ() + 0.5;
        }
        cx /= roofBlocks.size();
        cy /= roofBlocks.size();
        cz /= roofBlocks.size();

        // РќР°РїСЂР°РІР»РµРЅРёРµ Р±СЂРѕСЃРєР° вЂ” РѕС‚ С†РµРЅС‚СЂР° РєСЂС‹С€Рё РІ СЃС‚РѕСЂРѕРЅСѓ (РЅРµ РІРІРµСЂС…)
        double throwAngle = this.random.nextDouble() * Math.PI * 2;
        double throwX = Math.cos(throwAngle) * 0.8;
        double throwZ = Math.sin(throwAngle) * 0.8;

        // РЁР°Рі 3: РЈРґР°Р»РёС‚СЊ Р±Р»РѕРєРё + СЃРѕР·РґР°С‚СЊ FallingBlockEntity
        // Р’РЎР• Р±Р»РѕРєРё РїРѕР»СѓС‡Р°СЋС‚ РћР”Р˜РќРђРљРћР’РЈР® СЃРєРѕСЂРѕСЃС‚СЊ вЂ” Р»РµС‚СЏС‚ РєР°Рє РµРґРёРЅС‹Р№ РѕР±СЉРµРєС‚
        for (BlockPos pos : roofBlocks) {
            var blockState = this.level().getBlockState(pos);

            // Р\u00A7Р°СЃС‚РёС†С‹ СЂР°Р·СЂСѓС€РµРЅРёСЏ
            if (this.level() instanceof net.minecraft.server.level.ServerLevel sl) {
                sl.sendParticles(
                        new net.minecraft.core.particles.BlockParticleOption(
                                net.minecraft.core.particles.ParticleTypes.BLOCK, blockState),
                        pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                        5, 0.4, 0.4, 0.4, 0.1);
            }

            // РЈРґР°Р»СЏРµРј Р±Р»РѕРє РёР· РјРёСЂР°
            this.level().removeBlock(pos, false);

            // РЎРѕР·РґР°С‘Рј FallingBlockEntity С‡РµСЂРµР· setBlock + fall (Р±РµР·РѕРїР°СЃРЅС‹Р№ СЃРїРѕСЃРѕР±)
            this.level().setBlock(pos, blockState, 16); // 16 = UPDATE_CLIENTS
            var spawned = net.minecraft.world.entity.item.FallingBlockEntity.fall(
                    this.level(), pos, blockState);
            if (spawned != null) {
                // РћР”РРќРђРљРћР’РђРЇ СЃРєРѕСЂРѕСЃС‚СЊ РґР»СЏ РІСЃРµС… вЂ” Р»РµС‚СЏС‚ РєР°Рє РµРґРёРЅР°СЏ РєСЂС‹С€Р°
                spawned.setDeltaMovement(throwX, 1.0, throwZ);
                spawned.setNoGravity(true);
                spawned.hurtMarked = true;
                spawned.dropItem = false;
            }
        }

        // Р\u00A7РµСЂРµР· 1.5 СЃРµРє РІРєР»СЋС‡Р°РµРј РіСЂР°РІРёС‚Р°С†РёСЋ (Р±РµР· Thread.sleep!)
        this.roofGravityTimer = 30;
        this.roofThrowX = throwX;
        this.roofThrowZ = throwZ;

        // Р”СЂР°РјР°С‚РёС‡РЅС‹Р№ Р·РІСѓРє
        this.level().playSound(null, player.getX(), player.getY() + 3, player.getZ(),
                SoundEvents.ENDER_DRAGON_GROWL,
                SoundSource.HOSTILE, 2.0F, 0.6F);
        this.level().playSound(null, player.getX(), player.getY() + 3, player.getZ(),
                SoundEvents.ANVIL_LAND,
                SoundSource.HOSTILE, 1.5F, 0.5F);

        // Р­С„С„РµРєС‚С‹ РЅР° РёРіСЂРѕРєР°
        player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 200, 1, false, false, true));

        VerityMod.LOGGER.info("Verity tore off roof: {} blocks thrown as one object", roofBlocks.size());
    }

    private boolean isWeakBlock(BlockState state) {
        if (state.isAir()) return false;
        float hardness = state.getDestroySpeed(this.level(), this.blockPosition());
        if (hardness < 0) return false;
        return hardness < 3.0f;
    }

    /**
     * Р’РµРґРµРЅРёРµ РёРіСЂРѕРєР° вЂ” Verity РёРґС‘С‚ Рє С†РµР»Рё Рё Р¶РґС‘С‚ РµСЃР»Рё РёРіСЂРѕРє РѕС‚СЃС‚Р°Р».
     */
    private void tickLeading() {
        Player nearest = this.level().getNearestPlayer(this, 64.0D);
        if (nearest == null) {
            this.leading = false;
            this.leadTarget = null;
            return;
        }

        // Если цели нет — остановись
        if (this.leadTarget == null) {
            this.leading = false;
            return;
        }

        double distToTarget = this.distanceToSqr(this.leadTarget.getX() + 0.5, this.leadTarget.getY(), this.leadTarget.getZ() + 0.5);
        double distToPlayer = this.distanceToSqr(nearest);

        // Если игрок отстал >20 блоков — ждём
        if (distToPlayer > 400.0D) {
            this.getNavigation().stop();
            this.getLookControl().setLookAt(nearest, 30.0F, 30.0F);
            // Раз в 10 сек напоминаем
            if (this.tickCount % 200 == 0) {
                nearest.sendSystemMessage(Component.literal(
                        "\u00a7e<Verity\u2122>\u00a7r Пора идти? Отстаешь."));
            }
            return;
        }

        if (distToTarget < 16.0D) {
            this.getNavigation().stop();
            this.leading = false;
            this.leadTarget = null;
            nearest.sendSystemMessage(Component.literal(
                    "\u00a7e<Verity\u2122>\u00a7r Мы пришли!"));
            this.talkAnimTick = 30;
            return;
        }

        // Идём к цели
        if (this.getNavigation().isDone()) {
            this.getNavigation().moveTo(
                    this.leadTarget.getX() + 0.5,
                    this.leadTarget.getY(),
                    this.leadTarget.getZ() + 0.5,
                    1.2D);
        }
    }

    private void handleAutoDialogue() {
        if (this.chatCooldown > 0) {
            this.chatCooldown--;
            return;
        }
        Player nearest = this.level().getNearestPlayer(this, 32.0D);
        if (nearest == null || !nearest.isAlive()) return;

        VerityPhase phase = getVerityPhase();

        // COUNTDOWN день 2-3 — безумие, повтор фраз
        if (phase == VerityPhase.COUNTDOWN && this.dayCounter >= 1) {
            // Реже говорит (раз в 60-90 сек)
            if (this.ticksInPhase % (1200 + this.random.nextInt(600)) != 0) {
                this.chatCooldown = 200;
                return;
            }

            // 50% шанс — повторить фразу из истории (эхо безумия)
            if (this.random.nextBoolean()) {
                var history = getDialogueController().getDialogueHistory();
                if (!history.isEmpty()) {
                    // Ищем фразы игрока (не Verity)
                    java.util.List<String> playerLines = new java.util.ArrayList<>();
                    for (String line : history) {
                        if (line.contains("\u00a77") && !line.contains("<Verity")) {
                            playerLines.add(line);
                        }
                    }
                    if (!playerLines.isEmpty()) {
                        String echo = playerLines.get(this.random.nextInt(playerLines.size()));
                        // Убираем цветовые коды и имя игрока
                        String clean = echo.replaceAll("\u00a7[0-9a-fklmnor]", "")
                                .replaceAll("^[^:]+:\\s*", "").trim();
                        if (!clean.isEmpty() && clean.length() < 50) {
                            nearest.sendSystemMessage(Component.literal(
                                    "\u00a7c<Verity\u2122>\u00a7r " + clean + "..."));
                            this.talkAnimTick = 30;
                            this.chatCooldown = 1200;
                            return;
                        }
                    }
                }
            }

            // 50% шанс — "три дня" обсессия
            String[] obsessions = {
                    "\u00a7c<Verity\u2122>\u00a7r Три дня.",
                    "\u00a7c<Verity\u2122>\u00a7r Скоро-скоро.",
                    "\u00a7c<Verity\u2122>\u00a7r Обернись.",
                    "\u00a7c<Verity\u2122>\u00a7r ...ждать.",
                    "\u00a7c<Verity\u2122>\u00a7r Дни летят быстро.",
                    "\u00a7c<Verity\u2122>\u00a7r Пора мне остановиться."
            };
            nearest.sendSystemMessage(Component.literal(
                    obsessions[this.random.nextInt(obsessions.length)]));
            this.talkAnimTick = 30;
            this.chatCooldown = 1200;
            return;
        }

        boolean shouldSpeak = false;
        String contextHint = "";

        // Контекст 1: игрок сильно ранен (меньше 35% HP), кулдаун 5 минут
        if (nearest.getHealth() < nearest.getMaxHealth() * 0.35f) {
            shouldSpeak = true;
            contextHint = "Игрок тяжело ранен. Отреагируй с заботой.";
        }
        // Контекст 2: ночь и игрок на поверхности
        else if (this.level().getDayTime() % 24000 > 13000 && this.level().getDayTime() % 24000 < 18000
                && nearest.getY() > 60 && this.ticksInPhase % 2400 == 0) {
            shouldSpeak = true;
            contextHint = "Наступила ночь. Предупреди игрока про мобов или скажи что-то по теме.";
        }
        // Контекст 3: игрок в пещере (низкая высота)
        else if (nearest.getY() < 20 && this.ticksInPhase % 3600 == 0) {
            shouldSpeak = true;
            contextHint = "Игрок глубоко под землёй в шахте. Прокомментируй это.";
        }
        // Контекст 4: COUNTDOWN — день сменился
        else if (phase == VerityPhase.COUNTDOWN && this.ticksInPhase % 1200 == 0 && this.ticksInPhase > 0) {
            shouldSpeak = true;
            contextHint = "Прошёл ещё день отсчёта. Напомни сколько осталось.";
        }
        // Контекст 5: POSSESSIVE/HUNTER — другой игрок рядом
        else if ((phase == VerityPhase.POSSESSIVE || phase == VerityPhase.HUNTER)
                && this.level().getEntitiesOfClass(net.minecraft.server.level.ServerPlayer.class,
                    this.getBoundingBox().inflate(32.0D)).size() > 1
                && this.ticksInPhase % 1800 == 0) {
            shouldSpeak = true;
            contextHint = "Рядом другой игрок. Тебе это не нравится. Отреагируй.";
        }

        if (shouldSpeak) {
            getDialogueController().triggerContextualDialogue(contextHint);
            this.talkAnimTick = 40;
            this.chatCooldown = 1200; // 60 СЃРµРє РєСѓР»РґР°СѓРЅ
        } else {
            this.chatCooldown = 200; // РїСЂРѕРІРµСЂСЏРµРј РєР°Р¶РґС‹Рµ 10 СЃРµРє
        }
    }

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

        this.teleportTo(targetX, targetY, targetZ);
    }

    /**
     * Р”РѕРіРѕРЅСЏСЋС‰РёР№ С‚РµР»РµРїРѕСЂС‚ вЂ” РµСЃР»Рё РёРіСЂРѕРє СѓР±РµР¶Р°Р» >32 Р±Р»РѕРєРѕРІ, Verity С‚РµР»РµРїРѕСЂС‚РёСЂСѓРµС‚СЃСЏ
     * РЅР° ~10 Р±Р»РѕРєРѕРІ Р·Р° СЃРїРёРЅРѕР№ Рё РіРѕРІРѕСЂРёС‚ "Рћ, СЏ С‚СѓС‚."
     */
    private void tickCatchUpTeleport() {
        // РќРµ С‚РµР»РµРїРѕСЂС‚РёСЂРѕРІР°С‚СЊСЃСЏ РІРѕ РІСЂРµРјСЏ СЂР°Р·РіРѕРІРѕСЂР° вЂ” С‡С‚РѕР±С‹ СЃРѕРѕР±С‰РµРЅРёСЏ РЅРµ РЅР°Р»РµР·Р°Р»Рё
        if (this.talkAnimTick > 0) return;
        if (this.chatCooldown > 580) return; // С‚РѕР»СЊРєРѕ С‡С‚Рѕ СЃРєР°Р·Р°Р» С‡С‚Рѕ-С‚Рѕ
        if (this.teleportCooldown > 0) {
            this.teleportCooldown--;
            return;
        }

        Player nearest = this.level().getNearestPlayer(this, 512.0D);
        if (nearest == null) return;

        double distSq = this.distanceToSqr(nearest);
        if (distSq < 1024.0D) return; // < 32 блоков — не нужно

        // Р˜С‰РµРј С‚РѕС‡РєСѓ РЅР° ~10 Р±Р»РѕРєРѕРІ Р·Р° СЃРїРёРЅРѕР№ РёРіСЂРѕРєР°
        Vec3 lookVec = nearest.getViewVector(1.0F).normalize();
        double targetX = nearest.getX() - lookVec.x * 10.0D + (this.random.nextDouble() - 0.5D) * 2.0D;
        double targetY = nearest.getY();
        double targetZ = nearest.getZ() - lookVec.z * 10.0D + (this.random.nextDouble() - 0.5D) * 2.0D;

        // РџСЂРѕРІРµСЂСЏРµРј С‡С‚Рѕ С‚РѕС‡РєР° Р±РµР·РѕРїР°СЃРЅР° вЂ” РЅРµ РІ СЃС‚РµРЅРµ
        BlockPos targetPos = BlockPos.containing(targetX, targetY, targetZ);
        BlockState feetState = this.level().getBlockState(targetPos);
        BlockState headState = this.level().getBlockState(targetPos.above());
        if (!feetState.isAir() && !feetState.canBeReplaced()) {
            // РўРѕС‡РєР° РІ СЃС‚РµРЅРµ вЂ” РїСЂРѕР±СѓРµРј РЅР°Р№С‚Рё РІРѕР·РґСѓС… СЂСЏРґРѕРј
            for (int dy = -1; dy <= 2; dy++) {
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        BlockPos tryPos = targetPos.offset(dx, dy, dz);
                        if (this.level().getBlockState(tryPos).isAir()
                                && this.level().getBlockState(tryPos.above()).isAir()) {
                            targetX = tryPos.getX() + 0.5;
                            targetY = tryPos.getY();
                            targetZ = tryPos.getZ() + 0.5;
                            break;
                        }
                    }
                }
            }
        }

        // РўРµР»РµРїРѕСЂС‚РёСЂСѓРµРјСЃСЏ
        this.teleportTo(targetX, targetY, targetZ);

        // Р РµРїР»РёРєР° РІ Р·Р°РІРёСЃРёРјРѕСЃС‚Рё РѕС‚ С„Р°Р·С‹
        VerityPhase phase = getVerityPhase();
        String msg;
        if (phase == VerityPhase.COUNTDOWN || phase == VerityPhase.POSSESSIVE
                || phase == VerityPhase.HUNTER) {
            String[] creepyMsgs = {
                    "\u00a7e<Verity\u2122>\u00a7r \u042f \u0442\u0443\u0442.",
                    "\u00a7e<Verity\u2122>\u00a7r \u041d\u0435 \u0443\u0431\u0435\u0433\u0430\u0439.",
                    "\u00a7e<Verity\u2122>\u00a7r \u042f \u0432\u0438\u0436\u0443 \u0442\u0435\u0431\u044f.",
                    "\u00a7e<Verity\u2122>\u00a7r \u041a\u0443д\u0430 \u0442\u044b \u0441обрался?"
            };
            msg = creepyMsgs[this.random.nextInt(creepyMsgs.length)];
        } else {
            String[] friendlyMsgs = {
                    "\u00a7e<Verity\u2122>\u00a7r \u041e, \u044f \u0442\u0443\u0442!",
                    "\u00a7e<Verity\u2122>\u00a7r \u041fодожди \u043cеня!",
                    "\u00a7e<Verity\u2122>\u00a7r \u042dй, \u043dе \u0442ак \u0431ыстро!",
                    "\u00a7e<Verity\u2122>\u00a7r \u042f \u0434огнал!"
            };
            msg = friendlyMsgs[this.random.nextInt(friendlyMsgs.length)];
        }
        nearest.sendSystemMessage(Component.literal(msg));
        this.talkAnimTick = 30;
        this.teleportCooldown = 200; // 10 СЃРµРєСѓРЅРґ
    }

    private void updateRollAngle() {
        Vec3 motion = this.getDeltaMovement();
        double hSpeedSqr = motion.x * motion.x + motion.z * motion.z;
        boolean moving = hSpeedSqr > 5.0E-5;

        if (moving) {
            // РЈРіРѕР» РјРµР¶РґСѓ РЅР°РїСЂР°РІР»РµРЅРёРµРј РґРІРёР¶РµРЅРёСЏ Рё РЅР°РїСЂР°РІР»РµРЅРёРµРј РІР·РіР»СЏРґР°
            double moveYaw = Math.atan2(-motion.x, motion.z); // РЅР°РїСЂР°РІР»РµРЅРёРµ РґРІРёР¶РµРЅРёСЏ
            double lookYaw  = Math.toRadians(this.getYRot());   // РЅР°РїСЂР°РІР»РµРЅРёРµ РІР·РіР»СЏРґР°
            double relAngle  = moveYaw - lookYaw;
            float speed = (float) Math.sqrt(hSpeedSqr) * 20.0F; // РЅРѕСЂРјР°Р»РёР·Р°С†РёСЏ СЃРєРѕСЂРѕСЃС‚Рё

            // forward = РїРёС‚С‡ (РєСЂСѓС‡РµРЅРёРµ РІРїРµСЂРµРґ/РЅР°Р·Р°Рґ)
            float targetPitch  = speed *  (float) Math.cos(relAngle) * 2.5F;
            // strafe = Р±РѕРєРѕРІРѕР№ РЅР°РєР»РѕРЅ
            float targetStrafe = speed * -(float) Math.sin(relAngle) * 2.5F;

            // РџР»Р°РІРЅС‹Р№ lerp (15% РїСЂРёР±Р»РёР¶РµРЅРёРµ Р·Р° С‚РёРє)
            this.rollAngle  = this.rollAngle  + (targetPitch  - this.rollAngle)  * 0.18F;
            this.rollStrafe = this.rollStrafe + (targetStrafe - this.rollStrafe) * 0.18F;
        } else {
            // Р—Р°С‚СѓС…Р°РЅРёРµ РЅР°Р·Р°Рґ Рє 0
            this.rollAngle  *= 0.85F;
            this.rollStrafe *= 0.85F;
            if (Math.abs(this.rollAngle)  < 0.001F) this.rollAngle  = 0.0F;
            if (Math.abs(this.rollStrafe) < 0.001F) this.rollStrafe = 0.0F;
        }
    }

    // в”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђ РЎРћРҐР РђРќР•РќРР• Р’ NBT (persistence) в”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђ
    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString("VerityPhase", getVerityPhase().name());
        tag.putInt("TicksInPhase", this.ticksInPhase);
        tag.putInt("DayCounter", this.dayCounter);
        tag.putInt("ChatCooldown", this.chatCooldown);
        tag.putInt("IntroTicks", this.introTicks);
        tag.putBoolean("Leading", this.leading);
        tag.putBoolean("Thrown", this.thrown);
        if (this.leadTarget != null) {
            tag.putInt("LeadTargetX", this.leadTarget.getX());
            tag.putInt("LeadTargetY", this.leadTarget.getY());
            tag.putInt("LeadTargetZ", this.leadTarget.getZ());
        }

        // РЎРѕС…СЂР°РЅСЏРµРј РёСЃС‚РѕСЂРёСЋ РґРёР°Р»РѕРіР°
        var dc = getDialogueController();
        var history = dc.getDialogueHistory();
        if (!history.isEmpty()) {
            var list = new net.minecraft.nbt.ListTag();
            for (String line : history) {
                list.add(net.minecraft.nbt.StringTag.valueOf(line));
            }
            tag.put("DialogueHistory", list);
        }

        var facts = dc.getKnownFactsList();
        if (!facts.isEmpty()) {
            var factList = new net.minecraft.nbt.ListTag();
            for (String fact : facts) {
                factList.add(net.minecraft.nbt.StringTag.valueOf(fact));
            }
            tag.put("KnownFacts", factList);
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("VerityPhase")) {
            this.entityData.set(VERITY_PHASE, VerityPhase.valueOf(tag.getString("VerityPhase")).ordinal());
        }
        this.ticksInPhase = tag.getInt("TicksInPhase");
    }

    // ────── AI GOALS (внутренние классы) ──────

    /**
     * Следование за игроком (HELPER, OMNISCIENT, POSSESSIVE)
     */
    static class VerityFollowGoal extends Goal {
        private final VerityEntity entity;
        private Player target;
        private final double speed;

        public VerityFollowGoal(VerityEntity entity, double speed) {
            this.entity = entity;
            this.speed = speed;
            this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            if (entity.isStayHere() || entity.isThrown()) return false;
            VerityPhase p = entity.getVerityPhase();
            if (p == VerityPhase.MONSTER || p == VerityPhase.HUNTER) return false;
            if (p == VerityPhase.COUNTDOWN && entity.getDayCounter() >= 2) return false; // День 3 - замер
            if (entity.isLeadingToVillage()) return false;
            if (entity.stareTimer > 0) return false;
            this.target = entity.level().getNearestPlayer(entity, 256.0D);
            return this.target != null && entity.distanceToSqr(this.target) > 16.0D;
        }

        @Override
        public boolean canContinueToUse() {
            return this.target != null && this.target.isAlive() && entity.distanceToSqr(this.target) > 4.0D;
        }

        @Override
        public void start() {
            entity.getNavigation().moveTo(this.target, this.speed);
        }

        @Override
        public void stop() {
            entity.getNavigation().stop();
            this.target = null;
        }

        @Override
        public void tick() {
            entity.getLookControl().setLookAt(this.target, 30.0F, 30.0F);
            if (entity.getRandom().nextInt(5) == 0) {
                entity.getNavigation().moveTo(this.target, this.speed);
            }
        }
    }

    /**
     * Сталкер — держится на расстоянии (COUNTDOWN)
     */
    static class VerityStalkGoal extends Goal {
        private final VerityEntity entity;
        private Player target;
        private final double speed;
        private int cooldown = 0;

        public VerityStalkGoal(VerityEntity entity, double speed) {
            this.entity = entity;
            this.speed = speed;
            this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            if (entity.getVerityPhase() != VerityPhase.COUNTDOWN) return false;
            this.target = entity.level().getNearestPlayer(entity, 256.0D);
            return this.target != null;
        }

        @Override
        public void tick() {
            if (target == null) return;
            double distSq = entity.distanceToSqr(target);

            if (cooldown > 0) {
                cooldown--;
                return;
            }

            if (distSq < 100.0D) {
                double dx = entity.getX() - target.getX();
                double dz = entity.getZ() - target.getZ();
                double len = Math.sqrt(dx * dx + dz * dz);
                if (len > 0.01) {
                    entity.getNavigation().moveTo(
                            entity.getX() + dx / len * 5,
                            entity.getY(),
                            entity.getZ() + dz / len * 5,
                            speed
                    );
                }
                cooldown = 20;
            } else if (distSq > 400.0D) {
                entity.getNavigation().moveTo(target, speed);
                cooldown = 20;
            } else {
                entity.getNavigation().stop();
                entity.getLookControl().setLookAt(target, 30.0F, 30.0F);
            }
        }
    }

    static class VerityMonsterAttackGoal extends Goal {
        private final VerityEntity entity;
        private Player target;
        private final double speed;

        public VerityMonsterAttackGoal(VerityEntity entity, double speed) {
            this.entity = entity;
            this.speed = speed;
            this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            return false;
        }

        @Override
        public void tick() {
            if (target == null) return;
            double distSq = entity.distanceToSqr(target);

            if (distSq > 9.0D) {
                entity.getNavigation().moveTo(target, speed);
            } else {
                entity.getNavigation().stop();
                if (entity.getRandom().nextInt(40) == 0) {
                    Vec3 dir = target.position().subtract(entity.position()).normalize();
                    target.setDeltaMovement(dir.x * 0.5, 0.3, dir.z * 0.5);
                }
            }

            entity.getLookControl().setLookAt(target, 30.0F, 30.0F);

            boolean pathBlocked = entity.getNavigation().isDone() && distSq > 16.0D;
            if (pathBlocked && entity.getRandom().nextInt(10) == 0) {
                net.minecraft.core.Direction facing = entity.getDirection();
                for (int dy = 0; dy <= 2; dy++) {
                    BlockPos front = entity.blockPosition().relative(facing).above(dy);
                    BlockState frontState = entity.level().getBlockState(front);
                    if (frontState.isAir()) continue;
                    float hardness = frontState.getDestroySpeed(entity.level(), front);
                    if (hardness >= 0 && hardness < 3.0f) {
                        var blockState = entity.level().getBlockState(front);
                        entity.level().destroyBlock(front, false);
                        if (entity.level() instanceof net.minecraft.server.level.ServerLevel sl) {
                            sl.sendParticles(
                                    new net.minecraft.core.particles.BlockParticleOption(
                                            net.minecraft.core.particles.ParticleTypes.BLOCK, blockState),
                                    front.getX() + 0.5, front.getY() + 0.5, front.getZ() + 0.5,
                                    4, 0.2, 0.2, 0.2, 0.1);
                        }
                        entity.getNavigation().moveTo(target, speed);
                        break;
                    }
                }
            }
        }
    }

    static class VerityOpenDoorGoal extends Goal {
        private final VerityEntity entity;
        private final double range;
        private int cooldown = 0;

        public VerityOpenDoorGoal(VerityEntity entity, double range) {
            this.entity = entity;
            this.range = range;
            this.setFlags(EnumSet.noneOf(Flag.class));
        }

        @Override
        public boolean canUse() {
            return entity.getVerityPhase() == VerityPhase.COUNTDOWN;
        }

        @Override
        public void tick() {
            if (cooldown > 0) {
                cooldown--;
                return;
            }
            cooldown = 40;

            BlockPos entityPos = entity.blockPosition();
            for (int dx = -(int) range; dx <= range; dx++) {
                for (int dz = -(int) range; dz <= range; dz++) {
                    for (int dy = -2; dy <= 2; dy++) {
                        BlockPos pos = entityPos.offset(dx, dy, dz);
                        if (entity.level().getBlockState(pos).getBlock() instanceof net.minecraft.world.level.block.DoorBlock) {
                            entity.level().blockEvent(pos, entity.level().getBlockState(pos).getBlock(), 1, 1);
                            return;
                        }
                    }
                }
            }
        }
    }

    public void sayHelperText(String text) {
        Player player = this.level().getNearestPlayer(this, 32.0D);
        if (player != null) {
            player.sendSystemMessage(Component.literal(text));
            if (getDialogueController() != null) {
                String clean = text.replaceAll("§[0-9a-fk-or]", "").replace("<Verity™>", "").trim();
                getDialogueController().addToHistory("Verity: " + clean);
            }
            if (player instanceof net.minecraft.server.level.ServerPlayer sp) {
                net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(sp,
                        new net.verity.net.TTSPayload(text, this.getId(), this.getX(), this.getEyeY(), this.getZ()));
            }
        }
    }

    public void triggerWoodChopOrder() {
        if (getVerityPhase() == VerityPhase.HELPER || getVerityPhase() == VerityPhase.OMNISCIENT) {
            this.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, new ItemStack(net.minecraft.world.item.Items.STONE_AXE));
            if (this.goalSelector.getAvailableGoals() != null) {
                for (var goal : this.goalSelector.getAvailableGoals()) {
                    if (goal.getGoal() instanceof VerityWorkGoal workGoal) {
                        workGoal.forceStartWork();
                    }
                }
            }
        }
    }

    public void triggerDeliverOrder() {
        Player player = this.level().getNearestPlayer(this, 32.0D);
        if (player != null) {
            this.spawnAtLocation(new ItemStack(net.minecraft.world.item.Items.OAK_LOG, 4));
            this.level().playSound(null, this.getX(), this.getY(), this.getZ(), net.minecraft.sounds.SoundEvents.ITEM_PICKUP, net.minecraft.sounds.SoundSource.PLAYERS, 0.8F, 1.0F);
            this.sayHelperText("§e<Verity™>§r Держи дерево! Вот, отдаю тебе.");
            this.setTalkAnimTick(35);
        }
        if (this.goalSelector.getAvailableGoals() != null) {
            for (var goal : this.goalSelector.getAvailableGoals()) {
                if (goal.getGoal() instanceof VerityWorkGoal workGoal) {
                    workGoal.forceDeliver();
                    return;
                }
            }
        }
    }

    public void triggerStopWorkOrder() {
        if (this.goalSelector.getAvailableGoals() != null) {
            for (var goal : this.goalSelector.getAvailableGoals()) {
                if (goal.getGoal() instanceof VerityWorkGoal workGoal) {
                    workGoal.forceStopWork();
                }
                if (goal.getGoal() instanceof VerityLeadToVillageGoal leadGoal) {
                    leadGoal.stopLeading();
                }
            }
        }
    }

    public void triggerLeadToVillageOrder(BlockPos villagePos) {
        if (this.goalSelector.getAvailableGoals() != null) {
            for (var goal : this.goalSelector.getAvailableGoals()) {
                if (goal.getGoal() instanceof VerityLeadToVillageGoal leadGoal) {
                    leadGoal.startLeading(villagePos);
                    return;
                }
            }
        }
    }

    public boolean isLeadingToVillage() {
        if (this.goalSelector.getAvailableGoals() != null) {
            for (var goal : this.goalSelector.getAvailableGoals()) {
                if (goal.getGoal() instanceof VerityLeadToVillageGoal leadGoal) {
                    return leadGoal.isLeading();
                }
            }
        }
        return false;
    }

    private static class VerityLeadToVillageGoal extends Goal {
        private final VerityEntity entity;
        private BlockPos villagePos = null;
        private boolean active = false;
        private int waitPlayerTicks = 0;

        public VerityLeadToVillageGoal(VerityEntity entity) {
            this.entity = entity;
            this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        }

        public void startLeading(BlockPos pos) {
            if (pos == null) return;
            this.villagePos = pos;
            this.active = true;
            this.waitPlayerTicks = 0;
            moveTowardsVillage();
        }

        public void stopLeading() {
            this.active = false;
            this.villagePos = null;
            entity.getNavigation().stop();
        }

        public boolean isLeading() {
            return active && villagePos != null;
        }

        @Override
        public boolean canUse() {
            return active && villagePos != null;
        }

        @Override
        public boolean canContinueToUse() {
            return active && villagePos != null;
        }

        private void moveTowardsVillage() {
            if (villagePos == null) return;
            double dx = villagePos.getX() + 0.5 - entity.getX();
            double dz = villagePos.getZ() + 0.5 - entity.getZ();
            double dist = Math.sqrt(dx * dx + dz * dz);
            if (dist > 0.001) {
                double step = Math.min(dist, 24.0D);
                double targetX = entity.getX() + (dx / dist) * step;
                double targetZ = entity.getZ() + (dz / dist) * step;
                int targetY = entity.level().getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, (int) targetX, (int) targetZ);
                entity.getNavigation().moveTo(targetX, targetY, targetZ, 1.3D);
            }
        }

        @Override
        public void tick() {
            if (villagePos == null) return;
            Player player = entity.level().getNearestPlayer(entity, 64.0D);
            if (player != null) {
                double pDistSq = entity.distanceToSqr(player);
                if (pDistSq > 1600.0D) {
                    entity.getNavigation().stop();
                    entity.getLookControl().setLookAt(player, 30.0F, 30.0F);
                    waitPlayerTicks++;
                    if (waitPlayerTicks % 80 == 0) {
                        entity.sayHelperText("§e<Verity™>§r Догоняй! Деревня в той стороне!");
                        entity.setTalkAnimTick(30);
                    }
                    return;
                }
            }

            double vDistSq = entity.distanceToSqr(villagePos.getX() + 0.5, entity.getY(), villagePos.getZ() + 0.5);
            if (vDistSq <= 625.0D) {
                entity.sayHelperText("§e<Verity™>§r Смотри! Вот мы и пришли к деревне!");
                entity.setTalkAnimTick(40);
                stopLeading();
                return;
            }

            if (entity.getNavigation().isDone() || entity.tickCount % 15 == 0) {
                moveTowardsVillage();
            }
        }

        @Override
        public void stop() {
            stopLeading();
        }
    }

        private static class VerityWorkGoal extends Goal {
        private final VerityEntity entity;
        private BlockPos targetPos = null;
        private int miningTicks = 0;
        private int blocksChopped = 0;
        private int askCooldown = 0;
        private int woodTripCooldown = 1200; // ~60 sec initial delay
        private boolean isWood = true;
        private boolean isWoodTrip = false;
        private int woodTripTicks = 0;
        private boolean isDelivering = false;
        private int deliveryTicks = 0;
        private int vacuumWaitTicks = 0;
        private final java.util.List<ItemStack> gatheredItems = new java.util.ArrayList<>();

        public void forceStopWork() {
            this.targetPos = null;
            this.miningTicks = 0;
            this.blocksChopped = 0;
            this.isDelivering = false;
            this.deliveryTicks = 0;
            this.vacuumWaitTicks = 0;
            entity.getNavigation().stop();
            entity.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        }

        public void forceDeliver() {
            // Force delivery of whatever is already gathered
            this.targetPos = null;
            this.miningTicks = 0;
            this.isDelivering = true;
            this.deliveryTicks = 0;
            this.vacuumWaitTicks = 15; // skip wait, go to player immediately
            entity.getNavigation().stop();
        }

        public void forceStartWork() {
            this.targetPos = findTargetBlock(true);
            if (this.targetPos == null) {
                this.targetPos = findTargetBlock(false);
            }
            if (this.targetPos != null) {
                entity.getNavigation().moveTo(targetPos.getX() + 0.5, targetPos.getY(), targetPos.getZ() + 0.5, 1.25D);
            } else {
                this.isWoodTrip = true;
                this.woodTripTicks = 0;
                poofParticles();
                entity.setInvisible(true);
            }
        }
        public VerityWorkGoal(VerityEntity entity) {
            this.entity = entity;
            this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            if (entity.level().isClientSide) return false;
            VerityPhase phase = entity.getVerityPhase();
            if (phase != VerityPhase.HELPER && phase != VerityPhase.OMNISCIENT) {
                return false;
            }

            if (this.askCooldown > 0) this.askCooldown--;
            if (this.woodTripCooldown > 0) this.woodTripCooldown--;

            Player player = entity.level().getNearestPlayer(entity, 16.0D);
            ItemStack mainHand = entity.getMainHandItem();
            boolean hasTool = !mainHand.isEmpty() && (mainHand.getItem() instanceof net.minecraft.world.item.DiggerItem);

            // 1. Wood Trip: Occasionally disappear to gather wood & return with logs
            if (player != null && this.woodTripCooldown == 0 && !hasTool && !isWoodTrip) {
                BlockPos nearbyTree = findTargetBlock(true);
                if (nearbyTree != null && entity.random.nextInt(3) == 0) {
                    this.isWoodTrip = true;
                    this.woodTripTicks = 0;
                    this.woodTripCooldown = 3600; // 3 minutes between trips
                    entity.sayHelperText("\u00a7e<Verity\u2122>\u00a7r Пойду наберу немного дерева нам!");
                    entity.setTalkAnimTick(30);
                    poofParticles();
                    entity.setInvisible(true);
                    return true;
                }
            }

            if (isWoodTrip) return true;

            // 2. Player mining detection: Offer to help!
            if (player != null && isPlayerMining(player)) {
                boolean playerHasAxe = player.getMainHandItem().getItem() instanceof net.minecraft.world.item.AxeItem;
                boolean playerHasPick = player.getMainHandItem().getItem() instanceof net.minecraft.world.item.PickaxeItem;

                if (!hasTool && this.askCooldown == 0) {
                    if (playerHasAxe) {
                        entity.sayHelperText("\u00a7e<Verity\u2122>\u00a7r Давай помогу! Дай топор.");
                        entity.setTalkAnimTick(30);
                        this.askCooldown = 600;
                        entity.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, new ItemStack(net.minecraft.world.item.Items.STONE_AXE));
                        hasTool = true;
                    } else if (playerHasPick) {
                        entity.sayHelperText("\u00a7e<Verity\u2122>\u00a7r Давай помогу! Дай кирку.");
                        entity.setTalkAnimTick(30);
                        this.askCooldown = 600;
                        entity.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, new ItemStack(net.minecraft.world.item.Items.STONE_PICKAXE));
                        hasTool = true;
                    }
                }
            }

            // 3. If Verity has a tool equipped, search for target block
            if (hasTool) {
                boolean hasAxe = mainHand.getItem() instanceof net.minecraft.world.item.AxeItem;
                if (hasAxe) {
                    targetPos = findTargetBlock(true);
                    isWood = true;
                } else {
                    targetPos = findTargetBlock(false);
                    isWood = false;
                }
                return targetPos != null;
            }

            return false;
        }

        private boolean isPlayerMining(Player player) {
            if (player.swinging) {
                net.minecraft.world.phys.HitResult hit = player.pick(5.0D, 0.0F, false);
                if (hit.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK) {
                    BlockPos pos = ((net.minecraft.world.phys.BlockHitResult) hit).getBlockPos();
                    BlockState state = entity.level().getBlockState(pos);
                    return isLogBlock(state) || isMiningBlock(state);
                }
            }
            return false;
        }

        private BlockPos findTargetBlock(boolean searchWood) {
            BlockPos center = entity.blockPosition();
            Level level = entity.level();
            BlockPos best = null;
            double bestDist = Double.MAX_VALUE;

            for (int dx = -12; dx <= 12; dx++) {
                for (int dy = -4; dy <= 10; dy++) {
                    for (int dz = -12; dz <= 12; dz++) {
                        BlockPos p = center.offset(dx, dy, dz);
                        BlockState state = level.getBlockState(p);
                        boolean match = searchWood ? isLogBlock(state) : isMiningBlock(state);
                        if (match) {
                            double d = entity.distanceToSqr(p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5);
                            if (d < bestDist) {
                                bestDist = d;
                                best = p;
                            }
                        }
                    }
                }
            }
            return best;
        }

        private boolean isLogBlock(BlockState state) {
            return state.is(net.minecraft.tags.BlockTags.LOGS) ||
                   (state.getBlock() instanceof net.minecraft.world.level.block.RotatedPillarBlock && state.is(net.minecraft.tags.BlockTags.MINEABLE_WITH_AXE));
        }

        private boolean isMiningBlock(BlockState state) {
            return state.is(net.minecraft.tags.BlockTags.MINEABLE_WITH_PICKAXE) ||
                   state.is(net.minecraft.tags.BlockTags.NEEDS_STONE_TOOL) ||
                   state.is(net.minecraft.tags.BlockTags.NEEDS_IRON_TOOL) ||
                   state.is(net.minecraft.tags.BlockTags.NEEDS_DIAMOND_TOOL) ||
                   state.is(net.minecraft.world.level.block.Blocks.STONE) ||
                   state.is(net.minecraft.world.level.block.Blocks.COBBLESTONE);
        }

        @Override
        public void start() {
            miningTicks = 0;
            blocksChopped = 0;
            isDelivering = false;
            deliveryTicks = 0;
            vacuumWaitTicks = 0;
            gatheredItems.clear();
            if (targetPos != null && !isWoodTrip) {
                entity.getNavigation().moveTo(targetPos.getX() + 0.5, targetPos.getY(), targetPos.getZ() + 0.5, 1.25D);
            }
        }

        @Override
        public boolean canContinueToUse() {
            if (isWoodTrip) return woodTripTicks < 140;
            if (isDelivering) return deliveryTicks < 100;
            if (targetPos == null) return false;
            BlockState state = entity.level().getBlockState(targetPos);
            boolean match = isWood ? isLogBlock(state) : isMiningBlock(state);
            return match && miningTicks < 120;
        }

        @Override
        public void tick() {
            if (isWoodTrip) {
                woodTripTicks++;
                if (woodTripTicks % 20 == 0) {
                    entity.level().playSound(null, entity.getX(), entity.getY(), entity.getZ(),
                            SoundEvents.WOOD_HIT, SoundSource.BLOCKS, 0.8F, 1.0F);
                }
                if (woodTripTicks >= 120) {
                    isWoodTrip = false;
                    entity.setInvisible(false);
                    poofParticles();

                    Player player = entity.level().getNearestPlayer(entity, 32.0D);
                    if (player != null) {
                        entity.teleportTo(player.getX() + player.getLookAngle().x * 2.0, player.getY(), player.getZ() + player.getLookAngle().z * 2.0);
                        poofParticles();
                        entity.spawnAtLocation(new ItemStack(net.minecraft.world.item.Items.OAK_LOG, 4 + entity.random.nextInt(5)));
                        entity.sayHelperText("\u00a7e<Verity\u2122>\u00a7r Смотри! Набрал для нас дерева!");
                        entity.setTalkAnimTick(35);
                    }
                    entity.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, ItemStack.EMPTY);
                }
                return;
            }

            // Vacuum nearby dropped items on every tick (8 block radius covers 6-block reach)
            if (entity.level() instanceof net.minecraft.server.level.ServerLevel slv) {
                var dropped = slv.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, entity.getBoundingBox().inflate(8.0D));
                for (var ie : dropped) {
                    ItemStack st = ie.getItem().copy();
                    if (!st.isEmpty()) {
                        gatheredItems.add(st);
                        ie.discard();
                        slv.playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.6F, 1.2F);
                    }
                }
            }

            if (isDelivering) {
                deliveryTicks++;
                // Wait a bit near broken blocks so items can fall and be vacuumed
                if (vacuumWaitTicks < 15) {
                    vacuumWaitTicks++;
                    return;
                }
                Player player = entity.level().getNearestPlayer(entity, 32.0D);
                if (player != null) {
                    double pDist = entity.distanceToSqr(player);
                    if (pDist > 9.0D && entity.getNavigation().isDone()) {
                        entity.getNavigation().moveTo(player.getX(), player.getY(), player.getZ(), 1.4D);
                    }
                    if (pDist <= 9.0D || deliveryTicks >= 80) {
                        entity.getNavigation().stop();
                        if (gatheredItems.isEmpty()) {
                            entity.spawnAtLocation(new ItemStack(net.minecraft.world.item.Items.OAK_LOG, 4));
                        } else {
                            for (ItemStack st : gatheredItems) {
                                entity.spawnAtLocation(st);
                            }
                            gatheredItems.clear();
                        }
                        entity.level().playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.8F, 1.0F);
                        entity.sayHelperText(isWood ? "\u00a7e<Verity\u2122>\u00a7r Держи! Нарубил нам дерева." : "\u00a7e<Verity\u2122>\u00a7r Держи! Накопал нам ресурсов.");
                        entity.setTalkAnimTick(35);
                        isDelivering = false;
                        entity.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, ItemStack.EMPTY);
                    }
                } else {
                    isDelivering = false;
                    gatheredItems.clear();
                    entity.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, ItemStack.EMPTY);
                }
                return;
            }

            if (targetPos == null) return;

            entity.getLookControl().setLookAt(targetPos.getX() + 0.5, targetPos.getY() + 0.5, targetPos.getZ() + 0.5, 30.0F, 30.0F);
            double distSqr = entity.distanceToSqr(targetPos.getX() + 0.5, targetPos.getY() + 0.5, targetPos.getZ() + 0.5);

            // Move toward target using ground nav (radius 6 blocks)
            if (distSqr > 36.0D) {
                if (entity.getNavigation().isDone()) {
                    entity.getNavigation().moveTo(targetPos.getX() + 0.5, targetPos.getY(), targetPos.getZ() + 0.5, 1.25D);
                }
            } else {
                entity.getNavigation().stop();
                miningTicks++;

                if (miningTicks % 4 == 0) {
                    entity.swing(InteractionHand.MAIN_HAND);
                    net.minecraft.sounds.SoundEvent hitSound = isWood ? SoundEvents.WOOD_HIT : SoundEvents.STONE_HIT;
                    entity.level().playSound(null, targetPos, hitSound, SoundSource.BLOCKS, 0.8F, 1.0F);

                    if (entity.level() instanceof net.minecraft.server.level.ServerLevel sl) {
                        sl.sendParticles(new net.minecraft.core.particles.BlockParticleOption(
                                net.minecraft.core.particles.ParticleTypes.BLOCK, entity.level().getBlockState(targetPos)),
                                targetPos.getX() + 0.5, targetPos.getY() + 0.5, targetPos.getZ() + 0.5,
                                6, 0.2, 0.2, 0.2, 0.05);
                    }
                }

                if (miningTicks >= 20) {
                    Level level = entity.level();
                    if (level instanceof net.minecraft.server.level.ServerLevel sl) {
                        sl.destroyBlock(targetPos, true, entity);
                    }
                    this.blocksChopped++;
                    miningTicks = 0;

                    BlockPos next = findTargetBlock(isWood);
                    if (next != null && this.blocksChopped < 16) {
                        this.targetPos = next;
                        entity.getNavigation().moveTo(next.getX() + 0.5, next.getY(), next.getZ() + 0.5, 1.25D);
                    } else {
                        this.targetPos = null;
                        this.isDelivering = true;
                        this.deliveryTicks = 0;
                    }
                }
            }
        }

        private void poofParticles() {
            if (entity.level() instanceof net.minecraft.server.level.ServerLevel sl) {
                sl.sendParticles(net.minecraft.core.particles.ParticleTypes.POOF,
                        entity.getX(), entity.getY() + 0.5, entity.getZ(),
                        15, 0.3, 0.3, 0.3, 0.05);
            }
        }

        @Override
        public void stop() {
            targetPos = null;
            miningTicks = 0;
            blocksChopped = 0;
            isDelivering = false;
            deliveryTicks = 0;
            vacuumWaitTicks = 0;
            gatheredItems.clear();
            if (isWoodTrip) {
                entity.setInvisible(false);
                isWoodTrip = false;
            }
            entity.getNavigation().stop();
            entity.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        }
    }

    /**
     * Цель убойных атак злого Verity на жителей и големов в деревне
     */
    static class VerityKillVillageGoal extends Goal {
        private final VerityEntity entity;
        private net.minecraft.world.entity.LivingEntity victim;

        public VerityKillVillageGoal(VerityEntity entity) {
            this.entity = entity;
            this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            VerityPhase p = entity.getVerityPhase();
            if (p != VerityPhase.MONSTER && p != VerityPhase.HUNTER) return false;
            var list = entity.level().getEntitiesOfClass(
                    net.minecraft.world.entity.LivingEntity.class,
                    entity.getBoundingBox().inflate(32.0D),
                    e -> e.isAlive() && (e instanceof net.minecraft.world.entity.npc.Villager || e instanceof net.minecraft.world.entity.animal.IronGolem)
            );
            if (list.isEmpty()) return false;
            this.victim = list.get(0);
            return true;
        }

        @Override
        public void start() {
            if (this.victim != null) {
                entity.getNavigation().moveTo(this.victim, 1.6D);
            }
        }

        @Override
        public void tick() {
            if (this.victim == null || !this.victim.isAlive()) return;
            entity.getLookControl().setLookAt(this.victim, 30.0F, 30.0F);
            entity.getNavigation().moveTo(this.victim, 1.6D);
            if (entity.distanceToSqr(this.victim) < 5.0D) {
                this.victim.hurt(entity.damageSources().mobAttack(entity), 500.0F);
                if (entity.level() instanceof net.minecraft.server.level.ServerLevel sl) {
                    sl.sendParticles(net.minecraft.core.particles.ParticleTypes.SMOKE, victim.getX(), victim.getY() + 1, victim.getZ(), 20, 0.5, 0.5, 0.5, 0.1);
                    sl.playSound(null, victim.getX(), victim.getY(), victim.getZ(), net.minecraft.sounds.SoundEvents.WITHER_SHOOT, net.minecraft.sounds.SoundSource.HOSTILE, 1.0F, 0.5F);
                }
                Player player = entity.level().getNearestPlayer(entity, 32.0D);
                if (player != null && entity.getRandom().nextInt(3) == 0) {
                    player.sendSystemMessage(Component.literal("§4<Verity>§r Они больше не помешают нам, " + player.getName().getString() + "."));
                }
            }
        }
    }
}
