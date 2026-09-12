# Moray Plan 3.5 — macOS Chrome, Themes and Toolbar

**Status:** Roadmap amendment requested by the user on 2026-09-11. Implemented ahead of Plan 4 configuration and packaging; final review, native acceptance and integration are pending. This roadmap is complemented by the concrete design and execution plan linked below.
**Authority:** User feedback after trying Plan 3: the app looks good overall; next build a custom macOS title bar, fix theming using FlatLaf, aim for Atom-style dark/light themes, and discuss a toolbar with actual colored icons.
**Parent:** [Phase 1 design](../specs/2026-09-10-moray-phase-1-terminal-design.md), especially §§5–7. This amendment supersedes the earlier sequencing that deferred all theme work to Plan 4 and the assumption that the current toolbar presentation is final.

**Execution update:** The user approved work and selected toolbar study B (fuller, two-tone colored icons). The [concrete design](../specs/2026-09-11-moray-plan-3-5-titlebar-themes-design.md) and [four-task implementation plan](2026-09-11-moray-plan-3-5-titlebar-themes-implementation.md) now cover title bar, coordinated themes and that toolbar treatment together. Initial title layout uses a slim custom surface with separate tabs; an optional integrated-tab preference remains open to user steering.

## Goal

Give the working terminal a coherent macOS window and Atom/One Dark- and One Light-inspired appearance, then refine the toolbar with the user. Preserve the terminal functionality and ownership contracts delivered in Plan 3.

## Baseline before Plan 3.5

- FlatLaf and flatlaf-extras 3.7 are already application dependencies. Main installs FlatDarkLaf; the Appearance menu switches between stock FlatLightLaf and FlatDarkLaf.
- Appearance switching currently updates Swing chrome, while terminal views retain their creation-time palette. Coordinated light/dark theming therefore requires terminal palette updates as well as FlatLaf styling.
- TerminalWindow uses a standard JFrame; no custom macOS title-bar integration exists.
- The toolbar has seven actions with 20-pixel Tabler outline SVGs and labels underneath. The assets are adapted to a theme-aware gray. This is the baseline to reconsider, not the approved final visual design.

## Requested scope

### 1. Custom macOS title bar

Design and build a custom title-bar surface that belongs visually to Moray and its theme. Prefer retaining native macOS traffic-light controls and window behavior while customizing the surrounding surface; confirm the supported JBR 25/FlatLaf integration before choosing APIs. Do not assume FlatLaf alone supplies macOS custom decorations.

The design must account for dragging, resize edges, traffic lights, minimize/close/full screen, double-click behavior, active/inactive appearance, accessible controls, the screen menu bar, multiple windows and display scaling. Preserve safe-area spacing around native controls. Decide how title, tabs and toolbar relate after inspecting title-bar options; their exact arrangement is not selected by this amendment. Keep Linux/Windows window behavior working and isolate Mac-specific integration in the app module.

### 2. Coordinated Atom-inspired dark and light themes using FlatLaf

Use Atom/One Dark and One Light as the visual direction, not an exact-clone requirement or a final approved palette. Retain the built-in identifiers `moray-dark` and `moray-light`; settle exact colors through visual review.

Define coordinated colors for title bar, tabs, toolbar, menus, popups, find controls, dividers, status, focus/selection, hover and disabled states, plus terminal background/foreground, cursor, selection, search highlights, dimming and the 16 ANSI colors. Use FlatLaf for Swing look and feel and pass terminal colors through the terminal module's own API. Preserve explicitly supplied terminal truecolor values.

Switching built-in themes must update existing windows, hidden tabs and zoomed-away panes as well as newly created ones, without restarting shells, losing scrollback or resetting font choices. Theme selection must stay consistent across windows. Test readable normal, focused, inactive and disabled states in both variants.

Pull built-in theme definitions and live theme application forward from Plan 4. Keep TOML files, custom-theme loading, persisted choices and automatic system appearance in Plan 4. Plan 3.5 offers manual built-in selection for immediate use.

### 3. Toolbar discussion, then colored-icon implementation

The user wants actual colored icons rather than an all-gray toolbar. Review visual alternatives together before selecting artwork and layout. Compare restrained accent-colored icons with richer multicolor/filled artwork, in both themes. Discuss icon size, labels, spacing, grouping, button backgrounds, separators and how the toolbar relates to the title bar and tabs.

The local `~/projects/tabler-icons` repository remains an available source, not a requirement to keep gray outline artwork. Evaluate whether colored/filled Tabler assets meet the desired appearance; use other permissively licensed or original artwork if the selected direction calls for it. Preserve source/license attribution for bundled assets. Avoid merely applying one uniform tint and calling the design complete.

Keep the existing shared action routing, tooltips, accessible names, keyboard shortcuts and toolbar visibility modes. Settings/Reload stay visibly disabled until configuration exists. Color must supplement recognizable shapes, labels and interaction states.

## Work sequence and design decisions

1. Inspect supported macOS title-bar integration in the pinned runtime/LAF and document trade-offs. Compare native-controls-with-custom-surface against a fully custom undecorated window; prefer the former if it meets the design. Record any additional native dependency before adopting it.
2. Develop and review title-bar composition and paired dark/light theme samples. Write the concrete design spec, including terminal palette-update ownership and lifecycle behavior.
3. Turn the approved design into a test-driven implementation plan for the title bar and coordinated built-in themes, with per-task reviews and a final review.
4. Discuss toolbar alternatives in the context of the new title bar and both themes. Record the chosen icon style and layout before writing its implementation tasks.
5. Implement and review the selected toolbar; perform the user-run native acceptance checks below. Continue to Plan 4 after this visual milestone.

The concrete design records the chosen native integration, initial palette and title geometry. The user approved execution and selected toolbar B; all four tasks are now implemented. See STATUS for review and native acceptance progress.

## Acceptance

- [ ] Native macOS title-bar behavior works with multiple windows, full screen, activation changes and display scaling; input controls do not accidentally drag the window.
- [x] Dark and light themes cover both FlatLaf chrome and the terminal, with coherent title bars and readable ANSI/search/selection colors.
- [x] Live theme changes preserve sessions, content, split ratios, zoom and per-pane fonts, including hidden panes and newly opened windows.
- [x] The user has reviewed and selected toolbar layout and colored artwork in both themes.
- [ ] Toolbar actions, shortcuts, accessibility, hover/disabled states and visibility modes still work.
- [ ] Appropriate headless regression tests, full Gradle checks, task reviews and final review pass.
- [ ] The user checks native behavior and visual appearance; headless results are not presented as native validation.

Follow AGENTS.md: no unattended GUI or benchmark launches. The existing performance threshold, three-platform CI, terminal-hardening backlog and Phase 1 daily-use gate remain in force.

## Plan 4 boundary

Plan 4 remains configuration and packaging: per-OS paths, TOML diagnostics and last-good retention, live reload, custom theme files, persistence, automatic system appearance, settings-file creation, --config, logging and a macOS .app with JBR. It should consume Plan 3.5's built-in themes and application path rather than introduce another independent theme mechanism. SSH, SFTP, tunnels, vault and plugins remain later phases.
