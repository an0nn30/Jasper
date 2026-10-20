import dev.jasper.app.cygwin.BundleManifest;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.HexFormat;

public final class BundleManifestTest {
    private static int checks;
    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("cygwin-manifest-test");
        try {
            Files.writeString(root.resolve("archive"), "verified bytes");
            String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-512").digest(Files.readAllBytes(root.resolve("archive"))));
            String fixture = """
                {"schema":1,"architecture":"x86_64","installedBytes":100,
                 "setup":"archive","metadata":"archive","signature":"archive",
                 "artifacts":[{"path":"archive","origin":"https://example.org/archive","bytes":14,"sha512":"DIGEST"}],
                 "packages":[{"name":"bash","version":"1-1","binary":"archive","source":"archive","license":"archive","dependencies":[]}],
                 "relays":{"aarch64":"archive","amd64":"archive","backend":"archive"}}
                """.replace("DIGEST", digest);
            Path lock = root.resolve("seed.lock.json"); Files.writeString(lock, fixture);
            BundleManifest.read(lock).verify(root); checks++;
            for (String path : new String[]{"../archive", "/archive", "C:/archive", "a/../archive", "a\\\\archive", "a:stream", "CON", "a.", "a//b"})
                reject(lock, fixture.replace("\"path\":\"archive\"", "\"path\":\"" + path + "\""));
            reject(lock, fixture.replace("\"schema\":1", "\"schema\":2"));
            reject(lock, fixture.replace("\"schema\":1", "\"schema\":1,\"schema\":1"));
            reject(lock, fixture.replace("x86_64", "aarch64"));
            reject(lock, fixture.replace("\"source\":\"archive\"", "\"source\":\"missing\""));
            reject(lock, fixture.replace("\"license\":\"archive\"", "\"license\":\"\""));
            reject(lock, fixture.replace("\"dependencies\":[]", "\"dependencies\":[\"missing\"]"));
            reject(lock, fixture.replace("\"relays\":{", "\"relays\":{\"unknown\":\"archive\","));
            String artifact = fixture.substring(fixture.indexOf("{\"path\""), fixture.indexOf("],\n") );
            reject(lock, fixture.replace(artifact, artifact + "," + artifact));
            Files.writeString(lock, fixture);
            Path staged = Files.createDirectory(root.resolve("staged"));
            Files.writeString(staged.resolve("obsolete-package.tar.xz"), "stale bytes");
            BundleTool.main(new String[]{"stage", lock.toString(), root.toString(), staged.toString()});
            if (Files.exists(staged.resolve("obsolete-package.tar.xz"))) throw new AssertionError("Unlisted staged artifact retained");
            checks++;
            Files.writeString(lock, fixture); Files.writeString(root.resolve("archive"), "verified byteX");
            try { BundleManifest.read(lock).verify(root); throw new AssertionError("Changed bytes accepted"); }
            catch (java.io.IOException expected) { checks++; }
            Files.writeString(root.resolve("archive"), "short");
            try { BundleManifest.read(lock).verify(root); throw new AssertionError("Changed size accepted"); }
            catch (java.io.IOException expected) { checks++; }
            System.out.println("Manifest checks passed: " + checks);
        } finally { try (var files = Files.walk(root)) { for (Path p : files.sorted(java.util.Comparator.reverseOrder()).toList()) Files.delete(p); } }
    }
    private static void reject(Path path, String json) throws Exception {
        Files.writeString(path, json);
        try { BundleManifest.read(path); throw new AssertionError("Invalid manifest accepted: " + json); }
        catch (java.io.IOException expected) { checks++; }
    }
}
