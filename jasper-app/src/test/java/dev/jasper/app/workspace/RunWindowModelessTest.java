package dev.jasper.app.workspace;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.app.commands.ActionId;
import dev.jasper.app.config.KeyBindings;
import dev.jasper.app.lifecycle.Subscription;
import dev.jasper.app.palette.PaletteContext;
import dev.jasper.app.palette.PaletteKeyRouter;
import dev.jasper.app.palette.PaletteResults;
import dev.jasper.app.palette.PaletteRow;
import dev.jasper.app.palette.PaletteScope;
import dev.jasper.app.palette.PaletteVerb;
import dev.jasper.app.palette.RunWindowView;
import dev.jasper.app.presentation.PaletteLook;
import dev.jasper.app.testsupport.LayoutTestSupport;
import java.awt.Component;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.swing.JComponent;
import org.junit.jupiter.api.Test;

/** Spec 11.6: the Run window stays open while the person works in the window; the card dismisses as before. */
class RunWindowModelessTest {
    static KeyEvent key(Component source, int code, int modifiers) {
        return new KeyEvent(source, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), modifiers, code, KeyEvent.CHAR_UNDEFINED);
    }

    static PaletteKeyRouter router(WindowContent owner) {
        return new PaletteKeyRouter(owner.commandPalette().controller(), owner.commandPalette()::open, () -> KeyBindings.defaults(true), true, component -> true);
    }

    /** A scope with one row, "here", that records the pane each run targets. */
    static PaletteScope recording(List<String> runs) {
        return new PaletteScope() {
            @Override public String id() { return "test.here"; }
            @Override public String label() { return "Here"; }
            @Override public String placeholder() { return "Search here"; }
            @Override public List<PaletteVerb> verbs() { return List.of(new PaletteVerb("run", "Run")); }
            @Override public PaletteResults search(String query, PaletteContext context) {
                return new PaletteResults(query.isEmpty() || "here".contains(query) ? List.of(PaletteRow.of("here", "here")) : List.of(), null, null);
            }
            @Override public void execute(PaletteRow row, PaletteVerb verb, PaletteContext context) {
                runs.add(context.target().paneId().map(UUID::toString).orElse("none"));
            }
            @Override public Subscription onChanged(Runnable listener) { return new Subscription(() -> { }); }
        };
    }

    record Opened(WindowContent owner, javax.swing.JRootPane root, JComponent window, JComponent overlay) implements AutoCloseable {
        @Override public void close() { owner.close(); }
    }

    static Opened open(PaletteLook look) {
        var owner = CornerHomeTest.owner(look);
        var root = WindowCommandPaletteTest.install(owner); root.setSize(1400, 900); LayoutTestSupport.layoutTree(root);
        owner.commandPalette().toggle(); LayoutTestSupport.layoutTree(root);
        var window = owner.commandPalette().component();
        return new Opened(owner, root, window, (JComponent) window.getParent());
    }

    /** Review Focus 1. */
    @Test void aPaneTakesItsKeysWhileTheRunWindowStaysOpen() throws Exception {
        DesktopTestSupport.edt(() -> {
            try (var o = open(PaletteLook.RUN_WINDOW)) {
                var palette = o.owner().commandPalette();
                var router = router(o.owner());
                Component pane = o.owner().currentPane();
                assertThat(router.dispatch(key(pane, KeyEvent.VK_ENTER, 0))).as("Enter in a pane is the pane's").isFalse();
                assertThat(router.dispatch(key(pane, KeyEvent.VK_ESCAPE, 0))).as("so is Escape").isFalse();
                assertThat(palette.isOpen()).isTrue();
                assertThat(router.dispatch(key(pane, KeyEvent.VK_P, InputEvent.META_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK)))
                    .as("the opener is the palette's").isTrue();
                assertThat(palette.isOpen()).as("and it focuses, not closes").isTrue();
                o.owner().action(ActionId.COMMAND_PALETTE).actionPerformed(new ActionEvent(o.owner(), 0, "status item"));
                assertThat(palette.isOpen()).as("the COMMAND_PALETTE action (▶ Run…, Search everywhere) focuses too").isTrue();
                assertThat(palette.blocksActions()).isFalse();
                int tabs = o.owner().tabStrip().getTabCount();
                o.owner().action(ActionId.NEW_TAB).actionPerformed(new ActionEvent(o.owner(), 0, "menu"));
                assertThat(o.owner().tabStrip().getTabCount()).as("the window's actions run").isEqualTo(tabs + 1);
                assertThat(palette.isOpen()).as("a tab switch keeps it").isTrue();
                var inside = new Point(o.window().getX() + 5, o.window().getY() + 5);
                var outside = new Point(o.window().getX() + o.window().getWidth() + 40, o.window().getY() + 5);
                assertThat(o.overlay().contains(inside)).isTrue();
                assertThat(o.overlay().contains(outside)).as("a press outside reaches the window below").isFalse();
                Component query = dev.jasper.app.palette.PaletteTestSupport.runQuery(o.window());
                assertThat(router.dispatch(key(query, KeyEvent.VK_ESCAPE, 0))).as("Escape inside it closes it").isTrue();
                assertThat(palette.isOpen()).isFalse();
            }
        });
    }

    /** Review Focus 2. */
    @Test void focusLossAndATabSwitchKeepItAndARowRunsInTheCurrentPane() throws Exception {
        DesktopTestSupport.edt(() -> {
            var runs = new ArrayList<String>();
            try (var o = open(PaletteLook.RUN_WINDOW)) {
                o.owner().scopes().register(recording(runs));
                var palette = o.owner().commandPalette();
                palette.dismiss();
                palette.open("test.here"); LayoutTestSupport.layoutTree(o.root());
                o.owner().setActive(false);
                assertThat(palette.isOpen()).as("another application took focus").isTrue();
                o.owner().setActive(true);
                o.owner().action(ActionId.NEW_TAB).actionPerformed(new ActionEvent(o.owner(), 0, "menu"));
                String current = o.owner().currentPane().id().toString();
                palette.enterPressed(0);
                assertThat(runs).containsExactly(current);
                assertThat(palette.isOpen()).as("running a row closes it").isFalse();
            }
        });
    }

    /** A scope whose one row is what it reads when it searches, as History reads the target pane's folder. */
    static PaletteScope reading(String[] folder) {
        return new PaletteScope() {
            @Override public String id() { return "test.folder"; }
            @Override public String label() { return "Folder"; }
            @Override public String placeholder() { return "Search the folder"; }
            @Override public List<PaletteVerb> verbs() { return List.of(new PaletteVerb("run", "Run")); }
            @Override public PaletteResults search(String query, PaletteContext context) {
                return new PaletteResults(List.of(PaletteRow.of(folder[0], folder[0])), null, null);
            }
            @Override public void execute(PaletteRow row, PaletteVerb verb, PaletteContext context) { }
            @Override public Subscription onChanged(Runnable listener) { return new Subscription(() -> { }); }
        };
    }

    /**
     * Final review: an opener focusing the open Run window rebuilds its rows, so they follow the pane the person left (a
     * pane switch in the tab or a cd rebuilds nothing by itself); the fixture's folder stands for the target's.
     */
    @Test void anOpenerRebuildsTheRows() throws Exception {
        DesktopTestSupport.edt(() -> {
            var folder = new String[] {"first"};
            try (var o = open(PaletteLook.RUN_WINDOW)) {
                o.owner().scopes().register(reading(folder));
                var palette = o.owner().commandPalette();
                palette.open("test.folder"); LayoutTestSupport.layoutTree(o.root());
                assertThat(palette.view().selectedKey()).isEqualTo("test.folder/first");
                folder[0] = "second";
                palette.open("test.folder");
                assertThat(palette.isOpen()).as("the opener focuses it").isTrue();
                assertThat(palette.view().selectedKey()).as("its rows read the target again").isEqualTo("test.folder/second");
            }
        });
    }

    @Test void itClosesOnTheCloseButtonAndCancelAndHasNoReveal() throws Exception {
        DesktopTestSupport.edt(() -> {
            try (var o = open(PaletteLook.RUN_WINDOW)) {
                var palette = o.owner().commandPalette();
                var view = (RunWindowView) palette.view();
                dev.jasper.app.palette.PaletteTestSupport.handle(view, "run.close");
                assertThat(palette.isOpen()).isFalse();
                o.owner().setAnimations(dev.jasper.app.config.AnimationSettings.defaults());
                palette.toggle(); LayoutTestSupport.layoutTree(o.root());
                assertThat(palette.revealRunning()).as("no reveal, whatever the animations setting").isFalse();
                dev.jasper.app.palette.PaletteTestSupport.handle(view, "run.cancel");
                assertThat(palette.isOpen()).isFalse();
            }
        });
    }

    @Test void theCardKeepsTodaysDismissals() throws Exception {
        DesktopTestSupport.edt(() -> {
            try (var o = open(PaletteLook.DEFAULT)) {
                var palette = o.owner().commandPalette();
                assertThat(palette.blocksActions()).isTrue();
                var outside = new Point(o.window().getX() + o.window().getWidth() + 40, o.window().getY() + 5);
                assertThat(o.overlay().contains(outside)).as("the card's overlay takes every press").isTrue();
                o.owner().setActive(false);
                assertThat(palette.isOpen()).isFalse();
                o.owner().setActive(true);
                palette.toggle();
                palette.toggle();
                assertThat(palette.isOpen()).as("its opener closes it").isFalse();
            }
        });
    }
}
