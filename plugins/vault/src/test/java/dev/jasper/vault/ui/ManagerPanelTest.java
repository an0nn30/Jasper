package dev.jasper.vault.ui;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ManagerPanelTest {
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
        return new ManagerPanel(id -> Optional.empty(), added::set, id -> { }, id -> { }, id -> { }, grant -> { }, () -> { }, () -> { }, () -> { }, () -> { });
    }
}
