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
import net.verity.entity.VerityEntity;

public class VerityEntityModel extends HierarchicalModel<VerityEntity> {
    private final ModelPart root;
    private final ModelPart sphere;

    // Load original Bedrock polygon meshes
    private static final BedrockMesh BALL_MESH = new BedrockMesh(
            "/assets/verity/models/verityball.geo.json", "ball", 0.0F, 8.0F, 0.0F, true, true
    );

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
    public void setupAnim(VerityEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        this.currentEntity = entity;
        this.age = ageInTicks;

        this.root().getAllParts().forEach(ModelPart::resetPose);

        // Rolling animation when moving
        float rollAngle = limbSwing * 1.5F;
        this.sphere.xRot = rollAngle;

        // Turn sphere to face target with the Bedrock mesh's side-facing texture corrected.
        this.sphere.yRot = (netHeadYaw - 90.0F) * ((float) Math.PI / 180F);
        this.sphere.xRot += headPitch * ((float) Math.PI / 180F);
    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer vertexConsumer, int packedLight, int packedOverlay, int color) {
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
