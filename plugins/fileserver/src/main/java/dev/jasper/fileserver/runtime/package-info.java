/**
 * Running servers: {@link dev.jasper.fileserver.runtime.ServerRegistry} owns each server's state machine on the UI
 * thread, starts and stops engines on a worker, and keeps each server's in-memory activity log. May depend on
 * {@code engine} and its sub-packages, {@code auth}, {@code share}, {@code definition} and the SDK.
 */
package dev.jasper.fileserver.runtime;
