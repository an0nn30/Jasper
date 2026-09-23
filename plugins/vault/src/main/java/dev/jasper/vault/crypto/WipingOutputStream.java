package dev.jasper.vault.crypto;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.Objects;

/** Wipes retired buffers during growth and the last scratch buffer on close. */
public final class WipingOutputStream extends ByteArrayOutputStream {
    @Override public synchronized void write(int b) { reserve(1); buf[count++] = (byte) b; }
    @Override public synchronized void write(byte[] b, int off, int len) {
        Objects.checkFromIndexSize(off, len, b.length); reserve(len);
        System.arraycopy(b, off, buf, count, len); count += len;
    }
    private void reserve(int extra) {
        int needed = Math.addExact(count, extra);
        if (needed <= buf.length) return;
        byte[] old = buf; buf = Arrays.copyOf(old, Math.max(needed, Math.multiplyExact(old.length, 2)));
        Arrays.fill(old, (byte) 0);
    }
    @Override public synchronized void close() { Arrays.fill(buf, (byte) 0); reset(); }
}
