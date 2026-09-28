package dev.jasper.app.settings;

import static org.assertj.core.api.Assertions.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SettingsSchemaTest {
    private static final Setting<Integer> HEIGHT = Setting.of("window.tab_height",
        SettingKind.integer(20, 72, SettingKind.OutOfRange.CLAMP), 30).label("Tab height").build();
    private static final Setting<Float> SIZE = Setting.of("terminal.font.size", SettingKind.number(6, 72), 16f)
        .label("Font size").alias("font.size").keywords("font", "points").applies(Applies.LIVE).build();
    private static final SettingsSchema SCHEMA = SettingsSchema.of(List.of(HEIGHT, SIZE),
        Set.of(List.of("keybindings")), Map.of(List.of("window"), "Chrome."));

    @Test void settingsDescribeTheirKeyAndRequireALabel() {
        assertThat(SIZE.name()).isEqualTo("terminal.font.size");
        assertThat(SIZE.table()).containsExactly("terminal", "font");
        assertThat(SIZE.leaf()).isEqualTo("size");
        assertThat(SIZE.aliases()).containsExactly(List.of("font", "size"));
        assertThat(SIZE.example()).isEmpty();
        assertThat(Setting.of("ui.font.size", SettingKind.number(8, 32), 0f).label("UI font size").example(14f).build().example()).hasValue(14f);
        assertThatIllegalArgumentException().isThrownBy(() -> Setting.of("a.b", SettingKind.bool(), true).build());
        assertThat(Setting.of("keybindings.\"app.shortcuts\"", SettingKind.bool(), true).label("x").build().name())
            .isEqualTo("keybindings.\"app.shortcuts\"");
    }

    @Test void theSchemaFindsKeysAliasesAndTables() {
        assertThat(SCHEMA.settings()).containsExactly(HEIGHT, SIZE);
        assertThat(SCHEMA.find(List.of("terminal", "font", "size"))).containsSame(SIZE);
        assertThat(SCHEMA.find(List.of("font", "size"))).isEmpty();
        assertThat(SCHEMA.aliasOf(List.of("font", "size"))).containsSame(SIZE);
        assertThat(SCHEMA.knownTable(List.of("terminal"))).isTrue();
        assertThat(SCHEMA.knownTable(List.of("terminal", "font"))).isTrue();
        assertThat(SCHEMA.knownTable(List.of("font"))).as("old tables stay known").isTrue();
        assertThat(SCHEMA.knownTable(List.of("terminal", "font", "size"))).as("a key is not a table").isFalse();
        assertThat(SCHEMA.reserved(List.of("keybindings"))).isTrue();
        assertThat(SCHEMA.tableNote(List.of("window"))).hasValue("Chrome.");
        assertThat(SCHEMA.tableNote(List.of("terminal"))).isEmpty();
    }

    @Test void theSchemaRejectsCollidingKeys() {
        var duplicate = Setting.of("window.tab_height", SettingKind.bool(), true).label("x").build();
        assertThatIllegalArgumentException().isThrownBy(() -> SettingsSchema.of(List.of(HEIGHT, duplicate), Set.of(), Map.of()))
            .withMessageContaining("window.tab_height");
        var aliasOnKey = Setting.of("other.key", SettingKind.bool(), true).label("x").alias("window.tab_height").build();
        assertThatIllegalArgumentException().isThrownBy(() -> SettingsSchema.of(List.of(HEIGHT, aliasOnKey), Set.of(), Map.of()));
        var inside = Setting.of("window.tab_height.more", SettingKind.bool(), true).label("x").build();
        assertThatIllegalArgumentException().isThrownBy(() -> SettingsSchema.of(List.of(HEIGHT, inside), Set.of(), Map.of()))
            .withMessageContaining("lies inside");
        var reserved = Setting.of("keybindings.copy", SettingKind.bool(), true).label("x").build();
        assertThatIllegalArgumentException().isThrownBy(() -> SettingsSchema.of(List.of(reserved), Set.of(List.of("keybindings")), Map.of()))
            .withMessageContaining("reserved");
    }

    @Test void valuesRecordPresenceAndTheKeyTheyCameFrom() {
        var values = SettingValues.builder(SCHEMA).put(SIZE, 18f, List.of("font", "size")).build();
        assertThat(values.get(SIZE)).isEqualTo(18f);
        assertThat(values.present(SIZE)).isTrue();
        assertThat(values.source(SIZE)).hasValue(List.of("font", "size"));
        assertThat(values.saved(SIZE)).hasValue(18f);
        assertThat(values.get(HEIGHT)).isEqualTo(30);
        assertThat(values.present(HEIGHT)).isFalse();
        assertThat(values.saved(HEIGHT)).isEqualTo(Optional.empty());
        assertThat(SettingValues.defaults(SCHEMA).get(SIZE)).isEqualTo(16f);
        assertThat(values).isEqualTo(SettingValues.builder(SCHEMA).put(SIZE, 18f, List.of("font", "size")).build());
        var stranger = Setting.of("x.y", SettingKind.bool(), true).label("x").build();
        assertThatIllegalArgumentException().isThrownBy(() -> values.get(stranger));
        assertThat(new LegacyKey(List.of("font", "size"), List.of("terminal", "font", "size")).to()).containsExactly("terminal", "font", "size");
        assertThat(SettingsSchema.startsWith(List.of("a", "b"), List.of("a"))).isTrue();
        assertThat(SettingsSchema.startsWith(List.of("a"), List.of("a", "b"))).isFalse();
    }
}
