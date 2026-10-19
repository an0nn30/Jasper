# Remote: SSH, SFTP and tunnels

Remote is bundled with Jasper. Press **Cmd+Shift+H** (Ctrl+Shift+H elsewhere) or type `>ssh` in the
command palette to pick a saved host; Enter connects in a new tab, Cmd/Ctrl+Enter connects in a
new tab group beside the current one, Shift+Enter edits the host. The **Sessions** panel shows searchable
compact tree-style rows grouped by folder, favorites first, with slightly larger text.
Choose **Session › Toggle SSH hosts** or press **Cmd+Alt+1** (Alt+1 elsewhere) to show or hide
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

Under the Enterprise look the toolbar shows **Session:** with a dropdown (saved hosts, then WSL distributions, then
**Manage Sessions…**) and **Connect**, which focuses a running pane for the chosen host or opens one. The Sessions panel
is a Name/Host/Port tree over the selected host's Properties, Tunnels is a Type/Local/Remote/Via table whose rows start
or stop on Enter or a double click, SFTP's tools read **Hidden files** and **Upload** over a Modified column, and
Transfers (**Cmd+Alt+3**, Alt+3 elsewhere) is a bottom panel. The panels start with Sessions and Tunnels on the right,
SFTP on the left.

On Windows with WSL installed, the Sessions panel starts with a read-only **WSL** group: the
distributions Jasper detected for its shell profiles, each with its WSL version (WSL 1 or WSL 2) as a
badge. Double-click or Enter, **Open** on the card, or **Open in new tab** in the context menu opens
the distribution in a new local tab; **Open in split** opens it in a new tab group beside the current one. A dot and count mark
the panes Remote opened for it whose shell still runs. Distributions are never saved in `hosts.toml`
and cannot be edited, duplicated, deleted or browsed with SFTP; the group's collapsed state is saved
as its own `wsl_collapsed` flag in `panel-state.toml` (a host group named "wsl" keeps its own state),
and **Search sessions...** matches distribution names. With no SSH hosts saved, the hint under the
group reads "No SSH hosts yet — Add or Import from ~/.ssh/config". The Sessions dropdown lists
them first, titled with their version, and `>ssh` lists them as rows tagged `WSL`, matched on their
names; distributions come first but keep up to half the rows (rounded down) for matching hosts. To make a
distribution the default shell for new panes, choose it under Settings › Terminal › Shell. The status
bar still counts SSH sessions only. Without WSL, nothing here changes.

Connecting shows a centered overlay inside the owning app window, with an explicit **Cancel**
button. It stays centered on resize and blocks underlying input; Escape and outside clicks do not
dismiss it. The tab or split appears once the SSH shell is ready. Failures offer **Retry** and
**Close**. Reconnect uses the overlay while preserving the existing pane. Host-key and Vault prompts
remain native dialogs above it. A second connection request in that window focuses the current attempt.
The **Session** menu carries the connection commands, **New Host...** (`dev.jasper.remote.hosts.new`, the Sessions
panel's **+** without opening the panel; unbound by default), **Import from ~/.ssh/config...** and the tunnel commands; the **Transfer**
menu carries **Open SFTP here** and **Transfers**.

A host is a name, hostname, port, username, group, an optional jump host and how it authenticates:
a **Vault credential** (a login with a password, a key or both, or a standalone key with the host's
username) or the **SSH agent** (`SSH_AUTH_SOCK`, or the OpenSSH agent pipe on Windows). Hosts live in
`plugins/dev.jasper.remote/data/hosts.toml`, which you may edit; Jasper notices the change within a
second. The file never holds a secret. A jump host makes the connection go through that host first,
with its own authentication and host-key check. In the Add Host form, **Add and Connect** saves the host and then
connects to it in a new tab, as the palette's Connect does; if the save fails, nothing connects.

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

Settings live in `plugins/dev.jasper.remote/dev.jasper.remote.toml`; change them in Settings › Tools › Remote or in the
file (Plugins manager > Open Settings):

```toml
connect_timeout_seconds = 10    # 0 to 3600
auth_timeout_seconds = 30       # 0 to 3600
keepalive_seconds = 30          # 0 disables
session_linger_seconds = 5
read_user_known_hosts = true
use_ssh_agent = true
```

Connection settings apply to the next connection. A value out of range is reported and its default used.
Toggle SSH Hosts (Cmd+Alt+1), Connect to SSH Host… (Cmd+Shift+H) and Toggle SFTP (Cmd+Alt+2) are bound in
Settings › Keymap like any other action; `cmd` means Command on macOS and Ctrl+Shift elsewhere. A `[shortcuts]`
table from an older Remote is reported as unknown; delete it and rebind in Keymap.

The actions are `dev.jasper.remote.connect`, `dev.jasper.remote.hosts`, `dev.jasper.remote.split`,
`dev.jasper.remote.hosts.new` and `dev.jasper.remote.import`, plus `dev.jasper.remote.sessions.manage`; bind them like other
contributed actions. Tunnels have their own section at the end of this guide.

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

For the WSL group (Windows with at least one WSL 1 and one WSL 2 distribution), also check:

11. The panel is titled **Sessions** and starts with a WSL group listing each distribution with the
    right WSL 1 or WSL 2 badge; `docker-desktop` is absent. A Mac or a Windows machine without WSL
    shows no group.
12. Double-click a distribution: a tab titled with its name runs that distribution's shell, and the
    row shows a dot. **Open in split** adds a second pane (count 2). Exit one shell and close the other
    pane: the dot disappears.
13. Install or remove a distribution, then click **Detect shells again** (↻) under Settings › Terminal › Shell: the
    group, the Sessions dropdown and `>ssh` follow without a restart.
14. Collapse the WSL group, restart Jasper, and confirm it stays collapsed. A host group named `wsl`
    collapses and expands on its own, without moving the WSL group.
15. The toolbar's **Sessions** dropdown lists the distributions first (for example "Ubuntu (WSL 2)");
    choosing one opens it in a new tab. In `>ssh`, distributions are rows tagged `WSL`: Enter opens a
    tab, Cmd/Ctrl+Enter opens it in a new tab group beside the current one, and Shift+Enter (edit) does nothing.

For native acceptance of the UI changes, resize the app while connecting and confirm the overlay
stays centered; Escape/outside clicks and underlying menus should not interrupt it. Cancel should
leave no new tab, while success should focus the connected pane. Confirm host-key/Vault prompts
remain usable. Edit `[ui.font]` in the app configuration while Hosts is open and verify the list,
menus, palette and subsequent connection overlay follow the new family/size.

## SFTP browsing and transfers

Remote 0.4 includes SFTP in the same plugin as SSH. Choose **Transfer > Open SFTP here**, **View > SFTP**,
**Open SFTP here** in a terminal's context menu, or **Browse files** on a saved host.
Connecting a shell does not automatically open the browser. There is one remote browser in
each window, with an editable path, compact file list, Up, Upload, Download, Refresh,
New folder, Delete and Copy path controls. Select multiple rows for file operations. Hidden items (names starting with
a dot) stay out of the list until the **Show hidden items** toggle beside Refresh is pressed; the choice is remembered.
The context menu also offers **Upload folder** and **Copy to host**.
Opening a directory link deliberately resolves its path; recursive copy/delete still treat it as a link.

The browser remembers each remote pane's directory and Follow terminal folder choice.
Following uses the pane's own directory reports (OSC 7) when its shell sends them. Otherwise,
on Linux and macOS hosts, Remote reads the shell's folder after you press Enter or focus the
pane, with a fixed read-only script on a separate SSH exec channel: nothing is typed into your
shell. For this, each pane of a host with **Track shell folder for SFTP follow** (on by default,
in the host editor) has its own SSH connection. Windows hosts are not followed. A host that
refuses exec channels shows "This host does not report the shell's folder". Entering a path or
navigating a folder turns following off; Refresh preserves it. Selecting a local terminal
leaves the last remote browser available. When the SSH session of the pane the browser shows
ends (its tab or pane closes, the shell exits or the connection drops), the browser disconnects
and clears, showing "SSH session closed"; transfers already queued keep running. Browsing and transfers hold their own SSH leases, so
closing the source terminal does not cancel a copy.

- **Upload** opens the owning window's native file picker; the selection goes into the
  displayed remote directory. **Upload folder** picks a local directory recursively.
- **Download** sends the selected remote files/folders into a chosen local directory.
- **Copy to host** opens a destination chooser with a saved-host selector and one folder
  browser. Confirm **Copy here** to copy the captured selection. Remote-to-remote data streams
  through Jasper; it is not staged as a local file.
- **Delete** requires confirmation and removes the captured selection recursively. It is
  cancellable, but already deleted entries cannot be recovered. Links themselves are deleted;
  their targets are not traversed. Copy path writes plain paths to the clipboard.

### Editing remote files

With the bundled Editor installed, double-click a file in the SFTP browser, or select it and press Enter, to open it
in a tab of the Editor window; saving writes it back to the host. A link to a file opens the file it leads to (saving
changes that file, and the link stays a link); a link to a folder navigates as before. **Open in Editor** in the
right-click menu opens the selected files and links to files, at most 20 at a time, and says so ("Opened 20 of 57
files.") when there were more. Without the Editor, double-clicking a file does nothing and the menu item is hidden.

Saving follows the Editor's safe write. The new content goes to a hidden, owner-only sibling
(`.<name>.jasper-<random>.tmp`) beside the file, which gets the file's permissions and atomically replaces it through
the `posix-rename@openssh.com` extension. Remote writes the file in place instead, keeping its owner, group and links,
when the folder does not allow the sibling, when the sibling would get another owner or group, when the server does not
report the file's permissions, or when the server has no atomic rename; an interrupted write in place may leave a
partial file, and the tab stays unsaved so you can retry.
The atomic replacement gives the file a new inode: other hard links to it keep the old content (SFTP cannot report
link counts), and a file bind-mounted into a container keeps showing the old content until the container reopens it.
A read-only file is refused. Before saving, the Editor compares the file's modification time and size with what it
opened and asks when someone else changed it; SFTP reports times to the second, so a change within the same second
that keeps the size goes unnoticed. Remote files are not watched for outside changes; Revert re-reads them.

Editor operations for one connection run one at a time on one SFTP channel of the live SSH connection, opened for the
first and closed when none are left. When the connection has closed, the next operation reconnects; sign-in prompts
appear in the terminal window the file was opened from, or else in the active one. With no terminal window open it
fails with "Reconnecting to prod-web needs an open terminal window for the sign-in prompt."

Transfers appear at the bottom of the SFTP sidebar (the Transfers panel under the Enterprise look), in every window: what is copying and where,
a progress bar, speed and time left, and **×** to cancel. A transfer that needs you shows one
button — **Resume** (paused or interrupted), **Resolve…**, **Retry failed** or **Retry cleanup**.
A finished transfer shows "Done" for a few seconds and leaves; failed or cancelled ones stay until
dismissed with ×. The strip hides when nothing is queued. Before copying onto items that already
exist, Remote asks once: **Replace** (files replaced, folders merged), **Skip existing** or
**Cancel**. **Transfer > Transfers** shows the sidebar. The status bar displays aggregate progress,
confirmed-data speed and bytes remaining; scanning has an unknown total. Click it to show the
sidebar. Its cancel control cancels the sole active job, or shows the sidebar when several jobs
are active.

Pause retains partial files and checkpoints. Resume rechecks the source and destination
prefixes before appending; a large retained prefix takes time to validate. Cancel stops new
work and attempts to remove only verified, owned temporary files. Already published files
remain. A cancelled transfer whose cleanup could not finish offers **Retry cleanup** on its row
in the strip. **×** dismisses a finished or cancelled transfer; it removes queue history only, and
asks first when cleanup remains unresolved.

After a restart, unfinished jobs are **Paused**, with no automatic authentication or network
reconnect. Resume is explicit. A changed saved host, login account or jump route requires
attention rather than silently changing the copy's endpoint. Conflict choices are Replace,
Skip or Rename for files; Merge, Skip or Rename for folders; and Skip or Rename for type
mismatches. Applying a decision to remaining conflicts is scoped to that conflict type.
Independent files continue while other entries need a decision. Completed directory listings are
retained when a scan resumes. Potential case/Unicode-normalization aliases within one job are
rejected before copying, conservatively even on a case-sensitive server; rename or split the
selection instead of risking one sibling replacing another.

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

SFTP limits are in the same file, and apply at once:

```toml
[sftp]
max_parallel_files = 2           # 1 to 8; at most two copies per endpoint
request_timeout_seconds = 30    # 1 to 300
```

Shortcut changes apply live; app-level keybindings retain precedence. Actions are
`dev.jasper.remote.sftp`, `.sftp.toggle`, `.transfers` and
`.transfers.cancel` (each suffix uses the `dev.jasper.remote` prefix). Controls inherit the
app UI font and use SDK semantic icons. Native acceptance and the
verified build commands are in [SFTP verification](remote-7c-verification.md).

## Tunnels

Remote 0.10 adds SSH tunnels. Open the **Tunnels** panel from its rail button or **Session > Toggle Tunnels**
(the palette's **Show Tunnels** command opens it too). A tunnel goes through a saved host: its credential, host-key
trust and jump host apply. It has its own life: starting one never opens a terminal, closing terminals never stops it,
and it shares the host's SSH connection with any terminals that are open.

- **Local** (`-L`): a port on this computer reaches a target as seen from the host, such as
  `localhost:5432 → db.internal:5432`.
- **Remote** (`-R`): a port on the server comes back to a target as seen from this computer. Allowing other computers to
  connect needs the server's `GatewayPorts` to permit it.
- **Dynamic** (`-D`): a SOCKS5 proxy on this computer (no authentication, CONNECT only); names resolve on the server.

Click **+** (or **Session > New Tunnel...**) to add one: a name, the host to go through, the type, where to listen (port 0
means any free port) and where to forward to. Start and stop a tunnel with the button at the end of its row, Enter or
Space. The palette's Tunnels scope (`tunnels >`, or **Session > Start or Stop a Tunnel...**) lists the same tunnels: Enter starts or stops, Cmd/Ctrl+Enter opens a
running local tunnel in the browser, Shift+Enter edits a saved tunnel. Double-clicking a saved tunnel edits it. A
running row shows its open connections, the bytes moved each way and its uptime; right-click for Start or Stop, Open
in Browser, Copy Address, Edit, Duplicate and Delete. The status bar counts running tunnels and failures. A tunnel
whose host was deleted stays in the list, marked "Host missing"; edit it to choose another host.

**Forward a Port...** (a terminal's context menu, the Session menu or the palette) forwards a remote port to the same port
on this computer's loopback (the next free one if it is taken) through the SSH pane's host, or through a host you choose
when the command does not start from an SSH pane. **More** reveals a *Forward to host* field for a target other than the
server itself, named as the server sees it. It starts a quick forward, listed under *Quick forwards* until you stop it
or Jasper quits; **Save as Tunnel…** in its right-click menu (or double-clicking it) names it and keeps it, and **Keep
as a saved tunnel** in the dialog saves it from the start.

A row reports what went wrong: a busy port ("Port 5432 is already in use on this computer"), a port that needs
administrator rights, a refused remote port ("The server refused port 8080: already in use or not allowed"), a lost
connection ("Connection lost"), or the errors SSH sessions report. In this release a tunnel that loses its connection
stops; start it again. When a connection cannot be forwarded (for example the server has `AllowTcpForwarding no`, or the
target is unreachable) the tunnel stays up and its row says "Connections are being refused: <reason>" until a later
connection succeeds.

Tunnels are saved in `plugins/dev.jasper.remote/data/tunnels.toml`:

```toml
[[tunnel]]
id = "…"                      # optional; when absent it is derived from the name, so keep names unique
name = "pg-prod"
host = "5f2c…"                # a saved host's id
kind = "local"                # local | remote | dynamic
bind_address = "localhost"    # localhost, an IP address, or * for all interfaces
bind_port = 5432              # 0 = any free port
target_host = "db.internal"   # local and remote only
target_port = 5432
start = "manual"              # manual | launch (launch is stored; nothing starts it yet)
created = 2026-10-01T09:00:00Z
updated = 2026-10-01T09:00:00Z
```

Edits to the file apply within a second: a running tunnel whose forwarding changed restarts, and a removed one stops.
A file that does not parse keeps the last good tunnels and shows one error row ("tunnels.toml has errors: …"). While
the file has entries Remote cannot read (they are reported in the log), saves from Jasper are refused until you fix or
remove them. The actions are `dev.jasper.remote.tunnels` (Toggle Tunnels), `.tunnels.show` (Show Tunnels),
`.tunnels.pick` (Start or Stop a Tunnel...), `.tunnels.new` (New Tunnel...), `.tunnels.forward` (Forward a Port...) and `.tunnels.stop-all` (Stop All Tunnels),
none bound by default.

Native acceptance for tunnels (user-run):

1. A local tunnel to a real database or web server through a real host; Open in Browser for a web one.
2. A dynamic tunnel as a browser's SOCKS proxy with remote DNS: sites load and names resolve on the server.
3. A remote tunnel from the server back to a local web server; `curl localhost:<port>` on the server.
4. Forward a Port from an SSH pane, then Save as Tunnel; restart Jasper and find it saved.
5. Stop the network or `sshd`: the tunnel shows "Connection lost" and its port closes.
6. "Allow other computers to connect" on a local tunnel: macOS asks once to accept incoming connections.

## For plugins: projects on saved hosts

Remote 0.12 exports `dev.jasper.remote.api`. Its `RemoteProjects` service (published with `Services.publish`; require
`dev.jasper.remote` `>=0.12`, optionally) lets a plugin find where an SSH pane is (`locate`), check markers in a list of
folders with one command (`exists`), list a project's files (`list`: Git, then `find`, then an SFTP walk), and open a
file in the Editor or an SSH tab in a folder. Calls without a window use only an already authenticated connection and
fail with `NotConnectedException` otherwise; `list` with a window may connect and ask the user to sign in. Remote runs
fixed scripts as `sh -s` with every path single-quoted and never types into the user's shell. `exists` needs a POSIX
`sh` on the host, so marker detection does not work on Windows OpenSSH hosts; `list` falls back to an SFTP walk where
`sh`, Git or `find` is missing. `locate` attributes a pane's folder to the pane's saved host: after a nested `ssh`
inside an SSH pane, the folder reported is still read as one on the saved host. Project Index is the bundled consumer.
