package dev.jasper.app.platform;

import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.util.SystemInfo;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeListener;
import java.util.Objects;
import java.util.function.Supplier;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JRootPane;
import javax.swing.SwingUtilities;

/**
 * IntelliJ Classic's macOS title row (its {@code ToolbarServiceImpl} {@code setTransparentTitleBar}): the root takes
 * full-window content under a transparent title bar, and this band, in the toolbar's colour, sits above the content,
 * so macOS draws the traffic lights and the frame's own title over it. It is never a title-bar zone: it paints the
 * fill it is given, with no rule and no text, and holds no control but a child window's ⋮ at its trailing end (parent
 * tabs spec 6). Full screen removes it, so the content meets the screen's
 * top edge, and leaving full screen restores it. A double-click acts as the system setting says
 * ({@link MacTitleDoubleClick}). EDT.
 */
public final class MacTitleBand extends JComponent implements AutoCloseable {
    /** The band's height in points, unscaled because the native title bar does not scale. */
    public static final int HEIGHT = 28;
    /** The trailing control's distance from the window's edge, in points, unscaled as the band is. */
    private static final int TRAILING_GAP = 8;

    private final JRootPane root;
    private final Supplier<Color> fill;
    private final PropertyChangeListener fullScreenChanged;
    private boolean closed;
    /** The control at the band's trailing end; null while it has none. */
    private JComponent trailing;

    /**
     * Gives {@code root} the band above its current content pane, before the window is shown. On macOS it also
     * starts reading the system's double-click setting.
     *
     * @param root the frame's root pane, whose content pane the band wraps
     * @param fill the band's colour, read each time it paints
     * @return the installed band
     */
    public static MacTitleBand install(JRootPane root, Supplier<Color> fill) {
        if (SystemInfo.isMacOS) MacTitleDoubleClick.prefetch();
        return install(root, fill, MacTitleDoubleClick::current);
    }

    static MacTitleBand install(JRootPane root, Supplier<Color> fill, Supplier<MacTitleDoubleClick.Action> doubleClick) {
        root.putClientProperty("apple.awt.fullWindowContent", true);
        root.putClientProperty("apple.awt.transparentTitleBar", true);
        var band = new MacTitleBand(root, fill, doubleClick);
        Container content = root.getContentPane();
        var surface = new JPanel(new BorderLayout());
        surface.add(band, BorderLayout.NORTH);
        surface.add(content, BorderLayout.CENTER);
        root.setContentPane(surface);
        TitleInset.publish(root, band::inset);
        return band;
    }

    private MacTitleBand(JRootPane root, Supplier<Color> fill, Supplier<MacTitleDoubleClick.Action> doubleClick) {
        this.root = root;
        this.fill = Objects.requireNonNull(fill);
        Objects.requireNonNull(doubleClick);
        setOpaque(true);
        fullScreenChanged = event -> {
            int before = inset();
            setVisible(!fullScreen(event.getNewValue()));
            root.revalidate();
            TitleInset.changed(root, before);
        };
        root.addPropertyChangeListener(FlatClientProperties.FULL_WINDOW_CONTENT_BUTTONS_BOUNDS, fullScreenChanged);
        addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent event) {
                if (event.getClickCount() == 2 && SwingUtilities.getWindowAncestor(MacTitleBand.this) instanceof Frame frame)
                    frame.setExtendedState(afterDoubleClick(frame.getExtendedState(), doubleClick.get()));
            }
        });
    }

    /**
     * Gives the window the vibrant dark or light appearance under the band, as IntelliJ Classic does, then repaints.
     *
     * @param dark whether the theme is dark
     */
    public void setDark(boolean dark) {
        MacAppearance.vibrant(root, dark);
        repaint();
    }

    /**
     * The band's colour now.
     *
     * @return the fill it paints
     */
    public Color fill() { return fill.get(); }

    /**
     * How far the band pushes the content down.
     *
     * @return {@link #HEIGHT} while the band shows, else 0
     */
    public int inset() { return isVisible() ? HEIGHT : 0; }

    /** FlatLaf republishes the buttons bounds with width 0 in full screen. */
    static boolean fullScreen(Object buttonsBounds) { return buttonsBounds instanceof Rectangle bounds && bounds.width == 0; }

    static int afterDoubleClick(int state, MacTitleDoubleClick.Action action) {
        return switch (action) {
            case ZOOM -> (state & Frame.MAXIMIZED_BOTH) == Frame.MAXIMIZED_BOTH ? state & ~Frame.MAXIMIZED_BOTH : state | Frame.MAXIMIZED_BOTH;
            case MINIMIZE -> state | Frame.ICONIFIED;
            case NONE -> state;
        };
    }

    /**
     * A control at the band's trailing end, such as a child window's ⋮ (parent tabs spec 6), centred on the band and
     * 8 pt from the window's edge. Once only.
     *
     * @param component the control
     */
    public void setTrailing(JComponent component) {
        if (closed || trailing != null) return;
        trailing = Objects.requireNonNull(component, "component");
        add(component);
        revalidate();
        repaint();
    }

    @Override public void doLayout() {
        if (trailing == null) return;
        Dimension size = trailing.getPreferredSize();
        int height = Math.min(size.height, getHeight());
        trailing.setBounds(Math.max(0, getWidth() - TRAILING_GAP - size.width), (getHeight() - height) / 2, size.width, height);
    }

    @Override public Dimension getPreferredSize() { return new Dimension(0, HEIGHT); }
    @Override public Dimension getMinimumSize() { return new Dimension(0, HEIGHT); }

    @Override protected void paintComponent(Graphics g) {
        g.setColor(fill.get());
        g.fillRect(0, 0, getWidth(), getHeight());
    }

    /** Stops following the root's full-screen changes; later calls do nothing. */
    @Override public void close() {
        if (closed) return;
        closed = true;
        root.removePropertyChangeListener(FlatClientProperties.FULL_WINDOW_CONTENT_BUTTONS_BOUNDS, fullScreenChanged);
    }
}
