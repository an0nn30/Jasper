/**
 * Startup splash painting: the standard Protean composition and queue, or explicit look branding. The bootstrap
 * owns the AWT window and closes it through startup steps. A daemon samples monotonic transition time while the EDT
 * starts the application; a private lock serializes state, buffer painting and closure.
 * <p>Allowed outgoing Jasper dependencies: dev.jasper.app.startup, dev.jasper.ui.icons.
 * The module architecture check forbids package cycles; app types are not an external plugin API.
 */
package dev.jasper.app.splash;
