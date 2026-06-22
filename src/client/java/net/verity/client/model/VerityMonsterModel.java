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
    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer vertexConsumer, int packedLight, int packedOverlay, int color) {
        poseStack.pushPose();

        // Ground the model (Y=0 is feet, Y=1.5 is Minecraft's ModelPart rendering origin relative to ground)
        poseStack.translate(0.0F, 1.5F, 0.0F);
        // Face the player (180 degrees flip around Y since Bedrock meshes look back relative to Java)
        poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(180.0F));
        // Scale monster to 2.0x (12 blocks tall in world)
        poseStack.scale(2.0F, 2.0F, 2.0F);

        MONSTER_MESH.render(poseStack, vertexConsumer, packedLight, packedOverlay, color);

        poseStack.popPose();
    }
}
