package net.cnn_r.alliesandfoes.alliance.screen;

import net.cnn_r.alliesandfoes.network.KickAllianceMemberPayload;
import net.cnn_r.alliesandfoes.network.TransferAllianceOwnershipPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.UUID;

public class AllianceActionConfirmScreen extends Screen {
    private static final int PANEL_WIDTH = 300;
    private static final int PANEL_HEIGHT = 170;
    private static final int SCREEN_MARGIN = 18;
    private static final int SECTION_PAD = 14;
    private static final int HEADER_HEIGHT = 24;
    private static final int FOOTER_HEIGHT = 38;

    private final Screen parent;
    private final ConfirmAction action;
    private final UUID targetUuid;
    private final String targetName;
    private final String allianceName;

    private Button cancelButton;
    private Button confirmButton;

    public AllianceActionConfirmScreen(
            Screen parent,
            ConfirmAction action,
            UUID targetUuid,
            String targetName,
            String allianceName
    ) {
        super(Component.literal(action.title));
        this.parent = parent;
        this.action = action;
        this.targetUuid = targetUuid;
        this.targetName = targetName;
        this.allianceName = allianceName;
    }

    @Override
    protected void init() {
        this.clearWidgets();

        Layout layout = calculateLayout();
        int buttonWidth = (layout.contentWidth() - 10) / 2;

        this.cancelButton = this.addRenderableWidget(
                Button.builder(Component.literal("Cancel"), btn -> this.onClose())
                        .bounds(layout.contentLeft(), layout.bottomButtonY(), buttonWidth, 20)
                        .build()
        );

        this.confirmButton = this.addRenderableWidget(
                Button.builder(Component.literal(this.action.confirmLabel), btn -> confirmAction())
                        .bounds(layout.contentLeft() + buttonWidth + 10, layout.bottomButtonY(), buttonWidth, 20)
                        .build()
        );
    }

    @Override
    public boolean keyPressed(KeyEvent input) {
        int key = input.key();

        if (key == 256) {
            this.onClose();
            return true;
        }

        if (key == 257 || key == 335) {
            confirmAction();
            return true;
        }

        return super.keyPressed(input);
    }

    private void confirmAction() {
        switch (this.action) {
            case PROMOTE -> ClientPlayNetworking.send(new TransferAllianceOwnershipPayload(this.targetUuid));
            case KICK -> ClientPlayNetworking.send(new KickAllianceMemberPayload(this.targetUuid));
        }

        if (this.minecraft != null) {
            this.minecraft.setScreen(this.parent);
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, this.width, this.height, 0xCC000000);

        Layout layout = calculateLayout();

        context.fill(layout.left() - 10, layout.top() - 10, layout.right() + 10, layout.bottom() + 10, 0x66000000);
        context.fill(layout.left() - 1, layout.top() - 1, layout.right() + 1, layout.bottom() + 1, 0xFF8A6A3A);
        context.fill(layout.left(), layout.top(), layout.right(), layout.bottom(), 0xFFF3E7C9);
        context.fill(layout.left() + 4, layout.top() + 4, layout.right() - 4, layout.bottom() - 4, 0xFFF8EFD8);

        super.extractRenderState(context, mouseX, mouseY, delta);

        int titleColor = 0xFF3A2F1B;
        int bodyColor = 0xFF5A4A32;
        int accentColor = 0xFF6E5630;
        int strongColor = 0xFF241A10;

        String titleText = this.title.getString();
        int titleWidth = this.font.width(titleText);
        int titleX = this.width / 2 - titleWidth / 2;
        int titleY = layout.top() + 8;

        context.text(this.font, titleText, titleX, titleY, titleColor, false);

        int underlineY = titleY + this.font.lineHeight + 3;
        context.fill(titleX - 4, underlineY, titleX + titleWidth + 4, underlineY + 1, 0x668A6A3A);

        int textY = layout.bodyTop();

        for (FormattedLine line : this.action.buildLines(this.targetName, this.allianceName)) {
            context.text(
                    this.font,
                    line.text(),
                    layout.contentLeft(),
                    textY,
                    line.emphasis() ? strongColor : bodyColor,
                    false
            );
            textY += 12;
        }

        int dividerY = layout.bottom() - FOOTER_HEIGHT - 8;
        context.fill(
                layout.contentLeft(),
                dividerY,
                layout.contentRight(),
                dividerY + 1,
                0x668A6A3A
        );

        if (this.action == ConfirmAction.PROMOTE) {
            String warning = "You will no longer be the founder.";
            int warningWidth = this.font.width(warning);
            context.text(
                    this.font,
                    warning,
                    this.width / 2 - warningWidth / 2,
                    dividerY - 14,
                    accentColor,
                    false
            );
        }
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(this.parent);
        }
    }

    private Layout calculateLayout() {
        int panelWidth = Math.min(PANEL_WIDTH, this.width - SCREEN_MARGIN * 2);
        int panelHeight = Math.min(PANEL_HEIGHT, this.height - SCREEN_MARGIN * 2);

        int left = (this.width - panelWidth) / 2;
        int top = (this.height - panelHeight) / 2;
        int right = left + panelWidth;
        int bottom = top + panelHeight;

        int contentLeft = left + SECTION_PAD;
        int contentRight = right - SECTION_PAD;
        int contentWidth = contentRight - contentLeft;
        int bodyTop = top + HEADER_HEIGHT + 18;
        int bottomButtonY = bottom - 28;

        return new Layout(
                left,
                top,
                right,
                bottom,
                contentLeft,
                contentRight,
                contentWidth,
                bodyTop,
                bottomButtonY
        );
    }

    public enum ConfirmAction {
        PROMOTE("Transfer Leadership", "Promote"),
        KICK("Remove Member", "Kick");

        private final String title;
        private final String confirmLabel;

        ConfirmAction(String title, String confirmLabel) {
            this.title = title;
            this.confirmLabel = confirmLabel;
        }

        private List<FormattedLine> buildLines(String targetName, String allianceName) {
            return switch (this) {
                case PROMOTE -> List.of(
                        new FormattedLine("Transfer founder status to", false),
                        new FormattedLine(targetName + "?", true),
                        new FormattedLine("Alliance: " + allianceName, false)
                );
                case KICK -> List.of(
                        new FormattedLine("Remove", false),
                        new FormattedLine(targetName, true),
                        new FormattedLine("from " + allianceName + "?", false)
                );
            };
        }
    }

    private record FormattedLine(String text, boolean emphasis) {
    }

    private record Layout(
            int left,
            int top,
            int right,
            int bottom,
            int contentLeft,
            int contentRight,
            int contentWidth,
            int bodyTop,
            int bottomButtonY
    ) {
    }
}