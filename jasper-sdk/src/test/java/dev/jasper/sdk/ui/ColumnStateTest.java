package dev.jasper.sdk.ui;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ColumnStateTest {
    @Test void sortingTheSameColumnFlipsAndAnotherStartsAscending() {
        var s = ColumnState.NONE.sorted("name");
        assertThat(s.sortColumn()).contains("name"); assertThat(s.ascending()).isTrue();
        assertThat(s.sorted("name").ascending()).isFalse();
        assertThat(s.sorted("name").sorted("size")).isEqualTo(ColumnState.sortedBy("size", true));
    }
    @Test void applyMarksTheSortColumnAndWidthsByIdIgnoringUnknownIds() {
        var cols = List.of(new Column("name", "Name", Nodes.TextRole.BODY, false), new Column("size", "Size", Nodes.TextRole.BODY, true));
        var out = ColumnState.sortedBy("size", false).resized(Map.of("name", 240, "gone", 90)).apply(cols);
        assertThat(out.get(0).sorted()).isEmpty(); assertThat(out.get(0).width()).hasValue(240);
        assertThat(out.get(1).sorted()).contains(false); assertThat(out.get(1).width()).isEmpty();
    }
    @Test void encodeRoundTripsAndGarbageDecodesToNone() {
        var s = ColumnState.sortedBy("modified", false).resized(Map.of("name", 200, "size", 80));
        assertThat(ColumnState.decode(s.encode())).isEqualTo(s);
        for (String bad : List.of("", "x", "sort=;widths=a:-3", "widths=name:abc", "sort=name,sideways"))
            assertThat(ColumnState.decode(bad)).as(bad).isEqualTo(ColumnState.NONE);
    }
    @Test void fileKeepsOtherKeysAndTreatsBrokenFilesAsEmpty(@TempDir Path dir) throws IOException {
        var file = new ColumnStateFile(dir.resolve("data/columns.properties"));
        assertThat(file.get("sftp")).isEqualTo(ColumnState.NONE);
        file.put("sftp", ColumnState.sortedBy("name", true)); file.put("sessions", ColumnState.sortedBy("host", false));
        assertThat(new ColumnStateFile(dir.resolve("data/columns.properties")).get("sftp")).isEqualTo(ColumnState.sortedBy("name", true));
        Files.writeString(dir.resolve("data/columns.properties"), "\\u00zz not properties");
        assertThat(file.get("sessions")).isEqualTo(ColumnState.NONE);
    }
    @Test void idsWithTheEncodingsSeparatorsAreRejected() {
        for (String id : List.of("a;b", "a,b", "a:b", "a=b")) {
            assertThatThrownBy(() -> ColumnState.sortedBy(id, true)).as(id).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> ColumnState.NONE.sorted(id)).as(id).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> ColumnState.NONE.resized(Map.of(id, 80))).as(id).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new ColumnState(Optional.empty(), true, Map.of(id, 80))).as(id).isInstanceOf(IllegalArgumentException.class);
        }
    }
}
