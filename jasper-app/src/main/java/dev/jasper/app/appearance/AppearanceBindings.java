package dev.jasper.app.appearance;

import dev.jasper.ui.theme.*;
import dev.jasper.ui.swing.FlatLafInstaller;
import dev.jasper.ui.swing.KitContext;
import dev.jasper.ui.icons.IconRegistry;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** Application-owned appearance and icons shared by app-native kit views. EDT confined. */
public final class AppearanceBindings implements AutoCloseable {
    private final ThemeBinding binding;
    private final IconRegistry icons = new IconRegistry();
    private final BooleanSupplier animations;
    private final java.util.function.Supplier<dev.jasper.ui.kit.Kit> kit;
    private java.util.function.Function<String, String> shortcuts = action -> "";
    /** Bindings whose contexts carry the installed look and feel's kit, read when each context is made. */
    public AppearanceBindings(ThemePair initial, Consumer<Throwable> failures, BooleanSupplier animations) {
        this(initial, FlatLafInstaller::installedKit, failures, animations);
    }
    /** Bindings whose contexts carry {@code kit}, the presentation's. */
    public AppearanceBindings(ThemePair initial, dev.jasper.ui.kit.Kit kit, Consumer<Throwable> failures, BooleanSupplier animations) {
        this(initial, () -> kit, failures, animations);
        java.util.Objects.requireNonNull(kit);
    }
    private AppearanceBindings(ThemePair initial, java.util.function.Supplier<dev.jasper.ui.kit.Kit> kit, Consumer<Throwable> failures,
                               BooleanSupplier animations) {
        binding = new ThemeBinding(initial, failures);
        this.animations = java.util.Objects.requireNonNull(animations);
        this.kit = kit;
    }
    public ThemeBinding binding() { return binding; }
    /** The keymap text app-native menus show beside an item's action; contexts made earlier follow it. */
    public void shortcuts(java.util.function.Function<String, String> labels) { shortcuts = java.util.Objects.requireNonNull(labels); }
    /** A context for {@code route} over this binding's kit. */
    public KitContext context(ThemeRoute route) {
        return KitContext.of(kit.get(), binding, route, icons, animations, action -> shortcuts.apply(action));
    }
    @Override public void close() { binding.close(); icons.close(); }
}
