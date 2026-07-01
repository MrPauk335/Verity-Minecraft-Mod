package net.verity.client.model;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.verity.entity.VerityMonsterEntity;

public class VerityMonsterModel extends HierarchicalModel<VerityMonsterEntity> {
    private final ModelPart root;
    private final ModelPart base;

    // Fields to store animation states
    private float limbSwing;
    private float limbSwingAmount;
    private float ageInTicks;
    private float netHeadYaw;
    private float headPitch;

    private static final BedrockMesh MONSTER_MESH = new BedrockMesh(
            "/assets/verity/models/verity_monster.geo.json", "bb_main", 0.0F, 0.0F, 0.0F, true, true
    );

    public VerityMonsterModel(ModelPart root) {
        this.root = root;
        this.base = root.getChild("base");
    }

    public static LayerDefinition getTexturedModelData() {
        MeshDefinition meshDefinition = new MeshDefinition();
        PartDefinition rootDefinition = meshDefinition.getRoot();

        // Empty base bone
        rootDefinition.addOrReplaceChild("base", CubeListBuilder.create(), PartPose.ZERO);

        return LayerDefinition.create(meshDefinition, 64, 64);
    }

    @Override
    public ModelPart root() {
        return this.root;
    }

    @Override
    public void setupAnim(VerityMonsterEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        this.root().getAllParts().forEach(ModelPart::resetPose);
        this.limbSwing = limbSwing;
        this.limbSwingAmount = limbSwingAmount;
        this.ageInTicks = ageInTicks;
        this.netHeadYaw = netHeadYaw;
        this.headPitch = headPitch;
    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer vertexConsumer, int packedLight, int packedOverlay, int color) {
        poseStack.pushPose();

        // Ground the model (Y=0 is feet, Y=1.5 is Minecraft's ModelPart rendering origin relative to ground)
        poseStack.translate(0.0F, 1.5F, 0.0F);
        // Face the player (180 degrees flip around Y since Bedrock meshes look back relative to Java)
        poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(180.0F));

        // ── Idle breathing & slow vertical bobbing ──
        float idleBob = (float)Math.sin(this.ageInTicks * 0.06F) * 0.05F;
        poseStack.translate(0.0F, idleBob, 0.0F);

        // ── Slight chest expansion (breathing) ──
        float breatheScale = 1.0F + (float)Math.sin(this.ageInTicks * 0.06F) * 0.015F;
        poseStack.scale(breatheScale, 1.0F, breatheScale);

        // ── Head / Torso looking behavior (smooth rotation towards player) ──
        // Only look towards target player within reasonable angles (up to 45 degrees body turn)
        float bodyYaw = Math.max(-45.0F, Math.min(45.0F, this.netHeadYaw)) * 0.4F;
        float bodyPitch = Math.max(-30.0F, Math.min(30.0F, this.headPitch)) * 0.3F;
        poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(bodyYaw));
        poseStack.mulPose(com.mojang.math.Axis.XP.rotationDegrees(bodyPitch));

        // ── Lurching walk cycle & footstep impact ──
        if (this.limbSwingAmount > 0.01F) {
            // Forward lean during movement
            float forwardLean = this.limbSwingAmount * 12.0F;
            poseStack.mulPose(com.mojang.math.Axis.XP.rotationDegrees(forwardLean));

            // Side-to-side heavy wobbling (lurching step)
            float wobble = (float)Math.sin(this.limbSwing * 0.7F) * this.limbSwingAmount * 6.0F;
            poseStack.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(wobble));

            // Vertical walk bounce (downwards on foot impact)
            float walkBounce = Math.abs((float)Math.sin(this.limbSwing * 0.7F)) * this.limbSwingAmount * -0.15F;
            poseStack.translate(0.0F, walkBounce, 0.0F);
        }

        // ── Glitchy shaking / twitching (Horror analog aesthetic) ──
        // High frequency vibration
        float jitterX = (float)Math.sin(this.ageInTicks * 1.6F) * 0.012F;
        float jitterZ = (float)Math.cos(this.ageInTicks * 1.8F) * 0.012F;
        poseStack.translate(jitterX, 0.0F, jitterZ);

        // Random fast glitches (sudden brief displacement or rotation)
        float glitchPhase = (float)Math.sin(this.ageInTicks * 0.05F);
        if (glitchPhase > 0.94F) {
            // Sudden shift
            float glitchShiftX = (float)Math.sin(this.ageInTicks * 2.5F) * 0.08F;
            float glitchShiftZ = (float)Math.cos(this.ageInTicks * 3.0F) * 0.08F;
            poseStack.translate(glitchShiftX, 0.0F, glitchShiftZ);

            // Brief twitch angle
            float twitchAngle = (float)Math.sin(this.ageInTicks * 4.0F) * 4.0F;
            poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(twitchAngle));
        }

        // Scale monster to 2.0x (12 blocks tall in world)
        poseStack.scale(2.0F, 2.0F, 2.0F);

        MONSTER_MESH.render(poseStack, vertexConsumer, packedLight, packedOverlay, color);

        poseStack.popPose();
    }
}
