# IntelliJ theme import

**Status:** The user approved the design in chat on 2026-09-25. This written specification awaits their review, and there is no plan yet. It builds on [the theme engine](2026-09-25-jasper-theme-engine-design.md) (part 2a) and [the Search Everywhere palette](2026-09-25-jasper-search-everywhere-design.md) (part 2b).

## Purpose and scope

This is part 3 of the appearance consolidation. Part 2a made themes data in IntelliJ's `.theme.json` format, installed through one theme manager, with two built-ins: classic IntelliJ Light and Jasper Dark. This part lets Jasper use other IntelliJ themes:
- theme plugins downloaded from the JetBrains Marketplace, dropped into a themes folder;
- a bundled set of popular themes, vendored with their licences.

The user picks any of these by name in `config.toml`. A theme that carries an editor colour scheme also colours the terminal.

Decisions the user made:
- **Sources:** Marketplace theme plugin files (`.jar`/`.zip`) and a bundled set. There is no import from a local IntelliJ installation and no loose `.theme.json` files.
- **Selection:** config only. `ui.theme.name` names the theme. There is no gallery, preview, picker, settings panel or import command.
- **Installing:** the user drops the file into `<home>/themes/`, and Jasper reads it at start-up and on each config reload.
- **Terminal:** with `ui.theme.terminal = "match"`, a theme's editor-scheme console colours become the terminal palette. Keys a scheme omits come from its parent scheme, and IntelliJ's own `Default` and `Darcula` schemes are vendored for that.
- **Bundled set:** each theme is vendored from its upstream source repository, pinned to a commit, with its editor scheme and licence. This is approach A, one reader for bundled and installed themes.
- **Two plans:** 3a lets any Marketplace theme be used, and 3b ships the bundled set.

Out of scope:
- standalone `.theme.json` files;
- theme plugins that draw their theme in code (Material Theme UI and similar);
- background images (`background`, `emptyFrameBackground`);
- icon recolouring through `icons.ColorPalette`;
- New UI-only layout keys;
- watching the themes folder between config reloads;
- any change to the built-in themes' terminal palettes;
- any plugin SDK change.

## 1. Choosing a theme

`[ui.theme]` gains `name`, which replaces `variant`. `terminal` stays.

```toml
[ui.theme]
name = "Dracula"      # a built-in, bundled or installed theme's name; default "Jasper Dark"
terminal = "match"    # "match" (the theme's console colours), "light" or "dark" (Jasper's palettes)
```

- **Matching:** `name` is a string, compared with each theme's `.theme.json` `name`, ignoring case (`Locale.ROOT`) and surrounding whitespace. The built-ins are "IntelliJ Light" and "Jasper Dark".
- **Invalid names:** an empty or unknown name is a config warning listing the available names. At start-up Jasper uses Jasper Dark. On a live reload it keeps the theme it is showing.
- **Live:** changing `name` applies the new theme to every window without a restart, and keeps shells, scrollback, splits and font choices, as `variant` does today.
- **Removed outright (the MVP rule):**
  - `ui.theme.variant` (it is now an unknown key, reported by the usual unknown-setting warning);
  - the `Appearance` light/dark enum;
  - the View → Appearance Light and Dark items;
  - their palette commands, `view.appearance.light` and `view.appearance.dark` (a keybinding to either now gets the usual unknown-action warning).
- **What stays:** the View menu keeps the three Terminal Colors items (Match UI, Light, Dark) and their temporary override. Their submenu is renamed from "Appearance" to "Terminal Colors", since it holds nothing else.
- **Light versus dark:** code that needs only this reads the selected theme's `dark` flag. That covers the native title bar, the Buddy and plugins' `themeChanged(dark)`.

## 2. Where themes come from

A `ThemeCatalog` lists every theme Jasper can install, from three sources:
1. **Built-in:** IntelliJ Light and Jasper Dark, as in part 2a.
2. **Bundled** (plan 3b): the vendored themes under `dev/jasper/app/themes/bundled/<id>/`. Each directory holds a `.theme.json`, its editor scheme and its `LICENSE`, laid out as they are inside a plugin jar, so they use the same reader as installed themes.
3. **Installed:** `.jar` and `.zip` files in `<home>/themes/`, the directory holding `config.toml` (`~/.config/jasper/themes/` on macOS). Jasper creates the empty folder at start-up if it is missing.

**Scanning:**
- *What is considered:* only regular files directly in the folder whose names end in `.jar` or `.zip`, ignoring case. Hidden files, subdirectories and other files are ignored. A `.theme.json` placed there gets a warning saying that standalone theme files aren't supported and the plugin's `.jar` or `.zip` should be installed instead.
- *When:* the folder is scanned when the configuration loads, which means at start-up, when the file changes, and on **Reload config**. The scan runs on the configuration thread, not the Event Dispatch Thread.
- *Caching:* a file is re-read only when its size or last-modified time changed. A removed file's themes leave the catalog.
- *Hand-over:* the result is an immutable catalog snapshot handed to the Event Dispatch Thread with the configuration.

**Precedence:**
- An installed theme takes precedence over a bundled or built-in theme of the same name, so a user can install a newer Dracula.
- Two installed themes with the same name produce a warning naming both files. The one from the file that sorts first, ignoring case, wins.
- Within one plugin, the first provider in `plugin.xml` order wins.

## 3. Reading a theme plugin

**Layouts.** A Marketplace download is either a plugin `.jar` or a `.zip` holding `<Plugin>/lib/*.jar`:
- A `.jar`, or a `.zip` with `META-INF/plugin.xml` at its root, is read directly.
- Otherwise Jasper looks one level into the `.zip`'s `*/lib/*.jar` entries.

**Inside each jar:**
- `META-INF/plugin.xml` declares the themes as `<themeProvider id="…" path="/….theme.json"/>` elements. They sit inside `<extensions defaultExtensionNs="com.intellij">`, or appear as `com.intellij.themeProvider` without a default namespace. One plugin can provide several themes.
- A provider's `path` is resolved from the jar root. A path containing `..` is rejected.
- A theme's `editorScheme` names its editor colour scheme in the same jar. A leading `/` means the jar root; otherwise the path is resolved against the theme file's directory.

**Nothing is extracted, loaded or run.** Jasper reads only `plugin.xml`, the named `.theme.json` files and their schemes. It never loads classes, writes files or follows other references.

**Limits:**

| Limit | Value |
|---|---|
| Archive size on disk | 64 MiB |
| Each file read (`plugin.xml`, `.theme.json`, scheme) | 4 MiB |
| Each nested jar, read into memory | 32 MiB |
| Nested jars examined per `.zip` | 16 |
| Entries scanned per archive | 10,000 |

A file over a limit is a warning, and that theme (or archive) is skipped.

**XML is untrusted.** `plugin.xml` and scheme XML are parsed with the JDK's parser, with DOCTYPE declarations, external entities and external schemas disabled. A document with a DOCTYPE is rejected.

**Loading.** Each theme goes through the part 2a `ThemeLoader`, with the jar as its file reader:
- *Parents:* `parentTheme` resolves against the chains Jasper ships, "Darcula" and "IntelliJ". A parent Jasper doesn't have (for example the New UI's `ExperimentalDark`, or an Islands theme) falls back to Darcula when the theme is dark and to IntelliJ when it is light, with a warning naming the parent.
- *Author:* a missing `author` is filled in, because FlatLaf's `IntelliJTheme` fails without one.
- *Unchanged 2a behaviour:*
  - named colours;
  - per-OS values;
  - dropping values that name `com.intellij.*` classes;
  - putting back the keys FlatLaf skips;
  - the derived `Jasper.*` chrome keys with their 3:1 and 4.5:1 contrast floors, which keep a careless theme's chrome readable.
- *Loader fixes for untrusted files* (both part 2a deferred minors): a `parentTheme` that isn't a string is an error, where it was silently "no parent"; a per-OS value with one `com.intellij.*` branch drops only that branch, where it dropped the whole value.

A plugin with no `themeProvider` gets a warning. For example: "Material Theme UI provides no theme files; Jasper can't use themes drawn in code."

## 4. Terminal colours from the editor scheme

With `terminal = "match"`:
- A theme that has an editor scheme gets a terminal palette built from the scheme's console colours.
- A theme without one, or whose scheme fails to load, uses Jasper Light or Jasper Dark by its `dark` flag.
- The built-ins keep Jasper's palettes, as part 2a decided.
- `"light"` and `"dark"` always use Jasper's palettes, and View → Terminal Colors still overrides for the session.

**Mapping** to Jasper's `Palette`:

| Palette field | Scheme key, then fallback |
|---|---|
| background | `CONSOLE_BACKGROUND_KEY` (colour), then the `TEXT` attribute's `BACKGROUND` |
| foreground | `CONSOLE_NORMAL_OUTPUT` `FOREGROUND`, then the `TEXT` attribute's `FOREGROUND` |
| cursor | `CARET_COLOR` (colour) |
| selection | `SELECTION_BACKGROUND` (colour) |
| ANSI 0–7 | `CONSOLE_BLACK_OUTPUT`, `CONSOLE_RED_OUTPUT`, `CONSOLE_GREEN_OUTPUT`, `CONSOLE_YELLOW_OUTPUT`, `CONSOLE_BLUE_OUTPUT`, `CONSOLE_MAGENTA_OUTPUT`, `CONSOLE_CYAN_OUTPUT`, `CONSOLE_GRAY_OUTPUT` (`FOREGROUND`) |
| ANSI 8–15 | `CONSOLE_DARKGRAY_OUTPUT`, `CONSOLE_RED_BRIGHT_OUTPUT`, `CONSOLE_GREEN_BRIGHT_OUTPUT`, `CONSOLE_YELLOW_BRIGHT_OUTPUT`, `CONSOLE_BLUE_BRIGHT_OUTPUT`, `CONSOLE_MAGENTA_BRIGHT_OUTPUT`, `CONSOLE_CYAN_BRIGHT_OUTPUT`, `CONSOLE_WHITE_OUTPUT` (`FOREGROUND`) |

**Scheme format.** The root is `<scheme name="…" parent_scheme="…">`. Plain colours are `<colors><option name="…" value="…"/>`. Attributes are `<attributes><option name="…" baseAttributes="…"><value><option name="FOREGROUND" value="…"/>…</value></option>`. Values are hexadecimal RGB without `#`, and IntelliJ drops leading zeros (`"0"` is black), so values are parsed as numbers, not fixed-width strings. An absent or empty value means inherit.

**Inheritance.** A key the scheme doesn't set is looked up in this order:
1. the attribute named by its `baseAttributes`, within the scheme chain;
2. the scheme's `parent_scheme`, where "Darcula" and "Default" resolve to IntelliJ's own schemes, vendored for this;
3. the fallback column above;
4. Jasper's palette by the theme's `dark` flag, as the last resort, for example when the parent is unknown (with a warning) or a vendored parent lacks the key.

**Vendored parents.** IntelliJ's `Default` and `Darcula` editor schemes are vendored from the same `intellij-community` commit as part 2a (f7377708), at `platform/platform-resources/src/colorSchemes/`. They sit beside the vendored theme chain, are listed in `assets.tsv` with their SHA-256 hashes, and are pinned by the existing hash test. Plan 3a's first task confirms their upstream paths and that they carry the console keys above. Downloading them needs the user's approval.

**Faithful, not corrected.** No contrast floor is applied to scheme colours. They are the theme author's choice, as IntelliJ shows them.

## 5. Errors and diagnostics

Every theme problem is a configuration warning. It appears under the status indicator's **Config warnings** with the file and theme it concerns. None blocks start-up.

| Problem | Effect |
|---|---|
| Empty or unknown `ui.theme.name` | Warning listing the available names. Start-up uses Jasper Dark; a live reload keeps the current theme. |
| Unreadable, malformed or over-limit archive | Warning. That archive contributes no themes. |
| Plugin with no `themeProvider` | Warning. |
| A theme's JSON fails to parse or resolve | Warning naming the theme's file inside the archive. Other themes in the archive still load. |
| Unknown `parentTheme` | Warning. The theme falls back to the Darcula or IntelliJ base. |
| Duplicate installed names | Warning naming both files. The first file wins. |
| Bad, missing or over-limit editor scheme | Warning. The UI theme applies, and the terminal uses Jasper's palette by `dark` flag. |
| Unknown `parent_scheme` | Warning. Keys the scheme omits come from Jasper's palette. |
| Selected theme fails to install | The existing behaviour: the previous theme stays, with an error. |

## 6. The bundled set (plan 3b)

**Criteria.** A theme is bundled only if all of these hold:
- it is a classic-UI theme plugin;
- its source repository commits both the `.theme.json` and its editor scheme as files, not generated at build time;
- its licence allows redistribution with attribution (MIT, BSD, Apache-2.0 or ISC);
- it loads through Jasper's reader with no warnings and a complete terminal palette.

**Size.** Roughly 8 to 10 themes, mixing light and dark. The starting candidates are:
- Dracula;
- Nord;
- Gruvbox (dark and light);
- Solarized (light and dark);
- One Dark;
- Catppuccin;
- Night Owl;
- Xcode Dark;
- a GitHub-style light theme.

FlatLaf's IntelliJ Themes Pack index may be used to find each theme's upstream repository and licence. Jasper fetches from the upstream repositories, not from the pack.

**Process:**
1. Plan 3b's first task researches each candidate: repository, licence, whether the scheme is committed, and a pinned commit. It produces a table for the user to approve. Nothing is downloaded before that approval, and candidates that fail a criterion are dropped.
2. `tools/themes/fetch-intellij-themes.py` becomes manifest-driven. A new `tools/themes/bundled-themes.toml` gives each theme's id, repository, commit, theme path, scheme path and licence path.
3. The tool writes `dev/jasper/app/themes/bundled/<id>/` with the theme, its scheme and `LICENSE`. It also writes the SHA-256 lines in `assets.tsv` and a `SOURCE.md` recording each repository and commit, following the conventions of the IntelliJ Light vendoring. The two parent schemes from section 4 come through the same tool.
4. Each bundled theme's licence is included in the packaged application's notices.

Each bundled theme is selected by its own `name`, for example `name = "Nord"`.

## 7. Structure

New classes in `dev.jasper.app.appearance`, next to the part 2a loader:
- `ThemeCatalog` merges the three sources (section 2) and owns precedence, duplicates and the size and modified-time cache.
- `ThemeArchive` reads a plugin `.jar` or `.zip` (section 3).
- `EditorScheme` turns scheme XML into a `Palette` (section 4).

Other changes:
- `Theme` records a source (built-in resource, bundled resource, or an archive path plus the inner path) and the palette its scheme produced, if any.
- The `Appearance` enum is removed. The configuration carries the theme name, and the theme state resolves it through the catalog snapshot.
- Installing stays on the Event Dispatch Thread; scanning and reading happen on the configuration thread.
- The package graph and module boundaries are unchanged, and `verifyApplicationArchitecture` passes.
- The SDK is unchanged.

## 8. Testing

All tests are headless. Archive fixtures are built by the tests in temporary directories, so no test downloads anything.

- **Archives:**
  - a plugin `.jar`;
  - a Marketplace `.zip` with nested `lib/*.jar`;
  - several providers in one plugin;
  - no provider;
  - a `..` path;
  - each size and entry limit;
  - a malformed `plugin.xml`;
  - a DOCTYPE or external-entity (XXE) document, which is rejected without reading the entity.
- **Schemes:**
  - the full mapping;
  - hexadecimal values with dropped leading zeros;
  - `baseAttributes`;
  - `parent_scheme` resolving to the vendored Darcula and Default;
  - the `TEXT` fallbacks;
  - an unknown parent;
  - a malformed scheme.
- **Catalog:**
  - installed over bundled over built-in precedence;
  - duplicate names;
  - a rescan re-reading only files whose size or modified time changed;
  - a removed file's themes disappearing;
  - a `.theme.json` placed in the folder getting its warning.
- **Configuration:**
  - `ui.theme.name` matching;
  - unknown or empty names;
  - start-up versus live-reload behaviour;
  - `ui.theme.variant` now reported as an unknown key.
- **End to end:**
  - a Marketplace-shaped fixture theme installs and derives its chrome keys with the contrast floors;
  - under `"match"` the terminal takes its scheme palette;
  - under `"light"` and `"dark"` it takes Jasper's.
- **Removed features stay removed:** no Light or Dark item in the View menu, no `view.appearance.*` commands, and the submenu is named Terminal Colors.
- **Loader fixes:** a non-string `parentTheme` and a per-OS value with one `com.intellij.*` branch.
- **Bundled set** (plan 3b):
  - the hash test pins every vendored file;
  - every bundled theme loads with zero warnings and a complete palette;
  - every bundled licence is present in the packaged notices.
- `./gradlew check` passes. The user checks visually by installing a real Marketplace download, such as Dracula, and naming it in `config.toml`.

## 9. Documentation

- **New:** `docs/themes.md`. It covers installing a Marketplace theme, what is and isn't supported, how the terminal takes a theme's colours, the warnings, and the list of bundled themes.
- **Updated:**
  - `docs/configuration.md`, for `ui.theme.name`, the removal of `variant` and the new meaning of `terminal = "match"`;
  - `ConfigTemplate` and `config.example.toml`;
  - `docs/app-architecture.md`, the Themes section;
  - `docs/app-maintenance.md`, with a recipe for updating or adding a bundled theme;
  - `docs/STATUS.md`.

## 10. Plans

- **Plan 3a, "use any Marketplace theme":**
  - `ui.theme.name`;
  - removing `variant`, `Appearance` and the View-menu items;
  - `ThemeCatalog`, `ThemeArchive` and `EditorScheme`;
  - the two loader fixes;
  - vendoring the `Default` and `Darcula` schemes (downloads need approval);
  - the docs.
- **Plan 3b, "ship popular themes":**
  1. the research table, which the user approves before any download;
  2. the manifest-driven fetch tool;
  3. the vendored themes, their tests and notices;
  4. the bundled list in `docs/themes.md`.

3a comes first and is usable on its own.
