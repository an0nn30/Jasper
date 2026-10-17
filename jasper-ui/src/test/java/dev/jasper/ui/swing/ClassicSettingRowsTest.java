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

    @Test void theDefaultSwitchStaysAtTheRowsEnd() {
        Edt.run(() -> {
            var context = KitFixtures.context();
            try { assertThat(toggleX(context)).as("the switch at the row's end").isGreaterThan(600); } finally { release(context); }
        });
    }
}
