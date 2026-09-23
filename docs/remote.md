# Remote: SSH and SFTP

Remote is bundled with Jasper. Press **Cmd+Shift+H** (Ctrl+Shift+H elsewhere) or type `>ssh` in the
command palette to pick a saved host; Enter connects in a new tab, Cmd/Ctrl+Enter connects in a
split beside the current pane, Shift+Enter edits the host. The **SSH hosts** panel shows searchable
compact tree-style rows grouped by folder, favorites first, with slightly larger text.
Choose **View > SSH Hosts** or press **Cmd+Shift+S** (Ctrl+Shift+S elsewhere) to show or hide
the panel in the current window. Opening the sidebar highlights the first visible host and focuses
the list, ready for arrow keys and Enter. Both navigation shortcuts are configurable below.
A small dot marks an active session; multiple sessions show a count. Selecting a host shows its
address and detected OS/IP below the list.
The same details are available in the row tooltip and remain searchable. Double-click or Enter returns to the most recently used running pane for
that host, or connects when none is open. **Connect** and the **Connect in new tab** context action
always open another session; the menu also offers Connect in split, Edit, Duplicate and Delete.
Click the star to toggle a favorite. Right-click the default group header (**Other** initially)
and choose **Rename group…**; that name also applies to future hosts without an explicit group.

The toolbar’s **Sessions** dropdown lists saved hosts alphabetically with their addresses.
Choose a host to focus its most recently used running session, or connect in a new tab if none
is open. **Manage Sessions...** opens the hosts panel, including when no hosts are saved.
The list updates when hosts are added, edited, imported or removed.

Connecting shows a centered overlay inside the owning app window, with an explicit **Cancel**
button. It stays centered on resize and blocks underlying input; Escape and outside clicks do not
dismiss it. The tab or split appears once the SSH shell is ready. Failures offer **Retry** and
**Close**. Reconnect uses the overlay while preserving the existing pane. Host-key and Vault prompts
remain native dialogs above it. A second connection request in that window focuses the current attempt.
The **SSH** menu carries the connection commands plus **Import from ~/.ssh/config...**.

A host is a name, hostname, port, username, group, an optional jump host and how it authenticates:
a **Vault credential** (a login with a password, a key or both, or a standalone key with the host's
username) or the **SSH agent** (`SSH_AUTH_SOCK`, or the OpenSSH agent pipe on Windows). Hosts live in
`plugins/dev.jasper.remote/data/hosts.toml`, which you may edit; Jasper notices the change within a
second. The file never holds a secret. A jump host makes the connection go through that host first,
with its own authentication and host-key check.

**Import** reads `~/.ssh/config` (`Host`, `HostName`, `Port`, `User`, `ProxyJump`, ordered
`IdentityFile` directives and one level of `Include`). Wildcard hosts and `Match` blocks are
listed as unsupported. Select the hosts to import; existing aliases start unchecked and can be
selected for **Update**. Updates preserve their UUID, creation date, group and favorite.

Vault creates or unlocks its encrypted store, reads only the selected keys and asks for encrypted
key passphrases. Review the fingerprints and press **Import and use** to save the key material,
passphrases and Remote's grants. Repeated keys are deduplicated by fingerprint. A matching standalone
file-based Vault key is promoted while preserving its UUID and grants. Source files are untouched.
Hosts with no IdentityFile require a chosen stored Vault key or password. Unsupported paths,
unreadable keys and unresolved jump hosts block the affected selection; there is no Agent fallback.

Imported hosts authenticate from Vault even after a restart without their original files or the
system agent. To repair an earlier Agent import, re-import and check **Update** for that host.
Multiple identities stay ordered and can be reordered in the host editor. Existing manual Agent,
password and path-based Vault hosts continue to work. Each development worktree has its own Jasper
home and Vault; this operation imports into the home of the app you are running.

Keys are saved before hosts. If the hosts file changes or cannot be saved, the keys remain safely
in Vault; **Refresh** and retry to reuse them. Cancellation before host saving leaves hosts unchanged.
Once a durable write starts it completes; cancelling cannot undo keys already saved.

The first managed key upgrades the inner Vault payload to version 2 and preserves an encrypted,
owner-readable `vault.jv.v1-backup` beside `vault.jv`. Backup failure aborts the upgrade. Older Vault
versions cannot read version 2: to downgrade, stop Jasper and restore that backup, accepting that it
contains only pre-upgrade credentials and may require the previous master password/device binding.
Generated and unrelated path-based credentials remain as they were. No decrypted key files are created.

The first connection to a host shows its key type and SHA256 fingerprint with **Cancel**,
**Connect once** and **Trust and connect**; trusting writes `plugins/dev.jasper.remote/data/known_hosts`.
Keys already in `~/.ssh/known_hosts` (hashed entries included) connect without asking. A key that
differs from either file is rejected. A corrupt Jasper `known_hosts` refuses every connection until
it is fixed.

An SSH connection is shared by tabs, splits, SFTP browsers and transfers using the same
connection identity; it stays open for a few seconds after its last use. When it drops, every pane on that host
shows the disconnected banner and Reconnect makes a new connection. The status bar shows how many
SSH sessions are open; clicking it toggles the panel. Locking the Vault leaves live sessions alone;
only new connections need it.

Host information is best-effort. After a successful user-initiated connection, a separate SSH exec
channel reads `uname -s`, Linux `/etc/os-release`, or Windows `ver`; these commands never enter the
interactive shell. The probe has time and output limits and cannot prevent using the shell. Results
are cached in `data/host-info.properties`, refreshed on the next authenticated transport, and hidden
when the saved endpoint changes. Direct connections can show the connected IP when it differs from
the configured hostname; jump connections never show their local forwarding address as the host IP.
Key-based connections use the same flow; opening the panel does not initiate network connections.

Settings live in `plugins/dev.jasper.remote/dev.jasper.remote.toml` (Plugins manager > Open Settings):

```toml
connect_timeout_seconds = 10
auth_timeout_seconds = 30
keepalive_seconds = 30          # 0 disables
session_linger_seconds = 5
read_user_known_hosts = true
use_ssh_agent = true

[shortcuts]
toggle_panel = "cmd+shift+s"
open_palette = "cmd+shift+h"
```

Shortcut edits apply live to all open windows; no plugin restart is needed. Omitted keys use the
defaults above; set a value to `""` to leave that action without a plugin shortcut. `cmd` means
Command on macOS and Ctrl+Shift elsewhere. Syntax and collision handling follow Jasper’s normal
keybindings: an invalid shortcut or one already claimed is left unbound. An explicit
app-level `[keybindings]` override for the same action still takes priority, even when its plugin
shortcut is disabled. Connection settings remain above the `[shortcuts]` table.

The actions are `dev.jasper.remote.connect`, `dev.jasper.remote.hosts`, `dev.jasper.remote.split` and
`dev.jasper.remote.import`, plus `dev.jasper.remote.sessions.manage`; bind them like other
contributed actions. Tunnels remain a later feature.

## Native acceptance

Headless tests cover the store, the config import, trust decisions, the agent protocol, the shared
session, ProxyJump and every failure message against an SSH server on loopback. The following need a
real desktop and a host you control:

1. Connect to a real host with a Vault login (password), a Vault key and the system agent; verify the
   fingerprint prompt against the server's own, then that Trust and connect stops the prompt.
2. Connect through a real bastion (jump host); confirm two host-key prompts on first use and one
   saved trust entry for each host. The status count tracks shell channels, not jump transports.
3. Open a second tab and a split on the same host: no new authentication, both interactive; close all
   and confirm the session drops after the linger.
4. Import your `~/.ssh/config`; check the mapped fields and the "not importable" list.
5. Pull the network or stop `sshd`: every pane on that host shows the banner; Reconnect works after.
6. Lock the Vault while connected (sessions stay), then Connect a Vault host (unlock prompt appears).

For the host-list/dialog follow-up, also check:

7. Rename **Other**, restart, and add another ungrouped host; both use the saved name.
8. Cancel a slow connection and retry a rejected login; no empty tab should appear.
9. Open two sessions for one host, focus each in turn, then double-click its row; the last used
   running pane is selected. Close both; double-click now starts a new connection.
10. Connect to Linux/macOS/Windows hosts and search by the learned OS or IP. An SSH server that
    disallows exec requests should still provide its normal terminal.

For native acceptance of the UI changes, resize the app while connecting and confirm the overlay
stays centered; Escape/outside clicks and underlying menus should not interrupt it. Cancel should
leave no new tab, while success should focus the connected pane. Confirm host-key/Vault prompts
remain usable. Edit `[ui.font]` in the app configuration while Hosts is open and verify the list,
menus, palette and subsequent connection overlay follow the new family/size.

## SFTP browsing and transfers

Remote 0.3 includes SFTP in the same plugin as SSH. Choose **SSH > SFTP**, **View > SFTP**,
**Open SFTP here** in a terminal's context menu, or **Browse files** on a saved host.
Connecting a shell does not automatically open the browser. There is one remote browser in
each window, with an editable path, compact file list, Up, Upload, Download, Refresh,
New folder, Delete and Copy path controls. Select multiple rows for file operations.
The context menu also offers **Upload folder** and **Copy to host**.

The browser remembers each remote pane's directory and Follow terminal folder choice.
Following uses directory reports from that pane, without running a shell command. Entering
a path or navigating a folder turns following off; Refresh preserves it. Selecting a local
terminal leaves the last remote browser available. Browsing and transfers hold their own SSH
leases, so closing the source terminal does not cancel a copy.

- **Upload** opens the owning window's native file picker; the selection goes into the
  displayed remote directory. **Upload folder** picks a local directory recursively.
- **Download** sends the selected remote files/folders into a chosen local directory.
- **Copy to host** opens a destination chooser with a saved-host selector and one folder
  browser. Confirm **Copy here** to copy the captured selection. Remote-to-remote data streams
  through Jasper; it is not staged as a local file.
- **Delete** requires confirmation and removes the captured selection recursively. It is
  cancellable, but already deleted entries cannot be recovered. Links themselves are deleted;
  their targets are not traversed. Copy path writes plain paths to the clipboard.

**SSH > Transfers** or **View > Transfers** opens the global queue in the bottom panel.
It works without Buddy and can be viewed in multiple windows. Select a job to inspect its
paged file list, issues and cleanup count, and to Pause, Resume, Cancel or Retry it. The status
bar displays aggregate progress, confirmed-data speed and bytes remaining; scanning has an
unknown total. Click it to open Transfers. Its cancel control cancels the sole active job, or
opens the queue when several jobs are active.

Pause retains partial files and checkpoints. Resume rechecks the source and destination
prefixes before appending; a large retained prefix takes time to validate. Cancel stops new
work and attempts to remove only verified, owned temporary files. Already published files
remain. Cleanup failures stay visible and can be retried. Clear removes queue history only;
it requires acknowledgement when cleanup remains unresolved.

After a restart, unfinished jobs are **Paused**, with no automatic authentication or network
reconnect. Resume is explicit. A changed saved host, login account or jump route requires
attention rather than silently changing the copy's endpoint. Conflict choices are Replace,
Skip or Rename for files; Merge, Skip or Rename for folders; and Skip or Rename for type
mismatches. Applying a decision to remaining conflicts is scoped to that conflict type.
Independent files continue while other entries need a decision.

Copies preserve links as links, and attempt modification times and ordinary rwx permissions.
Unsupported metadata becomes a warning; ownership, ACLs, extended attributes and special mode
bits are not copied. Special files are reported as issues. Servers without safe atomic replace
cannot replace an existing file. Jasper never substitutes a delete-then-copy operation.
Files are published from exclusive sibling temporary files after validation. Ambiguous partial
ownership or a changed final file requires attention; Jasper does not guess and delete it.
External programs can still race namespace changes, so avoid modifying a destination during a
copy. SFTP protocol limitations can make an interrupted partial unverifiable; restarting that
file may be necessary rather than resuming it.

The private persistent queue is `plugins/dev.jasper.remote/data/transfers/queue.sqlite`, beside
its lifetime lock and SQLite journal files. It stores paths, route/account identity, checkpoint
hashes and decisions, not credentials or file payloads. A corrupt/newer queue or a second
process holding its lock disables transfers with an error while ordinary SSH remains available.
Do not remove the queue to resolve a cleanup issue: it contains the partial-file ownership record.

Add these options to Remote's existing TOML tables (do not duplicate a table):

```toml
[shortcuts]
# Existing toggle_panel and open_palette entries may remain here.
toggle_sftp = ""                 # e.g. "cmd+alt+f"
toggle_transfers = ""            # e.g. "cmd+alt+t"

[sftp]
max_parallel_files = 2           # clamped to 1..8; at most two copies per endpoint
request_timeout_seconds = 30    # clamped to 1..300
```

Shortcut changes apply live; app-level keybindings retain precedence. Actions are
`dev.jasper.remote.sftp`, `.sftp.toggle`, `.transfers`, `.transfers.toggle` and
`.transfers.cancel` (each suffix uses the `dev.jasper.remote` prefix). Controls inherit the
app UI font and use SDK semantic icons for the selected skin. Native acceptance and the
verified build commands are in [SFTP verification](remote-7c-verification.md).
