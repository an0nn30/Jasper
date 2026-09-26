package dev.jasper.app.plugins;

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
                new dev.jasper.app.appearance.ThemeController(dev.jasper.app.config.Appearance.LIGHT);
                host.start(new PluginInfo("dev.jasper.vault", "Credential Vault", "0.2.0", Set.of(dev.jasper.sdk.Capabilities.PALETTE_CONTRIBUTE)), Set.of(), Set.of(), plugin);
                var consumer = host.start(new PluginInfo("dev.jasper.remote", "Remote", "0.2.0", Set.of()), Set.of("dev.jasper.vault"), Set.of(), context -> api.set(context.services().require(apiType)));
                UUID id = host.addTerminalWindow(); host.activateTerminalWindow(id); owner.set(consumer.terminals().window(id).orElseThrow());
                host.invoke("dev.jasper.vault.open", id, null);
                var create = host.windowContent("dev.jasper.vault", "Create Vault").orElseThrow();
                assertThat(((JPanel) create).getUI()).as("host LAF is visible to plugin panels").isNotNull();
                fields(create, JPasswordField.class).forEach(f -> f.setText("fixture-password")); fields(create, JCheckBox.class).forEach(c -> c.setSelected(false));
                button(create, "Create Vault").doClick();
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
                var prompt = host.windowContent("dev.jasper.vault", "Import SSH keys").orElseThrow();
                render(prompt, "passphrase");
                fields(prompt, JPasswordField.class).getFirst().setText("phrase"); button(prompt, "Unlock key").doClick();
            });
            settle(host);
            onEdt(() -> {
                var prompt = host.windowContent("dev.jasper.vault", "Import SSH keys").orElseThrow();
                assertThat(button(prompt, "Import and use").isEnabled()).isTrue(); render(prompt, "review"); button(prompt, "Import and use").doClick();
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
                var manager = host.windowContent("dev.jasper.vault", "Credential Vault").orElseThrow(); render(manager, "manager");
                assertThat(host.failures()).isEmpty();
            });
        } finally { onEdt(() -> new dev.jasper.app.appearance.ThemeController()); }
    }
    static List<Path> jars(Path directory) throws Exception { try (var files = Files.list(directory)) { return files.filter(p -> p.toString().endsWith(".jar")).toList(); } }
    static void settle(FakePluginHost host) throws Exception { for (int i = 0; i < 8; i++) { host.runBackground(); onEdt(host::flush); } }
    static <T extends Component> List<T> fields(Container root, Class<T> type) {
        var result = new ArrayList<T>(); for (Component c : root.getComponents()) { if (type.isInstance(c)) result.add(type.cast(c)); if (c instanceof Container nested) result.addAll(fields(nested, type)); } return result;
    }
    static JButton button(Container root, String text) { return fields(root, JButton.class).stream().filter(b -> text.equals(b.getText())).findFirst().orElseThrow(); }
    static void layout(Container c) { c.doLayout(); for (Component child : c.getComponents()) if (child instanceof Container nested) layout(nested); }
    static void render(JComponent component, String name) {
        try {
            component.addNotify();
            component.setSize(name.equals("manager") ? 980 : 660, name.equals("manager") ? 580 : 360);
            layout(component); layout(component); component.validate();
            if (name.equals("passphrase")) {
                var field = fields(component, JPasswordField.class).getFirst();
                assertThat(SwingUtilities.convertRectangle(field.getParent(), field.getBounds(), component).y).isBetween(0, component.getHeight() - 20);
            }
            if (name.equals("manager")) assertThat(fields(component, JList.class).getFirst().getModel().getSize()).isEqualTo(1);
            Path output = Path.of(System.getProperty("java.io.tmpdir"), "jasper-managed-import-previews"); Files.createDirectories(output);
            for (int scale : List.of(1, 2)) {
                var image = new java.awt.image.BufferedImage(component.getWidth() * scale, component.getHeight() * scale, java.awt.image.BufferedImage.TYPE_INT_ARGB);
                var graphics = image.createGraphics(); graphics.scale(scale, scale); component.printAll(graphics); graphics.dispose();
                javax.imageio.ImageIO.write(image, "png", output.resolve("modern-" + name + "-" + scale + "x.png").toFile());
            }
        } catch (Exception failure) { throw new AssertionError(failure); }
        finally { component.removeNotify(); }
    }
}
