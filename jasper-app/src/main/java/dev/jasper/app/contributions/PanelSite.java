package dev.jasper.app.contributions;

import dev.jasper.app.lifecycle.Subscription;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** One panel instance's place in one window, as its contributor sees it. Created and notified by that window. EDT only. */
public final class PanelSite {
    private static final System.Logger LOG = System.getLogger(PanelSite.class.getName());
    private final UUID windowId;
    private final Runnable show;
    private final Runnable hide;
    private final BooleanSupplier visible;
    private final List<Consumer<Boolean>> visibility = new ArrayList<>();
    private final List<Runnable> closed = new ArrayList<>();
    private boolean done;

    public PanelSite(UUID windowId, Runnable show, Runnable hide, BooleanSupplier visible) {
        this.windowId = Objects.requireNonNull(windowId); this.show = Objects.requireNonNull(show);
        this.hide = Objects.requireNonNull(hide); this.visible = Objects.requireNonNull(visible);
    }

    public UUID windowId() { return windowId; }
    public void show() { if (!done) show.run(); }
    public void hide() { if (!done) hide.run(); }
    public boolean visible() { return !done && visible.getAsBoolean(); }

    public Subscription onVisibility(Consumer<Boolean> listener) {
        visibility.add(Objects.requireNonNull(listener));
        return new Subscription(() -> visibility.remove(listener));
    }

    public Subscription onClosed(Runnable listener) {
        closed.add(Objects.requireNonNull(listener));
        return new Subscription(() -> closed.remove(listener));
    }

    /** Called by the owning window after the panel was shown or hidden there. */
    public void notifyVisibility(boolean value) {
        if (done) return;
        for (Consumer<Boolean> listener : List.copyOf(visibility)) {
            try { listener.accept(value); }
            catch (RuntimeException failure) { LOG.log(System.Logger.Level.WARNING, "A panel visibility listener failed", failure); }
        }
    }

    /** Called by the owning window, once, when the instance is discarded. */
    public void notifyClosed() {
        if (done) return;
        done = true;
        for (Runnable listener : List.copyOf(closed)) {
            try { listener.run(); }
            catch (RuntimeException failure) { LOG.log(System.Logger.Level.WARNING, "A panel close listener failed", failure); }
        }
        visibility.clear(); closed.clear();
    }
}
