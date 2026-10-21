package dev.jasper.app.plugins;

import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/** The testkit validates icon names against the host's own built-in set. */
class KitIconNamesParityTest {
    @Test void theTestkitsBuiltInIconNamesAreTheHosts() throws Exception {
        var field = dev.jasper.sdk.testing.FakeKit.class.getDeclaredField("BUILT_INS");
        field.setAccessible(true);
        @SuppressWarnings("unchecked") var testkit = (Set<String>) field.get(null);
        try (var registry = new dev.jasper.ui.icons.IconRegistry()) {
            assertThat(testkit).isEqualTo(registry.names());
        }
    }
}
