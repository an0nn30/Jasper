# Task 1 implementation report: Linux default residency

## Result

Linux now defaults `system.run_in_background` to true while explicit saved values remain authoritative. The public setting shape and config alias are unchanged. Residency behavior when no tray indicator is installed remains covered by a lifecycle test.

## TDD evidence

- RED: `./gradlew :jasper-app:test --tests '*CoreSettingsTest' --tests '*ConfigLoaderTest' --tests '*JasperApplicationResidencyTest'` failed at `CoreSettingsTest.residencyIsOnByDefaultOnSupportedDesktopPlatforms`, with `[Linux] Expecting value to be true but was false`. The first attempt exposed a missing `java.util.List` import in the new test; after fixing it, the expected behavior failure was observed.
- GREEN focused: same command passed; XML totals were 70 tests, 0 failures, 0 errors, 0 skipped (16 CoreSettingsTest, 37 ConfigLoaderTest, 17 JasperApplicationResidencyTest).
- GREEN adjacent regressions: `./gradlew :jasper-app:test --tests '*ConfigTemplateTest' --tests '*ApplicationBootstrapTest' --tests '*ResidentIndicatorTest' --tests '*JasperApplicationBackgroundTest' --tests '*RehearsalTest'` passed; XML totals were 109 tests, 0 failures, 0 errors, 0 skipped (10, 27, 12, 40, and 20 respectively).
- Full `check` was not run; it is reserved for Task 4.

Logs: `/private/tmp/jasper-linux-residency-task1-focused-final.log` and `/private/tmp/jasper-linux-residency-task1-integration.log`.

## Files changed

- `CoreSettings.java`: recognizes macOS, Windows and case-insensitive Linux desktop identifiers; null and unsupported systems remain false. Updated the setting description.
- `config.example.toml`: updated the default comment and left the setting commented out.
- `CoreSettingsTest.java`: asserts supported platform variants and unsupported/null cases.
- `ConfigLoaderTest.java`: verifies true/false survive through both the canonical setting and alias, and canonical key precedence when both are present; removed the stale Linux-off comment.
- `JasperApplicationResidencyTest.java`: pins that residency with an installed indicator callback still keeps the process alive after the last-window event and quit terminates it.

## Review / concerns

The implementation is limited to the requested default and accurate copy. It does not change login registration, parsing, restart application, eligibility, or tray handling. The no-indicator lifecycle test follows the brief; indicator cleanup and late events remain covered by `ResidentIndicatorTest`. No GUI, login registration, AOT training, benchmark, full check, merge or push was performed. The focused build emitted existing unrelated doclint and unchecked-cast warnings during compilation; tests passed.
