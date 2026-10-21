package dev.jasper.app.workspace;

import static dev.jasper.app.workspace.DesktopTestSupport.*;
import static dev.jasper.app.workspace.TabDragTestSupport.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.jasper.app.appearance.ThemeTestSupport;
import dev.jasper.app.config.AnimationSettings;
import dev.jasper.app.contributions.Contributions;
import dev.jasper.app.contributions.Tab;
import dev.jasper.app.contributions.TabRegistry;
import dev.jasper.app.layout.Child;
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
import java.util.List;
import javax.swing.JComponent;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Pane flexibility spec 5 within one window: a whole tab moves into another tab of its window as one nested split beside
 * a pane, from Move Tab Into in a tab's menu and, on synthetic events over a stand-in frame on a pretend screen, by its
 * entry dragged onto a pane. The main window holds three tabs of plugin views: the first "a" beside "b" (H[a, b]), "b"
 * active and showing; the second "c" above "d" (V[c, d]), "d" active; the third "e". Its strip shows, as it holds three
 * tabs; no window manager opened it, so its drag is its own.
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
            for (Component child : moves.getMenuComponents())
                assertThat(((JMenuItem) child).getClientProperty(javax.swing.plaf.basic.BasicHTML.propertyKey))
                    .as("%s renders no HTML", ((JMenuItem) child).getText()).isNull();
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
}
