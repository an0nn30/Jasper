package dev.jasper.app.contributions;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import javax.swing.Icon;
import javax.swing.JComponent;

/** One contributed toolbar control. */
public sealed interface ToolbarEntry permits ToolbarEntry.Button, ToolbarEntry.Dropdown, ToolbarEntry.View {
    /** A button for one contributed action. */
    record Button(String actionId) implements ToolbarEntry { }

    /** A button that opens a menu of contributed actions. */
    record Dropdown(Icon icon, String title, List<String> actionIds) implements ToolbarEntry {
        public Dropdown { actionIds = List.copyOf(actionIds); }
    }

    /**
     * A contributed group of controls, one per window: each window asks {@code factory} once, with its id, when it opens,
     * and closes what it got when it closes or this entry goes. Empty means the contributor's factory failed, and that
     * window shows nothing for it.
     */
    record View(Function<UUID, Optional<Mounted>> factory) implements ToolbarEntry {
        public View { Objects.requireNonNull(factory, "factory"); }
    }

    /** One window's built view: the component the toolbar places, and its idempotent close. */
    record Mounted(JComponent component, Runnable closer) implements AutoCloseable {
        public Mounted {
            Objects.requireNonNull(component, "component");
            Objects.requireNonNull(closer, "closer");
        }

        @Override public void close() { closer.run(); }
    }
}
