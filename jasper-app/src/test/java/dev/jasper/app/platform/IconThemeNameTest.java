package dev.jasper.app.platform;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

class IconThemeNameTest {
    @TempDir Path config;

    @Test void xsettingsWinsOverFilesAndGsettings() throws Exception {
        ini("gtk-3.0", "gtk-icon-theme-name=Breeze");
        assertThat(IconThemeName.find(key -> key.equals("gnome.Net/IconThemeName") ? " Papirus " : null, config, () -> "'Yaru'"))
            .isEqualTo("Papirus");
    }

    @Test void settingsFilesComeNextGtk3BeforeGtk4() throws Exception {
        ini("gtk-4.0", "[Settings]\ngtk-icon-theme-name = \"Four\"");
        assertThat(IconThemeName.find(key -> null, config, () -> null)).isEqualTo("Four");
        ini("gtk-3.0", "[Settings]\n# comment\ngtk-icon-theme-name = Three");
        assertThat(IconThemeName.find(key -> "", config, () -> null)).isEqualTo("Three");
    }

    @Test void gsettingsOutputIsUnquoted() {
        assertThat(IconThemeName.find(key -> null, config, () -> "'Adwaita'\n")).isEqualTo("Adwaita");
    }

    @Test void unusableNamesAreIgnored() throws Exception {
        ini("gtk-3.0", "gtk-icon-theme-name=../../etc");
        assertThat(IconThemeName.find(key -> ".hidden", config, () -> "'a/b'")).isNull();
        assertThat(IconThemeName.find(key -> 7, config, () -> "  ")).isNull();
        assertThat(IconThemeName.valid("Adwaita")).isTrue();
        for (String bad : new String[]{null, "", " ", ".x", "a/b", "a\\b", "a\0b"}) assertThat(IconThemeName.valid(bad)).as(bad).isFalse();
    }

    @Test @DisabledOnOs(OS.WINDOWS) void commandsReturnOutputAndGiveUpOnTimeoutOrAbsence() {
        assertThat(IconThemeName.command(List.of("sh", "-c", "echo \"'Papirus'\""), Duration.ofSeconds(5))).isEqualTo("'Papirus'\n");
        assertThat(IconThemeName.command(List.of("sh", "-c", "sleep 5"), Duration.ofMillis(200))).isNull();
        assertThat(IconThemeName.command(List.of("sh", "-c", "exit 3"), Duration.ofSeconds(5))).isNull();
        assertThat(IconThemeName.command(List.of("jasper-no-such-binary"), Duration.ofSeconds(1))).isNull();
    }

    private void ini(String version, String text) throws Exception {
        Files.createDirectories(config.resolve(version));
        Files.writeString(config.resolve(version).resolve("settings.ini"), text);
    }
}
