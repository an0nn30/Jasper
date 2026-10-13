package dev.example.hello;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.sdk.PluginInfo;
import dev.jasper.sdk.testing.FakePluginHost;
import java.util.Set;
import org.junit.jupiter.api.Test;

class HelloPluginTest {
    @Test void theToolsCommandCopiesTheGreeting() {
        try (var host = new FakePluginHost()) {
            host.start(new PluginInfo("dev.example.hello", "Hello", "1.0.0", Set.of()), Set.of(), Set.of(), new HelloPlugin());
            assertThat(host.menu("TOOLS")).containsExactly("item:" + HelloPlugin.COPY);
            var window = host.addTerminalWindow();
            assertThat(host.invoke(HelloPlugin.COPY, window, null)).isTrue();
            assertThat(host.clipboard().copies()).containsExactly("copy|dev.example.hello|" + HelloPlugin.GREETING);
            assertThat(host.failures()).isEmpty();
        }
    }
}
