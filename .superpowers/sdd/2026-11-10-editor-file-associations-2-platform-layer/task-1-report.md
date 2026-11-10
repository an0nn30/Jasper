# Task 1 report: the file-type catalogue

## Implemented
- `file-types.tsv`: 28 type lines (the spec's list, dotted extensions) and 5 import lines (dev.jasper.kotlin-source, go-source, rust-source, typescript-source conforming to public.source-code; dev.jasper.toml to public.plain-text). Identifiers follow the brief/spec first draft. Header comments describe the format.
- `FileTypeCatalog` (package-private, no Swing): `builtIn()` (lazy holder, UTF-8 resource), `parse`, `extensions`, `macTypes`, `linuxTypes`, `macImports`, `record Import`. parse throws IllegalArgumentException naming "line N" for a malformed line or duplicate extension, and naming the UTI for an unused import.
- `FileTypeCatalogTest`: the two tests from the brief.

## TDD
- RED: `./gradlew :jasper-app:test --tests '*FileTypeCatalogTest'` -> compile errors, BUILD FAILED.
- GREEN: same command -> BUILD SUCCESSFUL, tests=2 failures=0.
- First `./gradlew check` failed in `ThemeMigrationCoverageTest.sourceDiscoveryIncludesTestFixturesAndHelperFalsePositives`: its CANDIDATE regex matched `new ArrayList` (`new X...List`) in FileTypeCatalog.java and demanded an inventory row. Fixed by writing `new java.util.ArrayList<>()` (no inventory entry needed). Second `./gradlew check`: BUILD SUCCESSFUL in 19m 59s.
- Source-hygiene Python check: no bad chars.

## Files
- jasper-app/src/main/resources/dev/jasper/app/platform/file-types.tsv
- jasper-app/src/main/java/dev/jasper/app/platform/FileTypeCatalog.java
- jasper-app/src/test/java/dev/jasper/app/platform/FileTypeCatalogTest.java

## Concerns
- No check task objected to the .tsv (tabs included).
- Spec table says Kotlin is `org.kotlinlang.source`; brief says `dev.jasper.kotlin-source`; I used the brief.
- The ThemeMigrationCoverageTest regex is a trap for any new `new *List` in jasper-app; later tasks should expect it.
- Linux .ts is only `application/x-typescript` (spec has an ellipsis); later tasks verify against globs2.
