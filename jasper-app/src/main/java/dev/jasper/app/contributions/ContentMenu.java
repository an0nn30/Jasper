package dev.jasper.app.contributions;

import java.awt.Point;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import javax.swing.Icon;

/**
 * A plugin's right-click menu over one of its content items (browser plan 4b; browser spec 11;
 * {@code ContentTab.showContextMenu}): the plugin's items before and after the pane items the item's window adds, where
 * the menu opens and who hears the choice. EDT only.
 *
 * @param at where the menu opens, on screen; copied
 * @param leading the plugin's items above the window's pane items; copied
 * @param trailing the plugin's items below them; copied
 * @param chosen hears the id of the plugin's item chosen, or nothing when one of the window's items ran, the menu was
 *     dismissed or no menu could show; it hears once, later calls doing nothing
 */
public record ContentMenu(Point at, List<Item> leading, List<Item> trailing, Consumer<Optional<String>> chosen) {
    /**
     * One of the plugin's items, as the window shows it.
     *
     * @param id the item's id, which {@code chosen} hears; unique in the menu
     * @param label its text, plain
     * @param icon its icon before the label; null for none
     * @param enabled false shows it disabled
     * @param separator true makes it a separator line
     * @param shortcut the id of the action whose binding shows at its end; empty for none
     * @param children the items of the submenu it opens; copied, and empty for an item that opens none
     */
    public record Item(String id, String label, Icon icon, boolean enabled, boolean separator, String shortcut, List<Item> children) {
        /** Requires every value but the icon. */
        public Item {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(label, "label");
            Objects.requireNonNull(shortcut, "shortcut");
            children = List.copyOf(children);
        }
    }

    /** Requires every value; {@code chosen} hears once. */
    public ContentMenu {
        at = new Point(at);
        leading = List.copyOf(leading);
        trailing = List.copyOf(trailing);
        Objects.requireNonNull(chosen, "chosen");
        var once = chosen;
        boolean[] heard = {false};
        chosen = value -> {
            Objects.requireNonNull(value, "value");
            if (heard[0]) return;
            heard[0] = true;
            once.accept(value);
        };
    }

    /** Tells {@code chosen} nothing: one of the window's items ran, the menu was dismissed, or none could show. */
    public void dismissed() { chosen.accept(Optional.empty()); }
}
