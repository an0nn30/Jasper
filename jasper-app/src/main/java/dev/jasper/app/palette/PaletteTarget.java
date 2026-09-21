package dev.jasper.app.palette;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** The origin pane as a scope sees it. Scopes get no window, pane or emulator types. */
public record PaletteTarget(Consumer<String> paste, Runnable sendReturn, Supplier<Optional<Path>> workingDirectory,
                     Supplier<String> shellName, BooleanSupplier live) {
    public PaletteTarget {
        Objects.requireNonNull(paste); Objects.requireNonNull(sendReturn); Objects.requireNonNull(workingDirectory);
        Objects.requireNonNull(shellName); Objects.requireNonNull(live);
    }

    public static PaletteTarget none() {
        return new PaletteTarget(text -> {}, () -> {}, Optional::empty, () -> "", () -> false);
    }

}
