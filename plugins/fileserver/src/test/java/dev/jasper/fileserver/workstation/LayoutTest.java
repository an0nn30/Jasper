package dev.jasper.fileserver.workstation;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.sdk.PluginInfo;
import dev.jasper.sdk.testing.FakePluginHost;
import dev.jasper.sdk.testing.FakePresentations;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/** Plan 15a: File Server composes its classic layout exactly under a classic presentation, else its rows. */
class LayoutTest {
    static final PluginInfo PROBE = new PluginInfo("dev.example.probe", "Probe", "1.0.0", Set.of());

    /** Plan 15a: any classic look composes the classic layout; the default look keeps the rows. */
    @Test void aClassicPresentationIsClassicAndTheDefaultIsRows() {
        assertThat(Layout.of(FakePresentations.CLASSIC)).isEqualTo(Layout.CLASSIC);
        assertThat(Layout.of(FakePresentations.STANDARD)).isEqualTo(Layout.ROWS);
    }

    @Test void aStartedPluginReadsItsLayoutFromTheHostsPresentation() {
        try (var host = new FakePluginHost()) {
            host.setPresentation(FakePresentations.CLASSIC);
            var layout = new AtomicReference<Layout>();
            host.start(PROBE, Set.of(), Set.of(), context -> layout.set(Layout.of(context.appearance().presentation())));
            assertThat(layout.get()).isEqualTo(Layout.CLASSIC);
        }
    }
}
