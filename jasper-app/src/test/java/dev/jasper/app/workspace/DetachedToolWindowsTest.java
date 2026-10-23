package dev.jasper.app.workspace;

import com.formdev.flatlaf.util.UIScale;
import dev.jasper.app.appearance.AppearanceBindings;
import dev.jasper.app.appearance.ThemeTestSupport;
import dev.jasper.app.contributions.Contributions;
import dev.jasper.app.contributions.PanelEntry;
import dev.jasper.app.dock.Slot;
import dev.jasper.app.dock.ToolWindowMode;
import dev.jasper.app.persistence.UiState;
import dev.jasper.ui.model.ControlState;
import dev.jasper.ui.model.Nodes;
import dev.jasper.ui.model.ViewSpec;
import dev.jasper.ui.swing.KitView;
import dev.jasper.ui.theme.ThemePair;
import dev.jasper.ui.theme.ThemeRoute;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Rectangle;
import java.awt.event.HierarchyEvent;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.BiFunction;
import javax.swing.AbstractButton;
import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static dev.jasper.app.workspace.DesktopTestSupport.edt;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Floating tool windows plan 1 Task 5: a detached tool window's own window opens, moves, reopens and closes as the main
 * window's dock says, and its mode, bounds and visibility are saved and restored. The main window stands in a stand-in
 * frame on a pretend screen with one 1920 by 1080 display; the native windows are recording shells.
 */
class DetachedToolWindowsTest {
    private static final String A = "dev.x.alpha";
    private static final Rectangle SCREEN = new Rectangle(0, 0, 1920, 1080);
    /** The stand-in main window on the pretend screen. */
    private static final Rectangle MAIN = new Rectangle(100, 50, 1200, 800);

    @TempDir Path dir;

    @AfterEach void closeWindows() throws Exception { DesktopTestSupport.closeOwners(); }

    /** Native windows as text: each opening, and every call on each window's shell. */
    private static final class Shells implements BiFunction<DetachedToolWindows.Opening, DetachedToolWindows.Events, DetachedToolWindows.Shell> {
        final List<String> log = new ArrayList<>();
        final Map<String, DetachedToolWindows.Opening> openings = new HashMap<>();
        final Map<String, DetachedToolWindows.Events> events = new HashMap<>();
        final Map<String, JComponent> contents = new HashMap<>();
        boolean active;

        @Override public DetachedToolWindows.Shell apply(DetachedToolWindows.Opening opening, DetachedToolWindows.Events heard) {
            String id = opening.id();
            log.add("open " + id + " " + opening.mode() + " " + text(opening.bounds()));
            openings.put(id, opening);
            events.put(id, heard);
            return new DetachedToolWindows.Shell(() -> log.add("show " + id), () -> log.add("toFront " + id),
                () -> log.add("dispose " + id), () -> active, bounds -> log.add("place " + id + " " + text(bounds)),
                frame -> { contents.put(id, frame); log.add("content " + id); });
        }

        /** What was recorded since the last call. */
        List<String> take() {
            var taken = List.copyOf(log);
            log.clear();
            return taken;
        }
    }

    private static String text(Rectangle bounds) { return bounds.x + "," + bounds.y + " " + bounds.width + "x" + bounds.height; }

    private static String open(ToolWindowMode mode, Rectangle bounds) { return "open " + A + " " + mode + " " + text(bounds); }

    /**
     * A main window connected to {@code model} and {@code state} in a stand-in frame at {@link #MAIN} on the pretend
     * screen, ready to open windows on {@link #SCREEN}; no shells yet.
     */
    private static WindowContent window(Contributions model, UiState state) {
        WindowContent owner = DesktopTestSupport.content(DesktopTestSupport.launcher(new ArrayDeque<>()));
        owner.connectContributions(model, state);
        TabDragTestSupport.frame(owner, MAIN.x, MAIN.y, MAIN.width, MAIN.height);
        DetachedToolWindows windows = owner.detachedWindows();
        windows.ready = () -> true;
        windows.displays = () -> List.of(SCREEN);
        return owner;
    }

    /** Lays the window's stand-in frame out again, as Swing would after a dock change. */
    private static void relayout(WindowContent owner) {
        Component top = owner;
        while (top.getParent() != null) top = top.getParent();
        TabDragTestSupport.relayout((JPanel) top);
    }

    /** A main window with panel {@link #A} ("Alpha") in Left Top, holding {@code view}, and recording shells. */
    private static final class Fixture {
        final Contributions model = new Contributions();
        final UiState state;
        final WindowContent owner;
        final Shells shells = new Shells();
        final JComponent view;
        final PanelEntry entry;
        final List<String> closed = new ArrayList<>();

        Fixture() { this(UiState.inMemory()); }

        Fixture(UiState state) { this(state, new JLabel("alpha"), true); }

        Fixture(UiState state, JComponent view, boolean withShells) {
            this.state = state;
            this.view = view;
            owner = window(model, state);
            entry = model.addPanel(A, "Alpha", new ImageIcon(), Slot.LEFT_TOP, site -> {
                site.onClosed(() -> shells.log.add("closed " + A));
                return view;
            });
            relayout(owner);
            if (withShells) owner.detachedShells(shells);
        }

        DetachedToolWindows windows() { return owner.detachedWindows(); }

        void toggle() {
            model.requestPanel(new Contributions.PanelRequest(owner.id(), A, Contributions.PanelRequest.Op.TOGGLE));
            relayout(owner);
        }

        AbstractButton button() { return owner.leftStripe().buttons().getFirst(); }

        /** The stripe button's Options menu. */
        JPopupMenu stripeMenu() { return button().getComponentPopupMenu(); }

        /** The detached frame's header's Options menu. */
        JPopupMenu detachedMenu() {
            JComponent frame = owner.area().detachedFrame(A);
            return ((ToolWindowHeader) ((BorderLayout) frame.getLayout()).getLayoutComponent(BorderLayout.NORTH)).menu();
        }

        void choose(JPopupMenu menu, String item) {
            ToolWindowHeaderTest.item(menu, item).doClick();
            relayout(owner);
        }

        /** Shows the panel docked, then floats it from its header: its window opens where its frame was. */
        Rectangle floated() {
            toggle();
            Rectangle docked = owner.area().frameOnScreen(A).orElseThrow();
            choose(owner.area().headerOf(Slot.LEFT_TOP).menu(), "Float");
            assertThat(shells.take()).containsExactly(open(ToolWindowMode.FLOAT, docked), "show " + A);
            return docked;
        }
    }

    @Test void viewModeFloatOpensItsWindowWhereTheFrameWas() throws Exception {
        edt(() -> {
            var fixture = new Fixture();
            fixture.toggle();
            Rectangle docked = fixture.owner.area().frameOnScreen(A).orElseThrow();
            assertThat(docked.width).as("the docked frame is wider than the minimum").isGreaterThanOrEqualTo(UIScale.scale(240));
            assertThat(docked.height).isGreaterThanOrEqualTo(UIScale.scale(160));
            assertThat(SCREEN.contains(docked)).as("and on the display, so fitting keeps it").isTrue();

            fixture.choose(fixture.owner.area().headerOf(Slot.LEFT_TOP).menu(), "Float");
            assertThat(fixture.shells.take()).containsExactly(open(ToolWindowMode.FLOAT, docked), "show " + A);
            assertThat(fixture.owner.area().content(Slot.LEFT_TOP)).as("the slot shows nothing").isNull();
            assertThat(fixture.owner.dock().visible(A)).isTrue();
            assertThat(fixture.owner.dock().bounds(A)).hasValue(docked);
            var opening = fixture.shells.openings.get(A);
            assertThat(opening.title()).isEqualTo("Alpha");
            assertThat(opening.frame()).isSameAs(fixture.owner.area().detachedFrame(A));
            assertThat(SwingUtilities.isDescendingFrom(fixture.view, opening.frame())).as("the same component, moved").isTrue();
            assertThat(opening.owner()).as("no native main window in a headless test").isNull();
            assertThat(fixture.windows().showing(A)).isTrue();
            assertThat(fixture.owner.panelDetached(A)).isTrue();
        });
    }

    @Test void viewModeWindowReopensItAsAWindow() throws Exception {
        edt(() -> {
            var fixture = new Fixture();
            Rectangle bounds = fixture.floated();
            fixture.choose(fixture.detachedMenu(), "Window");
            assertThat(fixture.shells.take()).containsExactly("dispose " + A, open(ToolWindowMode.WINDOW, bounds), "show " + A);
            assertThat(fixture.owner.dock().mode(A)).isEqualTo(ToolWindowMode.WINDOW);
            assertThat(fixture.owner.dock().visible(A)).isTrue();
        });
    }

    @Test void viewModeDockedPutsItBack() throws Exception {
        edt(() -> {
            var fixture = new Fixture();
            Rectangle bounds = fixture.floated();
            fixture.choose(fixture.stripeMenu(), "Docked");
            assertThat(fixture.shells.take()).containsExactly("dispose " + A);
            assertThat(fixture.owner.area().content(Slot.LEFT_TOP)).isSameAs(fixture.view);
            assertThat(fixture.owner.dock().bounds(A)).as("kept for the next detach").hasValue(bounds);
            assertThat(fixture.windows().showing(A)).isFalse();
            assertThat(fixture.owner.panelDetached(A)).isFalse();
        });
    }

    @Test void aHiddenToolWindowFloatsHiddenAndOpensCentredOnTheMainWindowWhenShown() throws Exception {
        edt(() -> {
            var fixture = new Fixture();
            Dimension landing = fixture.owner.area().landingSize(A);
            fixture.choose(fixture.stripeMenu(), "Float");
            assertThat(fixture.shells.take()).as("no window while it is hidden").isEmpty();
            assertThat(fixture.owner.dock().mode(A)).isEqualTo(ToolWindowMode.FLOAT);
            assertThat(fixture.owner.dock().visible(A)).isFalse();
            assertThat(fixture.owner.panelDetached(A)).as("detached while hidden too").isTrue();

            fixture.button().doClick();
            int width = Math.max(landing.width, UIScale.scale(240)), height = Math.max(landing.height, UIScale.scale(160));
            var centred = new Rectangle(MAIN.x + (MAIN.width - width) / 2, MAIN.y + (MAIN.height - height) / 2, width, height);
            assertThat(fixture.shells.take()).containsExactly(open(ToolWindowMode.FLOAT, centred), "show " + A);
            assertThat(fixture.button().isSelected()).as("its stripe button shows it showing").isTrue();
        });
    }

    @Test void aHiddenToolWindowsWindowIsAtLeastTheMinimumSize() throws Exception {
        edt(() -> {
            var fixture = new Fixture();
            fixture.owner.dock().resize(dev.jasper.app.dock.Side.LEFT, dev.jasper.app.dock.DockState.MIN_SIZE);
            relayout(fixture.owner);
            assertThat(fixture.owner.area().landingSize(A).width).isLessThan(UIScale.scale(240));
            fixture.choose(fixture.stripeMenu(), "Float");
            assertThat(fixture.owner.dock().bounds(A)).hasValueSatisfying(bounds -> {
                assertThat(bounds.width).isEqualTo(UIScale.scale(240));
                assertThat(bounds.x).as("centred on the main window").isEqualTo(MAIN.x + (MAIN.width - bounds.width) / 2);
            });
        });
    }

    @Test void closingItsWindowHidesItAndKeepsItDetached() throws Exception {
        edt(() -> {
            var fixture = new Fixture();
            fixture.floated();
            var events = fixture.shells.events.get(A);
            var moved = new Rectangle(300, 200, 500, 400);
            events.moved().accept(moved);
            assertThat(fixture.shells.take()).as("a move is only remembered").isEmpty();
            assertThat(fixture.owner.dock().bounds(A)).hasValue(moved);

            events.closing().run();
            assertThat(fixture.shells.take()).containsExactly("dispose " + A);
            assertThat(fixture.owner.dock().visible(A)).isFalse();
            assertThat(fixture.owner.dock().mode(A)).isEqualTo(ToolWindowMode.FLOAT);
            assertThat(fixture.button().isSelected()).isFalse();
            assertThat(fixture.state.panel(A)).as("hiding saves the window's place").hasValue(new UiState.Panel(Slot.LEFT_TOP, 0, false,
                ToolWindowMode.FLOAT, Optional.of(new UiState.Bounds(300, 200, 500, 400))));

            fixture.button().doClick();
            assertThat(fixture.shells.take()).containsExactly(open(ToolWindowMode.FLOAT, moved), "show " + A);
            events.closing().run();
            events.moved().accept(new Rectangle(0, 0, 640, 480));
            assertThat(fixture.owner.dock().visible(A)).as("the closed window's events are inert").isTrue();
            assertThat(fixture.owner.dock().bounds(A)).hasValue(moved);
        });
    }

    @Test void moveToOnADetachedToolWindowDocksItThere() throws Exception {
        edt(() -> {
            var fixture = new Fixture();
            fixture.floated();
            fixture.choose(fixture.detachedMenu(), "Right Top");
            assertThat(fixture.shells.take()).containsExactly("dispose " + A);
            assertThat(fixture.owner.dock().mode(A)).isEqualTo(ToolWindowMode.DOCKED);
            assertThat(fixture.owner.dock().slot(A)).hasValue(Slot.RIGHT_TOP);
            assertThat(fixture.owner.area().content(Slot.RIGHT_TOP)).isSameAs(fixture.view);
        });
    }

    @Test void aDetachAtOtherBoundsMovesItsOpenWindow() throws Exception {
        edt(() -> {
            var fixture = new Fixture();
            fixture.floated();
            var elsewhere = new Rectangle(700, 300, 420, 360);
            fixture.owner.dock().detach(A, ToolWindowMode.FLOAT, elsewhere);
            assertThat(fixture.shells.take()).containsExactly("place " + A + " " + text(elsewhere));
        });
    }

    @Test void activatingRaisesItsWindowAndActiveAsksTheShell() throws Exception {
        edt(() -> {
            var fixture = new Fixture();
            assertThat(fixture.windows().active(A)).isFalse();
            fixture.floated();
            assertThat(fixture.windows().active(A)).isFalse();
            fixture.shells.active = true;
            assertThat(fixture.windows().active(A)).isTrue();
            fixture.windows().activate(A);
            assertThat(fixture.shells.take()).containsExactly("toFront " + A);
        });
    }

    @Test void aKitPanelsReplacedHeaderReachesItsOpenWindow() throws Exception {
        edt(() -> {
            var theme = ThemeTestSupport.generated(true);
            try (var bindings = new AppearanceBindings(new ThemePair(theme, theme), failure -> { throw new AssertionError(failure); }, () -> false);
                 var view = new KitView(bindings.context(ThemeRoute.CHROME), spec("panel", "header"), event -> { })) {
                var fixture = new Fixture(UiState.inMemory(), view, true);
                fixture.toggle();
                fixture.choose(fixture.stripeMenu(), "Float");
                List<String> opened = fixture.shells.take();
                assertThat(opened).hasSize(2);
                assertThat(opened.getFirst()).startsWith("open " + A + " FLOAT");
                JComponent first = fixture.shells.openings.get(A).frame();

                view.update(spec("replacement", "replacement-header"));
                JComponent replaced = fixture.owner.area().detachedFrame(A);
                assertThat(replaced).as("the area remounted the frame").isNotNull().isNotSameAs(first);
                assertThat(fixture.shells.take()).containsExactly("content " + A);
                assertThat(fixture.shells.contents.get(A)).isSameAs(replaced);
                fixture.windows().sync();
                assertThat(fixture.shells.take()).as("the window holds the frame now").isEmpty();
            }
        });
    }

    @Test void modeAndBoundsAreSavedAndRestored() throws Exception {
        edt(() -> {
            Path file = dir.resolve("ui-state.toml");
            var fixture = new Fixture(UiState.load(file));
            Rectangle bounds = fixture.floated();
            var saved = new UiState.Panel(Slot.LEFT_TOP, 0, true, ToolWindowMode.FLOAT,
                Optional.of(new UiState.Bounds(bounds.x, bounds.y, bounds.width, bounds.height)));
            assertThat(UiState.load(file).panel(A)).hasValue(saved);
            fixture.owner.close();
            assertThat(fixture.shells.take()).containsExactly("dispose " + A, "closed " + A);

            WindowContent second = window(fixture.model, UiState.load(file));
            boolean[] showing = {false};
            second.detachedWindows().ready = () -> showing[0];
            var shells = new Shells();
            second.detachedShells(shells);
            assertThat(shells.take()).as("nothing opens before the main window shows").isEmpty();
            assertThat(second.dock().mode(A)).isEqualTo(ToolWindowMode.FLOAT);
            assertThat(second.dock().visible(A)).isTrue();
            assertThat(second.dock().bounds(A)).hasValue(bounds);

            showing[0] = true;
            second.dispatchEvent(new HierarchyEvent(second, HierarchyEvent.HIERARCHY_CHANGED, second, second.getParent(),
                HierarchyEvent.SHOWING_CHANGED));
            assertThat(shells.take()).as("the main window showed").containsExactly(open(ToolWindowMode.FLOAT, bounds), "show " + A);
        });
    }

    @Test void restoredBoundsOnAGoneDisplayLandOnAConnectedOne() throws Exception {
        edt(() -> {
            var state = UiState.inMemory();
            state.putPanel(A, new UiState.Panel(Slot.LEFT_TOP, 0, true, ToolWindowMode.FLOAT,
                Optional.of(new UiState.Bounds(-1500, 100, 400, 300))));
            var fixture = new Fixture(state);
            var landed = new Rectangle(0, 100, 400, 300);
            assertThat(fixture.shells.take()).containsExactly(open(ToolWindowMode.FLOAT, landed), "show " + A);
            assertThat(fixture.owner.dock().bounds(A)).hasValue(landed);
        });
    }

    @Test void aRemovedPanelsWindowCloses() throws Exception {
        edt(() -> {
            var fixture = new Fixture();
            fixture.floated();
            var moved = new Rectangle(320, 240, 480, 360);
            fixture.shells.events.get(A).moved().accept(moved);
            fixture.entry.close();
            assertThat(fixture.shells.take()).containsExactly("dispose " + A, "closed " + A);
            assertThat(fixture.state.panel(A)).as("kept whole for the plugin's next start, its last bounds included")
                .hasValue(new UiState.Panel(Slot.LEFT_TOP, 0, true, ToolWindowMode.FLOAT, Optional.of(new UiState.Bounds(320, 240, 480, 360))));
            assertThat(fixture.windows().showing(A)).isFalse();
        });
    }

    @Test void anAbsentPanelKeepsItsSavedModeAndBounds() throws Exception {
        edt(() -> {
            var state = UiState.inMemory();
            var gone = new UiState.Panel(Slot.LEFT_TOP, 0, true, ToolWindowMode.WINDOW, Optional.of(new UiState.Bounds(10, 20, 300, 200)));
            state.putPanel("dev.x.gone", gone);
            var fixture = new Fixture(state);
            fixture.toggle();
            assertThat(state.panel("dev.x.gone")).as("a panel not loaded keeps its record whole").hasValue(gone);
            assertThat(state.panel(A)).hasValue(new UiState.Panel(Slot.LEFT_TOP, 1, true));
        });
    }

    @Test void closingTheMainWindowClosesDetachedWindowsAndSavesThemShown() throws Exception {
        edt(() -> {
            var fixture = new Fixture();
            Rectangle bounds = fixture.floated();
            fixture.owner.close();
            assertThat(fixture.shells.take()).as("the window closes before the panel hears it").containsExactly("dispose " + A, "closed " + A);
            assertThat(fixture.state.panel(A)).hasValue(new UiState.Panel(Slot.LEFT_TOP, 0, true, ToolWindowMode.FLOAT,
                Optional.of(new UiState.Bounds(bounds.x, bounds.y, bounds.width, bounds.height))));
        });
    }

    @Test void withoutShellsNothingOpensUntilTheyAreGiven() throws Exception {
        edt(() -> {
            var fixture = new Fixture(UiState.inMemory(), new JLabel("alpha"), false);
            fixture.toggle();
            Rectangle docked = fixture.owner.area().frameOnScreen(A).orElseThrow();
            fixture.choose(fixture.owner.area().headerOf(Slot.LEFT_TOP).menu(), "Float");
            assertThat(fixture.windows().showing(A)).isFalse();
            assertThat(fixture.owner.area().detachedFrame(A)).as("its frame waits for a window").isNotNull();
            fixture.owner.detachedShells(fixture.shells);
            assertThat(fixture.shells.take()).containsExactly(open(ToolWindowMode.FLOAT, docked), "show " + A);
        });
    }

    @Test void shellsGivenBeforeContributionsConnectAreUsedAndAChildWindowHasNone() throws Exception {
        edt(() -> {
            var shells = new Shells();
            WindowContent owner = DesktopTestSupport.content(DesktopTestSupport.launcher(new ArrayDeque<>()));
            owner.detachedShells(shells);
            owner.connectContributions(new Contributions(), UiState.inMemory());
            assertThat(owner.detachedWindows().shells).isSameAs(shells);

            WindowContent child = DesktopTestSupport.content(DesktopTestSupport.launcher(new ArrayDeque<>()), WindowRole.CHILD,
                DesktopTestSupport.HOME, false, () -> { });
            child.connectContributions(new Contributions(), UiState.inMemory());
            child.detachedShells(shells);
            assertThat(child.detachedWindows()).isNull();
        });
    }

    private static ViewSpec spec(String panel, String header) {
        return new ViewSpec(new Nodes.Panel(panel, ControlState.READY,
            new Nodes.Header(header, ControlState.READY, "Alpha", OptionalInt.empty(), Optional.empty(), Optional.empty(), List.of()),
            Optional.empty(), new Nodes.Text("body", ControlState.READY, "Contents", Nodes.TextRole.BODY), Optional.empty()));
    }
}
