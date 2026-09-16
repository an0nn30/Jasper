package dev.jasper.app;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.PosixFilePermission;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

class ShellIntegrationScriptsTest {
    @TempDir Path dir;

    @Test void installWritesEveryFileOnceAndRewritesOnlyChangedContent() throws Exception {
        Path target = dir.resolve("shell-integration");
        assertThat(ShellIntegrationScripts.install(target)).isEqualTo(target);
        assertThat(ShellIntegrationScripts.FILES).containsExactly("jasper.zsh", "jasper.bash", "jasper.fish",
            "zsh/.zshenv", "zsh/.zprofile", "zsh/.zshrc", "zsh/.zlogin", "bash/rc.bash", "fish/fish/vendor_conf.d/jasper.fish");
        for (String name : ShellIntegrationScripts.FILES) {
            assertThat(target.resolve(name)).isRegularFile();
            assertThat(Files.readAllBytes(target.resolve(name))).isEqualTo(ShellIntegrationScripts.bundled(name));
        }
        Path zsh = target.resolve("jasper.zsh");
        Files.setLastModifiedTime(zsh, FileTime.fromMillis(1_000_000_000_000L));
        ShellIntegrationScripts.install(target);
        assertThat(Files.getLastModifiedTime(zsh)).isEqualTo(FileTime.fromMillis(1_000_000_000_000L));
        Files.writeString(zsh, "# stale\n");
        ShellIntegrationScripts.install(target);
        assertThat(Files.readAllBytes(zsh)).isEqualTo(ShellIntegrationScripts.bundled("jasper.zsh"));
        assertThat(AppDirs.resolve("Mac OS X", Map.of(), Path.of("/Users/j")).shellIntegration())
            .isEqualTo(Path.of("/Users/j/.config/jasper/shell-integration"));
    }

    @Test void everyScriptIsGuardedAndSyntacticallyValidWhereShellsExist() throws Exception {
        Path target = ShellIntegrationScripts.install(dir.resolve("shell-integration"));
        // bash gets a larger budget than zsh and fish because only bash needs the DEBUG-trap
        // plumbing, both PROMPT_COMMAND forms, the readonly guards and the HISTCONTROL check.
        for (Map.Entry<String, Integer> budget : Map.of("jasper.zsh", 90, "jasper.fish", 90, "jasper.bash", 115).entrySet()) {
            String text = Files.readString(target.resolve(budget.getKey()), StandardCharsets.UTF_8);
            assertThat(text).contains("TERM_PROGRAM").contains("JASPER_INTEGRATION_LOADED").contains("133;C").contains("1341;jasper;cmd;");
            assertThat(text.lines().count()).as(budget.getKey()).isLessThanOrEqualTo(budget.getValue());
        }
        if (Files.isExecutable(Path.of("/bin/zsh")))
            for (String name : List.of("jasper.zsh", "zsh/.zshenv", "zsh/.zprofile", "zsh/.zshrc", "zsh/.zlogin"))
                assertThat(exit(List.of("/bin/zsh", "-n", target.resolve(name).toString()))).as(name).isZero();
        if (Files.isExecutable(Path.of("/bin/bash")))
            for (String name : List.of("jasper.bash", "bash/rc.bash"))
                assertThat(exit(List.of("/bin/bash", "-n", target.resolve(name).toString()))).as(name).isZero();
        Assumptions.assumeTrue(Files.isExecutable(Path.of("/bin/zsh")) || Files.isExecutable(Path.of("/bin/bash")), "no shell to syntax-check");
    }

    private static int exit(List<String> command) throws IOException, InterruptedException {
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        process.getInputStream().readAllBytes();
        return process.waitFor();
    }

    /** These are sourced by the user's login shell, so a planted link must not redirect the write. */
    @Test void installReplacesASymlinkRatherThanWritingThroughIt() throws Exception {
        Path target = dir.resolve("shell-integration");
        ShellIntegrationScripts.install(target);
        Path outside = dir.resolve("outside.txt");
        Files.writeString(outside, "untouched");
        Files.delete(target.resolve("jasper.zsh"));
        Files.createSymbolicLink(target.resolve("jasper.zsh"), outside);

        ShellIntegrationScripts.install(target);

        assertThat(Files.readString(outside)).isEqualTo("untouched");
        assertThat(Files.isSymbolicLink(target.resolve("jasper.zsh"))).isFalse();
        assertThat(Files.readAllBytes(target.resolve("jasper.zsh")))
            .isEqualTo(ShellIntegrationScripts.bundled("jasper.zsh"));
    }

    @Test void extractedFilesAreReadableOnlyByTheirOwner() throws Exception {
        Path target = dir.resolve("shell-integration");
        ShellIntegrationScripts.install(target);
        Assumptions.assumeTrue(target.getFileSystem().supportedFileAttributeViews().contains("posix"));
        assertThat(Files.getPosixFilePermissions(target.resolve("jasper.zsh")))
            .containsExactlyInAnyOrder(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE);
        assertThat(Files.getPosixFilePermissions(target)).containsExactlyInAnyOrder(
            PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_EXECUTE);
    }

    @Test void aFailedInstallReportsRatherThanLeavingAPartialTree() throws Exception {
        Path blocked = dir.resolve("blocked");
        Files.writeString(blocked, "not a directory");
        assertThatThrownBy(() -> ShellIntegrationScripts.install(blocked.resolve("shell-integration")))
            .isInstanceOf(IOException.class);
    }
}
