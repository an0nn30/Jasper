package dev.jasper.sdk.ui;

import java.util.Optional;
import java.util.Map;
import java.util.Objects;

/** Immutable tablerow descriptor. */
public record TableRow(String id, Map<String,String> cells, Optional<String> icon) {
    public TableRow {
        ModelChecks.id(id);
        cells = Map.copyOf(cells); cells.keySet().forEach(ModelChecks::id); Objects.requireNonNull(icon); icon.ifPresent(name -> ModelChecks.label(name, "icon"));
    }
}
