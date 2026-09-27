# Editor plugin

**Status:** Approved by the user on 2026-09-27. Plan A (SDK 0.8.2 and the Editor for local files) is executed and merged. Plan B (SFTP editing through Remote) amends sections 4.4, 6.1, 6.2 and 6.4, as marked **Amended (Plan B)**, with the user's approval of that plan. Depends on the [plugin SDK](2026-09-21-jasper-plugin-sdk-design.md) and, for Plan B, on [Remote SFTP](2026-09-23-jasper-remote-sftp-design.md).

## Purpose and scope

Jasper browses remote hosts in Remote's SFTP panel but cannot edit a file there: double-clicking a file does nothing. A new bundled plugin, **Editor**, edits local and remote text files in tabs using RSyntaxTextArea. Double-clicking a file in the SFTP panel opens it in a new editor tab, and saving writes it back to the host.

Decisions the user made:
- **Where tabs live:** in one separate **Editor window**, a plugin window Jasper builds (title bar, theme, remembered bounds) with its own row of file tabs. Not in the main tab strip beside terminals, and not in a panel region. Moving editor tabs into the main strip stays possible later.
- **Quitting asks the editor.** Quit runs plugin window closing guards first (an SDK change), so ⌘Q with unsaved edits asks Save All / Discard / Cancel. Autosaved drafts and accepting the loss were rejected.
- **Extras in the first version:** a language picker and a soft-wrap toggle; New file and Save As (to local paths only); Revert to Saved; watching open local files for outside changes.
- **Highlighting:** RSyntaxTextArea's built-in lexers. Language detection lives in one class so a TextMate lexer can be added later without touching tabs or saving. TextMate grammars through Eclipse tm4e now were rejected: tm4e is not officially on Maven Central (only third-party repackagings), needs joni and jcodings, needs its state stacks mapped onto RSyntaxTextArea's integer line state, and needs a scope-to-colour theme.
- **Day-one languages:** TOML, JSON, Python, Bash and YAML. RSyntaxTextArea 3.6.0 has JSON, Python, Unix shell and YAML lexers but no TOML lexer, so the Editor ships its own.
- **Delivery:** one specification, two plans (see section 12).

Out of scope:
- TextMate grammars, and token colours taken from an installed IntelliJ theme's editor scheme;
- Save As to a remote path, and creating new remote files;
- restoring open tabs across restarts;
- autocomplete, language servers, and a diff view on conflict;
- more than one Editor window, and editor tabs in the main tab strip;
- choosing an encoding by hand, and switching line endings;
- a file tree or project view.

## 1. Plugins and boundaries

### 1.1 The Editor plugin

`plugins/editor`, Gradle module `:jasper-plugin-editor`:

```toml
id = "dev.jasper.editor"
name = "Editor"
version = "0.1.0"
entry = "dev.jasper.editor.EditorPlugin"
sdk = ">=0.8.2, <0.9"
description = "Edit local and remote text files in tabs, with syntax highlighting."
vendor = "Jasper"
capabilities = []
exports = ["dev.jasper.editor.api"]
```

- It compiles against the SDK (`compileOnly`) and bundles `com.fifesoft:rsyntaxtextarea:3.6.0` (BSD-3-Clause, no dependencies) as `implementation`.
- It needs no capability: it registers actions, menu items and one window, and reads local files, which plugins may do without one.
- `stagePlugins` in `jasper-app/build.gradle.kts` adds it to the application image. It requires no other plugin.
- Plugins cannot see the application's libraries, FlatLaf included. The Editor uses FlatLaf features only through client-property string keys whose values are JDK types (for example `JTabbedPane.tabClosable` and `JTabbedPane.tabCloseCallback`).

### 1.2 The exported API

Package `dev.jasper.editor.api`, the only package the Editor exports:

```java
/** Published by the Editor plugin with Services.publish. UI thread only. */
public interface Editor {
    /** Opens a local file in a tab, or selects the tab already showing it, and brings the Editor window to the front. */
    void open(Path file);
    /** Opens a document another plugin provides, or selects the tab whose document has the same key. */
    void open(EditorDocument document);
}

/**
 * A file the editor reads and writes through whoever provides it. The editor calls these methods on the
 * UI thread; none may block. Futures may complete on any thread.
 */
public interface EditorDocument {
    /** Stable identity: two documents with equal keys are the same file. */
    String key();
    /** The file name, used as the tab title. */
    String name();
    /** The full location for tooltips and the status line, for example "dustin@prod-web:/etc/nginx/nginx.conf". */
    String location();
    /** The host a remote document lives on, shown dimmed after the name; empty for a local document. */
    Optional<String> host();
    /** Reads the whole file. Fails with TooLargeException, before transferring content, when it is larger than limitBytes. Cancelling aborts the transfer. */
    CompletableFuture<Content> read(long limitBytes);
    /** The file's current revision, or empty when it no longer exists. */
    CompletableFuture<Optional<Revision>> revision();
    /** Replaces the file's content following the write contract (section 4.4) and returns the new revision. */
    CompletableFuture<Revision> write(byte[] content);
}

/** A file's bytes and the revision they were read at. The array passes to the receiver; the provider must not reuse it. */
public record Content(byte[] bytes, Revision revision) { }

/** Modification time and size: two reads with equal revisions are treated as the same file content. */
public record Revision(long modifiedMillis, long size) { }

/** The file is larger than the limit the editor asked for. */
public final class TooLargeException extends IOException { /* size(), limit() */ }
```

`EditorDocument` has two real implementations: the Editor's own `LocalDocument` and Remote's `RemoteDocument`. `Editor` has one implementation and a test fake, as `SnippetService` and `VaultApi` do.

### 1.3 Remote

Remote 0.4.0 changes (Plan B):
- **Manifest:** `requires` gains `{ id = "dev.jasper.editor", version = ">=0.1", optional = true }`. The SDK floor stays `>=0.8.1`.
- **Build:** `compileOnly(project(":jasper-plugin-editor"))` and `testImplementation` of the same; `declaredImports` in `gradle/plugin-architecture.gradle.kts` gains `dev.jasper.editor.api` for `:jasper-plugin-remote`.
- **Lookup:** `services().find(Editor.class)` once at start. Without the Editor, Remote behaves as today and the new menu item is hidden.
- **What Remote adds** is in section 6.

The rejected direction was Remote exporting a file API that the Editor requires. A double-click in Remote would then have to reach the Editor as an event, and local editing would depend on Remote. With the chosen direction the Editor stays generic and Remote remains the only code that knows SFTP.

### 1.4 SDK 0.8.2

`JasperSdk` goes to 0.8.2. Three additions, each implemented by the application (`HostedUi`, `AuxiliarySurface`, `NativeShells`) and by the testkit (`FakePluginHost`), with contract cases in `PluginContractTest` where the fake can express them:

1. **Quit consults plugin window closing guards.** Quit (⌘Q, the Quit menu item, the macOS quit event) and Restart (from the Plugins manager and elsewhere) first run the `WindowSurface.onClosing` guards of every open plugin window, in window creation order. The first guard that returns false cancels the quit or restart, and nothing is closed. The guards run before `JasperApplication` sets `quitting` and before `ApplicationShutdown` arms the exit deadline, so a guard's modal prompt can never be ended by the deadline. Plugin dialogs and overlays are not consulted. This replaces the documented rule, in `WindowSurface` and `plugin-authoring.md`, that quitting does not consult guards. Testkit: `FakePluginHost.requestQuit()` runs the guards the same way and returns whether the quit may proceed.
2. **`WindowSurface.onCloseCommand(BooleanSupplier handler)`,** returning a `Subscription`. The Close Window command of a plugin window (File → Close Window and ⌘W) asks these handlers first, newest first. The first to return true means the content handled the command, for example by closing a tab, and the window stays open. When none returns true, the window closes through its closing guards as today. The window's own close button does not consult these handlers. Reason: with the macOS screen menu bar, the Close Window accelerator fires before the content's key bindings see ⌘W; the shortcut recorder already works around the same problem through `WindowInput.captureShortcut`. Testkit: `FakePluginHost.closeCommand(windowId)`.
3. **`Windows.chooseSaveFile(WindowOwner owner, String title, Optional<Path> initial)`,** returning `Optional<Path>`: a native save-file chooser (AWT `FileDialog` in `SAVE` mode) with the same owner, lifetime and UI-thread rules as `chooseFiles`. `initial` may name a directory or a suggested file. The platform dialog confirms replacing an existing file. Returns an absolute normalized path, or empty on cancellation or owner closure. The default method throws `UnsupportedOperationException`, as `chooseFiles` does. Testkit: queued selections, as for `chooseFile`. The SDK had only existing-file and directory choosers, and Save As needs a new name.

## 2. The Editor window

- **One window for the whole application:** `WindowSpec("dev.jasper.editor.window", "Editor", 900 × 650, singleton = true)`. Jasper remembers its bounds. It opens with the first file and closes when the user closes it; open tabs are not restored at the next launch.
- **Layout, top to bottom:**
  1. **Tab row:** a scrolling tab strip with a close button on each tab. A tab shows the `FILE` icon for a local document or `SERVER` for one with a host, the file name, and for a remote document the host in dimmer text (`nginx.conf  prod-web`). When two open files share a name, the parent folder's name is added to each. A **●** before the name marks unsaved changes. The tooltip is the document's `location()`. At the right end, a **⋯** button lists every command in section 2.1 with its shortcut.
  2. **Banner** (section 5), shown above the text only when needed.
  3. **Text area:** RSyntaxTextArea in an `RTextScrollPane` with line numbers, code folding, bracket matching, mark occurrences and a current-line highlight. Its right-click menu adds Save, Find, Go to Line and Toggle Comment to RSyntaxTextArea's own items.
  4. **Find/replace bar,** hidden until used, styled like the terminal's find bar: a search field with a match count and previous/next buttons; toggles for match case, whole word and regular expression; a replace row with Replace and Replace All. It uses RSyntaxTextArea's `SearchEngine`.
  5. **Status line:** `Ln 12, Col 4 · Python · UTF-8 · LF · Spaces: 4 · Wrap`. Clicking the language opens the language picker (section 3.1). Clicking Wrap toggles soft wrap for that tab.
- **Empty state:** with no tabs, the window shows "Open a file with ⌘O, or double-click one in the SFTP panel."
- **Drag and drop:** files dropped onto the window from the desktop open as local documents. Folders are ignored.

### 2.1 Commands and shortcuts inside the Editor window

Key bindings belong to the window's content; ⌘ means the platform's menu shortcut key (Ctrl on Windows and Linux).

| Shortcut | Command |
|---|---|
| ⌘S | Save |
| ⇧⌘S | Save As… (local path, through `chooseSaveFile`) |
| ⌥⌘S | Save All |
| ⌘O | Open… (`chooseFiles`) |
| ⌘N | New untitled file |
| ⌘W | Close tab, through `onCloseCommand`; with no tabs, the window closes |
| ⌘F / ⌥⌘F | Find / Replace |
| ⌘G / ⇧⌘G | Next / previous match |
| ⌘L | Go to line |
| ⌘/ | Toggle comment |
| ⇧⌘] / ⇧⌘[ | Next / previous tab |
| (none) | Revert to Saved, in the ⋯ menu |

Undo, redo, cut, copy, paste and select all are RSyntaxTextArea's own bindings. Escape closes the find bar.

### 2.2 Actions in terminal windows

Registered with `context.actions()`, so they appear in every terminal window's palette; the first two are also placed in `StandardMenu.FILE`:
- `dev.jasper.editor.open`, **Open in Editor…**, default binding `cmd+o`: a native multi-file chooser owned by the invoking terminal window, then each file opens as a local document.
- `dev.jasper.editor.new`, **New Editor File**: an untitled tab.
- `dev.jasper.editor.show`, **Show Editor**: opens or raises the Editor window.

### 2.3 Tabs

- **De-duplication:** opening a document whose key matches an open tab selects that tab. A local file's key is `local:` plus its absolute normalized path.
- **Untitled tabs** are named `Untitled`, `Untitled 2`, …; saving one runs Save As.
- **Save As** writes the text to the chosen local path through `LocalDocument` (a remote tab included), and the tab then edits that local file. A path already open in another tab is refused with a message.
- **Tab states:**
  - **Loading:** "Loading nginx.conf from prod-web…" (or "Loading nginx.conf…" for a local file) with Cancel. Cancel closes the tab.
  - **Ready:** the text, editable.
  - **Saving:** the status line says "Saving…". Typing continues; the save writes the snapshot taken when it started.
  - **Could not open:** the reason (section 8) with Retry and Close.
- **Closing a tab with unsaved changes** asks "Save changes to nginx.conf?" with **Save**, **Don't Save** and **Cancel**, in a modal dialog owned by the Editor window.
- **Closing a tab while it is saving** waits for the save: the tab closes when the save succeeds, and stays open showing the error when it fails.
- **Closing the window, or quitting,** with unsaved tabs (through the window's closing guard) lists those files and offers **Save All**, **Discard** and **Cancel**. Save All shows a modal "Saving 3 files…" dialog with Cancel; the dialog is document-modal to the Editor window, so a credential prompt over a terminal window stays usable. The guard returns true only when every save succeeded. When one fails, the dialog shows the error and the guard returns false. Cancel returns false at once; saves already running finish in the background and update their tabs.

## 3. Editing

### 3.1 Languages

`SyntaxDetector` maps a file name and first line to an RSyntaxTextArea syntax style. It is the only place that decides a language, which is where a TextMate lexer would plug in.

| Language | Style | Detected from |
|---|---|---|
| TOML | `text/toml` (Editor's own, section 3.2) | `.toml`, `Cargo.lock`, `Pipfile`, `poetry.lock` |
| JSON | `SYNTAX_STYLE_JSON`; `SYNTAX_STYLE_JSON_WITH_COMMENTS` for `.jsonc` | `.json`, `.jsonc` |
| Python | `SYNTAX_STYLE_PYTHON` | `.py`, `.pyi`, `.pyw`; a shebang naming `python` or `python3` |
| Bash | `SYNTAX_STYLE_UNIX_SHELL` | `.sh`, `.bash`, `.zsh`, `.bashrc`, `.bash_profile`, `.profile`, `.zshrc`; a shebang naming `sh`, `bash` or `zsh` |
| YAML | `SYNTAX_STYLE_YAML` | `.yml`, `.yaml` |

- Other styles RSyntaxTextArea knows are detected by an unambiguous extension (for example `.go`, `.rs`, `.js`, `.ts`, `.xml`, `.sql`, `.md`, `Dockerfile`, `Makefile`). Everything else is plain text.
- A shebang wins over the extension only when the name has no recognised extension.
- The **language picker** lists every style RSyntaxTextArea's `TokenMakerFactory` knows plus TOML, by display name, with a filter field. Choosing one changes that tab only, until it closes.

### 3.2 The TOML lexer

`TomlTokenMaker` is hand-written on RSyntaxTextArea's `TokenMakerBase` (no JFlex build step) and registered with `AbstractTokenMakerFactory.putMapping("text/toml", className, pluginClassLoader)`, because RSyntaxTextArea creates lexers by class name.

| TOML | Token type |
|---|---|
| `# comment` | `COMMENT_EOL` |
| `[table]`, `[[array.table]]` headers | `RESERVED_WORD` |
| bare, quoted and dotted keys | `VARIABLE` |
| `"basic"` and `"""multi-line basic"""` strings | `LITERAL_STRING_DOUBLE_QUOTE` |
| `'literal'` and `'''multi-line literal'''` strings | `LITERAL_CHAR` |
| integers, including `0x`, `0o`, `0b` and `_` separators | `LITERAL_NUMBER_DECIMAL_INT` or `LITERAL_NUMBER_HEXADECIMAL` |
| floats, exponents, `inf`, `nan` | `LITERAL_NUMBER_FLOAT` |
| `true`, `false` | `LITERAL_BOOLEAN` |
| offset and local date-times, dates, times | `DATA_TYPE` |
| `=`, `.` | `OPERATOR` |
| `[`, `]`, `{`, `}`, `,` | `SEPARATOR` |
| anything else on a value | `ERROR_IDENTIFIER` |

A line that ends inside a multi-line string ends with one of two private negative token types (basic or literal), so the next line resumes inside the string, as RSyntaxTextArea's own lexers do for block comments. `getLineCommentStartAndEnd` returns `#`, which makes Toggle Comment work.

### 3.3 Indentation, wrap and fonts

- **Indentation:** a tab inserts spaces (`setTabsEmulated(true)`) unless most indented lines of the file start with a tab, or the style is Makefile. The status line shows `Spaces: 4` or `Tab: 4`. Width is `tab_size`.
- **Soft wrap** starts from `soft_wrap` and is toggled per tab from the status line.
- **Font:** `font_family` and `font_size` (section 7) for the text and gutter. Changing either applies to open tabs.

### 3.4 Theme

- Token colours come from RSyntaxTextArea's bundled `idea.xml` when `context.appearance().variant()` is light and `dark.xml` when it is dark, loaded from RSyntaxTextArea's jar with `Theme.load`.
- The rest then comes from the running Jasper theme's `UIManager` colours, so the editor matches the rest of the window: background, foreground and caret from `TextArea.background`, `TextArea.foreground` and `TextArea.caretForeground`; selection from `TextArea.selectionBackground` and `TextArea.selectionForeground`; the gutter from `Panel.background` with line numbers in `Label.disabledForeground`; the current-line highlight is `TextArea.background` blended 6% toward `TextArea.foreground`.
- `appearance().onChanged` reapplies both to every open tab.

## 4. Reading and saving

### 4.1 Reading

The editor calls `document.read(limitBytes)` with `limitBytes = max_file_mib × 1 MiB`. Each implementation checks the file's size before transferring content and fails with `TooLargeException` when it is over the limit. Decoding runs on `context.background()`.

### 4.2 Decoding and encoding

`TextCodec` is pure; `DecodedText` holds the text, charset, BOM, line separator and whether endings were mixed.

- **BOM:** a UTF-8, UTF-16LE or UTF-16BE byte-order mark selects that charset, and the BOM is kept for saving.
- **Binary:** without a UTF-16 BOM, a NUL byte in the first 8 KiB means binary, and the file is refused.
- **Charset:** otherwise the bytes are decoded as strict UTF-8. When that fails they are decoded as ISO-8859-1, which maps every byte and so round-trips any file exactly; the status line then shows `ISO-8859-1`.
- **Line endings:** the most common separator (LF, CRLF or CR) is recorded and the text is normalised to `\n` for editing. Saving writes the recorded separator. A file with mixed endings shows `Mixed → LF` (or the majority separator) in the status line, and its first save converts every line to that separator.
- **Round trip:** opening a file and saving it without edits writes byte-identical content for UTF-8 with and without a BOM, UTF-16LE and BE with a BOM, and ISO-8859-1, whenever line endings are not mixed. This is a tested property.
- **Unencodable text:** when the text holds a character the tab's charset cannot encode, saving stops, names the first such line, and offers **Convert to UTF-8 and Save** or **Cancel**. Converting writes UTF-8 without a BOM.

### 4.3 Unsaved changes and saving

- **The ●** means the text differs from the last loaded or saved text: length and hash are compared shortly after typing stops. Undoing back to the saved text clears it.
- **Save,** editor side:
  1. Snapshot the text on the UI thread; encode it on the background executor.
  2. **Conflict check:** call `document.revision()` and compare it with the revision the tab last adopted.
     - Different: "nginx.conf changed on prod-web since you opened it." (or "…changed on disk…" for a local file) with **Overwrite**, **Reload** (discards the tab's edits) and **Cancel**.
     - Empty: "nginx.conf no longer exists on prod-web." with **Save Anyway** and **Cancel**.
  3. Call `document.write(bytes)`. The tab adopts the returned revision, and the snapshot becomes the saved text.
  4. **Failure:** a dialog names the file and the reason. The tab stays unsaved, and the next Save retries; a remote document reconnects if needed.
- **Revert to Saved** re-reads the document through `read`, replacing the text and the revision, after confirming when the tab has unsaved edits.

### 4.4 The write contract

Documented on `EditorDocument.write`; `LocalDocument` and Remote's `SafeWrite` both follow it.

1. **Resolve the real path.** Follow symbolic links from the document's path to the regular file they name, so a link stays a link and its target is what changes. More than 40 hops is an error.
2. **Safe write:** **Amended (Plan B)**
   1. create a new hidden sibling, `.<name>.jasper-<random>.tmp`, exclusively in the same folder: owner-only (`rw-------`) when it will replace an existing file, so the new content is never readable by others while it is written;
   2. write the bytes to it;
   3. give it the target's permission bits;
   4. atomically replace the target with it.

   Any failure removes the sibling and leaves the target untouched. A read-only target (one this user may not write) is refused with "permission denied", although its folder might allow the rename.
3. **In-place fallback:** write the bytes over the target from offset 0 and truncate it to their length when:
   - the sibling cannot be created (the folder is not writable though the file is);
   - the sibling's owner or group differs from the target's;
   - the target has more than one hard link (local only; SFTP reports no link count);
   - an atomic replacement is unavailable (an SFTP server without the `posix-rename@openssh.com` extension).

   Writing in place keeps the owner, group, hard links and extended attributes, but is not atomic: an interrupted write may leave a partial file. The tab stays unsaved, so the user can retry.
4. **A missing target** (after Save Anyway, or a new local file) is created: a safe write without a permission copy, replacing nothing.
5. **Return the new revision** from a stat of the target after writing.

Local specifics: the replacement is `Files.move(sibling, target, ATOMIC_MOVE, REPLACE_EXISTING)`; permissions come from `PosixFileAttributeView` where supported; owner, group and link count come from the `unix:uid`, `unix:gid` and `unix:nlink` attributes where the platform reports them, and their checks are skipped where it does not.

**Known limit:** a revision is modification time plus size. SFTP reports times to the second, so a change made within the same second that keeps the size is not noticed.

## 5. Watching local files

- While the Editor window is open, `LocalWatcher` stats every open local document every 2 seconds on the background executor, and hands changes to the UI thread. Polling was chosen over `WatchService` because the JDK's macOS `WatchService` polls anyway, and polling behaves the same on network mounts and under a fake clock in tests.
- A revision the tab has already adopted (its own save) is not a change.
- **Changed, no unsaved edits:** the tab reloads silently, keeping the caret's line and the scroll position, and the status line shows "Reloaded: changed on disk" for a few seconds.
- **Changed, with unsaved edits:** a banner says "nginx.conf changed on disk." with **Reload** and **Keep Mine**. Keep Mine adopts the new revision, so the next save overwrites without asking.
- **Deleted:** a banner says "nginx.conf was deleted." with **Close** and **Keep**. Keep leaves the tab unsaved; its next save recreates the file.
- Remote documents are not polled; they are checked when saved (section 4.3) and re-read by Revert.

## 6. Remote documents (Plan B)

### 6.1 `FileEntry` owner and group

Remote's internal `sftp.FileEntry` gains `uid` and `gid` (−1 when unknown), filled from `SftpClient.Attributes` for SFTP and from `unix:uid`/`unix:gid` for `LocalEndpoint`. They exist for `SafeWrite`'s owner check.

**Amended (Plan B):** `FileEndpoint` also gains:
- `create(path, permissions)`: an exclusive create whose mode applies as the file is created. Over SFTP this is a hand-built `SSH_FXP_OPEN` with a permissions attribute, because MINA's `open` sends none.
- `writable(path)`: `Files.isWritable` locally; over SFTP it opens the file for writing and closes it without writing.

### 6.2 `SafeWrite`

A new package `dev.jasper.remote.edit` holds `SafeWrite`, the write contract (section 4.4) over any `FileEndpoint`:
- it follows links itself, because `stat` does not follow them and `canonical` does not resolve the final path component;
- **Amended (Plan B):** a read-only target is refused (`writable`). The sibling is created with `create(sibling, 0600)`, or `0666` for a missing target so the server's umask decides. It is then filled, given the target's permissions with `metadata(sibling, -1, permissions)`, and published with `publish(sibling, target, replace = true)`, or `replace = false` for a missing target;
- `publish` reports a server without posix-rename as `AtomicMoveNotSupportedException`; `SafeWrite` then removes the sibling and falls back to writing in place.

Being endpoint-generic, it is tested on `LocalEndpoint` and on SFTP against the existing test server.

### 6.3 `RemoteDocument`

`RemoteDocument` implements `EditorDocument` for one file on one connection identity:
- **Key:** `remote:` plus the connection identity's stable text and the absolute path as browsed.
- **Name, location, host:** the file name; `user@host:/path` using the saved host's name; the saved host's display name.
- **Connection use:** Remote runs editor operations for one connection identity **one at a time**, on one SFTP channel opened through `EndpointFactory` for the first queued operation and closed when the queue empties. Each `EndpointFactory.open` creates a new SFTP channel, and OpenSSH allows 10 sessions per connection by default, so parallel per-operation channels beside the user's shells could exhaust it.
- **Reconnecting:** the lease reuses the live SSH connection; when it has closed, the lease reconnects. Prompts are owned by the terminal window the file was opened from while it is open, else by the active terminal window. With no terminal window open, the operation fails with "Reconnecting to prod-web needs an open terminal window for the sign-in prompt," because Remote's prompts are cancelled without an owner.
- **Reading:** resolve links, stat, fail with `TooLargeException` above the limit, then read the file.
- **Cancellation:** cancelling a read aborts the endpoint.

### 6.4 The SFTP panel

- **Double-click or Enter** on exactly one entry:
  - a folder navigates, as today;
  - a regular file opens in the Editor as a `RemoteDocument` (**Amended (Plan B):** the panel lists only remote hosts, so there is no local listing);
  - a link is resolved: a link to a folder navigates, a link to a regular file opens in the Editor.

  Without the Editor, a file does nothing and links behave as today.
- **Open in Editor,** a new right-click item, opens the selected regular files and links to files, at most 20 at a time. With more selected it opens the first 20 and says "Opened 20 of 57 files." It is hidden when the Editor is absent and disabled when the selection holds no file.
- Remote catches any `RuntimeException` from the Editor's API and shows it as a notice, because the host does not contain calls between plugins.

## 7. Settings

The Editor ships a commented `settings.toml`, which becomes `plugins/dev.jasper.editor/dev.jasper.editor.toml`. Changes apply live through `config().onChanged`. A value of the wrong type or out of range is reported with `config().report(key, message)`, and the default is used.

| Key | Default | Range | Meaning |
|---|---|---|---|
| `font_family` | first installed of JetBrains Mono, Menlo, Consolas, DejaVu Sans Mono, else `Monospaced` | an installed family | editor font |
| `font_size` | 13 | 8–48 | points |
| `tab_size` | 4 | 1–16 | columns |
| `soft_wrap` | false | | wrapping for new tabs |
| `max_file_mib` | 10 | 1–256 | files larger than this are refused |

## 8. Errors

| Situation | What the user sees |
|---|---|
| File larger than `max_file_mib` | In the tab: "nginx.conf is 42 MiB; the limit is 10 MiB (`max_file_mib`)." Retry, Close |
| Binary file | In the tab: "nginx.conf looks like a binary file." Close |
| Not found, permission denied | In the tab: the reason. Retry, Close |
| Connection or sign-in failure | In the tab: Remote's existing failure message. Retry, Close |
| Reconnect needs a prompt but no terminal window is open | Section 6.3's message, in the tab or the save dialog |
| Save fails | Dialog naming the file and the reason; the tab stays unsaved |
| Unencodable character | Section 4.2's choice |
| Bad setting | Reported under the plugin's settings; the default is used |
| Editor API throws inside Remote | A notice; the SFTP panel keeps working |

## 9. Package layout

Each package has a `package-info.java` naming its allowed dependencies.

**Editor** (`plugins/editor`):

| Package | Contents | May depend on |
|---|---|---|
| `dev.jasper.editor.api` (exported) | `Editor`, `EditorDocument`, `Content`, `Revision`, `TooLargeException` | JDK |
| `dev.jasper.editor` | `EditorPlugin` (wiring, actions, service, appearance), `EditorSettings` | all Editor packages, SDK |
| `dev.jasper.editor.text` | `TextCodec`, `DecodedText` (pure) | JDK |
| `dev.jasper.editor.syntax` | `SyntaxDetector`, `TomlTokenMaker`, `Languages` | RSyntaxTextArea |
| `dev.jasper.editor.local` | `LocalDocument`, `LocalWatcher` (injectable clock and scheduler) | `api` |
| `dev.jasper.editor.session` | `DocumentSession` (per-tab state machine without Swing: loading, ready, saving, failed; revision; saved hash; conflict decisions through callbacks), `EditorModel` (keyed de-duplication, order, names) | `api`, `text` |
| `dev.jasper.editor.ui` | `EditorWindow`, `EditorTab`, `FindBar`, `StatusLine`, `EditorTheme`, `Prompts` | all but `dev.jasper.editor`, SDK, RSyntaxTextArea |

**Remote:** `dev.jasper.remote.edit` holds `SafeWrite`, `RemoteDocument` and the per-identity operation queue; it may depend on `sftp`, `client` and `dev.jasper.editor.api`. `ui.sftp` gains the double-click, Enter and menu wiring.

## 10. Testing

Automated tests are headless and run in `./gradlew check`.

**Editor:**
- `TextCodec`: every round trip in section 4.2, binary refusal, the ISO-8859-1 fallback, CR, CRLF and mixed endings, unencodable characters.
- `SyntaxDetector`: every day-one name, extension and shebang, and the extension-over-shebang rule.
- `TomlTokenMaker`: token streams for every row of section 3.2, including multi-line strings carried across lines and the comment delimiters.
- `LocalDocument`, on a temporary folder: atomic replacement keeps permissions; a symlink stays a symlink and its target changes; an unwritable folder and a hard-linked file fall back to writing in place; a missing target is created; an oversized file is refused before it is read; the returned revision matches a fresh stat.
- `DocumentSession`, against a fake `EditorDocument`: load success, cancellation, failure and retry; the ● set by edits and cleared by undoing back to the saved text; each conflict answer; Save Anyway; typing during a save writes the snapshot; closing while saving waits.
- `LocalWatcher` with a fake clock: silent reload, the banner and each answer, deletion, own saves ignored.
- `EditorWindow` through `FakePluginHost`: key de-duplication, same-name disambiguation, ⌘W through `closeCommand`, the tab and window prompts, and the quit guard through `requestQuit`, including Save All succeeding, failing and being cancelled.
- `EditorPlugin`: the actions and their placements, the published service, settings validation and live changes, appearance changes.

**Remote:**
- `SafeWrite` on `LocalEndpoint` and on SFTP; the owner-mismatch and missing-posix-rename fallbacks against a fake endpoint.
- `RemoteDocument`: one operation at a time per identity on one channel, the size check before reading, cancellation aborting the endpoint, the no-terminal-window failure.
- The SFTP panel: files and links to files open in the Editor, folders still navigate, the 20-file cap, the menu item hidden without the Editor.

**SDK and application:**
- `PluginContractTest` cases for `onCloseCommand` and for the quit guards, run against the fake and the application.
- Headless `AuxiliarySurface` tests for the close command and quit consultation; a test of the quit path showing a guard's false cancels quit and restart before the deadline is armed.
- `chooseSaveFile`'s lifetime rules, as for `chooseFiles`.

**Build checks:** `verifyPluginArchitecture` (the Editor references only the JDK, the SDK and RSyntaxTextArea; Remote additionally `dev.jasper.editor.api`), `verifySdkArchitecture`, `PluginZipsTest` including the Editor's zip, and the source-hygiene check.

**GUI acceptance** is user-run, from a new `docs/editor-verification.md`:
- opening files with ⌘O, by drag and drop, and by double-click in the SFTP panel, remote and local;
- editing and saving a remote file, then checking it on the host with `cat`;
- a conflict made by editing the same remote file from the terminal before saving;
- ⌘W closing a tab rather than the window, and ⌘Q with unsaved edits;
- a light/dark theme switch;
- highlighting of TOML, JSON, Python, Bash and YAML samples;
- a local file changed by another program while open.

## 11. Documentation

- New: `docs/editor.md` (the user's guide) and `docs/editor-verification.md`.
- Updated:
  - `docs/sdk-architecture.md` and `docs/plugin-authoring.md`: SDK 0.8.2, quit guards, `onCloseCommand`, `chooseSaveFile`;
  - `docs/remote.md`: Open in Editor;
  - `docs/keyboard-shortcuts.md`, `docs/configuration.md` (a pointer to the Editor's settings), `docs/README.md`;
  - `docs/STATUS.md`;
  - `AGENTS.md`: Editor is a bundled plugin (`plugins/editor`, bundling RSyntaxTextArea), and Remote uses only `dev.jasper.editor.api`.

## 12. Delivery

One plan per runnable deliverable, each written with `superpowers:writing-plans`:
- **Plan A — SDK 0.8.2 and the Editor for local files:** section 1.4; the Editor plugin and its API (sections 1.1–1.2, 2–5, 7–9); its tests and documentation. Runnable on its own: local files open, edit and save.
- **Plan B — SFTP editing through Remote:** sections 1.3 and 6; Remote's tests; the Remote documentation and the remote parts of the verification checklist.
