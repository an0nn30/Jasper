package dev.jasper.app.palette;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.UUID;

/**
 * The origin as a scope sees it: a terminal pane, or a content item that may take inserted text. Scopes get no window,
 * pane or emulator types; the ids let the plugin runtime hand a contributed scope handles of its own.
 *
 * @param live       whether the origin still takes input: its pane runs, or the content item takes inserted text now
 * @param insertText for a content item, puts text into it as its plugin's insertion takes it, with whether Return
 *                   follows (SDK 0.29.0's {@code ContentTab.setTextInsertion}); empty for a pane or a bare window
 */
public record PaletteTarget(Consumer<String> paste, Runnable sendReturn, Supplier<Optional<Path>> workingDirectory,
                     BooleanSupplier live, Optional<UUID> windowId, Optional<UUID> paneId,
                     Optional<BiConsumer<String, Boolean>> insertText) {
    public PaletteTarget {
        Objects.requireNonNull(paste); Objects.requireNonNull(sendReturn); Objects.requireNonNull(workingDirectory);
        Objects.requireNonNull(live); Objects.requireNonNull(windowId); Objects.requireNonNull(paneId);
        Objects.requireNonNull(insertText);
    }

    /** No pane and no window: a scope answers with nothing it needs a window for. */
    public static PaletteTarget none() {
        return new PaletteTarget(text -> {}, () -> {}, Optional::empty, () -> false, Optional.empty(), Optional.empty(), Optional.empty());
    }

    /** A window with no origin pane: nothing to paste into, but scopes know where they are. */
    public static PaletteTarget window(UUID windowId) {
        return new PaletteTarget(text -> {}, () -> {}, Optional::empty, () -> false, Optional.of(windowId), Optional.empty(), Optional.empty());
    }

    /**
     * A window whose origin is a content item: {@code insertion} gives what takes the item's inserted text now, empty
     * while it takes none or has closed, and is read again at each insertion and by {@link #live}.
     */
    public static PaletteTarget content(UUID windowId, Supplier<Optional<BiConsumer<String, Boolean>>> insertion) {
        Objects.requireNonNull(insertion, "insertion");
        BiConsumer<String, Boolean> insert = (text, submit) -> insertion.get().ifPresent(taking -> taking.accept(text, submit));
        return new PaletteTarget(text -> {}, () -> {}, Optional::empty, () -> insertion.get().isPresent(), Optional.of(windowId),
            Optional.empty(), Optional.of(insert));
    }
}
