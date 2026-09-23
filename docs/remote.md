# Remote: SSH hosts

Remote is bundled with Jasper. Press **Cmd+Shift+H** (Ctrl+Shift+H elsewhere) or type `>ssh` in the
command palette to pick a saved host; Enter connects in a new tab, Cmd/Ctrl+Enter connects in a
split beside the current pane, Shift+Enter edits the host. The **SSH hosts** panel shows searchable
compact tree-style rows grouped by folder, favorites first, with slightly larger text.
A small dot marks an active session; multiple sessions show a count. Selecting a host shows its
address and detected OS/IP below the list.
The same details are available in the row tooltip and remain searchable. Double-click or Enter returns to the most recently used running pane for
that host, or connects when none is open. **Connect** and the **Connect in new tab** context action
always open another session; the menu also offers Connect in split, Edit, Duplicate and Delete.
Click the star to toggle a favorite. Right-click the default group header (**Other** initially)
and choose **Rename group…**; that name also applies to future hosts without an explicit group.

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

**Import** reads `~/.ssh/config` (`Host`, `HostName`, `Port`, `User`, `ProxyJump`, `IdentityFile`,
one level of `Include`) and shows what each entry would become; wildcard hosts and `Match` blocks are
listed as not importable. An `IdentityFile` whose public key is a key in the Vault becomes that
credential; any other becomes the agent. Nothing is imported silently, and `~/.ssh` is never written.

The first connection to a host shows its key type and SHA256 fingerprint with **Cancel**,
**Connect once** and **Trust and connect**; trusting writes `plugins/dev.jasper.remote/data/known_hosts`.
Keys already in `~/.ssh/known_hosts` (hashed entries included) connect without asking. A key that
differs from either file is rejected. A corrupt Jasper `known_hosts` refuses every connection until
it is fixed.

One SSH connection per host is shared by every tab, split and (later) tunnel and SFTP browser for
that host; it stays open for a few seconds after its last use. When it drops, every pane on that host
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
```

The actions are `dev.jasper.remote.connect`, `dev.jasper.remote.hosts`, `dev.jasper.remote.split` and
`dev.jasper.remote.import`; bind them like other contributed actions. Tunnels and SFTP follow in
later versions of this plugin.

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
