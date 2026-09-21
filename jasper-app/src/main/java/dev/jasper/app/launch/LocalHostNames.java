package dev.jasper.app.launch;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import javax.swing.SwingUtilities;

/**
 * The names this machine is known by, for deciding whether a working directory a shell reports is local.
 * They are what Jasper's own shell integration reports: the output of {@code hostname} (zsh's {@code $HOST},
 * fish's {@code $hostname}) and the {@code HOSTNAME} variable bash uses. A name lookup through the resolver is
 * deliberately not used: it can block on DNS for seconds and may answer with a different name. Resolved once
 * per process, never on the EDT.
 */
public final class LocalHostNames {
    private static final System.Logger LOG = System.getLogger(LocalHostNames.class.getName());
    private static volatile Set<String> cached;

    private LocalHostNames() { }

    /** The names, resolved on the first call off the EDT. On the EDT before that: none, which classifies hosted reports as remote. */
    public static Set<String> cached() {
        Set<String> names = cached;
        if (names != null) return names;
        if (SwingUtilities.isEventDispatchThread()) {
            LOG.log(System.Logger.Level.WARNING, "Local host names were asked for on the EDT before they were resolved; treating them as unknown");
            return Set.of();
        }
        synchronized (LocalHostNames.class) {
            if (cached == null) cached = resolve(LocalHostNames::runHostname, System.getenv());
            return cached;
        }
    }

    static Set<String> resolve(Supplier<Optional<String>> hostnameCommand, Map<String, String> environment) {
        Set<String> names = new LinkedHashSet<>();
        try { hostnameCommand.get().map(String::strip).filter(name -> !name.isEmpty()).ifPresent(names::add); }
        catch (RuntimeException failure) { LOG.log(System.Logger.Level.DEBUG, "The hostname program is unavailable", failure); }
        for (String variable : new String[]{"HOSTNAME", "COMPUTERNAME"}) {
            String value = environment.get(variable);
            if (value != null && !value.isBlank()) names.add(value.strip());
        }
        return Set.copyOf(names);
    }

    static Optional<String> runHostname() {
        try {
            Process process = new ProcessBuilder("hostname").redirectErrorStream(true).start();
            process.getOutputStream().close();
            if (!process.waitFor(2, TimeUnit.SECONDS)) { process.destroyForcibly(); return Optional.empty(); }
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).strip();
            return process.exitValue() == 0 && !output.isEmpty() && !output.contains("\n") ? Optional.of(output) : Optional.empty();
        } catch (IOException unavailable) {
            return Optional.empty();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        }
    }
}
