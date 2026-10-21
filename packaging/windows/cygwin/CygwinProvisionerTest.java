import dev.jasper.app.cygwin.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public final class CygwinProvisionerTest {
    public static void main(String[] args) throws Exception {
        Path temporary = Files.createTempDirectory("jasper-cygwin-provision-").toRealPath();
        try {
            Path seed = Files.createDirectory(temporary.resolve("seed"));
            Files.writeString(seed.resolve("archive"), "verified bytes");
            String fixture = """
                {"schema":1,"architecture":"x86_64","installedBytes":100,"setup":"archive","metadata":"archive","signature":"archive",
                "artifacts":[{"path":"archive","origin":"https://example.org/archive","bytes":14,"sha512":"HASH"}],
                "packages":[{"name":"bash","version":"1-1","binary":"archive","source":"archive","license":"archive","dependencies":[]}],
                "relays":{"aarch64":"archive","amd64":"archive","backend":"archive"}}
                """.replace("HASH", BundleManifest.digest(seed.resolve("archive")));
            Files.writeString(seed.resolve("seed.lock.json"), fixture);
            var layout = CygwinLayout.at(temporary.resolve("environment"));
            var calls = new AtomicInteger();
            var provisioner = new CygwinProvisioner((command, log) -> {
                if (command.contains("--local-install")) {
                    calls.incrementAndGet();
                    try {
                        Files.createDirectories(layout.root().resolve("bin"));
                        Files.createDirectories(layout.root().resolve("etc/setup"));
                        Files.writeString(layout.root().resolve("etc/setup/installed.db"), "INSTALLED.DB 3\nbash bash-1-1.tar.bz2 0\n");
                        for (String name : List.of("bash.exe", "cygpath.exe", "cygwin1.dll", "git.exe", "ssh.exe", "curl.exe"))
                            Files.writeString(layout.root().resolve("bin/"+name), name);
                    } catch (Exception failure) { throw new RuntimeException(failure); }
                }
                return 0;
            }, path -> Long.MAX_VALUE);
            require(provisioner.ensure(layout, seed).outcome() == CygwinProvisioner.Outcome.CREATED, "fresh provision");
            Files.delete(layout.root().resolve("bin/git.exe"));
            Files.createDirectories(layout.root().resolve("home/me")); Files.writeString(layout.root().resolve("home/me/.bashrc"), "user customization");
            Map<String,String> before = snapshot(layout.root());
            require(provisioner.ensure(layout, seed).outcome() == CygwinProvisioner.Outcome.REUSED, "ready reuse");
            require(calls.get() == 1 && before.equals(snapshot(layout.root())), "reuse must not reinstall removed packages or touch root");
            var full = new CygwinProvisioner((command, log) -> { throw new AssertionError("Setup on full disk"); }, path -> 0L);
            require(full.ensure(CygwinLayout.at(temporary.resolve("diskfull")), seed).outcome() == CygwinProvisioner.Outcome.FAILED, "disk full");
            var interrupted = CygwinLayout.at(temporary.resolve("interrupted"));
            var fail = new CygwinProvisioner((command, log) -> 1, path -> Long.MAX_VALUE);
            require(fail.ensure(interrupted, seed).outcome() == CygwinProvisioner.Outcome.FAILED, "setup failure");
            require(CygwinState.inspect(interrupted).phase() == CygwinState.Phase.INITIALIZING, "retain partial initialization");
            var restart = new CygwinProvisioner((command, log) -> 118, path -> Long.MAX_VALUE);
            require(restart.ensure(CygwinLayout.at(temporary.resolve("restart")), seed).outcome() == CygwinProvisioner.Outcome.RESTART_REQUIRED, "Setup 2.937 restart code is IDS_REBOOT_REQUIRED (118)");
            Path foreign = Files.createDirectory(temporary.resolve("foreign"));
            require(provisioner.ensure(CygwinLayout.at(foreign), seed).outcome() == CygwinProvisioner.Outcome.FAILED, "foreign root refused");
            require(Files.list(foreign).findAny().isEmpty(), "foreign root untouched");
            System.out.println("Cygwin provisioning checks passed");
        } finally { try (var files = Files.walk(temporary)) { for (Path p : files.sorted(Comparator.reverseOrder()).toList()) Files.delete(p); } }
    }
    private static Map<String,String> snapshot(Path root) throws Exception {
        var result = new TreeMap<String,String>(); try (var files = Files.walk(root)) {
            for (Path file : files.filter(Files::isRegularFile).toList()) result.put(root.relativize(file).toString(), BundleManifest.digest(file));
        } return result;
    }
    private static void require(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
}
