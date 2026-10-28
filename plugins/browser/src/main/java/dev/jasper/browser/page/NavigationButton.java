package dev.jasper.browser.page;

import java.awt.Toolkit;
import java.awt.event.MouseEvent;
import java.util.Optional;

/**
 * The mouse's back and forward buttons, which on Windows and Linux go back and forward in a page's history (browser
 * spec section 7). AWT numbers them 4 and 5 on Windows and under the Wayland toolkit; under X11 they are X's buttons 8
 * and 9, which AWT reports as 6 and 7, while its 4 and 5 are X's sideways scrolling. On macOS no button is one.
 */
public enum NavigationButton {
    /** The back button. */
    BACK,
    /** The forward button. */
    FORWARD;

    private static final String X11 = "sun.awt.X11.XToolkit";

    /**
     * The navigation button AWT's {@code button} is on this platform.
     *
     * @param button a mouse event's {@link MouseEvent#getButton()}
     * @return the navigation button, or empty
     */
    static Optional<NavigationButton> of(int button) {
        if (button <= MouseEvent.BUTTON3) return Optional.empty();
        return of(button, System.getProperty("os.name", ""), Toolkit.getDefaultToolkit().getClass().getName());
    }

    /**
     * The navigation button AWT's {@code button} is on a platform.
     *
     * @param button a mouse event's {@link MouseEvent#getButton()}
     * @param osName the {@code os.name} system property
     * @param toolkit the class name of AWT's default toolkit
     * @return the navigation button, or empty
     */
    static Optional<NavigationButton> of(int button, String osName, String toolkit) {
        if (osName.startsWith("Mac")) return Optional.empty();
        int back = !osName.startsWith("Windows") && toolkit.equals(X11) ? 6 : 4;
        if (button == back) return Optional.of(BACK);
        if (button == back + 1) return Optional.of(FORWARD);
        return Optional.empty();
    }
}
