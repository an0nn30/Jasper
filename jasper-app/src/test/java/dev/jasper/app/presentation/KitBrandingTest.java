package dev.jasper.app.presentation;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.app.testsupport.BrandingFixtures;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Spec 5.1 and 5.9: the [branding] table, every way it can be invalid, the digest and the presentation's branding. */
class KitBrandingTest {
    static final String HEAD = "kit = 1\nbase = \"default\"\n";

    /** The fixture branding under the kit's folder, "presentation/". */
    static Map<String, byte[]> files() { return new HashMap<>(BrandingFixtures.files("presentation/")); }

    static KitParser.Parsed parse(String text, Map<String, byte[]> files) throws KitParser.Invalid {
        return KitParser.parse(text.getBytes(StandardCharsets.UTF_8), KitParserTest.context(files, Map.of()));
    }

    static String problem(String text, Map<String, byte[]> files) {
        try { parse(text, files); throw new AssertionError("valid: " + text); }
        catch (KitParser.Invalid invalid) { return invalid.getMessage(); }
    }

    static Map<String, byte[]> with(String path, byte[] bytes) {
        var files = files();
        if (bytes == null) files.remove(path); else files.put(path, bytes);
        return files;
    }

    @Test void aKitWithoutBrandingKeepsJaspersIconAndShowsNoSplash() throws Exception {
        assertThat(parse(HEAD, files()).branding()).isSameAs(Branding.NONE);
    }

    @Test void theBrandingTableGivesTheProductTheIconsBySizeAndTheArt() throws Exception {
        var files = files();
        var branding = parse(HEAD + BrandingFixtures.TABLE, files).branding();
        assertThat(branding.present()).isTrue();
        assertThat(branding.product()).isEqualTo("JasperOps");
        for (int size : Branding.SIZES) assertThat(branding.icon(size)).isEqualTo(files.get("presentation/icons/icon-" + size + ".png"));
        assertThat(branding.splashArt()).isEqualTo(files.get("presentation/splash/art.svg"));
    }

    /** One invalid branding: the kit text, the jar's files and the one message it gives. */
    record Case(String kit, Map<String, byte[]> files, String message) { }

    @Test void everyInvalidBrandingGivesOneMessageNamingTheKey() {
        String table = BrandingFixtures.TABLE;
        String art = "presentation/splash/art.svg", icon48 = "presentation/icons/icon-48.png";
        var cases = List.of(
            new Case(HEAD + table + "logo = \"x\"\n", files(), "[branding].logo: unknown key"),
            new Case(HEAD + "branding = 1\n", files(), "branding: expected a table"),
            new Case(HEAD + "[branding]\nproduct = \"JasperOps\"\n", files(), "[branding].icons: missing"),
            new Case(HEAD + table.replace("product = \"JasperOps\"\n", ""), files(), "[branding].product: missing"),
            new Case(HEAD + table.replace("\"JasperOps\"", "7"), files(), "[branding].product: expected a string"),
            new Case(HEAD + table.replace("JasperOps", " "), files(), "[branding].product: expected a name of 1 to 32 characters"),
            new Case(HEAD + table.replace("JasperOps", "x".repeat(33)), files(), "[branding].product: expected a name of 1 to 32 characters"),
            new Case(HEAD + table.replace("icons = \"icons\"", "icons = \"../icons\""), files(),
                "[branding].icons: ../icons must be a lowercase path inside the kit's folder, without . or .. segments"),
            new Case(HEAD + table, with(icon48, null), "[branding].icons: icons/icon-48.png is not in the plugin's jars"),
            new Case(HEAD + table, with(icon48, BrandingFixtures.png(64, 64)), "[branding].icons: icons/icon-48.png is 64 × 64 pixels, not 48 × 48"),
            new Case(HEAD + table, with(icon48, "GIF89a not a png".getBytes(StandardCharsets.UTF_8)), "[branding].icons: icons/icon-48.png is not a PNG file"),
            new Case(HEAD + table, with("presentation/icons/icon-1024.png", new byte[KitParser.MAX_ICON_BYTES + 1]),
                "[branding].icons: icons/icon-1024.png is larger than 512 KiB"),
            new Case(HEAD + table.replace("splash_art = \"splash/art.svg\"\n", ""), files(), "[branding].splash_art: missing"),
            new Case(HEAD + table.replace("splash/art.svg", "../art.svg"), files(),
                "[branding].splash_art: ../art.svg must be a lowercase path inside the kit's folder, without . or .. segments"),
            new Case(HEAD + table.replace("splash/art.svg", "splash/art.png"), files(), "[branding].splash_art: splash/art.png is not an .svg file"),
            new Case(HEAD + table.replace("splash/art.svg", "splash/missing.svg"), files(),
                "[branding].splash_art: splash/missing.svg is not in the plugin's jars"),
            new Case(HEAD + table, with(art, new byte[KitParser.MAX_ART_BYTES + 1]), "[branding].splash_art: splash/art.svg is larger than 256 KiB"),
            new Case(HEAD + table, with(art, "<html/>".getBytes(StandardCharsets.UTF_8)),
                "[branding].splash_art: splash/art.svg is not an SVG Jasper can draw: Not SVG"),
            new Case(HEAD + table, with(art, "<svg xmlns=\"http://www.w3.org/2000/svg\"><script/></svg>".getBytes(StandardCharsets.UTF_8)),
                "[branding].splash_art: splash/art.svg is not an SVG Jasper can draw: Active SVG denied"));
        for (Case item : cases) assertThat(problem(item.kit(), item.files())).as(item.message()).isEqualTo(item.message());
    }

    /** Rulings 5: a changed branding file is a changed look, so it gets a fresh try. */
    @Test void theDigestCoversTheBrandingFilesAfterTheKit() throws Exception {
        var jar = files();
        byte[] kit = (HEAD + BrandingFixtures.TABLE).getBytes(StandardCharsets.UTF_8);
        jar.put("presentation/look.kit.toml", kit);
        var source = new PresentationFacts.Source("dev.example.look", "presentation/look.kit.toml", PresentationFactsTest.files(jar), List.of());
        var facts = PresentationFacts.read(source);
        assertThat(facts.problem()).isEmpty();
        var digest = MessageDigest.getInstance("SHA-256");
        digest.update(kit);
        for (int size : Branding.SIZES) digest.update(jar.get("presentation/icons/icon-" + size + ".png"));
        digest.update(jar.get("presentation/splash/art.svg"));
        assertThat(facts.digest()).isEqualTo(HexFormat.of().formatHex(digest.digest()));
        jar.put("presentation/icons/icon-16.png", BrandingFixtures.png(16, 16, 0xFF000000));
        var changed = PresentationFacts.read(new PresentationFacts.Source("dev.example.look", "presentation/look.kit.toml",
            PresentationFactsTest.files(jar), List.of()));
        assertThat(changed.digest()).as("a changed icon").isNotEqualTo(facts.digest());
    }

    /** Spec 9.1 and 4.7: the presentation carries its kit's branding; the default look and the fallback have none. */
    @Test void thePresentationCarriesTheBrandingAndTheFallbackDropsIt() throws Exception {
        var parsed = parse(HEAD + BrandingFixtures.TABLE, files());
        var look = Presentation.of(new Presentation.Provider("dev.example.look", "Example", "0.1.0", "d"), parsed, ProjectionsTest.MAC);
        assertThat(look.branding()).isEqualTo(parsed.branding());
        assertThat(look.fallback(ProjectionsTest.MAC).branding()).isSameAs(Branding.NONE);
        assertThat(Presentation.standard(ProjectionsTest.MAC).branding()).isSameAs(Branding.NONE);
    }
}
