package dev.jasper.ui.swing;

import static dev.jasper.ui.swing.KitViewTest.release;
import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.ui.model.ControlState;
import dev.jasper.ui.model.MenuItem;
import dev.jasper.ui.model.Nodes;
import dev.jasper.ui.model.ViewSpec;
import dev.jasper.ui.testsupport.Edt;
import dev.jasper.ui.testsupport.KitFixtures;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Plan 9b Task 5: a navigation line's menu button keeps its width, and the classic face's title line. */
class NavigationLinesTest {
    static ViewSpec crowded() {
        return new ViewSpec(new Nodes.Stack("line", ControlState.READY, Nodes.LayoutRole.NAVIGATION, List.of(
            new Nodes.Text("text", ControlState.READY, "A text far longer than the line has room for, which takes the shrinking", Nodes.TextRole.BODY),
            new Nodes.MenuButton("more", ControlState.READY, "Actions", "", Nodes.ButtonKind.SECONDARY, "",
                List.of(new MenuItem("open", "Open", "", "open", true, false)), ""))));
    }

    static ViewSpec titled() {
        return new ViewSpec(new Nodes.Stack("head", ControlState.READY, Nodes.LayoutRole.NAVIGATION, List.of(
            new Nodes.Stack("identity", ControlState.READY, Nodes.LayoutRole.VERTICAL, List.of(
                new Nodes.Text("name", ControlState.READY, "Tidy Tabs", Nodes.TextRole.DETAIL_TITLE),
                new Nodes.Text("version", ControlState.READY, "1.0.0", Nodes.TextRole.DESCRIPTION),
                new Nodes.Text("vendor", ControlState.READY, "Example", Nodes.TextRole.DESCRIPTION))),
            new Nodes.Button("primary", ControlState.READY, "Disable", "", Nodes.ButtonKind.SECONDARY, "disable", ""))));
    }

    @Test void aNavigationLinesMenuButtonKeepsItsWidthWhileTheTextShrinks() {
        Edt.run(() -> {
            for (boolean classic : new boolean[] {false, true}) {
                var context = classic ? KitFixtures.context(KitFixtures.enterpriseLight(), KitFixtures.enterprise()) : KitFixtures.context();
                try (var view = new KitView(context, crowded(), event -> { })) {
                    view.setSize(200, 60);
                    for (int i = 0; i < 3; i++) InteractiveRowsTest.layout(view);
                    var menu = view.control("more").component;
                    var text = view.control("text").component;
                    assertThat(menu).isInstanceOf(Menus.MenuSurface.class);
                    assertThat(menu.getWidth()).as((classic ? "classic" : "default") + ": the menu at its preferred width").isEqualTo(menu.getPreferredSize().width);
                    assertThat(text.getWidth()).as("the text takes the shrinking").isLessThan(text.getPreferredSize().width);
                } finally { release(context); }
            }
        });
    }

    @Test void theClassicTitleLineStandsTheButtonOnTheTitleAndTheDefaultCentresIt() {
        Edt.run(() -> {
            for (boolean classic : new boolean[] {false, true}) {
                var context = classic ? KitFixtures.context(KitFixtures.enterpriseLight(), KitFixtures.enterprise()) : KitFixtures.context();
                try (var view = new KitView(context, titled(), event -> { })) {
                    view.setSize(500, 120);
                    for (int i = 0; i < 3; i++) InteractiveRowsTest.layout(view);
                    var button = view.control("primary").component;
                    var line = button.getParent();
                    assertThat(line.getHeight()).as("precondition: the title block is taller than the button").isGreaterThan(button.getHeight());
                    if (classic) assertThat(button.getY()).as("classic: on the title's line").isZero();
                    else assertThat(button.getY()).as("default: centred on the title block").isEqualTo((line.getHeight() - button.getHeight()) / 2).isPositive();
                } finally { release(context); }
            }
        });
    }
}
