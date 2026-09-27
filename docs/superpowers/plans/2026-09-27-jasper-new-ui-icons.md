# IntelliJ New UI icons implementation plan

> **For agentic workers:** Use superpowers:executing-plans for inline implementation and one independent final review.

**Goal:** Preview the complete New UI icon catalog through Jasper and its SDK in a worktree.
**Architecture:** Existing AppIcons manager owns paths and rendering; generated BundledIcon names cross the SDK bridge. Semantic app/plugin calls remain independent of artwork paths.
**Tech stack:** Java 25 JBR, Gradle wrapper, FlatSVGIcon, Python vendoring.
**Spec:** ../specs/2026-09-27-jasper-new-ui-icons-design.md
**Status:** Implemented and verified in the worktree; native acceptance is user-run. Inline execution under the user's direct implementation authorization; existing app/Buddy native workflow retained. Deviations: catalog and SDK plumbing form one integration commit because generated names and resource IDs must move together; KEY remains a central Tabler fallback after visual inspection found no upstream key equivalent.

## Global constraints
- Worktree only; no GUI launch, main commit, merge or push.
- SDK stays JDK-only; SDK imports in app.plugins only.
- Complete pinned platform expui plus terminal expui collection; preserve upstream bytes and legal notices.
- Title bar/layout and application brand assets unchanged; icons remain 16 logical pixels.

## Review focus
- Live theme changes on already-created icons.
- Complete catalog including size variants and intentional empty SVGs.
- Plugin loader cannot shadow host artwork.
- Non-square SVGs retain aspect ratio.
- Plugin controls use the same central semantic mappings as chrome.

## Task 1: Vendor and centralize
- [x] Add rendering/provenance regression expecting New UI resources; run it and observe failure.
- [x] Update tools/icons/fetch-intellij-icons.py: pinned recursive tree discovery, staged downloads, Git blob/hash verification, manifest, generated BundledIcon and index, source notice. Fetch every SVG, including dark and size variants.
- [x] Replace NamedIcons mappings and AppIcons resource lookup with validated catalog loading, caching and proportional sizing. Migrate app chrome to semantic IDs.
- [x] Render/check full catalog and live theme behavior; commit with co-author trailer.

## Task 2: SDK and plugins
- [x] Add contract tests for Appearance.icon(BundledIcon), legacy default, FakeBundledIcon, and hosted class-loader isolation; observe failure.
- [x] Add default overload and real/fake implementations; add semantic names and version 0.8.2.
- [x] Inject semantic icons into Vault ManagerPanel and SFTP paging; remove private painters and unused legacy artwork; update plugin SDK floors and documentation.
- [x] Run focused SDK/app/plugin checks and commit with co-author trailer.

## Task 3: Acceptance
- [x] Generate a headless before/after contact sheet, run ./gradlew check, count XML results and scan source hygiene.
- [x] Independent final review; fix actionable findings and verify affected checks.
- [x] Update docs/STATUS.md and plan with evidence/deviations; commit and hand user the worktree and launch command.

## Verification and review

`./gradlew check :jasper-app:installDist` passed: 2,009 tests, no failures/errors, five skips.
All 1,744 SVG hashes and exact manifest membership independently verified; installed app jar
contains all SVGs and all 885 catalog IDs. Source hygiene and diff whitespace checks passed.
The independent review identified an accidentally changed Escape action key and a catalog
entry with different light/dark dimensions. Both received failing regression tests, fixes,
and passing checks; the follow-up review found no remaining actionable issues. Disabled
rendering is retained via FlatLaf's DisabledIconProvider. Native GUI was never launched.
