package dev.jasper.ui.swing;

import static dev.jasper.ui.swing.KitViewTest.release;
import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.ui.model.Column;
import dev.jasper.ui.model.ControlState;
import dev.jasper.ui.model.Nodes;
import dev.jasper.ui.model.TableRow;
import dev.jasper.ui.model.ViewSpec;
import dev.jasper.ui.testsupport.Edt;
import dev.jasper.ui.testsupport.KitFixtures;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Plan 9b Rulings 5 and 12: the classic table face's heading counts and list heading bands. */
class ClassicListHeadingsTest {
    static ViewSpec groups() {
        var columns = List.of(new Column("group", "Group", Nodes.TextRole.BODY, false, 10, Optional.empty()),
            new Column("display", "Display", Nodes.TextRole.BODY, false, 3, Optional.empty()), new Column("source", "Source", Nodes.TextRole.BODY, false, 2.2, Optional.empty()));
        return new ViewSpec(new Nodes.Table("table", ControlState.READY, columns, List.of(TableRow.heading("jasper", "Jasper", OptionalInt.of(6), "group"),
            new TableRow("a", Map.of("group", "Configuration", "display", "Sticky card", "source", "Default"), Optional.empty())), Set.of(), "", List.of()));
    }

    @Test void aClassicHeadingsCountStandsUnderTheLastColumnsTitle() {
        Edt.run(() -> {
            var context = KitFixtures.context(KitFixtures.enterpriseLight(), KitFixtures.enterprise());
            try (var view = new KitView(context, groups(), event -> { })) {
                view.setSize(751, 200);
                for (int i = 0; i < 3; i++) InteractiveRowsTest.layout(view);
                var table = (Tables.TableControl) view.control("table");
                var source = table.header.getComponent(2);
                int titleStart = source.getX() + ((javax.swing.JComponent) source).getInsets().left;
                var heading = table.mounted.get("jasper");
                assertThat(heading.trailing.getX()).as("the count under Source").isEqualTo(titleStart);
            } finally { release(context); }
        });
    }

    @Test void theDefaultHeadingsCountStaysAtTheRowsEnd() {
        Edt.run(() -> {
            var context = KitFixtures.context();
            try (var view = new KitView(context, groups(), event -> { })) {
                view.setSize(751, 200);
                for (int i = 0; i < 3; i++) InteractiveRowsTest.layout(view);
                var table = (Tables.TableControl) view.control("table");
                var heading = table.mounted.get("jasper");
                assertThat(table.headingCountStart()).isEqualTo(-1);
                assertThat(heading.trailing.getX() + heading.trailing.getWidth()).as("at the row's end, inset")
                    .isLessThanOrEqualTo(heading.getWidth()).isGreaterThan(heading.getWidth() - 40);
            } finally { release(context); }
        });
    }
}
