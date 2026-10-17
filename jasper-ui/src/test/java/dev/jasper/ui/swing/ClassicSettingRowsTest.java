package dev.jasper.ui.swing;

import static dev.jasper.ui.swing.KitViewTest.release;
import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.ui.model.ControlState;
import dev.jasper.ui.model.Nodes;
import dev.jasper.ui.model.ViewSpec;
import dev.jasper.ui.testsupport.Edt;
import dev.jasper.ui.testsupport.KitFixtures;
import java.util.List;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

/** Plan 9b Execution notes N6: a classic checkbox leads its label also when the row's Reset is in its menu. */
class ClassicSettingRowsTest {
    /** A Settings card with one boolean row whose control is a one-child SETTINGS_TRAILING stack, as the classic rows build it. */
    static ViewSpec row() {
        var toggle = new Nodes.Toggle("blur", ControlState.READY, "Background blur", true, "blur", false);
        var controls = new Nodes.Stack("blur:controls", ControlState.READY, Nodes.LayoutRole.SETTINGS_TRAILING, List.of(toggle));
        return new ViewSpec(new Nodes.Card("card", ControlState.READY, Nodes.CardKind.SETTINGS, List.of(
            new Nodes.SettingRow("row:blur", ControlState.READY, "Background blur", "", controls, false))));
    }

    /** The toggle's left edge in the view under {@code context}. */
    static int toggleX(KitContext context) {
        try (var view = new KitView(context, row(), event -> { })) {
            view.setSize(760, 120);
            for (int i = 0; i < 3; i++) InteractiveRowsTest.layout(view);
            var box = KitViewTest.find(view, "blur", javax.swing.JComponent.class);
            return SwingUtilities.convertPoint(box, 0, 0, view).x;
        }
    }

    @Test void aClassicCheckboxLeadsItsLabelInAOneChildTrailingStack() {
        Edt.run(() -> {
            var context = KitFixtures.context(KitFixtures.enterpriseLight(), KitFixtures.enterprise());
            try { assertThat(toggleX(context)).as("the box at the row's leading inset").isLessThan(60); } finally { release(context); }
        });
    }

    /** A Settings page with one row whose control is an ordered list over its entry row, as the Shell page's arguments. */
    static ViewSpec listRow() {
        var list = new Nodes.ItemList("args", ControlState.READY, Nodes.ListKind.ORDERED, List.of(), java.util.Set.of(), "", List.of(),
            Nodes.SelectionMode.SINGLE, java.util.Optional.empty(), java.util.OptionalInt.empty(), "", "", "", "", "", "Empty");
        var entry = new Nodes.Stack("args:entry", ControlState.READY, Nodes.LayoutRole.INLINE, List.of(
            new Nodes.Field("args:new", ControlState.READY, Nodes.FieldKind.TEXT, "", "Add…", "Add to Shell arguments", ""),
            new Nodes.Button("args:add", ControlState.READY, "Add", "", Nodes.ButtonKind.SECONDARY, "args:add", "")));
        var editor = new Nodes.Stack("args:editor", ControlState.READY, Nodes.LayoutRole.VERTICAL, List.of(list, entry));
        var controls = new Nodes.Stack("args:controls", ControlState.READY, Nodes.LayoutRole.SETTINGS_TRAILING, List.of(editor));
        return new ViewSpec(new Nodes.Stack("page", ControlState.READY, Nodes.LayoutRole.SETTINGS_PAGE, List.of(
            new Nodes.Card("card", ControlState.READY, Nodes.CardKind.SETTINGS, List.of(
                new Nodes.SettingRow("row:args", ControlState.READY, "Shell arguments", "Applies to new panes", controls, false))))));
    }

    /** Plan 9b Task 6: under the classic group face a Settings list spans its row below the title and description. */
    @Test void aClassicSettingsListSpansItsRowBelowItsText() {
        Edt.run(() -> {
            var context = KitFixtures.context(KitFixtures.enterpriseLight(), KitFixtures.enterprise());
            try (var view = new KitView(context, listRow(), event -> { })) {
                view.setSize(760, 400);
                for (int i = 0; i < 3; i++) InteractiveRowsTest.layout(view);
                var row = KitViewTest.find(view, "row:args", javax.swing.JComponent.class);
                var list = (ItemLists.ListControl) view.control("args");
                var description = ((SettingRows.RowControl) view.control("row:args")).description;
                var at = SwingUtilities.convertPoint(list.scroll, 0, 0, row);
                var insets = row.getInsets();
                assertThat(at.x).as("the list at the row's leading inset").isEqualTo(insets.left);
                assertThat(list.scroll.getWidth()).as("across the row").isEqualTo(row.getWidth() - insets.left - insets.right);
                assertThat(at.y).as("below the description").isGreaterThanOrEqualTo(description.getY() + description.getHeight());
                assertThat(row.getHeight()).as("the row holds the list").isGreaterThan(at.y + list.scroll.getHeight());
            } finally { release(context); }
        });
    }

    @Test void theDefaultSwitchStaysAtTheRowsEnd() {
        Edt.run(() -> {
            var context = KitFixtures.context();
            try { assertThat(toggleX(context)).as("the switch at the row's end").isGreaterThan(600); } finally { release(context); }
        });
    }
}
