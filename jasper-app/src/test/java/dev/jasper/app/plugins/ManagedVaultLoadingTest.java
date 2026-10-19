package dev.jasper.app.plugins;

import dev.jasper.app.appearance.ThemeTestSupport;
import dev.jasper.sdk.PluginInfo;
import dev.jasper.sdk.plugin.Plugin;
import dev.jasper.sdk.testing.FakePluginHost;
import dev.jasper.sdk.terminal.WindowHandle;
import java.awt.*;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static dev.jasper.app.plugins.AppContractTest.onEdt;
import static org.assertj.core.api.Assertions.*;

/** Real staged API/parsing dependency boundary plus headless forms. */
class ManagedVaultLoadingTest {
    @TempDir Path root;
    @Test void stagedVaultImportsThroughExportedApiAndRendersForms() throws Exception {
        Path staged = Path.of(System.getProperty("jasper.stagedPlugins"));
        try (var loader = new PluginClassLoader("dev.jasper.vault", jars(staged.resolve("dev.jasper.vault")), getClass().getClassLoader(), Map.of());
             var remote = new PluginClassLoader("dev.jasper.remote", jars(staged.resolve("dev.jasper.remote")), getClass().getClassLoader(), Map.of("dev.jasper.vault.api", loader));
             var host = new FakePluginHost(root.resolve("home"))) {
            Class<?> apiType = loader.loadClass("dev.jasper.vault.api.VaultApi");
            assertThat(remote.loadClass(apiType.getName())).isSameAs(apiType);
            assertThat(loader.loadClass("org.apache.sshd.common.util.security.SecurityUtils").getClassLoader()).isSameAs(loader);
            Class<?> algorithm = loader.loadClass("dev.jasper.vault.keygen.KeyAlgorithm");
            Object ed = algorithm.getField("ED25519").get(null);
            Class<?> generator = loader.loadClass("dev.jasper.vault.keygen.KeyGenerator");
            Object key = generator.getMethod("generate", algorithm, String.class, String.class, char[].class)
                .invoke(generator.getConstructor(Path.class).newInstance(root.resolve("keys")), ed, "fixture", "fixture", "phrase".toCharArray());
            Path source = (Path) key.getClass().getMethod("privatePath").invoke(key);
            Plugin plugin = (Plugin) loader.loadClass("dev.jasper.vault.VaultPlugin").getConstructor().newInstance();
            var api = new AtomicReference<Object>(); var owner = new AtomicReference<WindowHandle>();
            onEdt(() -> {
                ThemeTestSupport.controller(false);
                UnboundVault.settings(host); // the staged no-arg plugin uses the platform keychain for bound vaults only
                host.start(new PluginInfo("dev.jasper.vault", "Credential Vault", "0.2.0", Set.of(dev.jasper.sdk.Capabilities.PALETTE_CONTRIBUTE)), Set.of(), Set.of(), plugin);
                var consumer = host.start(new PluginInfo("dev.jasper.remote", "Remote", "0.2.0", Set.of()), Set.of("dev.jasper.vault"), Set.of(), context -> api.set(context.services().require(apiType)));
                UUID id = host.addTerminalWindow(); host.activateTerminalWindow(id); owner.set(consumer.terminals().window(id).orElseThrow());
                host.invoke("dev.jasper.vault.open", id, null);
                var create = host.windowView("dev.jasper.vault", "Create Vault").orElseThrow();
                assertThat(node(create.spec().root(), "password")).isInstanceOf(dev.jasper.sdk.ui.Nodes.Secret.class);
                UnboundVault.assertUnbound(create);
                create.setSecret("password", "fixture-password".toCharArray()); create.setSecret("confirm", "fixture-password".toCharArray());
                UnboundVault.assertUnbound(create);
                create.emit(new dev.jasper.sdk.ui.UiEvent.Action("submit"));
                assertThat(create.spec().toString()).doesNotContain("fixture-password");
            });
            settle(host);
            var result = new AtomicReference<CompletableFuture<?>>();
            Class<?> sourceType = loader.loadClass("dev.jasper.vault.api.SshKeySource");
            Object request = sourceType.getConstructor(UUID.class, String.class, Path.class).newInstance(UUID.randomUUID(), "Shared deployment key", source);
            onEdt(() -> {
                try { result.set((CompletableFuture<?>) apiType.getMethod("importSshKeys", WindowHandle.class, List.class, List.class).invoke(api.get(), owner.get(), List.of(request), List.of())); }
                catch (ReflectiveOperationException e) { throw new AssertionError(e); }
            });
            settle(host);
            assertThat(result.get()).as("import request before passphrase: " + host.failures()).isNotDone();
            onEdt(() -> {
                var prompt = host.windowView("dev.jasper.vault", "Import SSH keys").orElseThrow();
                assertThat(node(prompt.spec().root(), "passphrase").state().visible()).as("the passphrase is asked for").isTrue();
                prompt.setSecret("passphrase", "phrase".toCharArray()); prompt.emit(new dev.jasper.sdk.ui.UiEvent.Action("unlock-key"));
            });
            settle(host);
            onEdt(() -> {
                var prompt = host.windowView("dev.jasper.vault", "Import SSH keys").orElseThrow();
                var commit = (dev.jasper.sdk.ui.Nodes.Button) node(prompt.spec().root(), "commit");
                assertThat(commit.label()).isEqualTo("Import and use"); assertThat(commit.state().enabled()).isTrue();
                assertThat(node(prompt.spec().root(), "passphrase").state().visible()).isFalse();
                prompt.emit(new dev.jasper.sdk.ui.UiEvent.Action("commit"));
            });
            settle(host);
            assertThat((Optional<?>) result.get().get(5, TimeUnit.SECONDS)).isPresent();
            Object answer = ((Optional<?>) result.get().join()).orElseThrow();
            Object descriptor = ((Map<?, ?>) answer.getClass().getMethod("keys").invoke(answer)).values().iterator().next();
            UUID credentialId = (UUID) descriptor.getClass().getMethod("id").invoke(descriptor);
            var fetched = new AtomicReference<CompletableFuture<?>>();
            onEdt(() -> {
                try { fetched.set((CompletableFuture<?>) apiType.getMethod("credential", UUID.class).invoke(api.get(), credentialId)); }
                catch (ReflectiveOperationException e) { throw new AssertionError(e); }
            });
            settle(host);
            try (var credential = (AutoCloseable) ((Optional<?>) fetched.get().join()).orElseThrow()) {
                byte[] bytes = (byte[]) ((Optional<?>) credential.getClass().getMethod("keyBytes").invoke(credential)).orElseThrow();
                Class.forName("dev.jasper.remote.client.Connections", true, remote);
                Class<?> passwords = remote.loadClass("org.apache.sshd.common.config.keys.FilePasswordProvider");
                Object phrase = passwords.getMethod("of", String.class).invoke(null, "phrase");
                Class<?> named = remote.loadClass("org.apache.sshd.common.NamedResource");
                Object resource = named.getMethod("ofName", String.class).invoke(null, "fixture");
                Object pairs = remote.loadClass("org.apache.sshd.common.util.security.SecurityUtils")
                    .getMethod("loadKeyPairIdentities", remote.loadClass("org.apache.sshd.common.session.SessionContext"), named, java.io.InputStream.class, passwords)
                    .invoke(null, null, resource, new java.io.ByteArrayInputStream(bytes), phrase);
                var pair = (java.security.KeyPair) ((Iterable<?>) pairs).iterator().next();
                assertThat(pair.getPrivate().getClass().getClassLoader()).isSameAs(remote);
            }
            onEdt(() -> {
                host.invoke("dev.jasper.vault.open", owner.get().id(), null);
                var manager = host.contentView("dev.jasper.vault", "Credential Vault").orElseThrow();
                assertThat(((dev.jasper.sdk.ui.Nodes.ItemList) node(manager.spec().root(), "rows")).items()).hasSize(1);
                assertThat(host.failures()).isEmpty();
            });
        } finally { onEdt(() -> ThemeTestSupport.controller(true)); }
    }
    static List<Path> jars(Path directory) throws Exception { try (var files = Files.list(directory)) { return files.filter(p -> p.toString().endsWith(".jar")).toList(); } }
    static void settle(FakePluginHost host) throws Exception { for (int i = 0; i < 8; i++) { host.runBackground(); onEdt(host::flush); } }
    /** A descriptor node by id, through the containers Vault views use. */
    static dev.jasper.sdk.ui.Nodes.Node node(dev.jasper.sdk.ui.Nodes.Node root, String id) {
        if (root.id().equals(id)) return root;
        var children = new ArrayList<dev.jasper.sdk.ui.Nodes.Node>();
        switch (root) {
            case dev.jasper.sdk.ui.Nodes.Form n -> { children.addAll(n.sections()); n.preview().ifPresent(children::add); children.addAll(n.actions()); }
            case dev.jasper.sdk.ui.Nodes.Stack n -> children.addAll(n.children());
            case dev.jasper.sdk.ui.Nodes.Card n -> children.addAll(n.children());
            case dev.jasper.sdk.ui.Nodes.Split n -> children.addAll(n.children());
            case dev.jasper.sdk.ui.Nodes.Panel n -> { children.add(n.header()); n.searchOrNavigation().ifPresent(children::add); children.add(n.body()); n.footer().ifPresent(children::add); }
            case dev.jasper.sdk.ui.Nodes.FormRow n -> children.add(n.control());
            case dev.jasper.sdk.ui.Nodes.Empty n -> children.addAll(n.actions());
            default -> { }
        }
        for (var child : children) { var found = node(child, id); if (found != null) return found; }
        return null;
    }
}
