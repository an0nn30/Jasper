package dev.jasper.ui.theme;

import static dev.jasper.ui.theme.SemanticColor.*;

import java.awt.Color;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Color-only legacy UI key policy; never admits geometry, fonts or delegates. */
public final class UiColorKeys {
    private static final Pattern HEX = Pattern.compile("#(?:[0-9a-fA-F]{3}|[0-9a-fA-F]{4}|[0-9a-fA-F]{6}|[0-9a-fA-F]{8})");
    private static final Pattern NAME = Pattern.compile("[A-Za-z_][A-Za-z0-9_.-]*");
    private static final Pattern CLASS_NAME = Pattern.compile("(?:[a-z_$][\\w$]*\\.)+[A-Z][\\w$]*");
    private static final Set<String> OPTIONS = Set.of("relative", "autoInverse", "noAutoInverse", "derived", "lazy");

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

    /** Legacy Jasper color aliases used to avoid inventing explicit tokens during import conversion. */
    public static Map<String, Token> chromeAliases() { return UiTemplate.JASPER; }

    /**
     * Supported vendor UI keys translated straight into a dedicated canonical role. Each was read raw by a native
     * consumer before the theme-engine migration, so an authored value wins over the legacy token for that role, as
     * the raw key did; an explicit canonical {@code [semantic]} value still wins. Other vendor keys either reach
     * canonical roles through their legacy token aliases (with token precedence) or have no canonical role
     * (see the contracts appendix, Plan 2 Task 5).
     */
    public static Map<String, SemanticColor> semanticRoles() { return SEMANTIC_ROLES; }
    private static final Map<String, SemanticColor> SEMANTIC_ROLES = Map.of(
        "ToolWindow.background", SemanticColor.SURFACE_SIDEBAR,
        "TextField.background", SemanticColor.SURFACE_INPUT,
        "Separator.foreground", SemanticColor.BORDER_SUBTLE,
        "StatusBar.background", SemanticColor.SURFACE_STATUS);

    /**
     * Nonfatal guidance for explicit authored keys whose old native consumer has been removed.
     * The color remains valid data for round trips and vendor delegates. Call only on authored data,
     * never the generated UiTemplate defaults; supported canonical and legacy aliases remain valid.
     */
    public static java.util.Optional<String> nativeOverrideWarning(String key) {
        if (SEMANTIC_ROLES.containsKey(key) || chromeAliases().containsKey(key)) return java.util.Optional.empty();
        String roles = null;
        if (key.startsWith("Jasper.notification.")) roles = "surface.panel, text.primary, text.secondary, border.subtle, status.danger";
        else if (key.startsWith("MainToolbar.searchField.") || key.startsWith("SearchEverywhere.SearchField."))
            roles = "surface.input, surface.control.hover, border.input, text.primary, text.placeholder";
        else if (key.startsWith("EditorTabs.")) roles = "accent, text.primary (tab surfaces follow the workspace terminal palette)";
        else roles = switch (key) {
            case "TitlePane.foreground", "TitlePane.inactiveForeground", "MainToolbar.foreground" -> "text.primary, text.secondary, text.tertiary";
            case "MainToolbar.borderColor", "Borders.color" -> "border.subtle";
            case "Button.toolbar.hoverBackground", "Button.toolbar.pressedBackground", "Button.toolbar.selectedBackground" -> "hover.overlay, surface.control.hover, segment.selected.bg";
            case "Popup.dropShadowColor" -> "surface.scrim";
            case "DragAndDrop.areaBackground", "DragAndDrop.borderColor", "TabbedPane.underlineColor" -> "accent";
            case "Label.infoForeground" -> "text.secondary";
            case "Actions.Green" -> "status.success";
            case "Actions.Yellow" -> "status.warning";
            case "Actions.Red" -> "status.danger";
            default -> null;
        };
        return roles == null ? java.util.Optional.empty() : java.util.Optional.of("[ui] key \"" + key
            + "\" no longer controls Jasper native UI; use [semantic] " + roles + "; color retained for vendor delegates and serialization");
    }

    /** Whether a legacy UI key describes a color or a referenced color variable. */
    public static boolean supports(String key) {
        if (key.endsWith("UI")) return false;
        if (key.startsWith("ColorPalette.")) return NAME.matcher(key.substring("ColorPalette.".length())).matches();
        if (Set.of("ScrollBar.trackHighlight", "TabbedPane.focus", "VersionControl.RefLabel.backgroundBase").contains(key)) return true;
        if (UiTemplate.JASPER.containsKey(key) || key.equals("Jasper.headerHighlight")) return true;
        String lower = key.toLowerCase(Locale.ROOT);
        // FlatLaf's border slots parse insets, line thickness and arcs, even without a class name.
        if (!key.startsWith("@") && lower.endsWith("border")) return false;
        return key.startsWith("@") || lower.endsWith("background") || lower.endsWith("foreground")
            || lower.endsWith("disabledtext") || lower.endsWith("selectionbackgroundinactive") || lower.endsWith("selectionforegroundinactive")
            || lower.endsWith("color") || lower.endsWith("thumb") || lower.endsWith("track")
            || lower.endsWith("caret") || lower.startsWith("actions.") || lower.startsWith("objects.");
    }

    /** Whether the key and value are color-only, including every per-platform choice. */
    public static boolean supports(String key, Object value) {
        if (!supports(key)) return false;
        if (value instanceof Color) return true;
        if (value instanceof String text) return text.length() <= 4096 && expression(text.strip(), 0);
        if (value instanceof Map<?, ?> choices && !choices.isEmpty())
            return choices.entrySet().stream().allMatch(entry -> entry.getKey() instanceof String os
                && os.startsWith("os.") && supports(key, entry.getValue()));
        return false;
    }

    /** Validates the color grammar only; rendering and reference resolution remain caller-owned. */
    private static boolean expression(String text, int depth) {
        // FlatLaf parses these as booleans before inferring color type. They are valid only as conditions.
        if (depth > 32 || text.isEmpty() || text.equals("true") || text.equals("false")) return false;
        if (HEX.matcher(text).matches() || text.equals("null")) return true;
        if (text.startsWith("@")) return NAME.matcher(text.substring(1)).matches();
        if (text.startsWith("$")) return property(text.substring(1));
        if (NAME.matcher(text).matches()) return !CLASS_NAME.matcher(text).matches();
        int open = text.indexOf('(');
        if (open < 1 || !text.endsWith(")")) return false;
        String function = text.substring(0, open);
        List<String> args = arguments(text.substring(open + 1, text.length() - 1));
        if (args == null) return false;
        int count = args.size();
        return switch (function) {
            case "rgb" -> count == 3 && channels(args, 255);
            case "rgba" -> count == 4 && channels(args, 255)
                || count == 2 && expression(args.get(0), depth + 1) && channel(args.get(1), 255);
            case "hsl", "hsla" -> count == (function.equals("hsl") ? 3 : 4)
                && integer(args.get(0), 0, 360) && percentage(args.get(1)) && percentage(args.get(2))
                && (count == 3 || percentage(args.get(3)));
            case "lighten", "darken", "saturate", "desaturate", "fadein", "fadeout", "fade",
                 "changeSaturation", "changeLightness", "changeAlpha" ->
                count >= 2 && count <= 3 && expression(args.get(0), depth + 1) && percentage(args.get(1))
                    && (count == 2 || options(args.get(2)));
            case "spin", "changeHue" -> count >= 2 && count <= 3 && expression(args.get(0), depth + 1)
                && integer(args.get(1), Integer.MIN_VALUE, Integer.MAX_VALUE) && (count == 2 || options(args.get(2)));
            case "mix" -> mix(args, 2, depth);
            case "tint", "shade" -> mix(args, 1, depth);
            case "contrast" -> (count == 3 || count == 4) && colors(args.subList(0, 3), depth)
                && (count == 3 || percentage(args.get(3)));
            case "over" -> count == 2 && colors(args, depth);
            case "lazy" -> count == 1 && property(args.getFirst());
            case "systemColor" -> (count == 1 || count == 2) && NAME.matcher(args.getFirst()).matches()
                && (count == 1 || expression(args.get(1), depth + 1));
            case "if" -> count == 3 && condition(args.get(0)) && colors(args.subList(1, 3), depth);
            default -> false;
        };
    }

    private static boolean property(String text) {
        // FlatLaf allows one optional marker after '$' (and inside lazy property names).
        if (text.startsWith("?")) text = text.substring(1);
        return NAME.matcher(text).matches() && supports(text) && !CLASS_NAME.matcher(text).matches();
    }

    private static boolean condition(String text) {
        return text.equals("true") || text.equals("false") || text.equals("0")
            || (text.startsWith("@") || text.startsWith("$")) && NAME.matcher(text.substring(1)).matches();
    }

    private static boolean colors(List<String> args, int depth) {
        return args.stream().allMatch(arg -> expression(arg, depth + 1));
    }

    private static boolean mix(List<String> args, int colorCount, int depth) {
        if (args.size() < colorCount || args.size() > colorCount + 2
                || !colors(args.subList(0, colorCount), depth)) return false;
        if (args.size() == colorCount) return true;
        String next = args.get(colorCount);
        if (percentage(next)) return args.size() == colorCount + 1 || options(args.get(colorCount + 1));
        return args.size() == colorCount + 1 && options(next);
    }

    private static boolean channels(List<String> args, int maximum) {
        return args.stream().allMatch(arg -> channel(arg, maximum));
    }

    private static boolean channel(String text, int maximum) {
        return percentage(text) || integer(text, 0, maximum);
    }

    private static boolean percentage(String text) {
        return text.endsWith("%") && integer(text.substring(0, text.length() - 1), 0, 100);
    }

    private static boolean integer(String text, int minimum, int maximum) {
        try {
            int value = Integer.parseInt(text);
            return value >= minimum && value <= maximum;
        } catch (NumberFormatException invalid) {
            return false;
        }
    }

    private static boolean options(String text) {
        return !text.isBlank() && List.of(text.split("\\s+")).stream().allMatch(OPTIONS::contains);
    }

    /** Split only commas outside nested color functions, rejecting unmatched parentheses. */
    private static List<String> arguments(String text) {
        var result = new ArrayList<String>();
        int level = 0, start = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '(') level++;
            else if (c == ')' && --level < 0) return null;
            else if (c == ',' && level == 0) {
                result.add(text.substring(start, i).strip());
                start = i + 1;
            }
        }
        if (level != 0) return null;
        result.add(text.substring(start).strip());
        return result;
    }
}
