# Jasper Palette Plugins — Design

**Status:** Approved 2026-09-21 (user decisions recorded in section 2). Amends section 14 of
`2026-09-21-jasper-plugin-sdk-design.md`: palette scopes contributed by plugins are now in scope.

**Scope:** A supported palette-contribution surface in `jasper-sdk`, three small SDK additions the
first contributors need (a pane's shell label, a one-method notice API, open-in-editor), the host
adapter and testkit fake for all of them, and the move of the History and Snippets palette scopes
out of `jasper-app` into two bundled plugins that depend on each other through `Services`. The
Commands scope stays in the application.

**Not in scope:** Plugin-contributed *verbs* on another scope's rows; scopes that render their own
components; scrolling result lists; a general notification centre; a settings-page extension point;
moving `command-history.toml` or the Commands scope.

---

## 1. Why

The palette has had a scope seam since the terminal refactor: `dev.jasper.app.palette.PaletteScope`
is a small EDT interface (identity, verbs, `search`, `available`, `step`, `execute`, `onChanged`)
behind which the Commands, History and Snippets scopes already sit as peers. The SDK spec deferred
exposing it "until a plugin needs it". Two plugins now do, and moving them out of the application
also removes the History-scope leakage the generic palette carries today (`PaletteContext.trivialCommands`,
`PaletteController`'s `HistorySettings` import, an unused `PaletteTarget.shellName`, three hard-coded
scope ids in `WorkspaceActions` and `PaletteKeyRouter`).

The two plugins are also the first real exercise of `Services`, `requires` with `optional = true`,
`exports` and load ordering before the Credential Vault and SSH plugins depend on exactly that.

## 2. Decisions

1. **Commands stays in core.** It is the palette's default scope and the place every plugin's actions
   land; a Commands plugin would need a read/run surface over window-owned Swing actions that
   nothing else wants. Its code is tidied where it lives (section 7).
2. **Two plugins, not one.** `dev.jasper.snippets` publishes a service; `dev.jasper.history`
   consumes it optionally for "Save as snippet…". The dependency is deliberately the Vault/SSH shape.
3. **Snippets move to the plugin's data directory** with a one-time migration from
   `<AppDirs>/snippets.toml`; documentation is updated to the new path.
4. Both are **bundled plugins** (`<app image>/lib/plugins/<id>/`), loaded without consent, disabled
   through the Plugins manager. The History shortcut (Cmd+R / Ctrl+Shift+R) and Snippets shortcut
   (Cmd+J / Ctrl+Shift+J) become the plugins' actions with those default bindings.

## 3. SDK: `dev.jasper.sdk.palette`

New package; `PluginContext.palette()` returns `Palette`. New capability
`Capabilities.PALETTE_CONTRIBUTE = "palette.contribute"`. Everything runs on the UI thread and does
no I/O, the same contract the application's own scopes follow; the host contains every call.

### 3.1 `Palette`

```java
public interface Palette {
    /** Registers an app-wide scope; the application shows it in every window. Needs palette.contribute. */
    Subscription register(PaletteScope scope);
    /**
     * Opens the palette in {@code window} on {@code scopeId}, or dismisses it when that scope is
     * already showing (the toggle every scope shortcut has). {@code query} replaces the query text,
     * {@code rowId} selects a row of the first result list. Unknown scopes are ignored.
     */
    void open(WindowHandle window, String scopeId, Optional<String> query, Optional<String> rowId);
}
```

`open` may target any registered scope, including `jasper.commands` and another plugin's: opening
the palette is not sensitive, and the History → Snippets hop needs it. It still needs
`palette.contribute`, so a plugin that has nothing to do with the palette cannot pop it up.

### 3.2 `PaletteScope`

Implemented by the plugins (two real implementations: History and Snippets).

```java
public interface PaletteScope {
    ScopeSpec spec();
    PaletteResults search(String query, PaletteQuery context);
    default boolean available(PaletteRow row, PaletteVerb verb, PaletteQuery context) { return row.enabled(); }
    default Optional<PaletteStep> step(PaletteRow row, PaletteVerb verb, PaletteQuery context) { return Optional.empty(); }
    void execute(PaletteRow row, PaletteVerb verb, PaletteQuery context);
    default void activated(PaletteQuery context) { }
    Subscription onChanged(Runnable listener);
}
```

Semantics are the application's, restated for plugin authors:

- `search` returns at most `context.maxResults()` rows; the palette is a hard-capped list. The
  empty query is the "recent" or "all" list and may carry a section label.
- `available` is rechecked immediately before execution; `false` refreshes the list instead.
- A present `step` is shown as a small form instead of running the verb; its completion does the
  work and answers `done()`, `error(message)` (keeps the form open) or `reopen(scopeId, rowId, query)`.
- `execute` runs the verb; verbs are bound in order to Enter, Cmd/Ctrl+Enter and Shift+Enter.
- `onChanged` tells the palette to re-run the open query; a scope may refresh its index in
  `activated` and publish through the listener when the index changes.
- `PaletteRow.token` is scope-private; the host never reads it and hands it back unchanged.

### 3.3 Values

```java
public record ScopeSpec(String id, String label, String description, String placeholder, List<String> aliases,
                        List<PaletteVerb> verbs, boolean monospaceRows, Optional<Icon> icon,
                        Optional<String> shortcutActionId)
```

- `id` is namespaced like an action id and must start with the plugin's id plus a dot
  (`dev.jasper.history.shell`); the host rejects anything else, so no plugin can shadow
  `jasper.commands`. 1 to 3 verbs, distinct ids. `aliases` are the picker's `>alias` shortcuts.
- `shortcutActionId`, when present, names one of the plugin's own actions. While the palette is
  open, that action's shortcut switches to (or dismisses) this scope exactly as the built-in
  Cmd+P/R/J do today instead of being swallowed; while it is closed the action's handler runs,
  which is where the plugin calls `Palette.open`. Without it a scope is reachable through the
  picker and `open` only.

```java
public record PaletteVerb(String id, String label)
public record PaletteRow(String id, String title, Optional<String> detail, Optional<String> tag, Optional<Icon> icon,
                         boolean enabled, Object token)           // token may be null
public record PaletteResults(List<PaletteRow> rows, Optional<String> sectionLabel, Optional<String> initialSelectionId)
public record PaletteStep(String title, List<Field> fields, BiConsumer<Map<String, String>, Consumer<Result>> complete) {
    public record Field(String name, String label, String prefill) { }
    public record Result(Optional<String> error, Optional<String> reopenScopeId, Optional<String> reopenRowId, Optional<String> reopenQuery) {
        public static Result done(); public static Result error(String message);
        public static Result reopen(String scopeId, Optional<String> rowId, Optional<String> query);
    }
}
public record PaletteQuery(WindowHandle window, Optional<PaneHandle> target, int maxResults, boolean macOs)
```

Validation mirrors the application's records (row id ≤ 256 characters, non-blank titles, ≤ 200
rows, at least one step field). `PaletteQuery.target` is the pane the palette was opened from; its
handle is bound to the contributing plugin's gate, so pasting into it needs `terminal.inject` as it
does anywhere else.

### 3.4 Three small additions

- **`PaneInfo.shell`** (`String`, the launcher label such as `zsh` or a provided session's title):
  the History plugin tags live entries with it, as `TerminalPane.shellLabel` does today. It is the
  eleventh component; `TerminalFixture`, `FakePluginHost.addTerminalPane` and the contract gain it.
- **`Notices`** at `PluginContext.notices()`: `void error(String message)`, shown as the active
  window's transient error notice, the one the application uses for its own failures. One method is
  what Snippets needs; a general notification centre stays out of scope.
- **`Platform`** at `PluginContext.platform()`: `void openInEditor(Path file)`, backed by the
  application's `ConfigEditor` (configured editor, `$VISUAL`/`$EDITOR`, then the desktop). Failures
  are reported through `Notices`, not thrown.

`JasperSdk.VERSION` becomes `0.6.0` (a new package, a new `PaneInfo` component and two new context
facades). The sample plugin's range becomes `>=0.6, <0.7`.

## 4. Application host

- `contributions.Contributions` gains `Kind.SCOPES`, `Subscription addScope(PaletteScope scope)`
  (the *application's* `PaletteScope`, one instance shared by every window, as the model's other
  entries are), `List<PaletteScope> scopes()`, and a `PaletteRequest(windowId, scopeId, query, rowId)`
  listener beside `PanelRequest`. The package's allowed dependencies gain `dev.jasper.app.palette`
  (which depends only on `commands`, `config` and `lifecycle`; no cycle).
- `workspace.WindowContributions` registers every contributed scope in its window's `ScopeRegistry`
  on connect and on `Kind.SCOPES`, removes the ones that left, and routes `PaletteRequest`s for its
  window to `WindowCommandPalette.open(scopeId, query, rowId)`.
- `palette.PaletteContext` gains `UUID windowId`; `palette.PaletteTarget` gains
  `Optional<UUID> paneId`. `PaletteController.open` accepts an optional query and row and applies
  them the way a step's `reopen` result already does. `PaletteKeyRouter` resolves a stroke bound to
  a contributed action to the registered scope whose `shortcutActionId` names it, through a
  `ScopeRegistry.byShortcutAction(String)` lookup, and treats it like a built-in scope shortcut.
  The app `PaletteScope` gains `default Optional<String> shortcutActionId()`.
- `plugins.HostedPalette` (new) adapts an SDK scope to an app scope: contained calls; `PaletteQuery`
  built from the context's window and pane ids through `HostedTerminals.windowHandle`/`paneHandle`;
  SDK values converted both ways with the token passed through; `spec()` read once at registration.
  It enforces the namespace, the capability (`gate.require(PALETTE_CONTRIBUTE)` with one audit
  line), the UI thread and an open context, and closes all registrations at teardown, like `HostedUi`.
  `open` becomes a `PaletteRequest` on the model.
- `HostedContext` adds `palette()`, `notices()` (routed to the application's error-notice callback,
  already reachable as the palette's `reportError`) and `platform()` (the app's `ConfigEditor`).
- `PluginRuntime`'s environment record grows the two callbacks the above need; the runtime's
  existing `Contributions` instance carries the scopes.

## 5. Testkit and contract

- `FakePalette` behind `FakePluginContext.palette()`: `register` checks the namespace and
  capability; `FakePluginHost` gains `List<String> scopes()`, `PaletteResults search(scopeId, query, windowId, paneIdOrNull)`,
  `Optional<PaletteStep> step(scopeId, rowId, verbId, …)`, `run(scopeId, rowId, verbId, …)`,
  `List<String> paletteOpens()` (as `windowId scopeId query rowId` lines), `List<String> notices()`
  and `List<Path> openedInEditor()`. `addTerminalPane` gains the shell label.
- Contract cases (both harnesses): registering needs `palette.contribute` and the plugin's
  namespace; a duplicate id fails and each registration removes only itself; a stopped plugin's
  scopes disappear; `search`, `available`, `step` and `execute` receive the same token they returned;
  a step's `reopen` names the scope, row and query to reopen with; `open` requests carry the
  window, scope, query and row (the showing-scope toggle is the window's and is tested there,
  because neither harness drives a real window); a `shortcutActionId` must be the plugin's own action; `notices().error` reaches the
  host; `PaneInfo.shell` round-trips; an optional `requires` resolves with the dependency present and
  absent (the History → Snippets shape).

## 6. The plugins

Both live under `plugins/`, compile against the SDK only, ship through `stagePlugins`, and keep
their existing scope, parser, store and file tests, moved into their modules.

### 6.1 `dev.jasper.snippets` (`plugins/snippets`)

- `plugin.toml`: capabilities `palette.contribute`, `terminal.inject`; `exports = ["dev.jasper.snippets.api"]`.
- Carries `Snippet`, `SnippetFile` (TOML read and atomic append; the SDK has no TOML writer) and a
  store over `context.background()` with `dataDirectory()/snippets.toml`. Reloads on
  `AppEvents.CONFIG_RELOADED` and refreshes on `activated`.
- Scope `dev.jasper.snippets.scope`, label "Snippets", aliases `snip`, `snippets`; verbs Paste,
  Paste and run, Edit file; placeholder fill-in as a `PaletteStep` with remembered values; Edit
  file through `platform().openInEditor`; file errors as a disabled first row plus `notices().error`.
- Action `dev.jasper.snippets.open` ("Snippets…", default binding `meta J` on macOS, `ctrl shift J`
  elsewhere) whose handler calls `palette.open`; the scope names it as `shortcutActionId`.
- Publishes `dev.jasper.snippets.api.SnippetService`:
  `Optional<SnippetView> byName(String name)`; `void append(String name, String command, BiConsumer<Optional<SnippetView>, Optional<String>> done)`
  (saved view or error message, on the UI thread); `String rowId(String name)` so a consumer can
  reopen on the saved row.
- **Migration:** at start, if `<AppDirs>/snippets.toml` exists and the plugin's file does not, move
  it (`Files.move`, same volume) and log at INFO; if both exist, leave both and log a WARNING naming
  the two paths. The application passes the old path as `snippets.legacy_file` in the plugin's
  default configuration, so the plugin has no knowledge of `AppDirs`.

### 6.2 `dev.jasper.history` (`plugins/history`)

- `plugin.toml`: capabilities `palette.contribute`, `terminal.observe`, `terminal.inject`;
  `requires = [{ id = "dev.jasper.snippets", version = ">=0.1", optional = true }]`.
- Carries the five parsers, `ShellHistoryIndex` (worker over `context.background()`, snapshots on
  the UI thread, the one-second `javax.swing.Timer` poll while a listener exists) and discovery from
  the home directory and environment. Never writes history files.
- Live capture: subscribes `TerminalEvents.COMMAND_FINISHED`, building an entry from the payload
  plus `terminals.pane(paneId).info().shell()`.
- Scope `dev.jasper.history.scope`, label "History", aliases `hist`, `shell`, monospace rows;
  verbs Paste, Paste and run and, only when `services.find(SnippetService.class)` is present at
  start, Save as snippet…, whose step appends through the service and answers
  `reopen("dev.jasper.snippets.scope", rowId, name)`.
- `trivial_commands` read from its own configuration table (`[plugins."dev.jasper.history"]`),
  default `["ls", "cd", "clear", "pwd", "exit"]` as today; disabling the plugin replaces
  `[palette.scopes.history] enabled = false`, which `ConfigLoader` reports as obsolete with the
  replacement.
- Action `dev.jasper.history.open` ("Shell history…", `meta R` / `ctrl shift R`), named as
  `shortcutActionId`.

## 7. What leaves the application and what is tidied

Removed in plan 5b: `palette.builtin.ShellHistoryScope`, `palette.builtin.SnippetsScope`, the
`snippets` package, `history.{ShellHistoryIndex, ShellHistoryParser, ShellHistorySnapshot, ShellHistorySource, ShellHistoryEntry, HistoryShell}`,
`config.HistorySettings`, `ActionId.HISTORY_PALETTE` and `SNIPPETS_PALETTE` with their
`WorkspaceActions`, `WindowContent` and `PaletteKeyRouter` cases, the `shellHistory`/`snippets`
constructor arms of `JasperApplication`, `TerminalWindow` and `WindowContent`,
`TerminalPane.onCommandExecuted`, and `AppDirs.snippets()` except as the migration source.

Moved: `CommandsScope` into `dev.jasper.app.palette` and the `builtin` package deleted;
`CommandHistory` and `CommandHistoryFile` into `dev.jasper.app.commands` and the `history` package
deleted; `shortcutText` beside `Command`. `PaletteContext` loses `trivialCommands`, `PaletteTarget`
loses `shellName`, `PaletteController` loses `HistorySettings`. Behaviour of the Commands scope is
unchanged.

## 8. Plans

- **Plan 5a — palette contribution surface.** Sections 3, 4 and 5: SDK package and additions, host
  adapter, key routing, testkit and contract, documentation. The application's own History and
  Snippets scopes stay in place and untouched; a test proves a contributed scope and a built-in
  scope coexist in one window. Ships a working product with a new, unused-by-shipping-plugins API,
  which the sample plugin exercises behind a `demo_scope` setting.
- **Plan 5b — the two plugins.** Sections 6 and 7 and their documentation: `docs/command-palette.md`,
  `docs/configuration.md`, `docs/plugin-authoring.md` (palette scopes and an optional service
  dependency, with the two bundled plugins as the worked example), `docs/sdk-architecture.md`,
  `AGENTS.md`, `verifyPluginArchitecture` and `stagePlugins`.

## 9. Self-review record

- Placeholders: none; every type named in sections 3 to 6 has its shape stated.
- Consistency: `ScopeSpec.shortcutActionId` (3.3) is what `PaletteKeyRouter` resolves (4) and what
  both plugins set (6); `PaletteStep.Result.reopen` (3.3) is what the History step returns (6.2) and
  what the contract checks (5); `PaneInfo.shell` (3.4) is what live capture reads (6.2).
- Scope: two plans, each leaving a working product; the second cannot start before the first.
- Ambiguity resolved: `open` may target any scope; scopes are app-wide, one instance per plugin
  shared by every window; the migration is the plugin's, fed the legacy path through configuration.
