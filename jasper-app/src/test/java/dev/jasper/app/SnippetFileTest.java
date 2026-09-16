package dev.jasper.app;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class SnippetFileTest {
    @Test void snippetsValidateNamesAndCommandsAndExtractPlaceholdersInOrder() {
        var snippet = new Snippet("  Rebase onto main ", "git fetch && git rebase origin/{{branch}} # {{branch}} \\{{literal}} {{1bad}} {{ok_2}}", List.of("git"));
        assertThat(snippet.name()).isEqualTo("Rebase onto main");
        assertThat(snippet.key()).isEqualTo("rebase onto main");
        assertThat(snippet.placeholders()).containsExactly("branch", "ok_2");
        assertThat(snippet.fill(Map.of("branch", "main", "ok_2", "x")))
            .isEqualTo("git fetch && git rebase origin/main # main {{literal}} {{1bad}} x");
        assertThat(snippet.fill(Map.of())).isEqualTo("git fetch && git rebase origin/ #  {{literal}} {{1bad}} ");
        assertThat(new Snippet("plain", "ls", List.of()).placeholders()).isEmpty();
        assertThatIllegalArgumentException().isThrownBy(() -> new Snippet(" ", "ls", List.of()));
        assertThatIllegalArgumentException().isThrownBy(() -> new Snippet("a\nb", "ls", List.of()));
        assertThatIllegalArgumentException().isThrownBy(() -> new Snippet("x".repeat(129), "ls", List.of()));
        assertThatIllegalArgumentException().isThrownBy(() -> new Snippet("ok", " ", List.of()));
        assertThatIllegalArgumentException().isThrownBy(() -> new Snippet("ok", "x".repeat(Snippet.MAX_COMMAND + 1), List.of()));
        assertThatIllegalArgumentException().isThrownBy(() -> new Snippet("ok", "ls", List.of(" ")));
    }

    @Test void parseIsLenientPerEntryAndStrictPerFile() throws Exception {
        String text = """
            # comment
            [[snippet]]
            name = "One"
            command = "echo 1"
            keywords = ["a", "b"]

            [[snippet]]
            name = "one"
            command = "duplicate"

            [[snippet]]
            command = "no name"

            [[snippet]]
            name = "Bad keywords"
            command = "x"
            keywords = [1]

            [[snippet]]
            name = "Multi"
            command = \"\"\"
            line one
            line two\"\"\"
            """;
        var parsed = SnippetFile.parse(text);
        assertThat(parsed.snippets()).extracting(Snippet::name).containsExactly("One", "Multi");
        assertThat(parsed.snippets().getFirst().keywords()).containsExactly("a", "b");
        assertThat(parsed.snippets().get(1).command()).isEqualTo("line one\nline two");
        assertThat(parsed.warnings()).hasSize(3);
        assertThat(parsed.warnings().getFirst()).contains("snippet 2").contains("duplicate");
        assertThat(SnippetFile.parse("").snippets()).isEmpty();
        assertThat(SnippetFile.parse("snippet = 3\n").warnings()).hasSize(1);
        assertThatThrownBy(() -> SnippetFile.parse("[[snippet]\nname = 'x'")).isInstanceOf(IOException.class);
    }

    @Test void appendKeepsExistingBytesAndRoundTrips() throws Exception {
        String existing = "# my notes\n[[snippet]]\nname = \"One\"\ncommand = \"echo 1\"";
        var added = new Snippet("Quote \"it\"", "printf '%s\\n' \"{{text}}\"\ttab", List.of("k1", "k2"));
        String appended = SnippetFile.append(existing, added);
        assertThat(appended).startsWith(existing + "\n\n[[snippet]]\n");
        assertThat(appended).contains("name = \"Quote \\\"it\\\"\"");
        assertThat(appended).contains("keywords = [\"k1\", \"k2\"]");
        var parsed = SnippetFile.parse(appended);
        assertThat(parsed.warnings()).isEmpty();
        assertThat(parsed.snippets()).hasSize(2);
        assertThat(parsed.snippets().get(1)).isEqualTo(added);
        String fresh = SnippetFile.append("", new Snippet("First", "ls", List.of()));
        assertThat(fresh).startsWith(SnippetFile.HEADER);
        assertThat(SnippetFile.parse(fresh).snippets()).extracting(Snippet::name).containsExactly("First");
        assertThat(SnippetFile.tomlString("a\nb")).isEqualTo("\"a\\nb\\u0001\"");
    }
}
