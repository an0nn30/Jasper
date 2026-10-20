import dev.jasper.app.cygwin.BundleManifest;
import java.nio.file.*;

/** Build-time entry point; installer provisioning uses the same manifest implementation. */
public final class BundleTool {
    public static void main(String[] args) throws Exception {
        if (args.length < 3 || !java.util.Set.of("verify", "stage", "acquire", "distribution").contains(args[0]))
            throw new IllegalArgumentException("BundleTool verify|stage lock snapshot [destination] | acquire lock snapshot local-artifacts");
        Path lock = Path.of(args[1]), snapshot = Path.of(args[2]);
        var manifest = BundleManifest.read(lock);
        if (args[0].equals("acquire")) {
            if (args.length != 4) throw new IllegalArgumentException("Missing local artifact directory");
            acquire(manifest, Path.of(args[3]), snapshot, BundleTool::download);
        }
        manifest.verify(snapshot);
        if (args[0].equals("distribution")) {
            if (args.length != 6) throw new IllegalArgumentException("distribution lock seed helper evidence architecture");
            releaseEvidence(lock, Path.of(args[3]), Path.of(args[4]), args[5]);
        }
        if (args[0].equals("stage")) {
            if (args.length != 4) throw new IllegalArgumentException("Missing staging destination");
            Path target = Path.of(args[3]).toAbsolutePath().normalize();
            if (snapshot.toAbsolutePath().normalize().startsWith(target)) throw new java.io.IOException("Staging cannot replace the source snapshot");
            Files.createDirectories(target.getParent());
            Path staging = Files.createTempDirectory(target.getParent(), ".cygwin-seed-");
            Path previous = target.resolveSibling(".cygwin-previous-" + java.util.UUID.randomUUID());
            boolean moved = false;
            try {
                for (var artifact : manifest.artifacts().values()) {
                    Path destination = staging.resolve(artifact.path()); Files.createDirectories(destination.getParent());
                    Files.copy(snapshot.resolve(artifact.path()), destination);
                }
                Files.copy(lock, staging.resolve("seed.lock.json"));
                manifest.verify(staging);
                if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) { Files.move(target, previous, StandardCopyOption.ATOMIC_MOVE); moved = true; }
                try { Files.move(staging, target, StandardCopyOption.ATOMIC_MOVE); }
                catch (Exception failure) { if (moved) Files.move(previous, target, StandardCopyOption.ATOMIC_MOVE); throw failure; }
                if (moved) remove(previous);
            } finally { if (Files.exists(staging)) remove(staging); }

        }
        System.out.println("Verified " + manifest.packages().size() + " packages and " + manifest.artifacts().size() + " artifacts");
    }
    static void releaseEvidence(Path lock, Path helper, Path evidence, String architecture) throws Exception {
        try (var jar = new java.util.jar.JarFile(helper.toFile())) {
            if (jar.getJarEntry("CygwinTool.class") == null) throw new java.io.IOException("Missing provisioning helper");
        }
        var data = dev.jasper.app.cygwin.CygwinJson.object(dev.jasper.app.cygwin.CygwinJson.parse(Files.readString(evidence)));
        if (!BundleManifest.digest(lock).equals(data.get("seed")) || !Boolean.TRUE.equals(data.get(architecture))
                || !(data.get("interactiveApps") instanceof java.util.Map<?, ?> interactive) || !Boolean.TRUE.equals(interactive.get(architecture)))
            throw new java.io.IOException("Native Windows " + architecture + " native or interactive validation is outstanding for this seed; release blocked");
    }
    @FunctionalInterface interface Download { void copy(java.net.URI origin, Path destination) throws Exception; }
    static void acquire(BundleManifest manifest, Path local, Path cache, Download download) throws Exception {
        Files.createDirectories(cache);
        for (var artifact : manifest.artifacts().values()) {
            Path target = cache.resolve(artifact.path());
            for (Path path = target; path != null && path.startsWith(cache); path = path.getParent())
                if (Files.exists(path, LinkOption.NOFOLLOW_LINKS) && (Files.isSymbolicLink(path)
                        || Files.readAttributes(path, java.nio.file.attribute.BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS).isOther()))
                    throw new java.io.IOException("Linked cache artifact: " + path);
            if (Files.exists(target)) { verifyArtifact(artifact, target); continue; }
            Files.createDirectories(target.getParent());
            Path part = Files.createTempFile(target.getParent(), ".download-", ".part");
            try {
                Path supplied = local.resolve(artifact.path());
                if (Files.isRegularFile(supplied)) Files.copy(supplied, part, StandardCopyOption.REPLACE_EXISTING);
                else download.copy(artifact.origin(), part);
                verifyArtifact(artifact, part);
                Files.move(part, target, StandardCopyOption.ATOMIC_MOVE);
            } finally { Files.deleteIfExists(part); }
        }
        manifest.verify(cache);
    }
    private static void verifyArtifact(BundleManifest.Artifact artifact, Path path) throws java.io.IOException {
        if (!Files.isRegularFile(path) || Files.size(path) != artifact.bytes() || !BundleManifest.digest(path).equals(artifact.sha512()))
            throw new java.io.IOException("Pinned artifact mismatch: " + artifact.path());
    }
    private static void download(java.net.URI origin, Path destination) throws Exception {
        // Never resolve package versions or invoke an external downloader.
        var connection = (javax.net.ssl.HttpsURLConnection) origin.toURL().openConnection();
        connection.setConnectTimeout(30_000); connection.setReadTimeout(120_000);
        connection.setInstanceFollowRedirects(false);
        try {
            int status = connection.getResponseCode();
            if (status != 200) throw new java.io.IOException("Pinned download returned HTTP " + status + ": " + origin);
            try (var input = connection.getInputStream()) { Files.copy(input, destination, StandardCopyOption.REPLACE_EXISTING); }
        } finally { connection.disconnect(); }
    }
    private static void remove(Path root) throws Exception {
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override public FileVisitResult preVisitDirectory(Path directory, java.nio.file.attribute.BasicFileAttributes attributes) throws java.io.IOException {
                if (attributes.isOther()) throw new java.io.IOException("Unexpected reparse directory in staging output");
                return FileVisitResult.CONTINUE;
            }
            @Override public FileVisitResult visitFile(Path file, java.nio.file.attribute.BasicFileAttributes attributes) throws java.io.IOException {
                Files.delete(file); return FileVisitResult.CONTINUE;
            }
            @Override public FileVisitResult postVisitDirectory(Path directory, java.io.IOException error) throws java.io.IOException {
                if (error != null) throw error;
                Files.delete(directory); return FileVisitResult.CONTINUE;
            }
        });
    }
}
