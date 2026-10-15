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
            "\\\\?\\UNC\\attacker\\share\\x", "\\\\.\\pipe\\x", "//localhost/c$/x", "//wsl.localhost.attacker/x", "//wsl/x",
            // A ~ prefix is home only when what follows is not itself a UNC path: WindowsPath.resolve returns an absolute one.
            "~///attacker/share/README.md", "~/\\\\attacker\\share\\README.md", "~\\\\\\attacker\\share\\README.md",
            "~/\\/attacker/x", "~\\//attacker/x")) {
            assertThat(FileLinks.resolve(unc, Optional.of(HERE), HOME, true)).as(unc + " with a directory").isEmpty();
            assertThat(FileLinks.resolve(unc, Optional.empty(), HOME, true)).as(unc + " without one").isEmpty();
        }
    }

    /** The WSL shares are this machine's own (launch/WslPaths), whatever the separators or case; the host rule lets them through. */
    @Test void onWindowsTheWslSharesAreNotRefusedAsHosts() {
        for (String wsl : List.of("\\\\wsl.localhost\\Ubuntu\\home\\me\\a.txt", "//wsl$/Ubuntu/x", "\\/WSL.LOCALHOST/Ubuntu/x",
            "//Wsl$\\Ubuntu\\x", "~/\\\\wsl.localhost\\Ubuntu\\x", "~/a.txt", "~\\a.txt")) {
            assertThat(FileLinks.resolve(wsl, Optional.of(HERE), HOME, true)).as(wsl).isPresent();
        }
    }

    /** The WSL exemption names a distribution's share, never {@code .} or {@code ..}, which Windows resolves to another host. */
    @Test void onWindowsADotShareUnderAWslHostIsRefused() {
        for (String text : List.of("\\\\wsl.localhost\\..\\x", "\\\\wsl$\\.\\x", "//wsl.localhost/../attacker/share", "//wsl$/./x",
            "\\\\WSL.LOCALHOST\\\\..\\x", "~/\\\\wsl$\\..\\x")) {
            assertThat(FileLinks.resolve(text, Optional.of(HERE), HOME, true)).as(text).isEmpty();
        }
        Path dotShare = Path.of("\\\\wsl.localhost\\..\\x");
        assertThat(FileLinks.resolve("a.txt", Optional.of(dotShare), HOME, true)).as("as a pane directory").isEmpty();
    }

    /**
     * Ruling 14: {@code equalsIgnoreCase} folds U+017F (long s) to {@code s}, but Windows does not, so such a name is not
     * the WSL share: it falls through to SMB and WebDAV name resolution. A host is exempt only when it is all ASCII.
     * The long s is written as a Java escape.
     */
    @Test void onWindowsAHostThatOnlyCaseFoldsToWslIsRefused() {
        for (String host : List.of("w\u017fl$", "w\u017fl.localhost", "wsl.localho\u017ft", "W\u017fL.LOCALHOST", "wsl.local\u017fhost")) {
            String backslashes = "\\\\" + host + "\\Ubuntu\\x";
            String slashes = "//" + host + "/Ubuntu/x";
            for (String text : List.of(backslashes, slashes, "~/" + backslashes, "~\\" + backslashes, "~/" + slashes)) {
                assertThat(FileLinks.resolve(text, Optional.of(HERE), HOME, true)).as(text).isEmpty();
                assertThat(FileLinks.resolve(text, Optional.empty(), HOME, true)).as(text + " without a directory").isEmpty();
            }
            Path directory = Path.of(backslashes);
            assertThat(FileLinks.resolve("a.txt", Optional.of(directory), HOME, true)).as("as a pane directory: " + backslashes).isEmpty();
            Path home = Path.of(backslashes);
            assertThat(FileLinks.resolve("~/a.txt", Optional.empty(), home, true)).as("as a home: " + backslashes).isEmpty();
        }
        assertThat("w\u017fl$".equalsIgnoreCase("wsl$")).as("the premise: Java folds the long s").isTrue();
        assertThat(FileLinks.resolve("\\\\wsl$\\Ubuntu\\x", Optional.of(HERE), HOME, true)).as("the real share").isPresent();
    }

    /**
     * The final path's text is checked too: whatever the branch that built it, a result that names a host is refused. A
     * home directory on a share therefore resolves no {@code ~/} path, as a pane directory on one resolves no relative path.
     */
    @Test void onWindowsAResultThatNamesAHostIsRefusedWhicheverBranchBuiltIt() {
        Path shareHome = Path.of("\\\\fileserver\\home\\me");
        assertThat(FileLinks.resolve("~/a.txt", Optional.of(HERE), shareHome, true)).isEmpty();
        assertThat(FileLinks.resolve("~\\a.txt", Optional.empty(), shareHome, true)).isEmpty();
        assertThat(FileLinks.resolve("~/a.txt", Optional.of(HERE), shareHome, false)).as("elsewhere it is only a name").isPresent();
        Path wslHome = Path.of("\\\\wsl.localhost\\Ubuntu\\home\\me");
        assertThat(FileLinks.resolve("~/a.txt", Optional.empty(), wslHome, true)).contains(wslHome.resolve("a.txt"));
    }

    /** With a real Windows home: an absolute UNC remainder after {@code ~} would be returned by {@code resolve} as it is. */
    @Test @EnabledOnOs(OS.WINDOWS) void onWindowsATildeBeforeAnAbsoluteUncPathIsRefusedAndAWslOneResolves() {
        Path home = Path.of("C:\\Users\\me");
        for (String text : List.of("~///attacker/share/README.md", "~/\\\\attacker\\share\\README.md", "~\\\\\\attacker\\share\\README.md")) {
            assertThat(FileLinks.resolve(text, Optional.empty(), home, true)).as(text).isEmpty();
        }
        assertThat(FileLinks.resolve("~/x/a.txt", Optional.empty(), home, true)).contains(Path.of("C:\\Users\\me\\x\\a.txt"));
        assertThat(FileLinks.resolve("~/\\\\wsl.localhost\\Ubuntu\\x", Optional.empty(), home, true))
            .contains(Path.of("\\\\wsl.localhost\\Ubuntu\\x"));
    }

    @Test @EnabledOnOs(OS.WINDOWS) void onWindowsTheWslSharesResolveToTheirUncPaths() {
        assertThat(FileLinks.resolve("\\\\wsl.localhost\\Ubuntu\\home\\me\\a.txt", Optional.empty(), HOME, true))
            .contains(Path.of("\\\\wsl.localhost\\Ubuntu\\home\\me\\a.txt"));
        assertThat(FileLinks.resolve("//wsl$/Ubuntu/x", Optional.empty(), HOME, true)).contains(Path.of("\\\\wsl$\\Ubuntu\\x"));
    }

    /**
     * Ruling 13: the pane's directory is program output too (any program can print an OSC 7 report that becomes it), so a
     * relative path under a directory whose text names a host is refused on Windows by the same rule as the path itself.
     * Textual: these directories keep their text as a {@code Path} on every OS (a POSIX {@code Path} would collapse
     * {@code //host}, so the forward-slash forms are in the Windows-only test below).
     */
    @Test void onWindowsARelativePathUnderADirectoryThatNamesAHostIsRefused() {
        for (String share : List.of("\\\\fileserver\\team\\project", "\\\\ATTACKER\\x\\y", "\\/attacker/x", "/\\attacker/x")) {
            Optional<Path> directory = Optional.of(Path.of(share));
            assertThat(FileLinks.resolve("a.txt", directory, HOME, true)).as("a.txt under " + share).isEmpty();
            assertThat(FileLinks.resolve("src\\a.txt", directory, HOME, true)).as("src\\a.txt under " + share).isEmpty();
            assertThat(FileLinks.resolve("./a.txt", directory, HOME, true)).as("./a.txt under " + share).isEmpty();
        }
    }

    @Test @EnabledOnOs(OS.WINDOWS) void onWindowsAForwardSlashShareDirectoryIsRefusedToo() {
        for (String share : List.of("//attacker/share", "//ATTACKER/x/y", "///attacker/share")) {
            assertThat(FileLinks.resolve("a.txt", Optional.of(Path.of(share)), HOME, true)).as(share).isEmpty();
        }
    }

    /** The directory is only consulted for a relative path: {@code ~/} and absolute paths never touch it. */
    @Test void aDirectoryThatNamesAHostDoesNotAffectPathsThatDoNotUseIt() {
        Optional<Path> share = Optional.of(Path.of("\\\\fileserver\\team\\project"));
        Path absolute = HOME.resolve("logs").resolve("x.log");
        assertThat(FileLinks.resolve("~/a.txt", share, HOME, true)).contains(HOME.resolve("a.txt"));
        assertThat(FileLinks.resolve(absolute.toString(), share, HOME, true)).contains(absolute);
    }

    /** A pane in a WSL distribution's share has this machine's own directory, so a relative path under it resolves. */
    @Test void aRelativePathUnderAWslShareStillResolvesOnWindows() {
        for (String wsl : List.of("\\\\wsl.localhost\\Ubuntu\\home\\me", "\\\\wsl$\\Ubuntu\\home\\me", "\\\\WSL.LOCALHOST\\Ubuntu\\home")) {
            Path directory = Path.of(wsl);
            assertThat(FileLinks.resolve("a.txt", Optional.of(directory), HOME, true)).as(wsl).contains(directory.resolve("a.txt"));
        }
    }

    /** Off Windows the directory's text is not a host, however it is written. */
    @Test void elsewhereADirectoryIsNeverRefusedForItsText() {
        Path directory = Path.of("\\\\fileserver\\team\\project");
        assertThat(FileLinks.resolve("a.txt", Optional.of(directory), HOME, false)).contains(directory.resolve("a.txt"));
    }

    /** Off Windows two leading slashes are one: {@code //tmp/x} is {@code /tmp/x}, not a host. */
    @Test @DisabledOnOs(OS.WINDOWS) void elsewhereTwoLeadingSlashesAreAnOrdinaryPath() {
        assertThat(FileLinks.resolve("//tmp/x", Optional.empty(), HOME, false)).contains(Path.of("/tmp/x"));
        assertThat(FileLinks.resolve("//attacker/share/README.md", Optional.empty(), HOME, false))
            .contains(Path.of("/attacker/share/README.md"));
    }
}
