# File Server FS-2: FTP/FTPS and TLS — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add FTP with explicit FTPS and HTTPS to the File Server plugin on a shared self-signed certificate, with the editor, panel and palette support they need, and close the four items parked by FS-1's final review.

**Architecture:** A new `engine.tls.TlsMaterial` creates and keeps the certificate (BouncyCastle, PKCS#12 in `data/tls/`). `HttpEngine` binds after its setup and serves HTTPS through the JDK `HttpsServer`. A new `engine.ftp` package adapts Apache FtpServer 1.2.1 to `ShareRoot`. The FTP view and files go through the share, a user manager goes through `ResolvedLogin` and `LoginThrottle`, an ftplet reports events, enforces Require TLS and commits uploads only after a 226 reply, and an `SslConfiguration` wraps `TlsMaterial`. Settings, the editor, the panel and the plugin gain the FTP and TLS options.

**Tech Stack:** Java 25 (JBR), Gradle wrapper, Swing, Apache FtpServer 1.2.1 (`org.apache.ftpserver:ftpserver-core`, which brings MINA 2 and slf4j), BouncyCastle `bcpkix-jdk18on` 1.85 with `bcprov-jdk18on` 1.85.2, the JDK `com.sun.net.httpserver.HttpsServer`, and commons-net 3.13.0 (tests only).

**Spec:** `docs/superpowers/specs/2026-09-30-jasper-file-server-design.md` (approved 2026-09-30). This plan implements its "FS-2" delivery, plus FS-1's parked items listed in `docs/STATUS.md`.

## Global Constraints

- Work on branch `claude/file-server-fs2` in `.worktrees/file-server-fs2`, created from `main`. Never commit on `main`. Verify the branch (`git branch --show-current`) before every commit, because other sessions switch branches in the main checkout. Never use `git stash` (the stash is shared between sessions).
- End every commit message with `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.
- Use `./gradlew` only. Tests are headless and bind only to `127.0.0.1`. Never launch the GUI; GUI checks go to the user.
- The plugin compiles against the SDK and, optionally, `dev.jasper.vault.api`. Vendor types stay inside `engine.*`: FtpServer and MINA types only in `engine.ftp`, BouncyCastle only in `engine.tls`.
- File Server becomes version `0.2.0` with `sdk = ">=0.11.0, <0.12"` (unchanged).
- Defaults, as in FS-1: read-only, bind `loopback`, FTP port 2121, passive ports `50000-50100` (the `passive_ports` setting) unless the server sets its own range. A new FTP server's login is a Vault account when the Vault is installed, otherwise a session password.
- TLS material: EC P-256, valid for 10 years, subject `CN=Jasper File Server`, SANs `localhost`, `127.0.0.1` and this computer's host name. Stored as `data/tls/server.p12` (PKCS#12, alias `jasper-file-server`) with its random password in `data/tls/password`, both owner-only where the file system supports POSIX permissions. The fingerprint is SHA-256 in uppercase hex pairs joined by `:`.
- FTP: explicit FTPS only (`AUTH TLS`), restricted to TLS 1.2 (FtpServer's default); no implicit FTPS. Require TLS refuses `USER` and `PASS` before `AUTH TLS` (530) and refuses data commands without `PROT P` (521).
- Every rail panel docks on the left. File Server's panel already uses `Anchor.LEFT`; keep it there.
- Source hygiene: no raw control, private-use or unpaired-surrogate characters in Java source (AGENTS.md). Labels that show user or client text set `html.disable` before their text.
- Threading: the registry, panels and dialogs live on the UI thread. Engines start and stop on `context.background()`. `TlsMaterial` does file I/O and must not be called on the UI thread, except `cachedFingerprint()`.

## Decisions made while planning (record in `docs/STATUS.md` and this plan's banner)

1. **FTP paths.** The FTP view resolves paths against the working directory and stops `..` at the share's root, as every FTP server's chroot does, so `CWD ..` at the top stays at `/`. `ShareRoot` still validates every name and link, and still refuses any escape.
2. **FTP uploads.** A `STOR` or `STOU` writes to `ShareRoot.upload` and commits only when the command's reply is 226, checked in the ftplet's `afterCommand`. Any other reply, a disconnect, or a new transfer abandons the upload.
   - FTP cannot tell a client that closed its data connection early from a finished one; that case commits whatever arrived. The temp file still prevents interleaving and in-place truncation.
   - A non-zero offset is accepted only at the file's current end (`APPE`, or `REST n` with n the size, then `STOR`). It appends in place, without the temporary file. Any other offset, such as resuming part-way through, is refused with 551.
3. **Active mode** (`PORT`/`EPRT`) stays enabled, because some command-line clients use nothing else. FtpServer's active and passive IP checks are both turned on, so data connections only go to, and come from, the client's own address. Passive mode uses the control connection's own address, which is FtpServer's default when no passive address is set.
4. **Plain FTP warning.** A new warning, "Plain FTP sends the password unencrypted", matches the existing HTTP one. The spec named only HTTP.
5. **TLS location.** `TlsMaterial` lives in its own package, `engine.tls`, so BouncyCastle stays in one place.
6. **Panel fixes.** The panel's empty-state hint wraps instead of being cut off, and the split divider is hidden while nothing is selected. Both came from the user's GUI check on 2026-09-30.
7. **FS-1 parked items fixed here:**
   - `HttpEngine` binds only after its setup succeeds.
   - Two event-ordering test races.
   - Replacing a file through `ShareRoot.upload` keeps the replaced file's POSIX permissions.
   - The overstated `docs/STATUS.md` line about `HttpEngine.start`.
8. **Setting name.** The spec's `passive-ports` becomes `passive_ports`, matching the plugin's other keys (`confirm_quit`, `activity_entries`).
9. **Fingerprint dialog.** Show Fingerprints… reuses `ConfirmPanel`: the fingerprint, with "Regenerate Certificate…" and "Close". Regenerate asks again before it replaces the certificate. The certificate is shared, so the dialog is the same for every TLS server. SFTP's host key joins it in FS-3.
10. **FTP listings show every shareable name**, dot files included, as HTTP's do (`isHidden()` is false). FtpServer's native file system would hide them.
11. **Throttled FTP logins are refused, not delayed.** As with HTTP's 429, an address that must wait gets 530 without its password being checked. FtpServer's own 500 ms pause after a failure and its three-strike disconnect stay.

## File map

- Modify `plugins/fileserver/build.gradle.kts`, `src/main/resources/plugin.toml`, `settings.toml`
- `share/ShareRoot.java` (permissions kept on commit)
- `engine/tls/{TlsMaterial,package-info}.java` (new)
- `engine/http/HttpEngine.java` (bind after setup, HTTPS)
- `engine/ftp/{FtpPaths,ShareView,ShareFile,ShareFileSystem,ShareUserManager,EventFtplet,TlsConfiguration,FtpEngine,package-info}.java` (new)
- `runtime/Engines.java`
- `FileServerSettings.java`, `FileServerPlugin.java`
- `ui/ServerRows.java`, `ui/ServerEditor.java`, `ui/FileServersPanel.java`
- Tests mirror each package. Update `engine/http/HttpEngineWriteTest.java`, `runtime/EnginesTest.java`, `ui/FileServersPanelTest.java`, `ui/ServerEditorTest.java`, `ui/ServerRowsTest.java` and `FileServerPluginTest.java`.
- `jasper-app/src/test/java/dev/jasper/app/plugins/PluginZipsTest.java`
- Docs: `docs/file-server.md`, `docs/STATUS.md`

---

### Task 1: FS-1 follow-ups — kept permissions, steady tests, a readable empty panel

**Files:**
- Modify: `plugins/fileserver/src/main/java/dev/jasper/fileserver/share/ShareRoot.java` (`Upload.commit`)
- Modify: `plugins/fileserver/src/main/java/dev/jasper/fileserver/ui/FileServersPanel.java` (`empty`, the split divider)
- Test: `plugins/fileserver/src/test/java/dev/jasper/fileserver/share/ShareRootTest.java`, `engine/http/HttpEngineWriteTest.java`, `ui/FileServersPanelTest.java`

**Interfaces:**
- Produces:
  - `ShareRoot.Upload.commit()` copies the replaced file's POSIX permissions onto the new file. This is best effort: it is skipped where POSIX attributes are unsupported or the target is new.
  - `FileServersPanel.empty` becomes a wrapping, non-editable `JTextArea` (same text).
  - New package-private `FileServersPanel.split` (`JSplitPane`), whose divider size is 0 while no server is selected.

- [ ] **Step 1: Write the failing tests**

In `ShareRootTest.java`, add `import java.nio.file.attribute.PosixFilePermissions;` and:

```java
    @Test void replacingAFileKeepsItsPermissions() throws IOException {
        if (!Files.getFileStore(shared).supportsFileAttributeView("posix")) abort("no POSIX permissions here");
        Path target = shared.resolve("docs/a.txt");
        Files.setPosixFilePermissions(target, PosixFilePermissions.fromString("rwxr-x---"));
        var share = new ShareRoot(shared, false);
        try (var upload = share.upload("docs/a.txt")) {
            upload.stream().write("new".getBytes(StandardCharsets.UTF_8));
            upload.commit();
        }
        assertThat(target).hasContent("new");
        assertThat(PosixFilePermissions.toString(Files.getPosixFilePermissions(target))).isEqualTo("rwxr-x---");
    }
```

In `FileServersPanelTest.java`, add:

```java
    @Test void theEmptyHintWrapsAndTheDividerShowsOnlyWithDetails() {
        var panel = panel();
        assertThat(panel.empty.getLineWrap()).isTrue();
        assertThat(panel.empty.getWrapStyleWord()).isTrue();
        assertThat(panel.empty.isEditable()).isFalse();
        assertThat(panel.empty.getText()).isEqualTo("No file servers yet — press + to share a folder");
        assertThat(panel.split.getDividerSize()).as("nothing selected").isZero();
        view("docs", ServerState.STOPPED, false, false, Bind.LOOPBACK, List.of(), Optional.empty());
        panel.setServers(views, Optional.empty());
        assertThat(panel.split.getDividerSize()).isPositive();
    }
```

- [ ] **Step 2: Run them to see them fail**

Run: `./gradlew :jasper-plugin-fileserver:test --tests 'dev.jasper.fileserver.share.ShareRootTest' --tests 'dev.jasper.fileserver.ui.FileServersPanelTest'`
Expected: FAIL. The permissions come back as the process default, `empty` is a `JLabel`, and `split` does not exist.

- [ ] **Step 3: Keep permissions on commit**

In `ShareRoot.Upload`, call a new helper between closing the stream and the move:

```java
        /** Closes the stream and moves the temporary file onto the target, replacing it. */
        public void commit() throws IOException {
            if (finished) throw new IllegalStateException("The upload is finished");
            finished = true;
            try {
                out.close();
                keepPermissions();
                try { Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
                catch (AtomicMoveNotSupportedException unsupported) { Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING); }
            } catch (IOException | RuntimeException failure) {
                discard();
                throw failure;
            }
        }

        /** A replaced file keeps its permissions; the temporary file was created with the process's defaults. Best effort. */
        private void keepPermissions() {
            try {
                if (!Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)) return;
                Files.setPosixFilePermissions(temp, Files.getPosixFilePermissions(target, LinkOption.NOFOLLOW_LINKS));
            } catch (UnsupportedOperationException | IOException notPosix) {
                // Windows, or a file system without POSIX permissions: the new file keeps its defaults.
            }
        }
```

- [ ] **Step 4: Make the empty hint wrap and hide the divider without details**

In `FileServersPanel.java`:

1. Replace the `empty` field with:

```java
    final JTextArea empty = note("No file servers yet — press + to share a folder");
```

2. Add the fields `final JSplitPane split;` and `private final int dividerSize;`.

3. In the constructor, replace `var split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, top, details);` with:

```java
        split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, top, details);
        dividerSize = split.getDividerSize();
```

4. In `refreshCard()`, right after `details.setVisible(chosen.isPresent());`, add:

```java
        split.setDividerSize(chosen.isPresent() ? dividerSize : 0);
```

`refreshCard()` runs from `rebuild()`, which the constructor calls last, so `split` is assigned before the first call.

5. Add the helper next to `label(...)`:

```java
    /** Wrapping, read-only text that looks like a label; a JTextArea never renders HTML. */
    private static JTextArea note(String text) {
        var note = new JTextArea(text) {
            @Override public void updateUI() {
                super.updateUI();
                setFont(UIManager.getFont("Label.font"));
                Color hint = UIManager.getColor("Label.disabledForeground");
                if (hint != null) setForeground(hint);
                setOpaque(false);
            }
        };
        note.setLineWrap(true);
        note.setWrapStyleWord(true);
        note.setEditable(false);
        note.setFocusable(false);
        return note;
    }
```

- [ ] **Step 5: Remove the two event-ordering races in `HttpEngineWriteTest`**

In `anUploadThatDropsMidBodyLeavesTheOriginalAndNoPartialFile`, the `Transferred` event fires before the request ends and deletes the temporary file. Replace the final listing check (`try (var listing = Files.list(...)) { ... }`) with a poll:

```java
        List<String> names;
        long settled = System.nanoTime() + 5_000_000_000L;
        do {
            try (var listing = Files.list(shared.resolve("docs"))) { names = listing.map(p -> p.getFileName().toString()).sorted().toList(); }
            if (names.equals(List.of("a.txt", "keep.txt"))) break;
            Thread.sleep(10);
        } while (System.nanoTime() < settled);
        assertThat(names).as("the temporary file is gone once the request ends").containsExactly("a.txt", "keep.txt");
```

In `aCancelledDownloadEndsAsStoppedNotAsAnError`, the handler would report an error after the `Transferred` event. Before the final `noneMatch` assertion, add:

```java
        Thread.sleep(300); // an error would be reported after the transfer ends; give it time to (not) arrive
```

- [ ] **Step 6: Run the tests**

Run: `./gradlew :jasper-plugin-fileserver:check`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add plugins/fileserver/src
git commit -m "File Server: replaced files keep their permissions; steadier tests; the empty panel hint wraps

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 2: `TlsMaterial`, the shared certificate

**Files:**
- Modify: `plugins/fileserver/build.gradle.kts` (BouncyCastle)
- Create: `plugins/fileserver/src/main/java/dev/jasper/fileserver/engine/tls/{TlsMaterial,package-info}.java`
- Test: `plugins/fileserver/src/test/java/dev/jasper/fileserver/engine/tls/TlsMaterialTest.java`

**Interfaces:**
- Consumes: `engine.EngineFailure`.
- Produces (`dev.jasper.fileserver.engine.tls.TlsMaterial`):
  - `TlsMaterial(Path directory, Clock clock, Supplier<String> hostName)`
  - `SSLContext context() throws EngineFailure`: creates the certificate on first use.
  - `X509Certificate certificate() throws EngineFailure`: creates it on first use.
  - `Optional<String> fingerprint() throws EngineFailure`: never creates; empty when none exists.
  - `Optional<String> cachedFingerprint()`: no I/O; empty until loaded or created.
  - `void regenerate() throws EngineFailure`
  - `static String fingerprint(X509Certificate)`
  - Failure messages:
    - "The TLS certificate in <dir> can't be read"
    - "Could not create the TLS certificate in <dir>: …"

- [ ] **Step 1: Add the dependency**

In `plugins/fileserver/build.gradle.kts`, extend the header comment ("Bundled: tomlj for servers.toml; BouncyCastle for the TLS certificate.") and add:

```kotlin
    implementation("org.bouncycastle:bcprov-jdk18on:1.85.2")
    implementation("org.bouncycastle:bcpkix-jdk18on:1.85")
```

- [ ] **Step 2: Write the failing tests**

`plugins/fileserver/src/test/java/dev/jasper/fileserver/engine/tls/TlsMaterialTest.java`:

```java
package dev.jasper.fileserver.engine.tls;

import dev.jasper.fileserver.TestClock;
import dev.jasper.fileserver.engine.EngineFailure;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.abort;

class TlsMaterialTest {
    @TempDir Path data;
    final TestClock clock = new TestClock();

    TlsMaterial material() { return new TlsMaterial(data.resolve("tls"), clock, () -> "studio.example"); }

    @Test void createsTheCertificateOnFirstUseAndReloadsIt() throws Exception {
        var tls = material();
        assertThat(tls.fingerprint()).as("nothing yet, and asking does not create one").isEmpty();
        assertThat(tls.cachedFingerprint()).isEmpty();
        assertThat(data.resolve("tls")).doesNotExist();
        assertThat(tls.context().getProtocol()).isEqualTo("TLS");
        X509Certificate certificate = tls.certificate();
        assertThat(certificate.getSubjectX500Principal().getName()).isEqualTo("CN=Jasper File Server");
        assertThat(certificate.getPublicKey().getAlgorithm()).isEqualTo("EC");
        assertThat(certificate.getNotAfter().toInstant()).isAfter(clock.instant().plus(Duration.ofDays(3649)));
        assertThat(certificate.getSubjectAlternativeNames()).extracting(name -> name.get(1))
            .containsExactlyInAnyOrder("localhost", "127.0.0.1", "studio.example");
        String fingerprint = tls.cachedFingerprint().orElseThrow();
        assertThat(fingerprint).matches("([0-9A-F]{2}:){31}[0-9A-F]{2}").isEqualTo(TlsMaterial.fingerprint(certificate));
        assertThat(data.resolve("tls/server.p12")).exists();
        assertThat(data.resolve("tls/password")).exists();
        var again = material();
        assertThat(again.fingerprint()).as("the same certificate is read back").contains(fingerprint);
        assertThat(again.certificate()).isEqualTo(certificate);
    }

    @Test void regeneratingMakesANewCertificate() throws Exception {
        var tls = material();
        String first = TlsMaterial.fingerprint(tls.certificate());
        tls.regenerate();
        assertThat(tls.cachedFingerprint()).isPresent().get().isNotEqualTo(first);
        assertThat(material().fingerprint()).isEqualTo(tls.cachedFingerprint());
    }

    @Test void anUnreadableCertificateSaysSoAndRegeneratingFixesIt() throws Exception {
        Files.createDirectories(data.resolve("tls"));
        Files.writeString(data.resolve("tls/server.p12"), "not a keystore");
        Files.writeString(data.resolve("tls/password"), "secret");
        var tls = material();
        assertThatThrownBy(tls::context).isInstanceOf(EngineFailure.class)
            .hasMessage("The TLS certificate in " + data.resolve("tls") + " can't be read");
        assertThatThrownBy(tls::fingerprint).isInstanceOf(EngineFailure.class);
        tls.regenerate();
        assertThat(tls.context()).isNotNull();
    }

    @Test void theKeyFilesAreOwnerOnly() throws Exception {
        if (!Files.getFileStore(data).supportsFileAttributeView("posix")) abort("no POSIX permissions here");
        material().context();
        for (String file : List.of("server.p12", "password"))
            assertThat(PosixFilePermissions.toString(Files.getPosixFilePermissions(data.resolve("tls").resolve(file)))).as(file).isEqualTo("rw-------");
        assertThat(PosixFilePermissions.toString(Files.getPosixFilePermissions(data.resolve("tls")))).isEqualTo("rwx------");
    }
}
```

- [ ] **Step 3: Run them to see them fail**

Run: `./gradlew :jasper-plugin-fileserver:test --tests 'dev.jasper.fileserver.engine.tls.*'`
Expected: FAIL (the package does not exist).

- [ ] **Step 4: Write `TlsMaterial`**

`engine/tls/package-info.java`:

```java
/**
 * The self-signed certificate HTTPS and FTPS servers present. The only package that names BouncyCastle types.
 * May depend on {@code engine} (for {@code EngineFailure}) and the JDK.
 */
package dev.jasper.fileserver.engine.tls;
```

`engine/tls/TlsMaterial.java`:

```java
package dev.jasper.fileserver.engine.tls;

import dev.jasper.fileserver.engine.EngineFailure;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.security.spec.ECGenParameterSpec;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

/**
 * The self-signed certificate HTTPS and FTPS servers present: an EC P-256 key and a certificate valid for ten years,
 * kept in one folder as {@code server.p12} (PKCS#12) with its random password in {@code password}, both readable by
 * the owner only where the file system allows. It is created on first use, and {@link #regenerate()} replaces it.
 * Running servers keep the context they started with.
 *
 * <p>Thread-safe. Every method does file I/O except {@link #cachedFingerprint()}, so call them off the UI thread.
 */
public final class TlsMaterial {
    static final String ALIAS = "jasper-file-server";
    static final Duration VALIDITY = Duration.ofDays(3650);
    private static final SecureRandom RANDOM = new SecureRandom();
    private final Path directory;
    private final Clock clock;
    private final Supplier<String> hostName;
    private KeyStore store;
    private char[] password;
    private volatile String fingerprint;

    public TlsMaterial(Path directory, Clock clock, Supplier<String> hostName) {
        this.directory = directory;
        this.clock = clock;
        this.hostName = hostName;
    }

    /** A TLS context presenting the certificate, created if there is none yet. */
    public synchronized SSLContext context() throws EngineFailure {
        KeyStore keys = load(true);
        try {
            var managers = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            managers.init(keys, password);
            var context = SSLContext.getInstance("TLS");
            context.init(managers.getKeyManagers(), null, null);
            return context;
        } catch (GeneralSecurityException failure) {
            throw unreadable(failure);
        }
    }

    /** The certificate, created if there is none yet. */
    public synchronized X509Certificate certificate() throws EngineFailure {
        try { return (X509Certificate) load(true).getCertificate(ALIAS); }
        catch (GeneralSecurityException failure) { throw unreadable(failure); }
    }

    /** The existing certificate's fingerprint; empty when none was created yet. Never creates one. */
    public synchronized Optional<String> fingerprint() throws EngineFailure {
        return load(false) == null ? Optional.empty() : Optional.of(fingerprint);
    }

    /** The fingerprint already known in memory, without touching the disk. */
    public Optional<String> cachedFingerprint() { return Optional.ofNullable(fingerprint); }

    /** Replaces the certificate with a new one. */
    public synchronized void regenerate() throws EngineFailure {
        store = null;
        password = null;
        fingerprint = null;
        try {
            Files.deleteIfExists(directory.resolve("server.p12"));
            Files.deleteIfExists(directory.resolve("password"));
        } catch (IOException failure) {
            throw new EngineFailure("Could not remove the TLS certificate in " + directory + ": " + failure.getMessage(), failure);
        }
        create();
    }

    /** SHA-256 of the encoded certificate in uppercase hex pairs joined by colons. */
    public static String fingerprint(X509Certificate certificate) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(certificate.getEncoded());
            return HexFormat.ofDelimiter(":").withUpperCase().formatHex(digest);
        } catch (GeneralSecurityException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private KeyStore load(boolean createIfMissing) throws EngineFailure {
        if (store != null) return store;
        Path keystore = directory.resolve("server.p12"), secret = directory.resolve("password");
        if (Files.exists(keystore) && Files.exists(secret)) {
            try {
                char[] pass = Files.readString(secret, StandardCharsets.UTF_8).strip().toCharArray();
                KeyStore keys = KeyStore.getInstance("PKCS12");
                try (InputStream in = Files.newInputStream(keystore)) { keys.load(in, pass); }
                if (!(keys.getCertificate(ALIAS) instanceof X509Certificate certificate) || !(keys.getKey(ALIAS, pass) instanceof PrivateKey))
                    throw new GeneralSecurityException("The keystore has no key entry " + ALIAS);
                return remember(keys, pass, certificate);
            } catch (IOException | GeneralSecurityException failure) {
                throw unreadable(failure);
            }
        }
        return createIfMissing ? create() : null;
    }

    private KeyStore create() throws EngineFailure {
        try {
            Files.createDirectories(directory);
            ownerOnly(directory, "rwx------");
            var generator = KeyPairGenerator.getInstance("EC");
            generator.initialize(new ECGenParameterSpec("secp256r1"));
            KeyPair pair = generator.generateKeyPair();
            Instant now = clock.instant();
            var subject = new X500Name("CN=Jasper File Server");
            var builder = new JcaX509v3CertificateBuilder(subject, new BigInteger(64, RANDOM).add(BigInteger.ONE),
                Date.from(now.minus(Duration.ofDays(1))), Date.from(now.plus(VALIDITY)), subject, pair.getPublic());
            var names = new ArrayList<>(List.of(new GeneralName(GeneralName.dNSName, "localhost"), new GeneralName(GeneralName.iPAddress, "127.0.0.1")));
            String host = hostName.get();
            if (host != null && !host.isBlank() && !host.equalsIgnoreCase("localhost")) names.add(new GeneralName(GeneralName.dNSName, host));
            builder.addExtension(Extension.subjectAlternativeName, false, new GeneralNames(names.toArray(GeneralName[]::new)));
            builder.addExtension(Extension.basicConstraints, true, new BasicConstraints(false));
            X509Certificate certificate = new JcaX509CertificateConverter()
                .getCertificate(builder.build(new JcaContentSignerBuilder("SHA256withECDSA").build(pair.getPrivate())));
            byte[] random = new byte[24];
            RANDOM.nextBytes(random);
            char[] pass = HexFormat.of().formatHex(random).toCharArray();
            KeyStore keys = KeyStore.getInstance("PKCS12");
            keys.load(null, null);
            keys.setKeyEntry(ALIAS, pair.getPrivate(), pass, new Certificate[]{certificate});
            write(directory.resolve("password"), out -> out.write(new String(pass).getBytes(StandardCharsets.UTF_8)));
            write(directory.resolve("server.p12"), out -> keys.store(out, pass));
            return remember(keys, pass, certificate);
        } catch (IOException | GeneralSecurityException | OperatorCreationException failure) {
            throw new EngineFailure("Could not create the TLS certificate in " + directory + ": " + failure.getMessage(), failure);
        }
    }

    private KeyStore remember(KeyStore keys, char[] pass, X509Certificate certificate) throws CertificateEncodingException {
        store = keys;
        password = pass;
        fingerprint = fingerprint(certificate);
        return keys;
    }

    @FunctionalInterface private interface Writer { void write(OutputStream out) throws IOException, GeneralSecurityException; }

    /** Writes a file owner-only, through a temporary sibling moved into place. */
    private static void write(Path file, Writer content) throws IOException, GeneralSecurityException {
        Path temp = file.resolveSibling(file.getFileName() + ".tmp");
        Files.deleteIfExists(temp);
        Files.createFile(temp);
        ownerOnly(temp, "rw-------");
        try (OutputStream out = Files.newOutputStream(temp)) { content.write(out); }
        try { Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
        catch (AtomicMoveNotSupportedException unsupported) { Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING); }
    }

    private static void ownerOnly(Path path, String permissions) {
        try { Files.setPosixFilePermissions(path, PosixFilePermissions.fromString(permissions)); }
        catch (UnsupportedOperationException | IOException notPosix) { /* Windows: the user's profile folder protects it */ }
    }

    private EngineFailure unreadable(Exception failure) {
        return new EngineFailure("The TLS certificate in " + directory + " can't be read", failure);
    }
}
```

- [ ] **Step 5: Run the tests**

Run: `./gradlew :jasper-plugin-fileserver:check`
Expected: PASS. `verifyPluginArchitecture` accepts BouncyCastle because the plugin bundles it.

- [ ] **Step 6: Commit**

```bash
git add plugins/fileserver
git commit -m "File Server: a self-signed TLS certificate for HTTPS and FTPS

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 3: HTTPS, and the HTTP engine binds after its setup

**Files:**
- Modify: `plugins/fileserver/src/main/java/dev/jasper/fileserver/engine/http/HttpEngine.java`
- Modify: `plugins/fileserver/src/main/java/dev/jasper/fileserver/runtime/Engines.java`
- Modify: `plugins/fileserver/src/main/java/dev/jasper/fileserver/FileServerPlugin.java` (creates the `TlsMaterial`)
- Test: `plugins/fileserver/src/test/java/dev/jasper/fileserver/engine/http/HttpsEngineTest.java` (new), `runtime/EnginesTest.java`

**Interfaces:**
- Consumes: `TlsMaterial(Path, Clock, Supplier<String>)` and `TlsMaterial.context()` (Task 2).
- Produces:
  - `HttpEngine(ServerDefinition, ShareRoot, ResolvedLogin, ServerEvents, LoginThrottle, Supplier<List<ServerAddresses.Nic>>, TlsMaterial tls)`. `tls` may be null only when the definition has `tls == false`. The FS-1 six-argument constructor stays and passes null.
  - `Engines.factory(LoginThrottle throttle, Supplier<List<ServerAddresses.Nic>> nics, TlsMaterial tls)` replaces the two-argument factory.
  - `FileServerPlugin.tls()` (package-private) returns the plugin's `TlsMaterial`, which lives in `context.dataDirectory().resolve("tls")`.
  - `FileServerPlugin.hostName()` (package-private static) returns this computer's host name, or `""` when unknown.

- [ ] **Step 1: Write the failing tests**

`plugins/fileserver/src/test/java/dev/jasper/fileserver/engine/http/HttpsEngineTest.java`:

```java
package dev.jasper.fileserver.engine.http;

import dev.jasper.fileserver.Ports;
import dev.jasper.fileserver.TestClock;
import dev.jasper.fileserver.auth.ResolvedLogin;
import dev.jasper.fileserver.definition.Protocol;
import dev.jasper.fileserver.definition.ServerDefinition;
import dev.jasper.fileserver.engine.EngineFailure;
import dev.jasper.fileserver.engine.LoginThrottle;
import dev.jasper.fileserver.engine.ServerEvents;
import dev.jasper.fileserver.engine.tls.TlsMaterial;
import dev.jasper.fileserver.share.ShareRoot;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.time.Clock;
import java.util.List;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLException;
import javax.net.ssl.TrustManagerFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HttpsEngineTest {
    @TempDir Path temp;
    HttpEngine engine;

    @AfterEach void stop() { if (engine != null) engine.stop(); }

    ServerDefinition definition(Path shared, int port, boolean tls) {
        var base = ServerDefinition.create("secure", Protocol.HTTP, shared);
        return new ServerDefinition(base.id(), base.name(), Protocol.HTTP, shared, base.bind(), port, base.passiveRange(), false,
            base.login(), List.of(), tls, false, false);
    }

    static HttpClient trusting(TlsMaterial tls) throws Exception {
        KeyStore trusted = KeyStore.getInstance("PKCS12");
        trusted.load(null, null);
        trusted.setCertificateEntry("server", tls.certificate());
        var managers = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        managers.init(trusted);
        var context = SSLContext.getInstance("TLS");
        context.init(null, managers.getTrustManagers(), null);
        return HttpClient.newBuilder().sslContext(context).build();
    }

    @Test void servesOverHttpsWithTheSharedCertificate() throws Exception {
        Path shared = Files.createDirectories(temp.resolve("shared"));
        Files.writeString(shared.resolve("a.txt"), "secret-free");
        var tls = new TlsMaterial(temp.resolve("tls"), Clock.systemUTC(), () -> "test-host");
        int port = Ports.free();
        engine = new HttpEngine(definition(shared, port, true), new ShareRoot(shared, true), ResolvedLogin.anonymous(),
            new ServerEvents(Clock.systemUTC(), e -> { }), new LoginThrottle(new TestClock()), List::of, tls);
        engine.start();
        assertThat(engine.addresses()).containsExactly(URI.create("https://127.0.0.1:" + port + "/"));
        var response = trusting(tls).send(HttpRequest.newBuilder(URI.create("https://127.0.0.1:" + port + "/a.txt")).build(),
            HttpResponse.BodyHandlers.ofString());
        assertThat(response.body()).isEqualTo("secret-free");
        assertThatThrownBy(() -> HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create("https://127.0.0.1:" + port + "/a.txt")).build(),
            HttpResponse.BodyHandlers.ofString())).as("an untrusted certificate is refused by default").isInstanceOf(SSLException.class);
    }

    @Test void aServerThatFailsBeforeBindingLeavesThePortFree() throws Exception {
        Path shared = Files.createDirectories(temp.resolve("shared"));
        Files.createDirectories(temp.resolve("tls"));
        Files.writeString(temp.resolve("tls/server.p12"), "broken");
        Files.writeString(temp.resolve("tls/password"), "broken");
        var tls = new TlsMaterial(temp.resolve("tls"), Clock.systemUTC(), () -> "");
        int port = Ports.free();
        engine = new HttpEngine(definition(shared, port, true), new ShareRoot(shared, true), ResolvedLogin.anonymous(),
            new ServerEvents(Clock.systemUTC(), e -> { }), new LoginThrottle(new TestClock()), List::of, tls);
        assertThatThrownBy(engine::start).isInstanceOf(EngineFailure.class)
            .hasMessage("The TLS certificate in " + temp.resolve("tls") + " can't be read");
        try (var socket = new ServerSocket()) {
            socket.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), port));
            assertThat(socket.isBound()).as("the port was never taken").isTrue();
        }
        assertThat(engine.addresses()).isEmpty();
    }

    @Test void aTlsDefinitionNeedsTheMaterial() throws Exception {
        Path shared = Files.createDirectories(temp.resolve("shared"));
        engine = new HttpEngine(definition(shared, Ports.free(), true), new ShareRoot(shared, true), ResolvedLogin.anonymous(),
            new ServerEvents(Clock.systemUTC(), e -> { }), new LoginThrottle(new TestClock()), List::of);
        assertThatThrownBy(engine::start).isInstanceOf(IllegalStateException.class).hasMessage("HTTPS needs the TLS material");
    }
}
```

In `EnginesTest.java`, replace the test with:

```java
    @Test void theFactoryBuildsHttpEngines(@TempDir Path folder) throws Exception {
        var factory = Engines.factory(new LoginThrottle(new TestClock()), List::of, new TlsMaterial(folder.resolve("tls"), new TestClock(), () -> ""));
        var events = new ServerEvents(new TestClock(), e -> { });
        var share = new ShareRoot(folder, true);
        assertThat(Engines.AVAILABLE).containsExactly(Protocol.HTTP);
        assertThat(factory.create(ServerDefinition.create("h", Protocol.HTTP, folder), share, ResolvedLogin.anonymous(), events)).isInstanceOf(HttpEngine.class);
        assertThatThrownBy(() -> factory.create(ServerDefinition.create("s", Protocol.SFTP, folder), share, ResolvedLogin.anonymous(), events))
            .isInstanceOf(EngineFailure.class).hasMessage("SFTP servers are not available in this version of File Server");
    }
```

and add `import dev.jasper.fileserver.engine.tls.TlsMaterial;`.

- [ ] **Step 2: Run them to see them fail**

Run: `./gradlew :jasper-plugin-fileserver:test --tests 'dev.jasper.fileserver.engine.http.HttpsEngineTest' --tests 'dev.jasper.fileserver.runtime.EnginesTest'`
Expected: compilation fails, because there is no seven-argument `HttpEngine` constructor and no three-argument `Engines.factory`.

- [ ] **Step 3: Make `HttpEngine` bind last and serve HTTPS**

Replace the fields, constructor and `start()` of `HttpEngine.java` (keep `stop()`, `addresses()` and `port()`), and add the imports `com.sun.net.httpserver.HttpsConfigurator`, `com.sun.net.httpserver.HttpsServer` and `dev.jasper.fileserver.engine.tls.TlsMaterial`. Update the class comment.

```java
/**
 * An HTTP or HTTPS server for one share on the JDK's HTTP server, with at most 16 requests handled at once. The
 * server is set up unbound and binds last, so a failed setup never holds the port.
 */
public final class HttpEngine implements ServerEngine {
    static final int WORKERS = 16;
    private final ServerDefinition definition;
    private final ShareRoot share;
    private final ResolvedLogin login;
    private final ServerEvents events;
    private final LoginThrottle throttle;
    private final Supplier<List<ServerAddresses.Nic>> nics;
    private final TlsMaterial tls;
    private HttpServer server;
    private ExecutorService workers;
    private int port;

    public HttpEngine(ServerDefinition definition, ShareRoot share, ResolvedLogin login, ServerEvents events, LoginThrottle throttle,
                      Supplier<List<ServerAddresses.Nic>> nics) {
        this(definition, share, login, events, throttle, nics, null);
    }

    /** {@code tls} may be null when the definition does not use TLS. */
    public HttpEngine(ServerDefinition definition, ShareRoot share, ResolvedLogin login, ServerEvents events, LoginThrottle throttle,
                      Supplier<List<ServerAddresses.Nic>> nics, TlsMaterial tls) {
        this.definition = definition; this.share = share; this.login = login; this.events = events; this.throttle = throttle;
        this.nics = nics; this.tls = tls;
    }

    @Override public synchronized void start() throws EngineFailure {
        if (server != null) return;
        if (definition.tls() && tls == null) throw new IllegalStateException("HTTPS needs the TLS material");
        InetAddress address = ServerAddresses.bindAddress(definition.bind(), nics.get());
        HttpServer created;
        try {
            if (definition.tls()) {
                HttpsServer secure = HttpsServer.create();
                secure.setHttpsConfigurator(new HttpsConfigurator(tls.context()));
                created = secure;
            } else {
                created = HttpServer.create();
            }
        } catch (IOException failure) {
            throw new EngineFailure("Could not create the HTTP server: " + failure.getMessage(), failure);
        }
        ExecutorService pool = Executors.newFixedThreadPool(WORKERS, Thread.ofPlatform().daemon().name("file-server-http-", 0).factory());
        try {
            HostCheck hosts = HostCheck.forBind(definition.bind());
            HttpContext context = created.createContext("/", new FileHandler(share, events, hosts));
            if (!login.isAnonymous()) context.setAuthenticator(new ThrottledAuthenticator(definition.name(), login, throttle, events, hosts));
            created.setExecutor(pool);
            try { created.bind(new InetSocketAddress(address, definition.port()), 64); }
            catch (IOException failure) { throw EngineFailure.bind(failure, definition.port(), ServerAddresses.describe(address), definition.protocol()); }
            created.start();
        } catch (EngineFailure | RuntimeException | Error failure) {
            created.stop(0);
            pool.shutdownNow();
            throw failure;
        }
        server = created;
        workers = pool;
        port = created.getAddress().getPort();
    }
```

`tls.context()` throws `EngineFailure` before anything is bound or any thread is created, so it needs no clean-up.

- [ ] **Step 4: Pass the material through `Engines` and the plugin**

`runtime/Engines.java`:

```java
package dev.jasper.fileserver.runtime;

import dev.jasper.fileserver.definition.Protocol;
import dev.jasper.fileserver.engine.EngineFailure;
import dev.jasper.fileserver.engine.LoginThrottle;
import dev.jasper.fileserver.engine.ServerAddresses;
import dev.jasper.fileserver.engine.http.HttpEngine;
import dev.jasper.fileserver.engine.tls.TlsMaterial;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

/** Which protocols this version serves, and the factory that builds their engines. */
public final class Engines {
    public static final Set<Protocol> AVAILABLE = Set.of(Protocol.HTTP);

    private Engines() { }

    public static ServerRegistry.EngineFactory factory(LoginThrottle throttle, Supplier<List<ServerAddresses.Nic>> nics, TlsMaterial tls) {
        return (definition, share, login, events) -> switch (definition.protocol()) {
            case HTTP -> new HttpEngine(definition, share, login, events, throttle, nics, tls);
            case FTP, SFTP, TFTP -> throw new EngineFailure(definition.protocol().label() + " servers are not available in this version of File Server");
        };
    }
}
```

`runtime/package-info.java` already allows `engine` and its sub-packages, so `engine.tls` needs no change there.

In `FileServerPlugin.java`:
- Add the field `private TlsMaterial tls;`.
- Add the imports `dev.jasper.fileserver.engine.tls.TlsMaterial` and `java.net.UnknownHostException`.
- In `start`, before the registry is built, add:

```java
        tls = new TlsMaterial(context.dataDirectory().resolve("tls"), Clock.systemUTC(), FileServerPlugin::hostName);
```

- Change the factory argument to `Engines.factory(new LoginThrottle(Clock.systemUTC()), environment.nics(), tls)`.
- Add the following:

```java
    TlsMaterial tls() { return tls; }

    /** This computer's name for the certificate; empty when the system will not say. Slow on some networks, so call it off the UI thread. */
    static String hostName() {
        try { return InetAddress.getLocalHost().getHostName(); }
        catch (UnknownHostException | SecurityException unknown) { return ""; }
    }
```

`TlsMaterial` calls the supplier only when it creates a certificate, which happens on an engine's start, on `context.background()`.

- [ ] **Step 5: Run the tests**

Run: `./gradlew :jasper-plugin-fileserver:check`
Expected: PASS. The FS-1 HTTP tests still use the six-argument constructor.

- [ ] **Step 6: Commit**

```bash
git add plugins/fileserver
git commit -m "File Server: HTTPS on the shared certificate; the HTTP engine binds after its setup

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 4: The FTP view of a share

**Files:**
- Modify: `plugins/fileserver/build.gradle.kts` (Apache FtpServer, slf4j to JUL, commons-net for tests)
- Create: `plugins/fileserver/src/main/java/dev/jasper/fileserver/engine/ftp/{package-info,FtpPaths,ShareView,ShareFile,ShareUser,ShareFileSystem}.java`
- Test: `plugins/fileserver/src/test/java/dev/jasper/fileserver/engine/ftp/{FtpPathsTest,ShareViewTest}.java`

**Interfaces:**
- Consumes: `ShareRoot` (`attributes`, `list`, `openRead`, `openWrite`, `upload`, `mkdir`, `delete`, `rename`, `readOnly`, static `segments`), `ShareEntry`, `ServerEvents` (`transfer`, `changed`, `error`), `ServerEvent.Direction`.
- Produces (all package-private in `dev.jasper.fileserver.engine.ftp`):
  - `FtpPaths.absolute(String workingDirectory, String argument) throws PathRefusedException` returns an absolute path such as `/docs/a.txt`, with `..` stopping at `/`.
  - `FtpPaths.relative(String absolute)` returns the share's path, without the leading slash. `FtpPaths.name(String absolute)` returns the last name, or `/` for the root.
  - `ShareView(ShareRoot share, ServerEvents events, String remote) implements FileSystemView`, plus `share()`, `events()`, `remote()`, `track(ServerEvents.Transfer, ShareRoot.Upload /*nullable*/, Closeable stream, String path)` and `finish(boolean success)`.
  - `ShareFile(ShareView view, String path, ShareEntry known /*nullable*/) implements FtpFile`.
  - `ShareUser(String name, String remote, boolean writable) extends BaseUser`, plus `remote()`.
  - `ShareFileSystem(ShareRoot share, ServerEvents events) implements FileSystemFactory`.

- [ ] **Step 1: Add the dependencies**

In `plugins/fileserver/build.gradle.kts`, extend the header comment to read "Bundled: tomlj for servers.toml; Apache FtpServer (with MINA, and slf4j routed to JUL) for FTP; BouncyCastle for the TLS certificate." Add these lines:

```kotlin
    implementation("org.apache.ftpserver:ftpserver-core:1.2.1")
    implementation("org.slf4j:slf4j-jdk14:2.0.13")
    testImplementation("commons-net:commons-net:3.13.0")
```

`ftpserver-core`'s Spring dependency is optional, so it is not pulled in. Run `./gradlew :jasper-plugin-fileserver:dependencies --configuration runtimeClasspath` and check that the classpath has `ftpserver-core`, `ftplet-api`, `mina-core` and `slf4j-api`/`slf4j-jdk14` 2.0.13, and no Spring.

- [ ] **Step 2: Write the failing tests**

`plugins/fileserver/src/test/java/dev/jasper/fileserver/engine/ftp/FtpPathsTest.java`:

```java
package dev.jasper.fileserver.engine.ftp;

import dev.jasper.fileserver.share.PathRefusedException;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FtpPathsTest {
    @Test void resolvesAgainstTheWorkingDirectory() throws Exception {
        assertThat(FtpPaths.absolute("/", "docs")).isEqualTo("/docs");
        assertThat(FtpPaths.absolute("/docs", "a.txt")).isEqualTo("/docs/a.txt");
        assertThat(FtpPaths.absolute("/docs", "/x")).isEqualTo("/x");
        assertThat(FtpPaths.absolute("/docs", "")).isEqualTo("/docs");
        assertThat(FtpPaths.absolute("/docs", null)).isEqualTo("/docs");
        assertThat(FtpPaths.absolute("/docs", "./a//b/")).isEqualTo("/docs/a/b");
        assertThat(FtpPaths.absolute("/", "a\\b")).isEqualTo("/a/b");
        assertThat(FtpPaths.absolute("/docs", "~")).isEqualTo("/");
        assertThat(FtpPaths.absolute("/docs", "~/x")).isEqualTo("/x");
        assertThat(FtpPaths.absolute("/docs", "~x")).isEqualTo("/docs/~x");
    }

    @Test void dotDotStopsAtTheRoot() throws Exception {
        assertThat(FtpPaths.absolute("/docs", "..")).isEqualTo("/");
        assertThat(FtpPaths.absolute("/", "../../etc/passwd")).isEqualTo("/etc/passwd");
        assertThat(FtpPaths.absolute("/docs/a", "../../..")).isEqualTo("/");
    }

    @Test void refusesNamesNoPlatformCanShare() {
        assertThatThrownBy(() -> FtpPaths.absolute("/", "CON")).isInstanceOf(PathRefusedException.class);
        assertThatThrownBy(() -> FtpPaths.absolute("/", "a:b")).isInstanceOf(PathRefusedException.class);
        assertThatThrownBy(() -> FtpPaths.absolute("/", "bad" + (char) 1 + "name")).isInstanceOf(PathRefusedException.class);
    }

    @Test void namesAndRelativePaths() {
        assertThat(FtpPaths.name("/")).isEqualTo("/");
        assertThat(FtpPaths.name("/docs/a.txt")).isEqualTo("a.txt");
        assertThat(FtpPaths.relative("/")).isEmpty();
        assertThat(FtpPaths.relative("/docs/a.txt")).isEqualTo("docs/a.txt");
    }
}
```

`plugins/fileserver/src/test/java/dev/jasper/fileserver/engine/ftp/ShareViewTest.java`:

```java
package dev.jasper.fileserver.engine.ftp;

import dev.jasper.fileserver.TestClock;
import dev.jasper.fileserver.engine.ServerEvent;
import dev.jasper.fileserver.engine.ServerEvents;
import dev.jasper.fileserver.share.ReadOnlyShareException;
import dev.jasper.fileserver.share.ShareRoot;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.apache.ftpserver.ftplet.FtpException;
import org.apache.ftpserver.ftplet.FtpFile;
import org.apache.ftpserver.usermanager.impl.ConcurrentLoginRequest;
import org.apache.ftpserver.usermanager.impl.WriteRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ShareViewTest {
    @TempDir Path shared;
    final List<ServerEvent> events = new CopyOnWriteArrayList<>();

    @BeforeEach void files() throws IOException {
        Files.createDirectories(shared.resolve("docs"));
        Files.writeString(shared.resolve("docs/a.txt"), "hello");
        Files.writeString(shared.resolve("b.txt"), "bee");
    }

    ShareView view(boolean writable) throws IOException {
        return new ShareView(new ShareRoot(shared, !writable), new ServerEvents(new TestClock(), events::add), "10.0.0.2");
    }

    List<String> docs() throws IOException {
        try (var listing = Files.list(shared.resolve("docs"))) { return listing.map(p -> p.getFileName().toString()).sorted().toList(); }
    }

    List<ServerEvent.Transferred> transferred() {
        return events.stream().filter(e -> e instanceof ServerEvent.Transferred).map(e -> (ServerEvent.Transferred) e).toList();
    }

    @Test void theWorkingDirectoryMovesAndStopsAtTheRoot() throws Exception {
        var view = view(false);
        assertThat(view.getHomeDirectory().getAbsolutePath()).isEqualTo("/");
        assertThat(view.getHomeDirectory().getName()).isEqualTo("/");
        assertThat(view.changeWorkingDirectory("docs")).isTrue();
        assertThat(view.getWorkingDirectory().getAbsolutePath()).isEqualTo("/docs");
        assertThat(view.changeWorkingDirectory("missing")).isFalse();
        assertThat(view.changeWorkingDirectory("a.txt")).as("a file is not a folder").isFalse();
        assertThat(view.changeWorkingDirectory("CON")).isFalse();
        assertThat(view.getWorkingDirectory().getAbsolutePath()).isEqualTo("/docs");
        assertThat(view.changeWorkingDirectory("../../..")).isTrue();
        assertThat(view.getWorkingDirectory().getAbsolutePath()).isEqualTo("/");
        assertThat(view.isRandomAccessible()).isTrue();
        assertThatThrownBy(() -> view.getFile("a:b")).isInstanceOf(FtpException.class);
    }

    @Test void filesDescribeTheShare() throws Exception {
        var view = view(false);
        FtpFile a = view.getFile("/docs/a.txt");
        assertThat(a.doesExist()).isTrue();
        assertThat(a.isFile()).isTrue();
        assertThat(a.isDirectory()).isFalse();
        assertThat(a.isReadable()).isTrue();
        assertThat(a.isWritable()).as("read-only share").isFalse();
        assertThat(a.isRemovable()).isFalse();
        assertThat(a.isHidden()).isFalse();
        assertThat(a.getSize()).isEqualTo(5);
        assertThat(a.getName()).isEqualTo("a.txt");
        assertThat(a.getLastModified()).isEqualTo(Files.getLastModifiedTime(shared.resolve("docs/a.txt")).toMillis());
        assertThat(a.getPhysicalFile()).isNull();
        assertThat(view.getFile("/nope").doesExist()).isFalse();
        assertThat(view.getHomeDirectory().listFiles()).extracting(FtpFile::getAbsolutePath, FtpFile::isDirectory)
            .containsExactly(org.assertj.core.groups.Tuple.tuple("/docs", true), org.assertj.core.groups.Tuple.tuple("/b.txt", false));
        assertThat(view.getFile("docs").listFiles()).extracting(FtpFile::getAbsolutePath).containsExactly("/docs/a.txt");
    }

    @Test void aDownloadReadsFromTheOffsetAndFinishesWithTheReply() throws Exception {
        var view = view(false);
        try (InputStream in = view.getFile("/docs/a.txt").createInputStream(2)) {
            assertThat(new String(in.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("llo");
        }
        assertThat(transferred()).as("nothing ends before the reply").isEmpty();
        view.finish(true);
        var started = (ServerEvent.TransferStarted) events.stream().filter(e -> e instanceof ServerEvent.TransferStarted).findFirst().orElseThrow();
        assertThat(started.total()).isEqualTo(3);
        assertThat(started.remote()).isEqualTo("10.0.0.2");
        assertThat(transferred()).singleElement().satisfies(done -> {
            assertThat(done.path()).isEqualTo("/docs/a.txt");
            assertThat(done.direction()).isEqualTo(ServerEvent.Direction.DOWNLOAD);
            assertThat(done.bytes()).isEqualTo(3);
            assertThat(done.complete()).isTrue();
        });
        view.finish(true);
        assertThat(transferred()).as("finishing twice reports once").hasSize(1);
    }

    @Test void anUploadAppearsOnlyAfterASuccessfulReply() throws Exception {
        var view = view(true);
        try (OutputStream out = view.getFile("/docs/new.txt").createOutputStream(0)) {
            out.write("abc".getBytes(StandardCharsets.UTF_8));
        }
        assertThat(shared.resolve("docs/new.txt")).as("not before the 226").doesNotExist();
        view.finish(true);
        assertThat(shared.resolve("docs/new.txt")).hasContent("abc");
        assertThat(docs()).containsExactly("a.txt", "new.txt");
        assertThat(transferred()).singleElement().satisfies(done -> {
            assertThat(done.direction()).isEqualTo(ServerEvent.Direction.UPLOAD);
            assertThat(done.bytes()).isEqualTo(3);
            assertThat(done.complete()).isTrue();
        });
    }

    @Test void aFailedOrAbandonedUploadLeavesTheOriginal() throws Exception {
        var view = view(true);
        try (OutputStream out = view.getFile("/docs/a.txt").createOutputStream(0)) { out.write('x'); }
        view.finish(false);
        assertThat(shared.resolve("docs/a.txt")).hasContent("hello");
        assertThat(docs()).containsExactly("a.txt");
        try (OutputStream out = view.getFile("/docs/a.txt").createOutputStream(0)) { out.write('y'); }
        view.getFile("/b.txt").createInputStream(0).close();
        assertThat(shared.resolve("docs/a.txt")).as("a new transfer abandons the pending upload").hasContent("hello");
        assertThat(docs()).containsExactly("a.txt");
        view.dispose();
        assertThat(transferred()).extracting(ServerEvent.Transferred::complete).containsExactly(false, false, false);
    }

    @Test void onlyAnAppendAtTheEndResumes() throws Exception {
        var view = view(true);
        FtpFile a = view.getFile("/docs/a.txt");
        assertThatThrownBy(() -> a.createOutputStream(2)).isInstanceOf(IOException.class)
            .hasMessage("Resuming an upload part-way through a file is not supported");
        try (OutputStream out = a.createOutputStream(5)) { out.write("!".getBytes(StandardCharsets.UTF_8)); }
        view.finish(true);
        assertThat(shared.resolve("docs/a.txt")).hasContent("hello!");
    }

    @Test void changesGoThroughTheShareAndAreReported() throws Exception {
        var view = view(true);
        assertThat(view.getFile("/docs/sub").mkdir()).isTrue();
        assertThat(view.getFile("/docs/sub").mkdir()).as("already there").isFalse();
        assertThat(view.getFile("/b.txt").move(view.getFile("/docs/c.txt"))).isTrue();
        assertThat(view.getFile("/docs/c.txt").isRemovable()).isTrue();
        assertThat(view.getFile("/docs/c.txt").delete()).isTrue();
        assertThat(view.getHomeDirectory().isWritable()).as("the root is never a file target").isFalse();
        assertThat(view.getHomeDirectory().isRemovable()).isFalse();
        assertThat(events).filteredOn(e -> e instanceof ServerEvent.Changed).extracting(e -> ((ServerEvent.Changed) e).description())
            .containsExactly("created /docs/sub", "renamed /b.txt to /docs/c.txt", "deleted /docs/c.txt");
    }

    @Test void aReadOnlyShareRefusesEveryChange() throws Exception {
        var view = view(false);
        assertThat(view.getFile("/docs/sub").mkdir()).isFalse();
        assertThat(view.getFile("/b.txt").delete()).isFalse();
        assertThat(view.getFile("/b.txt").move(view.getFile("/c.txt"))).isFalse();
        assertThatThrownBy(() -> view.getFile("/c.txt").createOutputStream(0)).isInstanceOf(ReadOnlyShareException.class);
        assertThat(events).isEmpty();
    }

    @Test void cancellingClosesTheStream() throws Exception {
        var view = view(false);
        InputStream in = view.getFile("/docs/a.txt").createInputStream(0);
        var started = (ServerEvent.TransferStarted) events.getFirst();
        started.cancel().run();
        assertThatThrownBy(in::read).isInstanceOf(IOException.class);
        view.finish(false);
        assertThat(transferred()).singleElement().extracting(ServerEvent.Transferred::complete).isEqualTo(false);
    }

    @Test void theFileSystemGivesEachLoginAViewWithItsAddress() throws Exception {
        var system = new ShareFileSystem(new ShareRoot(shared, true), new ServerEvents(new TestClock(), events::add));
        var view = (ShareView) system.createFileSystemView(new ShareUser("dustin", "192.168.1.9", false));
        assertThat(view.remote()).isEqualTo("192.168.1.9");
        var reader = new ShareUser("dustin", "192.168.1.9", false);
        var writer = new ShareUser("dustin", "192.168.1.9", true);
        assertThat(reader.getName()).isEqualTo("dustin");
        assertThat(reader.getHomeDirectory()).isEqualTo("/");
        assertThat(reader.getEnabled()).isTrue();
        assertThat(reader.authorize(new WriteRequest())).isNull();
        assertThat(writer.authorize(new WriteRequest())).isNotNull();
        assertThat(reader.authorize(new ConcurrentLoginRequest(5, 5))).isNotNull();
    }
}
```

- [ ] **Step 3: Run them to see them fail**

Run: `./gradlew :jasper-plugin-fileserver:test --tests 'dev.jasper.fileserver.engine.ftp.*'`
Expected: compilation fails, because the `engine.ftp` package does not exist.

- [ ] **Step 4: Write the package**

`engine/ftp/package-info.java`:

```java
/**
 * The FTP and FTPS engine on Apache FtpServer: a file system over the share, the server's one user, an ftplet that
 * reports activity and enforces Require TLS, and TLS from the shared certificate. The only package that names
 * FtpServer or MINA types. May depend on {@code engine}, {@code engine.tls}, {@code share}, {@code auth},
 * {@code definition} and the JDK.
 */
package dev.jasper.fileserver.engine.ftp;
```

`engine/ftp/FtpPaths.java`:

```java
package dev.jasper.fileserver.engine.ftp;

import dev.jasper.fileserver.share.PathRefusedException;
import dev.jasper.fileserver.share.ShareRoot;
import java.util.ArrayList;

/**
 * FTP paths: absolute and {@code /}-separated, resolved against the working directory, with {@code ..} stopping at
 * the share's root as an FTP server's chroot does. {@code ~} is the root. Names no platform can share are refused.
 */
final class FtpPaths {
    private FtpPaths() { }

    static String absolute(String workingDirectory, String argument) throws PathRefusedException {
        String text = argument == null ? "" : argument;
        if (text.equals("~") || text.startsWith("~/")) text = "/" + text.substring(1);
        var names = new ArrayList<String>();
        if (!text.startsWith("/") && !text.startsWith("\\"))
            for (String name : workingDirectory.split("/")) if (!name.isEmpty()) names.add(name);
        for (String name : text.split("[/\\\\]")) {
            if (name.isEmpty() || name.equals(".")) continue;
            if (name.equals("..")) {
                if (!names.isEmpty()) names.removeLast();
                continue;
            }
            names.add(name);
        }
        String path = "/" + String.join("/", names);
        ShareRoot.segments(path); // refuses names no platform can share
        return path;
    }

    /** The path the share takes: the absolute path without its leading slash. */
    static String relative(String absolute) { return absolute.substring(1); }

    /** The last name, or {@code /} for the root. */
    static String name(String absolute) {
        return absolute.equals("/") ? "/" : absolute.substring(absolute.lastIndexOf('/') + 1);
    }
}
```

`engine/ftp/ShareUser.java`:

```java
package dev.jasper.fileserver.engine.ftp;

import java.util.ArrayList;
import java.util.List;
import org.apache.ftpserver.ftplet.Authority;
import org.apache.ftpserver.usermanager.impl.BaseUser;
import org.apache.ftpserver.usermanager.impl.ConcurrentLoginPermission;
import org.apache.ftpserver.usermanager.impl.TransferRatePermission;
import org.apache.ftpserver.usermanager.impl.WritePermission;

/**
 * The server's one user after a login, remembering the client address it logged in from. FtpServer's {@code USER}
 * command needs the concurrent-login authority, and transfers need the transfer-rate one; 0 means unlimited. The
 * idle time 0 leaves the listener's timeout in force.
 */
final class ShareUser extends BaseUser {
    private final String remote;

    ShareUser(String name, String remote, boolean writable) {
        this.remote = remote;
        setName(name);
        setHomeDirectory("/");
        setEnabled(true);
        setMaxIdleTime(0);
        var authorities = new ArrayList<Authority>(List.of(new ConcurrentLoginPermission(0, 0), new TransferRatePermission(0, 0)));
        if (writable) authorities.add(new WritePermission());
        setAuthorities(authorities);
    }

    String remote() { return remote; }
}
```

`engine/ftp/ShareFileSystem.java`:

```java
package dev.jasper.fileserver.engine.ftp;

import dev.jasper.fileserver.engine.ServerEvents;
import dev.jasper.fileserver.share.ShareRoot;
import org.apache.ftpserver.ftplet.FileSystemFactory;
import org.apache.ftpserver.ftplet.FileSystemView;
import org.apache.ftpserver.ftplet.User;

/** Gives each login its own view of the share, reporting under the address it logged in from. */
final class ShareFileSystem implements FileSystemFactory {
    private final ShareRoot share;
    private final ServerEvents events;

    ShareFileSystem(ShareRoot share, ServerEvents events) { this.share = share; this.events = events; }

    @Override public FileSystemView createFileSystemView(User user) {
        return new ShareView(share, events, user instanceof ShareUser known ? known.remote() : "");
    }
}
```

`engine/ftp/ShareView.java`:

```java
package dev.jasper.fileserver.engine.ftp;

import dev.jasper.fileserver.engine.ServerEvents;
import dev.jasper.fileserver.share.PathRefusedException;
import dev.jasper.fileserver.share.ShareRoot;
import java.io.Closeable;
import java.io.IOException;
import org.apache.ftpserver.ftplet.FileSystemView;
import org.apache.ftpserver.ftplet.FtpException;
import org.apache.ftpserver.ftplet.FtpFile;

/**
 * One logged-in client's view of the share: its working directory and the one transfer it is running. A transfer
 * ends in {@link #finish}, which the ftplet calls with the command's reply. Only a 226 reply commits an upload; any
 * other reply, a new transfer, or the client leaving abandons it. Thread-safe.
 */
final class ShareView implements FileSystemView {
    private final ShareRoot share;
    private final ServerEvents events;
    private final String remote;
    private String workingDirectory = "/";
    private ServerEvents.Transfer transfer;
    private ShareRoot.Upload upload;
    private Closeable stream;
    private String transferPath;

    ShareView(ShareRoot share, ServerEvents events, String remote) { this.share = share; this.events = events; this.remote = remote; }

    ShareRoot share() { return share; }
    ServerEvents events() { return events; }
    String remote() { return remote; }

    @Override public FtpFile getHomeDirectory() { return new ShareFile(this, "/", null); }

    @Override public synchronized FtpFile getWorkingDirectory() { return new ShareFile(this, workingDirectory, null); }

    @Override public synchronized boolean changeWorkingDirectory(String directory) {
        try {
            String target = FtpPaths.absolute(workingDirectory, directory);
            if (!share.attributes(FtpPaths.relative(target)).directory()) return false;
            workingDirectory = target;
            return true;
        } catch (IOException missingOrRefused) {
            return false;
        }
    }

    @Override public synchronized FtpFile getFile(String file) throws FtpException {
        try { return new ShareFile(this, FtpPaths.absolute(workingDirectory, file), null); }
        catch (PathRefusedException refused) { throw new FtpException(refused.getMessage(), refused); }
    }

    @Override public boolean isRandomAccessible() { return true; }

    @Override public void dispose() { finish(false); }

    /** Starts tracking a transfer, abandoning any earlier one; {@code upload} is null for downloads and appends. */
    synchronized void track(ServerEvents.Transfer next, ShareRoot.Upload nextUpload, Closeable nextStream, String path) {
        finish(false);
        transfer = next;
        upload = nextUpload;
        stream = nextStream;
        transferPath = path;
    }

    /** Ends the tracked transfer, if any: commits its upload when {@code success}, otherwise abandons it. */
    synchronized void finish(boolean success) {
        if (transfer == null) return;
        ServerEvents.Transfer ending = transfer;
        ShareRoot.Upload pending = upload;
        Closeable open = stream;
        String path = transferPath;
        transfer = null;
        upload = null;
        stream = null;
        transferPath = null;
        boolean complete = success;
        try { open.close(); } catch (IOException ignored) { /* the transfer is over either way */ }
        if (pending != null) {
            if (success) {
                try { pending.commit(); }
                catch (IOException | RuntimeException failure) {
                    complete = false;
                    events.error(remote, "Could not save " + path + ": "
                        + (failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage()));
                }
            } else {
                pending.close();
            }
        }
        ending.finish(complete);
    }
}
```

`engine/ftp/ShareFile.java`:

```java
package dev.jasper.fileserver.engine.ftp;

import dev.jasper.fileserver.engine.ServerEvent;
import dev.jasper.fileserver.engine.ServerEvents;
import dev.jasper.fileserver.share.ShareEntry;
import dev.jasper.fileserver.share.ShareRoot;
import java.io.Closeable;
import java.io.FilterInputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.Channels;
import java.nio.channels.SeekableByteChannel;
import java.util.List;
import java.util.Optional;
import org.apache.ftpserver.ftplet.FtpFile;

/**
 * One path in a share as FtpServer sees it. Every operation goes through {@link ShareRoot}, which refuses escapes and
 * writes to a read-only share. Listings show every shareable name, as HTTP does. A new or replaced file arrives
 * through {@link ShareRoot#upload}. A non-zero offset is accepted only at the current end of the file, as an append;
 * an upload resumed part-way through is refused.
 */
final class ShareFile implements FtpFile {
    private final ShareView view;
    private final String path;
    private final ShareEntry known;

    /** {@code known} is the entry a listing already read, or null to read it when asked. */
    ShareFile(ShareView view, String path, ShareEntry known) { this.view = view; this.path = path; this.known = known; }

    private String relative() { return FtpPaths.relative(path); }

    private Optional<ShareEntry> entry() {
        if (known != null) return Optional.of(known);
        try { return Optional.of(view.share().attributes(relative())); }
        catch (IOException missingOrRefused) { return Optional.empty(); }
    }

    @Override public String getAbsolutePath() { return path; }
    @Override public String getName() { return FtpPaths.name(path); }
    @Override public boolean isHidden() { return false; }
    @Override public boolean isDirectory() { return entry().map(ShareEntry::directory).orElse(false); }
    @Override public boolean isFile() { return entry().map(entry -> !entry.directory()).orElse(false); }
    @Override public boolean doesExist() { return entry().isPresent(); }
    @Override public boolean isReadable() { return doesExist(); }
    @Override public boolean isWritable() { return !view.share().readOnly() && !path.equals("/"); }
    @Override public boolean isRemovable() { return isWritable() && doesExist(); }
    @Override public String getOwnerName() { return "user"; }
    @Override public String getGroupName() { return "group"; }
    @Override public int getLinkCount() { return isDirectory() ? 3 : 1; }
    @Override public long getLastModified() { return entry().map(entry -> entry.modified().toEpochMilli()).orElse(0L); }
    @Override public boolean setLastModified(long time) { return false; }
    @Override public long getSize() { return entry().map(ShareEntry::size).orElse(0L); }
    /** Never the real file: nothing outside the share's checks may touch the disk. */
    @Override public Object getPhysicalFile() { return null; }

    @Override public boolean mkdir() {
        try { view.share().mkdir(relative()); }
        catch (IOException refused) { return false; }
        view.events().changed(view.remote(), "created " + path);
        return true;
    }

    @Override public boolean delete() {
        try { view.share().delete(relative()); }
        catch (IOException refused) { return false; }
        view.events().changed(view.remote(), "deleted " + path);
        return true;
    }

    @Override public boolean move(FtpFile destination) {
        if (!(destination instanceof ShareFile target)) return false;
        try { view.share().rename(relative(), target.relative()); }
        catch (IOException refused) { return false; }
        view.events().changed(view.remote(), "renamed " + path + " to " + target.path);
        return true;
    }

    @Override public List<? extends FtpFile> listFiles() {
        String prefix = path.equals("/") ? "/" : path + "/";
        try { return view.share().list(relative()).stream().map(entry -> new ShareFile(view, prefix + entry.name(), entry)).toList(); }
        catch (IOException notAFolder) { return null; }
    }

    @Override public InputStream createInputStream(long offset) throws IOException {
        SeekableByteChannel channel = view.share().openRead(relative());
        long total;
        try {
            total = Math.max(0, channel.size() - offset);
            channel.position(offset);
        } catch (IOException | RuntimeException failure) {
            channel.close();
            throw failure;
        }
        ServerEvents.Transfer transfer = view.events().transfer(view.remote(), ServerEvent.Direction.DOWNLOAD, path, total, () -> closeQuietly(channel));
        var in = new CountingInput(Channels.newInputStream(channel), transfer);
        view.track(transfer, null, in, path);
        return in;
    }

    @Override public OutputStream createOutputStream(long offset) throws IOException {
        if (offset == 0) {
            ShareRoot.Upload upload = view.share().upload(relative());
            OutputStream raw = upload.stream();
            ServerEvents.Transfer transfer = view.events().transfer(view.remote(), ServerEvent.Direction.UPLOAD, path, -1, () -> closeQuietly(raw));
            var out = new CountingOutput(raw, transfer);
            view.track(transfer, upload, out, path);
            return out;
        }
        if (!isFile() || offset != getSize()) throw new IOException("Resuming an upload part-way through a file is not supported");
        OutputStream raw = view.share().openWrite(relative(), true);
        ServerEvents.Transfer transfer = view.events().transfer(view.remote(), ServerEvent.Direction.UPLOAD, path, -1, () -> closeQuietly(raw));
        var out = new CountingOutput(raw, transfer);
        view.track(transfer, null, out, path);
        return out;
    }

    private static void closeQuietly(Closeable closeable) {
        try { closeable.close(); } catch (IOException ignored) { /* cancelling; the transfer fails next */ }
    }

    /** Counts what a download sends. */
    private static final class CountingInput extends FilterInputStream {
        private final ServerEvents.Transfer transfer;

        CountingInput(InputStream in, ServerEvents.Transfer transfer) { super(in); this.transfer = transfer; }

        @Override public int read() throws IOException {
            int value = super.read();
            if (value >= 0) transfer.add(1);
            return value;
        }

        @Override public int read(byte[] buffer, int offset, int length) throws IOException {
            int count = super.read(buffer, offset, length);
            if (count > 0) transfer.add(count);
            return count;
        }
    }

    /** Counts what an upload receives; closing twice is harmless (FtpServer's STOR closes the stream twice). */
    private static final class CountingOutput extends FilterOutputStream {
        private final ServerEvents.Transfer transfer;

        CountingOutput(OutputStream out, ServerEvents.Transfer transfer) { super(out); this.transfer = transfer; }

        @Override public void write(int value) throws IOException {
            out.write(value);
            transfer.add(1);
        }

        @Override public void write(byte[] buffer, int offset, int length) throws IOException {
            out.write(buffer, offset, length);
            transfer.add(length);
        }
    }
}
```

`FilterOutputStream.close()` flushes and closes once; a second call does nothing. `FilterInputStream.close()` closes the channel's stream.

- [ ] **Step 5: Run the tests**

Run: `./gradlew :jasper-plugin-fileserver:check`
Expected: PASS. `verifyPluginArchitecture` accepts the FtpServer types because the plugin bundles them.

- [ ] **Step 6: Commit**

```bash
git add plugins/fileserver
git commit -m "File Server: an FTP view of a share, with uploads committed only after the reply

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 5: The FTP engine — logins, Require TLS, FTPS

**Files:**
- Create: `plugins/fileserver/src/main/java/dev/jasper/fileserver/engine/ftp/{ShareUserManager,EventFtplet,TlsConfiguration,FtpEngine}.java`
- Test: `plugins/fileserver/src/test/java/dev/jasper/fileserver/engine/ftp/{ShareUserManagerTest,FtpEngineTest}.java`

**Interfaces:**
- Consumes:
  - From Task 4: `ShareUser`, `ShareView.finish`, `ShareFileSystem`.
  - From Task 2: `TlsMaterial.context()`.
  - `ResolvedLogin` (`isAnonymous`, `user`, `matches`).
  - `LoginThrottle` (`retryAfter`, `failed`, `succeeded`).
  - `ServerEvents`, `ServerAddresses` (`bindAddress`, `reachable`, `urls`, `describe`, `hostText`), `EngineFailure.bind`, `PortRange`.
- Produces:
  - `public FtpEngine(ServerDefinition definition, ShareRoot share, ResolvedLogin login, ServerEvents events, LoginThrottle throttle, Supplier<List<ServerAddresses.Nic>> nics, PortRange passiveDefault, TlsMaterial tls) implements ServerEngine`. `tls` may be null when the definition does not use TLS. The class also has `public int port()`.
  - Package-private: `ShareUserManager(ResolvedLogin, boolean writable, LoginThrottle, ServerEvents) implements UserManager`, `EventFtplet(ServerEvents, boolean requireTls) extends DefaultFtplet`, and `TlsConfiguration(SSLContext) implements SslConfiguration`.

- [ ] **Step 1: Write the failing tests**

`plugins/fileserver/src/test/java/dev/jasper/fileserver/engine/ftp/ShareUserManagerTest.java`:

```java
package dev.jasper.fileserver.engine.ftp;

import dev.jasper.fileserver.TestClock;
import dev.jasper.fileserver.auth.ResolvedLogin;
import dev.jasper.fileserver.engine.LoginThrottle;
import dev.jasper.fileserver.engine.ServerEvent;
import dev.jasper.fileserver.engine.ServerEvents;
import java.net.InetAddress;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.apache.ftpserver.ftplet.AuthenticationFailedException;
import org.apache.ftpserver.usermanager.AnonymousAuthentication;
import org.apache.ftpserver.usermanager.UsernamePasswordAuthentication;
import org.apache.ftpserver.usermanager.impl.UserMetadata;
import org.apache.ftpserver.usermanager.impl.WriteRequest;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ShareUserManagerTest {
    final TestClock clock = new TestClock();
    final LoginThrottle throttle = new LoginThrottle(clock);
    final List<ServerEvent> events = new CopyOnWriteArrayList<>();

    ShareUserManager manager(ResolvedLogin login, boolean writable) {
        return new ShareUserManager(login, writable, throttle, new ServerEvents(clock, events::add));
    }

    static UserMetadata from(String address) throws Exception {
        var metadata = new UserMetadata();
        metadata.setInetAddress(InetAddress.getByName(address));
        return metadata;
    }

    @Test void anAnonymousServerAcceptsOnlyAnonymous() throws Exception {
        var users = manager(ResolvedLogin.anonymous(), false);
        var user = (ShareUser) users.authenticate(new AnonymousAuthentication(from("10.0.0.2")));
        assertThat(user.getName()).isEqualTo("anonymous");
        assertThat(user.remote()).isEqualTo("10.0.0.2");
        assertThat(user.authorize(new WriteRequest())).as("read-only").isNull();
        assertThatThrownBy(() -> users.authenticate(new UsernamePasswordAuthentication("dustin", "pw", from("10.0.0.2"))))
            .isInstanceOf(AuthenticationFailedException.class);
        assertThat(users.getUserByName("anonymous")).isNotNull();
        assertThat(users.getUserByName("dustin")).isNull();
        assertThat(users.getAllUserNames()).containsExactly("anonymous");
        assertThat(users.isAdmin("anonymous")).isFalse();
        assertThat(users.getAdminName()).isEmpty();
        assertThat(events).singleElement().isInstanceOf(ServerEvent.LoginSucceeded.class);
    }

    @Test void aPasswordServerChecksThroughTheThrottle() throws Exception {
        var users = manager(ResolvedLogin.password("dustin", "pw".toCharArray()), true);
        assertThatThrownBy(() -> users.authenticate(new AnonymousAuthentication(from("10.0.0.2")))).isInstanceOf(AuthenticationFailedException.class);
        assertThatThrownBy(() -> users.authenticate(new UsernamePasswordAuthentication("dustin", "bad", from("10.0.0.2"))))
            .isInstanceOf(AuthenticationFailedException.class);
        assertThat(events).singleElement().isEqualTo(new ServerEvent.LoginFailed(clock.instant(), "10.0.0.2", "dustin"));
        assertThatThrownBy(() -> users.authenticate(new UsernamePasswordAuthentication("dustin", "pw", from("10.0.0.2"))))
            .as("the address must wait before trying again").isInstanceOf(AuthenticationFailedException.class);
        assertThat(events).as("a refused attempt is not checked or logged").hasSize(1);
        var other = (ShareUser) users.authenticate(new UsernamePasswordAuthentication("dustin", "pw", from("10.0.0.3")));
        assertThat(other.remote()).isEqualTo("10.0.0.3");
        clock.advance(Duration.ofSeconds(2));
        var user = (ShareUser) users.authenticate(new UsernamePasswordAuthentication("dustin", "pw", from("10.0.0.2")));
        assertThat(user.getName()).isEqualTo("dustin");
        assertThat(user.authorize(new WriteRequest())).as("writable").isNotNull();
        assertThat(throttle.retryAfter("10.0.0.2")).isZero();
        assertThat(users.getUserByName("dustin")).isNotNull();
        assertThat(users.getUserByName("anonymous")).isNull();
    }
}
```

`plugins/fileserver/src/test/java/dev/jasper/fileserver/engine/ftp/FtpEngineTest.java`:

```java
package dev.jasper.fileserver.engine.ftp;

import dev.jasper.fileserver.Ports;
import dev.jasper.fileserver.TestClock;
import dev.jasper.fileserver.auth.ResolvedLogin;
import dev.jasper.fileserver.definition.PortRange;
import dev.jasper.fileserver.definition.Protocol;
import dev.jasper.fileserver.definition.ServerDefinition;
import dev.jasper.fileserver.engine.EngineFailure;
import dev.jasper.fileserver.engine.LoginThrottle;
import dev.jasper.fileserver.engine.ServerEvent;
import dev.jasper.fileserver.engine.ServerEvents;
import dev.jasper.fileserver.engine.tls.TlsMaterial;
import dev.jasper.fileserver.share.ShareRoot;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;
import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;
import org.apache.commons.net.ftp.FTPSClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FtpEngineTest {
    static final ResolvedLogin DUSTIN = ResolvedLogin.password("dustin", "pw".toCharArray());
    @TempDir Path temp;
    Path shared;
    final List<ServerEvent> events = new CopyOnWriteArrayList<>();
    final TestClock clock = new TestClock();
    final LoginThrottle throttle = new LoginThrottle(clock);
    FtpEngine engine;
    int port;

    @BeforeEach void share() throws IOException {
        shared = Files.createDirectories(temp.resolve("shared"));
        Files.createDirectories(shared.resolve("docs"));
        Files.writeString(shared.resolve("docs/a.txt"), "hello");
        Files.writeString(temp.resolve("outside.txt"), "secret");
    }

    @AfterEach void stop() { if (engine != null) engine.stop(); }

    FtpEngine engine(ResolvedLogin login, boolean writable, boolean tls, boolean requireTls) throws IOException {
        port = Ports.free();
        int first = Math.min(Ports.free(), 65000);
        var base = ServerDefinition.create("ftp", Protocol.FTP, shared);
        var definition = new ServerDefinition(base.id(), "ftp", Protocol.FTP, shared, base.bind(), port, Optional.empty(), writable,
            base.login(), List.of(), tls, requireTls, false);
        engine = new FtpEngine(definition, new ShareRoot(shared, !writable), login, new ServerEvents(Clock.systemUTC(), events::add), throttle,
            List::of, new PortRange(first, first + 20), new TlsMaterial(temp.resolve("tls"), Clock.systemUTC(), () -> ""));
        return engine;
    }

    FtpEngine start(ResolvedLogin login, boolean writable, boolean tls, boolean requireTls) throws Exception {
        engine(login, writable, tls, requireTls).start();
        return engine;
    }

    FTPClient client() throws IOException {
        var client = new FTPClient();
        client.setDefaultTimeout(5000);
        client.connect("127.0.0.1", port);
        client.enterLocalPassiveMode();
        return client;
    }

    FTPSClient secureClient() throws IOException {
        var client = new FTPSClient("TLS", false); // explicit: connect sends AUTH TLS
        client.setDefaultTimeout(5000);
        client.connect("127.0.0.1", port);
        client.enterLocalPassiveMode();
        return client;
    }

    static String fetch(FTPClient client, String path) throws IOException {
        var out = new ByteArrayOutputStream();
        assertThat(client.retrieveFile(path, out)).as("RETR " + path + ": " + client.getReplyString()).isTrue();
        return out.toString(StandardCharsets.UTF_8);
    }

    static boolean store(FTPClient client, String path, String text) throws IOException {
        return client.storeFile(path, new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)));
    }

    /** A transfer's reply reaches the client before the ftplet commits it; wait for the result. */
    static void await(BooleanSupplier condition, String what) throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) Thread.sleep(10);
        assertThat(condition.getAsBoolean()).as(what).isTrue();
    }

    static String content(Path file) {
        try { return Files.readString(file); } catch (IOException missing) { return null; }
    }

    @Test void anonymousReadOnlyListsAndDownloads() throws Exception {
        start(ResolvedLogin.anonymous(), false, false, false);
        assertThat(engine.addresses()).containsExactly(URI.create("ftp://127.0.0.1:" + port + "/"));
        var client = client();
        assertThat(client.login("anonymous", "me@example.com")).isTrue();
        client.setFileType(FTP.BINARY_FILE_TYPE);
        assertThat(client.listFiles("/")).extracting(FTPFile::getName).containsExactly("docs");
        assertThat(fetch(client, "/docs/a.txt")).isEqualTo("hello");
        assertThat(store(client, "/up.txt", "x")).as("read-only").isFalse();
        assertThat(client.makeDirectory("/x")).isFalse();
        assertThat(client.deleteFile("/docs/a.txt")).isFalse();
        assertThat(shared.resolve("up.txt")).doesNotExist();
        client.logout();
        client.disconnect();
        await(() -> events.stream().anyMatch(e -> e instanceof ServerEvent.Transferred done && done.complete()
            && done.path().equals("/docs/a.txt") && done.bytes() == 5), "the download is logged");
        assertThat(events).anyMatch(e -> e instanceof ServerEvent.Connected);
        assertThat(events).anyMatch(e -> e instanceof ServerEvent.LoginSucceeded ok && ok.user().equals("anonymous"));
        var named = client();
        assertThat(named.login("dustin", "pw")).as("an anonymous server has no named users").isFalse();
        named.disconnect();
    }

    @Test void theWorkingDirectoryStaysInsideTheShare() throws Exception {
        start(ResolvedLogin.anonymous(), false, false, false);
        var client = client();
        assertThat(client.login("anonymous", "x")).isTrue();
        assertThat(client.changeWorkingDirectory("..")).isTrue();
        assertThat(client.printWorkingDirectory()).isEqualTo("/");
        assertThat(client.changeWorkingDirectory("/docs")).isTrue();
        assertThat(client.changeWorkingDirectory("../../..")).isTrue();
        assertThat(client.printWorkingDirectory()).isEqualTo("/");
        assertThat(client.retrieveFile("../outside.txt", new ByteArrayOutputStream())).isFalse();
        assertThat(client.retrieveFile("/docs/../../outside.txt", new ByteArrayOutputStream())).isFalse();
        client.disconnect();
    }

    @Test void aUserWithWritesUploadsRenamesAndDeletes() throws Exception {
        start(DUSTIN, true, false, false);
        var client = client();
        assertThat(client.login("anonymous", "x")).as("no anonymous login").isFalse();
        assertThat(client.login("dustin", "pw")).isTrue();
        client.setFileType(FTP.BINARY_FILE_TYPE);
        assertThat(store(client, "/docs/new.txt", "abc")).isTrue();
        await(() -> "abc".equals(content(shared.resolve("docs/new.txt"))), "the upload is committed");
        assertThat(store(client, "/docs/a.txt", "replaced")).isTrue();
        await(() -> "replaced".equals(content(shared.resolve("docs/a.txt"))), "the file is replaced");
        assertThat(client.appendFile("/docs/a.txt", new ByteArrayInputStream("!".getBytes(StandardCharsets.UTF_8)))).isTrue();
        await(() -> "replaced!".equals(content(shared.resolve("docs/a.txt"))), "the append lands");
        client.setRestartOffset(2);
        assertThat(store(client, "/docs/a.txt", "zz")).as("resuming part-way through is refused").isFalse();
        assertThat(content(shared.resolve("docs/a.txt"))).isEqualTo("replaced!");
        assertThat(client.rename("/docs/new.txt", "/docs/renamed.txt")).isTrue();
        assertThat(client.makeDirectory("/docs/sub")).isTrue();
        assertThat(client.removeDirectory("/docs/sub")).isTrue();
        assertThat(client.deleteFile("/docs/renamed.txt")).isTrue();
        client.logout();
        client.disconnect();
        try (var listing = Files.list(shared.resolve("docs"))) {
            assertThat(listing.map(p -> p.getFileName().toString()).toList()).as("no temporary files remain").containsExactly("a.txt");
        }
        assertThat(events).filteredOn(e -> e instanceof ServerEvent.Changed).extracting(e -> ((ServerEvent.Changed) e).description())
            .containsExactly("renamed /docs/new.txt to /docs/renamed.txt", "created /docs/sub", "deleted /docs/sub", "deleted /docs/renamed.txt");
    }

    @Test void aWrongPasswordIsThrottled() throws Exception {
        start(DUSTIN, false, false, false);
        var first = client();
        assertThat(first.login("dustin", "bad")).isFalse();
        first.disconnect();
        var second = client();
        assertThat(second.login("dustin", "pw")).as("too soon after a failure").isFalse();
        second.disconnect();
        clock.advance(Duration.ofSeconds(2));
        var third = client();
        assertThat(third.login("dustin", "pw")).isTrue();
        third.disconnect();
        assertThat(events).filteredOn(e -> e instanceof ServerEvent.LoginFailed).hasSize(1);
    }

    @Test void requireTlsRefusesPlainLoginsAndPlainData() throws Exception {
        start(DUSTIN, false, true, true);
        var plain = client();
        assertThat(plain.login("dustin", "pw")).isFalse();
        assertThat(plain.getReplyCode()).isEqualTo(530);
        plain.disconnect();
        var secure = secureClient();
        assertThat(secure.login("dustin", "pw")).isTrue();
        secure.setFileType(FTP.BINARY_FILE_TYPE);
        assertThat(secure.listNames("/")).as("no PROT P yet").isNull();
        assertThat(secure.getReplyCode()).isEqualTo(521);
        secure.execPBSZ(0);
        secure.execPROT("P");
        assertThat(fetch(secure, "/docs/a.txt")).isEqualTo("hello");
        secure.disconnect();
    }

    @Test void tlsWithoutRequireAllowsBoth() throws Exception {
        start(DUSTIN, false, true, false);
        var plain = client();
        assertThat(plain.login("dustin", "pw")).isTrue();
        assertThat(fetch(plain, "/docs/a.txt")).isEqualTo("hello");
        plain.disconnect();
        var secure = secureClient();
        assertThat(secure.login("dustin", "pw")).isTrue();
        secure.execPBSZ(0);
        secure.execPROT("P");
        assertThat(fetch(secure, "/docs/a.txt")).isEqualTo("hello");
        secure.disconnect();
    }

    @Test void withoutTlsAuthIsRefused() throws Exception {
        start(DUSTIN, false, false, false);
        var client = client();
        assertThat(client.sendCommand("AUTH", "TLS")).isEqualTo(431);
        client.disconnect();
    }

    @Test void aBusyPortFailsWithAClearMessageAndStopFreesThePort() throws Exception {
        engine(ResolvedLogin.anonymous(), false, false, false);
        try (var taken = new ServerSocket()) {
            taken.setReuseAddress(false);
            taken.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), port));
            assertThatThrownBy(engine::start).isInstanceOf(EngineFailure.class).hasMessage("Port " + port + " is already in use on 127.0.0.1");
        }
        assertThat(engine.addresses()).isEmpty();
        engine(ResolvedLogin.anonymous(), false, false, false).start();
        assertThat(engine.port()).isEqualTo(port);
        engine.stop();
        engine.stop();
        assertThat(engine.addresses()).isEmpty();
        assertThatThrownBy(() -> new Socket(InetAddress.getLoopbackAddress(), port).close()).isInstanceOf(ConnectException.class);
    }
}
```

- [ ] **Step 2: Run them to see them fail**

Run: `./gradlew :jasper-plugin-fileserver:test --tests 'dev.jasper.fileserver.engine.ftp.*'`
Expected: compilation fails, because `ShareUserManager` and `FtpEngine` do not exist.

- [ ] **Step 3: Write `ShareUserManager`**

```java
package dev.jasper.fileserver.engine.ftp;

import dev.jasper.fileserver.auth.ResolvedLogin;
import dev.jasper.fileserver.engine.LoginThrottle;
import dev.jasper.fileserver.engine.ServerAddresses;
import dev.jasper.fileserver.engine.ServerEvents;
import java.time.Duration;
import java.util.Arrays;
import org.apache.ftpserver.ftplet.Authentication;
import org.apache.ftpserver.ftplet.AuthenticationFailedException;
import org.apache.ftpserver.ftplet.FtpException;
import org.apache.ftpserver.ftplet.User;
import org.apache.ftpserver.ftplet.UserManager;
import org.apache.ftpserver.usermanager.AnonymousAuthentication;
import org.apache.ftpserver.usermanager.UsernamePasswordAuthentication;
import org.apache.ftpserver.usermanager.impl.UserMetadata;

/**
 * The server's one login: the anonymous user, or the resolved user name and password. Every attempt goes through the
 * shared {@link LoginThrottle}. An address that must wait, or is locked out, is refused without its password being
 * checked, as HTTP answers 429. FtpServer adds its own half-second pause after each failure and drops the
 * connection after three. Nobody is an administrator, and users cannot be changed.
 */
final class ShareUserManager implements UserManager {
    private final ResolvedLogin login;
    private final boolean writable;
    private final LoginThrottle throttle;
    private final ServerEvents events;

    ShareUserManager(ResolvedLogin login, boolean writable, LoginThrottle throttle, ServerEvents events) {
        this.login = login; this.writable = writable; this.throttle = throttle; this.events = events;
    }

    private String expectedName() { return login.user().orElse(ANONYMOUS); }

    @Override public User getUserByName(String name) {
        return expectedName().equals(name) ? new ShareUser(name, "", writable) : null;
    }

    @Override public String[] getAllUserNames() { return new String[]{expectedName()}; }
    @Override public void delete(String name) throws FtpException { throw new FtpException("File Server's users cannot be changed"); }
    @Override public void save(User user) throws FtpException { throw new FtpException("File Server's users cannot be changed"); }
    @Override public boolean doesExist(String name) { return getUserByName(name) != null; }
    @Override public String getAdminName() { return ""; }
    @Override public boolean isAdmin(String name) { return false; }

    @Override public User authenticate(Authentication authentication) throws AuthenticationFailedException {
        String remote = remote(authentication);
        if (throttle.retryAfter(remote).compareTo(Duration.ZERO) > 0) throw new AuthenticationFailedException("Too many failed logins; try again later");
        if (authentication instanceof AnonymousAuthentication && login.isAnonymous()) {
            events.loginSucceeded(remote, ANONYMOUS);
            return new ShareUser(ANONYMOUS, remote, writable);
        }
        if (authentication instanceof UsernamePasswordAuthentication attempt && !login.isAnonymous()) {
            String user = attempt.getUsername();
            char[] password = attempt.getPassword() == null ? new char[0] : attempt.getPassword().toCharArray();
            try {
                if (login.matches(user, password)) {
                    throttle.succeeded(remote);
                    events.loginSucceeded(remote, user);
                    return new ShareUser(user, remote, writable);
                }
            } finally {
                Arrays.fill(password, '\0');
            }
            throttle.failed(remote);
            events.loginFailed(remote, String.valueOf(user));
            throw new AuthenticationFailedException("Wrong user name or password");
        }
        throw new AuthenticationFailedException("This server does not accept that login");
    }

    private static String remote(Authentication authentication) {
        UserMetadata metadata = switch (authentication) {
            case AnonymousAuthentication anonymous -> anonymous.getUserMetadata();
            case UsernamePasswordAuthentication attempt -> attempt.getUserMetadata();
            default -> null;
        };
        return metadata == null || metadata.getInetAddress() == null ? "" : ServerAddresses.hostText(metadata.getInetAddress());
    }
}
```

- [ ] **Step 4: Write `EventFtplet` and `TlsConfiguration`**

`engine/ftp/EventFtplet.java`:

```java
package dev.jasper.fileserver.engine.ftp;

import dev.jasper.fileserver.engine.ServerAddresses;
import dev.jasper.fileserver.engine.ServerEvents;
import java.net.InetSocketAddress;
import java.util.Locale;
import java.util.Set;
import org.apache.ftpserver.ftplet.DefaultFtpReply;
import org.apache.ftpserver.ftplet.DefaultFtplet;
import org.apache.ftpserver.ftplet.FtpException;
import org.apache.ftpserver.ftplet.FtpReply;
import org.apache.ftpserver.ftplet.FtpRequest;
import org.apache.ftpserver.ftplet.FtpSession;
import org.apache.ftpserver.ftplet.FtpletResult;

/**
 * Reports connections, enforces Require TLS before FtpServer runs a command, and ends each transfer with its command's
 * reply: a 226 commits an upload and completes a download, and anything else abandons the transfer. FtpServer calls
 * these methods on the session's own thread. A command that throws skips {@code afterCommand}; its transfer ends
 * with the next one, or when the client leaves.
 */
final class EventFtplet extends DefaultFtplet {
    static final int PROTECTION_REQUIRED = 521;
    private static final Set<String> LOGIN = Set.of("USER", "PASS");
    private static final Set<String> DATA = Set.of("RETR", "STOR", "STOU", "APPE", "LIST", "NLST", "MLSD");
    private static final Set<String> TRANSFERS = Set.of("RETR", "STOR", "STOU", "APPE");
    private final ServerEvents events;
    private final boolean requireTls;

    EventFtplet(ServerEvents events, boolean requireTls) { this.events = events; this.requireTls = requireTls; }

    @Override public FtpletResult onConnect(FtpSession session) {
        events.connected(remote(session));
        return FtpletResult.DEFAULT;
    }

    @Override public FtpletResult onDisconnect(FtpSession session) {
        if (session.getFileSystemView() instanceof ShareView view) view.finish(false);
        return FtpletResult.DEFAULT;
    }

    @Override public FtpletResult beforeCommand(FtpSession session, FtpRequest request) throws FtpException {
        if (!requireTls) return FtpletResult.DEFAULT;
        String command = command(request);
        if (LOGIN.contains(command) && !session.isSecure()) {
            session.write(new DefaultFtpReply(FtpReply.REPLY_530_NOT_LOGGED_IN, "This server requires TLS: use AUTH TLS before logging in"));
            return FtpletResult.SKIP;
        }
        if (DATA.contains(command) && !session.getDataConnection().isSecure()) {
            session.write(new DefaultFtpReply(PROTECTION_REQUIRED, "This server requires TLS: use PROT P before transferring"));
            return FtpletResult.SKIP;
        }
        return FtpletResult.DEFAULT;
    }

    @Override public FtpletResult afterCommand(FtpSession session, FtpRequest request, FtpReply reply) {
        if (TRANSFERS.contains(command(request)) && session.getFileSystemView() instanceof ShareView view)
            view.finish(reply != null && reply.getCode() == FtpReply.REPLY_226_CLOSING_DATA_CONNECTION);
        return FtpletResult.DEFAULT;
    }

    private static String command(FtpRequest request) {
        return request.getCommand() == null ? "" : request.getCommand().toUpperCase(Locale.ROOT);
    }

    private static String remote(FtpSession session) {
        InetSocketAddress address = session.getClientAddress();
        return address == null || address.getAddress() == null ? "" : ServerAddresses.hostText(address.getAddress());
    }
}
```

`engine/ftp/TlsConfiguration.java`:

```java
package dev.jasper.fileserver.engine.ftp;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import org.apache.ftpserver.ssl.ClientAuth;
import org.apache.ftpserver.ssl.SslConfiguration;

/** FtpServer's TLS settings over the shared certificate's context: TLS 1.2, FtpServer's default, and no client certificates. */
final class TlsConfiguration implements SslConfiguration {
    private final SSLContext context;

    TlsConfiguration(SSLContext context) { this.context = context; }

    @Override public SSLSocketFactory getSocketFactory() { return context.getSocketFactory(); }
    @Override public SSLContext getSSLContext() { return context; }
    @Override public SSLContext getSSLContext(String protocol) { return context; }
    @Override public String[] getEnabledCipherSuites() { return null; }
    @Override public String getEnabledProtocol() { return DEFAULT_ENABLED_PROTOCOL; }
    @Override public ClientAuth getClientAuth() { return ClientAuth.NONE; }
}
```

- [ ] **Step 5: Write `FtpEngine`**

```java
package dev.jasper.fileserver.engine.ftp;

import dev.jasper.fileserver.auth.ResolvedLogin;
import dev.jasper.fileserver.definition.PortRange;
import dev.jasper.fileserver.definition.ServerDefinition;
import dev.jasper.fileserver.engine.EngineFailure;
import dev.jasper.fileserver.engine.LoginThrottle;
import dev.jasper.fileserver.engine.ServerAddresses;
import dev.jasper.fileserver.engine.ServerEngine;
import dev.jasper.fileserver.engine.ServerEvents;
import dev.jasper.fileserver.engine.tls.TlsMaterial;
import dev.jasper.fileserver.share.ShareRoot;
import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.ftpserver.ConnectionConfigFactory;
import org.apache.ftpserver.DataConnectionConfigurationFactory;
import org.apache.ftpserver.FtpServer;
import org.apache.ftpserver.FtpServerFactory;
import org.apache.ftpserver.ftplet.FtpException;
import org.apache.ftpserver.ftplet.Ftplet;
import org.apache.ftpserver.listener.Listener;
import org.apache.ftpserver.listener.ListenerFactory;

/**
 * An FTP server for one share on Apache FtpServer, with explicit FTPS ({@code AUTH TLS}) when the definition uses TLS.
 * Passive data connections use the definition's range, or the default, on the address the control connection
 * arrived on. Active mode is allowed, but only back to the client's own address.
 */
public final class FtpEngine implements ServerEngine {
    static final int IDLE_SECONDS = 300;
    /** FtpServer logs every login and transfer at INFO; the activity log already shows them. Held so the level sticks. */
    private static final Logger FTP_SERVER_LOG = Logger.getLogger("org.apache.ftpserver");
    static { FTP_SERVER_LOG.setLevel(Level.WARNING); }

    private final ServerDefinition definition;
    private final ShareRoot share;
    private final ResolvedLogin login;
    private final ServerEvents events;
    private final LoginThrottle throttle;
    private final Supplier<List<ServerAddresses.Nic>> nics;
    private final PortRange passiveDefault;
    private final TlsMaterial tls;
    private FtpServer server;
    private int port;

    /** {@code tls} may be null when the definition does not use TLS. */
    public FtpEngine(ServerDefinition definition, ShareRoot share, ResolvedLogin login, ServerEvents events, LoginThrottle throttle,
                     Supplier<List<ServerAddresses.Nic>> nics, PortRange passiveDefault, TlsMaterial tls) {
        this.definition = definition; this.share = share; this.login = login; this.events = events; this.throttle = throttle;
        this.nics = nics; this.passiveDefault = passiveDefault; this.tls = tls;
    }

    @Override public synchronized void start() throws EngineFailure {
        if (server != null) return;
        if (definition.tls() && tls == null) throw new IllegalStateException("FTPS needs the TLS material");
        InetAddress address = ServerAddresses.bindAddress(definition.bind(), nics.get());
        TlsConfiguration ssl = definition.tls() ? new TlsConfiguration(tls.context()) : null;

        var data = new DataConnectionConfigurationFactory();
        data.setPassivePorts(definition.passiveRange().orElse(passiveDefault).token());
        data.setActiveIpCheck(true);
        data.setPassiveIpCheck(true);
        if (ssl != null) data.setSslConfiguration(ssl);

        var listeners = new ListenerFactory();
        if (!address.isAnyLocalAddress()) listeners.setServerAddress(address.getHostAddress());
        listeners.setPort(definition.port());
        listeners.setIdleTimeout(IDLE_SECONDS);
        listeners.setDataConnectionConfiguration(data.createDataConnectionConfiguration());
        if (ssl != null) {
            listeners.setSslConfiguration(ssl);
            listeners.setImplicitSsl(false);
        }
        Listener listener = listeners.createListener();

        var connections = new ConnectionConfigFactory();
        connections.setAnonymousLoginEnabled(login.isAnonymous());
        Map<String, Ftplet> ftplets = new LinkedHashMap<>();
        ftplets.put("jasper", new EventFtplet(events, definition.requireTls()));
        var factory = new FtpServerFactory();
        factory.addListener("default", listener);
        factory.setConnectionConfig(connections.createConnectionConfig());
        factory.setUserManager(new ShareUserManager(login, definition.writable(), throttle, events));
        factory.setFileSystem(new ShareFileSystem(share, events));
        factory.setFtplets(ftplets);
        FtpServer starting = factory.createServer();
        try {
            starting.start();
        } catch (FtpException | RuntimeException failure) {
            starting.stop();
            if (failure.getCause() instanceof IOException cause)
                throw EngineFailure.bind(cause, definition.port(), ServerAddresses.describe(address), definition.protocol());
            throw new EngineFailure("Could not start the FTP server: " + failure.getMessage(), failure);
        }
        server = starting;
        port = listener.getPort();
    }

    @Override public synchronized void stop() {
        if (server == null) return;
        server.stop();
        server = null;
    }

    @Override public synchronized List<URI> addresses() {
        if (server == null) return List.of();
        return ServerAddresses.urls(definition.protocol(), definition.tls(), ServerAddresses.reachable(definition.bind(), nics.get()), port, login.user());
    }

    /** The bound port; 0 before the first start. */
    public synchronized int port() { return port; }
}
```

FtpServer reports a bind failure as `FtpServerConfigurationException("Failed to bind…")` with the `IOException` as its cause. `EngineFailure.bind` turns that cause into the panel's message.

- [ ] **Step 6: Run the tests**

Run: `./gradlew :jasper-plugin-fileserver:test --tests 'dev.jasper.fileserver.engine.ftp.*'`
Expected: PASS. If an FTPS test fails during the handshake, read the server's JUL output (the test report's stderr) before changing anything. FtpServer's TLS goes through MINA's `SslFilter`, and its data connections through `SSLServerSocket`.

Then run `./gradlew :jasper-plugin-fileserver:check` and expect PASS.

- [ ] **Step 7: Commit**

```bash
git add plugins/fileserver
git commit -m "File Server: FTP and explicit FTPS on Apache FtpServer, with Require TLS and throttled logins

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 6: FTP in the editor — passive ports, Use TLS, Require TLS

**Files:**
- Modify: `plugins/fileserver/src/main/java/dev/jasper/fileserver/FileServerSettings.java`, `src/main/resources/settings.toml`
- Modify: `plugins/fileserver/src/main/java/dev/jasper/fileserver/runtime/Engines.java`, `FileServerPlugin.java` (factory arguments)
- Modify: `plugins/fileserver/src/main/java/dev/jasper/fileserver/ui/ServerRows.java`, `ui/ServerEditor.java`
- Test: `runtime/EnginesTest.java`, `ui/ServerRowsTest.java`, `ui/ServerEditorTest.java`, `FileServerPluginTest.java`

**Interfaces:**
- Consumes: `FtpEngine` from Task 5, `TlsMaterial` from Task 2, and `PortRange.parse` / `PortRange.token`.
- Produces:
  - `FileServerSettings.passivePorts` (`Setting<String>`, key `passive_ports`, default `"50000-50100"`) and `PortRange passiveRange()`.
  - `Engines.AVAILABLE = Set.of(Protocol.HTTP, Protocol.FTP)`.
  - `Engines.factory(LoginThrottle, Supplier<List<ServerAddresses.Nic>>, TlsMaterial, Supplier<PortRange> passiveDefault)` replaces the three-argument factory from Task 3.
  - `ServerRows.warnings` adds "Plain FTP sends the password unencrypted".
  - `ServerEditor` gains the package-private fields `useTls`, `requireTls` (`JCheckBox`) and `passive` (`JTextField`). Its constructor is unchanged.

- [ ] **Step 1: Write the failing tests**

In `EnginesTest.java`, replace the test with the following, and add the imports `dev.jasper.fileserver.definition.PortRange` and `dev.jasper.fileserver.engine.ftp.FtpEngine`:

```java
    @Test void theFactoryBuildsHttpAndFtpEngines(@TempDir Path folder) throws Exception {
        var factory = Engines.factory(new LoginThrottle(new TestClock()), List::of, new TlsMaterial(folder.resolve("tls"), new TestClock(), () -> ""),
            () -> new PortRange(50000, 50100));
        var events = new ServerEvents(new TestClock(), e -> { });
        var share = new ShareRoot(folder, true);
        assertThat(Engines.AVAILABLE).containsExactlyInAnyOrder(Protocol.HTTP, Protocol.FTP);
        assertThat(factory.create(ServerDefinition.create("h", Protocol.HTTP, folder), share, ResolvedLogin.anonymous(), events)).isInstanceOf(HttpEngine.class);
        assertThat(factory.create(ServerDefinition.create("f", Protocol.FTP, folder), share, ResolvedLogin.anonymous(), events)).isInstanceOf(FtpEngine.class);
        assertThatThrownBy(() -> factory.create(ServerDefinition.create("s", Protocol.SFTP, folder), share, ResolvedLogin.anonymous(), events))
            .isInstanceOf(EngineFailure.class).hasMessage("SFTP servers are not available in this version of File Server");
    }
```

In `ServerRowsTest.summarisesAccessAndWarnsAboutRiskyCombinations`, add:

```java
        assertThat(ServerRows.warnings(definition("e", Protocol.FTP, false, Bind.LOOPBACK, Login.SESSION))).containsExactly("Plain FTP sends the password unencrypted");
        assertThat(ServerRows.warnings(Protocol.FTP, true, Login.SESSION, false, Bind.LOOPBACK)).as("TLS on").isEmpty();
        assertThat(ServerRows.warnings(definition("f", Protocol.FTP, false, Bind.LOOPBACK, Login.ANONYMOUS))).as("no password to leak").isEmpty();
```

In `ServerEditorTest.java`, add the import `dev.jasper.fileserver.definition.PortRange`, and add:

```java
    ServerEditor ftpEditor(Optional<ServerDefinition> initial, boolean existing) {
        return new ServerEditor(List.of(), initial, existing, Set.of(Protocol.HTTP, Protocol.FTP), List.of(), false,
            id -> Optional.empty(), () -> CompletableFuture.completedFuture(Optional.empty()), () -> Optional.of(folder), false,
            saved::add, () -> cancelled = true);
    }

    @Test void ftpOffersTlsAndAPassiveRange() {
        var editor = ftpEditor(Optional.empty(), false);
        assertThat(editor.protocol.getSelectedItem()).isEqualTo(Protocol.HTTP);
        assertThat(editor.useTls.isEnabled()).as("HTTP can use TLS").isTrue();
        assertThat(editor.requireTls.isEnabled()).as("only FTP requires TLS").isFalse();
        assertThat(editor.passive.isEnabled()).as("only FTP has passive ports").isFalse();
        editor.protocol.setSelectedItem(Protocol.FTP);
        assertThat(editor.port.getText()).isEqualTo("2121");
        assertThat(editor.session.isSelected()).as("no Vault: a session password").isTrue();
        assertThat(editor.passive.isEnabled()).isTrue();
        assertThat(editor.warnings.getText()).isEqualTo("⚠ Plain FTP sends the password unencrypted");
        assertThat(editor.requireTls.isEnabled()).as("needs Use TLS").isFalse();
        editor.useTls.doClick();
        assertThat(editor.requireTls.isEnabled()).isTrue();
        assertThat(editor.warnings.getText()).isEqualTo(" ");
        editor.requireTls.doClick();
        editor.name.setText("drop");
        editor.folder.setText(folder.toString());
        editor.passive.setText("60000-50000");
        editor.save.doClick();
        assertThat(editor.message.getText()).isEqualTo("A port range runs from a lower to a higher port, 1 to 65535");
        assertThat(saved).isEmpty();
        editor.passive.setText(" 51000-51010 ");
        editor.save.doClick();
        assertThat(saved).singleElement().satisfies(definition -> {
            assertThat(definition.protocol()).isEqualTo(Protocol.FTP);
            assertThat(definition.tls()).isTrue();
            assertThat(definition.requireTls()).isTrue();
            assertThat(definition.passiveRange()).contains(new PortRange(51000, 51010));
            assertThat(definition.login()).isEqualTo(Login.SESSION);
        });
        editor.useTls.doClick();
        assertThat(editor.requireTls.isSelected()).as("Require TLS goes with Use TLS").isFalse();
        assertThat(editor.requireTls.isEnabled()).isFalse();
    }

    @Test void anEmptyPassiveRangeUsesTheSetting() {
        var editor = ftpEditor(Optional.empty(), false);
        editor.protocol.setSelectedItem(Protocol.FTP);
        editor.name.setText("plain");
        editor.folder.setText(folder.toString());
        editor.save.doClick();
        assertThat(saved).singleElement().satisfies(definition -> {
            assertThat(definition.passiveRange()).isEmpty();
            assertThat(definition.tls()).isFalse();
        });
    }

    @Test void editingAnFtpServerKeepsItsTlsAndRange() {
        var original = new ServerDefinition(UUID.randomUUID(), "drop", Protocol.FTP, folder, Bind.LOOPBACK, 2121,
            Optional.of(new PortRange(52000, 52005)), true, Login.SESSION, List.of(), true, true, false);
        var editor = ftpEditor(Optional.of(original), true);
        assertThat(editor.passive.getText()).isEqualTo("52000-52005");
        assertThat(editor.useTls.isSelected()).isTrue();
        assertThat(editor.requireTls.isSelected()).isTrue();
        assertThat(editor.requireTls.isEnabled()).isTrue();
        editor.save.doClick();
        assertThat(saved).singleElement().isEqualTo(original);
    }
```

In `FileServerPluginTest.registersItsPanelActionsSettingsAndAHiddenStatus`, add one more `anySatisfy` to the `declared(...)` chain:

```java
                .anySatisfy(line -> assertThat(line).startsWith("passive_ports"))
```

- [ ] **Step 2: Run them to see them fail**

Run: `./gradlew :jasper-plugin-fileserver:test --tests 'dev.jasper.fileserver.runtime.EnginesTest' --tests 'dev.jasper.fileserver.ui.*' --tests 'dev.jasper.fileserver.FileServerPluginTest'`
Expected: compilation fails, because there is no four-argument factory and no `useTls`, `requireTls` or `passive`.

- [ ] **Step 3: Add the setting**

In `FileServerSettings.java`, add the import `dev.jasper.fileserver.definition.PortRange`, the field `final Setting<String> passivePorts;` and, at the end of the constructor:

```java
        passivePorts = settings.declare(SettingSpec.of("passive_ports", SettingKind.text(FileServerSettings::validRange,
                "Use a port range such as 50000-50100."), "50000-50100")
            .label("Default FTP passive port range")
            .description("FTP data connections use these ports unless a server sets its own range. Applies to servers started afterwards.")
            .section("File Servers").keywords("ftp", "passive", "port", "firewall"));
```

Add the following, too:

```java
    /** The range in force; {@link Setting#get()} falls back to the default when the file holds something invalid. */
    PortRange passiveRange() { return PortRange.parse(passivePorts.get()); }

    static boolean validRange(String text) {
        try { PortRange.parse(text); return true; }
        catch (IllegalArgumentException invalid) { return false; }
    }
```

Append to `src/main/resources/settings.toml`:

```toml
# passive_ports = "50000-50100" # default FTP passive data ports; a server can set its own range
```

- [ ] **Step 4: Serve FTP from the factory**

Replace `runtime/Engines.java`:

```java
package dev.jasper.fileserver.runtime;

import dev.jasper.fileserver.definition.PortRange;
import dev.jasper.fileserver.definition.Protocol;
import dev.jasper.fileserver.engine.EngineFailure;
import dev.jasper.fileserver.engine.LoginThrottle;
import dev.jasper.fileserver.engine.ServerAddresses;
import dev.jasper.fileserver.engine.ftp.FtpEngine;
import dev.jasper.fileserver.engine.http.HttpEngine;
import dev.jasper.fileserver.engine.tls.TlsMaterial;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

/** Which protocols this version serves, and the factory that builds their engines. */
public final class Engines {
    public static final Set<Protocol> AVAILABLE = Set.of(Protocol.HTTP, Protocol.FTP);

    private Engines() { }

    /** {@code passiveDefault} is read each time an FTP engine is built, so a changed setting applies to the next start. */
    public static ServerRegistry.EngineFactory factory(LoginThrottle throttle, Supplier<List<ServerAddresses.Nic>> nics, TlsMaterial tls,
                                                       Supplier<PortRange> passiveDefault) {
        return (definition, share, login, events) -> switch (definition.protocol()) {
            case HTTP -> new HttpEngine(definition, share, login, events, throttle, nics, tls);
            case FTP -> new FtpEngine(definition, share, login, events, throttle, nics, passiveDefault.get(), tls);
            case SFTP, TFTP -> throw new EngineFailure(definition.protocol().label() + " servers are not available in this version of File Server");
        };
    }
}
```

In `FileServerPlugin.start`, the settings are created before the registry. Change the factory call to:

```java
        registry = new ServerRegistry(Engines.factory(new LoginThrottle(Clock.systemUTC()), environment.nics(), tls, settings::passiveRange), logins,
            context.background(), environment.ui(), Clock.systemUTC(), settings.activityEntries::get, this::observe);
```

- [ ] **Step 5: Warn about plain FTP**

In `ServerRows.warnings(Protocol, boolean, Login, boolean, Bind)`, replace the HTTP line with:

```java
        if ((protocol == Protocol.HTTP || protocol == Protocol.FTP) && !tls && !(login instanceof Login.Anonymous))
            warnings.add("Plain " + protocol.label() + " sends the password unencrypted");
```

- [ ] **Step 6: Add the FTP and TLS controls to the editor**

In `ServerEditor.java`:

1. Add the import `dev.jasper.fileserver.definition.PortRange`, and the fields:

```java
    final JCheckBox useTls = new JCheckBox("Use TLS"), requireTls = new JCheckBox("Require TLS (refuse logins and transfers without it)");
    final JTextField passive = new JTextField(12);
```

2. In the `if (initial.isPresent())` branch, after `selectBind(definition.bind());`, add:

```java
            useTls.setSelected(definition.tls());
            requireTls.setSelected(definition.requireTls());
            passive.setText(definition.passiveRange().map(PortRange::token).orElse(""));
```

3. In the form, replace `row(form, at, "Port", port);` with the first block below, and insert the second block just before `row(form, at, "", warnings);`, so the warnings stay last:

```java
        row(form, at, "Port", port);
        row(form, at, "Passive ports", line(passive, label("FTP only; empty uses the range in Settings")));
```

```java
        row(form, at, "Security", useTls);
        row(form, at, "", requireTls);
```

4. Register listeners next to the others:

```java
        useTls.addActionListener(event -> refresh());
        requireTls.addActionListener(event -> refresh());
```

5. In `build()`, replace the final `return` with:

```java
        boolean tlsOn = chosen.allowsTls() && useTls.isSelected();
        boolean requireOn = chosen == Protocol.FTP && tlsOn && requireTls.isSelected();
        String range = passive.getText().strip();
        Optional<PortRange> passiveRange = chosen == Protocol.FTP && !range.isEmpty() ? Optional.of(PortRange.parse(range)) : Optional.empty();
        return new ServerDefinition(existing ? base.id() : UUID.randomUUID(), nameText, chosen, folderPath, selectedBind(), portValue,
            passiveRange, writable.isSelected(), login, same ? base.authorizedKeys() : List.of(), tlsOn, requireOn, autostart.isSelected());
```

`PortRange.parse` throws `IllegalArgumentException` with the editor's message, and the Save listener already shows that message.

6. In `refresh()`, before the warnings are computed, add the following. Then replace the `boolean tls = initial...` line with `boolean tls = chosen.allowsTls() && useTls.isSelected();`.

```java
        boolean ftp = chosen == Protocol.FTP;
        passive.setEnabled(ftp);
        useTls.setEnabled(chosen.allowsTls());
        if (!chosen.allowsTls()) useTls.setSelected(false);
        requireTls.setEnabled(ftp && useTls.isSelected());
        if (!requireTls.isEnabled()) requireTls.setSelected(false);
```

- [ ] **Step 7: Run the tests**

Run: `./gradlew :jasper-plugin-fileserver:check`
Expected: PASS. `editingKeepsTheIdAndHiddenSettingsAndRefusesTakenNames` still passes, because its HTTP definition's `tls = true` now comes from the checkbox.

- [ ] **Step 8: Commit**

```bash
git add plugins/fileserver
git commit -m "File Server: FTP servers in the editor, with passive ports, Use TLS and Require TLS

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 7: Fingerprints in the panel, Serve Current Folder over FTP, version 0.2.0

**Files:**
- Modify: `plugins/fileserver/src/main/java/dev/jasper/fileserver/ui/FileServersPanel.java`
- Modify: `plugins/fileserver/src/main/java/dev/jasper/fileserver/FileServerPlugin.java`
- Modify: `plugins/fileserver/src/main/resources/plugin.toml`
- Modify: `jasper-app/src/test/java/dev/jasper/app/plugins/PluginZipsTest.java`
- Test: `ui/FileServersPanelTest.java`, `ui/UiTestAccess.java`, `FileServerPluginTest.java`

**Interfaces:**
- Consumes: `TlsMaterial` (`fingerprint()`, `cachedFingerprint()`, `regenerate()`), `FileServerPlugin.tls()` (Task 3), `ConfirmPanel`, and the plugin's `confirm(...)` helper.
- Produces:
  - `FileServersPanel.Actions` gains a ninth component, `Consumer<UUID> fingerprints`.
  - `FileServersPanel.setTlsFingerprint(Optional<String>)`.
  - Package-private `FileServersPanel.cardTls` (`JTextArea`, built with Task 1's `note(...)`).
  - The menu item "Show Fingerprints…", enabled when the server uses TLS.
  - `FileServerPlugin.SERVE_FTP = ID + ".serve-ftp"`, titled "Serve Current Folder over FTP".
  - `FileServerPlugin.showFingerprints(WindowHandle, UUID)` (package-private).
  - `UiTestAccess.cardTls(FileServersPanel)`.

- [ ] **Step 1: Write the failing tests**

In `FileServersPanelTest.java`:

1. Add a ninth consumer to `actions`: `, id -> events.add("fingerprints " + name(id))` after the `clear` consumer.
2. In `menusOfferWhatMakesSense`, append `"Show Fingerprints… (off)"` to each of the three `containsExactly(...)` menu lists, because none of those servers uses TLS.
3. Add:

```java
    @Test void aTlsServerShowsTheCertificateFingerprint() {
        var panel = panel();
        var definition = new ServerDefinition(UUID.randomUUID(), "secure", Protocol.HTTP, HOME.resolve("work"), Bind.LOOPBACK, 8443,
            Optional.empty(), false, Login.ANONYMOUS, List.of(), true, false, false);
        views.add(new ServerRegistry.View(definition, false, ServerState.STOPPED, List.of(), Optional.empty(), false));
        view("plain", ServerState.STOPPED, false, false, Bind.LOOPBACK, List.of(), Optional.empty());
        panel.setServers(views, Optional.empty());
        assertThat(panel.cardTls.isVisible()).isTrue();
        assertThat(panel.cardTls.getText()).isEqualTo("TLS certificate: created when the server first starts");
        panel.setTlsFingerprint(Optional.of("AB:CD"));
        assertThat(panel.cardTls.getText()).isEqualTo("TLS certificate SHA-256 AB:CD");
        assertThat(panel.cardAccess.getText()).isEqualTo("HTTP :8443 · read-only · anonymous · TLS · This computer only");
        assertThat(titles(panel.menuFor(0))).contains("Show Fingerprints…");
        click(panel.menuFor(0), "Show Fingerprints…");
        assertThat(events).containsExactly("fingerprints secure");
        panel.select(views.get(1).definition().id());
        assertThat(panel.cardTls.isVisible()).as("no TLS, no fingerprint").isFalse();
    }
```

In `UiTestAccess.java`, add the following (`javax.swing.JTextArea` is already imported):

```java
    public static JTextArea cardTls(FileServersPanel panel) { return panel.cardTls; }
```

In `FileServerPluginTest.java`, change `INFO`'s version to `"0.2.0"`, and add:

```java
    @Test void servesTheFocusedFolderOverFtpWithASessionPassword() throws Exception {
        Path project = Files.createDirectories(home.resolve("project"));
        try (var host = new FakePluginHost(root)) {
            var plugin = plugin(port -> port != 2121);
            host.start(INFO, Set.of(), Set.of(), plugin);
            settle(host);
            assertThat(host.actions()).contains("dev.jasper.fileserver.serve-ftp|Serve Current Folder over FTP|true");
            UUID window = host.addTerminalWindow(), tab = host.addTerminalTab(window, "zsh");
            UUID local = host.addTerminalPane(tab, pane(Optional.of(project)));
            host.openPanel(FileServerPlugin.PANEL, window);
            assertThat(host.invoke(FileServerPlugin.SERVE_FTP, window, local)).isTrue();
            await(host, () -> plugin.registry().servers().stream().noneMatch(v -> v.state().phase() == Phase.STARTING), "the start settles");
            var served = plugin.registry().servers().getFirst();
            assertThat(served.temporary()).isTrue();
            assertThat(served.definition().name()).isEqualTo("project (FTP)");
            assertThat(served.definition().protocol()).isEqualTo(Protocol.FTP);
            assertThat(served.definition().port()).as("2121 was taken").isEqualTo(2122);
            assertThat(served.definition().bind()).isEqualTo(Bind.LOOPBACK);
            assertThat(served.definition().writable()).isFalse();
            assertThat(served.definition().login()).isEqualTo(Login.SESSION);
            plugin.registry().stopAll();
            await(host, () -> plugin.registry().active() == 0, "everything stops");
        }
    }

    @Test void showsTheCertificateFingerprintAndRegeneratesIt() throws Exception {
        try (var host = new FakePluginHost(root)) {
            var plugin = plugin();
            host.start(INFO, Set.of(), Set.of(), plugin);
            settle(host);
            UUID window = host.addTerminalWindow();
            var panel = (FileServersPanel) host.openPanel(FileServerPlugin.PANEL, window);
            var base = http("secure", home, Ports.free(), Login.ANONYMOUS, false);
            var secure = new ServerDefinition(base.id(), base.name(), Protocol.HTTP, home, Bind.LOOPBACK, base.port(), Optional.empty(), false,
                Login.ANONYMOUS, List.of(), true, false, false);
            plugin.store().put(secure);
            settle(host);
            assertThat(UiTestAccess.cardTls(panel).getText()).isEqualTo("TLS certificate: created when the server first starts");
            UiTestAccess.toggle(panel).doClick();
            await(host, () -> plugin.registry().server(secure.id()).orElseThrow().state().phase() == Phase.RUNNING, "the HTTPS server runs");
            String fingerprint = plugin.tls().cachedFingerprint().orElseThrow();
            assertThat(UiTestAccess.cardTls(panel).getText()).isEqualTo("TLS certificate SHA-256 " + fingerprint);
            assertThat(plugin.registry().server(secure.id()).orElseThrow().addresses()).containsExactly(URI.create("https://127.0.0.1:" + secure.port() + "/"));
            click(UiTestAccess.menuFor(panel, 0), "Show Fingerprints…");
            await(host, () -> plugin.currentConfirm() != null, "the fingerprint dialog opens");
            assertThat(host.windows()).containsExactly("dialog|Fingerprints|true");
            assertThat(UiTestAccess.message(plugin.currentConfirm()).getText()).isEqualTo("TLS certificate (SHA-256): " + fingerprint);
            UiTestAccess.confirm(plugin.currentConfirm()).doClick();
            settle(host);
            assertThat(host.windows()).containsExactly("dialog|Regenerate the TLS certificate?|true");
            UiTestAccess.confirm(plugin.currentConfirm()).doClick();
            await(host, () -> plugin.tls().cachedFingerprint().filter(next -> !next.equals(fingerprint)).isPresent(), "a new certificate");
            settle(host);
            assertThat(UiTestAccess.cardTls(panel).getText()).isEqualTo("TLS certificate SHA-256 " + plugin.tls().cachedFingerprint().orElseThrow());
            plugin.registry().stopAll();
            await(host, () -> plugin.registry().active() == 0, "everything stops");
        }
    }
```

In `PluginZipsTest.java`, replace the File Server branch with:

```java
            else if (descriptor.id().equals("dev.jasper.fileserver"))
                assertThat(jars).as("File Server's libraries travel with it")
                    .contains("tomlj-1.1.1.jar", "ftpserver-core-1.2.1.jar", "bcpkix-jdk18on-1.85.jar");
```

- [ ] **Step 2: Run them to see them fail**

Run: `./gradlew :jasper-plugin-fileserver:test`
Expected: compilation fails, because `Actions` has eight components and there is no `cardTls` or `SERVE_FTP`. `tls()` already exists from Task 3.

- [ ] **Step 3: Show the fingerprint in the panel**

In `FileServersPanel.java`:

1. Extend `Actions`:

```java
    public record Actions(Consumer<UUID> toggle, Consumer<Optional<UUID>> edit, Consumer<UUID> duplicate, Consumer<UUID> delete,
                          Consumer<UUID> reveal, Consumer<String> copy, Consumer<UUID> copyPassword, Consumer<UUID> clearActivity,
                          Consumer<UUID> fingerprints) { }
```

2. Add the fields `final JTextArea cardTls = note("");` and `private Optional<String> tlsFingerprint = Optional.empty();`.
3. In the constructor's card loop, add `cardTls` after `cardWarnings`: `List.<JComponent>of(cardName, cardState, cardFolder, cardAccess, cardWarnings, cardTls, addresses)`.
4. Add:

```java
    /** The shared certificate's fingerprint, or empty before it exists; shown for servers that use TLS. */
    public void setTlsFingerprint(Optional<String> fingerprint) {
        if (fingerprint.equals(tlsFingerprint)) return;
        tlsFingerprint = fingerprint;
        refreshCard();
    }
```

5. In `refreshCard()`, after the `cardWarnings` lines, add:

```java
            cardTls.setText(tlsFingerprint.map(fingerprint -> "TLS certificate SHA-256 " + fingerprint)
                .orElse("TLS certificate: created when the server first starts"));
            cardTls.setVisible(definition.tls());
```

6. In `menuFor`, after the Copy Password item, add:

```java
        var fingerprints = item("Show Fingerprints…", () -> actions.fingerprints().accept(id));
        fingerprints.setEnabled(view.definition().tls());
        menu.add(fingerprints);
```

- [ ] **Step 4: Wire the plugin**

In `FileServerPlugin.java`:

1. Add `public static final String SERVE_FTP = ID + ".serve-ftp";` and the import `dev.jasper.fileserver.engine.EngineFailure`.
2. After the `SERVE_HTTP` registration, add:

```java
        context.actions().register(ActionSpec.of(SERVE_FTP, "Serve Current Folder over FTP").withIcon(icon)
                .withKeywords(List.of("file server", "share", "serve", "ftp", "folder")),
            invoked -> serve(invoked, Protocol.FTP));
```

`serve` already takes the protocol. `ServerDefinition.create` gives FTP a session password, as the spec asks.

3. At the end of `start`, read an existing certificate's fingerprint off the UI thread. This never creates a certificate:

```java
        context.background().execute(() -> {
            try { tls.fingerprint(); }
            catch (EngineFailure unreadable) { /* reported when a TLS server starts or the fingerprints are shown */ }
            environment.ui().execute(() -> { if (!stopped) refreshPanels(); });
        });
```

4. In `createPanel`, pass the ninth consumer `id -> showFingerprints(window, id)` after `registry::clearActivity`. Before `panel.setServers(...)`, add `panel.setTlsFingerprint(tls.cachedFingerprint());`.
5. In `refreshPanels()`, add `panel.setTlsFingerprint(tls.cachedFingerprint());` as the loop's first line. `serversChanged` already calls `refreshPanels()`, so a certificate created by a starting server appears once that server runs.
6. Add:

```java
    /** Shows the shared certificate's fingerprint, offering to regenerate it; the file I/O runs off the UI thread. */
    void showFingerprints(WindowHandle window, UUID id) {
        if (registry.server(id).isEmpty()) return;
        context.background().execute(() -> {
            String text;
            try {
                text = tls.fingerprint().map(fingerprint -> "TLS certificate (SHA-256): " + fingerprint)
                    .orElse("No TLS certificate yet: Jasper creates one when a TLS server first starts.");
            } catch (EngineFailure unreadable) {
                text = unreadable.getMessage() + ". Regenerate it to make a new one.";
            }
            String shown = text;
            environment.ui().execute(() -> {
                if (stopped || !window.isOpen()) return;
                refreshPanels();
                confirm(window, "Fingerprints", shown, "Regenerate Certificate…", "Close", () -> confirmRegenerate(window), () -> { });
            });
        });
    }

    private void confirmRegenerate(WindowHandle window) {
        confirm(window, "Regenerate the TLS certificate?",
            "Clients that trusted the old certificate will warn about the new one. Running TLS servers keep the old one until they restart.",
            "Regenerate", "Cancel", () -> context.background().execute(() -> {
                String failure = null;
                try { tls.regenerate(); }
                catch (EngineFailure unwritable) { failure = unwritable.getMessage(); }
                String error = failure;
                environment.ui().execute(() -> {
                    if (stopped) return;
                    if (error != null) context.notices().error(error);
                    refreshPanels();
                });
            }), () -> { });
    }
```

- [ ] **Step 5: Version and description**

In `plugin.toml`, set `version = "0.2.0"` and the following description:

```toml
description = "Share a local folder with other computers over HTTP, HTTPS, FTP or FTPS: named servers in a File Servers panel, read-only and local-only by default, with an activity log, Vault logins and Serve Current Folder."
```

- [ ] **Step 6: Run the tests**

Run: `./gradlew :jasper-plugin-fileserver:check :jasper-app:test --tests 'dev.jasper.app.plugins.PluginZipsTest'`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add plugins/fileserver jasper-app/src/test/java/dev/jasper/app/plugins/PluginZipsTest.java
git commit -m "File Server 0.2.0: certificate fingerprints in the panel and Serve Current Folder over FTP

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 8: Documentation, status and the full check

**Files:**
- Modify: `docs/file-server.md`, `docs/README.md`, `docs/STATUS.md`
- Modify: `docs/superpowers/plans/2026-09-30-file-server-fs2.md` (status banner)

- [ ] **Step 1: Update the user guide**

In `docs/file-server.md`:
- Change the opening to say File Server shares a folder over HTTP, HTTPS, FTP and FTPS, with SFTP and TFTP to follow.
- In "Safe by default", add that FTP's default port is 2121.
- Add the following sections before "Quitting":

```markdown
## FTP and FTPS

An FTP server shares the folder with any FTP client (FileZilla, Cyberduck, `ftp`, `curl`). New FTP servers use a Vault
account when the Credential Vault is installed and a session password otherwise; Anonymous is allowed too.

- **Passive ports.** Data connections use the server's own range, or the File Server setting "Default FTP passive
  port range" (50000–50100) when the range is empty. Open those ports in your firewall as well as the server's port.
- **Active mode** works, but only back to the client's own address.
- **Use TLS** turns on explicit FTPS: clients connect to the same port and send `AUTH TLS` (FileZilla calls this
  "Require explicit FTP over TLS"). Implicit FTPS (port 990) is not offered. **Require TLS** refuses a login before
  `AUTH TLS` and any transfer or listing without `PROT P`.
- Without TLS the password crosses the network in plain text; the panel and the editor warn about it.
- An upload replaces the file only once it has arrived completely; a failed or interrupted transfer leaves the old
  file. Resuming an upload part-way through is refused; `APPE` appends to the end of a file.
- `..` stops at the shared folder: `cd ..` at the top stays at `/`.

## HTTPS and the certificate

**Use TLS** on an HTTP server serves HTTPS. HTTPS and FTPS share one self-signed certificate, which File Server
creates the first time a TLS server starts and keeps in `data/tls/` (owner-only). Browsers warn about a self-signed
certificate; compare the fingerprint before you accept it. The server card shows the certificate's SHA-256
fingerprint. **Show Fingerprints…** in a server's menu shows it too, and offers **Regenerate Certificate…**. A new
certificate makes clients that trusted the old one warn again; running servers keep the old one until they restart.
```

- In "Files", add `data/tls/server.p12` and `data/tls/password`, and the `passive_ports` setting.

In `docs/README.md`, change the File Server line to: "share a folder over HTTP, HTTPS, FTP and FTPS from the File Servers panel (SFTP and TFTP follow)."

- [ ] **Step 2: Update `docs/STATUS.md`**

1. In the File Server FS-1 entry, replace "a failed `HttpEngine.start` releases its server and pool." with "a failed `HttpEngine.start` releases its pool (FS-2 also binds only after the setup succeeds)."
2. Add a new top entry:

```markdown
- **File Server FS-2: FTP/FTPS and TLS** (branch `claude/file-server-fs2`, plan
  `docs/superpowers/plans/2026-09-30-file-server-fs2.md`): File Server 0.2.0 serves FTP with explicit FTPS (Apache
  FtpServer 1.2.1) and HTTPS, on one self-signed EC certificate in `data/tls/` (BouncyCastle; fingerprint on the server
  card, Show Fingerprints… with Regenerate). Require TLS refuses `USER`/`PASS` before `AUTH TLS` (530) and data
  commands without `PROT P` (521). FTP paths stop `..` at the share root; uploads go through `ShareRoot.upload` and
  commit only on a 226 reply; a non-zero offset is accepted only at the file's end. Logins share `LoginThrottle` (a
  waiting address is refused unchecked). New setting `passive_ports` (the spec's `passive-ports`, renamed to match
  the plugin's other keys). Serve Current Folder over FTP (session password). FS-1 leftovers fixed: `HttpEngine`
  binds after its setup, replaced files keep their POSIX permissions, two event-ordering test races, and the panel's
  empty hint wraps with the divider hidden until a server is selected. GUI checks pending: FileZilla/Cyberduck over
  FTP and explicit FTPS, a browser over HTTPS (certificate warning, fingerprint match), Require TLS refusing a plain
  client, the editor's TLS and passive-port controls, Show Fingerprints and Regenerate, the empty panel's hint.
```

Add the exact test counts from `*/build/test-results/test/` after Step 3.

- [ ] **Step 3: Run the full check and the hygiene script**

Run: `./gradlew check`
Expected: BUILD SUCCESSFUL. Read the File Server and app test counts from the XML reports, and put them in the STATUS entry.

Run the source hygiene script from `AGENTS.md`. Expected: no output.

- [ ] **Step 4: Update the plan banner**

Under the plan's title, add a status line: "**Status:** executed on `claude/file-server-fs2` (date), `./gradlew check` green." List any deviations from the plan text, with the reason for each.

- [ ] **Step 5: Commit**

```bash
git add docs
git commit -m "File Server FS-2: documentation and status

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```
