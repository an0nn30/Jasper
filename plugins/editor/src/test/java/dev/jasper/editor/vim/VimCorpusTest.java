package dev.jasper.editor.vim;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import static org.assertj.core.api.Assertions.assertThat;

/** Replays the differential corpus (spec 11.1): every case must do what Vim did, and every command needs a case. */
class VimCorpusTest {
    static List<CorpusCase> cases() {
        try (Stream<Path> files = Files.list(Path.of(VimCorpusTest.class.getResource("/vim/corpus").toURI()))) {
            return files.filter(file -> file.toString().endsWith(".case")).sorted().flatMap(file -> {
                try { return CorpusFile.read(file).stream(); }
                catch (IOException failed) { throw new UncheckedIOException(failed); }
            }).toList();
        } catch (IOException | URISyntaxException failed) {
            throw new IllegalStateException(failed);
        }
    }

    @TestFactory Stream<DynamicTest> everyCaseDoesWhatVimDid() {
        return cases().stream().map(c -> DynamicTest.dynamicTest(c.toString(), () -> {
            assertThat(c.expected()).as("%s has no expectation; run ./gradlew :jasper-plugin-editor:recordVimCorpus", c).isNotNull();
            assertThat(CorpusRunner.compare(c.expected(), CorpusRunner.run(c, id -> { }))).as("%s, keys %s", c, c.keys()).isEmpty();
        }));
    }

    @Test void everyCommandHasACase() {
        Set<String> used = new TreeSet<>();
        for (CorpusCase c : cases()) CorpusRunner.run(c, used::add);
        List<String> ids = new java.util.ArrayList<>(VimCommands.standard().entries().stream().map(VimCommands.Entry::id).toList());
        for (Ex.Def def : Ex.implemented()) ids.add("ex " + def.name());
        List<String> missing = ids.stream().filter(id -> !used.contains(id)).toList();
        assertThat(missing).as("commands with no corpus case").isEmpty();
    }

    @Test void theCorpusFormatReadsBackWhatItWrites() {
        var lines = List.of("=== trailing blanks", "set: sw=4", "register a: l \"x\\n\"", "text:", "  ends in space $", "  $", "  cost $$",
            "cursor: 1,2", "keys: d<Esc>", "registers: \" a", "output: yes", "--- vim 9.1", "expect-text:", "  done", "expect-cursor: 1,1",
            "expect-mode: insert", "expect-output:", "  said $", "expect-register \": c \"one\\ttwo\"", "expect-register a: none");
        CorpusCase c = CorpusFile.parse("inline", lines).getFirst();
        assertThat(c.text()).containsExactly("ends in space ", "", "cost $");
        assertThat(c.given().get('a')).isEqualTo(new CorpusCase.Register("l", "x\n"));
        assertThat(c.expected().registers()).containsOnlyKeys('"');
        assertThat(c.expected().registers().get('"').text()).isEqualTo("one\ttwo");
        assertThat(c.output()).isTrue();
        assertThat(c.expected().output()).containsExactly("said ");
        assertThat(CorpusFile.format(c, c.expected())).containsExactly("--- vim 9.1", "expect-text:", "  done", "expect-cursor: 1,1",
            "expect-mode: insert", "expect-output:", "  said $", "expect-register \": c \"one\\ttwo\"", "expect-register a: none");
        assertThat(RecordCorpus.vimKeys("a\"b\\<Esc><C-w>")).isEqualTo("a\\\"b\\\\\\<Esc>\\<C-w>");
        assertThat(RecordCorpus.mode("no")).isEqualTo("operator-pending");
    }
}
