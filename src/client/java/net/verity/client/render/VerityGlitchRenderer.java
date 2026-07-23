package net.verity.client.render;

import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.player.Player;
import net.verity.entity.VerityEntity;

/**
 * Client-side renderer for Verity's GUI glitch effects during COUNTDOWN phase.
 * Red filter, screen shake, fake error popups.
 */
public class VerityGlitchRenderer {

    private static int glitchTimer = 0;
    private static int errorPopupTimer = 0;
    private static int errorPopupX = 0;
    private static int errorPopupY = 0;
    private static float originalFov = 70.0f;
    private static boolean fovModified = false;

    // Fake error messages
    private static final String[] ERROR_MESSAGES = {
        "System Error: Entity not found in world data",
        "CRITICAL: Save file corruption detected",
        "Warning: Unauthorized entity access",
        "Runtime Error: Reality.dll not responding",
        "Fatal: Verity core process cannot be terminated",
        "Exception: Stack overflow in consciousness module",
        "Alert: Boundary violation detected in chunk 0,0",
        "Error 0xVERITY: You cannot delete what was never installed"
    };

    public static void init() {
        HudRenderCallback.EVENT.register((graphics, tickDelta) -> {
            Minecraft mc = Minecraft.getInstance();
            Player player = mc.player;
            if (player == null) return;

            // Find nearest Verity
            VerityEntity nearest = null;
            var entities = player.level().getEntitiesOfClass(
                VerityEntity.class,
                player.getBoundingBox().inflate(128.0D),
                e -> e.isAlive());
            if (!entities.isEmpty()) {
                nearest = entities.get(0);
            }

            if (nearest == null) {
                resetEffects(mc);
                return;
            }

            VerityEntity.VerityPhase phase = nearest.getVerityPhase();
            if (phase != VerityEntity.VerityPhase.COUNTDOWN) {
                resetEffects(mc);
                return;
            }

            int day = nearest.getDayCounter() + 1;
            applyGlitchEffects(graphics, mc, day);
        });
    }

    private static void applyGlitchEffects(GuiGraphics graphics, Minecraft mc, int day) {
        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();

        // ── Red filter overlay (increases with day) ──
        glitchTimer++;
        int maxAlpha;
        int frequency;
        switch (day) {
            case 1 -> { maxAlpha = 15; frequency = 200; }
            case 2 -> { maxAlpha = 30; frequency = 100; }
            default -> { maxAlpha = 50; frequency = 50; }
        }

        if (glitchTimer % frequency < 10 + day * 5) {
            int alpha = maxAlpha * (day == 3 ? 2 : 1);
            // Pulsating red
            float pulse = (float) Math.sin(glitchTimer * 0.1) * 0.5f + 0.5f;
            int a = (int) (alpha * pulse);
            if (a > 0) {
                graphics.fill(0, 0, screenWidth, screenHeight, (a << 24) | 0xFF0000);
            }
        }

        // ── Screen shake (day 2+) ──
        if (day >= 2 && glitchTimer % 300 < 60) {
            float intensity = day == 2 ? 1.0f : 2.0f;
            float shakeX = (mc.player.getRandom().nextFloat() - 0.5f) * intensity;
            float shakeY = (mc.player.getRandom().nextFloat() - 0.5f) * intensity;
        }

        // ── FOV distortion (day 3) ──
        if (day >= 3) {
            if (!fovModified) {
                originalFov = mc.options.fov().get();
                fovModified = true;
            }
            float fovPulse = (float) Math.sin(glitchTimer * 0.05) * 5.0f;
            mc.options.fov().set((int) (originalFov + fovPulse));
        } else {
            resetFov(mc);
        }

        // ── Fake error popup (day 2+) ──
        if (day >= 2) {
            int popupChance = day == 2 ? 600 : 300;
            if (errorPopupTimer <= 0 && glitchTimer % popupChance == 0) {
                errorPopupTimer = 80 + mc.player.getRandom().nextInt(40); // 4-6 seconds
                errorPopupX = 20 + mc.player.getRandom().nextInt(screenWidth - 200);
                errorPopupY = 20 + mc.player.getRandom().nextInt(screenHeight - 60);
            }

            if (errorPopupTimer > 0) {
                errorPopupTimer--;
                drawErrorPopup(graphics, mc, screenWidth, screenHeight);
            }
        }
    }

    private static void drawErrorPopup(GuiGraphics graphics, Minecraft mc, int screenWidth, int screenHeight) {
        int popupW = 180;
        int popupH = 50;
        int x = Math.min(errorPopupX, screenWidth - popupW - 5);
        int y = Math.min(errorPopupY, screenHeight - popupH - 5);

        // Dark background
        graphics.fill(x, y, x + popupW, y + popupH, 0xCC1A1A2E);
        // Red border
        graphics.fill(x, y, x + popupW, y + 1, 0xFFFF0000);
        graphics.fill(x, y + popupH - 1, x + popupW, y + popupH, 0xFFFF0000);
        graphics.fill(x, y, x + 1, y + popupH, 0xFFFF0000);
        graphics.fill(x + popupW - 1, y, x + popupW, y + popupH, 0xFFFF0000);

        // Title bar
        graphics.fill(x + 1, y + 1, x + popupW - 1, y + 12, 0xFFCC0000);
        graphics.drawString(mc.font, "Verity\u2122", x + 5, y + 2, 0xFFFFFF00, false);

        // Error text
        String errorMsg = ERROR_MESSAGES[(int) (System.currentTimeMillis() / 1000) % ERROR_MESSAGES.length];
        graphics.drawString(mc.font, errorMsg, x + 5, y + 16, 0xFFFF4444, false);
        graphics.drawString(mc.font, "Click to dismiss", x + 5, y + 30, 0xFF888888, false);
    }

    private static void resetEffects(Minecraft mc) {
        resetFov(mc);
        errorPopupTimer = 0;
        glitchTimer = 0;
    }

    private static void resetFov(Minecraft mc) {
        if (fovModified) {
            mc.options.fov().set((int) originalFov);
            fovModified = false;
        }
    }
}
