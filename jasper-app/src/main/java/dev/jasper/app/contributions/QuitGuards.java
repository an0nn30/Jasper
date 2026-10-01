package dev.jasper.app.contributions;

import dev.jasper.app.lifecycle.Subscription;
import java.awt.Toolkit;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import javax.swing.SwingUtilities;

/**
 * Plugins' quit and restart guards, asked in registration order after plugin windows and content tabs, before
 * anything closes. EDT-only. Callers contain their guards: a supplier here never throws and its future never fails.
 */
public final class QuitGuards {
    private final List<Supplier<CompletableFuture<Boolean>>> guards = new ArrayList<>();
    private boolean asking;

    public Subscription add(Supplier<CompletableFuture<Boolean>> guard) {
        requireEdt();
        guards.add(Objects.requireNonNull(guard, "guard"));
        return new Subscription(() -> guards.remove(guard));
    }

    /**
     * Asks each guard in turn, pumping UI events while a decision is pending; the first refusal stops. An ask made
     * while one is already running (a guard's dialog quitting again) refuses.
     *
     * @return whether the quit may go ahead
     */
    public boolean mayQuit() {
        requireEdt();
        if (asking) return false;
        asking = true;
        try {
            for (var guard : List.copyOf(guards)) if (!await(guard.get())) return false;
            return true;
        } finally {
            asking = false;
        }
    }

    private static boolean await(CompletableFuture<Boolean> future) {
        if (!future.isDone()) {
            var loop = Toolkit.getDefaultToolkit().getSystemEventQueue().createSecondaryLoop();
            future.whenComplete((value, error) -> SwingUtilities.invokeLater(loop::exit));
            loop.enter();
        }
        return future.getNow(false);
    }

    private static void requireEdt() {
        if (!SwingUtilities.isEventDispatchThread()) throw new IllegalStateException("Quit guards belong to the EDT");
    }
}
