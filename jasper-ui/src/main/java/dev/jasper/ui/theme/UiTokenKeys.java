package dev.jasper.ui.theme;

import java.util.*;

/** Shared inverse legacy color keys. No component defaults are consulted. */
public final class UiTokenKeys {
    private UiTokenKeys() {}

    public static final Map<Token, String> KEYS =
            Map.ofEntries(
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

    static Set<String> aliases(Token token) {
        var keys = new LinkedHashSet<String>();
        keys.add(KEYS.get(token));
        UiColorKeys.chromeAliases()
                .forEach(
                        (key, value) -> {
                            if (value == token) keys.add(key);
                        });
        switch (token) {
            case BUTTON_BACKGROUND -> keys.add("Button.startBackground");
            case BUTTON_DEFAULT -> keys.add("Button.default.startBackground");
            case BUTTON_BORDER -> keys.add("Button.startBorderColor");
            case SCROLLBAR_TRACK -> keys.add("ScrollBar.trackColor");
            case SCROLLBAR_THUMB -> keys.add("ScrollBar.thumbColor");
            case SEPARATOR -> keys.add("Separator.separatorColor");
            case LINK -> keys.add("Link.activeForeground");
            default -> {}
        }
        return keys;
    }
}
