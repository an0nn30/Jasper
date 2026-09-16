# Jasper snippets — design

**Status:** Implemented on `claude/snippets` in `.worktrees/snippets`, commits `d80d1b0..bb06700` plus the Task 6 documentation commit that records this status. Fresh `./gradlew check --rerun-tasks`: 767 tests, 766 passed, one existing font skip, zero failures/errors. No GUI, merge or push.

**Development branch:** `claude/snippets` in `.worktrees/snippets`, from main `b9efd41`.

**Builds on:** the [palette scopes design](2026-09-15-jasper-palette-scopes-design.md), which stays binding. This document adds a third scope, grows the verb contract from two verbs to three, and adds two in-card steps.

## Purpose

Users keep commands they run often. Shell aliases are the traditional answer, but Jasper does not own the shell: it ships no integration script, cannot know which rc file a shell sources, and zsh, bash, fish, nushell and PowerShell each spell aliases differently. What Jasper can own is a **snippet**: a named command saved in Jasper's own directory, searchable from the palette, and pasted or run into whatever shell is focused. Snippets may contain placeholders that are filled in at paste time, and any command in the History scope can be saved as one.

## Confirmed decisions

- **File-first, minimal.** Snippets live in `snippets.toml` beside `config.toml`. Creating one from History asks only for a name; every other edit happens in the OS editor, the same way Settings opens `config.toml`. No in-app editor dialog.
- **Placeholders in this version.** `{{name}}` tokens are filled in a step inside the card before pasting; values are remembered per placeholder for the session.
- **A third verb slot on Shift+Enter.** The scope contract allows up to three verbs on fixed keys: Enter, Cmd/Ctrl+Enter, Shift+Enter. History's third verb is "Save as snippet…"; Snippets' third verb is "Edit file".
- **Shortcut:** `snippets_palette`, Cmd+J on macOS and Ctrl+Shift+J elsewhere, reachable also through `>snip` and the chip.
- Approach A: a third `PaletteScope` over its own `SnippetStore`. Snippets inside `config.toml` (the app would append to the user's settings file) and snippets registered as commands (no placeholders, no run verb, user content in the Commands list) were rejected.

## File format

`snippets.toml` lives in the application directory resolved through `AppDirs`, next to `command-history.toml`, independent of `--config`. It is never read or written by the configuration loader.

```toml
# Jasper snippets. Edit freely; Jasper only ever appends new [[snippet]] tables.

[[snippet]]
name = "Rebase onto main"
command = "git fetch origin && git rebase origin/{{branch}}"
keywords = ["git", "rebase"]
```

- `name`: required, nonblank, at most 128 characters, unique ignoring case and surrounding whitespace.
- `command`: required, nonblank, may be multi-line (TOML multi-line strings are fine), at most 16 KiB.
- `keywords`: optional array of nonblank strings.
- Placeholders are `{{identifier}}` with `identifier` matching `[A-Za-z_][A-Za-z0-9_]*`. `\{{` is a literal `{{`. Anything else between braces is literal text.
- File order is the order the empty query shows.

Parsing uses TomlJ like the configuration. An entry missing a required field, with an invalid name, or duplicating an earlier name is skipped with one warning naming the table index. A file that does not parse leaves the last good snapshot in memory, logs one warning per process, and shows "Snippets file has errors" in the scope's empty state so the problem is not silent. A missing file is an empty snapshot. The file is created on the first save with the comment header above.

## Store and reload

`SnippetStore` is application-wide, owned by `JasperApplication` like the history index: one serial daemon worker reads and writes, immutable snapshots publish on the EDT, listeners fire on change, `snapshot()` / `onChanged()` / `reload()` are EDT-only, `close()` does not wait.

It reads after the first window opens, on Reload Config (the store is invoked from the same place the configuration reload runs), and when the Snippets scope is activated and the file's size or modification time changed. Reads are coalesced like history refreshes.

Writes happen only for "Save as snippet". `append(name, command)` runs on the worker: it reads the current file bytes, validates the name against the current snapshot (duplicate names are refused before any write), appends exactly one `[[snippet]]` table to the end (preceded by a blank line, with the command as a TOML basic or multi-line string as needed), writes through a sibling temporary file with atomic replacement where supported, then re-reads and publishes. The store never rewrites existing bytes, so hand edits and comments survive. The result of an append is delivered on the EDT as success with the new snippet or failure with a message.

## The scope

`SnippetsScope`, id `jasper.snippets`, label "Snippets", icon Tabler `bookmark`, aliases `snip`, `snippets`, placeholder "Search snippets, or > to switch scope", monospace rows off (names are prose; the detail line shows the command in the muted face).

Rows: title is the name; detail is the command on one line with newlines shown as ↵; tag is the placeholder count when nonzero ("1 field", "2 fields"); token is the snippet. Search: every query word must match; tiers use the command-search rules over the name and keywords first (exact, prefix, word prefix, substring, keyword), then the command text as a plain substring; ties keep file order. The empty query lists file order under "Snippets". At most `palette.max_results` rows.

Verbs: `paste` ("Paste"), `paste_run` ("Paste and run"), `edit` ("Edit file"). Paste and Paste-and-run go through the fill-in step when the snippet has placeholders, then through the target exactly as History does. Edit file creates `snippets.toml` if missing and opens it with the same OS-editor mechanism Settings uses for `config.toml`. `available` is false when the target is not live (for the two paste verbs) or the row's snippet is no longer in the snapshot.

## Steps inside the card

A **step** replaces the result list with a small form while the input row, chip and footer stay. The controller owns the step state; the card paints it. Two steps exist:

- **Fill-in step.** Opened when a snippet with placeholders is chosen with Paste or Paste-and-run. One labelled text field per distinct placeholder in order of first appearance, the first focused, each row 40px, the snippet name as the section label. Fields are prefilled with the value last used for that placeholder name in this process. Tab and Shift+Tab move between fields and wrap. Enter substitutes every occurrence and runs the verb that was chosen. Escape returns to the list with the query intact. A snippet without placeholders skips the step.
- **Name step.** Opened by Shift+Enter on a History row. One "Name" field, prefilled with the command's first word plus its first argument (for example `git rebase`), the command shown as the section label. Enter appends through the store; on success the palette dismisses, then reopens in Snippets with the new snippet selected. A duplicate name shows "A snippet named … exists" under the field and keeps focus. A write failure shows the failure message the same way. Escape returns to the History list.

While a step is open: Up, Down and Cmd/Ctrl+1–5 do nothing and are consumed; Tab moves fields instead of committing the picker; Escape leaves the step; typing goes to the focused field; the scope shortcuts still switch scopes, which abandons the step; outside clicks and origin loss dismiss as usual. The card's preferred height is the input row plus the section label plus one row per field plus the footer.

The name-step "Save as snippet…" verb and the fill-in step both need the scope to call back into the controller. `PaletteContext` gains nothing; instead `PaletteScope.execute` may return a `PaletteStep` (a record of title, ordered field names with prefills, and a completion callback that receives the values and returns an optional error message) and the controller shows it. Scopes without steps return nothing, as today.

## Three verbs and keys

`PaletteScope.verbs()` may hold one to three verbs. Fixed keys: Enter runs verb 1, Cmd/Ctrl+Enter verb 2, Shift+Enter verb 3. `PaletteKeyRouter` adds Shift+Enter with the Shift modifier alone, so Cmd+Shift+Enter (Zoom Pane's default) is untouched. The footer lists every verb the scope has ("⏎ Paste  ⌘⏎ Paste and run  ⇧⏎ Save as snippet…"; the Ctrl/Shift+Enter spelling elsewhere) and stays hidden with one verb. Numbered shortcuts and mouse clicks keep running verb 1.

Scope verbs after this change:

| Scope | Enter | Cmd/Ctrl+Enter | Shift+Enter |
|---|---|---|---|
| Commands | Run | | |
| History | Paste | Paste and run | Save as snippet… |
| Snippets | Paste | Paste and run | Edit file |

`snippets_palette` joins `ActionId` ("Snippets", Cmd+J on macOS, Ctrl+Shift+J elsewhere, no `alt+` rewrite), the View menu after Search Shell History, the keybinding template, the root example and the configuration guide with the same collision note as `history_palette`. The Snippets scope is always registered; an empty file is an empty scope, so there is no enable flag.

## Errors and edge cases

- Unreadable or malformed file: last good snapshot stays, one log warning per process, the empty state reads "Snippets file has errors"; Reload Config retries.
- Save failure: the name step stays open with the message; nothing is lost.
- A placeholder that appears more than once is asked once and substituted everywhere.
- Paste and paste-and-run send exactly what History sends: through `TerminalView.paste`, then a raw carriage return for run.
- A snippet removed from the file while its row is selected fails `available` and the list refreshes instead of pasting.
- Saving never touches the Commands recents.

## Testing

- Parser: valid entries, missing or blank name or command, invalid name characters and length, duplicate names, keywords, escaped `\{{`, multi-line commands, placeholder extraction order, malformed file keeps the previous snapshot.
- Store: read on activation only when the file changed, append writes exactly one table and preserves existing bytes and comments, creates the file with the header, atomic replacement, duplicate refusal without a write, failure reporting, worker/EDT threading with the inline-executor pattern from the history tests.
- Scope: ranking over names, keywords and command text, the cap, placeholder-count tags, verbs, `available` after removal, Edit file creating the file and invoking the editor callback.
- Fill-in step: field order, Tab and Shift+Tab wrap, remembered values, Escape back to the list, paste versus paste-and-run bytes through a fake target, skipping the step without placeholders, key swallowing in step mode.
- Name step: prefill, duplicate refusal, file contents after saving, dismissal and reopening in Snippets with the new row selected, failure message.
- Keys: Shift+Enter routes verb 3, Cmd+Shift+Enter still zooms, Cmd+J and Ctrl+Shift+J open Snippets, switching in place, same-shortcut dismissal.
- Config and docs: `snippets_palette` in the catalog, template and example; guide sections.
- Renders: Snippets list, fill-in step and name step added to the matrix in dark and light.

User-run afterwards: Cmd+J on the desktop and editing `snippets.toml` in the real OS editor.

## Out of scope

Exporting snippets as shell aliases, an in-app editor dialog, placeholder validation or choice lists, per-snippet shells or working directories, sharing snippets between machines.
