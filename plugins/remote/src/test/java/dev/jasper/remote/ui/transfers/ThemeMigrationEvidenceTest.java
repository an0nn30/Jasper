package dev.jasper.remote.ui.transfers;

import dev.jasper.remote.sftp.FileEntry;
import dev.jasper.remote.transfer.ConflictDecision;
import dev.jasper.remote.transfer.TransferEntry;
import dev.jasper.sdk.testing.FakeKit;
import dev.jasper.sdk.ui.UiView;
import dev.jasper.sdk.ui.ViewSpec;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static dev.jasper.remote.testsupport.Kits.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Descriptor-scenario evidence for the migrated transfer rows and transfer dialogs (Plan 2 Task 8c, ruling G10): the
 * states the frozen pre-migration captures recorded (progress, paused, failed, finished, unavailable, existing items)
 * plus Resolve and Rename, as kit descriptors with synthetic data, written under
 * build/theme-migration-evidence/remote-transfers-descriptors. The rows render inside the SFTP sidebar; the real-host
 * pixels come from the app-side RemoteRenderEvidenceTest. The frozen before images are untouched.
 */
class ThemeMigrationEvidenceTest {
    static final Path OUTPUT = Path.of("build/theme-migration-evidence/remote-transfers-descriptors");

    static void save(String state, String text) throws Exception {
        Files.createDirectories(OUTPUT);
        Files.writeString(OUTPUT.resolve(state + ".txt"), text + "\n");
    }

    static void save(String state, UiView view) throws Exception { save(state, view.spec().toString()); }

    @Test void describesProgressPausedFailedFinishedUnavailableAndTheDialogs() throws Exception {
        edt(() -> {
            var strip = new TransferStrip(new TransferStrip.Actions((id, action) -> { }, id -> { }, id -> { }));
            var id = UUID.fromString("00000000-0000-0000-0000-000000000002");
            for (String state : List.of("progress", "paused", "failed", "finished")) {
                var action = state.equals("paused") ? Optional.of(TransferRows.Action.RESUME) : Optional.<TransferRows.Action>empty();
                strip.rows(List.of(TransferStripTest.row(id, state.equals("progress") ? "50% - Fixture transfer" : "Fixture " + state, action, state.equals("finished"))));
                var node = strip.node().orElseThrow();
                assertThat(new ViewSpec(node).root()).as("a valid tree").isSameAs(node);
                save(state, node.toString());
                assertThat(find(node, TransferStrip.closeId(id))).isPresent();
            }
            strip.unavailable("Fixture transfers owned by another instance");
            save("unavailable", strip.node().orElseThrow().toString());
            assertThat(find(strip.node().orElseThrow(), TransferStrip.UNAVAILABLE)).isPresent();
            var kit = new FakeKit("dev.jasper.remote");
            var existing = new ExistingItemsPanel(kit, "Fixture example.txt already exists in the destination.", choice -> { }, () -> { });
            save("existing-items", existing.view());
            var file = new FileEntry("example.txt", FileEntry.Kind.FILE, 3, 1000, 0644, "", "file-1");
            var entry = new TransferEntry(7, id, "example.txt", "/fixture/example.txt", "/srv/example.txt", file, "", Optional.empty(),
                TransferEntry.Phase.PENDING, 0, "", TransferEntry.Publication.NONE, Optional.of(file), ConflictDecision.ASK, TransferEntry.Outcome.PENDING, "Fixture destination changed");
            var resolve = new ResolvePanel(kit, entry, choice -> { }, () -> { }, () -> { });
            save("resolve", resolve.view());
            assertThat(button(resolve.view(), ResolvePanel.MERGE).state().visible()).isFalse();
            save("rename", new RenamePanel(kit, "example.txt", name -> { }, () -> { }).view());
            try (var saved = Files.list(OUTPUT)) { assertThat(saved.count()).isGreaterThanOrEqualTo(8); }
        });
    }
}
