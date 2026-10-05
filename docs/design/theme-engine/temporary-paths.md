# Plan 1 temporary theme paths and Plan 2 removal ownership

Audited 2026-10-04 after Tasks 1–13. This is the exact-path extraction/removal ledger linked by
[Plan 2](../../superpowers/plans/2026-10-04-theme-engine-2-consumer-migration.md); it does not claim
consumer migration. The designated reusable algorithm/value authority is `jasper-ui`'s `theme`,
`themeio` and `themeimport` packages. App copies still serve unmigrated consumers; new kit/SDK paths
use module generation/import contracts. Do not maintain the old algorithms as a second permanent
engine. Plan 2 Tasks 2/3 redirect consumers before deleting copies, preserving selection, import
limits, filesystem locks/fingerprints and saved schema. Plan 2 Task 12 verifies no competing engine.

The initial basename inventory contained 28 pairs. One pair is unrelated in meaning:
`app.config.TerminalColors` stores a saved choice, whereas `ui.theme.TerminalColors` stores 20 actual
colors. The saved-choice record remains app configuration; the other 27 app paths below are temporary.

| Exact app path | Module authority | Removal/adaptation owner | Disposition |
|---|---|---|---|
| `jasper-app/src/main/java/dev/jasper/app/appearance/GeneratedTheme.java` | `jasper-ui/src/main/java/dev/jasper/ui/theme/GeneratedTheme.java` | Plan 2 Task2 | Remove after consumers use module values/algorithms; retain behavior tests adapted to module contracts. |
| `jasper-app/src/main/java/dev/jasper/app/appearance/GlassLevels.java` | `jasper-ui/src/main/java/dev/jasper/ui/theme/GlassLevels.java` | Plan 2 Task2 | Remove after consumers use module values/algorithms; retain behavior tests adapted to module contracts. |
| `jasper-app/src/main/java/dev/jasper/app/appearance/Seed.java` | `jasper-ui/src/main/java/dev/jasper/ui/theme/Seed.java` | Plan 2 Task2 | Remove after consumers use module values/algorithms; retain behavior tests adapted to module contracts. |
| `jasper-app/src/main/java/dev/jasper/app/appearance/SyntaxRole.java` | `jasper-ui/src/main/java/dev/jasper/ui/theme/SyntaxRole.java` | Plan 2 Task2 | Remove after consumers use module values/algorithms; retain behavior tests adapted to module contracts. |
| `jasper-app/src/main/java/dev/jasper/app/appearance/SyntaxStyle.java` | `jasper-ui/src/main/java/dev/jasper/ui/theme/SyntaxStyle.java` | Plan 2 Task2 | Remove after consumers use module values/algorithms; retain behavior tests adapted to module contracts. |
| `jasper-app/src/main/java/dev/jasper/app/config/TerminalColors.java` | `jasper-ui/src/main/java/dev/jasper/ui/theme/TerminalColors.java` | Plan 2 Task2 | Retain saved-choice record; adapt palette resolution. This is a basename collision, not an extracted color-value copy. |
| `jasper-app/src/main/java/dev/jasper/app/appearance/ThemeDocument.java` | `jasper-ui/src/main/java/dev/jasper/ui/theme/ThemeDocument.java` | Plan 2 Task2 | Remove after consumers use module values/algorithms; retain behavior tests adapted to module contracts. |
| `jasper-app/src/main/java/dev/jasper/app/appearance/ThemeFileException.java` | `jasper-ui/src/main/java/dev/jasper/ui/theme/ThemeFileException.java` | Plan 2 Task2 | Remove after consumers use module values/algorithms; retain behavior tests adapted to module contracts. |
| `jasper-app/src/main/java/dev/jasper/app/appearance/ThemeGenerator.java` | `jasper-ui/src/main/java/dev/jasper/ui/theme/ThemeGenerator.java` | Plan 2 Task2 | Remove after consumers use module values/algorithms; retain behavior tests adapted to module contracts. |
| `jasper-app/src/main/java/dev/jasper/app/appearance/ThemeResolver.java` | `jasper-ui/src/main/java/dev/jasper/ui/theme/ThemeResolver.java` | Plan 2 Task2 | Remove after consumers use module values/algorithms; retain behavior tests adapted to module contracts. |
| `jasper-app/src/main/java/dev/jasper/app/appearance/Token.java` | `jasper-ui/src/main/java/dev/jasper/ui/theme/Token.java` | Plan 2 Task2 | Remove after consumers use module values/algorithms; retain behavior tests adapted to module contracts. |
| `jasper-app/src/main/java/dev/jasper/app/appearance/UiTemplate.java` | `jasper-ui/src/main/java/dev/jasper/ui/theme/UiTemplate.java` | Plan 2 Task2 | Remove after consumers use module values/algorithms; retain behavior tests adapted to module contracts. |
| `jasper-app/src/main/java/dev/jasper/app/appearance/WorkspaceGlass.java` | `jasper-ui/src/main/java/dev/jasper/ui/theme/WorkspaceGlass.java` | Plan 2 Task2 | Remove after consumers use module values/algorithms; retain behavior tests adapted to module contracts. |
| `jasper-app/src/main/java/dev/jasper/app/appearance/ThemeToml.java` | `jasper-ui/src/main/java/dev/jasper/ui/themeio/ThemeToml.java` | Plan 2 Task2 | Remove after consumers use module values/algorithms; retain behavior tests adapted to module contracts. |
| `jasper-app/src/main/java/dev/jasper/app/appearance/ThemeTomlWriter.java` | `jasper-ui/src/main/java/dev/jasper/ui/themeio/ThemeTomlWriter.java` | Plan 2 Task2 | Remove after consumers use module values/algorithms; retain behavior tests adapted to module contracts. |
| `jasper-app/src/main/java/dev/jasper/app/themeimport/AlacrittyConversion.java` | `jasper-ui/src/main/java/dev/jasper/ui/themeimport/AlacrittyConversion.java` | Plan 2 Task3 | Remove after consumers use module values/algorithms; retain behavior tests adapted to module contracts. |
| `jasper-app/src/main/java/dev/jasper/app/themeimport/AlacrittyTheme.java` | `jasper-ui/src/main/java/dev/jasper/ui/themeimport/AlacrittyTheme.java` | Plan 2 Task3 | Remove after consumers use module values/algorithms; retain behavior tests adapted to module contracts. |
| `jasper-app/src/main/java/dev/jasper/app/themeimport/ConversionResult.java` | `jasper-ui/src/main/java/dev/jasper/ui/themeimport/ConversionResult.java` | Plan 2 Task3 | Remove after consumers use module values/algorithms; retain behavior tests adapted to module contracts. |
| `jasper-app/src/main/java/dev/jasper/app/themeimport/EditorScheme.java` | `jasper-ui/src/main/java/dev/jasper/ui/themeimport/EditorScheme.java` | Plan 2 Task3 | Remove after consumers use module values/algorithms; retain behavior tests adapted to module contracts. |
| `jasper-app/src/main/java/dev/jasper/app/appearance/FlatLafThemeData.java` | `jasper-ui/src/main/java/dev/jasper/ui/themeimport/FlatLafThemeData.java` | Plan 2 Task2 | Remove after consumers use module values/algorithms; retain behavior tests adapted to module contracts. |
| `jasper-app/src/main/java/dev/jasper/app/themeimport/IntellijConversion.java` | `jasper-ui/src/main/java/dev/jasper/ui/themeimport/IntellijConversion.java` | Plan 2 Task3 | Remove after consumers use module values/algorithms; retain behavior tests adapted to module contracts. |
| `jasper-app/src/main/java/dev/jasper/app/themeimport/SafeXml.java` | `jasper-ui/src/main/java/dev/jasper/ui/themeimport/SafeXml.java` | Plan 2 Task3 | Remove after consumers use module values/algorithms; retain behavior tests adapted to module contracts. |
| `jasper-app/src/main/java/dev/jasper/app/themeimport/ThemeArchive.java` | `jasper-ui/src/main/java/dev/jasper/ui/themeimport/ThemeArchive.java` | Plan 2 Task3 | Remove after consumers use module values/algorithms; retain behavior tests adapted to module contracts. |
| `jasper-app/src/main/java/dev/jasper/app/themeimport/ThemeConverter.java` | `jasper-ui/src/main/java/dev/jasper/ui/themeimport/ThemeConverter.java` | Plan 2 Task3 | Remove after consumers use module values/algorithms; retain behavior tests adapted to module contracts. |
| `jasper-app/src/main/java/dev/jasper/app/themeimport/ThemeLoader.java` | `jasper-ui/src/main/java/dev/jasper/ui/themeimport/ThemeLoader.java` | Plan 2 Task3 | Remove after consumers use module values/algorithms; retain behavior tests adapted to module contracts. |
| `jasper-app/src/main/java/dev/jasper/app/themeimport/ThemeParents.java` | `jasper-ui/src/main/java/dev/jasper/ui/themeimport/ThemeParents.java` | Plan 2 Task3 | Remove after consumers use module values/algorithms; retain behavior tests adapted to module contracts. |
| `jasper-app/src/main/java/dev/jasper/app/themeimport/ThemeSources.java` | `jasper-ui/src/main/java/dev/jasper/ui/themeimport/ThemeSources.java` | Plan 2 Task3 | Remove after consumers use module values/algorithms; retain behavior tests adapted to module contracts. |
| `jasper-app/src/main/java/dev/jasper/app/appearance/WorkspaceTokenKeys.java` | `jasper-ui/src/main/java/dev/jasper/ui/themeimport/WorkspaceTokenKeys.java` | Plan 2 Task2 | Remove after consumers use module values/algorithms; retain behavior tests adapted to module contracts. |

Differently named equivalents and installation bridges have these dispositions. App policy/lifetime
owners remain app code, but lose any copied color derivation/generation when their consumers migrate.

| Exact app path | Module authority or responsibility | Plan 2 owner | Disposition |
|---|---|---|---|
| `jasper-app/src/main/java/dev/jasper/app/plugins/KitThemeBridge.java` | `theme.ThemePair`, module generator/semantic values | Task 2, adapter cleanup verified in Task 4 | Remove old-app-to-module conversion and test-host fallback after host receives app-owned module bindings. |
| `jasper-app/src/main/java/dev/jasper/app/appearance/ChromeKeys.java` | `theme.UiColorKeys.chromeAliases()`, `theme.SemanticDefaults`, `theme.ColorMath`, kit installer | Tasks 2/5 | Remove legacy UIDefaults color derivation after workspace/chrome consumers use canonical semantics; preserve existing alias behavior during cutover. |
| `jasper-app/src/main/java/dev/jasper/app/appearance/WorkspaceValues.java` | module generation/semantic values and `swing.FlatLafInstaller` | Task 2 | Remove private legacy FlatLaf resolve/regenerate path after module bindings supply effective workspace values. |
| `jasper-app/src/main/java/dev/jasper/app/appearance/WorkspaceDefaults.java` | module semantic publication and app adapters | Tasks 2/5 | Remove legacy `Jasper.workspace.*` token publication after remaining consumers migrate; any compatibility alias publication is app adapter policy only. |
| `jasper-app/src/main/java/dev/jasper/app/appearance/BuiltInThemes.java` | `themeimport.NativeThemeParents` owns independent pure import-parent loading | Tasks 2/3 | Retain/adapt app catalog entries and lazy discovery; remove parser/generator copies and redirect bundled pure parent inputs. |
| `jasper-app/src/main/java/dev/jasper/app/appearance/ThemeCatalog.java` | app native-file discovery/caching/selection policy | Tasks 2/3 | Retain owner and size/fingerprint boundaries; use module documents/resolution. |
| `jasper-app/src/main/java/dev/jasper/app/appearance/ThemeManager.java` | app installation/routing lifecycle; module installer/metrics | Tasks 2/5 | Retain application policy; replace legacy generation, token/geometry installation and global appearance derivation with module contracts. |
| `jasper-app/src/main/java/dev/jasper/app/appearance/Theme.java` | app catalog/source/selection wrapper | Task 2 | Retain/adapt wrapper to module values; remove embedded use of legacy pure engine. |
| `jasper-app/src/main/java/dev/jasper/app/appearance/ResolvedTheme.java` | app resolved selection wrapper | Task 2 | Retain/adapt wrapper to module snapshots. |
| `jasper-app/src/main/java/dev/jasper/app/appearance/ThemeState.java` | app selected chrome/workspace lifetime | Task 2 | Retain/adapt selection state and independent routes. |
| `jasper-app/src/main/java/dev/jasper/app/appearance/ThemeController.java` | app saved-selection/reload/rollback owner | Task 2 | Retain owner; bind module `ThemePair` transactionally. |
| `jasper-app/src/main/java/dev/jasper/app/themeimport/ThemeImporter.java` | app plan/commit and fingerprint lock policy | Task 3 | Retain owner; consume module conversion values. |
| `jasper-app/src/main/java/dev/jasper/app/themeimport/ThemeFiles.java` | app filesystem publication | Task 3 | Retain owner/limits/locks; redirect parsing/serialization to module. |
| `jasper-app/src/main/java/dev/jasper/app/themeimport/ThemeMigration.java` | app user-file migration policy | Task 3 | Retain owner/rollback guarantees; consume module algorithms. |
| `jasper-app/src/main/java/dev/jasper/app/themeimport/MigrationJournal.java` | app durable publication journal | Task 3 | Retain file identity/locking owner; pure converter's bounded reading already lives in module `ThemeConverter`. |
| `jasper-app/src/main/java/dev/jasper/app/themeimport/ConvertThemes.java` | app command-line entry point | Task 3 | Retain entry point; delegate conversion to module. |

`theme.UiColorKeys` is a new extracted color-policy helper rather than a same-name app file:
its legacy alias source is app `UiTemplate`, removed under Task 2 above. `themeimport.ImportPalettes`
replaces the app import callers' uses of `jasper-terminal/.../config/Palette.java`'s Jasper fallback
methods (`AlacrittyTheme`, `EditorScheme`, `ThemeConverter`, all Task 3). The terminal library and
its supported palette factories remain independent and are not removal targets. Native-parent loading
formerly used app `ThemeConverter` → `ThemeManager.builtIns()` → `BuiltInThemes`; Task 3 redirects
that pure conversion chain to module `NativeThemeParents` without taking app catalog ownership.

Exact copied-resource paths are listed below. The imported IntelliJ resources are byte-identical.
Module native parent TOML/index inputs are byte-identical to the app catalog inputs. Task 3 redirects
import readers and removes the duplicate app IntelliJ copy. Tasks 2/3 select the module native resource
source for catalog/import use; app catalog wrappers remain and shared theme license/alias policy is
preserved. License/provenance files travel with their assets. No resource is deleted in Plan 1.

| Exact app resource | Module resource authority | Plan 2 owner | Disposition |
|---|---|---|---|
| `jasper-app/src/main/resources/dev/jasper/app/themeimport/intellij/DefaultColorSchemesManager.xml` | `jasper-ui/src/main/resources/dev/jasper/ui/themeimport/intellij/DefaultColorSchemesManager.xml` | Task 3 | Redirect import readers; remove duplicate app asset. |
| `jasper-app/src/main/resources/dev/jasper/app/themeimport/intellij/LICENSE.txt` | `jasper-ui/src/main/resources/dev/jasper/ui/themeimport/intellij/LICENSE.txt` | Task 3 | Redirect import readers; remove duplicate app asset. |
| `jasper-app/src/main/resources/dev/jasper/app/themeimport/intellij/Light.theme.json` | `jasper-ui/src/main/resources/dev/jasper/ui/themeimport/intellij/Light.theme.json` | Task 3 | Redirect import readers; remove duplicate app asset. |
| `jasper-app/src/main/resources/dev/jasper/app/themeimport/intellij/Light.xml` | `jasper-ui/src/main/resources/dev/jasper/ui/themeimport/intellij/Light.xml` | Task 3 | Redirect import readers; remove duplicate app asset. |
| `jasper-app/src/main/resources/dev/jasper/app/themeimport/intellij/NOTICE.txt` | `jasper-ui/src/main/resources/dev/jasper/ui/themeimport/intellij/NOTICE.txt` | Task 3 | Redirect import readers; remove duplicate app asset. |
| `jasper-app/src/main/resources/dev/jasper/app/themeimport/intellij/SOURCE.md` | `jasper-ui/src/main/resources/dev/jasper/ui/themeimport/intellij/SOURCE.md` | Task 3 | Redirect import readers; remove duplicate app asset. |
| `jasper-app/src/main/resources/dev/jasper/app/themeimport/intellij/assets.tsv` | `jasper-ui/src/main/resources/dev/jasper/ui/themeimport/intellij/assets.tsv` | Task 3 | Redirect import readers; remove duplicate app asset. |
| `jasper-app/src/main/resources/dev/jasper/app/themeimport/intellij/darcula.theme.json` | `jasper-ui/src/main/resources/dev/jasper/ui/themeimport/intellij/darcula.theme.json` | Task 3 | Redirect import readers; remove duplicate app asset. |
| `jasper-app/src/main/resources/dev/jasper/app/themeimport/intellij/expUI/expUI_darculaContrastScheme.xml` | `jasper-ui/src/main/resources/dev/jasper/ui/themeimport/intellij/expUI/expUI_darculaContrastScheme.xml` | Task 3 | Redirect import readers; remove duplicate app asset. |
| `jasper-app/src/main/resources/dev/jasper/app/themeimport/intellij/expUI/expUI_dark.theme.json` | `jasper-ui/src/main/resources/dev/jasper/ui/themeimport/intellij/expUI/expUI_dark.theme.json` | Task 3 | Redirect import readers; remove duplicate app asset. |
| `jasper-app/src/main/resources/dev/jasper/app/themeimport/intellij/expUI/expUI_darkScheme.xml` | `jasper-ui/src/main/resources/dev/jasper/ui/themeimport/intellij/expUI/expUI_darkScheme.xml` | Task 3 | Redirect import readers; remove duplicate app asset. |
| `jasper-app/src/main/resources/dev/jasper/app/themeimport/intellij/expUI/expUI_light.theme.json` | `jasper-ui/src/main/resources/dev/jasper/ui/themeimport/intellij/expUI/expUI_light.theme.json` | Task 3 | Redirect import readers; remove duplicate app asset. |
| `jasper-app/src/main/resources/dev/jasper/app/themeimport/intellij/expUI/expUI_lightScheme.xml` | `jasper-ui/src/main/resources/dev/jasper/ui/themeimport/intellij/expUI/expUI_lightScheme.xml` | Task 3 | Redirect import readers; remove duplicate app asset. |
| `jasper-app/src/main/resources/dev/jasper/app/themeimport/intellij/expUI/expUI_light_with_light_header.theme.json` | `jasper-ui/src/main/resources/dev/jasper/ui/themeimport/intellij/expUI/expUI_light_with_light_header.theme.json` | Task 3 | Redirect import readers; remove duplicate app asset. |
| `jasper-app/src/main/resources/dev/jasper/app/themeimport/intellij/intellijlaf.theme.json` | `jasper-ui/src/main/resources/dev/jasper/ui/themeimport/intellij/intellijlaf.theme.json` | Task 3 | Redirect import readers; remove duplicate app asset. |
| `jasper-app/src/main/resources/dev/jasper/app/themeimport/intellij/islands/HighContrast.theme.json` | `jasper-ui/src/main/resources/dev/jasper/ui/themeimport/intellij/islands/HighContrast.theme.json` | Task 3 | Redirect import readers; remove duplicate app asset. |
| `jasper-app/src/main/resources/dev/jasper/app/themeimport/intellij/islands/IslandSchemeDark.xml` | `jasper-ui/src/main/resources/dev/jasper/ui/themeimport/intellij/islands/IslandSchemeDark.xml` | Task 3 | Redirect import readers; remove duplicate app asset. |
| `jasper-app/src/main/resources/dev/jasper/app/themeimport/intellij/islands/ManyIslandsDarcula.theme.json` | `jasper-ui/src/main/resources/dev/jasper/ui/themeimport/intellij/islands/ManyIslandsDarcula.theme.json` | Task 3 | Redirect import readers; remove duplicate app asset. |
| `jasper-app/src/main/resources/dev/jasper/app/themeimport/intellij/islands/ManyIslandsDark.theme.json` | `jasper-ui/src/main/resources/dev/jasper/ui/themeimport/intellij/islands/ManyIslandsDark.theme.json` | Task 3 | Redirect import readers; remove duplicate app asset. |
| `jasper-app/src/main/resources/dev/jasper/app/themeimport/intellij/islands/ManyIslandsLight.theme.json` | `jasper-ui/src/main/resources/dev/jasper/ui/themeimport/intellij/islands/ManyIslandsLight.theme.json` | Task 3 | Redirect import readers; remove duplicate app asset. |
| `jasper-app/src/main/resources/dev/jasper/app/themeimport/intellij/islands/highContrastScheme.xml` | `jasper-ui/src/main/resources/dev/jasper/ui/themeimport/intellij/islands/highContrastScheme.xml` | Task 3 | Redirect import readers; remove duplicate app asset. |
| `jasper-app/src/main/resources/dev/jasper/app/themes/clear-dark.toml` | `jasper-ui/src/main/resources/dev/jasper/ui/themeimport/native/clear-dark.toml` | Tasks 2/3 | Redirect catalog/import readers; remove duplicate native input after parity checks. |
| `jasper-app/src/main/resources/dev/jasper/app/themes/clear-light.toml` | `jasper-ui/src/main/resources/dev/jasper/ui/themeimport/native/clear-light.toml` | Tasks 2/3 | Redirect catalog/import readers; remove duplicate native input after parity checks. |
| `jasper-app/src/main/resources/dev/jasper/app/themes/index.txt` | `jasper-ui/src/main/resources/dev/jasper/ui/themeimport/native/index.txt` | Tasks 2/3 | Redirect catalog/import readers; remove duplicate native input after parity checks. |
| `jasper-app/src/main/resources/dev/jasper/app/themes/jasper-dark.toml` | `jasper-ui/src/main/resources/dev/jasper/ui/themeimport/native/jasper-dark.toml` | Tasks 2/3 | Redirect catalog/import readers; remove duplicate native input after parity checks. |
| `jasper-app/src/main/resources/dev/jasper/app/themes/jasper-light.toml` | `jasper-ui/src/main/resources/dev/jasper/ui/themeimport/native/jasper-light.toml` | Tasks 2/3 | Redirect catalog/import readers; remove duplicate native input after parity checks. |
| `jasper-app/src/main/resources/dev/jasper/app/themes/nord-nord.toml` | `jasper-ui/src/main/resources/dev/jasper/ui/themeimport/native/nord-nord.toml` | Tasks 2/3 | Redirect catalog/import readers; remove duplicate native input after parity checks. |
| `jasper-app/src/main/resources/dev/jasper/app/themes/THIRD-PARTY-THEMES.md` | App provenance/legacy-name policy (not a module copy) | Tasks 2/3 | Retain or relocate with catalog assets; preserve licensing and migration alias semantics. |
| `jasper-app/src/main/resources/dev/jasper/app/themes/legacy-aliases.json` | App provenance/legacy-name policy (not a module copy) | Tasks 2/3 | Retain or relocate with catalog assets; preserve licensing and migration alias semantics. |
| `jasper-app/src/main/resources/dev/jasper/app/themes/licenses/nord/license` | App provenance/legacy-name policy (not a module copy) | Tasks 2/3 | Retain or relocate with catalog assets; preserve licensing and migration alias semantics. |
