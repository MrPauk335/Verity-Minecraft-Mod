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

    // ─────── ФАЗЫ FSM (Finite State Machine) ───────────────────────────────
    public enum VerityPhase {
        DORMANT,     // 0 — в коробке, не активирован
        HELPER,      // 1 — дружелюбный помощник
        OMNISCIENT,  // 2 — всезнайка, вопросы о реальном мире
        COUNTDOWN,   // 3 — обратный отсчёт "3 дня", телекинез
        MONSTER,     // 4 — Monster Form, погоня
        POSSESSIVE,  // 5 — собственник, "You have me"
        HUNTER       // 6 — убийца Twixxel, устранение других игроков
    }

    // ─────── DATA TRACKERS (синхронизация сервер ↔ клиент) ────────────────
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

    // ─────── ГЛОБАЛЬНЫЙ ТРЕКЕР (быстрее, чем сканировать весь мир) ─────────
    /** Потокобезопасный набор всех живых VerityEntity на сервере. */
    public static final java.util.Set<VerityEntity> ACTIVE_VERITIES =
            java.util.Collections.synchronizedSet(
                    java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>()));

    // ─────── КОНСТАНТЫ СОСТОЯНИЙ ЛИЦА ──────────────────────────────────────
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

    // ─────── ВНУТРЕННЕЕ СОСТОЯНИЕ (серверное, не синхр.) ──────────────────
    private int chatCooldown = 0;
    private int teleportCooldown = 0;
    private int talkAnimTick = 0;
    private int ambientSoundCooldown = 0;
    private int introTicks = 0;
    private int introSquashTimer = 0;    // тики squash-эффекта после приземления
    private int ticksInPhase = 0;
    private int dayCounter = 0;          // для COUNTDOWN
    private float rollAngle = 0.0F;
    private float rollStrafe = 0.0F;     // боковой наклон при движении вбок
    private boolean nearbyMessageSent = false; // «Verity is nearby» перед Monster Form
    private int rageForgiveTicks = 0;    // тики ярости до прощения
    private int villagerEatCooldown = 0; // кулдаун проверки деревень
    private boolean emptyVillageMessageSent = false;
    private boolean hunterChasing = false; // HUNTER: преследует цель (для лица)

    // ─────── LLM-ИНТЕГРАЦИЯ ────────────────────────────────────────────────
    private VerityDialogueController dialogueController;

    public VerityDialogueController getDialogueController() {
        if (dialogueController == null) {
            dialogueController = new VerityDialogueController(this);
        }
        return dialogueController;
    }
    // ─────── КОНСТРУКТОР ────────────────────────────────────────────────────
    public VerityEntity(EntityType<? extends PathfinderMob> entityType, Level level) {
        super(entityType, level);
    }

    // ─────── АТРИБУТЫ ───────────────────────────────────────────────────────
    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 100.0)
                .add(Attributes.MOVEMENT_SPEED, 0.25)
                .add(Attributes.FOLLOW_RANGE, 64.0)
                .add(Attributes.ATTACK_DAMAGE, 0.0);
    }

    // ─────── РЕГИСТРАЦИЯ ЦЕЛЕЙ (Goals) ─────────────────────────────────────
    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new VerityFollowGoal(this, 1.2D));
        this.goalSelector.addGoal(2, new VerityStalkGoal(this, 0.8D));
        this.goalSelector.addGoal(3, new VerityMonsterAttackGoal(this, 1.5D));
        this.goalSelector.addGoal(4, new VerityOpenDoorGoal(this, 6.0D));
        this.goalSelector.addGoal(5, new LookAtPlayerGoal(this, Player.class, 10.0F));
    }

    // ─────── СИНХРОНИЗАЦИЯ ДАННЫХ ──────────────────────────────────────────
    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(VERITY_PHASE, VerityPhase.HELPER.ordinal());
        builder.define(FACE_INDEX, FACE_SMILE);
        builder.define(FACELESS, false);
        builder.define(IS_MONSTER_FORM, false);
        builder.define(INTRO_PHASE, 0);
    }


    // ─────── GETTERS / SETTERS ──────────────────────────────────────────────
    public VerityPhase getVerityPhase() {
        return VerityPhase.values()[this.entityData.get(VERITY_PHASE)];
    }

    public void setVerityPhase(VerityPhase phase) {
        VerityPhase old = getVerityPhase();
        this.entityData.set(VERITY_PHASE, phase.ordinal());
        this.ticksInPhase = 0;

        // Логика при входе в фазу
        onPhaseEnter(old, phase);
    }

    private void onPhaseEnter(VerityPhase oldPhase, VerityPhase newPhase) {
        switch (newPhase) {
            case MONSTER -> {
                setMonsterForm(true);
                this.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.35);
                this.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(20.0);
                this.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(64.0);
                if (!this.level().isClientSide) {
                    setFaceIndex(FACE_CREEPY_SMILE);
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

    public boolean isFaceless() {
        return this.entityData.get(FACELESS);
    }

    public void setFaceless(boolean v) {
        this.entityData.set(FACELESS, v);
    }

    public int getDayCounter() {
        return this.dayCounter;
    }

    /**
     * Дефолтное лицо для фазы (когда не говорит).
     * COUNTDOWN зависит от дня отсчёта.
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
     * Пара лиц для анимации разговора по фазе.
     * [0] = основное, [1] = альтернативное (говорит).
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

    // Совместимость со старым кодом (VerityMod.getInventoryItemForFace)
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

    // ─────── ВЗАИМОДЕЙСТВИЕ (ПКМ) ─────────────────────────────────────────
    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (this.level().isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (this.isFaceless()) {
            return InteractionResult.PASS;
        }
        if (getVerityPhase() == VerityPhase.MONSTER || getVerityPhase() == VerityPhase.HUNTER) {
            // В фазе монстра нельзя взять в инвентарь
            player.sendSystemMessage(Component.literal("§c<Verity>§r НЕТ."));
            return InteractionResult.FAIL;
        }

        // Даём инвентарный предмет с текущим лицом
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

        // Сохраняем данные Verity перед discard
        VerityMod.saveHeldData(getVerityPhase(), getDialogueController().getDialogueHistory(), getDialogueController().getKnownFactsList());

        this.discard();
        return InteractionResult.CONSUME;
    }

    // ─────── УРОН ──────────────────────────────────────────────────────────
    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (!this.level().isClientSide) {
            VerityPhase phase = getVerityPhase();

            // Иммунитет в Monster Form
            if (phase == VerityPhase.MONSTER || phase == VerityPhase.HUNTER) {
                return false;
            }

            // Лавовый инцидент или удар игрока → ярость → мгновенное прощение
            // «DON'T DO THAT! I THOUGHT WE WERE HAVING A NICE WALK!»
            if (source.getEntity() instanceof Player player
                    || source.is(net.minecraft.tags.DamageTypeTags.IS_FIRE)) {

                Player nearest = (source.getEntity() instanceof Player p) ? p
                        : this.level().getNearestPlayer(this, 32.0D);

                if (nearest != null) {
                    // Ярость — телепортируемся за спину
                    Vec3 rotVec = nearest.getViewVector(1.0F).normalize();
                    double tx = nearest.getX() - rotVec.x * 2.0;
                    double ty = nearest.getY();
                    double tz = nearest.getZ() - rotVec.z * 2.0;
                    this.teleportTo(tx, ty, tz);

                    nearest.sendSystemMessage(Component.literal(
                            "§4<Verity™>§r НЕ ДЕЛАЙ ЭТОГО!"));
                    nearest.sendSystemMessage(Component.literal(
                            "§4<Verity™>§r Я ДУМАЛ, МЫ ХОРОШО ГУЛЯЛИ! РАЗВЕ МЫ НЕ ХОРОШО ГУЛЯЛИ?"));

                    // Мгновенное прощение через 3 секунды (60 тиков) — запоминаем
                    this.rageForgiveTicks = 60;
                }
                return false; // Verity неуязвим
            }
        }
        return super.hurt(source, amount);
    }

    private void spawnMonsterBehind(Player player) {
        Vec3 rotationVec = player.getViewVector(1.0F).normalize();
        double spawnX = player.getX() - rotationVec.x * 3.0D;
        double spawnY = player.getY();
        double spawnZ = player.getZ() - rotationVec.z * 3.0D;

        VerityMonsterEntity monster = new VerityMonsterEntity(VerityMod.VERITY_MONSTER_ENTITY, this.level());
        monster.moveTo(spawnX, spawnY, spawnZ, player.getYRot(), player.getXRot());
        this.level().addFreshEntity(monster);
    }

    // ─────── ОСНОВНОЙ ТИК (FSM + поведения) ──────────────────────────────
    @Override
    public void tick() {
        // ── АНИМАЦИЯ ПОЯВЛЕНИЯ (suspend → drop → bounce) ──────────────────
        if (this.introTicks > 0) {
            this.introTicks--;

            // squash-таймер тикает независимо
            if (this.introSquashTimer > 0) this.introSquashTimer--;

            // ФАЗА 1 (ticks 60→40): висим в воздухе, кислое лицо
            if (this.introTicks > 40) {
                this.setNoGravity(true);
                this.setDeltaMovement(Vec3.ZERO);
                this.hurtMarked = true;
                if (!this.level().isClientSide) {
                    setFaceIndex(FACE_HURT);
                    this.entityData.set(INTRO_PHASE, 1);
                }

            // ФАЗА 2 (ticks 40→0): отпускаем, падаем
            } else if (this.introTicks == 40) {
                this.setNoGravity(false);
                if (!this.level().isClientSide) {
                    this.entityData.set(INTRO_PHASE, 2);
                }

            // ФАЗА 3 (падаем вниз, ищем приземление для отскока)
            } else if (this.introTicks > 0) {
                Vec3 vel = this.getDeltaMovement();
                if (this.onGround() && vel.y < -0.05) {
                    // Отскок
                    this.setDeltaMovement(vel.x * 0.3, -vel.y * 0.45, vel.z * 0.3);
                    if (!this.level().isClientSide) {
                        this.introSquashTimer = 8;
                        this.entityData.set(INTRO_PHASE, 3);
                    }
                }

            // introTicks == 0: анимация завершена, приветствие
            } else {
                this.setNoGravity(false);
                if (!this.level().isClientSide) {
                    this.entityData.set(INTRO_PHASE, 0);
                    setFaceIndex(FACE_SMILE);
                    this.talkAnimTick = 40;
                    this.chatCooldown = 600;
                    Player player = this.level().getNearestPlayer(this, 32.0D);
                    if (player != null) {
                    player.sendSystemMessage(Component.literal(
                            "§e<Verity>§r Привет! Я Верити, твой личный помощник-друг. Спрашивай что угодно — я знаю всё."));
                    }
                    setVerityPhase(VerityPhase.HELPER);
                }
            }

            super.tick();
            this.updateRollAngle();
            return;
        }

        super.tick();
        this.updateRollAngle();

        if (this.level().isClientSide) return;

        // Регистрируем себя в глобальном трекере (O(1) операция)
        ACTIVE_VERITIES.add(this);

        VerityPhase phase = getVerityPhase();
        this.ticksInPhase++;

        // ── Прощение после ярости (лавовый инцидент) ──
        if (this.rageForgiveTicks > 0) {
            this.rageForgiveTicks--;
            if (this.rageForgiveTicks == 0) {
                Player nearest = this.level().getNearestPlayer(this, 32.0D);
                if (nearest != null) {
                    nearest.sendSystemMessage(Component.literal(
                            "§e<Verity™>§r ...Всё хорошо. Я прощаю тебя. Я знаю, это было случайно."));
                }
            }
        }

        // ── Анимация разговора ──
        if (this.talkAnimTick > 0) {
            this.talkAnimTick--;
            if (this.talkAnimTick <= 0) {
                // Конец разговора — возвращаем дефолтное лицо по фазе
                setFaceIndex(getDefaultFaceForPhase(phase));
            } else {
                int currentFace = getFaceIndex();
                if (this.tickCount % 4 == 0) {
                    // Пара лиц для анимации разговора по фазе
                    int[] pair = getTalkPairForPhase(phase);
                    setFaceIndex(currentFace == pair[0] ? pair[1] : pair[0]);
                }
            }
        }

        // ── Телепорт "О, я тут" — догоняет игрока если убежал ──
        if (phase != VerityPhase.DORMANT && phase != VerityPhase.MONSTER) {
            tickCatchUpTeleport();
        }

        // ── Контекстные авто-реплики — только по событиям, не по таймеру ──
        if (phase != VerityPhase.DORMANT) {
            handleAutoDialogue();
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

    // ─────── ЛОГИКА ФАЗ ──────────────────────────────────────────────────

    private void tickHelper() {
        // Переход в OMNISCIENT через 2 минуты (2400 тиков)
        if (this.ticksInPhase > 2400) {
            Player nearest = this.level().getNearestPlayer(this, 32.0D);
            if (nearest != null) {
                nearest.sendSystemMessage(Component.literal("§e<Verity>§r Ты знаешь... я вижу больше, чем ты думаешь."));
            }
            setVerityPhase(VerityPhase.OMNISCIENT);
        }
    }

    private void tickOmniscient() {
        // OMNISCIENT: дружелюбный, но знает слишком много
        tickEmptyVillageDetection();

        // Переход в COUNTDOWN через ~3 минуты — Verity сам объявляет отсчёт
        if (this.ticksInPhase > 3600) {
            Player nearest = this.level().getNearestPlayer(this, 32.0D);
            if (nearest != null) {
                nearest.sendSystemMessage(Component.literal(
                        "§c<Verity™>§r Что-то грядёт. Через три дня."));
                nearest.sendSystemMessage(Component.literal(
                        "§c<Verity™>§r Что-то грядёт. Через три дня."));
            }
            setVerityPhase(VerityPhase.COUNTDOWN);
        }
    }

    private void tickCountdown() {
        // COUNTDOWN: обратный отсчёт 3 дня. Телекинез через VerityOpenDoorGoal.
        // Для теста: каждый «день» = 1200 тиков (1 минута)
        int ticksPerDay = 1200;
        int newDayCounter = this.ticksInPhase / ticksPerDay;

        if (newDayCounter > this.dayCounter) {
            this.dayCounter = newDayCounter;
            // Меняем лицо при смене дня
            setFaceIndex(getDefaultFaceForPhase(VerityPhase.COUNTDOWN));
            Player nearest = this.level().getNearestPlayer(this, 64.0D);
            switch (dayCounter) {
                 case 1 -> {
                     // День 1 — :| → abnormal shut. Повторяет дважды.
                     if (nearest != null) {
                         nearest.sendSystemMessage(Component.literal(
                                 "§c<Verity™>§r Что-то грядёт. Через три дня."));
                         nearest.sendSystemMessage(Component.literal(
                                 "§c<Verity™>§r Что-то грядёт. Через три дня."));
                     }
                 }
                 case 2 -> {
                     // День 2 — abnormal shut (уже установлен)
                     if (nearest != null) {
                         nearest.sendSystemMessage(Component.literal(
                                 "§c<Verity™>§r Два дня... ты мог остановить это."));
                     }
                 }
                 case 3 -> {
                     // День 3 — day2 shut → MONSTER
                     if (nearest != null) {
                         nearest.sendSystemMessage(Component.literal(
                                 "§4<Verity>§r Всё уже кончено. Ты мой!"));
                         nearest.sendSystemMessage(Component.literal(
                                 "§4<Verity>§r ...ТЫ МОЙ!"));
                     }
                    this.nearbyMessageSent = false;
                    setVerityPhase(VerityPhase.MONSTER);
                }
            }
        }

        // Поедаем жителей
        tickEmptyVillageDetection();
    }

    private void tickMonster() {
        // MONSTER FORM: преследует, запугивает — НЕ убивает основного игрока!
        // По лору: Verity хочет подчинения, а не смерти.

        // Отправляем «Verity is nearby» при первом входе в фазу
        if (!this.nearbyMessageSent) {
            this.nearbyMessageSent = true;
            Player nearest = this.level().getNearestPlayer(this, 64.0D);
            if (nearest != null) {
                nearest.sendSystemMessage(Component.literal("§4Верити рядом..."));
            }
        }

        // Тьма и механика прощения
        Player nearest = this.level().getNearestPlayer(this, 20.0D);
        if (nearest != null && nearest.isAlive()) {
            nearest.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 100, 0, false, false, true));

            // ПРОЩЕНИЕ: игрок присел рядом → Monster Form замирает → POSSESSIVE
            // «I came back for you, okay?» — подчинение через возврат
            if (nearest.isShiftKeyDown() && this.distanceToSqr(nearest) < 16.0D) {
                nearest.sendSystemMessage(Component.literal(
                        "§e<Verity™>§r ...Ты вернулся. Хорошо."));
                nearest.sendSystemMessage(Component.literal(
                        "§e<Verity™>§r Я прощаю тебя."));
                setVerityPhase(VerityPhase.POSSESSIVE);
                return;
            }
        }

        // Телепорт за спину (как в оригинале)
        if (nearest != null && this.distanceToSqr(nearest) > 16.0D) {
            if (this.teleportCooldown > 0) {
                this.teleportCooldown--;
            } else if (this.random.nextInt(60) == 0) {
                if (!isPlayerLookingAtMe(nearest)) {
                    teleportBehindPlayer(nearest);
                    this.teleportCooldown = 100;
                }
            }
        }
    }

    private void tickPossessive() {
        // POSSESSIVE: после примирения — снова «нормальный», дружелюбный, НО собственник.
        // Это ключевой момент лора: Verity ведёт себя как ни в чём не бывало!
        setMonsterForm(false);

        // Ревность: если второй игрок рядом → Verity начинает следить → HUNTER
        var otherPlayers = this.level().getEntitiesOfClass(
                net.minecraft.server.level.ServerPlayer.class,
                this.getBoundingBox().inflate(32.0D)
        );
        // Если рядом >1 игрока — один из них «Twixxel»
        if (otherPlayers.size() > 1) {
            Player main = this.level().getNearestPlayer(this, 32.0D);
            if (main != null) {
                main.sendSystemMessage(Component.literal(
                        "§e<Verity™>§r Почему. ...Нет причины искать других людей. У тебя есть я."));
                main.sendSystemMessage(Component.literal(
                        "§c<Verity™>§r Где он?"));
            }
            setVerityPhase(VerityPhase.HUNTER);
        }

        // Поедаем жителей в тени
        tickEmptyVillageDetection();
    }

    private void tickHunter() {
        // HUNTER: убивает «Twixxel» (других игроков), потом возвращается к основному.
        setMonsterForm(true);

        Player main = this.level().getNearestPlayer(this, 64.0D);

        // Атакуем ДРУГИХ игроков (не основного)
        var allPlayers = this.level().getEntitiesOfClass(Player.class, this.getBoundingBox().inflate(32.0D));
        Player twixxelTarget = null;
        for (Player p : allPlayers) {
            if (p != main) {
                twixxelTarget = p;
                break;
            }
        }

        if (twixxelTarget != null) {
            // Преследуем — лицо кричит (11)
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
            // Цели нет — спокойное лицо (5), как ни в чём не бывало
            if (this.hunterChasing) {
                this.hunterChasing = false;
                setFaceIndex(FACE_BORED_P2);
            }
            if (main != null && this.ticksInPhase > 200) {
                main.sendSystemMessage(Component.literal(
                        "§e<Verity™>§r ...Он не вернётся. Но у нас с тобой всё хорошо."));
                main.sendSystemMessage(Component.literal(
                        "§e<Verity™>§r Не волнуйся. Я здесь. Я всегда здесь."));
                setVerityPhase(VerityPhase.POSSESSIVE);
            }
        }
    }

    // ─────── ПОМОЩНИКИ ────────────────────────────────────────────────────

    /**
     * Детект пустой деревни — если рядом дома/кровати но нет жителей.
     * "Something hungry came through..."
     * Verity не ест жителей на глазах — деревни уже пустые.
     */
    private void tickEmptyVillageDetection() {
        if (!VerityConfig.villagerEatingEnabled()) return;
        if (this.villagerEatCooldown > 0) {
            this.villagerEatCooldown--;
            return;
        }
        this.villagerEatCooldown = 200; // каждые 10 сек

        Player nearest = this.level().getNearestPlayer(this, 48.0D);
        if (nearest == null) return;

        // Проверяем: есть ли жители рядом
        var villagers = this.level().getEntitiesOfClass(
                net.minecraft.world.entity.npc.Villager.class,
                nearest.getBoundingBox().inflate(48.0D));

        if (!villagers.isEmpty()) {
            emptyVillageMessageSent = false;
            return; // деревня не пустая
        }

        // Ищем признаки деревни: кровати и двери
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

        // 2+ деревенских маркеров и нет жителей = пустая деревня
        if (villageBlocks >= 2 && !emptyVillageMessageSent) {
            emptyVillageMessageSent = true;
            nearest.sendSystemMessage(Component.literal(
                    "\u00a7c<Verity\u2122>\u00a7r \u0427\u0442\u043e-\u0442\u043e \u0433\u043e\u043b\u043e\u0434\u043d\u043e\u0435 \u043f\u0440\u043e\u0448\u043b\u043e \u0437\u0434\u0435\u0441\u044c..."));
        }

        // Сброс когда игрок ушёл
        if (villageBlocks < 2) {
            emptyVillageMessageSent = false;
        }
    }

    /**
     * Контекстные авто-реплики — только когда есть реальный повод.
     * НЕ по таймеру — только по событиям.
     */
    private void handleAutoDialogue() {
        if (this.chatCooldown > 0) {
            this.chatCooldown--;
            return;
        }
        Player nearest = this.level().getNearestPlayer(this, 32.0D);
        if (nearest == null || !nearest.isAlive()) return;

        VerityPhase phase = getVerityPhase();
        boolean shouldSpeak = false;
        String contextHint = "";

        // Контекст 1: игрок получил урон
        if (nearest.getHealth() < nearest.getMaxHealth() * 0.5f) {
            shouldSpeak = true;
            contextHint = "Игрок ранен. Отреагируй с заботой.";
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
        // Контекст 4: COUNTDOWN — день сменялся
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
            this.chatCooldown = 1200; // 60 сек кулдаун
        } else {
            this.chatCooldown = 200; // проверяем каждые 10 сек
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
     * Догоняющий телепорт — если игрок убежал >32 блоков, Verity телепортируется
     * на ~10 блоков за спиной и говорит "О, я тут."
     */
    private void tickCatchUpTeleport() {
        // Не телепортироваться во время разговора — чтобы сообщения не налезали
        if (this.talkAnimTick > 0) return;
        if (this.chatCooldown > 580) return; // только что сказал что-то
        if (this.teleportCooldown > 0) {
            this.teleportCooldown--;
            return;
        }

        Player nearest = this.level().getNearestPlayer(this, 64.0D);
        if (nearest == null) return;

        double distSq = this.distanceToSqr(nearest);
        if (distSq < 1024.0D) return; // < 32 блоков — не нужно

        // Ищем точку на ~10 блоков за спиной игрока
        Vec3 lookVec = nearest.getViewVector(1.0F).normalize();
        double targetX = nearest.getX() - lookVec.x * 10.0D + (this.random.nextDouble() - 0.5D) * 2.0D;
        double targetY = nearest.getY();
        double targetZ = nearest.getZ() - lookVec.z * 10.0D + (this.random.nextDouble() - 0.5D) * 2.0D;

        // Проверяем что точка безопасна — не в стене
        BlockPos targetPos = BlockPos.containing(targetX, targetY, targetZ);
        BlockState feetState = this.level().getBlockState(targetPos);
        BlockState headState = this.level().getBlockState(targetPos.above());
        if (!feetState.isAir() && !feetState.canBeReplaced()) {
            // Точка в стене — пробуем найти воздух рядом
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

        // Телепортируемся
        this.teleportTo(targetX, targetY, targetZ);

        // Реплика в зависимости от фазы
        VerityPhase phase = getVerityPhase();
        String msg;
        if (phase == VerityPhase.COUNTDOWN || phase == VerityPhase.POSSESSIVE
                || phase == VerityPhase.HUNTER) {
            String[] creepyMsgs = {
                    "§e<Verity\u2122>§r Я тут.",
                    "§e<Verity\u2122>§r Не убегай.",
                    "§e<Verity\u2122>§r Я нашёл тебя.",
                    "§e<Verity\u2122>§r Куда ты собрался?"
            };
            msg = creepyMsgs[this.random.nextInt(creepyMsgs.length)];
        } else {
            String[] friendlyMsgs = {
                    "§e<Verity\u2122>§r О, я тут!",
                    "§e<Verity\u2122>§r Подожди меня!",
                    "§e<Verity\u2122>§r Эй, не так быстро!",
                    "§e<Verity\u2122>§r Я догнал!"
            };
            msg = friendlyMsgs[this.random.nextInt(friendlyMsgs.length)];
        }
        nearest.sendSystemMessage(Component.literal(msg));
        this.talkAnimTick = 30;

        this.teleportCooldown = 200; // 10 секунд
    }

    private void updateRollAngle() {
        Vec3 motion = this.getDeltaMovement();
        double hSpeedSqr = motion.x * motion.x + motion.z * motion.z;
        boolean moving = hSpeedSqr > 5.0E-5;

        if (moving) {
            // Угол между направлением движения и направлением взгляда
            double moveYaw = Math.atan2(-motion.x, motion.z); // направление движения
            double lookYaw  = Math.toRadians(this.getYRot());   // направление взгляда
            double relAngle  = moveYaw - lookYaw;
            float speed = (float) Math.sqrt(hSpeedSqr) * 20.0F; // нормализация скорости

            // forward = питч (кручение вперед/назад)
            float targetPitch  = speed *  (float) Math.cos(relAngle) * 2.5F;
            // strafe = боковой наклон
            float targetStrafe = speed * -(float) Math.sin(relAngle) * 2.5F;

            // Плавный lerp (15% приближение за тик)
            this.rollAngle  = this.rollAngle  + (targetPitch  - this.rollAngle)  * 0.18F;
            this.rollStrafe = this.rollStrafe + (targetStrafe - this.rollStrafe) * 0.18F;
        } else {
            // Затухание назад к 0
            this.rollAngle  *= 0.85F;
            this.rollStrafe *= 0.85F;
            if (Math.abs(this.rollAngle)  < 0.001F) this.rollAngle  = 0.0F;
            if (Math.abs(this.rollStrafe) < 0.001F) this.rollStrafe = 0.0F;
        }
    }

    // ─────── СОХРАНЕНИЕ В NBT (persistence) ────────────────────────────────
    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString("VerityPhase", getVerityPhase().name());
        tag.putInt("TicksInPhase", this.ticksInPhase);
        tag.putInt("DayCounter", this.dayCounter);
        tag.putInt("ChatCooldown", this.chatCooldown);
        tag.putInt("IntroTicks", this.introTicks);

        // Сохраняем историю диалога
        var dc = getDialogueController();
        var history = dc.getDialogueHistory();
        if (!history.isEmpty()) {
            var list = new net.minecraft.nbt.ListTag();
            for (String line : history) {
                list.add(net.minecraft.nbt.StringTag.valueOf(line));
            }
            tag.put("DialogueHistory", list);
        }

        // Сохраняем известные факты
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
        this.dayCounter = tag.getInt("DayCounter");
        this.chatCooldown = tag.getInt("ChatCooldown");
        this.introTicks = tag.getInt("IntroTicks");

        // Загружаем историю диалога
        if (tag.contains("DialogueHistory")) {
            var list = tag.getList("DialogueHistory", net.minecraft.nbt.Tag.TAG_STRING);
            var history = new java.util.ArrayList<String>();
            for (int i = 0; i < list.size(); i++) {
                history.add(list.getString(i));
            }
            getDialogueController().setDialogueHistory(history);
        }

        // Загружаем известные факты
        if (tag.contains("KnownFacts")) {
            var factList = tag.getList("KnownFacts", net.minecraft.nbt.Tag.TAG_STRING);
            var facts = new java.util.ArrayList<String>();
            for (int i = 0; i < factList.size(); i++) {
                facts.add(factList.getString(i));
            }
            getDialogueController().setKnownFacts(facts);
        }
    }

    // ─────── AI GOALS (внутренние классы) ──────────────────────────────────

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
            VerityPhase p = entity.getVerityPhase();
            if (p == VerityPhase.MONSTER || p == VerityPhase.HUNTER || p == VerityPhase.COUNTDOWN) {
                return false;
            }
            this.target = entity.level().getNearestPlayer(entity, 32.0D);
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
            this.target = entity.level().getNearestPlayer(entity, 32.0D);
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

            if (distSq < 100.0D) { // 10 блоков — слишком близко, отходит
                // Идти в противоположную сторону от игрока
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
            } else if (distSq > 400.0D) { // 20 блоков — слишком далеко, подходит
                entity.getNavigation().moveTo(target, speed);
                cooldown = 20;
            } else {
                // В оптимальной дистанции — просто смотрит
                entity.getNavigation().stop();
                entity.getLookControl().setLookAt(target, 30.0F, 30.0F);
            }
        }
    }

    /**
     * Погоня в MONSTER — НЕ убивает основного игрока, только пугает!
     * В HUNTER — атакует только «Twixxel» (других игроков).
     * По лору: Verity хочет подчинения, а не смерти основного игрока.
     */
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
            VerityPhase p = entity.getVerityPhase();
            if (p != VerityPhase.MONSTER) return false; // HUNTER управляется из tickHunter
            this.target = entity.level().getNearestPlayer(entity, 32.0D);
            return this.target != null;
        }

        @Override
        public void tick() {
            if (target == null) return;
            double distSq = entity.distanceToSqr(target);

            // Преследуем игрока — но НЕ атакуем!
            // Verity только запугивает, не убивает основного игрока
            if (distSq > 9.0D) {
                entity.getNavigation().moveTo(target, speed);
            } else {
                // Подошли вплотную — останавливаемся и смотрим (создаёт ужас)
                entity.getNavigation().stop();
                // Слабый knockback — отталкиваем игрока, но не убиваем
                if (entity.getRandom().nextInt(40) == 0) {
                    Vec3 dir = target.position().subtract(entity.position()).normalize();
                    target.setDeltaMovement(dir.x * 0.5, 0.3, dir.z * 0.5);
                }
            }

            entity.getLookControl().setLookAt(target, 30.0F, 30.0F);

            // Ломаем блоки на пути — имитация вылома окна/двери
            if (entity.horizontalCollision && entity.getRandom().nextInt(8) == 0) {
                BlockPos front = entity.blockPosition().relative(entity.getDirection());
                BlockState frontState = entity.level().getBlockState(front);
                float hardness = frontState.getDestroySpeed(entity.level(), front);
                if (hardness >= 0 && hardness < 50) { // не ломаем бедрок и обсидиан
                    entity.level().destroyBlock(front, false);
                }
            }
        }
    }

    /**
     * Открывание дверей (телекинез) — для COUNTDOWN
     */
    static class VerityOpenDoorGoal extends Goal {
        private final VerityEntity entity;
        private final double range;
        private int cooldown = 0;

        public VerityOpenDoorGoal(VerityEntity entity, double range) {
            this.entity = entity;
            this.range = range;
            this.setFlags(EnumSet.noneOf(Flag.class)); // не требует движения
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
            cooldown = 40; // каждые 2 сек

            // Ищем двери в радиусе
            BlockPos entityPos = entity.blockPosition();
            for (int dx = -(int) range; dx <= range; dx++) {
                for (int dz = -(int) range; dz <= range; dz++) {
                    for (int dy = -2; dy <= 2; dy++) {
                        BlockPos pos = entityPos.offset(dx, dy, dz);
                        if (entity.level().getBlockState(pos).getBlock() instanceof net.minecraft.world.level.block.DoorBlock) {
                            // "Открываем" дверь силой мысли
                            entity.level().blockEvent(pos, entity.level().getBlockState(pos).getBlock(), 1, 1);
                            return; // одну дверь за раз
                        }
                    }
                }
            }
        }
    }
}