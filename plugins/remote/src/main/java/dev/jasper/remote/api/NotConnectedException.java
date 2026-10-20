package dev.jasper.remote.api;

import java.io.IOException;

/** Background work found no authenticated connection to the host and may not open one. */
public final class NotConnectedException extends IOException {
    private static final long serialVersionUID = 1L;

    /**
     * Creates the exception with the message "<i>hostName</i> is not connected".
     *
     * @param hostName the saved host's name
     */
    public NotConnectedException(String hostName) { super(hostName + " is not connected"); }
}
