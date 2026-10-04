# Jasper dock layout: IntelliJ-style tool windows

**Status:** Design approved in chat by the user on 2026-10-01, section by section. Plan 1 (six-slot layout) and
plan 2 (drag and drop) were both implemented on branch `claude/dock-layout` on 2026-10-02.

Replaces: the single left rail (`WindowRail`), the three-region nested-split docking (`WorkspaceRegions`), the
"one panel per region" rule from the [plugin SDK design](2026-09-21-jasper-plugin-sdk-design.md) ("Rail and
panels") and plan 3a's decision that regions have no header or close button.

## Purpose

`Anchor` lets a plugin dock a panel LEFT, RIGHT or BOTTOM, and the rail's "Move to …" menu lets the user move it,
but there is only one rail, on the left, and it lists every region's icons in one column. Jasper gets IntelliJ's
tool window model instead: two stripes, six slots, headers, and drag-and-drop with IntelliJ's highlight guides,
built on a layout manager we own.

## Decisions (user, 2026-10-01)

1. **Two stripes, six slots.** A left and a right stripe. Each stripe's top group docks in the upper half of its
   side, the group under the grey separator in the lower half, and the group at the stripe's foot in its half of
   the bottom area. A window alone on its side (or alone in the bottom area) takes the whole of it.
2. **The bottom area spans full width**, stripe to stripe; side panels sit above it (IntelliJ's default, not its
   "widescreen" mode). No setting for the other geometry.
3. **Headers.** Every tool window gets an IntelliJ header: title, ⋮ options, — hide. The header is a drag handle.
4. **Drag and drop** from stripe icons and headers, with IntelliJ-style guides: a translucent highlight of where
   the window will land and a placeholder in the target stripe.
5. **Reorder within a stripe** is included; **View › Rail** hides both stripes ("hide tool window bars").
   **Maximize** and **float/window modes** are out of scope; the model leaves room for them.
6. **SDK:** `Anchor` stays; `PanelSpec.withSplit()` names the secondary half (IntelliJ's anchor + split).
7. **Approach:** our own dock layout over a pure geometry model, not nested `JSplitPane`s and not a docking
   library. The drop preview and the real layout come from the same geometry function, so they cannot disagree.
8. **Sizes are per side**, not per panel.
9. **SFTP** defaults to the left side's lower half; the other panels stay in the left top slot.

## 1. Layout model

New package `dev.jasper.app.dock`: the model, geometry and drop resolution. It is JDK-only and holds no Swing
components (it may use `java.awt.Rectangle`/`Point`); its `package-info` says so and
`verifyApplicationArchitecture` enforces it.

### Slots

```java
public enum Slot { LEFT_TOP, LEFT_BOTTOM, BOTTOM_LEFT, RIGHT_TOP, RIGHT_BOTTOM, BOTTOM_RIGHT }
```

Each slot has a side (`LEFT`, `RIGHT`, `BOTTOM`) and a split flag.

| Slot | Stripe | Stripe group | SDK default |
|---|---|---|---|
| `LEFT_TOP` | left | top | `Anchor.LEFT` |
| `LEFT_BOTTOM` | left | second, under the separator | `Anchor.LEFT` + `withSplit()` |
| `BOTTOM_LEFT` | left | foot | `Anchor.BOTTOM` |
| `RIGHT_TOP` | right | top | `Anchor.RIGHT` |
| `RIGHT_BOTTOM` | right | second | `Anchor.RIGHT` + `withSplit()` |
| `BOTTOM_RIGHT` | right | foot | `Anchor.BOTTOM` + `withSplit()` |

### State

`DockState` (mutable, one per window, EDT-confined by its owner) holds:

- per slot, an **ordered list** of tool-window ids (the stripe order) and **at most one active** id;
- **sizes per side**: left width, right width, bottom height (pixels, unscaled, defaults 260 / 260 / 200);
- **ratios**: left, right and bottom split ratios (0.1–0.9, default 0.5).

Operations: `add(id, slot)` (appends), `remove(id)`, `show(id)`, `hide(id)`, `toggle(id)`,
`move(id, slot, index)`, `resize(side, px)`, `setRatio(side, ratio)`. Showing a window hides the slot's other
active window. `move` keeps visibility (plan 2 adds a "move and show" for workspace drops). Every mutation that
changes something fires one change event carrying what changed (layout, stripes, visibility of which ids), so the
window can re-lay out, re-render the stripes, notify plugins and save.

### Geometry

`DockGeometry.compute(Rectangle bounds, DockState state, Minimums)` is a pure function returning rectangles for
each visible slot, the terminal column, and each divider (with what it resizes: a side's size or a ratio).

- The bottom area spans `bounds` from left to right edge; its height is the bottom height. If both bottom slots
  are active it splits horizontally at the bottom ratio; otherwise the active one takes the full width.
- Above it, the left side, the terminal column and the right side share the remaining height. A side with both
  slots active splits vertically at its ratio; a side with one active slot gives it the full height.
- Dividers are 6px. Each tool window and the terminal column have minimums (tool window 120px, as today; terminal
  column 200 × 120 px, scaled). When the window is too small, panels shrink toward their minimums in the order
  bottom, then right, then left, before the terminal column goes under its minimum. Sizes saved in the state are
  not changed by this squeeze; enlarging the window restores them.
- Gutters and rounded frames keep today's rules from `WorkspaceRegions.refreshGutters`: tool windows in their
  rounded `Host` frames, the center frame rounded only while the stripes are visible, and the terminal filling
  its area edge to edge when the stripes are hidden.

## 2. Stripes and headers

### Stripes

`ToolStripe` (in `workspace`) replaces `WindowRail`. There are two; `WindowContent`'s body becomes left stripe
WEST, `ToolWindowArea` CENTER, right stripe EAST. Both use `window.rail_width`. A stripe lays out, top to
bottom:

1. the **top group** (`LEFT_TOP` / `RIGHT_TOP`) in saved order;
2. a short grey **separator**, only when the second group has icons;
3. the **second group** (`LEFT_BOTTOM` / `RIGHT_BOTTOM`);
4. on the left stripe only: plugin **rail actions** (`Rail.add`) behind their own separator; they are plain
   buttons, not tool windows, and cannot be dragged;
5. flexible space;
6. the **foot group**, pinned to the bottom (`BOTTOM_LEFT` on the left stripe, `BOTTOM_RIGHT` on the right).

Button behaviour:

- Selected means active in its slot, painted as IntelliJ's rounded filled square.
- Click toggles the window.
- Right-click opens a menu with **Move to ›** (the six slots by their display names, current one checked) and
  **Hide**. This replaces "Move to Left/Right/Bottom".

Slot display names: Left Top, Left Bottom, Bottom Left, Right Top, Right Bottom, Bottom Right.

Visibility: both stripes show together whenever there is at least one tool window or rail action. An empty right
stripe still shows, because it is a drop target. `view.rail` (View › Rail) hides and shows both. With stripes
hidden, windows stay reachable from View › Tools, their shortcuts and the palette; drag works from headers only.

### Headers

`ToolWindowHeader` sits at the top of each tool window's rounded frame, inside the `Jasper.zone = chrome` host:

- the panel's title on the left;
- on the right, **⋮** (options menu: Move to › six slots, Hide) and **—** (hide);
- about 32px tall (scaled), painted in the tool window background with no separate bar colour, like IntelliJ's
  new UI;
- press-and-drag starts the same drag as a stripe icon (plan 2). Double-click does nothing in this project.

Hiding a window by any route returns focus to the terminal, as `hidePanel` does today. Plugin-contributed header
buttons and maximize are out of scope. Plan 1 checks the existing panels (Sessions, SFTP, Tunnels, File Servers,
Sample) for their own in-content title labels and removes any that would now duplicate the header.

## 3. Drag and drop (plan 2)

### Gesture

Press on a stripe icon or header, move more than 5px: a drag starts. It uses plain mouse events, not AWT
drag-and-drop, so it stays inside one window, needs no OS drag image and can be tested with synthetic events.
`DockDrag` owns the gesture. **Esc**, or releasing with no target, cancels.

### Targets

A target is `(slot, index)`, resolved by the pure `DockDropResolver` from the cursor position plus the stripe
group bounds, button bounds and workspace bounds:

- **Over a stripe:** the group under the cursor (top, second, foot) and the insertion index from button midpoints.
  During a drag both stripes show all three groups with their separators; an empty group gets one button's height
  of drop space.
- **Over the workspace** (between the stripes, from below the header to above the status bar): six edge zones, one
  for each slot. The target is the nearest of the workspace's left, right and bottom edges, each distance taken as a
  share of the workspace's own width (left, right) or height (bottom), so a wide or a tall workspace splits like any
  other. There is no top edge, because there are no top slots. The left and right edges split at half the height:
  the upper half is the side's top slot, the lower half its bottom slot. The bottom edge splits at half the width,
  into Bottom Left and Bottom Right. The three edges meet at the middle: a diagonal runs from each lower corner to
  it, and the half-height lines run across to it. A tie goes to a side over the bottom, and to the left over the
  right.

  ```
  ┌─────────────────────┬─────────────────────┐
  │                     │                     │
  │      LEFT_TOP       │     RIGHT_TOP       │
  │                     │                     │
  ├─────────────────────┼─────────────────────┤
  │ LEFT_BOTTOM      ╱     ╲     RIGHT_BOTTOM │
  │              ╱      │      ╲              │
  │          ╱          │          ╲          │
  │       ╱             │             ╲       │
  │    ╱    BOTTOM_LEFT │   BOTTOM_RIGHT ╲    │
  │                     │                     │
  └─────────────────────┴─────────────────────┘
  ```

  A workspace drop appends the window to that slot's group. Amended 2026-10-03 at the user's request: six edge zones
  replace the 3 × 2 grid, whose middle-third bottom halves were hard to hit.
- **Anywhere else** (title bar, status bar, outside the window): no target, no highlight; release cancels.

### Guides

`DockDropOverlay`, a transparent component on the layered pane's `DRAG_LAYER`, paints:

1. **Area highlight:** a translucent filled rectangle with a 1px border, exactly where the window would land,
   computed by `DockGeometry.compute` on a "what if" copy of the state (the dragged window active in the target
   slot). The real layout does not reflow during the drag. A stripe drop of a hidden window shows no area highlight,
   because the window stays hidden and no frame would appear; the placeholder and the ghost still show.
2. **Stripe placeholder:** the target group's buttons part to open a gap at the insertion index, holding a
   highlighted rounded square. The source button is lifted out of its old place.
3. **Ghost:** the dragged window's icon follows the cursor.

Colours: `DragAndDrop.areaBackground` and `DragAndDrop.borderColor` when the theme defines them, otherwise
`Component.accentColor` at about 25 % alpha for the fill and 60 % for the border. The theme system may map these to
its own tokens later.

### Drop

- **Stripe drop:** the window moves and keeps its visibility.
- **Workspace drop:** the window moves and shows; the slot's previously active window hides but stays on the
  stripe.
- Dropping where it already is changes nothing.
- A move does not fire `onVisibility` for plugins (as today); a workspace drop that shows a hidden window does.

## 4. SDK, saved state and wiring

### SDK (next minor: 0.13.0; 0.12.0 is the theme system's workspace appearance)

- `PanelSpec` gains a `boolean split` component, `split()` and `withSplit()`. The existing four- and
  five-argument constructors stay as explicit constructors, so plugins built against earlier SDKs still link.
  `withToggleActionId` preserves the flag.
- `Anchor` is unchanged. `PanelHost.show()` Javadoc changes from "its region" to "its slot".
- Testkit: `FakeUi` records the flag. New contract cases check that `withSplit()` survives
  `withToggleActionId` and that the app places a split panel in the secondary slot.
- Docs: SDK README, `docs/sdk-architecture.md` ("Panels, the rail and plugin windows") and
  `docs/plugin-authoring.md` (six slots, headers, drag).

### App wiring

- `PanelRegion` is removed; `dock.Slot` replaces it. `PanelEntry.defaultRegion` becomes `defaultSlot`;
  `HostedUi` maps `Anchor` + `split` to a `Slot`.
- `WindowContributions` keeps the lazy per-window `PanelInstance` (factory, `PanelSite`, placeholder on failure).
  Slot, visibility and order move into the window's `DockState`; `showPanel`, `hidePanel` and `movePanel` become
  calls on it, and one change listener re-renders the stripes, re-lays out the area, notifies plugins and saves.
- `WorkspaceRegions` is replaced by `ToolWindowArea` (container plus a `LayoutManager` over `DockGeometry`, with
  the dividers as its own components). `ToolWindowSurface` and the workspace styler integration carry over.
- Unchanged: `PanelRequest`, View › Tools, toggle actions and the `PANEL` shortcut context.

### `ui-state.toml` version 2

```toml
version = 2
rail_visible = true

[dock]
left_width = 260
right_width = 260
bottom_height = 200
left_ratio = 0.5
right_ratio = 0.5
bottom_ratio = 0.5

[panels."dev.jasper.remote.panel"]
slot = "LEFT_TOP"
order = 0
visible = true
```

- A saved slot wins over the plugin's default, as today. A panel with no saved entry is appended to its default
  slot's group.
- An unknown slot or an out-of-range value falls back to the default for that one entry or key, instead of the
  whole file falling back.
- **Migration from version 1:** LEFT → `LEFT_TOP`, RIGHT → `RIGHT_TOP`, BOTTOM → `BOTTOM_LEFT`; visibility carries
  over; each side's size comes from the panel visible in that region, else the first panel there; order follows
  registration. If two v1 panels were visible in the same region (impossible today but readable), the first wins.
  The next save writes version 2. Windows, chooser and other sections are untouched.
- Several windows read the same saved layout when they open; the last change saved wins (today's behaviour).

### Defaults for existing panels

SFTP (`SftpUi`) registers with `withSplit()`, so it opens in the left side's lower half beneath Sessions. Remote
takes a minor version bump and declares the new SDK floor. Sessions, Tunnels, File Servers and Sample stay
`Anchor.LEFT`. A user's saved positions win.

## 5. Testing, failures, delivery

### Tests

- Pure `dock` tests: `DockStateTest` (one active per slot, order, move and index, events), `DockGeometryTest`
  (every combination of active slots, ratios, minimums, squeezed windows, full-width bottom),
  `DockDropResolverTest` (the six edge zones, their ties and boundaries, stripe groups and indices, empty groups during
  a drag, no target outside).
- `UiStateTest`: version 2 round trip, v1 migration, per-entry fallback.
- Headless Swing tests in the `DesktopTestSupport` style: stripe layout (foot pinned, separator only when needed,
  rail actions after the second group), header buttons and menus, area layout against the geometry, divider drag,
  `DockDrag` with synthetic mouse events (5px threshold, Esc, stripe drop keeps visibility, workspace drop shows,
  reorder), and an overlay paint test into a `BufferedImage` checking the highlight lands in the expected
  rectangle.
- Rewritten for the new model: `WindowPanelsTest`, `WorkspaceRegionsTest`, `RailWidthTest`, `SidePanelRenderTest`,
  SDK/testkit contract tests, and plugin tests that name regions.

### Failures

- A failing panel factory still shows the "This panel could not be loaded." placeholder.
- A bad saved entry falls back alone.
- A plugin unloading or the window closing mid-drag cancels the drag; resizing the window mid-drag recomputes the
  targets.

### Delivery

1. **Plan 1, six-slot layout:** `dock` model and geometry, `ToolWindowArea`, two stripes, headers, six-slot Move to
   menus, `UiState` v2 with migration, `PanelSpec.withSplit()` (SDK 0.13.0), the SFTP default, docs. Everything but
   dragging works.
2. **Plan 2, drag and drop:** `DockDropResolver`, `DockDrag`, `DockDropOverlay`, the stripe gap and placeholder,
   reorder within a stripe, header drag.

GUI checks for the user after each plan: look against IntelliJ, divider feel, drag guides, saved positions across
a restart. Agents do not launch the GUI.
