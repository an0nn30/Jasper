package dev.jasper.fileserver;

import dev.jasper.sdk.Capabilities;
import dev.jasper.sdk.PluginInfo;
import dev.jasper.sdk.testing.FakePluginHost;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class FileServerPluginTest {
    static final PluginInfo INFO = new PluginInfo("dev.jasper.fileserver", "File Server", "0.1.0",
        Set.of(Capabilities.NETWORK_LISTEN, Capabilities.TERMINAL_OBSERVE, Capabilities.PALETTE_CONTRIBUTE));

    @Test void startsAndDeclaresItsSettings() {
        try (var host = new FakePluginHost()) {
            host.start(INFO, Set.of(), Set.of(), new FileServerPlugin());
            assertThat(host.active("dev.jasper.fileserver")).isTrue();
            assertThat(host.declared("dev.jasper.fileserver"))
                .anySatisfy(line -> assertThat(line).startsWith("confirm_quit"))
                .anySatisfy(line -> assertThat(line).startsWith("activity_entries"));
        }
    }
}
