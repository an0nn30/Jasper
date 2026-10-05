package dev.jasper.remote.ui.transfers;

import dev.jasper.sdk.contracts.ThemeMigrationCapture;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static dev.jasper.sdk.contracts.ThemeMigrationCapture.save;

class ThemeMigrationEvidenceTest {
    @Test void capturesActualProgressPausedFailedFinishedAndExistingItems() throws Exception {
        ThemeMigrationCapture.themes(theme -> {
            var strip = new TransferStrip(new TransferStrip.Actions((id, action) -> { }, id -> { }, id -> { }));
            var id = UUID.fromString("00000000-0000-0000-0000-000000000002");
            for (String state : List.of("progress", "paused", "failed", "finished")) {
                var action = state.equals("paused") ? Optional.of(TransferRows.Action.RESUME) : Optional.<TransferRows.Action>empty();
                strip.rows(List.of(TransferStripTest.row(id, state.equals("progress") ? "50% - Fixture transfer" : "Fixture " + state, action, state.equals("finished"))));
                save("remote-transfers", theme, state, strip, 680, 150);
            }
            strip.unavailable("Fixture transfers owned by another instance");
            save("remote-transfers", theme, "unavailable", strip, 680, 150);
            var existing = new ExistingItemsPanel("Fixture example.txt already exists in the destination.", choice -> { }, () -> { });
            save("remote-transfers", theme, "existing-items", existing, 680, 180);
        });
    }
}
