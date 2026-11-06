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
        assertThat(string("version")).isEqualTo("0.2.0");
        assertThat(string("entry")).isEqualTo(BrowserPlugin.class.getName());
        assertThat(string("sdk")).isEqualTo(">=0.29.0, <0.30");
        assertThat(raw("enabled_by_default")).isEqualTo("true");
        assertThat(string("icon")).isEqualTo("icons/browser.svg");
    }

    /**
     * Opening a page in the application's default browser goes through {@code Platform.browser}, which needs the first;
     * the Bookmarks and Browser History scopes need the second; a snippet pasted into a page with no field focused puts
     * the clipboard's text back, which needs the third.
     */
    @Test void theBrowserDeclaresBrowserProvidePaletteContributeAndClipboardRead() throws IOException {
        assertThat(list("capabilities")).containsExactly("browser.provide", "palette.contribute", "clipboard.read");
    }

    /** The Markdown preview's API is the one package other plugins may compile against. */
    @Test void theBrowserExportsItsApiPackageOnly() throws IOException {
        assertThat(list("exports")).containsExactly("dev.jasper.browser.api");
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
