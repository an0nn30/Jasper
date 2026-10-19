# Project Index

Project Index finds the files of your projects from the command palette's **Files** scope and opens them in the
[Editor](editor.md). It is a bundled plugin (`dev.jasper.indexer`), on by default; [Plugins](plugins.md#project-index)
lists its capabilities. When a terminal enters a folder that looks like a project, it offers to index it; you can also
index any folder by hand. With [Remote](remote.md), projects on Remote's saved hosts are offered and indexed too.
Opening files needs the Editor plugin, and remote projects need Remote; without them the rest still works.

## Commands

| Command | ID | macOS | Linux and Windows | Where |
|---|---|---|---|---|
| Go to File… | `dev.jasper.indexer.goto` | Cmd+P | Ctrl+Alt+Shift+P | palette; opens the palette on the Files chip, or switches to it from another chip; with Files already showing it closes the card, while the Run window takes the keyboard instead |
| Index Folder… | `dev.jasper.indexer.index-folder` | none | none | File menu, palette |
| Reindex Current Project | `dev.jasper.indexer.reindex` | none | none | palette |
| Remove Current Project from Index… | `dev.jasper.indexer.remove` | none | none | palette |
| Cancel Indexing | `dev.jasper.indexer.cancel` | none | none | palette, the status bar's indexing item; available while indexing you asked for runs |

Change or assign shortcuts in [Settings › Keymap](keyboard-shortcuts.md#settings--keymap).

## Indexing a project

When a terminal's shell reports its folder and that folder is inside a project, Project Index posts a card in the pane's
window:

> **Index project?**
>
> app · ~/src/app
>
> Git repository
>
> **Yes** · **No** · **Ignore this path**

The first line names the project's folder and where it is (your home folder written `~`), the second what kind of
project it looks like; a project on a host adds "on *host*".

- **Yes** remembers the project and indexes it at once.
- **No** stops asking about that project until Jasper restarts. Closing the card counts as No.
- **Ignore this path** stops asking for good: the project's folder is added to **Ignored paths** and the settings file
  is saved. If that fails, "Could not ignore *name*" says why.

No card is offered while **Offer to index projects** is off, for a folder inside an ignored path, for a folder inside a
project you declined or whose card is still open, or for a folder inside an indexed project, which is refreshed instead
([Keeping indexes fresh](#keeping-indexes-fresh)).

The shell must report its folder with OSC 7, as Jasper's [shell integration](terminal.md#shell-integration) does for
zsh, bash and fish.

### Project markers

A folder is a project when it holds a marker. Jasper looks at the reported folder and the folders above it, nearest
first: the nearest folder with a strong marker wins; otherwise the nearest folder with any marker does. Looking up from
a folder inside your home folder stops below it, so your home folder and the folders above it never become the project;
the file-system root never does.

| Marker | Kind shown |
|---|---|
| `.git` (strong) | Git repository |
| `.hg` (strong) | Mercurial repository |
| `.svn` (strong) | Subversion working copy |
| `*.sln` (strong) | Visual Studio solution |
| `Cargo.toml` | Cargo project |
| `go.mod` | Go module |
| `pyproject.toml`, `setup.py`, `requirements.txt`, `Pipfile` | Python project |
| `package.json` | Node.js project |
| `deno.json` | Deno project |
| `pom.xml` | Maven project |
| `build.gradle`, `build.gradle.kts`, `settings.gradle`, `settings.gradle.kts` | Gradle project |
| `CMakeLists.txt` | CMake project |
| `Makefile` | Make project |
| `meson.build` | Meson project |
| `composer.json` | PHP project |
| `Gemfile` | Ruby project |
| `mix.exs` | Elixir project |
| `pubspec.yaml` | Dart project |
| `Package.swift` | Swift package |
| `*.csproj` | .NET project |
| `*.xcodeproj` | Xcode project |
| `flake.nix` | Nix flake |

**Project markers** (`markers`) replaces this list. An entry is a file or folder name, or a pattern in which `*` matches
any text; a pattern with `*` matches case-sensitively, while a plain name matches as the file system does (on macOS and Windows, `makefile` counts as `Makefile`). A leading `!` makes an entry strong, as in `"!.git"`. A marker that is not in the
table shows as "Contains *name*". Within one folder the markers are tried in the order of the list.

### Indexing a folder by hand

**File › Index Folder…**, or **Add Folder…** in Settings, opens the [file chooser](workspace.md#the-file-chooser),
titled **Index Folder**. The folder you choose needs no marker; it is remembered as a project named after it and indexed
at once. Choosing a folder already indexed reindexes it. With Remote installed, the chooser offers Remote's saved hosts
too.

## Projects on remote hosts

With the Remote plugin, SSH panes on Remote's saved hosts are offered projects too, but only when the shell on the host
reports its folder with OSC 7 and the host is connected. Jasper does not load its shell integration on remote hosts, and
Remote passes only `TERM` and `COLORTERM` to the host's shell. To use Jasper's script there, copy `jasper.zsh`,
`jasper.bash` or `jasper.fish` from the `shell-integration` folder in [Jasper home](getting-started.md#jasper-home) to
the host and source it from the shell's startup file with `JASPER_TERMINAL=1` set, since the scripts do nothing without
it (or `TERM_PROGRAM=Jasper`); or have your own prompt send OSC 7. Remote's own folder following, the read-only check
after Enter that its SFTP browser uses ([Following the terminal](remote.md#following-the-terminal)), does not count. The
same reports refresh a project when a pane enters it again; refreshing after a command finishes also needs the command
marks (OSC 133), which Jasper's script sends as well. Without either, **Files** still puts the project you are in first,
and **Reindex Current Project** still works, because both use the folder Remote follows.

When a pane reports a folder, Jasper looks for markers on the host with one command and offers the project with its host
name. Your home folder on the host and the folders above it are never projects. Indexing uses Git on the host when the
project is a work tree, then `find`, then an SFTP walk, and gives up after two minutes. Opening a file opens it from the
host in the Editor ([Editing remote files](remote.md#editing-remote-files)); **Shift+Enter** opens a new SSH tab on the host
in the file's folder.

Remote projects refresh only while their host is connected; otherwise their saved index is still searched and shows as
Stale in Settings. Indexing you ask for may connect and ask you to sign in; background refreshes never do. Ignoring a
remote folder adds `<host id>:<path>` to **Ignored paths**, where the host id is the host's `id` in Remote's
[`hosts.toml`](remote.md#hoststoml).

## Finding files

Press **Cmd+P** (**Ctrl+Alt+Shift+P** on Linux and Windows), or set the palette's **Files** chip (`files >`); the field
reads "Search files". Files is also searched from All, and its rows can appear among the palette's recently used rows
([Searching](command-palette.md#searching)). Files from every indexed project are searched, except projects whose folder
no longer exists.

Type part of a name, its initials (`fsc` finds `FilesScope.java`), or folders and a name separated by `/` (`ui/Files`).
Every word you type must match. Files are ranked by how well their weakest word matches (the exact name, the start of
the name, the initials, part of the name, then part of the path), then the project you are in first, then shorter paths,
then alphabetically. Matching ignores case. A search that runs longer than 50 ms shows what it found so far, the project
you are in being searched first.

The project you are in is the one holding the folder of the pane the palette was opened for, else of the active pane.
With an empty query, Files lists **Recently opened**: the files of that project you opened from Files since Jasper
started, newest first, at most 20.

Each row shows the file's name, its path in the project and the project's name as a tag ("*project* · *host*" for a
remote project).

| macOS | Linux and Windows | Verb |
|---|---|---|
| Enter | Enter | **Open** the file in the Editor |
| Cmd+Enter | Ctrl+Enter | **Copy path**: the absolute path, or `host:/path` for a remote file |
| Shift+Enter | Shift+Enter | **Open terminal here**: a new terminal tab in the file's folder, an SSH tab for a remote file |

A row whose file cannot be opened is dimmed, and its path is preceded by the reason: "Install the Editor plugin to open
files", "Install the Remote plugin to open this file" or "Host no longer saved". **Copy path** still works on it. A
remote project whose host is not connected shows "Connect to open"; **Open** then connects.

Opening a local file that no longer exists refreshes its project; the Editor reports the missing file.

## What gets indexed

In a Git work tree, Project Index lists the files Git tracks plus the untracked files it does not ignore, with
`git ls-files --cached --others --exclude-standard`. Git runs with `core.fsmonitor=false`, so a repository's
configuration cannot start a program, and without taking Git's index lock. When Git is missing, fails or runs longer
than 60 seconds, or the work tree's top folder is your home folder, the folder is walked instead: every regular file,
without following links.

Either way, folders named in **Excluded folders** are skipped, and so are folders Git lists, such as nested
repositories, submodules and links to folders. A project stops at **Files indexed per project** (200,000 by default) and
shows as Truncated; a notice, "*name* has more than *n* files", says so once per project until Jasper restarts.

## Keeping indexes fresh

Indexes are saved and survive restarts: after a restart the saved indexes are searched without indexing again. A project
is refreshed in the background when a terminal enters it again, and 3 seconds after the last command finished in it, at
most once every **Minutes between background refreshes** (5 by default). A background refresh that succeeds posts no
"Indexed" notification.

**Reindex Current Project** refreshes the project of the focused pane at once; outside an indexed project it says "No
indexed project here". **Reindex** in Settings does the same for the selected project. Indexing you ask for (Yes, Index
Folder…, Reindex, Retry) shows "Indexing *name*…" in the status bar with **Cancel Indexing**, and ends with "Indexed
*name*" (*n* files). **Cancel Indexing** stops it and keeps the previous index.

Projects are indexed one at a time per computer: this computer is one queue, and each saved host another. Asking for a
project that is being indexed queues one more run after it.

**Remove Current Project from Index…** asks "Remove *name* from Project Index? Its saved index is deleted." (**Cancel**
/ **Remove**), then forgets the focused pane's project and deletes its saved index, as **Remove** in Settings does.

## Notifications

| Group | Id | Default | Notifications |
|---|---|---|---|
| Project indexing | `dev.jasper.indexer.prompts` | sticky card | "Index project?", "Could not ignore *name*" |
| Index results | `dev.jasper.indexer.results` | history only | "Indexed *name*", after indexing you asked for |
| Project Index | `dev.jasper.indexer` | card | "*name* has more than *n* files"; "Couldn't index *name*" with the reason and **Retry**; "Couldn't index *name*" with "The folder no longer exists: *folder*", after indexing you asked for; "No indexed project here" |

Change how a group appears in [Notifications › Groups](notifications.md#groups).

## Settings

**Settings › Tools › Project Index** has three sections.

**Projects** lists the indexed projects with **Name**, **Location** ("*host*: *folder*" for a remote project),
**State**, **Last indexed** and **Files**, and the buttons **Add Folder…**, **Reindex** and **Remove** (the last two for
the selected project). With no projects it shows "No Projects" and **Add Folder…**. These buttons act at once; Remove
asks first.

| State | Means |
|---|---|
| Fresh | the saved index is from the last indexing |
| Stale | the last indexing failed and the previous index stays, or the project's host is not connected |
| Truncated | the project has more files than **Files indexed per project** |
| Missing | the project's folder no longer exists; its files are not searched |
| Needs reindex | there is no saved index |
| Indexing… | being indexed now |
| Unavailable | the project's host is no longer saved in Remote, or Remote is not installed |

**Detection** and **Indexing** hold the settings below, which take effect when you press Apply. They live in
`plugins/dev.jasper.indexer/dev.jasper.indexer.toml` under Jasper home (see
[Plugin settings](plugins.md#plugin-settings)), and each applies from the next card or indexing job.

| Key | Setting | Section | Default | Values |
|---|---|---|---|---|
| `prompt` | Offer to index projects | Detection | `true` | `true` or `false` |
| `markers` | Project markers | Detection | the [markers above](#project-markers) | names or `*` patterns; a leading `!` marks a strong one |
| `ignored` | Ignored paths | Detection | `[]` | absolute local folders, or `<host id>:<path>` for a remote one; each covers the folder and everything inside it |
| `excludes` | Excluded folders | Indexing | `.git`, `.hg`, `.svn`, `node_modules`, `target`, `build`, `dist`, `out`, `.gradle`, `.idea`, `.vscode`, `.venv`, `venv`, `__pycache__`, `.mypy_cache`, `.pytest_cache`, `.tox`, `.next`, `.nuxt`, `vendor`, `Pods`, `DerivedData`, `bin`, `obj` | folder names, without `/` or `\` |
| `max_files` | Files indexed per project | Indexing | `200000` | 1000 to 1000000 |
| `refresh_minutes` | Minutes between background refreshes | Indexing | `5` | 1 to 1440 |

## Files

Under Jasper home, in `plugins/dev.jasper.indexer/`:

| File | Holds |
|---|---|
| `dev.jasper.indexer.toml` | the [settings](#settings) |
| `data/projects.toml` | the indexed projects: a `[[project]]` table each, with `key` (`local:<folder>` or `remote:<host id>:<folder>`), `name` and `manual` (indexed by hand) |
| `data/projects.toml.bad` | a project list that could not be read, set aside before the next save |
| `data/indexes/*.idx` | each project's saved index, one compressed file per project |

Jasper rewrites `projects.toml` itself; change the project list in Settings. A saved index that is damaged or belongs to
another project is deleted.
