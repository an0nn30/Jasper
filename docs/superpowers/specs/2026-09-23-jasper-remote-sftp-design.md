# Jasper Remote — SFTP phase 1 design

**Status:** Written spec for user review. The workflow, layout, persistent resume and
file-handling decisions below were approved in conversation on 2026-09-23. No SFTP
implementation has started. Written-spec approval precedes the SDD implementation plan.

**Base:** `main` at `1fcf9a8d`; design branch `codex/remote-sftp` in the native worktree
`/Users/dustin/.codex/worktrees/remote-sftp/moray`.

**Authority:** This elaborates plan **7c SFTP** in section 9 of the
[Remote design](2026-09-22-jasper-remote-design.md). It replaces that section's two-pane
browser with the user's single-sidebar workflow, and makes transfer management independent
of Buddy. SSH, tunnels and SFTP remain one bundled plugin, `dev.jasper.remote`. Completed
7a SSH is the prerequisite; 7b tunnels is not a prerequisite.

## 1. Intended outcome and agreed scope

Browse remote files beside a terminal and reliably transfer a large file or a folder with
many files. The user should see what is happening, retain terminal responsiveness, and be
able to pause, cancel, recover after connection loss, and resume after restarting Jasper.

The interaction reference is the user's MobaXTerm screenshots: one compact remote file
explorer, a path bar, ordinary file rows and a small toolbar. Use Jasper's own modern/retro
skins, semantic icons, UI font family and UI font size. There is no second permanent file
browser and no custom card-based file list. The user declined browser mockups.

Approved decisions:

1. Local-to-remote and remote-to-local use local source/destination pickers. Remote-to-remote
   uses **Copy to host…**, then a saved-host and remote destination-folder picker.
2. Remote-to-remote bytes stream through Jasper; a complete local staging copy is not made.
3. A left SFTP sidebar follows the active SSH pane. **Follow terminal folder** defaults on;
   manual navigation turns directory following off. Local panes leave the last remote view.
4. A bottom Transfers panel owns the visible queue. A status-bar progress control shows
   speed and remaining bytes and provides cancellation. Buddy is optional supplementary UI.
5. Jobs outlive hidden panels and closed terminal tabs. Queue state and partial-file progress
   survive application restart. Restored unfinished jobs are **paused**, never auto-started.
6. Pause retains partial files. Cancel removes owned temporary files where possible and
   retains completed destination files. Cleanup failures remain visible.
7. Copy directories recursively; preserve links as links without traversing them. Report
   unsupported links/special files. Preserve timestamps and ordinary permissions when possible.
8. Conflicts ask Replace/Skip/Rename for files or Merge/Skip/Rename for folders, with a
   scoped apply-to-remaining choice. Merge never removes unrelated destination children.
9. Detectable source/partial-destination changes require attention before resuming. Failed
   items remain retryable and cannot make a folder job appear wholly successful.

Not included: remote editing, synchronization/mirroring, moving files between hosts,
server-to-server shell commands, SCP fallback, tunnels, ownership/ACL/xattr replication,
sparse-file preservation, or automatic unattended reconnect/resume. File contents are not
snapshotted: copying files that other programs continually modify is not a backup system.

## 2. Browser and entry points

### 2.1 One remote browser

Register `dev.jasper.remote.sftp.panel`, title **SFTP**, default anchor LEFT. Existing
panel-region behavior means showing it replaces SSH Hosts in that region; either remains
available through the SSH/View menus. Other anchors remain user-selectable through the app.
Opening an SSH terminal does not force a hidden SFTP panel open.

The panel has a host header, editable absolute/relative path field, compact toolbar, file
list and Follow terminal folder checkbox. Directories sort before files, then by name.
Show hidden entries by default, as in the reference. Rows have native-style file/folder/link
icons, selection and modest padding; the name gets priority in a narrow panel. Size and
modification time appear when width permits and in item details/tooltips otherwise.
Paths and filenames are rendered as plain text, never Swing HTML.

Toolbar and context operations:

| Action | Behavior |
| --- | --- |
| Up | Browse the parent directory; disabled at the server's root. |
| Upload | Select local files; its menu also offers Upload Folder. Copy into the captured displayed remote directory. |
| Download | Choose an existing local destination directory for selected remote files/folders. |
| Refresh | Reload the displayed directory without changing following mode. |
| New Folder | Enter one child name and create it; existing names produce an inline error. |
| Delete | Confirm host, path and selection; recursively delete directories without following links. |
| Copy Path | Copy absolute remote paths to the clipboard, newline-separated for multiple selections; never inject them into a terminal. |
| Copy to host… | Context action: choose a saved host, browse one destination-folder list, then enqueue the selected sources. |

Enter/double-click opens a directory. File activation only selects it; phase 1 does not open
an editor or execute files. Arrow keys, multi-selection, select-all, and an accessible context
menu work normally. New Folder rejects separators, NUL, empty names, `.` and `..`.

Register **Open SFTP here** (`dev.jasper.remote.sftp`) in SSH, the command palette and the
terminal context menu; on a Remote-owned pane it uses that pane's endpoint and reported path.
The Hosts context menu adds **Browse files**, which can open SFTP without creating a shell.
With no associated pane/host, the command offers the saved-host picker. View includes SFTP
and Transfers toggles. The existing Sessions toolbar remains a host-session menu.

Showing a browser explicitly focuses its file list after loading. Empty/loading/error states
do not steal focus when an unrelated terminal is active. Busy directory navigation stays in
the panel, keeps the last usable listing, and is cancellable. Initial authentication uses the
existing SSH trust/Vault flows; opening SFTP never creates a placeholder terminal tab.

### 2.2 Following and captured intent

One view model per terminal window tracks the active Remote pane, remembered path and follow
flag per pane; standalone host browsing has its own window/host state. React to pane focus,
tab selection and directory events for that window, not just whichever window is globally active.

Use the Remote plugin's pane-to-connection association to identify the endpoint. A reported
`remoteDirectory` is only a path hint; it must never select a different host or become a local
`Path`. Missing directory reports fall back to the SFTP user's home. An unavailable path
leaves the last successful listing and an inline message. Nested SSH inside a shell is not
a discoverable new Remote endpoint. No commands are injected to obtain a directory.

Manual path entry, Up and opening a directory turn following off for that pane. Refresh
does not. Re-enabling follows the latest reported path immediately. Switching to a local
pane leaves the last remote view and identifies the host in the header. Hiding the panel
does not reset its state. Returning to another remote pane restores that pane's browsing state.

Every picker, confirmation and queued job captures endpoint identity, path and selected items
at invocation. Tab switches and later navigation cannot redirect a transfer or deletion.
Directory responses carry a generation token; obsolete results cannot replace a newer listing.

### 2.3 Destination picker and deletion

Copy to host presents a saved-host selector, current destination path, one directory list,
Up/Refresh/New Folder, and **Copy here**. The source is a fixed summary, not a second browser.
Selecting a host connects through its saved authentication/ProxyJump configuration. Closing
the picker cancels its browse request and releases its lease without enqueueing a copy.

Deletion is a separate background operation, with visible progress and Cancel. Cancelling
stops further deletions; completed deletions cannot be undone. It has no restart/resume queue
and is never restarted automatically. Link deletion removes only the link. Errors list the
affected paths. Overlapping active-transfer paths in this Jasper process cannot be deleted
until the affected transfers are stopped. Copying into one's own source subtree or onto the
same file is rejected when endpoint/canonical-path identity can establish the overlap.

## 3. Transfer management and progress

### 3.1 Shared queue, independent views

Register `dev.jasper.remote.transfers.panel`, title **Transfers**, default anchor BOTTOM.
`dev.jasper.remote.transfers` is an always-available **Transfers** show action in SSH and
the command palette. Status clicks show it rather than toggling an already-open panel off.
Showing uses the existing lazy-panel pattern (toggle only to create, then `PanelHost.show`).

The queue belongs to the plugin, not a window, pane or Buddy activity. Each window renders
a paged view of that queue. Jobs display source and destination identities, current file(s),
byte/file progress, speed, state and actions. Folder expansion loads child rows in pages;
large jobs never manufacture one Swing component per entry. A details area shows per-file
errors, conflicts and cleanup work. Clearing completed history never deletes destination files.

State names are explicit: Queued, Scanning, Connecting, Transferring, Pausing, Paused,
Checking partial file, Needs attention, Interrupted, Cancelling, Cancelled, Completed,
and Completed with issues. No remaining runnable work plus failed/skipped/metadata-warning
entries yields Completed with issues; a filtered count explains each category. Retry selects
failed/interrupted items; it does not silently include deliberately skipped items.

Pause/Resume/Cancel apply to a whole job. Folder details also offer Retry for failed files.
Conflict resolution is available in the row/details even if a prompt's original window closes.
Independent files may continue while one awaits a collision decision. Jobs sharing the same
canonical destination path are serialized within this process.

### 3.2 Status bar

One aggregate status item is rendered in every app window: progress bar, active-job count,
smoothed payload speed and remaining bytes. Count destination-confirmed bytes once, including
for remote-to-remote transfers; do not double-count the read and write legs. Use 64-bit byte
counts. Speed excludes scan/resume-validation reads and resets after a pause.

While any active job is still scanning, display indeterminate progress and **Scanning…**,
with discovered counts; unknown totals must not look like zero or a reliable percentage.
After discovery, show remaining bytes and files. Skipped/failed bytes are reported separately
from transferred bytes. An interrupted or failed job never makes aggregate completion green.
When no jobs run, show paused/interrupted/attention counts until handled or cleared.

Clicking the main item opens Transfers. The separate cancel affordance cancels the sole
active job; if several are active it opens Transfers for selection. It never silently cancels
all jobs. On narrow windows keep the progress indicator/count and controls usable, elide the
secondary text, and retain complete information in the tooltip and accessible description.

Update progress at most five times a second; state transitions are delivered promptly.
The app updates the existing progress component rather than rebuilding the status bar on
every byte sample. Theme/font changes still update all controls. Buddy may mirror one activity
per running job attempt, but absence/failure of that mirror cannot affect job state or controls.

## 4. Architecture and connection ownership

All SFTP behavior stays under `plugins/remote`, with these focused responsibilities:

| Package/component | Responsibility |
| --- | --- |
| `client` connection leases | Reuse authenticated SSH transports, own references/cancellation, expose endpoint snapshots to plugin internals. |
| `sftp` endpoints | Local and SFTP file operations, path/metadata values, bounded directory iteration and copy streams. |
| `transfer` coordinator | Job state machine, bounded scheduling, conflicts, pause/cancel, resume and publication. |
| `transfer.store` | Durable queue, checkpoint/commit records, discovery frontier and paged queries. |
| `ui.sftp` | Browser, destination picker and file-operation dialogs/controllers. |
| `ui.transfers` | Paged transfer views and aggregate presentation. |
| `RemotePlugin` | Composition, actions, panel registration and lifecycle; no transfer loop or SQL. |

`FileEndpoint` has two real implementations, local NIO and SFTP. Remote paths remain protocol
strings and typed endpoint locations, not local filesystem paths. MINA/vendor values remain
inside Remote. SDK/app boundaries stay unchanged except the generic UI additions in section 9.

Add `org.apache.sshd:sshd-sftp:2.19.0`, matching current sshd-core. Factor cancellable connection
acquisition from `Connections.shell` into an internal owned lease; shells, directory browsers
and transfer workers consume leases. Preserve the existing SSH authentication/trust, Vault
credential disposal, ProxyJump references, linger and failure/cancellation regressions.

Each active worker owns separate SFTP subsystem channels on leased shared sessions. Browsing
uses its own channel, so cancelling/closing a transfer cannot interrupt a shell or browser.
There is no concurrent unsynchronized use of one SFTP client. Pause releases worker channels
and leases after quiescing; resume reacquires. Hidden browsers release their browse resources
and refresh on showing. Closing a terminal tab does not close a transfer-owned reference.

Lease results carry the actual authenticated endpoint snapshot. Extend the pane association
to retain it; a host edit cannot make Open SFTP here silently browse another machine under an
old host id. Session reuse checks connection-defining identity, not host id alone. For a
changed definition while an old session is alive, keep the old lease valid and acquire the
new definition separately; do not evict existing shells merely to edit a saved host.

Queue records hold host ids plus endpoint snapshots (hostname, port, effective username,
authentication references and resolved jump chain), never passwords/private keys/passphrases.
Resume compares saved connection-defining fields to the current store. Missing/changed hosts
require attention; credential material may rotate behind an unchanged Vault reference and
normal current trust/authentication still applies. Never silently rebind jobs to another host.
Compare the acquired lease's effective username and endpoint to the recorded identity before
file operations, including when a Vault login's username changed behind its credential id.

Connection and trust prompts have a requesting window and cancellation owner. Serialize
modal prompts per owner and deduplicate shared-session acquisition; one cancelled requester
must not cancel another requester's transport. If the owner closes, unfinished transfers
remain in the queue awaiting Resume from a live window. Restoring the queue causes no login.

## 5. Bounded discovery, streaming and file semantics

### 5.1 Scheduling and responsiveness

Use `context.background()` with explicit bounded admission; its virtual threads are not a
license to create one worker/future per file. Defaults: two concurrent file copies globally,
at most two per endpoint, one directory-scanning worker, and a bounded control lane for
cancellation and persistence that cannot be starved by payload workers. The scheduler takes
small batches from the durable queue and gives runnable jobs fair turns.

Use pipelined, bounded SFTP I/O rather than one network round trip per tiny chunk. Target at
most 8 MiB of payload buffers and in-flight data per active copy, including both legs of a
remote relay; maximum configured copy concurrency is eight. Apply backpressure when the
destination stalls. File offsets, totals and checkpoints are `long`; no whole-file byte arrays.
The plan must verify MINA's acknowledgement and buffering behavior instead of treating a
successful stream `write`/`flush` as proof of a durable completed remote write.

Directory scanning iterates pages without materializing complete trees; persist a discovery
frontier and unique relative-path records in bounded batches. It can enqueue discovered files
before the whole tree is scanned. Directory re-enumeration after restart is idempotent and
must not duplicate tasks. Completed directory listings are not automatically rescanned to
include newly created files: this is a copy job, not continuous synchronization.

Browser listings also spool large directories to a separate disposable local cache and expose
bounded pages, with stable folders-first sorting. This cache has its own process-owned temporary
database, independent of the durable queue lock/schema; queue damage cannot remove browsing.
If the browser cache itself cannot be written, show a listing error rather than fall back to
unbounded memory. Progress, directory reads, SQL, hashing, deletion and file I/O never
run on the EDT. No global lock is held during network I/O or while awaiting a UI decision.

### 5.2 Copy rules

Copy a selected directory as a named child of the chosen destination, preserving its
relative structure and empty subdirectories. Deduplicate selections where an ancestor already
contains a selected child. Validate directory-entry names before joining them to destinations;
reject traversal entries, NUL, separators in a single component, unsupported local names and
case-insensitive destination collisions. Report errors without lossy filename rewriting.

Copy symbolic links using their stored link text. Never recursively traverse a link during
copy or delete. Preserve dangling links if the destination supports them; an unsupported link
becomes an explicit issue. Device files, sockets and FIFOs are skipped with an issue, never
opened as byte streams. User navigation may enter a linked directory deliberately, with its
resolved path reflected in the browser; this does not change recursive-copy rules.

Preserve modification time and ordinary rwx permissions where supported; never copy setuid,
setgid or sticky bits, owner/group, ACLs or extended attributes. Permissions unsupported by
an endpoint use that endpoint's defaults and produce a visible metadata warning. Apply
directory metadata after its children, so restrictive permissions do not prevent population.

Existing file/link conflicts offer Replace, Skip or Rename. Existing directory conflicts
offer Merge, Skip or Rename; Merge keeps unrelated children. A file/directory type mismatch
offers Skip or Rename, not recursive destructive replacement. Apply-to-remaining is scoped
to the job and conflict category. Rename validates one new sibling name and rechecks existence.
There is no overwrite default and no implicit destination deletion to make a rename succeed.

### 5.3 Temporary files and publication

Create a unique, job-owned sibling temporary file with exclusive creation, using an opaque
job/file id rather than appending to a potentially maximum-length original filename. Record
the intended temp path before creating it. Copy into that temp, validate expected length and
source metadata, finish acknowledged writes, apply metadata, then publish with a rename.

For replacement, require a supported atomic replacement primitive (local atomic move or
negotiated SFTP atomic/POSIX rename). If unavailable, leave the original intact and mark
Needs attention with Rename/Skip alternatives. For new targets use no-overwrite publication;
if the endpoint cannot guarantee that, report the limitation instead of risking replacement.
Recheck the destination against the conflict decision before publication; a newly detected
change requires another decision. SFTP cannot provide a universal compare-and-swap against
external concurrent writers, so concurrent mutation of the same target is not transactional.

Maintain a write-ahead **publishing** record with temp/final paths, byte count, digest and
replacement decision before rename; record completion afterward. On recovery from a crash
between these steps, reconcile the exact paths and verify final content before concluding
completion. Ambiguous results require attention; never re-overwrite or delete blindly.
Cancelling during publication resolves that in-flight outcome before reporting Cancelled.
An already-published file is completed work and remains in place.

## 6. Pause, resume and interruptions

Pause and Cancel first persist intent, stop admitting work, signal workers and close only
their owned channels as necessary. Display Pausing/Cancelling until mutation has stopped;
do not display Paused while writes are still in flight. Normal controls react within 100 ms
of EDT processing; a responsive fixture must stop payload progress within two seconds.
Hung requests are bounded by the configured request timeout and forced channel closure.
Filesystems or peers that remain unresponsive are reported, not waited on by the UI.

Pause keeps completed files and known partials. Cancel removes only verified job-owned
temporary files, never destination files or unrelated temporaries; created directories remain.
Failure to reach the host leaves cleanup records and visible **Cleanup pending** details.
Cleanup Retry is explicit and uses normal authentication. Restart never logs in to clean up.

Persist checkpoints only through the highest contiguous acknowledged destination offset;
local checkpoints require forcing the partial file before committing the checkpoint. Store
SHA-256 digests of bounded prefix segments (8 MiB segments, plus a final short segment on
pause). Checkpoint at segment boundaries and job state changes. Uncheckpointed tail data may
be re-read/retransmitted after interruption and is not counted as durable progress.

Before any resume, including within the same run:

1. Revalidate endpoint definitions and acquire leases through current trust/authentication.
2. Revalidate source type, size and modification time. Changed or unavailable metadata makes
   the item Needs attention (Restart this file or Skip); no blind append.
3. Read the checkpointed prefix of source and partial destination in bounded segments and
   compare it against the recorded digests. Present **Checking partial file…**, cancellable
   and distinct from transfer speed. Validation can take substantial time for a large partial.
4. If every segment matches, discard only an uncheckpointed tail in the owned temp and resume
   at the verified offset. Missing/short/changed partials require Restart or Skip.
5. Recheck source metadata before publication. Detectable changes leave the temp and original
   destination intact for attention. Metadata plus prefix checks do not create a snapshot of
   concurrently rewritten source bytes; that limitation is documented.

Restart explicitly replaces the owned partial with a fresh temp and resets that file's
checkpoint; it does not reset completed siblings. Resume/retry never retransfers completed
files merely because the rest of the folder was interrupted. Deliberate skipped items stay
skipped unless the user explicitly retries them.

Connection loss changes affected work to Interrupted and releases its leases. Other jobs
on healthy endpoints continue. A transient SFTP channel error does not close a shared shell
transport. Vault locking does not invalidate already-authenticated channels. Current access
denial, missing credentials, disk full and persistence failures are shown separately from
network interruptions; no busy reconnect/retry loops.

## 7. Durable queue and recovery

Use one plugin-owned SQLite database at `data/transfers/queue.sqlite`, with indexed job,
entry, directory-frontier, checkpoint and pending-cleanup records. This is a design choice
for bounded queries and transactional transitions instead of rewriting a giant TOML file
or implementing a custom crash-recovery journal. Bundle Xerial SQLite JDBC in Remote only;
SDK, Buddy and terminal gain no database dependency. Pin the verified driver release in the
implementation plan and exercise its real plugin-classloader/native-library path in tests.

Open the database asynchronously through plugin-owned driver objects, with a single serialized
store worker, bounded request batches and limited cached rows. Use WAL and FULL synchronous
mode, bounded cache/checkpoint settings, foreign keys and schema versioning. DB acknowledgements
are prerequisites for dispatch and checkpoint claims. The UI receives immutable snapshots;
it never executes SQL. Do not retain plugin driver registrations after shutdown.

A lifetime lock on `data/transfers/queue.lock` allows only one process to own this queue.
Another Jasper process sharing that home shows **Transfers managed by another Jasper instance**
and cannot enqueue/resume/clean up these jobs; ordinary SSH and browsing remain usable. This
avoids two independent applications writing the same partials. Distinct homes have distinct
queues; cross-process external file mutations remain subject to normal conflict checks.

Startup reads queue metadata but performs no remote I/O. Nonterminal jobs restore as Paused
with their previous interruption/attention reason retained; cancellation intent restores as
Cancelled with cleanup pending rather than becoming resumable. Completed history remains.
Publishing records are reconciled only after the user chooses Resume or Cleanup Retry and
authenticates. For a cancelled job, reconciliation records any already-published file as
completed work and cleans only remaining owned temporaries; it cannot restart payload copying.

Persist only paths, host/credential references, metadata, digests, outcomes and progress. Use
private local permissions where supported; never log credentials or store file payloads in
the queue. If the queue is corrupt, too new, locked or unavailable, preserve its files, show
a persistent diagnostic and disable transfer mutation; do not erase/reset it automatically.
SSH and SFTP browsing remain available. Clearing a job with unfinished cleanup requires an
explicit acknowledgement that the listed temporary files will remain; never silently lose
the only record of their locations.

The queue survives normal shutdown without relying on a last-second save: state transitions
and checkpoints are incremental. `stop()` stops admission promptly, enqueues bounded cleanup
on already-owned workers before the host executor closes, and never blocks the EDT. Workers
release channels/leases/store resources in `finally`; shutdown ordering keeps the store open
until workers stop publishing. Abrupt termination recovers from the last committed state.
Closing the last application window follows normal Jasper lifetime rules; transfers do not
silently turn the application into a headless daemon.

## 8. Configuration

Add documented defaults under the existing plugin TOML, never the app's config:

```toml
[sftp]
max_parallel_files = 2       # 1..8, global; running workers finish before a lower cap takes effect
request_timeout_seconds = 30 # 1..300; applies to new operations

[shortcuts]
toggle_sftp = ""             # optional, unbound by default
toggle_transfers = ""        # optional, unbound by default
```

Existing Remote shortcut settings remain intact. Global app keybinding overrides keep their
current precedence. Following state and remembered locations are UI state, not connection
settings; pane ids need not survive restart. Queue/durable checkpoints live only in the DB.
Invalid settings retain documented defaults and report through the existing config mechanism.

## 9. Generic SDK changes

Bump SDK to **0.7.5** and Remote's minimum accordingly. Existing ordinary status items and
single-file `WindowSurface.chooseFile` remain supported. Implement every addition in both
the app bridge and testkit, with lifecycle/ownership/threading contracts. App-native models
carry translated values outside `dev.jasper.app.plugins`; no SDK types enter workspace code.

### 9.1 Status progress

Add a dedicated status-progress contribution through `StatusBar`, using the existing
placement spec and plugin-owned action ids. A single immutable update carries label,
determinate fraction or indeterminate state, detail/tooltip, accessible description and
optional secondary-action id. The primary action opens details; Remote's secondary action
implements the single-job/multiple-job cancellation behavior. Ordinary text-only items
continue to use the existing `StatusItem` API.

The host owns the progress bar and buttons, validates fraction/action ownership, handles
theme/font/width changes and removes the contribution on plugin teardown. UI-thread mutation
and idempotent close mirror current status contracts. Stable components and coalesced updates
avoid repeated revalidation of all status controls during transfer. Testkit captures values
and actions so Remote's behavior is testable without constructing an application window.

### 9.2 File and directory selection from a panel

Add owner-based chooser methods to `Windows`: existing-file multiselection and single existing
directory selection, each taking `WindowOwner`, a title and optional initial local path.
Return normalized absolute local paths, or an empty selection on cancellation/owner closure.
The terminal window can own these directly; no empty helper dialog is required.

The application provides platform file/directory choosers, using the native picker where
supported and its standard themed directory chooser otherwise. Upload chooses multiple files;
Upload Folder chooses one directory; Download chooses one directory. These calls use the
existing UI-thread/native-modal event-pumping convention and dispose their chooser. Closing
the owner/plugin invalidates the result. No file content is read by selection. Testkit queues
selections and can simulate owner closure during selection.

Semantic icons for the browser/status controls use `Appearance` and the existing modern/retro
mapping. Add missing icon names only where a control has no suitable existing semantic icon.
There is no SFTP-specific SDK service and no exported Remote cross-plugin API.

## 10. Validation and delivery shape

The implementation plan will use SDD: focused implementer tasks with spec-compliance and
code-quality review, fix rounds on the same task, then one final integrated review. This is
one SFTP phase with ordered, testable milestones; internal layers can land incrementally,
but the user-facing phase is not complete without persistent resume and transfer controls.

Suggested dependency order for the plan:

1. SDK progress and chooser contracts, real host/testkit implementations and documentation.
2. Shared-session leases and endpoint snapshots, preserving SSH behavior.
3. Local/SFTP endpoint adapters and loopback SFTP fixture.
4. Durable queue, state machine and restart/publication recovery.
5. Bounded discovery/copy/resume scheduler, collision and cleanup behavior.
6. Browser/destination UI and terminal following.
7. Transfer views/status/actions/settings, then lifecycle integration and release verification.

Verification requirements:

- **Contracts:** SDK ownership, wrong-thread calls, shutdown, stale picker results, progress
  validation, narrow status bars, accessibility, modern/retro and configured typography.
- **SSH regression:** shell/session reuse, host-definition edits, Vault and agent authentication,
  shared-request cancellation, closing a shell while SFTP continues, and SFTP through ProxyJump.
- **Real loopback SFTP:** all three directions, binary/empty/>4-GiB logical streams, directories,
  symlinks, special entries, Unicode/long names, denied access, existing destinations and
  supported/unsupported rename capabilities. Validate hashes and exact resulting paths.
- **Durability fault injection:** terminate between each intent/write/checkpoint/publish/complete
  boundary, reopen the real store, resume paused jobs and reconcile without duplicate or
  destructive publication. Include a separate-process crash test and same-home lock contention.
- **Resume correctness:** changed same-size prefixes, changed source metadata, partial truncation,
  extra uncheckpointed tails, missing temporary files, stale endpoints and missing credentials.
- **Controls:** pause/cancel scanning, blocked read/write, connection/auth prompts, resume validation
  and publication; no late progress or mutations after a terminal state, no unrelated shell closure.
- **Scale:** a generated 100,000-entry manifest plus paged browser/queue reads, and a >4-GiB
  generated stream in a bounded-heap test process. Assert worker/buffer/page bounds and cancellation
  responsiveness rather than flaky machine-specific throughput thresholds.
- **UI responsiveness:** delayed endpoint/store fixtures demonstrate EDT heartbeat/input processing
  throughout scan/copy/checkpoint/recovery; progress events stay coalesced and disposed views stop
  receiving updates. Headless rendered layouts cover narrow windows and larger UI fonts.
- **Build:** `./gradlew check :jasper-app:installDist`, architecture guards, compiled SDK examples,
  plugin packaging with the real SFTP/SQLite dependencies, source hygiene and diff checks.
- **Native acceptance, user-run:** local upload/download pickers; two real remote hosts; one large
  file and one many-file folder; pause, restart, explicit resume and cancellation; follow-directory
  behavior; status/Transfers controls with Buddy absent. No unattended GUI or GUI benchmark launch.

Measure loopback throughput and an artificially delayed endpoint in a headless diagnostic to
catch accidental stop-and-wait I/O, but do not promise an absolute network transfer speed.
No tests may connect to the user's saved hosts, use their Vault secrets or delete user files.

## 11. Evidence and self-review

Repository checks: current `Connections` owns references privately and exposes only shell
creation; it needs a lease seam. `StatusBar` currently creates text/icon `StatusItem`s only.
`WindowSurface.chooseFile` currently accepts one file and requires a shown auxiliary surface.
Panels already support LEFT/BOTTOM, per-window lifetime and lazy instances. The SDK already
exposes pane activation and remote-directory events. No database is currently used by Remote.

Primary references checked for feasibility (the implementation plan must pin APIs and tests):

- [MINA 2.19.0 SftpClient](https://raw.githubusercontent.com/apache/mina-sshd/sshd-2.19.0/sshd-sftp/src/main/java/org/apache/sshd/sftp/client/SftpClient.java):
  offset-based reads/writes, incremental directory reads, link metadata and rename options.
- [MINA asynchronous output](https://raw.githubusercontent.com/apache/mina-sshd/sshd-2.19.0/sshd-sftp/src/main/java/org/apache/sshd/sftp/client/impl/SftpOutputStreamAsync.java)
  and [POSIX rename extension](https://raw.githubusercontent.com/apache/mina-sshd/sshd-2.19.0/sshd-sftp/src/main/java/org/apache/sshd/sftp/client/extensions/openssh/OpenSSHPosixRenameExtension.java):
  pipelined writes require explicit attention to acknowledgements; replacement depends on server capabilities.
- [SQLite JDBC](https://github.com/xerial/sqlite-jdbc), [SQLite WAL](https://www.sqlite.org/wal.html)
  and [synchronous settings](https://sqlite.org/pragma.html#pragma_synchronous): bundled driver,
  transactional local persistence and FULL-mode durability settings.

Self-review: the single browser supersedes the old two-pane outline; queue controls do not
depend on Buddy; restoring jobs causes no network activity; durable offsets exclude unacknowledged
writes; crash-between-rename-and-recording has a recovery state; large folders have disk-backed
discovery and paged UI; cancellation is scoped to owned resources; old SSH leases survive host
edits; SDK changes remain generic and have app/testkit implementations. No implementation code
or dependencies were changed while writing this spec.
