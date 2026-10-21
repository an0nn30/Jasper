package dev.jasper.editor.markdown.resources;

import java.util.Optional;

/** Plugin-wide accounting for retained and in-flight decoded pixels. */
public final class ImageBudget {
    private final long maximumBytes;
    private long used;
    /** @param maximumBytes maximum retained plus reserved bytes */
    public ImageBudget(long maximumBytes) {
        if (maximumBytes < 0) throw new IllegalArgumentException("Negative image budget");
        this.maximumBytes = maximumBytes;
    }
    /** Reserves four bytes per pixel before decoding, subject to the per-image pixel bound. */
    public synchronized Optional<Lease> reserve(int width, int height) {
        long pixels = (long) width * height;
        if (width <= 0 || height <= 0 || pixels > 16_000_000 || pixels * 4 > maximumBytes - used) return Optional.empty();
        used += pixels * 4;
        return Optional.of(new Lease(pixels * 4));
    }
    synchronized long usedBytes() { return used; }
    /** One reservation; closing more than once is harmless. */
    public final class Lease implements AutoCloseable {
        private long bytes;
        private Lease(long bytes) { this.bytes = bytes; }
        @Override public void close() { synchronized (ImageBudget.this) { used -= bytes; bytes = 0; } }
    }
}
