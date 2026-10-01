package dev.jasper.app.settings;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SchemaReaderTest {
    private static final Setting<Integer> SIZE = Setting.of("size", SettingKind.integer(8, 48, SettingKind.OutOfRange.DEFAULT), 13)
        .label("Size").build();
    private static final Setting<Boolean> WRAP = Setting.of("view.wrap", SettingKind.bool(), false).label("Wrap").alias("wrap").build();
    private static final SettingsSchema SCHEMA = SettingsSchema.of(List.of(SIZE, WRAP), Set.of(List.of("own")), Map.of());

    @Test void readsValuesAndPlacesEveryProblem() {
        var result = SchemaReader.read(SCHEMA, "size = 99\nmystery = 1\n[view]\nwrap = true\n");
        assertThat(result.values().get(SIZE)).isEqualTo(13);
        assertThat(result.values().get(WRAP)).isTrue();
        assertThat(result.rejected()).isFalse();
        assertThat(result.syntaxError()).isFalse();
        assertThat(result.problems()).containsExactly(
            new SchemaReader.Problem(Reading.Severity.ERROR, "size", 1, 1, "Use an integer from 8–48; using the default."),
            new SchemaReader.Problem(Reading.Severity.WARNING, "mystery", 2, 1, "Unknown setting or table; ignored."));
    }

    @Test void aWrongTypeRejectsTheFileAndBadSyntaxReadsNothing() {
        var wrong = SchemaReader.read(SCHEMA, "size = \"big\"\n");
        assertThat(wrong.rejected()).isTrue();
        assertThat(wrong.syntaxError()).isFalse();
        assertThat(wrong.problems()).extracting(SchemaReader.Problem::message)
            .containsExactly("Expected an integer; configuration was not applied.");
        var broken = SchemaReader.read(SCHEMA, "size = \n");
        assertThat(broken.syntaxError()).isTrue();
        assertThat(broken.rejected()).isTrue();
        assertThat(broken.values().present(SIZE)).isFalse();
        assertThat(broken.problems()).singleElement().satisfies(problem -> {
            assertThat(problem.key()).isEmpty();
            assertThat(problem.message()).isEqualTo("Invalid TOML syntax or duplicate definition; check this location.");
        });
    }

    @Test void oldKeysMoveAndTheCallerReadsItsOwnPaths() {
        var special = new ArrayList<String>();
        var reader = new SchemaReader(SCHEMA, "wrap = true\nversion = 3\n[own]\nanything = 1\n");
        var result = reader.read((path, value) -> {
            if (path.equals(List.of("version"))) { special.add("version=" + value); reader.warning(path, "Newer file."); return true; }
            if (path.equals(List.of("own"))) { special.add("own"); return true; }
            return false;
        });
        assertThat(special).containsExactlyInAnyOrder("version=3", "own");
        assertThat(result.values().get(WRAP)).isTrue();
        assertThat(result.legacyKeys()).containsExactly(new LegacyKey(List.of("wrap"), List.of("view", "wrap")));
        assertThat(result.problems()).extracting(SchemaReader.Problem::message)
            .containsExactly("Moved to view.wrap. Use Migrate Config to update the file.", "Newer file.");
    }

    @Test void aCurrentKeyWinsOverItsOldKey() {
        var result = SchemaReader.read(SCHEMA, "wrap = false\n[view]\nwrap = true\n");
        assertThat(result.values().get(WRAP)).isTrue();
        assertThat(result.problems()).extracting(SchemaReader.Problem::message).containsExactly("Superseded by view.wrap; ignored.");
    }

    @Test void aliasConversionAppliesBeforeKindReadingOnlyForAlias() {
        Setting<String> name = Setting.of("view.name", SettingKind.string(value -> !value.isBlank(), "blank"), "default")
            .alias("old_name").label("Name").build();
        var schema = SettingsSchema.of(List.of(name), Set.of(), Map.of());
        var converted = new SchemaReader(schema, "old_name = 'light'\n", (path, value) -> "Full Light")
            .read((path, value) -> false);
        assertThat(converted.values().get(name)).isEqualTo("Full Light");
        var canonical = new SchemaReader(schema, "view.name = 'light'\n", (path, value) -> "Full Light")
            .read((path, value) -> false);
        assertThat(canonical.values().get(name)).isEqualTo("light");
    }
}
