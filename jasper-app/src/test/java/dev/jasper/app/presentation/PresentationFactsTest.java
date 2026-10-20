package dev.jasper.app.presentation;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Spec 4.4: a provider's digest covers its kit, its themes and its fonts, in declared order, and names its first problem. */
class PresentationFactsTest {
    static KitParser.Files files(Map<String, byte[]> files) {
        return (path, max) -> Optional.ofNullable(files.get(path)).map(bytes -> java.util.Arrays.copyOf(bytes, Math.min(bytes.length, max + 1)));
    }

    @Test void theDigestCoversTheKitThenEachThemeThenEachFontInDeclaredOrder() throws Exception {
        var jar = new java.util.HashMap<>(KitParserTest.fonts());
        byte[] theme = jasperTheme();
        byte[] kit = KitParserTest.FULL.getBytes(StandardCharsets.UTF_8);
        jar.put("presentation/look.kit.toml", kit);
        jar.put("themes/look.json", theme);
        var facts = PresentationFacts.read(new PresentationFacts.Source("dev.example.look", "presentation/look.kit.toml", files(jar),
            List.of("themes/look.json")));
        assertThat(facts.problem()).isEmpty();
        var digest = MessageDigest.getInstance("SHA-256");
        digest.update(kit); digest.update(theme);
        for (String font : List.of("ibm-plex-sans-regular", "ibm-plex-sans-medium", "ibm-plex-sans-semibold", "ibm-plex-mono-regular", "ibm-plex-mono-medium"))
            digest.update(jar.get("presentation/fonts/" + font + ".ttf"));
        assertThat(facts.digest()).isEqualTo(HexFormat.of().formatHex(digest.digest()));
        assertThat(facts.parsed().orElseThrow().defaults().value(dev.jasper.app.config.CoreSettings.UI_THEME_DARK)).isEqualTo("look");
    }

    @Test void aMissingKitAnInvalidKitAndAThemeThatDoesNotLoadAreReported() {
        var missing = PresentationFacts.read(new PresentationFacts.Source("dev.example.look", "presentation/look.kit.toml", files(Map.of()), List.of()));
        assertThat(missing.problem()).contains("presentation: presentation/look.kit.toml is not in the plugin's jars");
        var invalid = PresentationFacts.read(new PresentationFacts.Source("dev.example.look", "look.kit.toml",
            files(Map.of("look.kit.toml", "kit = 1\nbase = \"default\"\n[metrics]\nrow_height = 40\n".getBytes(StandardCharsets.UTF_8))), List.of()));
        assertThat(invalid.problem()).contains("[metrics].row_height: 40 is outside 18-28");
        assertThat(invalid.parsed()).isEmpty();
        var brokenTheme = PresentationFacts.read(new PresentationFacts.Source("dev.example.look", "look.kit.toml", files(Map.of(
            "look.kit.toml", "kit = 1\nbase = \"default\"\n[defaults]\n\"ui.theme.dark\" = \"look\"\n".getBytes(StandardCharsets.UTF_8),
            "themes/look.json", "{ not a theme".getBytes(StandardCharsets.UTF_8))), List.of("themes/look.json")));
        assertThat(brokenTheme.problem()).as("a theme that does not load is not a kit problem").isEmpty();
        assertThat(brokenTheme.parsed().orElseThrow().dropped()).containsExactly("ui.theme.dark");
    }

    /** A valid theme file with both variants: Slate Cobalt's own text. */
    static byte[] jasperTheme() throws java.io.IOException {
        try (var in = dev.jasper.ui.theme.JasperTheme.class.getResourceAsStream("/dev/jasper/ui/theme/slate-cobalt.json")) { return in.readAllBytes(); }
    }
}
