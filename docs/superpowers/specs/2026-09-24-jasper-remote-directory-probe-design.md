# Jasper Remote — shell directory probe for SFTP follow

**Status:** Approved and implemented on `codex/remote-sftp`; GUI acceptance is the user's.

**Base:** `codex/remote-sftp` at `ffc34fb1`.

**Authority:** Amends §2.2 of the [SFTP phase 1 design](2026-09-23-jasper-remote-sftp-design.md).
Everything else in that design stands.

## 1. Problem and intended outcome

**Follow terminal folder** only follows OSC 7 reports. Stock bash and zsh send none over SSH:
macOS sends OSC 7 only when `TERM_PROGRAM` is `Apple_Terminal`, and Linux distributions only
from `vte.sh` under a VTE terminal. So the sidebar never moves on a normal Mac or Linux host.
On 2026-09-24 the user confirmed the app side works by printing an OSC 7 report by hand, which
made the sidebar jump.

The outcome: on ordinary hosts the SFTP sidebar follows `cd` in a Remote pane without any
setup on the remote, and **nothing ever appears in, or is typed into, the user's shell**. The
user has seen type-in injection echo the command or corrupt the prompt, and rejected it, as
well as a bootstrap that changes how the login shell starts.

Success criteria:

1. With default settings, `cd` in a Remote pane on the user's Mac host and on a stock Linux
   bash host moves the sidebar within about half a second, also with several panes open to the
   same host. Windows hosts are not followed and behave exactly as today.
2. Nothing is echoed, added to history, or changed in shell startup, MOTD or "Last login".
3. A per-host setting turns the probe off completely.
4. A shell that sends OSC 7 keeps working exactly as today.

## 2. Amendment to SFTP design §2.2

Replace "No commands are injected to obtain a directory." with:

> Nothing is ever written to the user's shell. When the host allows it, Remote may read the
> pane's directory with a fixed read-only probe on a separate exec channel (see the
> [directory probe amendment](2026-09-24-jasper-remote-directory-probe-design.md)). The result
> is a path hint with the same limits as a reported `remoteDirectory`.

## 3. Design

### 3.1 A dedicated connection per followed pane

The probe must know which shell belongs to the pane. Remote normally shares one connection
per host between panes, and on macOS the only per-process marker a client could set (an
environment variable) cannot be read from outside the process: `ps -E` no longer shows another
process's environment, even the user's own (verified 2026-09-24). So a followed pane gets its
own SSH connection, and the pane's shell is then the only process with a controlling terminal
under that connection's `sshd`.

- A pane's shell uses a dedicated connection when the host's `followDirectory` setting is on
  and the host's cached OS (`HostInfoCache`) is not `Windows`. Otherwise it shares as today.
- `Connections` gains a dedicated lease: a session registered under its own key, never reused
  by another caller, and evicted as soon as its last reference is released (no linger). Jump
  hops in its route are still shared.
- SFTP browsing, transfers and host inspection of other callers keep using shared leases, so
  following adds one login per pane. Agent and Vault-key logins are silent; a Vault credential
  that asks for approval asks once per connection.

### 3.2 The probe script

A fixed POSIX `sh` script is a plugin resource. Remote opens an exec channel on the pane's
dedicated connection with the command `sh -s` and writes the script to the channel's standard
input. The user's login shell parses only that command; the script takes no input.

The script:

1. Walks up from its own process to the nearest ancestor whose command name is `sshd`,
   `sshd-session` or `dropbear`. OpenSSH serves every channel of a connection from one such
   process, so the pane's login shell is a direct child of it.
2. Takes that ancestor's only child with a controlling terminal. The probe's own processes
   and an SFTP subsystem have none. If there is no such child, or more than one, it prints
   nothing: it never guesses between shells.
3. Reads the terminal's foreground process group (`tpgid`) and prints the current directory of
   the group leader, so a nested `bash` or a program started in another folder is followed. If
   that cannot be read (for example a root process after `sudo -s`), it prints the shell's own
   directory. If neither can be read, it prints nothing.
4. Prints the directory followed by a NUL byte and exits 0. On a system other than Linux or
   macOS (by `uname -s`) it prints nothing and exits 3.

Platform commands:

| Step | Linux | macOS |
| --- | --- | --- |
| Parent / command | `/proc/<pid>/stat` (fields after the last `)`), `/proc/<pid>/comm` | `ps -o ppid= -p <pid>`, `ps -o comm= -p <pid>` |
| Children | `/proc/<pid>/task/<pid>/children`, else scan `/proc/*/stat` | `pgrep -P <pid>` |
| Controlling terminal | `/proc/<pid>/stat` field 7 is not 0 | `ps -o tty= -p <pid>` is not `??` |
| `tpgid` | `/proc/<pid>/stat` field 8 | `ps -o tpgid= -p <pid>` |
| Directory | `readlink /proc/<pid>/cwd` | `lsof -a -p <pid> -d cwd -Fn` |

The macOS column was checked on the user's Mac on 2026-09-24, including a directory whose
name contains spaces and a foreground process group in another directory.

### 3.3 When Remote probes

A `DirectoryFollower` in the Remote plugin owns probing for panes:

- **Triggers:** an Enter (CR) byte written to the pane's input, 250 ms later (a further Enter
  restarts the delay); the pane gaining focus; the SFTP panel opening or being shown for the
  pane; follow being turned back on. `ShellChannels` wraps the connection's input stream to
  notice CR bytes; the bytes pass through unchanged.
- **Only when useful:** the pane has a dedicated connection, the SFTP panel is visible and
  following that pane, and the OS reported for the connection is not `Windows`. Otherwise
  triggers are ignored. A pane is only tracked after its connection's host inspection
  completes; `Windows` stops tracking it and is cached, so its next panes share connections.
- **One at a time:** at most one probe per pane is in flight. A trigger during a probe marks
  one more probe to run after it finishes.
- **OSC 7 wins:** once a pane has sent an OSC 7 report, it is never probed again.
- **Failures:** a probe has a 2-second timeout. Empty output is not a failure: the sidebar
  stays where it is. Exit status 3 (unsupported system) stops probing the pane at once. After
  three consecutive failed probes (exec refused, timeout, other non-zero exit), Remote stops
  probing the pane. In both cases the sidebar shows "This host does not report the shell's
  folder" while it follows that pane. A new pane starts fresh.

A result is delivered on the UI thread through `SftpUi.directory(pane, path)`, the same entry
point OSC 7 reports use, and only when it differs from the last delivered path. Absolute paths
only; anything else is dropped.

### 3.4 Boundaries

- The probe result is an SFTP path hint inside the Remote plugin. It is never published as the
  pane's `remoteDirectory`, never reaches the app or SDK, never becomes a local `Path` and
  never selects a host: the pane's Remote association still decides the endpoint.
- The probe runs only on the pane's own dedicated connection. It never opens or authenticates
  a connection.
- No SDK or app change is needed.

### 3.5 Per-host setting

`RemoteHost` gains `boolean followDirectory`, `true` by default. `HostFile` writes
`follow_directory = false` only when it is off, and reads a missing key as `true`, so existing
host files remain valid. The host editor gets a checkbox, "Track shell folder for SFTP follow",
under the connection fields. When it is off, panes share connections and no probe runs.
Changing it affects shells opened afterwards.

## 4. Known limits

- Exec channels must be allowed; some locked-down servers forbid them.
- Only Linux and macOS hosts are followed. Windows hosts are skipped by design; other systems
  (BSDs) report unsupported. The first connection to a Windows host before its OS is cached is
  dedicated, which is harmless.
- Each followed pane costs one SSH connection and login.
- Inside tmux or screen, the foreground process is the client, so the sidebar shows the
  multiplexer's starting directory.
- A root shell from `sudo -s` falls back to the login shell's directory.
- A directory change without Enter (for example an fzf `Alt-C` widget) is picked up on the next
  Enter or focus change.

## 5. Testing

Automated (headless):

1. Output parsing: a NUL-terminated absolute path, empty output, garbage, non-absolute paths,
   and paths containing spaces and newlines.
2. `DirectoryFollower` with a fake prober and a manual clock: Enter debounce, focus and
   visibility triggers, ignored triggers when not wanted, in-flight coalescing, OSC 7
   suppression, the unsupported stop, the three-failure stop, and delivery only on change.
3. `Connections`: a dedicated shell does not reuse or get reused by a shared lease, a second
   dedicated shell to the same host opens a second connection, and a dedicated session closes
   with its shell. The probe runs `sh -s` on the pane's own session and maps exit statuses.
4. `ShellChannels`: the input wrapper passes bytes through unchanged and reports CR.
5. `HostFile` reads a missing key as on and round-trips `follow_directory = false`.
6. The script against real local processes (Linux and macOS, skipped elsewhere): `script(1)`
   gives a child a controlling terminal, and a test-only `JASPER_PROBE_ANCESTOR` variable names
   the ancestor instead of `sshd`. The test waits until the child has its terminal, then checks
   the child's directory (with spaces), a foreground group in another directory, and nothing
   for two terminal children.

User GUI checks: following on the Mac host and the Linux bash host with two panes to each;
`bash` inside `zsh`; `sudo -s`; a Windows host still works without following; the per-host
setting off; and that the prompt, MOTD and history are untouched.
