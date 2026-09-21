# Jasper terminal: maintainable architecture and developer onboarding

**Date:** 2026-09-20

**Status:** Approved by the user on 2026-09-20. The [implementation plan](../plans/2026-09-20-jasper-terminal-refactor.md) is executing natively on the independently created `codex/terminal-refactor-native` branch. Tasks 1–11 are complete; documentation and final review are in progress. The final connector adapter belongs to internal.emulation (vendor confinement), with native ownership in internal.process. Row capture uses the measured compact detached adapter described in the verification report.

**Scope:** `jasper-terminal`, its documentation and tests, and migration of affected repository callers.

**Baseline:** `0f82c55`; source inspection and a fresh terminal test run on 2026-09-20.

## 1. Intent and authority

Jasper must be understandable and maintainable by its developer without requiring
an AI assistant to reconstruct the architecture. A developer must be able to
locate a behavior, identify its state owner and dependencies, understand its
threading and lifecycle contracts, change it, and find the relevant tests.

The user requested a packaged, modular Java terminal library with appropriate
design patterns, fluent configuration, substantial documentation, onboarding,
and feature-development recipes. A future plugin SDK will support both core and
external plugins. This refactor prepares boundaries for that work.

The user explicitly permits reorganizing the public terminal API and updating
its callers in `jasper-app`, preserving user-visible behavior. Source and binary
compatibility with the old flat package are not acceptance requirements. The
broader application refactor is a later project.

This specification amends the structural-redesign restriction in the
[Phase 1 design](2026-09-10-jasper-phase-1-terminal-design.md) for this refactor.
Other existing behavioral specifications and repository constraints remain in
force, including later approved amendments to Phase 1. It does not reinstate
superseded shortcut, appearance, or process-exit behavior from the original spec.

Keep Java/JBR 25, Swing, the two existing Gradle modules, and the pinned runtime
dependencies. `jasper-terminal` never depends on `jasper-app`. No emulator-backend
abstraction, new plugin framework, dependency upgrade, or new production
interface without two real implementations is part of this work. Existing
listener contracts remain valid; standard JDK functional interfaces may be used.

## 2. Assessment and selected approach

The module has 32 production Java files and 3,849 lines, all in
`dev.jasper.terminal`. `TerminalView` has 1,175 lines and `TerminalSession` has
814: together, 51.7% of the module. There is no module README or package-info
documentation. Existing comments and historical design documents contain useful
engineering knowledge, but do not provide an effective onboarding path.

Specific structural problems are:

- Session construction, emulation, buffer queries, coordinates, shell events,
  and process metadata are coupled through `TerminalSession`.
- View lifecycle, gestures, selection, search workers, repaint scheduling,
  bells, and desktop integration are coupled through `TerminalView`.
- Search and selection extract cells through `RunBuilder.readCells`, making
  text operations depend on rendering.
- Package-private access obscures conceptual dependencies; indiscriminately
  making moved classes public would turn these into accidental APIs.
- The listener's blanket reader-thread promise does not describe callbacks
  triggered by caller-thread resize/history operations.
- Some tests inspect private fields reflectively instead of exercising the
  collaborator responsible for that behavior.

Preserve the existing strengths: immutable configuration, focused algorithms,
connector adapters/decorators, bounded workers and caches, snapshot rendering,
absolute-row invalidation, and concurrency and Unicode regressions.

Selected approach: staged responsibility extraction, then explicit package and
API boundaries, with documentation delivered alongside every extraction.
Package moves alone leave the reasoning burden intact. A rewrite around a
plugin/backend framework would require unmade product decisions and put tested
terminal behavior at unnecessary risk.

## 3. Package structure and supported surface

```text
dev.jasper.terminal
  session/
    TerminalSession, SessionLaunchOptions, TerminalSessionListener
    PtySessionFactory (package-private composition helper)
  view/
    TerminalView, TerminalAction
    package-private keyboard, mouse, selection, search, rendering and bell controllers
  config/
    TerminalOptions, Palette, CursorStyle, BellMode, OptionAsMeta, GridSize
  search/
    SearchQuery, FindResult
  rendering/
    FontSet
  internal/
    emulation/  JediTermEngine, SessionDisplay, BufferQueries, vendor adapters
    process/    PtyChild process ownership, ForegroundJobResolver
    shell/      stream filtering, custom-command decoding, ShellCommandTracker
    text/       absolute-row state, copied-row access, text and selection algorithms
    rendering/ screen snapshots, runs, style resolution, painter
    desktop/   clipboard access and bounded browser dispatch
```

The named supported types above form the intended application-facing surface;
their builders and documented result members are included. `GridSize` belongs
with shared values so the engine does not depend on the session facade.
`FontSet` remains available for existing app sizing and warm-up use, with its
thread and ownership contract documented. Palette is a rendering configuration
value, not a TOML parser or theme-file loader.

Controllers remain package-private beside the Swing component. Helpers under
`internal` are unsupported implementation details even where Java requires
public visibility for cross-package collaboration. Any necessary session/view
bridge must be narrowly defined, explicitly marked internal, and excluded from
the supported API allowlist. It must not expose JediTerm types or the live buffer.
The implementation plan must enumerate these bridge members and their consumers
before moving classes; widening every old package-private method is unacceptable.

Architecture checks enforce:

1. App production and benchmark code use only the supported terminal surface.
2. Terminal production code never depends on the app.
3. Public terminal signatures, including generic arguments and inheritance,
   do not expose JediTerm types. Vendor connector implementations stay hidden
   behind Jasper-owned entry points.
4. Package dependencies are acyclic; internal implementations do not import the
   public session/view facades to call back into them.
5. JediTerm imports are confined to emulator/connector adapters, not view,
   rendering, text algorithms, or configuration values.

Callbacks and Jasper-owned internal values carry information toward facades.
Dependencies flow from view/session orchestration toward implementations and
shared values. Text algorithms never depend on painting. Shared value packages
never depend on session or view. Java package naming is not runtime isolation;
JPMS conversion is outside this refactor.

## 4. Session, process, and shell ownership

`TerminalSession` is a concrete lifecycle and operation facade. It exposes
startup, input, resize, metadata, listeners, exit completion, and close while
delegating implementation to cohesive concrete collaborators.

`PtySessionFactory`, beside the facade, composes process creation and the engine.
The process implementation owns PTY I/O and bounded termination. The factory
copies caller-owned arguments/environment, validates launch options before
starting a child, forces `TERM=xterm-256color` and `COLORTERM=truecolor`, and
cleans up an already-created process if subsequent initialization fails.
`ForegroundJobResolver` owns native foreground-job lookup and login-shell naming.

`JediTermEngine` owns emulator construction and execution, protocol input, and
buffer synchronization. It delegates row capture and queries to `BufferQueries`;
moving the old monolith intact into this class does not satisfy the design.
One reader thread remains associated with each session. The locked RIS override
and protocol-ordering safeguards are preserved.

`AbsoluteRowState` owns discard counts and reset generations. Absolute rows are
`discardedLines + historyLines + screenRow`. History clear, width reflow, and
alternate-buffer changes retain their existing invalidation behavior. Selection,
search, prompt marks, and viewport anchors respond coherently to invalidation.

`ShellCommandTracker` owns prompt/command state, pending command capture,
monotonic duration measurement, and command start/finish transitions. Stream
rewriting remains separate from interpretation. Preserve OSC 7/133 rewriting,
OSC 8 handling, q-final filtering, event ordering, and payload bounds. Capture
event metadata at the event, rather than asking listeners to reconstruct it
from mutable session state later.

## 5. Buffer, text, and rendering boundary

JediTerm remains the sole live terminal model. Do not create a second mutable
buffer with its own synchronization. Move cell extraction out of `RunBuilder`
into the emulator boundary; search, copy, selection, and painting consume
Jasper-owned row access and coordinate values.

Capture visible rows under the buffer lock; perform run building, font shaping,
and painting afterward. Search captures detached rows under the lock and runs
regex matching outside it. Preserve bounded traversal for wrapped links and
selections, whole-character handling, and the no-snapshot mouse-report path.

Copied rows may use an opaque adapter that privately retains a copied vendor
row. Construction and vendor access remain inside the adapter package. Its
public collaboration methods expose only Jasper/JDK values and no live mutable
state. Preserve approximately one vendor-row copy per captured visible row;
avoid additional full-screen copies or an allocated object per cell. Style
translation remains bounded and palette changes invalidate affected caches.
Allocation and timing comparisons are required when introducing this boundary.

## 6. View ownership and input

`TerminalView` remains the Swing component, lifecycle entry point, and assembly
point for these package-private collaborators:

| Collaborator | State and behavior it owns |
| --- | --- |
| KeyboardController | Modifier tracking, pressed/typed suppression, shortcut precedence and key routing |
| MouseController | Per-button gesture ownership, report modifiers, wheel accumulation and routing |
| SelectionController | Anchors, selected ranges, whole-character extraction and overwrite validation |
| SearchController | Query generations, matches, current result, bounded worker and EDT publication |
| RenderScheduler | Dirty coalescing, attachment generations, visibility, frame and cursor-blink scheduling |
| BellController | Coalesced delivery, visual expiry and sound invocation |

Viewport state has one owner. Row invalidation is coordinated across selection,
search, prompts, and scrolling rather than implemented as competing independent
listeners. Controllers receive only the collaborators and callbacks they use;
there is no all-fields context object or circular reference to the entire view.

Keep package-private headless input entry points. App shortcuts retain precedence
over terminal encoding, and mouse reporting retains Shift bypass, ownership
through release, right-click behavior, and bounds checks. Detaching a component
stops/cancels its presentation work but does not close its session. Reattachment
cannot revive stale callbacks. Hiding the component retains existing timer and
rendering suppression behavior.

## 7. Fluent API and patterns

Use composition, immutable values, and concrete collaborators. Preserve the
inheritance required by Swing and vendor adaptation; introduce no general
terminal/controller base-class hierarchy.

`TerminalOptions` and `SessionLaunchOptions` have fluent builders and immutable
results. `TerminalOptions.defaults().toBuilder()` supports changing a small
number of settings without repeating a long constructor. `build()` validates
using one canonical validation path; setters and builders perform no I/O.
Collections are defensively copied. Existing ranges/defaults and the distinction
between live options and new-session settings remain unchanged.

`SessionLaunchOptions` contains command, environment, working directory, initial
grid, and scrollback. It requires explicit command/environment/directory values;
it does not resolve a login shell, read app configuration, or silently inherit
an environment. App launch resolution remains in the app. Startup is explicit,
may fail with an I/O error, and is performed away from the EDT.

Proposed public usage, to be made compilable by the implementation:

```java
TerminalOptions options = TerminalOptions.defaults().toBuilder()
    .fontSize(16f)
    .copyOnSelect(true)
    .bell(BellMode.VISUAL)
    .build();
```

`TerminalAction` is a closed enum for existing parameterless view operations:
copy selection, paste clipboard, clear scrollback, find next/previous, and
previous/next prompt. `TerminalView.execute(TerminalAction)` requires the caller
to be on the EDT and synchronously invokes the same operations used by direct
calls; it does not silently enqueue work. The app adapts its existing
action system to these commands; this does not add another shortcut registry,
keymap, menu model, undo facility, or plugin command registry. Parameterized
operations such as paste text, find, and resize retain typed methods. Ordinary
keystrokes and mouse reports do not allocate command objects.

`SearchQuery` groups query text, regex selection, and case sensitivity.
Invalid regex still produces the existing user-visible search error result.

The patterns are Facade (session/view), Factory (startup), Builder (configuration),
Command (reusable user operations), Adapter (vendor/native integration), Decorator
(filtered connector), and Observer (existing listeners). Shell commands and mouse
gestures use explicit state transitions where this clarifies lifecycle invariants.

## 8. Threading, lifecycle, and failure contracts

| State/work | Ownership and contract |
| --- | --- |
| Emulator loop and shell command cycle | Reader thread; retain ordered protocol processing |
| Live buffer and row capture | Buffer lock on every read; small bounded lock regions |
| Swing, selection, viewport, highlights and timers | EDT |
| Regex work | Search worker over detached rows; latest generation publishes on EDT |
| Cross-thread invalidation/dirty/exit state | Explicit atomic, volatile, or lock protection documented per field |
| Desktop browser dispatch | Shared lazy bounded worker; no blocking browser launch on EDT |
| Process creation and shutdown waiting | Away from EDT where blocking is required; retain bounded cleanup |

Do not change listener delivery wholesale during extraction. Document callbacks
as synchronous on the thread causing the event unless a specific event promises
otherwise: title/cwd/command events originate in protocol processing, while
screen/reset/alternate-buffer notifications can originate in caller-thread
operations. Some callbacks occur under the reentrant buffer lock; listeners must
not block or synchronously wait for the EDT. Command-start delivery retains its
existing outside-buffer-lock guarantee. UI listeners marshal to the EDT and
reject stale attachments. Exit-future continuations must not assume EDT delivery.

Preserve one running and at most one queued search request per view, cancellation
and idle worker expiry, bounded caches, shared bounded browser work, listener
removal, and timer cleanup. Close remains safe to repeat and releases process
resources; child exit and view detachment remain distinct events. Startup errors
must not leak a child, connector, or reader thread.

Preserve existing failure policy: invalid regex is a result, unavailable native
metadata is empty, expected late input is tolerated, unexpected live input
failures are logged, and resize failure remains observable. Browser diagnostics
must not expose terminal URLs. Any newly discovered behavioral defect gets an
isolated regression and fix, rather than being hidden in a package move.

## 9. Future plugin SDK

This refactor establishes named terminal operations, documented events, immutable
configuration/results, and deliberate ownership. A later SDK can wrap selected
operations and observations behind host-controlled capabilities. Core and external
plugins should consume that same supported SDK so missing capabilities become
visible through actual use.

Do not expose the live buffer, engine, view controllers, or unrestricted process
internals as plugin extension points. This refactor does not implement plugin
discovery, loading/unloading, permissions, compatibility policy, service lookup,
registration, packaging, or dynamic command contributions. Those require their
own design and can evolve independently of terminal implementation packages.

## 10. Documentation and onboarding deliverables

Documentation ships with each extraction, not as a final comments-only task.

| Artifact | Required contents |
| --- | --- |
| `jasper-terminal/README.md` | Purpose, supported surface, setup with JBR 25, build/test commands, source map, embedding and teardown example, recommended reading order |
| Every `package-info.java` | Responsibility, exclusions, dependencies, supported/internal status, threading and links to entry points |
| `docs/terminal-architecture.md` | Ownership map, output/input/search/event flows, coordinate examples, lock and lifecycle tables, failure policy |
| `docs/terminal-maintenance.md` | Step-by-step feature recipes, owners, integration points, tests, invariants and common mistakes |
| Type/member JavaDoc | API and internal contracts that make collaborators understandable without reading implementation |
| Compiled examples | Builder, launch, EDT attachment, events, commands, search, detach and close examples checked by the build |

The onboarding path is: install/select JBR, run terminal tests, read the package
map, trace output and input once, inspect a focused behavior test, then follow a
maintenance recipe. Distinguish headless tests from user-run GUI checks clearly.

Required recipes: add a terminal action; change key encoding; add a live option;
handle a shell event; change painting/font behavior; extend search; change mouse
routing; inspect process metadata. Each names concrete classes and tests in the
final layout. Explain which settings require a new session and where app parsing
must be updated without refactoring the app itself.

Document every production type and supported public/protected member, including
record components, units, coordinate conventions, inclusive/exclusive endpoints,
null/empty meaning, side effects, ownership, thread requirements, and failures.
Internal comments explain algorithms, synchronization, protocol quirks, and
non-obvious decisions. Avoid comments that merely narrate Java syntax. Remove
obsolete milestone comments and link remaining vendor workarounds to regressions.

Update root README navigation and STATUS. Keep historical plans/reports as
historical evidence; the onboarding path must not require reading them all.

## 11. Migration and verification

One implementation plan covers this runnable deliverable, split into reviewable
tasks. The established implementer/reviewer workflow and final whole-branch
review apply when execution is authorized. Use a dedicated branch/worktree as
appropriate, one commit per task with a Co-Authored-By trailer, and record plan
deviations in its banner and STATUS. Do not merge or push without authorization.

| Checkpoint | Work and required evidence |
| --- | --- |
| Baseline | Inventory supported callers, run both modules, classify existing failures, record resource/performance fixtures |
| Session extraction | Factory, process metadata, engine/query and shell ownership; lifecycle and protocol regressions |
| Buffer boundary | Jasper-owned row access, cell extraction and vendor conversions; text/render correctness and allocation comparison |
| View extraction | Search, gestures, selection, desktop dispatch and scheduling; cancellation, stale callbacks and cleanup regressions |
| API/package migration | Builders, commands, queries, class/test moves, all app and benchmark callers; architecture checks |
| Acceptance | Documentation walkthrough, complete headless checks, measured comparison and explicit native acceptance checklist |

Every checkpoint builds a runnable app. Separate mechanical moves from algorithm
changes. Temporary forwarding code may ease staged migration but is removed
before acceptance. Move focused tests beside their owners; share test fixtures
without exposing production internals solely for tests. Replace reflective
assertions with owner-level tests while retaining meaningful component/integration
regressions. Do not reduce coverage to assertions that merely mirror the new code.

Run focused suites for each extraction and `./gradlew check` at checkpoints.
Acceptance includes successful JavaDoc/doclint, documentation link checks,
compiled examples, source-hygiene and diff checks, and architecture tests.
Three-platform CI follows when publication is authorized. Tests remain headless;
no GUI or native benchmark is launched by the unattended agent.

Retain regressions for OSC 7/8/133 and cursor filtering; RIS/ED3/reflow/alternate
buffer invalidation; Unicode/ligatures; selection/copy; mouse ownership and
no-snapshot reports; latest-search publication; view detach/hide/reattach; live
options; process startup/close; and app title/command-event integration.

For changed buffer/render boundaries, compare before/after allocation and timing
using identical input, runtime, fonts, grid, warm-up, and repeated measurements.
Document variability and investigate repeatable regressions. Preserve queue/cache
bounds and retained-resource cleanup. Historical benchmark results are not a
current baseline. Native throughput retains the 35 MB/s floor and 45 MB/s target;
native throughput/RSS/desktop acceptance remains explicitly user-run and pending
until measured.

Human acceptance requires a reviewer to follow the docs to locate the owner and
test for a key change, shell event, and live option, and explain shutdown and
callback threads without reconstructing either original monolith. Class count,
comment count, or an arbitrary line limit is not a substitute for this check.

## 12. Evidence and next gate

Before writing this spec, `./gradlew :jasper-terminal:test --rerun-tasks` executed
all three tasks successfully. XML records 322 tests: 321 passed, one skipped,
zero failures/errors. This establishes terminal-only baseline evidence, not a
fresh full-app, native GUI, or performance result. The implementation baseline
must rerun the full suite and classify any existing failures on its chosen base.

The design approval was given in conversation on 2026-09-20 after the package,
ownership, patterns, documentation, and migration proposal. The user approved
this written specification on 2026-09-20. Use `superpowers:writing-plans`
to define exact signatures, bridge members, files, task code, and checks; the
user then reviews that plan and selects execution. No implementation is included
in this documentation change.
