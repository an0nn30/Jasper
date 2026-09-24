package dev.jasper.app.appearance;

import dev.jasper.app.testsupport.EdtTestExtension;
import java.awt.Color;
import java.util.List;
import javax.swing.UIDefaults;
import javax.swing.UIManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.assertj.core.api.Assertions.*;

@ExtendWith(EdtTestExtension.class)
class GtkDefaultsTest {
    static final List<String> KEYS = List.of("Jasper.titleBackground", "Jasper.paletteBackground", "Jasper.tabSelectedBackground",
        "Jasper.titleForeground", "Jasper.chromeForeground", "Jasper.tabSelectedForeground", "Jasper.paletteForeground",
        "Jasper.titleInactiveForeground", "Jasper.mutedForeground", "Jasper.paletteMutedForeground",
        "Jasper.titleSeparator", "Jasper.splitDivider", "Component.borderColor", "Jasper.paletteBorder",
        "Jasper.paletteAccent", "Component.focusedBorderColor", "Jasper.paletteSelectionBackground",
        "Jasper.paletteSelectionForeground", "Jasper.runningForeground", "Actions.Red", "Actions.Yellow", "Actions.Green",
        "Jasper.configSuccessForeground", "Jasper.configWarningForeground", "Jasper.configErrorForeground");

    @Test void lightSamplesBecomeJasperAliasesAndLightStatusColors() {
        var defaults = new UIDefaults();
        GtkDefaults.decorate(defaults, GtkTestThemes.LIGHT::get);
        assertThat(defaults.getBoolean("Jasper.nativeChrome")).isTrue();
        assertThat(defaults.getBoolean("Jasper.gtk")).isTrue();
        assertThat(defaults.getBoolean("Jasper.retro")).isFalse();
        assertThat(defaults.getColor("Jasper.titleBackground")).isEqualTo(new Color(0xf6f5f4));
        assertThat(defaults.getColor("Jasper.chromeForeground")).isEqualTo(new Color(0x2e3436));
        assertThat(defaults.getColor("Jasper.paletteSelectionBackground")).isEqualTo(new Color(0x3584e4));
        assertThat(defaults.getColor("Jasper.paletteSelectionForeground")).isEqualTo(Color.WHITE);
        Color muted = defaults.getColor("Jasper.mutedForeground");
        assertThat(muted.getRed()).isStrictlyBetween(0x2e, 0xf6);
        assertThat(defaults.getColor("Jasper.configErrorForeground")).isEqualTo(new Color(0xb42332));
        assertThat(defaults.getColor(GtkPalette.BACKGROUND)).isEqualTo(Color.WHITE);
        assertThat(defaults.getColor(GtkPalette.CARET)).isEqualTo(Color.BLACK);
    }

    @Test void darkSamplesSelectDarkStatusColors() {
        var defaults = new UIDefaults();
        GtkDefaults.decorate(defaults, GtkTestThemes.DARK::get);
        assertThat(defaults.getColor("Jasper.configErrorForeground")).isEqualTo(new Color(0xff858d));
        assertThat(defaults.getColor("Actions.Green")).isEqualTo(new Color(0x499c54));
    }

    @Test void nullSamplesStillDefineEveryKey() {
        var defaults = new UIDefaults();
        GtkDefaults.decorate(defaults, key -> null);
        for (String key : KEYS) assertThat(defaults.getColor(key)).as(key).isNotNull();
        assertThat(defaults.get(GtkPalette.BACKGROUND)).isNull();
    }

    @Test void installEitherSucceedsOrExplainsWhyGtkIsUnavailable() {
        var before = UIManager.getLookAndFeel();
        try {
            assertThat(GtkDefaults.install()).isTrue();
            assertThat(UIManager.getLookAndFeel().getClass().getName()).isEqualTo(GtkDefaults.LOOK_AND_FEEL);
            assertThat(UIManager.getBoolean("Jasper.gtk")).isTrue();
        } catch (IllegalStateException unavailable) {
            assertThat(unavailable).hasMessageContaining("GTK");
            assertThat(UIManager.getLookAndFeel()).isSameAs(before);
        } finally { new ThemeController(); }
    }

    @Test void macOsAndWindowsNeverInstallGtk() {
        String os = System.getProperty("os.name");
        org.junit.jupiter.api.Assumptions.assumeTrue(os.startsWith("Mac") || os.startsWith("Windows"));
        assertThatIllegalStateException().isThrownBy(GtkDefaults::install).withMessageContaining("GTK");
    }
}
