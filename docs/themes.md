# Themes

Jasper has one theme, **Slate Steel**, in Dark and Light, and three terminal colour schemes: **Slate Steel Dark**,
**Slate Steel Light** and **High Contrast**. Its colours are defined once, in
[the Slate Steel colour reference](design/theme-engine/slate-steel.md), and bundled as `slate-steel.json`; there are no
other themes, no themes folder and no theme import.

## Choosing the appearance and the terminal scheme

```toml
[ui]
appearance = "system" # or "dark", "light"

[terminal]
scheme = "match" # or "slate-steel-dark", "slate-steel-light", "high-contrast"
```

`ui.appearance` (`system`, `dark`, `light`) chooses Slate Steel Dark or Light, or follows the operating system;
`terminal.scheme` (`match`, `slate-steel-dark`, `slate-steel-light`, `high-contrast`) colours the terminal panes. With
`system`, Jasper follows the operating system's light or dark setting live and uses Dark when it cannot read it; an
operating-system change never writes `config.toml`. `match` uses Slate Steel Dark with the Dark appearance and Slate
Steel Light with the Light one.

Settings › Appearance & Behavior › Appearance has the Dark, Light and Match System tiles, the Theme row (Slate Steel)
and the **Terminal scheme** dropdown, whose first entry, **Same as UI theme**, is `match`. Terminal › Font & Colors
links there, and the command palette's **Themes…** opens it. Choices stay staged until Apply or OK.

## What the terminal scheme colours

A scheme colours only the terminal text area: background, text, bold text, cursor, selection and the 16 ANSI colours.
Tabs, the tab strip, the area around panes, the editor, panels and dialogs stay on the appearance. With `match` the
text area and the active tab meet seamlessly; with High Contrast the text area is black and the tab keeps the
appearance's terminal surface. Workspace opacity and blur apply to the terminal area. The editor always follows the
appearance, with the editor scheme derived from Slate Steel.

## The token rule

Every colour in Jasper's interface code is a semantic role (`surface.panel`, `text.primary`, `accent`, …) read from the
active variant. A test fails on any hex colour literal (`#RRGGBB`, `0xRRGGBB` given to a colour constructor) in the
production code or text resources of jasper-ui, the app and the plugins outside `slate-steel.json`. Outside that scan
are SVG artwork (icons are painted as drawn), the terminal library's default palettes for embedders that set none, the
desk buddy's own artwork, decimal component literals (transparent fills, the editor's vim sneak highlight) and the
black and white that shadows and inset lines are painted in. A role that carries opacity (`surface.scrim`,
`border.subtle`, `text.onAccent.secondary`) is painted as given; treatments such as focus rings, hovers and tile tints
are kit metrics applied to a role. Shadows are black at the strengths the colour reference gives. Plugins receive the
same roles through the SDK.

## When something is wrong

- An unknown `ui.appearance` or `terminal.scheme` value is reported ("Use …; using the default.") and uses the
  default (`system`, `match`).
- A removed setting (`[ui.theme]`, `terminal.colors`, `[colors]`) is reported as an unknown setting and ignored.
- If Slate Steel cannot be installed, start-up uses Dark and a reload keeps the current appearance; Reload config
  retries.

## Native acceptance for this upgrade (user-run)

On a Mac, check Dark, Light and Match System (switch the operating system's appearance with Settings and a sheet
open), each terminal scheme including High Contrast, and compare the mocked pages at 100%; the
[acceptance record](design/theme-engine/acceptance.md) lists every check.
