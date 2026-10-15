package dev.jasper.app.workspace;

import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.swing.NativeStyles;

import com.formdev.flatlaf.util.UIScale;
import java.awt.Dimension;
import java.awt.Insets;
import java.util.function.IntSupplier;
import java.util.function.IntUnaryOperator;
import javax.swing.JPanel;
import javax.swing.JRootPane;
import javax.swing.SwingUtilities;

/**
 * The window header on every platform: the title search field and the toolbar packed against the trailing
 * edge. The border's insets keep clear of native window controls. The field is always centred on the window
 * (the root pane), whatever the header's own bounds. As space runs out each side gives way first: toolbar
 * labels, then toolbar controls into the overflow menu, and the leading content (the Windows and Linux menus)
 * folds; only then does the field shrink around the centre to its minimum and collapse to its magnifier.
 * Space the field and toolbar do not use drags the window.
 */
final class WindowHeader extends JPanel {
    /** The installed kit's gap between the field and each side, in logical pixels. */
    private static int headerGap() { return dev.jasper.ui.swing.FlatLafInstaller.installedKit().metrics().value(DesignMetrics.Role.NATIVE_HEADER_GAP); }
    private final TitleSearchField field;
    private final WindowToolbar toolbar;

    WindowHeader(TitleSearchField field, WindowToolbar toolbar) {
        super(null);
        this.field = field;
        this.toolbar = toolbar;
        setName("windowHeader");
        // The title bar is a native chrome receiver: its toolbar and command centre show the original chrome artwork.
        dev.jasper.app.platform.AppIcons.chromeReceiver(this);
        add(field);
        add(toolbar);
        toolbar.addPropertyChangeListener("barHeight", event -> {
            firePropertyChange("barHeight", event.getOldValue(), event.getNewValue());
            revalidate(); repaint();
        });
    }

    private IntUnaryOperator leading = space -> 0;
    private IntSupplier leadingMinimum = () -> 0;

    /**
     * Content left of the field (the Windows and Linux menus): {@code fit} receives the room left of the field and
     * returns the width it uses; {@code minimum} is the least it can ever use.
     */
    void setLeading(IntUnaryOperator fit, IntSupplier minimum) {
        leading = fit; leadingMinimum = minimum;
        revalidate(); repaint();
    }

    /** The window's horizontal centre in this header's coordinates. */
    int centre() {
        JRootPane root = SwingUtilities.getRootPane(this);
        if (root == null) return getWidth() / 2;
        return root.getWidth() / 2 - SwingUtilities.convertPoint(this, 0, 0, root).x;
    }

    TitleSearchField field() { return field; }
    WindowToolbar toolbar() { return toolbar; }

    private int height() { return toolbar.getPreferredSize().height; }
    private int fieldHeight(int height) { return Math.max(0, height - NativeStyles.installed().metric(DesignMetrics.Role.TITLE_SEARCH_VERTICAL_SPACE)); }

    /** The header adapts to the window's width, so it never asks for more than its minimum; the terminal sizes new windows. */
    @Override public Dimension getPreferredSize() { return getMinimumSize(); }

    @Override public Dimension getMinimumSize() {
        Insets insets = getInsets();
        int side = Math.max(insets.left + leadingMinimum.getAsInt(), insets.right + (toolbar.isVisible() ? toolbar.minimumWidth() : 0));
        return new Dimension(2 * (side + UIScale.scale(headerGap())) + fieldHeight(height()), height());
    }

    @Override public void doLayout() {
        Insets insets = getInsets();
        int width = getWidth(), height = getHeight(), gap = UIScale.scale(headerGap());
        int left = Math.min(insets.left, width), right = Math.max(left, width - insets.right);
        int centre = centre();
        int preferred = UIScale.scale(TitleSearchField.preferredWidth()), minimum = UIScale.scale(TitleSearchField.minimumWidth());
        int toolbarMinimum = toolbar.isVisible() ? toolbar.minimumWidth() : 0;
        // Each side gives way before the field does; the field keeps its centre.
        int half = Math.min(centre - left - leadingMinimum.getAsInt() - gap, right - centre - gap - toolbarMinimum);
        int fieldHeight = fieldHeight(height), y = (height - fieldHeight) / 2;
        int fieldWidth = Math.min(preferred, 2 * half);
        boolean collapsed = fieldWidth < minimum;
        if (collapsed) fieldWidth = 2 * half >= fieldHeight ? fieldHeight : 0;
        field.setCollapsed(collapsed);
        int fieldX = centre - fieldWidth / 2;
        field.setBounds(fieldX, y, fieldWidth, fieldWidth == 0 ? 0 : fieldHeight);
        leading.applyAsInt(Math.max(0, fieldX - gap - left));
        int toolbarX = Math.min(right, fieldX + fieldWidth + gap);
        toolbar.setBounds(toolbarX, 0, toolbar.isVisible() ? right - toolbarX : 0, height);
    }
}
