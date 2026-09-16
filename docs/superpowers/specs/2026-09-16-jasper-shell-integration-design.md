# Jasper shell integration scripts — design

**Status:** Implemented on `claude/shell-integration` in `.worktrees/shell-integration`, commits `38e511b..this commit`.

**Development branch:** `claude/shell-integration` in `.worktrees/shell-integration`, from main `27999fe`.

**Builds on:** the Phase 1 terminal design (OSC 7 and OSC 133 rewriting in `ShellIntegrationFilter`), the [palette scopes design](2026-09-15-jasper-palette-scopes-design.md) (live capture of commands through OSC 133 marks) and the [snippets design](2026-09-15-jasper-snippets-design.md).

## Purpose

Jasper understands OSC 7 (working directory) and OSC 133 A/B/C/D (prompt and command marks), and several features depend on them: prompt jumping, the status bar's directory, live history capture with directory and exit status. Today they only work when the user has configured their shell to emit the marks. This design ships small integration scripts for zsh, bash and fish and loads them automatically, so a fresh install gets all of it with no dotfile edits. Later features (per-command decorations, finished-command notifications, a Directories scope) build on the same marks.

## Confirmed decisions

- **Automatic injection with an off switch.** Jasper launches the shell so its script loads after the user's own startup files. `terminal.shell_integration = "auto" | "manual" | "off"`, default `auto`. `manual` only exports the variables so the user can `source` the script; `off` exports nothing.
- **Marks plus the exact command line.** The scripts emit OSC 7 at each prompt, OSC 133 A/B/C/D with the exit status in D, and, just before C, the command line base64-encoded on Jasper's private OSC 1341 channel. Jasper prefers that text over reading the screen. No title updates.
- Rejected: passing the script body through an environment variable; manual-only.

## The scripts

Three files, bundled as resources and extracted to `<app dir>/shell-integration/`: `jasper.zsh`, `jasper.bash`, `jasper.fish`. Each is under about 80 lines, self-contained, and safe to `source` by hand. Every script:

- returns immediately unless the shell is interactive, `TERM_PROGRAM` is `Jasper`, and `JASPER_INTEGRATION_LOADED` is unset; then exports `JASPER_INTEGRATION_LOADED=1` so nested shells started by the user do not double-mark (and get no integration unless they source the script themselves);
- writes every sequence as `ESC ] … BEL` through one helper;
- emits **A** before the prompt and **B** after it by wrapping the prompt string once per prompt cycle (zsh `PROMPT` with `%{ %}`, bash `PS1` with `\[ \]`, fish by wrapping `fish_prompt`), checking for its own marker first so themes that rebuild the prompt (powerlevel10k, starship, oh-my-zsh) are re-wrapped and never double-wrapped;
- emits **OSC 7** `file://HOST/PATH` from the prompt hook only when the directory changed since the last prompt, with the path percent-encoded byte by byte except `A-Z a-z 0-9 / . _ ~ -`;
- emits the command line as `OSC 1341 ; jasper ; cmd ; <base64>` and then **C** from the pre-execution hook (zsh `preexec`, fish `fish_preexec`, bash `DEBUG` trap firing once per prompt cycle). The base64 comes from the `base64` executable with newlines stripped; when it is missing the `cmd` line is skipped and C still goes out;
- emits **D** with `$?` from the next prompt hook only when a command ran since the previous prompt, so an empty Enter yields A/B again without a D;
- changes nothing else: no aliases, no prompt text, no history options.

Per shell:

- **zsh** uses `add-zsh-hook` for `precmd` and `preexec`. The exit status is captured as the first statement of `precmd`. `preexec` receives the command line as its first argument.
- **bash** targets bash 3.2 (macOS `/bin/bash`) upward. `PROMPT_COMMAND` gets a status-capture function first and the prompt function last, honouring both the string form and the array form (bash 5.1+). The `DEBUG` trap chains any existing trap, ignores its own functions and `PROMPT_COMMAND`, and reads the command line from `history 1`; when the line was not added to history (`HISTCONTROL` with `ignorespace`) it falls back to `BASH_COMMAND`, detected by the history number not having advanced since the prompt.
- **fish** targets fish 3.x and 4.x. `fish_prompt` event handler emits D (using the status recorded by a `fish_postexec` handler), OSC 7 and A; `fish_preexec` emits `cmd` and C; the original `fish_prompt` function is copied and wrapped so B follows its output. fish 4 emits some marks itself; duplicates are harmless because Jasper deduplicates a repeated A on the same row and ignores a C without a pending B.

## Extraction

`ShellIntegrationScripts.install(Path dir)` runs once at startup on the configuration worker before the first window opens: it creates the directory and writes each bundled file only when the on-disk content differs, so upgrades propagate and unchanged files keep their timestamps. Failures are logged and leave integration off for that run. Layout:

```
shell-integration/
  jasper.zsh  jasper.bash  jasper.fish
  zsh/.zshenv  zsh/.zprofile  zsh/.zshrc  zsh/.zlogin
  bash/rc.bash
  fish/fish/vendor_conf.d/jasper.fish
```

## Injection at launch

`LaunchSettings.resolve` gains the integration directory and the mode. It always sets `TERM_PROGRAM=Jasper`. In `manual` and `auto` it exports `JASPER_SHELL_INTEGRATION=<dir>`. In `auto` it keys on the basename of the resolved program:

| Shell | Mechanism |
|---|---|
| `zsh` | `ZDOTDIR=<dir>/zsh`; the user's original `ZDOTDIR`, if any, travels as `JASPER_ORIGINAL_ZDOTDIR`. Each wrapper file restores the user's `ZDOTDIR`, sources the user's counterpart file (`.zshenv`, `.zprofile`, `.zshrc`, `.zlogin`), and sets `ZDOTDIR` back to the wrapper directory when more startup files follow (`.zshenv` always; `.zprofile` always; `.zshrc` only for login shells, where `.zlogin` follows). `.zshrc` sources `jasper.zsh` after the user's `.zshrc`. After startup `ZDOTDIR` is the user's value again, so child processes see nothing unusual. |
| `bash` | `--rcfile <dir>/bash/rc.bash` is inserted right after the program. Because bash ignores `--rcfile` for login shells, `-l` and `--login` are removed from the arguments and `JASPER_LOGIN_SHELL=1` is exported; `rc.bash` then reads `/etc/profile` and the first of `~/.bash_profile`, `~/.bash_login`, `~/.profile` the way a login shell would, otherwise `/etc/bashrc` (or `/etc/bash.bashrc`) and `~/.bashrc`, and finally `jasper.bash`. The rest of the user's arguments are preserved. |
| `fish` | `XDG_DATA_DIRS=<dir>/fish:<previous value, or /usr/local/share:/usr/share when unset>`; fish loads `fish/vendor_conf.d/jasper.fish` after its own configuration. The stub sources `$JASPER_SHELL_INTEGRATION/jasper.fish`. |

Any other program (`sh`, nushell, PowerShell, a custom binary) gets the exported variables only. Every injected value is visible in the child's environment.

## Jasper's receiving side

`TerminalSession.onCustomCommand` learns `cmd`: it base64-decodes the payload as UTF-8 and keeps it as the command text for the current cycle. At C, that text replaces the screen read; the screen read remains the fallback when no `cmd` arrived. At D, or at the next A, the listener fires as today. A malformed payload is ignored. `TerminalSession.shellIntegrationDetected()` becomes true at the first A mark; `TerminalPane` exposes it and the status bar shows a filled dot after the shell name when true and a hollow one otherwise, with the tooltip "Shell integration active" / "Shell integration not detected".

## Setting and documentation

```toml
[terminal]
shell_integration = "auto"   # "auto", "manual" or "off"; new panes only
```

The configuration guide gets a "Shell integration" section: what the scripts emit, the three mechanisms, the manual `source` line per shell, the bash login-shell note (`-l` is emulated by `rc.bash`, so `shopt -q login_shell` reports false), nested shells, `TERM_PROGRAM=Jasper`, and troubleshooting through the status-bar dot. The palette guide and STATUS drop their "Jasper ships no integration script" caveats. The generated template and root example gain the setting.

## Errors and edge cases

- A custom `ZDOTDIR` or `XDG_DATA_DIRS` is preserved and restored.
- A missing `base64` executable skips the `cmd` line only.
- Extraction failure disables injection for the session and logs once.
- A user script that already emits the marks: duplicates of A on the same row are deduplicated; a second C or D without a pending cycle is ignored.
- The scripts never run in non-interactive shells, so scripts, `ssh host command` and cron are unaffected.

## Testing

- Extraction: temp directory; every file written; unchanged content not rewritten (timestamp unchanged); changed content rewritten; failure logged.
- Script syntax: `zsh -n` and `bash -n` over the extracted files where the shells exist.
- `LaunchSettings`: each shell and mode; `-l` and `--login` handling for bash; custom `ZDOTDIR` and `XDG_DATA_DIRS` preserved; custom program untouched; `TERM_PROGRAM` set in every mode.
- Session: `cmd` payload decoded and preferred over the screen; malformed payload ignored; `shellIntegrationDetected()`.
- Real shells (macOS and Linux only; each skipped when its binary is absent): start `/bin/zsh` and `/bin/bash` through `TerminalSession.start` with a temporary `HOME` holding rc files, the extracted directory and the same environment `LaunchSettings` builds; feed commands and assert through the listener: A/B marks and prompt rows, OSC 7 with a percent-encoded directory containing a space, `commandExecuted` with the exact text, the exit status, a multi-line quoted command arriving without continuation prompts, an empty Enter producing no callback, the user's rc file having run first with its `PS1` content intact, a nested shell not double-marking, an existing bash `DEBUG` trap still running, and bash's `PROMPT_COMMAND` array form on bash 5.
- Status bar: dot state before and after the first A mark.

User-run afterwards: each shell on the desktop with the user's real dotfiles and prompt theme, and fish if installed.

## Out of scope

Titles, nushell and PowerShell scripts, per-command decorations, notifications, exporting snippets as aliases.
