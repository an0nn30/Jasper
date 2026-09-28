package dev.jasper.sdk.settings;

import static org.assertj.core.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

class SettingKindTest {
    @Test void eachKindChecksItsValues() {
        assertThat(SettingKind.bool().check(true)).isEmpty();
        assertThat(SettingKind.integer(8, 48).check(8)).isEmpty();
        assertThat(SettingKind.integer(8, 48).check(49)).contains("Use a whole number from 8–48.");
        assertThat(SettingKind.number(0.5, 3).check(0.5)).isEmpty();
        assertThat(SettingKind.number(0.5, 3).check(Double.NaN)).contains("Use a number from 0.5–3.");
        assertThat(SettingKind.text().check("anything")).isEmpty();
        assertThat(SettingKind.text(text -> text.startsWith("/"), "Use an absolute path.").check("relative"))
            .contains("Use an absolute path.");
        var mode = SettingKind.choice(List.of(Choice.of("fast", "Fast", 1), Choice.of("safe", "Safe", 2)));
        assertThat(mode.check(2)).isEmpty();
        assertThat(mode.check(3)).contains("Use one of: fast, safe.");
        var words = SettingKind.stringList(word -> !word.contains(" "));
        assertThat(words.check(List.of("ls", "cd"))).isEmpty();
        assertThat(words.check(List.of("ls", "git log"))).contains("Not an allowed list element: \"git log\".");
        assertThat(SettingKind.stringList().check(List.of())).isEmpty();
    }

    @Test void impossibleKindsAreRejected() {
        assertThatIllegalArgumentException().isThrownBy(() -> SettingKind.integer(5, 1));
        assertThatIllegalArgumentException().isThrownBy(() -> SettingKind.number(2, 1));
        assertThatIllegalArgumentException().isThrownBy(() -> SettingKind.number(0, Double.POSITIVE_INFINITY));
        assertThatIllegalArgumentException().isThrownBy(() -> SettingKind.choice(List.of()));
        assertThatIllegalArgumentException().isThrownBy(() -> SettingKind.choice(List.of(Choice.of("a", "A", 1), Choice.of("a", "B", 2))));
        assertThatIllegalArgumentException().isThrownBy(() -> Choice.of(" ", "Blank", 1));
        assertThatIllegalArgumentException().isThrownBy(() -> Choice.of("a", "", 1));
        assertThatNullPointerException().isThrownBy(() -> SettingKind.text(null, "message"));
    }
}
