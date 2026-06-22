package net.verity.item;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
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
            VerityEntity entity = new VerityEntity(VerityMod.VERITY_ENTITY, level);
            entity.setFaceIndex(this.placedFace);
            entity.setPhase(this.placedPhase);
            Vec3 pos = Vec3.atBottomCenterOf(spawnPos);
            entity.moveTo(pos.x, pos.y, pos.z, player != null ? player.getYRot() : 0.0F, 0.0F);

            if (!level.noCollision(entity)) {
                return InteractionResult.FAIL;
            }

            level.addFreshEntity(entity);
            level.playSound(null, spawnPos, VerityMod.SOUND_ASKME, SoundSource.NEUTRAL, 0.8F, 1.0F);

            ItemStack stack = context.getItemInHand();
            if (player == null || !player.getAbilities().instabuild) {
                stack.shrink(1);
            }
        }

        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
