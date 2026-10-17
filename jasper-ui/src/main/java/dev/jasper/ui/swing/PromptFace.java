package dev.jasper.ui.swing;

import java.awt.Graphics2D;
import java.awt.LayoutManager;
import java.util.List;

/** The prompt point (spec 5.2, amended): a PROMPT form's header, as the tile over the title, or the plain classic prompt. */
interface PromptFace extends Face {
    /** The PROMPT_HEADER stack's layout. */
    LayoutManager headerLayout(Stacks.StackControl header);
    /** Paints the tile behind the prompt's icon. */
    void paintTile(Stacks.Tile tile, Graphics2D g);
    /** Prepares the header's children after they refresh (the classic face hides the title and inks the glyph). */
    void children(Stacks.StackControl header, List<ControlRegistry.Control> controls);
}
