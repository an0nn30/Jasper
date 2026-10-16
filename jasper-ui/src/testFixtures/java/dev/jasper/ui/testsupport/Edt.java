package dev.jasper.ui.testsupport;

import java.lang.reflect.InvocationTargetException;
import javax.swing.SwingUtilities;

/** Synchronous headless EDT bridge preserving the original assertion or runtime failure. */
public final class Edt {
    private Edt() {}
    public static void run(Runnable action) {
        if (SwingUtilities.isEventDispatchThread()) { action.run(); return; }
        try { SwingUtilities.invokeAndWait(action); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new AssertionError(e); }
        catch (InvocationTargetException e) {
            if (e.getCause() instanceof RuntimeException failure) throw failure;
            if (e.getCause() instanceof Error failure) throw failure;
            throw new AssertionError(e.getCause());
        }
    }
}
