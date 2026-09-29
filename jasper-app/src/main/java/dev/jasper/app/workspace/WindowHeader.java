package dev.jasper.app.workspace;

import com.formdev.flatlaf.util.UIScale;
import java.awt.Dimension;
import java.awt.Insets;
import javax.swing.JPanel;

/**
 * The window header on every platform: the title search field centred on the header, which spans the
 * window, and the toolbar packed against the trailing edge. The border's insets keep clear of native
 * window controls. As space runs out, toolbar controls overflow first, then the field shrinks to its
 * minimum, then it collapses to its magnifier. Space the field and toolbar do not use drags the window.
 */
final class WindowHeader extends JPanel {
    private static final int GAP = 12;
    private final TitleSearchField field;
    private final WindowToolbar toolbar;

    WindowHeader(TitleSearchField field, WindowToolbar toolbar) {
        super(null);
        this.field = field;
        this.toolbar = toolbar;
        setName("windowHeader");
        add(field);
        add(toolbar);
        toolbar.addPropertyChangeListener("barHeight", event -> {
            firePropertyChange("barHeight", event.getOldValue(), event.getNewValue());
            revalidate(); repaint();
        });
    }

    TitleSearchField field() { return field; }
    WindowToolbar toolbar() { return toolbar; }

    private int height() { return toolbar.getPreferredSize().height; }
    private int fieldHeight(int height) { return Math.max(0, height - UIScale.scale(8)); }

    @Override public Dimension getPreferredSize() {
        Insets insets = getInsets();
        return new Dimension(insets.left + insets.right + 2 * UIScale.scale(GAP) + UIScale.scale(TitleSearchField.PREFERRED_WIDTH)
            + (toolbar.isVisible() ? toolbar.fullWidth() : 0), height());
    }

    @Override public Dimension getMinimumSize() {
        Insets insets = getInsets();
        return new Dimension(insets.left + insets.right + 2 * UIScale.scale(GAP) + fieldHeight(height())
            + (toolbar.isVisible() ? toolbar.minimumWidth() : 0), height());
    }

    @Override public void doLayout() {
        Insets insets = getInsets();
        int width = getWidth(), height = getHeight(), gap = UIScale.scale(GAP);
        int left = Math.min(insets.left, width), inner = Math.max(0, width - left - insets.right);
        int preferred = UIScale.scale(TitleSearchField.PREFERRED_WIDTH), minimum = UIScale.scale(TitleSearchField.MINIMUM_WIDTH);
        int toolbarWidth = 0;
        if (toolbar.isVisible())
            toolbarWidth = Math.min(inner, Math.max(toolbar.minimumWidth(), Math.min(toolbar.fullWidth(), inner - preferred - 2 * gap)));
        toolbar.setBounds(left + inner - toolbarWidth, 0, toolbarWidth, height);
        int start = left + gap, end = left + inner - toolbarWidth - gap;
        int available = end - start, fieldHeight = fieldHeight(height), y = (height - fieldHeight) / 2;
        if (available < minimum) {
            field.setCollapsed(true);
            int side = available >= fieldHeight ? fieldHeight : 0;
            field.setBounds(Math.max(start, Math.min(width / 2 - side / 2, end - side)), y, side, side);
            return;
        }
        field.setCollapsed(false);
        int fieldWidth = Math.min(preferred, available);
        field.setBounds(Math.max(start, Math.min(width / 2 - fieldWidth / 2, end - fieldWidth)), y, fieldWidth, fieldHeight);
    }
}
