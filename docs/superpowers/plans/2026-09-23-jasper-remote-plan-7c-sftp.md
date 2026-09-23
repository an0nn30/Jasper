# Remote Plan 7c — SFTP Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans. The user explicitly requested an adversarial plan review followed by native implementation in this task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship single-sidebar SFTP browsing and durable, bounded, cancellable/resumable local/remote and remote/remote copies inside the existing Remote plugin.

**Architecture:** The SDK supplies host-owned named icons, owner-based file selection and a real status-progress contribution. Remote acquires owned leases on shared SSH sessions and adapts local/SFTP files behind one endpoint contract. A SQLite-backed transfer coordinator owns durable jobs independently of Swing views; browser and transfer views consume bounded snapshots.

**Tech Stack:** Java/JBR 25, Swing, SDK 0.7.5, Apache MINA sshd-sftp 2.19.0, Xerial sqlite-jdbc 3.53.4.0, JUnit/AssertJ, real temporary directories and loopback SFTP servers.

**Spec:** `docs/superpowers/specs/2026-09-23-jasper-remote-sftp-design.md` (approved, including section 9.3 named-icon correction).

**Execution status:** Independent adversarial review completed; eight findings are resolved in the contracts and acceptance tests below. No implementation task has started. Native execution is already selected and authorized after review findings are addressed. Baseline `1fcf9a8d`; design commits `68b7fb78`, `147a7629`.

## Global Constraints

- One bundled `dev.jasper.remote`; no separate SFTP plugin, no tunnels dependency, no Remote API exported to other plugins.
- SDK and testkit depend on the JDK only. Application SDK types stay in `dev.jasper.app.plugins`; workspace/windows receive native values. Remote imports SDK, Vault API and its bundled libraries only.
- Every icon is `Appearance.icon(IconName)`. New artwork lives in app resources, sourced from `/Users/dustin/projects/tabler-icons/icons/outline` and `/Users/dustin/Downloads/OldGNOME2`, with provenance/license records. No Remote image resources or custom icon painting.
- Use existing auth/trust/ProxyJump behavior, no external host tests, no GUI launch, no personal shell/config edits. Do not merge or push without a new user instruction.
- All file/network/SQL/hash work runs off EDT. Defaults: two concurrent copies, max eight; 8 MiB payload budget per copy; one scanner; UI samples at most 5 Hz; 64-bit offsets/counts.
- Temporary sibling files plus validated rename publication. No destructive overwrite fallback. Pause and restart retain progress; restored jobs stay paused. Cancel cleans only owned partials, completed files remain.
- SQLite lives in Remote data, FULL synchronous WAL, one process ownership lock, bounded queries; credentials/payloads never enter it.
- Ordinary source links are copied as links and never traversed by recursive operations. Unsupported special files become explicit issues. Remote paths never become local `Path` values.
- Test first per behavior, one commit per task ending with `Co-Authored-By: Codex <noreply@openai.com>`. Record rulings/test evidence in this plan's execution ledger and durable handoff.

## Review Focus

1. Crash after destination publication but before database completion: reconcile by content, never repeat an overwrite (Task 7).
2. A host's Vault login username or ProxyJump definition changes behind a saved id: old panes retain old leases, resumed jobs never silently change endpoints (Tasks 4, 7).
3. Directory entries contain traversal/case-colliding names or links to ancestors: no escape, infinite traversal or hidden replacement (Tasks 5, 7).
4. Pause/cancel while a request stalls or a window/prompt closes: writes stop, shared shells survive, incomplete cleanup is visible after restart (Tasks 4, 7, 9).
5. A 100,000-entry folder and multiple windows observing it: bounded scan/admission/pages/update rate, no unbounded futures or EDT I/O (Tasks 6–10).

## File and interface map

| Unit | Files / responsibility |
| --- | --- |
| Icon catalog | SDK `ui/IconName`, app `platform/NamedIcons`, `OldGnomeCatalog`, app resource manifests, catalog tests |
| Status progress | SDK `ui/StatusProgress`, `StatusProgressState`; native `contributions/ProgressState`; `StatusEntry`, `HostedUi`, `FakeUi`, `workspace/StatusProgressView` |
| Owner picker | SDK `Windows`; app `windows/PathChoice`, `PathChooser`, `AuxiliaryWindows`, `NativeShells`; `HostedUi`; fake selections |
| Session ownership | Remote `client/ConnectionIdentity`, `SessionLease`, `Connections`; shell's identity retained by `RemotePlugin` |
| Files | Remote `sftp/FileEndpoint`, `FileEntry`, `FileLocation`, `LocalEndpoint`, `SftpEndpoint`, `EndpointFactory` |
| Durable work | Remote `transfer/TransferState`, `TransferJob`, `TransferEntry`, `TransferRequest`, `ConflictDecision`; `transfer/store/TransferStore` |
| Engine | Remote `transfer/TransferCoordinator`, `TransferCopy`, `TransferScan`, `TransferRecovery`, `TransferControl` |
| Browser | Remote `ui/sftp/SftpController`, `SftpPanel`, `DirectoryCache`, `DestinationPanel`, `FileOperationController` |
| Queue UI | Remote `ui/transfers/TransfersPanel`, `TransferPresentation`, `TransferUi`; Remote settings/registration |

App and SDK types have independent values at the bridge. The store's SQL model is internal
to Remote. `FileEndpoint` has Local and SFTP implementations; do not introduce single-use
interfaces for stores/controllers. Views receive immutable records and callbacks.

### Task 1: Complete the host-owned file/transfer icon catalog

**Files:** Modify `jasper-sdk/src/main/java/dev/jasper/sdk/ui/IconName.java`,
`jasper-app/src/main/java/dev/jasper/app/platform/{NamedIcons,OldGnomeCatalog}.java`,
`jasper-app/src/main/resources/dev/jasper/app/icons/{standard,oldgnome-sdk}/` manifests/assets;
tests `platform/NamedIconsTest.java`, `plugins/HostedUiTest.java` and testkit named-icon contract.

**Interfaces:** Produce IconName FILE, LINK, UPLOAD, DOWNLOAD, UP, NEW_FOLDER, PAUSE, RESUME.
Existing FOLDER, COPY, DELETE, REFRESH, CLOSE and NETWORK remain unchanged.

- [ ] Add this assertion to the named catalog test before changing the enum/catalog:

```java
assertThat(NamedIcons.NAMES).contains("FILE", "LINK", "UPLOAD", "DOWNLOAD", "UP", "NEW_FOLDER", "PAUSE", "RESUME");
```

- [ ] Run `./gradlew :jasper-app:test --tests '*NamedIconsTest'`; expected missing-name failure.
- [ ] Add semantic names and exact host mappings. Modern files: `file.svg`, `link.svg`,
  `upload.svg`, `download.svg`, `arrow-up.svg`, `folder-plus.svg`, `player-pause.svg`,
  `player-play.svg`. Retro mappings use existing file/link, arrow-up/down, new-folder and
  media pause/play art in the supplied tree; verify available source sizes before copying.
  Copy bytes unchanged; record source paths and SHA-256 in the existing asset manifests.
  Preserve existing generic catalogs; new IconName meanings must resolve in both styles.

```java
// Each new mapping follows the existing host-owned routing, never a plugin path.
Map.entry("FILE", "dev/jasper/app/icons/standard/FILE.svg")
```

- [ ] Run named-icon, hosted-icon and testkit tests; assert all IconName values render 16px
  compact and 28px retro-toolbar variants with a resource-empty plugin loader. Remove the
  old fixed count of 22 from catalog tests; compare catalog names to supported semantic names.
- [ ] Commit `feat(sdk): add file and transfer semantic icons`.

### Task 2: Add the generic status-progress contract and stable host rendering

**Files:** Create SDK `ui/StatusProgressState.java`, `ui/StatusProgress.java`; modify
`StatusBar.java`, `JasperSdk.java`; create native `contributions/ProgressState.java` and
`workspace/StatusProgressView.java`; modify `StatusEntry`, `HostedUi`, `WindowStatusBar`,
`FakeUi`, `FakePluginHost`; add SDK/app/testkit progress tests.

**Interfaces:**

```java
public record StatusProgressState(String text, String detail, String accessibleDescription, OptionalDouble fraction,
                                  String actionId, String secondaryActionId) { }
public interface StatusProgress extends Subscription {
    void update(StatusProgressState state);
    void setVisible(boolean visible);
}
// StatusBar keeps its one abstract method for existing lambda hosts.
default StatusProgress addProgress(StatusItemSpec spec) { throw new UnsupportedOperationException("Progress is unavailable"); }
```

- [ ] Write tests rejecting NaN/out-of-range fractions, foreign actions, duplicate status ids,
  wrong-thread calls and stale updates; allow absent fraction and optional secondary action.
  Add a workspace regression holding a progress component reference across two updates:

```java
var first = status.getComponents();
entry.setProgress(new ProgressState("Copying", "20 MiB left", .5, "open", "cancel"));
status.setContributed(List.of(entry), actions);
assertThat(findProgress(status)).isSameAs(findProgress(first));
```

- [ ] Run `./gradlew :jasper-sdk:test :jasper-sdk-testkit:test :jasper-app:test --tests '*Progress*'`;
  expect missing contract/behavior failures. Use a test-only component finder; no production test hooks.
- [ ] Implement record validation, host-owned progress handle, native translation and fake
  recording. Represent indeterminate native progress with an absent fraction too. Reuse
  views keyed by entry id; action bindings resolve current actions, with independent main
  and secondary clicks. Update value/text in place, rebuild structural layout only on actual
  entry visibility/order changes. Keep text-only item behavior and `contributedItems` tests.
- [ ] Run affected SDK/app/testkit tests and doclint. Headless render at narrow widths and
  UI font 12/18/32, both skins; ensure bounded controls and accessible progress description.
- [ ] Set SDK version 0.7.5, document the API and commit `feat(sdk): support status bar progress controls`.

### Task 3: Add owner-based file and folder pickers

**Files:** Modify SDK `Windows.java`; create app-native `windows/PathChoice.java`,
`windows/PathChooser.java`; modify `AuxiliaryWindows`, `NativeShells`, application wiring,
`HostedUi`, `FakeUi`, `FakePluginHost`; add host/native-model/testkit chooser tests.

**Interfaces:**

```java
default List<Path> chooseFiles(WindowOwner owner, String title, Optional<Path> initial) {
    throw new UnsupportedOperationException("File selection is unavailable");
}
default Optional<Path> chooseDirectory(WindowOwner owner, String title, Optional<Path> initial) {
    throw new UnsupportedOperationException("Directory selection is unavailable");
}
// App-native request uses UUID terminal owner or AuxiliarySurface auxiliary owner; never SDK.
public record PathChoice(UUID terminalOwner, AuxiliarySurface auxiliaryOwner, String title,
                         Optional<Path> initial, boolean directory) { }
```

- [ ] Add tests for two selected files, directory selection, cancel, stale owner during the
  pumped chooser, foreign/dead window rejection and plugin stop during selection.

```java
host.queuePathSelection(List.of(root.resolve("a"), root.resolve("b")));
assertThat(context.windows().chooseFiles(window, "Upload", Optional.of(root)))
    .containsExactly(root.resolve("a"), root.resolve("b"));
```

- [ ] Run `./gradlew :jasper-sdk-testkit:test :jasper-app:test --tests '*Chooser*'`; expect unavailable chooser.
- [ ] Implement owner validation before and after the synchronous event-pumping call.
  Provide a native Files picker with multiple selection and a themed directory chooser;
  no global system-property toggles. Inject `PathChooser` at AuxiliaryWindows construction
  (production native and headless/fake are two implementations). Dispose on owner close and
  plugin stop; empty results mean cancel. Normalize paths, do not read file contents.
- [ ] Run chooser and existing auxiliary-window/overlay tests, SDK doclint/examples. Keep
  `WindowSurface.chooseFile` unchanged and working.
- [ ] Commit `feat(sdk): select local files and directories from plugin panels`.

### Task 4: Extract cancellable SSH session leases with endpoint identity

**Files:** Create Remote `client/ConnectionIdentity.java`, `client/SessionLease.java`;
modify `Connections.java`, `RemotePlugin.java`, Vault `api/VaultApi.java` and
`service/VaultService.java`; extend `ConnectionsTest.java` and loopback fixture.

**Interfaces:** `ConnectionIdentity` is an immutable snapshot of the host plus resolved jump
chain; equality excludes label/group/favorite/timestamps and includes address, port, username
and authentication references. `SessionLease` exposes actual host/identity/session to Remote
internals and idempotent `close()`. `Connections.identity(UUID)` resolves a snapshot (including effective Vault usernames for
all hops); `lease(ConnectionIdentity, WindowHandle, Consumer<String>)` returns a cancellable
`CompletableFuture<SessionLease>` on the UI executor. Shell creation composes it. Live pane
operations use their acquired identity; durable resume compares that snapshot with the current
saved definition before acquiring. Resolve credential usernames before deciding reuse, including
jump credentials. Add compatible `VaultApi.credential(WindowHandle, UUID)` and honor the owner
in VaultService. Shared acquisitions track individual waiters; a cancelled/dead prompt owner
cannot choose the active window implicitly or cancel another surviving waiter's lease.

- [ ] Add loopback tests acquiring two leases and one shell; close/cancel each independently.
  Edit host address/jump/username while old resources are live and acquire the new identity.

```java
SessionLease first = await(connections.lease(identity, window, ignored -> {}));
SessionLease second = await(connections.lease(identity, window, ignored -> {}));
assertThat(second.session()).isSameAs(first.session());
first.close();
assertThat(second.session().isOpen()).isTrue();
second.close();
```

- [ ] Run `./gradlew :jasper-plugin-remote:test --tests '*ConnectionsTest'`; expected missing lease API.
- [ ] Factor acquire/release ownership into leases without moving registry state off UI.
  Key sharing by connection-defining snapshot so old/new endpoints may coexist. Count only
  shells in SSH status. On late acquisition after cancellation close that lease once. Leases
  held by a transfer keep ProxyJump references alive; no foreign worker closes the whole client.
  Attach actual identity to Shell and retain it with pane associations; preserve host-id view APIs.
- [ ] Run all Remote tests, including real loopback shared auth, cancellation, host probes and
  linger, SFTP through ProxyJump, two-window acquisition with one cancelled/dead owner,
  and edited address/user/jump/Vault usernames. Prompts use captured live request owners.
- [ ] Commit `refactor(remote): share owned SSH leases with file operations`.

### Task 5: Implement local and SFTP file endpoints

**Files:** Add `sshd-sftp:2.19.0` to Remote build; create `sftp/{FileEndpoint,FileEntry,
FileLocation,LocalEndpoint,SftpEndpoint,EndpointFactory}.java`; add loopback SFTP server fixture
and `FileEndpointTest`, `SftpEndpointTest`.

**Interfaces:**

```java
public record FileEntry(String name, Kind kind, long size, long modifiedMillis, int permissions, String linkTarget) {
    public enum Kind { FILE, DIRECTORY, LINK, SPECIAL }
}
public record FileLocation(String endpointId, String path) { }
public interface FileEndpoint extends AutoCloseable {
    FileEntry stat(String path) throws IOException; // lstat semantics
    void list(String directory, Consumer<FileEntry> visitor) throws IOException;
    InputStream read(String path, long offset) throws IOException;
    WriteHandle write(String path, long offset, boolean createExclusive) throws IOException;
    void truncate(String path, long size) throws IOException;
    void mkdir(String path) throws IOException;
    void symlink(String path, String target) throws IOException;
    void remove(String path, boolean directory) throws IOException;
    void publish(String temporary, String target, boolean replace) throws IOException;
    void metadata(String path, long modifiedMillis, int ordinaryPermissions) throws IOException;
    String canonical(String path) throws IOException;
    String home() throws IOException;
    void abort(); // independent, nonwaiting; owned channel only
    void close() throws IOException;
}
public abstract class WriteHandle extends OutputStream {
    public abstract long checkpoint() throws IOException; // absolute contiguous confirmed offset
    public abstract void abort(); // thread-safe, independent of a blocked writer
}
```

- [ ] Write the same contract against a temporary local root and real loopback SFTP root:
  binary round trip from nonzero offset, no overwrite on exclusive create/publication,
  symlinks lstat, incremental directory listing, unsupported special types and permissions.

```java
try (var out = endpoint.write(temp, 0, true)) { out.write(new byte[]{0, 1, 2, 3}); }
try (var in = endpoint.read(temp, 2)) { assertThat(in.readAllBytes()).containsExactly(2, 3); }
assertThatThrownBy(() -> endpoint.write(temp, 0, true)).isInstanceOf(IOException.class);
```

- [ ] Run endpoint tests; expect missing implementation.
- [ ] Implement adapters. SFTP gets one subsystem per worker lease, offset streams with
  bounded pipeline and request timeout. Local uses channels/NOFOLLOW_LINKS; metadata rwx
  mask 0777. No shell commands. Implement bounded SFTP WRITE requests via RawSftpClient:
  track request ids/offsets, require successful STATUS for every write through each barrier.
  Null/timeout/non-OK responses fail the barrier. Neither stream flush nor MINA async-stream
  close proves acknowledgement (close can silently stop waiting on an ACK timeout).
  Local checkpoint forces the channel. Abort force-closes only the owned subsystem using
  close(true), without needing a writer lock. Close drains and validates pending acknowledgements. Publication is no-replace or atomic replace only, verified
  against endpoint capabilities. Local no-replace uses same-filesystem hard-link publication
  where supported then unlinks owned temp; never assumes ATOMIC_MOVE means no-replace.
  Directory listings ignore protocol dot entries and reject traversal names before consumer use.
- [ ] Run contracts for SFTP v3/POSIX rename support and an endpoint rejecting atomic replace.
  Add chunked large-offset tests (>Integer.MAX_VALUE), backpressure and channel-close interruption.
  Withhold a WRITE STATUS but acknowledge CLOSE: checkpoint must fail and no confirmed offset
  may advance. Test dangling links and destination intermediate symlinks; use handle-relative
  local operations where supported and revalidate ancestry for every mutation.
- [ ] Commit `feat(remote): add bounded local and SFTP file operations`.

### Task 6: Add durable job storage and bounded discovery records

**Files:** Add `sqlite-jdbc:3.53.4.0`; create Remote `transfer/{TransferState,TransferJob,
TransferEntry,TransferRequest,ConflictDecision}.java`, `transfer/store/TransferStore.java`;
tests `TransferStoreTest`, `QueueProcessProbe` (test source only).

**Interfaces:** Store is synchronous worker-only, one owner. `TransferRequest` captures source
endpoint snapshot, selected absolute paths, destination snapshot/directory. `create(request)`
returns UUID; `jobs(offset,limit)`/`entries(job,offset,limit)` return immutable pages. Update
methods perform transactions; `close()` releases DB and lifetime FileLock. Job and entry byte
counts are long. States include durable pause/cancel intent and publishing. EndpointRef stores
optional saved host id, length-prefixed nonsecret connection snapshot and effective username;
TransferRequest stores source/destination EndpointRefs and absolute selected paths/directory.
Do not use Java serialization. Entry phases are PLANNED_TEMP, CREATED_TEMP, COPYING,
PUBLISHING, COMPLETE and CLEANUP. Persist creation evidence only after exclusive creation;
a planned random path is never ownership evidence. An ambiguous create/crash requires attention.

- [ ] Write reopen, crash, duplicate-discovery and multi-process lock tests with the real DB.

```java
UUID id;
try (var db = new TransferStore(root)) { id = db.create(request); db.markRunning(id); }
try (var db = new TransferStore(root)) {
    assertThat(db.job(id).state()).isEqualTo(TransferState.PAUSED);
    assertThat(db.jobs(0, 50)).hasSize(1);
}
```

- [ ] Run store tests; expected absent schema/store.
- [ ] Implement schema version 1 with jobs, entries, scan_frontier, checkpoints, cleanup.
  Unique `(job_id,relative_path)` makes directory replay idempotent. Entry stores original
  source metadata, temp/final paths, confirmed offset, digest segments, decision and outcome.
  Publishing intent includes full-file digest and expected target state. No payload/secret fields.

```sql
PRAGMA journal_mode=WAL;
PRAGMA synchronous=FULL;
PRAGMA foreign_keys=ON;
PRAGMA busy_timeout=2000;
PRAGMA cache_size=-4096;
```

  Acquire queue.lock before opening/migrating; use a directly instantiated driver connection,
  not global DriverManager discovery. Close/deregister any driver registration on teardown.
  Startup changes active states to paused except cancellation intent; preserve diagnostics.
  Corrupt/newer schema refuses mutation without deleting data. Batch writes and indexed pages
  bound memory; never load all checkpoints or all entries at once.
- [ ] Test 100,000 synthetic entries queried in pages and killed writer recovery; driver through
  the real staged plugin loader; no silent queue reset and no second-process ownership.
- [ ] Commit `feat(remote): persist transfer jobs and restart checkpoints`.

### Task 7: Implement transfer scheduling, scanning, copy and recovery

**Files:** Create `transfer/{TransferCoordinator,TransferScan,TransferCopy,TransferRecovery,
TransferControl,PathReservations}.java`; tests `TransferCoordinatorTest`, `TransferRecoveryTest`,
`TransferScaleTest` and generated/delayed endpoint fixtures.

**Interfaces:** Coordinator consumes store, endpoint factory, background executor and UI
publisher. `enqueue(request)`, `pause(UUID)`, `resume(UUID)`, `cancel(UUID)`, `retry(UUID)`,
`resolve(entryId,decision)` and `cleanup(UUID)` enqueue commands; `snapshot(offset,limit)`
publishes bounded immutable rows asynchronously. Closing stops admission and drains owned work.

- [ ] Write end-to-end tests for files/folders in all three directions, pause/restart/resume,
  source prefix edits, acknowledged offsets, conflict decisions, cleanup failure, queued
  cancellation and crash around publication. Reuse a test Harness with real store/endpoints;
  fake only delays/fault boundaries, not filesystem results.

```java
var id = harness.enqueue(source, List.of("/large.bin"), destination, "/target");
harness.awaitBytes(id, 8L * 1024 * 1024);
harness.pause(id);
harness.awaitState(id, TransferState.PAUSED);
harness.restart();
assertThat(harness.state(id)).isEqualTo(TransferState.PAUSED);
harness.resume(id);
harness.awaitState(id, TransferState.COMPLETED);
assertThat(harness.digest(destination, "/target/large.bin")).isEqualTo(harness.digest(source, "/large.bin"));
```

- [ ] Run transfer tests; expect absent coordinator/recovery behavior.
- [ ] Implement bounded scheduling (2 default/8 cap, one scanner, fair job turns), durable
  discovery frontier, and confirmed 8-MiB segment checkpoints. No future per file. Workers
  own endpoint channels/leases and check the control token between reads/writes/discovery.
  Use separate control/store execution from payload slots so cancellation cannot queue behind
  blocked copies. The mutation protocol is:

```text
persist planned temp -> exclusive create -> persist creation evidence -> bounded copy
-> explicit acknowledged checkpoint -> source restat -> close/ack destination
-> metadata -> persist publishing intent
-> capability-safe rename -> persist completed
```

  Hash source segments incrementally while copying; explicit checkpoint barriers at segment boundaries force local
  partials, persist only confirmed contiguous offsets. Resume validates endpoint and all
  checkpointed source/temp prefix digests in pages, truncates uncheckpointed tail, resumes
  at offset. Source/partial changes require Restart/Skip. Interrupted publication compares
  stored digest with final/temp and records completion only on unambiguous match.
  Cancel preserves completed files and cleans only verified temp ownership; recovery of
  cancellation never restarts copying. Preserve directory structure/empty dirs; apply directory
  metadata after children. Links copy as links, special files record issues. Validate path
  components, duplicate/case collisions and source-subtree destination before enqueue/dispatch.
  Capture decisions per target state and revalidate before publish. Directory Merge is not delete.
  Apply the reservation, ownership, publication-recovery and resource protocols below.
  Add crash/race tests for preexisting opaque temp names, swapped partials, before/after exclusive
  creation, hard-link publication before unlink, rename before DB completion, same-target jobs,
  alias endpoints and deletion of active source/temp/destination ancestors. Symlink copies use
  exclusive temporary links and lstat/link-text evidence, never referent hashes or following chmod.
  Revalidate directory kind before recursive traversal and destination ancestry before mutations;
  directory-to-link swaps must stop rather than escape. Remote concurrent writers cannot be
  fully excluded by SFTP; changes that cannot be proved safe require attention.
- [ ] Run transfer/recovery tests and scale diagnostics in bounded heap, with >4-GiB generated
  streams and 100k entries. Assert bounded workers/pages/buffers and responsive control rather
  than absolute Mbps. Crash process at every publication checkpoint and verify originals survive.
- [ ] Commit `feat(remote): stream and resume durable transfer jobs`.

### Task 8: Build the single SFTP browser and remote destination picker

**Files:** Create `ui/sftp/{SftpPanel,SftpController,DirectoryCache,DestinationPanel,
FileOperationController}.java`; modify `HostsPanel` action wiring and Remote pane association;
tests `SftpPanelTest`, `SftpControllerTest`, `FileOperationTest`.

**Interfaces:** Controller receives window, context, endpoint factory, transfer coordinator,
host lookup and active-pane identity/path provider. `open(host,path)`, `follow(pane,path)`,
`visible(boolean)` and `close()` own generation/lease lifetime. DirectoryCache is a separate
temporary SQLite spool with indexed sort/pages. Views have no SQL/network access.

- [ ] Write headless tests for selection/Enter, fixed picker source/destination, late stale
  directory responses, pane/window switching, manual navigation disabling follow, local pane
  retaining last remote, hidden-view release and generation checks after close.

```java
controller.open(hostA, "/a");
controller.open(hostB, "/b");
fixture.completeListing(hostB, "/b", List.of("new"));
fixture.completeListing(hostA, "/a", List.of("old"));
assertThat(panel.names()).containsExactly("new");
```

- [ ] Run browser tests; expected absent UI.
- [ ] Build compact JTable/JList with native selection, SDK-provided icons injected through
  controller, path bar and toolbar. Use owner-based upload/download choosers. Add Upload Folder
  and Copy to host menu; destination dialog contains only one destination browser plus source
  summary. New-folder and confirmed delete run on owned background operations with cancellation.
  Preserve browser state per pane; directory events never change endpoint. No automatic panel
  opening on SSH connect. Following uses reported remote path only, no injected shell commands.
- [ ] Run browser tests in both skins/large UI font, including 100k cached directory entries,
  unsupported local names, literal `<html>` filenames, deletion link loops and cancellation. Verify clipboard paths do not
  inject commands and no plugin-local icon assets were added.
- [ ] Commit `feat(remote): browse remote files in a single sidebar`.

### Task 9: Integrate the shared transfer queue, status controls and lifecycle

**Files:** Create `ui/transfers/{TransfersPanel,TransferPresentation,TransferUi}.java`;
modify `RemotePlugin`, `RemoteSettings`, `RemoteShortcuts`, plugin descriptor/settings;
tests `TransfersPanelTest`, `SftpPluginTest`, existing staged-plugin assertions.

**Interfaces:** TransferUi owns per-window views over one coordinator and one status-progress
handle. `show(window)`, `toggle(window)`, `refresh(snapshot)` and `close()` use SDK only.
Register actions `dev.jasper.remote.sftp`, `.sftp.toggle`, `.transfers`, `.transfers.toggle`,
and `.transfers.cancel`; panels `.sftp.panel` and `.transfers.panel`. Existing actions retained.

- [ ] Write real plugin/testkit tests for contributions, visibility, queue across windows,
  no Buddy dependency, single-job cancel vs multi-job show queue, paused startup without
  authentication, pane close while transfer runs, and settings/keybinding reload.

```java
assertThat(host.panels()).anySatisfy(p -> assertThat(p).contains("SFTP"));
assertThat(host.menu("top:dev.jasper.remote.menu")).contains("item:dev.jasper.remote.transfers");
assertThat(fixture.authRequests()).isZero(); // restored paused queue
```

- [ ] Run plugin/UI tests; expect missing contributions.
- [ ] Implement paged table of jobs and child rows/details, per-job Pause/Resume/Cancel/Retry,
  conflict decisions and cleanup retry. Show completed-with-issues counts; Clear only discards
  safe history, with acknowledgement for pending cleanup. Publish aggregate status at 200ms
  cadence and immediately on transitions, preserving unknown totals and 64-bit remaining counts.
  Use SDK secondary cancel action. Wire shell/window events with captured identities, current
  host lookup and prompt ownership; serialization prevents stacked modal auth dialogs.
  Implement `[sftp] max_parallel_files=2, request_timeout_seconds=30`, both validated, and
  blank-by-default shortcut keys; global app keybindings retain precedence.
- [ ] Run all Remote tests and affected app staged-runtime tests. Verify plugin stop closes
  browsers/operations, checkpoints intents, stops workers before store close and releases owned
  sessions, without EDT waits. Start long-lived control/store loops during plugin start;
  stop synchronously signals those already-admitted loops and never submits work after context
  teardown. Suppress late UI callbacks. Exercise real HostedContext/staged plugin teardown during
  blocked copy, checkpoint and publication; require durable intent and queue lock reacquisition.
  Unavailable store shows error but ordinary SSH survives.
- [ ] Commit `feat(remote): manage SFTP transfers independently of Buddy`.

### Task 10: Integrated acceptance, documentation and independent final review

**Files:** Update `docs/remote.md`, SDK authoring/architecture/README, `docs/STATUS.md`;
create `docs/remote-7c-verification.md`; strengthen lifecycle/fault/scale tests as needed.

**Interfaces:** No new production surface; prove the spec through real composed flows.

- [ ] Run `./gradlew check :jasper-app:installDist` and count actual JUnit XML results.
  Run headless large-file/many-file/crash-process checks. Verify distribution contains the
  exact SFTP and SQLite jars and SDK floor, no Remote icon assets.
- [ ] Document settings, UI entry points, single-browser workflows, recovery/cleanup semantics,
  resume-validation cost, native picker behavior, unsupported metadata and runtime limitations.
  Include user-run native acceptance steps; do not launch the user's GUI or contact saved hosts.
- [ ] Generate final review package against `1fcf9a8d` and dispatch one fresh adversarial reviewer
  with spec, plan, ledger, changed files and Review Focus. Fix important findings with regression
  tests and re-run the full suite. Record justified rulings and any deferred polish explicitly.
- [ ] Commit final docs/fixes, mark all completed tasks and record verification evidence.
  Leave the worktree ready for user acceptance; no merge or push is implied by implementation.

## Plan review and execution record

Fresh reviewer `/root/sftp_plan_review` identified eight important issues: ACK proof, endpoint
identity/prompt ownership, temp ownership, path reservations, link/ancestor safety, publication
recovery, explicit resource budgets and real host shutdown ordering. All are accepted; the
contracts above and protocols below resolve them and name their negative tests. No scope was
removed. Baseline check: 1,765 tests, 1,762 passed, three skips, no failures/errors.

### Required mutation and recovery protocols (Tasks 5–9)

- Reservations are coordinator-owned and admitted atomically without filesystem I/O while
  holding the registry lock. Canonical endpoint/path keys use authenticated server identity and
  account rather than saved UUID, so known aliases converge. Read sources share reservations;
  destination subtrees, publication, cleanup and deletion require exclusive reservations.
  Ancestor/descendant overlap conflicts; acquire all keys in stable order or wait, release on
  every exit. Filesystem changes outside Jasper still require revalidation before dispatch.
- Persist source type/size/mtime and temp creation-success evidence (local file key where
  available, lstat metadata and checkpoint digests otherwise). No failed exclusive creation may
  authorize cleanup. Revalidate evidence before truncate/restart/remove. Ambiguous pre-checkpoint
  crash or replaced partial is Needs attention, not automatic deletion. Links use lstat and exact
  link text and never follow metadata operations. Refuse unsafe intermediate link traversal.
- Publishing persists method, expected target state, complete source digest/link text and temp
  evidence before changing names. Recovery reconciles publication before resume or cancellation:

| Observed after publishing intent | Recovery |
| --- | --- |
| Verified final, no temp | Complete; never repeat overwrite |
| Verified final and known temp (hard-link method, same file key) | Complete; unlink only known temp; never truncate either name |
| Verified final and known temp with equivalent digest/link text | Complete; remove only independently verified owned temp |
| No final, intact known temp, target was absent | Revalidate reservation/ancestry and retry no-replace publication |
| Original target intact, known temp, replace not performed | Needs attention; obtain/revalidate replacement decision before retry |
| Unrelated/changed final, missing/ambiguous temp or conflicting evidence | Needs attention; no inferred completion, overwrite or cleanup |

Fault-inject before/after hard-link create/unlink, replacement rename and completion commit.
External final appearance must never be overwritten under a no-replace decision.

### Concrete resource and shutdown bounds (Tasks 5–10)

- Global copying defaults to two, maximum eight; maximum two per endpoint; one scanner.
  Per-copy payload budget is 8 MiB total: at most 2 MiB read-ahead, 2 MiB outstanding writes,
  256 KiB copy buffer, leaving room for transport buffers. Set SFTP channel windows explicitly;
  at most sixteen pending 128-KiB writes and sixteen reads. Hash each 8-MiB segment incrementally.
- SQL batches and listing pages at most 256 entries; UI pages at most 200; one snapshot request
  per view in flight. Control mailbox at most 256 normal commands with explicit busy feedback;
  pause/cancel/stop intents coalesce per admitted job and bypass payload admission. Progress
  samples replace previous samples and emit at most five times per second. Never enqueue one
  future per discovered entry or observer tick. Measure these bounds under delayed ACKs,
  endpoint contention, many views and 100,000 entries.
- Control/store loops are admitted while HostedContext is live and own their shutdown finally
  blocks. Plugin.stop signals stop synchronously, closes UI admission and requests endpoint
  aborts; the already-running loops drain durable pause/cancel intents, join owned workers and
  close store/lock within the host grace period without new context executor submissions.
  Test using actual HostedContext teardown, not only a fake executor.
