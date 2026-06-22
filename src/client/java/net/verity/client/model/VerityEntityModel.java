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
                PartPose.offset(0.0F, 24.0F - 3.2F, 0.0F) // sphere radius is 0.2 blocks (3.2 units) when scaled by 0.4
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

        // roll: a decaying angle owned by the entity (updated in tick(), not here).
        // It grows while the ball is rolling/moving and decays back to 0 when idle,
        // so the face naturally returns to facing the look direction over time.
        // NOTE: this is already in the same "natural" (radian-scale) units the old
        // limbSwing-based value used — do NOT multiply by Math.PI/180F here, that
        // would shrink the roll ~57x and make it visually disappear.
        float rollAngle = entity.getRollAngle();

        // pitch: Bedrock mesh's vertical face direction is flipped relative to
        // Java's look pitch, hence the negation.
        this.sphere.xRot = rollAngle - headPitch * ((float) Math.PI / 180F);
        this.sphere.yRot = (netHeadYaw + yawOffset) * ((float) Math.PI / 180F);
        this.sphere.zRot = 0.0F;
    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer vertexConsumer, int packedLight, int packedOverlay,
            int color) {
        poseStack.pushPose();

        // Normal phase: render the original Bedrock sphere mesh
        this.sphere.translateAndRotate(poseStack);

        // Scale sphere by 0.4 (matching Bedrock scale: 0.4)
        poseStack.scale(0.4F, 0.4F, 0.4F);

        // Implementing original Bedrock's talk_pulse animation when speaking
        if (this.currentEntity != null && this.currentEntity.isTalking()) {
            float talkSpeed = 0.4F;
            float pulse = Math.abs((float) Math.sin(this.age * Math.PI * talkSpeed));
            float sx = 1.0F - pulse * 0.20F;
            float sy = 1.0F + pulse * 0.45F;
            float sz = 1.0F - pulse * 0.20F;
            poseStack.scale(sx, sy, sz);
        }

        BALL_MESH.render(poseStack, vertexConsumer, packedLight, packedOverlay, color);

        poseStack.popPose();
    }
}