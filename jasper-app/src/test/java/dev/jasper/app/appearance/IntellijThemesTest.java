package dev.jasper.app.appearance;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class IntellijThemesTest {
    private static final String BASE = "dev/jasper/app/themes/intellij/";
    private static final Map<String, String> PINNED = Map.of(
        "Light.theme.json", "429151594b8a013938c3c66dd3a5121702182c028ad87ae1e68bc4243cc1c68d",
        "intellijlaf.theme.json", "4450e2a85381b7b644c73bc9ec21f898aa55b184fc80f109a43ae9ff67af21ba",
        "darcula.theme.json", "858efbd6afdb12f26b4757c4ff7b1bccb8bdefc286797fa6344769496b6c59cb",
        "DefaultColorSchemesManager.xml", "e8eef86e79a25de2ab827c6535280019c1428afe7ff4688af94a7a50e3c9ea08");
    private static final Map<String, String> SOURCES = Map.of(
        "Light.theme.json", "platform/platform-resources/src/themes/Light.theme.json",
        "intellijlaf.theme.json", "platform/platform-resources/src/themes/intellijlaf.theme.json",
        "darcula.theme.json", "platform/platform-resources/src/themes/darcula.theme.json",
        "DefaultColorSchemesManager.xml", "platform/platform-resources/src/DefaultColorSchemesManager.xml");

    @Test void manifestPinsTheClassicLightChainAndTheDefaultSchemesToTheirUpstreamFiles() throws Exception {
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
