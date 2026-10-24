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
            Path evidence = root.resolve("validation.json"), helper = root.resolve("helper.jar");
            Files.writeString(evidence, "{\"seed\":\"" + BundleManifest.digest(lock) + "\",\"aarch64\":false}");
            try (var jar = new java.util.jar.JarOutputStream(Files.newOutputStream(helper))) {
                jar.putNextEntry(new java.util.jar.JarEntry("CygwinTool.class")); jar.write(1); jar.closeEntry();
            }
            try { BundleTool.releaseEvidence(lock, helper, evidence, "aarch64"); throw new AssertionError("Unvalidated architecture accepted"); }
            catch (java.io.IOException expected) { checks++; }
            Files.writeString(evidence, Files.readString(evidence).replace("false", "true"));
            try { BundleTool.releaseEvidence(lock, helper, evidence, "aarch64"); throw new AssertionError("Missing interactive acceptance allowed"); }
            catch (java.io.IOException expected) { checks++; }
            Files.writeString(evidence, Files.readString(evidence).replace("}", ",\"interactiveApps\":{\"aarch64\":true}}"));
            BundleTool.releaseEvidence(lock, helper, evidence, "aarch64"); checks++;
            Files.writeString(evidence, Files.readString(evidence).replace(BundleManifest.digest(lock), "wrong-seed"));
            try { BundleTool.releaseEvidence(lock, helper, evidence, "aarch64"); throw new AssertionError("Stale release evidence accepted"); }
            catch (java.io.IOException expected) { checks++; }
            Path fetched = root.resolve("fetched");
            BundleTool.acquire(BundleManifest.read(lock), root, fetched, (origin, destination) -> { throw new AssertionError("Local artifact downloaded"); });
            BundleManifest.read(lock).verify(fetched); checks++;
            Files.writeString(fetched.resolve("archive"), "corrupt cache");
            try { BundleTool.acquire(BundleManifest.read(lock), root, fetched, (origin, destination) -> { throw new AssertionError("Corruption silently downloaded"); }); throw new AssertionError("Corrupt cache accepted"); }
            catch (java.io.IOException expected) { checks++; }
            Files.delete(fetched.resolve("archive"));
            Path noLocal = Files.createDirectory(root.resolve("no-local"));
            BundleTool.acquire(BundleManifest.read(lock), noLocal, fetched, (origin, destination) -> Files.writeString(destination, "verified bytes"));
            BundleManifest.read(lock).verify(fetched); checks++;
            Files.delete(fetched.resolve("archive"));
            try { BundleTool.acquire(BundleManifest.read(lock), noLocal, fetched, (origin, destination) -> Files.writeString(destination, "wrong download")); throw new AssertionError("Wrong download accepted"); }
            catch (java.io.IOException expected) { if (Files.exists(fetched.resolve("archive"))) throw new AssertionError("Unverified bytes published"); checks++; }
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
