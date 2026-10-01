# File Server FS-3: SFTP — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add SFTP to the File Server plugin on Apache MINA sshd 2.19.0. This covers a persistent Ed25519 host key, password and authorized-key logins through the shared login throttle, a jail that routes every file operation through `ShareRoot`, authorized keys in the editor, the host key's fingerprint in the panel, and Serve Current Folder over SFTP.

**Architecture:**
- A new `engine.sftp` package wraps sshd. `HostKey` owns `data/hostkey`.
- `SftpEngine` configures an `SshServer` with only the SFTP subsystem. It has no shell, exec, forwarding or keyboard-interactive login.
- `ShareAccessor` is an `SftpFileSystemAccessor`. It maps every client path through the shared `engine.ClientPaths` rules and `ShareRoot.locate`. It performs every open, listing, stat, mkdir, remove, rename and attribute change through `ShareRoot` by client path, and refuses links and unvetted extensions.
- `SftpActivity`, an `SftpEventListener` plus session listener, reports logins, transfers and changes. It also marks a normally closed upload for commit.
- `auth.AuthorizedKeys` validates OpenSSH key lines without vendor types. The editor, panel and plugin gain the SFTP options.

**Tech Stack:** Java 25 (JBR), Gradle wrapper, Swing, Apache MINA sshd 2.19.0 (`sshd-core`, `sshd-sftp`; `sshd-common` comes with them), BouncyCastle `bcprov-jdk18on` 1.85.2 (already bundled; sshd uses it for Ed25519), slf4j-jdk14 2.0.13 (already bundled).

**Spec:** `docs/superpowers/specs/2026-09-30-jasper-file-server-design.md` (approved 2026-09-30). This plan implements its "FS-3" delivery. Verified sshd 2.19.0 behaviour is summarised under "sshd facts" below. Tasks rely on those facts, so do not re-derive them.

## Global Constraints

- Work on branch `claude/file-server-fs3` in `.worktrees/file-server-fs3`, created from `main` (which includes this plan). Never commit on `main`. Verify the branch (`git branch --show-current`) before every commit, because other sessions switch branches in the main checkout. Never use `git stash`.
- End every commit message with `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.
- Use `./gradlew` only; the plugin's project is `:jasper-plugin-fileserver`. Tests are headless and bind only to `127.0.0.1`. Never launch the GUI; GUI checks go to the user. Never push.
- The plugin compiles against the SDK and, optionally, `dev.jasper.vault.api`. Vendor types stay inside `engine.*` sub-packages: sshd types only in `engine.sftp`, FtpServer only in `engine.ftp`, BouncyCastle only in `engine.tls` and in `engine.sftp`'s security registrar.
- File Server becomes version `0.3.0` with `sdk = ">=0.11.0, <0.12"` (unchanged).
- SFTP defaults, from the spec: port 2222, read-only, bind `loopback`. A new SFTP server's login is a Vault account when the Vault is installed, otherwise a session password. "SFTP needs a password or authorized keys" (already enforced by `ServerDefinition`).
- Host key: Ed25519, kept in `data/hostkey` (OpenSSH private key format, owner-only, written by sshd). Its fingerprint is shown in OpenSSH form: `SHA256:` followed by unpadded base64.
- Panel placement is out of scope: do not change `PanelSpec`, `Anchor` or the app's panel code (the user is handling placement in another session).
- Source hygiene: no raw control, private-use or unpaired-surrogate characters in Java source (AGENTS.md). Labels that show user or client text set `html.disable` before their text.
- Threading: the registry, panels and dialogs live on the UI thread. Engines start and stop on `context.background()`. `HostKey` does file I/O and must not be called on the UI thread, except `cachedFingerprint()`.

## Decisions made while planning (record in `docs/STATUS.md` and this plan's banner)

1. **The SFTP jail is in the accessor, on the native file system.** sshd's `RootedFileSystem` stops `..` but follows symbolic links that already point outside the root. It also runs about 25 `Files.*` calls directly on resolved paths. So:
   - `ShareAccessor.resolveLocalFilePath` turns every remote path into a client path, with `..` stopping at `/` (shared with FTP as `engine.ClientPaths`). It returns `ShareRoot.locate(clientPath)`: the real path of the entry's folder, plus the entry's own name, unfollowed.
   - Every content operation then goes back through `ShareRoot` by client path: open, list, stat-following-links, mkdir, remove, rename and attribute changes. `ShareRoot` refuses outward links and writes to a read-only share.
   - Paths outside the share are refused. At the root, `..` reports the root's own attributes.
   - Reading or creating links (`READLINK`, `SYMLINK`, `LINK`, `hardlink@openssh.com`) is refused.
   - Only the extensions `posix-rename@openssh.com`, `fsync@openssh.com`, `limits@openssh.com` and `version-select` run. All others (`copy-file`, `copy-data`, `md5-hash`, `check-file-name`, `space-available`, …) answer "operation unsupported".
   - Replies name client paths only. `putRemoteFileName` rewrites the host path that `REALPATH` would send.
2. **SFTP uploads.** An `OPEN` that truncates, creates exclusively, or creates a missing file writes to `ShareRoot.upload` (a temporary file). It commits on a normal `CLOSE`. A dropped connection or a server stop abandons it. Any other write `OPEN` writes in place, at any offset, through `ShareRoot.openWriteChannel`. That covers resuming a file, `APPEND` (sshd appends at the channel's size) and read-write opens.
3. **SFTP logins.**
   - Passwords go through `LoginThrottle` exactly as FTP's do: a waiting or locked-out address is refused without the check, and a wrong password counts as a failure.
   - Offered public keys that are not authorized do not count as failures, because SSH agents offer several keys in turn. A locked-out address is still refused keys.
   - A server with a password login accepts keys only for that user name. A keys-only server, whose login is `anonymous` with authorized keys, accepts any user name.
   - Successful logins are recorded once, from the session's `Authenticated` event, because sshd calls the key authenticator twice per login.
   - Keyboard-interactive login is off. Exec, shell and every kind of forwarding are refused. `MAX_AUTH_REQUESTS` is 10.
4. **Attribute changes.** On a writable share, `SETSTAT` may change modification and access times and POSIX permissions. Owner, group and ACL changes are always refused. On a read-only share every change is refused.
5. **Host key.** It is generated on the first SFTP start with `SimpleGeneratorHostKeyProvider` (`setAlgorithm("Ed25519")`, never `"EdDSA"`, which fails silently with BouncyCastle only). sshd replaces an unreadable key file with a new key: this is recorded as a known behaviour, and there is no "regenerate host key" action. Like Remote, the engine registers this plugin's own BouncyCastle provider with sshd, so it never uses a JVM-global `BC` that belongs to another plugin.
6. **`AuthorizedKeys` is vendor-free.** It lives in `auth` and validates OpenSSH lines by hand: known key type, base64 blob whose embedded type matches, optional comment. Lines with options (`command=`, `from=`, `restrict`, …) are refused, as the spec says. `engine.sftp` turns stored lines into `PublicKey`s with sshd.
7. **Editor "Keys only".** For SFTP the Anonymous choice reads "Keys only (no password)" and stores `Login.ANONYMOUS`. `ServerDefinition` already requires authorized keys for it.
8. **Fingerprints.** The server card of an SFTP server shows "SSH host key SHA256:…". Show Fingerprints… is enabled for SFTP servers too. For them it shows the host key with Copy Fingerprint and Close, and offers no regenerate action.
9. **Shared client paths.** FS-2's `engine.ftp.FtpPaths` moves to `engine.ClientPaths` (public), and FTP uses it unchanged.

## sshd facts (verified against 2.19.0 sources and by running them; do not re-derive)

- `SshServer.setUpDefaultServer()` is not secure by default:
  - Its public-key authenticator reads the OS user's `~/.ssh/authorized_keys`.
  - Keyboard-interactive mirrors the password authenticator.
  - The default file system is the whole disk.
  - Always set `setPublickeyAuthenticator`, `setUserAuthFactories(...)` (explicit list), `setKeyboardInteractiveAuthenticator(null)` and `setFileSystemFactory(...)`.
- Forwarding is rejected by default (`RejectAllForwardingFilter`). With `setShellFactory(null)` and `setCommandFactory(null)`, shell and exec requests fail. A client sees that only if it asks for a reply (`CoreModuleProperties.REQUEST_EXEC_REPLY` / `REQUEST_SHELL_REPLY` set on the client); the channel then closes.
- `start()` on a busy port throws `java.net.BindException: Address already in use` directly. After a failed start, `stop()` does nothing, so call `close(true)`. An `SshServer` cannot be restarted: build a new one per start. `getPort()` returns the real port after start.
- `PasswordAuthenticator.authenticate(String username, String password, ServerSession session)`. `PublickeyAuthenticator.authenticate(String username, PublicKey key, ServerSession session)` runs before signature verification and is called twice for an accepted key (query, then signed). Returning `false` is a quiet failure; throwing logs a WARN stack trace, so always return `false`.
  - `ServerSession.getClientAddress()` is an `InetSocketAddress`.
  - `SessionListener.sessionCreated(Session)` fires before authentication; `sessionEvent(Session, SessionListener.Event.Authenticated)` fires after it; `session.getUsername()` is set by then.
  - sshd itself never delays failed logins.
- Properties: `CoreModuleProperties.MAX_AUTH_REQUESTS.set(server, 10)`, `AUTH_TIMEOUT`, `IDLE_TIMEOUT` (`Duration`).
- `SimpleGeneratorHostKeyProvider(Path)` with `setAlgorithm("Ed25519")`:
  - `loadKeys(null)` generates or loads the key and writes the file `rw-------`. The parent folder must exist; otherwise it silently uses a throwaway key. Check that the file exists after loading.
  - Fingerprint: `KeyUtils.getFingerPrint(BuiltinDigests.sha256, keyPair.getPublic())` gives `SHA256:…`.
- `AuthorizedKeyEntry.parseAuthorizedKeyEntry(String)` then `entry.resolvePublicKey(null, PublicKeyEntryResolver.FAILING)` gives a `PublicKey`. Compare keys with `KeyUtils.compareKeys(a, b)`.
- `SftpSubsystemFactory.Builder().withFileSystemAccessor(accessor).build()`; `factory.addSftpEventListener(listener)`.
- Listener hooks (all `default`, all `throws IOException`): `received`, `receivedExtension(ServerSession, String, int)`, `opening/open(ServerSession, String remoteHandle, Handle)`, `read(ServerSession, String, FileHandle, long, byte[], int, int, int readLen, Throwable)`, `written(ServerSession, String, FileHandle, long, byte[], int, int, Throwable)`, `closing(ServerSession, String, Handle)`, `closed(ServerSession, String, Handle, Throwable)`, `exiting(ServerSession, Handle)`.
  - On a dropped connection only `exiting` fires, and then `accessor.closeFile` runs.
  - On a normal CLOSE the order is `closing`, then `accessor.closeFile`, then `closed`.
  - Throwing from a hook refuses the request: an `AccessDeniedException` answers "permission denied", an `UnsupportedOperationException` answers "operation unsupported".
- `SftpFileSystemAccessor` methods used (exact): `Path resolveLocalFilePath(SftpSubsystemProxy, Path rootDir, String remotePath)`, `SeekableByteChannel openFile(SftpSubsystemProxy, FileHandle, Path, String handle, Set<? extends OpenOption>, FileAttribute<?>...)`, `void closeFile(SftpSubsystemProxy, FileHandle, Path, String handle, Channel, Set<? extends OpenOption>)`, `void syncFileData(SftpSubsystemProxy, FileHandle, Path, String handle, Channel)`, `FileLock tryLock(SftpSubsystemProxy, FileHandle, Path, String handle, Channel, long, long, boolean)`, `DirectoryStream<Path> openDirectory(SftpSubsystemProxy, DirectoryHandle, Path, String handle, LinkOption...)`, `Map<String, ?> readFileAttributes(SftpSubsystemProxy, Path, String view, LinkOption...)`, `void setFileAttribute(SftpSubsystemProxy, Path, String view, String attribute, Object value, LinkOption...)`, `void setFileOwner(SftpSubsystemProxy, Path, Principal, LinkOption...)`, `void setGroupOwner(SftpSubsystemProxy, Path, Principal, LinkOption...)`, `void setFilePermissions(SftpSubsystemProxy, Path, Set<PosixFilePermission>, LinkOption...)`, `void setFileAccessControl(SftpSubsystemProxy, Path, List<AclEntry>, LinkOption...)`, `void createDirectory(SftpSubsystemProxy, Path)`, `void createLink(SftpSubsystemProxy, Path link, Path existing, boolean symLink)`, `String resolveLinkTarget(SftpSubsystemProxy, Path)`, `void renameFile(SftpSubsystemProxy, Path, Path, Collection<CopyOption>)`, `void copyFile(SftpSubsystemProxy, Path, Path, Collection<CopyOption>)`, `void removeFile(SftpSubsystemProxy, Path, boolean isDirectory)`, `void putRemoteFileName(SftpSubsystemProxy, Path, Buffer, String name, boolean shortName)`.
  - Opening a file for writing with `Write+Create+Truncate` (what `SftpClient.write(path)` sends) gives the options `[WRITE, TRUNCATE_EXISTING, CREATE]`. `Write+Create+Append` gives `[WRITE, CREATE]`: sshd removes `APPEND` and writes at the channel's size.
  - sshd writes each request with one `channel.write`, so a custom channel must write every byte in one call.
  - `FileHandle.getFileChannel()` returns the accessor's channel. `Handle.getFile()` returns the resolved `Path`.
- Client side, for tests:
  - Connect: `SshClient.setUpDefaultClient()`, `setServerKeyVerifier(AcceptAllServerKeyVerifier.INSTANCE)`, `setKeyIdentityProvider(KeyIdentityProvider.EMPTY_KEYS_PROVIDER)`, `setHostConfigEntryResolver(HostConfigEntryResolver.EMPTY)`, `start()`, then `connect(user, "127.0.0.1", port).verify(Duration).getSession()`, `addPasswordIdentity` or `addPublicKeyIdentity(KeyPair)`, and `auth().verify(Duration)`. A failed login throws `SshException`.
  - SFTP: `SftpClientFactory.instance().createSftpClient(session)` gives an `SftpClient` with `read(String)`, `write(String)`, `mkdir`, `rmdir`, `remove`, `rename`, `readDir(String)`, `stat`, `lstat`, `symLink`, `readLink`, `canonicalPath`, `open(String, OpenMode...)`, `write(Handle, long, byte[])` and `setStat`. Failures throw `org.apache.sshd.sftp.common.SftpException` (`getStatus()`); `readDir` wraps it in `UncheckedIOException`.
  - Key pairs: `SecurityUtils.getKeyPairGenerator("Ed25519").generateKeyPair()`.
- Quiet logging: set `java.util.logging.Logger.getLogger("org.apache.sshd")` to `WARNING`, keeping a strong reference.

## File map

- Modify `plugins/fileserver/build.gradle.kts` (sshd), `src/main/resources/plugin.toml`.
- `engine/ClientPaths.java` (moved from `engine/ftp/FtpPaths.java`); `engine/ftp/ShareView.java` and `engine/ftp/ShareFile.java` use it.
- `share/ShareRoot.java` (`locate`, `clientPath`, `openWriteChannel`, `rename(from, to, replace)`, `Upload.channel()`).
- `auth/AuthorizedKeys.java` (new).
- `engine/sftp/{package-info,SshSecurity,HostKey,ShareAccessor,UploadChannel,SftpActivity,KeyCheck,SftpEngine}.java` (new).
- `runtime/Engines.java`, `FileServerPlugin.java`.
- `ui/ServerEditor.java`, `ui/FileServersPanel.java`, `ui/ServerRows.java`.
- Tests mirror each package. Update `engine/ftp/FtpPathsTest.java` (becomes `engine/ClientPathsTest.java`), `share/ShareRootTest.java`, `runtime/EnginesTest.java`, `ui/ServerEditorTest.java`, `ui/FileServersPanelTest.java`, `ui/UiTestAccess.java` and `FileServerPluginTest.java`.
- `jasper-app/src/test/java/dev/jasper/app/plugins/PluginZipsTest.java`.
- Docs: `docs/file-server.md`, `docs/STATUS.md`, `docs/README.md`.

---

### Task 1: Shared client paths, and the share's channels for SFTP

**Files:**
- Create: `plugins/fileserver/src/main/java/dev/jasper/fileserver/engine/ClientPaths.java` (moved from `engine/ftp/FtpPaths.java`, which is deleted)
- Modify: `plugins/fileserver/src/main/java/dev/jasper/fileserver/engine/ftp/ShareView.java`, `engine/ftp/ShareFile.java` (use `ClientPaths`)
- Modify: `plugins/fileserver/src/main/java/dev/jasper/fileserver/share/ShareRoot.java`
- Test: `plugins/fileserver/src/test/java/dev/jasper/fileserver/engine/ClientPathsTest.java` (moved from `engine/ftp/FtpPathsTest.java`, which is deleted), `share/ShareRootTest.java`

**Interfaces:**
- Produces:
  - `public final class dev.jasper.fileserver.engine.ClientPaths` with `public static String absolute(String workingDirectory, String argument) throws PathRefusedException`, `public static String relative(String absolute)`, `public static String name(String absolute)`. These keep FS-2's `FtpPaths` behaviour exactly.
  - `ShareRoot.locate(String clientPath) throws IOException` returns the entry's path, its folder resolved through links that stay inside, and its own name not followed. It returns the root for an empty path and throws `PathRefusedException` when the folder leaves the share.
  - `ShareRoot.clientPath(Path path) throws PathRefusedException` returns `"a/b"` for a path inside the share (`""` for the root), and refuses any other path.
  - `ShareRoot.openWriteChannel(String clientPath, Set<? extends OpenOption> options) throws IOException` returns a `FileChannel` for writing in place at any offset. It has the same refusals as `openWrite`; options must contain `WRITE`.
  - `ShareRoot.rename(String from, String to, boolean replace) throws IOException`. The two-argument `rename` keeps refusing an existing target.
  - `ShareRoot.Upload.channel()` returns the temporary file's `FileChannel`, for writes at any offset. `Upload.stream()` stays and writes to the same channel.

- [ ] **Step 1: Move the path rules**

Create `engine/ClientPaths.java`:

```java
package dev.jasper.fileserver.engine;

import dev.jasper.fileserver.share.PathRefusedException;
import dev.jasper.fileserver.share.ShareRoot;
import java.util.ArrayList;

/**
 * Paths as FTP and SFTP clients send them: absolute and {@code /}-separated, resolved against a working directory,
 * with {@code ..} stopping at the share's root as an FTP or SFTP server's chroot does. {@code ~} is the root. Names
 * no platform can share are refused.
 */
public final class ClientPaths {
    private ClientPaths() { }

    public static String absolute(String workingDirectory, String argument) throws PathRefusedException {
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
    public static String relative(String absolute) { return absolute.substring(1); }

    /** The last name, or {@code /} for the root. */
    public static String name(String absolute) {
        return absolute.equals("/") ? "/" : absolute.substring(absolute.lastIndexOf('/') + 1);
    }
}
```

Then:
- Delete `engine/ftp/FtpPaths.java`.
- In `engine/ftp/ShareView.java` and `engine/ftp/ShareFile.java`, replace every `FtpPaths.` with `ClientPaths.` and add `import dev.jasper.fileserver.engine.ClientPaths;`.
- `git mv` the test `engine/ftp/FtpPathsTest.java` to `engine/ClientPathsTest.java`. Change its package to `dev.jasper.fileserver.engine`, its class name to `ClientPathsTest`, and every `FtpPaths.` to `ClientPaths.`. Its imports stay the same: `PathRefusedException` and AssertJ.
- Update `engine/ftp/package-info.java`'s first sentence if it mentions path rules. It may now depend on `engine` (it already does).

Run: `./gradlew :jasper-plugin-fileserver:test --tests 'dev.jasper.fileserver.engine.ClientPathsTest' --tests 'dev.jasper.fileserver.engine.ftp.*'`
Expected: PASS (behaviour unchanged).

- [ ] **Step 2: Write the failing share tests**

In `share/ShareRootTest.java`, add the imports `java.nio.channels.FileChannel`, `java.nio.file.FileSystemException`, `java.nio.file.StandardOpenOption`, `java.util.Set` and `java.nio.file.FileAlreadyExistsException`, and these tests:

```java
    @Test void locateResolvesTheFolderButNotTheEntryItself() throws IOException {
        var share = new ShareRoot(shared, true);
        Path real = shared.toRealPath();
        assertThat(share.locate("")).isEqualTo(real);
        assertThat(share.locate("/docs/a.txt")).isEqualTo(real.resolve("docs/a.txt"));
        assertThat(share.locate("docs/new.txt")).as("a missing entry in an existing folder").isEqualTo(real.resolve("docs/new.txt"));
        link(shared.resolve("inside"), shared.resolve("docs"));
        link(shared.resolve("out"), outside);
        link(shared.resolve("docs/to-secret"), outside.resolve("secret.txt"));
        assertThat(share.locate("inside/a.txt")).as("an inside link in the folder is followed").isEqualTo(real.resolve("docs/a.txt"));
        assertThat(share.locate("docs/to-secret")).as("the entry itself is not followed").isEqualTo(real.resolve("docs/to-secret"));
        assertThatThrownBy(() -> share.locate("out/secret.txt")).isInstanceOf(PathRefusedException.class);
        assertThatThrownBy(() -> share.locate("../outside")).isInstanceOf(PathRefusedException.class);
        assertThatThrownBy(() -> share.locate("CON")).isInstanceOf(PathRefusedException.class);
    }

    @Test void clientPathIsTheInverseInsideTheShare() throws IOException {
        var share = new ShareRoot(shared, true);
        Path real = shared.toRealPath();
        assertThat(share.clientPath(real)).isEmpty();
        assertThat(share.clientPath(real.resolve("docs/a.txt"))).isEqualTo("docs/a.txt");
        assertThat(share.clientPath(real.resolve("docs/../docs/a.txt"))).isEqualTo("docs/a.txt");
        assertThatThrownBy(() -> share.clientPath(real.getParent())).isInstanceOf(PathRefusedException.class);
        assertThatThrownBy(() -> share.clientPath(outside.toRealPath().resolve("secret.txt"))).isInstanceOf(PathRefusedException.class);
    }

    @Test void anUploadAcceptsWritesAtAnyOffset() throws IOException {
        var share = new ShareRoot(shared, false);
        try (var upload = share.upload("docs/a.txt")) {
            FileChannel channel = upload.channel();
            channel.write(ByteBuffer.wrap("world".getBytes(StandardCharsets.UTF_8)), 6);
            channel.write(ByteBuffer.wrap("hello ".getBytes(StandardCharsets.UTF_8)), 0);
            assertThat(shared.resolve("docs/a.txt")).as("untouched until the commit").hasContent("hello");
            upload.commit();
        }
        assertThat(shared.resolve("docs/a.txt")).hasContent("hello world");
    }

    @Test void aWriteChannelWritesInPlace() throws IOException {
        var share = new ShareRoot(shared, false);
        try (FileChannel channel = share.openWriteChannel("docs/a.txt", Set.of(StandardOpenOption.WRITE))) {
            channel.write(ByteBuffer.wrap("J".getBytes(StandardCharsets.UTF_8)), 0);
            channel.write(ByteBuffer.wrap("!".getBytes(StandardCharsets.UTF_8)), channel.size());
        }
        assertThat(shared.resolve("docs/a.txt")).hasContent("Jello!");
        try (FileChannel channel = share.openWriteChannel("docs/b.txt", Set.of(StandardOpenOption.WRITE, StandardOpenOption.CREATE_NEW))) {
            channel.write(ByteBuffer.wrap("new".getBytes(StandardCharsets.UTF_8)));
        }
        assertThat(shared.resolve("docs/b.txt")).hasContent("new");
        assertThatThrownBy(() -> share.openWriteChannel("docs/b.txt", Set.of(StandardOpenOption.WRITE, StandardOpenOption.CREATE_NEW)))
            .isInstanceOf(FileAlreadyExistsException.class);
        assertThatThrownBy(() -> share.openWriteChannel("docs", Set.of(StandardOpenOption.WRITE))).isInstanceOf(IsFolderException.class);
        assertThatThrownBy(() -> share.openWriteChannel("docs/a.txt", Set.of(StandardOpenOption.READ))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ShareRoot(shared, true).openWriteChannel("docs/a.txt", Set.of(StandardOpenOption.WRITE)))
            .isInstanceOf(ReadOnlyShareException.class);
        link(shared.resolve("docs/to-secret"), outside.resolve("secret.txt"));
        assertThatThrownBy(() -> share.openWriteChannel("docs/to-secret", Set.of(StandardOpenOption.WRITE)))
            .as("a final link is never written through").isInstanceOf(FileSystemException.class);
        assertThat(outside.resolve("secret.txt")).hasContent("secret");
    }

    @Test void renameCanReplaceWhenAsked() throws IOException {
        Files.writeString(shared.resolve("docs/b.txt"), "bee");
        var share = new ShareRoot(shared, false);
        assertThatThrownBy(() -> share.rename("docs/a.txt", "docs/b.txt")).isInstanceOf(FileAlreadyExistsException.class);
        share.rename("docs/a.txt", "docs/b.txt", true);
        assertThat(shared.resolve("docs/b.txt")).hasContent("hello");
        assertThat(shared.resolve("docs/a.txt")).doesNotExist();
    }
```

- [ ] **Step 3: Run them to see them fail**

Run: `./gradlew :jasper-plugin-fileserver:test --tests 'dev.jasper.fileserver.share.ShareRootTest'`
Expected: compilation fails, because `locate`, `clientPath`, `openWriteChannel`, `channel()` and the three-argument `rename` do not exist.

- [ ] **Step 4: Add them to `ShareRoot`**

Add the imports `java.nio.channels.Channels`, `java.nio.channels.FileChannel`, `java.nio.file.OpenOption` and `java.util.HashSet`. Add after `resolve(...)`:

```java
    /**
     * The entry a client path names: its folder resolved through links that stay inside, its own name not followed.
     * The root for an empty path. SFTP works on these paths, and every content operation still comes back here.
     */
    public Path locate(String clientPath) throws IOException { return entry(clientPath); }

    /** The client path ({@code "a/b"}, empty for the root) of a path inside the share; refuses any other path. */
    public String clientPath(Path path) throws PathRefusedException {
        Path normal = path.toAbsolutePath().normalize();
        if (!normal.startsWith(root)) throw new PathRefusedException("The path leaves the shared folder");
        if (normal.equals(root)) return "";
        var names = new ArrayList<String>();
        for (Path name : root.relativize(normal)) names.add(name.toString());
        return String.join("/", names);
    }
```

Add after `openWrite(...)`:

```java
    /**
     * Opens a file for writing in place at any offset, for SFTP's resumed, appended and read-write opens. The same
     * refusals as {@link #openWrite}; a final link is never written through. {@code options} must contain WRITE; READ,
     * CREATE, CREATE_NEW and TRUNCATE_EXISTING are honoured.
     */
    public FileChannel openWriteChannel(String clientPath, Set<? extends OpenOption> options) throws IOException {
        if (!options.contains(StandardOpenOption.WRITE)) throw new IllegalArgumentException("A write channel needs WRITE");
        requireWritable();
        Path path = entry(clientPath);
        if (path.equals(root) || Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) throw new IsFolderException(clientPath);
        var all = new HashSet<OpenOption>(options);
        all.add(LinkOption.NOFOLLOW_LINKS);
        return FileChannel.open(path, all);
    }
```

Replace `upload(...)` and the `Upload` class with:

```java
    /**
     * Starts replacing or creating a file without touching the target until {@link Upload#commit()}: the bytes go to
     * a hidden temporary file in the target's folder, which then moves onto the target in one step. Closing the
     * upload without a commit removes the temporary file and leaves the target as it was. The same refusals as
     * {@link #openWrite}.
     */
    public Upload upload(String clientPath) throws IOException {
        requireWritable();
        Path target = entry(clientPath);
        if (target.equals(root) || Files.isDirectory(target, LinkOption.NOFOLLOW_LINKS)) throw new IsFolderException(clientPath);
        byte[] random = new byte[8];
        UPLOAD_NAMES.nextBytes(random);
        Path temp = target.resolveSibling(UPLOAD_PREFIX + HexFormat.of().formatHex(random) + ".part");
        FileChannel channel = FileChannel.open(temp, StandardOpenOption.CREATE_NEW, StandardOpenOption.READ, StandardOpenOption.WRITE,
            LinkOption.NOFOLLOW_LINKS);
        return new Upload(temp, target, channel);
    }

    /** A file being received; see {@link ShareRoot#upload}. */
    public static final class Upload implements AutoCloseable {
        private final Path temp, target;
        private final FileChannel channel;
        private final OutputStream out;
        private boolean finished;

        private Upload(Path temp, Path target, FileChannel channel) {
            this.temp = temp; this.target = target; this.channel = channel; this.out = Channels.newOutputStream(channel);
        }

        /** The stream to write the new content to, in order. */
        public OutputStream stream() { return out; }

        /** The temporary file's channel (readable and writable), for writes at any offset; use commit or close, not channel().close(). */
        public FileChannel channel() { return channel; }

        /** Closes the file and moves it onto the target, replacing it. */
        public void commit() throws IOException {
            if (finished) throw new IllegalStateException("The upload is finished");
            finished = true;
            try {
                channel.close();
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

        /** Abandons the upload unless it was committed. */
        @Override public void close() {
            if (finished) return;
            finished = true;
            discard();
        }

        private void discard() {
            try { channel.close(); } catch (IOException ignored) { /* the file is removed next */ }
            try { Files.deleteIfExists(temp); } catch (IOException ignored) { /* nothing more to do */ }
        }
    }
```

Replace `rename(...)` with:

```java
    public void rename(String from, String to) throws IOException { rename(from, to, false); }

    /** Renames an entry; {@code replace} replaces an existing target, as SFTP's posix-rename asks. */
    public void rename(String from, String to, boolean replace) throws IOException {
        requireWritable();
        Path source = entry(from), target = entry(to);
        if (source.equals(root) || target.equals(root)) throw new PathRefusedException("The shared folder itself cannot be renamed");
        if (replace) Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        else Files.move(source, target);
    }
```

Remove any import that is no longer used (the compiler and `check` will show them). Keep `java.io.OutputStream`, because `stream()` returns one.

- [ ] **Step 5: Run the tests**

Run: `./gradlew :jasper-plugin-fileserver:check`
Expected: PASS. The FTP and HTTP upload tests still use `stream()`.

- [ ] **Step 6: Commit**

```bash
git add -A plugins/fileserver
git commit -m "File Server: shared client path rules; share channels, locate and replacing rename for SFTP

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 2: `AuthorizedKeys`, OpenSSH key lines without an SSH library

**Files:**
- Create: `plugins/fileserver/src/main/java/dev/jasper/fileserver/auth/AuthorizedKeys.java`
- Test: `plugins/fileserver/src/test/java/dev/jasper/fileserver/auth/AuthorizedKeysTest.java`

**Interfaces:**
- Produces (`dev.jasper.fileserver.auth.AuthorizedKeys`):
  - `public static final Set<String> TYPES`.
  - `public record Key(String type, String data, String comment)` with `String line()` (the text to store: type, data and comment when present), `String fingerprint()` (`SHA256:` and unpadded base64, as `ssh-keygen -l` prints) and `String summary()` (`"<type> <fingerprint>"`, then two spaces and the comment when there is one).
  - `public static Key parse(String line)` and `public static List<Key> parseAll(String text)`. Both throw `IllegalArgumentException` with a message for the editor.
  - `parseAll` skips blank and `#` lines, prefixes errors with `"Line N: "`, and refuses text with no keys: "No public keys found".

- [ ] **Step 1: Write the failing tests**

`plugins/fileserver/src/test/java/dev/jasper/fileserver/auth/AuthorizedKeysTest.java` (the two keys and their fingerprints came from `ssh-keygen`):

```java
package dev.jasper.fileserver.auth;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthorizedKeysTest {
    static final String ED25519 = "ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAIAEgwWITbeAjMHfs63n4fe6AxR8buxH3Wx+IdWdotPF0 alice@laptop";
    static final String ECDSA = "ecdsa-sha2-nistp256 AAAAE2VjZHNhLXNoYTItbmlzdHAyNTYAAAAIbmlzdHAyNTYAAABBBCKLV4ChGhVzArLw7brgwvIOQAjpwGqDaTifUtUTLxNQCWVdgGVtTjhhZSbIMKVdDUtRyBDkfjMCE3d4mznEAa8= ";

    @Test void parsesAKeyLineWithItsCommentAndFingerprint() {
        var key = AuthorizedKeys.parse(ED25519);
        assertThat(key.type()).isEqualTo("ssh-ed25519");
        assertThat(key.comment()).isEqualTo("alice@laptop");
        assertThat(key.line()).isEqualTo(ED25519);
        assertThat(key.fingerprint()).isEqualTo("SHA256:MgI+ts5vUqLauXQhbGEfXEqh28w0ZiEyBNNzcFRSBqw");
        assertThat(key.summary()).isEqualTo("ssh-ed25519 SHA256:MgI+ts5vUqLauXQhbGEfXEqh28w0ZiEyBNNzcFRSBqw  alice@laptop");
        var plain = AuthorizedKeys.parse(ECDSA);
        assertThat(plain.comment()).isEmpty();
        assertThat(plain.line()).as("trailing space dropped").isEqualTo(ECDSA.strip());
        assertThat(plain.fingerprint()).isEqualTo("SHA256:BdY9YKNPFTw7H/hoV3Qc6hCyIMvvyPYe9U1HUtCszMw");
        assertThat(plain.summary()).isEqualTo("ecdsa-sha2-nistp256 SHA256:BdY9YKNPFTw7H/hoV3Qc6hCyIMvvyPYe9U1HUtCszMw");
    }

    @Test void refusesWhatIsNotAPlainKeyLine() {
        assertThatThrownBy(() -> AuthorizedKeys.parse("command=\"echo hi\" " + ED25519))
            .hasMessage("Key options (such as command= or from=) are not supported: remove them from the line");
        assertThatThrownBy(() -> AuthorizedKeys.parse("restrict " + ED25519))
            .hasMessage("Key options (such as command= or from=) are not supported: remove them from the line");
        assertThatThrownBy(() -> AuthorizedKeys.parse("hello world")).hasMessage("Not an OpenSSH public key (for example ssh-ed25519 AAAA…)");
        assertThatThrownBy(() -> AuthorizedKeys.parse("ssh-ed25519")).hasMessage("The key data is missing");
        assertThatThrownBy(() -> AuthorizedKeys.parse("ssh-ed25519 not*base64")).hasMessage("The key data is not valid base64");
        assertThatThrownBy(() -> AuthorizedKeys.parse(ED25519.replace("ssh-ed25519 ", "ssh-rsa ")))
            .hasMessage("The key data does not match its type ssh-rsa");
        assertThatThrownBy(() -> AuthorizedKeys.parse(ED25519 + (char) 7)).hasMessage("The line contains control characters");
        assertThatThrownBy(() -> AuthorizedKeys.parse("  ")).hasMessage("Not an OpenSSH public key (for example ssh-ed25519 AAAA…)");
    }

    @Test void parsesAWholeFileAndNamesTheBadLine() {
        String file = "# my keys\n\n" + ED25519 + "\r\n" + ECDSA + "\n";
        assertThat(AuthorizedKeys.parseAll(file)).extracting(AuthorizedKeys.Key::type).containsExactly("ssh-ed25519", "ecdsa-sha2-nistp256");
        assertThatThrownBy(() -> AuthorizedKeys.parseAll(ED25519 + "\nnonsense here\n")).hasMessage("Line 2: Not an OpenSSH public key (for example ssh-ed25519 AAAA…)");
        assertThatThrownBy(() -> AuthorizedKeys.parseAll("# nothing\n")).hasMessage("No public keys found");
        assertThat(AuthorizedKeys.TYPES).contains("ssh-ed25519", "ssh-rsa", "ecdsa-sha2-nistp256", "sk-ssh-ed25519@openssh.com");
        assertThat(List.of(ED25519)).allSatisfy(line -> assertThat(AuthorizedKeys.parse(line).line()).isEqualTo(line));
    }
}
```

- [ ] **Step 2: Run them to see them fail**

Run: `./gradlew :jasper-plugin-fileserver:test --tests 'dev.jasper.fileserver.auth.AuthorizedKeysTest'`
Expected: compilation fails, because `AuthorizedKeys` does not exist.

- [ ] **Step 3: Write `AuthorizedKeys`**

```java
package dev.jasper.fileserver.auth;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Set;

/**
 * OpenSSH public key lines ({@code ssh-ed25519 AAAA… comment}) as {@code authorized_keys} holds them. They are
 * checked without an SSH library: a known key type, base64 key data whose own type matches, and an optional comment.
 * Lines with options ({@code command=}, {@code from=}, {@code restrict}, …) are refused, because File Server could
 * not honour them. The SFTP engine turns stored lines into keys.
 */
public final class AuthorizedKeys {
    public static final Set<String> TYPES = Set.of("ssh-ed25519", "ssh-rsa", "ecdsa-sha2-nistp256", "ecdsa-sha2-nistp384",
        "ecdsa-sha2-nistp521", "sk-ssh-ed25519@openssh.com", "sk-ecdsa-sha2-nistp256@openssh.com");

    /** One key: its type, its base64 data and its comment (empty when none). */
    public record Key(String type, String data, String comment) {
        /** The line to store. */
        public String line() { return comment.isEmpty() ? type + " " + data : type + " " + data + " " + comment; }

        /** The OpenSSH SHA-256 fingerprint, as {@code ssh-keygen -l} prints it. */
        public String fingerprint() {
            try {
                byte[] digest = MessageDigest.getInstance("SHA-256").digest(Base64.getDecoder().decode(data));
                return "SHA256:" + Base64.getEncoder().withoutPadding().encodeToString(digest);
            } catch (NoSuchAlgorithmException impossible) {
                throw new IllegalStateException(impossible);
            }
        }

        /** The type and fingerprint, then the comment, for lists. */
        public String summary() { return type + " " + fingerprint() + (comment.isEmpty() ? "" : "  " + comment); }
    }

    private AuthorizedKeys() { }

    public static Key parse(String line) {
        String text = line == null ? "" : line.strip();
        if (text.chars().anyMatch(Character::isISOControl)) throw new IllegalArgumentException("The line contains control characters");
        String[] parts = text.split("\\s+", 3);
        if (!TYPES.contains(parts[0])) {
            for (String part : text.split("\\s+"))
                if (TYPES.contains(part)) throw new IllegalArgumentException("Key options (such as command= or from=) are not supported: remove them from the line");
            throw new IllegalArgumentException("Not an OpenSSH public key (for example ssh-ed25519 AAAA…)");
        }
        if (parts.length < 2) throw new IllegalArgumentException("The key data is missing");
        byte[] blob;
        try { blob = Base64.getDecoder().decode(parts[1]); }
        catch (IllegalArgumentException bad) { throw new IllegalArgumentException("The key data is not valid base64"); }
        if (!parts[0].equals(embeddedType(blob))) throw new IllegalArgumentException("The key data does not match its type " + parts[0]);
        return new Key(parts[0], parts[1], parts.length == 3 ? parts[2].strip() : "");
    }

    /** Every key in {@code authorized_keys} text, from a file or the clipboard; blank and {@code #} lines are skipped. */
    public static List<Key> parseAll(String text) {
        var keys = new ArrayList<Key>();
        String[] lines = text.split("\\R");
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].strip();
            if (line.isEmpty() || line.startsWith("#")) continue;
            try { keys.add(parse(line)); }
            catch (IllegalArgumentException bad) { throw new IllegalArgumentException("Line " + (i + 1) + ": " + bad.getMessage()); }
        }
        if (keys.isEmpty()) throw new IllegalArgumentException("No public keys found");
        return List.copyOf(keys);
    }

    /** The type string an SSH key blob starts with: a 4-byte length, then the name. */
    private static String embeddedType(byte[] blob) {
        if (blob.length < 4) return "";
        int length = ByteBuffer.wrap(blob, 0, 4).getInt();
        if (length < 0 || length > blob.length - 4) return "";
        return new String(blob, 4, length, StandardCharsets.US_ASCII);
    }
}
```

- [ ] **Step 4: Run the tests**

Run: `./gradlew :jasper-plugin-fileserver:check`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add plugins/fileserver/src
git commit -m "File Server: OpenSSH authorized key lines, checked without an SSH library

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 3: sshd, its security provider, and the host key

**Files:**
- Modify: `plugins/fileserver/build.gradle.kts`
- Create: `plugins/fileserver/src/main/java/dev/jasper/fileserver/engine/sftp/{package-info,SshSecurity,HostKey}.java`
- Test: `plugins/fileserver/src/test/java/dev/jasper/fileserver/engine/sftp/HostKeyTest.java`

**Interfaces:**
- Consumes: `engine.EngineFailure`.
- Produces:
  - Package-private `SshSecurity.ensure()`. Call it before any sshd use; it registers this plugin's BouncyCastle provider with sshd and quiets sshd's INFO logging.
  - `public final class HostKey` with:
    - `public HostKey(Path file)`
    - `KeyPairProvider provider() throws EngineFailure` (package-private; creates the key on first use)
    - `public Optional<String> fingerprint() throws EngineFailure` (loads an existing key; never creates one)
    - `public Optional<String> cachedFingerprint()` (no I/O)
  - Failure messages:
    - "Could not create the SSH host key in <file>"
    - "The SSH host key in <file> can't be read"

- [ ] **Step 1: Add the dependency**

In `plugins/fileserver/build.gradle.kts`, extend the header comment's "Bundled:" list with "Apache MINA sshd for SFTP" and add:

```kotlin
    implementation("org.apache.sshd:sshd-core:2.19.0")
    implementation("org.apache.sshd:sshd-sftp:2.19.0")
```

These are the same versions Remote bundles; each plugin has its own class loader.

- [ ] **Step 2: Write the failing tests**

`plugins/fileserver/src/test/java/dev/jasper/fileserver/engine/sftp/HostKeyTest.java`:

```java
package dev.jasper.fileserver.engine.sftp;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.KeyPair;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.abort;

class HostKeyTest {
    @TempDir Path data;

    @Test void createsAnEd25519KeyOnceAndReadsItBack() throws Exception {
        Path file = data.resolve("hostkey");
        var key = new HostKey(file);
        assertThat(key.fingerprint()).as("nothing yet, and asking does not create one").isEmpty();
        assertThat(file).doesNotExist();
        KeyPair pair = key.provider().loadKeys(null).iterator().next();
        assertThat(pair.getPublic().getAlgorithm()).isIn("Ed25519", "EdDSA");
        String fingerprint = key.cachedFingerprint().orElseThrow();
        assertThat(fingerprint).startsWith("SHA256:").doesNotEndWith("=");
        assertThat(file).isRegularFile();
        assertThat(Files.readString(file)).startsWith("-----BEGIN OPENSSH PRIVATE KEY-----");
        assertThat(key.provider()).as("the provider is created once").isSameAs(key.provider());
        var again = new HostKey(file);
        assertThat(again.fingerprint()).as("the same key after a restart").contains(fingerprint);
    }

    @Test void theKeyFileIsOwnerOnly() throws Exception {
        if (!Files.getFileStore(data).supportsFileAttributeView("posix")) abort("no POSIX permissions here");
        Path file = data.resolve("hostkey");
        new HostKey(file).provider();
        assertThat(PosixFilePermissions.toString(Files.getPosixFilePermissions(file))).isEqualTo("rw-------");
    }

    @Test void aMissingFolderIsCreated() throws Exception {
        Path file = data.resolve("nested/hostkey");
        var key = new HostKey(file);
        key.provider();
        assertThat(file).isRegularFile();
        assertThat(key.cachedFingerprint()).isPresent();
    }
}
```

- [ ] **Step 3: Run them to see them fail**

Run: `./gradlew :jasper-plugin-fileserver:test --tests 'dev.jasper.fileserver.engine.sftp.*'`
Expected: compilation fails, because the package does not exist.

- [ ] **Step 4: Write the package**

`engine/sftp/package-info.java`:

```java
/**
 * The SFTP engine on Apache MINA sshd: the host key, a jail that routes every file operation through the share,
 * logins through the login throttle, and activity reporting. The only package that names sshd types. It may depend
 * on {@code engine}, {@code share}, {@code auth}, {@code definition} and the JDK, and on BouncyCastle only to give
 * sshd this plugin's own provider.
 */
package dev.jasper.fileserver.engine.sftp;
```

`engine/sftp/SshSecurity.java`:

```java
package dev.jasper.fileserver.engine.sftp;

import java.security.Provider;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.sshd.common.util.security.SecurityUtils;
import org.apache.sshd.common.util.security.bouncycastle.BouncyCastleSecurityProviderRegistrar;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

/**
 * Makes sshd use this plugin's own BouncyCastle provider. Each plugin loads its own BouncyCastle classes, and a
 * JVM-wide provider named "BC" may belong to another plugin's loader and hand out keys of the wrong classes. Remote
 * does the same. Also quiets sshd's INFO logging (a line per login); warnings still reach the log.
 */
final class SshSecurity {
    /** Held so the logger's level is not lost to garbage collection. */
    private static final Logger SSHD_LOG = Logger.getLogger("org.apache.sshd");

    static {
        SSHD_LOG.setLevel(Level.WARNING);
        SecurityUtils.registerSecurityProvider(new BouncyCastleSecurityProviderRegistrar() {
            private final Provider local = new BouncyCastleProvider();
            @Override public boolean isNamedProviderUsed() { return false; }
            @Override public Provider getSecurityProvider() { return local; }
        });
    }

    private SshSecurity() { }

    /** Runs the registration once; later calls do nothing. */
    static void ensure() { /* the class initialiser did the work */ }
}
```

`engine/sftp/HostKey.java`:

```java
package dev.jasper.fileserver.engine.sftp;

import dev.jasper.fileserver.engine.EngineFailure;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.util.List;
import java.util.Optional;
import org.apache.sshd.common.config.keys.KeyUtils;
import org.apache.sshd.common.digest.BuiltinDigests;
import org.apache.sshd.common.keyprovider.KeyPairProvider;
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider;

/**
 * The SFTP servers' host key: one Ed25519 key in {@code data/hostkey}, created on first use by sshd, which writes it
 * owner-only. sshd replaces a key file it cannot read with a new key, so the fingerprint then changes. The
 * fingerprint is in OpenSSH form ({@code SHA256:…}).
 *
 * <p>Thread-safe. Every method does file I/O except {@link #cachedFingerprint()}, so call them off the UI thread.
 */
public final class HostKey {
    private final Path file;
    private SimpleGeneratorHostKeyProvider provider;
    private volatile String fingerprint;

    public HostKey(Path file) { this.file = file; }

    /** The key for a server, created if there is none yet. */
    synchronized KeyPairProvider provider() throws EngineFailure {
        if (provider != null) return provider;
        SshSecurity.ensure();
        try { Files.createDirectories(file.getParent()); }
        catch (IOException failure) { throw new EngineFailure("Could not create the SSH host key in " + file + ": " + failure.getMessage(), failure); }
        var created = new SimpleGeneratorHostKeyProvider(file);
        created.setAlgorithm("Ed25519"); // never "EdDSA": with BouncyCastle alone it fails silently
        List<KeyPair> keys;
        try { keys = created.loadKeys(null); }
        catch (RuntimeException failure) { throw new EngineFailure("The SSH host key in " + file + " can't be read", failure); }
        // sshd falls back to a throwaway key, without an exception, when it cannot write the file.
        if (keys.isEmpty() || !Files.isRegularFile(file)) throw new EngineFailure("Could not create the SSH host key in " + file);
        fingerprint = KeyUtils.getFingerPrint(BuiltinDigests.sha256, keys.getFirst().getPublic());
        provider = created;
        return created;
    }

    /** The existing key's fingerprint; empty when none was created yet. Never creates one. */
    public synchronized Optional<String> fingerprint() throws EngineFailure {
        if (provider == null && !Files.isRegularFile(file)) return Optional.empty();
        provider();
        return Optional.of(fingerprint);
    }

    /** The fingerprint already known in memory, without touching the disk. */
    public Optional<String> cachedFingerprint() { return Optional.ofNullable(fingerprint); }
}
```

The test calls `provider().loadKeys(null)`. `KeyPairProvider.loadKeys` declares `IOException, GeneralSecurityException`, and the test method already `throws Exception`.

- [ ] **Step 5: Run the tests**

Run: `./gradlew :jasper-plugin-fileserver:check`
Expected: PASS. `verifyPluginArchitecture` accepts sshd because the plugin bundles it. Run `./gradlew :jasper-plugin-fileserver:dependencies --configuration runtimeClasspath` and check that `sshd-core`, `sshd-common` and `sshd-sftp` 2.19.0 are present.

- [ ] **Step 6: Commit**

```bash
git add plugins/fileserver
git commit -m "File Server: a persistent Ed25519 SSH host key, on this plugin's own BouncyCastle

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 4: The SFTP engine — logins, the jail, uploads, refusals

**Files:**
- Create: `plugins/fileserver/src/main/java/dev/jasper/fileserver/engine/sftp/{KeyCheck,SftpLogins,ShareAccessor,UploadChannel,SftpActivity,SftpEngine}.java`
- Test: `plugins/fileserver/src/test/java/dev/jasper/fileserver/engine/sftp/{Clients,SftpEngineTest,SftpActivityTest}.java`

**Interfaces:**
- Consumes:
  - From Task 1: `ShareRoot.locate`, `clientPath`, `openWriteChannel`, `rename(from, to, replace)`, `Upload.channel()` and `ClientPaths`.
  - From Task 3: `HostKey.provider()` and `SshSecurity.ensure()`.
  - Existing: `ResolvedLogin` (`isAnonymous`, `user`, `matches`), `LoginThrottle` (`retryAfter`, `lockedOut`, `failed`, `succeeded`), `ServerEvents` (`connected`, `loginSucceeded`, `loginFailed`), `ServerAddresses`, `EngineFailure.bind`.
- Produces:
  - `public SftpEngine(ServerDefinition definition, ShareRoot share, ResolvedLogin login, ServerEvents events, LoginThrottle throttle, Supplier<List<ServerAddresses.Nic>> nics, HostKey hostKey) implements ServerEngine`, with `public int port()`.
  - Package-private:
    - `KeyCheck(List<String> lines) throws EngineFailure`, with `isEmpty()` and `accepts(PublicKey)`.
    - `SftpLogins(ResolvedLogin, KeyCheck, LoginThrottle, ServerEvents)`, which implements `PasswordAuthenticator`, `PublickeyAuthenticator` and `SessionListener`.
    - `ShareAccessor(ShareRoot)`, which implements `SftpFileSystemAccessor`.
    - `UploadChannel(ShareRoot.Upload)`, which implements `SeekableByteChannel`, with `complete()`.
    - `SftpActivity()`, which implements `SftpEventListener`; Task 5 adds a `ServerEvents` argument. It has the constant `EXTENSIONS`.
  - Test helper `Clients` (test sources).

- [ ] **Step 1: Write the test helper and the failing tests**

`plugins/fileserver/src/test/java/dev/jasper/fileserver/engine/sftp/Clients.java`:

```java
package dev.jasper.fileserver.engine.sftp;

import java.io.IOException;
import java.security.KeyPair;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.config.hosts.HostConfigEntryResolver;
import org.apache.sshd.client.keyverifier.AcceptAllServerKeyVerifier;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.common.keyprovider.KeyIdentityProvider;
import org.apache.sshd.common.util.security.SecurityUtils;
import org.apache.sshd.core.CoreModuleProperties;
import org.apache.sshd.sftp.client.SftpClient;
import org.apache.sshd.sftp.client.SftpClientFactory;

/** SSH and SFTP clients for an engine on 127.0.0.1; they accept any host key and offer only what a test gives them. */
final class Clients implements AutoCloseable {
    final SshClient client = SshClient.setUpDefaultClient();

    Clients() {
        SshSecurity.ensure();
        client.setServerKeyVerifier(AcceptAllServerKeyVerifier.INSTANCE);
        client.setKeyIdentityProvider(KeyIdentityProvider.EMPTY_KEYS_PROVIDER);
        client.setHostConfigEntryResolver(HostConfigEntryResolver.EMPTY);
        CoreModuleProperties.REQUEST_EXEC_REPLY.set(client, true);   // so a refused exec or shell shows as a closed channel
        CoreModuleProperties.REQUEST_SHELL_REPLY.set(client, true);
        client.start();
    }

    static KeyPair newKey() throws Exception { return SecurityUtils.getKeyPairGenerator("Ed25519").generateKeyPair(); }

    ClientSession password(int port, String user, String password) throws IOException {
        ClientSession session = client.connect(user, "127.0.0.1", port).verify(Duration.ofSeconds(10)).getSession();
        session.addPasswordIdentity(password);
        session.auth().verify(Duration.ofSeconds(10));
        return session;
    }

    ClientSession key(int port, String user, KeyPair key) throws IOException {
        ClientSession session = client.connect(user, "127.0.0.1", port).verify(Duration.ofSeconds(10)).getSession();
        session.addPublicKeyIdentity(key);
        session.auth().verify(Duration.ofSeconds(10));
        return session;
    }

    static SftpClient sftp(ClientSession session) throws IOException { return SftpClientFactory.instance().createSftpClient(session); }

    /** A folder's names without "." and "..", sorted. */
    static List<String> names(SftpClient sftp, String path) throws IOException {
        var names = new ArrayList<String>();
        for (SftpClient.DirEntry entry : sftp.readDir(path))
            if (!entry.getFilename().equals(".") && !entry.getFilename().equals("..")) names.add(entry.getFilename());
        names.sort(null);
        return names;
    }

    @Override public void close() { client.stop(); }
}
```

`plugins/fileserver/src/test/java/dev/jasper/fileserver/engine/sftp/SftpEngineTest.java`:

```java
package dev.jasper.fileserver.engine.sftp;

import dev.jasper.fileserver.Ports;
import dev.jasper.fileserver.TestClock;
import dev.jasper.fileserver.auth.ResolvedLogin;
import dev.jasper.fileserver.definition.Login;
import dev.jasper.fileserver.definition.Protocol;
import dev.jasper.fileserver.definition.ServerDefinition;
import dev.jasper.fileserver.engine.EngineFailure;
import dev.jasper.fileserver.engine.LoginThrottle;
import dev.jasper.fileserver.engine.ServerEvent;
import dev.jasper.fileserver.engine.ServerEvents;
import dev.jasper.fileserver.share.ShareRoot;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.KeyPair;
import java.time.Clock;
import java.time.Duration;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;
import org.apache.sshd.client.channel.ChannelExec;
import org.apache.sshd.client.channel.ChannelShell;
import org.apache.sshd.client.channel.ClientChannelEvent;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.common.SshException;
import org.apache.sshd.common.config.keys.PublicKeyEntry;
import org.apache.sshd.common.util.net.SshdSocketAddress;
import org.apache.sshd.sftp.client.SftpClient;
import org.apache.sshd.sftp.client.SftpClient.CopyMode;
import org.apache.sshd.sftp.client.SftpClient.OpenMode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.abort;

class SftpEngineTest {
    static final ResolvedLogin DUSTIN = ResolvedLogin.password("dustin", "pw".toCharArray());
    @TempDir Path temp;
    Path shared, outside;
    final List<ServerEvent> events = new CopyOnWriteArrayList<>();
    final TestClock clock = new TestClock();
    final LoginThrottle throttle = new LoginThrottle(clock);
    final Clients clients = new Clients();
    SftpEngine engine;
    int port;

    @BeforeEach void share() throws IOException {
        shared = Files.createDirectories(temp.resolve("shared"));
        outside = Files.createDirectories(temp.resolve("outside"));
        Files.createDirectories(shared.resolve("docs"));
        Files.writeString(shared.resolve("docs/a.txt"), "hello");
        Files.writeString(outside.resolve("secret.txt"), "secret");
    }

    @AfterEach void stop() {
        if (engine != null) engine.stop();
        clients.close();
    }

    SftpEngine engine(ResolvedLogin login, boolean writable, List<String> keys) throws IOException {
        port = Ports.free();
        var base = ServerDefinition.create("sftp", Protocol.SFTP, shared);
        var definition = new ServerDefinition(base.id(), "sftp", Protocol.SFTP, shared, base.bind(), port, Optional.empty(), writable,
            login.isAnonymous() ? Login.ANONYMOUS : Login.SESSION, keys, false, false, false);
        engine = new SftpEngine(definition, new ShareRoot(shared, !writable), login, new ServerEvents(Clock.systemUTC(), events::add), throttle,
            List::of, new HostKey(temp.resolve("hostkey")));
        return engine;
    }

    SftpClient start(boolean writable) throws Exception {
        engine(DUSTIN, writable, List.of()).start();
        return Clients.sftp(clients.password(port, "dustin", "pw"));
    }

    static String read(SftpClient sftp, String path) throws IOException {
        try (var in = sftp.read(path)) { return new String(in.readAllBytes(), StandardCharsets.UTF_8); }
    }

    static void write(SftpClient sftp, String path, String text) throws IOException {
        try (var out = sftp.write(path)) { out.write(text.getBytes(StandardCharsets.UTF_8)); }
    }

    static void link(Path link, Path target) {
        try { Files.createSymbolicLink(link, target); }
        catch (UnsupportedOperationException | IOException | SecurityException refused) { abort("symbolic links are not available: " + refused); }
    }

    static void await(BooleanSupplier condition, String what) throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) Thread.sleep(10);
        assertThat(condition.getAsBoolean()).as(what).isTrue();
    }

    List<String> docs() throws IOException {
        try (var listing = Files.list(shared.resolve("docs"))) { return listing.map(p -> p.getFileName().toString()).sorted().toList(); }
    }

    @Test void aPasswordLoginReadsAndListsInsideTheShare() throws Exception {
        var sftp = start(false);
        assertThat(engine.addresses()).containsExactly(URI.create("sftp://dustin@127.0.0.1:" + port + "/"));
        assertThat(sftp.canonicalPath(".")).isEqualTo("/");
        assertThat(sftp.canonicalPath("/docs/../..")).isEqualTo("/");
        assertThat(sftp.canonicalPath("docs")).isEqualTo("/docs");
        assertThat(Clients.names(sftp, "/")).containsExactly("docs");
        assertThat(Clients.names(sftp, "/docs")).containsExactly("a.txt");
        assertThat(read(sftp, "/docs/a.txt")).isEqualTo("hello");
        assertThat(sftp.stat("/docs/a.txt").getSize()).isEqualTo(5);
        assertThatThrownBy(() -> read(sftp, "/../outside/secret.txt")).as(".. stops at the root").isInstanceOf(IOException.class);
        assertThatThrownBy(() -> sftp.stat("/../../etc/hosts")).isInstanceOf(IOException.class);
    }

    @Test void aReadOnlyShareRefusesEveryChange() throws Exception {
        var sftp = start(false);
        assertThatThrownBy(() -> write(sftp, "/new.txt", "x")).isInstanceOf(IOException.class);
        assertThatThrownBy(() -> sftp.mkdir("/made")).isInstanceOf(IOException.class);
        assertThatThrownBy(() -> sftp.remove("/docs/a.txt")).isInstanceOf(IOException.class);
        assertThatThrownBy(() -> sftp.rename("/docs/a.txt", "/docs/b.txt")).isInstanceOf(IOException.class);
        assertThatThrownBy(() -> sftp.setStat("/docs/a.txt", new SftpClient.Attributes().perms(0600))).isInstanceOf(IOException.class);
        assertThatThrownBy(() -> sftp.open("/docs/a.txt", OpenMode.Write)).isInstanceOf(IOException.class);
        assertThat(shared.resolve("new.txt")).doesNotExist();
        assertThat(shared.resolve("made")).doesNotExist();
        assertThat(shared.resolve("docs/a.txt")).hasContent("hello");
    }

    @Test void linksNeverLeadOutAndCannotBeMadeOrRead() throws Exception {
        link(shared.resolve("out"), outside);
        link(shared.resolve("docs/to-secret"), outside.resolve("secret.txt"));
        link(shared.resolve("inside"), shared.resolve("docs"));
        var sftp = start(true);
        assertThat(Clients.names(sftp, "/")).as("outward links are not listed").containsExactly("docs", "inside");
        assertThat(read(sftp, "/inside/a.txt")).as("an inside link works").isEqualTo("hello");
        assertThatThrownBy(() -> read(sftp, "/out/secret.txt")).isInstanceOf(IOException.class);
        assertThatThrownBy(() -> read(sftp, "/docs/to-secret")).isInstanceOf(IOException.class);
        assertThatThrownBy(() -> Clients.names(sftp, "/out")).isInstanceOfAny(IOException.class, UncheckedIOException.class);
        assertThatThrownBy(() -> write(sftp, "/out/new.txt", "x")).isInstanceOf(IOException.class);
        assertThatThrownBy(() -> sftp.open("/docs/to-secret", OpenMode.Write)).isInstanceOf(IOException.class);
        assertThatThrownBy(() -> sftp.remove("/out/secret.txt")).isInstanceOf(IOException.class);
        assertThatThrownBy(() -> sftp.symLink("/docs/mylink", "/docs/a.txt")).isInstanceOf(IOException.class);
        assertThatThrownBy(() -> sftp.readLink("/inside")).isInstanceOf(IOException.class);
        assertThat(outside.resolve("secret.txt")).hasContent("secret");
        assertThat(outside.resolve("new.txt")).doesNotExist();
        assertThat(shared.resolve("docs/mylink")).doesNotExist();
    }

    @Test void anUploadAppearsWhenItsHandleClosesAndNotBefore() throws Exception {
        var sftp = start(true);
        try (var out = sftp.write("/docs/new.txt")) {
            out.write("abc".getBytes(StandardCharsets.UTF_8));
            out.flush();
            assertThat(shared.resolve("docs/new.txt")).as("not before the close").doesNotExist();
        }
        assertThat(shared.resolve("docs/new.txt")).hasContent("abc");
        write(sftp, "/docs/a.txt", "replaced");
        assertThat(shared.resolve("docs/a.txt")).hasContent("replaced");
        assertThat(docs()).as("no temporary files remain").containsExactly("a.txt", "new.txt");
    }

    @Test void aDroppedConnectionAbandonsTheUpload() throws Exception {
        engine(DUSTIN, true, List.of()).start();
        ClientSession session = clients.password(port, "dustin", "pw");
        var sftp = Clients.sftp(session);
        var handle = sftp.open("/docs/a.txt", OpenMode.Write, OpenMode.Create, OpenMode.Truncate);
        sftp.write(handle, 0, "partial".getBytes(StandardCharsets.UTF_8));
        session.close(true);
        await(() -> { try { return docs().equals(List.of("a.txt")); } catch (IOException e) { return false; } }, "the temporary file is removed");
        assertThat(shared.resolve("docs/a.txt")).hasContent("hello");
    }

    @Test void otherWritesHappenInPlace() throws Exception {
        var sftp = start(true);
        try (var handle = sftp.open("/docs/a.txt", OpenMode.Write)) { sftp.write(handle, 0, "J".getBytes(StandardCharsets.UTF_8)); }
        assertThat(shared.resolve("docs/a.txt")).hasContent("Jello");
        try (var handle = sftp.open("/docs/a.txt", OpenMode.Write, OpenMode.Append)) { sftp.write(handle, 0, "!".getBytes(StandardCharsets.UTF_8)); }
        assertThat(shared.resolve("docs/a.txt")).hasContent("Jello!");
    }

    @Test void foldersRenamesAndRemovals() throws Exception {
        var sftp = start(true);
        sftp.mkdir("/docs/sub");
        assertThat(shared.resolve("docs/sub")).isDirectory();
        write(sftp, "/docs/b.txt", "bee");
        assertThatThrownBy(() -> sftp.rename("/docs/a.txt", "/docs/b.txt")).as("no silent replace").isInstanceOf(IOException.class);
        sftp.rename("/docs/a.txt", "/docs/b.txt", CopyMode.Overwrite);
        assertThat(shared.resolve("docs/b.txt")).hasContent("hello");
        sftp.rename("/docs/b.txt", "/docs/sub/c.txt");
        sftp.remove("/docs/sub/c.txt");
        sftp.rmdir("/docs/sub");
        assertThat(docs()).isEmpty();
    }

    @Test void permissionsAndTimesMayChangeOnAWritableShare() throws Exception {
        if (!Files.getFileStore(shared).supportsFileAttributeView("posix")) abort("no POSIX permissions here");
        var sftp = start(true);
        sftp.setStat("/docs/a.txt", new SftpClient.Attributes().perms(0600));
        assertThat(PosixFilePermissions.toString(Files.getPosixFilePermissions(shared.resolve("docs/a.txt")))).isEqualTo("rw-------");
    }

    @Test void keysWorkForTheirUserAndKeysOnlyServersForAnyUser() throws Exception {
        KeyPair key = Clients.newKey(), stranger = Clients.newKey();
        String line = PublicKeyEntry.toString(key.getPublic());
        engine(DUSTIN, false, List.of(line)).start();
        assertThat(read(Clients.sftp(clients.key(port, "dustin", key)), "/docs/a.txt")).isEqualTo("hello");
        assertThatThrownBy(() -> clients.key(port, "mallory", key)).as("a password server's keys are for its user").isInstanceOf(SshException.class);
        assertThatThrownBy(() -> clients.key(port, "dustin", stranger)).isInstanceOf(SshException.class);
        engine.stop();
        engine(ResolvedLogin.anonymous(), false, List.of(line)).start();
        assertThat(read(Clients.sftp(clients.key(port, "anyone", key)), "/docs/a.txt")).isEqualTo("hello");
        assertThatThrownBy(() -> clients.password(port, "anyone", "pw")).as("keys only").isInstanceOf(SshException.class);
    }

    @Test void aWrongPasswordIsThrottled() throws Exception {
        engine(DUSTIN, false, List.of()).start();
        assertThatThrownBy(() -> clients.password(port, "dustin", "bad")).isInstanceOf(SshException.class);
        assertThatThrownBy(() -> clients.password(port, "dustin", "pw")).as("too soon after a failure").isInstanceOf(SshException.class);
        clock.advance(Duration.ofSeconds(2));
        assertThat(clients.password(port, "dustin", "pw").isAuthenticated()).isTrue();
        assertThat(events).filteredOn(e -> e instanceof ServerEvent.LoginFailed).hasSize(1);
    }

    @Test void onlyTheSftpSubsystemIsServed() throws Exception {
        engine(DUSTIN, false, List.of()).start();
        ClientSession session = clients.password(port, "dustin", "pw");
        try (ChannelExec exec = session.createExecChannel("echo hi")) {
            exec.open().verify(Duration.ofSeconds(5));
            assertThat(exec.waitFor(EnumSet.of(ClientChannelEvent.CLOSED), Duration.ofSeconds(5))).contains(ClientChannelEvent.CLOSED);
            assertThat(exec.getExitStatus()).isNull();
        }
        try (ChannelShell shell = session.createShellChannel()) {
            shell.open().verify(Duration.ofSeconds(5));
            assertThat(shell.waitFor(EnumSet.of(ClientChannelEvent.CLOSED), Duration.ofSeconds(5))).contains(ClientChannelEvent.CLOSED);
        }
        assertThatThrownBy(() -> session.createDirectTcpipChannel(new SshdSocketAddress("127.0.0.1", 0), new SshdSocketAddress("127.0.0.1", port))
            .open().verify(Duration.ofSeconds(5))).isInstanceOf(IOException.class);
        clients.client.setUserAuthFactoriesNameList("keyboard-interactive");
        assertThatThrownBy(() -> clients.password(port, "dustin", "pw")).as("no keyboard-interactive login").isInstanceOf(SshException.class);
    }

    @Test void aBusyPortFailsWithAClearMessageAndStopFreesThePort() throws Exception {
        engine(DUSTIN, false, List.of());
        try (var taken = new ServerSocket()) {
            taken.setReuseAddress(false);
            taken.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), port));
            assertThatThrownBy(engine::start).isInstanceOf(EngineFailure.class).hasMessage("Port " + port + " is already in use on 127.0.0.1");
        }
        assertThat(engine.addresses()).isEmpty();
        engine.start();
        assertThat(engine.port()).isEqualTo(port);
        engine.stop();
        engine.stop();
        assertThat(engine.addresses()).isEmpty();
        assertThatThrownBy(() -> new Socket(InetAddress.getLoopbackAddress(), port).close()).isInstanceOf(ConnectException.class);
    }

    @Test void anUnusableAuthorizedKeyFailsTheStart() throws Exception {
        engine(DUSTIN, false, List.of("ssh-ed25519 AAAAnotakey"));
        assertThatThrownBy(engine::start).isInstanceOf(EngineFailure.class).hasMessageStartingWith("Authorized key 1 can't be used");
    }
}
```

`plugins/fileserver/src/test/java/dev/jasper/fileserver/engine/sftp/SftpActivityTest.java`:

```java
package dev.jasper.fileserver.engine.sftp;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SftpActivityTest {
    @Test void onlyHarmlessExtensionsRun() {
        var activity = new SftpActivity();
        assertThat(SftpActivity.EXTENSIONS).containsExactlyInAnyOrder("posix-rename@openssh.com", "fsync@openssh.com", "limits@openssh.com", "version-select");
        for (String allowed : SftpActivity.EXTENSIONS) assertThatCode(() -> activity.receivedExtension(null, allowed, 1)).doesNotThrowAnyException();
        for (String refused : new String[]{"copy-file", "copy-data", "md5-hash", "check-file-name", "space-available", "hardlink@openssh.com", "lsetstat@openssh.com"})
            assertThatThrownBy(() -> activity.receivedExtension(null, refused, 1)).as(refused).isInstanceOf(UnsupportedOperationException.class);
    }
}
```

- [ ] **Step 2: Run them to see them fail**

Run: `./gradlew :jasper-plugin-fileserver:test --tests 'dev.jasper.fileserver.engine.sftp.*'`
Expected: compilation fails, because `SftpEngine` and `SftpActivity` do not exist.

- [ ] **Step 3: Write `KeyCheck` and `SftpLogins`**

`engine/sftp/KeyCheck.java`:

```java
package dev.jasper.fileserver.engine.sftp;

import dev.jasper.fileserver.engine.EngineFailure;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.List;
import org.apache.sshd.common.config.keys.AuthorizedKeyEntry;
import org.apache.sshd.common.config.keys.KeyUtils;
import org.apache.sshd.common.config.keys.PublicKeyEntryResolver;

/** A server's authorized keys as sshd keys. The editor already checked the lines; one sshd cannot use fails the start. */
final class KeyCheck {
    private final List<PublicKey> keys;

    KeyCheck(List<String> lines) throws EngineFailure {
        SshSecurity.ensure();
        var resolved = new ArrayList<PublicKey>();
        for (int i = 0; i < lines.size(); i++) {
            try {
                AuthorizedKeyEntry entry = AuthorizedKeyEntry.parseAuthorizedKeyEntry(lines.get(i));
                if (entry != null) resolved.add(entry.resolvePublicKey(null, PublicKeyEntryResolver.FAILING));
            } catch (IOException | GeneralSecurityException | IllegalArgumentException failure) {
                throw new EngineFailure("Authorized key " + (i + 1) + " can't be used: " + failure.getMessage(), failure);
            }
        }
        keys = List.copyOf(resolved);
    }

    boolean isEmpty() { return keys.isEmpty(); }

    boolean accepts(PublicKey offered) { return keys.stream().anyMatch(key -> KeyUtils.compareKeys(key, offered)); }
}
```

`engine/sftp/SftpLogins.java`:

```java
package dev.jasper.fileserver.engine.sftp;

import dev.jasper.fileserver.auth.ResolvedLogin;
import dev.jasper.fileserver.engine.LoginThrottle;
import dev.jasper.fileserver.engine.ServerAddresses;
import dev.jasper.fileserver.engine.ServerEvents;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.security.PublicKey;
import java.time.Duration;
import java.util.Arrays;
import org.apache.sshd.common.session.Session;
import org.apache.sshd.common.session.SessionListener;
import org.apache.sshd.server.auth.password.PasswordAuthenticator;
import org.apache.sshd.server.auth.pubkey.PublickeyAuthenticator;
import org.apache.sshd.server.session.ServerSession;

/**
 * The server's logins.
 * <ul>
 * <li>Passwords go through the shared {@link LoginThrottle}: an address that must wait is refused without its
 *     password being checked, and a wrong password counts.</li>
 * <li>Offered keys that are not authorized do not count, because agents offer several in turn. A locked-out address
 *     is still refused keys.</li>
 * <li>With a password login, keys are for that user; a keys-only server takes any user name.</li>
 * <li>A success is reported once, when the session authenticates, because sshd asks the key check twice per
 *     login.</li>
 * </ul>
 * Every method returns false rather than throwing, because sshd logs a stack trace for an exception.
 */
final class SftpLogins implements PasswordAuthenticator, PublickeyAuthenticator, SessionListener {
    private final ResolvedLogin login;
    private final KeyCheck keys;
    private final LoginThrottle throttle;
    private final ServerEvents events;

    SftpLogins(ResolvedLogin login, KeyCheck keys, LoginThrottle throttle, ServerEvents events) {
        this.login = login; this.keys = keys; this.throttle = throttle; this.events = events;
    }

    @Override public boolean authenticate(String username, String password, ServerSession session) {
        String remote = remote(session);
        if (login.isAnonymous() || throttle.retryAfter(remote).compareTo(Duration.ZERO) > 0) return false;
        char[] candidate = password == null ? new char[0] : password.toCharArray();
        try {
            if (login.matches(username, candidate)) return true;
        } finally {
            Arrays.fill(candidate, '\0');
        }
        throttle.failed(remote);
        events.loginFailed(remote, String.valueOf(username));
        return false;
    }

    @Override public boolean authenticate(String username, PublicKey key, ServerSession session) {
        if (throttle.lockedOut(remote(session))) return false;
        if (login.user().isPresent() && !login.user().get().equals(username)) return false;
        return keys.accepts(key);
    }

    @Override public void sessionCreated(Session session) { events.connected(remote(session)); }

    @Override public void sessionEvent(Session session, Event event) {
        if (event != Event.Authenticated) return;
        String remote = remote(session);
        throttle.succeeded(remote);
        events.loginSucceeded(remote, String.valueOf(session.getUsername()));
    }

    static String remote(Session session) {
        SocketAddress address = session instanceof ServerSession server ? server.getClientAddress() : null;
        return address instanceof InetSocketAddress inet && inet.getAddress() != null ? ServerAddresses.hostText(inet.getAddress()) : "";
    }
}
```

- [ ] **Step 4: Write the jail, `ShareAccessor` and `UploadChannel`**

`engine/sftp/UploadChannel.java`:

```java
package dev.jasper.fileserver.engine.sftp;

import dev.jasper.fileserver.share.ShareRoot;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.SeekableByteChannel;

/**
 * An upload's temporary file as an SFTP handle's channel. Closing it commits the upload only after the client closed
 * the handle normally ({@link #complete()}, from the CLOSE request); otherwise, on a dropped connection or a server
 * stop, closing abandons it.
 */
final class UploadChannel implements SeekableByteChannel {
    private final ShareRoot.Upload upload;
    private final FileChannel channel;
    private volatile boolean complete;
    private boolean closed;

    UploadChannel(ShareRoot.Upload upload) { this.upload = upload; this.channel = upload.channel(); }

    /** The client closed the handle normally: closing commits. */
    void complete() { complete = true; }

    /** Forces the temporary file to disk, for {@code fsync@openssh.com}. */
    void force() throws IOException { channel.force(true); }

    @Override public int read(ByteBuffer target) throws IOException { return channel.read(target); }

    /** Writes every byte: sshd ignores a short write. */
    @Override public int write(ByteBuffer source) throws IOException {
        int written = 0;
        while (source.hasRemaining()) written += channel.write(source);
        return written;
    }

    @Override public long position() throws IOException { return channel.position(); }
    @Override public SeekableByteChannel position(long position) throws IOException { channel.position(position); return this; }
    @Override public long size() throws IOException { return channel.size(); }
    @Override public SeekableByteChannel truncate(long size) throws IOException { channel.truncate(size); return this; }
    @Override public boolean isOpen() { return channel.isOpen(); }

    @Override public synchronized void close() throws IOException {
        if (closed) return;
        closed = true;
        if (complete) upload.commit();
        else upload.close();
    }
}
```

`engine/sftp/ShareAccessor.java`:

```java
package dev.jasper.fileserver.engine.sftp;

import dev.jasper.fileserver.engine.ClientPaths;
import dev.jasper.fileserver.share.PathRefusedException;
import dev.jasper.fileserver.share.ReadOnlyShareException;
import dev.jasper.fileserver.share.ShareRoot;
import java.io.IOException;
import java.nio.channels.Channel;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.AccessDeniedException;
import java.nio.file.CopyOption;
import java.nio.file.DirectoryStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermission;
import java.security.Principal;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.sshd.common.util.buffer.Buffer;
import org.apache.sshd.sftp.server.DirectoryHandle;
import org.apache.sshd.sftp.server.FileHandle;
import org.apache.sshd.sftp.server.SftpFileSystemAccessor;
import org.apache.sshd.sftp.server.SftpSubsystemProxy;

/**
 * The SFTP jail.
 *
 * <p>sshd resolves every remote path through {@link #resolveLocalFilePath} and then calls {@code Files} on the
 * result directly. So the result is always inside the share: the client path with {@code ..} stopped at the root,
 * located through {@link ShareRoot#locate}, which follows the folder's links only while they stay inside and never
 * follows the entry's own name.
 *
 * <p>Every content operation goes back through {@link ShareRoot} by client path. It refuses outward links and writes
 * to a read-only share. Links cannot be read or made, replies name client paths only, and the root's {@code ..}
 * describes the root itself. Owners, groups and ACLs never change. On a writable share, times and POSIX permissions
 * may.
 *
 * <p>A truncating, exclusive or creating open of a missing file uploads to a temporary file
 * ({@link UploadChannel}). Other write opens write in place. Refusals reach the client as "permission denied".
 */
final class ShareAccessor implements SftpFileSystemAccessor {
    private static final Set<String> TIMES = Set.of("lastModifiedTime", "lastAccessTime", "creationTime");
    private final ShareRoot share;
    private final Path root;

    ShareAccessor(ShareRoot share) { this.share = share; this.root = share.root(); }

    @Override public Path resolveLocalFilePath(SftpSubsystemProxy subsystem, Path rootDir, String remotePath) throws IOException {
        try { return share.locate(ClientPaths.relative(ClientPaths.absolute("/", remotePath))); }
        catch (PathRefusedException refused) { throw denied(refused); }
    }

    @Override public SeekableByteChannel openFile(SftpSubsystemProxy subsystem, FileHandle fileHandle, Path file, String handle,
                                                Set<? extends OpenOption> options, FileAttribute<?>... attrs) throws IOException {
        String path = client(file);
        try {
            if (!options.contains(StandardOpenOption.WRITE) && !options.contains(StandardOpenOption.APPEND)) return share.openRead(path);
            boolean exists = Files.exists(file, LinkOption.NOFOLLOW_LINKS);
            boolean creates = options.contains(StandardOpenOption.CREATE) || options.contains(StandardOpenOption.CREATE_NEW);
            if (options.contains(StandardOpenOption.CREATE_NEW) && exists) throw new FileAlreadyExistsException("/" + path);
            if (!exists && !creates) throw new NoSuchFileException("/" + path);
            if (options.contains(StandardOpenOption.TRUNCATE_EXISTING) || !exists) return new UploadChannel(share.upload(path));
            return share.openWriteChannel(path, options);
        } catch (PathRefusedException | ReadOnlyShareException refused) {
            throw denied(refused);
        }
    }

    @Override public void closeFile(SftpSubsystemProxy subsystem, FileHandle fileHandle, Path file, String handle, Channel channel,
                                    Set<? extends OpenOption> options) throws IOException {
        channel.close(); // no fsync here: on a dropped connection the thread is interrupted, and fsync@openssh.com asks for it
    }

    @Override public void syncFileData(SftpSubsystemProxy subsystem, FileHandle fileHandle, Path file, String handle, Channel channel) throws IOException {
        if (channel instanceof FileChannel inPlace) inPlace.force(true);
        else if (channel instanceof UploadChannel upload) upload.force();
    }

    @Override public FileLock tryLock(SftpSubsystemProxy subsystem, FileHandle fileHandle, Path file, String handle, Channel channel,
                                      long position, long size, boolean shared) {
        throw new UnsupportedOperationException("Byte-range locks are not supported");
    }

    @Override public DirectoryStream<Path> openDirectory(SftpSubsystemProxy subsystem, DirectoryHandle dirHandle, Path dir, String handle,
                                                         LinkOption... linkOptions) throws IOException {
        List<Path> entries;
        try { entries = share.list(client(dir)).stream().map(entry -> dir.resolve(entry.name())).toList(); }
        catch (PathRefusedException refused) { throw denied(refused); }
        return new DirectoryStream<>() {
            private boolean iterated;
            @Override public Iterator<Path> iterator() {
                if (iterated) throw new IllegalStateException("A directory stream is read once");
                iterated = true;
                return entries.iterator();
            }
            @Override public void close() { }
        };
    }

    @Override public Map<String, ?> readFileAttributes(SftpSubsystemProxy subsystem, Path file, String view, LinkOption... options) throws IOException {
        if (!file.toAbsolutePath().normalize().startsWith(root)) return Files.readAttributes(root, view); // ".." at the root
        if (Arrays.asList(options).contains(LinkOption.NOFOLLOW_LINKS)) return Files.readAttributes(file, view, LinkOption.NOFOLLOW_LINKS);
        try { return Files.readAttributes(share.resolve(client(file)), view); }
        catch (PathRefusedException refused) { throw denied(refused); }
    }

    @Override public void setFileAttribute(SftpSubsystemProxy subsystem, Path file, String view, String attribute, Object value,
                                           LinkOption... options) throws IOException {
        if (!TIMES.contains(attribute)) throw new AccessDeniedException("/" + client(file), null, "Only times and permissions may change");
        Files.setAttribute(writableTarget(file), view + ":" + attribute, value);
    }

    @Override public void setFilePermissions(SftpSubsystemProxy subsystem, Path file, Set<PosixFilePermission> perms, LinkOption... options) throws IOException {
        Files.setPosixFilePermissions(writableTarget(file), perms);
    }

    @Override public void setFileOwner(SftpSubsystemProxy subsystem, Path file, Principal value, LinkOption... options) throws IOException {
        throw new AccessDeniedException("/" + client(file), null, "Owners never change");
    }

    @Override public void setGroupOwner(SftpSubsystemProxy subsystem, Path file, Principal value, LinkOption... options) throws IOException {
        throw new AccessDeniedException("/" + client(file), null, "Groups never change");
    }

    @Override public void setFileAccessControl(SftpSubsystemProxy subsystem, Path file, List<AclEntry> acl, LinkOption... options) throws IOException {
        throw new AccessDeniedException("/" + client(file), null, "ACLs never change");
    }

    @Override public void createDirectory(SftpSubsystemProxy subsystem, Path path) throws IOException {
        try { share.mkdir(client(path)); }
        catch (PathRefusedException | ReadOnlyShareException refused) { throw denied(refused); }
    }

    @Override public void removeFile(SftpSubsystemProxy subsystem, Path path, boolean isDirectory) throws IOException {
        try { share.delete(client(path)); }
        catch (PathRefusedException | ReadOnlyShareException refused) { throw denied(refused); }
    }

    @Override public void renameFile(SftpSubsystemProxy subsystem, Path oldPath, Path newPath, Collection<CopyOption> opts) throws IOException {
        try { share.rename(client(oldPath), client(newPath), opts.contains(StandardCopyOption.REPLACE_EXISTING)); }
        catch (PathRefusedException | ReadOnlyShareException refused) { throw denied(refused); }
    }

    @Override public void copyFile(SftpSubsystemProxy subsystem, Path src, Path dst, Collection<CopyOption> opts) {
        throw new UnsupportedOperationException("Copying on the server is not supported");
    }

    @Override public void createLink(SftpSubsystemProxy subsystem, Path link, Path existing, boolean symLink) throws IOException {
        throw new AccessDeniedException(link.getFileName() == null ? "/" : link.getFileName().toString(), null, "Links cannot be made");
    }

    @Override public String resolveLinkTarget(SftpSubsystemProxy subsystem, Path link) {
        throw new UnsupportedOperationException("Links cannot be read");
    }

    /** Replies name client paths: REALPATH would otherwise send the host path. Listing names pass unchanged. */
    @Override public void putRemoteFileName(SftpSubsystemProxy subsystem, Path path, Buffer buf, String name, boolean shortName) throws IOException {
        buf.putString(path != null && name.equals(path.toString()) ? "/" + client(path) : name);
    }

    /** The real file an attribute change applies to: inside the share, on a writable share. */
    private Path writableTarget(Path file) throws IOException {
        if (share.readOnly()) throw denied(new ReadOnlyShareException());
        try { return share.resolve(client(file)); }
        catch (PathRefusedException refused) { throw denied(refused); }
    }

    private String client(Path path) throws IOException {
        try { return share.clientPath(path); }
        catch (PathRefusedException refused) { throw denied(refused); }
    }

    private static AccessDeniedException denied(IOException cause) {
        var denied = new AccessDeniedException(cause.getMessage()); // the client sees only "permission denied"
        denied.initCause(cause);
        return denied;
    }
}
```

- [ ] **Step 5: Write `SftpActivity` (Task 5 adds reporting) and `SftpEngine`**

`engine/sftp/SftpActivity.java`:

```java
package dev.jasper.fileserver.engine.sftp;

import java.util.Set;
import org.apache.sshd.sftp.server.FileHandle;
import org.apache.sshd.sftp.server.Handle;
import org.apache.sshd.sftp.server.SftpEventListener;
import org.apache.sshd.server.session.ServerSession;

/**
 * Watches every SFTP request. Only harmless extensions run: the others (copy-file, md5-hash, space-available, …)
 * would reach the disk without the jail's checks. A normal CLOSE marks an upload for commit; a dropped connection
 * never does.
 */
final class SftpActivity implements SftpEventListener {
    static final Set<String> EXTENSIONS = Set.of("posix-rename@openssh.com", "fsync@openssh.com", "limits@openssh.com", "version-select");

    @Override public void receivedExtension(ServerSession session, String extension, int id) {
        if (!EXTENSIONS.contains(extension)) throw new UnsupportedOperationException(extension + " is not supported");
    }

    @Override public void closing(ServerSession session, String remoteHandle, Handle localHandle) {
        if (localHandle instanceof FileHandle file && file.getFileChannel() instanceof UploadChannel upload) upload.complete();
    }
}
```

`engine/sftp/SftpEngine.java`:

```java
package dev.jasper.fileserver.engine.sftp;

import dev.jasper.fileserver.auth.ResolvedLogin;
import dev.jasper.fileserver.definition.ServerDefinition;
import dev.jasper.fileserver.engine.EngineFailure;
import dev.jasper.fileserver.engine.LoginThrottle;
import dev.jasper.fileserver.engine.ServerAddresses;
import dev.jasper.fileserver.engine.ServerEngine;
import dev.jasper.fileserver.engine.ServerEvents;
import dev.jasper.fileserver.share.ShareRoot;
import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import org.apache.sshd.common.file.nativefs.NativeFileSystemFactory;
import org.apache.sshd.core.CoreModuleProperties;
import org.apache.sshd.server.SshServer;
import org.apache.sshd.server.auth.UserAuthFactory;
import org.apache.sshd.server.auth.password.UserAuthPasswordFactory;
import org.apache.sshd.server.auth.pubkey.RejectAllPublickeyAuthenticator;
import org.apache.sshd.server.auth.pubkey.UserAuthPublicKeyFactory;
import org.apache.sshd.server.forward.RejectAllForwardingFilter;
import org.apache.sshd.sftp.server.SftpSubsystemFactory;

/**
 * An SFTP server for one share on Apache MINA sshd. It serves only the SFTP subsystem, with no shell, no exec and no
 * forwarding. Logins are by password, by authorized key, or both; keyboard-interactive is off. Every file operation
 * goes through the share ({@link ShareAccessor}). An {@code SshServer} cannot be restarted, so each start builds a
 * new one.
 */
public final class SftpEngine implements ServerEngine {
    static final int MAX_AUTH_REQUESTS = 10;
    private final ServerDefinition definition;
    private final ShareRoot share;
    private final ResolvedLogin login;
    private final ServerEvents events;
    private final LoginThrottle throttle;
    private final Supplier<List<ServerAddresses.Nic>> nics;
    private final HostKey hostKey;
    private SshServer server;
    private int port;

    public SftpEngine(ServerDefinition definition, ShareRoot share, ResolvedLogin login, ServerEvents events, LoginThrottle throttle,
                      Supplier<List<ServerAddresses.Nic>> nics, HostKey hostKey) {
        this.definition = definition; this.share = share; this.login = login; this.events = events; this.throttle = throttle;
        this.nics = nics; this.hostKey = hostKey;
    }

    @Override public synchronized void start() throws EngineFailure {
        if (server != null) return;
        InetAddress address = ServerAddresses.bindAddress(definition.bind(), nics.get());
        var keys = new KeyCheck(definition.authorizedKeys());
        if (login.isAnonymous() && keys.isEmpty()) throw new EngineFailure("SFTP needs a password or authorized keys");
        var hostKeys = hostKey.provider();
        var logins = new SftpLogins(login, keys, throttle, events);
        var sftp = new SftpSubsystemFactory.Builder().withFileSystemAccessor(new ShareAccessor(share)).build();
        sftp.addSftpEventListener(new SftpActivity());

        SshServer created = SshServer.setUpDefaultServer();
        created.setHost(address.isAnyLocalAddress() ? null : address.getHostAddress());
        created.setPort(definition.port());
        created.setKeyPairProvider(hostKeys);
        var methods = new ArrayList<UserAuthFactory>();
        if (!login.isAnonymous()) methods.add(UserAuthPasswordFactory.INSTANCE);
        if (!keys.isEmpty()) methods.add(UserAuthPublicKeyFactory.INSTANCE);
        created.setUserAuthFactories(methods); // an explicit list: no keyboard-interactive
        created.setPasswordAuthenticator(logins);
        created.setPublickeyAuthenticator(keys.isEmpty() ? RejectAllPublickeyAuthenticator.INSTANCE : logins); // never the OS user's authorized_keys
        created.setKeyboardInteractiveAuthenticator(null);
        created.setShellFactory(null);
        created.setCommandFactory(null);
        created.setForwardingFilter(RejectAllForwardingFilter.INSTANCE);
        created.setFileSystemFactory(NativeFileSystemFactory.INSTANCE); // the accessor confines every path to the share
        created.setSubsystemFactories(List.of(sftp));
        created.addSessionListener(logins);
        CoreModuleProperties.MAX_AUTH_REQUESTS.set(created, MAX_AUTH_REQUESTS);
        try {
            created.start();
        } catch (IOException failure) {
            closeQuietly(created);
            throw EngineFailure.bind(failure, definition.port(), ServerAddresses.describe(address), definition.protocol());
        } catch (RuntimeException failure) {
            closeQuietly(created);
            throw new EngineFailure("Could not start the SFTP server: " + failure.getMessage(), failure);
        }
        server = created;
        port = created.getPort();
    }

    @Override public synchronized void stop() {
        if (server == null) return;
        try { server.stop(true); }
        catch (IOException ignored) { /* the server is going away either way */ }
        server = null;
    }

    @Override public synchronized List<URI> addresses() {
        if (server == null) return List.of();
        return ServerAddresses.urls(definition.protocol(), false, ServerAddresses.reachable(definition.bind(), nics.get()), port, login.user());
    }

    /** The bound port; 0 before the first start. */
    public synchronized int port() { return port; }

    /** A server whose start failed stays open: {@code stop()} would do nothing, so close it. */
    private static void closeQuietly(SshServer server) {
        try { server.close(true).await(Duration.ofSeconds(5)); }
        catch (IOException ignored) { /* nothing more to release */ }
    }
}
```

- [ ] **Step 6: Run the tests**

Run: `./gradlew :jasper-plugin-fileserver:test --tests 'dev.jasper.fileserver.engine.sftp.*'`
Expected: PASS. If a test fails, read the server log in the test report's system-err before changing code. Do not loosen the jail to make a test pass: report it instead. Then run `./gradlew :jasper-plugin-fileserver:check` and expect PASS.

- [ ] **Step 7: Commit**

```bash
git add plugins/fileserver/src
git commit -m "File Server: an SFTP engine on MINA sshd, jailed through the share, with password and key logins

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 5: SFTP activity — transfers and changes in the activity log

**Files:**
- Modify: `plugins/fileserver/src/main/java/dev/jasper/fileserver/engine/sftp/SftpActivity.java`, `engine/sftp/SftpEngine.java`
- Test: `plugins/fileserver/src/test/java/dev/jasper/fileserver/engine/sftp/SftpEngineTest.java`, `engine/sftp/SftpActivityTest.java`

**Interfaces:**
- Consumes: `ServerEvents.transfer(remote, direction, path, total, cancel)` returns `ServerEvents.Transfer`, which has `add(long)`, `bytes()` and `finish(boolean)`; `ServerEvents.changed(remote, description)`; `SftpLogins.remote(Session)` (Task 4); `ShareRoot.clientPath(Path)` (Task 1).
- Produces: `SftpActivity(ShareRoot share, ServerEvents events)` replaces Task 4's no-argument constructor. `SftpEngine` passes both.
  - A file handle is one transfer: a download when it is opened without WRITE, with its total being the file's size, and an upload otherwise, with an unknown total. Its path is the client path, `/docs/a.txt`. Cancelling it closes the client's session.
  - A download is complete when it was closed normally and read to its size. An upload is complete when it was closed normally (its commit happens in that close). A handle left open by a dropped connection ends incomplete.
  - Changes are reported as "created <path>", "deleted <path>" and "renamed <from> to <to>", as FTP reports them.

- [ ] **Step 1: Write the failing tests**

In `SftpActivityTest.java`, add the imports `dev.jasper.fileserver.TestClock`, `dev.jasper.fileserver.engine.ServerEvents`, `dev.jasper.fileserver.share.ShareRoot`, `java.nio.file.Path` and `org.junit.jupiter.api.io.TempDir`. Add the field `@TempDir Path temp;`, then change the test's first line to:

```java
        var activity = new SftpActivity(new ShareRoot(temp, true), new ServerEvents(new TestClock(), event -> { }));
```

and declare the test `throws Exception`.

In `SftpEngineTest.java`, add these tests:

```java
    List<ServerEvent.Transferred> transferred() {
        return events.stream().filter(e -> e instanceof ServerEvent.Transferred).map(e -> (ServerEvent.Transferred) e).toList();
    }

    @Test void transfersAndLoginsAreReported() throws Exception {
        var sftp = start(true);
        assertThat(read(sftp, "/docs/a.txt")).isEqualTo("hello");
        write(sftp, "/docs/new.txt", "abc");
        await(() -> transferred().size() == 2, "both transfers end");
        assertThat(transferred()).extracting(ServerEvent.Transferred::path, ServerEvent.Transferred::direction,
                ServerEvent.Transferred::bytes, ServerEvent.Transferred::complete)
            .containsExactly(org.assertj.core.groups.Tuple.tuple("/docs/a.txt", ServerEvent.Direction.DOWNLOAD, 5L, true),
                org.assertj.core.groups.Tuple.tuple("/docs/new.txt", ServerEvent.Direction.UPLOAD, 3L, true));
        assertThat(events).anyMatch(e -> e instanceof ServerEvent.Connected c && c.remote().equals("127.0.0.1"));
        assertThat(events).filteredOn(e -> e instanceof ServerEvent.LoginSucceeded).singleElement()
            .satisfies(e -> assertThat(((ServerEvent.LoginSucceeded) e).user()).isEqualTo("dustin"));
        var started = (ServerEvent.TransferStarted) events.stream().filter(e -> e instanceof ServerEvent.TransferStarted).findFirst().orElseThrow();
        assertThat(started.total()).as("a download knows its size").isEqualTo(5);
    }

    @Test void aPartialReadOrADroppedUploadIsIncomplete() throws Exception {
        engine(DUSTIN, true, List.of()).start();
        ClientSession session = clients.password(port, "dustin", "pw");
        var sftp = Clients.sftp(session);
        try (var handle = sftp.open("/docs/a.txt", OpenMode.Read)) { sftp.read(handle, 0, new byte[2]); }
        await(() -> transferred().size() == 1, "the partial read ends");
        assertThat(transferred().getFirst().complete()).isFalse();
        var upload = sftp.open("/docs/b.txt", OpenMode.Write, OpenMode.Create, OpenMode.Truncate);
        sftp.write(upload, 0, "xy".getBytes(StandardCharsets.UTF_8));
        session.close(true);
        await(() -> transferred().size() == 2, "the dropped upload ends");
        assertThat(transferred().get(1).direction()).isEqualTo(ServerEvent.Direction.UPLOAD);
        assertThat(transferred().get(1).complete()).isFalse();
        assertThat(shared.resolve("docs/b.txt")).doesNotExist();
    }

    @Test void changesAreReportedAndRefusalsAreNot() throws Exception {
        var sftp = start(true);
        sftp.mkdir("/docs/sub");
        sftp.rename("/docs/a.txt", "/docs/sub/a.txt");
        sftp.remove("/docs/sub/a.txt");
        sftp.rmdir("/docs/sub");
        assertThatThrownBy(() -> sftp.rmdir("/docs/missing")).isInstanceOf(IOException.class);
        await(() -> events.stream().filter(e -> e instanceof ServerEvent.Changed).count() == 4, "four changes");
        assertThat(events).filteredOn(e -> e instanceof ServerEvent.Changed).extracting(e -> ((ServerEvent.Changed) e).description())
            .containsExactly("created /docs/sub", "renamed /docs/a.txt to /docs/sub/a.txt", "deleted /docs/sub/a.txt", "deleted /docs/sub");
    }
```

- [ ] **Step 2: Run them to see them fail**

Run: `./gradlew :jasper-plugin-fileserver:test --tests 'dev.jasper.fileserver.engine.sftp.*'`
Expected: compilation fails, because `SftpActivity` has no two-argument constructor.

- [ ] **Step 3: Report from `SftpActivity`**

Replace `engine/sftp/SftpActivity.java`:

```java
package dev.jasper.fileserver.engine.sftp;

import dev.jasper.fileserver.engine.ServerEvent;
import dev.jasper.fileserver.engine.ServerEvents;
import dev.jasper.fileserver.share.PathRefusedException;
import dev.jasper.fileserver.share.ShareRoot;
import java.io.IOException;
import java.nio.file.CopyOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.sshd.server.session.ServerSession;
import org.apache.sshd.sftp.server.FileHandle;
import org.apache.sshd.sftp.server.Handle;
import org.apache.sshd.sftp.server.SftpEventListener;

/**
 * Watches every SFTP request.
 * <ul>
 * <li>Only harmless extensions run. The others (copy-file, md5-hash, space-available, …) would reach the disk
 *     without the jail's checks.</li>
 * <li>Each file handle is one transfer. A normal CLOSE marks an upload for commit; a dropped connection never
 *     does.</li>
 * <li>Changes are reported as FTP reports them.</li>
 * </ul>
 */
final class SftpActivity implements SftpEventListener {
    static final Set<String> EXTENSIONS = Set.of("posix-rename@openssh.com", "fsync@openssh.com", "limits@openssh.com", "version-select");
    private final ShareRoot share;
    private final ServerEvents events;
    private final Map<Handle, Track> transfers = new ConcurrentHashMap<>();

    private record Track(ServerEvents.Transfer transfer, boolean upload, long total) { }

    SftpActivity(ShareRoot share, ServerEvents events) { this.share = share; this.events = events; }

    @Override public void receivedExtension(ServerSession session, String extension, int id) {
        if (!EXTENSIONS.contains(extension)) throw new UnsupportedOperationException(extension + " is not supported");
    }

    @Override public void open(ServerSession session, String remoteHandle, Handle localHandle) {
        if (!(localHandle instanceof FileHandle file)) return;
        Set<StandardOpenOption> options = file.getOpenOptions();
        boolean upload = options.contains(StandardOpenOption.WRITE) || options.contains(StandardOpenOption.APPEND);
        long total = -1;
        if (!upload) {
            try { total = file.getFileChannel().size(); }
            catch (IOException unknown) { total = -1; }
        }
        var transfer = events.transfer(SftpLogins.remote(session), upload ? ServerEvent.Direction.UPLOAD : ServerEvent.Direction.DOWNLOAD,
            display(file.getFile()), total, () -> session.close(true));
        transfers.put(localHandle, new Track(transfer, upload, total));
    }

    @Override public void read(ServerSession session, String remoteHandle, FileHandle localHandle, long offset, byte[] data, int dataOffset,
                               int dataLen, int readLen, Throwable thrown) {
        Track track = transfers.get(localHandle);
        if (track != null && readLen > 0) track.transfer().add(readLen);
    }

    @Override public void written(ServerSession session, String remoteHandle, FileHandle localHandle, long offset, byte[] data, int dataOffset,
                                  int dataLen, Throwable thrown) {
        Track track = transfers.get(localHandle);
        if (track != null && thrown == null) track.transfer().add(dataLen);
    }

    @Override public void closing(ServerSession session, String remoteHandle, Handle localHandle) {
        if (localHandle instanceof FileHandle file && file.getFileChannel() instanceof UploadChannel upload) upload.complete();
    }

    /** After a normal CLOSE, whose close committed any upload. */
    @Override public void closed(ServerSession session, String remoteHandle, Handle localHandle, Throwable thrown) {
        Track track = transfers.remove(localHandle);
        if (track != null) track.transfer().finish(thrown == null && (track.upload() || track.transfer().bytes() >= track.total()));
    }

    /** A handle the client left open when its connection ended: the transfer did not finish. */
    @Override public void exiting(ServerSession session, Handle handle) {
        Track track = transfers.remove(handle);
        if (track != null) track.transfer().finish(false);
    }

    @Override public void created(ServerSession session, Path path, Map<String, ?> attrs, Throwable thrown) {
        if (thrown == null) events.changed(SftpLogins.remote(session), "created " + display(path));
    }

    @Override public void removed(ServerSession session, Path path, boolean isDirectory, Throwable thrown) {
        if (thrown == null) events.changed(SftpLogins.remote(session), "deleted " + display(path));
    }

    @Override public void moved(ServerSession session, Path srcPath, Path dstPath, Collection<CopyOption> opts, Throwable thrown) {
        if (thrown == null) events.changed(SftpLogins.remote(session), "renamed " + display(srcPath) + " to " + display(dstPath));
    }

    /** The client path, "/docs/a.txt"; never a host path. */
    private String display(Path path) {
        try { return "/" + share.clientPath(path); }
        catch (PathRefusedException outside) { return path.getFileName() == null ? "/" : path.getFileName().toString(); }
    }
}
```

In `SftpEngine.start`, replace `sftp.addSftpEventListener(new SftpActivity());` with:

```java
        sftp.addSftpEventListener(new SftpActivity(share, events));
```

- [ ] **Step 4: Run the tests**

Run: `./gradlew :jasper-plugin-fileserver:test --tests 'dev.jasper.fileserver.engine.sftp.*'`, then run it again (the tests use real connections, so check they are steady), then `./gradlew :jasper-plugin-fileserver:check`.
Expected: PASS each time.

- [ ] **Step 5: Commit**

```bash
git add plugins/fileserver/src
git commit -m "File Server: SFTP transfers, changes and logins in the activity log

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 6: SFTP in the editor — authorized keys and "Keys only"

**Files:**
- Modify: `plugins/fileserver/src/main/java/dev/jasper/fileserver/runtime/Engines.java`
- Modify: `plugins/fileserver/src/main/java/dev/jasper/fileserver/ui/ServerEditor.java`
- Modify: `plugins/fileserver/src/main/java/dev/jasper/fileserver/FileServerPlugin.java` (host key, key sources)
- Test: `runtime/EnginesTest.java`, `ui/ServerEditorTest.java`

**Interfaces:**
- Consumes: `SftpEngine` and `HostKey` (Tasks 3–5) and `AuthorizedKeys` (Task 2).
- Produces:
  - `Engines.AVAILABLE = Set.of(Protocol.HTTP, Protocol.FTP, Protocol.SFTP)`.
  - `Engines.factory(LoginThrottle, Supplier<List<ServerAddresses.Nic>>, TlsMaterial, Supplier<PortRange>, HostKey)` replaces the four-argument factory.
  - `public record ServerEditor.KeySources(Supplier<Optional<String>> fromFile, Supplier<Optional<String>> fromClipboard)` with `public static final KeySources NONE`.
  - The `ServerEditor` constructor gains a `KeySources keySources` parameter right after `folderChooser`.
  - New package-private editor fields: `keyLines` (`DefaultListModel<String>`), `keys` (`JList<String>`), and the buttons `addKeyFile` ("Add from File…"), `pasteKeys` ("Paste") and `removeKey` ("Remove").
  - `FileServerPlugin.hostKey()` (package-private) returns the plugin's `HostKey`, kept in `context.dataDirectory().resolve("hostkey")`.
  - `FileServerPlugin.pasted()` (package-private static) returns the clipboard's text, if any.

- [ ] **Step 1: Write the failing tests**

In `EnginesTest.java`, add the imports `dev.jasper.fileserver.engine.sftp.HostKey` and `dev.jasper.fileserver.engine.sftp.SftpEngine`, and replace the test with:

```java
    @Test void theFactoryBuildsHttpFtpAndSftpEngines(@TempDir Path folder) throws Exception {
        var factory = Engines.factory(new LoginThrottle(new TestClock()), List::of, new TlsMaterial(folder.resolve("tls"), new TestClock(), () -> ""),
            () -> new PortRange(50000, 50100), new HostKey(folder.resolve("hostkey")));
        var events = new ServerEvents(new TestClock(), e -> { });
        var share = new ShareRoot(folder, true);
        assertThat(Engines.AVAILABLE).containsExactlyInAnyOrder(Protocol.HTTP, Protocol.FTP, Protocol.SFTP);
        assertThat(factory.create(ServerDefinition.create("h", Protocol.HTTP, folder), share, ResolvedLogin.anonymous(), events)).isInstanceOf(HttpEngine.class);
        assertThat(factory.create(ServerDefinition.create("f", Protocol.FTP, folder), share, ResolvedLogin.anonymous(), events)).isInstanceOf(FtpEngine.class);
        assertThat(factory.create(ServerDefinition.create("s", Protocol.SFTP, folder), share, ResolvedLogin.password("u", "p".toCharArray()), events))
            .isInstanceOf(SftpEngine.class);
        assertThatThrownBy(() -> factory.create(ServerDefinition.create("t", Protocol.TFTP, folder), share, ResolvedLogin.anonymous(), events))
            .isInstanceOf(EngineFailure.class).hasMessage("TFTP servers are not available in this version of File Server");
    }
```

In `ServerEditorTest.java`:
- Add the imports `java.util.Collections`.
- In both existing helpers (`editor(...)` and `ftpEditor(...)`), insert `ServerEditor.KeySources.NONE,` right after the folder chooser argument (`() -> Optional.of(folder),`).
- Add:

```java
    static final String ED25519 = "ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAIAEgwWITbeAjMHfs63n4fe6AxR8buxH3Wx+IdWdotPF0 alice@laptop";
    static final String ECDSA = "ecdsa-sha2-nistp256 AAAAE2VjZHNhLXNoYTItbmlzdHAyNTYAAAAIbmlzdHAyNTYAAABBBCKLV4ChGhVzArLw7brgwvIOQAjpwGqDaTifUtUTLxNQCWVdgGVtTjhhZSbIMKVdDUtRyBDkfjMCE3d4mznEAa8=";

    ServerEditor sftpEditor(Optional<ServerDefinition> initial, boolean existing, String file, String clipboard) {
        return new ServerEditor(List.of(), initial, existing, Set.of(Protocol.HTTP, Protocol.FTP, Protocol.SFTP), List.of(), false,
            id -> Optional.empty(), () -> CompletableFuture.completedFuture(Optional.empty()), () -> Optional.of(folder),
            new ServerEditor.KeySources(() -> Optional.ofNullable(file), () -> Optional.ofNullable(clipboard)), false,
            saved::add, () -> cancelled = true);
    }

    @Test void sftpTakesAuthorizedKeysAndMayBeKeysOnly() {
        var editor = sftpEditor(Optional.empty(), false, "# my keys\n" + ED25519 + "\n", ECDSA + " ");
        assertThat(editor.addKeyFile.isEnabled()).as("HTTP has no keys").isFalse();
        assertThat(editor.pasteKeys.isEnabled()).isFalse();
        editor.protocol.setSelectedItem(Protocol.SFTP);
        assertThat(editor.port.getText()).isEqualTo("2222");
        assertThat(editor.session.isSelected()).as("no Vault: a session password").isTrue();
        assertThat(editor.anonymous.getText()).isEqualTo("Keys only (no password)");
        assertThat(editor.anonymous.isEnabled()).isTrue();
        assertThat(editor.removeKey.isEnabled()).as("nothing selected").isFalse();
        editor.addKeyFile.doClick();
        editor.pasteKeys.doClick();
        editor.pasteKeys.doClick();
        assertThat(Collections.list(editor.keyLines.elements())).as("a listed key is not added twice").containsExactly(ED25519, ECDSA);
        editor.keys.setSelectedIndex(1);
        assertThat(editor.removeKey.isEnabled()).isTrue();
        editor.removeKey.doClick();
        editor.anonymous.doClick();
        editor.name.setText("drop");
        editor.folder.setText(folder.toString());
        editor.save.doClick();
        assertThat(saved).singleElement().satisfies(definition -> {
            assertThat(definition.protocol()).isEqualTo(Protocol.SFTP);
            assertThat(definition.login()).isEqualTo(Login.ANONYMOUS);
            assertThat(definition.authorizedKeys()).containsExactly(ED25519);
        });
    }

    @Test void keysOnlyNeedsAKeyAndBadKeyTextIsExplained() {
        var editor = sftpEditor(Optional.empty(), false, "not a key", null);
        editor.protocol.setSelectedItem(Protocol.SFTP);
        editor.addKeyFile.doClick();
        assertThat(editor.message.getText()).isEqualTo("Line 1: Not an OpenSSH public key (for example ssh-ed25519 AAAA…)");
        editor.pasteKeys.doClick();
        assertThat(editor.keyLines.isEmpty()).as("an empty clipboard adds nothing").isTrue();
        editor.anonymous.doClick();
        editor.name.setText("keys");
        editor.folder.setText(folder.toString());
        editor.save.doClick();
        assertThat(editor.message.getText()).isEqualTo("SFTP needs a password or authorized keys");
        assertThat(saved).isEmpty();
    }

    @Test void editingAnSftpServerKeepsItsKeysAndLogin() {
        var original = new ServerDefinition(UUID.randomUUID(), "keys", Protocol.SFTP, folder, Bind.LOOPBACK, 2222, Optional.empty(), false,
            Login.ANONYMOUS, List.of(ED25519), false, false, false);
        var editor = sftpEditor(Optional.of(original), true, null, null);
        assertThat(editor.anonymous.isSelected()).isTrue();
        assertThat(editor.keys.getModel().getSize()).isEqualTo(1);
        editor.save.doClick();
        assertThat(saved).singleElement().isEqualTo(original);
    }
```

- [ ] **Step 2: Run them to see them fail**

Run: `./gradlew :jasper-plugin-fileserver:test --tests 'dev.jasper.fileserver.runtime.EnginesTest' --tests 'dev.jasper.fileserver.ui.ServerEditorTest'`
Expected: compilation fails, because there is no five-argument factory, no `KeySources`, and no key controls.

- [ ] **Step 3: Serve SFTP from the factory**

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
import dev.jasper.fileserver.engine.sftp.HostKey;
import dev.jasper.fileserver.engine.sftp.SftpEngine;
import dev.jasper.fileserver.engine.tls.TlsMaterial;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

/** Which protocols this version serves, and the factory that builds their engines. */
public final class Engines {
    public static final Set<Protocol> AVAILABLE = Set.of(Protocol.HTTP, Protocol.FTP, Protocol.SFTP);

    private Engines() { }

    /** {@code passiveDefault} is read each time an FTP engine is built, so a changed setting applies to the next start. */
    public static ServerRegistry.EngineFactory factory(LoginThrottle throttle, Supplier<List<ServerAddresses.Nic>> nics, TlsMaterial tls,
                                                       Supplier<PortRange> passiveDefault, HostKey hostKey) {
        return (definition, share, login, events) -> switch (definition.protocol()) {
            case HTTP -> new HttpEngine(definition, share, login, events, throttle, nics, tls);
            case FTP -> new FtpEngine(definition, share, login, events, throttle, nics, passiveDefault.get(), tls);
            case SFTP -> new SftpEngine(definition, share, login, events, throttle, nics, hostKey);
            case TFTP -> throw new EngineFailure(definition.protocol().label() + " servers are not available in this version of File Server");
        };
    }
}
```

In `FileServerPlugin.java`:
- Add the field `private HostKey hostKey;` and the import `dev.jasper.fileserver.engine.sftp.HostKey`.
- In `start`, next to the `tls` line, add `hostKey = new HostKey(context.dataDirectory().resolve("hostkey"));`.
- Pass `hostKey` as the factory's last argument.
- Add `HostKey hostKey() { return hostKey; }`.

- [ ] **Step 4: Add the keys to the editor**

In `ServerEditor.java`:

1. Add the imports `dev.jasper.fileserver.auth.AuthorizedKeys`, `java.util.Collections`, `javax.swing.DefaultListCellRenderer`, `javax.swing.DefaultListModel`, `javax.swing.JList`, `javax.swing.JScrollPane` and `javax.swing.ListSelectionModel`. The `ui` package may depend on `auth`; if `ui/package-info.java` lists its allowed dependencies, add `auth` with "(authorized key lines)".

2. Add the record and the fields:

```java
    /** Where "Add from File…" and "Paste" get authorized-key text; each returns empty when there is nothing. */
    public record KeySources(Supplier<Optional<String>> fromFile, Supplier<Optional<String>> fromClipboard) {
        public static final KeySources NONE = new KeySources(Optional::empty, Optional::empty);
    }
```

```java
    final DefaultListModel<String> keyLines = new DefaultListModel<>();
    final JList<String> keys = new JList<>(keyLines);
    final JButton addKeyFile = new JButton("Add from File…"), pasteKeys = new JButton("Paste"), removeKey = new JButton("Remove");
```

3. Change the constructor signature to take `KeySources keySources` after `Supplier<Optional<Path>> folderChooser`. In the `if (initial.isPresent())` branch, after the `passive.setText(...)` line, add:

```java
            definition.authorizedKeys().forEach(keyLines::addElement);
```

4. In the form, after `row(form, at, "Login", login);`, add:

```java
        keys.setVisibleRowCount(3);
        keys.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        keys.setCellRenderer(new DefaultListCellRenderer() {
            { putClientProperty("html.disable", Boolean.TRUE); }
            @Override public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean selected, boolean focused) {
                return super.getListCellRendererComponent(list, keySummary(String.valueOf(value)), index, selected, focused);
            }
        });
        var keyPanel = new JPanel();
        keyPanel.setLayout(new BoxLayout(keyPanel, BoxLayout.Y_AXIS));
        var keyScroll = new JScrollPane(keys);
        keyScroll.setAlignmentX(LEFT_ALIGNMENT);
        keyPanel.add(keyScroll);
        keyPanel.add(line(addKeyFile, pasteKeys, removeKey));
        row(form, at, "Authorized keys", keyPanel);
```

5. Register the listeners next to the others:

```java
        addKeyFile.addActionListener(event -> addKeys(keySources.fromFile().get()));
        pasteKeys.addActionListener(event -> addKeys(keySources.fromClipboard().get()));
        removeKey.addActionListener(event -> {
            int[] chosen = keys.getSelectedIndices();
            for (int i = chosen.length - 1; i >= 0; i--) keyLines.remove(chosen[i]);
            refresh();
        });
        keys.addListSelectionListener(event -> refresh());
```

6. Add the helpers:

```java
    /** Adds every key in {@code text} that is not listed yet; bad text is explained, and nothing is added from it. */
    private void addKeys(Optional<String> text) {
        if (text.isEmpty()) return;
        try {
            for (AuthorizedKeys.Key key : AuthorizedKeys.parseAll(text.get()))
                if (!keyLines.contains(key.line())) keyLines.addElement(key.line());
            message.setText(" ");
        } catch (IllegalArgumentException bad) {
            message.setText(bad.getMessage());
        }
        refresh();
    }

    private static String keySummary(String line) {
        try { return AuthorizedKeys.parse(line).summary(); }
        catch (IllegalArgumentException unreadable) { return line; }
    }
```

7. In `build()`:
   - Delete the line `boolean same = base != null && base.protocol() == chosen;`. Since FS-2, only the authorized keys used it.
   - Replace `same ? base.authorizedKeys() : List.of()` with `chosen == Protocol.SFTP ? Collections.list(keyLines.elements()) : List.of()`.

8. Replace `refresh()` with:

```java
    private void refresh() {
        Protocol chosen = selectedProtocol();
        boolean ftp = chosen == Protocol.FTP, sftp = chosen == Protocol.SFTP;
        passive.setEnabled(ftp);
        useTls.setEnabled(chosen.allowsTls());
        if (!chosen.allowsTls()) useTls.setSelected(false);
        requireTls.setEnabled(ftp && useTls.isSelected());
        if (!requireTls.isEnabled()) requireTls.setSelected(false);
        boolean login = chosen.hasLogin();
        boolean noPassword = chosen.allowsAnonymous() || sftp; // SFTP's "anonymous" means keys only
        anonymous.setText(!login ? "No login (TFTP has none)" : sftp ? "Keys only (no password)" : "Anonymous");
        anonymous.setEnabled(login && noPassword);
        vault.setEnabled(login && vaultPresent);
        chooseAccount.setEnabled(login && vaultPresent);
        session.setEnabled(login);
        if (!login) anonymous.setSelected(true);
        else if (anonymous.isSelected() && !noPassword) (vaultPresent ? vault : session).setSelected(true);
        keys.setEnabled(sftp);
        addKeyFile.setEnabled(sftp);
        pasteKeys.setEnabled(sftp);
        removeKey.setEnabled(sftp && !keys.isSelectionEmpty());
        int value = parsedPort();
        portHint.setText(linux && value > 0 && value < 1024
            ? "Ports below 1024 need root or CAP_NET_BIND_SERVICE on Linux — try " + chosen.suggestedPort(value) : " ");
        Login shown = anonymous.isSelected() ? Login.ANONYMOUS : vault.isSelected() ? new Login.Vault(new UUID(0, 0)) : Login.SESSION;
        boolean tls = chosen.allowsTls() && useTls.isSelected();
        List<String> list = ServerRows.warnings(chosen, tls, shown, writable.isSelected(), selectedBind());
        warnings.setText(list.isEmpty() ? " " : "⚠ " + String.join(" · ", list));
    }
```

   Compare it with the current method before replacing it. The only changes are the `sftp` and `noPassword` lines, the anonymous label and its enabling and fallback, and the four key-control lines. Keep any other line the current method has.

In `FileServerPlugin.edit(...)`, pass the key sources after the folder chooser:

```java
            new ServerEditor.KeySources(() -> keyFiles(window), FileServerPlugin::pasted),
```

and add:

```java
    /** The text of public key files the user picks; a file that can't be read is reported and skipped. */
    private Optional<String> keyFiles(WindowHandle window) {
        List<Path> chosen = context.windows().chooseFiles(window, "Public keys to allow", Optional.of(environment.home().resolve(".ssh")));
        var text = new StringBuilder();
        for (Path file : chosen) {
            try { text.append(Files.readString(file)).append('\n'); }
            catch (IOException | RuntimeException failure) { context.notices().error("Could not read " + file + ": " + message(failure)); }
        }
        return text.isEmpty() ? Optional.empty() : Optional.of(text.toString());
    }

    /** The clipboard's text; empty when there is none or no clipboard. */
    static Optional<String> pasted() {
        try {
            Object data = Toolkit.getDefaultToolkit().getSystemClipboard().getData(DataFlavor.stringFlavor);
            return data instanceof String text && !text.isBlank() ? Optional.of(text) : Optional.empty();
        } catch (HeadlessException | IllegalStateException | UnsupportedFlavorException | IOException unavailable) {
            return Optional.empty();
        }
    }
```

Add the imports `java.awt.datatransfer.DataFlavor` and `java.awt.datatransfer.UnsupportedFlavorException`. `Files`, `IOException`, `Path`, `Toolkit` and `HeadlessException` are already imported. `chooseFiles(WindowOwner, String, Optional<Path>)` accepts the `WindowHandle`, as `chooseDirectory` does.

- [ ] **Step 5: Run the tests**

Run: `./gradlew :jasper-plugin-fileserver:check`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add plugins/fileserver/src
git commit -m "File Server: SFTP servers in the editor, with authorized keys and a keys-only login

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 7: The host key in the panel, Serve Current Folder over SFTP, version 0.3.0

**Files:**
- Modify: `plugins/fileserver/src/main/java/dev/jasper/fileserver/ui/FileServersPanel.java`
- Modify: `plugins/fileserver/src/main/java/dev/jasper/fileserver/FileServerPlugin.java`
- Modify: `plugins/fileserver/src/main/resources/plugin.toml`
- Modify: `jasper-app/src/test/java/dev/jasper/app/plugins/PluginZipsTest.java`
- Test: `ui/FileServersPanelTest.java`, `ui/UiTestAccess.java`, `FileServerPluginTest.java`

**Interfaces:**
- Consumes: `HostKey.fingerprint()` and `cachedFingerprint()` (Task 3), `FileServerPlugin.hostKey()` (Task 6), and the plugin's `confirm(...)` helper and `environment.clipboard()`.
- Produces:
  - `FileServersPanel.setHostKeyFingerprint(Optional<String>)`.
  - Package-private `FileServersPanel.cardHostKey` (`JTextArea`, built with `note(...)`), shown only for SFTP servers.
  - "Show Fingerprints…" is enabled for TLS servers and for SFTP servers.
  - `FileServerPlugin.SERVE_SFTP = ID + ".serve-sftp"`, titled "Serve Current Folder over SFTP".
  - `UiTestAccess.cardHostKey(FileServersPanel)`.

- [ ] **Step 1: Write the failing tests**

In `FileServersPanelTest.java`, add:

```java
    @Test void anSftpServerShowsTheHostKeyFingerprint() {
        var panel = panel();
        var definition = new ServerDefinition(UUID.randomUUID(), "keys", Protocol.SFTP, HOME.resolve("work"), Bind.LOOPBACK, 2222,
            Optional.empty(), false, Login.SESSION, List.of(), false, false, false);
        views.add(new ServerRegistry.View(definition, false, ServerState.STOPPED, List.of(), Optional.empty(), false));
        view("plain", ServerState.STOPPED, false, false, Bind.LOOPBACK, List.of(), Optional.empty());
        panel.setServers(views, Optional.empty());
        assertThat(panel.cardHostKey.isVisible()).isTrue();
        assertThat(panel.cardTls.isVisible()).isFalse();
        assertThat(panel.cardHostKey.getText()).isEqualTo("SSH host key: created when the server first starts");
        panel.setHostKeyFingerprint(Optional.of("SHA256:abc"));
        assertThat(panel.cardHostKey.getText()).isEqualTo("SSH host key SHA256:abc");
        assertThat(titles(panel.menuFor(0))).contains("Show Fingerprints…");
        click(panel.menuFor(0), "Show Fingerprints…");
        assertThat(events).containsExactly("fingerprints keys");
        panel.select(views.get(1).definition().id());
        assertThat(panel.cardHostKey.isVisible()).as("only SFTP has a host key").isFalse();
    }
```

In `UiTestAccess.java`, add:

```java
    public static JTextArea cardHostKey(FileServersPanel panel) { return panel.cardHostKey; }
```

In `FileServerPluginTest.java`, change `INFO`'s version to `"0.3.0"`, and add:

```java
    @Test void servesTheFocusedFolderOverSftpWithASessionPassword() throws Exception {
        Path project = Files.createDirectories(home.resolve("project"));
        try (var host = new FakePluginHost(root)) {
            var plugin = plugin(port -> port != 2222);
            host.start(INFO, Set.of(), Set.of(), plugin);
            settle(host);
            assertThat(host.actions()).contains("dev.jasper.fileserver.serve-sftp|Serve Current Folder over SFTP|true");
            UUID window = host.addTerminalWindow(), tab = host.addTerminalTab(window, "zsh");
            UUID local = host.addTerminalPane(tab, pane(Optional.of(project)));
            host.openPanel(FileServerPlugin.PANEL, window);
            assertThat(host.invoke(FileServerPlugin.SERVE_SFTP, window, local)).isTrue();
            await(host, () -> plugin.registry().servers().stream().noneMatch(v -> v.state().phase() == Phase.STARTING), "the start settles");
            var served = plugin.registry().servers().getFirst();
            assertThat(served.temporary()).isTrue();
            assertThat(served.definition().name()).isEqualTo("project (SFTP)");
            assertThat(served.definition().protocol()).isEqualTo(Protocol.SFTP);
            assertThat(served.definition().folder()).isEqualTo(project);
            assertThat(served.definition().port()).as("2222 was taken").isEqualTo(2223);
            assertThat(served.definition().writable()).isFalse();
            assertThat(served.definition().login()).isEqualTo(Login.SESSION);
            plugin.registry().stopAll();
            await(host, () -> plugin.registry().active() == 0, "everything stops");
        }
    }

    @Test void showsTheHostKeyFingerprintAndCopiesIt() throws Exception {
        try (var host = new FakePluginHost(root)) {
            var plugin = plugin();
            host.start(INFO, Set.of(), Set.of(), plugin);
            settle(host);
            UUID window = host.addTerminalWindow();
            var panel = (FileServersPanel) host.openPanel(FileServerPlugin.PANEL, window);
            var base = ServerDefinition.create("keys", Protocol.SFTP, home);
            var sftp = new ServerDefinition(base.id(), "keys", Protocol.SFTP, home, Bind.LOOPBACK, Ports.free(), Optional.empty(), false,
                Login.SESSION, List.of(), false, false, false);
            plugin.store().put(sftp);
            settle(host);
            assertThat(UiTestAccess.cardHostKey(panel).getText()).isEqualTo("SSH host key: created when the server first starts");
            UiTestAccess.toggle(panel).doClick();
            await(host, () -> plugin.registry().server(sftp.id()).orElseThrow().state().phase() == Phase.RUNNING, "the SFTP server runs");
            String fingerprint = plugin.hostKey().cachedFingerprint().orElseThrow();
            assertThat(fingerprint).startsWith("SHA256:");
            assertThat(UiTestAccess.cardHostKey(panel).getText()).isEqualTo("SSH host key " + fingerprint);
            click(UiTestAccess.menuFor(panel, 0), "Show Fingerprints…");
            await(host, () -> plugin.currentConfirm() != null, "the fingerprint dialog opens");
            assertThat(host.windows()).containsExactly("dialog|Fingerprints|true");
            assertThat(UiTestAccess.message(plugin.currentConfirm()).getText()).isEqualTo("SSH host key: " + fingerprint);
            UiTestAccess.confirm(plugin.currentConfirm()).doClick();
            settle(host);
            assertThat(copied).containsExactly(fingerprint);
            plugin.registry().stopAll();
            await(host, () -> plugin.registry().active() == 0, "everything stops");
        }
    }
```

In `PluginZipsTest.java`, extend the File Server branch's `.contains(...)` with `"sshd-core-2.19.0.jar", "sshd-sftp-2.19.0.jar"`.

- [ ] **Step 2: Run them to see them fail**

Run: `./gradlew :jasper-plugin-fileserver:test`
Expected: compilation fails, because there is no `cardHostKey`, `setHostKeyFingerprint` or `SERVE_SFTP`.

- [ ] **Step 3: Show the host key in the panel**

In `FileServersPanel.java`:
1. Add the fields `final JTextArea cardHostKey = note("");` and `private Optional<String> hostKeyFingerprint = Optional.empty();`.
2. Add `cardHostKey` to the card loop, after `cardTls`.
3. Add:

```java
    /** The SSH host key's fingerprint, or empty before it exists; shown for SFTP servers. */
    public void setHostKeyFingerprint(Optional<String> fingerprint) {
        if (fingerprint.equals(hostKeyFingerprint)) return;
        hostKeyFingerprint = fingerprint;
        refreshCard();
    }
```

4. In `refreshCard()`, after the `cardTls` lines, add:

```java
            cardHostKey.setText(hostKeyFingerprint.map(fingerprint -> "SSH host key " + fingerprint)
                .orElse("SSH host key: created when the server first starts"));
            cardHostKey.setVisible(definition.protocol() == Protocol.SFTP);
```

   Add the import `dev.jasper.fileserver.definition.Protocol` if it is missing.
5. In `menuFor`, change the fingerprints item's enabling to:

```java
        fingerprints.setEnabled(view.definition().tls() || view.definition().protocol() == Protocol.SFTP);
```

- [ ] **Step 4: Wire the plugin**

In `FileServerPlugin.java`:
1. Add `public static final String SERVE_SFTP = ID + ".serve-sftp";`. After the `SERVE_FTP` registration, add:

```java
        context.actions().register(ActionSpec.of(SERVE_SFTP, "Serve Current Folder over SFTP").withIcon(icon)
                .withKeywords(List.of("file server", "share", "serve", "sftp", "ssh", "folder")),
            invoked -> serve(invoked, Protocol.SFTP));
```

   `ServerDefinition.create` gives SFTP a session password, as the spec asks.
2. In the background block at the end of `start` that reads the TLS fingerprint, also read the host key's:

```java
            try { hostKey.fingerprint(); }
            catch (EngineFailure unreadable) { /* reported when an SFTP server starts or the fingerprints are shown */ }
```

3. In `createPanel` and in `refreshPanels()`, next to `setTlsFingerprint(...)`, add `panel.setHostKeyFingerprint(hostKey.cachedFingerprint());`.
4. Make `showFingerprints` handle SFTP servers first. Replace its opening `if (registry.server(id).isEmpty()) return;` with:

```java
        Optional<ServerRegistry.View> server = registry.server(id);
        if (server.isEmpty()) return;
        if (server.get().definition().protocol() == Protocol.SFTP) {
            showHostKey(window);
            return;
        }
```

   and add:

```java
    /** Shows the SSH host key's fingerprint, offering to copy it; the file I/O runs off the UI thread. */
    private void showHostKey(WindowHandle window) {
        context.background().execute(() -> {
            Optional<String> fingerprint;
            String text;
            try {
                fingerprint = hostKey.fingerprint();
                text = fingerprint.map(value -> "SSH host key: " + value)
                    .orElse("No SSH host key yet: Jasper creates one when an SFTP server first starts.");
            } catch (EngineFailure unreadable) {
                fingerprint = Optional.empty();
                text = unreadable.getMessage();
            }
            Optional<String> copy = fingerprint;
            String shown = text;
            environment.ui().execute(() -> {
                if (stopped || !window.isOpen()) return;
                refreshPanels();
                confirm(window, "Fingerprints", shown, "Copy Fingerprint", "Close", () -> copy.ifPresent(environment.clipboard()), () -> { });
            });
        });
    }
```

- [ ] **Step 5: Version and description**

In `plugin.toml`, set `version = "0.3.0"` and:

```toml
description = "Share a local folder with other computers over HTTP, HTTPS, FTP, FTPS or SFTP: named servers in a File Servers panel, read-only and local-only by default, with an activity log, Vault logins and Serve Current Folder."
```

- [ ] **Step 6: Run the tests**

Run: `./gradlew :jasper-plugin-fileserver:check :jasper-app:test --tests 'dev.jasper.app.plugins.PluginZipsTest'`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add plugins/fileserver jasper-app/src/test/java/dev/jasper/app/plugins/PluginZipsTest.java
git commit -m "File Server 0.3.0: the SSH host key in the panel and Serve Current Folder over SFTP

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 8: Documentation, status and the full check

**Files:**
- Modify: `docs/file-server.md`, `docs/README.md`, `docs/STATUS.md`
- Modify: `docs/superpowers/plans/2026-10-01-file-server-fs3.md` (status banner)

- [ ] **Step 1: Update the user guide**

In `docs/file-server.md`:
- The opening names HTTP, HTTPS, FTP, FTPS and SFTP, with TFTP to follow.
- In "Safe by default", add SFTP's default port, 2222.
- Add this section after "HTTPS and the certificate":

```markdown
## SFTP

An SFTP server shares the folder with any SFTP client (`sftp`, `scp` from OpenSSH 9 on, FileZilla, Cyberduck,
WinSCP). It serves file transfer only: no shell, no commands and no port forwarding.

- **Logins.** A Vault account or a session password, and/or **authorized keys**: OpenSSH public key lines added in
  the editor with Add from File… (for example `~/.ssh/id_ed25519.pub`) or Paste. A server with a password login
  accepts keys only for that user name. **Keys only (no password)** accepts any user name with an authorized key.
  Key lines with options (`command=`, `from=`, `restrict`) are refused. Wrong passwords are throttled as for FTP; an
  offered key that isn't authorized doesn't count.
- **Host key.** File Server creates an Ed25519 host key the first time an SFTP server starts and keeps it in
  `data/hostkey` (owner-only). Clients ask you to trust it on first connection; compare the fingerprint with the
  server card's "SSH host key SHA256:…" or Show Fingerprints…. If the key file becomes unreadable, a new key replaces
  it and clients warn about the change.
- **The shared folder is a jail.** `..` stops at the top; links that lead outside are invisible and cannot be
  followed, and links can't be created or read. Server-side copy, checksum and disk-space requests are not supported.
- **Uploads** replace a file only when the client closes it normally; a dropped connection leaves the old file.
  Resuming, appending and writing part of a file happen in place.
- On a writable server, a client may change a file's times and permissions, never its owner or group.
```

- Add `data/hostkey` to "Files".

In `docs/README.md`, change the File Server line to: "share a folder over HTTP, HTTPS, FTP, FTPS and SFTP from the File Servers panel (TFTP follows)."

- [ ] **Step 2: Update `docs/STATUS.md`**

Add a top entry under the current-state header, with today's date in the header:

```markdown
- **File Server FS-3: SFTP** (branch `claude/file-server-fs3`, plan `docs/superpowers/plans/2026-10-01-file-server-fs3.md`):
  File Server 0.3.0 serves SFTP on Apache MINA sshd 2.19.0 with a persistent Ed25519 host key (`data/hostkey`,
  fingerprint on the server card and in Show Fingerprints…), password, authorized-key and keys-only logins (through
  `LoginThrottle`; unauthorized keys don't count), and only the SFTP subsystem (no shell, exec, forwarding or
  keyboard-interactive). The jail is `engine.sftp.ShareAccessor`: every path through `ClientPaths` and
  `ShareRoot.locate`, every operation through `ShareRoot`, links refused, only harmless extensions. Uploads commit on
  a normal CLOSE. Editor: authorized keys (Add from File…, Paste, Remove) and "Keys only". Serve Current Folder over
  SFTP. Decisions: see the plan header. Deferred: sshd replaces an unreadable host key silently; no "regenerate host
  key". GUI checks pending: OpenSSH `sftp`/`scp`, FileZilla and Cyberduck with a password and with a key; the
  first-connection host key prompt matching the card; refused shell (`ssh -p 2222 host`); the editor's key list.
```

Add the exact test counts after Step 3.

- [ ] **Step 3: Run the full check and the hygiene script**

Run: `./gradlew check`
Expected: BUILD SUCCESSFUL. Read the counts from the XML reports.

Run the AGENTS.md source-hygiene script from the repository root. Expected: no output.

- [ ] **Step 4: Update the plan banner**

Under the plan's title, add a status line: "**Status:** executed on `claude/file-server-fs3` (date), `./gradlew check` green." List any deviations from the plan text, with the reason for each.

- [ ] **Step 5: Commit**

```bash
git add docs
git commit -m "File Server FS-3: documentation and status

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```
