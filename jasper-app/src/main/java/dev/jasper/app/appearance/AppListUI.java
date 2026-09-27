package dev.jasper.app.appearance;

import com.formdev.flatlaf.ui.FlatListUI;
import com.formdev.flatlaf.util.Graphics2DProxy;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import javax.swing.JComponent;
import javax.swing.ListCellRenderer;
import javax.swing.ListModel;
import javax.swing.ListSelectionModel;
import javax.swing.plaf.ComponentUI;

/** Extends FlatLaf's selection painting to composite plugin list renderers. */
public final class AppListUI extends FlatListUI {
    /** Swing look-and-feel factory; each list owns its delegate. */
    public static ComponentUI createUI(JComponent component) { return new AppListUI(); }

    @Override
    @SuppressWarnings("rawtypes") // BasicListUI's inherited signature predates generics.
    protected void paintCell(Graphics graphics, int row, Rectangle bounds, ListCellRenderer renderer,
                             ListModel model, ListSelectionModel selection, int lead) {
        // Standard renderers already use FlatLaf's proxy. This also catches a plugin panel or
        // label filling the entire row with the list's selection color, without changing its
        // component hierarchy, layout, text, icons, or explicitly custom background colors.
        if (selection.isSelectedIndex(row) && !Boolean.TRUE.equals(list.getClientProperty("List.isFileList")))
            graphics = new SelectionGraphics((Graphics2D) graphics, row, bounds);
        super.paintCell(graphics, row, bounds, renderer, model, selection, lead);
    }

    private final class SelectionGraphics extends Graphics2DProxy {
        private final Graphics2D delegate;
        private final int row;
        private final Rectangle bounds;
        SelectionGraphics(Graphics2D delegate, int row, Rectangle bounds) {
            super(delegate); this.delegate = delegate; this.row = row; this.bounds = bounds;
        }
        @Override public Graphics create() { return new SelectionGraphics((Graphics2D) super.create(), row, bounds); }
        @Override public Graphics create(int x, int y, int width, int height) {
            return new SelectionGraphics((Graphics2D) super.create(x, y, width, height), row, bounds);
        }
        @Override public void fillRect(int x, int y, int width, int height) {
            if (x == 0 && y == 0 && width == bounds.width && height == bounds.height
                    && getColor().equals(list.getSelectionBackground()))
                paintCellSelection(delegate, row, x, y, width, height);
            else super.fillRect(x, y, width, height);
        }
    }
}
