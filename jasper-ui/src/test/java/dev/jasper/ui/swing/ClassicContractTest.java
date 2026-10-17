package dev.jasper.ui.swing;

import static dev.jasper.ui.swing.KitViewTest.*;
import static org.assertj.core.api.Assertions.*;

import dev.jasper.ui.kit.Kit;
import dev.jasper.ui.model.*;
import dev.jasper.ui.testsupport.*;
import java.awt.event.ActionEvent;
import java.util.*;
import javax.swing.AbstractButton;
import javax.swing.text.DefaultEditorKit;
import org.junit.jupiter.api.Test;

/**
 * Spec 16.1.9 in jasper-ui (plan 4c Rulings 23): the same descriptors emit the same events under both shipped kits: a
 * checkbox or switch, a radio or segment, a folder tab or segment, the ordered list's commands, and a read-only field.
 */
@SuppressWarnings("try")
class ClassicContractTest {
    static final ControlState READY = ControlState.READY;

    static ListItem item(String id) {
        return new ListItem(id, Optional.empty(), List.of(new Nodes.Text(id + "-text", READY, id, Nodes.TextRole.MONO)), List.of(), List.of());
    }

    static ViewSpec controls() {
        var options = List.of(new Option("a", "Alpha", Optional.empty()), new Option("b", "Beta", Optional.empty()));
        return new ViewSpec(new Nodes.Stack("controls", READY, Nodes.LayoutRole.VERTICAL, List.of(
            new Nodes.Toggle("toggle", READY, "Allow changes", false, "toggle", false),
            new Nodes.Choice("protocol", READY, Nodes.ChoiceKind.EXCLUSIVE, options, "a", "Protocol", "protocol"),
            new Nodes.Choice("pages", READY, Nodes.ChoiceKind.TABS, options, "a", "Pages", "pages"),
            new Nodes.Field("folder", READY, Nodes.FieldKind.TEXT, "No folder selected", "", "Folder", "", Optional.empty(),
                Nodes.FieldPresentation.STANDARD, "", "", "", true),
            new Nodes.ItemList("keys", READY, Nodes.ListKind.ORDERED, List.of(item("a"), item("b")), Set.of(), "", List.of(),
                Nodes.SelectionMode.SINGLE, Optional.empty()))));
    }

    @Test void theControlsEmitTheSameEventsUnderBothShippedKits() {
        for (var kit : List.of(Kit.DEFAULT, KitFixtures.enterprise())) Edt.run(() -> {
            var context = KitFixtures.context(KitFixtures.light(), kit);
            var events = new ArrayList<UiEvent>();
            try (var view = new KitView(context, controls(), events::add)) {
                KitFixtures.render(view, 520, 420, 1);
                find(view, "toggle", AbstractButton.class).doClick(0);
                find(view, "protocol.option.b", AbstractButton.class).doClick(0);
                find(view, "pages.option.b", AbstractButton.class).doClick(0);
                // As a person types (3b's ReadOnlyFieldsTest): the key-typed action, which a read-only field ignores.
                var folder = ((TextFields.FieldControl) view.control("folder")).input;
                new DefaultEditorKit.DefaultKeyTypedAction().actionPerformed(new ActionEvent(folder, ActionEvent.ACTION_PERFORMED, "x"));
                assertThat(folder.getText()).as(kit.id() + " read-only").isEqualTo("No folder selected");
                var list = (ItemLists.ListControl) view.control("keys");
                list.select("b", false);
                KitFixtures.render(view, 520, 420, 1);
                boolean side = list.side != null;
                find(view, side ? "@ordered/keys/side/up" : list.internalId("b", "up"), AbstractButton.class).doClick(0);
                find(view, side ? "@ordered/keys/side/remove" : list.internalId("b", "remove"), AbstractButton.class).doClick(0);
                assertThat(events).as(kit.id()).containsExactly(new UiEvent.Toggled("toggle", true), new UiEvent.Selected("protocol", Set.of("b")),
                    new UiEvent.Selected("pages", Set.of("b")), new UiEvent.Selected("keys", Set.of("b")), new UiEvent.Reordered("keys", "b", 0),
                    new UiEvent.Removed("keys", "b"));
                assertThat(side).as(kit.id() + " side column").isEqualTo(kit != Kit.DEFAULT);
            } finally { release(context); }
        });
    }

    /** Spec 16.1.9: a SLIDER is no text field under either kit, though the classic stepper face builds it as a spinner. */
    @Test void aSliderIsNoTextFieldUnderEitherKit() {
        for (var kit : List.of(Kit.DEFAULT, KitFixtures.enterprise())) Edt.run(() -> {
            var context = KitFixtures.context(KitFixtures.light(), kit);
            var slider = new Nodes.Field("level", READY, Nodes.FieldKind.NUMBER, "42", "%", "Level", "", Optional.of(new Nodes.Numeric(0, 100, 1, 0)),
                Nodes.FieldPresentation.SLIDER);
            try (var view = new KitView(context, new ViewSpec(slider), e -> {})) {
                assertThatIllegalArgumentException().as(kit.id()).isThrownBy(() -> view.selectAll("level"));
                assertThatIllegalArgumentException().as(kit.id()).isThrownBy(() -> view.composing("level"));
            } finally { release(context); }
        });
    }
}
