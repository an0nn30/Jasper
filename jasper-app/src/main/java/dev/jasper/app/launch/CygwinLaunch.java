package dev.jasper.app.launch;

import dev.jasper.app.cygwin.*;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/** Cygwin-specific preparation on the launch worker; conversions never run on the terminal reader or EDT. */
public final class CygwinLaunch {
    private CygwinLaunch() { }
    public record Prepared(LaunchSettings settings, Path directory) { }
    public static Prepared prepare(LaunchSettings settings, Optional<Path> requested, Path scripts, CygwinLayout layout) throws IOException {
        if (javax.swing.SwingUtilities.isEventDispatchThread()) throw new IllegalStateException("Cygwin preparation must run off the EDT");
        var state = CygwinState.inspect(layout);
        if (state.phase() != CygwinState.Phase.READY) throw new IOException(state.diagnostic());
        Path generation = CygwinProvisioner.managerGeneration(layout);
        String arch = System.getProperty("os.arch");
        if (arch.equals("x86_64")) arch = "amd64";
        if (!(arch.equals("aarch64") || arch.equals("amd64"))) throw new IOException("Unsupported relay architecture: " + arch);
        var manifest = BundleManifest.read(generation.resolve("seed.lock.json"));
        if (!BundleManifest.digest(generation.resolve("seed.lock.json")).equals(generation.getFileName().toString()))
            throw new IOException("Cygwin manager manifest changed");
        Path frontend = generation.resolve("frontend-" + arch + ".exe"), backend = generation.resolve("backend.exe");
        for (var entry : Map.of(frontend, arch, backend, "backend").entrySet()) {
            CygwinLayout.plainAncestry(entry.getKey());
            if (!BundleManifest.digest(entry.getKey()).equals(manifest.artifacts().get(manifest.relays().get(entry.getValue())).sha512()))
                throw new IOException("Cygwin relay verification failed");
        }
        try { return prepare(settings, requested, scripts, layout, frontend, backend, path -> {
            try { return cygpath(layout, path); } catch (IOException error) { throw new UncheckedIOException(error); }
        }); } catch (UncheckedIOException error) { throw error.getCause(); }
    }
    static Prepared prepare(LaunchSettings settings, Optional<Path> requested, Path scripts, CygwinLayout layout,
            Path frontend, Path backend, Function<Path,String> convert) throws IOException {
        if (requested.isPresent() && UncHosts.namesForeignHost(requested.get().toString(), false))
            throw new IOException("Cygwin starting directory must be local");
        if (scripts == null || !Files.isRegularFile(scripts.resolve("bash/cygwin.bash"))) throw new IOException("Cygwin bootstrap scripts are unavailable");
        Path bin = layout.root().resolve("bin"), bash = bin.resolve("bash.exe");
        if (!Files.isRegularFile(bash) || !Files.isRegularFile(frontend) || !Files.isRegularFile(backend)) throw new IOException("Cygwin executable is unavailable");
        var environment = new HashMap<>(settings.environment());
        String path = environment.entrySet().stream().filter(e -> e.getKey().equalsIgnoreCase("PATH")).map(Map.Entry::getValue).findFirst().orElse("");
        String home = environment.entrySet().stream().filter(e -> e.getKey().equalsIgnoreCase("HOME")).map(Map.Entry::getValue).findFirst().orElse(null);
        boolean explicitHome = "1".equals(environment.remove("JASPER_CYGWIN_HOME_OVERRIDE"));
        environment.keySet().removeIf(key -> key.equalsIgnoreCase("PATH") || key.equalsIgnoreCase("BASH_ENV") || key.equalsIgnoreCase("ENV")
            || key.equalsIgnoreCase("JASPER_CYGWIN_BOOTSTRAP") || key.equalsIgnoreCase("JASPER_CYGWIN_START_DIRECTORY")
            || key.equalsIgnoreCase("JASPER_CYGWIN_CWD") || key.equalsIgnoreCase("HOME"));
        if (explicitHome && home != null) {
            if (UncHosts.namesForeignHost(home, false)) throw new IOException("Cygwin home directory must be local");
            environment.put("HOME", home.matches("^[A-Za-z]:[\\\\/].*") ? convert.apply(Path.of(home)) : home);
        }
        environment.put("PATH", bin + ";" + path);
        environment.putIfAbsent("LANG", "C.UTF-8");
        environment.put("TERM", "xterm-256color"); environment.put("COLORTERM", "truecolor");
        environment.put("JASPER_CYGWIN", "1");
        environment.put("JASPER_CYGWIN_CYGPATH", convert.apply(bin.resolve("cygpath.exe")));
        String mode = environment.getOrDefault("JASPER_CYGWIN_MODE", "auto");
        if (!mode.equals("off")) environment.put("JASPER_SHELL_INTEGRATION", convert.apply(scripts));
        else environment.remove("JASPER_SHELL_INTEGRATION");
        requested.ifPresent(directory -> environment.put("JASPER_CYGWIN_CWD", convert.apply(directory)));
        var command = List.of(frontend.toString(), backend.toString(), bash.toString(), "--noprofile", "--rcfile",
            convert.apply(scripts.resolve("bash/cygwin.bash")), "-i");
        return new Prepared(new LaunchSettings(command, environment, settings.columns(), settings.lines(), settings.scrollback(), settings.profile()),
            requested.orElse(layout.root()));
    }
    private static String cygpath(CygwinLayout layout, Path path) throws IOException {
        Process process = new ProcessBuilder(layout.root().resolve("bin/cygpath.exe").toString(), "-u", path.toString())
            .redirectErrorStream(true).start();
        var output = java.util.concurrent.CompletableFuture.supplyAsync(() -> {
            try (var input = process.getInputStream()) { return input.readNBytes(65537); }
            catch (IOException error) { throw new UncheckedIOException(error); }
        });
        try {
            byte[] bytes = output.get(5, TimeUnit.SECONDS);
            if (bytes.length > 65536 || !process.waitFor(1, TimeUnit.SECONDS) || process.exitValue() != 0) throw new IOException("Cygwin path conversion failed");
            String result = new String(bytes, StandardCharsets.UTF_8).strip();
            if (!result.startsWith("/") || result.indexOf('\n') >= 0 || result.indexOf('\0') >= 0) throw new IOException("Invalid Cygwin path conversion");
            return result;
        } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new IOException("Interrupted path conversion", interrupted); }
        catch (java.util.concurrent.ExecutionException | java.util.concurrent.TimeoutException error) { throw new IOException("Cygwin path conversion failed or timed out", error); }
        finally { if (process.isAlive()) process.destroyForcibly(); }
    }
}
