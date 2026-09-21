/**
 * Unsupported detached screen snapshots, drawing runs and painter. Painting never reads a live emulator buffer.
 * <p>Depends on text values, config and FontSet. TerminalPainter and RunBuilder run on EDT and retain bounded/reusable scratch state.
 */
package dev.jasper.terminal.internal.rendering;
