package dev.jasper.remote.sftp;

import java.io.IOException;
import java.io.OutputStream;

/** A write stream with an explicit durability/acknowledgement barrier. */
public abstract class WriteHandle extends OutputStream {
    /** Absolute contiguous offset forced locally or acknowledged by every successful SFTP WRITE STATUS. */
    public abstract long checkpoint() throws IOException;
    /** Independently interrupts this writer, without waiting for its pending request. */
    public abstract void abort();
}
