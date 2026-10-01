# Theme system: remaining-work assessment and execution order

**Status:** Baseline assessment audited against `main@5fc76c7d`. Plan 2 implementation and headless acceptance are complete and independently reviewed, merged to local `main` on 2026-10-01 and verified there; not pushed. User GUI acceptance remains pending. Plans 3–5 remain future deliverables; Plan 3 has been expanded into five implementation-ready tasks against `main@14f9913d`, including the startup regression fix. The baseline table below records the state before Plan 2.

**Authority:** [Full design](../specs/2026-10-01-jasper-theme-system-design.md), with the recorded plan-1 deviations in
[plan 1](2026-10-01-theme-system-1-format-generator.md) and `docs/STATUS.md`. Keep those deviations explicit rather
than silently reverting shipped behavior to the design's earlier sketches.

## What exists

| Requirement | Evidence in the merged code | Remaining work |
|---|---|---|
| Native format, strict errors, inheritance | `appearance/ThemeToml`, `ThemeDocument`, `ThemeResolver`, `ThemeCatalog`; tests cover diagnostics, invalid candidates and cycles | Writer/export; generic bundled resource index; live file watching |
| Generator | `ThemeGenerator`, `UiTemplate`, 48 tokens, 16 syntax roles, `GeneratedTheme` with author and icon data | Materialize workspace values for `ui_complete` documents; expose syntax to SDK/Editor |
| Native defaults | `jasper-dark.toml`, `jasper-light.toml`; Dark parity tests | Legacy `ui.theme.variant = "light"` must select Jasper Light |
| IntelliJ support | `IntellijConversion` preserves UI, named colors, icons and author; `ThemeLoader` and `EditorScheme` still used at runtime | Inverse token/syntax conversion, standalone export, importer package/resource move, remove runtime archive loading |
| Workspace | `ResolvedTheme(Theme chrome, Palette palette)`; `ThemeState` selects only a terminal palette | Two complete generated themes, surfaces, icons, SDK and Editor changes |
| Plugin panes | Ordinary Swing LAF, sidebar `ToolWindowSurface` | Workspace-only restyler with reversible ownership and renderer audit |
| Catalog | Four built-ins are hardcoded; user TOMLs and plugin archives scanned | Indexed bundled TOMLs, full vendored collection, only native files at runtime |
| Settings | Appearance dropdown, Font & Colors terminal selector, IntelliJ Marketplace and Alacritty pages | One two-picker Themes page, preview, file actions and diagnostics |
| Compatibility | `terminal.colors` plus older `ui.theme.terminal` alias | Canonical `ui.theme.workspace`, variant alias, legacy file migration and ignored Marketplace key |
| Reload | ConfigService scans companion catalogs only on config changes or explicit reload | Detect edits/additions/deletions of theme files even while config.toml is unchanged |

The merge review already fixed author-specific Dracula/Material adjustments, contrast on overridden fills, invalid
parents hiding valid alternatives, and fallback masking inheritance cycles. They are regression requirements, not
unfinished tasks. The earlier warning about named-color `Icon`/`.border`/`.rowHeight` suffixes was withdrawn after
installed-color parity tests passed. Baseline validation: 7,720 tests, zero failures/errors, six skips; native visual
acceptance has not been performed.

## Four sequential deliverables

| Order | Plan | Shippable result | Depends on |
|---|---|---|---|
| 2 | [Workspace zone](2026-10-01-theme-system-2-workspace.md) | Implemented, headless-verified and independently reviewed: terminal, tabs, dividers, find, icons, Editor and SDK 0.12.0; GUI acceptance pending | Merged plan 1 |
| 3 | [Plugin pane restyler](2026-10-01-theme-system-3-plugin-panes.md) | Hosted Swing panes adopt the workspace; plugin-owned styles and cleanup remain correct | Plan 2 |
| 4 | [Conversion, vendoring and migration](2026-10-01-theme-system-4-conversion-migration.md) | Offline native catalog, reproducible conversion, safe upgrade; old stores removed | Plans 2–3 |
| 5 | [Themes page and live reload](2026-10-01-theme-system-5-page-reload.md) | Complete user flow: browse, stage, preview, import, duplicate, remove and edit/reload | Plan 4 |

Execute sequentially. These are separate runnable deliverables, not four concurrent branches with guessed APIs.
Use the repository's per-task implementer/reviewer workflow, one commit per task, fixes before advancing and one
whole-branch review per deliverable. Every plan ends with `./gradlew check`, XML counts, source hygiene, documentation
and a user-only visual checklist. No GUI or benchmark launch by the agent.

## Decisions needed to connect the design to the current code

These are proposed implementation details for review, not changes to the product's intended scope.

1. **Preserve the plan-1 data model additions.** Keep `GeneratedTheme.author`, `icons`, `ui_complete`, 48 tokens and
   the recorded derivation rules. `ThemeManager` may serialize native UI keys into in-memory IntelliJ JSON as its
   FlatLaf adapter; removing runtime IntelliJ loading means no JSON/archive *theme sources* in the runtime catalog,
   not rewriting FlatLaf. Keep author-specific install parity tests after the importer moves.
2. **Complete-theme workspace values cannot come only from terminal seeds.** An imported theme's console palette may
   differ substantially from its controls/editor. Plan 2 materializes a private FlatLaf defaults table without
   `UIManager.setLookAndFeel`, maps effective keys back to tokens, preserves explicit token overrides and leaves the
   chrome's UI-key payload untouched. A headless isolation test is a gate for this approach.
3. **Exactly one installed LAF.** Workspace changes publish app-native values and component styles, never install a
   second global LAF. `match` uses the same generated object for both slots and removes the workspace override layer.
   Keep the SDK out of `appearance`, `platform`, `workspace` and the new theme packages; adapt only in `app.plugins`.
4. **Configuration compatibility is one semantic setting.** The canonical key is `ui.theme.workspace`. Read
   `terminal.colors` and `ui.theme.terminal` as aliases; canonical always wins. Only values read from legacy alias paths map `light`/`dark`
   to Jasper Light/Dark; canonical names Light/Dark remain selectable and `match` stays `match`. Persist canonical values on an explicit save/migration, never rewrite
   config merely because it was read. `ui.theme.variant` is a path-specific alias: its `light`/`dark` values must not
   redefine a genuinely named theme called Light. Existing old-page Use buttons write the canonical setting.
5. **Missing themes:** missing chrome falls back to Jasper Light/Dark by that slot's last successfully applied
   darkness (dark at a fresh start); missing workspace falls back to `match`. This is session state, not a new
   persistence setting. A failed LAF install preserves both previous slots and sends no successful-change event.
6. **Transitional Alacritty:** plan 2 converts catalog palettes into generated themes in memory; plans 2–3 retain both
   old pages. Native Jasper themes win name collisions over transitional bundled Alacritty themes; user Alacritty
   overrides continue to shadow their bundled Alacritty counterpart. Diagnostics make cross-format collisions visible.
   Plan 4 tests selected legacy names through migration and preserves user themes with unique names when necessary.
7. **Zone boundaries follow mounting location.** SFTP/File Server/Tunnels rail panels remain chrome because sidebars
   are chrome in section 1. The same component mounted in workspace content follows the workspace. Renderer code must
   use a component-aware palette choice; blindly applying a global workspace palette to every plugin panel is wrong.
   An additive `WorkspacePalette.current(Component)` helper is planned for this reason; the design's no-argument
   `current()` and `Appearance.workspacePalette()` remain available.
8. **Convert before writing.** Import and migration use the same converter and deterministic native writer. Preserve
   all ordered `[ui]` data and IntelliJ icon-palette data, including author identity. Plan 4 extends the native format
   with a strictly validated `[icons]` escape hatch so a disk round trip does not discard data already retained in
   `ThemeDocument.icons`. Record that small format amendment in the spec/status when implemented.
9. **Migration is resumable and non-destructive.** Stage and validate every output before moving an original; never
   overwrite native files or legacy originals. A small journal records committed outputs and source hashes, so a
   crash between output publication and moving the original does not duplicate themes. A failed input stays in
   place and remains reported after `.migrated` exists; later manual import is the retry route.
10. **Plan 4's intentional interim UI:** remove both old pages only after migration and native resolution work.
    Keep the Appearance dropdown and a simple workspace dropdown that read the new catalog until plan 5 replaces
    the Font & Colors control with a Themes link. No dead “More Terminal Themes…” navigation.
11. **Offline means offline at runtime.** Only the explicit maintainer fetch script uses network. Conversion, builds,
    drift tests, first-start migration, previews and runtime import use local inputs. Verify upstream licenses and
    exact variants during the source-lock task; the design's 2026-09-30 table is not a substitute for vendoring evidence.
12. **Preview is staged, not an application-wide theme switch.** Draw a miniature window from the two generated
    values and a terminal fixture plus pre-tokenized JTextPane. No temporary global LAF installation, shell launch,
    Editor-plugin dependency or application theme event on picker changes.

13. **Combo popup clarification — approved by the user, 2026-10-01:** a workspace combo's own dropdown follows
    the workspace, including selection colors. Other menus, tooltips and dialogs remain chrome. Plan 3 tests the
    hidden combo list and separate popup boundaries without showing a native popup or following arbitrary invokers.
    The design's section 5.3 and out-of-scope list now record this exception.

## Requirement-to-task coverage

| Design sections | Owning tasks |
|---|---|
| 1: two zones/settings/one LAF | 2.1–2.5; 3.1–3.3 |
| 2: native location/index/identity | Existing plan 1; 4.1, 4.3–4.5 |
| 2.1–2.2: tokens/syntax | Existing plan 1 deviations; 2.2, 2.6–2.7; 4.2 |
| 2.3: diagnostics/fallback | Existing parser/resolver; 2.3; 4.4–4.5; 5.1, 5.4 |
| 3: generator/install/rollback | Existing generator; 2.2–2.3; retain parity in 4.1–4.3 |
| 4: built-ins and legacy light default | Existing native themes; 2.1; 4.3 |
| 5.1: two generated slots, identity for match | 2.2–2.3 |
| 5.2: workspace surfaces | 2.4 |
| 5.3: plugin styler, dynamic trees/exemption/restoration | 3.1–3.3 |
| 5.4: icon artwork at paint time | 2.5; 3.3 |
| 5.5: SDK 0.12.0 and testkit/contracts | 2.6 |
| 5.6: Editor role mapping and reload | 2.7 |
| 6.1: both converters, syntax/tokens/UI preservation | 2.2 transitional conversion; 4.1–4.2 |
| 6.2: locked sources, all upstreams, renames/licenses/index/drift | 4.3 |
| 6.3: runtime import/report/name conflicts | 4.4; 5.3 UI |
| 6.4: file and config migration/failures/marker | 2.1; 4.5 |
| 6.5: remove stores/runtime sources/resource relocation | 4.1, 4.6 |
| 7: filters, pickers, swatches and staged preview | 5.1–5.2 |
| 7: Import/Duplicate/Open/Remove/About | 4.4; 5.3 |
| 7: live watch/errors/search/navigation/View menu | 5.1, 5.4–5.5 |
| 8: independently green rollout | Final gate in each plan |
| 9: tests and native acceptance | Each task's red/green checks and each plan's final gate |

## Expected dependency shape

- `appearance` owns native documents/generation/install and the runtime catalog, depending on config/lifecycle and
  terminal config as today. It does not depend on the importer or the Settings UI.
- `themeimport` depends on native appearance values and terminal config, owns legacy parsing, writer/import/migration
  work, and never depends on settingsui or application. Composition injects transitional sources until plan 4 removes them.
- `themesui` depends on appearance, themeimport, settingsui, config, lifecycle, platform/windows and supported terminal
  packages. `SampleTerminal` moves here. `settingsui` receives its factory from application and never imports themesui.
- `workspace` owns zone lifecycle and styling. `platform` reads JDK client properties for zone darkness; it does not
  import workspace or SDK. `plugins` is the only app SDK adapter and gains an allowed appearance dependency.
- Update package-info allowlists and the architecture verifier's declared package inventory with each new package;
  never solve a cycle by disabling architecture checks.

## Execution gates and remaining uncertainties

The plan-2 private-defaults and plan-3 style coverage spikes are test tasks with explicit outcomes, not permission to
silently weaken the spec. If an existing FlatLaf control cannot be styled, the task supplies a direct-color fallback
or records the exact custom-paint limitation and SDK remedy. The full vendored set and its renames must be frozen
from actual source artifacts before the conversion drift golden files are committed. Do not pretend those external
inputs have already been acquired or verified by this planning pass.

The component-aware SDK helper landed in Plan 2. The proposed `[icons]` format extension remains in Plan 4 to
preserve data already retained in memory. The user-approved combo exception reconciles the design's popup wording
and is now recorded in section 5.3. These details preserve the design's five-deliverable structure.

No test run is needed for this documentation-only planning change. The previously verified baseline is cited above,
not represented as a fresh test of future implementation. The executor runs the full check for each runnable plan.
