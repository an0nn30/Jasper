package dev.jasper.app.workspace;

import static dev.jasper.app.workspace.DesktopTestSupport.*;
import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.app.appearance.ThemeTestSupport;
import dev.jasper.app.config.AnimationSettings;
import dev.jasper.app.contributions.ContentMenu;
import dev.jasper.app.contributions.Contributions;
import dev.jasper.app.contributions.Tab;
import dev.jasper.app.contributions.TabRegistry;
import dev.jasper.app.terminals.TerminalRegistry;
import java.awt.Component;
import java.awt.Point;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Browser plan 4b Task 1 (SDK 0.27.0; browser spec 11, ruling P2): a plugin's right-click menu over its item in a real
 * window: the plugin's leading items, the pane items of the terminal's right-click menu (Split, Move Pane to Tab, Close
 * Pane), then its trailing items, a rule between groups. The window holds "a" beside "b" in its first tab and "c" in its
 * second; the menu opens over "b". A headless window shows nothing on screen, so the test records what would show.
 */
class ContentContextMenuTest {
    private final ArrayDeque<Runnable> pending = new ArrayDeque<>();
    private final Contributions model = new Contributions();
    private final TerminalRegistry terminals = new TerminalRegistry();
    private final List<JPopupMenu> shown = new ArrayList<>();
    private final List<Optional<String>> heard = new CopyOnWriteArrayList<>();
    private WindowContent owner;
    private Tab a, b, c;

    @BeforeEach void window() throws Exception {
        edt(() -> {
            ThemeTestSupport.install(true);
            owner = content(idleLauncher(pending), null, true);
            owner.setAnimations(new AnimationSettings(false, 0, 0, 0));
            owner.connectContributions(model);
            owner.connectTerminals(terminals, () -> { });
            a = view("a", TabRegistry.Placement.NEW_TAB);
            b = view("b", TabRegistry.Placement.SPLIT_RIGHT);
            c = view("c", TabRegistry.Placement.NEW_TAB);
            owner.contentMenuPopup = (popup, at) -> { shown.add(popup); return true; };
        });
    }

    @AfterEach void cleanup() throws Exception { closeOwners(); }

    private Tab view(String title, TabRegistry.Placement placement) {
        return model.tabs().open("dev.example.notes", title, title, owner.id(), new JPanel(), placement);
    }

    private static ContentMenu.Item item(String id, String label, boolean enabled) {
        return new ContentMenu.Item(id, label, null, enabled, false, "", List.of());
    }

    private ContentMenu menu(List<ContentMenu.Item> leading, List<ContentMenu.Item> trailing) {
        return new ContentMenu(new Point(20, 20), leading, trailing, heard::add);
    }

    /** The popup's rows: each item's text, "---" for a rule, a submenu's children after it indented. */
    private static List<String> rows(Component[] children, String indent) {
        var rows = new ArrayList<String>();
        for (Component child : children) {
            if (child instanceof JPopupMenu.Separator) rows.add(indent + "---");
            else if (child instanceof JMenu menu) { rows.add(indent + menu.getText() + (menu.isEnabled() ? "" : " (disabled)")); rows.addAll(rows(menu.getMenuComponents(), indent + "  ")); }
            else if (child instanceof JMenuItem item) rows.add(indent + item.getText() + (item.isEnabled() ? "" : " (disabled)"));
        }
        return rows;
    }

    private static JMenuItem row(Component[] children, String text) {
        for (Component child : children) if (child instanceof JMenuItem item && text.equals(item.getText())) return item;
        throw new AssertionError("no " + text);
    }

    @Test void theMenuListsTheLeadingItemsThePaneItemsThenTheTrailingItemsAndAPluginItemTellsItsId() throws Exception {
        edt(() -> {
            owner.selectTab(a);
            var more = new ContentMenu.Item("more", "More", null, true, false, "", List.of(item("source", "View Source", true)));
            model.tabs().showContextMenu(b, menu(List.of(item("back", "Back", true), item("forward", "Forward", false)), List.of(more)));
            assertThat(owner.selectedTab()).as("the item is selected, so the pane items act on its pane").isSameAs(b);
            assertThat(shown).hasSize(1);
            JPopupMenu popup = shown.getFirst();
            assertThat(popup.getInvoker()).isSameAs(b.component());
            assertThat(rows(popup.getComponents(), "")).containsExactly("Back", "Forward (disabled)", "---",
                "Split", "  Terminal Right", "  Terminal Down", "Move Pane to Tab", "  2 c", "Close Pane", "---", "More", "  View Source");
            ((JMenuItem) ((JMenu) row(popup.getComponents(), "More")).getMenuComponent(0)).doClick(0);
            row(popup.getComponents(), "Back").doClick(0);
            assertThat(heard).as("a submenu's child, once").containsExactly(Optional.of("source"));
        });
    }

    /** Review C2: a pane item runs, then the plugin hears nothing, as the fake and the contract say. */
    @Test void emptyGroupsAddNoRuleAndAPaneItemRunsAndTellsNothing() throws Exception {
        var shapes = new CopyOnWriteArrayList<String>();
        edt(() -> {
            model.tabs().showContextMenu(b, new ContentMenu(new Point(20, 20), List.of(), List.of(), value -> {
                heard.add(value);
                shapes.add(PaneDragTest.shape(owner));
            }));
            JPopupMenu popup = shown.getFirst();
            assertThat(rows(popup.getComponents(), "")).containsExactly("Split", "  Terminal Right", "  Terminal Down",
                "Move Pane to Tab", "  2 c", "Close Pane");
            ((JMenuItem) ((JMenu) row(popup.getComponents(), "Move Pane to Tab")).getMenuComponent(0)).doClick(0);
            assertThat(PaneDragTest.shape(owner)).as("b moved into c's tab").isEqualTo("H[c, b]");
            assertThat(owner.selectedTab()).isSameAs(b);
        });
        edt(() -> { });
        assertThat(heard).containsExactly(Optional.empty());
        assertThat(shapes).as("the plugin heard after the item ran").containsExactly("H[c, b]");
    }

    /** The popup closing, as Swing tells its listeners when a choice, Escape or a click elsewhere hides it. */
    private static void closing(JPopupMenu popup) {
        var event = new javax.swing.event.PopupMenuEvent(popup);
        for (var listener : popup.getPopupMenuListeners()) listener.popupMenuWillBecomeInvisible(event);
    }

    @Test void dismissingTellsNothingAfterAnyChoiceAndClosingTheItemDismissesIt() throws Exception {
        edt(() -> model.tabs().showContextMenu(b, menu(List.of(item("back", "Back", true)), List.of())));
        edt(() -> {
            JPopupMenu popup = shown.getFirst();
            // A choice closes the popup first, then runs the item: the item's id wins over the closing.
            closing(popup);
            row(popup.getComponents(), "Back").doClick(0);
        });
        edt(() -> { });
        assertThat(heard).containsExactly(Optional.of("back"));
        edt(() -> {
            model.tabs().showContextMenu(b, menu(List.of(item("back", "Back", true)), List.of()));
            closing(shown.getLast());
        });
        edt(() -> { });
        assertThat(heard).as("dismissed").containsExactly(Optional.of("back"), Optional.empty());
        edt(() -> {
            model.tabs().showContextMenu(b, menu(List.of(), List.of()));
            b.close();
        });
        assertThat(heard).as("its item closed").containsExactly(Optional.of("back"), Optional.empty(), Optional.empty());
    }

    @Test void anItemNotOnScreenOrClosedShowsNoneAndTellsNothingAtOnce() throws Exception {
        edt(() -> {
            owner.contentMenuPopup = (popup, at) -> false;
            model.tabs().showContextMenu(b, menu(List.of(item("back", "Back", true)), List.of()));
            assertThat(heard).as("not on screen").containsExactly(Optional.empty());
            c.close();
            model.tabs().showContextMenu(c, menu(List.of(), List.of()));
            assertThat(heard).as("closed").containsExactly(Optional.empty(), Optional.empty());
        });
    }
}
