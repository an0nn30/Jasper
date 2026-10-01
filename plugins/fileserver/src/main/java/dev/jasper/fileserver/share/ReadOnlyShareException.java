package dev.jasper.fileserver.share;

import java.io.IOException;

/** A write to a read-only share. */
public final class ReadOnlyShareException extends IOException {
    public ReadOnlyShareException() { super("This share is read-only"); }
}
