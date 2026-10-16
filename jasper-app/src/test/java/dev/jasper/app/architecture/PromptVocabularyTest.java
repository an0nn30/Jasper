package dev.jasper.app.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Spec 8.1: after SDK 0.18's renames the old word for the prompt kind names only {@code Form.hero}, the heading slot
 * that SHEET forms fill too. Comments and literals (node ids such as {@code "form.hero.tile"}) are not names.
 */
class PromptVocabularyTest {
    static final Path ROOT = Path.of(System.getProperty("jasper.repoRoot"));
    static final String SLOT = "hero";
    static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_$][A-Za-z0-9_$]*");
    static final Pattern WORDS = Pattern.compile("_|(?<=[a-z0-9])(?=[A-Z])|(?<=[A-Z])(?=[A-Z][a-z])");
    static final List<String> TABLES = List.of("compact-roles.tsv", "default-type.tsv");

    /** True when one of the identifier's words is the slot's word and the identifier is not the slot itself. */
    static boolean saysTheSlotWord(String identifier) {
        return !identifier.equals(SLOT) && Arrays.stream(WORDS.split(identifier)).anyMatch(word -> word.equalsIgnoreCase(SLOT));
    }

    /** The source with its comments, string and character literals and text blocks blanked out. */
    static String code(String source) {
        var out = new StringBuilder(source.length());
        int i = 0, n = source.length();
        while (i < n) {
            if (source.startsWith("\"\"\"", i)) {
                int end = source.indexOf("\"\"\"", i + 3);
                i = end < 0 ? n : end + 3;
            } else if (source.startsWith("//", i)) {
                int end = source.indexOf('\n', i);
                i = end < 0 ? n : end;
            } else if (source.startsWith("/*", i)) {
                int end = source.indexOf("*/", i + 2);
                i = end < 0 ? n : end + 2;
            } else if (source.charAt(i) == '"' || source.charAt(i) == '\'') {
                char quote = source.charAt(i);
                int j = i + 1;
                while (j < n && source.charAt(j) != quote) j += source.charAt(j) == '\\' ? 2 : 1;
                i = j + 1;
            } else {
                out.append(source.charAt(i++));
                continue;
            }
            out.append(' ');
        }
        return out.toString();
    }

    @Test void identifiersAreSplitIntoTheirWords() {
        assertThat(List.of("HERO", "HERO_TITLE", "heroTile", "HeroLayout", "applyHero", "ICON_HERO", "nestedHeroFields"))
            .allMatch(PromptVocabularyTest::saysTheSlotWord);
        assertThat(List.of(SLOT, "theRows", "otherOsc", "sphere", "HEROIC", "PROMPT_TITLE")).noneMatch(PromptVocabularyTest::saysTheSlotWord);
    }

    @Test void commentsAndLiteralsAreNotCode() {
        String source = "a /* x */ b // y\n\"z\\\"w\" 'q' \"\"\"\nv\n\"\"\" c";
        assertThat(code(source).split("\\s+")).containsExactly("a", "b", "c");
    }

    @Test void onlyTheFormSlotKeepsTheOldWord() throws IOException {
        var offenders = new TreeSet<String>();
        var roots = new ArrayList<>(Stream.of("jasper-sdk/src", "jasper-sdk-testkit/src", "jasper-ui/src", "jasper-app/src").map(ROOT::resolve).toList());
        try (var plugins = Files.list(ROOT.resolve("plugins"))) {
            plugins.map(plugin -> plugin.resolve("src")).filter(Files::isDirectory).forEach(roots::add);
        }
        for (Path root : roots) try (Stream<Path> files = Files.walk(root)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                var identifiers = IDENTIFIER.matcher(code(Files.readString(file)));
                while (identifiers.find())
                    if (saysTheSlotWord(identifiers.group())) offenders.add(ROOT.relativize(file) + ": " + identifiers.group());
            }
        }
        for (String table : TABLES)
            for (String row : Files.readAllLines(ROOT.resolve("jasper-ui/src/test/resources/dev/jasper/ui/metrics/" + table))) {
                String key = row.split("\t", 2)[0];
                if (saysTheSlotWord(key)) offenders.add(table + ": " + key);
            }
        assertThat(offenders).as("names that still use the slot's word for the prompt kind").isEmpty();
    }
}
