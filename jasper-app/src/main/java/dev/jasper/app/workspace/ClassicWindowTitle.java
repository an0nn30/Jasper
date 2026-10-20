package dev.jasper.app.workspace;

import dev.jasper.app.platform.ThemeZone;
import dev.jasper.ui.metrics.Typography;
import dev.jasper.ui.swing.FlatLafInstaller;
import dev.jasper.ui.theme.SurfaceContrast;
import javax.swing.JLabel;
import javax.swing.SwingConstants;

/**
 * The Windows classic title bar's title (Enterprise plan 14 Rulings 13 and 14), IntelliJ Classic's {@code MenuFrameHeader}
 * title: the frame's title, left-aligned {@link #GAP} after the menus in the embedded menu bar, dimmed while the window is
 * inactive. It has no mouse listener and no tooltip, so FlatLaf's title pane treats it as caption: it drags the window
 * and a double-click maximises it. EDT.
 */
final class ClassicWindowTitle extends JLabel {
    /** The room between the last menu and the title: IntelliJ's 10 px menu gap and 12 px border, in logical pixels. */
    static final int GAP = 22;
    /** The title's width before the menus fold further: FlatLaf's {@code TitlePane.titleMinimumWidth}, in logical pixels. */
    static final int MINIMUM = 60;
    /** The title bar's height, IntelliJ's and the macOS band's, in logical pixels. */
    static final int BAR_HEIGHT = 28;

    private boolean active = true;

    ClassicWindowTitle() {
        super("Jasper", SwingConstants.LEADING);
        putClientProperty("html.disable", Boolean.TRUE);
        getAccessibleContext().setAccessibleName("Window title");
        setOpaque(false);
        refresh();
    }

    /** Dims the title while the window is inactive. */
    void setActive(boolean value) {
        active = value;
        refresh();
    }

    boolean active() { return active; }

    /** Takes the installed theme's body face and the active or inactive title colour again. */
    void refresh() {
        setFont(ThemeZone.font(Typography.Role.BODY));
        setForeground(SurfaceContrast.painted(FlatLafInstaller.installedTheme(), active ? WindowPairs.TITLE : WindowPairs.INACTIVE_TITLE));
        repaint();
    }
}
