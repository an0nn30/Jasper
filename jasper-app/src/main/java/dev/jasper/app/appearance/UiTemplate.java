package dev.jasper.app.appearance;

import java.awt.Color;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import static dev.jasper.app.appearance.Token.*;

/**
 * How tokens become UI keys. {@link #FLATLAF} is the base layer for every theme that is not {@code ui_complete}: wildcards
 * first (IntelliJ applies keys in order), FlatLaf's base variables, Jasper's control geometry, then FlatLaf and IntelliJ
 * keys. {@link #JASPER} feeds Jasper's own chrome keys; {@link ChromeKeys} still derives the {@code Jasper.*} keys
 * without a token.
 */
final class UiTemplate {
    static final Map<String, Object> FLATLAF = flatlaf();
    static final Map<String, Token> JASPER = jasper();

    private UiTemplate() {}

    /** {@code #rrggbb}, or {@code #rrggbbaa} when not opaque. */
    static String hex(Color color) {
        String rgb = String.format("#%06x", color.getRGB() & 0xffffff);
        return color.getAlpha() == 255 ? rgb : rgb + String.format("%02x", color.getAlpha());
    }

    private static Map<String, Object> flatlaf() {
        var keys = new LinkedHashMap<String, Object>();
        keys.put("*.background", SURFACE);
        keys.put("*.foreground", TEXT);
        keys.put("*.disabledForeground", TEXT_DISABLED);
        keys.put("*.inactiveForeground", TEXT_DISABLED);
        keys.put("*.selectionBackground", TEXT_SELECTION);
        keys.put("*.selectionForeground", TEXT_SELECTION_TEXT);
        keys.put("*.selectionInactiveBackground", TEXT_SELECTION);
        keys.put("*.selectionInactiveForeground", TEXT_SELECTION_TEXT);
        keys.put("@background", SURFACE);
        keys.put("@foreground", TEXT);
        keys.put("@selectionBackground", SELECTION);
        keys.put("@selectionForeground", SELECTION_TEXT);
        keys.put("@accentBaseColor", ACCENT);
        keys.put("@disabledBackground", SURFACE);
        keys.put("@disabledForeground", TEXT_DISABLED);
        keys.put("@menuHoverBackground", HOVER);
        keys.put("@menuAcceleratorForeground", TEXT);
        keys.put("Component.arc", 4);
        keys.put("Button.arc", 8);
        keys.put("TextArea.margin", "4,8,4,8");
        keys.put("Button.margin", "2,14,2,14");
        keys.put("Button.minimumWidth", 72);
        keys.put("Button.default.boldText", true);
        keys.put("Button.paintShadow", false);
        keys.put("ComboBox.popupInsets", "0,0,0,0");
        keys.put("ComboBox.buttonStyle", "button");
        keys.put("Component.arrowType", "triangle");
        keys.put("Component.focusWidth", 0);
        keys.put("Component.innerFocusWidth", 0.5);
        keys.put("Component.innerOutlineWidth", 1);
        keys.put("Button.innerFocusWidth", 1);
        keys.put("CheckBox.icon.style", "outlined");
        keys.put("CheckBox.icon.focusWidth", 1);
        keys.put("RadioButton.icon.centerDiameter", 8);
        keys.put("Panel.background", SURFACE);
        keys.put("Label.foreground", TEXT);
        keys.put("Label.disabledForeground", TEXT_DISABLED);
        keys.put("List.background", SURFACE_SUNKEN);
        keys.put("List.foreground", TEXT);
        keys.put("List.selectionBackground", SELECTION);
        keys.put("List.selectionForeground", SELECTION_TEXT);
        keys.put("List.selectionInactiveBackground", SELECTION_INACTIVE);
        keys.put("List.selectionInactiveForeground", SELECTION_TEXT);
        for (String field : new String[] {"TextField", "TextArea", "PasswordField", "FormattedTextField"}) {
            keys.put(field + ".background", INPUT_BACKGROUND);
            keys.put(field + ".foreground", TEXT);
        }
        keys.put("ComboBox.background", INPUT_BACKGROUND);
        keys.put("ComboBox.nonEditableBackground", INPUT_BACKGROUND);
        keys.put("ComboBox.ArrowButton.background", INPUT_BACKGROUND);
        keys.put("ComboBox.ArrowButton.nonEditableBackground", INPUT_BACKGROUND);
        keys.put("ComboBox.ArrowButton.iconColor", TEXT);
        keys.put("ComboBox.foreground", TEXT);
        keys.put("ComboBox.selectionBackground", SELECTION);
        keys.put("ComboBox.selectionForeground", SELECTION_TEXT);
        keys.put("Button.startBackground", BUTTON_BACKGROUND);
        keys.put("Button.endBackground", BUTTON_BACKGROUND);
        keys.put("Button.startBorderColor", BUTTON_BORDER);
        keys.put("Button.endBorderColor", BUTTON_BORDER);
        keys.put("Button.foreground", BUTTON_TEXT);
        keys.put("Button.focusedBackground", BUTTON_BACKGROUND);
        keys.put("Button.hoverBackground", HOVER);
        keys.put("Button.pressedBackground", PRESSED);
        keys.put("Button.disabledBackground", SURFACE);
        keys.put("Button.default.startBackground", BUTTON_DEFAULT);
        keys.put("Button.default.endBackground", BUTTON_DEFAULT);
        keys.put("Button.default.startBorderColor", BUTTON_DEFAULT);
        keys.put("Button.default.endBorderColor", BUTTON_DEFAULT);
        keys.put("Button.default.foreground", TEXT_ON_ACCENT);
        keys.put("Button.default.focusedBackground", BUTTON_DEFAULT);
        keys.put("Button.toolbar.hoverBackground", HOVER);
        keys.put("Button.toolbar.pressedBackground", PRESSED);
        keys.put("Button.toolbar.selectedBackground", PRESSED);
        keys.put("Component.borderColor", BORDER);
        keys.put("Component.disabledBorderColor", BORDER);
        keys.put("Component.focusedBorderColor", FOCUS_RING);
        keys.put("Component.focusColor", FOCUS_RING);
        keys.put("Link.activeForeground", LINK);
        keys.put("ProgressBar.foreground", ACCENT);
        keys.put("ProgressBar.progressColor", ACCENT);
        keys.put("Separator.foreground", SEPARATOR);
        keys.put("Separator.separatorColor", SEPARATOR);
        keys.put("ToolBar.background", TITLE_BAR);
        keys.put("MenuBar.background", TITLE_BAR);
        keys.put("Menu.background", SURFACE_RAISED);
        keys.put("Menu.foreground", TEXT);
        keys.put("MenuItem.background", SURFACE_RAISED);
        keys.put("MenuItem.foreground", TEXT);
        keys.put("MenuItem.selectionForeground", SELECTION_TEXT);
        keys.put("MenuItem.acceleratorForeground", TEXT);
        keys.put("PopupMenu.background", SURFACE_RAISED);
        keys.put("PopupMenu.borderColor", BORDER);
        keys.put("TitlePane.background", TITLE_BAR);
        keys.put("TitlePane.inactiveBackground", TITLE_BAR);
        keys.put("TitlePane.foreground", TEXT_MUTED);
        keys.put("TitlePane.inactiveForeground", TEXT_MUTED);
        keys.put("TitlePane.infoForeground", TEXT_MUTED);
        keys.put("TitlePane.inactiveInfoForeground", TEXT_MUTED);
        keys.put("TabbedPane.background", TAB_BAR);
        keys.put("TabbedPane.selectedBackground", SURFACE_SUNKEN);
        keys.put("TabbedPane.selectedForeground", TEXT);
        keys.put("TabbedPane.hoverColor", HOVER);
        keys.put("TabbedPane.focusColor", HOVER);
        keys.put("TabbedPane.contentAreaColor", BORDER);
        keys.put("TabbedPane.underlineColor", ACCENT);
        keys.put("SplitPaneDivider.gripColor", TEXT);
        keys.put("SplitPaneDivider.hoverColor", HOVER);
        keys.put("SplitPaneDivider.pressedColor", PRESSED);
        keys.put("ScrollBar.thumb", SCROLLBAR_THUMB);
        keys.put("ScrollBar.track", SCROLLBAR_TRACK);
        keys.put("ScrollPane.background", SCROLLBAR_TRACK);
        keys.put("ToolTip.background", SURFACE_RAISED);
        keys.put("ToolTip.foreground", TEXT);
        keys.put("Tree.background", SURFACE_SUNKEN);
        keys.put("Table.background", SURFACE_SUNKEN);
        keys.put("MainToolbar.background", TITLE_BAR);
        keys.put("MainToolbar.foreground", TEXT);
        keys.put("EditorTabs.underlinedTabBackground", SURFACE_SUNKEN);
        keys.put("EditorTabs.underlinedTabForeground", TEXT);
        keys.put("EditorTabs.hoverBackground", TAB_HOVER);
        keys.put("EditorTabs.underlineColor", TAB_UNDERLINE);
        keys.put("EditorTabs.inactiveUnderlineColor", TAB_UNDERLINE_INACTIVE);
        keys.put("ToolWindow.background", SIDEBAR);
        keys.put("ToolWindow.Header.background", TITLE_BAR);
        keys.put("StatusBar.background", STATUS_BAR);
        keys.put("StatusBar.borderColor", BORDER);
        keys.put("Borders.color", BORDER);
        keys.put("List.hoverBackground", HOVER);
        keys.put("Popup.borderColor", BORDER);
        keys.put("SearchEverywhere.Header.background", TITLE_BAR);
        keys.put("SearchEverywhere.Tab.selectedBackground", PRESSED);
        keys.put("SearchEverywhere.Tab.selectedForeground", TEXT);
        keys.put("SearchEverywhere.SearchField.background", INPUT_BACKGROUND);
        keys.put("SearchEverywhere.SearchField.borderColor", BORDER);
        keys.put("SearchEverywhere.SearchField.infoForeground", TEXT_MUTED);
        keys.put("SearchEverywhere.List.separatorColor", BORDER);
        keys.put("SearchEverywhere.List.separatorForeground", TEXT_MUTED);
        keys.put("SearchEverywhere.Advertiser.background", TITLE_BAR);
        keys.put("SearchEverywhere.Advertiser.foreground", TEXT_MUTED);
        keys.put("SearchField.errorBackground", ERROR_BACKGROUND);
        return Collections.unmodifiableMap(keys);
    }

    private static Map<String, Token> jasper() {
        var keys = new LinkedHashMap<String, Token>();
        keys.put("Jasper.editorBackground", EDITOR_BACKGROUND);
        keys.put("Jasper.editorForeground", EDITOR_FOREGROUND);
        keys.put("Jasper.editorGutter", EDITOR_GUTTER);
        keys.put("Jasper.editorCurrentLine", EDITOR_CURRENT_LINE);
        keys.put("Jasper.editorSelection", EDITOR_SELECTION);
        keys.put("Jasper.editorCaret", EDITOR_CARET);
        keys.put("Jasper.editorLineNumbers", EDITOR_LINE_NUMBERS);
        keys.put("Jasper.titleBackground", TITLE_BAR);
        keys.put("Jasper.titleForeground", TEXT_MUTED);
        keys.put("Jasper.titleInactiveForeground", TEXT_MUTED);
        keys.put("Jasper.titleSeparator", BORDER);
        keys.put("Jasper.chromeForeground", TEXT);
        keys.put("Jasper.mutedForeground", TEXT_MUTED);
        keys.put("Jasper.tabSelectedBackground", TAB_ACTIVE);
        keys.put("Jasper.tabSelectedForeground", TAB_ACTIVE_TEXT);
        keys.put("Jasper.tabSelectedBorder", TAB_ACTIVE_BORDER);
        keys.put("Jasper.tabSelectedBorderInactive", TAB_UNDERLINE_INACTIVE);
        keys.put("Jasper.tabHoverBackground", TAB_HOVER);
        keys.put("Jasper.tabUnderline", TAB_UNDERLINE);
        keys.put("Jasper.tabUnderlineInactive", TAB_UNDERLINE_INACTIVE);
        keys.put("Jasper.findErrorBackground", ERROR_BACKGROUND);
        keys.put("Jasper.splitDivider", DIVIDER);
        keys.put("Jasper.runningForeground", SUCCESS);
        keys.put("Jasper.configSuccessForeground", SUCCESS);
        keys.put("Jasper.configWarningForeground", WARNING);
        keys.put("Jasper.configErrorForeground", ERROR);
        return Collections.unmodifiableMap(keys);
    }
}
