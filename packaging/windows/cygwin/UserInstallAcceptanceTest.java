import dev.jasper.app.cygwin.*;
import java.nio.file.*;
import java.util.*;

/** Headless assertions around actual Gradle installWindowsUser/uninstallWindowsUser invocations. */
public final class UserInstallAcceptanceTest {
    public static void main(String[] args) throws Exception {
        if (args.length < 2 || !CygwinLayout.windows()) throw new IllegalArgumentException("prepare|seeded|record|verify scratch [image extra-cache]");
        String mode = args[0]; Path scratch = Path.of(args[1]); var layout = CygwinLayout.system();
        if (mode.equals("prepare")) {
            if (Files.exists(scratch)) throw new IllegalArgumentException("Fresh acceptance directory required");
            layout.requireOwned();
            if (CygwinState.inspect(layout).phase() != CygwinState.Phase.READY) throw new AssertionError("MSI seed not ready");
            Files.createDirectory(scratch);
            Files.move(layout.base(), scratch.resolve("preserved-msi-cygwin"));
            System.out.println("Preserved MSI environment; fresh user-install target ready"); return;
        }
        layout.requireOwned();
        if (CygwinState.inspect(layout).phase() != CygwinState.Phase.READY) throw new AssertionError("Cygwin not ready");
        if (mode.equals("seeded")) {
            if (args.length != 4 || Files.exists(layout.root().resolve("bin/hello.exe"))) throw new AssertionError("Expected a fresh starter installation");
            InstallerAcceptanceTest.addTool(layout, Path.of(args[2]).resolve("app/cygwin-seed/cache"), Path.of(args[3]));
            Path home = layout.root().resolve("home/acceptance"); Files.createDirectories(home);
            Files.writeString(home.resolve(".bashrc"), "# preserve user startup configuration\n");
            Files.writeString(layout.root().resolve("etc/acceptance.conf"), "user configuration\n");
            var command = List.of(layout.root().resolve("bin/bash.exe").toString(), "--noprofile", "--norc", "-c",
                "cd /home/acceptance && /bin/git init --quiet repository && /bin/ln -s .bashrc startup-link && /bin/hello");
            if (CygwinProcess.run(command, scratch.resolve("tools.log")) != 0) throw new AssertionError("Extra tool/repository/link checks failed");
            CygwinLayout.write(scratch.resolve("root-snapshot.json"), snapshot(layout.root()));
            System.out.println("Fresh user install and extra package verified; root snapshot recorded"); return;
        }
        if (mode.equals("record")) {
            if (!Files.isRegularFile(layout.root().resolve("bin/hello.exe")) || !Files.isRegularFile(scratch.resolve("tools.log")))
                throw new AssertionError("Missing seeded fixture");
            CygwinLayout.write(scratch.resolve("root-snapshot.json"), snapshot(layout.root()));
            System.out.println("Recorded seeded root snapshot"); return;
        }
        if (!mode.equals("verify")) throw new IllegalArgumentException(mode);
        var before = CygwinLayout.read(scratch.resolve("root-snapshot.json"));
        var after = snapshot(layout.root());
        if (!before.equals(after)) {
            var changed = new TreeSet<String>(); changed.addAll(before.keySet()); changed.addAll(after.keySet());
            changed.removeIf(key -> Objects.equals(before.get(key), after.get(key)));
            throw new AssertionError("Cygwin changed across app operation: " + changed);
        }
        System.out.println("PASS: exact root preservation (" + after.size() + " entries), including added package, config, link and repository");
    }
    private static Map<String, Object> snapshot(Path root) throws Exception {
        var result = new TreeMap<String, Object>();
        try (var paths = Files.walk(root)) {
            for (Path path : paths.toList()) {
                String name = root.relativize(path).toString().replace('\\', '/');
                result.put(name, Files.isSymbolicLink(path) ? "link:" + Files.readSymbolicLink(path)
                    : Files.isDirectory(path) ? "directory" : fileValue(root, path));
            }
        }
        return result;
    }
    private static String fileValue(Path root, Path path) throws Exception {
        try { return BundleManifest.digest(path); }
        catch (java.io.IOException unreadable) {
            // WSL reparse-point symlinks are understood by Cygwin but not by the JDK.
            Path output = Files.createTempFile("cygwin-link-", ".txt");
            try {
                var process = new ProcessBuilder(root.resolve("bin/readlink.exe").toString(), "--zero", "--", path.toString().replace('\\', '/'))
                    .redirectOutput(output.toFile()).redirectError(ProcessBuilder.Redirect.INHERIT).start();
                if (!process.waitFor(java.time.Duration.ofSeconds(10))) { process.destroyForcibly(); throw unreadable; }
                String target = Files.readString(output);
                if (process.exitValue() != 0 || !target.endsWith("\0")) throw unreadable;
                return "link:" + target.substring(0, target.length() - 1);
            } finally { Files.delete(output); }
        }
    }

}
