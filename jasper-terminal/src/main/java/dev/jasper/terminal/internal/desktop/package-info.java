/**
 * Unsupported clipboard and bounded browser dispatch ownership. Link opening leaves the EDT through a shared bounded worker.
 * <p>Depends only on JDK/AWT services. Clipboard operations belong to EDT; browser opening runs on the shared worker. DesktopServices keeps worker lifetime independent of panes.
 */
package dev.jasper.terminal.internal.desktop;
