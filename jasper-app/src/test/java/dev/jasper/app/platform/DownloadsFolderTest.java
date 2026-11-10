package dev.jasper.app.platform;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

/**
 * Browser plan 7 Task 1c: {@code DownloadsFolder.of}, the pure rule behind {@code Platform.downloadsFolder}. The folders
 * are paths under {@code @TempDir} that are never created, read only through the given map; no test reads the user's own
 * home or {@code user-dirs.dirs} (F15).
 */
class DownloadsFolderTest {
    private static final Supplier<Optional<Path>> NO_KNOWN_FOLDER = () -> {
        throw new AssertionError("only Windows asks for the known folder");
    };

    @TempDir Path dir;
    private final Map<Path, String> files = new HashMap<>();
    private final List<Path> read = new ArrayList<>();
    private final Function<Path, Optional<String>> reader = path -> {
        read.add(path);
        return Optional.ofNullable(files.get(path));
    };

    private Path home() { return dir.resolve("home"); }

    private Path linux(Map<String, String> env, String userDirs) {
        Path config = env.containsKey("XDG_CONFIG_HOME") && Path.of(env.get("XDG_CONFIG_HOME")).isAbsolute()
            ? Path.of(env.get("XDG_CONFIG_HOME")) : home().resolve(".config");
        if (userDirs != null) files.put(config.resolve("user-dirs.dirs"), userDirs);
        return DownloadsFolder.of("Linux", home(), env, reader, NO_KNOWN_FOLDER);
    }

    private Path linux(String userDirs) { return linux(Map.of(), userDirs); }

    @Test void macOsGivesTheHomeDownloadsFolder() {
        Function<Path, Optional<String>> none = path -> { throw new AssertionError("macOS reads no file"); };
        assertThat(DownloadsFolder.of("Mac OS X", home(), Map.of("XDG_CONFIG_HOME", dir.toString()), none, NO_KNOWN_FOLDER))
            .isEqualTo(home().resolve("Downloads"));
    }

    @Test void linuxReadsXdgDownloadDirWithHomeExpanded() {
        assertThat(linux("""
            # This file is written by xdg-user-dirs-update
            XDG_DESKTOP_DIR="$HOME/Bureau"
            XDG_DOWNLOAD_DIR="$HOME/Téléchargements"
            XDG_TEMPLATES_DIR="$HOME/Modèles"
            """)).isEqualTo(home().resolve("Téléchargements"));
        assertThat(read).containsExactly(home().resolve(".config").resolve("user-dirs.dirs"));
    }

    @Test @DisabledOnOs(value = OS.WINDOWS, disabledReason = "a Linux path is not absolute on Windows")
    void linuxTakesAnAbsoluteFolder() {
        assertThat(linux("XDG_DOWNLOAD_DIR=\"/data/dl\"\n")).isEqualTo(Path.of("/data/dl"));
    }

    @Test void linuxReadsTheFileAsXdgUserDirLookupDoes() {
        assertThat(linux("  XDG_DOWNLOAD_DIR = \"$HOME/In Progress/\"  # where Firefox saves\r\n"))
            .as("spaces around =, a trailing slash, a comment and CRLF").isEqualTo(home().resolve("In Progress"));
        assertThat(linux("XDG_DOWNLOAD_DIR=\"$HOME/\\$5\\\\files\"\n")).as("backslash escapes")
            .isEqualTo(home().resolve("$5\\files"));
        assertThat(linux("XDG_DOWNLOAD_DIR=\"$HOME\"\n")).as("the home folder itself, as glib gives it").isEqualTo(home());
        assertThat(linux("XDG_DOWNLOAD_DIR=\"$HOME//dl\"\n")).as("a doubled slash stays under home").isEqualTo(home().resolve("dl"));
        assertThat(linux("XDG_DOWNLOAD_DIR=\"$HOME/Old\"\nXDG_DOWNLOAD_DIR=\"$HOME/New\"\n")).as("the last line wins")
            .isEqualTo(home().resolve("New"));
    }

    @Test void linuxIgnoresACommentedLine() {
        assertThat(linux("#XDG_DOWNLOAD_DIR=\"$HOME/Old\"\n  # XDG_DOWNLOAD_DIR=\"$HOME/Older\"\n"))
            .isEqualTo(home().resolve("Downloads"));
    }

    @Test void linuxFallsBackWithoutTheFile() {
        assertThat(linux(null)).isEqualTo(home().resolve("Downloads"));
        assertThat(DownloadsFolder.of("Linux", home(), Map.of(), path -> { throw new IllegalStateException("unreadable"); }, NO_KNOWN_FOLDER))
            .as("a read that fails").isEqualTo(home().resolve("Downloads"));
    }

    @Test void linuxIgnoresAMalformedValue() {
        for (String line : List.of(
                "XDG_DOWNLOAD_DIR=$HOME/dl",           // not quoted
                "XDG_DOWNLOAD_DIR=\"dl\"",             // relative
                "XDG_DOWNLOAD_DIR=\"$HOMEdl\"",        // $HOME glued to a name
                "XDG_DOWNLOAD_DIR=\"~/dl\"",           // a tilde, which the file never holds
                "XDG_DOWNLOAD_DIR=\"$HOME/dl",         // unclosed
                "XDG_DOWNLOAD_DIR \"$HOME/dl\"",       // no =
                "XDG_DOWNLOAD_DIRS=\"$HOME/dl\"",      // another key
                "XDG_DOWNLOAD_DIR=\"\"")) {            // empty
            files.clear();
            assertThat(linux(line + "\n")).as(line).isEqualTo(home().resolve("Downloads"));
        }
        files.clear();
        assertThat(linux("XDG_DOWNLOAD_DIR=\"$HOME/Good\"\nXDG_DOWNLOAD_DIR=\"bad\"\n")).as("a malformed line after a good one")
            .isEqualTo(home().resolve("Good"));
    }

    @Test void linuxReadsTheFileInXdgConfigHome() {
        Path config = dir.resolve("xdg");
        assertThat(linux(Map.of("XDG_CONFIG_HOME", config.toString()), "XDG_DOWNLOAD_DIR=\"$HOME/Fetched\"\n"))
            .isEqualTo(home().resolve("Fetched"));
        assertThat(read).containsExactly(config.resolve("user-dirs.dirs"));
    }

    @Test void linuxIgnoresARelativeOrEmptyXdgConfigHome() {
        files.put(home().resolve(".config").resolve("user-dirs.dirs"), "XDG_DOWNLOAD_DIR=\"$HOME/Fetched\"\n");
        for (String value : List.of("relative/xdg", "")) {
            read.clear();
            assertThat(DownloadsFolder.of("Linux", home(), Map.of("XDG_CONFIG_HOME", value), reader, NO_KNOWN_FOLDER))
                .as("XDG_CONFIG_HOME=" + value).isEqualTo(home().resolve("Fetched"));
            assertThat(read).containsExactly(home().resolve(".config").resolve("user-dirs.dirs"));
        }
    }

    @Test void otherUnixSystemsFollowXdgToo() {
        files.put(home().resolve(".config").resolve("user-dirs.dirs"), "XDG_DOWNLOAD_DIR=\"$HOME/dl\"\n");
        assertThat(DownloadsFolder.of("FreeBSD", home(), Map.of(), reader, NO_KNOWN_FOLDER)).isEqualTo(home().resolve("dl"));
    }

    @Test void windowsGivesTheKnownFolder() {
        Path known = dir.resolve("Known").resolve("Downloads");
        Function<Path, Optional<String>> none = path -> { throw new AssertionError("Windows reads no file"); };
        assertThat(DownloadsFolder.of("Windows 11", home(), Map.of("USERPROFILE", dir.resolve("profile").toString()), none,
            () -> Optional.of(known))).isEqualTo(known);
    }

    @Test void windowsFallsBackToTheUserProfileWhenTheKnownFolderFails() {
        Path profile = dir.resolve("profile");
        Map<String, String> env = Map.of("USERPROFILE", profile.toString());
        assertThat(DownloadsFolder.of("Windows 11", home(), env, reader, Optional::empty)).as("no known folder")
            .isEqualTo(profile.resolve("Downloads"));
        assertThat(DownloadsFolder.of("Windows Server 2022", home(), env, reader, () -> { throw new IllegalStateException("E_FAIL"); }))
            .as("the call throws").isEqualTo(profile.resolve("Downloads"));
        assertThat(DownloadsFolder.of("Windows 11", home(), env, reader, () -> Optional.of(Path.of("relative"))))
            .as("a relative answer").isEqualTo(profile.resolve("Downloads"));
        assertThat(read).isEmpty();
    }

    @Test void windowsFallsBackToHomeWithoutAUserProfile() {
        assertThat(DownloadsFolder.of("Windows 11", home(), Map.of(), reader, Optional::empty)).isEqualTo(home().resolve("Downloads"));
        assertThat(DownloadsFolder.of("Windows 11", home(), Map.of("USERPROFILE", "relative"), reader, Optional::empty))
            .as("a relative profile").isEqualTo(home().resolve("Downloads"));
        assertThat(DownloadsFolder.of("Windows 11", home(), Map.of("USERPROFILE", ""), reader, Optional::empty))
            .as("an empty profile").isEqualTo(home().resolve("Downloads"));
    }

    @Test void theFolderIsAbsoluteForARelativeHome() {
        assertThat(DownloadsFolder.of("Mac OS X", Path.of("relative-home"), Map.of(), reader, NO_KNOWN_FOLDER)).isAbsolute()
            .isEqualTo(Path.of("relative-home").toAbsolutePath().resolve("Downloads"));
    }
}
