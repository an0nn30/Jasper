package dev.jasper.app.plugins;

import dev.jasper.app.appearance.ThemeTestSupport;
import java.nio.file.Path;
import javax.swing.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static dev.jasper.app.plugins.AppContractTest.onEdt;
import static dev.jasper.app.plugins.StagedVaultHost.*;
import static org.assertj.core.api.Assertions.*;

/** Real staged plugin through the manager's tab, its action row and EntryPresenter. */
class VaultNativeCompositionTest {
    @TempDir Path directory;
    /** Workspace tabs plan 4b: the manager is a tab, so the Lock and Add actions its window's title bar held are its first row. */
    @Test void theActionRowOpensEditorsAttachedToTheWindowAndLocks() throws Exception {
        var previousLaf = AppContractTest.onEdtValue(UIManager::getLookAndFeel);
        onEdt(() -> assertThat(dev.jasper.app.appearance.ThemeTestSupport.install(true)).isTrue());
        try (var vault = new StagedVaultHost(directory, true)) {
            vault.createVault("synthetic-pass");var manager = vault.manager();
            onEdt(() -> {
                assertThat(named(manager, "lock")).isInstanceOf(AbstractButton.class);
                var menu = ((JComponent) named(manager, "add")).getComponentPopupMenu();
                var nativeDelegate = menu.getUI(); var invoker = menu.getInvoker();
                var selection = MenuSelectionManager.defaultManager().getSelectedPath();
                var add = (AbstractButton) named(manager, "add.main");
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
                        assertThat(menu.isVisible()).as("actual Add opens its menu").isTrue();
                        java.util.Arrays.stream(menu.getComponents()).filter(c -> c instanceof AbstractButton b && b.getText().equals(label))
                            .map(AbstractButton.class::cast).findFirst().orElseThrow().doClick(0);
                        assertThat(menu.isVisible()).as("selection dismisses the real popup").isFalse();
                        var editor = vault.auxiliary.open().stream().filter(s -> !s.closed()).findFirst().orElseThrow();
                        var form = (dev.jasper.ui.model.Nodes.Form) kitView(editor).spec().root();
                        assertThat(form.title()).isEqualTo(editor.title());
                        assertThat(form.kind()).isEqualTo(dev.jasper.ui.model.Nodes.FormKind.SHEET);
                        assertThat(editor.attachedSheet()).isTrue();
                        assertThat(editor.ownerWindow()).as("the tab's window owns the sheet").contains(vault.window);
                        editor.close();
                    }
                } finally {
                    menu.setVisible(false); menu.setUI(nativeDelegate); menu.setInvoker(invoker); add.setSelected(selected);
                    MenuSelectionManager.defaultManager().setSelectedPath(selection);
                }
                ((AbstractButton) named(manager, "lock")).doClick(0);
            });
            await(() -> node(manager, "locked") != null, "lock updates manager body");
            onEdt(() -> assertThat(named(manager, "lock").isEnabled()).isFalse());
            assertThat(vault.problems).isEmpty();
        } finally {
            onEdt(() -> {
                try { UIManager.setLookAndFeel(previousLaf); }
                catch (UnsupportedLookAndFeelException failure) { throw new AssertionError(failure); }
            });
        }
    }
}
