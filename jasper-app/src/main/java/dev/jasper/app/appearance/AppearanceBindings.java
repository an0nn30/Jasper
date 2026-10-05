package dev.jasper.app.appearance;

import dev.jasper.ui.theme.*;
import dev.jasper.ui.swing.KitContext;
import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.icons.IconRegistry;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** Application-owned appearance and icons shared by app-native kit views. EDT confined. */
public final class AppearanceBindings implements AutoCloseable {
    private final ThemeBinding binding;
    private final IconRegistry icons = new IconRegistry();
    private final BooleanSupplier animations;
    public AppearanceBindings(ThemePair initial, Consumer<Throwable> failures, BooleanSupplier animations) {
        binding = new ThemeBinding(initial, failures);
        this.animations = java.util.Objects.requireNonNull(animations);
    }
    public ThemeBinding binding() { return binding; }
    public KitContext context(ThemeRoute route) {
        return new KitContext(binding, route, DesignMetrics.DEFAULT, icons, animations);
    }
    @Override public void close() { binding.close(); icons.close(); }
}
