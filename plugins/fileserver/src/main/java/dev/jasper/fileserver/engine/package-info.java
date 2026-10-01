/**
 * What every protocol engine shares: the {@link dev.jasper.fileserver.engine.ServerEngine} contract, events for the
 * activity log, the login throttle and the addresses a server binds and advertises. Engines live in sub-packages and
 * are the only code that names a protocol library. May depend on {@code definition}, {@code share}, {@code auth} and
 * the JDK.
 */
package dev.jasper.fileserver.engine;
