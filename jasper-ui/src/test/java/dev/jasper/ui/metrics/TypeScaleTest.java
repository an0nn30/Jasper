package dev.jasper.ui.metrics;

import static org.assertj.core.api.Assertions.*;

import java.awt.Font;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Enterprise presentation spec 5.6 and 6.2: a kit's type is a value, and the default scale is today's table. */
class TypeScaleTest {
    /** A role's size at the 13-point base and its weight. */
    record Row(float size, int weight) {}

    /** default-type.tsv: the table frozen when the scale became a value; a retune edits it in the same change. */
    static Map<String, Row> defaultTable() throws IOException {
        var table = new LinkedHashMap<String, Row>();
        try (var in = TypeScaleTest.class.getResourceAsStream("default-type.tsv")) {
            for (var line : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\n")) {
                var cells = line.split("\t");
                table.put(cells[0], new Row(Float.parseFloat(cells[1]), Integer.parseInt(cells[2])));
            }
        }
        return table;
    }

    @Test void theDefaultScaleIsTheFrozenThirteenPointTable() throws IOException {
        var table = defaultTable();
        assertThat(Arrays.stream(Typography.Role.values()).map(Enum::name).toList()).containsExactlyElementsOf(table.keySet());
        for (var role : Typography.Role.values()) {
            assertThat(TypeScale.DEFAULT.size(role)).as(role.name()).isEqualTo(table.get(role.name()).size());
            assertThat(TypeScale.DEFAULT.weight(role)).as(role.name()).isEqualTo(table.get(role.name()).weight());
        }
        assertThat(TypeScale.DEFAULT.base()).isEqualTo(13f);
        assertThat(TypeScale.DEFAULT.body()).isEqualTo(13f);
    }

    @Test void aBodyCopyKeepsTheTableAndChangesOnlyTheBody() {
        var large = TypeScale.DEFAULT.withBody(20);
        for (var role : Typography.Role.values()) {
            assertThat(large.size(role)).as(role.name()).isEqualTo(TypeScale.DEFAULT.size(role));
            assertThat(large.weight(role)).as(role.name()).isEqualTo(TypeScale.DEFAULT.weight(role));
        }
        assertThat(large.base()).isEqualTo(13f);
        assertThat(large.body()).isEqualTo(20f);
        assertThat(large).isNotEqualTo(TypeScale.DEFAULT);
        assertThat(TypeScale.DEFAULT.withBody(13)).isEqualTo(TypeScale.DEFAULT).hasSameHashCodeAs(TypeScale.DEFAULT)
            .isNotSameAs(TypeScale.DEFAULT);
        for (float body : new float[] {Float.NaN, Float.POSITIVE_INFINITY, 5.9f, 96.1f})
            assertThatThrownBy(() -> TypeScale.DEFAULT.withBody(body)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageStartingWith("Invalid body size");
    }

    @Test void scalesAndFacesAreValuesWithoutPublicConstructors() {
        assertThat(TypeScale.class.getConstructors()).isEmpty();
        assertThat(FaceSet.class.getConstructors()).isEmpty();
        assertThat(FaceSet.PLATFORM.dataRegular()).isEqualTo(Font.MONOSPACED);
        assertThat(FaceSet.PLATFORM.dataMedium()).isEqualTo(Font.MONOSPACED);
    }
}
