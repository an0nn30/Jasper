package dev.jasper.browser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/** The Browser's shipped manifest, as Jasper reads it from the plugin's jar. */
class BrowserManifestTest {
    @Test void theManifestNamesTheBrowserAndItsSdkRange() throws IOException {
        assertThat(string("id")).isEqualTo("dev.jasper.browser");
        assertThat(string("name")).isEqualTo("Browser");
        assertThat(string("version")).isEqualTo("0.1.0");
        assertThat(string("entry")).isEqualTo(BrowserPlugin.class.getName());
        assertThat(string("sdk")).isEqualTo(">=0.24.0, <0.25");
        assertThat(raw("enabled_by_default")).isEqualTo("true");
        assertThat(string("icon")).isEqualTo("icons/browser.svg");
    }

    /** Opening a page in the application's default browser goes through {@code Platform.browser}, which needs this. */
    @Test void theBrowserDeclaresBrowserProvideAndNothingElseYet() throws IOException {
        assertThat(list("capabilities")).containsExactly("browser.provide");
    }

    private static String manifest() throws IOException {
        try (var in = BrowserPlugin.class.getResourceAsStream("/plugin.toml")) {
            assertThat(in).as("the Browser's plugin.toml").isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static String raw(String key) throws IOException {
        var line = Pattern.compile("(?m)^" + key + "\\s*=\\s*(.*?)\\s*$").matcher(manifest());
        assertThat(line.find()).as("a " + key + " line").isTrue();
        return line.group(1);
    }

    private static String string(String key) throws IOException {
        String value = raw(key);
        assertThat(value).as(key).startsWith("\"").endsWith("\"");
        return value.substring(1, value.length() - 1);
    }

    private static List<String> list(String key) throws IOException {
        var line = Pattern.compile("(?m)^" + key + "\\s*=\\s*\\[(.*)]\\s*$").matcher(manifest());
        assertThat(line.find()).as("a " + key + " line").isTrue();
        return Arrays.stream(line.group(1).split(",")).map(String::strip).filter(value -> !value.isEmpty())
            .map(value -> value.replace("\"", "")).toList();
    }
}
