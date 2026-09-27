# Jasper unified header and graphite chrome

Approved in the 2026-09-27 conversation; implemented in the existing
`codex/intellij-new-icons` worktree, preserving its vendored icon catalog.
This bounded layout update follows the native inline workflow with an independent
final review. No SDK changes or terminal-engine changes are required.

## Layout and context

The macOS workspace has two top rows: a unified native title/toolbar row and the
session tabs below. Native traffic lights remain native. A small leading label after
the controls follows the focused pane's directory, or a provider's saved host/session
label and reported remote path. The full context is available in a tooltip. OSC title
changes and manual tab names still update native window metadata and tabs, but never
replace working context. Remote reports remain display-only, never local paths.

The default header is 42 logical pixels, growing with toolbar-height configuration.
The toolbar remains a concrete workspace owner; the platform-only MacTitleBar accepts
Swing components and context values. Dialogs retain their existing 28-pixel title row.
Unsupported platforms retain their native title bars and separate toolbars.

## Actions and overflow

Keep labels for Split and contributed dropdowns such as Sessions. Familiar built-in
commands use icons. Settings lives in the header; remove the duplicate rail button.
Reserve blank header space for native dragging, including context-label dragging.

Move whole controls into an ellipsis menu at narrow widths. Never wrap or compress
button targets. Stable priority from lowest to highest: New window/Zoom, contributed
single actions, contributed dropdowns, New tab/Split, Find/Settings. Within a priority,
later controls overflow first. Overflow preserves original action enablement, selected
state, accelerators and submenu grouping; widening restores controls. Removed plugin
contributions do not leave an overflow registry behind.

## Surfaces

Jasper Dark uses terminal #191c20, panels #21252a, header/rail #25292e, silver foreground
#d5dee5 and ice-blue accent #8eb8d2. Thin rules and a faint header upper edge distinguish
regions. The selected tab follows the resolved terminal palette, including independent
terminal-color choices, with matching text and host icon variants for contrast.
Existing ANSI colors and the explicit legacy Dark terminal palette are preserved.
Other themes retain their supplied colors. Rail selections use softly rounded fills.

The optional angled tab shoulder is deferred: the first version uses surface continuity
and a restrained two-pixel indicator. Upstream icon artwork remains unchanged.

## Validation and native acceptance

Headless regressions cover context provenance, focus changes, literal labels, overflow
resizing, live actions/submenus, toolbar height, theme overrides, surface pixels and
native title metadata. `check` runs architecture guards, docs, assets and tests.
Headless previews use the actual Swing components without creating desktop windows.
Native traffic-light placement, window dragging/double-click, fullscreen and actual
notification presentation remain user-run checks; agents do not launch the GUI.
