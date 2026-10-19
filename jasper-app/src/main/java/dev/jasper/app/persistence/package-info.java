/**
 * Bounded TOML reads and atomic writes; synchronous helpers own short-lived file handles. Caller chooses thread and lifecycle.
 * <p>Allowed outgoing Jasper dependencies: dev.jasper.app.dock (slots and the dock's size limits), dev.jasper.app.layout
 * (the workspace layouts workspace.toml saves).
 * The module architecture check forbids package cycles; app types are not an external plugin API.
 */
package dev.jasper.app.persistence;
