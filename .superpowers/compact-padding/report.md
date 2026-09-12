# Compact padding and copyable config example report

## Result

- `TerminalPane` now owns one package-private `PADDING = 4` constant. Its empty border uses that value on all sides, and `InitialWindowSize` derives its 8px per-dimension addition from the same constant.
- The actual pane/view layout regression checks 4px insets and full remaining view bounds before and after light/dark theme changes. Initial-size tests use the requested grid plus 8px total in each dimension.
- Root `config.example.toml` is valid UTF-8 TOML with every supported non-shortcut setting active at the built-in default, an empty `[terminal.env]` with an optional example, and commented macOS and Linux/Windows keybinding examples.
- The app test task tracks the root example as a relative input. The real on-disk file is structurally checked for the complete supported setting set, parsed on both platform modes with no diagnostics, and compared with the complete built-in snapshot.
- README and configuration documentation link the example and give the guarded macOS copy command. STATUS and native checks record the 4px geometry; historical mock and Plan 4b geometry documents carry concise supersession banners without changing measured artifacts.

## TDD evidence

RED:

```text
./gradlew :moray-app:test --tests dev.moray.app.InitialWindowSizeTest --tests dev.moray.app.MockUiTest.terminalLayoutKeepsCompactInsetsAcrossThemeChanges --tests dev.moray.app.ConfigTemplateTest.repositoryExampleIsCompleteAndParsesAsBuiltInDefaultsOnBothPlatforms --rerun-tasks
```

Failed as intended: 5 tests completed, 3 failed. The failures were the old +48px initial area, the old 24px actual pane inset, and the absent root example.

GREEN, same command after minimal implementation: `BUILD SUCCESSFUL`; all 5 selected tests passed and all 6 tasks executed.

## Full verification

- `./gradlew check`: `BUILD SUCCESSFUL`; 8 actionable tasks, 1 executed and 7 up-to-date.
- `./gradlew check --rerun-tasks`: `BUILD SUCCESSFUL` in 10s; all 8 actionable tasks executed.
- Fresh Gradle XML totals: 443 tests, 442 passed, 0 failures, 0 errors, 1 known skip. The skip remains `FontSetTest.fallsBackWhenPrimaryCannotDisplay` because this machine has no qualifying code point.
- Repository source-hygiene scan across both modules: clean.
- `git diff --check`: clean.

## API and compatibility

No public API changed. The only new code contract is package-private `TerminalPane.PADDING`; existing terminal coordinate paths, toolbar/title/status geometry, font behavior and session lifecycle are unchanged.

## Concerns and remaining checks

Native visual sizing remains user-run under `AGENTS.md`. The root task owns the actual headless preview and independent review. No GUI, login shell, editor, benchmark, sound, user configuration write, merge or push was performed here.
