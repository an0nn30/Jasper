package dev.jasper.app.workspace;

import static dev.jasper.app.workspace.DesktopTestSupport.*;
import static dev.jasper.app.workspace.TabDragTestSupport.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.jasper.app.appearance.ThemeTestSupport;
import dev.jasper.app.config.AnimationSettings;
import dev.jasper.app.contributions.Contributions;
import dev.jasper.app.contributions.OtherTab;
import dev.jasper.app.contributions.Tab;
import dev.jasper.app.contributions.TabRegistry;
import dev.jasper.app.layout.Child;
import dev.jasper.app.layout.GroupId;
import dev.jasper.app.layout.Layout;
import dev.jasper.app.layout.ParentTab;
import dev.jasper.app.layout.ParentTabId;
import dev.jasper.app.layout.Side;
import dev.jasper.app.layout.Split;
import dev.jasper.app.terminals.TerminalEvent;
import dev.jasper.app.terminals.TerminalRegistry;
import java.awt.Component;
import java.awt.KeyboardFocusManager;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import javax.swing.JComponent;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Pane flexibility spec 5 within one window: a whole tab moves into another tab of its window as one nested split beside
 * a pane, from Move Tab Into in a tab's menu and through {@link WindowContent#mergeTab}. The main window, in a stand-in
 * frame on a pretend screen, holds three tabs of plugin views: the first "a" beside "b" (H[a, b]), "b" active and
 * showing; the second "c" above "d" (V[c, d]), "d" active; the third "e". Its strip shows, as it holds three tabs.
 */
class TabMergeTest {
    private final ArrayDeque<Runnable> pending = new ArrayDeque<>();
    private final Contributions model = new Contributions();
    private final TerminalRegistry terminals = new TerminalRegistry();
    private final List<TerminalEvent> facts = new ArrayList<>();
    /** Stands in for the focus manager during each test: it records the Esc dispatchers a drag adds. */
    private CommandPaletteShortcutsTest.RecordingFocusManager keys;
    private KeyboardFocusManager previousKeys;
    private int listeners;
    private WindowContent owner;
    private JPanel frame;
    private Tab a, b, c, d, e;
    private ParentTabId first, second, third;

    @BeforeEach void window() throws Exception {
        edt(() -> {
            ThemeTestSupport.install(true);
            previousKeys = KeyboardFocusManager.getCurrentKeyboardFocusManager();
            keys = new CommandPaletteShortcutsTest.RecordingFocusManager();
            KeyboardFocusManager.setCurrentKeyboardFocusManager(keys);
            listeners = motionListeners();
            terminals.onEvent(facts::add);
            owner = content(idleLauncher(pending), WindowRole.MAIN, null, true, () -> { });
            owner.setAnimations(new AnimationSettings(false, 0, 0, 0));
            owner.connectContributions(model);
            owner.connectTerminals(terminals, () -> { });
            a = view("a", TabRegistry.Placement.NEW_TAB);
            b = view("b", TabRegistry.Placement.SPLIT_RIGHT);
            c = view("c", TabRegistry.Placement.NEW_TAB);
            d = view("d", TabRegistry.Placement.SPLIT_DOWN);
            e = view("e", TabRegistry.Placement.NEW_TAB);
            first = owner.parentTabOf(a).orElseThrow();
            second = owner.parentTabOf(c).orElseThrow();
            third = owner.parentTabOf(e).orElseThrow();
            owner.selectTab(b);
            frame = frame(owner, 100, 50, 1208, 760);
        });
    }

    /** A drag that outlived its test would keep its listeners into the next test of this JVM. */
    @AfterEach void cleanup() throws Exception {
        edt(() -> {
            try {
                owner.tabDrag().cancel();
                assertThat(keys.dispatchers).as("no Esc dispatcher is left behind").isEmpty();
                assertThat(motionListeners()).as("no lost-release listener is left behind").isEqualTo(listeners);
            } finally {
                KeyboardFocusManager.setCurrentKeyboardFocusManager(previousKeys);
            }
        });
        closeOwners();
    }

    /** A plugin's view titled {@code title} in the window, placed as {@code placement} says. */
    private Tab view(String title, TabRegistry.Placement placement) { return view(title, new JPanel(), placement); }

    private Tab view(String title, JComponent component, TabRegistry.Placement placement) {
        return model.tabs().open("dev.example.notes", title, title, owner.id(), component, placement);
    }

    /** The window's tabs, in strip order. */
    private List<ParentTabId> ids() { return owner.windowLayout().parentTabs().stream().map(ParentTab::id).toList(); }

    /** What the registry heard of selections, moves and closes since {@link #facts} was last cleared. */
    private List<TerminalEvent> announced() {
        return facts.stream().filter(fact -> fact instanceof TerminalEvent.TabSelected || fact instanceof TerminalEvent.TabMoved
            || fact instanceof TerminalEvent.TabClosed).toList();
    }

    /** The Move Tab Into submenu of the menu of {@code window}'s strip entry that shows {@code item}. */
    static JMenu tabMoves(WindowContent window, Tab item) {
        for (Component child : window.tabMenu(item).getComponents())
            if (child instanceof JMenu moves && "Move Tab Into".equals(moves.getText())) return moves;
        throw new AssertionError("no Move Tab Into");
    }

    static List<String> texts(JMenu menu) {
        var texts = new ArrayList<String>();
        for (Component child : menu.getMenuComponents()) if (child instanceof JMenuItem item) texts.add(item.getText());
        return texts;
    }

    static JMenuItem entry(JMenu menu, String text) {
        for (Component child : menu.getMenuComponents())
            if (child instanceof JMenuItem item && text.equals(item.getText())) return item;
        throw new AssertionError("no " + text + " in " + texts(menu));
    }

    /** The titles of {@code window}'s strip entries as the strip draws them, from left to right. */
    static List<String> drawn(WindowContent window) {
        JComponent strip = window.tabBar().component();
        var shown = new ArrayList<Component>();
        selects(strip, shown);
        shown.sort(Comparator.comparingInt(select -> SwingUtilities.convertPoint(select, 0, 0, strip).x));
        return shown.stream().map(select -> select.getName().substring("select:".length())).toList();
    }

    /** Every title button under {@code parent} whose entry shows. */
    private static void selects(java.awt.Container parent, List<Component> found) {
        for (Component child : parent.getComponents()) {
            if (child.getName() != null && child.getName().startsWith("select:")) {
                if (child.getParent().isVisible()) found.add(child);
            } else if (child instanceof java.awt.Container container) {
                selects(container, found);
            }
        }
    }

    /**
     * Pane flexibility spec 5: the strip draws its entries in its tabs' order, as Select Tab, Move Tab Into's numbers, the
     * tab list and the drop places ({@code tabBounds()}) read them: titled {@code titles} from left to right.
     */
    private void drawsInOrder(String... titles) {
        assertThat(owner.parentTabs().bar().tabs()).extracting(owner::tabTitle).as("the tabs' order").containsExactly(titles);
        assertThat(drawn(owner)).as("the entries from left to right").containsExactly(titles);
        assertThat(owner.tabBar().tabBounds()).extracting(bounds -> bounds.x).as("the drop places from left to right")
            .doesNotHaveDuplicates().isSorted();
    }

    /**
     * Pane flexibility spec 5 and workspace plan 9a Rulings 10 to 12: Move Tab Into, from the menu of a tab that does not
     * show, lists the window's other tabs and moves every pane of the tab to the right of the target's active pane as one
     * nested split, its sizes kept. Its entry goes, the tab it joined shows, named after the moved tab's active pane,
     * which takes the focus (spec 5 as amended), and the registry hears that item selected once and no move or close.
     */
    @Test void moveTabIntoPutsEveryPaneBesideTheTargetsActivePaneAsOneNestedSplit() throws Exception {
        edt(() -> {
            owner.parentTabs().view(second).orElseThrow().resizeSplit(List.of(), List.of(0.3, 0.7));
            JMenu moves = tabMoves(owner, d);
            assertThat(texts(moves)).as("the window's other tabs, numbered as Select Tab numbers them").containsExactly("1 b", "3 e");
            assertThat(moves.isEnabled()).isTrue();
            facts.clear();
            entry(moves, "1 b").doClick();
            assertThat(ids()).as("its entry goes").containsExactly(first, third);
            assertThat(owner.activeParentTab()).as("the tab it joined shows").isEqualTo(first);
            assertThat(PaneDragTest.shape(owner)).as("to the right of b, the tab's active pane").isEqualTo("H[a, b, V[c, d]]");
            var root = (Split) owner.workspace().model().root();
            assertThat(root.children()).extracting(Child::weight).as("b's half for the moved tab").containsExactly(0.5, 0.25, 0.25);
            assertThat(((Split) root.children().get(2).node()).children()).extracting(Child::weight).as("its sizes kept")
                .containsExactly(0.3, 0.7);
            assertThat(owner.selectedTab()).as("the moved tab's active pane has the focus").isSameAs(d);
            assertThat(owner.tabTitle(owner.parentTabs().active().selected())).isEqualTo("d");
            assertThat(announced()).as("announced once, where it lands")
                .containsExactly(new TerminalEvent.TabSelected(owner.id(), d.id().value()));
            assertThat(List.of(c, d)).noneMatch(Tab::closed);
            assertThat(d.window()).isEqualTo(owner.id());
        });
    }

    /** Pane flexibility spec 5: the menu of the tab that shows moves it into a tab that does not, which then shows. */
    @Test void theShownTabMovesIntoAHiddenOneWhichShows() throws Exception {
        edt(() -> {
            entry(tabMoves(owner, b), "3 e").doClick();
            assertThat(ids()).containsExactly(second, third);
            assertThat(owner.activeParentTab()).isEqualTo(third);
            assertThat(PaneDragTest.shape(owner)).isEqualTo("H[e, a, b]");
            assertThat(owner.selectedTab()).isSameAs(b);
        });
    }

    /** Pane flexibility spec 5: a tab merged into a tab whose pane is maximized ends the maximize. */
    @Test void aTabMergedIntoAMaximizedTabEndsItsMaximize() throws Exception {
        edt(() -> {
            owner.selectTab(a);
            owner.toggleMaximize();
            assertThat(owner.workspace().model().maximized()).isPresent();
            owner.moveTabInto(third, first);
            assertThat(owner.workspace().model().maximized()).as("the maximize ends").isEmpty();
            assertThat(PaneDragTest.shape(owner)).as("to the right of a, the maximized pane").isEqualTo("H[a, e, b]");
            assertThat(owner.selectedTab()).isSameAs(e);
        });
    }

    /**
     * Pane flexibility spec 5 and workspace plan 9a Rulings 7: a tab holding an item that may not move, as under a modal
     * prompt, offers no tab and stays, and a window of one tab offers none: the submenu shows disabled.
     */
    @Test void aTabThatMayNotMoveAndAWindowOfOneTabOfferNoTab() throws Exception {
        edt(() -> {
            var held = c.pin();
            try {
                assertThat(owner.tabTargets(second)).as("a dialog open in one of its panes").isEmpty();
                assertThat(tabMoves(owner, d).isEnabled()).isFalse();
                owner.moveTabInto(second, first);
                owner.mergeTab(second, a.group().orElseThrow(), Side.LEFT);
                assertThat(ids()).as("it stays").containsExactly(first, second, third);
            } finally {
                held.close();
            }
            assertThat(tabMoves(owner, d).isEnabled()).isTrue();
            var lone = content(idleLauncher(pending), WindowRole.CHILD, null, true, () -> { });
            lone.connectContributions(model);
            Tab only = model.tabs().open("dev.example.notes", "only", "only", lone.id(), new JPanel(), TabRegistry.Placement.NEW_TAB);
            assertThat(lone.tabTargets(lone.activeParentTab())).isEmpty();
            assertThat(tabMoves(lone, only).isEnabled()).as("a window of one tab").isFalse();
        });
    }

    /** A tab is never merged into itself, and Move Tab Into names another tab. */
    @Test void aTabTakesNoMergeIntoItself() throws Exception {
        edt(() -> {
            owner.mergeTab(first, a.group().orElseThrow(), Side.RIGHT);
            owner.moveTabInto(first, first);
            assertThat(ids()).containsExactly(first, second, third);
            assertThat(PaneDragTest.shape(owner)).isEqualTo("H[a, b]");
        });
    }

    /** Pane flexibility spec 5 and Review Focus 6: a tab's title is plain text in Move Tab Into, never HTML. */
    @Test void aTabsTitleIsPlainTextInMoveTabInto() throws Exception {
        edt(() -> {
            view("<html><b>B", TabRegistry.Placement.NEW_TAB);
            owner.selectTab(b);
            JMenu moves = tabMoves(owner, b);
            assertThat(texts(moves)).containsExactly("2 d", "3 e", "4 <html><b>B");
            for (Component child : moves.getMenuComponents()) {
                assertThat(((JMenuItem) child).getClientProperty("html.disable"))
                    .as("%s disables HTML", ((JMenuItem) child).getText()).isEqualTo(Boolean.TRUE);
                assertThat(((JMenuItem) child).getClientProperty(javax.swing.plaf.basic.BasicHTML.propertyKey))
                    .as("%s renders no HTML", ((JMenuItem) child).getText()).isNull();
            }
        });
    }

    /**
     * Workspace plan 9a Rulings 12: a tab that fails to land stays as it was, its items back in their panes, nothing
     * closed, and the failure propagates.
     */
    @Test void aTabThatFailsToLandStaysAsItWas() throws Exception {
        edt(() -> {
            var refusing = new PaneDragTest.Refusing();
            Tab notes = view("notes", refusing, TabRegistry.Placement.NEW_TAB);
            owner.selectTab(b);
            ParentTabId own = owner.parentTabOf(notes).orElseThrow();
            var pane = notes.group().orElseThrow();
            var before = owner.windowLayout();
            refusing.armed = true;
            assertThatThrownBy(() -> owner.mergeTab(own, a.group().orElseThrow(), Side.RIGHT)).hasMessage("refused");
            assertThat(owner.windowLayout()).as("the window's tabs as they were").isEqualTo(before);
            assertThat(refusing.getParent()).as("back in its pane")
                .isSameAs(owner.parentTabs().view(own).orElseThrow().groupView(pane));
            assertThat(notes.closed()).as("nothing closes").isFalse();
            owner.mergeTab(own, a.group().orElseThrow(), Side.RIGHT);
            assertThat(owner.parentTabOf(notes)).as("once it lands").contains(first);
        });
    }

    /**
     * Pane flexibility spec 5 and workspace plan 9a Rulings 12: a two-pane tab that fails to land leaves both tabs exactly
     * as they were, each of its items back in its own pane, the strip as it was, and nothing closed.
     */
    @Test void aTwoPaneTabThatFailsToLandLeavesBothTabsExactlyAsTheyWere() throws Exception {
        edt(() -> {
            var refusing = new PaneDragTest.Refusing();
            Tab notes = view("notes", refusing, TabRegistry.Placement.NEW_TAB);
            Tab more = view("more", TabRegistry.Placement.SPLIT_RIGHT);
            owner.selectTab(b);
            ParentTabId own = owner.parentTabOf(notes).orElseThrow();
            assertThat(owner.parentTabOf(more)).contains(own);
            WorkspaceView moving = owner.parentTabs().view(own).orElseThrow(), target = owner.parentTabs().view(first).orElseThrow();
            var before = owner.windowLayout();
            Layout movingBefore = moving.model(), targetBefore = target.model();
            refusing.armed = true;
            assertThatThrownBy(() -> owner.mergeTab(own, a.group().orElseThrow(), Side.RIGHT)).hasMessage("refused");
            assertThat(owner.windowLayout()).as("the window's tabs as they were").isEqualTo(before);
            assertThat(owner.parentTabs().view(own)).as("its workspace stays").containsSame(moving);
            assertThat(moving.model()).as("the moving tab exactly as it was").isEqualTo(movingBefore);
            assertThat(target.model()).as("the target exactly as it was").isEqualTo(targetBefore);
            assertThat(refusing.getParent()).as("back in its pane").isSameAs(moving.groupView(notes.group().orElseThrow()));
            assertThat(more.component().getParent()).as("in its pane").isSameAs(moving.groupView(more.group().orElseThrow()));
            assertThat(target.contains(notes) || target.contains(more)).as("the target holds neither").isFalse();
            assertThat(owner.activeParentTab()).isEqualTo(first);
            assertThat(List.of(notes, more)).noneMatch(Tab::closed);
            drawsInOrder("b", "d", "e", "more");
        });
    }

    /**
     * Workspace plan 9a Rulings 12, as plan 8c's {@code PaneDragTest.aPaneMovedIntoAnotherTabIsAnnouncedOnceWhereItLands}:
     * the shown tab moving into a hidden one keeps its active item the selected item, so the registry hears no selection,
     * move or close, and nothing closes.
     */
    @Test void theShownTabMovingIntoAHiddenOneKeepsItsItemSelectedForTheRegistry() throws Exception {
        edt(() -> {
            facts.clear();
            entry(tabMoves(owner, b), "3 e").doClick();
            assertThat(owner.activeParentTab()).isEqualTo(third);
            assertThat(owner.selectedTab()).isSameAs(b);
            assertThat(announced()).as("nothing changed for the registry").isEmpty();
            assertThat(List.of(a, b)).noneMatch(Tab::closed);
        });
    }

    /** Workspace plan 9a Rulings 5: after a merge the tabs are numbered again, in strip order. */
    @Test void theTabsAreNumberedAgainAfterAMerge() throws Exception {
        edt(() -> {
            owner.moveTabInto(first, second);
            assertThat(ids()).containsExactly(second, third);
            assertThat(texts(tabMoves(owner, b))).as("the tab it joined, now the first, named after b").containsExactly("2 e");
            assertThat(texts(tabMoves(owner, e))).containsExactly("1 b");
            assertThat(owner.tabTargets(third)).extracting(OtherTab::number).containsExactly(1);
        });
    }

    /** Pane flexibility spec 5: a tab whose own pane is maximized arrives unmaximized, its active pane active. */
    @Test void aMaximizedTabArrivesUnmaximizedWithItsActivePaneActive() throws Exception {
        edt(() -> {
            owner.selectTab(a);
            owner.toggleMaximize();
            assertThat(owner.workspace().model().maximized()).contains(a.group().orElseThrow());
            owner.moveTabInto(first, third);
            Layout joined = owner.workspace().model();
            assertThat(joined.maximized()).as("it arrives unmaximized").isEmpty();
            assertThat(joined.activeGroup()).as("its active pane active").isEqualTo(a.group().orElseThrow());
            assertThat(PaneDragTest.shape(owner)).isEqualTo("H[e, a, b]");
            assertThat(owner.selectedTab()).isSameAs(a);
        });
    }

    /** Pane flexibility spec 5: a tab merges on any side of a pane; a split of that side's orientation joins its parent's. */
    @Test void aTabMergesAboveAPane() throws Exception {
        edt(() -> {
            owner.mergeTab(second, b.group().orElseThrow(), Side.UP);
            assertThat(ids()).containsExactly(first, third);
            assertThat(PaneDragTest.shape(owner)).as("above b, in b's column").isEqualTo("H[a, V[c, d, b]]");
            var column = (Split) ((Split) owner.workspace().model().root()).children().get(1).node();
            assertThat(column.children()).extracting(Child::weight).as("b's half for the moved tab").containsExactly(0.25, 0.25, 0.5);
            assertThat(owner.selectedTab()).isSameAs(d);
            assertThat(owner.workspace().model().activeGroup()).isEqualTo(d.group().orElseThrow());
        });
    }

    /** Workspace plan 9a Rulings 12: a workspace takes another tab's items only with the layout that places them. */
    @Test void aJoinWhoseLayoutPlacesOtherItemsIsRefused() throws Exception {
        edt(() -> {
            WorkspaceView target = owner.parentTabs().view(first).orElseThrow();
            Layout before = target.model(), arranged = owner.parentTabs().view(second).orElseThrow().model();
            GroupId pane = a.group().orElseThrow();
            assertThatThrownBy(() -> target.join(List.of(c), arranged, pane, Side.RIGHT)).as("one item short")
                .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> target.join(List.of(c, d, e), arranged, pane, Side.RIGHT)).as("one item more")
                .isInstanceOf(IllegalArgumentException.class);
            assertThat(target.model()).as("nothing changes").isEqualTo(before);
            assertThat(List.of(c, d, e)).noneMatch(target::contains);
        });
    }

    /**
     * Pane flexibility spec 5: after a merge between tabs that are not neighbours the strip draws its entries in its tabs'
     * order at once: the third tab into the first.
     */
    @Test void theStripKeepsItsTabsOrderAfterTheThirdTabMergesIntoTheFirst() throws Exception {
        edt(() -> {
            drawsInOrder("b", "d", "e");
            owner.moveTabInto(third, first);
            assertThat(ids()).containsExactly(first, second);
            drawsInOrder("e", "d");
        });
    }

    /** Pane flexibility spec 5: the shown tab, the first, into the third, which is not its neighbour. */
    @Test void theStripKeepsItsTabsOrderAfterTheShownTabMergesIntoAHiddenOne() throws Exception {
        edt(() -> {
            owner.moveTabInto(first, third);
            assertThat(ids()).containsExactly(second, third);
            drawsInOrder("d", "b");
        });
    }

    /** Workspace plan 8c's pane move: the third tab's last pane beside a pane of the first, which is not its neighbour. */
    @Test void theStripKeepsItsTabsOrderAfterATabsLastPaneMovesIntoATabThatIsNotItsNeighbour() throws Exception {
        edt(() -> {
            owner.movePane(e, a.group().orElseThrow(), Side.RIGHT);
            assertThat(ids()).containsExactly(first, second);
            assertThat(PaneDragTest.shape(owner)).isEqualTo("H[a, e, b]");
            drawsInOrder("e", "d");
        });
    }
}
