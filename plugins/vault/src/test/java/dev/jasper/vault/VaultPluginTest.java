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
import dev.jasper.vault.ui.SecretClipboard;
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

    @Test void stopLocksTheVaultEvenWhenClipboardCleanupFails() {
        String[] contents = {""};
        SecretClipboard clipboard = new SecretClipboard(text -> {
            if (text.isEmpty()) throw new IllegalStateException("clipboard busy");
            contents[0] = text;
        }, () -> Optional.of(contents[0]), clear -> { });
        VaultPlugin plugin = new VaultPlugin(context -> new DeviceSecrets(
            new KeychainStore("Linux", command -> { throw new UncheckedIOException(new IOException("none")); }, context.dataDirectory().resolve("x")),
            new FileStore(context.dataDirectory().resolve("device.secret"))), Runnable::run, () -> clipboard);
        try (var host = new FakePluginHost()) {
            host.start(INFO, Set.of(), Set.of(), plugin);
            plugin.lockManager().create("hunter2!".toCharArray(), false);
            host.runBackground();
            host.flush();
            clipboard.copySecret("deliberate copy");
            assertThat(plugin.lockManager().state()).isEqualTo(LockState.UNLOCKED);
            assertThatCode(plugin::stop).doesNotThrowAnyException();
            assertThat(plugin.lockManager().state()).isEqualTo(LockState.LOCKED);
        }
    }

    @Test void publishesTheServiceActionsAndLockStateEvents() {
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
            assertThat(host.actions()).contains("dev.jasper.vault.open|Open Vault...|true", "dev.jasper.vault.lock|Lock Vault|false");
            assertThat(host.scopes()).containsExactly("dev.jasper.vault.scope|Vault|copy_password,copy_username,open");
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
    }

    @Test void aConsumerFetchesThroughUnlockAndGrantDialogs() {
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
    }

    @Test void autoLockFollowsTheSettingsAndStopLocks() {
        VaultPlugin plugin = plugin();
        try (var host = new FakePluginHost()) {
            host.setConfig("dev.jasper.vault", Map.of("auto_lock_minutes", 1L));
            host.start(INFO, Set.of(), Set.of(), plugin);
            plugin.lockManager().create("hunter2!".toCharArray(), false);
            host.runBackground();
            assertThat(plugin.timer().timeout()).isEqualTo(Duration.ofMinutes(1));
            plugin.clock = 0;
            plugin.timer().touch();
            plugin.clock = Duration.ofMinutes(1).toMillis();
            plugin.tick();
            assertThat(plugin.lockManager().state()).isEqualTo(LockState.LOCKED);
            host.setConfig("dev.jasper.vault", Map.of("auto_lock_minutes", 0L));
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
    }

    @Test void settingsResolveTheKeysDirectoryAndDefaults() {
        try (var host = new FakePluginHost()) {
            host.setConfig("dev.jasper.vault", Map.of("keys_directory", "/elsewhere/keys", "bind_new_vaults_to_device", false));
            var context = host.start(INFO, Set.of(), Set.of(), plugin());
            VaultSettings settings = VaultSettings.read(context.config(), context.dataDirectory());
            assertThat(settings.keysDirectory()).isEqualTo(java.nio.file.Path.of("/elsewhere/keys"));
            assertThat(settings.bindByDefault()).isFalse();
            assertThat(settings.autoLock()).isEqualTo(Duration.ofMinutes(15));
        }
        try (var host = new FakePluginHost()) {
            var context = host.start(INFO, Set.of(), Set.of(), plugin());
            assertThat(VaultSettings.read(context.config(), context.dataDirectory()).keysDirectory()).isEqualTo(context.dataDirectory().resolve("keys"));
        }
    }
    @Test void thePadlockFollowsTheLockStateAndTheRailOpensTheVault() {
        VaultPlugin plugin = plugin();
        try (var host = new FakePluginHost()) {
            host.start(INFO, Set.of(), Set.of(), plugin);
            assertThat(host.rail()).containsExactly(VaultPlugin.OPEN);
            assertThat(host.status()).containsExactly("dev.jasper.vault.status|RIGHT|Vault|No vault — click to create one|dev.jasper.vault.open");
            plugin.lockManager().create("hunter2!".toCharArray(), false);
            host.runBackground();
            plugin.clock = 0;
            plugin.timer().touch();
            plugin.tick();
            assertThat(host.status()).containsExactly("dev.jasper.vault.status|RIGHT|Vault|Vault unlocked · locks in 15 min|dev.jasper.vault.lock");
            plugin.clock = Duration.ofMinutes(14).toMillis() + 1;
            plugin.tick();
            assertThat(host.status().getFirst()).contains("locks in 1 min");
            plugin.lockManager().lock();
            assertThat(host.status()).containsExactly("dev.jasper.vault.status|RIGHT|Vault|Vault locked|dev.jasper.vault.open");
            assertThat(VaultPlugin.tooltip(LockState.UNLOCKED, Duration.ZERO)).isEqualTo("Vault unlocked");
            assertThat(VaultPlugin.tooltip(LockState.UNLOCKED, Duration.ofSeconds(30))).isEqualTo("Vault unlocked · locks in 1 min");
        }
    }

    @Test void openingTheVaultAndGeneratorUsesOneManagerAndInlineUnlock() {
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
    }

    @Test void stoppingDuringUnlockClosesThePromptAndRejectsLateOpen() {
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
    }

    @Test void createPromptIsSingletonAndCanBeCancelled() {
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
    }

    @Test void defaultPluginStartsWithoutTouchingTheDesktopClipboard() {
        try (var host = new FakePluginHost()) {
            host.start(INFO, Set.of(), Set.of(), new VaultPlugin());
            assertThat(host.failures()).isEmpty();
            assertThat(host.scopes()).hasSize(1);
        }
    }

    @Test void requestsBothIconFamiliesForEitherSkin() {
        for (boolean retro : new boolean[]{false, true}) try (var host = new FakePluginHost()) {
            host.setRetroIcons(retro);

            var delegate = plugin();
            var icons = new java.util.ArrayList<dev.jasper.sdk.testing.FakeSkinIcon>();
            host.start(INFO, Set.of(), Set.of(), new dev.jasper.sdk.plugin.Plugin() {
                public void start(dev.jasper.sdk.plugin.PluginContext context) throws Exception {
                    var appearance = (dev.jasper.sdk.ui.Appearance) java.lang.reflect.Proxy.newProxyInstance(
                        getClass().getClassLoader(), new Class<?>[]{dev.jasper.sdk.ui.Appearance.class}, (proxy, method, args) -> {
                            try {
                                Object value = method.invoke(context.appearance(), args);
                                if (value instanceof dev.jasper.sdk.testing.FakeSkinIcon icon) icons.add(icon);
                                return value;
                            } catch (java.lang.reflect.InvocationTargetException e) { throw e.getCause(); }
                        });
                    var wrapped = (dev.jasper.sdk.plugin.PluginContext) java.lang.reflect.Proxy.newProxyInstance(
                        getClass().getClassLoader(), new Class<?>[]{dev.jasper.sdk.plugin.PluginContext.class}, (proxy, method, args) -> {
                            if (method.getName().equals("appearance")) return appearance;
                            try { return method.invoke(context, args); }
                            catch (java.lang.reflect.InvocationTargetException e) { throw e.getCause(); }
                        });
                    delegate.start(wrapped);
                }
                public void stop() { delegate.stop(); }
            });
            assertThat(host.failures()).isEmpty();
            assertThat(icons).extracting(dev.jasper.sdk.testing.FakeSkinIcon::retroIcon).containsExactly(dev.jasper.sdk.ui.OldGnomeIcon.LOCK, dev.jasper.sdk.ui.OldGnomeIcon.UNLOCK);
            assertThat(icons).allSatisfy(icon -> assertThat(icon.retro()).isEqualTo(retro));
        }
    }
}
