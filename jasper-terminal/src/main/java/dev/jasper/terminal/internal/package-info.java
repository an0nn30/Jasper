/**
 * Unsupported concrete bridge between the supported session and view. Public visibility enables package collaboration; it does not make these classes an application or plugin API.
 * <p>TerminalAccess depends on emulation, shell, process and shared values. Live-buffer query methods obtain the buffer lock; published metadata reads use volatile/atomic state or copied collections. Callers honor their facade/view thread contracts.
 */
package dev.jasper.terminal.internal;
