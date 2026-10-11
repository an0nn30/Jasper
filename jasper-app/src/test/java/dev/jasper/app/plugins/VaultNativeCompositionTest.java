package dev.jasper.app.plugins;

import java.awt.Dimension;
import java.nio.file.Path;
import javax.swing.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static dev.jasper.app.plugins.AppContractTest.onEdt;
import static dev.jasper.app.plugins.StagedVaultHost.*;
import static org.assertj.core.api.Assertions.*;

/** Real staged plugin through the production checked title slot and EntryPresenter. */
class VaultNativeCompositionTest {
    @TempDir Path directory;
    @Test void titleActionsOpenAttachedEditorsAndLockWithoutDuplicatingBodyControls() throws Exception {
        var previousLaf = AppContractTest.onEdtValue(UIManager::getLookAndFeel);
        onEdt(() -> assertThat(dev.jasper.app.appearance.ThemeManager.install(dev.jasper.app.appearance.Theme.DARK)).isTrue());
        try (var vault = new StagedVaultHost(directory, true)) {
            vault.createVault("synthetic-pass");var manager = vault.surface("Credential Vault");
            onEdt(() -> {
                assertThat(manager.contentSize()).isTrue();assertThat(manager.preferredSize()).isEqualTo(new Dimension(900,555));
                assertThat(named(manager.holder(), "lock")).isNull();assertThat(named(manager.holder(), "add")).isNull();
                assertThat(named(manager.titleActions(), "lock")).isInstanceOf(AbstractButton.class);
                var menu = ((JComponent) named(manager.titleActions(), "add")).getComponentPopupMenu();
                var nativeDelegate = menu.getUI(); var invoker = menu.getInvoker();
                var selection = MenuSelectionManager.defaultManager().getSelectedPath();
                var add = (AbstractButton) named(manager.titleActions(), "add.main");
                boolean selected = add.isSelected();
                assertThat(menu.isVisible()).isFalse();
                assertThat(nativeDelegate).isInstanceOf(com.formdev.flatlaf.ui.FlatPopupMenuUI.class);
                try {
                    // Keep the real Add action and popup lifecycle; only native screen discovery
                    // in FlatLaf's delegate is outside this headless integration boundary.
                    menu.setUI(new javax.swing.plaf.basic.BasicPopupMenuUI());
                    for (String label : java.util.List.of("Login", "SSH Key", "Secure Note", "Generate SSH Key...")) {
                        assertThat(add.isEnabled()).isTrue(); assertThat(add.isVisible()).isTrue();
                        add.doClick(0);
                        assertThat(menu.isVisible()).as("actual title Add opens its menu").isTrue();
                        java.util.Arrays.stream(menu.getComponents()).filter(c -> c instanceof AbstractButton b && b.getText().equals(label))
                            .map(AbstractButton.class::cast).findFirst().orElseThrow().doClick(0);
                        assertThat(menu.isVisible()).as("selection dismisses the real popup").isFalse();
                        var editor = vault.auxiliary.open().stream().filter(s -> s != manager).findFirst().orElseThrow();
                        assertThat(((dev.jasper.ui.model.Nodes.Form)kitView(editor).spec().root()).title()).isEqualTo(editor.title());assertThat(editor.attachedSheet()).isTrue();assertThat(((dev.jasper.ui.model.Nodes.Form)kitView(editor).spec().root()).kind()).isEqualTo(dev.jasper.ui.model.Nodes.FormKind.SHEET);assertThat(editor.ownerSurface()).containsSame(manager);editor.close();
                    }
                } finally {
                    menu.setVisible(false); menu.setUI(nativeDelegate); menu.setInvoker(invoker); add.setSelected(selected);
                    MenuSelectionManager.defaultManager().setSelectedPath(selection);
                }
                ((AbstractButton) named(manager.titleActions(), "lock")).doClick(0);
            });
            await(() -> node(manager, "locked") != null, "lock updates manager body");
            onEdt(() -> assertThat(named(manager.titleActions(), "lock").isEnabled()).isFalse());
            assertThat(vault.problems).isEmpty();
        } finally {
            onEdt(() -> {
                try { UIManager.setLookAndFeel(previousLaf); }
                catch (UnsupportedLookAndFeelException failure) { throw new AssertionError(failure); }
            });
        }
    }
}
