package dev.jasper.browser.preview;

import dev.jasper.browser.api.PreviewTheme;
import dev.jasper.browser.json.StrictJson;
import dev.jasper.sdk.Variant;
import dev.jasper.sdk.ui.SyntaxRole;
import dev.jasper.sdk.ui.SyntaxStyle;
import dev.jasper.sdk.ui.TerminalTypography;
import dev.jasper.sdk.ui.WorkspacePalette;
import java.awt.Color;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * A preview's theme as its page reads it: the CSS custom properties {@code preview.css} draws with, which
 * {@code preview.js} sets on the document's root, and Mermaid's theme variables. Colours are {@code #rrggbb}; numbers are
 * written as Java writes them, which no locale changes, with no exponent.
 */
final class PreviewCss {
    private PreviewCss() { }

    /**
     * The custom properties of {@code theme}:
     *
     * <ul>
     *   <li>the colours {@code --bg}, {@code --fg}, {@code --muted}, {@code --border}, {@code --raised},
     *   {@code --sunken}, {@code --accent}, {@code --selection}, {@code --selection-fg}, {@code --success},
     *   {@code --warning} and {@code --error}, from the workspace palette;</li>
     *   <li>the fonts: {@code --text-font} and {@code --code-font}, CSS font lists ({@link #fonts}): the text's the
     *   theme's text family, then {@code system-ui} and {@code sans-serif}, and the code's the typography's family and
     *   fallbacks, then {@code monospace}; {@code --font-size}, the typography's size in {@code px}, Java's points being
     *   its logical pixels; {@code --line-height}; and {@code --code-ligatures}, {@code normal} or {@code none};</li>
     *   <li>for each {@link SyntaxRole}, {@code --tok-<role>}, {@code --tok-<role>-weight} (700 or 400) and
     *   {@code --tok-<role>-style} ({@code italic} or {@code normal}), the role's name in lower case with hyphens, as
     *   the Editor's WEB dialect names its classes;</li>
     *   <li>the alerts' colours: {@code --alert-note} the accent, {@code --alert-tip} success, {@code --alert-important}
     *   the syntax palette's {@code KEYWORD}, the workspace palette having no purple, {@code --alert-warning} warning and
     *   {@code --alert-caution} error.</li>
     * </ul>
     *
     * @param theme the look
     * @return each property's name and value, in that order
     */
    static Map<String, String> variables(PreviewTheme theme) {
        WorkspacePalette palette = theme.palette();
        TerminalTypography typography = theme.typography();
        var variables = new LinkedHashMap<String, String>();
        variables.put("--bg", hex(palette.background()));
        variables.put("--fg", hex(palette.text()));
        variables.put("--muted", hex(palette.textMuted()));
        variables.put("--border", hex(palette.border()));
        variables.put("--raised", hex(palette.surfaceRaised()));
        variables.put("--sunken", hex(palette.surfaceSunken()));
        variables.put("--accent", hex(palette.accent()));
        variables.put("--selection", hex(palette.selection()));
        variables.put("--selection-fg", hex(palette.selectionText()));
        variables.put("--success", hex(palette.success()));
        variables.put("--warning", hex(palette.warning()));
        variables.put("--error", hex(palette.error()));
        variables.put("--text-font", textFont(theme));
        variables.put("--code-font", codeFont(typography));
        variables.put("--font-size", pixels(typography.size()));
        variables.put("--line-height", plain(typography.lineHeight()));
        variables.put("--code-ligatures", typography.ligatures() ? "normal" : "none");
        for (SyntaxRole role : SyntaxRole.values()) {
            SyntaxStyle style = theme.syntax().style(role);
            String name = "--tok-" + role.name().toLowerCase(Locale.ROOT).replace('_', '-');
            variables.put(name, hex(style.color()));
            variables.put(name + "-weight", style.bold() ? "700" : "400");
            variables.put(name + "-style", style.italic() ? "italic" : "normal");
        }
        variables.put("--alert-note", hex(palette.accent()));
        variables.put("--alert-tip", hex(palette.success()));
        variables.put("--alert-important", hex(theme.syntax().style(SyntaxRole.KEYWORD).color()));
        variables.put("--alert-warning", hex(palette.warning()));
        variables.put("--alert-caution", hex(palette.error()));
        return variables;
    }

    /**
     * Mermaid's {@code themeVariables} for {@code theme}, as one JSON object: {@code darkMode}; {@code background}, the
     * workspace background; {@code primaryColor}, the raised surface; {@code primaryTextColor}, the text;
     * {@code primaryBorderColor}, the border; {@code lineColor}, the muted text; {@code secondaryColor}, the sunken
     * surface; {@code tertiaryColor}, the background; {@code noteBkgColor}, the sunken surface; {@code noteTextColor},
     * the text; {@code fontFamily}, the text font; and {@code fontSize}, the typography's size in {@code px}.
     *
     * @param theme the look
     * @return the JSON text
     */
    static String mermaid(PreviewTheme theme) {
        WorkspacePalette palette = theme.palette();
        var variables = new LinkedHashMap<String, Object>();
        variables.put("darkMode", theme.variant() == Variant.DARK);
        variables.put("background", hex(palette.background()));
        variables.put("primaryColor", hex(palette.surfaceRaised()));
        variables.put("primaryTextColor", hex(palette.text()));
        variables.put("primaryBorderColor", hex(palette.border()));
        variables.put("lineColor", hex(palette.textMuted()));
        variables.put("secondaryColor", hex(palette.surfaceSunken()));
        variables.put("tertiaryColor", hex(palette.background()));
        variables.put("noteBkgColor", hex(palette.surfaceSunken()));
        variables.put("noteTextColor", hex(palette.text()));
        variables.put("fontFamily", textFont(theme));
        variables.put("fontSize", pixels(theme.typography().size()));
        return json(variables);
    }

    /**
     * A flat JSON object of these members: each a string or a boolean, the strings written with {@link StrictJson#quote}.
     *
     * @param members the names and values, in order
     * @return the JSON text
     */
    static String json(Map<String, ?> members) {
        return members.entrySet().stream()
            .map(member -> StrictJson.quote(member.getKey()) + ":" + (member.getValue() instanceof Boolean flag ? flag.toString()
                : StrictJson.quote(String.valueOf(member.getValue()))))
            .collect(Collectors.joining(",", "{", "}"));
    }

    private static String textFont(PreviewTheme theme) {
        return fonts(List.of(theme.textFamily()), "system-ui", "sans-serif");
    }

    private static String codeFont(TerminalTypography typography) {
        List<String> families = new ArrayList<>();
        families.add(typography.family());
        families.addAll(typography.fallback());
        return fonts(families, "monospace");
    }

    /**
     * A CSS font list: each of {@code families} as a quoted name, or as the CSS generic it stands for when Chromium
     * cannot find it by name (plan 9's N17), then {@code generics}. A Java logical family stands for its generic:
     * {@code Dialog} and {@code SansSerif} for {@code system-ui}, {@code Serif} for {@code serif}, and
     * {@code DialogInput} and {@code Monospaced} for {@code monospace}, in any case. A hidden system family, whose name
     * starts with a dot (as the Mac's interface font, {@code .AppleSystemUIFont}), stands for the list's first generic.
     * A generic is not written twice in a row.
     */
    private static String fonts(List<String> families, String... generics) {
        var names = new ArrayList<String>();
        for (String family : families) {
            String generic = family.startsWith(".") ? generics[0] : switch (family.toLowerCase(Locale.ROOT)) {
                case "dialog", "sansserif" -> "system-ui";
                case "serif" -> "serif";
                case "dialoginput", "monospaced" -> "monospace";
                default -> null;
            };
            add(names, generic == null ? string(family) : generic);
        }
        for (String generic : generics) add(names, generic);
        return String.join(", ", names);
    }

    /** Adds {@code name} to the font list, unless it is the name before it. */
    private static void add(List<String> names, String name) {
        if (names.isEmpty() || !names.getLast().equals(name)) names.add(name);
    }

    /** {@code text} as a CSS string: in double quotes, with quotes, backslashes and control characters escaped. */
    private static String string(String text) {
        var out = new StringBuilder(text.length() + 2).append('"');
        for (int at = 0; at < text.length(); at++) {
            char c = text.charAt(at);
            if (c == '"' || c == '\\') out.append('\\').append(c);
            else if (c < 0x20 || c == 0x7F) out.append('\\').append(Integer.toHexString(c)).append(' ');
            else out.append(c);
        }
        return out.append('"').toString();
    }

    private static String pixels(double size) {
        return plain(size) + "px";
    }

    /** {@code value} as {@code Double.toString} writes it, which no locale changes, and with no exponent. */
    private static String plain(double value) {
        return BigDecimal.valueOf(value).toPlainString();
    }

    private static String hex(Color color) {
        return String.format(Locale.ROOT, "#%02x%02x%02x", color.getRed(), color.getGreen(), color.getBlue());
    }
}
