# Remote SFTP verification

Branch: `codex/remote-sftp`, based on `1fcf9a8d`.
Worktree: `/Users/dustin/.codex/worktrees/remote-sftp/moray`.
The approved [design](superpowers/specs/2026-09-23-jasper-remote-sftp-design.md) and
[implementation plan](superpowers/plans/2026-09-23-jasper-remote-plan-7c-sftp.md) cover the
single Remote plugin; SSH remains available independently of the transfer queue.

## Automated evidence

The full gate is `./gradlew check :jasper-app:installDist` on JBR 25. Final counts and independent
review outcomes are recorded below after the final gate. Targeted regression suites passed for:

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
8. Set `toggle_sftp` and `toggle_transfers` in Remote's TOML; verify live changes and app override
   precedence. Set copy concurrency and a short request timeout for a deliberately stalled host.

## Boundaries

No editor, directory synchronization, move or SCP/shell-copy fallback is included. Restart does
not reconnect automatically. Metadata beyond modification time and ordinary rwx bits is not
preserved. SFTP cannot prove ownership of every interrupted partial; ambiguous evidence remains
an attention/cleanup issue rather than authorizing deletion. Prefix validation reads the saved
prefix at both endpoints and can take appreciable time. See [Remote](remote.md) for user-facing
recovery, conflict, queue and cleanup behavior.
