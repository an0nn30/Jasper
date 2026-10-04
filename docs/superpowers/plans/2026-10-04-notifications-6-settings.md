# Notifications Plan 6: Settings Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Status:** Implemented on branch `claude/notifications-6` (worktree `.worktrees/notifications-6`, cut from main
`0734c5c5`, where plans 1–5 are merged; `origin/main` was `192443b3`, two unrelated tab-width commits ahead, when this
plan was written). Executed natively, the user's choice, with one whole-branch review at the end. Deviations from the
plan text: none in code. Task 2 added `import java.util.Map;` to `NotificationCenterTest`, as its note allows. Task 5
wrote this banner and the STATUS entry before the final review, then updated both with its outcome. Final review:
pending. `./gradlew check --continue`: **8767 tests, 0 failures, 0 errors, 6 skipped**.

**Goal:** Let the user choose how each notification group appears (card, sticky card, history only or off), in
`config.toml` under `[notifications.groups]` and on the Settings › Notifications page, applied live.

**Architecture:**
- **Config.** `notifications.groups` is one string-map setting: group id → `"card"`, `"sticky"`, `"history"` or
  `"off"`. A string map can now report unusable entries as warnings: `SettingKind.MapRules` gains a severity. A
  WARNING map ignores a bad entry, including one of the wrong TOML type, instead of rejecting the file.
  `ConfigService` treats an empty table as an absent one, and `SettingWriter` removes a table once its last entry
  goes.
- **Delivery.** `NotificationCenter.useModes(Map<String, DisplayMode>)` overrides groups' declared defaults.
  `JasperApplication` pushes the configured modes into it from every published configuration state.
- **Settings UI.** The Notifications page gains a Groups section. It has one row for the setting, edited by a custom
  `NotificationGroupsEditor`: one line per known group (name, who declared it, a mode combo marking the default, and
  Reset). Entries for groups not known now are carried through untouched. The application supplies the groups as
  plain strings through `NotificationGroupChoices`, so `settingsui` does not depend on the notifications package.

**Tech Stack:** Java 25 (JetBrains Runtime 25), Swing with FlatLaf, tomlj via the app's `settings` package, JUnit 5,
AssertJ, the Gradle wrapper.

**Spec:** [`docs/superpowers/specs/2026-10-02-jasper-notifications-design.md`](../specs/2026-10-02-jasper-notifications-design.md)
(plan 6 of the six in its section 14; sections 2, 7 and 10 are this plan's). Read it before starting.

## Global Constraints

**Build and process**
- Java 25 on the JetBrains Runtime; use `./gradlew`, never a system Gradle.
- Tests are headless. Never launch the GUI or the benchmark.
- Never commit on `main`; never push without the user. End every commit message with:
  ```
  🤖 Generated with Claude Code at The Home Depot

  Co-Authored-By: Claude <noreply@anthropic.com>
  ```
- MVP: no compatibility shims or deprecations (change call sites instead). No animations.

**The setting (spec section 10)**
- The config:
  ```toml
  [notifications]
  long_command_seconds = 10        # unchanged; 0 disables long-command notifications
  system = true                    # OS notifications while Jasper is in the background
  [notifications.groups]
  "jasper.commands" = "history"    # card | sticky | history | off
  "dev.jasper.remote.activities" = "off"
  ```
- "Settings › Notifications page: the system-notifications checkbox, the long-command threshold, and a table of every
  known group (app groups and each running plugin's declared groups): name, owner, a mode combo with a '(default)'
  marker, and Reset. Changes apply live. Entries for groups not currently known (a disabled plugin) are preserved. An
  invalid mode is a config warning and the group's default applies."

**Package boundaries**
- `dev.jasper.app.settingsui` may depend only on settings, config, windows, platform and lifecycle (its
  package-info). It must not import `dev.jasper.app.notifications`.
- `dev.jasper.app.notifications` must not import `config`.

**Source hygiene and tests**
- No raw control, private-use or unpaired-surrogate characters in source (AGENTS.md Python check).
- `CorePagesTest` requires every core setting on exactly one page, with the page tree unchanged.
- `ConfigTemplateTest` requires a `| \`notifications.groups\` |` row in docs/configuration.md and a clean parse of
  config.example.toml, with no uncommented map entries.

## Spec interpretations (recorded deviations)

Recorded here and, at completion, in `docs/STATUS.md`:

1. **One string-map setting.** Unusable entries are warnings and are ignored, so the group's default applies. That
   covers a key that is not a group id, a mode other than the four lowercase tokens, and a value that is not a string.
   They never reject the file. (Other string maps such as `terminal.env` keep their errors.) A warning does not block
   Keymap saves, while an error would.
2. **Modes are lowercase only:** `"card"`, `"sticky"`, `"history"`, `"off"`. `"Card"` is a warning.
3. **Choosing a group's default mode, or its Reset, removes the group's entry.** A group whose entry the user has not
   touched keeps it exactly as written, even an entry that names the group's default (so a plugin later changing its
   default does not override a deliberate choice).
4. **Entries for groups not known now are preserved through Settings saves**, for example a plugin that is disabled or
   not running yet. The reader keeps every valid entry, and the editor carries entries it does not list.
5. **"Every known group" means every group declared since launch,** in declaration order: Jasper's first, then each
   plugin's as it starts. A plugin's groups stay listed after it stops, because groups are never unregistered. The
   list is read when the page opens and again on each configuration refresh; a plugin that starts while Settings is
   open appears after the next refresh or reopening.
6. **The owner column** shows "Jasper" for Jasper's groups and the plugin's display name for a plugin's.
7. **Live means from the next post on.** Each published configuration state pushes the modes into the center.
   Notifications already in history, cards already showing and cards already waiting stay as they are.
8. **The row's own Reset**, the generic one beside the Groups table, resets every group (it removes the whole table).
9. **Removing a table's last entry removes the table**, so an emptied `[notifications.groups]` leaves no bare header.
   An empty table in the file counts as absent when saving, so a file created from the template, whose bare table
   headers are uncommented, saves cleanly. This also fixes the same latent problem for `terminal.env`'s Reset.
10. **Plain strings across the boundary.** The groups and their default modes reach `settingsui` as plain strings
    through `NotificationGroupChoices`. `config` validates modes and group ids with its own rules. A test pins both
    to `DisplayMode` and `NotificationGroup`.

## Review Focus

1. **A hand-edited `[notifications.groups]` with bad entries** (`"Card"`, `3`, a key with spaces or capitals, a nested
   table). Expect warnings at each entry, the good entries applied, the file never rejected, and Keymap saves not
   blocked. Pinned by Task 1's `warningTablesIgnoreUnusableEntriesWithoutRejectingTheFile` and Task 4's
   `unusableEntriesAreWarningsAndTheRestApplies`.
2. **Entries for a plugin that is not running.** They must survive the user changing another group in Settings and
   saving. Pinned by Task 3's `entriesForGroupsNotKnownNowAreKept` and Task 4's
   `notificationGroupsKeepOtherGroupsAndAnEmptiedTableLeavesNoHeader`.
3. **Removing the last override, Reset, and a first save from no config file at all.** The save must succeed, leave no
   empty table, and leave no pending change in the dialog afterwards. Pinned by Task 1's
   `resettingATableRemovesItsEntriesAndTheTableLeftEmpty` and `aTableStartsFromTheTemplatesEmptyHeader`, and Task
   4's `aFirstGroupModeIsSavedIntoTheTemplate`.
4. **config.toml edited on disk while Settings is open.** The dialog re-shows the table: no spurious pending edit, and
   an untouched explicit entry stays. Pinned by Task 3's `showingIsNotAnEditAndAnUntouchedEntryStaysExactlyAsWritten`.
5. **A mode changed while that group's card shows or waits.** Only later posts follow the new mode. Pinned by Task 2's
   `changingAModeLeavesShowingAndWaitingCardsAlone`.

## File structure

**Create:**
- `jasper-app/src/main/java/dev/jasper/app/settingsui/NotificationGroupChoices.java`
- `jasper-app/src/main/java/dev/jasper/app/settingsui/NotificationGroupsEditor.java`
- Tests:
  - `settingsui/NotificationGroupsEditorTest`
  - `notifications/DisplayModeTest`
  - `config/NotificationGroupsSettingTest`
  - `application/JasperApplicationGroupModesTest`

**Modify:**
- **Settings layer:** `settings/SettingKind` (MapRules severity), `settings/SettingWriter`, `config/ConfigService`
  (empty table ≡ absent), `config/CoreSettings` (`NOTIFICATIONS_GROUPS`, `TERMINAL_ENV`'s rules).
- **Settings UI:** `settingsui/Editors` (factory), `settingsui/CorePages` (the Groups section and the choices).
- **Notifications:** `notifications/DisplayMode` (tokens), `notifications/NotificationCenter` (`useModes`).
- **Application:** `application/JasperApplication` (modes and choices).
- **Config files:** `config.example.toml`, `docs/configuration.md`.
- **Tests:** `SettingKindTest`, `SettingsSaveTest`, `CoreSettingsTest`, `CorePagesTest`, `NotificationCenterTest`.
- **Docs:** `docs/notifications.md`, `docs/configuration.md`, `docs/plugin-authoring.md`, `docs/STATUS.md`, the spec's
  and this plan's status banners.

All Java paths above are under `jasper-app/src/main/java/dev/jasper/app/` (tests under
`jasper-app/src/test/java/dev/jasper/app/`).

---

### Task 1: Warning tables, and empty tables that save cleanly

**Files:**
- Modify: `jasper-app/src/main/java/dev/jasper/app/settings/SettingKind.java` (`MapRules`, `StringMap.read`)
- Modify: `jasper-app/src/main/java/dev/jasper/app/settings/SettingWriter.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/config/ConfigService.java` (two comparisons in `save`)
- Modify: `jasper-app/src/main/java/dev/jasper/app/config/CoreSettings.java` (`TERMINAL_ENV`'s rules)
- Test: `jasper-app/src/test/java/dev/jasper/app/settings/SettingKindTest.java`,
  `jasper-app/src/test/java/dev/jasper/app/config/SettingsSaveTest.java`

**Interfaces:**
- Consumes: nothing.
- Produces:
  - `SettingKind.MapRules(Predicate<String> validName, String invalidName, Set<String> reserved, String reservedName, Predicate<String> validValue, String invalidValue, String valueType, Reading.Severity severity)`.
  - A WARNING map reports an unusable name, value or entry type as a WARNING and omits the entry, never rejecting the
    file. An ERROR map behaves as before.
  - Removing a string-map setting's value removes its table when empty.
  - `ConfigService.save` compares an empty string map as absent.

- [ ] **Step 1: Write the failing tests**

In `jasper-app/src/test/java/dev/jasper/app/settings/SettingKindTest.java`, in `stringMapsReportEachEntryAtItsOwnKey`,
replace

```java
            value -> value.indexOf('\0') < 0, "Bad value.", "a string environment value");
```

with

```java
            value -> value.indexOf('\0') < 0, "Bad value.", "a string environment value", Reading.Severity.ERROR);
```

and add this test after it:

```java
    @Test void warningTablesIgnoreUnusableEntriesWithoutRejectingTheFile() {
        var rules = new SettingKind.MapRules(name -> name.matches("[a-z.]+"), "Bad id.", Set.of(), "",
            Set.of("card", "off")::contains, "Bad mode.", "a display mode", Reading.Severity.WARNING);
        var kind = SettingKind.stringMap(rules);
        var reading = kind.read(toml("{ 'a.b' = 'card', 'A B' = 'off', c = 'CARD', d = 3 }"), Map.of());
        assertThat(reading.value()).containsExactly(Map.entry("a.b", "card"));
        assertThat(reading.rejectsFile()).isFalse();
        assertThat(reading.issues()).extracting(Reading.Issue::subPath, Reading.Issue::severity, Reading.Issue::message)
            .containsExactlyInAnyOrder(tuple(List.of("A B"), Reading.Severity.WARNING, "Bad id."),
                tuple(List.of("c"), Reading.Severity.WARNING, "Bad mode."),
                tuple(List.of("d"), Reading.Severity.WARNING, "Expected a display mode; entry ignored."));
    }
```

In `jasper-app/src/test/java/dev/jasper/app/config/SettingsSaveTest.java`, add before the class's closing brace:

```java
    @Test void resettingATableRemovesItsEntriesAndTheTableLeftEmpty() throws Exception {
        Path file = directory.resolve("config.toml");
        Files.writeString(file, "[terminal.env]\nA = \"1\"\n\n[window]\ntab_height = 44\n");
        try (var service = new ConfigService(file, true)) {
            var state = save(service, edit(Map.of(CoreSettings.TERMINAL_ENV, Optional.empty()),
                Map.of(CoreSettings.TERMINAL_ENV, Optional.of(Map.of("A", "1")))));
            assertThat(Files.readString(file)).doesNotContain("[terminal.env]").contains("[window]\ntab_height = 44\n");
            assertThat(state.values().saved(CoreSettings.TERMINAL_ENV)).isEmpty();
            assertThat(state.snapshot().tabHeight()).isEqualTo(44);
        }
    }

    /** The template's table headers are uncommented, so a new file already holds an empty [terminal.env]. */
    @Test void aTableStartsFromTheTemplatesEmptyHeader() throws Exception {
        Path file = directory.resolve("new/config.toml");
        try (var service = new ConfigService(file, true)) {
            var state = save(service, edit(Map.of(CoreSettings.TERMINAL_ENV, Optional.of(Map.of("A", "1"))),
                Map.of(CoreSettings.TERMINAL_ENV, Optional.empty())));
            assertThat(state.snapshot().terminal().env()).containsExactly(Map.entry("A", "1"));
            assertThat(Files.readString(file)).containsOnlyOnce("[terminal.env]");
            assertThat(state.diagnostics()).isEmpty();
        }
    }
```

- [ ] **Step 2: Run them to see them fail**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.settings.SettingKindTest' --tests 'dev.jasper.app.config.SettingsSaveTest'`
Expected: FAIL to compile, because `MapRules` has no eighth component.

After you add only the component (Step 3, item 1), the two save tests still fail:
- `resettingATableRemovesItsEntriesAndTheTableLeftEmpty` fails with "Cannot safely save these settings; review
  config.toml.";
- `aTableStartsFromTheTemplatesEmptyHeader` fails with "terminal.env changed on disk. Review the new value and try
  again.".

Run them at that point to see both failures before finishing Step 3.

- [ ] **Step 3: Implement**

1. In `jasper-app/src/main/java/dev/jasper/app/settings/SettingKind.java`, replace the `MapRules` record and its
   comment:

```java
    /**
     * The rules a table of string values follows. Each message is the whole diagnostic text.
     *
     * @param validName which entry names are allowed
     * @param invalidName the error for any other name; the entry is omitted
     * @param reserved names the application owns
     * @param reservedName the warning for a reserved name; the entry is omitted
     * @param validValue which values are allowed
     * @param invalidValue the error for any other value; the entry is omitted
     * @param valueType what a value must be, for the wrong-type message ("a string environment value")
     */
    record MapRules(Predicate<String> validName, String invalidName, Set<String> reserved, String reservedName,
                    Predicate<String> validValue, String invalidValue, String valueType) {
        public MapRules { reserved = Set.copyOf(reserved); }
    }
```

   with

```java
    /**
     * The rules a table of string values follows. Each message is the whole diagnostic text.
     *
     * @param validName which entry names are allowed
     * @param invalidName the diagnostic for any other name; the entry is omitted
     * @param reserved names the application owns
     * @param reservedName the warning for a reserved name; the entry is omitted
     * @param validValue which values are allowed
     * @param invalidValue the diagnostic for any other value; the entry is omitted
     * @param valueType what a value must be, for the wrong-type message ("a string environment value")
     * @param severity how serious an unusable entry is: ERROR where every entry matters (environment variables);
     *     WARNING where an unusable entry just leaves its default in place, which also ignores an entry of the wrong
     *     TOML type instead of rejecting the file
     */
    record MapRules(Predicate<String> validName, String invalidName, Set<String> reserved, String reservedName,
                    Predicate<String> validValue, String invalidValue, String valueType, Reading.Severity severity) {
        public MapRules {
            reserved = Set.copyOf(reserved);
            java.util.Objects.requireNonNull(severity, "severity");
        }
    }
```

2. In `StringMap.read`, replace

```java
                if (!(entry instanceof String text))
                    issues.add(new Reading.Issue(at, Reading.Severity.ERROR,
                        "Expected " + rules.valueType() + "; configuration was not applied.", true));
                else if (!rules.validName().test(name)) issues.add(new Reading.Issue(at, Reading.Severity.ERROR, rules.invalidName(), false));
                else if (rules.reserved().contains(name)) issues.add(new Reading.Issue(at, Reading.Severity.WARNING, rules.reservedName(), false));
                else if (!rules.validValue().test(text)) issues.add(new Reading.Issue(at, Reading.Severity.ERROR, rules.invalidValue(), false));
                else values.put(name, text);
```

   with

```java
                if (!(entry instanceof String text)) {
                    if (rules.severity() == Reading.Severity.ERROR)
                        issues.add(new Reading.Issue(at, Reading.Severity.ERROR,
                            "Expected " + rules.valueType() + "; configuration was not applied.", true));
                    else issues.add(new Reading.Issue(at, Reading.Severity.WARNING,
                        "Expected " + rules.valueType() + "; entry ignored.", false));
                }
                else if (!rules.validName().test(name)) issues.add(new Reading.Issue(at, rules.severity(), rules.invalidName(), false));
                else if (rules.reserved().contains(name)) issues.add(new Reading.Issue(at, Reading.Severity.WARNING, rules.reservedName(), false));
                else if (!rules.validValue().test(text)) issues.add(new Reading.Issue(at, rules.severity(), rules.invalidValue(), false));
                else values.put(name, text);
```

3. In `jasper-app/src/main/java/dev/jasper/app/config/CoreSettings.java`, in `TERMINAL_ENV`, replace

```java
            CoreSettings::noNul, "Environment values must contain no NUL; entry omitted.", "a string environment value")), Map.<String, String>of())
```

   with

```java
            CoreSettings::noNul, "Environment values must contain no NUL; entry omitted.", "a string environment value",
            dev.jasper.app.settings.Reading.Severity.ERROR)), Map.<String, String>of())
```

4. In `jasper-app/src/main/java/dev/jasper/app/settings/SettingWriter.java`, replace

```java
        if (entries == null) return next;
```

   with

```java
        // A table with no entries left goes too: an empty [table] header would read back as an empty table.
        if (entries == null) return next.removeTableIfEmpty(setting.key());
```

5. In `jasper-app/src/main/java/dev/jasper/app/config/ConfigService.java`, in `save`, replace

```java
                        if (!parsed.values().saved(setting).equals(edit.expected().get(setting)))
```

   with

```java
                        if (!comparable(setting, parsed.values().saved(setting)).equals(comparable(setting, edit.expected().get(setting))))
```

   and replace

```java
                            .anyMatch(change -> !checked.values().saved(change.getKey()).equals(change.getValue())))
```

   with

```java
                            .anyMatch(change -> !comparable(change.getKey(), checked.values().saved(change.getKey()))
                                .equals(comparable(change.getKey(), change.getValue()))))
```

   and add this method to `ConfigService`, directly after `save`:

```java
    /**
     * A table setting with no entries reads the same as an absent one: an empty {@code [table]} header (the template
     * has them uncommented) holds nothing, so it must not count as a change made on disk.
     */
    private static Optional<?> comparable(Setting<?> setting, Optional<?> value) {
        if (!(setting.kind() instanceof dev.jasper.app.settings.SettingKind.StringMap)) return value;
        return value.filter(map -> !((java.util.Map<?, ?>) map).isEmpty());
    }
```

- [ ] **Step 4: Run them to see them pass**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.settings.*' --tests 'dev.jasper.app.config.*' --tests 'dev.jasper.app.settingsui.*' --tests 'dev.jasper.app.workspace.ExpandedConfigTest'`
Expected: PASS (`SettingKindTest`, `SettingsSaveTest` with the two new tests, the config and settings UI suites
unchanged).

- [ ] **Step 5: Commit**

```bash
git add jasper-app/src/main/java/dev/jasper/app/settings/SettingKind.java jasper-app/src/main/java/dev/jasper/app/settings/SettingWriter.java jasper-app/src/main/java/dev/jasper/app/config/ConfigService.java jasper-app/src/main/java/dev/jasper/app/config/CoreSettings.java jasper-app/src/test/java/dev/jasper/app/settings/SettingKindTest.java jasper-app/src/test/java/dev/jasper/app/config/SettingsSaveTest.java
git commit -m "feat(settings): warning tables, and empty tables that save cleanly" -m "A string-map setting can report unusable entries as warnings and ignore them instead of rejecting the file; removing a table's last entry removes the table, and an empty table counts as absent when saving, so Reset and a first save from the template work for table settings." -m "🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>"
```

### Task 2: The center follows the user's modes

**Files:**
- Modify: `jasper-app/src/main/java/dev/jasper/app/notifications/DisplayMode.java` (whole file)
- Modify: `jasper-app/src/main/java/dev/jasper/app/notifications/NotificationCenter.java`
- Create: `jasper-app/src/test/java/dev/jasper/app/notifications/DisplayModeTest.java`
- Test: `jasper-app/src/test/java/dev/jasper/app/notifications/NotificationCenterTest.java`

**Interfaces:**
- Consumes: nothing.
- Produces:
  - `DisplayMode.token()` (`"card"`, `"sticky"`, `"history"`, `"off"`)
  - `DisplayMode.parse(String) → Optional<DisplayMode>`
  - `NotificationCenter.useModes(Map<String, DisplayMode>)`
  - `NotificationCenter.mode(String)` now returns the user's mode, else the group's default, else OFF for an
    undeclared group.

- [ ] **Step 1: Write the failing tests**

Create `jasper-app/src/test/java/dev/jasper/app/notifications/DisplayModeTest.java`:

```java
package dev.jasper.app.notifications;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class DisplayModeTest {
    @Test void eachModeHasTheTokenConfigWritesAndParsesBack() {
        assertThat(DisplayMode.CARD.token()).isEqualTo("card");
        assertThat(DisplayMode.STICKY.token()).isEqualTo("sticky");
        assertThat(DisplayMode.HISTORY.token()).isEqualTo("history");
        assertThat(DisplayMode.OFF.token()).isEqualTo("off");
        for (DisplayMode mode : DisplayMode.values()) assertThat(DisplayMode.parse(mode.token())).contains(mode);
    }

    @Test void anythingElseIsNoMode() {
        assertThat(DisplayMode.parse("Card")).isEmpty();
        assertThat(DisplayMode.parse("CARD")).isEmpty();
        assertThat(DisplayMode.parse("")).isEmpty();
        assertThat(DisplayMode.parse(null)).isEmpty();
    }
}
```

In `jasper-app/src/test/java/dev/jasper/app/notifications/NotificationCenterTest.java`, add before the class's closing
brace:

```java
    @Test void theUsersModeReplacesAGroupsDefault() {
        var center = center();
        center.useModes(Map.of(CARDS.id(), DisplayMode.HISTORY));
        assertThat(center.mode(CARDS.id())).isEqualTo(DisplayMode.HISTORY);
        center.post(request(CARDS, "quiet"));
        assertThat(kinds()).containsExactly("Upserted");
        center.useModes(Map.of(CARDS.id(), DisplayMode.OFF));
        assertThat(center.post(request(CARDS, "dropped"))).isEmpty();
        center.useModes(Map.of());
        assertThat(center.mode(CARDS.id())).isEqualTo(DisplayMode.CARD);
        center.post(request(CARDS, "loud"));
        assertThat(kinds()).containsExactly("Upserted", "Upserted", "CardRequested");
    }

    @Test void aModeForAnUndeclaredGroupDeclaresNothing() {
        var center = center();
        center.useModes(Map.of("test.unknown", DisplayMode.CARD));
        assertThat(center.mode("test.unknown")).isEqualTo(DisplayMode.OFF);
        assertThat(center.post(NotificationRequest.of("test.unknown", Severity.INFO, "nobody"))).isEmpty();
    }

    /** A mode changed while a card shows or waits applies to later posts only. */
    @Test void changingAModeLeavesShowingAndWaitingCardsAlone() {
        var center = center();
        presence = Delivery.Presence.BACKGROUND;
        UUID waiting = center.post(request(STICKY, "waits")).orElseThrow();
        presence = Delivery.Presence.FOCUSED;
        UUID showing = center.post(request(CARDS, "shows")).orElseThrow();
        changes.clear();
        center.useModes(Map.of(STICKY.id(), DisplayMode.OFF, CARDS.id(), DisplayMode.OFF));
        assertThat(changes).isEmpty();
        center.focusReturned();
        assertThat(changes).singleElement().isInstanceOfSatisfying(NotificationCenter.Change.CardRequested.class,
            requested -> assertThat(requested.notification().id()).isEqualTo(waiting));
        assertThat(center.find(showing)).isPresent();
    }
```

(`NotificationCenterTest` already imports `java.util.Map`, `java.util.UUID` and `NotificationRequest`; if `Map` is
missing, add `import java.util.Map;`.)

- [ ] **Step 2: Run them to see them fail**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.notifications.DisplayModeTest' --tests 'dev.jasper.app.notifications.NotificationCenterTest'`
Expected: FAIL to compile: `DisplayMode.token`, `DisplayMode.parse` and `NotificationCenter.useModes` do not exist.

- [ ] **Step 3: Implement**

Replace `jasper-app/src/main/java/dev/jasper/app/notifications/DisplayMode.java` with:

```java
package dev.jasper.app.notifications;

import java.util.Locale;
import java.util.Optional;

/** How a group's notifications appear: an auto-hiding card, a sticky card, history only, or not at all. */
public enum DisplayMode {
    CARD, STICKY, HISTORY, OFF;

    /** How {@code notifications.groups} writes it: "card", "sticky", "history" or "off". */
    public String token() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** The mode {@code token} names in {@code notifications.groups}, if it names one. */
    public static Optional<DisplayMode> parse(String token) {
        for (DisplayMode mode : values()) if (mode.token().equals(token)) return Optional.of(mode);
        return Optional.empty();
    }
}
```

In `jasper-app/src/main/java/dev/jasper/app/notifications/NotificationCenter.java`:

1. Replace

```java
    private Consumer<Throwable> actionFailures = failure -> { };
```

   with

```java
    private Consumer<Throwable> actionFailures = failure -> { };
    /** The user's modes by group id, from notifications.groups. */
    private Map<String, DisplayMode> modes = Map.of();
```

2. Replace

```java
    /** How the group's notifications appear: its declared default; {@code OFF} for an undeclared group. */
    public DisplayMode mode(String groupId) {
        requireEdt();
        NotificationGroup group = groups.get(groupId);
        return group == null ? DisplayMode.OFF : group.defaultMode();
    }
```

   with

```java
    /**
     * The user's modes by group id, from {@code notifications.groups}: a listed group's notifications appear that way
     * from the next post on; a group not listed keeps its declared default. What is in history, showing or waiting
     * stays as it is.
     */
    public void useModes(Map<String, DisplayMode> modes) {
        requireEdt();
        this.modes = Map.copyOf(modes);
    }

    /** How the group's notifications appear: the user's mode, else its declared default; {@code OFF} for an undeclared group. */
    public DisplayMode mode(String groupId) {
        requireEdt();
        NotificationGroup group = groups.get(groupId);
        return group == null ? DisplayMode.OFF : modes.getOrDefault(groupId, group.defaultMode());
    }
```

- [ ] **Step 4: Run them to see them pass**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.notifications.*' --tests 'dev.jasper.app.application.*' --tests 'dev.jasper.app.plugins.*'`
Expected: PASS (`DisplayModeTest` 2/2, the three new `NotificationCenterTest` cases).

- [ ] **Step 5: Commit**

```bash
git add jasper-app/src/main/java/dev/jasper/app/notifications/DisplayMode.java jasper-app/src/main/java/dev/jasper/app/notifications/NotificationCenter.java jasper-app/src/test/java/dev/jasper/app/notifications/DisplayModeTest.java jasper-app/src/test/java/dev/jasper/app/notifications/NotificationCenterTest.java
git commit -m "feat(notifications): the center follows the user's mode for each group" -m "NotificationCenter.useModes replaces groups' declared defaults from the next post on; DisplayMode gains the tokens notifications.groups writes." -m "🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>"
```

### Task 3: The groups editor

**Files:**
- Create: `jasper-app/src/main/java/dev/jasper/app/settingsui/NotificationGroupChoices.java`
- Create: `jasper-app/src/main/java/dev/jasper/app/settingsui/NotificationGroupsEditor.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/settingsui/Editors.java` (one factory method)
- Create: `jasper-app/src/test/java/dev/jasper/app/settingsui/NotificationGroupsEditorTest.java`

**Interfaces:**
- Consumes: nothing.
- Produces:
  - Public, `NotificationGroupChoices`:
    - `NotificationGroupChoices(Supplier<List<Group>> groups)`
    - `NotificationGroupChoices.NONE`
    - `NotificationGroupChoices.Group(String id, String name, String owner, String defaultMode)`
  - `Editors.notificationGroups(NotificationGroupChoices) → Editors.Factory<Map<String, String>>`
  - Package-private, `NotificationGroupsEditor`:
    - `NotificationGroupsEditor(NotificationGroupChoices)`
    - `static final Map<String, String> MODES`: token → name, in order
    - `combo(String id)`, `reset(String id)` (for tests)

- [ ] **Step 1: Write the failing tests**

Create `jasper-app/src/test/java/dev/jasper/app/settingsui/NotificationGroupsEditorTest.java`:

```java
package dev.jasper.app.settingsui;

import dev.jasper.app.config.ConfigLoader;
import dev.jasper.app.config.CoreSettings;
import dev.jasper.app.testsupport.EdtTestExtension;
import java.awt.Component;
import java.awt.Container;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.swing.JLabel;
import javax.swing.JList;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(EdtTestExtension.class)
class NotificationGroupsEditorTest {
    private static final NotificationGroupChoices.Group COMMANDS =
        new NotificationGroupChoices.Group("jasper.commands", "Long-running commands", "Jasper", "card");
    private static final NotificationGroupChoices.Group CONFIG =
        new NotificationGroupChoices.Group("jasper.config", "Configuration", "Jasper", "sticky");
    private static final NotificationGroupChoices.Group REMOTE =
        new NotificationGroupChoices.Group("dev.jasper.remote.activities", "Remote activities", "Remote", "card");

    private List<NotificationGroupChoices.Group> groups = List.of(COMMANDS, CONFIG);
    private int edits;

    private NotificationGroupsEditor editor() {
        var editor = new NotificationGroupsEditor(new NotificationGroupChoices(() -> groups));
        editor.onEdit(() -> edits++);
        return editor;
    }

    private static String rendered(NotificationGroupsEditor editor, String id, String token) {
        var combo = editor.combo(id);
        var label = (JLabel) combo.getRenderer().getListCellRendererComponent(new JList<>(), token, 0, false, false);
        return label.getText();
    }

    private static List<String> labels(Container container) {
        var texts = new ArrayList<String>();
        for (Component child : container.getComponents()) {
            if (child instanceof JLabel label) texts.add(label.getText());
            if (child instanceof Container nested) texts.addAll(labels(nested));
        }
        return texts;
    }

    @Test void everyGroupShowsItsModeWithTheDefaultMarked() {
        var editor = editor();
        editor.show(Map.of());
        assertThat(labels(editor.component())).contains("Long-running commands", "Configuration", "Jasper");
        assertThat(editor.combo(COMMANDS.id()).getSelectedItem()).isEqualTo("card");
        assertThat(editor.combo(CONFIG.id()).getSelectedItem()).isEqualTo("sticky");
        assertThat(rendered(editor, COMMANDS.id(), "card")).isEqualTo("Card (default)");
        assertThat(rendered(editor, COMMANDS.id(), "history")).isEqualTo("History only");
        assertThat(rendered(editor, CONFIG.id(), "sticky")).isEqualTo("Sticky card (default)");
        assertThat(editor.reset(COMMANDS.id()).isEnabled()).isFalse();
        assertThat(edits).isZero();
    }

    @Test void choosingAModeAddsAnEntryAndChoosingTheDefaultRemovesIt() {
        var editor = editor();
        editor.show(Map.of());
        editor.combo(COMMANDS.id()).setSelectedItem("history");
        assertThat(editor.value()).isEqualTo(Map.of(COMMANDS.id(), "history"));
        assertThat(editor.reset(COMMANDS.id()).isEnabled()).isTrue();
        editor.combo(COMMANDS.id()).setSelectedItem("card");
        assertThat(editor.value()).isEmpty();
        assertThat(edits).isEqualTo(2);
    }

    @Test void resetRemovesTheGroupsEntry() {
        var editor = editor();
        editor.show(Map.of(COMMANDS.id(), "off"));
        assertThat(editor.combo(COMMANDS.id()).getSelectedItem()).isEqualTo("off");
        editor.reset(COMMANDS.id()).doClick();
        assertThat(editor.value()).isEmpty();
        assertThat(editor.combo(COMMANDS.id()).getSelectedItem()).isEqualTo("card");
        assertThat(edits).isEqualTo(1);
    }

    /** A plugin that is not running now keeps its entries through any edit of the others. */
    @Test void entriesForGroupsNotKnownNowAreKept() {
        var editor = editor();
        editor.show(Map.of(REMOTE.id(), "off", CONFIG.id(), "history"));
        editor.combo(COMMANDS.id()).setSelectedItem("off");
        assertThat(editor.value()).isEqualTo(Map.of(REMOTE.id(), "off", CONFIG.id(), "history", COMMANDS.id(), "off"));
    }

    /** What the dialog re-shows after config.toml changes on disk is not an edit, and an untouched entry stays as written. */
    @Test void showingIsNotAnEditAndAnUntouchedEntryStaysExactlyAsWritten() {
        var editor = editor();
        editor.show(Map.of(COMMANDS.id(), "card"));
        assertThat(edits).isZero();
        assertThat(editor.value()).isEqualTo(Map.of(COMMANDS.id(), "card"));
        assertThat(editor.reset(COMMANDS.id()).isEnabled()).as("an entry is there to reset").isTrue();
        editor.combo(CONFIG.id()).setSelectedItem("off");
        assertThat(editor.value()).isEqualTo(Map.of(COMMANDS.id(), "card", CONFIG.id(), "off"));
        editor.show(Map.of(CONFIG.id(), "history"));
        assertThat(editor.value()).as("showing replaces the user's choices").isEqualTo(Map.of(CONFIG.id(), "history"));
        assertThat(edits).isEqualTo(1);
    }

    @Test void refreshListsGroupsDeclaredSinceAndKeepsTheChoices() {
        var editor = editor();
        editor.show(Map.of());
        editor.combo(COMMANDS.id()).setSelectedItem("history");
        groups = List.of(COMMANDS, CONFIG, REMOTE);
        editor.refresh();
        assertThat(editor.combo(REMOTE.id()).getSelectedItem()).isEqualTo("card");
        assertThat(editor.combo(COMMANDS.id()).getSelectedItem()).isEqualTo("history");
        assertThat(editor.value()).isEqualTo(Map.of(COMMANDS.id(), "history"));
        assertThat(labels(editor.component())).contains("Remote activities", "Remote");
    }

    @Test void withNoGroupsYetTheEditorSaysSo() {
        groups = List.of();
        var editor = editor();
        editor.show(Map.of(REMOTE.id(), "off"));
        assertThat(labels(editor.component())).contains("No notification groups yet.");
        assertThat(editor.value()).isEqualTo(Map.of(REMOTE.id(), "off"));
    }

    @Test void theFactoryBuildsAFullWidthEditorThatRemovesTheTableAtItsDefault() {
        var context = new PageContext(new SettingsDraft(ConfigLoader.parse(Path.of("config.toml"), "", true).values()),
            id -> { }, Runnable::run, (title, initial) -> Optional.empty(), () -> { });
        var factory = Editors.notificationGroups(new NotificationGroupChoices(() -> groups));
        var editor = factory.build().apply(CoreSettings.TERMINAL_ENV, context);
        assertThat(editor).isInstanceOf(NotificationGroupsEditor.class);
        assertThat(editor.labelled()).isTrue();
        assertThat(editor.removesAtDefault()).isTrue();
        assertThat(factory.terms()).contains("card", "sticky", "history only", "off");
    }
}
```

(The factory test builds against `TERMINAL_ENV` only because Task 4 adds `NOTIFICATIONS_GROUPS`; the editor ignores
which map setting it edits. Task 4 switches the test to `NOTIFICATIONS_GROUPS`.)

- [ ] **Step 2: Run them to see them fail**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.settingsui.NotificationGroupsEditorTest'`
Expected: FAIL to compile. `NotificationGroupChoices`, `NotificationGroupsEditor` and `Editors.notificationGroups`
do not exist.

- [ ] **Step 3: Implement**

Create `jasper-app/src/main/java/dev/jasper/app/settingsui/NotificationGroupChoices.java`:

```java
package dev.jasper.app.settingsui;

import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * The notification groups the Notifications page lists, supplied by the application as plain text. EDT only.
 *
 * @param groups every group known now, in the order to list them
 */
public record NotificationGroupChoices(Supplier<List<Group>> groups) {
    /** No groups, for pages built without an application. */
    public static final NotificationGroupChoices NONE = new NotificationGroupChoices(List::of);

    public NotificationGroupChoices {
        Objects.requireNonNull(groups, "groups");
    }

    /**
     * One group.
     *
     * @param id its id: the key in {@code notifications.groups}
     * @param name the name it is listed with
     * @param owner who declared it, as shown: "Jasper" or a plugin's name
     * @param defaultMode how its notifications appear by default: "card", "sticky", "history" or "off"
     */
    public record Group(String id, String name, String owner, String defaultMode) {
        public Group {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(owner, "owner");
            Objects.requireNonNull(defaultMode, "defaultMode");
        }
    }
}
```

Create `jasper-app/src/main/java/dev/jasper/app/settingsui/NotificationGroupsEditor.java`:

```java
package dev.jasper.app.settingsui;

import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.UIManager;

/**
 * The {@code notifications.groups} editor: one line per known group with its name, who declared it, how its
 * notifications appear (the default marked) and a Reset. The value is the whole table. A group set back to its
 * default has no entry; a group the user has not touched keeps its entry exactly as written, even one naming the
 * default; and entries for groups not known now, such as a plugin that is not running, are kept as they are. EDT only.
 */
final class NotificationGroupsEditor implements ValueEditor<Map<String, String>> {
    /** The modes the combo offers, in order, with the names it shows. */
    static final Map<String, String> MODES = modes();

    private final NotificationGroupChoices choices;
    private final JPanel component = new JPanel(new GridBagLayout());
    private final List<Runnable> listeners = new ArrayList<>();
    private final Map<String, JComboBox<String>> combos = new LinkedHashMap<>();
    private final Map<String, JButton> resets = new HashMap<>();
    private List<NotificationGroupChoices.Group> groups = List.of();
    /** The table as last shown. */
    private Map<String, String> shown = Map.of();
    /** The user's choices since then, by group id; a null choice means "back to the default". */
    private final Map<String, String> chosen = new HashMap<>();
    private boolean showing;

    NotificationGroupsEditor(NotificationGroupChoices choices) {
        this.choices = Objects.requireNonNull(choices, "choices");
        component.setOpaque(false);
        rebuild(choices.groups().get());
    }

    @Override public JComponent component() { return component; }

    @Override public Map<String, String> value() {
        var value = new LinkedHashMap<>(shown);
        chosen.forEach((id, token) -> {
            if (token == null) value.remove(id);
            else value.put(id, token);
        });
        return value;
    }

    @Override public void show(Map<String, String> value) {
        shown = Map.copyOf(value);
        chosen.clear();
        update();
    }

    @Override public void onEdit(Runnable listener) { listeners.add(listener); }

    /** The table spans the page; the section above it names it. */
    @Override public boolean labelled() { return true; }

    /** No overrides at all removes the table, rather than writing an empty one. */
    @Override public boolean removesAtDefault() { return true; }

    /** Lists the groups declared since the page last looked, keeping what the user chose. */
    @Override public void refresh() {
        var next = choices.groups().get();
        if (!next.equals(groups)) rebuild(next);
    }

    JComboBox<String> combo(String id) { return combos.get(id); }

    JButton reset(String id) { return resets.get(id); }

    private void rebuild(List<NotificationGroupChoices.Group> next) {
        groups = List.copyOf(next);
        component.removeAll();
        combos.clear();
        resets.clear();
        if (groups.isEmpty()) {
            component.add(muted("No notification groups yet."), cell(0, 0));
        } else {
            component.add(heading("Group"), cell(0, 0));
            component.add(heading("From"), cell(1, 0));
            component.add(heading("Shows"), cell(2, 0));
            int y = 1;
            for (var group : groups) {
                var name = new JLabel(group.name());
                name.putClientProperty("html.disable", true);
                component.add(name, cell(0, y));
                component.add(muted(group.owner()), cell(1, y));
                var combo = new JComboBox<>(MODES.keySet().toArray(String[]::new));
                combo.setRenderer(renderer(group.defaultMode()));
                combo.getAccessibleContext().setAccessibleName(group.name() + ": how its notifications appear");
                combo.addActionListener(event -> {
                    if (!showing) choose(group, (String) combo.getSelectedItem());
                });
                component.add(combo, cell(2, y));
                var reset = Editors.Items.button("Reset", "Reset " + group.name() + " to its default", () -> choose(group, null));
                component.add(reset, cell(3, y));
                combos.put(group.id(), combo);
                resets.put(group.id(), reset);
                y++;
            }
        }
        update();
        component.revalidate();
        component.repaint();
    }

    /** The user chose {@code token} for {@code group}; null, or the group's default, means "no entry". */
    private void choose(NotificationGroupChoices.Group group, String token) {
        chosen.put(group.id(), token == null || token.equals(group.defaultMode()) ? null : token);
        update();
        for (Runnable listener : List.copyOf(listeners)) listener.run();
    }

    /** Shows the current value on every line without counting as an edit. */
    private void update() {
        Map<String, String> value = value();
        boolean was = showing;
        showing = true;
        try {
            for (var group : groups) {
                String token = value.get(group.id());
                combos.get(group.id()).setSelectedItem(token != null && MODES.containsKey(token) ? token : group.defaultMode());
                resets.get(group.id()).setEnabled(value.containsKey(group.id()));
            }
        } finally {
            showing = was;
        }
    }

    private static DefaultListCellRenderer renderer(String defaultMode) {
        return new DefaultListCellRenderer() {
            @Override public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                                    boolean selected, boolean focused) {
                String token = String.valueOf(value);
                String text = MODES.getOrDefault(token, token) + (token.equals(defaultMode) ? " (default)" : "");
                return super.getListCellRendererComponent(list, text, index, selected, focused);
            }
        };
    }

    private static JLabel heading(String text) {
        var label = new JLabel(text);
        label.setFont(label.getFont().deriveFont(Font.BOLD));
        return label;
    }

    private static JLabel muted(String text) {
        var label = new JLabel(text);
        label.putClientProperty("html.disable", true);
        Color color = UIManager.getColor("Label.disabledForeground");
        if (color != null) label.setForeground(color);
        return label;
    }

    private static GridBagConstraints cell(int x, int y) {
        var constraints = new GridBagConstraints();
        constraints.gridx = x;
        constraints.gridy = y;
        constraints.anchor = GridBagConstraints.WEST;
        constraints.insets = new Insets(y == 0 ? 0 : 2, 0, 2, 16);
        return constraints;
    }

    private static Map<String, String> modes() {
        var modes = new LinkedHashMap<String, String>();
        modes.put("card", "Card");
        modes.put("sticky", "Sticky card");
        modes.put("history", "History only");
        modes.put("off", "Off");
        return Collections.unmodifiableMap(modes);
    }
}
```

In `jasper-app/src/main/java/dev/jasper/app/settingsui/Editors.java`, add this factory method directly after the
`table()` factory method:

```java
    /** The {@code notifications.groups} editor: one line per group {@code choices} lists. */
    public static Factory<Map<String, String>> notificationGroups(NotificationGroupChoices choices) {
        return new Factory<>((setting, context) -> new NotificationGroupsEditor(choices),
            List.of("group", "card", "sticky", "history only", "off", "mute"));
    }
```

`Editors.Items` is the existing package-private helper class whose `static JButton button(String text, String name,
Runnable action)` the list editors use. If it is declared `private`, make it package-private: remove only the
`private`.

- [ ] **Step 4: Run them to see them pass**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.settingsui.*'`
Expected: PASS (`NotificationGroupsEditorTest` 8/8; the other settings UI tests unchanged).

- [ ] **Step 5: Commit**

```bash
git add jasper-app/src/main/java/dev/jasper/app/settingsui/NotificationGroupChoices.java jasper-app/src/main/java/dev/jasper/app/settingsui/NotificationGroupsEditor.java jasper-app/src/main/java/dev/jasper/app/settingsui/Editors.java jasper-app/src/test/java/dev/jasper/app/settingsui/NotificationGroupsEditorTest.java
git commit -m "feat(settings): an editor for each notification group's mode" -m "One line per known group with its name, who declared it, a mode combo marking the default and a Reset; the value is the whole table, keeping untouched entries and groups not known now." -m "🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>"
```

### Task 4: The setting, the page and the live modes

**Files:**
- Modify: `jasper-app/src/main/java/dev/jasper/app/config/CoreSettings.java` (`NOTIFICATIONS_GROUPS`, the schema)
- Modify: `jasper-app/src/main/java/dev/jasper/app/settingsui/CorePages.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/application/JasperApplication.java`
- Modify: `config.example.toml`, `docs/configuration.md` (the TOML block and the settings table)
- Create: `jasper-app/src/test/java/dev/jasper/app/config/NotificationGroupsSettingTest.java`
- Create: `jasper-app/src/test/java/dev/jasper/app/application/JasperApplicationGroupModesTest.java`
- Test: `jasper-app/src/test/java/dev/jasper/app/config/CoreSettingsTest.java`, `SettingsSaveTest.java`,
  `jasper-app/src/test/java/dev/jasper/app/settingsui/CorePagesTest.java`, `NotificationGroupsEditorTest.java`

**Interfaces:**
- Consumes:
  - Task 1's `MapRules(..., Reading.Severity severity)`, the empty-table save fixes
  - Task 2's `NotificationCenter.useModes`, `DisplayMode.token()`, `DisplayMode.parse`
  - Task 3's `Editors.notificationGroups`, `NotificationGroupChoices`, `NotificationGroupsEditor.combo`
- Produces:
  - `CoreSettings.NOTIFICATIONS_GROUPS` (`Setting<Map<String, String>>`, key `notifications.groups`, default `{}`)
  - Package-private: `CoreSettings.NOTIFICATION_MODES`, `CoreSettings.notificationGroupId(String)`
  - Package-private `CorePages.specs(..., boolean glass, NotificationGroupChoices groups)`
  - `CorePages.pages(ThemeChoices, SystemActions, ShellChoices, NotificationGroupChoices, Pages)`
  - Package-private static: `JasperApplication.groupModes(Map<String, String>)`,
    `JasperApplication.groupChoices(List<NotificationGroup>, Function<String, String>)`

- [ ] **Step 1: Write the failing tests**

Create `jasper-app/src/test/java/dev/jasper/app/config/NotificationGroupsSettingTest.java`:

```java
package dev.jasper.app.config;

import dev.jasper.app.notifications.DisplayMode;
import dev.jasper.app.notifications.NotificationGroup;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** notifications.groups: what it accepts agrees with the notification model, and what it does not is a warning. */
class NotificationGroupsSettingTest {
    @Test void theModesAreTheDisplayModesTokens() {
        assertThat(CoreSettings.NOTIFICATION_MODES)
            .containsExactlyInAnyOrderElementsOf(Arrays.stream(DisplayMode.values()).map(DisplayMode::token).toList());
    }

    @Test void aGroupIdIsWhatANotificationGroupAccepts() {
        for (String id : List.of("jasper.commands", "dev.jasper.remote.activities", "a", "x_y-z.9")) {
            assertThat(CoreSettings.notificationGroupId(id)).as(id).isTrue();
            assertThatCode(() -> new NotificationGroup(id, "Name", "jasper", DisplayMode.CARD)).as(id).doesNotThrowAnyException();
        }
        for (String id : List.of("", "Jasper.commands", "9lives", "has space", "a".repeat(161))) {
            assertThat(CoreSettings.notificationGroupId(id)).as(id).isFalse();
            assertThatThrownBy(() -> new NotificationGroup(id, "Name", "jasper", DisplayMode.CARD)).as(id)
                .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test void unusableEntriesAreWarningsAndTheRestApplies() {
        var result = ConfigLoader.parse(Path.of("config.toml"), """
            [notifications.groups]
            "jasper.commands" = "history"
            "jasper.config" = "Sticky"
            "Not An Id" = "off"
            "jasper.general" = 3
            """, true);
        assertThat(result.rejected()).isFalse();
        assertThat(result.values().get(CoreSettings.NOTIFICATIONS_GROUPS)).isEqualTo(Map.of("jasper.commands", "history"));
        assertThat(result.diagnostics()).hasSize(3)
            .allSatisfy(diagnostic -> assertThat(diagnostic.severity()).isEqualTo(ConfigDiagnostic.Severity.WARNING));
    }
}
```

Create `jasper-app/src/test/java/dev/jasper/app/application/JasperApplicationGroupModesTest.java`:

```java
package dev.jasper.app.application;

import dev.jasper.app.commands.CommandHistory;
import dev.jasper.app.config.ConfigService;
import dev.jasper.app.config.CoreSettings;
import dev.jasper.app.config.SettingsEdit;
import dev.jasper.app.notifications.DisplayMode;
import dev.jasper.app.notifications.NotificationGroup;
import dev.jasper.app.settingsui.NotificationGroupChoices;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static dev.jasper.app.workspace.DesktopTestSupport.edt;
import static dev.jasper.app.workspace.DesktopTestSupport.launcher;
import static org.assertj.core.api.Assertions.assertThat;

class JasperApplicationGroupModesTest {
    @TempDir Path home;

    @Test void configuredTokensBecomeModesAndAnythingElseIsLeftOut() {
        assertThat(JasperApplication.groupModes(Map.of("jasper.commands", "history", "jasper.config", "off", "jasper.errors", "Loud")))
            .isEqualTo(Map.of("jasper.commands", DisplayMode.HISTORY, "jasper.config", DisplayMode.OFF));
    }

    @Test void settingsListsJaspersGroupsAsJasperAndAPluginsByItsName() {
        var groups = List.of(new NotificationGroup("jasper.commands", "Long-running commands", "jasper", DisplayMode.CARD),
            new NotificationGroup("dev.jasper.remote.activities", "Remote activities", "dev.jasper.remote", DisplayMode.CARD),
            new NotificationGroup("dev.example.tool", "Tool", "dev.example.tool", DisplayMode.STICKY));
        assertThat(JasperApplication.groupChoices(groups, id -> id.equals("dev.jasper.remote") ? "Remote" : id)).containsExactly(
            new NotificationGroupChoices.Group("jasper.commands", "Long-running commands", "Jasper", "card"),
            new NotificationGroupChoices.Group("dev.jasper.remote.activities", "Remote activities", "Remote", "card"),
            new NotificationGroupChoices.Group("dev.example.tool", "Tool", "dev.example.tool", "sticky"));
    }

    /** notifications.groups applies when Jasper starts and again when the file changes, without a restart. */
    @Test void aConfiguredModeAppliesAtStartAndLive() throws Exception {
        Path file = home.resolve("config.toml");
        Files.writeString(file, "[notifications.groups]\n\"jasper.general\" = \"off\"\n");
        var service = new ConfigService(file, false);
        var terminated = new CountDownLatch(1);
        JasperApplication[] application = new JasperApplication[1];
        try {
            edt(() -> {
                application[0] = new JasperApplication(service, launcher(new ArrayDeque<>()), new CommandHistory(), null, terminated::countDown);
                assertThat(application[0].notificationCenter().mode(NotificationsFeature.GENERAL.id())).isEqualTo(DisplayMode.OFF);
            });
            service.save(new SettingsEdit(Map.of(CoreSettings.NOTIFICATIONS_GROUPS, Optional.of(Map.of("jasper.general", "history"))),
                Map.of(CoreSettings.NOTIFICATIONS_GROUPS, Optional.of(Map.of("jasper.general", "off"))))).get(5, TimeUnit.SECONDS);
            // The save queued its new state on the EDT before it finished, so this runs after the application heard it.
            edt(() -> assertThat(application[0].notificationCenter().mode(NotificationsFeature.GENERAL.id()))
                .isEqualTo(DisplayMode.HISTORY));
        } finally {
            if (application[0] != null) {
                edt(application[0]::quit);
                assertThat(terminated.await(5, TimeUnit.SECONDS)).isTrue();
            }
            service.close();
        }
    }
}
```

In `jasper-app/src/test/java/dev/jasper/app/config/SettingsSaveTest.java`, add before the class's closing brace:

```java
    @Test void notificationGroupsKeepOtherGroupsAndAnEmptiedTableLeavesNoHeader() throws Exception {
        Path file = directory.resolve("config.toml");
        Files.writeString(file, "[notifications.groups]\n\"dev.jasper.remote.activities\" = \"off\"\n");
        try (var service = new ConfigService(file, true)) {
            var both = Map.of("dev.jasper.remote.activities", "off", "jasper.commands", "history");
            var state = save(service, edit(Map.of(CoreSettings.NOTIFICATIONS_GROUPS, Optional.of(both)),
                Map.of(CoreSettings.NOTIFICATIONS_GROUPS, Optional.of(Map.of("dev.jasper.remote.activities", "off")))));
            assertThat(state.values().get(CoreSettings.NOTIFICATIONS_GROUPS)).isEqualTo(both);
            assertThat(Files.readString(file)).containsOnlyOnce("[notifications.groups]");
            state = save(service, edit(Map.of(CoreSettings.NOTIFICATIONS_GROUPS, Optional.empty()),
                Map.of(CoreSettings.NOTIFICATIONS_GROUPS, Optional.of(both))));
            assertThat(state.values().saved(CoreSettings.NOTIFICATIONS_GROUPS)).isEmpty();
            assertThat(Files.readString(file)).doesNotContain("[notifications.groups]");
        }
    }

    @Test void aFirstGroupModeIsSavedIntoTheTemplate() throws Exception {
        Path file = directory.resolve("new/config.toml");
        try (var service = new ConfigService(file, true)) {
            var state = save(service, edit(Map.of(CoreSettings.NOTIFICATIONS_GROUPS, Optional.of(Map.of("jasper.commands", "off"))),
                Map.of(CoreSettings.NOTIFICATIONS_GROUPS, Optional.empty())));
            assertThat(state.values().get(CoreSettings.NOTIFICATIONS_GROUPS)).isEqualTo(Map.of("jasper.commands", "off"));
            assertThat(Files.readString(file)).containsOnlyOnce("[notifications.groups]");
            assertThat(state.diagnostics()).isEmpty();
        }
    }
```

In `jasper-app/src/test/java/dev/jasper/app/config/CoreSettingsTest.java`, replace `hasSize(40)` with `hasSize(41)`.

In `jasper-app/src/test/java/dev/jasper/app/settingsui/CorePagesTest.java`, add `import java.util.Map;` after
`import java.util.List;`, and add this test:

```java
    @Test void theNotificationsPageListsEveryGroupInItsOwnSection() {
        var groups = new NotificationGroupChoices(() -> List.of(
            new NotificationGroupChoices.Group("jasper.commands", "Long-running commands", "Jasper", "card")));
        var spec = CorePages.specs(JASPER_DARK, system(), ShellChoices.NONE, id -> { }, false, groups).stream()
            .filter(page -> page.settings().contains(CoreSettings.NOTIFICATIONS_GROUPS)).findFirst().orElseThrow();
        var draft = new SettingsDraft(ConfigLoader.parse(Path.of("config.toml"), "", true).values());
        var page = spec.build(new PageContext(draft, id -> { }, Runnable::run, (title, initial) -> Optional.empty(), () -> { }));
        assertThat(SchemaPageTest.sectionTitles(page.component())).containsExactly("Groups");
        var row = page.rows().stream().filter(candidate -> candidate.setting() == CoreSettings.NOTIFICATIONS_GROUPS)
            .findFirst().orElseThrow();
        var editor = (NotificationGroupsEditor) row.editor();
        editor.combo("jasper.commands").setSelectedItem("history");
        assertThat(draft.value(CoreSettings.NOTIFICATIONS_GROUPS)).isEqualTo(Map.of("jasper.commands", "history"));
        editor.combo("jasper.commands").setSelectedItem("card");
        assertThat(draft.modified(CoreSettings.NOTIFICATIONS_GROUPS)).as("back at the default, nothing to save").isFalse();
        page.dispose();
    }
```

(`CorePagesTest` already has a `system()` helper and the `JASPER_DARK` constant it passes to `specs`. `SchemaPageTest`
has the static `sectionTitles(...)` helper.)

In `jasper-app/src/test/java/dev/jasper/app/settingsui/NotificationGroupsEditorTest.java`, in
`theFactoryBuildsAFullWidthEditorThatRemovesTheTableAtItsDefault`, replace `CoreSettings.TERMINAL_ENV` with
`CoreSettings.NOTIFICATIONS_GROUPS`.

- [ ] **Step 2: Run them to see them fail**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.config.*' --tests 'dev.jasper.app.settingsui.*' --tests 'dev.jasper.app.application.JasperApplicationGroupModesTest'`
Expected: FAIL to compile. `CoreSettings.NOTIFICATIONS_GROUPS`, `NOTIFICATION_MODES`, `notificationGroupId`, the
six-argument `CorePages.specs`, `JasperApplication.groupModes` and `groupChoices` do not exist.

- [ ] **Step 3: Implement**

In `jasper-app/src/main/java/dev/jasper/app/config/CoreSettings.java`:

1. Directly after the declaration of `NOTIFICATIONS_SYSTEM` (it ends with
   `+ "balloons, or notify-send on Linux.").build();`), add:

```java
    /** The modes notifications.groups accepts. */
    static final Set<String> NOTIFICATION_MODES = Set.of("card", "sticky", "history", "off");
    private static final java.util.regex.Pattern NOTIFICATION_GROUP = java.util.regex.Pattern.compile("[a-z][a-z0-9_.-]{0,159}");

    /** Whether {@code id} is shaped like a notification group id: lowercase, a plugin's id or one under it. */
    static boolean notificationGroupId(String id) {
        return NOTIFICATION_GROUP.matcher(id).matches();
    }

    public static final Setting<Map<String, String>> NOTIFICATIONS_GROUPS = Setting.of("notifications.groups",
            SettingKind.stringMap(new SettingKind.MapRules(CoreSettings::notificationGroupId,
                "Use a notification group id such as \"jasper.commands\"; entry ignored.", Set.of(), "",
                NOTIFICATION_MODES::contains, "Use \"card\", \"sticky\", \"history\" or \"off\"; the group's default applies.",
                "a display mode", dev.jasper.app.settings.Reading.Severity.WARNING)), Map.<String, String>of())
        .label("Notification groups").keywords("notification", "group", "card", "sticky", "history", "off", "mute", "quiet")
        .description("Live. How each group's notifications appear: \"card\" (a card that hides itself), \"sticky\" (a card\n"
            + "that stays until you close it), \"history\" (only the Notifications panel) or \"off\". Settings > Notifications\n"
            + "lists every group; a group not listed here keeps its default.").build();
```

2. In `SCHEMA`, replace `NOTIFICATIONS_LONG_COMMAND_SECONDS, NOTIFICATIONS_SYSTEM, SYSTEM_RUN_IN_BACKGROUND,` with
   `NOTIFICATIONS_LONG_COMMAND_SECONDS, NOTIFICATIONS_SYSTEM, NOTIFICATIONS_GROUPS, SYSTEM_RUN_IN_BACKGROUND,`.

In `jasper-app/src/main/java/dev/jasper/app/settingsui/CorePages.java`:

1. Replace

```java
    static List<SchemaPage.Spec> specs(ThemeChoices themes, SystemActions system, ShellChoices shells, java.util.function.Consumer<String> navigate, boolean glass) {
        return List.of(
```

   with

```java
    static List<SchemaPage.Spec> specs(ThemeChoices themes, SystemActions system, ShellChoices shells, java.util.function.Consumer<String> navigate, boolean glass) {
        return specs(themes, system, shells, navigate, glass, NotificationGroupChoices.NONE);
    }

    /** As above, with the notification groups the Notifications page lists. */
    static List<SchemaPage.Spec> specs(ThemeChoices themes, SystemActions system, ShellChoices shells,
                                       java.util.function.Consumer<String> navigate, boolean glass, NotificationGroupChoices groups) {
        return List.of(
```

2. Replace

```java
                .row(NOTIFICATIONS_LONG_COMMAND_SECONDS, Editors.integer("s")).note("0 turns these notifications off."),
```

   with

```java
                .row(NOTIFICATIONS_LONG_COMMAND_SECONDS, Editors.integer("s")).note("0 turns these notifications off.")
                .section("Groups")
                .row(NOTIFICATIONS_GROUPS, Editors.notificationGroups(groups))
                .note("How each kind of notification appears. A plugin's groups are listed once it has run."),
```

3. Replace

```java
    /** The whole tree for one dialog, in display order, with the pages the application supplies. */
    public static Function<PageContext, List<SettingsPage>> pages(ThemeChoices themes,
                                                                   SystemActions system, ShellChoices shells, Pages supplied) {
        return context -> {
            var specs = specs(themes, system, shells, context.navigate());
```

   with

```java
    /** The whole tree for one dialog, in display order, with the pages the application supplies and no notification groups. */
    public static Function<PageContext, List<SettingsPage>> pages(ThemeChoices themes,
                                                                   SystemActions system, ShellChoices shells, Pages supplied) {
        return pages(themes, system, shells, NotificationGroupChoices.NONE, supplied);
    }

    /** The whole tree for one dialog, in display order, with the notification groups and pages the application supplies. */
    public static Function<PageContext, List<SettingsPage>> pages(ThemeChoices themes, SystemActions system, ShellChoices shells,
                                                                   NotificationGroupChoices groups, Pages supplied) {
        return context -> {
            var specs = specs(themes, system, shells, context.navigate(), com.formdev.flatlaf.util.SystemInfo.isMacOS, groups);
```

In `jasper-app/src/main/java/dev/jasper/app/application/JasperApplication.java`:

1. Replace

```java
            updateMigrateAction();
        });
```

   with

```java
            updateMigrateAction();
        });
        if (configuration != null) {
            applyGroupModes(configuration.state());
            configuration.onState(this::applyGroupModes);
        }
```

2. Directly before `    private String pluginName(String id) {`, add:

```java
    /** {@code notifications.groups}, live: each listed group's mode replaces its default from the next post on. */
    private void applyGroupModes(dev.jasper.app.config.ConfigService.State state) {
        notificationCenter.useModes(groupModes(state.values().get(dev.jasper.app.config.CoreSettings.NOTIFICATIONS_GROUPS)));
    }

    /** The configured modes the center understands; the reader has already left out anything else. */
    static Map<String, dev.jasper.app.notifications.DisplayMode> groupModes(Map<String, String> configured) {
        var modes = new LinkedHashMap<String, dev.jasper.app.notifications.DisplayMode>();
        configured.forEach((id, token) -> dev.jasper.app.notifications.DisplayMode.parse(token).ifPresent(mode -> modes.put(id, mode)));
        return modes;
    }

    /** What Settings › Notifications lists: every group declared so far, with who declared it as people know them. */
    static List<dev.jasper.app.settingsui.NotificationGroupChoices.Group> groupChoices(
            List<dev.jasper.app.notifications.NotificationGroup> groups, java.util.function.Function<String, String> pluginName) {
        return groups.stream().map(group -> new dev.jasper.app.settingsui.NotificationGroupChoices.Group(group.id(), group.name(),
            group.owner().equals(dev.jasper.app.notifications.NotificationRequest.JASPER) ? "Jasper" : pluginName.apply(group.owner()),
            group.defaultMode().token())).toList();
    }

```

3. Replace

```java
                dev.jasper.app.settingsui.CorePages.pages(themeChoices, system, shells, new dev.jasper.app.settingsui.CorePages.Pages(
```

   with

```java
                dev.jasper.app.settingsui.CorePages.pages(themeChoices, system, shells,
                    new dev.jasper.app.settingsui.NotificationGroupChoices(() -> groupChoices(notificationCenter.groups(), this::pluginName)),
                    new dev.jasper.app.settingsui.CorePages.Pages(
```

(`JasperApplication` imports `java.util.*`, so `Map`, `LinkedHashMap` and `List` need no new imports.)

In `config.example.toml`, replace

```toml
system = true

[system]
```

with

```toml
system = true

[notifications.groups]
# Live. How each group's notifications appear: "card" (a card that hides itself), "sticky" (a card
# that stays until you close it), "history" (only the Notifications panel) or "off". Settings > Notifications
# lists every group; a group not listed here keeps its default.
# "jasper.commands" = "history"

[system]
```

In `docs/configuration.md`, replace

```
long_command_seconds = 10
system = true
```

with

```
long_command_seconds = 10
system = true

[notifications.groups]
# "jasper.commands" = "history"
```

and replace

```
| `notifications.system` | `true` | Boolean | Live |
```

with

```
| `notifications.system` | `true` | Boolean | Live |
| `notifications.groups` | `{}` | Table of notification group id to `"card"`, `"sticky"`, `"history"` or `"off"` | Live |
```

- [ ] **Step 4: Run them to see them pass**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.config.*' --tests 'dev.jasper.app.settingsui.*' --tests 'dev.jasper.app.settings.*' --tests 'dev.jasper.app.application.*' --tests 'dev.jasper.app.notifications.*' --tests 'dev.jasper.app.documentation.*' verifyApplicationArchitecture`
Expected: PASS. That includes `NotificationGroupsSettingTest` 3/3, `JasperApplicationGroupModesTest` 3/3, the two new
`SettingsSaveTest` cases, `CorePagesTest` (every setting on exactly one page, the tree unchanged, the new Groups
test), and `ConfigTemplateTest` (the template prints `[notifications.groups]`; the example parses with no
diagnostics).

- [ ] **Step 5: Commit**

```bash
git add jasper-app/src/main/java/dev/jasper/app/config/CoreSettings.java jasper-app/src/main/java/dev/jasper/app/settingsui/CorePages.java jasper-app/src/main/java/dev/jasper/app/application/JasperApplication.java config.example.toml docs/configuration.md jasper-app/src/test/java/dev/jasper/app/config/NotificationGroupsSettingTest.java jasper-app/src/test/java/dev/jasper/app/application/JasperApplicationGroupModesTest.java jasper-app/src/test/java/dev/jasper/app/config/CoreSettingsTest.java jasper-app/src/test/java/dev/jasper/app/config/SettingsSaveTest.java jasper-app/src/test/java/dev/jasper/app/settingsui/CorePagesTest.java jasper-app/src/test/java/dev/jasper/app/settingsui/NotificationGroupsEditorTest.java
git commit -m "feat(notifications): choose how each group appears, in config.toml and Settings" -m "notifications.groups maps a group id to card, sticky, history or off; Settings › Notifications lists every known group with its mode, and the center follows the configuration live." -m "🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>"
```

### Task 5: Documentation and full verification

**Files:**
- Modify: `docs/notifications.md`, `docs/configuration.md`, `docs/plugin-authoring.md`
- Modify: `docs/STATUS.md`, `docs/superpowers/specs/2026-10-02-jasper-notifications-design.md`, this plan's banner

**Interfaces:**
- Consumes: everything above.
- Produces: documentation only. `AppDocumentationTest` checks local links and anchors outside fenced code.

- [ ] **Step 1: The guides**

In `docs/notifications.md`, replace

```
and **Open config.toml**. It goes away by itself once every problem is fixed.
```

with

```
and **Open config.toml**. It goes away by itself once every problem is fixed.

To change how a group appears, use **Settings › Notifications › Groups**. It lists every group known now: Jasper's,
and each plugin's once the plugin has run. Each group gets a choice of **Card**, **Sticky card**, **History only**
(the Notifications panel, unread, no card) or **Off**, with the default marked, and **Reset**. The change applies to
the next notification; what is already in the panel or on screen stays. The same choices live in `config.toml` under
[`[notifications.groups]`](configuration.md#notification-groups).
```

In `docs/configuration.md`, directly before `### Automatic tab and notification titles`, add:

````markdown
### Notification groups

`[notifications.groups]` sets how each group's notifications appear, by group id:

```toml
[notifications.groups]
"jasper.commands" = "history"   # Long-running commands: the Notifications panel only
"dev.jasper.remote" = "sticky"  # Remote's notifications stay until you close them
```

The modes are `"card"` (a card that hides itself), `"sticky"` (a card that stays until you close it), `"history"`
(only the Notifications panel, unread) and `"off"`. A group you do not list keeps its default. Settings ›
Notifications › Groups lists every group with its id, and changes there are saved here. An entry for a plugin that
is not running is kept. An unknown mode, a value that is not a string, or a key that is not a group id is a warning,
and that group keeps its default. See [Notifications](notifications.md#groups) for what each group covers.

````

In `docs/plugin-authoring.md`, replace

```
- Group ids follow the panel id pattern and start with your plugin id and a dot; `<plugin-id>.activities` is Jasper's.
```

with

```
- Group ids follow the panel id pattern and start with your plugin id and a dot; `<plugin-id>.activities` is Jasper's.
- Your group's mode is a default. The user can choose another for any group in Settings › Notifications or in
  `[notifications.groups]`, and theirs wins.
```

- [ ] **Step 2: Run the full check**

Run: `./gradlew check --continue`
Expected: BUILD SUCCESSFUL: every test, the four architecture gates, doclint and, on macOS, the native harnesses.

Known flakes not caused by this branch, each documented in `docs/STATUS.md`:
- `TerminalAppIntegrationTest`'s "reflow" row-reset case;
- Remote's `ListenerTest.acceptsOnLocalhostUntilClosed`;
- Editor's `ExFilesTest.bufferSelectsATabByNumberOrName` when `java.io.tmpdir` contains "zz".

If one fails, rerun that test alone three times and the full check once more, and record it.

Count the results right after the full check (the documentation rerun below replaces the app module's XML):

```bash
python3 - <<'PY'
import pathlib, re
tests = failures = errors = skipped = 0
for report in pathlib.Path(".").glob("**/build/test-results/test/*.xml"):
    head = report.read_text(encoding="utf-8")[:2000]
    match = re.search(r'tests="(\d+)" skipped="(\d+)" failures="(\d+)" errors="(\d+)"', head)
    if match:
        t, s, f, e = map(int, match.groups())
        tests += t; skipped += s; failures += f; errors += e
print(f"{tests} tests, {failures} failures, {errors} errors, {skipped} skipped")
PY
```

Then run the source hygiene check from `AGENTS.md` (expected: no output) and `git diff --check 0734c5c5` (expected:
no output).

- [ ] **Step 3: Record the outcome**

In `docs/STATUS.md`, add a new first entry under the `## Current state` heading (keep the heading's date, or set it
to today's), with the counts from Step 2:

```markdown
- **Notifications plan 6: Settings, reviewed on branch `claude/notifications-6`; awaiting the user's GUI checks and
  merge decision** (plan `docs/superpowers/plans/2026-10-04-notifications-6-settings.md`,
  [guide](configuration.md#notification-groups)): `[notifications.groups]` maps a group id to `"card"`, `"sticky"`,
  `"history"` or `"off"`, and Settings › Notifications gains a Groups section. It lists every group declared so far
  (Jasper's, and each plugin's once it has run): its name, who declared it, a mode combo marking the default, and
  Reset. Changes apply to the next notification, and entries for plugins that are not running are kept. An unusable
  entry is a warning, and the group keeps its default: `SettingKind.MapRules` gains a severity, and a WARNING map
  ignores even an entry of the wrong type instead of rejecting the file. Removing a table's last entry removes the
  table, and an empty table counts as absent when saving, which also fixes Reset on `terminal.env`. Spec
  interpretations: see the plan's list (1 to 10). `./gradlew check --continue`: COUNTS. No GUI launched. GUI checks for
  the user: Settings › Notifications shows the Groups table in light and dark themes; choosing History only for
  Long-running commands and applying it makes the next long command go to the panel without a card; Reset returns a
  group to its default; editing `[notifications.groups]` by hand with an unknown mode shows a configuration warning;
  a plugin's groups appear once the plugin has started.
```

Replace `COUNTS` with the four numbers the count printed, as `**N tests, N failures, N errors, N skipped**`, and name
any failure with its cause.

In `docs/superpowers/specs/2026-10-02-jasper-notifications-design.md`, replace

```
notifiers)](../plans/2026-10-03-notifications-5-os-notifiers.md) is merged to main.
```

with

```
notifiers)](../plans/2026-10-03-notifications-5-os-notifiers.md) is merged to main. [Plan 6
(Settings)](../plans/2026-10-04-notifications-6-settings.md) is implemented on branch `claude/notifications-6`.
```

In this plan, replace the `**Status:** Not started. …` paragraph with one that:
- says the plan is implemented on branch `claude/notifications-6`;
- lists every deviation from the plan text made during execution, each with its task and reason;
- gives the final review's fixes;
- gives the Step 2 counts.

Never write a triple backtick inside a paragraph: `AppDocumentationTest` pairs fences by counting them.

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.documentation.*' --rerun`
Expected: PASS: every link, anchor and compiled example matches (`--rerun`, because documentation files are not task
inputs).

- [ ] **Step 4: Commit**

```bash
git add docs
git commit -m "docs(notifications): choosing how each notification group appears" -m "The notifications and configuration guides and the plugin guide describe notifications.groups and Settings › Notifications › Groups; STATUS, the spec and the plan record plan 6." -m "🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>"
```
