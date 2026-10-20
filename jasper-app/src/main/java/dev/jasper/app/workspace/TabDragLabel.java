package dev.jasper.app.workspace;

import com.formdev.flatlaf.util.UIScale;
import dev.jasper.app.platform.ThemeZone;
import dev.jasper.ui.metrics.Typography;
import dev.jasper.ui.theme.SemanticColor;
import java.awt.BorderLayout;
import java.awt.GraphicsEnvironment;
import java.awt.Point;
import java.awt.Window;
import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JLabel;
import javax.swing.JWindow;

/**
 * The small undecorated window that shows a dragged tab's label beside the cursor (workspace tabs spec 12): the chrome's
 * card colours inside a one-pixel window border, never focused, above every window, moved to each drag event and never
 * animated. A headless JVM has no windows: there the label keeps its text and place alone, which tests read. EDT only.
 */
final class TabDragLabel {
    private final JLabel label = new JLabel();
    private JWindow window;
    private Point location;

    TabDragLabel() {
        // A tab's title comes from the terminal (OSC 0/2): never HTML.
        label.putClientProperty("html.disable", true);
        label.setOpaque(true);
    }

    /** Shows {@code text} and {@code icon}, the label's top-left corner at {@code screen}. */
    void show(String text, Icon icon, Point screen) {
        label.setText(text);
        label.setIcon(icon);
        label.setFont(ThemeZone.font(Typography.Role.BODY, text));
        label.setForeground(ThemeZone.color(label, SemanticColor.TEXT_PRIMARY));
        label.setBackground(ThemeZone.color(label, SemanticColor.SURFACE_CARD));
        int vertical = UIScale.scale(4), horizontal = UIScale.scale(8);
        label.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(ThemeZone.color(label, SemanticColor.BORDER_WINDOW)),
            BorderFactory.createEmptyBorder(vertical, horizontal, vertical, horizontal)));
        if (!GraphicsEnvironment.isHeadless()) {
            if (window == null) {
                window = new JWindow();
                window.setType(Window.Type.POPUP);
                window.setFocusableWindowState(false);
                window.setAlwaysOnTop(true);
                window.getContentPane().add(label, BorderLayout.CENTER);
            }
            window.pack();
        }
        move(screen);
        if (window != null) window.setVisible(true);
    }

    /** Puts the label's top-left corner at {@code screen}. */
    void move(Point screen) {
        location = new Point(screen);
        if (window != null) window.setLocation(location);
    }

    /** Hides the label and releases its window. */
    void hide() {
        location = null;
        if (window != null) {
            window.dispose();
            window = null;
        }
    }

    /** @return whether the label shows */
    boolean showing() { return location != null; }

    /** @return the label's text */
    String text() { return label.getText(); }

    /** @return where the label's top-left corner is on screen while it shows, else null */
    Point location() { return location == null ? null : new Point(location); }
}
