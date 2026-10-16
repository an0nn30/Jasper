package dev.jasper.ui.swing;

import static dev.jasper.ui.swing.KitViewTest.*;
import static org.assertj.core.api.Assertions.*;

import dev.jasper.ui.model.*;
import dev.jasper.ui.model.Nodes.*;
import dev.jasper.ui.testsupport.*;
import java.util.*;
import org.junit.jupiter.api.Test;

/** Spec 8.2's {@code ChoiceKind.TABS}: the default look draws the segment track, labelled by its tabs. */
@SuppressWarnings("try")
class TabsTest {
    static Choice choice(ChoiceKind kind) {
        return new Choice("tabs", ControlState.READY, kind, List.of(new Option("overview", "Overview", Optional.empty()),
            new Option("details", "Details", Optional.empty())), "overview", "Plugin information", "");
    }

    @Test void tabsAreASegmentTrackWithoutAVisibleLabelThatReportsSelected() {
        Edt.run(() -> {
            var context = KitFixtures.context(); var events = new ArrayList<UiEvent>();
            try (var view = new KitView(context, new ViewSpec(choice(ChoiceKind.TABS)), events::add)) {
                KitFixtures.render(view, 360, 60, 1);
                var control = (Choices.ChoiceControl) view.control("tabs");
                assertThat(control.input).isInstanceOf(Choices.SegmentTrack.class);
                assertThat(control.label.isVisible()).isFalse();
                assertThat(control.input.getAccessibleContext().getAccessibleName()).isEqualTo("Plugin information");
                find(view, "tabs.option.details", Choices.SegmentButton.class).doClick(0);
                assertThat(events).containsExactly(new UiEvent.Selected("tabs", Set.of("details")));
                control.embeddedLabel(false);
                assertThat(control.label.isVisible()).as("not even when a composition un-embeds it").isFalse();
            } finally { release(context); }
        });
    }

    @Test void anExclusiveChoiceStillShowsItsLabel() {
        Edt.run(() -> {
            var context = KitFixtures.context();
            try (var view = new KitView(context, new ViewSpec(choice(ChoiceKind.EXCLUSIVE)), e -> {})) {
                KitFixtures.render(view, 360, 80, 1);
                assertThat(((Choices.ChoiceControl) view.control("tabs")).label.isVisible()).isTrue();
            } finally { release(context); }
        });
    }
}
