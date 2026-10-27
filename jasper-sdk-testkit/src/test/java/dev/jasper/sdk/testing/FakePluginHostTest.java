package dev.jasper.sdk.testing;

import dev.jasper.sdk.Capabilities;
import dev.jasper.sdk.PluginInfo;
import dev.jasper.sdk.ui.LinkOrigin;
import dev.jasper.sdk.ui.LinkPlacement;
import dev.jasper.sdk.ui.WebLink;
import java.net.URI;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class FakePluginHostTest {
    private static final PluginInfo INFO = new PluginInfo("test.alpha", "Alpha", "1.0.0", Set.of());

    @Test void backgroundWorkWaitsUntilRunAndDataDirectoriesArePerPlugin() {
        try (var host = new FakePluginHost()) {
            List<String> ran = new ArrayList<>();
            var context = host.start(INFO, Set.of(), Set.of(), c -> c.background().execute(() -> ran.add("task")));
            assertThat(ran).isEmpty();
            assertThat(host.runBackground()).isEqualTo(1);
            assertThat(ran).containsExactly("task");
            assertThat(context.dataDirectory()).isDirectory().hasFileName("data");
            assertThat(context.dataDirectory().getParent()).as("the application layout, <plugins>/<id>/data").hasFileName("test.alpha");
            assertThat(Files.isDirectory(context.dataDirectory())).isTrue();
        }
    }

    /** An address with an authority but no host, such as http://my_host/, is no WebLink: openInBrowser sends it to the system browser, as the application does. */
    @Test void openInBrowserSendsAnAddressNoWebLinkTakesToTheSystemBrowser() {
        try (var host = new FakePluginHost()) {
            List<WebLink> asked = new ArrayList<>();
            var context = host.start(new PluginInfo("test.alpha", "Alpha", "1.0.0", Set.of(Capabilities.BROWSER_PROVIDE)), Set.of(), Set.of(),
                c -> c.platform().browser(link -> asked.add(link)));
            URI noHost = URI.create("http://my_host/");
            context.platform().openInBrowser(noHost);
            assertThat(asked).isEmpty();
            assertThat(host.browsed()).containsExactly(noHost);
            assertThatIllegalArgumentException().isThrownBy(() -> host.openLink(noHost, LinkOrigin.CLICK, LinkPlacement.TAB, Optional.empty(), Optional.empty()));
            context.platform().openInBrowser(URI.create("https://example.com/"));
            assertThat(asked).extracting(link -> link.address().toString()).containsExactly("https://example.com/");
            assertThat(host.browsed()).as("the provided browser opened it").containsExactly(noHost);
        }
    }
}
