package dev.jasper.remote.ui;

import dev.jasper.sdk.contracts.ThemeMigrationCapture;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import static dev.jasper.sdk.contracts.ThemeMigrationCapture.save;
import static org.assertj.core.api.Assertions.assertThat;

class ThemeMigrationEvidenceTest {
    @Test void capturesRealSessionRowsConnectionAndHostAuthenticationStates() throws Exception {
        ThemeMigrationCapture.themes(theme -> {
            var fixture = new HostsPanelTest();
            var hosts = fixture.withDistros(dev.jasper.sdk.testing.FakeBundledIcon::new);
            save("remote", theme, "sessions-populated", hosts, 460, 540);
            hosts.search.setText("no matches");
            save("remote", theme, "sessions-filtered-empty", hosts, 460, 540);
            hosts.search.setText(""); hosts.setDistros(List.of(), profile -> 0); hosts.setHosts(List.of(), Optional.empty());
            save("remote", theme, "sessions-empty", hosts, 460, 540);
            var editor = new HostEditor(List.of(), Optional.empty(), true, id -> Optional.empty(),
                () -> CompletableFuture.completedFuture(Optional.empty()), host -> { }, () -> { });
            save("remote", theme, "add-host-vault", editor, 860, 540);
            editor.agentAuth.doClick();
            save("remote", theme, "add-host-agent", editor, 860, 540);
            editor.save.doClick();
            assertThat(editor.message.getText()).isNotBlank();
            save("remote", theme, "add-host-invalid", editor, 860, 540);
            var connecting = new ConnectionPanel("Example host", "fixture-user@example.invalid", () -> { }, () -> { });
            save("remote", theme, "connection-busy", connecting, 520, 250);
            connecting.failed("Fixture connection failure");
            assertThat(connecting.retry.isVisible()).isTrue();
            save("remote", theme, "connection-error", connecting, 520, 250);
            connecting.finished();
            var trust = new HostKeyPanel(new dev.jasper.remote.client.HostKeyVerifier.Question("example.invalid", 22, "ssh-ed25519", "SHA256:fixture"), decision -> { });
            save("remote", theme, "host-key-trust", trust, 680, 220);
            var candidates = dev.jasper.remote.hosts.ConfigImport.plan(dev.jasper.remote.hosts.SshConfig.parse("Host fixture\n HostName example.invalid\nHost invalid\n IdentityFile %x\n", include -> List.of()), List.of(), java.nio.file.Path.of("/fixture"), "fixture-user");
            var importing = new ImportPanel(candidates, List.of("Fixture skipped entry"), selected -> { }, selected -> { }, () -> { }, () -> { });
            save("remote", theme, "import-preview", importing, 780, 420);
            importing.busy(); save("remote", theme, "import-busy", importing, 780, 420);
            importing.failed("Fixture import failure"); save("remote", theme, "import-failed", importing, 780, 420);
        });
    }
}
