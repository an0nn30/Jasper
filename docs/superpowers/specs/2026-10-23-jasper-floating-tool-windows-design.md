# Floating tool windows — design

**Date:** 2026-10-23. **Status:** written without a question round at the user's request ("I don't want questions — it
should behave like IntelliJ"); every choice below that the user did not state is marked *Ruling* with its reason, so it
can be reviewed afterwards. Amends the dock design (`2026-10-01-jasper-dock-layout-design.md`, which left "float/window
modes" out of scope and named the header's Options button ⋮).

## Intent

The user's words: "I want to be able to drag toolbar tabs (like the sessions tab strip or the SFTP tab) or in normal mode
the icons — into their own windows with an option to put them back in the tab strip. Something like what IntelliJ
offers." And: "I click and hold a toolbar's tab or icon and drag and it creates a window for that particular tool. It
gains a toolbar with the vertical 3 dots which offers a menu to move it back to its position on the rail." Plus "an SDK
update that lets plugins lay themselves out alternatively if they determine they've been pulled into a window".

"Toolbar tabs" are the labelled stripe buttons of the Enterprise look (Sessions, SFTP…); "the icons" are the default
look's rail buttons. Both are tool window stripe buttons in the main window's dock.

## IntelliJ's behaviour (the reference)

From IntelliJ's documentation ("Tool window view modes") and its platform source (`ToolWindowDragHelper`,
`FloatingDecorator`):

- A tool window has a view mode: Dock Pinned, Dock Unpinned, Undock, **Float** (a window of its own that stays above the
  IDE window and is shown only with it) and **Window** (an ordinary separate window). The header's Options button (the
  vertical ellipsis ⋮) has **View Mode ▸**, which switches between them, and **Move To ▸**.
- Releasing a dragged stripe button or tool window header anywhere that is not a stripe or dock area makes the tool
  window **Float**. Its window opens under the pointer, offset by where the drag grabbed it, sized from the bounds it last
  floated at or else from its docked size, and kept on screen.
- The floating window is an owned dialog titled with the tool window's name, holding the same tool window (header, ⋮,
  hide). Closing it with the OS close button **hides** the tool window; it stays Float, its stripe button stays on the
  stripe, and showing it again reopens the window where it was. Its bounds are remembered per tool window.
- Plugins read `ToolWindow.getType()` / `getAnchor()` and hear `ToolWindowManagerListener.stateChanged`.

Jasper copies this, limited to the modes it has: its docked tool windows are IntelliJ's Dock Pinned; it gains Float and
Window. Dock Unpinned and Undock (sliding) are not added.

## Goals

1. Drag a stripe button (rail icon, labelled stripe tab, bottom strip tab) or a docked tool window's header out of the
   main window and release it: the tool window opens in its own **Float** window at the release point.
2. The tool window keeps its header in its own window; the header's ⋮ Options menu offers **View Mode ▸ Docked / Float /
   Window**, **Move to ▸** the six places, and **Hide**. View Mode ▸ Docked puts it back at its stripe position and shows
   it there.
3. Its stripe button stays on the stripe while it is in its own window; clicking it shows, raises or hides that window.
4. Mode, stripe position, visibility and the window's bounds survive restarts, per look, like the rest of the dock.
5. SDK: a panel can read where it is (`PanelHost.placement()`: anchor, half, mode) and hear changes
   (`PanelHost.onPlacement`), so it can describe another layout when it has a window of its own. Moving between docked
   places notifies too (today a move tells the plugin nothing).
6. Works on macOS, Windows and Linux (x64 and aarch64), in the default and Enterprise looks.

## Non-goals

- Dock Unpinned / Undock (sliding) modes; several tool windows sharing one window; tool windows in child windows (child
  windows have no dock, unchanged).
- Dragging a detached window's header back into the dock (*Ruling:* its OS title bar moves the window; docking back is
  View Mode ▸ Docked, Move to ▸, or dragging its stripe button — IntelliJ's float window is also moved by its title bar).
- A thumbnail drag image (*Ruling:* the drag shows Jasper's existing tear-out label, as a tab torn out does; IntelliJ's
  220 px thumbnail is a nicety not worth a second drag-image mechanism).
- Palette actions or shortcuts for view modes; alpha (fade) for inactive floating windows (Jasper has no animations).
- Bundled panels adopting the new placement API with alternate layouts (Sessions, SFTP, Tunnels, File Servers): a
  follow-up per plugin. The Sample plugin and the plugin-authoring example demonstrate it. (Done on 2026-10-26: the
  [wide tool window layouts design](2026-10-26-jasper-wide-tool-window-layouts-design.md) and its
  [plan](../plans/2026-10-26-wide-tool-window-layouts.md).)

## Design

### Terms

A tool window is **docked** (in one of the main window's six slots) or **detached**: in its own window, in mode
**Float** or **Window**. A detached tool window still has a slot: the place its stripe button sits and where it docks
back.

### Model (`dev.jasper.app.dock`, JDK-only)

- New `enum ToolWindowMode { DOCKED, FLOAT, WINDOW }` (`ViewMode` is taken by `dev.jasper.app.config`).
- `DockState` keeps every id in exactly one slot's order, as now. It adds, per id, a mode (absent = `DOCKED`) and the
  bounds of its own window (`java.awt.Rectangle`, screen coordinates; kept after docking back, as IntelliJ keeps
  `floatingBounds`, so the next detach reopens there).
- Visibility: a docked id is visible when it is its slot's active id (now); a detached id is visible when its window is
  shown. A detached id is never a slot's active id, so detaching a shown docked window empties its slot's frame.
- Mutations (each effective one fires one `Change`, a no-op fires nothing):
  - `setMode(id, mode)`: docked → detached keeps visibility (a shown tool window stays shown, now in its window);
    detached → docked shows it in its slot if it was shown (hiding the slot's active one).
  - `detach(id, mode, bounds)`: sets mode and bounds and shows it (the drag's drop).
  - `show`/`hide`/`toggle` work on detached ids without touching slots' active ids.
  - `move(id, slot, index)` moves the stripe button and keeps mode and visibility (a stripe drop). `moveAndShow` (a
    workspace drop, and Move to ▸ on a detached tool window — see the menu) docks a detached id.
  - `setBounds(id, rect)` records a moved or resized window and fires nothing (persisted with the next save).
- `Change` gains `placed`: the ids whose slot or mode changed. It drives re-mounting and the SDK's placement
  notifications.

### Persistence (`ui-state.toml`)

`[panels."<id>"]` (and its `[look."<key>".panels."<id>"]` form) gains the mode (omitted when docked) and the window's
bounds. Absent panels (plugin not loaded) keep theirs, as their slot and order are kept now. The save points are the
existing ones (every dock change, window close) plus a detached window's hide and close. On restore, a detached tool
window that was visible reopens in its window after the main window shows, its bounds kept on today's displays with
`WindowPlacement.restore` (the same rule workspace windows use).

### The detached window

- One window per detached tool window, owned by the main window's dock (`WindowContributions`).
  - **Float**: a modeless `JDialog` owned by the main window's frame — above it, minimised and closed with it, no
    taskbar entry of its own.
  - **Window**: an ownerless `JFrame` — its own taskbar/Dock-switcher entry, can go behind the main window.
- Content: the same framed tool window as docked — the header (native `ToolWindowHeader`, or the panel's borrowed kit
  header with the host's Options and Hide actions) above the panel's component, painted with the tool window surface.
  The component moves between the dock frame and the window; it is never rebuilt. *Ruling:* the window is opaque (no
  glass).
- Title: the tool window's title. Chrome follows the platform rules auxiliary windows use (`NativeShells`): on macOS the
  `MacTitleBar` (Float uses the dialog style), on Windows and Linux FlatLaf's title pane in the theme's colours, the
  application icon, and theme changes applied to the window and its decorations.
- Closing it with the OS close button, the keys of Close Pane, Close Tab or Close Window (the platform's keymap; on
  macOS Close Pane is ⌘W) or the header's Hide hides the tool window: it stays detached and keeps its stripe button.
  Those keys never close a pane, tab or window of the main window from a detached window.
- Size: the bounds it last had; otherwise the size of its docked frame (or of the frame it would get, from
  `DockGeometry.landing`), at least the tool window minimum. Placement for View Mode ▸ Float/Window: where its docked
  frame is on screen, or centred on the main window when hidden; fitted to that display.
- Keyboard: Jasper's shortcuts work while a detached window has focus, including the panel's own
  (`ShortcutContext.panel`). Other window-level actions (new tab, palette, …) act on the main window and bring it
  forward. On macOS with the screen menu bar, a Window-mode window carries the minimal menu
  bar auxiliary frames carry so the screen menu bar does not empty; a Float dialog keeps its owner's.
- Focus: showing it through an activating request (stripe click, View menu, `PanelHost.show` from the panel's toggle),
  View Mode ▸ Float/Window chosen from a menu, and a drag-out drop raise its window and focus its content, as docked
  activation does. Any other show (a restore at start-up, Zen's return, a plugin's plain `show`) opens the window
  without taking the focus (*Ruling, Task 8:* IntelliJ shows a tool window active only when it is activated). Under
  flush frames (Enterprise) its header shows focus like a docked strip's.
- Lifetime: it closes with the main window (and its tool window's removal: plugin stopped or removed); mode and
  visibility are saved, so it returns with the main window.
- Zen hides detached tool windows with the docked ones and restores them after (*Ruling:* Zen means "only the
  terminal"; the existing `beforeZen` path covers both). Hiding the rail hides stripe buttons only.

### Stripe buttons and requests

- A detached tool window's stripe button stays in its slot; selected while its window is shown.
- A stripe click toggles, as it does for a docked tool window: hidden → show, raise and focus; shown → hide. (*Ruling,
  after Task 6's review:* a click on the main window's stripe makes the main window the active one, so "hide only when
  its window is active" could never hide from the stripe.)
- The activating toggles (View › Tool Windows, `<id>.toggle`, `ACTIVATING_TOGGLE`, as flush frames have it): hidden →
  show and focus; shown but its window not the active one → raise and focus; shown and active → hide — a shortcut
  pressed in the detached window itself reaches that last case.
- A child window's panel request (routed to the main window today, which comes to the front) raises the detached
  window instead of the main window.

### The ⋮ Options menu

`ToolWindowMenu` (header Options button, kit header's `host.options`, stripe button right-click) becomes:

1. **View Mode ▸** Docked / Float / Window (radio, current checked).
2. **Move to ▸** the six places (current checked). On a docked tool window it moves it, as now. *Ruling:* on a detached
   tool window it docks it at that place and shows it there — "move it to Left Top" reads as "put it there"; IntelliJ's
   float windows leave this ambiguous.
3. **Hide**.

The Options button's glyph becomes the vertical ellipsis (`more.vertical`, ⋮) in every header — native, flush strip and
borrowed kit header — as the dock spec named it, IntelliJ draws it, the user described it, and child windows' window
menu already draws it.

### The drag out (`DockDrag`)

- Sources are unchanged: stripe buttons (rail, labelled stripes, bottom strip) and docked headers' drag surfaces.
- While the pointer is outside the main window — or over another of Jasper's windows in front of it, as `TabDrag`'s
  covering rule has it — the target is **Detach**: the in-window guide and ghost go, and the tear-out label
  (`TabDragLabel`, the tool window's icon and title) follows the pointer.
- Release on Detach: `detach(id, FLOAT, bounds)` with bounds = (release point − the grab offset, the size above), fitted
  to the display under the pointer (`WindowPlacement.fitted`, as a torn-out tab is placed). Releasing a detached tool
  window's stripe button outside moves its window there.
- Inside the main window nothing changes: stripe and workspace drops dock as today (a workspace drop docks a detached
  tool window); a release on neither drops nothing. Esc and the window deactivating cancel.

### SDK (`dev.jasper.sdk.ui`, `@since 0.21.0`, SDK 0.21.0 is unreleased so no version bump)

```java
public enum PanelMode { DOCKED, FLOAT, WINDOW }
public record PanelPlacement(Anchor anchor, boolean split, PanelMode mode) {
    public boolean ownWindow() { return mode != PanelMode.DOCKED; }
}
// PanelHost:
PanelPlacement placement();                                   // UI thread
Subscription onPlacement(Consumer<PanelPlacement> handler);   // UI thread; contained; inert after close
```

- `anchor`/`split` name the panel's slot (its stripe position while detached). Notifications fire once per effective
  change of slot or mode, after the panel's component is in its new place; never for visibility alone. `window()` stays
  the main window (the tool window's owner) while detached.
- A plugin adapts by describing another `ViewSpec` and calling `UiView.update` (the kit keeps controls by id), the only
  layout path `PluginPresentationCheck` allows.
- App: `PanelSite` gains the placement supplier and `notifyPlacement`; `HostedUi.host` adapts it; `WindowContributions`
  notifies from `Change.placed`.
- Testkit: `FakePluginHost.placePanel(String panelId, PanelPlacement placement)` drives it; `ContractHarness` gains
  `placePanel`; contract tests (both runtimes): the initial placement is the spec's anchor and half, docked; a change
  notifies once and `placement()` reflects it; the same placement is silent; a throwing handler is contained; nothing
  after close.
- Sample plugin: its panel describes a different body when `ownWindow()` (the compiled plugin-authoring example).
- Docs: `docs/plugin-authoring.md` (Tool windows), the SDK README table, the testkit README, guide `07-contributions`,
  regenerated `docs/sdk-reference.html`, `docs/sdk-architecture.md`.

### User and architecture docs

`docs/workspace.md` "Tool windows and the dock" (drag out, View Mode, Move to on a detached window, close = hide);
`docs/app-architecture.md` rows for dock and workspace; `docs/STATUS.md` entries; `migration.csv` rows for new classes.

## Testing

Headless, as the rest of the dock: model tests for every mutation and `Change`; `UiState` round trips; the detached
window's core with a stand-in shell (native frames and dialogs are never built headless, like `WindowFrame` and
`NativeShells`); `DockDrag` releases outside the window on `TabDragTestSupport`'s pretend screen; menu items; toggle
semantics; restore; Zen; plugin removal; contract tests on both runtimes. GUI checks for the user on each platform:
drag out from rail icon, labelled tab and header; Float above / Window behind the main window; close = hide and stripe
click brings it back; View Mode ▸ Docked returns it to its stripe position; restart restores; shortcuts and the macOS
menu bar while it is focused; theme switch; two displays.

## Left to the builder

Class names and package placement for the detached window's core and native shell; how shortcut dispatch extends to a
second root; TOML key names; how `DockDrag` learns the covering windows; exact minimum sizes.
