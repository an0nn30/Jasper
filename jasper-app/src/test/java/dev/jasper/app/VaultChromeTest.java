package dev.jasper.app;

import dev.jasper.app.vault.VaultSettings;
import dev.jasper.app.vault.VaultSnapshot;
import java.util.ArrayDeque;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.*;
import org.junit.jupiter.api.Test;
import static dev.jasper.app.DesktopTestSupport.*;
import static org.assertj.core.api.Assertions.assertThat;

class VaultChromeTest {
    @org.junit.jupiter.api.AfterEach void cleanup() throws Exception { closeOwners(); }

    static VaultSnapshot snapshot(boolean exists, boolean locked, int autoLock) {
        return new VaultSnapshot(exists, locked, List.of(), List.of(), new VaultSettings(autoLock, 7), null, 1, null);
    }

    @Test void toolsMenuPaletteAndPadlockFollowTheVaultConnection() throws Exception {
        edt(() -> {
            var owner = content(launcher(new ArrayDeque<>()));
            owner.updateActions();
            JMenu tools = owner.menuBar().getMenu(5);
            assertThat(tools.getText()).isEqualTo("Tools");
            assertThat(java.util.Arrays.stream(tools.getMenuComponents()).map(JMenuItem.class::cast).map(JMenuItem::getAction))
                .containsExactly(owner.action(ActionId.VAULT_MANAGER), owner.action(ActionId.VAULT_LOCK));
            assertThat(owner.action(ActionId.VAULT_MANAGER).isEnabled()).isFalse();
            assertThat(owner.action(ActionId.VAULT_LOCK).isEnabled()).isFalse();
            assertThat(owner.action(ActionId.VAULT_MANAGER).getValue(Action.ACCELERATOR_KEY)).isNull();
            assertThat(owner.status().vaultButton().isEnabled()).isFalse();

            AtomicInteger opened = new AtomicInteger(), toggled = new AtomicInteger();
            owner.connectVault(opened::incrementAndGet, toggled::incrementAndGet);
            owner.showVaultState(snapshot(true, false, 15));
            assertThat(owner.action(ActionId.VAULT_MANAGER).isEnabled()).isTrue();
            assertThat(owner.action(ActionId.VAULT_LOCK).getValue(Action.NAME)).isEqualTo("Lock Vault");
            assertThat(owner.action(ActionId.VAULT_LOCK).getValue(Command.TITLE)).isEqualTo("Lock Vault");
            assertThat(owner.status().vaultButton().isEnabled()).isTrue();
            assertThat(owner.status().vaultButton().getIcon()).isSameAs(VaultIcons.icon("unlock"));
            assertThat(owner.status().vaultButton().getToolTipText())
                .isEqualTo("Credential vault unlocked. Click to lock. Auto-lock after 15 min of inactivity.");

            owner.showVaultState(snapshot(true, true, 0));
            assertThat(owner.action(ActionId.VAULT_LOCK).getValue(Action.NAME)).isEqualTo("Unlock Vault…");
            assertThat(owner.status().vaultButton().getIcon()).isSameAs(VaultIcons.icon("lock"));
            assertThat(owner.status().vaultButton().getToolTipText()).isEqualTo("Credential vault locked. Click to unlock.");
            owner.showVaultState(snapshot(false, true, 15));
            assertThat(owner.action(ActionId.VAULT_LOCK).getValue(Action.NAME)).isEqualTo("Create Vault…");
            assertThat(owner.status().vaultButton().getToolTipText()).isEqualTo("No credential vault yet. Click to create one.");

            owner.invoke(ActionId.VAULT_MANAGER); owner.invoke(ActionId.VAULT_LOCK);
            owner.status().vaultButton().doClick();
            assertThat(opened).hasValue(1); assertThat(toggled).hasValue(2);

            var entries = owner.commands().entries();
            assertThat(CommandSearch.find(entries, "vault", List.of()).stream().map(Command::id))
                .contains("vault_manager", "vault_lock");
            assertThat(CommandSearch.find(entries, "credential", List.of()).stream().map(Command::id)).contains("vault_manager");
            assertThat(CommandSearch.find(entries, "unlock", List.of()).stream().map(Command::id)).contains("vault_lock");

            owner.disconnectVault();
            assertThat(owner.action(ActionId.VAULT_MANAGER).isEnabled()).isFalse();
            assertThat(owner.status().vaultButton().isEnabled()).isFalse();
            owner.close();
            assertThat(owner.action(ActionId.VAULT_LOCK).isEnabled()).isFalse();
        });
    }
}
