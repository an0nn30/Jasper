package dev.jasper.app.cygwin;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Validates the exact offline distribution; never resolves current mirror packages. */
public record BundleManifest(Map<String, Artifact> artifacts, List<Package> packages,
        String setup, String metadata, String signature, Map<String, String> relays, long installedBytes) {
    public record Artifact(String path, URI origin, long bytes, String sha512) { }
    public record Package(String name, String version, String binary, String source, String license, List<String> dependencies) { }
    public BundleManifest { artifacts = Map.copyOf(artifacts); packages = List.copyOf(packages); relays = Map.copyOf(relays); }
    public static BundleManifest read(Path file) throws IOException {
        if (Files.size(file) > 8 * 1024 * 1024) throw new IOException("Manifest too large");
        var root = CygwinJson.object(CygwinJson.parse(Files.readString(file)));
        if (CygwinJson.number(root, "schema") != 1 || !CygwinJson.string(root, "architecture").equals("x86_64"))
            throw new IOException("Unsupported Cygwin distribution schema or architecture");
        long installed = CygwinJson.number(root, "installedBytes");
        if (installed <= 0 || installed > 100_000_000_000L) throw new IOException("Invalid installed size");
        var artifacts = new LinkedHashMap<String, Artifact>(); var folded = new java.util.HashSet<String>();
        for (Object entry : CygwinJson.list(root, "artifacts")) {
            var obj = CygwinJson.object(entry); String path = CygwinJson.string(obj, "path"); safePath(path);
            URI origin;
            try { origin = URI.create(CygwinJson.string(obj, "origin")); }
            catch (IllegalArgumentException failure) { throw new IOException("Invalid artifact origin", failure); }
            if (!"https".equals(origin.getScheme()) || origin.getHost() == null || origin.getUserInfo() != null || origin.getFragment() != null)
                throw new IOException("Artifact origin must be HTTPS");
            long bytes = CygwinJson.number(obj, "bytes"); String hash = CygwinJson.string(obj, "sha512");
            if (bytes <= 0 || !hash.matches("[0-9a-f]{128}") || !folded.add(path.toLowerCase(Locale.ROOT)))
                throw new IOException("Invalid or duplicate artifact: " + path);
            artifacts.put(path, new Artifact(path, origin, bytes, hash));
        }
        var packages = new java.util.ArrayList<Package>(); var names = new java.util.HashSet<String>();
        for (Object entry : CygwinJson.list(root, "packages")) {
            var obj = CygwinJson.object(entry); String name = CygwinJson.string(obj, "name");
            if (!name.matches("[A-Za-z0-9_+.-]+") || !names.add(name)) throw new IOException("Invalid/duplicate package");
            var dependencies = new java.util.ArrayList<String>();
            for (Object dep : CygwinJson.list(obj, "dependencies")) {
                if (!(dep instanceof String string)) throw new IOException("Invalid dependency"); dependencies.add(string);
            }
            packages.add(new Package(name, CygwinJson.string(obj, "version"), reference(obj, "binary", artifacts),
                reference(obj, "source", artifacts), reference(obj, "license", artifacts), List.copyOf(dependencies)));
        }
        if (packages.isEmpty()) throw new IOException("Empty package selection");
        for (Package pkg : packages) if (!names.containsAll(pkg.dependencies())) throw new IOException("Missing dependencies: " + pkg.name());
        var relayObjects = CygwinJson.object(root.get("relays"));
        if (!relayObjects.keySet().equals(Set.of("aarch64", "amd64", "backend"))) throw new IOException("Incomplete relay architecture set");
        var relays = new LinkedHashMap<String, String>();
        for (String key : relayObjects.keySet()) relays.put(key, reference(relayObjects, key, artifacts));
        return new BundleManifest(artifacts, packages, reference(root, "setup", artifacts), reference(root, "metadata", artifacts),
            reference(root, "signature", artifacts), relays, installed);
    }
    private static String reference(Map<String, Object> obj, String key, Map<String, Artifact> artifacts) throws IOException {
        String path = CygwinJson.string(obj, key);
        if (!artifacts.containsKey(path)) throw new IOException("Missing artifact for " + key + ": " + path); return path;
    }
    public static void safePath(String path) throws IOException {
        for (String part : path.split("/", -1)) {
            String base = part.split("\\.", 2)[0].toUpperCase(Locale.ROOT);
            if (part.isEmpty() || part.equals(".") || part.equals("..") || part.endsWith(".") || part.endsWith(" ")
                    || part.chars().anyMatch(c -> c < 32 || "\\:*?\"<>|".indexOf(c) >= 0)
                    || Set.of("CON", "PRN", "AUX", "NUL", "COM1", "COM2", "COM3", "COM4", "COM5", "COM6", "COM7", "COM8", "COM9",
                        "LPT1", "LPT2", "LPT3", "LPT4", "LPT5", "LPT6", "LPT7", "LPT8", "LPT9").contains(base))
                throw new IOException("Unsafe artifact path: " + path);
        }
    }
    public void verify(Path cache) throws IOException {
        Path root = cache.toAbsolutePath().normalize();
        for (Artifact artifact : artifacts.values()) {
            Path file = root.resolve(artifact.path());
            for (Path p = file; p != null && p.startsWith(root); p = p.getParent())
                if (Files.isSymbolicLink(p) || Files.readAttributes(p, java.nio.file.attribute.BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS).isOther())
                    throw new IOException("Linked artifact path: " + p);
            if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) || Files.size(file) != artifact.bytes()
                    || !digest(file).equals(artifact.sha512())) throw new IOException("Artifact verification failed: " + artifact.path());
        }
    }
    public static String digest(Path file) throws IOException {
        try {
            var hash = MessageDigest.getInstance("SHA-512");
            try (var input = Files.newInputStream(file)) { byte[] bytes = new byte[65536]; int n;
                while ((n = input.read(bytes)) >= 0) hash.update(bytes, 0, n); }
            return HexFormat.of().formatHex(hash.digest());
        } catch (java.security.NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
    }
}
