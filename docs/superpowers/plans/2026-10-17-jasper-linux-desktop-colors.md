# Linux Desktop colors Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship an optional Linux Desktop colors theme with portal preferences, GTK 3 and GTK 4/libadwaita sources, live updates and safe fallbacks.

**Architecture:** Three isolated native processes report preferences and palettes to an application-owned supervisor. A pure resolver produces immutable theme values; existing theme transactions and Settings drafts remain the publication boundaries. Toolkit dependencies never enter the JVM, SDK or terminal module.

**Tech Stack:** Java 25/JBR, existing Swing/FlatLaf/Kit, JUnit/AssertJ, C11, GLib/GIO, dynamically loaded GTK 3.24, GTK 4.10+ and optional libadwaita, Gradle wrapper.

**Spec:** [Approved Linux Desktop colors design](../specs/2026-10-17-jasper-linux-desktop-colors-design.md).

**Status:** Resumed 2026-10-24 after merging main `22a611145` (merge `0ddb13263`). DesktopPaintPairs gets a read-only cross-kit validation exception in StaticDefaultTest; class-load metric/painter restrictions remain. Tasks 1–4 remain reviewed; Task 5 is resumed from preserved unfinished work. Current guides and main’s Meadow defaults, independent Editor scheme and workspace APIs take precedence over stale implementation assumptions. Approved for execution, 2026-10-18. Implementation started in `codex/linux-desktop-colors`. This is one runnable deliverable; intermediate commits are not a claim that Desktop colors ships. Execution clarifications: modern libadwaita missing variables remain missing (spec precedence over Task 3 fallback wording); Task 8 preview acquisition will carry explicit source/appearance demand. Linux native evidence remains required; macOS protocol tests are supplemental. Ubuntu 26.04 VM native protocol/portal/GTK tests pass, with Ubuntu 24.04 userspace GTK coverage and Ubuntu 22.04 baseline compilation/unsupported-GTK rejection. These satisfy the local native-first gate; the digest-pinned Ubuntu 26.04 CI container remains unexecuted. Older userspaces share the VM kernel. Tasks 2–3 Java regressions already passed against Task 1; native missing APIs and behavioral regressions supplied RED evidence. GTK capability failures retry with capped backoff because native initialization is latched; portal absence retains its live observer. The appearance-to-platform dependency documentation/allowlist change moves from Task 7 to Task 5, where observation imports begin. Task 5 also adds a generic interface text-selection fill override (the inherited terminal fill cannot reach 7:1), plus minimal generic final focus/error-ring paint overrides: existing 25% Light halo opacity cannot reach 3:1. Preserve authored rendering and geometry, and verify actual ring paint. Task 6 also reserves the dynamic id in the theme converter, separately from its file-backed built-in list. Record further deviations here and in `docs/STATUS.md`.

## Global Constraints

- Interface only: GTK-only changes preserve editor/syntax and terminal values. Desktop `match` uses Slate Cobalt's matching scheme. Explicit terminal schemes remain independent.
- Existing defaults, authored themes, kits, typography and geometry stay intact. Preserve main’s independent Editor color-scheme setting and its selected content colors.
- Reserve `desktop-colors` everywhere; offer it on Linux and preserve unavailable saved picks elsewhere.
- `ui.theme.desktop_source`: `auto` (default), `gtk3`, `gtk4`; labels Automatic, GTK 3, GTK 4 / libadwaita.
- Portal appearance applies to Match System; explicit Light/Dark wins. Portal accent/contrast affect Desktop colors only.
- Automatic prefers GTK 4 on a case-insensitive GNOME token in colon-separated `XDG_CURRENT_DESKTOP`; otherwise GTK 3. Explicit sources never switch toolkits.
- GTK 3.24 and GTK 4.10 are the minimum runtime targets; GTK 4.16+ uses CSS-variable probes. Optional libraries cannot prevent Jasper startup.
- Limits: 64 KiB messages, 16 KiB stderr tail, two-second response timeout, 100 ms coalescing, 30-second resampling, 500 ms shutdown grace; retry 1, 2, 4, 8, 16, 30 seconds, capped at 30.
- Enabled text/selection/accent text: 4.5:1 normal, 7:1 high contrast. Necessary control/focus boundaries: 3:1. Repair uses deterministic 5% sRGB steps and black-direction tie breaking.
- Native work and generation run off EDT; install and publish on EDT. No config writes from desktop changes. No partial snapshot publication.
- GTK majors run separately. No native window presentation, focus acquisition, login shell, GUI benchmark or AOT training. Native desktop acceptance remains user-run.
- App platform observations are JDK-only values; platform depends on no other Jasper package. No SDK change; maintain all package DAGs and source hygiene.
- Execute on an isolated `codex/` branch/worktree. Do not include the unrelated Ubuntu Yaru edits in this checkout. One commit per task, each ending with `Co-Authored-By: Codex <noreply@openai.com>`. No merge or push without authorization.

## Review Focus

1. A portal restart races with an old ReadAll result: the new owner's preferences must win (Task 2).
2. A theme overrides modern CSS variables but retains old compatibility names: use the modern values, including a real sentinel-colored value (Task 3).
3. A saved Desktop colors pick travels to Windows/macOS or collides with a disabled plugin id: retain intent and reject shadowing (Task 6).
4. Dark and Light previews share one native worker: an old preview response must never replace the applied appearance (Tasks 4, 8).
5. Imported surfaces share Jasper foreground roles, and paint-time guards may lower a high-contrast target: validate actual final paints in both kits (Tasks 5, 9).

## Execution and file map

Read `AGENTS.md`, `docs/STATUS.md`, the spec and this plan before execution. Re-read owners after creating the worktree; the planning checkout has unrelated uncommitted Yaru work. Do not require Yaru to exist. Use `superpowers:using-git-worktrees` at execution time. Preserve the repository's per-task implementer/reviewer and final whole-branch review workflow unless the user selects native inline execution.

All paths below are repository-relative. New Java files use the package matching their directory. Production ownership:

| Files | Responsibility |
|---|---|
| `packaging/desktop-colors/src/{protocol.h,protocol.c,portal.c,gtk3.c,gtk4.c,gtk_api.h,gtk_api.c}` | Framing, portal observation, independent toolkit probes, runtime symbol resolution |
| `packaging/desktop-colors/test/` | Native protocol, fake portal and virtual-display extraction tests |
| `gradle/linux-desktop-colors.gradle` | Compile/test/stage helpers, Linux only |
| `jasper-app/src/main/java/dev/jasper/app/platform/{DesktopObservation,DesktopProtocol,DesktopJson,DesktopProcess,DesktopColors}.java` | Values, bounded decoder, supervision and capability lifecycle |
| `jasper-app/src/main/java/dev/jasper/app/appearance/{DesktopPalette,DesktopContrast,DesktopPaintPairs,ThemePick}.java` | Pure mapping/repair, final-paint validation, authored/dynamic selection |
| `jasper-app/src/main/java/dev/jasper/app/application/DesktopAppearance.java` | Saved config, applied/preview demand, worker-to-EDT generation checks |
| Existing `SystemAppearance`, theme/config/settings/application owners | Integrate the new source through existing lifetime and transaction paths |

Task commands below introduce new tasks explicitly; they are not claimed to exist today. Tests use controlled clocks/executors and real child Java fixtures, not wall-clock sleeps or the user's desktop. Native tests use a private D-Bus session and Xvfb on Linux CI. Native desktop acceptance is a separate final gate.

## Shared contracts (defined by Task 1)

Use these exact nested record names in `DesktopObservation.java`; compact constructors defensively copy maps and reject null fields. RGBA validates finite components in [0,1]. Generations are nonnegative signed 64-bit integers.

```java
public record DesktopObservation(Portal portal, Map<Source, Palette> palettes) {
    public enum Source { GTK3, GTK4 }
    public enum Polarity { DARK, LIGHT, UNKNOWN }
    public enum Pair { WINDOW, VIEW, HEADER, SIDEBAR, DIALOG, POPOVER, SELECTION }
    public record Rgba(double red, double green, double blue, double alpha) { }
    public record Colors(Rgba background, Rgba foreground) { }
    public record Portal(long owner, Polarity appearance, Optional<Rgba> accent,
                         Optional<Boolean> highContrast, String reason) { }
    public record Palette(long generation, Source source, Polarity appearance,
                          Map<Pair, Colors> colors, String implementation,
                          String version, String reason) { }
}
```

The record declarations above define the data shape; Task 1 supplies the validation bodies and tests. Add explicit imports for java.util.Map and java.util.Optional. Availability of a palette is determined by WINDOW usability, not an unchecked native boolean. Portal keys are independently optional.

Wire version 1 uses a flat object with scalar values only. Generations use JSON integer tokens, never floating-point conversion. Color components are encoded together as `r,g,b,a` decimal strings with sufficient round-trip precision; invalid color strings invalidate only that optional pair/preference. This keeps the small parser independent of toolkit or JSON libraries. All JSON syntax and duplicate keys are validated before decoding fields.

```json
{"v":1,"kind":"palette","generation":7,"source":"gtk4","appearance":"dark","implementation":"libadwaita","libraryVersion":"1.6","window.bg":"0.12,0.12,0.12,1","window.fg":"1,1,1,1","reason":""}
```

Commands are `{"v":1,"kind":"sample","generation":7,"appearance":"dark"}` and `{"v":1,"kind":"ping","generation":7}`. Ping returns `pong` with the same generation. Portal events use `kind=portal`, `owner`, appearance, optional `accent`, optional boolean `highContrast`, and reason. Unknown scalar keys are ignored for forward compatibility; unknown kinds/versions, nesting, arrays, duplicate keys, malformed UTF-8 and trailing content reject the message. The native command reader only accepts the fixed command schema. Maximum key count is 32 and maximum reason length is 1024 characters. Use a 10-second ping interval and the two-second response deadline independently of 30-second palette resampling.

Every task follows RED → implementation → GREEN → commit → independent review. Additional scenario lists are required tests, not optional suggestions. Run each listed focused command before the task commit and record its outcome. Commit only that task's files; commands must not stage unrelated changes.

### Task 1: Protocol values, strict decoding and native build harness

**Files:** Create the platform value and codec files in `jasper-app/src/main/java/dev/jasper/app/platform/{DesktopObservation,DesktopProtocol,DesktopJson}.java`, `jasper-app/src/test/java/dev/jasper/app/platform/DesktopProtocolTest.java`; create `packaging/desktop-colors/src/{protocol.h,protocol.c}`, `packaging/desktop-colors/test/protocol_test.c`, `packaging/desktop-colors/test/vectors.jsonl`, `packaging/desktop-colors/README.md`, `gradle/linux-desktop-colors.gradle`. Modify `jasper-app/build.gradle.kts` to apply the script.

**Interfaces:** `DesktopProtocol.decode(byte[] line)` returns a package-private sealed `Message` with `PaletteMessage(Palette value)`, `PortalMessage(Portal value)`, `Pong(long generation)`. `DesktopProtocol.sample(long generation, boolean dark)` and `ping(long generation)` return UTF-8 command bytes including newline. `DesktopJson.parse(String)` returns `Map<String,Object>` containing String/Long/Double/Boolean/null only. `DesktopProcess` later owns framing. Native `dc_read_command(const char *, size_t, struct dc_command *)` and `dc_write_palette(FILE *, const struct dc_palette *)` share protocol.h; no widget types cross that header.

- [ ] **Step 1 — Add the failing regression.** Add this complete test method to the named test class, using its package, JUnit 5 `@Test`, and AssertJ static assertions. Add the required scenarios below as separate named regressions in the same task.

```java
@Test void preservesIntegerGenerationAndRejectsDuplicates() {
    String wire = "{\"v\":1,\"kind\":\"pong\",\"generation\":9007199254740993}";
    assertThat(DesktopProtocol.decode(wire.getBytes(StandardCharsets.UTF_8)))
        .isEqualTo(new DesktopProtocol.Pong(9007199254740993L));
    String duplicate = "{\"v\":1,\"v\":2,\"kind\":\"pong\",\"generation\":1}";
    assertThatIllegalArgumentException().isThrownBy(() ->
        DesktopProtocol.decode(duplicate.getBytes(StandardCharsets.UTF_8)));
}
```

- [ ] **Step 2 — Confirm RED.** Run `./gradlew :jasper-app:test --tests '*DesktopProtocolTest'`. Expect a missing new API compilation error initially, or the named behavioral assertion to fail after scaffolding. Record the actual failure; unrelated infrastructure failures do not establish RED.

- [ ] **Step 3 — Implement the task.** Implement the shared contracts and a strict scalar-only JSON reader; keep it local to platform and do not change the theme JSON parser. Escape all control characters and quotes in C output, use locale-independent decimal parsing/printing and distinguish a rejected field from a rejected envelope. Bound byte input before UTF-8 decode and bound strings/key counts while parsing. Add Linux tasks `compileDesktopProtocolTests` and `testDesktopProtocol`, with `-std=c11 -Wall -Wextra -Werror`; off Linux they skip without discovering native dependencies. Make Linux `check` depend on the protocol tests. Java fixtures read the same vector file via `jasper.repoRoot`; include expected accepted/rejected results in an adjacent manifest. Native runtime compilation targets glibc 2.35 / GLib 2.72 (Ubuntu 22.04 packaging baseline). Toolkit headers/symbol decisions belong to Task 3, not a new Java dependency.

**Required regressions:** 64 KiB accepted and 64 KiB + 1 rejected; split UTF-8 framing handled later in Task 4; duplicate keys, nested values, non-finite/out-of-range colors, absent pair half, protocol version mismatch, signed-64-bit boundary, escaped control characters, unknown scalar fields, maximum 32 keys, 1024-character reasons, dot decimals under a comma-decimal locale. Run `./gradlew :jasper-app:testDesktopProtocol` on Linux in addition to the Java command.

- [ ] **Step 4 — Confirm GREEN.** Run `./gradlew :jasper-app:test --tests '*DesktopProtocolTest'` again. All selected tests must pass; inspect XML for failures, errors and skips. For native commands inspect their explicit test totals and exit codes. A skipped Linux extraction test is not a pass.

- [ ] **Step 5 — Commit and review.** Stage only the files listed for this task and commit with subject `feat: define Linux desktop appearance protocol` and the required co-author trailer. Obtain the task review and fix findings before the next dependent task.

### Task 2: Portal helper and owner-safe preference observation

**Files:** Create `packaging/desktop-colors/src/portal.c`, `packaging/desktop-colors/test/{portal_test.c,fake_portal.c}` and `jasper-app/src/test/java/dev/jasper/app/platform/DesktopPortalTest.java`. Extend the native script and README from Task 1.

**Interfaces:** Native portal executable `jasper-desktop-portal --protocol=1`, GLib main loop, GIO session D-Bus. `dc_portal_start(GDBusConnection *, FILE *)` and `dc_portal_stop(void)` live in portal.c and are exercised by compiling its core without main. Portal messages use the Task 1 `Portal` record; each unique bus owner gets an increasing owner generation.

- [ ] **Step 1 — Add the failing regression.** Add this complete test method to the named test class, using its package, JUnit 5 `@Test`, and AssertJ static assertions. Add the required scenarios below as separate named regressions in the same task.

```java
@Test void missingPreferencesRemainIndependent() {
    String wire = "{\"v\":1,\"kind\":\"portal\",\"owner\":4,\"appearance\":\"unknown\",\"accent\":\"0.2,0.4,0.6,1\",\"reason\":\"\"}";
    var message = (DesktopProtocol.PortalMessage)
        DesktopProtocol.decode(wire.getBytes(StandardCharsets.UTF_8));
    assertThat(message.value().appearance()).isEqualTo(DesktopObservation.Polarity.UNKNOWN);
    assertThat(message.value().accent()).isPresent();
    assertThat(message.value().highContrast()).isEmpty();
}
```

- [ ] **Step 2 — Confirm RED.** Run `./gradlew :jasper-app:test --tests '*DesktopPortalTest'`. Expect a missing new API compilation error initially, or the named behavioral assertion to fail after scaffolding. Record the actual failure; unrelated infrastructure failures do not establish RED.

- [ ] **Step 3 — Implement the task.** Use the session-bus destination `org.freedesktop.portal.Desktop`, path `/org/freedesktop/portal/desktop`, interface `org.freedesktop.portal.Settings`. Subscribe to SettingChanged before async ReadAll. Tag each read with owner and an event serial; merge its values only for keys not changed since that read started. Ignore replies from retired owners. Track NameOwnerChanged, clear absent keys on a new-owner baseline, and emit a complete snapshot. Use the task's two-second GIO call timeout. Read only the appearance namespace; color-scheme 1=dark, 2=light, 0/unknown=unknown; contrast 1=true, 0=false, unknown absent. Accent must be a `(ddd)` finite triple in range, made opaque. Service absence reports unavailable rather than exiting the application. The portal helper handles ping and stdin EOF. Add `compileDesktopPortal`, `testDesktopPortal` and a fake service in a private test bus; connect this native test to Linux check.

**Required regressions:** Delayed ReadAll after a newer signal, out-of-order key changes, owner A dies and B replies before A, no portal initially then registration, partial keys, incorrect D-Bus signatures, unknown enum values, invalid accent does not remove valid appearance, stop with a pending async read. Linux `./gradlew :jasper-app:testDesktopPortal` must exercise the fake service under its private bus without the user's session bus.

- [ ] **Step 4 — Confirm GREEN.** Run `./gradlew :jasper-app:test --tests '*DesktopPortalTest'` again. All selected tests must pass; inspect XML for failures, errors and skips. For native commands inspect their explicit test totals and exit codes. A skipped Linux extraction test is not a pass.

- [ ] **Step 5 — Commit and review.** Stage only the files listed for this task and commit with subject `feat: observe Linux appearance portal preferences` and the required co-author trailer. Obtain the task review and fix findings before the next dependent task.

### Task 3: GTK 3 and modern GTK 4/libadwaita extraction

**Files:** Create `packaging/desktop-colors/src/{gtk3.c,gtk4.c,gtk_api.c,gtk_api.h}`, `packaging/desktop-colors/test/{gtk3_test.c,gtk4_test.c,probe_test_support.c}`, `packaging/desktop-colors/test/themes/` fixtures, `jasper-app/src/test/java/dev/jasper/app/platform/DesktopGtkProtocolTest.java`. Extend native tasks/README and `.github/workflows/ci.yml` for Linux dependencies and virtual-display tests.

**Interfaces:** `jasper-desktop-gtk3 --protocol=1` and `jasper-desktop-gtk4 --protocol=1` consume Task 1 commands. `bool dc_sample_gtk3(bool dark, uint64_t generation, struct dc_palette *out)` and `dc_sample_gtk4` fill the normalized pair vocabulary. `dc_gtk_api_open(int major, struct dc_gtk_api *out)` resolves optional libraries and capabilities; close is idempotent. No GTK linkage in portal.c.

- [ ] **Step 1 — Add the failing regression.** Add this complete test method to the named test class, using its package, JUnit 5 `@Test`, and AssertJ static assertions. Add the required scenarios below as separate named regressions in the same task.

```java
@Test void incompletePairDoesNotBecomeAValidWindow() {
    String wire = "{\"v\":1,\"kind\":\"palette\",\"generation\":2,\"source\":\"gtk4\",\"appearance\":\"dark\",\"implementation\":\"libadwaita\",\"libraryVersion\":\"1.6\",\"window.bg\":\"0,0,0,1\",\"reason\":\"\"}";
    var message = (DesktopProtocol.PaletteMessage)
        DesktopProtocol.decode(wire.getBytes(StandardCharsets.UTF_8));
    assertThat(message.value().colors()).doesNotContainKey(DesktopObservation.Pair.WINDOW);
}
```

- [ ] **Step 2 — Confirm RED.** Run `./gradlew :jasper-app:test --tests '*DesktopGtkProtocolTest'`. Expect a missing new API compilation error initially, or the named behavioral assertion to fail after scaffolding. Record the actual failure; unrelated infrastructure failures do not establish RED.

- [ ] **Step 3 — Implement the task.** Implement the spec's extraction table, unmapped rooted widgets, GTK theme/preference notifications and optional libadwaita StyleManager notifications. Dynamic load `libgtk-3.so.0`, `libgtk-4.so.1`, and optional `libadwaita-1.so.0` with RTLD_LOCAL. Use opaque types and public ABI declarations in gtk_api.h, with runtime version checks; compile-time ABI verification against installed GTK headers is a separate CI target. This permits building on Ubuntu 22.04 without linking newer GTK symbols into the executable. Record every resolved symbol and its minimum version in README. Required APIs include init-check, settings/property access, root/widget lifecycle, style context and named lookup; GTK 4.10+ adds widget foreground lookup and 4.16+ permits variable probes. Resolve optional adw_init/style-manager symbols only when its library is present; plain GTK 4 remains distinguishable.

For modern variables, assign `color: var(--name, sentinel)` to dedicated label classes below an unmapped root. Run two probes with opaque magenta and cyan fallbacks: different results mean unavailable, identical results mean the variable resolved. Use an idle after style invalidation before reading; test actual override precedence. For GTK 4's original named-color route use lookup_color only when variables are unsupported, or a variable is missing and the corresponding compatibility fallback is explicitly marked partial. Modern variable values always win. Force Dark/Light only within the helper; verify actual WINDOW polarity and contrast instead of editing a theme name. On parent requests serialize dark/light samples and stamp each response with that request generation. On notifications resample the last requested appearance. Missing display/library produces a capability message and responds to ping. Destroy roots on replacement and exit on EOF.

Create `testDesktopGtk` using `xvfb-run -a` and a private D-Bus session, never the user's display. Use a native harness that overrides theme providers in-process with fixture CSS; it asserts native values before serialization. Run both old named-color and modern variable fixtures on appropriate Linux images. This task is a gate: do not advance to UI integration until modern libadwaita overridden variables are demonstrated. Lack of a Linux runner must be recorded as blocked native evidence, not replaced by Java fixtures.

**Required regressions:** Native assertions: `assert_rgba(window_bg, .1, .2, .3, 1)` for an overridden modern variable while the old name is a different color; real magenta and cyan values survive sentinel detection; missing var returns absent; missing pair half; forced variant unsupported; plain GTK 4 without libadwaita; GTK 3 only; neither toolkit; no mapped/presented roots; stylesheet notifications; two sequential preview variants; GTK4 runtime below 4.10 reports unsupported. Add Ubuntu 24.04 named-color coverage and GTK 4.16+ coverage in an Ubuntu 26.04 container (record its image digest and installed toolkit versions in task evidence). Runtime minimum and ABI symbol tests are mandatory. Linux `./gradlew :jasper-app:testDesktopGtk` prints toolkit versions and test counts.

- [ ] **Step 4 — Confirm GREEN.** Run `./gradlew :jasper-app:test --tests '*DesktopGtkProtocolTest'` again. All selected tests must pass; inspect XML for failures, errors and skips. For native commands inspect their explicit test totals and exit codes. A skipped Linux extraction test is not a pass.

- [ ] **Step 5 — Commit and review.** Stage only the files listed for this task and commit with subject `feat: extract GTK desktop palettes in isolated helpers` and the required co-author trailer. Obtain the task review and fix findings before the next dependent task.

### Task 4: Bounded process supervision and source lifecycle

**Files:** Create `jasper-app/src/main/java/dev/jasper/app/platform/{DesktopProcess,DesktopColors}.java`, tests `DesktopProcessTest.java`, `DesktopColorsTest.java`, and test-only `DesktopChildMain.java` in the same test package. Modify `SystemAppearance.java`, `SystemAppearanceTest.java`.

**Interfaces:** `DesktopColors.open(Path helperDirectory)` returns an AutoCloseable application-owned service; `current()`, `subscribe(Consumer<DesktopObservation>)` returning Runnable removal; `request(long generation, Set<Source> sources, boolean dark)`, `reload()`, `close()`. Add `SystemAppearance.open(DesktopColors desktop)` for Linux; macOS retains its existing observer. `DesktopProcess.start(List<String> command, Consumer<Message> messages, Consumer<String> unavailable)` owns one child and bounded streams. Package-private constructors accept ScheduledExecutorService, Executor, LongSupplier clock, and Function<List<String>,Process> launcher. No new test-only production interface.

- [ ] **Step 1 — Add the failing regression.** Add this complete test method to the named test class, using its package, JUnit 5 `@Test`, and AssertJ static assertions. Add the required scenarios below as separate named regressions in the same task.

```java
@Test void retryDelayIsBounded() {
    assertThat(java.util.stream.IntStream.range(0, 8)
        .mapToLong(DesktopProcess::retrySeconds).toArray())
        .containsExactly(1, 2, 4, 8, 16, 30, 30, 30);
}
```

- [ ] **Step 2 — Confirm RED.** Run `./gradlew :jasper-app:test --tests '*DesktopProcessTest' --tests '*DesktopColorsTest' --tests '*SystemAppearanceTest'`. Expect a missing new API compilation error initially, or the named behavioral assertion to fail after scaffolding. Record the actual failure; unrelated infrastructure failures do not establish RED.

- [ ] **Step 3 — Implement the task.** Use one serialized management executor, separate stdout/stderr drains and a scheduled timeout executor. Implement `static long retrySeconds(int attempt) { return attempt < 5 ? 1L << Math.max(0, attempt) : 30L; }`. Framing counts bytes before allocating/decode, recognizes LF, and drains stderr into a circular 16 KiB tail. Serialize commands; every outstanding request has a two-second timeout. Schedule ping each ten seconds; pong must match process/request generation. Resample active toolkit demand every 30 seconds; coalesce publications at 100 ms. Bounded pending work replaces obsolete requests rather than accumulating queues.

Native initialization cannot block the caller of open. Retire all callbacks before closing streams; destroy after EOF request and force-kill at 500 ms. Launch only absolute packaged paths, with no shell interpretation. Failure is a data event and retry, not an uncaught EDT exception. Stop unused toolkit children; retain the portal for application lifetime. Test startup/close races through real Java child processes and a deterministic scheduler. Feed Linux portal observations into the same SystemAppearance instance consumed by application policy; macOS startup must not create a Linux service.

**Required regressions:** Malformed UTF-8 split over reads, oversized line without newline, stderr flood concurrent with valid stdout, stdin write failure, child exit, stuck live child, timeout racing with valid reply, generation rollover across process restart, rapid request replacement, close before ProcessBuilder returns, repeated close, cancellation of all retries, no native child in headless default startup, and a simulated Linux open path explicitly enabled by injection. Last snapshots are immutable and listeners cannot prevent cleanup.

- [ ] **Step 4 — Confirm GREEN.** Run `./gradlew :jasper-app:test --tests '*DesktopProcessTest' --tests '*DesktopColorsTest' --tests '*SystemAppearanceTest'` again. All selected tests must pass; inspect XML for failures, errors and skips. For native commands inspect their explicit test totals and exit codes. A skipped Linux extraction test is not a pass.

- [ ] **Step 5 — Commit and review.** Stage only the files listed for this task and commit with subject `feat: supervise Linux desktop appearance helpers` and the required co-author trailer. Obtain the task review and fix findings before the next dependent task.

### Task 5: Pure palette selection, mapping and contrast repair

**Files:** Create `jasper-app/src/main/java/dev/jasper/app/appearance/{DesktopPalette,DesktopContrast,DesktopPaintPairs}.java`; create tests `DesktopPaletteTest.java`, `DesktopContrastTest.java` and resources `jasper-app/src/test/resources/dev/jasper/app/appearance/{desktop-role-map.tsv,desktop-paint-pairs.tsv}`. Modify no authored theme files.

**Interfaces:** `DesktopPalette.SourceMode { AUTO, GTK3, GTK4 }`; `DesktopPalette.Result(GeneratedTheme theme, Optional<Source> source, String status)`; `static Result resolve(boolean dark, SourceMode mode, String currentDesktop, DesktopObservation input)`. `static List<Source> preferredSources(SourceMode mode, String currentDesktop)`. `DesktopContrast.repair(Color color, Color background, double minimum)` returns Optional<Color>. `DesktopPaintPairs.validate(GeneratedTheme theme, boolean highContrast)` returns a list of named failing pairs after final derived-color/paint guard resolution. Test resources enumerate every SemanticColor and overridable DerivedColor.

- [ ] **Step 1 — Add the failing regression.** Add this complete test method to the named test class, using its package, JUnit 5 `@Test`, and AssertJ static assertions. Add the required scenarios below as separate named regressions in the same task.

```java
@Test void automaticUsesDesktopTokensAndExplicitSelectionStaysExplicit() {
    assertThat(DesktopPalette.preferredSources(DesktopPalette.SourceMode.AUTO, "ubuntu:GNOME"))
        .containsExactly(DesktopObservation.Source.GTK4, DesktopObservation.Source.GTK3);
    assertThat(DesktopPalette.preferredSources(DesktopPalette.SourceMode.AUTO, "not-gnome"))
        .containsExactly(DesktopObservation.Source.GTK3, DesktopObservation.Source.GTK4);
    assertThat(DesktopPalette.preferredSources(DesktopPalette.SourceMode.GTK3, "GNOME"))
        .containsExactly(DesktopObservation.Source.GTK3);
}
```

- [ ] **Step 2 — Confirm RED.** Run `./gradlew :jasper-app:test --tests '*DesktopPaletteTest' --tests '*DesktopContrastTest'`. Expect a missing new API compilation error initially, or the named behavioral assertion to fail after scaffolding. Record the actual failure; unrelated infrastructure failures do not establish RED.

- [ ] **Step 3 — Implement the task.** Choose only a source with a complete compatible WINDOW pair passing normal contrast; retain partial optional pairs from that single source. Fill the exact spec mapping over Slate Cobalt. Implement pure role families, then repair shared inks/surfaces and derive states. Avoid any global UIManager read. The role-map TSV columns are `role`, `family`, `input`, `fallback`; cover all roles including headers, terminal surrounds and status/hue inks. The paint-pair TSV is test evidence; production DesktopPaintPairs holds the same named requirements and tests assert parity. Enumerate actual default and classic kit uses rather than assuming every foreground pairs with every surface.

Repair evaluates the original color, then the 20 steps toward black and 20 toward white, retaining the passing candidate of minimum squared RGB distance; black candidates precede white and strictly-less replacement makes ties deterministic. Round each channel to nearest integer; use existing ColorMath.contrast. Composite alpha only with known parents. If a surface cannot share Jasper's ink, reject that surface override; if remaining neutral families fail, restore the whole neutral family. High contrast then uses opaque black/white neutral fallback and repaired foreground/border overrides as needed, with derived overrides for every legally overridable role that would otherwise lower contrast. Recheck actual painted colors; report failure if the engine cannot express a required pair instead of changing kit geometry or silently weakening thresholds. Keep terminal/editor/syntax/glass references from the bundled variant.

**Required regressions:** Both appearances, no portal, no GTK, valid portal accent alone, invalid optional accent, explicit GTK unavailable despite usable alternate, missing native variant, unknown/empty desktop, shared ink with a conflicting popover, alpha without parent, 4.5 and 7 thresholds, 3 boundary threshold, repair tie, fallback under partial extraction, all enum roles covered once. Compare editor/syntax/terminal equality before and after GTK-only changes. Assert both kits' final painted enabled text meets targets; classic-specific surfaces are included.

- [ ] **Step 4 — Confirm GREEN.** Run `./gradlew :jasper-app:test --tests '*DesktopPaletteTest' --tests '*DesktopContrastTest'` again. All selected tests must pass; inspect XML for failures, errors and skips. For native commands inspect their explicit test totals and exit codes. A skipped Linux extraction test is not a pass.

- [ ] **Step 5 — Commit and review.** Stage only the files listed for this task and commit with subject `feat: resolve desktop palettes with validated fallbacks` and the required co-author trailer. Obtain the task review and fix findings before the next dependent task.

### Task 6: Dynamic theme identity and configuration

**Files:** Create `jasper-app/src/main/java/dev/jasper/app/appearance/ThemePick.java` and `jasper-app/src/test/java/dev/jasper/app/appearance/DesktopThemeSelectionTest.java`. Modify `appearance/{ThemeSelection,ThemeLibrary,ThemeFolder,PluginThemes,ResolvedTheme}.java`, `config/{CoreSettings,ConfigSnapshot}.java`, and tests `ThemeLibraryTest`, `ThemeFolderTest`, `PluginThemesTest`, `config/CoreSettingsTest`, `ConfigTemplateTest`.

**Interfaces:** `ThemePick` is a sealed interface with two real implementations: `Authored(JasperTheme theme)` and `Desktop()`. Methods `String id()`, `GeneratedTheme generated(boolean dark, GeneratedTheme desktop)`, `TerminalScheme matching(boolean dark)`. Change ThemeSelection dark/light components from JasperTheme to ThemePick; its `theme(boolean)` returns ThemePick. Add `ResolvedTheme.of(boolean, ThemeSelection, GeneratedTheme desktop)`. Keep the existing overload using bundled Desktop fallback. Add `ConfigSnapshot.desktopSource()` as String validated by CoreSettings, builder setter and copy/from/default wiring; application converts it to SourceMode.

- [ ] **Step 1 — Add the failing regression.** Add this complete test method to the named test class, using its package, JUnit 5 `@Test`, and AssertJ static assertions. Add the required scenarios below as separate named regressions in the same task.

```java
@Test void desktopMatchUsesBundledTerminalScheme() {
    var pick = new ThemePick.Desktop();
    assertThat(pick.id()).isEqualTo("desktop-colors");
    assertThat(pick.matching(true)).isEqualTo(JasperTheme.bundled().matching(true));
    assertThat(pick.matching(false)).isEqualTo(JasperTheme.bundled().matching(false));
}
```

- [ ] **Step 2 — Confirm RED.** Run `./gradlew :jasper-app:test --tests '*DesktopThemeSelectionTest' --tests '*ThemeLibraryTest' --tests '*ThemeFolderTest' --tests '*PluginThemesTest' --tests '*CoreSettingsTest' --tests '*ConfigTemplateTest'`. Expect a missing new API compilation error initially, or the named behavioral assertion to fail after scaffolding. Record the actual failure; unrelated infrastructure failures do not establish RED.

- [ ] **Step 3 — Implement the task.** Add the enum-valued string setting `UI_THEME_DESKTOP_SOURCE`, default `auto`, values auto/gtk3/gtk4, label Desktop color source, included in CoreSettings registry and generated template. Store it independently of appearance policy and leave existing constructor callers compiling by updating builder-based fixtures and canonical constructors together.

Implement ThemePick explicitly; do not add a synthetic JasperTheme to JasperTheme.builtIns. `ThemeLibrary.desktopId(String)` recognizes the reserved id; `ThemeLibrary.reservedLabel(String)` returns Desktop colors for it, otherwise existing built-in/plugin ownership. Use this path consistently in folder read/import/remove and plugin descriptor validation, including disabled plugins. `ThemeSelection.of` recognizes the reserved id before authored lookup without an unknown-theme warning. Desktop matching returns Slate Cobalt's existing scheme ids, so no duplicate terminal schemes are listed. Update all `selection.theme(...)` consumers to use the new value contract. Settings metadata is added in Task 8. Existing imported-theme resolution, reserved plugin precedence and terminal-scheme validation stay unchanged.

**Required regressions:** Both picks Desktop, mixed authored/Desktop, named terminal independent of UI, invalid source token -> auto plus diagnostic, builder/from/default round trips, id collision in folder/import/enabled and dormant plugins, source remains saved on non-Linux, library refresh preserves dynamic identity, no invented desktop JSON or duplicated matching schemes. Non-Linux presentation is tested in Task 8.

- [ ] **Step 4 — Confirm GREEN.** Run `./gradlew :jasper-app:test --tests '*DesktopThemeSelectionTest' --tests '*ThemeLibraryTest' --tests '*ThemeFolderTest' --tests '*PluginThemesTest' --tests '*CoreSettingsTest' --tests '*ConfigTemplateTest'` again. All selected tests must pass; inspect XML for failures, errors and skips. For native commands inspect their explicit test totals and exit codes. A skipped Linux extraction test is not a pass.

- [ ] **Step 5 — Commit and review.** Stage only the files listed for this task and commit with subject `feat: add Desktop colors theme selection and settings` and the required co-author trailer. Obtain the task review and fix findings before the next dependent task.

### Task 7: Application ownership and transactional updates

**Files:** Create `jasper-app/src/main/java/dev/jasper/app/application/DesktopAppearance.java` and test `application/DesktopAppearanceTest.java`; create `jasper-app/src/test/java/dev/jasper/app/appearance/DesktopThemeTransactionTest.java`. Modify `application/{JasperApplication,ConfigurationController,SettingsAppearance}.java`, `appearance/ThemeController.java` and existing application/system-theme tests. Update package-info and architecture allowlists only for the actual allowed appearance-to-platform dependency if not already allowed.

**Interfaces:** `DesktopAppearance` owns DesktopColors, one resolver worker, cached raw observations keyed by source/polarity, and generation checks. `update(ConfigSnapshot config)`, `reload()`, `preview(DesktopPalette.SourceMode source, boolean dark, Consumer<DesktopPalette.Result> callback)` returning AutoCloseable demand, `current()` returning last applied Result, `subscribe(Runnable)` returning Runnable, `close()`. Constructor takes ThemeController, DesktopColors, String currentDesktop, Executor worker; test overload takes EDT Executor. `ThemeController.configure(boolean dark, ThemeSelection selection, UiFontConfig font, GeneratedTheme desktop)` routes to existing apply transaction. Existing configure/scheme paths retain the current desktop snapshot.

- [ ] **Step 1 — Add the failing regression.** Add this complete test method to the named test class, using its package, JUnit 5 `@Test`, and AssertJ static assertions. Add the required scenarios below as separate named regressions in the same task.

```java
@Test void equalDesktopSnapshotDoesNotReinstallTheLookAndFeel() throws Exception {
    javax.swing.SwingUtilities.invokeAndWait(() -> {
        var installs = new java.util.concurrent.atomic.AtomicInteger();
        var selection = ThemeSelection.of(ThemeLibrary.bundledOnly(), "desktop-colors", "desktop-colors",
            dev.jasper.app.config.TerminalSchemeChoice.MATCH, (key, problem) -> { throw new AssertionError(problem); });
        var started = ThemeController.start(true, selection, dev.jasper.ui.kit.Kit.DEFAULT,
            kit -> theme -> { installs.incrementAndGet(); return true; }, failure -> { throw failure; });
        var controller = started.controller();
        try {
            var first = controller.current().chrome();
            var equalCopy = new dev.jasper.ui.theme.GeneratedTheme(first.name(), first.dark(), first.author(),
                first.semantic(), first.terminal(), first.editor(), first.syntax(), first.glass(), first.derivedOverrides());
            int before = installs.get();
            controller.configure(true, selection, dev.jasper.app.config.UiFontConfig.defaults(), equalCopy);
            assertThat(installs.get()).isEqualTo(before);
            assertThat(controller.current().chrome()).isEqualTo(first);
        } finally { controller.close(); }
    });
}
```

- [ ] **Step 2 — Confirm RED.** Run `./gradlew :jasper-app:test --tests '*DesktopAppearanceTest' --tests '*DesktopThemeTransactionTest' --tests '*SystemThemeApplicationTest' --tests '*ThemeControllerFallbackTest'`. Expect a missing new API compilation error initially, or the named behavioral assertion to fail after scaffolding. Record the actual failure; unrelated infrastructure failures do not establish RED.

- [ ] **Step 3 — Implement the task.** First replace ThemeController's identity-only chrome change decision with value equality while preserving font-change installation and library selection notifications. Add the configure overload and ensure configureScheme cannot silently reset Desktop colors to bundled fallback.

Application bootstrap creates one DesktopColors service on Linux and passes it into SystemAppearance; start with bundled fallback immediately. After startup construct DesktopAppearance and register shutdown before async work can publish. ConfigurationController forwards saved choices and portal changes through one resolution path; avoid its old systemAppearanceChanged branch reinstalling a stale selection independently. A matching generation captures config revision, effective appearance, source mode and portal owner; worker completion checks all before EDT apply. Keep cached raw compatible palettes through temporary failure; re-resolve them with current portal accent/contrast. Clear source attribution when explicitly switching to a source with no cache. Automatic alternate output can publish while retrying the preferred source.

Commit current Result/status only after successful theme installation. Failed apply retains current theme, leaves the requested snapshot eligible for retry, and uses existing error reporting. Reload config resets probes and retries even equal requested palettes after an installation failure. Stop toolkit demand when neither an applied pick nor a preview requires Desktop. Disposing preview demand cannot close application-owned services. Expose current status separately from palette equality so service recovery can refresh status without repainting.

**Required regressions:** In addition to the installer-count regression above, add injected-controller tests proving no installer call for equal snapshots, one call for changed palettes, failed transaction rollback then equal-request retry, source change while old reply is queued, preview response vs applied response, last-good cache invalidation on polarity/contrast, current portal over cached toolkit, no helpers for authored-only config, no config write/dirty draft, all windows receive one binding update, font and terminal-scheme reload preserve desktop palette, startup failure cleanup and close-before-worker-return.

- [ ] **Step 4 — Confirm GREEN.** Run `./gradlew :jasper-app:test --tests '*DesktopAppearanceTest' --tests '*DesktopThemeTransactionTest' --tests '*SystemThemeApplicationTest' --tests '*ThemeControllerFallbackTest'` again. All selected tests must pass; inspect XML for failures, errors and skips. For native commands inspect their explicit test totals and exit codes. A skipped Linux extraction test is not a pass.

- [ ] **Step 5 — Commit and review.** Stage only the files listed for this task and commit with subject `feat: apply live desktop colors transactionally` and the required co-author trailer. Obtain the task review and fix findings before the next dependent task.

### Task 8: Source selector, staged previews and passive status

**Files:** Modify `jasper-app/src/main/java/dev/jasper/app/settingsui/{AppearanceChoices,AppearanceSelection,CorePages,ThemeMenu}.java`, `application/SettingsAppearance.java`. Create tests `settingsui/DesktopSourceSelectionTest.java`; extend `settingsui/AppearancePageTest.java`, `application/SettingsAppearanceTest.java`, and `settingsui/AppearanceSelectionTest.java`.

**Interfaces:** Add to AppearanceChoices a concrete `DesktopChoices` record in `settingsui/DesktopChoices.java`: `(boolean available, Supplier<String> status, BiFunction<String,Boolean,Option.Palette> preview, Function<Runnable,Subscription> onChanged, Function<Runnable,Subscription> acquire)`. Acquire owns preview demand for the page lifetime; preview reads cached results and schedules missing work through the application adapter, never blocks. Provide an unavailable singleton for existing fixture construction. Dynamic metadata is added in SettingsAppearance, not JasperTheme.builtIns.

- [ ] **Step 1 — Add the failing regression.** Add this complete test method to the named test class, using its package, JUnit 5 `@Test`, and AssertJ static assertions. Add the required scenarios below as separate named regressions in the same task.

```java
@Test void sourceSettingDefaultsToAutomatic() {
    assertThat(CoreSettings.UI_THEME_DESKTOP_SOURCE.defaultValue()).isEqualTo("auto");
    assertThat(ConfigSnapshot.defaults().desktopSource()).isEqualTo("auto");
    assertThat(ConfigSnapshot.defaults().toBuilder().desktopSource("gtk4").build().desktopSource())
        .isEqualTo("gtk4");
}
```

- [ ] **Step 2 — Confirm RED.** Run `./gradlew :jasper-app:test --tests '*DesktopSourceSelectionTest' --tests '*AppearancePageTest' --tests '*SettingsAppearanceTest' --tests '*AppearanceSelectionTest'`. Expect a missing new API compilation error initially, or the named behavioral assertion to fail after scaffolding. Record the actual failure; unrelated infrastructure failures do not establish RED.

- [ ] **Step 3 — Implement the task.** Add source selection to the existing AppearanceSelection composite so conditional visibility reacts to its staged theme picks. Include the hidden source schema row in CorePages for search/reset/save ownership, and implement modified values/reset/rebase alongside appearance/dark/light companions. Source is a separate persisted value retained when both theme picks become authored; reset of the appearance composite returns all four settings to defaults. Preserve field-level external rebase behavior: an externally changed source updates only a clean source field; dirty source retains its draft and conflict expectations. Use stable node ids `:desktop-source` and `:desktop-status` under the existing editor id.

Add the reserved theme option after authored built-ins on Linux, with current cached preview or bundled fallback. On non-Linux omit it unless selected, then label Desktop colors (unavailable on this platform). Disable Remove for the reserved id; keep ordinary Import available. Source labels are exactly the spec's three labels. Status describes applied source; when staged source differs prefix the draft indication with Pending Apply and retain applied status separately. Data-change callbacks refresh the composite without recreating focusable controls. Page close releases preview demands, and events from the retired page do nothing.

**Required regressions:** Rendered page assertions for selector visibility with either staged pick, default/each source label, Apply/Cancel, reset, search reveal, source-only external rebase, dirty source conflict, leaving and reopening page without helper leak, matching preview polarity, stale preview completion after source change/page close, pending vs applied status, non-Linux retained choice, toolkit absent status, default and Enterprise compositions, source change never edits terminal choice.

- [ ] **Step 4 — Confirm GREEN.** Run `./gradlew :jasper-app:test --tests '*DesktopSourceSelectionTest' --tests '*AppearancePageTest' --tests '*SettingsAppearanceTest' --tests '*AppearanceSelectionTest'` again. All selected tests must pass; inspect XML for failures, errors and skips. For native commands inspect their explicit test totals and exit codes. A skipped Linux extraction test is not a pass.

- [ ] **Step 5 — Commit and review.** Stage only the files listed for this task and commit with subject `feat: expose desktop color source and live preview status` and the required co-author trailer. Obtain the task review and fix findings before the next dependent task.

### Task 9: Final paint validation and retained editor/terminal state

**Files:** Create `jasper-app/src/test/java/dev/jasper/app/appearance/DesktopColorsRenderTest.java`, `application/DesktopColorsRetentionTest.java`, and `jasper-app/src/test/resources/dev/jasper/app/appearance/desktop-palettes.jsonl`. Extend existing staged Editor plugin theme tests via `plugins/editor/src/test/java/dev/jasper/editor/ui/EditorThemeTest.java` only if the app test cannot exercise its supported facade. Modify DesktopContrast/DesktopPaintPairs only to fix demonstrated violations; shared kit changes require explicit recorded design review.

**Interfaces:** Reuse DesktopPalette.resolve and existing headless staged-plugin/application fixture owners. Add fixture palettes for GTK3, libadwaita override, plain GTK4, partial source, mismatched appearance, extreme accent and high contrast. Capture final painted colors through the actual default/classic kit routes, not a second approximate resolver.

- [ ] **Step 1 — Add the failing regression.** Add this complete test method to the named test class, using its package, JUnit 5 `@Test`, and AssertJ static assertions. Add the required scenarios below as separate named regressions in the same task.

```java
@Test void desktopFallbackPreservesContentPalettes() {
    var portal = new DesktopObservation.Portal(1, DesktopObservation.Polarity.DARK,
        Optional.empty(), Optional.of(false), "");
    var result = DesktopPalette.resolve(true, DesktopPalette.SourceMode.AUTO, "GNOME",
        new DesktopObservation(portal, Map.of()));
    var bundled = JasperTheme.bundled().generated(true);
    assertThat(result.theme().terminal()).isEqualTo(bundled.terminal());
    assertThat(result.theme().editor()).isEqualTo(bundled.editor());
    assertThat(result.theme().syntax()).isEqualTo(bundled.syntax());
}
```

- [ ] **Step 2 — Confirm RED.** Run `./gradlew :jasper-app:test --tests '*DesktopColorsRenderTest' --tests '*DesktopColorsRetentionTest' --tests '*DesktopPaletteTest'`. Expect a missing new API compilation error initially, or the named behavioral assertion to fail after scaffolding. Record the actual failure; unrelated infrastructure failures do not establish RED.

- [ ] **Step 3 — Implement the task.** Render buttons, menus, text fields, selection, tables/trees, tabs, sidebar, status, dialogs and Settings under Light/Dark and both kits for each fixture. Assert all role-map and paint-pair inventory entries are exercised; disabled entries are classified explicitly. Check GTK-only changes keep terminal/editor values equal and application binding updates retain document, caret, selection, scroll and undo owners. Fixed terminal schemes remain equal across Light/Dark. Test actual high-contrast derived/paint guards; if a guard lowers contrast, repair legal generated overrides and recheck both normal and high-contrast renders. Do not relax threshold assertions to make them pass. Save deterministic fixture images and a compact measured-pair report under build output; compare unchanged authored theme gallery digests against the execution baseline.

**Required regressions:** Low-contrast imported surface with shared text, transparent native input, white/yellow portal accent, high-contrast transition while Settings dirty, multiple app windows with open menus, unchanged authored theme renders, visible terminal/editor content survives equal and unequal snapshots. Native keyboard focus remains user acceptance; headless tests verify component/document identity and simulated selection, not OS focus.

- [ ] **Step 4 — Confirm GREEN.** Run `./gradlew :jasper-app:test --tests '*DesktopColorsRenderTest' --tests '*DesktopColorsRetentionTest' --tests '*DesktopPaletteTest'` again. All selected tests must pass; inspect XML for failures, errors and skips. For native commands inspect their explicit test totals and exit codes. A skipped Linux extraction test is not a pass.

- [ ] **Step 5 — Commit and review.** Stage only the files listed for this task and commit with subject `verify: cover desktop palette rendering and content retention` and the required co-author trailer. Obtain the task review and fix findings before the next dependent task.

### Task 10: Distribution integration, documentation and final verification

**Files:** Modify `gradle/linux-desktop-colors.gradle`, `gradle/packaging.gradle`, `jasper-app/build.gradle.kts`, `.github/workflows/{ci,linux-packages}.yml`, `packaging/linux/check-install.sh`, `packaging/desktop-colors/README.md`, `docs/themes.md`, `docs/app-maintenance.md`, `docs/STATUS.md`. Create `jasper-app/src/test/java/dev/jasper/app/platform/DesktopHelperPathsTest.java` and extend `jasper-app/src/test/java/dev/jasper/app/architecture/PackagedResourcesTest.java`.

**Interfaces:** `DesktopColors.helperDirectory()` resolves an explicit development system property `jasper.desktopColors.helpers`, then the installed code-source-relative `lib/desktop-colors` directory using existing distribution path conventions. It returns unavailable if no valid directory exists; never searches PATH or the working directory. New tasks `compileDesktopHelpers`, `stageDesktopHelpers`, `verifyDesktopHelpers` build all three executables and assert runtime dependency boundaries.

- [ ] **Step 1 — Add the failing regression.** Add this complete test method to the named test class, using its package, JUnit 5 `@Test`, and AssertJ static assertions. Add the required scenarios below as separate named regressions in the same task.

```java
@Test void helperNamesAreFixedAndContainNoShellFragments() {
    assertThat(DesktopColors.helperNames()).containsExactly(
        "jasper-desktop-portal", "jasper-desktop-gtk3", "jasper-desktop-gtk4");
    assertThat(DesktopColors.helperNames()).allMatch(name -> name.matches("[a-z0-9-]+"));
}
```

- [ ] **Step 2 — Confirm RED.** Run `./gradlew :jasper-app:test --tests '*DesktopHelperPathsTest' --tests '*PackagedResourcesTest'`. Expect a missing new API compilation error initially, or the named behavioral assertion to fail after scaffolding. Record the actual failure; unrelated infrastructure failures do not establish RED.

- [ ] **Step 3 — Implement the task.** Wire helper staging into Linux installDist and jpackage resources and set the development property on run tasks without running them. Linux helpers are ELF executables for the build host architecture; run `readelf`/`ldd` checks confirming portal has no GTK dependency and probe binaries have no DT_NEEDED GTK/libadwaita entries. GLib/GIO are runtime dependencies; toolkit libraries remain optional. Keep Ubuntu 22.04/glibc 2.35 package baseline and verify on the existing newer/debian install matrix. Compile-only ABI checks compare runtime declarations to GTK 3/4 headers in the richer CI image. Add new Gradle script to Linux packaging workflow path filters. Update Linux CI dependency installation for GLib headers, compiler/pkg-config, GTK runtimes, libadwaita, dbus tools and Xvfb; explicitly test modern GTK 4.16+ on an Ubuntu 26.04 container, recording its digest, alongside Ubuntu 24.04.

Add Java path tests for installed, development, missing, spaces and non-Linux paths. verifyPackage asserts helper presence/mode/architecture only on Linux; macOS/Windows distributions omit them. Update theme docs with the new choice/source/status and interface-only scope; add a focused maintenance recipe for protocol diagnostics and native fixture tests. Record actual commands, XML totals, native versions, omitted environments and remaining user acceptance in STATUS. Do not claim native acceptance from headless tests.

Final commands: `./gradlew check --continue`, `./gradlew :jasper-app:installDist :jasper-app:packageApp :jasper-app:verifyPackage` without AOT properties. On Linux additionally `./gradlew :jasper-app:testDesktopProtocol :jasper-app:testDesktopPortal :jasper-app:testDesktopGtk :jasper-app:verifyDesktopHelpers`. Run repository source-hygiene Python check and `git diff --check`. Read all module test XML for exact counts. Independent final whole-branch review follows all task reviews; fix findings and rerun only affected tests plus required gates. User performs the spec's GNOME/GTK3/KDE and Wayland/X11 desktop acceptance matrix; no unattended GUI launch.

**Required regressions:** Missing optional libraries after installation, fallback portal-only distribution, no toolkit binaries on other OSes, executable paths with spaces, x86_64/aarch64 packaging through existing runners, helper response with all GTK libraries absent, authored-only launch has no GTK children, clean application shutdown leaves no helpers.

- [ ] **Step 4 — Confirm GREEN.** Run `./gradlew :jasper-app:test --tests '*DesktopHelperPathsTest' --tests '*PackagedResourcesTest'` again. All selected tests must pass; inspect XML for failures, errors and skips. For native commands inspect their explicit test totals and exit codes. A skipped Linux extraction test is not a pass.

- [ ] **Step 5 — Commit and review.** Stage only the files listed for this task and commit with subject `feat: package and document Linux Desktop colors` and the required co-author trailer. Obtain the task review and fix findings before the next dependent task.

## Spec coverage and self-review

| Spec section | Owning tasks |
|---|---|
| 1. Scope and content palettes | 5, 6, 9 |
| 2. Settings, identity and status | 6, 8 |
| 3. Ownership/data flow | 1, 4, 7 |
| 4. Portal and source policy | 2, 5, 7 |
| 5. GTK extraction | 3 |
| 6. Mapping/readability | 5, 9 |
| 7. Lifecycle/protocol | 1, 4, 7, 8 |
| 8. Packaging/verification | 3, 9, 10 |
| 9. Native-first sequence | 1–3 before 4–10 |

The wire schema, optional plain GTK4 path, retry/heartbeat schedule, source ownership and dynamic ThemePick contract
are implementation decisions within the approved spec. Native extraction remains the first empirical gate; this
plan does not assert it has been tested. Source/appearance generation checks and complete output snapshots are
mandatory at every async boundary. Resolve any discovery requiring a spec change before executing dependent tasks.

**Plan review boundary:** The user reviews this written plan before execution. The repository supplies a
subagent-driven default with per-task review; preserve it unless the user explicitly chooses native inline execution.
The planning task creates documentation only and does not start implementation or native probes.
