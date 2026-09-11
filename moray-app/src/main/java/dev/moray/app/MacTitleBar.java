package dev.moray.app;

import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.util.UIScale;
import java.awt.*;
import java.beans.PropertyChangeListener;
import java.util.function.Consumer;
import javax.swing.*;

/** Painted title surface; macOS continues to own the decorated window and its gestures. */
final class MacTitleBar extends JPanel implements AutoCloseable {
    private final JRootPane root;
    private final WindowContent content;
    private final JLabel title = new JLabel("Moray", SwingConstants.CENTER);
    private final PropertyChangeListener boundsChanged;
    private boolean active = true;
    private boolean closed;

    /** Called before pack; the platform argument keeps native configuration testable without a JFrame. */
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
            nativeTitle.accept(displayTitle);
            bar.title.setText(displayTitle);
        };
        content.onThemeChanged = bar::applyTheme;
        // Registration already applied the theme before the native boundary installed its callback.
        bar.applyTheme(content.theme());
        return bar;
    }

    private MacTitleBar(JRootPane root, WindowContent content) {
        super(null);
        this.root = root; this.content = content;
        boundsChanged = event -> {
            revalidate(); repaint(); content.onMinimumSizeChanged.run();
        };
        title.putClientProperty("html.disable", true);
        title.getAccessibleContext().setAccessibleName("Window title");
        add(title);
        root.addPropertyChangeListener(FlatClientProperties.FULL_WINDOW_CONTENT_BUTTONS_BOUNDS, boundsChanged);
    }

    private Rectangle buttonsBounds() {
        Object bounds = root.getClientProperty(FlatClientProperties.FULL_WINDOW_CONTENT_BUTTONS_BOUNDS);
        // Published bounds are already in root-pane coordinates: never scale them a second time.
        return bounds instanceof Rectangle rectangle ? rectangle : new Rectangle(UIScale.scale(68), UIScale.scale(28));
    }

    private int safeInset() {
        Rectangle bounds = buttonsBounds();
        return Math.max(0, bounds.x + bounds.width) + UIScale.scale(8);
    }

    private int titleHeight() {
        Rectangle bounds = buttonsBounds();
        return Math.max(Math.max(UIScale.scale(28), bounds.y + bounds.height),
            title.getFontMetrics(title.getFont()).getHeight() + UIScale.scale(8));
    }

    @Override public Dimension getMinimumSize() { return new Dimension(safeInset() * 2, titleHeight()); }
    @Override public Dimension getPreferredSize() { return new Dimension(Math.max(UIScale.scale(400), safeInset() * 2), titleHeight()); }

    @Override public void doLayout() {
        int inset = Math.min(safeInset(), getWidth() / 2);
        title.setBounds(inset, 0, Math.max(0, getWidth() - inset * 2), getHeight());
    }

    private void applyTheme(BuiltinTheme theme) {
        if (closed) return;
        root.putClientProperty("apple.awt.windowAppearance",
            theme == BuiltinTheme.LIGHT ? "NSAppearanceNameAqua" : "NSAppearanceNameDarkAqua");
        refreshColors();
    }

    void setActive(boolean active) { this.active = active; refreshColors(); }

    private void refreshColors() {
        setBackground(UIManager.getColor("Moray.titleBackground"));
        title.setForeground(UIManager.getColor(active ? "Moray.titleForeground" : "Moray.titleInactiveForeground"));
        repaint();
    }

    @Override public void close() {
        if (closed) return;
        closed = true;
        root.removePropertyChangeListener(FlatClientProperties.FULL_WINDOW_CONTENT_BUTTONS_BOUNDS, boundsChanged);
        content.onThemeChanged = theme -> {};
        content.onTitle = value -> {};
    }
}
