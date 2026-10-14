package dev.jasper.app.workspace;

import java.io.File;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import static org.assertj.core.api.Assertions.assertThat;

class FileLinksTest {
    private static final Path HOME = Path.of(System.getProperty("java.io.tmpdir")).toAbsolutePath().resolve("home");
    private static final Path HERE = HOME.resolve("project");

    @Test void aRelativePathIsUnderThePanesDirectoryAndNeedsOne() {
        assertThat(FileLinks.resolve("src/Foo.java", Optional.of(HERE), HOME, false)).contains(HERE.resolve("src").resolve("Foo.java"));
        assertThat(FileLinks.resolve("./notes.md", Optional.of(HERE), HOME, false)).contains(HERE.resolve("notes.md"));
        assertThat(FileLinks.resolve("../up/a.txt", Optional.of(HERE), HOME, false)).contains(HOME.resolve("up").resolve("a.txt"));
        assertThat(FileLinks.resolve("src/Foo.java", Optional.empty(), HOME, false)).as("no directory to resolve against").isEmpty();
    }

    @Test void homeAndAbsolutePathsNeedNoDirectory() {
        assertThat(FileLinks.resolve("~/a.txt", Optional.empty(), HOME, false)).contains(HOME.resolve("a.txt"));
        assertThat(FileLinks.resolve("~/x/../b.txt", Optional.of(HERE), HOME, false)).as("normalized").contains(HOME.resolve("b.txt"));
        Path absolute = HOME.resolve("logs").resolve("x.log");
        assertThat(FileLinks.resolve(absolute.toString(), Optional.empty(), HOME, false)).contains(absolute);
        assertThat(FileLinks.resolve(absolute + File.separator + ".." + File.separator + "y.log", Optional.of(HERE), HOME, false))
            .contains(HOME.resolve("logs").resolve("y.log"));
    }

    @Test void aPathThisFileSystemCannotHoldNamesNothing() {
        assertThat(FileLinks.resolve("a\0b.txt", Optional.of(HERE), HOME, false)).isEmpty();
    }

    @Test void onWindowsATildeAndABackslashMeanHomeToo() {
        assertThat(FileLinks.resolve("~\\a.txt", Optional.empty(), HOME, true)).contains(HOME.resolve("a.txt"));
        assertThat(FileLinks.resolve("~\\a.txt", Optional.empty(), HOME, false)).as("elsewhere it is a relative name").isEmpty();
    }

    @Test @EnabledOnOs(OS.WINDOWS) void driveAndBackslashPathsResolveOnWindows() {
        assertThat(FileLinks.resolve("C:\\src\\Foo.cs", Optional.empty(), HOME, true)).contains(Path.of("C:\\src\\Foo.cs"));
        assertThat(FileLinks.resolve("src\\Foo.cs", Optional.of(Path.of("D:\\work")), HOME, true)).contains(Path.of("D:\\work\\src\\Foo.cs"));
        assertThat(FileLinks.resolve("C:/src/Foo.cs", Optional.empty(), HOME, true)).contains(Path.of("C:\\src\\Foo.cs"));
    }
}
