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
import net.minecraft.world.phys.Vec3;
import net.verity.client.config.VerityClientConfig;
import net.verity.entity.VerityEntity;

import net.minecraft.client.model.ArmedModel;
import net.minecraft.world.entity.HumanoidArm;

public class VerityEntityModel extends HierarchicalModel<VerityEntity> implements ArmedModel {
    private final ModelPart root;
    private final ModelPart sphere;
    // Load original Bedrock polygon meshes
    private static final BedrockMesh BALL_MESH = new BedrockMesh(
            "/assets/verity/models/verityball.geo.json", "ball", 0.0F, 8.0F, 0.0F, true, true);
    private static BedrockMesh MONSTER_MESH = null;
    private static boolean monsterMeshLoaded = false;
    private static boolean monsterMeshFailed = false;

    private static BedrockMesh getMonsterMesh() {
        if (monsterMeshLoaded) return MONSTER_MESH;
        if (monsterMeshFailed) return null;
        monsterMeshLoaded = true;
        try {
            MONSTER_MESH = new BedrockMesh(
                    "/assets/verity/models/verity_monster.geo.json", "bb_main", 0.0F, 0.0F, 0.0F, true, true);
            net.verity.VerityMod.LOGGER.info("Verity monster mesh loaded successfully: {} polygons", 
                    MONSTER_MESH != null ? "OK" : "NULL");
        } catch (Throwable e) {
            monsterMeshFailed = true;
            net.verity.VerityMod.LOGGER.error("Failed to load monster mesh: {}", e.getMessage());
            e.printStackTrace();
        }
        return MONSTER_MESH;
    }

    private VerityEntity currentEntity;
    private float age;

    public VerityEntityModel(ModelPart root) {
        this.root = root;
        this.sphere = root.getChild("sphere");
    }

    public ModelPart getSphere() {
        return this.sphere;
    }

    public static LayerDefinition getTexturedModelData() {
        MeshDefinition meshDefinition = new MeshDefinition();
        PartDefinition rootDefinition = meshDefinition.getRoot();

        // Create an empty sphere bone for transformations
        rootDefinition.addOrReplaceChild("sphere", CubeListBuilder.create(),
                PartPose.offset(0.0F, 24.0F - 4.0F, 0.0F) // sphere radius is 0.25 blocks (4 units) at scale 0.5
        );

        return LayerDefinition.create(meshDefinition, 64, 64);
    }

    @Override
    public ModelPart root() {
        return this.root;
    }

    @Override
    public void setupAnim(VerityEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks,
            float netHeadYaw, float headPitch) {
        this.currentEntity = entity;
        this.age = ageInTicks;

        this.root().getAllParts().forEach(ModelPart::resetPose);

        // yaw: Bedrock mesh "forward" needs an offset relative to Java's look yaw
        float yawOffset = VerityClientConfig.faceYawOffsetDegrees();

        // roll pitch: forward/back rolling direction
        float rollAngle = entity.getRollAngle();
        // roll strafe: side-to-side lean when moving laterally
        float rollStrafe = entity.getRollStrafe();

        // Ball has no head — pitch/look doesn't affect a sphere
        this.sphere.xRot = rollAngle - (headPitch * ((float) Math.PI / 180F));
        this.sphere.yRot = (netHeadYaw + yawOffset) * ((float) Math.PI / 180F);
        // Lateral lean — applied as zRot (inverted: right movement → lean right)
        this.sphere.zRot = -rollStrafe;
    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer vertexConsumer, int packedLight, int packedOverlay,
            int color) {
        poseStack.pushPose();

        boolean isMonster = this.currentEntity != null && this.currentEntity.isMonsterForm();

        BedrockMesh monsterMesh = getMonsterMesh();
        if (isMonster && monsterMesh != null) {
            // ── MONSTER FORM: 3D-модель монстра ──
            // Bedrock model: Y is up, pivot at feet
            poseStack.translate(0.0F, 1.5F, 0.0F); // Minecraft model origin is 1.5 above ground
            poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(180.0F)); // Bedrock faces backwards
            float monsterScale = 2.0F;
            poseStack.scale(monsterScale, monsterScale, monsterScale);
            monsterMesh.render(poseStack, vertexConsumer, packedLight, packedOverlay, color);
        } else {
            // ── NORMAL FORM: шар ──
            // Pure rolling along ground, no vertical hopping/jumping while rolling
            if (this.currentEntity != null) {
                // Subtle idle bob when completely stationary
                float hSpeed = (float) this.currentEntity.getDeltaMovement().horizontalDistance();
                if (hSpeed < 0.01F && !this.currentEntity.isThrown()) {
                    float idleBob = (float) Math.sin(this.age * 0.12F) * 0.03F;
                    poseStack.translate(0.0F, idleBob, 0.0F);
                }
            }

            this.sphere.translateAndRotate(poseStack);

            float baseScale = 0.5F;
            poseStack.scale(baseScale, baseScale, baseScale);

            // Talk pulse
            if (this.currentEntity != null && this.currentEntity.isTalking()) {
                float talkSpeed = 0.4F;
                float pulse = Math.abs((float) Math.sin(this.age * Math.PI * talkSpeed));
                poseStack.scale(1.0F - pulse * 0.20F, 1.0F + pulse * 0.45F, 1.0F - pulse * 0.20F);
            }

            // Intro hover bob
            if (this.currentEntity != null && this.currentEntity.getIntroPhase() == 1) {
                float bob = (float) Math.sin(this.age * 0.25) * 0.08F;
                poseStack.translate(0.0F, bob, 0.0F);
            }

            // Intro squash
            if (this.currentEntity != null && this.currentEntity.getIntroPhase() == 3) {
                int squashTick = this.currentEntity.getIntroSquashTimer();
                if (squashTick > 0) {
                    float t = squashTick / 8.0F;
                    poseStack.scale(1.0F + t * 0.45F, 1.0F - t * 0.50F, 1.0F + t * 0.45F);
                }
            }

            // Ball physics squash/stretch on impact & fall
            if (this.currentEntity != null) {
                float st = this.currentEntity.getSquashTimer();
                if (st > 0) {
                    float decay = st / 6.0F;
                    float s = this.currentEntity.getSquashAmount() * decay;
                    // Horizontal squash & vertical compress on ground contact
                    poseStack.scale(1.0F + s * 1.2F, 1.0F - s * 0.8F, 1.0F + s * 1.2F);
                } else if (this.currentEntity.isThrown() || !this.currentEntity.onGround()) {
                    Vec3 vel = this.currentEntity.getDeltaMovement();
                    if (vel.y < -0.15) {
                        float stretch = (float) Math.min(-vel.y * 0.15, 0.25);
                        poseStack.scale(1.0F - stretch, 1.0F + stretch, 1.0F - stretch);
                    }
                }
            }

            BALL_MESH.render(poseStack, vertexConsumer, packedLight, packedOverlay, color);
        }

        poseStack.popPose();
    }

    @Override
    public void translateToHand(HumanoidArm arm, PoseStack poseStack) {
        this.root().translateAndRotate(poseStack);
        this.sphere.translateAndRotate(poseStack);
        float side = (arm == HumanoidArm.RIGHT) ? 0.42F : -0.42F;
        poseStack.translate(side, 0.65F, -0.15F);
        poseStack.mulPose(com.mojang.math.Axis.XP.rotationDegrees(-25.0F));
        poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(15.0F));
        poseStack.scale(0.75F, 0.75F, 0.75F);
    }
}