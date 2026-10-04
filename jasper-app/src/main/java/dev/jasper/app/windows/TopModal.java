package dev.jasper.app.windows;

import java.awt.Dialog;
import java.awt.Window;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

/**
 * The window a picker opened over an owner belongs to: the newest showing modal dialog at the deepest level of the
 * owner's dialogs, or the owner when none shows. A picker owned by the window a modal dialog blocks would itself be
 * blocked, and would sit below the dialog, which floats ({@link FloatWhileActive}); owned by the dialog, it is
 * neither. EDT only.
 */
final class TopModal {
    private TopModal() { }

    static Window over(Window owner) {
        return over(owner, window -> Arrays.stream(window.getOwnedWindows())
            .filter(child -> child instanceof Dialog dialog && dialog.isModal() && dialog.isShowing()).toList());
    }

    /** {@code showingModals} lists a window's showing modal dialogs, oldest first, as {@link Window#getOwnedWindows()} does. */
    static <W> W over(W owner, Function<W, List<W>> showingModals) {
        W current = owner;
        for (List<W> dialogs = showingModals.apply(current); !dialogs.isEmpty(); dialogs = showingModals.apply(current))
            current = dialogs.getLast();
        return current;
    }
}
