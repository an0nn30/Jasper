package dev.jasper.ui.gallery;

import java.nio.file.*;
import java.util.*;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class UiDocumentationTest {
    static final Path ROOT = Path.of(System.getProperty("jasper.repoRoot", "..")).toAbsolutePath().normalize();
    @Test void guidesExistAndPublicExamplesMatchCompiledTests() throws Exception {
        String examples = Files.readString(ROOT.resolve("jasper-ui/src/test/java/dev/jasper/ui/gallery/UiExamplesTest.java"));
        for (String name : List.of("jasper-ui/README.md", "docs/ui-architecture.md", "docs/ui-maintenance.md", "docs/design/theme-engine/calibration.md")) {
            Path guide = ROOT.resolve(name);
            assertThat(guide).as("required guide %s", name).exists();
            String text = Files.readString(guide);
            var links = Pattern.compile("\\[[^\\]]*]\\(([^)]+)\\)").matcher(text.replaceAll("(?s)```.*?```", ""));
            while (links.find()) {
                String link = links.group(1);
                if (link.matches("[a-zA-Z][a-zA-Z0-9+.-]*:.*")) continue;
                String target = link.split("#", 2)[0];
                assertThat(target.isEmpty() ? guide : guide.getParent().resolve(target).normalize()).as("%s: %s", name, link).exists();
            }
            var snippets = Pattern.compile("<!-- example:([a-z]+) -->\\s*```java\\n(.*?)```", Pattern.DOTALL).matcher(text);
            while (snippets.find()) {
                String snippet = examples.split("// example:" + snippets.group(1) + ":start\\R", 2)[1].split("// example:" + snippets.group(1) + ":end", 2)[0].stripIndent().strip();
                assertThat(snippets.group(2).stripIndent().strip()).as("compiled example %s", snippets.group(1)).isEqualTo(snippet);
            }
        }
    }
}
