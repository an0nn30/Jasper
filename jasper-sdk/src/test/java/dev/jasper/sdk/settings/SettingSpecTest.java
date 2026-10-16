package dev.jasper.sdk.settings;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;

class SettingSpecTest {
    @Test void aDeclarationCarriesEverythingSettingsShows() {
        var spec = SettingSpec.of("sftp.max_parallel_files", SettingKind.integer(1, 8), 2).label("Parallel files")
            .description("Files transferred at once.").keywords("sftp", "transfer").section("SFTP")
            .applies(Applies.RESTART).editor(EditorHint.directory()).unit(" files ");
        assertThat(spec.key()).isEqualTo("sftp.max_parallel_files");
        assertThat(spec.defaultValue()).isEqualTo(2);
        assertThat(spec.label()).isEqualTo("Parallel files");
        assertThat(spec.description()).isEqualTo("Files transferred at once.");
        assertThat(spec.keywords()).containsExactly("sftp", "transfer");
        assertThat(spec.section()).isEqualTo("SFTP");
        assertThat(spec.applies()).isEqualTo(Applies.RESTART);
        assertThat(spec.editor()).contains(EditorHint.directory());
        assertThat(spec.unit()).as("a short unit, trimmed").isEqualTo("files");
        var plain = SettingSpec.of("vim", SettingKind.bool(), false);
        assertThat(plain.label()).isEmpty();
        assertThat(plain.description()).isEmpty();
        assertThat(plain.keywords()).isEmpty();
        assertThat(plain.section()).isEmpty();
        assertThat(plain.applies()).isEqualTo(Applies.LIVE);
        assertThat(plain.editor()).isEmpty();
        assertThat(plain.unit()).isEmpty();
        assertThat(EditorHint.font(true)).isEqualTo(new EditorHint.Font(true));
    }

    /** Plan 3b: the technical hint is a value, like the others. */
    @Test void theTechnicalHintIsAValue() {
        assertThat(EditorHint.technical()).isEqualTo(new EditorHint.Technical());
        assertThat(SettingSpec.of("ports", SettingKind.text(), "").editor(EditorHint.technical()).editor()).contains(EditorHint.technical());
    }

    @Test void badDeclarationsAreRejected() {
        for (String key : new String[]{"", ".a", "a.", "a..b", "has space", "a.b c"})
            assertThatIllegalArgumentException().as(key).isThrownBy(() -> SettingSpec.of(key, SettingKind.bool(), false));
        assertThatIllegalArgumentException().isThrownBy(() -> SettingSpec.of(null, SettingKind.bool(), false));
        assertThatIllegalArgumentException().isThrownBy(() -> SettingSpec.of("size", SettingKind.integer(1, 8), 9))
            .withMessageContaining("Use a whole number from 1–8.");
        assertThatIllegalArgumentException().isThrownBy(() -> SettingSpec.of("vim", SettingKind.bool(), false).label(" "));
        assertThatNullPointerException().isThrownBy(() -> SettingSpec.of("vim", SettingKind.bool(), null));
        assertThatNullPointerException().isThrownBy(() -> SettingSpec.of("vim", null, false));
        assertThatIllegalArgumentException().as("a unit is short").isThrownBy(() -> SettingSpec.of("size", SettingKind.integer(1, 8), 2).unit("seventeen letters"));
        assertThatIllegalArgumentException().isThrownBy(() -> SettingSpec.of("size", SettingKind.integer(1, 8), 2).unit("m\nin"));
    }
}
