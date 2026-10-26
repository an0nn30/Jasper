package dev.jasper.app.workspace;

import dev.jasper.app.appearance.AppearanceBindings;
import dev.jasper.app.appearance.ThemeTestSupport;
import dev.jasper.app.dock.DockGeometry;
import dev.jasper.app.dock.DockState;
import dev.jasper.app.dock.Side;
import dev.jasper.app.dock.Slot;
import dev.jasper.app.dock.ToolWindowMode;
import dev.jasper.app.testsupport.EdtTestExtension;
import dev.jasper.app.testsupport.EnterpriseLook;
import dev.jasper.app.testsupport.LayoutTestSupport;
import dev.jasper.ui.model.ControlState;
import dev.jasper.ui.model.Nodes;
import dev.jasper.ui.model.ViewSpec;
import dev.jasper.ui.swing.KitView;
import dev.jasper.ui.theme.ThemePair;
import dev.jasper.ui.theme.ThemeRoute;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import javax.swing.AbstractButton;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Floating tool windows plan 1 Task 4: a shown detached tool window gets a frame like a docked one, a header over its
 * content, kept outside the area for the window that will hold it.
 */
@ExtendWith(EdtTestExtension.class)
class ToolWindowAreaDetachedTest {
    @BeforeEach void installModuleTheme() throws Exception {
        SwingUtilities.invokeAndWait(() -> ThemeTestSupport.install(true));
    }

    private static ToolWindowArea area(DockState state) { return new ToolWindowArea(new JPanel(), state); }

    /** Docks the window in Left Top, shows it, then detaches it in the mode, as the header's menu does. */
    private static void detach(ToolWindowArea area, String id, String title, JComponent content, ToolWindowMode mode) {
        area.setContent(id, title, content);
        area.state().add(id, Slot.LEFT_TOP);
        area.state().show(id);
        area.state().setMode(id, mode);
        area.sync();
    }

    private static Component part(JComponent frame, String where) { return ((BorderLayout) frame.getLayout()).getLayoutComponent(where); }

    private static ToolWindowHeader header(JComponent frame) { return (ToolWindowHeader) part(frame, BorderLayout.NORTH); }

    @Test void aDetachedWindowsFrameHoldsItsHeaderAndContentOutsideTheArea() {
        var area = area(new DockState());
        var label = new JLabel("alpha");
        detach(area, "a", "Alpha", label, ToolWindowMode.FLOAT);

        assertThat(area.content(Slot.LEFT_TOP)).as("a detached window leaves its slot's frame empty").isNull();
        JComponent frame = area.detachedFrame("a");
        assertThat(frame).isNotNull();
        assertThat(frame.getParent()).as("the frame is no child of the area").isNull();
        assertThat(List.of(area.getComponents())).doesNotContain(frame);
        assertThat(header(frame).titleLabel().getText()).isEqualTo("Alpha");
        assertThat(part(frame, BorderLayout.CENTER)).isSameAs(label);
        assertThat(frame.getInsets()).as("no gutters").isEqualTo(new Insets(0, 0, 0, 0));
        frame.setSize(200, 100);
        assertThat(frame.contains(0, 0)).as("square corners").isTrue();
        assertThat(area.detachedFrames()).containsExactly("a");
        assertThat(area.detachedFrame("nobody")).isNull();
    }

    @Test void aDockedFrameKeepsItsGuttersAndHasNoDetachedFrame() {
        var area = area(new DockState());
        var left = new JLabel("left");
        DockTestSupport.show(area, Slot.LEFT_TOP, left, 250);
        assertThat(area.detachedFrame(DockTestSupport.id(Slot.LEFT_TOP))).isNull();
        assertThat(area.detachedFrames()).isEmpty();
        assertThat(left.getParent().getInsets()).isNotEqualTo(new Insets(0, 0, 0, 0));
    }

    @Test void hidingOrDockingReleasesTheDetachedFrame() {
        var state = new DockState();
        var area = area(state);
        var label = new JLabel("alpha");
        detach(area, "a", "Alpha", label, ToolWindowMode.FLOAT);
        JComponent first = area.detachedFrame("a");

        state.hide("a");
        area.sync();
        assertThat(area.detachedFrame("a")).isNull();
        assertThat(area.detachedFrames()).isEmpty();
        assertThat(label.getParent()).as("hidden content leaves the frame").isNull();
        assertThat(first.getComponentCount()).isZero();

        state.show("a");
        area.sync();
        assertThat(area.detachedFrame("a")).as("shown again, in a fresh frame").isNotNull().isNotSameAs(first);
        assertThat(part(area.detachedFrame("a"), BorderLayout.CENTER)).isSameAs(label);

        state.setMode("a", ToolWindowMode.DOCKED);
        area.sync();
        assertThat(area.detachedFrame("a")).isNull();
        assertThat(area.content(Slot.LEFT_TOP)).as("docked, the content is back in its slot").isSameAs(label);

        state.setMode("a", ToolWindowMode.WINDOW);
        area.sync();
        assertThat(area.content(Slot.LEFT_TOP)).isNull();
        assertThat(part(area.detachedFrame("a"), BorderLayout.CENTER)).isSameAs(label);
    }

    @Test void theFramesFollowTheDocksOrderNotTheOrderTheyMounted() {
        var state = new DockState();
        var area = area(state);
        area.setContent("b", "Bravo", new JLabel("bravo"));
        state.add("b", Slot.RIGHT_TOP);
        state.show("b");
        state.setMode("b", ToolWindowMode.WINDOW);
        area.sync();
        assertThat(area.detachedFrames()).containsExactly("b");
        detach(area, "a", "Alpha", new JLabel("alpha"), ToolWindowMode.FLOAT);
        assertThat(area.detachedFrames()).as("Left Top before Right Top").containsExactly("a", "b");
    }

    @Test void aDetachedWindowWithoutContentHasNoFrame() {
        var state = new DockState();
        var area = area(state);
        state.add("a", Slot.LEFT_TOP);
        state.show("a");
        state.setMode("a", ToolWindowMode.FLOAT);
        area.sync();
        assertThat(area.detachedFrame("a")).isNull();
        assertThat(area.detachedFrames()).isEmpty();
    }

    @Test void aDetachedHeaderIsNoDragSource() {
        var area = area(new DockState());
        var heard = new ArrayList<String>();
        area.dragSource = (component, id) -> heard.add(id);
        area.releaseDragSource = component -> heard.add("released");
        detach(area, "a", "Alpha", new JLabel("alpha"), ToolWindowMode.FLOAT);
        assertThat(heard).isEmpty();
        area.state().hide("a");
        area.sync();
        assertThat(heard).as("nothing was lent, so nothing is taken back").isEmpty();

        DockTestSupport.show(area, Slot.RIGHT_TOP, new JLabel("right"), 250);
        assertThat(heard).as("a docked header still starts a drag").isNotEmpty().containsOnly(DockTestSupport.id(Slot.RIGHT_TOP));
    }

    @Test void theDetachedHeadersMenuOffersDocked() {
        var area = area(new DockState());
        var modes = new ArrayList<String>();
        var hidden = new ArrayList<String>();
        area.onMode = (id, mode) -> modes.add(id + ":" + mode);
        area.onHide = hidden::add;
        detach(area, "a", "Alpha", new JLabel("alpha"), ToolWindowMode.FLOAT);
        var header = header(area.detachedFrame("a"));

        JPopupMenu menu = header.menu();
        assertThat(((JCheckBoxMenuItem) ToolWindowHeaderTest.item(menu, "Float")).isSelected()).isTrue();
        assertThat(((JCheckBoxMenuItem) ToolWindowHeaderTest.item(menu, "Docked")).isSelected()).isFalse();
        ToolWindowHeaderTest.item(menu, "Docked").doClick();
        assertThat(modes).containsExactly("a:DOCKED");
        ToolWindowHeaderTest.item(menu, "Float").doClick();
        assertThat(modes).as("the current mode is inert").hasSize(1);

        header.hideButton().doClick();
        assertThat(hidden).containsExactly("a");
        area.state().setMode("a", ToolWindowMode.WINDOW);
        assertThat(((JCheckBoxMenuItem) ToolWindowHeaderTest.item(header.menu(), "Window")).isSelected())
            .as("the menu reads the mode when it opens").isTrue();
    }

    @Test void aDetachedHeaderTakesItsWindowsMetadata() {
        var area = area(new DockState());
        area.setHeader("a", new dev.jasper.app.contributions.PanelHeaderState("prod", false, List.of(
            new dev.jasper.app.contributions.PanelHeaderState.Action("New session", dev.jasper.app.platform.AppIcons.named("ADD"), () -> { }))));
        detach(area, "a", "Alpha", new JLabel("alpha"), ToolWindowMode.FLOAT);
        JComponent frame = area.detachedFrame("a");
        assertThat(tooltips(frame)).as("metadata set before the frame mounted").contains("New session");
        area.setHeader("a", dev.jasper.app.contributions.PanelHeaderState.EMPTY);
        assertThat(tooltips(frame)).as("metadata set while it shows").doesNotContain("New session");
    }

    @Test void aKitPanelLendsItsHeaderToTheDetachedFrame() {
        var theme = ThemeTestSupport.generated(true);
        try (var bindings = new AppearanceBindings(new ThemePair(theme, theme), failure -> { throw new AssertionError(failure); }, () -> false);
             var view = new KitView(bindings.context(ThemeRoute.CHROME), spec(), event -> { throw new AssertionError("Host action leaked"); })) {
            var state = new DockState();
            var area = new ToolWindowArea(new JPanel(), state);
            var sources = new IdentityHashMap<JComponent, String>();
            area.dragSource = sources::put;
            area.releaseDragSource = sources::remove;
            var hidden = new ArrayList<String>();
            var modes = new ArrayList<String>();
            var menus = new ArrayList<JPopupMenu>();
            area.onHide = hidden::add;
            area.onMode = (id, mode) -> modes.add(id + ":" + mode);
            area.showOptions = (anchor, menu) -> menus.add(menu);
            detach(area, "a", "Alpha", view, ToolWindowMode.FLOAT);

            var header = view.panelHeader("panel");
            assertThat(area.detachedFrame("a")).isNotNull();
            assertThat(part(area.detachedFrame("a"), BorderLayout.CENTER)).isSameAs(view);
            assertThat(part(area.detachedFrame("a"), BorderLayout.NORTH)).as("the view's own header, not a native one").isNull();
            assertThat(buttons(header)).extracting(AbstractButton::getToolTipText).containsExactly("Options", "Hide");
            assertThat(sources).as("a detached header is no drag source").isEmpty();

            button(header, "Options").doClick();
            assertThat(menus).hasSize(1);
            assertThat(((JCheckBoxMenuItem) ToolWindowHeaderTest.item(menus.getFirst(), "Float")).isSelected()).isTrue();
            ToolWindowHeaderTest.item(menus.getFirst(), "Docked").doClick();
            assertThat(modes).containsExactly("a:DOCKED");
            button(header, "Hide").doClick();
            assertThat(hidden).containsExactly("a");

            state.hide("a");
            area.sync();
            assertThat(area.detachedFrame("a")).isNull();
            assertThat(buttons(header)).as("releasing clears the host actions").isEmpty();
            assertThat(sources).isEmpty();
            area.close();
        }
    }

    /** The old frame lets go of the shared header before the new one borrows it, whichever way the window changes mode. */
    @Test void aKitPanelKeepsItsHeaderAsItDetachesAndDocksAgain() {
        var theme = ThemeTestSupport.generated(true);
        try (var bindings = new AppearanceBindings(new ThemePair(theme, theme), failure -> { throw new AssertionError(failure); }, () -> false);
             var view = new KitView(bindings.context(ThemeRoute.CHROME), spec(), event -> { throw new AssertionError("Host action leaked"); })) {
            var state = new DockState();
            var area = new ToolWindowArea(new JPanel(), state);
            var sources = new IdentityHashMap<JComponent, String>();
            area.dragSource = sources::put;
            area.releaseDragSource = sources::remove;
            area.setContent("a", "Alpha", view);
            state.add("a", Slot.LEFT_TOP);
            state.show("a");
            area.sync();
            var header = view.panelHeader("panel");
            assertThat(area.content(Slot.LEFT_TOP)).isSameAs(view);
            assertThat(sources).as("docked, the header starts a drag").containsKey(header);

            for (var mode : List.of(ToolWindowMode.FLOAT, ToolWindowMode.DOCKED, ToolWindowMode.WINDOW, ToolWindowMode.DOCKED)) {
                state.setMode("a", mode);
                area.sync();
                assertThat(view.getParent()).as("shown in %s", mode).isNotNull();
                assertThat(view.panelHeader("panel")).isSameAs(header);
                assertThat(buttons(header)).as("in %s", mode).extracting(AbstractButton::getToolTipText).containsExactly("Options", "Hide");
                assertThat(sources.containsKey(header)).as("a drag source only while docked, in %s", mode).isEqualTo(mode == ToolWindowMode.DOCKED);
                assertThat(area.detachedFrame("a") != null).as("a detached frame only while detached, in %s", mode).isEqualTo(mode.detached());
                assertThat(area.content(Slot.LEFT_TOP) != null).as("a slot frame only while docked, in %s", mode).isEqualTo(!mode.detached());
            }
            area.close();
        }
    }

    @Test void removingTheContentOrClosingTheAreaReleasesTheDetachedFrames() {
        var theme = ThemeTestSupport.generated(true);
        try (var bindings = new AppearanceBindings(new ThemePair(theme, theme), failure -> { throw new AssertionError(failure); }, () -> false);
             var view = new KitView(bindings.context(ThemeRoute.CHROME), spec(), event -> {})) {
            var area = area(new DockState());
            var label = new JLabel("alpha");
            detach(area, "a", "Alpha", label, ToolWindowMode.FLOAT);
            detach(area, "b", "Bravo", view, ToolWindowMode.WINDOW);
            assertThat(area.detachedFrames()).containsExactly("a", "b");
            var header = view.panelHeader("panel");
            assertThat(buttons(header)).hasSize(2);

            area.removeContent("a");
            assertThat(area.detachedFrame("a")).isNull();
            assertThat(label.getParent()).isNull();
            area.sync();
            assertThat(area.detachedFrame("a")).as("no content, so no frame to mount again").isNull();
            assertThat(area.detachedFrames()).containsExactly("b");

            area.close();
            assertThat(area.detachedFrames()).isEmpty();
            assertThat(buttons(header)).as("closing gives the borrowed header back").isEmpty();
        }
    }

    @Test void frameOnScreenAndLandingSize() {
        var state = new DockState();
        var area = area(state);
        // A pretend screen: the area sits 100 across and 50 down in a parentless stand-in frame.
        var standIn = new JPanel(null);
        standIn.setBounds(100, 50, 1000, 650);
        standIn.add(area);
        area.setBounds(0, 0, 1000, 600);
        DockTestSupport.show(area, Slot.LEFT_TOP, new JLabel("left"), 250);
        String left = DockTestSupport.id(Slot.LEFT_TOP);
        area.setContent("hidden", "Hidden", new JLabel("hidden"));
        state.add("hidden", Slot.BOTTOM_LEFT);
        LayoutTestSupport.layoutTree(standIn);

        // The frame is the left side, less its six-pixel gutters on the three edges that touch the area's chrome.
        assertThat(area.frameOnScreen(left)).hasValue(new Rectangle(106, 56, 244, 588));
        assertThat(area.frameOnScreen("hidden")).as("a hidden window shows no frame").isEmpty();
        assertThat(area.frameOnScreen("nobody")).isEmpty();

        assertThat(area.landingSize(left)).isEqualTo(new Dimension(250, 600));
        assertThat(area.landingSize("hidden")).as("what it would fill if it showed in its slot")
            .isEqualTo(DockGeometry.landing(new Rectangle(area.getSize()), state, "hidden", Slot.BOTTOM_LEFT, area.metrics()).orElseThrow().getSize())
            .isEqualTo(new Dimension(1000, state.size(Side.BOTTOM)));
        assertThat(area.landingSize("nobody")).as("a window the dock does not hold").isEqualTo(new Dimension(state.size(Side.LEFT), 300));

        state.setMode(left, ToolWindowMode.FLOAT);
        area.sync();
        assertThat(area.frameOnScreen(left)).as("a detached window has no docked frame").isEmpty();
        assertThat(area.landingSize(left)).as("it would dock back to the same size").isEqualTo(new Dimension(250, 600));
    }

    @Test void underFlushFramesTheFocusedDetachedFrameShowsFocus() {
        EnterpriseLook.install(false);
        try {
            var area = new ToolWindowArea(new JPanel(), new DockState(), EnterpriseLook.kit(), true);
            var inside = new JLabel("alpha");
            var docked = new JLabel("right");
            detach(area, "a", "Alpha", inside, ToolWindowMode.FLOAT);
            DockTestSupport.show(area, Slot.RIGHT_TOP, docked, 250);
            var detachedHeader = header(area.detachedFrame("a"));
            var dockedHeader = area.headerOf(Slot.RIGHT_TOP);

            area.focusMoved(inside);
            assertThat(detachedHeader.focused()).isTrue();
            assertThat(dockedHeader.focused()).isFalse();
            assertThat(area.focusedSlot()).as("the focused frame is a detached one").isEmpty();

            area.focusMoved(docked);
            assertThat(detachedHeader.focused()).isFalse();
            assertThat(dockedHeader.focused()).isTrue();
            assertThat(area.focusedSlot()).hasValue(Slot.RIGHT_TOP);

            area.focusMoved(inside);
            area.focusMoved(null);
            assertThat(detachedHeader.focused()).isFalse();
            assertThat(dockedHeader.focused()).isFalse();
        } finally { EnterpriseLook.restoreDefault(); }
    }

    @Test void standardFramesShowNoFocusOnADetachedHeader() {
        var area = area(new DockState());
        var inside = new JLabel("alpha");
        detach(area, "a", "Alpha", inside, ToolWindowMode.FLOAT);
        area.focusMoved(inside);
        assertThat(header(area.detachedFrame("a")).focused()).isFalse();
    }

    @Test void activatingADetachedFrameMovesTheKeyboardIntoItInAnyFrameStyle() {
        var area = area(new DockState());
        var focused = new ArrayList<Component>();
        area.focuser = focused::add;
        var content = new JPanel(new BorderLayout());
        var list = new JList<>(new String[] {"Mac"});
        content.add(new JLabel("Hosts"), BorderLayout.NORTH);
        content.add(list, BorderLayout.CENTER);
        detach(area, "a", "Alpha", content, ToolWindowMode.FLOAT);
        detach(area, "b", "Bravo", new JLabel("nothing keyed"), ToolWindowMode.WINDOW);

        area.activate("a");
        PressedFrameTest.flushEdt();
        assertThat(focused).as("the first keyed control of the content").containsExactly(list);
        focused.clear();

        area.activate("b");
        PressedFrameTest.flushEdt();
        assertThat(focused).as("nothing keyed: the frame itself").containsExactly(area.detachedFrame("b"));
        assertThat(area.detachedFrame("b").isFocusable()).isTrue();
        focused.clear();

        area.activate("nobody");
        area.state().hide("a");
        area.sync();
        area.activate("a");
        PressedFrameTest.flushEdt();
        assertThat(focused).as("an unknown or hidden window has no frame to take the keyboard").isEmpty();
    }

    @Test void underFlushFramesActivatingADetachedFrameShowsItsHeaderFocused() {
        EnterpriseLook.install(false);
        try {
            var area = new ToolWindowArea(new JPanel(), new DockState(), EnterpriseLook.kit(), true);
            var focused = new ArrayList<Component>();
            area.focuser = focused::add;
            var list = new JList<>(new String[] {"Mac"});
            detach(area, "a", "Alpha", list, ToolWindowMode.FLOAT);
            DockTestSupport.show(area, Slot.RIGHT_TOP, new JLabel("right"), 250);
            var dockedHeader = area.headerOf(Slot.RIGHT_TOP);
            area.activate(DockTestSupport.id(Slot.RIGHT_TOP));
            assertThat(dockedHeader.focused()).isTrue();

            area.activate("a");
            assertThat(header(area.detachedFrame("a")).focused()).as("shown at once").isTrue();
            assertThat(dockedHeader.focused()).as("the docked frame lets go").isFalse();
            PressedFrameTest.flushEdt();
            assertThat(focused).contains(list);
        } finally { EnterpriseLook.restoreDefault(); }
    }

    private static List<String> tooltips(Container root) {
        var result = new ArrayList<String>();
        for (AbstractButton button : buttons(root)) if (button.getToolTipText() != null) result.add(button.getToolTipText());
        return result;
    }

    private static List<AbstractButton> buttons(Container root) {
        var result = new ArrayList<AbstractButton>();
        for (var child : root.getComponents()) {
            if (child instanceof AbstractButton button) result.add(button);
            else if (child instanceof Container container) result.addAll(buttons(container));
        }
        return result;
    }

    private static AbstractButton button(Container root, String name) {
        return buttons(root).stream().filter(b -> name.equals(b.getAccessibleContext().getAccessibleName())).findFirst().orElseThrow();
    }

    private static ViewSpec spec() {
        return new ViewSpec(new Nodes.Panel("panel", ControlState.READY,
            new Nodes.Header("header", ControlState.READY, "Hosts", OptionalInt.of(3), Optional.empty(), Optional.empty(), List.of()),
            Optional.empty(), new Nodes.Text("body", ControlState.READY, "Contents", Nodes.TextRole.BODY), Optional.empty()));
    }
}
