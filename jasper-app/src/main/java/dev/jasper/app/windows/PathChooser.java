package dev.jasper.app.windows;

import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;

/** Native picker boundary; tests substitute a scripted, event-pumping implementation. */
@FunctionalInterface
public interface PathChooser {
    /** Registers disposal before showing the chooser. Empty means cancelled. EDT only. */
    List<dev.jasper.app.files.Location> choose(PathChoice choice, Consumer<Runnable> cancellation);
}
