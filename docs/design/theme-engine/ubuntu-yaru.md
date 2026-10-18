# Ubuntu Yaru

`ubuntu-yaru.json` is Jasper's Ubuntu Yaru theme, with Light and Dark variants. Select Ubuntu Yaru for both theme
picks in Settings > Appearance & Behavior > Appearance and Same as UI theme for the terminal scheme.

The source is Ubuntu 26.04's [Yaru 26.04.5.1ubuntu](https://github.com/ubuntu/yaru/tree/26.04.5.1ubuntu), commit
`f01c3e9a257296242806f8e0c5d4a660516f2181`: the GTK
[palette](https://github.com/ubuntu/yaru/blob/26.04.5.1ubuntu/gtk/src/default/gtk-3.0/_palette.scss) and
[semantic colours](https://github.com/ubuntu/yaru/blob/26.04.5.1ubuntu/gtk/src/default/gtk-3.0/_colors.scss).
The [Ubuntu package record](https://packages.ubuntu.com/resolute/all/yaru-theme-gtk) identifies this release.

| Role | Light | Dark | Mapping |
| --- | --- | --- | --- |
| Window | `#FAFAFA` | `#2C2C2C` | Yaru background; Dark is jet lightened by 8% |
| Title bar | `#FFFFFF` | `#242424` | Yaru header-bar relationship: white / darker charcoal |
| Terminal, editor, input | `#FFFFFF` | `#272727` | Yaru base: white / jet lightened 6% |
| Primary text | `#3D3D3D` | `#F7F7F7` | Inkstone / porcelain |
| Tab indicator | `#E95420` | `#E95420` | Ubuntu orange |
| Primary action | `#C34113` | `#C34113` | Orange darkened for at least 4.5:1 with white labels |
| Link | `#A93610` | `#FFAB8D` | Orange adjusted for small text on neutral surfaces |

Jasper's panel islands use white in Light and `#303030` in Dark, with raised cards at `#363636` in Dark. Explicit
header, table, hover and divider roles keep those surfaces neutral; orange marks focus, selection and primary actions.
The title bar follows each appearance. Menus use Yaru's `#FAFAFA` / `#1D1D1D`; secondary actions stay gray.

The terminal and editor use the matching base so the active tab meets its content. Their palettes are Jasper adaptations
of Yaru's red, green, amber, blue and aubergine hues, with brighter inks on Dark and darker inks on Light. Terminal
selection uses a muted orange tint that preserves the default foreground; editor comments use the neutral tertiary
text. Syntax colours meet 4.5:1 against the editor background. The theme uses Jasper's existing geometry and typography.

`UbuntuYaruThemeTest` checks text contrast and matching terminal surfaces, and renders the control gallery headlessly to
`jasper-ui/build/gallery/ubuntu-yaru-light/` and `ubuntu-yaru-dark/`. Run it with
`./gradlew :jasper-ui:test --tests '*UbuntuYaruThemeTest'`. Native Ubuntu acceptance: select both variants, check Settings,
a menu, a focused field, a selected row, terminal ANSI output and an editor, then switch the system appearance.
