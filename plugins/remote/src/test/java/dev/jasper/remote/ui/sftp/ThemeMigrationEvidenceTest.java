package dev.jasper.remote.ui.sftp;

import dev.jasper.remote.sftp.FileEntry;
import dev.jasper.sdk.contracts.ThemeMigrationCapture;
import java.util.List;
import org.junit.jupiter.api.Test;
import static dev.jasper.sdk.contracts.ThemeMigrationCapture.save;

class ThemeMigrationEvidenceTest {
    @Test void capturesDisconnectedBusyConnectedAndOperationFailure() throws Exception {
        ThemeMigrationCapture.themes(theme -> {
            var panel = new SftpPanel(dev.jasper.sdk.testing.FakeNamedIcon::new);
            save("remote-sftp", theme, "disconnected", panel, 720, 420);
            panel.busy(true, "Loading folder...");
            save("remote-sftp", theme, "loading", panel, 720, 420);
            panel.showPage("Example host", "/fixture", List.of(new FileEntry("example.txt", FileEntry.Kind.FILE, 42, 1000, 0644, "")), 0, 1, false);
            save("remote-sftp", theme, "connected", panel, 720, 420);
            panel.operationStatus(false, "Fixture operation failed: permission denied");
            save("remote-sftp", theme, "operation-error", panel, 720, 420);
        });
    }
}
