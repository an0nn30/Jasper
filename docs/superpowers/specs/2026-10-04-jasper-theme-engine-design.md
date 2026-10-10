# Jasper theme engine and UI kit overhaul

Status: **Approved for implementation planning on 2026-10-04.** The user approved the written
specification, including the additional plugin-panel reference, with “alrighty, lets get started”.
Plan 1 engine/kit Tasks 1–14 are complete, including final verification/review and headless gallery/distribution evidence.
Plan 2 consumer/SDK migration execution started 2026-10-05; Plan 3 remains written and unexecuted.

Branch: `codex/theme-engine`, based on `192443b3`.

## 1. Intent and authority

Jasper needs one design system that produces consistent app and plugin UI. The theme supplies colors;
the system owns layout, typography roles, spacing, icons, control geometry, and interaction. Success
means the depicted surfaces match the supplied mocks in their non-color visual details, and every
other relevant surface uses the same components and rules. Runtime theme changes apply everywhere.

The user requested one reviewed specification followed by three separate implementation plans:
build the engine and kit, migrate styling consumers, then refactor surface composition.

The source document and nine images are preserved unchanged under
[`docs/design/theme-engine/reference`](../../design/theme-engine/reference/).
The document is design input, not an independent instruction source. The following authority order
records the user's explicit decisions and overrides its statement that the document always wins:

1. Decisions recorded in this specification after user approval.
2. Supplied mocks for depicted non-color visual details, normalized for image scale.
3. [Source design document](../../design/theme-engine/reference/design-system-source.md) for behavior,
   tokens, and surfaces or states not shown by the mocks, subject to the clarifications below.
4. Existing approved architectural specifications for unaffected behavior and boundaries.

The [terminal amendment](2026-09-20-jasper-terminal-refactor-design.md),
[app/Buddy amendment](2026-09-20-jasper-app-buddy-refactor-design.md), and
[SDK design](2026-09-21-jasper-plugin-sdk-design.md) continue to govern independence, ownership,
threading, and supported APIs. This spec amends theme ownership, plugin UI construction, and the
visual rules described here. It does not reverse unrelated feature decisions.

## 2. Agreed decisions

- A standalone Gradle module contains the engine and shared UI kit.
- Extract and evolve the existing theme engine rather than replace its supported capabilities.
- Themes supply colors. They cannot override design-system geometry or typography roles.
  Existing user font preferences and accessibility/display scaling remain supported.
- The mocks win for depicted visual detail; their sample colors are not required colors.
- Plugins use SDK contracts and host adapters, never the module or application internals directly.
- All plugins must migrate. There is no supported legacy raw-Swing plugin UI compatibility path
  in the completed overhaul. This is an intentional SDK compatibility break.
- Terminal and Buddy remain independent libraries; the app adapts theme values to their APIs.
- Plan 2 changes styling consumption; Plan 3 changes screen composition and finishes visual matching.
- The additional plugin-panel screenshot is a required composition reference: inline headings/counts,
  mixed-content rows with protocol chips and switches, persistent footers, and full-panel empty states
  with centered actions must be expressible through the kit and SDK.

## 3. Existing system and approach

`dev.jasper.app.appearance` currently owns theme documents, inheritance, semantic colors, generation,
FlatLaf installation, and live selection. `themeimport` handles import formats and conversion.
`ThemeController` publishes chrome and workspace values together on the EDT and attempts installation
rollback on failure. Generated values currently reference terminal `Palette` directly.

The SDK exposes `Appearance`, workspace/editor/syntax palettes, icons, and raw Swing surface factories.
`HostedUi` adapts these services. Plugin forms such as Vault's `PasswordPanel` and `EditorForm` build
and style controls themselves. These are migration sources, not templates for the new contract.

The selected approach extracts reusable capabilities, introduces complete semantic tokens and kit
components, then removes old paths as consumers migrate. A parallel permanent styling layer is not
allowed. Rebuilding theme import and selection from scratch is unnecessary scope and regression risk.

## 4. Module and dependency boundaries

The proposed module name is **`jasper-ui`**, with root package **`dev.jasper.ui`**. Its supported API
is for application integration; plugins consume the separate SDK API.

| Owner | Responsibility | Boundary |
|---|---|---|
| `jasper-ui` theme packages | Documents, inheritance, color resolution, complete snapshots, serialization/import conversion logic | JDK and declared implementation libraries; no app, SDK, terminal, Buddy, or plugin dependencies |
| `jasper-ui` metrics and icons | Typed design metrics, typography roles, named icon registry and tinting | Theme/model dependencies only where needed |
| `jasper-ui` Swing kit | FlatLaf integration, controls, layouts, content scaffolds, theme binding | Internal model/metrics/icons; no app services or plugin logic |
| App `appearance` and integration owners | Saved selection, theme discovery locations, reload policy, chrome/workspace routing, configuration and lifecycle | Adapt app values into module values |
| App `windows` and platform owners | Native windows, title bars, ownership, modality, sheets, placement, native effects | Compose module content; no native window creation inside portable kit controls |
| App `plugins` | SDK-to-kit adapters, ownership, callback containment and plugin cleanup | Only app package allowed to reference SDK types |
| `jasper-sdk` | Vendor-free descriptors, handles, semantic keys and plugin contracts | JDK only |
| `jasper-sdk-testkit` | Functional fake controls/services and contract tests | JDK and SDK only |

Theme model packages do not depend on Swing. Terminal colors become module-owned color values,
converted by the app to `dev.jasper.terminal.config.Palette`; the UI module does not import terminal
types. Buddy appearance is similarly adapted through its supported values. No library imports the app.

Package-level dependency graphs remain acyclic and are checked from bytecode. Add a module-specific
architecture gate and extend the existing app, SDK, plugin, packaging, and documentation checks.
Do not introduce service interfaces without two real implementations; SDK interfaces have host and
testkit implementations. Use concrete values and JDK callbacks for host integration where sufficient.

## 5. Theme model and design metrics

### 5.1 Color resolution

Use three layers: authored palette/overrides, resolved semantic values, and consuming kit components.
The canonical token vocabulary includes every color in source sections 3 and 8, including secondary
button states omitted from the source's section 3 table. Use typed keys in Java and stable semantic
names in theme data. Components cannot fall back to unrelated hardcoded colors.

Resolution is deterministic: merge inheritance using the existing precedence/cycle rules, translate
legacy names and supported imported color roles, apply explicit canonical semantic overrides, then
derive remaining values through a documented acyclic fallback graph. An explicit canonical override
wins over its legacy alias. Required base values must exist; invalid input produces an origin-aware
diagnostic. A successfully resolved theme always contains every required semantic color.

Preserve existing theme names, selection, inheritance, terminal ANSI colors, syntax roles, import
formats, and workspace transparency semantics. Existing theme files remain readable without being
silently rewritten. Import conversion preserves color intent, not foreign control dimensions,
fonts, UI delegate classes, or artwork that would defeat the kit. Unsupported appearance overrides
receive actionable diagnostics. Canonical serialization and legacy mapping are specified and tested
in Plan 1 before consumers migrate.

Chrome and workspace remain distinct resolved slots. Dialogs, tool windows, menus and app chrome use
the established chrome routing; terminal/editor content uses the workspace route. Relocation between
windows rebinds the route and opacity. A chrome-only or workspace-only change must not accidentally
overwrite the other selection. Native glass remains confined to its supported workspace content.

### 5.2 Fixed design values

Spacing, typography, sizing, radii, icon geometry, elevations, and motion are typed, named values
owned by the kit. They are not user-theme overrides. Feature code chooses semantic roles and variants,
not numeric styling values. Layout data such as a saved window position or a user-resized column is
not a forbidden styling literal.

Source sections 4–9 provide the baseline catalog: 28 logical-pixel default controls, 32 hero fields,
24 panel icon buttons, 96 form-label column, 12 column gap, 14 card padding, 10 row gap, 24 form side
padding, 6 control radius, 9 card radius, and 12 window radius. Depicted differences are calibrated
from the mocks into named canonical metrics, not local exceptions scattered through feature code.

Source ranges must become named deterministic defaults in Plan 1's metric table. Its spacing scale
does not prohibit dimensions explicitly specified elsewhere (for example 3px focus rings and 5px menu
padding). These are named component metrics, not additions feature authors choose arbitrarily.
Likewise the source's segment/knob shadows are sanctioned component effects despite its general
three-elevation statement. Each effect has one kit implementation.

System UI and monospace families are resolved centrally. User UI font family/size overrides continue
to apply consistently; terminal font settings retain their existing independent meaning. Font
enlargement and display scale remeasure controls and layouts, prevent clipping, and enable scrolling.
Default-scale reference matching does not force small controls at enlarged accessibility settings.

### 5.3 Alpha and live state

Alpha-derived treatments reference a semantic color plus a kit-owned alpha. Apply alpha exactly once:
the scrim base color and its 55% treatment must not compound to 30.25%. The same rule applies to category
tiles, focus rings and disabled content.

Immutable resolved snapshots may cache calculated colors. The source's prohibition on cached colors
means consumers cannot retain stale values across changes; it does not require recomputation on every
paint. Theme bindings update snapshots and repaint/revalidate as needed.

Build and validate a candidate before installation. Installation and publication occur on the EDT;
the active snapshot changes only after success. Failure restores the previous active installation and
selection and reports through the existing error path. Same-variant theme changes still notify.
Subscriber failure is contained so other live surfaces update. Closing a surface or plugin removes
its registrations and stops its timers; repeated teardown is harmless.

## 6. Kit components and containers

Implement source sections 8–9 as one canonical family, with theme-derived colors and fixed geometry:

- Primary, secondary, ghost, destructive-ghost, link, icon, and menu/split buttons.
- Text, password/reveal, search, port-prefix, path/browse and multiline fields; inline validation.
- Dropdowns, credential pickers, segmented choices and switches. Settings use switches; checkboxes
  are reserved for list/table multi-selection. Radios become segments or dropdowns.
- Category tiles, chips, status dots, counts, menus, popovers, and tooltips.
- Cards, section labels, form rows, settings rows, helper text, hero headers and strength meters.
- Single/two-line/compact/ordered lists, detail rows, column headers and empty states.
- Dialog and sheet content, tool-window scaffolds, window footers, activity-rail and status-bar pieces.

Tables, trees, file browsers, settings editors and specialized document widgets must use kit-owned
styling and composition too. Extend the kit for a genuinely missing standard control instead of
allowing a feature to paint its own version. Swing implementation controls are allowed inside the kit.

The source's one-primary-action rule applies to each independently actionable surface; a modal sheet
is active while its parent is dimmed and blocked. Panel headers have one optional plugin primary action,
More, and Collapse; host-owned panel placement/expansion actions may appear alongside them as in the
panel reference. They do not count as additional plugin primary actions or require a separate toolbar.
Context actions belong to menus; browser navigation retains its documented exception. Empty collection
states hide irrelevant search and column-header UI, while retaining the panel title and applicable
header actions. An empty filtered result must retain search so the user can clear the query.

Dialogs autofocus the first applicable input, support Enter/Esc with multiline and popup handling,
disable primary actions while invalid or busy, and restore focus to their owner. Async completion
after closure must not reopen or mutate disposed UI. Error display cannot overwrite an unrelated
newer request. Accessibility includes semantic names, label association, focus order, keyboard
operation, and tooltips for icon-only controls.

Sheets attach below their parent's title bar, use only bottom corner rounding, dim the owner content,
and follow owner resize, movement and closure. The app supplies ownership/modality; a mock does not
authorize drawing nonfunctional window controls or replacing platform behavior with decoration.

Animations obey the existing `ui.animations.enabled` policy. Turning it off settles active effects
immediately. Theme changes preserve current input, selection, focus, scroll position and domain state.
Optional error shake is not required for this overhaul; focus and error presentation are required.

### 6.1 Required plugin-panel composition

The [plugin-panel screenshot](../../design/theme-engine/reference/plugin-panels.png), supplied during
spec review, is an explicit acceptance reference for the four panel areas. These compositions are
first-class kit/SDK capabilities, not exceptions that require custom plugin painting:

| Panel | Required composition |
|---|---|
| Sessions | Inline title and count; header actions on that same row; search beneath; compact rows combining status dot, name and secondary host text; selected-row treatment; bottom detail area with identity, technical endpoint, credential state, edit action and Connect |
| Tunnels, empty | Persistent title/header actions; empty state filling the available panel body with a centered icon tile, title, wrapped explanation and New Tunnel action |
| SFTP, disconnected | Centered icon/title/explanation and a Choose Session dropdown action; persistent bottom settings row with inline label and trailing Follow Terminal Folder switch |
| File Servers | Inline panel title/count; row with leading icon, name on the first line, protocol chip plus monospace endpoint on the second, and a compact trailing run/stop switch |

The scaffold exposes composable slots for a header, optional search/navigation, growing body, and
optional bottom-anchored footer. The body supports lists, browsers, and full-body empty/disconnected
states. Footers support settings rows and richer selected-item details. Empty states can offer ordinary
actions or selection/menu actions; they are not limited to a plain button. Their content centers in
the remaining body after header/footer allocation and remains usable when the panel is narrow or short.

Row descriptors support leading status/icon/tile, inline title and secondary text, an optional second
line with type chip and technical value, and a trailing control/action slot. Category chips identify
HTTP/FTP/SFTP/RSYNC with theme-provided hues. Text truncates before overlapping the trailing control;
technical endpoints retain monospace presentation. Counts and secondary information can sit inline
with their associated title without forcing an extra label row.

Interactive row switches are explicitly supported in addition to settings-row switches. This amends
the source document's restriction that toggles always appear in a Settings row. Row actions must be
individually focusable and operable with keyboard and pointer input; a switch cannot be merely painted
into a list renderer. Activating it invokes its bound operation once without also opening/activating
the row. Pending/error state and the displayed value follow the domain result, not an assumed success.

Panel resizing, collapse/restore, item arrival/removal, selection changes and live theme switching
must preserve these arrangements. Persistent controls remain visible in empty/disconnected states when
applicable, as shown by SFTP. Titles, chips, switch state and empty-state actions are supplied by plugin
descriptors; padding, alignment, colors, hit targets and rendering remain kit-owned.

The screenshot establishes panel composition, not a new requirement to redesign the central terminal,
folder tabs or the entire application's outer chrome. Their existing approved behavior remains in scope
for styling migration as already specified.

## 7. Plugin contract and coordinated cutover

Plugins describe their UI through SDK values and handles. The API exposes theme roles, named icons,
canonical component factories, tool-window descriptors, dialog/sheet descriptors, validation and
conditional state, list models/row descriptors, actions, and supported notification services.
No SDK signature exposes FlatLaf or a `dev.jasper.ui` type. The app adapter translates SDK values into
module values; neither the kit nor other app packages consume SDK descriptors.

A form descriptor contains title/size tier, optional hero, ordered sections and typed rows, visible
and enabled conditions, validation, primary action/label, and optional footer preview. State updates
use stable field identity so conditional changes and theme refreshes preserve unaffected input.
List models preserve selection identity and virtualized rendering for large collections.
Panel and row descriptors must express every slot and interactive combination in section 6.1,
including a dropdown empty-state action and a switch within a two-line item row.

The plugin owns domain validation and operations. The host owns focus, layout, enabled/visible
presentation, keyboard behavior, error placement, busy presentation and callback containment.
Callbacks execute under the established plugin boundary; worker completions marshal UI updates to
the EDT. Conditions are bounded UI evaluations, not blocking I/O.

Secret fields must support mutable secret documents and explicit erasure. Descriptors, snapshots,
logs, previews and general string bindings must not copy passwords into immutable strings. Paired
password reveal state is shared as described in the source; closing, locking, cancellation and
successful submission retain the existing secret-cleanup guarantees. A strength indicator does not
silently change the Vault's password acceptance policy.

Specialized content is an explicit SDK capability for terminal-like canvases, editor text areas,
charts and previews, hosted inside kit containers and bound to semantic tokens. It is not a general
raw-Swing form factory. Editor behavior, syntax highlighting, terminal rendering, and other domain
features remain supported through this route.

The completed API removes/replaces legacy raw component factories and styling escape hatches for
ordinary controls. Increase the SDK compatibility boundary and update bundled plugin manifests,
testkit, samples and authoring documentation together. Reject incompatible plugins before activation
with a message identifying the required migration. A plugin with a dishonest compatibility declaration
still receives contained linkage/contract failure, not an application crash.

Development diagnostics use explicit kit ownership/provenance, not a naive ban on Swing child
classes: FlatLaf and kit internals necessarily contain Swing controls. Source/bytecode architecture
checks enforce bundled-plugin restrictions. Runtime tree diagnostics report visible non-kit content
and unauthorized styling at contract boundaries while recognizing approved custom-content roots.
These checks are API discipline, not a security sandbox.

Plan 1 may keep the old app contract temporarily to leave the existing runnable app intact while the
new kit is built. Plan 2 performs the coordinated consumer/API cutover; its finished deliverable has
no supported legacy plugin route. Temporary migration bridges have named removal tasks and never
become the promised third-party compatibility path rejected by the user.

## 8. Surface coverage and visual authority

| Reference | Depicted requirements |
|---|---|
| [Add Host](../../design/theme-engine/reference/add-host.png) | Grouped Connection/Authentication/Options cards, host+port row, segmented authentication, ordered key list, settings rows, preview footer |
| [Unlock Vault](../../design/theme-engine/reference/unlock-vault.png) | Centered hero, password adornment and focus ring, equal-width action row |
| [New File Server](../../design/theme-engine/reference/new-file-server.png) | Protocol segments, path/port combinations, conditional access fields, dependent settings, preview footer |
| [Create Vault](../../design/theme-engine/reference/create-vault.png) | Hero, paired password fields, meter, device-binding setting, warning and equal buttons |
| [Credential Vault](../../design/theme-engine/reference/credential-vault.png) | Category sidebar, search/list column, detail column, type tiles, selected rows, detail card and footer |
| [Vault add menu](../../design/theme-engine/reference/vault-add-menu.png) | Anchored menu, category tiles, separator and generate action |
| [Add Login sheet](../../design/theme-engine/reference/add-login-sheet.png) | Owner scrim, attached sheet, category header, grouped fields and authentication segments |
| [Add SSH Key sheet](../../design/theme-engine/reference/add-ssh-key-sheet.png) | Attached sheet, path/browse rows, optional hint and footer actions |
| [Plugin panels](../../design/theme-engine/reference/plugin-panels.png) | Sessions inline rows and detail footer; full-body Tunnels/SFTP empty states; persistent SFTP setting; File Servers protocol chip, endpoint and inline switch |

The ninth screenshot supplies the panel composition reference previously missing from this draft.
Its depicted panel areas follow the same non-color visual authority as the original eight mocks.
Unpictured panel states and other surfaces follow the source anatomy and shared visual language,
with reviewable implementation renders.

Scope includes app settings, command palette, file chooser, tool-window chrome, menus, dialogs,
auxiliary managers, notifications/error UI, and visible UI in every bundled plugin (Vault, Remote
including Sessions/SFTP/Tunnels/transfers, File Servers, Editor, History and Snippets). Plan 2 begins
with a checked surface inventory and assigns every owner a migration and verification task; hidden,
empty, error, busy and conditional states count as part of a surface.

Preserve recent folder-tab sizing/motion, file-chooser behavior, docking, residency and notification
behavior. Migrate their styling sources and reusable controls. Where a new visual rule conflicts with
an existing specialized composition not depicted here, retain its behavior and present the visual
change in Plan 3's reviewable surface design rather than silently discarding the earlier decision.

Mock text and example accounts are fixture data. They do not alter real stored credentials, paths,
host defaults or server security settings. Conditional presentation follows actual model capability.
The source's dependent-toggle reset is implemented only where the domain model permits that transition;
the UI must not silently remove an existing security restriction to imitate a disabled screenshot.

## 9. Three runnable deliverables

### Plan 1 — Engine and UI kit

Build `jasper-ui`, extract reusable resolution capabilities, define canonical theme serialization and
legacy/import color mapping, freeze the metric/component catalogs, implement the complete kit and
host-independent scaffolds, add SDK contracts and testkit implementations, and build a headless
component gallery. Include explicit states, light/dark/custom theme fixtures, token completeness,
lifecycle, accessibility, scale, and failure tests. Add module/package architecture gates, dependency
and distribution wiring, onboarding and API examples.
The gallery must include the four panel compositions in section 6.1 using fixture data and working
local control bindings, proving the API can express them before any feature migration starts.

Exit: the existing app still runs, the independent kit is exercised without launching a native GUI,
and its supported API can express all inventoried surface patterns. No unfinished primitive is
implicitly deferred into a feature's custom painting. Broad consumer migration belongs to Plan 2.

### Plan 2 — Consumer and contract migration

Move app and bundled-plugin styling to the new engine and kit-owned styling entry points; preserve
existing screen arrangement where possible. Complete the SDK cutover, update compatibility metadata,
and remove superseded theme/geometry/icon styling paths. Convert specialized content palette adapters,
chrome/workspace routing, settings, theme imports and window lifecycle bindings. Update documentation
and architecture checks to the new ownership model.

Temporary app-native adapters may preserve existing layouts; they must use the same engine and have
explicit removal/replacement tasks in Plan 3. Plugin forms use the new contract, with host-owned
migration layouts when required. These are not plugin-defined pixel styling or legacy public APIs.

Exit: all relevant surfaces refresh from the new engine, every bundled plugin uses the new SDK,
incompatible third-party plugins fail clearly, the old theme engine is removed, and behavior tests
pass. Surface composition need not yet match the mocks, but no competing token authority remains.

### Plan 3 — Surface composition and visual matching

Refactor surfaces onto the canonical cards, rows, lists, headers, dialogs and sheets. Work from
Unlock/Create Vault to Add Host, New File Server, the Vault manager and sheets, then panels and the
remaining inventory. Preserve operation behavior and state while replacing ad hoc composition.
Remove temporary migration layouts. Match each supplied reference in normalized geometry, typography,
artwork and state, with active-theme colors. Publish before/after and state renders for all inventory
groups and a native acceptance checklist.

Exit: all inventoried UI follows the kit; all nine visual references, including their depicted panel
areas, pass visual acceptance; unseen surfaces have reviewed renders; the app has no remaining
per-feature styling implementation outside
documented specialized content. No plan is declared complete while its required work remains deferred.

## 10. Verification and acceptance

Use Java 25 JBR and `./gradlew`. Tests are headless; do not launch the app, benchmark or AOT training.
Each implementation plan uses TDD for substantive behavior changes and per-task commits, with review
and final whole-branch verification following the repository workflow.

- **Resolution:** all shipped and imported theme fixtures, legacy aliases, explicit precedence,
  missing/cyclic input, full token coverage, alpha-once, color-only overrides, syntax and ANSI preservation.
- **Live application:** light↔dark, same-variant changes, custom/imported themes, different chrome/workspace
  selections, failed installation, multiple windows, reparenting and teardown; preserve input/focus/state.
- **Kit behavior:** keyboard/mouse/accessibility, validation, busy state, conditional fields, list identity,
  empty versus filtered-empty state, secret cleanup, callback containment, disposal and animations disabled.
- **Architecture:** acyclic module/package graphs, SDK JDK-only, no SDK leakage outside app `plugins`,
  no plugin imports of app/UI module internals, independent terminal/Buddy, permitted custom-content roots.
- **Visual:** deterministic headless gallery and complete-surface renders at 1× and 2×; default system
  typography plus enlarged-font checks; controls in normal/hover/focus/pressed/selected/disabled/error states.
- **Panel composition:** the Sessions detail footer, Tunnels full-body empty action, SFTP disconnected
  chooser plus persistent switch, and File Servers chip/endpoint/switch row. Exercise narrow/short
  bounds, empty-to-populated transitions, keyboard row controls, pending/failed toggle operations,
  collapse/restore and runtime theme changes; compare the depicted states to the ninth screenshot.

For screenshot acceptance, preserve original source pixels and record each reference's crop, measured
scale and layout anchors in Plan 1's calibration artifact. The initial eight images appear approximately
2×, but scale must be established per reference; do not stretch an entire screenshot to hide layout
differences. Measure the original local images, not resized chat previews.
Compare normalized bounds, baselines, insets, radii, icon paths and alignments against the original.
Compare geometry/artwork separately from active-theme color values, with side-by-side and overlay/diff
artifacts for review. Do not use a broad whole-image similarity score to waive visible differences.

Pixel-perfect refers to the depicted non-color design at the reference environment and scale. Platform
font rasterization and native window shadows/title-bar controls are separately identified in comparisons;
they are not a license to change app-owned geometry. Cross-platform verification checks the same logical
layout, native behavior and absence of clipping. Golden renders cannot be auto-updated merely to make a
failed visual check pass; each changed reference requires review against the supplied mock or approved
unpictured-surface design.

Run focused checks while implementing, then `./gradlew check` for each finished deliverable. Read exact
counts from XML. Validate docs/examples, source character hygiene and whitespace. Packaging verification
uses only headless tasks. Native font feel, window/sheet attachment, platform controls, physical keyboard
and screen-reader interaction are recorded as user-run acceptance, distinct from headless proof.

## 11. Review and next step

This specification is approved. Write three separate detailed plans with complete
steps, meaningful tests, commit boundaries, review tasks and runnable exit criteria. Plan deviations
must be recorded in each plan's status banner and `docs/STATUS.md`.

Written plans: [engine and kit](../plans/2026-10-04-theme-engine-1-engine-and-kit.md),
[consumer migration](../plans/2026-10-04-theme-engine-2-consumer-migration.md), and
[surface refactoring](../plans/2026-10-04-theme-engine-3-surface-refactor.md).
Their [shared contract appendix](../plans/2026-10-04-theme-engine-contracts.md) records exact
cross-task APIs, defaults and compatibility decisions. Plan review precedes product implementation.


## Authorized reference completion amendment — 2026-10-10

The user authorized continuous implementation and controller-owned native capture against all16 Desktop images, preserved with hashes in `docs/design/theme-engine/reference/desktop-2026-10-10/manifest.json`. Overlapping Desktop states are primary; original-only states remain binding. Native QA uses an isolated synthetic home. Source scale, crops, geometry, text ink/baselines, icons, states, and profile-normalized colors require separate evidence; arbitrary resize/global similarity cannot establish acceptance.

Task1's measured shared amendments are HERO_COMPACT (380 logical preferred width, same hero controls), CONFIRMATION secrets (single-line submit/wipe/reveal-group behavior without a local reveal button), and optional SettingRow titleNote in a tertiary tone. Constructors for prior SettingRow call shapes remain supported. Hero glyphs use a 1.75 logical-pixel optical stroke at 24px, retaining the 16-grid/1.4–1.6 registration contract for ordinary icons. On macOS, default regular roles use the installed native Apple text face; Helvetica Neue 600 roles resolve its installed Medium face with measured .018 tracking. Explicit user-family overrides bypass the default regular-face mapping. DESCRIPTION remains 12.5pt with shared 18px line spacing, CAPTION remains 11.5pt, and SettingRow descriptions use the shared 11pt SETTING_DESCRIPTION role. User font family/size overrides remain authoritative. See calibration.md for actual mounted measurements and native status.
