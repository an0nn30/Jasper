# Jasper file chooser: one dialog for local and remote, open and save

Status: approved design, 2026-09-30. Jasper replaces the native `FileDialog`/`JFileChooser` and Remote's
`RemoteFilePicker` with one app-owned, Explorer-style file chooser. It opens where the active terminal is (local folder
or SSH host and folder), lists local places and remote hosts side by side in every mode, and lets you open or save on
either side regardless of where a document came from. Built on the SDK (`jasper-sdk`, 0.9.x), the app
(`jasper-app`), Editor (`plugins/editor`, API `dev.jasper.editor.api`) and Remote (`plugins/remote`), all on local
`main` at fa4ca54b. Supersedes the picker parts of
[2026-09-29-remote-open-file-design.md](2026-09-29-remote-open-file-design.md).

Visual north star: [assets/2026-09-30-file-chooser-mockup.html](assets/2026-09-30-file-chooser-mockup.html) (Open
with preview, Save, remote connecting). The mockup fixes layout and behaviour; exact spacing, colours and icons follow
the active FlatLaf theme.

## Outcome

- Cmd/Ctrl+O ("Open in Editor…") shows the Jasper chooser, not the native one, at the focused terminal's current
  folder: a local folder for a local pane, the host and folder for an SSH pane.
- Every chooser (Open, Save, Directory) lists local places and remote hosts together; a remote host is just another
  item in the navigation pane. There is no separate Connect button and no host dropdown.
- Save As can write a local document to a remote host and a remote document to the local disk.
- Every plugin that uses the SDK's `chooseFiles`, `chooseDirectory` or `chooseSaveFile` gets the new dialog
  (local places only, since those calls return `Path`).
- The dialog appears in about 100 ms; listing never blocks the UI.

## Decisions (user, 2026-09-30)

1. **Layout A** (Explorer navigation pane of places) **plus C's preview pane as a toolbar toggle.**
2. **Remote in every mode.** Open, Save and Directory all list remote hosts; the start location follows the active
   terminal, local or remote.
3. **No Connect button:** clicking a host connects.
4. **Approach 1:** the app owns the dialog; plugins contribute places through a new SDK extension point.
5. **Drop File › Open Remote File…** (`dev.jasper.remote.open-file`); Cmd+O covers it.
6. **Recent** is the chooser's own folder history, not the Editor's recent files.
7. Delivery in three plans (A, B, C below).

## Alternatives considered

- **Remote owns the chooser and publishes it as a service.** Least SDK surface, but local choosing would depend on
  Remote being installed, the Editor would keep a native fallback, and other plugins' choosers would stay native.
- **Editor owns it; Remote contributes hosts through the Editor API.** Smaller, but only the Editor benefits.

## SDK: `dev.jasper.sdk.files` (SDK 0.10.0)

JDK-only, like the rest of the SDK. Signatures are indicative; the plan fixes them.

### `FileLocation`

A sealed interface returned by the chooser:

- `FileLocation.Local(Path path)`: absolute, normalized.
- `FileLocation.Sourced(String sourceId, String rootId, String path)`: `path` is in that source's syntax (POSIX for
  SFTP). `sourceId` is the registering plugin's source id; `rootId` is opaque to the host.

Both expose `label()` for display, e.g. `~/projects/moray/build.gradle` or `amp-amp:/home/amp/x.txt`. Labels built
from remote strings are untrusted text.

### `FileSource` (implemented by plugins; UI thread, no I/O)

Modelled on `PaletteScope`: the host contains every failure (a throwing method counts as empty/unavailable).

- `FileSourceSpec spec()`: id and section heading (e.g. "Remote").
- `List<FileRoot> roots()`: `FileRoot(String id, String label, String detail, boolean connected)`; for Remote, one per
  saved host, `detail` = `user@host`.
- `Subscription onChanged(Runnable listener)`: roots or connected state changed (drives the green dot).
- `Optional<FileLocation> locate(PaneHandle pane)`: the location a terminal pane is at, or empty. Remote maps its pane
  → `ConnectionIdentity` and `PaneInfo.remoteDirectory` here; only Remote can.
- `CompletionStage<FileBrowser> connect(String rootId, WindowOwner owner)`: may show the Vault sign-in; cancelling the
  stage aborts. Completes immediately when already connected.

### `FileBrowser` (host calls it on one background worker per dialog)

`home()`, `list(String dir, Consumer<FileEntry> visitor)`, `stat(String path)`, `preview(String path, int maxBytes)`,
`mkdir(String path)`, `parent`/`child` (path syntax, traversal-safe), `abort()` (the only cross-thread call),
`close()`. `FileEntry(name, Kind {FILE, DIRECTORY, LINK, SPECIAL}, size, modified, linkTargetKind)` mirrors Remote's
`FileEntry`. Remote implements it over its existing `FileEndpoint`.

### Registration and capability

`context.files().register(FileSource)` → `Subscription`; requires a new `files.source` capability. The app's local
source implements the same interface internally, so `FileSource` has two real implementations (app local, Remote), plus
the testkit fake.

### Choosing: `Windows.choose(FileChoice)`

`FileChoice(owner, title, Mode {OPEN, SAVE, DIRECTORY}, boolean multiple, Optional<FileLocation> initial,
Optional<String> suggestedName, List<FileFilter> filters, Set<String> sources)`. `sources` lists the source ids the
caller can handle (empty = local only). Returns `List<FileLocation>`, empty on cancel. Same rules as today's calls:
synchronous, UI thread, pumps events, cancelled by owner closure or plugin stop; foreign/unshown/dead owner →
`IllegalArgumentException`.

**Start location** when `initial` is empty, in order: (1) the first allowed source whose `locate()` answers for the
owner's active pane; (2) the pane's local `workingDirectory`; (3) the user's home.

**Existing calls stay:** `chooseFiles`, `chooseDirectory`, `chooseSaveFile` keep their `Path` signatures and delegate to
`choose` with `sources` empty. `NativeShells`' `FileDialog`/`JFileChooser` code for them is deleted (`chooseFile` with
a suffix filter, app-internal, also moves to the new dialog with a filter).

The testkit `FakeUi` scripts `choose` like it scripts the current pickers, and the contract suite covers `FileSource`
and `FileBrowser`.

## App: the dialog (`dev.jasper.app.files`, new package)

The only place that draws the chooser. Package-info names its dependencies (windows, appearance, plugins registry for
sources) per the app conventions; `verifyApplicationArchitecture` rules are updated for the new package.

### Layout

Toolbar (back, forward, up, refresh; path bar; search; New folder in Save mode; preview toggle), navigation pane
(Places, Pinned, then one section per source), file list, optional preview pane, bottom bar (File name, filter
dropdown, status text, Cancel, primary button).

### Navigation

- **History** (back/forward) spans sources: back can return from a remote host to local.
- **Up** = parent; **Refresh** reloads.
- **Path bar:** clickable breadcrumbs; clicking its empty area or Cmd/Ctrl+L makes it a text field.
- **File name field** accepts `~/…`, relative and absolute paths: a folder navigates, a file opens/saves.
- **Search** filters the current folder as you type (not recursive).

### File list

Sortable Name / Modified / Size table, folders first, no paging. Entries stream in batches of 256 from the worker; a
spinner appears only after 150 ms. Keyboard: type-to-select, Enter opens, Backspace goes up, Alt+Left/Right history,
Cmd+Shift+. toggles dotfiles (shown by default, remembered). Multi-select in Open mode. Unreadable folders show the
error inline.

### Preview pane

Toolbar toggle, remembered. Shows name, size, modified; for text, the first 8 KB via `FileBrowser.preview`; a file
whose first bytes contain NUL is binary and shows metadata only; a folder shows its item count. Loaded on the worker,
debounced on selection change.

### Modes

- **Open:** button "Open"; acts on the selection (multiple allowed when `multiple`).
- **Save:** title "Save As", button "Save"; File name prefilled with `suggestedName`, name part (before the extension)
  selected; New folder with inline naming; files greyed out, clicking one copies its name; replacing an existing file
  asks first.
- **Directory:** folders only, button "Choose"; with no selection, chooses the current folder.

### Places

- **Terminal folder:** the start location computed above.
- **Home, Desktop, Downloads:** whichever exist.
- **Recent:** the last 10 folders a choice was made in, local and remote.
- **Pinned:** right-click a folder (list or path bar) → Pin / Unpin; remote folders can be pinned.
- Recent, Pinned, preview and dotfile toggles, dialog size and column widths live in app UI state. Recent/Pinned entries
  for a source that is not registered are hidden, not deleted.

### Remote hosts

Listed per source with a green dot when connected. Clicking a host connects immediately and shows the connecting state
(spinner, `user@host · port`, Cancel connection) in the list area; failure shows the error and Retry. A host that is
already connected opens at the folder last used there, else `home()`.

### Speed

One dialog instance per window, built lazily on first use and reused; showing it never waits for I/O. Target: visible
in about 100 ms.

## Editor

- **Editor API:** `locationDocuments(String sourceId, LocationResolver resolver)` → `Subscription`, where the resolver
  offers `EditorDocument document(FileLocation.Sourced location, boolean create, WindowOwner owner)` and
  `Optional<FileLocation> location(String documentKey)` (the reverse lookup). Editor API version bump.
- **Cmd+O:** `choose(OPEN, multiple, initial)` with `sources` = the ids that have a resolver. `initial` is empty
  (terminal context) unless an Editor tab has focus, in which case it is that document's folder (local parent, or the
  resolver's `location(key)` parent). `Local` results → `open(path, owner)`; `Sourced` results → resolver →
  `open(document, owner)`.
- **Save As:** `choose(SAVE, …)` starting at the document's current location with its name suggested. A `Sourced`
  target → resolver with `create = true` → `session.saveAs(document)`. Document key and tab host update as for a local
  Save As; a failed remote write uses the existing save-error UI and the buffer stays dirty.
- `opener(Predicate<OpenRequest>)` stays in the API for compatibility; nothing bundled uses it.

## Remote

- Registers a `FileSource` for its saved hosts (`HostStore`, `Connections`) and a `LocationResolver` reusing
  `RemoteDocument`, with a new create path (`FileEndpoint.create` + `publish`).
- Deletes `RemoteFilePicker`, the picker branch of `RemoteOpening`, `SftpUi.openRemoteFile`, the `OPEN_FILE` action and
  its File menu entry, and the `editor.opener` registration. `SftpPanel.pickMode` goes if nothing else uses it.
- Connections stay Remote's: the chooser's `FileBrowser` holds whatever lease Remote needs while the dialog browses and
  releases it on close or host switch; the chooser never disconnects.

## Lifetimes, threading and errors

- A `FileBrowser` lives from connect until the dialog closes or leaves that host.
- Owner closure or plugin stop cancels the dialog, as today. A source's plugin stopping while the dialog is open removes
  its section; if you are browsing it, the dialog returns to Terminal folder with a notice.
- One worker per dialog runs all `FileBrowser` calls (matches `FileEndpoint`'s worker confinement). Results return to
  the EDT tagged with a navigation generation; stale results are dropped.
- Source failures (throws, failed connect) are contained and shown inline with Retry.
- Remote names and error text are untrusted: rendered with `html.disable` and control characters escaped.
- The dialog writes nothing except folders made with New folder; saves happen in the caller after a choice.

## Testing

- SDK contract suite: `FileSource`/`FileBrowser` contracts in `jasper-sdk-testkit`; `FakeUi.choose` scripting.
- App, headless, against a fake source: navigation and cross-source history, start-location order, path entry, save
  confirmation, stale-result dropping, failure containment, source removal while open, UI-state persistence.
- Remote: its `FileBrowser` over the existing fake SFTP endpoint; `locate()` for SSH panes; resolver create path.
- Editor: Cmd+O and Save As routing for `Local` and `Sourced` results, and `sources` filtering.
- GUI checks (look and ~100 ms open time) are handed to the user.

## Delivery

- **Plan A:** SDK `dev.jasper.sdk.files` + `Windows.choose`, the app dialog with the local source, the three `Path`
  calls moved onto it, Editor Cmd+O using it for local files at the terminal's folder. Fixes the slowness on its own.
  Remote's existing opener still runs first, so in an SSH pane Cmd+O shows the old remote picker until plan B.
- **Plan B:** Remote `FileSource` + `locate()`, Editor `locationDocuments` and remote Cmd+O; delete the old picker and
  Open Remote File….
- **Plan C:** Save As to any location (Editor + Remote create path) and Directory-mode polish.

## Out of scope

Recursive search, file operations beyond New folder (rename, delete, copy), thumbnails or image preview, and plugins
other than Remote contributing sources (the extension point allows it; none is planned).
