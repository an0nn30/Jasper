# History scope ranking and freshness — design

**Status:** Designed. Development branch: `claude/history-scope-ranking`, from main `18fd513`.

**Builds on:** the [palette scopes design](2026-09-15-jasper-palette-scopes-design.md) (the History scope and `ShellHistoryIndex`) and the [shell integration design](2026-09-16-jasper-shell-integration-design.md) (OSC 133 live capture and script injection).

## Purpose

Three defects found in the shipped History scope, all confirmed against the user's own machine:

1. **A shell without timestamps can never rank.** `ShellHistorySnapshot.build` sorts on `timestamp` descending, and `timestamp` is `0` when unknown. bash writes no timestamps unless `HISTTIMEFORMAT` is set, so on the dev machine all 500 `.bash_history` entries sort below all 10,004 timestamped `.zsh_history` entries no matter how recently they ran. `0` is doing duty as both "unknown" and "older than everything".
2. **Nothing refreshes while you work.** The index refreshes at the first window and when the History scope is activated — that is all. There is no watcher and no timer, so a command run a second ago appears only after the palette is next opened.
3. **Live capture is inactive for a tmux user.** `LaunchSettings.inject` matches the basenames `zsh`, `bash` and `fish`; `tmux` falls to the default arm. Measured: a pane in a tmux server Jasper started *does* inherit `ZDOTDIR` and `JASPER_SHELL_INTEGRATION`, so the injection plumbing already reaches it — but tmux overwrites `TERM_PROGRAM` with `tmux`, and every script guards on `TERM_PROGRAM = Jasper`, so all three return early.

A fourth change is a preference, not a defect: trivial commands (`exit`, `clear`, `ll`) dominate the top of the list because they are genuinely the most recent. The user wants them ranked lower, behind a setting.

## Confirmed decisions

- **Rank, not timestamp.** Every entry gets a comparable rank. A known timestamp is used directly; an unknown one is inferred from its source file's modification time and the entry's distance from the end of that file. Rejected: ordering purely by file recency, which throws away the good zsh timestamps.
- **Watch the files.** The index polls its sources and refreshes when one changes, so a command appears without the palette being opened. Rejected: `WatchService`, which polls slowly on macOS — the same reason the config loader polls.
- **Give tmux a marker it cannot clobber.** Jasper exports `JASPER_TERMINAL=1` and the scripts accept either that or `TERM_PROGRAM=Jasper`. Rejected: rewriting tmux's `default-command`, which would fight the user's own tmux configuration.
- **De-ranking is configurable.** `history.deprioritize_trivial`, default `true`.

## Ranking

`ShellHistoryEntry` keeps `timestamp` as it is. `ShellHistorySnapshot.build` gains a rank per entry:

- **Known timestamp** (`timestamp > 0`): rank is the timestamp, in epoch seconds.
- **Unknown timestamp**: rank is `sourceModified - (entriesAfterThisOne + 1)`, where `sourceModified` is the source file's last-modified time in epoch seconds. The last entry in the file ranks one second before the file was written, the one before it two seconds before, and so on.

This places an untimestamped entry near when its file was actually written rather than at the epoch, so a bash command run minutes ago outranks a zsh command from last week, and a bash file untouched for seven weeks sinks below this week's zsh — both correct.

The inference needs the source's modification time, which `ShellHistoryIndex` already reads, so `build` takes `Collection<Source>` carrying `(entries, modified)` rather than a bare `Collection<List<ShellHistoryEntry>>`. Live entries keep their real timestamps and their existing tie-break above file entries.

Ties keep the current `sequence` tie-break, so behaviour for two entries at the same rank is unchanged.

## Freshness

`ShellHistoryIndex` gains a poll: a single `javax.swing.Timer` on the EDT, 1 second, started when the first listener subscribes and stopped when the last unsubscribes, calling the existing `refresh()`. `refresh()` already coalesces (one running, at most one queued) and `readSource` already returns immediately when size and mtime are unchanged, so a quiet second costs one `stat` per source.

The timer runs only while something is listening, so a window with no palette open pays nothing. `close()` stops it.

This makes a command appear within about a second for any shell that writes its history file promptly — the dev machine's zsh has `share_history`, which writes immediately. Live capture through OSC 133 remains the instant path where integration is active.

## tmux

`LaunchSettings.resolve` always exports `JASPER_TERMINAL=1` alongside `TERM_PROGRAM=Jasper`. It is scrubbed from the inherited environment with the other `JASPER_*` markers, so a nested Jasper re-establishes its own.

Each script's guard becomes: interactive, **and** (`TERM_PROGRAM` is `Jasper` **or** `JASPER_TERMINAL` is `1`), and `JASPER_INTEGRATION_LOADED` unset.

`inject` gains a `tmux` arm. tmux runs the user's `$SHELL` in each pane and passes its own environment down, so the arm applies the existing per-shell mechanism to the basename of `$SHELL` rather than to `tmux` itself:

- `$SHELL` is `zsh` → set `ZDOTDIR`, exactly as the `zsh` arm does. Verified to reach the pane.
- `$SHELL` is `fish` → set `XDG_DATA_DIRS`, as the `fish` arm does. Same mechanism, same propagation.
- `$SHELL` is `bash` → **nothing**. bash has no environment variable for an rc file, and `--rcfile` cannot be passed to a shell tmux starts without taking over `default-command`, which is the user's setting. Documented as a manual `source` line.

The command line is never rewritten for tmux; only environment variables are set. A user attaching to a tmux server that was already running gets nothing, because that server's environment predates Jasper — documented.

## Trivial de-ranking

```toml
[history]
deprioritize_trivial = true   # live; new setting
```

When true, an entry whose command is one of a built-in set — `exit`, `clear`, `ll`, `ls`, `cd`, `pwd`, plus `cd` with a single argument — sorts after every non-trivial entry, keeping its relative order within the trivial group. It is a partition, not a score adjustment, so the rule is predictable: trivial commands are still present, still searchable, just never above real work.

The set is deliberately built in and short. A user-supplied list is a larger surface (validation, matching semantics) than the problem warrants; if it is wanted later, the setting can grow from a boolean to a list without changing the ranking code.

`ShellHistoryScope` reads the flag from the config snapshot it already receives and passes it to `build`.

## Errors and edge cases

- A source whose modification time cannot be read keeps rank `0` for its untimestamped entries, i.e. today's behaviour, rather than failing the scan.
- A file with more entries than seconds since the epoch cannot occur in practice; the inferred rank is clamped at `0` so it can never go negative and sort above nothing.
- Entries merged across shells keep the newest rank, as they do now.
- A search query matching only trivial commands still returns them; de-ranking never removes a row.
- The poll timer must not run in tests that do not want it; it starts on first subscription, so a test that never subscribes never polls.

## Testing

- Ranking: a bash entry from a file modified now outranks a zsh entry from last week; a bash entry from a seven-week-old file sinks below this week's zsh; entries within one untimestamped file keep file order; a known timestamp is used unchanged; live entries still win ties.
- Freshness: appending to a history file publishes a new snapshot without the palette being activated; the timer starts on first subscribe and stops on last unsubscribe; a quiet tick performs no read.
- tmux: `inject` with `tmux` and `SHELL=/bin/zsh` sets `ZDOTDIR`; with `SHELL=/usr/local/bin/fish` sets `XDG_DATA_DIRS`; with `SHELL=/bin/bash` sets neither and leaves the command untouched; `JASPER_TERMINAL=1` is exported in every mode and scrubbed from the inherited environment.
- Scripts: each of zsh, bash and fish loads when `JASPER_TERMINAL=1` and `TERM_PROGRAM=tmux`, and still loads on `TERM_PROGRAM=Jasper` alone; neither loads when both are absent.
- De-ranking: on, a trivial command sorts below a non-trivial one even when more recent; off, strict recency returns; the setting parses, validates and applies live.

User-run afterwards: the History palette on the real desktop inside tmux, confirming a just-run command appears within about a second and that bash entries interleave sensibly.

## Out of scope

Rewriting tmux's `default-command`; bash integration inside tmux; a user-configurable trivial list; ranking by frequency; any change to how history files are parsed.
