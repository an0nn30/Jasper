package dev.jasper.editor.api;

import dev.jasper.sdk.terminal.PaneHandle;
import dev.jasper.sdk.terminal.WindowHandle;
import java.util.Optional;

/**
 * What Open in Editor… was invoked on.
 *
 * @param window the Jasper window
 * @param pane the focused terminal pane; empty while another kind of tab is selected. The handle is the Editor's and
 *     is gated by the Editor's capabilities: use its {@code id()} to find the pane through your own terminals
 * @param documentKey the key of the Editor tab that is active, when one is and no terminal pane is focused
 */
public record OpenRequest(WindowHandle window, Optional<PaneHandle> pane, Optional<String> documentKey) { }
