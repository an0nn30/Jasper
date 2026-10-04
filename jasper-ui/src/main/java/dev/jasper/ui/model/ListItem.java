package dev.jasper.ui.model;

import java.util.Optional;
import java.util.List;
import java.util.Objects;

/** Immutable listitem descriptor. */
public record ListItem(String id, Optional<Nodes.Node> leading, List<Nodes.Node> title, List<Nodes.Node> subtitle, List<Nodes.Node> trailing) {
    public ListItem {
        ModelChecks.id(id);
        Objects.requireNonNull(leading); title = List.copyOf(title); subtitle = List.copyOf(subtitle); trailing = List.copyOf(trailing);
    }
}
