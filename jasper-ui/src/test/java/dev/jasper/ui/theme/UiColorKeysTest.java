package dev.jasper.ui.theme;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import org.junit.jupiter.api.Test;

/** Review Focus 3: FlatLaf's keys from roles are exactly the template's values from the legacy token view. */
class UiColorKeysTest {
    @Test void theKeysFromRolesAreTheTemplateValuesForBothVariants() {
        for (boolean dark : new boolean[]{true, false}) {
            var theme = SlateSteel.bundled().generated(dark);
            var legacy = new LinkedHashMap<String, Object>();
            UiTemplate.FLATLAF.forEach((key, token) -> legacy.put(key, UiTemplate.hex(theme.tokens().get((Token) token))));
            var scheme = SlateSteel.bundled().terminal(TerminalScheme.matching(dark));
            assertThat(UiColorKeys.flatLaf(theme.semantic(), scheme.colors().selection())).as(dark ? "dark" : "light")
                .containsExactlyEntriesOf(legacy);
        }
    }
}
