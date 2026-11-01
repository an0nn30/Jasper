# Gentoo source package implementation

**Status: implemented, installed and task-reviewed; whole-branch review and user graphical acceptance pending.** Clean sandboxed all-off/all-on builds and final GTK-only installation pass. Independent task review found no actionable defects. The [verification record](2026-11-01-gentoo-package-verification.md) captures installed ownership, helper selection, runtime/ELF/JCEF checks and remaining QA notes.

Spec: `docs/superpowers/specs/2026-11-01-gentoo-package-design.md`

Execution: repository-required subagent-driven development. The user approved this source-package design and explicitly authorized implementation; proceed without another approval round.

## Global Constraints

- Source snapshot be557f600; package `app-misc/jasper-workstation`, `~arm64` initially.
- USE flags `gtk`, `gtk4`, `qt6`, `kde` default off; `kde? ( qt6 )`; disabled integrations explicitly off, enabled required; Qt5/KDE5 off.
- Portage fetches pinned inputs; build offline with isolated Gradle state and network sandbox retained.
- Install `/opt/jasper-workstation`, `/usr/bin/jasper`, desktop entry and icon. Preserve licenses and private JBR.
- No GUI launch, AOT training, global Java changes or edits to user preferences.

## Task 1: Source overlay and offline build inputs

**Files:** Create `packaging/gentoo/` overlay, README, dependency lock/generator and focused tests; modify `docs/packaging.md` and `docs/STATUS.md`.

**Interfaces:** Consumes existing `:jasper-app:packageApp` and `:jasper-app:verifyPackage`, and per-provider Gradle properties. Produces an EAPI 8 ebuild, Manifest, local Maven repository staging and installation documentation. The controller provides VM cache inventory and executes Portage verification; implementation owns fixes arising from those checks.

1. Inspect packaging inputs, runtime libraries and license notices. Write focused regressions for lock generation/staging: collisions, metadata preservation, unsupported paths, deterministic output. Prove a failing case before implementation.
2. Implement pinned Maven artifact catalog from the successful build cache, fetching from canonical upstream repositories. Seed wrapper distribution locally and replace project dependency repositories with the staged local Maven repository; invoke `./gradlew --offline` with fresh Gradle state.
3. Implement overlay metadata, license text, EAPI 8 ebuild dependencies/USE mapping and standard installation integration. Extract only source, Gradle and JBR archives, not arbitrary dependency jars. Preserve upstream executable permissions and runtime layout. Implement meaningful package verification in `src_test` where supported.
4. Generate Manifest using Portage. Run focused tests and syntax checks. Controller runs clean Portage all-off build, GTK build/install, installed package ownership and helper selection checks; fix evidenced failures. Check Qt6/KDE flag dependency resolution and selection where practical.
5. Document exact installation/update steps and evidence/limitations, update status, and commit with Co-Authored-By trailer. Independent task and whole-branch review follow; no claim of GUI acceptance.

## Review Focus

- Offline build must not succeed only because of a pre-existing Gradle cache: use clean state under Portage.
- Artifact names must not collide across Maven groups; preserve POM/module metadata and parents.
- USE off must omit helpers despite SDKs installed; installed GTK configuration must include only selected helpers.
- Runtime libraries and license declarations must match bundled private JBR and Java dependencies.
- Source snapshot predates overlay files: stage packaging support from FILESDIR, not from the source archive.
