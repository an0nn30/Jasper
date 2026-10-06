/**
 * The Tunnels UI: the rail panel, the editor, Forward a Port, the palette scope and {@link dev.jasper.remote.ui.tunnels.TunnelsUi},
 * which registers them and keeps the manager in step with tunnels.toml. The panel and dialogs are SDK kit views (descriptors
 * the host renders); UI thread only; it never touches MINA.
 */
package dev.jasper.remote.ui.tunnels;
