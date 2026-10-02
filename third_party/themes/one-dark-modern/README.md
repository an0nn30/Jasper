# One Dark Modern Theme

A dark theme plugin for JetBrains IDEs, based on the [One Dark Modern](https://github.com/jdinhlife/vscode-onedark-modern) color palette. It also bundles [Material Icon Theme](https://github.com/material-extensions/vscode-material-icon-theme) file and folder icons, plus colorful tool window icons.

## Features

- **UI theme** — Dark chrome, tuned buttons (solid secondary + blue primary), popups, tool windows, trees, and lists
- **Editor scheme** — One Dark syntax colors for code, comments, strings, keywords, diffs, and console output
- **New UI** — Layout tweaks (tree/list row height, spacing) for IntelliJ Platform 2025.1+
- **Project view icons** — File and folder icons from Material Icon Theme (`material-icons.json`)
- **Colorful tool window icons** — Sidebar tool windows (Git, Terminal, Run, Debug, Problems, AI Chat, and more) with light/dark theme support

## Requirements

- IntelliJ Platform **2025.1** or later (`sinceBuild = 251`)
- Compatible with IntelliJ IDEA Community/Ultimate and other JetBrains IDEs built on the same platform version

## Installation

### From source

1. Clone this repository.
2. Build the plugin:

   ```bash
   ./gradlew buildPlugin
   ```

3. Install the generated plugin archive from `build/distributions/` via **Settings → Plugins → ⚙ → Install Plugin from Disk…**

### Run in a sandbox IDE

```bash
./gradlew runIde
```

## Usage

**Settings → Appearance & Behavior → Appearance & UI → Theme** → choose **One Dark Modern**.

The editor color scheme **One Dark Modern** is applied automatically with the theme. File icons and colorful tool window icons are enabled as soon as the plugin is installed.

## Development

Palette and UI keys are maintained in `scripts/update-theme.js`. After editing colors there, regenerate theme assets:

```bash
node scripts/update-theme.js
```

| Path | Purpose |
|------|---------|
| `src/main/resources/theme/one_dark_modern.theme.json` | UI theme definition |
| `src/main/resources/theme/one_dark_modern.xml` | Editor color scheme |
| `src/main/resources/META-INF/plugin.xml` | Plugin descriptor |
| `scripts/update-theme.js` | Palette sync script |
| `assets/icons/` | File and folder icons |
| `assets/material-icons.json` | Icon association rules from Material Icon Theme |
| `assets/color-icons/` | Colorful SVG icons for tool windows |
| `src/main/resources/color_toolwindow_icons.xml` | Maps IDE icon paths to colorful replacements |
| `src/main/kotlin/github/cweijan/materialicons/` | File icon providers and project view integration |
| `src/main/kotlin/github/cweijan/materialicons/toolwindow/` | Tool window icon patcher and lifecycle hooks |

### Adding a tool window icon

1. Place SVG files in `assets/color-icons/` (add a `_dark.svg` variant for dark theme when needed).
2. Register mappings in `src/main/resources/color_toolwindow_icons.xml`:

   ```xml
   <iconReplacement icon="/color-icons/your-icon.svg">
     <replacePath path="/toolwindows/toolWindowExample.svg" />
     <replacePath path="/icons/expui/toolwindows/example@20x20.svg" />
   </iconReplacement>
   ```

3. Rebuild and restart the IDE (or reload the plugin sandbox).

Dark theme icons are resolved automatically: for `your-icon.svg`, the patcher looks for `your-icon_dark.svg`.

## Credits

- UI theme and editor scheme are based on the [One Dark Modern](https://github.com/jdinhlife/vscode-onedark-modern) color palette.
- File icon associations and artwork are based on the [VSCode Material Icon Theme](https://github.com/material-extensions/vscode-material-icon-theme) project.
- Tool window colorful icons are inspired by community plugins such as Extra ToolWindow Colorful Icons.

## License

This project is licensed under the [MIT License](LICENSE).
