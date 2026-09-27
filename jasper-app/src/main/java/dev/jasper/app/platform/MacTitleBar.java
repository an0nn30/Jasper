package dev.jasper.app.platform;

import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.util.UIScale;
import com.formdev.flatlaf.util.SystemInfo;
import com.jetbrains.JBR;
import com.jetbrains.WindowDecorations;
import java.awt.*;
import java.awt.event.*;
import java.beans.PropertyChangeListener;
import javax.swing.*;

/** Native macOS controls with either a dialog title or a workspace context and toolbar. */
public final class MacTitleBar extends JPanel implements AutoCloseable {
    /** Unscaled height of the title row. */
    public static final int TITLE_HEIGHT = 28;
    private final JRootPane root;
    private final Runnable minimumSizeChanged;
    private final JLabel title = new JLabel("Jasper", SwingConstants.CENTER);
    private final PropertyChangeListener boundsChanged;
    private JComponent toolbar;
    private MouseAdapter nativeDrag;
    private final PropertyChangeListener toolbarChanged = event -> {
        toolbarGeometryChanged();
    };
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

    /** Full-window content with transparent title; other platforms retain native decorations. */
    public static boolean isSupported() { return SystemInfo.isMacFullWindowContentSupported; }

    /** Called before pack; the host owns its component and notification wiring. */
    public static MacTitleBar install(JRootPane root, JComponent content, Runnable minimumSizeChanged, boolean supported) {
        root.setContentPane(content);
        if (!supported) return null;
        root.putClientProperty("apple.awt.fullWindowContent", true);
        root.putClientProperty("apple.awt.transparentTitleBar", true);
        root.putClientProperty("apple.awt.windowTitleVisible", false);
        var bar = new MacTitleBar(root, minimumSizeChanged);
        var surface = new JPanel(new BorderLayout());
        surface.add(bar, BorderLayout.NORTH); surface.add(content, BorderLayout.CENTER);
        root.setContentPane(surface);
        return bar;
    }

    private MacTitleBar(JRootPane root, Runnable minimumSizeChanged) {
        super(null);
        this.root = root;
        this.minimumSizeChanged = minimumSizeChanged;
        boundsChanged = event -> { revalidate(); repaint(); minimumSizeChanged.run(); };
        title.putClientProperty("html.disable", true);
        title.getAccessibleContext().setAccessibleName("Window title");
        add(title);
        // A tooltip installs mouse listeners, so explicitly preserve native dragging on context.
        var drag = new MouseAdapter() {
            private void hit() { if (nativeTitle != null) nativeTitle.forceHitTest(false); }
            @Override public void mousePressed(MouseEvent event) { hit(); }
            @Override public void mouseReleased(MouseEvent event) { hit(); }
            @Override public void mouseClicked(MouseEvent event) { hit(); }
            @Override public void mouseEntered(MouseEvent event) { hit(); }
            @Override public void mouseMoved(MouseEvent event) { hit(); }
            @Override public void mouseDragged(MouseEvent event) { hit(); }
        };
        nativeDrag = drag;
        title.addMouseListener(drag); title.addMouseMotionListener(drag);
        root.addPropertyChangeListener(FlatClientProperties.FULL_WINDOW_CONTENT_BUTTONS_BOUNDS, boundsChanged);
    }

    /** Moves an existing toolbar into this native row; ownership stays with the workspace. */
    public void setToolbar(JComponent toolbar) {
        if (closed || this.toolbar != null) return;
        this.toolbar = toolbar;
        add(toolbar);
        toolbar.addPropertyChangeListener(toolbarChanged);
        toolbar.addMouseListener(nativeDrag); toolbar.addMouseMotionListener(nativeDrag);
        title.setHorizontalAlignment(SwingConstants.LEADING);
        title.getAccessibleContext().setAccessibleName("Working context");
        toolbarGeometryChanged();
    }

    private void toolbarGeometryChanged() {
        refreshNativeGeometry(); revalidate(); repaint(); minimumSizeChanged.run();
    }

    /** Focused directory or provider identity; window title metadata is maintained by the host. */
    public void setContext(String label, String tooltip, Icon icon) {
        if (closed) return;
        title.setText(label); title.setToolTipText(tooltip); title.setIcon(icon);
        title.getAccessibleContext().setAccessibleDescription(tooltip);
        revalidate(); repaint();
    }

    /** Menus stay on the root, which the macOS screen menu bar displays. */
    public void setMenuBar(JMenuBar menuBar) {
        if (closed) return;
        root.setJMenuBar(menuBar);
    }

    /** Updates metadata independently of the window's workspace model. */
    public void setTitle(String value) {
        if (closed) return;
        if (!value.equals(title.getText())) title.setText(value);
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
        return Math.max(UIScale.scale(toolbar == null ? 120 : 96), Math.max(nativeLeft, controlsEnd) + UIScale.scale(8));
    }

    private int titleHeight() {
        return toolbar == null ? UIScale.scale(TITLE_HEIGHT)
            : Math.max(UIScale.scale(42), toolbar.getPreferredSize().height);
    }

    @Override public Dimension getMinimumSize() {
        return new Dimension(toolbar == null ? 2 * Math.max(safeInset(), nativeRight) + UIScale.scale(64)
            : safeInset() + nativeRight + UIScale.scale(280), titleHeight());
    }
    @Override public Dimension getPreferredSize() { return new Dimension(Math.max(UIScale.scale(400), getMinimumSize().width), titleHeight()); }

    @Override public void doLayout() {
        refreshNativeGeometry();
        if (toolbar != null) {
            int left = Math.min(safeInset(), getWidth());
            int right = Math.max(UIScale.scale(8), nativeRight);
            int available = Math.max(0, getWidth() - left - right);
            int preferred = Math.min(UIScale.scale(200), title.getPreferredSize().width + UIScale.scale(12));
            int contextWidth = toolbar.isVisible()
                ? Math.min(preferred, Math.max(0, available - UIScale.scale(190))) : available;
            title.setBounds(left, 0, contextWidth, getHeight());
            int gap = contextWidth > 0 ? UIScale.scale(8) : 0;
            toolbar.setBounds(left + contextWidth + gap, 0, Math.max(0, available - contextWidth - gap), getHeight());
            return;
        }
        // Dialog titles remain centred independently of the window controls.
        int inset = Math.max(Math.min(safeInset(), getWidth()), nativeRight);
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
        setBackground(UIManager.getColor("Jasper.titleBackground"));
        title.setFont(SystemFonts.ui(toolbar == null ? Font.BOLD : Font.PLAIN, 13f));
        title.setForeground(UIManager.getColor(active ? "Jasper.titleForeground" : "Jasper.titleInactiveForeground"));
        repaint();
    }

    @Override protected void paintChildren(Graphics g) {
        super.paintChildren(g);
        if (toolbar != null) {
            Color highlight = UIManager.getColor("Jasper.headerHighlight");
            if (highlight != null) { g.setColor(highlight); g.fillRect(0, 0, getWidth(), UIScale.scale(1)); }
        }
        g.setColor(UIManager.getColor("Jasper.titleSeparator"));
        g.fillRect(0, getHeight() - UIScale.scale(1), getWidth(), UIScale.scale(1));
    }

    @Override public void close() {
        if (closed) return;
        closed = true;
        if (toolbar != null) {
            toolbar.removePropertyChangeListener(toolbarChanged);
            toolbar.removeMouseListener(nativeDrag); toolbar.removeMouseMotionListener(nativeDrag);
        }
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
