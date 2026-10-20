package dev.jasper.editor.textview;

import dev.jasper.sdk.ui.TerminalTypography;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.awt.font.FontRenderContext;
import java.awt.font.TextAttribute;
import java.awt.font.TextLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Resolved ordered fonts and the uniform line box shared by the editor, gutter and viewport. */
public final class EditorFonts {
    private final TerminalTypography typography;
    private final FontRenderContext context;
    private final List<Font> fonts;
    private final Font[][] styledFonts;
    private final float naturalHeight, ascent;
    private final int height;

    /** Resolves installed families without allowing a missing family to masquerade as Dialog. */
    public EditorFonts(TerminalTypography typography, FontRenderContext context) {
        this.typography = typography;
        this.context = context;
        Set<String> available = java.util.Arrays.stream(GraphicsEnvironment.getLocalGraphicsEnvironment()
            .getAvailableFontFamilyNames(Locale.ROOT)).map(s -> s.toLowerCase(Locale.ROOT)).collect(Collectors.toSet());
        var names = new ArrayList<String>();
        names.add(typography.family()); names.addAll(typography.fallback()); names.add(Font.DIALOG);
        var resolved = new ArrayList<Font>();
        for (String name : names) if (available.contains(name.toLowerCase(Locale.ROOT))) {
            Font font = new Font(name, Font.PLAIN, 1).deriveFont((float) typography.size());
            if (!resolved.contains(font)) resolved.add(font);
        }
        if (resolved.isEmpty()) resolved.add(new Font(Font.DIALOG, Font.PLAIN, 1).deriveFont((float)typography.size()));
        fonts = List.copyOf(resolved);
        styledFonts=new Font[fonts.size()][4];
        for(int i=0;i<fonts.size();i++) for(int style=0;style<4;style++)
            styledFonts[i][style]=fonts.get(i).deriveFont(style).deriveFont(Map.of(
                TextAttribute.LIGATURES,typography.ligatures()?1:0,TextAttribute.KERNING,typography.ligatures()?1:0));
        float a = 0, d = 0;
        for (Font font : fonts) for (int style = 0; style < 4; style++) {
            var metrics = font.deriveFont(style).getLineMetrics("Mg", context);
            a = Math.max(a, metrics.getAscent()); d = Math.max(d, metrics.getDescent() + metrics.getLeading());
        }
        ascent = a; naturalHeight = a + d;
        height = Math.max(1, (int)Math.ceil(naturalHeight * typography.lineHeight()));
    }
    /** The first resolved family at the fractional configured size. */
    public Font primary() { return fonts.getFirst(); }
    /** Rendering context used for both measuring and painting. */
    public FontRenderContext context() { return context; }
    /** Natural maximum ascent, descent and leading across configured families/styles. */
    public float naturalHeight() { return naturalHeight; }
    /** Uniform row advance in logical pixels. */
    public int height() { return height; }
    /** Centered baseline; permitted to extend beyond a compressed row. */
    public float baseline() { return ascent + (height - naturalHeight) / 2f; }
    /** Advance used by tab stops. */
    public float spaceAdvance() { return new TextLayout(" ", primary(), context).getAdvance(); }
    /** Whether contextual ligatures are enabled. */
    boolean ligatures() { return typography.ligatures(); }
    /** First configured font capable of the complete grapheme, with system fallback last. */
    public Font forGrapheme(String grapheme, int style) {
        for(int i=0;i<fonts.size();i++) if(fonts.get(i).canDisplayUpTo(grapheme)<0) return styledFonts[i][style];
        return styledFonts[fonts.size()-1][style];
    }
    Font forCodePoint(int codePoint, int style) {
        for(int i=0;i<fonts.size();i++) if(fonts.get(i).canDisplay(codePoint)) return styledFonts[i][style];
        return styledFonts[fonts.size()-1][style];
    }
}
