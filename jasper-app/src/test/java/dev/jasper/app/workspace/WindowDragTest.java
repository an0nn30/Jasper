package dev.jasper.app.workspace;

import dev.jasper.app.contributions.ActionEntry;
import dev.jasper.app.contributions.Contributions;
import dev.jasper.app.contributions.PanelBadge;
import dev.jasper.app.contributions.PanelEntry;
import dev.jasper.app.dock.DockDropResolver;
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
            for (DockStripe stripe : List.of(owner.leftStripe(), owner.rightStripe()))
                for (AbstractButton button : stripe.buttons()) if (title.equals(button.getToolTipText())) return button;
            throw new AssertionError("No stripe button " + title);
        }

        Point center(Component component) {
            Rectangle bounds = SwingUtilities.convertRectangle(component.getParent(), component.getBounds(), owner);
            return new Point((int) bounds.getCenterX(), (int) bounds.getCenterY());
        }

        Rectangle workspace() { return SwingUtilities.convertRectangle(owner.area().getParent(), owner.area().getBounds(), owner); }

        /** A point well inside the slot's workspace zone. */
        Point zone(Slot slot) { return DockTestSupport.zone(workspace(), slot); }

        /**
         * Near the top of the empty right stripe's place: the workspace's outer stripe-wide edge while the stripe is
         * hidden, which is exactly where the stripe stands once it shows.
         */
        Point rightRailTop() {
            Rectangle workspace = workspace();
            return new Point(workspace.x + workspace.width - owner.rightStripe().getPreferredSize().width / 2, workspace.y + 10);
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
            f.drop(f.button("Files"), f.zone(Slot.BOTTOM_RIGHT));
            assertThat(f.owner.dock().slot("dev.x.files")).hasValue(Slot.BOTTOM_RIGHT);
            assertThat(f.owner.area().content(Slot.BOTTOM_RIGHT)).isNotNull();
            assertThat(f.events).containsExactly("files:true");
            assertThat(f.state.panel("dev.x.files")).hasValue(new UiState.Panel(Slot.BOTTOM_RIGHT, 0, true));
            assertThat(f.owner.rightStripe().buttons()).extracting(AbstractButton::getToolTipText).containsExactly("Files");
        });
    }

    /** The slot's frame, in the owner's coordinates. */
    private static Rectangle frame(Fixture f, Slot slot) {
        var host = f.owner.area().content(slot).getParent();
        return SwingUtilities.convertRectangle(host.getParent(), host.getBounds(), f.owner);
    }

    @Test void aDropInTheLowerHalfOfTheLeftBandStacksTheWindowUnderTheShowingOne() throws Exception {
        edt(() -> {
            var f = new Fixture();
            f.model.requestPanel(new Contributions.PanelRequest(f.owner.id(), "dev.x.sessions", Contributions.PanelRequest.Op.SHOW));
            f.layout();
            Rectangle workspace = f.workspace();
            Point lowerLeft = new Point(workspace.x + 130, workspace.y + workspace.height * 3 / 4);
            f.drag(f.button("Files"), lowerLeft);
            assertThat(f.owner.dockDrag().target()).as("the lower half of the left band, not the bottom area")
                .hasValue(DockDropResolver.Target.workspace(Slot.LEFT_BOTTOM));
            f.mouse(f.button("Files"), MouseEvent.MOUSE_RELEASED, lowerLeft);
            f.layout();
            assertThat(f.owner.dock().slot("dev.x.files")).hasValue(Slot.LEFT_BOTTOM);
            assertThat(f.owner.dock().slot("dev.x.sessions")).hasValue(Slot.LEFT_TOP);
            assertThat(f.owner.dock().visible("dev.x.files")).isTrue();
            assertThat(f.owner.dock().visible("dev.x.sessions")).isTrue();
            assertThat(f.owner.area().content(Slot.LEFT_TOP)).isNotNull();
            assertThat(f.owner.area().content(Slot.LEFT_BOTTOM)).isNotNull();
            Rectangle upper = frame(f, Slot.LEFT_TOP), lower = frame(f, Slot.LEFT_BOTTOM);
            assertThat(upper.y + upper.height).as("the frames are stacked, not overlapping").isLessThanOrEqualTo(lower.y);
            assertThat(lower.y).as("the lower frame is the lower half").isGreaterThanOrEqualTo(workspace.y + workspace.height / 2);
            assertThat(lower.y + lower.height).isLessThanOrEqualTo(workspace.y + workspace.height);
            assertThat(lower.x).as("one column").isEqualTo(upper.x);
            assertThat(lower.width).isEqualTo(upper.width);
            assertThat(f.events).containsExactly("sessions:true", "files:true");
        });
    }

    @Test void theGuideForALowerHalfShowsThatHalfEvenWhenTheWindowWillFillTheSide() throws Exception {
        edt(() -> {
            var f = new Fixture();
            f.model.requestPanel(new Contributions.PanelRequest(f.owner.id(), "dev.x.sessions", Contributions.PanelRequest.Op.SHOW));
            f.layout();
            Rectangle wholeSide = frame(f, Slot.LEFT_TOP);
            assertThat(wholeSide.height).as("alone on the left, Sessions fills the side").isEqualTo(f.workspace().height);

            var sessions = f.drag(f.button("Sessions"), f.zone(Slot.LEFT_BOTTOM));
            assertThat(f.owner.dockDrag().target()).hasValue(DockDropResolver.Target.workspace(Slot.LEFT_BOTTOM));
            Rectangle guide = SwingUtilities.convertRectangle(f.root.getLayeredPane(), f.owner.dockDrag().overlay().highlight(), f.owner);
            assertThat(guide.height).as("the guide is the lower half, not the whole side").isLessThan(wholeSide.height * 6 / 10);
            assertThat(guide.y).isGreaterThan(wholeSide.y + wholeSide.height * 4 / 10);
            assertThat(guide.y + guide.height).as("down to the side's foot").isEqualTo(wholeSide.y + wholeSide.height);
            assertThat(guide.x).isEqualTo(wholeSide.x);
            assertThat(guide.width).isEqualTo(wholeSide.width);
            assertThat(f.owner.area().content(Slot.LEFT_BOTTOM)).as("the real layout does not reflow").isNull();
            assertThat(frame(f, Slot.LEFT_TOP)).as("Sessions still fills the side while the guide shows").isEqualTo(wholeSide);

            f.mouse(sessions, MouseEvent.MOUSE_RELEASED, f.zone(Slot.LEFT_BOTTOM));
            f.layout();
            assertThat(f.owner.dock().slot("dev.x.sessions")).hasValue(Slot.LEFT_BOTTOM);
            assertThat(f.owner.dock().visible("dev.x.sessions")).isTrue();
            assertThat(f.owner.area().content(Slot.LEFT_TOP)).as("nothing left above it").isNull();
            assertThat(frame(f, Slot.LEFT_BOTTOM)).as("alone in its slot, the window still fills the whole side").isEqualTo(wholeSide);

            f.model.requestPanel(new Contributions.PanelRequest(f.owner.id(), "dev.x.files", Contributions.PanelRequest.Op.SHOW));
            f.layout();
            assertThat(frame(f, Slot.LEFT_BOTTOM)).as("a window above splits the side: the lower frame is the guide that showed")
                .isEqualTo(guide);
            assertThat(f.events).containsExactly("sessions:true", "files:true");
        });
    }

    @Test void theGuideForTheBottomAreasRightHalfShowsThatHalfWhenAHiddenPanelWillFillTheBottomArea() throws Exception {
        edt(() -> {
            var f = new Fixture();
            f.model.addPanel("dev.x.logs", "Logs", new ImageIcon(), Slot.BOTTOM_LEFT, site -> new JLabel("logs"));
            Rectangle workspace = f.workspace();
            var files = f.drag(f.button("Files"), f.zone(Slot.BOTTOM_RIGHT));
            assertThat(f.owner.dockDrag().target()).hasValue(DockDropResolver.Target.workspace(Slot.BOTTOM_RIGHT));
            Rectangle guide = SwingUtilities.convertRectangle(f.root.getLayeredPane(), f.owner.dockDrag().overlay().highlight(), f.owner);
            assertThat(guide.width).as("the right half of the bottom area, not all of it").isLessThan(workspace.width * 6 / 10);
            assertThat(guide.x).isGreaterThan(workspace.x + workspace.width * 4 / 10);
            assertThat(guide.x + guide.width).as("out to where the right stripe shows once Files is on it")
                .isEqualTo(workspace.x + workspace.width - f.owner.rightStripe().getPreferredSize().width);
            assertThat(f.owner.area().content(Slot.BOTTOM_RIGHT)).as("the real layout does not reflow").isNull();

            f.mouse(files, MouseEvent.MOUSE_RELEASED, f.zone(Slot.BOTTOM_RIGHT));
            f.layout();
            assertThat(f.owner.dock().slot("dev.x.files")).hasValue(Slot.BOTTOM_RIGHT);
            assertThat(f.owner.rightStripe().isVisible()).isTrue();
            workspace = f.workspace();
            Rectangle alone = frame(f, Slot.BOTTOM_RIGHT);
            assertThat(alone.x).as("alone in the bottom area, Files fills its whole width").isEqualTo(workspace.x);
            assertThat(alone.width).isEqualTo(workspace.width);

            f.model.requestPanel(new Contributions.PanelRequest(f.owner.id(), "dev.x.logs", Contributions.PanelRequest.Op.SHOW));
            f.layout();
            assertThat(frame(f, Slot.BOTTOM_RIGHT)).as("with Logs beside it, Files is the half the guide showed").isEqualTo(guide);
        });
    }

    @Test void withABottomAreaShowingASideSplitsAtHalfOfTheSidePanelsVisibleHeight() throws Exception {
        edt(() -> {
            var f = new Fixture();
            f.model.addPanel("dev.x.logs", "Logs", new ImageIcon(), Slot.BOTTOM_LEFT, site -> new JLabel("logs"));
            f.model.requestPanel(new Contributions.PanelRequest(f.owner.id(), "dev.x.sessions", Contributions.PanelRequest.Op.SHOW));
            f.model.requestPanel(new Contributions.PanelRequest(f.owner.id(), "dev.x.logs", Contributions.PanelRequest.Op.SHOW));
            f.layout();
            Rectangle workspace = f.workspace();
            Rectangle sessionsFrame = frame(f, Slot.LEFT_TOP), logsFrame = frame(f, Slot.BOTTOM_LEFT);
            int visibleHalf = (logsFrame.y - workspace.y) / 2, workspaceHalf = workspace.height / 2;
            assertThat(visibleHalf).as("the side panel is shorter than the workspace: its half is higher").isLessThan(workspaceHalf - 20);
            int down = (visibleHalf + workspaceHalf) / 2;
            assertThat(workspace.y + down).as("the point is over the side panel").isLessThan(sessionsFrame.y + sessionsFrame.height);
            Point lowerHalfOfTheSidePanel = new Point(workspace.x + 130, workspace.y + down);
            f.drag(f.button("Files"), lowerHalfOfTheSidePanel);
            assertThat(f.owner.dockDrag().target()).as("below the side panel's half, above the workspace's")
                .hasValue(DockDropResolver.Target.workspace(Slot.LEFT_BOTTOM));
            f.mouse(f.button("Files"), MouseEvent.MOUSE_RELEASED, lowerHalfOfTheSidePanel);
            f.layout();
            assertThat(f.owner.dock().slot("dev.x.files")).hasValue(Slot.LEFT_BOTTOM);
            assertThat(f.owner.dock().visible("dev.x.files")).isTrue();
            assertThat(f.owner.dock().visible("dev.x.sessions")).isTrue();
            assertThat(f.owner.dock().visible("dev.x.logs")).as("the bottom area stays").isTrue();
            Rectangle upper = frame(f, Slot.LEFT_TOP), lower = frame(f, Slot.LEFT_BOTTOM);
            assertThat(upper.y + upper.height).as("both left frames show, the lower one below").isLessThanOrEqualTo(lower.y);
            assertThat(lower.y + lower.height).as("above the bottom area").isLessThanOrEqualTo(frame(f, Slot.BOTTOM_LEFT).y);
        });
    }

    @Test void aStripeDropOfAHiddenPanelPreviewsNoFrameAndAVisibleOneDoes() throws Exception {
        edt(() -> {
            var f = new Fixture();
            f.model.requestPanel(new Contributions.PanelRequest(f.owner.id(), "dev.x.sessions", Contributions.PanelRequest.Op.SHOW));
            f.layout();
            Point emptyRightTop = f.rightRailTop();

            var files = f.drag(f.button("Files"), emptyRightTop);
            assertThat(f.owner.dockDrag().target()).hasValue(DockDropResolver.Target.stripe(Slot.RIGHT_TOP, 0));
            assertThat(f.owner.dock().visible("dev.x.files")).isFalse();
            assertThat(f.owner.dockDrag().overlay().highlight()).as("Files stays hidden on a stripe drop: nothing to preview").isNull();
            assertThat(f.owner.dockDrag().overlay().placeholder()).as("the stripe placeholder shows").isNotNull();
            f.mouse(files, MouseEvent.MOUSE_RELEASED, emptyRightTop);
            f.layout();
            assertThat(f.owner.dock().slot("dev.x.files")).hasValue(Slot.RIGHT_TOP);
            assertThat(f.owner.dock().visible("dev.x.files")).as("as previewed, it did not appear").isFalse();
            assertThat(f.events).containsExactly("sessions:true");

            var sessions = f.drag(f.button("Sessions"), emptyRightTop);
            assertThat(f.owner.dockDrag().target()).hasValue(DockDropResolver.Target.stripe(Slot.RIGHT_TOP, 0));
            assertThat(f.owner.dock().visible("dev.x.sessions")).isTrue();
            assertThat(f.owner.dockDrag().overlay().highlight()).as("a showing panel moves with its frame").isNotNull();
            assertThat(f.owner.dockDrag().overlay().placeholder()).isNotNull();
            f.mouse(sessions, MouseEvent.MOUSE_RELEASED, emptyRightTop);
        });
    }

    @Test void anEmptyHiddenStripeTakesADropAtItsEdgeWithoutShowingAndShowsOnceSomethingLands() throws Exception {
        edt(() -> {
            var f = new Fixture();
            assertThat(f.owner.rightStripe().isVisible()).as("nothing on the right").isFalse();
            Rectangle terminal = SwingUtilities.convertRectangle(f.owner.workspace().getParent(), f.owner.workspace().getBounds(), f.owner);
            Rectangle workspace = f.workspace();
            int width = f.owner.rightStripe().getPreferredSize().width;
            Point edge = f.rightRailTop();

            var files = f.drag(f.button("Files"), edge);
            assertThat(f.owner.dockDrag().target()).hasValue(DockDropResolver.Target.stripe(Slot.RIGHT_TOP, 0));
            assertThat(f.owner.rightStripe().isVisible()).as("the stripe itself stays hidden while it is a target").isFalse();
            Rectangle placeholder = SwingUtilities.convertRectangle(f.root.getLayeredPane(), f.owner.dockDrag().overlay().placeholder(), f.owner);
            assertThat(placeholder.x).as("the guide sits in the stripe's place at the edge").isGreaterThanOrEqualTo(workspace.x + workspace.width - width);
            assertThat(placeholder.x + placeholder.width).isLessThanOrEqualTo(workspace.x + workspace.width);
            f.layout();
            assertThat(SwingUtilities.convertRectangle(f.owner.workspace().getParent(), f.owner.workspace().getBounds(), f.owner))
                .as("the terminal neither moves nor resizes for the drag").isEqualTo(terminal);
            f.mouse(files, MouseEvent.MOUSE_RELEASED, edge);
            f.layout();
            assertThat(f.owner.dock().slot("dev.x.files")).hasValue(Slot.RIGHT_TOP);
            assertThat(f.owner.rightStripe().isVisible()).as("something landed: the stripe shows").isTrue();
            assertThat(f.owner.rightStripe().buttons()).extracting(AbstractButton::getToolTipText).containsExactly("Files");

            Rectangle left = SwingUtilities.convertRectangle(f.owner.leftStripe().getParent(), f.owner.leftStripe().getBounds(), f.owner);
            f.drop(f.button("Files"), new Point(left.x + left.width / 2, left.y + 10));
            assertThat(f.owner.dock().slot("dev.x.files")).hasValue(Slot.LEFT_TOP);
            assertThat(f.owner.rightStripe().isVisible()).as("emptied again, the stripe hides").isFalse();
            assertThat(f.events).isEmpty();
        });
    }

    @Test void aResizeMidDragMovesTheHiddenStripesPlaceToTheNewEdge() throws Exception {
        edt(() -> {
            var f = new Fixture();
            var files = f.drag(f.button("Files"), f.rightRailTop());
            assertThat(f.owner.dockDrag().target()).hasValue(DockDropResolver.Target.stripe(Slot.RIGHT_TOP, 0));
            f.root.setSize(1100, 800);
            f.layout();
            f.owner.dispatchEvent(new java.awt.event.ComponentEvent(f.owner, java.awt.event.ComponentEvent.COMPONENT_RESIZED));
            Point edge = f.rightRailTop();
            f.mouse(files, MouseEvent.MOUSE_DRAGGED, edge);
            assertThat(f.owner.dockDrag().target()).as("the new edge").hasValue(DockDropResolver.Target.stripe(Slot.RIGHT_TOP, 0));
            Rectangle workspace = f.workspace();
            Rectangle placeholder = SwingUtilities.convertRectangle(f.root.getLayeredPane(), f.owner.dockDrag().overlay().placeholder(), f.owner);
            assertThat(placeholder.x + placeholder.width).isLessThanOrEqualTo(workspace.x + workspace.width);
            assertThat(placeholder.x).isGreaterThanOrEqualTo(workspace.x + workspace.width - f.owner.rightStripe().getPreferredSize().width);
            f.mouse(files, MouseEvent.MOUSE_RELEASED, edge);
            f.layout();
            assertThat(f.owner.dock().slot("dev.x.files")).hasValue(Slot.RIGHT_TOP);
        });
    }

    @Test void anEmptyHiddenLeftStripeTakesADropAtTheLeftEdgeAndTheGuideAllowsForItShowing() throws Exception {
        edt(() -> {
            var f = new Fixture();
            f.owner.dock().move("dev.x.sessions", Slot.RIGHT_TOP, 0);
            f.owner.dock().move("dev.x.files", Slot.RIGHT_TOP, 1);
            f.model.requestPanel(new Contributions.PanelRequest(f.owner.id(), "dev.x.sessions", Contributions.PanelRequest.Op.SHOW));
            f.layout();
            assertThat(f.owner.leftStripe().isVisible()).as("everything is on the right").isFalse();
            Rectangle workspace = f.workspace();
            Point edge = new Point(workspace.x + f.owner.leftStripe().getPreferredSize().width / 2, workspace.y + 10);
            var sessions = f.drag(f.button("Sessions"), edge);
            assertThat(f.owner.dockDrag().target()).hasValue(DockDropResolver.Target.stripe(Slot.LEFT_TOP, 0));
            Rectangle guide = SwingUtilities.convertRectangle(f.root.getLayeredPane(), f.owner.dockDrag().overlay().highlight(), f.owner);
            f.mouse(sessions, MouseEvent.MOUSE_RELEASED, edge);
            f.layout();
            assertThat(f.owner.leftStripe().isVisible()).isTrue();
            assertThat(f.owner.dock().slot("dev.x.sessions")).hasValue(Slot.LEFT_TOP);
            assertThat(frame(f, Slot.LEFT_TOP)).as("the guide allowed for the left stripe showing").isEqualTo(guide);
        });
    }

    @Test void theGuideAllowsForTheStripeADropEmpties() throws Exception {
        edt(() -> {
            var f = new Fixture();
            f.drop(f.button("Files"), f.rightRailTop());
            f.model.requestPanel(new Contributions.PanelRequest(f.owner.id(), "dev.x.files", Contributions.PanelRequest.Op.SHOW));
            f.layout();
            assertThat(f.owner.rightStripe().isVisible()).as("Files alone on the right").isTrue();
            var files = f.drag(f.button("Files"), f.zone(Slot.BOTTOM_LEFT));
            assertThat(f.owner.dockDrag().target()).hasValue(DockDropResolver.Target.workspace(Slot.BOTTOM_LEFT));
            Rectangle guide = SwingUtilities.convertRectangle(f.root.getLayeredPane(), f.owner.dockDrag().overlay().highlight(), f.owner);
            f.mouse(files, MouseEvent.MOUSE_RELEASED, f.zone(Slot.BOTTOM_LEFT));
            f.layout();
            assertThat(f.owner.rightStripe().isVisible()).as("Files left it empty").isFalse();
            assertThat(frame(f, Slot.BOTTOM_LEFT)).as("the guide allowed for the stripe hiding").isEqualTo(guide);
        });
    }

    @Test void aCancelledDragLeavesTheEmptyStripeHiddenAndWithTheRailOffTheEdgeIsWorkspace() throws Exception {
        edt(() -> {
            var f = new Fixture();
            Point edge = f.rightRailTop();
            var files = f.drag(f.button("Files"), edge);
            assertThat(f.owner.dockDrag().target()).hasValue(DockDropResolver.Target.stripe(Slot.RIGHT_TOP, 0));
            f.owner.dockDrag().cancel();
            f.mouse(files, MouseEvent.MOUSE_RELEASED, edge);
            f.layout();
            assertThat(f.owner.rightStripe().isVisible()).isFalse();
            assertThat(f.owner.rightStripe().dragging()).isFalse();
            assertThat(f.owner.rightStripe().getBounds().isEmpty()).as("its place lasted only for the drag").isTrue();
            assertThat(f.owner.dock().slot("dev.x.files")).as("nothing dropped").hasValue(Slot.LEFT_TOP);

            f.model.requestPanel(new Contributions.PanelRequest(f.owner.id(), "dev.x.sessions", Contributions.PanelRequest.Op.SHOW));
            f.owner.setRailVisible(false);
            f.layout();
            var title = f.drag(f.owner.area().headerOf(Slot.LEFT_TOP).titleLabel(), f.rightRailTop());
            assertThat(f.owner.dockDrag().target()).as("View > Rail off: no stripe takes drops, the edge docks and shows")
                .hasValue(DockDropResolver.Target.workspace(Slot.RIGHT_TOP));
            assertThat(f.owner.rightStripe().dragging()).isFalse();
            f.mouse(title, MouseEvent.MOUSE_RELEASED, f.rightRailTop());
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
            Point emptyRightTop = f.rightRailTop();
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
            f.drop(f.owner.area().headerOf(Slot.LEFT_TOP).titleLabel(), f.zone(Slot.RIGHT_TOP));
            assertThat(f.owner.dock().slot("dev.x.sessions")).hasValue(Slot.RIGHT_TOP);
            assertThat(f.owner.area().headerOf(Slot.RIGHT_TOP).titleLabel().getText()).isEqualTo("Sessions");
        });
    }

    @Test void aPluginUnloadingOrTheWindowClosingMidDragCancelsIt() throws Exception {
        edt(() -> {
            var f = new Fixture();
            f.drag(f.button("Sessions"), f.zone(Slot.RIGHT_BOTTOM));
            assertThat(f.owner.dockDrag().active()).isTrue();
            f.files.close();
            assertThat(f.owner.dockDrag().active()).as("a plugin unloading").isFalse();
            assertThat(f.owner.dockDrag().overlay().getParent()).isNull();
            f.layout();
            f.drag(f.button("Sessions"), f.zone(Slot.RIGHT_BOTTOM));
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
                var source = f.drag(f.button("Files"), f.zone(Slot.BOTTOM_RIGHT));
                assertThat(f.owner.dockDrag().target()).as("a release here would drop").isPresent();
                assertThat(keys.dispatchers).as("the drag's Esc dispatcher").hasSize(before.size() + 1);
                // WindowFrame on windowDeactivated: a modal dialog opened, whose window takes the release, or
                // another application took focus.
                f.owner.setActive(false);
                assertThat(f.owner.dockDrag().active()).isFalse();
                assertThat(f.owner.dockDrag().overlay().getParent()).as("no guides are left").isNull();
                assertThat(f.owner.leftStripe().dragging()).isFalse();
                assertThat(f.owner.rightStripe().dragging()).isFalse();
                assertThat(keys.dispatchers).as("no Esc dispatcher is left").containsExactlyElementsOf(before);
                f.mouse(source, MouseEvent.MOUSE_RELEASED, f.zone(Slot.BOTTOM_RIGHT));
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
                f.drag(title, f.zone(Slot.RIGHT_TOP));
                assertThat(f.owner.dockDrag().active()).isTrue();
                assertThat(f.owner.leftStripe().dragging()).as("rail %s: the stripes show the drag only while they show", rail)
                    .isEqualTo(!rail);
                f.owner.setRailVisible(rail);
                assertThat(f.owner.dockDrag().active()).as("View > Rail %s ends the drag", rail ? "on" : "off").isFalse();
                assertThat(f.owner.dockDrag().overlay().getParent()).isNull();
                assertThat(f.owner.leftStripe().dragging()).isFalse();
                assertThat(f.owner.rightStripe().dragging()).isFalse();
                f.mouse(title, MouseEvent.MOUSE_RELEASED, f.zone(Slot.RIGHT_TOP));
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
            f.drag(files, f.zone(Slot.BOTTOM_RIGHT));
            assertThat(f.owner.dockDrag().dragged()).isEqualTo("dev.x.files");
            assertThat(files.getWidth()).as("lifted out of the stripe").isZero();
            assertThat(f.owner.dockDrag().icons.apply("dev.x.files")).as("the ghost has no dot").isSameAs(f.files.icon());
            f.mouse(files, MouseEvent.MOUSE_RELEASED, f.zone(Slot.BOTTOM_RIGHT));
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

    /**
     * A badge change reaches every window's stripes and cancels no dock drag, in the window whose panel changed or in the
     * other. One drag runs at a time, as one pointer allows: a press in another window while one runs is that drag's lost
     * release (floating tool windows plan 2's final review).
     */
    @Test void aBadgeChangeDoesNotCancelADragInAnyWindow() throws Exception {
        edt(() -> {
            var first = new Fixture();
            var second = new Fixture(first);
            first.layout();
            second.layout();
            AbstractButton inFirst = first.button("Files");
            first.drag(inFirst, first.zone(Slot.BOTTOM_RIGHT));
            assertThat(first.owner.dockDrag().active()).isTrue();

            first.files.setBadge(Optional.of(new PanelBadge(3, PanelBadge.Tone.ACCENT, "3 unread notifications")));
            second.sessions.setBadge(Optional.of(new PanelBadge(1, PanelBadge.Tone.ERROR, "1 unread notification, 1 error")));

            assertThat(first.owner.dockDrag().active()).as("a change to the dragged panel and to another").isTrue();
            assertThat(first.owner.leftStripe().buttons()).contains(inFirst);
            assertThat(inFirst.getWidth()).isZero();
            assertThat(second.button("Files (3 unread notifications)").getIcon()).as("every window's stripes show it")
                .isInstanceOf(BadgedIcon.class);
            assertThat(first.button("Sessions (1 unread notification, 1 error)").getIcon()).isInstanceOf(BadgedIcon.class);
            first.mouse(inFirst, MouseEvent.MOUSE_RELEASED, first.zone(Slot.BOTTOM_RIGHT));
            first.layout();
            assertThat(first.owner.dock().slot("dev.x.files")).hasValue(Slot.BOTTOM_RIGHT);

            AbstractButton inSecond = second.button("Sessions (1 unread notification, 1 error)");
            second.drag(inSecond, second.zone(Slot.BOTTOM_RIGHT));
            assertThat(second.owner.dockDrag().active()).isTrue();

            first.files.setBadge(Optional.of(new PanelBadge(4, PanelBadge.Tone.ACCENT, "4 unread notifications")));
            second.sessions.setBadge(Optional.of(new PanelBadge(2, PanelBadge.Tone.ERROR, "2 unread notifications, 1 error")));

            assertThat(second.owner.dockDrag().active()).as("in the other window too").isTrue();
            assertThat(second.owner.leftStripe().buttons()).contains(inSecond);
            assertThat(inSecond.getWidth()).isZero();
            second.mouse(inSecond, MouseEvent.MOUSE_RELEASED, second.zone(Slot.BOTTOM_RIGHT));
            second.layout();
            assertThat(second.owner.dock().slot("dev.x.sessions")).hasValue(Slot.BOTTOM_RIGHT);
        });
    }

    @Test void anActionsEnableOrTitleChangeMidDragRefreshesItsRailButtonInPlaceAndTheDragDrops() throws Exception {
        edt(() -> {
            var f = new Fixture();
            ActionEntry open = f.model.addAction("dev.x.open", "Open Manager", new ImageIcon(), List.of(), Optional.empty(), invocation -> { });
            f.model.addRailAction("dev.x.open");
            f.layout();
            AbstractButton files = f.button("Files");
            AbstractButton rail = f.button("Open Manager");
            f.drag(files, f.zone(Slot.BOTTOM_RIGHT));
            assertThat(f.owner.dockDrag().active()).isTrue();
            assertThat(files.getWidth()).as("lifted out of the stripe").isZero();
            Optional<Rectangle> leftGap = f.owner.leftStripe().gapBounds(), rightGap = f.owner.rightStripe().gapBounds();

            open.setEnabled(false);

            assertThat(f.owner.dockDrag().active()).as("an enable change is not a render").isTrue();
            assertThat(f.owner.dockDrag().dragged()).isEqualTo("dev.x.files");
            assertThat(f.owner.leftStripe().buttons()).as("the same buttons").contains(files, rail);
            assertThat(files.getWidth()).as("still lifted out").isZero();
            assertThat(rail.isEnabled()).isFalse();
            assertThat(f.owner.leftStripe().gapBounds()).isEqualTo(leftGap);
            assertThat(f.owner.rightStripe().gapBounds()).isEqualTo(rightGap);

            open.setTitle("Open Manager Now");

            assertThat(f.owner.dockDrag().active()).as("a title change is not a render").isTrue();
            assertThat(f.owner.dockDrag().dragged()).isEqualTo("dev.x.files");
            assertThat(f.owner.leftStripe().buttons()).contains(files, rail);
            assertThat(files.getWidth()).as("still lifted out").isZero();
            assertThat(f.button("Open Manager Now")).as("the rail button follows the title").isSameAs(rail);
            assertThat(rail.getAccessibleContext().getAccessibleName()).isEqualTo("Open Manager Now");
            assertThat(f.owner.leftStripe().gapBounds()).isEqualTo(leftGap);
            assertThat(f.owner.rightStripe().gapBounds()).isEqualTo(rightGap);

            f.mouse(files, MouseEvent.MOUSE_RELEASED, f.zone(Slot.BOTTOM_RIGHT));
            f.layout();
            assertThat(f.owner.dockDrag().active()).isFalse();
            assertThat(f.owner.dock().slot("dev.x.files")).as("the drop happened").hasValue(Slot.BOTTOM_RIGHT);
            assertThat(f.owner.rightStripe().buttons()).extracting(AbstractButton::getToolTipText).containsExactly("Files");
            assertThat(f.button("Open Manager Now").isEnabled()).as("the drop's render keeps the rail button's state").isFalse();
        });
    }

    @Test void aRailActionDoesNotDrag() throws Exception {
        edt(() -> {
            var f = new Fixture();
            f.model.addAction("dev.x.open", "Open Manager", new ImageIcon(), List.of(), java.util.Optional.empty(), invocation -> { });
            f.model.addRailAction("dev.x.open");
            f.layout();
            f.drag(f.button("Open Manager"), f.zone(Slot.RIGHT_TOP));
            assertThat(f.owner.dockDrag().active()).isFalse();
        });
    }
}
