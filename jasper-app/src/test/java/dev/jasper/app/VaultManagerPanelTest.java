package dev.jasper.app;

import dev.jasper.app.vault.VaultSettings;
import dev.jasper.app.vault.VaultSnapshot;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.*;
import org.junit.jupiter.api.Test;
import static dev.jasper.app.DesktopTestSupport.edt;
import static org.assertj.core.api.Assertions.assertThat;

class VaultManagerPanelTest {
    static final UUID LOGIN = VaultFormsTest.LOGIN, KEY = VaultFormsTest.KEY;
    static final UUID OTHER = UUID.fromString("10000000-0000-0000-0000-000000000002");

    static VaultSnapshot two() {
        return new VaultSnapshot(true, false,
            List.of(new VaultSnapshot.LoginInfo(LOGIN, "Production admin", "alice", KEY, false),
                new VaultSnapshot.LoginInfo(OTHER, "Backup", "bob", null, true)),
            List.of(new VaultSnapshot.KeyInfo(KEY, "Infrastructure", "Ed25519", "SHA256:fixture", "ssh-ed25519 AAAA", 1)),
            new VaultSettings(15, 7), null, 2, null);
    }

    static void layout(Container c) { c.doLayout(); for (Component child : c.getComponents()) if (child instanceof Container nested) layout(nested); }

    @Test void snapshotFillsSidebarCountsTableAndEditorsAndLockClearsThem() throws Exception {
        edt(() -> {
            var panel = new VaultManagerPanel();
            panel.showSnapshot(two());
            assertThat(panel.sidebar.getModel().getSize()).isEqualTo(7);
            assertThat(panel.sidebar.getSelectedValue()).isEqualTo(VaultManagerPanel.Entry.ALL);
            var renderer = panel.sidebar.getCellRenderer();
            assertThat(((JLabel) renderer.getListCellRendererComponent(panel.sidebar, VaultManagerPanel.Entry.ALL, 1, false, false)).getText())
                .isEqualTo("All credentials  3");
            assertThat(((JLabel) renderer.getListCellRendererComponent(panel.sidebar, VaultManagerPanel.Entry.LOGINS, 2, false, false)).getText())
                .isEqualTo("Logins  2");
            assertThat(((JLabel) renderer.getListCellRendererComponent(panel.sidebar, VaultManagerPanel.Entry.KEYS, 3, false, false)).getText())
                .isEqualTo("SSH keys  1");
            assertThat(panel.table.getRowCount()).isEqualTo(3);
            assertThat(panel.table.getValueAt(0, 0)).isEqualTo("Backup");
            assertThat(panel.table.getValueAt(0, 2)).isEqualTo("Password");
            assertThat(panel.table.getValueAt(1, 0)).isEqualTo("Infrastructure");
            assertThat(panel.table.getValueAt(1, 2)).isEqualTo("Ed25519");
            assertThat(panel.table.getValueAt(2, 2)).isEqualTo("SSH key");
            assertThat(panel.status.getText()).isEqualTo("3 credentials    Auto-lock 15 min");
            assertThat(panel.expiry.getText()).isEqualTo("No remembered device access");
            assertThat(panel.selected()).isEqualTo(OTHER);
            assertThat(panel.title.getText()).isEqualTo("Backup");
            assertThat(panel.kind.getText()).isEqualTo("Login");
            assertThat(panel.login.isShowing() || panel.login.getParent() != null).isTrue();

            panel.select(KEY);
            assertThat(panel.kind.getText()).isEqualTo("SSH key");
            assertThat(panel.keyForm.getParent()).isNotNull();
            assertThat(panel.login.getParent()).isNull();
            panel.keyForm.name.setText("Renamed");
            assertThat(panel.dirty()).isTrue();
            assertThat(panel.saveAction.isEnabled()).isTrue();

            panel.showSnapshot(new VaultSnapshot(true, true, List.of(), List.of(), new VaultSettings(0, 7), null, 3, null));
            assertThat(panel.table.getRowCount()).isZero();
            assertThat(panel.dirty()).isFalse();
            assertThat(panel.keyForm.name.getText()).isEmpty();
            assertThat(panel.login.password.getPassword()).isEmpty();
            assertThat(panel.status.getText()).isEqualTo("0 credentials    Auto-lock off");
            assertThat(panel.addAction.isEnabled()).isFalse();
            assertThat(panel.lockAction.isEnabled()).isTrue();
            var form = new JLabel("unlock form");
            panel.lockedContent(form);
            assertThat(form.getParent()).isNotNull();
        });
    }

    @Test void sidebarFiltersRunManagementActionsAndDefersThroughNavigate() throws Exception {
        edt(() -> {
            var panel = new VaultManagerPanel();
            panel.showSnapshot(two());
            var pending = new ArrayList<Runnable>(); panel.navigate = pending::add;
            panel.sidebar.setSelectedValue(VaultManagerPanel.Entry.LOGINS, false);
            assertThat(panel.category()).isEqualTo(VaultManagerPanel.Entry.ALL);
            assertThat(panel.sidebar.getSelectedValue()).isEqualTo(VaultManagerPanel.Entry.ALL);
            pending.removeFirst().run();
            assertThat(panel.category()).isEqualTo(VaultManagerPanel.Entry.LOGINS);
            assertThat(panel.sidebar.getSelectedValue()).isEqualTo(VaultManagerPanel.Entry.LOGINS);
            assertThat(panel.table.getRowCount()).isEqualTo(2);
            panel.navigate = Runnable::run;
            panel.sidebar.setSelectedValue(VaultManagerPanel.Entry.KEYS, false);
            assertThat(panel.table.getRowCount()).isEqualTo(1);
            assertThat(panel.selected()).isEqualTo(KEY);
            panel.sidebar.setSelectedValue(VaultManagerPanel.Entry.VAULT_HEADER, false);
            assertThat(panel.sidebar.getSelectedValue()).isEqualTo(VaultManagerPanel.Entry.KEYS);

            AtomicInteger settings = new AtomicInteger(), imports = new AtomicInteger();
            panel.onSettings = settings::incrementAndGet; panel.onImport = imports::incrementAndGet;
            panel.sidebar.setSelectedValue(VaultManagerPanel.Entry.SETTINGS, false);
            assertThat(settings).hasValue(1);
            assertThat(panel.sidebar.getSelectedValue()).isEqualTo(VaultManagerPanel.Entry.KEYS);
            panel.sidebar.setSelectedValue(VaultManagerPanel.Entry.IMPORT, false);
            assertThat(imports).hasValue(1);
            panel.importTop.doClick(); assertThat(imports).hasValue(2);
            panel.settingsTop.doClick(); assertThat(settings).hasValue(2);

            panel.sidebar.setSelectedValue(VaultManagerPanel.Entry.ALL, false);
            panel.search.setText("prod");
        });
        edt(() -> {});
        edt(() -> {});
    }

    @Test void searchFiltersNameAndUsernameAfterTheEventQueueSettles() throws Exception {
        VaultManagerPanel[] holder = new VaultManagerPanel[1];
        edt(() -> { holder[0] = new VaultManagerPanel(); holder[0].showSnapshot(two()); holder[0].search.setText("bob"); });
        edt(() -> {}); edt(() -> {});
        edt(() -> {
            assertThat(holder[0].table.getRowCount()).isEqualTo(1);
            assertThat(holder[0].table.getValueAt(0, 0)).isEqualTo("Backup");
            holder[0].search.setText("ed25519");
        });
        edt(() -> {}); edt(() -> {});
        edt(() -> {
            assertThat(holder[0].table.getRowCount()).isEqualTo(1);
            assertThat(holder[0].table.getValueAt(0, 0)).isEqualTo("Infrastructure");
            holder[0].search.setText("nothing here");
        });
        edt(() -> {}); edt(() -> {});
        edt(() -> {
            assertThat(holder[0].table.getRowCount()).isZero();
            assertThat(holder[0].selected()).isNull();
            assertThat(holder[0].title.getText()).isEqualTo("Select a credential");
        });
    }

    @Test void tableSitsAboveTheEditorAndSaveStaysReachableWhenNarrow() throws Exception {
        edt(() -> {
            var themes = new ThemeController();
            for (UiLookAndFeel laf : List.of(UiLookAndFeel.METAL, UiLookAndFeel.MOTIF, UiLookAndFeel.NIMBUS)) {
                themes.selectLaf(laf);
                var panel = new VaultManagerPanel();
                panel.showSnapshot(two()); panel.select(LOGIN);
                panel.setSize(900, 600); layout(panel);
                assertThat(SwingUtilities.convertPoint(panel.table, 0, 0, panel).y)
                    .isLessThan(SwingUtilities.convertPoint(panel.login, 0, 0, panel).y);
                assertThat(SwingUtilities.convertPoint(panel.sidebar, 0, 0, panel).x)
                    .isLessThan(SwingUtilities.convertPoint(panel.table, 0, 0, panel).x);
                assertThat(panel.settingsTop.getAction()).isSameAs(panel.settingsAction);
                assertThat(panel.lockTop.getAction()).isSameAs(panel.lockAction);
                panel.setSize(560, 420); layout(panel);
                assertThat(panel.saveButton.getWidth()).isPositive();
                assertThat(SwingUtilities.convertPoint(panel.saveButton, 0, 0, panel).y + panel.saveButton.getHeight())
                    .isLessThanOrEqualTo(panel.getHeight());
                var image = new java.awt.image.BufferedImage(560, 420, java.awt.image.BufferedImage.TYPE_INT_ARGB);
                var g = image.createGraphics(); panel.paint(g); g.dispose();
            }
        });
    }
}
