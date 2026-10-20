package dev.jasper.ui.theme;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Spec 7.1: every shipped variant's derived roles, frozen: the built-ins in derived-roles.tsv, the Enterprise plugin's
 * theme in derived-roles-enterprise.tsv; a recipe or shipped theme change edits them in the same commit.
 */
class DerivedRolesTableTest {
    @Test void theBuiltInVariantsDerivedRolesAreFrozen() throws IOException {
        String actual = table(JasperTheme.builtIns());
        Path written = Path.of("build/derived-roles/derived-roles.tsv");
        Files.createDirectories(written.getParent());
        Files.writeString(written, actual);
        assertThat(actual).isEqualTo(expected("derived-roles.tsv"));
    }

    @Test void theEnterpriseVariantsDerivedRolesAreFrozen() throws IOException {
        Path root = Path.of(System.getProperty("jasper.repoRoot", "..")).toAbsolutePath().normalize();
        var theme = JasperTheme.parse(Files.readString(root.resolve("plugins/enterprise/src/main/resources/themes/enterprise.json")), "enterprise");
        String actual = table(List.of(theme));
        // The computed table, for Step 3 to freeze and for any later recipe change to compare against.
        Path written = Path.of("build/derived-roles/derived-roles-enterprise.tsv");
        Files.createDirectories(written.getParent());
        Files.writeString(written, "# The Enterprise theme's derived roles (DerivedPalette), frozen: theme id, variant, role, colour.\n" + actual);
        assertThat(actual).isEqualTo(expected("derived-roles-enterprise.tsv"));
    }

    static String table(Iterable<JasperTheme> themes) {
        var actual = new StringBuilder();
        for (var theme : themes) for (boolean dark : new boolean[] {true, false}) if (theme.has(dark)) {
            var derived = DerivedPalette.of(theme.generated(dark));
            for (var role : DerivedColor.values())
                actual.append(theme.id()).append('\t').append(dark ? "dark" : "light").append('\t').append(role.key()).append('\t')
                    .append(ColorMath.hex(derived.color(role))).append('\n');
        }
        return actual.toString();
    }

    static String expected(String resource) throws IOException {
        var expected = new StringBuilder();
        try (var in = DerivedRolesTableTest.class.getResourceAsStream(resource)) {
            if (in == null) return "(" + resource + " is missing)\n";
            for (String line : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\n"))
                if (!line.isBlank() && !line.startsWith("#")) expected.append(line).append('\n');
        }
        return expected.toString();
    }
}
