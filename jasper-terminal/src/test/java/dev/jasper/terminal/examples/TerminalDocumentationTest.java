package dev.jasper.terminal.examples;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/** Developer guides and compiled snippets are part of the build, not unchecked prose. */
class TerminalDocumentationTest {
    private static final Path ROOT = Path.of(System.getProperty("jasper.repoRoot", "..")).toAbsolutePath().normalize();
    private static final List<Path> GUIDES = List.of(ROOT.resolve("jasper-terminal/README.md"),
        ROOT.resolve("docs/terminal-architecture.md"), ROOT.resolve("docs/terminal-maintenance.md"));

    @Test void guideLinksResolveAndExamplesMatchCompiledSource() throws Exception {
        String source = Files.readString(ROOT.resolve("jasper-terminal/src/test/java/dev/jasper/terminal/examples/TerminalExamplesTest.java"));
        for (Path guide : GUIDES) {
            assertThat(guide).exists();
            String text = Files.readString(guide);
            var links = Pattern.compile("\\[[^\\]]*]\\(([^)]+)\\)").matcher(text.replaceAll("(?s)```.*?```", ""));
            while (links.find()) {
                String link = links.group(1);
                if (link.matches("[a-zA-Z][a-zA-Z0-9+.-]*:.*")) continue;
                String[] parts = link.split("#", 2);
                Path target = parts[0].isEmpty() ? guide : guide.getParent().resolve(parts[0]).normalize();
                assertThat(target).as("%s: %s", guide, link).exists();
                if (parts.length == 2 && target.toString().endsWith(".md")) {
                    List<String> anchors = Files.readAllLines(target).stream().filter(l -> l.startsWith("#"))
                        .map(l -> l.replaceFirst("^#+ +", "").toLowerCase(Locale.ROOT)
                            .replaceAll("[^\\p{L}\\p{N}_ -]", "").replace(' ', '-')).toList();
                    assertThat(anchors).as("%s: fragment %s", target, parts[1]).contains(parts[1]);
                }
            }
            var snippets = Pattern.compile("<!-- example:([a-z]+) -->\\s*```java\\n(.*?)```", Pattern.DOTALL).matcher(text);
            while (snippets.find()) {
                String name = snippets.group(1);
                String snippet = source.split("// example:" + name + ":start\\R", 2)[1]
                    .split("// example:" + name + ":end", 2)[0].stripIndent().strip();
                assertThat(snippets.group(2).stripIndent().strip()).as("compiled example %s", name).isEqualTo(snippet);
            }
        }
    }

    @Test void everyProductionPackageHasAnOwnershipContract() throws Exception {
        Path source = ROOT.resolve("jasper-terminal/src/main/java/dev/jasper/terminal");
        try (var files = Files.walk(source)) {
            for (Path directory : files.filter(Files::isDirectory).filter(p -> !p.equals(source)).toList()) {
                try (var children = Files.list(directory)) {
                    if (children.noneMatch(p -> p.toString().endsWith(".java"))) continue;
                }
                Path info = directory.resolve("package-info.java");
                assertThat(info).exists();
                assertThat(Files.readString(info)).contains("/**", "package dev.jasper.terminal.");
            }
        }
    }
}
