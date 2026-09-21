/**
 * Byte transport for a session whose program is not a local process: reading, an application-owned writer
 * thread behind a bounded queue, draining after exit, and closing exactly once. JDK only; no emulator types.
 * Unsupported for application or plugin use.
 */
package dev.jasper.terminal.internal.transport;
