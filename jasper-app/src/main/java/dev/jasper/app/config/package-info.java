/**
 * Immutable values and pure parsing; ConfigService owns background watch/reload work and marshals delivery to EDT. Application closes the service.
 * <p>Allowed outgoing Jasper dependencies: dev.jasper.app.commands, dev.jasper.terminal.config.
 * The module architecture check forbids package cycles; app types are not an external plugin API.
 */
package dev.jasper.app.config;
