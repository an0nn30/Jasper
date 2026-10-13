# SDK release jars for external plugins — design

Date: 2026-10-13. Status: approved in conversation; awaiting review of this written spec.
Branch: `claude/sdk-release` (worktree `.claude/worktrees/sdk-release`), from main `9ce2bcc1`.

## Why

A plugin built outside this repository today needs a Jasper checkout and a Gradle composite build to compile against the
SDK, because the SDK and testkit are not available as files anywhere. The user's direction: a GitHub release carries the
SDK as jars that a plugin developer downloads and links to; how the SDK is published officially (Maven coordinates, a
Gradle plugin) is decided later.

This is piece 2 of three: (1) SDK 0.16.1 clipboard and Tools menu (done, `9ce2bcc1`); (2) this; (3) moving the external
encrypt/decrypt plugin to SDK 0.16.1, built from these jars.

## Scope

In: a Gradle task that builds versioned SDK and testkit jars; a verification of those jars inside `check`; a small
external-style example plugin that builds from the jars; a GitHub Actions workflow that attaches the jars to a published
release (and a manual dry run); one CI step that keeps the example building; documentation.

Out: Maven coordinates, `maven-publish`, a `dev.jasper.plugin` Gradle plugin, signing, publishing to any repository,
changing how in-repo plugins are built.

## Artifacts

A root task `sdkRelease` (group `distribution`) writes exactly these files to `build/sdk-release/`, where `<v>` is
`JasperSdk.VERSION` read from `jasper-sdk/src/main/java/dev/jasper/sdk/JasperSdk.java` (today `0.16.1`):

| File | Contents |
|---|---|
| `jasper-sdk-<v>.jar` | the `jasper-sdk` main classes |
| `jasper-sdk-<v>-sources.jar` | its sources |
| `jasper-sdk-<v>-javadoc.jar` | its Javadoc, from the same doclint-checked `javadoc` task `check` already runs |
| `jasper-sdk-testkit-<v>.jar` | the `jasper-sdk-testkit` main classes (`FakePluginHost` and the other fakes); not its contract test fixtures |
| `jasper-sdk-testkit-<v>-sources.jar` | its main sources |

- The version is read from `JasperSdk.VERSION` at configuration time; there is no second copy of it.
- Each jar's manifest carries `Implementation-Title` (`jasper-sdk` / `jasper-sdk-testkit`) and
  `Implementation-Version` (`<v>`).
- The testkit's main code depends only on the SDK and the JDK; a plugin's tests add JUnit and AssertJ themselves.
- In-repo plugins keep compiling against the projects as today.

## Verification inside `check`

A root task `verifySdkRelease` depends on `sdkRelease` and fails the build unless:

- `build/sdk-release/` holds exactly the five files above, named with `JasperSdk.VERSION`;
- the SDK jar contains `dev/jasper/sdk/JasperSdk.class` and only `dev/jasper/sdk/**` classes (no `dev/jasper/app`,
  `dev/jasper/ui`, `dev/jasper/terminal` or `dev/jasper/sdk/testing`);
- the testkit jar contains `dev/jasper/sdk/testing/FakePluginHost.class` and no `dev/jasper/sdk/testing/contract/**`;
- both class jars' manifests carry the `Implementation-Version` `<v>`.

`check` depends on `verifySdkRelease`.

## The example plugin

`docs/examples/external-plugin/` is a separate Gradle build (its own `settings.gradle.kts`), not part of Jasper's build:

- `settings.gradle.kts`, `build.gradle.kts`, `src/main/resources/plugin.toml`, one plugin class
  (`dev.example.hello.HelloPlugin`, which registers one action placed in `StandardMenu.TOOLS` and copies a fixed text
  with `context.platform().copy`), and one test using `FakePluginHost` that invokes the action and asserts the copy and
  the Tools placement.
- `build.gradle.kts` shows the whole recipe a developer copies: a Java 25 toolchain (any vendor; the JetBrains Runtime
  is needed only to run Jasper), `compileOnly(files("<dir>/jasper-sdk-<v>.jar"))`, the SDK and testkit jars for tests,
  JUnit 6 and AssertJ from Maven Central, and `stagePlugin` / `pluginZip` tasks that carry the plugin jar and its runtime
  classpath, as Jasper's own packaging does.
- The jar directory is the Gradle property `jasperSdkDir`, default `libs` (where a developer drops the downloaded
  jars); the SDK version is the property `jasperSdkVersion`, default the current `JasperSdk.VERSION` written into the
  example.
- Running it: `./gradlew sdkRelease`, then
  `./gradlew -p docs/examples/external-plugin -PjasperSdkDir=<repo>/build/sdk-release check pluginZip`.
- It uses Jasper's Gradle wrapper (`-p` runs it as its own build); it has no wrapper of its own.

## Release workflow

`.github/workflows/sdk-release.yml`:

- **On a published release** (`on: release: types: [published]`): check out the release's tag; set up JBR 25 exactly as
  `ci.yml` does (`actions/setup-java@v6`, `distribution: jetbrains`, `java-version: '25'`, `gradle/actions/setup-gradle@v6`);
  run `./gradlew sdkRelease verifySdkRelease`; build the example plugin against `build/sdk-release` (`check pluginZip`);
  then attach the five jars to that release with `gh release upload "<tag>" build/sdk-release/*.jar --clobber`, using
  `GH_TOKEN: ${{ github.token }}`. Permissions: `contents: write` only.
- **On manual dispatch** (`workflow_dispatch`): the same build and example, then the jars are uploaded as a workflow
  artifact (`actions/upload-artifact`, as `linux-packages.yml` does) instead of to a release.
- Runs on `ubuntu-latest` with a 30-minute timeout, like `ci.yml`.

## CI

`ci.yml`'s `check` job gains one step, on `ubuntu-latest` only (`if: runner.os == 'Linux'`), after `Check`: build the
example plugin against the jars `check` just produced
(`./gradlew -p docs/examples/external-plugin -PjasperSdkDir="$PWD/build/sdk-release" check pluginZip`), so the example
and its instructions cannot drift between releases. CI passes only `jasperSdkDir`, never `jasperSdkVersion`, so an SDK
version bump that forgets the example's default fails CI instead of drifting.

## Documentation

- `docs/plugin-authoring.md`: a section "Building a plugin outside the Jasper repository": take the five jars from the
  Jasper release that matches the `sdk` range you declare (or build them with `./gradlew sdkRelease`), put them in
  `libs/`, and start from `docs/examples/external-plugin/`; link to the example's files rather than copying their code.
  The existing "Distributing a plugin" text (zips, Install from Zip) still applies.
- `jasper-sdk/README.md`: a pointer to the release jars and the example.
- `docs/STATUS.md`: an entry for this work.

## Testing

- `verifySdkRelease` in `check` (above) pins the artifacts.
- The example's own test (`HelloPluginTest`) runs against the jars when the example is built (locally and in CI).
- `./gradlew check` in the worktree, and one local run of the example build against the fresh jars.
- The workflow YAML is checked for syntax (for example with Python's `yaml` module); it first runs for real on the next
  published release or a manual dispatch, which the user starts.

## Left to the user

Publishing a GitHub release (or dispatching the workflow) and confirming the five jars are attached; then piece 3 builds
the external plugin from them.
