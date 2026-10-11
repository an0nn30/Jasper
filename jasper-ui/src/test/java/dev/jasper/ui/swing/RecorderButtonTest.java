package dev.jasper.ui.swing;

import dev.jasper.ui.model.*;
import dev.jasper.ui.model.Nodes.*;
import dev.jasper.ui.testsupport.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class RecorderButtonTest {
    private static ViewSpec spec(ControlState state) { return new ViewSpec(new Button("candidate",state,"Press a key combination…","",ButtonKind.SECONDARY,"record","Record shortcut")); }
    @Test void presentationRetainsIdentityActionAndKeycapsAndResetsIndependently() {
        Edt.run(()->{
            var context=KitFixtures.context();var events=new ArrayList<UiEvent>();
            try(var view=new KitView(context,spec(ControlState.READY),events::add)) {
                var button=KitViewTest.find(view,"candidate",javax.swing.JButton.class);
                var ordinary=button.getPreferredSize();
                view.setButtonValuePresentation("candidate",ButtonValuePresentation.RECORDER_ARMED);
                assertThat(button.getHorizontalAlignment()).isEqualTo(javax.swing.SwingConstants.LEFT);
                button.doClick(0);assertThat(events).containsExactly(new UiEvent.Action("record"));
                var keys=new KeycapSequence(List.of("Command","Shift","Option","Control","Return"),"Command Shift Option Control Return");
                view.setButtonKeycaps("candidate",Optional.of(keys));
                assertThat(Composition.height(button,120)).isGreaterThan(Composition.height(button,600));
                view.update(spec(ControlState.READY));
                assertThat(KitViewTest.find(view,"candidate",javax.swing.JButton.class)).isSameAs(button);
                assertThat(button.getAccessibleContext().getAccessibleName()).isEqualTo(keys.accessibleName());
                view.setButtonValuePresentation("candidate",ButtonValuePresentation.RECORDER);
                view.setButtonKeycaps("candidate",Optional.empty());
                assertThat(button.getText()).isEqualTo("Press a key combination…");
                assertThat(button.getHorizontalAlignment()).isEqualTo(javax.swing.SwingConstants.LEFT);
                view.setButtonValuePresentation("candidate",ButtonValuePresentation.DEFAULT);
                assertThat(button.getHorizontalAlignment()).isEqualTo(javax.swing.SwingConstants.CENTER);
                assertThat(button.getPreferredSize()).isEqualTo(ordinary);
                view.update(spec(new ControlState(true,true,true,"")));
                button.doClick(0);assertThat(events).hasSize(1);
                assertThatThrownBy(()->view.setButtonValuePresentation("missing",ButtonValuePresentation.RECORDER)).isInstanceOf(IllegalArgumentException.class);
            } finally {KitViewTest.release(context);}
        });
    }
    @Test void orderedFooterKeepsCompositionAlignmentAcrossRefreshThemeAndRecorderReset() {
        Edt.run(() -> {
            var context = KitFixtures.context();
            var events = new ArrayList<UiEvent>();
            var spec = new ViewSpec(new ItemList("keys", ControlState.READY, ListKind.ORDERED,
                    List.of(), Set.of(), "", List.of(), SelectionMode.SINGLE,
                    Optional.of(new Button("add-key", ControlState.READY, "Add Stored Key...", "add",
                            ButtonKind.LINK, "add-key", ""))));
            try (var view = new KitView(context, spec, events::add)) {
                var button = KitViewTest.find(view, "add-key", javax.swing.JButton.class);
                assertThat(button.getHorizontalAlignment()).isEqualTo(javax.swing.SwingConstants.LEADING);
                view.update(spec);
                assertThat(KitViewTest.find(view, "add-key", javax.swing.JButton.class)).isSameAs(button);
                assertThat(button.getHorizontalAlignment()).isEqualTo(javax.swing.SwingConstants.LEADING);
                var light = KitFixtures.light();
                context.binding().publish(new dev.jasper.ui.theme.ThemePair(light, light));
                assertThat(button.getHorizontalAlignment()).isEqualTo(javax.swing.SwingConstants.LEADING);
                view.setButtonValuePresentation("add-key", ButtonValuePresentation.RECORDER_ARMED);
                assertThat(button.getHorizontalAlignment()).isEqualTo(javax.swing.SwingConstants.LEFT);
                view.setButtonValuePresentation("add-key", ButtonValuePresentation.DEFAULT);
                assertThat(button.getHorizontalAlignment()).isEqualTo(javax.swing.SwingConstants.LEADING);
                button.doClick(0);
                assertThat(events).containsExactly(new UiEvent.Action("add-key"));
            } finally { KitViewTest.release(context); }
        });
    }

}
