/**
 * EDT scope/query/step state, keyboard routing, Swing card and the built-in Commands scope. Controller owns scope listeners and invalidates asynchronous completions on close.
 * <p>Allowed outgoing Jasper dependencies: dev.jasper.app.commands, dev.jasper.app.config, dev.jasper.app.lifecycle, dev.jasper.app.platform,
 * dev.jasper.app.presentation (PaletteLook, read only by PaletteViews).
 * The card's controls are jasper-ui kit descriptors (dev.jasper.ui.model, dev.jasper.ui.swing) mounted in a layout-only
 * shell on the chrome KitContext its host passes in.
 * The module architecture check forbids package cycles; app types are not an external plugin API.
 */
package dev.jasper.app.palette;
