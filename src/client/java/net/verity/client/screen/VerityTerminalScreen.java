package net.verity.client.screen;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Terminal GUI for Verity's Support System.
 * Shows account cancellation option and the creepy support response.
 */
public class VerityTerminalScreen extends Screen {

    private int stage = 0; // 0=welcome, 1=options, 2=cancel_confirm, 3=response
    private String inputText = "";
    private int cursorBlink = 0;

    // Support responses
    private static final String WELCOME = "=== Verity Support Terminal ===\n\n" +
            "Hello. You have reached Verity Support.\n" +
            "How can we assist you today?\n\n" +
            "Available options:\n" +
            "  1 - Report a bug\n" +
            "  2 - Account information\n" +
            "  3 - Account cancellation\n\n" +
            "Type a number to select:";

    private static final String CANCEL_CONFIRM = "=== Account Cancellation ===\n\n" +
            "You have selected account cancellation.\n" +
            "This action is PERMANENT and IRREVERSIBLE.\n\n" +
            "Are you sure you want to cancel your account?\n" +
            "Type '3' to confirm:";

    private static final String CANCEL_RESPONSE = "=== Verity Support ===\n\n" +
            "Sorry, man. We don't really do that.\n\n" +
            "You can end it.\n" +
            "If you're dead, Verity won't be able to torment you anymore.\n" +
            "He'll just move on to the next one.\n\n" +
            "Have a nice day.";

    public VerityTerminalScreen() {
        super(Component.literal("Verity Support Terminal"));
    }

    @Override
    protected void init() {
        super.init();
        stage = 0;
        inputText = "";
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Dark background
        this.renderBackground(graphics, mouseX, mouseY, partialTick);

        int centerX = this.width / 2;
        int startY = 30;

        // Terminal border
        int boxW = Math.min(400, this.width - 40);
        int boxH = this.height - 60;
        int boxX = centerX - boxW / 2;

        // Black terminal background
        graphics.fill(boxX - 2, startY - 2, boxX + boxW + 2, startY + boxH + 2, 0xFF00AA00);
        graphics.fill(boxX, startY, boxX + boxW, startY + boxH, 0xFF0A0A0A);

        // Green text
        String text = switch (stage) {
            case 0 -> WELCOME;
            case 1 -> WELCOME;
            case 2 -> CANCEL_CONFIRM;
            case 3 -> CANCEL_RESPONSE;
            default -> WELCOME;
        };

        // Draw text line by line
        String[] lines = text.split("\n");
        int lineHeight = 12;
        int textY = startY + 10;

        for (String line : lines) {
            if (textY + lineHeight > startY + boxH - 20) break;
            graphics.drawString(this.font, line, boxX + 10, textY, 0xFF00FF00, false);
            textY += lineHeight;
        }

        // Input line (stages 0-2)
        if (stage < 3) {
            cursorBlink++;
            String prompt = "> " + inputText;
            if (cursorBlink % 20 < 10) prompt += "_";
            graphics.drawString(this.font, prompt, boxX + 10, startY + boxH - 20, 0xFF00FF00, false);
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (super.keyPressed(keyCode, scanCode, modifiers)) return true;

        // Backspace
        if (keyCode == 259 && !inputText.isEmpty()) {
            inputText = inputText.substring(0, inputText.length() - 1);
            return true;
        }

        // Enter
        if (keyCode == 257 || keyCode == 335) {
            handleInput();
            return true;
        }

        return false;
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (stage >= 3) return false;
        // Only accept numbers and backspace
        if (Character.isDigit(chr) && inputText.length() < 1) {
            inputText += chr;
            return true;
        }
        return false;
    }

    private void handleInput() {
        if (inputText.isEmpty()) return;

        switch (stage) {
            case 0, 1 -> {
                if (inputText.equals("3")) {
                    stage = 2;
                    inputText = "";
                } else {
                    // Other options - stay at stage 1
                    inputText = "";
                }
            }
            case 2 -> {
                if (inputText.equals("3")) {
                    stage = 3;
                    inputText = "";
                    // Trigger the final phase in Verity
                    triggerFinalPhase();
                } else {
                    stage = 1;
                    inputText = "";
                }
            }
        }
    }

    private void triggerFinalPhase() {
        if (this.minecraft != null && this.minecraft.player != null) {
            net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
                    new net.verity.net.TriggerFinalPhasePayload());
            this.onClose();
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        super.onClose();
    }
}
