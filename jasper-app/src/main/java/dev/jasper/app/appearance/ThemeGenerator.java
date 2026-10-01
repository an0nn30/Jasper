package dev.jasper.app.appearance;

import dev.jasper.terminal.config.Palette;
import java.awt.Color;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import static dev.jasper.app.appearance.ChromeKeys.contrast;
import static dev.jasper.app.appearance.ChromeKeys.mix;
import static dev.jasper.app.appearance.ChromeKeys.readable;
import static dev.jasper.app.appearance.Token.*;

/**
 * Fills a resolved theme document in: missing seeds, every token and syntax role, the terminal palette and the UI
 * keys. A value the theme sets is used as written; a derived text or stroke colour is moved toward the foreground
 * until it meets its contrast floor (4.5:1 text, links and status colours; 3:1 muted text, dividers and comments;
 * 2:1 disabled text). Pure: no Swing state.
 */
final class ThemeGenerator {
    private ThemeGenerator() {}

    /** @param resolved a document from {@link ThemeResolver}, so every required seed is present */
    static GeneratedTheme generate(ThemeDocument resolved) { return new Generation(resolved).result(); }

    private static final class Generation {
        private final ThemeDocument document;
        private final Color background, foreground, accent, cursor, selection;
        private final boolean dark;
        private final List<Color> ansi;
        private final Map<Token, Color> tokens = new EnumMap<>(Token.class);

        Generation(ThemeDocument document) {
            this.document = document;
            background = seed(Seed.BACKGROUND);
            foreground = seed(Seed.FOREGROUND);
            accent = seed(Seed.ACCENT);
            dark = document.dark().orElse(ChromeKeys.luminance(background) < .5);
            var colours = new ArrayList<Color>(16);
            for (int i = 0; i < 8; i++) {
                Color normal = document.ansi().get(i);
                if (normal == null) throw new IllegalArgumentException(document.origin() + ": [colors.normal] has no "
                    + ThemeDocument.ANSI_NAMES.get(i));
                colours.add(normal);
            }
            for (int i = 8; i < 16; i++)
                colours.add(document.ansi().getOrDefault(i, mix(dark ? Color.WHITE : Color.BLACK, colours.get(i - 8), .20)));
            ansi = List.copyOf(colours);
            cursor = document.seeds().getOrDefault(Seed.CURSOR, foreground);
            selection = document.seeds().getOrDefault(Seed.SELECTION, readable(mix(accent, background, .35), background, 1.5, accent));
        }

        private Color seed(Seed seed) {
            Color value = document.seeds().get(seed);
            if (value == null) throw new IllegalArgumentException(document.origin() + ": [colors] has no " + seed.key());
            return value;
        }

        GeneratedTheme result() {
            for (Token token : Token.values()) token(token);
            var syntax = new EnumMap<SyntaxRole, SyntaxStyle>(SyntaxRole.class);
            for (SyntaxRole role : SyntaxRole.values()) syntax.put(role, syntax(role));
            return new GeneratedTheme(document.name(), dark, tokens, syntax,
                new Palette(foreground, background, cursor, selection, ansi), uiKeys(), document.icons());
        }

        private Color token(Token token) {
            Color value = tokens.get(token);
            if (value != null) return value;
            value = document.tokens().containsKey(token) ? document.tokens().get(token) : derive(token);
            tokens.put(token, value);
            return value;
        }

        private Color derive(Token token) {
            return switch (token) {
                case SURFACE -> background;
                case SURFACE_RAISED -> mix(token(TEXT), token(SURFACE), .06);
                case SURFACE_SUNKEN -> mix(dark ? Color.BLACK : Color.WHITE, token(SURFACE), .12);
                case TITLE_BAR, STATUS_BAR, TAB_BAR -> mix(token(TEXT), token(SURFACE), .04);
                case SIDEBAR -> token(SURFACE_SUNKEN);
                case TEXT -> foreground;
                case TEXT_MUTED -> readable(mix(token(TEXT), token(SURFACE), .65), token(SURFACE), 3, token(TEXT));
                case TEXT_DISABLED -> readable(mix(token(TEXT), token(SURFACE), .45), token(SURFACE), 2, token(TEXT));
                case TEXT_ON_ACCENT -> blackOrWhite(token(BUTTON_DEFAULT));
                case LINK -> readable(token(ACCENT), token(SURFACE), 4.5, token(TEXT));
                case BORDER -> mix(token(TEXT), token(SURFACE), .16);
                case SEPARATOR -> token(BORDER);
                case DIVIDER -> readable(mix(token(TEXT), background, .40), background, 3, token(TEXT));
                case FOCUS_RING, TAB_UNDERLINE, BUTTON_DEFAULT -> token(ACCENT);
                case HOVER -> mix(token(TEXT), token(SURFACE), .08);
                case PRESSED -> mix(token(TEXT), token(SURFACE), .14);
                case SELECTION -> mix(token(ACCENT), token(SURFACE), .55);
                case SELECTION_TEXT -> textOn(token(SELECTION));
                case SELECTION_INACTIVE -> mix(token(SELECTION), token(SURFACE), .6);
                case TEXT_SELECTION, EDITOR_SELECTION -> selection;
                case TEXT_SELECTION_TEXT, TAB_ACTIVE_TEXT, BUTTON_TEXT -> token(TEXT);
                case TAB_ACTIVE -> mix(token(ACCENT), token(SURFACE), .18);
                case TAB_ACTIVE_BORDER -> mix(token(ACCENT), token(SURFACE), .45);
                case TAB_HOVER -> token(HOVER);
                case TAB_UNDERLINE_INACTIVE -> mix(token(TEXT), token(SURFACE), .45);
                case INPUT_BACKGROUND -> mix(token(TEXT), token(SURFACE), .03);
                case BUTTON_BACKGROUND -> mix(token(TEXT), token(SURFACE), .12);
                case BUTTON_BORDER -> mix(token(TEXT), token(SURFACE), .18);
                case SCROLLBAR_THUMB -> mix(token(TEXT), token(SURFACE), .22);
                case SCROLLBAR_TRACK -> mix(token(TEXT), token(SURFACE), .05);
                case ACCENT -> accent;
                case SUCCESS -> readable(ansi.get(2), token(STATUS_BAR), 4.5, token(TEXT));
                case WARNING -> readable(ansi.get(3), token(STATUS_BAR), 4.5, token(TEXT));
                case ERROR -> readable(ansi.get(1), token(STATUS_BAR), 4.5, token(TEXT));
                case ERROR_BACKGROUND -> mix(ansi.get(1), token(INPUT_BACKGROUND), .20);
                case EDITOR_BACKGROUND -> background;
                case EDITOR_FOREGROUND -> foreground;
                case EDITOR_CARET -> cursor;
                case EDITOR_CURRENT_LINE -> mix(token(EDITOR_FOREGROUND), token(EDITOR_BACKGROUND), .05);
                case EDITOR_GUTTER -> token(EDITOR_BACKGROUND);
                case EDITOR_LINE_NUMBERS -> token(TEXT_DISABLED);
            };
        }

        /** The text colour when it reads well on {@code fill}, else black or white, whichever contrasts more. */
        private Color textOn(Color fill) { return contrast(token(TEXT), fill) >= 4.5 ? token(TEXT) : blackOrWhite(fill); }

        private static Color blackOrWhite(Color fill) {
            return contrast(Color.WHITE, fill) >= contrast(Color.BLACK, fill) ? Color.WHITE : Color.BLACK;
        }

        private SyntaxStyle syntax(SyntaxRole role) {
            SyntaxStyle explicit = document.syntax().get(role);
            if (explicit != null) return explicit;
            Color editor = token(EDITOR_BACKGROUND), text = token(EDITOR_FOREGROUND);
            Color base = switch (role) {
                case KEYWORD -> ansi.get(5);
                case TYPE, REGEX -> ansi.get(6);
                case FUNCTION -> ansi.get(4);
                case VARIABLE -> text;
                case CONSTANT, NUMBER, ANNOTATION, ATTRIBUTE -> ansi.get(3);
                case STRING -> ansi.get(2);
                case COMMENT, DOC_COMMENT -> ansi.get(8);
                case OPERATOR, PUNCTUATION -> mix(text, editor, .75);
                case TAG, ERROR -> ansi.get(1);
            };
            boolean comment = role == SyntaxRole.COMMENT || role == SyntaxRole.DOC_COMMENT;
            return new SyntaxStyle(readable(base, editor, comment ? 3 : 4.5, text), false, comment);
        }

        /**
         * The template's FlatLaf section (skipped for a {@code ui_complete} theme), its {@code Jasper.*} section (only
         * explicit tokens for a complete theme), then the theme's own keys, which replace and follow the template's.
         */
        private Map<String, Object> uiKeys() {
            boolean complete = document.uiComplete().orElse(false);
            var keys = new LinkedHashMap<String, Object>();
            if (!complete) UiTemplate.FLATLAF.forEach((key, value) ->
                keys.put(key, value instanceof Token token ? UiTemplate.hex(token(token)) : value));
            UiTemplate.JASPER.forEach((key, token) -> {
                if (!complete || document.tokens().containsKey(token)) keys.put(key, UiTemplate.hex(token(token)));
            });
            document.ui().forEach((key, value) -> { keys.remove(key); keys.put(key, value); });
            return keys;
        }
    }
}
