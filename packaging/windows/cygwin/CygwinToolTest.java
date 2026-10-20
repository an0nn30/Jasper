import dev.jasper.app.cygwin.*;
import java.nio.file.*;
import java.util.*;

@SuppressWarnings("try")
public final class CygwinToolTest {
    static int checks;
    public static void main(String[] args) throws Exception {
        if (args.length == 2) {
            var childLayout = CygwinLayout.at(Path.of(args[1]));
            try (var operation = args[0].equals("manager") ? CygwinOperations.acquireManagement(childLayout) : CygwinOperations.registerLaunch(childLayout)) {
                System.out.println("locked"); System.out.flush(); Thread.sleep(60000);
            }
            return;
        }
        Path scratch = Files.createTempDirectory("jasper-cygwin-lifecycle-").toRealPath();
        try {
            var layout = CygwinLayout.at(scratch.resolve("owned space \u00e9"));
            require(CygwinState.inspect(layout).phase() == CygwinState.Phase.ABSENT, "absent");
            Files.createDirectory(layout.base());
            require(CygwinState.inspect(layout).phase() == CygwinState.Phase.FOREIGN, "foreign empty directory");
            require(!Files.exists(layout.base().resolve("owner.json")), "inspection must not adopt foreign root");
            Files.delete(layout.base()); layout.initialize();
            require(CygwinState.inspect(layout).phase() == CygwinState.Phase.INITIALIZING, "owned interrupted initialization");
            try (var launch = CygwinOperations.registerLaunch(layout)) {
                denied(() -> CygwinOperations.acquireManagement(layout), "management while launch reserved");
            }
            try (var management = CygwinOperations.acquireManagement(layout)) {
                denied(() -> CygwinOperations.registerLaunch(layout), "launch during management");
                denied(() -> CygwinOperations.acquireManagement(layout), "duplicate management");
            }
            try (var launch = CygwinOperations.registerLaunch(layout)) { require(launch != null, "recovered operation"); }
            for (String mode : List.of("manager", "launch")) {
                var child = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", CygwinLayout.windows() ? "java.exe" : "java").toString(),
                    "--enable-native-access=ALL-UNNAMED", "-cp", System.getProperty("java.class.path"), "CygwinToolTest", mode, layout.base().toString()).redirectErrorStream(true).start();
                try {
                    var ready = java.util.concurrent.CompletableFuture.supplyAsync(() -> {
                        try { return child.inputReader().readLine(); } catch (Exception error) { throw new RuntimeException(error); }
                    });
                    require("locked".equals(ready.get(10, java.util.concurrent.TimeUnit.SECONDS)), "child lock ready");
                    denied(() -> CygwinOperations.acquireManagement(layout), "child process excludes manager");
                } finally { child.destroyForcibly(); require(child.waitFor(5, java.util.concurrent.TimeUnit.SECONDS), "child terminated"); }
                try (var operation = CygwinOperations.acquireManagement(layout)) { require(operation != null, "dead owner reclaimed"); }
            }
            Path operation = Files.createDirectories(layout.base().resolve("operations/interrupted"));
            CygwinLayout.write(operation.resolve("request.json"), Map.of("root", layout.base().toString()));
            CygwinLayout.write(operation.resolve("owner.json"), Map.of("pid", ProcessHandle.current().pid(), "start", "2000-01-01T00:00:00Z"));
            denied(() -> CygwinOperations.registerLaunch(layout), "interrupted supervisor must be reconciled before launch");
            try (var guard = CygwinOperations.acquireManagement(layout)) { require(Files.isRegularFile(operation.resolve("result.json")), "stale supervisor reconciled after idle check"); }
            try (var launch = CygwinOperations.registerLaunch(layout)) { require(launch != null, "launch after manager recovery"); }
            Path abandoned = Files.createDirectories(layout.base().resolve("operations/abandoned-start"));
            CygwinLayout.write(abandoned.resolve("request.json"), Map.of("root", layout.base().toString()));
            try (var guard = CygwinOperations.acquireManagement(layout)) { require(Files.isRegularFile(abandoned.resolve("result.json")), "ownerless abandoned start is recoverable"); }
            Path starting = Files.createDirectories(layout.base().resolve("operations/live-starter"));
            CygwinLayout.write(starting.resolve("request.json"), Map.of("root", layout.base().toString(), "starterPid", ProcessHandle.current().pid(),
                "starterStart", ProcessHandle.current().info().startInstant().orElseThrow().toString()));
            denied(() -> CygwinOperations.acquireManagement(layout), "live starter excludes a second manager");
            CygwinLayout.write(starting.resolve("request.json"), Map.of("root", layout.base().toString(), "starterPid", ProcessHandle.current().pid(), "starterStart", "2000-01-01T00:00:00Z"));
            try (var guard = CygwinOperations.acquireManagement(layout)) { require(Files.exists(starting.resolve("result.json")), "dead starter is reconciled"); }
            var incomplete = CygwinLayout.at(scratch.resolve("incomplete"));
            Files.createDirectory(incomplete.base());
            CygwinLayout.write(incomplete.base().resolve("owner.json"), Map.of("layout", 1, "product", "Jasper Cygwin", "user", CygwinLayout.identity()));
            require(CygwinState.inspect(incomplete).phase() == CygwinState.Phase.INITIALIZING, "owned empty interrupted initialization is retryable");
            incomplete.initialize();
            require(Files.isDirectory(incomplete.base().resolve("leases")) && Files.isRegularFile(incomplete.base().resolve("state.json")), "retry completes control records");
            var managementLayout = CygwinLayout.at(scratch.resolve("manager-start")); managementLayout.initialize();
            Files.createDirectories(managementLayout.root().resolve("bin")); Files.createDirectories(managementLayout.root().resolve("etc/setup"));
            for (String file : List.of("bin/bash.exe", "bin/cygpath.exe", "bin/cygwin1.dll", "etc/setup/installed.db")) Files.writeString(managementLayout.root().resolve(file), "fixture");
            CygwinLayout.write(managementLayout.base().resolve("state.json"), Map.of("phase", "READY", "seed", "fixture"));
            Path runtime = Files.createDirectories(scratch.resolve("fake-runtime/bin")).getParent();
            Files.writeString(runtime.resolve("bin/java.exe"), "not an executable");
            Path helper = Files.writeString(scratch.resolve("fake-helper.jar"), "helper");
            try { CygwinManager.start(managementLayout, helper, runtime); throw new AssertionError("Invalid supervisor executable accepted"); }
            catch (java.io.IOException expected) { checks++; }
            Path failedStart;
            try (var starts = Files.list(managementLayout.base().resolve("operations"))) { failedStart = starts.findFirst().orElseThrow(); }
            require(Files.isRegularFile(failedStart.resolve("result.json")), "failed start publishes terminal result");
            try (var launch = CygwinOperations.registerLaunch(managementLayout)) { require(launch != null, "launch works after failed supervisor start"); }
            CygwinManager.supervise(managementLayout, failedStart, (command, log) -> { throw new AssertionError("Settled start resurrected"); });
            checks++;
            Path activeCopy = Files.createDirectories(managementLayout.base().resolve("operations/active-copy/runtime"));
            Files.writeString(activeCopy.resolve("keep"), "active supervisor");
            CygwinLayout.write(activeCopy.getParent().resolve("owner.json"), Map.of("pid", ProcessHandle.current().pid(), "start", ProcessHandle.current().info().startInstant().orElseThrow().toString()));
            CygwinLayout.write(activeCopy.getParent().resolve("result.json"), Map.of("failed", false, "message", "finishing"));
            Files.writeString(failedStart.resolve("setup.log"), "retained diagnostic");
            try { CygwinManager.start(managementLayout, helper, runtime); throw new AssertionError("Invalid supervisor executable accepted"); }
            catch (java.io.IOException expected) { checks++; }
            require(!Files.exists(failedStart.resolve("runtime")) && !Files.exists(failedStart.resolve("cygwin-tool.jar")), "later operation removes inactive runtime copies");
            require(Files.readString(failedStart.resolve("setup.log")).equals("retained diagnostic"), "cleanup preserves operation diagnostics");
            require(Files.exists(activeCopy.resolve("keep")), "cleanup preserves a still-live supervisor runtime");
            Files.writeString(layout.base().resolve("state.json"), "broken");
            require(CygwinState.inspect(layout).phase() == CygwinState.Phase.DAMAGED, "malformed state");
            Files.writeString(layout.base().resolve("owner.json"), "{}");
            require(CygwinState.inspect(layout).phase() == CygwinState.Phase.FOREIGN, "malformed ownership");
            if (!System.getProperty("os.name").startsWith("Windows")) {
                Path linked = scratch.resolve("linked"); Files.createSymbolicLink(linked, layout.base());
                require(CygwinState.inspect(CygwinLayout.at(linked)).phase() == CygwinState.Phase.FOREIGN, "linked control root");
            }
            System.out.println("Cygwin lifecycle checks passed: " + checks);
        } finally { try (var files = Files.walk(scratch)) { for (Path p : files.sorted(Comparator.reverseOrder()).toList()) Files.delete(p); } }
    }
    private static void require(boolean ok, String message) { if (!ok) throw new AssertionError(message); checks++; }
    private static void denied(java.util.concurrent.Callable<? extends AutoCloseable> action, String message) throws Exception {
        try (var ignored = action.call()) { throw new AssertionError(message); }
        catch (java.io.IOException expected) { checks++; }
    }
}
