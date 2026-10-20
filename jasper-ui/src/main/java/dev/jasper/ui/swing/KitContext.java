package dev.jasper.ui.swing;

import dev.jasper.ui.icons.IconRegistry;
import dev.jasper.ui.kit.Kit;
import dev.jasper.ui.metrics.*;
import dev.jasper.ui.theme.*;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Function;

/**
 * Shared rendering values. Host owns binding/registry; each view owns its motion registrations. The kit, which every
 * metric, type and face read follows, is fixed for the context's life.
 */
public final class KitContext {
    private final Kit kit;
    private final ThemeBinding binding;
    /** Changed only through the one view that owns this copy (KitView.route), on the EDT. */
    private volatile ThemeRoute route;
    private final IconRegistry icons;
    private final BooleanSupplier animationsEnabled;
    private final Function<String, String> shortcuts;
    private final KitFaces faces;

    private KitContext(Kit kit, ThemeBinding binding, ThemeRoute route, IconRegistry icons, BooleanSupplier animationsEnabled,
                       Function<String, String> shortcuts) {
        this.kit = Objects.requireNonNull(kit);
        this.binding = Objects.requireNonNull(binding); this.route = Objects.requireNonNull(route);
        this.icons = Objects.requireNonNull(icons);
        this.animationsEnabled = Objects.requireNonNull(animationsEnabled); this.shortcuts = Objects.requireNonNull(shortcuts);
        this.faces = KitFaces.of(kit);
    }
    /** A view's private copy, so re-routing one view never re-routes another sharing the caller's context. */
    KitContext(KitContext source) {
        this(source.kit, source.binding, source.route, source.icons, source.animationsEnabled, source.shortcuts);
    }
    /**
     * A context over {@code kit}; {@code shortcuts} maps an action id to its current binding as menus show it ("" when
     * unbound).
     */
    public static KitContext of(Kit kit, ThemeBinding binding, ThemeRoute route, IconRegistry icons, BooleanSupplier animationsEnabled,
                                Function<String, String> shortcuts) {
        return new KitContext(kit, binding, route, icons, animationsEnabled, shortcuts);
    }
    /** The kit this context renders with. */
    public Kit kit() { return kit; }
    /** The faces the kit's controls draw with, chosen once for this context. */
    KitFaces faces() { return faces; }
    /** The action-id-to-binding text menus use. */
    public Function<String, String> shortcuts() { return shortcuts; }
    /** The current binding of {@code actionId} as a menu shows it; "" for none or for an empty id. */
    public String shortcut(String actionId) { return actionId.isEmpty() ? "" : Objects.requireNonNullElse(shortcuts.apply(actionId), ""); }
    void route(ThemeRoute value) { route = Objects.requireNonNull(value); }
    public ThemeBinding binding() { return binding; }
    public ThemeRoute route() { return route; }
    /** The kit's metrics. */
    public DesignMetrics metrics() { return kit.metrics(); }
    public IconRegistry icons() { return icons; }
    public BooleanSupplier animationsEnabled() { return animationsEnabled; }
    public GeneratedTheme theme() { return route.select(binding.current()); }
    /**
     * The kit's type scale and faces with the user's font settings, read live so they survive theme installs. FlatLaf owns
     * scaling; role fonts remain unscaled logical values.
     */
    public Typography typography() {
        return SwingTypography.typography(kit);
    }
    public MotionPolicy motionPolicy() { return new MotionPolicy(animationsEnabled); }
}
