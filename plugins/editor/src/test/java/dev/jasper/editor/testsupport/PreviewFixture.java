package dev.jasper.editor.testsupport;

import java.awt.Dimension;
import javax.swing.JScrollPane;

/** Real headless viewport layout, with a fixed visible extent and the view's actual preferred height. */
public final class PreviewFixture {
    private PreviewFixture() { }

    public static void layout(JScrollPane scroll, int width, int height) {
        var viewport = scroll.getViewport();
        var view = viewport.getView();
        viewport.setExtentSize(new Dimension(width, height));
        view.setSize(width, 10000);
        view.setSize(width, Math.max(height, view.getPreferredSize().height));
        viewport.setViewSize(view.getSize());
    }
}
