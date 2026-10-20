package dev.jasper.app.workspace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.formdev.flatlaf.util.UIScale;
import dev.jasper.app.appearance.ThemeTestSupport;
import dev.jasper.app.commands.CommandHistory;
import dev.jasper.app.config.KeyBindings;
import dev.jasper.app.persistence.UiState;
import dev.jasper.app.presentation.PaletteLook;
import dev.jasper.app.presentation.TitleFormat;
import dev.jasper.app.presentation.WorkspaceLook;
import dev.jasper.app.testsupport.LayoutTestSupport;
import dev.jasper.ui.metrics.DesignMetrics;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.util.ArrayDeque;
import javax.swing.JComponent;
import javax.swing.JRootPane;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

/** Spec 11.5: the Run window's upper home, its attach rule, its saved place and its fit. */
class UpperHomeTest {
    /** The default look's workspace chrome (5a's default WorkspaceLook) carrying {@code look}'s palette. */
    static WorkspaceChrome chrome(PaletteLook look) {
        return WorkspaceChrome.of(new WorkspaceLook(WorkspaceLook.Menus.STANDARD, WorkspaceLook.Header.UNIFIED, WorkspaceLook.Stripes.RAILS,
            WorkspaceLook.Tabs.EQUAL, WorkspaceLook.Status.SLASH, false, WorkspaceLook.FrameStyle.STANDARD,
            WorkspaceLook.Icons.NEW_UI), new TitleFormat("{context}"), look);
    }

    /** A window under {@link #chrome(PaletteLook)}, through plan 6a's constructor. */
    static WindowContent owner(PaletteLook look) {
        return new WindowContent(DesktopTestSupport.launcher(new ArrayDeque<>()), DesktopTestSupport.HOME, false, path -> { }, () -> { }, () -> { },
            ThemeTestSupport.controller(true), KeyBindings.defaults(true), new CommandHistory(), true, chrome(look));
    }

    static int margin() { return UIScale.scale(DesignMetrics.DEFAULT.value(DesignMetrics.Role.PALETTE_WINDOW_MARGIN)); }

    static void mouse(JComponent window, int type, Point overlayPoint, int clicks) {
        var at = SwingUtilities.convertPoint(window.getParent(), overlayPoint, window);
        window.dispatchEvent(new MouseEvent(window, type, 0, type == MouseEvent.MOUSE_RELEASED || type == MouseEvent.MOUSE_CLICKED
            ? 0 : InputEvent.BUTTON1_DOWN_MASK, at.x, at.y, clicks, false, MouseEvent.BUTTON1));
    }

    /** The title row's middle: neither the title, the shortcut nor × takes a press there. */
    static Point grab(JComponent window) { return new Point(window.getX() + window.getWidth() / 2, window.getY() + UIScale.scale(4)); }

    record Opened(WindowContent owner, JRootPane root, JComponent window, JComponent overlay, UiState state) implements AutoCloseable {
        @Override public void close() { owner.close(); }
        Rectangle work() { return owner.paletteWorkArea(overlay); }
        Rectangle workspace() { return SwingUtilities.convertRectangle(owner.getParent(), owner.getBounds(), overlay); }
    }

    static Opened open(int width, int height) {
        var state = UiState.inMemory();
        var owner = owner(PaletteLook.RUN_WINDOW);
        owner.connectContributions(new dev.jasper.app.contributions.Contributions(), state);
        var root = WindowCommandPaletteTest.install(owner); root.setSize(width, height); LayoutTestSupport.layoutTree(root);
        owner.commandPalette().toggle(); LayoutTestSupport.layoutTree(root);
        var window = owner.commandPalette().component();
        return new Opened(owner, root, window, (JComponent) window.getParent(), state);
    }

    /** Rulings 19's rule as one expression, from the window's own height: the seventh down, or ending at the middle, clamped. */
    static int expectedTop(Opened o) {
        Rectangle ws = o.workspace(), work = o.work();
        int h = o.window().getHeight(), inset = Math.min(margin(), work.height / 2);
        int top = Math.min(ws.y + ws.height / UpperHome.TOP_FRACTION, ws.y + ws.height / 2 - h);
        return Math.max(work.y + inset, Math.min(top, work.y + work.height - inset - h));
    }

    /** The user's request (5): centred across the window, a seventh down, entirely in the upper half, in a window tall enough. */
    @Test void theRunWindowOpensCentredInTheUpperPartOfTheWindow() throws Exception {
        DesktopTestSupport.edt(() -> {
            try (var o = open(1400, 1300)) {
                assertThat(o.owner().commandPalette().view()).isInstanceOf(dev.jasper.app.palette.RunWindowView.class);
                var area = SwingUtilities.convertRectangle(o.owner().area().getParent(), o.owner().area().getBounds(), o.overlay());
                assertThat(o.work()).as("the tool window area between the stripes, above the status bar").isEqualTo(area);
                var ws = o.workspace();
                int seventh = ws.y + ws.height / UpperHome.TOP_FRACTION, middle = ws.y + ws.height / 2;
                assertThat(seventh + o.window().getHeight()).as("precondition: a seventh down fits above the middle").isLessThanOrEqualTo(middle);
                assertThat(o.window().getX() + o.window().getWidth() / 2.0).isCloseTo(ws.x + ws.width / 2.0, within(1.0));
                assertThat(o.window().getY()).isEqualTo(seventh);
                assertThat(o.window().getY() + o.window().getHeight()).isLessThanOrEqualTo(middle);
                assertThat(o.window().getY()).isGreaterThanOrEqualTo(o.work().y + margin());
            }
        });
    }

    @Test void aWindowTooTallForASeventhDownEndsAtTheMiddle() throws Exception {
        DesktopTestSupport.edt(() -> {
            try (var o = open(1400, 950)) {
                var ws = o.workspace();
                int middle = ws.y + ws.height / 2, h = o.window().getHeight();
                assertThat(ws.y + ws.height / UpperHome.TOP_FRACTION + h).as("precondition: a seventh down would cross the middle").isGreaterThan(middle);
                assertThat(middle - h).as("precondition: ending at the middle stays in the work area").isGreaterThanOrEqualTo(o.work().y + margin());
                assertThat(o.window().getX() + o.window().getWidth() / 2.0).isCloseTo(ws.x + ws.width / 2.0, within(1.0));
                assertThat(o.window().getY() + h).isEqualTo(middle);
            }
        });
    }

    /** Rulings 19: in an ordinary window the rule holds, even where the window must sit just under the toolbar and cross the middle. */
    @Test void anOrdinaryWindowFollowsTheRule() throws Exception {
        DesktopTestSupport.edt(() -> {
            for (int height : new int[] {700, 800, 900, 1000, 1100, 1200})
                try (var o = open(1400, height)) {
                    var ws = o.workspace();
                    assertThat(o.window().getX() + o.window().getWidth() / 2.0).as(height + " centred").isCloseTo(ws.x + ws.width / 2.0, within(1.0));
                    assertThat(o.window().getY()).as(height + " top").isEqualTo(expectedTop(o));
                }
        });
    }

    /** Review Focus 2. */
    @Test void aShortWindowKeepsTheRunWindowInsideTheWorkArea() throws Exception {
        DesktopTestSupport.edt(() -> {
            for (var size : new java.awt.Dimension[] {new java.awt.Dimension(1400, 380), new java.awt.Dimension(560, 900)})
                try (var o = open(size.width, size.height)) {
                    var work = o.work();
                    var bounds = o.window().getBounds();
                    assertThat(bounds.y).as(size + " never above the work area").isGreaterThanOrEqualTo(work.y + Math.min(margin(), work.height / 2));
                    assertThat(bounds.y).as(size + " the rule").isEqualTo(expectedTop(o));
                    assertThat(bounds.x).isGreaterThanOrEqualTo(work.x);
                    assertThat(bounds.x + bounds.width).isLessThanOrEqualTo(work.x + work.width);
                    assertThat(bounds.y + bounds.height).isLessThanOrEqualTo(work.y + work.height);
                }
        });
    }

    @Test void aDragAttachesNearItsHomeAndElsewhereSavesItsPlace() throws Exception {
        DesktopTestSupport.edt(() -> {
            try (var o = open(1400, 900)) {
                var palette = o.owner().commandPalette();
                Rectangle home = o.window().getBounds();
                Point press = grab(o.window());
                mouse(o.window(), MouseEvent.MOUSE_PRESSED, press, 1);
                mouse(o.window(), MouseEvent.MOUSE_DRAGGED, new Point(press.x + 300, press.y + 250), 1);
                assertThat(palette.dragging()).isTrue();
                assertThat(palette.snappedToCenter() || palette.snappedToTop()).as("no guides").isFalse();
                assertThat(o.window().getLocation()).isEqualTo(new Point(home.x + 300, home.y + 250));
                mouse(o.window(), MouseEvent.MOUSE_RELEASED, new Point(press.x + 300, press.y + 250), 1);
                var saved = o.state().runWindowPosition().orElseThrow();
                var ws = o.workspace();
                assertThat(saved.left()).isCloseTo((o.window().getX() - ws.x) / (double) ws.width, within(0.002));
                assertThat(saved.bottom()).isCloseTo((o.window().getY() + o.window().getHeight() - ws.y) / (double) ws.height, within(0.002));
                assertThat(o.state().palettePosition()).as("the card's place is not the Run window's").isEmpty();
                Rectangle dropped = o.window().getBounds();
                palette.dismiss(); palette.toggle(); LayoutTestSupport.layoutTree(o.root());
                assertThat(o.window().getBounds()).as("reopening keeps the saved place").isEqualTo(dropped);

                press = grab(o.window());
                int dx = home.x - o.window().getX(), dy = home.y - o.window().getY();
                mouse(o.window(), MouseEvent.MOUSE_PRESSED, press, 1);
                mouse(o.window(), MouseEvent.MOUSE_DRAGGED, new Point(press.x + dx, press.y + dy + 80), 1);
                assertThat(palette.snappedHome()).as("well below its home it stays where it is dropped").isFalse();
                mouse(o.window(), MouseEvent.MOUSE_DRAGGED, new Point(press.x + dx + 12, press.y + dy + 12), 1);
                assertThat(palette.snappedHome()).as("near it across and down").isTrue();
                assertThat(o.window().getBounds()).isEqualTo(home);
                mouse(o.window(), MouseEvent.MOUSE_RELEASED, new Point(press.x + dx + 12, press.y + dy + 12), 1);
                assertThat(o.state().runWindowPosition()).as("re-attached: forgotten").isEmpty();

                press = grab(o.window());
                mouse(o.window(), MouseEvent.MOUSE_PRESSED, press, 1);
                mouse(o.window(), MouseEvent.MOUSE_DRAGGED, new Point(press.x + 200, press.y + 200), 1);
                mouse(o.window(), MouseEvent.MOUSE_RELEASED, new Point(press.x + 200, press.y + 200), 1);
                assertThat(o.state().runWindowPosition()).isPresent();
                press = grab(o.window());
                mouse(o.window(), MouseEvent.MOUSE_PRESSED, press, 1);
                mouse(o.window(), MouseEvent.MOUSE_RELEASED, press, 1);
                mouse(o.window(), MouseEvent.MOUSE_CLICKED, press, 2);
                assertThat(o.state().runWindowPosition()).as("a double click on the title row returns home").isEmpty();
                assertThat(o.window().getBounds()).isEqualTo(home);
            }
        });
    }

    /** Review Focus 4. */
    @Test void aSavedPlaceFromALargerWindowStaysInside() throws Exception {
        DesktopTestSupport.edt(() -> {
            try (var o = open(1400, 900)) {
                o.state().setRunWindowPosition(new UiState.RunWindowPosition(0.95, 0.05));
                o.owner().commandPalette().dismiss(); o.owner().commandPalette().toggle(); LayoutTestSupport.layoutTree(o.root());
                var ws = o.workspace();
                var inset = new Rectangle(ws.x + margin(), ws.y + margin(), ws.width - 2 * margin(), ws.height - 2 * margin());
                assertThat(inset.contains(o.window().getBounds())).as(o.window().getBounds() + " inside " + inset).isTrue();
            }
        });
    }

    /** Review Focus 3: a 500 px tall window with eight visible rows. */
    @Test void aShortWindowShrinksTheListToFitTheHome() throws Exception {
        DesktopTestSupport.edt(() -> {
            try (var o = open(1400, 900)) {
                var view = (dev.jasper.app.palette.RunWindowView) o.owner().commandPalette().view();
                o.owner().commandPalette().setVisibleRows(8); LayoutTestSupport.layoutTree(o.root());
                int rows = view.shownRows();
                assertThat(rows).isEqualTo(view.visibleRows());
                o.root().setSize(1400, 500); LayoutTestSupport.layoutTree(o.root());
                o.overlay().doLayout(); LayoutTestSupport.layoutTree(o.root());
                var work = o.work();
                int room = work.height - 2 * margin(), row = UIScale.scale(DesignMetrics.DEFAULT.value(DesignMetrics.Role.RUN_WINDOW_ROW_HEIGHT));
                assertThat(view.shownRows()).as("the 427 px window does not fit: rows go").isLessThan(rows).isGreaterThanOrEqualTo(1);
                assertThat(o.window().getHeight() <= room || view.shownRows() == 1)
                    .as("the window, " + o.window().getHeight() + " px, fits " + room + " px, or shows exactly one row").isTrue();
                assertThat(o.window().getHeight() + row).as("only the rows that do not fit go").isGreaterThan(room);
                o.root().setSize(1400, 900); LayoutTestSupport.layoutTree(o.root());
                o.overlay().doLayout(); LayoutTestSupport.layoutTree(o.root());
                assertThat(view.shownRows()).as("it grows back").isEqualTo(rows);
            }
        });
    }

    /** Spec 11.2 and 8a Rulings 14: a press on the title text drags the window like the rest of the title row. */
    @Test void aPressOnTheTitleTextDragsTheWindow() throws Exception {
        DesktopTestSupport.edt(() -> {
            try (var o = open(1400, 900)) {
                var palette = o.owner().commandPalette();
                var title = dev.jasper.app.testsupport.KitTestSupport.require(o.window(), "run.title", javax.swing.JLabel.class);
                Rectangle home = o.window().getBounds();
                int x = title.getWidth() / 2, y = title.getHeight() / 2;
                title.dispatchEvent(new MouseEvent(title, MouseEvent.MOUSE_PRESSED, 0, InputEvent.BUTTON1_DOWN_MASK, x, y, 1, false, MouseEvent.BUTTON1));
                title.dispatchEvent(new MouseEvent(title, MouseEvent.MOUSE_DRAGGED, 0, InputEvent.BUTTON1_DOWN_MASK, x + 200, y + 150, 1, false, MouseEvent.BUTTON1));
                assertThat(palette.dragging()).as("the title text drags").isTrue();
                assertThat(o.window().getLocation()).isEqualTo(new Point(home.x + 200, home.y + 150));
                title.dispatchEvent(new MouseEvent(title, MouseEvent.MOUSE_RELEASED, 0, 0, x + 200, y + 150, 1, false, MouseEvent.BUTTON1));
                assertThat(o.state().runWindowPosition()).as("and the drop saves its place").isPresent();
            }
        });
    }

    @Test void theDefaultLookKeepsTheTitleFieldsCard() throws Exception {
        DesktopTestSupport.edt(() -> {
            try (var owner = owner(PaletteLook.DEFAULT)) {
                var root = WindowCommandPaletteTest.install(owner); root.setSize(1400, 900); LayoutTestSupport.layoutTree(root);
                owner.commandPalette().toggle(); LayoutTestSupport.layoutTree(root);
                assertThat(owner.commandPalette().view()).isInstanceOf(dev.jasper.app.palette.CardView.class);
                assertThat(owner.commandPalette().component().getWidth()).as("the anchored card").isEqualTo(UIScale.scale(560));
            }
        });
    }
}
