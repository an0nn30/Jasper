package dev.jasper.app.workspace;

import static dev.jasper.app.workspace.DesktopTestSupport.*;
import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.app.config.ConfigSnapshot;
import dev.jasper.app.config.TerminalConfig;
import dev.jasper.app.contributions.Contributions;
import dev.jasper.app.contributions.Tab;
import dev.jasper.app.contributions.TabRegistry;
import dev.jasper.app.layout.ParentTabId;
import dev.jasper.app.layout.Side;
import java.awt.AWTEvent;
import java.awt.Color;
import java.awt.Point;
import java.awt.Toolkit;
import java.awt.event.FocusEvent;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayDeque;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Tabs and panes spec 5 and Review Focus 3: in an active window, every pane of a split tab but the active one dims by
 * {@code terminal.dim_inactive_panes}, and a tab of one pane shows clear; an inactive window dims every terminal, as
 * before; a plugin's view dims under its pane's overlay while it is an inactive pane (workspace plan 8a Rulings 12 and
 * 13).
 */
class PaneDimTest {
    private static final float DIM = ConfigSnapshot.defaults().terminal().dimInactivePanes();
    private final ArrayDeque<Runnable> pending = new ArrayDeque<>();

    @AfterEach void cleanup() throws Exception { closeOwners(); }

    private void launch() { while (!pending.isEmpty()) pending.remove().run(); }

    /** A window of one tab whose shells have started: one terminal, and with {@code split} a second to its right. */
    private WindowContent window(boolean split) throws Exception {
        WindowContent[] owner = new WindowContent[1];
        edt(() -> { owner[0] = content(idleLauncher(pending)); if (split) owner[0].split(Side.RIGHT); });
        launch();
        edt(() -> { });
        return owner[0];
    }

    /** The dim a terminal item's view paints. */
    private static float dim(Tab tab) {
        try {
            var view = ((TerminalContent) tab.content()).pane().view();
            var field = view.getClass().getDeclaredField("inactiveDim");
            field.setAccessible(true);
            return (float) field.get(view);
        } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }

    /** The colour at the middle of {@code pane}, painted at 200 by 100. */
    private static Color middle(GroupView pane) {
        pane.setSize(200, 100);
        pane.doLayout();
        var image = new BufferedImage(200, 100, BufferedImage.TYPE_INT_ARGB);
        var g = image.createGraphics();
        try { pane.paint(g); } finally { g.dispose(); }
        return new Color(image.getRGB(100, 50), true);
    }

    /** {@link ConfigSnapshot#defaults()} with {@code terminal.dim_inactive_panes} at {@code amount}. */
    private static ConfigSnapshot dimmedBy(float amount) {
        TerminalConfig t = ConfigSnapshot.defaults().terminal();
        return ConfigSnapshot.defaults().toBuilder().terminal(new TerminalConfig(t.shell(), t.env(), t.scrollback(), t.optionAsMeta(),
            t.cursorShape(), t.cursorBlink(), amount, t.copyOnSelect(), t.bell(), t.onExit(), t.shellIntegration())).build();
    }

    @Test void aTabOfOnePaneDimsOnlyWhileItsWindowIsInactive() throws Exception {
        var owner = window(false);
        edt(() -> {
            Tab only = owner.selectedTab();
            assertThat(DIM).as("the configured default").isPositive();
            assertThat(dim(only)).as("one pane in an active window").isZero();
            owner.setActive(false);
            assertThat(dim(only)).as("an inactive window dims its terminals, as before (Rulings 13)").isEqualTo(DIM);
            owner.setActive(true);
            assertThat(dim(only)).isZero();
        });
    }

    @Test void aTabOfTwoPanesDimsAllButItsActivePaneAndTheDimFollowsAtOnce() throws Exception {
        var owner = window(true);
        edt(() -> {
            Tab left = owner.tabs().get(0), right = owner.tabs().get(1);
            assertThat(dim(right)).as("the active pane").isZero();
            assertThat(dim(left)).isEqualTo(DIM);
            owner.focusGroup(Side.LEFT);
            assertThat(dim(left)).as("Focus Pane Left").isZero();
            assertThat(dim(right)).isEqualTo(DIM);
            var view = ((TerminalContent) right.content()).pane().view();
            var gained = new FocusEvent(view, FocusEvent.FOCUS_GAINED);
            for (var listener : view.getFocusListeners()) listener.focusGained(gained);
            assertThat(dim(right)).as("a click into a pane").isZero();
            assertThat(dim(left)).isEqualTo(DIM);
            owner.setActive(false);
            assertThat(dim(right)).as("an inactive window dims its active pane's terminal too").isEqualTo(DIM);
            assertThat(dim(left)).isEqualTo(DIM);
            owner.setActive(true);
            assertThat(dim(right)).isZero();
            ParentTabId first = owner.activeParentTab();
            owner.newTab(HOME);
            owner.selectParentTab(first);
            assertThat(dim(right)).as("a tab switch shows the tab as it was left").isZero();
            assertThat(dim(left)).isEqualTo(DIM);
            owner.closeTab(right);
            assertThat(dim(left)).as("one pane again: nothing dims").isZero();
        });
    }

    @Test void aMaximizedPaneShowsClear() throws Exception {
        var owner = window(true);
        edt(() -> {
            Tab left = owner.tabs().get(0), right = owner.tabs().get(1);
            owner.toggleMaximize();
            assertThat(owner.workspace().model().maximized()).isPresent();
            assertThat(dim(right)).as("the maximized pane").isZero();
            owner.toggleMaximize();
            assertThat(dim(left)).isEqualTo(DIM);
        });
    }

    @Test void aPluginsViewAsTheInactivePaneDimsUnderAnOverlay() throws Exception {
        var owner = window(false);
        edt(() -> {
            var model = new Contributions();
            owner.connectContributions(model);
            Tab terminal = owner.selectedTab();
            var panel = new JPanel();
            panel.setOpaque(true);
            panel.setBackground(new Color(0xFF00FF));
            Tab view = model.tabs().open("dev.example.editor", "notes", "notes.txt", owner.id(), panel, TabRegistry.Placement.SPLIT_RIGHT);
            GroupView pane = owner.workspace().groupView(view.group().orElseThrow());
            assertThat(pane.dim()).as("the active pane").isZero();
            assertThat(dim(terminal)).isEqualTo(DIM);
            Color clear = middle(pane);
            owner.selectTab(terminal);
            assertThat(pane.dim()).as("the inactive plugin's view").isEqualTo(DIM);
            assertThat(dim(terminal)).isZero();
            assertThat(middle(pane)).as("the overlay over the view").isNotEqualTo(clear);
            owner.setActive(false);
            assertThat(pane.dim()).as("an inactive pane's view stays dimmed in an inactive window").isEqualTo(DIM);
            owner.setActive(true);
            owner.selectTab(view);
            assertThat(pane.dim()).isZero();
            assertThat(middle(pane)).as("clear again at once").isEqualTo(clear);
            owner.setActive(false);
            assertThat(pane.dim()).as("the active pane's view stays clear: only terminals dim with the window").isZero();
            assertThat(dim(terminal)).isEqualTo(DIM);
        });
    }

    /** Workspace plan 8a Rulings 21: a press on a pane makes it the active one, though its view takes no focus. */
    @Test void aPressOnAPaneMakesItTheActivePane() throws Exception {
        var owner = window(false);
        edt(() -> {
            var model = new Contributions();
            owner.connectContributions(model);
            Tab terminal = owner.selectedTab();
            var panel = new JPanel();
            Tab view = model.tabs().open("dev.example.editor", "notes", "notes.txt", owner.id(), panel, TabRegistry.Placement.SPLIT_RIGHT);
            GroupView pane = owner.workspace().groupView(view.group().orElseThrow());
            owner.selectTab(terminal);
            assertThat(pane.dim()).as("the inactive pane").isEqualTo(DIM);
            panel.dispatchEvent(new MouseEvent(panel, MouseEvent.MOUSE_PRESSED, System.currentTimeMillis(),
                InputEvent.BUTTON1_DOWN_MASK, 5, 5, 1, false, MouseEvent.BUTTON1));
            assertThat(owner.selectedTab()).as("the pressed pane's item").isSameAs(view);
            assertThat(pane.dim()).as("its pane is the active one").isZero();
            assertThat(dim(terminal)).isEqualTo(DIM);
        });
    }

    /** A window's terminal and, split to its right, a plugin's view with no listeners, laid out, the terminal active. */
    private record Plugged(Tab terminal, Tab view, JPanel panel, GroupView pane) {
        /** The middle of the view, in its pane's coordinates. */
        Point overView() { return SwingUtilities.convertPoint(panel, panel.getWidth() / 2, panel.getHeight() / 2, pane); }
    }

    private static Plugged plugged(WindowContent owner) {
        var model = new Contributions();
        owner.connectContributions(model);
        Tab terminal = owner.selectedTab();
        var panel = new JPanel();
        Tab view = model.tabs().open("dev.example.editor", "notes", "notes.txt", owner.id(), panel, TabRegistry.Placement.SPLIT_RIGHT);
        GroupView pane = owner.workspace().groupView(view.group().orElseThrow());
        owner.selectTab(terminal);
        owner.setSize(1000, 700);
        WindowTabsTest.layoutTree(owner);
        assertThat(panel.getWidth()).as("the view is laid out").isPositive();
        assertThat(pane.dim()).as("the inactive pane").isEqualTo(DIM);
        return new Plugged(terminal, view, panel, pane);
    }

    /** A press of {@code button} on {@code source} at {@code at}. */
    private static MouseEvent press(java.awt.Component source, Point at, int button) {
        int modifiers = button == MouseEvent.BUTTON1 ? InputEvent.BUTTON1_DOWN_MASK : InputEvent.BUTTON3_DOWN_MASK;
        return new MouseEvent(source, MouseEvent.MOUSE_PRESSED, System.currentTimeMillis(), modifiers, at.x, at.y, 1,
            button == MouseEvent.BUTTON3, button);
    }

    /** The listeners for every mouse event on the toolkit. */
    private static int mouseListeners() {
        return Toolkit.getDefaultToolkit().getAWTEventListeners(AWTEvent.MOUSE_EVENT_MASK).length;
    }

    /**
     * A press on a view's bare area: in a window AWT hands it to the deepest component with mouse listeners, or else to
     * the frame, so it comes from an ancestor of the view; where it lands still finds the pane.
     */
    @Test void aPressOverAViewFromAnAncestorMakesItsPaneActive() throws Exception {
        var owner = window(false);
        edt(() -> {
            var plugged = plugged(owner);
            GroupView pane = plugged.pane();
            assertThat(owner.tabHolding(pane)).as("the press's source holds no item").isNull();
            pane.dispatchEvent(press(pane, plugged.overView(), MouseEvent.BUTTON1));
            assertThat(owner.selectedTab()).as("the item under the press").isSameAs(plugged.view());
            assertThat(pane.dim()).as("its pane is the active one").isZero();
            assertThat(dim(plugged.terminal())).isEqualTo(DIM);
        });
    }

    /** Only a left press makes a pane active: a right press leaves the active pane as it was. */
    @Test void aRightPressOverAnInactivePaneSelectsNothing() throws Exception {
        var owner = window(false);
        edt(() -> {
            var plugged = plugged(owner);
            plugged.pane().dispatchEvent(press(plugged.pane(), plugged.overView(), MouseEvent.BUTTON3));
            assertThat(owner.selectedTab()).as("the active pane's item").isSameAs(plugged.terminal());
            assertThat(plugged.pane().dim()).as("still the inactive pane").isEqualTo(DIM);
        });
    }

    /** Every window hears every press; only the window holding the place it lands on acts. */
    @Test void aPressInAnotherWindowsViewSelectsNothingInThisWindow() throws Exception {
        var owner = window(false);
        var other = window(false);
        edt(() -> {
            var here = plugged(owner);
            var there = plugged(other);
            there.pane().dispatchEvent(press(there.pane(), there.overView(), MouseEvent.BUTTON1));
            assertThat(other.selectedTab()).as("the other window's pressed pane").isSameAs(there.view());
            assertThat(owner.selectedTab()).as("this window's active pane").isSameAs(here.terminal());
            assertThat(here.pane().dim()).as("this window's inactive pane").isEqualTo(DIM);
        });
    }

    /** Swing's look and feel adds a mouse listener of its own the first time a window is built, and keeps it. */
    @Test void aClosedWindowLeavesNoMouseListenerOnTheToolkit() throws Exception {
        var first = window(false);
        edt(first::close);
        int[] before = new int[1];
        edt(() -> before[0] = mouseListeners());
        var owner = window(false);
        edt(() -> {
            assertThat(mouseListeners()).as("the window's press listener").isGreaterThan(before[0]);
            owner.close();
            assertThat(mouseListeners()).as("the count before the window was built").isEqualTo(before[0]);
        });
    }

    @Test void zeroTurnsTheDimOffAndAnotherAmountTakesEffectAtOnce() throws Exception {
        var owner = window(true);
        edt(() -> {
            var model = new Contributions();
            owner.connectContributions(model);
            Tab left = owner.tabs().get(0), right = owner.tabs().get(1);
            Tab view = model.tabs().open("dev.example.editor", "notes", "notes.txt", owner.id(), new JPanel(),
                TabRegistry.Placement.SPLIT_RIGHT);
            GroupView pane = owner.workspace().groupView(view.group().orElseThrow());
            owner.selectTab(left);
            boolean mac = System.getProperty("os.name").startsWith("Mac");
            owner.applyConfiguration(dimmedBy(0f), mac);
            assertThat(dim(right)).as("terminal.dim_inactive_panes = 0").isZero();
            assertThat(pane.dim()).isZero();
            owner.applyConfiguration(dimmedBy(0.5f), mac);
            assertThat(dim(right)).isEqualTo(0.5f);
            assertThat(pane.dim()).isEqualTo(0.5f);
            assertThat(dim(left)).as("the active pane").isZero();
        });
    }
}
