package dev.jasper.app.cygwin;

import java.io.IOException;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.function.BiFunction;

/** Independent package-manager supervisor. Runtime, helper and result outlive the app installation. */
public final class CygwinManager {
    private CygwinManager() { }
    public record Operation(Path directory, long pid, Instant started) { }
    public record Status(boolean active, boolean failed, String message) { }
    @SuppressWarnings("try")
    public static Operation start(CygwinLayout layout, Path helper, Path runtime) throws IOException {
        layout.requireOwned(); var state = CygwinState.inspect(layout);
        if (state.phase() != CygwinState.Phase.READY && state.phase() != CygwinState.Phase.DAMAGED)
            throw new IOException(state.diagnostic() + "; provision Cygwin through the Jasper installer first");
        Path operation = null; Process process = null;
        try {
            Operation result;
            try (var guard = CygwinOperations.acquireManagement(layout)) {
                cleanupInactive(layout);
                Path operations = layout.base().resolve("operations"); CygwinLayout.plainAncestry(operations); Files.createDirectories(operations);
                operation = Files.createDirectory(operations.resolve(UUID.randomUUID().toString()));
                Instant starterStart = ProcessHandle.current().info().startInstant().orElseThrow(() -> new IOException("Cannot identify manager starter"));
                var starter = Map.of("pid", ProcessHandle.current().pid(), "start", starterStart.toString());
                CygwinLayout.write(operation.resolve("starter.json"), starter);
                copyVerified(runtime, operation.resolve("runtime"));
                CygwinLayout.plainAncestry(helper);
                Path jar = operation.resolve("cygwin-tool.jar"); Files.copy(helper, jar);
                if (!BundleManifest.digest(helper).equals(BundleManifest.digest(jar))) throw new IOException("Supervisor helper copy failed verification");
                CygwinLayout.write(operation.resolve("request.json"), Map.of("root", layout.base().toString(), "created", Instant.now().toString(),
                    "starterPid", ProcessHandle.current().pid(), "starterStart", starterStart.toString()));
                process = new ProcessBuilder(operation.resolve("runtime/bin/java.exe").toString(), "--enable-native-access=ALL-UNNAMED", "-cp", jar.toString(),
                    "CygwinTool", "manage", "--operation", operation.toString(), "--test-root", layout.base().toString())
                    .redirectErrorStream(true).redirectOutput(operation.resolve("supervisor.log").toFile()).start();
                Instant started = process.info().startInstant().orElseThrow(() -> new IOException("Cannot identify package-manager supervisor"));
                CygwinLayout.write(operation.resolve("owner.json"), Map.of("pid", process.pid(), "start", started.toString()));
                result = new Operation(operation, process.pid(), started);
            }
            // The child waits for this after the starter releases the lock; it cannot race its parent's lock.
            CygwinLayout.write(operation.resolve("launch-ready.json"), Map.of("ready", true));
            return result;
        } catch (IOException | RuntimeException failure) {
            if (process != null && process.isAlive()) process.destroyForcibly();
            if (operation != null) try { CygwinLayout.write(operation.resolve("result.json"), Map.of("failed", true,
                "message", "Package-manager startup failed: " + failure.getMessage(), "finished", Instant.now().toString())); }
            catch (IOException recordFailure) { failure.addSuppressed(recordFailure); }
            throw failure;
        }
    }
    private static void cleanupInactive(CygwinLayout layout) throws IOException {
        Path operations = layout.base().resolve("operations"); CygwinLayout.plainAncestry(operations);
        if (!Files.isDirectory(operations)) return;
        try (var entries = Files.list(operations)) {
            for (Path operation : entries.toList()) {
                CygwinLayout.plainAncestry(operation);
                if (recordAlive(operation.resolve("owner.json"))) continue;
                boolean complete = Files.isRegularFile(operation.resolve("result.json"));
                if (!complete && (!Files.isRegularFile(operation.resolve("starter.json")) || recordAlive(operation.resolve("starter.json")))) continue;
                if (!complete && Files.exists(operation.resolve("request.json"))) continue;
                Path runtime = operation.resolve("runtime"); CygwinLayout.plainAncestry(runtime);
                if (Files.exists(runtime)) try (var paths = Files.walk(runtime)) {
                    for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) { CygwinLayout.plainAncestry(path); Files.delete(path); }
                }
                Path helper = operation.resolve("cygwin-tool.jar"); CygwinLayout.plainAncestry(helper); Files.deleteIfExists(helper);
            }
        }
    }
    private static boolean recordAlive(Path record) throws IOException {
        if (!Files.exists(record)) return false;
        var owner = CygwinLayout.read(record);
        return CygwinOperations.alive(CygwinJson.number(owner, "pid"), Instant.parse(CygwinJson.string(owner, "start")));
    }
    public static Status status(Operation operation) throws IOException {
        Path result = operation.directory().resolve("result.json");
        if (Files.exists(result)) {
            var data = CygwinLayout.read(result);
            return new Status(false, !Boolean.FALSE.equals(data.get("failed")), CygwinJson.string(data, "message"));
        }
        var process = ProcessHandle.of(operation.pid());
        boolean active = process.isPresent() && process.get().isAlive()
            && process.get().info().startInstant().map(operation.started()::equals).orElse(true);
        return new Status(active, !active, active ? "Cygwin Setup is running" : "Package-manager supervisor exited without a result; inspect " + operation.directory());
    }
    @SuppressWarnings("try")
    public static void supervise(CygwinLayout layout, Path operation, BiFunction<List<String>,Path,Integer> run) throws IOException {
        layout.requireOwned();
        if (!operation.toAbsolutePath().normalize().getParent().equals(layout.base().resolve("operations"))) throw new IOException("Foreign management operation");
        var request = CygwinLayout.read(operation.resolve("request.json"));
        if (!CygwinJson.string(request, "root").equals(layout.base().toString())) throw new IOException("Management root changed");
        if (Files.exists(operation.resolve("result.json"))) return; // A recovered/failed start cannot be resurrected by a late child.
        CygwinLayout.write(operation.resolve("owner.json"), Map.of("pid", ProcessHandle.current().pid(), "start",
            ProcessHandle.current().info().startInstant().orElseThrow(() -> new IOException("Cannot identify supervisor")).toString()));
        boolean failed = true; String message;
        try {
            long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(10);
            while (!Files.exists(operation.resolve("launch-ready.json"))) {
                if (Files.exists(operation.resolve("result.json")) || System.nanoTime() >= deadline) throw new IOException("Package-manager starter did not complete");
                Thread.sleep(25);
            }
            if (Files.exists(operation.resolve("result.json"))) return;
            try (var guard = CygwinOperations.acquireManagement(layout)) {
            Path generation = CygwinProvisioner.managerGeneration(layout);
            var manifest = BundleManifest.read(generation.resolve("seed.lock.json"));
            if (!generation.getFileName().toString().equals(BundleManifest.digest(generation.resolve("seed.lock.json")))
                    || !manifest.artifacts().get(manifest.setup()).sha512().equals(BundleManifest.digest(generation.resolve("setup.exe"))))
                throw new IOException("Cygwin Setup failed verification");
            Path cache = layout.base().resolve("cache/packages"); CygwinLayout.plainAncestry(cache); Files.createDirectories(cache);
            int exit = run.apply(List.of(generation.resolve("setup.exe").toString(), "--no-admin", "--no-shortcuts", "--no-replaceonreboot",
                "--no-write-registry", "--root", layout.root().toString(), "--local-package-dir", cache.toString()), operation.resolve("setup.log"));
            var state = CygwinState.inspect(layout);
            if (exit == 118) message = "Cygwin Setup requires a Windows restart. " + state.diagnostic();
            else if (exit != 0) message = "Cygwin Setup exited with " + exit + "; it may have failed or been cancelled. " + state.diagnostic();
            else { failed = state.phase() != CygwinState.Phase.READY; message = "Cygwin Setup finished. " + state.diagnostic(); }
            }
        } catch (Exception error) { if (error instanceof InterruptedException) Thread.currentThread().interrupt(); message = Objects.toString(error.getMessage(), error.toString()); }
        CygwinLayout.write(operation.resolve("result.json"), Map.of("failed", failed, "message", message, "finished", Instant.now().toString()));
    }
    private static void copyVerified(Path source, Path target) throws IOException {
        // Packaged Windows runtimes contain ordinary files, unlike Unix SDKs with symlinks.
        try (var paths = Files.walk(source)) {
            for (Path path : paths.toList()) {
                CygwinLayout.plainAncestry(path);
                Path destination = target.resolve(source.relativize(path));
                if (Files.isDirectory(path)) Files.createDirectories(destination);
                else {
                    Files.copy(path, destination);
                    if (!BundleManifest.digest(path).equals(BundleManifest.digest(destination))) throw new IOException("Supervisor runtime copy failed verification");
                }
            }
        }
    }
    public static String manageSystem() throws Exception {
        String app = System.getProperty("jpackage.app-path");
        String override = System.getProperty("jasper.cygwinTool");
        Path helper = override != null ? Path.of(override) : app == null ? null : Path.of(app).getParent().resolve("app/cygwin-tool.jar");
        if (helper == null || !Files.isRegularFile(helper)) throw new IOException("Cygwin management helper is unavailable; use a packaged Jasper installation");
        var operation = start(CygwinLayout.system(), helper, Path.of(System.getProperty("java.home")));
        for (;;) {
            Status status = status(operation);
            if (!status.active()) { if (status.failed()) throw new IOException(status.message()); return status.message(); }
            Thread.sleep(500);
        }
    }
}
