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
    private final UiState state;
    private final Function<AuxiliarySurface, AuxiliarySurface.Shell> shells;
    private final List<AuxiliarySurface> open = new ArrayList<>();

    public AuxiliaryWindows(UiState state, Function<AuxiliarySurface, AuxiliarySurface.Shell> shells) {
        this.state = Objects.requireNonNull(state);
        this.shells = Objects.requireNonNull(shells);
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

    private AuxiliarySurface track(AuxiliarySurface surface) {
        open.add(surface);
        surface.onClosed(() -> {
            open.remove(surface);
            // Dialogs die with their owner, as native dialogs do.
            for (AuxiliarySurface other : List.copyOf(open))
                if (other.ownerSurface().filter(owner -> owner == surface).isPresent()) other.close();
            if (surface.kind() != AuxiliarySurface.Kind.WINDOW) return;
            surface.lastBounds().ifPresent(bounds -> {
                if (bounds.width <= 0 || bounds.height <= 0) return;
                state.putWindow(surface.id(), new UiState.Bounds(bounds.x, bounds.y, bounds.width, bounds.height));
                state.save();
            });
        });
        return surface;
    }

    public List<AuxiliarySurface> open() { return List.copyOf(open); }

    /** Shutdown: closes everything, newest first, without consulting guards. */
    @Override public void close() {
        List<AuxiliarySurface> closing = new ArrayList<>(open);
        for (int i = closing.size() - 1; i >= 0; i--) closing.get(i).close();
    }
}
