package dev.jasper.fileserver.ui;

import dev.jasper.fileserver.definition.Protocol;
import dev.jasper.fileserver.runtime.ServerState;
import dev.jasper.sdk.ui.Nodes;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/** Descriptor state evidence; real hosted rendering is exercised in jasper-app staged plugin tests. */
class ThemeMigrationEvidenceTest {
    @Test void emptyBusyFailedAndConditionalStatesUseOwnedDescriptors() {
        var fixture = new FileServersPanelTest();
        var panel = fixture.panel();
        assertThat(fixture.node(panel, FileServersPanel.EMPTY)).isInstanceOf(Nodes.Empty.class);
        fixture.server("Example", Protocol.HTTP, ServerState.STARTING, List.of()); fixture.set(panel);
        var list = (Nodes.ItemList) fixture.node(panel, FileServersPanel.ROWS);
        assertThat(list.items().getFirst().subtitle()).anyMatch(n -> n instanceof Nodes.Text t && t.text().contains("Starting"));
        fixture.views.clear(); fixture.server("Example", Protocol.HTTP, ServerState.failed("Fixture port unavailable"), List.of()); fixture.set(panel);
        list = (Nodes.ItemList) fixture.node(panel, FileServersPanel.ROWS);
        assertThat(list.items().getFirst().subtitle()).anyMatch(n -> n instanceof Nodes.Text t && t.severity().equals(Optional.of(Nodes.Severity.DANGER)));
        panel.close();
    }
}
