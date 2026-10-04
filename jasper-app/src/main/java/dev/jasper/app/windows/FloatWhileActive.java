package dev.jasper.app.windows;

import dev.jasper.app.lifecycle.Subscription;
import java.awt.KeyboardFocusManager;
import java.awt.Window;
import java.beans.PropertyChangeListener;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import javax.swing.SwingUtilities;

/**
 * Keeps a modal dialog above every Jasper window while Jasper is the active application, and at the normal level
 * while another application is. macOS orders a clicked window to the front before Java finds it blocked, so without
 * this the window a dialog blocks can cover it. The windows a dialog owns float with it. EDT only.
 */
final class FloatWhileActive implements AutoCloseable {
    private final BooleanSupplier applicationActive;
    private final Consumer<Boolean> alwaysOnTop;
    private final Subscription listening;
    private Boolean applied;
    private boolean closed;

    /** Floats {@code window} while any window of this application is the active one. */
    static FloatWhileActive attach(Window window) {
        KeyboardFocusManager focus = KeyboardFocusManager.getCurrentKeyboardFocusManager();
        return new FloatWhileActive(changed -> {
            // Switching between two Jasper windows passes through no active window; settle once the switch is done.
            PropertyChangeListener listener = event -> SwingUtilities.invokeLater(changed);
            focus.addPropertyChangeListener("activeWindow", listener);
            return new Subscription(() -> focus.removePropertyChangeListener("activeWindow", listener));
        }, () -> focus.getActiveWindow() != null, window::setAlwaysOnTop);
    }

    FloatWhileActive(Function<Runnable, Subscription> onActiveWindowChange, BooleanSupplier applicationActive,
                     Consumer<Boolean> alwaysOnTop) {
        this.applicationActive = Objects.requireNonNull(applicationActive);
        this.alwaysOnTop = Objects.requireNonNull(alwaysOnTop);
        listening = onActiveWindowChange.apply(this::update);
    }

    /** Floats the dialog when Jasper is active and lets it drop otherwise; an unchanged state is not reapplied. */
    void update() {
        if (closed) return;
        boolean active = applicationActive.getAsBoolean();
        if (Boolean.valueOf(active).equals(applied)) return;
        applied = active;
        alwaysOnTop.accept(active);
    }

    @Override public void close() {
        closed = true;
        listening.close();
    }
}
