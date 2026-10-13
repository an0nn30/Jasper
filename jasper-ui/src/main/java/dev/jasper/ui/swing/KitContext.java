package dev.jasper.ui.swing;

import dev.jasper.ui.theme.*;
import dev.jasper.ui.metrics.*;
import dev.jasper.ui.icons.IconRegistry;
import java.util.Objects;
import java.util.function.BooleanSupplier;

/** Shared rendering values. Host owns binding/registry; each view owns its motion registrations. */
public final class KitContext {
    private final ThemeBinding binding;
    /** Changed only through the one view that owns this copy (KitView.route), on the EDT. */
    private volatile ThemeRoute route;
    private final DesignMetrics metrics;
    private final IconRegistry icons;
    private final BooleanSupplier animationsEnabled;
    private final java.util.function.Function<String, String> shortcuts;
    public KitContext(ThemeBinding binding, ThemeRoute route, DesignMetrics metrics, IconRegistry icons, BooleanSupplier animationsEnabled) {
        this(binding, route, metrics, icons, animationsEnabled, action -> "");
    }
    /** {@code shortcuts} maps an action id to its current binding as menus show it ("" when unbound). */
    public KitContext(ThemeBinding binding, ThemeRoute route, DesignMetrics metrics, IconRegistry icons, BooleanSupplier animationsEnabled,
                      java.util.function.Function<String, String> shortcuts) {
        this.binding = Objects.requireNonNull(binding); this.route = Objects.requireNonNull(route);
        this.metrics = Objects.requireNonNull(metrics); this.icons = Objects.requireNonNull(icons);
        this.animationsEnabled = Objects.requireNonNull(animationsEnabled); this.shortcuts = Objects.requireNonNull(shortcuts);
    }
    /** A view's private copy, so re-routing one view never re-routes another sharing the caller's context. */
    KitContext(KitContext source) {
        this(source.binding, source.route, source.metrics, source.icons, source.animationsEnabled, source.shortcuts);
    }
    /** The action-id-to-binding text menus use. */
    public java.util.function.Function<String, String> shortcuts() { return shortcuts; }
    /** The current binding of {@code actionId} as a menu shows it; "" for none or for an empty id. */
    public String shortcut(String actionId) { return actionId.isEmpty() ? "" : Objects.requireNonNullElse(shortcuts.apply(actionId), ""); }
    void route(ThemeRoute value) { route = Objects.requireNonNull(value); }
    public ThemeBinding binding() { return binding; }
    public ThemeRoute route() { return route; }
    public DesignMetrics metrics() { return metrics; }
    public IconRegistry icons() { return icons; }
    public BooleanSupplier animationsEnabled() { return animationsEnabled; }
    public GeneratedTheme theme() { return route.select(binding.current()); }
    /** User font settings survive theme installs. FlatLaf owns scaling; role fonts remain unscaled logical values. */
    public Typography typography() {
        return SwingTypography.typography();
    }
    public MotionPolicy motionPolicy() { return new MotionPolicy(animationsEnabled); }
}
