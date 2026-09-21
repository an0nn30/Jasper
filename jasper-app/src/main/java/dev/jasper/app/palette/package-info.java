/**
 * EDT scope/query/step state, keyboard routing and Swing card. Controller owns scope listeners and invalidates asynchronous completions on close.
 * <p>Allowed outgoing Jasper dependencies: dev.jasper.app.commands, dev.jasper.app.config, dev.jasper.app.lifecycle.
 * The module architecture check forbids package cycles; app types are not an external plugin API.
 */
package dev.jasper.app.palette;
