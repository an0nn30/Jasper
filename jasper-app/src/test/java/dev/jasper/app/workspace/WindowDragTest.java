package dev.jasper.app.workspace;

import dev.jasper.app.contributions.Contributions;
import dev.jasper.app.contributions.PanelBadge;
import dev.jasper.app.contributions.PanelEntry;
import dev.jasper.app.dock.Slot;
import dev.jasper.app.persistence.UiState;
import dev.jasper.app.testsupport.LayoutTestSupport;
import java.awt.Component;
import java.awt.KeyEventDispatcher;
import java.awt.KeyboardFocusManager;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.swing.AbstractButton;
import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JRootPane;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static dev.jasper.app.workspace.DesktopTestSupport.edt;
import static org.assertj.core.api.Assertions.assertThat;

/** Dock drags in a real window: what a drop does to the panels, the saved layout and the plugins. */
class WindowDragTest {
    @AfterEach void closeWindows() throws Exception { DesktopTestSupport.closeOwners(); }

    /** A connected window in a root pane, with Sessions and Files on the left; events record what the plugins hear. */
    private static final class Fixture {
        final Contributions model;
        final UiState state = UiState.inMemory();
        final WindowContent owner = DesktopTestSupport.content(DesktopTestSupport.launcher(new ArrayDeque<>()));
        final JRootPane root = new JRootPane();
        final List<String> events = new ArrayList<>();
        final PanelEntry sessions, files;

        Fixture() { this(null); }

        /** A window on the same {@link Contributions} as another fixture's: a second window of the same application. */
        Fixture(Fixture sibling) {
            model = sibling == null ? new Contributions() : sibling.model;
            owner.connectContributions(model, state);
            if (sibling != null) {
                sessions = sibling.sessions;
                files = sibling.files;
            } else {
                sessions = model.addPanel("dev.x.sessions", "Sessions", new ImageIcon(), Slot.LEFT_TOP, site -> {
                    site.onVisibility(visible -> events.add("sessions:" + visible));
                    return new JLabel("sessions");
                });
                files = model.addPanel("dev.x.files", "Files", new ImageIcon(), Slot.LEFT_TOP, site -> {
                    site.onVisibility(visible -> events.add("files:" + visible));
                    return new JLabel("files");
                });
            }
            root.setContentPane(owner);
            root.setSize(1200, 800);
            layout();
        }

        void layout() { LayoutTestSupport.layoutTree(root); LayoutTestSupport.layoutTree(root); }

        AbstractButton button(String title) {
            for (ToolStripe stripe : List.of(owner.leftStripe(), owner.rightStripe()))
                for (AbstractButton button : stripe.buttons()) if (title.equals(button.getToolTipText())) return button;
            throw new AssertionError("No stripe button " + title);
        }

        Point center(Component component) {
            Rectangle bounds = SwingUtilities.convertRectangle(component.getParent(), component.getBounds(), owner);
            return new Point((int) bounds.getCenterX(), (int) bounds.getCenterY());
        }

        Point cell(int row, int column) {
            Rectangle w = SwingUtilities.convertRectangle(owner.area().getParent(), owner.area().getBounds(), owner);
            return new Point(w.x + w.width * (2 * column + 1) / 4, w.y + w.height * (2 * row + 1) / 6);
        }

        void mouse(JComponent source, int id, Point inOwner) {
            Point at = SwingUtilities.convertPoint(owner, inOwner, source);
            int modifiers = id == MouseEvent.MOUSE_RELEASED ? 0 : InputEvent.BUTTON1_DOWN_MASK;
            int button = id == MouseEvent.MOUSE_DRAGGED ? MouseEvent.NOBUTTON : MouseEvent.BUTTON1;
            source.dispatchEvent(new MouseEvent(source, id, System.currentTimeMillis(), modifiers, at.x, at.y, 1, false, button));
        }

        /** Press, pass the threshold, move to the point; the caller releases or not. */
        JComponent drag(JComponent source, Point to) {
            Point start = center(source);
            mouse(source, MouseEvent.MOUSE_PRESSED, start);
            mouse(source, MouseEvent.MOUSE_DRAGGED, new Point(start.x + 10, start.y));
            mouse(source, MouseEvent.MOUSE_DRAGGED, to);
            return source;
        }

        void drop(JComponent source, Point to) {
            drag(source, to);
            mouse(source, MouseEvent.MOUSE_RELEASED, to);
            layout();
        }
    }

    @Test void aWorkspaceDropShowsAHiddenPanelTellsItsPluginAndIsSaved() throws Exception {
        edt(() -> {
            var f = new Fixture();
            f.drop(f.button("Files"), f.cell(2, 1));
            assertThat(f.owner.dock().slot("dev.x.files")).hasValue(Slot.BOTTOM_RIGHT);
            assertThat(f.owner.area().content(Slot.BOTTOM_RIGHT)).isNotNull();
            assertThat(f.events).containsExactly("files:true");
            assertThat(f.state.panel("dev.x.files")).hasValue(new UiState.Panel(Slot.BOTTOM_RIGHT, 0, true));
            assertThat(f.owner.rightStripe().buttons()).extracting(AbstractButton::getToolTipText).containsExactly("Files");
        });
    }

    @Test void aClickStillTogglesAndADragDoesNotAlsoToggle() throws Exception {
        edt(() -> {
            var f = new Fixture();
            var files = f.button("Files");
            Point at = f.center(files);
            f.mouse(files, MouseEvent.MOUSE_PRESSED, at);
            f.mouse(files, MouseEvent.MOUSE_RELEASED, at);
            assertThat(f.owner.dock().visible("dev.x.files")).as("a click toggles").isTrue();
            f.layout();
            files = f.button("Files");
            Point home = f.center(files);
            f.drag(files, new Point(home.x + 300, home.y + 300));
            f.mouse(files, MouseEvent.MOUSE_DRAGGED, home);
            f.mouse(files, MouseEvent.MOUSE_RELEASED, home);
            assertThat(f.owner.dockDrag().active()).isFalse();
            assertThat(f.owner.dock().visible("dev.x.files")).as("releasing a drag where the button was does not toggle").isTrue();
            assertThat(f.owner.dock().ids(Slot.LEFT_TOP)).containsExactly("dev.x.sessions", "dev.x.files");
            assertThat(f.events).containsExactly("files:true");
        });
    }

    @Test void aStripeDropMovesAShowingPanelWithoutTellingItsPlugin() throws Exception {
        edt(() -> {
            var f = new Fixture();
            f.model.requestPanel(new Contributions.PanelRequest(f.owner.id(), "dev.x.sessions", Contributions.PanelRequest.Op.SHOW));
            f.layout();
            Rectangle stripe = SwingUtilities.convertRectangle(f.owner.rightStripe().getParent(), f.owner.rightStripe().getBounds(), f.owner);
            Point emptyRightTop = new Point(stripe.x + stripe.width / 2, stripe.y + 10);
            f.drop(f.button("Sessions"), emptyRightTop);
            assertThat(f.owner.dock().slot("dev.x.sessions")).as("into the empty right stripe's drop space").hasValue(Slot.RIGHT_TOP);
            assertThat(f.owner.dock().visible("dev.x.sessions")).isTrue();
            assertThat(f.events).as("a move is not a change of visibility").containsExactly("sessions:true");
        });
    }

    @Test void theHeaderDragsEvenWithTheStripesHidden() throws Exception {
        edt(() -> {
            var f = new Fixture();
            f.model.requestPanel(new Contributions.PanelRequest(f.owner.id(), "dev.x.sessions", Contributions.PanelRequest.Op.SHOW));
            f.owner.setRailVisible(false);
            f.layout();
            f.drop(f.owner.area().headerOf(Slot.LEFT_TOP).titleLabel(), f.cell(0, 1));
            assertThat(f.owner.dock().slot("dev.x.sessions")).hasValue(Slot.RIGHT_TOP);
            assertThat(f.owner.area().headerOf(Slot.RIGHT_TOP).titleLabel().getText()).isEqualTo("Sessions");
        });
    }

    @Test void aPluginUnloadingOrTheWindowClosingMidDragCancelsIt() throws Exception {
        edt(() -> {
            var f = new Fixture();
            f.drag(f.button("Sessions"), f.cell(1, 1));
            assertThat(f.owner.dockDrag().active()).isTrue();
            f.files.close();
            assertThat(f.owner.dockDrag().active()).as("a plugin unloading").isFalse();
            assertThat(f.owner.dockDrag().overlay().getParent()).isNull();
            f.layout();
            f.drag(f.button("Sessions"), f.cell(1, 1));
            assertThat(f.owner.dockDrag().active()).isTrue();
            f.owner.close();
            assertThat(f.owner.dockDrag().active()).as("the window closing").isFalse();
            assertThat(f.owner.dock().slot("dev.x.sessions")).hasValue(Slot.LEFT_TOP);
        });
    }

    @Test void theWindowDeactivatingMidDragCancelsItAndNothingDrops() throws Exception {
        edt(() -> {
            var previous = KeyboardFocusManager.getCurrentKeyboardFocusManager();
            var keys = new CommandPaletteShortcutsTest.RecordingFocusManager();
            KeyboardFocusManager.setCurrentKeyboardFocusManager(keys);
            try {
                var f = new Fixture();
                List<KeyEventDispatcher> before = List.copyOf(keys.dispatchers);
                var source = f.drag(f.button("Files"), f.cell(2, 1));
                assertThat(f.owner.dockDrag().target()).as("a release here would drop").isPresent();
                assertThat(keys.dispatchers).as("the drag's Esc dispatcher").hasSize(before.size() + 1);
                // TerminalWindow on windowDeactivated: a modal dialog opened, whose window takes the release, or
                // another application took focus.
                f.owner.setActive(false);
                assertThat(f.owner.dockDrag().active()).isFalse();
                assertThat(f.owner.dockDrag().overlay().getParent()).as("no guides are left").isNull();
                assertThat(f.owner.leftStripe().dragging()).isFalse();
                assertThat(f.owner.rightStripe().dragging()).isFalse();
                assertThat(keys.dispatchers).as("no Esc dispatcher is left").containsExactlyElementsOf(before);
                f.mouse(source, MouseEvent.MOUSE_RELEASED, f.cell(2, 1));
                assertThat(f.owner.dock().slot("dev.x.files")).as("nothing dropped").hasValue(Slot.LEFT_TOP);
                assertThat(f.owner.dock().visible("dev.x.files")).isFalse();
                assertThat(f.events).isEmpty();
            } finally {
                KeyboardFocusManager.setCurrentKeyboardFocusManager(previous);
            }
        });
    }

    @Test void turningTheRailOnOrOffMidDragCancelsIt() throws Exception {
        edt(() -> {
            var f = new Fixture();
            f.model.requestPanel(new Contributions.PanelRequest(f.owner.id(), "dev.x.sessions", Contributions.PanelRequest.Op.SHOW));
            f.layout();
            for (boolean rail : new boolean[] {false, true}) {
                var title = f.owner.area().headerOf(Slot.LEFT_TOP).titleLabel();
                f.drag(title, f.cell(0, 1));
                assertThat(f.owner.dockDrag().active()).isTrue();
                assertThat(f.owner.leftStripe().dragging()).as("rail %s: the stripes show the drag only while they show", rail)
                    .isEqualTo(!rail);
                f.owner.setRailVisible(rail);
                assertThat(f.owner.dockDrag().active()).as("View > Rail %s ends the drag", rail ? "on" : "off").isFalse();
                assertThat(f.owner.dockDrag().overlay().getParent()).isNull();
                assertThat(f.owner.leftStripe().dragging()).isFalse();
                assertThat(f.owner.rightStripe().dragging()).isFalse();
                f.mouse(title, MouseEvent.MOUSE_RELEASED, f.cell(0, 1));
                assertThat(f.owner.dock().slot("dev.x.sessions")).as("nothing dropped").hasValue(Slot.LEFT_TOP);
                f.layout();
            }
            assertThat(f.events).containsExactly("sessions:true");
        });
    }

    @Test void aBadgedButtonStillDragsAndLiftsOutAndItsGhostIsThePlainIcon() throws Exception {
        edt(() -> {
            var f = new Fixture();
            f.files.setBadge(java.util.Optional.of(new PanelBadge(2, PanelBadge.Tone.ACCENT, "2 unread notifications")));
            f.layout();
            AbstractButton files = f.button("Files (2 unread notifications)");
            assertThat(files.getIcon()).isInstanceOf(BadgedIcon.class);
            f.drag(files, f.cell(2, 1));
            assertThat(f.owner.dockDrag().dragged()).isEqualTo("dev.x.files");
            assertThat(files.getWidth()).as("lifted out of the stripe").isZero();
            assertThat(f.owner.dockDrag().icons.apply("dev.x.files")).as("the ghost has no dot").isSameAs(f.files.icon());
            f.mouse(files, MouseEvent.MOUSE_RELEASED, f.cell(2, 1));
            f.layout();
            assertThat(f.owner.dock().slot("dev.x.files")).hasValue(Slot.BOTTOM_RIGHT);
            AbstractButton moved = f.button("Files (2 unread notifications)");
            assertThat(moved.getParent()).as("the badge goes with it").isSameAs(f.owner.rightStripe());
            assertThat(moved.getIcon()).isInstanceOfSatisfying(BadgedIcon.class, badged -> assertThat(badged.base()).isSameAs(f.files.icon()));
        });
    }

    @Test void aBadgeChangeMidDragUpdatesButtonsInPlaceAndTheDragDropsAsIfNothingHappened() throws Exception {
        edt(() -> {
            var f = new Fixture();
            PanelEntry notes = f.model.addPanel("dev.x.notes", "Notes", new ImageIcon(), Slot.RIGHT_TOP, site -> new JLabel("notes"));
            f.layout();
            AbstractButton files = f.button("Files");
            AbstractButton notesButton = f.button("Notes");
            Point overNotes = f.center(notesButton);
            f.drag(files, overNotes);
            assertThat(f.owner.dockDrag().active()).isTrue();
            assertThat(files.getWidth()).as("lifted out of the stripe").isZero();
            Optional<Rectangle> leftGap = f.owner.leftStripe().gapBounds(), rightGap = f.owner.rightStripe().gapBounds();
            assertThat(rightGap).as("the gap is open over the right stripe").isPresent();

            f.files.setBadge(Optional.of(new PanelBadge(2, PanelBadge.Tone.ACCENT, "2 unread notifications")));
            notes.setBadge(Optional.of(new PanelBadge(1, PanelBadge.Tone.ERROR, "1 unread notification, 1 error")));

            assertThat(f.owner.dockDrag().active()).as("a badge change is not a render").isTrue();
            assertThat(f.owner.dockDrag().dragged()).isEqualTo("dev.x.files");
            assertThat(f.owner.leftStripe().buttons()).as("the same buttons").contains(files);
            assertThat(f.owner.rightStripe().buttons()).contains(notesButton);
            assertThat(files.getWidth()).as("still lifted out").isZero();
            assertThat(files.getIcon()).as("the dragged window's own badge shows").isInstanceOf(BadgedIcon.class);
            assertThat(files.getToolTipText()).isEqualTo("Files (2 unread notifications)");
            assertThat(notesButton.getIcon()).as("another window's badge shows too").isInstanceOf(BadgedIcon.class);
            assertThat(f.owner.leftStripe().gapBounds()).isEqualTo(leftGap);
            assertThat(f.owner.rightStripe().gapBounds()).isEqualTo(rightGap);

            f.mouse(files, MouseEvent.MOUSE_RELEASED, overNotes);
            f.layout();
            assertThat(f.owner.dockDrag().active()).isFalse();
            assertThat(f.owner.dock().slot("dev.x.files")).as("the drop happened").hasValue(Slot.RIGHT_TOP);
            assertThat(f.button("Files (2 unread notifications)").getParent()).isSameAs(f.owner.rightStripe());
        });
    }

    @Test void aBadgeChangeDoesNotCancelADragInAnyWindow() throws Exception {
        edt(() -> {
            var first = new Fixture();
            var second = new Fixture(first);
            first.layout();
            second.layout();
            AbstractButton inFirst = first.button("Files"), inSecond = second.button("Sessions");
            first.drag(inFirst, first.cell(2, 1));
            second.drag(inSecond, second.cell(2, 1));
            assertThat(first.owner.dockDrag().active()).isTrue();
            assertThat(second.owner.dockDrag().active()).isTrue();

            first.files.setBadge(Optional.of(new PanelBadge(3, PanelBadge.Tone.ACCENT, "3 unread notifications")));
            second.sessions.setBadge(Optional.of(new PanelBadge(1, PanelBadge.Tone.ERROR, "1 unread notification, 1 error")));

            assertThat(first.owner.dockDrag().active()).as("the window that heard the change").isTrue();
            assertThat(second.owner.dockDrag().active()).as("and the other window").isTrue();
            assertThat(first.owner.leftStripe().buttons()).contains(inFirst);
            assertThat(second.owner.leftStripe().buttons()).contains(inSecond);
            assertThat(inFirst.getWidth()).isZero();
            assertThat(inSecond.getWidth()).isZero();
            assertThat(second.button("Files (3 unread notifications)").getIcon()).as("every window's stripes show it")
                .isInstanceOf(BadgedIcon.class);
            assertThat(first.button("Sessions (1 unread notification, 1 error)").getIcon()).isInstanceOf(BadgedIcon.class);
            first.mouse(inFirst, MouseEvent.MOUSE_RELEASED, first.cell(2, 1));
            second.mouse(inSecond, MouseEvent.MOUSE_RELEASED, second.cell(2, 1));
            first.layout();
            second.layout();
            assertThat(first.owner.dock().slot("dev.x.files")).hasValue(Slot.BOTTOM_RIGHT);
            assertThat(second.owner.dock().slot("dev.x.sessions")).hasValue(Slot.BOTTOM_RIGHT);
        });
    }

    @Test void aRailActionDoesNotDrag() throws Exception {
        edt(() -> {
            var f = new Fixture();
            f.model.addAction("dev.x.open", "Open Manager", new ImageIcon(), List.of(), java.util.Optional.empty(), invocation -> { });
            f.model.addRailAction("dev.x.open");
            f.layout();
            f.drag(f.button("Open Manager"), f.cell(0, 1));
            assertThat(f.owner.dockDrag().active()).isFalse();
        });
    }
}
