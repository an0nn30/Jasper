import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.zip.ZipFile;

/** A disposable MSI product exercises a candidate JBR template hook; never installs Jasper. */
public final class InstallerProbe {
    static String runtimeOptions() { return "--strip-debug --no-header-files --no-man-pages"; }
    static String withHook(String template, String arguments) {
        if (template.indexOf("</Product>") < 0 || template.indexOf("<InstallExecuteSequence>") < 0
                || template.indexOf("</Product>") != template.lastIndexOf("</Product>"))
            throw new IllegalArgumentException("Unsupported jpackage main.wxs; retain and review the selected JBR template");
        String command = "\"[INSTALLDIR]runtime\\bin\\java.exe\" -cp \"[INSTALLDIR]app\\probe.jar\" InstallerProbe --helper " + arguments;
        String action = "<CustomAction Id=\"JasperCygwinProbe\" Directory=\"INSTALLDIR\" ExeCommand=\""
            + xml(command) + "\" Execute=\"deferred\" Impersonate=\"yes\" Return=\"check\"/>\n";
        return template.replace("<InstallExecuteSequence>", action + "<InstallExecuteSequence>\n"
            + "<Custom Action=\"JasperCygwinProbe\" After=\"InstallFiles\">NOT REMOVE</Custom>");
    }
    private static String xml(String text) {
        return text.replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;").replace(">", "&gt;");
    }
    public static void main(String[] args) throws Exception {
        ProbeSupport.windows("installer");
        if (args.length > 0 && args[0].equals("--helper")) { helper(Path.of(args[1]), args[2]); return; }
        if (args.length != 3) throw new IllegalArgumentException("InstallerProbe <fresh scratch root> <probe.jar> <report directory>");
        Path root = ProbeAssertions.freshRoot(args[0]);
        Path reports = Path.of(args[2]); Files.createDirectories(reports);
        var result = ProbeSupport.environment();
        result.put("scratch", root.toString()); result.put("status", "failed");
        String name = "JasperCygwinProbe-" + UUID.randomUUID().toString().substring(0, 8);
        String upgrade = UUID.randomUUID().toString();
        result.put("productName", name); result.put("upgradeUuid", upgrade);
        Path installed = root.resolve("installed space \u00e9");
        Path msi = null;
        try {
            String user = identity(root.resolve("caller-identity.log"));
            result.put("installingUser", user);
            Path input = Files.createDirectory(root.resolve("input"));
            Files.copy(Path.of(args[1]), input.resolve("probe.jar"));
            Path sentinel = root.resolve("persistent-sentinel"); Files.writeString(sentinel, upgrade);
            msi = build(root, input, name, upgrade, "1.0.0");
            Files.writeString(root.resolve("fail-helper"), "fail this attempt");
            int failed = install(msi, installed, root, "failure", false);
            Path failedEvidence = root.resolve("helper-1.0.0.properties");
            ProbeAssertions.require(Files.isRegularFile(failedEvidence), "Failing helper never ran");
            ProbeAssertions.require(!Files.exists(installed.resolve("app/probe.jar")), "Failed install did not roll back app files");
            result.put("helperFailureFailedInstaller", Boolean.toString(failed == 1603
                && ProbeSupport.text(root.resolve("failure-msi.log")).contains("JasperCygwinProbe")));
            Files.delete(root.resolve("fail-helper")); Files.delete(failedEvidence);
            int installedExit = install(msi, installed, root, "install", false);
            long returned = System.currentTimeMillis();
            ProbeAssertions.require(installedExit == 0, "Install failed or requires reboot: " + installedExit);
            Properties evidence = read(failedEvidence);
            result.put("helperUser", evidence.getProperty("user"));
            result.put("helperFilesAvailable", evidence.getProperty("files"));
            result.put("helperFinishedBeforeInstallerSuccess", Boolean.toString(Long.parseLong(evidence.getProperty("finished")) <= returned));
            result.put("silentModeRanHelper", Boolean.toString(ProbeSupport.text(root.resolve("install-msi.log")).contains("JasperCygwinProbe")));
            Files.delete(failedEvidence);
            ProbeAssertions.require(install(msi, installed, root, "repair", true) == 0, "Repair failed");
            result.put("repairRanHelper", Boolean.toString(Files.isRegularFile(failedEvidence)));
            Path next = build(root, input, name, upgrade, "1.0.1");
            ProbeAssertions.require(install(next, installed, root, "upgrade", false) == 0, "Upgrade failed");
            msi = next;
            ProbeAssertions.require(Files.isRegularFile(root.resolve("helper-1.0.1.properties")), "Upgrade helper did not run");
            result.put("upgradePreservedSentinel", Boolean.toString(Files.readString(sentinel).equals(upgrade)));
            ProbeAssertions.require(uninstall(msi, root, "uninstall") == 0, "Uninstall failed");
            msi = null;
            result.put("uninstallPreservedSentinel", Boolean.toString(Files.readString(sentinel).equals(upgrade)
                && !Files.exists(installed.resolve("app/probe.jar"))));
            ProbeAssertions.installer(result);
            result.put("status", "passed");
        } catch (Exception failure) {
            result.put("error", failure.toString()); throw failure;
        } finally {
            if (msi != null) {
                try { result.put("cleanupExit", Integer.toString(uninstall(msi, root, "cleanup"))); }
                catch (Exception cleanup) { result.put("cleanupError", cleanup.toString()); }
            }
            Files.writeString(reports.resolve("installer.json"), ProbeSupport.json(result));
        }
        System.out.println("Installer probe passed; evidence: " + reports.resolve("installer.json"));
    }
    private static Path build(Path root, Path input, String name, String upgrade, String version) throws Exception {
        Path build = Files.createDirectory(root.resolve("build-" + version));
        Path resources = Files.createDirectory(build.resolve("resources"));
        Path sdk = Path.of(System.getProperty("java.home"));
        try (var archive = new ZipFile(sdk.resolve("jmods/jdk.jpackage.jmod").toFile())) {
            var entry = archive.getEntry("classes/jdk/jpackage/internal/resources/main.wxs");
            ProbeAssertions.require(entry != null, "Selected JBR has no main.wxs resource");
            String template;
            try (var in = archive.getInputStream(entry)) { template = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8); }
            Files.writeString(build.resolve("original-main.wxs"), template);
            // Reject MSI formatted-field syntax in scratch paths rather than changing the command's meaning.
            ProbeAssertions.require(!root.toString().matches(".*[\\[\\]\"].*"), "Scratch path contains MSI formatting characters");
            Files.writeString(resources.resolve("main.wxs"), withHook(template, "\"" + root + "\" " + version));
        }
        ProbeSupport.success(List.of(sdk.resolve("bin/jpackage.exe").toString(), "--type", "msi", "--name", name,
            "--app-version", version, "--vendor", "Jasper probe", "--input", input.toString(),
            "--main-jar", "probe.jar", "--main-class", "InstallerProbe", "--add-modules", "java.base",
            "--jlink-options", runtimeOptions(),
            "--win-per-user-install", "--win-upgrade-uuid", upgrade, "--install-dir", name,
            "--resource-dir", resources.toString(), "--dest", build.resolve("output").toString(),
            "--temp", build.resolve("jpackage").toString(), "--verbose"), build.resolve("jpackage.log"));
        try (var files = Files.list(build.resolve("output"))) {
            return files.filter(p -> p.toString().endsWith(".msi")).findFirst().orElseThrow();
        }
    }
    private static int install(Path msi, Path installed, Path root, String phase, boolean repair) throws Exception {
        // msiexec requires PROPERTY="value", whereas ProcessBuilder quotes the whole argument.
        // cmd /s /c preserves this syntax; reject expansion/metacharacters in probe-owned paths.
        for (Path path : List.of(msi, installed, root))
            ProbeAssertions.require(!path.toString().matches(".*[\"%!?&|<>^\\r\\n].*"), "Unsupported shell character in MSI probe path");
        String command = "msiexec.exe /i \"" + msi + "\" /qn /norestart /l*v \""
            + root.resolve(phase + "-msi.log") + "\" INSTALLDIR=\"" + installed + "\""
            + (repair ? " REINSTALL=ALL REINSTALLMODE=amus" : "");
        return ProbeSupport.run(List.of("cmd.exe", "/d", "/s", "/c", "\"" + command + "\""),
            root.resolve(phase + "-process.log"), Duration.ofMinutes(10));
    }

    private static int uninstall(Path msi, Path root, String phase) throws Exception {
        return ProbeSupport.run(List.of("msiexec.exe", "/x", msi.toString(), "/qn", "/norestart", "/l*v",
            root.resolve(phase + "-msi.log").toString()), root.resolve(phase + "-process.log"), Duration.ofMinutes(10));
    }
    private static String identity(Path log) throws Exception {
        ProbeSupport.success(List.of("whoami.exe", "/user", "/fo", "csv", "/nh"), log);
        return ProbeSupport.text(log).strip();
    }
    private static Properties read(Path file) throws Exception {
        var properties = new Properties();
        try (var in = Files.newInputStream(file)) { properties.load(in); }
        return properties;
    }
    private static void helper(Path root, String version) throws Exception {
        ProbeAssertions.require(Files.isRegularFile(root.resolve(".jasper-cygwin-probe")), "No scratch ownership marker");
        var properties = new Properties();
        properties.setProperty("user", identity(root.resolve("helper-identity-" + version + ".log")));
        Path jar = Path.of(InstallerProbe.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        properties.setProperty("files", Boolean.toString(Files.isRegularFile(jar)
            && Files.isRegularFile(Path.of(System.getProperty("java.home"), "bin/java.exe"))));
        properties.setProperty("finished", Long.toString(System.currentTimeMillis()));
        try (var out = Files.newOutputStream(root.resolve("helper-" + version + ".properties"),
                StandardOpenOption.CREATE_NEW)) { properties.store(out, "actual installed helper evidence"); }
        if (Files.exists(root.resolve("fail-helper"))) System.exit(23);
    }
}
