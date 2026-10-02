package dev.jasper.app.contributions;

import dev.jasper.app.dock.Slot;
import java.util.function.Function;
import javax.swing.Icon;
import javax.swing.JComponent;

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

    public void close() {
        if (closed) return;
        closed = true;
        owner.remove(this);
    }
}
