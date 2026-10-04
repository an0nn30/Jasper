package dev.jasper.app.windows;

import dev.jasper.app.persistence.UiState;
import java.awt.Dimension;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;

/** Creates and tracks auxiliary surfaces, reuses open singletons and remembers window bounds. EDT only. */
public final class AuxiliaryWindows implements AutoCloseable {
    private final PathChooser chooser;
    private final UiState state;
    private final Function<AuxiliarySurface, AuxiliarySurface.Shell> shells;
    private final List<AuxiliarySurface> open = new ArrayList<>();

    public AuxiliaryWindows(UiState state, Function<AuxiliarySurface, AuxiliarySurface.Shell> shells) {
        this(state, shells, (choice, cancellation) -> List.of());
    }

    public AuxiliaryWindows(UiState state, Function<AuxiliarySurface, AuxiliarySurface.Shell> shells, PathChooser chooser) {
        this.chooser = Objects.requireNonNull(chooser);
        this.state = Objects.requireNonNull(state);
        this.shells = Objects.requireNonNull(shells);
    }

    /** Synchronous chooser boundary; caller owns cancellation registration and validates the owner. */
    public List<dev.jasper.app.files.Location> choose(PathChoice choice, java.util.function.Consumer<Runnable> cancellation) {
        if (choice.auxiliaryOwner() != null && (!open.contains(choice.auxiliaryOwner()) || !choice.auxiliaryOwner().shown()))
            throw new IllegalArgumentException("Picker requires a shown, open owner");
        return List.copyOf(chooser.choose(choice, cancellation));
    }

    public AuxiliarySurface window(String id, String title, Dimension preferred, boolean singleton) {
        if (singleton)
            for (AuxiliarySurface existing : open)
                if (existing.kind() == AuxiliarySurface.Kind.WINDOW && existing.id().equals(id)) return existing;
        return track(new AuxiliarySurface(id, title, AuxiliarySurface.Kind.WINDOW, false, preferred, null, null, shells));
    }

    public AuxiliarySurface dialog(String title, boolean modal, UUID ownerWindow) {
        return track(new AuxiliarySurface("", title, AuxiliarySurface.Kind.DIALOG, modal, new Dimension(1, 1),
            Objects.requireNonNull(ownerWindow), null, shells));
    }

    public AuxiliarySurface dialog(String title, boolean modal, AuxiliarySurface owner) {
        if (owner.closed() || !open.contains(owner)) throw new IllegalArgumentException("A dialog needs an open owner window");
        return track(new AuxiliarySurface("", title, AuxiliarySurface.Kind.DIALOG, modal, new Dimension(1, 1), null, owner, shells));
    }

    /**
     * A modal dialog that remembers its bounds under {@code id}: over the terminal window {@code ownerWindowOrNull}, or
     * application-modal with no owner when it is null. Showing it blocks until it closes.
     */
    public AuxiliarySurface modalDialog(String id, String title, Dimension preferred, UUID ownerWindowOrNull) {
        if (Objects.requireNonNull(id, "id").isBlank()) throw new IllegalArgumentException("A remembered dialog needs an id");
        return track(new AuxiliarySurface(id, title, AuxiliarySurface.Kind.DIALOG, true, preferred, ownerWindowOrNull, null, shells));
    }

    /** Reserves one overlay per terminal owner, including surfaces not shown yet. */
    public AuxiliarySurface overlay(String title, UUID ownerWindow) {
        Objects.requireNonNull(ownerWindow);
        if (open.stream().anyMatch(surface -> surface.kind() == AuxiliarySurface.Kind.OVERLAY
                && surface.ownerWindow().filter(ownerWindow::equals).isPresent()))
            throw new IllegalStateException("This window already has an overlay");
        return track(new AuxiliarySurface("", title, AuxiliarySurface.Kind.OVERLAY, false,
            new Dimension(1, 1), ownerWindow, null, shells));
    }

    /** Removes terminal-owned surfaces even if they have never been shown. */
    public void closeOwned(UUID ownerWindow) {
        for (AuxiliarySurface surface : List.copyOf(open))
            if (surface.ownerWindow().filter(ownerWindow::equals).isPresent()) surface.close();
    }

    /** Run after a close leaves no surface open; the application uses it to decide whether to exit. */
    public Runnable onAllClosed = () -> { };
    /** Run when a surface opens while none is open; the application brings Jasper back into the Dock for it. */
    public Runnable onFirstOpened = () -> { };
    private boolean emptyReported = true;

    private AuxiliarySurface track(AuxiliarySurface surface) {
        boolean first = open.isEmpty();
        open.add(surface);
        emptyReported = false;
        if (first) onFirstOpened.run();
        surface.onClosed(() -> {
            open.remove(surface);
            // Dialogs die with their owner, as native dialogs do.
            for (AuxiliarySurface other : List.copyOf(open))
                if (other.ownerSurface().filter(owner -> owner == surface).isPresent()) other.close();
            boolean remembered = surface.kind() == AuxiliarySurface.Kind.WINDOW
                || surface.kind() == AuxiliarySurface.Kind.DIALOG && !surface.id().isEmpty();
            if (remembered) surface.lastBounds().ifPresent(bounds -> {
                if (bounds.width <= 0 || bounds.height <= 0) return;
                state.putWindow(surface.id(), new UiState.Bounds(bounds.x, bounds.y, bounds.width, bounds.height));
                state.save();
            });
            // Closing a window closes its dialogs from inside this handler, so report the transition once.
            if (open.isEmpty() && !emptyReported) { emptyReported = true; onAllClosed.run(); }
        });
        return surface;
    }

    public List<AuxiliarySurface> open() { return List.copyOf(open); }

    /**
     * Quit and restart ask every open window's closing guards first, in creation order; the first refusal
     * cancels them. Dialogs and overlays are not asked, and nothing closes.
     *
     * @return whether the quit may go ahead
     */
    public boolean mayQuit() {
        for (AuxiliarySurface surface : List.copyOf(open))
            if (surface.kind() == AuxiliarySurface.Kind.WINDOW && !surface.mayClose()) return false;
        return true;
    }

    /** Shutdown: closes everything, newest first, without consulting guards. */
    @Override public void close() {
        List<AuxiliarySurface> closing = new ArrayList<>(open);
        for (int i = closing.size() - 1; i >= 0; i--) closing.get(i).close();
    }
}
