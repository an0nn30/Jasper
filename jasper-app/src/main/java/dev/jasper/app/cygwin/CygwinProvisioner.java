package dev.jasper.app.cygwin;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.function.BiFunction;
import java.util.function.ToLongFunction;

/** Initializes only new owned roots; app upgrades never reconcile a ready package database. */
public final class CygwinProvisioner {
    public enum Outcome { CREATED, REUSED, FAILED, RESTART_REQUIRED }
    public record Result(Outcome outcome, String diagnostic) {
        public boolean success() { return outcome == Outcome.CREATED || outcome == Outcome.REUSED; }
    }
    private final BiFunction<List<String>, Path, Integer> runner;
    private final ToLongFunction<Path> space;
    public CygwinProvisioner() { this(CygwinProcess::run, path -> {
        try { return Files.getFileStore(path).getUsableSpace(); }
        catch (IOException failure) { throw new java.io.UncheckedIOException(failure); }
    }); }
    public CygwinProvisioner(BiFunction<List<String>, Path, Integer> runner, ToLongFunction<Path> space) {
        this.runner = Objects.requireNonNull(runner); this.space = Objects.requireNonNull(space);
    }
    @SuppressWarnings("try")
    public Result ensure(CygwinLayout layout, Path suppliedSeed) {
        try {
            var state = CygwinState.inspect(layout);
            if (state.phase() == CygwinState.Phase.FOREIGN || state.phase() == CygwinState.Phase.DAMAGED)
                throw new IOException(state.diagnostic());
            Path seed = suppliedSeed.toAbsolutePath();
            var manifest = BundleManifest.read(seed.resolve("seed.lock.json")); manifest.verify(seed);
            String identity = BundleManifest.digest(seed.resolve("seed.lock.json"));
            if (state.phase() == CygwinState.Phase.ABSENT || state.phase() == CygwinState.Phase.INITIALIZING && state.seed().equals("unselected")) layout.initialize();
            // Installation may replace app-owned managers while shells run, but cannot overlap Setup.
            try (var operation = CygwinOperations.acquireInstallation(layout)) {
                state = CygwinState.inspect(layout);
                if (state.phase() == CygwinState.Phase.READY) {
                    installManager(layout, seed, manifest, identity);
                    return new Result(Outcome.REUSED, "Reused existing Cygwin environment at " + layout.root());
                }
                if (state.phase() != CygwinState.Phase.INITIALIZING) throw new IOException(state.diagnostic());
                if (!state.seed().equals("unselected")) {
                    if (!state.seed().matches("[0-9a-f]{128}")) throw new IOException("Invalid retained seed identity");
                    identity = state.seed(); seed = layout.base().resolve("cache/seed-" + identity);
                    manifest = BundleManifest.read(seed.resolve("seed.lock.json")); manifest.verify(seed);
                    if (!identity.equals(BundleManifest.digest(seed.resolve("seed.lock.json")))) throw new IOException("Retained seed changed");
                } else {
                    long archives = 0;
                    for (var artifact : manifest.artifacts().values()) archives = Math.addExact(archives, artifact.bytes());
                    long required = Math.addExact(Math.multiplyExact(archives, 2), Math.multiplyExact(manifest.installedBytes(), 2));
                    if (space.applyAsLong(layout.base()) < required) throw new IOException("Insufficient space for Cygwin: requires " + required + " bytes");
                    Path retained = layout.base().resolve("cache/seed-" + identity);
                    retain(seed, retained, manifest);
                    CygwinLayout.write(layout.base().resolve("state.json"), Map.of("phase", "INITIALIZING", "seed", identity));
                    seed = retained;
                }
                // An empty root cannot have loaded executables. MSI's protected processes
                // cannot be inspected from its impersonated custom action; partial retries
                // still need the full check before Setup changes any existing files.
                try (var contents = Files.list(layout.root())) {
                    if (contents.findAny().isPresent()) CygwinProcesses.assertIdle(layout);
                }
                String selection = String.join(",", manifest.packages().stream().map(p -> p.name() + "=" + p.version()).toList());
                var command = List.of(seed.resolve(manifest.setup()).toString(), "--quiet-mode=hidden", "--no-admin", "--local-install",
                    "--no-version-check", "--no-shortcuts", "--no-replaceonreboot", "--no-write-registry", "--local-package-dir",
                    seed.resolve("cache").toString(), "--root", layout.root().toString(), "--packages", selection);
                int exit = runner.apply(command, layout.logs().resolve("setup-" + System.currentTimeMillis() + ".log"));
                if (exit == 118) return new Result(Outcome.RESTART_REQUIRED, "Cygwin Setup requires a Windows restart; initialization remains incomplete");
                if (exit != 0) throw new IOException("Cygwin Setup failed with exit " + exit + "; see " + layout.logs());
                verifyInventory(layout, manifest);
                for (var probe : List.of(List.of("bash.exe", "--noprofile", "--norc", "-c", "exit 0"),
                        List.of("git.exe", "--version"), List.of("ssh.exe", "-V"), List.of("curl.exe", "--version"))) {
                    var args = new ArrayList<>(probe); args.set(0, layout.root().resolve("bin/" + probe.getFirst()).toString());
                    if (runner.apply(args, layout.logs().resolve(probe.getFirst() + ".log")) != 0) throw new IOException("Cygwin tool check failed: " + probe.getFirst());
                }
                installManager(layout, seed, manifest, identity);
                CygwinLayout.write(layout.base().resolve("state.json"), Map.of("phase", "READY", "seed", identity));
                return new Result(Outcome.CREATED, "Installed Cygwin at " + layout.root());
            }
        } catch (Exception failure) { return new Result(Outcome.FAILED, Objects.toString(failure.getMessage(), failure.toString())); }
    }
    private static void retain(Path seed, Path destination, BundleManifest manifest) throws IOException {
        CygwinLayout.plainAncestry(destination);
        if (Files.exists(destination)) { manifest.verify(destination); return; }
        Path temporary = Files.createTempDirectory(destination.getParent(), ".seed-");
        // A failed copy is retained for diagnosis; it is never mistaken for a complete seed.
        for (var artifact : manifest.artifacts().values()) {
            Path target = temporary.resolve(artifact.path()); Files.createDirectories(target.getParent()); Files.copy(seed.resolve(artifact.path()), target);
        }
        Files.copy(seed.resolve("seed.lock.json"), temporary.resolve("seed.lock.json")); manifest.verify(temporary);
        Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE);
    }
    private static void verifyInventory(CygwinLayout layout, BundleManifest manifest) throws IOException {
        Path database = layout.root().resolve("etc/setup/installed.db");
        if (Files.size(database) > 8 * 1024 * 1024) throw new IOException("Oversized Cygwin package database");
        var installed = new HashMap<String,String>();
        for (String line : Files.readAllLines(database)) {
            String[] parts = line.split(" ");
            if (parts.length >= 2 && !parts[0].equals("INSTALLED.DB")) installed.put(parts[0], parts[1]);
        }
        for (var pkg : manifest.packages()) {
            // Setup 2.937 encodes Canonical_version in a notional .tar.bz2 name, independent of archive compression.
            String binary = pkg.name() + "-" + pkg.version() + ".tar.bz2";
            if (!binary.equals(installed.remove(pkg.name()))) throw new IOException("Installed package differs from seed: " + pkg.name());
        }
        if (!installed.isEmpty()) throw new IOException("Unlisted packages installed: " + installed.keySet());
    }
    private static void installManager(CygwinLayout layout, Path seed, BundleManifest manifest, String identity) throws IOException {
        Path generation = layout.manager().resolve(identity); CygwinLayout.plainAncestry(generation);
        var names = new LinkedHashMap<String,String>(); names.put("setup.exe", manifest.setup());
        names.put("frontend-aarch64.exe", manifest.relays().get("aarch64"));
        names.put("frontend-amd64.exe", manifest.relays().get("amd64")); names.put("backend.exe", manifest.relays().get("backend"));
        if (!Files.exists(generation)) {
            Path staging = Files.createTempDirectory(layout.manager(), ".generation-");
            for (var entry : names.entrySet()) Files.copy(seed.resolve(entry.getValue()), staging.resolve(entry.getKey()));
            Files.copy(seed.resolve("seed.lock.json"), staging.resolve("seed.lock.json"));
            Files.move(staging, generation, StandardCopyOption.ATOMIC_MOVE);
        }
        for (var entry : names.entrySet()) {
            Path file = generation.resolve(entry.getKey()); CygwinLayout.plainAncestry(file);
            if (!BundleManifest.digest(file).equals(manifest.artifacts().get(entry.getValue()).sha512())) throw new IOException("Manager generation verification failed");
        }
        CygwinLayout.write(layout.manager().resolve("current.json"), Map.of("generation", identity));
    }
    public static Path managerGeneration(CygwinLayout layout) throws IOException {
        layout.requireOwned(); String generation = CygwinJson.string(CygwinLayout.read(layout.manager().resolve("current.json")), "generation");
        if (!generation.matches("[0-9a-f]{128}")) throw new IOException("Invalid manager identity");
        Path path = layout.manager().resolve(generation); CygwinLayout.plainAncestry(path); return path;
    }
}
