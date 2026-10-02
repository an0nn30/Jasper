/**
 * EDT Swing views of the notification center: each window's card stack, the history panel, and their text, style
 * and focus helpers. Views subscribe to the center and close their subscriptions when closed; the application owns
 * the center, mounts each window's card layer and closes it with the window.
 * <p>Allowed outgoing Jasper dependencies: dev.jasper.app.contributions, dev.jasper.app.lifecycle, dev.jasper.app.notifications, dev.jasper.app.platform.
 * The module architecture check forbids package cycles; app types are not an external plugin API.
 */
package dev.jasper.app.notificationui;
