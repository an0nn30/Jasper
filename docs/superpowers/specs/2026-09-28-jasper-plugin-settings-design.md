# Plugin settings in the SDK and the Settings dialog (sub-project C)

Status: approved design, 2026-09-28. Sub-project C of the settings effort. Sub-project A (the settings
foundation, `docs/superpowers/specs/2026-09-27-jasper-settings-foundation-design.md`) and sub-project B (the
Settings dialog, `docs/superpowers/specs/2026-09-28-jasper-settings-dialog-design.md`) are merged into local
`main`. A's "Roadmap: B and C" section recorded C's settled decisions; this spec does not reopen them.

## Outcome

Plugins declare their settings in code through SDK 0.9's `dev.jasper.sdk.settings` and read them through typed,
validated handles. Each running plugin with settings gets a page under **Tools** in the Settings dialog: generated
from its declarations, or built by the plugin itself (custom pages are equals, with standard rows and a highlight
hook). Settings and plugins write a plugin's `plugins/<id>/<id>.toml` through the same comment-preserving
transaction that writes `config.toml`. The raw `PluginConfig` getters are removed, and Editor, History, Remote,
Vault and Sample are reworked onto declarations; Remote's `[shortcuts]` table gives way to the Keymap.

## Decisions (user, 2026-09-28)

1. **Declarations live in code**, registered during `start()`, returning typed handles. The app validates values
   and reports problems; plugins drop their own checks. A plugin that is not running gets a stub page.
2. **Declare-only.** SDK 0.9 removes `PluginConfig` and `PluginContext.config()`. Every bundled plugin is reworked.
3. **Undeclared keys** in a plugin's file get the core "unknown setting" warning. No migration; Remote's stale
   `[shortcuts]` lines warn until the user deletes them.
4. **Approach: translate, with a draft per plugin.** SDK declarations are translated into the app's `Setting`
   types inside `dev.jasper.app.plugins`; each plugin page edits its own `SettingsDraft` and writes its own file.
5. **Custom pages are equals.** A plugin may supply its own page component instead of the generated page; the SDK
   gives it the standard editor rows, a way to stage its own values, keywords, and a highlight hook.
6. **Delivery:** one spec, three plans (C1 back end, C2 pages, C3 rework and the 0.9 break), executed inline.

Carried from A's roadmap: SDK types stay inside `dev.jasper.app.plugins`; plugin pages sit under Tools by default;
the custom page is modelled on IntelliJ's `Configurable`; testkit fakes and contract tests cover the API; the stale
`PluginConfig` Javadoc goes (with `PluginConfig` itself).

## Alternatives considered

- **Declarations in `plugin.toml`**: pages even for disabled plugins without running plugin code, but reads stay
  keyed by strings and rules that need code (an installed font) cannot be expressed. Rejected.
- **Declarations in code plus a cached schema per plugin**: real pages for disabled plugins, at the cost of a cache
  that can go stale. Rejected; the stub page and Open in Editor cover the case.
- **Keeping (or deprecating) the raw getters** alongside declarations. Rejected by the user in favour of one way to
  read settings.
- **Retired-key declarations with migration of Remote's shortcuts** into `[keybindings]`. Rejected; warnings only.
- **One combined draft across `config.toml` and every plugin file.** More change to B's tested core and awkward
  partial failure across files. Rejected.
- **Plugin-built pages only.** Every plugin would re-implement validation, Reset and search. Rejected in favour of
  generated pages with custom pages as equals.

## Design

### 1. SDK surface (`dev.jasper.sdk.settings`, SDK 0.9.0)

`PluginContext.settings()` returns `PluginSettings`; `PluginContext.config()` and `dev.jasper.sdk.plugin.PluginConfig`
are removed.

```java
public interface PluginSettings {
    <T> Setting<T> declare(SettingSpec<T> spec);   // only during start(); later calls throw IllegalStateException
    void page(SettingsPanel panel);                // optional: a custom page instead of the generated one
    Path file();                                   // plugins/<id>/<id>.toml under Jasper's home
    Subscription onChanged(Runnable handler);      // UI thread, after any declared value changes
}

public interface Setting<T> {
    String key();                                  // the dotted key
    T get();                                       // the valid saved value, or the default
    CompletableFuture<Void> set(T value);          // writes the file; completes on any thread
    Subscription onChanged(Consumer<T> handler);   // UI thread, when this value changes
}
```

**`SettingSpec<T>`**: `SettingSpec.of(dottedKey, kind, defaultValue)` then `label`, `description`, `keywords`,
`section` (a heading on the generated page), `applies` (`Applies.LIVE` or `Applies.RESTART`) and `editor` (a hint).
A dotted key (`sftp.max_parallel_files`) is a table in the file. Keys follow the core key rules; duplicates and a
key that is also a table are rejected when declared.

**`SettingKind<T>`** (the core kinds without the string map):
- `bool()`; `integer(min, max)` (`Integer`); `number(min, max)` (`Double`);
- `text()` and `text(Predicate<String> valid, String invalidMessage)`;
- `choice(List<Choice<T>> choices)`, where `Choice.of(token, displayName, value)`;
- `stringList()` and `stringList(Predicate<String> validElement)`.

**Editor hints** (`EditorHint`): `font(boolean monospaced)`, `directory()`, `file()`. Without a hint the editor
follows from the kind.

**Custom pages:**

```java
public interface SettingsPanel {
    JComponent create(SettingsPanelHost host);
    default List<String> keywords() { return List.of(); }
    default void highlight(String query) { }
    default boolean modified() { return false; }   // state beyond declared settings
    default void apply() { }
    default void reset() { }
    default void dispose() { }
}

public interface SettingsPanelHost {
    JComponent row(Setting<?> setting);            // the standard label, control, Reset and validation row
    <T> T pending(Setting<T> setting);             // the page's unsaved value
    <T> void stage(Setting<T> setting, T value);   // for the plugin's own controls
    void changed();                                // modified or validity changed
}
```

Standard rows and `stage` edit the page's draft of declared settings, which Settings saves on Apply; a custom page
implements `modified`, `apply` and `reset` only for state of its own. Standard rows highlight themselves; the hook
covers the plugin's own controls. All `SettingsPanel` methods run on the EDT.

**Validation shared with the testkit**: a small SDK class checks a value against its kind (range, choice, predicate,
list elements) and returns the message, so the app and the fake report the same way. The app's `SettingKind`
messages stay authoritative for text shown in Settings.

`JasperSdk.VERSION` becomes `0.9.0` (plan C3).

### 2. App side

All SDK types stay inside `dev.jasper.app.plugins`.

- **Translation.** `declare` turns a `SettingSpec` into an app `Setting` in the plugin's `SettingsSchema`. Kinds map
  one to one; `number` converts `Double` to the core's `Float`. A `text(valid, …)` predicate is plugin code: the app
  calls it under the plugin's containment, and a throw counts as invalid. The schema is complete when `start()`
  returns.
- **Reading.** The schema-driven reader now inside `ConfigLoader` moves into `dev.jasper.app.settings` as a reader
  that takes any `SettingsSchema` and returns `SettingValues`, diagnostics with positions, and unknown keys.
  `ConfigLoader` uses it for `config.toml`; the plugin settings store uses it for `<id>.toml`. An invalid value falls
  back to its default with a WARNING naming the file, line and column; an undeclared key gets "Unknown setting or
  table; ignored." Values read before `start()` returns are checked as they are declared; unknown keys are reported
  once the schema is complete. The 1-second poll and Reload Config stay. A handle's `onChanged` fires when its value
  changes; `PluginSettings.onChanged` fires on any change. Reports stay per plugin and clear on that plugin's reload.
- **Writing.** The plugin settings store gains `save(pluginId, SettingsEdit)`, the same transaction as
  `ConfigService.save`: re-read, compare the caller's expected values (a mismatch fails with "<key> changed on disk"),
  edit through `TomlDocument` (comments kept), re-parse and verify, write atomically, then publish. A file that does
  not parse refuses writes. `Setting.set(v)` is a one-setting edit expecting the current saved value. This reverses
  the plugin-home rule that Jasper never rewrites a plugin's settings file.
- **Pages.** `app.plugins` builds `settingsui` pages for each running plugin and hands them to the application
  through `contributions` (as palette scopes travel today), so `settingsui` and the application never see SDK types.
  `CorePages.pages` takes the plugin pages and parents them under `tools`, titled with the plugin's name; Tools shows
  only when it has a child.
- **Generated page**: a `SchemaPage` over the plugin's own `SettingsDraft`, rows in declaration order grouped by
  `section`. A rejected file shows a message at the top of the page, and the page cannot apply.
- **Custom page**: an `app.plugins` page that wraps the `SettingsPanel` and calls every method under containment; a
  throwing `create` shows an error on the page. `SettingsPanelHost.row` returns `settingsui` editor rows bound to the
  plugin's draft. Search indexes the declared labels, descriptions and keywords plus `keywords()`.
- **Apply order**: the core draft first, then each modified page in tree order, stopping at the first failure (B's
  existing rule); a plugin page's failure names the plugin.
- **Stub page**: an installed, enabled plugin that is not running (needs review, failed to start, safe mode) gets
  "Tools › <name>" with "Start <name> to edit its settings." and Open in Editor.
- **Plugins page**: the per-plugin "Open Settings" item opens that plugin's page in Settings (the stub page when it
  is not running) instead of the file.

### 3. Plugin rework

Every bundled plugin requires `sdk = ">=0.9.0, <0.10"` and bumps its minor version. Own validation and `report`
calls go. The shipped `settings.toml` files stay as commented documentation, lose undeclared keys, and say when
changes apply.

- **Editor** (generated page): `font_family` (text, monospaced font editor, valid when installed; default resolved
  at start as the first installed of JetBrains Mono, Menlo, Consolas, DejaVu Sans Mono, else Monospaced),
  `font_size` 8–48 (13), `tab_size` 1–16 (4), `soft_wrap` (false; described as applying to new tabs),
  `max_file_mib` 1–256 (10), `vim` (false). Toggle Vim Mode and the Vim status item call `vim.set(on)`;
  `SettingsFile` is removed. The "could not be saved" dialog and its session-only fallback stay.
- **History** (generated page): `trivial_commands` (string list of single words; default exit, clear, ls, ll, la,
  cd, pwd, c, q, logout) and `deprioritize_trivial` (true). Fix: an empty list means no trivial commands.
- **Remote** (generated page; sections Connection and SFTP): `connect_timeout_seconds` (10), `auth_timeout_seconds`
  (30), `keepalive_seconds` (30; 0 disables), `session_linger_seconds` (5), all whole seconds from 0 to 3600
  (0 keeps today's meaning); `read_user_known_hosts` (true), `use_ssh_agent` (true); `sftp.max_parallel_files` 1–8 (2),
  `sftp.request_timeout_seconds` 1–300 (30). `[shortcuts]` is removed: Toggle SSH Hosts (Cmd+Shift+S), Connect to
  SSH Host… (Cmd+Shift+H) and Toggle SFTP (none) keep those as their actions' default bindings, rebindable in
  Keymap. `RemoteShortcuts`, `configureShortcuts` and the example's uncommented `[shortcuts]` go.
- **Vault** (generated page): `auto_lock_minutes` 0–1440 (15; 0 never locks), `keys_directory` (text, directory
  editor; empty means the data folder's `keys`), `bind_new_vaults_to_device` (true).
- **Sample** (custom page, the authoring guide's compiled example): `demo_activity`, `demo_ui`, `demo_terminal`,
  `demo_session`, `demo_scope` (false; applies after restart) and `demo_step_millis` 0–5000 (300). Its page uses
  `host.row` for the settings, one control of its own staged through `host.stage`, keywords and the highlight hook.
- **Snippets** declares nothing and gets no page.

### 4. Testkit and architecture

- **Testkit**: `FakePluginHost` replaces `setConfig(id, Map)` with a fake settings store per plugin:
  `setSetting(id, key, rawValue)` (as if the file changed; runs listeners), `declared(id)`, `saved(id)` (what `set`
  wrote), and `reports()` including validation warnings, using the SDK's shared validator. `panel(id)` builds a
  custom page over a fake `SettingsPanelHost` that records `stage` and `changed` and returns labelled placeholder
  rows.
- **Contract**: `PluginContractTest` gains settings cases run against the app host and the fake: declaring after
  start throws; an invalid file value gives the default and one report; `set` round-trips and fires the handle's
  listener; listeners run on the UI thread; an unknown key reports.
- **Architecture**: `verifySdkArchitecture` keeps `dev.jasper.sdk.settings` JDK-only, with Swing only in
  `SettingsPanel` and `SettingsPanelHost`. `verifyApplicationArchitecture`: SDK types only in `dev.jasper.app.plugins`;
  `app.plugins` may depend on `settings` and `settingsui`; `contributions` carries app-native page factories and
  depends on `settingsui`; `settingsui` depends on no plugin package. `verifyPluginArchitecture` is unchanged.

## Testing

TDD, headless, no GUI launched by an agent.

- **SDK**: spec builder rejections (duplicate key, key and table clash, bad range, blank label); each kind's
  validator.
- **Translation and reading**: every kind and hint; invalid values fall back with a positioned warning; unknown
  keys; a throwing predicate is contained; declarations and reads before `start()` returns; `ConfigLoader` behaviour
  unchanged after the reader moves (existing tests).
- **Writing**: golden files for comments, tables and dotted keys; "changed on disk"; rejected files; `set` from a
  plugin; atomic replacement.
- **Pages**: generated sections, search, Reset and the rejected-file message; custom rows, `stage`, the highlight
  hook and a contained throwing `create`; the stub page; Tools only with children; Apply order across files; the
  Plugins page's "Open Settings" navigation.
- **Plugins**: each plugin's settings tests move to the fake host (defaults, ranges, live application, Editor's
  `vim.set`, History's empty list, Remote's default bindings without `[shortcuts]`).
- **Preview**: `settingsPreview` adds Tools › Editor (generated) and Tools › Sample (custom), dark and light, default
  UI font and 18 pt.
- **Gate**: `./gradlew check` green; counts from `*/build/test-results/test/` and
  `plugins/*/build/test-results/test/`.
- **GUI checklist for the user**: edit a plugin setting and Apply; a plugin setting with a bad value in the file; the
  Editor's Vim toggle; Sample's custom page; Open Settings from the Plugins page; a plugin in safe mode (stub page).

## Delivery

One spec, three plans, each mergeable alone with `check` green:

- **C1: SDK settings API and app back end.** `dev.jasper.sdk.settings` and `PluginContext.settings()`; translation;
  the reader moved into `dev.jasper.app.settings`; per-plugin reading, validation, unknown keys, polling and
  listeners; `save` and `set`; testkit fakes and contract cases. `PluginConfig` stays until C3 so the bundled
  plugins still compile; the SDK version stays 0.8.x (the API only grows).
- **C2: Plugin pages in Settings.** Generated, custom and stub pages; Tools placement and contributions wiring;
  Apply across files; Plugins page navigation; Sample as the custom-page example; previews.
- **C3: Plugin rework and the 0.9 break.** Editor, History, Remote and Vault on declarations; Remote's `[shortcuts]`
  removed; `PluginConfig` and `PluginContext.config()` removed; `JasperSdk.VERSION` 0.9.0 and bundled plugin ranges
  `>=0.9.0, <0.10`; docs (`plugin-authoring.md`, `sdk-architecture.md`, the SDK README, `configuration.md`'s Plugins
  section, Remote's docs) and STATUS.

## Non-goals

- Plugin pages outside Tools.
- Editing settings of a plugin that is not running (the stub page offers the file).
- Generating a plugin's commented template from its declarations; the shipped `settings.toml` stays.
- Migrating Remote's old shortcut values; retired-key declarations.
- Settings import, export or sync.

## Workflow

Branch `claude/plugin-settings` in `.worktrees/plugin-settings`, from local `main` at ef745526. Plans via
`superpowers:writing-plans` (TDD, one commit per task), C1 first, executed inline with checkpoints. Deviations go in
each plan's status banner and `docs/STATUS.md`. Merge only with the user's agreement; do not push without asking.
