package dev.jasper.app.contributions;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import javax.swing.Icon;

/** A contributed action. Mutators notify every window; all of them do nothing after {@link #close()}. */
public final class ActionEntry {
    private final Contributions owner;
    private final String id;
    private final Icon icon;
    private final List<String> keywords;
    private final Optional<String> defaultBinding;
    private Consumer<Contributions.Invocation> handler;
    private String title;
    private boolean enabled = true;
    private boolean closed;

    ActionEntry(Contributions owner, String id, String title, Icon icon, List<String> keywords,
                Optional<String> defaultBinding, Consumer<Contributions.Invocation> handler) {
        this.owner = owner; this.id = id; this.title = title; this.icon = icon;
        this.keywords = List.copyOf(keywords); this.defaultBinding = defaultBinding; this.handler = handler;
    }

    public String id() { return id; }
    public String title() { return title; }
    /** The icon, or null. */
    public Icon icon() { return icon; }
    public List<String> keywords() { return keywords; }
    public Optional<String> defaultBinding() { return defaultBinding; }
    public boolean enabled() { return enabled && !closed; }

    /** Runs the contributor's handler unless the action is disabled or closed. */
    public void invoke(Contributions.Invocation invocation) {
        if (enabled()) handler.accept(invocation);
    }

    public void setEnabled(boolean value) {
        if (closed || enabled == value) return;
        enabled = value;
        owner.changed(Contributions.Kind.ACTIONS);
    }

    public void setTitle(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("An action needs a title");
        if (closed || title.equals(value)) return;
        title = value;
        owner.changed(Contributions.Kind.ACTIONS);
    }

    public void close() {
        if (closed) return;
        closed = true;
        handler = invocation -> { };
        owner.remove(this);
    }
}
