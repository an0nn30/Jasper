package dev.moray.app;

import dev.moray.terminal.Palette;
import dev.moray.terminal.TerminalOptions;
import java.awt.Color;
import java.nio.file.Path;
import java.util.ArrayDeque;
import javax.swing.UIManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static dev.moray.app.DesktopTestSupport.*;
import static org.assertj.core.api.Assertions.assertThat;

class DarkPurpleThemeTest {
    @TempDir Path directory;
    @AfterEach void cleanup() throws Exception { closeOwners(); }

    @Test void missingConfigUsesPurpleAndTheNewBuiltinNeedsNoThemeFile() {
        var parsed = ConfigLoader.parse(directory.resolve("config.toml"), "", true);
        assertThat(parsed.snapshot().colors().theme()).isEqualTo("moray-dark-purple");
        assertThat(parsed.snapshot().colors().appearance()).isEqualTo(Appearance.SYSTEM);
        var selected = new ColorsConfig(Appearance.SYSTEM, "moray-dark-purple");
        assertThat(selected.custom()).isFalse();
        var loaded = new ThemeFiles(directory.resolve("absent")).refresh(selected, false);
        assertThat(loaded.diagnostics()).isEmpty();
        assertThat(loaded.palette().background()).isEqualTo(new Color(0x120c1c));
        try (var service = new ConfigService(directory.resolve("missing.toml"), true)) {
            assertThat(service.initialState().palette()).isEqualTo(loaded.palette());
        }
        assertThat(TerminalOptions.defaults().palette()).isEqualTo(loaded.palette());
    }

    @Test void systemAndManualAppearanceUsePurpleDarkAndTheExistingLightPalette() {
        var initial = ThemeState.defaults();
        assertThat(initial.resolve().palette().background()).isEqualTo(new Color(0x120c1c));
        var light = initial.systemChanged(BuiltinTheme.LIGHT);
        assertThat(light.resolve().palette()).isEqualTo(Palette.morayLight());
        assertThat(light.systemChanged(BuiltinTheme.DARK).resolve()).isEqualTo(initial.resolve());
        assertThat(light.choose(Appearance.DARK).resolve()).isEqualTo(initial.resolve());
        var selected = ConfigLoader.parse(directory.resolve("config.toml"),
            "[colors]\ntheme='moray-dark-purple'\n", true).snapshot().colors();
        assertThat(selected.appearance()).isEqualTo(Appearance.DARK);
    }

    @Test void explicitClassicSelectionSurvivesAndPairsWithLightUnderSystemAppearance() {
        var colors = new ColorsConfig(Appearance.SYSTEM, "moray-dark");
        var classic = ThemeState.defaults().configure(colors, Palette.morayDark());
        assertThat(classic.resolve().chrome().id()).isEqualTo("moray-dark");
        assertThat(classic.resolve().palette()).isEqualTo(Palette.morayDark());
        assertThat(classic.systemChanged(BuiltinTheme.LIGHT).resolve().palette()).isEqualTo(Palette.morayLight());
        assertThat(classic.systemChanged(BuiltinTheme.DARK).resolve().palette()).isEqualTo(Palette.morayDark());
    }

    @Test void realChromeAndPendingTerminalSurfacesSwitchTogetherWithoutLeakingClassicColors() throws Exception {
        edt(() -> {
            var themes = new ThemeController();
            var owner = content(launcher(new ArrayDeque<>()), themes);
            assertThat(owner.toolbar().getBackground()).isEqualTo(new Color(0x120c1c));
            assertThat(owner.currentPane().getBackground()).isEqualTo(new Color(0x120c1c));
            assertThat(owner.status().getBackground()).isEqualTo(new Color(0x120c1c));
            assertThat(UIManager.getColor("Moray.titleBackground")).isEqualTo(new Color(0x07050d));
            assertThat(UIManager.getColor("Moray.tabUnderline")).isEqualTo(new Color(0xc089ef));
            themes.systemChanged(BuiltinTheme.LIGHT);
            assertThat(owner.toolbar().getBackground()).isEqualTo(Palette.morayLight().background());
            themes.configure(new ColorsConfig(Appearance.SYSTEM, "moray-dark"), Palette.morayDark());
            themes.systemChanged(BuiltinTheme.DARK);
            assertThat(owner.toolbar().getBackground()).isEqualTo(Palette.morayDark().background());
            themes.configure(ColorsConfig.defaults(), TerminalOptions.defaults().palette());
            assertThat(owner.toolbar().getBackground()).isEqualTo(new Color(0x120c1c));
            assertThat(UIManager.getColor("Moray.titleBackground")).isEqualTo(new Color(0x07050d));
        });
    }

    @Test void defaultTerminalTextAndSemanticAnsiColorsStayReadableOnPurple() {
        Palette palette = TerminalOptions.defaults().palette();
        assertThat(palette.background()).isEqualTo(new Color(0x120c1c));
        assertThat(palette.cursor()).isEqualTo(Color.WHITE);
        assertThat(palette.selection()).isEqualTo(new Color(0x492b61));
        assertThat(palette.ansi()).doesNotHaveDuplicates();
        assertThat(WindowStatusBar.contrast(palette.foreground(), palette.background())).isGreaterThan(7);
        for (int index = 1; index < 16; index++) {
            assertThat(WindowStatusBar.contrast(palette.indexed(index), palette.background()))
                .as("ANSI %s", index).isGreaterThanOrEqualTo(4.5);
        }
        assertThat(palette.indexed(1).getRed()).isGreaterThan(palette.indexed(1).getGreen());
        assertThat(palette.indexed(2).getGreen()).isGreaterThan(palette.indexed(2).getRed());
        assertThat(palette.indexed(4).getBlue()).isGreaterThan(palette.indexed(4).getRed());
    }
}
