package net.verity.client.model;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;
import net.verity.entity.VerityMonsterEntity;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class VerityMonsterModel extends HierarchicalModel<VerityMonsterEntity> {
    private final ModelPart root;
    private final ModelPart base;

    private float limbSwing;
    private float limbSwingAmount;
    private float ageInTicks;
    private float netHeadYaw;
    private float headPitch;

    // Load original unified high-poly mesh
    private static final BedrockMesh MONSTER_MESH = new BedrockMesh("/assets/verity/models/verity_monster.geo.json", "bb_main", 0.0F, 0.0F, 0.0F, true, true);

    // Mixamo Keyframe Animation Data
    private static float animLength = 0.367F;
    private static final Map<String, List<Keyframe>> mixamoBoneAnims = new HashMap<>();
    private static boolean animLoaded = false;

    private static record Keyframe(float time, float rx, float ry, float rz) {}

    private static void loadMixamoAnimation() {
        if (animLoaded) return;
        animLoaded = true;
        try (InputStream stream = VerityMonsterModel.class.getResourceAsStream("/assets/verity/models/verity_monster.animation.json")) {
            if (stream == null) return;
            JsonObject rootObj = new Gson().fromJson(new InputStreamReader(stream, StandardCharsets.UTF_8), JsonObject.class);
            JsonObject anims = rootObj.getAsJsonObject("animations");
            if (anims == null) return;
            JsonObject runAnim = anims.getAsJsonObject("animation.verity_monster.run");
            if (runAnim == null) return;

            if (runAnim.has("animation_length")) {
                animLength = runAnim.get("animation_length").getAsFloat();
            }

            JsonObject bonesObj = runAnim.getAsJsonObject("bones");
            if (bonesObj != null) {
                for (Map.Entry<String, JsonElement> entry : bonesObj.entrySet()) {
                    String boneName = entry.getKey();
                    JsonObject bData = entry.getValue().getAsJsonObject();
                    if (bData.has("rotation")) {
                        JsonObject rotObj = bData.getAsJsonObject("rotation");
                        List<Keyframe> kfs = new ArrayList<>();
                        for (Map.Entry<String, JsonElement> kfEntry : rotObj.entrySet()) {
                            float time = Float.parseFloat(kfEntry.getKey());
                            JsonArray rotArr = kfEntry.getValue().getAsJsonArray();
                            float rx = rotArr.get(0).getAsFloat();
                            float ry = rotArr.get(1).getAsFloat();
                            float rz = rotArr.get(2).getAsFloat();
                            kfs.add(new Keyframe(time, rx, ry, rz));
                        }
                        kfs.sort(Comparator.comparingDouble(Keyframe::time));
                        mixamoBoneAnims.put(boneName, kfs);
                    }
                }
            }
            net.verity.VerityMod.LOGGER.info("Mixamo animation loaded: {} bones", mixamoBoneAnims.size());
        } catch (Throwable e) {
            net.verity.VerityMod.LOGGER.error("Failed to load Mixamo animation: {}", e.getMessage());
        }
    }

    public VerityMonsterModel(ModelPart root) {
        this.root = root;
        this.base = root.getChild("base");
        loadMixamoAnimation();
    }

    public static LayerDefinition getTexturedModelData() {
        MeshDefinition meshDefinition = new MeshDefinition();
        PartDefinition rootDefinition = meshDefinition.getRoot();
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

    private float[] sampleBoneRotation(String boneName, float animTime) {
        List<Keyframe> kfs = mixamoBoneAnims.get(boneName);
        if (kfs == null || kfs.isEmpty()) return new float[]{0, 0, 0};
        if (kfs.size() == 1) return new float[]{kfs.get(0).rx(), kfs.get(0).ry(), kfs.get(0).rz()};

        Keyframe prev = kfs.get(0);
        Keyframe next = kfs.get(kfs.size() - 1);
        for (int i = 0; i < kfs.size() - 1; i++) {
            if (animTime >= kfs.get(i).time() && animTime <= kfs.get(i + 1).time()) {
                prev = kfs.get(i);
                next = kfs.get(i + 1);
                break;
            }
        }
        float dt = next.time() - prev.time();
        float factor = dt > 0.0001F ? (animTime - prev.time()) / dt : 0.0F;
        float rx = Mth.lerp(factor, prev.rx(), next.rx());
        float ry = Mth.lerp(factor, prev.ry(), next.ry());
        float rz = Mth.lerp(factor, prev.rz(), next.rz());
        return new float[]{rx, ry, rz};
    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer vertexConsumer, int packedLight, int packedOverlay, int color) {
        poseStack.pushPose();

        // Ground the model
        poseStack.translate(0.0F, 1.5F, 0.0F);
        poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(180.0F));

        // ── 1. Breathing & Chest Expansion ──
        float idleBob = (float)Math.sin(this.ageInTicks * 0.06F) * 0.04F;
        poseStack.translate(0.0F, idleBob, 0.0F);

        float animTime = 0.0F;
        if (animLength > 0.0F) {
            animTime = (this.limbSwing * 0.15F) % animLength;
            if (animTime < 0) animTime += animLength;
        }

        // ── 2. Mixamo Running Sway & Footstep Impact ──
        if (this.limbSwingAmount > 0.01F) {
            float[] hipsRot = sampleBoneRotation("Hips", animTime);
            float[] spineRot = sampleBoneRotation("Spine", animTime);

            // Forward lean & pelvic sway
            poseStack.mulPose(com.mojang.math.Axis.XP.rotationDegrees((12.0F + hipsRot[0] * 0.3F) * this.limbSwingAmount));
            poseStack.mulPose(com.mojang.math.Axis.ZP.rotationDegrees((hipsRot[2] * 0.4F) * this.limbSwingAmount));
            poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees((spineRot[1] * 0.4F) * this.limbSwingAmount));

            float walkBounce = Math.abs(Mth.sin(this.limbSwing * 0.6F)) * this.limbSwingAmount * -0.12F;
            poseStack.translate(0.0F, walkBounce, 0.0F);
        }

        // ── 3. Head Looking at Player ──
        float bodyYaw = Math.max(-45.0F, Math.min(45.0F, this.netHeadYaw)) * 0.35F;
        float bodyPitch = Mth.clamp(this.headPitch, -80.0F, 80.0F);
        poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(bodyYaw));
        poseStack.mulPose(com.mojang.math.Axis.XP.rotationDegrees(bodyPitch));

        // ── 4. Horror Glitch Tremor ──
        float jitterX = (float)Math.sin(this.ageInTicks * 1.6F) * 0.01F;
        float jitterZ = (float)Math.cos(this.ageInTicks * 1.8F) * 0.01F;
        poseStack.translate(jitterX, 0.0F, jitterZ);

        // Scale high-poly unified mesh to 2.0x (12 blocks tall)
        poseStack.scale(2.0F, 2.0F, 2.0F);

        MONSTER_MESH.render(poseStack, vertexConsumer, packedLight, packedOverlay, color);

        poseStack.popPose();
    }
}
