import dev.jasper.app.cygwin.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;

/** Opt-in Windows acceptance against the real seed, helper and production MSI template. Never opens Jasper. */
public final class InstallerAcceptanceTest {
    public static void main(String[] args) throws Exception {
        if (args.length != 4 || !CygwinLayout.windows()) throw new IllegalArgumentException("InstallerAcceptanceTest app-image fresh-scratch jbr-sdk extra-tool-cache");
        Path image = Path.of(args[0]), scratch = Path.of(args[1]), sdk = Path.of(args[2]);
        if (Files.exists(scratch)) throw new IllegalArgumentException("Acceptance scratch must be new");
        var layout = CygwinLayout.system();
        if (Files.exists(layout.base())) throw new IllegalArgumentException("Fresh MSI acceptance requires an absent Cygwin environment");
        Files.createDirectories(scratch);
        String name = "JasperSeedAcceptance-" + UUID.randomUUID().toString().substring(0, 8);
        String upgrade = UUID.randomUUID().toString();
        Path installed = Path.of(System.getenv("LOCALAPPDATA")).resolve(name), resources = scratch.resolve("resources");
        CygwinInstallerTemplate.main(new String[]{sdk.resolve("jmods/jdk.jpackage.jmod").toString(), resources.toString()});
        Path shortImage = Files.createTempDirectory("jpi");
        CygwinInstallerTemplate.stageImage(image, shortImage);
        Path msi = build(shortImage, scratch, sdk, resources, name, upgrade, "1.0.0");
        int checks = 0;
        try {
            msi(msi, installed, scratch, "install", "/i");
            require(CygwinState.inspect(layout).phase() == CygwinState.Phase.READY, "fresh MSI provisioning"); checks++;
            addTool(layout, image.resolve("app/cygwin-seed/cache"), Path.of(args[3]));
            require(Files.isRegularFile(layout.root().resolve("bin/hello.exe")), "additional package installed"); checks++;
            Path sentinel = layout.root().resolve("home/acceptance-user-data"); Files.createDirectories(sentinel.getParent());
            Files.writeString(sentinel, "preserve user data\n");
            Path database = layout.root().resolve("etc/setup/installed.db");
            String before = BundleManifest.digest(database);
            msi(msi, installed, scratch, "repair", "/fa");
            require(Files.readString(sentinel).equals("preserve user data\n") && BundleManifest.digest(database).equals(before), "repair preservation"); checks++;
            Path next = build(shortImage, scratch, sdk, resources, name, upgrade, "1.0.1");
            msi(next, installed, scratch, "upgrade", "/i"); msi = next;
            require(Files.readString(sentinel).equals("preserve user data\n") && BundleManifest.digest(database).equals(before), "upgrade preservation"); checks++;
            msi(msi, installed, scratch, "uninstall", "/x"); msi = null;
            require(Files.exists(sentinel) && BundleManifest.digest(database).equals(before), "uninstall preservation"); checks++;
            require(!Files.exists(installed.resolve("app/cygwin-tool.jar")), "MSI removed application"); checks++;
            // Reject an incomplete seed before Setup runs, preserving the ready environment.
            var bad = new CygwinProvisioner().ensure(layout, scratch.resolve("missing-seed"));
            require(!bad.success() && BundleManifest.digest(database).equals(before), "missing seed fails closed"); checks++;
            Files.writeString(scratch.resolve("acceptance.txt"), "PASS: " + checks + " checks; fresh MSI, repair, upgrade, uninstall, preservation, missing seed\n"
                + "seed=" + BundleManifest.digest(image.resolve("app/cygwin-seed/seed.lock.json")) + "\narch=" + System.getProperty("os.arch") + "\n");
            System.out.println(Files.readString(scratch.resolve("acceptance.txt")));
        } finally {
            try { if (msi != null) try { msi(msi, installed, scratch, "cleanup", "/x"); }
                catch (Exception | AssertionError cleanup) { System.err.println("Cleanup diagnostic: " + cleanup); } }
            finally { try (var paths = Files.walk(shortImage)) { for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) { Files.setAttribute(path, "dos:readonly", false); Files.delete(path); } } }
        }
    }
    static void addTool(CygwinLayout layout, Path seedCache, Path extraCache) throws Exception {
        Path cache = layout.base().resolve("cache/packages");
        for (Path source : List.of(seedCache, extraCache)) try (var paths = Files.walk(source)) {
            for (Path path : paths.toList()) {
                Path dest = cache.resolve(source.relativize(path));
                if (Files.isDirectory(path)) Files.createDirectories(dest);
                else Files.copy(path, dest, StandardCopyOption.REPLACE_EXISTING);
            }
        }
        Path operation = layout.base().resolve("operations/acceptance"); Files.createDirectories(operation);
        CygwinLayout.write(operation.resolve("request.json"), Map.of("root", layout.base().toString()));
        CygwinLayout.write(operation.resolve("launch-ready.json"), Map.of("ready", true));
        CygwinManager.supervise(layout, operation, (command, log) -> {
            var quiet = new ArrayList<>(command);
            quiet.addAll(List.of("--quiet-mode=hidden", "--local-install", "--no-version-check", "--packages", "hello"));
            return CygwinProcess.run(quiet, log);
        });
        var result = CygwinLayout.read(operation.resolve("result.json"));
        require(Boolean.FALSE.equals(result.get("failed")), "package-management supervisor: " + result);
    }
    private static Path build(Path image, Path root, Path sdk, Path resources, String name, String upgrade, String version) throws Exception {
        Path output = root.resolve("msi-" + version); Files.createDirectory(output);
        run(List.of(sdk.resolve("bin/jpackage.exe").toString(), "--type", "msi", "--app-image", image.toString(),
            "--name", name, "--app-version", version, "--vendor", "Jasper acceptance", "--win-per-user-install",
            "--win-upgrade-uuid", upgrade, "--install-dir", name, "--resource-dir", resources.toString(), "--dest", output.toString(), "--verbose"), root.resolve("build-" + version + ".log"));
        try (var entries = Files.list(output)) { return entries.filter(p -> p.toString().endsWith(".msi")).findFirst().orElseThrow(); }
    }
    private static void msi(Path msi, Path installed, Path root, String phase, String action) throws Exception {
        run(List.of("msiexec.exe", action, msi.toString(), "INSTALLDIR=" + installed, "/qn", "/norestart", "/l*v", root.resolve(phase + "-msi.log").toString()), root.resolve(phase + ".log"));
    }
    private static void run(List<String> command, Path log) throws Exception {
        var process = new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(log.toFile()).start();
        if (!process.waitFor(Duration.ofMinutes(20))) { process.descendants().forEach(ProcessHandle::destroyForcibly); process.destroyForcibly(); throw new AssertionError("Timed out: " + log); }
        if (process.exitValue() != 0) throw new AssertionError("Exit " + process.exitValue() + ": " + log);
    }
    private static void require(boolean ok, String what) { if (!ok) throw new AssertionError(what); }
}
