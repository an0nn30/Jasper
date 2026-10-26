package dev.jasper.app.contributions;

import dev.jasper.app.dock.Slot;
import dev.jasper.app.dock.ToolWindowMode;
import dev.jasper.app.lifecycle.Subscription;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** One panel instance's place in one window, as its contributor sees it. Created and notified by that window. EDT only. */
public final class PanelSite {
    private static final System.Logger LOG = System.getLogger(PanelSite.class.getName());

    /** Where the panel is: its slot (its stripe button's while it has a window of its own) and its mode. */
    public record Placement(Slot slot, ToolWindowMode mode) {
        public Placement {
            Objects.requireNonNull(slot, "slot");
            Objects.requireNonNull(mode, "mode");
        }
    }

    /** Where a site built outside a dock reports its panel. */
    private static final Placement NO_DOCK = new Placement(Slot.LEFT_TOP, ToolWindowMode.DOCKED);
    private final UUID windowId;
    private final Runnable show;
    private final Runnable hide;
    private final BooleanSupplier visible;
    private final List<Consumer<Boolean>> visibility = new ArrayList<>();
    private final List<Runnable> closed = new ArrayList<>();
    private final Consumer<PanelHeaderState> headerChanged;
    private final Supplier<Placement> placement;
    private final List<Consumer<Placement>> placements = new ArrayList<>();
    /** Counts placement notifications: one a listener's own move overtook stops reaching the listeners after it. */
    private int placementRound;
    /** Where the panel was when its instance was discarded: a closed site reports it, whatever its window does next. */
    private Placement closedIn;
    private boolean done;

    /** A site outside a dock, as tests and fixtures build: its panel reports {@link Slot#LEFT_TOP}, docked. */
    public PanelSite(UUID windowId, Runnable show, Runnable hide, BooleanSupplier visible) {
        this(windowId, show, hide, visible, header -> { });
    }

    /** As above, publishing the panel's header metadata. */
    public PanelSite(UUID windowId, Runnable show, Runnable hide, BooleanSupplier visible,
                     Consumer<PanelHeaderState> headerChanged) {
        this(windowId, show, hide, visible, headerChanged, () -> NO_DOCK);
    }

    /** A site in a window's dock, which reads the panel's place from {@code placement}. */
    public PanelSite(UUID windowId, Runnable show, Runnable hide, BooleanSupplier visible,
                     Consumer<PanelHeaderState> headerChanged, Supplier<Placement> placement) {
        this.headerChanged = Objects.requireNonNull(headerChanged);
        this.windowId = Objects.requireNonNull(windowId); this.show = Objects.requireNonNull(show);
        this.hide = Objects.requireNonNull(hide); this.visible = Objects.requireNonNull(visible);
        this.placement = Objects.requireNonNull(placement);
    }

    /** Publishes metadata while guarding callbacks against a discarded panel instance. */
    public void setHeader(PanelHeaderState header) {
        Objects.requireNonNull(header);
        if (done) return;
        headerChanged.accept(new PanelHeaderState(header.detail(), header.active(), header.actions().stream()
            .map(action -> new PanelHeaderState.Action(action.label(), action.icon(), () -> {
                if (!done) action.invoke().run();
            })).toList()));
    }

    public UUID windowId() { return windowId; }
    public void show() { if (!done) show.run(); }
    public void hide() { if (!done) hide.run(); }
    public boolean visible() { return !done && visible.getAsBoolean(); }
    public Placement placement() { return done ? closedIn : placement.get(); }

    public Subscription onVisibility(Consumer<Boolean> listener) {
        visibility.add(Objects.requireNonNull(listener));
        return new Subscription(() -> visibility.remove(listener));
    }

    public Subscription onPlacement(Consumer<Placement> listener) {
        placements.add(Objects.requireNonNull(listener));
        return new Subscription(() -> placements.remove(listener));
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

    /**
     * Called by the owning window after the panel moved to another slot or mode, once it is in its new place. A listener
     * that moves the panel again, which tells every listener the newer place, ends this round: the listeners after it
     * never hear the place it overtook.
     */
    public void notifyPlacement(Placement value) {
        Objects.requireNonNull(value);
        if (done) return;
        int round = ++placementRound;
        for (Consumer<Placement> listener : List.copyOf(placements)) {
            if (done || round != placementRound) return;
            try { listener.accept(value); }
            catch (RuntimeException failure) { LOG.log(System.Logger.Level.WARNING, "A panel placement listener failed", failure); }
        }
    }

    /** Called by the owning window, once, when the instance is discarded; the place it reads now is the one it keeps. */
    public void notifyClosed() {
        if (done) return;
        closedIn = placement.get();
        done = true;
        headerChanged.accept(PanelHeaderState.EMPTY);
        for (Runnable listener : List.copyOf(closed)) {
            try { listener.run(); }
            catch (RuntimeException failure) { LOG.log(System.Logger.Level.WARNING, "A panel close listener failed", failure); }
        }
        visibility.clear(); placements.clear(); closed.clear();
    }
}
