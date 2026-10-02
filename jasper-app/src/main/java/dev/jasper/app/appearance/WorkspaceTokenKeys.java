package dev.jasper.app.appearance;

import java.awt.Color;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Function;

/** Pure inverse of effective UI colors; absent keys retain their generated value. */
public final class WorkspaceTokenKeys {
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

    static Map<Token, Color> resolve(Map<Token, Color> generated, Function<String, Color> colors) {
        var tokens = new EnumMap<Token, Color>(Token.class);
        tokens.putAll(generated);
        KEYS.forEach((token, key) -> {
            Color color = colors.apply(key);
            if (color != null) tokens.put(token, color);
        });
        return tokens;
    }

    /** Inverse authored colors only; platform-specific values remain in UI for runtime materialization. */
    public static Map<Token, Color> explicitTokens(Map<String, Object> resolvedUi) {
        var result = new EnumMap<Token, Color>(Token.class);
        for (Token token : Token.values()) {
            // In a complete theme, an explicit token also emits Jasper chrome aliases. Do not invent those
            // from another component's color: ChromeKeys may derive them differently (including contrast).
            // Authored UI remains intact and WorkspaceValues still resolves these tokens from effective defaults.
            if (UiTemplate.JASPER.entrySet().stream().anyMatch(entry -> entry.getValue() == token && !resolvedUi.containsKey(entry.getKey())))
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
            var candidates = new java.util.ArrayList<>(keys);
            // A wildcard matching an absent canonical key must not outrank an authored alternate key.
            candidates.sort(java.util.Comparator.comparing(key -> !resolvedUi.containsKey(key)));
            for (String key : candidates) {
                Object supplied = null;
                for (var entry : resolvedUi.entrySet()) {
                    String source = entry.getKey();
                    if (source.equals(key) || (source.startsWith("*.") && !key.startsWith("@")
                            && !key.endsWith(".hoverBackground") && !key.endsWith(".pressedBackground")
                            && key.endsWith(source.substring(1))))
                        supplied = entry.getValue();
                }
                // Even an os.default value cannot be frozen as a token: explicit tokens outrank effective workspace defaults.
                if (supplied instanceof Map<?, ?>) break;
                Color color = FlatLafThemeData.color(supplied);
                if (color != null) { result.put(token, color); break; }
            }
        }
        return Map.copyOf(result);
    }
}
