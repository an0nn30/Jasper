package dev.jasper.ui.swing;

import dev.jasper.ui.model.Nodes;

/** The tree point (spec 5.2): today's tree, or ▶/▼ disclosure, a deeper indent and medium category rows. */
interface TreeFace extends HeaderFace {
    /** A row's indent at {@code depth}. */
    int indent(KitContext context, int depth);
    /** The presentation of a branch row's expand button. */
    Nodes.ButtonPresentation disclosure();
    /** Prepares a branch row's expand button after it refreshes. */
    void styleDisclosure(Buttons.KitButton button, KitContext context, boolean expanded);
    /** A row title's role: {@code declared}, or the category role for a branch. */
    Nodes.TextRole title(boolean leaf, Nodes.TextRole declared);
    /** Paints the keycap detail of row {@code index} across {@code width}: today's card, or the classic band below the row. */
    void paintDetail(Trees.TreeControl tree, java.awt.Graphics2D g, int index, int width);
    /** Whether the edited row keeps its selection fill above its detail (the classic band); today's card covers it. */
    boolean fillsDetailRow();
    /** Whether a selected row's keycaps are opaque light caps (the classic Keymap's), not today's passive translucent ones. */
    boolean boxesSelectedKeycaps();
}
