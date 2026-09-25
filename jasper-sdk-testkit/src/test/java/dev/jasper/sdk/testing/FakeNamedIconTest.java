package dev.jasper.sdk.testing;

import dev.jasper.sdk.PluginInfo;
import dev.jasper.sdk.ui.IconName;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class FakeNamedIconTest {
    @Test void namedIconsAreInspectableByNameAlone() {
        try (var host = new FakePluginHost()) {
            var seen = new java.util.concurrent.atomic.AtomicReference<javax.swing.Icon>();
            host.start(new PluginInfo("dev.x.icons", "Icons", "1.0.0", Set.of()), Set.of(), Set.of(), new dev.jasper.sdk.plugin.Plugin() {
                public void start(dev.jasper.sdk.plugin.PluginContext context) { seen.set(context.appearance().icon(IconName.LOCK)); }
                public void stop() { }
            });
            assertThat(seen.get()).isEqualTo(new FakeNamedIcon(IconName.LOCK));
            assertThat(seen.get().getIconWidth()).isEqualTo(16);
        }
    }
}
