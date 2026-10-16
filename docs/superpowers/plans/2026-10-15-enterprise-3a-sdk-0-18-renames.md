# Enterprise presentation 3a: SDK 0.18 renames Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** The SDK becomes 0.18.0, a clean break: its seven renamed constants (and the kit's private names that follow
them), every in-repo manifest and guide range on `>=0.18.0, <0.19`, the `presentation.provide` constant, a
`KitCompatibility` gate that refuses a rebuilt plugin still reading a removed constant and names the SDK version that
removed it, and the re-documented contracts of spec 8.3, with no painted pixel changed.

**Architecture:** The SDK's `Nodes` enums and their jasper-ui model mirror rename in lockstep, because `KitValues`
maps them by name (and `Typography.Role.valueOf` maps text roles on by name); the kit's private names
(`Typography.Role`, the `DesignMetrics` roles, `MacTitleBar.Style`, the reconciler's `applyHero` pass) follow, so the
word hero names only `Form.hero`, the heading slot. One mechanical edit script performs the rename over every tracked
Java file and the two frozen tables keyed by role names; `PromptVocabularyTest` (jasper-app architecture tests) keeps it that
way. `KitCompatibility` keys every removed type and member by the SDK version that removed it, and a 0.17 manifest is
skipped by its range before any class is read. Nothing outside the SDK's surface changes behaviour.

**Tech Stack:** Java 25 on JetBrains Runtime 25, Swing and FlatLaf 3.7, the JDK class-file API
(`java.lang.classfile`), Gradle 9.7 (Kotlin DSL), JUnit 6.1.3 and AssertJ 3.27.7, Python 3.11+ standard library for
the mechanical edit scripts (always `python3 -I`).

**Spec:** [`docs/superpowers/specs/2026-10-15-jasper-enterprise-presentation-design.md`](../specs/2026-10-15-jasper-enterprise-presentation-design.md)
(sections 8.1, 8.3, 8.5, the constant of 4.2, the last row of 15 and row 3 of 19.1). The user split spec row 3 into
five plans on 2026-10-15: this one (3a sdk-0-18-renames), then 3b sdk-0-18-controls, 3c sdk-0-18-tables, 3d
sdk-0-18-chrome and 3e default-look; THD Sourcing Crypto's 0.18 rebuild is a hand-off note only, done separately in
its own repository. Plans 1 and 2 are merged on `main` (`995ff899f`); their `docs/STATUS.md` entries hold their
deviations and hand-offs, none of which this plan touches.

**Workspace:** worktree `/Users/RQ7RQVF/projects/moray/.claude/worktrees/enterprise-sdk-0-18`, branch
`claude/enterprise-sdk-0-18-renames`, from `main` at `995ff899f`. Run every command from the worktree root, never in
`~/projects/moray` itself. Every line number below is from `995ff899f` unless a step says otherwise, and every edit
quotes the exact text it replaces: the edit scripts stop with a message if their text is not found the stated number
of times.

## Global Constraints

- Java 25 on the JetBrains Runtime 25 through the Gradle toolchain; run `./gradlew`, never a system `gradle`, and never `./gradlew --stop` (other sessions share the daemons).
- Never launch the GUI (`:jasper-app:run`, `:jasper-app:bench`) and never train the AOT cache (AGENTS.md). `:jasper-ui:gallery` is headless (`java.awt.headless=true`) and may be run.
- Work only in the worktree above on `claude/enterprise-sdk-0-18-renames`; one commit per task; every commit message ends with the two lines `🤖 Generated with Claude Code at The Home Depot` and `Co-Authored-By: Claude <noreply@anthropic.com>` separated by a blank line. Do not push or merge.
- Zero pixel change: no metric value, type size or weight, colour or layout changes. The frozen tables `compact-roles.tsv` and `default-type.tsv` change only in their keys (`HERO_*` to `PROMPT_*`, `ICON_HERO` to `ICON_PROMPT`). Every PNG the headless gallery writes is byte-identical to the base once its file name is mapped (`form-HERO.png` is `form-PROMPT.png`), except that the five sheets that print a constant's name as a label show the new name on that line; its JSON differs only in the renamed names.
- The seven renames are exactly spec 8.1's table: `ChoiceKind.SEGMENTED` to `EXCLUSIVE`, `ButtonPresentation.CIRCULAR` to `HELP`, `FormKind.HERO` and `HERO_COMPACT` to `PROMPT` and `PROMPT_COMPACT`, `LayoutRole.HERO` to `PROMPT_HEADER`, `TextRole.HERO_TITLE` to `PROMPT_TITLE`, `IconSize.HERO` to `PROMPT`. `Toggle`, `GHOST`, `DESTRUCTIVE_GHOST`, `MONO*` and `Card` keep their names, and so do every constant's declaration order and every other constant.
- "The word hero names only `Form.hero`" (spec 8.1): the `Form` record component `hero`, its accessor and locals named `hero` keep the word; node id strings (`"form.hero.tile"`, `"unlock-hero"`) and comments about the slot ("no hero") are not names and stay; every other identifier and frozen-table key that says hero for the prompt kind is renamed.
- SDK 0.18.0 is a clean break (spec 8.5, and the MVP rule: no compatibility shims): no deprecated alias, no second name, no migration code. Every in-repo manifest is `sdk = ">=0.18.0, <0.19"`; THD's code never enters this repository.
- `presentation.provide` joins the SDK's `Capabilities` only; `DescriptorParser.CAPABILITIES` and the consent text arrive with plan 5 (spec 19.1 row 5).
- Javadoc doclint fails the build on errors: the SDK and jasper-app run `-Xdoclint:all`, jasper-ui `-Xdoclint:all,-missing`; every `{@link}` must resolve and no comment may contain a raw `<`, `>`, `&` or `->`.
- Every new or renamed candidate source under jasper-app, the SDK, the testkit or plugins gets its `docs/design/theme-engine/migration.csv` row in the same commit (`ThemeMigrationCoverageTest`).
- Documentation edits are the smallest that make each document accurate (AGENTS.md); `docs/superpowers/` history and earlier `docs/STATUS.md` entries are not rewritten.
- Source hygiene (AGENTS.md): no raw control, private-use or unpaired surrogate characters in any source file.

## Review Focus

1. A plugin built on 0.17 that claims a falsely broad range (`>=0.17.0, <0.19`) and still reads a renamed constant,
   for example through a `switch` (javac's synthetic switch map reads every case constant with `getstatic`): it is
   refused at load, before any of its classes initialise, with "references removed SDK member
   dev/jasper/sdk/ui/Nodes$ChoiceKind#SEGMENTED…; rebuild it for SDK 0.18". Task 3:
   `KitCompatibilityTest.aConstantSdkZeroEighteenRenamedIsRefusedWithTheVersionThatRemovedIt`.
2. A plugin rebuilt on 0.18 reads `EXCLUSIVE`, `HELP` and the `PROMPT*` constants, or its own enum happens to have a
   constant called `HERO`: it loads, because the gate matches exact owners and names. Task 3:
   `KitCompatibilityTest.theNamesSdkZeroEighteenGaveThemAreAdmitted`.
3. A truthful 0.17 manifest (`>=0.17.0, <0.18`) is skipped by its range with "needs SDK >=0.17.0, <0.18.0; this is
   SDK 0.18.0", and none of its classes are read. Task 3: `KitCompatibilityTest.aZeroSeventeenRangeIsSkippedBeforeItLoads`;
   Task 1: `VersionRangeTest.pluginsBuiltForOlderMinorsAreIncompatibleWithZeroEighteen`.
4. A plugin author copies a range from a guide or the example plugin: every range they can copy loads on this host.
   Task 1: `VersionRangeTest.everySdkRangeThePluginGuidesTellAuthorsToDeclareAdmitsThisSdk` (existing, now against
   0.18.0) and the example build's default `jasperSdkVersion`.
5. A rename missed in one place of a name chain (an SDK text role renamed but its `Typography.Role` not, which
   `valueOf` turns into an exception at render time), a metric role left with the old word, or a test named for the old
   kind: `PromptVocabularyTest` lists every identifier and frozen-table key that still says hero, and the existing
   `KitValuesTest` (lossless) and gallery coverage render every constant. Task 2:
   `PromptVocabularyTest.onlyTheFormSlotKeepsTheOldWord` and `KitValuesTest.zeroEighteenNamesTheExclusiveHelpAndPromptConstants`.

## Planned deviations

Record each of these in `docs/STATUS.md` (Task 5) and in this plan's status banner when it is executed.

1. **Five plans for spec row 3.** At the user's request (2026-10-15) row 3 is delivered as plans 3a to 3e; this plan
   holds 8.1, 8.3, 8.5 and the constant of 4.2. 8.2's additions, 13's default-look changes and 14.6's nouns, hues and
   SPLIT roles follow in 3b to 3e. THD Sourcing Crypto's rebuild is a hand-off note (Task 5), not a task.
2. **The capability before its key.** `Capabilities.PRESENTATION_PROVIDE` (with its `@since 0.18.0` Javadoc and the
   class Javadoc of 4.2) joins `Capabilities.ALL` now, as row 3 says; the host still refuses a manifest that declares
   it ("capabilities names an unknown capability") until plan 5 adds it to `DescriptorParser.CAPABILITIES` with the
   `presentation` key rule and the consent text.
3. **Removed types keep their version too.** `KitCompatibility` keys its four removed types and 33 members (all removed
   in 0.15) and the seven constants (removed in 0.18) by version; the message reads "references removed SDK member X;
   rebuild it for SDK 0.18" (spec 15), and "type" in place of "member" for a removed type.
4. **The re-documented contracts in `docs/plugin-ui.md`.** Spec 17 asks for 8.3 in `docs/plugin-ui.md`; it is one
   paragraph after the controls list, and the deleted checkbox rule becomes "`Toggle` (a boolean, drawn as a switch or
   a checkbox)".
5. **One id string changes.** Remote's `ExistingItemsPanel.HERO` constant (the id of the stack that fills the form's
   hero slot) becomes `HEADER` with the id `"existing.header"`; no test or other code names that id. Every other id
   string keeps its text.
6. **The guard reads every source.** `PromptVocabularyTest` reads the SDK, testkit, jasper-ui, jasper-app and plugin
   sources and the two frozen tables, so jasper-app's test task declares `jasper-ui/src`, `jasper-sdk/src`,
   `jasper-sdk-testkit/src` and every plugin's `src` as inputs (it declared only the production trees before).

---

### Task 1: SDK 0.18.0, every range, and the presentation.provide constant

`JasperSdk.VERSION` becomes `0.18.0`, `Capabilities` gains `PRESENTATION_PROVIDE` with spec 4.2's Javadoc, and every
in-repo range moves to `>=0.18.0, <0.19`: the eight bundled manifests, the example external plugin (and its build's
default `jasperSdkVersion`), and every range the three guides tell authors to declare. 0.18 is a clean break, so a
guide sentence about an older feature ("SDK 0.15.3 adds …, declare …") now names 0.18's range too, as the 0.17 bump did.
`VersionRangeTest` lists 0.17 among the incompatible minors. No constant is renamed yet.

**Files:**
- Modify: `jasper-sdk/src/main/java/dev/jasper/sdk/JasperSdk.java:6`, `jasper-sdk/src/main/java/dev/jasper/sdk/Capabilities.java:5-8,39-43`
- Modify: `plugins/{editor,fileserver,history,indexer,remote,sample,snippets,vault}/src/main/resources/plugin.toml` (the `sdk` line)
- Modify: `docs/examples/external-plugin/src/main/resources/plugin.toml:5`, `docs/examples/external-plugin/build.gradle.kts:7`
- Modify: `docs/plugin-authoring.md` (13 ranges: lines 19, 173, 325, 516, 537, 541, 955, 971, 987, 1011, 1026, 1033, 1041), `jasper-sdk/README.md` (lines 45, 56, 64, 74, 106, 123, 178, 195), `docs/sdk-icons.md:33`
- Test: `jasper-sdk/src/test/java/dev/jasper/sdk/CapabilitiesTest.java:7-14`, `jasper-sdk/src/test/java/dev/jasper/sdk/terminal/TerminalValuesTest.java:89`, `jasper-sdk/src/test/java/dev/jasper/sdk/files/FilesValuesTest.java:54`, `jasper-sdk/src/test/java/dev/jasper/sdk/palette/PaletteValuesTest.java:70`, `jasper-sdk/src/test/java/dev/jasper/sdk/ui/KitBoundaryTest.java:5`, `jasper-app/src/test/java/dev/jasper/app/plugins/VersionRangeTest.java:58-69`

**Interfaces:**
- Consumes: nothing from other tasks.
- Produces: `JasperSdk.VERSION == "0.18.0"`; `public static final String Capabilities.PRESENTATION_PROVIDE =
  "presentation.provide"`, last in `Capabilities.ALL`. Task 3's tests read `JasperSdk.VERSION` and expect the skip
  reason "needs SDK >=0.17.0, <0.18.0; this is SDK 0.18.0".

- [ ] **Step 1: Write the failing tests**

Run from the worktree root:

```bash
python3 -I - <<'PY'
"""Plan 3a Task 1 step 1: the tests that pin SDK 0.18.0 and its capability list."""
import pathlib
def edit(name, edits):
    path = pathlib.Path(name); text = path.read_text(encoding="utf-8")
    for old, new, count in edits:
        if text.count(old) != count: raise SystemExit(f"{name}: expected {count} of {old!r}, found {text.count(old)}")
        text = text.replace(old, new)
    path.write_text(text, encoding="utf-8")
for test in ["jasper-sdk/src/test/java/dev/jasper/sdk/CapabilitiesTest.java", "jasper-sdk/src/test/java/dev/jasper/sdk/files/FilesValuesTest.java",
             "jasper-sdk/src/test/java/dev/jasper/sdk/palette/PaletteValuesTest.java", "jasper-sdk/src/test/java/dev/jasper/sdk/ui/KitBoundaryTest.java"]:
    edit(test, [('isEqualTo("0.17.1")', 'isEqualTo("0.18.0")', 1)])
edit("jasper-sdk/src/test/java/dev/jasper/sdk/CapabilitiesTest.java", [
    ("    @Test void theSdkIsVersionSeventeenOneWithTheEnvironmentAndEditorCapabilities() {", "    @Test void theSdkIsVersionEighteenWithThePresentationCapability() {", 1),
    ('''        assertThat(Capabilities.ALL).endsWith(Capabilities.CLIPBOARD_READ, Capabilities.TERMINAL_ENVIRONMENT, Capabilities.EDITOR_PROVIDE);''',
     '''        assertThat(Capabilities.PRESENTATION_PROVIDE).isEqualTo("presentation.provide");
        assertThat(Capabilities.ALL).endsWith(Capabilities.CLIPBOARD_READ, Capabilities.TERMINAL_ENVIRONMENT, Capabilities.EDITOR_PROVIDE,
            Capabilities.PRESENTATION_PROVIDE);''', 1)])
edit("jasper-sdk/src/test/java/dev/jasper/sdk/terminal/TerminalValuesTest.java", [
    ('''"clipboard.read", "terminal.environment", "editor.provide");''', '''"clipboard.read", "terminal.environment", "editor.provide", "presentation.provide");''', 1)])
edit("jasper-app/src/test/java/dev/jasper/app/plugins/VersionRangeTest.java", [
    ("    @Test void pluginsBuiltForOlderMinorsAreIncompatibleWithZeroSeventeen() {", "    @Test void pluginsBuiltForOlderMinorsAreIncompatibleWithZeroEighteen() {", 1),
    ('''        assertThat(VersionRange.parse(">=0.17.0, <0.18").contains(host)).isTrue();
        assertThat(VersionRange.parse(">=0.17.1, <0.18").contains(host)).as("the Editor's range").isTrue();''',
     '''        assertThat(VersionRange.parse(">=0.17.0, <0.18").contains(host)).isFalse();
        assertThat(VersionRange.parse(">=0.17.1, <0.18").contains(host)).as("the former Editor range").isFalse();
        assertThat(VersionRange.parse(">=0.18.0, <0.19").contains(host)).as("every bundled range").isTrue();''', 1)])
PY
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :jasper-sdk:test :jasper-app:test --tests dev.jasper.app.plugins.VersionRangeTest --continue`
Expected: FAIL. `:jasper-sdk:compileTestJava` stops with "cannot find symbol … PRESENTATION_PROVIDE" in
`CapabilitiesTest`, and `VersionRangeTest.pluginsBuiltForOlderMinorsAreIncompatibleWithZeroEighteen` fails ("Expecting
value to be false but was true", the 0.17 range still admits 0.17.1).

- [ ] **Step 3: Bump the version, add the constant and move every range**

Run from the worktree root:

```bash
python3 -I - <<'PY'
"""Plan 3a Task 1 step 3: SDK 0.18.0, the presentation.provide constant and every in-repo range."""
import pathlib, re
def edit(name, edits):
    path = pathlib.Path(name); text = path.read_text(encoding="utf-8")
    for old, new, count in edits:
        if text.count(old) != count: raise SystemExit(f"{name}: expected {count} of {old!r}, found {text.count(old)}")
        text = text.replace(old, new)
    path.write_text(text, encoding="utf-8")
edit("jasper-sdk/src/main/java/dev/jasper/sdk/JasperSdk.java", [('VERSION = "0.17.1";', 'VERSION = "0.18.0";', 1)])
edit("jasper-sdk/src/main/java/dev/jasper/sdk/Capabilities.java", [
    (''' * The capability names a plugin declares in {@code plugin.toml} and the user consents to. They cover
 * effects the user cannot see; contributions to the window need none. They are not a sandbox.''',
     ''' * The capability names a plugin declares in {@code plugin.toml} and the user consents to. They cover effects the user
 * cannot see, and the roles one plugin at a time may take over ({@code editor.provide}, {@code presentation.provide});
 * contributions to the window need none. They are not a sandbox.''', 1),
    ('''    public static final String EDITOR_PROVIDE = "editor.provide";
''', '''    public static final String EDITOR_PROVIDE = "editor.provide";
    /**
     * Providing Jasper's presentation, the window chrome and controls, from the next start. One plugin at a time
     * provides it, and it needs no call: the host reads the presentation from the plugin's descriptor. @since 0.18.0
     */
    public static final String PRESENTATION_PROVIDE = "presentation.provide";
''', 1),
    ('''        TERMINAL_ENVIRONMENT, EDITOR_PROVIDE);''', '''        TERMINAL_ENVIRONMENT, EDITOR_PROVIDE, PRESENTATION_PROVIDE);''', 1)])
for plugin in ["editor", "fileserver", "history", "indexer", "remote", "sample", "snippets", "vault"]:
    path = pathlib.Path(f"plugins/{plugin}/src/main/resources/plugin.toml"); text = path.read_text(encoding="utf-8")
    new, n = re.subn(r'(?m)^sdk = ">=0\.17\.[01], <0\.18"$', 'sdk = ">=0.18.0, <0.19"', text)
    if n != 1: raise SystemExit(f"{path}: expected one 0.17 range, found {n}")
    path.write_text(new, encoding="utf-8")
edit("docs/examples/external-plugin/src/main/resources/plugin.toml", [('sdk = ">=0.17.0, <0.18"', 'sdk = ">=0.18.0, <0.19"', 1)])
edit("docs/examples/external-plugin/build.gradle.kts", [('getOrElse("0.17.1")', 'getOrElse("0.18.0")', 1)])
edit("jasper-sdk/README.md", [('Bundled manifests require `sdk = ">=0.17.0, <0.18"`, the Editor `>=0.17.1` (0.17.1 adds',
    'Bundled manifests require `sdk = ">=0.18.0, <0.19"` (0.18.0 is a breaking boundary: `ChoiceKind.SEGMENTED` is '
    '`EXCLUSIVE`, `ButtonPresentation.CIRCULAR` is `HELP`, and `FormKind.HERO`, `HERO_COMPACT`, `LayoutRole.HERO`, '
    '`TextRole.HERO_TITLE` and `IconSize.HERO` are `PROMPT`, `PROMPT_COMPACT`, `PROMPT_HEADER`, `PROMPT_TITLE` and '
    '`PROMPT`, and it adds the `presentation.provide` capability; 0.17.1 adds', 1)])
# 0.18 is a clean break: a guide's range for an older feature no longer loads, so every range in the guides is 0.18's.
expected = {"docs/plugin-authoring.md": 13, "jasper-sdk/README.md": 7, "docs/sdk-icons.md": 1}
for guide, count in expected.items():
    path = pathlib.Path(guide); text = path.read_text(encoding="utf-8")
    new, n = re.subn(r'sdk = ">=0\.1[5-7]\.[0-9], <0\.18"', 'sdk = ">=0.18.0, <0.19"', text)
    if n != count: raise SystemExit(f"{guide}: expected {count} ranges below 0.18, found {n}")
    path.write_text(new, encoding="utf-8")
PY
```

The README's line 45 is rewritten first (it held the eighth README range, with the Editor's `>=0.17.1`), so the
generic pass finds seven there. `docs/plugin-authoring.md:1019`'s open range `>=0.15.5` admits 0.18.0 and stays.

- [ ] **Step 4: Run the tests and the whole build**

Run: `git grep -n '<0\.18"' -- docs jasper-sdk plugins` and expect no output except under `docs/superpowers/` (plan and
spec history, which stays). Then run: `./gradlew check --continue`
Expected: BUILD SUCCESSFUL; `CapabilitiesTest`, `TerminalValuesTest`, `VersionRangeTest` (all five tests, including the
guide ranges and the example's default) and the staged bundled plugins' tests pass.

- [ ] **Step 5: Commit**

```bash
git add jasper-sdk/src docs/plugin-authoring.md jasper-sdk/README.md docs/sdk-icons.md docs/examples/external-plugin \
  plugins/*/src/main/resources/plugin.toml jasper-app/src/test/java/dev/jasper/app/plugins/VersionRangeTest.java
git commit -F - <<'EOF'
SDK 0.18: the version, every range and presentation.provide

JasperSdk.VERSION is 0.18.0, a breaking boundary: every bundled manifest,
the example external plugin and every range the guides tell authors to
declare move to >=0.18.0, <0.19, and VersionRangeTest lists 0.17 among the
incompatible minors. Capabilities gains presentation.provide with its
Javadoc; the host accepts it in a manifest from plan 5.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

### Task 2: The seven renames and the kit's private names

The SDK's seven constants of spec 8.1 and their jasper-ui model mirror rename in lockstep, and so do the kit-private
names that say hero for the prompt kind: `Typography.Role.HERO_TITLE`, the twenty `HERO_*` metric roles and
`ICON_HERO`, `MacTitleBar.Style.HERO`, `Stacks.HeroLayout`, `ViewReconciler.applyHero`, and the tests named for the kind
(`HeroCompositionTest` becomes `PromptCompositionTest`). The two frozen tables keyed by role names change their keys
and none of their values. `Form.hero`, the heading slot, keeps its name, and so do node id strings, except Remote's
`ExistingItemsPanel.HERO` (planned deviation 5). `PromptVocabularyTest` and an SDK test pin the result. Nothing paints
differently.

**Files:**
- Modify (by the edit script, 59 Java files): the enums in `jasper-sdk/src/main/java/dev/jasper/sdk/ui/Nodes.java:16-108` and `jasper-ui/src/main/java/dev/jasper/ui/model/Nodes.java` (same declarations); `jasper-ui/src/main/java/dev/jasper/ui/metrics/{Typography,DesignMetrics}.java`, `jasper-ui/src/main/java/dev/jasper/ui/icons/IconRegistry.java`, `jasper-ui/src/main/java/dev/jasper/ui/swing/{Buttons,Cards,EmptyStates,Forms,ItemLists,Labels,SettingRows,Stacks,TextFields,ViewReconciler}.java`; `jasper-app/src/main/java/dev/jasper/app/{about/AboutView,notificationui/ErrorsView,platform/MacTitleBar,pluginmanager/PluginDetails,settingsui/Editors,settingsui/SettingsView,windows/NativeShells}.java`; `plugins/fileserver/src/main/java/dev/jasper/fileserver/ui/ServerEditor.java`, `plugins/remote/src/main/java/dev/jasper/remote/ui/{HostEditor,HostsPanel,transfers/ExistingItemsPanel,tunnels/TunnelEditor}.java`, `plugins/vault/src/main/java/dev/jasper/vault/ui/{EditorForm,ManagerPanel,PasswordPanel}.java`; their tests, and the jasper-ui gallery and kit fixtures
- Modify: `jasper-ui/src/test/resources/dev/jasper/ui/metrics/compact-roles.tsv` (20 keys), `jasper-ui/src/test/resources/dev/jasper/ui/metrics/default-type.tsv` (1 key)
- Rename: `jasper-ui/src/test/java/dev/jasper/ui/swing/HeroCompositionTest.java` to `PromptCompositionTest.java`
- Modify: `docs/plugin-ui.md:38,41,53,56`, `docs/sdk-architecture.md:595`, `docs/ui-architecture.md:141`, `docs/app-maintenance.md:624`
- Modify: `jasper-app/build.gradle.kts:199-200` (test inputs), `docs/design/theme-engine/migration.csv` (one row appended)
- Create: `jasper-app/src/test/java/dev/jasper/app/architecture/PromptVocabularyTest.java`
- Test: `jasper-sdk/src/test/java/dev/jasper/sdk/ui/KitValuesTest.java:9` (one test)

**Interfaces:**
- Consumes: nothing from Task 1 (the renames are independent of the version).
- Produces: `Nodes.ChoiceKind.EXCLUSIVE`, `Nodes.ButtonPresentation.HELP`, `Nodes.FormKind.PROMPT` and
  `PROMPT_COMPACT`, `Nodes.LayoutRole.PROMPT_HEADER`, `Nodes.TextRole.PROMPT_TITLE`, `Nodes.IconSize.PROMPT` in both
  the SDK and `dev.jasper.ui.model.Nodes`; `Typography.Role.PROMPT_TITLE`, the `DesignMetrics.Role` constants
  `PROMPT_*` and `ICON_PROMPT`; `MacTitleBar.Style.PROMPT`; `Stacks.PromptLayout`; `ViewReconciler.applyPrompt`. Task 3's gate
  lists the old SDK names; Task 4's Javadoc describes the new ones.

- [ ] **Step 1: Write the failing tests**

Create `jasper-app/src/test/java/dev/jasper/app/architecture/PromptVocabularyTest.java`:

```java
package dev.jasper.app.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Spec 8.1: after SDK 0.18's renames the old word for the prompt kind names only {@code Form.hero}, the heading slot
 * that SHEET forms fill too. Comments and literals (node ids such as {@code "form.hero.tile"}) are not names.
 */
class PromptVocabularyTest {
    static final Path ROOT = Path.of(System.getProperty("jasper.repoRoot"));
    static final String SLOT = "hero";
    static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_$][A-Za-z0-9_$]*");
    static final Pattern WORDS = Pattern.compile("_|(?<=[a-z0-9])(?=[A-Z])|(?<=[A-Z])(?=[A-Z][a-z])");
    static final List<String> TABLES = List.of("compact-roles.tsv", "default-type.tsv");

    /** True when one of the identifier's words is the slot's word and the identifier is not the slot itself. */
    static boolean saysTheSlotWord(String identifier) {
        return !identifier.equals(SLOT) && Arrays.stream(WORDS.split(identifier)).anyMatch(word -> word.equalsIgnoreCase(SLOT));
    }

    /** The source with its comments, string and character literals and text blocks blanked out. */
    static String code(String source) {
        var out = new StringBuilder(source.length());
        int i = 0, n = source.length();
        while (i < n) {
            if (source.startsWith("\"\"\"", i)) {
                int end = source.indexOf("\"\"\"", i + 3);
                i = end < 0 ? n : end + 3;
            } else if (source.startsWith("//", i)) {
                int end = source.indexOf('\n', i);
                i = end < 0 ? n : end;
            } else if (source.startsWith("/*", i)) {
                int end = source.indexOf("*/", i + 2);
                i = end < 0 ? n : end + 2;
            } else if (source.charAt(i) == '"' || source.charAt(i) == '\'') {
                char quote = source.charAt(i);
                int j = i + 1;
                while (j < n && source.charAt(j) != quote) j += source.charAt(j) == '\\' ? 2 : 1;
                i = j + 1;
            } else {
                out.append(source.charAt(i++));
                continue;
            }
            out.append(' ');
        }
        return out.toString();
    }

    @Test void identifiersAreSplitIntoTheirWords() {
        assertThat(List.of("HERO", "HERO_TITLE", "heroTile", "HeroLayout", "applyHero", "ICON_HERO", "nestedHeroFields"))
            .allMatch(PromptVocabularyTest::saysTheSlotWord);
        assertThat(List.of(SLOT, "theRows", "otherOsc", "sphere", "HEROIC", "PROMPT_TITLE")).noneMatch(PromptVocabularyTest::saysTheSlotWord);
    }

    @Test void commentsAndLiteralsAreNotCode() {
        String source = "a /* x */ b // y\n\"z\\\"w\" 'q' \"\"\"\nv\n\"\"\" c";
        assertThat(code(source).split("\\s+")).containsExactly("a", "b", "c");
    }

    @Test void onlyTheFormSlotKeepsTheOldWord() throws IOException {
        var offenders = new TreeSet<String>();
        var roots = new ArrayList<>(Stream.of("jasper-sdk/src", "jasper-sdk-testkit/src", "jasper-ui/src", "jasper-app/src").map(ROOT::resolve).toList());
        try (var plugins = Files.list(ROOT.resolve("plugins"))) {
            plugins.map(plugin -> plugin.resolve("src")).filter(Files::isDirectory).forEach(roots::add);
        }
        for (Path root : roots) try (Stream<Path> files = Files.walk(root)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                var identifiers = IDENTIFIER.matcher(code(Files.readString(file)));
                while (identifiers.find())
                    if (saysTheSlotWord(identifiers.group())) offenders.add(ROOT.relativize(file) + ": " + identifiers.group());
            }
        }
        for (String table : TABLES)
            for (String row : Files.readAllLines(ROOT.resolve("jasper-ui/src/test/resources/dev/jasper/ui/metrics/" + table))) {
                String key = row.split("\t", 2)[0];
                if (saysTheSlotWord(key)) offenders.add(table + ": " + key);
            }
        assertThat(offenders).as("names that still use the slot's word for the prompt kind").isEmpty();
    }
}
```

Then run from the worktree root (the SDK test, the guard's row in the migration ledger, and the test task's inputs):

```bash
python3 -I - <<'PY'
"""Plan 3a Task 2 step 1: the SDK test of 0.18's names, the guard's ledger row and the test task's inputs."""
import pathlib
def edit(name, edits):
    path = pathlib.Path(name); text = path.read_text(encoding="utf-8")
    for old, new, count in edits:
        if text.count(old) != count: raise SystemExit(f"{name}: expected {count} of {old!r}, found {text.count(old)}")
        text = text.replace(old, new)
    path.write_text(text, encoding="utf-8")
edit("jasper-sdk/src/test/java/dev/jasper/sdk/ui/KitValuesTest.java", [
    ('''    private Text text(String id) { return new Text(id, ControlState.READY, "Text", TextRole.BODY); }
''', '''    private Text text(String id) { return new Text(id, ControlState.READY, "Text", TextRole.BODY); }
    private static List<String> names(Class<? extends Enum<?>> type) {
        return Arrays.stream(type.getEnumConstants()).map(constant -> constant.name()).toList();
    }
    /** Spec 8.1: the constants SDK 0.18 renames; every other constant keeps its name and place. */
    @Test void zeroEighteenNamesTheExclusiveHelpAndPromptConstants() {
        assertThat(names(ChoiceKind.class)).containsExactly("DROPDOWN", "PICKER", "EXCLUSIVE", "SEARCHABLE", "PREVIEWS");
        assertThat(names(ButtonPresentation.class)).containsExactly("STANDARD", "EMBEDDED", "CONTEXTUAL_EMBEDDED", "HELP");
        assertThat(names(FormKind.class)).containsExactly("PROMPT", "PROMPT_COMPACT", "SHEET", "FULL", "SETTINGS", "WINDOW");
        assertThat(names(LayoutRole.class)).startsWith("VERTICAL", "INLINE", "ACTIONS", "PROMPT_HEADER", "DETAIL");
        assertThat(names(TextRole.class)).startsWith("PROMPT_TITLE", "DETAIL_TITLE").contains("MONO", "MONO_SMALL", "MONO_CAPTION");
        assertThat(names(IconSize.class)).containsExactly("CAPTION", "CONTROL", "RAIL", "EMPTY", "PROMPT");
        assertThat(names(ButtonKind.class)).contains("GHOST", "DESTRUCTIVE_GHOST");
    }
''', 1)])
edit("jasper-app/build.gradle.kts", [
    ('''        // ColourLiteralScanTest reads every production source and resource.
        rootProject.fileTree("jasper-ui/src/main"), rootProject.fileTree("plugins") { include("*/src/main/**") })''',
     '''        // ColourLiteralScanTest reads every production source and resource, PromptVocabularyTest every source.
        rootProject.fileTree("jasper-ui/src"), rootProject.fileTree("jasper-sdk/src"), rootProject.fileTree("jasper-sdk-testkit/src"),
        rootProject.fileTree("plugins") { include("*/src/**") })''', 1)])
ledger = pathlib.Path("docs/design/theme-engine/migration.csv"); text = ledger.read_text(encoding="utf-8")
if not text.endswith("\n"): raise SystemExit("migration.csv: expected a final newline")
ledger.write_text(text + "jasper-app/src/test/java/dev/jasper/app/architecture/PromptVocabularyTest.java,app.architecture,"
    "PromptVocabularyTest,nonvisual-helper,none,none,jasper-app/src/test/java/dev/jasper/app/architecture/PromptVocabularyTest.java,"
    "New in Enterprise presentation Plan 3a; after SDK 0.18's renames only the Form.hero slot keeps the old word; "
    "matched only by collection construction\n", encoding="utf-8")
PY
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :jasper-sdk:test --tests dev.jasper.sdk.ui.KitValuesTest :jasper-app:test --tests dev.jasper.app.architecture.PromptVocabularyTest --continue`
Expected: FAIL. `KitValuesTest.zeroEighteenNamesTheExclusiveHelpAndPromptConstants` reports `SEGMENTED` where
`EXCLUSIVE` is expected; `PromptVocabularyTest.onlyTheFormSlotKeepsTheOldWord` lists 143 offenders (among
them `jasper-sdk/src/main/java/dev/jasper/sdk/ui/Nodes.java: HERO_TITLE`, `HeroLayout`, `applyHero` and the 21 table
keys); its other two tests pass.

- [ ] **Step 3: Rename**

Move the test named for the old kind first, so the script (which reads `git ls-files`) sees its new path:

```bash
git mv jasper-ui/src/test/java/dev/jasper/ui/swing/HeroCompositionTest.java jasper-ui/src/test/java/dev/jasper/ui/swing/PromptCompositionTest.java
```

Then run the rename from the worktree root (the `.` argument is the repository root):

```bash
python3 -I - . <<'PY'
"""Plan 3a's rename: the SDK's seven constants, the kit-private hero names, and nothing that names Form.hero."""
import pathlib, re, subprocess, sys
root = pathlib.Path(sys.argv[1])
files = subprocess.run(["git", "ls-files", "*.java"], cwd=root, capture_output=True, text=True, check=True).stdout.split()
files = [name for name in files if not name.endswith("/PromptVocabularyTest.java")]  # its examples spell the old word
QUALIFIED = [  # (pattern, replacement): qualified API constants, every file
    (r"\bChoiceKind\.SEGMENTED\b", "ChoiceKind.EXCLUSIVE"),
    (r"\bButtonPresentation\.CIRCULAR\b", "ButtonPresentation.HELP"),
    (r"\bFormKind\.HERO\b", "FormKind.PROMPT"),
    (r"\bLayoutRole\.HERO\b", "LayoutRole.PROMPT_HEADER"),
    (r"\bIconSize\.HERO\b", "IconSize.PROMPT"),
    (r"\bStyle\.HERO\b", "Style.PROMPT"),
]
TOKENS = [  # whole identifiers, every file
    (r"\bHERO_([A-Z0-9_]+)\b", r"PROMPT_\1"),      # FormKind.HERO_COMPACT, TextRole/Typography HERO_TITLE, the HERO_* metric roles
    (r"\bICON_HERO\b", "ICON_PROMPT"),
    (r"\bHeroLayout\b", "PromptLayout"),
    (r"\bapplyHero\b", "applyPrompt"),
    # identifiers that say hero for the prompt kind (test classes and methods); the bare `hero` (Form.hero) stays
    (r"(?<=[a-z0-9])Hero(?=[A-Z])|(?<![A-Za-z0-9_])Hero(?=[A-Z])", "Prompt"),
    (r"(?<![A-Za-z0-9_])hero(?=[A-Z])", "prompt"),
]
COMMENTS = {  # comments that name the prompt kind; "no hero" (the Form.hero slot) stays
    "jasper-app/src/main/java/dev/jasper/app/about/AboutView.java": [("composes it as a compact hero form", "composes it as a compact prompt form")],
    "jasper-app/src/main/java/dev/jasper/app/platform/MacTitleBar.java": [("/** A hero dialog: no title", "/** A prompt dialog: no title")],
    "jasper-app/src/test/java/dev/jasper/app/appearance/SurfaceGeometryTest.java": [("// The packed hero heights", "// The packed prompt heights")],
    "jasper-ui/src/main/java/dev/jasper/ui/icons/IconRegistry.java": [("/** Hero glyphs retain", "/** Prompt glyphs retain"),
        ("/** Hero context retains", "/** Prompt context retains"), ("the hero padlock carries", "the prompt padlock carries")],
    "jasper-ui/src/main/java/dev/jasper/ui/metrics/DesignMetrics.java": [("and a hero dialog's band", "and a prompt dialog's band")],
    "jasper-ui/src/test/java/dev/jasper/ui/swing/GroupedFormGeometryTest.java": [("// FULL and HERO rows", "// FULL and PROMPT rows")],
    "plugins/vault/src/main/java/dev/jasper/vault/ui/PasswordPanel.java": [("/** The standalone hero;", "/** The standalone prompt;")],
}
EXPLICIT = {  # unqualified constants: (old, new, count) in one file
    "jasper-sdk/src/main/java/dev/jasper/sdk/ui/Nodes.java": [
        ("public enum IconSize { CAPTION, CONTROL, RAIL, EMPTY, HERO }", "public enum IconSize { CAPTION, CONTROL, RAIL, EMPTY, PROMPT }", 1),
        ("public enum ChoiceKind { DROPDOWN, PICKER, SEGMENTED, SEARCHABLE, PREVIEWS }", "public enum ChoiceKind { DROPDOWN, PICKER, EXCLUSIVE, SEARCHABLE, PREVIEWS }", 1),
        ("public enum FormKind { HERO, PROMPT_COMPACT,", "public enum FormKind { PROMPT, PROMPT_COMPACT,", 1),
        ("public enum LayoutRole { VERTICAL, INLINE, ACTIONS, HERO, DETAIL,", "public enum LayoutRole { VERTICAL, INLINE, ACTIONS, PROMPT_HEADER, DETAIL,", 1),
        ("public enum ButtonPresentation { STANDARD, EMBEDDED, CONTEXTUAL_EMBEDDED, CIRCULAR }", "public enum ButtonPresentation { STANDARD, EMBEDDED, CONTEXTUAL_EMBEDDED, HELP }", 1)],
    "jasper-ui/src/main/java/dev/jasper/ui/model/Nodes.java": [
        ("public enum IconSize { CAPTION, CONTROL, RAIL, EMPTY, HERO }", "public enum IconSize { CAPTION, CONTROL, RAIL, EMPTY, PROMPT }", 1),
        ("public enum ChoiceKind { DROPDOWN, PICKER, SEGMENTED, SEARCHABLE, PREVIEWS }", "public enum ChoiceKind { DROPDOWN, PICKER, EXCLUSIVE, SEARCHABLE, PREVIEWS }", 1),
        ("public enum FormKind { HERO, PROMPT_COMPACT,", "public enum FormKind { PROMPT, PROMPT_COMPACT,", 1),
        ("public enum LayoutRole { VERTICAL, INLINE, ACTIONS, HERO, DETAIL,", "public enum LayoutRole { VERTICAL, INLINE, ACTIONS, PROMPT_HEADER, DETAIL,", 1),
        ("public enum ButtonPresentation { STANDARD, EMBEDDED, CONTEXTUAL_EMBEDDED, CIRCULAR }", "public enum ButtonPresentation { STANDARD, EMBEDDED, CONTEXTUAL_EMBEDDED, HELP }", 1)],
    "jasper-app/src/main/java/dev/jasper/app/platform/MacTitleBar.java": [
        ("        HERO,\n", "        PROMPT,\n", 1),
        ("            case HERO -> ", "            case PROMPT -> ", 2)],
    "jasper-ui/src/main/java/dev/jasper/ui/icons/IconRegistry.java": [("case HERO -> ", "case PROMPT -> ", 1)],
    "jasper-ui/src/main/java/dev/jasper/ui/swing/Forms.java": [("case HERO -> ", "case PROMPT -> ", 2)],
    "jasper-ui/src/test/java/dev/jasper/ui/swing/FormsTest.java": [("case HERO -> ", "case PROMPT -> ", 1)],
    "jasper-ui/src/main/java/dev/jasper/ui/swing/ItemLists.java": [("case SEGMENTED ->", "case EXCLUSIVE ->", 1)],
    "plugins/remote/src/main/java/dev/jasper/remote/ui/transfers/ExistingItemsPanel.java": [
        ('static final String HERO = "existing.hero";', 'static final String HEADER = "existing.header";', 1),
        ("new Nodes.Stack(HERO, ", "new Nodes.Stack(HEADER, ", 1), ("(HERO + \".", "(HEADER + \".", 2)],
}
changed = 0
for name in files:
    path = root / name
    text = path.read_text(encoding="utf-8")
    new = text
    for pattern, repl in QUALIFIED + TOKENS: new = re.sub(pattern, repl, new)
    for old, repl in COMMENTS.get(name, []):
        if new.count(old) != 1: raise SystemExit(f"{name}: expected one {old!r}, found {new.count(old)}")
        new = new.replace(old, repl)
    for old, repl, count in EXPLICIT.get(name, []):
        if new.count(old) != count: raise SystemExit(f"{name}: expected {count} of {old!r}, found {new.count(old)}")
        new = new.replace(old, repl)
    if new != text: path.write_text(new, encoding="utf-8"); changed += 1
for tsv in ["jasper-ui/src/test/resources/dev/jasper/ui/metrics/compact-roles.tsv", "jasper-ui/src/test/resources/dev/jasper/ui/metrics/default-type.tsv"]:
    path = root / tsv; text = path.read_text(encoding="utf-8")
    new = re.sub(r"(?m)^HERO_", "PROMPT_", text); new = re.sub(r"(?m)^ICON_HERO\b", "ICON_PROMPT", new)
    if new != text: path.write_text(new, encoding="utf-8"); changed += 1
print("files changed:", changed)
PY
```

Expected output: `files changed: 61` (59 Java files and the two tables). Then the current documents
that name an old constant:

```bash
python3 -I - <<'PY'
"""Plan 3a Task 2: the current docs that name a renamed constant."""
import pathlib
def edit(name, edits):
    path = pathlib.Path(name); text = path.read_text(encoding="utf-8")
    for old, new, count in edits:
        if text.count(old) != count: raise SystemExit(f"{name}: expected {count} of {old!r}, found {text.count(old)}")
        text = text.replace(old, new)
    path.write_text(text, encoding="utf-8")
edit("docs/plugin-ui.md", [
    ("| `Form` with `FormKind.HERO` or `HERO_COMPACT` and a hero |", "| `Form` with `FormKind.PROMPT` or `PROMPT_COMPACT` and a hero |", 1),
    ("| Any `Form`, often `HERO`/`HERO_COMPACT`, shown with", "| Any `Form`, often `PROMPT`/`PROMPT_COMPACT`, shown with", 1),
    ("- `CardKind.MIXED` combines a segmented choice, rows and settings", "- `CardKind.MIXED` combines an exclusive choice, rows and settings", 1),
    ("`Choice` (DROPDOWN, SEGMENTED for two to four\noptions,", "`Choice` (DROPDOWN, EXCLUSIVE for two to four\nvisible options,", 1)])
edit("docs/sdk-architecture.md", [
    ("`ButtonPresentation.CIRCULAR` requests an engine-owned round outlined face.",
     "`ButtonPresentation.HELP` marks a button that opens help; the default look draws it as an engine-owned round outlined face.", 1)])
edit("docs/ui-architecture.md", [("explicit SHEET/DETAIL, HERO/RAIL and", "explicit SHEET/DETAIL, PROMPT/RAIL and", 1)])
edit("docs/app-maintenance.md", [("a hero dialog keeps a 20-logical band", "a prompt dialog keeps a 20-logical band", 1)])
PY
```

`docs/plugin-ui.md:191`'s "segmented track" names a colour role and stays; the checkbox rule at `docs/plugin-ui.md:57`
is Task 4's.

- [ ] **Step 4: Run the tests and the whole build**

Run: `./gradlew check --continue`
Expected: BUILD SUCCESSFUL. `PromptVocabularyTest` (3 tests) and the SDK `KitValuesTest` pass; `DesignMetricsTest` and
`TypographyTest` read the renamed table keys; jasper-app's `KitValuesTest` maps every renamed constant losslessly.

Then check that nothing painted changed: render the headless gallery here and at the base, and compare each file by
its mapped name (gallery files are named by the constants they render, `form-HERO.png` among them). PNGs must be
byte-identical, except the five sheets that print a constant's name as a label (`typography`, `controls`,
`composition`, and the contact sheets `sheet-3` and `sheet-5`, which caption each fixture with its file name): those may
differ only in that many one-line bands. JSON must be equal once the renamed names are mapped:

```bash
B=$(mktemp -d) && git worktree add --detach "$B/base" 995ff899f
for theme in dark light; do
  (cd "$B/base" && ./gradlew -q :jasper-ui:gallery -Pjasper.galleryTheme=$theme && cp -R jasper-ui/build/gallery/$theme-1x "$B/base-$theme")
  ./gradlew -q :jasper-ui:gallery -Pjasper.galleryTheme=$theme && cp -R jasper-ui/build/gallery/$theme-1x "$B/new-$theme"
done
python3 -I - "$B" <<'PY'
import pathlib, re, struct, sys, zlib
root = pathlib.Path(sys.argv[1])
NAMES = [(r"\bHERO_COMPACT\b", "PROMPT_COMPACT"), (r"\bHERO_TITLE\b", "PROMPT_TITLE"), (r"\bSEGMENTED\b", "EXCLUSIVE"),
         (r"\bCIRCULAR\b", "HELP"), (r"\bHERO\b", "PROMPT")]
LAYOUT = (r"\bHERO\b", "PROMPT_HEADER")  # composition.json lists LayoutRole's constants
# Sheets that draw a constant's name as text, and how many one-line bands of their pixels that changes.
LABELLED = {"typography.png": 1, "controls.png": 1, "composition.png": 1, "sheet-3.png": 3, "sheet-5.png": 2}
def mapped(text, file):
    rules = [LAYOUT] + NAMES if file == "composition.json" else NAMES
    for old, new in rules: text = re.sub(old, new, text)
    return text
def rows(path):
    data, pos, idat = path.read_bytes(), 8, b""
    while pos < len(data):
        length, kind = struct.unpack(">I4s", data[pos:pos + 8]); body = data[pos + 8:pos + 8 + length]; pos += 12 + length
        if kind == b"IHDR": width, height, depth, colour, _, _, interlace = struct.unpack(">IIBBBBB", body)
        elif kind == b"IDAT": idat += body
    assert depth == 8 and interlace == 0 and colour in (2, 6), (path, depth, colour, interlace)
    bpp = 4 if colour == 6 else 3; stride = width * bpp; raw = zlib.decompress(idat); out, prev, i = [], bytearray(stride), 0
    for _ in range(height):
        kind, line = raw[i], bytearray(raw[i + 1:i + 1 + stride]); i += 1 + stride
        for x in range(stride):
            a = line[x - bpp] if x >= bpp else 0; b = prev[x]; c = prev[x - bpp] if x >= bpp else 0
            if kind == 1: line[x] = (line[x] + a) & 255
            elif kind == 2: line[x] = (line[x] + b) & 255
            elif kind == 3: line[x] = (line[x] + (a + b) // 2) & 255
            elif kind == 4:
                p = a + b - c; pa, pb, pc = abs(p - a), abs(p - b), abs(p - c)
                line[x] = (line[x] + (a if pa <= pb and pa <= pc else b if pb <= pc else c)) & 255
        out.append(bytes(line)); prev = line
    return (width, height), out
def bands(old, new):
    (size, a), (other, b) = rows(old), rows(new)
    assert size == other, (old.name, size, other)
    found = []
    for y in (y for y in range(len(a)) if a[y] != b[y]):
        if found and y - found[-1][1] <= 2: found[-1][1] = y
        else: found.append([y, y])
    return found
for theme in ["dark", "light"]:
    base, new = root / f"base-{theme}", root / f"new-{theme}"
    expected = {mapped(p.name, p.name): p for p in base.iterdir()}
    actual = {p.name: p for p in new.iterdir()}
    assert expected.keys() == actual.keys(), (theme, sorted(expected.keys() ^ actual.keys()))
    for name, old in expected.items():
        if name in LABELLED:
            found = bands(old, actual[name])
            assert len(found) == LABELLED[name] and all(y1 - y0 < 16 for y0, y1 in found), (theme, name, found)
        elif name.endswith(".png"): assert old.read_bytes() == actual[name].read_bytes(), (theme, name)
        else: assert mapped(old.read_text(encoding="utf-8"), name) == actual[name].read_text(encoding="utf-8"), (theme, name)
    print(theme, len(actual), "files identical once the renamed names are mapped, but for the label lines of",
          len(LABELLED), "sheets that print a renamed name")
PY
git worktree remove --force "$B/base"
```

Expected: `dark 142 files identical once the renamed names are mapped, but for the label lines of 5 sheets that
print a renamed name`, and the same for `light`. (The changed bands read `HERO_TITLE · Jasper` as `PROMPT_TITLE ·
Jasper`, `choice-SEGMENTED` as `choice-EXCLUSIVE`, the composition sheet's `HERO` as `PROMPT_HEADER`, and the contact
sheets' `form-HERO` captions as `form-PROMPT`.)

- [ ] **Step 5: Commit**

```bash
git add -A jasper-sdk jasper-ui jasper-app plugins docs/plugin-ui.md docs/sdk-architecture.md docs/ui-architecture.md \
  docs/app-maintenance.md docs/design/theme-engine/migration.csv
git commit -F - <<'EOF'
SDK 0.18: EXCLUSIVE, HELP and the prompt names

ChoiceKind.SEGMENTED is EXCLUSIVE, ButtonPresentation.CIRCULAR is HELP, and
FormKind.HERO and HERO_COMPACT, LayoutRole.HERO, TextRole.HERO_TITLE and
IconSize.HERO are PROMPT, PROMPT_COMPACT, PROMPT_HEADER, PROMPT_TITLE and
PROMPT, in the SDK and the jasper-ui model alike. The kit's private names
follow, so hero names only Form.hero, the heading slot; the frozen tables
change their keys only, and the gallery's pixels are unchanged.
PromptVocabularyTest keeps it that way.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

### Task 3: KitCompatibility names the SDK that removed each member

`KitCompatibility` keys every removed type and member by the SDK version that removed it: the four types and 33
members of 0.15 as today, and spec 8.1's seven constants (`HERO_COMPACT` included) as exact `getstatic` members
removed in 0.18. Its message becomes spec 15's "references removed SDK member X; rebuild it for SDK 0.18" (with
"type" for a removed type). A rebuilt plugin that claims `<0.19` and still reads a renamed constant is refused at load;
the new names, and a plugin's own constant that happens to be called `HERO`, load; a 0.17 manifest never gets that far,
because its range skips it.

**Files:**
- Modify: `jasper-app/src/main/java/dev/jasper/app/plugins/KitCompatibility.java` (whole file)
- Modify: `docs/plugin-authoring.md:894-896`, `docs/design/theme-engine/migration.csv:1132` (the gate's note)
- Test: `jasper-app/src/test/java/dev/jasper/app/plugins/KitCompatibilityTest.java:3,33-38` (helpers and three tests)

**Interfaces:**
- Consumes: `JasperSdk.VERSION == "0.18.0"` (Task 1); the renamed constants exist under their new names (Task 2).
- Produces: `PluginLoader.LoadFailure` messages "<jar>!<entry> references removed SDK member <owner#name descriptor>;
  rebuild it for SDK <version>" and "… removed SDK type <internal name>; rebuild it for SDK <version>". No signature
  changes: `KitCompatibility.check(byte[], String)` is as before.

- [ ] **Step 1: Write the failing tests**

Run from the worktree root:

```bash
python3 -I - <<'PY'
"""Plan 3a Task 3 step 1: KitCompatibilityTest names the removing version and covers the seven renamed constants."""
import pathlib
def edit(name, edits):
    path = pathlib.Path(name); text = path.read_text(encoding="utf-8")
    for old, new, count in edits:
        if text.count(old) != count: raise SystemExit(f"{name}: expected {count} of {old!r}, found {text.count(old)}")
        text = text.replace(old, new)
    path.write_text(text, encoding="utf-8")
edit("jasper-app/src/test/java/dev/jasper/app/plugins/KitCompatibilityTest.java", [
    ("import dev.jasper.app.testsupport.PluginJars;\n", "import dev.jasper.app.testsupport.PluginJars;\nimport dev.jasper.sdk.JasperSdk;\n", 1),
    ('''    private void rejects(byte[] bytes,String expected) throws Exception {
        var candidate=candidate(">=0.1",Map.of("test/kit/Probe.class",bytes),false);
        assertThat(PluginResolver.resolve(List.of(candidate),Map.of(),Version.parse("0.15.0"),false).load()).containsExactly(candidate);
        assertThatThrownBy(() -> PluginLoader.load(candidate,Map.of(),Plugin.class.getClassLoader()))
            .isInstanceOf(PluginLoader.LoadFailure.class).hasMessageContaining("SDK 0.15").hasMessageContaining(expected).hasMessageContaining("Probe.class");
    }
''', '''    /** A class whose constant pool reads one enum constant, as {@code getstatic} does. */
    private byte[] constant(String owner,String name) {
        return ClassFile.of().build(ClassDesc.of("test.kit.Probe"),builder -> builder.constantPool().fieldRefEntry(ClassDesc.of(owner),name,ClassDesc.of(owner)));
    }
    private void rejects(byte[] bytes,String expected) throws Exception { rejects(bytes,expected,"0.15"); }
    private void rejects(byte[] bytes,String expected,String removedIn) throws Exception {
        var candidate=candidate(">=0.1",Map.of("test/kit/Probe.class",bytes),false);
        assertThat(PluginResolver.resolve(List.of(candidate),Map.of(),Version.parse(JasperSdk.VERSION),false).load()).containsExactly(candidate);
        assertThatThrownBy(() -> PluginLoader.load(candidate,Map.of(),Plugin.class.getClassLoader()))
            .isInstanceOf(PluginLoader.LoadFailure.class).hasMessageContaining("; rebuild it for SDK "+removedIn).hasMessageContaining(expected).hasMessageContaining("Probe.class");
    }
    private static final List<List<String>> RENAMED=List.of(List.of("ChoiceKind","SEGMENTED"),List.of("ButtonPresentation","CIRCULAR"),
        List.of("FormKind","HERO"),List.of("FormKind","HERO_COMPACT"),List.of("LayoutRole","HERO"),List.of("TextRole","HERO_TITLE"),List.of("IconSize","HERO"));
    private static final List<List<String>> RENAMED_TO=List.of(List.of("ChoiceKind","EXCLUSIVE"),List.of("ButtonPresentation","HELP"),
        List.of("FormKind","PROMPT"),List.of("FormKind","PROMPT_COMPACT"),List.of("LayoutRole","PROMPT_HEADER"),List.of("TextRole","PROMPT_TITLE"),List.of("IconSize","PROMPT"));
    /** Spec 8.1 and 15: a plugin built on 0.17 that claims a 0.18 range and still reads a renamed constant is refused at load. */
    @Test void aConstantSdkZeroEighteenRenamedIsRefusedWithTheVersionThatRemovedIt() throws Exception {
        for(var renamed:RENAMED)
            rejects(constant("dev.jasper.sdk.ui.Nodes$"+renamed.get(0),renamed.get(1)),"dev/jasper/sdk/ui/Nodes$"+renamed.get(0)+"#"+renamed.get(1)+"L","0.18");
        var candidate=candidate(">=0.18.0, <0.19",Map.of("test/kit/Probe.class",constant("dev.jasper.sdk.ui.Nodes$ChoiceKind","SEGMENTED")),false);
        assertThatThrownBy(() -> PluginLoader.load(candidate,Map.of(),Plugin.class.getClassLoader())).hasMessage("plugin.jar!test/kit/Probe.class references removed SDK member "
            +"dev/jasper/sdk/ui/Nodes$ChoiceKind#SEGMENTEDLdev/jasper/sdk/ui/Nodes$ChoiceKind;; rebuild it for SDK 0.18");
    }
    @Test void theNamesSdkZeroEighteenGaveThemAreAdmitted() throws Exception {
        for(var renamed:RENAMED_TO) {
            var candidate=candidate(">=0.18.0, <0.19",Map.of("test/kit/Probe.class",constant("dev.jasper.sdk.ui.Nodes$"+renamed.get(0),renamed.get(1))),false);
            try(var loader=PluginLoader.load(candidate,Map.of(),Plugin.class.getClassLoader()).loader()){assertThat(loader).as(renamed.toString()).isNotNull();}
        }
        var own=candidate(">=0.18.0, <0.19",Map.of("test/kit/Probe.class",constant("test.kit.Mode","HERO")),false);
        try(var loader=PluginLoader.load(own,Map.of(),Plugin.class.getClassLoader()).loader()){assertThat(loader).as("the plugin's own constant").isNotNull();}
    }
    /** Spec 8.1: a 0.17 manifest is skipped by its range before any class is read. */
    @Test void aZeroSeventeenRangeIsSkippedBeforeItLoads() throws Exception {
        var candidate=candidate(">=0.17.0, <0.18",Map.of("test/kit/Probe.class",constant("dev.jasper.sdk.ui.Nodes$ChoiceKind","SEGMENTED")),false);
        var result=PluginResolver.resolve(List.of(candidate),Map.of(),Version.parse(JasperSdk.VERSION),false);
        assertThat(result.load()).isEmpty();
        assertThat(result.rejected()).singleElement().satisfies(value -> assertThat(value.reason()).isEqualTo("needs SDK >=0.17.0, <0.18.0; this is SDK 0.18.0"));
    }
''', 1)])
PY
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :jasper-app:test --tests dev.jasper.app.plugins.KitCompatibilityTest`
Expected: FAIL. `aConstantSdkZeroEighteenRenamedIsRefusedWithTheVersionThatRemovedIt` fails ("Expecting code to raise a
throwable": the gate does not know the constants yet), and the four existing tests that go through `rejects` fail on
the message (it still says "requires migration to SDK 0.15 kit UI", not "; rebuild it for SDK 0.15").
`theNamesSdkZeroEighteenGaveThemAreAdmitted` and `aZeroSeventeenRangeIsSkippedBeforeItLoads` already pass.

- [ ] **Step 3: Key the removed members by version**

Replace the whole of `jasper-app/src/main/java/dev/jasper/app/plugins/KitCompatibility.java` with:

```java
package dev.jasper.app.plugins;

import java.lang.classfile.ClassFile;
import java.lang.classfile.constantpool.ClassEntry;
import java.lang.classfile.constantpool.MemberRefEntry;
import java.lang.classfile.constantpool.NameAndTypeEntry;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Admission across SDK breaks: exact removed JVM members and types, never plugin execution or reflective linking.
 * Each entry names the SDK version that removed it. A plugin reads an enum constant with {@code getstatic}, as a classic
 * or arrow {@code switch} does through its switch map. Generic erasure and names held as strings (an enum constant
 * looked up by {@code valueOf}, or a qualified constant label in a pattern {@code switch}) are not provable here;
 * normal linkage containment still applies.
 */
final class KitCompatibility {
    private KitCompatibility() {}
    private static final Map<String, String> TYPES = removed(Map.of("0.15", List.of(
        "dev/jasper/sdk/settings/SettingsPanel$View",
        "dev/jasper/sdk/ui/BundledIcon",
        "dev/jasper/sdk/ui/IconName",
        "dev/jasper/sdk/ui/PanelFactory$View")));
    private static final Map<String, String> MEMBERS = removed(Map.of("0.15", List.of(
        "dev/jasper/sdk/notifications/NotificationSpec#withIcon(Ljavax/swing/Icon;)Ldev/jasper/sdk/notifications/NotificationSpec;",
        "dev/jasper/sdk/palette/PaletteRow#withIcon(Ljavax/swing/Icon;)Ldev/jasper/sdk/palette/PaletteRow;",
        "dev/jasper/sdk/palette/ScopeSpec#withIcon(Ljavax/swing/Icon;)Ldev/jasper/sdk/palette/ScopeSpec;",
        "dev/jasper/sdk/settings/SettingsPanel#create(Ldev/jasper/sdk/settings/SettingsPanelHost;)Ljavax/swing/JComponent;",
        "dev/jasper/sdk/settings/SettingsPanelHost#row(Ldev/jasper/sdk/settings/Setting;)Ljavax/swing/JComponent;",
        "dev/jasper/sdk/settings/SettingsPanelHost#rowNode(Ldev/jasper/sdk/settings/Setting;)Ldev/jasper/sdk/ui/Nodes$Node;",
        "dev/jasper/sdk/ui/ActionSpec#withIcon(Ljavax/swing/Icon;)Ldev/jasper/sdk/ui/ActionSpec;",
        "dev/jasper/sdk/ui/Appearance#icon(Ldev/jasper/sdk/ui/BundledIcon;)Ljavax/swing/Icon;",
        "dev/jasper/sdk/ui/Appearance#icon(Ldev/jasper/sdk/ui/IconName;)Ljavax/swing/Icon;",
        "dev/jasper/sdk/ui/Appearance#icon(Ljava/lang/String;)Ljavax/swing/Icon;",
        "dev/jasper/sdk/ui/ContentPresentation#<init>(Ljava/lang/String;Ljava/lang/String;Ljavax/swing/Icon;ZLjava/lang/String;Ljava/lang/String;)V",
        "dev/jasper/sdk/ui/ContentPresentation#icon()Ljavax/swing/Icon;",
        "dev/jasper/sdk/ui/EditorPalette#current()Ldev/jasper/sdk/ui/EditorPalette;",
        "dev/jasper/sdk/ui/PanelFactory#create(Ldev/jasper/sdk/ui/PanelHost;)Ljavax/swing/JComponent;",
        "dev/jasper/sdk/ui/PanelHeader$Action#<init>(Ljava/lang/String;Ljavax/swing/Icon;Ljava/lang/Runnable;)V",
        "dev/jasper/sdk/ui/PanelHeader$Action#icon()Ljavax/swing/Icon;",
        "dev/jasper/sdk/ui/PanelSpec#<init>(Ljava/lang/String;Ljava/lang/String;Ljavax/swing/Icon;Ldev/jasper/sdk/ui/Anchor;)V",
        "dev/jasper/sdk/ui/PanelSpec#<init>(Ljava/lang/String;Ljava/lang/String;Ljavax/swing/Icon;Ldev/jasper/sdk/ui/Anchor;Ljava/util/Optional;)V",
        "dev/jasper/sdk/ui/PanelSpec#<init>(Ljava/lang/String;Ljava/lang/String;Ljavax/swing/Icon;Ldev/jasper/sdk/ui/Anchor;Ljava/util/Optional;Z)V",
        "dev/jasper/sdk/ui/PanelSpec#<init>(Ljava/lang/String;Ljava/lang/String;Ljavax/swing/Icon;Ldev/jasper/sdk/ui/Anchor;Ljava/util/Optional;ZLjava/lang/String;)V",
        "dev/jasper/sdk/ui/PanelSpec#icon()Ljavax/swing/Icon;",
        "dev/jasper/sdk/ui/PanelSpec#iconName()Ljava/lang/String;",
        "dev/jasper/sdk/ui/Panels#registerView(Ldev/jasper/sdk/ui/PanelSpec;Ldev/jasper/sdk/ui/PanelFactory$View;)Ldev/jasper/sdk/Subscription;",
        "dev/jasper/sdk/ui/PluginDialog#setContent(Ljavax/swing/JComponent;)V",
        "dev/jasper/sdk/ui/PluginWindow#setContent(Ljavax/swing/JComponent;)V",
        "dev/jasper/sdk/ui/StatusItem#setIcon(Ljavax/swing/Icon;)V",
        "dev/jasper/sdk/ui/SyntaxPalette#current()Ldev/jasper/sdk/ui/SyntaxPalette;",
        "dev/jasper/sdk/ui/ToolbarItem#menu(Ljavax/swing/Icon;Ljava/lang/String;Ljava/util/List;)Ldev/jasper/sdk/ui/ToolbarItem;",
        "dev/jasper/sdk/ui/ToolbarItem$Dropdown#<init>(Ljavax/swing/Icon;Ljava/lang/String;Ljava/util/List;)V",
        "dev/jasper/sdk/ui/ToolbarItem$Dropdown#icon()Ljavax/swing/Icon;",
        "dev/jasper/sdk/ui/WindowSurface#setContent(Ljavax/swing/JComponent;)V",
        "dev/jasper/sdk/ui/Windows#openTab(Ldev/jasper/sdk/ui/ContentSpec;Ldev/jasper/sdk/WindowOwner;Ljavax/swing/JComponent;)Ldev/jasper/sdk/ui/ContentTab;",
        "dev/jasper/sdk/ui/WorkspacePalette#current()Ldev/jasper/sdk/ui/WorkspacePalette;",
        "dev/jasper/sdk/ui/WorkspacePalette#current(Ljava/awt/Component;)Ldev/jasper/sdk/ui/WorkspacePalette;"),
        // The constants SDK 0.18 renamed.
        "0.18", List.of(
        "dev/jasper/sdk/ui/Nodes$ChoiceKind#SEGMENTEDLdev/jasper/sdk/ui/Nodes$ChoiceKind;",
        "dev/jasper/sdk/ui/Nodes$ButtonPresentation#CIRCULARLdev/jasper/sdk/ui/Nodes$ButtonPresentation;",
        "dev/jasper/sdk/ui/Nodes$FormKind#HEROLdev/jasper/sdk/ui/Nodes$FormKind;",
        "dev/jasper/sdk/ui/Nodes$FormKind#HERO_COMPACTLdev/jasper/sdk/ui/Nodes$FormKind;",
        "dev/jasper/sdk/ui/Nodes$LayoutRole#HEROLdev/jasper/sdk/ui/Nodes$LayoutRole;",
        "dev/jasper/sdk/ui/Nodes$TextRole#HERO_TITLELdev/jasper/sdk/ui/Nodes$TextRole;",
        "dev/jasper/sdk/ui/Nodes$IconSize#HEROLdev/jasper/sdk/ui/Nodes$IconSize;")));

    private static Map<String, String> removed(Map<String, List<String>> byVersion) {
        var removed = new HashMap<String, String>();
        byVersion.forEach((version, names) -> names.forEach(name -> removed.put(name, version)));
        return Map.copyOf(removed);
    }

    static void check(byte[] bytes, String source) throws PluginLoader.LoadFailure {
        try {
            var model=ClassFile.of().parse(bytes);
            for(var entry:model.constantPool()) {
                if(entry instanceof ClassEntry type) {
                    if(TYPES.containsKey(type.asInternalName())) fail(source,"type",type.asInternalName(),TYPES);
                    descriptor(source,type.asInternalName()); // Array class constants carry descriptors.
                }
                if(entry instanceof NameAndTypeEntry value) descriptor(source,value.type().stringValue());
                if(entry instanceof MemberRefEntry member) {
                    String reference=member.owner().asInternalName()+"#"+member.name().stringValue()+member.type().stringValue();
                    if(MEMBERS.containsKey(reference))fail(source,"member",reference,MEMBERS);
                }
            }
            for(var field:model.fields())descriptor(source,field.fieldType().stringValue());
            for(var method:model.methods()) {
                String signature=method.methodName().stringValue()+method.methodType().stringValue();
                descriptor(source,method.methodType().stringValue());
                // An old factory implementation may never reference its interface's removed member.
                for(var implemented:model.interfaces()) {
                    String reference=implemented.asInternalName()+"#"+signature;
                    if(MEMBERS.containsKey(reference))fail(source,"member",reference,MEMBERS);
                }
            }
        } catch(IllegalArgumentException failure) {
            throw new PluginLoader.LoadFailure("Cannot inspect " + source + ": invalid class file",failure);
        }
    }
    private static void descriptor(String source,String descriptor) throws PluginLoader.LoadFailure {
        for(var type:TYPES.keySet())if(descriptor.contains("L"+type+";"))fail(source,"type",type,TYPES);
    }
    private static void fail(String source,String kind,String name,Map<String, String> removedIn) throws PluginLoader.LoadFailure {
        throw new PluginLoader.LoadFailure(source+" references removed SDK "+kind+" "+name+"; rebuild it for SDK "+removedIn.get(name),null);
    }
}
```

Then run from the worktree root (the guide's sentence about the gate's errors and the gate's ledger note):

```bash
python3 -I - <<'PY'
"""Plan 3a Task 3 step 3: the gate's ledger note and the guide sentence about its errors."""
import pathlib
def edit(name, edits):
    path = pathlib.Path(name); text = path.read_text(encoding="utf-8")
    for old, new, count in edits:
        if text.count(old) != count: raise SystemExit(f"{name}: expected {count} of {old!r}, found {text.count(old)}")
        text = text.replace(old, new)
    path.write_text(text, encoding="utf-8")
edit("docs/design/theme-engine/migration.csv", [
    ("KitCompatibility.java,app.plugins,KitCompatibility,nonvisual-helper,11,10,jasper-app/src/test/java/dev/jasper/app/plugins/KitCompatibilityTest.java,SDK 0.15 loader admission checker;",
     "KitCompatibility.java,app.plugins,KitCompatibility,nonvisual-helper,11,10,jasper-app/src/test/java/dev/jasper/app/plugins/KitCompatibilityTest.java,SDK 0.15 and 0.18 loader admission checker;", 1)])
edit("docs/plugin-authoring.md", [
    ("including obsolete factory implementations. Errors identify the class and member to migrate.",
     "including obsolete factory implementations, and SDK 0.18's renamed constants. Errors identify the class, the\nmember and the SDK version that removed it.", 1),
    ("Erased generic icon values and reflective member lookup cannot all be detected statically;",
     "Erased generic icon values, reflective member lookup and enum constants named by strings (`valueOf`, a\nqualified constant label in a pattern `switch`) cannot all be detected statically;", 1)])
PY
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.plugins.*' --tests dev.jasper.app.appearance.ThemeMigrationCoverageTest`
Expected: BUILD SUCCESSFUL; `KitCompatibilityTest` passes all 12 tests.

- [ ] **Step 5: Commit**

```bash
git add jasper-app/src/main/java/dev/jasper/app/plugins/KitCompatibility.java \
  jasper-app/src/test/java/dev/jasper/app/plugins/KitCompatibilityTest.java docs/plugin-authoring.md \
  docs/design/theme-engine/migration.csv
git commit -F - <<'EOF'
SDK 0.18: KitCompatibility names the SDK that removed a member

The load gate keys every removed type and member by the SDK version that
removed it and gains 0.18's seven renamed constants as exact getstatic
members, so a plugin rebuilt with a 0.18 range that still reads SEGMENTED
is refused with "references removed SDK member ...; rebuild it for SDK
0.18". The new names and a plugin's own like-named constants load.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

### Task 4: The re-documented contracts

Spec 8.3 changes no API shape: the SDK Javadoc and `docs/plugin-ui.md` say what each descriptor means and which
choices are the presentation's, so a second look can draw the same descriptors differently. `Toggle` is a boolean
drawn as a switch or a checkbox; EXCLUSIVE shows two to four options; GHOST is quiet; HELP opens help; PROMPT is a
short, focused question; TECHNICAL and the MONO roles mark data values in the data face (`monospaceRows` means the
data face, not the terminal font, which `CommandPalette` already does: it draws such rows as `TextRole.MONO`);
Section with a Card is a titled group; FormRow labels carry no colon; `Empty.icon` shows only where a presentation
draws an icon tile; the dialog placement, list densities, scroll policy, picker density and STEPPER versus SLIDER are
hints; `PanelHeader.detail` takes counts as digits; `Menus.create` places a menu where the presentation puts plugin
menus, and PANE and TAB items may appear in a Window menu. The rule "settings never use checkboxes" is deleted.

**Files:**
- Modify: `jasper-sdk/src/main/java/dev/jasper/sdk/ui/Nodes.java` (the Javadoc of `TextRole`, `ButtonKind`, `ChoiceKind`, `ListKind`, `FormKind`, `ScrollPolicy`, `ButtonPresentation`, `FieldPresentation`, `PickerDensity`, `Toggle`, `FormRow`, `Section`, `Empty`)
- Modify: `jasper-sdk/src/main/java/dev/jasper/sdk/ui/DialogSpec.java:16`, `jasper-sdk/src/main/java/dev/jasper/sdk/ui/PanelHeader.java:7`, `jasper-sdk/src/main/java/dev/jasper/sdk/palette/ScopeSpec.java:20,55,89`, `jasper-sdk/src/main/java/dev/jasper/sdk/ui/Menus.java:14`, `jasper-sdk/src/main/java/dev/jasper/sdk/ui/StandardMenu.java:3`
- Modify: `docs/plugin-ui.md:57-59` (the checkbox rule, and one paragraph after the controls list)

**Interfaces:**
- Consumes: the renamed constants (Task 2): the Javadoc names `EXCLUSIVE`, `HELP`, `PROMPT` and `PROMPT_COMPACT`.
- Produces: Javadoc only; no signature changes.

- [ ] **Step 1: Re-document the contracts**

Run from the worktree root:

```bash
python3 -I - <<'PY'
"""Plan 3a Task 4: spec 8.3's re-documented contracts, in the SDK Javadoc and docs/plugin-ui.md."""
import pathlib
def edit(name, edits):
    path = pathlib.Path(name); text = path.read_text(encoding="utf-8")
    for old, new, count in edits:
        if text.count(old) != count: raise SystemExit(f"{name}: expected {count} of {old!r}, found {text.count(old)}")
        text = text.replace(old, new)
    path.write_text(text, encoding="utf-8")
NODES = "jasper-sdk/src/main/java/dev/jasper/sdk/ui/Nodes.java"
edit(NODES, [
    ("    public enum TextRole { PROMPT_TITLE,",
     "    /** The MONO roles mark data values (hosts, ports, paths, commands), drawn in the data face. */\n    public enum TextRole { PROMPT_TITLE,", 1),
    ("    public enum ButtonKind { PRIMARY,", "    /** GHOST is a quiet button. */\n    public enum ButtonKind { PRIMARY,", 1),
    ("    public enum ChoiceKind { DROPDOWN,", "    /** EXCLUSIVE is a choice among two to four options, all of them visible. */\n    public enum ChoiceKind { DROPDOWN,", 1),
    ("     * RESULTS is a one-line result list driven by an external field (a command palette): it never takes focus, the host\n     * moves its selection, a left press activates a row, and fixed row and heading heights keep it steady.\n     */",
     "     * RESULTS is a one-line result list driven by an external field (a command palette): it never takes focus, the host\n     * moves its selection, a left press activates a row, and fixed row and heading heights keep it steady. SINGLE,\n     * DOUBLE and COMPACT are density hints.\n     */", 1),
    ("    /** WINDOW has exactly one full-bleed content section, no hero, and a shared pinned footer. */",
     "    /**\n     * PROMPT is a short, focused question (unlock, create) and PROMPT_COMPACT its compact form. WINDOW has exactly one\n     * full-bleed content section, no hero, and a shared pinned footer.\n     */", 1),
    ("    /** STANDARD reserves the scroll bar's width; OVERLAY shows a transient thumb over the rows only while scrolling. */",
     "    /** A hint: STANDARD reserves the scroll bar's width; OVERLAY shows a transient thumb over the rows only while scrolling. */", 1),
    ("     * Outside an item row it behaves like EMBEDDED. It never changes action dispatch or selection.\n     */\n    public enum ButtonPresentation",
     "     * Outside an item row it behaves like EMBEDDED. It never changes action dispatch or selection. HELP is a button that\n     * opens help; the default look draws it round.\n     */\n    public enum ButtonPresentation", 1),
    ("     * TECHNICAL uses standard geometry with the monospaced technical-value font.\n     * PLAIN_PATH keeps PATH semantics and Browse with no leading folder; other kinds reject it.\n",
     "     * TECHNICAL marks a data value (a host, port, path or command): standard geometry in the data face.\n     * PLAIN_PATH keeps PATH semantics and Browse with no leading folder; other kinds reject it. STEPPER and SLIDER\n     * (NUMBER only) are hints for how the number is offered.\n", 1),
    ("    /** The full form and inline picker share one renderer at two explicit densities. */",
     "    /** A hint: the full form and inline picker share one renderer at two densities. */", 1),
    ("     * {@code tooltip} is the switch's literal tooltip (blank shows the label); a validation error still takes precedence.\n     * A {@code pending} switch shows its knob midway on the warning colour while the domain confirms a change (for\n     * example a tunnel starting); it stays operable and reports the opposite of {@code checked}, which is always the\n     * confirmed state.\n",
     "     * A boolean, drawn as a switch or a checkbox; {@code compact} is a size hint. {@code tooltip} is the control's literal\n     * tooltip (blank shows the label); a validation error still takes precedence. {@code pending} means a change is in\n     * flight while the domain confirms it (for example a tunnel starting): the control stays operable and reports the\n     * opposite of {@code checked}, which is always the confirmed state, and the default look shows the switch's knob\n     * midway on the warning colour.\n", 1),
    ("        /** A switch that is not pending. */", "        /** A toggle that is not pending. */", 1),
    ("    /** {@code highlighted} marks a row matching the host's current search. */\n    public record FormRow(",
     "    /** {@code label} carries no colon: the presentation punctuates it. {@code highlighted} marks a row matching the host's current search. */\n    public record FormRow(", 1),
    ("    public record Section(String id,", "    /** With a {@link Card} as its body, a titled group. */\n    public record Section(String id,", 1),
    ("    public record Empty(String id,", "    /** Nothing to show yet, or a disconnected state. {@code icon} is drawn only by presentations that draw an icon tile. */\n    public record Empty(String id,", 1)])
edit("jasper-sdk/src/main/java/dev/jasper/sdk/ui/DialogSpec.java", [
    ("    /** Placement, independent of the form's content kind. */", "    /** Placement, independent of the form's content kind; a hint the presentation may place another way. */", 1)])
edit("jasper-sdk/src/main/java/dev/jasper/sdk/ui/PanelHeader.java", [
    (" * @param detail count or connection name beside the title; empty to omit",
     " * @param detail count or connection name beside the title; empty to omit. A count is passed as digits, and the\n *     presentation punctuates it", 1)])
edit("jasper-sdk/src/main/java/dev/jasper/sdk/palette/ScopeSpec.java", [
    (" * @param monospaceRows    whether rows are painted in the terminal font (commands, paths)",
     " * @param monospaceRows    whether rows are data values (commands, paths), drawn in the data face", 1),
    ("     * @param monospaceRows    whether rows use the terminal font", "     * @param monospaceRows    whether rows are data values", 1),
    ("     * @param value whether rows use the terminal font", "     * @param value whether rows are data values", 1)])
edit("jasper-sdk/src/main/java/dev/jasper/sdk/ui/Menus.java", [
    ("     * A new top-level menu, placed after the Tab menu.\n",
     "     * A new top-level menu, placed where the presentation puts plugin menus: after the Tab menu in the default look.\n", 1)])
edit("jasper-sdk/src/main/java/dev/jasper/sdk/ui/StandardMenu.java", [
    ("/** The application's built-in menus; a plugin's entries form a section at the end of one. */",
     "/** The application's built-in menus; a plugin's entries form a section at the end of one. A presentation may show the\n * PANE and TAB items in a Window menu. */", 1)])
edit("docs/plugin-ui.md", [
    ("options, SEARCHABLE, PICKER with a rich `PickerFace`, PREVIEWS), `Toggle` (a switch; settings never use checkboxes),",
     "options, SEARCHABLE, PICKER with a rich `PickerFace`, PREVIEWS), `Toggle` (a boolean, drawn as a switch or a checkbox),", 1),
    ("Use `TextRole.MONO*` and `FieldPresentation.TECHNICAL` for technical values (hosts, ports, paths, commands).\n",
     "Use `TextRole.MONO*` and `FieldPresentation.TECHNICAL` for technical values (hosts, ports, paths, commands).\n\n"
     "Descriptors say what a control means and the presentation draws it. GHOST is a quiet button, `ButtonPresentation.HELP`\n"
     "opens help and `FormKind.PROMPT` is a short, focused question. A `Section` holding a `Card` is a titled group.\n"
     "`FormRow` labels carry no colon (the presentation punctuates), `Empty.icon` shows only where the presentation draws an\n"
     "icon tile, and `PanelHeader.detail` takes a count as digits. `DialogSpec.Presentation`, the list kinds' densities,\n"
     "`ScrollPolicy`, `PickerDensity`, `Toggle.compact` and STEPPER versus SLIDER are hints. `ScopeSpec.monospaceRows` and\n"
     "the MONO roles mean data values in the data face, not the terminal font. A menu from `Menus.create` goes where the\n"
     "presentation puts plugin menus, and the PANE and TAB items may appear in a Window menu.\n", 1)])
PY
```

- [ ] **Step 2: Verify the Javadoc and the guides**

Run: `git grep -n 'never use checkboxes\|in the terminal font\|use the terminal font' -- jasper-sdk docs/plugin-ui.md`
Expected: no output.

Run: `./gradlew :jasper-sdk:javadoc :jasper-app:test --tests 'dev.jasper.app.documentation.*'`
Expected: BUILD SUCCESSFUL (doclint accepts every new comment; the guides' links resolve and their examples still
match compiled source).

- [ ] **Step 3: Commit**

```bash
git add jasper-sdk/src/main docs/plugin-ui.md
git commit -F - <<'EOF'
SDK 0.18: the re-documented contracts

The SDK Javadoc and the plugin UI guide say what each descriptor means and
which choices are the presentation's: a Toggle is a boolean drawn as a
switch or a checkbox, EXCLUSIVE shows two to four options, GHOST is quiet,
HELP opens help, PROMPT is a short focused question, the MONO roles and
TECHNICAL mark data values, and placement, densities and STEPPER versus
SLIDER are hints. "Settings never use checkboxes" is gone.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

### Task 5: STATUS, the THD hand-off and the whole build

`docs/STATUS.md` records plan 3a, its planned deviations, what plans 3b to 3e and 5 take over, and what THD Sourcing
Crypto must change to load on 0.18 (spec 8.5: it rebuilds for 0.18, in its own repository; nothing of it enters this
one). The whole build runs once more and the plan gets its status banner.

**Files:**
- Modify: `docs/STATUS.md:2` (a new entry above plan 2's)
- Modify: `docs/superpowers/plans/2026-10-15-enterprise-3a-sdk-0-18-renames.md:1` (the status banner)

**Interfaces:**
- Consumes: Tasks 1 to 4, committed.
- Produces: documentation only.

- [ ] **Step 1: Run the whole build**

Run: `./gradlew check --continue`
Expected: BUILD SUCCESSFUL. Then print the totals:

```bash
python3 -I - <<'PY'
import pathlib, re
totals = [0, 0, 0, 0]
for path in pathlib.Path(".").glob("**/build/test-results/test/*.xml"):
    suite = re.search(r"<testsuite\b[^>]*>", path.read_text(encoding="utf-8")).group(0)
    for i, key in enumerate(("tests", "failures", "errors", "skipped")):
        totals[i] += int(re.search(rf'\b{key}="(\d+)"', suite).group(1))
print(f"{totals[0]:,} tests, {totals[1]} failures, {totals[2]} errors, {totals[3]} skipped")
PY
```

Expected: 10,507 tests, 0 failures, 0 errors, 25 skipped (the base's 10,500 and 25 skipped, plus `PromptVocabularyTest`'s three, the SDK
`KitValuesTest`'s one and `KitCompatibilityTest`'s three). Then run the AGENTS.md source-hygiene script over every Java
source; it must print nothing. Run `ls build/sdk-release` and expect the five 0.18.0 release jars
(`jasper-sdk-0.18.0.jar`, `-sources`, `-javadoc`, `jasper-sdk-testkit-0.18.0.jar`, `-sources`): spec 8.5's
`sdkRelease`, which `verifySdkRelease` in `:jasper-sdk:check` builds and checks.

- [ ] **Step 2: Record the plan in STATUS and in this plan**

In `docs/STATUS.md`, insert after line 2 (the blank line under the title) this entry, with the line Step 1's totals
script printed in place of `TOTALS` and the first and last commit of Tasks 1-4 in place of `FIRST..LAST`, followed by a
blank line:

```markdown
**Enterprise presentation, plan 3a: SDK 0.18 renames — 2026-10-15** (`claude/enterprise-sdk-0-18-renames`, worktree
`.claude/worktrees/enterprise-sdk-0-18`, `FIRST..LAST`, then this documentation commit;
[plan](superpowers/plans/2026-10-15-enterprise-3a-sdk-0-18-renames.md),
[spec](superpowers/specs/2026-10-15-jasper-enterprise-presentation-design.md)). The SDK is 0.18.0, a breaking
boundary, and every in-repo manifest, the example external plugin and every guide range declare
`sdk = ">=0.18.0, <0.19"`. Spec 8.1's seven constants are renamed in the SDK and the jasper-ui model alike
(`ChoiceKind.EXCLUSIVE`, `ButtonPresentation.HELP`, `FormKind.PROMPT` and `PROMPT_COMPACT`, `LayoutRole.PROMPT_HEADER`,
`TextRole.PROMPT_TITLE`, `IconSize.PROMPT`), and the kit's private names follow (`Typography.Role.PROMPT_TITLE`, the
`PROMPT_*` metric roles and `ICON_PROMPT`, `MacTitleBar.Style.PROMPT`, `Stacks.PromptLayout`, `ViewReconciler.applyPrompt`), so
hero names only `Form.hero`; `PromptVocabularyTest` keeps it so. `compact-roles.tsv` and `default-type.tsv` changed
their keys only, and the headless gallery's PNGs for Slate Cobalt Dark and Light are byte-identical to the base once
their names are mapped, but for the label lines of the five sheets that print a renamed constant's name. `KitCompatibility` keys every removed type and member by the SDK that removed it and refuses a
plugin that still reads a renamed constant ("references removed SDK member X; rebuild it for SDK 0.18"); a 0.17
manifest is skipped by its range. `Capabilities.PRESENTATION_PROVIDE` exists; the SDK Javadoc and
`docs/plugin-ui.md` carry spec 8.3's re-documented contracts, and "settings never use checkboxes" is gone. Planned
deviations (plan, "Planned deviations"): (1) spec row 3 is five plans, 3a to 3e, and THD's rebuild is this hand-off;
(2) the host refuses a manifest declaring `presentation.provide` until plan 5 adds it to `DescriptorParser.CAPABILITIES`
with the `presentation` key and the consent text; (3) removed types carry their version too, and their message says
"type"; (4) 8.3 is one paragraph in `docs/plugin-ui.md`; (5) Remote's `ExistingItemsPanel` header id is
`existing.header`; (6) jasper-app's test task declares every source tree `PromptVocabularyTest` reads. The gate cannot
see a constant named by a string (`valueOf`, a qualified constant label in a pattern `switch`); linkage containment
still applies. THD Sourcing Crypto (`~/projects/thd-sourcing-crypto-jasper-plugin`, branch `jasper-sdk-0.17.1` at
`3740e4e`) is skipped on 0.18 until it is rebuilt there: `CryptoForm.java:112` and `CryptoFormTest.java:61` read
`Nodes.ChoiceKind.SEGMENTED` (now `EXCLUSIVE`), and its `plugin.toml` (`sdk = ">=0.16.1, <0.18"`) and
`PluginPackagingTest.java:49` move to `>=0.18.0, <0.19`, built against the 0.18.0 jars `sdkRelease` publishes; rebuild
once plans 3b to 3d have settled 0.18's additions (its Mode choice has two options, within 3b's rule of two to four
for EXCLUSIVE). `./gradlew check --continue`: **TOTALS**. GUI checks for the user:
none new (nothing visible changed). Not merged, not pushed.
```

Then insert, after this plan's first line (`# Enterprise presentation 3a: SDK 0.18 renames Implementation Plan`) and a
blank line:

```markdown
> **Status (2026-10-15): executed on `claude/enterprise-sdk-0-18-renames` (`FIRST..LAST`).** Deviations from this
> plan's text: none beyond its "Planned deviations".
```

replacing `FIRST..LAST` the same way, and naming any step whose text had to change instead of "none" (and the same in
the STATUS entry).

- [ ] **Step 3: Commit**

```bash
git add docs/STATUS.md docs/superpowers/plans/2026-10-15-enterprise-3a-sdk-0-18-renames.md
git commit -F - <<'EOF'
SDK 0.18 renames: status and the THD hand-off

STATUS records plan 3a with its planned deviations and what THD Sourcing
Crypto must change in its own repository to load on SDK 0.18.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```
