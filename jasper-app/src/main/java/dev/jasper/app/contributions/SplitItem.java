package dev.jasper.app.contributions;

import java.util.Objects;
import javax.swing.Icon;

/**
 * A plugin's split item (tabs and panes spec 8 and 11; workspace plan 8b Rulings 3 and 17): what a new pane can hold,
 * listed in every window's Split lists with the two contributed actions that open it to the right of and below the
 * focused pane.
 *
 * @param id the item's namespaced id
 * @param label what the Split lists show before "Right" and "Down"
 * @param icon the item's icon, or null
 * @param rightAction the contributed action that opens it in a new pane to the right
 * @param downAction the contributed action that opens it in a new pane below
 * @param bundled whether a plugin that ships with Jasper registered it: the lists show those first (Rulings 5)
 */
public record SplitItem(String id, String label, Icon icon, String rightAction, String downAction, boolean bundled) {
    /** Requires every value but the icon. */
    public SplitItem {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(label, "label");
        Objects.requireNonNull(rightAction, "rightAction");
        Objects.requireNonNull(downAction, "downAction");
    }
}
