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

The name is the theme's own name, matched ignoring case and surrounding spaces. Changing it
applies the theme to every window at once, keeping shells, scrollback and splits. There is no
theme picker: the configuration file is the only place a theme is chosen.

## Installing a Marketplace theme

1. Download the theme plugin from the Marketplace, using the version for a recent IntelliJ. It is a `.jar` or a
   `.zip`.
2. Put the file, unchanged, in the `themes` folder beside `config.toml`
   (`~/.config/jasper/themes/` on macOS). Jasper creates the folder when it starts.
3. Set `ui.theme.name` to the theme's name and save. If the name is already set, choose
   **Reload config**.

Jasper reads the folder when it starts, when `config.toml` changes and on **Reload config**. It
finds a plugin's themes through its `META-INF/plugin.xml`, and reads each theme's `.theme.json`
and editor colour scheme straight from the file. Nothing is unpacked, and no code in the plugin
runs. A theme you install takes precedence over a built-in theme of the same name.

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
