package dev.jasper.ui.swing;

import dev.jasper.ui.theme.*;
import java.awt.EventQueue;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import javax.swing.LookAndFeel;
import javax.swing.UIDefaults;
import javax.swing.UIManager;

/** Installs global state before publishing; failures restore both defaults layers and the previous LAF. */
public final class AppearanceTransaction {
    private final ThemeBinding binding;
    private final Consumer<GeneratedTheme> install;
    private final Consumer<GeneratedTheme> restore;
    private boolean applying;
    public AppearanceTransaction(ThemeBinding binding, Consumer<GeneratedTheme> install, Consumer<GeneratedTheme> restore) {
        this.binding = Objects.requireNonNull(binding); this.install = Objects.requireNonNull(install); this.restore = Objects.requireNonNull(restore);
    }
    public void apply(ThemePair next) {
        if (!EventQueue.isDispatchThread()) throw new IllegalStateException("Appearance requires EDT");
        Objects.requireNonNull(next);
        if (applying) throw new IllegalStateException("Appearance installation cannot re-enter");
        ThemePair old = binding.current();
        var snapshot = Snapshot.capture();
        applying = true;
        try {
            try {
                install.accept(next.chrome());
                binding.publish(next);
            } catch (RuntimeException | Error failure) {
                try { restore.accept(old.chrome()); } catch (RuntimeException | Error rollback) { suppress(failure, rollback); }
                try { snapshot.restore(); } catch (RuntimeException | Error rollback) { suppress(failure, rollback); }
                throw failure;
            }
        } finally { applying = false; }
    }
    private static void suppress(Throwable failure, Throwable rollback) {
        if (failure != rollback) failure.addSuppressed(rollback);
    }
    private record Snapshot(LookAndFeel laf, Map<Object, Object> lafDefaults, Map<Object, Object> overrides) {
        static Snapshot capture() { return new Snapshot(UIManager.getLookAndFeel(), raw(UIManager.getLookAndFeelDefaults()), raw(UIManager.getDefaults())); }
        private static Map<Object, Object> raw(UIDefaults defaults) {
            var result = new HashMap<Object, Object>();
            // Hashtable.forEach reads the actual table: unlike MultiUIDefaults.entrySet it does not merge LAF entries.
            defaults.forEach(result::put);
            return result;
        }
        void restore() {
            for (var key : raw(UIManager.getDefaults()).keySet()) UIManager.put(key, null);
            try { UIManager.setLookAndFeel(laf); }
            catch (javax.swing.UnsupportedLookAndFeelException failure) { throw new IllegalStateException("Cannot restore appearance", failure); }
            var defaults = UIManager.getLookAndFeelDefaults();
            defaults.clear(); defaults.putAll(lafDefaults);
            overrides.forEach(UIManager::put);
        }
    }
}
