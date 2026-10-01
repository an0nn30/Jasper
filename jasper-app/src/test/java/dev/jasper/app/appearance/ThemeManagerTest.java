package dev.jasper.app.appearance;

import com.formdev.flatlaf.FlatLaf;
import dev.jasper.app.testsupport.EdtTestExtension;
import java.awt.Color;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.swing.LookAndFeel;
import javax.swing.UIManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

@ExtendWith(EdtTestExtension.class)
class ThemeManagerTest {
    /** Jasper Dark before the engine: FlatLaf Dark plus Jasper's former properties files. */
    private static final String[] TODAYS_DARK = {
        "Panel.background", "#21252a", "List.background", "#191c20", "List.foreground", "#d5dee5",
        "List.selectionBackground", "#5e7293", "List.selectionForeground", "#ffffff", "List.selectionInactiveBackground", "#5e7293",
        "TextField.background", "#25292e", "TextField.foreground", "#d5dee5", "TextField.selectionBackground", "#3e4451",
        "TextArea.background", "#25292e", "PasswordField.background", "#25292e", "ComboBox.background", "#333841",
        "ComboBox.buttonBackground", "#333841", "ComboBox.foreground", "#d5dee5", "ComboBox.selectionBackground", "#5e7293",
        "ComboBox.buttonArrowColor", "#d5dee5", "Button.background", "#3d424b", "Button.foreground", "#a7aebb",
        "Button.borderColor", "#464c55", "Button.hoverBackground", "#323942", "Button.pressedBackground", "#3e4451",
        "Button.focusedBackground", "#3d424b", "Button.default.background", "#6b80a1", "Button.default.foreground", "#ffffff",
        "Button.default.borderColor", "#6b80a1", "Button.default.hoverBackground", "#7b8dab", "Button.default.pressedBackground", "#5e7394",
        "Button.toolbar.hoverBackground", "#323942", "Button.toolbar.pressedBackground", "#3e4451", "Button.toolbar.selectedBackground", "#3e4451",
        "ToolBar.background", "#25292e", "MenuBar.background", "#25292e", "Menu.background", "#21252a", "MenuItem.background", "#21252a",
        "MenuItem.foreground", "#d5dee5", "MenuItem.acceleratorForeground", "#d5dee5", "PopupMenu.background", "#21252a",
        "PopupMenu.borderColor", "#343a42", "TitlePane.background", "#25292e", "TitlePane.foreground", "#a7b2bf",
        "TitlePane.inactiveForeground", "#a7b2bf", "TabbedPane.background", "#25292e", "TabbedPane.selectedBackground", "#191c20",
        "TabbedPane.selectedForeground", "#d5dee5", "TabbedPane.hoverColor", "#323942", "TabbedPane.underlineColor", "#8eb8d2",
        "Label.foreground", "#d5dee5", "Label.disabledForeground", "#7e8491", "Component.borderColor", "#343a42",
        "Component.focusedBorderColor", "#8eb8d2", "Component.focusColor", "#8eb8d2", "Component.accentColor", "#8eb8d2",
        "Component.linkColor", "#8eb8d2", "Separator.foreground", "#343a42", "ScrollBar.thumb", "#4d5262", "ScrollBar.track", "#2b2e37",
        "SplitPaneDivider.gripColor", "#d5dee5", "SplitPaneDivider.hoverColor", "#323942", "SplitPaneDivider.pressedColor", "#3e4451",
        "ToolTip.background", "#15161a", "ToolTip.foreground", "#d5dee5", "Tree.background", "#191c20",
        "Tree.selectionBackground", "#3e4451", "CheckBox.background", "#21252a", "ProgressBar.foreground", "#8eb8d2",
        "Jasper.chromeForeground", "#d5dee5", "Jasper.configErrorForeground", "#ff858d", "Jasper.configSuccessForeground", "#a8c58d",
        "Jasper.configWarningForeground", "#e5c07b", "Jasper.findErrorBackground", "#5c3b3b", "Jasper.mutedForeground", "#a7b2bf",
        "Jasper.runningForeground", "#a8c58d", "Jasper.splitDivider", "#77808f", "Jasper.tabHoverBackground", "#2c3036",
        "Jasper.tabSelectedBackground", "#293d51", "Jasper.tabSelectedForeground", "#d5dee5", "Jasper.tabUnderline", "#8eb8d2",
        "Jasper.tabUnderlineInactive", "#747a80", "Jasper.titleBackground", "#25292e", "Jasper.titleForeground", "#a7b2bf",
        "Jasper.titleInactiveForeground", "#a7b2bf", "Jasper.titleSeparator", "#343a42",
        // FlatLaf builds IntelliJ dark themes on its Darcula base; these keep FlatLaf Dark's values.
        "Button.disabledBackground", "#21252a", "Button.disabledText", "#8b929f", "CheckBox.disabledText", "#8b929f",
        "CheckBox.icon.background", "#34373e", "CheckBox.icon.disabledBackground", "#21252a",
        "CheckBox.icon.focusedBackground", "#61afef4d", "CheckBox.icon.hoverBackground", "#3b3e46",
        "CheckBox.icon.pressedBackground", "#42464f", "CheckBox.icon.selectedBackground", "#34373e",
        "CheckBox.icon[filled].checkmarkColor", "#34373e", "CheckBoxMenuItem.acceleratorForeground", "#d5dee5",
        "CheckBoxMenuItem.icon.checkmarkColor", "#afb2b9", "ComboBox.buttonEditableBackground", "#2f333b",
        "ComboBox.disabledBackground", "#21252a", "EditorPane.disabledBackground", "#21252a",
        "EditorPane.inactiveBackground", "#21252a", "FormattedTextField.disabledBackground", "#21252a",
        "FormattedTextField.inactiveBackground", "#21252a", "FormattedTextField.placeholderForeground", "#8b929f",
        "HelpButton.disabledBackground", "#21252a", "InternalFrame.inactiveTitleForeground", "#8b929f",
        "List.cellFocusColor", "#545c6e", "Menu.acceleratorForeground", "#d5dee5", "MenuBar.hoverBackground", "#323942",
        "MenuItem.underlineSelectionBackground", "#323942", "PasswordField.disabledBackground", "#21252a",
        "PasswordField.inactiveBackground", "#21252a", "PasswordField.placeholderForeground", "#8b929f",
        "PopupMenu.hoverScrollArrowBackground", "#343842", "ProgressBar.background", "#3b3f4b",
        "RadioButton.disabledText", "#8b929f", "RadioButtonMenuItem.acceleratorForeground", "#d5dee5",
        "ScrollPane.background", "#2b2e37", "Slider.tickColor", "#8b929f", "Spinner.buttonArrowColor", "#afb2b9",
        "Spinner.buttonBackground", "#25272e", "Spinner.disabledBackground", "#21252a",
        "TabbedPane.closeForeground", "#8b929f", "Table.cellFocusColor", "#545c6e", "TextArea.disabledBackground", "#21252a",
        "TextArea.inactiveBackground", "#21252a", "TextField.disabledBackground", "#21252a",
        "TextField.inactiveBackground", "#21252a", "TextField.placeholderForeground", "#8b929f",
        "TextPane.disabledBackground", "#21252a", "TextPane.inactiveBackground", "#21252a",
        "ToggleButton.disabledBackground", "#21252a", "ToggleButton.disabledText", "#8b929f",
        "Tree.selectionBorderColor", "#545c6e"};
    /** Geometry Jasper Dark keeps from FlatLaf Dark where FlatLaf's Darcula base differs. */
    private static final String[] TODAYS_DARK_NUMBERS = {
        "CheckBox.icon.focusWidth", "1", "Component.innerOutlineWidth", "1", "RadioButton.icon.centerDiameter", "8"};

    @Test void intellijLightInstallsTheClassicLightColours() throws Exception {
        LookAndFeel original = UIManager.getLookAndFeel();
        try {
            assertThat(ThemeManager.install(Theme.INTELLIJ_LIGHT)).isTrue();
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

    @Test void jasperDarkUsesGraphiteSurfacesAndRetainsControlContracts() throws Exception {
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
            assertColours(Map.of("ToolWindow.background", "#191c20", "Table.background", "#191c20", "StatusBar.background", "#25292e",
                "SearchEverywhere.Tab.selectedBackground", "#3e4451", "List.hoverBackground", "#323942"));
            assertThat(UIManager.getBoolean("TitlePane.centerTitle")).as("dialog titles are centred").isTrue();
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
            ThemeManager.install(Theme.INTELLIJ_LIGHT);
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
            ThemeManager.install(Theme.INTELLIJ_LIGHT);
            assertThat(UIManager.getColor("Plugins.hoverBackground")).isNotNull();
            ThemeManager.install(Theme.DARK);
            assertThat(UIManager.getColor("Plugins.hoverBackground")).isNull();
            assertColours(Map.of("ToolWindow.background", "#191c20", "Jasper.titleBackground", "#25292e"));
        } finally { UIManager.setLookAndFeel(original); }
    }

    @Test void everyBuiltInThemeGeneratesUnderItsOwnName() {
        assertThat(ThemeManager.builtIns()).containsExactly(Theme.INTELLIJ_LIGHT, Theme.DARK, Theme.DARCULA);
        for (Theme theme : ThemeManager.builtIns()) {
            GeneratedTheme generated = ThemeManager.generated(theme);
            assertThat(generated.name()).isEqualTo(theme.name());
            assertThat(generated.dark()).isEqualTo(theme.dark());
            assertThat(generated.terminal()).isEqualTo(theme.palette());
        }
        assertThat(Theme.INTELLIJ_LIGHT.id()).isEqualTo("intellij-light");
        assertThat(Theme.DARK.id()).isEqualTo("jasper-dark");
        assertThat(Theme.DARCULA).extracting(Theme::id, Theme::name, Theme::dark).containsExactly("darcula", "Darcula", true);
    }

    @Test void theVendoredIntellijThemesLoadWithoutWarnings() {
        for (String file : List.of("intellij/Light.theme.json", "intellij/darcula.theme.json"))
            assertThat(ThemeManager.loadBuiltIn(file).warnings()).as(file).isEmpty();
    }

    @Test void anInstalledThemeInstallsWithItsOwnColoursAndDerivedChromeKeys(@TempDir Path home) throws Exception {
        LookAndFeel original = UIManager.getLookAndFeel();
        try {
            ThemeFixtures.plugin(home.resolve("themes/fixture.jar"), "Fixture Dark", true, "#123456", "101820");
            Theme theme = ThemeCatalog.beside(home.resolve("config.toml")).scan().find("Fixture Dark").orElseThrow();
            assertThat(ThemeManager.install(theme)).isTrue();
            assertThat(UIManager.getLookAndFeel().getName()).isEqualTo("Fixture Dark");
            assertThat(UIManager.getColor("Panel.background")).isEqualTo(new Color(0x123456));
            assertThat(FlatLaf.isLafDark()).isTrue();
            assertThat(UIManager.getColor("Jasper.titleBackground")).isNotNull();
        } finally { UIManager.setLookAndFeel(original); }
    }

    private static void assertColours(Map<String, String> expected) {
        expected.forEach((key, value) -> assertThat(hex(UIManager.getColor(key))).as(key).isEqualTo(value));
    }

    @Test void aGeneratedThemeInstallsItsTokensAsTheLookAndFeel() throws Exception {
        LookAndFeel original = UIManager.getLookAndFeel();
        try {
            GeneratedTheme theme = ThemeGeneratorTest.generate(ThemeGeneratorTest.SEEDS);
            assertThat(ThemeManager.install(theme)).isTrue();
            assertThat(UIManager.getLookAndFeel().getName()).isEqualTo("Mocha");
            assertThat(FlatLaf.isLafDark()).isTrue();
            assertThat(UIManager.getColor("Panel.background")).isEqualTo(theme.tokens().get(Token.SURFACE));
            assertThat(UIManager.getColor("List.background")).isEqualTo(theme.tokens().get(Token.SURFACE_SUNKEN));
            assertThat(UIManager.getColor("Jasper.tabSelectedBackground")).isEqualTo(theme.tokens().get(Token.TAB_ACTIVE));
            assertThat(UIManager.getColor("EditorTabs.underlineColor")).as("a key FlatLaf skips is put back")
                .isEqualTo(theme.tokens().get(Token.TAB_UNDERLINE));
            assertThat(UIManager.getColor("Jasper.titleSearch.background")).as("ChromeKeys fills keys without a token").isNotNull();
            assertThat(UIManager.getInt("Component.arc")).isEqualTo(4);
            assertThat(UIManager.getFont("Label.font").getSize2D()).as("app defaults still apply").isEqualTo(12f);
        } finally { UIManager.setLookAndFeel(original); }
    }

    /** The 2026-10-01 spike: FlatLaf re-derives its controls from the base variables the template sets. */
    @Test void flatLafReDerivesUnsetControlsFromTheBaseVariables() throws Exception {
        LookAndFeel original = UIManager.getLookAndFeel();
        try {
            ThemeManager.install(ThemeGeneratorTest.generate(ThemeGeneratorTest.SEEDS));
            Color first = UIManager.getColor("Button.disabledSelectedBackground");
            ThemeManager.install(ThemeGeneratorTest.generate(ThemeGeneratorTest.SEEDS.replace("#1e1e2e", "#2e3440")));
            assertThat(UIManager.getColor("Button.disabledSelectedBackground")).isNotEqualTo(first);
        } finally { UIManager.setLookAndFeel(original); }
    }

    @Test void aThemesOwnKeysWinOverTheTemplate() throws Exception {
        LookAndFeel original = UIManager.getLookAndFeel();
        try {
            ThemeManager.install(ThemeGeneratorTest.generate(ThemeGeneratorTest.SEEDS + "[ui]\n\"Panel.background\" = \"#010203\"\n"));
            assertThat(hex(UIManager.getColor("Panel.background"))).isEqualTo("#010203");
        } finally { UIManager.setLookAndFeel(original); }
    }

    private static String hex(Color color) {
        if (color == null) return null;
        String rgb = String.format("#%06x", color.getRGB() & 0xffffff);
        return color.getAlpha() == 255 ? rgb : rgb + String.format("%02x", color.getAlpha());
    }
}
