package dev.jasper.app.workspace;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
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

    /**
     * Ruling 12: on Windows a UNC path makes the file check authenticate to its host over SMB, so a click never resolves a
     * path whose text names one: a printed {@code \\host\share} and an OSC 8 link such as {@code file:////host/share}
     * (which decodes to {@code //host/share}). Textual, so it holds on every OS.
     */
    @Test void onWindowsAPathThatNamesAHostIsRefusedBeforeAnyFileSystemWork() {
        for (String unc : List.of("\\\\attacker\\share\\README.md", "//attacker/share/README.md", "\\/attacker/x", "/\\attacker/x",
            "//ATTACKER/x", "\\\\attacker", "//attacker", "\\\\\\attacker\\share\\x", "///attacker/share/x", "//", "\\\\",
            "\\\\?\\UNC\\attacker\\share\\x", "\\\\.\\pipe\\x", "//localhost/c$/x", "//wsl.localhost.attacker/x", "//wsl/x")) {
            assertThat(FileLinks.resolve(unc, Optional.of(HERE), HOME, true)).as(unc + " with a directory").isEmpty();
            assertThat(FileLinks.resolve(unc, Optional.empty(), HOME, true)).as(unc + " without one").isEmpty();
        }
    }

    /** The WSL shares are this machine's own (launch/WslPaths), whatever the separators or case; the host rule lets them through. */
    @Test void onWindowsTheWslSharesAreNotRefusedAsHosts() {
        for (String wsl : List.of("\\\\wsl.localhost\\Ubuntu\\home\\me\\a.txt", "//wsl$/Ubuntu/x", "\\/WSL.LOCALHOST/Ubuntu/x",
            "//Wsl$\\Ubuntu\\x")) {
            assertThat(FileLinks.resolve(wsl, Optional.of(HERE), HOME, true)).as(wsl).isPresent();
        }
    }

    @Test @EnabledOnOs(OS.WINDOWS) void onWindowsTheWslSharesResolveToTheirUncPaths() {
        assertThat(FileLinks.resolve("\\\\wsl.localhost\\Ubuntu\\home\\me\\a.txt", Optional.empty(), HOME, true))
            .contains(Path.of("\\\\wsl.localhost\\Ubuntu\\home\\me\\a.txt"));
        assertThat(FileLinks.resolve("//wsl$/Ubuntu/x", Optional.empty(), HOME, true)).contains(Path.of("\\\\wsl$\\Ubuntu\\x"));
    }

    /** A relative path's host comes from the pane's own directory, not from program output, even when that is a share. */
    @Test void aRelativePathUnderAPanesNetworkShareStillResolvesOnWindows() {
        Path share = Path.of("\\\\fileserver\\team\\project");
        assertThat(FileLinks.resolve("a.txt", Optional.of(share), HOME, true)).contains(share.resolve("a.txt"));
        assertThat(FileLinks.resolve("src\\a.txt", Optional.of(share), HOME, true)).isPresent();
    }

    /** Off Windows two leading slashes are one: {@code //tmp/x} is {@code /tmp/x}, not a host. */
    @Test @DisabledOnOs(OS.WINDOWS) void elsewhereTwoLeadingSlashesAreAnOrdinaryPath() {
        assertThat(FileLinks.resolve("//tmp/x", Optional.empty(), HOME, false)).contains(Path.of("/tmp/x"));
        assertThat(FileLinks.resolve("//attacker/share/README.md", Optional.empty(), HOME, false))
            .contains(Path.of("/attacker/share/README.md"));
    }
}
