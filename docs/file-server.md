# File Server

File Server shares a folder on this computer with other programs and computers over **HTTP, HTTPS, FTP, FTPS, SFTP or
TFTP**. It is a bundled plugin (`dev.jasper.fileserver`), on by default; [Plugins](plugins.md#file-server) lists its
capabilities. HTTPS is an HTTP server with **Use TLS** on, and FTPS an FTP server with **Use TLS** on. With the
[Credential Vault](credential-vault.md) installed, clients can sign in with a Vault account.

A server is a named definition: a folder, a protocol, a port, where it can be reached from and who may log in. Saved
servers live in `servers.toml` and are listed in the **File Servers** tool window, where a switch starts and stops each
one. Quick shares from the terminal and the command palette start a temporary server at once. Servers run inside Jasper
and stop when it quits.

## Commands

No File Server command has a default shortcut; assign one in
[Settings › Keymap](keyboard-shortcuts.md#settings--keymap).

| Command | ID | Where |
|---|---|---|
| Toggle File Servers | `dev.jasper.fileserver.show-panel` | palette, View › Tool Windows (the Window menu under the Enterprise look), the tool window's rail button, the status bar item |
| Share Folder… | `dev.jasper.fileserver.share-folder` | Transfer menu, palette; opens New File Server |
| New File Server… | `dev.jasper.fileserver.new` | Transfer menu, palette, the tool window's **+** |
| Start or Stop a File Server… | `dev.jasper.fileserver.servers` | palette; opens the palette's [File Servers scope](#the-command-palette) |
| Stop All File Servers | `dev.jasper.fileserver.stop-all` | palette; available while a server is starting, running or stopping |
| Show TLS Certificate Fingerprint… | `dev.jasper.fileserver.tls-fingerprint` | palette |
| Show SFTP Host Key Fingerprint… | `dev.jasper.fileserver.host-key-fingerprint` | palette |
| Serve Current Folder over HTTP | `dev.jasper.fileserver.serve-http` | palette |
| Serve Current Folder over FTP | `dev.jasper.fileserver.serve-ftp` | palette |
| Serve Current Folder over SFTP | `dev.jasper.fileserver.serve-sftp` | palette |
| Serve Current Folder over TFTP | `dev.jasper.fileserver.serve-tftp` | palette |
| HTTP, HTTPS, FTP, FTPS, SFTP, TFTP | `dev.jasper.fileserver.share-http`, `dev.jasper.fileserver.share-https`, `dev.jasper.fileserver.share-ftp`, `dev.jasper.fileserver.share-ftps`, `dev.jasper.fileserver.share-sftp`, `dev.jasper.fileserver.share-tftp` | a terminal's context menu › **Share Path**, palette (as **HTTP**, **HTTPS**, **FTP**, **FTPS**, **SFTP**, **TFTP**) |

## Safe by default

- **Read-only.** A new server refuses every upload, rename and delete ("This share is read-only") until **Allow
  changes** is on.
- **This computer only.** A new server listens on the loopback address, 127.0.0.1. **Reachable from** chooses **All
  networks** or one network interface instead.
- **Unprivileged ports.**

  | Protocol | Default port | Login of a new server |
  |---|---|---|
  | HTTP (and HTTPS) | 8080 (TCP) | Anonymous |
  | FTP (and FTPS) | 2121 (TCP), plus the passive data ports | Vault Account when the Credential Vault is installed, else Session Password |
  | SFTP | 2222 (TCP) | Vault Account when the Credential Vault is installed, else Session Password |
  | TFTP | 6969 (UDP) | none: TFTP has no login |

- **Jailed.** Clients cannot leave the shared folder, through `..` or through a link whose real location is outside it.
  See [The shared folder](#the-shared-folder).
- **Warnings.** The tool window, the server's details and the editor warn about a risky server:

  | Warning | When |
  |---|---|
  | "Plain HTTP sends the password unencrypted" / "Plain FTP sends the password unencrypted" | an HTTP or FTP server with a login and without **Use TLS** |
  | "Anyone who can reach it can change files" | a writable, anonymous HTTP or FTP server |
  | "TFTP has no login: anyone who can reach it can change files" | a writable TFTP server |
  | "Writable and reachable from other computers" | a writable server not limited to this computer |

  An FTP server with **Use TLS** but not **Require TLS** still lets a client log in without TLS; turn on **Require TLS**
  to refuse that.

## The File Servers tool window

**Toggle File Servers** shows or hides the tool window. It starts in the left side's top slot, and each window has its
own (see [Tool windows and the dock](workspace.md#tool-windows-and-the-dock)). Its header shows how many servers there
are and **New File Server…** (**+**). With no servers it shows "No File Servers" and **Share Folder…**, which opens New
File Server.

Saved servers are listed in the order of `servers.toml`, then a **Temporary** heading and the temporary servers. Each
row shows the server's name; its protocol (HTTP blue, FTP amber, SFTP green, TFTP purple); its address (the first
address it answers on while it runs, otherwise `127.0.0.1:<port>`, `0.0.0.0:<port>` or `<interface>:<port>`); the state
while it is starting, stopping, failed or waiting for the Vault, otherwise its first warning; and a switch. The row's
tooltip adds every warning, and for a running server reachable from other computers, "If other computers can't connect,
check your firewall (…)", naming the macOS Application Firewall, Windows Defender Firewall, or ufw or firewalld on
Linux. When `servers.toml` cannot be read, "servers.toml: *problem*" stands above the rows.

The switch starts a stopped, failed or waiting server, stops a running one and cancels a start; it is disabled while the
server stops.

| Key or gesture | Action |
|---|---|
| Enter | Start or stop the selected server, or cancel its start |
| Space | Operate the row's switch |
| Double-click | Show the server's details |
| D | Show or hide the details |
| Cmd+C (Ctrl+C) | Copy the server's first address |
| Escape | Hide the details |

Right-click a row for its menu:

| Item | Does |
|---|---|
| Start, Cancel Start or Stop | as the switch; disabled while the server stops |
| Edit… | edits a saved server ([Adding and editing a server](#adding-and-editing-a-server)) |
| Save as a Server… | on a temporary server: saves it ([Keeping a quick share](#keeping-a-quick-share)) |
| Duplicate | saved servers: saves a copy named "*name* copy" (then "*name* copy 2", …), with **Start when Jasper starts** off |
| Delete… | saved servers: asks "Delete ‘*name*’? Its settings are removed; the shared folder is not touched." (**Delete** / **Cancel**), stops the server and removes it |
| Reveal Folder | shows the shared folder in the system's file manager ("Could not show a server's folder" when it cannot) |
| Copy Address | copies the first address; available while the server runs |
| Copy Password | copies the password of a running server with a session password or a Vault account |
| Show Fingerprints… | TLS and SFTP servers: the [fingerprint dialog](#fingerprints) |
| Show Details / Hide Details | shows or hides the details |

The details belong to the selected server, or to the first one while none is selected, and are hidden until you open
them. They show the name and state; the folder (your home folder written `~`); the protocol, port, access ("read-only"
or "writable", the login, "TLS") and where it can be reached from, with the user name while it runs; every warning; "TLS
certificate SHA-256 …" for a TLS server and "SSH host key SHA256:…" for SFTP (or "created when the server first
starts"); every address the server answers on, each with **Copy**; **Start**, **Retry** (after a failure), **Cancel**,
**Stop** or **Stopping…**; **Edit…** (**Save as a Server…** on a temporary server); and the
[activity log](#activity-and-transfers).

While a File Servers tool window is visible, the addresses are recomputed every 10 seconds and each time it is shown, so
a server reachable from all networks picks up a network you joined.

The status bar shows "⇅ 1 server" or "⇅ *n* servers" while servers run; its tooltip lists each running server with its
first address, and clicking it toggles the tool window.

### Under a classic look

Under a classic look, one whose [kit file starts from the classic base](looks.md#kit-and-base) as the
[Enterprise look](enterprise.md)'s does, the tool window starts in the left side's bottom slot, beneath SFTP. It is a
table of **Folder** (name and protocol), **Address** and **Status** (a status dot and the state); Enter or a double
click starts or stops the selected server. A chosen server's details are **Properties — *name***: Folder, Protocol,
Port, Access, Reachable from, User, TLS certificate, SSH host key, its addresses and Warning. The row menu reads **Copy
URI** for Copy Address, adds **Copy *address*** for each further address, and has **Show Activity** / **Hide Activity**
in place of Show Details, which shows the activity log under the properties. New File Server lists the authorized keys
as a Type / Fingerprint / Comment table.

Under labelled tool window stripes, as in Enterprise, **Toggle File Servers** makes the tool window the active one when
it shows it, which takes the keyboard, and activates a shown tool window that is not the active one instead of hiding
it.

## Adding and editing a server

**Share Folder…**, **New File Server…** and **+** open **New File Server**; **Edit…** opens **Edit *name***. The form
has three sections:

| Section | Field | Notes |
|---|---|---|
| Server | Name | required, at most 80 characters, unique ignoring case |
| | Protocol | HTTP, FTP, SFTP or TFTP; fixed once the server exists |
| | Folder | set only with **Choose…** ("Folder to share"); it must exist when you save |
| | Reachable from, Port | **This computer only**, **All networks**, or an interface that is up, shown with its address such as "en0 (192.168.1.20)"; a saved interface that is gone shows "*name* (not available)". Port 1 to 65535 |
| | Passive ports | FTP only: a range such as `50000-50100`; empty uses the **Default FTP passive port range** setting |
| Access | Login | see [Logins](#logins) |
| | Account | for **Vault Account**: **Choose…** opens the Vault's picker; the account needs a user name and a password |
| | Authorized keys | SFTP only: **Add from File…**, **Paste**, **Remove** ([SFTP](#sftp)) |
| | Allow changes | "Clients can upload, rename and delete files" |
| Security & Startup | Use TLS | HTTP and FTP: serves HTTPS or FTPS |
| | Require TLS | FTP with Use TLS: "Refuse logins and transfers without it" |
| | Start when Jasper starts | starts the server when File Server starts ([Starting with Jasper](#starting-with-jasper)) |

On Linux, a port below 1024 adds "Ports below 1024 need root or CAP_NET_BIND_SERVICE on Linux — try *port*", 8000
higher. On a new server, choosing another protocol moves a port still at the old protocol's default to the new one's and
resets the login to the new protocol's default. The current warnings show as one notice, and the foot of the form
previews the address, such as `https://0.0.0.0:8080`, or shows what is wrong. **Add Server** (on an existing server
**Save**) is available once the form is valid. A new server is saved stopped.

Saving a server that is running with other settings asks "Restart *name*?" ("‘*name*’ is running with its old settings.
Restart it now to apply the changes?"): **Restart Now** stops and starts it, **Later** keeps it running as it was.

## Logins

| Login | Protocols | Clients sign in with |
|---|---|---|
| Anonymous | HTTP, FTP | no password |
| Keys Only | SFTP | an authorized key, under any user name |
| No login | TFTP | nothing: anyone who can reach the server can use it |
| Session Password | HTTP, FTP, SFTP | your user name on this computer and a 20-character password made each time the server starts, from letters and digits that cannot be misread (no 0, O, 1, l or I) |
| Vault Account | HTTP, FTP, SFTP | the user name and password of a login in the Credential Vault; offered while the Vault is installed |

**Copy Password** copies a running server's password, the session password or the Vault account's. `servers.toml` never
holds a password: a Vault login stores only the account's id.

A Vault account is read just before the server listens. Starting it yourself may ask over the window to unlock the Vault
and to allow File Server to use the account (see
[Other plugins and grants](credential-vault.md#other-plugins-and-grants)). A start fails with "Install or enable the
Credential Vault to use a saved account", "Vault account no longer exists — edit the server", "The Vault did not provide
the account" (for example when the request was denied) or "The Vault account '*name*' has no user name and password".
While the Vault is not installed, the editor shows a saved Vault server's login as "Vault account (needs the Credential
Vault)".

Wrong passwords are throttled per client address, across every server: after each failure the next attempt from that
address waits 1, 2, 4, 8 or 16 seconds, then 30 seconds, and ten failures within ten minutes lock the address out for
five minutes. A successful login clears the count, except during a lock-out. A refused early attempt is not checked:
HTTP answers 429 with a `Retry-After` header, FTP refuses the login (530 "Authentication failed."), and SFTP refuses
the password. A locked-out address is refused SFTP keys too.

## Starting and stopping

| State | Shown as |
|---|---|
| Stopped | "Stopped" |
| Starting | "Starting…" |
| Running | "Running" |
| Stopping | "Stopping…" |
| Failed | "Failed: *reason*", also written to the server's activity log as "error: *reason*" |
| Waiting for the Vault | "Waiting for the Vault to unlock" |

A start fails before it listens when the folder is gone ("The folder *path* does not exist") or another of your servers
that is starting, running or stopping holds the same port on an overlapping address ("Port *n* is already used by
‘*name*’"), whatever its protocol. When the system refuses the port, the reason is "Port *n* is already in use on
*address*", "Port *n* needs administrator rights on this system — use a port above 1024 (for example *port*)", "Could
not listen on port *n*: …" or "Network interface *name* is not available".

A server reachable from **All networks** listens on every address and lists as its addresses every IPv4 address, then
every IPv6 address, that is not link-local, of the interfaces that are up (not loopback or virtual), or the loopback
address when there is none. A server on one interface listens on, and lists, that interface's first IPv4 address (or its
first address). Addresses read `http://`, `https://`, `ftp://` (FTPS too), `sftp://user@host:port/`
(`sftp://host:port/` for **Keys Only**) and `tftp://`, with IPv6 addresses in brackets.

**Stop All File Servers** stops every server that is not stopped.

### Starting with Jasper

A saved server with **Start when Jasper starts** starts when File Server starts. One that uses a Vault account while the
Vault is locked waits in "Waiting for the Vault to unlock", without asking for the master password, and starts when you
unlock the Vault; the Vault may still ask to allow File Server to use the account unless you chose **Always allow for
File Server**. Use its switch to start it now instead. Temporary servers never start with Jasper.

## Quick shares

### Share Path from the terminal

Right-click a terminal and choose **Share Path** › **HTTP**, **HTTPS**, **FTP**, **FTPS**, **SFTP** or **TFTP**. The
server starts at once, without a dialog, with that pane's folder. It needs the folder of a local shell: a pane without a
known folder, one whose shell reported a remote folder, one whose folder is gone, or a pane a plugin provides (such as a
Remote SSH pane) gives "Could not share path" ("This terminal has no available local folder to share."). Share Path
never uses your home folder or another pane's folder instead.

### Serve Current Folder

**Serve Current Folder over HTTP**, **FTP**, **SFTP** or **TFTP** in the command palette does the same with the folder
of the pane the command runs for (or the active pane), or with your home folder when that pane has no local folder.
These servers never use TLS.

### What a quick share is

A quick share is a temporary server named "*folder* (*TYPE*)", such as "Downloads (HTTP)" (a longer folder name is cut
at 60 characters, and " 2", " 3" … is added when the name is taken). It is read-only and reachable from this computer
only. Its port is the protocol's default, or the next port above it, up to 99 above, that is free on the loopback
address for both TCP and UDP and not used by another of your servers that is starting, running or stopping; when all of those are taken it uses the
port 100 above without checking it. HTTP, HTTPS and TFTP shares need no login;
FTP, FTPS and SFTP shares get a session password (**Copy Password**). An HTTPS share has **Use TLS**, and an FTPS share
**Use TLS** and **Require TLS**, with File Server's [certificate](#the-tls-certificate). The tool window opens with the
new server selected, under **Temporary**.

### Keeping a quick share

A temporary server disappears when it stops. **Save as a Server…** opens **Save *name* as a Server**, with the same
fields as Edit; saving keeps it under its own id as a saved server, and it keeps running. Change **Reachable from**
there to share it with other computers.

## Activity and transfers

Each server keeps an activity log in memory, newest last, with times as `HH:mm:ss`: "*address* connected", "*address*
login *user*", "*address* login failed for *user*", "*address* created *path*", "*address* deleted *path*", "*address*
renamed *a* to *b*", "*address* error: *message*", and every transfer as "*address* ↓ *path*  *size*  *seconds*"
(download) or "↑ …" (upload), with "(stopped)" when it did not finish. The log shows 8 lines and scrolls to the newest;
**Clear** empties it and **Copy** copies it. It keeps at most **Activity entries kept per server** entries (500),
dropping the oldest. Control characters a client sends show as `?`.

A transfer still running after a second becomes a Jasper activity, "Sending *file*" or "Receiving *file*", with the
client's address, progress when the size is known, and **Cancel**, which drops that client's connection. It ends with
"*size* in *seconds*" or "Stopped after *size*". Clicking it toggles the File Servers tool window.

## The TLS certificate

HTTPS and FTPS servers present one self-signed certificate, which File Server creates the first time a TLS server
starts: an EC P-256 key signed with SHA-256, subject "CN=Jasper File Server", valid for ten years, for `localhost`,
`127.0.0.1` and this computer's host name. It lives in `data/tls/server.p12` with its generated password in
`data/tls/password`, readable by you only where the file system allows. Browsers and FTP clients warn about a
self-signed certificate: compare its SHA-256 fingerprint (uppercase hex pairs joined by colons) before you trust it. A
running server keeps the certificate it started with.

## Fingerprints

**Show Fingerprints…** on a server, or the palette's **Show TLS Certificate Fingerprint…** and **Show SFTP Host Key
Fingerprint…**, open a **Fingerprints** dialog; the palette commands need no server.

- **TLS:** "TLS certificate (SHA-256): …", or "No TLS certificate yet: Jasper creates one when a TLS server first
  starts.", with **Regenerate Certificate…** and **Close**. Regenerating asks first ("Clients that trusted the old
  certificate will warn about the new one. Running TLS servers keep the old one until they restart.", **Regenerate** /
  **Cancel**). A certificate that cannot be read is reported with "Regenerate it to make a new one."
- **SFTP:** "SSH host key: SHA256:…", or "No SSH host key yet: Jasper creates one when an SFTP server first starts.",
  with **Copy Fingerprint** and **Close**.

## The shared folder

Every protocol reaches the disk through the same rules:

- Client paths are separated by `/` or `\`. `..` stops at the shared folder (HTTP refuses it: "The path leaves the
  shared folder"); `~` and `/` name the shared folder in FTP, SFTP and TFTP.
- Links are followed, but only their real location counts: a path that ends outside the shared folder is refused however
  it gets there, and a link that points nowhere is refused. Listings leave out links that lead outside and links that
  point nowhere. Writes, deletes and renames act on a link itself, never on what it points to.
- Names no platform could share are refused and left out of listings: names with control characters or a colon, names
  ending with a dot or a space, Windows device names (CON, PRN, AUX, NUL, COM1–COM9, LPT1–LPT9) and `\\` network paths.
- Listings put folders first, then names ignoring case.
- An upload is written to a hidden `.jasper-upload-*.part` file in the same folder and replaces the target only once it
  has arrived completely, so an interrupted upload leaves the old file; the listing does not show it. A replaced file
  keeps its POSIX permissions. The shared folder itself cannot be deleted or renamed.

## Protocols

### HTTP and HTTPS

- Any browser can download from the folder listing (Name, Size, Modified). Files are always sent as downloads, never
  shown in the browser, so a shared HTML page does not run. Downloads support `HEAD`, `Last-Modified` and a single byte
  range.
- On a writable server the listing adds **Upload** (several files at once) and a **Delete** button per entry. From a
  terminal, `curl -O http://HOST:8080/file` downloads, `curl -T file http://HOST:8080/folder/file` uploads (`PUT`; the
  folder must exist) and `curl -X DELETE http://HOST:8080/folder/file` deletes a file or an empty folder.
- Logins use HTTP Basic authentication, so a plain HTTP server sends the password unencrypted; use **Use TLS**.
- The server answers only requests addressed to an IP address, `localhost` or this computer's name; a server reachable
  from this computer only answers only loopback addresses and `localhost`. Other requests get 421 (on a server without a
  login, with the text "Use this server's address, such as its IP address"), which stops a web page from reaching the
  server through DNS rebinding. Uploads and
  deletes from another web site are refused (403 "Requests from other sites are refused").
- At most 16 requests are handled at once.

### FTP and FTPS

An FTP server serves any FTP client (FileZilla, Cyberduck, `ftp`, `curl`).

- **Passive ports.** Data connections use the server's own range, else the **Default FTP passive port range** setting
  (50000–50100) as it was when the server started. Open those ports in your firewall as well as the server's port. Data
  connections must come from the client's own address.
- **Active mode** works, but connects only back to the client's own address.
- **Use TLS** turns on explicit FTPS: clients connect to the same port and send `AUTH TLS` (FileZilla calls this
  "Require explicit FTP over TLS"). Implicit FTPS (port 990) is not offered. **Require TLS** refuses `USER` and `PASS`
  before `AUTH TLS` (530 "This server requires TLS: use AUTH TLS before logging in") and any transfer or listing without
  `PROT P` (521 "This server requires TLS: use PROT P before transferring").
- A client that tries `anonymous` on a server with a login is disconnected and must reconnect. After three failed logins
  the server drops the connection.
- An upload replaces the file only once it has arrived completely. It is committed after the server sends its success
  reply: if saving then fails (a full disk, say), the client has already been told it worked, and the failure appears in
  the activity log as "Could not save *path*: …". Resuming an upload part-way through a file is refused; `APPE`, or a
  resume at the file's end, appends in place, so an interrupted append keeps the bytes already written.
- `MD5` and `MMD5` checksums work; file times cannot be set.
- An idle connection is closed after 5 minutes.

### SFTP

An SFTP server serves any SFTP client (`sftp`, `scp` from OpenSSH 9 on, FileZilla, Cyberduck, WinSCP). It serves file
transfer only: no shell, no commands and no port forwarding, so the legacy `scp` protocol, which runs a command, fails.

- **Logins.** A password (Vault account or session password) and/or **authorized keys**. A server with a password
  accepts keys only for its user name; **Keys Only** accepts any user name with an authorized key. File Server never
  reads `~/.ssh/authorized_keys`. A connection may make at most 10 sign-in attempts; an offered key that is not
  authorized does not count as a failed login.
- **Authorized keys.** **Add from File…** ("Public keys to allow", starting in `~/.ssh`) reads one or more public key
  files of at most 64 KiB, such as `~/.ssh/id_ed25519.pub`; a file that is larger, is not a regular file or cannot be
  read is skipped with the error "Could not read a public key file". **Paste** reads the clipboard. Each line is an OpenSSH
  public key of type `ssh-ed25519`, `ssh-rsa`, `ecdsa-sha2-nistp256`, `ecdsa-sha2-nistp384`, `ecdsa-sha2-nistp521`,
  `sk-ssh-ed25519@openssh.com` or `sk-ecdsa-sha2-nistp256@openssh.com`; blank lines, `#` comments and keys already in
  the list are skipped. A line with options such as `command=` or `from=` is refused ("Key options (such as command= or
  from=) are not supported: remove them from the line"), and so is anything else that is not a public key; one such line
  refuses the whole addition, with a message naming it ("Line *n*: …"). The list shows each key's type, `SHA256:`
  fingerprint and comment. A key that cannot be used fails the start ("Authorized key *n* can't be used: …").
- **Host key.** File Server creates one Ed25519 host key, shared by every SFTP server, the first time an SFTP server
  starts, and keeps it in `data/hostkey`, readable by you only. Clients ask you to trust it on first connection: compare
  the fingerprint with the details' "SSH host key SHA256:…" or the [fingerprint dialog](#fingerprints).
- **Jail.** Links cannot be created or read through SFTP; a refused path answers "permission denied". Server-side copy,
  hard links, byte-range locks, checksum and disk-space requests are refused; the `posix-rename`, `fsync` and `limits`
  extensions work.
- **Uploads.** A new or truncated file is written aside and replaces the old one only when the client closes it normally
  and every write succeeded; a dropped connection or a failed write (a full disk, say) leaves the old file. An exclusive
  create fails if the file appeared during the upload. Resuming, appending and writing part of a file happen in place.
- On a writable server a client may change a file's times and permissions, never its owner, group or ACL.

### TFTP

TFTP serves network boot files and firmware to devices that speak nothing else (PXE clients, switches, phones, embedded
boards). It has **no login and no encryption**: anyone who can reach the port can read the folder, and on a writable
server replace its files. Keep it on This computer only or on a private network, and read-only unless a device must
upload.

- Reads work on any server; writes only on a writable one, where a file is replaced only once it has arrived completely.
  A write that announces a size (`tsize`) larger than the free disk space is refused ("Disk full or allocation
  exceeded").
- In octet mode, a write that announced its size and delivers a different amount is refused ("Transfer size mismatch")
  and the old file kept. Netascii writes skip this check, because line ends change the size on the wire. A block larger
  than the agreed size is refused in either mode.
- Modes `octet` and `netascii`; options `blksize` (8 or more, larger values lowered to 65464; a smaller one is ignored)
  and `tsize`. Other options are ignored. Files of any size can be read.
- Each transfer gets its own UDP port; at most 16 run at once, and a further request is told "Server busy". A lost
  packet is resent after a second, up to five times.
- Errors never name paths on this computer.
- TFTP runs over UDP: open the port for UDP in your firewall. Each transfer also uses a random UDP port, so a strict
  firewall must allow the replies too.
- UDP senders can be forged. A forged read request makes the server send up to about 3 KB (the first block and its
  resends) to the forged address, and a few forged requests a second can keep all 16 transfers busy for about 6 s each.
  Keep TFTP on This computer only or on a trusted network.
- The server never answers an error packet, or a datagram that isn't well-formed TFTP, so a forged packet can't start an
  endless exchange with another TFTP server or with a service such as chargen or echo.

## The command palette

The palette's **File Servers** scope (`dev.jasper.fileserver.scope`, "Search file servers") lists every server with
"*PROTOCOL* :*port* · *folder*" and a tag: Stopped, Starting, Running, Stopping, Failed or Waiting. Typing matches the
name, protocol, port and folder. **Enter** (**Start or Stop**) starts, stops or cancels the start, as the switch does;
**Cmd+Enter** (**Ctrl+Enter**) **Edit** opens the editor. **Start or Stop a File Server…** opens the palette on this scope.
Its rows never appear among the palette's recently used rows. See [Bundled scopes](command-palette.md#bundled-scopes).

## Quitting

While a server is starting, running or stopping, quitting or restarting Jasper asks "Quit Jasper?" ("1 file server is
running and will stop." or "*n* file servers are running and will stop.", **Quit** / **Cancel**). Turn the question off
with **Ask before quitting while servers run**. While Jasper stays running in the background (see
[Quitting, background residency and restarts](workspace.md#quitting-background-residency-and-restarts)), closing its
windows, and on macOS Cmd+Q, leaves the servers running and asks nothing; they stop when Jasper really quits, for example
with **Quit Jasper Completely** in the macOS menu bar item or by logging out.

## Settings

**Settings › Tools › File Server** holds these settings, which live in
`plugins/dev.jasper.fileserver/dev.jasper.fileserver.toml` under [Jasper home](getting-started.md#jasper-home) (see
[Plugin settings](plugins.md#plugin-settings)):

| Key | Setting | Default | Values |
|---|---|---|---|
| `confirm_quit` | Ask before quitting while servers run | `true` | `true` or `false`; applies at once |
| `activity_entries` | Activity entries kept per server | `500` | 50 to 5000; applies to servers added afterwards (new, duplicated and temporary ones), and to existing saved servers the next time Jasper starts (restarting a server keeps its current limit) |
| `passive_ports` | Default FTP passive port range | `"50000-50100"` | `first-last`, 1 to 65535; applies to FTP servers started afterwards; an invalid value in the file counts as the default |

## Files

Under Jasper home, in `plugins/dev.jasper.fileserver/`:

| File | Holds |
|---|---|
| `dev.jasper.fileserver.toml` | the [settings](#settings) |
| `data/servers.toml` | the saved servers |
| `data/tls/server.p12`, `data/tls/password` | the [TLS certificate](#the-tls-certificate) and its password |
| `data/hostkey` | the [SFTP host key](#sftp) |

`servers.toml` is read when File Server starts. Saving from the tool window reads it again, applies the change and
rewrites it; comments and keys File Server does not know are not kept. Each server is a `[[server]]` table:

| Key | Default when missing | Values |
|---|---|---|
| `id` | made from the name | a UUID |
| `name` | required | 1 to 80 characters, unique ignoring case |
| `protocol` | required | `"http"`, `"ftp"`, `"sftp"` or `"tftp"` |
| `folder` | required | an absolute path |
| `bind` | `"loopback"` | `"loopback"` (this computer only), `"all"` (all networks) or `"interface:<name>"` |
| `port` | the protocol's default | 1 to 65535 |
| `passive_ports` | the setting | FTP only: `"first-last"` |
| `writable` | `false` | **Allow changes** |
| `login` | `"anonymous"`; `"session"` for SFTP | `"anonymous"`, `"session"` or `"vault:<account id>"`; TFTP is always `"anonymous"`, and an anonymous SFTP server needs keys |
| `authorized_keys` | `[]` | SFTP only: one public key line each |
| `tls` | `false` | HTTP and FTP only |
| `require_tls` | `false` | FTP with `tls` only |
| `autostart` | `false` | **Start when Jasper starts** |

An entry File Server cannot read is skipped and reported ("servers.toml has a problem", naming the entry as "server *n*:
…"); a file that is not valid TOML, is larger than 1 MiB or cannot be read keeps the servers last read and is reported
as "servers.toml has errors". Either way File Server saves nothing until the file is fixed.
