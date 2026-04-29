package net.cnn_r.alliesandfoes.territory;

import net.cnn_r.alliesandfoes.map.MapState;
import net.cnn_r.alliesandfoes.network.ClearBeaconPayload;
import net.cnn_r.alliesandfoes.network.FlagScreenDataPayload;
import net.cnn_r.alliesandfoes.network.RenameTerritoryPayload;
import net.cnn_r.alliesandfoes.network.SetBeaconPayload;
import net.cnn_r.alliesandfoes.network.UpgradeFlagPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class TerritoryFlagScreen extends Screen {

    private static final int PANEL_W = 270;
    private static final int PANEL_H = 200;

    private static final int COLOR_BORDER     = 0xFFAA8833;
    private static final int COLOR_BG         = 0xCC111111;
    private static final int COLOR_BAR_EMPTY  = 0xFF333333;
    private static final int COLOR_BAR_FILL   = 0xFF44AA44;

    private FlagScreenDataPayload data;
    private EditBox nameField;

    public TerritoryFlagScreen(FlagScreenDataPayload data) {
        super(Component.literal("Territory Flag"));
        this.data = data;
    }

    public void refreshData(FlagScreenDataPayload data) {
        this.data = data;
        rebuildWidgets();
    }

    @Override
    protected void init() {
        clearWidgets();
        int panelX = (this.width - PANEL_W) / 2;
        int panelY = (this.height - PANEL_H) / 2;

        // Name edit box (y+95)
        nameField = new EditBox(font, panelX + 10, panelY + 95, PANEL_W - 80, 16,
                Component.literal("Territory name"));
        nameField.setMaxLength(32);
        nameField.setValue(data.anchorName());
        addRenderableWidget(nameField);

        addRenderableWidget(Button.builder(Component.literal("Rename"), btn -> sendRename())
                .bounds(panelX + PANEL_W - 65, panelY + 93, 55, 20)
                .build());

        // Beacon button (y+130)
        String beaconLabel = data.beaconHere() ? "Clear Beacon" : "Place Beacon";
        addRenderableWidget(Button.builder(Component.literal(beaconLabel), btn -> toggleBeacon())
                .bounds(panelX + 10, panelY + 130, PANEL_W - 20, 20)
                .build());

        // Upgrade button (y+165)
        boolean isElite = "elite".equalsIgnoreCase(data.tierId());
        String upgradeLabel = isElite
                ? "Max Tier Reached"
                : "Upgrade to " + nextTierDisplayName() + " — "
                    + data.upgradeInfluenceCost() + " inf + "
                    + data.upgradeMaterialCount() + "x " + formatMaterialName(data.upgradeMaterialId());
        Button upgradeBtn = Button.builder(Component.literal(upgradeLabel), btn -> sendUpgrade())
                .bounds(panelX + 10, panelY + 165, PANEL_W - 20, 20)
                .build();
        upgradeBtn.active = !isElite;
        addRenderableWidget(upgradeBtn);

        addRenderableWidget(Button.builder(Component.literal("Close"), btn -> onClose())
                .bounds(panelX + 10, panelY + 175, PANEL_W - 20, 20)
                .build());
    }

    private void sendRename() {
        String name = nameField.getValue().strip();
        if (!name.isEmpty()) {
            ClientPlayNetworking.send(new RenameTerritoryPayload(data.anchorId(), name));
        }
    }

    private void sendUpgrade() {
        ClientPlayNetworking.send(new UpgradeFlagPayload(
                data.anchorId(), data.blockX(), data.blockY(), data.blockZ()));
    }

    private void toggleBeacon() {
        if (data.beaconHere()) {
            ClientPlayNetworking.send(new ClearBeaconPayload(data.anchorId()));
        } else {
            ClientPlayNetworking.send(new SetBeaconPayload(data.anchorId()));
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float partialTick) {
        context.fill(0, 0, this.width, this.height, 0xCC000000);

        int panelX = (this.width - PANEL_W) / 2;
        int panelY = (this.height - PANEL_H) / 2;

        // Panel background + border
        context.fill(panelX, panelY, panelX + PANEL_W, panelY + PANEL_H, COLOR_BG);
        context.fill(panelX,              panelY,              panelX + PANEL_W, panelY + 1,           COLOR_BORDER);
        context.fill(panelX,              panelY + PANEL_H - 1, panelX + PANEL_W, panelY + PANEL_H,   COLOR_BORDER);
        context.fill(panelX,              panelY,              panelX + 1,        panelY + PANEL_H,    COLOR_BORDER);
        context.fill(panelX + PANEL_W - 1, panelY,             panelX + PANEL_W, panelY + PANEL_H,    COLOR_BORDER);

        // Header
        String tierDisplay = capitalise(data.tierId()) + " Flag";
        context.text(font, Component.literal("Territory Flag"), panelX + 10, panelY + 8, 0xFFFFCC44);
        context.text(font, Component.literal(tierDisplay),      panelX + 10, panelY + 20, tierColor());

        // Separator
        context.fill(panelX + 10, panelY + 31, panelX + PANEL_W - 10, panelY + 32, 0xFF444444);

        // Capacity bar
        int barX = panelX + 10;
        int barY = panelY + 48;
        int barW = PANEL_W - 20;
        int barH = 8;
        float fill = data.maxClaimValue() > 0
                ? Math.min(1f, (float) data.usedClaimValue() / data.maxClaimValue())
                : 0f;
        context.text(font,
                Component.literal("Capacity: " + data.usedClaimValue() + " / " + data.maxClaimValue()),
                barX, barY - 10, 0xFFCCCCCC);
        context.fill(barX, barY, barX + barW, barY + barH, COLOR_BAR_EMPTY);
        if (fill > 0) {
            context.fill(barX, barY, barX + (int)(barW * fill), barY + barH, COLOR_BAR_FILL);
        }

        // Alliance influence
        int balance = MapState.getAllianceInfluenceBalance();
        context.text(font,
                Component.literal("Influence: " + balance),
                panelX + 10, panelY + 62, 0xFFAAAAFF);

        // Separator
        context.fill(panelX + 10, panelY + 73, panelX + PANEL_W - 10, panelY + 74, 0xFF444444);

        // Name field label + editbox background
        context.text(font, Component.literal("Name:"), panelX + 10, panelY + 82, 0xFFCCCCCC);
        context.fill(panelX + 9, panelY + 93, panelX + PANEL_W - 66, panelY + 113, 0xFF222222);

        // Separator
        context.fill(panelX + 10, panelY + 115, panelX + PANEL_W - 10, panelY + 116, 0xFF444444);

        // Beacon section
        String beaconStatus = "Beacons: " + data.beaconCount() + " / " + data.beaconMax()
                + (data.beaconHere() ? "  [active]" : "");
        context.text(font, Component.literal(beaconStatus),
                panelX + 10, panelY + 121, 0xFFAAFFAA);

        // Separator above upgrade
        context.fill(panelX + 10, panelY + 152, panelX + PANEL_W - 10, panelY + 153, 0xFF444444);

        // Upgrade hint
        if (!"elite".equalsIgnoreCase(data.tierId())) {
            String hint = data.upgradeInfluenceCost() + " inf + "
                    + data.upgradeMaterialCount() + "x " + formatMaterialName(data.upgradeMaterialId());
            boolean canAfford = balance >= data.upgradeInfluenceCost();
            context.text(font, Component.literal(hint),
                    panelX + 10, panelY + 157, canAfford ? 0xFF88FF88 : 0xFFFF6666);
        }

        super.extractRenderState(context, mouseX, mouseY, partialTick);
    }

    private int tierColor() {
        return switch (data.tierId().toLowerCase()) {
            case "standard" -> 0xFFBBBBBB;
            case "advanced" -> 0xFFFFDD55;
            case "elite"    -> 0xFF55EEFF;
            default         -> 0xFFCC9944;
        };
    }

    private String nextTierDisplayName() {
        return switch (data.tierId().toLowerCase()) {
            case "basic"    -> "Standard";
            case "standard" -> "Advanced";
            case "advanced" -> "Elite";
            default         -> "?";
        };
    }

    private static String formatMaterialName(String id) {
        String path = id.contains(":") ? id.substring(id.indexOf(':') + 1) : id;
        StringBuilder sb = new StringBuilder();
        for (String part : path.split("_")) {
            if (part.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return sb.toString();
    }

    private static String capitalise(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1).toLowerCase();
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent input) {
        int key = input.key();
        if (key == 256 || key == 69) { // ESC or E
            this.onClose();
            return true;
        }
        return super.keyPressed(input);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
