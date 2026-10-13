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
    private java.util.function.Function<String, String> shortcuts = action -> "";
    public AppearanceBindings(ThemePair initial, Consumer<Throwable> failures, BooleanSupplier animations) {
        binding = new ThemeBinding(initial, failures);
        this.animations = java.util.Objects.requireNonNull(animations);
    }
    public ThemeBinding binding() { return binding; }
    /** The keymap text app-native menus show beside an item's action; contexts made earlier follow it. */
    public void shortcuts(java.util.function.Function<String, String> labels) { shortcuts = java.util.Objects.requireNonNull(labels); }
    public KitContext context(ThemeRoute route) {
        return new KitContext(binding, route, DesignMetrics.DEFAULT, icons, animations, action -> shortcuts.apply(action));
    }
    @Override public void close() { binding.close(); icons.close(); }
}
