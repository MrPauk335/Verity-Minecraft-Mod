package net.verity.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.verity.client.model.BedrockMesh;
import org.joml.Matrix4f;

public class VerityItemRenderer implements BuiltinItemRenderer {

    private static final BedrockMesh BALL_MESH = new BedrockMesh(
            "/assets/verity/models/verityball.geo.json", "ball", 0.0F, 8.0F, 0.0F, false, true);

    private final ResourceLocation tex3d;
    private final ResourceLocation tex2d;

    public VerityItemRenderer(ResourceLocation tex3d, ResourceLocation tex2d) {
        this.tex3d = tex3d;
        this.tex2d = tex2d;
    }

    @Override
    public void render(ItemStack stack, PoseStack poseStack,
                       MultiBufferSource bufferSource,
                       int packedLight, int packedOverlay) {
        Matrix4f mat = poseStack.last().pose();
        float scaleX = (float) Math.sqrt(mat.m00() * mat.m00() + mat.m10() * mat.m10() + mat.m20() * mat.m20());

        if (scaleX > 5f) {
            render2D(poseStack, bufferSource, packedLight, packedOverlay);
        } else {
            render3D(poseStack, bufferSource, packedLight, packedOverlay);
        }
    }

    private void render2D(PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        VertexConsumer consumer = bufferSource.getBuffer(RenderType.entityCutoutNoCull(tex2d));
        poseStack.pushPose();

        org.joml.Vector3f normal = new org.joml.Vector3f(0, 0, 1);
        poseStack.last().normal().transform(normal);

        var pose = poseStack.last().pose();
        float nx = normal.x(), ny = normal.y(), nz = normal.z();

        consumer.addVertex(pose, 0, 0, 0)
                .setColor(255, 255, 255, 255).setUv(0, 1)
                .setOverlay(packedOverlay).setLight(packedLight)
                .setNormal(nx, ny, nz);
        consumer.addVertex(pose, 1, 0, 0)
                .setColor(255, 255, 255, 255).setUv(1, 1)
                .setOverlay(packedOverlay).setLight(packedLight)
                .setNormal(nx, ny, nz);
        consumer.addVertex(pose, 1, 1, 0)
                .setColor(255, 255, 255, 255).setUv(1, 0)
                .setOverlay(packedOverlay).setLight(packedLight)
                .setNormal(nx, ny, nz);
        consumer.addVertex(pose, 0, 1, 0)
                .setColor(255, 255, 255, 255).setUv(0, 0)
                .setOverlay(packedOverlay).setLight(packedLight)
                .setNormal(nx, ny, nz);

        poseStack.popPose();
    }

    // Анимация говорения в руках — синхронно с аудио
    private static volatile boolean isPlaying = false;

    public static void setTalking(boolean talking) {
        isPlaying = talking;
    }

    public static boolean isTalking() {
        return isPlaying;
    }

    private void render3D(PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        poseStack.pushPose();
        poseStack.translate(0.5f, 0.5f, 0.5f);

        ResourceLocation faceTex = tex3d;
        boolean mouthOpen = false;

        if (isPlaying) {
            long t = System.currentTimeMillis();

            // Squish — пульсация пока говорит
            float pulse = (float) Math.sin(t * 0.008f) * 0.04f;
            poseStack.scale(1.0f - pulse, 1.0f + pulse, 1.0f - pulse);

            long cycle = t % 600;
            if (cycle < 200) {
                mouthOpen = true;
            } else if (cycle < 280) {
                mouthOpen = false;
            } else if (cycle < 450) {
                mouthOpen = true;
            } else {
                mouthOpen = false;
            }

            long bigCycle = t % 3000;
            if (bigCycle > 2400) {
                mouthOpen = false;
            }

            if (mouthOpen) {
                if (tex3d.equals(TEX3D_SMILE)) {
                    faceTex = TEX3D_SPEAK;
                } else if (tex3d.equals(TEX3D_BORED)) {
                    faceTex = TEX3D_DAY2_OPEN;
                } else if (tex3d.equals(TEX3D_ABNORMAL_SHUT) || tex3d.equals(TEX3D_ABNORMAL_OPEN)) {
                    faceTex = TEX3D_ABNORMAL_OPEN;
                }
            } else {
                if (tex3d.equals(TEX3D_ABNORMAL_OPEN) || tex3d.equals(TEX3D_ABNORMAL_SHUT)) {
                    faceTex = TEX3D_ABNORMAL_SHUT;
                }
            }
        }

        poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(180f));
        poseStack.mulPose(com.mojang.math.Axis.XP.rotationDegrees(-15f));

        var consumer = bufferSource.getBuffer(RenderType.entityCutout(faceTex));
        BALL_MESH.render(poseStack, consumer, packedLight, packedOverlay, 0xFFFFFFFF);
        poseStack.popPose();
    }

    public static final ResourceLocation TEX3D_SMILE =
            ResourceLocation.parse("verity:textures/entity/verity_face_smile.png");
    public static final ResourceLocation TEX3D_SPEAK =
            ResourceLocation.parse("verity:textures/entity/verity_face_speak.png");
    public static final ResourceLocation TEX3D_BORED =
            ResourceLocation.parse("verity:textures/entity/verity_face_bored_p2.png");
    public static final ResourceLocation TEX3D_DAY2_OPEN =
            ResourceLocation.parse("verity:textures/entity/verity_face_day2_open.png");
    public static final ResourceLocation TEX3D_ABNORMAL_SHUT =
            ResourceLocation.parse("verity:textures/entity/verity_face_abnormal_shut.png");
    public static final ResourceLocation TEX3D_ABNORMAL_OPEN =
            ResourceLocation.parse("verity:textures/entity/verity_face_abnormal_open.png");

    public static final ResourceLocation TEX2D_1 =
            ResourceLocation.parse("verity:textures/item/verity_inventory_1.png");
    public static final ResourceLocation TEX2D_2 =
            ResourceLocation.parse("verity:textures/item/verity_inventory_2.png");
    public static final ResourceLocation TEX2D_3 =
            ResourceLocation.parse("verity:textures/item/verity_inventory_3.png");
}