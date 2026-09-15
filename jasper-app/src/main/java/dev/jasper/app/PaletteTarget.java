package dev.jasper.app;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** The origin pane as a scope sees it. Scopes get no window, pane or emulator types. */
record PaletteTarget(Consumer<String> paste, Runnable sendReturn, Supplier<Optional<Path>> workingDirectory,
                     Supplier<String> shellName, BooleanSupplier live) {
    PaletteTarget {
        Objects.requireNonNull(paste); Objects.requireNonNull(sendReturn); Objects.requireNonNull(workingDirectory);
        Objects.requireNonNull(shellName); Objects.requireNonNull(live);
    }

    static PaletteTarget none() {
        return new PaletteTarget(text -> {}, () -> {}, Optional::empty, () -> "", () -> false);
    }

    /** Paste goes through the view (bracketed paste, newline normalization); the return is a raw carriage return. */
    static PaletteTarget of(TerminalPane pane) {
        return new PaletteTarget(
            text -> { if (pane.view() != null) pane.view().paste(text); },
            () -> { if (pane.session() != null) pane.session().write("\r"); },
            () -> pane.session() == null ? Optional.empty() : pane.session().workingDirectory(),
            pane::shellLabel,
            pane::running);
    }
}
