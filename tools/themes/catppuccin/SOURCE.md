# Catppuccin for JetBrains

Source: https://github.com/catppuccin/jetbrains, release v3.6.1 (tag commit `71b78a50557d87af64ec75bb1b1bdfa17f416ed8`).
`Catppuccin.Theme-3.6.1.jar` is the release's plugin jar, byte for byte (SHA-256
`3a1104e0db53ff5ba216c94c9e730a85c7ac2847f4e5fe82d91b99dc91045fff`, 100,851 bytes), taken from the Marketplace
download `Catppuccin_Theme-3.6.1.zip` (`Catppuccin Theme/lib/Catppuccin Theme-3.6.1.jar`).
License: MIT (`LICENSE`, "Copyright (c) 2022 Catppuccin"). Jasper ships a copy as
`jasper-ui/src/main/resources/dev/jasper/ui/theme/catppuccin.LICENSE.txt`, listed in the Licenses window.

Jasper's built-in Catppuccin themes are the converter's output for the jar's four classic themes:

- `themes/latte.theme.json`, Catppuccin Latte (Light): `catppuccin-latte.json`
- `themes/frappe.theme.json`, Catppuccin Frappé (Dark): `catppuccin-frappe.json`
- `themes/macchiato.theme.json`, Catppuccin Macchiato (Dark): `catppuccin-macchiato.json`
- `themes/mocha.theme.json`, Catppuccin Mocha (Dark): `catppuccin-mocha.json`

The `*-islands.theme.json` variants are not built in, but each classic theme takes its window chrome (`surface.app`,
crust) from its Islands variant, as the converter does for any plugin that ships one. `roles.json` hand-picks the
dialog body, card and sidebar from each flavour's palette, layered as Slate Cobalt's are (IntelliJ's Catppuccin paints all
three mantle): base, mantle and crust in the dark flavours; mantle, base and crust in Latte. Each theme's editor colours
come from the scheme its `editorScheme` names (`themes/<flavour>.xml`, the italic one).

`test_jasper_theme.py` converts the jar with `roles.json` and compares the result with the built-in files byte for byte. After a converter
change, `JASPER_THEME_UPDATE_GOLDEN=1 python3 -m unittest discover -s tools/themes -p 'test_*.py'` rewrites them.
