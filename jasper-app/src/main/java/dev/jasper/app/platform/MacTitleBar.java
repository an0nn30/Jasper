package dev.jasper.app.platform;

import dev.jasper.ui.theme.SemanticColor;

import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.util.UIScale;
import com.formdev.flatlaf.util.SystemInfo;
import com.jetbrains.JBR;
import com.jetbrains.WindowDecorations;
import java.awt.*;
import java.awt.event.*;
import java.beans.PropertyChangeListener;
import javax.swing.*;

/** Native macOS controls with either a dialog title or a workspace header. */
public final class MacTitleBar extends JPanel implements AutoCloseable {
    /** Unscaled height of the title row. */
    public static final int TITLE_HEIGHT = 28;
    private final JRootPane root;
    private final Runnable minimumSizeChanged;
    private final JLabel title = new JLabel("Jasper", SwingConstants.CENTER) {
        @Override public JToolTip createToolTip() {
            var tip = new JToolTip();
            tip.putClientProperty("html.disable", true);
            tip.setComponent(this);
            return tip;
        }
    };
    private final PropertyChangeListener boundsChanged;
    private JComponent header, accessory;
    private java.util.List<JComponent> dragSurfaces = java.util.List.of();
    private MouseAdapter nativeDrag;
    private AWTEventListener diagnosticEvents;
    private final PropertyChangeListener headerChanged = event -> {
        headerGeometryChanged();
    };
    private Window window;
    private WindowDecorations decorations;
    private WindowDecorations.CustomTitleBar nativeTitle;
    private int nativeLeft, nativeRight;
    private boolean active = true;
    private boolean dialogHero;
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

    /**
     * Moves the workspace header into this native row; ownership stays with the workspace. Empty areas of
     * {@code dragSurfaces} drag the window.
     */
    public void setHeader(JComponent header, java.util.List<JComponent> dragSurfaces) {
        if (closed || this.header != null) return;
        this.header = header;
        this.dragSurfaces = java.util.List.copyOf(dragSurfaces);
        add(header);
        header.addPropertyChangeListener(headerChanged);
        for (JComponent surface : this.dragSurfaces) { surface.addMouseListener(nativeDrag); surface.addMouseMotionListener(nativeDrag); }
        title.setVisible(false);
        title.getAccessibleContext().setAccessibleName("Working context");
        headerGeometryChanged();
    }

    /** Auxiliary actions retain their kit borders and identity; the host still owns the centered title. */
    public void setAccessory(JComponent value) {
        if (closed || header != null || accessory != null) throw new IllegalStateException("Title accessory already installed");
        accessory = java.util.Objects.requireNonNull(value); add(value);
        value.addPropertyChangeListener(headerChanged);
        // Temporary observation only: component mouse listeners would change JBR hit classification.
        if (Boolean.getBoolean("jasper.nativeTitleTrace")) {
            diagnosticEvents = event -> {
                if (!(event instanceof MouseEvent mouse) || !(mouse.getSource() instanceof Component source)
                    || SwingUtilities.getRootPane(source) != root || mouse.getID() == MouseEvent.MOUSE_MOVED
                    || mouse.getID() == MouseEvent.MOUSE_DRAGGED) return;
                Point point = SwingUtilities.convertPoint(source, mouse.getPoint(), root);
                if (point.y > getHeight() && !"more.main".equals(source.getName())) return;
                String name = java.util.Set.of("add.main", "add.arrow", "lock", "more.main").contains(source.getName() == null ? "" : source.getName()) ? source.getName() : "other";
                System.getLogger("jasper.native-title-trace").log(System.Logger.Level.INFO,
                    "mouse=" + mouse.getID() + " source=" + source.getClass().getName() + " control=" + name
                        + " point=" + point + " enabled=" + source.isEnabled() + " showing=" + source.isShowing()
                        + " bounds=" + source.getBounds() + " consumed=" + mouse.isConsumed());
            };
            Toolkit.getDefaultToolkit().addAWTEventListener(diagnosticEvents, AWTEvent.MOUSE_EVENT_MASK);
        }
        headerGeometryChanged();
    }
    /** Action updates change layout within existing outer bounds. */
    public void accessoryChanged() { if (!closed) headerGeometryChanged(); }

    private void headerGeometryChanged() {
        refreshNativeGeometry(); revalidate(); repaint(); minimumSizeChanged.run();
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

    /** Hero content already supplies its heading; keep native controls over the dialog surface. */
    public void setDialogHero(boolean hero) {
        if (closed || header != null) return;
        dialogHero = hero;
        title.setVisible(!hero);
        headerGeometryChanged();
        refreshColors();
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
        return Math.max(UIScale.scale(header == null ? 120 : 96), Math.max(nativeLeft, controlsEnd) + UIScale.scale(8));
    }

    private int titleHeight() {
        return header == null ? Math.max(UIScale.scale(dialogHero ? 20 : TITLE_HEIGHT), accessory != null && accessory.isVisible() ? accessory.getPreferredSize().height : 0)
            : Math.max(UIScale.scale(TITLE_HEIGHT), header.getPreferredSize().height);
    }

    @Override public Dimension getMinimumSize() {
        if (header == null) return new Dimension(2 * Math.max(safeInset(), accessory != null && accessory.isVisible() ? accessory.getMinimumSize().width + Math.max(UIScale.scale(8), nativeRight) : nativeRight) + UIScale.scale(64), titleHeight());
        Insets insets = header.getInsets();
        return new Dimension(safeInset() + Math.max(UIScale.scale(8), nativeRight)
            + header.getMinimumSize().width - insets.left - insets.right, titleHeight());
    }
    @Override public Dimension getPreferredSize() {
        return new Dimension(Math.max(header == null ? 0 : UIScale.scale(400), getMinimumSize().width), titleHeight());
    }

    @Override public void doLayout() {
        refreshNativeGeometry();
        if (header != null) {
            int left = Math.min(safeInset(), getWidth());
            int right = Math.max(UIScale.scale(8), nativeRight);
            Insets insets = header.getInsets();
            // The header spans the window so its field centres on it; insets keep clear of the controls.
            if (insets.left != left || insets.right != right) header.setBorder(BorderFactory.createEmptyBorder(0, left, 0, right));
            header.setBounds(0, 0, getWidth(), getHeight());
            return;
        }
        // Dialog titles remain centred independently of the window controls.
        int right = Math.max(UIScale.scale(8), nativeRight);
        int actionWidth = accessory != null && accessory.isVisible()
            ? Math.min(accessory.getPreferredSize().width, Math.max(0, getWidth() - safeInset() - right)) : 0;
        if (accessory != null) accessory.setBounds(Math.max(0, getWidth() - right - actionWidth), 0, actionWidth, getHeight());
        int inset = Math.max(Math.min(safeInset(), getWidth()), actionWidth + right);
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
        setBackground(ThemeZone.color(null, dialogHero ? SemanticColor.SURFACE_DIALOG : SemanticColor.SURFACE_APP));
        title.setFont(SystemFonts.ui(header == null ? Font.BOLD : Font.PLAIN, 13f));
        title.setForeground(ThemeZone.color(this, active ? SemanticColor.TEXT_SECONDARY : SemanticColor.TEXT_TERTIARY));
        repaint();
    }

    @Override protected void paintChildren(Graphics g) {
        super.paintChildren(g);
        if (header != null) {
            Color highlight = dev.jasper.ui.theme.ColorMath.opacity(ThemeZone.color(this, SemanticColor.HOVER_OVERLAY), .08);
            if (highlight != null) { g.setColor(highlight); g.fillRect(0, 0, getWidth(), UIScale.scale(1)); }
        }
        if (header == null && !dialogHero) {
            g.setColor(ThemeZone.color(null, SemanticColor.BORDER_SUBTLE));
            g.fillRect(0, getHeight() - UIScale.scale(1), getWidth(), UIScale.scale(1));
        }
    }

    @Override public void close() {
        if (closed) return;
        closed = true;
        if (header != null) {
            header.removePropertyChangeListener(headerChanged);
            for (JComponent surface : dragSurfaces) { surface.removeMouseListener(nativeDrag); surface.removeMouseMotionListener(nativeDrag); }
        }
        if (accessory != null) accessory.removePropertyChangeListener(headerChanged);
        if (diagnosticEvents != null) Toolkit.getDefaultToolkit().removeAWTEventListener(diagnosticEvents);
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
