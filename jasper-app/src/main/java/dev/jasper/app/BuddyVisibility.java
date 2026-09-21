package dev.jasper.app;

import java.util.LinkedHashMap;
import java.util.Map;

/** Saved default, session toggle and terminal-window states → whether the buddy is on screen. */
final class BuddyVisibility {
    private record WindowState(boolean showing, boolean iconified) { }

    private final Map<Object, WindowState> windows = new LinkedHashMap<>();
    private boolean configured = true;
    private Boolean session;

    /** Applies the saved default; a changed saved value discards the session choice. */
    void configure(boolean enabled) {
        if (configured != enabled) session = null;
        configured = enabled;
    }

    void toggle() { session = !enabled(); }

    boolean enabled() { return session != null ? session : configured; }

    void window(Object key, boolean showing, boolean iconified) { windows.put(key, new WindowState(showing, iconified)); }

    void remove(Object key) { windows.remove(key); }

    void clear() { windows.clear(); }

    boolean shown() {
        if (!enabled()) return false;
        for (WindowState state : windows.values()) if (state.showing() && !state.iconified()) return true;
        return false;
    }
}
