# Jasper Enterprise presentation

Status: **Approved on 2026-10-15** (the user: "The spec is approved; left-aligned colon labels and keeping Form.hero are fine.").
Amended late on 2026-10-15 for the second mock set (reference folders 07 to 10) under the user's overnight instruction "make
decisions on my behalf": 5.1, 5.2, 5.9, 7.6, 8.2, 9.1, 10.8, 10.11, 14.1, 14.3, 14.6, 14.8, 16.2, 18 and 19.1.
Amended on 2026-10-16 with plan 3d's two rulings, made under the same authority: 8.2 (`DialogSpec.swatch`), 10.3 and 15
(toolbar view admission).
Amended on 2026-10-16 with plan 4a's Rulings 17, made under the same authority: 5.4 (the provisional token ranges stand).
Amended on 2026-10-16 with plan 3e's chooser ruling: 11.7 and 13.
Amended on 2026-10-17 with plan 6b's ruling 3: 7.1, 7.2 and 7.7 (border.divider is overridable).
Amended on 2026-10-17 with plan 6b's rulings 10 and R44, made under the same authority: 10.8 (the overlay's placement),
4.9, 9.1 and 16.1 (the `DialogMapping` reader).
Amended on 2026-10-17 with plan 5b's accent ruling, made under the same authority: 7.6 (Enterprise Dark's accent is #3C74D6).
Amended on 2026-10-17 with plan 5b's status-text ruling, made under the same authority: 5.6 (classic status text is 11 px).
Amended on 2026-10-17 with plan 7's bottom-strip ruling, made under the same authority: 10.7 (the bottom strip is 24 px plus its rule).
Amended on 2026-10-17 with plan 10b's ruling: 10.1 (auxiliary windows carry no menu row under Enterprise).
Amended on 2026-10-18 by the series' final alignment pass, recording rulings approved during execution: 4.1, 6.4, 7.1,
7.6, 9.2, 10.6, 10.11, 11.1, 11.2, 11.5 and 14.8.
Amended on 2026-10-18 at the user's direction after trying Enterprise: 10.7 (side stripes fit their labels, centred, not resizable; the second group stands at the stripe's bottom), with 3, 9.1, 12.1 and 12.2 (Settings hides the Panel rail width).
Amended on 2026-10-18 at the user's direction after trying Enterprise (plan 12): 1 (decisions 4 and 14), 4.1, 5.1, 5.2, 5.8, 7.6 (the theme ports IntelliJ Light and New UI Dark; plan 5b's accent ruling is superseded), 7.7, 8.2, 8.6, 9.1, 10.3, 10.7, 11.5, 14.4, 17 and 19.1.
Branch: `claude/enterprise-presentation`, from `main` at `3c6e9dcc3`.

This amends the [single layout specification](2026-09-25-jasper-single-layout-design.md): its "a single layout and a
single theme engine" direction (line 11) is superseded by one default layout plus host-implemented variants chosen once
at start; everything else in that document stands. A runtime-switchable skin stays rejected ([retro Metal
design](2026-09-22-jasper-retro-metal-design.md), line 97).
This amends the [compact density design](2026-10-12-jasper-compact-density-design.md): its decision 4 ("one, compact,
density") and "not a second scale" (line 35) become one density per kit catalog; everything else in that document
stands, including "no density setting".
This amends the [theme engine specification](2026-10-04-jasper-theme-engine-design.md): its kit rules (switches,
segments, no checkboxes; lines 166-167) become rules of the default look; everything else in that document stands.
This amends the [plugin SDK design](2026-09-21-jasper-plugin-sdk-design.md): "never for chrome" (line 376) and section
14's "plugin-supplied chrome components" are superseded by admitted toolbar views and declarative presentation data, and
`--safe-mode` (line 924) also drops presentations; everything else in that document stands, including "no privileged
tier".
Section 19.2 lists every amended specification and the line each status banner gains.

## 1. Intent and decisions

Enterprise is a second look for Jasper, built the way IntelliJ builds its Classic UI: a bundled plugin that holds a
declarative kit definition, a theme and fonts, while every line of presentation code lives in jasper-ui and the app.
Turning the plugin on and restarting gives native title bars, menus inside the window, labelled tool-window stripes, a
classic toolbar, ruled status cells, a Run window in place of the command palette card, and classic controls
(checkboxes, radios, titled group boxes, spinners, classic tables and trees). Colours always come from the active
theme, so every existing theme keeps working under both looks.

The mocks and the design language the user supplied are preserved unchanged under
[`docs/design/enterprise/reference/`](../../design/enterprise/reference/) with a [manifest](../../design/enterprise/reference/manifest.json)
of SHA-256 hashes. Authority order: (1) the decisions in this document; (2) the mocks for depicted layout;
(3) [DESIGN_LANGUAGE.md](../../design/enterprise/reference/DESIGN_LANGUAGE.md) for intent; (4) existing approved
specifications for everything this design does not change. Numbers, names and data shown in the mocks (row counts,
host names, stripe numbers, saved setting values, "1 of 11") are fixtures. Hex values are targets for the Enterprise
theme only.

The user's decisions (2026-10-14 and 2026-10-15):

1. **Kit code lives host-side.** jasper-ui and the app implement each look variant once. The Enterprise plugin ships a
   declarative kit definition, its theme and its fonts as data, and exports `dev.jasper.enterprise.api`. The plugin
   presentation gate, the JDK-only SDK and the SDK signature rules are unchanged.
2. **Bundled and off by default.** The user turns Enterprise on in Settings › Plugins; a restart applies it.
3. **Colours come from the active theme**, and every existing theme must keep working under the Enterprise kit,
   enforced by tests.
4. **Remote adds the toolbar's session combo and Run button only when Enterprise is enabled**, discovering Enterprise
   through its exported API (a presence-only `Workstation` service and an optional requirement).
5. The overall shape and principles (section 3) are approved as presented.
6. The presentation provider (section 4) is approved as presented.
7. The kit file and the jasper-ui seam (sections 5 and 6) are approved as presented.
8. **macOS menus under Enterprise are in the window** (`apple.laf.useScreenMenuBar=false`), not the screen menu bar.
9. **Stripe numbers come from the real toggle shortcuts** (IntelliJ semantics). With today's bindings the stripes read
   "1: Sessions", "2: SFTP" and "3: Transfers"; the mock's numbering is a fixture. Nothing is rebound.
10. **The Run window stays open** (modeless, like the Windows Run dialog). It closes only on Escape, ×, Cancel or
    running a row; clicking a pane hands focus back without closing it.
11. **Bottom strip:** under Enterprise the existing Notifications panel docks in the bottom strip titled "Event Log";
    the default look keeps "Notifications". Remote's Transfers becomes a bottom panel under Enterprise. A Problems panel
    and Detach are not part of this series.
12. Window chrome, the Run window and Settings (sections 10, 11 and 12) are approved with rulings 8 to 11.
13. **SDK 0.18 renames names a classic look makes untrue**: `ChoiceKind.SEGMENTED` → `EXCLUSIVE`,
    `ButtonPresentation.CIRCULAR` → `HELP`, and the hero family → prompt. A clean break: plugins built on 0.17 that use
    a removed member are refused at load by the existing `KitCompatibility` gate, and THD Sourcing Crypto rebuilds for
    0.18. `Toggle`, `GHOST`, `MONO*` and `Card` keep their names, which stay accurate.
14. **Enterprise's own theme is "Enterprise"** (id `enterprise`) with a Light and a Dark variant in this series. Since
    2026-10-18 (the user) Light is a port of IntelliJ's IntelliJ Light and Dark of its New UI Dark, each with its terminal
    scheme (7.6). The kit's defaults select `enterprise` for both `ui.theme.light` and `ui.theme.dark`.
15. **Visible changes to the default look**, applied in both looks (section 13): the Session and Transfer standard
    menus replace Remote's "SSH" menu; a new File › Open File… action; "Vault: Locked"/"Vault: Unlocked"; the Snippets
    verb "Insert"; titled leading groups on Settings pages; the FTP warning as a Notice; read-only TFTP login and File
    Server folder fields; the ordered-list editor for plugin `stringList` settings; the data face in Project Index's
    Location column; three recent palette items instead of two.
16. The theme engine and the SDK 0.18 changes (sections 7 and 8) are approved; the theme name "Enterprise" is
    confirmed.
17. **Plugin form field labels switch to sentence case in both looks** ("Via host", "Jump host", "Reachable from"),
    per DESIGN_LANGUAGE §3. The plugin compositions (section 14) are approved.
18. The guarantee, the tests, the ten plans and the amendments (sections 16 and 19) are approved.
19. **The second mock set** (Credential Vault, Dark Mode, App Icon, Boot Splash) was added on 2026-10-15 with the
    instruction to make the kit match the mocks and to decide on the user's behalf. Claude's rulings: Enterprise Dark
    takes the Dark Mode mock (7.6); OVERLAY and ATTACHED_SHEET dialogs are drawn in the window as classic dialogs
    (10.8), replacing native dialogs; Vault consumes `Workstation` for a classic manager (14.8); the splash and the
    icon belong to the look's `[branding]` (10.11) as plan 11, the splash without motion and with real start-up steps;
    the packaged icon, the product name outside the splash and "Remember in Keychain" stay out of scope (section 18).

Jasper is an MVP: settings and APIs are removed or replaced outright, with no migration, aliases or deprecation shims.

## 2. Verified facts this design rests on

Established on 2026-10-15 in the worktree at `3c6e9dcc3`. Do not re-derive; test if in doubt.

1. **Bootstrap order.** `ApplicationBootstrap.prepareDesktop` sets the desktop properties (`:68`, with
   `apple.laf.useScreenMenuBar=true` at `:89`), chooses the title-bar style from the first config snapshot
   (`:70-72`), prepares built-in icons (`:74`), runs plugin maintenance (`:75`) and posts `startDesktop` (`:76`).
   `ApplicationBootstrap.start` builds the `ConfigService` (`:320-323`) before calling the desktop callback (`:327`).
2. **Launch paths.** `startDesktop` exits for `--background` without residency (`:114-118`), serves `--no-window`
   (`:135-138`, `serveWithoutWindow` at `:413-428`, whose resident branch warms up at `:420`), opens the first window
   (`:139-143`), or quits or warms up a `--background` start (`:145-152`).
3. **Application start.** `JasperApplication` registers the bundled fonts (`:238`), captures the start-up view mode
   (`:240`), reads the theme library and selection (`:246-250`) and installs the theme with `ThemeController.startup`
   (`:251-252`) before `startPlugins(codeLocation, developmentDirectory, safeMode, dirs)` (`:843`) and `plugins.start`
   (`:906`). The app contributes Help as a top-level menu after plugins start (`:950`).
4. **Paint milestones.** The rehearsal bypasses `openWindow` and fires neither `FIRST_PAINT` nor `OUTPUT_PAINTED`
   (`JasperApplication.java:780-781`). `FIRST_PAINT` marks the first window's first terminal paint; `OUTPUT_PAINTED`
   needs session output, which a provider's connection never reports (`:1389-1401`). `Rehearsal.paint(Window)` is at
   `Rehearsal.java:216`.
5. **Resolution.** `PluginResolver.resolve(candidates, state, sdk, safeMode)` (`PluginResolver.java:21-58`) is pure.
   Safe mode rejects USER plugins only (`:26-29`). `admit` (`:65-77`) admits a non-USER plugin whose SDK range holds
   (`:66-68`) and that is not disabled or marked for removal (`:69-71`), and asks for consent for a USER plugin with no
   entry (`:72`). Cycle detection follows hard
   and optional edges (`:90-110`).
6. **Plugin state.** `Entry.DEFAULT` is `(true, {}, false)` (`PluginStateStore.java:34-35`). `transact` is a locked
   read-modify-write (`:67-82`). `enabling` always writes the entry (`:85-91`); `consenting` writes `(true, caps,
   false)` (`:94-96`); `removing` drops an entry equal to `DEFAULT` (`:102-109`).
7. **Three prune paths.** `PluginAdmin.remove(id, false)` skips the installed check and calls `removing`
   (`PluginAdmin.java:74-92`); its installed check also accepts pending staged ids (`:77-79`).
   `PluginInstaller.discardPending` deletes the entry of an id with no installed folder (`PluginInstaller.java:140-149`).
   `PluginMaintenance.removals` deletes the entry after removing the plugin (`PluginMaintenance.java:126-140`).
8. **Plugins page.** `PluginCatalog.compute` predicts the next start with `PluginResolver.resolve` (`:58-63`); a row's
   `enabled` is `entry == null || entry.enabled()` (`:94`); `restartNeeded` compares the prediction with the launch
   (`:100-101`). The card's toggle is on while `row.enabled() && !row.needsConsent()` (`PluginCard.java:101`); the
   details button reads "Disable" while `enabled()` (`PluginDetails.java:177-178`).
9. **Runtime start.** `PluginRuntime.start` discovers and resolves again (`:615-641`). Pre-start setters throw after
   start (`:356-393`). A load failure (`:671-678`) or a start failure (`PluginHost.java:158-170`) makes the plugin
   FAILED while the others run.
10. **Descriptor and capabilities.** `DescriptorParser.CAPABILITIES` (`:15-17`) and `KEYS` (`:21-23`) reject unknown
    entries. The SDK's `Capabilities` defines capabilities as "effects the user cannot see" (`Capabilities.java:5-8`);
    `ALL` is at `:42-43`. `JasperSdk.VERSION` is `0.17.1` (`JasperSdk.java:6`); the Editor's manifest declares
    `sdk = ">=0.17.1, <0.18"`, and the other seven bundled manifests and `docs/examples/external-plugin` declare
    `sdk = ">=0.17.0, <0.18"`.
11. **One-provider precedents.** `FileEditors.register` lets a bundled plugin replace a non-bundled editor
    (`FileEditors.java:64-77`); `Subcommands.choose` prefers a bundled declaration, then the first by id
    (`Subcommands.java:31-37`).
12. **Removed-member gate.** `KitCompatibility` refuses classes that reference exact removed SDK members or types
    (`KitCompatibility.java:12-88`); its message names SDK 0.15 (`:87`).
13. **Kit contexts.** `KitContext` has public constructors (`KitContext.java:18-27`) and `typography()` ignores the
    context (`:44-46`). The app builds contexts at `AppearanceBindings.java:23-25`, `HostedKit.java:57` and
    `PresentationIcons.java:23-25`, all with `DesignMetrics.DEFAULT` or a copy of it.
14. **Metrics.** `DesignMetrics` is final with one `DEFAULT` (`DesignMetrics.java:17`); `value(role)` returns the enum
    constant's fixed value (`:121-125`); `contentHeight` grows a role by the font's line height (`:143-146`). It has 560
    roles, frozen by `compact-roles.tsv` (`DesignMetricsTest.java:27-32`). Thirty reads in 16 files use
    `DesignMetrics.DEFAULT` directly, among them `FlatLafInstaller.java:42,81`, `NativeChromePainting.java:32`,
    `NativeControlStyles.java:60,86`, `IconRegistry.java:211,222`, `ThemeManager.java:16`, the static finals
    `ToolWindowArea.java:53,55`, `ToolWindowHeader.java:31` and `DockDrag.java:54`, two reads at
    `ToolWindowHeader.java:78`, and `AnimationSettings.java:16-18`.
15. **Typography.** The MONO roles resolve to `Font.MONOSPACED` (`Typography.java:46`); JBR needs an explicitly named
    medium face for Helvetica Neue's semibold (`:51-55`); role sizes scale by `bodySize/13` (`:54-57`).
16. **Renderer dispatch.** `ControlRegistry.create` is one exhaustive switch over the node types
    (`ControlRegistry.java:24-55`). The SDK's `Nodes.Node` permits 28 types (`jasper-sdk/.../ui/Nodes.java:12`).
17. **FlatLaf.** `FlatLafInstaller.install` takes its geometry from `DesignMetrics.DEFAULT` (`:41-67`, `:81-82`) and
    sets no title-pane key; `ThemeManager.install` adds the split divider from `DEFAULT` (`:16`) and
    `TitlePane.centerTitle` (`:19`, `:24`).
18. **Rollback and fallback.** `AppearanceTransaction.apply` restores and rethrows (`AppearanceTransaction.java:29-37`).
    `ThemeController.startup` catches `InstallationFailure` and installs Slate Cobalt Dark (`ThemeController.java:64-72`);
    the start-up transaction runs in the constructor (`:96`); `configure` runs its own (`:132`).
19. **Theme data.** `JasperTheme.OPTIONAL` is a `Map<SemanticColor, SemanticColor>` (`JasperTheme.java:49-51`); the
    palette parser rejects any key that is not a `SemanticColor` (`:184-189`) and any missing required role
    (`:190-193`); FlatLaf keys are baked into `GeneratedTheme` at parse time (`:175-176`). `SemanticPalette` requires
    every role (`SemanticPalette.java:12-21`); there are 70 roles (`SemanticPaletteTest.java:34`). `TitleBarTheme`
    copies the palette (`TitleBarTheme.java:31-54`) and caches by identity (`:18-23`). `SurfaceContrast` holds the
    TEXT 4.5 and INDICATOR 3 floors and `apart` (`SurfaceContrast.java:12-46`). The `*.selectionBackground` wildcard is
    the terminal selection (`UiColorKeys.java:47-50`).
20. **Theme catalog.** `ThemeLibrary` holds built-ins then imported themes (`ThemeLibrary.java:55-58`) and skips a
    folder file with a built-in id (`:104-108`); Import refuses built-in ids (`ThemeFolder.java:66-67`);
    `ThemeSelection.of` reports and replaces an unusable pick (`ThemeSelection.java:35-56`).
    `ColourLiteralScanTest` exempts only the built-in theme files (`ColourLiteralScanTest.java:14-16`).
21. **What `check` runs.** The jasper-ui gallery is an opt-in `JavaExec` for dark or light Slate Cobalt
    (`jasper-ui/build.gradle.kts:26-44`); `jasper-ui/build.gradle.kts` adds only javadoc to `check` (`:53`), and the
    gallery is not part of `check`. `mockUiPreview`, `settingsPreview`,
    `commandPalettePreview` and `titleBarPreview` are opt-in and "check never runs them"
    (`jasper-app/build.gradle.kts:120-153`). jasper-ui has no `testFixtures` source set.
22. **Gates.** `verifyUiArchitecture` forbids other Jasper modules, vendors in `theme`/`model`, and package cycles
    (`gradle/ui-architecture.gradle.kts:30-56`). `ThemeMigrationCoverageTest` needs a `migration.csv` row for every
    candidate source (`ThemeMigrationCoverageTest.java:17-18`) in jasper-app, the SDK, the testkit and plugins
    (`:62-77`), and every `plugins/<name>` must own a row as `dev.jasper.<name>` (`:85-91`). `verifyThirdPartyNotices`
    covers Maven modules and licence files in jasper-app and jasper-ui resources only
    (`gradle/third-party-notices.gradle.kts:13-19`). `declaredImports` lists each plugin's allowed foreign API packages
    (`gradle/plugin-architecture.gradle.kts:10`).
23. **Title bars.** `TitleBarStyle.choose` gives `NATIVE` for Compact (`TitleBarStyle.java:16-32`). Four call sites
    pass `MacTitleBar.isSupported()`: `TerminalWindow.java:66`, `NativeShells.java:146,227,419`. The native-title path
    is tested (`MacTitleBarTest.java:170`). Compact sets NSAppearance from the theme (`WindowContent.java:326-330`);
    the window title comes from `WindowHeaderContext` (`:311-320`).
24. **Auxiliary frames** get a minimal File menu with New Window, Close Window (⌘W/Ctrl+W) and, off macOS, Quit
    (`NativeShells.java:494-514`), set in the window when there is no unified title bar (`:148`).
25. **Window chrome.** `WindowChrome` builds File, Edit, View, Pane and Tab (`WindowChrome.java:66-91`), the Toolbar
    radio menu over `ToolbarMode` (`:92-97`), the right-packed toolbar (`:109-120`) and the Split dropdown
    (`:122-141`); Tools and plugin menus follow in registration order (`:246-283`). `WindowToolbar.doLayout` places
    only `JButton` and `JSeparator` children, packed right (`WindowToolbar.java:123-173`). `ToolbarMode` is
    `ICONS_AND_LABELS`, `ICONS`, `HIDDEN` (`ToolbarMode.java:4`; setting at `CoreSettings.java:37-41`).
26. **Dock.** `ToolStripe.Button` already carries the title (`ToolStripe.java:55-59`); `DockGeometry.Metrics` fixes
    6 px dividers (`DockGeometry.java:39-40`); the tab strip mounts above the deck at `WindowContent.java:247-252`.
    ui-state saves `[dock]` and `[panels."<id>"]` (`UiState.java:162-173`, `:268-271`). Notifications registers as
    "Notifications" at `RIGHT_TOP` (`NotificationsFeature.java:64`).
27. **Palette.** The reveal is gated by animations (`WindowCommandPalette.java:127-143`); the attach test treats
    anywhere at or above the home as home (`:289-293`); the home drops from the title field (`:375-380`); the saved
    place is a `(center_x, top)` fraction (`UiState.java:88-96`, `:151-154`, `:281-282`). `PaletteController` offers
    `RECENT_ROWS = 2` (`PaletteController.java:21`), heads them "Recently used" and "Most used"/"Suggested"
    (`:215-216`), shows a chosen scope's own section label (`:348`) and heads each scope by its label under a query,
    with "More in…" rows from a one-row probe (`:354-373`). `CommandPalette.listHeight` sums only the entries present
    (`CommandPalette.java:613-624`). `PaletteSettings.DEFAULT_VISIBLE_ROWS` is 5 in 1-20. The Commands scope labels
    its section "Recent" or "Suggested" (`CommandsScope.java:56`); Snippets' first verb is "Paste"
    (`SnippetsScope.java:24`); `ScopeSpec.monospaceRows` is documented as "the terminal font" (`ScopeSpec.java:20`).
28. **Settings defaults.** `SchemaPage` stages `Optional.empty()` when a value equals `setting.defaultValue()` and the
    editor removes at default (`SchemaPage.java:465-470`). `PlatformFontSize` treats the core default as "platform
    size" (`PlatformFontSize.java:12-27`). `SettingsDraft.value` shows a staged empty as the core default
    (`SettingsDraft.java:31-35`). `AppearanceSelection` compares and stages against core defaults
    (`AppearanceSelection.java:58-59`, `:155-159`). `ConfigSnapshot.from` collapses presence (`ConfigSnapshot.java:87-111`)
    although `SettingValues` tracks it (`SettingValues.java:33-42`). Settings integrates its title row
    (`SettingsDialog.java:174-177`). Host rows render `stringList` as a text area (`KitSettingRows.java:43-46`).
29. **Remote.** The "SSH" menu (`RemotePlugin.java:216-219`); Sessions at `Anchor.LEFT` (`:228`); Toggle SSH hosts
    `cmd+alt+1`/`alt+1` (`:263-265`) and Toggle SFTP `cmd+alt+2`/`alt+2` (`:266-269`); `activateHost` focuses a running
    pane for the host or opens one (`:368-375`). The Editor lookup sits in `RemotePlugin` itself (`:185-199`).
    `SessionsToolbar` lists WSL distributions, saved hosts and "Manage Sessions..." (`SessionsToolbar.java:56-90`).
    Transfers is `cmd+alt+3` (`TransferUi.java:40-42`). Tunnels anchor LEFT (`TunnelsUi.java:116`).
30. **File Server, Vault, Indexer.** TFTP's login is a one-option SEGMENTED choice (`ServerEditor.java:379-381`, `:417`);
    authorized keys are a COMPACT list (`:431-438`); the warning is DESCRIPTION text (`:451`); File Servers anchor LEFT
    (`FileServerPlugin.java:190`). Vault's status reads "Vault" with an icon (`VaultPlugin.java:159-160`, `:349-355`).
    Project Index's Location column uses BODY (`IndexerSettingsPanel.java:140`).
31. **Hosting.** `HostedUi.toolbar` translates Button and Dropdown only (`HostedUi.java:225-238`); `dialog` routes
    OVERLAY to the kit's modal overlay and the rest to native dialogs (`:720-750`); progress overlays at `:706-715`.
    `KitView.titleStrip` is the precedent for a kit view in native chrome (`KitView.java:319-330`).
32. **Other facts.** `docs/plugin-ui.md:57` says "settings never use checkboxes". `TerminalSession` writes UTF-8
    (`TerminalSession.java:80`). `JasperApplication.openFileAt` routes a file to the default editor
    (`JasperApplication.java:534-539`). IntelliJ's Classic UI plugin (`com.intellij.classic.ui`, IntelliJ 2026.2) is a
    marker jar whose `plugin.xml` contributes one empty extension; both UIs live in the platform and switching needs a
    restart.

## 3. Principles

1. **A marker and data; the code lives in the host.** Plugins never paint. `PluginPresentationCheck`,
   `verifyUiDistribution`, the JDK-only SDK and the signature gate are unchanged.
2. **Decide once, before Swing.** One immutable `Presentation` value is chosen on the bootstrap thread from descriptor
   data, replaced at most once by the fallback value before plugins start (4.7), and reaches every window, dialog and
   rehearsal. Switching looks means a restart.
3. **Strategies at the owner, not branches.** Each variant point is a pair of real implementations created in one
   factory per package: `KitFaces.of` (jasper-ui), `WorkspaceChrome.of` (workspace), `ShellChrome.of` (windows) and
   `PaletteViews.of` (palette). The `dialogs` and `motion` points are values that their named readers apply (9.1).
   Architecture tests fail on any other read of a variant (section 16), so the retro failure, where every chrome class
   branched on the style, cannot return.
4. **The theme owns colour; the kit owns shape.** Classic shapes paint existing roles or derived roles that have
   recipes. Guards repair contrast where a colour is painted and never change stored roles.
5. **Behaviour stays the same.** Actions, palette scopes, verbs and keys, the Settings draft and Apply, and the
   immediate Plugins page are identical in both looks. A look changes presentation, host copy, and the effective
   defaults of a closed list of settings that a saved value always overrides; under `motion = "none"`,
   `title_bar = "native"` and `tool_windows = "stripes"` it also ignores `ui.animations.enabled`,
   `window.native_decorations` and `window.rail_width`. Where a plugin
   composes differently, every action stays reachable (section 14.7).
6. **Composition belongs to its owner.** Where a mock changes layout (a tree table instead of rows), the owner branches
   once at its composition root: plugins on whether `Workstation` is published, app pages on the `Composition` value
   their factory receives. Renderers never branch on a look.
7. **A small closed schema over a catalog the host owns.** The kit file selects a host catalog and tunes 11 public
   tokens. Unknown keys fail, and an invalid file means the default look.
8. **No motion under Enterprise, and the header stays flush.** The native title, menu bar and toolbar rows are
   separated by 1 px rules with no gutters.
9. **The vocabulary grows semantically, and the default look holds still.** New SDK nodes state intent and both looks
   render them; the default look stays pixel-identical except for the approved changes of section 13.

## 4. The presentation provider

### 4.1 Descriptor

The Enterprise plugin's `plugin.toml`:

```toml
id = "dev.jasper.enterprise"
name = "Enterprise"
version = "0.2.0"
entry = "dev.jasper.enterprise.EnterprisePlugin"
sdk = ">=0.18.0, <0.19"
description = "The Enterprise look: native title bars, menus in the window, labelled tool window stripes, a Run window and classic controls, in the Enterprise theme or any other."
vendor = "Jasper"
icon_hue = "blue"
icon_selected_ink = "foreground"
icon = "icons/enterprise.svg"
enabled_by_default = false
capabilities = ["presentation.provide"]
exports = ["dev.jasper.enterprise.api"]
presentation = "presentation/enterprise.kit.toml"
themes = ["themes/enterprise.json"]
```

Three keys join `DescriptorParser.KEYS`:

| Key | Type | Rule |
|---|---|---|
| `presentation` | string | A jar resource path to a kit file. Legal only with the `presentation.provide` capability, and the capability is legal only with this key. |
| `themes` | array of strings | Jar resource paths to Jasper theme files. Any plugin may declare it, with no capability: the files are data checked by the strict theme loader. A theme's id is its file name without `.json`. |
| `enabled_by_default` | boolean | Defaults to `true`. `false` keeps a BUNDLED plugin off until the user turns it on; DEV and USER plugins ignore it (a DEV plugin is an explicit per-launch request, a USER plugin needs consent anyway). |

Paths are relative to the jar root, use only `[a-z0-9/_.-]`, contain no `..` and no leading `/`, and must name an
entry in the plugin's jars. The descriptor gains `presentation()`, `themes()` and `enabledByDefault()`.

### 4.2 The `presentation.provide` capability

`Capabilities.PRESENTATION_PROVIDE = "presentation.provide"` (`@since 0.18.0`) joins `Capabilities.ALL` and
`DescriptorParser.CAPABILITIES`. It is modelled on `editor.provide` (one active provider) and its effect needs no
runtime gate: the host reads the presentation from the descriptor and the plugin calls nothing. The `Capabilities`
class javadoc becomes: "The capability names a plugin declares in `plugin.toml` and the user consents to. They cover
effects the user cannot see, and the roles one plugin at a time may take over (`editor.provide`,
`presentation.provide`); contributions to the window need none. They are not a sandbox." The consent text in
`pluginmanager.Capabilities.describe` is "Change Jasper's window chrome and controls after a restart". BUNDLED and DEV
plugins need no consent; USER plugins do.

### 4.3 Bundled but off by default

The effective-enabled rule lives in one place, `PluginStateStore.effective(Entry entry, PluginCandidate candidate)`:
enabled unless the entry says disabled, or there is no entry and the candidate is BUNDLED with
`enabled_by_default = false`. Its callers are `PluginResolver.admit` (an off-by-default bundled plugin is DISABLED,
"off by default"), the `PluginCatalog` row (replacing `entry == null || entry.enabled()` at `PluginCatalog.java:94`),
`PluginCard` and `PluginDetails` labels through the row, and `PluginAdmin.setEnabled`. So Enterprise shows unchecked,
its details button reads "Enable", and one click writes the opt-in and shows the existing restart banner.

The opt-in is stored explicitly so that it never equals `Entry.DEFAULT`: `Entry` gains `boolean optIn`, written
`opt_in = true` by `PluginAdmin.setEnabled(id, true)` for a BUNDLED off-by-default plugin and cleared by any write that
disables. `consenting` writes `optIn = false`, and `admit` treats a USER candidate whose entry carries `optIn` as not
reviewed yet, because only bundled plugins are ever opted in. The three prune paths keep it:

- `removing` drops only entries equal to `DEFAULT`, which an opted-in entry never is (`PluginAdmin.remove(id, false)`);
- `PluginInstaller.discardPending` keeps an opted-in entry and clears its consent and removal mark;
- `PluginMaintenance.removals`, reached through the pending check at `PluginAdmin.java:77`, keeps an opted-in entry
  as `(true, {}, false, opt-in)` after it removes the USER copy.

A USER copy installed from the Plugins page is committed with consent (`PluginInstaller.java:121-128`), which replaces
the opt-in, so removing that copy leaves the bundled plugin off until the user turns it on again; a zip dropped into the
folder is committed without consent (`PluginMaintenance.java:108`) and keeps it.

The opted-in entry in `plugins.toml`:

```toml
version = 1

[plugins."dev.jasper.enterprise"]
enabled = true
consented = []
remove = false
opt_in = true
```

### 4.4 One active presentation

Presentation selection lives inside `PluginResolver.resolve`, which stays pure and takes a new argument,
`Presentations(Map<String, PresentationFacts> facts, AttemptRecord attempts, String build)`, so the launch and
`PluginCatalog`'s "after restart" prediction run the same rule on the same data. `PresentationFacts(String digest,
Optional<String> problem)` is computed for every candidate that declares a presentation by
`PresentationFacts.read(byte[] kit, List<byte[]> themes, Function<String, Optional<byte[]>> resources)`, where
`resources` reads a path from the candidate's jars (supplied by `dev.jasper.app.plugins`, so the `presentation` package
never sees a plugin type): `digest` is the SHA-256 of the kit file, then each declared theme file, then each font file,
in declared order; `problem` is the first kit validation failure (section 5.9). `PluginSelection` (4.8) and
`PluginAdmin.disk()` both call it.

After admission, cycle removal and unmet-dependency removal, the candidates are the loadable plugins that declare a
presentation and have no problem. The winner is chosen by origin, DEV first, then BUNDLED, then USER, and within an
origin by the lowest plugin id. Losers are SKIPPED whole with "the <winner name> look is active", so a provider that
cannot load never wins and the presentation stands or falls with its plugin. Like superseded copies, losers are left
out of the plugin problems (`PluginRuntime.java:643-646`), so they post no notice; the Plugins page shows the reason. A
candidate with a problem is SKIPPED with "Presentation invalid: <message>". Attempt-record skips (4.6) are applied
after the winner is chosen: a skipped winner leaves the standard look, and the other providers stay SKIPPED as losers.
The resolution gains `Optional<PluginCandidate> presentation()`.

Enabling or consenting to a presentation provider disables every other enabled provider in the same locked
`plugins.toml` transaction (`PluginStateStore.enablingPresentation(String id, Set<String> others)` and
`consentingPresentation(String id, Set<String> capabilities, Set<String> others)`). The
snapshot gains `Optional<String> turnedOff`, and the restart banner reads "Restart Jasper to apply your changes.
<Other> was turned off: one look can be active at a time."

### 4.5 Safe mode

`--safe-mode` also rejects every plugin that declares a presentation, whatever its origin, as DISABLED with "safe mode
uses the standard look". The standard look is then guaranteed. Safe mode still rejects USER plugins as today.

### 4.6 The attempt record

The launcher's watcher covers JVMs that never start; a JVM that fails later is never retried (start-up recovery
§5.2). The Java side therefore keeps `<home>/presentation-attempt.toml`, read and written with a `FileLock`
read-modify-write like `PluginStateStore`'s (its own lock file), keyed by provider:

```toml
version = 1

[providers."dev.jasper.enterprise"]
version = "0.1.0"
digest = "9f2c4e0b7a1d3c5e8f6a0b2d4c6e8f0a1b3d5c7e9f1a2b4c6d8e0f1a3b5c7d9e"
build = "/Applications/Jasper.app/Contents/app/jasper-app.jar@1760500000000"
state = "pending"
```

`build` is the application's code location and its modification time, the identity `ApplicationBootstrap.stale`
compares (`:446-448`). `state` is `pending`, `proven` or `failed`. The store, `AttemptStore`, lives in
`dev.jasper.app.presentation`.

- **`pending`** is written only on the paths about to show a window or rehearse: in `startDesktop` just before
  `application.newWindow()` and before the resident `--background` warm-up when not headless. It is never written for
  `--no-window` (including its resident warm-up), the background exit paths, a headless start or `quitNow`. Unlike
  `PluginStateStore`'s `transact`, which never runs on the EDT (`PluginStateStore.java:28-32`), the `pending` write runs
  on the EDT, bounded by a 250 ms lock wait; when the lock is busy the launch proceeds without a record and logs it.
- **`proven`** is written, off the EDT, at the first window's `FIRST_PAINT`, or when `Rehearsal.paint` returns. It is
  never tied to `OUTPUT_PAINTED`, which a provider connection or a silent shell never fires.
- An orderly quit before proof (`quitNow` or the quit action) marks the quit as orderly; the shutdown hook clears
  `pending` only when that mark is set. Any other exit, including `System.exit` with a nonzero status
  (`ApplicationBootstrap.java:44`), which also runs the hook (`:66`), leaves `pending`.
- **`failed`** is written when the kit is blamed at start (4.7) or when the provider fails to load or start (its
  `PluginStatus` is FAILED). In that last case the current session keeps the classic chrome with no `Workstation`:
  Remote and File Server compose their default layouts, which is a reachable, tested state. `EnterprisePlugin.start`
  only publishes `Workstation`, and a test pins that it cannot throw.
- At start, a `pending` or `failed` record whose digest and build match makes the resolver SKIP the provider with
  "did not finish starting with this look last time". `PluginRuntime.start` leaves this SKIPPED status out of the
  plugin problems, so the Warning below is the only notice. Jasper posts one Warning notification, "Jasper started
  with its standard look", text "The last start with the <Name> look did not finish, so Jasper uses its standard
  look until you try again.", with the action **Try Again**, which clears the record and restarts Jasper.
- A different digest or build always gets a fresh try.

### 4.7 Fallback at start

`ThemeController.startup(dark, selection, kit, installer, fallingBack)` tries, in order:

1. the user's selection under the chosen kit;
2. the same selection under `Kit.DEFAULT`; when this works the kit is blamed: the record says `failed`;
3. Slate Cobalt Dark under the chosen kit (the theme was at fault, as today);
4. Slate Cobalt Dark under `Kit.DEFAULT`, blaming the kit; a failure here propagates, as today's fallback's does.

With the default kit, steps 2 and 4 do not exist and the behaviour is today's. Blaming the kit replaces the chosen
`Presentation` with one explicit fallback value, `Presentation.fallback(DesktopFlags committed)`: the native title and
in-window menus already committed before AWT, with the default kit; 9.1 gives the value of every projection field. The
fallback is handed to every consumer before `startPlugins`, and the provider is marked SKIPPED in the `PluginSelection`
so no `Workstation` is published. `ThemeController.startup` returns which step succeeded, and `JasperApplication`
records `failed` through `AttemptStore` (the `appearance` package has no `presentation` edge). The fallback session
keeps the `ConfigService`'s `DefaultSource` and the plugin theme tier as read at start; the next start, with the
provider skipped, uses `DefaultSource.CORE`. Strategy construction (`WorkspaceChrome.of` and the others) must be
infallible and is tested; there is no rollback around it.

### 4.8 Bootstrap: `PluginSelection`

A new pure pre-pass, `PluginSelection` in `dev.jasper.app.plugins` (a public type there, beside `PluginRuntime` and
`GlassSource`), runs on the bootstrap
thread inside `ApplicationBootstrap.start`, after the handoff checks (`:311-319`) and before `new ConfigService(...)`
(`:320-323`):

1. `JasperApplication.preparePlugins(dirs)` moves here from `prepareDesktop` (`:75`). It touches no AWT. `AppLog.open`
   (`:55`) moves with it, before `PluginSelection.of`, so maintenance and selection still log to the Jasper log file.
2. `PluginSelection.of(dirs, codeLocation, developmentDirectory, safeMode, sdk)` discovers bundled, user and
   development plugins, reads the plugin state and the attempt record, reads the bytes of every declared presentation,
   theme and font file, computes `PresentationFacts`, resolves, and parses and validates the winner's kit with the
   production `KitParser`. It touches no AWT.
3. `new ConfigService(file, mac, selection.presentation().defaults())`: the service takes the injected `DefaultSource`
   (section 5.8), so the first snapshot already carries the kit's defaults.
4. In `prepareDesktop`: `configureDesktopProperties(flags)` sets `apple.laf.useScreenMenuBar` to `false` when the
   look's menus are in the window; `TitleBarStyle.choose(mac, nativeDecorations, compact, flags.nativeTitle())`
   gives `NATIVE` on every platform for a native title; then built-in icons and `startDesktop` as today.

`startPlugins(PluginSelection selection, AppDirs dirs)` and `PluginRuntime.start` consume this one selection: there is
no second scan, and a `plugins.toml` edited during start-up cannot load a plugin set that disagrees with the chrome
already committed. `PluginRuntime.start` still prepares a settings file for every discovered candidate in the
selection, loaded or not (`PluginRuntime.java:625-628`).

### 4.9 Reaching every surface

`Presentation` is an app-native value in the new package `dev.jasper.app.presentation`. It holds the jasper-ui `Kit`,
the parsed `ChromeSpec`, the provider's id and name, and the projections of section 9. `JasperApplication` captures it
beside `startupViewMode` (`:240`), from the `PluginSelection` the bootstrap hands it:

- **Fonts:** the provider's font files are registered right after `BundledFonts.register()` (`:238`), before
  `Fonts.families` caches its list.
- **Themes and FlatLaf:** `ThemeLibrary` is built with the plugin theme tier (7.4); `ThemeController.startup` passes
  the kit to `ThemeManager.install(theme, kit)`, which passes it to `FlatLafInstaller.install(theme, kit)` and sets
  `TitlePane.centerTitle`, which is inert under a native title bar.
- **Kit contexts:** every context is built with `KitContext.of(kit, …)` (6.3). The kit reaches `AppearanceBindings`,
  `PresentationIcons` (from its source context) and `HostedKit`, through a new pre-start setter
  `PluginRuntime.presentation(Kit kit)` beside `appearance()`; plugin dialogs meet the `DialogMapping` in
  `AuxiliaryWindows`, which is built with it; motion reaches plugins through the existing animations supplier (10.9).
- **Windows and dialogs:** `composeWindow`, reopen and hand-off, the `--background` rehearsal (`:760-829`),
  `NativeShells`, `AuxiliaryWindows`, `HostedUi` and `SettingsDialog` receive their projections at construction.

Nothing reads the presentation after bootstrap except through these values. `PresentationReachTest` (16.1) pins it.

### 4.10 AOT and start-up speed

Enterprise is off by default, so AOT training records the default look; the cache stays valid under Enterprise because
the class path and flags do not change, and the classic classes load from the app jars on a cold start.
`StartupTrace` reports `look=<provider id or default>`, so the user can measure the cost. Training the classic
strategies waits for that measurement. The pre-built window and the contribution-replay cache of the start-up speed
design are keyed by presentation id and digest, and `ToolbarItem.View` entries are never replayed.

## 5. The kit file

### 5.1 Format

A kit file is TOML 1.0, one key per line, parsed by `dev.jasper.app.presentation.KitParser` with tomlj in the strict
style of `DescriptorParser`. Paths inside it are relative to the kit file's directory, lowercase, `[a-z0-9/_.-]`, with
no `..`. The parser produces the jasper-ui value `dev.jasper.ui.kit.Kit` and the app value `ChromeSpec`. jasper-ui
knows no file format; the default look is not a file but `Kit.DEFAULT` and `ChromeSpec.DEFAULT`.

The shipped `presentation/enterprise.kit.toml`:

```toml
kit = 1
base = "classic"

[chrome]
title_bar = "native"
menu_bar = "window"
window_title = "Workstation — {session} — {directory}"
tool_windows = "stripes"
editor_tabs = "classic"
status_bar = "cells"
palette = "run_window"
dialogs = "classic"
motion = "none"
icons = "classic"

[controls]
toggle = "checkbox"
exclusive = "radios"
tabs = "folder"
group = "titled_box"
form_label = "colon"
empty = "text"
table = "classic"
tree = "classic"
stepper = "spinner"
ordered_list = "side_column"
buttons = "classic"
tag = "boxed"
route = "edged"
prompt = "plain"

[metrics]
control_height = 22
row_height = 22
control_radius = 3
field_radius = 2
group_radius = 3
focus_ring = 2
tool_window_header = 24
group_padding = 12
dependent_indent = 20
navigation_width = 230
status_dot = 7

[type]
base = 12

[type.ui]
family = "IBM Plex Sans"
regular = "fonts/ibm-plex-sans-regular.ttf"
medium = "fonts/ibm-plex-sans-medium.ttf"
semibold = "fonts/ibm-plex-sans-semibold.ttf"

[type.data]
family = "IBM Plex Mono"
regular = "fonts/ibm-plex-mono-regular.ttf"
medium = "fonts/ibm-plex-mono-medium.ttf"

[colors]
dialog_surface = "surface.app"
navigation_surface = "surface.card"

[branding]
product = "JasperOps"
icons = "icons"
splash_art = "splash/art.svg"

[defaults]
"ui.theme.light" = "enterprise"
"ui.theme.dark" = "enterprise"
"palette.visible_rows" = 8
"window.toolbar_height" = 34
"window.toolbar" = "icons"
```

Top-level keys: `kit` (required, the integer 1), `base` (required, `"default"` or `"classic"`, the host catalog that
supplies every private role table and a default for every variant point), and the tables `chrome`, `controls`,
`metrics`, `type`, `colors`, `branding` and `defaults`, each optional. A key left out takes the base's value.
`branding` has no base value: a look without it keeps Jasper's icon and shows no splash (10.11). `icons` names a
directory holding `icon-16.png`, `icon-32.png`, `icon-48.png`, `icon-64.png`, `icon-128.png`, `icon-256.png` and
`icon-1024.png`; `splash_art` names a 718 × 250 SVG; `product` is the name the splash shows. Enterprise lists every
key so the file can be reviewed; `PresentationConformanceTest` pins the list.

### 5.2 Variant points

Twenty-four points: 10 chrome points and 14 control points, all two-valued except the `window_title` template. There
is no `toolbar` point: the classic
header honours the existing `window.toolbar` setting (10.3).

| `[chrome]` key | Default | Enterprise | What the Enterprise value does |
|---|---|---|---|
| `title_bar` | `unified` | `native` | The OS title bar on every platform (`TitleBarStyle.NATIVE`); the workspace header becomes a chrome row under it (10.1) |
| `menu_bar` | `system` | `window` | macOS: `apple.laf.useScreenMenuBar=false`, so each window's menu bar is a row in the window; elsewhere menus already sit in the window |
| `window_title` | `"{context}"` | `"Workstation — {session} — {directory}"` | The frame title template (10.4) |
| `tool_windows` | `rails` | `stripes` | Labelled stripes, the bottom strip and header strips (10.7), whatever `window.rail_width` says |
| `editor_tabs` | `equal` | `classic` | Content-sized, always-visible tabs with a status dot, ×, "+" and an underline (10.5) |
| `status_bar` | `segments` | `cells` | Ruled status cells (10.6) |
| `palette` | `anchored` | `run_window` | The Run window and the leading "▶ Run…" status item (section 11) |
| `dialogs` | `as_requested` | `classic` | OVERLAY and ATTACHED_SHEET dialogs are drawn in their window as classic dialogs (10.8) |
| `motion` | `standard` | `none` | No motion, whatever `ui.animations.enabled` says (10.9) |
| `icons` | `new_ui` | `classic` | The workspace chrome's icons: IntelliJ's classic icons in the header and status bar (10.3) |

| `[controls]` key | Default | Enterprise | What the Enterprise value does |
|---|---|---|---|
| `toggle` | `switch` | `checkbox` | A 13 px checkbox leading its label, the description under the label, no knob motion |
| `exclusive` | `segments` | `radios` | An EXCLUSIVE choice as a row of 14 px radios; `Option.hue` as a swatch; PREVIEWS as a thumbnail above each radio, the selected one in a 2 px accent frame |
| `tabs` | `segments` | `folder` | A TABS choice as folder tabs: the active tab white and joined to the content |
| `group` | `card` | `titled_box` | Section + Card as one 1 px box whose semibold title breaks the top border |
| `form_label` | `plain` | `colon` | FormRow labels in a left-aligned label column as wide as the card's widest label, each ending with a colon; a FormRow caption sits under its label in the muted description face and may wrap; a trailing PORT field gets its own "Port:" label |
| `empty` | `tile` | `text` | An Empty state as a bold line, one sentence and at most one button, without the tile |
| `table` | `plain` | `classic` | A header band with 11 px titles and 1 px column separators, zebra rows, full-width square selection |
| `tree` | `plain` | `classic` | ▶/▼ disclosure, 24 px child indent, medium-weight category rows with a muted count on the right, full-width square selection |
| `stepper` | `stepper` | `spinner` | A right-aligned data-face value, a muted unit and stacked ▲/▼ arrows; SLIDER draws the same |
| `ordered_list` | `inline` | `side_column` | A muted data-face index column and a side column of equal buttons, `addAction` first, then Remove, Move Up, Move Down, acting on the selection |
| `buttons` | `raised` | `classic` | A top-to-bottom fill from `button.secondary.bg` to `button.end`, a border that darkens on hover; the primary filled with the accent, an `accent.edge` border and a medium label; no inset line or drop. Dropdown and picker faces follow buttons, without icon tiles and ending with ▼; SEARCH and PATH fields lose their leading glyph (6.4) |
| `tag` | `tint` | `boxed` | A Chip as a tiny uppercase label in a 1 px `tag.border` box on the pale hue tint |
| `route` | `tiles` | `edged` | ROUTE steps as bordered cells with a hue left edge, an uppercase caption and → arrows |
| `prompt` | `tile` | `plain` | PROMPT forms without the 54 px icon tile and hero title: the title goes to the dialog's header strip (10.8), and a 30 px accent tile with a white glyph sits beside the message |

Looks that need no point come from the catalog alone: square panels, dialogs and popups (their radii), keycaps (face
`surface.tableHeader`, edge `border.control`, a thicker bottom edge in `border.hover`, data face), the joined
Back/Forward pair, notification cards (square, 1 px border, the catalog's popup shadow) and the dock rule widths.

### 5.3 Constraints

`menu_bar = "window"` requires `title_bar = "native"`: the unified row hosts the header and leaves an in-window menu row
no home. It is the only cross-field constraint. The `palette` point alone decides whether the "▶ Run…" status item
exists, under either status layout.

### 5.4 Public tokens

The 11 `[metrics]` tokens fan out to private roles through a host-owned table in `dev.jasper.ui.metrics.MetricTokens`;
`DesignMetrics.with(MetricTokens)` applies it to the base catalog. The ranges for `control_height` and `row_height` come
from the approved design and are binding; plan 4a calibrated the others against the mocks, which draw only
Enterprise's value of each token, inside its range, so they stand as listed, and `PresentationConformanceTest` (plan 5)
freezes them.

| Token | Enterprise | Range | Private roles it sets |
|---|---|---|---|
| `control_height` | 22 | 20-28 | `BUTTON_HEIGHT`, `FIELD_HEIGHT`, `PICKER_DEFAULT_HEIGHT`, `FULL_FOOTER_BUTTON_HEIGHT`, `JOINED_ACTION_HEIGHT`, `PANEL_EMPTY_ACTION_HEIGHT`, `PANEL_SEARCH_HEIGHT`, `COLUMN_SEARCH_HEIGHT`, `KEYMAP_SEARCH_HEIGHT`, `FULL_SETTING_CHOICE_HEIGHT`; `FIELD_VERTICAL_PADDING` and `BUTTON_VERTICAL_PADDING`, each `max(2, (control_height − line height) / 2)` per side, where the line height is the base face's (16 for a 12 px face, host-calibrated), so 3 for Enterprise; FlatLaf `Button.minimumHeight` and `Component.minimumHeight` |
| `row_height` | 22 | 18-28 | `MENU_ITEM_HEIGHT`, `PICKER_ROW_HEIGHT`, `RESULTS_ROW_HEIGHT`, `RESULTS_HEADING_HEIGHT`, `KEYCAP_TREE_ROW_HEIGHT`, `KEYCAP_TREE_GROUP_HEIGHT`, `ORDERED_ROW_HEIGHT`, `SETTINGS_NAV_ROW`, `SETTINGS_NAV_HEADING`, `COLUMN_RAIL_ROW`, `PANEL_COMPACT_ROW_HEIGHT`, and a new `ROW_HEIGHT` that becomes the generic list, table and tree row floor (today that floor reuses `BUTTON_HEIGHT`) |
| `control_radius` | 3 | 0-4 | `CONTROL_RADIUS`, `SEGMENT_TRACK_RADIUS`, `SEGMENT_ITEM_RADIUS`, `PICKER_SEARCH_RADIUS`, `KEYCAP_RADIUS`, `CHIP_RADIUS`, `ORDERED_RADIUS`; FlatLaf `Component.arc` and `Button.arc` (twice the radius) |
| `field_radius` | 2 | 0-4 | A new `FIELD_RADIUS` (fields today use `CONTROL_RADIUS`), `TRANSPARENT_FIELD_RADIUS`; FlatLaf `TextComponent.arc` |
| `group_radius` | 3 | 0-6 | `CARD_RADIUS` (titled boxes and plain cards) |
| `focus_ring` | 2 | 1-3 | `FOCUS_RING`; FlatLaf `Component.focusWidth` |
| `tool_window_header` | 24 | 22-32 | `NATIVE_TOOL_HEADER_HEIGHT` (app headers) and `PANEL_HEADER` (borrowed kit headers) |
| `group_padding` | 12 | 8-16 | `CARD_PADDING`, `FULL_SETTING_SIDE_PADDING`, `SETTINGS_ROW_SIDE` |
| `dependent_indent` | 20 | 12-28 | `SETTING_DEPENDENT_INDENT` |
| `navigation_width` | 230 | 180-300 | `SETTINGS_SIDEBAR_WIDTH` |
| `status_dot` | 7 | 5-9 | `STATUS_DOT`, `PANEL_ROW_DOT`, `TOOL_HEADER_ACTIVE_DOT` |

### 5.5 Private catalog

Everything else in the 560-role catalog is private: `DesignMetrics.CLASSIC = DEFAULT.with(classic delta)`, frozen as
`classic-roles.tsv` beside `compact-roles.tsv`. It includes the new roles `RUN_WINDOW_WIDTH` (460), `CHECKBOX_BOX`
(13), `RADIO_RING` (14), `RADIO_DOT` (6), `GROUP_TITLE_INSET`, `DOCK_RULE` (1) and `DOCK_RULE_HIT` (6), and the stripe
label metrics (`STRIPE_LABEL_PADDING`, `STRIPE_LABEL_GAP`), plus classic values for existing roles: `PALETTE_WINDOW_MARGIN`
8, `WORKSPACE_GUTTER` 0, and 0 for `WINDOW_RADIUS`, `WORKSPACE_SURFACE_ARC`, `PALETTE_CORNER_ARC`, `DOCK_DROP_ARC`,
`RAIL_BUTTON_ARC`, `NATIVE_TOOLBAR_HOVER_ARC`, `TITLE_SEARCH_ARC` and `PROMPT_TILE_RADIUS` (today `HERO_TILE_RADIUS`,
renamed in plan 3). Plan 4 calibrates `GROUP_TITLE_INSET` and the stripe label metrics against the mocks. New roles
join `compact-roles.tsv` too; their `DEFAULT` values are `ROW_HEIGHT` = `BUTTON_HEIGHT`, `FIELD_RADIUS` =
`CONTROL_RADIUS`, and the classic values for roles that only classic faces read. Exposing these would freeze
them as an ABI. A third-party look picks a `base` and tunes the public tokens; a value it needs beyond them is promoted
into the public set in a later SDK minor.

### 5.6 Type and faces

The classic catalog carries a 12-based type scale, `dev.jasper.ui.metrics.TypeScale.CLASSIC`, frozen as
`classic-type.tsv`; `TypeScale.DEFAULT` is today's 13-based table. A `TypeScale` holds the role table, its base and the
kit's standard body size (`[type].base`). A role's size is its scale value times the body size over the scale's base.

| Roles | Classic size / weight |
|---|---|
| `BODY`, `LABEL`, `SETTINGS_BODY`, `PANEL_SEARCH`, `TITLE_CONTEXT`, `TOOLBAR_LABEL`, `STATUS_PATH`, `MONO` | 12 / 400 |
| `DESCRIPTION`, `SUPPORTING`, `CAPTION`, `SETTING_DESCRIPTION`, `COUNT`, `RESULTS_HEADING`, `PALETTE_HINT`, `PANEL_HEADER_COUNT`, `STATUS_TEXT`, column headers, `MONO_SMALL`, `MONO_CAPTION` | 11 / 400 |
| `PANEL_TITLE`, `PANEL_HEADER_TITLE`, `WINDOW_TITLE`, `SECTION_LABEL` (group titles, no tracking) | 12 / 600 |
| `BODY_STRONG`, `PANEL_ROW_SELECTED`, the primary button label, tree category rows | 12 / 500 |
| `DETAIL_TITLE`, `PROMPT_TITLE` | 15 / 600 |
| `CHIP` (tags, uppercase) | 10 / 500 |

Column headers, the primary button label and tree category rows are uses, not roles: the classic faces draw them at
their row's size and weight. A role the table does not list keeps its default size scaled by 12/13 and its default
weight until plan 4 calibrates it against the mocks; `classic-type.tsv` records every role.

Faces name a file for each weight, because JBR does not pick the weights of static faces through `TextAttribute.WEIGHT`
(the Helvetica Neue lesson at `Typography.java:51-55`). `dev.jasper.ui.metrics.FaceSet` holds the UI face (regular,
medium, semibold) and the data face (regular, medium) by registered face name; `FaceSet.PLATFORM` is today's policy with
`Font.MONOSPACED` as the data face. The pre-pass only checks the font files (4.8 touches no AWT); the application
registers each one with `GraphicsEnvironment.registerFont` right after `BundledFonts.register()` and checks that its
family is the declared `family` (4.9). The data face replaces `Font.MONOSPACED` (`Typography.java:46`) for the MONO
roles, `FieldPresentation.TECHNICAL`, PORT and PATH fields, palette details, keycaps, spinner values and ordered-list
rows. Under the classic scale it also draws Breadcrumbs crumbs and the status bar's grid-size cell; the default scale
keeps their faces. The mocks' data face in Project Index's Last indexed column and in Add Host's Username placeholder is
a fixture. The terminal font is untouched.

`ui.font.family = "system"` (today's default) means the look's standard UI face: the platform face under the default
look, IBM Plex Sans under Enterprise; a named family overrides it. `ui.font.size` left unset means the look's standard
size: the platform size under the default look, `[type].base` (12) under Enterprise; a set size replaces `[type].base`
as the body size, so it scales every role by size / `[type].base`. Font settings are not kit defaults (5.8).

### 5.7 Colour slots

Two slots, each with a closed set of allowed roles:

| Slot | Allowed | Default | Enterprise | Mock |
|---|---|---|---|---|
| `dialog_surface` | `surface.dialog`, `surface.app` | `surface.dialog` | `surface.app` | dialog bodies on window chrome |
| `navigation_surface` | `surface.sidebar`, `surface.card` | `surface.sidebar` | `surface.card` | the Settings navigation tree on white |

Everything else a classic face paints is fixed inside the face as a role or a derived role (section 7), never kit
data.

### 5.8 Defaults for settings

The precedence is **user-saved, then kit default, then core default**, for an allowlist of five keys: `ui.theme.light`,
`ui.theme.dark`, `palette.visible_rows`, `window.toolbar_height` and `window.toolbar`. A kit default must pass the
setting's own kind check (`palette.visible_rows` 1-20, `window.toolbar_height` 28-64), and a theme must be a built-in or
one of the providing plugin's own themes with the variant its key needs (5.9). A declared theme file that fails to load
is dropped from the `DefaultSource` (the core default applies) and named in the plugin's problem notice.

`DefaultSource` is a value in `dev.jasper.app.config`, `DefaultSource(Map<Setting<?>, Object> overrides)` with
`DefaultSource.CORE` empty and `<T> T value(Setting<T>)`. `ConfigService` takes it at construction (4.8), and every
snapshot, reload and Settings draft baseline uses it: `ConfigSnapshot.from(values, defaults, …)` reads an allowlisted
key as `values.saved(setting).orElse(defaults.value(setting))`. `ConfigSnapshot.defaults()` becomes
`ConfigSnapshot.defaults(DefaultSource)`: the missing-file state (`ConfigService.java:429`), the initial state (`:118`)
and the syntax-error result (`ConfigLoader.java:80`) use it, so a fresh home's first snapshot carries the kit's
defaults.

Every comparison with a core default is audited:

| Site | Outcome |
|---|---|
| `ConfigService.java:118`, `:429`, `ConfigLoader.java:80` (`ConfigSnapshot.defaults()`) | `ConfigSnapshot.defaults(DefaultSource)` with the injected source |
| `SchemaPage.java:465-470` (`removesAtDefault`) | Compares with `draft.defaultValue(setting)`, the effective default |
| `SchemaPage.java:511-512` (`resettable`) | Compares with `draft.defaultValue(setting)`, the effective default |
| `SettingsDraft.java:31-35` | A staged empty shows the effective default; the draft gains `defaultValue(Setting)` |
| `SettingsDraft.java:32`, an unstaged key, which reads `baseline.get` and so `setting.defaultValue()` for an unsaved key (`SettingValues.java:28-31`) | Reads `baseline.saved(setting).orElse(defaultValue(setting))`, so an unsaved allowlisted key shows the effective default |
| `AppearanceSelection.java:58-59`, `:155-159` | `modified` and `staged` use the effective defaults of the theme keys; picking the core default while a kit overrides it stages an explicit value |
| `PlatformFontSize.java:12-27` | Compares with `draft.defaultValue(setting)`, the effective default (equal to the core default for a key outside the allowlist); unset now means the look's size, and its label comes from `SettingsChrome.fontRowCopy` (12.1) |
| `Editors.java:523` (`OptionalNumber`), `WorkspaceEditors.java:37,54`, `NotificationGroupsEditor.java:132` | Compare with `draft.defaultValue(setting)`, the effective default (equal to the core default for a key outside the allowlist); a test pins that no allowlisted key reaches a core-default comparison |

Reset stages `Optional.empty()`, which now means the effective default, and Reset shows while the value differs from
it. `config.example.toml` and `docs/configuration.md` say for the five keys: "The default depends on the active look."
A test starts a fresh home under Enterprise and checks that the first window opens with the kit's defaults.

### 5.9 Validation and limits

Validation is strict and all-or-nothing. The kit file is at most 64 KiB; fonts are `.ttf` or `.otf`, at most 2 MiB
each and at most 8 files. A kit is rejected for:

- an unknown table or key, a wrong type, an unknown value, or `kit` other than 1;
- a token outside its range, or a `[type].base` (the body size in points) that is not an integer from 10 to 16;
- `menu_bar = "window"` without `title_bar = "native"`;
- a `[defaults]` key outside the allowlist, or a value its setting rejects;
- a `[defaults]` theme id that is neither a built-in id nor a theme file the plugin declares;
- a path that breaks the path rule, a missing file, or a font file that is not `.ttf` or `.otf`;
- a `window_title` token other than `{context}`, `{session}` and `{directory}`;
- a `[branding]` icon that is missing, not a PNG of its stated size or over 512 KiB, a `splash_art` that is not an SVG
  of at most 256 KiB, or a `product` that is blank or longer than 32 characters;
- a string that looks like a colour literal anywhere in the file;
- a file over the size limits.

Every failure gives one message naming the key, for example
`Presentation invalid: [metrics].row_height: 40 is outside 18-28`. The plugin is SKIPPED, the default look applies, and
the existing plugin-problem notice appears once per digest: `AttemptStore` records under the provider the digest it
announced (`announced = "<digest>"`), and `PluginRuntime.start` leaves the NOT_STARTED problem for "Presentation
invalid" out while the digest matches. An invalid theme file is skipped alone, as folder themes are. A font that does
not decode or register, or whose family is not the declared one, degrades its own face to the platform face for that
role; the presentation stays and a notice appears. The conformance test keeps the shipped fonts valid, so this only
meets a damaged install.

## 6. jasper-ui: the kit seam and faces

### 6.1 Package graph

A new JDK-only package `dev.jasper.ui.kit` holds `Kit`, `ControlVariants` (an enum per control point) and
`ColorSlots`. It depends on `dev.jasper.ui.metrics` and `dev.jasper.ui.theme` only; its `package-info` says so.
`TypeScale`, `FaceSet` and `MetricTokens` join `Typography` and `DesignMetrics` in `dev.jasper.ui.metrics`. `Kit` is a
record: `Kit(String id, DesignMetrics metrics, TypeScale type, FaceSet faces, ControlVariants controls, ColorSlots
colors, UiKeySet uiKeys)`, with `Kit.DEFAULT` and `Kit.CLASSIC`, the two bases a kit file names in `base` (the parser
starts from one and applies the file's values). The `package-info` of `dev.jasper.ui.swing` gains `dev.jasper.ui.kit`;
`KitFaces` and the faces live in `swing`. Nothing in `kit` refers to `swing`, and `metrics` refers to neither, so the
graph stays acyclic.

### 6.2 Instance metrics and type

`DesignMetrics` becomes instance-valued: an `int[]` indexed by role ordinal, `DEFAULT` (today's values, still frozen by
`compact-roles.tsv`), `CLASSIC`, and `with(MetricTokens)`. Its public no-argument constructor goes. The about 460
`ThemePainting.metric(context, role)` and `context.metrics()` reads then follow the context. `KitContext.typography()`
returns `Typography.of(kit.type(), kit.faces(), userOverride)` instead of the global `SwingTypography.typography()`,
where `userOverride` is the user's `ui.font.family` and `ui.font.size` when set.

### 6.3 `KitContext.of`

`KitContext.of(Kit kit, ThemeBinding binding, ThemeRoute route, IconRegistry icons, BooleanSupplier animations,
Function<String, String> shortcuts)` lives in `dev.jasper.ui.swing` and replaces the public constructors, so no
surface builds a context without a kit. `KitContext.kit()` returns it; `metrics()` returns `kit.metrics()`. The three
app construction sites (`AppearanceBindings.java:24`, `HostedKit.java:57`, `PresentationIcons.java:24`) and the test
fixtures convert. `IconRegistry.size` and the tile opacity take the `DesignMetrics` the context passes in, because
`dev.jasper.ui.icons` cannot depend on `swing`.

### 6.4 Faces

`ControlRegistry.create` keeps its exhaustive switch and the control classes keep their state, events and the
reconciler's composition flags; no reconciler pass is duplicated. A structurally different control delegates preferred
size, layout, painting and its declared painted pairs to a face, built once by `KitFaces.of(kit)` and fixed at the
control's construction. Faces never reference `Kit` or `ControlVariants`.

| Face | Default implementation | Classic implementation |
|---|---|---|
| `ToggleFace` | switch, trailing | checkbox leading its label, description under it |
| `ExclusiveFace` | segment track | radio row; `Option.hue` swatches; PREVIEWS thumbnail with a radio |
| `TabsFace` | segment track | folder tabs |
| `GroupFace` | section label above a rounded card | titled box, the title breaking the border, in `text.primary` |
| `LabelPolicy` | plain left labels | left label column with a colon, caption under the label; trailing "Port:" |
| `EmptyFace` | tile, title, description, actions | bold line, sentence, one button |
| `TableFace` | transparent header | header band on `surface.tableHeader`, `border.divider` separators, `surface.zebra` rows |
| `TreeFace` | today's tree | disclosure glyphs, 24 px indent, medium category rows with muted counts |
| `StepperFace` | − value + | data-face value, muted unit, stacked arrows |
| `OrderedFace` | per-row glyphs | side column acting on the selection, emitting the same `Removed` and `Reordered` events |
| `ButtonFace` | inset line and drop | gradient, `border.hover`, `accent.edge` on the primary; combo and picker faces alike, without icon tiles (below) |
| `TagFace` | borderless tint | uppercase, `tag.border` box on the hue tint |
| `RouteFace` | icon tiles | edged cells with a hue left edge |
| `PromptFace` | 54 px icon tile | 30 px accent tile beside the message; the title in the dialog's header strip |
| `PropertiesFace` | section label over a card of label and value rows | titled: a title band and a label column on `surface.panel` beside `surface.card` values; untitled: label-colon lines |
| `SeparatorFace` | control-tall `border.subtle` hairline | `SEPARATOR_HEIGHT` separators and manager rules in `border.window`; manager column headers as header strips |
| `RevealFace` | glyph in the field, glyph buttons beside a stored value | "Show" checkbox after the input, "Show password" under a prompt's field, "Show" and "Copy" text buttons |

Composition rules for classic forms: a non-Toggle `SettingRow` inside a Form renders under `LabelPolicy` like a
`FormRow`; the classic ordered side column spans the list and any sibling entry row and is top-aligned; an unlabelled
EXCLUSIVE choice in a MIXED card sits in the control column (Add Host wraps its choice in an empty-label FormRow where
needed), and so does TFTP's read-only login field, which takes the choice's place. This supersedes the New File Server
mocks, which draw the Access radios and the TFTP field flush left. In a FULL form, a SETTINGS card draws no divider
between rows, and a MIXED card draws one `border.row` divider at its content-to-settings transition; on a Settings page
`border.row` divides every row.

Classic button and field details, owned by the classic `ButtonFace` and the classic text-field policy that
`KitFaces.of` builds with it:

- Combo, dropdown and picker faces draw no icon tile on the closed face (the primary text, then the caption, in the
  data face when `technicalCaption`, then ▼) and their popup rows draw no tiles; captions are muted, and UTILITY rows
  sit below a `border.row` divider.
- SEARCH and PATH fields and the picker's search field draw no leading glyph.
- GHOST and DESTRUCTIVE_GHOST buttons in ACTIONS stacks and side columns draw as standard buttons; in toolbars, panel
  headers and navigation rows they are flat until hovered, then show a 1 px `border.hover` border on
  `surface.control.hover`. A LINK `addAction` in a side column draws as a standard button. A disabled button draws a
  flat fill, a lighter border and disabled text.
- The RECORDER and RECORDER_ARMED value presentations draw as a field (12.2, Keymap).

The mocks' Project Index rows without a folder icon are a fixture.

### 6.5 FlatLaf

`FlatLafInstaller.install(GeneratedTheme theme, Kit kit)` takes its geometry extras from `kit.metrics()` and its colour
keys from `UiColorKeys.flatLaf(theme, kit.uiKeys())`, computed at install time. `UiKeySet` is a theme-local enum
(`DEFAULT`, `CLASSIC`) in `dev.jasper.ui.theme`, so the theme package never sees the kit. The classic overlay, frozen as
`ui-color-keys-classic.tsv`, adds `TableHeader.*`, `Table.alternateRowColor`, `Table.gridColor`,
`Button.hoverBorderColor`, `Button.default.borderColor = accent.edge`, `Button.startBackground`/`endBackground`
(`button.secondary.bg`/`button.end`), `MenuBar.selection*` and `ToolBar.separatorColor`, and sets
`Table.selectionBackground` and `Tree.selectionBackground` to `selection.bg` (overriding the terminal-selection
wildcard at `UiColorKeys.java:47-50`). `UiKeySet.DEFAULT` stays frozen by today's `ui-color-keys.tsv`.
`GeneratedTheme` loses `uiKeys`.

### 6.6 Static painters

`NativeChromePainting` and `NativeControlStyles` become the single classes `ChromePainter(Kit)` and `NativeStyles(Kit)`
(no interfaces); their statics go. The app's static metric finals (`ToolWindowArea.java:53,55`,
`ToolWindowHeader.java:31`, `DockDrag.java:54`, `WindowHeader.java:24`) and method reads (`ThemeManager.java:16`,
`MacTitleBar.java:217`, `TitleSearchField.java:36`, `WindowToolbar.java:48`, `ToolWindowHeader.java:78`,
`AppIcons.java:43,50`) read the window's kit. The `AnimationSettings` durations (`AnimationSettings.java:16-18`) feed
`ConfigSnapshot`, which is built before any window, so they come from the default catalog; motion is off under
Enterprise. `StaticDefaultTest` keeps it so and allows only that read.

## 7. Theme engine: derived roles and guards

### 7.1 Derived roles

`dev.jasper.ui.theme.DerivedColor` is a separate host-private enum, so `SemanticPalette`'s completeness rule and the
SDK mirror stay untouched. `DerivedPalette.of(GeneratedTheme)` is a pure function of the palette and the theme's
authored overrides, computed after `TitleBarTheme`, cached by identity and frozen as `derived-roles.tsv` for the shipped
themes. Recipes use `ColorMath` and `SurfaceContrast`; a recipe that keeps its result a ΔL* from a reference (`apart`,
"kept … from") moves it toward `text.primary`'s lightness, and the other way only when that side has no room. `*`
marks a role with an authored override key; `′` means the role after its boundary guard.

| Role | Recipe | Used for |
|---|---|---|
| `surface.header`* | apart(mix(text.primary, surface.app, 5%), surface.app, 3 L*) | inactive tool-window header strips |
| `surface.header.active`* | mix(accent, surface.app, 10%), kept 2 L* from `surface.header` | the focused tool window's header |
| `surface.stripe`* | apart(mix(text.primary, surface.app, 3.5%), surface.app, 1.5 L*) | stripes and the bottom strip |
| `surface.tableHeader`* | apart(mix(text.primary, surface.card, 6%), surface.card, 3 L*) | column and section headers, read-only fields, keycaps, Run headings |
| `surface.zebra`* | apart(mix(text.primary, surface.card, 2.5%), surface.card, 1.5 L*), closer to `surface.card` than `surface.tableHeader` | alternate rows |
| `surface.rowHover`* | mix(accent, surface.card, 8%) | row hover |
| `warning.ground`* | mix(status.warning, surface.card, 14%) | warning Notices, the warning status cell, warning count pills |
| `warning.border`* | mix(status.warning, surface.card, 40%) | warning Notice borders |
| `danger.ground`, `danger.border` | as the warning pair from `status.danger` | DANGER Notices and pills |
| `info.ground`, `info.border` | as the warning pair from `accent` | INFO Notices |
| `surface.footer` | apart(mix(text.primary, surface.dialog, 3%), surface.dialog, 2 L*) | dialog, Settings and Run footers |
| `border.divider`* | mix(border.window′, surface.card, 73%) | column separators |
| `border.row` | mix(border.window′, surface.card, 46%) | faint row dividers |
| `border.hover` | mix(text.primary, border.control′, 24%) | button hover borders, keycap bottom edges |
| `accent.edge` | accent moved 7 L* away from `surface.card` | the primary button, a checked box, a selected radio ring |
| `button.end` | mix(text.primary, button.secondary.bg, 4%) | the second stop of the classic button and combo fill |
| `tag.border.blue`, `.green`, `.amber`, `.purple`, `.neutral` | mix(hue, surface.card, 40%) | the 1 px border of a boxed tag; its tint stays the hue surface at the tile share |

Existing roles serve the rest: the open stripe tile and a pressed toolbar toggle paint `surface.control.selected`; a
count pill paints its tone's ground with the tone's colour: `PanelBadge.Tone` ACCENT on `info.ground` with `accent`,
ERROR on `danger.ground` with `status.danger`, and `warning.ground` with `status.warning` for the mocks' amber Problems
pill, which belongs to the deferred Problems panel; disabled text keeps its 0.42 alpha.

Calibration, computed for Slate Cobalt Light against the design targets: header strip #DFE2E6 against #E1E3E6, table
header #F1F2F2 against #F0F1F3, row hover #EEF3FB against #EEF2FA.

### 7.2 Override keys

A theme's `ui.light` or `ui.dark` object may list the nine starred keys after its roles. They get their own parse table
in `JasperTheme` (`DERIVED_OVERRIDES`, a `Map<String, DerivedColor>`), consulted before `SemanticColor.of`, so every
existing file still loads and an unknown key still fails. The parsed overrides travel on the variant and on
`GeneratedTheme` (`Map<DerivedColor, Color> derivedOverrides()`), and `TitleBarTheme` copies them.

### 7.3 Guards

Guards repair at paint time and never store the result. Each terminates: any opaque colour reaches about 4.58:1 against
black or white, and a ΔL* of 50 or less is always reachable. They extend `SurfaceContrast` and are owned by faces, so no
kit file can weaken them. Each face declares the `(slot, against, guard)` pairs it paints; default faces declare only the
pairs they repair today, so the default look stays pixel-identical.

| Guard | Floor |
|---|---|
| Text | 4.5:1 |
| Indicator (checked box, radio dot, status dot, swatch) | 3:1 |
| Boundary, structural | 1.3:1 |
| Boundary, control | 1.5:1 |
| Apart | the ΔL* its recipe names |

Known repairs: Catppuccin's `border.control` equals `surface.input` (1.00:1) and gets an outline; Latte's
`border.window` on `surface.app` (1.17:1) and ocean-dawn's (1.11:1); `text.onAccent` on the accent (Latte 4.09:1, the
ocean fixture 3.09:1); the Slate Cobalt Dark checked box (accent on card 2.70:1) gets an `accent.edge` ring; warning
text on its ground (Latte 2.06:1, ocean-dawn 1.60:1, ocean 3.52:1, Slate Cobalt Light 4.15:1). The guards visibly
outline Catppuccin's borderless controls under the classic look; that is intended.

### 7.4 Plugin theme tier

The `themes` files of every loadable plugin are read in the bootstrap pre-pass and handed to
`ThemeLibrary.read(folder, previous, pluginThemes)`, which holds built-ins, then plugin themes, then folder themes.

- **Ids** must satisfy `validId` and cannot equal a built-in id. Among plugins, a DEV plugin's theme wins over a
  BUNDLED one's, which wins over a USER one's, then the lowest plugin id; the loser is dropped with a notice.
- **Reserved ids:** the theme ids of every discovered plugin, enabled or not, are reserved like built-in ids: a folder
  file with such an id is reported and ignored (as at `ThemeLibrary.java:104-108`) and Import refuses it with "<Theme>
  is provided by the <Plugin> plugin and can't be replaced".
- **Literal scan:** `ColourLiteralScanTest` exempts exactly the files named by in-repo `plugin.toml` `themes` entries;
  a companion test loads each with the production loader. Kit files stay under the scan.
- **Settings:** the Appearance page captions plugin themes with the provider's name.

### 7.5 A saved pick whose provider is off

`ThemeSelection.of` receives the dormant ids: themes declared by plugins that were discovered but are not loadable. A
dormant pick falls back to the default silently, with no configuration warning, and the pick is kept, so turning the
provider back on restores it. An unknown id keeps today's warning.

### 7.6 The Enterprise theme

`themes/enterprise.json`, named "Enterprise", id `enterprise`, with `ui.light`, `ui.dark` and the terminal schemes `light`
and `dark`. At the user's direction (2026-10-18) both variants are ports of IntelliJ's own themes: `convert_port` in
`tools/themes/jasper_theme.py` builds the file from `tools/themes/enterprise/port.json` and IntelliJ's theme files in
`tools/themes/intellij/` (JetBrains/intellij-community, Apache-2.0; the port is identical at tag idea/2026.2.3, commit
`8062c061ff33`), and `test_jasper_theme.py` holds the file to it.

- **Light** is "IntelliJ Light": `Light.theme.json` over `intellijlaf.theme.json` and `darcula.theme.json`, with the editor
  scheme `Light.xml`.
- **Dark** is the New UI "Dark": `expUI/expUI_dark.theme.json` over `darcula.theme.json`, with the editor scheme
  `expUI/expUI_darkScheme.xml`.

Each variant is the converter's output for its theme, its terminal scheme the scheme's console colours. An override key is
filled when IntelliJ's own theme sets its key exactly (not through a `*.` wildcard); port.json chooses three roles and one
override. Key values:

| Role | Light | Dark |
|---|---|---|
| `surface.app`, `surface.dialog` | #F2F2F2 | #2B2D30 |
| `surface.panel`, `surface.card` | #FFFFFF | #2B2D30 |
| `surface.terminal` | #FFFFFF | #1E1F22 |
| `text.primary` | #000000 | #DFE1E5 |
| `accent` | #2675BF | #366ACE |
| `selection.bg` | #2675BF | #2E436E |
| `border.window` | #D1D1D1 | #1E1F22 |
| `border.control` | #C4C4C4 | #4E5157 |
| `surface.header` | #F2F2F2 | #2B2D30 |
| `surface.header.active` | #DFE2E9 | #2C3340 (its recipe) |

port.json's choices: Light's `accent` is IntelliJ Light's `List.selectionBackground` and its `surface.card` IntelliJ's
`contentBackground`; Dark's `accent` is the palette's `Blue5`. IntelliJ's default-button blues (#528CC7, #3574F0) give
white text 3.54:1 and 4.28:1, which the TEXT guard would paint black; these give 4.80:1 and 5.11:1. Light's
`surface.header.active` is the user's #DFE2E9 (IntelliJ Light's own is #E2E6EC). New UI Dark sets no exact
`ToolWindow.Header.background`, so Dark's follows its recipe. `derived-roles-enterprise.tsv` freezes every derived role.

### 7.7 Converter

`tools/themes/jasper_theme.py` learns eight of the override keys (all but `border.divider`; `ROLES` at line 38, `OPTIONAL_ROLES` at 55, and the
role-order check in `validate()` at 184, which accept roles followed by any of the optional and override keys in
order). It fills them when the
IntelliJ theme has the key:

| IntelliJ key | Override key |
|---|---|
| `TableHeader.background` | `surface.tableHeader` |
| `Table.stripeColor` | `surface.zebra` |
| `ToolWindow.Header.background` | `surface.header.active` |
| `ToolWindow.Header.inactiveBackground` | `surface.header` |
| `Table.hoverBackground` | `surface.rowHover` |
| `Banner.warningBackground` | `warning.ground` |
| `Banner.warningBorderColor` | `warning.border` |

Goldens are regenerated. The Islands chrome mapping stays the only one: the guards absorb the difference. `convert_port`
(7.6) converts IntelliJ's own themes for the Enterprise theme and also fills the override keys they set exactly.

## 8. SDK 0.18.0

### 8.1 Renames

| 0.17 | 0.18 |
|---|---|
| `Nodes.ChoiceKind.SEGMENTED` | `EXCLUSIVE` |
| `Nodes.ButtonPresentation.CIRCULAR` | `HELP` |
| `Nodes.FormKind.HERO`, `HERO_COMPACT` | `PROMPT`, `PROMPT_COMPACT` |
| `Nodes.LayoutRole.HERO` | `PROMPT_HEADER` |
| `Nodes.TextRole.HERO_TITLE` | `PROMPT_TITLE` |
| `Nodes.IconSize.HERO` | `PROMPT` |

The jasper-ui model mirrors each rename (`KitValues` maps by name), and the kit's private names follow
(`Typography.Role.HERO_TITLE`, the `HERO_*` metrics roles, `MacTitleBar.Style.HERO`, the reconciler's hero pass), so
the word hero names only `Form.hero`, the heading slot that SHEET forms also fill, which keeps its name.
`KitCompatibility` gains the seven removed constants of the table (`HERO_COMPACT` included) as exact members (a
`getstatic` of, for example, `dev/jasper/sdk/ui/Nodes$ChoiceKind#SEGMENTEDLdev/jasper/sdk/ui/Nodes$ChoiceKind;`), and
its message names the SDK version that removed the member: "references removed SDK member X; rebuild it for SDK 0.18".
A 0.17 manifest (`<0.18`) is SKIPPED by the SDK range before it loads (`PluginResolver.java:66-68`);
`KitCompatibility` catches a rebuilt manifest that claims `<0.19` while still using a removed member. `Toggle`, `GHOST`,
`DESTRUCTIVE_GHOST`, `MONO*` and `Card` keep their names.

### 8.2 Additions

Each addition is mirrored in the jasper-ui model, `KitValues` (lossless), `ViewSpec` validation, the `ListItem` inline
whitelist where it applies, the testkit fake and the shared contract suites, and both looks render it; the palette
additions `hue`, `itemNoun` and `role` are drawn only by the Run window (11.2), and the card accepts them.

| Addition | Shape | Validation and meaning | Mock |
|---|---|---|---|
| `Nodes.Notice` | `Notice(String id, ControlState state, Severity severity, String text, List<Button> actions)` | `text` is a non-blank sentence; at most one SECONDARY or LINK action; allowed as a Form section and in VERTICAL stacks. The presentation shows the severity as a glyph and its word ("Warning") before the text | FTP "Warning · Plain FTP sends the password unencrypted" |
| `Nodes.Properties` | `Properties(String id, ControlState state, String title, List<PropertyRow> rows)`, `PropertyRow(String label, String value, boolean technical, Optional<Severity> severity)` | At least one row; labels non-blank, without a colon (the presentation punctuates); `technical` uses the data face; a severity colours the value through its guard; a blank title shows no heading | "Properties — minecraft-local" |
| `Tree.columns` | `List<Column> columns`, empty for no header | The first column is the title column; each `TreeItem.trailing` has at most `columns.size() - 1` values, aligned under the rest; header clicks report `Sorted` as a Table's do | Sessions Name/Host/Port; Keymap Action/Scope/Shortcut |
| `TableRow.cellNodes` | `Map<String, Node> cellNodes` | Text, StatusDot, Icon, Chip, an INLINE Stack of those, or a DROPDOWN Choice (which emits `Selected`); a column id is in `cells` or `cellNodes`, never both. Under both kits a DROPDOWN in a cell draws borderless (its value, then ▼), with a border only on hover or focus | Tunnels "● Local"; File Servers "test [HTTP]"; Notifications "Card ▼" |
| `TableRow.heading` | `static TableRow heading(String id, String title, OptionalInt count, String countNoun)`; the row's `Optional<Heading> heading`, `Heading(String title, OptionalInt count, String countNoun)` | A heading row has no cells, is never selected or activated, spans the row and groups the rows below it; its count shows muted on the right, followed by `countNoun` (a lowercase singular noun, to which the host adds "s" unless the count is 1; blank shows the bare number) | Notifications groups "6 groups"; Sessions groups |
| `Table.empty`, `Table.emptyText`, `ItemList.emptyText` | `Optional<Empty> empty`; `String emptyText` | Drawn inside the body below kept column headers; `empty` and a non-blank `emptyText` are exclusive; an Empty in a table has at most one action | SFTP "Not Connected"; "No Tunnels" [New Tunnel…]; "No keys" |
| `ChoiceKind.TABS` | enum constant | Two or more options; `Selected` on change; the owner composes the content below | Plugins Overview / Details |
| `Field.readOnly` | `boolean readOnly` | Focusable, selectable and copyable, never edited: emits no `Edited`; replaces the host-only `KitView.setFieldReadOnly` | "No folder selected" with Choose…; "No login (TFTP has none)" |
| `PickerFace.technicalCaption` | `boolean technicalCaption` | The caption is a data value in the data face | Via host "Mac dustin@192.168.1.119:22" |
| `EditorHint.technical()` | `record Technical() implements EditorHint` | Host rows and generated pages draw the field with `FieldPresentation.TECHNICAL` | FTP passive ports, host rows |
| `Nodes.Separator` | `Separator(String id, ControlState state)` | Only in INLINE and ACTIONS stacks, never first, last or beside another | SFTP ← → ⟳ \| Hidden files \| Upload ▾ |
| `Form.footnote` | `String footnote` | One plain line drawn muted at the leading edge of the form's footer, beside its actions; blank draws none | Unlock Vault "Auto-locks after 15 min idle" |
| `DialogSpec.swatch` | `Nodes.Hue swatch` | A SWATCH square of the hue before the dialog's title wherever the presentation draws the title (a native title bar, or FlatLaf's on Windows and Linux, draws none); NONE draws none | "■ Add Login", "■ Add SSH Key" |
| `IconSize.SWATCH` | enum constant | A solid square of the icon's hue at caption size; the hue must not be NONE; the name stays a valid icon name | Run window hue squares; radio swatches |
| `StandardMenu.SESSION`, `TRANSFER` | enum constants | Built-in menus with no items of their own, shown only while a plugin has entries in them, as TOOLS is | Session and Transfer menus |
| `ToolbarItem.View` | `record View(ToolbarViewFactory factory)`; `@FunctionalInterface interface ToolbarViewFactory { UiView create(WindowHandle window); }` | Admission and lifetime in 10.3 | the session combo and Run |
| `ScopeSpec.hue` | `Nodes.Hue hue` | NONE falls back to the owner's icon hue, then NEUTRAL; adds an SDK `palette` → `ui` package edge, which stays acyclic | Run window rows |
| `ScopeSpec.itemNoun` | `String itemNoun` | A lowercase singular noun ("session"); blank falls back to the scope label; the host capitalises it for the detail label | "Session: dustin@192.168.1.119:22" |
| `PaletteVerb.role` | `Role role`, `enum Role { STANDARD, SPLIT }` | At most one SPLIT verb per scope, never verb 0: it is the split variant of verb 0 | "Open in a split pane" |
| `ViewSpec` validation | rule | An EXCLUSIVE choice has 2 to 4 options | TFTP's single option becomes a read-only field |

Existing record constructors stay as conveniences with the new components at their empty values. App-native mirrors
follow in `dev.jasper.app.palette` (`PaletteScope.hue()`, `itemNoun()`, `PaletteVerb.role()`), translated in
`dev.jasper.app.plugins`.

### 8.3 Re-documented contracts

No API shape changes; the javadoc and `docs/plugin-ui.md` say:

- `Toggle` is a boolean, drawn as a switch or a checkbox; `compact` is a size hint; `pending` means a change is in flight.
- EXCLUSIVE is a choice among 2 to 4 visible options; GHOST is a quiet button; HELP opens help; PROMPT is a short
  focused question; `FieldPresentation.TECHNICAL` and the MONO roles mark data values.
- Section + Card is a titled group. FormRow labels carry no colon; the presentation punctuates.
- `Empty.icon` is drawn only by presentations that draw an icon tile.
- `DialogSpec.Presentation`, list densities, `ScrollPolicy`, `PickerDensity`, and STEPPER versus SLIDER are hints.
- `PanelHeader.detail`: a count is passed as digits, and the presentation punctuates it ("(2)").
- `ScopeSpec.monospaceRows` means the data face, not the terminal font (`ScopeSpec.java:20`).
- `Menus.create` places a menu where the presentation puts plugin menus; PANE and TAB items may be shown in a Window
  menu.
- The rule at `docs/plugin-ui.md:57`, "settings never use checkboxes", is deleted.

### 8.4 Not added

No mock needs these: `StatusItem` severity, SDK panel badges, WINDOW or HELP standard menus, `Option.enabled` with a
reason (TFTP's case becomes a read-only field), `Numeric.unit`, an ORDERED entry slot, Status or Tabs nodes, an active-
presentation query, `SettingSpec.dependsOn`, `SettingsPanel.valid()`, a radio hint.

### 8.5 Version and manifests

`JasperSdk.VERSION` becomes `0.18.0`. Every in-repo manifest moves to `sdk = ">=0.18.0, <0.19"`: the eight bundled
plugins, `docs/examples/external-plugin`, and Enterprise. The example's `jasperSdkVersion` default becomes `0.18.0`
(`docs/examples/external-plugin/build.gradle.kts:7`). The ranges in `docs/plugin-authoring.md`, `jasper-sdk/README.md`
and `docs/sdk-icons.md` move with it (`VersionRangeTest.java:17-40`), and `VersionRangeTest` lists 0.17 among the
incompatible minors. THD Sourcing Crypto rebuilds for 0.18. `sdkRelease` publishes the 0.18.0 jars.

### 8.6 SDK 0.18.1

An additive release (the user, 2026-10-18): `ToolbarItem.View` admits, besides at most one PRIMARY Button, any number of
icon-only GHOST Buttons (a blank label with an icon and a tooltip), drawn flat until hovered with their glyph at rail
size; and two authored built-in icon names, `run` (IntelliJ's classic Run triangle) and `application` (its
run-configuration icon), host artwork in both looks. Only Remote's manifest moves to `sdk = ">=0.18.1, <0.19"`.

## 9. The app: projections and consumers

### 9.1 `dev.jasper.app.presentation`

The package holds `Presentation`, `ChromeSpec`, `KitParser`, `PresentationFacts`, `AttemptStore` and the projection
records. `ChromeSpec` is referenced only here; `ControlVariants` only here (by `KitParser`), in `dev.jasper.ui.kit` and
in `KitFaces`. Each consumer receives its own projection, computed once:

| Projection | Fields | Consumer |
|---|---|---|
| `DesktopFlags` | `screenMenuBar`, `nativeTitle` | bootstrap: desktop properties, `TitleBarStyle` |
| `WorkspaceLook` | menus (STANDARD, CLASSIC), header (UNIFIED, CLASSIC), stripes (RAILS, LABELLED), tabs (EQUAL, CLASSIC), status (SLASH, RULED), `runItem`, `FrameStyle`, icons (NEW_UI, CLASSIC) | workspace: `WorkspaceChrome.of` |
| `ShellLook` | `unifiedTitle`, `windowMenus`, `followThemeAppearance` | windows: `ShellChrome.of`, `NativeShells` |
| `PaletteLook` | view (CARD, RUN_WINDOW), home (TITLE_FIELD, UPPER) | palette: `PaletteViews.of` (view); workspace: `WorkspaceChrome.of` (home, 11.5) |
| `SettingsChrome` | `integratedTitle`, `collapsibleNav`, `inlineReset`, `hiddenKeys`, `kitFontSize` (from which `fontRowCopy` follows) | settingsui |
| `Composition` | ROWS, CLASSIC | composition roots: `CorePages`, `AppearanceSelection`, `NotificationGroupsEditor`, the environment-variables editor (`Editors`), `KeymapPage`, `PluginsPage` |
| `DialogMapping` | AS_REQUESTED, CLASSIC; `windowTitled` | windows (`AuxiliaryWindows`, which also hosts plugin dialogs) |
| `Branding` | `product`, the icon images, the splash art; `Branding.NONE` for a look without `[branding]` | bootstrap: `Splash`; platform: `ApplicationIcon` (10.11) |
| `Motion` | STANDARD, NONE | application: the one animations supplier that every `MotionPolicy` reads (10.9) |
| `TitleFormat` | the template | workspace: `WindowContent.onTitle` |
| `DockLayoutKey` | empty for the default look, else the provider id | application → `UiState` (a plain string) |
| `AppPanels` | `notificationsTitle`, `notificationsSlot` | application: `NotificationsFeature` |
| `DefaultSource` (in `config`) | the kit's defaults | `ConfigService`, `SettingsDraft` |

`WorkspaceChrome.of`, `ShellChrome.of` and `PaletteViews.of` are the only classes that read the `WorkspaceLook`,
`ShellLook` and `PaletteLook` variant enums and pick implementations; `DialogMapping` is read only by
`AuxiliaryWindows`, `Motion` only by the application's animations supplier, and `Composition` only by the listed roots. Other readers read values (sizes, strings, booleans).

Each field comes from the kit file as below, and the last column is `Presentation.fallback` (4.7). The default look's
values follow from `ChromeSpec.DEFAULT` and `Kit.DEFAULT` by the same rules (12.1 lists the Settings ones).

| Field | Enterprise value, and when | Fallback |
|---|---|---|
| `DesktopFlags.screenMenuBar`, `nativeTitle` | false when `menu_bar = "window"`; true when `title_bar = "native"` | as committed |
| `WorkspaceLook.menus` | CLASSIC when `menu_bar = "window"` | STANDARD |
| `WorkspaceLook.header` | CLASSIC when `title_bar = "native"` | UNIFIED, as a row under the native title, as `TitleBarStyle.NATIVE` keeps it today |
| `WorkspaceLook.stripes`, `FrameStyle` | LABELLED and flush frames when `tool_windows = "stripes"` | RAILS and today's frames |
| `WorkspaceLook.tabs` | CLASSIC when `editor_tabs = "classic"` | EQUAL |
| `WorkspaceLook.status` | RULED when `status_bar = "cells"` | SLASH |
| `WorkspaceLook.icons` | CLASSIC when `icons = "classic"` | NEW_UI |
| `WorkspaceLook.runItem`, `PaletteLook.view`, `PaletteLook.home` | true, RUN_WINDOW and UPPER when `palette = "run_window"` | false, CARD, TITLE_FIELD |
| `ShellLook.unifiedTitle` | true only when `title_bar = "unified"` and `MacTitleBar.isSupported()` | false |
| `ShellLook.windowMenus` | true when `menu_bar = "window"` | as committed |
| `ShellLook.followThemeAppearance` | true when `title_bar = "native"` | true |
| `SettingsChrome.integratedTitle` | false when `title_bar = "native"` | false |
| `SettingsChrome.collapsibleNav`, `inlineReset`; `Composition` | true, false and CLASSIC when `base = "classic"` | false, true, ROWS |
| `SettingsChrome.hiddenKeys` | `window.native_decorations` when `title_bar = "native"`, and `ui.animations.enabled` when `motion = "none"`, and `window.rail_width` when `tool_windows = "stripes"` | `window.native_decorations` |
| `SettingsChrome.fontRowCopy` | "Use the default font size" when `[type.ui]` names a face | "Use platform font size" |
| `DialogMapping` | CLASSIC when `dialogs = "classic"`; `windowTitled` when `title_bar = "native"` | AS_REQUESTED, `windowTitled` |
| `Branding` | `[branding]` | `Branding.NONE` |
| `Motion` | NONE when `motion = "none"` | STANDARD |
| `TitleFormat` | `window_title` | `"{context}"` |
| `DockLayoutKey` | the provider id | empty |
| `AppPanels` | "Event Log" at BOTTOM_LEFT when `tool_windows = "stripes"` | "Notifications" at RIGHT_TOP |
| `DefaultSource` | `[defaults]` | as read at start (4.7) |

### 9.2 Package edges

The `package-info` files gain these allowed dependencies:

- `bootstrap` → `plugins` (`PluginSelection`), `presentation`;
- `workspace`, `windows`, `palette`, `settingsui`, `pluginmanager`, `shortcuthelp`, `plugins`, `application` →
  `presentation`;
- `presentation` → `config`, `settings`, `persistence`, `dock`, `dev.jasper.ui.kit`, `dev.jasper.ui.metrics`,
  `dev.jasper.ui.theme`, `dev.jasper.ui.icons` (the splash art's check), tomlj;
- `appearance` → `dev.jasper.ui.kit` (it receives the `Kit`, not the presentation);
- `plugins`' public-type sentence names `PluginRuntime`, `GlassSource` and `PluginSelection`.

`config` and `persistence` gain no edge: `DefaultSource` lives in `config`, and `UiState` takes the dock layout key as a
string. The graph stays acyclic; `dev.jasper.app.dock` stays JDK-only.

## 10. Window chrome

### 10.1 Title bar and menus

- `TitleBarStyle.choose` gains `nativeTitle`; under it every platform gets `NATIVE`. The three `NativeShells` literals
  of `MacTitleBar.isSupported()` become `ShellChrome.unifiedTitle()`, and `TerminalWindow.java:66` reads the
  `ShellLook.unifiedTitle` value it receives (`workspace` has no `windows` edge); the native path already works
  (`MacTitleBarTest.java:170`).
- With a native title the toolbar row is chrome, not a `ThemeZone.titleBar` zone.
- On macOS, NSAppearance follows the theme's darkness on every native-titled frame and dialog: the Compact branch at
  `WindowContent.java:326-330` applies to every native title, and `NativeShells` does the same when it has no unified
  bar.
- On Windows and Linux, plan 6 sets the dark-caption hint where JBR exposes it; otherwise the documentation records
  that the system title bar follows the operating system's light or dark setting.
- Under `menu_bar = "window"` the auxiliary frames show no menu row, as the 07 Credential Vault mocks draw them; their
  actions stay on their own controls, and each frame binds Close Window's ⌘W itself.
- The native title, menu bar and toolbar rows meet flush, separated by 1 px rules.

### 10.2 Menu composition

`MenuBarLayout` has two implementations over the same contributions:

- `StandardMenuBar`: File, Edit, View, Pane, Tab, Session, Transfer, Tools, plugin menus, Help;
- `ClassicMenuBar`: File, Edit, View, Session, Transfer, plugin menus, Tools, Window, Help. Window holds the Pane items
  and the Tab items as two sections, then the tool-window toggles; Help is the app's Help menu, placed last by the
  layout. Help holds About Jasper on every platform, including macOS under `menu_bar = "window"` (today it is in the
  application menu there, `AboutWindows.java:72`). Classic menu items highlight in the accent with `text.onAccent`.

Each built-in menu that has no items of its own shows only while it has entries, extending today's Tools rule.

### 10.3 Header, toolbar and toolbar views

`HeaderComposition` has `UnifiedHeader` (today's centred title field) and `ClassicHeader`: a toolbar row of left-packed
buttons in separated groups, New Session (NEW_TAB), Split (today's Split dropdown), Maximize (ZOOM_PANE); then
contributed items and views; then, trailing, the title search field reading "Search everywhere" with the
COMMAND_PALETTE keycap, and "Settings…". New Window and Find stay in the File and Edit menus. Built-in labels are host
strings owned by the strategy. `WindowToolbar.doLayout` learns to place any component and to pack leading.

The classic header honours `window.toolbar`. `icons` (Enterprise's default, 5.8) gives 22 px icon buttons whose tooltips
are their labels, Split marking its menu with a small ▼ in its corner. `icons_and_labels` gives each button's icon, then
its label (Split ending in ▾). `hidden` gives no row, so the Session view and Search everywhere go with it, and the Run
window stays reachable by ⇧⌘P and "▶ Run…". The icons are the look's `[chrome] icons` set (5.2), IntelliJ's classic
icons under Enterprise. `window.toolbar_height` is the classic toolbar row's height.

**`ToolbarItem.View` admission and lifetime:**

- the root is an INLINE Stack of LABEL Text, Choice (DROPDOWN, SEARCHABLE or PICKER), at most one PRIMARY Button,
  icon-only GHOST Buttons (8.6), MenuButton and Separator; anything else is refused with a message naming the node, when
  a window mounts the view (contained and logged against the plugin; the view closes and that window shows none) and on
  every update (an `IllegalArgumentException` to the caller), since `Toolbar.add` receives only the factory;
- the factory runs when a window's open event fires, once per window, never for the rehearsal or a pre-built window
  until it is shown; the view closes with its window or its registration;
- a view never goes into the overflow menu: it hides while the toolbar is too narrow for it;
- it renders at toolbar density through `KitView.toolbarStrip`, following `titleStrip` (`KitView.java:319-330`);
- in the default look it renders in today's header, inside the macOS unified title bar, hit-tested like the existing
  header controls; translation happens in `HostedUi.toolbar` (`:225-238`) and is mirrored in the fake.

### 10.4 Window title

`TitleFormat` is a template with three tokens: `{context}` is today's header label (`WindowContent.java:311-320`);
`{session}` is the active pane's remote host name, or "Local: <shell name>" for a local pane, or a hosted document's
title; `{directory}` is the active pane's working directory with `~` for home, or a hosted document's location. A token
with no value (a remote pane without a reported directory) is dropped with its separator. The default template is
`{context}`; Enterprise's gives "Workstation — Mac — ~" and "Workstation — Local: zsh — ~". The
frame title starts as "Jasper" (`TerminalWindow.java:27`) until the first title update.

### 10.5 Editor tabs

`TabStrip` has `EqualTabs` (today's `WindowTabs`) and `ClassicTabs`: always visible, content-sized, a status dot, a
persistent ×, a "+" after the last tab, the active tab on `surface.card` with a 2 px `tab.indicator` underline, 1 px
dividers, no motion. Both drive the same `TerminalDeck` callbacks mounted at `WindowContent.java:252`.

### 10.6 Status bar

`StatusCells` has `SlashSegments` (today) and `RuledCells`: cells divided by 1 px `border.divider` rules. On the left,
the session cell ("● Mac", the dot and the session name) and the folder; on the right, plugin items as cells ("Vault:
Locked", "1 SSH session"), the grid size, "UTF-8" (the encoding Jasper's sessions write, `TerminalSession.java:80`),
and the configuration warning as a `warning.ground` cell with "▲". When `palette = "run_window"` either layout adds a
leading accent item "▶ Run…" bound to COMMAND_PALETTE; while the Run window is open it shows as an active toggle, on
`surface.header.active` under `RuledCells`, as the Run Dialog mocks draw it, and on `surface.control.selected` under
`SlashSegments`. The status bar keeps `window.status_bar_height` (24 by default) against DESIGN_LANGUAGE
§4's 22 px, and the Run Dialog mocks, which show "▶ Run…", supersede the Workstation mocks, which leave it out.

### 10.7 Dock

- **Stripes.** `StripeSet` has `IconRails` (today's `ToolStripe`) and `LabelledStripes`: rotated text labels on
  `surface.stripe`, an open panel's label on `surface.control.selected`. A label starts with "N: " when the panel's
  toggle action is bound to a single digit with modifiers (⌘⌥1 on macOS, Alt+1 elsewhere), read from the live keymap,
  so today's bindings give "1: Sessions", "2: SFTP" and "3: Transfers". The stripe is as thick as its label's line and
  the rail's two edges, with its 1 px rule (the mocks' 22 + 1 under IBM Plex), the label centred across it;
  `window.rail_width` does not apply to it. Left-stripe labels read bottom to top, right-stripe labels top to bottom.
  The top group stands at the stripe's top and the second group (LEFT_BOTTOM, RIGHT_BOTTOM) at its bottom end, as
  IntelliJ's.
- **Bottom strip.** `LabelledStripes` adds a horizontal strip for the BOTTOM_LEFT and BOTTOM_RIGHT slots, with count
  pills for badges that carry a number. It is 24 px plus its 1 px rule, as the mocks draw it. `DockDrag` and the drop
  resolution gain horizontal group rectangles, as `Rectangle` math; the six slots are unchanged.
- **Frames.** `FrameStyle`: arc 0, gutter 0, a painted `DOCK_RULE` with a `DOCK_RULE_HIT` drag area, a header strip of
  `tool_window_header` height on `surface.header` (`surface.header.active` while focused), the hide glyph "−". A frame
  is also the focused one after any mouse press inside it (its content, blank space, its header strip, a control that
  takes no focus) and when the user opens it from its stripe or its View toggle action; both move the keyboard into it
  (its first control that takes keys, else the frame), as IntelliJ activates a tool window. A press in the terminal
  column hands the tint back; dragging a divider keeps it, and a plugin's own toggle opens a frame without activating
  it. A header detail is muted, and a digits-only detail is parenthesised ("Sessions (2)"). Kit `PanelHeaders` receive
  it as a host flag. Its arc and gutter are the window's kit metrics `WORKSPACE_SURFACE_ARC` and `WORKSPACE_GUTTER` (0
  in the classic catalog), which replace the static finals at `ToolWindowArea.java:53,55` (6.6).
- **Layout per look.** The default look keeps today's ui-state keys unchanged. A non-default look saves its dock under
  its provider id, so a first Enterprise start gets Enterprise's default anchors and a return to the default look finds
  the old layout:

```toml
[dock]
left_width = 280

[look."dev.jasper.enterprise".dock]
left_width = 260

[look."dev.jasper.enterprise".panels."dev.jasper.remote.panel"]
slot = "RIGHT_TOP"
order = 0
visible = true
```

- **Event Log.** Under Enterprise, `AppPanels` gives the Notifications panel the title "Event Log" and the default slot
  BOTTOM_LEFT; the default look keeps "Notifications" at RIGHT_TOP.

### 10.8 Dialogs

`DialogMapping.CLASSIC` draws OVERLAY and ATTACHED_SHEET dialogs inside their owner window, with the same modality, as
the 07 Credential Vault mocks show them:

- a card with the catalog's dialog radius on `surface.dialog`, a 1 px `border.hover` frame and the catalog's popup
  shadow, over a still scrim (black at 21 %: white content reads #C9CACC) covering the window below its title bar;
- a 26 px header strip on `surface.header.active` above a `border.window` rule, holding the `DialogSpec.swatch` and the
  title in the semibold UI face; the form below it does not repeat the title;
- the footer on `surface.footer` under a `border.window` rule, `Form.footnote` at its leading edge and the actions at
  its trailing edge;
- OVERLAY centred on the whole window, its title bar included, as the Unlock Vault mock places it; ATTACHED_SHEET
  centred horizontally and joined to the bottom of the title bar, with no top frame edge;
- no fade (10.9); keys, focus and closing as each presentation has them today.

A dialog whose owner has no window opens as a native dialog. Progress overlays (`OverlaySpec`) stay in the window,
square and still, with their label. With `windowTitled`, a FULL form does not repeat the title the native bar shows.
Form and dialog footers sit on `surface.footer` under a `border.window` rule.

### 10.9 Motion

`Motion.NONE` turns off the tab-strip motion, the palette reveal and the overlay fade, whatever `ui.animations.enabled`
says: the app's one animations supplier becomes `motion == STANDARD && settings.enabled()`, and every consumer
(`AppearanceBindings`, `PluginRuntime.animations`, `WindowCommandPalette`, `WindowTabs`) reads it, so every jasper-ui
`MotionPolicy` (`KitContext.motionPolicy()`) follows the one policy the look supplies. Indeterminate
progress draws a still bar with its label, as it does with animations off today.

### 10.10 View modes

Compact under a native-titled look hides the toolbar row and keeps the in-window menu bar, the only menu surface once
`useScreenMenuBar` is false. Zen is unchanged, and the Run window's home does not depend on either.

### 10.11 Splash and application icon

A look with `[branding]` (5.1) brands the start-up and the windows; `Branding.NONE` changes nothing.

**Icon.** The look's icons replace `ApplicationIcon.images()` in every window's icon list, and `icon-1024.png` becomes
the taskbar and Dock icon as the splash opens, in packaged macOS launches too (today they keep the bundle icon). The
packaged icons (`packaging/icons`) are unchanged.

**Splash.** A start that will open a window (not a launch handed to a running instance, not `--background`, not safe
mode) shows `Splash` once `PluginSelection` (4.8) has chosen a branded presentation and the desktop properties are
committed, until the first window is
shown or start-up fails. It is an undecorated, unfocusable window centred on the main screen, drawn as the 10 Boot
Splash frames and the animated mock's markup give it, the same in Light and Dark:

- 720 × 440 with a 1 px #8A8F96 border over a vertical gradient from #17346F to #102552;
- the art (`splash_art`, rendered with jsvg) across the top 250 px above a 1 px #8A8F96 rule;
- at (40, 278) the 64 px icon, then 16 px to its right the product name (the UI face, 30 px semibold, #FFFFFF,
  tracking −0.4 px) above the version line 5 px below (the data face, 12 px, #C7D6F2): the app version and the build's
  short commit, as About shows them ("1.0.0 · Build a3f9c21");
- 40 px from the sides and 30 px above the bottom, the step (12 px, #C7D6F2, ellipsised) at the left and About's
  copyright line (11 px, #9FB3DA) at the right, in place of the mock's "CopyRight Notice Here";
- a 6 px progress bar along the bottom edge: a #0B1A3C track under a 1 px #2A4580 top rule, and a fill whose vertical
  gradient runs #5A8BE0, #2F65CA at 60 %, #1F4C9E.

The steps follow start-up as it happens: "Starting Java runtime…", "Reading configuration…", "Loading plugin: <name>"
for each plugin as it starts, "Restoring workspace…" and "Starting <product> Workstation…". The fill is the step's
index over the step count, which is known once the plugins are selected. The splash has no motion: it appears and
closes at once and the fill moves without easing; the animated mock's fades and easing are not adopted (no animations,
10.9).

## 11. The Run window

### 11.1 Views

The same `PaletteController`, scopes, verbs, history, steps and `PaletteKeyRouter` drive both views. `PaletteView` is
extracted from `CommandPalette`'s controller-facing API with two implementations, `CardView` (today's card) and
`RunWindowView`, built by `PaletteViews.of`. The Run window is assembled from kit nodes only: hue squares are
`IconSize.SWATCH` icons, the help tile is `Icon("play.filled", BLUE)`, and the shortcut is the live binding as caption
text. No app code paints it.

### 11.2 Layout

From top to bottom, as in the two `06 Run Dialog` mocks:

- **Title row:** a header strip on `surface.header.active` with "Run", the live COMMAND_PALETTE shortcut (⇧⌘P), and × to
  close. The title row and empty areas drag the window.
- **Help row:** the ▶ tile and the fixed host string "Type the name of a session, command, file or snippet, and Jasper
  will open it for you." With a scope chosen, the row shows that scope's description.
- **"Open:" field:** its ▼ opens the scope menu (All, then each scope with its shortcut), today's mode list. A chosen
  scope shows as today's chip; typed scope prefixes and Tab work as today.
- **List:** a bordered list on `surface.card` with headings on `surface.tableHeader`. Its height is fixed at
  `palette.visible_rows` rows plus two heading rows, reduced when the window is too short for the home position;
  it does not shrink with fewer results. Each row has its scope's hue square, the title, the detail in the data face
  and a right-aligned tag (the row's shortcut).
- **Detail line:** "{ItemNoun}: {detail}", the label semibold and the detail in the data face ("Session:
  dustin@192.168.1.119:22").
- **"Open in a split pane" checkbox:** enabled when the selected row's scope has a SPLIT verb; cleared each time the
  window opens.
- **Footer** on `surface.footer`: the primary button, labelled with the selected row's verb 0 ("Connect"), running the
  SPLIT verb when the box is ticked, and reading "Open", disabled, with no selection; Cancel; Browse…. The order follows
  Windows Run; the mock's layout overrides DESIGN_LANGUAGE §6's "primary right-most", which still governs Form footers.

### 11.3 Headings and copy

`PaletteEntry.Header` gains `Kind kind`: RECENT, USED, SUGGESTED, SECTION (a chosen scope's own section label, shown as
written in both views) and MATCHES (a scope's heading under a query, carrying the scope label). The card keeps today's
strings: "Recently used", "Most used" or "Suggested", and scope labels. The Run window shows "Recent", "Most used",
"Suggested", and under a query one "Matches" heading for all MATCHES headers, whose muted right text reads "i of n": the
selected row's position among the matching rows, where n leaves out "More in…" rows; with a "More in…" row selected
the text is blank. "More in…" rows stay rows. `RECENT_ROWS` becomes 3 in both views
(section 13).

### 11.4 Keys and verbs

The router's keys are unchanged: Enter, Cmd/Ctrl+Enter, Shift+Enter, Tab and Escape. The contract is "Enter runs the
view's primary verb": verb 0 in the card; verb 0, or the SPLIT verb when the box is ticked, in the Run window. The
second and third verbs (Cmd/Ctrl+Enter, Shift+Enter) keep their keys and also appear in the row's context menu.
Inline `PaletteStep` forms render in the list region exactly as they do in the card.

### 11.5 Home and saved place

`PaletteHome` wraps `home()` (`WindowCommandPalette.java:375-380`) and the attach test (`:289-293`). It and its two
implementations live in `dev.jasper.app.workspace`, beside the workspace geometry they use, and `WorkspaceChrome.of`
picks them from `PaletteLook.home`; `PaletteViews.of` picks only the view (`palette` cannot depend on `workspace`).

- `TitleFieldHome` is today's: dropped from the title field, centred in Compact.
- `UpperHome` is centred across the window's workspace with its top a seventh of the workspace's height down, moved up
  to end at the workspace's middle while that keeps it in the dock work area, and clamped into the work area by
  `PALETTE_WINDOW_MARGIN` (the user, 2026-10-18), `RUN_WINDOW_WIDTH` wide. It attaches within reach of its home across
  and down: `|left − home.left| ≤ reach && |top − home.top| ≤ reach`. It has no centre or top-fifth guides; home is its
  only snap target. Double-clicking the title row forgets the saved place and returns home. The Run window is about
  364 px tall with 5 visible rows and 427 px with Enterprise's 8, so with 8 rows it is entirely in the upper half only in
  windows about 1000 px tall or more; in shorter ones it sits just under the toolbar row and crosses the middle.

The saved place is keyed by home kind: `TitleFieldHome` keeps `[palette] center_x/top`; `UpperHome` saves
`[palette.run_window] left/bottom`, fractions of the workspace, shared by every window:

```toml
[palette.run_window]
left = 0.0
bottom = 1.0
```

### 11.6 Opening, focus and closing

Openers: ⇧⌘P, the "▶ Run…" status item and the toolbar's Search everywhere field; an open Run window takes focus
instead of opening again. The window is modeless: it closes only on Escape, ×, Cancel or running a row. It ignores the
card's other dismissals: a tab switch, the window losing focus, and the opener or the active scope's shortcut pressed
while it is open, which focuses it (`PaletteController.java:72`, `WindowContent.java:272`, `:1382`); it goes with its
window. Clicking a pane hands focus back to it and leaves the window open; the overlay passes presses outside
the window through. There is no
reveal or other motion, and the shadow uses the catalog's popup values.

### 11.7 Browse… and File › Open File…

Browse… runs the new File › Open File… action (section 13): Jasper's file chooser, then the routing Cmd-click uses
(`JasperApplication.openFileAt`, `:534-539`). The Run window stays open (11.6).

## 12. Settings

### 12.1 Shell

`SettingsChrome` changes values only:

| Field | Default look | Enterprise |
|---|---|---|
| `integratedTitle` | true (`SettingsDialog.java:174-177`) | false: a native title bar with "Settings" |
| `collapsibleNav` | false: fixed headings | true: ▼ section nodes; a click selects the page, the triangle toggles; the state is not saved |
| `inlineReset` | true: the row's Reset button | false: "Reset to Default" in the row's context menu |
| `hiddenKeys` | `window.native_decorations` on macOS (as today) | `window.native_decorations` (implied by the look), `ui.animations.enabled` (no motion) and `window.rail_width` (the stripes fit their labels) |
| `fontRowCopy` | "Use platform font size" | "Use the default font size"; the default is `[type].base` (12) |

The navigation surface comes from the `navigation_surface` slot, metrics from the catalog, and the footer sits on
`surface.footer`. Draft and Apply, search, back and forward, and the immediate Plugins page are unchanged.

### 12.2 Pages

Pages branch once at their composition root on `Composition`:

- **Appearance:** View mode stays; theme import and remove move from the gear to a ⋯ menu beside the theme dropdowns
  (CLASSIC).
- **Window:** the Toolbar choice renders as radios through EXCLUSIVE; Panel rail width is hidden (12.1).
- **Notifications** (CLASSIC): one grouped table with Group, Display and Source columns; a heading row per owner with
  "N groups"; the Display cell is a DROPDOWN ("Card ▼"); Source reads "Default" or "Custom".
- **Keymap** (CLASSIC): the tree gets Action, Scope and Shortcut columns; the buttons read "Find by Shortcut" (the
  recording search), "Expand All" and "Collapse All". Editing a row opens an inline band under it on
  `surface.rowHover`: the recorder drawn as a field (`surface.input`, the focus ring, the armed `status.danger` dot and
  "Press a key combination…"), Cancel and Set to its right, and the hint line below with the ⋯ menu of shortcut
  actions; clicking the field starts recording.
- **Plugins** (CLASSIC): checkbox rows without the plugin icon; Overview and Details as TABS in both looks (the default
  draws today's segment track); install from disk and uninstall move to ⋯ menus.
- **Shell** (CLASSIC): environment variables as a Name/Value table with "Add Variable…", which opens a sheet with Name
  and Value fields; Edit… and Remove in the row's context menu. With no variables the table is hidden and the row
  shows only "Add Variable…" at the right, as the mock does.
- **Untitled leading groups** (both looks, section 13): a page whose first group has no title titles it with the page
  name (Command Palette, Desk Buddy, Terminal, Cursor, Shell, Credential Vault, and generated plugin pages); System
  Settings' first
  group is titled "General".
- **Plugin settings** (both looks): host rows render `stringList` with the ordered-list editor.
- **Interface font:** the Font row's "System" means the look's standard face (5.6); under Enterprise, unticking "Use the
  default font size" shows the stepper starting at 12.

## 13. Changes to the default look

Approved for both looks (decisions 15 and 17), made in plan 3, which updates the affected reference pairs; calibration
stays green otherwise:

- **Menus and actions.** Remote's "SSH" menu becomes entries in the Session menu (Connect to SSH Host…, the hosts
  toggle, Split with Same Host, New Host…, Import from ~/.ssh/config…, and the tunnels group) and the Transfer menu
  (Open SFTP here, Transfers); File Server adds a new Share Folder… action (what the empty state's button runs today,
  `FileServersPanel.java:138`) and New File Server… to Transfer. A new app action, File › Open File… ("Open a file with
  the default editor"), with no default binding (the Editor plugin's Open in Editor… keeps cmd+o), opens Jasper's file
  chooser and routes the file as Cmd-click does.
- **Copy.** Vault's status item reads "Vault: Locked" or "Vault: Unlocked"; the Snippets scope's first verb reads
  "Insert" (its other verbs keep their labels); the untitled leading groups of 12.2.
- **Controls.** File Server's warning becomes a WARNING Notice; TFTP's login becomes a read-only field reading "No
  login (TFTP has none)"; the folder becomes a read-only field ("No folder selected") with a separate "Choose…" button;
  plugin `stringList` host rows use the ordered-list editor; Project Index's Location column uses the data face.
- **Labels.** Plugin form field labels are sentence case: "Via host", "Jump host", "Reachable from", "Listen on",
  "Forward to".
- **Palette.** `RECENT_ROWS` 2 → 3.

## 14. The Enterprise plugin and its consumers

### 14.1 Contents

`plugins/enterprise` (`:jasper-plugin-enterprise`), compiled against the SDK only:

- `src/main/resources/plugin.toml` (4.1);
- `presentation/enterprise.kit.toml` (5.1) and `presentation/fonts/ibm-plex-sans-regular.ttf`, `-medium.ttf`,
  `-semibold.ttf`, `ibm-plex-mono-regular.ttf` and `-medium.ttf`;
- `presentation/icons/icon-16.png` to `icon-1024.png` (5.1), copied from the 09 App Icon set (9b, Narrow Square
  Prompt), and `presentation/splash/art.svg`, the 10 Boot Splash mock's art;
- `themes/enterprise.json` (7.6);
- `dev.jasper.enterprise.EnterprisePlugin`, whose `start()` only calls
  `context.services().publish(Workstation.class, new Workstation() { })`;
- `dev.jasper.enterprise.api.Workstation` and its `package-info`.

`stagePlugins` (`jasper-app/build.gradle.kts:41-57`) gains `:jasper-plugin-enterprise` into `dev.jasper.enterprise`, so
the plugin is bundled.

IBM Plex (OFL-1.1) gets a `licenses/notices.toml` component in the app with the OFL text in app resources, and
`verifyThirdPartyNotices` also requires every font file in a bundled plugin's resources to be listed in a component's
new `files` key. The plugin has no requirements and imports no plugin API, and a gate check keeps it so: cycle detection
counts optional edges.

### 14.2 The API

```java
/** Published only while the Enterprise presentation is active; a plugin that finds it composes its classic layout. */
public interface Workstation { }
```

Presence only: the equivalent of IntelliJ's optional dependency on `com.intellij.classic.ui`. The toolbar control is
generic SDK (`ToolbarItem.View`), the menus are standard menus, and anchors and compositions belong to their owners.

### 14.3 Consuming it

Remote, File Server and Vault:

- add `{ id = "dev.jasper.enterprise", version = ">=0.1", optional = true }` to `requires`;
- add `compileOnly(project(":jasper-plugin-enterprise"))`;
- add `dev.jasper.enterprise.api` to their `declaredImports` (`gradle/plugin-architecture.gradle.kts:10`);
- confine the lookup to `dev.jasper.remote.workstation`, `dev.jasper.fileserver.workstation` and
  `dev.jasper.vault.workstation`, which turn
  `services().find(Workstation.class)`, wrapped in `catch (NoClassDefFoundError absent)`, into a plugin-local
  `Layout.CLASSIC` or `Layout.ROWS`;
- `verifyPluginArchitecture` gains a check that only those packages reference `dev.jasper.enterprise.api`, and
  AGENTS.md records it;
- tests use fakes of `Workstation`.

### 14.4 Remote

Under CLASSIC:

- **Toolbar view.** `SessionsToolbar` contributes a View in place of its Sessions dropdown: a DROPDOWN drawn as
  IntelliJ's run-configuration combo (its face the chosen row's label and the `application` icon) listing saved hosts as
  "name (address)", WSL distributions as "name (detail)", a separator and "Manage Sessions…", and an icon-only GHOST
  `run` button, tooltip "Connect", that runs `activateHost` for a host (`RemotePlugin.java:368-375`: focus a running pane
  for it, else open one) or `openDistro` for a distribution. Choosing "Manage Sessions…" shows the Sessions panel.
  The selection is shared per window with the Sessions tree and Properties, and falls back to the active SSH pane's host
  when nothing is selected. Under ROWS the Sessions dropdown stays as today.
- **Sessions.** A Tree with Name, Host and Port columns, group heading rows, status dots, "★" after favourites (toggled
  from the context menu) and the placeholder "Filter sessions". Below it, `Properties` titled "Properties — <host>"
  (Host, User and Port technical; "Credentials" reading "Vault: <name>", with a WARNING "Vault locked" while locked) and
  an ACTIONS row, "Edit…" and "Connect". The header detail is the count.
- **Tunnels.** A Table (Type, Local, Remote, Via) whose Type cell is a status dot and word. Activating a row toggles the
  tunnel; the context menu has Start, Stop and Edit…; the footer reads "<name> — inactive. Click to toggle."; with no
  tunnels, an Empty "No Tunnels" with [New Tunnel…] under the kept headers.
- **SFTP.** The navigation row ← → ⟳, a Separator, "Hidden files", a Separator, "Upload ▾"; while disconnected, a
  DROPDOWN "Choose Session…" and a disabled Breadcrumbs (its `ControlState`) reading "/" stay visible above an Empty
  "Not Connected" under the kept Name, Size and Modified headers; a Modified column. The header detail is the connection
  name with the active dot.
- **Transfers.** Its own BOTTOM panel, registered with `withToggleActionId(TransferUi.SHOW)`, so its stripe reads
  "3: Transfers". "Transfers" (`TransferUi.SHOW`, ⌘⌥3) shows it under CLASSIC and reveals the SFTP sidebar under ROWS,
  so the action is reachable in both.
- **Default slots** (in Enterprise's own dock layout, as in the Workstation mocks): Sessions RIGHT_TOP, Tunnels
  RIGHT_BOTTOM (`withSplit`), SFTP LEFT_TOP (registered without `withSplit` under CLASSIC), and Transfers BOTTOM_LEFT
  after Event Log.
- **Add Host.** The Keys list sets `emptyText` "No keys"; the Authentication choice sits in the control column (6.4);
  the side column reads Add Stored Key…, Remove, Move Up, Move Down (6.4), which supersedes the mock's "Add Key…" and
  two-button column.

In both looks: the Session and Transfer menus (section 13), `itemNoun "session"`, hue GREEN, and the split verb marked
SPLIT.

### 14.5 File Server

Under CLASSIC: a Table of Folder (the name plus a protocol Chip), Address (data face) and Status (a status dot and
word), at LEFT_BOTTOM (`withSplit`) beneath SFTP. Activating a row starts or stops the server, the footer reads "<name>
— stopped. Click to toggle.", and the context menu has Start, Stop, Edit…, Copy URI and the fingerprints. The selected
server's details appear as `Properties` under the table. With no servers, an Empty "No File Servers" with [Share
Folder…] under the kept headers. In New File Server, the authorized keys become a Table (Type, Fingerprint, Comment)
with the empty text "No keys added" and the existing ACTIONS row; the mock's colon after "Authorized keys" is a fixture.

In both looks: the Notice, the read-only fields, the Transfer menu entries (section 13), `itemNoun "file server"`.

### 14.6 Other plugins and the app

- **Vault:** its classic manager (14.8); "Vault: Locked"/"Vault: Unlocked"; the host maps its OVERLAY and
  ATTACHED_SHEET dialogs (10.8) and draws its PROMPT forms through `PromptFace`. `itemNoun "login"`.
- **Snippets:** "Insert"; `itemNoun "snippet"`, hue PURPLE.
- **Indexer:** the Location column's data face; Files `itemNoun "file"`, hue AMBER.
- **Editor:** Recent files `itemNoun "file"`, hue AMBER.
- **History:** `itemNoun "command"`, hue PURPLE.
- **Remote tunnels scope:** `itemNoun "tunnel"`.
- **Sample:** `itemNoun "greeting"`.
- **App Commands scope:** `itemNoun "command"`, hue BLUE.

### 14.7 Every action stays reachable

Classic compositions keep each default-look gesture reachable: Tunnels' and File Servers' row switches become row
activation plus the context menu; File Server's details card becomes Properties; Remote's toolbar keeps WSL launch and
Manage Sessions; the Transfers action works in both; the Vault manager's cards and menus become its toolbar row, table
and Details pane. `CompositionParityTest` lists each default-look gesture of Remote, File Server and Vault and checks its
classic path.

### 14.8 Credential Vault

Under CLASSIC the Credential Vault window takes the 07 Credential Vault mocks:

- **Toolbar row.** A MenuButton "+ New" whose items are Login, SSH Key and Secure Note, a separator and Generate SSH
  Key…, each with its hue swatch (BLUE, GREEN, AMBER and PURPLE); a Separator; Edit…, Delete, Copy
  Username and Copy Password; a Separator; Lock Vault followed by its shortcut as a muted caption; and the SEARCH field
  "Search vault" at the trailing edge. An entry the selection does not allow is disabled.
- **Categories.** A Tree with counts: All Items, expanded, over Logins, SSH Keys and Secure Notes, each with
  its swatch and its count.
- **Items.** A sortable Table: Name (the entry's swatch and name), Username (data face), Type and Modified ("Sep 24,
  2026"), with the footer "<shown> of <total> items".
- **Details.** The selected entry's swatch and name, its kind and method muted below ("Login · Password"); a titled
  group "Fields" with one row per field: its label, its value in the data face (a secret masked) and Copy, with Show
  before Copy for a secret; Properties for Modified and Used by (the plugins granted the entry, else "Not used"); Edit…
  and Delete…. With nothing selected the pane reads "Select an item to view its details."
- **Status row.** Cells for the store ("● Unlocked · Keychain"), the auto-lock setting ("Auto-lock: 15 min") and the
  vault file's path in the data face, and the PRIMARY Done at the trailing edge.
- **Editors.** ATTACHED_SHEET forms titled "Add Login", "Add SSH Key" and "Add Secure Note" ("Edit …" when editing),
  with the kind's `DialogSpec.swatch`, in titled groups. Add Login: Login (Name with the placeholder "e.g. Production
  server", Username) and Authentication (Method as Password or Private Key radios, then Password with a Show checkbox,
  or the key). Add SSH Key: SSH Key (Name, "e.g. Work laptop key"; Private key and Public key with Browse…; Comment,
  "Optional"). Save stays disabled until the form is valid.
- **Unlock Vault.** The OVERLAY PROMPT form of 10.8: the lock tile beside "Enter your master password to use saved
  credentials.", "Master password:", a "Show password" checkbox, `Form.footnote` "Auto-locks after <n> min idle", and
  Cancel and Unlock, disabled while the field is empty.

The default look's manager is unchanged. The mocks' names, paths, dates and counts are fixtures, and so is "Remember in
Keychain" (section 18).

## 15. Errors and diagnostics

| Situation | Behaviour |
|---|---|
| Kit file invalid | The provider is SKIPPED with "Presentation invalid: <key>: <reason>"; the default look; the plugin-problem notice once per digest |
| Two providers enabled (a hand-edited `plugins.toml`) | One wins by origin then id; the others are SKIPPED with "the <winner> look is active" and post no notice |
| `--safe-mode` | Every presentation-declaring plugin is DISABLED with "safe mode uses the standard look" |
| Matching `pending` or `failed` attempt | SKIPPED with "did not finish starting with this look last time"; one Warning notification with Try Again |
| The kit fails to install while the theme installs under the default kit | The fallback presentation (4.7); the record says `failed`; the next start uses the default look |
| The provider fails to load or start | FAILED; the record says `failed`; this session keeps the classic chrome without `Workstation` |
| The attempt record unreadable | Treated as absent and logged: a fresh try |
| The attempt record lock busy or unwritable | Logged; the launch proceeds without a record |
| A plugin theme file invalid | That theme is skipped and reported, as folder themes are |
| A `[defaults]` theme did not load | Dropped from the defaults; the core default applies; named in the plugin's problem notice |
| A plugin theme id taken by a built-in or another plugin | The losing theme is dropped with a notice |
| Import of a reserved plugin theme id | Refused: "<Theme> is provided by the <Plugin> plugin and can't be replaced" |
| A saved pick of a dormant theme | The default theme, silently; the pick is kept |
| A font fails to register | That face falls back to the platform face; a notice; the look stays |
| A toolbar view's root breaks admission | At mount: contained, logged against the plugin naming the node, and the window shows no view; on update: `IllegalArgumentException` naming the node |
| A toolbar view factory throws | Contained by `Containment` (logged against the plugin, counted and reported as an incident); that window shows no view |
| A plugin built on 0.17 claims `<0.19` and uses a renamed member | Refused at load: "references removed SDK member <member>; rebuild it for SDK 0.18" |

`StartupTrace` reports `look=<id>`. `PluginStatus` reasons appear on the Plugins page and in the log.

## 16. Testing

Everything below runs headlessly inside `./gradlew check` on every OS unless noted.

### 16.1 The guarantee

1. **Static closure.** `DerivedColorTest` evaluates every recipe symbolically: each input is a required, ruled,
   optional or earlier derived role, and the order is acyclic, so every theme that loads is covered. `PaintSpecTest`
   checks that each face's declared `(slot, against, guard)` table is complete and names existing roles, including the
   classic button states, the Run window's title row, the active "▶ Run…" item and the count pills.
2. **Painted contrast.** `ShippedControlReadabilityTest` generalises into `PaintedContrastTest`: it renders every face
   and chrome painter under both shipped kits and samples the declared pairs as painted, over Slate Cobalt Dark and
   Light, the four Catppuccin flavours, Ubuntu Yaru Dark and Light, the converter fixtures (ocean, ocean-dawn, stray),
   Enterprise Light and Dark, and eight seeded degenerate palettes (all roles one grey, all black, all white, accent
   equal to the surfaces, maximum chroma, each text equal to its surface, each border equal to its surface, light
   colours under the dark flag).
3. **Kit matrix, as JUnit.** jasper-ui gains a `testFixtures` source set (`KitFixtures.context(theme, kit)`, the
   classic fixture, the gallery builders). `KitMatrixTest` renders the gallery for: the default and the Enterprise
   fixture kits over the full theme set; the complement kit (every point at the value Enterprise does not use, with
   Enterprise's tokens and type) and a checked-in strength-2 covering array over the 14 control points (seven kits)
   over Slate Cobalt Dark and Light, Latte, `marked()` and `custom()`. It asserts:
   `GalleryCoverageTest` under both shipped kits (every node, enum constant and new field, including the classic picker
   faces without tiles, the fields without leading glyphs and the in-cell DROPDOWN); every classic pixel under
   `marked()` maps to a role or derived role; `custom()` finds no literal; the frozen tables `classic-roles.tsv`,
   `classic-type.tsv`, `ui-color-keys-classic.tsv` and `derived-roles.tsv`; the default kit's calibration suites stay
   green unchanged.
4. **Chrome renders, as JUnit.** Test forms of `mockUiPreview`, `titleBarPreview`, `commandPalettePreview` and
   `settingsPreview` render under the default, Enterprise and complement kits, both palette views and the fallback
   combination (native title, window menus, default kit), and over a checked-in strength-2 array of `ChromeSpec` values
   across the 9 chrome points (`window_title` as its two shipped templates) honouring the `menu_bar`/`title_bar`
   constraint, asserting painted pairs and structure (a flush header, no gutter, the stripe labels) and writing images
   under `build/` for inspection. The opt-in `JavaExec` tasks and `tools/visual/pairs.json` stay opt-in tools. Each of
   plans 5 to 10 adds `tools/visual/pairs.json` crops pairing its Enterprise renders with the preserved mocks; the
   comparison stays opt-in.
5. **The shipped kit.** `EnterpriseKitRenderTest` (jasper-app tests) parses the real `enterprise.kit.toml` with the
   production `KitParser`, registers the real Plex fonts from `plugins/enterprise/src/main/resources`, and renders with
   the jasper-ui test fixtures. `EnterpriseFixtureTest` checks that the parsed kit's metrics, type scale, variants and
   colour slots equal the jasper-ui Enterprise fixture.
6. **Conformance.** `PresentationConformanceTest` checks that the shipped kit lists every key, every token is in range,
   the constraint holds, the fonts decode and every weight resolves to its family, the theme loads with both variants
   and the `[defaults]` themes resolve, and the notices entries exist. Negative fixtures cover every error message of
   5.9; a light fuzz test (truncated, oversized, deeply nested and path-traversal inputs) produces only problems.
7. **Architecture.**
   - The variant-read scan: `ChromeSpec` and `ControlVariants` only in `dev.jasper.app.presentation`,
     `dev.jasper.ui.kit` and `KitFaces`; the `WorkspaceLook`, `ShellLook` and `PaletteLook` variant enums only in
     `WorkspaceChrome.of`, `ShellChrome.of` and `PaletteViews.of`; `DialogMapping` only in `AuxiliaryWindows`; `Motion` only in the application's animations supplier; `Composition` only in the listed roots.
   - `StrategyIsolationTest` (ASM): faces never reference `Kit`, `ControlVariants` or `ChromeSpec`, and the reconciler
     references control classes only; `ProjectionReadTest` keeps the palette strategies off the kit. The app's workspace
     strategies may read the installed kit's metrics; `VariantReadTest` and `ChromeSpecReachTest` keep variants out of
     them.
   - `StaticDefaultTest`: no `DesignMetrics.DEFAULT` read or static metric final outside the default catalog, including
     `ThemeManager`'s split divider.
   - The package DAGs (`verifyUiArchitecture`: `kit` depends on `metrics` and `theme` only, `metrics` on `theme` only,
     and `swing` gains `kit`;
     `verifyApplicationArchitecture`: the edges of 9.2), `dev.jasper.app.dock` JDK-only, `KitValuesTest` lossless, the
     Enterprise confinement check (14.3).
8. **Runtime.**
   - `PresentationSelectionTest`: origin then id; whole-plugin skip; invalid kits skipped; safe mode; the effective
     rule in `admit`; the one selection consumed by start; the one-provider transaction for enabling and consenting; the
     catalog's prediction equals the launch, including after an attempt-record skip.
   - `PluginStateStoreOptInTest`: the opt-in survives `remove(id, false)`, `discardPending` and the pending removal
     through maintenance; a USER copy with an opt-in entry needs review.
   - `PluginCatalogTest` and manager tests: an off-by-default bundled row is unchecked; one click writes the opt-in; the
     banner appears and names a provider turned off.
   - `PresentationAttemptTest`: `pending` only before a window or a rehearsal; none for `--no-window`, the background
     exits, headless and `quitNow`; cleared by an orderly quit; a nonzero exit before proof keeps `pending`; `proven` at
     `FIRST_PAINT` and after `Rehearsal.paint`;
     a digest or build change; Try Again; a provider start failure records `failed`; `EnterprisePlugin.start` cannot
     throw.
   - `ThemeControllerFallbackTest`: the four steps of 4.7 and which of them blame the kit.
   - `PresentationReachTest`: every `KitContext` construction site, window, dialog and rehearsal carries the
     presentation, the fallback value included.
   - `DefaultsPrecedenceTest`: a fresh home under Enterprise opens its first window with the kit's defaults; a saved
     value wins; Reset gives the effective default; picking the core default under the kit stages an explicit value; the
     audit of 5.8.
   - Strategy-construction tests: every strategy builds for every projection value without throwing.
9. **Behavioural contracts** (the testkit fake and the host; a disagreement is a host bug).
   - `KitContractTest` and `HostedKitContractTest` run for both kits: a checkbox click emits `Toggled`, a radio
     `Selected`, a folder tab `Selected`, spinner arrows `Edited`, the ordered side column `Removed` and `Reordered`,
     and a read-only field nothing.
   - `PaletteViewContractTest`: Enter runs the view's primary verb (card verb 0; Run verb 0, or SPLIT when ticked),
     other verbs and keys identical, the SPLIT checkbox gating, "i of n".
   - `PluginContractTest` cases: `ToolbarItem.View` (admission refusals, one view per open window, none for the
     rehearsal, closing with the window and the registration, event routing); SESSION and TRANSFER; Notice; Properties;
     tree columns; cell nodes; heading rows; `empty` and `emptyText`; TABS; read-only fields; Separator placement;
     SWATCH; EXCLUSIVE with 2 to 4 options; palette `hue`, `itemNoun` and `role` validation; `presentation.provide`;
     the `declareBundled` one-active rule.
   - `CompositionParityTest` (14.7).
10. **Guards.** `ColourLiteralScanTest` with the plugin-theme exemption and its loader test; every plan adds its
    `docs/design/theme-engine/migration.csv` rows (`ThemeMigrationCoverageTest`); `RemovedThemeLayerTest`;
    `verifyThirdPartyNotices` with font files.

### 16.2 GUI checks for the user

Agents never launch the GUI or train the AOT cache. The user checks, per plan: the macOS in-window menu bar and native
title, and Windows and Linux NATIVE with the caption colour; the Run window's drag, attach to home, saved place, focus
hand-back and modeless behaviour; stripe drag and drop including the bottom strip; the classic in-window dialogs;
Enterprise Dark against the Dark Mode mock; the splash and the Dock or taskbar icon on a cold start; the Credential
Vault window; and Enterprise's cold-start time against the default look.

## 17. Documentation

Smallest edits:

- `docs/plugin-ui.md`: delete the checkbox rule at line 57; the re-documented contracts and the new vocabulary of 8.2
  and 8.3; the renames.
- `docs/plugin-authoring.md`: the `presentation`, `themes` and `enabled_by_default` keys, `presentation.provide`, and
  consuming `Workstation`.
- `docs/plugin-authoring.md`, `jasper-sdk/README.md`, `docs/sdk-icons.md` and `docs/examples/external-plugin`: the
  0.18 SDK range (8.5).
- `docs/sdk-architecture.md`: one active presentation, safe mode dropping presentations, the opt-in.
- `docs/ui-architecture.md`, `docs/ui-maintenance.md`, `jasper-ui/README.md`: the `kit` package, instance metrics,
  faces, derived roles and guards.
- `docs/app-architecture.md`: the `presentation` package, projections and chrome strategies.
- `docs/themes.md`: plugin themes, the override keys, reserved ids.
- `docs/configuration.md` and `config.example.toml`: "The default depends on the active look." for the five keys; the
  meaning of `"system"` and of an unset `ui.font.size`.
- `docs/command-palette.md`: the Run window.
- `docs/remote.md`, `docs/file-server.md`, `docs/credential-vault.md`: the menus and status text.
- `AGENTS.md`: the Enterprise plugin, the `presentation` package and the confinement of `dev.jasper.enterprise.api`.
- `docs/STATUS.md`: an entry per plan.

## 18. Out of scope

Live switching between looks, and mixed or per-window looks; plugin renderers or Swing; an SDK active-presentation
query or traits; the items of 8.4; per-look theme variants (`targetUi`) and a theme reset on switching; derived roles in
the SDK's `SemanticColor`; public tokens beyond the 11 metrics and two colour slots; an Enterprise settings page or a
look picker (turning the plugin on is the choice); `JASPER_PRESENTATION_SKIP`; AOT training of the classic strategies
before a measurement; a `PaletteResults` total; a Problems panel; Detach; rebinding any shortcut; the macOS screen menu
bar under Enterprise; remembering the master password in the Keychain (the Unlock Vault mock's "Remember in
Keychain"); the splash steps "Unlocking credential vault…" and "Indexing hosts…", which no start-up phase performs;
the product name anywhere but the splash; a new packaged application icon.

## 19. Delivery

### 19.1 Plans

Twelve plans, each a runnable deliverable leaving `./gradlew check` green, written with `superpowers:writing-plans`
and named `docs/superpowers/plans/YYYY-MM-DD-enterprise-N-<slug>.md` (the date each is written). Each adds its
`migration.csv` rows. Order: 1 to 12 in sequence; plan 10 needs 3, 4 and 5; plan 11 needs 5 and 6.

Plan 3 is delivered as five plans (the user, 2026-10-15): 3a `sdk-0-18-renames` (8.1, 8.3, 8.5, the 4.2 constant and
`KitCompatibility`); 3b `sdk-0-18-controls` (Notice, Properties, `ChoiceKind.TABS`, `Field.readOnly`,
`PickerFace.technicalCaption`, `EditorHint.technical()`, Separator, `IconSize.SWATCH`, `Form.footnote` and the
EXCLUSIVE validation); 3c `sdk-0-18-tables` (`Tree.columns`, `TableRow.cellNodes`, `TableRow.heading`, `Table.empty`,
`Table.emptyText` and `ItemList.emptyText`); 3d `sdk-0-18-chrome` (SESSION and TRANSFER, `ToolbarItem.View`,
`ScopeSpec.hue` and `itemNoun`, `PaletteVerb.role` and their app mirrors, `DialogSpec.swatch`, and the bundled scopes'
nouns, hues and SPLIT roles of 14.6); 3e `default-look` (section 13 with its reference pairs). Each of 3b to 3d renders
its additions in the default look and adds them to the testkit fake and the contract suites.

| # | Slug | Runnable deliverable |
|---|---|---|
| 1 | `kit-seam` | `dev.jasper.ui.kit` with `Kit.DEFAULT`; instance `DesignMetrics` and `TypeScale`; `KitContext.of` at every site; `ChromePainter` and `NativeStyles` instances; the static finals removed; `FlatLafInstaller.install(theme, kit)` with the install-time FlatLaf keys and `UiKeySet`; `ThemeManager.install(theme, kit)`; `StaticDefaultTest`. Zero pixel change |
| 2 | `derived-roles` | `DerivedColor`, `DerivedPalette`, override keys and their parse table, the guards, the converter keys and goldens, `PaintedContrastTest` with the degenerate palettes, jasper-ui `testFixtures` and the default-kit matrix in `check`. Zero pixel change |
| 3 | `sdk-0-18` | SDK 0.18.0: the renames and `KitCompatibility`, every addition of 8.2 with its default rendering (the Notice card, toolbar views in today's header, SESSION and TRANSFER), the re-documentation, the `presentation.provide` constant and its javadoc, the testkit and contracts; every manifest bumped; the bundled scopes' nouns, hues and SPLIT roles (14.6); the default-look changes of section 13 with their reference pairs; THD's rebuild coordinated |
| 4 | `classic-catalog` | `DesignMetrics.CLASSIC`, `TypeScale.CLASSIC`, `MetricTokens`, the 14 face pairs and `KitFaces.of`, the classic FlatLaf overlay, the Enterprise fixture kit, the full kit matrix with the complement and covering array, the isolation and variant-read tests |
| 5 | `provider` | The descriptor keys, the effective rule and opt-in, `PluginSelection` and the single selection, selection in `resolve`, safe mode, the attempt record, the fallback chain, reach, the plugin theme tier with reservation, exemption and dormant ids, `KitParser` and validation, `DefaultSource` and the audit, font registration and notices, `presentation.provide` in `DescriptorParser.CAPABILITIES` with its key rule, `DesktopFlags` computed (plan 6 applies them), the Enterprise plugin with its kit, fonts, theme (Light and Dark, 7.6), `dev.jasper.enterprise.api.Workstation` and its publication in `EnterprisePlugin.start`, its `stagePlugins` entry and its `dev.jasper.enterprise` `migration.csv` row, conformance and the shipped-kit render, compared with the Workstation and Dark Mode mocks. Until its plan lands, a chrome value maps to the default projection. Turning Enterprise on gives classic controls, metrics and fonts |
| 6 | `window-chrome` | `DesktopFlags` applied: native titles and in-window menus; `ClassicMenuBar`, `ClassicHeader` with `window.toolbar` and toolbar views, `TitleFormat`, `RuledCells` and the leading "▶ Run…" item (opening the palette's current view), `ClassicTabs`, `DialogMapping` and the classic in-window dialogs (10.8), NSAppearance and captions, `Motion`, Compact under a native title; the JUnit chrome renders of 16.1.4 |
| 7 | `dock-chrome` | Labelled stripes and their numbers, the bottom strip and pills, frames and header strips, horizontal drop geometry, the dock layout per look, Event Log; the chrome renders extended |
| 8 | `run-window` | `PaletteView` and `PaletteHome` pairs, typed headings and Run copy, the modeless window and focus hand-back, the saved place per home kind, Browse…, the fixed list height; the chrome renders extended |
| 9 | `settings` | `SettingsChrome`, the collapsible navigation, Reset in the context menu, hidden rows, the Appearance, Keymap, Notifications, Plugins and Shell compositions, ⋯ menus, the font row copy; the chrome renders extended |
| 10 | `plugin-compositions` | `Workstation` consumption; Remote's toolbar view, Sessions tree and Properties, Tunnels table, SFTP row, Transfers panel and anchors; File Server's table, Properties and keys table; Vault's classic manager, editors and Unlock form (14.8); the confinement check; `CompositionParityTest` |
| 11 | `branding` | `[branding]` and its validation, the `Branding` projection, the Enterprise icons and splash art, the runtime application icon and `Splash` with its start-up steps (10.11); a render of the splash compared with the Boot Splash frames |
| 12 | `intellij-classic` | The user's 2026-10-18 direction: IntelliJ's classic icons (vendored, attributed) under `[chrome] icons`, the classic header's icon buttons and `window.toolbar` as a look default, SDK 0.18.1's icon-only GHOST toolbar buttons with `run` and `application`, Remote's run-configuration combo and Run, the Enterprise theme as ports of IntelliJ Light and New UI Dark, a pressed tool window activated, the Run window's upper home |

### 19.2 Amendments

Each amended specification's status banner gains one line, "Amended on 2026-10-15 by the [Enterprise presentation
design](2026-10-15-jasper-enterprise-presentation-design.md): <what>; everything else in this document stands.", with
<what> as below.

| Specification | Superseded text | <what> |
|---|---|---|
| [Single layout](2026-09-25-jasper-single-layout-design.md) | line 11, "a single layout and a single theme engine" | "one default layout plus host-implemented variants, selected once at start from presentation data and read only in factories and projections; a runtime-switchable skin stays rejected" |
| [Compact density](2026-10-12-jasper-compact-density-design.md) | decision 4 (line 23) and "not a second scale" (line 35) | "one density per kit catalog; there is still no density setting" |
| [Theme engine](2026-10-04-jasper-theme-engine-design.md) | lines 166-167 (switches, checkboxes, radios) | "these are rules of the default look; the Enterprise look draws checkboxes, radios and titled groups for the same descriptors" |
| [Dock layout](2026-10-01-jasper-dock-layout-design.md) | decision 1's two stripes (line 24) and the rounded filled square (line 127) | "stripe, frame and bottom-strip presentation and saved layouts are per look; the model and the six slots are unchanged" |
| [Plugin SDK](2026-09-21-jasper-plugin-sdk-design.md) | line 376 "never for chrome"; section 14's "plugin-supplied chrome components"; line 924 `--safe-mode` | "chrome only as admitted toolbar views and declarative presentation data; for presentations, DEV outranks BUNDLED, which outranks USER (the `editor.provide` precedent); `enabled_by_default` keeps a bundled plugin off; safe mode also drops presentations" |
| [IntelliJ chrome](2026-09-24-jasper-intellij-chrome-design.md) | out of scope "a selector combo in the toolbar" (line 19) | "toolbar views (`ToolbarItem.View`) supersede this exclusion" |
| [Slate Steel](2026-10-12-jasper-slate-steel-themes-design.md) | out of scope "plugin-contributed themes" (line 49) | "plugins contribute themes through the manifest `themes` key" |
| [Theme import](2026-10-13-jasper-theme-import-design.md) | section 2.2 (lines 70-73) and the reserved id (line 108) | "a variant may add the derived override keys; plugin theme ids are reserved like built-in ids" |
| [View modes](2026-10-03-jasper-view-modes-design.md) | "Compact removes the entire workspace header" (line 15) | "under a native-titled look Compact hides the toolbar row and keeps the in-window menu bar" |
| [Settings dialog](2026-09-28-jasper-settings-dialog-design.md) | the page tree's chevron disclosure and rounded selection (lines 94-96) | "the integrated title row applies under a unified title bar; the Enterprise look gives Settings a native title, collapsible ▼ sections and a square full-width selection" |
| [Start-up speed](2026-10-02-jasper-startup-speed-design.md) | the pre-built hidden window (line 359) and contribution replay (line 252) | "both are keyed by presentation id and digest; toolbar views are never replayed" |
| [Start-up recovery](2026-10-14-jasper-startup-recovery-design.md) | §5.2 "is never retried" (line 166) | "the presentation attempt record covers a look that fails after the JVM starts" |
| [Command palette](2026-09-12-jasper-command-palette-design.md) | placement (line 43) and dismissal (line 65) | "under the Enterprise look the palette is the Run window, with its own home, saved place and closing rules" |
| [Palette scopes](2026-09-15-jasper-palette-scopes-design.md) | same-shortcut, outside-click and tab-switch dismissal (line 57) | "the Run window closes only on Escape, ×, Cancel or running a row" |
| [Snippets](2026-09-15-jasper-snippets-design.md) | the verb "Paste" (line 56) | "the first verb reads Insert" |
| [Palette plugins](2026-09-21-jasper-palette-plugins-design.md) | Snippets' verbs "Paste" (line 203) | "the first verb reads Insert" |
| [Remote](2026-09-22-jasper-remote-design.md) | "an SSH menu" (line 39) | "Remote's entries live in the Session and Transfer standard menus" |
| [Remote tunnels](2026-09-30-jasper-remote-tunnels-design.md) | the SSH menu's tunnels group (line 240) | "Remote's entries live in the Session and Transfer standard menus" |
| [SDK clipboard and Tools menu](2026-10-13-sdk-clipboard-and-tools-menu-design.md) | "Remote's SSH menu is unchanged" (line 104) | "Remote's entries live in the Session and Transfer standard menus" |
| [Vault](2026-09-22-jasper-vault-design.md) | the padlock status item (line 217) | "the status item reads Vault: Locked or Vault: Unlocked" |
| [Notifications](2026-10-02-jasper-notifications-design.md) | title "Notifications" and default region RIGHT (lines 165-166) | "under the Enterprise look the panel is titled Event Log, with default slot BOTTOM_LEFT" |
