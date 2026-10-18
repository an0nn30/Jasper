/**
 * The instance lock, the bounded handoff protocol and its endpoint worker. The lock lives as long as the process; bootstrap transfers endpoint close to application shutdown and process-hook backup.
 * <p>Allowed outgoing Jasper dependencies: dev.jasper.app.platform.
 * The module architecture check forbids package cycles; app types are not an external plugin API.
 */
package dev.jasper.app.residency;
