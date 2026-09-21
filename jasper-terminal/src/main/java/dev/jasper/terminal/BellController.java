package dev.jasper.terminal;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

/** Coalesces reader bells per attachment; flashes and sounds are delivered on the EDT. */
final class BellController {
    private final Supplier<BellMode> mode;
    private final Runnable repaint;
    private Runnable bellSound;
    private volatile long attachmentGeneration;
    private static final int BELL_MILLIS = 150;
    private boolean visualBell;
    private volatile AtomicBoolean pendingBell;
    private final Timer bellTimer;

    BellController(Supplier<BellMode> mode, Runnable repaint, Runnable sound) {
        this.mode = mode; this.repaint = repaint; this.bellSound = sound;
        bellTimer = new Timer(BELL_MILLIS, event -> clearVisualBell());
        bellTimer.setRepeats(false);
    }
    void attach(long generation) { attachmentGeneration = generation; pendingBell = new AtomicBoolean(); }
    void detach() { pendingBell = null; clearVisualBell(); }
    void modeChanged() {
        pendingBell = pendingBell == null ? null : new AtomicBoolean();
        clearVisualBell();
    }
    boolean visual() { return visualBell; }
    void setSound(Runnable sound) { bellSound = Objects.requireNonNull(sound, "sound"); }
    void signal(long generation) {
        AtomicBoolean pending = pendingBell;
        if (generation != attachmentGeneration || pending == null || !pending.compareAndSet(false, true)) return;
        SwingUtilities.invokeLater(() -> {
            pending.set(false);
            if (generation == attachmentGeneration && pending == pendingBell) ringBell();
        });
    }
    private void ringBell() {
        switch (mode.get()) {
            case VISUAL -> {
                visualBell = true;
                bellTimer.restart();
                repaint.run();
            }
            case SOUND -> bellSound.run();
            case NONE -> { }
        }
    }

    private void clearVisualBell() {
        bellTimer.stop();
        if (visualBell) {
            visualBell = false;
            repaint.run();
        }
    }
}
