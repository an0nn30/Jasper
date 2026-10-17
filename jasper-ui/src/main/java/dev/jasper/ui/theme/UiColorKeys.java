package dev.jasper.ui.theme;

import static dev.jasper.ui.theme.SemanticColor.*;

import java.awt.Color;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * FlatLaf's colour defaults for a theme variant in a {@link UiKeySet}, each from a semantic role (or, in the classic
 * set, a derived one); text selection in text components takes the variant's terminal selection and a search field's
 * no-match background the {@linkplain #errorTint error tint}. Order matters: a {@code *.suffix} key recolours the keys
 * before it that end in the suffix.
 */
public final class UiColorKeys {
    private UiColorKeys() {}

    private sealed interface Source permits Role, Derived, TextSelection, ErrorTint, None {}
    private record Role(SemanticColor role) implements Source {}
    private record Derived(DerivedColor role) implements Source {}
    private record TextSelection() implements Source {}
    private record ErrorTint() implements Source {}
    /** No colour: FlatLaf's own default for the key is removed, so the component falls back to its base colour. */
    private record None() implements Source {}
    private static final Source SELECTED_TEXT = new TextSelection(), ERROR = new ErrorTint(), NONE = new None();
    private static final Map<String, Source> KEYS = keys();
    private static final Map<String, Source> CLASSIC_KEYS = classicKeys();

    /**
     * {@code set}'s keys for {@code theme} in FlatLaf's order, each as {@code #rrggbb} or {@code #rrggbbaa}: the roles from
     * the theme's palette (and, in the classic set, its derived roles), text selection from its terminal colours; a
     * classic key with no colour is the string {@code "null"}, FlatLaf's null, which removes FlatLaf's default.
     */
    public static Map<String, Object> flatLaf(GeneratedTheme theme, UiKeySet set) {
        Map<String, Source> sources = switch (Objects.requireNonNull(set)) {
            case DEFAULT -> KEYS;
            case CLASSIC -> CLASSIC_KEYS;
        };
        return values(sources, theme, theme.terminal().selection());
    }

    private static Map<String, Object> values(Map<String, Source> sources, GeneratedTheme theme, Color textSelection) {
        var palette = theme.semantic();
        var keys = new LinkedHashMap<String, Object>();
        sources.forEach((key, source) -> keys.put(key, switch (source) {
            case Role role -> ColorMath.hex(palette.color(role.role()));
            case Derived role -> ColorMath.hex(role.role().color(theme));
            case TextSelection ignored -> ColorMath.hex(textSelection);
            case ErrorTint ignored -> ColorMath.hex(errorTint(palette));
            case None ignored -> "null";
        }));
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
        role(keys, "@selectionForeground", TEXT_ONSELECTION);
        role(keys, "@accentBaseColor", ACCENT);
        role(keys, "@disabledBackground", SURFACE_PANEL);
        role(keys, "@disabledForeground", TEXT_PLACEHOLDER);
        role(keys, "@menuHoverBackground", SURFACE_CONTROL_HOVER);
        role(keys, "@menuAcceleratorForeground", TEXT_TERTIARY);
        role(keys, "@menuAcceleratorSelectionForeground", TEXT_ONACCENT);
        role(keys, "Panel.background", SURFACE_PANEL);
        role(keys, "Label.foreground", TEXT_PRIMARY);
        role(keys, "Label.disabledForeground", TEXT_PLACEHOLDER);
        role(keys, "List.background", SURFACE_CARD);
        role(keys, "List.foreground", TEXT_PRIMARY);
        role(keys, "List.selectionBackground", SELECTION_BG);
        role(keys, "List.selectionForeground", TEXT_ONSELECTION);
        role(keys, "List.selectionInactiveBackground", SURFACE_CONTROL_SELECTED);
        role(keys, "List.selectionInactiveForeground", TEXT_ONSELECTION);
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
        role(keys, "ComboBox.selectionForeground", TEXT_ONSELECTION);
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
        // Native menus are the kit menu: its surface, an accent highlight, a border.menu edge, tertiary accelerators.
        role(keys, "Menu.background", SURFACE_MENU);
        role(keys, "Menu.foreground", TEXT_PRIMARY);
        role(keys, "Menu.selectionBackground", ACCENT);
        role(keys, "Menu.selectionForeground", TEXT_ONACCENT);
        role(keys, "MenuItem.background", SURFACE_MENU);
        role(keys, "MenuItem.foreground", TEXT_PRIMARY);
        role(keys, "MenuItem.selectionForeground", TEXT_ONACCENT);
        role(keys, "MenuItem.selectionBackground", ACCENT);
        role(keys, "MenuItem.acceleratorForeground", TEXT_TERTIARY);
        role(keys, "MenuItem.acceleratorSelectionForeground", TEXT_ONACCENT);
        role(keys, "CheckBoxMenuItem.selectionBackground", ACCENT);
        role(keys, "RadioButtonMenuItem.selectionBackground", ACCENT);
        role(keys, "PopupMenu.background", SURFACE_MENU);
        role(keys, "PopupMenu.borderColor", BORDER_MENU);
        role(keys, "TitlePane.background", SURFACE_TITLEBAR);
        role(keys, "TitlePane.inactiveBackground", SURFACE_TITLEBAR);
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
        role(keys, "TabbedPane.underlineColor", TAB_INDICATOR);
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
        role(keys, "MainToolbar.background", SURFACE_TITLEBAR);
        role(keys, "MainToolbar.foreground", TEXT_TITLEBAR);
        role(keys, "EditorTabs.underlinedTabBackground", SURFACE_CARD);
        role(keys, "EditorTabs.underlinedTabForeground", TEXT_PRIMARY);
        role(keys, "EditorTabs.hoverBackground", SURFACE_CONTROL_HOVER);
        role(keys, "EditorTabs.underlineColor", TAB_INDICATOR);
        role(keys, "EditorTabs.inactiveUnderlineColor", TAB_INDICATOR_INACTIVE);
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

    /**
     * The classic overlay (spec 6.5): the default keys, an existing key recoloured in place and a new key after every
     * wildcard, so the table and tree selections take {@code selection.bg} over the terminal-selection wildcard.
     */
    private static Map<String, Source> classicKeys() {
        var keys = new LinkedHashMap<String, Source>(KEYS);
        keys.put("TableHeader.background", new Derived(DerivedColor.SURFACE_TABLE_HEADER));
        role(keys, "TableHeader.foreground", TEXT_SECONDARY);
        keys.put("TableHeader.separatorColor", new Derived(DerivedColor.BORDER_DIVIDER));
        keys.put("TableHeader.bottomSeparatorColor", new Derived(DerivedColor.BORDER_DIVIDER));
        keys.put("Table.alternateRowColor", new Derived(DerivedColor.SURFACE_ZEBRA));
        keys.put("Table.gridColor", new Derived(DerivedColor.BORDER_DIVIDER));
        role(keys, "Table.selectionBackground", SELECTION_BG);
        role(keys, "Table.selectionForeground", TEXT_ONSELECTION);
        role(keys, "Tree.selectionBackground", SELECTION_BG);
        role(keys, "Tree.selectionForeground", TEXT_ONSELECTION);
        role(keys, "Button.startBackground", BUTTON_SECONDARY_BG);
        keys.put("Button.endBackground", new Derived(DerivedColor.BUTTON_END));
        // FlatLaf paints the gradient only while a button's state colour is Button.startBackground itself (plan 4c): a
        // hovered or focused button keeps it (its edge darkens, its focus ring shows); a pressed one darkens to its end.
        keys.put("Button.focusedBackground", NONE);
        keys.put("Button.hoverBackground", NONE);
        keys.put("Button.pressedBackground", new Derived(DerivedColor.BUTTON_END));
        keys.put("Button.hoverBorderColor", new Derived(DerivedColor.BORDER_HOVER));
        keys.put("Button.default.borderColor", new Derived(DerivedColor.ACCENT_EDGE));
        // FlatLaf reads the default border from startBorderColor first and ignores borderColor while it is set.
        keys.put("Button.default.startBorderColor", new Derived(DerivedColor.ACCENT_EDGE));
        keys.put("Button.default.endBorderColor", new Derived(DerivedColor.ACCENT_EDGE));
        role(keys, "MenuBar.selectionBackground", ACCENT);
        role(keys, "MenuBar.selectionForeground", TEXT_ONACCENT);
        keys.put("ToolBar.separatorColor", new Derived(DerivedColor.BORDER_DIVIDER));
        return Collections.unmodifiableMap(keys);
    }
}
