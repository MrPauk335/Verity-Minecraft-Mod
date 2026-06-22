package net.verity.client;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public final class VerityClientConfig {
    private static final Path CONFIG_PATH = FabricLoader.getInstance()
            .getConfigDir()
            .resolve("verity-client.properties");
    private static final long RELOAD_INTERVAL_MS = 250L;
    private static final float DEFAULT_FACE_YAW_OFFSET_DEGREES = -90.0F;

    private static long nextReloadTimeMs = 0L;
    private static long lastModifiedMs = Long.MIN_VALUE;
    private static float faceYawOffsetDegrees = DEFAULT_FACE_YAW_OFFSET_DEGREES;

    private VerityClientConfig() {
    }

    public static float getFaceYawOffsetDegrees() {
        long now = System.currentTimeMillis();
        if (now >= nextReloadTimeMs) {
            nextReloadTimeMs = now + RELOAD_INTERVAL_MS;
            reloadIfChanged();
        }
        return faceYawOffsetDegrees;
    }

    private static void reloadIfChanged() {
        try {
            ensureConfigExists();

            long modifiedMs = Files.getLastModifiedTime(CONFIG_PATH).toMillis();
            if (modifiedMs == lastModifiedMs) {
                return;
            }

            Properties properties = new Properties();
            try (var reader = Files.newBufferedReader(CONFIG_PATH)) {
                properties.load(reader);
            }

            String rawYaw = properties.getProperty("face_yaw_offset_degrees");
            if (rawYaw != null) {
                faceYawOffsetDegrees = Float.parseFloat(rawYaw.trim());
            }
            lastModifiedMs = modifiedMs;
        } catch (Exception ignored) {
            faceYawOffsetDegrees = DEFAULT_FACE_YAW_OFFSET_DEGREES;
        }
    }

    private static void ensureConfigExists() throws IOException {
        if (Files.exists(CONFIG_PATH)) {
            return;
        }

        Files.createDirectories(CONFIG_PATH.getParent());
        String text = """
                # Verity client-side tuning.
                # Change this while Minecraft is running to rotate Verity's face relative to the F3+B blue look line.
                # Try: -180, -90, 0, 90, 180
                face_yaw_offset_degrees=-90
                """;
        Files.writeString(CONFIG_PATH, text);
    }
}
