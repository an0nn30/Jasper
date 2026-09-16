package dev.jasper.app;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

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
        for (String name : FILES) {
            byte[] content = bundled(name);
            Path target = dir.resolve(name);
            if (Files.isRegularFile(target) && Arrays.equals(Files.readAllBytes(target), content)) continue;
            Files.createDirectories(target.getParent());
            Files.write(target, content);
        }
        return dir;
    }
}
