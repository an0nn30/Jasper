# Jasper Remote — shell directory probe for SFTP follow

**Status:** Design approved in conversation 2026-09-24; written spec awaiting the user's review.
No implementation has started.

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
   bash host moves the sidebar within about half a second.
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

### 3.1 Tagging the shell

When the host's `followDirectory` setting is on, `ShellChannels` adds `LC_JASPER_PANE=<tag>`
to the shell channel's environment request, beside `TERM` and `COLORTERM`. The tag is 32
random lowercase hex characters, generated per shell channel and kept with the pane's
Remote association. `LC_*` is used because default sshd configurations on Debian, Ubuntu,
Fedora and macOS accept `LANG LC_*`. A server that refuses the variable still opens the shell;
the probe then falls back as described in §3.2.

### 3.2 The probe script

A fixed POSIX `sh` script is a plugin resource. Remote opens an exec channel on the pane's
existing connection with the command `sh -s -- <tag>` and writes the script to the channel's
standard input. So the user's login shell (bash, zsh, fish or other) parses only that short
command, and the only variable input is the tag. The tag is validated as `[0-9a-f]{32}` before
use, or is the literal `none` when the variable was not sent.

The script:

1. Walks up from its own process to the nearest ancestor whose command name is `sshd`,
   `sshd-session` or `dropbear`. OpenSSH serves every channel of a connection from one such
   process, so the pane's login shell is a direct child of it.
2. Among that ancestor's children, finds the one whose initial environment contains
   `LC_JASPER_PANE=<tag>`. If the tag is `none` or matches nothing, and exactly one child has
   a controlling terminal, it uses that child. Otherwise it prints nothing: it never guesses
   between shells.
3. Reads the controlling terminal's foreground process group (`tpgid`) of that shell and
   prints the current directory of the group leader. If that cannot be read (for example a
   root process after `sudo -s`), it prints the shell's own directory. If neither can be
   read, it prints nothing.
4. Prints the directory followed by a NUL byte and exits 0.

Platform commands:

| Step | Linux | macOS |
| --- | --- | --- |
| Parent / command | `/proc/<pid>/stat` (fields after the last `)`), `/proc/<pid>/comm` | `ps -o ppid= -o comm= -p <pid>` |
| Children | `/proc/*/stat` with the ancestor as parent | `pgrep -P <pid>` |
| Environment | `/proc/<pid>/environ` | `ps -E -o command= -p <pid>` |
| `tpgid` | `/proc/<pid>/stat` field 8 | `ps -o tpgid= -p <pid>` |
| Directory | `readlink /proc/<pid>/cwd` | `lsof -a -p <pid> -d cwd -Fn` |

Any other system prints nothing.

### 3.3 When Remote probes

A `DirectoryFollower` in the Remote plugin owns probing for panes:

- **Triggers:** an Enter (CR) byte written to the pane's input, 250 ms later (a further Enter
  restarts the delay); the pane gaining focus; the SFTP panel opening or being shown for the
  pane; follow being turned back on. `ShellChannels` wraps the connection's input stream to
  notice CR bytes; the bytes pass through unchanged.
- **Only when useful:** the SFTP panel is visible, it is following that pane, and the host's
  `followDirectory` is on. Otherwise triggers are ignored.
- **One at a time:** at most one probe per pane is in flight. A trigger during a probe marks
  one more probe to run after it finishes.
- **OSC 7 wins:** once a pane has sent an OSC 7 report, it is never probed again.
- **Failures:** a probe has a 2-second timeout. Empty output is not a failure: the sidebar
  stays where it is. After three consecutive failed probes (exec refused, timeout, non-zero
  exit) on a connection, Remote stops probing that connection and the sidebar shows "This
  host does not report the shell's folder". A new connection starts fresh.

A result is delivered on the UI thread through `SftpUi.directory(pane, path)`, the same entry
point OSC 7 reports use, and only when it differs from the last delivered path. Absolute paths
only; anything else is dropped.

### 3.4 Boundaries

- The probe result is an SFTP path hint inside the Remote plugin. It is never published as the
  pane's `remoteDirectory`, never reaches the app or SDK, never becomes a local `Path` and
  never selects a host: the pane's Remote association still decides the endpoint.
- The probe runs only on the pane's existing connection. It never opens or authenticates a
  connection.
- No SDK or app change is needed.

### 3.5 Per-host setting

`RemoteHost` gains `boolean followDirectory`, `true` by default. `HostFile` writes
`follow_directory = false` only when it is off, and reads a missing key as `true`, so existing
host files remain valid. The host editor gets a checkbox, "Track shell folder for SFTP follow",
under the connection fields. When it is off, no tag is sent and no probe runs. Changing it
affects shells opened afterwards.

## 4. Known limits

- Exec channels must be allowed; some locked-down servers forbid them.
- Systems without `/proc` or the macOS commands (BSDs, Windows OpenSSH) are not followed.
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
   visibility triggers, ignored triggers when not following or disabled, in-flight coalescing,
   OSC 7 suppression, the three-failure stop, and delivery only on change.
3. `ShellChannels`: the environment carries `LC_JASPER_PANE` only when the setting is on; the
   input wrapper passes bytes through unchanged and reports CR.
4. `HostFile` reads a missing key as on and round-trips `follow_directory = false`.
5. The script against the local Mac: run it with a tagged child process standing in for the
   login shell (the test supplies the ancestor process ID through a test-only variable), and
   check it prints the child's directory, falls back when untagged, and prints nothing when
   ambiguous. If the local sshd is reachable, the plan may add a loopback check.

User GUI checks: following on the Mac host and the Linux bash host; `bash` inside `zsh`;
`sudo -s`; the per-host setting off; and that the prompt, MOTD and history are untouched.
