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

/** Runs in a fresh JVM, matching the production provisioning-helper/app boundary. */
public final class ConPtySessionProbe {
    private static final Duration EVENT_TIMEOUT = Duration.ofSeconds(15);
    public static void main(String[] args) throws Exception {
        ProbeSupport.windows("terminal session");
        if (args.length != 1) throw new IllegalArgumentException("ConPtySessionProbe <owned scratch root>");
        Path scratch = Path.of(args[0]);
        ProbeAssertions.require(Files.isRegularFile(scratch.resolve(".jasper-cygwin-probe")), "Missing probe ownership marker");
        Path bin = scratch.resolve("cygwin space \u00e9/bin");
        var result = ProbeSupport.environment();
        result.put("status", "failed");
        var events = new CopyOnWriteArrayList<String>();
        var commandStatus = new java.util.concurrent.atomic.AtomicInteger(-1);
        try {
            Path home = Files.createDirectory(scratch.resolve("home space \u00e9"));
            Path cwd = Files.createDirectory(scratch.resolve("work space \u00e9"));
            Path rc = scratch.resolve("rc space \u00e9.bash");
            Files.writeString(home.resolve(".bash_profile"), "export JASPER_PROFILE_PROBE=loaded\n");
            Files.writeString(rc, """
                exec 3>&2 2> "$JASPER_PROBE_STDERR"
                source /etc/profile
                source "$HOME/.bash_profile"
                cd -- "$JASPER_PROBE_CWD" || exit 31
                exec 2>&3 3>&-
                probe_title() { printf '\\033]2;%s\\007' "$1"; }
                PS1='\\[\\e]2;ready:$JASPER_PROFILE_PROBE\\a\\]probe> '
                printf '\\033]133;A\\007'
                printf '\\033]7;%s\\007' "$JASPER_PROBE_URI"
                """);
            var environment = new HashMap<>(System.getenv());
            environment.keySet().removeIf(key -> key.equalsIgnoreCase("PATH") || key.equalsIgnoreCase("HOME"));
            environment.put("PATH", bin + ";" + System.getenv().entrySet().stream().filter(e -> e.getKey().equalsIgnoreCase("PATH"))
                .map(Map.Entry::getValue).findFirst().orElse(""));
            environment.put("HOME", cygpath(bin, home, scratch.resolve("home-path.log")));
            environment.put("JASPER_PROBE_CWD", cygpath(bin, cwd, scratch.resolve("cwd-path.log")));
            environment.put("JASPER_PROBE_STDERR", cygpath(bin, scratch.resolve("bash-stderr.log"), scratch.resolve("stderr-path.log")));
            environment.put("JASPER_PROBE_URI", cwd.toUri().toASCIIString());
            environment.put("TERM", "xterm-256color"); environment.put("COLORTERM", "truecolor");
            environment.put("LANG", "C.UTF-8");
            var command = List.of(bin.resolve("bash.exe").toString(), "--rcfile",
                cygpath(bin, rc, scratch.resolve("rc-path.log")), "-i");
            String relayPath = System.getProperty("jasper.cygwinProbeRelay");
            result.put("transport", relayPath == null ? "direct-conpty" : "experimental-posix-relay");
            if (relayPath != null) {
                Path relay = Path.of(relayPath);
                ProbeAssertions.require(relay.isAbsolute() && Files.isRegularFile(relay), "Invalid relay executable");
                String frontendPath = System.getProperty("jasper.cygwinProbeFrontend");
                ProbeAssertions.require(frontendPath != null && Path.of(frontendPath).isAbsolute()
                    && Files.isRegularFile(Path.of(frontendPath)), "Invalid native relay frontend");
                var wrapped = new java.util.ArrayList<>(List.of(frontendPath, relay.toString()));
                wrapped.addAll(command); command = wrapped;
            }
            result.put("bashCommand", command.toString());
            try (var session = TerminalSession.start(SessionLaunchOptions.builder().command(command)
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
