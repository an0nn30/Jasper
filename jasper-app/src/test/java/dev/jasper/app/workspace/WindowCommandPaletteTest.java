package dev.jasper.app.workspace;

import dev.jasper.app.application.ApplicationTestSupport;
import dev.jasper.app.history.HistoryTestSupport;
import dev.jasper.app.palette.PaletteTestSupport;
import dev.jasper.app.testsupport.LayoutTestSupport;
import dev.jasper.app.appearance.ThemeController;
import dev.jasper.app.application.JasperApplication;
import dev.jasper.app.commands.ActionId;
import dev.jasper.app.commands.Command;
import dev.jasper.app.config.Appearance;
import dev.jasper.app.config.KeyBindings;
import dev.jasper.app.history.CommandHistory;
import dev.jasper.app.palette.PaletteController;
import dev.jasper.app.palette.PaletteRow;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.*;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class WindowCommandPaletteTest {
    static JRootPane install(WindowContent owner) {
        var root = new JRootPane(); root.setContentPane(owner); owner.installRootBindings(root);
        root.setSize(900, 600); LayoutTestSupport.layoutTree(root); return root;
    }
    static WindowContent owner(CommandHistory history) {
        return new WindowContent(DesktopTestSupport.launcher(new ArrayDeque<>()), DesktopTestSupport.HOME,
            path -> {}, () -> {}, () -> {}, new ThemeController(), KeyBindings.defaults(true),
            System::nanoTime, history, true);
    }
    static Command command(String id, Runnable run) {
        return new Command(id, new AbstractAction(id) {
            @Override public void actionPerformed(ActionEvent event) { run.run(); }
        }, List.of());
    }
    @Test void theCardTopIsFixedSoTheInputRowNeverMovesAsResultsChange() {
        var deck = new Rectangle(0, 100, 900, 600);
        int anchor = deck.y + deck.height / 5;
        for (int height : new int[] {96, 160, 304, 420}) {
            var placed = WindowCommandPalette.positioned(deck, new Dimension(560, height), deck, 16);
            assertThat(placed.y).as("card top for height %d", height).isEqualTo(anchor);
        }
    }
    @Test void aCardTallerThanTheDeckIsPulledUpToStayOnScreen() {
        var deck = new Rectangle(0, 100, 900, 300);
        var placed = WindowCommandPalette.positioned(deck, new Dimension(560, 400), deck, 16);
        assertThat(placed.y).isEqualTo(deck.y + 16);
        assertThat(placed.height).isEqualTo(300 - 32);
    }
    @Test void neitherAScopeSwitchNorANewRowCountMovesTheInputRow() throws Exception {
        DesktopTestSupport.edt(() -> {
            try (var owner = owner(new CommandHistory())) {
                var root = install(owner);
                var fake = new PaletteScopesTest.FakeScope();
                owner.scopes().register(fake);
                var palette = owner.commandPalette(); var card = palette.component();
                palette.toggle(); LayoutTestSupport.layoutTree(root);
                int anchor = card.getBounds().y; int commandsHeight = card.getBounds().height;
                palette.open(fake.id()); LayoutTestSupport.layoutTree(root);
                assertThat(card.getBounds().y).as("after a scope switch").isEqualTo(anchor);
                fake.rows = List.of();
                fake.listeners.forEach(Runnable::run); LayoutTestSupport.layoutTree(root);
                assertThat(card.getBounds().y).as("with no rows").isEqualTo(anchor);
                int emptyHeight = card.getBounds().height;
                fake.rows = List.of(PaletteRow.of("a", "A"), PaletteRow.of("b", "B"), PaletteRow.of("c", "C"),
                    PaletteRow.of("d", "D"), PaletteRow.of("e", "E"));
                fake.listeners.forEach(Runnable::run); LayoutTestSupport.layoutTree(root);
                assertThat(card.getBounds().y).as("with five rows").isEqualTo(anchor);
                // The card must actually have changed height, or the assertions above prove nothing.
                assertThat(card.getBounds().height).isGreaterThan(emptyHeight);
                assertThat(commandsHeight).isNotEqualTo(emptyHeight);
            }
        });
    }
    @Test void overlayDoesNotResizeTerminalAndAnchorsTheCardTopInTheWholeDeck() throws Exception {
        DesktopTestSupport.edt(() -> {
            try (var owner = owner(new CommandHistory())) {
                var root = install(owner); var before = owner.tabStrip().getBounds();
                owner.commandPalette().toggle(); LayoutTestSupport.layoutTree(root);
                assertThat(owner.tabStrip().getBounds()).isEqualTo(before);
                var palette = owner.commandPalette().component();
                var deck = SwingUtilities.convertRectangle(owner.tabStrip().getParent(), before, palette.getParent());
                assertThat(Math.abs(palette.getBounds().getCenterX() - deck.getCenterX())).isLessThanOrEqualTo(1);
                assertThat(palette.getBounds().y).isEqualTo(deck.y + deck.height / 5);
                owner.setActive(false); assertThat(owner.commandPalette().isOpen()).isFalse();
            }
        });
    }
    @SuppressWarnings("try")
    @Test void registeredActionsExecuteAndRecentsAreSharedWithoutCrossWindowDispatch() throws Exception {
        DesktopTestSupport.edt(() -> {
            try (var history = new CommandHistory(); var first = owner(history); var second = owner(history)) {
                install(first); install(second);
                var a = new AtomicInteger(); var b = new AtomicInteger();
                first.commands().register(command("custom", a::incrementAndGet));
                second.commands().register(command("custom", b::incrementAndGet));
                first.commandPalette().toggle(); first.commandPalette().component().queryField().setText("custom");
                dev.jasper.app.palette.PaletteTestSupport.executeSelected(first.commandPalette().component());
                assertThat(a.get()).isEqualTo(1); assertThat(b.get()).isZero();
                second.commandPalette().toggle();
                assertThat(dev.jasper.app.palette.PaletteTestSupport.resultList(second.commandPalette().component()).getSelectedValue().id()).isEqualTo("custom");
                first.close(); dev.jasper.app.palette.PaletteTestSupport.executeSelected(second.commandPalette().component());
                assertThat(b.get()).isEqualTo(1); assertThat(history.recent()).containsExactly("custom");
            }
        });
    }
    @Test void staleRowsAndDisabledCommandsCannotExecute() throws Exception {
        DesktopTestSupport.edt(() -> {
            try (var owner = owner(new CommandHistory())) {
                install(owner); var count = new AtomicInteger(); var stale = command("custom", count::incrementAndGet);
                var registration = owner.commands().register(stale);
                owner.commandPalette().toggle(); owner.commandPalette().component().queryField().setText("custom");
                registration.close(); owner.commands().register(command("custom", count::incrementAndGet));
                // Simulate a row already selected by a queued mouse/key event.
                PaletteTestSupport.setResults(owner.commandPalette().component(), dev.jasper.app.palette.builtin.ScopeTestSupport.rows(owner.commandsScope(), List.of(stale)), null, null);
                dev.jasper.app.palette.PaletteTestSupport.executeSelected(owner.commandPalette().component()); assertThat(count.get()).isZero();
                owner.commandPalette().toggle(); stale = command("disabled", count::incrementAndGet);
                owner.commands().register(stale); stale.action().setEnabled(false);
                PaletteTestSupport.setResults(owner.commandPalette().component(), dev.jasper.app.palette.builtin.ScopeTestSupport.rows(owner.commandsScope(), List.of(stale)), null, null);
                dev.jasper.app.palette.PaletteTestSupport.executeSelected(owner.commandPalette().component()); assertThat(count.get()).isZero();
            }
        });
    }
    @Test void tabChangesDismissAndUnchangedUpdatesDoNotRebuildResults() throws Exception {
        DesktopTestSupport.edt(() -> {
            try (var owner = owner(new CommandHistory())) {
                install(owner); owner.commandPalette().toggle();
                var changes = new AtomicInteger();
                dev.jasper.app.palette.PaletteTestSupport.resultList(owner.commandPalette().component()).getModel().addListDataListener(new ListDataListener() {
                    public void intervalAdded(ListDataEvent e) { changes.incrementAndGet(); }
                    public void intervalRemoved(ListDataEvent e) { changes.incrementAndGet(); }
                    public void contentsChanged(ListDataEvent e) { changes.incrementAndGet(); }
                });
                owner.update(); owner.update(); assertThat(changes.get()).isZero();
                owner.newTab(DesktopTestSupport.HOME); assertThat(owner.commandPalette().isOpen()).isFalse();
            }
        });
    }
    @Test void themesAndStatusRefreshWhileOpenAndDisposalDetachesOverlay() throws Exception {
        DesktopTestSupport.edt(() -> {
            var owner = owner(new CommandHistory()); var root = install(owner);
            int listeners = root.getComponentListeners().length;
            owner.commandPalette().toggle(); owner.commandPalette().component().queryField().setText("status bar");
            assertThat(dev.jasper.app.palette.PaletteTestSupport.resultList(owner.commandPalette().component()).getSelectedValue().title()).isEqualTo("Hide Status Bar");
            owner.setStatusVisible(false);
            assertThat(dev.jasper.app.palette.PaletteTestSupport.resultList(owner.commandPalette().component()).getSelectedValue().title()).isEqualTo("Show Status Bar");
            owner.selectAppearance(Appearance.LIGHT); assertThat(owner.commandPalette().isOpen()).isTrue();
            var overlay = owner.commandPalette().component().getParent(); owner.close();
            assertThat(overlay.getParent()).isNull(); assertThat(root.getComponentListeners().length).isLessThan(listeners);
            assertThat(owner.commands().entries()).isEmpty();
        });
    }
    @Test void outsideClickIsCapturedThroughReleaseAcrossSurroundingChrome() throws Exception {
        DesktopTestSupport.edt(() -> {
            try (var owner = owner(new CommandHistory())) {
                var root = new JRootPane(); var wrapper = new JPanel(new BorderLayout());
                var title = new JButton("title chrome"); wrapper.add(title, BorderLayout.NORTH); wrapper.add(owner);
                root.setContentPane(wrapper); owner.installRootBindings(root); root.setSize(900,600); LayoutTestSupport.layoutTree(root);
                owner.commandPalette().toggle(); LayoutTestSupport.layoutTree(root);
                var overlay = (JComponent) owner.commandPalette().component().getParent();
                var point = SwingUtilities.convertPoint(title, 3, 3, overlay);
                assertThat(overlay.contains(point)).isTrue();
                for (int type : new int[]{MouseEvent.MOUSE_PRESSED, MouseEvent.MOUSE_RELEASED, MouseEvent.MOUSE_CLICKED}) {
                    var event = new MouseEvent(overlay, type, 0, 0, point.x, point.y, 1, false, MouseEvent.BUTTON1);
                    overlay.dispatchEvent(event); assertThat(event.isConsumed()).isTrue();
                    assertThat(owner.commandPalette().isOpen()).isFalse();
                }
                assertThat(overlay.isVisible()).isFalse();
            }
        });
    }
    @Test void lastWindowAndQuitDispatchAreRecordedBeforeDeferredHistoryClose(@org.junit.jupiter.api.io.TempDir java.nio.file.Path dir) throws Exception {
        for (boolean quit : new boolean[]{false, true}) {
            CommandHistory[] history = new CommandHistory[1];
            var file = dir.resolve(quit ? "quit.toml" : "close.toml");
            DesktopTestSupport.edt(() -> {
                history[0] = new CommandHistory(file);
                var application = new JasperApplication(null,
                    DesktopTestSupport.launcher(new ArrayDeque<>()), history[0]);
                WindowContent[] content = new WindowContent[1];
                content[0] = new WindowContent(DesktopTestSupport.launcher(new ArrayDeque<>()), DesktopTestSupport.HOME,
                    path -> {}, () -> { content[0].close(); application.quit(); },
                    () -> { content[0].close(); ApplicationTestSupport.windowClosed(application, null); }, new ThemeController(),
                    KeyBindings.defaults(true), System::nanoTime, history[0], true);
                install(content[0]); content[0].commandPalette().toggle();
                content[0].commandPalette().component().queryField().setText(quit ? "quit" : "close tab");
                dev.jasper.app.palette.PaletteTestSupport.executeSelected(content[0].commandPalette().component());
                assertThat(history[0].recent()).containsExactly(quit ? "quit" : "close_tab");
                assertThat(history[0].closedFuture()).isNotDone();
                assertThat(application.newWindow(DesktopTestSupport.HOME)).isNull();
                application.quit(); // Repeated shutdown requests retain the accepted dispatch.
            });
            history[0].closedFuture().get(5, java.util.concurrent.TimeUnit.SECONDS);
            assertThat(HistoryTestSupport.readCommands(file)).containsExactly(quit ? "quit" : "close_tab");
        }
    }

    @Test void failedActionReportsAndDoesNotRecord() throws Exception {
        DesktopTestSupport.edt(() -> {
            var logger = java.util.logging.Logger.getLogger(PaletteController.class.getName());
            boolean parents = logger.getUseParentHandlers(); logger.setUseParentHandlers(false);
            var records = new ArrayList<java.util.logging.LogRecord>();
            var handler = new java.util.logging.Handler() {
                public void publish(java.util.logging.LogRecord record) { records.add(record); }
                public void flush() {} public void close() {}
            };
            logger.addHandler(handler);
            try (var history = new CommandHistory(); var owner = owner(history)) {
                install(owner); var errors = new ArrayList<String>(); owner.onError = errors::add;
                owner.commands().register(command("failing", () -> { throw new IllegalStateException("fixture"); }));
                owner.commandPalette().toggle(); owner.commandPalette().component().queryField().setText("failing");
                dev.jasper.app.palette.PaletteTestSupport.executeSelected(owner.commandPalette().component());
                assertThat(errors).containsExactly("Could not run failing. See the application log for details.");
                assertThat(history.recent()).isEmpty(); assertThat(owner.commandPalette().isOpen()).isFalse();
                assertThat(records).hasSize(1); assertThat(records.getFirst().getThrown()).isInstanceOf(IllegalStateException.class);
            } finally { logger.removeHandler(handler); logger.setUseParentHandlers(parents); }
        });
    }

    @Test void refreshPreservesSelectionButEditingResetsItAndCloseDetachesListeners() throws Exception {
        DesktopTestSupport.edt(() -> {
            try (var history = new CommandHistory(); var owner = owner(history)) {
                var root = install(owner); int baseline = root.getLayeredPane().getComponentListeners().length;
                var first = command("custom.one", () -> {}); var second = command("custom.two", () -> {});
                owner.commands().register(first); owner.commands().register(second);
                owner.commandPalette().toggle(); var palette = owner.commandPalette().component();
                palette.queryField().setText("custom"); PaletteTestSupport.selectRelative(palette, 1);
                var selected = dev.jasper.app.palette.PaletteTestSupport.resultList(palette).getSelectedValue(); first.action().putValue(Command.TITLE, "custom one");
                // Rows are rebuilt as fresh PaletteRow records on every refresh; only their
                // content (matched by row ID) is stable across an edit to an unrelated command.
                assertThat(dev.jasper.app.palette.PaletteTestSupport.resultList(palette).getSelectedValue()).isEqualTo(selected);
                palette.queryField().setText("custom "); assertThat(dev.jasper.app.palette.PaletteTestSupport.resultList(palette).getSelectedIndex()).isZero();
                var changes = new AtomicInteger(); dev.jasper.app.palette.PaletteTestSupport.resultList(palette).getModel().addListDataListener(new ListDataListener() {
                    public void intervalAdded(ListDataEvent e) { changes.incrementAndGet(); }
                    public void intervalRemoved(ListDataEvent e) { changes.incrementAndGet(); }
                    public void contentsChanged(ListDataEvent e) { changes.incrementAndGet(); }
                });
                owner.commandPalette().close(); history.record("custom.one"); first.action().setEnabled(false);
                assertThat(changes.get()).isZero();
                assertThat(root.getLayeredPane().getComponentListeners().length).isLessThan(baseline);
            }
        });
    }

    @Test void smallCardsKeepInputFixedAndSelectedResultsScrollable() throws Exception {
        DesktopTestSupport.edt(() -> {
            try (var owner = owner(new CommandHistory())) {
                var root = install(owner);
                for (int i = 0; i < 5; i++) owner.commands().register(command("custom." + i, () -> {}));
                owner.commandPalette().toggle(); var palette = owner.commandPalette().component();
                palette.queryField().setText("custom"); root.setSize(350, 210); LayoutTestSupport.layoutTree(root);
                root.dispatchEvent(new ComponentEvent(root, ComponentEvent.COMPONENT_RESIZED)); LayoutTestSupport.layoutTree(root);
                assertThat(palette.getWidth()).isLessThanOrEqualTo(318);
                assertThat(palette.queryField().getParent().getHeight()).isEqualTo(com.formdev.flatlaf.util.UIScale.scale(56));
                PaletteTestSupport.selectRelative(palette, 4); LayoutTestSupport.layoutTree(root);
                assertThat(dev.jasper.app.palette.PaletteTestSupport.resultList(palette).getVisibleRect().intersects(dev.jasper.app.palette.PaletteTestSupport.resultList(palette).getCellBounds(4,4))).isTrue();
                assertThat(palette.getHeight()).isLessThan(palette.getPreferredSize().height);
            }
        });
    }

    @Test void queryChangeRevealsResetSelectionInScrolledShortCard() throws Exception {
        DesktopTestSupport.edt(() -> {
            try (var owner = owner(new CommandHistory())) {
                var root = install(owner);
                for (int i = 0; i < 5; i++) owner.commands().register(command("custom." + i, () -> {}));
                owner.commandPalette().toggle(); var palette = owner.commandPalette().component();
                palette.queryField().setText("custom"); root.setSize(350, 210); LayoutTestSupport.layoutTree(root);
                root.dispatchEvent(new ComponentEvent(root, ComponentEvent.COMPONENT_RESIZED)); LayoutTestSupport.layoutTree(root);
                PaletteTestSupport.selectRelative(palette, 4); LayoutTestSupport.layoutTree(root);

                palette.queryField().setText("custom "); LayoutTestSupport.layoutTree(root);

                assertThat(dev.jasper.app.palette.PaletteTestSupport.resultList(palette).getSelectedIndex()).isZero();
                assertThat(dev.jasper.app.palette.PaletteTestSupport.resultList(palette).getVisibleRect().contains(
                    dev.jasper.app.palette.PaletteTestSupport.resultList(palette).getCellBounds(0, 0))).isTrue();
            }
        });
    }

    @Test @org.junit.jupiter.api.condition.DisabledOnOs(org.junit.jupiter.api.condition.OS.WINDOWS)
    void delayedReadinessPreservesOriginThenPaneChangeDismissesAndZoomTitleUpdates() throws Exception {
        var pending = new ArrayDeque<Runnable>(); WindowContent[] owner = new WindowContent[1]; JRootPane[] root = new JRootPane[1];
        try {
            DesktopTestSupport.edt(() -> {
                owner[0] = DesktopTestSupport.content(DesktopTestSupport.launcher(pending)); root[0] = install(owner[0]);
                owner[0].commandPalette().toggle(); owner[0].commandPalette().component().queryField().setText("split");
            });
            pending.remove().run(); DesktopTestSupport.until(() -> owner[0].currentPane().view() != null);
            DesktopTestSupport.edt(() -> {
                var content = owner[0]; var origin = content.currentPane();
                assertThat(content.commandPalette().isOpen()).isTrue(); assertThat(origin.allowLaunchFocus.getAsBoolean()).isFalse();
                assertThat(dev.jasper.app.palette.PaletteTestSupport.resultList(content.commandPalette().component()).getModel().getElementAt(0).id()).startsWith("split_");
                LayoutTestSupport.layoutTree(root[0]); var before = origin.view().getBounds();
                content.commandPalette().dismiss(); assertThat(origin.allowLaunchFocus.getAsBoolean()).isTrue();
                content.commandPalette().toggle(); LayoutTestSupport.layoutTree(root[0]); assertThat(origin.view().getBounds()).isEqualTo(before);
                content.currentTab().split(SplitTree.Axis.RIGHT); assertThat(content.commandPalette().isOpen()).isFalse();
                content.commandPalette().toggle(); content.currentTab().focus(origin); assertThat(content.commandPalette().isOpen()).isFalse();
                content.commandPalette().toggle(); content.commandPalette().component().queryField().setText("zoom");
                content.currentTab().toggleZoom();
                assertThat(content.commandPalette().isOpen()).isTrue();
                assertThat(content.action(ActionId.ZOOM_PANE).getValue(Command.TITLE)).isEqualTo("Restore Pane");
                content.currentTab().closePane(origin); assertThat(content.commandPalette().isOpen()).isFalse();
            });
        } finally { DesktopTestSupport.edt(() -> { if (owner[0] != null) owner[0].close(); }); }
    }

    @Test @org.junit.jupiter.api.condition.DisabledOnOs(org.junit.jupiter.api.condition.OS.WINDOWS)
    void priorFocusRestoresSynchronouslyBeforeFindAndNeverAgain() throws Exception {
        var pending = new ArrayDeque<Runnable>(); WindowContent[] owner = new WindowContent[1];
        var requests = new AtomicInteger(); var ordered = new ArrayList<String>();
        var previousManager = KeyboardFocusManager.getCurrentKeyboardFocusManager();
        JTextField previous = new JTextField() {
            @Override public boolean requestFocusInWindow() { requests.incrementAndGet(); ordered.add("restore"); return true; }
        };
        previous.removeCaretListener((javax.swing.event.CaretListener) previous.getAccessibleContext());
        try {
            DesktopTestSupport.edt(() -> {
                owner[0] = DesktopTestSupport.content(DesktopTestSupport.launcher(pending)); install(owner[0]);
                owner[0].toolbar().add(previous);
                var query = owner[0].commandPalette().component().queryField();
                query.removeCaretListener((javax.swing.event.CaretListener) query.getAccessibleContext());
                var host = new JPanel(); host.add(SwingUtilities.getRootPane(owner[0])); host.addNotify();
                KeyboardFocusManager.setCurrentKeyboardFocusManager(new DefaultKeyboardFocusManager() {
                    @Override public Component getFocusOwner() { return previous; }
                });
            });
            pending.remove().run(); DesktopTestSupport.until(() -> owner[0].currentPane().view() != null);
            DesktopTestSupport.edt(() -> {
                assertThat(SwingUtilities.isDescendingFrom(previous, owner[0])).isTrue();
                // A lightweight component can have a peer in headless tests without creating a native window.
                assertThat(previous.isShowing()).isTrue();
                owner[0].commandPalette().toggle();
                owner[0].commands().register(command("find.fixture", () -> {
                    ordered.add("find"); owner[0].action(ActionId.FIND).actionPerformed(new ActionEvent(owner[0], 0, "find"));
                }));
                owner[0].commandPalette().component().queryField().setText("find.fixture");
                dev.jasper.app.palette.PaletteTestSupport.executeSelected(owner[0].commandPalette().component());
                assertThat(ordered).containsExactly("restore", "find");
                assertThat(owner[0].currentPane().findBar().isVisible()).isTrue();
                assertThat(requests.get()).isEqualTo(1);
            });
            DesktopTestSupport.edt(() -> assertThat(requests.get()).isEqualTo(1));
        } finally {
            DesktopTestSupport.edt(() -> {
                KeyboardFocusManager.setCurrentKeyboardFocusManager(previousManager);
                if (owner[0] != null) { SwingUtilities.getRootPane(owner[0]).getParent().removeNotify(); owner[0].close(); }
            });
        }
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    @org.junit.jupiter.api.condition.DisabledOnOs(org.junit.jupiter.api.condition.OS.WINDOWS)
    void paneTransitionNeverRestoresTheOldPanesFocus(boolean navigate) throws Exception {
        var pending = new ArrayDeque<Runnable>(); WindowContent[] owner = new WindowContent[1];
        var previousManager = KeyboardFocusManager.getCurrentKeyboardFocusManager();
        var oldRequests = new AtomicInteger(); JPanel[] host = new JPanel[1];
        try {
            DesktopTestSupport.edt(() -> {
                owner[0] = DesktopTestSupport.content(DesktopTestSupport.launcher(pending)); install(owner[0]);
            });
            pending.remove().run(); DesktopTestSupport.until(() -> owner[0].currentPane().view() != null);
            DesktopTestSupport.edt(() -> owner[0].currentTab().split(SplitTree.Axis.RIGHT));
            pending.remove().run(); DesktopTestSupport.until(() -> owner[0].currentPane().view() != null);
            DesktopTestSupport.edt(() -> {
                var tab = owner[0].currentTab(); var original = tab.panes().getFirst(); var destination = tab.panes().getLast();
                // Record requests on a real focusable child of the old pane. Like TerminalPane's
                // focus listener, accepting a stale request would select that pane again.
                var prior = new JTextField() {
                    @Override public boolean requestFocusInWindow() {
                        oldRequests.incrementAndGet(); tab.focus(original); return true;
                    }
                };
                prior.removeCaretListener((javax.swing.event.CaretListener) prior.getAccessibleContext());
                // Accessible text location callbacks require a native screen peer, which this
                // lightweight fixture deliberately does not create.
                for (var listener : prior.getComponentListeners()) prior.removeComponentListener(listener);
                original.add(prior, BorderLayout.NORTH);
                var query = owner[0].commandPalette().component().queryField();
                query.removeCaretListener((javax.swing.event.CaretListener) query.getAccessibleContext());
                for (var listener : query.getComponentListeners()) query.removeComponentListener(listener);
                var root = SwingUtilities.getRootPane(owner[0]);
                host[0] = new JPanel(); host[0].add(root); host[0].addNotify(); LayoutTestSupport.layoutTree(root);
                assertThat(original.isShowing()).isTrue(); assertThat(destination.isShowing()).isTrue();
                assertThat(prior.isShowing()).isTrue(); assertThat(original.view().isShowing()).isTrue();
                KeyboardFocusManager.setCurrentKeyboardFocusManager(new DefaultKeyboardFocusManager() {
                    @Override public Component getFocusOwner() { return prior; }
                });
                tab.focus(original); owner[0].commandPalette().toggle();
                if (navigate) tab.navigate(SplitTree.Direction.RIGHT); else tab.focus(destination);
                assertThat(owner[0].commandPalette().isOpen()).isFalse();
                assertThat(oldRequests.get()).isZero();
                assertThat(tab.focusedPane()).isSameAs(destination);
                // Cancellation with a still-valid origin must retain normal synchronous restoration.
                tab.focus(original); owner[0].commandPalette().toggle(); owner[0].commandPalette().dismiss();
                assertThat(oldRequests.get()).isEqualTo(1);
            });
            DesktopTestSupport.edt(() -> assertThat(oldRequests.get()).isEqualTo(1));
        } finally {
            DesktopTestSupport.edt(() -> {
                KeyboardFocusManager.setCurrentKeyboardFocusManager(previousManager);
                if (host[0] != null) host[0].removeNotify();
                if (owner[0] != null) owner[0].close();
            });
        }
    }

}
