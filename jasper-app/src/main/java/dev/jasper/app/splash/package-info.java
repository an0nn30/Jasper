/**
 * The boot splash of a branded look (spec 10.11): {@link dev.jasper.app.splash.SplashPainter} draws it from the look's
 * files as the Boot Splash frames give it, the same in every theme, and {@link dev.jasper.app.splash.Splash} is its
 * undecorated, unfocusable window, painted at once at each step because the event thread is busy starting Jasper. The
 * bootstrap owns the splash and closes it through the start-up steps.
 * <p>Allowed outgoing Jasper dependencies: dev.jasper.app.startup, dev.jasper.ui.icons.
 * The module architecture check forbids package cycles; app types are not an external plugin API.
 */
package dev.jasper.app.splash;
