package dev.jasper.terminal;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

/** EDT frame/blink scheduling; reader notifications retain the token from their own attachment. */
final class RenderScheduler {
    private final Runnable reconcileRows, repaint, cancelSearch;
    private final BooleanSupplier cursorEligible;
    private boolean attached;
    private static final int FRAME_MILLIS = 8;
    private static final int BLINK_MILLIS = 530;
    private final AtomicBoolean dirty = new AtomicBoolean(true);
    private volatile AtomicBoolean pendingFrame = new AtomicBoolean();
    private volatile boolean renderingActive;
    private volatile long attachmentGeneration;
    private final Timer frameTimer;
    private final Timer blinkTimer;
    private boolean blinkOn = true;

    RenderScheduler(Runnable reconcileRows, Runnable repaint, Runnable cancelSearch, BooleanSupplier cursorEligible) {
        this.reconcileRows = reconcileRows; this.repaint = repaint; this.cancelSearch = cancelSearch;
        this.cursorEligible = cursorEligible;
        frameTimer = new Timer(FRAME_MILLIS, event -> frameTimerFinished());
        frameTimer.setRepeats(false);
        blinkTimer = new Timer(BLINK_MILLIS, event -> {
            reconcileBlink();
            if (((Timer) event.getSource()).isRunning()) {
                blinkOn = !blinkOn;
                repaint.run();
            }
        });
    }
    long attach() {
        ++attachmentGeneration;
        pendingFrame = new AtomicBoolean();
        attached = true;
        return attachmentGeneration;
    }
    void detach() {
        ++attachmentGeneration;
        attached = false;
        renderingActive = false;
        pendingFrame = new AtomicBoolean();
        frameTimer.stop();
        blinkTimer.stop();
    }
    boolean blinkOn() { return blinkOn; }
    long generation() { return attachmentGeneration; }
    AtomicBoolean pendingToken() { return pendingFrame; }
    void restartBlink() {
        blinkOn = true;
        reconcileBlink();
        if (blinkTimer.isRunning()) blinkTimer.restart();
        repaint.run();
    }

    /** Reader-thread calls retain one dirty bit and at most one queued EDT delivery per attachment. */
    void markDirty(long generation) {
        // Capture before validation: an old callback must never claim a newer attachment's token.
        AtomicBoolean pending = pendingFrame;
        if (generation != attachmentGeneration) return;
        publishDirty(generation, pending);
    }

    /** An already validated reader request may resume here after its attachment has been replaced. */
    void publishDirty(long generation, AtomicBoolean pending) {
        dirty.set(true);
        if (!renderingActive || !pending.compareAndSet(false, true)) return;
        SwingUtilities.invokeLater(() -> {
            if (generation == attachmentGeneration && pending == pendingFrame && renderingActive) {
                frameTimer.start();
            }
        });
    }

    void frameTimerFinished() {
        frameTimer.stop();
        pendingFrame.set(false);
        if (!renderingActive) return;
        if (dirty.getAndSet(false)) {
            reconcileRows.run();
            reconcileBlink();
            repaint.run();
        }
    }

    /** Hidden tabs keep session/application metadata flowing, but schedule no view frames or cursor ticks. */
    void showing(boolean showing) {
        renderingActive = attached && showing;
        if (renderingActive) {
            markDirty(attachmentGeneration);
        } else {
            cancelSearch.run();
            pendingFrame = new AtomicBoolean();
            frameTimer.stop();
        }
        reconcileBlink();
    }

    void reconcileBlink() {
        boolean eligible = renderingActive && cursorEligible.getAsBoolean();
        if (eligible) blinkTimer.start(); else blinkTimer.stop();
    }
}
