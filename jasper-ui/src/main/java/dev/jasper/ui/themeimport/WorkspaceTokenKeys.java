package dev.jasper.ui.themeimport;

import dev.jasper.ui.theme.Token;
import dev.jasper.ui.theme.UiColorKeys;

import java.awt.Color;
import java.util.EnumMap;
import java.util.Map;

/** Pure inverse of effective UI colors; absent keys retain their generated value. */
final class WorkspaceTokenKeys {
    static final Map<Token, String> KEYS = Map.ofEntries(
        Map.entry(Token.SURFACE, "Panel.background"),
        Map.entry(Token.TEXT, "Label.foreground"),
        Map.entry(Token.TEXT_MUTED, "Jasper.mutedForeground"),
        Map.entry(Token.TEXT_DISABLED, "Label.disabledForeground"),
        Map.entry(Token.TITLE_BAR, "Jasper.titleBackground"),
        Map.entry(Token.STATUS_BAR, "StatusBar.background"),
        Map.entry(Token.SIDEBAR, "ToolWindow.background"),
        Map.entry(Token.SURFACE_RAISED, "PopupMenu.background"),
        Map.entry(Token.SURFACE_SUNKEN, "List.background"),
        Map.entry(Token.INPUT_BACKGROUND, "TextField.background"),
        Map.entry(Token.BORDER, "Component.borderColor"),
        Map.entry(Token.SEPARATOR, "Separator.foreground"),
        Map.entry(Token.DIVIDER, "Jasper.splitDivider"),
        Map.entry(Token.FOCUS_RING, "Component.focusColor"),
        Map.entry(Token.SELECTION, "List.selectionBackground"),
        Map.entry(Token.SELECTION_TEXT, "List.selectionForeground"),
        Map.entry(Token.SELECTION_INACTIVE, "List.selectionInactiveBackground"),
        Map.entry(Token.TEXT_SELECTION, "TextField.selectionBackground"),
        Map.entry(Token.TEXT_SELECTION_TEXT, "TextField.selectionForeground"),
        Map.entry(Token.TAB_BAR, "TabbedPane.background"),
        Map.entry(Token.TAB_ACTIVE, "Jasper.tabSelectedBackground"),
        Map.entry(Token.TAB_ACTIVE_TEXT, "Jasper.tabSelectedForeground"),
        Map.entry(Token.TAB_ACTIVE_BORDER, "Jasper.tabSelectedBorder"),
        Map.entry(Token.TAB_HOVER, "Jasper.tabHoverBackground"),
        Map.entry(Token.TAB_UNDERLINE, "Jasper.tabUnderline"),
        Map.entry(Token.TAB_UNDERLINE_INACTIVE, "Jasper.tabUnderlineInactive"),
        Map.entry(Token.BUTTON_BACKGROUND, "Button.background"),
        Map.entry(Token.BUTTON_BORDER, "Button.borderColor"),
        Map.entry(Token.BUTTON_TEXT, "Button.foreground"),
        Map.entry(Token.BUTTON_DEFAULT, "Button.default.background"),
        Map.entry(Token.TEXT_ON_ACCENT, "Button.default.foreground"),
        Map.entry(Token.HOVER, "Button.hoverBackground"),
        Map.entry(Token.PRESSED, "Button.pressedBackground"),
        Map.entry(Token.SCROLLBAR_THUMB, "ScrollBar.thumb"),
        Map.entry(Token.SCROLLBAR_TRACK, "ScrollBar.track"),
        Map.entry(Token.ACCENT, "Component.accentColor"),
        Map.entry(Token.LINK, "Component.linkColor"),
        Map.entry(Token.SUCCESS, "Jasper.configSuccessForeground"),
        Map.entry(Token.WARNING, "Jasper.configWarningForeground"),
        Map.entry(Token.ERROR, "Jasper.configErrorForeground"),
        Map.entry(Token.ERROR_BACKGROUND, "Jasper.findErrorBackground"),
        Map.entry(Token.EDITOR_BACKGROUND, "Jasper.editorBackground"),
        Map.entry(Token.EDITOR_FOREGROUND, "Jasper.editorForeground"),
        Map.entry(Token.EDITOR_CARET, "Jasper.editorCaret"),
        Map.entry(Token.EDITOR_SELECTION, "Jasper.editorSelection"),
        Map.entry(Token.EDITOR_CURRENT_LINE, "Jasper.editorCurrentLine"),
        Map.entry(Token.EDITOR_GUTTER, "Jasper.editorGutter"),
        Map.entry(Token.EDITOR_LINE_NUMBERS, "Jasper.editorLineNumbers"));
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
