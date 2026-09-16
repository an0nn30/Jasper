package dev.jasper.app;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/** The bundled shell integration files and their extraction into the application directory. */
final class ShellIntegrationScripts {
    static final String RESOURCE_ROOT = "dev/jasper/app/shell-integration/";
    static final List<String> FILES = List.of("jasper.zsh", "jasper.bash", "jasper.fish",
        "zsh/.zshenv", "zsh/.zprofile", "zsh/.zshrc", "zsh/.zlogin", "bash/rc.bash", "fish/fish/vendor_conf.d/jasper.fish");

    private ShellIntegrationScripts() {}

    static byte[] bundled(String name) throws IOException {
        try (InputStream in = ShellIntegrationScripts.class.getClassLoader().getResourceAsStream(RESOURCE_ROOT + name)) {
            if (in == null) throw new IOException("Missing bundled shell integration file: " + name);
            return in.readAllBytes();
        }
    }

    /** Writes each bundled file whose on-disk content differs; unchanged files keep their timestamps. */
    static Path install(Path dir) throws IOException {
        Files.createDirectories(dir);
        restrict(dir, EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE,
            PosixFilePermission.OWNER_EXECUTE));
        for (String name : FILES) {
            byte[] content = bundled(name);
            Path target = dir.resolve(name);
            if (Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)
                && Arrays.equals(Files.readAllBytes(target), content)) continue;
            Files.createDirectories(target.getParent());
            // The user's login shell sources these, so replace rather than truncate: a crash mid-write
            // must never leave a half-written script, and a planted link must not redirect the write.
            Path staged = target.resolveSibling(target.getFileName() + ".jasper-new");
            try {
                Files.write(staged, content);
                restrict(staged, EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
                Files.move(staged, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } finally {
                Files.deleteIfExists(staged);
            }
        }
        return dir;
    }

    private static void restrict(Path path, Set<PosixFilePermission> permissions) {
        try {
            Files.setPosixFilePermissions(path, permissions);
        } catch (IOException | UnsupportedOperationException windowsOrOtherFilesystem) {
            // Windows has no POSIX modes; the application directory's own ACL already restricts it.
        }
    }
}
