# Start-up Recovery, Plan 1: The Shipped Cache — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

Status: **Written 2026-10-14** on `claude/startup-recovery` (spec approved by the user the same day).

**Goal:** The AOT cache shipped inside `Jasper.app` works wherever Jasper is installed and never depends on the
repository's build directory, and Jasper finds its own files (bundled plugins, handoff identity, the replaced-on-disk
check) from where it is installed.

**Architecture:** `stagePackage` rewrites sealed class-path jars without their `Sealed` attributes, so a class the
JBR 25 AOT cache loads without a code source can no longer stop the JVM; `verifyPackage` rejects a sealed jar in the
image. A new `dev.jasper.app.platform.CodeLocation` reads a class's jar or classes directory from its resource URL, and
every former code-source caller uses it. `trainAotCache` trains in a throwaway `ditto` copy of the image that is
deleted before `verifyAotCache`, so the cache records paths that exist on no machine.

**Tech Stack:** Gradle Groovy scripts (`gradle/packaging*.gradle`), Java 25 (JBR 25.0.4.1), JUnit 5 + AssertJ.

**Spec:** [`docs/superpowers/specs/2026-10-14-jasper-startup-recovery-design.md`](../specs/2026-10-14-jasper-startup-recovery-design.md)
(sections 2 and 3, plan 1 of section 12).

## Global Constraints

- Staging removes every `Sealed` attribute (main attributes and per-entry sections) from app class-path jars, keeps
  every other entry and attribute, and adds the main attribute `Jasper-Modified: package sealing removed`.
- The About screen's notices gain `jbr-api.NOTICE.txt`; `jbr-api` is Apache-2.0.
- `HandoffSocket.codeSource()` becomes `HandoffSocket.codeLocation()`, read from the class's own resource URL:
  `jar:file:<jar>!/<entry>` gives `<jar>`, `file:<dir>/<entry>` gives `<dir>`, anything else `null`. No code in the
  modules on the application class path (`jasper-app`, `jasper-ui`, `jasper-terminal`, `jasper-buddy`, `jasper-sdk`)
  reads a `ProtectionDomain` code source.
- `trainAotCache` copies the image with `ditto` to `build/packaging/aot-training-image/Jasper.app`, trains there, copies
  `jasper.aot` into the image and deletes the copy before `verifyAotCache` runs.
- `dev.jasper.app.platform` stays free of other Jasper packages (its `package-info.java`); `residency` may use it.
- MVP (memory `jasper-mvp-no-compat`): rename outright, no compatibility aliases.
- Never launch the Jasper GUI and never run `trainAotCache`, `packageDist` or anything with `-Pjasper.aot=true`
  (AGENTS.md). `packageApp` and `verifyPackage` without that property are headless and fine to run.
- Work in the worktree `.claude/worktrees/startup-recovery` (branch `claude/startup-recovery`), never on `main`. Every
  commit message ends with:

  ```
  🤖 Generated with Claude Code at The Home Depot

  Co-Authored-By: Claude <noreply@anthropic.com>
  ```
- Source hygiene (AGENTS.md): no raw control, private-use or unpaired surrogate characters; write escapes such as
  `ä`.

## Verified while writing this plan (2026-10-14; do not re-derive)

- Of the third-party jars on the app class path only FlatLaf's `FlatNativeLibrary` reads its code source, and only when
  the library is missing from `flatlaf.nativeLibraryPath`: it first loads `<flatlaf.nativeLibraryPath>/<mapped name>`
  when that file exists, which `Jasper.cfg` and the bundled natives guarantee on macOS and Windows (the only platforms
  with an AOT cache are macOS). JNA (`jna.boot.library.path`) and pty4j (`pty4j.preferred.native.folder`) get explicit
  paths. Spec 3.2's confirmation is therefore done; no task changes a library.
- `jasper-ui`, `jasper-terminal`, `jasper-buddy` and `jasper-sdk` main sources contain no `getProtectionDomain` or
  `getCodeSource` call; `jasper-app` has two (`HandoffSocket`, `MacAppearance`).
- `ThirdPartyNoticesTest` already asserts that every `notice = …` file named in `notices.toml` is readable and not
  blank.
- `verifyPackage` compares every staged file byte-for-byte with the packaged one, so unsealing in `stagePackage`
  reaches the image unchanged.

## Planned deviations from the spec

None.

## Review Focus

- A jar sealed only per package (a `Name: p/` section, no main `Sealed`): it must still be unsealed and pass
  `verifyPackage` (fixture `p/` and `q/` sections, Task 1).
- A future dependency that is both signed and sealed: the build must fail naming the jar instead of shipping a jar
  whose signature no longer verifies (Task 1).
- Jasper installed under a path with a space or a non-ASCII character (`/Users/x/My Apps/Jäsper.app`): bundled
  plugins and the handoff identity must still resolve (Task 2, `CodeLocationTest`).
- `:jasper-app:run` from a classes directory: the code location is that directory and the bundled plugin directory is
  `null`, as before (Task 2, `PluginRuntimeTest` unchanged and `CodeLocationTest`).
- A training copy left behind by a killed earlier training, or a copier that loses modification times: the stale copy
  is replaced and a time mismatch fails before any window opens (Task 3).

---

### Task 1: Unseal the application class path

**Files:**
- Modify: `gradle/packaging-support.gradle` (imports; new `sealingSections`, `unsealJar` after `modificationTimeMismatches`)
- Modify: `gradle/packaging-support-test.gradle` (new `verifySupport.configure` block at the end)
- Modify: `gradle/packaging.gradle:196-207` (`stagePackage` gains a `doLast`), `gradle/packaging.gradle:369-375`
  (`verifyPackage` gains the sealed-jar check after the staged-file comparison)
- Modify: `jasper-app/src/main/resources/dev/jasper/app/about/licenses/notices.toml` (the "JetBrains Runtime API" component)
- Create: `jasper-app/src/main/resources/dev/jasper/app/about/licenses/jbr-api.NOTICE.txt`

**Interfaces:**
- Produces: `sealingSections(File jar) -> List<String>` (`'main'` first when the main attributes declare `Sealed`, then
  the per-entry section names that declare it, sorted); `unsealJar(File jar) -> boolean` (true when it rewrote the jar;
  throws `GradleException` for a signed jar that declares `Sealed`).

- [ ] **Step 1: Write the failing fixture test**

Append to `gradle/packaging-support-test.gradle`:

```groovy
verifySupport.configure {
    doLast {
        // Spec 2026-10-14 section 3.1: classes the AOT cache loads get no code source once its training paths are gone,
        // and a sealed package then refuses its second class, so staging unseals the class path.
        File jars = new File(temporaryDir, 'sealing')
        jars.deleteDir()
        jars.mkdirs()
        def manifest = new java.util.jar.Manifest()
        manifest.mainAttributes.putValue('Manifest-Version', '1.0')
        manifest.mainAttributes.putValue('Sealed', 'true')
        manifest.mainAttributes.putValue('Multi-Release', 'true')
        manifest.entries.put('p/', new java.util.jar.Attributes())
        manifest.entries['p/'].putValue('Sealed', 'true')
        manifest.entries['p/'].putValue('Implementation-Title', 'P')
        manifest.entries.put('q/', new java.util.jar.Attributes())
        manifest.entries['q/'].putValue('Sealed', 'false')
        long stamp = 1_759_400_000_000L
        File sealed = new File(jars, 'sealed.jar')
        sealed.withOutputStream { raw ->
            new java.util.jar.JarOutputStream(raw, manifest).withCloseable { out ->
                [['p/', null], ['p/A.class', 'class A'], ['q/B.class', 'class B']].each { String name, String body ->
                    def entry = new java.util.zip.ZipEntry(name)
                    entry.time = stamp
                    if (name == 'q/B.class') entry.comment = 'kept'
                    out.putNextEntry(entry)
                    if (body != null) out.write(body.getBytes('UTF-8'))
                    out.closeEntry()
                }
            }
        }
        def entries = { File jar ->
            new java.util.zip.ZipFile(jar).withCloseable { zip ->
                zip.entries().toList().collect { entry ->
                    [entry.name, entry.time, entry.comment, entry.directory ? null : zip.getInputStream(entry).bytes.encodeHex().toString()]
                }
            }
        }
        def before = entries(sealed)
        assert sealingSections(sealed) == ['main', 'p/', 'q/']
        assert unsealJar(sealed)
        assert sealingSections(sealed).isEmpty()
        def after = entries(sealed)
        assert after.collect { it[0] } == before.collect { it[0] } : 'entries keep their names and order'
        assert after.findAll { it[0] != 'META-INF/MANIFEST.MF' } == before.findAll { it[0] != 'META-INF/MANIFEST.MF' } :
            'every other entry keeps its bytes, time and comment'
        def rewritten = new java.util.jar.JarFile(sealed).withCloseable { it.manifest }
        assert rewritten.mainAttributes.getValue('Sealed') == null
        assert rewritten.mainAttributes.getValue('Multi-Release') == 'true'
        assert rewritten.mainAttributes.getValue('Jasper-Modified') == 'package sealing removed'
        assert rewritten.entries.keySet() == ['p/'] as Set : 'a section left empty is dropped'
        assert rewritten.entries['p/'].getValue('Implementation-Title') == 'P'
        byte[] once = sealed.bytes
        assert !unsealJar(sealed) && Arrays.equals(sealed.bytes, once) : 'an unsealed jar is left alone'

        File plain = new File(jars, 'plain.jar')
        plain.withOutputStream { raw ->
            new java.util.zip.ZipOutputStream(raw).withCloseable { out ->
                out.putNextEntry(new java.util.zip.ZipEntry('a.txt'))
                out.write('a'.getBytes('UTF-8'))
                out.closeEntry()
            }
        }
        assert sealingSections(plain).isEmpty() && !unsealJar(plain) : 'a jar without a manifest is not sealed'

        File signed = new File(jars, 'signed.jar')
        signed.withOutputStream { raw ->
            new java.util.jar.JarOutputStream(raw, manifest).withCloseable { out ->
                out.putNextEntry(new java.util.zip.ZipEntry('META-INF/SIGNER.SF'))
                out.write('Signature-Version: 1.0\n'.getBytes('UTF-8'))
                out.closeEntry()
            }
        }
        byte[] signedBytes = signed.bytes
        try {
            unsealJar(signed)
            assert false : 'a signed, sealed jar must fail the build'
        } catch (GradleException expected) {
            assert expected.message.contains('signed.jar') && expected.message.contains('signed;')
        }
        assert Arrays.equals(signed.bytes, signedBytes) : 'a signed jar is left untouched'
        assert !new File(jars, 'signed.jar.unsealing').exists()
        logger.lifecycle('Verified unsealing of the application class path')
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew :jasper-app:verifyPackagingSupport`
Expected: FAIL with `No signature of method` / `Could not find method sealingSections()` (the helpers do not exist yet).

- [ ] **Step 3: Implement the helpers**

In `gradle/packaging-support.gradle`, replace the import block at the top:

```groovy
import groovy.json.JsonException
import groovy.json.JsonSlurper
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.StandardCopyOption
import java.util.concurrent.TimeUnit
import java.util.jar.Attributes
import java.util.jar.JarFile
import java.util.jar.Manifest
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
```

and append after `ext.modificationTimeMismatches = { … }`:

```groovy
// The manifest sections of jar that declare Sealed (any value): 'main' for the main attributes, then the per-entry
// section names, sorted. Classes the JBR 25 AOT cache loads get no code source once the cache's training paths are gone
// (JDK-8376576, spec 2026-10-14 section 2), and a package sealed by the manifest then refuses its second class, which
// stops the JVM; so stagePackage unseals the class path and verifyPackage rejects a sealed jar in the image.
ext.sealingSections = { File jar ->
    Manifest manifest = new JarFile(jar, false).withCloseable { it.manifest }
    if (manifest == null) return []
    List<String> sections = manifest.entries.findAll { String name, Attributes attributes ->
        attributes.getValue(Attributes.Name.SEALED) != null
    }.keySet().sort()
    manifest.mainAttributes.getValue(Attributes.Name.SEALED) != null ? ['main'] + sections : sections
}

// Rewrites jar without its manifest's Sealed attributes (main and per-entry; a section left empty is dropped) and adds
// the main attribute "Jasper-Modified: package sealing removed", the notice Apache-2.0 section 4(b) asks of modified
// files. Every other entry is copied in order with its bytes, time, comment and extra field. Returns whether it changed
// the jar. Rewriting the manifest of a signed jar would break its signature, so a signed jar that declares Sealed fails.
ext.unsealJar = { File jar ->
    if (sealingSections(jar).isEmpty()) return false
    Manifest manifest = new JarFile(jar, false).withCloseable { it.manifest }
    manifest.mainAttributes.remove(Attributes.Name.SEALED)
    manifest.mainAttributes.put(new Attributes.Name('Jasper-Modified'), 'package sealing removed')
    manifest.entries.values().each { it.remove(Attributes.Name.SEALED) }
    manifest.entries.values().removeIf { it.isEmpty() }
    File rewritten = new File(jar.parentFile, jar.name + '.unsealing')
    new ZipFile(jar).withCloseable { zip ->
        List<ZipEntry> entries = zip.entries().toList()
        if (entries.any { it.name ==~ /(?i)META-INF\/[^\/]+\.(SF|RSA|DSA|EC)/ }) {
            throw new GradleException("Packaging: ${jar.name} seals packages and is signed; unsealing it would break its signature")
        }
        rewritten.withOutputStream { raw ->
            new ZipOutputStream(raw).withCloseable { out ->
                entries.each { ZipEntry entry ->
                    ZipEntry copy = new ZipEntry(entry.name)
                    copy.time = entry.time
                    if (entry.comment != null) copy.comment = entry.comment
                    if (entry.extra != null) copy.extra = entry.extra
                    out.putNextEntry(copy)
                    if (entry.name.equalsIgnoreCase(JarFile.MANIFEST_NAME)) {
                        manifest.write(out)
                    } else if (!entry.directory) {
                        zip.getInputStream(entry).withCloseable { out << it }
                    }
                    out.closeEntry()
                }
            }
        }
    }
    Files.move(rewritten.toPath(), jar.toPath(), StandardCopyOption.REPLACE_EXISTING)
    true
}
```

- [ ] **Step 4: Run the fixture test to verify it passes**

Run: `./gradlew :jasper-app:verifyPackagingSupport`
Expected: PASS, with `Verified unsealing of the application class path` among the lifecycle lines.

- [ ] **Step 5: Unseal in staging and reject sealed jars in verification**

In `gradle/packaging.gradle`, replace the `stagePackage` registration:

```groovy
def stage = tasks.register('stagePackage', Sync) {
    group = 'distribution'
    description = 'Stages the application JAR, runtime dependencies, configuration example and package guide.'
    dependsOn(jarTask)
    from(jarTask.flatMap { it.archiveFile })
    from(configurations.runtimeClasspath)
    from(rootProject.file('config.example.toml'))
    from(rootProject.file('packaging/README.txt'))
    from(tasks.named('stagePlugins')) { into('plugins') }
    into(inputDirectory)
    duplicatesStrategy = DuplicatesStrategy.FAIL
    // Spec 2026-10-14 section 3.1: the AOT cache's classes cannot satisfy package sealing (sealingSections). Nothing in
    // Jasper relies on sealing; today this rewrites jbr-api only.
    doLast {
        inputDirectory.get().asFile.listFiles().findAll { it.name.endsWith('.jar') }.sort { it.name }.each { File jar ->
            if (unsealJar(jar)) logger.lifecycle("Unsealed ${jar.name}: classes from the AOT cache cannot satisfy package sealing")
        }
    }
}
```

In `verifyPackage`'s `doLast`, directly after the loop that compares `inputDirectory` files with the packaged ones
(the block ending `throw new GradleException("Package verification failed: staged file differs or is missing: ${staged.name}")`
and its two closing braces), insert:

```groovy
        // stagePackage unseals the class path (spec 2026-10-14 section 3.1); a sealed jar here would stop the JVM once
        // the AOT cache's training paths are gone.
        appDir.listFiles().findAll { it.name.endsWith('.jar') }.sort { it.name }.each { File jar ->
            List<String> sealing = sealingSections(jar)
            if (sealing) throw new GradleException("Package verification failed: ${jar.name} still seals packages (${sealing.join(', ')})")
        }
```

- [ ] **Step 6: Record the modification in the About notices**

Create `jasper-app/src/main/resources/dev/jasper/app/about/licenses/jbr-api.NOTICE.txt`:

```
JetBrains Runtime API
Copyright 2000-2025 JetBrains s.r.o.

Jasper ships jbr-api 1.9.0 with one change to META-INF/MANIFEST.MF: the "Sealed" attribute is removed and the
attribute "Jasper-Modified: package sealing removed" is added. Classes loaded from Jasper's start-up (AOT) cache
cannot satisfy package sealing on JetBrains Runtime 25 (OpenJDK bug JDK-8376576). No class or other file is changed.
```

In `notices.toml`, make the "JetBrains Runtime API" component:

```toml
[[component]]
name = "JetBrains Runtime API"
version = "1.9.0"
license = "Apache-2.0"
homepage = "https://github.com/JetBrains/JetBrainsRuntimeApi"
modules = ["org.jetbrains.runtime:jbr-api:1.9.0"]
text = "apache-2.0.txt"
notice = "jbr-api.NOTICE.txt"
```

- [ ] **Step 7: Verify staging, the image and the notices**

Run: `./gradlew :jasper-app:verifyPackagingSupport :jasper-app:test --tests 'dev.jasper.app.about.*'`
Expected: PASS.

Run: `./gradlew :jasper-app:verifyPackage`
Expected: PASS (headless: builds the untrained image), with `Unsealed jbr-api-1.9.0.jar: classes from the AOT cache
cannot satisfy package sealing` in the output.

Then confirm the packaged jar (macOS image path; on Linux it is `image/Jasper/lib/app/`, on Windows `image/Jasper/app/`):

```bash
unzip -p jasper-app/build/packaging/image/Jasper.app/Contents/app/jbr-api-1.9.0.jar META-INF/MANIFEST.MF
```

Expected: `Multi-Release: true` and `Jasper-Modified: package sealing removed`, no `Sealed:` line.

- [ ] **Step 8: Commit**

```bash
git add gradle/packaging-support.gradle gradle/packaging-support-test.gradle gradle/packaging.gradle \
  jasper-app/src/main/resources/dev/jasper/app/about/licenses/notices.toml \
  jasper-app/src/main/resources/dev/jasper/app/about/licenses/jbr-api.NOTICE.txt
git commit -F - <<'EOF'
Unseal the application class path at staging

Classes from the JBR 25 AOT cache get no code source once the cache's
training paths are gone (JDK-8376576), so the sealed jbr-api package
refused its second class and the JVM exited. Staging now removes Sealed
attributes, verifyPackage rejects a sealed jar, and the About notices
record the change to jbr-api's manifest.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

---

### Task 2: Find Jasper's own location from its class resource

**Files:**
- Create: `jasper-app/src/main/java/dev/jasper/app/platform/CodeLocation.java`
- Create: `jasper-app/src/test/java/dev/jasper/app/platform/CodeLocationTest.java`
- Create: `jasper-app/src/test/java/dev/jasper/app/architecture/CodeSourceScanTest.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/residency/HandoffSocket.java:6, 112-140, 182-191`
- Modify: `jasper-app/src/main/java/dev/jasper/app/residency/LaunchRequest.java:15-47`
- Modify: `jasper-app/src/main/java/dev/jasper/app/bootstrap/ApplicationBootstrap.java:129, 176, 204, 345, 444-457`
- Modify: `jasper-app/src/main/java/dev/jasper/app/plugins/PluginRuntime.java:470-482`
- Modify: `jasper-app/src/main/java/dev/jasper/app/application/JasperApplication.java:796-801, 821`
- Modify: `jasper-app/src/main/java/dev/jasper/app/platform/MacAppearance.java:17-22`
- Modify: `jasper-app/src/test/java/dev/jasper/app/residency/HandoffSocketTest.java:37-38, 95-101, 199-205`

**Interfaces:**
- Produces: `dev.jasper.app.platform.CodeLocation.of(Class<?> type) -> Path` (nullable) and package-private
  `CodeLocation.of(URL resource, String entry) -> Path`; `HandoffSocket.codeLocation() -> Path` (nullable);
  `LaunchRequest(String token, Path codeLocation, long codeLocationModified, Kind kind)` with accessors
  `codeLocation()` and `codeLocationModified()`; `PluginRuntime.bundledDirectory(Path codeLocation)`;
  `JasperApplication.startPlugins(Path codeLocation, Path developmentDirectory, boolean safeMode, AppDirs dirs)`.

- [ ] **Step 1: Write the failing `CodeLocation` test**

Create `jasper-app/src/test/java/dev/jasper/app/platform/CodeLocationTest.java`:

```java
package dev.jasper.app.platform;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CodeLocationTest {
    private static final String ENTRY = "dev/jasper/app/platform/CodeLocation.class";

    @TempDir Path dir;

    @Test void aJarResourceNamesTheJarEvenWithASpaceAndANonAsciiCharacterInItsPath() throws Exception {
        Path jar = dir.resolve("My Apps/Jäsper.app/Contents/app/jasper-app.jar");
        var resource = URI.create("jar:" + jar.toUri() + "!/" + ENTRY).toURL();
        assertThat(CodeLocation.of(resource, ENTRY)).isEqualTo(jar.toAbsolutePath().normalize());
    }

    @Test void aClassesDirectoryResourceNamesTheDirectory() throws Exception {
        Path classes = dir.resolve("build dir/classes/java/main");
        var resource = classes.resolve(ENTRY).toUri().toURL();
        assertThat(CodeLocation.of(resource, ENTRY)).isEqualTo(classes.toAbsolutePath().normalize());
    }

    @Test void anythingElseHasNoLocation() throws Exception {
        assertThat(CodeLocation.of(null, ENTRY)).isNull();
        assertThat(CodeLocation.of(dir.resolve("Other.class").toUri().toURL(), ENTRY)).as("another file").isNull();
        assertThat(CodeLocation.of(URI.create("jar:" + dir.resolve("a.jar").toUri() + "!/other/Name.class").toURL(), ENTRY))
            .as("another entry").isNull();
        assertThat(CodeLocation.of(URI.create("jar:http://example.com/a.jar!/" + ENTRY).toURL(), ENTRY))
            .as("a jar that is not a file").isNull();
        assertThat(CodeLocation.of(URI.create("jrt:/java.base/java/lang/Object.class").toURL(), "java/lang/Object.class"))
            .as("the runtime image").isNull();
    }

    @Test void thisBuildIsFoundAtTheJarOrDirectoryHoldingItsClassFile() throws Exception {
        Path location = CodeLocation.of(CodeLocation.class);
        assertThat(location).as("tests run from a jar or a classes directory").isNotNull();
        assertThat(location.isAbsolute()).isTrue();
        if (Files.isDirectory(location)) {
            assertThat(location.resolve(ENTRY)).isRegularFile();
        } else {
            try (var jar = new JarFile(location.toFile())) {
                assertThat(jar.getEntry(ENTRY)).isNotNull();
            }
        }
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.platform.CodeLocationTest'`
Expected: FAIL to compile: `cannot find symbol: class CodeLocation`.

- [ ] **Step 3: Implement `CodeLocation`**

Create `jasper-app/src/main/java/dev/jasper/app/platform/CodeLocation.java`:

```java
package dev.jasper.app.platform;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.Path;

/**
 * Where a class was loaded from: its jar, or its classes directory during development.
 *
 * <p>Read from the class's own resource URL, never from its protection domain: a class the JDK 25 AOT cache loads
 * reports the location the cache was trained at, or none once that location is gone (JDK-8376576, fixed for JDK 28),
 * while the class path's resource lookup always names the file that is installed.
 */
public final class CodeLocation {
    private CodeLocation() { }

    /** The jar or classes directory {@code type} was loaded from; null when it cannot be determined. */
    public static Path of(Class<?> type) {
        String entry = type.getName().replace('.', '/') + ".class";
        return of(type.getResource("/" + entry), entry);
    }

    /**
     * The jar or classes directory holding the class file {@code entry} that was found at {@code resource}:
     * {@code jar:file:<jar>!/<entry>} gives the jar and {@code file:<dir>/<entry>} the directory; anything else, null.
     */
    static Path of(URL resource, String entry) {
        if (resource == null) return null;
        try {
            if (resource.getProtocol().equals("jar")) {
                String path = resource.getPath();
                int separator = path.lastIndexOf("!/");
                if (separator < 0 || !path.substring(separator + 2).equals(entry)) return null;
                URI jar = new URI(path.substring(0, separator));
                return "file".equals(jar.getScheme()) ? Path.of(jar).toAbsolutePath().normalize() : null;
            }
            if (resource.getProtocol().equals("file")) {
                Path file = Path.of(resource.toURI()).toAbsolutePath().normalize();
                Path relative = Path.of(entry);
                if (!file.endsWith(relative)) return null;
                Path root = file;
                for (int i = 0; i < relative.getNameCount(); i++) root = root.getParent();
                return root;
            }
            return null;
        } catch (URISyntaxException | IllegalArgumentException | FileSystemNotFoundException unusable) {
            return null;
        }
    }
}
```

- [ ] **Step 4: Run it to verify it passes**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.platform.CodeLocationTest'`
Expected: PASS (4 tests).

- [ ] **Step 5: Write the failing scan test**

Create `jasper-app/src/test/java/dev/jasper/app/architecture/CodeSourceScanTest.java`:

```java
package dev.jasper.app.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Spec 2026-10-14 section 3.2: classes the AOT cache loads report the cache's training location, or none
 * (JDK-8376576), so the modules on the application class path find their files through {@code CodeLocation}.
 * Plugins load from their own class loaders, outside the cache, and are not scanned.
 */
class CodeSourceScanTest {
    static final Path ROOT = Path.of(System.getProperty("jasper.repoRoot"));
    static final List<String> CLASS_PATH_MODULES = List.of("jasper-app", "jasper-ui", "jasper-terminal", "jasper-buddy", "jasper-sdk");

    @Test void noModuleOnTheApplicationClassPathReadsACodeSource() throws IOException {
        var offenders = new ArrayList<String>();
        for (String module : CLASS_PATH_MODULES) {
            try (Stream<Path> files = Files.walk(ROOT.resolve(module).resolve("src/main/java"))) {
                for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                    String text = Files.readString(file, StandardCharsets.UTF_8);
                    if (text.contains("getProtectionDomain(") || text.contains("getCodeSource(")) {
                        offenders.add(ROOT.relativize(file).toString().replace('\\', '/'));
                    }
                }
            }
        }
        assertThat(offenders).isEmpty();
    }
}
```

- [ ] **Step 6: Run it to verify it fails**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.architecture.CodeSourceScanTest'`
Expected: FAIL: `offenders` contains `jasper-app/src/main/java/dev/jasper/app/platform/MacAppearance.java` and
`jasper-app/src/main/java/dev/jasper/app/residency/HandoffSocket.java`.

- [ ] **Step 7: Move every caller to the code location**

`HandoffSocket.java`: delete `import java.net.URISyntaxException;`, add `import dev.jasper.app.platform.CodeLocation;`
after `import dev.jasper.app.platform.OwnerOnlyFiles;`, and replace the `codeSource()` method with:

```java
    /** The jar or classes directory this build was loaded from; null when it cannot be determined. */
    public static Path codeLocation() {
        return CodeLocation.of(HandoffSocket.class);
    }
```

In the same file rename the parameter `codeSource` to `codeLocation` in `handOff`, `retire` and `exchange`, and make
the two lines inside `exchange` that use it:

```java
        // codeLocation() is nullable; an unknown location compares equal to itself and to nothing else.
        var request = new LaunchRequest(token, codeLocation == null ? Path.of("") : codeLocation, modified, kind);
```

`LaunchRequest.java`: rename the components and every use (the wire format is unchanged; names are not serialized):

```java
public record LaunchRequest(String token, Path codeLocation, long codeLocationModified, Kind kind) {
```

```java
    public LaunchRequest {
        Objects.requireNonNull(token, "token");
        Objects.requireNonNull(codeLocation, "codeLocation");
        Objects.requireNonNull(kind, "kind");
    }
```

```java
    /** An ordinary launch. */
    public LaunchRequest(String token, Path codeLocation, long codeLocationModified) {
        this(token, codeLocation, codeLocationModified, Kind.OPEN);
    }
```

```java
        String line = String.join("\t", MAGIC, Integer.toString(PROTOCOL), token,
            encode(codeLocation), Long.toString(codeLocationModified));
```

`ApplicationBootstrap.java`: replace each of the four `HandoffSocket.codeSource()` calls (lines 129, 176, 204, 345) with
`HandoffSocket.codeLocation()`, and make `stale` and the `replacedOnDisk` comment:

```java
    /** True when a request comes from a different build than this one. A resident process must not
     *  serve windows built from code it is not running. */
    static boolean stale(LaunchRequest request, Path source, long modified) {
        return !request.codeLocation().equals(source == null ? Path.of("") : source)
            || request.codeLocationModified() != modified;
    }

    /**
     * True once the application jar this process was loaded from has changed on disk (a new build was installed under
     * it) or gone. An unknown code location never counts. Any thread; one file stat per call.
     */
```

`PluginRuntime.java`: make `bundledDirectory`:

```java
    /**
     * The bundled plugin directory: the {@code jasper.plugins.bundled} system property when set (Gradle
     * run and tests), otherwise {@code plugins} beside the application jar. A classes directory has none.
     *
     * @param codeLocation the application's code location ({@code HandoffSocket.codeLocation()}), or null
     * @return the directory, which need not exist, or null
     */
    public static Path bundledDirectory(Path codeLocation) {
        String override = System.getProperty("jasper.plugins.bundled");
        if (override != null && !override.isBlank()) return Path.of(override);
        if (codeLocation == null || !Files.isRegularFile(codeLocation)) return null;
        return codeLocation.toAbsolutePath().getParent().resolve("plugins");
    }
```

`JasperApplication.java`: rename the first parameter of `startPlugins` to `codeLocation`, its Javadoc sentence to
"{@code codeLocation} locates bundled plugins beside the application jar and may be null;", and the call at line 821 to
`PluginRuntime.bundledDirectory(codeLocation)`.

`MacAppearance.java`: make `load()`:

```java
    @SuppressWarnings("restricted")
    private static synchronized void load() {
        if (loaded) return;
        Path path = library(System.getProperty("jasper.appearanceLibrary"), System.getProperty("jpackage.app-path"),
            CodeLocation.of(MacAppearance.class));
        if (path == null) throw new IllegalStateException("No development or packaged appearance bridge");
        System.load(path.toString()); loaded = true;
    }
```

`HandoffSocketTest.java`: in `anAcceptedRequestReachesTheHandlerAndTheTokenFileIsOwnerOnly` use
`seen.get(0).codeLocation()` and `seen.get(0).codeLocationModified()`; rename
`anUnresolvableCodeSourceHandsOffInsteadOfCrashingTheLauncher` to
`anUnknownCodeLocationHandsOffInsteadOfCrashingTheLauncher` and use `.extracting(LaunchRequest::codeLocation)`; and make
the build-location test:

```java
    @Test void theCodeLocationOfThisBuildIsResolvableAndItsTimestampIsReadable() {
        Path source = HandoffSocket.codeLocation();
        assertThat(source).as("tests run from a jar or a classes directory").isNotNull();
        assertThat(source.isAbsolute()).isTrue();
        assertThat(HandoffSocket.lastModified(source)).isPositive();
        assertThat(HandoffSocket.lastModified(dir.resolve("absent"))).isZero();
    }
```

- [ ] **Step 8: Check nothing still uses the old names**

Run: `grep -rn 'codeSource()\|codeSourceModified\|HandoffSocket.codeSource\|startPlugins(Path codeSource' jasper-app/src`
Expected: no output (test helpers that call `getCodeSource()` on their own test classes, such as
`BundledNativesProbeTest.codeSource(String)`, are test code outside the cache and stay).

- [ ] **Step 9: Run the affected tests and the architecture checks**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.platform.*' --tests 'dev.jasper.app.residency.*' --tests 'dev.jasper.app.bootstrap.*' --tests 'dev.jasper.app.plugins.PluginRuntimeTest' --tests 'dev.jasper.app.architecture.*' :verifyApplicationArchitecture`
Expected: PASS, including `CodeSourceScanTest`.

- [ ] **Step 10: Commit**

```bash
git add jasper-app/src
git commit -F - <<'EOF'
Find Jasper's own jar from its class resource, not its code source

Classes from the AOT cache report the cache's training path as their
code source, or none (JDK-8376576), so the installed app read its
bundled plugins, handoff identity and replaced-on-disk check from the
build image. CodeLocation reads the class's resource URL instead, and a
scan keeps code-source lookups out of the class-path modules.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

---

### Task 3: Train the AOT cache in a throwaway copy

**Files:**
- Modify: `gradle/packaging-support.gradle` (new `trainInCopy` after `unsealJar`)
- Modify: `gradle/packaging-support-test.gradle` (new `verifySupport.configure` block at the end)
- Modify: `gradle/packaging.gradle:897-919` (`trainAotCache`)

**Interfaces:**
- Consumes: `modificationTimeMismatches(File expectedRoot, File actualRoot) -> List<String>` (existing),
  `runAotTraining(File launcher, File work, Long settleSeconds, Long exitSeconds) -> File` (existing),
  `executeNative(List<String> command, String purpose)` (existing, local to `packaging.gradle`).
- Produces: `trainInCopy(File image, File copy, Closure copier, Closure train) -> Object` (what `train` returns).

- [ ] **Step 1: Write the failing fixture test**

Append to `gradle/packaging-support-test.gradle`:

```groovy
verifySupport.configure {
    doLast {
        // Spec 2026-10-14 section 3.3: training runs in a copy that is gone before the cache is verified.
        File root = new File(temporaryDir, 'training copy')
        root.deleteDir()
        File image = new File(root, 'image/Jasper.app')
        File copy = new File(root, 'aot-training-image/Jasper.app')
        File jar = new File(image, 'Contents/app/jasper-app.jar')
        jar.parentFile.mkdirs()
        jar.text = 'jar'
        assert jar.setLastModified(1_759_400_000_000L)
        def keepTimes = { File from, File to ->
            from.eachFileRecurse(groovy.io.FileType.FILES) { File source ->
                File target = new File(to, from.toPath().relativize(source.toPath()).toString())
                target.parentFile.mkdirs()
                target.bytes = source.bytes
                assert target.setLastModified(source.lastModified())
            }
        }
        File stale = new File(copy, 'Contents/app/left-over.jar')
        stale.parentFile.mkdirs()
        stale.text = 'from a killed training'

        File seen = null
        def result = trainInCopy(image, copy, keepTimes, { File trainee ->
            seen = trainee
            assert new File(trainee, 'Contents/app/jasper-app.jar').text == 'jar'
            assert !new File(trainee, 'Contents/app/left-over.jar').exists() : 'a stale copy is replaced'
            'cache'
        })
        assert result == 'cache' && seen == copy
        assert !copy.exists() : 'the copy is gone once training returns'
        assert jar.isFile() : 'the image is untouched'

        boolean trained = false
        def loseTimes = { File from, File to ->
            keepTimes(from, to)
            assert new File(to, 'Contents/app/jasper-app.jar').setLastModified(1_800_000_000_000L)
        }
        try {
            trainInCopy(image, copy, loseTimes, { File trainee -> trained = true })
            assert false : 'a copy with other modification times must fail'
        } catch (GradleException expected) {
            assert expected.message.contains('jasper-app.jar') && expected.message.contains('modification times')
        }
        assert !trained && !copy.exists()

        try {
            trainInCopy(image, copy, keepTimes, { File trainee -> throw new GradleException('training failed') })
            assert false : 'a failed training must propagate'
        } catch (GradleException expected) {
            assert expected.message == 'training failed'
        }
        assert !copy.exists() : 'a failed training still removes the copy'
        logger.lifecycle('Verified training in a throwaway copy')
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew :jasper-app:verifyPackagingSupport`
Expected: FAIL with `Could not find method trainInCopy()` (or `No signature of method`).

- [ ] **Step 3: Implement `trainInCopy`**

Append to `gradle/packaging-support.gradle`, after `ext.unsealJar = { … }`:

```groovy
// Runs train on a copy of image at copy, made by copier (which must keep modification times, as ditto does: the JVM
// checks them for the class-path jars), and deletes the copy afterwards, also when copier or train throws. The AOT
// cache then records class-path locations that exist on no machine, so the build machine runs the trained image in
// the condition every user's machine is in (spec 2026-10-14 section 3.3). A copy left by an earlier, killed training is
// replaced. Returns what train returns; train receives the copy.
ext.trainInCopy = { File image, File copy, Closure copier, Closure train ->
    copy.deleteDir()
    copy.parentFile.mkdirs()
    try {
        copier(image, copy)
        List<String> changed = modificationTimeMismatches(image, copy)
        if (changed) {
            throw new GradleException("AOT training: the copy of ${image.name} has other modification times for " +
                "${changed.take(10)}; the JVM would refuse the cache")
        }
        return train(copy)
    } finally {
        copy.deleteDir()
    }
}
```

- [ ] **Step 4: Run the fixture test to verify it passes**

Run: `./gradlew :jasper-app:verifyPackagingSupport`
Expected: PASS, with `Verified training in a throwaway copy`.

- [ ] **Step 5: Train in the copy**

In `gradle/packaging.gradle`, below `def aotTrainingDirectory = packagingRoot.map { it.dir('aot-training') }`, add:

```groovy
    // Beside aotTrainingDirectory, which runAotTraining clears: the image copy that training launches (trainInCopy).
    def aotTrainingImageDirectory = packagingRoot.map { it.dir('aot-training-image') }
```

and in `trainAotCache`'s `doLast` replace the line
`File cache = runAotTraining(new File(image, 'Contents/MacOS/Jasper'), aotTrainingDirectory.get().asFile, 90, 300)`
with:

```groovy
            File cache = trainInCopy(image, new File(aotTrainingImageDirectory.get().asFile, 'Jasper.app'),
                { File from, File to -> executeNative(['/usr/bin/ditto', from.absolutePath, to.absolutePath], 'copying the image for AOT training') },
                { File copy -> runAotTraining(new File(copy, 'Contents/MacOS/Jasper'), aotTrainingDirectory.get().asFile, 90, 300) })
```

The following lines stay: they copy `cache` (in `aot-training`, outside the deleted copy) into the image, add the
cache option to the image's `Jasper.cfg`, re-sign, and run `verifyAotCache` from the image, now with the training
location gone.

- [ ] **Step 6: Check the script still configures**

Run: `./gradlew :jasper-app:tasks --all | grep -E 'trainAotCache|packageDist'`
Expected: both tasks listed (configuration succeeds). Do not run them: they launch Jasper.

- [ ] **Step 7: Commit**

```bash
git add gradle/packaging-support.gradle gradle/packaging-support-test.gradle gradle/packaging.gradle
git commit -F - <<'EOF'
Train the AOT cache in a throwaway copy of the image

The cache recorded the build image's paths, and JBR 25 resolves cached
classes' code sources from them, so the build machine never saw what
every user's Mac sees. Training now runs in a ditto copy that is deleted
before verifyAotCache, which then checks the cache in that condition.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

---

### Task 4: Documentation, full check and the user's GUI check

**Files:**
- Modify: `docs/app-maintenance.md` (the "The AOT cache." bullet)
- Modify: `docs/STATUS.md` (new entry at the top)
- Modify: `docs/superpowers/plans/2026-10-14-jasper-startup-recovery-1-shipped-cache.md` (status line)

- [ ] **Step 1: Update the maintenance recipe**

In `docs/app-maintenance.md`, replace the bullet that starts `- **The AOT cache.** It is trained per build` with:

```markdown
- **The AOT cache.** It is trained per build, so it follows class-path changes automatically; never edit a jar inside a
  trained image, rebuild it. `trainAotCache` trains in a `ditto` copy of the image that it deletes before
  `verifyAotCache`, because JBR 25 gives cached classes the code source of the path they were trained at
  (JDK-8376576): the build machine then runs the trained image as every user's Mac does. For the same reason
  `stagePackage` removes package sealing from class-path jars (`verifyPackage` rejects a sealed one) and Jasper finds
  its own files with `CodeLocation`, never a code source. Training opens a window: agents ask the user before
  `packageDist`, `trainAotCache` or `-Pjasper.aot=true`.
```

- [ ] **Step 2: Run the full check**

Run: `./gradlew check --continue`
Expected: BUILD SUCCESSFUL. Read the totals from the XML (AGENTS.md):

```bash
python3 - <<'PY'
import pathlib, xml.etree.ElementTree as ET
t = f = e = s = 0
for p in pathlib.Path('.').glob('*/build/test-results/test/*.xml'):
    r = ET.parse(p).getroot()
    t += int(r.get('tests', 0)); f += int(r.get('failures', 0)); e += int(r.get('errors', 0)); s += int(r.get('skipped', 0))
for p in pathlib.Path('plugins').glob('*/build/test-results/test/*.xml'):
    r = ET.parse(p).getroot()
    t += int(r.get('tests', 0)); f += int(r.get('failures', 0)); e += int(r.get('errors', 0)); s += int(r.get('skipped', 0))
print(f"{t} tests, {f} failures, {e} errors, {s} skipped")
PY
```

Also run the headless image check once more: `./gradlew :jasper-app:verifyPackage` (PASS).

- [ ] **Step 3: Record the status**

Add at the top of `docs/STATUS.md`, below the `# Jasper — Status and Handoff` heading, using the totals from Step 2:

```markdown
**Start-up recovery plan 1 (the shipped cache) complete — 2026-10-14** (`claude/startup-recovery`, worktree
`.claude/worktrees/startup-recovery`; spec `docs/superpowers/specs/2026-10-14-jasper-startup-recovery-design.md`).
The installed Jasper.app would not start ("sealing violation: can't seal package com.jetbrains"): JBR 25 gives
classes from the AOT cache the code source of the path the cache was trained at (JDK-8376576), so the cache only
worked while the repository's build image existed, and the installed app read its bundled plugins from it.
`stagePackage` now removes package sealing (only `jbr-api` declared it; the About notices record the change) and
`verifyPackage` rejects a sealed jar; `CodeLocation` finds Jasper's own jar from its class resource for plugin
discovery, handoff and the replaced-on-disk check (`CodeSourceScanTest` keeps code sources out of the class-path
modules); `trainAotCache` trains in a `ditto` copy deleted before `verifyAotCache`. `./gradlew check --continue`:
**<T> tests, <F> failures, <E> errors, <S> skipped** (replace with Step 2's totals). No GUI launched. GUI check for
the user: `./gradlew :jasper-app:packageDist`, install the DMG, quit Jasper completely, move the repository's
`jasper-app/build/packaging/image` aside and start Jasper: the start-up line says `AOT cache used` and the bundled
plugins are `ACTIVE` with no "Plugin discovery: …/build/…" warning. Plans 2 (watcher) and 3 (application) follow.
```

Replace `<T>`, `<F>`, `<E>` and `<S>` with the numbers printed in Step 2 before committing; the entry must not contain
angle-bracket placeholders.

- [ ] **Step 4: Mark the plan complete**

In this plan, replace the status line with:

```markdown
Status: **Complete on 2026-10-14** on `claude/startup-recovery` (spec approved by the user the same day).
```

- [ ] **Step 5: Commit**

```bash
git add docs/app-maintenance.md docs/STATUS.md docs/superpowers/plans/2026-10-14-jasper-startup-recovery-1-shipped-cache.md
git commit -F - <<'EOF'
Document the shipped-cache fix

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

- [ ] **Step 6: Hand the GUI check to the user**

Do not run `packageDist` or launch Jasper. Report to the user: the branch and commits, the totals, and the GUI check
from the STATUS entry; ask before any `packageDist` run (memory `jasper-gui-launch-per-batch`).
