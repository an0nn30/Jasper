# Start-up Recovery, Plan 3: The Application — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

Status: **Complete on 2026-10-14** on `claude/startup-recovery` (spec approved by the user on 2026-10-14). Beyond the
eleven deviations below: Task 0 (plan 2 carry-over, `b05c2585`) removed `_JPACKAGE_LAUNCHER` from the watcher's Windows
launches and recorded the watcher's re-exec handling, which resolves plan 2's "plan 3's task 0"; and in Task 1
`AotCacheRecord.matches` also requires a non-negative modification time, as the launcher's matcher does.

**Goal:** After the launcher recovers from a JVM that died while starting, Jasper says so in its start-up line,
checks the start-up cache that was skipped, remembers a failing cache in `<home>/jasper-aot.failed` so later launches
skip it, and tells the user once, in the "Memory & VM options" group, without ever blaming a broken custom option on
the cache.

**Architecture:** A new JDK-only value type `dev.jasper.app.vmoptions.AotCacheRecord` parses, formats, matches and
writes the record with the launcher's rules, proven against plan 2's shared vectors. `VmOptionsLaunch` reads the two
new launch properties, `VmOptionsCheck` attributes its AOT cache check (`Findings.cache` as an `Optional<Fatal>` and
whether it used the custom options), and `StartupTrace` names a skipped cache in the start-up line. In
`dev.jasper.app.application`, `VmOptionsFeature.startupSettled()` runs the after-start flow on its existing worker and
`VmOptionsNotices` posts the one new notification. `JasperApplication` needs no change: it already builds
`VmOptionsFeature` from `VmOptionsLaunch.current()` and calls `startupSettled()` once start-up settles.

**Tech Stack:** Java 25 (JBR 25.0.4.1), JUnit 5 (6.1.3 BOM) + AssertJ 3.27.7, Gradle.

**Spec:** [`docs/superpowers/specs/2026-10-14-jasper-startup-recovery-design.md`](../specs/2026-10-14-jasper-startup-recovery-design.md)
(section 6, the application rows of sections 8 and 9, and the application's part of section 10; plan 3 of section 12).
The custom VM options design it extends is
[`2026-10-13-jasper-custom-vm-options-design.md`](../specs/2026-10-13-jasper-custom-vm-options-design.md) (sections 3.4
and 6.4).

## Global Constraints

- **Order.** Plans 1 and 2 of this spec are on the branch first. Plan 2 creates
  `packaging/launcher/test/aot-record-vectors.txt` and makes the launcher pass the properties below. Before Task 1,
  check that the vectors file exists; if it does not, stop and ask.
- **Launch properties** (spec 6.1 and the custom VM options design 3.4). Existing: `jasper.vmOptionsFile`,
  `jasper.vmOptionsDigest`, `jasper.vmOptionsFile.corrupted`, `jasper.vmOptionsSkipped`. New:
  - `jasper.aotCacheSkipped`: "the cache was skipped for this run; the value is the reason (`launch-failed`,
    `remembered`, or the variable's value or `environment`)". The launcher adds it only when the cache was in use and
    is being skipped.
  - `jasper.aotCacheRecord=<absolute path of the record file>`: added by the launcher whenever the merged options name
    an AOT cache, including a start that then skips it. The app writes the record to exactly that path, and writes
    nothing when the property is absent (for example `:jasper-app:run`).
- **The record** (spec 6.2), UTF-8, LF, exactly:

  ```
  # Jasper skips its start-up cache while this file matches it. Delete this file to try the cache again.
  cache=<absolute path of jasper.aot>
  size=<bytes>
  modified=<seconds since the epoch>
  options=<digest, or empty>
  ```

  `cache` is the cache path as the launcher expands `-XX:AOTCache=$APPDIR/jasper.aot`, a required key written for
  information that matching never compares (spec 6.2 as amended by the lead on 2026-10-14); `size` and `modified` come
  from the file (modification time in whole seconds); `options` is the custom options' digest when the failing cache
  check used the custom options, else empty. "The app writes it atomically (temporary file and move) and never deletes it; a
  new build makes it stale."
- **Parsing** (identical in Java and C++): split on `\n`; remove one trailing `\r` from each line; skip blank lines and
  lines whose first character is `#`; every other line must be `key=value` (split at the first `=`) with key one of
  `cache`, `size`, `modified`, `options`; a line without `=`, an unknown key or a repeated key makes the record
  malformed; all four keys are required; `cache` non-empty; `size` and `modified` are 1–19 ASCII decimal digits;
  `options` empty or exactly 16 lowercase hex digits. A missing, unreadable or malformed record matches nothing.
  Confirmed by the lead: "blank" means empty after removing one trailing `\r` (a line of spaces is malformed); `size`
  and `modified` may have leading zeros and compare as unsigned 64-bit numbers.
- **Matching** (as amended by the lead): record valid AND a regular file exists at the expanded cache path AND its size
  equals `size` AND its modification time in whole seconds equals `modified` AND (`options` empty OR equals this
  start's digest; no digest never matches a non-empty `options`). The path is not compared. The app needs matching only
  for tests against the shared vectors; it never skips anything itself, the launcher decides.
- **Shared vectors** (plan 2's file, read the way `VmOptionsSyntaxTest` reads `digest-vectors.txt`):

  ```
  ## Each vector: a line "record", the record's lines verbatim (none for an empty file), a line
  ## "actual <size> <modified> <digest> <path>" (size and modified are both "absent" when no file is at the path; digest
  ## "-" when the start has none; the path is the rest of the line and may contain spaces), then "= match" or
  ## "= no match". Lines starting "##" are comments of this file.
  ```

  The file is split on `\n` only. Inside a vector's record lines, a line ending with the two characters backslash and
  `r` (`\r`) stands for that record line ending with a carriage return; there are no other escapes.
- **Attribution** (spec 6.3, verbatim): "`VmOptionsCheck` step 3 runs the cache check (`-XX:AOTMode=on -cp <classpath>
  -version`) with the merged line-up when step 2 found no fatal message, and otherwise with the bundled options alone,
  so a broken custom option is never blamed on the cache. `Findings` carries the cache result as an `Optional<Fatal>`
  with Java's message, in place of `cacheRejected`, and whether that check used the custom options."
- **System Settings sentence** (spec 6.3, verbatim): "The System Settings sentence "These options turn off Jasper's
  start-up cache; it will start more slowly." appears only when the failing cache check used them."
- **Start-up line** (spec 6.1): `Startup (cold, AOT cache skipped: launch-failed, CDS on)`.
- **The notification** (spec 6.4, verbatim): one notification in "Memory & VM options", Warning: "Jasper started
  without its start-up cache", text "The start-up cache made Java fail at launch, so Jasper starts more slowly until a
  new Jasper build replaces it." and Java's message. Its content is exactly that text, a space, `Java reported: ` and
  Java's message. Group id `jasper.vmoptions` (`VmOptionsNotices.GROUP`); no action button. Java's message for the
  cache (the lead's rule): the text of the first output line that starts with `[` and contains `][error][aot` and whose
  text after the last `] ` contains `Exception` or `Error:`, taken after that `] ` and stripped; otherwise the existing
  `fatal(...)` line.
- **After a recovered start** (spec 6.4, verbatim): "`jasper.aotCacheSkipped=launch-failed`: run the check. When the
  cache check fails, write the record (`options` set when the check used the custom options) and post one
  notification … When it passes, log at INFO that the cache failed at launch and passes now, and write nothing; the
  next start tries it." "`remembered` or any other value: one INFO log line." "`jasper.vmOptionsSkipped=launch-failed`:
  unchanged ("Jasper started without your custom VM options") … When both were skipped, each notification appears only
  when its own check fails."
- **Errors** (spec 8): "The record cannot be written | Logged at WARNING; the notification still appears, and the next
  start fails once more"; "The check times out | No record and no notification, as `VmOptionsCheck` does today".
- **Packages** (spec 6.4): "The record's reading, writing and matching live in `dev.jasper.app.vmoptions` (JDK-only, as
  its `package-info` says); the notification and its wiring in `VmOptionsFeature` and `VmOptionsNotices`."
  `verifyApplicationArchitecture` enforces the JDK-only rule.
- **Logs hold fixed descriptions only** (`docs/diagnostics.md`: records contain "a timestamp, severity, fixed operation
  description …", and Jasper's call sites log no environment values or user configuration paths; `AppLog` writes a
  record's message without its parameters). Every new log line is a plain fixed string: no path, no environment value,
  no Java output.
- **Start-up path:** nothing new runs before start-up settles. The after-start work runs on the existing
  `jasper-vmoptions` worker from `VmOptionsFeature.startupSettled()`.
- **Inventory tests.** `ThemeMigrationCoverageTest` needs a row in `docs/design/theme-engine/migration.csv` for every
  Java file matching its `CANDIDATE` pattern (for example one containing `new ArrayList`); this plan's new files do not
  match it (`AotCacheRecordTest` uses a `StringBuilder`), so keep them that way or add the row.
  `AppDocumentationTest` resolves every Markdown link in the docs and in plan files.
- **MVP** (memory `jasper-mvp-no-compat`): `Findings.cacheRejected` is removed outright, no alias.
- Never launch the Jasper GUI and never run `:jasper-app:run`, `:jasper-app:bench`, `trainAotCache`, `packageDist` or
  anything with `-Pjasper.aot=true` (AGENTS.md). Work in the worktree `.claude/worktrees/startup-recovery` on branch
  `claude/startup-recovery`, never on `main`. Every commit message ends with:

  ```
  🤖 Generated with Claude Code at The Home Depot

  Co-Authored-By: Claude <noreply@anthropic.com>
  ```
- Source hygiene (AGENTS.md): no raw control, private-use or unpaired surrogate characters in Java sources; write
  escapes such as `"\uFEFF"` and `"\u00e4"`. `-Xlint:all` is on for every compile; keep new code warning-free.

## Verified while writing this plan (2026-10-14; do not re-derive)

- The JVM's output for the 2026-10-14 failure, from the installed app's runtime:
  `[0.142s][error][aot               ] java.lang.SecurityException: sealing violation: can't seal package com.jetbrains:
  already defined`, `Error occurred during initialization of VM`, `Unexpected exception when loading aot-linked
  classes.` `VmOptionsCheck.fatal` skips unified-logging lines and takes the line after HotSpot's marker; the cache
  rule (`cacheFatal`, Task 4) takes the first line instead, so the notification reads
  `java.lang.SecurityException: sealing violation: can't seal package com.jetbrains: already defined`.
- The test JVM (JBR 25.0.4.1, macOS) with `-XX:AOTCache=<missing file> -XX:AOTMode=on -cp <jar> -version` prints three
  `[error][aot]` lines, then `Error occurred during initialization of VM` and `Unable to use AOT cache.`, exit 1.
- The recorded `cache` is the last `-XX:AOTCache=` of the line-up the failing check ran with, from `Jasper.cfg`
  through `LauncherImage` (`$APPDIR` resolved from the normalized `jpackage.app-path`). Matching ignores it, so how a
  launch spelled the app's path never decides whether the cache is skipped.
- Every task's code was compiled with `javac -Xlint:all -Werror` and its tests run (97 tests, 0 failures, 6 failing
  first where Task 5 says so) in a scratch harness against `main` at `4328f02b`, with a stand-in for plan 2's
  vectors file that uses the `\r` escape and a vector whose path differs from the record's (`= match`).

## Planned deviations from the spec

1. **`jasper.aotCacheRecord` (an addition to spec 6.2, agreed with plan 2).** The launcher names the record file; the
   app writes to exactly that path and writes nothing without it. The app never computes `<home>` itself.
2. **One notification per record.** A failing cache check whose record file already holds the same values (same
   `cache`, `size`, `modified`, `options`) neither rewrites it nor posts again, and logs one INFO line. This keeps the
   user's decision 3 ("once per failed cache") and spec 9's "one notification per record" whenever a record is there
   but the launcher did not use it (for example it could not read the file). A new build's cache gives a new record
   and a new notification.
3. **A record that cannot be written** (spec 8) also covers a start without `jasper.aotCacheRecord`: WARNING, and the
   notification still appears.
4. **The start-up line names a hand-set skip `environment`.** `jasper.aotCacheSkipped` keeps the variable's value, but
   the start-up line reports `launch-failed`, `remembered` and `environment` as they are and any other value as
   `environment`, because the log holds no environment values. The trace file's `jvm.aotCache` carries the same
   `skipped: <reason>` text.
5. **`options` is the digest of the lines the failing check ran with** (`VmOptionsLaunch.appliedDigest` of the file as
   read after start-up). That equals this start's `jasper.vmOptionsDigest` while the file is unchanged; in a start
   that also skipped the custom options, which carries no digest, it is the digest the next start will carry.
6. **"When both were skipped"** means both were skipped after failed launches (`launch-failed`). With the cache
   `remembered` or skipped by hand, the custom VM options notification is unchanged (always posted after a
   `launch-failed` options skip). When both are `launch-failed` and the options pass their check, an INFO line is
   logged instead of their notification.
7. **When the after-start work runs.** As today, `startupSettled()` runs on `OUTPUT_PAINTED`, `WARMED_UP` or 5 s after
   `FIRST_PAINT` (custom VM options plan 2, deviation 7), not on `OUTPUT_PAINTED` alone.
8. **Matching ignores the path** (spec 6.2 as amended by the lead on 2026-10-14). `cache` stays a required non-empty
   key and is written for information; the launcher and `AotCacheRecord.matches` compare only the file at the
   expanded cache path (its size and modification time) and the options. A moved or reinstalled copy of the same
   build therefore still skips its broken cache, and the spelling of the app's path never matters.
9. **Wording the spec leaves open:** the notification joins the text and Java's message with `Java reported: ` (the
   words of spec 5.3's dialog); the log lines are this plan's (Task 5, Global Constraints' log rule).
10. **`VmOptionsCheck.checkWithCache`.** System Settings keeps `check`, which still runs nothing without custom
    options; the after-start flow calls `checkWithCache`, which in a trained image also checks the cache with the
    bundled options when the file applies no lines (a start without custom options can still have a broken cache).
11. **Java's message for the cache names the cause** (the lead's rule; spec 6.4 says "Java's message"). The cache
    result's message is the JVM's own `[error][aot]` line that names an exception or error (Global Constraints, "The
    notification"), else `fatal(...)`'s line, so the 2026-10-14 failure reads
    `java.lang.SecurityException: sealing violation: can't seal package com.jetbrains: already defined` and a missing
    cache still reads `Unable to use AOT cache.` The log never carries it.

## Review Focus

Inputs the spec implies that are most likely to bite a person, each pinned by a test in the owning task:

1. **Jasper moved to another folder, or reinstalled from the same DMG** (the same build, so the same broken cache):
   the record still matches wherever the cache now is, so Jasper keeps skipping it instead of failing again — Task 1,
   `captureReadsTheCacheFileAsTheLauncherSeesIt` (the moved copy) and the vectors' path case.
2. **Jasper's home cannot take the record** (a file where the folder should be, a read-only folder): a WARNING with the
   exception, the notification still appears, nothing escapes the worker — Task 5,
   `aRecordThatCannotBeWrittenIsLoggedAndTheNoticeStillAppears`.
3. **Both skipped after failed launches, one of them innocent** (`-Xms4g` in `jasper.vmoptions` with a good cache, or a
   broken cache with good options): no notification blames the innocent one — Task 5,
   `whenBothWereSkippedEachNoticeAppearsOnlyWhenItsOwnCheckFails`.
4. **Jasper installed under a path with a space or a non-ASCII character** (`/Users/me/My Apps/Jäsper.app`): the
   record is UTF-8 and reads back unchanged — Task 1, `writingReplacesTheFileAtomicallyAndReadingFindsIt`.
5. **A hand-set `JASPER_AOT_CACHE_SKIP` holding anything** (`yes`, a path): the start-up line says `environment` and no
   log line repeats the value — Task 2, `theJvmStatusNamesASkippedCacheByItsReason`, and Task 5,
   `aRememberedOrHandSetSkipIsOneLogLine`.

## File structure

| Path | Responsibility |
|---|---|
| `jasper-app/src/main/java/dev/jasper/app/vmoptions/AotCacheRecord.java` (new) | The record: parse, format, match, capture the cache file, read, write. |
| `jasper-app/src/main/java/dev/jasper/app/vmoptions/VmOptionsFile.java` | Atomic write names its temporary file after the target. |
| `jasper-app/src/main/java/dev/jasper/app/vmoptions/package-info.java` | Package contract mentions the record. |
| `jasper-app/src/main/java/dev/jasper/app/vmoptions/VmOptionsLaunch.java` | Reads `jasper.aotCacheSkipped` and `jasper.aotCacheRecord`. |
| `jasper-app/src/main/java/dev/jasper/app/vmoptions/VmOptionsCheck.java` | Cache check attribution, `Findings.cache`, `checkWithCache`. |
| `jasper-app/src/main/java/dev/jasper/app/startup/StartupTrace.java` | `JvmStatus` names a skipped cache. |
| `jasper-app/src/main/java/dev/jasper/app/application/VmOptionsFeature.java` | The Settings sentence rule; the after-start flow. |
| `jasper-app/src/main/java/dev/jasper/app/application/VmOptionsNotices.java` | The "Jasper started without its start-up cache" notification. |
| Tests under `jasper-app/src/test/java/dev/jasper/app/{vmoptions,startup,application}/` | `AotCacheRecordTest` (new), `VmOptionsLaunchTest`, `VmOptionsCheckTest`, `StartupTraceTest`, `VmOptionsFeatureTest`. |
| Docs | `docs/configuration.md`, `docs/diagnostics.md`, `docs/notifications.md`, `docs/app-architecture.md`, `docs/app-maintenance.md`, `docs/STATUS.md`, the spec's and this plan's status. |

---

### Task 1: The remembered failure, `AotCacheRecord`

**Files:**
- Create: `jasper-app/src/main/java/dev/jasper/app/vmoptions/AotCacheRecord.java`
- Create: `jasper-app/src/test/java/dev/jasper/app/vmoptions/AotCacheRecordTest.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/vmoptions/VmOptionsFile.java:93` (temporary file prefix)
- Modify: `jasper-app/src/main/java/dev/jasper/app/vmoptions/package-info.java:1-8` (contract)

**Interfaces:**
- Consumes: `VmOptionsFile.write(Path file, String text) throws IOException` (atomic, follows a symbolic link, creates
  the directory); plan 2's `packaging/launcher/test/aot-record-vectors.txt`.
- Produces (all `public`, in `dev.jasper.app.vmoptions`):
  - `record AotCacheRecord(String cache, long size, long modified, Optional<String> options)`; the constructor throws
    `IllegalArgumentException` for an empty `cache`, one holding `\n`, or `options` that are not 16 lowercase hex
    digits. `size` and `modified` are read and written as unsigned 64-bit numbers.
  - `static final String HEADER` (the record's first line).
  - `record AotCacheRecord.CacheFile(long size, long modified)` with
    `static Optional<CacheFile> at(Path path) throws IOException` (a regular file, following links; else empty).
  - `static Optional<String> cachePath(List<String> options)`: the value of the last non-empty `-XX:AOTCache=`.
  - `static AotCacheRecord capture(List<String> options, Optional<String> digest) throws IOException`.
  - `static Optional<AotCacheRecord> parse(String text)`; `String format()`.
  - `boolean matches(Optional<CacheFile> file, Optional<String> digest)` and
    `static boolean matches(String text, Optional<CacheFile> file, Optional<String> digest)`; `file` is the regular
    file at the expanded cache path (or empty), and the path itself is not compared.
  - `static Optional<AotCacheRecord> read(Path file)` (missing, unreadable, not UTF-8 or malformed → empty);
    `void write(Path file) throws IOException`.

- [ ] **Step 1: Confirm plan 2's vectors are there**

Run: `ls packaging/launcher/test/aot-record-vectors.txt`
Expected: the path is listed. If it is missing, plan 2 is not on the branch: stop and ask.

- [ ] **Step 2: Write the failing test**

Create `jasper-app/src/test/java/dev/jasper/app/vmoptions/AotCacheRecordTest.java`:

```java
package dev.jasper.app.vmoptions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AotCacheRecordTest {
    private static final Path ROOT = Path.of(System.getProperty("jasper.repoRoot", "..")).toAbsolutePath().normalize();
    private static final String CACHE = "/Applications/Jasper.app/Contents/app/jasper.aot";
    private static final String DIGEST = "c758526d21c369d0";
    private static final AotCacheRecord.CacheFile FILE = new AotCacheRecord.CacheFile(57_016_320, 1_791_966_245);

    @TempDir Path home;

    private static String text(String... lines) {
        var text = new StringBuilder();
        for (String line : lines) text.append(line).append('\n');
        return text.toString();
    }

    private static String valid(String options) {
        return text(AotCacheRecord.HEADER, "cache=" + CACHE, "size=57016320", "modified=1791966245", "options=" + options);
    }

    @Test void aRecordFormatsAsTheSpecSaysAndReadsBack() {
        var record = new AotCacheRecord(CACHE, 57_016_320, 1_791_966_245, Optional.of(DIGEST));
        assertThat(record.format()).isEqualTo(
            "# Jasper skips its start-up cache while this file matches it. Delete this file to try the cache again.\n"
                + "cache=/Applications/Jasper.app/Contents/app/jasper.aot\n"
                + "size=57016320\n"
                + "modified=1791966245\n"
                + "options=c758526d21c369d0\n");
        assertThat(AotCacheRecord.parse(record.format())).contains(record);
        var bundled = new AotCacheRecord(CACHE, 1, 2, Optional.empty());
        assertThat(bundled.format()).endsWith("\noptions=\n");
        assertThat(AotCacheRecord.parse(bundled.format())).contains(bundled);
    }

    @Test void commentsEmptyLinesAndCarriageReturnsAreSkippedAndOrderIsFree() {
        String crlf = "# note\r\n\r\noptions=\r\nmodified=1791966245\r\n#cache=/elsewhere\r\nsize=57016320\r\ncache=" + CACHE + "\r\n";
        assertThat(AotCacheRecord.parse(crlf)).contains(new AotCacheRecord(CACHE, 57_016_320, 1_791_966_245, Optional.empty()));
        assertThat(AotCacheRecord.parse(valid("").replace("cache=" + CACHE, "cache=/a\r\r")))
            .as("only one carriage return is removed").hasValueSatisfying(record -> assertThat(record.cache()).isEqualTo("/a\r"));
        assertThat(AotCacheRecord.parse(valid("").replace("cache=" + CACHE, "cache= /my app/x=y.aot")))
            .as("values are verbatim and split at the first =").hasValueSatisfying(record -> assertThat(record.cache()).isEqualTo(" /my app/x=y.aot"));
        assertThat(AotCacheRecord.parse(valid("").replace("size=57016320", "size=0057016320")))
            .as("leading zeros are digits").hasValueSatisfying(record -> assertThat(record.size()).isEqualTo(57_016_320));
        assertThat(AotCacheRecord.parse(valid("").replace("size=57016320", "size=9999999999999999999")))
            .as("19 digits always fit").hasValueSatisfying(record -> assertThat(Long.toUnsignedString(record.size())).isEqualTo("9999999999999999999"));
    }

    @Test void aMalformedRecordReadsAsNothing() {
        assertThat(AotCacheRecord.parse("")).isEmpty();
        assertThat(AotCacheRecord.parse(text(AotCacheRecord.HEADER))).isEmpty();
        assertThat(AotCacheRecord.parse(valid("").replace("options=\n", ""))).as("a missing key").isEmpty();
        assertThat(AotCacheRecord.parse(valid("") + "size=1\n")).as("a repeated key").isEmpty();
        assertThat(AotCacheRecord.parse(valid("") + "extra=1\n")).as("an unknown key").isEmpty();
        assertThat(AotCacheRecord.parse(valid("") + "no equals sign\n")).as("a line without =").isEmpty();
        assertThat(AotCacheRecord.parse(valid("") + "   \n")).as("spaces are not an empty line").isEmpty();
        assertThat(AotCacheRecord.parse(valid("").replace("cache=", " cache="))).as("keys are exact").isEmpty();
        assertThat(AotCacheRecord.parse(valid("").replace("cache=" + CACHE, "cache="))).as("an empty cache").isEmpty();
        assertThat(AotCacheRecord.parse(valid("").replace("size=57016320", "size="))).isEmpty();
        assertThat(AotCacheRecord.parse(valid("").replace("size=57016320", "size=57a"))).isEmpty();
        assertThat(AotCacheRecord.parse(valid("").replace("size=57016320", "size=+5"))).isEmpty();
        assertThat(AotCacheRecord.parse(valid("").replace("size=57016320", "size=-5"))).isEmpty();
        assertThat(AotCacheRecord.parse(valid("").replace("size=57016320", "size=12345678901234567890"))).as("20 digits").isEmpty();
        assertThat(AotCacheRecord.parse(valid("").replace("modified=1791966245", "modified=1.5"))).isEmpty();
        assertThat(AotCacheRecord.parse(valid("C758526D21C369D0"))).as("uppercase hex").isEmpty();
        assertThat(AotCacheRecord.parse(valid("c758526d21c369d"))).as("15 digits").isEmpty();
        assertThat(AotCacheRecord.parse(valid("c758526d21c369d0 "))).as("a trailing space").isEmpty();
        assertThat(AotCacheRecord.parse("\uFEFF" + valid(""))).as("a byte order mark is not a comment").isEmpty();
    }

    @Test void aRecordMatchesOnlyTheSameCacheFileAndOptions() {
        var bundled = new AotCacheRecord(CACHE, FILE.size(), FILE.modified(), Optional.empty());
        assertThat(bundled.matches(Optional.of(FILE), Optional.empty())).isTrue();
        assertThat(bundled.matches(Optional.of(FILE), Optional.of(DIGEST))).as("empty options match any start").isTrue();
        assertThat(bundled.matches(Optional.empty(), Optional.empty())).as("no file there").isFalse();
        assertThat(bundled.matches(Optional.of(new AotCacheRecord.CacheFile(FILE.size() + 1, FILE.modified())), Optional.empty()))
            .as("a new build: another size").isFalse();
        assertThat(bundled.matches(Optional.of(new AotCacheRecord.CacheFile(FILE.size(), FILE.modified() + 1)), Optional.empty()))
            .as("a new build: another time").isFalse();
        var custom = new AotCacheRecord(CACHE, FILE.size(), FILE.modified(), Optional.of(DIGEST));
        assertThat(custom.matches(Optional.of(FILE), Optional.of(DIGEST))).isTrue();
        assertThat(custom.matches(Optional.of(FILE), Optional.of("0000000000000000"))).as("other options").isFalse();
        assertThat(custom.matches(Optional.of(FILE), Optional.empty())).as("a start without custom options").isFalse();
        assertThat(AotCacheRecord.matches(valid(""), Optional.of(FILE), Optional.empty())).isTrue();
        assertThat(AotCacheRecord.matches(valid("") + "junk\n", Optional.of(FILE), Optional.empty())).as("malformed").isFalse();
    }

    /**
     * The launcher's shared cases. A record line ending with the two characters backslash and r stands for that line
     * ending with a carriage return; the path at the end of an "actual" line is the expanded cache path, which matching
     * ignores.
     */
    @Test void theLaunchersVectorsAgree() throws IOException {
        String vectors = Files.readString(ROOT.resolve("packaging/launcher/test/aot-record-vectors.txt"), StandardCharsets.UTF_8);
        StringBuilder record = null;
        String actual = null;
        int matches = 0;
        int misses = 0;
        for (String line : vectors.split("\n", -1)) {
            if (line.startsWith("##")) continue;
            if (record == null) {
                if (line.isEmpty()) continue;
                assertThat(line).as("a vector starts with a line \"record\"").isEqualTo("record");
                record = new StringBuilder();
            } else if (actual == null) {
                if (line.startsWith("actual ")) actual = line;
                else record.append(line.endsWith("\\r") ? line.substring(0, line.length() - 2) + "\r" : line).append('\n');
            } else {
                if (line.isEmpty()) continue;
                assertThat(line).as("after %s", actual).isIn("= match", "= no match");
                String[] facts = actual.split(" ", 5);
                assertThat(facts).as("actual <size> <modified> <digest> <path>: %s", actual).hasSize(5);
                Optional<AotCacheRecord.CacheFile> file = facts[1].equals("absent") ? Optional.empty()
                    : Optional.of(new AotCacheRecord.CacheFile(number(facts[1]), number(facts[2])));
                Optional<String> digest = facts[3].equals("-") ? Optional.empty() : Optional.of(facts[3]);
                boolean expected = line.equals("= match");
                assertThat(AotCacheRecord.matches(record.toString(), file, digest)).as("record %s against %s", record, actual)
                    .isEqualTo(expected);
                if (expected) matches++;
                else misses++;
                record = null;
                actual = null;
            }
        }
        assertThat(record).as("the last vector is complete").isNull();
        assertThat(matches).as("vectors that match").isPositive();
        assertThat(misses).as("vectors that do not match").isPositive();
    }

    /** A vector's number: unsigned, or negative with a sign (a file modified before 1970). */
    private static long number(String value) { return value.startsWith("-") ? Long.parseLong(value) : Long.parseUnsignedLong(value); }

    @Test void theCachePathIsTheLastAotCacheOption() {
        assertThat(AotCacheRecord.cachePath(List.of("-Xmx2048m", "-XX:AOTCache=/a/jasper.aot", "-XX:AOTCache=/b.aot", "-Dx=1")))
            .contains("/b.aot");
        assertThat(AotCacheRecord.cachePath(List.of("-XX:AOTCache=/a.aot", "-XX:AOTCache="))).as("an empty value names nothing").contains("/a.aot");
        assertThat(AotCacheRecord.cachePath(List.of("-Xmx2048m"))).isEmpty();
    }

    @Test void captureReadsTheCacheFileAsTheLauncherSeesIt() throws IOException {
        Path cache = Files.writeString(home.resolve("jasper.aot"), "cache");
        Files.setLastModifiedTime(cache, FileTime.from(Instant.parse("2026-10-14T08:24:05.900Z")));
        var record = AotCacheRecord.capture(List.of("-Xmx2048m", "-XX:AOTCache=" + cache), Optional.of(DIGEST));
        assertThat(record).isEqualTo(new AotCacheRecord(cache.toString(), 5, 1_791_966_245, Optional.of(DIGEST)));
        assertThat(record.matches(AotCacheRecord.CacheFile.at(cache), Optional.of(DIGEST))).isTrue();
        Path moved = Files.copy(cache, Files.createDirectory(home.resolve("Moved.app")).resolve("jasper.aot"), StandardCopyOption.COPY_ATTRIBUTES);
        assertThat(record.matches(AotCacheRecord.CacheFile.at(moved), Optional.of(DIGEST))).as("the same build moved elsewhere").isTrue();
        assertThat(AotCacheRecord.CacheFile.at(home)).as("a directory").isEmpty();
        assertThat(AotCacheRecord.CacheFile.at(home.resolve("missing.aot"))).isEmpty();
        assertThatThrownBy(() -> AotCacheRecord.capture(List.of("-XX:AOTCache=" + home.resolve("missing.aot")), Optional.empty()))
            .isInstanceOf(NoSuchFileException.class);
        assertThatThrownBy(() -> AotCacheRecord.capture(List.of("-Xmx2048m"), Optional.empty())).isInstanceOf(IOException.class);
        assertThatThrownBy(() -> AotCacheRecord.capture(List.of("-XX:AOTCache=/a.aot\r"), Optional.empty()))
            .as("a path that would not read back").isInstanceOf(IOException.class);
    }

    @Test void writingReplacesTheFileAtomicallyAndReadingFindsIt() throws IOException {
        Path file = home.resolve("jasper-aot.failed");
        var first = new AotCacheRecord(CACHE, 1, 2, Optional.empty());
        first.write(file);
        assertThat(Files.readString(file)).isEqualTo(first.format());
        var second = new AotCacheRecord("/Users/me/My Apps/J\u00e4sper.app/Contents/app/jasper.aot", 3, 4, Optional.of(DIGEST));
        second.write(file);
        assertThat(AotCacheRecord.read(file)).as("a space and a non-ASCII character").contains(second);
        assertThat(Files.readAllBytes(file)).as("UTF-8").containsSequence((byte) 'J', (byte) 0xC3, (byte) 0xA4, (byte) 's');
        try (Stream<Path> entries = Files.list(home)) {
            assertThat(entries.map(path -> path.getFileName().toString())).as("no temporary file").containsExactly("jasper-aot.failed");
        }
    }

    @Test void aMissingUnreadableOrNonUtf8FileReadsAsNothing() throws IOException {
        assertThat(AotCacheRecord.read(home.resolve("jasper-aot.failed"))).isEmpty();
        assertThat(AotCacheRecord.read(home)).as("a directory").isEmpty();
        Path invalid = home.resolve("invalid");
        byte[] bytes = valid("").replace("cache=" + CACHE, "cache=/a").getBytes(StandardCharsets.UTF_8);
        byte[] broken = new byte[bytes.length + 1];
        System.arraycopy(bytes, 0, broken, 0, bytes.length);
        broken[bytes.length] = (byte) 0xC3;
        Files.write(invalid, broken);
        assertThat(AotCacheRecord.read(invalid)).isEmpty();
    }

    @Test void onlyRecordsThatReadBackCanBeMade() {
        assertThatThrownBy(() -> new AotCacheRecord("", 1, 2, Optional.empty())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AotCacheRecord("/a\n/b", 1, 2, Optional.empty())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AotCacheRecord(CACHE, 1, 2, Optional.of("XYZ"))).isInstanceOf(IllegalArgumentException.class);
    }
}
```

- [ ] **Step 3: Run it to see it fail**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.vmoptions.AotCacheRecordTest'`
Expected: FAIL to compile: `cannot find symbol` … `class AotCacheRecord`.

- [ ] **Step 4: Write the implementation**

Create `jasper-app/src/main/java/dev/jasper/app/vmoptions/AotCacheRecord.java`:

```java
package dev.jasper.app.vmoptions;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * The remembered start-up cache failure, {@code <home>/jasper-aot.failed} (start-up recovery spec section 6.2): the AOT
 * cache that made Java fail at launch, as it was on disk, and the custom options its failing check ran with. The launcher
 * skips the cache while the record matches it, wherever the cache now is; the application writes the record and never
 * deletes it. Parsing and matching follow the rules the launcher's native code shares through
 * {@code packaging/launcher/test/aot-record-vectors.txt}. Values and pure functions, plus blocking file I/O on the
 * caller's thread.
 *
 * @param cache the cache's path as the launcher expands its {@code -XX:AOTCache=} option; written for information, never
 *              compared
 * @param size the cache file's size in bytes, read as an unsigned 64-bit number
 * @param modified the cache file's modification time in whole seconds since the epoch, read as an unsigned 64-bit number
 * @param options the digest of the custom VM options the failing check ran with; empty when it ran with the bundled
 *                options alone
 */
public record AotCacheRecord(String cache, long size, long modified, Optional<String> options) {
    /** The record file's first line. */
    public static final String HEADER = "# Jasper skips its start-up cache while this file matches it. Delete this file to try the cache again.";

    private static final String CACHE_OPTION = "-XX:AOTCache=";
    private static final List<String> KEYS = List.of("cache", "size", "modified", "options");
    private static final Pattern NUMBER = Pattern.compile("[0-9]{1,19}");
    private static final Pattern DIGEST = Pattern.compile("[0-9a-f]{16}");

    /** {@code cache} is non-empty and holds no line feed; {@code options} is 16 lowercase hex digits when present. */
    public AotCacheRecord {
        Objects.requireNonNull(cache, "cache");
        Objects.requireNonNull(options, "options");
        if (cache.isEmpty() || cache.indexOf('\n') >= 0) throw new IllegalArgumentException("A cache path is one non-empty line");
        if (options.isPresent() && !DIGEST.matcher(options.get()).matches()) {
            throw new IllegalArgumentException("A VM options digest is 16 lowercase hex digits");
        }
    }

    /**
     * What the match compares about the file at the cache's path: a regular file's size and its modification time in
     * whole seconds since the epoch (rounded down, as POSIX {@code st_mtime}).
     */
    public record CacheFile(long size, long modified) {
        /** The regular file at {@code path}, following links; empty when nothing, or something else, is there. Blocking. */
        public static Optional<CacheFile> at(Path path) throws IOException {
            BasicFileAttributes attributes;
            try {
                attributes = Files.readAttributes(path, BasicFileAttributes.class);
            } catch (NoSuchFileException absent) {
                return Optional.empty();
            }
            if (!attributes.isRegularFile()) return Optional.empty();
            return Optional.of(new CacheFile(attributes.size(), attributes.lastModifiedTime().toInstant().getEpochSecond()));
        }
    }

    /** The path of the cache {@code options} name: the value of their last non-empty {@code -XX:AOTCache=}. */
    public static Optional<String> cachePath(List<String> options) {
        for (int i = options.size() - 1; i >= 0; i--) {
            String option = options.get(i);
            if (option.startsWith(CACHE_OPTION) && option.length() > CACHE_OPTION.length()) {
                return Optional.of(option.substring(CACHE_OPTION.length()));
            }
        }
        return Optional.empty();
    }

    /**
     * The record of the cache {@code options} name, as it is on disk now; {@code digest} is the custom options' digest
     * when the failing check ran with them. Blocking.
     *
     * @throws IOException when the options name no cache, its path would not read back (a line break in it, or a
     *                     carriage return at its end), no regular file is there, or its modification time is before 1970
     */
    public static AotCacheRecord capture(List<String> options, Optional<String> digest) throws IOException {
        String cache = cachePath(options).orElseThrow(() -> new IOException("The options name no AOT cache"));
        if (cache.indexOf('\n') >= 0 || cache.endsWith("\r")) throw new IOException("The AOT cache's path cannot be recorded");
        CacheFile file = CacheFile.at(Path.of(cache)).orElseThrow(() -> new NoSuchFileException(cache));
        if (file.modified() < 0) throw new IOException("The AOT cache's modification time is before 1970");
        return new AotCacheRecord(cache, file.size(), file.modified(), digest);
    }

    /**
     * Reads a record (spec 6.2): lines split on {@code \n}, one trailing {@code \r} removed from each; empty lines and
     * lines starting with {@code #} skipped; every other line {@code key=value}, split at the first {@code =}, with each
     * of {@code cache}, {@code size}, {@code modified} and {@code options} exactly once and no other key; {@code cache}
     * non-empty, {@code size} and {@code modified} 1 to 19 ASCII digits, {@code options} empty or 16 lowercase hex
     * digits. Anything else is malformed and reads as empty.
     */
    public static Optional<AotCacheRecord> parse(String text) {
        var values = new HashMap<String, String>();
        for (String raw : text.split("\n", -1)) {
            String line = raw.endsWith("\r") ? raw.substring(0, raw.length() - 1) : raw;
            if (line.isEmpty() || line.charAt(0) == '#') continue;
            int equals = line.indexOf('=');
            if (equals < 0) return Optional.empty();
            String key = line.substring(0, equals);
            if (!KEYS.contains(key) || values.putIfAbsent(key, line.substring(equals + 1)) != null) return Optional.empty();
        }
        if (values.size() != KEYS.size()) return Optional.empty();
        String cache = values.get("cache");
        String size = values.get("size");
        String modified = values.get("modified");
        String options = values.get("options");
        if (cache.isEmpty() || !NUMBER.matcher(size).matches() || !NUMBER.matcher(modified).matches()
            || !(options.isEmpty() || DIGEST.matcher(options).matches())) {
            return Optional.empty();
        }
        return Optional.of(new AotCacheRecord(cache, Long.parseUnsignedLong(size), Long.parseUnsignedLong(modified),
            options.isEmpty() ? Optional.empty() : Optional.of(options)));
    }

    /** The record file's text: the header, then the four keys in order, UTF-8 with LF line ends. */
    public String format() {
        return HEADER + "\n"
            + "cache=" + cache + "\n"
            + "size=" + Long.toUnsignedString(size) + "\n"
            + "modified=" + Long.toUnsignedString(modified) + "\n"
            + "options=" + options.orElse("") + "\n";
    }

    /**
     * Whether the launcher skips the cache (spec 6.2, as amended): {@code file}, the regular file at the expanded cache
     * path, has this record's size and modification time, and its options are empty or equal {@code digest}, the start's
     * {@code jasper.vmOptionsDigest} (a start without one never matches recorded options). The path is not compared.
     */
    public boolean matches(Optional<CacheFile> file, Optional<String> digest) {
        return file.isPresent() && file.get().size() == size && file.get().modified() == modified
            && (options.isEmpty() || options.equals(digest));
    }

    /** Whether the record file's {@code text} matches; a malformed record matches nothing. */
    public static boolean matches(String text, Optional<CacheFile> file, Optional<String> digest) {
        return parse(text).filter(record -> record.matches(file, digest)).isPresent();
    }

    /** The record in {@code file}; empty when it is missing, unreadable, not UTF-8 or malformed. Blocking. */
    public static Optional<AotCacheRecord> read(Path file) {
        try {
            byte[] bytes = Files.readAllBytes(file);
            return parse(StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString());
        } catch (IOException unreadable) {
            return Optional.empty();
        }
    }

    /** Replaces {@code file} with this record atomically (a temporary file beside it, then a move). Blocking. */
    public void write(Path file) throws IOException {
        VmOptionsFile.write(file, format());
    }
}
```

In `jasper-app/src/main/java/dev/jasper/app/vmoptions/VmOptionsFile.java`, replace the line

```java
        Path temporary = Files.createTempFile(directory, ".jasper.vmoptions", ".tmp");
```

with

```java
        Path temporary = Files.createTempFile(directory, "." + target.getFileName(), ".tmp");
```

(the same prefix for `jasper.vmoptions`; the record's temporary file is `.jasper-aot.failed…tmp`).

In `jasper-app/src/main/java/dev/jasper/app/vmoptions/package-info.java`, replace the first paragraph (lines 1-8, up
to and including `the JMX notification thread.`) with:

```java
/**
 * Custom VM options (spec section 6.4): the launcher's reading of {@code jasper.vmoptions}, mirrored for the
 * application; editing the file; what this JVM was launched with; the bundled launcher configuration; the check
 * that runs the bundled {@code java} with the options; the record of a start-up cache that made Java fail at launch
 * (start-up recovery spec 6.2); and the memory readings and low-memory watch of spec sections 5.3 and 5.4. JDK-only: no
 * Swing or AWT ({@code com.sun.management} for the physical memory); {@code verifyApplicationArchitecture} enforces it.
 * Values and pure functions, plus file I/O and a check that run on the caller's thread; the one resource is
 * {@code HeapThreshold}, which its creator closes and whose callback runs on the JMX notification thread.
```

(keep the `<p>Allowed outgoing Jasper dependencies: …` lines and `package dev.jasper.app.vmoptions;` unchanged).

- [ ] **Step 5: Run the tests to see them pass**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.vmoptions.*' --tests 'dev.jasper.app.appearance.ThemeMigrationCoverageTest' --tests 'dev.jasper.app.documentation.AppDocumentationTest' verifyApplicationArchitecture`
Expected: BUILD SUCCESSFUL. If `theLaunchersVectorsAgree` fails, compare the failing vector with the Global
Constraints' parsing, matching and vector rules (the lead confirmed them for both sides) and fix whichever side breaks
them; if the rules themselves are in doubt, stop and ask.

- [ ] **Step 6: Commit**

```bash
git add jasper-app/src/main/java/dev/jasper/app/vmoptions/AotCacheRecord.java \
  jasper-app/src/main/java/dev/jasper/app/vmoptions/VmOptionsFile.java \
  jasper-app/src/main/java/dev/jasper/app/vmoptions/package-info.java \
  jasper-app/src/test/java/dev/jasper/app/vmoptions/AotCacheRecordTest.java
git commit -F - <<'EOF'
Read and write the record of a start-up cache that failed at launch

AotCacheRecord is the app's side of <home>/jasper-aot.failed: the
launcher skips the AOT cache while the record matches it, and the app
writes it after a start that had to skip the cache. Parsing and matching
follow the launcher's rules and are checked against the shared vectors.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

---

### Task 2: The start-up line names a skipped cache

**Files:**
- Modify: `jasper-app/src/main/java/dev/jasper/app/startup/StartupTrace.java:39-72, 106` (`AOT_CACHE_SKIPPED_PROPERTY`,
  `JvmStatus`, `detached()`)
- Test: `jasper-app/src/test/java/dev/jasper/app/startup/StartupTraceTest.java`

**Interfaces:**
- Consumes: the launcher's `-Djasper.aotCacheSkipped=<reason>`.
- Produces: package-private `StartupTrace.AOT_CACHE_SKIPPED_PROPERTY = "jasper.aotCacheSkipped"` and
  `static JvmStatus of(String aotCacheOption, boolean sharing, String skipped)` (the two-argument `of` is gone);
  `JvmStatus.aotCache()` is `skipped: <reason>` when the launcher skipped the cache. `StartupTraceTest.Fixture` gains
  a mutable `StartupTrace.JvmStatus status` field that its trace reports (`StartupTraceFileTest` keeps using the
  fixture unchanged).

- [ ] **Step 1: Write the failing tests**

In `jasper-app/src/test/java/dev/jasper/app/startup/StartupTraceTest.java`, in `Fixture`, after
`final List<Runnable> timers = new ArrayList<>();` add

```java
        StartupTrace.JvmStatus status = StartupTrace.JvmStatus.of("/Jasper.app/Contents/app/jasper.aot", true, null);
```

and in `Fixture.trace(Path file)` replace `() -> StartupTrace.JvmStatus.of("/Jasper.app/Contents/app/jasper.aot", true),`
with `() -> status,`.

Replace the test `theJvmStatusNamesAStaleCache` with:

```java
    @Test void theJvmStatusNamesAStaleCache() {
        assertThat(StartupTrace.JvmStatus.of("", true, null)).isEqualTo(new StartupTrace.JvmStatus("none", "on"));
        assertThat(StartupTrace.JvmStatus.of("/a.aot", true, null)).isEqualTo(new StartupTrace.JvmStatus("used", "on"));
        assertThat(StartupTrace.JvmStatus.of("/a.aot", false, null)).isEqualTo(new StartupTrace.JvmStatus("stale", "off"));
        assertThat(StartupTrace.JvmStatus.of(null, false, null)).isEqualTo(new StartupTrace.JvmStatus("none", "off"));
    }

    @Test void theJvmStatusNamesASkippedCacheByItsReason() {
        assertThat(StartupTrace.JvmStatus.of("", true, "launch-failed")).isEqualTo(new StartupTrace.JvmStatus("skipped: launch-failed", "on"));
        assertThat(StartupTrace.JvmStatus.of("", true, "remembered")).isEqualTo(new StartupTrace.JvmStatus("skipped: remembered", "on"));
        assertThat(StartupTrace.JvmStatus.of("", false, "environment")).isEqualTo(new StartupTrace.JvmStatus("skipped: environment", "off"));
        assertThat(StartupTrace.JvmStatus.of("", true, "/Users/me/notes")).as("a hand-set value stays out of the log")
            .isEqualTo(new StartupTrace.JvmStatus("skipped: environment", "on"));
        assertThat(StartupTrace.JvmStatus.of("/a.aot", true, " ")).as("a blank value is no skip")
            .isEqualTo(new StartupTrace.JvmStatus("used", "on"));
    }

    @Test void theLaunchersSkipReachesTheObservedStatus() {
        String previous = System.getProperty("jasper.aotCacheSkipped");
        System.setProperty("jasper.aotCacheSkipped", "remembered");
        try {
            assertThat(StartupTrace.JvmStatus.observe().aotCache()).isEqualTo("skipped: remembered");
        } finally {
            if (previous == null) System.clearProperty("jasper.aotCacheSkipped");
            else System.setProperty("jasper.aotCacheSkipped", previous);
        }
    }

    @Test void aStartWithoutTheCacheSaysWhyInTheLineAndTheFile() {
        var fixture = new Fixture();
        fixture.status = StartupTrace.JvmStatus.of("", true, "launch-failed");
        StartupTrace trace = fixture.trace();
        fixture.at(20);
        trace.mark(StartupTrace.Milestone.MAIN_ENTERED);
        trace.settle();
        assertThat(fixture.log).containsExactly("Startup (cold, AOT cache skipped: launch-failed, CDS on): main entered 120 ms");
        assertThat(trace.document(trace.status()).get("jvm")).isEqualTo(java.util.Map.of("aotCache", "skipped: launch-failed", "cds", "on"));
    }
```

- [ ] **Step 2: Run them to see them fail**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.startup.*'`
Expected: FAIL to compile: `method of in record JvmStatus cannot be applied to given types`.

- [ ] **Step 3: Write the implementation**

In `jasper-app/src/main/java/dev/jasper/app/startup/StartupTrace.java`, after the line
`public static final String TRACE_FILE_PROPERTY = "jasper.startupTrace";` add:

```java
    /** Set by Jasper's launcher when it started this JVM without the AOT cache; the value is the reason. */
    static final String AOT_CACHE_SKIPPED_PROPERTY = "jasper.aotCacheSkipped";
```

Replace

```java
    /** Whether this JVM mapped an AOT cache or the default CDS archive. */
    record JvmStatus(String aotCache, String cds) {
        static JvmStatus of(String aotCacheOption, boolean sharing) {
            boolean configured = aotCacheOption != null && !aotCacheOption.isEmpty();
            return new JvmStatus(!configured ? "none" : sharing ? "used" : "stale", sharing ? "on" : "off");
        }
```

with

```java
    /** Whether this JVM mapped an AOT cache or the default CDS archive, or why the launcher left the cache out. */
    record JvmStatus(String aotCache, String cds) {
        private static final List<String> SKIP_REASONS = List.of("launch-failed", "remembered", "environment");

        /**
         * {@code skipped} is {@code jasper.aotCacheSkipped}, which the launcher sets when it starts this JVM without
         * the cache. {@code launch-failed}, {@code remembered} and {@code environment} are reported as they are; any other
         * value is a hand-set {@code JASPER_AOT_CACHE_SKIP} and reads {@code environment}, because the log holds no
         * environment values.
         */
        static JvmStatus of(String aotCacheOption, boolean sharing, String skipped) {
            String cds = sharing ? "on" : "off";
            if (skipped != null && !skipped.isBlank()) {
                return new JvmStatus("skipped: " + (SKIP_REASONS.contains(skipped) ? skipped : "environment"), cds);
            }
            boolean configured = aotCacheOption != null && !aotCacheOption.isEmpty();
            return new JvmStatus(!configured ? "none" : sharing ? "used" : "stale", cds);
        }
```

In `JvmStatus.observe()`, replace `return of(configured, sharing);` with:

```java
            return of(configured, sharing, System.getProperty(AOT_CACHE_SKIPPED_PROPERTY));
```

In `detached()`, replace `() -> JvmStatus.of("", false)` with `() -> JvmStatus.of("", false, null)`.

- [ ] **Step 4: Run the tests to see them pass**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.startup.*' --tests 'dev.jasper.app.benchmark.*'`
Expected: BUILD SUCCESSFUL (`StartupTraceFileTest` still reads `aotCache` `used` from the fixture's default status).

- [ ] **Step 5: Commit**

```bash
git add jasper-app/src/main/java/dev/jasper/app/startup/StartupTrace.java \
  jasper-app/src/test/java/dev/jasper/app/startup/StartupTraceTest.java
git commit -F - <<'EOF'
Name a skipped start-up cache in the start-up line

When the launcher starts the JVM without the AOT cache it says why in
jasper.aotCacheSkipped; the start-up line now reads, for example,
"Startup (cold, AOT cache skipped: launch-failed, CDS on)". A hand-set
JASPER_AOT_CACHE_SKIP value reads "environment", so the log holds no
environment values.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

---

### Task 3: The launch's start-up cache properties

**Files:**
- Modify: `jasper-app/src/main/java/dev/jasper/app/vmoptions/VmOptionsLaunch.java:11-46`
- Test: `jasper-app/src/test/java/dev/jasper/app/vmoptions/VmOptionsLaunchTest.java`
- Modify (constructor calls only): `jasper-app/src/test/java/dev/jasper/app/application/VmOptionsFeatureTest.java:44,
  152, 227, 242, 273`

**Interfaces:**
- Consumes: the launcher's `-Djasper.aotCacheSkipped=<reason>` and `-Djasper.aotCacheRecord=<path>`.
- Produces: `record VmOptionsLaunch(Optional<Path> file, Optional<String> digest, boolean corrupted,
  Optional<String> skipped, Optional<String> aotCacheSkipped, Optional<Path> aotCacheRecord)`; a blank property reads
  as absent; a record path the platform cannot parse reads as absent (logged at WARNING).

- [ ] **Step 1: Write the failing tests**

In `jasper-app/src/test/java/dev/jasper/app/vmoptions/VmOptionsLaunchTest.java`:

In `theLaunchersPropertiesAreRead`, after `assertThat(launch.launcherManaged()).isTrue();` add

```java
        assertThat(launch.aotCacheSkipped()).isEmpty();
```

Add this test before `aRestartIsNeededWhenTheAppliedLinesDiffer`:

```java
    @Test void theStartUpCachesPropertiesAreRead() {
        var launch = VmOptionsLaunch.of(properties("jasper.vmOptionsFile", "/h/jasper.vmoptions", "jasper.aotCacheSkipped", "launch-failed",
            "jasper.aotCacheRecord", "/h/jasper-aot.failed"));
        assertThat(launch.aotCacheSkipped()).contains("launch-failed");
        assertThat(launch.aotCacheRecord()).contains(Path.of("/h/jasper-aot.failed"));
        var development = VmOptionsLaunch.of(properties());
        assertThat(development.aotCacheSkipped()).isEmpty();
        assertThat(development.aotCacheRecord()).as(":jasper-app:run remembers nothing").isEmpty();
        var unparsable = VmOptionsLaunch.of(properties("jasper.aotCacheRecord", "bad\u0000path", "jasper.aotCacheSkipped", "remembered"));
        assertThat(unparsable.aotCacheRecord()).as("a path the platform cannot parse").isEmpty();
        assertThat(unparsable.aotCacheSkipped()).contains("remembered");
    }
```

Give every direct constructor call the two new components. In `VmOptionsLaunchTest`:

```java
        var applied = new VmOptionsLaunch(Optional.of(Path.of("/h/jasper.vmoptions")), Optional.of(VmOptionsSyntax.digest(List.of("-Xmx2048m"))),
            false, Optional.empty(), Optional.empty(), Optional.empty());
```

```java
        var none = new VmOptionsLaunch(Optional.of(Path.of("/h/jasper.vmoptions")), Optional.empty(), false, Optional.empty(), Optional.empty(), Optional.empty());
```

```java
        var skipped = new VmOptionsLaunch(Optional.of(Path.of("/h/jasper.vmoptions")), Optional.empty(), false, Optional.of("launch-failed"),
            Optional.empty(), Optional.empty());
```

```java
        var corrupted = new VmOptionsLaunch(Optional.of(Path.of("/h/jasper.vmoptions")), Optional.empty(), true, Optional.empty(), Optional.empty(), Optional.empty());
```

In `jasper-app/src/test/java/dev/jasper/app/application/VmOptionsFeatureTest.java`, the helper on line 44 becomes

```java
    private VmOptionsLaunch launch(boolean corrupted, Optional<String> skipped) {
        return new VmOptionsLaunch(Optional.of(file()), Optional.empty(), corrupted, skipped, Optional.empty(), Optional.empty());
    }
```

and the four other calls become, in file order:

```java
        var feature = feature(new VmOptionsLaunch(Optional.empty(), Optional.empty(), false, Optional.empty(), Optional.empty(), Optional.empty()), false);
```

```java
        var launch = new VmOptionsLaunch(Optional.of(home.resolve("blocker/jasper.vmoptions")), Optional.empty(), false, Optional.empty(),
            Optional.empty(), Optional.empty());
```

```java
            var launch = new VmOptionsLaunch(Optional.of(folder.resolve("jasper.vmoptions")), Optional.empty(), false, Optional.empty(),
                Optional.empty(), Optional.empty());
```

```java
        var launch = new VmOptionsLaunch(Optional.of(file()), Optional.of(VmOptionsSyntax.digest(List.of("-Xmx1g"))), false, Optional.empty(),
            Optional.empty(), Optional.empty());
```

- [ ] **Step 2: Run them to see them fail**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.vmoptions.VmOptionsLaunchTest' --tests 'dev.jasper.app.application.VmOptionsFeatureTest'`
Expected: FAIL to compile: `constructor VmOptionsLaunch in record VmOptionsLaunch cannot be applied to given types`
and `cannot find symbol` … `method aotCacheSkipped()`.

- [ ] **Step 3: Write the implementation**

In `jasper-app/src/main/java/dev/jasper/app/vmoptions/VmOptionsLaunch.java`, replace

```java
 * @param corrupted whether it ignored the file as corrupted
 * @param skipped why it skipped the file ({@code launch-failed} after the guard's fallback start)
 */
public record VmOptionsLaunch(Optional<Path> file, Optional<String> digest, boolean corrupted, Optional<String> skipped) {
```

with

```java
 * @param corrupted whether it ignored the file as corrupted
 * @param skipped why it skipped the file ({@code launch-failed} after the launcher's fallback start)
 * @param aotCacheSkipped why it started this JVM without the AOT cache (start-up recovery spec 6.1): {@code launch-failed},
 *                        {@code remembered}, or a hand-set {@code JASPER_AOT_CACHE_SKIP}'s value ({@code environment}
 *                        when blank)
 * @param aotCacheRecord the file in which the application remembers a cache that failed at launch (spec 6.2); empty when
 *                       the options named no cache or Jasper's launcher did not start this JVM
 */
public record VmOptionsLaunch(Optional<Path> file, Optional<String> digest, boolean corrupted, Optional<String> skipped,
                              Optional<String> aotCacheSkipped, Optional<Path> aotCacheRecord) {
```

and replace `of(Properties)` and `file(Properties)` (from `static VmOptionsLaunch of(Properties properties) {` through
the end of `file`) with:

```java
    static VmOptionsLaunch of(Properties properties) {
        return new VmOptionsLaunch(path(properties, "jasper.vmOptionsFile", "so the VM options are read-only"),
            present(properties, "jasper.vmOptionsDigest"), "true".equals(properties.getProperty("jasper.vmOptionsFile.corrupted")),
            present(properties, "jasper.vmOptionsSkipped"), present(properties, "jasper.aotCacheSkipped"),
            path(properties, "jasper.aotCacheRecord", "so a start-up cache that fails at launch cannot be remembered"));
    }

    /**
     * A path the launcher reported. A value this platform cannot parse (the Windows launcher passes arguments through the
     * ANSI code page, so unrepresentable characters arrive as {@code ?}) reads as absent instead of stopping Jasper from
     * starting; {@code consequence} says what that costs.
     */
    private static Optional<Path> path(Properties properties, String name, String consequence) {
        Optional<String> value = present(properties, name);
        if (value.isEmpty()) return Optional.empty();
        try {
            return Optional.of(Path.of(value.get()));
        } catch (InvalidPathException unparsable) {
            LOG.log(System.Logger.Level.WARNING, name + " is not a path this platform can parse, " + consequence + ": {0}", value.get());
            return Optional.empty();
        }
    }
```

(`AppLog` writes the message without its `{0}` parameter, so the unparsable value never reaches the log file.)

- [ ] **Step 4: Run the tests to see them pass**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.vmoptions.*' --tests 'dev.jasper.app.application.VmOptionsFeatureTest'`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add jasper-app/src/main/java/dev/jasper/app/vmoptions/VmOptionsLaunch.java \
  jasper-app/src/test/java/dev/jasper/app/vmoptions/VmOptionsLaunchTest.java \
  jasper-app/src/test/java/dev/jasper/app/application/VmOptionsFeatureTest.java
git commit -F - <<'EOF'
Read the launcher's start-up cache properties

VmOptionsLaunch now carries jasper.aotCacheSkipped, why the launcher
started this JVM without the AOT cache, and jasper.aotCacheRecord, the
file in which the app remembers a cache that failed at launch.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

---

### Task 4: The check never blames a broken option on the cache

**Files:**
- Modify: `jasper-app/src/main/java/dev/jasper/app/vmoptions/VmOptionsCheck.java:17-21, 42-54, 77-100, 156` (new
  `cacheFatal` after `fatal`)
- Modify: `jasper-app/src/main/java/dev/jasper/app/application/VmOptionsFeature.java:325` (the Settings sentence)
- Test: `jasper-app/src/test/java/dev/jasper/app/vmoptions/VmOptionsCheckTest.java`,
  `jasper-app/src/test/java/dev/jasper/app/application/VmOptionsFeatureTest.java`

**Interfaces:**
- Consumes: `LauncherImage.hasAotCache()`, `LauncherImage.javaOptions()`, `LauncherImage.classpath()`,
  `VmOptionsSyntax.merge(List<String>, List<String>)`, `VmOptionsCheck.fatal(String)`.
- Produces:
  - `record VmOptionsCheck.Findings(List<String> unrecognized, Optional<Fatal> fatal, Optional<Fatal> cache,
    boolean cacheUsedOptions)` and `Findings.NONE`; `cacheRejected` is gone.
  - `Findings check(VmOptionsSyntax.Parsed parsed) throws IOException` (System Settings; runs nothing without custom
    lines) and new `Findings checkWithCache(VmOptionsSyntax.Parsed parsed) throws IOException` (also checks a trained
    image's cache with the bundled options when there are no custom lines).
  - In both, the cache check uses the merged line-up when the custom lines exist and step 2 found no fatal message,
    else `image.javaOptions()` alone; `cacheUsedOptions` says which (false when no cache check ran). Any run over its
    limit still returns `Findings.NONE`.
  - Package-private `static Fatal cacheFatal(String output)`: the cache result's `Fatal`, whose message is the JVM's
    own `[error][aot]` line naming an exception or error, else `fatal(output)`'s; `recoverable` is `fatal(output)`'s.
  - `VmOptionsFeatureTest` gains a `trained` flag and `cacheFile()` (`<home>/app/jasper.aot`); with `trained` its
    image's options end with `-XX:AOTCache=<cacheFile()>`. Task 5 builds on both.

- [ ] **Step 1: Write the failing tests**

In `jasper-app/src/test/java/dev/jasper/app/vmoptions/VmOptionsCheckTest.java`, replace the imports with:

```java
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.File;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
```

After the `UNRECOVERABLE` constant add:

```java
    /** What the installed app printed on 2026-10-14 (start-up recovery spec section 1). */
    private static final String SEALED = "[0.142s][error][aot               ] java.lang.SecurityException: sealing violation: "
        + "can't seal package com.jetbrains: already defined\nError occurred during initialization of VM\n"
        + "Unexpected exception when loading aot-linked classes.\n";
    private static final VmOptionsCheck.Fatal SEALED_FATAL = new VmOptionsCheck.Fatal(
        "java.lang.SecurityException: sealing violation: can't seal package com.jetbrains: already defined", false);
    /** What the test JVM printed on 2026-10-14 for a missing cache with -XX:AOTMode=on. */
    private static final String MISSING = "[0.005s][error][aot] An error has occurred while processing the AOT cache. Run with -Xlog:aot for details.\n"
        + "[0.005s][error][aot] Loading static archive failed.\n[0.005s][error][aot] Unable to map shared spaces\n"
        + "Error occurred during initialization of VM\nUnable to use AOT cache.\n";
    private static final String CLASSPATH = "/app/jasper-app.jar" + File.pathSeparator + "/app/b.jar";

    @TempDir Path temp;
```

Replace the test `aTrainedImageAlsoChecksTheCache` with:

```java
    @Test void aTrainedImageAlsoChecksTheCacheWithTheLineUp() throws IOException {
        var findings = check(TRAINED, command -> command.contains("-XX:AOTMode=on") ? new VmOptionsCheck.Outcome(1, "Unable to use AOT cache.\n", false) : ok())
            .check(parsed("-XX:+UseZGC\n"));
        assertThat(findings.cache()).contains(new VmOptionsCheck.Fatal("Unable to use AOT cache.", true));
        assertThat(findings.cacheUsedOptions()).isTrue();
        assertThat(runs.getLast()).containsSubsequence("-XX:+UseZGC", "-XX:AOTMode=on", "-cp", CLASSPATH, "-version");
        runs.clear();
        var untrained = check(IMAGE, command -> ok()).check(parsed("-XX:+UseZGC\n"));
        assertThat(untrained.cache()).isEmpty();
        assertThat(untrained.cacheUsedOptions()).isFalse();
        assertThat(runs).noneMatch(command -> command.contains("-XX:AOTMode=on"));
    }

    @Test void optionsThatStopTheJvmAreNeverBlamedOnTheCache() throws IOException {
        var findings = check(TRAINED, command -> command.contains("-Xmx3q") ? new VmOptionsCheck.Outcome(1, RECOVERABLE, false)
            : command.contains("-XX:AOTMode=on") ? new VmOptionsCheck.Outcome(1, SEALED, false) : ok()).check(parsed("-Xmx3q\n"));
        assertThat(findings.fatal()).contains(new VmOptionsCheck.Fatal("Invalid maximum heap size: -Xmx3q", true));
        assertThat(runs.getLast()).as("the cache check runs with the bundled options alone").containsExactly("/java", "-Xmx256m",
            "-XX:+IgnoreUnrecognizedVMOptions", "-XX:AOTCache=/app/jasper.aot", "-XX:AOTMode=on", "-cp", CLASSPATH, "-version");
        assertThat(findings.cache()).as("Java's own message").contains(SEALED_FATAL);
        assertThat(findings.cacheUsedOptions()).isFalse();
    }

    @Test void aRefusedCacheIsExplainedByTheJvmsOwnAotErrorWhenItNamesTheCause() {
        assertThat(VmOptionsCheck.cacheFatal(SEALED)).isEqualTo(SEALED_FATAL);
        assertThat(VmOptionsCheck.cacheFatal(MISSING)).as("no AOT error names an exception: HotSpot's line")
            .isEqualTo(new VmOptionsCheck.Fatal("Unable to use AOT cache.", false));
        assertThat(VmOptionsCheck.cacheFatal("[0.1s][warning][aot] java.lang.IllegalStateException: only a warning\nUnable to use AOT cache.\n"))
            .as("warnings never explain").isEqualTo(new VmOptionsCheck.Fatal("Unable to use AOT cache.", true));
        assertThat(VmOptionsCheck.cacheFatal("[0.1s][error][aot,load] java.lang.OutOfMemoryError: Java heap space\n"
            + "Error occurred during initialization of VM\nUnexpected exception when loading aot-linked classes.\n"))
            .isEqualTo(new VmOptionsCheck.Fatal("java.lang.OutOfMemoryError: Java heap space", false));
    }

    @Test void afterASkippedCacheTheCacheIsCheckedEvenWithoutCustomOptions() throws IOException {
        var findings = check(TRAINED, command -> command.contains("-XX:AOTMode=on") ? new VmOptionsCheck.Outcome(1, SEALED, false) : ok())
            .checkWithCache(parsed("# only a comment\n"));
        assertThat(runs).singleElement().satisfies(command -> assertThat(command).containsExactly("/java", "-Xmx256m",
            "-XX:+IgnoreUnrecognizedVMOptions", "-XX:AOTCache=/app/jasper.aot", "-XX:AOTMode=on", "-cp", CLASSPATH, "-version"));
        assertThat(findings).isEqualTo(new VmOptionsCheck.Findings(List.of(), Optional.empty(), Optional.of(SEALED_FATAL), false));
        runs.clear();
        assertThat(check(TRAINED, command -> ok()).checkWithCache(VmOptionsSyntax.parse(new byte[] {'-', 0})))
            .as("a corrupted file: the bundled options pass").isEqualTo(VmOptionsCheck.Findings.NONE);
        assertThat(runs).hasSize(1);
        runs.clear();
        var withOptions = check(TRAINED, command -> command.contains("-XX:AOTMode=on") ? new VmOptionsCheck.Outcome(1, SEALED, false) : ok())
            .checkWithCache(parsed("-Xmx1g\n"));
        assertThat(withOptions.cacheUsedOptions()).as("the options start the JVM, so the cache check uses them").isTrue();
        assertThat(runs.getLast()).containsSubsequence("-Xmx1g", "-XX:AOTMode=on");
        runs.clear();
        assertThat(check(IMAGE, command -> ok()).checkWithCache(parsed(""))).as("no cache to check").isEqualTo(VmOptionsCheck.Findings.NONE);
        assertThat(runs).isEmpty();
        assertThat(check(TRAINED, command -> new VmOptionsCheck.Outcome(-1, "", true)).checkWithCache(parsed("")))
            .as("a cache check over its limit finds nothing").isEqualTo(VmOptionsCheck.Findings.NONE);
    }
```

Replace the test `nothingToCheckRunsNothing` with:

```java
    @Test void nothingToCheckRunsNothing() throws IOException {
        assertThat(check(IMAGE, command -> ok()).check(parsed("# only\nXmx300m\n"))).isEqualTo(VmOptionsCheck.Findings.NONE);
        assertThat(check(IMAGE, command -> ok()).check(VmOptionsSyntax.parse(new byte[] {'-', 0}))).isEqualTo(VmOptionsCheck.Findings.NONE);
        assertThat(check(TRAINED, command -> ok()).check(parsed("# only\n"))).as("Settings never checks the cache alone")
            .isEqualTo(VmOptionsCheck.Findings.NONE);
        assertThat(runs).isEmpty();
    }
```

Before the test `anInterruptStopsTheCheck` add:

```java
    @Test void theRealJvmRefusesACacheItCannotUse() throws IOException {
        var image = new LauncherImage(temp, List.of("-Xmx256m", "-XX:AOTCache=" + temp.resolve("missing.aot")), List.of(temp.resolve("a.jar").toString()));
        var findings = new VmOptionsCheck(VmOptionsCheck.runtimeJava(), image, VmOptionsCheck::runProcess, Duration.ofSeconds(30))
            .checkWithCache(VmOptionsSyntax.Parsed.EMPTY);
        assertThat(findings.cache()).contains(new VmOptionsCheck.Fatal("Unable to use AOT cache.", false));
        assertThat(findings.cacheUsedOptions()).isFalse();
    }
```

In `jasper-app/src/test/java/dev/jasper/app/application/VmOptionsFeatureTest.java`, after `private int restarts;` add

```java
    private boolean trained;
```

after `private Path file() { return home.resolve("jasper.vmoptions"); }` add

```java
    private Path cacheFile() { return home.resolve("app").resolve("jasper.aot"); }
```

in `feature(VmOptionsLaunch launch, boolean image, Consumer<Runnable> ui)` replace the line
`var launcherImage = new LauncherImage(home.resolve("app"), List.of("-Xmx2048m", "-XX:+IgnoreUnrecognizedVMOptions"), List.of());`
with

```java
        var options = new ArrayList<>(List.of("-Xmx2048m", "-XX:+IgnoreUnrecognizedVMOptions"));
        if (trained) options.add("-XX:AOTCache=" + cacheFile());
        var launcherImage = new LauncherImage(home.resolve("app"), options, List.of());
```

and add this test before `restartNowThatCannotRestartSaysSo`:

```java
    @Test void theCacheSentenceAppearsOnlyWhenTheFailingCacheCheckUsedTheOptions() throws IOException {
        trained = true;
        String refused = "Error occurred during initialization of VM\nUnable to use AOT cache.\n";
        Files.writeString(file(), "-XX:+UseZGC\n");
        script = command -> command.contains("-XX:AOTMode=on") ? new VmOptionsCheck.Outcome(1, refused, false) : new VmOptionsCheck.Outcome(0, "", false);
        var feature = feature(managed(), true);
        feature.reload();
        assertThat(feature.hooks().findings().get()).hasValueSatisfying(notice ->
            assertThat(notice.text()).isEqualTo("These options turn off Jasper's start-up cache; it will start more slowly."));
        Files.writeString(file(), "-Xmx3q\n");
        script = command -> command.contains("-Xmx3q") ? new VmOptionsCheck.Outcome(1, "Invalid maximum heap size: -Xmx3q\n", false)
            : command.contains("-XX:AOTMode=on") ? new VmOptionsCheck.Outcome(1, refused, false) : new VmOptionsCheck.Outcome(0, "", false);
        feature.reload();
        assertThat(feature.hooks().findings().get()).as("the cache failed with the bundled options alone").hasValueSatisfying(notice ->
            assertThat(notice.text()).contains("Invalid maximum heap size: -Xmx3q").doesNotContain("start-up cache"));
    }
```

- [ ] **Step 2: Run them to see them fail**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.vmoptions.VmOptionsCheckTest' --tests 'dev.jasper.app.application.VmOptionsFeatureTest'`
Expected: FAIL to compile: `cannot find symbol` … `method cache()`, `method cacheUsedOptions()`,
`method checkWithCache(Parsed)` and `method cacheFatal(String)`.

- [ ] **Step 3: Write the implementation**

In `jasper-app/src/main/java/dev/jasper/app/vmoptions/VmOptionsCheck.java`, replace the class comment with:

```java
/**
 * Runs the bundled {@code java} with the user's options (spec sections 5.1 and 6.4): each applied line alone with
 * unrecognized options made fatal, then the line-up as launched, then, in a trained image, with the AOT cache required:
 * with that line-up when it starts, else with the bundled options alone, so a broken option is never blamed on the cache
 * (start-up recovery spec 6.3); a refused cache is explained by the JVM's own AOT error when it names the cause.
 * Blocking: run it on a worker, never on the EDT.
 */
```

Replace the `Findings` record's comment, header and `NONE` (keep its compact constructor) with:

```java
    /**
     * What the check found.
     *
     * @param unrecognized applied lines the JVM would ignore
     * @param fatal why the JVM would not start with the line-up, if it would not
     * @param cache why the JVM would not start with the trained AOT cache required, if it would not, in Java's words
     * @param cacheUsedOptions whether that cache check ran with the custom options; it runs with the bundled options alone
     *                         when there are none or they stop the JVM, and false when it did not run
     */
    public record Findings(List<String> unrecognized, Optional<Fatal> fatal, Optional<Fatal> cache, boolean cacheUsedOptions) {
        /** Nothing found, or nothing checked. */
        public static final Findings NONE = new Findings(List.of(), Optional.empty(), Optional.empty(), false);
```

Replace the whole `check(VmOptionsSyntax.Parsed parsed)` method (its comment included) with:

```java
    /**
     * Checks {@code parsed}'s applied lines; a corrupted file, or one without option lines, is not checked. A run over its
     * time limit ends the check with no findings at all (spec section 7).
     */
    public Findings check(VmOptionsSyntax.Parsed parsed) throws IOException { return check(parsed, false); }

    /**
     * {@link #check}, except that a trained image's cache is checked even when {@code parsed} applies no lines: after a
     * start that skipped the cache because it failed at launch (start-up recovery spec 6.4).
     */
    public Findings checkWithCache(VmOptionsSyntax.Parsed parsed) throws IOException { return check(parsed, true); }

    private Findings check(VmOptionsSyntax.Parsed parsed, boolean cacheAlways) throws IOException {
        boolean custom = !parsed.corrupted() && !parsed.lines().isEmpty();
        if (!custom && !(cacheAlways && image.hasAotCache())) return Findings.NONE;
        var unrecognized = new ArrayList<String>();
        List<String> options = image.javaOptions();
        Optional<Fatal> fatal = Optional.empty();
        if (custom) {
            for (String line : parsed.lines()) {
                Outcome alone = run(List.of(java.toString(), "-XX:-IgnoreUnrecognizedVMOptions", line, "-version"));
                if (alone.timedOut()) return Findings.NONE;
                if (alone.exit() != 0 && unrecognized(alone.output())) unrecognized.add(line);
            }
            options = VmOptionsSyntax.merge(image.javaOptions(), parsed.lines());
            Outcome merged = run(command(options, List.of("-version")));
            if (merged.timedOut()) return Findings.NONE;
            if (merged.exit() != 0) fatal = Optional.of(fatal(merged.output()));
        }
        if (!image.hasAotCache()) return new Findings(unrecognized, fatal, Optional.empty(), false);
        boolean usedOptions = custom && fatal.isEmpty();
        Outcome cache = run(command(usedOptions ? options : image.javaOptions(),
            List.of("-XX:AOTMode=on", "-cp", String.join(File.pathSeparator, image.classpath()), "-version")));
        if (cache.timedOut()) return Findings.NONE;
        return new Findings(unrecognized, fatal, cache.exit() != 0 ? Optional.of(cacheFatal(cache.output())) : Optional.empty(), usedOptions);
    }
```

After `fatal(String output)` (before `private static boolean unrecognized(String output)`) add:

```java
    /**
     * Why the JVM refused the AOT cache, from its output: the first line that starts with {@code [}, contains
     * {@code ][error][aot} and whose text after its last {@code "] "} names an {@code Exception} or an {@code Error:},
     * as that text; otherwise {@link #fatal}'s line. So the sealing failure of 2026-10-14 reads as its
     * {@code SecurityException} rather than HotSpot's generic last line.
     */
    static Fatal cacheFatal(String output) {
        Fatal fallback = fatal(output);
        for (String line : output.lines().toList()) {
            int end = line.lastIndexOf("] ");
            if (!line.startsWith("[") || !line.contains("][error][aot") || end < 0) continue;
            String text = line.substring(end + 2).strip();
            if (text.contains("Exception") || text.contains("Error:")) return new Fatal(text, fallback.recoverable());
        }
        return fallback;
    }
```

In `jasper-app/src/main/java/dev/jasper/app/application/VmOptionsFeature.java` (`findingsNotice`), replace

```java
        if (findings.cacheRejected()) sentences.add("These options turn off Jasper's start-up cache; it will start more slowly.");
```

with

```java
        // Start-up recovery spec 6.3: only a failing cache check that ran with the options blames them.
        if (findings.cache().isPresent() && findings.cacheUsedOptions()) sentences.add("These options turn off Jasper's start-up cache; it will start more slowly.");
```

- [ ] **Step 4: Run the tests to see them pass**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.vmoptions.*' --tests 'dev.jasper.app.application.VmOptionsFeatureTest' --tests 'dev.jasper.app.settingsui.*'`
Expected: BUILD SUCCESSFUL (`theRealJvmRefusesACacheItCannotUse` starts one short `java -version`).

- [ ] **Step 5: Commit**

```bash
git add jasper-app/src/main/java/dev/jasper/app/vmoptions/VmOptionsCheck.java \
  jasper-app/src/main/java/dev/jasper/app/application/VmOptionsFeature.java \
  jasper-app/src/test/java/dev/jasper/app/vmoptions/VmOptionsCheckTest.java \
  jasper-app/src/test/java/dev/jasper/app/application/VmOptionsFeatureTest.java
git commit -F - <<'EOF'
Attribute the AOT cache check to the cache or to the options

The cache check now runs with the custom options only when they start
the JVM, and otherwise with the bundled options alone, so a broken
option is never blamed on the cache. Findings carries Java's message for
a failing cache, the JVM's own AOT error when it names the cause, and
whether that check used the custom options; System Settings blames the
options only then. checkWithCache also checks a trained image's cache
when there are no custom options.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

---

### Task 5: After a recovered start: remember the cache and say so once

**Files:**
- Modify: `jasper-app/src/main/java/dev/jasper/app/application/VmOptionsFeature.java` (imports, class comment, a
  constant, `startupSettled`, new `remember` and `logSkippedCache`, the `check` helper and its call in `reload`)
- Modify: `jasper-app/src/main/java/dev/jasper/app/application/VmOptionsNotices.java` (class comment, `cacheSkipped`)
- Test: `jasper-app/src/test/java/dev/jasper/app/application/VmOptionsFeatureTest.java`

**Interfaces:**
- Consumes: `AotCacheRecord.capture`, `AotCacheRecord.read`, `AotCacheRecord.write`, `AotCacheRecord.HEADER` (Task 1);
  `VmOptionsLaunch.aotCacheSkipped()` and `aotCacheRecord()` (Task 3); `VmOptionsCheck.checkWithCache`,
  `Findings.cache()`, `Findings.cacheUsedOptions()` (Task 4); `VmOptionsLaunch.appliedDigest(Parsed)`,
  `VmOptionsSyntax.merge`; the test's `trained` and `cacheFile()` (Task 4).
- Produces: `VmOptionsNotices.cacheSkipped(String message)` (EDT); `VmOptionsFeature.startupSettled()` as spec 6.4
  says, with private `remember(Optional<LauncherImage>, VmOptionsSyntax.Parsed, VmOptionsCheck.Findings)` returning
  Java's message to announce, and the private `check(Optional<LauncherImage>, VmOptionsFile.Contents, boolean withCache)`.
- Log lines (all plain strings, logger `dev.jasper.app.application.VmOptionsFeature`):
  - INFO `The start-up cache made Java fail at launch, but its check finds no failure now; the next start tries it again`
  - WARNING `The start-up cache made Java fail at launch; Jasper skips it until a new build replaces it`
  - INFO `The start-up cache made Java fail at launch again; it is already remembered, so Jasper does not say so again`
  - WARNING `Could not remember the start-up cache that made Java fail at launch; the next start tries it again` (with
    the exception)
  - WARNING `The start-up cache made Java fail at launch, and the launcher named no file to remember it in`
  - INFO `Jasper started without its start-up cache, which made Java fail at an earlier launch` (`remembered`)
  - INFO `Jasper started without its start-up cache because JASPER_AOT_CACHE_SKIP is set` (any other reason)
  - INFO `Jasper started without the custom VM options after failed launches, but their check finds no failure now`

- [ ] **Step 1: Write the failing tests**

In `jasper-app/src/test/java/dev/jasper/app/application/VmOptionsFeatureTest.java`:

Add the imports `dev.jasper.app.notifications.Severity`, `dev.jasper.app.vmoptions.AotCacheRecord`,
`java.nio.file.attribute.FileTime` and `java.time.Instant`, so the import block reads:

```java
import dev.jasper.app.notifications.Delivery;
import dev.jasper.app.notifications.DisplayMode;
import dev.jasper.app.notifications.NotificationAction;
import dev.jasper.app.notifications.NotificationCenter;
import dev.jasper.app.notifications.Severity;
import dev.jasper.app.testsupport.EdtTestExtension;
import dev.jasper.app.vmoptions.AotCacheRecord;
import dev.jasper.app.vmoptions.LauncherImage;
import dev.jasper.app.vmoptions.VmOptionsCheck;
import dev.jasper.app.vmoptions.VmOptionsFile;
import dev.jasper.app.vmoptions.VmOptionsLaunch;
import dev.jasper.app.vmoptions.VmOptionsSyntax;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.function.Consumer;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
```

Make the center replaceable (each simulated start gets a fresh one, because the center coalesces a repeat within 60
seconds and would hide a second post): replace

```java
    private final NotificationCenter center = new NotificationCenter(Runnable::run, Clock.systemUTC(), () -> Delivery.Presence.FOCUSED);
```

with

```java
    private NotificationCenter center = new NotificationCenter(Runnable::run, Clock.systemUTC(), () -> Delivery.Presence.FOCUSED);
```

After the `titles()` helper add:

```java
    /** What the installed app printed on 2026-10-14 (start-up recovery spec section 1). */
    private static final String SEALED = "[0.142s][error][aot               ] java.lang.SecurityException: sealing violation: "
        + "can't seal package com.jetbrains: already defined\nError occurred during initialization of VM\n"
        + "Unexpected exception when loading aot-linked classes.\n";
    private static final String CACHE_TEXT = "The start-up cache made Java fail at launch, so Jasper starts more slowly until a new "
        + "Jasper build replaces it. Java reported: java.lang.SecurityException: sealing violation: can't seal package com.jetbrains: "
        + "already defined";

    private Path record() { return home.resolve("jasper-aot.failed"); }

    /** A start the launcher made without the cache for {@code reason}, after skipping the options for {@code optionsSkipped}. */
    private VmOptionsLaunch withoutCache(String reason, Optional<String> optionsSkipped, Optional<String> digest) {
        return new VmOptionsLaunch(Optional.of(file()), digest, false, optionsSkipped, Optional.of(reason), Optional.of(record()));
    }

    /** A trained image whose cache is on disk: 5 bytes, modified at 2026-10-14T08:24:05.900Z. */
    private void installCache() throws IOException {
        trained = true;
        Files.createDirectories(cacheFile().getParent());
        Files.writeString(cacheFile(), "cache");
        Files.setLastModifiedTime(cacheFile(), FileTime.from(Instant.parse("2026-10-14T08:24:05.900Z")));
    }

    private String recorded(long size, String options) {
        return AotCacheRecord.HEADER + "\ncache=" + cacheFile() + "\nsize=" + size + "\nmodified=1791966245\noptions=" + options + "\n";
    }

    private void newStart() { center = new NotificationCenter(Runnable::run, Clock.systemUTC(), () -> Delivery.Presence.FOCUSED); }

    /** Runs {@code action} and returns what VmOptionsFeature logged meanwhile. */
    private static List<java.util.logging.LogRecord> logged(Runnable action) {
        var logger = java.util.logging.Logger.getLogger(VmOptionsFeature.class.getName());
        var previousLevel = logger.getLevel();
        boolean previousParents = logger.getUseParentHandlers();
        var records = new ArrayList<java.util.logging.LogRecord>();
        var handler = new java.util.logging.Handler() {
            @Override public void publish(java.util.logging.LogRecord record) { records.add(record); }
            @Override public void flush() { }
            @Override public void close() { }
        };
        handler.setLevel(java.util.logging.Level.ALL);
        logger.addHandler(handler);
        logger.setLevel(java.util.logging.Level.ALL);
        logger.setUseParentHandlers(false);
        try {
            action.run();
        } finally {
            logger.removeHandler(handler);
            logger.setLevel(previousLevel);
            logger.setUseParentHandlers(previousParents);
        }
        return records;
    }
```

Add these tests before `restartNowThatCannotRestartSaysSo`:

```java
    @Test void aCacheThatMadeJavaFailIsRememberedAndAnnouncedOncePerRecord() throws IOException {
        installCache();
        script = command -> command.contains("-XX:AOTMode=on") ? new VmOptionsCheck.Outcome(1, SEALED, false) : new VmOptionsCheck.Outcome(0, "", false);
        feature(withoutCache("launch-failed", Optional.empty(), Optional.empty()), true).startupSettled();
        assertThat(Files.readString(record())).isEqualTo(recorded(5, ""));
        assertThat(center.history()).singleElement().satisfies(entry -> {
            assertThat(entry.request().group()).isEqualTo("jasper.vmoptions");
            assertThat(entry.request().severity()).isEqualTo(Severity.WARNING);
            assertThat(entry.request().title()).isEqualTo("Jasper started without its start-up cache");
            assertThat(entry.request().content()).isEqualTo(CACHE_TEXT);
            assertThat(entry.request().actions()).isEmpty();
        });
        newStart();
        feature(withoutCache("launch-failed", Optional.empty(), Optional.empty()), true).startupSettled();
        assertThat(center.history()).as("the same cache failed again, say because the launcher did not match the record").isEmpty();
        assertThat(Files.readString(record())).isEqualTo(recorded(5, ""));
        newStart();
        Files.writeString(cacheFile(), "cache2");
        Files.setLastModifiedTime(cacheFile(), FileTime.from(Instant.parse("2026-10-14T08:24:05Z")));
        feature(withoutCache("launch-failed", Optional.empty(), Optional.empty()), true).startupSettled();
        assertThat(Files.readString(record())).as("a new build's cache fails too: a new record").isEqualTo(recorded(6, ""));
        assertThat(titles()).containsExactly("Jasper started without its start-up cache");
    }

    @Test void aCacheThatFailsWithTheCustomOptionsIsRememberedWithTheirDigest() throws IOException {
        installCache();
        Files.writeString(file(), "-Xmx1g\n");
        String digest = VmOptionsSyntax.digest(List.of("-Xmx1g"));
        script = command -> command.contains("-XX:AOTMode=on") && command.contains("-Xmx1g")
            ? new VmOptionsCheck.Outcome(1, SEALED, false) : new VmOptionsCheck.Outcome(0, "", false);
        feature(withoutCache("launch-failed", Optional.empty(), Optional.of(digest)), true).startupSettled();
        assertThat(Files.readString(record())).isEqualTo(recorded(5, digest));
        assertThat(titles()).containsExactly("Jasper started without its start-up cache");
    }

    @Test void aCacheThatPassesOrCannotBeCheckedIsNeitherRememberedNorAnnounced() throws IOException {
        installCache();
        var passes = logged(() -> feature(withoutCache("launch-failed", Optional.empty(), Optional.empty()), true).startupSettled());
        assertThat(passes).singleElement().satisfies(entry -> {
            assertThat(entry.getLevel()).isEqualTo(java.util.logging.Level.INFO);
            assertThat(entry.getMessage()).isEqualTo("The start-up cache made Java fail at launch, but its check finds no failure now; the next start tries it again");
        });
        script = command -> new VmOptionsCheck.Outcome(-1, "", true);
        feature(withoutCache("launch-failed", Optional.empty(), Optional.empty()), true).startupSettled();
        feature(withoutCache("launch-failed", Optional.empty(), Optional.empty()), false).startupSettled();
        assertThat(record()).as("timed out, and no Jasper.cfg found").doesNotExist();
        assertThat(center.history()).isEmpty();
    }

    @Test void aRememberedOrHandSetSkipIsOneLogLine() throws IOException {
        installCache();
        var commands = new ArrayList<List<String>>();
        script = command -> { commands.add(command); return new VmOptionsCheck.Outcome(1, SEALED, false); };
        var lines = logged(() -> {
            for (String reason : List.of("remembered", "environment", "1")) feature(withoutCache(reason, Optional.empty(), Optional.empty()), true).startupSettled();
        });
        assertThat(lines).extracting(java.util.logging.LogRecord::getMessage).containsExactly(
            "Jasper started without its start-up cache, which made Java fail at an earlier launch",
            "Jasper started without its start-up cache because JASPER_AOT_CACHE_SKIP is set",
            "Jasper started without its start-up cache because JASPER_AOT_CACHE_SKIP is set");
        assertThat(lines).allSatisfy(entry -> assertThat(entry.getLevel()).isEqualTo(java.util.logging.Level.INFO));
        assertThat(commands).as("nothing is checked").isEmpty();
        assertThat(record()).doesNotExist();
        assertThat(center.history()).isEmpty();
    }

    @Test void aRecordThatCannotBeWrittenIsLoggedAndTheNoticeStillAppears() throws IOException {
        installCache();
        Files.writeString(home.resolve("blocker"), "not a folder");
        script = command -> command.contains("-XX:AOTMode=on") ? new VmOptionsCheck.Outcome(1, SEALED, false) : new VmOptionsCheck.Outcome(0, "", false);
        var blocked = new VmOptionsLaunch(Optional.of(file()), Optional.empty(), false, Optional.empty(), Optional.of("launch-failed"),
            Optional.of(home.resolve("blocker/jasper-aot.failed")));
        var lines = logged(() -> feature(blocked, true).startupSettled());
        assertThat(titles()).containsExactly("Jasper started without its start-up cache");
        assertThat(lines).anySatisfy(entry -> {
            assertThat(entry.getLevel()).isEqualTo(java.util.logging.Level.WARNING);
            assertThat(entry.getMessage()).isEqualTo("Could not remember the start-up cache that made Java fail at launch; the next start tries it again");
            assertThat(entry.getThrown()).isInstanceOf(IOException.class);
        });
        newStart();
        var nowhere = new VmOptionsLaunch(Optional.of(file()), Optional.empty(), false, Optional.empty(), Optional.of("launch-failed"), Optional.empty());
        feature(nowhere, true).startupSettled();
        assertThat(titles()).as("no record file named").containsExactly("Jasper started without its start-up cache");
        assertThat(record()).doesNotExist();
    }

    @Test void whenBothWereSkippedEachNoticeAppearsOnlyWhenItsOwnCheckFails() throws IOException {
        installCache();
        var both = withoutCache("launch-failed", Optional.of("launch-failed"), Optional.empty());
        Files.writeString(file(), "-Xms4g\n");
        String unrecoverable = "Error occurred during initialization of VM\nInitial heap size set to a larger value than the maximum heap size\n";
        script = command -> command.contains("-Xms4g") && !command.contains("-XX:-IgnoreUnrecognizedVMOptions")
            ? new VmOptionsCheck.Outcome(1, unrecoverable, false) : new VmOptionsCheck.Outcome(0, "", false);
        feature(both, true).startupSettled();
        assertThat(titles()).as("the options stop the JVM; the cache passes with the bundled options alone")
            .containsExactly("Jasper started without your custom VM options");
        assertThat(record()).doesNotExist();
        newStart();
        Files.writeString(file(), "-Xmx1g\n");
        script = command -> command.contains("-XX:AOTMode=on") ? new VmOptionsCheck.Outcome(1, SEALED, false) : new VmOptionsCheck.Outcome(0, "", false);
        feature(both, true).startupSettled();
        assertThat(titles()).as("the options start the JVM; the cache fails with them").containsExactly("Jasper started without its start-up cache");
        assertThat(Files.readString(record())).as("the next start carries their digest").isEqualTo(recorded(5, VmOptionsSyntax.digest(List.of("-Xmx1g"))));
        newStart();
        Files.delete(record());
        script = command -> new VmOptionsCheck.Outcome(0, "", false);
        feature(both, true).startupSettled();
        assertThat(center.history()).as("both pass now").isEmpty();
        assertThat(record()).doesNotExist();
    }
```

- [ ] **Step 2: Run them to see them fail**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.application.VmOptionsFeatureTest'`
Expected: FAIL, 6 tests: `aCacheThatMadeJavaFailIsRememberedAndAnnouncedOncePerRecord` and
`aCacheThatFailsWithTheCustomOptionsIsRememberedWithTheirDigest` with `NoSuchFileException: …/jasper-aot.failed`;
`aCacheThatPassesOrCannotBeCheckedIsNeitherRememberedNorAnnounced`, `aRememberedOrHandSetSkipIsOneLogLine`,
`aRecordThatCannotBeWrittenIsLoggedAndTheNoticeStillAppears` and `whenBothWereSkippedEachNoticeAppearsOnlyWhenItsOwnCheckFails`
with assertion errors. The other 21 pass.

- [ ] **Step 3: Write the notification**

In `jasper-app/src/main/java/dev/jasper/app/application/VmOptionsNotices.java`, replace the class comment with:

```java
/**
 * Custom VM options notifications (spec sections 4.3, 5.1, 5.4 and 5.5) in their own group: a fallback start, a start
 * without the start-up cache (start-up recovery spec 6.4), a corrupted file, a pending restart, failures and low memory.
 * The instance methods run on the EDT only; {@link #lowMemory} builds a request on any thread.
 */
```

and before `/** The launcher ignored {@code file} because it is not valid text. */` add:

```java
    /**
     * The launcher started Jasper once more without its start-up cache, which made Java fail at launch, and Jasper now
     * remembers it; {@code message} is Java's own.
     */
    void cacheSkipped(String message) {
        center.post(NotificationRequest.of(GROUP.id(), Severity.WARNING, "Jasper started without its start-up cache")
            .withContent("The start-up cache made Java fail at launch, so Jasper starts more slowly until a new Jasper build "
                + "replaces it. Java reported: " + message));
    }
```

- [ ] **Step 4: Write the after-start flow**

In `jasper-app/src/main/java/dev/jasper/app/application/VmOptionsFeature.java`:

Add `import dev.jasper.app.vmoptions.AotCacheRecord;` after `import dev.jasper.app.settingsui.VmOptionsHooks;`.

In the class comment, replace `Help and palette actions' handlers, and the notices after start-up. File I/O and the check run on {@code worker};`
with:

```java
 * Help and palette actions' handlers, and the notices after start-up, including a start without the start-up cache
 * (start-up recovery spec 6.4). File I/O and the check run on {@code worker};
```

After `private static final List<String> KEYWORDS = …;` add:

```java
    private static final String LAUNCH_FAILED = "launch-failed";
```

Replace the whole `startupSettled()` method (its one-line comment included) with:

```java
    /**
     * Once, after start-up settles: reads the file and announces a fallback start or a corrupted file; after a start
     * without the start-up cache (start-up recovery spec 6.4) checks the cache, remembers it when it fails and says so.
     * When the launcher skipped both after failed launches, each notice appears only when its own check fails. EDT.
     */
    void startupSettled() {
        if (started) return;
        started = true;
        boolean managed = launch.launcherManaged();
        Optional<String> cacheSkipped = launch.aotCacheSkipped();
        if (!managed && cacheSkipped.isEmpty()) return;
        Optional<Path> file = launch.file();
        boolean fallback = launch.skipped().filter(LAUNCH_FAILED::equals).isPresent();
        boolean cacheFailed = cacheSkipped.filter(LAUNCH_FAILED::equals).isPresent();
        boolean corrupted = launch.corrupted();
        worker.execute(() -> {
            VmOptionsFile.Contents read = file.map(VmOptionsFeature::read).orElse(VmOptionsFile.Contents.ABSENT);
            Optional<LauncherImage> located = images.get();
            VmOptionsCheck.Findings found = fallback || cacheFailed ? check(located, read, cacheFailed) : VmOptionsCheck.Findings.NONE;
            Optional<String> message = found.fatal().map(VmOptionsCheck.Fatal::message);
            boolean announceFallback = fallback && (!cacheFailed || message.isPresent());
            if (fallback && !announceFallback) {
                LOG.log(System.Logger.Level.INFO, "Jasper started without the custom VM options after failed launches, but their check finds no failure now");
            }
            Optional<String> cacheMessage = cacheFailed ? remember(located, read.parsed(), found) : Optional.empty();
            if (!cacheFailed) cacheSkipped.ifPresent(VmOptionsFeature::logSkippedCache);
            ui.accept(() -> {
                if (managed && atStart == null) {
                    atStart = read;
                    contents = read;
                    located.ifPresent(image -> bundledMiB = image.bundledMaxHeapMiB());
                }
                if (announceFallback) notices.fallbackStart(message);
                cacheMessage.ifPresent(notices::cacheSkipped);
                if (corrupted) file.ifPresent(notices::corrupted);
            });
        });
    }

    /**
     * After a start that skipped the cache because it made Java fail at launch (start-up recovery spec 6.4). A failing
     * cache check writes the record the launcher matches, with the options' digest when the check ran with them, and
     * returns Java's message to announce; a record already holding the same values was announced before, so it is not
     * written or announced again. A passing check writes nothing, so the next start tries the cache. Worker.
     */
    private Optional<String> remember(Optional<LauncherImage> located, VmOptionsSyntax.Parsed parsed, VmOptionsCheck.Findings found) {
        if (found.cache().isEmpty() || located.isEmpty()) {
            LOG.log(System.Logger.Level.INFO, "The start-up cache made Java fail at launch, but its check finds no failure now; the next start tries it again");
            return Optional.empty();
        }
        String message = found.cache().get().message();
        Optional<Path> target = launch.aotCacheRecord();
        if (target.isEmpty()) {
            LOG.log(System.Logger.Level.WARNING, "The start-up cache made Java fail at launch, and the launcher named no file to remember it in");
            return Optional.of(message);
        }
        List<String> options = found.cacheUsedOptions() ? VmOptionsSyntax.merge(located.get().javaOptions(), parsed.lines()) : located.get().javaOptions();
        Optional<String> digest = found.cacheUsedOptions() ? VmOptionsLaunch.appliedDigest(parsed) : Optional.empty();
        try {
            AotCacheRecord record = AotCacheRecord.capture(options, digest);
            if (AotCacheRecord.read(target.get()).filter(record::equals).isPresent()) {
                LOG.log(System.Logger.Level.INFO, "The start-up cache made Java fail at launch again; it is already remembered, so Jasper does not say so again");
                return Optional.empty();
            }
            record.write(target.get());
            LOG.log(System.Logger.Level.WARNING, "The start-up cache made Java fail at launch; Jasper skips it until a new build replaces it");
        } catch (IOException | RuntimeException failure) {
            LOG.log(System.Logger.Level.WARNING, "Could not remember the start-up cache that made Java fail at launch; the next start tries it again", failure);
        }
        return Optional.of(message);
    }

    /** One line for a start without the cache that needs no check: remembered, or skipped by hand. */
    private static void logSkippedCache(String reason) {
        LOG.log(System.Logger.Level.INFO, "remembered".equals(reason)
            ? "Jasper started without its start-up cache, which made Java fail at an earlier launch"
            : "Jasper started without its start-up cache because JASPER_AOT_CACHE_SKIP is set");
    }
```

Replace the `check` helper (its comment included):

```java
    /** The check's findings; a check that cannot run finds nothing, never blocks saving, and is logged (spec section 7). */
    private VmOptionsCheck.Findings check(Optional<LauncherImage> located, VmOptionsFile.Contents read) {
        if (located.isEmpty()) return VmOptionsCheck.Findings.NONE;
        try {
            return checks.apply(located.get()).check(read.parsed());
```

with

```java
    /**
     * The check's findings, with the cache checked even without custom options when {@code withCache}; a check that cannot
     * run finds nothing, never blocks saving, and is logged (spec section 7).
     */
    private VmOptionsCheck.Findings check(Optional<LauncherImage> located, VmOptionsFile.Contents read, boolean withCache) {
        if (located.isEmpty()) return VmOptionsCheck.Findings.NONE;
        try {
            VmOptionsCheck check = checks.apply(located.get());
            return withCache ? check.checkWithCache(read.parsed()) : check.check(read.parsed());
```

(its `catch` stays), and in `reload()` replace `VmOptionsCheck.Findings found = check(located, read);` with:

```java
            VmOptionsCheck.Findings found = check(located, read, false);
```

- [ ] **Step 5: Run the tests to see them pass**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.application.*' --tests 'dev.jasper.app.vmoptions.*' --tests 'dev.jasper.app.startup.*' verifyApplicationArchitecture`
Expected: BUILD SUCCESSFUL (`VmOptionsFeatureTest`: 27 tests; `withoutJaspersLauncherTheSectionIsReadOnly` still posts
nothing, because a start without Jasper's launcher has neither property).

- [ ] **Step 6: Commit**

```bash
git add jasper-app/src/main/java/dev/jasper/app/application/VmOptionsFeature.java \
  jasper-app/src/main/java/dev/jasper/app/application/VmOptionsNotices.java \
  jasper-app/src/test/java/dev/jasper/app/application/VmOptionsFeatureTest.java
git commit -F - <<'EOF'
Remember a start-up cache that made Java fail, and say so once

After a start the launcher made without the AOT cache because it failed
at launch, Jasper checks the cache; a failing check writes the record the
launcher matches and posts "Jasper started without its start-up cache"
in Memory & VM options, once per record. A passing check writes nothing,
a remembered or hand-set skip is one log line, and when the options were
skipped too each notification appears only when its own check fails.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

---

### Task 6: Documentation, full check and the user's checks

**Files:**
- Modify: `docs/configuration.md` (the "Custom VM options" section)
- Modify: `docs/diagnostics.md` ("Start-up timing" and "Start-up cache (packaged macOS app)")
- Modify: `docs/notifications.md` (the "Memory & VM options" row)
- Modify: `docs/app-architecture.md` (the `vmoptions` row)
- Modify: `docs/app-maintenance.md` (the "Bundled JVM defaults." bullet)
- Modify: `docs/STATUS.md` (new entry at the top)
- Modify: `docs/superpowers/specs/2026-10-14-jasper-startup-recovery-design.md` (status paragraph)
- Modify: `docs/superpowers/plans/2026-10-14-jasper-startup-recovery-3-application.md` (status line)

- [ ] **Step 1: The skip variables for users**

In `docs/configuration.md`, section "Custom VM options", replace the paragraph that starts `If the JVM rejects the
options` with the text below. If plan 2 already rewrote that paragraph, keep its account of the recovery and add only
the `JASPER_AOT_CACHE_SKIP` clause next to `JASPER_VM_OPTIONS_SKIP`.

```markdown
If the JVM cannot start with the options (a malformed value such as `-Xmx3q`, conflicting garbage collectors, `-Xms`
above `-Xmx`, a missing agent), Jasper starts again without them. Setting `JASPER_VM_OPTIONS_SKIP=1` in the
environment starts Jasper without the file so you can fix it, and `JASPER_AOT_CACHE_SKIP=1` starts it without its
[start-up cache](diagnostics.md#start-up-cache-packaged-macos-app). `./gradlew :jasper-app:run` does not read the file.
```

In the same section, replace `When Jasper had to start without your options, or ignored the file, it says` with:

```markdown
When Jasper had to start without your options or its start-up cache, or ignored the file, it says
```

- [ ] **Step 2: The start-up line and the record**

In `docs/diagnostics.md`, section "Start-up timing", the paragraph after the example line has the sentence about
`AOT cache stale`, which ends `costs more than` / `having none.` across a line break. Insert, between `having none.`
and `Each window a running Jasper opens logs`, re-wrapping the paragraph at 120 columns:

```markdown
`AOT cache skipped: <reason>` means Jasper's launcher started the JVM without it: `launch-failed` (it made Java fail at
this launch), `remembered` (it did at an earlier one) or `environment` (`JASPER_AOT_CACHE_SKIP` is set).
```

At the end of the section "Start-up cache (packaged macOS app)", after its last paragraph (the one ending with the
link to `packaging.md#keeping-the-cache-valid`), add a blank line and:

```markdown
If the cache makes Java fail at launch, Jasper starts again without it, says so once in a **Memory & VM options**
notification and writes `jasper-aot.failed` in Jasper's home (next to `config.toml`). Jasper starts without the cache
while that file matches it, until a new Jasper build replaces the cache; delete the file to try the cache again.
Setting `JASPER_AOT_CACHE_SKIP=1` in the environment also starts Jasper without the cache.
```

- [ ] **Step 3: The notification group, the package and the maintenance recipe**

In `docs/notifications.md`, in the `Memory & VM options` row, replace `a start without your custom VM options,` with
`a start without your custom VM options or without the start-up cache,`.

In `docs/app-architecture.md`, in the `vmoptions` row, after ``the check that runs the bundled `java`,`` insert
`the record of a start-up cache that failed at launch,` (with a space before it).

In `docs/app-maintenance.md`, in the "Bundled JVM defaults." bullet, after the sentence that ends `which both test
suites read.` insert:

```markdown
  `AotCacheRecord` reads the record of a failed start-up cache (`jasper-aot.failed`) with the launcher's rules: change
  both together and add a vector to `packaging/launcher/test/aot-record-vectors.txt`.
```

- [ ] **Step 4: Run the full check**

Run: `./gradlew check --continue`
Expected: BUILD SUCCESSFUL. Read the totals from the XML (AGENTS.md):

```bash
python3 - <<'PY'
import pathlib, xml.etree.ElementTree as ET
t = f = e = s = 0
for pattern in ('*/build/test-results/test/*.xml', 'plugins/*/build/test-results/test/*.xml'):
    for p in pathlib.Path('.').glob(pattern):
        r = ET.parse(p).getroot()
        t += int(r.get('tests', 0)); f += int(r.get('failures', 0)); e += int(r.get('errors', 0)); s += int(r.get('skipped', 0))
print(f"{t} tests, {f} failures, {e} errors, {s} skipped")
PY
```

Check source hygiene with the AGENTS.md script (all Java sources):

```bash
python3 - <<'PY'
import pathlib, sys
for name in sys.argv[1:] or [str(p) for root in ("jasper-terminal/src", "jasper-app/src", "jasper-buddy/src", "jasper-sdk/src", "jasper-sdk-testkit/src", "plugins") for p in pathlib.Path(root).rglob("*.java")]:
    text = pathlib.Path(name).read_text(encoding="utf-8")
    bad = sum(1 for c in text if 0xD800 <= ord(c) <= 0xDFFF or 0xE000 <= ord(c) <= 0xF8FF or (ord(c) < 0x20 and c not in "\n\t\r") or ord(c) == 0x7f)
    if bad: print(name, "bad chars:", bad)
PY
```

Expected: no output.

- [ ] **Step 5: Record the status**

Add at the top of `docs/STATUS.md`, below the `# Jasper — Status and Handoff` heading, with Step 4's totals and the
date you finish:

```markdown
**Start-up recovery plan 3 (the application) complete — YYYY-MM-DD** (`claude/startup-recovery`, worktree
`.claude/worktrees/startup-recovery`; spec `docs/superpowers/specs/2026-10-14-jasper-startup-recovery-design.md`).
After the launcher recovers a start without the AOT cache, the start-up line says why (`AOT cache skipped:
launch-failed`, `remembered` or `environment`); Jasper checks the cache on its VM options worker, and when the check
fails writes `<home>/jasper-aot.failed` (the path the launcher names in `jasper.aotCacheRecord`) and posts "Jasper
started without its start-up cache" in Memory & VM options, once per record. `VmOptionsCheck` runs its cache check
with the custom options only when they start the JVM, so a broken option is never blamed on the cache, and System
Settings' "These options turn off Jasper's start-up cache" appears only then. New JDK-only `AotCacheRecord`, checked
against the launcher's `aot-record-vectors.txt`; matching compares the cache file's size and time and the options,
not its path. The notification quotes the JVM's own AOT error (for the 2026-10-14 failure, the `SecurityException`).
Deviations: the eleven planned ones in the plan (among them one notification per record, and a hand-set skip reading
`environment` in the log).
`./gradlew check --continue`: **<T> tests, <F> failures, <E> errors, <S> skipped**. No GUI launched. GUI checks for
the user are in the plan's hand-off section.
```

Replace `YYYY-MM-DD`, `<T>`, `<F>`, `<E>` and `<S>` before committing; the entry must not contain placeholders.

- [ ] **Step 6: Mark the spec and this plan**

In `docs/superpowers/specs/2026-10-14-jasper-startup-recovery-design.md`, append to the status paragraph (the one that
starts `Status: **Approved on 2026-10-14**`) the sentence `Plan 3 (application) complete on YYYY-MM-DD.` with the same
date. In this plan, replace the status line with:

```markdown
Status: **Complete on YYYY-MM-DD** on `claude/startup-recovery` (spec approved by the user on 2026-10-14).
```

(again with the date; add any deviation made during execution, such as a Task 1 vectors alignment, after it).

- [ ] **Step 7: Run the documentation test once more and commit**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.documentation.AppDocumentationTest'`
Expected: BUILD SUCCESSFUL (the new `diagnostics.md#start-up-cache-packaged-macos-app` link resolves).

```bash
git add docs/configuration.md docs/diagnostics.md docs/notifications.md docs/app-architecture.md docs/app-maintenance.md \
  docs/STATUS.md docs/superpowers/specs/2026-10-14-jasper-startup-recovery-design.md \
  docs/superpowers/plans/2026-10-14-jasper-startup-recovery-3-application.md
git commit -F - <<'EOF'
Document starting without the start-up cache

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

- [ ] **Step 8: Hand the GUI checks to the user**

Do not run `packageDist` or launch Jasper. Report the branch, the commits, the totals and the checks below; ask before
any `packageDist` run (memory `jasper-gui-launch-per-batch`).

## Hand-off checks for the user

Spec section 9's GUI checks, after `./gradlew :jasper-app:packageDist` and installing the DMG (quit Jasper fully first):

1. Move the repository's `jasper-app/build/packaging/image` aside and start Jasper: the start-up line in the log says
   `AOT cache used`, and no notification appears.
2. Add `-Xms4g` to `jasper.vmoptions` and start Jasper: "Jasper started without your custom VM options" appears, the
   start-up line says `AOT cache skipped: launch-failed`, no "Jasper started without its start-up cache" notification,
   and no `jasper-aot.failed` in Jasper's home. Remove the line and start again: `AOT cache used`.
3. From a terminal, `JASPER_AOT_CACHE_SKIP=1 /Applications/Jasper.app/Contents/MacOS/Jasper`: the start-up line says
   `AOT cache skipped: environment`, no notification.
