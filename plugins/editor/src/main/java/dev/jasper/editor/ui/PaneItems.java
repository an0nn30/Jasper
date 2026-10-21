package dev.jasper.editor.ui;

import dev.jasper.sdk.terminal.Direction;
import dev.jasper.sdk.ui.ContentTab;
import dev.jasper.sdk.ui.SplitChoice;
import java.awt.Component;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import javax.swing.JPopupMenu;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;

/**
 * The pane entries of an editor's right-click menu (tabs and panes spec 2.10 and 9; workspace plan 8b Rulings 10): after
 * a rule, a Split submenu with Jasper's Split choices, each Right and Down, and Move Pane to New Tab, which shows
 * disabled while the editor is its tab's only pane. They are built again each time the menu opens, so a split item that
 * registers or goes later shows at once. {@link DocumentArea} builds the menu and its items, as the plugin presentation
 * check lets only it construct the vendor popup's controls. UI thread only.
 */
final class PaneItems implements PopupMenuListener {
    private final JPopupMenu popup;
    private final Supplier<List<SplitChoice>> choices;
    private final ContentTab pane;
    private final List<Component> added = new ArrayList<>();

    private PaneItems(JPopupMenu popup, Supplier<List<SplitChoice>> choices, ContentTab pane) {
        this.popup = popup;
        this.choices = choices;
        this.pane = pane;
    }

    /**
     * Adds the entries at the end of {@code popup}, for the editor {@code pane} holds, and builds them again whenever the
     * menu opens.
     *
     * @param popup the text area's right-click menu
     * @param choices Jasper's Split choices, asked each time
     * @param pane the editor's item
     * @return the entries' owner
     */
    static PaneItems install(JPopupMenu popup, Supplier<List<SplitChoice>> choices, ContentTab pane) {
        var items = new PaneItems(popup, choices, pane);
        popup.addPopupMenuListener(items);
        items.rebuild();
        return items;
    }

    /** The entries again, from the current choices and whether the pane may move; none once the pane is closed. */
    void rebuild() {
        for (Component component : added) popup.remove(component);
        added.clear();
        if (pane.isClosed()) return;
        var split = DocumentArea.submenu("Split");
        for (SplitChoice choice : choices.get()) {
            split.add(DocumentArea.plainItem(choice.label() + " Right", () -> splitIfListed(choice, Direction.RIGHT)));
            split.add(DocumentArea.plainItem(choice.label() + " Down", () -> splitIfListed(choice, Direction.DOWN)));
        }
        var move = DocumentArea.plainItem("Move Pane to New Tab", pane::moveToNewTab);
        move.setEnabled(pane.mayMoveToNewTab());
        popup.addSeparator();
        added.add(popup.getComponent(popup.getComponentCount() - 1));
        add(split);
        add(move);
    }

    /** Splits beside the pane, unless the choice is no longer listed: an entry whose split item went does nothing. */
    private void splitIfListed(SplitChoice choice, Direction direction) {
        if (choices.get().contains(choice)) pane.split(choice, direction);
    }

    private void add(Component component) {
        popup.add(component);
        added.add(component);
    }

    @Override public void popupMenuWillBecomeVisible(PopupMenuEvent event) { rebuild(); }
    @Override public void popupMenuWillBecomeInvisible(PopupMenuEvent event) { }
    @Override public void popupMenuCanceled(PopupMenuEvent event) { }
}
