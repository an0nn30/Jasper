# Editor Plugin, Plan B: SFTP Editing through Remote — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Status:** Not executed. Plan A is merged into `main` (`0f9b960a`). Branch: `claude/editor-plugin-b`.

**Goal:** Double-clicking a file in Remote's SFTP panel opens it in the Editor, and saving writes it back to the host through the Editor's write contract.

**Architecture:**
- **Endpoints (`dev.jasper.remote.sftp`).** `FileEntry` gains `uid`/`gid`. `FileEndpoint` gains two methods, implemented by `LocalEndpoint` and `SftpEndpoint`:
  - `create(path, permissions)`: an exclusive create whose mode applies as the file is created;
  - `writable(path)`.
- `SftpEndpoint.publish` reports a server without posix-rename as `AtomicMoveNotSupportedException`.
- **The new package `dev.jasper.remote.edit`:**
  - `SafeWrite`: the write contract over any `FileEndpoint`.
  - `EditChannels`: one queued SFTP channel per connection identity.
  - `RemoteDocument`: implements the Editor's `EditorDocument`.
  - `RemoteEditing`: opens documents and resolves links for the panel.
  - Only `RemoteDocument` and `RemoteEditing` name Editor types. They load only when the Editor is installed.
- **The SFTP panel.**
  - Double-click or Enter opens a file, or a link to a file, in the Editor.
  - A right-click **Open in Editor** item opens up to 20 selected files at a time.
  - `RemotePlugin` looks the Editor up once at start, guarded like Vault.

**Tech Stack:** Java 25 (JBR), Swing, Apache MINA sshd 2.19.0 (SFTP v3), JUnit 5, AssertJ 3.27.7.

**Spec:** `docs/superpowers/specs/2026-09-27-jasper-editor-plugin-design.md` (sections 1.3 and 6; the write contract is section 4.4). Task 1 amends sections 4.4, 6.1, 6.2 and 6.4. **The amendments need the user's approval together with this plan.**

**Planning evidence.** These were checked on 2026-09-27 against the loopback test server (MINA's `SftpSubsystemFactory` over a `VirtualFileSystemFactory`), with a throwaway probe that was then deleted:
- A hand-built `SSH_FXP_OPEN` with `SSH_FXF_WRITE|SSH_FXF_CREAT|SSH_FXF_EXCL` and a permissions attribute of `0600` returns `SSH_FXP_HANDLE`, and the file is created `rw-------`. MINA's `SftpClient.open` sends no attributes, so it cannot do this.
- `lstat` reports `Attribute.UidGid`, and the values equal the local `unix:uid`/`unix:gid` (501/20).
- The same exclusive open on an existing file returns status 11 (`SSH_FX_FILE_ALREADY_EXISTS`). Real OpenSSH answers `SSH_FX_FAILURE` in v3, and nothing below depends on the code.
- Opening a `r--r--r--` file for writing returns status 3 (`SSH_FX_PERMISSION_DENIED`, "Permission denied").
- The server supports `posix-rename@openssh.com` unless `SftpModuleProperties.OPENSSH_EXTENSIONS` is set to `","`. `SftpEndpointTest.unsupportedAtomicReplaceLeavesBothFilesUnchanged` already does this.
- An optional requirement works like History → Snippets and Remote → Vault:
  - the host starts required plugins first;
  - `services().find` is called once at start;
  - `NoClassDefFoundError` is caught when the plugin is absent, because Remote's class loader then cannot see `dev.jasper.editor.api`.
- `BundledSamplePluginTest`'s remote-only runtime already starts Remote without Vault or Editor through the real `PluginClassLoader`.

**Planning decisions** (rulings where the spec leaves room; the spec is binding otherwise):
1. **Owner-only sibling over SFTP (spec amendment).** Plan A's final review made the local safe write create its sibling `rw-------` before any bytes, then copy the target's mode just before the rename, and refuse a read-only target. Plan B applies the same rule remotely.
   - `FileEndpoint.create(path, 0600)` creates the sibling. Over SFTP it is a hand-built `SSH_FXP_OPEN` with a permissions attribute.
   - A missing target gets `0666`, so the server's umask decides, as for any new file.
   - `create(…)` then `metadata(…)` would leave a window in which another user could open the empty file and keep reading as it fills.
2. **Read-only refusal through `FileEndpoint.writable` (spec amendment).**
   - Locally it is `Files.isWritable`.
   - Over SFTP it opens the file for writing and closes it without writing. That is exact under ACLs and root, where mode bits and an unknown uid are not.
3. **`AtomicMoveNotSupportedException` signals a missing posix-rename.** It is the JDK's type, and `LocalEndpoint`'s `Files.move(…, ATOMIC_MOVE)` already throws it. `SafeWrite` then removes the sibling and writes in place.
4. **The SFTP panel lists only remote hosts (spec amendment to §6.4).** `SftpController.Capture` always carries a `ConnectionIdentity`, so "the local listing through `Editor.open(Path)`" does not apply.
5. **Links are resolved component by component.**
   - `stat` refuses a path through a linked folder, and `canonical` leaves the last component unresolved.
   - A fast path stats the whole path first; browsed paths have real folders.
   - `..` inside a link target is applied after that link is resolved, as POSIX does.
6. **A failed channel open fails every operation queued for that identity.** This means one sign-in attempt, not one per tab. Twenty tabs opened over a dead connection would otherwise prompt twenty times.
7. **Any failed operation discards its channel,** and the next opens a fresh one. An aborted or timed-out `SftpEndpoint` is unusable, and telling benign failures apart is not worth it.
8. **`EditChannels` finishes its bookkeeping before completing a result.** A caller woken by the result then finds the queue settled. This matters under the tests' `Runnable::run` UI executor.
9. **Open in Editor resolves the selection's links in one queued operation.** It opens only entries that lead to regular files and caps at 20 after resolving: "Opened 20 of 26 files."
10. **`EditChannels` runs on Remote's transfer executor.** In production that is `context.background()`, as for the SFTP browser; the tests use real threads.
11. **Only `RemoteDocument` and `RemoteEditing` name Editor types.**
    - `SftpUi` holds a `RemoteEditing`, whose method signatures carry no Editor types, so `SftpUi` verifies and loads without the Editor.
    - `RemotePlugin` catches `NoClassDefFoundError` around the lookup, as for Vault.
12. **An unknown owner or group (−1) skips the owner check,** as the local write does. The hard-link check stays local only, since SFTP reports no link count.

## Global Constraints

- Java 25 on the JetBrains Runtime; use `./gradlew`, never a system `gradle`. `./gradlew check` runs everything headless.
- Remote's manifest:
  - `version = "0.4.0"`;
  - `sdk = ">=0.8.1, <0.9"` (unchanged);
  - `requires` gains `{ id = "dev.jasper.editor", version = ">=0.1", optional = true }`.
- Remote may import `dev.jasper.editor.api` and nothing else of the Editor. `gradle/plugin-architecture.gradle.kts` declares it; `verifyPluginArchitecture` checks it.
- Without the Editor, Remote behaves as today: a file does nothing, links navigate, and Open in Editor is hidden.
- There is no interface without two real implementations. `FileEndpoint` has two (local and SFTP). `EditChannels.Work` is a private-style functional interface for checked-exception lambdas, like `TransferCodec.Write`/`Read`.
- User-visible text, exactly:
  - `Reconnecting to <host name> needs an open terminal window for the sign-in prompt.`
  - `Opened 20 of <n> files.`
  - `Remote has stopped`
  - `Could not open <path> in the Editor: <reason>`
  - `<name>: its target does not exist`
  - `<name> is not a regular file`
- The sibling is named `.<name>.jasper-<hex>.tmp`, in the target's folder.
- Tests are headless. POSIX-only tests (POSIX paths, modes, symbolic links) are `@DisabledOnOs(OS.WINDOWS)`, as on `main`.
- Do not launch the GUI or the benchmark.
- Source hygiene: no raw control, private-use or unpaired surrogate characters in source (the check is in `AGENTS.md`).
- Match surrounding style: `ui/sftp` files are compact (several statements per line); `sftp/` and the new `edit/` files use conventional formatting.
- Commit on `claude/editor-plugin-b`, never on `main`. Every commit message ends with:

  ```
  🤖 Generated with Claude Code at The Home Depot

  Co-Authored-By: Claude <noreply@anthropic.com>
  ```

## Review Focus

1. **A file reached through a link whose target lies under a linked folder** (e.g. `/etc/alternatives`-style chains, or `../alias/file`). Expected: the link stays a link and the real file changes. Test: `SafeWriteTest.aLinkThroughALinkedFolderAndDotDotIsFollowed` (Task 2).
2. **The SSH connection closed while a tab is open, then Save.** Expected: a reconnect whose sign-in prompts appear in the window the file came from, else the active window; with none, the exact sign-in message. Tests: `EditChannelsTest.reconnectingWithoutATerminalWindowFailsWithTheSignInMessage` and `RemoteEditingTest.signInPromptsBelongToTheOriginWindowWhileItIsOpenThenToTheActiveWindow` (Tasks 3 and 4).
3. **Many files opened at once over a dead connection.** Expected: one sign-in attempt, and every tab fails with the same reason. Test: `EditChannelsTest.aFailedOpenFailsEveryQueuedOperationOnce` (Task 3).
4. **Closing a tab while a large remote file is loading.** Expected: the transfer aborts, and the next operation on that host still works. Test: `EditChannelsTest.cancellingRunningWorkAbortsItsChannelAndTheNextOperationReopens` (Task 3).
5. **Read-only remote files, and servers without posix-rename.** Expected: the first is refused and left unchanged; the second is written in place. Test: `SafeWriteTest.overSftpItReplacesAtomicallyRefusesReadOnlyFilesAndFallsBackWithoutPosixRename` (Task 2).

---

### Task 1: Spec amendment and endpoint support for the safe write

**Files:**
- Modify: `docs/superpowers/specs/2026-09-27-jasper-editor-plugin-design.md` (Status line, §4.4, §6.1, §6.2, §6.4)
- Modify: `plugins/remote/src/main/java/dev/jasper/remote/sftp/FileEntry.java`
- Modify: `plugins/remote/src/main/java/dev/jasper/remote/sftp/FileEndpoint.java`
- Modify: `plugins/remote/src/main/java/dev/jasper/remote/sftp/LocalEndpoint.java`
- Modify: `plugins/remote/src/main/java/dev/jasper/remote/sftp/SftpEndpoint.java`
- Modify: `plugins/remote/src/test/java/dev/jasper/remote/transfer/TransferScaleProbe.java` (the test endpoint gains the two methods)
- Test: `plugins/remote/src/test/java/dev/jasper/remote/sftp/FileEndpointTest.java`, `plugins/remote/src/test/java/dev/jasper/remote/sftp/SftpEndpointTest.java`

**Interfaces:**
- Consumes: nothing new.
- Produces:
  - `record FileEntry(String name, Kind kind, long size, long modifiedMillis, int permissions, String linkTarget, String fileKey, int uid, int gid)`. The 7- and 6-argument constructors remain, with `uid = gid = -1`.
  - `WriteHandle FileEndpoint.create(String path, int ordinaryPermissions) throws IOException`.
  - `boolean FileEndpoint.writable(String path) throws IOException`.
  - `SftpEndpoint.publish(temp, target, true)` throws `java.nio.file.AtomicMoveNotSupportedException` when the server lacks posix-rename.
  - `create` on an existing file throws `FileAlreadyExistsException` on both endpoints (and against MINA's server).

- [ ] **Step 1: Amend the spec**

In `docs/superpowers/specs/2026-09-27-jasper-editor-plugin-design.md`:

Replace the `**Status:**` line (line 3) with:

```markdown
**Status:** Approved by the user on 2026-09-27. Plan A (SDK 0.8.2 and the Editor for local files) is executed and merged. Plan B (SFTP editing through Remote) amends sections 4.4, 6.1, 6.2 and 6.4, as marked **Amended (Plan B)**, with the user's approval of that plan. Depends on the [plugin SDK](2026-09-21-jasper-plugin-sdk-design.md) and, for Plan B, on [Remote SFTP](2026-09-23-jasper-remote-sftp-design.md).
```

In §4.4, replace item 2 (from `2. **Safe write:**` through `Any failure removes the sibling and leaves the target untouched.`) with:

```markdown
2. **Safe write:** **Amended (Plan B)**
   1. create a new hidden sibling, `.<name>.jasper-<random>.tmp`, exclusively in the same folder: owner-only (`rw-------`) when it will replace an existing file, so the new content is never readable by others while it is written;
   2. write the bytes to it;
   3. give it the target's permission bits;
   4. atomically replace the target with it.

   Any failure removes the sibling and leaves the target untouched. A read-only target (one this user may not write) is refused with "permission denied", although its folder might allow the rename.
```

Replace §6.1's body (the paragraph under `### 6.1 \`FileEntry\` owner and group`) with:

```markdown
Remote's internal `sftp.FileEntry` gains `uid` and `gid` (−1 when unknown), filled from `SftpClient.Attributes` for SFTP and from `unix:uid`/`unix:gid` for `LocalEndpoint`. They exist for `SafeWrite`'s owner check.

**Amended (Plan B):** `FileEndpoint` also gains:
- `create(path, permissions)`: an exclusive create whose mode applies as the file is created. Over SFTP this is a hand-built `SSH_FXP_OPEN` with a permissions attribute, because MINA's `open` sends none.
- `writable(path)`: `Files.isWritable` locally; over SFTP it opens the file for writing and closes it without writing.
```

In §6.2, replace the second bullet (`- the sibling is written with …`) and the third bullet (`- \`publish\` failing because …`) with:

```markdown
- **Amended (Plan B):** a read-only target is refused (`writable`). The sibling is created with `create(sibling, 0600)`, or `0666` for a missing target so the server's umask decides. It is then filled, given the target's permissions with `metadata(sibling, -1, permissions)`, and published with `publish(sibling, target, replace = true)`, or `replace = false` for a missing target;
- `publish` reports a server without posix-rename as `AtomicMoveNotSupportedException`; `SafeWrite` then removes the sibling and falls back to writing in place.
```

In §6.4, replace the sub-bullet `  - a regular file opens in the Editor: a remote listing as a \`RemoteDocument\`, the local listing through \`Editor.open(Path)\`;` with:

```markdown
  - a regular file opens in the Editor as a `RemoteDocument` (**Amended (Plan B):** the panel lists only remote hosts, so there is no local listing);
```

- [ ] **Step 2: Write the failing tests**

In `FileEndpointTest.java`, replace the end of `contract`. The current text runs from the line `        endpoint.remove(root + "/dangling", false); endpoint.remove(root + "/empty", true);` through the method's closing brace. Replace it with:

```java
        endpoint.remove(root + "/dangling", false); endpoint.remove(root + "/empty", true);
        // NTFS has no POSIX modes: LocalEndpoint refuses them once the time is set (a transfer records a
        // metadata warning), and MINA's server reports every Windows file as 0666.
        boolean posix = !org.junit.jupiter.api.condition.OS.WINDOWS.isCurrentOs();
        if (posix || !(endpoint instanceof LocalEndpoint)) endpoint.metadata(target, 1_700_000_000_000L, 0640);
        else assertThatThrownBy(() -> endpoint.metadata(target, 1_700_000_000_000L, 0640)).isInstanceOf(IOException.class).hasMessageContaining("POSIX");
        if (posix) assertThat(endpoint.stat(target).permissions() & 0777).isEqualTo(0640);
        assertThat(endpoint.stat(target).modifiedMillis()).isEqualTo(1_700_000_000_000L);
        // The editor's safe write: an exclusive create whose mode applies as the file is created, owner and
        // group, and whether this user may write. Skipped for SFTP on Windows, where MINA's server has no POSIX modes.
        if (posix || endpoint instanceof LocalEndpoint) {
            String created = endpoint.child(root, "private");
            try (var out = endpoint.create(created, 0600)) { out.write(new byte[]{7, 8}); assertThat(out.checkpoint()).isEqualTo(2); }
            try (var in = endpoint.read(created, 0)) { assertThat(in.readAllBytes()).containsExactly(7, 8); }
            assertThatThrownBy(() -> endpoint.create(created, 0600)).isInstanceOf(FileAlreadyExistsException.class);
            if (posix) {
                var entry = endpoint.stat(created);
                assertThat(entry.permissions()).isEqualTo(0600);
                assertThat(entry.uid()).isGreaterThanOrEqualTo(0);
                assertThat(entry.gid()).isGreaterThanOrEqualTo(0);
                assertThat(endpoint.writable(created)).isTrue();
                endpoint.metadata(created, -1, 0400);
                assertThat(endpoint.writable(created)).as("a read-only file").isFalse();
            }
            endpoint.remove(created, false);
        }
    }
```

Add this test to `FileEndpointTest` (after `localContract`):

```java
    @Test @org.junit.jupiter.api.condition.DisabledOnOs(org.junit.jupiter.api.condition.OS.WINDOWS)
    void localStatReportsOwnerAndGroup() throws Exception {
        Path file = Files.writeString(directory.toRealPath().resolve("owned"), "x");
        try (var endpoint = new LocalEndpoint()) {
            var entry = endpoint.stat(file.toString());
            assertThat(entry.uid()).isEqualTo(Files.getAttribute(file, "unix:uid"));
            assertThat(entry.gid()).isEqualTo(Files.getAttribute(file, "unix:gid"));
        }
    }
```

In `SftpEndpointTest.unsupportedAtomicReplaceLeavesBothFilesUnchanged`, replace

```java
                assertThatThrownBy(() -> endpoint.publish("/temp", "/original", true)).isInstanceOf(IOException.class).hasMessageContaining("atomic replacement");
```

with

```java
                assertThatThrownBy(() -> endpoint.publish("/temp", "/original", true))
                    .isInstanceOf(java.nio.file.AtomicMoveNotSupportedException.class).hasMessageContaining("atomic replacement");
```

Add this test to `SftpEndpointTest` (after `sftpV3ContractAndSubsystemCloseKeepsSharedSessionAlive`):

```java
    @Test @org.junit.jupiter.api.condition.DisabledOnOs(org.junit.jupiter.api.condition.OS.WINDOWS)
    void sftpStatReportsOwnerAndGroup() throws Exception {
        Path file = Files.writeString(root.resolve("owned"), "x");
        try (var fixture = new Fixture(new SftpSubsystemFactory()); var endpoint = fixture.endpoint(Duration.ofSeconds(3))) {
            var entry = endpoint.stat("/owned");
            assertThat(entry.uid()).isEqualTo(Files.getAttribute(file, "unix:uid"));
            assertThat(entry.gid()).isEqualTo(Files.getAttribute(file, "unix:gid"));
        }
    }
```

- [ ] **Step 3: Run the tests to verify they fail**

Run: `./gradlew :jasper-plugin-remote:test --tests 'dev.jasper.remote.sftp.*'`
Expected: compilation fails, because `create`, `writable`, `uid()` and `gid()` do not exist.

- [ ] **Step 4: Extend `FileEntry`**

Replace the body of `FileEntry.java` with:

```java
package dev.jasper.remote.sftp;

import java.util.Objects;

/**
 * A no-follow directory entry; unavailable metadata uses -1 and unavailable identity uses empty text.
 * {@code uid} and {@code gid} are the owner and group ids, for the editor's safe-write owner check.
 */
public record FileEntry(String name, Kind kind, long size, long modifiedMillis, int permissions,
                        String linkTarget, String fileKey, int uid, int gid) {
    public enum Kind { FILE, DIRECTORY, LINK, SPECIAL }
    public FileEntry {
        Objects.requireNonNull(name); Objects.requireNonNull(kind); Objects.requireNonNull(fileKey);
        linkTarget = linkTarget == null ? "" : linkTarget;
    }
    public FileEntry(String name, Kind kind, long size, long modifiedMillis, int permissions, String linkTarget, String fileKey) {
        this(name, kind, size, modifiedMillis, permissions, linkTarget, fileKey, -1, -1);
    }
    public FileEntry(String name, Kind kind, long size, long modifiedMillis, int permissions, String linkTarget) {
        this(name, kind, size, modifiedMillis, permissions, linkTarget, "", -1, -1);
    }
}
```

`TransferCodec` and `DirectoryCache` keep the 7-argument constructor and so drop `uid`/`gid`. `TransferRecovery.sameSource` compares fields one by one, never whole records, so nothing else changes.

- [ ] **Step 5: Extend `FileEndpoint`**

In `FileEndpoint.java`, after the line `WriteHandle write(String path, long offset, boolean createExclusive) throws IOException;`, add:

```java
    /**
     * Creates {@code path} exclusively, with {@code ordinaryPermissions} (the low nine bits, reduced by the
     * umask) applied as it is created, and opens it for writing from offset 0. Where POSIX modes are
     * unsupported the file gets the platform's default access.
     */
    WriteHandle create(String path, int ordinaryPermissions) throws IOException;
    /** Whether this user may write the regular file at {@code path}; SFTP opens and closes it without writing. */
    boolean writable(String path) throws IOException;
```

- [ ] **Step 6: Implement them in `LocalEndpoint`**

1. Replace `stat` with this version, and add `unix` after it:

```java
    @Override public FileEntry stat(String text) throws IOException {
        Path file = path(text); if (file.getParent() != null) ancestors(file.getParent());
        var a = attributes(file);
        int permissions = -1;
        try { permissions = permissions(Files.readAttributes(file, PosixFileAttributes.class, LinkOption.NOFOLLOW_LINKS).permissions()); }
        catch (UnsupportedOperationException ignored) { /* reported as unavailable */ }
        return new FileEntry(file.getFileName() == null ? "/" : file.getFileName().toString(),
            a.isSymbolicLink() ? FileEntry.Kind.LINK : a.isDirectory() ? FileEntry.Kind.DIRECTORY : a.isRegularFile() ? FileEntry.Kind.FILE : FileEntry.Kind.SPECIAL,
            a.size(), a.lastModifiedTime().toMillis(), permissions, a.isSymbolicLink() ? Files.readSymbolicLink(file).toString() : "",
            a.fileKey() == null ? "" : a.fileKey().toString(), unix(file, "unix:uid"), unix(file, "unix:gid"));
    }
    private static int unix(Path file, String attribute) throws IOException {
        try { return ((Number) Files.getAttribute(file, attribute, LinkOption.NOFOLLOW_LINKS)).intValue(); }
        catch (UnsupportedOperationException | IllegalArgumentException unavailable) { return -1; }
    }
```

2. In the inner class `Parent`, replace the first two lines of `open`:

```java
        FileChannel open(Set<OpenOption> options) throws IOException {
            var channel = secure == null ? Files.newByteChannel(file, options) : secure.newByteChannel(name, options);
```

with:

```java
        FileChannel open(Set<OpenOption> options, FileAttribute<?>... attributes) throws IOException {
            var channel = secure == null ? Files.newByteChannel(file, options, attributes) : secure.newByteChannel(name, options, attributes);
```

3. Replace the whole `write` method (from `@Override public WriteHandle write(String text, long offset, boolean exclusive)` through the method's closing brace, after `return new WriteHandle() {…};` and the closing brace of its `try`) with:

```java
    @Override public WriteHandle write(String text, long offset, boolean exclusive) throws IOException {
        if (offset < 0) throw new IOException("Negative offset");
        Path file = path(text);
        try (var parent = new Parent(file)) {
            var options = new HashSet<OpenOption>(Set.of(StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS));
            if (exclusive) options.add(StandardOpenOption.CREATE_NEW);
            var channel = parent.open(options);
            try { channel.position(offset); }
            catch (IOException failure) { handles.remove(channel); channel.close(); throw failure; }
            return writer(channel);
        }
    }
    @Override public WriteHandle create(String text, int mode) throws IOException {
        Path file = path(text);
        try (var parent = new Parent(file)) {
            Set<OpenOption> options = Set.of(StandardOpenOption.WRITE, StandardOpenOption.CREATE_NEW, LinkOption.NOFOLLOW_LINKS);
            // The mode is part of the create itself, so the file is never more open than asked.
            return writer(file.getFileSystem().supportedFileAttributeViews().contains("posix")
                ? parent.open(options, PosixFilePermissions.asFileAttribute(bits(mode))) : parent.open(options));
        }
    }
    @Override public boolean writable(String text) throws IOException { return Files.isWritable(path(text)); }
    private WriteHandle writer(FileChannel channel) {
        return new WriteHandle() {
            @Override public void write(int b) throws IOException { write(new byte[]{(byte) b}); }
            @Override public void write(byte[] b, int off, int len) throws IOException {
                check(); var buffer = ByteBuffer.wrap(b, off, len);
                while (buffer.hasRemaining()) { check(); channel.write(buffer); }
            }
            @Override public long checkpoint() throws IOException { check(); channel.force(true); return channel.position(); }
            @Override public void flush() throws IOException { checkpoint(); }
            @Override public void abort() { try { channel.close(); } catch (IOException ignored) {} finally { handles.remove(channel); } }
            @Override public void close() throws IOException {
                try { if (channel.isOpen() && !closed) channel.force(true); } finally { channel.close(); handles.remove(channel); }
            }
        };
    }
```

4. In `metadata`, replace the two lines

```java
                var bits = java.util.EnumSet.noneOf(PosixFilePermission.class);
                for (var bit : PosixFilePermission.values()) if ((mode & (1 << (8 - bit.ordinal()))) != 0) bits.add(bit);
                posix.setPermissions(bits);
```

with

```java
                posix.setPermissions(bits(mode));
```

5. After the existing `private static int permissions(Set<PosixFilePermission> bits)`, add:

```java
    private static Set<PosixFilePermission> bits(int mode) {
        var bits = java.util.EnumSet.noneOf(PosixFilePermission.class);
        for (var bit : PosixFilePermission.values()) if ((mode & (1 << (8 - bit.ordinal()))) != 0) bits.add(bit);
        return bits;
    }
```

`FileAttribute`, `PosixFilePermissions` and `PosixFilePermission` are already covered by `import java.nio.file.attribute.*;`.

- [ ] **Step 7: Implement them in `SftpEndpoint`**

1. Add these imports:

```java
import java.nio.file.AccessDeniedException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
import org.apache.sshd.sftp.client.impl.DefaultCloseableHandle;
```

2. Replace `entry`:

```java
    private FileEntry entry(String path, SftpClient.Attributes a) throws IOException {
        boolean owner = a.getFlags().contains(SftpClient.Attribute.UidGid);
        return new FileEntry(FilePaths.name(path), a.isSymbolicLink() ? FileEntry.Kind.LINK : a.isDirectory() ? FileEntry.Kind.DIRECTORY
            : a.isRegularFile() ? FileEntry.Kind.FILE : FileEntry.Kind.SPECIAL, a.getSize(),
            a.getModifyTime() == null ? -1 : a.getModifyTime().toMillis(), a.getFlags().contains(SftpClient.Attribute.Perms) ? a.getPermissions() & 0777 : -1,
            a.isSymbolicLink() ? client.readLink(path) : "", "", owner ? a.getUserId() : -1, owner ? a.getGroupId() : -1);
    }
```

3. Replace the whole `write` method with the following four methods. The current method runs from `@Override public WriteHandle write(String text, long offset, boolean exclusive)` through the closing `};` of its anonymous `WriteHandle` and the method's closing brace, just before `@Override public void truncate`. The body of `writer` is the old anonymous class, unchanged:

```java
    @Override public WriteHandle write(String text, long offset, boolean exclusive) throws IOException {
        if (offset < 0) throw new IOException("Negative offset");
        var handle = exclusive ? file(text, SftpClient.OpenMode.Write, SftpClient.OpenMode.Create, SftpClient.OpenMode.Exclusive)
            : file(text, SftpClient.OpenMode.Write);
        return writer(handle, offset);
    }
    @Override public WriteHandle create(String text, int permissions) throws IOException {
        String path = path(text); ancestors(FilePaths.parent(path));
        // MINA's open() sends no attributes, so the mode travels in a hand-built SSH_FXP_OPEN and applies as the file is created.
        var request = new ByteArrayBuffer();
        request.putString(path);
        request.putInt(SftpConstants.SSH_FXF_WRITE | SftpConstants.SSH_FXF_CREAT | SftpConstants.SSH_FXF_EXCL);
        request.putInt(SftpConstants.SSH_FILEXFER_ATTR_PERMISSIONS);
        request.putInt(permissions & 0777);
        int id = client.send(SftpConstants.SSH_FXP_OPEN, request);
        check(); Buffer reply = client.receive(id, timeout);
        if (reply == null) throw new SocketTimeoutException("Timed out waiting for SFTP open");
        if (reply.available() < 9) throw new IOException("Truncated SFTP response");
        int length = reply.getInt(); int type = reply.getUByte(); int answered = reply.getInt();
        if (length != reply.available() + 5 || answered != id) throw new IOException("Invalid SFTP response identity");
        if (type == SftpConstants.SSH_FXP_STATUS) {
            int code = status(reply);
            String reason = reply.available() >= 4 ? reply.getString() : "";
            throw switch (code) {
                case SftpConstants.SSH_FX_FILE_ALREADY_EXISTS -> new FileAlreadyExistsException(path);
                case SftpConstants.SSH_FX_NO_SUCH_FILE, SftpConstants.SSH_FX_NO_SUCH_PATH -> new NoSuchFileException(path);
                case SftpConstants.SSH_FX_PERMISSION_DENIED -> new AccessDeniedException(path);
                default -> new SftpException(code, reason.isEmpty() ? "SFTP open failed" : reason);
            };
        }
        if (type != SftpConstants.SSH_FXP_HANDLE) throw new IOException("Unexpected SFTP response type");
        return writer(new DefaultCloseableHandle(client, path, reply.getBytes()), 0);
    }
    @Override public boolean writable(String text) throws IOException {
        try { file(text, SftpClient.OpenMode.Write).close(); return true; }
        catch (SftpException refused) { if (refused.getStatus() == SftpConstants.SSH_FX_PERMISSION_DENIED) return false; throw refused; }
    }
    private WriteHandle writer(SftpClient.CloseableHandle handle, long offset) {
        return new WriteHandle() {
            final ArrayDeque<Pending> pending = new ArrayDeque<>();
            long next = offset, confirmed = offset;
            boolean stopped;
            IOException failure;
            private void checkWriter() throws IOException { if (failure != null) throw failure; if (stopped) throw new IOException("Writer closed"); check(); }
            private void acknowledged() throws IOException {
                var request = pending.removeFirst(); var reply = response(request, "write");
                if (type(reply) != SftpConstants.SSH_FXP_STATUS) throw new IOException("Expected a write acknowledgement");
                int code = status(reply); if (code != SftpConstants.SSH_FX_OK) throw new SftpException(code, "SFTP write acknowledgement failed");
                if (request.offset() != confirmed) throw new IOException("Noncontiguous write acknowledgement");
                confirmed = request.end();
            }
            private IOException failed(IOException problem) { failure = problem; SftpEndpoint.this.abort(); return problem; }
            @Override public void write(int value) throws IOException { write(new byte[]{(byte) value}); }
            @Override public void write(byte[] data, int off, int length) throws IOException {
                Objects.checkFromIndexSize(off, length, data.length); checkWriter();
                try {
                    while (length > 0) {
                        if (pending.size() == REQUESTS) acknowledged();
                        int count = Math.min(CHUNK, length);
                        var message = new ByteArrayBuffer(count + 64, false);
                        message.putBytes(handle.getIdentifier()); message.putLong(next); message.putBytes(data, off, count);
                        if (next > Long.MAX_VALUE - count) throw new IOException("File offset overflow");
                        pending.addLast(new Pending(client.send(SftpConstants.SSH_FXP_WRITE, message), next, count));
                        next += count; off += count; length -= count;
                    }
                } catch (IOException problem) { throw failed(problem); }
            }
            @Override public long checkpoint() throws IOException {
                checkWriter(); try { while (!pending.isEmpty()) acknowledged(); return confirmed; }
                catch (IOException problem) { throw failed(problem); }
            }
            @Override public void flush() throws IOException { checkpoint(); }
            @Override public void abort() { SftpEndpoint.this.abort(); }
            @Override public void close() throws IOException {
                // A new exception: rethrowing the one write() threw makes try-with-resources fail on self-suppression.
                if (failure != null) throw new IOException("Write already failed: " + failure.getMessage(), failure);
                if (stopped) return;
                try { checkpoint(); handle.close(); } catch (IOException problem) { throw failed(problem); }
                finally { stopped = true; pending.clear(); }
            }
        };
    }
```

4. In `publish`, replace

```java
            if (extension == null || !extension.isSupported()) throw new IOException("Server does not support atomic replacement");
```

with

```java
            if (extension == null || !extension.isSupported()) throw new AtomicMoveNotSupportedException(from, to, "Server does not support atomic replacement");
```

- [ ] **Step 8: Give the test endpoint the two methods**

In `TransferScaleProbe.Generated`, after `public void metadata(String path,long time,int mode) {}`, add:

```java
        public WriteHandle create(String path,int mode) { throw new UnsupportedOperationException(); }
        public boolean writable(String path) { return true; }
```

- [ ] **Step 9: Run the tests to verify they pass**

Run: `./gradlew :jasper-plugin-remote:test`
Expected: PASS. That covers every Remote test, including the transfer, browser and contract suites that use `FileEntry` and both endpoints.

- [ ] **Step 10: Commit**

```bash
git add docs/superpowers/specs/2026-09-27-jasper-editor-plugin-design.md plugins/remote/src/main/java/dev/jasper/remote/sftp plugins/remote/src/test/java/dev/jasper/remote/sftp plugins/remote/src/test/java/dev/jasper/remote/transfer/TransferScaleProbe.java
git commit -m "feat(remote): owner-only exclusive create, writability and owner ids for the editor's safe write

Amends editor spec 4.4/6.1/6.2/6.4 for Plan B.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 2: `SafeWrite`, the write contract over a file endpoint

**Files:**
- Create: `plugins/remote/src/main/java/dev/jasper/remote/edit/SafeWrite.java`
- Create: `plugins/remote/src/main/java/dev/jasper/remote/edit/package-info.java`
- Create: `plugins/remote/src/test/java/dev/jasper/remote/edit/TestEndpoint.java`
- Create: `plugins/remote/src/test/java/dev/jasper/remote/sftp/SftpFixture.java`
- Test: `plugins/remote/src/test/java/dev/jasper/remote/edit/SafeWriteTest.java`

**Interfaces:**
- Consumes (Task 1): `FileEndpoint.create`, `writable`, `FileEntry.uid()`/`gid()`, and `AtomicMoveNotSupportedException` from `publish`.
- Produces:
  - `record SafeWrite.Resolved(String path, Optional<FileEntry> entry)`.
  - `static SafeWrite.Resolved SafeWrite.resolve(FileEndpoint endpoint, String path) throws IOException`: follows every link, at most 40; the entry is empty when nothing is there.
  - `static FileEntry SafeWrite.write(FileEndpoint endpoint, String path, byte[] content) throws IOException`: returns a stat of the target after writing.
  - Tests: `TestEndpoint(FileEndpoint delegate)` with `calls`, `stats`, `replaceFailure`, `aborted` and `wasAborted()`; `SftpFixture(Path directory)` with `files`, `server`, `connections`, `host`, `background`, `ui`, `onUi(Callable)`, `identity()`, `lease()` and `endpoint()`.

- [ ] **Step 1: Write the test helpers**

`plugins/remote/src/test/java/dev/jasper/remote/edit/TestEndpoint.java`:

```java
package dev.jasper.remote.edit;

import dev.jasper.remote.sftp.FileEndpoint;
import dev.jasper.remote.sftp.FileEntry;
import dev.jasper.remote.sftp.WriteHandle;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

/** Forwards to another endpoint, records each call, and lets a test change stats or fail a replacing publish. */
final class TestEndpoint implements FileEndpoint {
    final FileEndpoint delegate;
    final List<String> calls = new CopyOnWriteArrayList<>();
    final CountDownLatch aborted = new CountDownLatch(1);
    volatile UnaryOperator<FileEntry> stats = UnaryOperator.identity();
    volatile IOException replaceFailure;

    TestEndpoint(FileEndpoint delegate) { this.delegate = delegate; }

    boolean wasAborted() { return aborted.getCount() == 0; }

    @Override public String id() { return delegate.id(); }
    @Override public String child(String directory, String name) throws IOException { return delegate.child(directory, name); }
    @Override public String parent(String path) throws IOException { return delegate.parent(path); }
    @Override public FileEntry stat(String path) throws IOException { calls.add("stat " + path); return stats.apply(delegate.stat(path)); }
    @Override public void list(String directory, Consumer<FileEntry> visitor) throws IOException { calls.add("list " + directory); delegate.list(directory, visitor); }
    @Override public InputStream read(String path, long offset) throws IOException { calls.add("read " + path); return delegate.read(path, offset); }
    @Override public WriteHandle write(String path, long offset, boolean createExclusive) throws IOException {
        calls.add("write " + path); return delegate.write(path, offset, createExclusive);
    }
    @Override public WriteHandle create(String path, int permissions) throws IOException {
        calls.add("create " + path + " " + Integer.toOctalString(permissions)); return delegate.create(path, permissions);
    }
    @Override public boolean writable(String path) throws IOException { calls.add("writable " + path); return delegate.writable(path); }
    @Override public void truncate(String path, long size) throws IOException { calls.add("truncate " + path); delegate.truncate(path, size); }
    @Override public void mkdir(String path) throws IOException { delegate.mkdir(path); }
    @Override public void symlink(String path, String target) throws IOException { delegate.symlink(path, target); }
    @Override public void remove(String path, boolean directory) throws IOException { calls.add("remove " + path); delegate.remove(path, directory); }
    @Override public void publish(String temporary, String target, boolean replace) throws IOException {
        calls.add("publish " + target);
        if (replace && replaceFailure != null) throw replaceFailure;
        delegate.publish(temporary, target, replace);
    }
    @Override public void metadata(String path, long modifiedMillis, int permissions) throws IOException {
        calls.add("metadata " + path); delegate.metadata(path, modifiedMillis, permissions);
    }
    @Override public String canonical(String path) throws IOException { return delegate.canonical(path); }
    @Override public String resolveDirectory(String path) throws IOException { return delegate.resolveDirectory(path); }
    @Override public String home() throws IOException { return delegate.home(); }
    @Override public void abort() { calls.add("abort"); aborted.countDown(); delegate.abort(); }
    @Override public void close() { abort(); }
}
```

`plugins/remote/src/test/java/dev/jasper/remote/sftp/SftpFixture.java`:

```java
package dev.jasper.remote.sftp;

import dev.jasper.remote.RemoteSettings;
import dev.jasper.remote.client.ConnectionIdentity;
import dev.jasper.remote.client.Connections;
import dev.jasper.remote.client.HostKeyVerifier;
import dev.jasper.remote.client.LoopbackServer;
import dev.jasper.remote.client.SessionLease;
import dev.jasper.remote.hosts.Auth;
import dev.jasper.remote.hosts.RemoteHost;
import dev.jasper.remote.trust.KnownHosts;
import dev.jasper.vault.api.Credential;
import dev.jasper.vault.api.Kind;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.apache.sshd.common.file.virtualfs.VirtualFileSystemFactory;
import org.apache.sshd.sftp.server.SftpSubsystemFactory;

/**
 * A loopback SSH server serving {@code <directory>/files} over SFTP, and a connection registry signed in to
 * it as {@code deploy}. Trust lives outside the served folder, so listings hold only what a test made.
 */
public final class SftpFixture implements AutoCloseable {
    public final LoopbackServer server = new LoopbackServer();
    public final ExecutorService background = Executors.newVirtualThreadPerTaskExecutor();
    public final ExecutorService ui = Executors.newSingleThreadExecutor();
    public final Path files;
    public final RemoteHost host;
    public final Connections connections;

    public SftpFixture(Path directory) throws Exception {
        files = Files.createDirectories(directory.resolve("files")).toRealPath();
        server.server.setFileSystemFactory(new VirtualFileSystemFactory(files));
        server.server.setSubsystemFactories(List.of(new SftpSubsystemFactory()));
        host = RemoteHost.create("files", "127.0.0.1", server.port(), "deploy", new Auth.Vault(UUID.randomUUID()), "", Optional.empty());
        var settings = new RemoteSettings(Duration.ofSeconds(5), Duration.ofSeconds(5), Duration.ZERO, Duration.ZERO, false, false);
        connections = new Connections(() -> settings, new KnownHosts(directory.resolve("known_hosts"), Optional.empty()), Optional.empty(),
            id -> id.equals(host.id()) ? Optional.of(host) : Optional.empty(), Optional.of(id -> CompletableFuture.completedFuture(Optional.of(
                new Credential(id, "login", Kind.ACCOUNT_PASSWORD, "deploy", "s3cret".toCharArray(), null, null)))),
            question -> CompletableFuture.completedFuture(HostKeyVerifier.Decision.ONCE), background, ui, (delay, work) -> () -> {});
    }

    /** Runs {@code work} on the fixture's UI thread and waits for it. */
    public <T> T onUi(Callable<T> work) throws Exception { return ui.submit(work).get(10, TimeUnit.SECONDS); }
    public ConnectionIdentity identity() throws Exception { return onUi(() -> connections.identity(host.id())); }
    public SessionLease lease() throws Exception { return onUi(() -> connections.lease(connections.identity(host.id()), null, s -> {})).get(10, TimeUnit.SECONDS); }
    public SftpEndpoint endpoint() throws Exception { return new SftpEndpoint(lease(), Duration.ofSeconds(5)); }
    @Override public void close() throws Exception { ui.submit(connections::close).get(10, TimeUnit.SECONDS); background.close(); ui.close(); server.close(); }
}
```

- [ ] **Step 2: Write the failing tests**

`plugins/remote/src/test/java/dev/jasper/remote/edit/SafeWriteTest.java`:

```java
package dev.jasper.remote.edit;

import dev.jasper.remote.sftp.FileEntry;
import dev.jasper.remote.sftp.LocalEndpoint;
import dev.jasper.remote.sftp.SftpFixture;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AccessDeniedException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

/** POSIX paths, modes and links: SafeWrite serves the SFTP browser, whose paths are POSIX text. */
@DisabledOnOs(OS.WINDOWS)
class SafeWriteTest {
    @TempDir Path temp;

    Path folder() throws IOException { return temp.toRealPath(); }
    static byte[] bytes(String text) { return text.getBytes(StandardCharsets.UTF_8); }
    static List<String> names(Path folder) throws IOException {
        try (Stream<Path> list = Files.list(folder)) { return list.map(path -> path.getFileName().toString()).sorted().toList(); }
    }

    @Test void aSafeWriteReplacesTheFileAndKeepsItsPermissions() throws Exception {
        Path file = Files.writeString(folder().resolve("notes.toml"), "old");
        Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-r-----"));
        try (var endpoint = new TestEndpoint(new LocalEndpoint())) {
            FileEntry written = SafeWrite.write(endpoint, file.toString(), bytes("new text"));
            assertThat(Files.readString(file)).isEqualTo("new text");
            assertThat(Files.getPosixFilePermissions(file)).isEqualTo(PosixFilePermissions.fromString("rw-r-----"));
            assertThat(written.size()).isEqualTo(8);
            assertThat(written.modifiedMillis()).isEqualTo(Files.getLastModifiedTime(file).toMillis());
            assertThat(names(folder())).as("no sibling is left").containsExactly("notes.toml");
            assertThat(endpoint.calls).as("the sibling is created owner-only")
                .anyMatch(call -> call.matches("create .*/\\.notes\\.toml\\.jasper-[0-9a-f]+\\.tmp 600"));
            assertThat(endpoint.calls).contains("publish " + file);
        }
    }

    @Test void aNewFileIsCreatedWithTheDefaultMode() throws Exception {
        Path file = folder().resolve("new.txt");
        try (var endpoint = new TestEndpoint(new LocalEndpoint())) {
            SafeWrite.write(endpoint, file.toString(), bytes("fresh"));
            assertThat(Files.readString(file)).isEqualTo("fresh");
            assertThat(endpoint.calls).anyMatch(call -> call.startsWith("create ") && call.endsWith(".tmp 666"));
            assertThat(names(folder())).containsExactly("new.txt");
        }
    }

    @Test void aReadOnlyFileIsRefusedAndLeftAlone() throws Exception {
        Path file = Files.writeString(folder().resolve("locked.conf"), "keep");
        Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("r--r--r--"));
        try (var endpoint = new LocalEndpoint()) {
            assertThatThrownBy(() -> SafeWrite.write(endpoint, file.toString(), bytes("lost"))).isInstanceOf(AccessDeniedException.class);
            assertThat(Files.readString(file)).isEqualTo("keep");
            assertThat(names(folder())).containsExactly("locked.conf");
        } finally {
            Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-r--r--"));
        }
    }

    @Test void aSymlinkStaysALinkAndItsTargetChanges() throws Exception {
        Path target = Files.writeString(folder().resolve("target.conf"), "old");
        Path link = Files.createSymbolicLink(folder().resolve("link.conf"), Path.of("target.conf"));
        try (var endpoint = new LocalEndpoint()) {
            SafeWrite.write(endpoint, link.toString(), bytes("new"));
            assertThat(Files.isSymbolicLink(link)).isTrue();
            assertThat(Files.readString(target)).isEqualTo("new");
        }
    }

    @Test void aLinkThroughALinkedFolderAndDotDotIsFollowed() throws Exception {
        Path real = Files.createDirectory(folder().resolve("real"));
        Path file = Files.writeString(real.resolve("app.yaml"), "old");
        Files.createSymbolicLink(folder().resolve("alias"), Path.of("real"));
        Path deep = Files.createDirectory(folder().resolve("deep"));
        Path link = Files.createSymbolicLink(deep.resolve("app.yaml"), Path.of("../alias/app.yaml"));
        try (var endpoint = new LocalEndpoint()) {
            assertThat(SafeWrite.resolve(endpoint, link.toString()).path()).isEqualTo(file.toString());
            SafeWrite.write(endpoint, link.toString(), bytes("new"));
            assertThat(Files.readString(file)).isEqualTo("new");
            assertThat(Files.isSymbolicLink(link)).isTrue();
        }
    }

    @Test void aDanglingLinkCreatesItsTarget() throws Exception {
        Path link = Files.createSymbolicLink(folder().resolve("pending.conf"), Path.of("created.conf"));
        try (var endpoint = new LocalEndpoint()) {
            assertThat(SafeWrite.resolve(endpoint, link.toString()).entry()).isEmpty();
            SafeWrite.write(endpoint, link.toString(), bytes("made"));
            assertThat(Files.readString(folder().resolve("created.conf"))).isEqualTo("made");
            assertThat(Files.isSymbolicLink(link)).isTrue();
        }
    }

    @Test void aLinkLoopIsAnError() throws Exception {
        Files.createSymbolicLink(folder().resolve("a"), Path.of("b"));
        Files.createSymbolicLink(folder().resolve("b"), Path.of("a"));
        try (var endpoint = new LocalEndpoint()) {
            assertThatThrownBy(() -> SafeWrite.write(endpoint, folder().resolve("a").toString(), bytes("x")))
                .isInstanceOf(FileSystemException.class).hasMessageContaining("Too many levels of symbolic links");
        }
    }

    @Test void aFolderIsRefused() throws Exception {
        Path directory = Files.createDirectory(folder().resolve("conf.d"));
        try (var endpoint = new LocalEndpoint()) {
            assertThatThrownBy(() -> SafeWrite.write(endpoint, directory.toString(), bytes("x")))
                .isInstanceOf(FileSystemException.class).hasMessageContaining("Is a folder");
        }
    }

    @Test void anUnwritableFolderFallsBackToWritingInPlace() throws Exception {
        Path locked = Files.createDirectory(folder().resolve("locked"));
        Path file = Files.writeString(locked.resolve("app.conf"), "a much longer old text");
        Files.setPosixFilePermissions(locked, PosixFilePermissions.fromString("r-xr-xr-x"));
        try (var endpoint = new TestEndpoint(new LocalEndpoint())) {
            SafeWrite.write(endpoint, file.toString(), bytes("short"));
            assertThat(Files.readString(file)).as("written over and truncated").isEqualTo("short");
            assertThat(endpoint.calls).contains("truncate " + file).noneMatch(call -> call.startsWith("publish"));
        } finally {
            Files.setPosixFilePermissions(locked, PosixFilePermissions.fromString("rwxr-xr-x"));
        }
    }

    @Test void anOwnerOrGroupMismatchWritesInPlace() throws Exception {
        Path file = Files.writeString(folder().resolve("shared.conf"), "old contents");
        try (var endpoint = new TestEndpoint(new LocalEndpoint())) {
            // The sibling reports another owner, as a new file would in a folder whose setgid group differs.
            endpoint.stats = entry -> entry.name().startsWith(".shared.conf.jasper-")
                ? new FileEntry(entry.name(), entry.kind(), entry.size(), entry.modifiedMillis(), entry.permissions(),
                    entry.linkTarget(), entry.fileKey(), entry.uid() + 1, entry.gid())
                : entry;
            SafeWrite.write(endpoint, file.toString(), bytes("new"));
            assertThat(Files.readString(file)).isEqualTo("new");
            assertThat(endpoint.calls).contains("truncate " + file).noneMatch(call -> call.startsWith("publish"));
            assertThat(names(folder())).as("the sibling is removed").containsExactly("shared.conf");
        }
    }

    @Test void withoutAtomicReplacementItWritesInPlace() throws Exception {
        Path file = Files.writeString(folder().resolve("app.conf"), "old contents");
        try (var endpoint = new TestEndpoint(new LocalEndpoint())) {
            endpoint.replaceFailure = new AtomicMoveNotSupportedException("from", "to", "Server does not support atomic replacement");
            SafeWrite.write(endpoint, file.toString(), bytes("new"));
            assertThat(Files.readString(file)).isEqualTo("new");
            assertThat(endpoint.calls).contains("truncate " + file);
            assertThat(names(folder())).as("the sibling is removed").containsExactly("app.conf");
        }
    }

    @Test void aFailedReplacementRemovesTheSiblingAndLeavesTheTarget() throws Exception {
        Path file = Files.writeString(folder().resolve("app.conf"), "old");
        try (var endpoint = new TestEndpoint(new LocalEndpoint())) {
            endpoint.replaceFailure = new IOException("connection reset");
            assertThatThrownBy(() -> SafeWrite.write(endpoint, file.toString(), bytes("new"))).hasMessage("connection reset");
            assertThat(Files.readString(file)).isEqualTo("old");
            assertThat(names(folder())).containsExactly("app.conf");
        }
    }

    @Test void overSftpItReplacesAtomicallyRefusesReadOnlyFilesAndFallsBackWithoutPosixRename() throws Exception {
        try (var fixture = new SftpFixture(temp)) {
            Path file = Files.writeString(fixture.files.resolve("site.conf"), "old");
            Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-r-----"));
            try (var endpoint = fixture.endpoint()) {
                FileEntry written = SafeWrite.write(endpoint, "/site.conf", bytes("new text"));
                assertThat(Files.readString(file)).isEqualTo("new text");
                assertThat(Files.getPosixFilePermissions(file)).isEqualTo(PosixFilePermissions.fromString("rw-r-----"));
                assertThat(written.size()).isEqualTo(8);
                assertThat(names(fixture.files)).containsExactly("site.conf");
                Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("r--r--r--"));
                assertThatThrownBy(() -> SafeWrite.write(endpoint, "/site.conf", bytes("lost"))).isInstanceOf(AccessDeniedException.class);
                assertThat(Files.readString(file)).isEqualTo("new text");
                Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-r-----"));
            }
            org.apache.sshd.sftp.SftpModuleProperties.OPENSSH_EXTENSIONS.set(fixture.server.server, ",");
            try (var endpoint = new TestEndpoint(fixture.endpoint())) {
                SafeWrite.write(endpoint, "/site.conf", bytes("in place"));
                assertThat(Files.readString(file)).isEqualTo("in place");
                assertThat(endpoint.calls).contains("truncate /site.conf");
                assertThat(names(fixture.files)).containsExactly("site.conf");
            }
        }
    }
}
```

- [ ] **Step 3: Run the tests to verify they fail**

Run: `./gradlew :jasper-plugin-remote:test --tests 'dev.jasper.remote.edit.SafeWriteTest'`
Expected: compilation fails, because `SafeWrite` does not exist.

- [ ] **Step 4: Write `SafeWrite` and the package documentation**

`plugins/remote/src/main/java/dev/jasper/remote/edit/package-info.java`:

```java
/**
 * Editing remote files in the Editor: the write contract over a file endpoint, one queued SFTP channel per
 * connection identity, and the documents the Editor opens. May depend on: {@code sftp}, {@code client}, the SDK
 * and {@code dev.jasper.editor.api}. Only {@link dev.jasper.remote.edit.RemoteDocument} and
 * {@link dev.jasper.remote.edit.RemoteEditing} name Editor types; they load only when the Editor is installed.
 */
package dev.jasper.remote.edit;
```

`plugins/remote/src/main/java/dev/jasper/remote/edit/SafeWrite.java`:

```java
package dev.jasper.remote.edit;

import dev.jasper.remote.sftp.FileEndpoint;
import dev.jasper.remote.sftp.FileEntry;
import dev.jasper.remote.sftp.FilePaths;
import dev.jasper.remote.sftp.WriteHandle;
import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileSystemException;
import java.nio.file.NoSuchFileException;
import java.util.ArrayDeque;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The editor's write contract (editor spec 4.4) over any {@link FileEndpoint}, in POSIX path text. Blocking
 * and endpoint-confined: call it on the worker that owns the endpoint.
 */
public final class SafeWrite {
    static final int MAX_LINKS = 40;

    /**
     * Where a path's links lead, and what is there.
     *
     * @param path the resolved path, whose folders are real
     * @param entry a no-follow stat of it, or empty when nothing is there
     */
    public record Resolved(String path, Optional<FileEntry> entry) { }

    private SafeWrite() { }

    /**
     * Follows every symbolic link in {@code path}, relative ones from their own folder.
     *
     * @return where it leads; the last component need not exist
     * @throws IOException after more than 40 links, when a folder on the way is a file, or when the endpoint fails
     */
    public static Resolved resolve(FileEndpoint endpoint, String path) throws IOException {
        String absolute = FilePaths.absolute(path);
        try {
            FileEntry entry = endpoint.stat(absolute);
            if (entry.kind() != FileEntry.Kind.LINK) return new Resolved(absolute, Optional.of(entry));
        } catch (NoSuchFileException missing) {
            return new Resolved(absolute, Optional.empty());
        } catch (IOException linkedFolder) {
            // stat refuses a path through a linked folder; the walk below follows it (and rethrows real failures).
        }
        return walk(endpoint, absolute);
    }

    /**
     * Replaces the file at {@code path}: resolve links; refuse a folder, a special file or a read-only file;
     * write an owner-only hidden sibling, give it the target's permissions and atomically replace the target;
     * write in place when the sibling cannot be created, would change the owner or group, or when atomic
     * replacement is unavailable; create a missing target.
     *
     * @return a stat of the target after writing
     */
    public static FileEntry write(FileEndpoint endpoint, String path, byte[] content) throws IOException {
        Resolved resolved = resolve(endpoint, path);
        String target = resolved.path();
        Optional<FileEntry> existing = resolved.entry();
        if (existing.isPresent()) {
            FileEntry file = existing.orElseThrow();
            if (file.kind() == FileEntry.Kind.DIRECTORY) throw new FileSystemException(target, null, "Is a folder");
            if (file.kind() != FileEntry.Kind.FILE) throw new FileSystemException(target, null, "Not a regular file");
            // The folder may allow the rename, but a read-only file is not replaced (as a write in place would fail).
            if (!endpoint.writable(target)) throw new AccessDeniedException(target);
        }
        String sibling = FilePaths.child(FilePaths.parent(target),
            "." + FilePaths.name(target) + ".jasper-" + Long.toHexString(ThreadLocalRandom.current().nextLong()) + ".tmp");
        WriteHandle out;
        try {
            // Owner-only while it holds the new content; the target's own permissions are copied just before the rename.
            out = endpoint.create(sibling, existing.isPresent() ? 0600 : 0666);
        } catch (IOException cannotCreate) {
            if (existing.isEmpty()) throw cannotCreate;
            return inPlace(endpoint, target, content);
        }
        boolean sameOwner;
        try {
            sameOwner = existing.isEmpty() || sameOwner(endpoint.stat(sibling), existing.orElseThrow());
        } catch (IOException | RuntimeException failure) {
            try { out.close(); } catch (IOException suppressed) { failure.addSuppressed(suppressed); }
            remove(endpoint, sibling, failure);
            throw failure;
        }
        if (!sameOwner) {
            out.close();
            endpoint.remove(sibling, false);
            return inPlace(endpoint, target, content);
        }
        boolean atomicUnavailable = false;
        try {
            try (out) {
                out.write(content);
                out.checkpoint();
            }
            int permissions = existing.map(FileEntry::permissions).orElse(-1);
            if (permissions >= 0) endpoint.metadata(sibling, -1, permissions);
            try {
                endpoint.publish(sibling, target, existing.isPresent());
            } catch (AtomicMoveNotSupportedException unavailable) {
                atomicUnavailable = true;
            }
        } catch (IOException | RuntimeException failure) {
            remove(endpoint, sibling, failure);
            throw failure;
        }
        if (atomicUnavailable) {
            endpoint.remove(sibling, false);
            return inPlace(endpoint, target, content);
        }
        return endpoint.stat(target);
    }

    /** Resolves {@code path} one component at a time, so no stat ever passes through an unresolved link. */
    private static Resolved walk(FileEndpoint endpoint, String path) throws IOException {
        var pending = new ArrayDeque<String>();
        push(pending, path);
        String resolved = "/";
        FileEntry entry = null;
        int links = 0;
        while (!pending.isEmpty()) {
            String part = pending.poll();
            if (part.isEmpty() || part.equals(".")) continue;
            if (part.equals("..")) {
                resolved = FilePaths.parent(resolved);
                entry = null;
                continue;
            }
            String next = FilePaths.child(resolved, part);
            try {
                entry = endpoint.stat(next);
            } catch (NoSuchFileException missing) {
                var rest = new StringBuilder(next);
                for (String left : pending) rest.append('/').append(left);
                return new Resolved(FilePaths.absolute(rest.toString()), Optional.empty());
            }
            if (entry.kind() == FileEntry.Kind.LINK) {
                if (++links > MAX_LINKS) throw new FileSystemException(path, null, "Too many levels of symbolic links");
                String target = entry.linkTarget();
                if (target.startsWith("/")) resolved = "/";
                push(pending, target);
                entry = null;
                continue;
            }
            if (!pending.isEmpty() && entry.kind() != FileEntry.Kind.DIRECTORY) throw new FileSystemException(next, null, "Not a folder");
            resolved = next;
        }
        return new Resolved(resolved, Optional.of(entry != null ? entry : endpoint.stat(resolved)));
    }

    /** Puts the components of {@code path} in front of {@code pending}, in order. */
    private static void push(ArrayDeque<String> pending, String path) {
        String[] parts = path.split("/");
        for (int i = parts.length - 1; i >= 0; i--) pending.push(parts[i]);
    }

    /** Keeps the owner, group, hard links and extended attributes; not atomic. */
    private static FileEntry inPlace(FileEndpoint endpoint, String target, byte[] content) throws IOException {
        try (var out = endpoint.write(target, 0, false)) {
            out.write(content);
            out.checkpoint();
        }
        endpoint.truncate(target, content.length);
        return endpoint.stat(target);
    }

    /** Unknown ids (-1) skip the check, as the local write does where the platform reports none. */
    private static boolean sameOwner(FileEntry sibling, FileEntry target) {
        if (sibling.uid() < 0 || target.uid() < 0 || sibling.gid() < 0 || target.gid() < 0) return true;
        return sibling.uid() == target.uid() && sibling.gid() == target.gid();
    }

    private static void remove(FileEndpoint endpoint, String sibling, Exception failure) {
        try { endpoint.remove(sibling, false); } catch (IOException suppressed) { failure.addSuppressed(suppressed); }
    }
}
```

- [ ] **Step 5: Run the tests to verify they pass**

Run: `./gradlew :jasper-plugin-remote:test --tests 'dev.jasper.remote.edit.SafeWriteTest'`
Expected: PASS (13 tests).

- [ ] **Step 6: Commit**

```bash
git add plugins/remote/src/main/java/dev/jasper/remote/edit plugins/remote/src/test/java/dev/jasper/remote/edit plugins/remote/src/test/java/dev/jasper/remote/sftp/SftpFixture.java
git commit -m "feat(remote): SafeWrite, the editor's write contract over a file endpoint

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 3: `Connections.authenticated` and `EditChannels`, one queued SFTP channel per identity

**Files:**
- Modify: `plugins/remote/src/main/java/dev/jasper/remote/client/Connections.java` (add `authenticated` after `existingLease`)
- Create: `plugins/remote/src/main/java/dev/jasper/remote/edit/EditChannels.java`
- Create: `plugins/remote/src/test/java/dev/jasper/remote/edit/TestWindow.java`
- Test: `plugins/remote/src/test/java/dev/jasper/remote/edit/EditChannelsTest.java`, `plugins/remote/src/test/java/dev/jasper/remote/client/ConnectionsTest.java`

**Interfaces:**
- Consumes (Task 2): `TestEndpoint`.
- Produces:
  - `boolean Connections.authenticated(ConnectionIdentity identity)`: UI only; true while an authenticated shared route is open.
  - `EditChannels(BiFunction<ConnectionIdentity, Optional<WindowHandle>, CompletableFuture<FileEndpoint>> opener, Predicate<ConnectionIdentity> authenticated, Executor background, Executor ui)`.
  - `<T> CompletableFuture<T> EditChannels.submit(ConnectionIdentity identity, Supplier<Optional<WindowHandle>> owner, EditChannels.Work<T> work)`.
  - `@FunctionalInterface interface EditChannels.Work<T> { T run(FileEndpoint endpoint) throws Exception; }`.
  - `void EditChannels.close()`.
  - Package-private `boolean active(ConnectionIdentity)`.
  - Tests: `record TestWindow(UUID id, AtomicBoolean open) implements WindowHandle`, and `static ConnectionIdentity EditChannelsTest.identity(String hostName)`.

- [ ] **Step 1: Write the failing tests**

Add this test to `ConnectionsTest` (after `leasesShareWithShellAndKeepCapturedEndpointAfterHostEdit`):

```java
    @Test void authenticatedReportsALiveRouteWithoutTakingALease(@TempDir Path dir) throws Exception {
        try (var server = new LoopbackServer()) {
            connections(dir, Optional.empty());
            var host = host("target", server.port(), "deploy", new Auth.Vault(passwordCredential()), Optional.empty());
            var identity = onUi(() -> connections.identity(host.id()));
            assertThat(onUi(() -> connections.authenticated(identity))).isFalse();
            var lease = onUi(() -> connections.lease(identity, null, status -> {})).get(10, TimeUnit.SECONDS);
            assertThat(onUi(() -> connections.authenticated(identity))).isTrue();
            lease.close();
        }
    }
```

`plugins/remote/src/test/java/dev/jasper/remote/edit/TestWindow.java`:

```java
package dev.jasper.remote.edit;

import dev.jasper.sdk.terminal.TabHandle;
import dev.jasper.sdk.terminal.WindowHandle;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/** A terminal window whose openness a test controls. */
record TestWindow(UUID id, AtomicBoolean open) implements WindowHandle {
    @Override public List<TabHandle> tabs() { return List.of(); }
    @Override public Optional<TabHandle> activeTab() { return Optional.empty(); }
    @Override public boolean isActive() { return open.get(); }
    @Override public boolean isOpen() { return open.get(); }
    @Override public void toFront() { }
}
```

`plugins/remote/src/test/java/dev/jasper/remote/edit/EditChannelsTest.java`:

```java
package dev.jasper.remote.edit;

import dev.jasper.remote.client.ConnectionIdentity;
import dev.jasper.remote.hosts.Auth;
import dev.jasper.remote.hosts.RemoteHost;
import dev.jasper.remote.sftp.FileEndpoint;
import dev.jasper.remote.sftp.LocalEndpoint;
import dev.jasper.sdk.terminal.WindowHandle;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class EditChannelsTest {
    final ExecutorService ui = Executors.newSingleThreadExecutor();
    final ExecutorService background = Executors.newCachedThreadPool();
    final List<Optional<WindowHandle>> owners = new CopyOnWriteArrayList<>();
    final List<TestEndpoint> opened = new CopyOnWriteArrayList<>();
    volatile boolean connected = true;
    EditChannels channels = new EditChannels((identity, owner) -> {
        owners.add(owner);
        var endpoint = new TestEndpoint(new LocalEndpoint());
        opened.add(endpoint);
        return CompletableFuture.completedFuture(endpoint);
    }, identity -> connected, background, ui);

    @AfterEach void stop() throws Exception {
        onUi(() -> { channels.close(); return null; });
        ui.shutdownNow(); background.shutdownNow();
    }

    static ConnectionIdentity identity(String name) {
        return new ConnectionIdentity(List.of(new ConnectionIdentity.Hop(
            RemoteHost.create(name, "127.0.0.1", 22, "deploy", Auth.AGENT, "", Optional.empty()), "deploy")));
    }
    <T> T onUi(Callable<T> work) throws Exception { return ui.submit(work).get(10, TimeUnit.SECONDS); }
    <T> CompletableFuture<T> submit(ConnectionIdentity identity, EditChannels.Work<T> work) throws Exception {
        return onUi(() -> channels.submit(identity, Optional::empty, work));
    }

    @Test void oneIdentityRunsOneOperationAtATimeOnOneChannelThenClosesIt() throws Exception {
        var prod = identity("prod");
        var release = new CountDownLatch(1);
        var running = new AtomicInteger();
        var most = new AtomicInteger();
        EditChannels.Work<String> work = endpoint -> {
            most.accumulateAndGet(running.incrementAndGet(), Math::max);
            try { release.await(10, TimeUnit.SECONDS); return "done"; } finally { running.decrementAndGet(); }
        };
        var first = submit(prod, work);
        var second = submit(prod, work);
        var third = submit(prod, work);
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (running.get() == 0 && System.nanoTime() < deadline) Thread.sleep(5);
        Thread.sleep(100);
        release.countDown();
        assertThat(List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS), third.get(10, TimeUnit.SECONDS))).containsOnly("done");
        assertThat(most).as("never two at once").hasValue(1);
        assertThat(opened).as("one channel for the three").hasSize(1);
        assertThat(onUi(() -> channels.active(prod))).as("an emptied queue closes its channel").isFalse();
        assertThat(opened.getFirst().wasAborted()).isTrue();
    }

    @Test void twoIdentitiesRunInParallel() throws Exception {
        var both = new CountDownLatch(2);
        EditChannels.Work<Boolean> work = endpoint -> { both.countDown(); return both.await(10, TimeUnit.SECONDS); };
        var prod = submit(identity("prod"), work);
        var staging = submit(identity("staging"), work);
        assertThat(prod.get(10, TimeUnit.SECONDS)).isTrue();
        assertThat(staging.get(10, TimeUnit.SECONDS)).isTrue();
        assertThat(opened).hasSize(2);
    }

    @Test void theNextOperationAfterAnEmptyQueueOpensAFreshChannel() throws Exception {
        var prod = identity("prod");
        assertThat(submit(prod, endpoint -> "one").get(10, TimeUnit.SECONDS)).isEqualTo("one");
        assertThat(submit(prod, endpoint -> "two").get(10, TimeUnit.SECONDS)).isEqualTo("two");
        assertThat(opened).hasSize(2);
    }

    @Test void cancellingRunningWorkAbortsItsChannelAndTheNextOperationReopens() throws Exception {
        var prod = identity("prod");
        var started = new CountDownLatch(1);
        CompletableFuture<String> reading = submit(prod, endpoint -> {
            started.countDown();
            if (!((TestEndpoint) endpoint).aborted.await(10, TimeUnit.SECONDS)) throw new AssertionError("not aborted");
            throw new IOException("aborted");
        });
        var next = submit(prod, endpoint -> "next");
        assertThat(started.await(10, TimeUnit.SECONDS)).isTrue();
        onUi(() -> reading.cancel(true));
        assertThat(next.get(10, TimeUnit.SECONDS)).isEqualTo("next");
        assertThat(opened).hasSize(2);
        assertThat(opened.getFirst().wasAborted()).isTrue();
    }

    @Test void cancellingQueuedWorkWithdrawsIt() throws Exception {
        var prod = identity("prod");
        var release = new CountDownLatch(1);
        var ran = new AtomicBoolean();
        var first = submit(prod, endpoint -> release.await(10, TimeUnit.SECONDS));
        var withdrawn = submit(prod, endpoint -> { ran.set(true); return "withdrawn"; });
        var last = submit(prod, endpoint -> "last");
        onUi(() -> withdrawn.cancel(true));
        release.countDown();
        assertThat(last.get(10, TimeUnit.SECONDS)).isEqualTo("last");
        assertThat(first.get(10, TimeUnit.SECONDS)).isTrue();
        assertThat(ran).isFalse();
        assertThat(opened).hasSize(1);
    }

    @Test void reconnectingWithoutATerminalWindowFailsWithTheSignInMessage() throws Exception {
        connected = false;
        var failure = submit(identity("prod-web"), endpoint -> "never");
        assertThatThrownBy(() -> failure.get(10, TimeUnit.SECONDS))
            .hasRootCauseMessage("Reconnecting to prod-web needs an open terminal window for the sign-in prompt.");
        assertThat(owners).as("nothing was opened").isEmpty();
    }

    @Test void aLiveConnectionNeedsNoWindow() throws Exception {
        assertThat(submit(identity("prod"), endpoint -> "read").get(10, TimeUnit.SECONDS)).isEqualTo("read");
        assertThat(owners).containsExactly(Optional.empty());
    }

    @Test void aWindowOwnsTheSignInWhenReconnecting() throws Exception {
        connected = false;
        var window = new TestWindow(UUID.randomUUID(), new AtomicBoolean(true));
        var read = onUi(() -> channels.submit(identity("prod"), () -> Optional.of(window), endpoint -> "read"));
        assertThat(read.get(10, TimeUnit.SECONDS)).isEqualTo("read");
        assertThat(owners).containsExactly(Optional.of(window));
    }

    @Test void aFailedOpenFailsEveryQueuedOperationOnce() throws Exception {
        onUi(() -> { channels.close(); return null; });
        var opening = new CompletableFuture<FileEndpoint>();
        var asked = new AtomicInteger();
        channels = new EditChannels((identity, owner) -> { asked.incrementAndGet(); return opening; }, identity -> true, background, ui);
        var prod = identity("prod");
        var reads = List.of(submit(prod, endpoint -> "a"), submit(prod, endpoint -> "b"), submit(prod, endpoint -> "c"));
        onUi(() -> opening.completeExceptionally(new IOException("Permission denied (publickey)")));
        for (var read : reads) assertThatThrownBy(() -> read.get(10, TimeUnit.SECONDS)).hasRootCauseMessage("Permission denied (publickey)");
        assertThat(asked).as("one sign-in attempt for the three").hasValue(1);
    }

    @Test void closingFailsQueuedWorkAndAbortsTheChannel() throws Exception {
        var prod = identity("prod");
        var started = new CountDownLatch(1);
        var running = submit(prod, endpoint -> {
            started.countDown();
            ((TestEndpoint) endpoint).aborted.await(10, TimeUnit.SECONDS);
            throw new IOException("aborted");
        });
        var queued = submit(prod, endpoint -> "never");
        assertThat(started.await(10, TimeUnit.SECONDS)).isTrue();
        onUi(() -> { channels.close(); return null; });
        assertThatThrownBy(() -> queued.get(10, TimeUnit.SECONDS)).hasRootCauseMessage("Remote has stopped");
        assertThatThrownBy(() -> running.get(10, TimeUnit.SECONDS)).hasMessageContaining("Remote has stopped");
        assertThat(opened.getFirst().wasAborted()).isTrue();
        var late = submit(prod, endpoint -> "late");
        assertThatThrownBy(() -> late.get(10, TimeUnit.SECONDS)).hasRootCauseMessage("Remote has stopped");
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :jasper-plugin-remote:test --tests 'dev.jasper.remote.edit.EditChannelsTest' --tests 'dev.jasper.remote.client.ConnectionsTest'`
Expected: compilation fails, because `EditChannels` and `Connections.authenticated` do not exist.

- [ ] **Step 3: Add `Connections.authenticated`**

In `Connections.java`, directly after the `existingLease` method, add:

```java
    /** UI-only: whether an authenticated route to {@code identity} is open, so leasing it needs no sign-in prompt. */
    public boolean authenticated(ConnectionIdentity identity) {
        if (closed || !identity.resolved()) return false;
        Shared shared = sessions.get(Key.shared(identity));
        return shared != null && shared.session != null && shared.session.isOpen() && shared.session.isAuthenticated();
    }
```

- [ ] **Step 4: Write `EditChannels`**

`plugins/remote/src/main/java/dev/jasper/remote/edit/EditChannels.java`:

```java
package dev.jasper.remote.edit;

import dev.jasper.remote.client.ConnectionIdentity;
import dev.jasper.remote.sftp.FileEndpoint;
import dev.jasper.sdk.terminal.WindowHandle;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.BiFunction;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Runs the editor's file operations for each connection identity one at a time, on one SFTP channel opened for
 * the first queued operation and closed when the queue empties: every channel counts against OpenSSH's 10
 * sessions per connection, beside the user's shells. UI thread only; operations run on the background executor.
 * A failed or cancelled operation discards its channel, and the next operation opens a fresh one.
 */
public final class EditChannels implements AutoCloseable {
    /**
     * One operation on an endpoint, run on the background executor.
     *
     * @param <T> what it produces
     */
    @FunctionalInterface public interface Work<T> { T run(FileEndpoint endpoint) throws Exception; }

    private final BiFunction<ConnectionIdentity, Optional<WindowHandle>, CompletableFuture<FileEndpoint>> opener;
    private final Predicate<ConnectionIdentity> authenticated;
    private final Executor background, ui;
    private final Map<ConnectionIdentity, Channel> channels = new HashMap<>();
    private boolean closed;

    /**
     * @param opener opens an endpoint, on the UI thread; the window, when present, owns sign-in prompts
     * @param authenticated whether an authenticated connection exists, so opening needs no prompt
     * @param background where operations run
     * @param ui the UI thread
     */
    public EditChannels(BiFunction<ConnectionIdentity, Optional<WindowHandle>, CompletableFuture<FileEndpoint>> opener,
                        Predicate<ConnectionIdentity> authenticated, Executor background, Executor ui) {
        this.opener = Objects.requireNonNull(opener); this.authenticated = Objects.requireNonNull(authenticated);
        this.background = Objects.requireNonNull(background); this.ui = Objects.requireNonNull(ui);
    }

    /**
     * Queues {@code work} behind the identity's earlier operations. Cancelling the result withdraws queued work,
     * or aborts the channel under running work.
     *
     * @param owner the window for sign-in prompts, asked when a channel must be opened
     * @return the work's result, completed on the UI thread
     */
    public <T> CompletableFuture<T> submit(ConnectionIdentity identity, Supplier<Optional<WindowHandle>> owner, Work<T> work) {
        var job = new Job<T>(Objects.requireNonNull(owner), Objects.requireNonNull(work));
        if (closed) {
            job.result.completeExceptionally(new IOException("Remote has stopped"));
            return job.result;
        }
        Channel channel = channels.computeIfAbsent(identity, Channel::new);
        channel.queue.add(job);
        job.result.whenComplete((value, failure) -> { if (job.result.isCancelled()) ui.execute(() -> cancelled(channel, job)); });
        next(channel);
        return job.result;
    }

    /** @return whether {@code identity} has a channel open, opening or queued, for tests */
    boolean active(ConnectionIdentity identity) { return channels.containsKey(identity); }

    private void next(Channel channel) {
        if (closed || channel.running != null || channel.opening != null) return;
        while (!channel.queue.isEmpty() && channel.queue.peek().result.isDone()) channel.queue.poll();
        Job<?> job = channel.queue.peek();
        if (job == null) {
            channel.discard();
            channels.remove(channel.identity, channel);
            return;
        }
        if (channel.endpoint == null) {
            open(channel, job);
            return;
        }
        channel.queue.poll();
        run(channel, job);
    }

    private void open(Channel channel, Job<?> job) {
        Optional<WindowHandle> owner = job.owner.get();
        if (owner.isEmpty() && !authenticated.test(channel.identity)) {
            // Remote's sign-in prompts are cancelled without an owner window.
            channel.queue.poll();
            job.result.completeExceptionally(new IOException("Reconnecting to " + channel.identity.host().name()
                + " needs an open terminal window for the sign-in prompt."));
            next(channel);
            return;
        }
        CompletableFuture<FileEndpoint> opening;
        try { opening = opener.apply(channel.identity, owner); }
        catch (RuntimeException failure) { opening = CompletableFuture.failedFuture(failure); }
        channel.opening = opening;
        var requested = opening;
        opening.whenComplete((endpoint, failure) -> ui.execute(() -> opened(channel, requested, endpoint, failure)));
    }

    private void opened(Channel channel, CompletableFuture<FileEndpoint> requested, FileEndpoint endpoint, Throwable failure) {
        if (channel.opening != requested) {
            if (endpoint != null) endpoint.abort();
            return;
        }
        channel.opening = null;
        if (closed) {
            if (endpoint != null) endpoint.abort();
            return;
        }
        if (failure != null) {
            // Every queued operation needed this connection: fail them together rather than prompting once each.
            for (Job<?> waiting : List.copyOf(channel.queue)) waiting.result.completeExceptionally(failure);
            channel.queue.clear();
        } else {
            channel.endpoint = endpoint;
        }
        next(channel);
    }

    private <T> void run(Channel channel, Job<T> job) {
        channel.running = job;
        FileEndpoint endpoint = channel.endpoint;
        try {
            background.execute(() -> {
                T value = null;
                Throwable failure = null;
                // Anything thrown must reach finished(), or the queue would stop for good.
                try { value = job.work.run(endpoint); } catch (Throwable problem) { failure = problem; }
                T result = value;
                Throwable problem = failure;
                ui.execute(() -> finished(channel, job, result, problem));
            });
        } catch (RuntimeException rejected) {
            finished(channel, job, null, rejected);
        }
    }

    private <T> void finished(Channel channel, Job<T> job, T value, Throwable failure) {
        if (channel.running == job) channel.running = null;
        if (failure != null) channel.discard();
        // Bookkeeping first, so a caller woken by the result finds the queue settled.
        next(channel);
        if (failure == null) job.result.complete(value);
        else job.result.completeExceptionally(closed ? new IOException("Remote has stopped", failure) : failure);
    }

    private void cancelled(Channel channel, Job<?> job) {
        if (channel.running == job) {
            channel.discard();
            return;
        }
        if (channel.opening != null && channel.queue.stream().allMatch(waiting -> waiting.result.isDone())) {
            var opening = channel.opening;
            channel.opening = null;
            opening.cancel(true);
            next(channel);
        }
    }

    /** Fails queued work, cancels opening channels and aborts open ones; later submissions fail. */
    @Override public void close() {
        if (closed) return;
        closed = true;
        for (Channel channel : List.copyOf(channels.values())) {
            for (Job<?> waiting : channel.queue) waiting.result.completeExceptionally(new IOException("Remote has stopped"));
            channel.queue.clear();
            if (channel.opening != null) channel.opening.cancel(true);
            channel.opening = null;
            channel.discard();
        }
        channels.clear();
    }

    private static final class Channel {
        final ConnectionIdentity identity;
        final ArrayDeque<Job<?>> queue = new ArrayDeque<>();
        FileEndpoint endpoint;
        CompletableFuture<FileEndpoint> opening;
        Job<?> running;

        Channel(ConnectionIdentity identity) { this.identity = identity; }

        void discard() {
            var owned = endpoint;
            endpoint = null;
            if (owned != null) owned.abort();
        }
    }

    private static final class Job<T> {
        final Supplier<Optional<WindowHandle>> owner;
        final Work<T> work;
        final CompletableFuture<T> result = new CompletableFuture<>();

        Job(Supplier<Optional<WindowHandle>> owner, Work<T> work) { this.owner = owner; this.work = work; }
    }
}
```

- [ ] **Step 5: Run the tests to verify they pass**

Run: `./gradlew :jasper-plugin-remote:test --tests 'dev.jasper.remote.edit.EditChannelsTest' --tests 'dev.jasper.remote.client.ConnectionsTest'`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add plugins/remote/src/main/java/dev/jasper/remote/client/Connections.java plugins/remote/src/main/java/dev/jasper/remote/edit/EditChannels.java plugins/remote/src/test/java/dev/jasper/remote/edit plugins/remote/src/test/java/dev/jasper/remote/client/ConnectionsTest.java
git commit -m "feat(remote): one queued SFTP channel per connection identity for editor operations

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 4: Remote 0.4.0, `RemoteDocument` and `RemoteEditing`

**Files:**
- Modify: `plugins/remote/src/main/resources/plugin.toml`
- Modify: `plugins/remote/build.gradle.kts`
- Modify: `gradle/plugin-architecture.gradle.kts` (line 10, `declaredImports`)
- Create: `plugins/remote/src/main/java/dev/jasper/remote/edit/RemoteDocument.java`
- Create: `plugins/remote/src/main/java/dev/jasper/remote/edit/RemoteEditing.java`
- Test: `plugins/remote/src/test/java/dev/jasper/remote/edit/RemoteDocumentTest.java`, `plugins/remote/src/test/java/dev/jasper/remote/edit/RemoteEditingTest.java`

**Interfaces:**
- Consumes:
  - `SafeWrite.resolve`/`write` (Task 2);
  - `EditChannels`, `TestWindow` and `EditChannelsTest.identity` (Task 3);
  - `SftpFixture` (Task 2);
  - the Editor API: `Editor`, `EditorDocument`, `Content`, `Revision` and `TooLargeException(long size, long limit)`.
- Produces:
  - `RemoteDocument(ConnectionIdentity identity, String path, EditChannels channels, Supplier<Optional<WindowHandle>> owner)`.
  - `static String RemoteDocument.key(ConnectionIdentity identity, String path)`, of the form `remote:<hostId>/<login>[,<hostId>/<login>…]:<path>`.
  - `RemoteEditing(Editor editor, EditChannels channels, Supplier<Optional<WindowHandle>> activeWindow, Consumer<String> notice)`.
  - `static final int RemoteEditing.MAX_OPEN = 20`.
  - `void RemoteEditing.open(ConnectionIdentity identity, String path, WindowHandle from)`.
  - `CompletableFuture<List<Optional<FileEntry.Kind>>> RemoteEditing.kinds(ConnectionIdentity identity, List<String> paths, WindowHandle from)`.

- [ ] **Step 1: Wire the build and the manifest**

`plugins/remote/src/main/resources/plugin.toml`, whole file:

```toml
id = "dev.jasper.remote"
name = "Remote"
version = "0.4.0"
entry = "dev.jasper.remote.RemotePlugin"
sdk = ">=0.8.1, <0.9"
description = "SSH and SFTP: browsing, persistent file transfers, editing remote files in the Editor, and saved hosts with groups and favorites, managed Vault imports and optional SSH-agent authentication, ProxyJump, host-key trust, a hosts panel and a palette scope."
vendor = "Jasper"
capabilities = ["terminal.open", "session.provide", "terminal.observe", "palette.contribute"]
requires = [{ id = "dev.jasper.vault", version = ">=0.2", optional = true }, { id = "dev.jasper.editor", version = ">=0.1", optional = true }]
```

`plugins/remote/build.gradle.kts`: replace the first comment line with

```kotlin
// A plugin compiles against the SDK and the exported API of plugins it requires (Vault, and the Editor
// for editing remote files); the application supplies both at run time. Bundled: Apache MINA sshd (with slf4j routed to JUL), BouncyCastle so MINA
```

Then add `compileOnly(project(":jasper-plugin-editor"))` after `compileOnly(project(":jasper-plugin-vault"))`, and `testImplementation(project(":jasper-plugin-editor"))` after `testImplementation(project(":jasper-plugin-vault"))`.

`gradle/plugin-architecture.gradle.kts` line 10:

```kotlin
val declaredImports = mapOf(":jasper-plugin-history" to listOf("dev.jasper.snippets.api"), ":jasper-plugin-remote" to listOf("dev.jasper.vault.api", "dev.jasper.editor.api"))
```

- [ ] **Step 2: Write the failing tests**

`plugins/remote/src/test/java/dev/jasper/remote/edit/RemoteDocumentTest.java`:

```java
package dev.jasper.remote.edit;

import dev.jasper.editor.api.Revision;
import dev.jasper.editor.api.TooLargeException;
import dev.jasper.remote.client.ConnectionIdentity;
import dev.jasper.remote.sftp.EndpointFactory;
import dev.jasper.remote.sftp.LocalEndpoint;
import dev.jasper.remote.sftp.SftpFixture;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

@DisabledOnOs(OS.WINDOWS)
class RemoteDocumentTest {
    @TempDir Path temp;
    final ExecutorService ui = Executors.newSingleThreadExecutor();
    final ExecutorService background = Executors.newCachedThreadPool();
    final List<TestEndpoint> opened = new CopyOnWriteArrayList<>();
    final EditChannels channels = new EditChannels((identity, owner) -> {
        var endpoint = new TestEndpoint(new LocalEndpoint());
        opened.add(endpoint);
        return CompletableFuture.completedFuture(endpoint);
    }, identity -> true, background, ui);
    final ConnectionIdentity prod = EditChannelsTest.identity("prod");

    @AfterEach void stop() throws Exception {
        ui.submit(channels::close).get(10, TimeUnit.SECONDS);
        ui.shutdownNow(); background.shutdownNow();
    }

    RemoteDocument document(Path file) { return new RemoteDocument(prod, file.toString(), channels, Optional::empty); }
    <T> T await(Callable<CompletableFuture<T>> call) throws Exception { return ui.submit(call).get(10, TimeUnit.SECONDS).get(10, TimeUnit.SECONDS); }

    @Test void itIsNamedByItsConnectionAndPath() {
        var document = new RemoteDocument(prod, "/etc/nginx/nginx.conf", channels, Optional::empty);
        var hop = prod.hops().getFirst();
        assertThat(document.key()).isEqualTo("remote:" + hop.host().id() + "/deploy:/etc/nginx/nginx.conf")
            .isEqualTo(RemoteDocument.key(prod, "/etc/nginx/nginx.conf"));
        assertThat(document.name()).isEqualTo("nginx.conf");
        assertThat(document.location()).isEqualTo("deploy@prod:/etc/nginx/nginx.conf");
        assertThat(document.host()).contains("prod");
    }

    @Test void itReadsTheFileAtTheRevisionItReports() throws Exception {
        Path file = Files.writeString(temp.toRealPath().resolve("app.toml"), "a = 1\n");
        var document = document(file);
        var content = await(() -> document.read(1024));
        assertThat(new String(content.bytes(), StandardCharsets.UTF_8)).isEqualTo("a = 1\n");
        assertThat(content.revision()).isEqualTo(new Revision(Files.getLastModifiedTime(file).toMillis(), 6));
        assertThat(await(document::revision)).contains(content.revision());
    }

    @Test void aFileOverTheLimitIsRefusedBeforeAnythingIsRead() throws Exception {
        Path file = Files.writeString(temp.toRealPath().resolve("big.log"), "x".repeat(20));
        var document = document(file);
        assertThatThrownBy(() -> await(() -> document.read(10))).hasCauseInstanceOf(TooLargeException.class);
        assertThat(opened).allSatisfy(endpoint -> assertThat(endpoint.calls).noneMatch(call -> call.startsWith("read ")));
    }

    @Test void aFolderIsRefusedAndAMissingFileHasNoRevision() throws Exception {
        Path directory = Files.createDirectory(temp.toRealPath().resolve("conf.d"));
        assertThatThrownBy(() -> await(() -> document(directory).read(1024))).hasRootCauseMessage(directory + ": Is a folder");
        var missing = document(temp.toRealPath().resolve("gone.txt"));
        assertThat(await(missing::revision)).isEmpty();
        assertThatThrownBy(() -> await(() -> missing.read(1024))).hasCauseInstanceOf(NoSuchFileException.class);
    }

    @Test void writingFollowsTheContractAndReturnsTheNewRevision() throws Exception {
        Path file = Files.writeString(temp.toRealPath().resolve("app.toml"), "a = 1\n");
        var document = document(file);
        var revision = await(() -> document.write("a = 22\n".getBytes(StandardCharsets.UTF_8)));
        assertThat(Files.readString(file)).isEqualTo("a = 22\n");
        assertThat(revision).isEqualTo(new Revision(Files.getLastModifiedTime(file).toMillis(), 7));
        assertThat(opened).anySatisfy(endpoint -> assertThat(endpoint.calls).anyMatch(call -> call.startsWith("publish ")));
    }

    @Test void overSftpItReadsAndWritesThroughTheQueue() throws Exception {
        try (var fixture = new SftpFixture(temp)) {
            Path file = Files.writeString(fixture.files.resolve("site.yaml"), "port: 80\n");
            var endpoints = new EndpointFactory(fixture.connections, fixture.background, fixture.ui, () -> Duration.ofSeconds(5));
            var sftp = new EditChannels((identity, owner) -> endpoints.open(Optional.of(identity), owner.orElse(null), status -> {}),
                fixture.connections::authenticated, fixture.background, fixture.ui);
            try (var held = fixture.lease()) {
                var document = new RemoteDocument(held.identity(), "/site.yaml", sftp, Optional::empty);
                var content = fixture.onUi(() -> document.read(1024)).get(10, TimeUnit.SECONDS);
                assertThat(new String(content.bytes(), StandardCharsets.UTF_8)).isEqualTo("port: 80\n");
                var revision = fixture.onUi(() -> document.write("port: 8080\n".getBytes(StandardCharsets.UTF_8))).get(10, TimeUnit.SECONDS);
                assertThat(Files.readString(file)).isEqualTo("port: 8080\n");
                assertThat(revision.size()).isEqualTo(11);
                assertThat(fixture.onUi(document::revision).get(10, TimeUnit.SECONDS)).contains(revision);
            } finally {
                fixture.onUi(() -> { sftp.close(); return null; });
            }
        }
    }
}
```

`plugins/remote/src/test/java/dev/jasper/remote/edit/RemoteEditingTest.java`:

```java
package dev.jasper.remote.edit;

import dev.jasper.editor.api.Editor;
import dev.jasper.editor.api.EditorDocument;
import dev.jasper.remote.client.ConnectionIdentity;
import dev.jasper.remote.sftp.FileEntry;
import dev.jasper.remote.sftp.LocalEndpoint;
import dev.jasper.sdk.terminal.WindowHandle;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

@DisabledOnOs(OS.WINDOWS)
class RemoteEditingTest {
    @TempDir Path temp;
    final ExecutorService ui = Executors.newSingleThreadExecutor();
    final ExecutorService background = Executors.newCachedThreadPool();
    final List<Optional<WindowHandle>> owners = new CopyOnWriteArrayList<>();
    volatile boolean connected = true;
    final EditChannels channels = new EditChannels((identity, owner) -> {
        owners.add(owner);
        return CompletableFuture.completedFuture(new LocalEndpoint());
    }, identity -> connected, background, ui);
    final List<EditorDocument> documents = new ArrayList<>();
    final List<String> notices = new ArrayList<>();
    volatile RuntimeException editorFailure;
    final Editor editor = new Editor() {
        @Override public void open(Path file) { throw new AssertionError("Remote opens documents, not local paths"); }
        @Override public void open(EditorDocument document) { if (editorFailure != null) throw editorFailure; documents.add(document); }
    };
    volatile Optional<WindowHandle> active = Optional.empty();
    final RemoteEditing editing = new RemoteEditing(editor, channels, () -> active, notices::add);
    final ConnectionIdentity prod = EditChannelsTest.identity("prod");
    final TestWindow origin = new TestWindow(UUID.randomUUID(), new AtomicBoolean(true));

    @AfterEach void stop() throws Exception {
        ui.submit(channels::close).get(10, TimeUnit.SECONDS);
        ui.shutdownNow(); background.shutdownNow();
    }

    <T> T onUi(Callable<T> work) throws Exception { return ui.submit(work).get(10, TimeUnit.SECONDS); }

    @Test void openHandsTheEditorARemoteDocument() throws Exception {
        onUi(() -> { editing.open(prod, "/etc/hosts", origin); return null; });
        assertThat(documents).singleElement().satisfies(document -> {
            assertThat(document).isInstanceOf(RemoteDocument.class);
            assertThat(document.key()).isEqualTo(RemoteDocument.key(prod, "/etc/hosts"));
            assertThat(document.location()).isEqualTo("deploy@prod:/etc/hosts");
        });
    }

    @Test void anEditorFailureBecomesANotice() throws Exception {
        editorFailure = new IllegalStateException("the Editor window is closing");
        onUi(() -> { editing.open(prod, "/etc/hosts", origin); return null; });
        assertThat(notices).containsExactly("Could not open /etc/hosts in the Editor: the Editor window is closing");
        assertThat(documents).isEmpty();
    }

    @Test void signInPromptsBelongToTheOriginWindowWhileItIsOpenThenToTheActiveWindow() throws Exception {
        connected = false;
        Path file = Files.writeString(temp.toRealPath().resolve("hosts"), "127.0.0.1 localhost\n");
        onUi(() -> { editing.open(prod, file.toString(), origin); return null; });
        var document = documents.getFirst();
        onUi(document::revision).get(10, TimeUnit.SECONDS);
        origin.open().set(false);
        var other = new TestWindow(UUID.randomUUID(), new AtomicBoolean(true));
        active = Optional.of(other);
        onUi(document::revision).get(10, TimeUnit.SECONDS);
        assertThat(owners).containsExactly(Optional.of(origin), Optional.of(other));
        active = Optional.empty();
        assertThatThrownBy(() -> onUi(document::revision).get(10, TimeUnit.SECONDS))
            .hasRootCauseMessage("Reconnecting to prod needs an open terminal window for the sign-in prompt.");
    }

    @Test void kindsFollowLinksInOrder() throws Exception {
        Path folder = temp.toRealPath();
        Path file = Files.writeString(folder.resolve("app.conf"), "x");
        Files.createSymbolicLink(folder.resolve("to-file"), Path.of("app.conf"));
        Files.createDirectory(folder.resolve("conf.d"));
        Files.createSymbolicLink(folder.resolve("to-folder"), Path.of("conf.d"));
        Files.createSymbolicLink(folder.resolve("dangling"), Path.of("gone"));
        var paths = List.of(file.toString(), folder.resolve("to-file").toString(), folder.resolve("to-folder").toString(), folder.resolve("dangling").toString());
        var kinds = onUi(() -> editing.kinds(prod, paths, origin)).get(10, TimeUnit.SECONDS);
        assertThat(kinds).containsExactly(Optional.of(FileEntry.Kind.FILE), Optional.of(FileEntry.Kind.FILE),
            Optional.of(FileEntry.Kind.DIRECTORY), Optional.empty());
    }
}
```

- [ ] **Step 3: Run the tests to verify they fail**

Run: `./gradlew :jasper-plugin-remote:test --tests 'dev.jasper.remote.edit.RemoteDocumentTest' --tests 'dev.jasper.remote.edit.RemoteEditingTest'`
Expected: compilation fails, because `RemoteDocument` and `RemoteEditing` do not exist.

- [ ] **Step 4: Write `RemoteDocument`**

`plugins/remote/src/main/java/dev/jasper/remote/edit/RemoteDocument.java`:

```java
package dev.jasper.remote.edit;

import dev.jasper.editor.api.Content;
import dev.jasper.editor.api.EditorDocument;
import dev.jasper.editor.api.Revision;
import dev.jasper.editor.api.TooLargeException;
import dev.jasper.remote.client.ConnectionIdentity;
import dev.jasper.remote.sftp.FileEndpoint;
import dev.jasper.remote.sftp.FileEntry;
import dev.jasper.remote.sftp.FilePaths;
import dev.jasper.sdk.terminal.WindowHandle;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileSystemException;
import java.nio.file.NoSuchFileException;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/** One file on one connection identity, edited in the Editor. Every operation queues on {@link EditChannels}. */
public final class RemoteDocument implements EditorDocument {
    private final ConnectionIdentity identity;
    private final String path;
    private final EditChannels channels;
    private final Supplier<Optional<WindowHandle>> owner;

    /**
     * @param identity the connection the file was browsed on
     * @param path the absolute path as browsed
     * @param channels where its operations queue
     * @param owner the window for sign-in prompts when reconnecting
     */
    public RemoteDocument(ConnectionIdentity identity, String path, EditChannels channels, Supplier<Optional<WindowHandle>> owner) {
        this.identity = Objects.requireNonNull(identity);
        try { this.path = FilePaths.absolute(path); }
        catch (IOException relative) { throw new IllegalArgumentException("An absolute path is required: " + path, relative); }
        this.channels = Objects.requireNonNull(channels);
        this.owner = Objects.requireNonNull(owner);
    }

    /**
     * @param identity a connection identity
     * @param path an absolute, normalized path
     * @return {@code remote:}, each hop's saved-host id and login, and the path; cosmetic host edits keep it
     */
    public static String key(ConnectionIdentity identity, String path) {
        return "remote:" + identity.hops().stream().map(hop -> hop.host().id() + "/" + hop.username()).collect(Collectors.joining(",")) + ":" + path;
    }

    @Override public String key() { return key(identity, path); }
    @Override public String name() { return path.equals("/") ? path : path.substring(path.lastIndexOf('/') + 1); }
    @Override public String location() { return identity.username() + "@" + identity.host().name() + ":" + path; }
    @Override public Optional<String> host() { return Optional.of(identity.host().name()); }

    @Override public CompletableFuture<Content> read(long limitBytes) {
        return channels.submit(identity, owner, endpoint -> read(endpoint, path, limitBytes));
    }

    @Override public CompletableFuture<Optional<Revision>> revision() {
        return channels.submit(identity, owner, endpoint -> SafeWrite.resolve(endpoint, path).entry().map(RemoteDocument::revision));
    }

    @Override public CompletableFuture<Revision> write(byte[] content) {
        return channels.submit(identity, owner, endpoint -> revision(SafeWrite.write(endpoint, path, content)));
    }

    /** Resolves links, checks the size before transferring anything, then reads at most the limit. */
    static Content read(FileEndpoint endpoint, String path, long limitBytes) throws IOException {
        var resolved = SafeWrite.resolve(endpoint, path);
        FileEntry file = resolved.entry().orElseThrow(() -> new NoSuchFileException(path));
        if (file.kind() == FileEntry.Kind.DIRECTORY) throw new FileSystemException(path, null, "Is a folder");
        if (file.kind() != FileEntry.Kind.FILE) throw new FileSystemException(path, null, "Not a regular file");
        if (file.size() > limitBytes) throw new TooLargeException(file.size(), limitBytes);
        byte[] bytes;
        try (InputStream in = endpoint.read(resolved.path(), 0)) {
            // One byte past the limit shows that the file grew beyond it after the size check.
            bytes = in.readNBytes((int) Math.min(limitBytes, Integer.MAX_VALUE - 9L) + 1);
        }
        if (bytes.length > limitBytes) throw new TooLargeException(bytes.length, limitBytes);
        return new Content(bytes, new Revision(file.modifiedMillis(), bytes.length));
    }

    private static Revision revision(FileEntry file) { return new Revision(file.modifiedMillis(), file.size()); }
}
```

- [ ] **Step 5: Write `RemoteEditing`**

`plugins/remote/src/main/java/dev/jasper/remote/edit/RemoteEditing.java`:

```java
package dev.jasper.remote.edit;

import dev.jasper.editor.api.Editor;
import dev.jasper.remote.client.ConnectionIdentity;
import dev.jasper.remote.sftp.FileEntry;
import dev.jasper.sdk.terminal.WindowHandle;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Remote's side of the Editor: opens remote files as {@link RemoteDocument}s and resolves links for the SFTP
 * panel. Constructed only when the Editor is installed; its method signatures name no Editor type, so callers
 * load without it. UI thread only.
 */
public final class RemoteEditing {
    /** The most files Open in Editor opens at once. */
    public static final int MAX_OPEN = 20;

    private final Editor editor;
    private final EditChannels channels;
    private final Supplier<Optional<WindowHandle>> activeWindow;
    private final Consumer<String> notice;

    /**
     * @param editor the Editor's service
     * @param channels where documents queue their operations
     * @param activeWindow the terminal window used last, for sign-in prompts once the origin window has closed
     * @param notice shows an error notice
     */
    public RemoteEditing(Editor editor, EditChannels channels, Supplier<Optional<WindowHandle>> activeWindow, Consumer<String> notice) {
        this.editor = Objects.requireNonNull(editor); this.channels = Objects.requireNonNull(channels);
        this.activeWindow = Objects.requireNonNull(activeWindow); this.notice = Objects.requireNonNull(notice);
    }

    /**
     * Opens a remote file in an editor tab, or selects its tab.
     *
     * @param from the terminal window it was opened from, which owns sign-in prompts while it is open
     */
    public void open(ConnectionIdentity identity, String path, WindowHandle from) {
        try {
            editor.open(new RemoteDocument(identity, path, channels, owner(from)));
        } catch (RuntimeException failure) {
            // The host does not contain calls between plugins.
            notice.accept("Could not open " + path + " in the Editor: "
                + (failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage()));
        }
    }

    /**
     * @return what each path's links lead to, in order; empty where nothing is there
     */
    public CompletableFuture<List<Optional<FileEntry.Kind>>> kinds(ConnectionIdentity identity, List<String> paths, WindowHandle from) {
        var requested = List.copyOf(paths);
        return channels.submit(identity, owner(from), endpoint -> {
            var kinds = new ArrayList<Optional<FileEntry.Kind>>();
            for (String path : requested) kinds.add(SafeWrite.resolve(endpoint, path).entry().map(FileEntry::kind));
            return kinds;
        });
    }

    private Supplier<Optional<WindowHandle>> owner(WindowHandle from) {
        return () -> from.isOpen() ? Optional.of(from) : activeWindow.get();
    }
}
```

- [ ] **Step 6: Run the tests to verify they pass**

Run: `./gradlew :jasper-plugin-remote:test --tests 'dev.jasper.remote.edit.*' verifyPluginArchitecture`
Expected: PASS. `verifyPluginArchitecture` accepts Remote's references to `dev.jasper.editor.api`.

- [ ] **Step 7: Commit**

```bash
git add plugins/remote/src/main/resources/plugin.toml plugins/remote/build.gradle.kts gradle/plugin-architecture.gradle.kts plugins/remote/src/main/java/dev/jasper/remote/edit plugins/remote/src/test/java/dev/jasper/remote/edit
git commit -m "feat(remote): Remote 0.4.0 optionally requires the Editor; remote documents

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 5: The SFTP panel opens files in the Editor

**Files:**
- Modify: `plugins/remote/src/main/java/dev/jasper/remote/ui/sftp/SftpPanel.java`
- Modify: `plugins/remote/src/main/java/dev/jasper/remote/ui/sftp/SftpUi.java`
- Modify: `plugins/remote/src/main/java/dev/jasper/remote/RemotePlugin.java`
- Test: `plugins/remote/src/test/java/dev/jasper/remote/ui/sftp/SftpPanelTest.java`
- Create (test): `plugins/remote/src/test/java/dev/jasper/remote/RemoteEditingPluginTest.java`

**Interfaces:**
- Consumes:
  - `RemoteEditing.open`/`kinds`/`MAX_OPEN` and `EditChannels` (Tasks 3 and 4);
  - `Connections.authenticated` (Task 3);
  - `EndpointFactory.open(Optional<ConnectionIdentity>, WindowHandle, Consumer<String>)` (existing).
- Produces:
  - `record SftpPanel.Editing(Consumer<FileEntry> activate, Runnable open)`;
  - `void SftpPanel.editing(SftpPanel.Editing editing)`;
  - `void SftpUi.editing(RemoteEditing editing)`;
  - an "Open in Editor" item in the panel's right-click menu.

- [ ] **Step 1: Write the failing tests**

Add to `SftpPanelTest`:

```java
    @Test void filesGoToTheEditorOnlyWhenItIsPresent() throws Exception {
        SwingUtilities.invokeAndWait(()-> {
            var panel=new SftpPanel(icon->new ImageIcon(new java.awt.image.BufferedImage(16,16,2)));var navigated=new ArrayList<String>();
            panel.actions(new SftpPanel.Actions(navigated::add,()->{},()->{},f->{},()->{},()->{},()->{},()->{},()->{},()->{},()->{},()->{},p->{}));
            var folder=new FileEntry("conf",FileEntry.Kind.DIRECTORY,0,1000,0755,""),file=new FileEntry("notes.toml",FileEntry.Kind.FILE,6,1000,0644,""),link=new FileEntry("alias",FileEntry.Kind.LINK,10,1000,0777,"notes.toml");
            panel.showPage("host","/srv",List.of(folder,file,link),0,3,false);
            var item=menuItem(panel.table().getComponentPopupMenu(),"Open in Editor");
            assertThat(item.isVisible()).as("hidden without the Editor").isFalse();
            enter(panel,1);assertThat(navigated).as("a file does nothing without the Editor").isEmpty();
            enter(panel,2);assertThat(navigated).as("a link navigates as before").containsExactly("alias");
            var activated=new ArrayList<String>();var opened=new int[1];
            panel.editing(new SftpPanel.Editing(entry->activated.add(entry.name()),()->opened[0]++));
            assertThat(item.isVisible()).isTrue();
            enter(panel,0);assertThat(navigated).as("a folder still navigates").containsExactly("alias","conf");
            enter(panel,1);enter(panel,2);assertThat(activated).containsExactly("notes.toml","alias");
            doubleClick(panel,1);assertThat(activated).containsExactly("notes.toml","alias","notes.toml");
            panel.table().setRowSelectionInterval(0,0);assertThat(item.isEnabled()).as("a folder alone holds no file").isFalse();
            panel.table().setRowSelectionInterval(0,2);assertThat(item.isEnabled()).isTrue();
            item.doClick();assertThat(opened[0]).isEqualTo(1);
        });
    }
    private static void enter(SftpPanel panel,int row) { panel.table().setRowSelectionInterval(row,row);panel.table().getActionMap().get("browse").actionPerformed(null); }
    private static void doubleClick(SftpPanel panel,int row) {
        panel.table().setRowSelectionInterval(row,row);var cell=panel.table().getCellRect(row,0,true);
        panel.table().dispatchEvent(new java.awt.event.MouseEvent(panel.table(),java.awt.event.MouseEvent.MOUSE_CLICKED,0,0,cell.x+2,cell.y+2,2,false,java.awt.event.MouseEvent.BUTTON1));
    }
    private static JMenuItem menuItem(JPopupMenu menu,String text) {
        return Arrays.stream(menu.getComponents()).filter(JMenuItem.class::isInstance).map(JMenuItem.class::cast).filter(item->text.equals(item.getText())).findFirst().orElseThrow();
    }
```

(`SftpPanelTest` already imports `java.util.*`, `java.util.List`, `javax.swing.*` and `java.awt.*`.)

`plugins/remote/src/test/java/dev/jasper/remote/RemoteEditingPluginTest.java`:

```java
package dev.jasper.remote;

import dev.jasper.editor.api.Editor;
import dev.jasper.editor.api.EditorDocument;
import dev.jasper.remote.client.LoopbackServer;
import dev.jasper.remote.hosts.Auth;
import dev.jasper.remote.hosts.RemoteHost;
import dev.jasper.remote.trust.KnownHosts;
import dev.jasper.remote.ui.sftp.SftpPanel;
import dev.jasper.remote.ui.sftp.SftpUi;
import dev.jasper.sdk.PluginInfo;
import dev.jasper.sdk.plugin.Plugin;
import dev.jasper.sdk.plugin.PluginContext;
import dev.jasper.sdk.testing.FakePluginHost;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import org.apache.sshd.common.file.virtualfs.VirtualFileSystemFactory;
import org.apache.sshd.sftp.server.SftpSubsystemFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

/** The SFTP browser with and without the Editor, against a loopback SFTP server. POSIX paths and links. */
@DisabledOnOs(OS.WINDOWS)
class RemoteEditingPluginTest {
    static final PluginInfo EDITOR = new PluginInfo("dev.jasper.editor", "Editor", "0.1.0", Set.of());

    /** A stand-in Editor that records what Remote asks it to open. */
    static final class FakeEditor implements Plugin {
        final List<EditorDocument> documents = new CopyOnWriteArrayList<>();
        volatile RuntimeException failure;
        @Override public void start(PluginContext context) {
            context.services().publish(Editor.class, new Editor() {
                @Override public void open(Path file) { throw new AssertionError("Remote opens documents, not local paths"); }
                @Override public void open(EditorDocument document) { if (failure != null) throw failure; documents.add(document); }
            });
        }
        @Override public void stop() { }
    }

    /** Connects a saved host whose SFTP root is {@code files} and shows its listing of {@code rows} entries. */
    static SftpPanel browse(FakePluginHost host, LoopbackServer server, Path files, Path dir, Optional<FakeEditor> editor, int rows) throws Exception {
        server.server.setFileSystemFactory(new VirtualFileSystemFactory(files.toRealPath()));
        server.server.setSubsystemFactories(List.of(new SftpSubsystemFactory()));
        var vault = new FakeVault();
        host.start(FakeVault.INFO, Set.of(), Set.of(), vault);
        editor.ifPresent(fake -> host.start(EDITOR, Set.of(), Set.of(), fake));
        RemotePlugin plugin = new TestRemotePlugin(Runnable::run, context -> Optional.empty(), (delay, task) -> () -> {}, dir.resolve("ssh"));
        var context = host.start(RemotePluginTest.INFO, Set.of(), Set.of("dev.jasper.vault", "dev.jasper.editor"), plugin);
        UUID credential = vault.password("deploy login", "deploy", "s3cret");
        RemoteHost prod = RemoteHost.create("prod", "127.0.0.1", server.port(), "", new Auth.Vault(credential), "", Optional.empty()).withFollowDirectory(false);
        plugin.store().put(prod);
        RemotePluginTest.settle(host);
        new KnownHosts(context.dataDirectory().resolve("known_hosts"), Optional.empty()).trust("127.0.0.1", server.port(), server.hostPublicKey());
        UUID window = host.addTerminalWindow();
        host.activateTerminalWindow(window);
        var panel = (SftpPanel) host.openPanel(SftpUi.PANEL, window);
        plugin.openHost(context.terminals().window(window).orElseThrow(), prod); RemotePluginTest.settle(host);
        host.focusTerminalPane(host.terminalPanes().getFirst()); host.flush();
        RemotePluginTest.awaitRowCount(host, panel, rows);
        return panel;
    }

    static void select(SftpPanel panel, String name) {
        for (int row = 0; row < panel.table().getRowCount(); row++)
            if (name.equals(panel.table().getValueAt(row, 0))) { panel.table().setRowSelectionInterval(row, row); return; }
        throw new AssertionError("no row " + name);
    }
    static void enter(SftpPanel panel, String name) { select(panel, name); panel.table().getActionMap().get("browse").actionPerformed(null); }
    static void await(FakePluginHost host, BooleanSupplier condition, String what) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) { RemotePluginTest.settle(host); Thread.sleep(20); }
        assertThat(condition.getAsBoolean()).as(what).isTrue();
    }
    static <T> T done(FakePluginHost host, CompletableFuture<T> future) throws Exception { await(host, future::isDone, "the operation finishes"); return future.get(); }
    static List<String> labels(java.awt.Container root) {
        var texts = new ArrayList<String>();
        for (var child : root.getComponents()) {
            if (child instanceof javax.swing.JLabel label && label.getText() != null) texts.add(label.getText());
            if (child instanceof java.awt.Container container) texts.addAll(labels(container));
        }
        return texts;
    }

    @Test void enterOpensFilesAndLinksToFilesAndALinkToAFolderNavigates(@TempDir Path dir) throws Exception {
        Path files = Files.createDirectories(dir.resolve("files"));
        Files.writeString(files.resolve("notes.toml"), "a = 1\n");
        Files.createSymbolicLink(files.resolve("alias.toml"), Path.of("notes.toml"));
        Files.createDirectory(files.resolve("conf"));
        Files.createSymbolicLink(files.resolve("conf-link"), Path.of("conf"));
        var editor = new FakeEditor();
        try (var server = new LoopbackServer(); var host = new FakePluginHost()) {
            var panel = browse(host, server, files, dir, Optional.of(editor), 4);
            enter(panel, "notes.toml");
            assertThat(editor.documents).singleElement().satisfies(document -> {
                assertThat(document.name()).isEqualTo("notes.toml");
                assertThat(document.location()).isEqualTo("deploy@prod:/notes.toml");
                assertThat(document.host()).contains("prod");
                assertThat(document.key()).startsWith("remote:").endsWith(":/notes.toml");
            });
            var notes = editor.documents.getFirst();
            assertThat(new String(done(host, notes.read(1 << 20)).bytes(), StandardCharsets.UTF_8)).isEqualTo("a = 1\n");
            done(host, notes.write("a = 2\n".getBytes(StandardCharsets.UTF_8)));
            assertThat(Files.readString(files.resolve("notes.toml"))).isEqualTo("a = 2\n");

            enter(panel, "alias.toml");
            await(host, () -> editor.documents.size() == 2, "the link to a file opens");
            var alias = editor.documents.get(1);
            assertThat(alias.name()).isEqualTo("alias.toml");
            done(host, alias.write("b = 3\n".getBytes(StandardCharsets.UTF_8)));
            assertThat(Files.readString(files.resolve("notes.toml"))).isEqualTo("b = 3\n");
            assertThat(Files.isSymbolicLink(files.resolve("alias.toml"))).isTrue();

            enter(panel, "conf-link");
            await(host, () -> panel.directory().equals("/conf"), "the link to a folder navigates");
            assertThat(editor.documents).hasSize(2);
            host.stopAll();
            assertThat(host.failures()).isEmpty();
        }
    }

    @Test void openInEditorOpensTheSelectedFilesTwentyAtATime(@TempDir Path dir) throws Exception {
        Path files = Files.createDirectories(dir.resolve("files"));
        for (int i = 0; i < 25; i++) Files.writeString(files.resolve(String.format("f%02d.txt", i)), "file " + i);
        Files.createSymbolicLink(files.resolve("alias.txt"), Path.of("f00.txt"));
        Files.createDirectory(files.resolve("conf"));
        Files.createSymbolicLink(files.resolve("conf-link"), Path.of("conf"));
        var editor = new FakeEditor();
        try (var server = new LoopbackServer(); var host = new FakePluginHost()) {
            var panel = browse(host, server, files, dir, Optional.of(editor), 28);
            panel.table().selectAll();
            RemotePluginTest.menuItem(panel.table().getComponentPopupMenu(), "Open in Editor").doClick();
            await(host, () -> labels(panel).contains("Opened 20 of 26 files."), "the cap is reported");
            assertThat(editor.documents).hasSize(20).extracting(EditorDocument::name).doesNotContain("conf", "conf-link");
            host.stopAll();
            assertThat(host.failures()).isEmpty();
        }
    }

    @Test void withoutTheEditorFilesStayPutAndOpenInEditorIsHidden(@TempDir Path dir) throws Exception {
        Path files = Files.createDirectories(dir.resolve("files"));
        Files.writeString(files.resolve("notes.toml"), "a = 1\n");
        try (var server = new LoopbackServer(); var host = new FakePluginHost()) {
            var panel = browse(host, server, files, dir, Optional.empty(), 1);
            assertThat(RemotePluginTest.menuItem(panel.table().getComponentPopupMenu(), "Open in Editor").isVisible()).isFalse();
            enter(panel, "notes.toml");
            RemotePluginTest.settle(host);
            assertThat(panel.directory()).isEqualTo("/");
            host.stopAll();
            assertThat(host.failures()).isEmpty();
        }
    }

    @Test void anEditorThatThrowsBecomesANoticeAndThePanelKeepsWorking(@TempDir Path dir) throws Exception {
        Path files = Files.createDirectories(dir.resolve("files"));
        Files.writeString(files.resolve("notes.toml"), "a = 1\n");
        var editor = new FakeEditor();
        editor.failure = new IllegalStateException("the Editor window is closing");
        try (var server = new LoopbackServer(); var host = new FakePluginHost()) {
            var panel = browse(host, server, files, dir, Optional.of(editor), 1);
            enter(panel, "notes.toml");
            assertThat(host.notices()).containsExactly("dev.jasper.remote: Could not open /notes.toml in the Editor: the Editor window is closing");
            editor.failure = null;
            enter(panel, "notes.toml");
            assertThat(editor.documents).hasSize(1);
            host.stopAll();
            assertThat(host.failures()).isEmpty();
        }
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :jasper-plugin-remote:test --tests 'dev.jasper.remote.ui.sftp.SftpPanelTest' --tests 'dev.jasper.remote.RemoteEditingPluginTest'`
Expected: compilation fails, because `SftpPanel.Editing` and `editing(…)` do not exist.

- [ ] **Step 3: Teach the panel about files**

In `SftpPanel.java`:

1. After the `Actions` record, add:

```java
    /** What double-click, Enter and Open in Editor do with files and links; set only when the Editor is installed. */
    public record Editing(Consumer<FileEntry> activate,Runnable open) {}
```

2. After the field `private final JPopupMenu popup=new JPopupMenu();`, add:

```java
    private final JMenuItem openInEditor;
    private final JPopupMenu.Separator editingSeparator=new JPopupMenu.Separator();
    private Editing editing;
```

3. In the constructor, directly before the line that begins `        item("Upload folder…",IconName.UPLOAD,`, insert:

```java
        openInEditor=new JMenuItem("Open in Editor",icons.apply(IconName.FILE));openInEditor.addActionListener(event->{if(editing!=null) editing.open().run();});
        openInEditor.setVisible(false);editingSeparator.setVisible(false);popup.add(openInEditor);popup.add(editingSeparator);
```

4. After `public void actions(Actions actions) { this.actions=actions; }`, add:

```java
    /** Shows Open in Editor and sends files to {@code editing}: double-click and Enter on one file or link, and the menu item. */
    public void editing(Editing editing) { this.editing=editing;openInEditor.setVisible(true);editingSeparator.setVisible(true);selectionChanged(); }
```

5. Replace `activate`:

```java
    private void activate() {
        if(actions==null) return;var selected=selection();if(selected.size()!=1) return;var entry=selected.getFirst();
        if(entry.kind()==FileEntry.Kind.DIRECTORY || entry.kind()==FileEntry.Kind.LINK && editing==null) actions.navigate().accept(entry.name());
        else if(editing!=null && (entry.kind()==FileEntry.Kind.FILE || entry.kind()==FileEntry.Kind.LINK)) editing.activate().accept(entry);
    }
```

6. Replace `selectionChanged`:

```java
    private void selectionChanged() {
        var selection=selection();boolean selected=!selection.isEmpty();for(String name:List.of("Download selected","Delete selected","Copy paths")) buttons.get(name).setEnabled(selected);buttons.get("Upload files").setEnabled(loaded);buttons.get("New folder").setEnabled(loaded);
        openInEditor.setEnabled(selection.stream().anyMatch(entry->entry.kind()==FileEntry.Kind.FILE || entry.kind()==FileEntry.Kind.LINK));
    }
```

- [ ] **Step 4: Open files from `SftpUi`**

In `SftpUi.java`:

1. Add the import `import dev.jasper.remote.edit.RemoteEditing;`.

2. After the field `private Consumer<UUID> followRequested=pane->{};`, add:

```java
    private RemoteEditing editing;
```

3. After `public void onFollowRequested(Consumer<UUID> listener) { followRequested=listener; }`, add:

```java
    /** Sends files to the Editor. Call before any panel opens, and only when the Editor is installed. */
    public void editing(RemoteEditing editing) { this.editing=editing; }
```

4. In `create(PanelHost host)`, directly after the line that begins `        controller.operations(new SftpController.Operations(`, insert:

```java
        if(editing!=null) panel.editing(new SftpPanel.Editing(entry->activate(view,entry),()->openSelected(view)));
```

5. Before `private static String message(Throwable failure)`, add:

```java
    /** Double-click or Enter on one file or link: a file opens in the Editor; a link opens when it leads to a file and navigates when it leads to a folder. */
    private void activate(View view,FileEntry entry) {
        var captured=view.controller().capture();if(captured.isEmpty() || closed || editing==null) return;var capture=captured.orElseThrow();WindowHandle owner=view.host().window();
        String path;try { path=FilePaths.child(capture.directory(),entry.name()); } catch(IOException invalid) { view.panel().error(invalid.getMessage());return; }
        if(entry.kind()==FileEntry.Kind.FILE) { editing.open(capture.identity(),path,owner);return; }
        editing.kinds(capture.identity(),List.of(path),owner).whenComplete((kinds,failure)->ui.execute(()-> {
            if(closed || !owner.isOpen()) return;
            if(failure!=null) { view.panel().error(message(failure));return; }
            var kind=kinds.getFirst();
            if(kind.isEmpty()) view.panel().error(entry.name()+": its target does not exist");
            else if(kind.orElseThrow()==FileEntry.Kind.FILE) editing.open(capture.identity(),path,owner);
            else if(kind.orElseThrow()==FileEntry.Kind.DIRECTORY) { var now=view.controller().capture();if(now.isPresent() && now.orElseThrow().identity().equals(capture.identity())) view.controller().manual(path); }
            else view.panel().error(entry.name()+" is not a regular file");
        }));
    }
    /** Open in Editor: the selected files and links to files, at most {@link RemoteEditing#MAX_OPEN}. */
    private void openSelected(View view) {
        var captured=view.controller().capture();if(captured.isEmpty() || closed || editing==null) return;var capture=captured.orElseThrow();WindowHandle owner=view.host().window();
        var candidates=capture.selection().stream().filter(entry->entry.kind()==FileEntry.Kind.FILE || entry.kind()==FileEntry.Kind.LINK).toList();if(candidates.isEmpty()) return;
        var paths=new ArrayList<String>();var links=new ArrayList<String>();
        try { for(var entry:candidates) { String path=FilePaths.child(capture.directory(),entry.name());paths.add(path);if(entry.kind()==FileEntry.Kind.LINK) links.add(path); } }
        catch(IOException invalid) { view.panel().error(invalid.getMessage());return; }
        CompletableFuture<List<Optional<FileEntry.Kind>>> resolved=links.isEmpty()?CompletableFuture.completedFuture(List.of()):editing.kinds(capture.identity(),links,owner);
        resolved.whenComplete((kinds,failure)->ui.execute(()-> {
            if(closed || !owner.isOpen()) return;
            if(failure!=null) { view.panel().error(message(failure));return; }
            var files=new ArrayList<String>();int link=0;
            for(int i=0;i<candidates.size();i++) {
                if(candidates.get(i).kind()==FileEntry.Kind.FILE) files.add(paths.get(i));
                else if(kinds.get(link++).filter(kind->kind==FileEntry.Kind.FILE).isPresent()) files.add(paths.get(i));
            }
            for(String path:files.subList(0,Math.min(files.size(),RemoteEditing.MAX_OPEN))) editing.open(capture.identity(),path,owner);
            if(files.size()>RemoteEditing.MAX_OPEN) view.panel().error("Opened "+RemoteEditing.MAX_OPEN+" of "+files.size()+" files.");
        }));
    }
```

- [ ] **Step 5: Look up the Editor in `RemotePlugin`**

In `RemotePlugin.java`:

1. After the field `private dev.jasper.remote.ui.sftp.DirectoryFollower follower;`, add:

```java
    private dev.jasper.remote.edit.EditChannels editChannels;
```

2. In `start`, directly after the line `        sftpUi=new dev.jasper.remote.ui.sftp.SftpUi(context,ui,transferBackground,connections,endpoints,()->transfers,store::hosts,this::sftpPane,transferUi::strip,transferUi::release);`, insert:

```java
        try {
            var editor=context.services().find(dev.jasper.editor.api.Editor.class);
            if(editor.isPresent()) {
                editChannels=new dev.jasper.remote.edit.EditChannels((identity,owner)->endpoints.open(Optional.of(identity),owner.orElse(null),status->{}),connections::authenticated,transferBackground,ui);
                sftpUi.editing(new dev.jasper.remote.edit.RemoteEditing(editor.orElseThrow(),editChannels,()->context.terminals().activeWindow(),context.notices()::error));
            }
        } catch(NoClassDefFoundError absent) { /* the Editor is not installed: files in the SFTP panel do not open */ }
```

3. In `stop`, replace

```java
        if(follower!=null)follower.close();if(sftpUi!=null)sftpUi.close();if(transferUi!=null)transferUi.close();if(transfers!=null)transfers.close();
```

with

```java
        if(follower!=null)follower.close();if(sftpUi!=null)sftpUi.close();if(editChannels!=null)editChannels.close();if(transferUi!=null)transferUi.close();if(transfers!=null)transfers.close();
```

- [ ] **Step 6: Run the tests to verify they pass**

Run: `./gradlew :jasper-plugin-remote:check`
Expected: PASS. That covers every Remote test (including the new ones) and `verifyPluginArchitecture`.

- [ ] **Step 7: Commit**

```bash
git add plugins/remote/src/main/java/dev/jasper/remote/ui/sftp/SftpPanel.java plugins/remote/src/main/java/dev/jasper/remote/ui/sftp/SftpUi.java plugins/remote/src/main/java/dev/jasper/remote/RemotePlugin.java plugins/remote/src/test/java/dev/jasper/remote/ui/sftp/SftpPanelTest.java plugins/remote/src/test/java/dev/jasper/remote/RemoteEditingPluginTest.java
git commit -m "feat(remote): open SFTP files in the Editor by double-click, Enter or Open in Editor

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 6: Documentation and the full check

**Files:**
- Modify: `docs/remote.md`, `docs/editor.md`, `docs/editor-verification.md`, `docs/README.md`, `AGENTS.md`, `docs/STATUS.md`, and this plan's Status banner.

**Interfaces:** none.

- [ ] **Step 1: `docs/remote.md`**

Replace `Remote 0.3 includes SFTP in the same plugin as SSH.` with `Remote 0.4 includes SFTP in the same plugin as SSH.`

Then insert this subsection after the bullet that begins `- **Delete** requires confirmation` (and its continuation lines), before the paragraph that begins `Transfers appear at the bottom of the SFTP sidebar`:

```markdown
### Editing remote files

With the bundled Editor installed, double-click a file in the SFTP browser, or select it and press Enter, to open it
in a tab of the Editor window; saving writes it back to the host. A link to a file opens the file it leads to (saving
changes that file, and the link stays a link); a link to a folder navigates as before. **Open in Editor** in the
right-click menu opens the selected files and links to files, at most 20 at a time, and says so ("Opened 20 of 57
files.") when there were more. Without the Editor, double-clicking a file does nothing and the menu item is hidden.

Saving follows the Editor's safe write. The new content goes to a hidden, owner-only sibling
(`.<name>.jasper-<random>.tmp`) beside the file, which gets the file's permissions and atomically replaces it through
the `posix-rename@openssh.com` extension. Remote writes the file in place instead, keeping its owner, group and links,
when the folder does not allow the sibling, when the sibling would get another owner or group, or when the server has
no atomic rename; an interrupted write in place may leave a partial file, and the tab stays unsaved so you can retry.
A read-only file is refused. Before saving, the Editor compares the file's modification time and size with what it
opened and asks when someone else changed it; SFTP reports times to the second, so a change within the same second
that keeps the size goes unnoticed. Remote files are not watched for outside changes; Revert re-reads them.

Editor operations for one connection run one at a time on one SFTP channel of the live SSH connection, opened for the
first and closed when none are left. When the connection has closed, the next operation reconnects; sign-in prompts
appear in the terminal window the file was opened from, or else in the active one. With no terminal window open it
fails with "Reconnecting to prod-web needs an open terminal window for the sign-in prompt."
```

- [ ] **Step 2: `docs/editor.md`**

Replace the first paragraph's first sentence, `The bundled Editor plugin (\`dev.jasper.editor\`) edits text files in tabs in one Editor window,`, with `The bundled Editor plugin (\`dev.jasper.editor\`) edits local and remote text files in tabs in one Editor window,`.

Insert this section after the `## Outside changes` section, before `## Languages`:

```markdown
## Remote files

With Remote installed, double-click a file in the SFTP browser to edit it here (see
[Remote: editing remote files](remote.md#editing-remote-files)). A remote tab shows its host dimmed after the name,
saves back to the host with the same safe write, and asks before overwriting a file someone else changed. Remote files
are not watched for outside changes; Revert re-reads them. Save As writes a local copy.
```

- [ ] **Step 3: `docs/editor-verification.md`**

Replace the first paragraph with:

```markdown
Automated coverage runs in `./gradlew check` (`plugins/editor/src/test`, `plugins/remote/src/test` for remote files,
the SDK contract suite and `JasperApplicationPluginsTest`). These GUI checks are the user's; an unchecked item is
unverified.
```

Append:

```markdown
## Plan B: remote files

- [ ] 12. In the SFTP browser, double-clicking a file (and Enter on a selected file) opens it in a new Editor tab with the host dimmed after its name; the tab's tooltip reads `user@host:/path`.
- [ ] 13. Editing and saving a remote file writes it back: `cat` on the host shows the new content, and `ls -l` shows the same owner, group and permissions.
- [ ] 14. Changing the same remote file from the terminal (`echo x >> file`) before saving makes the save ask Overwrite / Reload / Cancel.
- [ ] 15. Double-clicking a link to a file opens the file, and saving keeps the link (`ls -l` still shows `->`); double-clicking a link to a folder navigates into it.
- [ ] 16. Selecting several files and choosing Open in Editor opens each in a tab; with more than 20 selected, 20 open and the browser says "Opened 20 of N files."
- [ ] 17. Saving a read-only remote file (`chmod 444`) fails with "permission denied" and leaves it unchanged.
- [ ] 18. After the SSH connection ends (close its tabs and wait for it to drop), saving an edited remote tab reconnects, asking for sign-in in a terminal window if needed; with every terminal window closed, the save fails with "Reconnecting to <host> needs an open terminal window for the sign-in prompt."
- [ ] 19. With the Editor disabled in the Plugins manager (after the restart), double-clicking a file in the SFTP browser does nothing and Open in Editor is not in the menu.
```

- [ ] **Step 4: `docs/README.md` and `AGENTS.md`**

`docs/README.md`: replace `- [Editor](editor.md): editing local files in tabs in the Editor window,` with `- [Editor](editor.md): editing local and remote files in tabs in the Editor window,` and keep the rest of the line.

`AGENTS.md`: replace `Remote is bundled too (\`plugins/remote\`, bundling Apache MINA sshd); it consumes only the Vault API.` with `Remote is bundled too (\`plugins/remote\`, bundling Apache MINA sshd); it consumes only the Vault API and, optionally, the Editor API (\`dev.jasper.editor.api\`, named only in \`dev.jasper.remote.edit\`'s \`RemoteDocument\` and \`RemoteEditing\`).`

- [ ] **Step 5: `docs/STATUS.md` and this plan's banner**

In `docs/STATUS.md`, directly under `## Current state — 2026-09-23`, insert:

```markdown
- **Editor plugin, plan B** (branch `claude/editor-plugin-b`, plan
  `docs/superpowers/plans/2026-09-27-jasper-editor-plugin-b.md`): Remote 0.4.0 optionally requires the Editor.
  - Double-click or Enter in the SFTP browser opens a file, or a link to a file, in the Editor; **Open in Editor** opens up to 20 selected files.
  - Saving follows the write contract over SFTP (`dev.jasper.remote.edit.SafeWrite`):
    - an owner-only sibling created with its mode through a hand-built `SSH_FXP_OPEN`;
    - posix-rename, and writing in place otherwise;
    - read-only files refused.
  - Editor operations run one at a time per connection on one SFTP channel (`EditChannels`).
  - The spec's sections 4.4, 6.1, 6.2 and 6.4 carry the Plan B amendments.
  - Not done: GUI acceptance (items 12–19 of the [checklist](editor-verification.md), user-run), merge and push.
```

Replace this plan's `**Status:**` line with `**Status:** Executed <date> on branch claude/editor-plugin-b, <execution method>.` Then list any deviations from the task text as bullets, as Plan A's banner does.

- [ ] **Step 6: Run the full check and the hygiene check**

Run: `./gradlew check`
Expected: BUILD SUCCESSFUL. Read the totals from `*/build/test-results/test/*.xml` and `plugins/*/build/test-results/test/*.xml` and record them in `docs/STATUS.md`'s entry.

Run the source-hygiene script from `AGENTS.md`.
Expected: no output.

- [ ] **Step 7: Commit**

```bash
git add docs AGENTS.md
git commit -m "docs(remote): editing remote files, the Plan B checklist and status

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>"
```
