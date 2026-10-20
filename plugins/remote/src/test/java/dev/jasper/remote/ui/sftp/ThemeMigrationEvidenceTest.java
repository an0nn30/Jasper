package dev.jasper.remote.ui.sftp;

import dev.jasper.remote.hosts.Auth;
import dev.jasper.remote.hosts.RemoteHost;
import dev.jasper.remote.sftp.FileEntry;
import dev.jasper.sdk.testing.FakeKit;
import dev.jasper.sdk.ui.Nodes;
import dev.jasper.sdk.ui.UiView;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import static dev.jasper.remote.testsupport.Kits.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Descriptor-scenario evidence for the migrated SFTP explorer and its dialogs (Plan 2 Task 8c, controller ruling G10,
 * following Task 7 ruling 2): the states the frozen pre-migration captures recorded (disconnected, loading, connected,
 * operation error) plus the dialogs, asserted as kit descriptors with synthetic data and no networking, written under
 * build/theme-migration-evidence/remote-sftp-descriptors. Pixels of the real host rendering come from the app-side
 * RemoteRenderEvidenceTest. The frozen before images are untouched.
 */
class ThemeMigrationEvidenceTest {
    static final Path OUTPUT = Path.of("build/theme-migration-evidence/remote-sftp-descriptors");

    static void save(String state, UiView view) throws Exception {
        Files.createDirectories(OUTPUT);
        Files.writeString(OUTPUT.resolve(state + ".txt"), view.spec().toString() + "\n");
    }

    @Test void describesDisconnectedLoadingConnectedOperationFailureAndTheDialogs() throws Exception {
        edt(() -> {
            var kit = new FakeKit("dev.jasper.remote");
            var panel = new SftpPanel(kit);
            save("disconnected", panel.view());
            assertThat(node(panel.view(), SftpPanel.EMPTY, Nodes.Empty.class).title()).isEqualTo("Not Connected");
            panel.busy(true, "Loading folder...");
            save("loading", panel.view());
            assertThat(button(panel.view(), SftpPanel.CANCEL).state().visible()).isTrue();
            panel.showPage("Example host", "/fixture", List.of(new FileEntry("example.txt", FileEntry.Kind.FILE, 42, 1000, 0644, "")), 0, 1, false);
            save("connected", panel.view());
            assertThat(SftpTestAccess.table(panel.view()).rows()).hasSize(1);
            panel.operationStatus(false, "Fixture operation failed: permission denied");
            save("operation-error", panel.view());
            assertThat(texts(panel.view())).contains("Fixture operation failed: permission denied");

            var host = RemoteHost.create("fixture", "fixture.example.invalid", 22, "fixture-user", Auth.AGENT, "", Optional.empty());
            var chooser = new HostChooser(kit, List.of(host), chosen -> { }, () -> { });
            save("browse-host", chooser.view());
            var folder = new FolderNamePanel(kit, name -> Optional.of("Enter one folder name"), () -> { });
            save("new-folder", folder.view());
            click(folder.view(), FolderNamePanel.CREATE_BUTTON);
            assertThat(folder.error()).isEqualTo("Enter one folder name");
            save("new-folder-invalid", folder.view());
            try (var workers = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
                var destination = new DestinationPanel(kit, "1 items from fixture:/fixture", List.of(host), Files.createTempDirectory("sftp-evidence"),
                    workers, UI, id -> new CompletableFuture<>(), identity -> new CompletableFuture<>(), capture -> { }, capture -> { }, () -> { }, () -> { });
                save("copy-to-host", destination.view());
                assertThat(find(destination.view(), SftpPanel.FILES)).isPresent();
                destination.close();
            }
            try (var saved = Files.list(OUTPUT)) { assertThat(saved.count()).isGreaterThanOrEqualTo(8); }
        });
    }
}
