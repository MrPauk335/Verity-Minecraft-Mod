package net.verity.item;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.verity.VerityMod;
import net.verity.entity.VerityEntity;

public class VerityInventoryItem extends Item {
    private final int placedFace;
    private final int placedPhase;

    public VerityInventoryItem(Properties properties, int placedFace, int placedPhase) {
        super(properties);
        this.placedFace = placedFace;
        this.placedPhase = placedPhase;
    }

    public int getPlacedFace() {
        return placedFace;
    }

    public int getPlacedPhase() {
        return placedPhase;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        BlockPos spawnPos = context.getClickedPos().relative(context.getClickedFace());

        if (!level.getBlockState(spawnPos).getCollisionShape(level, spawnPos).isEmpty()) {
            return InteractionResult.FAIL;
        }

        if (!level.isClientSide) {
            VerityEntity entity = spawnVerity(level, player, Vec3.atBottomCenterOf(spawnPos));
            if (entity == null) return InteractionResult.FAIL;
            ItemStack stack = context.getItemInHand();
            if (player == null || !player.getAbilities().instabuild) {
                stack.shrink(1);
            }
        }

        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public net.minecraft.world.InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide) {
            return net.minecraft.world.InteractionResultHolder.success(player.getItemInHand(hand));
        }

        ItemStack stack = player.getItemInHand(hand);

        VerityEntity entity = spawnVerity(level, player, player.getEyePosition().add(player.getLookAngle().scale(1.5)));
        if (entity == null) {
            return net.minecraft.world.InteractionResultHolder.fail(stack);
        }

        entity.throwVerity(player);

        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }

        return net.minecraft.world.InteractionResultHolder.consume(stack);
    }

    private VerityEntity spawnVerity(Level level, Player player, Vec3 pos) {
        VerityEntity entity = new VerityEntity(VerityMod.VERITY_ENTITY, level);
        entity.setFaceIndex(this.placedFace);
        VerityEntity.VerityPhase savedPhase = VerityMod.getHeldPhase();
        entity.setVerityPhase(savedPhase);
        entity.moveTo(pos.x, pos.y, pos.z, player != null ? player.getYRot() : 0.0F, 0.0F);

        if (!level.noCollision(entity)) {
            return null;
        }

        level.addFreshEntity(entity);
        entity.getDialogueController().setDialogueHistory(VerityMod.getHeldHistory());
        entity.getDialogueController().setKnownFacts(VerityMod.getHeldFacts());

        return entity;
    }
}
