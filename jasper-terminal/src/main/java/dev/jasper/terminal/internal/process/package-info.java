/**
 * Unsupported native process ownership, byte I/O, shutdown and foreground-job inspection. This package does not depend on the emulator or Swing.
 * <p>PtyChild owns native resources; ForegroundJobResolver owns OS inspection. Creation and potentially blocking metadata queries run off EDT; close guards are atomic.
 */
package dev.jasper.terminal.internal.process;
