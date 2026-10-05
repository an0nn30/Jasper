# File Server: share a folder with other computers

File Server is bundled with Jasper. It shares a folder on this computer with other programs and computers, the way
MobaXterm's Servers window does. This version serves **HTTP, HTTPS, FTP, FTPS and SFTP**; TFTP follows.

## The File Servers panel

Choose **Toggle File Servers** in the command palette (or click the server icon on the rail) to show the panel. Each
row is one server: a dot for its state, its name, protocol and port, and ▶ or ■ at the right to start or stop it
(double-click or Enter does the same). Below the list, the selected server shows its folder, who may log in, every
address the server answers on (each with **Copy**), warnings, and its activity: logins, failed logins, downloads
and uploads with their sizes, and errors. The activity stays in memory; **Clear** empties it.

Press **+** (or **New File Server…**) to add a server. Right-click a row for Start/Stop, Edit…, Duplicate, Delete…,
Reveal Folder, Copy Address and Copy Password. The status bar shows "⇅ 2 servers" while servers run; click it to toggle
the panel.

## Safe by default

- **Read-only.** Turn on *Allow changes (upload, rename, delete)* to let clients write.
- **This computer only.** *Reachable from* chooses *All networks* or one network interface; the panel then lists
  the addresses others can use. macOS and Windows ask once whether Jasper may accept connections; on macOS, Jasper
  also needs Privacy & Security › Local Network.
- **Unprivileged ports.** HTTP starts at 8080, FTP at 2121 and SFTP at 2222. On Linux, ports below 1024 need root or `CAP_NET_BIND_SERVICE`; the
  editor suggests one 8000 higher.
- **Jailed.** Clients cannot leave the folder, through `..` or through a link that points outside it.
- **Warnings.** The panel and the editor warn about plain HTTP or plain FTP with a password ("Plain FTP sends the
  password unencrypted"), a writable server anyone can reach, and a writable server open to other computers. With
  *Use TLS* but not *Require TLS*, a client may still log in without TLS; turn on *Require TLS* to refuse that.

## Logins

*Anonymous* needs no login. *Session password* makes a new password each time the server starts (Copy Password
copies it; the user name is yours). *Vault account* uses an account you keep in the Credential Vault; choose it with
**Choose…**. The Vault may ask to unlock and to allow File Server. After a wrong password, an immediate retry from the
same address is refused with a short wait (HTTP answers 429 with a `Retry-After` header, FTP answers 530), the wait
growing to half a minute; after ten wrong passwords the address is refused for five minutes.

HTTP logins use Basic auth, which sends the password unencrypted; turn on **Use TLS** to serve HTTPS instead.

## Serve Current Folder

**Serve Current Folder over HTTP** shares the folder your shell is in (or your home folder), read-only and on this
computer only, without saving it. It appears under *Temporary* and disappears when stopped; **Save as a Server…**
keeps it.

**Serve Current Folder over FTP** does the same with a temporary read-only FTP server on this computer only. It uses a
session password; copy it with **Copy Password** in the server's menu.

**Serve Current Folder over SFTP** does the same with a temporary read-only SFTP server on this computer only, with
a session password.

## FTP and FTPS

An FTP server shares the folder with any FTP client (FileZilla, Cyberduck, `ftp`, `curl`). New FTP servers use a Vault
account when the Credential Vault is installed and a session password otherwise; Anonymous is allowed too.

- **Passive ports.** Data connections use the server's own range, or the File Server setting "Default FTP passive
  port range" (50000–50100) when the range is empty. Open those ports in your firewall as well as the server's port.
- **Active mode** works, but only back to the client's own address.
- **Use TLS** turns on explicit FTPS: clients connect to the same port and send `AUTH TLS` (FileZilla calls this
  "Require explicit FTP over TLS"). Implicit FTPS (port 990) is not offered. **Require TLS** refuses a login before
  `AUTH TLS` and any transfer or listing without `PROT P`.
- Without TLS the password crosses the network in plain text; the panel and the editor warn about it.
- An upload replaces the file only once it has arrived completely; a failed or interrupted transfer leaves the old
  file. Resuming an upload part-way through is refused; `APPE` appends to the end of a file. An interrupted `APPE`
  keeps the bytes already appended, because appends happen in place.
- An upload is committed after the server sends its success reply. If saving then fails (for example the disk is
  full), the client has already been told it worked, and the failure appears in the server's activity log.
- A client that tries `anonymous` when the server has a login is disconnected and must reconnect.
- `..` stops at the shared folder: `cd ..` at the top stays at `/`.

## HTTPS and the certificate

**Use TLS** on an HTTP server serves HTTPS. HTTPS and FTPS share one self-signed certificate, which File Server
creates the first time a TLS server starts and keeps in `data/tls/` (owner-only). Browsers warn about a self-signed
certificate; compare the fingerprint before you accept it. The server card shows the certificate's SHA-256
fingerprint. **Show Fingerprints…** in a server's menu shows it too, and offers **Regenerate Certificate…**. A new
certificate makes clients that trusted the old one warn again; running servers keep the old one until they restart.

## SFTP

An SFTP server shares the folder with any SFTP client (`sftp`, `scp` from OpenSSH 9 on, FileZilla, Cyberduck,
WinSCP). It serves file transfer only: no shell, no commands and no port forwarding.

- **Logins.** A Vault account or a session password, and/or **authorized keys**: OpenSSH public key lines added in
  the editor with Add from File… (for example `~/.ssh/id_ed25519.pub`) or Paste. A server with a password login
  accepts keys only for that user name. **Keys only (no password)** accepts any user name with an authorized key.
  Key lines with options (`command=`, `from=`, `restrict`) are refused. Wrong passwords are throttled as for FTP; an
  offered key that isn't authorized doesn't count.
- **Host key.** File Server creates an Ed25519 host key the first time an SFTP server starts and keeps it in
  `data/hostkey` (owner-only). Clients ask you to trust it on first connection; compare the fingerprint with the
  server card's "SSH host key SHA256:…" or Show Fingerprints…. If the key file becomes unreadable, a new key replaces
  it and clients warn about the change.
- **The shared folder is a jail.** `..` stops at the top; links that lead outside are invisible and cannot be
  followed, and links can't be created or read. A link inside the shared folder that leads outside is refused
  ("permission denied"), however deep the path behind it. Server-side copy, checksum and disk-space requests are not
  supported.
- **Uploads** replace a file only when the client closes it normally; a dropped connection leaves the old file.
  Resuming, appending and writing part of a file happen in place.
- On a writable server, a client may change a file's times and permissions. SFTP clients can't set owners or groups.

## Quitting

Servers run inside Jasper and stop when it quits. While any runs, quitting or restarting asks first; turn that off
with *Ask before quitting while servers run* in File Server's settings. On a Mac where Jasper stays resident, Cmd+Q
only closes the windows and keeps Jasper in the background: servers keep running and nothing is asked until you choose
**Quit Jasper Completely** in the menu bar item (or log out).

## Files

Saved servers live in `plugins/dev.jasper.fileserver/data/servers.toml` under Jasper's home; the file never holds a
password. Settings (`confirm_quit`, `activity_entries`, `passive_ports`) live in
`plugins/dev.jasper.fileserver/dev.jasper.fileserver.toml`. The certificate lives in
`plugins/dev.jasper.fileserver/data/tls/server.p12`, with its generated password in `data/tls/password`. The SFTP host key lives in
`plugins/dev.jasper.fileserver/data/hostkey`.

## For other programs

Any browser can download from the listing; on a writable server the listing also uploads and deletes. From a
terminal: `curl -O http://HOST:8080/file` downloads, `curl -T file http://HOST:8080/folder/file` uploads, and
`curl -X DELETE http://HOST:8080/folder/file` deletes. An upload is written to a hidden `.jasper-upload-*.part` file
in the same folder and replaces the target only once it has fully arrived, so an interrupted upload leaves the
original alone. The server only answers requests addressed to an IP address,
`localhost` or this computer's name, and refuses forms posted from other web sites.
