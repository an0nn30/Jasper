package dev.jasper.sdk.terminal;

import java.util.UUID;

/** One application window. Implemented by the application; holds no Swing object. */
public interface WindowHandle {
    /**
     * The window's identity for as long as it is open.
     *
     * @return the id
     */
    UUID id();
}
