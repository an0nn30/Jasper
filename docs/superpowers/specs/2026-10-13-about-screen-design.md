# About screen, build information and licenses — design

Date: 2026-10-13. Status: approved in conversation; awaiting review of this written spec.
Branch: `claude/about-screen` (worktree `.claude/worktrees/about-screen`), from `origin/main` `b9f6b848`.

## Why

Jasper has no About screen, and a running Jasper cannot say which commit it was built from: the only version it
knows is jpackage's `jpackage.app-version` (from `-PjasperVersion`, default `1.0.0`), and a Gradle `run` knows only
that it is a "development build". The repository has no license. The user's direction:

- an About screen with the app icon, the name, the version and the commit hash;
- a build that is not on a release tag (from `run` or `packageDist`) is visibly marked as a development build of a
  branch and commit; a build from a release tag is an official version;
- Jasper is licensed for non-commercial use (PolyForm Noncommercial 1.0.0, chosen in conversation), and the screen
  shows the licenses: Jasper's own and those of the third-party software Jasper ships (Apache-2.0, MIT and BSD all
  require their notices to travel with distributed copies).

## Scope

In: build information generated from git into the app jar; the release/development rule; the package version taken
from the release tag; an About window and a Licenses window; the `app.about` action and its menu placement; a root
`LICENSE.md`; a curated third-party notices index with a completeness check inside `check`; error reports using the
same build description; documentation.

Out: update checks, release notes, a credits or authors list, a release workflow that packages on a tag, licenses of
plugins a user installs (they carry their own).

## Build information

### What the build records

A `:jasper-app` task `buildInfo` writes `build/generated/build-info/dev/jasper/app/build-info.properties`; its output
directory is added to the main resources, so `run`, `installDist`, the distribution archives and `packageDist` all
carry the same file inside `jasper-app.jar`.

Gradle collects raw git facts only, through `providers.exec` (each command ignoring its exit value; a failure or a
missing `git` yields an empty value), run in the root project directory:

| Key | Source |
|---|---|
| `commit` | `git rev-parse HEAD` |
| `branch` | `git rev-parse --abbrev-ref HEAD` (`HEAD` when detached) |
| `commitDate` | `git log -1 --format=%cI` |
| `dirty` | `true` when `git status --porcelain` prints anything (tracked changes, or untracked files git does not ignore) |
| `headTags` | `git tag --points-at HEAD`, newline-separated |
| `describe` | `git describe --tags --long --match "v[0-9]*.[0-9]*.[0-9]*"` (for example `v1.2.0-14-g8ba67ed3`) |

The file records the commit date, not the build time, so two builds of the same tree write identical bytes and the
packaging tasks stay up to date. The task declares these values as inputs, so it reruns when any of them changes.

`:jasper-app` declares `normalization { runtimeClasspath { ignore("dev/jasper/app/build-info.properties") } }`, so a
new commit or a dirty tree does not make Jasper's tests out of date.

### How a build is classified

`dev.jasper.app.platform.BuildInfo` (JDK only) reads the resource and derives everything below; Gradle derives only the
package version. A release tag is a tag matching `v(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)`; other tags are ignored.

- **Release:** `headTags` holds a release tag and `dirty` is false. The version is the tag without `v` (`1.2.0`).
  If HEAD carries several release tags, the highest version wins.
- **Development:** anything else. The version is `<base> + <n> commits` (`+ 1 commit` for one) from `describe`
  when it names a release tag, `<base>` alone when HEAD is that tag but the tree is dirty, and absent when no release
  tag is reachable.
- **Unknown:** the resource is missing or `commit` is empty (a source archive without `.git`, an IDE build that skipped
  the task). It is a development build with the commit shown as `unknown`.

The location line is `<branch> @ <short>`, or `<short>` alone when detached, where `<short>` is the first 8
characters of `commit`; `, modified` follows when `dirty`. The date is shown as the commit date in the local zone.

`BuildInfo` offers one summary line used by error reports and the Copy text:

- release: `1.2.0`
- development: `1.2.0 + 14 commits, development build (main @ 8ba67ed3, modified)`;
  without a reachable tag: `development build (main @ 8ba67ed3)`; unknown: `development build (commit unknown)`.

`IncidentReport.Environment.current()` uses that summary instead of `jpackage.app-version`/"development build";
`dev.jasper.app.notifications` gains `dev.jasper.app.platform` as an allowed dependency.

### Package version

`gradle/packaging.gradle`'s version becomes: `-PjasperVersion` if given; else the release tag on HEAD (highest if
several), whether or not the tree is dirty; else the latest release tag reachable from HEAD (from `describe`); else
`1.0.0`. The existing `validatedVersion` rule (X.Y.Z, major 1–255, minor 0–255, patch 0–65535) applies to the result,
so a `v0.x` tag cannot be packaged. On a commit carrying a release tag, a `-PjasperVersion` that differs from the tag
fails the build with a message naming both.

`.github/workflows/linux-packages.yml` passes `-PjasperVersion` only when the dispatch input is non-empty, and the
input loses its `1.0.0` default.

## The About window

### Opening it

- An application action `app.about`, "About Jasper" (no ellipsis, as platform About items have none), keywords `version`, `commit`, `build`, `license`,
  `licenses`, `credits`, in the command palette and rebindable like `app.shortcuts`; `ShortcutCatalog` lists it under
  Application.
- macOS: `Desktop.setAboutHandler` (when `Desktop.Action.APP_ABOUT` is supported) runs it, so Jasper › About Jasper
  opens this window instead of AWT's default panel. It is not added to the Help menu on macOS.
- Windows and Linux: Help › About Jasper, after Keyboard Shortcuts.

### Window

A singleton auxiliary window `jasper.about`, titled "About Jasper", non-modal, opened through
`AuxiliaryWindows.window` with a preferred size of 340 × 400 and brought to the front when already open (like `ErrorsWindow`). It
closes at shutdown with the other auxiliary windows. It is built as a jasper-ui kit view, so it follows the current
theme and density.

Top to bottom, centred:

1. the platform's application icon at 64 pt, from the existing `ApplicationIcon` resources;
2. "Jasper" (title type);
3. the version line: `1.2.0`, `1.2.0 + 14 commits`, or nothing when no version is known;
4. on development builds only, a kit `Chip` "Development build" in the `AMBER` hue;
5. the location line (`main @ 8ba67ed3, modified`), monospace and selectable;
6. "Committed 13 Oct 2026" (omitted when the date is unknown);
7. "PolyForm Noncommercial 1.0.0" and "Copyright 2026 Dustin McKenna-Watts";
8. two secondary buttons: **Licenses…** and **Copy**.

### Copy

Copy puts this text on the clipboard through the shared `HostClipboard` (a busy clipboard is reported as today's
"Could not copy"):

```
Jasper <BuildInfo summary>
Commit <full commit> on <branch>[, modified]; committed <yyyy-MM-dd>
<IncidentReport.Environment os>
<IncidentReport.Environment java>
```

The commit line is `Commit unknown` for an unknown build, and drops ` on <branch>` when detached. The button's label
reads "Copied" for two seconds after a successful copy, switching instantly in both directions (no animation).

## The Licenses window

A singleton auxiliary window `jasper.licenses`, titled "Licenses", opened only by About's Licenses… button, through
`AuxiliaryWindows.window` with a preferred size of 760 × 520; it is resizable and remembers its bounds like other auxiliary windows,
and stays open when About closes.

Like the Errors window, a list beside a detail:

- **List:** Jasper first, then the third-party components sorted by name (case-insensitive). Each row shows the name
  and version, with the license name as its subtitle. Jasper is selected when the window opens.
- **Detail:** a header with the name, version, license name and a homepage link (opened with the application's
  browse hook), then the component's NOTICE text when it has one, then its license text: monospace, selectable,
  scrolling inside the detail.

Texts are read from resources when a component is selected, not when the window opens.

## Licenses

### Jasper's license

- A root `LICENSE.md`: the PolyForm Noncommercial License 1.0.0, verbatim from
  `https://polyformproject.org/licenses/noncommercial/1.0.0/`, followed by the line
  `Required Notice: Copyright 2026 Dustin McKenna-Watts (https://github.com/an0nn30/Jasper)`.
- `:jasper-app:processResources` copies it to `dev/jasper/app/about/licenses/jasper.md`; it is the only copy in the
  repository.
- The portable distribution archives (`distZip`, `distTar`, `installDist`) carry `LICENSE.md` at their root.
- `README.md` gains a License section with one line naming the license and linking `LICENSE.md`.

### Third-party notices

`jasper-app/src/main/resources/dev/jasper/app/about/licenses/notices.toml` lists one `[[component]]` per project:

```toml
[[component]]
name = "Apache MINA SSHD"
version = "2.19.0"
license = "Apache-2.0"            # SPDX id; the window shows its full name from a fixed table
homepage = "https://mina.apache.org/sshd-project/"
modules = ["org.apache.sshd:sshd-common:2.19.0", "org.apache.sshd:sshd-core:2.19.0", "org.apache.sshd:sshd-sftp:2.19.0"]
text = "apache-2.0.txt"           # relative to this folder, or an absolute classpath resource path
notice = "mina-sshd.NOTICE.txt"   # optional
```

- Texts identical across components are stored once (`apache-2.0.txt`, `epl-1.0.txt`,
  `gpl-2.0-classpath-exception.txt`); MIT and BSD texts are per component, carrying each project's copyright line.
- A component whose project publishes a NOTICE file (in its jar's `META-INF` or its source distribution) names it in
  `notice`.
- Components that are not jars have no `modules` and point at files the repository already holds:
  the IntelliJ-derived icons (`/dev/jasper/ui/icons/LICENSE.txt`, `NOTICE.txt`) and Cousine Nerd Font Mono
  (`/dev/jasper/app/fonts/cousine/OFL.txt`, `NERD-FONTS-LICENSE.txt`). The JetBrains Runtime 25 has an entry
  (GPL-2.0 with the Classpath Exception).
- A project offered under a choice of licenses is listed under the one Jasper uses: Apache-2.0 for JediTerm and JNA.
- Each license is confirmed from the artifact's POM and its jar's `META-INF` during implementation.

From the jars staged today the index covers about 25 components: JediTerm, pty4j, JNA (with JNA Platform), FlatLaf
(with FlatLaf Extras), jsvg, tomlj, the ANTLR 4 runtime, Checker Qual, the Kotlin standard library, JetBrains
Annotations, the JBR API, SLF4J (API, NOP, JDK14), JCL over SLF4J, Bouncy Castle (bcprov, bcpkix, bcutil), Apache
MINA, Apache MINA SSHD, Apache FtpServer (core, ftplet API), RSyntaxTextArea, SQLite JDBC, the icons, the font and the
runtime. Where one entry covers modules of different versions (Bouncy Castle's `bcprov` 1.85.2 beside 1.85), the
entry's `version` is the project's release and `modules` keeps each exact coordinate.

`dev.jasper.app.about.ThirdPartyNotices` reads the index with tomlj and loads a component's texts on request.

### Completeness check

A root task `verifyThirdPartyNotices`, in a new `gradle/third-party-notices.gradle.kts` applied by the root build and
part of `check`, headless:

- collects the resolved external module coordinates (`group:name:version`) of `:jasper-app`'s `runtimeClasspath` and
  of each bundled plugin's `runtimeClasspath` (the plugins `stagePlugins` copies), excluding Jasper's own projects;
- fails, naming each offender, when a shipped coordinate is in no component's `modules` (so a new or bumped dependency
  cannot ship until its license has been looked at), when a listed coordinate is no longer shipped, when a
  coordinate is listed twice, or when a `text`, `notice` or asset file does not exist.

## Packages and architecture

- New package `dev.jasper.app.about`: `AboutView`, `LicensesView`, `ThirdPartyNotices` and the window owners. Its
  allowed outgoing dependencies are `dev.jasper.app.platform` and `dev.jasper.app.windows` (plus the jasper-ui kit, as
  for other app packages).
- `dev.jasper.app.application` may depend on `dev.jasper.app.about`; it registers `app.about`, the macOS handler and
  the Help entry.
- `dev.jasper.app.notifications` may depend on `dev.jasper.app.platform`.
- `verifyApplicationArchitecture` enforces these through the package-info files.

## Testing

All headless and part of `check`:

- `BuildInfo`: release from a clean release tag; development for a release tag on a dirty tree, for commits after a
  tag (`1 commit` and `n commits`), for no reachable tag, for a detached HEAD, for non-release tags only; highest of
  several release tags; unknown for a missing resource and for an empty commit; the summary and location lines in
  each case.
- Package version, in `gradle/packaging-support-test.gradle`'s fixture style: `-PjasperVersion` wins; a differing
  value on a tagged commit fails; each fallback in order. One fixture creates a throwaway git repository in a
  temporary directory and walks it through clean, tagged, ahead-of-tag and dirty states, checking both the recorded
  facts and the package version.
- `AboutView`: no chip on a release; the chip, version and location on development builds; Copy writes the expected
  text to an injected clipboard; "Copied" reverts on an injected timer.
- `LicensesView`: Jasper first, then sorted; selecting a component shows its notice and text; the homepage link calls
  the browse hook.
- `ThirdPartyNotices`: the real index loads; every named file resolves; Jasper's text equals the root `LICENSE.md`
  byte for byte (through `jasper.projectDir`).
- Menu wiring: `app.about` is registered; it sits after `app.shortcuts` in Help on Windows and Linux and not in Help on
  macOS; `ShortcutCatalog` groups it under Application.
- `IncidentReportTest`: the environment's Jasper line is the `BuildInfo` summary.

Checked by hand during implementation and recorded in the plan: after a commit, `./gradlew check` reports the
`:jasper-app:test` task up to date; removing a component from the index makes `verifyThirdPartyNotices` fail.

For the user (no GUI is launched by the agent): Jasper › About Jasper on macOS; Help › About Jasper on Windows and
Linux; both windows in Dark and Light; Copy; the Licenses window's list and texts.

## Documentation

- `docs/STATUS.md`: an entry for this work.
- `docs/app-maintenance.md`: two recipes, "Cut a release" (tag `vX.Y.Z` on a clean tree; packaging takes the version
  from the tag) and "Add or bump a dependency" (add or update its `notices.toml` component).
- `packaging/README.txt`: the version now comes from the release tag unless `-PjasperVersion` is given.
