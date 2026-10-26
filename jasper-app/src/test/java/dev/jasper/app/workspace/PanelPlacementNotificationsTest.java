package dev.jasper.app.workspace;

import dev.jasper.app.contributions.Contributions;
import dev.jasper.app.contributions.PanelSite;
import dev.jasper.app.dock.Slot;
import dev.jasper.app.dock.ToolWindowMode;
import dev.jasper.app.persistence.UiState;
import java.awt.Component;
import java.awt.Rectangle;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static dev.jasper.app.workspace.DesktopTestSupport.edt;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Floating tool windows plan 3 Task 1: a panel's site reads its slot and mode, and hears once per effective change of
 * either, after its component is in its new place: never for a move within its slot or for visibility alone, never a
 * placement a nested change already overtook, nothing after it closed. The main window stands in a stand-in frame on a
 * pretend screen; the detached windows' native windows are {@link DetachedToolWindowsTest.Shells}.
 */
class PanelPlacementNotificationsTest {
    private static final String A = "dev.x.alpha", B = "dev.x.beta";
    private static final Rectangle SCREEN = new Rectangle(0, 0, 1920, 1080);

    @AfterEach void closeWindows() throws Exception { DesktopTestSupport.closeOwners(); }

    /** A main window with panels whose sites record what they read and hear, and recording shells. */
    private static final class Fixture {
        final Contributions model = new Contributions();
        final WindowContent owner;
        final DetachedToolWindowsTest.Shells shells = new DetachedToolWindowsTest.Shells();
        final Map<String, JComponent> views = new HashMap<>();
        final Map<String, PanelSite> sites = new HashMap<>();
        /** What each panel's factory read from its site. */
        final Map<String, PanelSite.Placement> built = new HashMap<>();
        /** Every notification, as "id slot mode". */
        final List<String> heard = new ArrayList<>();
        /** Run with each notification after it is recorded: where the panel's component is, or a nested change. */
        Consumer<PanelSite.Placement> during = placement -> { };

        Fixture() { this(UiState.inMemory()); }

        Fixture(UiState state) {
            owner = DesktopTestSupport.content(DesktopTestSupport.launcher(new ArrayDeque<>()));
            owner.connectContributions(model, state);
            TabDragTestSupport.frame(owner, 100, 50, 1200, 800);
            DetachedToolWindows windows = owner.detachedWindows();
            windows.ready = () -> true;
            windows.displays = () -> List.of(new WindowPlacement.Display(SCREEN, SCREEN));
            owner.detachedShells(shells);
            add(A, "Alpha", Slot.LEFT_TOP);
        }

        void add(String id, String title, Slot slot) {
            var view = new JLabel(title);
            views.put(id, view);
            model.addPanel(id, title, new ImageIcon(), slot, site -> {
                sites.put(id, site);
                built.put(id, site.placement());
                site.onPlacement(placement -> {
                    heard.add(id + " " + placement.slot() + " " + placement.mode());
                    during.accept(placement);
                });
                return view;
            });
            relayout();
        }

        void toggle(String id) {
            model.requestPanel(new Contributions.PanelRequest(owner.id(), id, Contributions.PanelRequest.Op.TOGGLE));
            relayout();
        }

        void choose(JPopupMenu menu, String item) {
            ToolWindowHeaderTest.item(menu, item).doClick();
            relayout();
        }

        /** The docked header's Options menu of the slot. */
        JPopupMenu headerMenu(Slot slot) { return owner.area().headerOf(slot).menu(); }

        /** The detached frame's header's Options menu. */
        JPopupMenu detachedMenu() {
            JComponent frame = owner.area().detachedFrame(A);
            return ((ToolWindowHeader) ((java.awt.BorderLayout) frame.getLayout()).getLayoutComponent(java.awt.BorderLayout.NORTH)).menu();
        }

        /** The stripe button's Options menu of the first panel on the left stripe. */
        JPopupMenu stripeMenu() { return owner.leftStripe().buttons().getFirst().getComponentPopupMenu(); }

        /** Lays the window's stand-in frame out again, as Swing would after a dock change. */
        void relayout() {
            Component top = owner;
            while (top.getParent() != null) top = top.getParent();
            TabDragTestSupport.relayout((JPanel) top);
        }

        /** What was heard since the last call. */
        List<String> take() {
            var taken = List.copyOf(heard);
            heard.clear();
            return taken;
        }
    }

    /** Review focus 1: a panel built while it is detached (restored so at start-up) reads its slot and mode in its factory. */
    @Test void theFactoryReadsTheCurrentPlacement() throws Exception {
        edt(() -> {
            var state = UiState.inMemory();
            state.putPanel(A, new UiState.Panel(Slot.RIGHT_BOTTOM, 0, true, ToolWindowMode.FLOAT,
                Optional.of(new UiState.Bounds(200, 150, 400, 300))));
            var fixture = new Fixture(state);
            assertThat(fixture.built.get(A)).isEqualTo(new PanelSite.Placement(Slot.RIGHT_BOTTOM, ToolWindowMode.FLOAT));
            assertThat(fixture.sites.get(A).placement()).isEqualTo(new PanelSite.Placement(Slot.RIGHT_BOTTOM, ToolWindowMode.FLOAT));

            fixture.add(B, "Beta", Slot.LEFT_TOP);
            fixture.owner.dock().moveAndShow(B, Slot.BOTTOM_LEFT, 0);
            assertThat(fixture.built.get(B)).as("built by the change that placed it, it reads the new place")
                .isEqualTo(new PanelSite.Placement(Slot.BOTTOM_LEFT, ToolWindowMode.DOCKED));
            assertThat(fixture.take()).as("and hears nothing of the change that built it").isEmpty();
        });
    }

    /**
     * Fix round 1: the place a panel is in once its factory returns is the baseline, so a move made while the factory ran,
     * then a move back to where the factory started, is told.
     */
    @Test void aMoveWhileTheFactoryRunsLeavesNoStaleBaseline() throws Exception {
        edt(() -> {
            var fixture = new Fixture();
            var heard = new ArrayList<String>();
            fixture.model.addPanel(B, "Beta", new ImageIcon(), Slot.LEFT_BOTTOM, site -> {
                fixture.owner.dock().move(B, Slot.RIGHT_TOP, 0);
                site.onPlacement(placement -> heard.add(placement.slot() + " " + placement.mode()));
                return new JLabel("Beta");
            });
            fixture.relayout();
            fixture.toggle(B);
            assertThat(fixture.owner.dock().slot(B)).hasValue(Slot.RIGHT_TOP);
            assertThat(heard).as("nothing before the factory returned, and nothing for its own move").isEmpty();

            fixture.owner.dock().move(B, Slot.LEFT_BOTTOM, 0);
            assertThat(heard).containsExactly("LEFT_BOTTOM DOCKED");
        });
    }

    /** Review focus 2: Move to another slot tells the panel once; reordering within its slot tells it nothing. */
    @Test void movingToAnotherSlotNotifiesOnce() throws Exception {
        edt(() -> {
            var fixture = new Fixture();
            fixture.toggle(A);
            fixture.choose(fixture.headerMenu(Slot.LEFT_TOP), "Right Top");
            assertThat(fixture.take()).containsExactly(A + " RIGHT_TOP DOCKED");
            assertThat(fixture.sites.get(A).placement()).isEqualTo(new PanelSite.Placement(Slot.RIGHT_TOP, ToolWindowMode.DOCKED));

            fixture.choose(fixture.headerMenu(Slot.RIGHT_TOP), "Right Top");
            assertThat(fixture.take()).as("its own slot").isEmpty();

            fixture.add(B, "Beta", Slot.RIGHT_TOP);
            fixture.owner.dock().move(A, Slot.RIGHT_TOP, 1);
            assertThat(fixture.owner.dock().ids(Slot.RIGHT_TOP)).containsExactly(B, A);
            assertThat(fixture.take()).as("reordered within its slot").isEmpty();

            fixture.toggle(A);
            fixture.owner.dock().move(A, Slot.BOTTOM_LEFT, 0);
            assertThat(fixture.take()).as("built and hidden, a move still places it").containsExactly(A + " BOTTOM_LEFT DOCKED");
        });
    }

    @Test void floatingAndDockingNotify() throws Exception {
        edt(() -> {
            var fixture = new Fixture();
            JComponent view = fixture.views.get(A);
            var where = new ArrayList<String>();
            fixture.during = placement -> where.add(placement.mode().detached()
                ? SwingUtilities.isDescendingFrom(view, fixture.owner.area().detachedFrame(A)) ? "own window" : "elsewhere"
                : fixture.owner.area().content(placement.slot()) == view ? "slot" : "elsewhere");
            fixture.toggle(A);

            fixture.choose(fixture.headerMenu(Slot.LEFT_TOP), "Float");
            assertThat(fixture.take()).containsExactly(A + " LEFT_TOP FLOAT");
            fixture.choose(fixture.detachedMenu(), "Window");
            assertThat(fixture.take()).containsExactly(A + " LEFT_TOP WINDOW");
            fixture.choose(fixture.detachedMenu(), "Docked");
            assertThat(fixture.take()).containsExactly(A + " LEFT_TOP DOCKED");
            assertThat(where).as("the component is in its new place when each arrives").containsExactly("own window", "own window", "slot");

            fixture.choose(fixture.stripeMenu(), "Window");
            fixture.choose(fixture.stripeMenu(), "Right Bottom");
            assertThat(fixture.take()).as("Move to on a detached tool window docks it there")
                .containsExactly(A + " LEFT_TOP WINDOW", A + " RIGHT_BOTTOM DOCKED");
        });
    }

    @Test void visibilityAloneNotifiesNothing() throws Exception {
        edt(() -> {
            var fixture = new Fixture();
            fixture.toggle(A);
            fixture.toggle(A);
            fixture.toggle(A);
            assertThat(fixture.take()).isEmpty();

            fixture.choose(fixture.headerMenu(Slot.LEFT_TOP), "Float");
            assertThat(fixture.take()).hasSize(1);
            fixture.toggle(A);
            fixture.toggle(A);
            fixture.owner.dock().detach(A, ToolWindowMode.FLOAT, new Rectangle(300, 200, 500, 400));
            assertThat(fixture.take()).as("its window hidden, shown and moved").isEmpty();
        });
    }

    /** Review focus 3: a handler that moves or hides the panel hears each place that is still true, once, in order. */
    @Test void aHandlerThatMovesThePanelDoesNotGetAStaleSecondCall() throws Exception {
        edt(() -> {
            var fixture = new Fixture();
            fixture.toggle(A);
            var second = new ArrayList<String>();
            fixture.during = placement -> {
                if (placement.slot() == Slot.RIGHT_TOP) fixture.owner.dock().move(A, Slot.BOTTOM_LEFT, 0);
            };
            fixture.sites.get(A).onPlacement(placement -> second.add(placement.slot() + " " + placement.mode()));
            fixture.choose(fixture.headerMenu(Slot.LEFT_TOP), "Right Top");
            assertThat(fixture.take()).containsExactly(A + " RIGHT_TOP DOCKED", A + " BOTTOM_LEFT DOCKED");
            assertThat(second).as("a later listener hears only the place that is still true").containsExactly("BOTTOM_LEFT DOCKED");
            assertThat(fixture.sites.get(A).placement()).isEqualTo(new PanelSite.Placement(Slot.BOTTOM_LEFT, ToolWindowMode.DOCKED));

            // A visibility listener that moves it while a change shows and places it: the place it moved on to, once.
            fixture.during = placement -> { };
            second.clear();
            fixture.toggle(A);
            fixture.sites.get(A).onVisibility(visible -> {
                if (visible) fixture.owner.dock().move(A, Slot.RIGHT_BOTTOM, 0);
            });
            fixture.owner.dock().moveAndShow(A, Slot.LEFT_BOTTOM, 0);
            assertThat(fixture.take()).containsExactly(A + " RIGHT_BOTTOM DOCKED");
            assertThat(second).containsExactly("RIGHT_BOTTOM DOCKED");

            // A handler that hides it: no second call.
            var site = fixture.sites.get(A);
            site.onPlacement(placement -> site.hide());
            second.clear();
            fixture.owner.dock().setMode(A, ToolWindowMode.FLOAT);
            assertThat(fixture.take()).containsExactly(A + " RIGHT_BOTTOM FLOAT");
            assertThat(second).containsExactly("RIGHT_BOTTOM FLOAT");
            assertThat(fixture.owner.dock().visible(A)).isFalse();
        });
    }

    @Test void nothingAfterClose() throws Exception {
        edt(() -> {
            var fixture = new Fixture();
            fixture.toggle(A);
            var site = fixture.sites.get(A);
            fixture.model.panels().getFirst().close();
            site.notifyPlacement(new PanelSite.Placement(Slot.RIGHT_TOP, ToolWindowMode.WINDOW));
            assertThat(fixture.take()).as("its plugin removed it").isEmpty();

            var other = new Fixture();
            other.toggle(A);
            var dock = other.owner.dock();
            other.owner.close();
            dock.move(A, Slot.RIGHT_TOP, 0);
            other.sites.get(A).notifyPlacement(new PanelSite.Placement(Slot.BOTTOM_RIGHT, ToolWindowMode.FLOAT));
            assertThat(other.take()).as("its window closed").isEmpty();
        });
    }
}
