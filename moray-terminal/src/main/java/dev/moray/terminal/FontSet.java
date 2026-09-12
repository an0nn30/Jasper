package dev.moray.terminal;

import java.awt.Font;
import java.awt.RenderingHints;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.font.LineMetrics;
import java.awt.font.TextAttribute;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The primary font plus fallbacks, chosen per code point, and the cell grid metrics.
 * Used on the Event Dispatch Thread only.
 */
public final class FontSet {
    private static final FontRenderContext FRC = new FontRenderContext(
        null, RenderingHints.VALUE_TEXT_ANTIALIAS_ON, RenderingHints.VALUE_FRACTIONALMETRICS_ON);

    /** [font index][style index]: style index = (bold ? 1 : 0) | (italic ? 2 : 0). */
    private final Font[][] fonts;
    private final boolean ligatures;
    private final Map<Integer, Integer> fontIndexByCodePoint = new HashMap<>();
    private final int cellWidth;
    private final int cellHeight;
    private final int ascent;

    public FontSet(String family, float size, List<String> fallbackFamilies, boolean ligatures) {
        this(family, size, fallbackFamilies, ligatures, 1f);
    }

    public FontSet(String family, float size, List<String> fallbackFamilies, boolean ligatures, float lineHeight) {
        this.ligatures = ligatures;
        List<String> families = new ArrayList<>();
        families.add(family);
        families.addAll(fallbackFamilies);
        fonts = new Font[families.size()][];
        for (int i = 0; i < families.size(); i++) {
            Font plain = create(families.get(i), size, ligatures);
            fonts[i] = new Font[] {
                plain, plain.deriveFont(Font.BOLD), plain.deriveFont(Font.ITALIC), plain.deriveFont(Font.BOLD | Font.ITALIC)};
        }
        Font primary = fonts[0][0];
        cellWidth = Math.max(1, Math.round(primary.createGlyphVector(FRC, "M").getGlyphMetrics(0).getAdvance()));
        LineMetrics metrics = primary.getLineMetrics("Mg", FRC);
        int naturalAscent = (int) Math.ceil(metrics.getAscent());
        int naturalHeight = Math.max(1, (int) Math.ceil(metrics.getAscent() + metrics.getDescent() + metrics.getLeading()));
        cellHeight = Math.max(naturalHeight, (int) Math.ceil(naturalHeight * lineHeight));
        ascent = naturalAscent + (cellHeight - naturalHeight) / 2;
    }

    public Font fontFor(int codePoint, boolean bold, boolean italic) {
        int index = fontIndexByCodePoint.computeIfAbsent(codePoint, this::firstFontThatCanDisplay);
        return fonts[index][(bold ? 1 : 0) | (italic ? 2 : 0)];
    }

    /** Shaped (ligatures on) or unshaped (ligatures off) glyphs for one run of text. */
    public GlyphVector layout(Font font, char[] text) {
        return ligatures
            ? font.layoutGlyphVector(FRC, text, 0, text.length, Font.LAYOUT_LEFT_TO_RIGHT)
            : font.createGlyphVector(FRC, text);
    }

    public String primaryFamily() {
        return fonts[0][0].getFamily();
    }

    public int cellWidth() {
        return cellWidth;
    }

    public int cellHeight() {
        return cellHeight;
    }

    public int ascent() {
        return ascent;
    }

    private int firstFontThatCanDisplay(int codePoint) {
        for (int i = 0; i < fonts.length; i++) {
            if (fonts[i][0].canDisplay(codePoint)) {
                return i;
            }
        }
        return 0;
    }

    private static Font create(String family, float size, boolean ligatures) {
        Map<TextAttribute, Object> attributes = new HashMap<>();
        attributes.put(TextAttribute.FAMILY, family);
        attributes.put(TextAttribute.SIZE, size);
        if (ligatures) {
            attributes.put(TextAttribute.LIGATURES, TextAttribute.LIGATURES_ON);
        }
        return new Font(attributes);
    }
}
