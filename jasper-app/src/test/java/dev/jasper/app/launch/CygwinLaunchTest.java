package dev.jasper.app.launch;

import dev.jasper.app.cygwin.CygwinLayout;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

class CygwinLaunchTest {
    @TempDir Path temporary;
    @Test void usesRelayOwnedBashAndSeparateArgumentsWithoutChangingParentEnvironment() throws Exception {
        Path root = temporary.resolve("owned space \u00e9"); Files.createDirectories(root.resolve("root/bin"));
        Path bash = Files.writeString(root.resolve("root/bin/bash.exe"), "bash");
        Path scripts = temporary.resolve("scripts ; space"); Files.createDirectories(scripts.resolve("bash"));
        Files.writeString(scripts.resolve("bash/cygwin.bash"), "wrapper");
        Path front = Files.writeString(temporary.resolve("front.exe"), "front"), back = Files.writeString(temporary.resolve("back.exe"), "back");
        var environment = new HashMap<>(Map.of("Path", "C:/Windows", "HOME", "C:/wrong-home", "JASPER_CYGWIN_MODE", "auto"));
        var before = Map.copyOf(environment);
        var profile = new ShellProfile("cygwin", "Cygwin Bash", ShellProfile.Kind.LOCAL_SHELL, Optional.empty(), List.of(bash.toString()));
        var settings = new LaunchSettings(profile.command(), environment, 80, 24, 1000, profile);
        Path requested = Files.createDirectory(temporary.resolve("cwd ; \u00e9"));
        var prepared = CygwinLaunch.prepare(settings, Optional.of(requested), scripts, CygwinLayout.at(root), front, back,
            path -> "/converted/" + path.getFileName());
        assertThat(prepared.settings().command()).containsExactly(front.toString(), back.toString(), bash.toString(),
            "--noprofile", "--rcfile", "/converted/cygwin.bash", "-i");
        assertThat(prepared.settings().environment()).containsEntry("JASPER_CYGWIN_CWD", "/converted/cwd ; \u00e9")
            .containsEntry("JASPER_SHELL_INTEGRATION", "/converted/scripts ; space").doesNotContainKey("HOME").doesNotContainKey("Path");
        assertThat(prepared.settings().environment().get("PATH")).startsWith(root.resolve("root/bin").toString() + ";");
        assertThat(environment).isEqualTo(before);
        assertThat(prepared.directory()).isEqualTo(requested);
    }
    @Test void convertsExplicitWindowsHomeAndDropsInheritedStartingDirectory() throws Exception {
        Path root = temporary.resolve("owned"); Files.createDirectories(root.resolve("root/bin"));
        Path bash = Files.writeString(root.resolve("root/bin/bash.exe"), "bash");
        Path scripts = temporary.resolve("scripts"); Files.createDirectories(scripts.resolve("bash"));
        Files.writeString(scripts.resolve("bash/cygwin.bash"), "wrapper");
        Path front = Files.writeString(temporary.resolve("front.exe"), "front"), back = Files.writeString(temporary.resolve("back.exe"), "back");
        var profile = new ShellProfile("cygwin", "Cygwin Bash", ShellProfile.Kind.LOCAL_SHELL, Optional.empty(), List.of(bash.toString()));
        var settings = new LaunchSettings(profile.command(), Map.of("Home", "C:/chosen home", "JASPER_CYGWIN_HOME_OVERRIDE", "1",
            "JASPER_CYGWIN_CWD", "/inherited", "JASPER_CYGWIN_MODE", "off"), 80, 24, 1000, profile);
        var prepared = CygwinLaunch.prepare(settings, Optional.empty(), scripts, CygwinLayout.at(root), front, back,
            path -> "/converted/" + path.getFileName());
        assertThat(prepared.settings().environment()).containsEntry("HOME", "/converted/chosen home")
            .doesNotContainKeys("Home", "JASPER_CYGWIN_CWD", "JASPER_SHELL_INTEGRATION");
        assertThat(prepared.directory()).isEqualTo(root.resolve("root"));
    }
    @Test void refusesForeignUncBeforeCallingPathConverter() throws Exception {
        var profile = new ShellProfile("cygwin", "Cygwin Bash", ShellProfile.Kind.LOCAL_SHELL, Optional.empty(), List.of("bash"));
        var settings = new LaunchSettings(profile.command(), Map.of(), 80, 24, 1000, profile);
        assertThatThrownBy(() -> CygwinLaunch.prepare(settings, Optional.of(Path.of("\\\\foreign\\share")), temporary,
            CygwinLayout.at(temporary), temporary, temporary, path -> { throw new AssertionError("UNC conversion attempted"); }))
            .isInstanceOf(java.io.IOException.class);
    }
}
