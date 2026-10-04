# Folder tabs

The user approved this visual follow-up on 2026-10-04 after reviewing an interactive mock over the application screenshot. The supplied reference below is the visual authority for the silhouette. This supersedes the inset rounded selection with a blue outline; the earlier Inset rail proposal remains a design alternative, not an application setting.

![Approved folder-tab reference](folder-tabs-reference.png)

The selected tab rises out of the workspace surface, with rounded top corners and broad concave bottom shoulders. Its bottom edge joins the terminal surface without an outline or underline. Following the user’s native screenshot correction on 2026-10-04, the tab fills the entire rail height with no top inset, and its upper corners share the workspace frame’s 8-pixel radius (scaled with the UI); the bottom shoulders retain their broader curve. Inactive tabs are quiet labels. The active tab shows its session or plugin icon and close button; hovering an inactive tab reveals its close control. Tabs stay compact and content-sized. A new-tab button sits at the right of the strip, beside access to the existing tab list.

Use the actual workspace background for the selected surface, including the effective glass opacity. The recessed area around the folder remains solid; its color and readable text/icon colors derive from the active workspace appearance. Chrome and workspace themes remain independent. Preserve configurable tab height, wheel overflow, keyboard selection, middle-click close, context menus, drag reordering, plugin tabs, and literal bounded titles/tooltips.

This is a bounded change to the current Jasper workspace tab strip, based on `main` at `5979c030`. The initially attached worktree was still at the historical Moray readiness revision; the clean worktree was moved to the current application before implementation. The earlier tab-motion plans describe superseded code; this follow-up preserves current interactions and introduces no animation subsystem or configuration setting.

## Implementation preview

Headless renders from the actual Swing tab strip at 2× device scale:

![Dark workspace](folder-tabs-dark-middle-strip.png)

![Light workspace](folder-tabs-light-middle-strip.png)

## Native acceptance

Headless renders and tests cover the implementation. The user should confirm the shape on a physical display, including the first and a middle selected tab, dark/light workspace themes, translucent workspaces, short and long titles, overflow, plugin tabs, and the new-tab button. No unattended GUI or benchmark is part of this change.
