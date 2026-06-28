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
import net.verity.client.VerityClientConfig;
import net.verity.entity.VerityEntity;

public class VerityEntityModel extends HierarchicalModel<VerityEntity> {
    private final ModelPart root;
    private final ModelPart sphere;
    // Load original Bedrock polygon meshes
    private static final BedrockMesh BALL_MESH = new BedrockMesh(
            "/assets/verity/models/verityball.geo.json", "ball", 0.0F, 8.0F, 0.0F, true, true);

    private VerityEntity currentEntity;
    private float age;

    public VerityEntityModel(ModelPart root) {
        this.root = root;
        this.sphere = root.getChild("sphere");
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
        float yawOffset = VerityClientConfig.getFaceYawOffsetDegrees();

        // roll pitch: forward/back rolling direction
        float rollAngle = entity.getRollAngle();
        // roll strafe: side-to-side lean when moving laterally
        float rollStrafe = entity.getRollStrafe();

        this.sphere.xRot = rollAngle - headPitch * ((float) Math.PI / 180F);
        this.sphere.yRot = (netHeadYaw + yawOffset) * ((float) Math.PI / 180F);
        // Lateral lean — applied as zRot (inverted: right movement → lean right)
        this.sphere.zRot = -rollStrafe;
    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer vertexConsumer, int packedLight, int packedOverlay,
            int color) {
        poseStack.pushPose();

        // Normal phase: render the original Bedrock sphere mesh
        this.sphere.translateAndRotate(poseStack);

        // Base scale
        float baseScale = 0.5F;
        poseStack.scale(baseScale, baseScale, baseScale);

        // ── Talk pulse animation when speaking ────────────────────────────
        if (this.currentEntity != null && this.currentEntity.isTalking()) {
            float talkSpeed = 0.4F;
            float pulse = Math.abs((float) Math.sin(this.age * Math.PI * talkSpeed));
            float sx = 1.0F - pulse * 0.20F;
            float sy = 1.0F + pulse * 0.45F;
            float sz = 1.0F - pulse * 0.20F;
            poseStack.scale(sx, sy, sz);
        }

        // ── Intro: hover bob when suspended in air (introPhase == 1) ─────
        if (this.currentEntity != null && this.currentEntity.getIntroPhase() == 1) {
            float bob = (float) Math.sin(this.age * 0.25) * 0.08F;
            poseStack.translate(0.0F, bob, 0.0F);
        }

        // ── Intro: squash/stretch bounce effect (introPhase == 3) ────────
        if (this.currentEntity != null && this.currentEntity.getIntroPhase() == 3) {
            int squashTick = this.currentEntity.getIntroSquashTimer();
            if (squashTick > 0) {
                // 0–8: squash on land, then spring back via lerp
                float t = squashTick / 8.0F; // 1.0 → 0.0
                // At impact (t=1): wide and flat. At recovery (t=0): normal (1,1,1)
                float squashX = 1.0F + t * 0.45F;   // wider
                float squashY = 1.0F - t * 0.50F;   // flatter
                float squashZ = 1.0F + t * 0.45F;
                poseStack.scale(squashX, squashY, squashZ);
            }
        }

        BALL_MESH.render(poseStack, vertexConsumer, packedLight, packedOverlay, color);

        poseStack.popPose();
    }
}