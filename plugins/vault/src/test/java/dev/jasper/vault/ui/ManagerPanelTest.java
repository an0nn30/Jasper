package dev.jasper.vault.ui;

import dev.jasper.vault.testsupport.ZoneColors;
import dev.jasper.sdk.ui.WorkspacePalette;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ManagerPanelTest {
    @Test void repeatedNotePaintsPreserveTheHostCompositeStyle() throws Exception {
        javax.swing.SwingUtilities.invokeAndWait(() -> {
            try (var colors = new ZoneColors()) {
                var panel = panel(new AtomicReference<>());
                colors.workspace.add(panel);
                colors.assertStableSemanticStyle(panel.status, "textMuted");
                var empty = ZoneColors.labels(panel).stream().filter(l -> "Nothing to show".equals(l.getText())).findFirst().orElseThrow();
                colors.put("Jasper.workspace.textMuted", new java.awt.Color(0x314253));
                colors.assertStableSemanticStyle(empty, "textMuted");
                panel.close();
            }
        });
    }

    @Test void mountedManagerKeepsMutedTextAndRuleAfterUiRefresh() throws Exception {
        javax.swing.SwingUtilities.invokeAndWait(() -> {
            try (var colors = new ZoneColors()) {
                var panel = panel(new AtomicReference<>());
                var value = new VaultManager.Row(UUID.randomUUID(), "Name", "Detail", VaultManager.Type.LOGIN);
                var empty = ZoneColors.labels(panel).stream().filter(l -> "Nothing to show".equals(l.getText())).findFirst().orElseThrow();
                var frame = (javax.swing.JComponent) empty.getParent();
                for (var parent : List.of(colors.workspace, colors.chrome, colors.workspace)) {
                    parent.add(panel);
                    var p = WorkspacePalette.current(panel.rows);
                    var labels = ZoneColors.labels(panel.rows.getCellRenderer().getListCellRendererComponent(panel.rows, value, 0, false, false));
                    assertThat(labels.stream().filter(l -> "Detail".equals(l.getText())).findFirst().orElseThrow().getForeground()).isEqualTo(p.textMuted());
                    var selected = ZoneColors.labels(panel.rows.getCellRenderer().getListCellRendererComponent(panel.rows, value, 0, true, false));
                    for (var label : selected) assertThat(label.getForeground()).isEqualTo(panel.rows.getSelectionForeground());
                    assertThat(ZoneColors.rule(frame, 30, 0)).isEqualTo(p.border());
                    assertThat(frame.getInsets()).isEqualTo(new java.awt.Insets(1, 1, 1, 1));
                    empty.updateUI(); panel.status.updateUI(); ZoneColors.paint(empty); ZoneColors.paint(panel.status);
                    assertThat(empty.getForeground()).isEqualTo(p.textMuted());
                    assertThat(panel.status.getForeground()).isEqualTo(p.textMuted());
                    colors.put("Jasper.workspace.textMuted", new java.awt.Color(0x234567));
                }
                panel.close();
            }
        });
    }

    @Test void controlsUseHostSemanticIcons() {
        var panel = panel(new AtomicReference<>());
        assertThat(panel.add.getIcon()).isEqualTo(new dev.jasper.sdk.testing.FakeNamedIcon(dev.jasper.sdk.ui.IconName.ADD));
        assertThat(panel.edit.getIcon()).isEqualTo(new dev.jasper.sdk.testing.FakeNamedIcon(dev.jasper.sdk.ui.IconName.EDIT));
        assertThat(panel.more.getIcon()).isEqualTo(new dev.jasper.sdk.testing.FakeNamedIcon(dev.jasper.sdk.ui.IconName.MORE));
        assertThat(panel.search.getClientProperty("JTextField.leadingIcon"))
            .isEqualTo(new dev.jasper.sdk.testing.FakeNamedIcon(dev.jasper.sdk.ui.IconName.SEARCH));
        panel.close();
    }

    @Test void searchAndTypeFiltersPreserveOnlyVisibleSelectionAndPaletteNavigationRevealsRows() {
        var panel = panel(new AtomicReference<>());
        var key = new VaultManager.Row(UUID.randomUUID(), "Zulu", "SHA256:fingerprint", VaultManager.Type.SSH_KEY);
        var login = new VaultManager.Row(UUID.randomUUID(), "Alpha", "deploy", VaultManager.Type.LOGIN);
        panel.refresh(List.of(key, login), List.of(), "device status", true);
        assertThat(panel.rows.getModel().getElementAt(0)).isEqualTo(login);
        assertThat(panel.status.getText()).isEqualTo("device status");
        assertThat(panel.rows.getAccessibleContext().getAccessibleChild(0).getAccessibleContext().getAccessibleName())
            .contains("Alpha", "Login", "deploy");
        panel.select(key.id()); panel.search.setText("DEPLOY");
        assertThat(panel.rows.getModel().getSize()).isEqualTo(1);
        assertThat(panel.selected()).isEmpty(); assertThat(panel.edit.isEnabled()).isFalse();
        panel.select(key.id());
        assertThat(panel.search.getText()).isEmpty(); assertThat(panel.selected()).contains(key.id());
        panel.kind.setSelectedItem("Login"); assertThat(panel.selected()).isEmpty();
        panel.refresh(List.of(key, login), List.of(), "device status", true);
        assertThat(panel.rows.getModel().getSize()).isEqualTo(1);
        panel.refresh(List.of(key, login), List.of(), "locked", false);
        assertThat(panel.rows.getModel().getSize()).isZero(); assertThat(panel.copyPublic.isEnabled()).isFalse();
        panel.close();
    }
    @Test void addMenuKeepsEveryExistingTypeAndKeyGenerationReachable() {
        var added = new AtomicReference<VaultManager.Type>(); var panel = panel(added);
        for (int index = 0; index < VaultManager.Type.values().length; index++) {
            ((javax.swing.JMenuItem) panel.addMenu.getComponent(index)).doClick();
            assertThat(added.get()).isEqualTo(VaultManager.Type.values()[index]);
        }
        assertThat(panel.moreMenu.getComponentCount()).isEqualTo(2);
        panel.close();
    }
    private static ManagerPanel panel(AtomicReference<VaultManager.Type> added) {
        return new ManagerPanel(id -> Optional.empty(), added::set, id -> { }, id -> { }, id -> { }, grant -> { }, () -> { }, () -> { }, () -> { }, () -> { }, dev.jasper.sdk.testing.FakeNamedIcon::new);
    }
}
