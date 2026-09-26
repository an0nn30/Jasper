# One layout: remove the retro style

**Status:** Implemented on `claude/theme-engine`; visual acceptance pending.

## Purpose and scope

Jasper currently has two appearance styles:
- **modern:** FlatLaf with the IntelliJ-style chrome;
- **retro:** Java Metal with its own toolbar, tabs, find bar, menu placement, terminal palette and GNOME2, Tango and OldGNOME2 raster icons.

Nearly every chrome class branches on the style. The user wants a single layout and a single theme engine. This specification is part 1 of that refactor: remove retro and collapse the code to the IntelliJ-style path. Part 2, the theme engine with IntelliJ-format themes and IntelliJ Light, gets its own specification. Part 3, importing user themes, comes later.

Decisions the user made:
- **Remove retro entirely.** This covers its chrome, its icon artwork and catalogs, and the `ui.theme.style` setting.
- **Drop the GTK style.** It exists only on the unmerged `claude/gtk-theme` branch. This branch is untouched and doesn't merge it; if GTK is wanted later, it returns as a theme.
- **SDK 0.8.0** removes `OldGnomeIcon` and `Appearance.icon(String, OldGnomeIcon)`.
- **No migration notice.** Jasper is an unpublished MVP, so `ui.theme.style` is simply removed. An old `style = …` line becomes an ordinary unknown-key warning, and no documentation about the removal is written.
- **Branch:** `claude/theme-engine`, cut from `main` at `eb0b2959`.

Out of scope:
- any new theme code;
- any change to how modern Light and Dark look;
- the `Variant` plugin API, which is unchanged;
- rewriting historical records (older STATUS entries, specs and plans).

## 1. Deletions

- **Production classes:**
  - `appearance/MetalDefaults`, `appearance/RetroPalette`;
  - `workspace/RetroToolbar`, `workspace/RetroTabs`;
  - `platform/GnomeIcons`, `platform/OldGnomeCatalog`, `platform/SkinIcon`, `platform/SwingAppearance`;
  - `config/ThemeStyle`;
  - the `BuiltinTheme.RETRO` constant.
- **Resources:** `jasper-app/src/main/resources/dev/jasper/app/icons/{gnome2,tango,oldgnome-sdk}/`, including their licences, notices and manifests.
- **SDK (version 0.8.0):**
  - `dev.jasper.sdk.ui.OldGnomeIcon` and `Appearance.icon(String, OldGnomeIcon)`;
  - Javadoc about skins, retro and 28 px retro toolbar variants.
- **Testkit:** `FakeSkinIcon`; the `retro` component of `FakeNamedIcon`; `FakePluginHost.retroIcons`/`setRetroIcons`; and the related branches in `FakePluginContext`.
- **Configuration:**
  - `ui.theme.style`, removed from the `ConfigLoader` known keys and parsing;
  - the `ConfigSnapshot` `style` component, its builder field and setter, and the compatibility constructor that takes a `ThemeStyle`;
  - the `ConfigTemplate` and `config.example.toml` lines;
  - the `ConfigurationController` restart notice.
- **Tests:**
  - whole files: `RetroThemeTest`, `RetroChromeTest`, `RetroTabsTest`, `OldGnomeCatalogTest`, `GnomeIconsTest`, `SkinIconsTest`, `FakeSkinIconTest`, and the SDK tests `AppearanceIconTest` and `IconCatalogTest`;
  - retro-only test methods, for example in `NativeShellsChromeTest`, `UiTypographyTest`, `TerminalColorsThemeTest`, `TerminalColorsTest`, `MacTitleBarTest`, `WindowPanelsTest`, `FindBarModernTest`, `ConfigLoaderTest`, `ConfigSnapshotBuilderTest`, `ConfigurationControllerTest`, `JasperApplicationPluginsTest`, `CommandPaletteTest`, `HostedUiTest` and Vault's `EntryEditorTest`.

## 2. Collapsing to one path

Every conditional on the style keeps its modern branch:
- `SwingAppearance.retro()`, `WindowContent.retro()`, `ThemeController.style()`, `ThemeStyle`, `BuiltinTheme.RETRO`;
- the UIManager keys `Jasper.retro` and `Jasper.retroTitleBackground`.

Specifically:
- **`ThemeController`:**
  - has no style field and no style parameters; its constructors take only the saved `Appearance` and, for tests, the installer;
  - `choice()`, `terminalColors()`, `selectAppearance`, `selectTerminalColors` and `apply` act unconditionally;
  - the Metal default capture and restore is removed;
  - the installer installs only FlatLaf Light or Dark.
- **`AppIcons`:**
  - `icon(name)` validates against the modern chrome map and returns IntelliJ artwork;
  - `toolbarIcon(name)` becomes the same as `icon(name)` and is removed if nothing needs the alias;
  - `skin(...)` and `forToolbar(...)` are removed, and their callers use icons directly;
  - `named(name)` returns only the IntelliJ or tinted standard artwork.
- **`MacTitleBar`:**
  - one `HEIGHT = 28`;
  - menus are always the root's (screen) menu bar;
  - `isSupported()` depends only on full-window-content support;
  - one set of colour and font keys.
- **Workspace:**
  - `WindowChrome` always builds the IntelliJ toolbar row: it has no retro toolbar, separator helper or note menu item, and Settings stays in the toolbar;
  - `WindowContent` always creates `WindowTabs`, has no `RetroTabs` and no `retro()`, and uses the terminal palette behind the panes;
  - `FindBar` always builds the IntelliJ row, with no text-button row;
  - `TerminalDeck`, `WindowStatusBar`, `WindowRail`, `WindowCommandPalette` and `WindowCommands` keep only their modern code. The View "Tab height" item and the Appearance items are always enabled.
- **Elsewhere:**
  - `CommandPalette` keeps only its modern painting;
  - `ApplicationBootstrap` always uses the macOS screen menu bar;
  - `JasperApplication` constructs `ThemeController` from the saved variant only;
  - `HostedUi` drops the retro `Appearance` override;
  - Remote's `FlatToolBar` Javadoc no longer mentions Metal.

## 3. SDK 0.8.0 and plugins

- `JasperSdk.VERSION = "0.8.0"`.
- `Appearance` keeps `variant()`, `onChanged(...)`, `icon(IconName)` and `icon(String)`. `IconName` and its Javadoc describe a single appearance.
- Every bundled `plugin.toml` (`history`, `snippets`, `sample`, `vault`, `remote`) declares `sdk = ">=0.8.0, <0.9"`.
- The sample plugin uses `context.appearance().icon("dev/jasper/sample/flask.svg")`.
- The testkit's `FakeNamedIcon(IconName name)` and its fake `Appearance` match the SDK, and the contract suite covers only the remaining methods.

## 4. Documentation

Current documentation drops every description of retro and of choosing a style:
- the root and `jasper-app` READMEs;
- `jasper-sdk/README.md`;
- `docs/configuration.md`, including the `[ui.theme]` example, the table rows and the "Retro Metal appearance" section;
- `docs/app-architecture.md`, `docs/app-maintenance.md` and `docs/sdk-architecture.md`;
- `docs/sdk-icons.md`, which is rewritten for one appearance;
- `docs/plugin-authoring.md`, `docs/remote-7c-verification.md` and any build or run notes.

`docs/STATUS.md` gains one entry for this change. Older STATUS entries, specs and plans are historical and stay as they are.

## 5. Verification

- `./gradlew check` passes: tests, doclint, documentation examples, and the SDK, plugin, application and terminal architecture checks.
- Tests that were parametrized over `{modern, retro}` run once, on the single path. No assertion about modern behaviour is weakened.
- **Removal gates:** a check over `jasper-*/src`, `plugins/*/src`, `config.example.toml` and current docs finds no remaining:
  - `retro`, `Retro`, `Metal`, `OldGnome`, `ThemeStyle`, `SkinIcon` or `GnomeIcons` identifiers in production or test source (outside historical docs);
  - `icons/gnome2`, `icons/tango` or `icons/oldgnome-sdk` resources.
- The user checks modern Light and Dark visually; no agent launches the GUI.
