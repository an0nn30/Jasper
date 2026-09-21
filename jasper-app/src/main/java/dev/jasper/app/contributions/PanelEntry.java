package dev.jasper.app.contributions;

import java.util.function.Function;
import javax.swing.Icon;
import javax.swing.JComponent;

/** A contributed panel. Each window asks the factory for its own instance, lazily, on first show. */
public final class PanelEntry {
    private final Contributions owner;
    private final String id;
    private final String title;
    private final Icon icon;
    private final PanelRegion defaultRegion;
    private final Function<PanelSite, JComponent> factory;
    private boolean closed;

    PanelEntry(Contributions owner, String id, String title, Icon icon, PanelRegion defaultRegion,
               Function<PanelSite, JComponent> factory) {
        this.owner = owner; this.id = id; this.title = title; this.icon = icon;
        this.defaultRegion = defaultRegion; this.factory = factory;
    }

    public String id() { return id; }
    public String title() { return title; }
    public Icon icon() { return icon; }
    public PanelRegion defaultRegion() { return defaultRegion; }
    /** May return null or throw; the window then shows a placeholder. */
    public Function<PanelSite, JComponent> factory() { return factory; }

    public void close() {
        if (closed) return;
        closed = true;
        owner.remove(this);
    }
}
