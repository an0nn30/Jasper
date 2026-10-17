package dev.jasper.ui.swing;

import java.awt.Graphics2D;
import javax.swing.JLabel;

/** The table point (spec 5.2): a transparent header over plain rows, or the classic band, zebra rows and heading bands. */
interface TableFace extends HeaderFace {
    /** Paints the bands under the rows' selection and hover fills, across {@code width}. */
    void paintBands(Tables.TableControl table, Graphics2D g, int width);
    /** Whether a heading row after other rows opens with a hairline rule. */
    boolean headingRule();
    /** How far a title or cell after the first column sits from its column's start. */
    int cellInset(KitContext context);
    /** How far a data row's first cell is indented under a heading row. */
    int groupIndent(KitContext context);
    /** Styles a heading row's count after it refreshes. */
    void styleCount(JLabel count, KitContext context);
}
