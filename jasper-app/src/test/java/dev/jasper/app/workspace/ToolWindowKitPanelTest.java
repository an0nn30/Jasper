package dev.jasper.app.workspace;

import dev.jasper.app.appearance.*;
import dev.jasper.app.dock.*;
import dev.jasper.app.testsupport.EdtTestExtension;
import dev.jasper.ui.model.*;
import dev.jasper.ui.swing.*;
import dev.jasper.ui.theme.*;
import java.util.*;
import javax.swing.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(EdtTestExtension.class)
class ToolWindowKitPanelTest {
    @Test void kitPanelRetainsOneHeaderAndHostActionsAcrossHideAndRemount() {
        ThemeManager.install(Theme.DARK);
        var theme = ThemeManager.generated(Theme.DARK);
        try (var bindings = new AppearanceBindings(new ThemePair(theme, theme), failure -> { throw new AssertionError(failure); }, () -> false);
             var view = new KitView(bindings.context(ThemeRoute.CHROME), spec(), event -> { throw new AssertionError("Host action leaked"); })) {
            var area = new ToolWindowArea(new JPanel(), new DockState());
            var sources = new java.util.IdentityHashMap<JComponent, String>();
            area.dragSource = (component, id) -> { assertThat(sources.put(component, id)).isNull(); };
            area.releaseDragSource = component -> assertThat(sources.remove(component)).isNotNull();
            var hidden = new ArrayList<String>();
            area.onHide = hidden::add;
            DockTestSupport.show(area, Slot.LEFT_TOP, view, 250);
            assertThat(area.headerOf(Slot.LEFT_TOP)).as("kit header replaces outer legacy header").isNull();
            var header = view.panelHeader("panel");
            assertThat(buttons(header)).hasSize(2);
            assertThat(sources).containsKey(header);
            buttons(header).stream().filter(b -> "Hide".equals(b.getToolTipText())).findFirst().orElseThrow().doClick();
            assertThat(hidden).containsExactly(DockTestSupport.id(Slot.LEFT_TOP));
            DockTestSupport.hide(area, Slot.LEFT_TOP);
            assertThat(buttons(header)).isEmpty();
            assertThat(sources).isEmpty();
            DockTestSupport.show(area, Slot.LEFT_TOP, view, 250);
            area.sync(); area.sync();
            assertThat(view.panelHeader("panel")).isSameAs(header);
            assertThat(buttons(header)).hasSize(2);
            buttons(header).stream().filter(b -> "Hide".equals(b.getToolTipText())).findFirst().orElseThrow().doClick();
            assertThat(hidden).hasSize(2);
            view.update(spec("replacement", "replacement-header"));
            var replacement = view.panelHeader("replacement");
            assertThat(replacement).isNotSameAs(header);
            assertThat(sources).doesNotContainKey(header).containsKey(replacement);
            assertThat(buttons(replacement)).hasSize(2);
            view.close(); area.sync(); area.close();
            assertThat(sources).isEmpty();
        }
    }
    private static List<AbstractButton> buttons(java.awt.Container root) {
        var result = new ArrayList<AbstractButton>();
        for (var child : root.getComponents()) {
            if (child instanceof AbstractButton button) result.add(button);
            else if (child instanceof java.awt.Container container) result.addAll(buttons(container));
        }
        return result;
    }
    private static ViewSpec spec() { return spec("panel", "header"); }
    private static ViewSpec spec(String panel, String header) {
        return new ViewSpec(new Nodes.Panel(panel, ControlState.READY,
            new Nodes.Header(header, ControlState.READY, "Hosts", OptionalInt.of(3), Optional.empty(), Optional.empty(), List.of()),
            Optional.empty(), new Nodes.Text("body", ControlState.READY, "Contents", Nodes.TextRole.BODY), Optional.empty()));
    }
}
