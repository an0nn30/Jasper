# Opening remote files from Cmd/Ctrl+O and File › Open Remote File…

Status: approved design, 2026-09-29. "Open in Editor…" (Cmd/Ctrl+O by default) becomes context-aware: in a focused
SSH pane, or in an Editor tab showing an SFTP file, it opens a remote file picker on that host instead of the local
chooser. Remote adds File › Open Remote File…, which opens the same picker with a host dropdown. Built on the Editor
(`plugins/editor`, API `dev.jasper.editor.api`, 0.4.0), Remote (`plugins/remote`, 0.6.2), its SFTP browser
(`dev.jasper.remote.ui.sftp`) and the Keymap (`[keybindings]`, Settings › Keymap), all on local `main`.

## Outcome

- Cmd/Ctrl+O in a focused SSH terminal pane shows a remote file picker on that pane's connection, at the shell's
  current folder. Picking files opens them in the Editor.
- Cmd/Ctrl+O in an Editor tab showing a remote file shows the picker on that file's host, at the file's folder.
- Anywhere else, Cmd/Ctrl+O shows the local chooser as today.
- The shortcut is the Keymap binding of "Open in Editor…" (`dev.jasper.editor.open`): rebinding it there changes it
  in terminal panes and Editor tabs alike.
- File › Open Remote File… (`dev.jasper.remote.open-file`) opens the picker with a host dropdown, preselected when the
  focused pane or Editor tab is remote. It has no default shortcut and can be bound in the Keymap.

## Decisions (user, 2026-09-29)

1. **"Remote tab"** means a focused SSH terminal pane or a focused Editor tab showing an SFTP file.
2. **Approach A:** the Editor keeps Cmd/Ctrl+O and asks registered *openers* first; Remote registers one. Remote adds
   the picker and the menu item.
3. **Open Remote File…** has no default shortcut.

## Alternatives considered

- **Remote owns Cmd+O and falls back to the Editor's chooser.** When two plugins claim the same default, the first
  registered keeps it, so the outcome depends on load order, and the Editor's shortcut would depend on Remote.
  Rejected.
- **Two shortcuts** (Cmd+O local, another for remote): no API change, but not what was asked. Rejected.
- **Keeping the Editor tab's own Cmd+O command** and making it context-aware too: works, but that stroke is hardcoded
  (`ContentCommand` strokes are not in the Keymap), so rebinding "Open in Editor…" would not reach Editor tabs.
  Rejected.

## Facts this design relies on (verified on `main` 962dc944; unchanged at f8c9255e)

- `WindowContent.dispatchShortcut` tries the selected hosted content's `ContentCommand` strokes before the Keymap;
  the Editor tab publishes "open" with Cmd+O, so today Cmd+O in an Editor tab runs `EditorWindow.openChooser` and in a
  terminal pane runs the `dev.jasper.editor.open` action. Every workspace window, including a separated one, installs
  the Keymap.
- `ActionContext.pane()` is the focused terminal pane, and is empty while a hosted (non-terminal) tab is selected.
- Remote keeps `paneIdentities: Map<UUID, ConnectionIdentity>` for its connected SSH panes; `PaneInfo.remoteDirectory()`
  carries the shell's current folder when known.
- `DestinationPanel` (the Copy to host picker) already combines a host `JComboBox`, an `SftpPanel` in a reduced mode
  and an `SftpController`; `SftpUi` resolves a chosen host with `connections.resolveIdentity(host.id(), window)`.
- `context.windows().dialog(new DialogSpec(title, owner, true))` gives a modal plugin dialog; `menus().standard(FILE)`
  needs no capability.

## Design

### 1. Editor API (Editor 0.4.0 → 0.5.0)

In `dev.jasper.editor.api`:

```java
/**
 * What Open in Editor… was invoked on.
 *
 * @param window the workspace window
 * @param pane the focused terminal pane; empty while another kind of tab is selected
 * @param documentKey the key of the Editor tab that is active, when one is
 */
public record OpenRequest(WindowHandle window, Optional<PaneHandle> pane, Optional<String> documentKey) { }
```

and on `Editor`:

```java
/**
 * Lets a plugin answer Open in Editor… in its own way, for example by browsing a remote host. Handlers run on the UI
 * thread in registration order before the local file chooser; the first to return true has handled the request. A
 * handler that throws counts as false. Since Editor 0.5.0.
 *
 * @param handler answers a request; true when it handled it
 * @return removes the handler
 */
default Subscription opener(Predicate<OpenRequest> handler) { return () -> { }; }
```

The package-info already allows the SDK.

### 2. The Editor's Open

- A new package `dev.jasper.editor.open` holds `Openers`: the handlers
  (registration order; `register` returns a `Subscription` removing only that handler) and `boolean handle(OpenRequest)`.
- `EditorWindow.open(WindowHandle window, Optional<PaneHandle> pane)` is the single Open routine:
  1. `documentKey` is the key of the hosted tab whose `ContentTab.isActive()` is true, if any, and only when `pane` is
     empty.
  2. If `openers.handle(request)` returns true, stop.
  3. Otherwise show the local chooser, starting in the active local document's folder (as `openChooser` does today),
     and open the chosen files for `window`.
- The action `dev.jasper.editor.open` calls it with `invoked.window()` and `invoked.pane()`.
- The Editor tab's "open" `Command` keeps its id and title but loses its stroke (`Optional.empty()`, not bound), so
  Vim's `:action open` and `<Action>(open)` still work and Cmd+O falls through to the Keymap. Run from Vim or the
  command list, it calls the same routine with no pane. "open" is not in the right-click popup (`POPUP`), so the popup
  is unchanged.
- A tab's owner is a `ContentTab`, not a `WindowHandle`. When the in-tab command runs, the request's `window` is the
  last `WindowHandle` the Editor was invoked from (`origin` when it is a `WindowHandle`), else the result of
  `context.terminals().activeWindow()`; if neither exists, openers are skipped and the local chooser opens.

### 3. The remote file picker (Remote, `dev.jasper.remote.ui.sftp.RemoteFilePicker`)

A `JPanel implements AutoCloseable`, shown in a modal `PluginDialog` titled "Open Remote File" by `SftpUi`:

- **Top:** a host `JComboBox<RemoteHost>` of the saved hosts (sorted as the SFTP host chooser sorts them), with the
  initial host and folder passed in, or no selection.
- **Middle:** an `SftpPanel` in a new *pick* mode: multiple selection, files and folders listed, navigation (up, path
  field, double-click a folder), refresh; no transfer, delete, copy-path or follow controls and no popup menu. Its
  `SftpController` is not `foldersOnly`.
- **Bottom:** Cancel and Open. Open is enabled when the selection holds at least one file or link.
- **Choosing a host** resolves it with `connections.resolveIdentity(host.id(), window)` (which may ask for credentials)
  and opens the controller at the home folder. A failure is shown in the panel's status line; the dropdown stays
  usable.
- **Opening:** double-clicking a file, or Open, resolves links with `RemoteEditing.kinds` and opens up to
  `RemoteEditing.MAX_OPEN` (20) files with `RemoteEditing.open(identity, path, window)`, reusing
  `SftpUi.openSelected`'s logic (moved to a shared helper, not duplicated). The dialog then closes. Links to folders
  are entered, as in the SFTP panel.
- **Closing** cancels pending listings and closes the controller. It opens endpoints the way `SftpUi` does
  (`endpoints.open(Optional.of(identity), owner, …)`), so closing releases only its own lease and never a connection
  the SFTP panel or a terminal still uses.

### 4. Remote's opener and action (Remote 0.6.2 → 0.7.0)

Both exist only when the Editor is installed (inside the block that builds `RemoteEditing`).

- **Opener.** `RemotePlugin` registers `request -> remoteOpen(request)`:
  - `request.pane()` is a pane in `paneIdentities`: show the picker on that identity at
    `pane.info().remoteDirectory()`'s path, or the home folder when unknown; return true.
  - else `request.documentKey()` parses (`RemoteDocument.parseKey`) and every hop's host is saved: show the picker on
    that identity (built as `RemoteEditing.reopen` builds it) at the file's parent folder; return true.
  - otherwise return false.
  - With an Editor older than 0.5 the registration throws `NoSuchMethodError`; Remote catches it and Cmd+O stays local.
- **Action** `dev.jasper.remote.open-file`, "Open Remote File…", keywords sftp, remote, open, file, edit; icon FOLDER;
  no default binding. It always shows the picker: on the invocation's SSH pane (host and folder preselected, as the
  opener does) when there is one, otherwise with no host selected. Remote cannot see which Editor tab is active, so
  an Editor tab does not preselect here; Cmd/Ctrl+O covers that case. Added to `menus().standard(StandardMenu.FILE)` and to the SSH menu.
- The manifest's version becomes `0.7.0`; its Editor requirement stays `>=0.2`.

## Architecture and boundaries

- The Editor names `WindowHandle` and `PaneHandle` (SDK) in its API; no new capability, since it only passes the pane
  along. Remote reads `pane.info()` under its existing `terminal.observe`.
- Remote names Editor types only in `dev.jasper.remote.edit`; the opener's `OpenRequest` handling lives in a new
  `dev.jasper.remote.edit.RemoteOpening` (the third class there allowed to name Editor types; its package-info is
  updated) which calls back into Remote through JDK/SDK types only, so `RemotePlugin` still loads without the Editor.
- `verifyPluginArchitecture` already allows Remote to import `dev.jasper.editor.api`.

## Testing

- Editor, `OpenersTest`: registration order; first true wins; a throwing handler is skipped; unsubscribe.
- Editor, `EditorPluginTest`: Open in Editor… with no opener shows the local chooser (queued selection opens a tab); an
  opener that returns true suppresses the chooser and receives the pane; with an Editor tab active and no pane, the
  request carries its key.
- Editor, `EditorWindowTest`: the "open" command has no stroke; `:action open` runs Open.
- Remote, `RemoteOpeningTest`: a connected SSH pane opens the picker at its folder; a `remote:` key opens it at the
  parent folder; a key whose host is deleted, a local pane and no context return false; `NoSuchMethodError` from an old
  Editor is tolerated.
- Remote, `RemoteFilePickerTest` against the loopback SFTP server (`RemoteEditingPluginTest`'s fixture): listing, entering
  a folder, opening a file, a link to a file, a link to a folder, the 20-file cap, Cancel, switching hosts, a host that
  fails to connect.
- Remote, plugin test: `dev.jasper.remote.open-file` is registered, in File and SSH menus, only with an Editor.
- GUI checks (user): Cmd+O in an SSH pane, a remote Editor tab and a local pane; rebinding Open in Editor… in the
  Keymap moves all three; File › Open Remote File… with and without an SSH pane focused.

## Out of scope

- Remembering the last folder per host, previewing files, creating files from the picker, and opening folders.
