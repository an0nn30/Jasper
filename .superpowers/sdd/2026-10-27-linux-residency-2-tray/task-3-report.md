# Plan 2 Task 3 implementation report

## Implemented

- Added `LinuxTrayPolicy` with modern-first capability selection and retry delays capped at 30 seconds.
- Added `LinuxTrayMenu` with the agreed stable menu rows, callback dispatch, and row-major ARGB bytes in network byte order.
- Added focused tests for policy boundaries, menu IDs/actions, transparency and ARGB ordering.
- Added all four sources to the theme migration inventory. Its source-discovery test catches AWT imports even when the new tests are headless; this is the task deviation recorded in the plan status banner and `docs/STATUS.md`.
- Did not change tray routing, call D-Bus, or perform AWT/native/GUI work. Task 2 recovery acceptance remains pending.

## TDD evidence

- **RED:** `./gradlew :jasper-app:test --tests '*LinuxTrayPolicyTest' --tests '*LinuxTrayMenuTest'` failed compiling the tests with 13 expected unresolved-symbol errors for the missing `LinuxTrayPolicy` and `LinuxTrayMenu` classes.
- **GREEN:** The same command passed after implementation. Final combined verification:
  `./gradlew :jasper-app:test --tests '*LinuxTrayPolicyTest' --tests '*LinuxTrayMenuTest' --tests '*ThemeMigrationCoverageTest' verifyApplicationArchitecture`
  completed successfully. XML counts: policy 1/1, menu 2/2, inventory 5/5; zero failures, errors or skips. Architecture verification passed.
- `git diff --check` passed. Source hygiene scan found no raw control/private-use/surrogate characters in the four Java files.

## Files changed

- `jasper-app/src/main/java/dev/jasper/app/platform/LinuxTrayMenu.java`
- `jasper-app/src/main/java/dev/jasper/app/platform/LinuxTrayPolicy.java`
- `jasper-app/src/test/java/dev/jasper/app/platform/LinuxTrayMenuTest.java`
- `jasper-app/src/test/java/dev/jasper/app/platform/LinuxTrayPolicyTest.java`
- `docs/design/theme-engine/migration.csv`
- `docs/superpowers/plans/2026-10-27-linux-residency-2-tray.md`
- `docs/STATUS.md`

## Self-review

Implementation follows the task's complete class definitions. `List.of` keeps menu rows immutable; `BufferedImage` remains inside the conversion call and only a fresh byte array leaves it. No concerns found.
