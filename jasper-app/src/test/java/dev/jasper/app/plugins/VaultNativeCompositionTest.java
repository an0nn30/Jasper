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
        try (var vault = new StagedVaultHost(directory, true)) {
            vault.createVault("synthetic-pass");var manager = vault.surface("Credential Vault");
            onEdt(() -> {
                assertThat(manager.contentSize()).isTrue();assertThat(manager.preferredSize()).isEqualTo(new Dimension(900,555));
                assertThat(named(manager.holder(), "lock")).isNull();assertThat(named(manager.holder(), "add")).isNull();
                assertThat(named(manager.titleActions(), "lock")).isInstanceOf(AbstractButton.class);
                var menu = ((JComponent) named(manager.titleActions(), "add")).getComponentPopupMenu();
                for (String label : java.util.List.of("Login", "SSH Key", "Secure Note", "Generate SSH Key...")) {
                    java.util.Arrays.stream(menu.getComponents()).filter(c -> c instanceof AbstractButton b && b.getText().equals(label))
                        .map(AbstractButton.class::cast).findFirst().orElseThrow().doClick(0);
                    var editor = vault.auxiliary.open().stream().filter(s -> s != manager).findFirst().orElseThrow();
                    assertThat(((dev.jasper.ui.model.Nodes.Form)kitView(editor).spec().root()).title()).isEqualTo(editor.title());assertThat(editor.attachedSheet()).isTrue();assertThat(((dev.jasper.ui.model.Nodes.Form)kitView(editor).spec().root()).kind()).isEqualTo(dev.jasper.ui.model.Nodes.FormKind.SHEET);assertThat(editor.ownerSurface()).containsSame(manager);editor.close();
                }
                ((AbstractButton) named(manager.titleActions(), "lock")).doClick(0);
            });
            await(() -> node(manager, "locked") != null, "lock updates manager body");
            onEdt(() -> assertThat(named(manager.titleActions(), "lock").isEnabled()).isFalse());
            assertThat(vault.problems).isEmpty();
        }
    }
}
