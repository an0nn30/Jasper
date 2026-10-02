package dev.jasper.app.themeimport;
import dev.jasper.app.appearance.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class IntellijThemesTest {
    private static final String BASE = "dev/jasper/app/themeimport/intellij/";
    private static final Map<String, String> PINNED = Map.ofEntries(
        Map.entry("Light.theme.json", "429151594b8a013938c3c66dd3a5121702182c028ad87ae1e68bc4243cc1c68d"),
        Map.entry("intellijlaf.theme.json", "4450e2a85381b7b644c73bc9ec21f898aa55b184fc80f109a43ae9ff67af21ba"),
        Map.entry("darcula.theme.json", "858efbd6afdb12f26b4757c4ff7b1bccb8bdefc286797fa6344769496b6c59cb"),
        Map.entry("expUI/expUI_dark.theme.json", "aeb714d70acebca4b37a3c5e7926294bfcd22f33dd6658f05eec367c55c376d1"),
        Map.entry("expUI/expUI_light.theme.json", "230fa169774a00145002fb6804283d8cc47f7c60812b90aae97d5625e6a7ebbb"),
        Map.entry("expUI/expUI_light_with_light_header.theme.json", "6c699a4b0826be6f53cb40c3fd0bf767429e70e5434d4119957e69eb48a8efe5"),
        Map.entry("islands/ManyIslandsDark.theme.json", "5c16ff76a0cd12154517c0b8fde9da7a849a7fd7f96bef71470e34c3c98e4aa3"),
        Map.entry("islands/ManyIslandsLight.theme.json", "1fa3268e023862b0357b52cd5a6e8ccd288be6dbce5dda9da4f654829ef14acd"),
        Map.entry("islands/ManyIslandsDarcula.theme.json", "f486326da9133aa5ad8b73e1568ce5ffd9fa1f1dc2cc0f3a895b88f960bf7b75"),
        Map.entry("islands/HighContrast.theme.json", "9337b64932cd06ecf63647c70a11a5c5454e10176abb9c3f69e927cd696418c3"),
        Map.entry("DefaultColorSchemesManager.xml", "e8eef86e79a25de2ab827c6535280019c1428afe7ff4688af94a7a50e3c9ea08"),
        Map.entry("Light.xml", "ea2af41e12491f0681b4b5b3dcafe84245a2b56805f8d991b65df73c1a4b2dbb"),
        Map.entry("expUI/expUI_lightScheme.xml", "4a8d24f993e3001204979eaf714c97ad74d53074932249290890ef53d354adaa"),
        Map.entry("expUI/expUI_darkScheme.xml", "4777f0d8a7feac1e265c3cd74b96a6b74286844a0904c4a8cd2084bcbec7c87c"),
        Map.entry("expUI/expUI_darculaContrastScheme.xml", "a33973beeaadcaa00adb29d75ad937a3350a9b6361f9a5f353a66fcecb81232d"),
        Map.entry("islands/highContrastScheme.xml", "3a125a2b71ea750bcd8c0e7970b341b5def460706d027edc560b3e1cf786f25d"),
        Map.entry("islands/IslandSchemeDark.xml", "d8c9a27c9f349e3616cfb18f0bbcebef0ac25cea88d3f946aaf117335ff08cab"));
    private static final Map<String, String> SOURCES = Map.ofEntries(
        Map.entry("Light.theme.json", "platform/platform-resources/src/themes/Light.theme.json"),
        Map.entry("intellijlaf.theme.json", "platform/platform-resources/src/themes/intellijlaf.theme.json"),
        Map.entry("darcula.theme.json", "platform/platform-resources/src/themes/darcula.theme.json"),
        Map.entry("expUI/expUI_dark.theme.json", "platform/platform-resources/src/themes/expUI/expUI_dark.theme.json"),
        Map.entry("expUI/expUI_light.theme.json", "platform/platform-resources/src/themes/expUI/expUI_light.theme.json"),
        Map.entry("expUI/expUI_light_with_light_header.theme.json", "platform/platform-resources/src/themes/expUI/expUI_light_with_light_header.theme.json"),
        Map.entry("islands/ManyIslandsDark.theme.json", "platform/platform-resources/src/themes/islands/ManyIslandsDark.theme.json"),
        Map.entry("islands/ManyIslandsLight.theme.json", "platform/platform-resources/src/themes/islands/ManyIslandsLight.theme.json"),
        Map.entry("islands/ManyIslandsDarcula.theme.json", "platform/platform-resources/src/themes/islands/ManyIslandsDarcula.theme.json"),
        Map.entry("islands/HighContrast.theme.json", "platform/platform-resources/src/themes/islands/HighContrast.theme.json"),
        Map.entry("DefaultColorSchemesManager.xml", "platform/platform-resources/src/DefaultColorSchemesManager.xml"),
        Map.entry("Light.xml", "platform/platform-resources/src/themes/Light.xml"),
        Map.entry("expUI/expUI_lightScheme.xml", "platform/platform-resources/src/themes/expUI/expUI_lightScheme.xml"),
        Map.entry("expUI/expUI_darkScheme.xml", "platform/platform-resources/src/themes/expUI/expUI_darkScheme.xml"),
        Map.entry("expUI/expUI_darculaContrastScheme.xml", "platform/platform-resources/src/themes/expUI/expUI_darculaContrastScheme.xml"),
        Map.entry("islands/highContrastScheme.xml", "platform/platform-resources/src/themes/islands/highContrastScheme.xml"),
        Map.entry("islands/IslandSchemeDark.xml", "platform/platform-resources/src/themes/islands/IslandSchemeDark.xml"));
    /** Each theme id IntelliJ registers at the pinned commit, with whether it is dark. */
    private static final Map<String, Boolean> PARENTS = Map.of(
        "Darcula", true, "IntelliJ", false, "JetBrainsLightTheme", false,
        "ExperimentalDark", true, "ExperimentalLight", false, "ExperimentalLightWithLightHeader", false,
        "Islands Dark", true, "Islands Light", false, "Islands Darcula", true, "JetBrainsHighContrastTheme", true);

    @Test void manifestPinsEveryBundledThemeAndSchemeToItsUpstreamFile() throws Exception {
        List<String> rows = text("assets.tsv").lines().skip(1).toList();
        assertThat(rows).hasSize(PINNED.size());
        for (String row : rows) {
            String[] fields = row.split("\t");
            assertThat(fields).as(row).hasSize(3);
            assertThat(fields[1]).isEqualTo(SOURCES.get(fields[0]));
            assertThat(fields[2]).as(fields[0]).isEqualTo(PINNED.get(fields[0]));
            assertThat(sha256(bytes(fields[0]))).as(fields[0]).isEqualTo(fields[2]);
        }
    }

    @Test void theDefaultSchemesFileHoldsIntellijsDefaultAndDarculaSchemes() throws Exception {
        assertThat(text("DefaultColorSchemesManager.xml"))
            .contains("<scheme name=\"Default\"").contains("<scheme name=\"Darcula\"")
            .contains("CONSOLE_BACKGROUND_KEY").contains("CONSOLE_WHITE_OUTPUT");
        assertThat(text("SOURCE.md")).contains("DefaultColorSchemesManager.xml");
    }

    @Test void everyBundledThemeResolvesAsAPluginThemesParentWithoutWarnings() {
        PARENTS.forEach((id, dark) -> {
            String json = "{\"name\": \"Child of " + id + "\", \"dark\": " + dark + ", \"parentTheme\": \"" + id + "\"}";
            var resolved = ThemeParents.load("child.theme.json", json);
            assertThat(resolved.warnings()).as(id).isEmpty();
            assertThat(resolved.dark()).as(id).isEqualTo(dark);
        });
    }

    @Test void anIslandsChildInheritsTheIslandsChainNotTheClassicBase() {
        var islands = ThemeParents.load("child.theme.json",
            "{\"name\": \"Child\", \"dark\": true, \"parentTheme\": \"Islands Dark\"}");
        var darcula = ThemeParents.load("child.theme.json",
            "{\"name\": \"Child\", \"dark\": true, \"parentTheme\": \"Darcula\"}");
        assertThat(islands.json()).contains("\"Island.borderColor\"");
        assertThat(darcula.json()).doesNotContain("\"Island.borderColor\"");
    }

    @Test void everyBundledSchemeResolvesToTheDefaultOrDarculaRoot() {
        for (String name : List.of("IntelliJ Light", "Light", "Dark", "Darcula Contrast", "High contrast", "Islands Dark"))
            assertThat(EditorScheme.named(name, "t.theme.json", true).warnings()).as(name).isEmpty();
    }

    @Test void licenseSourceAndNoticeTravelWithTheThemes() throws Exception {
        assertThat(text("LICENSE.txt").stripLeading()).startsWith("Apache License").contains("Version 2.0, January 2004");
        assertThat(text("NOTICE.txt")).isNotBlank();
        assertThat(text("SOURCE.md")).contains("f7377708b654b73b206da40bb382ecb4d44e8f12");
    }

    private static byte[] bytes(String name) throws Exception {
        try (var in = IntellijThemesTest.class.getClassLoader().getResourceAsStream(BASE + name)) {
            assertThat(in).as(name).isNotNull();
            return in.readAllBytes();
        }
    }

    private static String text(String name) throws Exception { return new String(bytes(name), StandardCharsets.UTF_8); }

    private static String sha256(byte[] data) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
    }
}
