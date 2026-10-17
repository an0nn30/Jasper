package dev.jasper.vault;

import dev.jasper.sdk.PluginInfo;
import dev.jasper.sdk.testing.FakePluginHost;
import dev.jasper.vault.api.Credential;
import dev.jasper.vault.api.LockState;
import dev.jasper.vault.api.VaultApi;
import dev.jasper.vault.model.Account;
import dev.jasper.vault.model.Auth;
import dev.jasper.vault.store.DeviceSecrets;
import dev.jasper.vault.store.FileStore;
import dev.jasper.vault.store.KeychainStore;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class VaultPluginTest {
    static final PluginInfo INFO = new PluginInfo("dev.jasper.vault", "Credential Vault", "0.1.0", Set.of(dev.jasper.sdk.Capabilities.PALETTE_CONTRIBUTE));
    static final PluginInfo SSH = new PluginInfo("dev.jasper.ssh", "SSH", "0.1.0", Set.of());

    static VaultPlugin plugin() {
        return new VaultPlugin(context -> new DeviceSecrets(
            new KeychainStore("Linux", command -> { throw new UncheckedIOException(new IOException("none")); }, context.dataDirectory().resolve("x")),
            new FileStore(context.dataDirectory().resolve("device.secret"))), Runnable::run);
    }

    @Test void searchOpenAndLockHaveTheirDefaultShortcuts() throws Exception { dev.jasper.vault.testsupport.KitViews.edt(() -> {
        try (var host = new FakePluginHost()) {
            host.start(INFO, Set.of(), Set.of(), plugin());
            var search = host.actionSpec(VaultPlugin.SEARCH).orElseThrow();
            assertThat(search.defaultBinding()).as("cmd is Ctrl+Shift off macOS: Ctrl+Shift+\\").contains("cmd+\\");
            assertThat(search.otherDefaultBinding()).isEmpty();
            var open = host.actionSpec(VaultPlugin.OPEN).orElseThrow();
            assertThat(open.defaultBinding()).as("the Alt rule makes it Ctrl+Alt+Shift+\\ off macOS").contains("cmd+shift+\\");
            assertThat(open.otherDefaultBinding()).isEmpty();
            var lock = host.actionSpec(VaultPlugin.LOCK).orElseThrow();
            assertThat(lock.defaultBinding()).as("the Alt rule makes it Ctrl+Alt+Shift+L off macOS").contains("cmd+shift+l");
            assertThat(lock.otherDefaultBinding()).isEmpty();
        }
    }); }

    @Test void aStartUnderEnterpriseReadsTheClassicLayoutOnce() throws Exception { dev.jasper.vault.testsupport.KitViews.edt(() -> {
        try (var host = new FakePluginHost()) {
            dev.jasper.vault.workstation.FakeEnterprise.start(host);
            var plugin = plugin();
            host.start(INFO, Set.of(), Set.of("dev.jasper.enterprise"), plugin);
            assertThat(host.failures()).isEmpty();
            assertThat(plugin.layout()).isEqualTo(dev.jasper.vault.workstation.Layout.CLASSIC);
        }
    }); }

    @Test void cancellingOneImportDoesNotCancelSharedVaultSetup() throws Exception { dev.jasper.vault.testsupport.KitViews.edt(() -> {
        var plugin = plugin();
        try (var host = new FakePluginHost()) {
            var context = host.start(INFO, Set.of(), Set.of(), plugin);
            var owner = context.terminals().window(host.addTerminalWindow()).orElseThrow();
            var first = plugin.service().forConsumer(SSH).importSshKeys(owner, List.of(), List.of());
            var second = plugin.service().forConsumer(OTHER_SETUP).importSshKeys(owner, List.of(), List.of());
            first.cancel(false);
            assertThat(second).isNotDone(); assertThat(host.windows()).contains("dialog|Create Vault|true");
            second.cancel(false);
            assertThat(host.windows()).doesNotContain("dialog|Create Vault|true");
        }
    }); }
    static final PluginInfo OTHER_SETUP = new PluginInfo("dev.jasper.other", "Other", "0.2.0", Set.of());

    @Test void stopLocksTheVault() throws Exception { dev.jasper.vault.testsupport.KitViews.edt(() -> {
        VaultPlugin plugin = plugin();
        try (var host = new FakePluginHost()) {
            host.start(INFO, Set.of(), Set.of(), plugin);
            plugin.lockManager().create("hunter2!".toCharArray(), false);
            host.runBackground();
            host.flush();
            assertThat(plugin.lockManager().state()).isEqualTo(LockState.UNLOCKED);
            assertThatCode(plugin::stop).doesNotThrowAnyException();
            assertThat(plugin.lockManager().state()).isEqualTo(LockState.LOCKED);
        }
    }); }

    @Test void publishesTheServiceActionsAndLockStateEvents() throws Exception { dev.jasper.vault.testsupport.KitViews.edt(() -> {
        VaultPlugin plugin = plugin();
        try (var host = new FakePluginHost()) {
            host.start(INFO, Set.of(), Set.of(), plugin);
            AtomicReference<VaultApi> api = new AtomicReference<>();
            List<LockState> seen = new ArrayList<>();
            host.start(SSH, Set.of("dev.jasper.vault"), Set.of(), context -> {
                api.set(context.services().require(VaultApi.class));
                context.events().subscribe(VaultApi.LOCK_STATE_CHANGED, seen::add);
            });
            assertThat(host.failures()).isEmpty();
            assertThat(host.actions()).contains("dev.jasper.vault.open|Open Vault...|true", "dev.jasper.vault.lock|Lock Vault|false",
                "dev.jasper.vault.search|Search Vault Logins|true");
            assertThat(host.notifications().groups()).contains("dev.jasper.vault.copies|Vault copies|CARD");
            assertThat(host.scopes()).containsExactly("dev.jasper.vault.scope|Vault|copy_password,copy_username,open");
            assertThat(host.scopePresentation("dev.jasper.vault.scope")).as("spec 14.6").isEqualTo("NEUTRAL|login|STANDARD,STANDARD,STANDARD");
            assertThat(host.scopesInAll()).as("secrets stay out of the All tab").isEmpty();
            UUID window = host.addTerminalWindow();
            assertThat(api.get().lockState()).isEqualTo(LockState.NO_VAULT);
            assertThat(host.invoke(VaultPlugin.OPEN, window, null)).isTrue();
            assertThat(host.windows()).as("no vault: the create dialog").containsExactly("dialog|Create Vault|true");
            plugin.lockManager().create("hunter2!".toCharArray(), true);
            host.runBackground();
            host.flush();
            assertThat(api.get().lockState()).isEqualTo(LockState.UNLOCKED);
            assertThat(seen).containsExactly(LockState.UNLOCKED);
            assertThat(host.actions()).contains("dev.jasper.vault.lock|Lock Vault|true");
            assertThat(host.invoke(VaultPlugin.LOCK, window, null)).isTrue();
            host.flush();
            assertThat(seen).containsExactly(LockState.UNLOCKED, LockState.LOCKED);
            assertThat(host.invoke(VaultPlugin.OPEN, window, null)).isTrue();
            assertThat(host.windows()).as("locked: the unlock dialog").contains("dialog|Unlock Vault|true");
        }
    }); }

    @Test void searchOpensTheVaultChipAndItsLockedRowUnlocksBackIntoIt() throws Exception { dev.jasper.vault.testsupport.KitViews.edt(() -> {
        VaultPlugin plugin = plugin();
        try (var host = new FakePluginHost()) {
            host.start(INFO, Set.of(), Set.of(), plugin);
            UUID window = host.addTerminalWindow();
            plugin.lockManager().create("hunter2!".toCharArray(), false);
            host.runBackground();
            UUID id = UUID.randomUUID();
            plugin.lockManager().vault().accounts().add(new Account(id, "prod", "deploy", new Auth.Password("s3cret".toCharArray()), Instant.EPOCH, Instant.EPOCH));
            plugin.lockManager().save();
            host.runBackground();
            plugin.lockManager().lock();
            host.flush();
            assertThat(host.invoke(VaultPlugin.SEARCH, window, null)).isTrue();
            assertThat(host.paletteOpens()).containsExactly(window + " dev.jasper.vault.scope - -");
            assertThat(host.searchScope("dev.jasper.vault.scope", "", window, null)).singleElement().asString().startsWith("locked|Vault is locked");
            host.executeInScope("dev.jasper.vault.scope", "locked", "copy_password", window, null);
            assertThat(host.windows()).as("unlock in place of the manager").containsExactly("dialog|Unlock Vault|true");
            plugin.currentUnlock().submit("hunter2!".toCharArray());
            host.runBackground();
            host.flush();
            assertThat(plugin.lockManager().state()).isEqualTo(LockState.UNLOCKED);
            assertThat(host.windows()).as("no manager window").isEmpty();
            assertThat(host.paletteOpens()).as("back on the Vault chip").containsExactly(window + " dev.jasper.vault.scope - -", window + " dev.jasper.vault.scope - -");
            assertThat(host.searchScope("dev.jasper.vault.scope", "pro", window, null)).containsExactly("cred." + id + "|prod|true");
            host.executeInScope("dev.jasper.vault.scope", "cred." + id, "copy_password", window, null);
            assertThat(host.clipboard().text()).contains("s3cret");
            assertThat(host.notifications().lines()).contains("dev.jasper.vault.copies|INFO|Copied the password for prod|Copied to Clipboard for 30 seconds");
        }
    }); }

    @Test void aCancelledUnlockFromTheLockedRowDoesNotReopenThePalette() throws Exception { dev.jasper.vault.testsupport.KitViews.edt(() -> {
        VaultPlugin plugin = plugin();
        try (var host = new FakePluginHost()) {
            host.start(INFO, Set.of(), Set.of(), plugin);
            UUID window = host.addTerminalWindow();
            plugin.lockManager().create("hunter2!".toCharArray(), false);
            host.runBackground();
            plugin.lockManager().lock();
            host.flush();
            host.searchScope("dev.jasper.vault.scope", "", window, null);
            host.executeInScope("dev.jasper.vault.scope", "locked", "copy_password", window, null);
            plugin.currentUnlock().cancel();
            host.flush();
            assertThat(plugin.lockManager().state()).isEqualTo(LockState.LOCKED);
            assertThat(host.paletteOpens()).isEmpty();
        }
    }); }

    @Test void aConsumerFetchesThroughUnlockAndGrantDialogs() throws Exception { dev.jasper.vault.testsupport.KitViews.edt(() -> {
        VaultPlugin plugin = plugin();
        try (var host = new FakePluginHost()) {
            host.start(INFO, Set.of(), Set.of(), plugin);
            AtomicReference<VaultApi> api = new AtomicReference<>();
            host.start(SSH, Set.of("dev.jasper.vault"), Set.of(), context -> api.set(context.services().require(VaultApi.class)));
            UUID window = host.addTerminalWindow();
            plugin.lockManager().create("hunter2!".toCharArray(), false);
            host.runBackground();
            UUID id = UUID.randomUUID();
            plugin.lockManager().vault().accounts().add(new Account(id, "prod", "deploy", new Auth.Password("s3cret".toCharArray()), Instant.EPOCH, Instant.EPOCH));
            plugin.lockManager().save();
            host.runBackground();
            plugin.lockManager().lock();
            CompletableFuture<Optional<Credential>> fetch = api.get().credential(id);
            assertThat(host.windows()).containsExactly("dialog|Unlock Vault|true");
            plugin.service().requestUnlock(null);   // a second requester joins the same prompt
            assertThat(host.windows()).hasSize(1);
            plugin.currentUnlock().submit("hunter2!".toCharArray());
            host.runBackground();
            assertThat(host.windows()).as("unlock closed, grant open").containsExactly("dialog|Allow SSH to use prod?|true");
            plugin.currentGrant().answer(dev.jasper.vault.service.GrantPrompt.Decision.ALLOW_ONCE);
            try (Credential credential = fetch.join().orElseThrow()) { assertThat(credential.password()).isEqualTo("s3cret".toCharArray()); }
            assertThat(host.windows()).isEmpty();
            CompletableFuture<Optional<Credential>> cancelled = api.get().credential(id);
            assertThat(host.windows()).hasSize(1);
            cancelled.cancel(false);
            assertThat(host.windows()).as("the last waiter left: dialog closed").isEmpty();
        }
    }); }

    @Test void autoLockFollowsTheSettingsAndStopLocks() throws Exception { dev.jasper.vault.testsupport.KitViews.edt(() -> {
        VaultPlugin plugin = plugin();
        try (var host = new FakePluginHost()) {
            host.setSetting("dev.jasper.vault", "auto_lock_minutes", 1L);
            host.start(INFO, Set.of(), Set.of(), plugin);
            plugin.lockManager().create("hunter2!".toCharArray(), false);
            host.runBackground();
            assertThat(plugin.timer().timeout()).isEqualTo(Duration.ofMinutes(1));
            plugin.clock = 0;
            plugin.timer().touch();
            plugin.clock = Duration.ofMinutes(1).toMillis();
            plugin.tick();
            assertThat(plugin.lockManager().state()).isEqualTo(LockState.LOCKED);
            host.setSetting("dev.jasper.vault", "auto_lock_minutes", 0L);
            host.flush();
            assertThat(plugin.timer().timeout()).isEqualTo(Duration.ZERO);
            plugin.lockManager().unlock("hunter2!".toCharArray());
            host.runBackground();
            plugin.clock += Duration.ofDays(1).toMillis();
            plugin.tick();
            assertThat(plugin.lockManager().state()).isEqualTo(LockState.UNLOCKED);
            host.stopAll();
            assertThat(plugin.lockManager().state()).isEqualTo(LockState.LOCKED);
        }
    }); }

    @Test void settingsResolveTheKeysDirectoryAndDefaults() throws Exception { dev.jasper.vault.testsupport.KitViews.edt(() -> {
        try (var host = new FakePluginHost()) {
            host.setSetting("dev.jasper.vault", "keys_directory", "/elsewhere/keys");
            host.setSetting("dev.jasper.vault", "bind_new_vaults_to_device", false);
            VaultPlugin plugin = plugin();
            host.start(INFO, Set.of(), Set.of(), plugin);
            assertThat(plugin.settings().keysDirectory()).isEqualTo(java.nio.file.Path.of("/elsewhere/keys"));
            assertThat(plugin.settings().bindByDefault()).isFalse();
            assertThat(plugin.settings().autoLock()).isEqualTo(Duration.ofMinutes(15));
            assertThat(host.declared("dev.jasper.vault")).containsExactly("auto_lock_minutes", "keys_directory", "bind_new_vaults_to_device");
        }
        try (var host = new FakePluginHost()) {
            host.setSetting("dev.jasper.vault", "auto_lock_minutes", -5L);
            VaultPlugin plugin = plugin();
            var context = host.start(INFO, Set.of(), Set.of(), plugin);
            assertThat(plugin.settings().keysDirectory()).isEqualTo(context.dataDirectory().resolve("keys"));
            assertThat(plugin.settings().autoLock()).as("out of range: the default").isEqualTo(Duration.ofMinutes(15));
            assertThat(host.settingsReports("dev.jasper.vault")).singleElement().asString().startsWith("auto_lock_minutes: ");
        }
    }); }
    @Test void thePadlockFollowsTheLockStateAndTheFileMenuOpensTheVault() throws Exception { dev.jasper.vault.testsupport.KitViews.edt(() -> {
        VaultPlugin plugin = plugin();
        try (var host = new FakePluginHost()) {
            host.start(INFO, Set.of(), Set.of(), plugin);
            assertThat(host.rail()).isEmpty();
            assertThat(host.menu("FILE")).contains("item:" + VaultPlugin.OPEN);
            assertThat(host.status()).containsExactly("dev.jasper.vault.status|RIGHT|Vault|No vault — click to create one|dev.jasper.vault.open");
            plugin.lockManager().create("hunter2!".toCharArray(), false);
            host.runBackground();
            plugin.clock = 0;
            plugin.timer().touch();
            plugin.tick();
            assertThat(host.status()).containsExactly("dev.jasper.vault.status|RIGHT|Vault: Unlocked|Vault unlocked · locks in 15 min|dev.jasper.vault.lock");
            plugin.clock = Duration.ofMinutes(14).toMillis() + 1;
            plugin.tick();
            assertThat(host.status().getFirst()).contains("locks in 1 min");
            plugin.lockManager().lock();
            assertThat(host.status()).containsExactly("dev.jasper.vault.status|RIGHT|Vault: Locked|Vault locked|dev.jasper.vault.open");
            assertThat(VaultPlugin.tooltip(LockState.UNLOCKED, Duration.ZERO)).isEqualTo("Vault unlocked");
            assertThat(VaultPlugin.statusText(LockState.NO_VAULT)).as("spec 13; no vault is neither locked nor unlocked").isEqualTo("Vault");
            assertThat(VaultPlugin.tooltip(LockState.UNLOCKED, Duration.ofSeconds(30))).isEqualTo("Vault unlocked · locks in 1 min");
        }
    }); }

    @Test void passwordAndGrantsAreVaultCommandsInTheFileMenu() throws Exception { dev.jasper.vault.testsupport.KitViews.edt(() -> {
        VaultPlugin plugin = plugin();
        try (var host = new FakePluginHost()) {
            host.start(INFO, Set.of(), Set.of(), plugin);
            UUID window = host.addTerminalWindow();
            assertThat(host.menu("FILE")).contains("item:" + VaultPlugin.OPEN, "item:" + VaultPlugin.CHANGE_PASSWORD, "item:" + VaultPlugin.GRANTS);
            plugin.lockManager().create("test-password".toCharArray(), false);
            host.runBackground();
            host.invoke(VaultPlugin.CHANGE_PASSWORD, window, null);
            assertThat(host.windows()).contains("dev.jasper.vault.manager|Credential Vault|true", "dialog|Change Master Password|true");
            host.requestClose("dialog");
            host.invoke(VaultPlugin.GRANTS, window, null);
            assertThat(host.windows()).containsExactly("dev.jasper.vault.manager|Credential Vault|true");
            var manager = host.windowView("dev.jasper.vault", "Credential Vault").orElseThrow();
            assertThat(dev.jasper.vault.testsupport.KitViews.find(manager, "grants")).as("the grants card is shown").isPresent();
            plugin.lockManager().lock();
            host.invoke(VaultPlugin.CHANGE_PASSWORD, window, null);
            assertThat(plugin.currentUnlock()).as("a locked vault asks to unlock before the form").isNotNull();
            assertThat(host.windows()).doesNotContain("dialog|Change Master Password|true");
            assertThat(host.failures()).isEmpty();
        }
    }); }

    @Test void addCommandsOpenTheManagerAndThenTheirSheet() throws Exception { dev.jasper.vault.testsupport.KitViews.edt(() -> {
        VaultPlugin plugin = plugin();
        try (var host = new FakePluginHost()) {
            host.start(INFO, Set.of(), Set.of(), plugin);
            UUID window = host.addTerminalWindow();
            plugin.lockManager().create("test-password".toCharArray(), false);
            host.runBackground();
            Map<String, String> sheets = Map.of(VaultPlugin.ADD_LOGIN, "Add Login", VaultPlugin.ADD_KEY, "Add SSH Key",
                VaultPlugin.ADD_NOTE, "Add Secure Note");
            sheets.forEach((id, title) -> {
                assertThat(host.actionSpec(id).orElseThrow().defaultBinding()).as("unbound until the user binds it").isEmpty();
                host.invoke(id, window, null);
                assertThat(host.windows()).contains("dev.jasper.vault.manager|Credential Vault|true", "dialog|" + title + "|true");
                host.requestClose("dialog");
            });
            plugin.lockManager().lock();
            host.invoke(VaultPlugin.ADD_LOGIN, window, null);
            assertThat(plugin.currentUnlock()).as("a locked vault asks to unlock before the sheet").isNotNull();
            assertThat(host.windows()).doesNotContain("dialog|Add Login|true");
            assertThat(host.failures()).isEmpty();
        }
    }); }

    @Test void openingTheVaultAndGeneratorUsesOneManagerAndInlineUnlock() throws Exception { dev.jasper.vault.testsupport.KitViews.edt(() -> {
        VaultPlugin plugin = plugin();
        try (var host = new FakePluginHost()) {
            host.start(INFO, Set.of(), Set.of(), plugin);
            UUID window = host.addTerminalWindow();
            plugin.lockManager().create("test-password".toCharArray(), false);
            host.runBackground();
            host.invoke(VaultPlugin.OPEN, window, null);
            host.invoke(VaultPlugin.OPEN, window, null);
            assertThat(host.windows()).containsExactly("dev.jasper.vault.manager|Credential Vault|true");
            host.invoke("dev.jasper.vault.generate_key", window, null);
            assertThat(host.windows()).contains("dialog|Generate SSH Key|true");
            host.requestClose("dialog");
            host.invoke(VaultPlugin.LOCK, window, null);
            host.invoke(VaultPlugin.OPEN, window, null);
            assertThat(host.windows()).as("the existing manager hosts the shared unlock form").containsExactly("dev.jasper.vault.manager|Credential Vault|true");
            assertThat(plugin.currentUnlock()).isNotNull();
            plugin.currentUnlock().submit("test-password".toCharArray());
            host.runBackground();
            assertThat(plugin.lockManager().state()).isEqualTo(LockState.UNLOCKED);
            assertThat(host.windows()).hasSize(1);
            assertThat(host.failures()).isEmpty();
        }
    }); }

    @Test void stoppingDuringUnlockClosesThePromptAndRejectsLateOpen() throws Exception { dev.jasper.vault.testsupport.KitViews.edt(() -> {
        VaultPlugin plugin = plugin();
        try (var host = new FakePluginHost()) {
            host.start(INFO, Set.of(), Set.of(), plugin);
            UUID window = host.addTerminalWindow();
            plugin.lockManager().create("test-password".toCharArray(), false);
            host.runBackground();
            plugin.lockManager().lock();
            host.invoke(VaultPlugin.OPEN, window, null);
            plugin.currentUnlock().submit("test-password".toCharArray());
            host.stopAll();
            host.runBackground();
            assertThat(plugin.lockManager().state()).isEqualTo(LockState.LOCKED);
            assertThat(host.windows()).isEmpty();
        }
    }); }

    @Test void createPromptIsSingletonAndCanBeCancelled() throws Exception { dev.jasper.vault.testsupport.KitViews.edt(() -> {
        VaultPlugin plugin = plugin();
        try (var host = new FakePluginHost()) {
            host.start(INFO, Set.of(), Set.of(), plugin);
            UUID window = host.addTerminalWindow();
            host.invoke(VaultPlugin.OPEN, window, null);
            host.invoke(VaultPlugin.OPEN, window, null);
            assertThat(host.windows()).containsExactly("dialog|Create Vault|true");
            host.requestClose("dialog");
            assertThat(plugin.lockManager().state()).isEqualTo(LockState.NO_VAULT);
            assertThat(host.windows()).isEmpty();
        }
    }); }

    @Test void defaultPluginStartsWithoutTouchingTheDesktopClipboard() throws Exception { dev.jasper.vault.testsupport.KitViews.edt(() -> {
        try (var host = new FakePluginHost()) {
            // The default plugin uses the platform keychain for bound vaults only; this test creates none, and binding is off.
            host.setSetting(INFO.id(), "bind_new_vaults_to_device", false);
            host.start(INFO, Set.of(), Set.of(), new VaultPlugin());
            assertThat(host.failures()).isEmpty();
            assertThat(host.scopes()).hasSize(1);
        }
    }); }

    @Test void contributesSemanticLockIcon() throws Exception { dev.jasper.vault.testsupport.KitViews.edt(() -> {
        try(var host=new FakePluginHost()) {
            host.start(INFO,Set.of(),Set.of(),plugin());
            assertThat(host.failures()).isEmpty();
            assertThat(host.actionSpec("dev.jasper.vault.open").orElseThrow().icon()).contains("lock");
        }
    }); }

    @Test void failedCreationReportsWithoutThePasswordAndLeavesTheFormUsable() throws Exception { dev.jasper.vault.testsupport.KitViews.edt(() -> {
        VaultPlugin plugin = plugin();
        try (var host = new FakePluginHost()) {
            var context = host.start(INFO, Set.of(), Set.of(), plugin);
            UUID window = host.addTerminalWindow();
            host.invoke(VaultPlugin.OPEN, window, null);
            var view = host.windowView(INFO.id(), "Create Vault").orElseThrow();
            assertThat(host.dialogPresentation(INFO.id(), "Create Vault")).as("setup is the kit's modal overlay")
                .contains(dev.jasper.sdk.ui.DialogSpec.Presentation.OVERLAY);
            // An obstructed vault file makes the write fail.
            java.nio.file.Files.createDirectories(context.dataDirectory().resolve("vault.jv.tmp"));
            dev.jasper.vault.testsupport.KitViews.type(view, "password", "synthetic-create-1");
            dev.jasper.vault.testsupport.KitViews.type(view, "confirm", "synthetic-create-1");
            dev.jasper.vault.testsupport.KitViews.press(view, "submit");
            host.runBackground(); host.flush();
            assertThat(host.notifications().lines()).anyMatch(line -> line.contains("Could not create the vault"));
            assertThat(String.join("\n", host.notifications().lines()) + view.spec()).doesNotContain("synthetic-create-1");
            assertThat(host.windows()).containsExactly("dialog|Create Vault|true");
            assertThat(dev.jasper.vault.testsupport.KitViews.button(view, "submit").state().busy()).as("the form is usable again").isFalse();
            assertThat(dev.jasper.vault.testsupport.KitViews.secret(view, "password")).as("no password stays behind").isEmpty();
            host.requestClose("dialog");
            assertThat(dev.jasper.vault.testsupport.KitViews.closed(view)).isTrue();
            assertThat(host.windows()).isEmpty();
            assertThat(plugin.lockManager().state()).as("an abandoned failed create leaves nothing unlocked").isNotEqualTo(LockState.UNLOCKED);
            assertThat(context.kit().failures()).isEmpty();
        }
    }); }

    @Test void closingTheUnlockDialogWhileTheAttemptRunsCancelsAndIgnoresItsResult() throws Exception { dev.jasper.vault.testsupport.KitViews.edt(() -> {
        VaultPlugin plugin = plugin();
        try (var host = new FakePluginHost()) {
            var context = host.start(INFO, Set.of(), Set.of(), plugin);
            UUID window = host.addTerminalWindow();
            plugin.lockManager().create("test-password".toCharArray(), false);
            host.runBackground();
            plugin.lockManager().lock();
            host.invoke(VaultPlugin.OPEN, window, null);
            var view = host.windowView(INFO.id(), "Unlock Vault").orElseThrow();
            assertThat(host.dialogPresentation(INFO.id(), "Unlock Vault")).as("unlocking is the kit's modal overlay")
                .contains(dev.jasper.sdk.ui.DialogSpec.Presentation.OVERLAY);
            dev.jasper.vault.testsupport.KitViews.type(view, "password", "test-password");
            dev.jasper.vault.testsupport.KitViews.press(view, "submit");
            assertThat(dev.jasper.vault.testsupport.KitViews.secret(view, "password")).isEmpty();
            assertThat(host.requestClose("dialog")).isTrue();
            assertThat(dev.jasper.vault.testsupport.KitViews.closed(view)).isTrue();
            host.runBackground(); host.flush();
            assertThat(plugin.lockManager().state()).as("closing during the attempt keeps the vault locked").isEqualTo(LockState.LOCKED);
            assertThat(host.windows()).isEmpty();
            assertThat(host.failures()).isEmpty();
            assertThat(context.kit().failures()).isEmpty();
        }
    }); }

    @Test void grantAndPickerDialogsAnswerThroughTheirViewsAndEscapeDenies() throws Exception { dev.jasper.vault.testsupport.KitViews.edt(() -> {
        VaultPlugin plugin = plugin();
        try (var host = new FakePluginHost()) {
            host.start(INFO, Set.of(), Set.of(), plugin);
            AtomicReference<VaultApi> api = new AtomicReference<>();
            host.start(SSH, Set.of("dev.jasper.vault"), Set.of(), context -> api.set(context.services().require(VaultApi.class)));
            host.addTerminalWindow();
            plugin.lockManager().create("hunter2!".toCharArray(), false);
            host.runBackground();
            UUID id = UUID.randomUUID();
            plugin.lockManager().vault().accounts().add(new Account(id, "prod", "deploy", new Auth.Password("s3cret".toCharArray()), Instant.EPOCH, Instant.EPOCH));
            plugin.lockManager().save(); host.runBackground();
            CompletableFuture<Optional<Credential>> denied = api.get().credential(id);
            var grant = host.windowView(INFO.id(), "Allow SSH to use prod?").orElseThrow();
            var form = dev.jasper.vault.testsupport.KitViews.node(grant, "grant", dev.jasper.sdk.ui.Nodes.Form.class);
            dev.jasper.vault.testsupport.KitViews.press(grant, form.cancelAction());
            assertThat(denied.join()).isEmpty();
            assertThat(host.windows()).isEmpty();
            assertThat(dev.jasper.vault.testsupport.KitViews.closed(grant)).isTrue();
            CompletableFuture<Optional<Credential>> allowed = api.get().credential(id);
            dev.jasper.vault.testsupport.KitViews.press(host.windowView(INFO.id(), "Allow SSH to use prod?").orElseThrow(), "allow-once");
            try (Credential credential = allowed.join().orElseThrow()) { assertThat(credential.password()).isEqualTo("s3cret".toCharArray()); }
            assertThat(host.windows()).isEmpty();
        }
    }); }
}
