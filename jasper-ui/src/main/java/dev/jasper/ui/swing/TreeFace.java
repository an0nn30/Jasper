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
}
