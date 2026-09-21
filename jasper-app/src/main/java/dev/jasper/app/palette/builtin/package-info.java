/**
 * EDT adapters for commands, shell history and snippets. Providers own I/O; scope subscriptions are disposed by the palette.
 * <p>Allowed outgoing Jasper dependencies: dev.jasper.app.commands, dev.jasper.app.history, dev.jasper.app.lifecycle, dev.jasper.app.palette, dev.jasper.app.platform, dev.jasper.app.snippets.
 * The module architecture check forbids package cycles; app types are not an external plugin API.
 */
package dev.jasper.app.palette.builtin;
