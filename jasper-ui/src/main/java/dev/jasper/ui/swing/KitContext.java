package dev.jasper.ui.swing;

import dev.jasper.ui.theme.*;
import dev.jasper.ui.metrics.*;
import dev.jasper.ui.icons.IconRegistry;
import java.util.Objects;
import java.util.function.BooleanSupplier;

/** Shared rendering values. Host owns binding/registry; each view owns its motion registrations. */
public final class KitContext {
    private final ThemeBinding binding;
    private final ThemeRoute route;
    private final DesignMetrics metrics;
    private final IconRegistry icons;
    private final BooleanSupplier animationsEnabled;
    public KitContext(ThemeBinding binding, ThemeRoute route, DesignMetrics metrics, IconRegistry icons, BooleanSupplier animationsEnabled) {
        this.binding = Objects.requireNonNull(binding); this.route = Objects.requireNonNull(route);
        this.metrics = Objects.requireNonNull(metrics); this.icons = Objects.requireNonNull(icons);
        this.animationsEnabled = Objects.requireNonNull(animationsEnabled);
    }
    public ThemeBinding binding() { return binding; }
    public ThemeRoute route() { return route; }
    public DesignMetrics metrics() { return metrics; }
    public IconRegistry icons() { return icons; }
    public BooleanSupplier animationsEnabled() { return animationsEnabled; }
    public GeneratedTheme theme() { return route.select(binding.current()); }
    /** User font settings survive theme installs. FlatLaf owns scaling; role fonts remain unscaled logical values. */
    public Typography typography() {
        // FlatLaf's LAF defaultFont is already scaled. A host's UIManager override is the unscaled user setting.
        java.awt.Font[] override = {null, null};
        javax.swing.UIManager.getDefaults().forEach((key, value) -> {
            if ("defaultFont".equals(key) && value instanceof java.awt.Font font) override[0] = font;
            if ("Label.font".equals(key) && value instanceof java.awt.Font font) override[1] = font;
        });
        return override[0] == null ? new Typography() : new Typography(override[0].getFamily(), (override[1] == null ? override[0] : override[1]).getSize2D());
    }
    public MotionPolicy motionPolicy() { return new MotionPolicy(animationsEnabled); }
}
