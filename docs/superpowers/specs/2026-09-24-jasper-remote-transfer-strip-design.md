# Jasper Remote — transfer strip in the SFTP sidebar

**Status:** Approved and implemented on `codex/remote-sftp`; GUI acceptance is the user's.

**Base:** `codex/remote-sftp` at `e60d9979`.

**Authority:** Amends §3 (and the Transfers entries of §1 and §2) of the
[SFTP phase 1 design](2026-09-23-jasper-remote-sftp-design.md). The queue, its durability,
pause/cancel/resume semantics, recovery, cleanup and the status-bar item (§3.2, §5–§7) stand.

## 1. Problem and intended outcome

The bottom **Transfers** panel is two paged tables (jobs, then the selected job's files) with
thirteen always-visible buttons, a preferred height of 320 px, and empty grids with disabled
buttons when nothing runs. The user cannot tell what is going on from it and does not want it
as its own panel.

In the user's words, Transfers should only "track the progress of something being uploaded"
with "an ability to cancel", placed "in the same pane as the SFTP pane at the bottom". Jasper
is not a full SFTP client; the user needs "just enough to let me know how the upload is going
and that I can resume/cancel an upload".

Success criteria:

1. While a copy runs, the SFTP sidebar shows what is being copied, where, how far along, how
   fast and how long is left, with a way to cancel it.
2. A paused or interrupted copy offers Resume; nothing else takes screen space unless it applies.
3. When nothing is queued, transfers take no space at all.
4. The durable queue and its recovery behave exactly as today.

## 2. Design

### 2.1 The transfer strip

Each window's SFTP sidebar (`SftpPanel`) gains a **Transfers** strip at its bottom, below the
file list and the Follow terminal folder checkbox, separated by a thin titled rule. The strip
is hidden when the queue has no visible transfers. It shows up to three transfers and scrolls
beyond that, newest first. Every window's sidebar shows the same plugin-owned queue.

Each transfer occupies two lines:

```
↑ report.pdf → prod:/srv/app                 ×
  ▓▓▓▓▓▓▓░░░░░ 58% · 4.1 MB/s · 12 s left
```

- **Line 1:** a direction arrow (↑ to a remote host, ↓ to this machine, ⇄ host to host), what
  is copied (the item's name, or "*name* and N more" / "*folder* (N files)"), an arrow, and the
  destination as `host:directory` (or the local directory), elided in the middle when narrow.
  The full source and destination are in the tooltip and accessible description.
- **Line 2:** a thin progress bar and one status text:
  - Copying: `58% · 4.1 MB/s · 12 s left` (percent of confirmed bytes, smoothed speed, time
    left; time left is omitted until a speed is known).
  - Scanning: `Scanning… N files found`, with an indeterminate bar.
  - Other states use the existing names: `Queued`, `Connecting…`, `Checking partial file…`,
    `Pausing…`, `Paused`, `Cancelling…`, `Interrupted — <reason>`, `Needs attention —
    <reason>`, `Done`, `Done · N failed · M skipped`, `Cancelled`, `Failed — <reason>`.
- **Actions:** at most one text button, and only when it applies:

  | Transfer state | Button |
  | --- | --- |
  | Paused, Interrupted | **Resume** |
  | Needs attention | **Resolve…** |
  | Completed with issues | **Retry failed** |
  | Cleanup pending (any finished state) | **Retry cleanup** (takes precedence) |

  plus **×** at the end of line 1: it cancels an unfinished transfer (the existing Cancel
  semantics, never silently more than that transfer) and dismisses a finished one.
- **Finishing:** a transfer that completes without issues shows `Done` for about five seconds,
  then leaves the strip and the queue's visible history (clearing history never deletes
  destination files). Completed-with-issues, cancelled and failed transfers stay until
  dismissed with ×. Dismissing a transfer with cleanup pending asks first, as Clear does today.
- **No Pause button, per-file table or paging.** Pause remains available internally (status
  cancel semantics, app shutdown, interruption); a paused transfer from an earlier run is resumed
  with Resume.
- **Another instance owns the queue:** the strip shows one line, "Transfers are managed by
  another Jasper instance", and no transfers.

The strip updates from the existing coordinator snapshots at most five times a second, reusing
the status bar's smoothed-speed calculation per transfer. It never builds one component per
file; it holds at most the transfers it shows.

### 2.2 Ask before copying

Before Upload, Upload folder, Download or Copy to host enqueues a transfer, Remote checks, off
the UI thread, whether any selected item already exists in the destination directory (one
`stat` per selected top-level item, through the destination endpoint). If none exists, the
transfer is queued as today. If any exists, one dialog asks:

> **3 of 5 items already exist in prod:/srv/app.**
> [Replace] [Skip existing] [Cancel]

- **Replace** queues the transfer with the job's conflict policy set to replace files and merge
  folders.
- **Skip existing** queues it with the policy set to skip existing files and merge folders.
- **Cancel** queues nothing.

The policy is part of the enqueued request and recorded as the job's file and folder policies
before any work is admitted, so conflicts found deeper inside merged folders follow it without
asking. Only a conflict the policy does not cover — for example a destination that changed
between the check and the copy, or a file where a folder is expected — stops the transfer as
**Needs attention**. **Resolve…** then opens a small dialog naming the file, with Replace /
Skip / Rename (and Merge for folders) and "Apply to the rest of this transfer", backed by the
existing per-entry resolution.

### 2.3 Status bar, actions and removals

- The status-bar progress item (SFTP design §3.2) is unchanged, except that clicking it shows
  the SFTP sidebar of that window (creating it if needed) instead of the Transfers panel. Its
  cancel affordance keeps its rules; with several active transfers it shows the sidebar.
- The **Transfers** show action (`dev.jasper.remote.transfers`) stays in the SSH menu and command
  palette and shows the SFTP sidebar.
- Removed: the `dev.jasper.remote.transfers.panel` panel, the **Transfers panel** toggle action
  (`dev.jasper.remote.transfers.toggle`) and its View-menu entry, the `toggle_transfers`
  shortcut setting (a leftover key in a user's settings file is ignored), and the per-file
  details view.

### 2.4 Components

- `TransferStrip` (new, `ui.transfers`): the strip's Swing component and its rows, given
  immutable job snapshots and action callbacks. Pure presentation.
- `TransferUi` (reshaped): no longer registers a panel. It owns the status item, the refresh
  timer, per-transfer speed smoothing, the "Done" fade timing and the action wiring, and feeds
  every window's strip. `SftpUi` adds the strip to each `SftpPanel` it creates.
- `TransferRequest` gains the chosen conflict policy (none, replace, skip existing); the
  coordinator records it when it enqueues. `TransferCodec` persists it with the job.
- `TransfersPanel` is deleted.

## 3. Testing

Automated (headless):

1. `TransferStrip`: hidden when empty; line texts for copying, scanning, interrupted, needs
   attention, done-with-issues and cancelled; the one action per state from the table, with
   cleanup taking precedence; × cancels unfinished and dismisses finished; at most three rows
   visible; long names elided with full text in the tooltip.
2. `TransferUi`: a completed transfer is removed after the fade delay (manual clock), others stay;
   the status item still reflects the queue; clicking it shows the SFTP sidebar.
3. Ask before copying: no dialog when nothing exists; Replace and Skip existing enqueue with the
   matching policy; Cancel enqueues nothing; a folder conflict under Replace merges without
   asking (coordinator test with a real local endpoint).
4. `TransferRequest`/`TransferCodec`: the policy round-trips through persistence.
5. Removal: the plugin registers no Transfers panel or toggle action; the Transfers action shows
   the SFTP sidebar.

User GUI checks: upload a large file and a folder and watch the strip; cancel one; interrupt one
by dropping the network and resume it; upload onto existing files and choose Replace, then Skip
existing; watch a finished transfer fade; confirm the strip disappears when empty and in a second
window shows the same queue.
