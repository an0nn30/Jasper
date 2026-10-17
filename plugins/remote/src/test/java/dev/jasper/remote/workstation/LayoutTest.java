package dev.jasper.remote.workstation;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.sdk.PluginInfo;
import dev.jasper.sdk.services.Services;
import dev.jasper.sdk.testing.FakePluginHost;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

/** Spec 14.3: Remote composes its classic layout exactly while Workstation is published. */
class LayoutTest {
    static final PluginInfo PROBE = new PluginInfo("dev.example.probe", "Probe", "1.0.0", Set.of());

    @Test void whileWorkstationIsPublishedTheLayoutIsClassic() {
        try (var host = new FakePluginHost()) {
            FakeEnterprise.start(host);
            var layout = new AtomicReference<Layout>();
            host.start(PROBE, Set.of(), Set.of(FakeEnterprise.INFO.id()), context -> layout.set(Layout.of(context.services())));
            assertThat(layout.get()).isEqualTo(Layout.CLASSIC);
            assertThat(host.failures()).isEmpty();
        }
    }

    @Test void withoutAWorkstationTheLayoutIsRows() {
        try (var host = new FakePluginHost()) {
            var layout = new AtomicReference<Layout>();
            host.start(PROBE, Set.of(), Set.of(FakeEnterprise.INFO.id()), context -> layout.set(Layout.of(context.services())));
            assertThat(layout.get()).isEqualTo(Layout.ROWS);
        }
    }

    /** Enterprise not installed: its API class is not visible to the plugin's loader. */
    @Test void aMissingEnterpriseClassIsRows() {
        Services missing = new Services() {
            @Override public <T> void publish(Class<T> api, T implementation) { throw new UnsupportedOperationException(); }
            @Override public <T> void publishPerConsumer(Class<T> api, Function<PluginInfo, T> perConsumer) { throw new UnsupportedOperationException(); }
            @Override public <T> T require(Class<T> api) { throw new UnsupportedOperationException(); }
            @Override public <T> Optional<T> find(Class<T> api) { throw new NoClassDefFoundError("dev/jasper/enterprise/api/Workstation"); }
        };
        assertThat(Layout.of(missing)).isEqualTo(Layout.ROWS);
    }
}
