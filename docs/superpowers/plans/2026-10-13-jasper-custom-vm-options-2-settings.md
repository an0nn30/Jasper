# Custom VM Options, Plan 2: Settings and Actions — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

Status: **Complete on 2026-10-13.** Written 2026-10-13 on `claude/custom-vm-options-settings` (from `main` at `3bf81d1c`, which
holds plan 1).

Deviations beyond the seven planned ones, made in review: (R2) the plan's own docs text spells out the
`custom-vm-options` link in words, because `AppDocumentationTest` resolves links in plan files; (R3)
`VmOptionsFile.withMaxHeap` drops a leading byte order mark so a first-line `-Xmx` is still found; (R4)
`VmOptionsCheck.runProcess` throws `InterruptedIOException` on an interrupt instead of reporting a timeout; (R5) saving
the heap reads the file back so the new value shows at once; (R6) saving never rewrites a corrupted file.

**Goal:** In the installed app, System Settings gets a Java Virtual Machine section (heap limit, Edit Custom VM
Options…, the options' check, a restart banner), Help and the palette get the two IntelliJ actions, and Jasper says
when it had to start without the user's options.

**Architecture:** A new JDK-only package `dev.jasper.app.vmoptions` mirrors the launcher's reading of
`jasper.vmoptions`, edits it IntelliJ's way, reads what this JVM was launched with and the bundled `Jasper.cfg`, and
runs the bundled `java` to check the options. `SchemaPage` gains rows whose value lives outside `config.toml`, notice
rows and a refresh trigger; the System Settings page builds its new section from a `VmOptionsHooks` record. In
`dev.jasper.app.application`, `VmOptionsFeature` supplies those hooks, the actions' handlers and the start-up notices
(posted through `VmOptionsNotices` in a new notification group), and `JasperApplication` wires it in.

**Tech Stack:** Java 25, Swing through the jasper-ui kit descriptors, JUnit 5 + AssertJ, Gradle; one small C++ change
in the plan-1 launcher core.

**Spec:** [`docs/superpowers/specs/2026-10-13-jasper-custom-vm-options-design.md`](../specs/2026-10-13-jasper-custom-vm-options-design.md)
(sections 3.2–3.4, 4.2–4.5, 5.1, 5.2, 5.5, 6.4, 6.5, 7, 8 and 11, plan 2). Plan 1:
[`2026-10-13-jasper-custom-vm-options-1-launcher.md`](2026-10-13-jasper-custom-vm-options-1-launcher.md).

## Global Constraints

- `dev.jasper.app.vmoptions` is JDK-only: no Swing, no AWT, no other Jasper package; `com.sun.management` is allowed
  (physical memory). `verifyApplicationArchitecture` enforces it (Task 2).
- The application's parse of `jasper.vmoptions` mirrors `packaging/launcher/jasper/JasperVmOptionsCore.cpp` exactly:
  a leading UTF-8 BOM skipped; NUL or invalid UTF-8 means corrupted; lines split on `\n`, trimmed of exactly
  `" \t\r\n\f\v"`; blank and `#` lines skipped; a line applies only when it starts with `-` and is not one of libjli's
  launcher options (spec 3.2 list); the digest is FNV-1a 64 over each applied line's UTF-8 bytes plus `\n`, as 16
  lowercase hex digits. `packaging/launcher/test/digest-vectors.txt` (file lines → digest of the applied lines) is read
  by both test suites.
- Launch properties: `jasper.vmOptionsFile`, `jasper.vmOptionsDigest`, `jasper.vmOptionsFile.corrupted` (`true`),
  `jasper.vmOptionsSkipped` (`launch-failed` from the guard, the variable's value or `environment` otherwise).
- Heap: an integer in MiB, at least 512 (error below), a warning above physical memory; written as `-Xmx<N>m` by
  replacing the last `-Xmx…` line or appending one; setting the bundled default removes every `-Xmx…` line; writes are
  atomic. A new file starts with `# custom Jasper VM options (expand/override the bundled options)` and a blank line.
- Notification group: id `jasper.vmoptions`, name `Memory & VM options`, owner `jasper`, `DisplayMode.CARD`.
- Actions: `app.memory` "Change Memory Settings…" and `app.vmoptions` "Edit Custom VM Options…", in Help after
  Keyboard Shortcuts (one section, a separator between) and in the palette.
- Start-up path: nothing new runs before the first output paints except reading the four system properties; reading
  the file, the check and the notices start on `OUTPUT_PAINTED`, `WARMED_UP`, or 5 s after `FIRST_PAINT`.
- Never launch the Jasper GUI; never run `trainAotCache`, `packageDist` or `-Pjasper.aot=true` (AGENTS.md).
- Work in the worktree `.claude/worktrees/custom-vm-options` on `claude/custom-vm-options-settings`, never on `main`.
  Every commit message ends with:

  ```
  🤖 Generated with Claude Code at The Home Depot

  Co-Authored-By: Claude <noreply@anthropic.com>
  ```
- Source hygiene (AGENTS.md): no raw control, private-use or unpaired surrogate characters in source files; use
  escapes (`"\u000B"`, `(byte) 0xEF`).
- `-Xlint:all` is on for every Java compile; keep new code warning-free.

## Planned deviations from the spec

1. **Help anchor.** Anchors are per page, so the System Settings page keeps `background-residency`; the docs heading
   `### Custom VM options` (slug `custom-vm-options`) is where the guide describes the section. There is no
   `vm-options` anchor.
2. **Restart Now** calls `JasperApplication.restart(RestartMode.SAME)` directly instead of a `RestartFlow`: a SAME
   restart needs no resident conversation, and `RestartFlow` reports a failure only through the Plugins page's state.
   A failure posts "Jasper could not restart".
3. **A blank `JASPER_VM_OPTIONS_SKIP`** skips with reason `environment` (spec 4.2, amended with this plan), so the core
   agrees with the guard (Task 1).
4. **The heap row is disabled while the file is corrupted**, with the reason in its description; editing it would
   rewrite unreadable bytes.
5. **Section notices are settings rows** (title, text, buttons) shown only while present; the kit has no coloured
   row text. The check runs when the section is first shown, after a save or an edit, and when a 2-second watch sees
   the file change while the section is shown.
6. **"Restart pending"** is posted each time the Settings dialog closes with a restart pending; the notification
   center coalesces repeats within 60 s.
7. **Start-up trigger.** Besides `OUTPUT_PAINTED`, the start-up notices also start on `WARMED_UP` (a `--background`
   warm-up paints no output) and 5 s after `FIRST_PAINT` (a shell that prints nothing never paints output), as
   residency's settle already does.

## Review Focus

Inputs the spec implies but no other test pins, most likely to bite first; each has its test in the owning task:

1. `jasper.vmoptions` edited in another editor while System Settings is open: the section shows the new heap and
   findings without reopening — Task 8.
2. A heap typed below 512, or cleared: an error under the field and Apply disabled — Task 6.
3. A file Jasper cannot write (permissions, a file where its folder should be): Apply fails with a message naming the
   file, and the edit stays — Tasks 6 and 8.
4. A corrupted file: the heap row is disabled and says why — Task 8.
5. A fallback start when the check cannot run (no `Jasper.cfg` found): the notification still says Jasper started
   without the options — Task 8.

## File structure

| Path | Responsibility |
|---|---|
| `packaging/launcher/jasper/JasperVmOptionsCore.cpp`, `packaging/launcher/test/vmoptions_core_test.cpp` | Blank skip variable (Task 1). |
| `jasper-app/src/main/java/dev/jasper/app/vmoptions/package-info.java` | Package contract. |
| `…/vmoptions/VmOptionsSyntax.java` | Parse, applicability, merge, digest, `-Xmx` sizes (mirror of the core). |
| `…/vmoptions/VmOptionsFile.java` | Read, edit the heap, create, atomic write. |
| `…/vmoptions/VmOptionsLaunch.java` | Launch properties, restart detection, running heap, physical memory. |
| `…/vmoptions/LauncherImage.java` | `Jasper.cfg` from `jpackage.app-path`: bundled options, class path. |
| `…/vmoptions/VmOptionsCheck.java` | Runs the bundled `java` with the options; classifies the output. |
| `jasper-app/src/main/java/dev/jasper/app/settingsui/SchemaPage.java` | External rows, notice rows, refresh trigger, reveal/focus. |
| `…/settingsui/SettingsDialog.java` | Applies a schema page's external edits. |
| `…/settingsui/VmOptionsHooks.java`, `…/settingsui/CorePages.java` | The section's hooks and the System Settings page. |
| `jasper-app/src/main/java/dev/jasper/app/application/VmOptionsNotices.java` | The notification group and its notices. |
| `…/application/VmOptionsFeature.java` | Hooks, actions' handlers, start-up notices, file watch. |
| `…/application/JasperApplication.java`, `…/shortcuthelp/ShortcutCatalog.java` | Wiring. |
| `gradle/application-architecture.gradle.kts` | JDK-only rule for `vmoptions`. |
| Tests under `jasper-app/src/test/java/dev/jasper/app/{vmoptions,settingsui,application}/` | One class per unit. |
| Docs | `docs/configuration.md`, `docs/notifications.md`, `docs/app-architecture.md`, `docs/STATUS.md`, spec status, this plan's status. |

---

### Task 1: A blank skip variable skips

**Files:**
- Modify: `packaging/launcher/jasper/JasperVmOptionsCore.cpp` (the `plan` function)
- Test: `packaging/launcher/test/vmoptions_core_test.cpp`

**Interfaces:**
- Produces: `-Djasper.vmOptionsSkipped=environment` when `JASPER_VM_OPTIONS_SKIP` is set but blank (Task 4 reads it).

- [ ] **Step 1: Write the failing test**

In `packaging/launcher/test/vmoptions_core_test.cpp`, `planning()`, replace:

```cpp
    p = vm::plan(bundled, path, &lines, {{"JASPER_VM_OPTIONS_SKIP", "  "}});
    check(p.javaOptions.back() == "-Djasper.vmOptionsDigest=" + vm::digest(lines.lines),
        "a blank skip variable skips nothing");
```

with:

```cpp
    expectLines(vm::plan(bundled, path, &lines, {{"JASPER_VM_OPTIONS_SKIP", "  "}}).javaOptions,
        {"-Xmx2048m", "-XX:+UseG1GC", file, "-Djasper.vmOptionsSkipped=environment"},
        "a blank skip variable is set too, as the guard sees it");
```

- [ ] **Step 2: Run it to see it fail**

Run: `./gradlew :jasper-app:testLauncherCore`
Expected: FAIL — `FAIL: a blank skip variable is set too, as the guard sees it: got [... -Djasper.vmOptionsDigest=...]`.

- [ ] **Step 3: Implement**

In `packaging/launcher/jasper/JasperVmOptionsCore.cpp`, replace the whole `plan` function with:

```cpp
Plan plan(const std::vector<std::string>& bundled, const std::string& path, const UserFile* file,
          const Environment& env) {
    Plan result;
    result.javaOptions = bundled;
    const std::string* skip = variable(env, SKIP_VARIABLE);
    const bool skipped = skip != nullptr;
    const bool applied = !skipped && !path.empty() && file != nullptr && !file->corrupted && !file->lines.empty();
    if (applied) {
        result.javaOptions = merge(bundled, file->lines);
    }
    if (!path.empty()) {
        result.javaOptions.push_back("-Djasper.vmOptionsFile=" + path);
    }
    if (skipped) {
        // A blank value is set too: the guard tests presence, so both agree that this launch is a fallback start.
        const std::string reason = trim(*skip);
        result.javaOptions.push_back("-Djasper.vmOptionsSkipped=" + (reason.empty() ? std::string("environment") : reason));
    } else if (!path.empty() && file != nullptr && file->corrupted) {
        result.javaOptions.push_back("-Djasper.vmOptionsFile.corrupted=true");
    } else if (applied) {
        result.javaOptions.push_back("-Djasper.vmOptionsDigest=" + digest(file->lines));
    }
    return result;
}
```

- [ ] **Step 4: Run it to see it pass**

Run: `./gradlew :jasper-app:testLauncherCore :jasper-app:verifyLauncherImage`
Expected: BUILD SUCCESSFUL (core checks 0 failures; the image test's `JASPER_VM_OPTIONS_SKIP=manual` scenario still
passes).

- [ ] **Step 5: Commit**

```bash
git add packaging/launcher/jasper/JasperVmOptionsCore.cpp packaging/launcher/test/vmoptions_core_test.cpp
git commit -F - <<'EOF'
Launcher: a blank JASPER_VM_OPTIONS_SKIP skips too

The guard treats the variable's presence as a fallback start, so the core
now does as well and reports the reason as "environment" (spec 4.2).

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

---

### Task 2: `dev.jasper.app.vmoptions` — the launcher's reading, mirrored

**Files:**
- Create: `jasper-app/src/main/java/dev/jasper/app/vmoptions/package-info.java`
- Create: `jasper-app/src/main/java/dev/jasper/app/vmoptions/VmOptionsSyntax.java`
- Test: `jasper-app/src/test/java/dev/jasper/app/vmoptions/VmOptionsSyntaxTest.java`
- Modify: `gradle/application-architecture.gradle.kts` (JDK-only rule)

**Interfaces:**
- Produces: `VmOptionsSyntax.Parsed(List<String> lines, List<String> ignored, boolean corrupted)` with
  `Parsed.EMPTY`; `static Parsed parse(byte[] bytes)`; `static boolean isApplicable(String line)`;
  `static List<String> merge(List<String> bundled, List<String> user)`; `static String digest(List<String> lines)`;
  `static OptionalLong maxHeapMiB(String option)`; `static OptionalLong lastMaxHeapMiB(List<String> options)`;
  package-private `static String trim(String value)`.

- [ ] **Step 1: Write the failing test**

Create `jasper-app/src/test/java/dev/jasper/app/vmoptions/VmOptionsSyntaxTest.java`:

```java
package dev.jasper.app.vmoptions;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalLong;
import org.junit.jupiter.api.Test;

class VmOptionsSyntaxTest {
    private static final Path ROOT = Path.of(System.getProperty("jasper.repoRoot", "..")).toAbsolutePath().normalize();

    private static VmOptionsSyntax.Parsed parse(String text) { return VmOptionsSyntax.parse(text.getBytes(StandardCharsets.UTF_8)); }

    /** Parses raw bytes given as ints (chars widen to int; an invocation never narrows constants to byte). */
    private static VmOptionsSyntax.Parsed parseBytes(int... values) {
        byte[] bytes = new byte[values.length];
        for (int i = 0; i < values.length; i++) bytes[i] = (byte) values[i];
        return VmOptionsSyntax.parse(bytes);
    }

    @Test void linesAreTrimmedAndCommentsAndBlankLinesSkipped() {
        var parsed = parse("# heap\r\n\r\n  -Xmx1g  \r\n\t-Dfoo=a b\t\n#-Xmx2g\n-XX:+UseZGC");
        assertThat(parsed.lines()).containsExactly("-Xmx1g", "-Dfoo=a b", "-XX:+UseZGC");
        assertThat(parsed.ignored()).isEmpty();
        assertThat(parsed.corrupted()).isFalse();
        assertThat(parse("-Dcolor=#fff\n").lines()).containsExactly("-Dcolor=#fff");
        assertThat(parse("-Dv=a\u000B\n").lines()).as("vertical tab is trimmed like the core").containsExactly("-Dv=a");
        assertThat(parse("").lines()).isEmpty();
        assertThat(parse("# only\n\n   \n").lines()).isEmpty();
    }

    @Test void aByteOrderMarkIsSkippedAndUtf8TextKept() {
        assertThat(parseBytes(0xEF, 0xBB, 0xBF, '-', 'X', 'm', 'x', '1', 'g', '\n').lines()).containsExactly("-Xmx1g");
        assertThat(parse("-Dname=Jürgen\n").lines()).containsExactly("-Dname=Jürgen");
    }

    @Test void nulOrInvalidUtf8CorruptsTheWholeFile() {
        assertThat(parseBytes('-', 'X', 'm', 'x', '1', 'g', '\n', 0, '-', 'X', 'm', 's', '\n')).isEqualTo(new VmOptionsSyntax.Parsed(List.of(), List.of(), true));
        assertThat(parseBytes('-', 'D', '=', 0xC3, 0x28).corrupted()).as("invalid continuation").isTrue();
        assertThat(parseBytes('-', 'D', '=', 0xC0, 0xAF).corrupted()).as("overlong").isTrue();
        assertThat(parseBytes('-', 'D', '=', 0xED, 0xA0, 0x80).corrupted()).as("surrogate").isTrue();
        assertThat(parseBytes('-', 'D', '=', 0xE2, 0x82).corrupted()).as("truncated").isTrue();
    }

    @Test void onlyOptionLinesApply() {
        var parsed = parse("Xmx300m\n\"-Dfoo=a b\"\n// heap\n-version\n--add-opens\n-Xmx1g\n"
            + "--add-opens=java.base/java.lang=ALL-UNNAMED\n--class-path=/x\n-Xshare:auto\n");
        assertThat(parsed.lines()).containsExactly("-Xmx1g", "--add-opens=java.base/java.lang=ALL-UNNAMED", "-Xshare:auto");
        assertThat(parsed.ignored()).containsExactly("Xmx300m", "\"-Dfoo=a b\"", "// heap", "-version", "--add-opens", "--class-path=/x");
        assertThat(VmOptionsSyntax.isApplicable("-X")).isFalse();
        assertThat(VmOptionsSyntax.isApplicable("-XshowSettings")).isTrue();
        assertThat(VmOptionsSyntax.isApplicable("-cp")).isFalse();
        assertThat(VmOptionsSyntax.isApplicable("--module-path=/x")).isTrue();
        assertThat(VmOptionsSyntax.isApplicable("--module-path")).isFalse();
        assertThat(VmOptionsSyntax.isApplicable("--enable-native-access=ALL-UNNAMED")).isTrue();
        assertThat(VmOptionsSyntax.isApplicable("")).isFalse();
        assertThat(VmOptionsSyntax.isApplicable("-")).isTrue();
    }

    @Test void mergeAppendsAndAppliesIntelliJsFilters() {
        assertThat(VmOptionsSyntax.merge(List.of("-a", "-b"), List.of("-c"))).containsExactly("-a", "-b", "-c");
        assertThat(VmOptionsSyntax.merge(List.of("-XX:+UseG1GC", "-Xmx2048m"), List.of("-XX:+UseZGC"))).containsExactly("-Xmx2048m", "-XX:+UseZGC");
        assertThat(VmOptionsSyntax.merge(List.of("-XX:+UseG1GC"), List.of("-XX:-UseG1GC"))).containsExactly("-XX:+UseG1GC", "-XX:-UseG1GC");
        assertThat(VmOptionsSyntax.merge(List.of("-Xmx2048m", "-Xms128m"), List.of("-XX:MaxRAMPercentage=50"))).containsExactly("-Xms128m", "-XX:MaxRAMPercentage=50");
        assertThat(VmOptionsSyntax.merge(List.of("-Xmx2048m"), List.of("-XX:MinRAMPercentage=5"))).containsExactly("-XX:MinRAMPercentage=5");
        assertThat(VmOptionsSyntax.merge(List.of("-Xmx2048m", "-Xms128m"), List.of("-XX:InitialRAMPercentage=5"))).containsExactly("-Xmx2048m", "-XX:InitialRAMPercentage=5");
        assertThat(VmOptionsSyntax.merge(List.of("-Xmx2048m"), List.of("-Xmx4g"))).containsExactly("-Xmx2048m", "-Xmx4g");
    }

    @Test void digestsMatchTheLaunchersVectors() throws IOException {
        List<String> lines = new ArrayList<>();
        int vectors = 0;
        for (String line : Files.readAllLines(ROOT.resolve("packaging/launcher/test/digest-vectors.txt"), StandardCharsets.UTF_8)) {
            if (line.isEmpty() || line.startsWith("#")) continue;
            if (line.startsWith("= ")) {
                String joined = lines.stream().map(entry -> entry + "\n").reduce("", String::concat);
                assertThat(VmOptionsSyntax.digest(parse(joined).lines())).as("digest of %s", lines).isEqualTo(line.substring(2));
                lines.clear();
                vectors++;
            } else {
                lines.add(line);
            }
        }
        assertThat(vectors).isGreaterThanOrEqualTo(6);
    }

    @Test void heapSizesAreReadInMiB() {
        assertThat(VmOptionsSyntax.maxHeapMiB("-Xmx2048m")).hasValue(2048);
        assertThat(VmOptionsSyntax.maxHeapMiB("-Xmx4g")).hasValue(4096);
        assertThat(VmOptionsSyntax.maxHeapMiB("-Xmx4G")).hasValue(4096);
        assertThat(VmOptionsSyntax.maxHeapMiB("-Xmx1t")).hasValue(1_048_576);
        assertThat(VmOptionsSyntax.maxHeapMiB("-Xmx1048576k")).hasValue(1024);
        assertThat(VmOptionsSyntax.maxHeapMiB("-Xmx2147483648")).hasValue(2048);
        assertThat(VmOptionsSyntax.maxHeapMiB("-Xmx3q")).isEmpty();
        assertThat(VmOptionsSyntax.maxHeapMiB("-Xmx")).isEmpty();
        assertThat(VmOptionsSyntax.maxHeapMiB("-Xms1g")).isEmpty();
        assertThat(VmOptionsSyntax.maxHeapMiB("-Xmx99999999999999t")).as("overflow").isEmpty();
        assertThat(VmOptionsSyntax.lastMaxHeapMiB(List.of("-Xmx1g", "-Dx=1", "-Xmx3q", "-Xmx2g"))).hasValue(2048);
        assertThat(VmOptionsSyntax.lastMaxHeapMiB(List.of("-Dx=1"))).isEqualTo(OptionalLong.empty());
    }
}
```

- [ ] **Step 2: Run it to see it fail**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.vmoptions.VmOptionsSyntaxTest'`
Expected: FAIL — compilation error, `VmOptionsSyntax` does not exist.

- [ ] **Step 3: Implement**

Create `jasper-app/src/main/java/dev/jasper/app/vmoptions/package-info.java`:

```java
/**
 * Custom VM options (spec section 6.4): the launcher's reading of {@code jasper.vmoptions}, mirrored for the
 * application; editing the file; what this JVM was launched with; the bundled launcher configuration; and the check
 * that runs the bundled {@code java} with the options. JDK-only: no Swing or AWT ({@code com.sun.management} for the
 * physical memory); {@code verifyApplicationArchitecture} enforces it. No owner: values and pure functions, plus file
 * I/O and a check that run on the caller's thread.
 * <p>Allowed outgoing Jasper dependencies: no other Jasper package.
 * The module architecture check forbids package cycles; app types are not an external plugin API.
 */
package dev.jasper.app.vmoptions;
```

Create `jasper-app/src/main/java/dev/jasper/app/vmoptions/VmOptionsSyntax.java`:

```java
package dev.jasper.app.vmoptions;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalLong;
import java.util.Set;

/**
 * The launcher's reading of {@code jasper.vmoptions}, mirrored from packaging/launcher/jasper/JasperVmOptionsCore.cpp
 * (spec sections 3.2 and 3.3): the same trimming, skipped lines, applicable options, merge filters and digest, so the
 * application's view of the file, and its digest, match what the launcher did. Pure.
 */
public final class VmOptionsSyntax {
    /**
     * A file as the launcher reads it.
     *
     * @param lines the lines it applies, in order
     * @param ignored the lines it read but does not apply (no leading {@code -}, or one of libjli's own options)
     * @param corrupted whether it ignored the whole file (a NUL character or invalid UTF-8)
     */
    public record Parsed(List<String> lines, List<String> ignored, boolean corrupted) {
        /** An absent or empty file. */
        public static final Parsed EMPTY = new Parsed(List.of(), List.of(), false);

        public Parsed {
            lines = List.copyOf(lines);
            ignored = List.copyOf(ignored);
        }
    }

    /** The core's whitespace: space, tab, CR, LF, form feed and vertical tab. */
    private static final String WHITESPACE = " \t\r\n\f\u000B";
    /** libjli's own launcher options (spec 3.2). */
    private static final Set<String> LAUNCHER_OPTIONS = Set.of(
        "-version", "--version", "-help", "--help", "-h", "-?", "-X", "--help-extra", "--dry-run", "--list-modules",
        "-d", "--describe-module", "--validate-modules", "-jar", "-m", "--module", "-cp", "-classpath", "--class-path",
        "--source", "--add-opens", "--add-exports", "--add-reads", "--add-modules", "--limit-modules", "--module-path", "-p",
        "--upgrade-module-path", "--patch-module", "--enable-native-access");
    private static final List<String> LAUNCHER_OPTION_PREFIXES = List.of("--describe-module=", "--module=", "--class-path=", "--source=");

    private VmOptionsSyntax() { }

    /** Reads a file's bytes as the launcher does. */
    public static Parsed parse(byte[] bytes) {
        int start = bytes.length >= 3 && (bytes[0] & 0xFF) == 0xEF && (bytes[1] & 0xFF) == 0xBB && (bytes[2] & 0xFF) == 0xBF ? 3 : 0;
        for (int i = start; i < bytes.length; i++) {
            if (bytes[i] == 0) return new Parsed(List.of(), List.of(), true);
        }
        String text;
        try {
            text = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes, start, bytes.length - start))
                .toString();
        } catch (CharacterCodingException malformed) {
            return new Parsed(List.of(), List.of(), true);
        }
        var lines = new ArrayList<String>();
        var ignored = new ArrayList<String>();
        for (String raw : text.split("\n", -1)) {
            String line = trim(raw);
            if (line.isEmpty() || line.charAt(0) == '#') continue;
            if (isApplicable(line)) lines.add(line);
            else ignored.add(line);
        }
        return new Parsed(lines, ignored, false);
    }

    /** Whether a line is passed to the JVM: it starts with {@code -} and is not one of libjli's own launcher options. */
    public static boolean isApplicable(String line) {
        if (line.isEmpty() || line.charAt(0) != '-') return false;
        if (LAUNCHER_OPTIONS.contains(line)) return false;
        for (String prefix : LAUNCHER_OPTION_PREFIXES) {
            if (line.startsWith(prefix)) return false;
        }
        return true;
    }

    /** The bundled options without those the user's lines replace, then the user's lines (spec 3.3). */
    public static List<String> merge(List<String> bundled, List<String> user) {
        boolean collector = user.stream().anyMatch(VmOptionsSyntax::isCollector);
        boolean xms = user.stream().anyMatch(line -> line.startsWith("-XX:InitialRAMPercentage="));
        boolean xmx = user.stream().anyMatch(line -> line.startsWith("-XX:MinRAMPercentage=") || line.startsWith("-XX:MaxRAMPercentage="));
        var merged = new ArrayList<String>();
        for (String option : bundled) {
            if ((collector && isCollector(option)) || (xms && option.startsWith("-Xms")) || (xmx && option.startsWith("-Xmx"))) continue;
            merged.add(option);
        }
        merged.addAll(user);
        return List.copyOf(merged);
    }

    /** FNV-1a 64 over each line's UTF-8 bytes followed by {@code \n}, as 16 lowercase hex digits. */
    public static String digest(List<String> lines) {
        long hash = 0xcbf29ce484222325L;
        for (String line : lines) {
            for (byte value : (line + "\n").getBytes(StandardCharsets.UTF_8)) {
                hash ^= value & 0xFF;
                hash *= 0x100000001b3L;
            }
        }
        return String.format("%016x", hash);
    }

    /** The heap limit an {@code -Xmx} option sets, in MiB rounded down; empty when it is no valid {@code -Xmx}. */
    public static OptionalLong maxHeapMiB(String option) {
        if (!option.startsWith("-Xmx") || option.length() == 4) return OptionalLong.empty();
        String size = option.substring(4);
        long scale = switch (Character.toLowerCase(size.charAt(size.length() - 1))) {
            case 'k' -> 1L << 10;
            case 'm' -> 1L << 20;
            case 'g' -> 1L << 30;
            case 't' -> 1L << 40;
            default -> 1L;
        };
        String digits = scale == 1L ? size : size.substring(0, size.length() - 1);
        if (digits.isEmpty() || !digits.chars().allMatch(Character::isDigit)) return OptionalLong.empty();
        try {
            return OptionalLong.of(Math.multiplyExact(Long.parseLong(digits), scale) >> 20);
        } catch (ArithmeticException | NumberFormatException tooLarge) {
            return OptionalLong.empty();
        }
    }

    /** The last valid {@code -Xmx} among {@code options}, in MiB. */
    public static OptionalLong lastMaxHeapMiB(List<String> options) {
        OptionalLong last = OptionalLong.empty();
        for (String option : options) {
            OptionalLong heap = maxHeapMiB(option);
            if (heap.isPresent()) last = heap;
        }
        return last;
    }

    /** {@code value} without the core's whitespace at either end. */
    static String trim(String value) {
        int first = 0;
        int last = value.length();
        while (first < last && WHITESPACE.indexOf(value.charAt(first)) >= 0) first++;
        while (last > first && WHITESPACE.indexOf(value.charAt(last - 1)) >= 0) last--;
        return value.substring(first, last);
    }

    private static boolean isCollector(String option) { return option.startsWith("-XX:+") && option.endsWith("GC"); }
}
```

In `gradle/application-architecture.gradle.kts`, after the block that ends
`"dev.jasper.app.dock is JDK-only and holds no Swing or AWT components: $line"` and its closing `}`, add:

```kotlin
                if (module == app && packageOf(from) == "dev.jasper.app.vmoptions")
                    check(to.startsWith("dev.jasper.app.vmoptions.") || to.startsWith("com.sun.management.")
                        || (jdk(to) && !to.startsWith("java.awt.") && !to.startsWith("javax.swing."))) {
                        "dev.jasper.app.vmoptions is JDK-only and holds no Swing or AWT: $line"
                    }
```

- [ ] **Step 4: Run the tests and the architecture check**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.vmoptions.VmOptionsSyntaxTest' :jasper-app:test --tests 'dev.jasper.app.documentation.AppDocumentationTest' verifyApplicationArchitecture`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add jasper-app/src/main/java/dev/jasper/app/vmoptions jasper-app/src/test/java/dev/jasper/app/vmoptions gradle/application-architecture.gradle.kts
git commit -F - <<'EOF'
VM options: the launcher's reading of jasper.vmoptions, mirrored

dev.jasper.app.vmoptions starts with VmOptionsSyntax: the same parse,
applicable options, merge filters and digest as the launcher core, checked
against the shared digest vectors, plus -Xmx sizes. The package is
JDK-only, enforced by verifyApplicationArchitecture.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

---

### Task 3: Editing the file

**Files:**
- Create: `jasper-app/src/main/java/dev/jasper/app/vmoptions/VmOptionsFile.java`
- Test: `jasper-app/src/test/java/dev/jasper/app/vmoptions/VmOptionsFileTest.java`

**Interfaces:**
- Consumes: Task 2's `VmOptionsSyntax.parse`, `Parsed.EMPTY`, `trim`.
- Produces: `VmOptionsFile.HEADER`; `record Contents(boolean exists, String text, VmOptionsSyntax.Parsed parsed)` with
  `Contents.ABSENT`; `static Contents read(Path file) throws IOException`;
  `static String withMaxHeap(String text, long mib, long bundledMib)`;
  `static void setMaxHeap(Path file, long mib, long bundledMib) throws IOException`;
  `static Path ensureExists(Path file) throws IOException`; `static void write(Path file, String text) throws IOException`.

- [ ] **Step 1: Write the failing test**

Create `jasper-app/src/test/java/dev/jasper/app/vmoptions/VmOptionsFileTest.java`:

```java
package dev.jasper.app.vmoptions;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class VmOptionsFileTest {
    @TempDir Path home;

    @Test void aMissingFileOrADirectoryReadsAsAbsent() throws IOException {
        assertThat(VmOptionsFile.read(home.resolve("jasper.vmoptions"))).isEqualTo(VmOptionsFile.Contents.ABSENT);
        Files.createDirectory(home.resolve("dir.vmoptions"));
        assertThat(VmOptionsFile.read(home.resolve("dir.vmoptions")).exists()).isFalse();
        Files.writeString(home.resolve("jasper.vmoptions"), "-Xmx4g\nXmx1g\n");
        var contents = VmOptionsFile.read(home.resolve("jasper.vmoptions"));
        assertThat(contents.exists()).isTrue();
        assertThat(contents.text()).isEqualTo("-Xmx4g\nXmx1g\n");
        assertThat(contents.parsed().lines()).containsExactly("-Xmx4g");
        assertThat(contents.parsed().ignored()).containsExactly("Xmx1g");
    }

    @Test void theLastXmxLineIsReplacedAndEverythingElseKept() {
        String text = "# mine\n-Xmx1g\n-Dfoo=bar\n   -Xmx2g\n#-Xmx9g\n";
        assertThat(VmOptionsFile.withMaxHeap(text, 4096, 2048)).isEqualTo("# mine\n-Xmx1g\n-Dfoo=bar\n-Xmx4096m\n#-Xmx9g\n");
    }

    @Test void withoutAnXmxLineOneIsAppended() {
        assertThat(VmOptionsFile.withMaxHeap("-Dfoo=bar", 4096, 2048)).isEqualTo("-Dfoo=bar\n-Xmx4096m\n");
        assertThat(VmOptionsFile.withMaxHeap("", 4096, 2048)).isEqualTo(VmOptionsFile.HEADER + "\n\n-Xmx4096m\n");
        assertThat(VmOptionsFile.withMaxHeap("\n\n", 1024, 2048)).isEqualTo(VmOptionsFile.HEADER + "\n\n-Xmx1024m\n");
    }

    @Test void theBundledDefaultRemovesEveryXmxLine() {
        assertThat(VmOptionsFile.withMaxHeap("# mine\n-Xmx1g\n-Dfoo=bar\n -Xmx2g\n", 2048, 2048)).isEqualTo("# mine\n-Dfoo=bar\n");
        assertThat(VmOptionsFile.withMaxHeap("", 2048, 2048)).isEmpty();
    }

    @Test void windowsLineEndingsStay() {
        assertThat(VmOptionsFile.withMaxHeap("# mine\r\n-Xmx1g\r\n", 3072, 2048)).isEqualTo("# mine\r\n-Xmx3072m\r\n");
    }

    @Test void ensureExistsCreatesTheHeaderOnceAndWriteLeavesNoTemporaryFile() throws IOException {
        Path file = home.resolve("nested/jasper.vmoptions");
        VmOptionsFile.ensureExists(file);
        assertThat(Files.readString(file)).isEqualTo(VmOptionsFile.HEADER + "\n\n");
        Files.writeString(file, "-Xmx1g\n");
        VmOptionsFile.ensureExists(file);
        assertThat(Files.readString(file)).isEqualTo("-Xmx1g\n");
        VmOptionsFile.setMaxHeap(file, 8192, 2048);
        assertThat(Files.readString(file, StandardCharsets.UTF_8)).isEqualTo("-Xmx8192m\n");
        try (Stream<Path> entries = Files.list(file.getParent())) {
            assertThat(entries.map(path -> path.getFileName().toString())).containsExactly("jasper.vmoptions");
        }
    }
}
```

- [ ] **Step 2: Run it to see it fail**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.vmoptions.VmOptionsFileTest'`
Expected: FAIL — compilation error, `VmOptionsFile` does not exist.

- [ ] **Step 3: Implement**

Create `jasper-app/src/main/java/dev/jasper/app/vmoptions/VmOptionsFile.java`:

```java
package dev.jasper.app.vmoptions;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Reading and editing {@code jasper.vmoptions} the way IntelliJ edits its file (spec section 5.1). Blocking file I/O. */
public final class VmOptionsFile {
    /** The first line of a file Jasper creates. */
    public static final String HEADER = "# custom Jasper VM options (expand/override the bundled options)";

    /**
     * The file as it is now.
     *
     * @param exists whether it is a regular file
     * @param text its text, decoded leniently for editing; empty when absent
     * @param parsed the launcher's reading of it
     */
    public record Contents(boolean exists, String text, VmOptionsSyntax.Parsed parsed) {
        /** No file. */
        public static final Contents ABSENT = new Contents(false, "", VmOptionsSyntax.Parsed.EMPTY);
    }

    private VmOptionsFile() { }

    /** Reads {@code file}; a missing file, or a directory, reads as absent. */
    public static Contents read(Path file) throws IOException {
        if (!Files.isRegularFile(file)) return Contents.ABSENT;
        byte[] bytes = Files.readAllBytes(file);
        return new Contents(true, new String(bytes, StandardCharsets.UTF_8), VmOptionsSyntax.parse(bytes));
    }

    /**
     * {@code text} with the heap limit set as IntelliJ's {@code VMOptions.setOption} does: the last line starting with
     * {@code -Xmx} becomes {@code -Xmx<mib>m} (losing its indentation), or one is appended, after the header when the
     * text holds nothing yet. {@code bundledMib} instead removes every {@code -Xmx} line, so a later change to the
     * bundled default reaches the user. Keeps the text's line separator.
     */
    public static String withMaxHeap(String text, long mib, long bundledMib) {
        String separator = text.contains("\r\n") ? "\r\n" : "\n";
        var lines = new ArrayList<String>(text.isEmpty() ? List.of() : Arrays.asList(text.split("\r?\n", -1)));
        if (!lines.isEmpty() && lines.getLast().isEmpty()) lines.removeLast();
        if (mib == bundledMib) {
            lines.removeIf(line -> VmOptionsSyntax.trim(line).startsWith("-Xmx"));
        } else {
            String option = "-Xmx" + mib + "m";
            int last = -1;
            for (int i = lines.size() - 1; i >= 0 && last < 0; i--) {
                if (VmOptionsSyntax.trim(lines.get(i)).startsWith("-Xmx")) last = i;
            }
            if (last >= 0) {
                lines.set(last, option);
            } else {
                if (lines.stream().allMatch(line -> VmOptionsSyntax.trim(line).isEmpty())) {
                    lines.clear();
                    lines.add(HEADER);
                    lines.add("");
                }
                lines.add(option);
            }
        }
        return lines.isEmpty() ? "" : String.join(separator, lines) + separator;
    }

    /** Reads {@code file}, sets the heap limit as {@link #withMaxHeap} does, and writes it back. */
    public static void setMaxHeap(Path file, long mib, long bundledMib) throws IOException {
        write(file, withMaxHeap(read(file).text(), mib, bundledMib));
    }

    /** Creates {@code file} with the header and a blank line when it does not exist; returns it. */
    public static Path ensureExists(Path file) throws IOException {
        if (!Files.exists(file)) write(file, HEADER + "\n\n");
        return file;
    }

    /** Replaces {@code file} with {@code text} (UTF-8) atomically, creating its directory. */
    public static void write(Path file, String text) throws IOException {
        Path directory = file.toAbsolutePath().getParent();
        Files.createDirectories(directory);
        Path temporary = Files.createTempFile(directory, ".jasper.vmoptions", ".tmp");
        try {
            Files.writeString(temporary, text, StandardCharsets.UTF_8);
            try {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
```

- [ ] **Step 4: Run it to see it pass**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.vmoptions.VmOptionsFileTest'`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add jasper-app/src/main/java/dev/jasper/app/vmoptions/VmOptionsFile.java jasper-app/src/test/java/dev/jasper/app/vmoptions/VmOptionsFileTest.java
git commit -F - <<'EOF'
VM options: edit jasper.vmoptions the way IntelliJ does

VmOptionsFile reads the file, sets the heap by replacing the last -Xmx
line or appending one (the bundled default removes them), creates it with
IntelliJ's header, and writes atomically.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

---

### Task 4: The launch and the launcher image

**Files:**
- Create: `jasper-app/src/main/java/dev/jasper/app/vmoptions/VmOptionsLaunch.java`
- Create: `jasper-app/src/main/java/dev/jasper/app/vmoptions/LauncherImage.java`
- Test: `jasper-app/src/test/java/dev/jasper/app/vmoptions/VmOptionsLaunchTest.java`,
  `jasper-app/src/test/java/dev/jasper/app/vmoptions/LauncherImageTest.java`

**Interfaces:**
- Consumes: Task 2's `VmOptionsSyntax`.
- Produces: `record VmOptionsLaunch(Optional<Path> file, Optional<String> digest, boolean corrupted, Optional<String> skipped)`
  with `static VmOptionsLaunch current()`, package-private `static VmOptionsLaunch of(Properties)`,
  `boolean launcherManaged()`, `static Optional<String> appliedDigest(VmOptionsSyntax.Parsed)`,
  `boolean restartNeeded(VmOptionsSyntax.Parsed now, VmOptionsSyntax.Parsed atStart)`,
  `static long runningMaxHeapMiB()`, package-private `static long runningMaxHeapMiB(List<String>, long)`,
  `static OptionalLong physicalMemoryMiB()`;
  `record LauncherImage(Path appDirectory, List<String> javaOptions, List<String> classpath)` with
  `DEFAULT_MAX_HEAP_MIB = 2048`, `static Optional<LauncherImage> locate(String appPath)`,
  package-private `static LauncherImage parse(Path, List<String>)`, `long bundledMaxHeapMiB()`, `boolean hasAotCache()`.

- [ ] **Step 1: Write the failing tests**

Create `jasper-app/src/test/java/dev/jasper/app/vmoptions/VmOptionsLaunchTest.java`:

```java
package dev.jasper.app.vmoptions;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import org.junit.jupiter.api.Test;

class VmOptionsLaunchTest {
    private static Properties properties(String... pairs) {
        var properties = new Properties();
        for (int i = 0; i < pairs.length; i += 2) properties.setProperty(pairs[i], pairs[i + 1]);
        return properties;
    }

    private static VmOptionsSyntax.Parsed parsed(String text) { return VmOptionsSyntax.parse(text.getBytes(StandardCharsets.UTF_8)); }

    @Test void theLaunchersPropertiesAreRead() {
        var launch = VmOptionsLaunch.of(properties("jasper.vmOptionsFile", "/h/jasper.vmoptions", "jasper.vmOptionsDigest", "c758526d21c369d0",
            "jasper.vmOptionsSkipped", "launch-failed"));
        assertThat(launch.file()).contains(Path.of("/h/jasper.vmoptions"));
        assertThat(launch.digest()).contains("c758526d21c369d0");
        assertThat(launch.skipped()).contains("launch-failed");
        assertThat(launch.corrupted()).isFalse();
        assertThat(launch.launcherManaged()).isTrue();
        assertThat(VmOptionsLaunch.of(properties("jasper.vmOptionsFile.corrupted", "true")).corrupted()).isTrue();
        var development = VmOptionsLaunch.of(properties());
        assertThat(development.launcherManaged()).isFalse();
        assertThat(development.file()).isEmpty();
    }

    @Test void aRestartIsNeededWhenTheAppliedLinesDiffer() {
        var applied = new VmOptionsLaunch(Optional.of(Path.of("/h/jasper.vmoptions")), Optional.of(VmOptionsSyntax.digest(List.of("-Xmx2048m"))),
            false, Optional.empty());
        assertThat(applied.restartNeeded(parsed("# heap\n-Xmx2048m\n"), VmOptionsSyntax.Parsed.EMPTY)).as("comments do not count").isFalse();
        assertThat(applied.restartNeeded(parsed("-Xmx4g\n"), VmOptionsSyntax.Parsed.EMPTY)).isTrue();
        var none = new VmOptionsLaunch(Optional.of(Path.of("/h/jasper.vmoptions")), Optional.empty(), false, Optional.empty());
        assertThat(none.restartNeeded(VmOptionsSyntax.Parsed.EMPTY, VmOptionsSyntax.Parsed.EMPTY)).isFalse();
        assertThat(none.restartNeeded(parsed("Xmx300m\n"), VmOptionsSyntax.Parsed.EMPTY)).as("ignored lines apply nothing").isFalse();
        assertThat(none.restartNeeded(parsed("-Xmx4g\n"), VmOptionsSyntax.Parsed.EMPTY)).isTrue();
    }

    @Test void aSkippedOrCorruptedLaunchComparesWithTheFileAtStart() {
        var skipped = new VmOptionsLaunch(Optional.of(Path.of("/h/jasper.vmoptions")), Optional.empty(), false, Optional.of("launch-failed"));
        assertThat(skipped.restartNeeded(parsed("-Xmx3q\n"), parsed("-Xmx3q\n"))).as("restarting would fail the same way").isFalse();
        assertThat(skipped.restartNeeded(parsed("-Xmx3g\n"), parsed("-Xmx3q\n"))).isTrue();
        var corrupted = new VmOptionsLaunch(Optional.of(Path.of("/h/jasper.vmoptions")), Optional.empty(), true, Optional.empty());
        assertThat(corrupted.restartNeeded(parsed("-Xmx3g\n"), VmOptionsSyntax.parse(new byte[] {'-', 0}))).isTrue();
    }

    @Test void theRunningHeapIsTheLastXmxElseTheRuntimesMaximum() {
        assertThat(VmOptionsLaunch.runningMaxHeapMiB(List.of("-Xmx2048m", "-Dx=1", "-Xmx4096m"), 1L << 30)).isEqualTo(4096);
        assertThat(VmOptionsLaunch.runningMaxHeapMiB(List.of("-Dx=1"), 3L << 30)).isEqualTo(3072);
        assertThat(VmOptionsLaunch.runningMaxHeapMiB()).isPositive();
    }

    @Test void physicalMemoryIsKnownOnSupportedJvms() {
        assertThat(VmOptionsLaunch.physicalMemoryMiB().orElseThrow()).isPositive();
    }
}
```

Create `jasper-app/src/test/java/dev/jasper/app/vmoptions/LauncherImageTest.java`:

```java
package dev.jasper.app.vmoptions;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LauncherImageTest {
    @TempDir Path root;

    private static final String CONFIG = """
        [Application]
        app.classpath=$APPDIR/jasper-app.jar
        app.mainclass=dev.jasper.app.Main
        app.classpath=$APPDIR/flatlaf-3.7.jar

        [JavaOptions]
        java-options=-Djpackage.app-version=1.0.0
        java-options=-Xmx2048m
        java-options=-Dflatlaf.nativeLibraryPath=$APPDIR/native
        java-options=-XX:AOTCache=$APPDIR/jasper.aot
        """;

    private Path image(String launcher, String appDirectory) throws IOException {
        Path app = root.resolve(appDirectory);
        Files.createDirectories(app);
        Files.writeString(app.resolve("Jasper.cfg"), CONFIG);
        Path executable = root.resolve(launcher);
        Files.createDirectories(executable.getParent());
        Files.writeString(executable, "");
        return app;
    }

    @Test void macOsWindowsAndLinuxLayoutsAreFound() throws IOException {
        Path mac = image("Jasper.app/Contents/MacOS/Jasper", "Jasper.app/Contents/app");
        assertThat(LauncherImage.locate(root.resolve("Jasper.app/Contents/MacOS/Jasper").toString()))
            .hasValueSatisfying(image -> assertThat(image.appDirectory()).isEqualTo(mac));
        Path windows = image("win/Jasper/Jasper.exe", "win/Jasper/app");
        assertThat(LauncherImage.locate(root.resolve("win/Jasper/Jasper.exe").toString()))
            .hasValueSatisfying(image -> assertThat(image.appDirectory()).isEqualTo(windows));
        Path linux = image("linux/Jasper/bin/Jasper", "linux/Jasper/lib/app");
        assertThat(LauncherImage.locate(root.resolve("linux/Jasper/bin/Jasper").toString()))
            .hasValueSatisfying(image -> assertThat(image.appDirectory()).isEqualTo(linux));
    }

    @Test void optionsAndClassPathHaveTheAppDirectoryResolved() throws IOException {
        Path app = image("Jasper.app/Contents/MacOS/Jasper", "Jasper.app/Contents/app");
        var image = LauncherImage.locate(root.resolve("Jasper.app/Contents/MacOS/Jasper").toString()).orElseThrow();
        assertThat(image.classpath()).containsExactly(app + "/jasper-app.jar", app + "/flatlaf-3.7.jar");
        assertThat(image.javaOptions()).contains("-Xmx2048m", "-Dflatlaf.nativeLibraryPath=" + app + "/native");
        assertThat(image.bundledMaxHeapMiB()).isEqualTo(2048);
        assertThat(image.hasAotCache()).isTrue();
    }

    @Test void noLauncherMeansNoImage() {
        assertThat(LauncherImage.locate(null)).isEmpty();
        assertThat(LauncherImage.locate(" ")).isEmpty();
        assertThat(LauncherImage.locate(root.resolve("nowhere/Jasper").toString())).isEmpty();
        var bare = LauncherImage.parse(root, List.of("java-options=-Dx=1"));
        assertThat(bare.bundledMaxHeapMiB()).isEqualTo(LauncherImage.DEFAULT_MAX_HEAP_MIB);
        assertThat(bare.hasAotCache()).isFalse();
    }
}
```

- [ ] **Step 2: Run them to see them fail**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.vmoptions.VmOptionsLaunchTest' --tests 'dev.jasper.app.vmoptions.LauncherImageTest'`
Expected: FAIL — compilation errors, the two types do not exist.

- [ ] **Step 3: Implement**

Create `jasper-app/src/main/java/dev/jasper/app/vmoptions/VmOptionsLaunch.java`:

```java
package dev.jasper.app.vmoptions;

import java.lang.management.ManagementFactory;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Properties;

/**
 * What Jasper's launcher reported about this JVM (spec section 3.4). {@link #current()} reads only system properties,
 * so it is safe on the start-up path; the running heap and the physical memory are read on demand.
 *
 * @param file the options file the launcher read, or would have read; empty when Jasper's launcher did not start this
 *             JVM (for example {@code :jasper-app:run})
 * @param digest the digest of the lines it applied; empty when it applied none
 * @param corrupted whether it ignored the file as corrupted
 * @param skipped why it skipped the file ({@code launch-failed} after the guard's fallback start)
 */
public record VmOptionsLaunch(Optional<Path> file, Optional<String> digest, boolean corrupted, Optional<String> skipped) {
    /** This JVM's launch. */
    public static VmOptionsLaunch current() { return of(System.getProperties()); }

    static VmOptionsLaunch of(Properties properties) {
        return new VmOptionsLaunch(present(properties, "jasper.vmOptionsFile").map(Path::of), present(properties, "jasper.vmOptionsDigest"),
            "true".equals(properties.getProperty("jasper.vmOptionsFile.corrupted")), present(properties, "jasper.vmOptionsSkipped"));
    }

    /** Whether Jasper's launcher started this JVM, so jasper.vmoptions applies to it. */
    public boolean launcherManaged() { return file.isPresent(); }

    /** The digest of the lines {@code parsed} applies, or empty when it applies none (corrupted or no option lines). */
    public static Optional<String> appliedDigest(VmOptionsSyntax.Parsed parsed) {
        return parsed.corrupted() || parsed.lines().isEmpty() ? Optional.empty() : Optional.of(VmOptionsSyntax.digest(parsed.lines()));
    }

    /**
     * Whether a restart would apply other options than this JVM's: {@code now} against the launch's digest. A skipped or
     * corrupted launch applied nothing on purpose, so it compares against {@code atStart}, the file as Jasper first read it.
     */
    public boolean restartNeeded(VmOptionsSyntax.Parsed now, VmOptionsSyntax.Parsed atStart) {
        Optional<String> baseline = skipped.isPresent() || corrupted ? appliedDigest(atStart) : digest;
        return !appliedDigest(now).equals(baseline);
    }

    /** This JVM's heap limit in MiB: the last {@code -Xmx} it was given, else the runtime's maximum. */
    public static long runningMaxHeapMiB() {
        return runningMaxHeapMiB(ManagementFactory.getRuntimeMXBean().getInputArguments(), Runtime.getRuntime().maxMemory());
    }

    static long runningMaxHeapMiB(List<String> inputArguments, long maxMemoryBytes) {
        return VmOptionsSyntax.lastMaxHeapMiB(inputArguments).orElse(maxMemoryBytes >> 20);
    }

    /** This computer's physical memory in MiB, when the JVM can tell. */
    public static OptionalLong physicalMemoryMiB() {
        if (ManagementFactory.getOperatingSystemMXBean() instanceof com.sun.management.OperatingSystemMXBean system) {
            long total = system.getTotalMemorySize();
            return total > 0 ? OptionalLong.of(total >> 20) : OptionalLong.empty();
        }
        return OptionalLong.empty();
    }

    private static Optional<String> present(Properties properties, String name) {
        String value = properties.getProperty(name);
        return value == null || value.isBlank() ? Optional.empty() : Optional.of(value);
    }
}
```

Create `jasper-app/src/main/java/dev/jasper/app/vmoptions/LauncherImage.java`:

```java
package dev.jasper.app.vmoptions;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The packaged app's launcher configuration ({@code <name>.cfg}, spec section 6.4), found from the launcher's path in
 * {@code jpackage.app-path}: {@code Contents/app} on macOS, {@code app} on Windows, {@code lib/app} on Linux. Read as
 * packaging-support.gradle's {@code launcherJavaOptions} and {@code launcherClasspath} do. Blocking file I/O.
 *
 * @param appDirectory the directory {@code $APPDIR} names
 * @param javaOptions its {@code java-options}, in order, with {@code $APPDIR} resolved
 * @param classpath its {@code app.classpath} entries, in order, with {@code $APPDIR} resolved
 */
public record LauncherImage(Path appDirectory, List<String> javaOptions, List<String> classpath) {
    /** The bundled heap limit when the configuration names none (jasper-app/build.gradle.kts). */
    public static final long DEFAULT_MAX_HEAP_MIB = 2048;

    public LauncherImage {
        javaOptions = List.copyOf(javaOptions);
        classpath = List.copyOf(classpath);
    }

    /** The image of the launcher at {@code appPath}; empty when there is none (a development run) or it cannot be read. */
    public static Optional<LauncherImage> locate(String appPath) {
        if (appPath == null || appPath.isBlank()) return Optional.empty();
        Path launcher = Path.of(appPath.strip()).toAbsolutePath().normalize();
        Path parent = launcher.getParent();
        if (parent == null || launcher.getFileName() == null) return Optional.empty();
        String name = launcher.getFileName().toString();
        if (name.toLowerCase(java.util.Locale.ROOT).endsWith(".exe")) name = name.substring(0, name.length() - 4);
        List<Path> candidates = List.of(parent.resolveSibling("app"), parent.resolve("app"), parent.resolveSibling("lib").resolve("app"));
        for (Path directory : candidates) {
            Path config = directory.resolve(name + ".cfg");
            if (!Files.isRegularFile(config)) continue;
            try {
                return Optional.of(parse(directory, Files.readAllLines(config, StandardCharsets.UTF_8)));
            } catch (IOException unreadable) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    static LauncherImage parse(Path appDirectory, List<String> lines) {
        String directory = appDirectory.toString();
        var options = new ArrayList<String>();
        var classpath = new ArrayList<String>();
        for (String line : lines) {
            if (line.startsWith("java-options=")) options.add(line.substring("java-options=".length()).replace("$APPDIR", directory));
            else if (line.startsWith("app.classpath=")) classpath.add(line.substring("app.classpath=".length()).replace("$APPDIR", directory));
        }
        return new LauncherImage(appDirectory, options, classpath);
    }

    /** The bundled heap limit: the last {@code -Xmx} in the configuration, else {@link #DEFAULT_MAX_HEAP_MIB}. */
    public long bundledMaxHeapMiB() { return VmOptionsSyntax.lastMaxHeapMiB(javaOptions).orElse(DEFAULT_MAX_HEAP_MIB); }

    /** Whether the configuration names an AOT cache (a trained image). */
    public boolean hasAotCache() { return javaOptions.stream().anyMatch(option -> option.startsWith("-XX:AOTCache=")); }
}
```

- [ ] **Step 4: Run them to see them pass**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.vmoptions.*' verifyApplicationArchitecture`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add jasper-app/src/main/java/dev/jasper/app/vmoptions jasper-app/src/test/java/dev/jasper/app/vmoptions
git commit -F - <<'EOF'
VM options: what this JVM was launched with and the bundled Jasper.cfg

VmOptionsLaunch reads the launcher's properties (only system properties,
safe on the start-up path) and tells when a restart would apply other
options; LauncherImage finds Jasper.cfg from jpackage.app-path on each OS.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

---

### Task 5: The check

**Files:**
- Create: `jasper-app/src/main/java/dev/jasper/app/vmoptions/VmOptionsCheck.java`
- Test: `jasper-app/src/test/java/dev/jasper/app/vmoptions/VmOptionsCheckTest.java`

**Interfaces:**
- Consumes: Tasks 2 and 4 (`VmOptionsSyntax.merge`, `LauncherImage.javaOptions/classpath/hasAotCache`).
- Produces: `VmOptionsCheck(Path java, LauncherImage image, Runner runner, Duration limit)`;
  `interface Runner { Outcome run(List<String> command, Duration limit) throws IOException; }`;
  `record Outcome(int exit, String output, boolean timedOut)`; `record Fatal(String message, boolean recoverable)`;
  `record Findings(List<String> unrecognized, Optional<Fatal> fatal, boolean cacheRejected)` with `Findings.NONE`;
  `Findings check(VmOptionsSyntax.Parsed parsed) throws IOException`; `static Path runtimeJava()`;
  `static Outcome runProcess(List<String> command, Duration limit) throws IOException`; package-private
  `static Fatal fatal(String output)`.

- [ ] **Step 1: Write the failing test**

Create `jasper-app/src/test/java/dev/jasper/app/vmoptions/VmOptionsCheckTest.java`:

```java
package dev.jasper.app.vmoptions;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

class VmOptionsCheckTest {
    private static final LauncherImage IMAGE = new LauncherImage(Path.of("/app"), List.of("-Xmx256m", "-XX:+IgnoreUnrecognizedVMOptions"),
        List.of("/app/jasper-app.jar"));
    private static final LauncherImage TRAINED = new LauncherImage(Path.of("/app"),
        List.of("-Xmx256m", "-XX:+IgnoreUnrecognizedVMOptions", "-XX:AOTCache=/app/jasper.aot"), List.of("/app/jasper-app.jar", "/app/b.jar"));
    private static final String RECOVERABLE = "Invalid maximum heap size: -Xmx3q\nError: Could not create the Java Virtual Machine.\n"
        + "Error: A fatal exception has occurred. Program will exit.\n";
    private static final String UNRECOVERABLE = "Error occurred during initialization of VM\n"
        + "Initial heap size set to a larger value than the maximum heap size\n";

    private final List<List<String>> runs = new ArrayList<>();

    private VmOptionsCheck check(LauncherImage image, Function<List<String>, VmOptionsCheck.Outcome> script) {
        return new VmOptionsCheck(Path.of("/java"), image, (command, limit) -> { runs.add(command); return script.apply(command); },
            Duration.ofSeconds(10));
    }

    private static VmOptionsSyntax.Parsed parsed(String text) { return VmOptionsSyntax.parse(text.getBytes(StandardCharsets.UTF_8)); }
    private static VmOptionsCheck.Outcome ok() { return new VmOptionsCheck.Outcome(0, "openjdk version \"25\"\n", false); }

    @Test void eachLineAloneFindsWhatTheJvmWouldIgnore() throws IOException {
        var findings = check(IMAGE, command -> command.contains("-XX:-IgnoreUnrecognizedVMOptions") && command.contains("-XX:+NoSuchFlag")
            ? new VmOptionsCheck.Outcome(1, "Unrecognized VM option 'NoSuchFlag'\nError: Could not create the Java Virtual Machine.\n", false)
            : ok()).check(parsed("-XX:+NoSuchFlag\n-Xmx1g\n"));
        assertThat(findings.unrecognized()).containsExactly("-XX:+NoSuchFlag");
        assertThat(findings.fatal()).isEmpty();
        assertThat(runs.getFirst()).containsExactly("/java", "-XX:-IgnoreUnrecognizedVMOptions", "-XX:+NoSuchFlag", "-version");
        assertThat(runs.get(2)).as("then the line-up as launched")
            .containsExactly("/java", "-Xmx256m", "-XX:+IgnoreUnrecognizedVMOptions", "-XX:+NoSuchFlag", "-Xmx1g", "-version");
    }

    @Test void aJvmThatRejectsTheOptionsIsRecoverable() throws IOException {
        var findings = check(IMAGE, command -> command.contains("-Xmx3q") ? new VmOptionsCheck.Outcome(1, RECOVERABLE, false) : ok())
            .check(parsed("-Xmx3q\n"));
        assertThat(findings.unrecognized()).isEmpty();
        assertThat(findings.fatal()).contains(new VmOptionsCheck.Fatal("Invalid maximum heap size: -Xmx3q", true));
    }

    @Test void hotSpotsOwnInitializationFailureIsNotRecoverable() throws IOException {
        var findings = check(IMAGE, command -> command.contains("-Xms4g") && !command.contains("-XX:-IgnoreUnrecognizedVMOptions")
            ? new VmOptionsCheck.Outcome(1, UNRECOVERABLE, false) : ok()).check(parsed("-Xms4g\n"));
        assertThat(findings.fatal()).contains(new VmOptionsCheck.Fatal("Initial heap size set to a larger value than the maximum heap size", false));
        assertThat(VmOptionsCheck.fatal("Error occurred during initialization of boot layer\njava.lang.module.FindException: Module jdk.nope not found\n"))
            .isEqualTo(new VmOptionsCheck.Fatal("java.lang.module.FindException: Module jdk.nope not found", false));
        assertThat(VmOptionsCheck.fatal("[0.010s][warning][aot] cache mismatch\n" + RECOVERABLE).message()).isEqualTo("Invalid maximum heap size: -Xmx3q");
    }

    @Test void aTrainedImageAlsoChecksTheCache() throws IOException {
        var findings = check(TRAINED, command -> command.contains("-XX:AOTMode=on") ? new VmOptionsCheck.Outcome(1, "Unable to use AOT cache.\n", false) : ok())
            .check(parsed("-XX:+UseZGC\n"));
        assertThat(findings.cacheRejected()).isTrue();
        assertThat(runs.getLast()).containsSubsequence("-XX:+UseZGC", "-XX:AOTMode=on", "-cp", "/app/jasper-app.jar" + java.io.File.pathSeparator + "/app/b.jar", "-version");
        runs.clear();
        assertThat(check(IMAGE, command -> ok()).check(parsed("-XX:+UseZGC\n")).cacheRejected()).isFalse();
        assertThat(runs).noneMatch(command -> command.contains("-XX:AOTMode=on"));
    }

    @Test void nothingToCheckRunsNothing() throws IOException {
        assertThat(check(IMAGE, command -> ok()).check(parsed("# only\nXmx300m\n"))).isEqualTo(VmOptionsCheck.Findings.NONE);
        assertThat(check(IMAGE, command -> ok()).check(VmOptionsSyntax.parse(new byte[] {'-', 0}))).isEqualTo(VmOptionsCheck.Findings.NONE);
        assertThat(runs).isEmpty();
    }

    @Test void aTimedOutRunFindsNothing() throws IOException {
        var findings = check(IMAGE, command -> new VmOptionsCheck.Outcome(-1, "", true)).check(parsed("-Xmx3q\n"));
        assertThat(findings).isEqualTo(VmOptionsCheck.Findings.NONE);
    }

    @Test void theRealJvmAgrees() throws IOException {
        var check = new VmOptionsCheck(VmOptionsCheck.runtimeJava(), IMAGE, VmOptionsCheck::runProcess, Duration.ofSeconds(30));
        var findings = check.check(parsed("-XX:+NoSuchFlag\n-Xmx3q\n"));
        assertThat(findings.unrecognized()).containsExactly("-XX:+NoSuchFlag");
        assertThat(findings.fatal()).contains(new VmOptionsCheck.Fatal("Invalid maximum heap size: -Xmx3q", true));
        assertThat(check.check(parsed("-Xms1g\n")).fatal())
            .contains(new VmOptionsCheck.Fatal("Initial heap size set to a larger value than the maximum heap size", false));
    }
}
```

- [ ] **Step 2: Run it to see it fail**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.vmoptions.VmOptionsCheckTest'`
Expected: FAIL — compilation error, `VmOptionsCheck` does not exist.

- [ ] **Step 3: Implement**

Create `jasper-app/src/main/java/dev/jasper/app/vmoptions/VmOptionsCheck.java`:

```java
package dev.jasper.app.vmoptions;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Runs the bundled {@code java} with the user's options (spec sections 5.1 and 6.4): each applied line alone with
 * unrecognized options made fatal, then the line-up as launched, then, in a trained image, with the AOT cache required.
 * Blocking: run it on a worker, never on the EDT.
 */
public final class VmOptionsCheck {
    /** Runs a command and returns its exit status and merged output, or a timeout. */
    @FunctionalInterface
    public interface Runner {
        /** Runs {@code command} for at most {@code limit}. */
        Outcome run(List<String> command, Duration limit) throws IOException;
    }

    /** A run's exit status and merged output; {@code timedOut} runs were killed. */
    public record Outcome(int exit, String output, boolean timedOut) { }

    /**
     * A JVM that would not start.
     *
     * @param message the JVM's own explanation
     * @param recoverable whether the launcher recovers at launch (spec 4.5): HotSpot's initialization failures end with
     *                    {@code _exit} and are not recoverable
     */
    public record Fatal(String message, boolean recoverable) { }

    /**
     * What the check found.
     *
     * @param unrecognized applied lines the JVM would ignore
     * @param fatal why the JVM would not start with the line-up, if it would not
     * @param cacheRejected whether the options turn off the trained AOT cache
     */
    public record Findings(List<String> unrecognized, Optional<Fatal> fatal, boolean cacheRejected) {
        /** Nothing found, or nothing checked. */
        public static final Findings NONE = new Findings(List.of(), Optional.empty(), false);

        public Findings { unrecognized = List.copyOf(unrecognized); }
    }

    private static final System.Logger LOG = System.getLogger(VmOptionsCheck.class.getName());

    private final Path java;
    private final LauncherImage image;
    private final Runner runner;
    private final Duration limit;

    /** A check with {@code java}, the image's bundled options, and at most {@code limit} per run. */
    public VmOptionsCheck(Path java, LauncherImage image, Runner runner, Duration limit) {
        this.java = Objects.requireNonNull(java, "java");
        this.image = Objects.requireNonNull(image, "image");
        this.runner = Objects.requireNonNull(runner, "runner");
        this.limit = Objects.requireNonNull(limit, "limit");
    }

    /** This runtime's own {@code java}: {@code <java.home>/bin/java}. */
    public static Path runtimeJava() {
        boolean windows = System.getProperty("os.name", "").startsWith("Windows");
        return Path.of(System.getProperty("java.home"), "bin", windows ? "java.exe" : "java");
    }

    /** Checks {@code parsed}'s applied lines; a corrupted file, or one without option lines, is not checked. */
    public Findings check(VmOptionsSyntax.Parsed parsed) throws IOException {
        if (parsed.corrupted() || parsed.lines().isEmpty()) return Findings.NONE;
        var unrecognized = new ArrayList<String>();
        for (String line : parsed.lines()) {
            Outcome alone = run(List.of(java.toString(), "-XX:-IgnoreUnrecognizedVMOptions", line, "-version"));
            if (!alone.timedOut() && alone.exit() != 0 && unrecognized(alone.output())) unrecognized.add(line);
        }
        List<String> options = VmOptionsSyntax.merge(image.javaOptions(), parsed.lines());
        Outcome merged = run(command(options, List.of("-version")));
        Optional<Fatal> fatal = !merged.timedOut() && merged.exit() != 0 ? Optional.of(fatal(merged.output())) : Optional.empty();
        boolean cacheRejected = false;
        if (image.hasAotCache() && fatal.isEmpty() && !merged.timedOut()) {
            Outcome cache = run(command(options, List.of("-XX:AOTMode=on", "-cp", String.join(File.pathSeparator, image.classpath()), "-version")));
            cacheRejected = !cache.timedOut() && cache.exit() != 0;
        }
        return new Findings(unrecognized, fatal, cacheRejected);
    }

    /** One run; a run over its limit finds nothing and is logged (spec section 7). */
    private Outcome run(List<String> command) throws IOException {
        Outcome outcome = runner.run(command, limit);
        if (outcome.timedOut()) LOG.log(System.Logger.Level.WARNING, "The VM options check timed out after {0}: {1}", limit, String.join(" ", command));
        return outcome;
    }

    /** Runs {@code command} as a process with its output merged, without the variables that add JVM options. */
    public static Outcome runProcess(List<String> command, Duration limit) throws IOException {
        var builder = new ProcessBuilder(command).redirectErrorStream(true);
        builder.environment().keySet().removeAll(List.of("JAVA_TOOL_OPTIONS", "_JAVA_OPTIONS", "JDK_JAVA_OPTIONS"));
        Process process = builder.start();
        var output = new ByteArrayOutputStream();
        Thread reader = Thread.ofVirtual().name("jasper-vmoptions-check").start(() -> {
            try (InputStream in = process.getInputStream()) {
                in.transferTo(output);
            } catch (IOException closed) {
                // The process was killed; what was read so far is enough.
            }
        });
        try {
            if (!process.waitFor(limit.toMillis(), TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                return new Outcome(-1, "", true);
            }
            reader.join(limit.toMillis());
        } catch (InterruptedException interrupted) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
            return new Outcome(-1, "", true);
        }
        synchronized (output) {
            return new Outcome(process.exitValue(), output.toString(StandardCharsets.UTF_8), false);
        }
    }

    /** Why a JVM did not start, from its output: the line after HotSpot's own marker, else the first message line. */
    static Fatal fatal(String output) {
        List<String> lines = output.lines().map(String::strip)
            .filter(line -> !line.isEmpty() && !line.startsWith("[") && !line.contains("VM warning:") && !line.startsWith("Picked up "))
            .toList();
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).startsWith("Error occurred during initialization of")) {
                return new Fatal(i + 1 < lines.size() ? lines.get(i + 1) : lines.get(i), false);
            }
        }
        String message = lines.stream()
            .filter(line -> !line.startsWith("Error: Could not create the Java Virtual Machine") && !line.startsWith("Error: A fatal exception has occurred"))
            .findFirst()
            .orElse("The JVM did not start.");
        return new Fatal(message, true);
    }

    private static boolean unrecognized(String output) {
        return output.contains("Unrecognized VM option") || output.contains("Unrecognized option:");
    }

    private List<String> command(List<String> options, List<String> tail) {
        var command = new ArrayList<String>();
        command.add(java.toString());
        command.addAll(options);
        command.addAll(tail);
        return command;
    }
}
```

- [ ] **Step 4: Run it to see it pass**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.vmoptions.*' verifyApplicationArchitecture`
Expected: BUILD SUCCESSFUL (the real-JVM test starts four short `java -version` processes).

- [ ] **Step 5: Commit**

```bash
git add jasper-app/src/main/java/dev/jasper/app/vmoptions/VmOptionsCheck.java jasper-app/src/test/java/dev/jasper/app/vmoptions/VmOptionsCheckTest.java
git commit -F - <<'EOF'
VM options: check the options with the bundled java

VmOptionsCheck runs each applied line alone with unrecognized options made
fatal, then the line-up as launched, then (trained images) with the AOT
cache required, and tells a failure the launcher recovers from apart from
HotSpot's own initialization failures (spec 4.5).

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

---

### Task 6: `SchemaPage` — external rows, notices and a refresh trigger

**Files:**
- Modify: `jasper-app/src/main/java/dev/jasper/app/settingsui/SchemaPage.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/settingsui/SettingsDialog.java` (`pendingCount`, `apply`)
- Test: `jasper-app/src/test/java/dev/jasper/app/settingsui/SchemaPageExternalTest.java` (new),
  `jasper-app/src/test/java/dev/jasper/app/settingsui/SettingsDialogTest.java` (one test)

**Interfaces:**
- Produces (public, in `SchemaPage`): `record External(String id, String label, String unit, List<String> keywords,
  Nodes.Numeric bounds, IntSupplier saved, IntSupplier defaultValue, IntFunction<String> error,
  IntFunction<String> comment, IntFunction<CompletableFuture<Void>> save, BooleanSupplier enabled)`;
  `record Notice(String title, String text)`; `Spec.external(External)`;
  `Spec.notice(String id, Supplier<Optional<Notice>> notice, Button... buttons)`;
  `Spec.refreshOn(Function<Runnable, Subscription> subscribe)`; `boolean externalModified()`; overrides of
  `reveal(String)` and `selected()`. Node ids: external row `"row:" + id`, its field `id` (Swing text field
  `id + ".input"`, error label `id + ".error"`), reset `id + ":reset"`; notice row `"notice-" + id`, its buttons
  `"notice-" + id + "-" + index`. Package-private for tests: `List<ExternalRow> externals()`, `String revealed()`,
  `ExternalRow.error()`, `ExternalRow.pending()`, `ExternalRow.highlighted()`.

- [ ] **Step 1: Write the failing tests**

Create `jasper-app/src/test/java/dev/jasper/app/settingsui/SchemaPageExternalTest.java`:

```java
package dev.jasper.app.settingsui;

import static dev.jasper.app.settingsui.PageKit.kit;
import static dev.jasper.app.settingsui.PageKit.node;
import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.app.config.ConfigLoader;
import dev.jasper.app.lifecycle.Subscription;
import dev.jasper.app.testsupport.EdtTestExtension;
import dev.jasper.ui.model.Nodes;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JTextField;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(EdtTestExtension.class)
class SchemaPageExternalTest {
    private final SettingsDraft draft = new SettingsDraft(ConfigLoader.parse(Path.of("config.toml"), "", true).values());
    private final PageContext context = PageKit.context(draft, id -> { }, () -> { });
    private final int[] stored = {2048};
    private final int[] restarts = {0};
    private final List<Integer> saves = new ArrayList<>();
    private final List<Runnable> listeners = new ArrayList<>();
    private CompletableFuture<Void> saveResult = CompletableFuture.completedFuture(null);
    private Optional<SchemaPage.Notice> notice = Optional.empty();
    private boolean enabled = true;

    private SchemaPage page() {
        return SchemaPage.page("system", "System Settings")
            .section("Java Virtual Machine")
            .external(new SchemaPage.External("vm.heap", "Maximum heap size", "MiB", List.of("memory", "xmx"),
                new Nodes.Numeric(512, 1_048_576, 256, 0), () -> stored[0], () -> 2048,
                value -> value < 512 ? "Use 512 MiB or more." : "", value -> "Showing " + value + " MiB.",
                value -> {
                    saves.add(value);
                    if (!saveResult.isCompletedExceptionally()) stored[0] = value;
                    return saveResult;
                }, () -> enabled))
            .notice("vm.restart", () -> notice, new SchemaPage.Button("Restart Now", () -> restarts[0]++, () -> true))
            .refreshOn(listener -> { listeners.add(listener); return new Subscription(() -> listeners.remove(listener)); })
            .build(context);
    }

    private static String description(SchemaPage page, String id) { return ((Nodes.SettingRow) node(page, id)).description(); }

    @Test void anExternalRowShowsItsSavedValueAndHoldsAnEdit() {
        var page = page();
        var field = kit(page, "vm.heap.input", JTextField.class);
        assertThat(field.getText()).isEqualTo("2048");
        assertThat(description(page, "row:vm.heap")).isEqualTo("Showing 2048 MiB.");
        field.setText("4096");
        assertThat(page.modified()).isTrue();
        assertThat(page.externalModified()).isTrue();
        assertThat(page.externals().getFirst().pending()).isEqualTo(4096);
        assertThat(description(page, "row:vm.heap")).as("the comment follows the value").isEqualTo("Showing 4096 MiB.");
        assertThat(saves).isEmpty();
        field.setText("2048");
        assertThat(page.modified()).isFalse();
    }

    @Test void belowTheMinimumOrNoNumberIsAnErrorThatBlocksApply() {
        var page = page();
        var field = kit(page, "vm.heap.input", JTextField.class);
        field.setText("256");
        assertThat(page.valid()).isFalse();
        assertThat(page.externals().getFirst().error()).isEqualTo("Use 512 MiB or more.");
        assertThat(kit(page, "vm.heap.error", JLabel.class).getText()).isEqualTo("Use 512 MiB or more.");
        field.setText("");
        assertThat(page.externals().getFirst().error()).isEqualTo("Not a valid value.");
        field.setText("1024");
        assertThat(page.valid()).isTrue();
        assertThat(page.externals().getFirst().pending()).isEqualTo(1024);
    }

    @Test void resetOffersTheDefaultAndPageResetDiscards() {
        stored[0] = 4096;
        var page = page();
        var field = kit(page, "vm.heap.input", JTextField.class);
        assertThat(field.getText()).isEqualTo("4096");
        kit(page, "vm.heap:reset", JButton.class).doClick();
        assertThat(field.getText()).isEqualTo("2048");
        assertThat(page.externals().getFirst().pending()).isEqualTo(2048);
        page.reset();
        assertThat(field.getText()).isEqualTo("4096");
        assertThat(page.modified()).isFalse();
    }

    @Test void applySavesTheEditAndThenShowsTheSavedValue() {
        var page = page();
        kit(page, "vm.heap.input", JTextField.class).setText("4096");
        assertThat(page.apply()).isCompleted();
        assertThat(saves).containsExactly(4096);
        assertThat(page.modified()).isFalse();
        assertThat(kit(page, "vm.heap.input", JTextField.class).getText()).isEqualTo("4096");
    }

    @Test void aFailedSaveKeepsTheEdit() {
        saveResult = CompletableFuture.failedFuture(new IOException("Could not save jasper.vmoptions: Permission denied"));
        var page = page();
        kit(page, "vm.heap.input", JTextField.class).setText("4096");
        assertThat(page.apply()).isCompletedExceptionally();
        assertThat(page.modified()).isTrue();
        assertThat(kit(page, "vm.heap.input", JTextField.class).getText()).isEqualTo("4096");
        assertThat(page.externals().getFirst().error()).isEqualTo("Could not save jasper.vmoptions: Permission denied");
        assertThat(page.valid()).as("a failed save is no invalid value: Apply can retry").isTrue();
    }

    @Test void aDisabledExternalRowShowsButCannotBeEdited() {
        enabled = false;
        var page = page();
        assertThat(((Nodes.Field) node(page, "vm.heap")).state().enabled()).isFalse();
    }

    @Test void aNoticeShowsWhilePresentAndRefreshOnRendersIt() {
        var page = page();
        page.component();
        assertThat(((Nodes.SettingRow) node(page, "notice-vm.restart")).state().visible()).isFalse();
        notice = Optional.of(new SchemaPage.Notice("Restart required", "Restart Jasper to apply your changes."));
        listeners.forEach(Runnable::run);
        var row = (Nodes.SettingRow) node(page, "notice-vm.restart");
        assertThat(row.state().visible()).isTrue();
        assertThat(row.description()).isEqualTo("Restart Jasper to apply your changes.");
        kit(page, "notice-vm.restart-0", JButton.class).doClick();
        assertThat(restarts[0]).isEqualTo(1);
        page.dispose();
        assertThat(listeners).as("the page closes its refresh subscription").isEmpty();
    }

    @Test void revealThenSelectedFocusesTheExternalRow() {
        var page = page();
        page.component();
        page.reveal("vm.heap");
        assertThat(page.revealed()).isEqualTo("vm.heap");
        page.selected();
        assertThat(page.revealed()).isNull();
    }

    @Test void searchFindsTheExternalRow() {
        var page = page();
        page.component();
        assertThat(page.searchTerms()).contains("Maximum heap size");
        page.highlight("xmx");
        assertThat(page.externals().getFirst().highlighted()).isTrue();
    }
}
```

In `jasper-app/src/test/java/dev/jasper/app/settingsui/SettingsDialogTest.java`, add this test after
`applySavesTheDraftThenOtherPagesAndOkCloses`:

```java
    @Test void anExternalRowsEditIsAppliedWithoutWritingConfigToml() throws Exception {
        var saves = new CopyOnWriteArrayList<Integer>();
        int[] stored = {2048};
        var dialog = new SettingsDialog(windows, context -> List.of(SchemaPage.page("vm", "VM")
            .external(new SchemaPage.External("vm.heap", "Maximum heap size", "MiB", List.of("heap"), new Nodes.Numeric(512, 1_048_576, 256, 0),
                () -> stored[0], () -> 2048, value -> value < 512 ? "Use 512 MiB or more." : "", value -> "",
                value -> { saves.add(value); stored[0] = value; return CompletableFuture.completedFuture(null); }, () -> true))
            .build(context)), hooks());
        edt(() -> {
            dialog.open(null, "vm");
            KitTestSupport.require(page(dialog), "vm.heap.input", javax.swing.JTextField.class).setText("4096");
            assertThat(button(dialog, "apply").isEnabled()).isTrue();
            dialog.apply();
        });
        until(() -> saves.equals(List.of(4096)));
        edt(() -> {
            assertThat(saved).as("an external edit leaves config.toml alone").isEmpty();
            dialog.refreshNow();
            assertThat(button(dialog, "apply").isEnabled()).isFalse();
        });
    }
```

- [ ] **Step 2: Run them to see them fail**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.settingsui.SchemaPageExternalTest' --tests 'dev.jasper.app.settingsui.SettingsDialogTest'`
Expected: FAIL — compilation errors (`SchemaPage.External`, `Spec.external` and the rest do not exist).

- [ ] **Step 3: Implement in `SchemaPage`**

All edits are in `jasper-app/src/main/java/dev/jasper/app/settingsui/SchemaPage.java`.

(a) Imports: add after `import java.util.function.BooleanSupplier;`:

```java
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.IntSupplier;
```

(b) After the `Button` record, add:

```java
    /**
     * A number row whose value lives outside config.toml, read, checked and saved through these hooks (spec section 5.1:
     * the heap limit in jasper.vmoptions). {@code saved} and {@code defaultValue} are read again on every refresh and
     * reset; {@code error} blocks Apply while it is non-empty; {@code comment} is the row's description for a value, read
     * on every render; {@code save} runs on Apply.
     */
    public record External(String id, String label, String unit, List<String> keywords, Nodes.Numeric bounds,
                           IntSupplier saved, IntSupplier defaultValue, IntFunction<String> error, IntFunction<String> comment,
                           IntFunction<CompletableFuture<Void>> save, BooleanSupplier enabled) {
        public External { keywords = List.copyOf(keywords); }
    }

    /** What a notice row shows while present: a title and a text, beside the row's buttons. */
    public record Notice(String title, String text) { }
```

(c) Replace the `Item` declarations:

```java
    private sealed interface Item permits RowItem, SectionItem, ActionItem { }
```

with:

```java
    private sealed interface Item permits RowItem, SectionItem, ActionItem, ExternalItem, NoticeItem { }
```

and after `private record ActionItem(...)` add:

```java
    private record ExternalItem(External value) implements Item { }
    private record NoticeItem(String id, Supplier<Optional<Notice>> notice, List<Button> buttons) implements Item { }
```

(d) In `Spec`, after `private final List<Item> items = new ArrayList<>();` add:

```java
        private final List<Function<Runnable, Subscription>> refreshers = new ArrayList<>();
```

and after the `action(...)` method add:

```java
        /** A row whose value lives outside config.toml (see {@link External}). */
        public Spec external(External value) { items.add(new ExternalItem(Objects.requireNonNull(value, "value"))); return this; }

        /** A row shown only while {@code notice} supplies one (read on every render), with {@code buttons} at its end. */
        public Spec notice(String id, Supplier<Optional<Notice>> notice, Button... buttons) {
            items.add(new NoticeItem(Objects.requireNonNull(id, "id"), Objects.requireNonNull(notice, "notice"), List.of(buttons)));
            return this;
        }

        /** Refreshes the page whenever {@code subscribe}'s listener runs (on the EDT); the page closes the subscription when disposed. */
        public Spec refreshOn(Function<Runnable, Subscription> subscribe) { refreshers.add(Objects.requireNonNull(subscribe, "subscribe")); return this; }
```

(e) After `private record ActionRow(...) { ... }` add:

```java
    /** An external row's state; package-private for tests. */
    static final class ExternalRow {
        private final External value;
        private final Editors.NumberField<Integer> editor;
        private final String searchText;
        private Integer pending;
        private boolean invalid;
        private String error = "";
        private boolean highlighted;

        ExternalRow(External value) {
            this.value = value;
            editor = new Editors.NumberField<>(value.label(), value.bounds(), value.unit(), Integer::valueOf, Nodes.FieldPresentation.STEPPER);
            searchText = String.join(" ", value.label(), String.join(" ", value.keywords()));
        }

        String id() { return value.id(); }
        String rowId() { return "row:" + value.id(); }
        String resetId() { return value.id() + ":reset"; }
        Integer pending() { return pending; }
        String error() { return error; }
        boolean highlighted() { return highlighted; }
        /** The value the row stands for: the pending edit, else the saved value. */
        int shown() { return pending != null ? pending : value.saved().getAsInt(); }
    }
```

(f) After `private final List<ActionRow> actions = new ArrayList<>();` add:

```java
    private final List<ExternalRow> externals = new ArrayList<>();
    private final List<Subscription> refreshing = new ArrayList<>();
    private String revealed;
```

(g) In `searchTerms()`, replace:

```java
            } else if (item instanceof SectionItem section && sectionShown(section)) terms.add(section.title());
```

with:

```java
            } else if (item instanceof ExternalItem external) {
                terms.add(external.value().label());
                terms.add(String.join(" ", external.value().keywords()));
            } else if (item instanceof SectionItem section && sectionShown(section)) terms.add(section.title());
```

(h) In `sectionShown(...)`, replace `if (item instanceof ActionItem) return true;` with:

```java
            if (item instanceof ActionItem || item instanceof ExternalItem || item instanceof NoticeItem) return true;
```

(i) After `KitView view() { return view; }` add:

```java
    /** The external rows, in page order; for tests. */
    List<ExternalRow> externals() { return externals; }
    /** The row {@link #reveal} asked for, until {@link #selected} focuses it; for tests. */
    String revealed() { return revealed; }
```

(j) In `component()`, replace:

```java
        for (Item item : spec.items) {
            if (item instanceof RowItem row && row.shown()) addRow(row);
            else if (item instanceof ActionItem action) actions.add(new ActionRow(action, actionIndex++));
        }
        listening = context.draft().onChanged(this::showValues);
        showValues(false);
```

with:

```java
        for (Item item : spec.items) {
            if (item instanceof RowItem row && row.shown()) addRow(row);
            else if (item instanceof ActionItem action) actions.add(new ActionRow(action, actionIndex++));
            else if (item instanceof ExternalItem external) externals.add(new ExternalRow(external.value()));
        }
        listening = context.draft().onChanged(this::showValues);
        for (var subscribe : spec.refreshers) refreshing.add(subscribe.apply(() -> { if (!disposed) refresh(); }));
        for (var row : externals) row.editor.show(row.shown());
        showValues(false);
```

(k) In `viewSpec()`, replace `int sections = 0, rowIndex = 0, actionIndex = 0;` with
`int sections = 0, rowIndex = 0, actionIndex = 0, externalIndex = 0;` and after
`case ActionItem ignored -> group.add(actionNode(actions.get(actionIndex++)));` add:

```java
                case ExternalItem ignored -> group.add(externalNode(externals.get(externalIndex++)));
                case NoticeItem notice -> group.add(noticeNode(notice));
```

(l) After `actionNode(...)` add:

```java
    private Nodes.Node externalNode(ExternalRow row) {
        boolean enabled = row.value.enabled().getAsBoolean();
        var state = new ControlState(true, enabled, false, row.error);
        boolean resettable = enabled && row.shown() != row.value.defaultValue().getAsInt();
        var reset = new Nodes.Button(row.resetId(), new ControlState(resettable, true, false, ""), "", "reset",
            Nodes.ButtonKind.GHOST, row.resetId(), "Reset " + row.value.label() + " to default");
        var control = new Nodes.Stack(row.id() + ":controls", ControlState.READY, Nodes.LayoutRole.SETTINGS_TRAILING,
            List.of(row.editor.node(row.id(), state), reset));
        return new Nodes.SettingRow(row.rowId(), ControlState.READY, row.value.label(), row.value.comment().apply(row.shown()),
            control, false, row.highlighted);
    }

    /** A notice row: hidden, with its buttons, while its supplier has nothing to say. */
    private Nodes.Node noticeNode(NoticeItem item) {
        Optional<Notice> notice = item.notice().get();
        var hidden = new ControlState(false, true, false, "");
        var buttons = new ArrayList<Nodes.Node>();
        for (int i = 0; i < item.buttons().size(); i++) {
            var button = item.buttons().get(i);
            boolean enabled = button.enabled().getAsBoolean();
            buttons.add(new Nodes.Button(noticeButtonId(item, i), notice.isPresent() ? new ControlState(true, enabled, false, "") : hidden,
                button.label(), "", Nodes.ButtonKind.SECONDARY, noticeButtonId(item, i), enabled ? "" : button.whenDisabled()));
        }
        var control = new Nodes.Stack("notice-" + item.id() + ":buttons", ControlState.READY, Nodes.LayoutRole.INLINE, buttons);
        // A row's title must not be blank, even hidden: the id stands in.
        return new Nodes.SettingRow("notice-" + item.id(), notice.isPresent() ? ControlState.READY : hidden,
            notice.map(Notice::title).orElse(item.id()), notice.map(Notice::text).orElse(""), control, false, false);
    }

    private static String noticeButtonId(NoticeItem item, int index) { return "notice-" + item.id() + "-" + index; }
```

(m) Replace the whole `event(UiEvent event)` method with:

```java
    /** Routes a kit event to the row, action or notice it names. */
    void event(UiEvent event) {
        if (disposed) return;
        if (event instanceof UiEvent.Action action) {
            for (var row : rows) if (action.id().equals(row.resetId())) { reset(row); return; }
            for (var row : externals) if (action.id().equals(row.resetId())) { resetExternal(row); return; }
            for (var actionRow : actions)
                for (int i = 0; i < actionRow.item().buttons().size(); i++)
                    if (action.id().equals(actionRow.buttonId(i))) { actionRow.item().buttons().get(i).action().run(); render(); return; }
            for (Item item : spec.items)
                if (item instanceof NoticeItem notice)
                    for (int i = 0; i < notice.buttons().size(); i++)
                        if (action.id().equals(noticeButtonId(notice, i))) { notice.buttons().get(i).action().run(); render(); return; }
        }
        for (var row : rows) {
            if (row.editor.event(row.id(), event)) { edited(row); render(); return; }
        }
        for (var row : externals) {
            if (row.editor.event(row.id(), event)) { editedExternal(row); render(); return; }
        }
        render();
    }

    private void editedExternal(ExternalRow row) {
        int value;
        try {
            value = row.editor.value();
        } catch (RuntimeException unreadable) {
            invalidExternal(row, "Not a valid value.");
            return;
        }
        String problem = row.value.error().apply(value);
        if (!problem.isEmpty()) { invalidExternal(row, problem); return; }
        row.invalid = false;
        row.error = "";
        row.pending = value == row.value.saved().getAsInt() ? null : value;
        context.changed().run();
    }

    private void invalidExternal(ExternalRow row, String message) {
        row.invalid = true;
        row.error = message;
        context.changed().run();
    }

    private void resetExternal(ExternalRow row) {
        row.invalid = false;
        row.error = "";
        int target = row.value.defaultValue().getAsInt();
        row.pending = target == row.value.saved().getAsInt() ? null : target;
        row.editor.show(target);
        context.changed().run();
        render();
    }
```

(n) Replace `modified()`, `valid()`, `reset()` and `apply()` with:

```java
    @Override public boolean modified() {
        return spec.settings().stream().anyMatch(context.draft()::modified) || externalModified();
    }

    /** Whether an external row holds an edit to save; the dialog applies such a page after the draft. */
    public boolean externalModified() { return externals.stream().anyMatch(row -> row.pending != null); }

    @Override public boolean valid() {
        return rows.stream().noneMatch(row -> row.invalid) && externals.stream().noneMatch(row -> row.invalid);
    }

    @Override public void reset() {
        for (Row<?> row : rows) { row.invalid = false; row.error = ""; }
        for (var row : externals) {
            row.invalid = false;
            row.error = "";
            row.pending = null;
            row.editor.show(row.shown());
        }
        context.draft().reset(spec.settings());
        showValues();
    }

    /**
     * Saves the external rows' edits in page order; config.toml rows are saved by the dialog's draft. A failed save keeps
     * the edit and shows the failure under its row (spec section 7); the dialog shows it too.
     */
    @Override public CompletableFuture<Void> apply() {
        CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);
        for (var row : externals) {
            Integer value = row.pending;
            if (value == null) continue;
            chain = chain.thenCompose(ignored -> row.value.save().apply(value).whenComplete((saved, failure) -> {
                if (failure != null) onEdt(() -> { row.error = message(failure); render(); });
            }));
        }
        var done = new CompletableFuture<Void>();
        chain.whenComplete((ignored, failure) -> onEdt(() -> {
            if (failure != null) {
                done.completeExceptionally(failure);
                return;
            }
            for (var row : externals) { row.pending = null; row.error = ""; }
            refresh();
            done.complete(null);
        }));
        return done;
    }

    private static String message(Throwable failure) {
        Throwable cause = failure instanceof java.util.concurrent.CompletionException && failure.getCause() != null ? failure.getCause() : failure;
        return String.valueOf(cause.getMessage());
    }

    private static void onEdt(Runnable action) {
        if (javax.swing.SwingUtilities.isEventDispatchThread()) action.run();
        else javax.swing.SwingUtilities.invokeLater(action);
    }
```

(o) Replace the whole `highlight(String query)` method with:

```java
    /** Highlights the rows matching {@code query} and scrolls the first into view, leaving focus with the search. */
    @Override public void highlight(String query) {
        String first = null;
        for (Row<?> row : rows) {
            row.highlighted = SearchText.matches(row.searchText, query);
            if (row.highlighted && first == null) first = row.editor.fullSpan() ? row.editor.revealTarget(row.id()) : row.rowId();
        }
        for (var row : externals) {
            row.highlighted = SearchText.matches(row.searchText, query);
            if (row.highlighted && first == null) first = row.rowId();
        }
        render();
        if (first != null && view != null && !view.isClosed()) view.reveal(first);
    }
```

(p) Replace the whole `refresh()` method with:

```java
    @Override public void refresh() {
        for (Row<?> row : rows) row.editor.refresh();
        for (var row : externals) if (row.pending == null && !row.invalid) row.editor.show(row.value.saved().getAsInt());
        if (body != null) showValues();
    }

    /** Remembers the row to focus once the dialog shows this page; {@code fragment} is an external row's id. */
    @Override public void reveal(String fragment) { revealed = fragment; }

    /** Focuses the revealed row once the page's view exists; until then the request waits. */
    @Override public void selected() {
        if (revealed == null || view == null || view.isClosed()) return;
        String target = revealed;
        revealed = null;
        for (var row : externals) {
            if (row.id().equals(target)) {
                view.reveal(row.rowId());
                view.focus(row.id());
                return;
            }
        }
    }
```

(q) In `dispose()`, after `if(listening!=null)cleanup.add(listening::close);` add:

```java
        for(var subscription:refreshing)cleanup.add(subscription::close);
```

- [ ] **Step 4: Implement in `SettingsDialog`**

In `jasper-app/src/main/java/dev/jasper/app/settingsui/SettingsDialog.java`, replace:

```java
    private int pendingCount() {
        return draft.size() + (int) pages.stream().filter(page -> !(page instanceof SchemaPage) && page.modified()).count();
    }
```

with:

```java
    private int pendingCount() {
        return draft.size() + (int) pages.stream().filter(page -> appliedAfterDraft(page) && page.modified()).count();
    }

    /** Pages the dialog applies after the draft: every page but a schema page, which applies only its external rows. */
    private static boolean appliedAfterDraft(SettingsPage page) {
        return !(page instanceof SchemaPage schema) || schema.externalModified();
    }
```

and in `apply(Runnable done)` replace:

```java
            for (SettingsPage page : pages) if (!(page instanceof SchemaPage) && page.modified()) remaining.add(page);
```

with:

```java
            for (SettingsPage page : pages) if (appliedAfterDraft(page) && page.modified()) remaining.add(page);
```

- [ ] **Step 5: Run the settings tests to see them pass**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.settingsui.*'`
Expected: BUILD SUCCESSFUL. If `aNoticeShowsWhilePresentAndRefreshOnRendersIt` cannot find the hidden button by name,
the kit does not mount invisible controls; then look the button up only after the notice is shown (as the test does)
and keep the rest unchanged.

- [ ] **Step 6: Commit**

```bash
git add jasper-app/src/main/java/dev/jasper/app/settingsui/SchemaPage.java jasper-app/src/main/java/dev/jasper/app/settingsui/SettingsDialog.java jasper-app/src/test/java/dev/jasper/app/settingsui/SchemaPageExternalTest.java jasper-app/src/test/java/dev/jasper/app/settingsui/SettingsDialogTest.java
git commit -F - <<'EOF'
Settings: rows whose value lives outside config.toml, and notices

SchemaPage gains external number rows (read, checked and saved through
hooks; applied by the dialog after the draft), notice rows shown only while
present, a refresh trigger, and focusing a row the dialog reveals.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

---

### Task 7: The System Settings section

**Files:**
- Create: `jasper-app/src/main/java/dev/jasper/app/settingsui/VmOptionsHooks.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/settingsui/CorePages.java` (`SystemActions`, the system page)
- Modify: every `new CorePages.SystemActions(` / `new dev.jasper.app.settingsui.CorePages.SystemActions(` call
- Test: `jasper-app/src/test/java/dev/jasper/app/settingsui/CorePagesTest.java` (one test)

**Interfaces:**
- Consumes: Task 6's `SchemaPage.External`, `SchemaPage.Notice`, `Spec.external/notice/refreshOn`.
- Produces: `record VmOptionsHooks(SchemaPage.External heap, Supplier<String> file, Runnable edit, Runnable reveal,
  BooleanSupplier editable, Supplier<Optional<SchemaPage.Notice>> state, Supplier<Optional<SchemaPage.Notice>> findings,
  Supplier<Optional<SchemaPage.Notice>> restart, Runnable restartNow, Function<Runnable, Subscription> onChanged)`;
  `CorePages.SystemActions(..., BooleanSupplier migratable, Optional<VmOptionsHooks> vm)`; notice ids `vm.state`,
  `vm.findings`, `vm.restart`; the heap row id `vm.heap` (from the hooks).

- [ ] **Step 1: Write the failing test**

In `jasper-app/src/test/java/dev/jasper/app/settingsui/CorePagesTest.java`, add (with imports
`dev.jasper.app.config.ConfigLoader`, `dev.jasper.app.lifecycle.Subscription`, `dev.jasper.ui.model.Nodes`,
`java.util.concurrent.CompletableFuture`, `javax.swing.JButton` if missing):

```java
    @Test void systemSettingsHasTheJavaVirtualMachineSectionWhenTheApplicationSuppliesIt() {
        int[] restarted = {0};
        var vm = new VmOptionsHooks(new SchemaPage.External("vm.heap", "Maximum heap size", "MiB", List.of("memory"),
                new Nodes.Numeric(512, 1_048_576, 256, 0), () -> 2048, () -> 2048, value -> "", value -> "Current: 2048 MiB.",
                value -> CompletableFuture.completedFuture(null), () -> false),
            () -> "Applies to the installed Jasper app.", () -> { }, () -> { }, () -> false,
            () -> Optional.of(new SchemaPage.Notice("Applies to the installed app", "Custom VM options apply to the installed Jasper app.")),
            Optional::empty,
            () -> Optional.of(new SchemaPage.Notice("Restart required", "Restart Jasper to apply your changes.")),
            () -> restarted[0]++, listener -> new Subscription(() -> { }));
        var system = new CorePages.SystemActions(() -> Path.of("/fixture/config.toml"), () -> { }, () -> { }, () -> { }, () -> { }, () -> false,
            Optional.of(vm));
        var pageContext = PageKit.context(new SettingsDraft(ConfigLoader.parse(Path.of("config.toml"), "", true).values()), id -> { }, () -> { });
        var page = CorePages.specs(AppearanceFixtures.CHOICES, system).stream().filter(spec -> spec.id().equals("system"))
            .findFirst().orElseThrow().build(pageContext);
        assertThat(PageKit.sectionTitles(page)).containsSubsequence("Java Virtual Machine", "Configuration file");
        assertThat(((Nodes.Field) PageKit.node(page, "vm.heap")).state().enabled()).isFalse();
        assertThat(((Nodes.SettingRow) PageKit.node(page, "notice-vm.state")).description())
            .isEqualTo("Custom VM options apply to the installed Jasper app.");
        assertThat(((Nodes.SettingRow) PageKit.node(page, "notice-vm.findings")).state().visible()).isFalse();
        PageKit.kit(page, "notice-vm.restart-0", JButton.class).doClick();
        assertThat(restarted[0]).isEqualTo(1);
    }
```

- [ ] **Step 2: Run it to see it fail**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.settingsui.CorePagesTest'`
Expected: FAIL — compilation errors (`VmOptionsHooks` does not exist; `SystemActions` has six components).

- [ ] **Step 3: Implement**

Create `jasper-app/src/main/java/dev/jasper/app/settingsui/VmOptionsHooks.java`:

```java
package dev.jasper.app.settingsui;

import dev.jasper.app.lifecycle.Subscription;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * What the System Settings page's Java Virtual Machine section shows and does (spec section 5.1); the application
 * supplies it. EDT only.
 *
 * @param heap the Maximum heap size row
 * @param file the Custom VM options row's description: the file's path, or why it does not apply
 * @param edit opens the file in the user's editor, creating it first
 * @param reveal shows the file in the file manager
 * @param editable whether the file applies (Jasper's launcher started this JVM); the buttons follow it
 * @param state why the options did not apply this time, or that they apply only to the installed app
 * @param findings what the check found
 * @param restart that a restart would apply other options
 * @param restartNow restarts Jasper
 * @param onChanged runs the listener whenever any of these may have changed; subscribing also reads and checks the file
 */
public record VmOptionsHooks(SchemaPage.External heap, Supplier<String> file, Runnable edit, Runnable reveal, BooleanSupplier editable,
                             Supplier<Optional<SchemaPage.Notice>> state, Supplier<Optional<SchemaPage.Notice>> findings,
                             Supplier<Optional<SchemaPage.Notice>> restart, Runnable restartNow,
                             Function<Runnable, Subscription> onChanged) { }
```

In `CorePages.java`:

1. Replace the `SystemActions` record and its javadoc with:

```java
    /**
     * What the System Settings page's buttons do.
     *
     * @param file the configuration file's path
     * @param openInEditor opens it in the user's editor
     * @param reveal shows it in the file manager
     * @param reload reads it again
     * @param migrate runs Migrate Config
     * @param migratable whether Migrate Config has work
     * @param vm the Java Virtual Machine section; empty leaves the section out (tests and previews)
     */
    public record SystemActions(Supplier<Path> file, Runnable openInEditor, Runnable reveal, Runnable reload, Runnable migrate,
                                BooleanSupplier migratable, Optional<VmOptionsHooks> vm) { }
```

2. In `specs(..., NotificationGroupChoices groups)`, replace the inline system page entry (from
   `SchemaPage.page("system", "System Settings")` through the `Migrate…` button line ending
   `system.migratable(), "Nothing to migrate")),`) with:

```java
            systemPage(system),
```

3. Add this method below `customShell(...)`:

```java
    /** System Settings: Run in background, the Java Virtual Machine section when supplied, then the configuration file. */
    private static SchemaPage.Spec systemPage(SystemActions system) {
        var page = SchemaPage.page("system", "System Settings").parent("appearance-behavior").anchor("background-residency")
            .row(SYSTEM_RUN_IN_BACKGROUND);
        system.vm().ifPresent(vm -> page.section("Java Virtual Machine")
            .notice("vm.state", vm.state())
            .external(vm.heap())
            .action("Custom VM options", vm.file(),
                new SchemaPage.Button("Edit Custom VM Options…", vm.edit(), vm.editable(), "Custom VM options apply to the installed Jasper app"),
                new SchemaPage.Button("Reveal", vm.reveal(), vm.editable(), "Custom VM options apply to the installed Jasper app"))
            .notice("vm.findings", vm.findings())
            .notice("vm.restart", vm.restart(), new SchemaPage.Button("Restart Now", vm.restartNow(), () -> true))
            .refreshOn(vm.onChanged()));
        return page.section("Configuration file")
            .action("Location", () -> system.file().get().toString(),
                new SchemaPage.Button("Open in Editor", system.openInEditor(), () -> true),
                new SchemaPage.Button("Reveal", system.reveal(), () -> true))
            .action("Reload from disk", () -> "Read the file again after editing it elsewhere.",
                new SchemaPage.Button("Reload", system.reload(), () -> true))
            .action("Migrate old settings", () -> "Rewrite old setting names to the current layout; the original is backed up first.",
                new SchemaPage.Button("Migrate…", system.migrate(), system.migratable(), "Nothing to migrate"));
    }
```

4. Every other `SystemActions` construction gains `java.util.Optional.empty()` as its last argument. Find them with
   `grep -rn "CorePages.SystemActions(" jasper-app/src` — the main one is in
   `jasper-app/src/main/java/dev/jasper/app/application/JasperApplication.java` (Task 9 replaces its empty with the real
   hooks); the test and preview ones are in `NativeThemeNavigationTest`, `AppearanceSelectionTest`,
   `SettingsDialogTest` (nine), `CorePagesTest` (two), `Settings9bPreview`, `PluginSettingsPagesEvidenceTest`,
   `SettingsSurfacePreview`, `SettingsDialogPreview`, `AppearancePageTest` and `SettingsCompositionTest`, all under
   `jasper-app/src/test/java/dev/jasper/app/settingsui/`.

- [ ] **Step 4: Run the tests to see them pass**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.settingsui.*' --tests 'dev.jasper.app.application.*'`
Expected: BUILD SUCCESSFUL (existing System Settings tests are unchanged: they pass no VM section).

- [ ] **Step 5: Commit**

```bash
git add jasper-app/src
git commit -F - <<'EOF'
Settings: the Java Virtual Machine section on System Settings

VmOptionsHooks carries what the section shows and does; System Settings
builds the heap row, Edit Custom VM Options... and Reveal, and the state,
findings and restart notices from it, between Run in background and the
configuration file.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

---

### Task 8: The feature and its notices

**Files:**
- Create: `jasper-app/src/main/java/dev/jasper/app/application/VmOptionsNotices.java`
- Create: `jasper-app/src/main/java/dev/jasper/app/application/VmOptionsFeature.java`
- Test: `jasper-app/src/test/java/dev/jasper/app/application/VmOptionsFeatureTest.java`

**Interfaces:**
- Consumes: Tasks 2–5 (`VmOptionsSyntax`, `VmOptionsFile`, `VmOptionsLaunch`, `LauncherImage`, `VmOptionsCheck`),
  Task 7's `VmOptionsHooks`, Task 6's `SchemaPage.External/Notice`.
- Produces: `VmOptionsNotices.GROUP` (`jasper.vmoptions`); `VmOptionsFeature(VmOptionsLaunch launch,
  Supplier<Optional<LauncherImage>> images, Function<LauncherImage, VmOptionsCheck> checks, Executor worker,
  Consumer<Runnable> ui, NotificationCenter center, BooleanSupplier restart, Consumer<Path> openInEditor,
  Consumer<Path> reveal, LongSupplier runningHeapMiB, Supplier<OptionalLong> physicalMemoryMiB, Duration watchInterval)`;
  methods `VmOptionsHooks hooks()`, `boolean editable()`, `void editCustomOptions()`, `void startupSettled()`,
  `void settingsClosed()`, `void restartNow()`; package-private `void reload()`, `void pollFile()`.

- [ ] **Step 1: Write the failing test**

Create `jasper-app/src/test/java/dev/jasper/app/application/VmOptionsFeatureTest.java`:

```java
package dev.jasper.app.application;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.app.notifications.Delivery;
import dev.jasper.app.notifications.DisplayMode;
import dev.jasper.app.notifications.NotificationAction;
import dev.jasper.app.notifications.NotificationCenter;
import dev.jasper.app.testsupport.EdtTestExtension;
import dev.jasper.app.vmoptions.LauncherImage;
import dev.jasper.app.vmoptions.VmOptionsCheck;
import dev.jasper.app.vmoptions.VmOptionsFile;
import dev.jasper.app.vmoptions.VmOptionsLaunch;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

@ExtendWith(EdtTestExtension.class)
class VmOptionsFeatureTest {
    @TempDir Path home;
    private final NotificationCenter center = new NotificationCenter(Runnable::run, Clock.systemUTC(), () -> Delivery.Presence.FOCUSED);
    private final List<Path> opened = new ArrayList<>();
    private final List<Path> revealed = new ArrayList<>();
    private Function<List<String>, VmOptionsCheck.Outcome> script = command -> new VmOptionsCheck.Outcome(0, "", false);
    private boolean restartWorks = true;
    private int restarts;

    private Path file() { return home.resolve("jasper.vmoptions"); }
    private VmOptionsLaunch launch(boolean corrupted, Optional<String> skipped) { return new VmOptionsLaunch(Optional.of(file()), Optional.empty(), corrupted, skipped); }
    private VmOptionsLaunch managed() { return launch(false, Optional.empty()); }

    private VmOptionsFeature feature(VmOptionsLaunch launch, boolean image) {
        var launcherImage = new LauncherImage(home.resolve("app"), List.of("-Xmx2048m", "-XX:+IgnoreUnrecognizedVMOptions"), List.of());
        return new VmOptionsFeature(launch, () -> image ? Optional.of(launcherImage) : Optional.empty(),
            found -> new VmOptionsCheck(Path.of("java"), found, (command, limit) -> script.apply(command), Duration.ofSeconds(10)),
            Runnable::run, Runnable::run, center, () -> { restarts++; return restartWorks; }, opened::add, revealed::add,
            () -> 2048, () -> OptionalLong.of(16384), Duration.ZERO);
    }

    private List<String> titles() { return center.history().stream().map(entry -> entry.request().title()).toList(); }

    @Test void theGroupIsDeclared() {
        feature(managed(), true);
        assertThat(center.groups()).anySatisfy(group -> {
            assertThat(group.id()).isEqualTo("jasper.vmoptions");
            assertThat(group.name()).isEqualTo("Memory & VM options");
            assertThat(group.defaultMode()).isEqualTo(DisplayMode.CARD);
        });
    }

    @Test void theHeapShowsTheFileElseTheBundledDefault() throws IOException {
        var feature = feature(managed(), true);
        feature.reload();
        var heap = feature.hooks().heap();
        assertThat(heap.saved().getAsInt()).isEqualTo(2048);
        assertThat(heap.enabled().getAsBoolean()).isTrue();
        assertThat(heap.comment().apply(2048)).isEqualTo("Current: 2048 MiB.");
        assertThat(heap.comment().apply(32768)).contains("more than this computer's 16384 MiB");
        assertThat(heap.error().apply(256)).isEqualTo("Use 512 MiB or more.");
        Files.writeString(file(), "-Xmx4g\n");
        feature.reload();
        assertThat(heap.saved().getAsInt()).isEqualTo(4096);
    }

    @Test void savingTheHeapWritesTheFileAndAsksForARestart() throws IOException {
        var feature = feature(managed(), true);
        feature.startupSettled();
        var hooks = feature.hooks();
        assertThat(hooks.restart().get()).isEmpty();
        hooks.heap().save().apply(4096).join();
        assertThat(Files.readString(file())).isEqualTo(VmOptionsFile.HEADER + "\n\n-Xmx4096m\n");
        assertThat(hooks.restart().get()).hasValueSatisfying(notice -> assertThat(notice.text()).isEqualTo("Restart Jasper to apply your changes."));
        feature.settingsClosed();
        assertThat(titles()).containsExactly("Restart Jasper to apply your VM options");
        hooks.heap().save().apply(2048).join();
        assertThat(hooks.restart().get()).as("back to what this JVM applied").isEmpty();
    }

    @Test void aFileEditedElsewhereShowsWhileTheSectionIsOpen() throws IOException {
        var feature = feature(managed(), true);
        int[] changes = {0};
        var subscription = feature.hooks().onChanged().apply(() -> changes[0]++);
        int before = changes[0];
        Files.writeString(file(), "-Xmx8g\n");
        feature.pollFile();
        assertThat(changes[0]).isGreaterThan(before);
        assertThat(feature.hooks().heap().saved().getAsInt()).isEqualTo(8192);
        subscription.close();
    }

    @Test void aCorruptedFileDisablesTheHeapRowAndSaysWhy() throws IOException {
        Files.write(file(), new byte[] {'-', 'X', 'm', 'x', '1', 'g', '\n', 0});
        var feature = feature(managed(), true);
        feature.reload();
        var hooks = feature.hooks();
        assertThat(hooks.heap().enabled().getAsBoolean()).isFalse();
        assertThat(hooks.heap().comment().apply(2048)).contains("NUL");
        assertThat(hooks.state().get()).isPresent();
    }

    @Test void withoutJaspersLauncherTheSectionIsReadOnly() {
        var feature = feature(new VmOptionsLaunch(Optional.empty(), Optional.empty(), false, Optional.empty()), false);
        var hooks = feature.hooks();
        assertThat(feature.editable()).isFalse();
        assertThat(hooks.heap().enabled().getAsBoolean()).isFalse();
        assertThat(hooks.state().get()).hasValueSatisfying(notice -> assertThat(notice.text()).isEqualTo("Custom VM options apply to the installed Jasper app."));
        feature.startupSettled();
        assertThat(center.history()).isEmpty();
    }

    @Test void aFallbackStartSaysWhyWithTheJvmsMessage() throws IOException {
        Files.writeString(file(), "-Xmx3q\n");
        script = command -> command.contains("-Xmx3q") && !command.contains("-XX:-IgnoreUnrecognizedVMOptions")
            ? new VmOptionsCheck.Outcome(1, "Invalid maximum heap size: -Xmx3q\nError: Could not create the Java Virtual Machine.\n", false)
            : new VmOptionsCheck.Outcome(0, "", false);
        var feature = feature(launch(false, Optional.of("launch-failed")), true);
        feature.startupSettled();
        feature.startupSettled();
        assertThat(center.history()).singleElement().satisfies(entry -> {
            assertThat(entry.request().title()).isEqualTo("Jasper started without your custom VM options");
            assertThat(entry.request().content()).contains("Invalid maximum heap size: -Xmx3q");
            assertThat(entry.request().actions()).extracting(NotificationAction::label).containsExactly("Edit Custom VM Options…");
        });
    }

    @Test void aFallbackStartWithoutACheckStillSaysSo() throws IOException {
        Files.writeString(file(), "-Xmx3q\n");
        feature(launch(false, Optional.of("launch-failed")), false).startupSettled();
        assertThat(center.history()).singleElement().satisfies(entry ->
            assertThat(entry.request().content()).isEqualTo("The JVM rejected them, so Jasper started with the bundled options."));
    }

    @Test void aCorruptedLaunchIsAnnounced() {
        feature(launch(true, Optional.empty()), true).startupSettled();
        assertThat(titles()).containsExactly("Custom VM options were ignored");
    }

    @Test void findingsDescribeIgnoredUnrecognizedAndFatalLines() throws IOException {
        Files.writeString(file(), "Xmx300m\n-XX:+NoSuchFlag\n-Xms4g\n");
        script = command -> {
            if (command.contains("-XX:-IgnoreUnrecognizedVMOptions") && command.contains("-XX:+NoSuchFlag"))
                return new VmOptionsCheck.Outcome(1, "Unrecognized VM option 'NoSuchFlag'\n", false);
            if (!command.contains("-XX:-IgnoreUnrecognizedVMOptions"))
                return new VmOptionsCheck.Outcome(1, "Error occurred during initialization of VM\nInitial heap size set to a larger value than the maximum heap size\n", false);
            return new VmOptionsCheck.Outcome(0, "", false);
        };
        var feature = feature(managed(), true);
        feature.reload();
        assertThat(feature.hooks().findings().get()).hasValueSatisfying(notice -> {
            assertThat(notice.title()).isEqualTo("Jasper won't start with these options");
            assertThat(notice.text()).contains("Not an option, ignored: Xmx300m.")
                .contains("Unrecognized option, ignored: -XX:+NoSuchFlag.")
                .contains("Initial heap size set to a larger value than the maximum heap size")
                .contains("JASPER_VM_OPTIONS_SKIP=1");
        });
    }

    @Test void restartNowThatCannotRestartSaysSo() {
        restartWorks = false;
        var feature = feature(managed(), true);
        feature.restartNow();
        assertThat(restarts).isEqualTo(1);
        assertThat(titles()).containsExactly("Jasper could not restart");
    }

    @Test void editCreatesTheFileAndOpensIt() throws IOException {
        var feature = feature(managed(), true);
        feature.editCustomOptions();
        assertThat(Files.readString(file())).isEqualTo(VmOptionsFile.HEADER + "\n\n");
        assertThat(opened).containsExactly(file());
        feature.hooks().reveal().run();
        assertThat(revealed).containsExactly(file());
    }

    @Test void aFailedSaveNamesTheFile() throws IOException {
        Files.writeString(home.resolve("blocker"), "not a folder");
        var launch = new VmOptionsLaunch(Optional.of(home.resolve("blocker/jasper.vmoptions")), Optional.empty(), false, Optional.empty());
        var result = feature(launch, true).hooks().heap().save().apply(4096);
        assertThat(result).isCompletedExceptionally();
        assertThat(result.exceptionNow()).hasMessageContaining("Could not save jasper.vmoptions");
        assertThat(center.history()).singleElement().satisfies(entry -> {
            assertThat(entry.request().title()).isEqualTo("Could not save jasper.vmoptions");
            assertThat(entry.request().content()).startsWith(home.resolve("blocker/jasper.vmoptions").toString());
        });
    }
}
```

- [ ] **Step 2: Run it to see it fail**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.application.VmOptionsFeatureTest'`
Expected: FAIL — compilation errors (`VmOptionsFeature` does not exist).

- [ ] **Step 3: Implement the notices**

Create `jasper-app/src/main/java/dev/jasper/app/application/VmOptionsNotices.java`:

```java
package dev.jasper.app.application;

import dev.jasper.app.notifications.DisplayMode;
import dev.jasper.app.notifications.NotificationAction;
import dev.jasper.app.notifications.NotificationCenter;
import dev.jasper.app.notifications.NotificationGroup;
import dev.jasper.app.notifications.NotificationRequest;
import dev.jasper.app.notifications.Severity;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Custom VM options notifications (spec sections 4.3, 5.1 and 5.5) in their own group: a fallback start, a corrupted
 * file, a pending restart and failures. EDT only.
 */
final class VmOptionsNotices {
    static final NotificationGroup GROUP = new NotificationGroup("jasper.vmoptions", "Memory & VM options", NotificationRequest.JASPER, DisplayMode.CARD);

    private final NotificationCenter center;
    private final Runnable edit;
    private final Runnable restart;

    VmOptionsNotices(NotificationCenter center, Runnable edit, Runnable restart) {
        this.center = center;
        this.edit = edit;
        this.restart = restart;
        center.register(GROUP);
    }

    /** The launcher started Jasper once more without the user's options; {@code message} is the JVM's own, when known. */
    void fallbackStart(Optional<String> message) {
        center.post(NotificationRequest.of(GROUP.id(), Severity.WARNING, "Jasper started without your custom VM options")
            .withContent(message.map(text -> "The JVM rejected them: " + text).orElse("The JVM rejected them, so Jasper started with the bundled options."))
            .withAction(new NotificationAction("Edit Custom VM Options…", edit, false)));
    }

    /** The launcher ignored {@code file} because it is not valid text. */
    void corrupted(Path file) {
        center.post(NotificationRequest.of(GROUP.id(), Severity.WARNING, "Custom VM options were ignored")
            .withContent(file.getFileName() + " contains a NUL character or is not valid UTF-8, so Jasper started with the bundled options.")
            .withAction(new NotificationAction("Edit Custom VM Options…", edit, false)));
    }

    /** A restart would apply other options than this JVM's. */
    void restartPending() {
        center.post(NotificationRequest.of(GROUP.id(), Severity.INFO, "Restart Jasper to apply your VM options")
            .withContent("The new options take effect the next time Jasper starts.")
            .withAction(NotificationAction.of("Restart Now", restart)));
    }

    /** Restart could not work out how Jasper was started. */
    void restartUnavailable() {
        center.post(NotificationRequest.of(GROUP.id(), Severity.ERROR, "Jasper could not restart")
            .withContent("Jasper could not work out how it was started. Quit and reopen it to apply your changes."));
    }

    /** A file operation failed. */
    void failed(String title, String details) {
        center.post(NotificationRequest.of(GROUP.id(), Severity.ERROR, title).withContent(details));
    }
}
```

- [ ] **Step 4: Implement the feature**

Create `jasper-app/src/main/java/dev/jasper/app/application/VmOptionsFeature.java`:

```java
package dev.jasper.app.application;

import dev.jasper.app.lifecycle.Subscription;
import dev.jasper.app.notifications.NotificationCenter;
import dev.jasper.app.settingsui.SchemaPage;
import dev.jasper.app.settingsui.VmOptionsHooks;
import dev.jasper.app.vmoptions.LauncherImage;
import dev.jasper.app.vmoptions.VmOptionsCheck;
import dev.jasper.app.vmoptions.VmOptionsFile;
import dev.jasper.app.vmoptions.VmOptionsLaunch;
import dev.jasper.app.vmoptions.VmOptionsSyntax;
import dev.jasper.ui.model.Nodes;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * Custom VM options in the running app (spec sections 4.3, 5.1 and 5.2): the System Settings section's hooks, the
 * Help and palette actions' handlers, and the notices after start-up. File I/O and the check run on {@code worker};
 * state lives on the EDT, where {@code ui} delivers results. Nothing here runs on the start-up path until
 * {@link #startupSettled()} or the section asks.
 */
final class VmOptionsFeature {
    private static final System.Logger LOG = System.getLogger(VmOptionsFeature.class.getName());
    private static final List<String> KEYWORDS = List.of("memory", "heap", "xmx", "jvm", "java", "vm options");

    private final VmOptionsLaunch launch;
    private final Supplier<Optional<LauncherImage>> images;
    private final Function<LauncherImage, VmOptionsCheck> checks;
    private final Executor worker;
    private final Consumer<Runnable> ui;
    private final VmOptionsNotices notices;
    private final BooleanSupplier restart;
    private final Consumer<Path> openInEditor;
    private final Consumer<Path> reveal;
    private final LongSupplier runningHeapMiB;
    private final Supplier<OptionalLong> physicalMemoryMiB;
    private final Duration watchInterval;
    private final List<Runnable> listeners = new ArrayList<>();
    private VmOptionsFile.Contents contents = VmOptionsFile.Contents.ABSENT;
    private VmOptionsFile.Contents atStart;
    private VmOptionsCheck.Findings findings = VmOptionsCheck.Findings.NONE;
    private long bundledMiB = LauncherImage.DEFAULT_MAX_HEAP_MIB;
    private String stamp = "";
    private int generation;
    private boolean started;
    private javax.swing.Timer watch;

    VmOptionsFeature(VmOptionsLaunch launch, Supplier<Optional<LauncherImage>> images, Function<LauncherImage, VmOptionsCheck> checks,
                     Executor worker, Consumer<Runnable> ui, NotificationCenter center, BooleanSupplier restart,
                     Consumer<Path> openInEditor, Consumer<Path> reveal, LongSupplier runningHeapMiB,
                     Supplier<OptionalLong> physicalMemoryMiB, Duration watchInterval) {
        this.launch = Objects.requireNonNull(launch, "launch");
        this.images = Objects.requireNonNull(images, "images");
        this.checks = Objects.requireNonNull(checks, "checks");
        this.worker = Objects.requireNonNull(worker, "worker");
        this.ui = Objects.requireNonNull(ui, "ui");
        this.restart = Objects.requireNonNull(restart, "restart");
        this.openInEditor = Objects.requireNonNull(openInEditor, "openInEditor");
        this.reveal = Objects.requireNonNull(reveal, "reveal");
        this.runningHeapMiB = Objects.requireNonNull(runningHeapMiB, "runningHeapMiB");
        this.physicalMemoryMiB = Objects.requireNonNull(physicalMemoryMiB, "physicalMemoryMiB");
        this.watchInterval = Objects.requireNonNull(watchInterval, "watchInterval");
        notices = new VmOptionsNotices(center, this::editCustomOptions, this::restartNow);
    }

    /** The System Settings section's hooks. EDT. */
    VmOptionsHooks hooks() {
        var heap = new SchemaPage.External("vm.heap", "Maximum heap size", "MiB", KEYWORDS, new Nodes.Numeric(512, 1_048_576, 256, 0),
            this::savedHeap, () -> (int) Math.min(Integer.MAX_VALUE, bundledMiB), mib -> mib < 512 ? "Use 512 MiB or more." : "",
            this::heapComment, this::saveHeap, () -> launch.launcherManaged() && !contents.parsed().corrupted());
        return new VmOptionsHooks(heap,
            () -> launch.file().map(Path::toString).orElse("Custom VM options apply to the installed Jasper app."),
            this::editCustomOptions, this::revealFile, this::editable, this::stateNotice, this::findingsNotice, this::restartNotice,
            this::restartNow, this::subscribe);
    }

    /** Whether jasper.vmoptions applies to this JVM (Jasper's launcher started it). */
    boolean editable() { return launch.launcherManaged(); }

    /** Opens the file in the user's editor, creating it with the header first. EDT. */
    void editCustomOptions() {
        Optional<Path> target = launch.file();
        if (target.isEmpty()) return;
        Path file = target.get();
        worker.execute(() -> {
            try {
                VmOptionsFile.ensureExists(file);
                openInEditor.accept(file);
            } catch (IOException | RuntimeException failure) {
                ui.accept(() -> notices.failed("Could not open " + file.getFileName(), String.valueOf(failure.getMessage())));
            }
            ui.accept(this::reload);
        });
    }

    /** Once, after start-up settles: reads the file and announces a fallback start or a corrupted file. EDT. */
    void startupSettled() {
        if (started) return;
        started = true;
        if (!launch.launcherManaged()) return;
        Path file = launch.file().orElseThrow();
        boolean fallback = launch.skipped().filter("launch-failed"::equals).isPresent();
        boolean corrupted = launch.corrupted();
        worker.execute(() -> {
            VmOptionsFile.Contents read = read(file);
            Optional<LauncherImage> located = images.get();
            Optional<String> message = fallback ? check(located, read).fatal().map(VmOptionsCheck.Fatal::message) : Optional.empty();
            ui.accept(() -> {
                if (atStart == null) {
                    atStart = read;
                    contents = read;
                    located.ifPresent(image -> bundledMiB = image.bundledMaxHeapMiB());
                }
                if (fallback) notices.fallbackStart(message);
                if (corrupted) notices.corrupted(file);
            });
        });
    }

    /** The Settings dialog closed: repeats a pending restart as a notification. EDT. */
    void settingsClosed() { if (restartNeeded()) notices.restartPending(); }

    /** Restarts Jasper, or says it cannot. EDT. */
    void restartNow() { if (!restart.getAsBoolean()) notices.restartUnavailable(); }

    /** Reads the file again, then checks it; publishes each result on the EDT. EDT. */
    void reload() {
        if (!launch.launcherManaged()) {
            notifyListeners();
            return;
        }
        int ticket = ++generation;
        Path file = launch.file().orElseThrow();
        worker.execute(() -> {
            VmOptionsFile.Contents read = read(file);
            Optional<LauncherImage> located = images.get();
            String readStamp = stamp(file);
            ui.accept(() -> {
                if (ticket != generation) return;
                contents = read;
                stamp = readStamp;
                if (atStart == null) atStart = read;
                located.ifPresent(image -> bundledMiB = image.bundledMaxHeapMiB());
                notifyListeners();
            });
            VmOptionsCheck.Findings found = check(located, read);
            ui.accept(() -> {
                if (ticket != generation) return;
                findings = found;
                notifyListeners();
            });
        });
    }

    /** One tick of the file watch: reloads when the file's size or modification time changed. EDT. */
    void pollFile() {
        if (!launch.launcherManaged()) return;
        Path file = launch.file().orElseThrow();
        worker.execute(() -> {
            String now = stamp(file);
            ui.accept(() -> { if (!now.equals(stamp)) reload(); });
        });
    }

    private Subscription subscribe(Runnable listener) {
        listeners.add(listener);
        if (listeners.size() == 1) {
            reload();
            if (!watchInterval.isZero() && !watchInterval.isNegative()) {
                watch = new javax.swing.Timer((int) Math.min(Integer.MAX_VALUE, watchInterval.toMillis()), event -> pollFile());
                watch.start();
            }
        }
        return new Subscription(() -> {
            listeners.remove(listener);
            if (listeners.isEmpty() && watch != null) {
                watch.stop();
                watch = null;
            }
        });
    }

    private void notifyListeners() { for (Runnable listener : List.copyOf(listeners)) listener.run(); }

    private int savedHeap() {
        return (int) Math.min(Integer.MAX_VALUE, VmOptionsSyntax.lastMaxHeapMiB(contents.parsed().lines()).orElse(bundledMiB));
    }

    private String heapComment(int mib) {
        if (!launch.launcherManaged()) return "Custom VM options apply to the installed Jasper app.";
        if (contents.parsed().corrupted()) return "Fix jasper.vmoptions first: it contains a NUL character or is not valid UTF-8.";
        String current = "Current: " + runningHeapMiB.getAsLong() + " MiB.";
        OptionalLong physical = physicalMemoryMiB.get();
        return physical.isPresent() && mib > physical.getAsLong()
            ? current + " That is more than this computer's " + physical.getAsLong() + " MiB of memory."
            : current;
    }

    private CompletableFuture<Void> saveHeap(int mib) {
        var done = new CompletableFuture<Void>();
        Optional<Path> target = launch.file();
        if (target.isEmpty()) {
            done.completeExceptionally(new IllegalStateException("Custom VM options apply to the installed Jasper app."));
            return done;
        }
        Path file = target.get();
        long bundled = bundledMiB;
        worker.execute(() -> {
            try {
                VmOptionsFile.setMaxHeap(file, mib, bundled);
                done.complete(null);
            } catch (IOException | RuntimeException failure) {
                done.completeExceptionally(new IOException("Could not save " + file.getFileName() + ": " + failure.getMessage(), failure));
                // Spec section 7: the row shows the error and a notification names the path.
                ui.accept(() -> notices.failed("Could not save " + file.getFileName(), file + ": " + failure.getMessage()));
            }
            ui.accept(this::reload);
        });
        return done;
    }

    private void revealFile() {
        Optional<Path> target = launch.file();
        if (target.isEmpty()) return;
        Path file = target.get();
        worker.execute(() -> {
            try {
                reveal.accept(Files.exists(file) ? file : file.toAbsolutePath().getParent());
            } catch (RuntimeException failure) {
                ui.accept(() -> notices.failed("Could not reveal " + file.getFileName(), String.valueOf(failure.getMessage())));
            }
        });
    }

    private boolean restartNeeded() {
        return launch.launcherManaged() && atStart != null && launch.restartNeeded(contents.parsed(), atStart.parsed());
    }

    private Optional<SchemaPage.Notice> stateNotice() {
        if (!launch.launcherManaged()) {
            return Optional.of(new SchemaPage.Notice("Applies to the installed app", "Custom VM options apply to the installed Jasper app."));
        }
        if (launch.skipped().filter("launch-failed"::equals).isPresent()) {
            return Optional.of(new SchemaPage.Notice("Started without your options",
                "Jasper started without your custom VM options this time because the JVM rejected them."));
        }
        if (launch.skipped().isPresent()) {
            return Optional.of(new SchemaPage.Notice("Started without your options",
                "JASPER_VM_OPTIONS_SKIP is set, so Jasper started without your custom VM options."));
        }
        if (launch.corrupted() || contents.parsed().corrupted()) {
            return Optional.of(new SchemaPage.Notice("Options ignored",
                "jasper.vmoptions contains a NUL character or is not valid UTF-8; fix it to apply your options."));
        }
        return Optional.empty();
    }

    private Optional<SchemaPage.Notice> findingsNotice() {
        // Spec section 5.1's wording; "Not an option, ignored" reports the lines the launcher skips (spec 3.2).
        var sentences = new ArrayList<String>();
        for (String line : contents.parsed().ignored()) sentences.add("Not an option, ignored: " + line + ".");
        for (String line : findings.unrecognized()) sentences.add("Unrecognized option, ignored: " + line + ".");
        findings.fatal().ifPresent(fatal -> sentences.add("Jasper can't start with these options: " + fatal.message() + ". "
            + (fatal.recoverable() ? "It will start with the bundled options instead."
                : "Fix the file before restarting; setting JASPER_VM_OPTIONS_SKIP=1 starts Jasper without it.")));
        if (findings.cacheRejected()) sentences.add("These options turn off Jasper's start-up cache; it will start more slowly.");
        if (sentences.isEmpty()) return Optional.empty();
        return Optional.of(new SchemaPage.Notice(findings.fatal().isPresent() ? "Jasper won't start with these options" : "Check your options",
            String.join(" ", sentences)));
    }

    private Optional<SchemaPage.Notice> restartNotice() {
        return restartNeeded() ? Optional.of(new SchemaPage.Notice("Restart required", "Restart Jasper to apply your changes.")) : Optional.empty();
    }

    /** The check's findings; a check that cannot run finds nothing, never blocks saving, and is logged (spec section 7). */
    private VmOptionsCheck.Findings check(Optional<LauncherImage> located, VmOptionsFile.Contents read) {
        if (located.isEmpty()) return VmOptionsCheck.Findings.NONE;
        try {
            return checks.apply(located.get()).check(read.parsed());
        } catch (IOException | RuntimeException unavailable) {
            LOG.log(System.Logger.Level.WARNING, "Could not check the custom VM options", unavailable);
            return VmOptionsCheck.Findings.NONE;
        }
    }

    private static VmOptionsFile.Contents read(Path file) {
        try {
            return VmOptionsFile.read(file);
        } catch (IOException unreadable) {
            LOG.log(System.Logger.Level.WARNING, "Could not read " + file, unreadable);
            return VmOptionsFile.Contents.ABSENT;
        }
    }

    private static String stamp(Path file) {
        try {
            return Files.isRegularFile(file) ? Files.getLastModifiedTime(file).toMillis() + ":" + Files.size(file) : "absent";
        } catch (IOException unreadable) {
            return "unreadable";
        }
    }
}
```

- [ ] **Step 5: Run it to see it pass**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.application.VmOptionsFeatureTest' --tests 'dev.jasper.app.application.NotificationsFeatureTest' verifyApplicationArchitecture`
Expected: BUILD SUCCESSFUL (`NotificationsFeatureTest` still sees only its two groups: the new group is registered by
`VmOptionsNotices`).

- [ ] **Step 6: Commit**

```bash
git add jasper-app/src/main/java/dev/jasper/app/application/VmOptionsNotices.java jasper-app/src/main/java/dev/jasper/app/application/VmOptionsFeature.java jasper-app/src/test/java/dev/jasper/app/application/VmOptionsFeatureTest.java
git commit -F - <<'EOF'
VM options: the feature behind the section, actions and notices

VmOptionsFeature supplies the System Settings section (heap, file, check
findings, restart), handles Edit Custom VM Options and Restart Now, watches
the file while the section is shown, and after start-up says when Jasper
started without the user's options or ignored the file; VmOptionsNotices
posts them in the new "Memory & VM options" group.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

---

### Task 9: Wire it into the application, and document it

**Files:**
- Modify: `jasper-app/src/main/java/dev/jasper/app/application/JasperApplication.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/application/package-info.java` (allowed list)
- Modify: `jasper-app/src/main/java/dev/jasper/app/shortcuthelp/ShortcutCatalog.java`
- Test: `jasper-app/src/test/java/dev/jasper/app/application/JasperApplicationPluginsTest.java` (one test)
- Docs: `docs/configuration.md`, `docs/notifications.md`, `docs/app-architecture.md`, `docs/STATUS.md`, the spec's
  status paragraph, this plan's status line

**Interfaces:**
- Consumes: Task 8's `VmOptionsFeature` and its constructor; Task 7's `SystemActions(..., Optional<VmOptionsHooks>)`;
  `JasperApplication.restart(RestartMode)`, `openSettings(UUID, String)`, `afterDelay(Duration, Runnable)`, `trace`.

- [ ] **Step 1: Write the failing test**

In `jasper-app/src/test/java/dev/jasper/app/application/JasperApplicationPluginsTest.java`, in
`theManagerIsAContributedActionInTheFileMenu`, replace:

```java
            assertThat(application[0].contributions().action("app.shortcuts")).isPresent();
            assertThat(application[0].contributions().menus()).anySatisfy(section -> {
                assertThat(section.target().title()).isEqualTo("Help");
                assertThat(section.entries()).contains(new MenuEntry.Item("app.shortcuts"));
            });
```

with:

```java
            assertThat(application[0].contributions().action("app.shortcuts")).isPresent();
            assertThat(application[0].contributions().action("app.memory")).get()
                .satisfies(action -> assertThat(action.title()).isEqualTo("Change Memory Settings…"));
            assertThat(application[0].contributions().action("app.vmoptions")).get()
                .satisfies(action -> assertThat(action.title()).isEqualTo("Edit Custom VM Options…"));
            assertThat(application[0].contributions().menus()).anySatisfy(section -> {
                assertThat(section.target().title()).isEqualTo("Help");
                assertThat(section.entries()).containsExactly(new MenuEntry.Item("app.shortcuts"), new MenuEntry.Separator(),
                    new MenuEntry.Item("app.memory"), new MenuEntry.Item("app.vmoptions"));
            });
```

- [ ] **Step 2: Run it to see it fail**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.application.JasperApplicationPluginsTest'`
Expected: FAIL — `app.memory` is not registered.

- [ ] **Step 3: Wire the feature**

In `JasperApplication.java`:

1. Add a field next to `settings`: `private VmOptionsFeature vmOptions;`.
2. In `startPlugins`, just before the line that registers `"app.shortcuts"`, add:

```java
        var vmEditor = new dev.jasper.app.platform.ConfigEditor();
        vmOptions = new VmOptionsFeature(dev.jasper.app.vmoptions.VmOptionsLaunch.current(),
            () -> dev.jasper.app.vmoptions.LauncherImage.locate(System.getProperty("jpackage.app-path")),
            image -> new dev.jasper.app.vmoptions.VmOptionsCheck(dev.jasper.app.vmoptions.VmOptionsCheck.runtimeJava(), image,
                dev.jasper.app.vmoptions.VmOptionsCheck::runProcess, Duration.ofSeconds(10)),
            work -> Thread.ofVirtual().name("jasper-vmoptions").start(work), SwingUtilities::invokeLater, notificationCenter,
            () -> restart(RestartMode.SAME), vmEditor::open, vmEditor::reveal,
            dev.jasper.app.vmoptions.VmOptionsLaunch::runningMaxHeapMiB, dev.jasper.app.vmoptions.VmOptionsLaunch::physicalMemoryMiB,
            Duration.ofSeconds(2));
        // Never on the start-up path: the options file is read once the first output shows, the warm-up ends, or 5 s after the first paint.
        trace.when(StartupTrace.Milestone.OUTPUT_PAINTED, SwingUtilities::invokeLater, vmOptions::startupSettled);
        trace.when(StartupTrace.Milestone.WARMED_UP, SwingUtilities::invokeLater, vmOptions::startupSettled);
        trace.when(StartupTrace.Milestone.FIRST_PAINT, SwingUtilities::invokeLater, () -> afterDelay(Duration.ofSeconds(5), vmOptions::startupSettled));
```

3. Replace:

```java
        contributions.addAction("app.shortcuts", "Keyboard Shortcuts…", null, List.of("help", "keys", "bindings", "keymap", "edit shortcuts", "keyboard settings"),
            Optional.empty(), invocation -> openSettings(null, dev.jasper.app.shortcuthelp.KeymapPage.ID));
        contributions.addMenuSection(MenuTarget.topLevel("app.help", "Help")).set(List.of(new MenuEntry.Item("app.shortcuts")));
```

with:

```java
        contributions.addAction("app.shortcuts", "Keyboard Shortcuts…", null, List.of("help", "keys", "bindings", "keymap", "edit shortcuts", "keyboard settings"),
            Optional.empty(), invocation -> openSettings(null, dev.jasper.app.shortcuthelp.KeymapPage.ID));
        contributions.addAction("app.memory", "Change Memory Settings…", null, List.of("memory", "heap", "xmx", "jvm", "java", "vm options"),
            Optional.empty(), invocation -> openSettings(invocation.windowId(), "system#vm.heap"));
        contributions.addAction("app.vmoptions", "Edit Custom VM Options…", null, List.of("vm options", "vmoptions", "jvm", "java", "heap", "memory"),
            Optional.empty(), invocation -> {
                if (vmOptions.editable()) vmOptions.editCustomOptions();
                else openSettings(invocation.windowId(), "system");
            });
        contributions.addMenuSection(MenuTarget.topLevel("app.help", "Help")).set(List.of(new MenuEntry.Item("app.shortcuts"),
            new MenuEntry.Separator(), new MenuEntry.Item("app.memory"), new MenuEntry.Item("app.vmoptions")));
```

4. In the `SystemActions` construction (inside `if (configuration != null)`), replace the `java.util.Optional.empty()`
   Task 7 added with `Optional.of(vmOptions.hooks())`.
5. Replace `openSettings`:

```java
        /** Opens Settings over the given window, or the active one when null, at a page (the last visited when null). */
        void openSettings(java.util.UUID ownerOrNull, String pageOrNull) {
            if (settings == null || quitting || stopped) return;
            java.util.UUID owner = ownerOrNull != null ? ownerOrNull
                : lastActive != null && windows.contains(lastActive) ? lastActive.content().id() : null;
            settings.open(owner, pageOrNull);
        }
```

with:

```java
        /**
         * Opens Settings over the given window, or the active one when null, at a page (the last visited when null). The
         * dialog is modal, so the first call returns once it closed; then a pending VM-options restart is repeated.
         */
        void openSettings(java.util.UUID ownerOrNull, String pageOrNull) {
            if (settings == null || quitting || stopped) return;
            java.util.UUID owner = ownerOrNull != null ? ownerOrNull
                : lastActive != null && windows.contains(lastActive) ? lastActive.content().id() : null;
            boolean opening = !settings.isOpen();
            settings.open(owner, pageOrNull);
            if (opening && !settings.isOpen() && vmOptions != null && !quitting && !stopped) vmOptions.settingsClosed();
        }
```

In `jasper-app/src/main/java/dev/jasper/app/application/package-info.java`, add `dev.jasper.app.vmoptions` to the
"Allowed outgoing Jasper dependencies:" list.

In `ShortcutCatalog.java`, replace
`if (id.equals("plugins.manage") || id.equals("app.shortcuts")) return "Application";` with:

```java
        if (id.equals("plugins.manage") || id.equals("app.shortcuts") || id.equals("app.memory") || id.equals("app.vmoptions")) return "Application";
```

- [ ] **Step 4: Run the tests to see them pass**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.application.*' --tests 'dev.jasper.app.shortcuthelp.*' --tests 'dev.jasper.app.settingsui.*' verifyApplicationArchitecture`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Document**

`docs/configuration.md`:
- Change `System Settings shows the file's location with Open in Editor, Reveal, Reload from Disk and Migrate Config.`
  to `System Settings shows the file's location with Open in Editor, Reveal, Reload from Disk and Migrate Config, and a
  Java Virtual Machine section for the custom VM options.`, with the words "custom VM options" made a Markdown link to
  the in-page anchor `custom-vm-options` (the plan spells the link out in words because `AppDocumentationTest` resolves
  links in plan files too).
- At the end of `### Custom VM options`, add the paragraph:

```markdown
In the installed app, System Settings → Java Virtual Machine sets the maximum heap size (it edits the file's `-Xmx` line;
the bundled 2048 MiB removes it) and opens the file with **Edit Custom VM Options…**; Help → **Change Memory
Settings…** and **Edit Custom VM Options…** lead to the same places. The section checks the options with the bundled
`java` and says which lines it ignores, which options the JVM does not know, whether Jasper would start with them, and
when a restart is needed (**Restart Now**). When Jasper had to start without your options, or ignored the file, it says
so in a notification in the **Memory & VM options** group.
```

`docs/notifications.md`, Groups table: after the `Internal errors` row add

```markdown
| Memory & VM options | `jasper.vmoptions` | a start without your custom VM options, an options file Jasper had to ignore, and a restart that would apply new options | card |
```

`docs/app-architecture.md`, Package ownership table: after the `restart` row add

```markdown
| `vmoptions` | Custom VM options: the launcher's reading of `jasper.vmoptions` mirrored for the app, editing it, the launch's properties, the bundled `Jasper.cfg` and the check that runs the bundled `java`. JDK-only (`com.sun.management` allowed; `verifyApplicationArchitecture` enforces it); values and pure functions, with file I/O and the check on the caller's thread. |
```

In the spec's status paragraph add `Plan 2 (settings and actions) complete on <today's date>.` at the end. In this
plan, change `Status: **Not started.**` to `Status: **Complete on <today's date>.**` followed by any deviation beyond the
six planned ones. In `docs/STATUS.md`, add at the top, filling in the date and the counts from Step 6:

```markdown
**Custom VM options plan 2 (settings and actions) complete — <date>.** On `claude/custom-vm-options-settings` (not
pushed): System Settings has a Java Virtual Machine section (heap limit in `jasper.vmoptions`, Edit Custom VM Options…,
Reveal, the options' check with the bundled `java`, a restart banner with Restart Now; read-only outside the installed
app), Help and the palette have Change Memory Settings… and Edit Custom VM Options…, and Jasper says in the new
**Memory & VM options** notification group when it started without the user's options or ignored the file. New JDK-only
package `dev.jasper.app.vmoptions` mirrors the launcher's reading (shared digest vectors). A blank
`JASPER_VM_OPTIONS_SKIP` now skips too. Deviations: the six in the plan's banner. Full `./gradlew check`: <tests> tests,
<failures> failures, <skips> skips. Manual checks pending (the user): in the packaged app, set the heap in Settings, see
the restart banner, Restart Now, and see the new limit; type an invalid line and see the findings; Help menu entries.
Plan 3 (memory indicator and low-memory warning) is next.
```

- [ ] **Step 6: Run the whole check**

Run: `./gradlew check`
Expected: BUILD SUCCESSFUL. Count the tests across every module (including `plugins/*`):

```bash
python3 - <<'PY'
import pathlib, xml.etree.ElementTree as ET
t = f = s = 0
for p in list(pathlib.Path('.').glob('*/build/test-results/test/*.xml')) + list(pathlib.Path('.').glob('plugins/*/build/test-results/test/*.xml')):
    r = ET.parse(p).getroot(); t += int(r.get('tests')); f += int(r.get('failures')) + int(r.get('errors')); s += int(r.get('skipped'))
print(t, 'tests', f, 'failures', s, 'skips')
PY
```

Put the counts into the STATUS entry.

- [ ] **Step 7: Commit**

```bash
git add jasper-app/src docs
git commit -F - <<'EOF'
VM options in Settings, Help and the palette

JasperApplication wires VmOptionsFeature: the System Settings section, Help
and palette actions Change Memory Settings... and Edit Custom VM Options...,
the start-up notices (never on the start-up path), and a pending restart
repeated when Settings closes. Docs: configuration, notifications,
architecture, STATUS.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

---

## Hand-off checks for the user

Agents never launch the GUI. After the branch is reviewed, in the packaged app (`./gradlew :jasper-app:packageApp`,
then open `jasper-app/build/packaging/image/Jasper.app`):

1. Settings → System Settings → Java Virtual Machine: the heap shows "Current: 2048 MiB."; set 3072 and Apply; the
   restart banner appears; Restart Now; reopen the section: "Current: 3072 MiB.".
2. Edit Custom VM Options… opens the file (on macOS it may only reveal it if no app is set for `.vmoptions`); add
   `Xmx300m` and `-Xmx3q`, save, and see the section list the ignored line and "Jasper can't start with these options".
   Remove them.
3. Help shows Change Memory Settings… (opens the section with the heap field focused) and Edit Custom VM Options….
4. With `-Xmx3q` in the file, quit and reopen: Jasper starts, and a "Jasper started without your custom VM options"
   notification names the JVM's message.
