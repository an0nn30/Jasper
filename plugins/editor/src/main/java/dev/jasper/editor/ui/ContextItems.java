package dev.jasper.editor.ui;

import dev.jasper.editor.api.ContextRequest;
import dev.jasper.sdk.Subscription;
import dev.jasper.sdk.ui.ContentTab;
import java.awt.Component;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.swing.JPopupMenu;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;

/**
 * The items other plugins add to every Editor tab's right-click menu with {@code Editor.contextItem}, in registration
 * order. Each menu shows them last, after a rule, and builds them again each time it opens, so an item that registers
 * or goes later shows at once. UI thread only.
 */
public final class ContextItems {
    /** One registration: the item's title and what choosing it runs. */
    private record Item(String title, Consumer<ContextRequest> run) { }

    private final List<Item> items = new CopyOnWriteArrayList<>();
    private final Consumer<RuntimeException> failed;

    /** @param failed told each failure of an item that threw */
    public ContextItems(Consumer<RuntimeException> failed) { this.failed = Objects.requireNonNull(failed, "failed"); }

    /**
     * @param title the item's title, shown as plain text
     * @param run told what the item was chosen on
     * @return removes the item
     * @throws IllegalArgumentException when {@code title} is blank
     */
    public Subscription register(String title, Consumer<ContextRequest> run) {
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(run, "run");
        if (title.isBlank()) throw new IllegalArgumentException("A context item needs a title");
        var item = new Item(title, run);
        items.add(item);
        return () -> items.removeIf(each -> each == item);
    }

    /**
     * Adds the items at the end of {@code popup}, for the editor {@code pane} holds, and builds them again whenever the
     * menu opens.
     *
     * @param popup the text area's right-click menu
     * @param pane the editor's item; once it is closed the menu shows none
     * @param request what an item is chosen on, asked when it is chosen
     */
    void install(JPopupMenu popup, ContentTab pane, Supplier<ContextRequest> request) {
        var entries = new Entries(popup, pane, request);
        popup.addPopupMenuListener(entries);
        entries.rebuild();
    }

    private void choose(Item item, ContextRequest request) {
        try { item.run().accept(request); }
        catch (RuntimeException failure) { failed.accept(failure); }
    }

    /** One menu's entries for the items; {@link DocumentArea} builds them, as only it may construct the vendor popup's controls. */
    private final class Entries implements PopupMenuListener {
        private final JPopupMenu popup;
        private final ContentTab pane;
        private final Supplier<ContextRequest> request;
        private final List<Component> added = new ArrayList<>();

        Entries(JPopupMenu popup, ContentTab pane, Supplier<ContextRequest> request) {
            this.popup = popup;
            this.pane = pane;
            this.request = request;
        }

        void rebuild() {
            for (Component component : added) popup.remove(component);
            added.clear();
            if (pane.isClosed() || items.isEmpty()) return;
            popup.addSeparator();
            added.add(popup.getComponent(popup.getComponentCount() - 1));
            for (Item item : items) {
                var entry = DocumentArea.plainItem(item.title(), () -> choose(item, request.get()));
                popup.add(entry);
                added.add(entry);
            }
        }

        @Override public void popupMenuWillBecomeVisible(PopupMenuEvent event) { rebuild(); }
        @Override public void popupMenuWillBecomeInvisible(PopupMenuEvent event) { }
        @Override public void popupMenuCanceled(PopupMenuEvent event) { }
    }
}
