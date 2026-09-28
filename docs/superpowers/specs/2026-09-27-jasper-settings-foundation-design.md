# Settings foundation: schema, key layout, writer and migration

Status: Design approved section by section on 2026-09-27; awaiting review of this written spec.
This is sub-project **A** of the Settings dialog work. Sub-projects B (dialog and core pages)
and C (SDK settings API and plugin rework) get their own brainstorm, spec and plan; their
already-settled decisions are recorded in [Roadmap](#roadmap-b-and-c) so they are not reopened.

## Outcome

Jasper gets an IntelliJ-style Settings dialog, pluggable through the SDK. Before any UI, the
configuration needs a foundation the dialog can stand on:

1. One declarative **schema** describing every core setting (key, type, default, constraint,
   label, description, keywords, when it applies), replacing the five hand-synchronised copies
   of the schema today (`ConfigLoader`'s `FIELDS` map and `readField` switch, `ConfigSnapshot`'s
   checks, `ConfigTemplate`, `config.example.toml`, `docs/configuration.md`).
2. A corrected **key layout** (`[font]` becomes `[terminal.font]`, and so on) with a config
   `version`.
3. A general, comment-preserving **TOML writer** and one save transaction, so settings other
   than keybindings can be written from inside the app.
4. An opt-in **migration** that rewrites old keys to the new layout, with a backup.
5. Plugin configuration diagnostics attributed to the plugin's own file.

A is testable and mergeable alone: it adds no dialog and needs no GUI launch.

## Current state (2026-09-27, `main` at 8883f670)

- `config.toml` is parsed with tomlj 1.1.1 (read-only library) by `ConfigLoader`: a `FIELDS`
  table map plus a 40-case `readField` switch, producing the immutable `ConfigSnapshot` record,
  whose constructors repeat the range checks. A 1-second mtime/size poll in `ConfigService`
  republishes to the EDT through `ConfigurationController`; `WorkspaceConfiguration` applies
  field-by-field changes.
- The only in-app writer of `config.toml` is the shortcut editor: `ConfigService.saveKeybindings`
  with the `KeybindingText` patcher (comment-preserving, optimistic, re-parsed, atomic).
- `config.toml` has no version and no migration framework. Legacy keys (`palette.scopes`,
  `[plugins."<id>"]`) only produce warnings.
- Plugins read `plugins/<id>/<id>.toml` through the read-only SDK `PluginConfig`. A plugin's
  `report(key, message)` becomes a `ConfigDiagnostic` naming `config.toml` at line 0, cleared only
  when `config.toml` reloads.
- `Cmd+,` opens `config.toml` in an external editor. There is no Settings dialog and no macOS
  preferences handler.

## Alternatives considered

1. **Schema drives the loader** (selected). A typed descriptor registry; the loader becomes a
   generic walker; `ConfigSnapshot.from(values)` keeps every consumer unchanged.
2. Keep the hand-written loader and add a parallel metadata registry with a parity test.
   Least churn, but two sources of truth, and C would need a second validation engine for
   plugin declarations.
3. Replace `ConfigSnapshot` with generic `values.get(Setting)` lookups everywhere. Purest, but
   touches every consumer and discards the useful grouped records for no dialog benefit.

For plugins (sub-project C) the user chose **declarative settings first, with a custom-page
escape hatch**, so the core app uses the same declarative model plugins will.

## Key layout (version 2)

```toml
version = 2

[ui.theme]            name
[ui.font]             family, size

[window]              tab_height, rail_width, toolbar, toolbar_height,
                      status_bar, status_bar_height, columns, lines

[terminal]            scrollback, option_as_meta, dim_inactive_panes, copy_on_select,
                      bell, on_exit, shell_integration, colors
[terminal.font]       family, size, fallback, ligatures, line_height
[terminal.cursor]     shape, blink
[terminal.shell]      program, args
[terminal.env]        NAME = "value"

[palette]             max_results
[notifications]       long_command_seconds
[buddy]               enabled
[system]              run_in_background

[keybindings]         unchanged
```

Moved keys (aliases):

| Version 1 key | Version 2 key |
|---|---|
| `font.family` | `terminal.font.family` |
| `font.size` | `terminal.font.size` |
| `font.fallback` | `terminal.font.fallback` |
| `font.ligatures` | `terminal.font.ligatures` |
| `font.line_height` | `terminal.font.line_height` |
| `ui.theme.terminal` | `terminal.colors` |
| `background.enabled` | `system.run_in_background` |

Every other key keeps its path, type, default, range and applies-when behaviour. `window.columns`
and `window.lines` stay under `[window]` (the size a new window opens at). The TOML layout is
independent of the dialog's page tree; B maps settings to pages.

Legacy tables kept as warnings only: `palette.scopes` and `plugins."<id>"`.

## Design

### 1. Schema model

New package `dev.jasper.app.settings`: generic machinery only, depending on the JDK and tomlj,
no other Jasper package.

- `Setting<T>`:

  ```java
  public record Setting<T>(List<String> key,            // ["terminal", "font", "size"]
                           SettingKind<T> kind,
                           T defaultValue,
                           List<List<String>> aliases,   // [["font", "size"]]
                           String label,
                           String description,
                           List<String> keywords,
                           Applies applies) { }
  public enum Applies { LIVE, NEW_PANES, NEW_WINDOWS, RESTART }
  ```

  Page assignment and editor hints (font picker, directory chooser) are **not** part of A; B adds
  them without changing the loader.

- `SettingKind<T>`: a sealed interface. Each kind parses a tomlj value, checks it, and formats
  a TOML literal for the writer. Kinds reproduce today's behaviour and message wording exactly:

  | Kind | Used by | Wrong value (right TOML type) |
  |---|---|---|
  | `bool` | `status_bar`, `ligatures`, `blink`, … | — |
  | `integer(min, max, CLAMP or DEFAULT)` | heights and rail width (CLAMP: warning, nearest bound); columns, lines, scrollback, seconds, max results (DEFAULT: error, default) | as today |
  | `number(min, max)` | font sizes, `line_height`, `dim_inactive_panes` | error, default |
  | `string(check, message)` | font families, shell program, theme name | error, default |
  | `choice(token → value, ordered)` | toolbar, bell, cursor shape, `option_as_meta`, `on_exit`, `shell_integration`, colors | error; message lists the tokens |
  | `stringList(element check)` | font fallback, shell args | any bad element: whole list default |
  | `stringMap(name check, reserved names)` | `terminal.env` | bad entry omitted; reserved names (`TERM`, `COLORTERM`) warned and omitted |

  A value of the wrong TOML type still rejects the whole file (the last good snapshot is kept).
  A whole-number `number` formats as `16`, not `16.0`, matching the example file.

- `SettingsSchema`: an ordered, immutable registry with lookup by key or alias, plus a set of
  **reserved tables** read by their own readers (`keybindings`, `plugins`, `palette.scopes`) and
  the root `version` key. Building a schema rejects duplicate keys, an alias equal to any key, a
  setting whose key lies inside another setting's key, and a setting inside a reserved table.

- `SettingValues`: the immutable load result. For each setting it holds the effective value and
  whether it was **present** in the file (and under which key, current or alias). B needs
  presence for "modified" markers and reset-to-default (reset means removing the key).

Core declarations live in `dev.jasper.app.config.CoreSettings` (constants such as
`TERMINAL_FONT_SIZE`, and the core `SCHEMA`). `config` depends on `settings`, never the reverse.
`ConfigSnapshot.from(SettingValues, keybindings, plugins)` builds the existing record;
`ConfigSnapshot`'s components, legacy constructors, builder and validations are unchanged, and so
are all consumers (`WorkspaceConfiguration`, `LaunchSettings`, `ConfigurationController`, …).
Existing range constants (`ConfigSnapshot.MIN_RAIL_WIDTH`, `PaletteSettings`, …) are referenced
by the declarations rather than duplicated.

### 2. Loader, aliases and version

`ConfigLoader.parse(Path, String, boolean)` keeps its signature. `Result` gains
`SettingValues values()` and `List<LegacyKey> legacyKeys()` (`LegacyKey(List<String> from,
List<String> to)`, in file order).

- Known tables are derived from the schema: every prefix of a setting key or alias, plus reserved
  tables. `FIELDS` is deleted.
- For each key the walk finds:
  - **Current key**: read through its kind.
  - **Alias**: read into the current setting; warning at the alias's position:
    `Moved to terminal.font.size. Use Migrate Config to update the file.`
    The `LegacyKey` is recorded.
  - **Alias and current key both present**: the current key wins; the alias gets
    `Superseded by terminal.font.size; ignored.` and is recorded as a `LegacyKey`.
  - **`stringMap` setting** (`terminal.env`): its key names a table that the kind reads whole,
    with `readEnvironment`'s current rules; the walker does not descend into it.
  - **Reserved table**: its existing reader, unchanged (`readBindings`, `readPlugins`, the
    `palette.scopes` warning).
  - **Anything else**: `Unknown setting or table; ignored.`
- `version`:

  | File | Treated as |
  |---|---|
  | absent | 1 |
  | `version = 2` | layout 2; aliases still accepted, with the same warnings |
  | integer > 2 | warning `Written by a newer Jasper; unknown settings are ignored.`; not rejected |
  | integer < 1 | error, treated as 1 |
  | not an integer | type error: file rejected, like every other key |

- `ConfigTemplate` (the file `Cmd+,` creates) is **generated from the schema**: `version = 2`,
  current keys only, each setting's `description` as its comment, a one-paragraph introduction
  per table kept beside the schema, and the commented `[keybindings]` and `[terminal.env]`
  examples it has today. Both platform variants still parse to `ConfigSnapshot.defaults()` with no
  diagnostics.
- `config.example.toml` stays hand-edited and moves to layout 2; `ConfigTemplateTest` computes
  the expected key sets and defaults from the schema instead of literal lists.
- A new test checks that every schema key appears in `docs/configuration.md`'s key table.
- Message wording and severities for everything else are unchanged; existing `ConfigLoaderTest`
  cases pass after mechanical key renames.

### 3. Writer and save transaction

#### `TomlDocument` (in `settings`; pure text in, text out)

- `TomlDocument.parse(String text)` requires valid TOML. It splits statements with
  `KeybindingText`'s scanner (moved here: multiline strings and arrays, trailing comments) and
  indexes every assignment by **full key path** (enclosing header + dotted key) with its
  statement, value and line spans; table headers; and inline tables. `[[array-of-tables]]`
  blocks are indexed as off-limits: an edit inside one is refused.
- Operations return a new document; `text()` returns the result.
  - `set(path, literal)`: replace the value in place when the key exists (rewriting an inline
    table when the key lives inside one). Otherwise insert, in this order of preference:
    1. after the last assignment of the key's own `[table]` header, if that header exists;
    2. beside the sibling assignment sharing the longest key prefix, as a dotted key in the same
       style (under the same header, or at the root);
    3. as a new `[table]` section appended at the end of the file.
    A root key (`version`) is inserted after the last root assignment, before the first header.
  - `remove(path)`: delete the statement and its same-line comment. Tables left empty keep
    their header (except in migration, below).
  - `move(from, to)`: carry the value's **source text** verbatim (so `16.0` stays `16.0`) and its
    same-line comment. Comment lines above the old key stay in place.
  - `renameTable(from, to)`: rewrite a `[from]` header to `[to]` in place.
  - `removeTableIfEmpty(path)`: remove a header with no remaining assignments; comment lines stay.
- **Safety net**: `TomlDocument.verify(before, after, intended)` parses the result and compares
  values: every intended path must hold its intended value (or be absent), and **every other
  value must deep-equal the original**. Otherwise the edit is refused with
  `Can't safely edit <key> here; edit config.toml by hand.` An edit may fail; it cannot corrupt.
- Line endings follow the file (CRLF kept). Literals come from the setting's kind; a
  `stringMap` writes one key per entry (`terminal.env.NAME`).
- `KeybindingText` becomes a thin caller of `TomlDocument`. Where the general insertion position
  differs from bytes pinned by `KeybindingSaveTest`, the expectation is updated case by case and
  recorded as a deviation in the plan's status banner.

#### `ConfigService.save(SettingsEdit)`

```java
public record SettingsEdit(Map<Setting<?>, Optional<?>> changes,    // empty: reset (remove the key)
                           Map<Setting<?>, Optional<?>> expected) { } // caller's last-seen presence and value
public CompletableFuture<State> save(SettingsEdit edit)
```

On the config worker (the only writer):

1. Read the file; when missing, start from the generated template.
2. Refuse only when the file is **rejected** (syntax or type errors). Value errors in untouched
   keys do not block (more permissive than today's shortcut save).
3. Conflict check per touched setting: on-disk presence and value must equal `expected`; if not,
   refresh and fail with `<key> changed on disk. Review the new value and try again.`
4. Validate each new value through its kind; failures use the same messages a bad file value
   produces.
5. For each touched setting present under an **alias**, remove the alias and write the current
   key, so a saved file never holds both.
6. Apply with `TomlDocument`, `verify`, re-parse and confirm the touched settings' values; enforce
   the 1 MiB cap; TOCTOU check (original text and symlink target unchanged); write with
   `TomlStateFile.writeAtomically`; `refresh(true, true)`; return the published `State`.

`saveKeybindings` keeps its collision and default-availability rules but runs through the same
internal pipeline (steps 1, 6).

Writing plugin settings files is **not** in A. `TomlDocument` is file-agnostic; C adds the plugin
write path, deliberately reversing the plugin-home plan's "the application never rewrites an
existing settings file".

### 4. Migration

Triggers (nothing runs automatically):

- Action `config.migrate`, "Migrate Config", in the palette; enabled only while the last load
  reported legacy keys or removable dead tables.
- A **Migrate…** button in the configuration diagnostics dialog (`WindowContent`'s
  `showConfigDiagnostics`) when legacy keys exist.
- B's Settings dialog will offer it on open (not in A).

`ConfigService.migrate(Predicate<String> pluginSettingsExist)` on the config worker:

1. Read; if rejected: `Fix config.toml's errors before migrating.` If there are no legacy keys and
   no dead tables to clean: return `Nothing to migrate` (no write).
2. Plan:
   - **Table rename**: when every assignment in an old table maps into the same new table and
     that new table does not exist, rename the header in place (`[font]` → `[terminal.font]`);
     comments and blank lines inside the section travel with it.
   - Otherwise **per key**: only the alias present → `move`; both present → `remove` the alias.
   - Headers left empty by the migration are removed (comment lines stay).
   - Dead tables: remove `palette.scopes`; remove `plugins."<id>"` only when
     `pluginSettingsExist.test(id)` (its `plugins/<id>/<id>.toml` exists), otherwise keep it and
     its warning so an uninstalled plugin's seed survives. The application supplies the
     predicate, so `config` does not depend on `plugins`.
   - Set `version = 2`.
3. `verify` with the intended values = the old file's values re-keyed to their new paths, minus
   the removed tables; everything else unchanged.
4. Back up the **original bytes** to `config.toml.v1.bak` beside the real file (the symlink
   target when the config is a symlink); never overwrite an existing backup: use
   `config.toml.v1.2.bak`, `.v1.3.bak`, …. Then the TOCTOU check and atomic write.
5. `refresh(true, true)`; the caller shows a notice:
   `Migrated 7 settings to the new layout. Backup: config.toml.v1.bak.`

Properties: idempotent; works on the active file including `--config`; a version 1 file keeps
working indefinitely through the aliases (removing aliases is a separate, unplanned decision).

### 5. Plugin diagnostics and stale text

- `PluginConfig.report(key, message)` produces a `ConfigDiagnostic` naming
  `plugins/<id>/<id>.toml`, with the key's line and column when known. `PluginSettingsFiles`
  keeps key positions from its last good parse.
- `ConfigurationController` stores plugin reports per plugin id. `PluginRuntime.applySettings`
  clears a plugin's reports before re-running that plugin's `onChanged` listeners, so fixing a
  value clears its warning. App config reloads still clear app diagnostics as today.
- No SDK change in A.
- Stale text fixed: the `palette.scopes.history` warning in `ConfigLoader` (should point to
  `plugins/dev.jasper.history/dev.jasper.history.toml`); `docs/plugin-authoring.md:451`
  (`[plugins."<id>"]`); `docs/app-architecture.md`'s `ThemeState` description (it holds only a
  temporary terminal-colours override).
- Docs updated for A: `docs/configuration.md` (key table, version and migration section, moved-key
  warnings), `README.md`'s configuration sample, `docs/app-maintenance.md`'s "add a config field"
  recipe (declare a `Setting` in `CoreSettings`, add the snapshot component and its `from`
  mapping, a docs row, an example line), `docs/app-architecture.md` (the `settings` package),
  `docs/STATUS.md`.

## Package and architecture rules

- `dev.jasper.app.settings/package-info.java`: pure values and text editing; no Jasper
  dependencies; caller chooses thread.
- `config` adds `dev.jasper.app.settings` to its allowed outgoing dependencies.
- `verifyApplicationArchitecture` must pass (acyclic package graph). No SDK or plugin changes.
- Source hygiene rules in `AGENTS.md` apply (no raw control or private-use characters).

## Testing

TDD throughout; no GUI launch is needed anywhere in A.

- **Kinds**: parse, format, exact messages and severities per kind; schema construction
  rejections.
- **`TomlDocument`**: table-driven cases for headers, root dotted keys, inline tables, quoted
  keys, CRLF, multiline strings and arrays, refused array-of-tables, every insertion rule,
  `remove`, `move`, `renameTable`, `removeTableIfEmpty`; `verify` refusals. A **seeded property
  test** over a corpus (`config.example.toml`, both templates, fixtures) applies random set/remove
  sequences and asserts the result parses to exactly the intended values with every other value
  unchanged.
- **`ConfigLoader`**: existing tests after key renames; aliases; alias plus current key; `version`
  cases; `legacyKeys`; example, template and docs parity with the schema.
- **`ConfigService`**: `save` (conflict, missing file, alias cleanup, invalid value, reset,
  TOCTOU, rejected file); `migrate` (golden v1 → v2 bytes; backup naming and no overwrite;
  plugin predicate; idempotence; rejected file; symlinked config); `saveKeybindings` regression.
- **Plugin diagnostics**: file and position attribution; clearing on the plugin's own reload.
- **Gate**: `./gradlew check` green, counts read from `*/build/test-results/test/` and
  `plugins/*/build/test-results/test/`.

## Non-goals (A)

- Any Settings dialog UI, page tree, editor hints, macOS preferences handler (B).
- Persisting today's session-only view choices (toolbar mode toggle, size dialogs, terminal
  colours override, per-pane zoom) (B decides).
- SDK changes, plugin settings writes, plugin rework, Remote's `[shortcuts]` removal (C).
- Renaming `ConfigSnapshot` components (`backgroundEnabled`, `font`, `terminalColors`).
- Removing aliases.

## Roadmap: B and C

Settled decisions, recorded so they are not reopened:

**B: Settings dialog and core pages.** IntelliJ-style modal dialog: searchable category tree,
breadcrumb header, highlighted search matches, modified markers, per-setting reset, Apply / OK /
Cancel. Opened by `Cmd+,` (replacing "open config.toml", which moves to a link in the dialog) and a
macOS `Desktop.setPreferencesHandler`. Pages generated from the schema (Appearance, Terminal,
Window, System, …) plus custom pages for Keymap (the existing shortcut editor) and Plugins (the
plugin manager). B adds page assignment and editor hints to `Setting`, offers migration on open,
and decides which session-only view choices become saved settings. Saves go through
`ConfigService.save`.

**C: SDK settings API and plugin rework.** SDK 0.9, package `dev.jasper.sdk.settings`:
declarative setting declarations (the same kinds), translated into `dev.jasper.app.settings` types
inside `dev.jasper.app.plugins`, so SDK types stay confined there. A plugin settings write path on
`TomlDocument` (reversing the plugin-home "never rewrite" invariant). A custom-page escape hatch
modelled on IntelliJ's `Configurable` (create component, is-modified, apply, reset, dispose).
Testkit fakes and contract tests in `PluginContractTest`. Editor, History, Remote, Vault and Sample
move to declared settings; Remote's `[shortcuts]` table is removed in favour of the keymap; the
stale `PluginConfig` Javadoc is fixed.

## Workflow

Branch `claude/settings-foundation` in `.worktrees/settings-foundation`, from local `main` at
8883f670. Plan via `superpowers:writing-plans` (TDD, one commit per task), executed per
`AGENTS.md`. Deviations go in the plan's status banner and `docs/STATUS.md`. Merge to `main` only
with the user's agreement; do not push without asking.
