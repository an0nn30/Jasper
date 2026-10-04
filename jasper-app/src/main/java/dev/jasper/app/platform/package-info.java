/**
 * OS adapters, icons, fonts, title-bar paint, logging and system notifications. Callers own registrations and native handles; Swing operations run on EDT, and logging and system notifications each have their own worker.
 * <p>Allowed outgoing Jasper dependencies: no other Jasper package.
 * The module architecture check forbids package cycles; app types are not an external plugin API.
 */
package dev.jasper.app.platform;
