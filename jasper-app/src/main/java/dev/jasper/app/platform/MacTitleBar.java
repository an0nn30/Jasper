package dev.jasper.app.platform;

import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.util.UIScale;
import com.formdev.flatlaf.util.SystemInfo;
import com.jetbrains.JBR;
import com.jetbrains.WindowDecorations;
import java.awt.*;
import java.awt.event.*;
import java.beans.PropertyChangeListener;
import java.util.function.IntSupplier;
import javax.swing.*;

/** Integrated modern tabs or a retro title; macOS owns window controls and gestures. */
public final class MacTitleBar extends JPanel implements AutoCloseable {
    private final JRootPane root;
    private final boolean retro;
    private final JComponent tabs;
    private final IntSupplier tabHeight;
    private final Runnable minimumSizeChanged;
    private final JLabel title = new JLabel("Jasper", SwingConstants.CENTER);
    private final PropertyChangeListener boundsChanged;
    private Window window;
    private WindowDecorations decorations;
    private WindowDecorations.CustomTitleBar nativeTitle;
    private int nativeLeft, nativeRight;
    private boolean active = true;
    private boolean closed;
    private final ComponentAdapter frameBounds = new ComponentAdapter() {
        @Override public void componentResized(ComponentEvent event) { refreshNativeGeometry(); }
        @Override public void componentShown(ComponentEvent event) { refreshNativeGeometry(); }
    };
    private final WindowStateListener stateChanged = event -> SwingUtilities.invokeLater(this::refreshNativeGeometry);
    private final HierarchyListener peerChanged = event -> {
        if ((event.getChangeFlags() & HierarchyEvent.DISPLAYABILITY_CHANGED) != 0)
            SwingUtilities.invokeLater(this::refreshNativeGeometry);
    };

    /** Retro requires JBR's native-control geometry; other platforms retain native decorations. */
    public static boolean isSupported() {
        return SystemInfo.isMacFullWindowContentSupported
            && (!SwingAppearance.retro() || JBR.isWindowDecorationsSupported());
    }

    /** Called before pack; the host owns its component and notification wiring. */
    public static MacTitleBar install(JRootPane root, JComponent content, JComponent tabs,
                               IntSupplier tabHeight, Runnable minimumSizeChanged, boolean supported) {
        root.setContentPane(content);
        if (!supported) return null;
        root.putClientProperty("apple.awt.fullWindowContent", true);
        root.putClientProperty("apple.awt.transparentTitleBar", true);
        root.putClientProperty("apple.awt.windowTitleVisible", false);
        var bar = new MacTitleBar(root, tabs, tabHeight, minimumSizeChanged);
        var surface = new JPanel(new BorderLayout());
        surface.add(bar, BorderLayout.NORTH); surface.add(content, BorderLayout.CENTER);
        root.setContentPane(surface);
        return bar;
    }

    private MacTitleBar(JRootPane root, JComponent tabs, IntSupplier tabHeight, Runnable minimumSizeChanged) {
        super(null);
        this.root = root; this.retro = SwingAppearance.retro();
        this.tabs = retro ? null : tabs;
        this.tabHeight = tabHeight; this.minimumSizeChanged = minimumSizeChanged;
        boundsChanged = event -> { revalidate(); repaint(); minimumSizeChanged.run(); };
        title.putClientProperty("html.disable", true);
        title.getAccessibleContext().setAccessibleName("Window title");
        add(title); if (this.tabs != null) add(this.tabs);
        root.addPropertyChangeListener(FlatClientProperties.FULL_WINDOW_CONTENT_BUTTONS_BOUNDS, boundsChanged);
    }

    /** Places in-window retro menus below the native title region; modern menus retain root ownership. */
    public void setMenuBar(JMenuBar menuBar) {
        if (closed) return;
        if (!retro) { root.setJMenuBar(menuBar); return; }
        root.setJMenuBar(null);
        var heading = new JPanel(new BorderLayout());
        heading.add(this, BorderLayout.NORTH);
        menuBar.setBackground(new Color(UIManager.getColor("Panel.background").getRGB()));
        menuBar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, UIManager.getColor("MenuBar.borderColor")));
        heading.add(menuBar, BorderLayout.CENTER);
        root.getContentPane().add(heading, BorderLayout.NORTH);
        root.revalidate();
    }

    /** Updates metadata independently of the window's workspace model. */
    public void setTitle(String value, boolean singleTab) {
        if (closed) return;
        if (!value.equals(title.getText())) title.setText(value);
        title.setVisible(retro || singleTab);
        if (tabs != null) tabs.setVisible(!singleTab);
        revalidate(); repaint();
    }

    /** Public JBR API only. Root properties above remain the fallback on other runtimes. */
    public void attach(Window window) {
        if (!(window instanceof Frame) && !(window instanceof Dialog))
            throw new IllegalArgumentException("A title bar needs a frame or dialog");
        if (closed || this.window != null || !JBR.isWindowDecorationsSupported()) return;
        decorations = JBR.getWindowDecorations();
        nativeTitle = decorations.createCustomTitleBar();
        nativeTitle.setHeight(titleHeight());
        this.window = window;
        applyNativeTitle(nativeTitle);
        window.addComponentListener(frameBounds);
        window.addWindowStateListener(stateChanged);
        window.addHierarchyListener(peerChanged);
        refreshNativeGeometry();
    }

    private void applyNativeTitle(WindowDecorations.CustomTitleBar value) {
        if (window instanceof Frame frame) decorations.setCustomTitleBar(frame, value);
        else if (window instanceof Dialog dialog) decorations.setCustomTitleBar(dialog, value);
    }

    private void refreshNativeGeometry() {
        if (closed || nativeTitle == null) return;
        if (nativeTitle.getHeight() != titleHeight()) nativeTitle.setHeight(titleHeight());
        int left = (int) Math.ceil(nativeTitle.getLeftInset());
        int right = (int) Math.ceil(nativeTitle.getRightInset());
        if (nativeLeft != left || nativeRight != right) {
            nativeLeft = left; nativeRight = right;
            revalidate(); repaint(); minimumSizeChanged.run();
        }
    }

    private int safeInset() {
        Object bounds = root.getClientProperty(FlatClientProperties.FULL_WINDOW_CONTENT_BUTTONS_BOUNDS);
        // Published native bounds are already in root-pane coordinates; do not scale again.
        int controlsEnd = bounds instanceof Rectangle rectangle ? Math.max(0, rectangle.x + rectangle.width) : 0;
        return Math.max(UIScale.scale(120), Math.max(nativeLeft, controlsEnd) + UIScale.scale(8));
    }

    private int titleHeight() { return UIScale.scale(retro ? 32 : tabHeight.getAsInt()); }

    public void refreshHeight() {
        if (closed) return;
        refreshNativeGeometry();
        revalidate(); repaint(); root.revalidate(); root.repaint();
    }

    @Override public Dimension getMinimumSize() {
        int width = retro ? 2 * Math.max(safeInset(), nativeRight) + UIScale.scale(64)
            : safeInset() + nativeRight + tabs.getMinimumSize().width;
        return new Dimension(width, titleHeight());
    }
    @Override public Dimension getPreferredSize() { return new Dimension(Math.max(UIScale.scale(400), getMinimumSize().width), titleHeight()); }

    @Override public void doLayout() {
        refreshNativeGeometry();
        int left = Math.min(safeInset(), getWidth());
        int available = Math.max(0, getWidth() - left - nativeRight);
        if (tabs != null) tabs.setBounds(left, 0, available, getHeight());
        // A lone session uses the ordinary centered window title. Reserve the same space
        // at both ends so native controls cannot shift the title away from the window center.
        int inset = Math.max(left, nativeRight);
        title.setBounds(inset, 0, Math.max(0, getWidth() - 2 * inset), getHeight());
    }

    public void setLight(boolean light) {
        if (closed) return;
        root.putClientProperty("apple.awt.windowAppearance",
            light ? "NSAppearanceNameAqua" : "NSAppearanceNameDarkAqua");
        refreshColors();
    }

    public void setActive(boolean active) { this.active = active; refreshColors(); }

    private void refreshColors() {
        setBackground(UIManager.getColor(retro ? "Jasper.retroTitleBackground" : "Jasper.titleBackground"));
        title.setFont(retro ? UIManager.getFont("Label.font").deriveFont(Font.PLAIN, UIManager.getFont("Label.font").getSize2D() + UIScale.scale(1f))
            : SystemFonts.ui(Font.PLAIN, 13f));
        title.setForeground(UIManager.getColor(active ? "Jasper.titleForeground" : "Jasper.titleInactiveForeground"));
        repaint();
    }

    @Override protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        g.setColor(UIManager.getColor(retro ? "MenuBar.borderColor" : "Jasper.titleSeparator"));
        g.fillRect(0, getHeight() - UIScale.scale(1), getWidth(), UIScale.scale(1));
    }

    @Override public void close() {
        if (closed) return;
        closed = true;
        root.removePropertyChangeListener(FlatClientProperties.FULL_WINDOW_CONTENT_BUTTONS_BOUNDS, boundsChanged);
        if (window != null) {
            window.removeComponentListener(frameBounds);
            window.removeWindowStateListener(stateChanged);
            window.removeHierarchyListener(peerChanged);
            applyNativeTitle(null);
            window = null; nativeTitle = null; decorations = null;
        }
    }
}
