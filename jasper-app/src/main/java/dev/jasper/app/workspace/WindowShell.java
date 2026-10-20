package dev.jasper.app.workspace;

import java.awt.Component;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Window;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * What a Jasper window's native frame does for it (workspace tabs plan 4a), as functions: {@code WindowFrame} gives them
 * for a frame on screen, and a headless test gives its own. EDT only.
 *
 * @param show shows the frame for the first time
 * @param toFront brings the frame to the front, restoring it when it is minimized
 * @param dispose disposes the frame, after its content has closed
 * @param discard disposes the frame and remembers nothing: a window that failed to open
 * @param window the frame, for parenting dialogs; null in a headless test
 * @param owns whether a component is the frame or sits inside it
 * @param place puts the frame, before it first shows, so that its workspace takes screen bounds, on the display under a
 *     screen point, the cursor's: a torn-out tab's window (workspace tabs plan 5)
 */
public record WindowShell(Runnable show, Runnable toFront, Runnable dispose, Runnable discard, Supplier<Window> window,
                          Predicate<Component> owns, BiConsumer<Rectangle, Point> place) {
    /** Requires every function. */
    public WindowShell {
        Objects.requireNonNull(show, "show"); Objects.requireNonNull(toFront, "toFront"); Objects.requireNonNull(dispose, "dispose");
        Objects.requireNonNull(discard, "discard"); Objects.requireNonNull(window, "window"); Objects.requireNonNull(owns, "owns");
        Objects.requireNonNull(place, "place");
    }

    /**
     * A frame that opens where the platform puts it, as a headless test's stand-in does.
     *
     * @param show shows the frame for the first time
     * @param toFront brings the frame to the front, restoring it when it is minimized
     * @param dispose disposes the frame, after its content has closed
     * @param discard disposes the frame and remembers nothing
     * @param window the frame, for parenting dialogs; null in a headless test
     * @param owns whether a component is the frame or sits inside it
     */
    public WindowShell(Runnable show, Runnable toFront, Runnable dispose, Runnable discard, Supplier<Window> window,
                       Predicate<Component> owns) {
        this(show, toFront, dispose, discard, window, owns, (bounds, cursor) -> { });
    }
}
