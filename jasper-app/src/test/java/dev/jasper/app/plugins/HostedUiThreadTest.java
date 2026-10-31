package dev.jasper.app.plugins;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import dev.jasper.sdk.PluginInfo;
import dev.jasper.sdk.plugin.PluginContext;
import java.nio.file.Path;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/** Browser plans 4a and 4c: the application host refuses openFile, hasModal, createForContent, isDefaultBrowser and onDefaultBrowserChanged off the UI thread; the testkit models no UI thread. */
class HostedUiThreadTest {
    @Test void theBrowserCoreCallsOffTheUiThreadThrow() {
        try (var harness = new AppContractTest().newHarness()) {
            UUID window = harness.addTerminalWindow();
            var context = new AtomicReference<PluginContext>();
            harness.start(new PluginInfo("test.off", "Off", "1.0.0", Set.of()), Set.of(), Set.of(), context::set);
            var owner = new AtomicReference<dev.jasper.sdk.terminal.WindowHandle>();
            harness.ui(() -> owner.set(context.get().terminals().window(window).orElseThrow()));
            assertThatIllegalStateException().isThrownBy(() -> context.get().platform().openFile(Path.of("/tmp/index.html")))
                .withMessageContaining("UI thread");
            assertThatIllegalStateException().isThrownBy(() -> context.get().windows().hasModal(owner.get())).withMessageContaining("UI thread");
            assertThatIllegalStateException().isThrownBy(() -> context.get().menus().createForContent("test.off.web", "Web"))
                .withMessageContaining("UI thread");
            assertThatIllegalStateException().as("browser plan 4c").isThrownBy(() -> context.get().platform().isDefaultBrowser())
                .withMessageContaining("UI thread");
            assertThatIllegalStateException().isThrownBy(() -> context.get().platform().onDefaultBrowserChanged(jasper -> { }))
                .withMessageContaining("UI thread");
            assertThat(harness.openedFiles()).isEmpty();
        }
    }
}
