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
import java.awt.Color;
import java.awt.event.FocusEvent;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayDeque;
import javax.swing.JPanel;
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
