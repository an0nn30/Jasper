package dev.jasper.ui.swing;

import dev.jasper.ui.model.Option;
import java.awt.*;

/**
 * The exclusive and tabs points (spec 5.2, plan 4b Rulings 3): an EXCLUSIVE or TABS choice's track and options, and a
 * PREVIEWS choice's tiles (asked of the exclusive point's face only).
 */
interface ChoiceFace extends Face {
    /** The layout of a choice's options inside its track. */
    LayoutManager layout(KitContext context);
    /** The track's padding round its options. */
    int trackPadding(KitContext context, boolean form);
    /** Paints the track behind its options. */
    void paintTrack(Choices.SegmentTrack track, Graphics2D g);
    /** Styles an option for {@code model}: its font, colours, glyph and padding. */
    void style(Choices.SegmentButton option, Option model, boolean settings, boolean identitySection, int track, int count);
    /** An option's size. */
    Dimension optionSize(Choices.SegmentButton option);
    /** Paints an option; {@code label} paints the button's own glyph and label, as the default look does. */
    void paintOption(Choices.SegmentButton option, Graphics graphics, Runnable label);
    /** The selected option's label colour. */
    Color selectedText(KitContext context);
    /** The layout of a PREVIEWS choice's tiles. */
    LayoutManager previewLayout(KitContext context, int count);
    /** A preview tile's size. */
    Dimension previewSize(PreviewChoices.Tile tile);
    /** Paints a preview tile. */
    void paintPreview(PreviewChoices.Tile tile, Graphics2D g);
}
