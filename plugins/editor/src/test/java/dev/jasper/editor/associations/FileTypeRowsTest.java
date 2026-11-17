package dev.jasper.editor.associations;

import dev.jasper.editor.associations.FileTypeRows.Row;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class FileTypeRowsTest {
    @Test void theBuiltInRowsAreTheCopyTableInOrder() {
        assertThat(FileTypeRows.BUILT_IN).containsExactly(
            new Row("java", "Java", List.of(".java"), false),
            new Row("kotlin", "Kotlin", List.of(".kt", ".kts"), false),
            new Row("c", "C", List.of(".c", ".h"), false),
            new Row("c++", "C++", List.of(".cpp", ".cc", ".cxx", ".hpp", ".hh"), false),
            new Row("python", "Python", List.of(".py"), false),
            new Row("go", "Go", List.of(".go"), false),
            new Row("rust", "Rust", List.of(".rs"), false),
            new Row("javascript", "JavaScript", List.of(".js", ".mjs"), false),
            new Row("shell", "Shell", List.of(".sh", ".zsh", ".bash"), false),
            new Row("markdown", "Markdown", List.of(".md", ".markdown"), false),
            new Row("json", "JSON", List.of(".json"), false),
            new Row("yaml", "YAML", List.of(".yaml", ".yml"), false),
            new Row("toml", "TOML", List.of(".toml"), false),
            new Row("xml", "XML", List.of(".xml"), false),
            new Row("plain text", "Plain text", List.of(".txt", ".log"), false));
    }

    @Test void withUserAppendsOneRowPerExtensionInTheOrderSaved() {
        var rows = FileTypeRows.withUser(List.of(".proto", ".gradle"));
        assertThat(rows.subList(0, FileTypeRows.BUILT_IN.size())).isEqualTo(FileTypeRows.BUILT_IN);
        assertThat(rows.subList(FileTypeRows.BUILT_IN.size(), rows.size())).containsExactly(
            new Row("user:.proto", ".proto", List.of(".proto"), true),
            new Row("user:.gradle", ".gradle", List.of(".gradle"), true));
    }

    @Test void normalisingLowerCasesAndAddsTheDot() {
        assertThat(FileTypeRows.normalised("JAVA")).isEqualTo(".java");
        assertThat(FileTypeRows.normalised(".Proto")).isEqualTo(".proto");
    }

    @Test void addErrorsAreTheCopyTablesText() {
        assertThat(FileTypeRows.addError(".JAVA", List.of())).contains(".java is already in the list");
        assertThat(FileTypeRows.addError("java", List.of())).contains(".java is already in the list");
        assertThat(FileTypeRows.addError(".html", List.of())).contains(".html opens in Jasper's browser");
        assertThat(FileTypeRows.addError(".htm", List.of())).contains(".htm opens in Jasper's browser");
        String invalid = "Use a dot and up to 16 letters, digits, _, + or -";
        assertThat(FileTypeRows.addError(".a b", List.of())).contains(invalid);
        assertThat(FileTypeRows.addError(".", List.of())).contains(invalid);
        assertThat(FileTypeRows.addError("." + "a".repeat(17), List.of())).contains(invalid);
        assertThat(FileTypeRows.addError("." + "a".repeat(16), List.of())).isEmpty();
        assertThat(FileTypeRows.addError(".gradle", List.of(".gradle"))).contains(".gradle is already in the list");
        assertThat(FileTypeRows.addError(".proto", List.of())).isEmpty();
        assertThat(FileTypeRows.addError("c++", List.of())).isEmpty();
    }
}
