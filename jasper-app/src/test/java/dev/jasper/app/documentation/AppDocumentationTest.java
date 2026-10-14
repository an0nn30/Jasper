package dev.jasper.app.documentation;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/** Developer guides and compiled snippets are part of the build, not unchecked prose. */
class AppDocumentationTest {
    private static final Path ROOT = Path.of(System.getProperty("jasper.repoRoot", "..")).toAbsolutePath().normalize();
    private static final List<Path> GUIDES = List.of(
        "README.md", "AGENTS.md", "jasper-app/README.md", "jasper-buddy/README.md",
        "docs/README.md", "docs/STATUS.md", "docs/app-architecture.md", "docs/app-maintenance.md",
        "docs/buddy-architecture.md", "docs/buddy-maintenance.md", "docs/configuration.md", "docs/notifications.md",
        "docs/command-palette.md", "docs/diagnostics.md", "docs/packaging.md", "docs/benchmarks.md",
        "docs/rebranding.md", "docs/terminal-readiness.md", "docs/terminal-refactor-verification.md",
        "docs/app-refactor-verification.md", "docs/documentation-audit.md",
        "packaging/icons/README.md", "packaging/buddy/README.md",
        "jasper-sdk/README.md", "docs/sdk-architecture.md", "docs/plugin-authoring.md", "docs/plugin-ui.md")
        .stream().map(ROOT::resolve).toList();

    @Test void guideLinksResolveAndExamplesMatchCompiledSource() throws Exception {
        String source = Files.readString(ROOT.resolve("jasper-app/src/test/java/dev/jasper/app/documentation/AppExamplesTest.java"));
        source += "\n" + Files.readString(ROOT.resolve("jasper-buddy/src/test/java/dev/jasper/buddy/documentation/BuddyExamplesTest.java"));
        source += "\n" + Files.readString(ROOT.resolve("plugins/sample/src/main/java/dev/jasper/sample/SamplePlugin.java"));
        source += "\n" + Files.readString(ROOT.resolve("jasper-app/src/test/java/dev/jasper/app/documentation/PluginUiExamplesTest.java"));
        var guides = new java.util.ArrayList<>(GUIDES);
        // Dated records retain old code examples: they get the link check only, not the example snippets.
        for (String directory : List.of("docs/superpowers", "docs/design", "docs/benchmarks")) {
            try (var files = Files.walk(ROOT.resolve(directory))) {
                guides.addAll(files.filter(path -> path.toString().endsWith(".md")).toList());
            }
        }
        for (Path guide : guides) {
            assertThat(guide).exists();
            String text = Files.readString(guide);
            var links = Pattern.compile("\\[[^\\]]*]\\(([^)]+)\\)").matcher(withoutCode(text));
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
            if (!GUIDES.contains(guide)) continue;
            var snippets = Pattern.compile("<!-- example:([a-z]+) -->\\s*```java\\n(.*?)```", Pattern.DOTALL).matcher(text);
            while (snippets.find()) {
                String name = snippets.group(1);
                String snippet = source.split("// example:" + name + ":start\\R", 2)[1]
                    .split("// example:" + name + ":end", 2)[0].stripIndent().strip();
                assertThat(snippets.group(2).stripIndent().strip()).as("compiled example %s", name).isEqualTo(snippet);
            }
        }
    }

    @Test void fencesCloseOnlyOnALineOfAtLeastAsManyBackticks() {
        String text = String.join("\n",
            "Before [kept](a.md).",
            "`````bash",
            " ```json",
            " [inside](nowhere.md)",
            " ```",
            "`````",
            "Between [kept too](b.md).",
            "  ```toml",
            "  [also inside](nowhere.md)",
            "  ```",
            "After.");
        assertThat(withoutCode(text)).contains("[kept](a.md)", "[kept too](b.md)", "After.").doesNotContain("nowhere.md");
    }

    /**
     * {@code text} without its fenced code blocks, CommonMark-style: a fence of three or more backticks (optionally
     * indented) closes only on a line holding nothing but at least as many backticks, so a fence can hold shorter ones.
     */
    static String withoutCode(String text) {
        return text.replaceAll("(?ms)^[ \\t]*(`{3,})[^\\n]*$.*?^[ \\t]*\\1`*[ \\t]*$", "");
    }

    @Test void everyProductionPackageHasAnOwnershipContract() throws Exception {
        for (String module : List.of("app", "buddy", "sdk")) {
        Path source = ROOT.resolve("jasper-" + module + "/src/main/java/dev/jasper/" + module);
        try (var files = Files.walk(source)) {
            for (Path directory : files.filter(Files::isDirectory).toList()) {
                try (var children = Files.list(directory)) {
                    if (children.noneMatch(p -> p.toString().endsWith(".java"))) continue;
                }
                Path info = directory.resolve("package-info.java");
                assertThat(info).exists();
                assertThat(Files.readString(info)).contains("/**", "package dev.jasper." + module);
            }
        }
        }
    }
}
