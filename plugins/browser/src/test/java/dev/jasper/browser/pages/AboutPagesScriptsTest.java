package dev.jasper.browser.pages;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Review Focus 1: Jasper's pages show titles and addresses any page chose, so no file of theirs, under
 * {@code dev/jasper/browser/pages/}, turns text into markup or code. Read as text, every template, script and style sheet
 * the plugin ships there, as the plugin's resources hold them.
 */
class AboutPagesScriptsTest {
    private static final List<Pattern> FORBIDDEN = Stream.concat(
        Stream.of("innerHTML", "outerHTML", "insertAdjacentHTML", "document.write", "srcdoc", "createContextualFragment",
            "setHTMLUnsafe", "DOMParser", "javascript:").map(word -> Pattern.compile(Pattern.quote(word))),
        Stream.of("\\beval\\s*\\(", "\\bnew\\s+Function\\b", "\\bset(?:Timeout|Interval)\\(\\s*['\"`]",
            // An event handler attribute, in a template or written into one: on… = in any case.
            "(?i)[\\s\"'/]on[a-z]+\\s*=").map(Pattern::compile)).toList();

    @Test void noPageFileTurnsTextIntoMarkupOrCode() throws Exception {
        Map<String, String> files = files();
        assertThat(files).as("the scan sees the pages' files").containsKeys("about.js", "about.css", "permissions.html", "permissions.js");
        for (var file : files.entrySet())
            for (Pattern forbidden : FORBIDDEN)
                assertThat(file.getValue()).as(file.getKey() + ": " + forbidden).doesNotContainPattern(forbidden);
    }

    /**
     * A script or style sheet is written inside its element, so none can end it, and a template runs no script of its
     * own: every script comes through its {@code {{script}}} slot, with the nonce of its render.
     */
    @Test void noScriptOrStyleSheetEndsItsElementAndNoTemplateHasAScript() throws Exception {
        for (var file : files().entrySet()) {
            String name = file.getKey(), text = file.getValue();
            if (name.endsWith(".js")) assertThat(text).as(name).doesNotContainPattern("(?i)</script");
            if (name.endsWith(".css")) assertThat(text).as(name).doesNotContainPattern("(?i)</style");
            if (name.endsWith(".html")) assertThat(text).as(name).doesNotContainPattern("(?i)<script");
        }
    }

    /** Every resource under the pages' folder, by name, read as UTF-8. */
    private static Map<String, String> files() throws IOException, URISyntaxException {
        URL about = AboutPages.class.getResource("about.js");
        assertThat(about).as("the plugin's about.js").isNotNull();
        Path folder = Path.of(about.toURI()).getParent();
        var files = new TreeMap<String, String>();
        try (Stream<Path> listed = Files.list(folder)) {
            for (Path file : listed.filter(Files::isRegularFile).toList())
                files.put(file.getFileName().toString(), Files.readString(file, StandardCharsets.UTF_8));
        }
        return files;
    }
}
