package dev.jasper.browser.downloads;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spec section 17 and ruling F37: the Downloads folder setting is empty for the system's own folder, {@code ~} or
 * {@code ~/folder} under the home folder, or an absolute folder as typed; anything else is refused. The Windows rows run
 * on every system, through the {@code os.name} seam. Nothing here touches the disk: the folders are paths under a
 * {@code @TempDir} that are never created (F15).
 */
class DownloadFolderTest {
    private static final String MAC = "Mac OS X", LINUX = "Linux", WINDOWS = "Windows 11";
    @TempDir Path temp;

    @Test void emptyIsTheSystemsFolderTildeIsTheHomeFolderAndAnAbsoluteFolderIsAsTyped() {
        Path system = temp.resolve("system/Downloads"), home = temp.resolve("home");
        for (String os : List.of(MAC, LINUX, WINDOWS)) {
            assertThat(DownloadFolder.resolve("", system, home, os)).as(os).isEqualTo(system);
            assertThat(DownloadFolder.resolve("~", system, home, os)).as(os).isEqualTo(home);
            assertThat(DownloadFolder.resolve("~/", system, home, os)).as(os).isEqualTo(home);
            assertThat(DownloadFolder.resolve("~/Saved", system, home, os)).as(os).isEqualTo(home.resolve("Saved"));
            assertThat(DownloadFolder.resolve("~//Saved", system, home, os)).as(os + ": a doubled slash stays under home")
                .isEqualTo(home.resolve("Saved"));
        }
        String absolute = temp.resolve("elsewhere/../files").toString();
        assertThat(DownloadFolder.resolve(absolute, system, home, MAC)).as("as typed").isEqualTo(Path.of(absolute));
        assertThat(DownloadFolder.resolve("/srv/files", system, home, LINUX)).isEqualTo(Path.of("/srv/files"));
        assertThat(DownloadFolder.resolve("~\\Saved", system, home, WINDOWS)).isEqualTo(home.resolve("Saved"));
        assertThat(DownloadFolder.resolve("C:\\Users\\me\\Saved", system, home, WINDOWS)).isEqualTo(Path.of("C:\\Users\\me\\Saved"));
        assertThat(DownloadFolder.resolve("\\\\server\\share\\in", system, home, WINDOWS)).isEqualTo(Path.of("\\\\server\\share\\in"));
    }

    @Test void onMacOsAndLinuxAnAbsoluteFolderOrTheHomeFolderIsValid() {
        for (String os : List.of(MAC, LINUX)) {
            for (String setting : List.of("", "~", "~/", "~/Saved", "~/Saved/Browser", "/", "/Users/me/Saved", "/srv/my files", "/a/../b",
                    "/srv/Saved ", "~/Saved /in"))
                assertThat(DownloadFolder.valid(setting, os)).as(os + ": " + setting).isTrue();
            for (String setting : List.of("relative/dir", "Saved", "./Saved", "../Saved", "~\\Saved", "~user/Saved", "~Saved", " ", " /srv",
                    "C:\\Users\\me", "C:/Users/me", "\\\\server\\share", "/srv/\u0000x"))
                assertThat(DownloadFolder.valid(setting, os)).as(os + ": " + setting).isFalse();
        }
    }

    @Test void onWindowsADriveOrShareFolderOrTheHomeFolderIsValid() {
        for (String setting : List.of("", "~", "~/", "~\\", "~/Saved", "~\\Saved", "~\\Saved\\Browser", "C:\\", "C:\\Users\\me\\Saved",
                "d:/Saved", "C:\\Saved/Browser", "\\\\server\\share", "\\\\server\\share\\in", "//server/share/in", "\\\\fs.example.com\\share",
                "C:\\My Files\\Downloads", "~\\My Files"))
            assertThat(DownloadFolder.valid(setting, WINDOWS)).as(setting).isTrue();
        for (String setting : List.of("relative/dir", "relative\\dir", "Saved", "C:x", "C:", "C:Saved\\Browser", "\\Saved", "/Saved",
                "\\\\server", "\\\\server\\", "\\\\?\\C:\\Saved", "\\\\.\\pipe\\x", "C:\\a:b", "C:\\a|b", "C:\\a*b", "C:\\a?b", "C:\\a<b",
                "C:\\a\"b", "C:\\a\tb", "~user\\Saved", "~\\a:b", "~/a|b", "1:\\Saved"))
            assertThat(DownloadFolder.valid(setting, WINDOWS)).as(setting).isFalse();
    }

    /**
     * Task 2's review: Windows refuses a name that ends in a space, before a separator or at the end, so Java's
     * {@code WindowsPathParser} throws "Trailing char" for it. Such a setting is refused, and resolves as the default.
     */
    @Test void onWindowsANameEndingInASpaceIsRefused() {
        Path system = temp.resolve("system/Downloads"), home = temp.resolve("home");
        for (String setting : List.of("C:\\Saved ", "C:\\Saved \\in", "C:/Saved /in", "D:\\My Files \\Downloads", "C:\\ ", "~\\Saved ",
                "~/Saved ", "~\\Saved \\in", "~\\ \\in", "\\\\srv\\share ", "\\\\srv \\share", "\\\\srv\\share\\in ")) {
            assertThat(DownloadFolder.valid(setting, WINDOWS)).as(setting).isFalse();
            assertThat(DownloadFolder.resolve(setting, system, home, WINDOWS)).as(setting).isEqualTo(system);
        }
    }

    /** Task 2's review: a path this system cannot read, which {@code valid} should have refused, resolves as the default. */
    @Test void aPathThatCannotBeReadResolvesAsTheSystemsFolder() {
        Path system = temp.resolve("system/Downloads"), home = temp.resolve("home");
        java.util.function.Function<String, Path> refusing = text -> {
            throw new InvalidPathException(text, "Trailing char < >");
        };
        for (String os : List.of(MAC, LINUX, WINDOWS)) {
            assertThat(DownloadFolder.resolve(temp.resolve("Saved").toString(), system, home, os, refusing)).as(os).isEqualTo(system);
            assertThat(DownloadFolder.resolve("~/Saved", system, home, os, refusing)).as(os).isEqualTo(system);
            assertThat(DownloadFolder.resolve("", system, home, os, refusing)).as(os + ": nothing to read").isEqualTo(system);
        }
        assertThat(DownloadFolder.resolve("~/Saved", system, home, MAC, Path::of)).as("the parser that reads").isEqualTo(home.resolve("Saved"));
    }

    @Test void aSettingThatIsNoneOfTheFormsResolvesAsTheDefaultTheSystemsFolder() {
        Path system = temp.resolve("system/Downloads"), home = temp.resolve("home");
        assertThat(DownloadFolder.resolve("relative/dir", system, home, LINUX)).isEqualTo(system);
        assertThat(DownloadFolder.resolve("~\\Saved", system, home, MAC)).isEqualTo(system);
        assertThat(DownloadFolder.resolve("C:x", system, home, WINDOWS)).isEqualTo(system);
    }

    @Test void theOverloadsWithoutTheSeamReadThisSystem() {
        Path system = temp.resolve("system/Downloads"), home = temp.resolve("home");
        assertThat(DownloadFolder.valid(temp.toString())).as("a @TempDir is absolute here").isTrue();
        assertThat(DownloadFolder.valid("")).isTrue();
        assertThat(DownloadFolder.valid("~/Saved")).isTrue();
        assertThat(DownloadFolder.valid("relative/dir")).isFalse();
        assertThat(DownloadFolder.valid(null)).isFalse();
        assertThat(DownloadFolder.resolve(temp.toString(), system, home)).isEqualTo(temp);
        assertThat(DownloadFolder.resolve("", system, home)).isEqualTo(system);
        assertThat(DownloadFolder.resolve("~/Saved", system, home)).isEqualTo(home.resolve("Saved"));
    }
}
