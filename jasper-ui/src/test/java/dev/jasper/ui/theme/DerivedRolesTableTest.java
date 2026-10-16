package dev.jasper.ui.theme;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/** Spec 7.1: every built-in variant's derived roles, frozen in derived-roles.tsv; a recipe or built-in theme change edits it in the same commit. */
class DerivedRolesTableTest {
    @Test void theBuiltInVariantsDerivedRolesAreFrozen() throws IOException {
        var expected = new StringBuilder();
        try (var in = DerivedRolesTableTest.class.getResourceAsStream("derived-roles.tsv")) {
            for (String line : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\n"))
                if (!line.isBlank() && !line.startsWith("#")) expected.append(line).append('\n');
        }
        var actual = new StringBuilder();
        for (var theme : JasperTheme.builtIns()) for (boolean dark : new boolean[] {true, false}) if (theme.has(dark)) {
            var derived = DerivedPalette.of(theme.generated(dark));
            for (var role : DerivedColor.values())
                actual.append(theme.id()).append('\t').append(dark ? "dark" : "light").append('\t').append(role.key()).append('\t')
                    .append(ColorMath.hex(derived.color(role))).append('\n');
        }
        assertThat(actual.toString()).isEqualTo(expected.toString());
    }
}
