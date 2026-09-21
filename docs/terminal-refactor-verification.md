# Terminal refactor verification

Execution branch: `codex/terminal-refactor-native`, independently from production
base `0f82c55` and plan commit `bf038c6`. Native execution was explicitly selected;
there are no per-task subagents, and one independent final review remains required.

## Baseline (2026-09-20)

JBR 25.0.4.1+1-b583.48, macOS aarch64. A fresh isolated full check and then a
focused title suite reproduced the same three existing title failures as planning.
A controlled JBR ProcessBuilder probe launched `/bin/sh -c 'printf ready; read step'`,
waited for readiness and queried `Process.info().command()`: `Optional[/bin/bash]`.
`ps` still displayed `/bin/sh`; the Java metadata is what the production resolver
uses. The two timeouts also compare literal `(sh)` titles. The test fixture now
launches `/bin/bash --noprofile --norc -c` explicitly and expects that name;
platform absence is an explicit test assumption. No production title behavior,
wait budget, or title/event ordering assertion changed. Focused 5/5 passed.

## Headless baseline

Run `./gradlew :jasper-terminal:refactorMeasurement`. It starts no PTY or native
window. Each of plain and mixed fixtures has 150 × 45 cells, 10,000 history rows,
JetBrains Mono 14, default palette, 1,000 warm-up operations, and five batches of
1,000 measured operations on the EDT. Allocation is current-thread allocated
bytes; timing is Java CPU-path elapsed time, not FPS or native RSS. One JVM run
with five measured batches is the initial baseline; comparison runs use the same
fixture/runtime and report variability rather than unit-test timing thresholds.

| Fixture | Median ms/op [batch range] | Bytes/op |
| --- | --- | --- |
| Plain capture | 0.0023 [0.0023–0.0027] | 11,768 |
| Plain capture + paint | 1.1900 [1.1824–1.2053] | 1,067,656 |
| Mixed capture | 0.0019 [0.0018–0.0019] | 13,096 |
| Mixed capture + paint | 0.5904 [0.5823–0.5930] | 1,227,040 |

Mouse-report efficiency remains a behavioral zero-line-read/no-repaint regression.
Native throughput (35 MB/s floor, 45 MB/s target), visual behavior and RSS remain
user-run; none is established by these headless measurements.

## Execution rulings

- Native execution replaces per-task implementation/review agents, following the
  user's explicit request. Cost: no independent checkpoint review until the end.
- Work is independent of the older refactor branch, following the user's choice.
  Cost: some source exploration/extraction work is duplicated.
- The title fixtures name their actual executable explicitly after the Java
  metadata probe. Cost: these Unix-specific integration tests require Bash.

Full baseline after fixture corrections: 1,050 tests, 1,048 passed and two environment skips, zero failures/errors. The tab-title override test showed the same `(sh)`/`(bash)` mismatch in the full run and received the same explicit-executable fixture correction.

## Vendor-free row boundary (Task 4)

The initial full-width cell arrays failed the allocation gate: plain capture
60,336 bytes / 0.0186 ms; mixed capture 76,176 bytes / 0.0146 ms. The next compact
candidate still converted styles under the lock and cost 0.0037/0.0056 ms.
The accepted representation copies entry membership and text-length metadata,
then converts styles during cell reads outside the lock for detached rows.
Live query rows use the same Jasper contract only while holding the lock.
Both are real production implementations of `TerminalRow`.

JediTerm 3.76 replaces text entries on writes; its entry-copy implementation
shares them too. The one in-place entry mutation changes trailing NUL padding
to spaces on append. Detached rows preserve the pre-append text length and
normalize NUL cells to spaces, protecting both text and cell semantics. Tests
cover overwrite, output-array mutation and append-after-NUL independence. This
pinned-vendor assumption must be rechecked on a dependency upgrade.

Same JBR, fixtures, warmup and batching as baseline:

| Operation | Median ms | Range ms | Bytes/op |
| --- | ---: | ---: | ---: |
| Plain capture | 0.0014 | 0.0008–0.0015 | 4,281 |
| Plain capture + paint | 1.1572 | 1.1494–1.1677 | 800,240 |
| Mixed capture | 0.0008 | 0.0007–0.0008 | 3,792 |
| Mixed capture + paint | 0.5569 | 0.5544–0.5573 | 655,528 |

Capture allocation and time improve over baseline; scratch-array reuse also
reduces painting allocation. These remain headless samples, not native FPS/RSS.
