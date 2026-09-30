# Themes

Jasper's UI theme is an IntelliJ theme. Two are built in: **Jasper Dark** (the default) and
**IntelliJ Light**, the classic IntelliJ Light. Any theme plugin from the JetBrains Marketplace
(plugins.jetbrains.com) can be installed.

## Choosing a theme

Name the theme in `config.toml`:

```toml
[ui.theme]
name = "Dracula"
```

The name is the theme's own name, matched ignoring case and surrounding spaces. Changing it applies the theme to
every window at once, keeping shells, scrollback and splits. Settings › Appearance › Theme lists every built-in and
installed theme; **Get More Themes…** at the bottom opens Settings › Themes › IntelliJ Themes.

## Installing a theme

Settings › Themes › IntelliJ Themes has two tabs.

- **Marketplace** searches the JetBrains Marketplace's theme plugins (most downloaded first). Select one to read its
  description, then **Install**. Jasper downloads the latest release, checks that it has a theme Jasper can use, and
  puts it in the `themes` folder beside `config.toml` (`~/.config/jasper/themes/` on macOS); the banner then offers
  **Use "…"**, which chooses it on the Appearance page (Apply or OK switches). Paid plugins are not listed; freemium
  ones are marked. Clicking a link in a description opens it in your browser (http and https only).
- **Installed** lists the theme plugins in the folder, with **Use** for each theme, **Update to …** when the
  Marketplace has a newer release of one installed from it, and **Uninstall**. **Install from Disk…** copies a
  downloaded `.jar` or `.zip` into the folder; **Reveal Folder** shows it.

You can also put a plugin's `.jar` or `.zip`, unchanged, in the folder yourself and choose **Reload config**.

Jasper contacts the Marketplace only while that page is open: searches, the selected plugin's details and icons, the
download, and one update check per Marketplace install each time the Installed tab opens. Requests carry your search
text and nothing else about you. Uncheck **Browse the JetBrains Marketplace** (`themes.marketplace.enabled = false`)
to hide the Marketplace tab and stop update checks.

Jasper finds a plugin's themes through its `META-INF/plugin.xml` and reads each theme's `.theme.json` and editor
colour scheme straight from the file. Nothing is unpacked, and no code in the plugin runs. A plugin whose theme needs
IntelliJ's code, such as Material Theme UI, is refused with a message. A theme you install takes precedence over a
built-in theme of the same name. Marketplace installs are named `<plugin id>-<version>`, and `themes/.sources.toml`
records where each came from.

## What is supported

- Classic-UI theme plugins: the `.theme.json` files a plugin declares with `themeProvider`,
  including several themes in one plugin.
- A theme's `parentTheme` when it is one of the themes IntelliJ bundles: the classic `Darcula`,
  `IntelliJ` and `JetBrainsLightTheme`, the New UI `ExperimentalDark`, `ExperimentalLight` and
  `ExperimentalLightWithLightHeader`, and `Islands Dark`, `Islands Light`, `Islands Darcula` and
  `JetBrainsHighContrastTheme`. They are vendored as parents only, not offered as themes. Any other
  parent, such as another plugin's theme, is replaced by Darcula or IntelliJ by the theme's darkness,
  with a warning.
- The theme's editor colour scheme, for the terminal (below).

Not supported:
- themes a plugin draws in code, such as Material Theme UI;
- loose `.theme.json` files;
- background images;
- icon recolouring;
- New UI-only layout keys.

## Terminal colours

With `terminal.colors = "match"` (the default), a theme that has an editor colour scheme
colours the terminal with the scheme's console colours: background, text, cursor, selection and
the 16 ANSI colours.
- **Missing colours:** colours the scheme leaves out come from its parent schemes, as in IntelliJ's console. A theme's `editorScheme` or a scheme's `parent_scheme` may name any scheme IntelliJ bundles: `Default`, `Darcula`, `IntelliJ Light`, `Light`, `Dark`, `Darcula Contrast`, `High contrast` and `Islands Dark`.
- **No scheme:** a theme without one, and the built-in themes, use Jasper Dark or Jasper Light by the theme's darkness.
- **Forcing Jasper's palettes:** `"light"` and `"dark"` always use Jasper's palettes.

Scheme colours are used exactly as the theme author chose them.

## When something is wrong

Problems appear under the status bar's **Config warnings**, naming the file:
- An unknown `ui.theme.name` lists the themes available. At start-up Jasper uses Jasper Dark; on a reload it keeps the theme showing.
- A file that isn't a readable plugin, or a plugin with no theme files, is reported.
- A theme that fails to load, or names a parent theme Jasper doesn't have, is reported.
- Two installed themes with the same name are reported, and the file that sorts first wins.
- An unreadable editor scheme is reported, and the terminal then uses Jasper's palette.

No problem stops Jasper from starting. Each plugin is capped at 64 MiB, each file read from it at
4 MiB, and each jar inside a `.zip` at 32 MiB, with at most 16 such jars read.
