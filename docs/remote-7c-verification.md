# Remote SFTP verification

Branch: `codex/remote-sftp`, based on `1fcf9a8d`.
Worktree: `/Users/dustin/.codex/worktrees/remote-sftp/moray`.
The approved [design](superpowers/specs/2026-09-23-jasper-remote-sftp-design.md) and
[implementation plan](superpowers/plans/2026-09-23-jasper-remote-plan-7c-sftp.md) cover the
single Remote plugin; SSH remains available independently of the transfer queue.

## Automated evidence

The full gate `./gradlew check :jasper-app:installDist` passed after the review fixes on JBR 25 in 1m41s:
**1,845 tests, 1,842 passed, three expected skips, zero failures/errors**, counted from
JUnit XML. All accepted final-review findings have passing regression tests. The installed distribution contains
`sshd-sftp-2.19.0.jar`, `sqlite-jdbc-3.53.4.0.jar` and Remote `0.3.0` declaring SDK
`>=0.7.5, <0.8`; its plugin jar contains no image assets. Targeted regression suites passed for:

- Local, SFTP and remote-to-remote copies through loopback SSH; ProxyJump and a surviving shell.
- Explicit write acknowledgements, bounded pipelining, short reads, sparse offsets and request aborts.
- Pausing stalled reads/writes, short checkpoints, resume validation and changed source rejection.
- Killed child processes at seven temporary-file/publication boundaries, including hard links and replacement.
- A generated 4,294,967,313-byte copy and 100,001-entry dataset in a child JVM limited to 64 MiB.
- SQLite reopen/corruption/newer-version handling, lifetime lock contention and killed-writer recovery.
- Browser stale-result suppression, 100k-entry disk cache, bounded pages, links and cancelled recursive deletion.
- Real staged-plugin/HostedContext stop during a blocked read, after a checkpoint and after publication;
  persisted pause intent, queue lock reacquisition and intact published output.
- Both host icon skins, owner-cancelled pickers, stable progress components, larger UI typography,
  settings reload and restored paused jobs without authentication.

The transfer implementation lives in the plugin. Architecture guards enforce SDK-only plugin
imports and app/SDK bridges. SQLite and SFTP are bundled plugin dependencies. No icon resources
are packaged by Remote; host-owned Tabler and OldGNOME2 mappings provide all semantic icons.

## Build and run for acceptance

From this worktree, build the application with its matching SDK and bundled plugin:

```sh
./gradlew check :jasper-app:installDist
```

The distribution is `jasper-app/build/install/jasper-app/`; launch its `bin/jasper-app` yourself,
or use `./gradlew :jasper-app:run` for the worktree development home. The standalone plugin bundle
is built by `./gradlew :jasper-plugin-remote:pluginZip` under `plugins/remote/build/distributions/`.
It needs Jasper SDK 0.7.5 or later below 0.8; do not install it into an older application.

Native GUI and real-host checks were not run by the agent. Use disposable directories on hosts
you control for the following acceptance steps:

1. Open SFTP through the SSH/View menus, saved-host context menu and terminal context menu.
   Confirm one browser, compact readable rows, keyboard navigation, SDK icons under both skins
   and enlarged UI fonts. Resize the sidebar; controls and paths should remain usable.
2. Switch between two remote panes, then a local pane. Verify each remote directory/follow
   choice, manual navigation disabling follow and Refresh preserving it. A new SSH connection
   should not force open a hidden SFTP panel.
3. Upload multiple files and one folder using the native pickers, download a mixed selection,
   and copy to another saved host through the single destination browser. Close/cancel a picker
   and change focus while it is open: the captured source and destination must stay correct.
4. Copy a large file and a many-file folder. Observe progress, speed and remaining bytes in the
   status bar; open Transfers without Buddy. View it in a second window. Pause, resume, cancel,
   close the originating pane and resize while copying; the app should stay responsive.
5. Quit during a paused/active copy, relaunch and verify jobs restore paused without prompts.
   Resume explicitly; validation precedes append. Edit the source or saved host identity before
   resuming and verify attention is required. Retry a network interruption.
6. Exercise file and directory conflicts, Rename and apply-to-remaining. Verify unrelated files
   continue, existing targets survive Skip, and published files survive Cancel. Test links to an
   ancestor without recursive traversal. Delete only disposable selections, cancelling partway.
7. Deny destination cleanup or metadata permissions. Confirm a visible issue/warning, preserved
   copied content and explicit cleanup retry/acknowledgement before forgetting cleanup history.
8. Set `toggle_sftp` in Remote's TOML; verify live changes and app override precedence. Set copy
   concurrency and a short request timeout for a deliberately stalled host.
9. Directory follow without shell integration (2026-09-24 amendment): on the Mac host and a stock
   Linux bash host, open two panes each and `cd` in each; the sidebar follows the focused pane
   within about half a second. Check `bash` inside `zsh`, a program started in another folder,
   `sudo -s` (falls back to the login shell's folder), a Windows host (no following, otherwise
   unchanged), the host setting off (panes share one connection, no following), and that the
   prompt, MOTD and shell history show nothing Remote ran. Also: with Follow off, browse the
   sidebar to a different, manually chosen folder, click back into the terminal, then turn
   Follow on — the sidebar returns to the shell's actual folder instead of staying on the
   manually chosen one.
10. Session end: with the SFTP sidebar showing a remote pane, type `exit` in that pane, then repeat by
    closing the tab and by dropping the network. Each time the sidebar clears and shows "SSH session
    closed", while a transfer started earlier keeps running. Reconnecting the pane browses it again.
11. Transfer strip (2026-09-24 amendment): upload a large file and a folder and watch the strip in
    the SFTP sidebar (name → host:folder, percent, speed, time left); cancel one with ×; drop the
    network during one and Resume it; upload onto existing files and choose Replace, then Skip
    existing; a finished upload shows Done and leaves after about five seconds; the strip hides when
    empty, appears in a second window with the same queue, and no Transfers panel exists any more.

## Boundaries

No editor, directory synchronization, move or SCP/shell-copy fallback is included. Restart does
not reconnect automatically. Metadata beyond modification time and ordinary rwx bits is not
preserved. SFTP cannot prove ownership of every interrupted partial; ambiguous evidence remains
an attention/cleanup issue rather than authorizing deletion. Prefix validation reads the saved
prefix at both endpoints and can take appreciable time. See [Remote](remote.md) for user-facing
recovery, conflict, queue and cleanup behavior.

## Final adversarial review

The independent reviewer inspected `1fcf9a8d..a599f457` and confirmed two data-integrity
issues and six recovery/control issues. The native fix pass addressed all eight:

| Finding | Fix and regression |
| --- | --- |
| Emoji-named folder descendants escaped conflict blocking | SQLite substring offsets use code points; actual coordinator Merge/Skip/Rename cases preserve untouched children until a decision. |
| Case/normalization aliases could overwrite a sibling | Indexed destination keys reject collisions before dispatch and on Rename. |
| Failed replacement publication could not accept a new decision | Durable resolution intent keeps old paths/evidence until reconciliation; all three decisions are tested both before and after publication. |
| Cancelling resume left credential resolution pending | Cancellation explicitly reaches the upstream identity/Vault future. |
| Apply-remaining swept every conflict on the control lane | Policies apply lazily during bounded dispatch; a 100k-conflict set does not delay another stalled job's pause. |
| Resuming a scan reopened finished directories | Only newly admitted directories create frontier rows; unfinished rows persist. |
| Deliberate directory-link navigation failed | Browser-only resolution follows bounded link chains, while recursive mutation remains no-follow. |
| Partial deletion results disappeared on refresh | A separate operation-result label retains cancellation and failed paths. |

The author additionally fixed missing cleanup paths/reasons in Details and a clipped conflict
scope checkbox, each with an observed failing regression followed by a passing test. Headless
component renders were inspected. The final full suite, architecture checks, source-hygiene scan and diff check passed.
There are no deferred review minors. Native desktop acceptance,
an independent Gradle rerun, and atomic exclusion of unrelated external filesystem writers were
the reviewer's explicitly unjudged items; their limits are recorded in the rulings below.

## Execution rulings

These are the execution ledger's decisions and their costs, retained here before scratch cleanup.
- Task 2: Ruling: the brief sample ProgressState constructor omitted the accessible description and OptionalDouble wrapper — use its declared interface and the approved accessibility requirement — cost if wrong: call-site adjustment only.
- Task 4: Ruling: identity(UUID) captures the configured route synchronously; lease resolves empty Vault accounts before reuse and resolveIdentity(UUID, owner) resolves without connecting — credential prompts require a captured owner and an async result — cost if wrong: callers must await resume validation.
- Task 4: Ruling: SFTP-through-ProxyJump regression moves to Task 5 because SFTP is not linked until then; SSH-through-ProxyJump and changed jump-account identities are verified now — cost if wrong: integration issue detected one task later.
- Task 5: Ruling: FileEntry additionally carries a nonsecret fileKey (empty when remote/unavailable), and FileEndpoint.id uses host-key fingerprint + effective account — temp ownership and alias reservations need this evidence — cost if wrong: conservative extra serialization across servers sharing a host key.
- Task 5: Ruling: server directory responses use MINA's bounded wire packets (256 KiB), then consumer/SQL batches are capped at 256; the client cannot dictate the server's entry count per reply — avoids rejecting valid remote listings — cost if wrong: one bounded protocol page may exceed 256 records in memory.
- Task 5: Ruling: local no-replace symbolic links publish via exclusive createSymbolicLink of the temp's exact link text, then unlink the temp — macOS createLink follows the source symlink (caught RED in shared contract), so hard-link publication would copy its referent — cost if wrong: recovery must reconcile two distinct symlink entries by link text, not inode equality. Regular files retain hard-link no-replace publication.
- Task 6: Ruling: staged plugin-loader SQLite test moves to Task 10 with distribution/loader integration; direct driver deregistration and child-process native-library loading are tested now — no product integration exists yet — cost if wrong: classloader packaging problem discovered during final integration.
- Task 7: Ruling: each running job retains up to two reusable endpoint pairs through its scan/copy/final-metadata phases — avoids a new SFTP subsystem for every file and pins the authenticated transport after pane closure — cost if wrong: up to four idle subsystem channels per admitted active job until pause/completion; scheduling limits active copies and scanner. Existing authenticated leases can be borrowed without prompts even when the initiating window closed; no new authentication is inferred.
- Task 7: Ruling: local reservation names use conservative Unicode normalization/case folding — covers case-insensitive APFS/Windows alias collisions — cost if wrong: extra serialization on case-sensitive local filesystems. Destination-directory reservation is intentionally conservative and shared among that job's workers.
- Task 7: Ruling: a remote partial without stable file identity must match persisted lstat evidence; an ambiguous uncheckpointed remote tail requires attention instead of blind truncate — SFTP v3 cannot prove its ownership — cost if wrong: more manual restarts after abrupt disconnects. Local owned tails validate all prefix digests and truncate safely.
- Task 8: Ruling: SftpUi is an additional composition class for captured picker flows and per-window controllers — keeps RemotePlugin free of file-operation/dialog details — cost if wrong: one extra focused UI class. Its composition tests run with Task 9 plugin wiring. Browser caches and recursive-deletion stacks are separate disposable SQLite files, never the durable queue. Controllers admit their own worker loops while the panel/dialog is created; close signals them without submitting shutdown work.
- Task 9: Ruling: synchronous FakePluginHost's runBackground cannot execute admitted lifetime loops — SSH test fixtures inject a real loop executor and join only in their test-only stop override; production uses HostedContext's scoped executor and never waits on EDT. The actual staged classloader/HostedContext test covers production executor admission, shutdown during read/checkpoint/publication, persisted pause and released queue lock.
- Task 9: Ruling: unsupported/failed metadata application is a separately counted warning if content can still be safely published — preserves content while showing Completed with issues — cost if wrong: permissions/time need manual correction, never reported as fully successful. Live status counts durable confirmed bytes once; speculative submitted writes are excluded. File policies can apply to remaining conflicts of the same kind; type mismatches still require individual decisions.
- Task 10: Ruling: fresh reviewer spawn was rejected by the agent thread limit — reused completed cards_review seat, independent of all SFTP implementation and plan review, with explicit new review context — cost if wrong: earlier SSH review context may bias judgments; no author review substituted.
- Final: Ruling: reject case/normalization-equivalent destination names conservatively for remote as well as local jobs — SFTP does not advertise filesystem comparison rules, so silent sibling overwrites are unacceptable — cost if wrong: a case-sensitive destination may require renaming or splitting otherwise-valid selections into separate jobs.
- Final: Ruling: native GUI/real-host acceptance remains user-run, as repository instructions require — no app window, login shell or saved-host connection was launched — cost if wrong: platform-specific desktop behavior remains unverified until acceptance.
- Final: Ruling: the reviewer did not run Gradle because the native implementer owned verification — full check/installDist, actual XML and staged-distribution evidence are recorded by the author — cost if wrong: test interpretation lacks an independent rerun, not test execution.
- Final: Ruling: fully atomic exclusion of external namespace changes remains the documented SFTP/local-provider limitation — revalidation, reservations and conservative ownership govern Jasper operations, not unrelated programs — cost if wrong: outside writers racing the same paths can still require manual review.
