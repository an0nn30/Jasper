package dev.jasper.ui.theme;

import static dev.jasper.ui.theme.SemanticColor.*;

import java.awt.Color;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * FlatLaf's colour defaults for a Slate Steel variant, each from a semantic role; text selection in text components
 * takes the variant's terminal selection and a search field's no-match background the {@linkplain #errorTint error
 * tint}. Order matters: a {@code *.suffix} key recolours the keys before it that end in the suffix.
 */
public final class UiColorKeys {
    private UiColorKeys() {}

    private sealed interface Source permits Role, TextSelection, ErrorTint {}
    private record Role(SemanticColor role) implements Source {}
    private record TextSelection() implements Source {}
    private record ErrorTint() implements Source {}
    private static final Source SELECTED_TEXT = new TextSelection(), ERROR = new ErrorTint();
    private static final Map<String, Source> KEYS = keys();

    /** The keys in FlatLaf's order, each as {@code #rrggbb} or {@code #rrggbbaa}. */
    public static Map<String, Object> flatLaf(SemanticPalette palette, Color textSelection) {
        var keys = new LinkedHashMap<String, Object>();
        KEYS.forEach((key, source) -> keys.put(key, ColorMath.hex(switch (source) {
            case Role role -> palette.color(role.role());
            case TextSelection ignored -> textSelection;
            case ErrorTint ignored -> errorTint(palette);
        })));
        return Collections.unmodifiableMap(keys);
    }

    /** A search field's no-match background: the error border laid lightly over the input surface. */
    public static Color errorTint(SemanticPalette palette) {
        return ColorMath.mix(palette.color(BORDER_ERROR), palette.color(SURFACE_INPUT), .16);
    }

    private static Map<String, Source> keys() {
        var keys = new LinkedHashMap<String, Source>();
        role(keys, "*.background", SURFACE_PANEL);
        role(keys, "*.foreground", TEXT_PRIMARY);
        role(keys, "*.disabledForeground", TEXT_PLACEHOLDER);
        role(keys, "*.inactiveForeground", TEXT_PLACEHOLDER);
        keys.put("*.selectionBackground", SELECTED_TEXT);
        role(keys, "*.selectionForeground", TEXT_PRIMARY);
        keys.put("*.selectionInactiveBackground", SELECTED_TEXT);
        role(keys, "*.selectionInactiveForeground", TEXT_PRIMARY);
        role(keys, "@background", SURFACE_PANEL);
        role(keys, "@foreground", TEXT_PRIMARY);
        role(keys, "@selectionBackground", SELECTION_BG);
        role(keys, "@selectionForeground", TEXT_ONACCENT);
        role(keys, "@accentBaseColor", ACCENT);
        role(keys, "@disabledBackground", SURFACE_PANEL);
        role(keys, "@disabledForeground", TEXT_PLACEHOLDER);
        role(keys, "@menuHoverBackground", SURFACE_CONTROL_HOVER);
        role(keys, "@menuAcceleratorForeground", TEXT_PRIMARY);
        role(keys, "Panel.background", SURFACE_PANEL);
        role(keys, "Label.foreground", TEXT_PRIMARY);
        role(keys, "Label.disabledForeground", TEXT_PLACEHOLDER);
        role(keys, "List.background", SURFACE_CARD);
        role(keys, "List.foreground", TEXT_PRIMARY);
        role(keys, "List.selectionBackground", SELECTION_BG);
        role(keys, "List.selectionForeground", TEXT_ONACCENT);
        role(keys, "List.selectionInactiveBackground", SURFACE_CONTROL_SELECTED);
        role(keys, "List.selectionInactiveForeground", TEXT_ONACCENT);
        for (String field : new String[] {"TextField", "TextArea", "PasswordField", "FormattedTextField"}) {
            role(keys, field + ".background", SURFACE_INPUT);
            role(keys, field + ".foreground", TEXT_PRIMARY);
        }
        role(keys, "ComboBox.background", SURFACE_INPUT);
        role(keys, "ComboBox.nonEditableBackground", SURFACE_INPUT);
        role(keys, "ComboBox.ArrowButton.background", SURFACE_INPUT);
        role(keys, "ComboBox.ArrowButton.nonEditableBackground", SURFACE_INPUT);
        role(keys, "ComboBox.ArrowButton.iconColor", TEXT_PRIMARY);
        role(keys, "ComboBox.foreground", TEXT_PRIMARY);
        role(keys, "ComboBox.selectionBackground", SELECTION_BG);
        role(keys, "ComboBox.selectionForeground", TEXT_ONACCENT);
        role(keys, "Button.startBackground", BUTTON_SECONDARY_BG);
        role(keys, "Button.endBackground", BUTTON_SECONDARY_BG);
        role(keys, "Button.startBorderColor", BUTTON_SECONDARY_BORDER);
        role(keys, "Button.endBorderColor", BUTTON_SECONDARY_BORDER);
        role(keys, "Button.foreground", TEXT_STRONG_SECONDARY);
        role(keys, "Button.focusedBackground", BUTTON_SECONDARY_BG);
        role(keys, "Button.hoverBackground", SURFACE_CONTROL_HOVER);
        role(keys, "Button.pressedBackground", SURFACE_CONTROL_SELECTED);
        role(keys, "Button.disabledBackground", SURFACE_PANEL);
        role(keys, "Button.default.startBackground", ACCENT);
        role(keys, "Button.default.endBackground", ACCENT);
        role(keys, "Button.default.startBorderColor", ACCENT);
        role(keys, "Button.default.endBorderColor", ACCENT);
        role(keys, "Button.default.foreground", TEXT_ONACCENT);
        role(keys, "Button.default.focusedBackground", ACCENT);
        role(keys, "Button.toolbar.hoverBackground", SURFACE_CONTROL_HOVER);
        role(keys, "Button.toolbar.pressedBackground", SURFACE_CONTROL_SELECTED);
        role(keys, "Button.toolbar.selectedBackground", SURFACE_CONTROL_SELECTED);
        role(keys, "Component.borderColor", BORDER_WINDOW);
        role(keys, "Component.disabledBorderColor", BORDER_WINDOW);
        role(keys, "Component.focusedBorderColor", BORDER_FOCUS);
        role(keys, "Component.focusColor", BORDER_FOCUS);
        role(keys, "Link.activeForeground", TEXT_LINK);
        role(keys, "ProgressBar.foreground", ACCENT);
        role(keys, "ProgressBar.progressColor", ACCENT);
        role(keys, "Separator.foreground", BORDER_SUBTLE);
        role(keys, "Separator.separatorColor", BORDER_SUBTLE);
        role(keys, "ToolBar.background", SURFACE_APP);
        role(keys, "MenuBar.background", SURFACE_APP);
        role(keys, "Menu.background", SURFACE_PANEL_RAISED);
        role(keys, "Menu.foreground", TEXT_PRIMARY);
        role(keys, "MenuItem.background", SURFACE_PANEL_RAISED);
        role(keys, "MenuItem.foreground", TEXT_PRIMARY);
        role(keys, "MenuItem.selectionForeground", TEXT_ONACCENT);
        role(keys, "MenuItem.acceleratorForeground", TEXT_PRIMARY);
        role(keys, "PopupMenu.background", SURFACE_PANEL_RAISED);
        role(keys, "PopupMenu.borderColor", BORDER_WINDOW);
        role(keys, "TitlePane.background", SURFACE_APP);
        role(keys, "TitlePane.inactiveBackground", SURFACE_APP);
        role(keys, "TitlePane.foreground", TEXT_SECONDARY);
        role(keys, "TitlePane.inactiveForeground", TEXT_SECONDARY);
        role(keys, "TitlePane.infoForeground", TEXT_SECONDARY);
        role(keys, "TitlePane.inactiveInfoForeground", TEXT_SECONDARY);
        role(keys, "TabbedPane.background", SURFACE_TABSTRIP);
        role(keys, "TabbedPane.selectedBackground", SURFACE_CARD);
        role(keys, "TabbedPane.selectedForeground", TEXT_PRIMARY);
        role(keys, "TabbedPane.hoverColor", SURFACE_CONTROL_HOVER);
        role(keys, "TabbedPane.focusColor", SURFACE_CONTROL_HOVER);
        role(keys, "TabbedPane.contentAreaColor", BORDER_WINDOW);
        role(keys, "TabbedPane.underlineColor", ACCENT);
        role(keys, "SplitPaneDivider.gripColor", TEXT_PRIMARY);
        role(keys, "SplitPaneDivider.hoverColor", SURFACE_CONTROL_HOVER);
        role(keys, "SplitPaneDivider.pressedColor", SURFACE_CONTROL_SELECTED);
        role(keys, "ScrollBar.thumb", BORDER_CONTROL);
        role(keys, "ScrollBar.track", SURFACE_PANEL);
        role(keys, "ScrollPane.background", SURFACE_PANEL);
        role(keys, "ToolTip.background", SURFACE_PANEL_RAISED);
        role(keys, "ToolTip.foreground", TEXT_PRIMARY);
        role(keys, "Tree.background", SURFACE_CARD);
        role(keys, "Table.background", SURFACE_CARD);
        role(keys, "MainToolbar.background", SURFACE_APP);
        role(keys, "MainToolbar.foreground", TEXT_PRIMARY);
        role(keys, "EditorTabs.underlinedTabBackground", SURFACE_CARD);
        role(keys, "EditorTabs.underlinedTabForeground", TEXT_PRIMARY);
        role(keys, "EditorTabs.hoverBackground", SURFACE_CONTROL_HOVER);
        role(keys, "EditorTabs.underlineColor", ACCENT);
        role(keys, "EditorTabs.inactiveUnderlineColor", TEXT_TERTIARY);
        role(keys, "ToolWindow.background", SURFACE_SIDEBAR);
        role(keys, "ToolWindow.Header.background", SURFACE_APP);
        role(keys, "StatusBar.background", SURFACE_STATUS);
        role(keys, "StatusBar.borderColor", BORDER_WINDOW);
        role(keys, "Borders.color", BORDER_WINDOW);
        role(keys, "List.hoverBackground", SURFACE_CONTROL_HOVER);
        role(keys, "Popup.borderColor", BORDER_WINDOW);
        role(keys, "SearchEverywhere.Header.background", SURFACE_APP);
        role(keys, "SearchEverywhere.Tab.selectedBackground", SURFACE_CONTROL_SELECTED);
        role(keys, "SearchEverywhere.Tab.selectedForeground", TEXT_PRIMARY);
        role(keys, "SearchEverywhere.SearchField.background", SURFACE_INPUT);
        role(keys, "SearchEverywhere.SearchField.borderColor", BORDER_WINDOW);
        role(keys, "SearchEverywhere.SearchField.infoForeground", TEXT_SECONDARY);
        role(keys, "SearchEverywhere.List.separatorColor", BORDER_WINDOW);
        role(keys, "SearchEverywhere.List.separatorForeground", TEXT_SECONDARY);
        role(keys, "SearchEverywhere.Advertiser.background", SURFACE_APP);
        role(keys, "SearchEverywhere.Advertiser.foreground", TEXT_SECONDARY);
        keys.put("SearchField.errorBackground", ERROR);
        return Collections.unmodifiableMap(keys);
    }

    private static void role(Map<String, Source> keys, String key, SemanticColor role) { keys.put(key, new Role(role)); }
}
