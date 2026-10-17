package dev.jasper.ui.swing;

import dev.jasper.ui.model.Nodes;
import java.awt.Graphics2D;
import java.awt.LayoutManager;
import javax.swing.JLabel;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;

/** The group point (spec 5.2): a Section over its Card, as a section label and a card, or one titled box. */
interface GroupFace extends Face {
    /** The section's layout of its title and body. */
    LayoutManager sectionLayout(Cards.SectionControl section);
    /** Paints the section behind its title and body (the classic titled box), or nothing. */
    void paintSection(Cards.SectionSurface surface, Graphics2D g);
    /** Paints a card's surface and dividers. */
    void paintCard(Cards.CardSurface card, Graphics2D g);
    /** Styles the section's title. */
    void styleTitle(JLabel title, KitContext context);
    /** A {@code kind} card's padding, {@code standard} being the default look's. */
    Border cardBorder(KitContext context, Nodes.CardKind kind, boolean page, EmptyBorder standard);
}
