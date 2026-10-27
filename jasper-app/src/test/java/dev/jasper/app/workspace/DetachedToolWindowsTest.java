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
import java.util.function.Consumer;
import javax.swing.AbstractButton;
import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static dev.jasper.app.workspace.DesktopTestSupport.edt;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Floating tool windows plan 1 Tasks 5 and 6: a detached tool window's own window opens, moves, reopens and closes as the
 * main window's dock says, its mode, bounds and visibility are saved and restored, and its toggles, Zen and a dock change
 * made in the middle of a sync treat it as a window of its own. The main window stands in a stand-in frame on a pretend
 * screen with one 1920 by 1080 display; the native windows are recording shells.
 */
class DetachedToolWindowsTest {
    private static final String A = "dev.x.alpha";
    private static final Rectangle SCREEN = new Rectangle(0, 0, 1920, 1080);
    /** The stand-in main window on the pretend screen. */
    private static final Rectangle MAIN = new Rectangle(100, 50, 1200, 800);

    @TempDir Path dir;

    @AfterEach void closeWindows() throws Exception { DesktopTestSupport.closeOwners(); }

    /** Native windows as text: each opening, and every call on each window's shell. {@link DockDragDetachTest} uses them too. */
    static final class Shells implements BiFunction<DetachedToolWindows.Opening, DetachedToolWindows.Events, DetachedToolWindows.Shell> {
        final List<String> log = new ArrayList<>();
        final Map<String, DetachedToolWindows.Opening> openings = new HashMap<>();
        final Map<String, DetachedToolWindows.Events> events = new HashMap<>();
        final Map<String, JComponent> contents = new HashMap<>();
        boolean active;
        /**
         * Run, with the tool window's id, inside the shell's making and its show, content and dispose functions: a dock
         * change in the middle of a sync, or a failure of the native window.
         */
        Consumer<String> duringApply = id -> { }, duringShow = id -> { }, duringContent = id -> { }, duringDispose = id -> { };

        @Override public DetachedToolWindows.Shell apply(DetachedToolWindows.Opening opening, DetachedToolWindows.Events heard) {
            String id = opening.id();
            log.add("open " + id + " " + opening.mode() + " " + text(opening.bounds()));
            openings.put(id, opening);
            events.put(id, heard);
            duringApply.accept(id);
            return new DetachedToolWindows.Shell(() -> { log.add("show " + id); duringShow.accept(id); }, () -> log.add("toFront " + id),
                () -> { log.add("dispose " + id); duringDispose.accept(id); }, () -> active, bounds -> log.add("place " + id + " " + text(bounds)),
                frame -> { contents.put(id, frame); log.add("content " + id); duringContent.accept(id); });
        }

        /** What was recorded since the last call. */
        List<String> take() {
            var taken = List.copyOf(log);
            log.clear();
            return taken;
        }
    }

    static String text(Rectangle bounds) { return bounds.x + "," + bounds.y + " " + bounds.width + "x" + bounds.height; }

    private static String open(ToolWindowMode mode, Rectangle bounds) { return "open " + A + " " + mode + " " + text(bounds); }

    /**
     * A main window connected to {@code model} and {@code state} in a stand-in frame at {@link #MAIN} on the pretend
     * screen, ready to open windows on {@link #SCREEN}; no shells yet.
     */
    private static WindowContent window(Contributions model, UiState state) { return window(model, state, false); }

    /** As above, under flush frames (Enterprise's classic dock) when {@code flush}, else the default look's standard ones. */
    private static WindowContent window(Contributions model, UiState state, boolean flush) {
        WindowContent owner = flush
            ? DesktopTestSupport.content(DesktopTestSupport.launcher(new ArrayDeque<>()), ThemeTestSupport.controller(true), DockChromeTest.classicDock())
            : DesktopTestSupport.content(DesktopTestSupport.launcher(new ArrayDeque<>()));
        owner.connectContributions(model, state);
        TabDragTestSupport.frame(owner, MAIN.x, MAIN.y, MAIN.width, MAIN.height);
        DetachedToolWindows windows = owner.detachedWindows();
        windows.ready = () -> true;
        windows.displays = () -> List.of(new WindowPlacement.Display(SCREEN, SCREEN));
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

        Fixture(UiState state, JComponent view, boolean withShells) { this(state, view, withShells, false); }

        Fixture(UiState state, JComponent view, boolean withShells, boolean flush) {
            this.state = state;
            this.view = view;
            owner = window(model, state, flush);
            entry = model.addPanel(A, "Alpha", new ImageIcon(), Slot.LEFT_TOP, site -> {
                site.onClosed(() -> shells.log.add("closed " + A));
                return view;
            });
            relayout(owner);
            if (withShells) owner.detachedShells(shells);
        }

        DetachedToolWindows windows() { return owner.detachedWindows(); }

        void toggle() { request(A, Contributions.PanelRequest.Op.TOGGLE); }

        void request(String id, Contributions.PanelRequest.Op op) {
            model.requestPanel(new Contributions.PanelRequest(owner.id(), id, op));
            relayout(owner);
        }

        /** Adds a panel of its own, hidden and docked in {@code slot}. */
        void addPanel(String id, String title, Slot slot) {
            model.addPanel(id, title, new ImageIcon(), slot, site -> new JLabel(title));
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

        /** Shows the panel docked, then floats it from its header: its window opens where its frame was, and is raised. */
        Rectangle floated() {
            toggle();
            Rectangle docked = owner.area().frameOnScreen(A).orElseThrow();
            choose(owner.area().headerOf(Slot.LEFT_TOP).menu(), "Float");
            assertThat(shells.take()).containsExactly(open(ToolWindowMode.FLOAT, docked), "show " + A, "toFront " + A);
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
            assertThat(fixture.shells.take()).containsExactly(open(ToolWindowMode.FLOAT, docked), "show " + A, "toFront " + A);
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
            assertThat(fixture.shells.take()).containsExactly("dispose " + A, open(ToolWindowMode.WINDOW, bounds), "show " + A, "toFront " + A);
            assertThat(fixture.owner.dock().mode(A)).isEqualTo(ToolWindowMode.WINDOW);
            assertThat(fixture.owner.dock().visible(A)).isTrue();
        });
    }

    /**
     * Fix round 2: View Mode \u203a Float or Window chosen from a menu (the header's, the stripe button's) is a user gesture
     * that ends with the shown tool window's own window raised and the keyboard in its frame, as IntelliJ keeps a tool
     * window active through a view-mode change; switching between Float and Window does too.
     */
    @Test void viewModeFloatOrWindowFromAMenuFocusesTheWindow() throws Exception {
        edt(() -> {
            var list = new JList<>(new String[] {"Mac"});
            var fixture = new Fixture(UiState.inMemory(), list, true);
            var focused = new ArrayList<Component>();
            fixture.owner.area().focuser = focused::add;
            fixture.toggle();
            Rectangle docked = fixture.owner.area().frameOnScreen(A).orElseThrow();
            PressedFrameTest.flushEdt();
            focused.clear();

            fixture.choose(fixture.owner.area().headerOf(Slot.LEFT_TOP).menu(), "Float");
            assertThat(fixture.shells.take()).as("opened, shown, then raised")
                .containsExactly(open(ToolWindowMode.FLOAT, docked), "show " + A, "toFront " + A);
            PressedFrameTest.flushEdt();
            assertThat(focused).as("the keyboard goes into its frame").containsExactly(list);

            focused.clear();
            fixture.choose(fixture.detachedMenu(), "Window");
            assertThat(fixture.shells.take()).containsExactly("dispose " + A, open(ToolWindowMode.WINDOW, docked), "show " + A, "toFront " + A);
            PressedFrameTest.flushEdt();
            assertThat(focused).containsExactly(list);

            focused.clear();
            fixture.choose(fixture.stripeMenu(), "Float");
            assertThat(fixture.shells.take()).as("from the stripe button's menu too")
                .containsExactly("dispose " + A, open(ToolWindowMode.FLOAT, docked), "show " + A, "toFront " + A);
            PressedFrameTest.flushEdt();
            assertThat(focused).containsExactly(list);
        });
    }

    /** Fix round 2: a hidden tool window floated from its stripe button's menu stays hidden: nothing opens or is activated. */
    @Test void viewModeFloatOnAHiddenToolWindowOpensAndActivatesNothing() throws Exception {
        edt(() -> {
            var list = new JList<>(new String[] {"Mac"});
            var fixture = new Fixture(UiState.inMemory(), list, true);
            var focused = new ArrayList<Component>();
            fixture.owner.area().focuser = focused::add;
            fixture.choose(fixture.stripeMenu(), "Float");
            assertThat(fixture.shells.take()).isEmpty();
            PressedFrameTest.flushEdt();
            assertThat(focused).isEmpty();
            assertThat(fixture.owner.dock().mode(A)).isEqualTo(ToolWindowMode.FLOAT);
            assertThat(fixture.owner.dock().visible(A)).isFalse();
        });
    }

    /** Fix round 2: a detached tool window restored from the saved state opens and shows, and is neither raised nor focused. */
    @Test void aRestoredDetachedToolWindowOpensWithoutBeingActivated() throws Exception {
        edt(() -> {
            var state = UiState.inMemory();
            var saved = new Rectangle(200, 150, 400, 300);
            state.putPanel(A, new UiState.Panel(Slot.LEFT_TOP, 0, true, ToolWindowMode.FLOAT,
                Optional.of(new UiState.Bounds(saved.x, saved.y, saved.width, saved.height))));
            var list = new JList<>(new String[] {"Mac"});
            var fixture = new Fixture(state, list, false);
            var focused = new ArrayList<Component>();
            fixture.owner.area().focuser = focused::add;
            fixture.owner.detachedShells(fixture.shells);
            assertThat(fixture.shells.take()).containsExactly(open(ToolWindowMode.FLOAT, saved), "show " + A);
            PressedFrameTest.flushEdt();
            assertThat(focused).isEmpty();
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
            assertThat(fixture.shells.take()).as("a stripe click shows its window and raises it")
                .containsExactly(open(ToolWindowMode.FLOAT, centred), "show " + A, "toFront " + A);
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
            assertThat(fixture.shells.take()).containsExactly(open(ToolWindowMode.FLOAT, moved), "show " + A, "toFront " + A);
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
                assertThat(opened).hasSize(3);
                assertThat(opened.getFirst()).startsWith("open " + A + " FLOAT");
                assertThat(opened.subList(1, 3)).as("a menu's View Mode activates it").containsExactly("show " + A, "toFront " + A);
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
            assertThat(shells.take()).as("nothing opens inside the main window's own show").isEmpty();
            PressedFrameTest.flushEdt();
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

    /**
     * Final review: a native window that fails to be made does not break the dock change that opened it. The tool window
     * hides and stays detached, the change renders the stripes and saves it hidden, so neither the next dock change nor the
     * next start tries again; a click on its stripe tries once more, and fails the same way.
     */
    @Test void aWindowThatFailsToBeMadeHidesItsToolWindowAndTheDockChangeCompletes() throws Exception {
        edt(() -> {
            Path file = dir.resolve("ui-state.toml");
            var fixture = new Fixture(UiState.load(file));
            String bravo = "dev.x.bravo";
            fixture.addPanel(bravo, "Bravo", Slot.RIGHT_TOP);
            fixture.toggle();
            Rectangle docked = fixture.owner.area().frameOnScreen(A).orElseThrow();
            fixture.shells.duringApply = id -> { throw new IllegalStateException("No native window"); };

            fixture.choose(fixture.owner.area().headerOf(Slot.LEFT_TOP).menu(), "Float");
            assertThat(fixture.shells.take()).containsExactly(open(ToolWindowMode.FLOAT, docked));
            assertHiddenAfterFailing(fixture, file, docked);

            fixture.request(bravo, Contributions.PanelRequest.Op.SHOW);
            assertThat(fixture.shells.take()).as("the next dock change does not try again").isEmpty();
            assertThat(fixture.owner.area().content(Slot.RIGHT_TOP)).isNotNull();

            fixture.button().doClick();
            assertThat(fixture.shells.take()).as("a click tries again").containsExactly(open(ToolWindowMode.FLOAT, docked));
            assertHiddenAfterFailing(fixture, file, docked);
        });
    }

    /** Final review: a native window that fails to show is disposed, and its tool window hides as when it fails to be made. */
    @Test void aWindowThatFailsToShowIsDisposedAndItsToolWindowHides() throws Exception {
        edt(() -> {
            Path file = dir.resolve("ui-state.toml");
            var fixture = new Fixture(UiState.load(file));
            fixture.toggle();
            Rectangle docked = fixture.owner.area().frameOnScreen(A).orElseThrow();
            fixture.shells.duringShow = id -> { throw new IllegalStateException("Not shown"); };

            fixture.choose(fixture.owner.area().headerOf(Slot.LEFT_TOP).menu(), "Float");
            assertThat(fixture.shells.take()).containsExactly(open(ToolWindowMode.FLOAT, docked), "show " + A, "dispose " + A);
            assertHiddenAfterFailing(fixture, file, docked);
        });
    }

    /** Final review: a window restored at start-up that fails to open is saved hidden, so the next start opens nothing. */
    @Test void aRestoredWindowThatFailsToOpenIsSavedHidden() throws Exception {
        edt(() -> {
            Path file = dir.resolve("ui-state.toml");
            var saved = new Rectangle(200, 150, 400, 300);
            var state = UiState.load(file);
            state.putPanel(A, new UiState.Panel(Slot.LEFT_TOP, 0, true, ToolWindowMode.FLOAT,
                Optional.of(new UiState.Bounds(saved.x, saved.y, saved.width, saved.height))));
            state.save();
            var fixture = new Fixture(UiState.load(file), new JLabel("alpha"), false);
            fixture.shells.duringApply = id -> { throw new IllegalStateException("No native window"); };

            fixture.owner.detachedShells(fixture.shells);
            assertThat(fixture.shells.take()).containsExactly(open(ToolWindowMode.FLOAT, saved));
            assertHiddenAfterFailing(fixture, file, saved);
        });
    }

    /** The tool window whose own window failed to open: hidden, still floating, its stripe button unselected, and saved so. */
    private static void assertHiddenAfterFailing(Fixture fixture, Path file, Rectangle bounds) {
        assertThat(fixture.owner.dock().visible(A)).isFalse();
        assertThat(fixture.owner.dock().mode(A)).as("still detached").isEqualTo(ToolWindowMode.FLOAT);
        assertThat(fixture.windows().showing(A)).isFalse();
        assertThat(fixture.button().isSelected()).as("the stripes rendered").isFalse();
        assertThat(UiState.load(file).panel(A)).as("saved hidden").hasValue(new UiState.Panel(Slot.LEFT_TOP, 0, false,
            ToolWindowMode.FLOAT, Optional.of(new UiState.Bounds(bounds.x, bounds.y, bounds.width, bounds.height))));
    }

    /** Final review: a window that fails to dispose still goes, and the dock change that hid its tool window completes. */
    @Test void aWindowThatFailsToDisposeStillHidesItsToolWindow() throws Exception {
        edt(() -> {
            Path file = dir.resolve("ui-state.toml");
            var fixture = new Fixture(UiState.load(file));
            Rectangle bounds = fixture.floated();
            fixture.shells.duringDispose = id -> { throw new IllegalStateException("Not disposed"); };

            fixture.button().doClick();
            assertThat(fixture.shells.take()).containsExactly("dispose " + A);
            assertHiddenAfterFailing(fixture, file, bounds);
        });
    }

    /** Final review: closing the main window disposes every detached window when one fails to dispose, and the main window closes. */
    @Test void closingTheMainWindowDisposesEveryWindowWhenOneFails() throws Exception {
        edt(() -> {
            var fixture = new Fixture();
            String bravo = "dev.x.bravo";
            fixture.addPanel(bravo, "Bravo", Slot.RIGHT_TOP);
            fixture.floated();
            fixture.request(bravo, Contributions.PanelRequest.Op.SHOW);
            fixture.choose(fixture.owner.area().headerOf(Slot.RIGHT_TOP).menu(), "Float");
            assertThat(fixture.windows().showing(bravo)).isTrue();
            fixture.shells.take();
            fixture.shells.duringDispose = id -> { if (id.equals(A)) throw new IllegalStateException("Not disposed"); };

            fixture.owner.close();
            assertThat(fixture.shells.take()).containsExactly("dispose " + A, "dispose " + bravo, "closed " + A);
            assertThat(fixture.owner.closed()).isTrue();
        });
    }

    /**
     * Final review: under flush frames View Mode \u203a Docked from the stripe button's menu docks a floating tool window
     * back in place of the window its slot shows and leaves it the active tool window: the window it replaces hides without
     * handing the focus back to the terminal, whose focus would undo the activation.
     */
    @Test void underFlushFramesViewModeDockedInPlaceOfAnotherWindowKeepsItActive() throws Exception {
        try {
            edt(() -> {
                var fixture = new Fixture(UiState.inMemory(), new JLabel("alpha"), true, true);
                String charlie = "dev.x.charlie";
                fixture.addPanel(charlie, "Charlie", Slot.LEFT_TOP);
                var events = new ArrayList<String>();
                fixture.model.tabs().open("dev.x", "doc", "Doc", fixture.owner.id(), new JPanel()).focusTarget(() -> events.add("focus"));
                fixture.floated();
                fixture.request(charlie, Contributions.PanelRequest.Op.SHOW);
                assertThat(fixture.owner.area().content(Slot.LEFT_TOP)).isNotNull().isNotSameAs(fixture.view);
                events.clear();

                fixture.choose(fixture.stripeMenu(), "Docked");
                assertThat(fixture.shells.take()).containsExactly("dispose " + A);
                assertThat(fixture.owner.area().content(Slot.LEFT_TOP)).isSameAs(fixture.view);
                assertThat(fixture.owner.dock().visible(charlie)).isFalse();
                assertThat(events).as("no hand-back to the terminal").isEmpty();
                assertThat(fixture.owner.area().focusedSlot()).as("its frame is the active one").hasValue(Slot.LEFT_TOP);
            });
        } finally { edt(() -> ThemeTestSupport.controller(true)); }
    }

    /** A label that counts its look-and-feel updates. */
    private static final class CountedLabel extends JLabel {
        int updates;

        CountedLabel(String text) { super(text); }

        @Override public void updateUI() { super.updateUI(); updates++; }
    }

    /**
     * Task 8: a theme change reaches a shown detached tool window whose own window has not opened (no shells yet, or the
     * main window not showing), which is in no window's component tree; a docked one that shows is updated with its window,
     * once.
     */
    @Test void aShownDetachedToolWindowWithoutItsWindowFollowsTheTheme() throws Exception {
        edt(() -> {
            var view = new CountedLabel("alpha");
            var fixture = new Fixture(UiState.inMemory(), view, false);
            fixture.toggle();
            boolean dark = fixture.owner.theme().chrome().dark();
            int before = view.updates;
            fixture.owner.selectVariant(!dark);
            assertThat(view.updates).as("docked and shown, it is updated with its window, once").isEqualTo(before + 1);

            fixture.choose(fixture.owner.area().headerOf(Slot.LEFT_TOP).menu(), "Float");
            assertThat(fixture.windows().showing(A)).isFalse();
            before = view.updates;
            fixture.owner.selectVariant(dark);
            assertThat(view.updates).as("shown detached, with no window of its own yet").isEqualTo(before + 1);
        });
    }

    @Test void shellsGivenBeforeContributionsConnectAreUsedAndEveryWindowHasItsOwn() throws Exception {
        edt(() -> {
            var shells = new Shells();
            WindowContent owner = DesktopTestSupport.content(DesktopTestSupport.launcher(new ArrayDeque<>()));
            owner.detachedShells(shells);
            owner.connectContributions(new Contributions(), UiState.inMemory());
            assertThat(owner.detachedWindows().shells).isSameAs(shells);

            WindowContent child = DesktopTestSupport.content(DesktopTestSupport.launcher(new ArrayDeque<>()),
                DesktopTestSupport.HOME, false);
            child.connectContributions(new Contributions(), UiState.inMemory());
            child.detachedShells(shells);
            assertThat(child.detachedWindows()).as("every window's tool windows are its own").isNotNull().isNotSameAs(owner.detachedWindows());
        });
    }

    /**
     * A stripe click on a detached tool window toggles it as it does a docked one: hidden, it shows and raises its window;
     * shown, it hides, whichever window is the active one (the click made the main window the active one, so the window never
     * is at that moment). Its button follows.
     */
    @Test void aStripeClickTogglesADetachedWindowAsItDoesADockedOne() throws Exception {
        edt(() -> {
            var list = new JList<>(new String[] {"Mac"});
            var fixture = new Fixture(UiState.inMemory(), list, true);
            var focused = new ArrayList<Component>();
            fixture.owner.area().focuser = focused::add;
            fixture.choose(fixture.stripeMenu(), "Float");
            assertThat(fixture.shells.take()).as("hidden: no window").isEmpty();

            fixture.button().doClick();
            List<String> shown = fixture.shells.take();
            assertThat(shown).hasSize(3);
            assertThat(shown.getFirst()).startsWith("open " + A + " FLOAT");
            assertThat(shown.subList(1, 3)).as("shown, then raised").containsExactly("show " + A, "toFront " + A);
            PressedFrameTest.flushEdt();
            assertThat(focused).as("the keyboard goes into its frame").containsExactly(list);
            assertThat(fixture.owner.dock().visible(A)).isTrue();
            assertThat(fixture.button().isSelected()).isTrue();

            assertThat(fixture.windows().active(A)).as("the click made the main window the active one").isFalse();
            fixture.button().doClick();
            assertThat(fixture.shells.take()).as("shown: hidden, not raised").containsExactly("dispose " + A);
            assertThat(fixture.owner.dock().visible(A)).isFalse();
            assertThat(fixture.button().isSelected()).isFalse();

            fixture.shells.active = true;
            fixture.button().doClick();
            assertThat(fixture.shells.take().getLast()).as("hidden again: shown and raised").isEqualTo("toFront " + A);
            fixture.button().doClick();
            assertThat(fixture.shells.take()).as("whichever window is the active one: hidden").containsExactly("dispose " + A);
            assertThat(fixture.owner.dock().visible(A)).isFalse();
        });
    }

    /**
     * The View menu's toggle, a shortcut or a plugin's own toggle action (an activating toggle) raises a shown window that is not
     * the active one, and hides the active one.
     */
    @Test void theViewMenusToggleFollowsTheSameRule() throws Exception {
        edt(() -> {
            var fixture = new Fixture();
            fixture.toggle();
            fixture.choose(fixture.owner.area().headerOf(Slot.LEFT_TOP).menu(), "Float");
            fixture.choose(fixture.detachedMenu(), "Hide");
            fixture.shells.take();
            assertThat(fixture.owner.dock().visible(A)).isFalse();

            fixture.request(A, Contributions.PanelRequest.Op.ACTIVATING_TOGGLE);
            List<String> shown = fixture.shells.take();
            assertThat(shown).hasSize(3);
            assertThat(shown.subList(1, 3)).containsExactly("show " + A, "toFront " + A);
            assertThat(fixture.owner.dock().visible(A)).isTrue();

            fixture.request(A, Contributions.PanelRequest.Op.ACTIVATING_TOGGLE);
            assertThat(fixture.shells.take()).containsExactly("toFront " + A);
            assertThat(fixture.owner.dock().visible(A)).isTrue();

            fixture.shells.active = true;
            fixture.request(A, Contributions.PanelRequest.Op.ACTIVATING_TOGGLE);
            assertThat(fixture.shells.take()).containsExactly("dispose " + A);
            assertThat(fixture.owner.dock().visible(A)).isFalse();
        });
    }

    /** A detached window's own window is the one to raise whatever the frame style: flush frames do not make the toggle a raise forever. */
    @Test void underFlushFramesTheActivatingToggleOfADetachedWindowFollowsTheSameRule() throws Exception {
        try {
            edt(() -> {
                var fixture = new Fixture(UiState.inMemory(), new JLabel("alpha"), true, true);
                assertThat(fixture.owner.area().flush()).isTrue();
                fixture.toggle();
                fixture.choose(fixture.owner.area().headerOf(Slot.LEFT_TOP).menu(), "Float");
                fixture.shells.take();

                fixture.request(A, Contributions.PanelRequest.Op.ACTIVATING_TOGGLE);
                assertThat(fixture.shells.take()).as("shown, not active: raised").containsExactly("toFront " + A);
                assertThat(fixture.owner.dock().visible(A)).isTrue();
                fixture.shells.active = true;
                fixture.request(A, Contributions.PanelRequest.Op.ACTIVATING_TOGGLE);
                assertThat(fixture.shells.take()).as("active: hidden").containsExactly("dispose " + A);
                fixture.request(A, Contributions.PanelRequest.Op.ACTIVATING_TOGGLE);
                assertThat(fixture.shells.take().getLast()).as("hidden: shown and raised").isEqualTo("toFront " + A);
                assertThat(fixture.owner.dock().visible(A)).isTrue();
                assertThat(fixture.owner.area().focusedSlot()).as("no docked frame is the active one").isEmpty();
            });
        } finally { edt(() -> ThemeTestSupport.controller(true)); }
    }

    /** An activating show (a child window's request, a plugin's own toggle after its first open) raises the window, and never hides it. */
    @Test void anActivatingShowRaisesTheWindowAndNeverHidesIt() throws Exception {
        edt(() -> {
            var fixture = new Fixture();
            fixture.floated();
            fixture.shells.active = true;
            fixture.request(A, Contributions.PanelRequest.Op.ACTIVATING_SHOW);
            assertThat(fixture.shells.take()).containsExactly("toFront " + A);
            assertThat(fixture.owner.dock().visible(A)).isTrue();
        });
    }

    /**
     * Hiding a detached tool window leaves the focus where it is (its window was one of its own); hiding a docked one hands it
     * back to the selected tab. The selected tab is hosted content, so handing focus back runs its focus target.
     */
    @Test void hidingADetachedWindowLeavesFocusAloneAndHidingADockedOneHandsItBack() throws Exception {
        edt(() -> {
            var fixture = new Fixture();
            var events = new ArrayList<String>();
            fixture.model.tabs().open("dev.x", "doc", "Doc", fixture.owner.id(), new JPanel()).focusTarget(() -> events.add("focus"));
            fixture.floated();
            events.clear();

            fixture.shells.events.get(A).closing().run();
            assertThat(fixture.owner.dock().visible(A)).isFalse();
            assertThat(events).as("its window closed").isEmpty();

            fixture.button().doClick();
            assertThat(fixture.owner.dock().visible(A)).isTrue();
            fixture.choose(fixture.detachedMenu(), "Hide");
            assertThat(fixture.owner.dock().visible(A)).isFalse();
            assertThat(events).as("its Hide item").isEmpty();

            fixture.choose(fixture.stripeMenu(), "Docked");
            fixture.toggle();
            events.clear();
            fixture.toggle();
            assertThat(fixture.owner.dock().visible(A)).isFalse();
            assertThat(events).as("a docked window hides: back to the tab").containsExactly("focus");
        });
    }

    /**
     * Full windows plan 1 Controller Ruling 10: a window opened while another is open starts out of Zen, and its tool
     * window saved showing in Window mode starts hidden, keeping its mode and bounds, so showing it opens its window
     * there; the first window still opens it, and the shared saved state keeps it showing and Zen as it was.
     */
    @Test void aWindowOpenedBesideAnotherStartsOutOfZenWithItsDetachedToolWindowsHidden() throws Exception {
        edt(() -> {
            var state = UiState.inMemory();
            var saved = new Rectangle(200, 150, 400, 300);
            var showing = new UiState.Panel(Slot.LEFT_TOP, 0, true, ToolWindowMode.WINDOW,
                Optional.of(new UiState.Bounds(saved.x, saved.y, saved.width, saved.height)));
            state.putPanel(A, showing);
            var first = new Fixture(state);
            assertThat(first.shells.take()).as("the first window opens it").containsExactly(open(ToolWindowMode.WINDOW, saved), "show " + A);

            WindowContent second = DesktopTestSupport.content(DesktopTestSupport.launcher(new ArrayDeque<>()));
            second.connectContributions(first.model, state, true, true);
            TabDragTestSupport.frame(second, MAIN.x + 30, MAIN.y + 30, MAIN.width, MAIN.height);
            second.detachedWindows().ready = () -> true;
            second.detachedWindows().displays = () -> List.of(new WindowPlacement.Display(SCREEN, SCREEN));
            var shells = new Shells();
            second.detachedShells(shells);
            relayout(second);
            PressedFrameTest.flushEdt();
            assertThat(shells.take()).as("no second window of it opens").isEmpty();
            assertThat(second.dock().visible(A)).isFalse();
            assertThat(second.dock().mode(A)).isEqualTo(ToolWindowMode.WINDOW);
            assertThat(second.dock().bounds(A)).hasValue(saved);
            assertThat(state.panel(A)).as("still saved as showing").hasValue(showing);

            first.model.requestPanel(new Contributions.PanelRequest(second.id(), A, Contributions.PanelRequest.Op.SHOW));
            assertThat(shells.take()).as("showing it opens its window").startsWith(open(ToolWindowMode.WINDOW, saved), "show " + A);
            second.close();
        });
    }

    /** Controller Ruling 10: a window opened beside another starts out of Zen, and leaves the saved Zen mode alone. */
    @Test void aWindowOpenedBesideAnotherStartsOutOfZen() throws Exception {
        edt(() -> {
            var state = UiState.inMemory();
            state.setZenMode(true);
            var first = new Fixture(state);
            assertThat(first.owner.zenMode()).as("the first window restores Zen").isTrue();

            WindowContent second = DesktopTestSupport.content(DesktopTestSupport.launcher(new ArrayDeque<>()));
            second.connectContributions(first.model, state, true, true);
            assertThat(second.zenMode()).isFalse();
            first.model.requestPanel(new Contributions.PanelRequest(second.id(), A, Contributions.PanelRequest.Op.SHOW));
            assertThat(second.dock().visible(A)).isTrue();
            assertThat(state.zenMode()).as("its own dock changes keep the saved Zen mode").isTrue();
            second.toggleZenMode();
            second.toggleZenMode();
            assertThat(state.zenMode()).as("until its own Zen changes").isFalse();
            second.close();
        });
    }

    /** Zen hides the detached windows' own windows with the docked ones and brings them back; they stay saved as showing. */
    @Test void zenHidesDetachedWindowsAndBringsThemBack() throws Exception {
        edt(() -> {
            var fixture = new Fixture();
            Rectangle bounds = fixture.floated();
            var showing = new UiState.Panel(Slot.LEFT_TOP, 0, true, ToolWindowMode.FLOAT,
                Optional.of(new UiState.Bounds(bounds.x, bounds.y, bounds.width, bounds.height)));

            fixture.owner.toggleZenMode();
            assertThat(fixture.shells.take()).containsExactly("dispose " + A);
            assertThat(fixture.owner.dock().visible(A)).isFalse();
            assertThat(fixture.windows().showing(A)).isFalse();
            assertThat(fixture.state.panel(A)).as("Zen keeps it saved as showing").hasValue(showing);

            fixture.owner.toggleZenMode();
            assertThat(fixture.shells.take()).containsExactly(open(ToolWindowMode.FLOAT, bounds), "show " + A);
            assertThat(fixture.owner.dock().visible(A)).isTrue();
            assertThat(fixture.state.panel(A)).hasValue(showing);
        });
    }

    @Test void zenBringsBackDockedAndDetachedWindowsAndClosesTheOnesOpenedInIt() throws Exception {
        edt(() -> {
            var fixture = new Fixture();
            String bravo = "dev.x.bravo", charlie = "dev.x.charlie", delta = "dev.x.delta";
            fixture.addPanel(bravo, "Bravo", Slot.RIGHT_TOP);
            fixture.addPanel(charlie, "Charlie", Slot.BOTTOM_LEFT);
            fixture.addPanel(delta, "Delta", Slot.LEFT_BOTTOM);
            Rectangle bounds = fixture.floated();
            fixture.request(bravo, Contributions.PanelRequest.Op.SHOW);
            var deltaBounds = new Rectangle(500, 200, 400, 300);
            fixture.owner.dock().setBounds(delta, deltaBounds);
            fixture.owner.dock().setMode(delta, ToolWindowMode.FLOAT);
            assertThat(fixture.owner.area().content(Slot.RIGHT_TOP)).isNotNull();
            assertThat(fixture.owner.dock().visible(delta)).as("detached while hidden").isFalse();

            fixture.owner.toggleZenMode();
            assertThat(fixture.shells.take()).containsExactly("dispose " + A);
            assertThat(fixture.owner.area().content(Slot.RIGHT_TOP)).as("the docked one hides too").isNull();
            fixture.request(charlie, Contributions.PanelRequest.Op.SHOW);
            fixture.request(delta, Contributions.PanelRequest.Op.SHOW);
            assertThat(fixture.shells.take()).as("a detached window opened in Zen shows")
                .containsExactly("open " + delta + " FLOAT " + text(deltaBounds), "show " + delta);

            fixture.owner.toggleZenMode();
            assertThat(fixture.owner.area().content(Slot.RIGHT_TOP)).as("the docked one is back").isNotNull();
            assertThat(fixture.owner.area().content(Slot.BOTTOM_LEFT)).as("one opened in Zen closes").isNull();
            assertThat(fixture.owner.dock().visible(delta)).as("the detached one opened in Zen closes").isFalse();
            assertThat(fixture.owner.dock().visible(A)).as("the detached one that showed before is back").isTrue();
            assertThat(fixture.shells.take()).containsExactly("dispose " + delta, open(ToolWindowMode.FLOAT, bounds), "show " + A);
        });
    }

    /** Move to on a detached tool window's own slot docks it at its own place there and shows it; the checked item is not inert for it. */
    @Test void moveToItsOwnSlotDocksADetachedWindowThereAndShowsIt() throws Exception {
        edt(() -> {
            var fixture = new Fixture();
            String charlie = "dev.x.charlie";
            fixture.addPanel(charlie, "Charlie", Slot.LEFT_TOP);
            fixture.floated();
            assertThat(fixture.owner.dock().ids(Slot.LEFT_TOP)).containsExactly(A, charlie);

            fixture.choose(fixture.stripeMenu(), "Left Top");
            assertThat(fixture.shells.take()).containsExactly("dispose " + A);
            assertThat(fixture.owner.dock().mode(A)).isEqualTo(ToolWindowMode.DOCKED);
            assertThat(fixture.owner.dock().slot(A)).hasValue(Slot.LEFT_TOP);
            assertThat(fixture.owner.dock().visible(A)).isTrue();
            assertThat(fixture.owner.dock().ids(Slot.LEFT_TOP)).as("at its own place").containsExactly(A, charlie);
            assertThat(fixture.owner.area().content(Slot.LEFT_TOP)).isSameAs(fixture.view);
        });
    }

    @Test void moveToItsOwnSlotFromTheDetachedHeaderOrWhileHiddenDocksAndShowsIt() throws Exception {
        edt(() -> {
            var fixture = new Fixture();
            fixture.floated();
            fixture.choose(fixture.detachedMenu(), "Left Top");
            assertThat(fixture.shells.take()).containsExactly("dispose " + A);
            assertThat(fixture.owner.area().content(Slot.LEFT_TOP)).isSameAs(fixture.view);

            fixture.choose(fixture.owner.area().headerOf(Slot.LEFT_TOP).menu(), "Float");
            fixture.choose(fixture.detachedMenu(), "Hide");
            fixture.shells.take();
            assertThat(fixture.owner.dock().visible(A)).isFalse();
            fixture.choose(fixture.stripeMenu(), "Left Top");
            assertThat(fixture.owner.dock().mode(A)).isEqualTo(ToolWindowMode.DOCKED);
            assertThat(fixture.owner.dock().visible(A)).as("a hidden detached window shows where it docks").isTrue();
            assertThat(fixture.owner.area().content(Slot.LEFT_TOP)).isSameAs(fixture.view);
            assertThat(fixture.shells.take()).isEmpty();

            fixture.choose(fixture.owner.area().headerOf(Slot.LEFT_TOP).menu(), "Left Top");
            assertThat(fixture.owner.dock().mode(A)).as("a docked window's own slot stays inert").isEqualTo(ToolWindowMode.DOCKED);
        });
    }

    /** A later window in the sync's list whose frame a dock change made in the middle of the sync took opens no window. */
    @Test void aWindowWhoseFrameWentWhileAnEarlierOneOpenedOpensNoWindow() throws Exception {
        edt(() -> {
            var fixture = new Fixture(UiState.inMemory(), new JLabel("alpha"), false);
            String bravo = "dev.x.bravo";
            fixture.addPanel(bravo, "Bravo", Slot.RIGHT_TOP);
            fixture.toggle();
            fixture.choose(fixture.owner.area().headerOf(Slot.LEFT_TOP).menu(), "Float");
            fixture.request(bravo, Contributions.PanelRequest.Op.SHOW);
            fixture.choose(fixture.owner.area().headerOf(Slot.RIGHT_TOP).menu(), "Float");
            assertThat(fixture.owner.dock().detachedVisible()).containsExactly(A, bravo);

            fixture.shells.duringShow = id -> { if (id.equals(A)) fixture.owner.dock().hide(bravo); };
            fixture.owner.detachedShells(fixture.shells);
            List<String> log = fixture.shells.take();
            assertThat(log).as("only Alpha's window").hasSize(2);
            assertThat(log.getFirst()).startsWith("open " + A);
            assertThat(fixture.windows().showing(A)).isTrue();
            assertThat(fixture.windows().showing(bravo)).isFalse();
            assertThat(fixture.owner.dock().visible(bravo)).isFalse();
        });
    }

    @Test void aWindowHiddenWhileItsShellIsBeingMadeIsDisposedAtOnce() throws Exception {
        edt(() -> {
            var fixture = new Fixture(UiState.inMemory(), new JLabel("alpha"), false);
            fixture.toggle();
            fixture.choose(fixture.owner.area().headerOf(Slot.LEFT_TOP).menu(), "Float");
            Rectangle bounds = fixture.owner.dock().bounds(A).orElseThrow();
            fixture.shells.duringApply = id -> fixture.owner.dock().hide(id);
            fixture.owner.detachedShells(fixture.shells);
            assertThat(fixture.shells.take()).as("the shell made for a tool window that is gone is never shown")
                .containsExactly(open(ToolWindowMode.FLOAT, bounds), "dispose " + A);
            assertThat(fixture.windows().showing(A)).isFalse();
            assertThat(fixture.owner.dock().visible(A)).isFalse();

            fixture.shells.duringApply = id -> { };
            fixture.button().doClick();
            assertThat(fixture.shells.take()).as("and it opens when shown again")
                .containsExactly(open(ToolWindowMode.FLOAT, bounds), "show " + A, "toFront " + A);
            assertThat(fixture.windows().showing(A)).isTrue();
        });
    }

    @Test void aWindowHiddenInsideItsShowIsDisposedOnce() throws Exception {
        edt(() -> {
            var fixture = new Fixture(UiState.inMemory(), new JLabel("alpha"), false);
            fixture.toggle();
            fixture.choose(fixture.owner.area().headerOf(Slot.LEFT_TOP).menu(), "Float");
            Rectangle bounds = fixture.owner.dock().bounds(A).orElseThrow();
            fixture.shells.duringShow = id -> fixture.owner.dock().hide(id);
            fixture.owner.detachedShells(fixture.shells);
            assertThat(fixture.shells.take()).containsExactly(open(ToolWindowMode.FLOAT, bounds), "show " + A, "dispose " + A);
            assertThat(fixture.windows().showing(A)).isFalse();
            assertThat(fixture.owner.dock().visible(A)).isFalse();
        });
    }

    /** A kit view's new header and a moved window reach the shell together; the first hiding the tool window leaves the second nothing to move. */
    @Test void aWindowHiddenWhileItsFrameIsReplacedIsNotPlacedAfterItsDisposal() throws Exception {
        edt(() -> {
            var theme = ThemeTestSupport.generated(true);
            try (var bindings = new AppearanceBindings(new ThemePair(theme, theme), failure -> { throw new AssertionError(failure); }, () -> false);
                 var view = new KitView(bindings.context(ThemeRoute.CHROME), spec("panel", "header"), event -> { })) {
                var fixture = new Fixture(UiState.inMemory(), view, true);
                fixture.toggle();
                fixture.choose(fixture.stripeMenu(), "Float");
                fixture.shells.take();
                fixture.owner.dock().setBounds(A, new Rectangle(700, 300, 420, 360));
                fixture.shells.duringContent = id -> fixture.owner.dock().hide(id);

                view.update(spec("replacement", "replacement-header"));
                assertThat(fixture.shells.take()).containsExactly("content " + A, "dispose " + A);
                assertThat(fixture.windows().showing(A)).isFalse();
                assertThat(fixture.owner.dock().visible(A)).isFalse();
            }
        });
    }

    private static ViewSpec spec(String panel, String header) {
        return new ViewSpec(new Nodes.Panel(panel, ControlState.READY,
            new Nodes.Header(header, ControlState.READY, "Alpha", OptionalInt.empty(), Optional.empty(), Optional.empty(), List.of()),
            Optional.empty(), new Nodes.Text("body", ControlState.READY, "Contents", Nodes.TextRole.BODY), Optional.empty()));
    }
}
