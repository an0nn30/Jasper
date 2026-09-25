# Theme engine and IntelliJ Light

**Status:** Design approved in chat by the user on 2026-09-25. Written specification awaiting user review. No plan yet.

## Purpose and scope

Part 2a of the appearance consolidation. Part 1, "one layout", removed the retro style.

Today the light and dark looks are FlatLaf Light and FlatLaf Dark, plus Jasper's hand-tuned `.properties` overrides. Those are TermLab-style form, button and panel colours, and `BrandedButtonUI`. That is why the side panel, its buttons and the search field don't look like IntelliJ.

This part makes themes data, in IntelliJ's own `.theme.json` format, loaded through one theme manager:
- The built-in **Light** becomes the real **classic IntelliJ Light**.
- The built-in **Dark** keeps today's Jasper Dark look, re-expressed as an IntelliJ-format theme.
- User-installed themes come later (part 3), and the format and loader are designed for them now.

Decisions the user made:
- **Classic, not New UI.** Vendor IntelliJ Light from `JetBrains/intellij-community`, Apache-2.0, pinned to a commit. The user runs IntelliJ with the Classic UI plugin, so this is the classic `themes/Light.theme.json` → `themes/intellijlaf.theme.json` chain. The New UI themes (`themes/expUI/`, `themes/islands/`) are not used.
- **The terminal palette is unchanged.** Light and Dark keep Jasper's terminal palettes, and `ui.theme.terminal` keeps working. Importing IntelliJ editor schemes and console colours is part 3.
- **Dark keeps today's look,** rewritten as `jasper-dark.theme.json`. It does not become Darcula.
- The Search Everywhere palette is part 2b, with its own spec. It reads the `SearchEverywhere.*` keys this part installs.

Out of scope:
- theme selection by name, and a user themes directory (part 3);
- editor-scheme and console-colour import (part 3);
- restyling Dark to Darcula;
- any SDK change (part 2b adds `ScopeSpec.withInAll`);
- the palette's layout (part 2b).

## 1. Theme files

The built-in theme resources live in `jasper-app/src/main/resources/dev/jasper/app/themes/`:
- **Vendored:** `intellij/Light.theme.json`, `intellij/intellijlaf.theme.json` and `intellij/darcula.theme.json`. These are byte-identical copies from `platform/platform-resources/src/themes/` at a pinned `intellij-community` commit, with `SOURCE.md`, `LICENSE.txt` (the canonical Apache-2.0 text) and `assets.tsv` (path, upstream path, SHA-256). They follow the conventions of `icons/intellij/`. `darcula.theme.json` is included only because `intellijlaf.theme.json` names it as `parentTheme`.
- **Written by Jasper:** `jasper-dark.theme.json`, a standalone theme with no `parentTheme`. It sets the IntelliJ keys, and where needed the `Jasper.*` keys (section 3), so that the dark UI matches today's colours. It also covers the `SearchEverywhere.*` keys, so the part 2b palette has dark values.

The old `FlatLaf.properties`, `FlatLightLaf.properties` and `FlatDarkLaf.properties` overrides in `dev/jasper/app/themes/` are deleted, along with the `FlatLaf.registerCustomDefaultsSource("dev.jasper.app.themes")` call that loads them. The TermLab form keys go with them: `Jasper.formBackground`, `formListBackground`, `controlBackground`, `comboBackground`, `buttonBackground`, `buttonBorder`, `buttonForeground`, `accentBackground`, `primaryBackground` and `primaryBorder`. `BrandedButtonUI` and its registration are deleted too. Buttons, fields, lists, combos and panels are drawn by FlatLaf with the theme's colours.

## 2. Loading

Two facts about FlatLaf 3.7's `IntelliJTheme`, checked in its class file, shape the loader:
- **It ignores `parentTheme`.**
- **It skips IntelliJ-only key namespaces.** These include `EditorTabs.`, `MainToolbar.`, `SearchEverywhere.`, `StatusBar.`, `ToolWindow.`, `Borders.` and `Link.` (it maps `Link.activeForeground` to `Component.linkColor`).

Jasper resolves the chain itself, and puts the skipped keys back.

- **`ThemeLoader`** is new, in `dev.jasper.app.appearance`. It reads a `.theme.json` by resource name and resolves it into one flat theme JSON:
  - **`parentTheme` chain:** `parentTheme` names a theme by its `name` field (`"IntelliJ"`, `"Darcula"`), looked up among the built-in themes. The parent is resolved first, and the child's `ui`, `colors` and `icons` entries override the parent's key by key. Nested objects are merged, and object-form keys (`"Button": {"arc": 3}`) are equivalent to dotted keys (`"Button.arc"`).
  - **Named colours:** values that name an entry of the merged `colors` table (`"panel"`, `"contentBackground"`) are replaced by that entry's value, recursively.
  - **Failure:** a missing parent, a reference cycle or an unknown named colour fails with an error naming the theme and the key.
  - The resolved JSON is passed to FlatLaf's `IntelliJTheme.createLaf(InputStream)`.
  - **Skipped keys:** every colour-valued `ui` entry that FlatLaf skipped is added to that LAF's defaults (`FlatLaf.setExtraDefaults`). Only explicitly named keys are put back, so `*.` wildcard keys are not expanded into those namespaces. Putting a key back never replaces a key FlatLaf set. So `UIManager.getColor("SearchEverywhere.Tab.selectedBackground")` and `UIManager.getColor("EditorTabs.underlineColor")` return the theme's colours.
  - **Colour values:** IntelliJ `#RRGGBBAA` values keep their alpha (for example `EditorTabs.hoverBackground` `#00000019`).
- **Parent semantics spike.** IntelliJ treats `parentTheme` specially: its classic light base is really its Java `IntelliJLaf` defaults, not only Darcula JSON. So the implementation begins with a spike test that loads IntelliJ Light and asserts classic values in `UIManager`:
  - `Panel.background` is `#F2F2F2`;
  - `TextField.background` is white;
  - `List.selectionBackground` is `#2675BF`;
  - `EditorTabs.underlineColor` is `#4083C9` (a skipped key that was put back);
  - `SearchEverywhere.Tab.selectedBackground` is `#DFDFDF`;
  - `SearchEverywhere.SearchField.background` is white;
  - `ToolWindow.background` is white.

  If inherited Darcula values leak into the light theme, the loader must stop taking `ui` keys from a dark parent into a light child (a `dark` mismatch). Colours the classic light theme needs but doesn't declare then come from FlatLaf's light base, and the spike asserts the list above. The spike's outcome and the rule it forces are recorded in the plan's status.
- **`ThemeManager`** replaces `BuiltinTheme` and the enum installer, and takes over `ThemeController`'s installation role:
  - It holds the built-in themes by id (`intellij-light`, `jasper-dark`), each with its resource name, display name, `dark` flag and Jasper terminal palette (`Palette.jasperLight()`, `Palette.jasperDark()`).
  - It installs a theme by building its FlatLaf LAF through `ThemeLoader` and `UIManager.setLookAndFeel`. A failed install rolls back to the previous LAF, as today.
  - `ThemeController` keeps its public behaviour: saved variant, session overrides, terminal colours, font defaults, listeners and `ResolvedTheme`.
  - `ui.theme.variant = "light"` resolves to IntelliJ Light, and `"dark"` to Jasper Dark.
- **SDK `Variant`** is derived from the installed theme's `dark` flag. The SDK itself is unchanged.

## 3. Jasper's own UI keys

Jasper chrome reads `Jasper.*` UIManager keys for:
- the title row, toolbar and tab strip;
- the split divider;
- the find bar's error colour;
- the config status colours;
- the running indicator.

Until part 2b, the palette reads them too. After a theme is installed, a single **key derivation** step fills every `Jasper.*` key the theme did not set, from IntelliJ keys, in a fixed fallback order. The keys it reads from include those put back in section 2.

| Jasper key | Derived from (first present wins) |
|---|---|
| `Jasper.titleBackground` | `MainToolbar.background`, `TitlePane.background` |
| `Jasper.titleForeground` | `TitlePane.foreground` |
| `Jasper.titleInactiveForeground` | `TitlePane.inactiveForeground` |
| `Jasper.titleSeparator` | `MainToolbar.borderColor`, `Borders.color`, `Separator.foreground` |
| `Jasper.chromeForeground` | `MainToolbar.foreground`, `Label.foreground` |
| `Jasper.mutedForeground` | `Label.infoForeground`, `Label.disabledForeground` |
| `Jasper.tabSelectedBackground` | `EditorTabs.underlinedTabBackground`, `EditorTabs.selectedBackground`, `Panel.background` |
| `Jasper.tabSelectedForeground` | `EditorTabs.underlinedTabForeground`, `Label.foreground` |
| `Jasper.tabHoverBackground` | `EditorTabs.hoverBackground` |
| `Jasper.tabUnderline` | `EditorTabs.underlineColor` |
| `Jasper.tabUnderlineInactive` | `EditorTabs.inactiveUnderlineColor` |
| `Jasper.splitDivider` | `Borders.color`, `Separator.foreground` |
| `Jasper.findErrorBackground` | `SearchField.errorBackground`, then `Actions.Red` mixed 12 % over `TextField.background` |
| `Jasper.runningForeground`, `Jasper.configSuccessForeground` | `Actions.Green` |
| `Jasper.configWarningForeground` | `Actions.Yellow` |
| `Jasper.configErrorForeground` | `Actions.Red` |
| `Jasper.palette*` (until part 2b) | Today's aliases: background, foreground and muted from the derived `Jasper.tabSelectedBackground`, `chromeForeground` and `mutedForeground`; border from `Component.borderColor`; accent from `Component.focusedBorderColor`; selection from `List.selectionBackground` and `List.selectionForeground` |

FlatLaf always defines the last source in each row, so derivation never falls through to a literal. Where a row mixes colours, it mixes the theme's own colours; no light or dark literal is used.
- **Rail and side panels:** the rail and the plugin side panels paint `ToolWindow.background` instead of the generic panel colour they get today. That is the white side panel in IntelliJ Light. Their headers use `ToolWindow.Header.background`, falling back to `ToolWindow.background`.
- **Status bar:** the status bar paints `StatusBar.background` with a top rule in `StatusBar.borderColor`, falling back to `Panel.background` and `Jasper.titleSeparator`.
- **Overrides:** a theme may set any `Jasper.*` key directly in its `ui` section, and the derivation never overwrites a key the theme set. `jasper-dark.theme.json` uses this to keep today's dark chrome colours exactly.
- **Palette keys:** part 2b deletes the `Jasper.palette*` rows when the palette reads `SearchEverywhere.*` and `List.*` directly.

## 4. Behaviour that stays

- Live light and dark switching works as before: the View menu, `ui.theme.variant` reload, and the session override.
- `ui.theme.terminal` works as before.
- UI font settings work as before.
- The IntelliJ layout, icons and the palette-colour remapping of SVG icons work as before.
- A failed theme install keeps the previous theme and reports the error, as before.

## 5. Testing

- **Loader:** parent-chain merge order, object versus dotted keys, named-colour resolution (nested), and failure messages for a missing parent, a cycle and an unknown colour.
- **Spike assertions** (section 2) for IntelliJ Light, and the equivalent for Jasper Dark: its key colours equal today's values for the title row, tabs, status bar, panels and selection.
- **Skipped keys:** a fixture theme's `EditorTabs.`, `SearchEverywhere.`, `StatusBar.` and `ToolWindow.` colours reach `UIManager`; a key FlatLaf itself sets is not replaced; `#RRGGBBAA` keeps its alpha.
- **Derivation:** every `Jasper.*` key is non-null under both themes. A theme-set key is not overwritten. Each derived key follows its table source, with fixture themes that set only one source.
- **Side panels and status bar:** under IntelliJ Light, the rail and side panels paint `ToolWindow.background` (white) and the status bar paints `StatusBar.background`.
- **Screenshot parity:** the colours visible in the user's screenshot, sampled from a headless render of the SSH hosts panel with its search field and buttons, match IntelliJ Light's panel, field and button colours.
- **Existing appearance tests keep passing:** `ThemeControllerTest`, `TerminalColorsTest`, `MockUiTest`, `MacTitleBarTest`, `WindowTabsTest`, `FindBarModernTest` and `BrandedButtonsTest`, which is replaced by a button-colour test against the theme. Tests that pinned the old hand-tuned hex values are updated to the theme's values, and each change is listed in the plan.
- **Provenance:** `assets.tsv` hashes match, and the LICENSE and SOURCE files are present.
- `./gradlew check` passes. The user checks light and dark visually.
