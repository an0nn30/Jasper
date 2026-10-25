# Buddy Stand-up Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development task-by-task.

**Goal:** Animate the end of thinking through laptop closure and rising, instead of snapping to standing.

**Architecture:** Append three sprite cells without altering existing artwork. Add a finite RISING state to the JDK-only animator and reschedule from the presentation owner on intent changes.

**Tech Stack:** Java 25/JBR, Swing, JUnit/AssertJ, built-in ImageGen, existing Swift/Pillow sprite packing/export tools.

**Spec:** `docs/superpowers/specs/2026-10-24-buddy-stand-up-design.md`.

**Status:** authorized for execution; tasks pending. Reuse current checkout on `codex/buddy-stand-up`, based on `c4dda9b0b` (includes the completed tab-aware work). Plan pins contracts, timing and algorithms; artwork packing coordinates and fixture adaptation remain implementer decisions, a recorded deviation from complete-code planning.

## Global Constraints

- Existing 20 frames remain pixel-identical; append CLOSE_LAPTOP, LOWER_LAPTOP, RISE at columns 20–22.
- Source cells 84 × 96; logical geometry 42 × 48; binary alpha, transparent side/bottom margins, nearest-neighbor sampling.
- Sequence: TYPE_REST 100 ms, CLOSE_LAPTOP 125 ms, LOWER_LAPTOP 125 ms, RISE 150 ms, IDLE at 500 ms.
- New work interrupts rising immediately; repeated false and greet/poke do not restart or interrupt it.
- Buddy remains JDK-only; no SDK/facade or application notification policy changes.
- Serialize all Gradle commands. No GUI, benchmark, AOT, merge or push. Every commit has a Co-Authored-By trailer.

## Review Focus

1. Character size/ground jitter or laptop popping at the endpoints: inspect a timed production-animator preview (Task 2).
2. New work in each rise phase: immediately typing and fresh later transition (Task 2).
3. Intent change between timer deadlines: transition starts from current time and timer is rescheduled (Task 2).
4. Hide/show or clear while spawning/greeting/waking: no phantom rise (Task 2).
5. Export/packing accidentally alters existing frames or erases added cells on re-export: pixel-compare baseline and round-trip master (Task 1).

### Task 1: Add matching transition artwork

**Files:** `BuddyFrame.java` and `BuddySpriteTest.java` in their existing Buddy animation/presentation packages; `packaging/buddy/generate.py`, `packaging/buddy/jasper-buddy.ase`, runtime `jasper-buddy.png`; new source atlas/prompt notes under `docs/design/jasper-stand-up/`; dedicated deterministic pack script under `packaging/buddy/` if required. Update `packaging/buddy/README.md` with new columns and reproducible commands.

**Interfaces:** append `CLOSE_LAPTOP, LOWER_LAPTOP, RISE` to BuddyFrame after TYPE_REST; total 23 frames, strip 1932 × 96. Existing enum ordinals unchanged. Exporter FRAMES appends `close_laptop`, `lower_laptop`, `rise`.

- [ ] Add failing sprite tests pinning column20/21/22, total23, new poses distinct, all current alpha/margin checks. Snapshot old first20 cells (pixel digest in test or comparison fixture) to prove preservation. Run focused test for RED.
- [ ] Generate one transparent three-pose atlas with built-in ImageGen, using existing `docs/design/jasper-pixel/source-atlas.png` as reference after viewing it. Pose1: seated, lowering lid of same laptop; pose2: feet planted, closed laptop lowered aside/out of view; pose3: halfway rising, legs extending toward existing IDLE pose, laptop absent. Keep character/style consistent. Save selected original output and exact prompt, tool provenance locally. Never use Python to draw or synthesize replacement poses.
- [ ] Inspect output. Pack measured cells with shared scale and common foot baseline into appended 84 × 96 cells, using a deterministic Swift/CoreGraphics or existing asset pipeline. Preserve original strip pixel values, not re-rendered source atlas. Require transparent gutter detection/explicit measured coordinates and opaque bounds checks. Do not overwrite old20 frames through the historical 20-frame atlas packer. Extend exporter and import appended strip into master, then verify exact master/runtime parity.
- [ ] Run exporter `--verify-only`, `./gradlew :jasper-buddy:check verifyApplicationArchitecture`; inspect enlarged old/new frames and check no clipped head/feet/laptop. Record RED/GREEN and asset paths/prompt in report.
- [ ] Commit `feat: add Buddy laptop-close and rising poses` with coauthor trailer.

### Task 2: Animate working-to-standing and verify delivery

**Files:** `jasper-buddy/src/main/java/dev/jasper/buddy/internal/animation/BuddyAnimator.java`; `internal/presentation/BuddyWindow.java`; animator/presentation tests; new `BuddyStandUpPreview.java` in test presentation package plus JavaExec task in `jasper-buddy/build.gradle.kts`; current Buddy guides/STATUS/this plan.

**Interfaces:** Task1 supplies the three frame constants. Keep existing animator `setWorking(boolean)` callers working; introduce `setWorking(boolean value, long now)` as the clock-explicit entry point if needed, and have presentation call it then `paintAndSchedule()`. No facade signature change. Old entry point delegates with lastTick for existing deterministic callers. `RISING` ignores greet/poke, yields to true working intent.

- [ ] Tests first: begin working after spawn, tick to a known time, clear work and assert TYPE_REST immediately, CLOSE_LAPTOP at100ms, LOWER_LAPTOP at225ms, RISE at350ms, IDLE at500ms; no earlier transition. At each new state assert nextDue. Repeat false without extending deadlines. Renew true at0/100/225/350ms: TYPE_A and next typing step+250ms, then a subsequent false starts a fresh500ms sequence. Test late tick after500ms, idle sitting20s after completion, greet/poke ignored mid-rise, hide resets schedule/show spawns, work clearing during busy stages and off-boundary current-time entry point.
- [ ] Run `./gradlew :jasper-buddy:test --tests '*BuddyAnimatorTest'` and capture expected RED.
- [ ] Implement finite phase table and mode. Use `List.of(TYPE_REST,CLOSE_LAPTOP,LOWER_LAPTOP,RISE)` and nanos durations `[100,125,125,150]ms`. Start rising with step0/frame first/stepDue=now+100ms, blinking=false. Advance using:

```java
while (now >= stepDue) {
    long at = stepDue;
    if (++step == RISING_FRAMES.size()) { standUp(at); return; }
    frame = RISING_FRAMES.get(step);
    stepDue = at + RISING_DURATIONS[step];
}
```

Dispatch RISING in tick; default greet/poke ignores it. `setWorking(false, now)` ticks current state before changing intent, starts rise only from WORKING. `setWorking(true, now)` starts typing immediately from RISING as well as existing idle modes. Repeating same intent advances time if necessary but does not restart phase. Avoid unbounded catch-up work where adding timestamp advancement would amplify the existing typing loop; use arithmetic phase advancement for elapsed typing cycles if required, covered by a long-gap equivalence test. `standUp` still handles other animation endings instantly and restarts idle origin at the precise completion timestamp. Window setter calls time-explicit animator then paintAndSchedule, ensuring the new first deadline replaces any pending typing/blink timer.
- [ ] Add headless preview from production animator and committed sprites: normal finish and renewed-work interruption, contact sheet with timestamps and animated GIF using ImageIO or existing tooling. Verify frame timing from rendered preview, transparent/background composition, actual size and enlarged. Add owner scheduling coverage through a headless seam only if it tests real production logic; no test-only production API.
- [ ] Run Buddy check, architecture, exporter verification. Run full `./gradlew check --continue -Pjasper.python=/Users/dustin/.local/share/uv/python/cpython-3.13.15-macos-aarch64-none/bin/python3.13` serialized; count XML before targeted runs replace results. Update current guides minimally: sprite23/frame dimensions, 500ms working end transition, interruptions and preview recipe. Add new design record to index if current index lists such records individually; otherwise existing project-record index covers it. Record deviations, exact check results and pending GUI check in STATUS/plan.
- [ ] Run relevant documentation checks after edits, source hygiene and diff check. Commit `feat: animate Buddy standing up after thinking` with coauthor trailer.
- [ ] Controller task review, fixes as needed, then whole-branch review versus c4dda9b0b. Record outcome; no merge/push. User GUI check: finish final running command and watch standing transition; start new work midway.
