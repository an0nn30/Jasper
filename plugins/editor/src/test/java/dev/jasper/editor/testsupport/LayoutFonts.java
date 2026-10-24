package dev.jasper.editor.testsupport;

import java.awt.Font;
import java.awt.GraphicsEnvironment;

/** Deterministic test-only physical fonts; licenses and provenance accompany the resources. */
public final class LayoutFonts {
    private LayoutFonts() { }
    public static void register() throws Exception {
        for(String file:new String[]{"ibm-plex-sans-regular.ttf","CousineNerdFontMono-Regular.ttf"}) {
            try(var in=LayoutFonts.class.getResourceAsStream("/fonts/"+file)) {
                GraphicsEnvironment.getLocalGraphicsEnvironment().registerFont(Font.createFont(Font.TRUETYPE_FONT,in));
            }
        }
    }
}
