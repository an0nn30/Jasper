package dev.jasper.sdk.terminal;

import java.util.UUID;

/** One terminal pane. Implemented by the application; holds no Swing object. */
public interface PaneHandle {
    /**
     * The pane's identity for as long as it is open.
     *
     * @return the id
     */
    UUID id();
}
