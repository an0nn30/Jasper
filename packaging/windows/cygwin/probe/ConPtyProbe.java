import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

/** Uses only the supported terminal facade; all shells run in a newly owned scratch environment. */
public final class ConPtyProbe {
    static Duration sessionTimeout() { return Duration.ofMinutes(10); }
    static String quietMode() { return "hidden"; }
    public static void main(String[] args) throws Exception {
        ProbeSupport.windows("terminal");
        if (args.length != 3) throw new IllegalArgumentException("ConPtyProbe <fresh scratch root> <verified local payload> <report directory>");
        Path payload = Path.of(args[1]).toAbsolutePath();
        Path setup = payload.resolve("setup-x86_64.exe");
        ProbeAssertions.require(Files.isRegularFile(setup), "Payload must contain verified setup-x86_64.exe and cache/");
        ProbeAssertions.require(Files.isDirectory(payload.resolve("cache")), "Missing offline cache");
        Path scratch = ProbeAssertions.freshRoot(args[0]);
        Path reports = Path.of(args[2]); Files.createDirectories(reports);
        var result = ProbeSupport.environment();
        result.put("status", "failed"); result.put("scratch", scratch.toString());
        try {
            result.put("setupSha256", java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(setup))));
            ProbeSupport.success(List.of(setup.toString(), "--version"), scratch.resolve("setup-version.log"));
            ProbeSupport.success(List.of(setup.toString(), "--help"), scratch.resolve("setup-help.log"));
            Path root = scratch.resolve("cygwin space \u00e9");
            var setupArgs = List.of(setup.toString(), "--quiet-mode=" + quietMode(), "--no-admin", "--local-install",
                "--no-version-check", "--no-shortcuts", "--no-replaceonreboot", "--local-package-dir",
                payload.resolve("cache").toString(), "--root", root.toString(), "--categories", "Base", "--packages",
                "bash,coreutils,findutils,grep,sed,gawk,diffutils,less,tar,gzip,bzip2,xz,unzip,git,openssh,curl,ca-certificates");
            int setupExit = ProbeSupport.run(setupArgs, scratch.resolve("setup.log"), Duration.ofMinutes(10));
            result.put("setupExit", Integer.toString(setupExit));
            ProbeAssertions.require(setupExit == 0, "Setup incomplete or restart required: " + setupExit);
            Files.copy(root.resolve("etc/setup/installed.db"), scratch.resolve("installed.db"));
            Path bin = root.resolve("bin");
            for (String tool : List.of("bash", "git", "ssh", "curl")) {
                ProbeSupport.success(List.of(bin.resolve(tool + ".exe").toString(), tool.equals("ssh") ? "-V" : "--version"),
                    scratch.resolve(tool + "-version.log"));
            }
            long installedBytes = 0;
            try (var files = Files.walk(root)) {
                for (var file : files.filter(p -> Files.isRegularFile(p, java.nio.file.LinkOption.NOFOLLOW_LINKS)).toList())
                    installedBytes += Files.size(file);
            }
            result.put("installedBytes", Long.toString(installedBytes));
            // A direct bash/ssh preflight in this JVM changes later Cygwin console behavior.
            // Provisioning and the app are separate processes; exercise that same boundary here.
            int sessionExit = ProbeSupport.run(List.of(
                Path.of(System.getProperty("java.home"), "bin/java.exe").toString(),
                "--enable-native-access=ALL-UNNAMED", "-Djava.awt.headless=true", "-cp",
                System.getProperty("java.class.path"), "ConPtySessionProbe", scratch.toString()),
                scratch.resolve("terminal-session.log"), sessionTimeout());
            var sessionEvidence = new java.util.Properties();
            try (var in = Files.newInputStream(scratch.resolve("terminal-session.properties"))) { sessionEvidence.load(in); }
            for (String key : sessionEvidence.stringPropertyNames()) result.put(key, sessionEvidence.getProperty(key));
            ProbeAssertions.require(sessionExit == 0 && "passed".equals(result.get("status")),
                "Terminal child failed: " + result.getOrDefault("error", "inspect terminal-session.log"));
            result.put("status", "passed");
        } catch (Exception failure) { result.put("status", "failed"); result.put("error", failure.toString()); throw failure; }
        finally {
            Files.writeString(reports.resolve("terminal.json"), ProbeSupport.json(result));
        }
        System.out.println("Terminal probe passed; evidence: " + reports.resolve("terminal.json"));
    }
}
