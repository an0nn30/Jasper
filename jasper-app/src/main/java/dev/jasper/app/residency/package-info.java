/**
 * Bounded interprocess protocol and endpoint workers. Bootstrap transfers endpoint close to application shutdown and process-hook backup.
 * <p>Allowed outgoing Jasper dependencies: dev.jasper.app.platform.
 * The module architecture check forbids package cycles; app types are not an external plugin API.
 */
package dev.jasper.app.residency;
