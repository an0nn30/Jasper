package dev.jasper.app.notifications;

import java.util.LinkedHashMap;
import java.util.Map;

/** Saved default, session toggle and terminal-window states → whether the buddy is on screen. */
public final class BuddyVisibility {
    private record WindowState(boolean showing, boolean iconified) { }

    private final Map<Object, WindowState> windows = new LinkedHashMap<>();
    private boolean configured = true;
    private Boolean session;

    /** Applies the saved default; a changed saved value discards the session choice. */
    public void configure(boolean enabled) {
        if (configured != enabled) session = null;
        configured = enabled;
    }

    public void toggle() { session = !enabled(); }

    public boolean enabled() { return session != null ? session : configured; }

    public void window(Object key, boolean showing, boolean iconified) { windows.put(key, new WindowState(showing, iconified)); }

    public void remove(Object key) { windows.remove(key); }

    public void clear() { windows.clear(); }

    public boolean shown() {
        if (!enabled()) return false;
        for (WindowState state : windows.values()) if (state.showing() && !state.iconified()) return true;
        return false;
    }
}
