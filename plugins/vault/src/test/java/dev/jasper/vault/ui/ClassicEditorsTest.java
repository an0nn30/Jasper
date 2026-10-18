package dev.jasper.vault.ui;

import static dev.jasper.vault.testsupport.KitViews.*;
import static org.assertj.core.api.Assertions.*;

import dev.jasper.sdk.PluginInfo;
import dev.jasper.sdk.plugin.PluginContext;
import dev.jasper.sdk.terminal.WindowHandle;
import dev.jasper.sdk.testing.FakeKit;
import dev.jasper.sdk.testing.FakeNotifications;
import dev.jasper.sdk.testing.FakePluginHost;
import dev.jasper.sdk.ui.Nodes;
import dev.jasper.vault.service.VaultService;
import dev.jasper.vault.workstation.Layout;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Plan 10b Rulings 5, 7 and 10: the classic editor sheets of the 07 Credential Vault mocks. */
class ClassicEditorsTest {
    @TempDir Path directory;
    static final PluginInfo INFO = new PluginInfo("dev.jasper.vault", "Vault", "0.1.0", Set.of());

    static List<String> groups(Nodes.Form form) {
        return form.sections().stream().filter(Nodes.Section.class::isInstance).map(n -> ((Nodes.Section) n).title()).toList();
    }

    @Test void addLoginTakesTheMocksGroupsLabelsAndSwatch() throws Exception {
        edt(() -> {
            var form = EntryEditor.login(new FakeKit("dev.jasper.vault"), null, account -> CompletableFuture.completedFuture(null), () -> { });
            form.layout(Layout.CLASSIC);
            var view = form.view();
            var root = (Nodes.Form) view.spec().root();
            assertThat(root.hero()).as("the header strip titles the sheet").isEmpty();
            assertThat(groups(root)).containsExactly("Login", "Authentication");
            assertThat(node(view, "row." + EntryEditor.NAME, Nodes.FormRow.class).label()).isEqualTo("Name");
            assertThat(node(view, "row." + EntryEditor.AUTH, Nodes.FormRow.class).label()).isEqualTo("Method");
            assertThat(node(view, EntryEditor.AUTH, Nodes.Choice.class).kind()).isEqualTo(Nodes.ChoiceKind.EXCLUSIVE);
            assertThat(node(view, EntryEditor.AUTH, Nodes.Choice.class).options()).extracting(o -> o.label()).containsExactly("Password", "Private Key");
            assertThat(node(view, "row." + EntryEditor.PRIVATE_PATH, Nodes.FormRow.class).label()).isEqualTo("Private key");
            assertThat(node(view, EntryEditor.PASSWORD, Nodes.Secret.class).kind()).as("the classic reveal shows its Show").isEqualTo(Nodes.SecretKind.DISCREET_INPUT);
            assertThat(form.swatch()).isEqualTo(Nodes.Hue.BLUE);
            form.close();
        });
    }

    @Test void addSshKeyAndAddSecureNoteTakeTheirGroups() throws Exception {
        edt(() -> {
            var kit = new FakeKit("dev.jasper.vault");
            var key = EntryEditor.key(kit, null, value -> CompletableFuture.completedFuture(null), () -> { });
            key.layout(Layout.CLASSIC);
            assertThat(groups((Nodes.Form) key.view().spec().root())).containsExactly("SSH Key");
            assertThat(node(key.view(), "row." + EntryEditor.PRIVATE_PATH, Nodes.FormRow.class).label()).isEqualTo("Private key");
            assertThat(node(key.view(), "row." + EntryEditor.PUBLIC_PATH, Nodes.FormRow.class).label()).isEqualTo("Public key");
            assertThat(key.swatch()).isEqualTo(Nodes.Hue.GREEN);
            key.close();
            var note = EntryEditor.note(kit, null, value -> CompletableFuture.completedFuture(null), () -> { });
            note.layout(Layout.CLASSIC);
            assertThat(groups((Nodes.Form) note.view().spec().root())).containsExactly("Secure Note");
            assertThat(note.swatch()).isEqualTo(Nodes.Hue.AMBER);
            note.close();
        });
    }

    @Test void theRowsLayoutKeepsTodaysForms() throws Exception {
        edt(() -> {
            var form = EntryEditor.login(new FakeKit("dev.jasper.vault"), null, account -> CompletableFuture.completedFuture(null), () -> { });
            var view = form.view();
            var root = (Nodes.Form) view.spec().root();
            assertThat(root.hero()).isPresent();
            assertThat(groups(root)).containsExactly("Authentication");
            assertThat(find(view, "row." + EntryEditor.AUTH)).as("the choice stays unlabelled").isEmpty();
            assertThat(node(view, "row." + EntryEditor.PRIVATE_PATH, Nodes.FormRow.class).label()).isEqualTo("Private Key");
            form.close();
        });
    }

    @Test void theSheetsCarryTheirSwatchUnderClassicOnly() throws Exception {
        assertThat(swatchOfAddLogin(Layout.CLASSIC)).hasValue(Nodes.Hue.BLUE);
        assertThat(swatchOfAddLogin(Layout.ROWS)).hasValue(Nodes.Hue.NONE);
    }

    Optional<Nodes.Hue> swatchOfAddLogin(Layout layout) throws Exception {
        var swatch = new AtomicReference<Optional<Nodes.Hue>>();
        edt(() -> {
            try (var f = new VaultUiFixture(directory.resolve(layout.name())); var host = new FakePluginHost()) {
                PluginContext context = host.start(INFO, Set.of(), Set.of(), c -> { });
                host.addTerminalWindow(); WindowHandle owner = context.terminals().windows().getFirst();
                var service = new VaultService(f.lock, () -> Optional.of(owner), prompt -> { }, p -> { }, p -> { },
                    new FakeNotifications().group("dev.jasper.vault"));
                var window = new VaultManagerWindow(context, f.lock, f.manager, service,
                    () -> new VaultManagerWindow.Status("test", "15 min", "/synthetic/vault.jv"), () -> { }, layout);
                window.show(owner, Optional.empty());
                window.add(VaultManager.Type.LOGIN);
                swatch.set(host.dialogSwatch("dev.jasper.vault", "Add Login"));
                window.close();
            }
        });
        return swatch.get();
    }
}
