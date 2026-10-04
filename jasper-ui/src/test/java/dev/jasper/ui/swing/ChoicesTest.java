package dev.jasper.ui.swing;

import dev.jasper.ui.model.*;
import dev.jasper.ui.model.Nodes.*;
import dev.jasper.ui.testsupport.*;
import java.util.*;
import javax.swing.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static dev.jasper.ui.swing.KitViewTest.*;

@SuppressWarnings("unchecked")
class ChoicesTest {
    static Choice choice(ChoiceKind kind, String selected, ControlState state) {
        return new Choice("choice", state, kind, List.of(new Option("a", "<html>Alpha", Optional.of(Hue.BLUE)), new Option("b", "Beta", Optional.empty())), selected, "Protocol", "choose-protocol");
    }
    @Test void dropdownDispatchesNodeAndImmutableSelectionWithoutEventsFromUpdates() {
        Edt.run(() -> {
            var context = KitFixtures.context(); var events = new ArrayList<UiEvent>();
            try (var view = new KitView(context, stack(choice(ChoiceKind.DROPDOWN, "a", ControlState.READY)), events::add)) {
                var combo = find(view, "choice.input", JComboBox.class); combo.setSelectedIndex(1);
                assertThat(events).containsExactly(new UiEvent.Selected("choice", Set.of("b")));
                assertThatThrownBy(() -> ((UiEvent.Selected)events.getFirst()).ids().clear()).isInstanceOf(UnsupportedOperationException.class);
                events.clear(); view.update(stack(choice(ChoiceKind.DROPDOWN, "a", ControlState.READY)));
                assertThat(find(view, "choice.input", JComboBox.class)).isSameAs(combo); assertThat(combo.getSelectedIndex()).isZero(); assertThat(events).isEmpty();
                var renderer = combo.getRenderer().getListCellRendererComponent(new JList<>(), combo.getItemAt(0), 0, false, false);
                assertThat(((JComponent)renderer).getClientProperty("html")).isNull();
            } finally { release(context); }
        });
    }
    @Test void segmentsKeyboardSelectAndPickerOpensItsConfiguredAction() {
        Edt.run(() -> {
            var context = KitFixtures.context(); var events = new ArrayList<UiEvent>();
            try (var view = new KitView(context, stack(choice(ChoiceKind.SEGMENTED, "a", ControlState.READY)), events::add)) {
                var a = find(view, "choice.option.a", JButton.class);
                var key = a.getInputMap(JComponent.WHEN_FOCUSED).get(KeyStroke.getKeyStroke("RIGHT")); assertThat(key).isNotNull();
                a.getActionMap().get(key).actionPerformed(new java.awt.event.ActionEvent(a, 0, ""));
                assertThat(events).containsExactly(new UiEvent.Selected("choice", Set.of("b")));
                events.clear(); view.update(stack(choice(ChoiceKind.PICKER, "a", ControlState.READY)));
                find(view, "choice.input", JButton.class).doClick(0); assertThat(events).containsExactly(new UiEvent.Action("choose-protocol"));
            } finally { release(context); }
        });
    }
    @Test void emptyPickerUsesPlaceholderRoleAndTrailingChevron() {
        Edt.run(() -> {
            var context = KitFixtures.context();
            try (var view = new KitView(context, stack(choice(ChoiceKind.PICKER, "", ControlState.READY)), e -> {})) {
                var picker = find(view, "choice.input", JButton.class); assertThat(picker.getText()).isEqualTo("Choose…");
                assertThat(picker.getForeground()).isEqualTo(context.theme().semantic().color(dev.jasper.ui.theme.SemanticColor.TEXT_TERTIARY));
                picker.setSize(180, 34); var image = ButtonsTest.paint(picker); int right = picker.getWidth()-com.formdev.flatlaf.util.UIScale.scale(16);
                int count=0; int color=context.theme().semantic().color(dev.jasper.ui.theme.SemanticColor.BUTTON_SECONDARY_BG).getRGB();
                for (int x=right-5; x<right+5; x++) for (int y=10; y<24; y++) if (image.getRGB(x,y)!=color) count++;
                assertThat(count).isPositive();
            } finally { release(context); }
        });
    }
    @Test void disabledSegmentTrackUsesDisabledOpacityOnce() {
        Edt.run(() -> {
            var context = KitFixtures.context();
            try (var view = new KitView(context, stack(choice(ChoiceKind.SEGMENTED, "a", new ControlState(true, false, false, ""))), e -> {})) {
                var track = find(view, "choice.input", JComponent.class); track.setSize(200, 32);
                int alpha=(ButtonsTest.paint(track).getRGB(100, com.formdev.flatlaf.util.UIScale.scale(1))>>>24)&255;
                assertThat(alpha).isBetween(106,108);
            } finally { release(context); }
        });
    }
    @Test void busyAndParentDisabledChoicesCannotDispatchAndSelectionCallbacksMayUpdate() {
        Edt.run(() -> {
            var context = KitFixtures.context(); var events = new ArrayList<UiEvent>(); KitView[] owner = {null};
            try (var view = new KitView(context, stack(choice(ChoiceKind.DROPDOWN, "a", ControlState.READY)), event -> {
                events.add(event); owner[0].update(stack(choice(ChoiceKind.DROPDOWN, "b", new ControlState(true, true, true, ""))));
            })) {
                owner[0] = view; var combo = find(view, "choice.input", JComboBox.class); combo.setSelectedIndex(1);
                assertThat(combo.isEnabled()).isFalse(); combo.setSelectedIndex(0); assertThat(events).hasSize(1);
                assertThat(combo.getAccessibleContext().getAccessibleName()).isEqualTo("Protocol");
            } finally { release(context); }
        });
    }
}
