package dev.jasper.ui.themeimport;

import dev.jasper.ui.theme.Token;
import dev.jasper.ui.theme.UiColorKeys;

import java.awt.Color;
import java.util.EnumMap;
import java.util.Map;

/** Pure inverse of effective UI colors; absent keys retain their generated value. */
final class WorkspaceTokenKeys {
    static final Map<Token, String> KEYS = dev.jasper.ui.theme.UiTokenKeys.KEYS;
    private WorkspaceTokenKeys() {}

    /** Inverse authored colors only; platform-specific values remain in UI for runtime materialization. */
    public static Map<Token, Color> explicitTokens(Map<String, Object> resolvedUi) {
        var result = new EnumMap<Token, Color>(Token.class);
        for (Token token : Token.values()) {
            // In a complete theme, an explicit token also emits Jasper chrome aliases. Do not invent those
            // from another component's color: ChromeKeys may derive them differently (including contrast).
            // Authored UI remains intact and WorkspaceValues still resolves these tokens from effective defaults.
            if (UiColorKeys.chromeAliases().entrySet().stream().anyMatch(entry -> entry.getValue() == token && !resolvedUi.containsKey(entry.getKey())))
                continue;
            var keys = new java.util.LinkedHashSet<String>();
            keys.add(KEYS.get(token));
            // Template keys sharing a token are not inverse aliases: a tooltip is not a popup menu,
            // and a progress fill is not Component.accentColor. Only equivalent FlatLaf inputs qualify.
            switch (token) {
                case BUTTON_BACKGROUND -> keys.add("Button.startBackground");
                case BUTTON_DEFAULT -> keys.add("Button.default.startBackground");
                case SCROLLBAR_TRACK -> keys.add("ScrollBar.trackColor");
                case SCROLLBAR_THUMB -> keys.add("ScrollBar.thumbColor");
                case SEPARATOR -> keys.add("Separator.separatorColor");
                case LINK -> keys.add("Link.activeForeground");
                default -> { }
            }
            String start = switch (token) {
                case BUTTON_BACKGROUND -> "Button.startBackground";
                case BUTTON_DEFAULT -> "Button.default.startBackground";
                case BUTTON_BORDER -> "Button.startBorderColor";
                default -> null;
            };
            boolean gradient = start != null && resolvedUi.containsKey(start)
                && resolvedUi.containsKey(start.replace(".start", ".end"));
            if (gradient) { keys.clear(); keys.add(start); }
            // FlatLaf maps legacy aliases to the same effective key in authored insertion order.
            // Button start/end pairs have their separate post-JSON precedence above.
            Object supplied = null;
            for (var entry : resolvedUi.entrySet()) {
                String source = entry.getKey();
                if (keys.contains(source) || (source.startsWith("*.")
                        && !KEYS.get(token).endsWith(".hoverBackground") && !KEYS.get(token).endsWith(".pressedBackground")
                        && keys.stream().anyMatch(key -> key.endsWith(source.substring(1)))))
                    supplied = entry.getValue();
            }
            // Explicit native tokens outrank effective defaults, so OS-dependent input must remain only in UI.
            if (!(supplied instanceof Map<?, ?>)) {
                Color color = FlatLafThemeData.color(supplied);
                if (color != null) result.put(token, color);
            }
        }
        return Map.copyOf(result);
    }

    /** Omits tokens whose effective color FlatLaf adjusts after applying the authored JSON. No LAF is installed. */
    public static Map<Token, Color> explicitTokens(Map<String, Object> ui, String name, String author, boolean dark) {
        var result = new EnumMap<Token, Color>(Token.class); result.putAll(explicitTokens(ui));
        String normalizedName = name.replace(' ', '_'), normalizedAuthor = author.replace(' ', '_');
        var prefixes = java.util.List.of("{*}", dark ? "{*-dark}" : "{*-light}", "{author-" + normalizedAuthor + "}",
            "{" + normalizedName + "}", "{" + normalizedName + "---" + normalizedAuthor + "}");
        for (String raw : PostJson.KEYS) {
            String key = raw;
            if ((!dark && key.startsWith("[dark]")) || (dark && key.startsWith("[light]"))) continue;
            while (key.startsWith("[") && key.contains("]")) key = key.substring(key.indexOf(']') + 1);
            for (String prefix : prefixes) if (key.startsWith(prefix)) {
                String adjusted = key.substring(prefix.length());
                if ((!dark && adjusted.startsWith("[dark]")) || (dark && adjusted.startsWith("[light]"))) break;
                while (adjusted.startsWith("[") && adjusted.contains("]")) adjusted = adjusted.substring(adjusted.indexOf(']') + 1);
                String affected = adjusted;
                result.keySet().removeIf(token -> affected.equals(KEYS.get(token))
                    || (affected.startsWith("*.") && KEYS.get(token).endsWith(affected.substring(1))));
                break;
            }
        }
        return Map.copyOf(result);
    }

    private static final class PostJson {
        static final java.util.Set<String> KEYS = read();
        private static java.util.Set<String> read() {
            // Read the pinned dependency's data instead of reproducing its author/theme-specific color formulas.
            try (var input = com.formdev.flatlaf.IntelliJTheme.class.getResourceAsStream("IntelliJTheme$ThemeLaf.properties")) {
                if (input == null) throw new IllegalStateException("Missing FlatLaf IntelliJ theme defaults");
                var properties = new java.util.Properties(); properties.load(input);
                return java.util.Set.copyOf(properties.stringPropertyNames());
            } catch (java.io.IOException failure) { throw new java.io.UncheckedIOException(failure); }
        }
    }
}
