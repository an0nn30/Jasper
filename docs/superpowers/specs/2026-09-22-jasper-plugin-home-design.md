# Jasper Plugin Home — Design

**Status:** Approved 2026-09-22 (user decisions recorded in section 2). Amends the plugin SDK design
(`2026-09-21-jasper-plugin-sdk-design.md`, "Plugin directories" and "Configuration") and the
Plugins-manager plan 3b.

**Scope:** One directory, `<Jasper home>/plugins/`, holds everything about plugins: each plugin's
jars, its own settings file and its private data, side by side under `plugins/<id>/`; a zip dropped
there is installed at the next launch; the application creates the per-plugin folder and settings
file for every plugin it knows. The SDK's `PluginConfig` is backed by that file and can name it.
The Plugins manager gains a real uninstall and a context menu that opens the settings file, the
plugin folder and the data folder. `plugin-data/` disappears.

**Not in scope:** Hot reload of jars; a settings *editor* in the manager (the file is opened in the
user's editor, as Jasper's own settings are); per-plugin key bindings; changing `plugins.toml`
(state) or `plugins.lock`, which stay beside `config.toml` as files, not folders.

---

## 1. Why

Today an installed plugin's jars live in `plugins/<id>/`, its private data in `plugin-data/<id>/`,
and its settings in a `[plugins."<id>"]` table of Jasper's `config.toml`. Three places for one
plugin, two of them invisible from the manager, and the settings table competes with the user's own
configuration for the same file. The manager can disable and remove, but "remove" leaves the data
behind and offers nothing for bundled plugins, and there is no way to get from a plugin to its files.

## 2. Decisions

1. **One top-level folder.** Everything about plugins is under `plugins/`; `plugin-data/` goes.
2. **Per-plugin settings file, next to the plugin**: `plugins/<id>/<id>.toml`, autocreated by the
   application, the only source of a plugin's settings. The old `[plugins."<id>"]` table in
   `config.toml` seeds it once and is reported as moved from then on.
3. **A plugin may ship an example settings file** that the application uses to seed the real one:
   a `settings.toml` resource beside `plugin.toml` in the plugin's jar.
4. **Zips dropped into `plugins/` are installed at the next launch**, the same way the manager's
   Install from Zip lands; consent is still asked for in the manager before the plugin runs.
5. **Development launches are already isolated** by `jasper.home` (`jasper-app/build/dev-home`),
   so a plugin run from the IDE gets its folder, settings and data there and never touches the
   installed Jasper's `plugins/`.
6. **Uninstall means the whole folder**: jars, settings and data, at the next launch, after a
   confirmation that says so. Bundled plugins stay disable-only; their jars are in the application
   image, not in `plugins/`.

## 3. Layout

```
<Jasper home>/                       ~/.config/jasper, or jasper.home / JASPER_HOME
  config.toml                        the user's configuration (no plugin tables any more)
  plugins.toml, plugins.lock         plugin state: enabled, consented capabilities, pending removal
  plugins/
    dev.example.tool/                one folder per plugin the application knows about
      jars/                          an installed plugin's jars (absent for a bundled plugin)
      dev.example.tool.toml          its settings, autocreated; what PluginConfig reads
      data/                          PluginContext.dataDirectory()
    dev.jasper.history/              a bundled plugin: settings and data only
      dev.jasper.history.toml
      data/
    dev.example.other-1.2.0.zip      a drop-in install, consumed at the next launch
    .pending/<id>/                   installs staged by the manager, applied at the next launch
    .staging-*/, .trash-*/           the installer's and remover's transient directories
```

`plugins/<id>/` exists for every plugin discovered at launch, from any origin: bundled (jars in the
image), installed (jars in `jars/`) or development (`--plugin-dir`, jars where they were built).
The folder is created, the settings file seeded and `data/` made before the plugin starts, so a
plugin's `start()` sees a settings file and a data directory that exist.

## 4. Discovery and maintenance

`PluginDiscovery.single(directory)` reads jars from `directory/jars/` when that directory exists,
otherwise from `directory` itself. Installed plugins always use `jars/`; bundled directories in the
image and `--plugin-dir` directories keep the flat shape the build produces (`stagePlugin`,
`stagePlugins`). Only a plugin's jars are ever loaded from a directory; the settings file, `data/`
and a stray zip are not jars and are ignored by discovery.

`PluginMaintenance.apply`, at launch, before anything loads and inside the state lock, in this
order:

1. **Sweep** abandoned staging and trash directories (unchanged).
2. **Migrate the old layout** once: every `plugins/<id>/*.jar` moves into `plugins/<id>/jars/`;
   every `<home>/plugin-data/<id>/` moves to `plugins/<id>/data/`; an empty `plugin-data/` is then
   removed. A jar that cannot be moved (open by a running process) leaves the migration for the next
   launch, as installs do today.
3. **Consume drop-in zips**: each `plugins/*.zip` is validated with the installer's rules (jars at
   the root or in one folder, one `plugin.toml`, an SDK range that includes this Jasper) and unpacked
   into `.pending/<id>/`; the zip is deleted. An invalid zip is renamed `<name>.rejected` beside a
   log line, so the next launch does not retry it and the user can see what happened.
4. **Pending removals**: `plugins/<id>/` is retired whole (jars, settings, data), as `retire` does
   today for the jars directory. The state entry goes with it.
5. **Pending installs**: `.pending/<id>/` becomes `plugins/<id>/jars/`, replacing only the jars;
   settings and data survive an update.
6. **Ensure folders** for every plugin the launch will consider (bundled, installed, development):
   `plugins/<id>/`, the seeded settings file and `data/`.

## 5. Settings

### 5.1 The file

`plugins/<id>/<id>.toml` is plain TOML with the same value kinds `PluginConfig` reads: strings,
integers, booleans, string lists and nested tables. It is seeded once, when missing, in this order:

1. If `config.toml` still has a non-empty `[plugins."<id>"]` table, the file is written from that
   table (a header comment, then the table's keys as TOML), so nobody loses settings.
2. Else, if the plugin's jar has a `settings.toml` resource beside `plugin.toml`, that text is the
   file (the plugin's own example, comments included). The bundled History plugin ships one with
   `trivial_commands` and `deprioritize_trivial` commented; the sample ships its `demo_*` flags.
3. Else a two-line header: `# Settings for <name> <version>. Jasper reads this file live.` and the
   plugin id.

The application never rewrites an existing settings file.

### 5.2 Reading and reloading

`plugins.PluginSettingsFiles` (new) owns every plugin's settings: it reads a file into a
`Map<String, Object>` (the loader's existing `freeze` of a `TomlTable`, made shareable), keeps a
per-file fingerprint (size and modification time), polls once a second on the runtime's admin
worker while plugins run, and on a change delivers the new map to `PluginSettings.update` on the
UI thread, which is what fires `PluginConfig.onChanged`. Reload Config also re-reads every file.
A file that fails to parse keeps the last good values and is reported through the configuration
report path under the key `plugins.<id>` with the file's path, so it appears in the Configuration
status like a `config.toml` problem; the report clears when the file parses again.

`PluginRuntime.start` no longer takes plugin tables and `configurationChanged` no longer carries
them: the runtime reads settings from files. `ConfigSnapshot.plugins` stays, for seeding only.

### 5.3 SDK

`PluginConfig` gains one method:

```java
/** The settings file this configuration is read from; a plugin may open it or point the user at it. */
Path file();
```

Everything else (`string`, `integer`, `bool`, `stringList`, `table`, `onChanged`, `report`) is
unchanged in shape and meaning; `report` still names a key relative to the plugin's own table. The
testkit's `FakePluginConfig.file()` is `<data root>/<id>/<id>.toml`, and `FakePluginHost`'s data
directories mirror the application's layout, `<data root>/<id>/data`, so a plugin that derives
paths from `dataDirectory()` (the Snippets migration) behaves the same under both. `JasperSdk.VERSION`
becomes `0.7.0` (a new method on an implemented interface); the bundled plugins' ranges become
`>=0.7, <0.8`.

### 5.4 Transition

`ConfigLoader` keeps parsing `[plugins."<id>"]` tables into `ConfigSnapshot.plugins` and reports
each one as a warning: "Plugin settings moved to plugins/<id>/<id>.toml; this table only seeds that
file when it is missing." The seeding happens in maintenance step 6, which receives the snapshot's
tables. Removing the table from `config.toml` clears the warning; leaving it does no harm.

## 6. The Plugins manager

`PluginRuntime.Row` gains `Path directory` (`plugins/<id>/`), `Path settingsFile` and
`Path dataDirectory`. The list gets a context menu (right-click, and the keyboard's context-menu
key) with, in order: the row's own actions as today (Enable/Disable, Review…, Remove…/Keep,
Discard Install), a separator, **Open Settings**, **Open Plugin Folder**, **Open Data Folder**.
The three openers go through the application's `ConfigEditor` (open in editor for the file, reveal
for the folders) on the manager's worker, with failures shown as the window's error notice; a missing
data folder is created first, a missing settings file is seeded first (the same seeding as at
launch), so the actions never fail for a plugin the manager lists.

**Remove…** asks first: "Remove <name>? Its jars, settings and data in plugins/<id>/ are deleted
the next time Jasper starts. Keep undoes this until then." The details pane of a bundled plugin says
"Bundled with Jasper; disable it here, or remove it from the application image." A development
plugin (`--plugin-dir`) is not removable either; its folder in `plugins/` holds only settings and
data, which Open Plugin Folder reveals.

## 7. Removal of `plugin-data`

`AppDirs.pluginData()` goes; `PluginRuntime.Options` loses `dataRoot`, and the host's data-directory
function becomes `plugins/<id>/data`. The Snippets plugin's legacy-file lookup, which derived the
home from its data directory, becomes three levels up (`data` → `<id>` → `plugins` → home) and is
documented as such. `README.md`, `jasper-app/README.md`, `docs/configuration.md`,
`docs/plugin-authoring.md` and `docs/sdk-architecture.md` describe the new layout, the drop-in
install, the settings file, the example resource and the uninstall.

## 8. Plan

One plan, `2026-09-22-jasper-plugin-home-plan.md`, in this order so the build stays green per task:
(1) layout: discovery of `jars/`, maintenance migration, drop-in zips, whole-folder removal,
`Options` without `dataRoot`, `AppDirs`; (2) settings files: seeding, `PluginSettingsFiles`,
runtime wiring, loader warning, SDK `file()`, testkit, bundled plugins' `settings.toml`, SDK 0.7.0;
(3) manager: rows, context menu, openers, confirmation; (4) documentation and verification.

## 9. Self-review record

- Placeholders: none.
- Consistency: the `jars/` rule in section 4 is what section 3's tree shows and what step 5's
  install produces; the seeding order of 5.1 is what step 6 and the manager's Open Settings both
  run; `Row.directory/settingsFile/dataDirectory` in section 6 are the three paths of section 3.
- Scope: one plan of four tasks; the SDK change is a single method.
- Ambiguity resolved: a bundled plugin's folder holds settings and data only; a development
  plugin's likewise; installed plugins own `jars/`. Zips are consumed, never kept.
