package dev.jasper.editor.command;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.editor.command.EditorArguments.Help;
import dev.jasper.editor.command.EditorArguments.Invalid;
import dev.jasper.editor.command.EditorArguments.Open;
import dev.jasper.editor.command.EditorArguments.Target;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

class EditorArgumentsTest {
    private static final Path HERE = Path.of("/work").toAbsolutePath();

    private static EditorArguments.Request parse(Set<Path> files, String... arguments) {
        return EditorArguments.parse(List.of(arguments), HERE, files::contains);
    }

    @Test void filesResolveFromTheWorkingDirectoryAndMayNameALineAndColumn() {
        assertThat(parse(Set.of(), "notes.txt", "src/Foo.java:42", "a.c:3:7", "/abs/b.txt")).isEqualTo(new Open(false, List.of(
            new Target(HERE.resolve("notes.txt"), 0, 0),
            new Target(HERE.resolve("src/Foo.java").normalize(), 42, 0),
            new Target(HERE.resolve("a.c"), 3, 7),
            new Target(HERE.resolve("/abs/b.txt").normalize(), 0, 0))));
    }

    @Test @DisabledOnOs(value = OS.WINDOWS, disabledReason = "a colon is not part of a Windows file name")
    void anExistingFileIsTakenAsWrittenEvenWithAColonAndDigits() {
        Path odd = HERE.resolve("a:1");
        assertThat(parse(Set.of(odd), "a:1")).isEqualTo(new Open(false, List.of(new Target(odd, 0, 0))));
        assertThat(parse(Set.of(), "a:1")).isEqualTo(new Open(false, List.of(new Target(HERE.resolve("a"), 1, 0))));
        assertThat(parse(Set.of(), "a:12345678901")).as("too many digits for a line")
            .isEqualTo(new Open(false, List.of(new Target(HERE.resolve("a:12345678901"), 0, 0))));
    }

    @Test void aDriveLetterIsNeverALine() {
        assertThat(EditorArguments.target("C:\\x", HERE, path -> false).line()).isZero();
        assertThat(EditorArguments.target("C:\\x:5", HERE, path -> false).line()).isEqualTo(5);
    }

    @Test void waitHelpAndOptions() {
        assertThat(parse(Set.of(), "--wait", "a")).isEqualTo(new Open(true, List.of(new Target(HERE.resolve("a"), 0, 0))));
        assertThat(parse(Set.of(), "a", "-w")).isEqualTo(new Open(true, List.of(new Target(HERE.resolve("a"), 0, 0))));
        assertThat(parse(Set.of(), "--help")).isEqualTo(new Help());
        assertThat(parse(Set.of(), "-h")).isEqualTo(new Help());
        assertThat(parse(Set.of(), "--wait")).isEqualTo(new Invalid("--wait needs a file"));
        assertThat(parse(Set.of(), "--bogus", "a")).isEqualTo(new Invalid("unknown option --bogus"));
        assertThat(parse(Set.of(), "--", "--wait")).isEqualTo(new Open(false, List.of(new Target(HERE.resolve("--wait"), 0, 0))));
        assertThat(parse(Set.of())).isEqualTo(new Open(false, List.of()));
    }
}
