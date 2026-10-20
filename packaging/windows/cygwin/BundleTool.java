import dev.jasper.app.cygwin.BundleManifest;
import java.nio.file.*;

/** Build-time entry point; installer provisioning uses the same manifest implementation. */
public final class BundleTool {
    public static void main(String[] args) throws Exception {
        if (args.length < 3 || !(args[0].equals("verify") || args[0].equals("stage")))
            throw new IllegalArgumentException("BundleTool verify|stage lock snapshot [destination]");
        Path lock = Path.of(args[1]), snapshot = Path.of(args[2]);
        var manifest = BundleManifest.read(lock); manifest.verify(snapshot);
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
