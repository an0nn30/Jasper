package dev.jasper.editor.api;

import dev.jasper.sdk.terminal.PaneHandle;
import dev.jasper.sdk.terminal.WindowHandle;
import java.util.Optional;

/**
 * What Open in Editor… was invoked on. Since Editor 0.5.0.
 *
 * @param window the workspace window
 * @param pane the focused terminal pane; empty while another kind of tab is selected
 * @param documentKey the key of the Editor tab that is active, when one is and no terminal pane is focused
 */
public record OpenRequest(WindowHandle window, Optional<PaneHandle> pane, Optional<String> documentKey) { }
