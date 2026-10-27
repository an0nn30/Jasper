/**
 * The theme-selected and branded-look splashes (spec 10.11): {@link dev.jasper.app.splash.SplashPainter} draws it from the look's
 * files or a bundled still illustration; the original Meadow landscape and pixel sprites provide the asset-error fallback, and {@link dev.jasper.app.splash.Splash} is its
 * undecorated, unfocusable window, painted at once at each step because the event thread is busy starting Jasper. The
 * bootstrap owns the splash and closes it through the start-up steps. A daemon animates fallback Meadow while startup occupies
 * the EDT; a private lock serializes buffer painting and closure, and the user's animation preference is honored.
 * <p>Allowed outgoing Jasper dependencies: dev.jasper.app.startup, dev.jasper.ui.icons.
 * The module architecture check forbids package cycles; app types are not an external plugin API.
 */
package dev.jasper.app.splash;
