# Plan 1 temporary theme paths and Plan 2 removal ownership

Audited 2026-10-04 after Tasks 1–13; updated for integrated Plan 2 Tasks 2–3 on 2026-10-05. This is the exact-path extraction/removal ledger linked by
[Plan 2](../../superpowers/plans/2026-10-04-theme-engine-2-consumer-migration.md); it does not claim
consumer migration. The designated reusable algorithm/value authority is `jasper-ui`'s `theme`,
`themeio` and `themeimport` packages. Plan 2 Tasks 2–3 removed the app algorithm/value copies below; app and hosted kit paths
now use module generation/import contracts. Remaining legacy UI consumes temporary defaults adapters. Do not maintain the old algorithms as a second permanent
engine. Plan 2 Tasks 2/3 redirect consumers before deleting copies, preserving selection, import
limits, filesystem locks/fingerprints and saved schema. Plan 2 Task 12 verifies no competing engine.

The initial basename inventory contained 28 pairs. One pair is unrelated in meaning:
`app.config.TerminalColors` stores a saved choice, whereas `ui.theme.TerminalColors` stores 20 actual
colors. The saved-choice record remains app configuration; the other 27 app paths below are temporary.

| Exact app path | Module authority | Removal/adaptation owner | Disposition |
|---|---|---|---|
| `jasper-app/src/main/java/dev/jasper/app/appearance/GeneratedTheme.java` | `jasper-ui/src/main/java/dev/jasper/ui/theme/GeneratedTheme.java` | Plan 2 Task2 | Removed in integrated Plan 2 Tasks 2–3; module authority retained; existing source/resource bytes preserved. |
| `jasper-app/src/main/java/dev/jasper/app/appearance/GlassLevels.java` | `jasper-ui/src/main/java/dev/jasper/ui/theme/GlassLevels.java` | Plan 2 Task2 | Removed in integrated Plan 2 Tasks 2–3; module authority retained; existing source/resource bytes preserved. |
| `jasper-app/src/main/java/dev/jasper/app/appearance/Seed.java` | `jasper-ui/src/main/java/dev/jasper/ui/theme/Seed.java` | Plan 2 Task2 | Removed in integrated Plan 2 Tasks 2–3; module authority retained; existing source/resource bytes preserved. |
| `jasper-app/src/main/java/dev/jasper/app/appearance/SyntaxRole.java` | `jasper-ui/src/main/java/dev/jasper/ui/theme/SyntaxRole.java` | Plan 2 Task2 | Removed in integrated Plan 2 Tasks 2–3; module authority retained; existing source/resource bytes preserved. |
| `jasper-app/src/main/java/dev/jasper/app/appearance/SyntaxStyle.java` | `jasper-ui/src/main/java/dev/jasper/ui/theme/SyntaxStyle.java` | Plan 2 Task2 | Removed in integrated Plan 2 Tasks 2–3; module authority retained; existing source/resource bytes preserved. |
| `jasper-app/src/main/java/dev/jasper/app/config/TerminalColors.java` | `jasper-ui/src/main/java/dev/jasper/ui/theme/TerminalColors.java` | Plan 2 Task2 | Retain saved-choice record; adapt palette resolution. This is a basename collision, not an extracted color-value copy. |
| `jasper-app/src/main/java/dev/jasper/app/appearance/ThemeDocument.java` | `jasper-ui/src/main/java/dev/jasper/ui/theme/ThemeDocument.java` | Plan 2 Task2 | Removed in integrated Plan 2 Tasks 2–3; module authority retained; existing source/resource bytes preserved. |
| `jasper-app/src/main/java/dev/jasper/app/appearance/ThemeFileException.java` | `jasper-ui/src/main/java/dev/jasper/ui/theme/ThemeFileException.java` | Plan 2 Task2 | Removed in integrated Plan 2 Tasks 2–3; module authority retained; existing source/resource bytes preserved. |
| `jasper-app/src/main/java/dev/jasper/app/appearance/ThemeGenerator.java` | `jasper-ui/src/main/java/dev/jasper/ui/theme/ThemeGenerator.java` | Plan 2 Task2 | Removed in integrated Plan 2 Tasks 2–3; module authority retained; existing source/resource bytes preserved. |
| `jasper-app/src/main/java/dev/jasper/app/appearance/ThemeResolver.java` | `jasper-ui/src/main/java/dev/jasper/ui/theme/ThemeResolver.java` | Plan 2 Task2 | Removed in integrated Plan 2 Tasks 2–3; module authority retained; existing source/resource bytes preserved. |
| `jasper-app/src/main/java/dev/jasper/app/appearance/Token.java` | `jasper-ui/src/main/java/dev/jasper/ui/theme/Token.java` | Plan 2 Task2 | Removed in integrated Plan 2 Tasks 2–3; module authority retained; existing source/resource bytes preserved. |
| `jasper-app/src/main/java/dev/jasper/app/appearance/UiTemplate.java` | `jasper-ui/src/main/java/dev/jasper/ui/theme/UiTemplate.java` | Plan 2 Task2 | Removed in integrated Plan 2 Tasks 2–3; module authority retained; existing source/resource bytes preserved. |
| `jasper-app/src/main/java/dev/jasper/app/appearance/WorkspaceGlass.java` | `jasper-ui/src/main/java/dev/jasper/ui/theme/WorkspaceGlass.java` | Plan 2 Task2 | Removed in integrated Plan 2 Tasks 2–3; module authority retained; existing source/resource bytes preserved. |
| `jasper-app/src/main/java/dev/jasper/app/appearance/ThemeToml.java` | `jasper-ui/src/main/java/dev/jasper/ui/themeio/ThemeToml.java` | Plan 2 Task2 | Removed in integrated Plan 2 Tasks 2–3; module authority retained; existing source/resource bytes preserved. |
| `jasper-app/src/main/java/dev/jasper/app/appearance/ThemeTomlWriter.java` | `jasper-ui/src/main/java/dev/jasper/ui/themeio/ThemeTomlWriter.java` | Plan 2 Task2 | Removed in integrated Plan 2 Tasks 2–3; module authority retained; existing source/resource bytes preserved. |
| `jasper-app/src/main/java/dev/jasper/app/themeimport/AlacrittyConversion.java` | `jasper-ui/src/main/java/dev/jasper/ui/themeimport/AlacrittyConversion.java` | Plan 2 Task3 | Removed in integrated Plan 2 Tasks 2–3; module authority retained; existing source/resource bytes preserved. |
| `jasper-app/src/main/java/dev/jasper/app/themeimport/AlacrittyTheme.java` | `jasper-ui/src/main/java/dev/jasper/ui/themeimport/AlacrittyTheme.java` | Plan 2 Task3 | Removed in integrated Plan 2 Tasks 2–3; module authority retained; existing source/resource bytes preserved. |
| `jasper-app/src/main/java/dev/jasper/app/themeimport/ConversionResult.java` | `jasper-ui/src/main/java/dev/jasper/ui/themeimport/ConversionResult.java` | Plan 2 Task3 | Removed in integrated Plan 2 Tasks 2–3; module authority retained; existing source/resource bytes preserved. |
| `jasper-app/src/main/java/dev/jasper/app/themeimport/EditorScheme.java` | `jasper-ui/src/main/java/dev/jasper/ui/themeimport/EditorScheme.java` | Plan 2 Task3 | Removed in integrated Plan 2 Tasks 2–3; module authority retained; existing source/resource bytes preserved. |
| `jasper-app/src/main/java/dev/jasper/app/appearance/FlatLafThemeData.java` | `jasper-ui/src/main/java/dev/jasper/ui/themeimport/FlatLafThemeData.java` | Plan 2 Task2 | Removed in integrated Plan 2 Tasks 2–3; module authority retained; existing source/resource bytes preserved. |
| `jasper-app/src/main/java/dev/jasper/app/themeimport/IntellijConversion.java` | `jasper-ui/src/main/java/dev/jasper/ui/themeimport/IntellijConversion.java` | Plan 2 Task3 | Removed in integrated Plan 2 Tasks 2–3; module authority retained; existing source/resource bytes preserved. |
| `jasper-app/src/main/java/dev/jasper/app/themeimport/SafeXml.java` | `jasper-ui/src/main/java/dev/jasper/ui/themeimport/SafeXml.java` | Plan 2 Task3 | Removed in integrated Plan 2 Tasks 2–3; module authority retained; existing source/resource bytes preserved. |
| `jasper-app/src/main/java/dev/jasper/app/themeimport/ThemeArchive.java` | `jasper-ui/src/main/java/dev/jasper/ui/themeimport/ThemeArchive.java` | Plan 2 Task3 | Removed in integrated Plan 2 Tasks 2–3; module authority retained; existing source/resource bytes preserved. |
| `jasper-app/src/main/java/dev/jasper/app/themeimport/ThemeConverter.java` | `jasper-ui/src/main/java/dev/jasper/ui/themeimport/ThemeConverter.java` | Plan 2 Task3 | Removed in integrated Plan 2 Tasks 2–3; module authority retained; existing source/resource bytes preserved. |
| `jasper-app/src/main/java/dev/jasper/app/themeimport/ThemeLoader.java` | `jasper-ui/src/main/java/dev/jasper/ui/themeimport/ThemeLoader.java` | Plan 2 Task3 | Removed in integrated Plan 2 Tasks 2–3; module authority retained; existing source/resource bytes preserved. |
| `jasper-app/src/main/java/dev/jasper/app/themeimport/ThemeParents.java` | `jasper-ui/src/main/java/dev/jasper/ui/themeimport/ThemeParents.java` | Plan 2 Task3 | Removed in integrated Plan 2 Tasks 2–3; module authority retained; existing source/resource bytes preserved. |
| `jasper-app/src/main/java/dev/jasper/app/themeimport/ThemeSources.java` | `jasper-ui/src/main/java/dev/jasper/ui/themeimport/ThemeSources.java` | Plan 2 Task3 | Removed in integrated Plan 2 Tasks 2–3; module authority retained; existing source/resource bytes preserved. |
| `jasper-app/src/main/java/dev/jasper/app/appearance/WorkspaceTokenKeys.java` | `jasper-ui/src/main/java/dev/jasper/ui/themeimport/WorkspaceTokenKeys.java` | Plan 2 Task2 | Removed in integrated Plan 2 Tasks 2–3; module authority retained; existing source/resource bytes preserved. |

Differently named equivalents and installation bridges have these dispositions. App policy/lifetime
owners remain app code, but lose any copied color derivation/generation when their consumers migrate.

| Exact app path | Module authority or responsibility | Plan 2 owner | Disposition |
|---|---|---|---|
| `jasper-app/src/main/java/dev/jasper/app/plugins/KitThemeBridge.java` | `theme.ThemePair`, module generator/semantic values | Task 2, adapter cleanup verified in Task 4 | Removed in integrated Plan 2 Tasks 2–3; module authority retained; existing source/resource bytes preserved. |
| `jasper-app/src/main/java/dev/jasper/app/appearance/ChromeKeys.java` | `theme.UiColorKeys.chromeAliases()`, `theme.SemanticDefaults`, `theme.ColorMath`, kit installer | Task 5 | Removed; notification-only module-value publication has the exact Task 6/11 ownership below. |
| `jasper-app/src/main/java/dev/jasper/app/appearance/WorkspaceValues.java` | module generation/semantic values and `swing.FlatLafInstaller` | Task 2 | Removed in integrated Plan 2 Tasks 2–3; module authority retained; existing source/resource bytes preserved. |
| `jasper-app/src/main/java/dev/jasper/app/appearance/WorkspaceDefaults.java` | module semantic publication and app adapters | Tasks 7–10 / 11 | Retained exact module-value publication for dynamically constructed SDK reader keys; superseding Task 5 audit below. |
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
| `jasper-app/src/main/resources/dev/jasper/app/themeimport/intellij/DefaultColorSchemesManager.xml` | `jasper-ui/src/main/resources/dev/jasper/ui/themeimport/intellij/DefaultColorSchemesManager.xml` | Task 3 | Removed in integrated Plan 2 Tasks 2–3; module authority retained; existing source/resource bytes preserved. |
| `jasper-app/src/main/resources/dev/jasper/app/themeimport/intellij/LICENSE.txt` | `jasper-ui/src/main/resources/dev/jasper/ui/themeimport/intellij/LICENSE.txt` | Task 3 | Removed in integrated Plan 2 Tasks 2–3; module authority retained; existing source/resource bytes preserved. |
| `jasper-app/src/main/resources/dev/jasper/app/themeimport/intellij/Light.theme.json` | `jasper-ui/src/main/resources/dev/jasper/ui/themeimport/intellij/Light.theme.json` | Task 3 | Removed in integrated Plan 2 Tasks 2–3; module authority retained; existing source/resource bytes preserved. |
| `jasper-app/src/main/resources/dev/jasper/app/themeimport/intellij/Light.xml` | `jasper-ui/src/main/resources/dev/jasper/ui/themeimport/intellij/Light.xml` | Task 3 | Removed in integrated Plan 2 Tasks 2–3; module authority retained; existing source/resource bytes preserved. |
| `jasper-app/src/main/resources/dev/jasper/app/themeimport/intellij/NOTICE.txt` | `jasper-ui/src/main/resources/dev/jasper/ui/themeimport/intellij/NOTICE.txt` | Task 3 | Removed in integrated Plan 2 Tasks 2–3; module authority retained; existing source/resource bytes preserved. |
| `jasper-app/src/main/resources/dev/jasper/app/themeimport/intellij/SOURCE.md` | `jasper-ui/src/main/resources/dev/jasper/ui/themeimport/intellij/SOURCE.md` | Task 3 | Removed in integrated Plan 2 Tasks 2–3; module authority retained; existing source/resource bytes preserved. |
| `jasper-app/src/main/resources/dev/jasper/app/themeimport/intellij/assets.tsv` | `jasper-ui/src/main/resources/dev/jasper/ui/themeimport/intellij/assets.tsv` | Task 3 | Removed in integrated Plan 2 Tasks 2–3; module authority retained; existing source/resource bytes preserved. |
| `jasper-app/src/main/resources/dev/jasper/app/themeimport/intellij/darcula.theme.json` | `jasper-ui/src/main/resources/dev/jasper/ui/themeimport/intellij/darcula.theme.json` | Task 3 | Removed in integrated Plan 2 Tasks 2–3; module authority retained; existing source/resource bytes preserved. |
| `jasper-app/src/main/resources/dev/jasper/app/themeimport/intellij/expUI/expUI_darculaContrastScheme.xml` | `jasper-ui/src/main/resources/dev/jasper/ui/themeimport/intellij/expUI/expUI_darculaContrastScheme.xml` | Task 3 | Removed in integrated Plan 2 Tasks 2–3; module authority retained; existing source/resource bytes preserved. |
| `jasper-app/src/main/resources/dev/jasper/app/themeimport/intellij/expUI/expUI_dark.theme.json` | `jasper-ui/src/main/resources/dev/jasper/ui/themeimport/intellij/expUI/expUI_dark.theme.json` | Task 3 | Removed in integrated Plan 2 Tasks 2–3; module authority retained; existing source/resource bytes preserved. |
| `jasper-app/src/main/resources/dev/jasper/app/themeimport/intellij/expUI/expUI_darkScheme.xml` | `jasper-ui/src/main/resources/dev/jasper/ui/themeimport/intellij/expUI/expUI_darkScheme.xml` | Task 3 | Removed in integrated Plan 2 Tasks 2–3; module authority retained; existing source/resource bytes preserved. |
| `jasper-app/src/main/resources/dev/jasper/app/themeimport/intellij/expUI/expUI_light.theme.json` | `jasper-ui/src/main/resources/dev/jasper/ui/themeimport/intellij/expUI/expUI_light.theme.json` | Task 3 | Removed in integrated Plan 2 Tasks 2–3; module authority retained; existing source/resource bytes preserved. |
| `jasper-app/src/main/resources/dev/jasper/app/themeimport/intellij/expUI/expUI_lightScheme.xml` | `jasper-ui/src/main/resources/dev/jasper/ui/themeimport/intellij/expUI/expUI_lightScheme.xml` | Task 3 | Removed in integrated Plan 2 Tasks 2–3; module authority retained; existing source/resource bytes preserved. |
| `jasper-app/src/main/resources/dev/jasper/app/themeimport/intellij/expUI/expUI_light_with_light_header.theme.json` | `jasper-ui/src/main/resources/dev/jasper/ui/themeimport/intellij/expUI/expUI_light_with_light_header.theme.json` | Task 3 | Removed in integrated Plan 2 Tasks 2–3; module authority retained; existing source/resource bytes preserved. |
| `jasper-app/src/main/resources/dev/jasper/app/themeimport/intellij/intellijlaf.theme.json` | `jasper-ui/src/main/resources/dev/jasper/ui/themeimport/intellij/intellijlaf.theme.json` | Task 3 | Removed in integrated Plan 2 Tasks 2–3; module authority retained; existing source/resource bytes preserved. |
| `jasper-app/src/main/resources/dev/jasper/app/themeimport/intellij/islands/HighContrast.theme.json` | `jasper-ui/src/main/resources/dev/jasper/ui/themeimport/intellij/islands/HighContrast.theme.json` | Task 3 | Removed in integrated Plan 2 Tasks 2–3; module authority retained; existing source/resource bytes preserved. |
| `jasper-app/src/main/resources/dev/jasper/app/themeimport/intellij/islands/IslandSchemeDark.xml` | `jasper-ui/src/main/resources/dev/jasper/ui/themeimport/intellij/islands/IslandSchemeDark.xml` | Task 3 | Removed in integrated Plan 2 Tasks 2–3; module authority retained; existing source/resource bytes preserved. |
| `jasper-app/src/main/resources/dev/jasper/app/themeimport/intellij/islands/ManyIslandsDarcula.theme.json` | `jasper-ui/src/main/resources/dev/jasper/ui/themeimport/intellij/islands/ManyIslandsDarcula.theme.json` | Task 3 | Removed in integrated Plan 2 Tasks 2–3; module authority retained; existing source/resource bytes preserved. |
| `jasper-app/src/main/resources/dev/jasper/app/themeimport/intellij/islands/ManyIslandsDark.theme.json` | `jasper-ui/src/main/resources/dev/jasper/ui/themeimport/intellij/islands/ManyIslandsDark.theme.json` | Task 3 | Removed in integrated Plan 2 Tasks 2–3; module authority retained; existing source/resource bytes preserved. |
| `jasper-app/src/main/resources/dev/jasper/app/themeimport/intellij/islands/ManyIslandsLight.theme.json` | `jasper-ui/src/main/resources/dev/jasper/ui/themeimport/intellij/islands/ManyIslandsLight.theme.json` | Task 3 | Removed in integrated Plan 2 Tasks 2–3; module authority retained; existing source/resource bytes preserved. |
| `jasper-app/src/main/resources/dev/jasper/app/themeimport/intellij/islands/highContrastScheme.xml` | `jasper-ui/src/main/resources/dev/jasper/ui/themeimport/intellij/islands/highContrastScheme.xml` | Task 3 | Removed in integrated Plan 2 Tasks 2–3; module authority retained; existing source/resource bytes preserved. |
| `jasper-app/src/main/resources/dev/jasper/app/themes/clear-dark.toml` | `jasper-ui/src/main/resources/dev/jasper/ui/themeimport/native/clear-dark.toml` | Tasks 2/3 | Removed in integrated Plan 2 Tasks 2–3; module authority retained; existing source/resource bytes preserved. |
| `jasper-app/src/main/resources/dev/jasper/app/themes/clear-light.toml` | `jasper-ui/src/main/resources/dev/jasper/ui/themeimport/native/clear-light.toml` | Tasks 2/3 | Removed in integrated Plan 2 Tasks 2–3; module authority retained; existing source/resource bytes preserved. |
| `jasper-app/src/main/resources/dev/jasper/app/themes/index.txt` | `jasper-ui/src/main/resources/dev/jasper/ui/themeimport/native/index.txt` | Tasks 2/3 | Removed in integrated Plan 2 Tasks 2–3; module authority retained; existing source/resource bytes preserved. |
| `jasper-app/src/main/resources/dev/jasper/app/themes/jasper-dark.toml` | `jasper-ui/src/main/resources/dev/jasper/ui/themeimport/native/jasper-dark.toml` | Tasks 2/3 | Removed in integrated Plan 2 Tasks 2–3; module authority retained; existing source/resource bytes preserved. |
| `jasper-app/src/main/resources/dev/jasper/app/themes/jasper-light.toml` | `jasper-ui/src/main/resources/dev/jasper/ui/themeimport/native/jasper-light.toml` | Tasks 2/3 | Removed in integrated Plan 2 Tasks 2–3; module authority retained; existing source/resource bytes preserved. |
| `jasper-app/src/main/resources/dev/jasper/app/themes/nord-nord.toml` | `jasper-ui/src/main/resources/dev/jasper/ui/themeimport/native/nord-nord.toml` | Tasks 2/3 | Removed in integrated Plan 2 Tasks 2–3; module authority retained; existing source/resource bytes preserved. |
| `jasper-app/src/main/resources/dev/jasper/app/themes/THIRD-PARTY-THEMES.md` | App provenance/legacy-name policy (not a module copy) | Tasks 2/3 | Removed in integrated Plan 2 Tasks 2–3; module authority retained; existing source/resource bytes preserved. |
| `jasper-app/src/main/resources/dev/jasper/app/themes/legacy-aliases.json` | App provenance/legacy-name policy (not a module copy) | Tasks 2/3 | Retain or relocate with catalog assets; preserve licensing and migration alias semantics. |
| `jasper-app/src/main/resources/dev/jasper/app/themes/licenses/nord/license` | App provenance/legacy-name policy (not a module copy) | Tasks 2/3 | Removed in integrated Plan 2 Tasks 2–3; module authority retained; existing source/resource bytes preserved. |


## Required host-policy wiring before production cutover

Completed by Plan 2 Task 4: `ConfigurationController` updates `ThemeController` from
`ui.animations.enabled`; existing app-native `AppearanceBindings` contexts read the live controller
supplier. `JasperApplication` installs that supplier through `PluginRuntime.animations` before plugin
startup; `PluginHost` passes it to `HostedKit`. Existing switch effects settle on their next frame,
future effects stay disabled, and enabling resumes motion without view replacement. Owner-package
headless probes cover native and hosted contexts; configuration reload is covered end to end.

## Task 5 superseding adapter ownership

The original Tasks 2–3 Task 5 removal estimate is superseded by the controller-approved sequencing below.
Task 5 removes production `appearance/ChromeKeys.java` and its derivation. The only former derived
aliases still published are the five notification colors below, copied from the installed module snapshot.
No production ChromeKeys/derive calls or native Task 5 `WorkspaceTheme.color` callers remain.
`workspace/WorkspaceTheme` still publishes explicit route properties; its unused raw `color/resolvedColor`
compatibility lookup is removed with the legacy bridge in Task 11, not used as a native-role bypass.

| Exact retained path / keys | Actual remaining consumers | Migration / final removal owner |
|---|---|---|
| `appearance/LegacyAppearanceDefaults.java`: module `UiTokenKeys.KEYS` publication (48 exact keys in the module map), authored native `[ui]` last-word raw colors | Task 6 app dialogs/settings/notifications and raw controls in bundled Tasks 7–10 plugins | Migrate controls in Tasks 6–10; remove adapter and legacy publication in Task 11. No generation, parsing or app color derivation. |
| Same adapter: `Jasper.notification.{background,borderColor,foreground,mutedForeground,linkForeground}` → `SURFACE_CARD,BORDER_SUBTLE,TEXT_PRIMARY,TEXT_SECONDARY,TEXT_LINK` | App notification rendering | Task 6 migrates; Task 11 removes adapter. Explicit authored key wins for that install only. |
| Same adapter: `Jasper.editor{Background,Foreground,Caret,Selection,CurrentLine,Gutter,LineNumbers}` in module UiTokenKeys | SDK EditorPalette fallback / Editor | Task 9 migrates Editor; Task 11 removes aliases/legacy current(). |
| Same adapter: `ListUI=AppListUI`; `{List,Tree,Table,ComboBox,MenuItem}.selection{Arc,Insets}`; `{Label,List,TextField,PasswordField,FormattedTextField,TextArea,ComboBox,Button}.font`; `List.cellMargins`, `Table.cellMargins`, `SplitPane.dividerSize`, `SplitPaneDivider.border`, `TitlePane.centerTitle` | Legacy raw controls until their assigned migration, plus native window delegate plumbing | Tasks 6–10 remove raw readers; Task 11 removes adapter and verifies module/native delegate defaults. Kit controls and native adapters take explicit module roles. |
| `appearance/WorkspaceDefaults.java`: `Jasper.workspace.{background,surfaceRaised,surfaceSunken,text,textMuted,border,selection,selectionText,hover,accent,success,warning,error}`; `editor{Background,Foreground,Caret,Selection,CurrentLine,Gutter,LineNumbers}`; `dark`; `syntax.<role>.{color,bold,italic}` for every module SyntaxRole | SDK `ui/WorkspacePalette.current`, `EditorPalette.current`, `SyntaxPalette.current`, default `Appearance` methods, app `plugins/HostedUi` absent-snapshot fallback | Tasks 7–10 migrate dynamic consumers; Task 11 removes current() compatibility and publication together. Existing publisher only copies current GeneratedTheme values, with no resolver or cache. |
| `workspace/{WorkspaceStyler,WorkspaceStyles,WorkspaceStyleLayer,WorkspaceComboPopup}.java`, `ToolWindowSurface` raw recursion | `WindowContent.hostedStylers` for raw contributed components only | Tasks 7–10 migrate contributions; Task 11 deletes raw adapter machinery. Traversal stops at KitOwnership roots, including mixed trees. |

Exact UiTokenKeys residual publication (no derivation):

| Keys | Consumer migration / publication removal |
|---|---|
| `Panel.background` | App Task 6 / plugin Tasks 7–10 raw controls; Task 11 publication removal. |
| `Label.foreground` | App Task 6 / plugin Tasks 7–10 raw controls; Task 11 publication removal. |
| `Jasper.mutedForeground` | App Task 6 / plugin Tasks 7–10 raw controls; Task 11 publication removal. |
| `Label.disabledForeground` | App Task 6 / plugin Tasks 7–10 raw controls; Task 11 publication removal. |
| `Jasper.titleBackground` | No Task 5 native reader remains; remove compatibility publication in Task 11. |
| `StatusBar.background` | App Task 6 / plugin Tasks 7–10 raw controls; Task 11 publication removal. |
| `ToolWindow.background` | App Task 6 / plugin Tasks 7–10 raw controls; Task 11 publication removal. |
| `PopupMenu.background` | App Task 6 / plugin Tasks 7–10 raw controls; Task 11 publication removal. |
| `List.background` | App Task 6 / plugin Tasks 7–10 raw controls; Task 11 publication removal. |
| `TextField.background` | App Task 6 / plugin Tasks 7–10 raw controls; Task 11 publication removal. |
| `Component.borderColor` | App Task 6 / plugin Tasks 7–10 raw controls; Task 11 publication removal. |
| `Separator.foreground` | App Task 6 / plugin Tasks 7–10 raw controls; Task 11 publication removal. |
| `Jasper.splitDivider` | No Task 5 native reader remains; remove compatibility publication in Task 11. |
| `Component.focusColor` | App Task 6 / plugin Tasks 7–10 raw controls; Task 11 publication removal. |
| `List.selectionBackground` | App Task 6 / plugin Tasks 7–10 raw controls; Task 11 publication removal. |
| `List.selectionForeground` | App Task 6 / plugin Tasks 7–10 raw controls; Task 11 publication removal. |
| `List.selectionInactiveBackground` | App Task 6 / plugin Tasks 7–10 raw controls; Task 11 publication removal. |
| `TextField.selectionBackground` | App Task 6 / plugin Tasks 7–10 raw controls; Task 11 publication removal. |
| `TextField.selectionForeground` | App Task 6 / plugin Tasks 7–10 raw controls; Task 11 publication removal. |
| `TabbedPane.background` | App Task 6 / plugin Tasks 7–10 raw controls; Task 11 publication removal. |
| `Jasper.tabSelectedBackground` | No Task 5 native reader remains; remove compatibility publication in Task 11. |
| `Jasper.tabSelectedForeground` | No Task 5 native reader remains; remove compatibility publication in Task 11. |
| `Jasper.tabSelectedBorder` | No Task 5 native reader remains; remove compatibility publication in Task 11. |
| `Jasper.tabHoverBackground` | No Task 5 native reader remains; remove compatibility publication in Task 11. |
| `Jasper.tabUnderline` | No Task 5 native reader remains; remove compatibility publication in Task 11. |
| `Jasper.tabUnderlineInactive` | No Task 5 native reader remains; remove compatibility publication in Task 11. |
| `Button.background` | App Task 6 / plugin Tasks 7–10 raw controls; Task 11 publication removal. |
| `Button.borderColor` | App Task 6 / plugin Tasks 7–10 raw controls; Task 11 publication removal. |
| `Button.foreground` | App Task 6 / plugin Tasks 7–10 raw controls; Task 11 publication removal. |
| `Button.default.background` | App Task 6 / plugin Tasks 7–10 raw controls; Task 11 publication removal. |
| `Button.default.foreground` | App Task 6 / plugin Tasks 7–10 raw controls; Task 11 publication removal. |
| `Button.hoverBackground` | App Task 6 / plugin Tasks 7–10 raw controls; Task 11 publication removal. |
| `Button.pressedBackground` | App Task 6 / plugin Tasks 7–10 raw controls; Task 11 publication removal. |
| `ScrollBar.thumb` | App Task 6 / plugin Tasks 7–10 raw controls; Task 11 publication removal. |
| `ScrollBar.track` | App Task 6 / plugin Tasks 7–10 raw controls; Task 11 publication removal. |
| `Component.accentColor` | App Task 6 / plugin Tasks 7–10 raw controls; Task 11 publication removal. |
| `Component.linkColor` | App Task 6 / plugin Tasks 7–10 raw controls; Task 11 publication removal. |
| `Jasper.configSuccessForeground` | App Task 6 / plugin Tasks 7–10 raw controls; Task 11 publication removal. |
| `Jasper.configWarningForeground` | App Task 6 / plugin Tasks 7–10 raw controls; Task 11 publication removal. |
| `Jasper.configErrorForeground` | App Task 6 / plugin Tasks 7–10 raw controls; Task 11 publication removal. |
| `Jasper.findErrorBackground` | No Task 5 native reader remains; remove compatibility publication in Task 11. |
| `Jasper.editorBackground` | Editor Task 9; Task 11 removal. |
| `Jasper.editorForeground` | Editor Task 9; Task 11 removal. |
| `Jasper.editorCaret` | Editor Task 9; Task 11 removal. |
| `Jasper.editorSelection` | Editor Task 9; Task 11 removal. |
| `Jasper.editorCurrentLine` | Editor Task 9; Task 11 removal. |
| `Jasper.editorGutter` | Editor Task 9; Task 11 removal. |
| `Jasper.editorLineNumbers` | Editor Task 9; Task 11 removal. |

## Task 6a consumer removal (settingsui, restart, themesui)

Plan 2 Task 6a moved the Settings dialog shell controls, schema/group pages and theme-import dialogs to module kit
views on the app's chrome `KitContext` (`ThemeController.appearance().context(ThemeRoute.CHROME)` through
`SettingsDialog.Hooks.kit` and `PageContext.kit`). These families no longer read any `LegacyAppearanceDefaults` key:
the former readers of `Panel.background`, `Label.foreground`, `Label.disabledForeground`, `Component.linkColor`,
`Separator.foreground`, `Jasper.configErrorForeground`, the `Tree.*`/`List.*`/`ComboBox.*` selection and popup keys,
`Banner.warningBackground`, `SearchMatch.startBackground` and `TextComponent.selectionBackgroundInactive`
(`SettingsView`, `PageTree`, `GroupPage`, `SearchCombo`, `HighlightBox`, `TitledSeparator`, `WrappingText`,
`ThemeImportView`, `ThemeDialogs`) were deleted or rewritten. The `restart` rows are nonvisual. The keys stay published
for their other consumers (Tasks 6b/6c app pages and dialogs, Tasks 7–10 plugins) until Task 11 removes the adapter.
Remaining raw Swing in these families is layout only (`SettingsView` split/scroller, `PageBody`, `FillingPage`);
fix round 1 replaced `SettingsDialog.askDiscard`'s `JOptionPane` with a modal kit SHEET over Settings.

The dynamic WorkspacePalette callers are exactly:
- Task 7: `plugins/vault/.../ui/ManagerPanel.java`.
- Task 8: `plugins/remote/.../ui/HostsPanel.java`, `ui/tunnels/TunnelsPanel.java`, `ui/transfers/TransferStrip.java`.
- Task 9: `plugins/editor/.../ui/EditorTab.java`, `vim/ui/VimBar.java`; editor/syntax snapshots also serve Editor through Appearance.
- Task 10: `plugins/fileserver/.../ui/FileServersPanel.java`.

Correction to the initial Task 5 audit: a literal-key search appeared to show zero WorkspaceDefaults readers;
the SDK constructs these keys dynamically. Independent chrome/workspace selection tests through each
actual SDK reader demonstrated the dependency. Publication is retained until its consumers migrate.
No new SDK snapshot bridge is introduced. `LegacyInstallFixture` was a Task 2 test helper, not a frozen
Task 1 artifact. Its only caller now tests the real module install path (skipped authored keys, alpha,
explicit precedence and same-LAF reinstall), so the unused fixture and obsolete derivation are removed.
The 572-file before checksum manifest and all 408 before renders remain unchanged.

Native standard-control/layout adapters retained for **Plan 3 Task 10** composition are the exact Task 5
standard-ui rows in `migration.csv`: WindowChrome/WindowHeader/WindowContent/WindowToolbar/WindowStatusBar,
WindowTabs and menu items, ToolStripe/ToolWindowHeader/ToolWindowMenu, FindBar, TitleSearchField,
PathBreadcrumbs, StatusProgressView and TerminalPane notice actions. `NativeControlStyles` owns their
button/field/menu recipes, fonts and padding; `NativeChromePainting` owns native search/command painting.
Native folder tabs, terminal surfaces, docking overlays and glass keep host geometry/input ownership,
using module roles and named metrics. This is implemented Plan 2 styling consumption; it does not
claim Plan 3 composition or screenshot matching. Unchanged coordination-only owners are marked as such.

`AppearanceBindings`, `TerminalPaletteAdapter`, selection/file policy remain app boundaries. Native parents,
licenses and import algorithms stay in jasper-ui. Inventory removed/moved rows retain their authority paths;
checksum and discovery assertions are unchanged apart from recognizing Task 5 removal dispositions.

## Task 6b-1 consumer removal (files chooser, shortcuthelp)

Plan 2 Task 6b-1 moved the file chooser (`files/FileChooserPanel`) and the Keymap settings page
(`shortcuthelp/ShortcutPanel`, `ShortcutTree`, `ShortcutEditPanel`, `KeymapPage`) to module kit views on the chrome
`KitContext` (the chooser's from `NativeShells` via `ThemeController.appearance().context(ThemeRoute.CHROME)`, the
Keymap page's from `PageContext.kit`). These families no longer read any `LegacyAppearanceDefaults` key or FlatLaf
style: the former readers of `TableHeader.font`/`TableHeader.foreground`, `Label.disabledForeground`,
`Label.foreground`, `TextField.background`, `Component.borderColor`, `TextComponent.arc`, `ScrollBar.thumb`,
`Panel.background`, `Tree.selectionForeground` and `Component.linkColor`, the `JButton.buttonType`/`JTextField.*`/
`FlatClientProperties` client properties and `AppIcons.bundled` artwork were rewritten or deleted
(`FileListScrollPane` → kit `ScrollPolicy.OVERLAY`; `WrapLayout` → wrapping kit ACTIONS stacks). Remaining raw Swing in
these families is layout only (BorderLayout/CardLayout shells, the chooser's `JSplitPane` with its unpainted gutter
and `RoundedPane` clipping on `WORKSPACE_SURFACE_ARC`) plus the preview's read-only `JTextArea`, registered as kit
`Custom` EDITOR content and painted from module roles. The keys stay published for their other consumers (6b-2
palette, 6c pluginmanager/notifications, Tasks 7–10 plugins) until Task 11 removes the adapter.

Out-of-family integration: `windows/NativeShells` now passes the chrome context and calls
`FileChooserPanel.installDefaultAction` instead of `setDefaultButton`; its `JOptionPane` replace and new-folder
prompts are Task 6c. `workspace/WindowCommandPalette` (palette focus traversal) is untouched until 6b-2.

## Task 6b-2 consumer removal (palette)

Plan 2 Task 6b-2 moved the command palette card (`palette/CommandPalette`) to module kit views on the chrome
`KitContext` (`ThemeController.appearance().context(ThemeRoute.CHROME)`, passed from `workspace/WindowContent` through
`WindowCommandPalette` and `PaletteController`). The family no longer reads any `LegacyAppearanceDefaults` key or
FlatLaf style: the former readers of `List.foreground`/`List.background`/`List.selectionBackground`/
`List.selectionForeground`/`List.hoverBackground`/`List.selectionArc`/`List.selectionInsets`,
`SearchEverywhere.SearchField.infoForeground`/`.borderColor`, `SearchEverywhere.List.separatorColor`/
`.separatorForeground`, `SearchEverywhere.Advertiser.foreground`, `SearchEverywhere.Tab.selectedBackground`,
`Component.linkColor`, `Component.borderColor`, `Popup.borderColor`, `PopupMenu.borderColor`, `Separator.foreground`,
`Label.foreground`/`Label.disabledForeground`/`Label.font`, `Panel.background`, `Actions.Red`, the
`FlatClientProperties` placeholder/style/leading-icon client properties, `FlatLineBorder` and `FlatUIUtils.paintSelection`
(the JList renderer, the `JTextField` with its `DocumentFilter`, the chip, hint and step `JLabel`s) were rewritten as kit
descriptors. Remaining raw Swing in the family is layout only: the card shell (rounded clip and hairline outline painted
from `border.menu` over `surface.card`, `PALETTE_*`/`RESULTS_*` named metrics with the former values), the fixed-height
query row and hint bar (a `border.subtle` rule), the CardLayout centre and the query-only focus traversal policy. The
keys stay published for their other consumers (6c pluginmanager/notifications, Tasks 7–10 plugins) until Task 11.

Task 11 dependency (ruling P5): SDK `PaletteRow.icon`/`ScopeSpec.icon` are still raw `Optional<Icon>`. The kit palette
shows host glyphs by name (`AppIcons.kitName`) and omits plugin row icons on this branch; the scope chip has no icon
(ruling P9). Task 11 must turn the SDK palette icons into named icons and show them again.

Out-of-family integration: `workspace/WindowCommandPalette` passes the chrome context and focuses the query through
`CommandPalette.focusQuery()`; the card owns its focus cycle (the old query-only policy), so nothing outside the
family reaches the kit input. `workspace/WindowContent` passes `themes.appearance().context(CHROME)` (one line).

## Task 5 review fix round 1 — native geometry, vendor roles and open status-surface item

**Preserved native geometry (no visual change in Plan 2).** Native adapters keep their pre-migration values
under their own `DesignMetrics` names, so changing a kit, icon or dock role cannot move them:
rail button arc `RAIL_BUTTON_ARC` 10; chrome toolbar command hover arc `NATIVE_TOOLBAR_HOVER_ARC` 6 and
disabled content `NATIVE_TOOLBAR_DISABLED_PERCENT` 38; folder-tab scroll step/reveal margin/fade/close width
`TAB_SCROLL_STEP`/`TAB_REVEAL_MARGIN`/`TAB_FADE_WIDTH`/`TAB_CLOSE_WIDTH` 16 and reorder threshold
`TAB_REORDER_THRESHOLD` 5; running dot `STATUS_RUNNING_DOT` 5; progress bar/min height
`STATUS_PROGRESS_BAR_HEIGHT` 8/`STATUS_PROGRESS_MIN_HEIGHT` 24; tool header padding
`TOOL_HEADER_LEADING_PADDING` 10/`TOOL_HEADER_TRAILING_PADDING` 6; rail badge `RAIL_BADGE_DOT` 6; find row
`FIND_GAP`/`FIND_SIDE_PADDING` 6, `FIND_CONTROL_GAP` 4, `FIND_CLOSE_SPACER` 16; title search padding/gap
`TITLE_SEARCH_PADDING` 8/`TITLE_SEARCH_GAP` 6; menu arrow gap `NATIVE_MENU_ARROW_GAP` 3; terminal notice
`NOTICE_VERTICAL_PADDING` 8/`NOTICE_HORIZONTAL_PADDING` 12/`NOTICE_BUTTON_GAP` 8; collapsed breadcrumbs
`BREADCRUMB_COLLAPSED_HEIGHT` 16. `NativeControlStyles` recipes add role colors and typography only: the find
query stays borderless (`borderWidth: 0; focusWidth: 0; innerFocusWidth: 0`), ghost toolbar buttons keep the
look-and-feel arc and margins (the rail passes its named arc), native menus keep look-and-feel margins, and the
terminal notice commands and Migrate Config stay framed standard buttons (`framedButton`, the kit secondary
kind; before Task 5 both notice commands were identical default `JButton`s, so there was no primary emphasis to
restore). Any visual change to these belongs to **Plan 3 Task 10**.

**Vendor keys with a canonical role.** One module table, `UiColorKeys.semanticRoles()`, translates
`ToolWindow.background` → `surface.sidebar`, `TextField.background` → `surface.input`,
`Separator.foreground` → `border.subtle` and `StatusBar.background` → `surface.status`: an authored value wins over the legacy token for that role (as the raw
key did for the native consumer) and an explicit `[semantic]` value wins over both. `MainToolbar.background` and
`TitlePane.background` keep their existing translation as `title_bar` aliases into `surface.app` (token
precedence, as the old `Jasper.titleBackground` publication had).

**Unsupported native overrides (no canonical role).** These authored `[ui]` keys remain accepted color data,
stay in the raw compatibility publication for not-yet-migrated raw controls until Task 11, and no longer
affect migrated native chrome. Diagnostic behaviour in Plan 2: none at runtime (import conversion warnings for
non-color data are unchanged); Task 11 owns the diagnostic when raw publication is removed.

| Key(s) | Native consumer now | Canonical route |
|---|---|---|
| `TitlePane.foreground`, `TitlePane.inactiveForeground`, `MainToolbar.foreground` | title/toolbar text | `text.secondary`/`text.tertiary`/`text.primary` |
| `MainToolbar.borderColor`, `Borders.color` | title separator | `border.subtle` |
| `MainToolbar.searchField.*`, `SearchEverywhere.SearchField.*` | title search field | `surface.input`, `surface.control.hover`, `border.input`, `text.primary`, `text.placeholder` |
| `EditorTabs.*` | folder tabs | terminal palette plus workspace `accent`/text roles |
| `Button.toolbar.{hover,pressed,selected}Background` | native toolbar commands | `hover.overlay` at 8 %, `surface.control.hover`, `segment.selected.bg` |
| `Popup.dropShadowColor` | command palette shadow | `surface.scrim` |
| `DragAndDrop.areaBackground`, `DragAndDrop.borderColor` | dock drop guides | `accent` at the named 25 %/60 % alphas |
| `Label.infoForeground`, `Actions.{Green,Yellow,Red}`, `TabbedPane.underlineColor` | muted/status/badge colors | `text.secondary`, `status.*` (floored on `surface.status`), `accent` (floored on the rail) |

**Status-bar surface and indicator floors (controller ruling, fix round 1).** Canonical `surface.status` (base
`status_bar`; vendor `StatusBar.background`; explicit `[semantic]` wins) is the native status bar's surface, so
light themes with a dark title bar (IntelliJ New UI Light: `MainToolbar.background` Gray2, `StatusBar.background`
Gray13) keep their light status bar. Status text and the running dot are held to 4.5:1 on `surface.status`, and
rail badges to 3:1 on `surface.app`, by `theme.SurfaceContrast.on` where painted; the shared `status.*`/`accent`
roles are not changed. Rejected alternative: flooring the shared canonical roles against `surface.app` (it made
status colors unreadable on light panels).

## Task 4 typed-mount transition — exact Task 11 removal owners

All entries below are temporary source compatibility for bundled plugins migrating in Tasks 7–10.
Task 11 removes every legacy member and bridge name together, updates both hosts and their contracts,
and does not retain a legacy raw-Swing plugin entry point.

| Exact SDK owner/member | Task 11 final action |
|---|---|
| `ui.PanelFactory.create(PanelHost): JComponent` | Replace return type with UiView. |
| `ui.PanelFactory.View` and `View.createView(PanelHost)` | Remove nested bridge; move typed signature to PanelFactory.create. |
| `ui.Panels.registerView(PanelSpec,PanelFactory.View)` | Fold into register(PanelSpec,PanelFactory), remove bridge name and legacy registration adapter. |
| `ui.WindowSurface.setContent(JComponent)` | Remove; retain setContent(UiView). |
| `ui.Windows.openTab(ContentSpec,WindowOwner,JComponent)` | Remove; retain UiView overload. |
| `settings.SettingsPanel.create(SettingsPanelHost): JComponent` | Replace return type with UiView. |
| `settings.SettingsPanel.View`, `View.createView`, `View.create` default throwing shim | Remove nested bridge and shim; typed create becomes final factory. The host selects by View marker, never by null fallback. |
| `settings.SettingsPanelHost.row(Setting): JComponent` | Remove Swing path. |
| `settings.SettingsPanelHost.rowNode(Setting): Nodes.Node` | Rename to row after removing Swing path. |
| `ui.PanelSpec.icon(): Icon`, Icon constructors (4/5/6 arguments), dual-source canonical 7-argument constructor | Remove arbitrary Icon slot/constructors and dual-source compatibility; retain semantic iconName, identity/anchor/split/toggle behavior. |

Mirrored implementation removal owners: `app.plugins.HostedUi` legacy register/setContent/openTab
adapters; `PluginPanelPage` legacy `panel.create`, `Host.row`, `rows`, `own` and `KeepsHeight` Swing-row
support; `sdk.testing.FakeUi` legacy factory/content adapters; `FakePluginHost.settingsPanel` legacy
create branch; `FakeSettingsPanelHost.row` placeholder. `KitSettingRows`, owned handle lifetime checks,
semantic icon resolution and live motion source are final behavior, not removal targets. App-native
JComponent transport in contributions/windows/workspace remains an internal module boundary; it does
not expose raw Swing through the final SDK. Existing auxiliary/panel/content lifetime owners require
no duplicate new adapter outside app.plugins.
