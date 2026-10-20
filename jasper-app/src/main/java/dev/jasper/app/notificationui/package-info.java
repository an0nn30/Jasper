/**
 * EDT Swing views of the notification center: each window's card stack, the history panel, the Errors window's
 * view, and their text, style and focus helpers. Views subscribe to the center or the incidents and close their
 * subscriptions when closed; the application owns the center, mounts each window's card layer and closes it with the
 * window, and the Errors window closes its view.
 * Controls are jasper-ui kit descriptors (dev.jasper.ui.model, dev.jasper.ui.swing) on the chrome KitContext, mounted in
 * layout-only shells; cards and history rows are pointer-only so the terminal keeps the keyboard.
 * <p>Allowed outgoing Jasper dependencies: dev.jasper.app.contributions, dev.jasper.app.lifecycle, dev.jasper.app.notifications, dev.jasper.app.platform.
 * The module architecture check forbids package cycles; app types are not an external plugin API.
 */
package dev.jasper.app.notificationui;
