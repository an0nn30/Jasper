package dev.jasper.app.plugins;

import dev.jasper.sdk.testing.FakeKitView;
import dev.jasper.sdk.testing.FakePluginHost;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Keeps tests that start the real staged Vault off the developer's keychain. The plugin's no-arg constructor uses the
 * platform keychain, which the Vault touches only to create, unlock or re-key a device-bound vault; tests create unbound
 * vaults only, guaranteed twice: the settings file the plugin starts with turns binding off, and the create form's
 * bind switch is asserted off before anything submits it (an unchecked event never decides it). Use both for every
 * test that starts the staged Vault and creates a vault; Tasks 8–10 reuse this with their staged-plugin tests.
 *
 * <p>Audit (Task 7 review fix round 1): the only test paths constructing the real {@code KeychainStore} are the staged
 * Vault's no-arg constructor (this class's users, {@code RemoteShortcutConfigTest} and the opt-in
 * {@code SettingsDialogPreview}, which start the runtime and create no vault) and {@code VaultPluginTest}'s start-only
 * default-plugin test. Vault and Remote unit tests inject a throwing keychain fixture.
 */
final class UnboundVault {
    static final String ID = "dev.jasper.vault", BIND_SETTING = "bind_new_vaults_to_device", BIND_SWITCH = "bind";
    private UnboundVault() { }

    /** A fake host: part of the settings file the plugin starts with. Call before starting the plugin. */
    static void settings(FakePluginHost host) { host.setSetting(ID, BIND_SETTING, false); }

    /**
     * The real host: the plugin's settings file in the {@code PluginSettingsFiles} layout,
     * {@code <userDirectory>/<id>/<id>.toml}, which the host never replaces once it exists. Call before starting.
     */
    static void settings(Path userDirectory) throws IOException {
        Path folder = Files.createDirectories(userDirectory.resolve(ID));
        Files.writeString(folder.resolve(ID + ".toml"), BIND_SETTING + " = false\n");
    }

    /** The fake host's create form: its bind switch must be off. */
    static void assertUnbound(FakeKitView create) {
        var found = find(create.spec().root());
        assertThat(found).as("the create form's bind switch").isNotNull();
        assertThat(found.checked()).as("a test vault is never bound to the device keychain").isFalse();
    }

    /** The real host's create form, as mounted kit view: its bind switch must be off. */
    static void assertUnbound(dev.jasper.ui.swing.KitView create) {
        var found = find(create.spec().root());
        assertThat(found).as("the create form's bind switch").isNotNull();
        assertThat(found.checked()).as("a test vault is never bound to the device keychain").isFalse();
    }

    private static dev.jasper.sdk.ui.Nodes.Toggle find(dev.jasper.sdk.ui.Nodes.Node node) {
        if (node instanceof dev.jasper.sdk.ui.Nodes.Toggle toggle && toggle.id().equals(BIND_SWITCH)) return toggle;
        List<dev.jasper.sdk.ui.Nodes.Node> children = switch (node) {
            case dev.jasper.sdk.ui.Nodes.Form n -> n.sections();
            case dev.jasper.sdk.ui.Nodes.Stack n -> n.children();
            case dev.jasper.sdk.ui.Nodes.SettingRow n -> List.of(n.control());
            default -> List.of();
        };
        for (var child : children) { var found = find(child); if (found != null) return found; }
        return null;
    }

    private static dev.jasper.ui.model.Nodes.Toggle find(dev.jasper.ui.model.Nodes.Node node) {
        if (node instanceof dev.jasper.ui.model.Nodes.Toggle toggle && toggle.id().equals(BIND_SWITCH)) return toggle;
        List<dev.jasper.ui.model.Nodes.Node> children = switch (node) {
            case dev.jasper.ui.model.Nodes.Form n -> n.sections();
            case dev.jasper.ui.model.Nodes.Stack n -> n.children();
            case dev.jasper.ui.model.Nodes.SettingRow n -> List.of(n.control());
            default -> List.of();
        };
        for (var child : children) { var found = find(child); if (found != null) return found; }
        return null;
    }
}
