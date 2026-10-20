import dev.jasper.app.cygwin.*;
import dev.jasper.app.launch.*;
import dev.jasper.terminal.session.SessionLaunchOptions;
import dev.jasper.terminal.session.TerminalSession;
import dev.jasper.terminal.session.TerminalSessionListener;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

/** Native acceptance of the production launch preparation and bundled shell bootstrap. Uses an explicit scratch root. */
@SuppressWarnings("try")
public final class ProductionLaunchProbe {
    private static final Duration EVENT_TIMEOUT = Duration.ofSeconds(15);
    public static void main(String[] args) throws Exception {
        ProbeSupport.windows("terminal session");
        if (args.length != 2) throw new IllegalArgumentException("ProductionLaunchProbe <new scratch root> <existing probe Cygwin root>");
        Path scratch = Path.of(args[0]);
        ProbeAssertions.require(!Files.exists(scratch), "Production probe requires a new scratch directory");
        var layout = CygwinLayout.at(scratch); layout.initialize();
        int copied = ProbeSupport.run(List.of("robocopy.exe", args[1], layout.root().toString(), "/E", "/SL", "/R:0", "/W:0", "/NFL", "/NDL"),
            layout.logs().resolve("fixture-copy.log"), Duration.ofMinutes(3));
        ProbeAssertions.require(copied < 8, "Fixture runtime copy failed");
        fixtureManager(layout);
        Path bin = layout.root().resolve("bin");
        var result = ProbeSupport.environment();
        result.put("status", "failed");
        var events = new CopyOnWriteArrayList<String>();
        var commandStatus = new java.util.concurrent.atomic.AtomicInteger(-1);
        try {
            Path home = Files.createDirectory(scratch.resolve("home space \u00e9"));
            Path cwd = Files.createDirectory(scratch.resolve("work space \u00e9"));
            Files.writeString(home.resolve(".bash_profile"), """
                export JASPER_PROFILE_PROBE=loaded
                probe_title() { printf '\033]2;%s\007' "$1"; }
                PS1='\\[\\e]2;ready:$JASPER_PROFILE_PROBE\\a\\]probe> '
                """);
            var environment = new HashMap<>(System.getenv());
            environment.keySet().removeIf(key -> key.toUpperCase(java.util.Locale.ROOT).startsWith("JASPER_"));
            environment.put("HOME", home.toString()); environment.put("JASPER_CYGWIN_HOME_OVERRIDE", "1");
            environment.put("JASPER_CYGWIN_MODE", "auto"); environment.put("TERM_PROGRAM", "Jasper");
            Path scripts = ShellIntegrationScripts.install(scratch.resolve("integration space"));
            var profile = new ShellProfile("cygwin", "Cygwin Bash", ShellProfile.Kind.LOCAL_SHELL,
                java.util.Optional.empty(), List.of(bin.resolve("bash.exe").toString()));
            var prepared = CygwinLaunch.prepare(new LaunchSettings(profile.command(), environment, 80, 24, 1000, profile),
                java.util.Optional.of(cwd), scripts, layout);
            environment = new HashMap<>(prepared.settings().environment());
            environment.put("JASPER_PROBE_CWD", cygpath(bin, cwd, scratch.resolve("cwd-path.log")));
            var command = prepared.settings().command();
            result.put("transport", "production-posix-relay");
            result.put("bashCommand", command.toString());
            Path external = Files.copy(bin.resolve("sleep.exe"), scratch.resolve("renamed-external.exe"));
            var externalBuilder = new ProcessBuilder(external.toString(), "60");
            externalBuilder.environment().put("PATH", environment.get("PATH"));
            Process outside = externalBuilder.start();
            try {
                ProbeAssertions.await(() -> {
                    if (!outside.isAlive()) throw new AssertionError("External Cygwin process failed to start");
                    try { CygwinProcesses.assertIdle(layout); return false; }
                    catch (java.io.IOException busy) {
                        return busy.getMessage().contains("Close processes") && busy.getMessage().contains(Long.toString(outside.pid()));
                    }
                }, EVENT_TIMEOUT, "Renamed external process using the owned Cygwin DLL was not detected");
                result.put("externalModuleExclusion", "passed");
            } finally { outside.destroyForcibly(); outside.waitFor(5, TimeUnit.SECONDS); }

            try (var lease = CygwinOperations.registerLaunch(layout); var session = TerminalSession.start(SessionLaunchOptions.builder().command(command)
                    .environment(environment).workingDirectory(cwd).build())) {
                session.addListener(new TerminalSessionListener() {
                    @Override public void titleChanged(String title) { events.add(title); }
                    @Override public void commandExecuted(String command, java.util.OptionalInt status,
                            java.util.Optional<Path> directory, Duration duration) {
                        events.add("command:" + command + ";status:" + status);
                        if (!command.isBlank() && status.isPresent()) commandStatus.set(status.getAsInt());
                    }
                });
                title(session, events, "ready:loaded"); result.put("startupProfile", "passed");
                ProbeAssertions.await(session::shellIntegrationDetected, EVENT_TIMEOUT, "OSC 133 A not decoded");
                ProbeAssertions.await(() -> session.workingDirectory().map(p -> p.normalize().equals(cwd.normalize())).orElse(false),
                    EVENT_TIMEOUT, "OSC 7 directory not decoded");
                result.put("osc7And133", "passed");
                session.write("printf '\\033]133;A\\007\\033]133;B\\007'; probe_title markers\r");
                title(session, events, "markers");
                session.write("printf '\\033]133;C\\007'; false; status=$?; printf '\\033]133;D;%s\\007' \"$status\"; probe_title cycle\r");
                title(session, events, "cycle");
                ProbeAssertions.await(() -> commandStatus.get() == 1, EVENT_TIMEOUT, "OSC 133 command completion missing or wrong status");
                result.put("osc133CommandCycle", "passed");
                session.write("probe_title \"cwd:$(pwd)\"\r");
                title(session, events, "cwd:" + environment.get("JASPER_PROBE_CWD")); result.put("workingDirectory", "passed");
                session.write("probe_title '\u00e9\u6f22\ud83d\ude00'\r"); title(session, events, "\u00e9\u6f22\ud83d\ude00");
                result.put("unicodeRoundTrip", "passed");
                // Record the baseline; this query alone does not make later resize/input safe.
                session.write("probe_title \"initial-size:$(stty size)\"\r");
                title(session, events, "initial-size:" + session.rows() + " " + session.columns());
                result.put("initialSize", "passed");
                for (int iteration = 0; iteration < 20; iteration++) {
                    int columns = 81 + iteration, rows = 25 + iteration;
                    session.resize(columns, rows);
                    // Immediate input catches the dropped first byte seen during Cygwin resize handling.
                    session.write("probe_title \"size:" + iteration + ":$(stty size)\"\r");
                    title(session, events, "size:" + iteration + ":" + rows + " " + columns);
                    result.put("completedResizes", Integer.toString(iteration + 1));
                }
                result.put("resize", "passed");
                session.write("saved=$(stty -g); stty -echo -icanon; printf '\\033[2J\\033[H'; printf '%105s' x; printf '\\033[6n'; IFS= read -r -d R -t 5 position; stty \"$saved\"; position=${position#??}; probe_title \"wrap:${position/;/,}\"\r");
                title(session, events, "wrap:2,6"); result.put("outputWrap", "passed");
                session.write("cmd.exe /d /c exit 7; probe_title \"native:$?\"\r");
                title(session, events, "native:7"); result.put("windowsProgram", "passed");
                session.write("bash -c 'trap \"exit 130\" INT; printf \"\\033]2;sleeping\\007\"; read -r -t 60; exit 99'; probe_title \"interrupt:$?\"\r");
                title(session, events, "sleeping"); // Child is foreground and its INT trap is installed.
                session.write(new byte[]{3});
                title(session, events, "interrupt:130"); result.put("interrupt", "passed");
                session.write("exit\r");
                ProbeAssertions.require(session.exitFuture().get(15, TimeUnit.SECONDS) == 0, "Bash did not exit cleanly");
            }
            result.put("status", "passed");
        } catch (Exception failure) { result.put("error", failure.toString()); throw failure; }
        finally {
            Files.writeString(scratch.resolve("terminal-events.json"), ProbeSupport.json(Map.of("titles", events.toString())));
            var properties = new Properties(); properties.putAll(result);
            try (var out = Files.newOutputStream(scratch.resolve("terminal-session.properties"))) {
                properties.store(out, "Fresh-JVM Cygwin terminal evidence");
            }
        }
    }
    private static void fixtureManager(CygwinLayout layout) throws Exception {
        // Fixture manifest verifies the same manager/relay boundary as a real seed, without claiming a distribution seed.
        Path staged = Files.createDirectory(layout.manager().resolve("fixture"));
        var artifacts = new java.util.ArrayList<Map<String,Object>>();
        var relays = new HashMap<String,String>();
        for (String arch : List.of("aarch64", "amd64", "backend")) {
            String property = arch.equals("backend") ? "jasper.cygwinProbeRelay" : "jasper.cygwinProbeFrontend." + arch;
            Path source = Path.of(System.getProperty(property));
            String name = arch.equals("backend") ? "backend.exe" : "frontend-" + arch + ".exe";
            Path target = Files.copy(source, staged.resolve(name));
            artifacts.add(Map.of("path", name, "origin", "https://example.org/probe-fixture", "bytes", Files.size(target), "sha512", BundleManifest.digest(target)));
            relays.put(arch, name);
        }
        String reference = relays.get("backend");
        var manifest = Map.of("schema", 1, "architecture", "x86_64", "installedBytes", 1,
            "setup", reference, "metadata", reference, "signature", reference, "artifacts", artifacts, "relays", relays,
            "packages", List.of(Map.of("name", "bash", "version", "fixture", "binary", reference, "source", reference,
                "license", reference, "dependencies", List.of())));
        CygwinLayout.write(staged.resolve("seed.lock.json"), manifest);
        String identity = BundleManifest.digest(staged.resolve("seed.lock.json"));
        Files.move(staged, layout.manager().resolve(identity));
        CygwinLayout.write(layout.manager().resolve("current.json"), Map.of("generation", identity));
        CygwinLayout.write(layout.base().resolve("state.json"), Map.of("phase", "READY", "seed", identity));
    }
    private static void title(TerminalSession session, List<String> events, String expected) throws Exception {
        // A command's title precedes the next prompt. Wait for both before submitting more input.
        ProbeAssertions.await(() -> (expected.equals("sleeping") || expected.equals("ready:loaded")) ? session.title().equals(expected) :
                events.contains(expected) && session.title().equals("ready:loaded"), EVENT_TIMEOUT,
            "Expected title " + expected + "; terminal output/ConPTY probe");
    }
    private static String cygpath(Path bin, Path path, Path log) throws Exception {
        int exit = ProbeSupport.run(List.of(bin.resolve("cygpath.exe").toString(), "-u", path.toString()), log, Duration.ofSeconds(5));
        ProbeAssertions.require(exit == 0 && Files.size(log) < 65536, "cygpath failed: " + log);
        return ProbeSupport.text(log).strip();
    }
}
