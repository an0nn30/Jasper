package dev.jasper.remote.ui;

import dev.jasper.sdk.testing.FakeKit;
import dev.jasper.sdk.ui.Nodes;
import dev.jasper.sdk.ui.UiEvent;
import dev.jasper.sdk.ui.UiView;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import static dev.jasper.remote.testsupport.Kits.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Descriptor-scenario evidence for the migrated Sessions panel and host dialogs (Plan 2 Task 8a, controller ruling G10,
 * following Task 7 ruling 2): the states the frozen pre-migration captures recorded, asserted as kit descriptors with
 * synthetic data and no networking, written under build/theme-migration-evidence/remote-descriptors. Pixels of the real
 * host rendering come from the app-side RemoteRenderEvidenceTest. The frozen before images are untouched.
 */
class ThemeMigrationEvidenceTest {
    static void save(String state, UiView view) throws Exception {
        Path output = Path.of("build/theme-migration-evidence/remote-descriptors");
        Files.createDirectories(output);
        Files.writeString(output.resolve(state + ".txt"), view.spec().toString() + "\n");
    }

    @Test void describesRealSessionRowsConnectionAndHostAuthenticationStates() throws Exception {
        edt(() -> {
            var kit = new FakeKit("dev.jasper.remote");
            var fixture = new HostsPanelTest();
            var hosts = fixture.withDistros();
            save("sessions-populated", hosts.view());
            assertThat(HostsPanelTest.rows(hosts)).hasSize(5);
            emit(hosts.view(), new UiEvent.Edited(HostsPanel.SEARCH, "no matches"));
            save("sessions-filtered-empty", hosts.view());
            assertThat(node(hosts.view(), HostsPanel.HINT, Nodes.Text.class).text()).isEqualTo("No sessions match");
            emit(hosts.view(), new UiEvent.Edited(HostsPanel.SEARCH, "")); hosts.setDistros(List.of(), profile -> 0); hosts.setHosts(List.of(), Optional.empty());
            save("sessions-empty", hosts.view());
            assertThat(node(hosts.view(), HostsPanel.HINT, Nodes.Text.class).text()).startsWith("No hosts yet");
            var editor = new HostEditor(kit, UI, List.of(), Optional.empty(), true, id -> Optional.empty(),
                () -> CompletableFuture.completedFuture(Optional.empty()), host -> { }, () -> { });
            save("add-host-vault", editor.view());
            emit(editor.view(), new UiEvent.Selected(HostEditor.AUTH, Set.of(HostEditor.AGENT)));
            save("add-host-agent", editor.view());
            click(editor.view(), HostEditor.SAVE);
            assertThat(editor.message()).isNotBlank();
            save("add-host-invalid", editor.view());
            var connecting = new ConnectionPanel(kit, "Example host", "fixture-user@example.invalid", () -> { }, () -> { });
            save("connection-busy", connecting.view());
            connecting.failed("Fixture connection failure");
            assertThat(button(connecting.view(), ConnectionPanel.RETRY).state().visible()).isTrue();
            save("connection-error", connecting.view());
            connecting.close();
            var trust = new HostKeyPanel(kit, new dev.jasper.remote.client.HostKeyVerifier.Question("example.invalid", 22, "ssh-ed25519", "SHA256:fixture"), decision -> { });
            save("host-key-trust", trust.view());
            var candidates = dev.jasper.remote.hosts.ConfigImport.plan(dev.jasper.remote.hosts.SshConfig.parse("Host fixture\n HostName example.invalid\nHost invalid\n IdentityFile %x\n", include -> List.of()), List.of(), java.nio.file.Path.of("/fixture"), "fixture-user");
            var importing = new ImportPanel(kit, candidates, List.of("Fixture skipped entry"), selected -> { }, selected -> { }, () -> { }, () -> { });
            save("import-preview", importing.view());
            importing.busy(); save("import-busy", importing.view());
            importing.failed("Fixture import failure"); save("import-failed", importing.view());
            var group = new GroupNamePanel(kit, "Other", name -> { }, () -> { });
            save("rename-default-group", group.view());
            var confirm = new ConfirmPanel(kit, "Delete the saved host fixture? Open sessions stay connected.", "Delete", () -> { }, () -> { });
            save("delete-host", confirm.view());
            try (var saved = Files.list(Path.of("build/theme-migration-evidence/remote-descriptors"))) { assertThat(saved.count()).isGreaterThanOrEqualTo(14); }
        });
    }
}
