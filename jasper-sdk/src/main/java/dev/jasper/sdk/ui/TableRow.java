package dev.jasper.sdk.ui;

import java.util.List;
import java.util.Optional;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable tablerow descriptor. A {@code muted} row shows every cell in the de-emphasized text color (for example
 * entries that cannot be chosen); a present {@code contextActions} replaces the table's context menu for this row, and
 * an empty list means the row has none. {@code tooltip} is the row's literal tooltip and accessible description, shown
 * over every cell; blank shows none.
 */
public record TableRow(String id, Map<String,String> cells, Optional<String> icon, boolean muted, Optional<List<MenuItem>> contextActions, String tooltip) {
    public TableRow {
        ModelChecks.id(id);
        cells = Map.copyOf(cells); cells.keySet().forEach(ModelChecks::id); Objects.requireNonNull(icon); icon.ifPresent(name -> ModelChecks.label(name, "icon"));
        Objects.requireNonNull(contextActions); contextActions = contextActions.map(List::copyOf); contextActions.ifPresent(items -> ModelChecks.unique(items, MenuItem::id));
        Objects.requireNonNull(tooltip);
    }
    /** A row without a tooltip. */
    public TableRow(String id, Map<String,String> cells, Optional<String> icon, boolean muted, Optional<List<MenuItem>> contextActions) {
        this(id, cells, icon, muted, contextActions, "");
    }
    public TableRow(String id, Map<String,String> cells, Optional<String> icon) {
        this(id, cells, icon, false, Optional.empty());
    }
}
