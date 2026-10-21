# Shell History and Snippets

Shell History (`dev.jasper.history`) and Snippets (`dev.jasper.snippets`) are bundled [plugins](plugins.md#bundled-plugins), both on
by default. Shell History searches your shells' history from the [command palette](command-palette.md) and pastes or
runs a command. Snippets keeps named commands, with placeholders you fill in, in a file you edit, and pastes or runs
them from the palette. Shell History saves a command as a snippet through Snippets' API. Either can be turned off in
Settings › Plugins; the change applies at the next launch. Shell History needs Snippets on: with Snippets off, Shell
History fails to start, and Settings › Plugins marks it Failed.

| Command | Id | macOS | Linux and Windows | What it does |
|---|---|---|---|---|
| Search Shell History | `dev.jasper.history.open` | Cmd+R | Ctrl+Shift+R | Opens the palette on the History chip |
| Snippets… | `dev.jasper.snippets.open` | Cmd+J | Ctrl+Shift+J | Opens the palette on the Snippets chip |
| Edit Snippets File | `dev.jasper.snippets.edit` | none | none | Opens `snippets.toml` in your [default editor](editor.md#default-editor), creating it first when it is missing |

The commands are in the palette's Commands scope and in Settings › Keymap under each plugin's name; neither plugin adds
menu items. While the palette is open, an opener's shortcut switches to its chip
([scope chips](command-palette.md#scope-chips)). A shortcut you set, or one Jasper's built-in actions use, wins over
these defaults; [changing shortcuts](keyboard-shortcuts.md#changing-shortcuts) explains how to set one.

## Shell History

### Where commands come from

History merges your shells' history files with the commands Jasper sees finish.

**History files.** Jasper reads each of these files that exists on this computer, and never writes to them; an SSH
host's or WSL distribution's history, and Cygwin Bash's (its file is in your Cygwin home), reaches History only through
live capture:

| Shell | Tag | File | What Jasper reads |
|---|---|---|---|
| zsh | `zsh` | `$HISTFILE` when Jasper's own environment sets it, else `~/.zsh_history` | Plain lines and extended-history lines (`: <time>:<duration>;<command>`), which carry the time; a line ending in `\` continues on the next |
| bash | `bash` | `~/.bash_history` | One command per line; a `#<seconds>` line, which bash writes when `HISTTIMEFORMAT` is set, gives the next command its time |
| fish | `fish` | `~/.local/share/fish/fish_history` | `- cmd:` entries and their `when:` time |
| nushell | `nu` | `$XDG_CONFIG_HOME/nushell/history.txt` (`~/.config/nushell/history.txt` when it is unset), and on macOS also `~/Library/Application Support/nushell/history.txt` | One command per line; nushell's SQLite history is not read |
| PowerShell | `pwsh` | Windows: `%APPDATA%\Microsoft\Windows\PowerShell\PSReadLine\ConsoleHost_history.txt`; macOS and Linux: `~/.local/share/powershell/PSReadLine/ConsoleHost_history.txt` | One command per line; a line ending in a backtick continues on the next |

A command reaches History from its file once the shell has written it there. bash and zsh write their files when the
shell exits unless set to write sooner (bash `PROMPT_COMMAND="history -a"`, zsh `INC_APPEND_HISTORY` or
`SHARE_HISTORY`); live capture does not wait for that.

**Live capture.** Every command a pane's shell reports as finished through
[shell integration](terminal.md#shell-integration) is added at once, from any window. Jasper's scripts report commands
for zsh, bash and fish; for PowerShell, add the profile code in
[Terminal panes](terminal.md#wsl-powershell-and-other-shells). A captured command keeps the local folder it ran in, when
the shell reported one. It is tagged with the pane's shell name: a Windows shell's profile name (such as `PowerShell`
or `Cygwin Bash`), a WSL distribution's name, otherwise the shell program's file name (such as `zsh`), or `shell` when
there is none; for a
session a plugin provides, such as an SSH pane, it is that session's title. The tag can therefore differ from the
history file's: a command run in a PowerShell pane and read from PowerShell's history file shows `PowerShell/pwsh`. In
bash, a command that `HISTCONTROL=ignorespace` or `ignoreboth` keeps out of history is not reported. zsh and fish report
every command, including one that starts with a space and that `HIST_IGNORE_SPACE` (zsh) or fish keeps out of its
history file; History holds it until Jasper quits. Live captures are kept in memory only: after a restart, History holds
what the files hold.

**The merged list.**

- One row per distinct command text. Its place is its most recent run, and it carries the tags of every shell that ran
  it.
- Newest first. An entry without a time (bash without `HISTTIMEFORMAT`, plain zsh lines, nushell and PowerShell) counts
  as written just before its file was last modified, one second apart in file order, so a bash command from a minute
  ago sits above a zsh command from last week. A live capture wins a tie.
- Blank entries are skipped.

Jasper reads the files when Shell History starts, when the palette opens on or switches to History or All, and once a
second while it shows either, so a command written to a file appears without reopening the palette. After the first
read it reads only what a file gained; a file that was rewritten (bash overwriting its file, zsh trimming it) is read
again in full. A file Jasper cannot read is skipped and noted once in the [log](diagnostics.md#logs).

| Limit | Value |
|---|---|
| Read from each file | The last 16 MiB |
| Entries kept from each file | The newest 50,000 |
| Live captures kept | The newest 50,000, until Jasper quits |
| Commands in the merged list | The newest 50,000 |
| Length of an entry in a file | 16,384 UTF-16 code units; longer entries are skipped |

### Searching history

Open History with **Search Shell History** (Cmd+R, Ctrl+Shift+R) or by typing `history >` in the palette; the card's
field then reads "Search shell history". History is also searched in All once you type, under its own heading. Its
rows never appear among the empty query's Recently used, Most used or Suggested rows, and running one is not recorded
as a palette use, since shell commands can hold anything.

A row shows the command on one line (in a monospaced font in the card), with ↵ where it had line breaks. Its detail is
the folder a live capture ran in. Its tag names the shells that ran it, joined by `/`, and appears only when more than
one shell contributes to the list. The list holds at most 200 rows.

| macOS | Linux and Windows | Verb | What it does |
|---|---|---|---|
| Enter | Enter | Paste | Pastes the command into the pane without running it |
| Cmd+Enter | Ctrl+Enter | Paste and run | Pastes the command, then sends Return |
| Shift+Enter | Shift+Enter | Save as snippet… | Saves the command in Snippets ([below](#saving-a-command-as-a-snippet)); offered only while Snippets runs |

The pane is the one that was focused when the card opened, or, in the Enterprise look's
[Run window](command-palette.md#the-run-window), the one that is current when you run the row. The command is pasted
the way your own paste does it, bracketed when the program asked for that. Paste and Paste and run do nothing when
there is no terminal pane, for example when the palette opened over an Editor tab, or when the pane has closed. Save as
snippet… needs no pane.

### Ranking

**Empty query.** The list, headed **Most recent**, is newest first. With **Rank trivial commands last** on, trivial
commands follow all the others, each group keeping its order. A command is trivial when it has at most two words and
its first word, ignoring case, is in **Trivial commands**: with the defaults `ls -la` and `cd src` are trivial, and
`cd src && make` and `clearcache` are not.

**Typed query.** Case is ignored, and every word of the query must appear in the command. Matching commands come in
three groups:

1. commands that start with the whole query;
2. commands in which every query word starts a word, at the start or after a character that is not a letter or digit;
3. the other matches.

Within a group, commands captured live in the folder the pane is in now come first, then newer before older.

### Settings

The settings are on **Settings › Tools › Shell History**, section **Ranking**, and in
`plugins/dev.jasper.history/dev.jasper.history.toml` in [Jasper home](getting-started.md#jasper-home). Apply on the
page writes the file. Jasper reads the file live, and a change applies from the next empty History query.

| Key | Default | Setting | Meaning |
|---|---|---|---|
| `trivial_commands` | `["exit", "clear", "ls", "ll", "la", "cd", "pwd", "c", "q", "logout"]` | Trivial commands | Single words compared, ignoring case, with a command's first word. Setting the key replaces the whole list; `[]` means no trivial commands. An entry that is empty or holds a space makes the value invalid, and the default list is used. |
| `deprioritize_trivial` | `true` | Rank trivial commands last | `false` lists an empty query by recency alone |

Jasper creates the file, with both keys commented out, when it is missing. Problems in it are reported like those of
any [plugin settings file](plugins.md#plugin-settings).

## Snippets

### snippets.toml

Snippets live in `plugins/dev.jasper.snippets/data/snippets.toml` in [Jasper home](getting-started.md#jasper-home).
You edit the file; **Edit Snippets File** and Edit file (Shift+Enter in the Snippets list) open it; Edit Snippets File
creates it when it is missing. Jasper writes to it only to create it and to append a snippet you save from History, and keeps everything
already there, comments included. A file Jasper creates starts with the comment
`# Jasper snippets. Edit freely; Jasper only ever appends new [[snippet]] tables.`

The file holds one `[[snippet]]` table per snippet:

```toml
# Jasper snippets. Edit freely; Jasper only ever appends new [[snippet]] tables.

[[snippet]]
name = "Rebase onto a branch"
command = "git fetch origin && git rebase origin/{{branch}}"
keywords = ["git", "rebase"]

[[snippet]]
name = "Pod logs"
command = "kubectl logs -n {{namespace}} {{pod}} --tail=200 -f"
keywords = ["k8s", "kubernetes"]

[[snippet]]
name = "Tag a release"
command = '''
git tag -a v{{version}} -m "Release {{version}}"
git push origin v{{version}}'''
```

| Key | Type | Required | Rules |
|---|---|---|---|
| `name` | string | yes | The row's title. Surrounding spaces are trimmed; 1 to 128 characters, no control characters; unique, ignoring case |
| `command` | string | yes | Not blank; at most 16,384 UTF-16 code units; may span several lines (a `"""` or `'''` string); may hold [placeholders](#placeholders) |
| `keywords` | array of strings | no | Extra words for search; none may be blank |

A line break in a multi-line string reaches the command as `\n` on every platform. The empty query lists snippets in
file order, under the heading **Snippets**.

Jasper reads the file at start, on **Reload Config** and whenever it reads `config.toml` again, and, when the file's
size or modification time changed, each time the palette opens on or switches to Snippets or All. Snippets has no
settings and no Settings page.

### Placeholders

`{{name}}` in a command is a placeholder: double braces around a letter or `_` followed by letters, digits or `_`
(`[A-Za-z_][A-Za-z0-9_]*`), with no spaces.

Insert and Paste and run on a snippet with placeholders show a [step](command-palette.md#steps) headed with the
snippet's name: one field per distinct placeholder, labelled with its name, in the order the placeholders first
appear. Each field is prefilled with the value last entered for that placeholder name, in any snippet, since Jasper
started. The first field takes the keyboard with its text selected, and Tab and Shift+Tab move between fields,
wrapping. Enter replaces every occurrence of each placeholder with its field (an empty field leaves nothing) and
pastes, sending Return too for Paste and run; Escape returns to the list with the query kept. A snippet without
placeholders pastes at once.

To keep `{{` as text, write `\{{`: `\{{user}}` pastes as `{{user}}` and opens no field. In a double-quoted TOML string
the backslash itself is written twice (`"echo \\{{user}}"`); in a single-quoted string it is not
(`'echo \{{user}}'`).

### Using snippets

Open Snippets with **Snippets…** (Cmd+J, Ctrl+Shift+J) or by typing `snippets >` in the palette; the card's field
then reads "Search snippets". Snippets is also searched in All, and snippets you run appear among the empty query's
Recently used and Most used rows.

A row shows the snippet's name, its command on one line as the detail (↵ where it has line breaks) and, when it has
placeholders, the tag "1 field" or "*n* fields". The list holds at most 200 rows.

| macOS | Linux and Windows | Verb | What it does |
|---|---|---|---|
| Enter | Enter | Insert | Pastes the command, after its placeholders are filled, without running it |
| Cmd+Enter | Ctrl+Enter | Paste and run | Pastes the command, after its placeholders are filled, then sends Return |
| Shift+Enter | Shift+Enter | Edit file | Opens `snippets.toml` in your [default editor](editor.md#default-editor) |

Insert and Paste and run use the pane and the paste described for [History](#searching-history), and do nothing when
there is no open terminal pane or the snippet is no longer in the file. Edit file needs no pane.

A search ignores case, and every word of the query must be found in the snippet's name, keywords or command. Snippets
rank in this order, ties keeping file order:

1. the name equals the query;
2. the name starts with the query;
3. every query word starts a word of the name;
4. every query word is in the name;
5. every query word is in the name or the keywords;
6. the other matches, where some word is found only in the command.

### Saving a command as a snippet

On a History row, **Save as snippet…** (Shift+Enter) shows a step headed "Save as snippet: *command*" with one field,
**Name**, prefilled with the first two words of the command's first line (`git rebase` for
`git rebase -i origin/main`), at most 128 characters. Enter appends a `[[snippet]]` table with that name and the
command, written as one-line TOML strings with `\n` for line breaks and no keywords, creating the file when it is
missing. The palette then reopens on Snippets with the name as the query and the new snippet selected. Escape returns
to the History list.

A refusal keeps the step open with the message under the field:

| Message | Cause |
|---|---|
| A snippet named *name* exists | The file has a snippet of that name, ignoring case |
| snippets.toml has errors; fix it and Reload Config before saving | The file [has errors](#problems-with-snippetstoml) |
| Could not save snippet: Snippet names are 1–128 printable characters | The name is empty, too long or holds a control character |
| Could not save snippet: *reason* | The command breaks a [rule](#snippetstoml), or the file could not be written |

### Problems with snippets.toml

| Problem | What happens |
|---|---|
| The file is missing | No snippets. **Edit Snippets File** and Save as snippet… create it. |
| It is not valid TOML, is larger than 1 MiB or is not valid UTF-8 | The file has errors. The snippets last read stay in use, and the list starts with a dimmed row, "Snippets file has errors", whose detail reads "Fix *path* and Reload Config"; only Edit file runs on it. Save as snippet… is refused. Once the file is fixed, Reload Config, or opening the palette on Snippets or All, reads it again. |
| An entry breaks a rule or repeats an earlier name | That entry is skipped and the others load. The [log](diagnostics.md#logs) notes `snippets.toml: snippet <n>: <reason>`, counting entries from 1. |
| `snippet` is not an array of tables | No snippets; the log notes it. |
| **Edit Snippets File** cannot create the file | A notification, "Could not open the snippets file", reads "*path*: *reason*". |

Snippets' notifications are in its own group, **Snippets** (`dev.jasper.snippets`), which shows cards
([groups](notifications.md#groups)).

## For plugin authors

Snippets exports `dev.jasper.snippets.api`, which Shell History uses for Save as snippet…;
[Plugin APIs](plugin-apis.md#snippets-api) describes it.
