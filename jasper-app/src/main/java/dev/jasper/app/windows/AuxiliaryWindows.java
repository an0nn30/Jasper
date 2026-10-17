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
    /** The look's mapping: classic overlays and sheets, and dialogs a native title bar titles. */
    private final boolean classic, windowTitled;
    private final dev.jasper.app.presentation.DialogMapping dialogs;

    /** The look's dialog mapping these windows were made with (spec 4.9; {@code PresentationReachTest}). */
    public dev.jasper.app.presentation.DialogMapping dialogs() { return dialogs; }

    public AuxiliaryWindows(UiState state, Function<AuxiliarySurface, AuxiliarySurface.Shell> shells) {
        this(state, shells, (choice, cancellation) -> List.of());
    }

    public AuxiliaryWindows(UiState state, Function<AuxiliarySurface, AuxiliarySurface.Shell> shells, PathChooser chooser) {
        this(state, shells, chooser, new dev.jasper.app.presentation.DialogMapping(dev.jasper.app.presentation.DialogMapping.Kind.AS_REQUESTED, false));
    }

    /**
     * As above, under the look's dialog mapping (Enterprise presentation spec 10.8): the one reader of
     * {@code DialogMapping}, it marks every surface it makes, so plugin and app dialogs share the classic path.
     */
    public AuxiliaryWindows(UiState state, Function<AuxiliarySurface, AuxiliarySurface.Shell> shells, PathChooser chooser,
                            dev.jasper.app.presentation.DialogMapping dialogs) {
        this.dialogs = Objects.requireNonNull(dialogs);
        this.classic = dialogs.kind() == dev.jasper.app.presentation.DialogMapping.Kind.CLASSIC;
        this.windowTitled = dialogs.windowTitled();
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

    /**
     * File › Open File…'s chooser: one existing local file, chosen over terminal window {@code window} and starting in
     * {@code folder} when its pane has one; empty when cancelled. EDT.
     */
    public java.util.Optional<java.nio.file.Path> openFile(UUID window, java.util.Optional<java.nio.file.Path> folder) {
        var choice = new PathChoice(window, null, "Open File", java.util.Optional.empty(), PathChoice.Mode.OPEN, false,
            folder.map(dev.jasper.app.files.Location::local), java.util.Optional.empty(), List.of());
        return choose(choice, cancel -> { }).stream().filter(dev.jasper.app.files.Location::isLocal)
            .map(dev.jasper.app.files.Location::localPath).findFirst();
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

    /** A checked control attachment resolves to its actual containing native surface, including a modal dialog. */
    public AuxiliarySurface dialog(String title, boolean modal, javax.swing.JComponent anchor) {
        for(var surface:open) if(surface.shown() && javax.swing.SwingUtilities.isDescendingFrom(anchor,surface.holder()))
            return dialog(title,modal,surface);
        if(!anchor.isShowing())throw new IllegalArgumentException("Anchor has no shown owner");
        var result=new AuxiliarySurface("",title,AuxiliarySurface.Kind.DIALOG,modal,new Dimension(1,1),null,null,shells);
        result.anchorOwner(anchor);return track(result);
    }

    /**
     * A modal dialog that remembers its bounds under {@code id}: over the terminal window {@code ownerWindowOrNull}, or
     * application-modal with no owner when it is null. Showing it blocks until it closes.
     */
    public AuxiliarySurface modalDialog(String id, String title, Dimension preferred, UUID ownerWindowOrNull) {
        if (Objects.requireNonNull(id, "id").isBlank()) throw new IllegalArgumentException("A remembered dialog needs an id");
        return track(new AuxiliarySurface(id, title, AuxiliarySurface.Kind.DIALOG, true, preferred, ownerWindowOrNull, null, shells));
    }

    /** Reserves one progress overlay per terminal owner, including surfaces not shown yet; modal overlays may stack over it. */
    public AuxiliarySurface overlay(String title, UUID ownerWindow) {
        Objects.requireNonNull(ownerWindow);
        if (open.stream().anyMatch(surface -> surface.kind() == AuxiliarySurface.Kind.OVERLAY && !surface.modal()
                && surface.ownerWindow().filter(ownerWindow::equals).isPresent()))
            throw new IllegalStateException("This window already has an overlay");
        return track(new AuxiliarySurface("", title, AuxiliarySurface.Kind.OVERLAY, false,
            new Dimension(1, 1), ownerWindow, null, shells));
    }

    /**
     * A modal overlay over the terminal window {@code ownerWindow}: a dialog presented inside the window, over a
     * dimmed scrim. It may stack over the window's progress overlay. Showing it blocks until it closes.
     */
    public AuxiliarySurface modalOverlay(String title, UUID ownerWindow) {
        return track(new AuxiliarySurface("", title, AuxiliarySurface.Kind.OVERLAY, true, new Dimension(1, 1),
            Objects.requireNonNull(ownerWindow), null, shells));
    }

    /** A modal overlay inside the open auxiliary window or dialog {@code owner}; it closes with it. */
    public AuxiliarySurface modalOverlay(String title, AuxiliarySurface owner) {
        if (owner.closed() || !open.contains(owner)) throw new IllegalArgumentException("An overlay needs an open owner window");
        return track(new AuxiliarySurface("", title, AuxiliarySurface.Kind.OVERLAY, true, new Dimension(1, 1), null, owner, shells));
    }

    /** A modal overlay inside the window that holds the checked control {@code anchor}. */
    public AuxiliarySurface modalOverlay(String title, javax.swing.JComponent anchor) {
        for (var surface : open) if (surface.shown() && javax.swing.SwingUtilities.isDescendingFrom(anchor, surface.holder()))
            return modalOverlay(title, surface);
        if (!anchor.isShowing()) throw new IllegalArgumentException("Anchor has no shown owner");
        var result = new AuxiliarySurface("", title, AuxiliarySurface.Kind.OVERLAY, true, new Dimension(1, 1), null, null, shells);
        result.anchorOwner(anchor); return track(result);
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
        surface.classic(classic);
        if (surface.kind() == AuxiliarySurface.Kind.DIALOG) surface.hostTitled(windowTitled);
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
