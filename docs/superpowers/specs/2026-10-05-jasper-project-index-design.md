# Project Index plugin

Status: design approved in chat by the user on 2026-10-05; written specification awaiting user review. No plan yet.
A new bundled plugin, **Project Index** (`plugins/indexer`, `dev.jasper.indexer`), notices when a terminal enters a
project, offers to index it, and serves the indexed files in a new **Files** palette scope that opens them in the
Editor. Remote projects work through a new exported Remote API (`dev.jasper.remote.api`). Built on the SDK's terminal
events, notifications, palette, settings and menus, the Editor API (`dev.jasper.editor.api`) and Remote
(`plugins/remote`), all on local `main` at 18de50d1. No SDK change.

## Outcome

When a terminal pane's shell reports a working directory inside a project (a folder holding `.git`, `Cargo.toml`, a
solution file, `pyproject.toml` and similar configurable markers), Jasper posts a card:

> **Index project?**
> moray · ~/projects/moray — Git repository
> **Yes** · **No** · **Ignore this path**

Yes indexes the project's files. From then on the command palette's **Files** tab (also searched by All) finds any file
in any indexed project by name, initials or path, ranking the current project first; Enter opens the file in the
Editor. The same works in SSH panes: the project is detected, listed and opened on the remote host through Remote.
Folders that are not projects can be indexed by hand from **File › Index Folder…** or the plugin's Settings page.

## Decisions made with the user

- **Palette placement:** the indexer contributes its own **Files** scope. Palette scopes belong to one plugin, so rows
  cannot be added to the Editor's Recent files tab; an Editor-owned scope fed through an Editor API, or merging into
  Recent files, were rejected.
- **Remote:** Remote exports a small API (`dev.jasper.remote.api`, service `RemoteProjects`) that detects, lists and
  opens remote project files over Remote's existing connections. Rejected: making SDK file sources consumable by
  plugins and walking trees through `FileBrowser.list` (one SFTP round trip per folder; an SDK and Editor API change),
  and having the indexer run its own commands over a raw exec channel (SSH knowledge split across two plugins).
- **Freshness:** indexes persist across restarts and refresh in the background when a pane enters the project again,
  after a command finishes in it, and on demand. No file watchers (macOS `WatchService` polls slowly; History rejected
  it for the same reason).
- **Search set:** every indexed project, with files of the current project ranked first.
- **Listing:** Git-aware. Inside a Git work tree, `git ls-files`; otherwise, or when Git fails or is missing, a walk
  that skips a configurable exclude list. Same strategy locally and remotely. Rejected: our own `.gitignore` parser,
  and an exclude-list-only walk.
- **No** suppresses the prompt for that root until Jasper restarts; **Ignore this path** persists.
- Per-project cap of **200,000** files.
- **Shift+Enter** opens a terminal tab in the file's folder (local and remote); the plugin needs `terminal.open`.

Out of scope: file content search (grep), symbol indexing, live file watching, projects on hosts that are not Remote
saved hosts, merging with Recent files, and any SDK change.

## 1. Components

### Plugin manifest

```toml
id = "dev.jasper.indexer"
name = "Project Index"
version = "0.1.0"
entry = "dev.jasper.indexer.IndexerPlugin"
capabilities = ["terminal.observe", "terminal.open", "palette.contribute"]
requires = [{ id = "dev.jasper.editor", version = ">=0.7", optional = true },
            { id = "dev.jasper.remote", version = ">=0.12", optional = true }]
```

The `sdk` range matches the other bundled plugins at the time of the plan. The indexer compiles against the SDK,
`dev.jasper.editor.api` and `dev.jasper.remote.api` only; `verifyPluginArchitecture` enforces this. Without the Editor
the Files scope still lists files, but Open is unavailable and rows say so. Without Remote, SSH panes are never offered
and saved remote projects are listed as unavailable.

### Units in the indexer

| Unit | Responsibility |
|---|---|
| `detect.ProjectDetector` | From a folder, walks up to the project root. Pure logic over a `MarkerProbe` (which of these names exist in these folders), so local and remote share it. |
| `detect.Markers` | Parses the configured markers into strong and weak sets, with `*` globs (for `*.sln`). |
| `watch.CwdWatcher` | Turns `CWD_CHANGED` and `COMMAND_FINISHED` into prompts and refreshes; owns the session "No" set, open-card dedupe, throttle and debounce. |
| `index.ProjectKey` | Identity of a project: `local:<absolute path>` or `remote:<host id>:<absolute path>`. |
| `index.ProjectIndex` | One immutable snapshot: key, display name, root, file list, strategy, indexed-at time, truncated flag. |
| `index.IndexStore` | Loads and saves `data/projects.json` and `data/indexes/*`; atomic writes. |
| `index.Indexer` | Schedules jobs: one serial worker per host (local is one host), one job per project with at most one queued behind it, cancellation, status-bar progress. |
| `index.LocalLister` | Git-aware local listing with the walk fallback, excludes and the cap. |
| `remote.RemoteBridge` | Adapter over `RemoteProjects`, absent when Remote is not installed. |
| `search.FileMatcher` | Ranks paths against a query. |
| `ui.FilesScope` | The **Files** palette scope. |
| `ui.PromptCards` | Builds and posts the "Index project?" card and result/failure cards. |
| `ui.IndexerSettingsPanel` | The plugin's Settings page. |
| `IndexerPlugin` | Wires everything; registers the menu item and palette commands. |

### Remote API (Remote 0.12.0)

Remote's manifest gains `exports = ["dev.jasper.remote.api"]`; it publishes one service with `Services.publish`, the
same way the Vault and Editor publish theirs. The package holds the interface and its value records only.

```java
package dev.jasper.remote.api;

/** Published by Remote. UI-thread methods do no I/O; the others complete off the UI thread. */
public interface RemoteProjects {
    /** Where an SSH pane is: its saved host and the shell's folder, when known. UI thread, no I/O. */
    Optional<RemoteFolder> locate(UUID paneId);

    /** Whether the host has an open connection, so background work needs no sign-in. UI thread. */
    boolean connected(UUID hostId);

    /** The host's display name, or empty when the saved host no longer exists. UI thread. */
    Optional<String> hostName(UUID hostId);

    /** Maps a location the file chooser returned from Remote's file source to a host and path. UI thread. */
    Optional<RemoteFolder> folder(FileLocation.Sourced location);

    /** For each folder, which of the names (or '*' globs) match in it. One exec; existing connection only. */
    CompletionStage<Map<String, Set<String>>> exists(UUID hostId, List<String> folders, Set<String> names);

    /**
     * Lists the files under a root, relative and '/'-separated. With a window, connects first when needed and may ask
     * the user to sign in there; without one, fails with NotConnectedException when the host has no open connection.
     */
    CompletionStage<RemoteListing> list(UUID hostId, String root, ListOptions options, Optional<WindowHandle> signIn);

    /** Opens a file in the Editor for a window; may connect and ask the user to sign in. UI thread. */
    void open(UUID hostId, String path, WindowHandle window);

    /** Opens an SSH terminal tab on the host, starting in a folder; may connect and ask to sign in. UI thread. */
    void openTerminal(UUID hostId, String folder, WindowHandle window);
}

public record RemoteFolder(UUID hostId, String hostName, String path) { }
public record ListOptions(List<String> excludes, int maxFiles, Duration timeout) { }
public record RemoteListing(List<String> files, Strategy strategy, boolean truncated) {
    public enum Strategy { GIT, FIND, SFTP }
}
```

`exists` never connects and fails with `NotConnectedException` (in the API package) without an open connection.
Background work (detection, refreshes) calls `list` without a window, so it never causes a sign-in prompt.
User-initiated jobs (Yes, Reindex, Index Folder…) pass the window the user acted in.

Remote's implementation lives in a new `dev.jasper.remote.projects` package that uses Remote's existing connection
leases, exec channels and SFTP endpoints, the existing `RemoteDocument`/`RemoteEditing` path for `open`, and the
existing SSH session provider for `openTerminal` (starting folder through the same mechanism Remote uses for its
"open terminal here" actions; the plan confirms the exact entry point).

## 2. Detection

**Markers** (setting `markers`, editable). A marker is a file or folder name, or a glob with `*`. Defaults:

- Strong: `.git`, `.hg`, `.svn`, `*.sln`.
- Weak: `Cargo.toml`, `go.mod`, `pyproject.toml`, `setup.py`, `requirements.txt`, `Pipfile`, `package.json`,
  `deno.json`, `pom.xml`, `build.gradle`, `build.gradle.kts`, `settings.gradle`, `settings.gradle.kts`,
  `CMakeLists.txt`, `Makefile`, `meson.build`, `composer.json`, `Gemfile`, `mix.exs`, `pubspec.yaml`,
  `Package.swift`, `*.csproj`, `*.xcodeproj`, `flake.nix`.

In the setting, a strong marker is written with a leading `!` (`!.git`); the default list is written out in full in the
settings file so the user sees and edits it.

**Root rule.** From the reported folder, walk up through its ancestors, stopping below the user's home folder (local)
or the login's home (remote; `/` when unknown):

1. The nearest ancestor holding a strong marker is the root.
2. Otherwise the nearest ancestor holding a weak marker is the root.
3. Otherwise there is no project.

The home folder itself and `/` are never roots, even with a marker (dotfile repositories are common).

**Probing.** Locally, `MarkerProbe` checks the file system on the indexer's local worker. Remotely, it is one
`RemoteProjects.exists` call listing every ancestor folder and every literal marker name; globs are resolved by the
remote side listing matching names (`exists` treats names containing `*` as globs). Any probe failure means no
project, logged at debug level.

## 3. Prompts

`CwdWatcher` subscribes to `TerminalEvents.CWD_CHANGED` and `COMMAND_FINISHED`.

On `CWD_CHANGED` for a pane:

1. **Where.** A local `workingDirectory` is used as is. Otherwise, when Remote is installed, `locate(paneId)` gives
   host and folder; the remote path is used only while `connected(host)` is true. Anything else is ignored.
2. **Cheap checks first** (UI thread, no I/O): if the folder is inside an indexed project's root, schedule a
   throttled refresh of that project and stop. If it is inside an ignored path, or inside a root declined this
   session, stop. If `prompt` is false, stop.
3. **Detect** on the host's worker. If no root, stop. Re-check the root against indexed, ignored, declined and
   open-card sets (another pane may have raced).
4. **Post** the card in the notification group `dev.jasper.indexer.prompts` ("Project indexing", default
   `DisplayMode.STICKY`), with `withWindow` set to the pane's window:
   - Title: `Index project?`
   - Content: `<name> · <path with ~ for home>` on the first line, then the strongest marker found
     (`Git repository`, `Cargo project`, `Visual Studio solution`, … from a fixed table, else `Contains <marker>`), and
     `on <host name>` for remote projects.
   - Actions, each expiring the card: **Yes** (register the project and start a foreground index job), **No** (add
     the root to the session "declined" set), **Ignore this path** (append it to the `ignored` setting).
   - The open card is remembered by project key; while it is open no second card is posted for that root. When the
     card expires any other way (closed, cleared), the root is treated as declined for the session.

The project's display name is the root folder's name; two projects with the same name are told apart by their tag
(host) and detail (path).

## 4. Indexing and refresh

**Jobs.** `Indexer` keeps one serial worker for local jobs and one per remote host, so a slow host never delays local
work. A job for a project already running coalesces into one queued follow-up. A foreground job (Yes, Reindex,
Index Folder…) shows a status-bar progress item `Indexing <name>…` with cancel; background refreshes show nothing
unless they fail.

**Local listing (`LocalLister`).**

1. If `git` is on the `PATH` and the root is inside a work tree, run
   `git -c core.fsmonitor=false -C <root> ls-files -z --cached --others --exclude-standard` with environment
   `GIT_OPTIONAL_LOCKS=0`, through `ProcessBuilder` with an argument list (no shell). Output is NUL-separated, so
   names with newlines survive. Entries matching an `excludes` folder name are still dropped. A non-zero exit, a
   missing `git` or a timeout (60 s) falls through to the walk.
2. **Walk** with `Files.walkFileTree`, not following links, skipping folders whose name is in `excludes` and
   unreadable folders, collecting regular files.
3. Stop at `maxFiles`; the index is then marked truncated.

Paths are stored relative to the root with `/` separators. `core.fsmonitor=false` stops a repository's own config from
running a program during listing.

**Remote listing** (inside Remote): the same order. `git -c core.fsmonitor=false -C '<root>' ls-files -z …` with
`GIT_OPTIONAL_LOCKS=0`; else `find '<root>' \( -type d \( -name '<ex1>' -o … \) -prune \) -o -type f -print0`; else an
SFTP walk. Every path and exclude is single-quoted with `'` escaped as `'\''`; remote names are untrusted text. Output
stops being read at `maxFiles` (truncated). Timeout from `ListOptions` (default 120 s for find/git; the SFTP walk
shares the same budget).

**Results.** A finished job atomically replaces the project's snapshot, saves it, and fires the Files scope's
`onChanged`. Success is posted to `dev.jasper.indexer.results` ("Index results", default `DisplayMode.HISTORY`):
`Indexed <name>` with `<n> files` content. A cancelled job keeps the old snapshot and posts nothing.

**Refresh triggers** (background, no prompt):

- A pane's reported folder enters an indexed root: at most once per `refreshMinutes` (default 5) per project.
- A command finishes (`COMMAND_FINISHED`) whose directory is inside an indexed root: debounced 3 s, same throttle.
- **Reindex** in Settings or the palette command **Reindex Current Project**: immediate, foreground.

Remote refreshes run only while the host is connected; otherwise the saved snapshot is served and marked stale.
Manual folders refresh by the same triggers.

**Manual folders.** **File › Index Folder…** (a `StandardMenu.FILE` item, plus a palette command of the same name)
opens `Windows.choose` with `Mode.DIRECTORY`, local places and Remote's source (`RemoteHosts` id) when Remote is
installed. A local pick, or a remote pick mapped by `RemoteProjects.folder`, is registered as a project with
`manual = true` and indexed in the foreground. Detection is skipped; Git listing is used when the folder happens to
be in a work tree. The settings page's **Add Folder…** does the same. Ignore never removes a manual project.

## 5. Storage and settings

**Settings file** `plugins/dev.jasper.indexer/dev.jasper.indexer.toml`, declared with `PluginSettings.declare`:

| Key | Kind | Default |
|---|---|---|
| `prompt` | bool | `true` |
| `markers` | string list | the list in §2 |
| `excludes` | string list | `.git`, `.hg`, `.svn`, `node_modules`, `target`, `build`, `dist`, `out`, `.gradle`, `.idea`, `.vscode`, `.venv`, `venv`, `__pycache__`, `.mypy_cache`, `.pytest_cache`, `.tox`, `.next`, `.nuxt`, `vendor`, `Pods`, `DerivedData`, `bin`, `obj` |
| `ignored` | string list | empty; local absolute paths, and `<host id>:<absolute path>` for remote |
| `maxFiles` | integer 1,000–1,000,000 | `200000` |
| `refreshMinutes` | integer 1–1,440 | `5` |

Changing `markers`, `excludes` or `maxFiles` does not reindex by itself; the next refresh uses the new values.

**State** under `plugins/dev.jasper.indexer/data/`:

- `projects.json`: one entry per project: key, display name, `manual` flag, last indexed time, file count, strategy,
  truncated flag. Written atomically (temp file, then move).
- `indexes/<sha256 of key>.idx`: a gzip stream holding a header line (format version, key) and the NUL-separated
  relative paths. Written atomically. A file that does not parse or whose header key does not match is deleted and
  the project marked **needs reindex**.

Indexes load on the local worker after start and never block startup; the Files scope is empty until they arrive.

**Settings page** (`PluginSettings.page`, custom `SettingsPanel`):

- **Projects** table: name, location (`path` or `host: path`), files, last indexed, state (Fresh, Stale, Truncated,
  Missing, Needs reindex, Unavailable). Buttons: **Add Folder…**, **Reindex**, **Remove** (deletes the index file
  and entry; asks first).
- List editors for Markers, Excludes and Ignored paths (Ignored shows remote entries with the host name).
- The prompt toggle and the two numbers.

## 6. Files scope and search

`FilesScope` implements `PaletteScope`: id `dev.jasper.indexer.files`, label **Files**, placeholder `Search files`,
`monospaceRows = false`, `inAll = true`, `inRecents = true`, shortcut action **Go to File** (`dev.jasper.indexer.goto`,
default binding `cmd+p`; Remote already owns `cmd+shift+o` and the app `cmd+shift+p`). Off macOS the binding follows the
keymap's existing rules for `cmd+` defaults; the plan confirms it does not take Ctrl+P away from the shell in a
terminal pane, choosing a terminal-safe alternative if it would.

**Verbs:**

| Key | Verb | Local row | Remote row |
|---|---|---|---|
| Enter | Open | `Editor.open(path, window)` | `RemoteProjects.open(host, path, window)` |
| Cmd/Ctrl+Enter | Copy path | absolute path to the clipboard | `host:path` to the clipboard |
| Shift+Enter | Open terminal here | `Terminals.openTab` in the file's folder | `RemoteProjects.openTerminal(host, folder, window)` |

Open is unavailable (row dimmed, detail says why) when the Editor is not installed, or for remote rows when Remote is
not installed or the saved host was deleted.

**Current project.** The project containing the window's focused pane folder (local, or remote through `locate`);
none when the pane is outside every project.

**Ranking** (`FileMatcher`; case-insensitive; every query word must match; the last `/`-separated part of the query
matches the file name, earlier parts match folders in order):

1. Exact file name.
2. File name prefix.
3. Initials: CamelCase humps and word starts after `_`, `-`, `.` (`FSc` → `FilesScope.java`).
4. File name substring.
5. Path match: every query part matches a path segment in order (`ui/Files` → `ui/FilesScope.java`).

Within a tier, the current project's files come first, then shorter paths, then alphabetical. Results stop at
`PaletteQuery.maxResults`. Matching scans precomputed lower-case names and paths; for 200,000 files a query is a
single linear pass. If a scan exceeds 50 ms the scope returns what it has so far (the palette re-queries on each
keystroke).

**Rows:** title = file name; detail = path relative to the root, prefixed by `Connect to open · ` for a remote row
whose host is not connected; tag = project name, with ` · <host>` for remote. Token = (project key, relative path).

**Empty query:** the current project's files this scope opened during this session, newest first (kept in memory),
else nothing.

## 7. Failures

| Situation | Behaviour |
|---|---|
| Detection cannot read a folder | No prompt; debug log. |
| Remote not installed, host not connected | No prompt, no background refresh; saved remote projects stay searchable as stale. |
| Git missing, not a work tree, or fails | Walk, silently. |
| Listing fails | Previous snapshot kept. Error card `Couldn't index <name>` with **Retry**, cause attached. |
| Truncated at `maxFiles` | Warning card `<name> has more than <n> files` (content: only the first `<n>` were indexed; add excludes) with **Open Settings**. Once per project per session. |
| Remote has no `find` | SFTP walk under the same cap and timeout. |
| Index file corrupt | Deleted; project marked Needs reindex. |
| Project root deleted | Found at refresh: removed from search, state Missing; kept until the user removes it. |
| Opened file no longer exists | The Editor or Remote reports it; a background refresh of that project is queued. |
| Plugin stops | Jobs cancelled, remote calls aborted, open cards expire with the context. |

## 8. Testing

TDD, headless, with the SDK testkit's fakes for terminals, notifications, palette, settings and services.

- **`ProjectDetector`**: fake probe; strong over weak; nearest weak marker; never home or `/`; globs; remote home
  boundary.
- **`CwdWatcher`**: one card per root across panes; dedupe while a card is open; No lasts for the session (a fresh
  watcher prompts again); Ignore writes the setting; an indexed root refreshes instead of prompting; throttle and
  debounce on a fake clock; remote panes only while connected; `prompt = false`.
- **`LocalLister`**: temp folders with a real `git init` (skipped when `git` is absent), untracked and ignored files,
  walk fallback, excludes, cap and truncation, a link loop, a name with a newline, a repository whose config sets
  `core.fsmonitor` to a command that would create a file (the file must not appear).
- **`IndexStore`**: round trip, atomic replace, corrupt and mismatched files.
- **`FileMatcher`**: each tier, multi-word and path queries, current-project boost, the time budget.
- **`FilesScope`**: verbs and availability, rows for local/remote/disconnected/deleted host, empty query.
- **`Indexer`**: per-host workers, coalescing, cancellation, progress item, result and failure cards.
- **Remote API** (in Remote, against the existing `LoopbackServer` test fixture): `locate`, batched `exists` with
  globs, `list` through git, find and SFTP fallbacks, quoting with hostile names (`'`, spaces, newlines, `$(…)`), cap,
  timeout, `NotConnectedException` without a window and connecting with one, `open` and `openTerminal` delegation.
- **Architecture**: `verifyPluginArchitecture` passes with the indexer reaching Remote and Editor only through their
  API packages; Remote's `api` package names no Remote internals.
- **Native GUI checks** (user-run): the prompt card and its three actions, the Files tab and its verbs, File › Index
  Folder… for a local and a remote folder, the Settings page.

## 9. Delivery

Two plans, each a runnable deliverable:

- **Plan A, local:** the indexer plugin with detection, prompts, local listing, storage, the Files scope, settings,
  menu item and palette commands. Remote projects are not offered yet.
- **Plan B, remote:** Remote 0.12.0 with `dev.jasper.remote.api` and `dev.jasper.remote.projects`, and the indexer's
  `RemoteBridge`, remote prompts, refreshes, rows and Index Folder… remote picks.

Documentation: the plugin's README, `docs/plugin-authoring.md`'s list of bundled plugins, and Remote's README for the
new API.
