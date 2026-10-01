# Jasper File Server plugin: share a local folder over HTTP, FTP/FTPS, SFTP and TFTP

Status: approved design, 2026-09-30. A new bundled plugin, File Server (`plugins/fileserver`, `dev.jasper.fileserver`),
hosts servers that share a local folder with other programs and computers, like MobaXterm's Servers window. It runs on
macOS, Linux and Windows. Built on the SDK (`jasper-sdk`, 0.10.1 → 0.11.0), the app (`jasper-app`) and, optionally,
the Credential Vault API (`dev.jasper.vault.api`), all on local `main` at ea35152d.

## Outcome

- A **File Servers** rail panel lists any number of named server definitions. Each row starts and stops its server,
  shows the shared folder, the access summary and every URL other machines can use, with copy.
- Four protocols: **HTTP** (optionally HTTPS), **FTP/FTPS**, **SFTP** and **TFTP**. File sharing only.
- **Serve Current Folder over HTTP / FTP / SFTP / TFTP** starts a temporary, read-only server rooted at the focused
  pane's working directory in one command.
- Safe by default: read-only, bound to `127.0.0.1`, users confined to the shared folder, warnings for risky
  combinations, a status-bar reminder while anything is running, and a confirmation before quitting.
- Servers stop when Jasper quits. Nothing needs administrator rights.

## Decisions (user, 2026-09-30)

1. **Host servers** (MobaXterm's Servers window), not a client for remote shares.
2. **Protocols: HTTP, FTP/FTPS, SFTP, TFTP; file sharing only.** No SMB, NFS, WebDAV, Telnet, VNC, iperf or Cron.
3. **UI: a rail panel plus a status-bar item**, with palette commands.
4. **Access:** read-only by default with an "Allow changes" switch; login per server; users jailed to the folder;
   `127.0.0.1` by default; high default ports.
5. **Credentials:** a server's password login uses an **existing Vault credential chosen with `VaultApi.pick()`**
   when the Vault is installed; otherwise (or by choice) a **session password** generated on every start and never
   saved. The Vault API is not extended.
6. **Any number of named definitions**, plus temporary ones from "Serve Current Folder".
7. **Lifecycle:** servers run in-process, stop when Jasper quits (with a confirmation), optional per-server "Start when
   Jasper starts" (off by default), an in-memory activity log per server.
8. **Approach 1:** one engine per protocol over a shared, jailed `ShareRoot`.
9. **New SDK capability `network.listen`**, declared by File Server and consented to by the user.

## Alternatives considered

- **SMB and NFS.** The only open-source Java SMB server, JFileServer (LGPLv3), speaks SMB1 alone (SMB2/3 is a paid
  add-on); modern Windows and macOS disable SMB1, and Windows already holds port 445. nfs4j (LGPLv2) is a real NFSv3/4.1
  server, but NFSv3 needs a portmapper on port 111 and macOS clients expect a reserved source port; it is impractical
  without root. Both are out.
- **WebDAV** would let users mount a share in Finder, Explorer and GNOME; a candidate for a later version.
- **A custom jailed `java.nio.file.FileSystem` consumed by every engine.** Apache FtpServer does not use NIO file
  systems and the JDK's `SimpleFileServer` requires the default file system, so adapters are still needed; more code
  for the same guarantee.
- **A separate helper process** (for ports below 1024 and crash isolation). Needs IPC, a second launcher on three
  platforms and elevation prompts, and contradicts "stops when Jasper quits".
- **Extending `VaultApi` with a create call** so File Server can generate and save a password in one click. Adds a
  write path to the Vault's public API that needs its own review; not needed, because the Vault's own dialog can
  generate a password.
- **One server per protocol** (MobaXterm's model). Cannot serve two folders over the same protocol.

## Plugin shape

### Manifest

`plugins/fileserver/src/main/resources/plugin.toml`:

```toml
id = "dev.jasper.fileserver"
name = "File Server"
version = "0.1.0"
entry = "dev.jasper.fileserver.FileServerPlugin"
sdk = ">=0.11.0, <0.12"
description = "Share a local folder over HTTP, FTP/FTPS, SFTP or TFTP: named servers in a File Servers panel, read-only and local-only by default, with an activity log, Vault logins and Serve Current Folder."
vendor = "Jasper"
icon = "icons/fileserver.svg"
homepage = "https://github.com/an0nn30/Jasper"
capabilities = ["network.listen", "terminal.observe", "palette.contribute"]
requires = [{ id = "dev.jasper.vault", version = ">=0.2", optional = true }]
```

`terminal.observe` is for the focused pane's working directory in Serve Current Folder.

### SDK 0.11.0

`JasperSdk.VERSION` becomes `0.11.0`; every bundled plugin's `sdk` range becomes `<0.12`.

1. **`Capabilities.NETWORK_LISTEN = "network.listen"`**, documented as "Accepting connections from other programs or
   computers, such as serving files. @since 0.11.0", and added to `Capabilities.ALL`. Consent only; the app does not
   (and cannot) enforce it, consistent with the class's "They are not a sandbox". The existing consent UI shows it.
2. **`Windows.onQuitting(Supplier<CompletableFuture<Boolean>> guard)`** returning a `Subscription`. Asked on quit and
   restart together with the existing plugin-window guards and `onTabsClosing` guards; any `false` cancels; a guard
   that throws or fails is treated as allowing the quit (as window guards are). Default method throws
   `UnsupportedOperationException`. Implemented by the app and by the testkit fake, with a testkit contract test.

### Build

`plugins/fileserver/build.gradle.kts`, shaped like Remote's:

- `compileOnly`: `:jasper-sdk`, `:jasper-plugin-vault`.
- `implementation`: `org.apache.ftpserver:ftpserver-core:1.2.1`, `org.apache.sshd:sshd-core:2.19.0`,
  `org.apache.sshd:sshd-sftp:2.19.0` (Remote's version; plugins have separate class loaders),
  `org.bouncycastle:bcprov-jdk18on` and `bcpkix-jdk18on` (certificate generation; same version as Remote's `bcprov`),
  `org.slf4j:slf4j-jdk14`, `org.tomlj:tomlj:1.1.1`.
- `testImplementation`: the SDK, the testkit, the Vault, `commons-net:commons-net` (FTP, FTPS and TFTP clients).
- HTTP and TFTP use only the JDK.
- Becomes `:jasper-plugin-fileserver` automatically (`settings.gradle.kts` includes every `plugins/<name>/` with a
  build script) and is staged like the other bundled plugins.

### Packages

Each package has a `package-info.java` naming its allowed outgoing dependencies, as elsewhere.

| Package | Owns |
|---|---|
| `dev.jasper.fileserver` | `FileServerPlugin` (start/stop wiring), `FileServerSettings` |
| `dev.jasper.fileserver.share` | `ShareRoot`, `ShareEntry`, `ReadOnlyShareException`, `PathRefusedException` |
| `dev.jasper.fileserver.engine` | `ServerEngine`, `ServerEvents`, `ServerAddresses`, `LoginThrottle`, `EngineFailure`, `TlsMaterial` |
| `dev.jasper.fileserver.engine.http` / `.ftp` / `.sftp` / `.tftp` | One engine each; the only packages that name vendor types |
| `dev.jasper.fileserver.definition` | `ServerDefinition`, `Protocol`, `Bind`, `Login`, `DefinitionStore` |
| `dev.jasper.fileserver.runtime` | `ServerRegistry`, `RunningServer`, `ServerState`, `ActivityLog` |
| `dev.jasper.fileserver.auth` | `LoginResolver`, `ResolvedLogin`, `SessionPasswords`, `AuthorizedKeys` |
| `dev.jasper.fileserver.ui` | Panel, editor dialog, status item, palette commands, quit guard |

Dependency direction: `ui → runtime → engine.* → engine, share, auth, definition`; `auth → definition` and, only in
`LoginResolver`, `dev.jasper.vault.api`. Vendor types never leave `engine.*`. Add the plugin to the existing
`verifyPluginArchitecture` checks; if a package-DAG check is cheap to reuse from Remote, apply it.

## `ShareRoot`: the security boundary

Every engine reaches the disk only through `ShareRoot`. It is built from the folder's real path (`toRealPath()`) and a
`readOnly` flag.

**`resolve(String clientPath)`** turns a client path (always `/`-separated, relative to the share) into a `Path`, or
throws `PathRefusedException`:

1. Refuse NUL and other control characters, a drive letter (`C:`), a UNC or `\\` prefix, a `:` anywhere (Windows
   alternate data streams), Windows reserved device names (`CON`, `PRN`, `AUX`, `NUL`, `COM1`–`COM9`, `LPT1`–`LPT9`,
   with or without an extension, any case) and names ending in a dot or space. These rules apply on every OS, so
   a share behaves the same everywhere and the rules are tested everywhere.
2. Split on `/` (and `\`), drop empty and `.` segments, apply `..` against the segment stack; a `..` that would climb
   above the root is refused, not clamped.
3. For an existing target, its real path must equal the root or start with it. For a target that does not exist yet
   (upload, mkdir, rename destination), its parent's real path must, and the final component must not be an existing
   symbolic link.

**Operations** are methods on `ShareRoot`: `list(dir)` (returns `ShareEntry` name, kind, size, modified time; entries
that are symlinks pointing outside the share are omitted), `attributes`, `openRead`, `openWrite(create, truncate or
append)`, `mkdir`, `delete` (file or empty directory), `rename`. Final components open with
`LinkOption.NOFOLLOW_LINKS`. Every write operation throws `ReadOnlyShareException` when `readOnly`, so read-only
never depends on an engine remembering to check. The root itself cannot be deleted or renamed.

**Residual risk**, stated in the plugin README: a local user who can already write inside the share could swap a
checked directory for a symlink between the check and the open. Protecting against local users with write access to
the share is out of scope.

## Engines

```java
interface ServerEngine {
    void start() throws EngineFailure;   // binds synchronously; returns when listening
    void stop();                         // closes the listener, drops live sessions; idempotent
    List<URI> addresses();               // reachable URLs for the bound address(es)
}
```

Each engine is built from a `ServerDefinition`, its `ShareRoot`, a `ResolvedLogin`, a `ServerEvents` sink, the shared
`LoginThrottle` and, for TLS, `TlsMaterial`. `ServerEvents` receives `connected(remote)`, `loginSucceeded(remote,
user)`, `loginFailed(remote, user)`, `transferStarted/Progress/Finished(remote, path, direction, bytes)` and
`error(remote, message)`.

**`LoginThrottle`** (shared by HTTP, FTP and SFTP): after each failed login from a remote address the next attempt from
it is delayed (1 s, doubling, capped at 30 s); ten failures within ten minutes lock the address out for five minutes.
A success resets it. Uses an injected clock.

**`ServerAddresses`**: for `loopback`, `127.0.0.1` (and `[::1]` when bound); for `all`, every up, non-loopback,
non-virtual IPv4 address and global IPv6 address; for `interface:<name>`, that interface's addresses. Formats URLs
per protocol (`http`/`https`, `ftp`, `sftp://user@host:port`, `tftp`).

### HTTP

- JDK `com.sun.net.httpserver.HttpServer` (or `HttpsServer` when TLS is on) with Jasper's own handler, on a bounded
  thread pool (16 threads). `SimpleFileServer` is not used: it cannot take a `ShareRoot` or authentication.
- `GET`/`HEAD`: a directory returns an HTML listing (every name HTML-escaped and URL-encoded; name, size, modified;
  parent link); a file streams with `Content-Type` from `Files.probeContentType` (fallback
  `application/octet-stream`), `Content-Disposition` for download, and single-range `Range` support (206, 416).
  A directory URL without a trailing slash redirects to one.
- Login other than anonymous uses HTTP Basic auth, realm = server name.
- When writable: the listing includes an upload form (`multipart/form-data` `POST`, streamed to disk, no size limit
  beyond disk space), `PUT` creates or replaces a file, `DELETE` removes one, and each listing entry has a delete
  button (a form POST with `_method=DELETE`). Read-only servers answer these with 403.
- Responses carry `X-Content-Type-Options: nosniff` and a restrictive `Content-Security-Policy` on listings.
- HTTPS (optional) uses `TlsMaterial`; plan FS-1 ships HTTP only and FS-2 turns HTTPS on.

### FTP/FTPS

- Apache FtpServer: `FtpServerFactory` with one `ListenerFactory` on the bound address and port; a passive port range
  (default from settings, 50000–50100) and the passive external address set to the bound address (for `all`, the
  address the control connection arrived on).
- A `UserManager` holding either the anonymous user or the single resolved user; `WritePermission` only when
  writable; a `FileSystemFactory` whose `FtpFile`s delegate to `ShareRoot`.
- An `Ftplet` reports logins, uploads and downloads to `ServerEvents` and consults `LoginThrottle`.
- Explicit FTPS (`AUTH TLS`) when TLS is on; "Require TLS" refuses `USER` before `AUTH TLS` and requires `PROT P`.
  No implicit FTPS.

### SFTP

- MINA `SshServer.setUpDefaultServer()` with an Ed25519 host key persisted in `data/hostkey`
  (`SimpleGeneratorHostKeyProvider`), whose SHA-256 fingerprint the panel shows.
- Only the SFTP subsystem: no shell factory, no command factory, `RejectAllForwardingFilter`, agent and X11
  forwarding off. OpenSSH 9+ `scp` uses the SFTP protocol by default, so `scp` clients work.
- `PasswordAuthenticator` for a password login; `PublickeyAuthenticator` over the definition's authorized keys; both
  go through `LoginThrottle`. No anonymous login.
- A custom `SftpFileSystemAccessor` routes every resolve, open, directory read, mkdir, remove and rename through
  `ShareRoot`; an `SftpEventListener` reports transfers.

### TFTP

- Hand-written RFC 1350 server on a `DatagramSocket`: read (RRQ) and, only when writable, write (WRQ) requests;
  `octet` and `netascii` modes; options `blksize` (RFC 2348, 8–65464) and `tsize` (RFC 2349) through OACK (RFC 2347).
- Each transfer runs on its own ephemeral socket on the plugin's executor; a lost packet is resent after 1 s, up to
  5 times; at most 16 concurrent transfers (further requests get error 0 "Server busy").
- Errors as protocol error packets: 1 file not found, 2 access violation (refused path, read-only server, a
  directory as the target), 3 disk full. A WRQ on a writable server replaces an existing file, as the other protocols
  do.
- No login and no TLS; the editor says so.

### TLS material

`TlsMaterial` (plan FS-2) generates, on first use, a self-signed certificate (EC P-256, 10-year validity, subject
`CN=Jasper File Server`, SANs `localhost`, the host name and `127.0.0.1`) with BouncyCastle, stores it in
`data/tls/` as a PKCS#12 keystore protected by a random password kept beside it, both owner-only where the OS
supports POSIX permissions, and exposes an `SSLContext` and the SHA-256 fingerprint. **Regenerate** replaces it.

## Definitions and persistence

Under `<home>/plugins/dev.jasper.fileserver/`:

- **`data/servers.toml`**: the saved definitions (plugin data, like Remote's `hosts.toml`). Written atomically (temp
  file, then move). A malformed entry is skipped and reported once through `Notices`; the rest load.
- **`data/tls/`**, **`data/hostkey`**: as above.
- **`dev.jasper.fileserver.toml`**: the declared settings, shown in Settings › Plugins:
  - `confirm-quit` (boolean, default true): "Ask before quitting while servers run".
  - `activity-entries` (integer, default 500, 50–5000): "Activity entries kept per server".
  - `passive-ports` (string, default `50000-50100`): "Default FTP passive port range".

**`ServerDefinition`** (record, validated on construction):

| Field | Type | Notes |
|---|---|---|
| `id` | UUID | Stable; temporary servers get one too but are never saved |
| `name` | String | Non-blank, unique among definitions |
| `protocol` | `HTTP`, `FTP`, `SFTP`, `TFTP` | Fixed after creation |
| `folder` | Path | Absolute |
| `bind` | `loopback`, `all`, `interface:<name>` | Default `loopback` |
| `port` | int 1–65535 | Defaults: HTTP 8080, FTP 2121, SFTP 2222, TFTP 6969 |
| `passiveRange` | optional range | FTP only; empty means the setting |
| `writable` | boolean | Default false |
| `login` | `anonymous`, `vault:<uuid>`, `session` | `anonymous` only for HTTP and FTP; TFTP has none; SFTP needs `vault` or `session` unless it has authorized keys |
| `authorizedKeys` | list of OpenSSH public key lines | SFTP only; public data |
| `tls` | boolean | HTTP and FTP only |
| `requireTls` | boolean | FTP only; implies `tls` |
| `autostart` | boolean | Default false; never for temporary servers |

Defaults for a new definition: HTTP anonymous; FTP and SFTP `vault` when the Vault is present, else `session`.

## Logins

`LoginResolver` turns a definition's `login` into a `ResolvedLogin` just before binding:

- `anonymous`: no credentials.
- `session`: `SessionPasswords` generates a 20-character password from an unambiguous alphabet; the user name is the
  OS user name. Held in memory for the life of that run only.
- `vault:<uuid>`: `VaultApi.ensureUnlocked(owner)` (which may prompt), then `credential(owner, uuid)`; the credential
  must be `ACCOUNT_PASSWORD` or `ACCOUNT_KEY_AND_PASSWORD` and supplies the user name and password. A missing credential
  fails the start with "Vault account no longer exists — edit the server." Only the UUID is stored in
  `servers.toml`.
- Authorized keys are parsed by `AuthorizedKeys` (OpenSSH `authorized_keys` line format, options ignored and
  refused if present, so no `command=` surprises).

The editor's "Vault account" choice uses `VaultApi.pick(owner)` and shows the credential's name; it is disabled with
"Install or enable the Credential Vault to use a saved account" when the Vault is absent.

## Runtime

**`ServerRegistry`** owns running servers, keyed by definition id, and publishes state changes on the UI thread.

States: `STOPPED → STARTING → RUNNING → STOPPING → STOPPED`, plus `FAILED(reason)` (from STARTING or RUNNING) and
`WAITING_FOR_VAULT` (autostart only).

- **Start**: validate the definition and that the folder exists; refuse a clash with another running server on the
  same port and overlapping address ("Port 8080 is already used by ‘build-artifacts’"); resolve the login; build
  `ShareRoot`; bind on the plugin's background executor. The row shows a spinner; Cancel aborts a pending start.
- **Autostart** when the plugin starts: servers with `autostart` start in the background. A server whose login is a
  Vault account while the Vault is locked enters `WAITING_FOR_VAULT` without prompting and starts when
  `VaultApi.LOCK_STATE_CHANGED` reports it unlocked, or when the user clicks Start (which prompts).
- **Editing a running server**: saving offers "Restart now to apply" / "Later".
- **Folder removed while running**: each operation fails and is logged; the row shows a warning; the server stays up.
- **Addresses** are recomputed when the panel is shown and every 10 s while it is visible.
- **Stop all**: `Plugin.stop()` and Stop All Servers stop every server in parallel, waiting up to 5 s each.

**`ActivityLog`**: per server, a bounded in-memory ring (the `activity-entries` setting) of timestamped entries:
connected, login succeeded/failed, transfer (direction, path, bytes, duration), error. Never written to disk.

**Transfers** that last longer than about a second also begin an SDK activity (`Activities.begin`) with progress; its
cancel drops that client's connection.

## UI

### File Servers panel

`PanelSpec("dev.jasper.fileserver.panel", "File Servers", icon, Anchor left)` (rail panels always open on the left, beside the rail), toggled by the plugin's own
`dev.jasper.fileserver.show-panel` action.

```
 File Servers                         [+]
 ─────────────────────────────────────────
 ● build-artifacts   HTTP   :8080     [■]
   ~/work/app/build/libs   read-only
   http://192.168.1.20:8080  ⧉
 ○ firmware          TFTP   :6969     [▶]
   ~/tftpboot              read-only
 ● phone-drop        FTP    :2121  ⚠  [■]
   ~/Downloads/drop        writable · TLS
   ftp://192.168.1.20:2121  ⧉   user: dustin
 ─────────────────────────────────────────
 Activity — phone-drop                 ⌄
 14:02:11 192.168.1.44 login dustin
 14:02:15 ↑ IMG_2231.HEIC  3.1 MB  0.4 s
```

- A row: state dot (running, stopped, starting, failed, waiting), name, protocol, port, Start/Stop button; then the
  folder (home abbreviated to `~`) and access summary (read-only / writable, anonymous / user / keys, TLS); then one
  line per URL with a copy button. A failed row shows the reason with Retry.
- **Warning** icon with a tooltip for: plain HTTP with a password; writable TFTP; any writable server bound to all
  networks; anonymous with writes.
- Context menu: Start/Stop, Edit…, Duplicate, Delete…, Reveal Folder, Copy Address, Copy Password (session password,
  or the Vault credential after unlocking), Show Fingerprints (TLS and SSH host key).
- **Temporary** servers are listed under a "Temporary" header in italics and disappear when stopped; Edit… on one
  offers "Save as a server…".
- The lower pane shows the selected server's activity log with Clear and Copy.
- The firewall tooltip on every running row: "If other computers can't connect, check your firewall", with the
  platform's firewall named (macOS Application Firewall, Windows Defender Firewall, `ufw`/`firewalld`).

### Server editor

One `PluginDialog` for New and Edit:

- **General**: name; protocol (locked after creation); folder (Jasper's chooser in Directory mode); "Start when
  Jasper starts".
- **Network**: Reachable from (This computer only / All networks / each up interface by name and address); port;
  for FTP, the passive range.
- **Access**: "Allow changes (upload, rename, delete)"; Login: Anonymous (HTTP, FTP) / Vault account (picker) /
  Session password; for SFTP, authorized keys with Add from File… and Paste, and Remove.
- **Security**: Use TLS (HTTP, FTP); Require TLS (FTP).
- Inline validation: name unique and non-blank; folder exists; port 1–65535; on Linux, a port below 1024 shows "Ports
  below 1024 need root or CAP_NET_BIND_SERVICE on Linux — try 8021" (a hint, not a block); TFTP disables login and TLS;
  risky combinations show the same warnings as the panel.

### Status bar

A right-side status item, hidden while nothing runs; otherwise "⇅ N servers" (singular for one) with a tooltip
listing each running server's name and first URL; clicking shows the panel.

### Palette and actions

Registered as actions and offered in the Commands scope:

- File Server: Show Panel; New Server…; Stop All Servers.
- Start ‹name› / Stop ‹name› for each saved definition.
- **Serve Current Folder over HTTP / FTP / SFTP / TFTP**: the focused pane's working directory when it is local
  (`terminal.observe`), else the home folder; creates a temporary read-only server with the protocol's default port
  (next free port above it if taken), `loopback` bind, anonymous for HTTP and a session password for FTP and SFTP;
  starts it and shows the panel with it selected. The user widens the bind in Edit… when they want LAN access.

### Quit

While at least one server runs and `confirm-quit` is on, File Server holds a `Windows.onQuitting` guard that asks
"N file servers are running and will stop." with Quit and Cancel. The guard is removed when the last server stops.

## Error messages

| Cause | Message |
|---|---|
| `BindException`, address in use | "Port 2121 is already in use on 0.0.0.0" |
| `BindException`/`SocketException`, permission denied | "Port 21 needs administrator rights on this system — use a port above 1024 (for example 2121)" |
| Interface missing | "Network interface en7 is not available" |
| Folder missing at start | "The folder ~/tftpboot does not exist" |
| TLS material unreadable | "The TLS certificate in … can't be read" with Regenerate |
| Vault credential missing | "Vault account no longer exists — edit the server" |
| Anything else | The engine's message; the stack trace goes to the plugin log |

## Testing

Headless; tests bind only to `127.0.0.1` on port 0 and never open a window.

- **`ShareRoot`**: `..` in every form (leading, trailing, repeated, mixed separators, percent-encoded after decoding by
  the engine), NUL and control characters, absolute paths; symlinks to an outside file, an outside directory, a chain,
  and an inside link (allowed); listing omits outward links; a new file under a symlinked parent; every write refused
  when read-only; root cannot be deleted or renamed; Windows name rules as pure string checks on every OS. Symlink
  tests skip when the OS refuses to create links.
- **`LoginThrottle`** with a fake clock: delays, lockout, reset on success.
- **`DefinitionStore`**: TOML round trip of every field; a malformed entry skipped and reported; atomic replacement.
- **`ServerDefinition`** validation per protocol.
- **`ServerRegistry`** with a fake engine and a fake `VaultApi`: every transition; `WAITING_FOR_VAULT → RUNNING` on
  unlock; port clash; deleted credential; stop all; temporary servers vanish on stop.
- **Engines, end to end** against a temporary share with real clients:
  - HTTP (JDK `HttpClient`): listing and escaping, redirect to trailing slash, Range 206/416, Basic auth and
    throttling, upload/PUT/DELETE when writable and 403 when read-only, traversal refused; HTTPS in FS-2.
  - FTP/FTPS (commons-net `FTPClient`/`FTPSClient`): passive listing and transfer, anonymous and user login, write
    permission, Require TLS refusing a plain login, traversal refused.
  - SFTP (MINA `SftpClient`): password and key logins, exec and shell channels refused, port forwarding refused, jail.
  - TFTP (commons-net `TFTPClient`): read, write when writable, access violation when read-only, `blksize`/`tsize`,
    retransmission over a socket wrapper that drops packets, busy refusal.
- **UI models** (headless): rows and warnings from states and definitions, editor validation and the Linux hint,
  status item text, Serve Current Folder's choice of folder and port.
- **SDK**: `network.listen` in `ALL`; `onQuitting` in the testkit fake and contract suite; the app cancels quit and
  restart when a guard refuses and proceeds when one throws.
- **GUI checks** (handed to the user per plan): start each protocol and connect from another device on the LAN; the
  firewall prompt on macOS and Windows; the quit confirmation; the Vault picker and a locked-Vault autostart; Serve
  Current Folder from a terminal.

## Delivery

One plan per runnable deliverable, each on its own branch in `.worktrees/`, written with `superpowers:writing-plans`
and executed with `superpowers:subagent-driven-development`:

1. **FS-1: foundation and HTTP.** SDK 0.11.0 (`network.listen`, `Windows.onQuitting`, app and testkit); plugin
   skeleton and build; `ShareRoot`; definitions and store; `LoginResolver`, `LoginThrottle`; `ServerRegistry` and
   `ActivityLog`; the HTTP engine without HTTPS; panel, editor, status item, palette including Serve Current Folder;
   quit guard. Usable end to end over HTTP.
2. **FS-2: FTP/FTPS and TLS.** `TlsMaterial`, the FTP engine, HTTPS for the HTTP engine, fingerprints in the panel.
3. **FS-3: SFTP.** Host key, the SFTP engine, authorized keys in the editor.
4. **FS-4: TFTP.** The TFTP engine.

Each plan updates `docs/STATUS.md`, `plugins/fileserver/README.md` (new in FS-1) and, in FS-1, the SDK and plugin
authoring docs for `network.listen` and `onQuitting`.
