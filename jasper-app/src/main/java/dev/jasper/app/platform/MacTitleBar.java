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

/** Native macOS controls with either a window or dialog title or a workspace header. */
public final class MacTitleBar extends JPanel implements AutoCloseable {
    /** Unscaled height of the workspace header row's floor. */
    public static final int TITLE_HEIGHT = 28;
    /**
     * How a title row without a workspace header looks; the kit's title metrics size each one.
     */
    public enum Style {
        /** An auxiliary window's row: centred title, title actions at the right and a hairline below. */
        WINDOW,
        /** A dialog's band in the dialog surface, with no rule; its title and the controls centre lower, as a dialog's do. */
        DIALOG,
        /** A prompt dialog: no title, since the content has its heading; the controls over the dialog surface. */
        PROMPT,
        /** The content draws the title row itself (the Settings sidebar and header): no strip, only the controls. */
        INTEGRATED
    }
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
    private final PropertyChangeListener headerChanged = event -> {
        headerGeometryChanged();
    };
    private Window window;
    private WindowDecorations decorations;
    private WindowDecorations.CustomTitleBar nativeTitle;
    private int nativeLeft, nativeRight;
    private boolean active = true;
    private Style style = Style.WINDOW;
    /** The content's own title row height for {@link Style#INTEGRATED}, in pixels. */
    private java.util.function.IntSupplier integratedHeight = () -> 0;
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
        ThemeZone.titleBar(this);
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

    /**
     * A square of {@code hue}'s theme colour before a window or dialog title ({@code DialogSpec.swatch}); NONE draws none.
     * A row that shows no title shows no square either.
     */
    public void setTitleSwatch(dev.jasper.ui.model.Nodes.Hue hue) {
        if (closed) return;
        SemanticColor role = switch (java.util.Objects.requireNonNull(hue)) {
            case BLUE -> SemanticColor.HUE_BLUE; case GREEN -> SemanticColor.HUE_GREEN; case AMBER -> SemanticColor.HUE_AMBER;
            case PURPLE -> SemanticColor.HUE_PURPLE; case NEUTRAL -> SemanticColor.HUE_NEUTRAL; case NONE -> null;
        };
        title.setIcon(role == null ? null : new TitleSwatch(role));
        title.setIconTextGap(dev.jasper.ui.swing.NativeStyles.installed().metric(dev.jasper.ui.metrics.DesignMetrics.Role.ICON_GAP));
        revalidate(); repaint();
    }

    /** A solid square of a hue role at the caption icon size, coloured from the theme each time it paints. */
    private record TitleSwatch(SemanticColor role) implements Icon {
        private static int side() { return dev.jasper.ui.swing.NativeStyles.installed().metric(dev.jasper.ui.metrics.DesignMetrics.Role.ICON_CAPTION); }
        @Override public int getIconWidth() { return side(); }
        @Override public int getIconHeight() { return side(); }
        @Override public void paintIcon(Component component, Graphics graphics, int x, int y) {
            graphics.setColor(ThemeZone.color(component, role));
            graphics.fillRect(x, y, side(), side());
        }
    }

    /** How this title row looks; a workspace header keeps its own. */
    public void setStyle(Style value) {
        if (closed || header != null || value == Style.INTEGRATED) return;
        restyle(value);
    }

    /**
     * The content draws its own title row, {@code height} pixels tall when asked: this row shows nothing and takes no
     * space, and the window controls centre on the content's row. Empty areas of the content there drag the window.
     */
    public void integrate(java.util.function.IntSupplier height) {
        if (closed || header != null) return;
        integratedHeight = java.util.Objects.requireNonNull(height);
        restyle(Style.INTEGRATED);
    }

    public Style style() { return style; }

    private void restyle(Style value) {
        style = java.util.Objects.requireNonNull(value);
        title.setVisible(value == Style.WINDOW || value == Style.DIALOG);
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
        nativeTitle.setHeight(nativeHeight());
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
        if (nativeTitle.getHeight() != nativeHeight()) nativeTitle.setHeight(nativeHeight());
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

    private static int metric(dev.jasper.ui.metrics.DesignMetrics.Role role) {
        return dev.jasper.ui.swing.NativeStyles.installed().metric(role);
    }

    private int accessoryHeight() { return accessory != null && accessory.isVisible() ? accessory.getPreferredSize().height : 0; }

    /** The row's own height: what it takes above the content. */
    private int titleHeight() {
        if (header != null) return Math.max(UIScale.scale(TITLE_HEIGHT), header.getPreferredSize().height);
        return switch (style) {
            case WINDOW -> Math.max(metric(dev.jasper.ui.metrics.DesignMetrics.Role.WINDOW_TITLE_HEIGHT)
                + metric(dev.jasper.ui.metrics.DesignMetrics.Role.HAIRLINE), accessoryHeight());
            case DIALOG -> Math.max(metric(dev.jasper.ui.metrics.DesignMetrics.Role.DIALOG_TITLE_HEIGHT), accessoryHeight());
            case PROMPT -> metric(dev.jasper.ui.metrics.DesignMetrics.Role.PROMPT_TITLE_HEIGHT);
            case INTEGRATED -> 0;
        };
    }

    /** The native title region, on whose middle macOS centres the window controls; it may reach over the content. */
    public int controlsHeight() { return nativeHeight(); }

    private int nativeHeight() {
        if (header != null) return titleHeight();
        return switch (style) {
            case WINDOW -> titleHeight();
            case DIALOG -> Math.max(titleHeight(), 2 * metric(dev.jasper.ui.metrics.DesignMetrics.Role.DIALOG_TITLE_CENTER));
            case PROMPT -> 2 * metric(dev.jasper.ui.metrics.DesignMetrics.Role.PROMPT_CONTROLS_CENTER);
            case INTEGRATED -> Math.max(0, integratedHeight.getAsInt());
        };
    }

    /** Where the title's middle sits in this row. */
    private int titleCenter() {
        return style == Style.DIALOG ? metric(dev.jasper.ui.metrics.DesignMetrics.Role.DIALOG_TITLE_CENTER)
            : (getHeight() - (style == Style.WINDOW ? metric(dev.jasper.ui.metrics.DesignMetrics.Role.HAIRLINE) : 0)) / 2;
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
        int center = titleCenter();
        if (accessory != null) {
            int height = Math.min(Math.max(1, accessory.getPreferredSize().height), getHeight());
            accessory.setBounds(Math.max(0, getWidth() - right - actionWidth), Math.max(0, center - height / 2), actionWidth, height);
        }
        int inset = Math.max(Math.min(safeInset(), getWidth()), actionWidth + right);
        int height = Math.min(title.getPreferredSize().height, getHeight());
        title.setBounds(inset, Math.max(0, Math.min(getHeight() - height, center - height / 2)), Math.max(0, getWidth() - 2 * inset), height);
    }

    public void setLight(boolean light) {
        if (closed) return;
        MacAppearance.follow(root, !light);
        refreshColors();
    }

    public void setActive(boolean active) { this.active = active; refreshColors(); }

    private void refreshColors() {
        setBackground(ThemeZone.color(this, style == Style.WINDOW ? SemanticColor.SURFACE_APP : SemanticColor.SURFACE_DIALOG));
        title.setFont(SystemFonts.ui(header == null ? Font.BOLD : Font.PLAIN, 13f));
        title.setForeground(ThemeZone.color(this, active ? SemanticColor.TEXT_SECONDARY : SemanticColor.TEXT_TERTIARY));
        repaint();
    }

    @Override protected void paintChildren(Graphics g) {
        super.paintChildren(g);
        // A header row's top highlight follows its own zone; a window title's lower rule follows the chrome.
        dev.jasper.ui.swing.ChromePainter.installed().titleBarEdges(this, g, ThemeZone.theme(header != null ? this : null), header != null, style == Style.WINDOW);
    }

    @Override public void close() {
        if (closed) return;
        closed = true;
        if (header != null) {
            header.removePropertyChangeListener(headerChanged);
            for (JComponent surface : dragSurfaces) { surface.removeMouseListener(nativeDrag); surface.removeMouseMotionListener(nativeDrag); }
        }
        if (accessory != null) accessory.removePropertyChangeListener(headerChanged);
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
