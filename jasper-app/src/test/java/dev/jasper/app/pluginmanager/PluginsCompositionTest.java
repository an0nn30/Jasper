package dev.jasper.app.pluginmanager;

import static dev.jasper.app.workspace.DesktopTestSupport.edt;
import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.ui.model.MenuItem;
import dev.jasper.ui.model.Nodes;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Spec 12.2 (CLASSIC): the Plugins page's checkbox rows, folder tabs and ⋯ menus; every action stays reachable. */
class PluginsCompositionTest {
    @Test void aClassicRowLeadsWithItsCheckboxAndHasNoIcon() {
        var row = TestRows.plugin("dev.example.tidy", "Tidy Tabs").row();
        var item = PluginCard.item(row, false, "plugin", PluginShape.CLASSIC);
        assertThat(item.leading()).containsInstanceOf(Nodes.Toggle.class);
        assertThat(item.trailing()).isEmpty();
        assertThat(item.title()).singleElement().satisfies(title -> assertThat(((Nodes.Text) title).role()).isEqualTo(Nodes.TextRole.BODY));
        var plain = PluginCard.item(row, false, "plugin", PluginShape.ROWS);
        assertThat(plain.leading()).containsInstanceOf(Nodes.Icon.class);
        assertThat(plain.trailing()).singleElement().isInstanceOf(Nodes.Toggle.class);
    }

    @Test void everyRowsStateAndActionStayReachable() {
        for (var row : List.of(TestRows.plugin("dev.example.review", "Review").needsReview("network").row(),
                TestRows.plugin("dev.example.failed", "Failed").state("FAILED", "boom").row(),
                TestRows.plugin("dev.example.gone", "Gone").removing().row())) {
            var item = PluginCard.item(row, false, "plugin", PluginShape.CLASSIC);
            var toggle = (Nodes.Toggle) item.leading().orElseThrow();
            assertThat(toggle.checked()).as(row.id()).isEqualTo(PluginCard.checked(row));
            if (!PluginCard.toggleable(row)) assertThat(toggle.tooltip()).as(row.id()).isEqualTo(PluginCard.why(row));
            assertThat(item.contextActions().orElseThrow()).extracting(MenuItem::label).as(row.id()).containsAll(PluginCard.menu(row, false).stream().map(MenuItem::label).toList());
        }
    }

    @Test void theDetailsUseTabsInBothLooksAndTheClassicEllipsisSitsBesideDisable() throws Exception {
        edt(() -> {
            for (var shape : List.of(PluginShape.ROWS, PluginShape.CLASSIC)) {
                var details = new PluginDetails(dev.jasper.app.testsupport.KitTestSupport.context(), event -> { }, shape);
                try {
                    details.show(TestRows.plugin("dev.example.tidy", "Tidy Tabs").row(), false);
                    var root = details.header().spec().root();
                    var section = (Nodes.Choice) dev.jasper.app.settingsui.ResetMenuTestAccess.find(root, PluginDetails.SECTION);
                    assertThat(section.kind()).as(shape.toString()).isEqualTo(Nodes.ChoiceKind.TABS);
                    var more = (Nodes.MenuButton) dev.jasper.app.settingsui.ResetMenuTestAccess.find(root, PluginDetails.MORE);
                    assertThat(more.icon()).isEqualTo(shape.ellipsisActions() ? "more" : "chevron.down");
                    var head = (Nodes.Stack) dev.jasper.app.settingsui.ResetMenuTestAccess.find(root, "head");
                    assertThat(head.children().stream().anyMatch(node -> node.id().equals(PluginDetails.MORE))).as(shape + ": the ⋯ in the head").isEqualTo(shape.ellipsisActions());
                    assertThat(head.children().stream().anyMatch(node -> node.id().equals("icon"))).as(shape + ": the identity icon").isEqualTo(!shape.checkboxRows());
                } finally { details.close(); }
            }
        });
    }
}
