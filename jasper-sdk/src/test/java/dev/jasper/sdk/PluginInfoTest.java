package dev.jasper.sdk;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class PluginInfoTest {
    @Test void acceptsNamespacedIdsAndCopiesCapabilities() {
        var capabilities = new HashSet<>(Set.of("terminal.observe"));
        var info = new PluginInfo("dev.example.tool", "Tool", "1.2.3", capabilities);
        capabilities.add("terminal.inject");
        assertThat(info.capabilities()).containsExactly("terminal.observe");
        assertThat(JasperSdk.VERSION).matches("0\\.\\d+\\.\\d+");
    }

    @Test void rejectsMalformedAndReservedIds() {
        for (String id : new String[]{"", "Upper", "1abc", "has space", "jasper", "jasper.core", "a".repeat(129)}) {
            assertThat(PluginInfo.validId(id)).as(id).isFalse();
            assertThatIllegalArgumentException().as(id)
                .isThrownBy(() -> new PluginInfo(id, "n", "1.0.0", Set.of()));
        }
        assertThat(PluginInfo.validId("jasperish.tool")).isTrue();
    }

    @Test void rejectsBlankNameAndVersion() {
        assertThatIllegalArgumentException().isThrownBy(() -> new PluginInfo("a.b", " ", "1.0.0", Set.of()));
        assertThatIllegalArgumentException().isThrownBy(() -> new PluginInfo("a.b", "n", "", Set.of()));
        assertThatNullPointerException().isThrownBy(() -> new PluginInfo("a.b", "n", "1.0.0", null));
    }
}
