package net.verity.client.model;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class BedrockMesh {
    public static class Vertex {
        public final float x, y, z;
        public final float nx, ny, nz;
        public final float u, v;

        public Vertex(float x, float y, float z, float nx, float ny, float nz, float u, float v) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.nx = nx;
            this.ny = ny;
            this.nz = nz;
            this.u = u;
            this.v = v;
        }
    }

    public static class Polygon {
        public final List<Vertex> vertices = new ArrayList<>();
    }

    private final List<Polygon> polygons = new ArrayList<>();

    public BedrockMesh(String resourcePath, String boneName, float pivotX, float pivotY, float pivotZ, boolean flipY, boolean flipZ) {
        try (InputStream stream = BedrockMesh.class.getResourceAsStream(resourcePath)) {
            if (stream == null) {
                throw new IllegalArgumentException("Resource not found: " + resourcePath);
            }
            JsonObject root = new Gson().fromJson(new InputStreamReader(stream, StandardCharsets.UTF_8), JsonObject.class);
            JsonArray geometryArray = root.getAsJsonArray("minecraft:geometry");
            JsonObject geometry = geometryArray.get(0).getAsJsonObject();
            JsonArray bones = geometry.getAsJsonArray("bones");
            
            JsonObject targetBone = null;
            if (boneName != null) {
                for (JsonElement boneElement : bones) {
                    JsonObject bone = boneElement.getAsJsonObject();
                    if (bone.has("name") && bone.get("name").getAsString().equals(boneName) && bone.has("poly_mesh")) {
                        targetBone = bone;
                        break;
                    }
                }
            }
            // Fallback: search any bone that contains poly_mesh
            if (targetBone == null) {
                for (JsonElement boneElement : bones) {
                    JsonObject bone = boneElement.getAsJsonObject();
                    if (bone.has("poly_mesh")) {
                        targetBone = bone;
                        break;
                    }
                }
            }

            if (targetBone == null || !targetBone.has("poly_mesh")) {
                return;
            }

            JsonObject mesh = targetBone.getAsJsonObject("poly_mesh");
            
            // Read positions
            JsonArray jsonPositions = mesh.getAsJsonArray("positions");
            float[][] positions = new float[jsonPositions.size()][3];
            for (int i = 0; i < jsonPositions.size(); i++) {
                JsonArray pos = jsonPositions.get(i).getAsJsonArray();
                positions[i][0] = pos.get(0).getAsFloat();
                positions[i][1] = pos.get(1).getAsFloat();
                positions[i][2] = pos.get(2).getAsFloat();
            }

            // Read normals
            JsonArray jsonNormals = mesh.getAsJsonArray("normals");
            float[][] normals = new float[jsonNormals.size()][3];
            for (int i = 0; i < jsonNormals.size(); i++) {
                JsonArray norm = jsonNormals.get(i).getAsJsonArray();
                normals[i][0] = norm.get(0).getAsFloat();
                normals[i][1] = norm.get(1).getAsFloat();
                normals[i][2] = norm.get(2).getAsFloat();
            }

            // Read uvs
            JsonArray jsonUvs = mesh.getAsJsonArray("uvs");
            float[][] uvs = new float[jsonUvs.size()][2];
            for (int i = 0; i < jsonUvs.size(); i++) {
                JsonArray uv = jsonUvs.get(i).getAsJsonArray();
                uvs[i][0] = uv.get(0).getAsFloat();
                uvs[i][1] = uv.get(1).getAsFloat();
            }

            // Read polys
            JsonArray polys = mesh.getAsJsonArray("polys");
            for (JsonElement polyElement : polys) {
                JsonArray polyVertices = polyElement.getAsJsonArray();
                Polygon polygon = new Polygon();
                for (JsonElement vertElement : polyVertices) {
                    JsonArray indices = vertElement.getAsJsonArray();
                    int posIdx = indices.get(0).getAsInt();
                    int normIdx = indices.get(1).getAsInt();
                    int uvIdx = indices.get(2).getAsInt();

                    // Convert position coordinates
                    float px = positions[posIdx][0] - pivotX;
                    float py = positions[posIdx][1] - pivotY;
                    float pz = positions[posIdx][2] - pivotZ;

                    px /= 16.0F;
                    py /= 16.0F;
                    pz /= 16.0F;

                    if (flipY) py = -py;
                    if (flipZ) pz = -pz;

                    // Normals
                    float nx = normals[normIdx][0];
                    float ny = normals[normIdx][1];
                    float nz = normals[normIdx][2];

                    if (flipY) ny = -ny;
                    if (flipZ) nz = -nz;

                    // UVs: Bedrock meshes are vertically flipped compared to Java Edition's GL texture mapping
                    float u = uvs[uvIdx][0];
                    float v = 1.0F - uvs[uvIdx][1];

                    polygon.vertices.add(new Vertex(px, py, pz, nx, ny, nz, u, v));
                }
                polygons.add(polygon);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to load Bedrock model " + resourcePath, e);
        }
    }

    public void render(PoseStack poseStack, VertexConsumer vertexConsumer, int packedLight, int packedOverlay, int color) {
        int a = (color >> 24) & 0xFF;
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;

        for (Polygon polygon : polygons) {
            for (Vertex vertex : polygon.vertices) {
                // Transform normal using the pose stack's normal matrix (Matrix3f)
                org.joml.Vector3f normal = new org.joml.Vector3f(vertex.nx, vertex.ny, vertex.nz);
                poseStack.last().normal().transform(normal);

                vertexConsumer.addVertex(poseStack.last().pose(), vertex.x, vertex.y, vertex.z)
                        .setColor(r, g, b, a)
                        .setUv(vertex.u, vertex.v)
                        .setOverlay(packedOverlay)
                        .setLight(packedLight)
                        .setNormal(normal.x(), normal.y(), normal.z());
            }
        }
    }
}
