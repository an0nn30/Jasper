package dev.jasper.app.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.app.persistence.LayoutsFile.Layouts;
import dev.jasper.app.persistence.LayoutsFile.Named;
import dev.jasper.app.persistence.WorkspaceFile.WindowRecord;
import dev.jasper.app.testsupport.LogRecorder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Full windows plan 4: layouts.toml keeps a default layout and named layouts, without bounds. */
class LayoutsFileTest {
    @TempDir Path dir;

    private static WindowRecord layout(WindowRecord window) {
        return LayoutsFile.asLayout(window);
    }

    private static WindowRecord main() {
        return WorkspaceFileTest.snapshot().window();
    }

    private static WindowRecord two() {
        return WorkspaceFileTest.parentTabs().window();
    }

    @Test void aDefaultAndTwoNamedLayoutsRoundTrip() throws Exception {
        Path file = dir.resolve("layouts.toml");
        var layouts = new Layouts(Optional.of(layout(main())), List.of(new Named("Work", main()), new Named("Say \"hi\"", two())));
        LayoutsFile.write(file, layouts);
        String text = Files.readString(file);
        assertThat(text).startsWith("version = 1\n\n[default]\nextended_state = 0\n");
        assertThat(text).contains("\n[[layouts]]\nname = \"Work\"\nextended_state = 0\n").contains("name = \"Say \\\"hi\\\"\"");
        assertThat(text).contains("[[layouts.tabs]]").contains("[[layouts.items]]").contains("[default.tool_windows]")
            .doesNotContain("width =");
        assertThat(LayoutsFile.read(file)).isEqualTo(layouts);
        assertThat(LayoutsFile.read(file).named().getFirst().layout().bounds()).isEmpty();
    }

    @Test void anAbsentFileReadsAsEmpty() {
        assertThat(LayoutsFile.read(dir.resolve("none.toml"))).isEqualTo(Layouts.EMPTY);
    }

    @Test void anUnreadableFileIsKeptAsideAndReadAsEmpty() throws Exception {
        Path file = dir.resolve("layouts.toml");
        Files.writeString(file, "this is = = not toml");
        try (var log = LogRecorder.of(LayoutsFile.class.getName())) {
            assertThat(LayoutsFile.read(file)).isEqualTo(Layouts.EMPTY);
        }
        assertThat(file).doesNotExist();
        assertThat(Files.readString(dir.resolve("layouts.toml.bad"))).isEqualTo("this is = = not toml");
    }

    @Test void aBadLayoutIsDroppedAlone() throws Exception {
        Path file = dir.resolve("layouts.toml");
        LayoutsFile.write(file, new Layouts(Optional.of(layout(main())), List.of(new Named("Good", main()), new Named("Bad", two()))));
        String text = Files.readString(file);
        int bad = text.indexOf("name = \"Bad\"");
        // Break the Bad layout's only reachable structure: its tabs.
        String broken = text.substring(0, bad) + text.substring(bad).replace("[[layouts.tabs]]", "[[layouts.nothing]]");
        Files.writeString(file, broken);
        try (var log = LogRecorder.of(WorkspaceFile.class.getName()); var own = LogRecorder.of(LayoutsFile.class.getName())) {
            Layouts read = LayoutsFile.read(file);
            assertThat(read.defaultLayout()).contains(layout(main()));
            assertThat(read.named()).extracting(Named::name).containsExactly("Good");
        }
        assertThat(dir.resolve("layouts.toml.bad")).doesNotExist();
    }

    @Test void aNameUsedTwiceOrBlankIsDropped() throws Exception {
        Path file = dir.resolve("layouts.toml");
        LayoutsFile.write(file, new Layouts(Optional.empty(), List.of(new Named("A", main()), new Named("A", two()), new Named("  ", main()))));
        try (var log = LogRecorder.of(LayoutsFile.class.getName())) {
            assertThat(LayoutsFile.read(file).named()).extracting(Named::name).containsExactly("A");
        }
    }

    @Test void aLayoutHasNoBounds() {
        WindowRecord with = main();
        assertThat(with.bounds()).isPresent();
        WindowRecord stripped = LayoutsFile.asLayout(with);
        assertThat(stripped.bounds()).isEmpty();
        assertThat(stripped.extendedState()).isZero();
        assertThat(stripped.layout()).isEqualTo(with.layout());
    }
}
