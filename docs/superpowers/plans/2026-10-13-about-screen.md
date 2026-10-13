# About Screen Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Status:** not started. Branch `claude/about-screen`, worktree `.claude/worktrees/about-screen`, from `origin/main`
`b9f6b848`; spec committed as `bb05af8a`. Baseline `./gradlew check --continue` in the worktree: 9,763 tests,
0 failures, 0 errors, 8 skipped.

**Deviations from the spec, decided while planning (record any new ones here and in `docs/STATUS.md`):**

1. The package-info "Allowed outgoing" lines are documentation; `verifyApplicationArchitecture` enforces only an acyclic
   package graph (the spec said it enforces the lists).
2. The window owner `AboutWindows` lives in `dev.jasper.app.application`, as `ErrorsWindow` does; `dev.jasper.app.about`
   holds the two views and the notices reader and depends on `dev.jasper.app.platform` only.
3. Copy's OS and Java lines carry the error report's `OS: ` and `Java: ` prefixes, so the two texts read alike.
4. The packaging version note goes in `docs/packaging.md` (where `jasperVersion` is documented); `packaging/README.txt` is
   the end-user guide inside the package and is not changed.
5. The fixture checks for `gradle/build-info.gradle` are their own task, `verifyBuildInfoSupport` in
   `gradle/build-info-test.gradle` (part of `check`), in the style of `packaging-support-test.gradle`.
6. Cousine and Nerd Fonts are two components (each has its own license file). The JetBrains Runtime's text is its own
   `legal/java.base/LICENSE`. Three texts no shipped jar carries are downloaded once and committed: the PolyForm
   Noncommercial text, EPL-1.0 (pty4j's `LICENSE`) and ANTLR's `LICENSE.txt`.
7. A failed Copy (busy clipboard) posts "Could not copy the build information" and leaves the label as "Copy".
8. The license text area caps its visible rows with the Errors window's `ERRORS_TRACE_ROWS` metric and scrolls beyond it.

**Goal:** An About Jasper window (icon, name, version, development-build marking, commit, license) with a Copy button and
a Licenses window listing Jasper's PolyForm Noncommercial license and every third-party component Jasper ships.

**Architecture:** Gradle records raw git facts in `dev/jasper/app/build-info.properties` inside `jasper-app.jar`
(ignored by test up-to-date checks); `BuildInfo` derives release/development labels from them; packaging takes the
version from the release tag. A curated `notices.toml` lists the shipped third-party components, kept complete by
`verifyThirdPartyNotices`. Two kit views (`AboutView`, `LicensesView`) in a new `dev.jasper.app.about` package are
opened by `AboutWindows` in `dev.jasper.app.application`.

**Tech Stack:** Java 25 (JBR), Swing with the jasper-ui kit (`Nodes`, `KitView`), Gradle 9.7 (Groovy and Kotlin
scripts), tomlj, JUnit 5 and AssertJ.

**Spec:** [2026-10-13-about-screen-design.md](../specs/2026-10-13-about-screen-design.md)

## Global Constraints

- Java 25 on the JetBrains Runtime; always `./gradlew`, never a system `gradle`. Work only in the worktree.
- Never launch the GUI (`:jasper-app:run`, `bench`, packaging with AOT training). GUI checks go to the user.
- Every commit message ends with:
  ```
  🤖 Generated with Claude Code at The Home Depot

  Co-Authored-By: Claude <noreply@anthropic.com>
  ```
- No raw control, private-use or unpaired surrogate characters in `.java` sources (check with AGENTS.md's Python snippet).
- A release tag matches `v(0|[1-9]\d{0,8})\.(0|[1-9]\d{0,8})\.(0|[1-9]\d{0,8})`; other tags are ignored.
- The resource is `dev/jasper/app/build-info.properties`; its keys are `commit`, `branch`, `commitDate`, `dirty`,
  `headTags`, `describe`.
- The Required Notice line, verbatim: `Required Notice: Copyright 2026 Dustin McKenna-Watts (https://github.com/an0nn30/Jasper)`.
- About: window id `jasper.about`, title "About Jasper", preferred 340 × 400. Licenses: id `jasper.licenses`, title
  "Licenses", preferred 760 × 520.
- Action `app.about`, title "About Jasper", keywords `version`, `commit`, `build`, `license`, `licenses`, `credits`.
- Literal UI text: "Development build", "Licenses…", "Copy", "Copied", "Committed 13 Oct 2026" (pattern `d MMM yyyy`,
  English), "PolyForm Noncommercial 1.0.0", "Copyright 2026 Dustin McKenna-Watts", "commit unknown".
- No animations: label changes are instant.
- Documentation edits are the smallest that make the doc accurate.

## Review Focus

1. A build from a source archive without `.git`, or a machine without `git`, must still build; About then says
   "commit unknown" and counts as a development build. (Task 1: outside-a-repository fixture; Task 2 and Task 6: unknown builds.)
2. A developer's own git settings (`log.showSignature`, `color.ui = always`) must not leak into the recorded facts.
   (Task 1: the fixture's global config turns both on.)
3. Branch names with slashes and non-ASCII letters (`claude/über`) and backslashes must round-trip through the resource.
   (Task 1: `buildInfoText`; Task 2: UTF-8 read.)
4. Several tags on HEAD: only `vX.Y.Z` counts, and the highest wins numerically (`1.10.0` above `1.2.0`).
   (Task 1 and Task 2.)
5. A busy clipboard must not say "Copied"; pressing Copy twice restarts the two seconds rather than reverting early.
   (Task 6.)

---

### Task 1: Record the build's git facts in the app jar

**Files:**
- Create: `gradle/build-info.gradle`
- Create: `gradle/build-info-test.gradle`
- Modify: `jasper-app/build.gradle.kts` (apply the script before `gradle/packaging.gradle`)

**Interfaces:**
- Produces (project `ext` on `:jasper-app`, for Task 3 and the fixtures):
  - `gitFacts(File directory, Map<String, String> environment = [:]) -> Map<String, String>` with the six keys
    (all `''` and `dirty: 'false'` outside a repository)
  - `releaseVersions(String tags) -> List<String>` (`'v1.2.0 nightly'` → `['1.2.0']`)
  - `highestVersion(List<String>) -> String` or `null`
  - `describedVersion(String describe) -> [version: String, commits: int]` or `null`
  - `packageVersion(String requestedOrNull, Map<String, String> facts) -> String`
  - `buildInfoText(Map<String, String> facts) -> String`
  - `buildFacts`: a `Provider<Map<String, String>>` of `gitFacts(rootProject.projectDir)`, computed once per build
- Produces the resource `dev/jasper/app/build-info.properties` in `jasper-app.jar` (task `:jasper-app:buildInfo`).

- [ ] **Step 1: Write the fixture checks**

Create `gradle/build-info-test.gradle`:

```groovy
import java.time.OffsetDateTime

// Fixtures for gradle/build-info.gradle in the style of packaging-support-test.gradle: the pure helpers, then a throwaway
// git repository walked through clean, tagged, ahead-of-tag, dirty and detached states. Needs git; no network.
def verifyBuildInfo = tasks.register('verifyBuildInfoSupport') {
    group = 'verification'
    description = 'Checks the build-information helpers with fixtures and a temporary git repository.'
    doLast {
        assert releaseVersions('v1.2.0 v1.10.0 v1.2 v01.2.3 x1.0.0 v1.2.3-rc1 pre-author-rewrite-main') == ['1.2.0', '1.10.0']
        assert highestVersion(['1.2.0', '1.10.0', '1.9.9']) == '1.10.0'
        assert highestVersion([]) == null
        assert describedVersion('v1.2.0-14-g8ba67ed3') == [version: '1.2.0', commits: 14]
        assert describedVersion('v1.2.0-0-g8ba67ed3') == [version: '1.2.0', commits: 0]
        assert describedVersion('') == null && describedVersion('v1.2-3-gabc1234') == null
        assert packageVersion(null, [headTags: '', describe: '']) == '1.0.0'
        assert packageVersion(null, [headTags: '', describe: 'v1.2.0-3-gabc1234']) == '1.2.0'
        assert packageVersion(null, [headTags: 'nightly v1.3.0 v1.10.0', describe: 'v1.10.0-0-gabc1234']) == '1.10.0'
        assert packageVersion('1.4.0', [headTags: '', describe: 'v1.2.0-3-gabc1234']) == '1.4.0'
        assert packageVersion('1.3.0', [headTags: 'v1.3.0', describe: '']) == '1.3.0'
        try {
            packageVersion('1.4.0', [headTags: 'v1.3.0', describe: ''])
            assert false : 'a -PjasperVersion that disagrees with the release tag must fail'
        } catch (GradleException expected) {
            assert expected.message.contains('1.4.0') && expected.message.contains('v1.3.0')
        }
        String text = buildInfoText([commit: 'a' * 40, branch: 'claude/über', commitDate: '2026-10-13T14:02:11-04:00',
            dirty: 'true', headTags: 'v1.2.0', describe: 'v1.2.0-0-gaaaaaaa'])
        assert text.readLines() == ['# Written by :jasper-app:buildInfo (gradle/build-info.gradle).',
            "commit=${'a' * 40}".toString(), 'branch=claude/über', 'commitDate=2026-10-13T14:02:11-04:00', 'dirty=true',
            'headTags=v1.2.0', 'describe=v1.2.0-0-gaaaaaaa']
        assert buildInfoText([branch: 'a\\b']).readLines().contains('branch=a\\\\b')
        logger.lifecycle('Verified the build-information helpers')
    }
}
tasks.named('check') { dependsOn(verifyBuildInfo) }

verifyBuildInfo.configure {
    doLast {
        File root = new File(temporaryDir, 'git')
        root.deleteDir()
        root.mkdirs()
        // The developer's own git configuration stays out: this global file replaces theirs and there is no system file.
        // It turns on the settings that would pollute the facts, to prove gitFacts turns them off.
        File global = new File(root, 'gitconfig')
        global.text = '[log]\n\tshowSignature = true\n[color]\n\tui = always\n[user]\n\tname = Fixture\n\temail = fixture@example.invalid\n' +
            '[commit]\n\tgpgSign = false\n[tag]\n\tgpgSign = false\n'
        // The ceiling keeps git from finding Jasper's own repository above the fixture.
        Map<String, String> environment = [GIT_CONFIG_GLOBAL: global.absolutePath, GIT_CONFIG_NOSYSTEM: '1',
            GIT_CEILING_DIRECTORIES: root.absolutePath, LC_ALL: 'C']
        File repo = new File(root, 'repo')
        repo.mkdirs()
        def run = { List<String> arguments ->
            def builder = new ProcessBuilder(['git'] + arguments).directory(repo).redirectErrorStream(true)
            builder.environment().putAll(environment)
            def process = builder.start()
            String output = process.inputStream.getText('UTF-8')
            assert process.waitFor() == 0 : "git ${arguments.join(' ')} failed:\n${output}"
        }

        File outside = new File(root, 'outside')
        outside.mkdirs()
        assert gitFacts(outside, environment) == [commit: '', branch: '', commitDate: '', dirty: 'false', headTags: '', describe: '']

        run(['init', '-q', '-b', 'main'])
        new File(repo, 'a.txt').text = 'one\n'
        run(['add', 'a.txt'])
        run(['commit', '-q', '-m', 'one'])
        def first = gitFacts(repo, environment)
        assert first.commit ==~ /[0-9a-f]{40}/ && first.branch == 'main' && first.dirty == 'false'
        assert first.headTags == '' && first.describe == ''
        assert OffsetDateTime.parse(first.commitDate) != null
        assert packageVersion(null, first) == '1.0.0'

        run(['tag', 'v1.2.0'])
        run(['tag', 'nightly'])
        def tagged = gitFacts(repo, environment)
        assert tagged.headTags == 'nightly v1.2.0' && tagged.describe ==~ /v1\.2\.0-0-g[0-9a-f]+/
        assert packageVersion(null, tagged) == '1.2.0'

        new File(repo, 'a.txt').text = 'two\n'
        assert gitFacts(repo, environment).dirty == 'true'
        run(['commit', '-q', '-am', 'two'])
        def ahead = gitFacts(repo, environment)
        assert ahead.headTags == '' && ahead.dirty == 'false' && ahead.describe ==~ /v1\.2\.0-1-g[0-9a-f]+/
        assert packageVersion(null, ahead) == '1.2.0' && describedVersion(ahead.describe).commits == 1

        new File(repo, 'untracked.txt').text = 'new\n'
        assert gitFacts(repo, environment).dirty == 'true'
        run(['checkout', '-q', '--detach', 'HEAD'])
        assert gitFacts(repo, environment).branch == 'HEAD'
        logger.lifecycle('Verified build facts from a temporary git repository')
    }
}
```

- [ ] **Step 2: Apply a stub so the checks run and fail**

Create `gradle/build-info.gradle` containing only the line that applies the fixtures:

```groovy
apply from: rootProject.file('gradle/build-info-test.gradle')
```

In `jasper-app/build.gradle.kts`, immediately above `apply(from = rootProject.file("gradle/packaging.gradle"))`, add:

```kotlin
// The commit this jar is built from (dev/jasper/app/build-info.properties); packaging.gradle reads its version from it.
apply(from = rootProject.file("gradle/build-info.gradle"))
```

- [ ] **Step 3: Run the checks to see them fail**

Run: `./gradlew :jasper-app:verifyBuildInfoSupport`
Expected: FAIL with `Could not find method releaseVersions()` (or `No signature of method`).

- [ ] **Step 4: Write the helpers and the task**

Replace `gradle/build-info.gradle` with:

```groovy
import java.util.concurrent.TimeUnit

// The commit jasper-app.jar is built from, as raw git facts in dev/jasper/app/build-info.properties. BuildInfo
// (dev.jasper.app.platform) derives the release and development labels from them; gradle/packaging.gradle derives the
// package version. gradle/build-info-test.gradle checks these helpers with fixtures (verifyBuildInfoSupport, part of check).

// The six facts, each command's output trimmed, or '' when git is missing, fails or this is no repository. A developer's
// own settings must not change the output, so signatures and colour are off; stderr is discarded.
ext.gitFacts = { File directory, Map<String, String> environment = [:] ->
    def git = { List<String> arguments ->
        try {
            def builder = new ProcessBuilder(['git', '-c', 'log.showSignature=false', '-c', 'color.ui=never'] + arguments)
                .directory(directory).redirectError(ProcessBuilder.Redirect.DISCARD)
            builder.environment().putAll(environment)
            def process = builder.start()
            process.outputStream.close()
            String output = process.inputStream.getText('UTF-8')
            if (!process.waitFor(30, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                return ''
            }
            process.exitValue() == 0 ? output.trim() : ''
        } catch (IOException ignored) {
            ''
        }
    }
    String commit = git(['rev-parse', 'HEAD'])
    if (!(commit ==~ /[0-9a-f]{40}([0-9a-f]{24})?/))
        return [commit: '', branch: '', commitDate: '', dirty: 'false', headTags: '', describe: '']
    [commit    : commit,
     branch    : git(['rev-parse', '--abbrev-ref', 'HEAD']),
     commitDate: git(['log', '-1', '--format=%cI', 'HEAD']),
     dirty     : String.valueOf(!git(['status', '--porcelain']).isEmpty()),
     headTags  : git(['tag', '--points-at', 'HEAD']).readLines()*.trim().findAll { !it.isEmpty() }.sort().join(' '),
     describe  : git(['describe', '--tags', '--long', '--match', 'v[0-9]*.[0-9]*.[0-9]*', '--exclude', 'v*[!0-9.]*', 'HEAD'])]
}

// A release tag is vX.Y.Z without leading zeroes; other tags are ignored.
ext.releaseVersions = { String tags ->
    tags.split(/\s+/).findAll { it ==~ /v(0|[1-9][0-9]{0,8})\.(0|[1-9][0-9]{0,8})\.(0|[1-9][0-9]{0,8})/ }.collect { it.substring(1) }
}

// The highest X.Y.Z by number (1.10.0 above 1.2.0), or null for none.
ext.highestVersion = { List<String> versions ->
    Comparator<String> numeric = { String a, String b ->
        List<Integer> x = a.tokenize('.').collect { it as int }
        List<Integer> y = b.tokenize('.').collect { it as int }
        for (int i = 0; i < 3; i++) if (x[i] != y[i]) return x[i] <=> y[i]
        0
    } as Comparator<String>
    versions.isEmpty() ? null : versions.max(numeric)
}

// git describe's "v1.2.0-14-g8ba67ed3": the version and the commits since it, or null.
ext.describedVersion = { String describe ->
    def match = describe =~ /^v((?:0|[1-9][0-9]{0,8})\.(?:0|[1-9][0-9]{0,8})\.(?:0|[1-9][0-9]{0,8}))-([0-9]{1,9})-g[0-9a-f]+$/
    match.matches() ? [version: match.group(1), commits: Integer.parseInt(match.group(2))] : null
}

// -PjasperVersion, else the release tag on HEAD (dirty or not), else the latest release tag behind HEAD, else 1.0.0.
// A -PjasperVersion that disagrees with HEAD's release tag fails.
ext.packageVersion = { String requested, Map<String, String> facts ->
    String tagged = highestVersion(releaseVersions(facts.headTags ?: ''))
    if (requested) {
        if (tagged != null && requested != tagged)
            throw new GradleException("-PjasperVersion=${requested} differs from this commit's release tag v${tagged}")
        return requested
    }
    if (tagged != null) return tagged
    def described = describedVersion(facts.describe ?: '')
    described != null ? described.version : '1.0.0'
}

// The resource: the six facts in a fixed order, so the same tree writes the same bytes (no build time).
ext.buildInfoText = { Map<String, String> facts ->
    '# Written by :jasper-app:buildInfo (gradle/build-info.gradle).\n' +
        ['commit', 'branch', 'commitDate', 'dirty', 'headTags', 'describe'].collect { String key ->
            "${key}=${(facts[key] ?: '').replace('\\', '\\\\')}".toString()
        }.join('\n') + '\n'
}

def computedFacts = null
ext.buildFacts = providers.provider { computedFacts == null ? (computedFacts = gitFacts(rootProject.projectDir)) : computedFacts }
def buildInfoDirectory = layout.buildDirectory.dir('generated/build-info')
def buildInfo = tasks.register('buildInfo') {
    group = 'build'
    description = 'Records the git commit Jasper is built from in dev/jasper/app/build-info.properties.'
    inputs.property('facts', buildFacts)
    outputs.dir(buildInfoDirectory)
    doLast {
        File file = buildInfoDirectory.get().file('dev/jasper/app/build-info.properties').asFile
        file.parentFile.mkdirs()
        file.setText(buildInfoText(buildFacts.get()), 'UTF-8')
    }
}
sourceSets.main.resources.srcDir(buildInfo)
// A new commit or a dirty tree changes only this file; it must not make Jasper's tests out of date.
normalization { runtimeClasspath { ignore 'dev/jasper/app/build-info.properties' } }

apply from: rootProject.file('gradle/build-info-test.gradle')
```

- [ ] **Step 5: Run the checks to see them pass**

Run: `./gradlew :jasper-app:verifyBuildInfoSupport`
Expected: PASS, logging "Verified the build-information helpers" and "Verified build facts from a temporary git repository".

- [ ] **Step 6: Check the resource lands in the jar**

Run: `./gradlew :jasper-app:jar` then `unzip -p jasper-app/build/libs/jasper-app.jar dev/jasper/app/build-info.properties`
Expected: seven lines; `commit=` is this worktree's HEAD (`git rev-parse HEAD`), `branch=claude/about-screen`,
`dirty=true` (the new files are uncommitted).

- [ ] **Step 7: Confirm a changed tree leaves tests up to date**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.notifications.IncidentReportTest'` (passes; may run).
Then create an untracked file: `touch build-info-dirty-probe.txt`. Run the same command again.
Expected: `:jasper-app:buildInfo` runs (the facts changed) and `:jasper-app:test` is reported `UP-TO-DATE`.
Remove the probe: `rm build-info-dirty-probe.txt`. If the test task re-ran, the normalization is wrong: stop and fix it.
Record the result in this plan's status banner.

- [ ] **Step 8: Commit**

```bash
git add gradle/build-info.gradle gradle/build-info-test.gradle jasper-app/build.gradle.kts
git commit -m "Record the build's git facts in the app jar

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 2: BuildInfo, and error reports that name the build

**Files:**
- Create: `jasper-app/src/main/java/dev/jasper/app/platform/BuildInfo.java`
- Create: `jasper-app/src/test/java/dev/jasper/app/platform/BuildInfoTest.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/notifications/IncidentReport.java` (`Environment.current()` and its Javadoc)
- Modify: `jasper-app/src/main/java/dev/jasper/app/notifications/package-info.java` (allowed dependencies)
- Modify: `jasper-app/src/test/java/dev/jasper/app/notifications/IncidentReportTest.java:38-43`

**Interfaces:**
- Consumes: the resource from Task 1.
- Produces (`dev.jasper.app.platform.BuildInfo`, a public record):
  - components `boolean release()`, `Optional<String> version()`, `int commitsSince()`, `String commit()`,
    `String branch()`, `boolean dirty()`, `Optional<OffsetDateTime> committed()`
  - `static final BuildInfo UNKNOWN`; `static BuildInfo current()`; `static BuildInfo of(Map<String, String> facts)`;
    package-private `static BuildInfo read(InputStream orNull)`; package-private `static final String RESOURCE`
  - `boolean known()`, `String shortCommit()`, `String versionLine()`, `String location()`, `String summary()`,
    `String committedLine(ZoneId)`, `String commitLine(ZoneId)`

- [ ] **Step 1: Write the failing tests**

Create `jasper-app/src/test/java/dev/jasper/app/platform/BuildInfoTest.java`:

```java
package dev.jasper.app.platform;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class BuildInfoTest {
    private static final String COMMIT = "8ba67ed3" + "0".repeat(32);

    /** A clean main checkout with no tags, overridden pair by pair. */
    private static Map<String, String> facts(String... pairs) {
        var facts = new HashMap<>(Map.of("commit", COMMIT, "branch", "main", "commitDate", "2026-10-13T14:02:11-04:00",
            "dirty", "false", "headTags", "", "describe", ""));
        for (int i = 0; i < pairs.length; i += 2) facts.put(pairs[i], pairs[i + 1]);
        return facts;
    }

    @Test void aCleanReleaseTagIsARelease() {
        var build = BuildInfo.of(facts("headTags", "v1.2.0", "describe", "v1.2.0-0-g8ba67ed3"));
        assertThat(build.release()).isTrue();
        assertThat(build.versionLine()).isEqualTo("1.2.0");
        assertThat(build.summary()).isEqualTo("1.2.0");
        assertThat(build.location()).isEqualTo("main @ 8ba67ed3");
    }

    @Test void aReleaseTagOnAModifiedTreeIsADevelopmentBuildOfThatVersion() {
        var build = BuildInfo.of(facts("headTags", "v1.2.0", "dirty", "true"));
        assertThat(build.release()).isFalse();
        assertThat(build.versionLine()).isEqualTo("1.2.0");
        assertThat(build.location()).isEqualTo("main @ 8ba67ed3, modified");
        assertThat(build.summary()).isEqualTo("1.2.0, development build (main @ 8ba67ed3, modified)");
    }

    @Test void commitsAfterATagCountFromIt() {
        var build = BuildInfo.of(facts("describe", "v1.2.0-14-g8ba67ed3"));
        assertThat(build.release()).isFalse();
        assertThat(build.versionLine()).isEqualTo("1.2.0 + 14 commits");
        assertThat(build.summary()).isEqualTo("1.2.0 + 14 commits, development build (main @ 8ba67ed3)");
        assertThat(BuildInfo.of(facts("describe", "v1.2.0-1-g8ba67ed3")).versionLine()).isEqualTo("1.2.0 + 1 commit");
    }

    @Test void withoutAReachableTagThereIsNoVersion() {
        var build = BuildInfo.of(facts());
        assertThat(build.version()).isEmpty();
        assertThat(build.versionLine()).isEmpty();
        assertThat(build.summary()).isEqualTo("development build (main @ 8ba67ed3)");
    }

    @Test void aDetachedHeadShowsTheHashAlone() {
        var build = BuildInfo.of(facts("branch", "HEAD"));
        assertThat(build.branch()).isEmpty();
        assertThat(build.location()).isEqualTo("8ba67ed3");
        assertThat(build.commitLine(ZoneOffset.UTC)).isEqualTo("Commit " + COMMIT + "; committed 2026-10-13");
    }

    @Test void onlyReleaseTagsCountAndTheHighestWins() {
        var build = BuildInfo.of(facts("headTags", "nightly v1.2.0 v1.10.0 v01.2.3 v1.3.0-rc1"));
        assertThat(build.release()).isTrue();
        assertThat(build.version()).contains("1.10.0");
        assertThat(BuildInfo.of(facts("headTags", "pre-author-rewrite-main nightly")).release()).isFalse();
    }

    @Test void anEmptyCommitOrAMissingResourceIsUnknown() {
        assertThat(BuildInfo.of(Map.of())).isEqualTo(BuildInfo.UNKNOWN);
        assertThat(BuildInfo.of(facts("commit", ""))).isEqualTo(BuildInfo.UNKNOWN);
        assertThat(BuildInfo.read(null)).isEqualTo(BuildInfo.UNKNOWN);
        assertThat(BuildInfo.UNKNOWN.known()).isFalse();
        assertThat(BuildInfo.UNKNOWN.release()).isFalse();
        assertThat(BuildInfo.UNKNOWN.versionLine()).isEmpty();
        assertThat(BuildInfo.UNKNOWN.location()).isEqualTo("commit unknown");
        assertThat(BuildInfo.UNKNOWN.summary()).isEqualTo("development build (commit unknown)");
        assertThat(BuildInfo.UNKNOWN.commitLine(ZoneOffset.UTC)).isEqualTo("Commit unknown");
        assertThat(BuildInfo.UNKNOWN.committedLine(ZoneOffset.UTC)).isEmpty();
    }

    @Test void datesAreShownInTheGivenZone() {
        var build = BuildInfo.of(facts("dirty", "true"));
        assertThat(build.committedLine(ZoneOffset.UTC)).isEqualTo("Committed 13 Oct 2026");
        assertThat(build.commitLine(ZoneOffset.UTC)).isEqualTo("Commit " + COMMIT + " on main, modified; committed 2026-10-13");
        var late = BuildInfo.of(facts("commitDate", "2026-10-13T23:30:00-04:00"));
        assertThat(late.committedLine(ZoneOffset.UTC)).isEqualTo("Committed 14 Oct 2026");
    }

    @Test void anUnreadableDateIsLeftOut() {
        var build = BuildInfo.of(facts("commitDate", "yesterday"));
        assertThat(build.committed()).isEmpty();
        assertThat(build.committedLine(ZoneOffset.UTC)).isEmpty();
        assertThat(build.commitLine(ZoneOffset.UTC)).isEqualTo("Commit " + COMMIT + " on main");
    }

    @Test void theResourceIsReadAsUtf8WithItsEscapes() {
        String text = "commit=" + COMMIT + "\nbranch=claude/über\n";
        assertThat(BuildInfo.read(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8))).branch()).isEqualTo("claude/über");
        String escaped = "commit=" + COMMIT + "\nbranch=a\\\\b\n";
        assertThat(BuildInfo.read(new ByteArrayInputStream(escaped.getBytes(StandardCharsets.UTF_8))).branch()).isEqualTo("a\\b");
    }

    @Test void theBuildWritesTheResource() {
        assertThat(BuildInfo.class.getResource(BuildInfo.RESOURCE)).isNotNull();
    }
}
```

- [ ] **Step 2: Run the tests to see them fail**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.platform.BuildInfoTest'`
Expected: FAIL to compile, "cannot find symbol: class BuildInfo".

- [ ] **Step 3: Write BuildInfo**

Create `jasper-app/src/main/java/dev/jasper/app/platform/BuildInfo.java`:

```java
package dev.jasper.app.platform;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * The commit this Jasper was built from, read from {@code dev/jasper/app/build-info.properties} (written by
 * {@code gradle/build-info.gradle}), and what About and error reports call it. A release is a clean tree whose HEAD
 * carries a {@code vX.Y.Z} tag; anything else, including a missing resource or an empty commit, is a development build.
 *
 * @param release whether this is an official release
 * @param version the release's version, or for a development build the latest release tag at or behind HEAD; empty for none
 * @param commitsSince commits after {@code version}; 0 on the tagged commit itself
 * @param commit the full commit hash, empty when unknown
 * @param branch the branch, empty when unknown or detached
 * @param dirty whether the tree had changes or untracked files git does not ignore
 * @param committed when the commit was made
 */
public record BuildInfo(boolean release, Optional<String> version, int commitsSince, String commit, String branch, boolean dirty,
                        Optional<OffsetDateTime> committed) {
    static final String RESOURCE = "/dev/jasper/app/build-info.properties";
    private static final String NUMBER = "(?:0|[1-9]\\d{0,8})";
    private static final Pattern RELEASE_TAG = Pattern.compile("v(" + NUMBER + "\\." + NUMBER + "\\." + NUMBER + ")");
    private static final Pattern DESCRIBE = Pattern.compile("v(" + NUMBER + "\\." + NUMBER + "\\." + NUMBER + ")-(\\d{1,9})-g[0-9a-f]+");
    private static final Comparator<String> NUMERIC = Comparator.<String>comparingInt(version -> part(version, 0))
        .thenComparingInt(version -> part(version, 1)).thenComparingInt(version -> part(version, 2));
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);

    /** A build nothing is known about. */
    public static final BuildInfo UNKNOWN = new BuildInfo(false, Optional.empty(), 0, "", "", false, Optional.empty());

    public BuildInfo {
        Objects.requireNonNull(version, "version");
        Objects.requireNonNull(commit, "commit");
        Objects.requireNonNull(branch, "branch");
        Objects.requireNonNull(committed, "committed");
        if (commitsSince < 0) throw new IllegalArgumentException("commitsSince must not be negative");
        if (release && version.isEmpty()) throw new IllegalArgumentException("A release has a version");
    }

    /** This process's build, read once from the application jar. */
    public static BuildInfo current() { return Current.BUILD; }

    private static final class Current {
        static final BuildInfo BUILD = read(BuildInfo.class.getResourceAsStream(RESOURCE));
    }

    /** Reads and closes {@code in}; a null or unreadable stream is {@link #UNKNOWN}. */
    static BuildInfo read(InputStream in) {
        if (in == null) return UNKNOWN;
        try (in; var reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            var properties = new Properties();
            properties.load(reader);
            return of(properties.stringPropertyNames().stream().collect(Collectors.toMap(key -> key, properties::getProperty)));
        } catch (IOException | IllegalArgumentException unreadable) {
            return UNKNOWN;
        }
    }

    /**
     * The build the raw facts describe.
     *
     * @param facts {@code commit}, {@code branch}, {@code commitDate}, {@code dirty}, {@code headTags} (space-separated)
     *              and {@code describe}; a missing key counts as empty
     * @return the build, {@link #UNKNOWN} when the commit is empty
     */
    public static BuildInfo of(Map<String, String> facts) {
        String commit = fact(facts, "commit");
        if (commit.isEmpty()) return UNKNOWN;
        String branch = fact(facts, "branch");
        if (branch.equals("HEAD")) branch = "";
        boolean dirty = Boolean.parseBoolean(fact(facts, "dirty"));
        Optional<OffsetDateTime> committed;
        try {
            String date = fact(facts, "commitDate");
            committed = date.isEmpty() ? Optional.empty() : Optional.of(OffsetDateTime.parse(date));
        } catch (DateTimeParseException unreadable) {
            committed = Optional.empty();
        }
        Optional<String> tagged = Arrays.stream(fact(facts, "headTags").split("\\s+")).map(RELEASE_TAG::matcher)
            .filter(Matcher::matches).map(match -> match.group(1)).max(NUMERIC);
        if (tagged.isPresent()) return new BuildInfo(!dirty, tagged, 0, commit, branch, dirty, committed);
        Matcher described = DESCRIBE.matcher(fact(facts, "describe"));
        if (described.matches())
            return new BuildInfo(false, Optional.of(described.group(1)), Integer.parseInt(described.group(2)), commit, branch, dirty, committed);
        return new BuildInfo(false, Optional.empty(), 0, commit, branch, dirty, committed);
    }

    /** Whether anything is known about the commit. */
    public boolean known() { return !commit.isEmpty(); }

    /** The first eight characters of the commit, or "unknown". */
    public String shortCommit() { return known() ? commit.substring(0, Math.min(8, commit.length())) : "unknown"; }

    /** "1.2.0", "1.2.0 + 14 commits" or "1.2.0 + 1 commit"; empty when no version is known. */
    public String versionLine() {
        if (version.isEmpty()) return "";
        if (commitsSince == 0) return version.get();
        return version.get() + " + " + commitsSince + (commitsSince == 1 ? " commit" : " commits");
    }

    /** "main @ 8ba67ed3, modified"; the hash alone when detached; "commit unknown" when nothing is known. */
    public String location() {
        if (!known()) return "commit unknown";
        return (branch.isEmpty() ? "" : branch + " @ ") + shortCommit() + (dirty ? ", modified" : "");
    }

    /** What error reports and About's Copy call this build: "1.2.0", or "1.2.0 + 14 commits, development build (main @ 8ba67ed3)". */
    public String summary() {
        if (release) return version.orElseThrow();
        String line = versionLine();
        return (line.isEmpty() ? "" : line + ", ") + "development build (" + location() + ")";
    }

    /** "Committed 13 Oct 2026" in {@code zone}; empty when the date is unknown. */
    public String committedLine(ZoneId zone) {
        return committed.map(at -> "Committed " + DAY.format(at.atZoneSameInstant(zone))).orElse("");
    }

    /** Copy's commit line: "Commit <hash> on main, modified; committed 2026-10-13", or "Commit unknown". */
    public String commitLine(ZoneId zone) {
        if (!known()) return "Commit unknown";
        return "Commit " + commit + (branch.isEmpty() ? "" : " on " + branch) + (dirty ? ", modified" : "")
            + committed.map(at -> "; committed " + DateTimeFormatter.ISO_LOCAL_DATE.format(at.atZoneSameInstant(zone))).orElse("");
    }

    private static String fact(Map<String, String> facts, String key) {
        String value = facts.get(key);
        return value == null ? "" : value.strip();
    }

    private static int part(String version, int index) { return Integer.parseInt(version.split("\\.")[index]); }
}
```

- [ ] **Step 4: Run the tests to see them pass**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.platform.BuildInfoTest'`
Expected: PASS (11 tests).

- [ ] **Step 5: Change the error report's expectation**

In `jasper-app/src/test/java/dev/jasper/app/notifications/IncidentReportTest.java`, in `theCurrentEnvironmentDescribesThisProcess`, replace

```java
        assertThat(current.jasper()).isEqualTo(System.getProperty("jpackage.app-version", "development build"));
```

with

```java
        assertThat(current.jasper()).isEqualTo(dev.jasper.app.platform.BuildInfo.current().summary());
```

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.notifications.IncidentReportTest'`
Expected: FAIL in `theCurrentEnvironmentDescribesThisProcess` (it still reads `jpackage.app-version`).

- [ ] **Step 6: Make error reports name the build**

In `jasper-app/src/main/java/dev/jasper/app/notifications/IncidentReport.java`, change the record's `@param jasper`
line to `@param jasper Jasper's build, as {@link dev.jasper.app.platform.BuildInfo#summary()} gives it`, and replace
`current()`'s Javadoc and first argument:

```java
        /** This process's environment: its build (a release's version, or what a development build was built from). */
        public static Environment current() {
            return new Environment(dev.jasper.app.platform.BuildInfo.current().summary(),
```

(the `os` and `java` arguments stay as they are). In `jasper-app/src/main/java/dev/jasper/app/notifications/package-info.java`
change the allowed-dependencies line to:

```java
 * <p>Allowed outgoing Jasper dependencies: dev.jasper.app.lifecycle, dev.jasper.app.platform, dev.jasper.buddy.notice, dev.jasper.buddy.view.
```

- [ ] **Step 7: Run the tests to see them pass**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.notifications.*' --tests 'dev.jasper.app.platform.BuildInfoTest'`
Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add jasper-app/src/main/java/dev/jasper/app/platform/BuildInfo.java jasper-app/src/test/java/dev/jasper/app/platform/BuildInfoTest.java \
  jasper-app/src/main/java/dev/jasper/app/notifications/IncidentReport.java jasper-app/src/main/java/dev/jasper/app/notifications/package-info.java \
  jasper-app/src/test/java/dev/jasper/app/notifications/IncidentReportTest.java
git commit -m "Name the build in error reports from the recorded git facts

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 3: Take the package version from the release tag

**Files:**
- Modify: `gradle/packaging.gradle:20` (the `version` provider) and `:60-71` (`validatedVersion`'s messages)
- Modify: `gradle/build-info-test.gradle` (one assertion on this build's own facts)
- Modify: `.github/workflows/linux-packages.yml:10-12` and `:51-52`
- Modify: `docs/packaging.md:35`

**Interfaces:**
- Consumes: `packageVersion` and `buildFacts` from Task 1.

- [ ] **Step 1: Add a failing check that packaging's expression gives a valid version here**

Append to the first `doLast` of `verifyBuildInfoSupport` in `gradle/build-info-test.gradle`, before its
`logger.lifecycle` line:

```groovy
        // What gradle/packaging.gradle asks for, on this checkout: a valid X.Y.Z, and the same as packaging's own provider.
        String here = packageVersion(providers.gradleProperty('jasperVersion').orNull, buildFacts.get())
        assert here ==~ /[1-9][0-9]{0,2}\.(0|[1-9][0-9]{0,2})\.(0|[1-9][0-9]{0,4})/
        assert jasperPackageVersion.get() == here
```

Run: `./gradlew :jasper-app:verifyBuildInfoSupport`
Expected: FAIL with `Could not get unknown property 'jasperPackageVersion'`.

- [ ] **Step 2: Derive packaging's version from the facts**

In `gradle/packaging.gradle`, replace line 20

```groovy
def version = providers.gradleProperty('jasperVersion').orElse('1.0.0')
```

with

```groovy
// -PjasperVersion, else the commit's release tag, else the latest release tag behind it, else 1.0.0 (gradle/build-info.gradle).
def version = providers.provider { packageVersion(providers.gradleProperty('jasperVersion').orNull, buildFacts.get()) }
ext.jasperPackageVersion = version
```

and in `validatedVersion`, replace both occurrences of

```groovy
"Invalid -PjasperVersion '${value}'; expected X.Y.Z
```

with

```groovy
"Invalid package version '${value}' (from -PjasperVersion or a vX.Y.Z release tag); expected X.Y.Z
```

(the rest of each message stays).

- [ ] **Step 3: Run the check to see it pass**

Run: `./gradlew :jasper-app:verifyBuildInfoSupport :jasper-app:verifyPackagingSupport`
Expected: PASS.

- [ ] **Step 4: Let the Linux packaging workflow take the tag's version**

In `.github/workflows/linux-packages.yml`, replace

```yaml
      version:
        description: Package version (X.Y.Z)
        default: 1.0.0
```

with

```yaml
      version:
        description: Package version (X.Y.Z); leave empty to take it from the commit's release tag
        required: false
```

and replace

```yaml
          JASPER_VERSION: ${{ inputs.version || '1.0.0' }}
        run: ./gradlew :jasper-app:packageDist "-PjasperVersion=$JASPER_VERSION" --continue --stacktrace
```

with

```yaml
          JASPER_VERSION: ${{ inputs.version }}
        run: ./gradlew :jasper-app:packageDist ${JASPER_VERSION:+"-PjasperVersion=$JASPER_VERSION"} --continue --stacktrace
```

- [ ] **Step 5: Document the version source**

In `docs/packaging.md`, replace the sentence ``` `jasperVersion` defaults to `1.0.0`. ``` (line 35) with:

```markdown
Without `-PjasperVersion`, the version is the commit's `vX.Y.Z` release tag, else the latest release tag behind it,
else `1.0.0`; on a commit with a release tag, a different `-PjasperVersion` fails the build.
```

(the following "It accepts three decimal components…" sentence stays).

- [ ] **Step 6: Commit**

```bash
git add gradle/packaging.gradle gradle/build-info-test.gradle .github/workflows/linux-packages.yml docs/packaging.md
git commit -m "Take the package version from the release tag

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 4: Jasper's license, the third-party notices and their completeness check

**Files:**
- Create: `LICENSE.md`
- Modify: `README.md` (License section at the end)
- Create: `jasper-app/src/main/resources/dev/jasper/app/about/licenses/notices.toml` and the text files listed in Step 3
- Create: `gradle/third-party-notices.gradle.kts`
- Modify: `build.gradle.kts` (apply it, after `gradle/sdk-release.gradle.kts`)
- Modify: `jasper-app/build.gradle.kts` (copy `LICENSE.md` into resources; add it to the distributions)

**Interfaces:**
- Produces the classpath resources `dev/jasper/app/about/licenses/notices.toml`, `.../jasper.md` (the copy of
  `LICENSE.md`) and the texts the index names, for Task 5.

The three downloads in Steps 1 and 3 need the user's approval before running (they are listed in the handoff).

- [ ] **Step 1: Write LICENSE.md**

```bash
curl -fsSL https://raw.githubusercontent.com/polyformproject/polyform-licenses/1.0.0/PolyForm-Noncommercial-1.0.0.md -o LICENSE.md
printf '\nRequired Notice: Copyright 2026 Dustin McKenna-Watts (https://github.com/an0nn30/Jasper)\n' >> LICENSE.md
head -1 LICENSE.md
tail -1 LICENSE.md
```

Expected: the first line is `# PolyForm Noncommercial License 1.0.0`; the last is the Required Notice line.

- [ ] **Step 2: Write the failing completeness check**

Create `gradle/third-party-notices.gradle.kts`:

```kotlin
import org.gradle.api.artifacts.component.ModuleComponentIdentifier

// Every external jar Jasper or a bundled plugin ships has a component in the third-party notices index, and every file
// the index names exists. The index is Jasper's own format (notices.toml); ThirdPartyNotices reads it with tomlj at
// runtime, and this check reads the few fields it needs with regular expressions, as plugin-packaging reads plugin.toml.
val noticesApp = project(":jasper-app")
val verifyThirdPartyNotices = tasks.register("verifyThirdPartyNotices") {
    group = "verification"
    description = "Checks that every shipped third-party jar has a license entry and that every named license file exists."
    val index = noticesApp.file("src/main/resources/dev/jasper/app/about/licenses/notices.toml")
    inputs.file(index)
    doLast {
        val modules = listOf(noticesApp) + rootProject.subprojects.filter { it.path.startsWith(":jasper-plugin-") }
        val shipped = modules.flatMap { module ->
            module.configurations.getByName("runtimeClasspath").incoming.artifacts.artifacts.mapNotNull { artifact ->
                (artifact.id.componentIdentifier as? ModuleComponentIdentifier)?.let { "${it.group}:${it.module}:${it.version}" }
            }
        }.toSortedSet()
        val resources = listOf(noticesApp.file("src/main/resources"), project(":jasper-ui").file("src/main/resources"))
        fun field(block: String, key: String) = Regex("(?m)^\\s*$key\\s*=\\s*\"([^\"]*)\"").find(block)?.groupValues?.get(1)
        val problems = mutableListOf<String>()
        val listed = mutableListOf<String>()
        for (block in index.readText().split("[[component]]").drop(1)) {
            val name = field(block, "name") ?: "(unnamed component)"
            Regex("(?s)modules\\s*=\\s*\\[(.*?)]").find(block)?.groupValues?.get(1)?.let { list ->
                Regex("\"([^\"]+)\"").findAll(list).forEach { listed.add(it.groupValues[1]) }
            }
            for (key in listOf("text", "notice")) {
                val path = field(block, key)
                if (path == null) {
                    if (key == "text") problems.add("$name has no text")
                    continue
                }
                val exists = if (path.startsWith("/")) resources.any { it.resolve(path.removePrefix("/")).isFile }
                    else index.parentFile.resolve(path).isFile
                if (!exists) problems.add("$name: its $key file $path does not exist")
            }
        }
        listed.groupingBy { it }.eachCount().filterValues { it > 1 }.keys.forEach { problems.add("$it is listed twice") }
        (shipped - listed.toSet()).forEach { problems.add("$it ships but no component in notices.toml lists it") }
        (listed.toSet() - shipped).forEach { problems.add("$it is listed in notices.toml but no longer ships") }
        check(problems.isEmpty()) { "Third-party notices are incomplete:\n" + problems.joinToString("\n") }
        logger.lifecycle("Verified third-party notices for ${shipped.size} shipped modules")
    }
}
gradle.projectsEvaluated { noticesApp.tasks.named("check") { dependsOn(verifyThirdPartyNotices) } }
```

In `build.gradle.kts`, after `apply(from = "gradle/sdk-release.gradle.kts")`, add:

```kotlin
apply(from = "gradle/third-party-notices.gradle.kts")
```

Create `jasper-app/src/main/resources/dev/jasper/app/about/licenses/notices.toml` with only its header comment:

```toml
# Third-party software Jasper ships, for the Licenses window (ThirdPartyNotices) and verifyThirdPartyNotices.
# One [[component]] per project. modules are the exact group:artifact:version coordinates it covers; the check fails
# when a shipped jar is in no component or a listed one no longer ships. text and notice are paths relative to this
# folder, or absolute classpath resources. license is an SPDX id ThirdPartyNotices knows.
```

Run: `./gradlew verifyThirdPartyNotices`
Expected: FAIL, "Third-party notices are incomplete", listing 28 coordinates such as
`com.formdev:flatlaf:3.7 ships but no component in notices.toml lists it`.

- [ ] **Step 3: Gather the license texts**

Save this script as `<scratchpad>/extract-notices.py` (outside the repository; it is not committed):

```python
import pathlib, sys, zipfile

# Copies license and NOTICE texts out of the shipped jars in the Gradle cache, and the JetBrains Runtime's licence,
# into the notices folder. argv: the notices folder, the JBR home.
cache = pathlib.Path.home() / ".gradle/caches/modules-2/files-2.1"
out, jbr = pathlib.Path(sys.argv[1]), pathlib.Path(sys.argv[2])
out.mkdir(parents=True, exist_ok=True)

def write(name, text, keyword):
    text = text.replace("\r\n", "\n")
    if keyword.lower() not in text[:2000].lower():
        sys.exit(f"{name}: expected {keyword!r} near the start, found {text[:120]!r}")
    (out / name).write_text(text if text.endswith("\n") else text + "\n", encoding="utf-8")
    print("wrote", name, len(text), "characters")

def from_jar(coordinate, entry, name, keyword):
    group, artifact, version = coordinate.split(":")
    jars = sorted(cache.glob(f"{group}/{artifact}/{version}/*/{artifact}-{version}.jar"))
    if not jars:
        sys.exit(f"{coordinate} is not in the Gradle cache; run ./gradlew :jasper-app:stagePlugins first")
    with zipfile.ZipFile(jars[0]) as jar:
        write(name, jar.read(entry).decode("utf-8"), keyword)

from_jar("com.formdev:flatlaf:3.7", "META-INF/LICENSE", "apache-2.0.txt", "Apache License")
from_jar("org.apache.ftpserver:ftpserver-core:1.2.1", "META-INF/NOTICE", "ftpserver.NOTICE.txt", "Apache")
from_jar("org.apache.mina:mina-core:2.2.4", "META-INF/NOTICE", "mina.NOTICE.txt", "Apache")
from_jar("org.apache.sshd:sshd-core:2.19.0", "META-INF/NOTICE", "mina-sshd.NOTICE.txt", "Apache")
from_jar("org.bouncycastle:bcprov-jdk18on:1.85.2", "META-INF/LICENSE.md", "bouncycastle.LICENSE.md", "Bouncy Castle")
from_jar("org.checkerframework:checker-qual:3.21.2", "META-INF/LICENSE.txt", "checker-qual.LICENSE.txt", "MIT")
from_jar("net.java.dev.jna:jna:5.14.0", "META-INF/LICENSE", "jna.LICENSE.txt", "Apache")
from_jar("com.github.weisj:jsvg:2.0.0", "META-INF/LICENSE", "jsvg.LICENSE.txt", "MIT")
from_jar("com.fifesoft:rsyntaxtextarea:3.6.0", "META-INF/LICENSE", "rsyntaxtextarea.LICENSE.txt", "Copyright")
from_jar("org.slf4j:slf4j-api:2.0.13", "META-INF/LICENSE.txt", "slf4j.LICENSE.txt", "Copyright")
from_jar("org.xerial:sqlite-jdbc:3.53.4.0", "META-INF/maven/org.xerial/sqlite-jdbc/LICENSE.zentus", "sqlite-jdbc.LICENSE.zentus.txt", "Copyright")
write("gpl-2.0-classpath-exception.txt", (jbr / "legal/java.base/LICENSE").read_text(encoding="utf-8"), "GNU General Public License")
```

Run:

```bash
python3 -I <scratchpad>/extract-notices.py jasper-app/src/main/resources/dev/jasper/app/about/licenses ~/Library/Java/JavaVirtualMachines/jbrsdk-25.0.4.1-osx-aarch64-b583.48/Contents/Home
curl -fsSL https://raw.githubusercontent.com/JetBrains/pty4j/master/LICENSE -o jasper-app/src/main/resources/dev/jasper/app/about/licenses/epl-1.0.txt
curl -fsSL https://raw.githubusercontent.com/antlr/antlr4/4.11.1/LICENSE.txt -o jasper-app/src/main/resources/dev/jasper/app/about/licenses/antlr4.LICENSE.txt
head -1 jasper-app/src/main/resources/dev/jasper/app/about/licenses/epl-1.0.txt
grep -c "The ANTLR Project" jasper-app/src/main/resources/dev/jasper/app/about/licenses/antlr4.LICENSE.txt
grep -c "CLASSPATH" jasper-app/src/main/resources/dev/jasper/app/about/licenses/gpl-2.0-classpath-exception.txt
```

Expected: twelve "wrote" lines; `Eclipse Public License - v 1.0`; a count of at least 1 for each grep. Open each new
file and confirm it is the license its name says (and, for MIT/BSD texts, that it carries the project's copyright line).

- [ ] **Step 4: Write the index**

Append to `notices.toml` (after the header):

```toml

[[component]]
name = "ANTLR 4 Runtime"
version = "4.11.1"
license = "BSD-3-Clause"
homepage = "https://www.antlr.org/"
modules = ["org.antlr:antlr4-runtime:4.11.1"]
text = "antlr4.LICENSE.txt"

[[component]]
name = "Apache FtpServer"
version = "1.2.1"
license = "Apache-2.0"
homepage = "https://mina.apache.org/ftpserver-project/"
modules = ["org.apache.ftpserver:ftplet-api:1.2.1", "org.apache.ftpserver:ftpserver-core:1.2.1"]
text = "apache-2.0.txt"
notice = "ftpserver.NOTICE.txt"

[[component]]
name = "Apache MINA"
version = "2.2.4"
license = "Apache-2.0"
homepage = "https://mina.apache.org/"
modules = ["org.apache.mina:mina-core:2.2.4"]
text = "apache-2.0.txt"
notice = "mina.NOTICE.txt"

[[component]]
name = "Apache MINA SSHD"
version = "2.19.0"
license = "Apache-2.0"
homepage = "https://mina.apache.org/sshd-project/"
modules = ["org.apache.sshd:sshd-common:2.19.0", "org.apache.sshd:sshd-core:2.19.0", "org.apache.sshd:sshd-sftp:2.19.0"]
text = "apache-2.0.txt"
notice = "mina-sshd.NOTICE.txt"

[[component]]
name = "Bouncy Castle"
version = "1.85"
license = "MIT"
homepage = "https://www.bouncycastle.org/"
modules = ["org.bouncycastle:bcprov-jdk18on:1.85.2", "org.bouncycastle:bcpkix-jdk18on:1.85", "org.bouncycastle:bcutil-jdk18on:1.85"]
text = "bouncycastle.LICENSE.md"

[[component]]
name = "Checker Framework Qualifiers"
version = "3.21.2"
license = "MIT"
homepage = "https://checkerframework.org/"
modules = ["org.checkerframework:checker-qual:3.21.2"]
text = "checker-qual.LICENSE.txt"

[[component]]
name = "Cousine"
version = "1.241"
license = "OFL-1.1"
homepage = "https://github.com/googlefonts/cousine"
text = "/dev/jasper/app/fonts/cousine/OFL.txt"
notice = "/dev/jasper/app/fonts/cousine/NOTICE.md"

[[component]]
name = "FlatLaf"
version = "3.7"
license = "Apache-2.0"
homepage = "https://www.formdev.com/flatlaf/"
modules = ["com.formdev:flatlaf:3.7", "com.formdev:flatlaf-extras:3.7"]
text = "apache-2.0.txt"

[[component]]
name = "IntelliJ Platform icons"
version = ""
license = "Apache-2.0"
homepage = "https://github.com/JetBrains/intellij-community"
text = "/dev/jasper/ui/icons/LICENSE.txt"
notice = "/dev/jasper/ui/icons/NOTICE.txt"

[[component]]
name = "JCL over SLF4J"
version = "1.7.36"
license = "Apache-2.0"
homepage = "https://www.slf4j.org/legacy.html"
modules = ["org.slf4j:jcl-over-slf4j:1.7.36"]
text = "apache-2.0.txt"

[[component]]
name = "JediTerm"
version = "3.76"
license = "Apache-2.0"
homepage = "https://github.com/JetBrains/jediterm"
modules = ["org.jetbrains.jediterm:jediterm-core:3.76"]
text = "apache-2.0.txt"

[[component]]
name = "JetBrains Annotations"
version = "24.0.1"
license = "Apache-2.0"
homepage = "https://github.com/JetBrains/java-annotations"
modules = ["org.jetbrains:annotations:24.0.1"]
text = "apache-2.0.txt"

[[component]]
name = "JetBrains Runtime"
version = "25"
license = "GPL-2.0-only WITH Classpath-exception-2.0"
homepage = "https://github.com/JetBrains/JetBrainsRuntime"
text = "gpl-2.0-classpath-exception.txt"

[[component]]
name = "JetBrains Runtime API"
version = "1.9.0"
license = "Apache-2.0"
homepage = "https://github.com/JetBrains/JetBrainsRuntimeApi"
modules = ["org.jetbrains.runtime:jbr-api:1.9.0"]
text = "apache-2.0.txt"

[[component]]
name = "JNA"
version = "5.14.0"
license = "Apache-2.0"
homepage = "https://github.com/java-native-access/jna"
modules = ["net.java.dev.jna:jna:5.14.0", "net.java.dev.jna:jna-platform:5.14.0"]
text = "apache-2.0.txt"
notice = "jna.LICENSE.txt"

[[component]]
name = "jsvg"
version = "2.0.0"
license = "MIT"
homepage = "https://github.com/weisJ/jsvg"
modules = ["com.github.weisj:jsvg:2.0.0"]
text = "jsvg.LICENSE.txt"

[[component]]
name = "Kotlin Standard Library"
version = "2.4.0"
license = "Apache-2.0"
homepage = "https://kotlinlang.org/"
modules = ["org.jetbrains.kotlin:kotlin-stdlib:2.4.0"]
text = "apache-2.0.txt"

[[component]]
name = "Nerd Fonts"
version = "3.5.1"
license = "LicenseRef-Nerd-Fonts"
homepage = "https://www.nerdfonts.com/"
text = "/dev/jasper/app/fonts/cousine/NERD-FONTS-LICENSE.txt"

[[component]]
name = "pty4j"
version = "0.13.10"
license = "EPL-1.0"
homepage = "https://github.com/JetBrains/pty4j"
modules = ["org.jetbrains.pty4j:pty4j:0.13.10"]
text = "epl-1.0.txt"

[[component]]
name = "RSyntaxTextArea"
version = "3.6.0"
license = "BSD-3-Clause"
homepage = "https://bobbylight.github.io/RSyntaxTextArea/"
modules = ["com.fifesoft:rsyntaxtextarea:3.6.0"]
text = "rsyntaxtextarea.LICENSE.txt"

[[component]]
name = "SLF4J"
version = "2.0.13"
license = "MIT"
homepage = "https://www.slf4j.org/"
modules = ["org.slf4j:slf4j-api:2.0.13", "org.slf4j:slf4j-nop:2.0.13", "org.slf4j:slf4j-jdk14:2.0.13"]
text = "slf4j.LICENSE.txt"

[[component]]
name = "SQLite JDBC"
version = "3.53.4.0"
license = "Apache-2.0"
homepage = "https://github.com/xerial/sqlite-jdbc"
modules = ["org.xerial:sqlite-jdbc:3.53.4.0"]
text = "apache-2.0.txt"
notice = "sqlite-jdbc.LICENSE.zentus.txt"

[[component]]
name = "tomlj"
version = "1.1.1"
license = "Apache-2.0"
homepage = "https://github.com/tomlj/tomlj"
modules = ["org.tomlj:tomlj:1.1.1"]
text = "apache-2.0.txt"
```

- [ ] **Step 5: Run the check to see it pass, then see it catch a gap**

Run: `./gradlew verifyThirdPartyNotices`
Expected: PASS, "Verified third-party notices for 28 shipped modules". If a coordinate is reported (a version moved
since this plan was written), check that version's license, update the component, and record it in the status banner.

Then delete the `tomlj` component's `modules` line, run `./gradlew verifyThirdPartyNotices`, and confirm it FAILS with
`org.tomlj:tomlj:1.1.1 ships but no component in notices.toml lists it` (and, since the line names no other module,
nothing else). Restore the line and run it again: PASS.

- [ ] **Step 6: Ship Jasper's license with the app and the portable distributions**

In `jasper-app/build.gradle.kts`, inside `distributions { main { contents { ... } } }`, add as the first line of the
`contents` block:

```kotlin
    from(rootProject.file("LICENSE.md"))
```

and after that `distributions` block add:

```kotlin
// Jasper's own license has one copy, at the root; the Licenses window reads it from the jar.
tasks.named<ProcessResources>("processResources") {
    from(rootProject.file("LICENSE.md")) {
        into("dev/jasper/app/about/licenses")
        rename { "jasper.md" }
    }
}
```

Run: `./gradlew :jasper-app:processResources :jasper-app:installDist`
then `cmp LICENSE.md jasper-app/build/resources/main/dev/jasper/app/about/licenses/jasper.md && cmp LICENSE.md jasper-app/build/install/jasper-app/LICENSE.md && echo same`
Expected: `same`.

- [ ] **Step 7: Name the license in the README**

Append to `README.md`:

```markdown

## License

Jasper is licensed under the [PolyForm Noncommercial License 1.0.0](LICENSE.md).
```

- [ ] **Step 8: Run the documentation and resource scans**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.documentation.*' --tests 'dev.jasper.app.architecture.*'`
Expected: PASS (the README link resolves; no colour literal in the new resources).

- [ ] **Step 9: Commit**

```bash
git add LICENSE.md README.md build.gradle.kts gradle/third-party-notices.gradle.kts jasper-app/build.gradle.kts \
  jasper-app/src/main/resources/dev/jasper/app/about/licenses
git commit -m "License Jasper for non-commercial use and list the third-party software it ships

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 5: Read the notices at runtime

**Files:**
- Create: `jasper-app/src/main/java/dev/jasper/app/about/package-info.java`
- Create: `jasper-app/src/main/java/dev/jasper/app/about/ThirdPartyNotices.java`
- Create: `jasper-app/src/test/java/dev/jasper/app/about/ThirdPartyNoticesTest.java`
- Modify: `docs/app-architecture.md` (one package table row after `notificationui`)

**Interfaces:**
- Consumes: the resources from Task 4.
- Produces (`dev.jasper.app.about.ThirdPartyNotices`):
  - `public record Component(String name, String version, String license, String homepage, List<String> modules, String text, Optional<String> notice)`
    with `String licenseName()` and `String title()` (`"FlatLaf 3.7"`, or the name alone when the version is empty)
  - `static Component jasper(String version)`; `static List<Component> listed(String jasperVersion)` (Jasper first, then
    by name ignoring case); `static List<Component> thirdParty()`; package-private `static List<Component> parse(String toml)`;
    `static String read(String path)` (throws `IllegalStateException` when missing)

- [ ] **Step 1: Write the failing tests**

Create `jasper-app/src/test/java/dev/jasper/app/about/ThirdPartyNoticesTest.java`:

```java
package dev.jasper.app.about;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ThirdPartyNoticesTest {
    private static final Path ROOT = Path.of(System.getProperty("jasper.projectDir", "..")).toAbsolutePath().normalize();

    @Test void everyListedTextResolves() {
        for (var component : ThirdPartyNotices.listed("1.2.0")) {
            assertThat(ThirdPartyNotices.read(component.text())).as(component.name()).isNotBlank();
            component.notice().ifPresent(notice -> assertThat(ThirdPartyNotices.read(notice)).as(component.name()).isNotBlank());
            assertThat(component.licenseName()).as(component.name()).isNotBlank();
        }
    }

    @Test void jasperComesFirstThenTheRestByNameIgnoringCase() {
        var listed = ThirdPartyNotices.listed("1.2.0 + 14 commits");
        assertThat(listed.getFirst().title()).isEqualTo("Jasper 1.2.0 + 14 commits");
        assertThat(listed.getFirst().licenseName()).isEqualTo("PolyForm Noncommercial 1.0.0");
        var rest = listed.subList(1, listed.size()).stream().map(ThirdPartyNotices.Component::name).toList();
        assertThat(rest).isSortedAccordingTo(String.CASE_INSENSITIVE_ORDER).contains("FlatLaf", "JediTerm", "jsvg");
        assertThat(ThirdPartyNotices.listed("").getFirst().title()).isEqualTo("Jasper");
    }

    @Test void jaspersTextIsTheRootLicense() throws Exception {
        String root = Files.readString(ROOT.resolve("LICENSE.md"));
        assertThat(ThirdPartyNotices.read(ThirdPartyNotices.jasper("").text())).isEqualTo(root);
        assertThat(root).startsWith("# PolyForm Noncommercial License 1.0.0")
            .endsWith("Required Notice: Copyright 2026 Dustin McKenna-Watts (https://github.com/an0nn30/Jasper)\n");
    }

    @Test void jarComponentsNameExactCoordinates() {
        for (var component : ThirdPartyNotices.thirdParty())
            assertThat(component.modules()).as(component.name()).allMatch(module -> module.matches("[^:\\s]+:[^:\\s]+:[^:\\s]+"));
    }

    @Test void theIndexReadsOptionalFieldsAndRejectsUnknownLicenses() {
        var parsed = ThirdPartyNotices.parse("""
            [[component]]
            name = "Alpha"
            license = "MIT"
            homepage = "https://alpha.example"
            text = "alpha.txt"
            """);
        assertThat(parsed).containsExactly(new ThirdPartyNotices.Component("Alpha", "", "MIT", "https://alpha.example",
            List.of(), "alpha.txt", Optional.empty()));
        assertThatThrownBy(() -> ThirdPartyNotices.parse("""
            [[component]]
            name = "Beta"
            license = "WTFPL"
            homepage = "https://beta.example"
            text = "beta.txt"
            """)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("WTFPL");
        assertThatThrownBy(() -> ThirdPartyNotices.parse("[[component]\n")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> ThirdPartyNotices.parse("[[component]]\nname = \"Gamma\"\n"))
            .isInstanceOf(IllegalStateException.class).hasMessageContaining("license");
    }

    @Test void aMissingTextIsReported() {
        assertThatThrownBy(() -> ThirdPartyNotices.read("missing.txt")).isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("missing.txt");
    }
}
```

- [ ] **Step 2: Run the tests to see them fail**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.about.*'`
Expected: FAIL to compile, "package dev.jasper.app.about does not exist" (or "cannot find symbol ThirdPartyNotices").

- [ ] **Step 3: Write the package and the reader**

Create `jasper-app/src/main/java/dev/jasper/app/about/package-info.java`:

```java
/**
 * The About and Licenses windows' content: the build's identity and Jasper's license (AboutView), the license list and
 * texts (LicensesView) and the third-party notices index (ThirdPartyNotices). The application's AboutWindows owns each
 * view's window and closes the view when its window closes. The views are EDT only; ThirdPartyNotices is pure.
 * <p>Allowed outgoing Jasper dependencies: dev.jasper.app.platform.
 * The module architecture check forbids package cycles; app types are not an external plugin API.
 */
package dev.jasper.app.about;
```

Create `jasper-app/src/main/java/dev/jasper/app/about/ThirdPartyNotices.java`:

```java
package dev.jasper.app.about;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.tomlj.Toml;
import org.tomlj.TomlArray;
import org.tomlj.TomlTable;

/**
 * Jasper's license and the third-party software it ships, from {@code licenses/notices.toml} beside this class
 * ({@code verifyThirdPartyNotices} keeps it complete). Texts are read when asked for. Pure; any thread.
 */
public final class ThirdPartyNotices {
    private static final String FOLDER = "/dev/jasper/app/about/licenses/";
    /** The SPDX ids the index may use, with the names the Licenses window shows. */
    private static final Map<String, String> LICENSES = Map.of(
        "PolyForm-Noncommercial-1.0.0", "PolyForm Noncommercial 1.0.0",
        "Apache-2.0", "Apache License 2.0",
        "MIT", "MIT License",
        "BSD-3-Clause", "BSD 3-Clause License",
        "EPL-1.0", "Eclipse Public License 1.0",
        "OFL-1.1", "SIL Open Font License 1.1",
        "GPL-2.0-only WITH Classpath-exception-2.0", "GNU GPL v2 with the Classpath Exception",
        "LicenseRef-Nerd-Fonts", "Nerd Fonts licenses");

    private ThirdPartyNotices() {}

    /**
     * One licensed component.
     *
     * @param name what it is called
     * @param version its version, empty when it has none (bundled artwork)
     * @param license its SPDX id, one this class knows
     * @param homepage where it comes from
     * @param modules the exact {@code group:artifact:version} coordinates it covers; empty for one that is not a jar
     * @param text its license text: a path relative to the licenses folder, or an absolute classpath resource
     * @param notice its NOTICE text, the same way, when it has one
     */
    public record Component(String name, String version, String license, String homepage, List<String> modules, String text,
                            Optional<String> notice) {
        public Component {
            requireText(name, "name");
            Objects.requireNonNull(version, "version");
            if (!LICENSES.containsKey(license)) throw new IllegalArgumentException("Unknown license " + license + " for " + name);
            requireText(homepage, "homepage");
            modules = List.copyOf(modules);
            requireText(text, "text");
            Objects.requireNonNull(notice, "notice");
        }

        /** The license's name as the Licenses window shows it. */
        public String licenseName() { return LICENSES.get(license); }

        /** The name and version, as a list row and the detail heading show them. */
        public String title() { return version.isEmpty() ? name : name + " " + version; }
    }

    /** Jasper itself, at {@code version} (empty when the build knows none). */
    public static Component jasper(String version) {
        return new Component("Jasper", version, "PolyForm-Noncommercial-1.0.0", "https://github.com/an0nn30/Jasper", List.of(),
            "jasper.md", Optional.empty());
    }

    /** Jasper first, then the third-party components by name, ignoring case. */
    public static List<Component> listed(String jasperVersion) {
        var out = new ArrayList<Component>();
        out.add(jasper(jasperVersion));
        out.addAll(thirdParty().stream().sorted(Comparator.comparing(Component::name, String.CASE_INSENSITIVE_ORDER)).toList());
        return List.copyOf(out);
    }

    /**
     * The index's components, in file order.
     *
     * @throws IllegalStateException when the index is missing or malformed
     */
    public static List<Component> thirdParty() { return parse(read("notices.toml")); }

    static List<Component> parse(String toml) {
        var result = Toml.parse(toml);
        if (result.hasErrors()) throw new IllegalStateException("notices.toml: " + result.errors().getFirst());
        TomlArray components = result.getArrayOrEmpty("component");
        var out = new ArrayList<Component>();
        for (int i = 0; i < components.size(); i++) {
            TomlTable table = components.getTable(i);
            TomlArray modules = table.getArrayOrEmpty("modules");
            var coordinates = new ArrayList<String>();
            for (int j = 0; j < modules.size(); j++) coordinates.add(modules.getString(j));
            out.add(new Component(required(table, "name"), table.getString("version", () -> ""), required(table, "license"),
                required(table, "homepage"), coordinates, required(table, "text"), Optional.ofNullable(table.getString("notice"))));
        }
        return List.copyOf(out);
    }

    /**
     * A text the index names.
     *
     * @param path relative to the licenses folder, or an absolute classpath resource
     * @throws IllegalStateException when it is not on the classpath
     */
    public static String read(String path) {
        String resource = path.startsWith("/") ? path : FOLDER + path;
        try (InputStream in = ThirdPartyNotices.class.getResourceAsStream(resource)) {
            if (in == null) throw new IllegalStateException("Missing license text " + resource);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    private static String required(TomlTable table, String key) {
        String value = table.getString(key);
        if (value == null) throw new IllegalStateException("notices.toml: a component has no " + key);
        return value;
    }

    private static void requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
    }
}
```

- [ ] **Step 4: Run the tests to see them pass**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.about.*'`
Expected: PASS (6 tests).

- [ ] **Step 5: Add the package to the architecture table**

In `docs/app-architecture.md`, after the `notificationui` row, add:

```markdown
| `about` | The About and Licenses windows' views and the third-party notices index read from resources. Application's AboutWindows owns both windows and closes each view with its window. |
```

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.documentation.*'`
Expected: PASS (the new package has its package-info).

- [ ] **Step 6: Commit**

```bash
git add jasper-app/src/main/java/dev/jasper/app/about jasper-app/src/test/java/dev/jasper/app/about docs/app-architecture.md
git commit -m "Read the license notices for the Licenses window

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 6: The About view

**Files:**
- Create: `jasper-app/src/main/java/dev/jasper/app/about/AboutView.java`
- Create: `jasper-app/src/test/java/dev/jasper/app/about/AboutViewTest.java`

**Interfaces:**
- Consumes: `BuildInfo` (Task 2); `ApplicationIcon.images()` (existing, `dev.jasper.app.platform`).
- Produces (`dev.jasper.app.about.AboutView extends JPanel`):
  - `public record Hooks(BuildInfo build, String os, String java, ZoneId zone, Predicate<String> copy, Runnable licenses, Runnable close, BiFunction<Duration, Runnable, Runnable> after)`
  - `public AboutView(KitContext kit, Hooks hooks)`; `public void close()` (idempotent)
  - kit node ids (package-private constants) `NAME = "about.name"`, `VERSION = "about.version"`, `BADGE = "about.badge"`,
    `COMMITTED = "about.committed"`, `LICENSE = "about.license"`, `COPYRIGHT = "about.copyright"`, buttons `LICENSES = "licenses"`
    and `COPY = "copy"`; Task 8 finds the Licenses… button by the name `"licenses"`.

- [ ] **Step 1: Write the failing tests**

Create `jasper-app/src/test/java/dev/jasper/app/about/AboutViewTest.java`:

```java
package dev.jasper.app.about;

import dev.jasper.app.platform.BuildInfo;
import dev.jasper.app.testsupport.EdtTestExtension;
import dev.jasper.app.testsupport.KitTestSupport;
import java.awt.Component;
import java.awt.Container;
import java.time.Duration;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.swing.AbstractButton;
import javax.swing.JTextArea;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(EdtTestExtension.class)
class AboutViewTest {
    private static final String COMMIT = "8ba67ed3" + "0".repeat(32);
    private final List<String> copied = new ArrayList<>();
    private final List<Duration> delays = new ArrayList<>();
    private final List<Runnable> pending = new ArrayList<>();
    private int licenses, closes, cancels;
    private boolean clipboardWorks = true;
    private final List<AboutView> opened = new ArrayList<>();

    @AfterEach void closeViews() { opened.forEach(AboutView::close); }

    private static BuildInfo build(String... pairs) {
        var facts = new HashMap<>(Map.of("commit", COMMIT, "branch", "main", "commitDate", "2026-10-13T14:02:11-04:00",
            "dirty", "false", "headTags", "", "describe", ""));
        for (int i = 0; i < pairs.length; i += 2) facts.put(pairs[i], pairs[i + 1]);
        return BuildInfo.of(facts);
    }

    private AboutView view(BuildInfo build) {
        var view = new AboutView(KitTestSupport.context(), new AboutView.Hooks(build, "TestOS 1 (arm)", "TestJava 25 (Vendor)",
            ZoneOffset.UTC, text -> clipboardWorks && copied.add(text), () -> licenses++, () -> closes++, (delay, task) -> {
                delays.add(delay);
                pending.add(task);
                return () -> cancels++;
            }));
        opened.add(view);
        view.setSize(340, 400);
        for (int i = 0; i < 3; i++) KitTestSupport.layout(view);
        return view;
    }

    private static <T extends Component> T first(Component root, Class<T> type) {
        if (type.isInstance(root)) return type.cast(root);
        if (root instanceof Container container) for (Component child : container.getComponents()) {
            T found = first(child, type);
            if (found != null) return found;
        }
        return null;
    }

    private static AbstractButton copyButton(AboutView view) { return KitTestSupport.require(view, AboutView.COPY, AbstractButton.class); }

    @Test void aReleaseShowsItsVersionWithoutTheChip() {
        var view = view(build("headTags", "v1.2.0", "describe", "v1.2.0-0-g8ba67ed3"));
        assertThat(KitTestSupport.texts(view)).contains("Jasper", "1.2.0", "main @ 8ba67ed3", "Committed 13 Oct 2026",
            "PolyForm Noncommercial 1.0.0", "Copyright 2026 Dustin McKenna-Watts");
        assertThat(KitTestSupport.require(view, AboutView.LICENSES, AbstractButton.class).getText()).isEqualTo("Licenses…");
        assertThat(copyButton(view).getText()).isEqualTo("Copy");
        assertThat(KitTestSupport.find(view, AboutView.BADGE, Component.class)).isNull();
    }

    @Test void aDevelopmentBuildShowsTheChipItsVersionAndWhereItWasBuilt() {
        var view = view(build("describe", "v1.2.0-14-g8ba67ed3", "dirty", "true"));
        assertThat(KitTestSupport.texts(view)).contains("1.2.0 + 14 commits", "Development build", "main @ 8ba67ed3, modified");
        assertThat(KitTestSupport.find(view, AboutView.BADGE, Component.class)).isNotNull();
    }

    @Test void anUnknownBuildShowsNoVersionOrDateAndSaysSo() {
        var view = view(BuildInfo.UNKNOWN);
        assertThat(KitTestSupport.find(view, AboutView.VERSION, Component.class)).isNull();
        assertThat(KitTestSupport.find(view, AboutView.COMMITTED, Component.class)).isNull();
        assertThat(KitTestSupport.texts(view)).contains("Development build", "commit unknown");
    }

    @Test void theLocationIsReadOnlySelectableText() {
        var view = view(build());
        JTextArea location = first(view, JTextArea.class);
        assertThat(location.getText()).isEqualTo("main @ 8ba67ed3");
        assertThat(location.isEditable()).isFalse();
        location.selectAll();
        assertThat(location.getSelectedText()).isEqualTo("main @ 8ba67ed3");
    }

    @Test void theIconIsMountedAtSixtyFourPixels() {
        var view = view(build());
        var icon = first(view, AboutView.IconCanvas.class);
        assertThat(icon).isNotNull();
        assertThat(icon.getPreferredSize().width).isEqualTo(com.formdev.flatlaf.util.UIScale.scale(64));
    }

    @Test void copyPutsTheBuildOnTheClipboardAndSaysCopiedForTwoSeconds() {
        var view = view(build("describe", "v1.2.0-14-g8ba67ed3", "dirty", "true"));
        copyButton(view).doClick();
        assertThat(copied).containsExactly("""
            Jasper 1.2.0 + 14 commits, development build (main @ 8ba67ed3, modified)
            Commit %s on main, modified; committed 2026-10-13
            OS: TestOS 1 (arm)
            Java: TestJava 25 (Vendor)
            """.formatted(COMMIT));
        assertThat(copyButton(view).getText()).isEqualTo("Copied");
        assertThat(delays).containsExactly(Duration.ofSeconds(2));
        pending.getFirst().run();
        assertThat(copyButton(view).getText()).isEqualTo("Copy");
    }

    @Test void aReleaseCopiesItsVersionAlone() {
        var view = view(build("headTags", "v1.2.0"));
        copyButton(view).doClick();
        assertThat(copied.getFirst()).startsWith("Jasper 1.2.0\nCommit " + COMMIT + " on main; committed 2026-10-13\n");
    }

    @Test void aFailedCopyKeepsTheLabel() {
        clipboardWorks = false;
        var view = view(build());
        copyButton(view).doClick();
        assertThat(copyButton(view).getText()).isEqualTo("Copy");
        assertThat(delays).isEmpty();
    }

    @Test void aSecondCopyRestartsTheTwoSeconds() {
        var view = view(build());
        copyButton(view).doClick();
        copyButton(view).doClick();
        assertThat(cancels).isEqualTo(1);
        assertThat(delays).hasSize(2);
        pending.getLast().run();
        assertThat(copyButton(view).getText()).isEqualTo("Copy");
    }

    @Test void licensesReachesTheApplication() {
        var view = view(build());
        KitTestSupport.require(view, AboutView.LICENSES, AbstractButton.class).doClick();
        assertThat(licenses).isEqualTo(1);
    }

    @Test void closingCancelsAPendingRevert() {
        var view = view(build());
        copyButton(view).doClick();
        view.close();
        assertThat(cancels).isEqualTo(1);
        pending.getFirst().run();
        view.close();
    }
}
```

- [ ] **Step 2: Run the tests to see them fail**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.about.AboutViewTest'`
Expected: FAIL to compile, "cannot find symbol: class AboutView".

- [ ] **Step 3: Write the view**

Create `jasper-app/src/main/java/dev/jasper/app/about/AboutView.java`:

```java
package dev.jasper.app.about;

import com.formdev.flatlaf.util.UIScale;
import dev.jasper.app.platform.ApplicationIcon;
import dev.jasper.app.platform.BuildInfo;
import dev.jasper.ui.metrics.Typography;
import dev.jasper.ui.model.ControlState;
import dev.jasper.ui.model.Nodes;
import dev.jasper.ui.model.UiEvent;
import dev.jasper.ui.model.ViewSpec;
import dev.jasper.ui.swing.KitContext;
import dev.jasper.ui.swing.KitView;
import dev.jasper.ui.swing.NativeControlStyles;
import dev.jasper.ui.theme.SemanticColor;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.time.Duration;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Predicate;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JTextArea;

/**
 * The About window's content: the application icon, the name, the version line, a Development build chip on anything but
 * a release, the branch and commit as selectable text, the commit date, Jasper's license and copyright, then Licenses… and
 * Copy. One chrome kit view composes it as a compact hero form; Escape closes the window. Copy puts the build's
 * description on the clipboard and reads "Copied" for two seconds when that worked. EDT only; {@link #close} stops it.
 */
public final class AboutView extends JPanel {
    /**
     * What the view shows and asks of the application.
     *
     * @param build this build
     * @param os the operating system line error reports use
     * @param java the Java runtime line error reports use
     * @param zone the zone the commit date is shown in
     * @param copy puts text on the clipboard; false when that failed (the application has said so)
     * @param licenses opens the Licenses window
     * @param close closes the window (Escape)
     * @param after runs a task once after a delay, returning what cancels it
     */
    public record Hooks(BuildInfo build, String os, String java, ZoneId zone, Predicate<String> copy, Runnable licenses,
                        Runnable close, BiFunction<Duration, Runnable, Runnable> after) {
        public Hooks {
            Objects.requireNonNull(build, "build");
            Objects.requireNonNull(os, "os");
            Objects.requireNonNull(java, "java");
            Objects.requireNonNull(zone, "zone");
            Objects.requireNonNull(copy, "copy");
            Objects.requireNonNull(licenses, "licenses");
            Objects.requireNonNull(close, "close");
            Objects.requireNonNull(after, "after");
        }
    }

    static final String ICON = "about.icon", NAME = "about.name", VERSION = "about.version", BADGE = "about.badge",
        LOCATION = "about.location", COMMITTED = "about.committed", LICENSE = "about.license", COPYRIGHT = "about.copyright";
    static final String LICENSES = "licenses", COPY = "copy", CLOSE = "close";
    static final Duration COPIED_FOR = Duration.ofSeconds(2);
    private static final String ICON_SLOT = "about.icon.canvas", LOCATION_SLOT = "about.location.text";
    private final KitContext kit;
    private final Hooks hooks;
    private final KitView view;
    private final JTextArea location = new JTextArea();
    private final AutoCloseable theme;
    private final AutoCloseable typography;
    private Runnable revert = () -> { };
    private boolean copied, closed;

    /**
     * @param kit the chrome kit context
     * @param hooks what it shows and asks of the application
     */
    public AboutView(KitContext kit, Hooks hooks) {
        super(new BorderLayout());
        this.kit = Objects.requireNonNull(kit, "kit");
        this.hooks = Objects.requireNonNull(hooks, "hooks");
        setOpaque(false);
        location.setText(hooks.build().location());
        location.setEditable(false);
        location.setOpaque(false);
        location.setBorder(BorderFactory.createEmptyBorder());
        location.getAccessibleContext().setAccessibleName("Branch and commit");
        view = new KitView(kit, spec(), this::event);
        view.getAccessibleContext().setAccessibleName("About Jasper");
        add(view, BorderLayout.CENTER);
        view.registerContent(ICON_SLOT, new IconCanvas(), () -> { });
        view.registerContent(LOCATION_SLOT, location, () -> { });
        theme = kit.binding().subscribe(pair -> restyle());
        typography = NativeControlStyles.observeTypography(this::restyle);
        restyle();
    }

    /** The location text takes the module's small mono role and the theme's secondary text and selection colours. */
    private void restyle() {
        if (closed) return;
        location.setFont(NativeControlStyles.font(kit, Typography.Role.MONO_SMALL));
        location.setForeground(kit.theme().semantic().color(SemanticColor.TEXT_SECONDARY));
        location.setCaretColor(location.getForeground());
        location.setSelectionColor(kit.theme().semantic().color(SemanticColor.SELECTION_BG));
        location.setSelectedTextColor(kit.theme().semantic().color(SemanticColor.TEXT_PRIMARY));
        revalidate();
        repaint();
    }

    private void event(UiEvent event) {
        if (closed || !(event instanceof UiEvent.Action action)) return;
        switch (action.id()) {
            case LICENSES -> hooks.licenses().run();
            case COPY -> copy();
            case CLOSE -> hooks.close().run();
            default -> { }
        }
    }

    /** Copies the report; on success the label reads "Copied" until two seconds after the latest copy. */
    private void copy() {
        if (!hooks.copy().test(report())) return;
        revert.run();
        copied = true;
        view.update(spec());
        revert = hooks.after().apply(COPIED_FOR, () -> {
            if (closed) return;
            copied = false;
            revert = () -> { };
            view.update(spec());
        });
    }

    /** What Copy puts on the clipboard: the build, its commit, then the OS and Java lines as error reports write them. */
    String report() {
        BuildInfo build = hooks.build();
        return "Jasper " + build.summary() + "\n" + build.commitLine(hooks.zone()) + "\nOS: " + hooks.os() + "\nJava: " + hooks.java() + "\n";
    }

    /** Cancels a pending revert and closes the kit view; the window calls this when it closes. Idempotent. */
    public void close() {
        if (closed) return;
        closed = true;
        revert.run();
        try { typography.close(); } catch (Exception ignored) { /* idempotent module subscription */ }
        try { theme.close(); } catch (Exception ignored) { /* the binding outlives the view */ }
        view.close();
    }

    private ViewSpec spec() {
        BuildInfo build = hooks.build();
        var hero = new ArrayList<Nodes.Node>(List.of(
            new Nodes.Custom(ICON, ControlState.READY, ICON_SLOT, Nodes.CustomKind.PREVIEW, "Jasper"),
            text(NAME, "Jasper", Nodes.TextRole.HERO_TITLE)));
        if (!build.versionLine().isEmpty()) hero.add(text(VERSION, build.versionLine(), Nodes.TextRole.DESCRIPTION));
        var facts = new ArrayList<Nodes.Node>();
        if (!build.release()) facts.add(new Nodes.Chip(BADGE, ControlState.READY, "Development build", Nodes.Hue.AMBER));
        facts.add(new Nodes.Custom(LOCATION, ControlState.READY, LOCATION_SLOT, Nodes.CustomKind.EDITOR, "Branch and commit"));
        String committed = build.committedLine(hooks.zone());
        if (!committed.isEmpty()) facts.add(text(COMMITTED, committed, Nodes.TextRole.CAPTION));
        facts.add(text(LICENSE, "PolyForm Noncommercial 1.0.0", Nodes.TextRole.CAPTION));
        facts.add(text(COPYRIGHT, "Copyright 2026 Dustin McKenna-Watts", Nodes.TextRole.CAPTION));
        return new ViewSpec(new Nodes.Form("about", ControlState.READY, Nodes.FormKind.HERO_COMPACT, "About Jasper",
            Optional.of(new Nodes.Stack("about.hero", ControlState.READY, Nodes.LayoutRole.HERO, hero)),
            List.of(new Nodes.Stack("about.facts", ControlState.READY, Nodes.LayoutRole.HERO, facts)), Optional.empty(),
            List.of(button(LICENSES, "Licenses…"), button(COPY, copied ? "Copied" : "Copy")), CLOSE, Nodes.FormSubmission.NONE));
    }

    private static Nodes.Text text(String id, String text, Nodes.TextRole role) {
        return new Nodes.Text(id, ControlState.READY, text, role);
    }

    private static Nodes.Button button(String id, String label) {
        return new Nodes.Button(id, ControlState.READY, label, "", Nodes.ButtonKind.SECONDARY, id, "");
    }

    /** The application icon at 64 logical pixels, drawn from the largest image of the platform's set. */
    static final class IconCanvas extends JComponent {
        private static final int SIZE = 64;
        private final Image image = ApplicationIcon.images().stream().max(Comparator.comparingInt(icon -> icon.getWidth(null))).orElse(null);

        IconCanvas() {
            setOpaque(false);
            getAccessibleContext().setAccessibleName("Jasper");
        }

        @Override public Dimension getPreferredSize() { return new Dimension(UIScale.scale(SIZE), UIScale.scale(SIZE)); }
        @Override public Dimension getMinimumSize() { return getPreferredSize(); }
        @Override public Dimension getMaximumSize() { return getPreferredSize(); }

        @Override protected void paintComponent(Graphics graphics) {
            if (image == null) return;
            var g = (Graphics2D) graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                int size = Math.min(getWidth(), getHeight());
                g.drawImage(image, (getWidth() - size) / 2, (getHeight() - size) / 2, size, size, null);
            } finally {
                g.dispose();
            }
        }
    }
}
```

- [ ] **Step 4: Run the tests to see them pass**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.about.AboutViewTest'`
Expected: PASS (11 tests). If the kit rejects a node (for example a Custom inside a HERO stack), keep the layout the
spec describes, adapt the node arrangement, and record the change in the status banner.

- [ ] **Step 5: Commit**

```bash
git add jasper-app/src/main/java/dev/jasper/app/about/AboutView.java jasper-app/src/test/java/dev/jasper/app/about/AboutViewTest.java
git commit -m "Show the build, its commit and Jasper's license in an About view

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 7: The Licenses view

**Files:**
- Create: `jasper-app/src/main/java/dev/jasper/app/about/LicensesView.java`
- Create: `jasper-app/src/test/java/dev/jasper/app/about/LicensesViewTest.java`

**Interfaces:**
- Consumes: `ThirdPartyNotices.Component`, `ThirdPartyNotices.read`, `ThirdPartyNotices.listed` (Task 5).
- Produces (`dev.jasper.app.about.LicensesView extends JPanel`):
  - `public record Hooks(Consumer<URI> browse, Runnable done)`
  - `public LicensesView(KitContext kit, List<ThirdPartyNotices.Component> components, Hooks hooks)`; `public void close()`
  - the detail heading is the kit label named `"detail.title"` (Task 8 reads it).

- [ ] **Step 1: Write the failing tests**

Create `jasper-app/src/test/java/dev/jasper/app/about/LicensesViewTest.java`:

```java
package dev.jasper.app.about;

import dev.jasper.app.testsupport.EdtTestExtension;
import dev.jasper.app.testsupport.KitTestSupport;
import java.awt.Component;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.swing.AbstractButton;
import javax.swing.JLabel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(EdtTestExtension.class)
class LicensesViewTest {
    private static final ThirdPartyNotices.Component WITH_NOTICE = new ThirdPartyNotices.Component("Alpha", "1.0", "Apache-2.0",
        "https://alpha.example/project", List.of(), "apache-2.0.txt", Optional.of("mina.NOTICE.txt"));
    private static final ThirdPartyNotices.Component WITHOUT_NOTICE = new ThirdPartyNotices.Component("Beta", "", "MIT",
        "https://beta.example", List.of(), "jsvg.LICENSE.txt", Optional.empty());
    private final List<URI> browsed = new ArrayList<>();
    private final List<LicensesView> opened = new ArrayList<>();
    private int dones;

    @AfterEach void closeViews() { opened.forEach(LicensesView::close); }

    private LicensesView view(List<ThirdPartyNotices.Component> components) {
        var view = new LicensesView(KitTestSupport.context(), components, new LicensesView.Hooks(browsed::add, () -> dones++));
        opened.add(view);
        view.setSize(760, 520);
        for (int i = 0; i < 3; i++) KitTestSupport.layout(view);
        return view;
    }

    private static String title(LicensesView view) { return KitTestSupport.require(view, LicensesView.TITLE, JLabel.class).getText(); }

    @Test void jasperIsListedFirstAndShownFirst() {
        var view = view(ThirdPartyNotices.listed("1.2.0"));
        assertThat(view.rowTitle(0)).isEqualTo("Jasper 1.2.0");
        assertThat(title(view)).isEqualTo("Jasper 1.2.0");
        assertThat(KitTestSupport.require(view, LicensesView.LICENSE_NAME, JLabel.class).getText()).isEqualTo("PolyForm Noncommercial 1.0.0");
        assertThat(view.licenseText()).startsWith("# PolyForm Noncommercial License 1.0.0");
    }

    @Test void selectingAComponentShowsItsNoticeAndTextFromTheTop() {
        var view = view(List.of(WITHOUT_NOTICE, WITH_NOTICE));
        view.select(1);
        assertThat(title(view)).isEqualTo("Alpha 1.0");
        assertThat(view.noticeText()).isEqualTo(ThirdPartyNotices.read("mina.NOTICE.txt"));
        assertThat(view.licenseText()).isEqualTo(ThirdPartyNotices.read("apache-2.0.txt"));
        assertThat(view.licenseCaret()).isZero();
    }

    @Test void aComponentWithoutANoticeHasNoNoticeCardAndTheNoticeReturns() {
        var view = view(List.of(WITH_NOTICE, WITHOUT_NOTICE));
        assertThat(KitTestSupport.find(view, "notice", Component.class)).isNotNull();
        view.select(1);
        assertThat(title(view)).isEqualTo("Beta");
        assertThat(KitTestSupport.find(view, "notice", Component.class)).isNull();
        view.select(0);
        assertThat(KitTestSupport.find(view, "notice", Component.class)).isNotNull();
        assertThat(view.noticeText()).isEqualTo(ThirdPartyNotices.read("mina.NOTICE.txt"));
    }

    @Test void theHomepageLinkOpensInTheBrowser() {
        var view = view(List.of(WITH_NOTICE));
        var link = KitTestSupport.require(view, LicensesView.HOMEPAGE, AbstractButton.class);
        assertThat(link.getText()).isEqualTo("alpha.example/project");
        link.doClick();
        assertThat(browsed).containsExactly(URI.create("https://alpha.example/project"));
    }

    @Test void doneClosesTheWindow() {
        var view = view(List.of(WITH_NOTICE));
        KitTestSupport.require(view, LicensesView.DONE, AbstractButton.class).doClick();
        assertThat(dones).isEqualTo(1);
    }

    @Test void aMissingTextSaysSoInsteadOfFailing() {
        var view = view(List.of(new ThirdPartyNotices.Component("Gamma", "2", "MIT", "https://gamma.example", List.of(),
            "missing.txt", Optional.empty())));
        assertThat(view.licenseText()).contains("missing.txt");
    }

    @Test void aSelectionOutsideTheListIsIgnored() {
        var view = view(List.of(WITH_NOTICE, WITHOUT_NOTICE));
        view.select(7);
        view.select(-1);
        assertThat(title(view)).isEqualTo("Alpha 1.0");
    }
}
```

- [ ] **Step 2: Run the tests to see them fail**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.about.LicensesViewTest'`
Expected: FAIL to compile, "cannot find symbol: class LicensesView".

- [ ] **Step 3: Write the view**

Create `jasper-app/src/main/java/dev/jasper/app/about/LicensesView.java`:

```java
package dev.jasper.app.about;

import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.metrics.Typography;
import dev.jasper.ui.model.ControlState;
import dev.jasper.ui.model.ListItem;
import dev.jasper.ui.model.Nodes;
import dev.jasper.ui.model.UiEvent;
import dev.jasper.ui.model.ViewSpec;
import dev.jasper.ui.swing.KitContext;
import dev.jasper.ui.swing.KitView;
import dev.jasper.ui.swing.NativeControlStyles;
import dev.jasper.ui.theme.SemanticColor;
import java.awt.BorderLayout;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;

/**
 * The Licenses window's content: Jasper and the third-party components it ships, beside the selected one's name,
 * version, license and homepage link, then its NOTICE text when it has one and its license text. One chrome kit view
 * composes it as a manager window (the Errors window's arrangement); the texts are read-only selectable text, the
 * license scrolling inside its card. Texts are read when a component is selected. EDT only; {@link #close} stops it.
 */
public final class LicensesView extends JPanel {
    /**
     * What the view asks of the application.
     *
     * @param browse opens a homepage
     * @param done closes the window (Done and Escape)
     */
    public record Hooks(Consumer<URI> browse, Runnable done) {
        public Hooks {
            Objects.requireNonNull(browse, "browse");
            Objects.requireNonNull(done, "done");
        }
    }

    static final String LIST = "licenses", TITLE = "detail.title", LICENSE_NAME = "detail.license", HOMEPAGE = "homepage", DONE = "done";
    private static final String NOTICE_SLOT = "licenses.notice", TEXT_SLOT = "licenses.text";
    private final KitContext kit;
    private final List<ThirdPartyNotices.Component> components;
    private final Hooks hooks;
    private final KitView view;
    private final JTextArea notice = new JTextArea(), text = new JTextArea();
    private final JScrollPane textScroll;
    private final AutoCloseable theme;
    private final AutoCloseable typography;
    private int selected, shown = -1;
    /** Whether the notice and text widgets are registered with their mounted Custom slots. */
    private boolean noticeMounted, textMounted, closed;

    /**
     * @param kit the chrome kit context
     * @param components what it lists, in order; the first is selected
     * @param hooks what it asks of the application
     */
    public LicensesView(KitContext kit, List<ThirdPartyNotices.Component> components, Hooks hooks) {
        super(new BorderLayout());
        this.kit = Objects.requireNonNull(kit, "kit");
        this.components = List.copyOf(components);
        this.hooks = Objects.requireNonNull(hooks, "hooks");
        if (this.components.isEmpty()) throw new IllegalArgumentException("Nothing to list");
        setOpaque(false);
        notice.setLineWrap(true);
        notice.setWrapStyleWord(true);
        notice.getAccessibleContext().setAccessibleName("Notice");
        text.getAccessibleContext().setAccessibleName("License text");
        // A narrow preferred width: the card gives the text its width and the scroll pane shows the long lines.
        text.setColumns(1);
        for (var area : List.of(notice, text)) { area.setEditable(false); area.setOpaque(false); area.setBorder(BorderFactory.createEmptyBorder()); }
        textScroll = new JScrollPane(text, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        textScroll.setBorder(BorderFactory.createEmptyBorder());
        textScroll.setViewportBorder(BorderFactory.createEmptyBorder());
        textScroll.setOpaque(false);
        textScroll.getViewport().setOpaque(false);
        view = new KitView(kit, spec(), this::event);
        view.getAccessibleContext().setAccessibleName("Licenses");
        add(view, BorderLayout.CENTER);
        theme = kit.binding().subscribe(pair -> restyle());
        typography = NativeControlStyles.observeTypography(this::restyle);
        restyle();
        render();
    }

    /** The texts take the module's mono roles and the theme's text and selection colours. */
    private void restyle() {
        if (closed) return;
        notice.setFont(NativeControlStyles.font(kit, Typography.Role.MONO_SMALL));
        text.setFont(NativeControlStyles.font(kit, Typography.Role.MONO_SMALL));
        for (var area : List.of(notice, text)) {
            area.setForeground(kit.theme().semantic().color(SemanticColor.TEXT_PRIMARY));
            area.setCaretColor(area.getForeground());
            area.setSelectionColor(kit.theme().semantic().color(SemanticColor.SELECTION_BG));
            area.setSelectedTextColor(kit.theme().semantic().color(SemanticColor.TEXT_PRIMARY));
        }
        revalidate();
        repaint();
    }

    private void event(UiEvent event) {
        if (closed) return;
        switch (event) {
            case UiEvent.Selected chosen when chosen.id().equals(LIST) ->
                chosen.ids().stream().findFirst().map(LicensesView::index).ifPresent(this::select);
            case UiEvent.Action action -> {
                switch (action.id()) {
                    case HOMEPAGE -> hooks.browse().accept(URI.create(current().homepage()));
                    case DONE -> hooks.done().run();
                    default -> { }
                }
            }
            default -> { }
        }
    }

    /** Shows the component at {@code index}; an index outside the list is ignored. */
    void select(int index) {
        if (closed || index < 0 || index >= components.size()) return;
        selected = index;
        render();
    }

    /** Stops listening and closes the kit view; the window calls this when it closes. Idempotent. */
    public void close() {
        if (closed) return;
        closed = true;
        try { typography.close(); } catch (Exception ignored) { /* idempotent module subscription */ }
        try { theme.close(); } catch (Exception ignored) { /* the binding outlives the view */ }
        view.close();
    }

    private ThirdPartyNotices.Component current() { return components.get(selected); }

    /** Shows the list and the selection; a newly selected component's texts are read once, from the top. */
    private void render() {
        if (closed) return;
        ThirdPartyNotices.Component component = current();
        if (shown != selected) {
            notice.setText(component.notice().map(LicensesView::readOrSay).orElse(""));
            notice.setCaretPosition(0);
            text.setText(readOrSay(component.text()));
            text.setCaretPosition(0);
            text.setRows(Math.clamp(text.getLineCount(), DesignMetrics.NATIVE_BODY_VISIBLE_UNITS,
                kit.metrics().value(DesignMetrics.Role.ERRORS_TRACE_ROWS)));
            shown = selected;
        }
        view.update(spec());
        // The notice's Custom slot comes and goes with the selection; the same widget returns to it.
        if (component.notice().isPresent() && !noticeMounted) { view.registerContent(NOTICE_SLOT, notice, () -> noticeMounted = false); noticeMounted = true; }
        if (!textMounted) { view.registerContent(TEXT_SLOT, textScroll, () -> textMounted = false); textMounted = true; }
    }

    /** A text, or a line saying it is missing from this build (verifyThirdPartyNotices should have caught that). */
    private static String readOrSay(String path) {
        try {
            return ThirdPartyNotices.read(path);
        } catch (IllegalStateException missing) {
            return "This build is missing " + path + ".";
        }
    }

    private static String id(int index) { return "component:" + index; }

    private static int index(String id) {
        try {
            return Integer.parseInt(id.substring(id.indexOf(':') + 1));
        } catch (NumberFormatException | StringIndexOutOfBoundsException unknown) {
            return -1;
        }
    }

    private ViewSpec spec() {
        var items = new ArrayList<ListItem>();
        for (int i = 0; i < components.size(); i++) {
            var component = components.get(i);
            items.add(new ListItem(id(i), Optional.empty(), List.of(text("row:" + i, component.title(), Nodes.TextRole.BODY)),
                List.of(text("row.license:" + i, component.licenseName(), Nodes.TextRole.CAPTION)), List.of()));
        }
        var list = new Nodes.ItemList(LIST, ControlState.READY, Nodes.ListKind.DOUBLE, items, Set.of(id(selected)), "", List.of());
        var listPanel = new Nodes.Panel("licenses.list", ControlState.READY, hiddenHeader("licenses.list.header", "Components"),
            Optional.empty(), list, Optional.empty());
        var detailPanel = new Nodes.Panel("licenses.detail", ControlState.READY, hiddenHeader("licenses.detail.header", "License"),
            Optional.empty(), detail(), Optional.empty());
        var columns = new Nodes.Split("licenses.columns", ControlState.READY, Nodes.SplitKind.SIDEBAR_DETAIL, List.of(listPanel, detailPanel));
        return new ViewSpec(new Nodes.Form("licenses.window", ControlState.READY, Nodes.FormKind.WINDOW, "Licenses", Optional.empty(),
            List.of(columns), Optional.empty(), List.of(new Nodes.Button(DONE, ControlState.READY, "Done", "", Nodes.ButtonKind.PRIMARY, DONE, "")),
            DONE, Nodes.FormSubmission.NONE));
    }

    private Nodes.Node detail() {
        var component = current();
        var heading = new Nodes.Stack("detail.heading", ControlState.READY, Nodes.LayoutRole.NAVIGATION, List.of(
            new Nodes.Stack("detail.identity", ControlState.READY, Nodes.LayoutRole.VERTICAL, List.of(
                text(TITLE, component.title(), Nodes.TextRole.DETAIL_TITLE),
                text(LICENSE_NAME, component.licenseName(), Nodes.TextRole.DESCRIPTION))),
            new Nodes.Button(HOMEPAGE, ControlState.READY, component.homepage().replaceFirst("^https?://", ""), "",
                Nodes.ButtonKind.LINK, HOMEPAGE, component.homepage())));
        var parts = new ArrayList<Nodes.Node>(List.of(heading));
        if (component.notice().isPresent())
            parts.add(card("notice", "Notice", new Nodes.Custom("notice", ControlState.READY, NOTICE_SLOT, Nodes.CustomKind.EDITOR, "Notice")));
        parts.add(card("text", "License", new Nodes.Custom("text", ControlState.READY, TEXT_SLOT, Nodes.CustomKind.EDITOR, "License text")));
        return new Nodes.Stack("detail.content", ControlState.READY, Nodes.LayoutRole.DETAIL, parts);
    }

    /** A detail card holding one captioned value, as the Errors window's detail rows. */
    private static Nodes.Card card(String id, String caption, Nodes.Node value) {
        return new Nodes.Card("detail." + id + ".card", ControlState.READY, Nodes.CardKind.FORM, List.of(
            new Nodes.Stack("detail." + id + ".row", ControlState.READY, Nodes.LayoutRole.VERTICAL, List.of(
                text("detail." + id + ".label", caption, Nodes.TextRole.CAPTION), value))));
    }

    private static Nodes.Header hiddenHeader(String id, String title) {
        return new Nodes.Header(id, new ControlState(false, true, false, ""), title, OptionalInt.empty(), Optional.empty(), Optional.empty(), List.of());
    }

    private static Nodes.Text text(String id, String text, Nodes.TextRole role) {
        return new Nodes.Text(id, ControlState.READY, text, role);
    }

    String rowTitle(int index) {
        var form = (Nodes.Form) view.spec().root();
        var columns = (Nodes.Split) form.sections().getFirst();
        var list = (Nodes.ItemList) ((Nodes.Panel) columns.children().getFirst()).body();
        return ((Nodes.Text) list.items().get(index).title().getFirst()).text();
    }
    String noticeText() { return notice.getText(); }
    String licenseText() { return text.getText(); }
    int licenseCaret() { return text.getCaretPosition(); }
}
```

- [ ] **Step 4: Run the tests to see them pass**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.about.*'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add jasper-app/src/main/java/dev/jasper/app/about/LicensesView.java jasper-app/src/test/java/dev/jasper/app/about/LicensesViewTest.java
git commit -m "List Jasper's and the shipped components' licenses in a Licenses view

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 8: Open the windows: About Jasper action, Help menu, macOS application menu

**Files:**
- Create: `jasper-app/src/main/java/dev/jasper/app/application/AboutWindows.java`
- Create: `jasper-app/src/test/java/dev/jasper/app/application/AboutWindowsTest.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/application/JasperApplication.java` (fields near line 141 and 170;
  the macOS handlers near line 282; `startPlugins` after the `errorsWindow` assignment near line 731; the Help menu
  line near 781; a `copyBuildInformation` method beside `copyReport` near line 936; a `showAbout` method beside
  `showErrors` near line 932; shutdown near line 1472)
- Modify: `jasper-app/src/main/java/dev/jasper/app/application/package-info.java` (allowed dependencies: add `dev.jasper.app.about`)
- Modify: `jasper-app/src/main/java/dev/jasper/app/shortcuthelp/ShortcutCatalog.java:71`
- Modify: `jasper-app/src/test/java/dev/jasper/app/shortcuthelp/ShortcutHelpTest.java` (one test)
- Modify: `jasper-app/src/test/java/dev/jasper/app/application/JasperApplicationPluginsTest.java` (`theManagerIsAContributedActionInTheFileMenu`)
- Modify: `docs/configuration.md:685`

**Interfaces:**
- Consumes: `AboutView`, `LicensesView`, `ThirdPartyNotices.listed` (Tasks 5–7); `BuildInfo.current()` (Task 2);
  `IncidentReport.Environment.current()`; `AuxiliaryWindows.window(String, String, Dimension, boolean)`.
- Produces (`dev.jasper.app.application.AboutWindows`, package-private):
  - constants `ABOUT = "app.about"`, `ABOUT_ID = "jasper.about"`, `LICENSES_ID = "jasper.licenses"`, `ABOUT_SIZE`, `LICENSES_SIZE`
  - `record Hooks(BuildInfo build, IncidentReport.Environment environment, ZoneId zone, Predicate<String> copy, Consumer<URI> browse, BiFunction<Duration, Runnable, Runnable> after)`
  - `AboutWindows(Contributions, AuxiliaryWindows, Supplier<KitContext>, Hooks)`; `static List<MenuEntry> helpEntries(boolean macOs)`;
    `void showAbout()`; `void showLicenses()`; `Optional<AuxiliarySurface> aboutSurface()`, `licensesSurface()`

- [ ] **Step 1: Write the failing tests**

Create `jasper-app/src/test/java/dev/jasper/app/application/AboutWindowsTest.java`:

```java
package dev.jasper.app.application;

import dev.jasper.app.contributions.Contributions;
import dev.jasper.app.contributions.MenuEntry;
import dev.jasper.app.notifications.IncidentReport;
import dev.jasper.app.persistence.UiState;
import dev.jasper.app.platform.BuildInfo;
import dev.jasper.app.testsupport.EdtTestExtension;
import dev.jasper.app.testsupport.KitTestSupport;
import dev.jasper.app.windows.AuxiliarySurface;
import dev.jasper.app.windows.AuxiliaryWindows;
import java.awt.Rectangle;
import java.net.URI;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import javax.swing.AbstractButton;
import javax.swing.JLabel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(EdtTestExtension.class)
class AboutWindowsTest {
    private final List<String> shell = new ArrayList<>();
    private final List<URI> browsed = new ArrayList<>();
    private final Contributions contributions = new Contributions();
    private final AuxiliaryWindows windows = new AuxiliaryWindows(UiState.inMemory(), surface -> new AuxiliarySurface.Shell(
        () -> shell.add("show " + surface.id()), () -> shell.add("front " + surface.id()), () -> shell.add("dispose " + surface.id()),
        title -> { }, () -> new Rectangle(0, 0, 760, 520), (title, initial) -> Optional.empty()));

    private AboutWindows about(java.util.function.Supplier<dev.jasper.ui.swing.KitContext> kit) {
        return new AboutWindows(contributions, windows, kit, new AboutWindows.Hooks(
            BuildInfo.of(Map.of("commit", "8ba67ed3" + "0".repeat(32), "branch", "main", "describe", "v1.2.0-14-g8ba67ed3")),
            new IncidentReport.Environment("ignored", "TestOS 1 (arm)", "TestJava 25 (Vendor)"), ZoneOffset.UTC,
            text -> true, browsed::add, (delay, task) -> () -> { }));
    }

    private AboutWindows about() { return about(KitTestSupport::context); }

    private void invoke() {
        contributions.action(AboutWindows.ABOUT).orElseThrow().invoke(new Contributions.Invocation(UUID.randomUUID(), Optional.empty()));
    }

    @Test void aboutJasperIsAnApplicationActionThatOpensOneWindow() {
        about();
        var action = contributions.action(AboutWindows.ABOUT).orElseThrow();
        assertThat(action.title()).isEqualTo("About Jasper");
        assertThat(action.keywords()).contains("version", "commit", "build", "license", "licenses", "credits");
        invoke();
        invoke();
        assertThat(windows.open()).singleElement().satisfies(surface -> {
            assertThat(surface.id()).isEqualTo(AboutWindows.ABOUT_ID);
            assertThat(surface.title()).isEqualTo("About Jasper");
            assertThat(surface.preferredSize()).isEqualTo(AboutWindows.ABOUT_SIZE);
        });
        assertThat(shell).containsExactly("show jasper.about", "front jasper.about", "show jasper.about", "front jasper.about");
    }

    @Test void licensesOpensItsOwnWindowAndOutlivesAbout() {
        var about = about();
        about.showAbout();
        AuxiliarySurface aboutSurface = about.aboutSurface().orElseThrow();
        KitTestSupport.require(aboutSurface.holder(), "licenses", AbstractButton.class).doClick();
        AuxiliarySurface licenses = about.licensesSurface().orElseThrow();
        assertThat(licenses.id()).isEqualTo(AboutWindows.LICENSES_ID);
        assertThat(licenses.title()).isEqualTo("Licenses");
        assertThat(licenses.preferredSize()).isEqualTo(AboutWindows.LICENSES_SIZE);
        assertThat(KitTestSupport.require(licenses.holder(), "detail.title", JLabel.class).getText()).isEqualTo("Jasper 1.2.0 + 14 commits");
        aboutSurface.close();
        assertThat(about.aboutSurface()).isEmpty();
        assertThat(about.licensesSurface()).contains(licenses);
        assertThat(windows.open()).containsExactly(licenses);
    }

    @Test void reopeningAboutAfterItClosedOpensAFreshWindow() {
        var about = about();
        about.showAbout();
        var first = about.aboutSurface().orElseThrow();
        first.close();
        about.showAbout();
        assertThat(about.aboutSurface()).get().isNotSameAs(first);
        assertThat(windows.open()).hasSize(1);
    }

    @Test void aViewThatFailsToBuildLeavesNoWindowBehind() {
        boolean[] fail = {true};
        var about = about(() -> {
            if (fail[0]) { fail[0] = false; throw new IllegalStateException("no kit"); }
            return KitTestSupport.context();
        });
        assertThatThrownBy(about::showAbout).isInstanceOf(IllegalStateException.class);
        assertThat(windows.open()).isEmpty();
        about.showAbout();
        assertThat(about.aboutSurface()).isPresent();
    }

    @Test void helpListsAboutAfterKeyboardShortcutsExceptOnMacOs() {
        assertThat(AboutWindows.helpEntries(false)).containsExactly(new MenuEntry.Item("app.shortcuts"), new MenuEntry.Item("app.about"));
        assertThat(AboutWindows.helpEntries(true)).containsExactly(new MenuEntry.Item("app.shortcuts"));
    }
}
```

In `jasper-app/src/test/java/dev/jasper/app/shortcuthelp/ShortcutHelpTest.java`, add:

```java
    @Test void theApplicationsOwnActionsShareTheApplicationGroup() throws Exception {
        edt(() -> {
            var contributions = new Contributions();
            for (String id : List.of("plugins.manage", "app.shortcuts", "app.about"))
                contributions.addAction(id, id, null, List.of(), Optional.empty(), ignored -> {});
            var rows = ShortcutCatalog.rows(KeyBindings.defaults(true), contributions.actions(), Map.of(), true);
            assertThat(rows).filteredOn(row -> List.of("plugins.manage", "app.shortcuts", "app.about").contains(row.id()))
                .extracting(ShortcutEntry::group).containsOnly("Application").hasSize(3);
        });
    }
```

In `JasperApplicationPluginsTest.theManagerIsAContributedActionInTheFileMenu`, replace

```java
            assertThat(application[0].contributions().action("app.shortcuts")).isPresent();
            assertThat(application[0].contributions().menus()).anySatisfy(section -> {
                assertThat(section.target().title()).isEqualTo("Help");
                assertThat(section.entries()).contains(new MenuEntry.Item("app.shortcuts"));
            });
```

with

```java
            assertThat(application[0].contributions().action("app.shortcuts")).isPresent();
            assertThat(application[0].contributions().action(AboutWindows.ABOUT)).get()
                .satisfies(action -> assertThat(action.title()).isEqualTo("About Jasper"));
            assertThat(application[0].contributions().menus()).anySatisfy(section -> {
                assertThat(section.target().title()).isEqualTo("Help");
                assertThat(section.entries()).isEqualTo(AboutWindows.helpEntries(System.getProperty("os.name").startsWith("Mac")));
            });
```

- [ ] **Step 2: Run the tests to see them fail**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.application.AboutWindowsTest' --tests 'dev.jasper.app.shortcuthelp.ShortcutHelpTest'`
Expected: FAIL to compile, "cannot find symbol: class AboutWindows".

- [ ] **Step 3: Write the window owner**

Create `jasper-app/src/main/java/dev/jasper/app/application/AboutWindows.java`:

```java
package dev.jasper.app.application;

import dev.jasper.app.about.AboutView;
import dev.jasper.app.about.LicensesView;
import dev.jasper.app.about.ThirdPartyNotices;
import dev.jasper.app.contributions.Contributions;
import dev.jasper.app.contributions.MenuEntry;
import dev.jasper.app.notifications.IncidentReport;
import dev.jasper.app.platform.BuildInfo;
import dev.jasper.app.windows.AuxiliarySurface;
import dev.jasper.app.windows.AuxiliaryWindows;
import dev.jasper.ui.swing.KitContext;
import java.awt.Dimension;
import java.net.URI;
import java.time.Duration;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * The About and Licenses windows. About Jasper is an application action (the palette, Help on Windows and Linux, and on
 * macOS the application menu through JasperApplication); About's Licenses… opens Licenses, which stays open when About
 * closes. Each is one auxiliary window: opening it again brings it to the front. The auxiliary windows remember their
 * bounds and close them at shutdown; closing a window stops its view. EDT only.
 */
final class AboutWindows {
    static final String ABOUT = "app.about", ABOUT_ID = "jasper.about", LICENSES_ID = "jasper.licenses";
    static final Dimension ABOUT_SIZE = new Dimension(340, 400), LICENSES_SIZE = new Dimension(760, 520);

    /**
     * What the windows show and ask of the application.
     *
     * @param build this build
     * @param environment the OS and Java lines Copy adds (its Jasper line is not used)
     * @param zone the zone the commit date is shown in
     * @param copy puts text on the clipboard; false when that failed (the application has said so)
     * @param browse opens a homepage
     * @param after runs a task once after a delay, returning what cancels it
     */
    record Hooks(BuildInfo build, IncidentReport.Environment environment, ZoneId zone, Predicate<String> copy, Consumer<URI> browse,
                 BiFunction<Duration, Runnable, Runnable> after) {
        Hooks {
            Objects.requireNonNull(build, "build");
            Objects.requireNonNull(environment, "environment");
            Objects.requireNonNull(zone, "zone");
            Objects.requireNonNull(copy, "copy");
            Objects.requireNonNull(browse, "browse");
            Objects.requireNonNull(after, "after");
        }
    }

    private final AuxiliaryWindows windows;
    private final Supplier<KitContext> kit;
    private final Hooks hooks;
    private AuxiliarySurface about, licenses;

    AboutWindows(Contributions contributions, AuxiliaryWindows windows, Supplier<KitContext> kit, Hooks hooks) {
        this.windows = Objects.requireNonNull(windows, "windows");
        this.kit = Objects.requireNonNull(kit, "kit");
        this.hooks = Objects.requireNonNull(hooks, "hooks");
        contributions.addAction(ABOUT, "About Jasper", null, List.of("version", "commit", "build", "license", "licenses", "credits"),
            Optional.empty(), invocation -> showAbout());
    }

    /** Help's entries: Keyboard Shortcuts, then About Jasper except on macOS, where About is in the application menu. */
    static List<MenuEntry> helpEntries(boolean macOs) {
        var shortcuts = new MenuEntry.Item("app.shortcuts");
        return macOs ? List.of(shortcuts) : List.of(shortcuts, new MenuEntry.Item(ABOUT));
    }

    /** Opens About, or brings it forward. A view that fails to build takes its window with it. */
    void showAbout() {
        if (about == null || about.closed()) {
            AuxiliarySurface window = windows.window(ABOUT_ID, "About Jasper", ABOUT_SIZE, true);
            AboutView view;
            try {
                view = new AboutView(kit.get(), new AboutView.Hooks(hooks.build(), hooks.environment().os(), hooks.environment().java(),
                    hooks.zone(), hooks.copy(), this::showLicenses, window::close, hooks.after()));
            } catch (RuntimeException | Error failure) {
                window.close();
                throw failure;
            }
            window.setContent(view);
            window.onClosed(view::close);
            about = window;
        }
        about.show();
        about.toFront();
    }

    /** Opens Licenses, or brings it forward, with Jasper (at this build's version) first. */
    void showLicenses() {
        if (licenses == null || licenses.closed()) {
            AuxiliarySurface window = windows.window(LICENSES_ID, "Licenses", LICENSES_SIZE, true);
            LicensesView view;
            try {
                view = new LicensesView(kit.get(), ThirdPartyNotices.listed(hooks.build().versionLine()),
                    new LicensesView.Hooks(hooks.browse(), window::close));
            } catch (RuntimeException | Error failure) {
                window.close();
                throw failure;
            }
            window.setContent(view);
            window.onClosed(view::close);
            licenses = window;
        }
        licenses.show();
        licenses.toFront();
    }

    /** Test access: the open About window. */
    Optional<AuxiliarySurface> aboutSurface() { return about == null || about.closed() ? Optional.empty() : Optional.of(about); }

    /** Test access: the open Licenses window. */
    Optional<AuxiliarySurface> licensesSurface() { return licenses == null || licenses.closed() ? Optional.empty() : Optional.of(licenses); }
}
```

In `ShortcutCatalog.java:71`, change the condition to:

```java
        if (id.equals("plugins.manage") || id.equals("app.shortcuts") || id.equals("app.about")) return "Application";
```

In `jasper-app/src/main/java/dev/jasper/app/application/package-info.java`, add `dev.jasper.app.about` at the front of
the allowed-dependencies list (`Allowed outgoing Jasper dependencies: dev.jasper.app.about, dev.jasper.app.appearance, …`).

- [ ] **Step 4: Run the unit tests to see them pass**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.application.AboutWindowsTest' --tests 'dev.jasper.app.shortcuthelp.ShortcutHelpTest'`
Expected: PASS.

- [ ] **Step 5: Wire it into the application**

In `JasperApplication.java`:

1. Beside `private ErrorsWindow errorsWindow;` (line 170) add `private AboutWindows aboutWindows;`, and beside
   `private boolean preferencesHandlerInstalled;` (line 141) add `private boolean aboutHandlerInstalled;`.
2. After the `APP_PREFERENCES` block (ending near line 285), add:

```java
        // Jasper › About Jasper: this window instead of AWT's default panel.
        if (supports(Desktop.Action.APP_ABOUT)) {
            Desktop.getDesktop().setAboutHandler(event -> SwingUtilities.invokeLater(this::showAbout));
            aboutHandlerInstalled = true;
        }
```

3. In `startPlugins`, right after the `errorsWindow = new ErrorsWindow(...)` statement, add:

```java
        aboutWindows = new AboutWindows(contributions, auxiliary, () -> themes.appearance().context(dev.jasper.ui.theme.ThemeRoute.CHROME),
            new AboutWindows.Hooks(dev.jasper.app.platform.BuildInfo.current(), IncidentReport.Environment.current(),
                notificationCenter.clock().getZone(), this::copyBuildInformation, this::browse, JasperApplication::afterDelay));
```

4. Replace the Help line

```java
        contributions.addMenuSection(MenuTarget.topLevel("app.help", "Help")).set(List.of(new MenuEntry.Item("app.shortcuts")));
```

with

```java
        contributions.addMenuSection(MenuTarget.topLevel("app.help", "Help")).set(AboutWindows.helpEntries(macOs));
```

5. Beside `showErrors`, add:

```java
    /** Jasper › About Jasper on macOS: the About window, once the auxiliary windows exist. */
    private void showAbout() {
        if (aboutWindows != null && !quitting && !stopped) aboutWindows.showAbout();
    }
```

6. Beside `copyReport`, add:

```java
    /** About's Copy; false (after saying so) when the clipboard refused. */
    private boolean copyBuildInformation(String text) {
        try {
            clipboard.copy(text);
            return true;
        } catch (RuntimeException failure) {
            notificationViews.error("Could not copy the build information", failure.getMessage());
            return false;
        }
    }
```

7. In shutdown, after the `preferencesHandlerInstalled` line (near line 1472), add:

```java
        if (aboutHandlerInstalled) { Desktop.getDesktop().setAboutHandler(null); aboutHandlerInstalled = false; }
```

In `docs/configuration.md` line 685, replace
``` `"plugins.manage"` opens Settings at Plugins and `"app.shortcuts"` at Keymap; neither has a default shortcut. ```
with
``` `"plugins.manage"` opens Settings at Plugins, `"app.shortcuts"` at Keymap and `"app.about"` the About window; none has a default shortcut. ```

- [ ] **Step 6: Run the application tests to see them pass**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.application.*' --tests 'dev.jasper.app.shortcuthelp.*' --tests 'dev.jasper.app.documentation.*'`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add jasper-app/src/main/java/dev/jasper/app/application jasper-app/src/test/java/dev/jasper/app/application \
  jasper-app/src/main/java/dev/jasper/app/shortcuthelp/ShortcutCatalog.java jasper-app/src/test/java/dev/jasper/app/shortcuthelp/ShortcutHelpTest.java \
  docs/configuration.md
git commit -m "Open About Jasper from the palette, Help and the macOS application menu

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 9: Recipes, status and the full check

**Files:**
- Modify: `docs/app-maintenance.md` (two sections before `## Review checklist`)
- Modify: `docs/STATUS.md` (an entry at the top)
- Modify: this plan's status banner

- [ ] **Step 1: Add the two recipes**

In `docs/app-maintenance.md`, immediately before `## Review checklist`, add:

```markdown
## Cutting a release

Tag the release commit `vX.Y.Z` (no leading zeroes, major 1–255) on a clean tree and package from it: `packageDist`
takes the version from the tag, and About shows "Jasper X.Y.Z" without the Development build chip. Any other commit,
or a tree with changes or untracked files, is a development build named after the latest release tag behind it.
`gradle/build-info.gradle` records the git facts in the jar and `BuildInfo` derives the labels.

## Adding or bumping a dependency

Every external jar Jasper or a bundled plugin ships needs its exact `group:artifact:version` in a component of
`jasper-app/src/main/resources/dev/jasper/app/about/licenses/notices.toml`, with its license text (and its NOTICE, when
the project publishes one) beside it; `verifyThirdPartyNotices` (part of `check`) names any jar without one and any
entry that no longer ships. Read the new version's license before updating the entry.
```

- [ ] **Step 2: Run the full check**

Run: `./gradlew check --continue`
Expected: BUILD SUCCESSFUL. Count tests from the XML:

```bash
python3 - <<'PY'
import pathlib, xml.etree.ElementTree as ET
t = f = e = s = 0
for p in pathlib.Path('.').glob('**/build/test-results/test/*.xml'):
    r = ET.parse(p).getroot()
    t += int(r.get('tests', 0)); f += int(r.get('failures', 0)); e += int(r.get('errors', 0)); s += int(r.get('skipped', 0))
print(f"tests={t} failures={f} errors={e} skipped={s}")
PY
```

Expected: 0 failures, 0 errors; about 9,800 tests. Also run AGENTS.md's source hygiene snippet: no output.

- [ ] **Step 3: Record the status**

Add at the top of `docs/STATUS.md` (below the title line):

```markdown
**About screen, build information and licenses — 2026-10-13** (`claude/about-screen`, worktree
`.claude/worktrees/about-screen`; [spec](superpowers/specs/2026-10-13-about-screen-design.md),
[plan](superpowers/plans/2026-10-13-about-screen.md)). `:jasper-app:buildInfo` records the commit's git facts in
`dev/jasper/app/build-info.properties` inside the jar (ignored by test up-to-date checks); `BuildInfo` calls a clean
`vX.Y.Z`-tagged commit a release and anything else a development build ("1.2.0 + 14 commits", "main @ 8ba67ed3,
modified"); packaging takes its version from the release tag unless `-PjasperVersion` is given. Jasper is licensed under
PolyForm Noncommercial 1.0.0 (`LICENSE.md`); `notices.toml` lists the 23 third-party components it ships and
`verifyThirdPartyNotices` keeps that complete. About Jasper (palette; Help on Windows/Linux; the macOS application menu)
shows the icon, version, chip, commit, date and license with Licenses… and Copy; error reports name the build the same
way. Deviations from the spec are listed in the plan's status banner. `./gradlew check --continue`: **<N> tests,
0 failures, 0 errors, <S> skipped**. No GUI launched. GUI checks for the user: Jasper › About Jasper on macOS; Help ›
About Jasper on Windows and Linux; About and Licenses in Dark and Light; Copy (and "Copied" for two seconds); the
Licenses list and texts. Not merged or pushed.
```

Replace `<N>` and `<S>` with Step 2's counts. Update this plan's status banner to "complete" with the same counts and
any deviations met during implementation.

- [ ] **Step 4: Commit**

```bash
git add docs/app-maintenance.md docs/STATUS.md docs/superpowers/plans/2026-10-13-about-screen.md
git commit -m "Document releases, dependency notices and the About screen's status

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>"
```
