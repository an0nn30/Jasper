package dev.jasper.fileserver.share;

import java.io.IOException;

/** A client path that would leave the share or names something no platform can share safely. */
public final class PathRefusedException extends IOException {
    public PathRefusedException(String message) { super(message); }
}
