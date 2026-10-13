package dev.jasper.app.plugins;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import dev.jasper.sdk.Capabilities;
import dev.jasper.sdk.PluginInfo;
import dev.jasper.sdk.plugin.PluginContext;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/** The application host refuses clipboard calls off the UI thread; the testkit models no UI thread. */
class HostedClipboardThreadTest {
    @Test void clipboardCallsOffTheUiThreadThrow() {
        try (var harness = new AppContractTest().newHarness()) {
            var context = new AtomicReference<PluginContext>();
            harness.start(new PluginInfo("test.off", "Off", "1.0.0", Set.of(Capabilities.CLIPBOARD_READ)), Set.of(), Set.of(), context::set);
            var platform = context.get().platform();
            assertThatIllegalStateException().isThrownBy(() -> platform.copy("x")).withMessageContaining("UI thread");
            assertThatIllegalStateException().isThrownBy(() -> platform.copySecret("x")).withMessageContaining("UI thread");
            assertThatIllegalStateException().isThrownBy(platform::clipboardText).withMessageContaining("UI thread");
            assertThat(harness.clipboard()).isEmpty();
        }
    }
}
