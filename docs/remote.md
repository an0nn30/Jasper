# Remote: SSH, SFTP and tunnels

Remote (`dev.jasper.remote`) is a bundled plugin, on by default. It keeps your SSH hosts and opens SSH terminal tabs and
splits on them, browses their files over SFTP with a persistent transfer queue, opens remote files in the Editor, runs
local, remote and SOCKS tunnels, imports hosts from `~/.ssh/config` with their keys stored in Credential Vault, and
lists Windows' WSL distributions beside your hosts. Turn it off or on in Settings › Plugins (see [Plugins](plugins.md)).

Remote works with two other bundled plugins when they are on: [Credential Vault](credential-vault.md) holds the
passwords and keys hosts sign in with (the SSH agent works without it), and the [Editor](editor.md) edits remote files.
Remote's tool windows are in every window ([Tool windows and the dock](workspace.md#tool-windows-and-the-dock)). Under a classic look, such as
[Enterprise](enterprise.md), they take their classic layout ([below](#under-a-classic-look)).

## Commands and shortcuts

| Command | Action id | macOS | Linux and Windows |
|---|---|---|---|
| Connect to SSH Host... | `dev.jasper.remote.connect` | Cmd+Shift+H | Ctrl+Shift+H |
| Toggle SSH hosts | `dev.jasper.remote.hosts` | Cmd+Option+1 | Alt+1 |
| Toggle SFTP | `dev.jasper.remote.sftp.toggle` | Cmd+Option+2 | Alt+2 |
| Transfers | `dev.jasper.remote.transfers` | Cmd+Option+3 | Alt+3 |
| Open SFTP here (in a terminal) | `dev.jasper.remote.sftp` | Cmd+Shift+O | Ctrl+Alt+Shift+O |
| SFTP: Back (in the SFTP panel) | `dev.jasper.remote.sftp.back` | Option+Left | Alt+Left |
| SFTP: Forward (in the SFTP panel) | `dev.jasper.remote.sftp.forward` | Option+Right | Alt+Right |
| SFTP: Parent Folder (in the SFTP panel) | `dev.jasper.remote.sftp.parent` | Option+Up | Alt+Up |
| Split with Same Host (in a terminal) | `dev.jasper.remote.split` | | |
| New Host... | `dev.jasper.remote.hosts.new` | | |
| Import from ~/.ssh/config... | `dev.jasper.remote.import` | | |
| Manage Sessions... | `dev.jasper.remote.sessions.manage` | | |
| Cancel transfer | `dev.jasper.remote.transfers.cancel` | | |
| Toggle Tunnels | `dev.jasper.remote.tunnels` | | |
| Show Tunnels | `dev.jasper.remote.tunnels.show` | | |
| Start or Stop a Tunnel... | `dev.jasper.remote.tunnels.pick` | | |
| New Tunnel... | `dev.jasper.remote.tunnels.new` | | |
| Forward a Port... | `dev.jasper.remote.tunnels.forward` | | |
| Stop All Tunnels | `dev.jasper.remote.tunnels.stop-all` | | |

Rebind any of them in Settings › Keymap or under `[keybindings]` in `config.toml`
(see [Shortcuts](configuration.md#shortcuts)). Every saved host and every WSL distribution is an action too
(`dev.jasper.remote.session.h<host id>` and `dev.jasper.remote.session.w<id>`), titled like its entry in the toolbar's
Sessions menu, so the palette's Commands scope finds them and Keymap can bind them.

The **Session** menu holds Connect to SSH Host..., Toggle SSH hosts, Split with Same Host, New Host..., Import from
~/.ssh/config..., Toggle Tunnels, Start or Stop a Tunnel..., New Tunnel..., Forward a Port... and Stop All Tunnels. The
**Transfer** menu holds Open SFTP here and Transfers. A terminal's right-click menu offers Forward a Port... and Open
SFTP here. View › Tool Windows (the Window menu under the Enterprise look) lists Toggle SSH hosts, Toggle SFTP and
Toggle Tunnels.

## Opening sessions

### From the command palette

**Connect to SSH Host...** opens the palette on the SSH scope; typing `ssh >` in the palette does the same. It lists the
WSL distributions first, then your hosts, favourites first and then by name, each with `user@host:port` and its group.
Matching hosts keep up to half the rows. A search matches a host's name, hostname, username or group, and a
distribution's name.

| Key | Verb |
|---|---|
| Enter | **Connect**: a new SSH tab, or a new tab on the distribution |
| Cmd+Enter (Ctrl+Enter) | **Connect in split**: the same, in a new pane beside the current one |
| Shift+Enter | **Edit host…** (hosts only) |

### The Sessions panel

**Toggle SSH hosts** shows or hides the Sessions tool window, which starts on the left. Opening it selects the first
host or distribution and puts the keyboard in the list, ready for the arrow keys and Enter. Its header shows how many
hosts and distributions there are, with **New session** (+) and **Import sessions**.

- **Search sessions** filters by name, hostname, username, group and the OS and address Remote learned for a host
  ([Host information](#host-information)).
- Rows: the [WSL group](#wsl-distributions) first, then your groups in alphabetical order with the default group last
  (**Other** until you rename it), hosts alphabetical within each. While every host is ungrouped and the default group
  is still **Other**, the list has no group headers.
- Hosts sort by name by default; the header's **Sort sessions** menu (default look) or a click on the Name, Host or Port header (Enterprise look) sorts them within each group by that column (both share one sort), and the sort and column widths are remembered.
- A click on a group header collapses or expands it, as do Enter and a double click. Collapsed groups are remembered;
  a search shows their hosts.
- A host row shows a dot (green while one of its SSH panes runs), its name and hostname, and a star: click the star to
  make it a favourite or not. The row's tooltip adds `user@host:port`, the learned OS and address, the jump host and
  the number of sessions.
- Enter or a double click on a host returns to its most recently used running pane. When none runs, it shows the
  host's oldest [restored tab](#restored-ssh-tabs) that has not been shown yet, and otherwise connects in a new tab.
- A host's right-click menu: **Connect in new tab** and **Connect in split** (always a new session), **Browse files**
  (its home folder in [SFTP](#sftp)), **Edit…**, **Duplicate**, **Delete…** and **Add to favorites** or **Remove from
  favorites**; while the list has no headers, also **Rename group…**.

Under the list, the selected host's card shows its name, `user@host:port` and how it signs in: the credential's name,
"*n* Vault key(s)", "SSH agent", "credential missing", "needs Credential Vault" or "Vault locked". While the host has a
running pane the card reads "Connected" and its button **Open Shell** goes to that pane; otherwise the button is
**Connect**. The pencil edits the host.

In a window of its own ([Float and Window](workspace.md#float-and-window)) or docked at the bottom, the card gives way
to a column beside the list with the selected row's details: its name and address, **Edit…** and **Connect** (**Open
Shell** while a pane runs), and the host's Host, User, Port, Credentials, Jump host (when set), Group, System (when
learned) and Sessions, with **Connect in Split** and **Browse Files**. A distribution shows its Distribution, Version,
Command and Sessions, with **Open** and **Open in Split**.

### The toolbar's Sessions menu

The toolbar's **Sessions** menu lists the WSL distributions ("Ubuntu (WSL 2)"), then your hosts alphabetically
("prod-web (deploy@web1:22)"), then **Manage Sessions...**, which shows the Sessions panel. Choosing a host goes to its
most recently used running pane, else to its oldest [restored tab](#restored-ssh-tabs) not shown yet, or connects in a
new tab; choosing a distribution opens a new tab on it.

### Split with Same Host

**Split with Same Host** splits the active pane to the right with a new session on the same host. It is enabled while
the active pane is an SSH pane Remote opened.

## Saved hosts

### Adding and editing a host

**New Host...**, the Sessions panel's **+** and **Edit…** open the host form (**Add Host**, or **Edit** *name*):

| Field | What it holds |
|---|---|
| Name | Required and unique, ignoring case ("e.g. homelab") |
| Host, Port | The hostname or IP address (required) and the port (1 to 65535, default 22) |
| Username | The login. May be blank with a Vault login, whose username is then used; an SSH-agent host needs one |
| Jump host | **None**, or another saved host to connect through ([Jump hosts](#jump-hosts)) |
| Authentication | **Vault Credential** or **SSH Agent** ([Authentication](#authentication)) |
| Credential | With Vault: one Vault entry, chosen with **Choose…** |
| Keys | With Vault: SSH keys stored in the vault, "Tried in order"; **Add Stored Key…** adds one, and the list can be reordered and trimmed |
| Group | A group name, or blank for the default group |
| Add to Favorites | The host's star |
| Track shell folder | On by default: SFTP follows the terminal's current directory ([Following the terminal](#following-the-terminal)) |

A new host defaults to **Vault Credential** when Credential Vault is on, otherwise to **SSH Agent**; without the Vault
the option reads "Vault credential (needs Credential Vault)" and cannot be chosen. A credential and stored keys are
alternatives: choosing one clears the other. The footer shows the equivalent command (`ssh deploy@web1 -p 2222`) or
what is wrong ("A host needs a name", "The port must be a number from 1 to 65535", "Choose a credential, or use the SSH
agent", "A host named prod-web exists"). **Add Host** (or **Save**) saves; **Add and Connect**, offered when adding,
saves and then connects in a new tab, and connects nothing when the save fails. Enter saves nothing; Escape cancels.

### Groups and favourites

Every host with a blank group belongs to the default group. Right-click its header and choose **Rename group…** to
rename it (1 to 80 characters, not the name of another group); the name also applies to hosts added later. Favourites
lead the palette's SSH scope and show a filled star in the panel.

### Duplicating and deleting

**Duplicate** saves "*name* copy" ("*name* copy 2", …) with the same address, port, username, authentication, group and
jump host; the copy is not a favourite and tracks the shell folder. **Delete…** asks "Delete the saved host *name*? Open
sessions stay connected." and names the tunnels that go through the host; they stay, marked "Host missing". A host that
another host uses as its jump host cannot be deleted until that host no longer jumps through it.

### Importing from ~/.ssh/config

**Import from ~/.ssh/config...** (or **Import sessions** in the panel) reads your OpenSSH configuration and shows each
host it can import. Import needs Credential Vault: imported hosts always sign in with credentials stored in the vault,
never through the SSH agent.

- Read: `Host` blocks with `HostName`, `Port`, `User`, `ProxyJump` and `IdentityFile`. The first value of a key wins;
  `IdentityFile` lines add up in order (`IdentityFile none` is ignored). Lines before the first `Host` apply to every
  host and come first; a `Host *` block applies to every host at its place in the file. `Include` is followed one level
  deep, relative to `~/.ssh`, with wildcards in the file name.
- Defaults: the hostname is the alias, the port 22 and the user your local user name. `IdentityFile` may use `~`, `%d`,
  `%u`, `%r`, `%h`, `%p` and `%%`; a relative path is relative to your home folder.
- Listed under "Not importable": other `Host` patterns with `*`, `?` or `!`, `Match` blocks, a bad `Port` and lines with
  invalid quoting. A row with an unsupported `IdentityFile` (`${…}`, `~user`) or `ProxyJump` cannot be chosen.
  `ProxyJump` must be the alias of one host in the file or already saved ("Unsupported ProxyJump expression", "ProxyJump
  host missing"). A host whose jump host is not imported with it and signs in through the agent is refused: "Select and
  import the jump host too: *name*".

Rows read "New: *alias* — *user@host:port*" (checked) or "Update: …" for an alias you already have (unchecked), with the
key files below. A host without `IdentityFile` needs a **Credential**: a Vault password login, or a key stored in the
vault. **Import** then hands the key files to Credential Vault, which opens (creating or unlocking the vault), reads
each key, asks for the passphrase of an encrypted one (**Unlock key**), and shows **Import SSH keys** with each key's
fingerprint. **Import and use** stores the keys inside the vault and allows Remote to use them. A key whose fingerprint
the vault already holds is reused; a path key with that fingerprint (see
[Credential Vault](credential-vault.md#other-plugins-and-grants)) becomes a stored key and keeps its id and grants. The
key files are only read.

Remote then saves the hosts, signing in with their stored keys in the config's order, and reports "*n* new, *m* updated
SSH hosts saved using Vault credentials". An update replaces the host's address, port, user, jump host and keys, keeps
its group, favourite, id and creation date, and turns **Track shell folder** on. Because the keys live in the vault,
imported hosts connect without the original key files or the SSH agent. Keys are saved before hosts: if the hosts cannot
be saved ("Keys were saved in Vault, but hosts.toml was not. Refresh the import and retry."), **Refresh** and import
again to reuse them. When hosts changed while the preview was open, the import asks you to refresh it.

### hosts.toml

Hosts are saved in `plugins/dev.jasper.remote/data/hosts.toml` under the
[Jasper home](configuration.md#location-and-startup-options). You may edit it; Remote reads it again within a second of
a change, and rewrites it when you save from Jasper. It holds references to Vault entries, never a secret.

```toml
[[host]]
id = "5f2c…"                  # when absent, a new one on each read until Jasper saves the file
name = "prod-web"             # required, unique ignoring case
hostname = "web1.example.com" # required
port = 22                     # default 22
username = "deploy"           # may be blank with a Vault login
auth = "vault-keys"           # "agent" (default), "vault" or "vault-keys"
credentials = ["…", "…"]      # vault-keys: stored Vault keys, tried in order
# credential = "…"            # vault: one Vault entry
group = "Production"          # blank or absent: the default group
favorite = true
jump = "…"                    # another saved host's id
follow_directory = false      # Track shell folder; written only when off
created = 2026-10-01T09:00:00Z
updated = 2026-10-01T09:00:00Z
```

Give a host you add by hand an `id` before a tunnel, a Keymap binding or [Project Index](project-index.md) refers to
it: they all name the host by its id.

An entry Remote cannot read (a missing name, a bad id, a duplicate name or id) is skipped and logged. Saving from Jasper
writes only the hosts it read, so a skipped entry is dropped from the file: fix it before you change hosts in Jasper. A
file that is not valid TOML, cannot be read or is larger than 4 MiB keeps the last good hosts: the Sessions panel shows
the error, the palette shows "hosts.toml has errors: …", and saving from Jasper is refused until the file is fixed.
Saving also refuses a jump host that is missing or a chain of jump hosts that loops ("Jump hosts form a cycle at
*name*").

## Connecting

Connecting shows **Connecting to** *name* centred in the window: the host, `user@host:port`, what is happening and a
progress bar, over the window's content. **Cancel** (or Escape) stops it. The tab or split appears only once the shell
is ready, so a cancelled or failed attempt leaves no pane. After a failure, **Retry** tries again and **Close** (or
Escape) dismisses it. A second connection request in the same window brings the current one forward. Host-key and
Vault prompts appear while it waits.

The status line reads "Preparing connection…", "Connecting…", "Verifying host key…" (a key Jasper does not know yet),
"Authenticating…" and "Opening shell…".

### Authentication

| Authentication | How it signs in |
|---|---|
| Vault Credential: one entry | A Vault login (a password, a key, or both) or an SSH key. A blank username takes the login's |
| Vault Credential: stored keys | The keys stored in the vault, offered in order |
| SSH Agent | The keys of the agent at `SSH_AUTH_SOCK`, or on Windows, without it, the OpenSSH agent pipe `\\.\pipe\openssh-ssh-agent` |

Credential Vault asks, as for any plugin, whether Remote may use an entry (once, always for that entry, or not), and
asks you to unlock the vault first when it is locked. Remote reads the secret only to sign in. Locking the vault
afterwards leaves open sessions connected; the next connection needs it unlocked again.

Remote only lists the agent's keys and asks it to sign. It does not forward the agent to the server and never adds or
removes agent keys. With `use_ssh_agent = false` agent hosts do not connect.

### Jump hosts

A host with a jump host connects to the jump host first, then reaches the host through a port forward on that
connection. Each hop signs in with its own authentication and checks its own host key, so a first connection through a
new jump host asks about two keys. A jump host may itself have a jump host.

### Host keys

A host key Jasper does not know opens **Verify host key** *host*: "The authenticity of *host* port *port* can't be
established.", the key type and its SHA256 fingerprint, and "Compare it with the server's before trusting."

- **Trust and connect** saves the key in Remote's own `known_hosts` (`plugins/dev.jasper.remote/data/known_hosts`, the
  OpenSSH format, `[host]:port` for a port other than 22) and connects. When the file cannot be written, the
  connection stops ("Could not save the host key: …").
- **Connect once** connects without saving it.
- **Cancel**, Escape or closing the dialog stops the connection ("Host key not trusted"). No button answers Enter. An
  answer that does not come within `auth_timeout_seconds` counts as Cancel.

Keys in `~/.ssh/known_hosts` connect without asking, hashed entries included, while `read_user_known_hosts` is on;
Remote never writes that file. There, an `@revoked` entry for the key rejects it, `@cert-authority` lines are ignored
and lines Remote cannot read are skipped. A key that differs from the one either file holds for the host is rejected
("Host key rejected: fingerprint changed (known …, offered …)"). Remote's own file is strict: while any line in it does
not parse, every connection is refused ("known_hosts line 3 is not a known_hosts entry") until you fix or remove it.

### When a connection fails

| Message | Meaning |
|---|---|
| Could not resolve host *host* | The hostname did not resolve |
| No route to host *host*. On macOS, allow Jasper under System Settings > Privacy & Security > Local Network. | The address is unreachable; on macOS, Local Network access may be off for Jasper |
| Connection refused | Nothing accepts connections on that port |
| Timed out after *n* s | The server did not answer within `connect_timeout_seconds`, or signing in took longer than `auth_timeout_seconds` |
| Connection lost | The connection closed or was reset |
| Authentication failed (tried publickey, password) | The server refused every method offered; the list names those tried |
| SSH agent has no usable keys; load a key or choose Vault. | The agent holds no keys |
| SSH agent unavailable: … | The agent did not answer |
| SSH agent not available | No agent was found, or `use_ssh_agent` is off |
| needs Credential Vault | A Vault host while Credential Vault is off |
| Credential denied | Remote was not allowed to use the entry, or it is gone |
| Could not read the Vault key *name* | The key could not be loaded (a wrong passphrase, an unsupported format) |
| Import this key into Vault before connecting | A **Keys** entry is not a key stored in the vault |
| No username for *host* | No username, and the Vault entry has none |
| Vault username changed; reopen this host before connecting | The Vault login's username changed while connecting |
| Host key not trusted, Host key rejected: …, Could not save the host key: … | See [Host keys](#host-keys) |

## SSH sessions

An SSH pane's terminal type is `xterm-256color`, and Remote asks the server to set `COLORTERM=truecolor`. A pane Remote
opens in a folder (as [Project Index](project-index.md#projects-on-remote-hosts) does) starts the login shell there, or
in the home folder when the folder cannot be entered. An SSH tab's `{host}` [tab name piece](configuration.md#tab-names)
is the address of the saved host it opened, such as `db1.example.com`, and stays while the tab shows that host.

### Connections

Each SSH pane of a host with **Track shell folder** on (the default) has its own SSH connection, closed with the pane,
so each such pane signs in again. Hosts Remote has found to run Windows are the exception. All other use of a host
(panes of hosts with Track shell folder off, the SFTP browser, transfers, tunnels and the Editor) shares one connection
per host and login, which stays open for `session_linger_seconds` (5) after its last user. Remote sends a keepalive
every `keepalive_seconds` (30) and gives the connection up after three unanswered ones.

When the shell exits or the connection drops, the pane stays with "Disconnected (exit 0)" or "Disconnected: …",
**Reconnect** and **Close**. Reconnect connects again into the same pane, through the same progress overlay; a pane
opened in a folder starts there again. When a shared connection drops, every pane using it shows the notice.

The status bar's "*n* SSH sessions" item counts the SSH panes whose shell runs and is hidden at none; clicking it
toggles the Sessions panel.

### Host information

After a successful connection Remote reads the host's OS on a separate SSH command channel, never in your shell: `uname
-s`, then on Linux the `PRETTY_NAME` in `/etc/os-release`, or on Windows `cmd /c ver`. A direct connection also notes
the server's IP address when it differs from the hostname. The reads time out after a few seconds and a host that
refuses them still gives you its shell. Results are kept in `data/host-info.properties` and shown in the host's tooltip
and the Sessions search; they are hidden once the host's address, port or jump host changes, and refreshed on the next
connection. Opening the Sessions panel connects to nothing.

### Restored SSH tabs

When Jasper [restores its window](workspace.md#saving-and-restoring-the-windows), each SSH tab comes back to its host,
named after it, in the folder it was opened in, if any (a folder too long to save is left out), and waits until it is
first shown. Then it connects at once when signing in asks nothing (every host on its route signs in through the SSH
agent), after any connection in progress in that window. Otherwise, for example with a Vault login, locked or not, it
shows "Not connected: Reconnect to sign in" with **Reconnect** and **Close**; Reconnect connects and asks as usual. A
restored tab's own connection never asks: a host key that is not trusted yet, or cancelling the progress overlay, leaves
the same notice, and a failure "Not connected: *reason*". A tab whose host is no longer saved says "Its host is no longer
saved in Remote", or comes back as a placeholder that says why. An SSH agent that confirms each use (`ssh-add -c`,
1Password, Secretive) prompts outside Jasper when a restored tab connects.

## WSL distributions

On Windows with WSL, the Sessions panel starts with a **WSL** group: the distributions Jasper found for its shell
profiles (Docker Desktop's are left out), each with a badge such as "WSL 2" or "WSL 2 · default". Enter, a double
click, **Open** on the card or **Open in new tab** in the right-click menu opens one in a new local tab; **Open in
split** opens it in a new pane beside the current one. A dot, and a count above one, marks the panes Remote opened for
it whose shell still runs. Distributions are not saved hosts: they cannot be edited, duplicated, deleted or browsed with
SFTP, and the status bar's SSH count leaves them out. The group's collapsed state is kept apart from host groups (a
host group named "wsl" has its own), and the search matches distribution names and "WSL". With no SSH hosts saved, the
hint reads "No SSH hosts yet — Add or Import from ~/.ssh/config".

The toolbar's Sessions menu and the palette's SSH scope list them too (tagged `WSL`; Shift+Enter does nothing). The
list follows **Detect shells again** in Settings › Terminal › Shell without a restart. To make a distribution the shell
of new panes, choose it there (see [Shell, environment and history](configuration.md#shell-environment-and-history)).

## SFTP

### Opening the browser

There is one SFTP browser, in each window's SFTP tool window (in the lower half of the left side to start with). It
shows the SSH pane you focus in any window: focusing another SSH pane switches it, and focusing a local pane leaves it
as it is.

- **Open SFTP here** (Cmd+Shift+O, Ctrl+Alt+Shift+O; the Transfer menu or a terminal's right-click menu) opens the
  current SSH pane's folder. From any other pane it shows the browser, and when it shows nothing, asks **Browse files
  on SSH host** for a saved host.
- **Browse files** on a host in the Sessions panel opens the host's home folder.
- **Toggle SFTP** (Cmd+Option+2, Alt+2) shows or hides the tool window. While nothing is connected it reads "Not
  Connected" with **Choose Session**, a menu of your saved hosts.

The browser connects through the host's shared connection. When the SSH session of the pane it shows ends (the pane
closes, the shell exits or the connection drops), it clears and reads "SSH session closed"; transfers already queued go
on.

### Browsing

The tool window's header shows the host. The tools are **Back**, **Forward**, **Refresh**, **Show hidden items** and
**Upload** (a click uploads files; its menu holds the folder's other actions), over the path as breadcrumbs. Click a
crumb to open that folder, or click beside the crumbs to type a path (Enter goes, Escape cancels). Back and Forward
retrace the folders you visited in this pane; Option+Left, Option+Right and Option+Up on macOS, Alt+Left, Alt+Right
and Alt+Up on Linux and Windows (Back, Forward, Parent Folder) work anywhere in the panel.

The list shows Name and Size, sorted by name ignoring case (folders mixed in with files) until you click a header, with each entry's modified time in its tooltip.
In a window of its own or docked at the bottom it shows Name, Modified (date and time), Permissions (`rwxr-xr-x`) and Size.
Click a header to sort the whole folder by that column, again to reverse; drag a column boundary to resize it and double-click it to fit. The sort is shared by every SFTP browser and both layouts, and the sort and widths are remembered. A sort by Modified or
Permissions chosen in the wide layout also orders the narrow table, which shows no arrow because it lacks that column.
Select several rows for the file actions. A folder of more than 200 entries is shown 200 at a time ("1–200 of 950",
**Previous page**, **Next page**). Names starting with a dot stay hidden until you press **Show hidden items**; the
choice is remembered and applies to every browser opened afterwards. Enter or a double click opens a folder, opens a
file in the Editor ([Editing remote files](#editing-remote-files)), and follows a link to where it leads. A line under the list says what
is loading (with **Cancel**) and how the last action went.

### Following the terminal

**Follow terminal folder** ("Keep the SFTP browser in the active SSH terminal's folder"), at the foot of the panel, is
on for each pane until you change it. The browser remembers each pane's folder and choice.

- A shell that reports its folder (OSC 7) is followed from those reports.
- Otherwise, on Linux and macOS hosts with **Track shell folder** on, Remote reads the shell's folder with a fixed
  read-only script on a separate SSH command channel of the pane's own connection, a moment after you press Enter and
  when the browser is shown or the pane focused. Nothing is typed into your shell. The folder found this way is also
  the folder Jasper shows for the pane.
- Windows hosts are not followed. A host that does not let Remote read the folder shows "This host does not report the
  shell's folder".

Typing a path, opening a folder, **Up**, Back and Forward turn following off for that pane; **Refresh** keeps it.
Turning it back on returns to the shell's folder.

### File actions

A row's right-click menu acts on the selection when the row is selected, otherwise on that row; right-clicking beside
the rows acts on the selection.

| Item | What it does |
|---|---|
| Open in Editor | Opens the selected files, and links to files, in the Editor, at most 20 at a time ("Opened 20 of 57 files."); shown only with the Editor on |
| Up | The parent folder |
| New folder… | Asks for one folder name and creates it |
| Upload folder… | Uploads a local folder and everything in it |
| Download selected… | Copies the selection into a local folder you choose |
| Copy to host… | Copies the selection to another saved host |
| Copy paths | Puts the selected entries' full paths on the clipboard, one per line |
| Delete selected… | After "Delete *n* selected items from *host*:*folder*?", deletes them and everything in them |

**Copy to host** asks for the **Destination host** and a folder there, with **New folder**; **Copy here** queues the
copy. The data streams through Jasper without being saved on this computer. **Delete** checks each entry again first,
deletes links themselves and never what they lead to, and can be cancelled; what it already deleted stays deleted
("Cancelled; completed deletions were kept"). New folder and Delete are refused where they overlap a running transfer.

### Transfers

Uploads, downloads and host-to-host copies go into one queue that each window shows: at the foot of the SFTP panel
(three rows at a time), or in the Transfers tool window under a classic look. **Transfers** (Cmd+Option+3, Alt+3, or
Transfer › Transfers) shows them.

Before queueing, Remote checks whether the selected names already exist at the destination and asks once: **Replace**
(files are replaced, folders merged), **Skip existing** (existing files are kept, folders merged) or **Cancel**.

A row shows the direction (↑ upload, ↓ download, ⇄ host to host), what goes where ("logs (12 items) → prod-web:/srv"), a
progress bar and the status: "Queued", "Scanning… *n* items found", "Checking partial file…", "42% · 3.1 MiB/s · 2 min
left", "Paused", "Cancelling…", "Done", "Done · 2 failed · 1 skipped", "Needs attention — …", "Interrupted — …" or
"Failed — …". **×** cancels a transfer that has not finished (paused, interrupted and failed ones included) and removes
a finished one from the list. A row that needs you shows one link:

| Link | When |
|---|---|
| Resume | Paused, interrupted or failed |
| Resolve… | It needs a decision |
| Retry failed | It finished with items that failed |
| Retry cleanup | Cancelled with temporary files it could not remove yet |

**Resolve…** shows the conflicting file or folder and why: **Replace** (a file onto a file), **Merge** (a folder onto a
folder), **Skip**, **Rename…** (another name at the destination), **Restart file** or **Cancel**. **Apply to the rest of
this transfer** uses the answer for the other conflicts of the same kind, files or folders. Other files go on copying
while one waits.

A transfer that finished cleanly says "Done" for five seconds and leaves the list; cancelled ones, and finished ones
with failed items, stay until you remove them. Removing one whose cleanup is unfinished asks first, since partial files
may remain. The status bar shows the queue's progress, speed and what is left ("2 transfers", "1.2 MiB/s · 40 MiB left ·
7 files"), or what waits ("1 need attention", "2 paused"); clicking it shows the transfers, and its cancel control
cancels the only running transfer, or shows the list when several run. Up to `max_parallel_files` files copy at once, at
most two per transfer and two to or from any one host or this computer.

How copies behave:

- Each file is written to a hidden `.jasper-<id>.partial` file in the destination folder and put in place only when it
  is complete and checked. Replacing an existing file needs a server with atomic rename; Remote never deletes the old
  file first.
- Unfinished transfers are **Paused** when Jasper quits; nothing reconnects by itself at the next start. **Resume**
  checks the partial file's content before going on, which takes a while for a large one. When the saved host, its
  login or its jump host changed, Resume refuses ("Saved host or authentication identity changed; queue a new
  transfer").
- A source that changed during the copy, or a destination that changed since you decided, asks again. Two names in
  one transfer that could stand for the same file on a case-insensitive or Unicode-normalising system (`Readme` and
  `README`) stop it until you rename or leave out one of them.
- Links are copied as links. Modification times and the read, write and execute permissions are copied where the
  destination allows ("Metadata warning: …" otherwise); owners, ACLs, extended attributes and special mode bits are
  not. Special files are not transferable.
- Cancel stops the transfer and removes the temporary files it can prove are its own; files already put in place stay.

The queue is `plugins/dev.jasper.remote/data/transfers/queue.sqlite`, with `queue.lock` and SQLite's journal files. It
holds paths, the route and logins and the decisions you made, never credentials or file contents, and it records which
partial files are Remote's: keep it to clean up later. While the queue cannot be locked or is damaged, transfers are
unavailable (the status bar reads "Transfers unavailable") and SSH still works.

### Editing remote files

With the Editor on, Enter or a double click on a file in the SFTP browser, or **Open in Editor**, opens it in an Editor
tab; saving writes it back to the host. A link to a file opens the file it leads to (the link stays a link). Without the
Editor, opening a file does nothing.

Saving writes the new content to a hidden, owner-only `.<name>.jasper-<random>.tmp` beside the file, gives it the file's
permissions and replaces the file with it atomically (the `posix-rename@openssh.com` extension). Remote writes the file
in place instead, keeping its owner, group and links, when the folder does not allow the sibling, the sibling would get
another owner or group, the server does not report the file's permissions or it cannot rename atomically. An interrupted
write in place may leave a partial file; the tab stays unsaved, so you can save again. A read-only file is refused.

An atomic replacement gives the file a new inode: other hard links to it keep the old content, and a file bind-mounted
into a container shows the old content until the container opens it again. Before saving, the Editor compares the
file's modification time and size with what it opened and asks when someone else changed it; SFTP reports times to the
second, so a change within the same second that keeps the size goes unnoticed. Remote files are not watched; **Revert to
Saved** reads them again.

The Editor's reads and writes for one host and login go one at a time over one SFTP channel of the shared connection,
opened for the first and closed when none are left. In the Editor's [Markdown preview](editor.md#markdown-preview), a
remote file's relative links and images open from the same host, over that channel and with the same login. When the
connection has closed, the next one reconnects and asks you to sign in in the window the file was opened from, or the
active one; with no Jasper window open it fails ("Reconnecting to prod-web needs an open Jasper window for the sign-in
prompt.").

When Jasper restores its window, a remote file's Editor tab reads the file only when signing in asks nothing (an open
connection, or the SSH agent for every host on its route), and answers a host-key question with Cancel; otherwise it
says "Not signed in to prod-web; Retry signs in", and **Retry** signs in as usual. A file whose host is no longer saved
says "Its host is no longer saved in Remote", or comes back as a placeholder ([Editor tabs](editor.md#tabs)).

Remote files also reopen from the Editor's recent files, and Jasper's file chooser lists your saved hosts under
**Remote**, so **Open in Editor…** can open a file on a host and Save As can save there. Choosing a host connects to
it, and may ask you to sign in. See [Editor](editor.md).

## Tunnels

A tunnel forwards connections through a saved host, with its credentials, host-key trust and jump host. It runs on its
own: starting one opens no terminal, closing terminals does not stop it, and it uses the host's shared connection.

| Type | What it does |
|---|---|
| Local (`-L`) | A port on this computer reaches a target as the host sees it: `localhost:5432 → db.internal:5432` |
| Remote (`-R`) | A port on the server reaches a target as this computer sees it. Letting other computers use the server's port needs the server's `GatewayPorts` to allow it |
| Dynamic (`-D`) | A SOCKS5 proxy on this computer (no authentication, CONNECT only); names resolve on the server |

### The Tunnels panel

**Toggle Tunnels**, its rail button or the status bar item shows or hides the Tunnels tool window, which starts on the
left; **Show Tunnels** shows it without hiding it. It lists saved tunnels by name, then **Quick forwards**, with
**Filter tunnels** (name, host, addresses, ports and type). A row shows a state dot, the name and "via *host*" (or what is happening or wrong), the mapping and a switch. The
switch, Enter or Space starts or stops a tunnel; a double click edits a saved one; Delete or Backspace deletes it, after
asking. The row's tooltip carries its counters: open connections, bytes each way and uptime.

The right-click menu offers **Start** or **Stop**, **Open in Browser** (a running local tunnel, at
`http://localhost:<port>/`, in the web browser a plugin provides, else your browser), **Copy Address**, and **Edit…**, **Duplicate** and **Delete…** for a saved tunnel or
**Save as Tunnel…** for a quick forward. Deleting a running tunnel stops it. A tunnel whose host was deleted stays,
marked "Host missing · Edit to choose a host".

In a window of its own or docked at the bottom, the selected tunnel's details (the first tunnel's while none is
selected) stand beside the list: its name and state, the route as the tunnel form draws it, Type and Via and,
while it is up, Connections, Traffic and Uptime, with **Start** or **Stop**, **Edit…** (**Save as Tunnel…** for a quick
forward), **Open in Browser** and **Copy Address**.

The status bar shows "2 tunnels", "2 tunnels · 1 failing" or "1 tunnel failing" while any runs or failed, with the state
of each running, starting or failed tunnel in its tooltip; clicking it toggles the panel. **Stop All Tunnels** stops
them all.

### Adding a tunnel

**+**, **New Tunnel...** (which starts with the current SSH pane's host) or **Edit…** opens the tunnel form:

| Field | What it holds |
|---|---|
| Name | Required and unique ("e.g. Postgres") |
| Via host | The saved host to go through |
| Type | **Local**, **Remote** or **Dynamic (SOCKS)** |
| Listen on | `localhost`, `*` or an IP address, and the port (0 is any free port) |
| Forward to | The target host and port (Local and Remote only), as the host sees it (Local) or as this computer sees it (Remote) |
| Allow other computers to connect | Listens on every interface (`*`) |

A route under the ports shows the path from listener to target, and the footer shows the equivalent `ssh -L`, `-R` or
`-D` command, or a warning when another tunnel listens on the same port.

### Forward a Port

**Forward a Port...** (a terminal's right-click menu, the Session menu or the palette) forwards a port on a host to the
same port on this computer's loopback, or the next free port above it. From an SSH pane it uses that pane's host;
otherwise choose one. **More ▸** reveals **Forward to host** for a target other than the server itself, named as the
server sees it. The forward starts at once and is listed under Quick forwards until you stop it or Jasper quits; **Save
as Tunnel…** (or a double click) names and keeps it, and **Keep as a saved tunnel** saves it from the start.

### When a tunnel fails

| Message | Meaning |
|---|---|
| Port 5432 is already in use on this computer | Another program listens on the port |
| Port 80 needs administrator rights | The port is privileged |
| Cannot listen on *address*: not an address of this computer | The listen address belongs to another computer |
| The server refused port 8080: already in use or not allowed | The server would not open the remote port |
| Connection lost | The host's connection closed; the tunnel stops, start it again |
| Host missing | The tunnel's host was deleted |
| Connections are being refused: *reason* | The tunnel is up but cannot reach its target (for example `AllowTcpForwarding no`, or an unreachable target), until a connection succeeds |

### tunnels.toml

Saved tunnels are in `plugins/dev.jasper.remote/data/tunnels.toml`:

```toml
[[tunnel]]
id = "…"                    # optional; when absent it is derived from the name
name = "pg-prod"            # required, unique
host = "5f2c…"              # required: a saved host's id
kind = "local"              # local (default), remote or dynamic
bind_address = "localhost"  # localhost (default), * or an IP address
bind_port = 5432            # required; 0 is any free port
target_host = "db.internal" # local and remote
target_port = 5432          # local and remote
start = "manual"            # manual (default) or launch
created = 2026-10-01T09:00:00Z
updated = 2026-10-01T09:00:00Z
```

Tunnels start only when you start them; `start = "launch"` is kept but starts nothing. Edits apply within a second: a
running tunnel whose forwarding changed restarts, and a removed one stops. A file that does not parse (or is larger than
1 MiB) keeps the last good tunnels and shows "tunnels.toml has errors: …"; while it has entries Remote cannot read
(reported in the log), saving from Jasper is refused until you fix or remove them.

## Palette scopes

| Scope | Prefix | Enter | Cmd+Enter (Ctrl+Enter) | Shift+Enter |
|---|---|---|---|---|
| SSH | `ssh >`, or **Connect to SSH Host...** | Connect | Connect in split | Edit host… |
| Tunnels | `tunnels >`, or **Start or Stop a Tunnel...** | Start or stop | Open in browser (a running local tunnel) | Edit tunnel… (a saved tunnel) |

The Tunnels scope lists saved tunnels, then quick forwards, with the mapping, state and host. See
[Command palette](command-palette.md).

## Settings

Change Remote's settings in Settings › Tools › Remote, or in `plugins/dev.jasper.remote/dev.jasper.remote.toml` under
the Jasper home. Jasper reads the file live; a value outside its range is reported and the default used (see
[Plugins](configuration.md#plugins)).

| Key | Default | Range | Label | Applies |
|---|---|---|---|---|
| `connect_timeout_seconds` | `10` | 0 to 3600 | Connect timeout | To the next connection, and to tunnels started afterwards |
| `auth_timeout_seconds` | `30` | 0 to 3600 | Authentication timeout | To the next connection: signing in, agent requests and the host-key question |
| `keepalive_seconds` | `30` | 0 to 3600; 0 turns keepalives off | Keepalive interval | To the next connection |
| `session_linger_seconds` | `5` | 0 to 3600 | Session linger | The next time a shared connection is left unused |
| `read_user_known_hosts` | `true` | | Read ~/.ssh/known_hosts | To the next host-key check |
| `use_ssh_agent` | `true` | | Use the SSH agent | To the next connection |
| `[sftp]` `max_parallel_files` | `2` | 1 to 8 | Parallel files | At once |
| `[sftp]` `request_timeout_seconds` | `30` | 1 to 300 | Request timeout | To SFTP channels opened afterwards |

Remote's messages (a host that could not be saved, a file that could not be opened) are posted in the Remote
[notification group](notifications.md#groups) (`dev.jasper.remote`), and tunnel failures in the Tunnels group
(`dev.jasper.remote.tunnels`); change how each shows in Settings › Appearance & Behavior › Notifications › Groups.

## Files

Under `plugins/dev.jasper.remote/` in the Jasper home:

| File | Holds |
|---|---|
| `dev.jasper.remote.toml` | The [settings](#settings) |
| `data/hosts.toml` | Saved hosts ([hosts.toml](#hoststoml)) |
| `data/tunnels.toml` | Saved tunnels ([tunnels.toml](#tunnelstoml)) |
| `data/known_hosts` | The host keys you trusted, in the OpenSSH format |
| `data/panel-state.toml` | The default group's name (`default_group`), collapsed groups (`collapsed`, `wsl_collapsed`) and `sftp_show_hidden`; written by Jasper, and a broken file reads as defaults |
| `data/host-info.properties` | The OS and address learned per host; safe to delete |
| `data/transfers/` | The transfer queue (`queue.sqlite` and its journal files, `queue.lock`) |
| `data/browser-cache/` | Folder listings while the SFTP browser shows them, deleted after use |

SQLite's native library loads from a copy Remote writes into `jasper-sqlite` in the system's temporary folder while
Jasper runs.

Remote reads `~/.ssh/config` and the files it includes only when you import, and `~/.ssh/known_hosts` while
`read_user_known_hosts` is on; it writes neither.

## Under a classic look

Under a classic look, one whose [kit file starts from the classic base](looks.md#kit-and-base) as the
[Enterprise look](enterprise.md)'s does, Remote's tool windows take their classic layout. Remote chooses its layout
when it starts, so it follows a change of look at the next start of Jasper.

- **Toolbar**: the session combo, drawn as IntelliJ's run-configuration combo: IntelliJ's Application
  run-configuration icon and the chosen row's name (a long one cut with an ellipsis), over a list of your hosts
  ("prod-web (web1.example.com)"), then the WSL distributions ("No saved sessions" when there are neither), then
  **Manage Sessions…**, which shows the Sessions panel. Beside it the Run button, a green triangle with the tooltip
  **Connect**, goes to the chosen host's most recently used running pane, else to its oldest
  [restored tab](#restored-ssh-tabs) not shown yet, or opens one, or opens the distribution; it is disabled while there
  is nothing to run. The combo and the Sessions tree share the chosen row; until you choose, it is the current SSH
  pane's host, else the first.
- **Sessions** starts on the right, with its count beside the title: **Filter sessions** over a Name, Host and Port tree
  (favourites marked ★, groups opened and closed with their disclosure triangle, Enter or a double click), and below
  it the selected host's **Properties** (Host, User, Port, Credentials) with **Edit…** and **Connect** (**Open Shell**
  while it runs), or a distribution's Distribution, Version and Command with **Open**. In a window of its own or docked
  at the bottom, the Properties stand beside the tree instead of below it.
- **Tunnels** starts on the right, under Sessions: a Type, Local, Remote and Via table whose headers sort and resize. Enter or a double click starts
  or stops a row, the right-click menu holds the rest, and the line below reads "pg-prod — active. Click to toggle." In a
  window of its own or docked at the bottom, the selected tunnel's **Properties** (Type, Local, Remote, Via, State and,
  while it is up, Connections, Traffic and Uptime) stand beside the table, with that line under them.
- **SFTP** starts on the left: Back, Forward, Refresh, **Hidden files** and **Upload** over the breadcrumbs, and a
  Name, Size and Modified ("Sep 28") table; in a window of its own or docked at the bottom, Modified is the date and time
  and a Permissions column follows. While nothing is connected, a **Choose Session…** dropdown lists your hosts.
- **Transfers** is a bottom tool window ("No Transfers" when the queue is empty), listed with the other toggles in the
  Window menu; **Transfers** (Cmd+Option+3, Alt+3) shows it.

Under labelled tool window stripes, as in Enterprise, **Toggle SSH hosts**, **Toggle SFTP**, **Toggle Tunnels** and
**Transfers** make the tool window they show the active one, which takes the keyboard. A toggle of a shown tool window
that is not the active one activates it instead of hiding it; **Transfers** only shows, so it activates its tool window
each time.

## For plugin authors: dev.jasper.remote.api

Remote exports `dev.jasper.remote.api`, which names only JDK and SDK types, so other plugins can work with projects on
saved hosts; Project Index uses it. Declare Remote in your `plugin.toml`, as Project Index does:

```toml
requires = [{ id = "dev.jasper.remote", version = ">=0.12", optional = true }]
```

Compile against it with `compileOnly` and look the service up inside `try … catch (NoClassDefFoundError)`, so your
plugin runs without Remote ([reference](sdk-reference.html#package-dev.jasper.remote.api); [using an exported
API](plugin-apis.md#using-an-exported-api)).

Find `RemoteProjects` with [`Services.find`](sdk-reference.html#dev.jasper.sdk.services.Services) (Remote publishes it
with `Services.publish`). Call it on the UI thread. A stage completes on a worker thread once its remote work has
started; one that fails before then (no connection, or a failed sign-in) completes on the UI thread and may already be
complete when returned. Methods that take no window never connect or ask the user to sign in: `exists`, and `list`
without a window, fail with `NotConnectedException` ("*host* is not connected") while no connection is open.

| Method | What it does |
|---|---|
| `locate(UUID paneId)` | The saved host and folder of an SSH pane Remote opened, once its folder is known, as a [`RemoteFolder`](sdk-reference.html#dev.jasper.remote.api.RemoteFolder) (host id, host name, absolute path) |
| `connected(UUID hostId)` | Whether an authenticated connection to the host is open |
| `hostName(UUID hostId)` | The host's name; empty once it is no longer saved |
| `fileSourceId()` | Remote's file source id, `dev.jasper.remote.hosts`, for [`FileChoice`](sdk-reference.html#dev.jasper.sdk.files.FileChoice) sources |
| `folder(FileLocation.Sourced)` | The host and folder of a [location](sdk-reference.html#dev.jasper.sdk.files.FileLocation.Sourced) the file chooser returned from Remote's source |
| `exists(hostId, folders, names)` | Which names (`*` globs) exist in which absolute folders, in one command on the host (needs a POSIX `sh`, 10 s at most); the file-system root, the login's home folder and its ancestors report nothing |
| `list(hostId, root, ListOptions, Optional<WindowHandle>)` | A project's files, within its [`ListOptions`](sdk-reference.html#dev.jasper.remote.api.ListOptions) (folder names to exclude, at most `maxFiles` files, one timeout for all strategies, a cancellation check polled from a worker thread): Git's tracked and untracked files without ignored ones (unless the work tree is the home folder), else `find`, else an SFTP walk, as a `RemoteListing` with its strategy and whether it was cut short; with a window it may connect and ask the user to sign in |
| `open(hostId, path, window)` | Opens a remote file in the Editor; may ask the user to sign in, and reports failures itself |
| `openTerminal(hostId, folder, window)` | Opens an SSH tab whose shell starts in the folder |

Remote runs its commands with `sh -s`, every path single-quoted, and never types into the user's shell. `locate` reads
the pane's folder as one on its saved host, even after a nested `ssh` inside the pane. The [plugin APIs](plugin-apis.md)
guide covers the exported APIs together.
