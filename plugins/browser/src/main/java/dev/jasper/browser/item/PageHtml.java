package dev.jasper.browser.item;

import dev.jasper.sdk.ui.WorkspacePalette;
import java.awt.Color;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * What Jasper's own {@code jasper-about:} pages share (spec section 14): the workspace theme's colours as CSS custom
 * properties, text escaped for HTML and cut to a length, and the random tokens by which an item knows its own pages.
 */
public final class PageHtml {
    private static final Pattern TOKENS = Pattern.compile("[0-9a-f]{32}");
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String ELLIPSIS = "…";

    private PageHtml() { }

    /**
     * The theme's colours as a CSS rule that sets them on the document: {@code --background}, {@code --card} (a raised
     * surface), {@code --text}, {@code --muted}, {@code --border}, {@code --accent}, {@code --warning} and
     * {@code --error}.
     *
     * @param palette the workspace palette
     * @return the rule, one property to a line
     */
    public static String colours(WorkspacePalette palette) {
        return """
            :root {
              --background: %s;
              --card: %s;
              --text: %s;
              --muted: %s;
              --border: %s;
              --accent: %s;
              --warning: %s;
              --error: %s;
            }""".formatted(css(palette.background()), css(palette.surfaceRaised()), css(palette.text()), css(palette.textMuted()),
            css(palette.border()), css(palette.accent()), css(palette.warning()), css(palette.error()));
    }

    /**
     * {@code text} as HTML shows it literally, in an element's text or in a quoted attribute's value.
     *
     * @param text any text
     * @return the text with {@code & < > " '} written as character references
     */
    public static String escape(String text) {
        var out = new StringBuilder(text.length() + 16);
        for (int at = 0; at < text.length(); at++) {
            char c = text.charAt(at);
            switch (c) {
                case '&' -> out.append("&amp;");
                case '<' -> out.append("&lt;");
                case '>' -> out.append("&gt;");
                case '"' -> out.append("&quot;");
                case '\'' -> out.append("&#39;");
                default -> out.append(c);
            }
        }
        return out.toString();
    }

    /**
     * A new random token, 128 bits as 32 lowercase hexadecimal digits: what names one of an item's pages to it, which no
     * page can guess.
     */
    static String newToken() {
        var bytes = new byte[16];
        RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    /** Whether {@code text} is a token as {@link #newToken} makes them. */
    static boolean isToken(String text) {
        return TOKENS.matcher(text).matches();
    }

    /** {@code text} cut to {@code length} characters, the last an ellipsis, when it is longer. */
    static String cut(String text, int length) {
        if (text.length() <= length) return text;
        int end = length - 1;
        if (Character.isHighSurrogate(text.charAt(end - 1))) end--;
        return text.substring(0, end) + ELLIPSIS;
    }

    /** {@code color} as CSS, its alpha kept. */
    static String css(Color color) {
        if (color.getAlpha() == 255) return String.format(Locale.ROOT, "#%02x%02x%02x", color.getRed(), color.getGreen(), color.getBlue());
        return String.format(Locale.ROOT, "rgba(%d, %d, %d, %.3f)", color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha() / 255.0);
    }
}
