package dev.jasper.remote.ui.transfers;

import static dev.jasper.remote.testsupport.Kits.*;
import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.sdk.testing.FakeKit;
import dev.jasper.sdk.ui.Nodes;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Spec 14.4: the classic Transfers panel shows the window's transfer rows, or "No Transfers". */
class TransfersPanelTest {
    @Test void thePanelShowsTheRowsWithoutTheSidebarsHeading() throws Exception {
        edt(() -> {
            var cancelled = new ArrayList<UUID>();
            var strip = new TransferStrip(new TransferStrip.Actions((id, action) -> { }, cancelled::add, id -> { }));
            var panel = new TransfersPanel(new FakeKit("dev.jasper.remote"), strip);
            assertThat(node(panel.view(), TransfersPanel.EMPTY, Nodes.Empty.class).title()).isEqualTo("No Transfers");
            UUID id = UUID.randomUUID();
            strip.rows(List.of(new TransferRows.Row(id, "↑", "report.pdf", "Local: /tmp/report.pdf → prod: /srv", "Uploading",
                OptionalDouble.of(0.4), false, Optional.empty(), false, false)));
            assertThat(find(panel.view(), TransfersPanel.EMPTY)).isEmpty();
            assertThat(find(panel.view(), TransferStrip.HEADING)).as("the panel's own title heads it").isEmpty();
            assertThat(node(panel.view(), TransferStrip.ROWS, Nodes.ItemList.class).items()).hasSize(1);
            press(panel.view(), TransferStrip.CLOSE + id);
            assertThat(cancelled).containsExactly(id);
        });
    }
}
