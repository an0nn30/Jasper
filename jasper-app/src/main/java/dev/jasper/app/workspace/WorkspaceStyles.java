package dev.jasper.app.workspace;

import com.formdev.flatlaf.ui.FlatStylingSupport.StyleableUI;
import dev.jasper.app.appearance.GeneratedTheme;
import dev.jasper.app.appearance.Token;
import java.awt.Color;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import javax.swing.*;
import javax.swing.table.JTableHeader;
import javax.swing.text.JTextComponent;

/** App-native semantic colors for FlatLaf's real Swing delegates. All calls belong to the EDT. */
final class WorkspaceStyles {
    record Recipe(String type, Map<String, Object> style, Map<String, Color> direct) {
        Recipe {
            style = Map.copyOf(style);
            direct = Map.copyOf(direct);
        }
    }

    private WorkspaceStyles() {}

    static Recipe recipe(JComponent component, GeneratedTheme theme) {
        if (!SwingUtilities.isEventDispatchThread()) throw new IllegalStateException("Workspace styles require EDT");
        Objects.requireNonNull(component);
        Objects.requireNonNull(theme);
        String type = type(component);
        if (type.isEmpty()) return new Recipe("", Map.of(), Map.of());
        var style = new LinkedHashMap<String, Object>();
        var direct = new LinkedHashMap<String, Color>();
        boolean flat = flatDelegate(component.getUI());
        switch (type) {
            case "Panel" -> {
                put(component, flat, style, direct, "background", theme, Token.SURFACE);
                put(component, flat, style, direct, "foreground", theme, Token.TEXT);
            }
            case "Label" -> {
                put(component, flat, style, direct, "background", theme, Token.SURFACE);
                put(component, flat, style, direct, "foreground", theme, Token.TEXT);
                if (flat) color(style, "disabledForeground", theme, Token.TEXT_DISABLED);
            }
            case "List", "Table", "Tree" -> {
                put(component, flat, style, direct, "background", theme, Token.SURFACE_SUNKEN);
                put(component, flat, style, direct, "foreground", theme, Token.TEXT);
                put(component, flat, style, direct, "selectionBackground", theme, Token.SELECTION);
                put(component, flat, style, direct, "selectionForeground", theme, Token.SELECTION_TEXT);
                if (flat) {
                    color(style, "selectionInactiveBackground", theme, Token.SELECTION_INACTIVE);
                    color(style, "selectionInactiveForeground", theme, Token.SELECTION_TEXT);
                    if (type.equals("Table")) {
                        color(style, "gridColor", theme, Token.BORDER);
                        color(style, "cellFocusColor", theme, Token.FOCUS_RING);
                    } else if (type.equals("List")) color(style, "cellFocusColor", theme, Token.FOCUS_RING);
                    else color(style, "selectionBorderColor", theme, Token.FOCUS_RING);
                }
            }
            case "TableHeader" -> {
                put(component, flat, style, direct, "background", theme, Token.SURFACE_RAISED);
                put(component, flat, style, direct, "foreground", theme, Token.TEXT);
                if (flat) {
                    color(style, "separatorColor", theme, Token.BORDER);
                    color(style, "bottomSeparatorColor", theme, Token.BORDER);
                    color(style, "hoverBackground", theme, Token.HOVER);
                }
            }
            case "TextField", "PasswordField", "FormattedTextField", "TextArea", "TextPane", "EditorPane" -> {
                put(component, flat, style, direct, "background", theme, Token.INPUT_BACKGROUND);
                put(component, flat, style, direct, "foreground", theme, Token.TEXT);
                put(component, flat, style, direct, "selectionColor", theme, Token.TEXT_SELECTION, "selectionBackground");
                put(component, flat, style, direct, "selectedTextColor", theme, Token.TEXT_SELECTION_TEXT, "selectionForeground");
                put(component, flat, style, direct, "caretColor", theme, Token.EDITOR_CARET);
                put(component, flat, style, direct, "disabledTextColor", theme, Token.TEXT_DISABLED);
                if (flat) {
                    color(style, "disabledBackground", theme, Token.INPUT_BACKGROUND);
                    color(style, "inactiveBackground", theme, Token.INPUT_BACKGROUND);
                    if (type.equals("TextField") || type.equals("PasswordField") || type.equals("FormattedTextField"))
                        border(component, style, theme);
                }
            }
            case "ComboBox" -> {
                put(component, flat, style, direct, "background", theme, Token.INPUT_BACKGROUND);
                put(component, flat, style, direct, "foreground", theme, Token.TEXT);
                if (flat) {
                    color(style, "disabledForeground", theme, Token.TEXT_DISABLED);
                    color(style, "editableBackground", theme, Token.INPUT_BACKGROUND);
                    color(style, "disabledBackground", theme, Token.INPUT_BACKGROUND);
                    color(style, "buttonBackground", theme, Token.INPUT_BACKGROUND);
                    color(style, "buttonEditableBackground", theme, Token.INPUT_BACKGROUND);
                    color(style, "buttonArrowColor", theme, Token.TEXT);
                    color(style, "buttonDisabledArrowColor", theme, Token.TEXT_DISABLED);
                    color(style, "popupBackground", theme, Token.SURFACE_SUNKEN);
                    border(component, style, theme);
                }
            }
            case "Button", "ToggleButton" -> {
                put(component, flat, style, direct, "background", theme, Token.BUTTON_BACKGROUND);
                put(component, flat, style, direct, "foreground", theme, Token.BUTTON_TEXT);
                if (flat) {
                    color(style, "disabledText", theme, Token.TEXT_DISABLED);
                    color(style, "hoverBackground", theme, Token.HOVER);
                    color(style, "pressedBackground", theme, Token.PRESSED);
                    color(style, "selectedBackground", theme, Token.SELECTION);
                    color(style, "selectedForeground", theme, Token.SELECTION_TEXT);
                    color(style, "default.background", theme, Token.BUTTON_DEFAULT);
                    color(style, "default.foreground", theme, Token.TEXT_ON_ACCENT);
                    border(component, style, theme);
                }
            }
            case "CheckBox", "RadioButton" -> {
                put(component, flat, style, direct, "foreground", theme, Token.TEXT);
                if (flat) {
                    color(style, "disabledText", theme, Token.TEXT_DISABLED);
                    // A plugin icon has its own palette and may not implement FlatLaf's icon styling.
                    if (((AbstractButton) component).getIcon() == null) {
                        color(style, "icon.background", theme, Token.INPUT_BACKGROUND);
                        color(style, "icon.borderColor", theme, Token.BORDER);
                        color(style, "icon.selectedBackground", theme, Token.ACCENT);
                        color(style, "icon.selectedBorderColor", theme, Token.ACCENT);
                        color(style, "icon.checkmarkColor", theme, Token.TEXT_ON_ACCENT);
                        color(style, "icon.hoverBackground", theme, Token.HOVER);
                        color(style, "icon.pressedBackground", theme, Token.PRESSED);
                        color(style, "icon.focusColor", theme, Token.FOCUS_RING);
                    }
                }
            }
            case "ScrollPane" -> {
                put(component, flat, style, direct, "background", theme, Token.SURFACE_SUNKEN);
                if (flat) border(component, style, theme);
            }
            case "Viewport" -> direct.put("background", theme.tokens().get(Token.SURFACE_SUNKEN));
            case "ScrollBar" -> {
                put(component, flat, style, direct, "background", theme, Token.SURFACE_SUNKEN);
                put(component, flat, style, direct, "foreground", theme, Token.TEXT);
                if (flat) {
                    color(style, "track", theme, Token.SCROLLBAR_TRACK);
                    color(style, "thumb", theme, Token.SCROLLBAR_THUMB);
                    color(style, "hoverThumbColor", theme, Token.HOVER);
                    color(style, "pressedThumbColor", theme, Token.PRESSED);
                    color(style, "buttonArrowColor", theme, Token.TEXT);
                    color(style, "buttonDisabledArrowColor", theme, Token.TEXT_DISABLED);
                }
            }
            case "SplitPane" -> {
                put(component, flat, style, direct, "background", theme, Token.SURFACE);
                if (flat) {
                    color(style, "draggingColor", theme, Token.DIVIDER);
                    color(style, "gripColor", theme, Token.DIVIDER);
                    color(style, "oneTouchArrowColor", theme, Token.DIVIDER);
                }
            }
            case "TabbedPane" -> {
                put(component, flat, style, direct, "background", theme, Token.TAB_BAR);
                put(component, flat, style, direct, "foreground", theme, Token.TEXT);
                if (flat) {
                    color(style, "selectedBackground", theme, Token.TAB_ACTIVE);
                    color(style, "selectedForeground", theme, Token.TAB_ACTIVE_TEXT);
                    color(style, "hoverColor", theme, Token.TAB_HOVER);
                    color(style, "underlineColor", theme, Token.TAB_UNDERLINE);
                    color(style, "inactiveUnderlineColor", theme, Token.TAB_UNDERLINE_INACTIVE);
                    color(style, "tabSeparatorColor", theme, Token.BORDER);
                    color(style, "contentAreaColor", theme, Token.SURFACE);
                }
            }
            case "ToolBar" -> {
                put(component, flat, style, direct, "background", theme, Token.SURFACE);
                put(component, flat, style, direct, "foreground", theme, Token.TEXT);
                if (flat) color(style, "separatorColor", theme, Token.SEPARATOR);
            }
            case "Separator" -> {
                put(component, flat, style, direct, "background", theme, Token.SURFACE);
                put(component, flat, style, direct, "foreground", theme, Token.SEPARATOR);
            }
            case "ProgressBar" -> {
                put(component, flat, style, direct, "background", theme, Token.SURFACE_SUNKEN);
                put(component, flat, style, direct, "foreground", theme, Token.ACCENT);
            }
            default -> throw new IllegalStateException(type);
        }
        return new Recipe(type, style, direct);
    }

    private static boolean flatDelegate(javax.swing.plaf.ComponentUI ui) {
        if (!(ui instanceof StyleableUI)) return false;
        for (Class<?> kind = ui.getClass(); kind != null; kind = kind.getSuperclass()) {
            if (kind.getPackageName().equals("com.formdev.flatlaf.ui")
                && kind.getSimpleName().startsWith("Flat")) return true;
        }
        return false;
    }

    private static void color(Map<String, Object> style, String key, GeneratedTheme theme, Token token) {
        style.put(key, theme.tokens().get(token));
    }

    private static void put(JComponent component, boolean flat, Map<String, Object> style, Map<String, Color> direct,
                            String key, GeneratedTheme theme, Token token) {
        put(component, flat, style, direct, key, theme, token, key);
    }

    private static void put(JComponent component, boolean flat, Map<String, Object> style, Map<String, Color> direct,
                            String key, GeneratedTheme theme, Token token, String directKey) {
        if (flat) color(style, key, theme, token);
        else if (supportsDirect(component, directKey)) direct.put(directKey, theme.tokens().get(token));
    }

    private static boolean supportsDirect(JComponent component, String key) {
        return switch (key) {
            case "background", "foreground" -> true;
            case "selectionBackground", "selectionForeground" ->
                component instanceof JList<?> || component instanceof JTable || component instanceof JTextComponent;
            case "caretColor", "disabledTextColor" -> component instanceof JTextComponent;
            default -> false;
        };
    }

    private static void border(JComponent component, Map<String, Object> style, GeneratedTheme theme) {
        if (component.getBorder() instanceof com.formdev.flatlaf.ui.FlatBorder
            && component.getBorder() instanceof javax.swing.plaf.UIResource) {
            color(style, "borderColor", theme, Token.BORDER);
            color(style, "focusedBorderColor", theme, Token.FOCUS_RING);
            color(style, "focusColor", theme, Token.FOCUS_RING);
        }
    }

    private static String type(JComponent c) {
        if (c instanceof JTableHeader) return "TableHeader";
        if (c instanceof JPasswordField) return "PasswordField";
        if (c instanceof JFormattedTextField) return "FormattedTextField";
        if (c instanceof JTextField) return "TextField";
        if (c instanceof JTextArea) return "TextArea";
        if (c instanceof JTextPane) return "TextPane";
        if (c instanceof JEditorPane) return "EditorPane";
        if (c instanceof JCheckBox) return "CheckBox";
        if (c instanceof JRadioButton) return "RadioButton";
        if (c instanceof JToggleButton) return "ToggleButton";
        if (c instanceof JButton) return "Button";
        if (c instanceof JList) return "List";
        if (c instanceof JTable) return "Table";
        if (c instanceof JTree) return "Tree";
        if (c instanceof JComboBox) return "ComboBox";
        if (c instanceof JScrollPane) return "ScrollPane";
        if (c instanceof JViewport) return "Viewport";
        if (c instanceof JScrollBar) return "ScrollBar";
        if (c instanceof JSplitPane) return "SplitPane";
        if (c instanceof JTabbedPane) return "TabbedPane";
        if (c instanceof JToolBar) return "ToolBar";
        if (c instanceof JSeparator) return "Separator";
        if (c instanceof JProgressBar) return "ProgressBar";
        if (c instanceof JLabel) return "Label";
        if (c instanceof JPanel) return "Panel";
        return "";
    }
}
