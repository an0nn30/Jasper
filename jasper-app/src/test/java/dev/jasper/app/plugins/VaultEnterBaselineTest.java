package dev.jasper.app.plugins;

import dev.jasper.sdk.PluginInfo;
import dev.jasper.sdk.plugin.Plugin;
import dev.jasper.sdk.plugin.PluginContext;
import dev.jasper.sdk.terminal.WindowHandle;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static dev.jasper.app.plugins.AppContractTest.onEdt;
import static dev.jasper.app.plugins.AppContractTest.onEdtValue;
import static dev.jasper.app.plugins.StagedVaultHost.*;
import static org.assertj.core.api.Assertions.*;

/**
 * Enter in the staged Vault, through the real host's kit components and Swing's key-binding path, matches baseline
 * f16ae32f (Task 7 review fix round 1): the key import unlocks only from its passphrase field and never commits; the
 * manager's inline unlock is the window's default, so Enter on a footer button unlocks.
 */
class VaultEnterBaselineTest {
    static final PluginInfo CONSUMER = new PluginInfo("test.consumer", "Consumer", "0.1.0", Set.of());
    @TempDir Path root;

    @Test void enterOnTheManagersFooterUnlocksWhileTheInlineUnlockShows() throws Exception {
        try (var vault = new StagedVaultHost(root.resolve("home"), true)) {
            vault.createVault("synthetic-enter-pass");
            var manager = vault.surface("Credential Vault");
            vault.invoke("dev.jasper.vault.lock");
            onEdt(() -> click(manager, "unlock"));
            await(() -> named(manager.holder(), "unlock.password.input") != null, "inline unlock form");
            onEdt(() -> {
                type(manager, "unlock.password.input", "synthetic-enter-pass");
                enter(require(manager, "okay"));
            });
            await(() -> node(manager, "search") != null, "Enter on Okay pressed Unlock and the entries came back");
            assertThat(onEdtValue(() -> node(manager, "unlock.password"))).isNull();
            assertThat(onEdtValue(() -> ((dev.jasper.ui.model.Nodes.Button) node(manager, "okay")).kind()))
                .as("Okay is the default again once unlocked").isEqualTo(dev.jasper.ui.model.Nodes.ButtonKind.PRIMARY);
            onEdt(() -> enter(require(manager, "okay")));
            await(() -> vault.surfaceOrNull("Credential Vault") == null, "Enter on Okay closes the unlocked manager");
            assertThat(vault.problems).isEmpty();
        }
    }

    @Test void keyImportUnlocksOnlyFromThePassphraseFieldAndEnterNeverCommits() throws Exception {
        try (var vault = new StagedVaultHost(root.resolve("home"), true)) {
            vault.createVault("synthetic-enter-pass");
            onEdt(() -> vault.surface("Credential Vault").requestClose());
            Class<?> apiType = vault.loader.loadClass("dev.jasper.vault.api.VaultApi");
            var api = new AtomicReference<Object>(); var context = new AtomicReference<PluginContext>();
            vault.start(CONSUMER, Set.of(UnboundVault.ID), new Plugin() {
                @Override public void start(PluginContext started) { context.set(started); api.set(started.services().require(apiType)); }
            });
            Class<?> algorithm = vault.loader.loadClass("dev.jasper.vault.keygen.KeyAlgorithm");
            Class<?> generator = vault.loader.loadClass("dev.jasper.vault.keygen.KeyGenerator");
            Object key = generator.getMethod("generate", algorithm, String.class, String.class, char[].class)
                .invoke(generator.getConstructor(Path.class).newInstance(root.resolve("keys")), algorithm.getField("ED25519").get(null),
                    "fixture", "fixture", "synthetic-key-phrase".toCharArray());
            Path source = (Path) key.getClass().getMethod("privatePath").invoke(key);
            Class<?> sourceType = vault.loader.loadClass("dev.jasper.vault.api.SshKeySource");
            Object request = sourceType.getConstructor(UUID.class, String.class, Path.class).newInstance(UUID.randomUUID(), "Fixture key", source);
            var result = new AtomicReference<CompletableFuture<?>>();
            onEdt(() -> {
                WindowHandle owner = context.get().terminals().window(vault.window).orElseThrow();
                try { result.set((CompletableFuture<?>) apiType.getMethod("importSshKeys", WindowHandle.class, List.class, List.class).invoke(api.get(), owner, List.of(request), List.of())); }
                catch (ReflectiveOperationException e) { throw new AssertionError(e); }
            });
            var prompt = vault.awaitSurface("Import SSH keys");
            await(() -> node(prompt, "passphrase") != null && node(prompt, "passphrase").state().visible(), "the passphrase is asked for");
            String status = onEdtValue(() -> ((dev.jasper.ui.model.Nodes.Text) node(prompt, "status")).text());
            onEdt(() -> {
                type(prompt, "passphrase.input", "synthetic-key-phrase");
                enter(require(prompt, "cancel"));
                enter(keyTarget(require(prompt, "keys")));
                enter(require(prompt, "unlock-key"));
            });
            settle();
            onEdt(() -> {
                assertThat(((dev.jasper.ui.model.Nodes.Text) node(prompt, "status")).text()).as("Enter outside the field did not unlock").isEqualTo(status);
                assertThat(node(prompt, "passphrase").state().visible()).isTrue();
                enter(require(prompt, "passphrase.input"));
            });
            await(() -> ((dev.jasper.ui.model.Nodes.Button) node(prompt, "commit")).state().enabled(), "Enter in the passphrase field unlocked the key");
            onEdt(() -> {
                enter(require(prompt, "commit"));
                enter(require(prompt, "cancel"));
                enter(keyTarget(require(prompt, "keys")));
            });
            settle();
            assertThat(result.get()).as("Enter never commits an import").isNotDone();
            onEdt(() -> click(prompt, "cancel"));
            await(() -> result.get().isDone(), "Cancel answers the request");
            assertThat((java.util.Optional<?>) result.get().join()).isEmpty();
            assertThat(vault.problems).isEmpty();
        }
    }
}
