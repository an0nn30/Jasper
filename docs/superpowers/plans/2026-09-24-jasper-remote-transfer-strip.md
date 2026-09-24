# Remote transfer strip — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the bottom Transfers panel with a compact transfer strip at the bottom of the SFTP sidebar, and ask once before copying onto existing items.

**Architecture:** The queue (`TransferCoordinator`, `TransferStore`) is unchanged except that a request can carry the user's "Replace / Skip existing" choice, stored in the job's existing file/folder policy columns. Pure helpers turn job snapshots into row text (`TransferRows`, `JobSpeeds`, `FinishedJobs`); a Swing `TransferStrip` renders them; `TransferUi` feeds every window's strip, owns the status item and the "Done" fade, and no longer registers a panel. `SftpUi` embeds a strip in each `SftpPanel` and runs the ask-before-copying check.

**Tech Stack:** Java 25 (JBR), Swing, Apache MINA sshd/SFTP (tests), SQLite queue (existing), JUnit 5, AssertJ, SDK testkit `FakePluginHost`.

**Spec:** [`docs/superpowers/specs/2026-09-24-jasper-remote-transfer-strip-design.md`](../specs/2026-09-24-jasper-remote-transfer-strip-design.md) (amends §3 of the [SFTP design](../specs/2026-09-23-jasper-remote-sftp-design.md)).

**Branch:** `codex/remote-sftp`.

## Global Constraints

- Strip: below the file list and Follow terminal folder; hidden when nothing is queued; at most three transfers visible, then it scrolls; newest first; every window shows the same queue.
- Row line 1: arrow `↑` (to a host), `↓` (to this machine), `⇄` (host to host); item name, or "*name* and N more", or "*folder* (N items)"; `→`; destination `host:directory` (local: the directory); elided in the middle; full source and destination in tooltip and accessible description. Then `×`.
- Row line 2: thin progress bar and one status: copying `58% · 4.1 MiB/s · 12 s left`; `Scanning… N items found` (indeterminate); `Queued`, `Checking partial file…`, `Pausing…`, `Paused`, `Cancelling…`, `Interrupted — <reason>`, `Needs attention — <reason>`, `Done`, `Done · N failed · M skipped`, `Cancelled`, `Failed — <reason>`.
- One action at most: Paused/Interrupted/Failed → **Resume**; Needs attention → **Resolve…**; Completed with issues with failures → **Retry failed**; cleanup pending on a finished transfer → **Retry cleanup** (takes precedence). `×` cancels an unfinished transfer and dismisses a finished one; dismissing with cleanup pending asks first.
- A clean finish shows `Done` for 5 seconds, then its history is cleared (never destination files). Clean = Completed, or Completed with issues whose only issues are skipped items, with no cleanup pending. Everything else stays until dismissed.
- Another instance owns the queue: the strip shows the one error line (e.g. "Transfers are managed by another Jasper instance").
- Ask before copying: one stat per selected top-level item in the destination directory, off the UI thread. None exist → queue as before. Any exist → dialog "*N of M items already exist in X.*" / `"name" already exists in X.` with **Replace**, **Skip existing**, **Cancel**. Replace = replace files, merge folders; Skip existing = skip existing files, merge folders; recorded as the job's policies before any work is admitted.
- Status-bar item unchanged except its click shows the SFTP sidebar. The `dev.jasper.remote.transfers` action stays and shows the SFTP sidebar. Removed: the `dev.jasper.remote.transfers.panel` panel, the `dev.jasper.remote.transfers.toggle` action and its View-menu entry, the `toggle_transfers` shortcut (a leftover key is ignored), `TransfersPanel`.
- No Pause button, no per-file table, no paging in the UI. The queue semantics, durability and recovery do not change.
- Plugin code uses the SDK only; no raw control/private-use characters in source (arrows, `…`, `×` and `·` are ordinary characters and are allowed). Tests are headless; never launch the GUI.
- Commit messages end with:
  ```
  🤖 Generated with Claude Code at The Home Depot

  Co-Authored-By: Claude <noreply@anthropic.com>
  ```

## Review Focus

1. **Copying onto a folder that already exists with Replace.** The person expects the folder merged and files inside replaced without further questions (Task 1 `anExistingItemsChoiceCoversConflictsInsideMergedFolders`).
2. **Skip existing leaving "Done · N skipped" on screen forever.** A deliberate skip is not a problem; the person expects it to fade like a clean finish (Task 2 `skipOnlyFinishFadesButFailuresStay`).
3. **Long names on a narrow sidebar.** The person expects to still see where it is going; middle elision keeps both ends (Task 3 `longTitlesAreElidedInTheMiddleWithTheFullTextInTheTooltip`).
4. **Old history from the previous panel.** Completed jobs already in the queue must fade on first sight and failed/cancelled ones must be dismissable, not stuck. Both go through the same fade and × paths as new jobs (Task 2 `skipOnlyFinishFadesButFailuresStay`, Task 3 `theActionButtonAndCloseDoTheRightThing`, Task 4 `theStripShowsACopyLetsItFadeAndTheShowActionRevealsTheSidebar`).
5. **The destination check failing** (host unreachable, permission denied on stat). The person expects an error notice and nothing queued, not a silent upload: `ExistingItems` propagates every error except "missing" (Task 1 `otherStatFailuresPropagate`), and `SftpUi.check` reports it through `context.notices().error` without enqueueing (Task 5 Step 3).

---

## File Structure

| File | Responsibility |
| --- | --- |
| `plugins/remote/src/main/java/dev/jasper/remote/transfer/TransferRequest.java` (modify) | `existing` choice + `withExisting` |
| `plugins/remote/src/main/java/dev/jasper/remote/transfer/store/TransferStore.java` (modify) | record the choice as job policies at creation |
| `plugins/remote/src/main/java/dev/jasper/remote/transfer/TransferCoordinator.java` (modify) | `request(UUID)` lookup |
| `plugins/remote/src/main/java/dev/jasper/remote/transfer/ExistingItems.java` (create) | which selected names already exist at the destination |
| `plugins/remote/src/main/java/dev/jasper/remote/ui/transfers/TransferRows.java` (create) | pure row text/action from a job |
| `plugins/remote/src/main/java/dev/jasper/remote/ui/transfers/JobSpeeds.java` (create) | per-transfer smoothed speed |
| `plugins/remote/src/main/java/dev/jasper/remote/ui/transfers/FinishedJobs.java` (create) | which clean finishes have shown "Done" long enough |
| `plugins/remote/src/main/java/dev/jasper/remote/ui/transfers/TransferStrip.java` (create) | the strip component |
| `plugins/remote/src/main/java/dev/jasper/remote/ui/transfers/ResolvePanel.java` (create) | the Resolve… dialog content |
| `plugins/remote/src/main/java/dev/jasper/remote/ui/transfers/ExistingItemsPanel.java` (create) | the ask-before-copying dialog content |
| `plugins/remote/src/main/java/dev/jasper/remote/ui/transfers/TransferUi.java` (rewrite) | status item, strips feed, fade, actions |
| `plugins/remote/src/main/java/dev/jasper/remote/ui/transfers/TransfersPanel.java` (delete) | — |
| `plugins/remote/src/main/java/dev/jasper/remote/ui/sftp/SftpPanel.java`, `SftpUi.java` (modify) | strip slot; embedding; ask-before-copying |
| `plugins/remote/src/main/java/dev/jasper/remote/RemotePlugin.java`, `RemoteShortcuts.java`, `src/main/resources/settings.toml` (modify) | wiring and removals |
| Tests per task; `docs/remote.md`, `docs/remote-7c-verification.md`, `docs/STATUS.md` | docs |

Run tests with `./gradlew :jasper-plugin-remote:test --tests '<class>'` from the repository root. Swing tests that create controllers must close them in `finally` (a failing assertion before `close()` otherwise hangs the run).

---

### Task 1: The queue records "Replace / Skip existing"

**Files:**
- Modify: `plugins/remote/src/main/java/dev/jasper/remote/transfer/TransferRequest.java`
- Modify: `plugins/remote/src/main/java/dev/jasper/remote/transfer/store/TransferStore.java:81-85` (`create`)
- Modify: `plugins/remote/src/main/java/dev/jasper/remote/transfer/TransferCoordinator.java` (after `entries(...)`, line ~106)
- Create: `plugins/remote/src/main/java/dev/jasper/remote/transfer/ExistingItems.java`
- Test: `plugins/remote/src/test/java/dev/jasper/remote/transfer/TransferCoordinatorTest.java`, `plugins/remote/src/test/java/dev/jasper/remote/transfer/store/TransferStoreTest.java`, create `plugins/remote/src/test/java/dev/jasper/remote/transfer/ExistingItemsTest.java`

**Interfaces:**
- Produces:
  - `TransferRequest(EndpointRef source, List<String> paths, EndpointRef destination, String directory, ConflictDecision existing)`; `existing` ∈ {`ASK`, `REPLACE`, `SKIP`}; the 4-argument constructor means `ASK`; `TransferRequest withExisting(ConflictDecision choice)`.
  - `TransferCoordinator.request(UUID id) -> CompletableFuture<TransferRequest>`.
  - `ExistingItems.existing(FileEndpoint destination, String directory, List<String> names) throws IOException -> List<String>` (names that exist; `NoSuchFileException` means absent; other errors propagate).
- The choice is stored in the job's `file_policy` / `directory_policy` columns (REPLACE→REPLACE/MERGE, SKIP→SKIP/MERGE). `TransferCodec` is unchanged; a decoded request reads back as `ASK`, which is correct because the policy lives on the job.

- [ ] **Step 1: Write the failing tests**

Add to `TransferStoreTest`:

```java
    @Test void anExistingItemsChoiceBecomesTheJobsPolicies() throws Exception {
        try (var db = new TransferStore(root)) {
            UUID asked = db.create(request());
            assertThat(db.policy(asked, false)).isEqualTo(ConflictDecision.ASK);
            assertThat(db.policy(asked, true)).isEqualTo(ConflictDecision.ASK);
            UUID replace = db.create(request().withExisting(ConflictDecision.REPLACE));
            assertThat(db.policy(replace, false)).isEqualTo(ConflictDecision.REPLACE);
            assertThat(db.policy(replace, true)).isEqualTo(ConflictDecision.MERGE);
            UUID skip = db.create(request().withExisting(ConflictDecision.SKIP));
            assertThat(db.policy(skip, false)).isEqualTo(ConflictDecision.SKIP);
            assertThat(db.policy(skip, true)).isEqualTo(ConflictDecision.MERGE);
            assertThat(db.request(replace).existing()).as("the policy lives on the job, not the encoded request").isEqualTo(ConflictDecision.ASK);
        }
        assertThatThrownBy(() -> request().withExisting(ConflictDecision.RENAME)).isInstanceOf(IllegalArgumentException.class);
    }
```

Add to `TransferCoordinatorTest`:

```java
    @Test void anExistingItemsChoiceCoversConflictsInsideMergedFolders() throws Exception {
        root=root.toRealPath();Path source=Files.createDirectories(root.resolve("source/sub")).getParent(),dest=Files.createDirectories(root.resolve("dest/source/sub")).getParent().getParent();
        Files.writeString(source.resolve("sub/file"),"new");Files.writeString(source.resolve("fresh"),"fresh");Files.writeString(dest.resolve("source/sub/file"),"old");
        try(var executor=Executors.newVirtualThreadPerTaskExecutor()) {
            var coordinator=new TransferCoordinator(root.resolve("queue"),executor,Runnable::run,(ref,owner)->CompletableFuture.completedFuture(new LocalEndpoint()),()->2);
            try {
                var request=new TransferRequest(EndpointRef.local(),List.of(source.toString()),EndpointRef.local(),dest.toString());
                var replaced=coordinator.enqueue(request.withExisting(ConflictDecision.REPLACE),null).get(5,TimeUnit.SECONDS);
                await(coordinator,replaced,TransferState.COMPLETED);
                assertThat(Files.readString(dest.resolve("source/sub/file"))).isEqualTo("new");
                assertThat(Files.readString(dest.resolve("source/fresh"))).isEqualTo("fresh");
                assertThat(coordinator.request(replaced).get(5,TimeUnit.SECONDS).paths()).containsExactly(source.toString());
                Files.writeString(source.resolve("sub/file"),"newer");
                var skipped=coordinator.enqueue(request.withExisting(ConflictDecision.SKIP),null).get(5,TimeUnit.SECONDS);
                var job=await(coordinator,skipped,TransferState.COMPLETED_WITH_ISSUES);
                assertThat(Files.readString(dest.resolve("source/sub/file"))).as("existing file kept").isEqualTo("new");
                assertThat(job.skippedEntries()).isGreaterThanOrEqualTo(1);
                assertThat(job.failedEntries()).isZero();
            } finally { coordinator.close();coordinator.stopped().get(5,TimeUnit.SECONDS); }
        }
    }
```

Create `ExistingItemsTest.java`:

```java
package dev.jasper.remote.transfer;

import static org.assertj.core.api.Assertions.*;

import dev.jasper.remote.sftp.LocalEndpoint;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ExistingItemsTest {
    @TempDir Path root;

    @Test void reportsOnlyTheSelectedNamesThatExist() throws IOException {
        Files.writeString(root.resolve("report.pdf"), "x");
        Files.createDirectory(root.resolve("photos"));
        try (var endpoint = new LocalEndpoint()) {
            assertThat(ExistingItems.existing(endpoint, root.toString(), List.of("report.pdf", "new.txt", "photos"))).containsExactly("report.pdf", "photos");
            assertThat(ExistingItems.existing(endpoint, root.toString(), List.of("new.txt"))).isEmpty();
        }
    }

    @Test void otherStatFailuresPropagate() throws IOException {
        Path file = Files.writeString(root.resolve("not-a-folder"), "x");
        try (var endpoint = new LocalEndpoint()) {
            assertThatThrownBy(() -> ExistingItems.existing(endpoint, file.toString(), List.of("child"))).isInstanceOf(IOException.class)
                .isNotInstanceOf(java.nio.file.NoSuchFileException.class);
        }
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :jasper-plugin-remote:test --tests 'dev.jasper.remote.transfer.store.TransferStoreTest' --tests 'dev.jasper.remote.transfer.TransferCoordinatorTest' --tests 'dev.jasper.remote.transfer.ExistingItemsTest'`
Expected: compilation FAILS (`withExisting`, `existing()`, `request(UUID)`, `ExistingItems` do not exist).

- [ ] **Step 3: Implement**

`TransferRequest.java` (replace the record):

```java
package dev.jasper.remote.transfer;

import java.util.List;
import java.util.Objects;

/**
 * Immutable user selection; normalized and inspected off the UI thread before mutation. {@code existing} is the
 * choice made before copying for items already at the destination: ASK (none), REPLACE or SKIP; folders merge.
 */
public record TransferRequest(EndpointRef source, List<String> paths, EndpointRef destination, String directory, ConflictDecision existing) {
    public TransferRequest {
        Objects.requireNonNull(source); Objects.requireNonNull(destination); Objects.requireNonNull(directory); Objects.requireNonNull(existing);
        paths = List.copyOf(paths);
        if (paths.isEmpty()) throw new IllegalArgumentException("Select files or a folder");
        if (paths.stream().anyMatch(p -> p.isEmpty() || p.indexOf(0) >= 0) || directory.isEmpty() || directory.indexOf(0) >= 0)
            throw new IllegalArgumentException("Invalid path");
        if (existing != ConflictDecision.ASK && existing != ConflictDecision.REPLACE && existing != ConflictDecision.SKIP)
            throw new IllegalArgumentException("Choose Replace or Skip existing");
    }

    public TransferRequest(EndpointRef source, List<String> paths, EndpointRef destination, String directory) {
        this(source, paths, destination, directory, ConflictDecision.ASK);
    }

    public TransferRequest withExisting(ConflictDecision choice) { return new TransferRequest(source, paths, destination, directory, choice); }
}
```

`TransferStore.create` — replace the `try` line so the policies are written in the same insert:

```java
        ConflictDecision files = request.existing(), folders = files == ConflictDecision.ASK ? ConflictDecision.ASK : ConflictDecision.MERGE;
        try { update("INSERT INTO jobs(id,request,source,destination,state,intent,created,file_policy,directory_policy) VALUES(?,?,?,?,?,?,?,?,?)",id,encoded,request.source().label(),request.destination().label(),"QUEUED","RUN",System.currentTimeMillis(),files.name(),folders.name()); return id; }
```

(add `import dev.jasper.remote.transfer.ConflictDecision;` if the file does not already import `dev.jasper.remote.transfer.*`).

`TransferCoordinator` — after the `entries(...)` method add:

```java
    public CompletableFuture<TransferRequest> request(UUID id) { return command(()->store.request(id)); }
```

`ExistingItems.java`:

```java
package dev.jasper.remote.transfer;

import dev.jasper.remote.sftp.FileEndpoint;
import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.util.ArrayList;
import java.util.List;

/** Which selected top-level names already exist in a destination directory: one stat each, never a listing. */
public final class ExistingItems {
    private ExistingItems() { }

    public static List<String> existing(FileEndpoint destination, String directory, List<String> names) throws IOException {
        var found = new ArrayList<String>();
        for (String name : names) {
            try { destination.stat(destination.child(directory, name)); found.add(name); }
            catch (NoSuchFileException absent) { /* free to copy */ }
        }
        return List.copyOf(found);
    }
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: the Step 2 command.
Expected: PASS. If `otherStatFailuresPropagate` sees `NoSuchFileException` on this platform (a child of a regular file), change its fixture to a directory without read/execute permission (`Files.setPosixFilePermissions(dir, Set.of())`, restored in `finally`) and note it in the report.

- [ ] **Step 5: Commit**

```bash
git add plugins/remote/src/main/java/dev/jasper/remote/transfer plugins/remote/src/test/java/dev/jasper/remote/transfer
git commit -m "feat(remote): record a Replace or Skip existing choice with the transfer

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 2: Row text, speed and the "Done" fade (pure helpers)

**Files:**
- Create: `plugins/remote/src/main/java/dev/jasper/remote/ui/transfers/TransferRows.java`
- Create: `plugins/remote/src/main/java/dev/jasper/remote/ui/transfers/JobSpeeds.java`
- Create: `plugins/remote/src/main/java/dev/jasper/remote/ui/transfers/FinishedJobs.java`
- Test: create `plugins/remote/src/test/java/dev/jasper/remote/ui/transfers/TransferRowsTest.java`

**Interfaces:**
- Consumes: `TransferJob`, `TransferRequest` (Task 1), `TransferPresentation.bytes(long)` (existing).
- Produces (package `dev.jasper.remote.ui.transfers`):
  - `public final class TransferRows` with `public enum Action { RESUME, RESOLVE, RETRY_FAILED, RETRY_CLEANUP; public String label() }` (labels `Resume`, `Resolve…`, `Retry failed`, `Retry cleanup`); `public record Row(UUID id, String arrow, String title, String tooltip, String status, OptionalDouble fraction, boolean indeterminate, Optional<Action> action, boolean finished, boolean cancelling)`; `public static Row row(TransferJob job, Optional<TransferRequest> request, long doneBytes, double bytesPerSecond)`; package-private `static Optional<Action> action(TransferJob)`, `static String status(TransferJob, long doneBytes, double speed)`, `static String remaining(long seconds)`, `static String name(String path)`.
  - `final class JobSpeeds`: `double update(UUID id, TransferState state, long doneBytes, long nanos)`, `void retain(Set<UUID> ids)`.
  - `final class FinishedJobs`: `static final long FADE_NANOS` (5 s); `static boolean clean(TransferJob)`; `List<UUID> expired(List<TransferJob> jobs, long nanos)`.

- [ ] **Step 1: Write the failing test**

```java
package dev.jasper.remote.ui.transfers;

import static org.assertj.core.api.Assertions.*;

import dev.jasper.remote.transfer.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class TransferRowsTest {
    static final UUID ID = UUID.randomUUID();
    static TransferJob job(TransferState state, String source, String destination, long entries, long total, long confirmed, long done, long skipped, long failed, String detail, long cleanup) {
        return new TransferJob(ID, source, destination, state, TransferJob.Intent.RUN, 0, entries, total, confirmed, done, skipped, failed, state != TransferState.SCANNING, detail, cleanup);
    }
    static TransferRequest upload(String... paths) { return new TransferRequest(EndpointRef.local(), List.of(paths), EndpointRef.local(), "/srv/app"); }

    @Test void copyingShowsPercentSpeedAndTimeLeft() {
        var running = job(TransferState.RUNNING, "Local", "dustin@host", 1, 100L * 1024 * 1024, 0, 0, 0, 0, "", 0);
        var row = TransferRows.row(running, Optional.of(upload("/Users/me/report.pdf")), 58L * 1024 * 1024, 4.1 * 1024 * 1024);
        assertThat(row.arrow()).isEqualTo("↑");
        assertThat(row.title()).isEqualTo("report.pdf → /srv/app");
        assertThat(row.status()).isEqualTo("58% · 4.1 MiB/s · 11 s left");
        assertThat(row.fraction().orElseThrow()).isEqualTo(0.58, within(0.001));
        assertThat(row.action()).isEmpty();
        assertThat(row.finished()).isFalse();
        assertThat(TransferRows.status(running, 58L * 1024 * 1024, 0)).as("no speed yet, no time left").isEqualTo("58%");
    }

    @Test void titlesNameTheSelectionAndTheDestination() {
        var folder = job(TransferState.RUNNING, "dustin@host", "Local", 341, 10, 0, 0, 0, 0, "", 0);
        assertThat(TransferRows.row(folder, Optional.of(upload("/var/log/")), 0, 0).title()).isEqualTo("log (340 items) → /srv/app");
        assertThat(TransferRows.row(folder, Optional.of(upload("/a/one", "/a/two", "/a/three")), 0, 0).title()).isEqualTo("one and 2 more → /srv/app");
        assertThat(TransferRows.row(folder, Optional.of(upload("C:\\Users\\me\\notes.txt")), 0, 0).title()).startsWith("notes.txt");
        assertThat(TransferRows.row(folder, Optional.empty(), 0, 0).title()).as("before the request arrives").isEqualTo("dustin@host → Local");
        assertThat(TransferRows.row(folder, Optional.empty(), 0, 0).arrow()).isEqualTo("↓");
        assertThat(TransferRows.row(job(TransferState.RUNNING, "a@one", "b@two", 1, 1, 0, 0, 0, 0, "", 0), Optional.empty(), 0, 0).arrow()).isEqualTo("⇄");
        assertThat(TransferRows.row(folder, Optional.of(upload("/var/log")), 0, 0).tooltip()).contains("dustin@host", "/var/log", "/srv/app");
    }

    @Test void statesUseTheirNamesAndReasons() {
        assertThat(TransferRows.status(job(TransferState.SCANNING, "Local", "h", 12, 0, 0, 0, 0, 0, "", 0), 0, 0)).isEqualTo("Scanning… 12 items found");
        assertThat(TransferRows.row(job(TransferState.SCANNING, "Local", "h", 12, 0, 0, 0, 0, 0, "", 0), Optional.empty(), 0, 0).indeterminate()).isTrue();
        assertThat(TransferRows.status(job(TransferState.INTERRUPTED, "Local", "h", 1, 10, 5, 0, 0, 0, "connection lost", 0), 5, 0)).isEqualTo("Interrupted — connection lost");
        assertThat(TransferRows.status(job(TransferState.NEEDS_ATTENTION, "Local", "h", 1, 10, 5, 0, 0, 0, "Destination changed", 0), 5, 0)).isEqualTo("Needs attention — Destination changed");
        assertThat(TransferRows.status(job(TransferState.COMPLETED, "Local", "h", 1, 10, 10, 1, 0, 0, "", 0), 10, 0)).isEqualTo("Done");
        assertThat(TransferRows.status(job(TransferState.COMPLETED_WITH_ISSUES, "Local", "h", 5, 10, 8, 2, 1, 2, "", 0), 8, 0)).isEqualTo("Done · 2 failed · 1 skipped");
        assertThat(TransferRows.status(job(TransferState.CANCELLED, "Local", "h", 1, 10, 5, 0, 0, 0, "", 0), 5, 0)).isEqualTo("Cancelled");
        assertThat(TransferRows.status(job(TransferState.PAUSED, "Local", "h", 1, 10, 5, 0, 0, 0, "", 0), 5, 0)).isEqualTo("Paused");
    }

    @Test void oneActionPerStateWithCleanupFirst() {
        assertThat(TransferRows.action(job(TransferState.PAUSED, "Local", "h", 1, 1, 0, 0, 0, 0, "", 0))).contains(TransferRows.Action.RESUME);
        assertThat(TransferRows.action(job(TransferState.INTERRUPTED, "Local", "h", 1, 1, 0, 0, 0, 0, "", 0))).contains(TransferRows.Action.RESUME);
        assertThat(TransferRows.action(job(TransferState.FAILED, "Local", "h", 1, 1, 0, 0, 0, 0, "", 0))).contains(TransferRows.Action.RESUME);
        assertThat(TransferRows.action(job(TransferState.NEEDS_ATTENTION, "Local", "h", 1, 1, 0, 0, 0, 0, "", 0))).contains(TransferRows.Action.RESOLVE);
        assertThat(TransferRows.action(job(TransferState.COMPLETED_WITH_ISSUES, "Local", "h", 3, 1, 0, 1, 0, 2, "", 0))).contains(TransferRows.Action.RETRY_FAILED);
        assertThat(TransferRows.action(job(TransferState.COMPLETED_WITH_ISSUES, "Local", "h", 3, 1, 0, 2, 1, 0, "", 0))).as("skips only").isEmpty();
        assertThat(TransferRows.action(job(TransferState.CANCELLED, "Local", "h", 1, 1, 0, 0, 0, 0, "", 2))).contains(TransferRows.Action.RETRY_CLEANUP);
        assertThat(TransferRows.action(job(TransferState.COMPLETED_WITH_ISSUES, "Local", "h", 3, 1, 0, 1, 0, 2, "", 1))).contains(TransferRows.Action.RETRY_CLEANUP);
        assertThat(TransferRows.action(job(TransferState.RUNNING, "Local", "h", 1, 1, 0, 0, 0, 0, "", 0))).isEmpty();
        assertThat(TransferRows.Action.RESOLVE.label()).isEqualTo("Resolve…");
        var cancelling = new TransferJob(ID, "Local", "h", TransferState.PAUSED, TransferJob.Intent.CANCEL, 0, 1, 1, 0, 0, 0, 0, true, "", 0);
        assertThat(TransferRows.action(cancelling)).isEmpty();
        assertThat(TransferRows.row(cancelling, Optional.empty(), 0, 0).cancelling()).isTrue();
        assertThat(TransferRows.row(job(TransferState.CANCELLED, "Local", "h", 1, 1, 0, 0, 0, 0, "", 0), Optional.empty(), 0, 0).finished()).isTrue();
    }

    @Test void remainingTimeReads() {
        assertThat(TransferRows.remaining(12)).isEqualTo("12 s");
        assertThat(TransferRows.remaining(61)).isEqualTo("2 min");
        assertThat(TransferRows.remaining(3900)).isEqualTo("1 h 5 min");
        assertThat(TransferRows.name("/a/b/")).isEqualTo("b");
        assertThat(TransferRows.name("C:\\x\\y.txt")).isEqualTo("y.txt");
    }

    @Test void speedIsSmoothedAndResetsWhenNotCopying() {
        var speeds = new JobSpeeds();
        long second = TimeUnit.SECONDS.toNanos(1);
        assertThat(speeds.update(ID, TransferState.RUNNING, 0, 0)).isZero();
        assertThat(speeds.update(ID, TransferState.RUNNING, 1000, second)).isEqualTo(1000.0, within(0.01));
        assertThat(speeds.update(ID, TransferState.RUNNING, 3000, 2 * second)).isEqualTo(1300.0, within(0.01));
        assertThat(speeds.update(ID, TransferState.PAUSED, 3000, 3 * second)).isZero();
        assertThat(speeds.update(ID, TransferState.RUNNING, 3000, 4 * second)).as("a pause starts over").isZero();
    }

    @Test void skipOnlyFinishFadesButFailuresStay() {
        var finished = new FinishedJobs();
        var clean = job(TransferState.COMPLETED, "Local", "h", 1, 1, 1, 1, 0, 0, "", 0);
        var skipOnly = new TransferJob(UUID.randomUUID(), "Local", "h", TransferState.COMPLETED_WITH_ISSUES, TransferJob.Intent.RUN, 0, 3, 1, 1, 2, 1, 0, true, "", 0);
        var failed = new TransferJob(UUID.randomUUID(), "Local", "h", TransferState.COMPLETED_WITH_ISSUES, TransferJob.Intent.RUN, 0, 3, 1, 1, 2, 0, 1, true, "", 0);
        var dirty = new TransferJob(UUID.randomUUID(), "Local", "h", TransferState.COMPLETED, TransferJob.Intent.RUN, 0, 1, 1, 1, 1, 0, 0, true, "", 1);
        var jobs = List.of(clean, skipOnly, failed, dirty);
        assertThat(finished.expired(jobs, 0)).isEmpty();
        assertThat(finished.expired(jobs, FinishedJobs.FADE_NANOS - 1)).isEmpty();
        assertThat(finished.expired(jobs, FinishedJobs.FADE_NANOS)).containsExactlyInAnyOrder(clean.id(), skipOnly.id());
        assertThat(FinishedJobs.clean(failed)).isFalse();
        assertThat(FinishedJobs.clean(dirty)).isFalse();
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :jasper-plugin-remote:test --tests 'dev.jasper.remote.ui.transfers.TransferRowsTest'`
Expected: compilation FAILS (`TransferRows`, `JobSpeeds`, `FinishedJobs` do not exist).

- [ ] **Step 3: Implement**

`TransferRows.java`:

```java
package dev.jasper.remote.ui.transfers;

import dev.jasper.remote.transfer.*;
import java.io.IOException;
import java.util.*;

/** Pure text for one transfer in the strip: what, where, how far, and the one action that applies. */
public final class TransferRows {
    public enum Action {
        RESUME("Resume"), RESOLVE("Resolve…"), RETRY_FAILED("Retry failed"), RETRY_CLEANUP("Retry cleanup");
        private final String label;
        Action(String label) { this.label = label; }
        public String label() { return label; }
    }
    public record Row(UUID id, String arrow, String title, String tooltip, String status, OptionalDouble fraction,
                      boolean indeterminate, Optional<Action> action, boolean finished, boolean cancelling) { }
    static final String UP = "↑", DOWN = "↓", ACROSS = "⇄", LOCAL = "Local";

    private TransferRows() { }

    public static Row row(TransferJob job, Optional<TransferRequest> request, long doneBytes, double bytesPerSecond) {
        String arrow = job.source().equals(LOCAL) ? UP : job.destination().equals(LOCAL) ? DOWN : ACROSS;
        String title = request.map(r -> what(r.paths(), job) + " → " + where(r, job)).orElse(job.source() + " → " + job.destination());
        String tooltip = request.map(r -> job.source() + ": " + String.join(", ", r.paths()) + " → " + job.destination() + ": " + r.directory())
            .orElse(job.source() + " → " + job.destination());
        boolean indeterminate = job.state() == TransferState.SCANNING;
        OptionalDouble fraction = job.state() == TransferState.COMPLETED ? OptionalDouble.of(1)
            : !indeterminate && job.totalBytes() > 0 ? OptionalDouble.of(Math.clamp((double) doneBytes / job.totalBytes(), 0, 1)) : OptionalDouble.empty();
        return new Row(job.id(), arrow, title, tooltip, status(job, doneBytes, bytesPerSecond), fraction, indeterminate, action(job),
            job.state().terminal(), job.intent() == TransferJob.Intent.CANCEL);
    }

    static Optional<Action> action(TransferJob job) {
        if (job.cleanupPending() > 0 && job.state().terminal()) return Optional.of(Action.RETRY_CLEANUP);
        if (job.intent() == TransferJob.Intent.CANCEL) return Optional.empty();
        return switch (job.state()) {
            case PAUSED, INTERRUPTED, FAILED -> Optional.of(Action.RESUME);
            case NEEDS_ATTENTION -> Optional.of(Action.RESOLVE);
            case COMPLETED_WITH_ISSUES -> job.failedEntries() > 0 ? Optional.of(Action.RETRY_FAILED) : Optional.empty();
            default -> Optional.empty();
        };
    }

    static String status(TransferJob job, long doneBytes, double speed) {
        String reason = job.detail().isBlank() ? "" : " — " + job.detail();
        return switch (job.state()) {
            case QUEUED -> "Queued";
            case SCANNING -> "Scanning… " + job.totalEntries() + (job.totalEntries() == 1 ? " item" : " items") + " found";
            case VALIDATING -> "Checking partial file…";
            case RUNNING -> running(job, doneBytes, speed);
            case PAUSING -> "Pausing…";
            case PAUSED -> "Paused";
            case CANCELLING -> "Cancelling…";
            case CANCELLED -> "Cancelled";
            case COMPLETED -> "Done";
            case COMPLETED_WITH_ISSUES -> done(job);
            case NEEDS_ATTENTION -> "Needs attention" + reason;
            case INTERRUPTED -> "Interrupted" + reason;
            case FAILED -> "Failed" + reason;
        };
    }

    private static String running(TransferJob job, long doneBytes, double speed) {
        if (job.totalBytes() <= 0) return "Transferring";
        var text = new StringBuilder().append((long) Math.floor(100.0 * Math.min(doneBytes, job.totalBytes()) / job.totalBytes())).append('%');
        if (speed > 0) {
            text.append(" · ").append(TransferPresentation.bytes((long) speed)).append("/s");
            long left = job.totalBytes() - doneBytes;
            if (left > 0) text.append(" · ").append(remaining((long) Math.ceil(left / speed))).append(" left");
        }
        return text.toString();
    }

    private static String done(TransferJob job) {
        var text = new StringBuilder("Done");
        if (job.failedEntries() > 0) text.append(" · ").append(job.failedEntries()).append(" failed");
        if (job.skippedEntries() > 0) text.append(" · ").append(job.skippedEntries()).append(" skipped");
        if (job.metadataWarnings() > 0) text.append(" · ").append(job.metadataWarnings()).append(" warnings");
        return text.toString();
    }

    static String remaining(long seconds) {
        if (seconds < 60) return seconds + " s";
        if (seconds < 3600) return (seconds + 59) / 60 + " min";
        return seconds / 3600 + " h " + (seconds % 3600) / 60 + " min";
    }

    static String name(String path) {
        String trimmed = path;
        while (trimmed.length() > 1 && (trimmed.endsWith("/") || trimmed.endsWith("\\"))) trimmed = trimmed.substring(0, trimmed.length() - 1);
        int cut = Math.max(trimmed.lastIndexOf('/'), trimmed.lastIndexOf('\\'));
        return cut < 0 || cut == trimmed.length() - 1 ? trimmed : trimmed.substring(cut + 1);
    }

    private static String what(List<String> paths, TransferJob job) {
        String first = name(paths.getFirst());
        if (paths.size() > 1) return first + " and " + (paths.size() - 1) + " more";
        return job.scanned() && job.totalEntries() > 1 ? first + " (" + (job.totalEntries() - 1) + " items)" : first;
    }

    private static String where(TransferRequest request, TransferJob job) {
        try {
            return request.destination().identity().map(identity -> identity.host().name() + ":" + request.directory()).orElse(request.directory());
        } catch (IOException unreadable) {
            return job.destination();
        }
    }
}
```

`JobSpeeds.java`:

```java
package dev.jasper.remote.ui.transfers;

import dev.jasper.remote.transfer.TransferState;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Per-transfer smoothed payload speed; a transfer that is not copying has none and starts over. */
final class JobSpeeds {
    private record Sample(long bytes, long nanos, double speed) { }
    private final Map<UUID, Sample> samples = new HashMap<>();

    double update(UUID id, TransferState state, long doneBytes, long nanos) {
        if (state != TransferState.RUNNING) { samples.remove(id); return 0; }
        var last = samples.get(id);
        double speed = 0;
        if (last != null && nanos > last.nanos() && doneBytes >= last.bytes()) {
            double instant = (doneBytes - last.bytes()) * 1_000_000_000.0 / (nanos - last.nanos());
            speed = last.speed() == 0 ? instant : last.speed() * .7 + instant * .3;
        }
        samples.put(id, new Sample(doneBytes, nanos, speed));
        return speed;
    }

    void retain(Set<UUID> ids) { samples.keySet().retainAll(ids); }
}
```

`FinishedJobs.java`:

```java
package dev.jasper.remote.ui.transfers;

import dev.jasper.remote.transfer.TransferJob;
import dev.jasper.remote.transfer.TransferState;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** Remembers when each transfer finished cleanly and says which have shown "Done" long enough to leave. */
final class FinishedJobs {
    static final long FADE_NANOS = TimeUnit.SECONDS.toNanos(5);
    private final Map<UUID, Long> since = new HashMap<>();

    /** Completed, or completed whose only issues are deliberately skipped items, with nothing left to clean up. */
    static boolean clean(TransferJob job) {
        if (job.cleanupPending() > 0) return false;
        return job.state() == TransferState.COMPLETED
            || job.state() == TransferState.COMPLETED_WITH_ISSUES && job.failedEntries() == 0 && job.metadataWarnings() == 0;
    }

    List<UUID> expired(List<TransferJob> jobs, long nanos) {
        var seen = new HashSet<UUID>();
        var result = new ArrayList<UUID>();
        for (var job : jobs) {
            if (!clean(job)) continue;
            seen.add(job.id());
            if (nanos - since.computeIfAbsent(job.id(), id -> nanos) >= FADE_NANOS) result.add(job.id());
        }
        since.keySet().retainAll(seen);
        return List.copyOf(result);
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: the Step 2 command.
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add plugins/remote/src/main/java/dev/jasper/remote/ui/transfers/TransferRows.java plugins/remote/src/main/java/dev/jasper/remote/ui/transfers/JobSpeeds.java plugins/remote/src/main/java/dev/jasper/remote/ui/transfers/FinishedJobs.java plugins/remote/src/test/java/dev/jasper/remote/ui/transfers/TransferRowsTest.java
git commit -m "feat(remote): describe a transfer in one compact row

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 3: The strip and its two dialogs

**Files:**
- Create: `plugins/remote/src/main/java/dev/jasper/remote/ui/transfers/TransferStrip.java`
- Create: `plugins/remote/src/main/java/dev/jasper/remote/ui/transfers/ResolvePanel.java`
- Create: `plugins/remote/src/main/java/dev/jasper/remote/ui/transfers/ExistingItemsPanel.java`
- Test: create `plugins/remote/src/test/java/dev/jasper/remote/ui/transfers/TransferStripTest.java`

**Interfaces:**
- Consumes: `TransferRows.Row`, `TransferRows.Action` (Task 2); `TransferEntry`, `ConflictDecision`, `FileEntry`.
- Produces:
  - `public final class TransferStrip extends JPanel`: `public record Actions(BiConsumer<UUID, TransferRows.Action> action, Consumer<UUID> cancel, Consumer<UUID> dismiss)`; `public TransferStrip(Actions)`; `public void rows(List<TransferRows.Row>)`; `public void unavailable(String message)`; package-private `int visibleRows()`, `Optional<RowView> view(UUID)`, `String unavailableText()`, `static String elideMiddle(String, FontMetrics, int)`, `static JLabel label(String)`; `RowView` exposes `String fullTitle()`, `String titleText()`, `String tooltip()`, `String statusText()`, `JButton actionButton()`, `JButton closeButton()`, `JProgressBar progress()`, `void setSize/doLayout` via Swing.
  - `public final class ResolvePanel extends JPanel`: `public record Choice(ConflictDecision decision, boolean remaining)`; `public ResolvePanel(TransferEntry entry, Consumer<Choice> decide, Runnable restartFile, Runnable close)`; package-private buttons `replace, merge, skip, rename, restart, cancel` and checkbox `remaining`.
  - `public final class ExistingItemsPanel extends JPanel`: `public ExistingItemsPanel(String message, Consumer<ConflictDecision> choose, Runnable close)`; `public static String message(List<String> existing, int selected, String destination)`; package-private buttons `replace, skip, cancel`.

- [ ] **Step 1: Write the failing test**

```java
package dev.jasper.remote.ui.transfers;

import static org.assertj.core.api.Assertions.*;

import dev.jasper.remote.sftp.FileEntry;
import dev.jasper.remote.transfer.*;
import java.util.*;
import javax.swing.*;
import org.junit.jupiter.api.Test;

class TransferStripTest {
    final List<String> calls = new ArrayList<>();
    final TransferStrip strip = new TransferStrip(new TransferStrip.Actions((id, action) -> calls.add(action + " " + id), id -> calls.add("cancel " + id), id -> calls.add("dismiss " + id)));

    static TransferRows.Row row(UUID id, String status, Optional<TransferRows.Action> action, boolean finished) {
        return new TransferRows.Row(id, "↑", "report.pdf → prod:/srv/app", "Local: /tmp/report.pdf → dustin@host: /srv/app", status, OptionalDouble.of(0.5), false, action, finished, false);
    }

    @Test void hiddenWhenEmptyAndShownWithRows() {
        assertThat(strip.isVisible()).isFalse();
        var id = UUID.randomUUID();
        strip.rows(List.of(row(id, "58% · 4.1 MiB/s · 11 s left", Optional.empty(), false)));
        assertThat(strip.isVisible()).isTrue();
        var view = strip.view(id).orElseThrow();
        assertThat(view.fullTitle()).isEqualTo("↑ report.pdf → prod:/srv/app");
        assertThat(view.tooltip()).contains("/tmp/report.pdf", "/srv/app");
        assertThat(view.statusText()).isEqualTo("58% · 4.1 MiB/s · 11 s left");
        assertThat(view.progress().getValue()).isEqualTo(500);
        assertThat(view.actionButton().isVisible()).isFalse();
        strip.rows(List.of());
        assertThat(strip.isVisible()).isFalse();
        assertThat(strip.view(id)).isEmpty();
    }

    @Test void theActionButtonAndCloseDoTheRightThing() {
        var running = UUID.randomUUID();
        var paused = UUID.randomUUID();
        var done = UUID.randomUUID();
        strip.rows(List.of(row(running, "40%", Optional.empty(), false), row(paused, "Paused", Optional.of(TransferRows.Action.RESUME), false),
            row(done, "Cancelled", Optional.empty(), true)));
        var resume = strip.view(paused).orElseThrow().actionButton();
        assertThat(resume.isVisible()).isTrue();
        assertThat(resume.getText()).isEqualTo("Resume");
        resume.doClick();
        strip.view(running).orElseThrow().closeButton().doClick();
        strip.view(done).orElseThrow().closeButton().doClick();
        assertThat(calls).containsExactly("RESUME " + paused, "cancel " + running, "dismiss " + done);
        assertThat(strip.view(running).orElseThrow().closeButton().getToolTipText()).isEqualTo("Cancel transfer");
        assertThat(strip.view(done).orElseThrow().closeButton().getToolTipText()).isEqualTo("Dismiss");
    }

    @Test void atMostThreeRowsAreVisible() {
        var rows = new ArrayList<TransferRows.Row>();
        for (int i = 0; i < 5; i++) rows.add(row(UUID.randomUUID(), "Queued", Optional.empty(), false));
        strip.rows(rows);
        assertThat(strip.visibleRows()).isEqualTo(3);
    }

    @Test void longTitlesAreElidedInTheMiddleWithTheFullTextInTheTooltip() {
        var metrics = new JLabel().getFontMetrics(new JLabel().getFont());
        String text = "a-very-long-folder-name-for-photos (340 items) → production-server:/srv/app/uploads/2026";
        String elided = TransferStrip.elideMiddle(text, metrics, metrics.stringWidth(text) / 2);
        assertThat(elided).contains("…").startsWith("a-very").endsWith("2026");
        assertThat(metrics.stringWidth(elided)).isLessThanOrEqualTo(metrics.stringWidth(text) / 2);
        assertThat(TransferStrip.elideMiddle("short", metrics, 1000)).isEqualTo("short");
        var id = UUID.randomUUID();
        strip.rows(List.of(new TransferRows.Row(id, "↑", text, "full tooltip", "Queued", OptionalDouble.empty(), false, Optional.empty(), false, false)));
        var view = strip.view(id).orElseThrow();
        view.setSize(160, 60);
        view.doLayout();
        assertThat(view.titleText()).contains("…").endsWith("2026");
        assertThat(view.tooltip()).isEqualTo("full tooltip");
    }

    @Test void anotherInstanceShowsOneLine() {
        strip.unavailable("Transfers are managed by another Jasper instance");
        assertThat(strip.isVisible()).isTrue();
        assertThat(strip.unavailableText()).isEqualTo("Transfers are managed by another Jasper instance");
        strip.rows(List.of());
        assertThat(strip.unavailableText()).isEmpty();
        assertThat(strip.isVisible()).isFalse();
    }

    @Test void resolveOffersTheDecisionsThatFit() {
        var chosen = new ArrayList<Object>();
        var file = new FileEntry("report.pdf", FileEntry.Kind.FILE, 3, 1000, 0644, "", "file-1");
        var entry = new TransferEntry(7, UUID.randomUUID(), "report.pdf", "/tmp/report.pdf", "/srv/app/report.pdf", file, "", Optional.empty(),
            TransferEntry.Phase.PENDING, 0, "", TransferEntry.Publication.NONE, Optional.of(file), ConflictDecision.ASK, TransferEntry.Outcome.PENDING, "Destination changed");
        var panel = new ResolvePanel(entry, chosen::add, () -> chosen.add("restart"), () -> chosen.add("close"));
        assertThat(panel.replace.isVisible()).isTrue();
        assertThat(panel.replace.isEnabled()).isTrue();
        assertThat(panel.merge.isVisible()).as("files cannot merge").isFalse();
        panel.remaining.setSelected(true);
        panel.replace.doClick();
        panel.rename.doClick();
        panel.restart.doClick();
        panel.cancel.doClick();
        assertThat(chosen).containsExactly(new ResolvePanel.Choice(ConflictDecision.REPLACE, true), new ResolvePanel.Choice(ConflictDecision.RENAME, false), "restart", "close");
    }

    @Test void existingItemsAsksOnce() {
        assertThat(ExistingItemsPanel.message(List.of("report.pdf"), 1, "prod:/srv/app")).isEqualTo("\"report.pdf\" already exists in prod:/srv/app.");
        assertThat(ExistingItemsPanel.message(List.of("a", "b", "c"), 5, "prod:/srv/app")).isEqualTo("3 of 5 items already exist in prod:/srv/app.");
        assertThat(ExistingItemsPanel.message(List.of("a"), 5, "/Users/me")).isEqualTo("1 of 5 items already exists in /Users/me.");
        var chosen = new ArrayList<Object>();
        var panel = new ExistingItemsPanel("3 of 5 items already exist in prod:/srv/app.", chosen::add, () -> chosen.add("cancel"));
        panel.replace.doClick();
        panel.skip.doClick();
        panel.cancel.doClick();
        assertThat(chosen).containsExactly(ConflictDecision.REPLACE, ConflictDecision.SKIP, "cancel");
        assertThat(panel.skip.getText()).isEqualTo("Skip existing");
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :jasper-plugin-remote:test --tests 'dev.jasper.remote.ui.transfers.TransferStripTest'`
Expected: compilation FAILS (`TransferStrip`, `ResolvePanel`, `ExistingItemsPanel` do not exist).

- [ ] **Step 3: Implement**

`TransferStrip.java`:

```java
package dev.jasper.remote.ui.transfers;

import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.function.*;
import javax.swing.*;

/** The SFTP sidebar's transfers: two-line rows, at most three visible before scrolling, hidden when empty. */
public final class TransferStrip extends JPanel {
    static final int VISIBLE_ROWS = 3;
    public record Actions(BiConsumer<UUID, TransferRows.Action> action, Consumer<UUID> cancel, Consumer<UUID> dismiss) { }
    private final Actions actions;
    private final JPanel list = new JPanel();
    private final JScrollPane scroll = new JScrollPane(list, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
    private final JLabel unavailable = label("");
    private final Map<UUID, RowView> views = new LinkedHashMap<>();

    public TransferStrip(Actions actions) {
        super(new BorderLayout(0, 4));
        this.actions = actions;
        Color rule = UIManager.getColor("Separator.foreground");
        setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, rule == null ? Color.GRAY : rule),
            BorderFactory.createEmptyBorder(6, 0, 0, 0)));
        list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
        scroll.setBorder(BorderFactory.createEmptyBorder());
        add(label("Transfers"), BorderLayout.NORTH);
        add(scroll, BorderLayout.CENTER);
        add(unavailable, BorderLayout.SOUTH);
        unavailable.setVisible(false);
        setVisible(false);
    }

    public void rows(List<TransferRows.Row> rows) {
        unavailable.setVisible(false);
        var keep = new HashSet<UUID>();
        list.removeAll();
        for (var row : rows) {
            keep.add(row.id());
            var view = views.computeIfAbsent(row.id(), id -> new RowView(id, actions));
            view.show(row);
            list.add(view);
        }
        views.keySet().retainAll(keep);
        resize();
        setVisible(!rows.isEmpty());
    }

    public void unavailable(String message) {
        views.clear();
        list.removeAll();
        unavailable.setText(message);
        unavailable.setToolTipText(message);
        unavailable.setVisible(true);
        resize();
        setVisible(true);
    }

    private void resize() {
        int height = 0, shown = 0;
        for (var view : views.values()) { if (shown++ == VISIBLE_ROWS) break; height += view.getPreferredSize().height; }
        scroll.setPreferredSize(new Dimension(10, height));
        scroll.setVisible(!views.isEmpty());
        revalidate();
        repaint();
    }

    int visibleRows() { return Math.min(views.size(), VISIBLE_ROWS); }
    Optional<RowView> view(UUID id) { return Optional.ofNullable(views.get(id)); }
    String unavailableText() { return unavailable.isVisible() ? unavailable.getText() : ""; }

    static JLabel label(String text) {
        var label = new JLabel(text);
        label.putClientProperty("html.disable", true);
        return label;
    }

    static String elideMiddle(String text, FontMetrics metrics, int width) {
        if (metrics.stringWidth(text) <= width) return text;
        for (int keep = text.length() - 1; keep > 1; keep--) {
            String candidate = text.substring(0, (keep + 1) / 2) + "…" + text.substring(text.length() - keep / 2);
            if (metrics.stringWidth(candidate) <= width) return candidate;
        }
        return "…";
    }

    static final class RowView extends JPanel {
        private final JLabel title = label(""), status = label("");
        private final JProgressBar progress = new JProgressBar(0, 1000);
        private final JButton action = new JButton(), close = new JButton("×");
        private String fullTitle = "", tooltip = "";
        private TransferRows.Row row;

        RowView(UUID id, Actions actions) {
            super(new BorderLayout(4, 2));
            setBorder(BorderFactory.createEmptyBorder(3, 0, 3, 0));
            progress.setPreferredSize(new Dimension(60, 6));
            progress.setBorderPainted(false);
            close.setMargin(new Insets(0, 4, 0, 4));
            close.getAccessibleContext().setAccessibleName("Cancel or dismiss transfer");
            action.setMargin(new Insets(0, 6, 0, 6));
            var top = new JPanel(new BorderLayout(4, 0));
            top.setOpaque(false);
            top.add(title, BorderLayout.CENTER);
            top.add(close, BorderLayout.EAST);
            var middle = new JPanel(new BorderLayout(0, 2));
            middle.setOpaque(false);
            middle.add(progress, BorderLayout.NORTH);
            middle.add(status, BorderLayout.CENTER);
            var bottom = new JPanel(new BorderLayout(4, 0));
            bottom.setOpaque(false);
            bottom.add(middle, BorderLayout.CENTER);
            bottom.add(action, BorderLayout.EAST);
            add(top, BorderLayout.NORTH);
            add(bottom, BorderLayout.CENTER);
            action.addActionListener(event -> { if (row != null) row.action().ifPresent(chosen -> actions.action().accept(id, chosen)); });
            close.addActionListener(event -> {
                if (row == null) return;
                if (row.finished()) actions.dismiss().accept(id); else actions.cancel().accept(id);
            });
        }

        void show(TransferRows.Row next) {
            row = next;
            fullTitle = next.arrow() + " " + next.title();
            tooltip = next.tooltip();
            title.setToolTipText(tooltip);
            title.getAccessibleContext().setAccessibleDescription(tooltip);
            fitTitle();
            status.setText(next.status());
            status.setToolTipText(next.status());
            progress.setIndeterminate(next.indeterminate());
            progress.setValue((int) Math.round(next.fraction().orElse(0) * 1000));
            progress.setVisible(next.indeterminate() || next.fraction().isPresent());
            action.setVisible(next.action().isPresent());
            next.action().ifPresent(chosen -> action.setText(chosen.label()));
            close.setEnabled(!next.cancelling());
            close.setToolTipText(next.finished() ? "Dismiss" : "Cancel transfer");
        }

        @Override public void doLayout() { fitTitle(); super.doLayout(); }

        private void fitTitle() {
            int width = getWidth() - close.getPreferredSize().width - 8;
            title.setText(width <= 0 ? fullTitle : elideMiddle(fullTitle, title.getFontMetrics(title.getFont()), width));
        }

        String fullTitle() { return fullTitle; }
        String titleText() { return title.getText(); }
        String tooltip() { return tooltip; }
        String statusText() { return status.getText(); }
        JButton actionButton() { return action; }
        JButton closeButton() { return close; }
        JProgressBar progress() { return progress; }
    }
}
```

`ResolvePanel.java`:

```java
package dev.jasper.remote.ui.transfers;

import dev.jasper.remote.sftp.FileEntry;
import dev.jasper.remote.transfer.ConflictDecision;
import dev.jasper.remote.transfer.TransferEntry;
import java.awt.*;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.*;

/** One transfer entry's decision: Replace for files, Merge for folders, Skip, Rename or Restart file. */
public final class ResolvePanel extends JPanel {
    public record Choice(ConflictDecision decision, boolean remaining) { }
    final JButton replace = new JButton("Replace"), merge = new JButton("Merge"), skip = new JButton("Skip"),
        rename = new JButton("Rename…"), restart = new JButton("Restart file"), cancel = new JButton("Cancel");
    final JCheckBox remaining = new JCheckBox("Apply to the rest of this transfer");

    public ResolvePanel(TransferEntry entry, Consumer<Choice> decide, Runnable restartFile, Runnable close) {
        super(new BorderLayout(8, 8));
        setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        boolean folder = entry.sourceInfo().kind() == FileEntry.Kind.DIRECTORY;
        var text = new JPanel(new GridLayout(0, 1, 0, 4));
        text.add(TransferStrip.label(entry.relative()));
        text.add(TransferStrip.label(entry.error()));
        add(text, BorderLayout.NORTH);
        replace.setVisible(!folder);
        replace.setEnabled(entry.expectedTarget().filter(target -> target.kind() == entry.sourceInfo().kind()).isPresent());
        merge.setVisible(folder);
        merge.setEnabled(entry.expectedTarget().filter(target -> target.kind() == FileEntry.Kind.DIRECTORY).isPresent());
        var buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        for (var button : List.of(replace, merge, skip, rename, restart, cancel)) buttons.add(button);
        var south = new JPanel(new BorderLayout(0, 6));
        south.add(remaining, BorderLayout.NORTH);
        south.add(buttons, BorderLayout.SOUTH);
        add(south, BorderLayout.SOUTH);
        replace.addActionListener(event -> decide.accept(new Choice(ConflictDecision.REPLACE, remaining.isSelected())));
        merge.addActionListener(event -> decide.accept(new Choice(ConflictDecision.MERGE, remaining.isSelected())));
        skip.addActionListener(event -> decide.accept(new Choice(ConflictDecision.SKIP, remaining.isSelected())));
        rename.addActionListener(event -> decide.accept(new Choice(ConflictDecision.RENAME, false)));
        restart.addActionListener(event -> restartFile.run());
        cancel.addActionListener(event -> close.run());
    }
}
```

`ExistingItemsPanel.java`:

```java
package dev.jasper.remote.ui.transfers;

import dev.jasper.remote.transfer.ConflictDecision;
import java.awt.*;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.*;

/** Asked once before copying when selected items already exist at the destination. */
public final class ExistingItemsPanel extends JPanel {
    final JButton replace = new JButton("Replace"), skip = new JButton("Skip existing"), cancel = new JButton("Cancel");

    public ExistingItemsPanel(String message, Consumer<ConflictDecision> choose, Runnable close) {
        super(new BorderLayout(8, 12));
        setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        add(TransferStrip.label(message), BorderLayout.CENTER);
        var buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        for (var button : List.of(replace, skip, cancel)) buttons.add(button);
        add(buttons, BorderLayout.SOUTH);
        replace.addActionListener(event -> choose.accept(ConflictDecision.REPLACE));
        skip.addActionListener(event -> choose.accept(ConflictDecision.SKIP));
        cancel.addActionListener(event -> close.run());
    }

    public static String message(List<String> existing, int selected, String destination) {
        if (selected == 1) return "\"" + existing.getFirst() + "\" already exists in " + destination + ".";
        return existing.size() + " of " + selected + " items already " + (existing.size() == 1 ? "exists" : "exist") + " in " + destination + ".";
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: the Step 2 command.
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add plugins/remote/src/main/java/dev/jasper/remote/ui/transfers/TransferStrip.java plugins/remote/src/main/java/dev/jasper/remote/ui/transfers/ResolvePanel.java plugins/remote/src/main/java/dev/jasper/remote/ui/transfers/ExistingItemsPanel.java plugins/remote/src/test/java/dev/jasper/remote/ui/transfers/TransferStripTest.java
git commit -m "feat(remote): add the compact transfer strip and its dialogs

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 4: Put the strip in the SFTP sidebar and retire the Transfers panel

**Files:**
- Rewrite: `plugins/remote/src/main/java/dev/jasper/remote/ui/transfers/TransferUi.java`
- Delete: `plugins/remote/src/main/java/dev/jasper/remote/ui/transfers/TransfersPanel.java`, `plugins/remote/src/test/java/dev/jasper/remote/ui/transfers/TransfersPanelTest.java`
- Create: `plugins/remote/src/test/java/dev/jasper/remote/ui/transfers/TransferPresentationTest.java`, `plugins/remote/src/test/java/dev/jasper/remote/ui/transfers/TransferUiTest.java`
- Modify: `plugins/remote/src/main/java/dev/jasper/remote/ui/sftp/SftpPanel.java`, `plugins/remote/src/main/java/dev/jasper/remote/ui/sftp/SftpUi.java`
- Modify: `plugins/remote/src/main/java/dev/jasper/remote/RemotePlugin.java:159-160,173-174,229`, `plugins/remote/src/main/java/dev/jasper/remote/RemoteShortcuts.java`, `plugins/remote/src/main/resources/settings.toml:17`
- Test: `plugins/remote/src/test/java/dev/jasper/remote/RemotePluginTest.java:74,137`, `plugins/remote/src/test/java/dev/jasper/remote/SftpPluginTest.java:26-31`

**Interfaces:**
- Consumes: Tasks 1–3 (`TransferCoordinator.request`, `TransferRows`, `JobSpeeds`, `FinishedJobs`, `TransferStrip`, `ResolvePanel`).
- Produces:
  - `TransferUi`: `public static final String SHOW = "dev.jasper.remote.transfers", CANCEL = SHOW + ".cancel"`; `public TransferUi(PluginContext, TransferCoordinator, Executor ui, Consumer<WindowHandle> showSidebar)`; package-private `TransferUi(PluginContext, TransferCoordinator, Executor ui, Consumer<WindowHandle> showSidebar, LongSupplier clock, boolean autoRefresh)`; `public JComponent strip(WindowHandle)`; `public void release(WindowHandle)`; package-private `void refresh()`; `close()`. No `PANEL`, `TOGGLE`, `show(...)` or `configureBinding(...)` any more.
  - `SftpPanel.transfers(JComponent strip)`.
  - `SftpUi`: constructors take `Function<WindowHandle,JComponent> transferStrip, Consumer<WindowHandle> releaseStrip` in place of `Consumer<WindowHandle> showTransfers`; `public void reveal(WindowHandle window)` shows the sidebar without choosing a host.

- [ ] **Step 1: Write the failing tests**

`TransferPresentationTest.java` (the presentation test moves here from `TransfersPanelTest`, unchanged):

```java
package dev.jasper.remote.ui.transfers;

import dev.jasper.remote.transfer.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class TransferPresentationTest {
    @Test void unknownScanIsIndeterminateAndLargeRemainingCountsDoNotOverflow() {
        var id=UUID.randomUUID();long bytes=5L*1024*1024*1024;
        var scanning=new TransferJob(id,"local","host",TransferState.SCANNING,TransferJob.Intent.RUN,0,1,bytes,0,0,0,0,false,"",0);
        var presentation=new TransferPresentation();
        var first=presentation.update(new TransferCoordinator.Snapshot(List.of(scanning),List.of(),0),1_000_000_000L);
        assertThat(first.fraction()).isEmpty();assertThat(first.text()).contains("Scanning");
        var running=new TransferJob(id,"local","host",TransferState.RUNNING,TransferJob.Intent.RUN,0,1,bytes,1024,0,0,0,true,"",0);
        var next=presentation.update(new TransferCoordinator.Snapshot(List.of(running),List.of(),1),2_000_000_000L);
        assertThat(next.detail()).contains("5.0 GiB");assertThat(next.fraction().orElseThrow()).isBetween(0.0,1.0);
    }
}
```

`TransferUiTest.java`:

```java
package dev.jasper.remote.ui.transfers;

import static org.assertj.core.api.Assertions.*;

import dev.jasper.remote.sftp.LocalEndpoint;
import dev.jasper.remote.transfer.*;
import dev.jasper.sdk.Capabilities;
import dev.jasper.sdk.PluginInfo;
import dev.jasper.sdk.testing.FakePluginHost;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TransferUiTest {
    static final PluginInfo INFO = new PluginInfo("dev.jasper.remote", "Remote", "0.2.0",
        Set.of(Capabilities.TERMINAL_OPEN, Capabilities.SESSION_PROVIDE, Capabilities.TERMINAL_OBSERVE, Capabilities.PALETTE_CONTRIBUTE));
    @TempDir Path root;

    @Test void theStripShowsACopyLetsItFadeAndTheShowActionRevealsTheSidebar() throws Exception {
        root = root.toRealPath();
        Path source = Files.writeString(root.resolve("report.txt"), "payload"), dest = Files.createDirectory(root.resolve("dest"));
        long[] now = {0};
        var shown = new ArrayList<UUID>();
        try (var host = new FakePluginHost(root.resolve("home")); var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var context = host.start(INFO, Set.of(), Set.of(), ignored -> { });
            var coordinator = new TransferCoordinator(root.resolve("queue"), executor, Runnable::run, (ref, owner) -> CompletableFuture.completedFuture(new LocalEndpoint()), () -> 2);
            TransferUi transfers = null;
            try {
                transfers = new TransferUi(context, coordinator, Runnable::run, window -> shown.add(window.id()), () -> now[0], false);
                UUID window = host.addTerminalWindow();
                var strip = (TransferStrip) transfers.strip(context.terminals().window(window).orElseThrow());
                var id = coordinator.enqueue(new TransferRequest(EndpointRef.local(), List.of(source.toString()), EndpointRef.local(), dest.toString()), null).get(5, TimeUnit.SECONDS);
                awaitView(transfers, strip, id, view -> view.statusText().equals("Done") && view.fullTitle().equals("↑ report.txt → " + dest));
                assertThat(strip.isVisible()).isTrue();
                now[0] += FinishedJobs.FADE_NANOS;
                awaitGone(transfers, strip, id);
                assertThat(strip.isVisible()).isFalse();
                assertThat(coordinator.snapshot(0, 50).get(5, TimeUnit.SECONDS).jobs()).as("history cleared").isEmpty();
                assertThat(Files.readString(dest.resolve("report.txt"))).as("destination kept").isEqualTo("payload");
                host.invoke(TransferUi.SHOW, window, null);
                assertThat(shown).containsExactly(window);
            } finally {
                if (transfers != null) transfers.close();
                coordinator.close();
                coordinator.stopped().get(5, TimeUnit.SECONDS);
            }
        }
    }

    static void awaitView(TransferUi transfers, TransferStrip strip, UUID id, Predicate<TransferStrip.RowView> done) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            transfers.refresh();
            var view = strip.view(id);
            if (view.isPresent() && done.test(view.get())) return;
            Thread.sleep(20);
        }
        throw new AssertionError("Row not reached: " + strip.view(id).map(v -> v.fullTitle() + " | " + v.statusText()).orElse("absent"));
    }

    static void awaitGone(TransferUi transfers, TransferStrip strip, UUID id) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            transfers.refresh();
            if (strip.view(id).isEmpty()) return;
            Thread.sleep(20);
        }
        throw new AssertionError("Row did not fade");
    }
}
```

Change `RemotePluginTest` lines 74 and 137 to:

```java
            assertThat(host.panels()).containsExactly("dev.jasper.remote.sftp.panel|SFTP|LEFT","dev.jasper.remote.panel|SSH hosts|LEFT");
```

In `SftpPluginTest.contributionsAndPausedStartupWorkWithoutBuddyOrAuthentication`, replace the `host.panels()` assertion with:

```java
            assertThat(host.panels()).contains("dev.jasper.remote.sftp.panel|SFTP|LEFT").noneMatch(panel->panel.startsWith("dev.jasper.remote.transfers.panel"));
            assertThat(host.actions()).noneMatch(action->action.startsWith("dev.jasper.remote.transfers.toggle"));
```

and after the two `host.invoke("dev.jasper.remote.transfers",...)` calls add:

```java
            assertThat(host.windows()).as("showing transfers never opens the host chooser").isEmpty();
            var sidebar=host.openPanel(dev.jasper.remote.ui.sftp.SftpUi.PANEL,window);
            assertThat(find(sidebar,dev.jasper.remote.ui.transfers.TransferStrip.class)).as("the strip lives in the SFTP sidebar").isPresent();
```

with this helper in `SftpPluginTest`:

```java
    static <T> Optional<T> find(java.awt.Component component,Class<T> type) {
        if(type.isInstance(component)) return Optional.of(type.cast(component));
        if(component instanceof java.awt.Container container) for(var child:container.getComponents()) { var found=find(child,type);if(found.isPresent()) return found; }
        return Optional.empty();
    }
```

(Keep the existing `toggle_transfers` entry in that test's `setConfig` call: it now proves a leftover key is ignored.)

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :jasper-plugin-remote:test --tests 'dev.jasper.remote.ui.transfers.*' --tests 'dev.jasper.remote.SftpPluginTest' --tests 'dev.jasper.remote.RemotePluginTest'`
Expected: compilation FAILS (`TransferUi` has no 6-argument constructor, `strip`, `refresh` package access).

- [ ] **Step 3: Rewrite `TransferUi`**

```java
package dev.jasper.remote.ui.transfers;

import dev.jasper.remote.transfer.*;
import dev.jasper.sdk.plugin.PluginContext;
import dev.jasper.sdk.terminal.WindowHandle;
import dev.jasper.sdk.ui.*;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.function.*;
import javax.swing.*;

/** The aggregate status item and every window's transfer strip over the plugin-owned queue. */
public final class TransferUi implements AutoCloseable {
    public static final String SHOW = "dev.jasper.remote.transfers", CANCEL = SHOW + ".cancel";
    private static final int PAGE = 50;
    private final PluginContext context;
    private final TransferCoordinator coordinator;
    private final Executor ui;
    private final Consumer<WindowHandle> showSidebar;
    private final LongSupplier clock;
    private final Map<UUID, TransferStrip> strips = new HashMap<>();
    private final Map<UUID, TransferRequest> requests = new HashMap<>();
    private final Set<UUID> requesting = new HashSet<>(), clearing = new HashSet<>();
    private final TransferPresentation presentation = new TransferPresentation();
    private final JobSpeeds speeds = new JobSpeeds();
    private final FinishedJobs finished = new FinishedJobs();
    private final StatusProgress status;
    private final javax.swing.Timer timer;
    private TransferCoordinator.Snapshot latest;
    private boolean pending;
    private volatile boolean closed;

    public TransferUi(PluginContext context, TransferCoordinator coordinator, Executor ui, Consumer<WindowHandle> showSidebar) {
        this(context, coordinator, ui, showSidebar, System::nanoTime, true);
    }

    TransferUi(PluginContext context, TransferCoordinator coordinator, Executor ui, Consumer<WindowHandle> showSidebar, LongSupplier clock, boolean autoRefresh) {
        this.context = context; this.coordinator = coordinator; this.ui = ui; this.showSidebar = showSidebar; this.clock = clock;
        context.actions().register(ActionSpec.of(SHOW, "Transfers").withIcon(context.appearance().icon(IconName.DOWNLOAD))
            .withKeywords(List.of("sftp", "upload", "download", "queue")), invoked -> showSidebar.accept(invoked.window()));
        context.actions().register(ActionSpec.of(CANCEL, "Cancel transfer").withIcon(context.appearance().icon(IconName.CLOSE)), invoked -> {
            if (latest != null && latest.activeJobs().size() == 1) coordinator.cancel(latest.activeJobs().getFirst());
            else showSidebar.accept(invoked.window());
        });
        status = context.statusBar().addProgress(new StatusItemSpec(SHOW + ".progress", Side.RIGHT, 65));
        status.setVisible(false);
        timer = autoRefresh ? new javax.swing.Timer(200, event -> refresh()) : null;
        if (timer != null) timer.start();
        refresh();
    }

    /** A strip for {@code window}'s SFTP sidebar; the same queue in every window. */
    public JComponent strip(WindowHandle window) {
        var strip = new TransferStrip(new TransferStrip.Actions((id, action) -> act(window, id, action), coordinator::cancel, id -> dismiss(window, id)));
        strips.put(window.id(), strip);
        refresh();
        return strip;
    }

    public void release(WindowHandle window) { strips.remove(window.id()); }

    void refresh() {
        if (closed || pending) return;
        pending = true;
        coordinator.snapshot(0, PAGE).whenComplete((snapshot, error) -> ui.execute(() -> {
            pending = false;
            if (closed) return;
            if (error != null) {
                String text = message(error);
                status.update(new StatusProgressState("Transfers unavailable", text, text, OptionalDouble.empty(), SHOW, null));
                status.setVisible(true);
                for (var strip : strips.values()) strip.unavailable(text);
                return;
            }
            latest = snapshot;
            long now = clock.getAsLong();
            status.update(presentation.update(snapshot, now));
            var summary = snapshot.summary();
            status.setVisible(summary.runnable() > 0 || summary.paused() > 0 || summary.attention() > 0);
            for (UUID id : finished.expired(snapshot.jobs(), now)) {
                if (clearing.add(id)) coordinator.clear(id, false).whenComplete((ignored, failure) -> ui.execute(() -> clearing.remove(id)));
            }
            var inflight = new HashMap<UUID, Long>();
            for (var sample : snapshot.progress()) inflight.merge(sample.job(), sample.bytes(), Long::sum);
            var jobs = new ArrayList<>(snapshot.jobs());
            jobs.sort(Comparator.comparingLong(TransferJob::createdMillis).reversed());
            var rows = new ArrayList<TransferRows.Row>();
            var ids = new HashSet<UUID>();
            for (var job : jobs) {
                if (clearing.contains(job.id())) continue;
                ids.add(job.id());
                fetchRequest(job.id());
                long done = job.confirmedBytes() + inflight.getOrDefault(job.id(), 0L);
                if (job.totalBytes() > 0) done = Math.min(done, job.totalBytes());
                rows.add(TransferRows.row(job, Optional.ofNullable(requests.get(job.id())), done, speeds.update(job.id(), job.state(), done, now)));
            }
            speeds.retain(ids);
            requests.keySet().retainAll(ids);
            for (var strip : strips.values()) strip.rows(rows);
        }));
    }

    private void fetchRequest(UUID id) {
        if (requests.containsKey(id) || !requesting.add(id)) return;
        coordinator.request(id).whenComplete((request, error) -> ui.execute(() -> {
            requesting.remove(id);
            if (request != null && !closed) requests.put(id, request);
        }));
    }

    private void act(WindowHandle window, UUID id, TransferRows.Action action) {
        switch (action) {
            case RESUME -> report(coordinator.resume(id, window));
            case RETRY_FAILED -> report(coordinator.retry(id, window));
            case RETRY_CLEANUP -> report(coordinator.cleanup(id, window));
            case RESOLVE -> resolve(window, id);
        }
    }

    private void report(CompletableFuture<?> operation) {
        operation.whenComplete((ignored, error) -> ui.execute(() -> {
            if (closed) return;
            if (error != null) context.notices().error(message(error));
            refresh();
        }));
    }

    private void dismiss(WindowHandle window, UUID id) {
        var job = latest == null ? Optional.<TransferJob>empty() : latest.jobs().stream().filter(candidate -> candidate.id().equals(id)).findFirst();
        if (job.isEmpty()) return;
        if (job.get().cleanupPending() == 0) { report(coordinator.clear(id, false)); return; }
        var dialog = context.windows().dialog(new DialogSpec("Clear transfer history", window, false));
        dialog.setContent(new dev.jasper.remote.ui.ConfirmPanel("Unfinished cleanup will be forgotten. Partial files may remain. Clear this history?", "Clear history",
            () -> { dialog.close(); report(coordinator.clear(id, true)); }, dialog::close));
        dialog.show();
    }

    private void resolve(WindowHandle window, UUID id) {
        coordinator.entries(id, 0, 200).whenComplete((entries, error) -> ui.execute(() -> {
            if (closed) return;
            if (error != null) { context.notices().error(message(error)); return; }
            var entry = entries.stream().filter(candidate -> candidate.outcome() == TransferEntry.Outcome.PENDING && !candidate.error().isBlank()).findFirst();
            if (entry.isEmpty()) { report(coordinator.resume(id, window)); return; }
            var target = entry.get();
            var dialog = context.windows().dialog(new DialogSpec("Resolve transfer", window, false));
            dialog.setContent(new ResolvePanel(target, choice -> {
                dialog.close();
                if (choice.decision() == ConflictDecision.RENAME) rename(window, target);
                else resolved(window, target, choice.decision(), null, choice.remaining());
            }, () -> { dialog.close(); report(coordinator.restart(target.id(), window)); }, dialog::close));
            dialog.show();
        }));
    }

    private void rename(WindowHandle window, TransferEntry entry) {
        var dialog = context.windows().dialog(new DialogSpec("Rename destination", window, false));
        var body = new JPanel(new BorderLayout(6, 6));
        body.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        var name = new JTextField(entry.sourceInfo().name(), 24);
        var apply = new JButton("Use name");
        body.add(name, BorderLayout.CENTER);
        body.add(apply, BorderLayout.SOUTH);
        apply.addActionListener(event -> { dialog.close(); resolved(window, entry, ConflictDecision.RENAME, name.getText(), false); });
        dialog.setContent(body);
        dialog.show();
    }

    private void resolved(WindowHandle window, TransferEntry entry, ConflictDecision decision, String name, boolean remaining) {
        coordinator.resolve(entry.id(), decision, name, remaining).whenComplete((ignored, error) -> ui.execute(() -> {
            if (closed) return;
            if (error != null) { context.notices().error(message(error)); return; }
            report(coordinator.resume(entry.jobId(), window));
        }));
    }

    private static String message(Throwable error) {
        while ((error instanceof CompletionException || error instanceof ExecutionException) && error.getCause() != null) error = error.getCause();
        return error.getMessage() == null ? "Transfer operation failed" : error.getMessage();
    }

    @Override public void close() {
        if (closed) return;
        closed = true;
        if (timer != null) timer.stop();
        status.close();
        strips.clear();
    }
}
```

Delete `TransfersPanel.java` and `TransfersPanelTest.java`.

- [ ] **Step 4: Embed the strip in the SFTP sidebar**

`SftpPanel.java` — add the field beside `onLoaded`:

```java
    private final JPanel transfersSlot=new JPanel(new BorderLayout());
```

in the constructor replace `south.add(follow);add(south,BorderLayout.SOUTH);` with:

```java
south.add(follow);south.add(transfersSlot);add(south,BorderLayout.SOUTH);
```

and add after `onLoaded(Runnable)`:

```java
    /** The window's transfer strip, shown at the bottom of the sidebar. */
    public void transfers(JComponent strip) { transfersSlot.removeAll();transfersSlot.add(strip,BorderLayout.CENTER);transfersSlot.revalidate(); }
```

`SftpUi.java`:
- Replace the field `private final Consumer<WindowHandle> showTransfers;` with `private final Function<WindowHandle,JComponent> transferStrip;private final Consumer<WindowHandle> releaseStrip;`.
- In both constructors replace the parameter `Consumer<WindowHandle> showTransfers` with `Function<WindowHandle,JComponent> transferStrip,Consumer<WindowHandle> releaseStrip`, pass both through from the short constructor to the long one, and assign them in place of `this.showTransfers=showTransfers`.
- In `create(PanelHost host)`, directly after `var panel=new SftpPanel(context.appearance()::icon);var window=host.window();` add `panel.transfers(transferStrip.apply(window));`, and change the `host.onClosed(...)` body to begin with `releaseStrip.accept(window);`.
- Add after `toggle(...)`:

```java
    /** Shows the window's SFTP sidebar without choosing a host. */
    public void reveal(WindowHandle window) { if(!closed) showView(window); }
```

- In `background(...)`, replace `else if(owner.isOpen()) showTransfers.accept(owner);` with nothing (the strip is already in the sidebar): the success branch becomes `if(error!=null) context.notices().error(message(error));`.

- [ ] **Step 5: Rewire `RemotePlugin` and remove the toggle**

`RemotePlugin.java`:

```java
        transferUi=new dev.jasper.remote.ui.transfers.TransferUi(context,transfers,ui,window->sftpUi.reveal(window));
        sftpUi=new dev.jasper.remote.ui.sftp.SftpUi(context,ui,transferBackground,connections,endpoints,()->transfers,store::hosts,this::sftpPane,transferUi::strip,transferUi::release);
```

Replace `context.menus().standard(StandardMenu.VIEW).add(SFTP_TOGGLE);context.menus().standard(StandardMenu.VIEW).add(dev.jasper.remote.ui.transfers.TransferUi.TOGGLE);` with `context.menus().standard(StandardMenu.VIEW).add(SFTP_TOGGLE);`, and delete the line `if(shortcuts==null || !next.toggleTransfers().equals(shortcuts.toggleTransfers())) transferUi.configureBinding(next.toggleTransfers().orElse(null));`.

`RemoteShortcuts.java`:

```java
/** User preferences for Remote's navigation actions; the app resolves syntax and conflicts. */
record RemoteShortcuts(Optional<String> togglePanel, Optional<String> openPalette, Optional<String> toggleSftp) {
    static RemoteShortcuts read(PluginConfig config) {
        var table = config.table("shortcuts");
        return new RemoteShortcuts(binding(table, "toggle_panel", "cmd+shift+s"),
            binding(table, "open_palette", "cmd+shift+h"), binding(table, "toggle_sftp", ""));
    }
```

(keep `binding(...)`). In `settings.toml` delete the line `toggle_transfers = ""`.

- [ ] **Step 6: Run the tests to verify they pass**

Run: `./gradlew :jasper-plugin-remote:test`
Expected: PASS, including every existing test.

- [ ] **Step 7: Commit**

```bash
git add -A plugins/remote/src
git commit -m "feat(remote): show transfers in the SFTP sidebar instead of a panel

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 5: Ask before copying, and document the change

**Files:**
- Modify: `plugins/remote/src/main/java/dev/jasper/remote/ui/sftp/SftpUi.java` (`upload`, `download`, `copyHost`, `background`)
- Test: `plugins/remote/src/test/java/dev/jasper/remote/RemotePluginTest.java`
- Modify: `docs/remote.md:175-180,220,228-229`, `docs/remote-7c-verification.md`, `docs/STATUS.md`, the spec's and this plan's status banners

**Interfaces:**
- Consumes: `TransferRequest.withExisting` and `ExistingItems.existing` (Task 1); `ExistingItemsPanel` (Task 3); `SftpUi` after Task 4.
- Produces: no new public API.

- [ ] **Step 1: Write the failing test**

Add to `RemotePluginTest` (it reuses `awaitRowCount` from the session-end test; add the helper `button` below):

```java
    @Test void uploadingOntoExistingFilesAsksOnceAndReplaceCopies(@TempDir Path dir) throws Exception {
        try (var server = new LoopbackServer(); var host = new FakePluginHost()) {
            Path files = java.nio.file.Files.createDirectories(dir.resolve("files"));
            java.nio.file.Files.writeString(files.resolve("readme.txt"), "old");
            server.server.setFileSystemFactory(new org.apache.sshd.common.file.virtualfs.VirtualFileSystemFactory(files.toRealPath()));
            server.server.setSubsystemFactories(List.of(new org.apache.sshd.sftp.server.SftpSubsystemFactory()));
            Path local = java.nio.file.Files.createDirectories(dir.resolve("local"));
            Path readme = java.nio.file.Files.writeString(local.resolve("readme.txt"), "new"), fresh = java.nio.file.Files.writeString(local.resolve("fresh.txt"), "fresh");
            var vault = new FakeVault();
            host.start(FakeVault.INFO, Set.of(), Set.of(), vault);
            RemotePlugin plugin = plugin(dir.resolve("ssh"));
            var context = host.start(INFO, Set.of(), Set.of("dev.jasper.vault"), plugin);
            UUID credential = vault.password("deploy login", "deploy", "s3cret");
            RemoteHost prod = RemoteHost.create("prod", "127.0.0.1", server.port(), "", new Auth.Vault(credential), "", Optional.empty()).withFollowDirectory(false);
            plugin.store().put(prod);
            settle(host);
            new KnownHosts(context.dataDirectory().resolve("known_hosts"), Optional.empty()).trust("127.0.0.1", server.port(), server.hostPublicKey());
            UUID window = host.addTerminalWindow();
            host.activateTerminalWindow(window);
            var panel = (dev.jasper.remote.ui.sftp.SftpPanel) host.openPanel(SftpUi.PANEL, window);
            plugin.openHost(context.terminals().window(window).orElseThrow(), prod); settle(host);
            host.focusTerminalPane(host.terminalPanes().getFirst()); host.flush();
            awaitRowCount(host, panel, 1);

            host.queuePathSelection(List.of(fresh));
            button(panel, "Upload files").doClick();
            awaitRemote(host, files.resolve("fresh.txt"), "fresh");
            assertThat(host.windowContent("dev.jasper.remote", "Items already exist")).as("nothing existed, nothing asked").isEmpty();

            host.queuePathSelection(List.of(readme, fresh));
            button(panel, "Upload files").doClick();
            long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(10);
            while (host.windowContent("dev.jasper.remote", "Items already exist").isEmpty() && System.nanoTime() < deadline) { settle(host); Thread.sleep(20); }
            var ask = (dev.jasper.remote.ui.transfers.ExistingItemsPanel) host.windowContent("dev.jasper.remote", "Items already exist").orElseThrow();
            assertThat(find(ask, javax.swing.JLabel.class).orElseThrow().getText()).isEqualTo("2 of 2 items already exist in prod:" + panel.directory() + ".");
            button(ask, "Replace").doClick();
            awaitRemote(host, files.resolve("readme.txt"), "new");
            host.stopAll();
            assertThat(host.failures()).isEmpty();
        }
    }

    static javax.swing.JButton button(java.awt.Container root, String name) {
        return find(root, javax.swing.JButton.class, candidate -> name.equals(candidate.getText()) || name.equals(candidate.getToolTipText())).orElseThrow();
    }

    static <T> Optional<T> find(java.awt.Component component, Class<T> type) { return find(component, type, candidate -> true); }

    static <T> Optional<T> find(java.awt.Component component, Class<T> type, java.util.function.Predicate<T> match) {
        if (type.isInstance(component) && match.test(type.cast(component))) return Optional.of(type.cast(component));
        if (component instanceof java.awt.Container container)
            for (var child : container.getComponents()) { var found = find(child, type, match); if (found.isPresent()) return found; }
        return Optional.empty();
    }

    static void awaitRemote(FakePluginHost host, Path file, String content) throws Exception {
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(15);
        while (System.nanoTime() < deadline) {
            settle(host);
            if (java.nio.file.Files.isRegularFile(file) && java.nio.file.Files.readString(file).equals(content)) return;
            Thread.sleep(20);
        }
        throw new AssertionError(file + " never held " + content);
    }
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :jasper-plugin-remote:test --tests 'dev.jasper.remote.RemotePluginTest.uploadingOntoExistingFilesAsksOnceAndReplaceCopies'`
Expected: FAIL — no "Items already exist" dialog appears (uploads are queued without a check).

- [ ] **Step 3: Implement the check in `SftpUi`**

Add, next to `background(...)` (compact style, matching the file):

```java
    private record Queued(TransferRequest request,List<String> names) {}
    private void background(Callable<Queued> prepare,WindowHandle owner) {
        background.execute(()-> { try { var queued=prepare.call();var identity=queued.request().destination().identity();ui.execute(()->check(queued,identity,owner)); }
            catch(Exception failure) { ui.execute(()-> { if(!closed) context.notices().error(message(failure)); }); } });
    }
    /** Asks once before copying onto items that already exist; one stat per selected item, off the UI thread. */
    private void check(Queued queued,Optional<ConnectionIdentity> identity,WindowHandle owner) {
        if(closed || !owner.isOpen()) return;
        endpoints.open(identity,owner,status->{}).whenComplete((endpoint,error)-> {
            if(error!=null) { ui.execute(()-> { if(!closed) context.notices().error(message(error)); });return; }
            background.execute(()-> { List<String> existing;
                try { existing=ExistingItems.existing(endpoint,queued.request().directory(),queued.names()); }
                catch(Exception failure) { ui.execute(()-> { if(!closed) context.notices().error(message(failure)); });return; }
                finally { endpoint.abort(); }
                ui.execute(()->decide(queued,identity,existing,owner)); });
        });
    }
    private void decide(Queued queued,Optional<ConnectionIdentity> identity,List<String> existing,WindowHandle owner) {
        if(closed || !owner.isOpen()) return;
        if(existing.isEmpty()) { enqueue(queued.request(),owner);return; }
        String where=identity.map(value->value.host().name()+":").orElse("")+queued.request().directory();
        var dialog=dialog(owner,"Items already exist");
        dialog.setContent(new dev.jasper.remote.ui.transfers.ExistingItemsPanel(dev.jasper.remote.ui.transfers.ExistingItemsPanel.message(existing,queued.names().size(),where),
            choice-> { dialog.close();enqueue(queued.request().withExisting(choice),owner); },dialog::close));dialog.show();
    }
    private void enqueue(TransferRequest request,WindowHandle owner) {
        transfers.get().enqueue(request,owner).whenComplete((id,error)->ui.execute(()-> { if(!closed && error!=null) context.notices().error(message(error)); }));
    }
```

Delete the old `background(Callable<TransferRequest> prepare,WindowHandle owner)` method. Change the three callers to return `Queued` with the selected names:

- `upload`: `background(()-> { var selected=new ArrayList<String>();for(Path path:paths) selected.add(canonicalSelection(path));return new Queued(new TransferRequest(EndpointRef.local(),selected,EndpointRef.remote(target.identity()),target.directory()),selected.stream().map(value->Path.of(value).getFileName().toString()).toList()); },owner);`
- `download`: `background(()->new Queued(new TransferRequest(EndpointRef.remote(source.identity()),paths(source),EndpointRef.local(),directory.orElseThrow().toRealPath().toString()),source.selection().stream().map(FileEntry::name).toList()),owner);`
- `copyHost` destination callback: `background(()->new Queued(new TransferRequest(EndpointRef.remote(source.identity()),paths(source),EndpointRef.remote(destination.identity()),destination.directory()),source.selection().stream().map(FileEntry::name).toList()),owner);`

`ExistingItems` is `dev.jasper.remote.transfer.ExistingItems` (already covered by `import dev.jasper.remote.transfer.*;`); `FileEntry` comes from `dev.jasper.remote.sftp.*`.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :jasper-plugin-remote:test`
Expected: PASS.

- [ ] **Step 5: Document**

In `docs/remote.md` replace the paragraph starting "**SSH > Transfers** or **View > Transfers** opens the global queue" (lines 175–180) with:

```markdown
Transfers appear at the bottom of the SFTP sidebar, in every window: what is copying and where,
a progress bar, speed and time left, and **×** to cancel. A transfer that needs you shows one
button — **Resume** (paused or interrupted), **Resolve…**, **Retry failed** or **Retry cleanup**.
A finished transfer shows "Done" for a few seconds and leaves; failed or cancelled ones stay until
dismissed with ×. The strip hides when nothing is queued. Before copying onto items that already
exist, Remote asks once: **Replace** (files replaced, folders merged), **Skip existing** or
**Cancel**. **SSH > Transfers** shows the sidebar. The status bar displays aggregate progress,
confirmed-data speed and bytes remaining; scanning has an unknown total. Click it to show the
sidebar. Its cancel control cancels the sole active job, or shows the sidebar when several jobs
are active.
```

Delete the `toggle_transfers = ""  # e.g. "cmd+alt+t"` line (line 220), and in the action list at lines 228–229 remove `` `.transfers.toggle` `` (keep `.transfers` and `.transfers.cancel`).

In `docs/remote-7c-verification.md` append after item 10:

```markdown
11. Transfer strip (2026-09-24 amendment): upload a large file and a folder and watch the strip in
    the SFTP sidebar (name → host:folder, percent, speed, time left); cancel one with ×; drop the
    network during one and Resume it; upload onto existing files and choose Replace, then Skip
    existing; a finished upload shows Done and leaves after about five seconds; the strip hides when
    empty, appears in a second window with the same queue, and no Transfers panel exists any more.
```

In `docs/STATUS.md` add after `## Current state — 2026-09-23`:

```markdown
### Transfer strip — 2026-09-24

The bottom Transfers panel (two paged tables, thirteen buttons) is replaced by a compact strip at
the bottom of the SFTP sidebar with one contextual action per transfer, and uploads, downloads and
host-to-host copies ask once before copying onto existing items
([amendment](superpowers/specs/2026-09-24-jasper-remote-transfer-strip-design.md),
[plan](superpowers/plans/2026-09-24-jasper-remote-transfer-strip.md)). The queue, durability and
recovery are unchanged; the "Replace / Skip existing" choice is stored in the job's existing
file/folder policies. A finish whose only issues are skipped items fades like a clean one.
GUI acceptance is item 11 of [remote-7c-verification](remote-7c-verification.md).
```

Set the spec's status banner to "Approved and implemented on `codex/remote-sftp`; GUI acceptance is the user's." and add a status line under this plan's title: "**Status:** Executed on `codex/remote-sftp`", listing any deviations.

- [ ] **Step 6: Full check**

Run: `./gradlew check`
Expected: BUILD SUCCESSFUL. Run the AGENTS.md Python source-hygiene check on the new and changed Java files.

- [ ] **Step 7: Commit**

```bash
git add plugins/remote/src docs/remote.md docs/remote-7c-verification.md docs/STATUS.md docs/superpowers/specs/2026-09-24-jasper-remote-transfer-strip-design.md docs/superpowers/plans/2026-09-24-jasper-remote-transfer-strip.md
git commit -m "feat(remote): ask once before copying onto existing items

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>"
```
