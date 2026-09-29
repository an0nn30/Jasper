# Recent files in the command palette

Status: approved design, 2026-09-29. The Editor remembers the files it opened, local and remote, and offers them in a
new "Recent files" palette scope that reopens them. Remote makes its SFTP documents reopenable through a new hook in
the Editor API. Built on the Editor (`plugins/editor`, API `dev.jasper.editor.api`), Remote's SFTP editing
(`dev.jasper.remote.edit`) and the palette SDK (`dev.jasper.sdk.palette`), all on local `main`.

## Outcome

Opening a file in the Editor, whether from disk, from the SFTP panel or from any other plugin that provides documents,
adds it to a recent-files list that survives restarts. The command palette's "Recent files" scope searches that list by
file name, host and location. Enter reopens the file, selecting its tab when it is already open; a remote file
reconnects and signs in exactly as when it is opened from the SFTP panel. Entries not opened within a configurable
number of days (default 30) are forgotten, and the list never holds more than 50 entries.

## Decisions (user, 2026-09-29)

1. **One list for local and SFTP files**, owned by the Editor, not an SFTP-only list in Remote.
2. **Approach A:** the Editor records and stores entries and owns the scope; plugins that provide documents register a
   *reopener* for their key prefix. Remote registers `remote:`.
3. **Defaults:** at most 50 entries, newest first, a re-opened file moves to the top; rows show name, host tag and
   location; verbs Open and Remove from recent files; rows that cannot be reopened stay visible, dimmed.
4. **Retention policy:** a setting, `recent_files_days`, forgets entries not opened for that many days. Default 30;
   0 keeps no recent files.

## Alternatives considered

- **SFTP-only list in Remote.** No Editor API change, but local files would be missing. Rejected by decision 1.
- **Self-describing documents** (`EditorDocument.reopenToken()` resolved through another service): the same outcome
  as A with one more indirection. Rejected.
- **"Document opened" events, one list per plugin:** two scopes instead of one. Rejected.
- **Count limit only** (JetBrains' "Recent files limit", 50; VS Code and Sublime Text also cap by count): the list
  stays bounded but keeps years-old entries. Rejected by decision 4; the count cap of 50 stays as a ceiling beneath the
  age policy.

## Design

### 1. Recording and storage (Editor, new package `dev.jasper.editor.recent`)

`RecentFiles` holds the list in memory. UI thread only; no Swing.

- An entry is a record `RecentFile(String key, String name, String location, Optional<String> host, Instant opened)`,
  copied from the `EditorDocument` when recorded.
- `record(EditorDocument, Instant now)` removes any entry with the same key, adds the new one first, then prunes.
- `remove(String key)`, `entries(Instant now)` (newest first, expired entries excluded), `onChanged(Runnable)`.
- **Pruning** drops entries whose `opened` is older than `now - recent_files_days` and everything past the 50th. It
  runs on load, on every `record`, and when the setting changes. `entries(now)` filters by age as well, so a session
  left running for weeks never shows expired entries even before the next prune.
- **`recent_files_days = 0`** records nothing and empties the list (and the file) when the setting changes to 0.
- **Persistence:** `<home>/plugins/dev.jasper.editor/data/recent-files.toml`, an array of tables with `key`, `name`,
  `location`, optional `host`, and `opened` as an ISO-8601 instant. Every change schedules a write on the plugin's
  background executor with a snapshot of the list; writes are serialized and the last snapshot wins. The write goes to
  a sibling temporary file and is moved into place atomically. On load, a missing file is an empty list; an unreadable
  or malformed file is renamed to `recent-files.toml.bad` (replacing an older `.bad`) and the list starts empty;
  individually malformed entries are skipped.

**When an entry is recorded.** `EditorWindow.open(document, owner)` records the document once its first read
succeeds, so a failed open (too large, not found, sign-in cancelled, connection failure) is never listed. Opening a
document whose key already has a tab selects that tab and records it again, which moves it to the top. Untitled
buffers have no document and are never recorded. Local documents keep their existing key (`LocalDocument.key`,
`local:<absolute normalized path>`); other documents keep the key their provider gives.

### 2. The reopener hook (Editor API)

A new default method on `dev.jasper.editor.api.Editor`:

```java
/**
 * Makes recent files under a key prefix reopenable. The resolver runs on the UI thread and must do no I/O. It returns
 * empty when the key cannot be reopened now, for example because its host was deleted, and otherwise a factory that
 * builds the document for the window reopening it (null when there is none).
 *
 * @param keyPrefix the start of the keys this plugin's documents use, for example {@code "remote:"}
 * @param resolve resolves a recorded key
 * @return removes the reopener
 */
default Subscription reopener(String keyPrefix, Function<String, Optional<Function<WindowOwner, EditorDocument>>> resolve) {
    return () -> { };
}
```

- The Editor's implementation keeps the registrations in a map, prefix to resolver; a later registration of the same
  prefix replaces the earlier one, and removing it restores nothing. The longest matching prefix wins.
- The Editor resolves `local:` keys itself (to a `LocalDocument` for the path) without registering.
- A resolver that throws is treated as empty.
- The API version: Editor `0.3.1` becomes `0.4.0`. `Subscription` and `WindowOwner` are the SDK's (`Editor` already
  names `WindowOwner`), so the `api` package-info's "May depend on: the JDK only" is corrected to "the JDK and the
  SDK". No SDK change.

**Remote's reopener.** In `RemotePlugin`, inside the block that constructs `RemoteEditing` (so it exists only when the
Editor is installed), Remote calls `RemoteEditing.registerReopener()`, which registers `remote:` and returns the SDK
`Subscription` that `stop()` closes. An Editor older than 0.4 has no `reopener` method, so the call throws
`NoSuchMethodError`; Remote catches it and keeps no recent files for that Editor. `RemoteEditing` gains a constructor
parameter that finds a saved host by id in memory (`store.hosts()`).

- `RemoteDocument.parseKey(String key)` is the inverse of `RemoteDocument.key(identity, path)`: it returns the hop list
  (saved-host id and login per hop, target first) and the path, or empty when the key is malformed. The key format is
  `remote:<uuid>/<login>[,<uuid>/<login>…]:<path>`. UUIDs contain no `/`, `,` or `:`; the path is everything after the
  first `:` that follows the hop list, so paths with `:` and `,` survive; an empty login (a hop whose login was never resolved) parses as
  empty and is kept as recorded; a login containing `,`, `/` or `:` cannot be
  parsed and is treated as malformed (such logins are not valid SSH user names).
- The resolver looks each hop's host up in the store's in-memory `hosts()` by id. If any is missing, the result is
  empty. Otherwise it builds `ConnectionIdentity` from those hosts and the recorded logins (as `TransferCodec` does)
  and returns `owner -> new RemoteDocument(identity, path, channels, ownerSupplier(owner))`, where `ownerSupplier`
  uses the owner when it is an open `WindowHandle` and otherwise the active terminal window, as `RemoteEditing.owner`
  does now. `RemoteEditing` exposes a `reopen(key)` method holding this logic so it stays in `dev.jasper.remote.edit`,
  the only package allowed to name Editor types.
- The recorded route is used as recorded, even if the saved target host's jump host has since changed; that keeps the
  key, and so the tab identity, stable.

### 3. The palette scope (Editor, `RecentFilesScope` in `dev.jasper.editor.recent`)

- `ScopeSpec.of("dev.jasper.editor.recent", "Recent files", "Search recently opened files", List.of(OPEN, REMOVE))`,
  with a description, the Editor's file icon, `withInAll(true)`, `withInRecents(true)` and the shortcut action below.
- **Action** `dev.jasper.editor.recent-files`, "Recent Files…", keywords editor, recent, files, open; no default
  binding; it opens the palette on this scope.
- **Search:** the empty query lists entries newest first. A query matches case-insensitively as a substring of the
  name, the host or the location; rows whose name matches come before rows that match only the host or location, each
  group newest first. At most `context.maxResults()` rows.
- **Rows:** id = the SHA-256 of the key in hex (row ids are at most 256 characters; keys may be longer); title = name; tag = host (remote entries only); detail = location; token = the entry. A row whose
  key cannot be resolved (no reopener for its prefix, or the resolver returns empty) is shown with
  `withEnabled(false)` and the detail "Can't reopen: host or plugin unavailable · <location>".
- **Verbs:** `OPEN` ("Open", Enter) resolves the key again and calls the Editor window's `open(document,
  context.window())`. `REMOVE` ("Remove from recent files", the second verb, so Cmd/Ctrl+Enter) removes the entry. `available` allows `REMOVE` on every
  row and `OPEN` only when the key resolves now.
- **Local files** always resolve, because the scope may not touch the disk; a file that no longer exists fails on open
  with the Editor's usual "not found" notice, and its entry stays until removed.
- The scope fires `onChanged` when `RecentFiles` changes, when a reopener is registered or removed, and when the
  setting changes.

### 4. The setting

In `EditorSettings.Declared` and `plugins/editor/src/main/resources/settings.toml`:

```toml
# Forget recent files not opened for this many days, 0 to 365; 0 keeps no recent files.
# recent_files_days = 30
```

`SettingKind.integer(0, 365)`, default 30, shown in Settings › Tools › Editor with the label "Keep recent files for
(days)". A change applies at once: `RecentFiles` prunes and the scope refreshes.

## Architecture and boundaries

- `dev.jasper.editor.recent` may depend on `api`, `local` and the SDK; `ui` and `EditorPlugin` wire it. Its
  `package-info.java` records this, and `verifyPluginArchitecture` must stay green.
- Remote names Editor types only in `dev.jasper.remote.edit` (`RemoteDocument`, `RemoteEditing`), as today;
  `RemotePlugin` calls `RemoteEditing.reopen` through a signature that names no Editor type, so Remote still loads
  without the Editor.
- No SDK change. The Editor's manifest `version` becomes `0.4.0` and Remote's `0.6.2`; Remote's requirement on the
  Editor stays `>=0.2`, since it tolerates an older Editor (see Remote's reopener).

## Testing

- `RecentFilesTest`: newest first; duplicate key moves to top; the cap of 50; age pruning at the boundary; `days = 0`
  records nothing and empties; `entries(now)` hides expired entries without a prune; TOML round trip including hosts,
  paths with `:` and `,`, and non-ASCII names; missing file; malformed file renamed to `.bad`; malformed entry skipped;
  atomic write leaves the old file on failure.
- `RecentFilesScopeTest` with fake resolvers: empty-query order; name matches ranked first; case-insensitive match on
  host and location; row limit; unresolvable rows disabled with the detail; `available` for both verbs; Open calls the
  window's open with the palette window; Remove updates the list; `onChanged` on list, reopener and setting changes.
- `EditorWindow` test: recorded only after a successful first read; not recorded after a failed read; reopening an
  open key records again and selects the tab.
- Editor reopener registry: longest prefix wins; replacement; unsubscribe; a throwing resolver is empty.
- `RemoteDocumentKeyTest`: `parseKey(key(identity, path))` round-trips for one and several hops and for paths with
  `:`, `,`, spaces and non-ASCII; malformed keys are empty.
- `RemoteEditing.reopen` test: empty for a deleted target or jump host; builds a document with the recorded logins and
  the same key otherwise.
- GUI checks (user): open a local file and an SFTP file, restart, reopen both from Recent files; the SFTP reopen
  prompts for sign-in when needed; deleting the host dims the row; `recent_files_days = 0` empties the scope.

## Out of scope

- Pinning entries, clearing the whole list from the palette (setting 0 then back does it), a Recent Files submenu in
  the File menu, and restoring open tabs at start-up.
