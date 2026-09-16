# History ranking, freshness and tmux integration — implementation plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the History scope rank every shell fairly, show a command within about a second of running it, work for a tmux user, and let trivial commands be pushed down by a setting.

**Architecture:** Four independent slices. Ranking changes `ShellHistorySnapshot.build` and the shape `ShellHistoryIndex` hands it. Freshness adds a poll timer to `ShellHistoryIndex`. tmux changes `LaunchSettings` and the three scripts' guards. De-ranking adds a config setting carried on `PaletteContext` and applied in `ShellHistoryScope`.

**Tech Stack:** Java 25 on the JetBrains Runtime, Swing, JUnit 6 + AssertJ, zsh/bash/fish.

**Spec:** `docs/superpowers/specs/2026-09-16-jasper-history-ranking-design.md`

**Status: complete.** All five tasks executed on `claude/history-scope-ranking`.
Deviations beyond the one already recorded above: Task 3 grew a `.zshenv` change
that was not planned — driving a real tmux showed `default-command ${SHELL}`
runs the interactive shell as the child of a non-interactive one, which the
previous feature's ZDOTDIR fix stranded. Three pre-existing tests were updated
where they pinned orders that only held under the old ranking.

## Global Constraints

- Java 25 on the **JetBrains Runtime**. Use `./gradlew`, never a system `gradle`.
- `jasper-terminal` never depends on `jasper-app`. No public method in `jasper-terminal` takes or returns a JediTerm type.
- Never put raw control, private-use or unpaired surrogate characters in source. Use Java escapes.
- Threading: `ShellHistoryIndex` has one serial worker for file reads and publishes snapshots on the EDT. `snapshot()`, `onChanged()` and `refresh()` assert the EDT via `CommandRegistry.requireEdt()`. Worker-only fields must never be touched from the EDT.
- `jasper.bash` stays within 115 lines; `jasper.zsh` and `jasper.fish` within 90.
- Do not launch the GUI. `./gradlew check` only.
- Each task ends with a commit whose message ends with the repository's attribution trailers.

## Spec deviation recorded up front

The spec says "`ShellHistoryScope` reads the flag from the config snapshot it already receives". It receives no config snapshot. The setting instead rides on `PaletteContext`, which is exactly how `palette.max_results` already reaches a scope — see `WindowCommandPalette:87`. Ranking stays in the scope rather than in `ShellHistorySnapshot.build`, so the snapshot remains pure merged data and presentation order stays a presentation concern.

---

### Task 1: rank untimestamped entries by file position and mtime

**Files:**
- Modify: `jasper-app/src/main/java/dev/jasper/app/ShellHistorySnapshot.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/ShellHistoryIndex.java` (the two `build` call sites and `perSource`)
- Test: `jasper-app/src/test/java/dev/jasper/app/ShellHistorySnapshotTest.java`

**Interfaces:**
- Produces: `ShellHistorySnapshot.Source(List<ShellHistoryEntry> entries, long modified)` — `modified` is epoch seconds, `0` when unknown.
- Produces: `ShellHistorySnapshot.build(Collection<Source> perSource, List<ShellHistoryEntry> live, int cap)` — replaces the `Collection<List<ShellHistoryEntry>>` overload.

- [x] **Step 1: Write the failing tests**

```java
    private static ShellHistoryEntry entry(String command, long timestamp, String shell) {
        return ShellHistoryEntry.of(command, timestamp, shell);
    }

    @Test void anUntimestampedEntryRanksNearWhenItsFileWasWritten() {
        long now = 1_700_000_000L;
        var bash = new ShellHistorySnapshot.Source(List.of(entry("bash-recent", 0, "bash")), now);
        var zsh = new ShellHistorySnapshot.Source(List.of(entry("zsh-last-week", now - 604_800, "zsh")), now);

        var snapshot = ShellHistorySnapshot.build(List.of(bash, zsh), List.of(), 50);

        assertThat(snapshot.entries()).extracting(ShellHistoryEntry::command)
            .containsExactly("bash-recent", "zsh-last-week");
    }

    @Test void aStaleUntimestampedFileSinksBelowRecentTimestampedEntries() {
        long now = 1_700_000_000L;
        var bash = new ShellHistorySnapshot.Source(List.of(entry("bash-old", 0, "bash")), now - 604_800);
        var zsh = new ShellHistorySnapshot.Source(List.of(entry("zsh-today", now, "zsh")), now);

        var snapshot = ShellHistorySnapshot.build(List.of(bash, zsh), List.of(), 50);

        assertThat(snapshot.entries()).extracting(ShellHistoryEntry::command)
            .containsExactly("zsh-today", "bash-old");
    }

    @Test void untimestampedEntriesKeepTheirFileOrderNewestLast() {
        long now = 1_700_000_000L;
        var bash = new ShellHistorySnapshot.Source(
            List.of(entry("first", 0, "bash"), entry("second", 0, "bash"), entry("third", 0, "bash")), now);

        var snapshot = ShellHistorySnapshot.build(List.of(bash), List.of(), 50);

        assertThat(snapshot.entries()).extracting(ShellHistoryEntry::command)
            .containsExactly("third", "second", "first");
    }

    @Test void aSourceWithNoModificationTimeKeepsTodaysBehaviour() {
        var unknown = new ShellHistorySnapshot.Source(List.of(entry("no-mtime", 0, "bash")), 0);
        var zsh = new ShellHistorySnapshot.Source(List.of(entry("zsh", 1_700_000_000L, "zsh")), 1_700_000_000L);

        var snapshot = ShellHistorySnapshot.build(List.of(unknown, zsh), List.of(), 50);

        assertThat(snapshot.entries()).extracting(ShellHistoryEntry::command).containsExactly("zsh", "no-mtime");
    }

    @Test void liveEntriesStillWinTiesAgainstFileEntries() {
        long now = 1_700_000_000L;
        var file = new ShellHistorySnapshot.Source(List.of(entry("shared", now, "zsh")), now);
        var live = ShellHistoryEntry.of("shared", now, "live");

        var snapshot = ShellHistorySnapshot.build(List.of(file), List.of(live), 50);

        assertThat(snapshot.entries()).hasSize(1);
        assertThat(snapshot.entries().getFirst().shells()).contains("live", "zsh");
    }
```

- [x] **Step 2: Run to verify they fail**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.ShellHistorySnapshotTest' --rerun-tasks`
Expected: compile failure — `ShellHistorySnapshot.Source` does not exist. That counts as red for the first run; once it compiles, the two ordering tests must fail on the assertion, not on a missing type.

- [x] **Step 3: Add the Source record and rank the entries**

In `ShellHistorySnapshot`:

```java
    /** One history file's entries, oldest first, with the file's last-modified time in epoch seconds (0 when unknown). */
    record Source(List<ShellHistoryEntry> entries, long modified) {
        Source {
            entries = List.copyOf(entries);
        }
    }
```

Replace the body of `build`:

```java
    /**
     * Each source's list is oldest first. An entry with a known timestamp ranks by it; one without ranks
     * just before its file was last written, stepping back a second per entry from the end, so a shell
     * that records no timestamps still interleaves by recency instead of sinking to the epoch. Live
     * entries beat file entries at the same rank. The most recent occurrence of a command wins and the
     * shells that ran it are merged into it.
     */
    static ShellHistorySnapshot build(Collection<Source> perSource, List<ShellHistoryEntry> live, int cap) {
        var keyed = new ArrayList<Keyed>();
        long sequence = 0;
        for (Source source : perSource) {
            List<ShellHistoryEntry> entries = source.entries();
            for (int i = 0; i < entries.size(); i++) {
                ShellHistoryEntry entry = entries.get(i);
                long rank = entry.timestamp() > 0 ? entry.timestamp()
                    : Math.max(0, source.modified() - (entries.size() - i));
                keyed.add(new Keyed(entry, rank, sequence++));
            }
        }
        for (int i = 0; i < live.size(); i++)
            keyed.add(new Keyed(live.get(i), live.get(i).timestamp(), Long.MAX_VALUE - live.size() + i));
        keyed.sort(Comparator.comparingLong(Keyed::rank).thenComparingLong(Keyed::sequence).reversed());
        Map<String, ShellHistoryEntry> byCommand = new LinkedHashMap<>();
        for (Keyed item : keyed) byCommand.merge(item.entry().command(), item.entry(), ShellHistorySnapshot::merge);
        List<ShellHistoryEntry> ordered = byCommand.values().stream().limit(Math.max(0, cap)).toList();
        var shells = new HashSet<String>();
        for (ShellHistoryEntry entry : ordered) shells.addAll(entry.shells());
        return new ShellHistorySnapshot(ordered, shells);
    }
```

Rename the `Keyed` component `timestamp` to `rank` so the name states what it now is:

```java
    private record Keyed(ShellHistoryEntry entry, long rank, long sequence) {}
```

- [x] **Step 4: Feed the modification time through the index**

In `ShellHistoryIndex`, change the worker map to carry the time:

```java
    private final Map<ShellHistorySource, ShellHistorySnapshot.Source> perSource = new LinkedHashMap<>();
```

`FileState` already holds `modified`. At the end of `readSource`, replace `perSource.put(source, state.entries);` with:

```java
        perSource.put(source, new ShellHistorySnapshot.Source(state.entries,
            state.modified == null ? 0 : state.modified.to(java.util.concurrent.TimeUnit.SECONDS)));
```

`publish` needs no change beyond the type: `ShellHistorySnapshot.build(perSource.values(), live, ShellHistorySnapshot.MAX_ENTRIES)`.

- [x] **Step 5: Verify**

Run: `./gradlew :jasper-app:test --rerun-tasks`
Expected: PASS, including every existing `ShellHistoryIndexTest` case. Existing tests that call the old `build` overload must be updated to pass `Source` values; do that in this task rather than keeping a deprecated overload.

- [x] **Step 6: Commit**

```bash
git add jasper-app/src/main/java/dev/jasper/app/ShellHistorySnapshot.java \
        jasper-app/src/main/java/dev/jasper/app/ShellHistoryIndex.java \
        jasper-app/src/test/java/dev/jasper/app/
git commit -m "fix: rank history entries that carry no timestamp by file position and mtime"
```

---

### Task 2: refresh while you work

**Files:**
- Modify: `jasper-app/src/main/java/dev/jasper/app/ShellHistoryIndex.java`
- Test: `jasper-app/src/test/java/dev/jasper/app/ShellHistoryIndexTest.java`

**Interfaces:**
- Produces: `ShellHistoryIndex.pollTimer()` — package-private test seam returning the `javax.swing.Timer`, or null when none was created.

- [x] **Step 1: Write the failing tests**

```java
    @Test void theIndexPollsOnlyWhileSomethingIsListening() throws Exception {
        var index = new ShellHistoryIndex(List.of(source), worker, Runnable::run);
        assertThat(index.pollTimer()).isNull();

        var first = index.onChanged(() -> {});
        assertThat(index.pollTimer().isRunning()).isTrue();
        var second = index.onChanged(() -> {});
        first.close();
        assertThat(index.pollTimer().isRunning()).as("still one listener").isTrue();

        second.close();
        assertThat(index.pollTimer().isRunning()).as("no listeners left").isFalse();
    }

    @Test void aCommandAppendedToAFileIsPublishedWithoutTheScopeBeingActivated() throws Exception {
        Files.writeString(file, "old\n");
        var index = new ShellHistoryIndex(List.of(source), worker, Runnable::run);
        var seen = new ArrayList<List<String>>();
        index.onChanged(() -> seen.add(index.snapshot().entries().stream().map(ShellHistoryEntry::command).toList()));
        index.refresh();
        drain(worker);

        Files.writeString(file, "old\nfresh\n");
        tick(index.pollTimer());   // fire the timer directly rather than sleeping
        drain(worker);

        assertThat(seen.getLast()).contains("fresh");
    }
```

`tick(Timer)` fires every `ActionListener` on the timer with a synthetic event, the way the terminal tests drive their timers. `drain(worker)` submits a no-op and waits for it, so the serial worker has finished.

- [x] **Step 2: Run to verify they fail**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.ShellHistoryIndexTest' --rerun-tasks`
Expected: compile failure on `pollTimer()`, then a failure showing `fresh` absent.

- [x] **Step 3: Add the poll**

In `ShellHistoryIndex`, add EDT-only state and start/stop it with the listener count:

```java
    /** One second is short enough to feel immediate and long enough that a quiet tick is one stat per source. */
    private static final int POLL_MILLIS = 1000;
    private javax.swing.Timer poll;
```

```java
    CommandRegistry.Subscription onChanged(Runnable listener) {
        CommandRegistry.requireEdt();
        listeners.add(listener);
        startPolling();
        return new CommandRegistry.Subscription(() -> {
            CommandRegistry.requireEdt();
            listeners.remove(listener);
            if (listeners.isEmpty() && poll != null) poll.stop();
        });
    }

    /**
     * Files change without anyone asking, so the index looks for itself rather than waiting for the
     * palette to open. Only while something is listening: a window with no palette pays nothing.
     */
    private void startPolling() {
        if (closed || poll != null) { if (poll != null) poll.start(); return; }
        poll = new javax.swing.Timer(POLL_MILLIS, event -> refresh());
        poll.setRepeats(true);
        poll.start();
    }

    /** Test seam: the poll timer, or null before the first listener subscribes. */
    javax.swing.Timer pollTimer() { return poll; }
```

and in `close()`, before `worker.shutdownNow()`:

```java
        if (poll != null) poll.stop();
```

- [x] **Step 4: Verify**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.ShellHistoryIndexTest' --rerun-tasks`
Expected: PASS.

- [x] **Step 5: Commit**

```bash
git add jasper-app/src/main/java/dev/jasper/app/ShellHistoryIndex.java \
        jasper-app/src/test/java/dev/jasper/app/ShellHistoryIndexTest.java
git commit -m "feat: poll shell history files so a new command appears without reopening the palette"
```

---

### Task 3: integration that survives tmux

**Files:**
- Modify: `jasper-app/src/main/java/dev/jasper/app/LaunchSettings.java`
- Modify: `jasper-app/src/main/resources/dev/jasper/app/shell-integration/jasper.zsh`
- Modify: `jasper-app/src/main/resources/dev/jasper/app/shell-integration/jasper.bash`
- Modify: `jasper-app/src/main/resources/dev/jasper/app/shell-integration/jasper.fish`
- Test: `jasper-app/src/test/java/dev/jasper/app/LaunchSettingsTest.java`
- Test: `jasper-app/src/test/java/dev/jasper/app/ShellIntegrationScriptTest.java`

Measured on the dev machine: a pane in a tmux server Jasper started inherits `ZDOTDIR` and `JASPER_SHELL_INTEGRATION`, but tmux overwrites `TERM_PROGRAM` with `tmux`. The plumbing already reaches the pane; only the guard rejects it.

- [x] **Step 1: Write the failing tests**

In `LaunchSettingsTest`:

```java
    @Test void aMarkerTmuxCannotOverwriteIsExportedInEveryMode() {
        Path dir = Path.of("/opt/jasper/shell-integration");
        for (ShellIntegrationMode mode : ShellIntegrationMode.values()) {
            var settings = LaunchSettings.resolve(withShell("/bin/zsh", List.of(), mode), "Mac OS X",
                Map.of("JASPER_TERMINAL", "stale"), 150, 45, dir);
            assertThat(settings.environment()).as("%s", mode).containsEntry("JASPER_TERMINAL", "1");
        }
    }

    @Test void tmuxGetsTheInjectionItsOwnShellWillUse() {
        Path dir = Path.of("/opt/jasper/shell-integration");
        var zsh = new java.util.ArrayList<>(List.of("/opt/homebrew/bin/tmux"));
        var zshEnv = new HashMap<>(Map.of("SHELL", "/bin/zsh"));
        LaunchSettings.inject(zsh, zshEnv, dir);
        assertThat(zsh).containsExactly("/opt/homebrew/bin/tmux");
        assertThat(zshEnv).containsEntry("ZDOTDIR", dir.resolve("zsh").toString());

        var fish = new java.util.ArrayList<>(List.of("tmux"));
        var fishEnv = new HashMap<>(Map.of("SHELL", "/usr/local/bin/fish"));
        LaunchSettings.inject(fish, fishEnv, dir);
        assertThat(fishEnv).containsEntry("XDG_DATA_DIRS", dir.resolve("fish") + ":/usr/local/share:/usr/share");

        // bash has no environment variable for an rc file, so tmux cannot carry it: nothing is set.
        var bash = new java.util.ArrayList<>(List.of("tmux"));
        var bashEnv = new HashMap<>(Map.of("SHELL", "/bin/bash"));
        LaunchSettings.inject(bash, bashEnv, dir);
        assertThat(bash).containsExactly("tmux");
        assertThat(bashEnv).containsOnlyKeys("SHELL");
    }
```

In `ShellIntegrationScriptTest`, prove the guard accepts the tmux case:

```java
    @Test void theScriptsLoadUnderTmuxWhichOverwritesTermProgram() throws Exception {
        for (String shell : List.of("/bin/zsh", "/bin/bash")) {
            if (!Files.isExecutable(Path.of(shell))) continue;
            String flavour = shell.endsWith("zsh") ? "zsh" : "bash";
            Path home = Files.createDirectories(dir.resolve("home-tmux-" + flavour));
            Files.writeString(home.resolve("." + flavour + "rc"),
                "source \"" + scripts() + "/jasper." + flavour + "\"\n");
            var env = environment(home);
            env.put("TERM_PROGRAM", "tmux");          // what tmux leaves behind
            env.put("JASPER_TERMINAL", "1");          // Jasper's own marker
            if (flavour.equals("zsh")) env.put("ZDOTDIR", home.toString());
            assertThat(ShellRun.run(List.of(shell, "-i"), env, home, "true\nexit\n"))
                .as(shell).contains(ShellRun.C);
        }
    }

    @Test void theScriptsStayQuietWithNeitherMarker() throws Exception {
        Assumptions.assumeTrue(Files.isExecutable(Path.of("/bin/zsh")));
        Path home = Files.createDirectories(dir.resolve("home-nomarker"));
        Files.writeString(home.resolve(".zshrc"), "source \"" + scripts() + "/jasper.zsh\"\n");
        var env = environment(home);
        env.put("TERM_PROGRAM", "iTerm.app");
        env.remove("JASPER_TERMINAL");
        env.put("ZDOTDIR", home.toString());
        assertThat(ShellRun.run(List.of("/bin/zsh", "-i"), env, home, "true\nexit\n")).doesNotContain(ShellRun.A);
    }
```

- [x] **Step 2: Run to verify they fail**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.LaunchSettingsTest' --tests 'dev.jasper.app.ShellIntegrationScriptTest' --rerun-tasks`
Expected: no `JASPER_TERMINAL`, no `ZDOTDIR` for tmux, and no `C` mark under `TERM_PROGRAM=tmux`.

- [x] **Step 3: Export the marker and add the tmux arm**

In `LaunchSettings.resolve`, beside the existing `TERM_PROGRAM` put — it is already scrubbed by the `JASPER_` prefix rule, and goes before the `[terminal.env]` overlay so a user can still override it:

```java
        environment.put("TERM_PROGRAM", "Jasper");
        // tmux overwrites TERM_PROGRAM with "tmux" in every pane, so the scripts need a marker it
        // leaves alone. Measured: arbitrary variables do reach a pane of a server Jasper started.
        environment.put("JASPER_TERMINAL", "1");
```

In `inject`, add before the `default` arm:

```java
            case "tmux" -> {
                // tmux runs the user's $SHELL per pane and passes its own environment down, so the
                // injection that reaches that shell is the environment kind. bash's is --rcfile, an
                // argument tmux never sees, so bash inside tmux is left to the manual source line.
                String inner = environment.get("SHELL");
                if (inner == null || inner.isBlank()) return;
                String innerShell;
                try {
                    Path name = Path.of(inner).getFileName();
                    innerShell = name == null ? "" : name.toString();
                } catch (InvalidPathException notAPath) {
                    return;
                }
                if (innerShell.equals("zsh") || innerShell.equals("fish")) {
                    inject(new ArrayList<>(List.of(inner)), environment, dir);
                }
            }
```

Calling `inject` recursively with a throwaway command list reuses the zsh and fish arms exactly, so the two mechanisms cannot drift apart. The real command list is never touched.

- [x] **Step 4: Widen the guard in all three scripts**

`jasper.zsh` and `jasper.bash`, replacing the single `TERM_PROGRAM` line:

```sh
[[ "${TERM_PROGRAM-}" == "Jasper" || "${JASPER_TERMINAL-}" == "1" ]] || return 0
```

`jasper.fish`, in the opening condition:

```fish
if status is-interactive
    and begin; test "$TERM_PROGRAM" = Jasper; or test "$JASPER_TERMINAL" = 1; end
    and not set -q JASPER_INTEGRATION_LOADED
```

Check the line budgets after editing: `wc -l` must be at most 115 for `jasper.bash` and 90 for the other two.

- [x] **Step 5: Verify**

Run: `./gradlew :jasper-app:test --rerun-tasks`
Expected: PASS.

- [x] **Step 6: Commit**

```bash
git add jasper-app/src/main/java/dev/jasper/app/LaunchSettings.java \
        jasper-app/src/main/resources/dev/jasper/app/shell-integration/ \
        jasper-app/src/test/java/dev/jasper/app/
git commit -m "feat: load shell integration inside tmux, which overwrites TERM_PROGRAM"
```

---

### Task 4: the `history.deprioritize_trivial` setting

**Files:**
- Modify: `jasper-app/src/main/java/dev/jasper/app/ConfigLoader.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/ConfigSnapshot.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/ConfigTemplate.java`
- Modify: `config.example.toml`
- Modify: `jasper-app/src/main/java/dev/jasper/app/PaletteContext.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/WindowCommandPalette.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/WindowContent.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/ShellHistoryScope.java`
- Test: `jasper-app/src/test/java/dev/jasper/app/ConfigLoaderTest.java`, `ShellHistoryScopeTest.java`

**Interfaces:**
- Produces: `ConfigSnapshot.deprioritizeTrivial()` — `boolean`, default `true`.
- Produces: `PaletteContext.deprioritizeTrivial()` — `boolean`, carried per query like `maxResults`.
- Produces: `ShellHistoryScope.TRIVIAL` — the built-in set, package-private for the test.

- [x] **Step 1: Write the failing tests**

```java
    @Test void trivialCommandsSortBelowRealWorkWhenTheSettingIsOn() {
        // "clear" is the most recent, "./gradlew build" older; with the setting on, work comes first.
        var index = indexWith(entry("clear", 200), entry("./gradlew build", 100));
        var scope = new ShellHistoryScope(index);

        var on = scope.search("", context(true)).rows();
        assertThat(on).extracting(PaletteRow::title).containsExactly("./gradlew build", "clear");

        var off = scope.search("", context(false)).rows();
        assertThat(off).extracting(PaletteRow::title).containsExactly("clear", "./gradlew build");
    }

    @Test void trivialCommandsAreStillFoundWhenSearchedFor() {
        var index = indexWith(entry("clear", 200), entry("./gradlew build", 100));
        var scope = new ShellHistoryScope(index);
        assertThat(scope.search("clear", context(true)).rows()).extracting(PaletteRow::title).contains("clear");
    }

    @Test void trivialCommandsKeepTheirOrderAmongThemselves() {
        var index = indexWith(entry("exit", 300), entry("clear", 200), entry("./gradlew build", 100));
        var scope = new ShellHistoryScope(index);
        assertThat(scope.search("", context(true)).rows()).extracting(PaletteRow::title)
            .containsExactly("./gradlew build", "exit", "clear");
    }
```

and in `ConfigLoaderTest`, following the shape of the existing `history.enabled` cases:

```java
    @Test void deprioritizeTrivialParsesAndDefaultsToOn() {
        assertThat(parse("").snapshot().deprioritizeTrivial()).isTrue();
        assertThat(parse("[history]\ndeprioritize_trivial=false\n").snapshot().deprioritizeTrivial()).isFalse();
        var bad = parse("[history]\ndeprioritize_trivial='yes'\n");
        assertThat(bad.snapshot().deprioritizeTrivial()).isTrue();
        assertThat(bad.diagnostics()).isNotEmpty();
    }
```

- [x] **Step 2: Run to verify they fail**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.ShellHistoryScopeTest' --tests 'dev.jasper.app.ConfigLoaderTest' --rerun-tasks`
Expected: compile failure on the new accessors, then ordering failures.

- [x] **Step 3: Add the setting**

`ConfigLoader`: add `"deprioritize_trivial"` to the `history` field set, a `private boolean deprioritizeTrivial = true;`, the case

```java
            case "history.deprioritize_trivial" -> deprioritizeTrivial = bool(path, value, deprioritizeTrivial);
```

and pass it into the `ConfigSnapshot` it builds. `ConfigSnapshot` gains the component with a defaulting convenience constructor, exactly as `historyEnabled` has.

`ConfigTemplate` and `config.example.toml` gain:

```toml
[history]
# Live. Rank exit, clear, ls, ll, cd and pwd below more substantial commands in the History palette.
# They are still listed and still searchable, just never above real work.
deprioritize_trivial = true
```

- [x] **Step 4: Carry it to the scope and apply it**

`PaletteContext` gains a `boolean deprioritizeTrivial` component, defaulting to `true` in the short constructors. `WindowCommandPalette` sets it where it sets `maxResults`, and `WindowContent.applyConfiguration` pushes it the same way `commandPalette.setMaxResults(next.maxResults())` is pushed.

In `ShellHistoryScope`:

```java
    /** Commands that are noise at the top of a recency list: still listed, still searchable, just lower. */
    static final Set<String> TRIVIAL = Set.of("exit", "clear", "ls", "ll", "la", "cd", "pwd", "c");

    /** True for a command whose first word is trivial and which carries at most one argument. */
    static boolean trivial(String command) {
        String[] words = command.strip().split("\\s+");
        return words.length <= 2 && TRIVIAL.contains(words[0].toLowerCase(Locale.ROOT));
    }
```

In `search`, for the empty query, partition before taking `maxResults`:

```java
        if (q.isEmpty()) {
            List<ShellHistoryEntry> ordered = snapshot.entries();
            if (context.deprioritizeTrivial()) {
                var work = new ArrayList<ShellHistoryEntry>();
                var noise = new ArrayList<ShellHistoryEntry>();
                for (ShellHistoryEntry entry : ordered) (trivial(entry.command()) ? noise : work).add(entry);
                work.addAll(noise);
                ordered = work;
            }
            var rows = new ArrayList<PaletteRow>();
            for (ShellHistoryEntry entry : ordered) {
                if (rows.size() == context.maxResults()) break;
                rows.add(row(entry, tagged));
            }
            return new PaletteResults(rows, "Most recent", null);
        }
```

For a non-empty query, add a trivial flag as the **last** component of the existing `Ranked` comparator, so a search for `clear` still finds it but a broad query prefers real work. Keep the existing `tier`/`directory`/`position` ordering ahead of it.

- [x] **Step 5: Verify**

Run: `./gradlew check --rerun-tasks`
Expected: PASS. Read exact counts from `*/build/test-results/test/TEST-*.xml`.

- [x] **Step 6: Commit**

```bash
git add jasper-app/src config.example.toml
git commit -m "feat: add history.deprioritize_trivial so exit and clear stop crowding the list"
```

---

### Task 5: documentation

**Files:**
- Modify: `docs/configuration.md`
- Modify: `docs/command-palette.md`
- Modify: `docs/STATUS.md`
- Modify: `docs/superpowers/specs/2026-09-16-jasper-history-ranking-design.md`

- [x] **Step 1: Document the setting and the new behaviour**

`docs/configuration.md` gains `history.deprioritize_trivial` in the settings table and a short section naming the built-in set. The Shell integration section records that tmux now works for zsh and fish, that bash inside tmux still needs the manual `source` line, and that attaching to a tmux server started before Jasper gets nothing because its environment predates Jasper.

`docs/command-palette.md` records that the History list now refreshes about once a second while the palette is subscribed, and that a shell which records no timestamps is ranked by when its file was written rather than sinking to the bottom.

- [x] **Step 2: Correct the spec's one wrong claim**

The spec says the scope "reads the flag from the config snapshot it already receives". Replace with the truth: the flag rides on `PaletteContext` beside `maxResults`, and the partition happens in the scope rather than in `ShellHistorySnapshot.build`.

- [x] **Step 3: Add the STATUS entry**

Describe the four changes, the measured evidence behind each, the deviation above, the bash-inside-tmux gap, and the real test counts read from the result XML. Record as user-run: the History palette inside tmux on the real desktop.

- [x] **Step 4: Commit**

```bash
git add docs/
git commit -m "docs: record history ranking, polling, tmux integration and the new setting"
```

---

## Self-review

**Spec coverage.** Ranking → Task 1. Freshness → Task 2. tmux marker, guard and injection → Task 3. `deprioritize_trivial` → Task 4. Documentation and the spec correction → Task 5. Every "Testing" bullet in the spec maps to a step above.

**Deliberately not done.** A user-configurable trivial list, bash integration inside tmux, and rewriting tmux's `default-command` — all listed out of scope in the spec.

**Type consistency.** `ShellHistorySnapshot.Source(List<ShellHistoryEntry>, long)` is introduced in Task 1 and used by `ShellHistoryIndex` in the same task; no later task changes it. `Keyed.timestamp` becomes `Keyed.rank` in Task 1 only. `PaletteContext.deprioritizeTrivial()` is introduced in Task 4 and read only by `ShellHistoryScope`. `ShellHistoryScope.TRIVIAL` and `trivial(String)` are introduced in Task 4 and referenced by its tests.
