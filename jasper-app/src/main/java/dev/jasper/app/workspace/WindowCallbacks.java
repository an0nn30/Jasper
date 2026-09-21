package dev.jasper.app.workspace;

import java.nio.file.Path;
import java.util.Objects;
import java.util.function.Consumer;

/** Application-supplied requests and native window events; confined to the EDT. */
public record WindowCallbacks(Consumer<Path> newWindow, Runnable quit,
                       Consumer<TerminalWindow> activated, Consumer<TerminalWindow> closed,
                       Consumer<State> stateChanged) {
    public WindowCallbacks {
        Objects.requireNonNull(newWindow); Objects.requireNonNull(quit);
        Objects.requireNonNull(activated); Objects.requireNonNull(closed); Objects.requireNonNull(stateChanged);
    }
    /** A window's visibility facts; residency and companion policy remain with the application. */
    public record State(TerminalWindow window, boolean showing, boolean iconified) {}
}
