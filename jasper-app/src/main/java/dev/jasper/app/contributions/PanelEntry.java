package dev.jasper.app.contributions;

import dev.jasper.app.dock.Slot;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.SwingUtilities;

/** A contributed panel. Each window asks the factory for its own instance, lazily, on first show. */
public final class PanelEntry {
    private final Contributions owner;
    private final String id;
    private final String title;
    private final Icon icon;
    private final Slot defaultSlot;
    private final Function<PanelSite, JComponent> factory;
    private final String toggleActionId;
    private final boolean namedToggle;
    private boolean closed;
    private PanelBadge badge;

    PanelEntry(Contributions owner, String id, String title, Icon icon, Slot defaultSlot,
               String toggleActionId, boolean namedToggle, Function<PanelSite, JComponent> factory) {
        this.owner = owner; this.id = id; this.title = title; this.icon = icon;
        this.defaultSlot = defaultSlot; this.factory = factory;
        this.toggleActionId = toggleActionId; this.namedToggle = namedToggle;
    }

    public String id() { return id; }
    public String title() { return title; }
    public Icon icon() { return icon; }
    /** Where the panel docks until the user moves it. */
    public Slot defaultSlot() { return defaultSlot; }
    /** The action View > Tools lists: the one the contributor named, or the application's {@code <id>.toggle}. */
    public String toggleActionId() { return toggleActionId; }
    /** Whether the contributor named its own toggle, so the application registered none. */
    public boolean namedToggle() { return namedToggle; }
    /** May return null or throw; the window then shows a placeholder. */
    public Function<PanelSite, JComponent> factory() { return factory; }

    /** The stripe button's unread mark, if any. */
    public Optional<PanelBadge> badge() { return Optional.ofNullable(badge); }

    /** Replaces the mark; every window's stripes re-render when it changed. EDT only. */
    public void setBadge(Optional<PanelBadge> value) {
        if (!SwingUtilities.isEventDispatchThread()) throw new IllegalStateException("Contributions belong to the EDT");
        PanelBadge next = value.orElse(null);
        if (closed || Objects.equals(badge, next)) return;
        badge = next;
        owner.changed(Contributions.Kind.RAIL);
    }

    public void close() {
        if (closed) return;
        closed = true;
        owner.remove(this);
    }
}
