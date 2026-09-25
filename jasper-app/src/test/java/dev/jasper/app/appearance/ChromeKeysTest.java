package dev.jasper.app.appearance;

import java.awt.Color;
import java.util.List;
import javax.swing.UIDefaults;
import javax.swing.plaf.ColorUIResource;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ChromeKeysTest {
    private static final Color TERMINAL = new Color(0xfafafa);

    /** Only keys FlatLaf itself always defines, with light values. */
    private static UIDefaults flatLafOnly() {
        var d = new UIDefaults();
        put(d, "Label.foreground", 0x000000); put(d, "Label.disabledForeground", 0x8c8c8c);
        put(d, "TitlePane.background", 0xf2f2f2); put(d, "TitlePane.foreground", 0x6e6e6e);
        put(d, "TitlePane.inactiveForeground", 0x999999); put(d, "Separator.foreground", 0xd1d1d1);
        put(d, "Panel.background", 0xf2f2f2); put(d, "TabbedPane.hoverColor", 0xd9d9d9);
        put(d, "TabbedPane.underlineColor", 0x4083c9); put(d, "TabbedPane.inactiveUnderlineColor", 0x9ca7b8);
        put(d, "TextField.background", 0xffffff); put(d, "Actions.Red", 0xdb5860);
        put(d, "Actions.Green", 0x59a869); put(d, "Actions.Yellow", 0xeda200);
        put(d, "Component.borderColor", 0xc4c4c4); put(d, "Component.focusedBorderColor", 0x87afda);
        put(d, "List.selectionBackground", 0x2675bf); put(d, "List.selectionForeground", 0xffffff);
        return d;
    }

    private static void put(UIDefaults d, String key, int rgb) { d.put(key, new ColorUIResource(rgb)); }

    @Test void flatLafsOwnKeysAreEnoughForEveryChromeKey() {
        var defaults = flatLafOnly();
        var added = ChromeKeys.derive(defaults, TERMINAL);
        assertThat(added).containsOnlyKeys(ChromeKeys.KEYS);
        for (String key : ChromeKeys.KEYS) assertThat(defaults.getColor(key)).as(key).isNotNull();
        assertThat(defaults.getColor("Jasper.titleBackground")).isEqualTo(new Color(0xf2f2f2));
        assertThat(defaults.getColor("Jasper.titleSeparator")).isEqualTo(new Color(0xd1d1d1));
        assertThat(defaults.getColor("Jasper.chromeForeground")).isEqualTo(new Color(0x000000));
        assertThat(defaults.getColor("Jasper.tabSelectedBackground")).isEqualTo(new Color(0xf2f2f2));
        assertThat(defaults.getColor("Jasper.tabHoverBackground")).isEqualTo(new Color(0xd9d9d9));
        assertThat(defaults.getColor("Jasper.tabUnderline")).isEqualTo(new Color(0x4083c9));
        assertThat(defaults.getColor("Jasper.findErrorBackground")).isEqualTo(ChromeKeys.mix(new Color(0xdb5860), Color.WHITE, .12));
        assertThat(defaults.getColor("Jasper.paletteSelectionBackground")).isEqualTo(new Color(0x2675bf));
    }

    @Test void intellijKeysWinOverFlatLafFallbacks() {
        var defaults = flatLafOnly();
        put(defaults, "MainToolbar.background", 0xeeeeee); put(defaults, "MainToolbar.foreground", 0x111111);
        put(defaults, "Borders.color", 0xcccccc); put(defaults, "Label.infoForeground", 0x707070);
        put(defaults, "EditorTabs.underlinedTabBackground", 0xffffff); put(defaults, "EditorTabs.underlinedTabForeground", 0x222222);
        defaults.put("EditorTabs.hoverBackground", new ColorUIResource(new Color(0, 0, 0, 0x19)));
        put(defaults, "EditorTabs.underlineColor", 0x3574f0); put(defaults, "EditorTabs.inactiveUnderlineColor", 0xa0a0a0);
        put(defaults, "SearchField.errorBackground", 0xffcccc); put(defaults, "StatusBar.background", 0xf7f7f7);
        ChromeKeys.derive(defaults, TERMINAL);
        assertThat(defaults.getColor("Jasper.titleBackground")).isEqualTo(new Color(0xeeeeee));
        assertThat(defaults.getColor("Jasper.chromeForeground")).isEqualTo(new Color(0x111111));
        assertThat(defaults.getColor("Jasper.titleSeparator")).isEqualTo(new Color(0xcccccc));
        assertThat(defaults.getColor("Jasper.mutedForeground")).isEqualTo(new Color(0x707070));
        assertThat(defaults.getColor("Jasper.tabSelectedBackground")).isEqualTo(Color.WHITE);
        assertThat(defaults.getColor("Jasper.tabSelectedForeground")).isEqualTo(new Color(0x222222));
        assertThat(defaults.getColor("Jasper.tabHoverBackground")).isEqualTo(new Color(0, 0, 0, 0x19));
        assertThat(defaults.getColor("Jasper.tabUnderline")).isEqualTo(new Color(0x3574f0));
        assertThat(defaults.getColor("Jasper.tabUnderlineInactive")).isEqualTo(new Color(0xa0a0a0));
        assertThat(defaults.getColor("Jasper.findErrorBackground")).isEqualTo(new Color(0xffcccc));
    }

    @Test void aKeyTheThemeSetsIsNeverReplaced() {
        var defaults = flatLafOnly();
        put(defaults, "Jasper.titleBackground", 0x123456);
        put(defaults, "Jasper.configErrorForeground", 0xffeeee);
        var added = ChromeKeys.derive(defaults, TERMINAL);
        assertThat(defaults.getColor("Jasper.titleBackground")).isEqualTo(new Color(0x123456));
        // A theme-set colour is not adjusted, even below the floor.
        assertThat(defaults.getColor("Jasper.configErrorForeground")).isEqualTo(new Color(0xffeeee));
        assertThat(added).doesNotContainKeys("Jasper.titleBackground", "Jasper.configErrorForeground");
    }

    @Test void derivedTextAndStrokesMeetJaspersContrastFloor() {
        var defaults = flatLafOnly();
        ChromeKeys.derive(defaults, TERMINAL);
        Color title = defaults.getColor("Jasper.titleBackground");
        Color status = defaults.getColor("Panel.background");
        assertThat(defaults.getColor("Jasper.titleInactiveForeground")).isNotEqualTo(new Color(0x999999));
        assertThat(ChromeKeys.contrast(defaults.getColor("Jasper.titleInactiveForeground"), title)).isGreaterThanOrEqualTo(3);
        assertThat(ChromeKeys.contrast(defaults.getColor("Jasper.mutedForeground"), title)).isGreaterThanOrEqualTo(3);
        assertThat(ChromeKeys.contrast(defaults.getColor("Jasper.splitDivider"), TERMINAL)).isGreaterThanOrEqualTo(3);
        assertThat(ChromeKeys.contrast(defaults.getColor("Jasper.runningForeground"), status)).isGreaterThanOrEqualTo(3);
        for (String key : List.of("Jasper.configSuccessForeground", "Jasper.configWarningForeground", "Jasper.configErrorForeground"))
            assertThat(ChromeKeys.contrast(defaults.getColor(key), status)).as(key).isGreaterThanOrEqualTo(4.5);
    }

    @Test void aColourThatAlreadyMeetsTheFloorIsKept() {
        assertThat(ChromeKeys.readable(new Color(0x333333), Color.WHITE, 4.5, Color.BLACK)).isEqualTo(new Color(0x333333));
        Color moved = ChromeKeys.readable(new Color(0xdddddd), Color.WHITE, 3, Color.BLACK);
        assertThat(ChromeKeys.contrast(moved, Color.WHITE)).isGreaterThanOrEqualTo(3);
    }

    @Test void aThemeMissingAFlatLafKeyFailsNamingIt() {
        var defaults = flatLafOnly();
        defaults.remove("TitlePane.background");
        assertThatIllegalStateException().isThrownBy(() -> ChromeKeys.derive(defaults, TERMINAL))
            .withMessageContaining("TitlePane.background");
    }
}
