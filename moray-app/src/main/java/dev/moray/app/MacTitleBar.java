package dev.moray.app;

import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.util.UIScale;
import com.jetbrains.JBR;
import com.jetbrains.WindowDecorations;
import java.awt.*;
import java.awt.event.*;
import java.beans.PropertyChangeListener;
import java.util.function.Consumer;
import javax.swing.*;

/** Integrated title tabs; macOS retains ownership of the decorated window and its gestures. */
final class MacTitleBar extends JPanel implements AutoCloseable {
    private final JRootPane root;
    private final WindowContent content;
    private final JLabel title = new JLabel("Moray", SwingConstants.RIGHT);
    private final PropertyChangeListener boundsChanged;
    private JFrame frame;
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

    /** Called before pack; the platform argument keeps configuration testable without a JFrame. */
    static MacTitleBar install(JRootPane root, WindowContent content, boolean supported, Consumer<String> nativeTitle) {
        root.setContentPane(content);
        content.onTitle = value -> nativeTitle.accept(Main.windowTitle(value));
        if (!supported) return null;

        root.putClientProperty("apple.awt.fullWindowContent", true);
        root.putClientProperty("apple.awt.transparentTitleBar", true);
        root.putClientProperty("apple.awt.windowTitleVisible", false);
        var bar = new MacTitleBar(root, content);
        var surface = new JPanel(new BorderLayout());
        surface.add(bar, BorderLayout.NORTH); surface.add(content, BorderLayout.CENTER);
        root.setContentPane(surface);
        content.onTitle = value -> {
            String displayTitle = Main.windowTitle(value);
            if (!"Moray".equals(displayTitle)) displayTitle += " \u2014 Moray";
            nativeTitle.accept(displayTitle);
            if (!displayTitle.equals(bar.title.getText())) { bar.title.setText(displayTitle); bar.revalidate(); }
        };
        content.onThemeChanged = bar::applyTheme;
        bar.applyTheme(content.theme());
        content.update();
        return bar;
    }

    private MacTitleBar(JRootPane root, WindowContent content) {
        super(null);
        this.root = root; this.content = content;
        boundsChanged = event -> { revalidate(); repaint(); content.onMinimumSizeChanged.run(); };
        title.putClientProperty("html.disable", true);
        title.getAccessibleContext().setAccessibleName("Window title");
        add(title); add(content.windowTabs());
        root.addPropertyChangeListener(FlatClientProperties.FULL_WINDOW_CONTENT_BUTTONS_BOUNDS, boundsChanged);
    }

    /** Public JBR API only. Root properties above remain the fallback on other runtimes. */
    void attach(JFrame frame) {
        if (closed || this.frame != null || !JBR.isWindowDecorationsSupported()) return;
        decorations = JBR.getWindowDecorations();
        nativeTitle = decorations.createCustomTitleBar();
        nativeTitle.setHeight(titleHeight());
        decorations.setCustomTitleBar(frame, nativeTitle);
        this.frame = frame;
        frame.addComponentListener(frameBounds);
        frame.addWindowStateListener(stateChanged);
        frame.addHierarchyListener(peerChanged);
        refreshNativeGeometry();
    }

    private void refreshNativeGeometry() {
        if (closed || nativeTitle == null) return;
        if (nativeTitle.getHeight() != titleHeight()) nativeTitle.setHeight(titleHeight());
        int left = (int) Math.ceil(nativeTitle.getLeftInset());
        int right = (int) Math.ceil(nativeTitle.getRightInset());
        if (nativeLeft != left || nativeRight != right) {
            nativeLeft = left; nativeRight = right;
            revalidate(); repaint(); content.onMinimumSizeChanged.run();
        }
    }

    private int safeInset() {
        Object bounds = root.getClientProperty(FlatClientProperties.FULL_WINDOW_CONTENT_BUTTONS_BOUNDS);
        // Published native bounds are already in root-pane coordinates; do not scale again.
        int controlsEnd = bounds instanceof Rectangle rectangle ? Math.max(0, rectangle.x + rectangle.width) : 0;
        return Math.max(UIScale.scale(98), Math.max(nativeLeft, controlsEnd) + UIScale.scale(8));
    }

    private int titleHeight() { return UIScale.scale(54); }

    @Override public Dimension getMinimumSize() {
        return new Dimension(safeInset() + nativeRight + content.windowTabs().getMinimumSize().width, titleHeight());
    }
    @Override public Dimension getPreferredSize() { return new Dimension(Math.max(UIScale.scale(400), getMinimumSize().width), titleHeight()); }

    @Override public void doLayout() {
        refreshNativeGeometry();
        int left = Math.min(safeInset(), getWidth());
        int available = Math.max(0, getWidth() - left - nativeRight);
        int gap = Math.min(available, UIScale.scale(14));
        int titleWidth = Math.min(UIScale.scale(260), title.getPreferredSize().width);
        titleWidth = Math.min(titleWidth, Math.max(0, available - content.windowTabs().getMinimumSize().width - gap * 2));
        int tabsWidth = Math.max(0, available - titleWidth - gap * 2);
        content.windowTabs().setBounds(left, 0, tabsWidth, getHeight());
        title.setBounds(left + tabsWidth + gap, 0, titleWidth, getHeight());
    }

    private void applyTheme(BuiltinTheme theme) {
        if (closed) return;
        root.putClientProperty("apple.awt.windowAppearance",
            theme == BuiltinTheme.LIGHT ? "NSAppearanceNameAqua" : "NSAppearanceNameDarkAqua");
        refreshColors();
    }

    void setActive(boolean active) { this.active = active; content.windowTabs().setActive(active); refreshColors(); }

    private void refreshColors() {
        setBackground(UIManager.getColor("Moray.titleBackground"));
        title.setFont(UIManager.getFont("Label.font").deriveFont(Font.BOLD, UIScale.scale(12f)));
        title.setForeground(UIManager.getColor(active ? "Moray.titleForeground" : "Moray.titleInactiveForeground"));
        repaint();
    }

    @Override protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        g.setColor(UIManager.getColor("Moray.titleSeparator"));
        g.fillRect(0, getHeight() - UIScale.scale(1), getWidth(), UIScale.scale(1));
    }

    @Override public void close() {
        if (closed) return;
        closed = true;
        root.removePropertyChangeListener(FlatClientProperties.FULL_WINDOW_CONTENT_BUTTONS_BOUNDS, boundsChanged);
        if (frame != null) {
            frame.removeComponentListener(frameBounds);
            frame.removeWindowStateListener(stateChanged);
            frame.removeHierarchyListener(peerChanged);
            decorations.setCustomTitleBar(frame, null);
            frame = null; nativeTitle = null; decorations = null;
        }
        content.onThemeChanged = theme -> {};
        content.onTitle = value -> {};
    }
}
