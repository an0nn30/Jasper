package dev.jasper.app.themeimport;

import java.nio.charset.StandardCharsets;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;

/** Bundled IntelliJ input parents, never runtime catalog entries. */
final class ThemeParents {
    private ThemeParents() {}
    static final Map<String, String> FILES = Map.ofEntries(
        Map.entry("IntelliJ Light", "Light.theme.json"),
        Map.entry("JetBrainsLightTheme", "Light.theme.json"),
        Map.entry("IntelliJ", "intellijlaf.theme.json"),
        Map.entry("Darcula", "darcula.theme.json"),
        Map.entry("ExperimentalDark", "expUI/expUI_dark.theme.json"),
        Map.entry("ExperimentalLight", "expUI/expUI_light.theme.json"),
        Map.entry("ExperimentalLightWithLightHeader", "expUI/expUI_light_with_light_header.theme.json"),
        Map.entry("Islands Dark", "islands/ManyIslandsDark.theme.json"),
        Map.entry("Islands Light", "islands/ManyIslandsLight.theme.json"),
        Map.entry("Islands Darcula", "islands/ManyIslandsDarcula.theme.json"),
        Map.entry("JetBrainsHighContrastTheme", "islands/HighContrast.theme.json"));

    static String read(String file) {
        try (var in = ThemeParents.class.getResourceAsStream("/dev/jasper/app/themeimport/intellij/" + file)) {
            return in == null ? null : new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException failure) { throw new UncheckedIOException(failure); }
    }
    static ThemeLoader.Resolved load(String file) { return new ThemeLoader(ThemeParents::read, FILES).load(file); }
    static ThemeLoader.Resolved load(String label, String json) {
        return new ThemeLoader(file -> file.equals(label) ? json : read(file), FILES).load(label);
    }
}
