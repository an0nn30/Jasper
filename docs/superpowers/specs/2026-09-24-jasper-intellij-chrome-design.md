# IntelliJ-style modern chrome and colour icons

**Status:** Design approved section by section by the user on 2026-09-24. Written specification approved on 2026-09-24. On 2026-09-24, during planning, the user trimmed the new SDK icon names from ten to four (SPLIT, ZOOM, TERMINAL, SERVER), because the repository has no suitable retro rasters for the rest.

## Purpose and scope

In the **modern** style, Jasper's window chrome (titlebar, toolbar, tab strip and find bar) should look like the IntelliJ IDEA classic UI. It should use IntelliJ's full-colour, filled action icons, where colour carries meaning and buttons have no resting highlight. The **retro** style keeps its current Metal chrome and OldGNOME2/GNOME2/Tango artwork. The plugin SDK must give plugins the correct artwork for the running skin, so plugin controls look native in both styles.

Decisions the user made:

- Layout A ("IntelliJ-faithful"): the titlebar holds only the native window controls and a centred title. The toolbar is a separate row below it, and the tabs get their own strip below the toolbar.
- Icon contract A: plugins use host-owned semantic `IconName`s first. Custom plugin SVGs are drawn once in the IntelliJ light palette, and the host remaps palette colours for dark themes.
- Toolbar composition A: tab and window group, pane group, plugin group, and Find and Settings pinned to the right.
- Tabs copy IntelliJ editor tabs in shape and behaviour. Per-tab coloured backgrounds (IntelliJ "file colours") are **out of scope**.
- The find bar restyle to IntelliJ's single-line find bar is **in scope**.
- New retro names use the closest existing GNOME2 or OldGNOME2 rasters already in the repository, not new artwork. Only four names are added now. RUN, DEBUG, STOP, SYNC, UPLOAD and DOWNLOAD wait until a plugin needs them and suitable retro artwork exists.

Out of scope:
- a selector combo in the toolbar (IntelliJ's run-configuration box);
- the tab-strip ⋮ menu;
- multiline and whole-word find;
- any change to retro chrome;
- a general skin framework;
- changes to contribution record signatures.

The GTK style lives on the unmerged `claude/gtk-theme` branch. `main` has only `ThemeStyle { MODERN, RETRO }`. This design changes only the MODERN code paths. When GTK merges, its own spec decides its chrome, and GTK does not inherit this layout implicitly.

## 1. Window layout (modern)

From top to bottom, a modern window is:

1. **Titlebar (28 px scaled, macOS full-window-content)**
   - The traffic lights are on the left. A centred bold title shows the active session, for example `moray – zsh [~/projects]`.
   - It has a flat `Jasper.titleBackground` and a 1 px `Jasper.titleSeparator` bottom rule.
   - `MacTitleBar` no longer holds `WindowTabs` in modern mode. Modern and retro share the centred-title layout and differ only in colour and font tokens.
   - `titleHeight()` no longer depends on `tabHeight`. The `tabHeight` supplier and the `tabs` parameter are removed from `MacTitleBar.install`.
   - The title clips with an ellipsis when narrow. The existing symmetric insets keep it centred on the window.
2. **Toolbar (30 px scaled)**, described in section 2.
3. **Tab strip (28 px scaled)**, described in section 3. `WindowTabs` moves out of the titlebar into `WindowContent`'s north stack, below the toolbar.
4. Pane area. Each pane's find bar sits at the top of the pane (section 4).

On platforms without macOS full-window content, the OS draws the titlebar and rows 2–4 are unchanged.

## 2. Toolbar (modern)

```
[New tab][New window] │ [Split ▾][Zoom] │ <plugin items…> ⟶ [Find][Settings]
```

- Buttons are 16 px icons centred in 24 × 24 px squares. They are transparent at rest. On hover they fill with a rounded background (radius 3 px), taken from the look and feel's toolbar hover colour, and pressed uses the pressed colour. Toggle actions use the pressed colour while selected.
- Dropdowns (Split and plugin menus such as Remote's Sessions) draw a small ▾ to the right of the icon inside the same button.
- Separators are 1 px wide and 16 px tall, in `Separator.foreground`. They appear only between non-empty groups, so an empty plugin group adds no separator.
- Glue sits before Find and Settings. Settings (`OPEN_SETTINGS`) gains a modern toolbar button, where today it is retro-only. `QUIT` stays retro-only.
- Tooltips show the action label and its shortcut. The accessible name is the label.
- `ToolbarMode`:
  - *icons*: the default above;
  - *icons and labels*: the label is drawn right of the icon;
  - *hidden*: the row is removed.
- Narrow widths: labels drop first. Then the plugin group moves into a trailing » overflow menu, which keeps today's collapse behaviour in `ReferenceToolbar`.
- Implementation: restyle `ReferenceToolbar` and `ReferenceButton` in `workspace/WindowChrome.java` and reorder `addButton` calls into the groups. `renderContributedToolbar()` inserts plugin items into the plugin group. `RetroToolbar` is untouched.

## 3. Tab strip (modern, IntelliJ editor tabs)

- Tabs are rectangular with square corners and no gaps, directly adjacent on the strip background.
- Contents: a 16 px session icon, the title with an ellipsis, and a close ×. The × is grey and darkens on hover. It is always shown on the selected tab and on hover for the others.
- Selected tab: a lighter background (the look and feel's selected-tab background) and a 3 px accent underline. The underline uses the accent colour while the window is focused and turns grey when it is not.
- Unselected tab: the strip background, darkened slightly on hover.
- The strip scrolls horizontally when tabs overflow. A trailing ▾ opens a list of all tabs, and choosing one selects it and scrolls it into view.
- The strip is **always visible**, even with a single tab, as in IntelliJ. The modern behaviour where a lone session hides the tabs and shows the title is removed, because the titlebar now always shows the title.
- Session icon:
  - `SessionSpec.icon`, today "reserved for a tab icon", becomes the tab icon.
  - Sessions without one (local shells) use `IconName.TERMINAL`.
  - Remote passes `appearance().icon(IconName.SERVER)`.
  - The SDK Javadoc on `SessionSpec.icon` is updated to say so.
- Existing behaviour is preserved: close hit-testing, middle-click close, drag reordering, animations, keyboard selection and accessible names.
- Implementation: rework painting and layout in `workspace/WindowTabs.java`. Its title icons move from `icons/title/*.svg` (Tabler) to the IntelliJ set. `RetroTabs` is untouched.

## 4. Find bar (modern)

```
[🔍▾ query ……………………… ✕ │ Cc  .* ]  3/17   ↑  ↓                ✕
```

- It stays pane-local at the top of the pane, with a 1 px bottom separator against the terminal.
- **Field:**
  - borderless and full width;
  - a leading magnifier with a ▾ that opens a recent-queries menu for this pane's session, held in memory and never written to disk;
  - trailing in-field controls: ✕ to clear, then **Cc** (case-sensitive) and **.\*** (regex) toggles, which show a subtle filled background when selected.
- **Count:** `3/17` after the field. With no matches it reads `0 results` and the field gets the look and feel's error outline and background (`JComponent.outline = error`). A regex error does the same, with the error message as the tooltip.
- **Buttons:** icon-only ↑ Previous and ↓ Next (IntelliJ `previousOccurence` and `nextOccurence`), then a right-aligned ✕ Close. Their tooltips carry the labels and shortcuts.
- Keyboard bindings are unchanged: Enter goes to the next match, Shift+Enter to the previous, and Esc closes. The existing accessible names "Find in terminal", "Previous", "Next" and "Close" are kept. The icon-only toggles take the accessible names "Case sensitive" and "Regular expression", where today their visible labels are "Case" and "Regex".
- All search behaviour is unchanged: the 180 ms debounce, queued navigation, generation checks and `findAsync`.
- **Retro:** keeps today's text-button `FlowLayout` row.
- Implementation: `FindBar` keeps one component and all of its state. Its constructor builds either the modern or the retro row based on the skin. Toggles map to the existing `SearchQuery(text, regex, caseSensitive)`.

## 5. Icons

### Host artwork

- **Modern:** `jasper-app/src/main/resources/dev/jasper/app/icons/intellij/`
  - It holds the IntelliJ classic-UI SVGs (about 30) copied from `JetBrains/intellij-community` `platform/icons`, which is Apache-2.0. `_dark.svg` variants are copied where upstream has them.
  - `SOURCE.txt` records the upstream commit and the path of each file. It sits next to `LICENSE.txt` (Apache-2.0) and a `NOTICE`.
  - JetBrains product logos are not copied.
- **Rendering:** icons are `FlatSVGIcon` at 16 px with **no** single-colour tint.
  - `FlatSVGIcon` selects `_dark` variants under a dark look and feel.
  - Otherwise FlatLaf's global colour filter maps IntelliJ light-palette colours to the look and feel's `Actions.*` and `Objects.*` colours. The palette test in section 7 verifies this assumption, and the plan must stop if it fails.
  - Theme switches re-filter without reloading.
- **Retro:** unchanged. It uses `OldGnomeCatalog`, `GnomeIcons` and `SkinIcon`, with 28 px in retro toolbars.
- **Resolution:** `AppIcons` and `NamedIcons` resolve each host toolbar icon and each `IconName` to the IntelliJ SVG in modern and to the raster in retro.
- **Tabler cleanup:** the Tabler outline set (the root `icons/*.svg` and `icons/title/`) and its `SOURCE.txt` entry are deleted once nothing references them.

### SDK 0.7.5

- `JasperSdk.VERSION` becomes `0.7.5`. The plugin range `>=0.7.4, <0.8` is unaffected.
- **`IconName`** gains, each `@since 0.7.5`: `SPLIT`, `ZOOM`, `TERMINAL` and `SERVER`.
- **`OldGnomeIcon`** gains the same four entries.
  - Their retro rasters (16 and 24 px, plus the 28 px toolbar adaptation) are byte-identical copies of existing repository artwork:
    - SPLIT: GNOME2 `stock_table-split`;
    - ZOOM: GNOME2 `view-fullscreen`;
    - TERMINAL: GNOME2 `gtk-execute`;
    - SERVER: OldGNOME2 `NETWORK`.
  - All four come from the same OldGNOME2 collection and licence.
  - Each choice is recorded in `oldgnome-sdk/assets.tsv`, with its source set and licence noted in `NOTICE.md`.
- **`Appearance.icon(String svgResourcePath)`** changes behaviour. It is documented in the Javadoc and the SDK notes as a 0.7.5 behaviour change:
  - Old contract: "recoloured to the chrome's foreground; use monochrome artwork".
  - New contract: "rendered as authored, with IntelliJ light-palette colours remapped for dark themes".
  - Artwork stroked in `#6E6E6E`, like the sample's `flask.svg`, looks the same as before. Artwork in any other single colour now shows in that colour.
  - `icon(String, OldGnomeIcon)` modern rendering follows the same rule.
- **`SessionSpec.icon`** is documented as the tab icon (section 3).
- The testkit's fake `Appearance` and the contract suite cover every new name.

### Plugin guidance (`docs/plugin-authoring.md`, new "Icons" section)

- Prefer `IconName`: the host supplies modern and retro artwork.
- For custom art, draw a 16 × 16 SVG in the IntelliJ light palette (grey `#6E6E6E`, blue `#389FD6`, green `#59A869`, red `#DB5860`, yellow `#EDA200`). Colour carries meaning: green for run or success, red for stop or error, blue for navigation and transfer.
- Use `icon(svg, OldGnomeIcon)` to choose retro artwork for custom art.

## 6. Error handling and edge cases

- A missing host icon resource is caught at build time by the resolution test (section 7). There is no silent runtime fallback for host icons.
- A plugin SVG path that doesn't exist still throws `IllegalArgumentException`.
- Changing the style still requires a restart. Dark and light variants switch live.
- Narrow windows: the toolbar collapses as in section 2, tabs scroll and the ▾ stays reachable, and the title shows an ellipsis.
- Threading is unchanged: all chrome runs on the EDT, and the find bar keeps its existing generation guards.

## 7. Testing

Tests are headless, written TDD-first and follow the existing workspace and view test patterns.

- **Toolbar (modern):**
  - group order;
  - separators only between non-empty groups;
  - Find and Settings after the glue;
  - plugin items in the plugin group;
  - tooltips and accessible names;
  - `ToolbarMode` variants.
- **Toolbar (retro):** today's composition is pinned by a test, so it stays unchanged.
- **Titlebar:**
  - modern `MacTitleBar` holds no tabs;
  - its height is independent of the tab height;
  - its title follows the active session.
- **Tabs:**
  - the strip is visible with one tab;
  - `SessionSpec.icon` is used, with `TERMINAL` as the default;
  - ▾ list selection;
  - close hit-testing;
  - existing keyboard and drag behaviour still passes.
- **Find bar:**
  - the modern and retro rows are chosen by skin;
  - accessible names and key bindings are preserved;
  - Cc and .\* drive `SearchQuery`;
  - no-match and regex-error states set the error outline;
  - recent queries are per pane and in memory.
- **Icons:**
  - every `IconName` and every host toolbar icon resolves in modern light, modern dark and retro;
  - a palette test renders a `#389FD6` SVG under a dark FlatLaf and asserts the dark `Actions.Blue` colour;
  - `icon(String)` output is not tinted to a single colour.
- **SDK:** the testkit contract suite covers the new names. `./gradlew check` passes, including `verifySdkArchitecture`, `verifyApplicationArchitecture`, doclint and the documentation examples.
- **Source hygiene:** the AGENTS.md character check passes.
- **Visual acceptance:** the user runs the app in modern light, modern dark and retro. No agent launches the GUI.

## 8. Documentation

Update:
- `docs/plugin-authoring.md`: the new Icons section;
- `docs/sdk-architecture.md`: a 0.7.5 section covering the new names, the `icon(String)` behaviour change and `SessionSpec.icon`;
- `jasper-sdk/README.md`, if it lists the version;
- `docs/app-architecture.md`: the chrome layout;
- the icon `SOURCE.txt`, `LICENSE.txt`, `NOTICE`, `assets.tsv` and `NOTICE.md`;
- `docs/STATUS.md`.
