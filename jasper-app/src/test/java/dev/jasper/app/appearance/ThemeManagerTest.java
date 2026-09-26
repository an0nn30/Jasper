package dev.jasper.app.appearance;

import com.formdev.flatlaf.FlatLaf;
import dev.jasper.app.testsupport.EdtTestExtension;
import java.awt.Color;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.swing.LookAndFeel;
import javax.swing.UIManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.assertj.core.api.Assertions.*;

@ExtendWith(EdtTestExtension.class)
class ThemeManagerTest {
    /** Jasper Dark before the engine: FlatLaf Dark plus Jasper's former properties files. */
    private static final String[] TODAYS_DARK = {
        "Panel.background", "#21252b", "List.background", "#21252b", "List.foreground", "#abb2bf",
        "List.selectionBackground", "#5e7293", "List.selectionForeground", "#ffffff", "List.selectionInactiveBackground", "#5e7293",
        "TextField.background", "#282c34", "TextField.foreground", "#abb2bf", "TextField.selectionBackground", "#3e4451",
        "TextArea.background", "#282c34", "PasswordField.background", "#282c34", "ComboBox.background", "#333841",
        "ComboBox.buttonBackground", "#333841", "ComboBox.foreground", "#abb2bf", "ComboBox.selectionBackground", "#5e7293",
        "ComboBox.buttonArrowColor", "#abb2bf", "Button.background", "#3d424b", "Button.foreground", "#a7aebb",
        "Button.borderColor", "#464c55", "Button.hoverBackground", "#323842", "Button.pressedBackground", "#3e4451",
        "Button.focusedBackground", "#3d424b", "Button.default.background", "#6b80a1", "Button.default.foreground", "#ffffff",
        "Button.default.borderColor", "#6b80a1", "Button.default.hoverBackground", "#7b8dab", "Button.default.pressedBackground", "#5e7394",
        "Button.toolbar.hoverBackground", "#323842", "Button.toolbar.pressedBackground", "#3e4451", "Button.toolbar.selectedBackground", "#3e4451",
        "ToolBar.background", "#23262c", "MenuBar.background", "#23262c", "Menu.background", "#292c34", "MenuItem.background", "#292c34",
        "MenuItem.foreground", "#d3d7df", "MenuItem.acceleratorForeground", "#abb2bf", "PopupMenu.background", "#292c34",
        "PopupMenu.borderColor", "#353940", "TitlePane.background", "#23262c", "TitlePane.foreground", "#848c9b",
        "TitlePane.inactiveForeground", "#848c9b", "TabbedPane.background", "#23262c", "TabbedPane.selectedBackground", "#282a36",
        "TabbedPane.selectedForeground", "#d3d7df", "TabbedPane.hoverColor", "#323842", "TabbedPane.underlineColor", "#61afef",
        "Label.foreground", "#abb2bf", "Label.disabledForeground", "#7e8491", "Component.borderColor", "#353940",
        "Component.focusedBorderColor", "#61afef", "Component.focusColor", "#61afef", "Component.accentColor", "#61afef",
        "Component.linkColor", "#61afef", "Separator.foreground", "#353940", "ScrollBar.thumb", "#4d5262", "ScrollBar.track", "#2b2e37",
        "SplitPaneDivider.gripColor", "#d3d7df", "SplitPaneDivider.hoverColor", "#323842", "SplitPaneDivider.pressedColor", "#3e4451",
        "ToolTip.background", "#15161a", "ToolTip.foreground", "#d3d7df", "Tree.background", "#292c34",
        "Tree.selectionBackground", "#3e4451", "CheckBox.background", "#292c34", "ProgressBar.foreground", "#61afef",
        "Jasper.chromeForeground", "#d3d7df", "Jasper.configErrorForeground", "#ff858d", "Jasper.configSuccessForeground", "#a8c58d",
        "Jasper.configWarningForeground", "#e5c07b", "Jasper.findErrorBackground", "#5c3b3b", "Jasper.mutedForeground", "#848c9b",
        "Jasper.runningForeground", "#a8c58d", "Jasper.splitDivider", "#77808f", "Jasper.tabHoverBackground", "#2c3036",
        "Jasper.tabSelectedBackground", "#262a2f", "Jasper.tabSelectedForeground", "#d3d7df", "Jasper.tabUnderline", "#4a88c7",
        "Jasper.tabUnderlineInactive", "#747a80", "Jasper.titleBackground", "#23262c", "Jasper.titleForeground", "#848c9b",
        "Jasper.titleInactiveForeground", "#848c9b", "Jasper.titleSeparator", "#313439",
        // FlatLaf builds IntelliJ dark themes on its Darcula base; these keep FlatLaf Dark's values.
        "Button.disabledBackground", "#292c34", "Button.disabledText", "#8b929f", "CheckBox.disabledText", "#8b929f",
        "CheckBox.icon.background", "#34373e", "CheckBox.icon.disabledBackground", "#292c34",
        "CheckBox.icon.focusedBackground", "#61afef4d", "CheckBox.icon.hoverBackground", "#3b3e46",
        "CheckBox.icon.pressedBackground", "#42464f", "CheckBox.icon.selectedBackground", "#34373e",
        "CheckBox.icon[filled].checkmarkColor", "#34373e", "CheckBoxMenuItem.acceleratorForeground", "#abb2bf",
        "CheckBoxMenuItem.icon.checkmarkColor", "#afb2b9", "ComboBox.buttonEditableBackground", "#2f333b",
        "ComboBox.disabledBackground", "#292c34", "EditorPane.disabledBackground", "#292c34",
        "EditorPane.inactiveBackground", "#292c34", "FormattedTextField.disabledBackground", "#292c34",
        "FormattedTextField.inactiveBackground", "#292c34", "FormattedTextField.placeholderForeground", "#8b929f",
        "HelpButton.disabledBackground", "#292c34", "InternalFrame.inactiveTitleForeground", "#8b929f",
        "List.cellFocusColor", "#545c6e", "Menu.acceleratorForeground", "#abb2bf", "MenuBar.hoverBackground", "#323842",
        "MenuItem.underlineSelectionBackground", "#323842", "PasswordField.disabledBackground", "#292c34",
        "PasswordField.inactiveBackground", "#292c34", "PasswordField.placeholderForeground", "#8b929f",
        "PopupMenu.hoverScrollArrowBackground", "#343842", "ProgressBar.background", "#3b3f4b",
        "RadioButton.disabledText", "#8b929f", "RadioButtonMenuItem.acceleratorForeground", "#abb2bf",
        "ScrollPane.background", "#2b2e37", "Slider.tickColor", "#8b929f", "Spinner.buttonArrowColor", "#afb2b9",
        "Spinner.buttonBackground", "#25272e", "Spinner.disabledBackground", "#292c34",
        "TabbedPane.closeForeground", "#8b929f", "Table.cellFocusColor", "#545c6e", "TextArea.disabledBackground", "#292c34",
        "TextArea.inactiveBackground", "#292c34", "TextField.disabledBackground", "#292c34",
        "TextField.inactiveBackground", "#292c34", "TextField.placeholderForeground", "#8b929f",
        "TextPane.disabledBackground", "#292c34", "TextPane.inactiveBackground", "#292c34",
        "ToggleButton.disabledBackground", "#292c34", "ToggleButton.disabledText", "#8b929f",
        "Tree.selectionBorderColor", "#545c6e"};
    /** Geometry Jasper Dark keeps from FlatLaf Dark where FlatLaf's Darcula base differs. */
    private static final String[] TODAYS_DARK_NUMBERS = {
        "CheckBox.icon.focusWidth", "1", "Component.innerOutlineWidth", "1", "RadioButton.icon.centerDiameter", "8"};

    @Test void intellijLightInstallsTheClassicLightColours() throws Exception {
        LookAndFeel original = UIManager.getLookAndFeel();
        try {
            assertThat(ThemeManager.install(Theme.LIGHT)).isTrue();
            assertThat(UIManager.getLookAndFeel().getName()).isEqualTo("IntelliJ Light");
            assertThat(FlatLaf.isLafDark()).isFalse();
            assertColours(Map.ofEntries(
                Map.entry("Panel.background", "#f2f2f2"), Map.entry("TextField.background", "#ffffff"),
                Map.entry("List.background", "#ffffff"), Map.entry("List.selectionBackground", "#2675bf"),
                Map.entry("List.hoverBackground", "#edf5fc"), Map.entry("Button.background", "#ffffff"),
                Map.entry("Component.linkColor", "#2470b3"), Map.entry("ToolWindow.background", "#ffffff"),
                Map.entry("ToolWindow.Header.background", "#e2e6ec"), Map.entry("StatusBar.background", "#f2f2f2"),
                Map.entry("StatusBar.borderColor", "#d1d1d1"), Map.entry("MainToolbar.background", "#f2f2f2"),
                Map.entry("EditorTabs.underlineColor", "#4083c9"), Map.entry("EditorTabs.inactiveUnderlineColor", "#9ca7b8"),
                Map.entry("EditorTabs.hoverBackground", "#00000019"), Map.entry("EditorTabs.underlinedTabBackground", "#ffffff"),
                Map.entry("SearchEverywhere.Header.background", "#f2f2f2"), Map.entry("SearchEverywhere.Tab.selectedBackground", "#dfdfdf"),
                Map.entry("SearchEverywhere.Tab.selectedForeground", "#000000"), Map.entry("SearchEverywhere.SearchField.background", "#ffffff"),
                Map.entry("SearchEverywhere.SearchField.borderColor", "#c4c4c4"), Map.entry("SearchEverywhere.SearchField.infoForeground", "#808080"),
                Map.entry("SearchEverywhere.List.separatorColor", "#d9d9d9"), Map.entry("SearchEverywhere.List.separatorForeground", "#999999"),
                Map.entry("SearchEverywhere.Advertiser.background", "#f2f2f2"), Map.entry("SearchEverywhere.Advertiser.foreground", "#808080"),
                Map.entry("Popup.borderColor", "#ababab")));
            assertColours(Map.of("Jasper.titleBackground", "#f2f2f2", "Jasper.titleForeground", "#6e6e6e",
                "Jasper.titleInactiveForeground", "#8a8a8a", "Jasper.titleSeparator", "#d1d1d1",
                "Jasper.tabSelectedBackground", "#ffffff", "Jasper.tabUnderline", "#4083c9", "Jasper.findErrorBackground", "#ffcccc"));
            // IntelliJ's border classes are dropped, so FlatLaf keeps its own.
            assertThat(UIManager.getBorder("ScrollPane.border")).isInstanceOf(com.formdev.flatlaf.ui.FlatScrollPaneBorder.class);
            assertThat(UIManager.getFont("Label.font").getSize2D()).isEqualTo(12f);
        } finally { UIManager.setLookAndFeel(original); }
    }

    @Test void jasperDarkKeepsTheColoursItHadBeforeTheEngine() throws Exception {
        LookAndFeel original = UIManager.getLookAndFeel();
        try {
            assertThat(ThemeManager.install(Theme.DARK)).isTrue();
            assertThat(UIManager.getLookAndFeel().getName()).isEqualTo("Jasper Dark");
            assertThat(FlatLaf.isLafDark()).isTrue();
            var expected = new LinkedHashMap<String, String>();
            for (int i = 0; i < TODAYS_DARK.length; i += 2) expected.put(TODAYS_DARK[i], TODAYS_DARK[i + 1]);
            assertColours(expected);
            for (int i = 0; i < TODAYS_DARK_NUMBERS.length; i += 2)
                assertThat(String.valueOf(UIManager.get(TODAYS_DARK_NUMBERS[i]))).as(TODAYS_DARK_NUMBERS[i]).isEqualTo(TODAYS_DARK_NUMBERS[i + 1]);
            // FlatLaf copies the list selection into menus; white text keeps it readable.
            assertColours(Map.of("MenuItem.selectionBackground", "#5e7293", "MenuItem.selectionForeground", "#ffffff"));
            assertColours(Map.of("ToolWindow.background", "#21252b", "StatusBar.background", "#23262c",
                "SearchEverywhere.Tab.selectedBackground", "#3e4451", "List.hoverBackground", "#323842"));
        } finally { UIManager.setLookAndFeel(original); }
    }

    @Test void keysFlatLafSkipsAreRestoredWithoutReplacingItsOwn() throws Exception {
        LookAndFeel original = UIManager.getLookAndFeel();
        try {
            var resolved = new ThemeLoader(Map.of("fixture.json", """
                {"name": "Fixture", "ui": {"SearchEverywhere.Header.background": "#123456",
                  "EditorTabs.hoverBackground": "#00000019", "ComboBox.background": "#010101"}}""")::get, Map.of())
                .load("fixture.json");
            assertThat(ThemeManager.install(resolved, Color.WHITE)).isTrue();
            assertColours(Map.of("SearchEverywhere.Header.background", "#123456", "EditorTabs.hoverBackground", "#00000019"));
            // FlatLaf reads IntelliJ's combo keys its own way; its value stands.
            assertThat(hex(UIManager.getColor("ComboBox.background"))).isNotEqualTo("#010101");
        } finally { UIManager.setLookAndFeel(original); }
    }

    @Test void restoredAndDerivedKeysSurviveReinstallingTheSameLookAndFeel() throws Exception {
        LookAndFeel original = UIManager.getLookAndFeel();
        try {
            ThemeManager.install(Theme.LIGHT);
            LookAndFeel light = UIManager.getLookAndFeel();
            ThemeManager.install(Theme.DARK);
            // ThemeController rolls back like this when a later install fails.
            UIManager.setLookAndFeel(light);
            assertColours(Map.of("SearchEverywhere.Tab.selectedBackground", "#dfdfdf", "Jasper.titleBackground", "#f2f2f2",
                "Jasper.tabUnderline", "#4083c9"));
            assertThat(UIManager.getFont("Label.font").getSize2D()).isEqualTo(12f);
        } finally { UIManager.setLookAndFeel(original); }
    }

    @Test void switchingThemesLeavesNothingOfThePreviousOneBehind() throws Exception {
        LookAndFeel original = UIManager.getLookAndFeel();
        try {
            ThemeManager.install(Theme.LIGHT);
            assertThat(UIManager.getColor("Plugins.hoverBackground")).isNotNull();
            ThemeManager.install(Theme.DARK);
            assertThat(UIManager.getColor("Plugins.hoverBackground")).isNull();
            assertColours(Map.of("ToolWindow.background", "#21252b", "Jasper.titleBackground", "#23262c"));
        } finally { UIManager.setLookAndFeel(original); }
    }

    @Test void everyBuiltInThemeMatchesItsFile() {
        assertThat(ThemeManager.builtIns()).containsExactly(Theme.LIGHT, Theme.DARK);
        for (Theme theme : ThemeManager.builtIns()) {
            var resolved = ThemeManager.resolve(theme);
            assertThat(resolved.name()).isEqualTo(theme.name());
            assertThat(resolved.dark()).isEqualTo(theme.dark());
        }
        assertThat(Theme.LIGHT.id()).isEqualTo("intellij-light");
        assertThat(Theme.DARK.id()).isEqualTo("jasper-dark");
    }

    private static void assertColours(Map<String, String> expected) {
        expected.forEach((key, value) -> assertThat(hex(UIManager.getColor(key))).as(key).isEqualTo(value));
    }

    private static String hex(Color color) {
        if (color == null) return null;
        String rgb = String.format("#%06x", color.getRGB() & 0xffffff);
        return color.getAlpha() == 255 ? rgb : rgb + String.format("%02x", color.getAlpha());
    }
}
