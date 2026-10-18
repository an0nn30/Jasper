package dev.jasper.ui.swing;

import dev.jasper.ui.metrics.Typography;
import dev.jasper.ui.model.Nodes;
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
    /** Whether a heading row's count stands under the last column's title (the classic table); otherwise at the row's end. */
    boolean countUnderLastColumn();
    /**
     * Whether a table on a Settings page shows every row (up to {@code Tables.TableControl.PAGE_ROWS} data rows), the page
     * scrolling instead of the table; otherwise eight.
     */
    boolean everyRowOnPage();
    /** The type a {@code role} column's text cells take in place of the role's own, {@code settings} on a Settings page; null keeps it. */
    Typography.Role cellType(Nodes.TextRole role, boolean settings);
    /** Paints behind an item list's heading rows across {@code width}: nothing, or the classic heading bands (plan 9b Rulings 12). */
    void paintListHeadings(InteractiveRows.RowsControl rows, java.awt.Graphics2D g, int width);
    /** How far a manager column's table rows start below its header: the rail's top inset, or flush under the classic band. */
    int columnTop(KitContext context);
    /**
     * Whether a manager list column's text footer under a table is a band ({@code surface.zebra} under a {@code border.row}
     * rule, the header band's height); otherwise today's raised footer.
     */
    boolean columnFooterBand();
}
