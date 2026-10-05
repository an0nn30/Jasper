package dev.jasper.ui.theme;

import java.awt.Color;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import static dev.jasper.ui.theme.ColorMath.contrast;
import static dev.jasper.ui.theme.ColorMath.mix;
import static dev.jasper.ui.theme.Token.*;

/**
 * Fills a resolved theme document in: missing seeds, every token and syntax role, the terminal palette and the UI
 * keys. A value the theme sets is used as written; a derived text or stroke colour is moved toward the foreground
 * until it meets its contrast floor, falling back toward black or white when needed (4.5:1 text, links and status
 * colours; 3:1 muted text, dividers and comments;
 * 2:1 disabled text). Pure: no Swing state.
 *
 * <p>Each token the theme does not set is derived from the seeds and from other tokens (a token another rule names is
 * itself explicit or derived first). Below, {@code mix(a, b, t)} is {@code t} of {@code a} laid over {@code b}, and
 * {@code readable(c, bg, r)} is {@code c} moved toward {@code text} until it reaches contrast {@code r} on {@code bg}.
 * <ul>
 *   <li>{@code surface}, {@code editor_background}: the background seed
 *   <li>{@code text}, {@code editor_foreground}: the foreground seed, adjusted to 4.5:1 against their surface
 *   <li>{@code accent}: the accent seed; {@code focus_ring}, {@code tab_underline}, {@code button_default}: {@code accent}
 *   <li>{@code editor_caret}: the cursor seed (the foreground when unset)
 *   <li>{@code text_selection}, {@code editor_selection}: the selection seed (when unset,
 *       {@code mix(accent, background, 0.35)} moved toward the accent to at least 1.5:1 on the background)
 *   <li>{@code title_bar}, {@code status_bar}, {@code tab_bar}: {@code mix(text, surface, 0.04)}
 *   <li>{@code surface_raised}: {@code mix(text, surface, 0.06)}
 *   <li>{@code surface_sunken}: {@code mix(black, surface, 0.12)} on a dark theme, {@code mix(white, surface, 0.12)} on
 *       a light one
 *   <li>{@code sidebar}: {@code surface_sunken}
 *   <li>{@code text_muted}: {@code readable(mix(text, surface, 0.65), surface, 3)}
 *   <li>{@code text_disabled}: {@code readable(mix(text, surface, 0.45), surface, 2)}; {@code editor_line_numbers}:
 *       {@code text_disabled}
 *   <li>{@code text_on_accent}: black or white, whichever contrasts more with {@code button_default}
 *   <li>{@code link}: {@code readable(accent, surface, 4.5)}
 *   <li>{@code border}: {@code mix(text, surface, 0.16)}; {@code separator}: {@code border}
 *   <li>{@code divider}: {@code readable(mix(text, background, 0.40), background, 3)}
 *   <li>{@code hover}: {@code mix(text, surface, 0.08)}; {@code tab_hover}: {@code hover}
 *   <li>{@code pressed}: {@code mix(text, surface, 0.14)}
 *   <li>{@code selection}: {@code mix(accent, surface, 0.55)}
 *   <li>{@code selection_text}: {@code text} when it reaches 4.5:1 on {@code selection}, else black or white
 *   <li>{@code selection_inactive}: {@code mix(selection, surface, 0.6)}
 *   <li>{@code text_selection_text}, {@code tab_active_text}, {@code button_text}: {@code text} when it reaches
 *       4.5:1 on the corresponding fill, else black or white
 *   <li>{@code tab_active}: {@code mix(accent, surface, 0.18)}
 *   <li>{@code tab_active_border}: {@code mix(accent, surface, 0.45)}
 *   <li>{@code tab_underline_inactive}: {@code mix(text, surface, 0.45)}
 *   <li>{@code input_background}: {@code mix(text, surface, 0.03)}
 *   <li>{@code button_background}: {@code mix(text, surface, 0.12)}
 *   <li>{@code button_border}: {@code mix(text, surface, 0.18)}
 *   <li>{@code scrollbar_thumb}: {@code mix(text, surface, 0.22)}
 *   <li>{@code scrollbar_track}: {@code mix(text, surface, 0.05)}
 *   <li>{@code success}, {@code warning}, {@code error}: ANSI green, yellow and red, each
 *       {@code readable(colour, status_bar, 4.5)}
 *   <li>{@code error_background}: {@code mix(ANSI red, input_background, 0.20)}
 *   <li>{@code editor_current_line}: {@code mix(editor_foreground, editor_background, 0.05)}
 *   <li>{@code editor_gutter}: {@code editor_background}
 * </ul>
 */
public final class ThemeGenerator {
    private ThemeGenerator() {}

    /**
     * Generates native theme values without reading or modifying Swing state, including for palette importers.
     * @param resolved a resolved document with background, foreground, accent and all eight normal ANSI colors
     * @return the complete generated tokens, syntax, terminal palette and UI data
     */
    public static GeneratedTheme generate(ThemeDocument resolved) { return generate(resolved, RuntimePlatform.current()); }

    /** Generate deterministically for an explicit runtime platform, without consulting Swing. */
    public static GeneratedTheme generate(ThemeDocument resolved, RuntimePlatform platform) { return new Generation(resolved, platform).result(); }

    private static final class Generation {
        private final ThemeDocument document;
        private final RuntimePlatform platform;
        private final Color background, foreground, accent, cursor, selection;
        private final boolean dark;
        private final List<Color> ansi;
        private final Map<Token, Color> tokens = new EnumMap<>(Token.class);

        Generation(ThemeDocument document, RuntimePlatform platform) {
            this.platform = java.util.Objects.requireNonNull(platform);
            this.document = document;
            background = seed(Seed.BACKGROUND);
            foreground = seed(Seed.FOREGROUND);
            accent = seed(Seed.ACCENT);
            dark = document.dark().orElse(ColorMath.luminance(background) < .5);
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
            // Resolve authored expressions against one complete fallback snapshot, then rebuild
            // dependent values from the final authored parents before deriving syntax.
            var authored = new AuthoredUiColors(document, platform, uiKeys()).tokens();
            tokens.clear();
            tokens.putAll(authored);
            for (Token token : Token.values()) token(token);
            var syntax = new EnumMap<SyntaxRole, SyntaxStyle>(SyntaxRole.class);
            for (SyntaxRole role : SyntaxRole.values()) syntax.put(role, syntax(role));
            return new GeneratedTheme(document.name(), dark, document.metadata().getOrDefault("author", "Jasper"), tokens, syntax,
                new TerminalColors(background, foreground, cursor, selection, ansi), uiKeys(), document.icons(), document.workspace().levels(),
                semanticPalette(authored));
        }

        private SemanticPalette semanticPalette(Map<Token, Color> authored) {
            // Tokens already include these authored values and their rebuilt dependents. Reusing
            // their provenance avoids evaluating references against a second, different fallback map.
            var legacy = new EnumMap<Token,Color>(Token.class); legacy.putAll(tokens);
            var overrides = new EnumMap<SemanticColor,Color>(SemanticColor.class);
            // Focus is a dependent default only when no legacy focus color was authored.
            if (authored.containsKey(FOCUS_RING) || document.tokens().containsKey(FOCUS_RING))
                overrides.put(SemanticColor.BORDER_FOCUS, legacy.get(FOCUS_RING));
            overrides.putAll(document.semanticOverrides());
            return SemanticDefaults.resolve(overrides, legacy, new TerminalColors(background, foreground, cursor, selection, ansi));
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
                case TEXT -> readable(foreground, token(SURFACE), 4.5, blackOrWhite(token(SURFACE)));
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
                case TEXT_SELECTION_TEXT -> textOn(token(TEXT_SELECTION));
                case TAB_ACTIVE_TEXT -> textOn(token(TAB_ACTIVE));
                case BUTTON_TEXT -> textOn(token(BUTTON_BACKGROUND));
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
                case EDITOR_FOREGROUND -> readable(foreground, token(EDITOR_BACKGROUND), 4.5, blackOrWhite(token(EDITOR_BACKGROUND)));
                case EDITOR_CARET -> cursor;
                case EDITOR_CURRENT_LINE -> mix(token(EDITOR_FOREGROUND), token(EDITOR_BACKGROUND), .05);
                case EDITOR_GUTTER -> token(EDITOR_BACKGROUND);
                case EDITOR_LINE_NUMBERS -> token(TEXT_DISABLED);
            };
        }

        /** The text colour when it reads well on {@code fill}, else black or white, whichever contrasts more. */
        private Color textOn(Color fill) { return contrast(token(TEXT), fill) >= 4.5 ? token(TEXT) : blackOrWhite(fill); }

        /** Prefer the theme's foreground, but fall back when it cannot meet the floor on this fill. */
        private static Color readable(Color color, Color fill, double minimum, Color toward) {
            Color adjusted = ColorMath.readable(color, fill, minimum, toward);
            return contrast(adjusted, fill) >= minimum ? adjusted
                : ColorMath.readable(color, fill, minimum, blackOrWhite(fill));
        }

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
            document.ui().forEach((key, value) -> {
                if (UiColorKeys.supports(key, value)) { keys.remove(key); keys.put(key, value); }
            });
            return keys;
        }
    }
}
