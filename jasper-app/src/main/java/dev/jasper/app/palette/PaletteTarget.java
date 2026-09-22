package dev.jasper.app.palette;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.UUID;

/**
 * The origin pane as a scope sees it. Scopes get no window, pane or emulator types; the ids let the
 * plugin runtime hand a contributed scope handles of its own.
 */
public record PaletteTarget(Consumer<String> paste, Runnable sendReturn, Supplier<Optional<Path>> workingDirectory,
                     BooleanSupplier live, Optional<UUID> windowId, Optional<UUID> paneId) {
    public PaletteTarget {
        Objects.requireNonNull(paste); Objects.requireNonNull(sendReturn); Objects.requireNonNull(workingDirectory);
        Objects.requireNonNull(live); Objects.requireNonNull(windowId); Objects.requireNonNull(paneId);
    }

    /** No pane and no window: a scope answers with nothing it needs a window for. */
    public static PaletteTarget none() {
        return new PaletteTarget(text -> {}, () -> {}, Optional::empty, () -> false, Optional.empty(), Optional.empty());
    }

    /** A window with no origin pane: nothing to paste into, but scopes know where they are. */
    public static PaletteTarget window(UUID windowId) {
        return new PaletteTarget(text -> {}, () -> {}, Optional::empty, () -> false, Optional.of(windowId), Optional.empty());
    }
}
