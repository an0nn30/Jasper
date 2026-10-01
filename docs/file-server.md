# File Server: share a folder with other computers

File Server is bundled with Jasper. It shares a folder on this computer with other programs and computers, the way
MobaXterm's Servers window does. This version serves **HTTP**; FTP/FTPS, SFTP and TFTP follow.

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
- **Unprivileged ports.** HTTP starts at 8080. On Linux, ports below 1024 need root or `CAP_NET_BIND_SERVICE`; the
  editor suggests one 8000 higher.
- **Jailed.** Clients cannot leave the folder, through `..` or through a link that points outside it.
- **Warnings.** The panel and the editor warn about plain HTTP with a password, a writable server anyone can reach,
  and a writable server open to other computers.

## Logins

*Anonymous* needs no login. *Session password* makes a new password each time the server starts (Copy Password
copies it; the user name is yours). *Vault account* uses an account you keep in the Credential Vault; choose it with
**Choose…**. The Vault may ask to unlock and to allow File Server. After a wrong password, an immediate retry from the
same address is refused with a short wait (HTTP status 429 and a `Retry-After` header), the wait growing to half a
minute; after ten wrong passwords the address is refused for five minutes.

HTTP logins use Basic auth, which sends the password unencrypted; HTTPS arrives with FTP/FTPS in the next version.

## Serve Current Folder

**Serve Current Folder over HTTP** shares the folder your shell is in (or your home folder), read-only and on this
computer only, without saving it. It appears under *Temporary* and disappears when stopped; **Save as a Server…**
keeps it.

## Quitting

Servers run inside Jasper and stop when it quits. While any runs, quitting or restarting asks first; turn that off
with *Ask before quitting while servers run* in File Server's settings.

## Files

Saved servers live in `plugins/dev.jasper.fileserver/data/servers.toml` under Jasper's home; the file never holds a
password. Settings live in `plugins/dev.jasper.fileserver/dev.jasper.fileserver.toml`.

## For other programs

Any browser can download from the listing; on a writable server the listing also uploads and deletes. From a
terminal: `curl -O http://HOST:8080/file` downloads, `curl -T file http://HOST:8080/folder/file` uploads, and
`curl -X DELETE http://HOST:8080/folder/file` deletes. An upload is written to a hidden `.jasper-upload-*.part` file
in the same folder and replaces the target only once it has fully arrived, so an interrupted upload leaves the
original alone. The server only answers requests addressed to an IP address,
`localhost` or this computer's name, and refuses forms posted from other web sites.
