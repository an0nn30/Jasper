package dev.jasper.app.plugins;

import dev.jasper.app.contributions.Contributions;
import dev.jasper.app.windows.AuxiliarySurface;
import dev.jasper.app.windows.AuxiliaryWindows;
import dev.jasper.sdk.PluginInfo;
import dev.jasper.sdk.contracts.ThemeMigrationCapture;
import dev.jasper.sdk.plugin.Plugin;
import java.awt.Component;
import java.awt.Container;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import javax.swing.AbstractButton;
import javax.swing.JComponent;
import javax.swing.text.JTextComponent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static dev.jasper.app.plugins.AppContractTest.onEdt;
import static dev.jasper.app.plugins.AppContractTest.onEdtValue;
import static org.assertj.core.api.Assertions.*;

/**
 * Real-host, dark and light pixel evidence of the migrated Vault (Plan 2 Task 7, controller ruling 2): the staged
 * plugin runs in the application's plugin host with headless windows, and its kit views render through the host
 * translation. Output goes to build/theme-migration-evidence/vault-hosted; the frozen before images are untouched.
 * Reusable shape for Tasks 8–10: stage, start in the real host, drive by kit component names, capture surfaces.
 * Passwords are synthetic, the vault is never bound to a device (no keychain) and no clipboard is touched.
 */
class VaultRenderEvidenceTest {
    static final String FAMILY = "vault-hosted";
    @TempDir Path root;

    @Test void capturesTheStagedVaultInTheRealHostDarkAndLight() throws Exception {
        for (String theme : List.of("dark", "light")) capture(theme, root.resolve(theme));
    }

    private void capture(String theme, Path data) throws Exception {
        Path staged = Path.of(System.getProperty("jasper.stagedPlugins")).resolve("dev.jasper.vault");
        var controller = onEdtValue(dev.jasper.app.appearance.ThemeController::new);
        onEdt(() -> controller.configure(theme.equals("dark") ? dev.jasper.app.appearance.Theme.DARK : dev.jasper.app.appearance.Theme.JASPER_LIGHT));
        var contributions = onEdtValue(Contributions::new);
        AuxiliaryWindows auxiliary = onEdtValue(AppContractTest::headlessWindows);
        TerminalFixture terminals = onEdtValue(TerminalFixture::new);
        var problems = new CopyOnWriteArrayList<String>();
        // Never bind a test vault to the device: no keychain is read or written. The form's switch starts off.
        Files.createDirectories(data.resolve("dev.jasper.vault"));
        Files.writeString(data.resolve("dev.jasper.vault").resolve("dev.jasper.vault.toml"), "bind_new_vaults_to_device = false\n");
        var host = AppContractTest.host(data, Duration.ofMillis(200), contributions, new AtomicBoolean(theme.equals("dark")), auxiliary, terminals.registry,
            problems, new CopyOnWriteArrayList<>());
        try (var loader = new PluginClassLoader("dev.jasper.vault", ManagedVaultLoadingTest.jars(staged), getClass().getClassLoader(), Map.of())) {
            Plugin plugin = (Plugin) loader.loadClass("dev.jasper.vault.VaultPlugin").getConstructor().newInstance();
            var window = new AtomicReference<UUID>();
            onEdt(() -> {
                host.start(new HostedPlugin(new PluginInfo("dev.jasper.vault", "Credential Vault", "0.2.0", Set.of(dev.jasper.sdk.Capabilities.PALETTE_CONTRIBUTE)),
                    Set.of(), Set.of(), Set.of("dev.jasper.vault.api"), loader, () -> plugin));
                window.set(terminals.addWindow()); terminals.activateWindow(window.get());
            });
            invoke(contributions, "dev.jasper.vault.open", window.get());
            var create = surface(auxiliary, "Create Vault");
            save(theme, "create", create, 580, 380);
            onEdt(() -> click(create, "submit"));
            save(theme, "create-invalid", create, 580, 380);
            onEdt(() -> {
                assertThat(toggle(create, "bind").checked()).as("the test vault is never bound to the device keychain").isFalse();
                type(create, "password.input", "synthetic-host-pass"); type(create, "confirm.input", "synthetic-host-pass");
                assertThat(toggle(create, "bind").checked()).isFalse();
                click(create, "submit");
            });
            await(() -> surfaceOrNull(auxiliary, "Credential Vault") != null, "the manager opens once the vault exists");
            var manager = surface(auxiliary, "Credential Vault");
            save(theme, "manager-empty", manager, 900, 580);
            invoke(contributions, "dev.jasper.vault.generate_key", window.get());
            var generator = surface(auxiliary, "Generate SSH Key");
            save(theme, "generate-key", generator, 640, 560);
            onEdt(() -> generator.requestClose());
            invoke(contributions, "dev.jasper.vault.lock", window.get());
            save(theme, "manager-locked", manager, 900, 580);
            onEdt(() -> click(manager, "unlock"));
            save(theme, "manager-unlock", manager, 900, 580);
            onEdt(() -> manager.requestClose());
            invoke(contributions, "dev.jasper.vault.open", window.get());
            var unlock = surface(auxiliary, "Unlock Vault");
            save(theme, "unlock", unlock, 580, 300);
            onEdt(() -> unlock.requestClose());
            assertThat(problems).isEmpty();
        } finally {
            var pending = new AtomicReference<List<CompletableFuture<?>>>(List.of());
            onEdt(() -> pending.set(host.stop()));
            CompletableFuture.allOf(pending.get().toArray(CompletableFuture[]::new)).join();
            onEdt(() -> { auxiliary.close(); controller.close(); new dev.jasper.app.appearance.ThemeController(); });
        }
    }

    static void invoke(Contributions contributions, String action, UUID window) {
        onEdt(() -> assertThat(contributions.action(action)).as(action).hasValueSatisfying(entry ->
            entry.invoke(new Contributions.Invocation(window, Optional.empty()))));
    }

    static AuxiliarySurface surfaceOrNull(AuxiliaryWindows auxiliary, String title) {
        return onEdtValue(() -> auxiliary.open().stream().filter(s -> s.title().equals(title) && !s.closed()).findFirst().orElse(null));
    }

    static AuxiliarySurface surface(AuxiliaryWindows auxiliary, String title) {
        var found = surfaceOrNull(auxiliary, title);
        assertThat(found).as("open surface %s", title).isNotNull();
        return found;
    }

    static void await(BooleanSupplier condition, String description) throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(20).toNanos();
        while (!onEdtValue(condition::getAsBoolean)) {
            if (System.nanoTime() > deadline) throw new AssertionError("Timed out: " + description);
            Thread.sleep(20);
        }
    }

    static Component named(Container root, String name) {
        if (name.equals(root.getName())) return root;
        for (var child : root.getComponents()) {
            if (name.equals(child.getName())) return child;
            if (child instanceof Container nested) { var found = named(nested, name); if (found != null) return found; }
        }
        return null;
    }

    /** The live descriptor of a switch in the surface's kit view. */
    static dev.jasper.ui.model.Nodes.Toggle toggle(AuxiliarySurface surface, String id) {
        var view = kitView(surface.holder());
        assertThat(view).as("kit view in %s", surface.title()).isNotNull();
        var found = find(view.spec().root(), id);
        assertThat(found).as("switch %s", id).isInstanceOf(dev.jasper.ui.model.Nodes.Toggle.class);
        return (dev.jasper.ui.model.Nodes.Toggle) found;
    }

    static dev.jasper.ui.swing.KitView kitView(Container root) {
        for (var child : root.getComponents()) {
            if (child instanceof dev.jasper.ui.swing.KitView view) return view;
            if (child instanceof Container nested) { var found = kitView(nested); if (found != null) return found; }
        }
        return null;
    }

    static dev.jasper.ui.model.Nodes.Node find(dev.jasper.ui.model.Nodes.Node node, String id) {
        if (node.id().equals(id)) return node;
        var children = switch (node) {
            case dev.jasper.ui.model.Nodes.Form n -> n.sections();
            case dev.jasper.ui.model.Nodes.Stack n -> n.children();
            case dev.jasper.ui.model.Nodes.SettingRow n -> List.of(n.control());
            default -> List.<dev.jasper.ui.model.Nodes.Node>of();
        };
        for (var child : children) { var found = find(child, id); if (found != null) return found; }
        return null;
    }

    static void click(AuxiliarySurface surface, String name) {
        var button = (AbstractButton) named(surface.holder(), name);
        assertThat(button).as("button %s", name).isNotNull();
        button.doClick(0);
    }

    static void type(AuxiliarySurface surface, String name, String text) {
        var input = (JTextComponent) named(surface.holder(), name);
        assertThat(input).as("input %s", name).isNotNull();
        input.setText(text);
    }

    static void save(String theme, String state, AuxiliarySurface surface, int width, int height) throws Exception {
        onEdt(() -> {
            JComponent holder = surface.holder();
            ThemeMigrationCapture.save(FAMILY, theme, state, holder, width, height);
        });
        assertThat(Files.size(Path.of("build/theme-migration-evidence", FAMILY, theme, state + ".png"))).isPositive();
    }
}
