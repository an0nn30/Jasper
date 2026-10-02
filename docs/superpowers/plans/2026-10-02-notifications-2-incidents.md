# Notifications Plan 2: Incidents Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Status:** Not started. Branch `claude/notifications-2` (worktree `.worktrees/notifications-2`, cut from main
`63e3b95c`, where plan 1 is merged).

**Goal:** Record every unexpected failure (uncaught exceptions on any thread, failed plugin callbacks, failed shell
launches, failing notification actions) as an in-memory incident. Each incident gets one ERROR notification and a
place in a new Errors window that shows its stack trace, as IntelliJ does.

**Architecture:** A Swing-free `Capture` / `Incident` / `Incidents` model in `dev.jasper.app.notifications` takes the
trace once, on the thread that caught the failure, and coalesces repeats. `dev.jasper.app.application.IncidentNotices`
is the only producer: its any-thread entry points capture, then record on the EDT and post or repeat one notification
per incident through two new center operations (`repeat`, `reportActionFailuresTo`). Three hooks feed it: the bootstrap
uncaught handler, plugin `Containment` and the window's launch failure. `notificationui.ErrorsView` inside an
application-owned `ErrorsWindow` (one auxiliary window) shows the incidents.

**Tech Stack:** Java 25 (JetBrains Runtime 25), Swing with FlatLaf, JUnit 5, AssertJ, Gradle wrapper.

**Spec:** [`docs/superpowers/specs/2026-10-02-jasper-notifications-design.md`](../specs/2026-10-02-jasper-notifications-design.md)
(plan 2 of the six in its section 14; sections 5, 7 and 11 bind this plan). Read it, and the status banner of
[plan 1](2026-10-02-notifications-1-core.md) (its deviations and deferred findings), before starting.

## Global Constraints

- Build and test only with `./gradlew` on JBR 25; tests are headless. Never launch the GUI or the benchmark.
- Work only in `.worktrees/notifications-2` on branch `claude/notifications-2`. Never edit the main checkout (other
  sessions work there), never push, never use a bare `git stash`.
- **No animations anywhere.** The Errors window, its list and its detail appear and change instantly.
- Text is **plain, never HTML**. Every `JLabel` or `JButton` that shows incident or notification text gets the client
  property `"html.disable"` = `Boolean.TRUE` *before* its text is set (construct empty, set the property, then
  `setText`). Text areas never render markup.
- Limits (spec section 5): trace at most **256 KiB** (`262,144` characters), coalescing key = source + exception class
  + the root cause's top **5** frames, at most **100** incidents (oldest dropped), a repeating incident's card at
  most **once a minute**, Errors window **900×560** by default. This plan also caps a captured message at 16 KiB.
- Incidents live in memory only. **The log is unchanged** (spec section 11): new log calls pass the throwable to the
  existing `System.Logger` with a fixed message, which `AppLog` scrubs; never concatenate exception messages, notification
  text or traces into a log message.
- **Capture never throws.** A failure inside the notification system itself is logged only, never notified, and
  capture is guarded against re-entry on the same thread.
- Threading: `Incidents` and `NotificationCenter` are EDT-confined. `IncidentNotices.jasperFailure`, `pluginFailure`
  and `launchFailure`, the `PluginRuntime.reportFailuresTo` sink and the bootstrap's forward are safe from any thread.
- Package graph (checked by `verifyApplicationArchitecture`): `notificationui → notifications`, never the reverse;
  `workspace` imports neither (`WindowContent.onLaunchFailure` uses JDK types only); SDK types stay inside
  `dev.jasper.app.plugins` (`PluginRuntime.Failure` is app-native). This plan adds no package edges; update the
  touched package-info descriptions.
- Doclint runs on public types (`./gradlew check` includes `javadoc` with `-Xdoclint:all`): no bare `<`, `>` or `&` in
  JavaDoc outside `{@code}`, and `@param` names must match.
- Source hygiene (AGENTS.md): no raw control, private-use or unpaired surrogate characters in Java source. `"…"`,
  `"×"` and `"·"` may be written as characters, as plan 1 did.
- Tests: injected clocks and executors, never sleep. EDT tests use `@ExtendWith(EdtTestExtension.class)`; `@BeforeEach`
  and `@AfterEach` and field initializers do **not** run on the EDT. Owner-package test bridges live in test sources.
- One commit per task; every commit message ends with:

  ```
  🤖 Generated with Claude Code at The Home Depot

  Co-Authored-By: Claude <noreply@anthropic.com>
  ```

## Spec interpretations (recorded deviations)

Recorded here and, at completion, in `docs/STATUS.md`:

1. **No `Notification.incident` field.** `IncidentNotices` links each incident to its notification (incident id to
   notification id). Plan 5 needs a marker for OS-notification privacy and click routing (spec sections 7 and 11), so
   it adds the field when it needs it.
2. **An incident's notification expires when its incident is cleared or dropped** from the 100. Its links are then
   disabled (spec section 4, "disabled actions after expiry or cleared incident"). Show more… and Disable Plugin use
   `expires = false`, so both keep working after use.
3. **Every incident row uses the error icon.** Plugin icons come with the SDK work (plan 3).
4. **The notification system** is any class in `dev.jasper.app.notifications` or `dev.jasper.app.notificationui`, plus
   `IncidentNotices`, `NotificationsFeature`, `ErrorsWindow` and `ConfigProblemNotices` (and their nested classes).
   A failure whose first `dev.jasper.` frame, in any throwable of its cause chain, lies there is only logged.
5. **A shell launch failure** is an incident (operation "starting a shell") announced in `jasper.general`, as spec
   section 7 producer 1 says. The announcement has the title "Could not start the terminal", the directory and cause as
   content, the window as target, and Show more….
6. **A notification action that throws** is a Jasper incident with operation "a notification action". Plugin actions
   arrive with the SDK (plan 3), which contains and attributes them.
7. **Plugin titles:** "<Name> failed to start", "<Name> failed to stop", otherwise "<Name> failed in <operation>", with
   the operation strings `Containment` already logs. Jasper posts these (`source = jasper`), so plan 3's "a plugin's
   notifications expire when it stops" leaves them alone.
8. **Disable Plugin's outcome** goes to `jasper.plugins`: INFO "<Name> will be disabled when Jasper restarts", or ERROR
   "Could not disable <Name>" with the reason.
9. **Jasper's version in a report** is the `jpackage.app-version` property, or "development build".
10. **A repeat whose notification was removed or has expired posts a new one,** whose card is routed like any new post.
    The once-a-minute rule applies to repeats of a live notification.
11. **Captured messages are capped at 16 KiB** (the spec caps only the trace), to bound memory at 100 incidents.
12. **Incidents are recorded whatever the group's mode** (spec section 7: `OFF` still records an incident).

**Hand-offs to later plans:**

- Plan 3: a plugin that fails to start already gets a plugin-problem notification from its `Containment` incident. The
  startup-problem notifications must not post a second one for the same failure.
- Plan 5: incident notifications carry only their title to the OS (spec section 11), which needs interpretation 1's
  marker.

## Review Focus

1. **The notification system failing** (a card's paint, a center listener, `IncidentNotices` itself) must be logged
   once and never turn into a notification loop. Pinned by Task 4's own-failure and re-entry tests.
2. **A burst of the same exception** (a plugin's background task failing every few milliseconds, off the EDT) must give
   one incident ×N and at most one card a minute. Pinned by Task 4's burst and repeat tests.
3. **The trace is taken where the failure was caught:** a throwable that is changed, reused or rethrown afterwards must
   not change its incident. Pinned by Task 1's and Task 4's capture-time tests.
4. **Hostile throwables:** one whose `getMessage`, `toString` or `getCause` throws, a cause cycle, or a 400,000-character
   message must not make capture throw, hang or keep more than the caps. Pinned by Task 1's tests and Task 4's
   unreadable-failure test.
5. **A cleared or dropped incident expires its notification,** so Show more… can never open the window at an incident
   that is gone. A stale selection is ignored. Pinned by Task 4's clear/drop tests and Task 3's select test.

## File structure

Create:

- `jasper-app/src/main/java/dev/jasper/app/notifications/Capture.java`: what an incident keeps of a throwable (class,
  capped message, capped `printStackTrace` text, signature), taken without throwing.
- `jasper-app/src/main/java/dev/jasper/app/notifications/Incident.java`: one incident value.
- `jasper-app/src/main/java/dev/jasper/app/notifications/Incidents.java`: the EDT store (coalescing, 100 cap, changes).
- `jasper-app/src/main/java/dev/jasper/app/notifications/IncidentReport.java`: Copy's clipboard text and the
  environment it names.
- `jasper-app/src/main/java/dev/jasper/app/notificationui/ErrorsView.java`: the Errors window's content.
- `jasper-app/src/main/java/dev/jasper/app/application/ErrorsWindow.java`: the singleton window and Notifications: Show
  Errors.
- `jasper-app/src/main/java/dev/jasper/app/application/IncidentNotices.java`: capture, record, announce.
- Tests: `notifications/CaptureTest`, `notifications/IncidentsTest`, `notifications/IncidentReportTest`,
  `notificationui/ErrorsViewTest`, `notificationui/ErrorsViewTestSupport`, `application/ErrorsWindowTest`,
  `application/IncidentNoticesTest`, `application/JasperApplicationIncidentsTest`, `plugins/ContainmentTest`,
  `bootstrap/UnexpectedExceptionsTest` (all under `jasper-app/src/test/java/dev/jasper/app/`).

Modify:

- `notifications/NotificationCenter.java`: `repeat`, `reportActionFailuresTo`, shared `announce`.
- `plugins/Containment.java`, `plugins/PluginHost.java`, `plugins/PluginRuntime.java`: report contained failures.
- `bootstrap/ApplicationBootstrap.java`: the uncaught handler forwards; `startDesktop` wires it.
- `workspace/TerminalPane.java`, `workspace/TerminalTab.java`, `workspace/WindowContent.java`: the launch failure
  carries its throwable to `WindowContent.onLaunchFailure`.
- `application/JasperApplication.java`: composition.
- Tests: `notifications/NotificationCenterTest`, `plugins/PluginRuntimeTest`, `workspace/TerminalTabTest`.
- Docs: `docs/notifications.md`, `docs/app-maintenance.md`, `docs/app-architecture.md`, `docs/STATUS.md`, the spec's
  status banner, this plan's status banner, and the `notifications` / `notificationui` package-info files.

---

### Task 1: Captures and the incident store

**Files:**
- Create: `jasper-app/src/main/java/dev/jasper/app/notifications/Capture.java`
- Create: `jasper-app/src/main/java/dev/jasper/app/notifications/Incident.java`
- Create: `jasper-app/src/main/java/dev/jasper/app/notifications/Incidents.java`
- Test: `jasper-app/src/test/java/dev/jasper/app/notifications/CaptureTest.java`
- Test: `jasper-app/src/test/java/dev/jasper/app/notifications/IncidentsTest.java`

**Interfaces:**
- Consumes: `NotificationText.cut(String, int)`, `NotificationText.line(String, int)`, `NotificationText.ELLIPSIS`
  (package-private, same package); `NotificationRequest.JASPER`; `dev.jasper.app.lifecycle.Subscription`.
- Produces:
  - `public record Capture(String exceptionClass, String message, String trace, String signature)` with
    `TRACE_LIMIT = 256 * 1024`, `MESSAGE_LIMIT = 16 * 1024`, `KEY_FRAMES = 5` and `public static Capture of(Throwable)`.
  - `public record Incident(UUID id, String source, String operation, String thread, String exceptionClass,
    String message, String trace, int count, Instant firstSeen, Instant lastSeen)` with `simpleName()`, `firstLine()`,
    `summary()`, `fromPlugin()`; package-private `SUMMARY_LIMIT = 200` and `repeated(Instant)`.
  - `public final class Incidents` with `LIMIT = 100`, `Incidents(Clock)`,
    `Incident record(String source, String operation, String thread, Capture capture)`, `Optional<Incident> find(UUID)`,
    `List<Incident> list()` (newest first), `void clear()`, `Subscription onChange(Consumer<Incidents.Change>)` and
    `sealed interface Change` with records `Added(Incident)`, `Repeated(Incident)`, `Removed(UUID id)`, `Cleared()`.

- [ ] **Step 1: Write the failing capture tests**

Create `jasper-app/src/test/java/dev/jasper/app/notifications/CaptureTest.java`:

```java
package dev.jasper.app.notifications;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

class CaptureTest {
    private static String printed(Throwable failure) {
        var out = new StringWriter();
        failure.printStackTrace(new PrintWriter(out));
        return out.toString().replace("\r", "").stripTrailing();
    }

    private static StackTraceElement frame(String method, int line) {
        return new StackTraceElement("dev.example.Tool", method, "Tool.java", line);
    }

    private static IllegalStateException at(StackTraceElement... frames) {
        var failure = new IllegalStateException("broken");
        failure.setStackTrace(frames);
        return failure;
    }

    /** A failure whose message, and so its toString and trace, cannot be read. */
    private static final class Unreadable extends RuntimeException {
        @Override public String getMessage() { throw new IllegalStateException("no message for you"); }
    }

    /** A failure whose cause cannot be read. */
    private static final class Causeless extends RuntimeException {
        Causeless() { super("no cause"); }
        @Override public synchronized Throwable getCause() { throw new IllegalStateException("cause unreadable"); }
    }

    @Test void theTraceIsPrintStackTraceWithCausesAndSuppressed() {
        var root = new IllegalArgumentException("root cause");
        var failure = new IllegalStateException("outer\nsecond line", root);
        failure.addSuppressed(new UnsupportedOperationException("cleanup failed"));
        Capture capture = Capture.of(failure);
        assertThat(capture.exceptionClass()).isEqualTo("java.lang.IllegalStateException");
        assertThat(capture.message()).isEqualTo("outer\nsecond line");
        assertThat(capture.trace()).isEqualTo(printed(failure))
            .contains("Caused by: java.lang.IllegalArgumentException: root cause",
                "Suppressed: java.lang.UnsupportedOperationException: cleanup failed");
    }

    @Test void theTraceIsTakenOnceAndLaterChangesDoNotReachIt() {
        var failure = new IllegalStateException("as caught");
        Capture capture = Capture.of(failure);
        String taken = capture.trace();
        failure.addSuppressed(new IllegalStateException("added later"));
        failure.setStackTrace(new StackTraceElement[0]);
        assertThat(capture.trace()).isEqualTo(taken).doesNotContain("added later")
            .contains("at dev.jasper.app.notifications.CaptureTest");
    }

    @Test void aHugeMessageIsCutAndTheTraceStaysWithinItsLimit() {
        Capture capture = Capture.of(new IllegalStateException("x".repeat(400_000)));
        assertThat(capture.message()).hasSize(Capture.MESSAGE_LIMIT).endsWith(NotificationText.ELLIPSIS);
        assertThat(capture.trace()).hasSize(Capture.TRACE_LIMIT).endsWith(NotificationText.ELLIPSIS)
            .startsWith("java.lang.IllegalStateException: xxx");
    }

    @Test void aThrowableThatCannotDescribeItselfIsCapturedByItsClass() {
        Capture capture = Capture.of(new Unreadable());
        assertThat(capture.exceptionClass()).isEqualTo(Unreadable.class.getName());
        assertThat(capture.message()).isEmpty();
        assertThat(capture.trace()).isEqualTo(Unreadable.class.getName());
    }

    @Test void aCauseThatCannotBeReadLeavesAnEmptySignature() {
        Capture capture = Capture.of(new Causeless());
        assertThat(capture.message()).isEqualTo("no cause");
        assertThat(capture.signature()).isEmpty();
        assertThat(capture.trace()).isEqualTo(Causeless.class.getName());
    }

    @Test void aCauseCycleEnds() {
        var first = new IllegalStateException("first");
        var second = new IllegalArgumentException("second", first);
        first.initCause(second);
        Capture capture = assertTimeoutPreemptively(Duration.ofSeconds(5), () -> Capture.of(first));
        assertThat(capture.trace()).contains("CIRCULAR REFERENCE");
        assertThat(capture.signature()).isNotEmpty();
    }

    @Test void theSignatureIsTheRootCausesTopFiveFrames() {
        var a = at(frame("one", 1), frame("two", 2), frame("three", 3), frame("four", 4), frame("five", 5), frame("six", 6));
        var b = at(frame("one", 1), frame("two", 2), frame("three", 3), frame("four", 4), frame("five", 5), frame("other", 60));
        var c = at(frame("one", 1), frame("two", 2), frame("three", 3), frame("four", 4), frame("changed", 50), frame("six", 6));
        assertThat(Capture.of(a).signature()).isEqualTo(Capture.of(b).signature()).isEqualTo(
            "dev.example.Tool.one:1\ndev.example.Tool.two:2\ndev.example.Tool.three:3\ndev.example.Tool.four:4\ndev.example.Tool.five:5");
        assertThat(Capture.of(c).signature()).isNotEqualTo(Capture.of(a).signature());
        var wrapped = new RuntimeException("wrapper", at(frame("one", 1), frame("two", 2), frame("three", 3), frame("four", 4), frame("five", 5)));
        assertThat(Capture.of(wrapped).signature()).as("a wrapper is keyed by what it wraps").isEqualTo(Capture.of(a).signature());
    }
}
```

- [ ] **Step 2: Write the failing store tests**

Create `jasper-app/src/test/java/dev/jasper/app/notifications/IncidentsTest.java`:

```java
package dev.jasper.app.notifications;

import dev.jasper.app.testsupport.EdtTestExtension;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.assertj.core.api.Assertions.*;

@ExtendWith(EdtTestExtension.class)
class IncidentsTest {
    /** A clock a test moves by hand. */
    private static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-10-02T14:05:00Z");
        void advance(Duration by) { now = now.plus(by); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }

    private final MutableClock clock = new MutableClock();
    private final List<Incidents.Change> changes = new ArrayList<>();

    private Incidents incidents() {
        var incidents = new Incidents(clock);
        incidents.onChange(changes::add);
        return incidents;
    }

    private static Capture capture(String type, String message, String signature) {
        return new Capture(type, message, type + ": " + message + "\n\tat somewhere", signature);
    }

    private List<String> kinds() { return changes.stream().map(change -> change.getClass().getSimpleName()).toList(); }

    @Test void aNewFailureIsAnIncidentAndTheNewestComesFirst() {
        var incidents = incidents();
        Incident first = incidents.record("jasper", "thread main", "main", capture("java.lang.IllegalStateException", "first", "a"));
        clock.advance(Duration.ofSeconds(1));
        Incident second = incidents.record("dev.example.tool", "action tool.run", "AWT-EventQueue-0",
            capture("java.io.UncheckedIOException", "second", "b"));
        assertThat(incidents.list()).extracting(Incident::id).containsExactly(second.id(), first.id());
        assertThat(first.source()).isEqualTo("jasper");
        assertThat(first.operation()).isEqualTo("thread main");
        assertThat(first.thread()).isEqualTo("main");
        assertThat(first.count()).isEqualTo(1);
        assertThat(first.firstSeen()).isEqualTo(first.lastSeen()).isEqualTo(Instant.parse("2026-10-02T14:05:00Z"));
        assertThat(first.fromPlugin()).isFalse();
        assertThat(second.fromPlugin()).isTrue();
        assertThat(kinds()).containsExactly("Added", "Added");
        assertThat(incidents.find(first.id())).contains(first);
    }

    @Test void aRepeatCountsKeepsTheFirstTraceAndComesFirstAgain() {
        var incidents = incidents();
        Incident first = incidents.record("jasper", "thread main", "main", capture("java.lang.IllegalStateException", "first message", "same"));
        incidents.record("jasper", "thread other", "other", capture("java.lang.IllegalArgumentException", "unrelated", "else"));
        clock.advance(Duration.ofMinutes(3));
        Incident again = incidents.record("jasper", "thread worker", "worker",
            capture("java.lang.IllegalStateException", "a different message", "same"));
        assertThat(again.id()).isEqualTo(first.id());
        assertThat(again.count()).isEqualTo(2);
        assertThat(again.firstSeen()).isEqualTo(first.firstSeen());
        assertThat(again.lastSeen()).isEqualTo(first.firstSeen().plus(Duration.ofMinutes(3)));
        assertThat(again.message()).isEqualTo("first message");
        assertThat(again.trace()).isEqualTo(first.trace());
        assertThat(again.operation()).isEqualTo("thread main");
        assertThat(incidents.list().getFirst()).isEqualTo(again);
        assertThat(changes.getLast()).isEqualTo(new Incidents.Change.Repeated(again));
    }

    @Test void theSameFailureFromAnotherSourceOrOfAnotherClassIsAnotherIncident() {
        var incidents = incidents();
        incidents.record("jasper", "a", "t", capture("java.lang.IllegalStateException", "m", "same"));
        incidents.record("dev.example.tool", "a", "t", capture("java.lang.IllegalStateException", "m", "same"));
        incidents.record("jasper", "a", "t", capture("java.lang.IllegalArgumentException", "m", "same"));
        assertThat(incidents.list()).hasSize(3).allSatisfy(incident -> assertThat(incident.count()).isEqualTo(1));
    }

    @Test void onlyTheNewest100AreKept() {
        var incidents = incidents();
        Incident oldest = incidents.record("jasper", "a", "t", capture("x.Boom0", "m", "s"));
        for (int i = 1; i <= Incidents.LIMIT; i++) incidents.record("jasper", "a", "t", capture("x.Boom" + i, "m", "s"));
        assertThat(incidents.list()).hasSize(Incidents.LIMIT).extracting(Incident::exceptionClass).doesNotContain("x.Boom0");
        assertThat(incidents.find(oldest.id())).isEmpty();
        assertThat(changes.getLast()).isEqualTo(new Incidents.Change.Removed(oldest.id()));
        assertThat(kinds().subList(kinds().size() - 2, kinds().size())).as("the dropped one goes after the new one is added")
            .containsExactly("Added", "Removed");
    }

    @Test void clearEmptiesTheListOnce() {
        var incidents = incidents();
        incidents.record("jasper", "a", "t", capture("x.Boom", "m", "s"));
        incidents.clear();
        incidents.clear();
        assertThat(incidents.list()).isEmpty();
        assertThat(kinds()).containsExactly("Added", "Cleared");
    }

    @Test void aFailingListenerStopsNeitherTheOthersNorTheRecord() {
        var incidents = new Incidents(clock);
        incidents.onChange(change -> { throw new IllegalStateException("listener broke"); });
        incidents.onChange(changes::add);
        assertThatCode(() -> incidents.record("jasper", "a", "t", capture("x.Boom", "m", "s"))).doesNotThrowAnyException();
        assertThat(kinds()).containsExactly("Added");
    }

    @Test void summariesAreOneShortLine() {
        var incidents = incidents();
        Incident incident = incidents.record("jasper", "a", "t", new Capture("java.lang.IllegalStateException", "first line\nsecond line", "trace", "s1"));
        assertThat(incident.simpleName()).isEqualTo("IllegalStateException");
        assertThat(incident.firstLine()).isEqualTo("first line");
        assertThat(incident.summary()).isEqualTo("IllegalStateException: first line");
        Incident bare = incidents.record("jasper", "a", "t", new Capture("Boom", "", "trace", "s2"));
        assertThat(bare.summary()).isEqualTo("Boom");
        Incident wide = incidents.record("jasper", "a", "t", new Capture("x.Wide", "w".repeat(500), "trace", "s3"));
        assertThat(wide.summary()).hasSize(Incident.SUMMARY_LIMIT).endsWith(NotificationText.ELLIPSIS);
    }

    @Test void incidentsBelongToTheEdt() throws Exception {
        var incidents = incidents();
        var thrown = new AtomicReference<Throwable>();
        var worker = new Thread(() -> { try { incidents.list(); } catch (Throwable failure) { thrown.set(failure); } });
        worker.start();
        worker.join();
        assertThat(thrown.get()).isInstanceOf(IllegalStateException.class);
    }
}
```

- [ ] **Step 3: Run the tests to see them fail**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.notifications.CaptureTest' --tests 'dev.jasper.app.notifications.IncidentsTest'`
Expected: compilation fails: `Capture`, `Incident` and `Incidents` do not exist.

- [ ] **Step 4: Write `Capture`**

Create `jasper-app/src/main/java/dev/jasper/app/notifications/Capture.java`:

```java
package dev.jasper.app.notifications;

import java.io.PrintWriter;
import java.io.Writer;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Objects;
import java.util.Set;
import java.util.StringJoiner;

/**
 * What an incident keeps of a throwable, taken once on the thread that caught it (spec section 5): its class, its
 * message (at most {@value #MESSAGE_LIMIT} characters), its trace in {@code printStackTrace} form with line feeds (at
 * most {@value #TRACE_LIMIT}), and its signature, the root cause's top {@value #KEY_FRAMES} frames, by which repeats
 * coalesce.
 */
public record Capture(String exceptionClass, String message, String trace, String signature) {
    public static final int TRACE_LIMIT = 256 * 1024;
    public static final int MESSAGE_LIMIT = 16 * 1024;
    public static final int KEY_FRAMES = 5;

    public Capture {
        Objects.requireNonNull(exceptionClass, "exceptionClass");
        Objects.requireNonNull(message, "message");
        Objects.requireNonNull(trace, "trace");
        Objects.requireNonNull(signature, "signature");
    }

    /**
     * Takes what an incident keeps of {@code failure}. Never throws: a message that cannot be read is empty, a trace that
     * cannot be rendered is the class name, and a cause chain that cannot be walked leaves the signature empty.
     *
     * @param failure what was caught
     * @return the capture
     */
    public static Capture of(Throwable failure) {
        Objects.requireNonNull(failure, "failure");
        String type = failure.getClass().getName();
        String message;
        try { message = NotificationText.cut(Objects.requireNonNullElse(failure.getMessage(), ""), MESSAGE_LIMIT); }
        catch (RuntimeException | Error unreadable) { message = ""; }
        String trace;
        try { trace = render(failure); }
        catch (RuntimeException | Error unrenderable) { trace = type; }
        String signature;
        try { signature = signature(failure); }
        catch (RuntimeException | Error unreadable) { signature = ""; }
        return new Capture(type, message, trace, signature);
    }

    private static String render(Throwable failure) {
        var kept = new Bounded(TRACE_LIMIT + 1);
        try (var writer = new PrintWriter(kept)) { failure.printStackTrace(writer); }
        return NotificationText.cut(kept.text().stripTrailing(), TRACE_LIMIT);
    }

    /** The root cause's top frames as {@code class.method:line} lines: the deepest cause, stopping at a cycle. */
    private static String signature(Throwable failure) {
        Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        Throwable root = failure;
        for (Throwable next = failure; next != null && seen.add(next); next = next.getCause()) root = next;
        StackTraceElement[] frames = root.getStackTrace();
        var lines = new StringJoiner("\n");
        for (int i = 0; i < Math.min(KEY_FRAMES, frames.length); i++)
            lines.add(frames[i].getClassName() + "." + frames[i].getMethodName() + ":" + frames[i].getLineNumber());
        return lines.toString();
    }

    /**
     * Keeps the first {@code limit} characters written, without carriage returns, and drops the rest unread, so a huge
     * message never becomes a huge trace.
     */
    private static final class Bounded extends Writer {
        private final StringBuilder kept = new StringBuilder();
        private final int limit;

        Bounded(int limit) { this.limit = limit; }

        @Override public void write(char[] buffer, int offset, int length) {
            for (int i = offset; i < offset + length && kept.length() < limit; i++)
                if (buffer[i] != '\r') kept.append(buffer[i]);
        }

        @Override public void write(String text, int offset, int length) {
            for (int i = offset; i < offset + length && kept.length() < limit; i++) {
                char c = text.charAt(i);
                if (c != '\r') kept.append(c);
            }
        }

        @Override public void write(int c) {
            if (c != '\r' && kept.length() < limit) kept.append((char) c);
        }

        @Override public void flush() { }

        @Override public void close() { }

        String text() { return kept.toString(); }
    }
}
```

- [ ] **Step 5: Write `Incident`**

Create `jasper-app/src/main/java/dev/jasper/app/notifications/Incident.java`:

```java
package dev.jasper.app.notifications;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * One unexpected failure and its repeats (spec section 5): who failed ({@code jasper} or a plugin id), what it was
 * doing, on which thread, the first sighting's class, message and trace, how often it happened and when.
 */
public record Incident(UUID id, String source, String operation, String thread, String exceptionClass, String message,
                       String trace, int count, Instant firstSeen, Instant lastSeen) {
    /** The room a summary gets: the Errors window's list rows and the notification's content. */
    static final int SUMMARY_LIMIT = 200;

    public Incident {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(thread, "thread");
        Objects.requireNonNull(exceptionClass, "exceptionClass");
        Objects.requireNonNull(message, "message");
        Objects.requireNonNull(trace, "trace");
        Objects.requireNonNull(firstSeen, "firstSeen");
        Objects.requireNonNull(lastSeen, "lastSeen");
        if (count < 1) throw new IllegalArgumentException("An incident happened at least once");
    }

    /** The exception's class name without its package. */
    public String simpleName() { return exceptionClass.substring(exceptionClass.lastIndexOf('.') + 1); }

    /** The message's first line, trimmed; empty when there is no message. */
    public String firstLine() {
        int newline = message.indexOf('\n');
        return (newline < 0 ? message : message.substring(0, newline)).strip();
    }

    /** {@code SimpleName: first line}, or the simple name alone, as one line of at most 200 characters. */
    public String summary() {
        String line = firstLine();
        return NotificationText.line(line.isEmpty() ? simpleName() : simpleName() + ": " + line, SUMMARY_LIMIT);
    }

    /** Whether a plugin, not Jasper, failed. */
    public boolean fromPlugin() { return !NotificationRequest.JASPER.equals(source); }

    /** The same failure again: counted and re-stamped; the first sighting's text is kept. */
    Incident repeated(Instant at) {
        return new Incident(id, source, operation, thread, exceptionClass, message, trace, count + 1, firstSeen, at);
    }
}
```

- [ ] **Step 6: Write `Incidents`**

Create `jasper-app/src/main/java/dev/jasper/app/notifications/Incidents.java`:

```java
package dev.jasper.app.notifications;

import dev.jasper.app.lifecycle.Subscription;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import javax.swing.SwingUtilities;

/**
 * This session's incidents, the Errors window's model (spec section 5): at most {@value #LIMIT}, the oldest dropped.
 * A failure from the same source, of the same class and with the same root-cause frames is a repeat of an earlier
 * incident. Nothing is written to disk. EDT-confined.
 */
public final class Incidents {
    public static final int LIMIT = 100;
    private static final System.Logger LOG = System.getLogger(Incidents.class.getName());

    /** What changed. */
    public sealed interface Change {
        record Added(Incident incident) implements Change {}
        record Repeated(Incident incident) implements Change {}
        /** Dropped to keep the newest {@value Incidents#LIMIT}. */
        record Removed(UUID id) implements Change {}
        record Cleared() implements Change {}
    }

    private final Clock clock;
    /** Oldest sighting first; a repeat moves to the end. Keyed by source, exception class and signature. */
    private final LinkedHashMap<String, Incident> byKey = new LinkedHashMap<>();
    private final List<Consumer<Change>> listeners = new ArrayList<>();

    /**
     * @param clock stamps sightings
     */
    public Incidents(Clock clock) { this.clock = Objects.requireNonNull(clock, "clock"); }

    /**
     * Records a failure: a new incident, or a repeat of the one with the same key.
     *
     * @param source {@code jasper} or a plugin id
     * @param operation what it was doing, such as "thread main" or "panel remote.sftp"
     * @param thread the name of the thread that caught it
     * @param capture what was taken of the throwable on that thread
     * @return the incident as it now stands
     */
    public Incident record(String source, String operation, String thread, Capture capture) {
        requireEdt();
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(thread, "thread");
        Objects.requireNonNull(capture, "capture");
        Instant now = clock.instant();
        String key = source + '\n' + capture.exceptionClass() + '\n' + capture.signature();
        Incident earlier = byKey.remove(key);
        if (earlier != null) {
            Incident again = earlier.repeated(now);
            byKey.put(key, again);
            emit(new Change.Repeated(again));
            return again;
        }
        Incident added = new Incident(UUID.randomUUID(), source, operation, thread, capture.exceptionClass(),
            capture.message(), capture.trace(), 1, now, now);
        byKey.put(key, added);
        var dropped = new ArrayList<UUID>();
        for (Iterator<Incident> oldest = byKey.values().iterator(); byKey.size() > LIMIT; ) {
            dropped.add(oldest.next().id());
            oldest.remove();
        }
        emit(new Change.Added(added));
        for (UUID id : dropped) emit(new Change.Removed(id));
        return added;
    }

    public Optional<Incident> find(UUID id) {
        requireEdt();
        return byKey.values().stream().filter(incident -> incident.id().equals(id)).findFirst();
    }

    /** The incidents, the most recently seen first. */
    public List<Incident> list() {
        requireEdt();
        var newestFirst = new ArrayList<>(byKey.values());
        Collections.reverse(newestFirst);
        return newestFirst;
    }

    /** The Errors window's Clear. */
    public void clear() {
        requireEdt();
        if (byKey.isEmpty()) return;
        byKey.clear();
        emit(new Change.Cleared());
    }

    public Subscription onChange(Consumer<Change> listener) {
        requireEdt();
        listeners.add(Objects.requireNonNull(listener, "listener"));
        return new Subscription(() -> listeners.remove(listener));
    }

    private void emit(Change change) {
        for (Consumer<Change> listener : List.copyOf(listeners)) {
            try { listener.accept(change); }
            catch (RuntimeException failure) { LOG.log(System.Logger.Level.WARNING, "An incident listener failed", failure); }
        }
    }

    private static void requireEdt() {
        if (!SwingUtilities.isEventDispatchThread()) throw new IllegalStateException("Incidents belong to the EDT");
    }
}
```

- [ ] **Step 7: Run the tests to see them pass**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.notifications.CaptureTest' --tests 'dev.jasper.app.notifications.IncidentsTest'`
Expected: PASS (15 tests).

- [ ] **Step 8: Commit**

```bash
git add jasper-app/src/main/java/dev/jasper/app/notifications/Capture.java jasper-app/src/main/java/dev/jasper/app/notifications/Incident.java jasper-app/src/main/java/dev/jasper/app/notifications/Incidents.java jasper-app/src/test/java/dev/jasper/app/notifications/CaptureTest.java jasper-app/src/test/java/dev/jasper/app/notifications/IncidentsTest.java
git commit -m "feat(notifications): add incidents and their captures" -m "A throwable's class, capped message and printStackTrace text are taken once without throwing; repeats coalesce by source, class and root-cause frames, keeping the newest 100." -m "🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 2: Repeats and action failures in the center

**Files:**
- Modify: `jasper-app/src/main/java/dev/jasper/app/notifications/NotificationCenter.java`
- Test: `jasper-app/src/test/java/dev/jasper/app/notifications/NotificationCenterTest.java`

**Interfaces:**
- Consumes: plan 1's `NotificationCenter` (fields `history`, `pending`, `presence`, `clock`; `emit`, `mode`,
  `requireEdt`), `Notification.repeated(Instant)`, `Delivery.route`, `Delivery.Outcome.HISTORY`.
- Produces:
  - `public boolean repeat(UUID id, boolean showCard)`
  - `public void reportActionFailuresTo(Consumer<Throwable> sink)`

- [ ] **Step 1: Write the failing tests**

Append these tests to `NotificationCenterTest` (before its closing brace; it already imports `Instant`, `Duration`,
`UUID`, `ArrayList` and AssertJ's `Assertions.*`):

```java
    @Test void aRepeatCountsReStampsMarksUnreadAndMovesToTheTop() {
        var center = center();
        UUID a = center.post(request(CARDS, "a")).orElseThrow();
        center.post(request(CARDS, "b"));
        center.markRead(a);
        clock.advance(Duration.ofMinutes(5));
        changes.clear();
        assertThat(center.repeat(a, false)).isTrue();
        Notification entry = center.find(a).orElseThrow();
        assertThat(entry.count()).isEqualTo(2);
        assertThat(entry.read()).isFalse();
        assertThat(entry.posted()).isEqualTo(Instant.parse("2026-10-02T14:10:00Z"));
        assertThat(center.history().getFirst().id()).isEqualTo(a);
        assertThat(kinds()).as("no card unless asked").containsExactly("Upserted");
    }

    @Test void aRepeatShowsItsCardWhenAskedAndItsGroupRoutesOne() {
        var center = center();
        UUID card = center.post(request(CARDS, "card")).orElseThrow();
        UUID quiet = center.post(request(QUIET, "quiet")).orElseThrow();
        changes.clear();
        center.repeat(card, true);
        center.repeat(quiet, true);
        assertThat(kinds()).containsExactly("Upserted", "CardRequested", "Upserted");
        presence = Delivery.Presence.BACKGROUND;
        UUID sticky = center.post(request(STICKY, "sticky")).orElseThrow();
        center.repeat(sticky, true);
        changes.clear();
        presence = Delivery.Presence.FOCUSED;
        center.focusReturned();
        assertThat(kinds()).as("a sticky repeat in the background waits for focus").containsExactly("CardRequested");
        assertThat(((NotificationCenter.Change.CardRequested) changes.getFirst()).notification().count()).isEqualTo(2);
    }

    @Test void aRepeatOfAGoneOrExpiredEntryIsRefused() {
        var center = center();
        UUID expired = center.post(request(CARDS, "expired")).orElseThrow();
        UUID removed = center.post(request(CARDS, "removed")).orElseThrow();
        center.expire(expired);
        center.remove(removed);
        changes.clear();
        assertThat(center.repeat(expired, true)).isFalse();
        assertThat(center.repeat(removed, true)).isFalse();
        assertThat(center.repeat(UUID.randomUUID(), true)).isFalse();
        assertThat(changes).isEmpty();
    }

    @Test void aFailingActionOrDefaultActionReachesTheFailureSink() {
        var center = center();
        var failures = new ArrayList<Throwable>();
        center.reportActionFailuresTo(failures::add);
        var action = new IllegalStateException("action broke");
        var activation = new IllegalArgumentException("activation broke");
        UUID id = center.post(request(CARDS, "boom")
            .withAction(new NotificationAction("Boom", () -> { throw action; }, false))
            .withActivate(() -> { throw activation; })).orElseThrow();
        center.runAction(id, 0);
        center.activate(id);
        assertThat(failures).containsExactly(action, activation);
    }

    @Test void aFailureSinkThatThrowsIsContained() {
        var center = center();
        center.reportActionFailuresTo(failure -> { throw new IllegalStateException("sink broke"); });
        UUID id = center.post(request(CARDS, "boom").withAction(NotificationAction.of("Boom", () -> { throw new IllegalStateException("boom"); })))
            .orElseThrow();
        assertThatCode(() -> center.runAction(id, 0)).doesNotThrowAnyException();
        assertThat(center.find(id).orElseThrow().expired()).isTrue();
    }
```

- [ ] **Step 2: Run them to see them fail**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.notifications.NotificationCenterTest'`
Expected: compilation fails: `repeat` and `reportActionFailuresTo` do not exist.

- [ ] **Step 3: Implement**

In `NotificationCenter.java`:

1. Below `private boolean delivering;` add:

```java
    private Consumer<Throwable> actionFailures = failure -> { };
```

2. In `post`, replace the routing tail

```java
        if (outcome.card()) {
            pending.remove(entry.id()); // a repeat may still wait from an earlier background post; its card shows now
            emit(new Change.Upserted(entry), new Change.CardRequested(entry));
        } else {
            if (outcome.pending()) pending.add(entry.id());
            emit(new Change.Upserted(entry));
        }
        return Optional.of(entry.id());
```

with

```java
        announce(entry, outcome);
        return Optional.of(entry.id());
```

3. After `submit`, add:

```java
    /**
     * An incident happened again (spec section 5): the entry is counted, re-stamped, unread again and moved to the
     * top, whatever its text. With {@code showCard} its card shows again as its group's mode routes it; otherwise a
     * card already showing only updates.
     *
     * @param id the entry
     * @param showCard whether the card may show again
     * @return false when the entry is gone or expired, so the caller posts a new notification instead
     */
    public boolean repeat(UUID id, boolean showCard) {
        requireEdt();
        Notification earlier = history.get(id);
        if (earlier == null || earlier.expired()) return false;
        Notification entry = earlier.repeated(clock.instant());
        history.remove(id);
        history.put(id, entry);
        announce(entry, showCard ? Delivery.route(mode(entry.request().group()), presence.get()) : Delivery.Outcome.HISTORY);
        return true;
    }

    /**
     * Where an action's or a default action's exception goes after it is logged; the application records an incident.
     *
     * @param sink hears each failure on the EDT
     */
    public void reportActionFailuresTo(Consumer<Throwable> sink) {
        requireEdt();
        actionFailures = Objects.requireNonNull(sink, "sink");
    }
```

4. Before `repeatOf`, add:

```java
    /** Emits the entry's change, with its card when the outcome shows one now, or remembers that the card waits. */
    private void announce(Notification entry, Delivery.Outcome outcome) {
        if (outcome.card()) {
            pending.remove(entry.id()); // a repeat may still wait from an earlier background post; its card shows now
            emit(new Change.Upserted(entry), new Change.CardRequested(entry));
        } else {
            if (outcome.pending()) pending.add(entry.id());
            emit(new Change.Upserted(entry));
        }
    }
```

5. Replace `contained`

```java
    private static void contained(Runnable task, String failed) {
        try { task.run(); }
        catch (RuntimeException failure) { LOG.log(System.Logger.Level.WARNING, failed, failure); }
    }
```

with

```java
    private void contained(Runnable task, String failed) {
        try { task.run(); }
        catch (RuntimeException failure) {
            LOG.log(System.Logger.Level.WARNING, failed, failure);
            try { actionFailures.accept(failure); }
            catch (RuntimeException unrecorded) {
                LOG.log(System.Logger.Level.WARNING, "A notification action's failure could not be recorded", unrecorded);
            }
        }
    }
```

- [ ] **Step 4: Run the tests to see them pass**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.notifications.*'`
Expected: PASS, including every existing center test.

- [ ] **Step 5: Commit**

```bash
git add jasper-app/src/main/java/dev/jasper/app/notifications/NotificationCenter.java jasper-app/src/test/java/dev/jasper/app/notifications/NotificationCenterTest.java
git commit -m "feat(notifications): let the center repeat an entry and report action failures" -m "An incident's repeat counts, re-stamps and re-routes its entry without comparing text; a failing action's exception reaches an application sink after it is logged." -m "🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 3: The Errors window

**Files:**
- Create: `jasper-app/src/main/java/dev/jasper/app/notifications/IncidentReport.java`
- Create: `jasper-app/src/main/java/dev/jasper/app/notificationui/ErrorsView.java`
- Create: `jasper-app/src/main/java/dev/jasper/app/application/ErrorsWindow.java`
- Test: `jasper-app/src/test/java/dev/jasper/app/notifications/IncidentReportTest.java`
- Test: `jasper-app/src/test/java/dev/jasper/app/notificationui/ErrorsViewTest.java`
- Test: `jasper-app/src/test/java/dev/jasper/app/notificationui/ErrorsViewTestSupport.java`
- Test: `jasper-app/src/test/java/dev/jasper/app/application/ErrorsWindowTest.java`

**Interfaces:**
- Consumes: Task 1's `Incident`, `Incidents`, `Capture`; plan 1's `notificationui.NotificationStyle.color(String, Color)`
  (package-private); `AppIcons.bundled("status/error")`; `AuxiliaryWindows.window(String, String, Dimension, boolean)`;
  `AuxiliarySurface` (`setContent`, `show`, `toFront`, `onClosed`, `closed`, `close`); `Contributions.addAction`.
- Produces:
  - `public final class IncidentReport` with `public record Environment(String jasper, String os, String java)`,
    `Environment.current()` and `public static String text(Incident incident, String pluginVersion, Environment environment)`.
  - `public final class ErrorsView extends JPanel` with
    `public record Hooks(Function<String, String> pluginName, Function<String, String> pluginVersion,
    IncidentReport.Environment environment, Consumer<String> copy, Consumer<String> disablePlugin, Runnable openLogFolder)`,
    `public ErrorsView(Incidents incidents, Hooks hooks, ZoneId zone)`, `public void select(UUID id)`,
    `public void close()`.
  - `final class ErrorsWindow` (application) with `ID = "jasper.errors"`, `SHOW_ERRORS = "notifications.errors"`,
    `SIZE = 900×560`, `ErrorsWindow(Contributions, AuxiliaryWindows, Incidents, ErrorsView.Hooks, ZoneId)`,
    `void show(Optional<UUID> incident)`, and test accessors `Optional<ErrorsView> view()`,
    `Optional<AuxiliarySurface> surface()`.

- [ ] **Step 1: Write the failing report tests**

Create `jasper-app/src/test/java/dev/jasper/app/notifications/IncidentReportTest.java`:

```java
package dev.jasper.app.notifications;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class IncidentReportTest {
    private static final IncidentReport.Environment HERE = new IncidentReport.Environment("1.4.0", "Mac OS X 15.6 (aarch64)",
        "OpenJDK Runtime Environment 25.0.4 (JetBrains s.r.o.)");

    private static Incident incident(String source, int count) {
        Instant at = Instant.parse("2026-10-02T14:05:00Z");
        return new Incident(UUID.randomUUID(), source, "panel remote.sftp", "AWT-EventQueue-0", "java.lang.IllegalStateException",
            "broken", "java.lang.IllegalStateException: broken\n\tat dev.example.Tool.run(Tool.java:3)", count, at, at);
    }

    @Test void aJasperReportNamesTheEnvironmentWhatItWasDoingAndTheTrace() {
        assertThat(IncidentReport.text(incident("jasper", 1), "", HERE)).isEqualTo("""
            Jasper 1.4.0
            OS: Mac OS X 15.6 (aarch64)
            Java: OpenJDK Runtime Environment 25.0.4 (JetBrains s.r.o.)
            Operation: panel remote.sftp
            Thread: AWT-EventQueue-0

            java.lang.IllegalStateException: broken
            \tat dev.example.Tool.run(Tool.java:3)
            """);
    }

    @Test void aPluginReportNamesThePluginItsVersionAndTheCount() {
        assertThat(IncidentReport.text(incident("dev.jasper.remote", 3), "2.1.0", HERE))
            .contains("Plugin: dev.jasper.remote 2.1.0\nOperation: panel remote.sftp\n", "Occurrences: 3\n");
        assertThat(IncidentReport.text(incident("dev.jasper.remote", 1), "", HERE))
            .contains("Plugin: dev.jasper.remote\n").doesNotContain("Occurrences");
    }

    @Test void theCurrentEnvironmentDescribesThisProcess() {
        var current = IncidentReport.Environment.current();
        assertThat(current.jasper()).isEqualTo(System.getProperty("jpackage.app-version", "development build"));
        assertThat(current.os()).startsWith(System.getProperty("os.name"));
        assertThat(current.java()).contains(System.getProperty("java.runtime.version"));
    }
}
```

- [ ] **Step 2: Write the failing view tests and their bridge**

Create `jasper-app/src/test/java/dev/jasper/app/notificationui/ErrorsViewTestSupport.java`:

```java
package dev.jasper.app.notificationui;

import dev.jasper.app.notifications.Incident;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Package-local test access, excluded from production artifacts. */
public final class ErrorsViewTestSupport {
    private ErrorsViewTestSupport() {}

    public static Optional<UUID> selected(ErrorsView view) { return view.selected().map(Incident::id); }

    public static List<Incident> listed(ErrorsView view) { return view.listed(); }
}
```

Create `jasper-app/src/test/java/dev/jasper/app/notificationui/ErrorsViewTest.java`:

```java
package dev.jasper.app.notificationui;

import dev.jasper.app.notifications.Capture;
import dev.jasper.app.notifications.Incident;
import dev.jasper.app.notifications.IncidentReport;
import dev.jasper.app.notifications.Incidents;
import dev.jasper.app.testsupport.EdtTestExtension;
import java.awt.Font;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.swing.JLabel;
import javax.swing.plaf.basic.BasicHTML;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(EdtTestExtension.class)
class ErrorsViewTest {
    /** A clock a test moves by hand. */
    private static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-10-02T14:05:00Z");
        void advance(Duration by) { now = now.plus(by); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }

    private static final IncidentReport.Environment HERE = new IncidentReport.Environment("1.4.0", "TestOS 1 (arm)", "TestJava 25 (Vendor)");
    private final MutableClock clock = new MutableClock();
    private final Incidents incidents = new Incidents(clock);
    private final List<String> copied = new ArrayList<>();
    private final List<String> disabled = new ArrayList<>();
    private int logFolders;

    private ErrorsView view() {
        return new ErrorsView(incidents, new ErrorsView.Hooks(id -> id.equals("dev.jasper.remote") ? "Remote" : id,
            id -> id.equals("dev.jasper.remote") ? "2.1.0" : "", HERE, copied::add, disabled::add, () -> logFolders++),
            ZoneOffset.UTC);
    }

    private Incident record(String source, String operation, Throwable failure) {
        return incidents.record(source, operation, "AWT-EventQueue-0", Capture.of(failure));
    }

    @Test void incidentsAreListedNewestFirstWithSummarySourceAndTime() {
        var view = view();
        record("jasper", "thread main", new IllegalStateException("first broke\nmore detail"));
        clock.advance(Duration.ofSeconds(30));
        Incident second = record("dev.jasper.remote", "panel remote.sftp",
            new java.io.UncheckedIOException("no route", new java.io.IOException("no route")));
        assertThat(view.listed()).extracting(Incident::id).first().isEqualTo(second.id());
        assertThat(view.rowSummary(0)).isEqualTo("UncheckedIOException: no route");
        assertThat(view.rowDetail(0)).isEqualTo("Remote · 14:05:30");
        assertThat(view.rowSummary(1)).isEqualTo("IllegalStateException: first broke");
        assertThat(view.rowDetail(1)).isEqualTo("Jasper · 14:05:00");
    }

    @Test void theSelectedIncidentShowsItsSourceSightingsMessageAndMonospacedTrace() {
        var view = view();
        var failure = new IllegalStateException("could not open the panel\nbecause of reasons");
        Incident incident = record("dev.jasper.remote", "panel remote.sftp", failure);
        clock.advance(Duration.ofMinutes(2));
        record("dev.jasper.remote", "panel remote.sftp", failure);
        assertThat(view.selected()).map(Incident::id).contains(incident.id());
        assertThat(view.showingEmpty()).isFalse();
        assertThat(view.headerText()).isEqualTo("Remote · panel remote.sftp");
        assertThat(view.sightingsText()).isEqualTo("First seen 14:05:00 · last seen 14:07:00 · 2 times");
        assertThat(view.messageText()).isEqualTo("could not open the panel\nbecause of reasons");
        assertThat(view.traceText()).isEqualTo(incident.trace()).startsWith("java.lang.IllegalStateException: could not open the panel");
        assertThat(view.traceFont().getName()).isEqualTo(Font.MONOSPACED);
        assertThat(view.rowDetail(0)).isEqualTo("Remote · ×2 · 14:07:00");
    }

    @Test void aSingleSightingSaysWhenItWasSeen() {
        var view = view();
        record("jasper", "thread main", new IllegalStateException("once"));
        assertThat(view.sightingsText()).isEqualTo("Seen at 14:05:00");
        assertThat(view.headerText()).isEqualTo("Jasper · thread main");
    }

    @Test void copyHandsTheReportToTheClipboardHook() {
        var view = view();
        Incident incident = record("dev.jasper.remote", "panel remote.sftp", new IllegalStateException("broke"));
        view.copyButton().doClick();
        assertThat(copied).containsExactly(IncidentReport.text(incident, "2.1.0", HERE));
    }

    @Test void disablePluginIsOfferedOnlyForAPluginsIncident() {
        var view = view();
        Incident own = record("jasper", "thread main", new IllegalStateException("ours"));
        Incident plugin = record("dev.jasper.remote", "panel remote.sftp", new IllegalArgumentException("theirs"));
        view.select(own.id());
        assertThat(view.disableButton().isVisible()).isFalse();
        view.select(plugin.id());
        assertThat(view.disableButton().isVisible()).isTrue();
        view.disableButton().doClick();
        assertThat(disabled).containsExactly("dev.jasper.remote");
    }

    @Test void openLogFolderAndClear() {
        var view = view();
        assertThat(view.showingEmpty()).isTrue();
        assertThat(view.clearButton().isEnabled()).isFalse();
        assertThat(view.copyButton().isEnabled()).isFalse();
        record("jasper", "thread main", new IllegalStateException("broke"));
        assertThat(view.clearButton().isEnabled()).isTrue();
        view.logFolderButton().doClick();
        assertThat(logFolders).isEqualTo(1);
        view.clearButton().doClick();
        assertThat(incidents.list()).isEmpty();
        assertThat(view.listed()).isEmpty();
        assertThat(view.showingEmpty()).isTrue();
        assertThat(view.copyButton().isEnabled()).isFalse();
    }

    @Test void aNewIncidentKeepsTheUsersSelection() {
        var view = view();
        Incident older = record("jasper", "a", new IllegalStateException("older"));
        record("jasper", "b", new IllegalArgumentException("newer"));
        view.select(older.id());
        record("jasper", "c", new UnsupportedOperationException("newest"));
        assertThat(view.selected()).map(Incident::id).contains(older.id());
        assertThat(view.listed()).hasSize(3);
    }

    @Test void selectIgnoresAnIncidentThatIsNotListed() {
        var view = view();
        Incident listed = record("jasper", "a", new IllegalStateException("listed"));
        view.select(UUID.randomUUID());
        assertThat(view.selected()).map(Incident::id).contains(listed.id());
    }

    @Test void textIsNeverMarkup() {
        var view = new ErrorsView(incidents, new ErrorsView.Hooks(id -> "<html><b>Evil</b>", id -> "", HERE, copied::add,
            disabled::add, () -> { }), ZoneOffset.UTC);
        record("dev.example.evil", "<html><i>op</i>", new IllegalStateException("<html><u>message</u>"));
        assertThat(view.headerText()).isEqualTo("<html><b>Evil</b> · <html><i>op</i>");
        for (JLabel label : List.of(view.headerLabel(), view.rowLabels(0).get(0), view.rowLabels(0).get(1))) {
            assertThat(label.getClientProperty("html.disable")).isEqualTo(Boolean.TRUE);
            assertThat(label.getClientProperty(BasicHTML.propertyKey)).isNull();
        }
    }

    @Test void closingStopsListening() {
        var view = view();
        view.close();
        record("jasper", "a", new IllegalStateException("after close"));
        assertThat(view.listed()).isEmpty();
    }
}
```

- [ ] **Step 3: Write the failing window tests**

Create `jasper-app/src/test/java/dev/jasper/app/application/ErrorsWindowTest.java`:

```java
package dev.jasper.app.application;

import dev.jasper.app.contributions.Contributions;
import dev.jasper.app.notifications.Capture;
import dev.jasper.app.notifications.Incident;
import dev.jasper.app.notifications.IncidentReport;
import dev.jasper.app.notifications.Incidents;
import dev.jasper.app.notificationui.ErrorsView;
import dev.jasper.app.notificationui.ErrorsViewTestSupport;
import dev.jasper.app.persistence.UiState;
import dev.jasper.app.testsupport.EdtTestExtension;
import dev.jasper.app.windows.AuxiliarySurface;
import dev.jasper.app.windows.AuxiliaryWindows;
import java.awt.Dimension;
import java.awt.Rectangle;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(EdtTestExtension.class)
class ErrorsWindowTest {
    private final List<String> shell = new ArrayList<>();
    private final Contributions contributions = new Contributions();
    private final Incidents incidents = new Incidents(Clock.systemUTC());
    private final AuxiliaryWindows windows = new AuxiliaryWindows(UiState.inMemory(), surface -> new AuxiliarySurface.Shell(
        () -> shell.add("show"), () -> shell.add("front"), () -> shell.add("dispose"), title -> { },
        () -> new Rectangle(0, 0, 900, 560), (title, initial) -> Optional.empty()));

    private ErrorsWindow window() {
        return new ErrorsWindow(contributions, windows, incidents, new ErrorsView.Hooks(id -> id, id -> "",
            new IncidentReport.Environment("1", "os", "java"), text -> { }, id -> { }, () -> { }), ZoneOffset.UTC);
    }

    @Test void showErrorsOpensOneErrorsWindow() {
        window();
        var action = contributions.action(ErrorsWindow.SHOW_ERRORS).orElseThrow();
        assertThat(action.title()).isEqualTo("Notifications: Show Errors");
        action.invoke(new Contributions.Invocation(UUID.randomUUID(), Optional.empty()));
        action.invoke(new Contributions.Invocation(UUID.randomUUID(), Optional.empty()));
        assertThat(windows.open()).singleElement().satisfies(surface -> {
            assertThat(surface.id()).isEqualTo(ErrorsWindow.ID);
            assertThat(surface.title()).isEqualTo("Errors");
            assertThat(surface.preferredSize()).isEqualTo(new Dimension(900, 560));
        });
        assertThat(shell).containsExactly("show", "front", "show", "front");
    }

    @Test void showMoreSelectsItsIncidentInTheSameWindow() {
        var errors = window();
        Incident first = incidents.record("jasper", "a", "t", Capture.of(new IllegalStateException("first")));
        Incident second = incidents.record("jasper", "b", "t", Capture.of(new IllegalArgumentException("second")));
        errors.show(Optional.of(first.id()));
        assertThat(ErrorsViewTestSupport.selected(errors.view().orElseThrow())).contains(first.id());
        errors.show(Optional.of(second.id()));
        assertThat(windows.open()).hasSize(1);
        assertThat(ErrorsViewTestSupport.selected(errors.view().orElseThrow())).contains(second.id());
    }

    @Test void closingTheWindowStopsItsViewAndTheNextShowOpensAFreshOne() {
        var errors = window();
        errors.show(Optional.empty());
        ErrorsView first = errors.view().orElseThrow();
        errors.surface().orElseThrow().close();
        assertThat(errors.view()).isEmpty();
        incidents.record("jasper", "a", "t", Capture.of(new IllegalStateException("after close")));
        assertThat(ErrorsViewTestSupport.listed(first)).as("the closed view stopped listening").isEmpty();
        errors.show(Optional.empty());
        assertThat(errors.view().orElseThrow()).isNotSameAs(first);
        assertThat(ErrorsViewTestSupport.listed(errors.view().orElseThrow())).hasSize(1);
    }
}
```

- [ ] **Step 4: Run the tests to see them fail**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.notifications.IncidentReportTest' --tests 'dev.jasper.app.notificationui.ErrorsViewTest' --tests 'dev.jasper.app.application.ErrorsWindowTest'`
Expected: compilation fails: `IncidentReport`, `ErrorsView` and `ErrorsWindow` do not exist.

- [ ] **Step 5: Write `IncidentReport`**

Create `jasper-app/src/main/java/dev/jasper/app/notifications/IncidentReport.java`:

```java
package dev.jasper.app.notifications;

import java.util.Objects;

/**
 * The text Copy puts on the clipboard for an incident (spec section 5): Jasper's version, the OS and the Java runtime,
 * the plugin and its version when a plugin failed, what it was doing and on which thread, then the trace. Pure.
 */
public final class IncidentReport {
    /**
     * Where Jasper runs.
     *
     * @param jasper Jasper's version
     * @param os the operating system, its version and architecture
     * @param java the Java runtime, its version and vendor
     */
    public record Environment(String jasper, String os, String java) {
        public Environment {
            Objects.requireNonNull(jasper, "jasper");
            Objects.requireNonNull(os, "os");
            Objects.requireNonNull(java, "java");
        }

        /** This process's environment: the packaged version, or "development build" from Gradle or an IDE. */
        public static Environment current() {
            return new Environment(System.getProperty("jpackage.app-version", "development build"),
                System.getProperty("os.name", "unknown OS") + " " + System.getProperty("os.version", "") + " ("
                    + System.getProperty("os.arch", "") + ")",
                System.getProperty("java.runtime.name", "Java") + " "
                    + System.getProperty("java.runtime.version", System.getProperty("java.version", "")) + " ("
                    + System.getProperty("java.vm.vendor", "") + ")");
        }
    }

    private IncidentReport() {}

    /**
     * @param incident what failed
     * @param pluginVersion the plugin's version, empty when unknown; unused for Jasper's own incidents
     * @param environment where Jasper runs
     * @return the report, ending with the trace and a line feed
     */
    public static String text(Incident incident, String pluginVersion, Environment environment) {
        var out = new StringBuilder()
            .append("Jasper ").append(environment.jasper()).append('\n')
            .append("OS: ").append(environment.os()).append('\n')
            .append("Java: ").append(environment.java()).append('\n');
        if (incident.fromPlugin())
            out.append("Plugin: ").append(incident.source()).append(pluginVersion.isEmpty() ? "" : " " + pluginVersion).append('\n');
        out.append("Operation: ").append(incident.operation()).append('\n')
            .append("Thread: ").append(incident.thread()).append('\n');
        if (incident.count() > 1) out.append("Occurrences: ").append(incident.count()).append('\n');
        return out.append('\n').append(incident.trace()).append('\n').toString();
    }
}
```

- [ ] **Step 6: Write `ErrorsView`**

Create `jasper-app/src/main/java/dev/jasper/app/notificationui/ErrorsView.java`:

```java
package dev.jasper.app.notificationui;

import com.formdev.flatlaf.util.UIScale;
import dev.jasper.app.lifecycle.Subscription;
import dev.jasper.app.notifications.Incident;
import dev.jasper.app.notifications.IncidentReport;
import dev.jasper.app.notifications.Incidents;
import dev.jasper.app.platform.AppIcons;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Font;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingConstants;
import javax.swing.UIManager;

/**
 * The Errors window's content (spec section 5): this session's incidents, newest first, beside the selected one's
 * source and operation, sightings, message and stack trace, with Copy, Disable Plugin (a plugin's incidents only),
 * Open Log Folder and Clear. Every text is plain. EDT only; {@link #close} stops it listening.
 */
public final class ErrorsView extends JPanel {
    /**
     * What the view asks of the application.
     *
     * @param pluginName a plugin's display name by id
     * @param pluginVersion a plugin's version by id, empty when unknown
     * @param environment what Copy's report names
     * @param copy puts a report on the clipboard
     * @param disablePlugin disables a plugin by id from the next launch
     * @param openLogFolder shows Jasper's log folder
     */
    public record Hooks(Function<String, String> pluginName, Function<String, String> pluginVersion,
                        IncidentReport.Environment environment, Consumer<String> copy, Consumer<String> disablePlugin,
                        Runnable openLogFolder) {
        public Hooks {
            Objects.requireNonNull(pluginName, "pluginName");
            Objects.requireNonNull(pluginVersion, "pluginVersion");
            Objects.requireNonNull(environment, "environment");
            Objects.requireNonNull(copy, "copy");
            Objects.requireNonNull(disablePlugin, "disablePlugin");
            Objects.requireNonNull(openLogFolder, "openLogFolder");
        }
    }

    private static final String EMPTY = "empty";
    private static final String DETAIL = "detail";
    private final Incidents incidents;
    private final Hooks hooks;
    private final DateTimeFormatter time;
    private final DefaultListModel<Incident> model = new DefaultListModel<>();
    private final JList<Incident> list = new JList<>(model) {
        // Rows take the list's width, so a long summary ends in an ellipsis instead of widening the list.
        @Override public boolean getScrollableTracksViewportWidth() { return true; }
    };
    private final CardLayout cards = new CardLayout();
    private final JPanel detail = new JPanel(cards);
    private final JLabel header = plain();
    private final JLabel sightings = plain();
    private final ReadOnlyText message = new ReadOnlyText(false);
    private final ReadOnlyText trace = new ReadOnlyText(true);
    private final JButton copy = new JButton("Copy");
    private final JButton disable = new JButton("Disable Plugin");
    private final JButton logFolder = new JButton("Open Log Folder");
    private final JButton clear = new JButton("Clear");
    private final Subscription listening;
    private UUID shown;
    private boolean refreshing;
    private boolean closed;

    /**
     * @param incidents the store it lists and clears
     * @param hooks what it asks of the application
     * @param zone the zone sightings are shown in
     */
    public ErrorsView(Incidents incidents, Hooks hooks, ZoneId zone) {
        super(new BorderLayout());
        this.incidents = Objects.requireNonNull(incidents, "incidents");
        this.hooks = Objects.requireNonNull(hooks, "hooks");
        time = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(zone);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setCellRenderer(new Row());
        list.getAccessibleContext().setAccessibleName("Errors");
        list.addListSelectionListener(event -> { if (!event.getValueIsAdjusting()) showSelected(); });
        var listScroll = new JScrollPane(list, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
            ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);

        header.setFont(header.getFont().deriveFont(Font.BOLD));
        header.setAlignmentX(LEFT_ALIGNMENT);
        sightings.setForeground(NotificationStyle.color("Jasper.mutedForeground", Color.GRAY));
        sightings.setAlignmentX(LEFT_ALIGNMENT);
        message.setLineWrap(true);
        message.setWrapStyleWord(true);
        message.setRows(3);
        message.getAccessibleContext().setAccessibleName("Message");
        trace.getAccessibleContext().setAccessibleName("Stack trace");
        var messageScroll = new JScrollPane(message, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
            ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        messageScroll.setAlignmentX(LEFT_ALIGNMENT);
        var heading = new JPanel();
        heading.setLayout(new BoxLayout(heading, BoxLayout.Y_AXIS));
        heading.setOpaque(false);
        heading.add(header);
        heading.add(Box.createVerticalStrut(UIScale.scale(2)));
        heading.add(sightings);
        heading.add(Box.createVerticalStrut(UIScale.scale(8)));
        heading.add(messageScroll);
        var details = new JPanel(new BorderLayout(0, UIScale.scale(8)));
        details.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));
        details.add(heading, BorderLayout.NORTH);
        details.add(new JScrollPane(trace), BorderLayout.CENTER);
        JLabel empty = plain();
        empty.setText("No errors");
        empty.setHorizontalAlignment(SwingConstants.CENTER);
        empty.setForeground(NotificationStyle.color("Jasper.mutedForeground", Color.GRAY));
        detail.add(empty, EMPTY);
        detail.add(details, DETAIL);

        var split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, listScroll, detail);
        split.setResizeWeight(0.35);
        split.setDividerLocation(UIScale.scale(320));
        split.setBorder(BorderFactory.createEmptyBorder());
        add(split, BorderLayout.CENTER);

        var leading = new JPanel(new FlowLayout(FlowLayout.LEADING, UIScale.scale(6), 0));
        leading.add(copy);
        leading.add(disable);
        var trailing = new JPanel(new FlowLayout(FlowLayout.TRAILING, UIScale.scale(6), 0));
        trailing.add(logFolder);
        trailing.add(clear);
        var buttons = new JPanel(new BorderLayout());
        buttons.setBorder(BorderFactory.createEmptyBorder(8, 6, 8, 6));
        buttons.add(leading, BorderLayout.WEST);
        buttons.add(trailing, BorderLayout.EAST);
        add(buttons, BorderLayout.SOUTH);

        copy.addActionListener(event -> selected().ifPresent(incident -> hooks.copy().accept(report(incident))));
        disable.addActionListener(event -> selected().filter(Incident::fromPlugin)
            .ifPresent(incident -> hooks.disablePlugin().accept(incident.source())));
        logFolder.addActionListener(event -> hooks.openLogFolder().run());
        clear.addActionListener(event -> incidents.clear());
        listening = incidents.onChange(change -> refresh());
        refresh();
    }

    /**
     * Selects an incident when it is listed; Show more… uses this. An incident no longer listed is ignored.
     *
     * @param id the incident
     */
    public void select(UUID id) {
        int index = indexOf(id);
        if (index < 0) return;
        list.setSelectedIndex(index);
        list.ensureIndexIsVisible(index);
    }

    /** Stops listening; the window calls this when it closes. Idempotent. */
    public void close() {
        if (closed) return;
        closed = true;
        listening.close();
    }

    Optional<Incident> selected() { return Optional.ofNullable(list.getSelectedValue()); }

    /** Rebuilds the list, keeping the user's selection, else selecting the newest. */
    private void refresh() {
        UUID chosen = selected().map(Incident::id).orElse(null);
        List<Incident> now = incidents.list();
        refreshing = true;
        try {
            model.clear();
            now.forEach(model::addElement);
            int index = indexOf(chosen);
            if (index < 0 && !now.isEmpty()) index = 0;
            if (index >= 0) list.setSelectedIndex(index);
        } finally {
            refreshing = false;
        }
        clear.setEnabled(!now.isEmpty());
        showSelected();
    }

    private int indexOf(UUID id) {
        if (id == null) return -1;
        for (int i = 0; i < model.size(); i++) if (model.get(i).id().equals(id)) return i;
        return -1;
    }

    private void showSelected() {
        if (refreshing) return;
        Optional<Incident> chosen = selected();
        copy.setEnabled(chosen.isPresent());
        disable.setVisible(chosen.map(Incident::fromPlugin).orElse(false));
        if (chosen.isEmpty()) {
            shown = null;
            cards.show(detail, EMPTY);
            return;
        }
        Incident incident = chosen.get();
        header.setText(sourceName(incident) + " · " + incident.operation());
        sightings.setText(sightingsOf(incident));
        if (!incident.id().equals(shown)) {
            // The first sighting's text never changes, so a long trace is set once per selection.
            message.setText(incident.message().isEmpty() ? "No message" : incident.message());
            message.setCaretPosition(0);
            trace.setText(incident.trace());
            trace.setCaretPosition(0);
            shown = incident.id();
        }
        cards.show(detail, DETAIL);
    }

    private String sourceName(Incident incident) {
        return incident.fromPlugin() ? hooks.pluginName().apply(incident.source()) : "Jasper";
    }

    private String sightingsOf(Incident incident) {
        if (incident.count() == 1) return "Seen at " + time.format(incident.firstSeen());
        return "First seen " + time.format(incident.firstSeen()) + " · last seen " + time.format(incident.lastSeen())
            + " · " + incident.count() + " times";
    }

    private String detailLine(Incident incident) {
        return sourceName(incident) + (incident.count() > 1 ? " · ×" + incident.count() : "") + " · "
            + time.format(incident.lastSeen());
    }

    private String report(Incident incident) {
        String version = incident.fromPlugin() ? hooks.pluginVersion().apply(incident.source()) : "";
        return IncidentReport.text(incident, version == null ? "" : version, hooks.environment());
    }

    /** A label that never renders markup: the property goes in before any text is set. */
    private static JLabel plain() {
        var label = new JLabel();
        label.putClientProperty("html.disable", Boolean.TRUE);
        return label;
    }

    List<Incident> listed() { return Collections.list(model.elements()); }
    String headerText() { return header.getText(); }
    JLabel headerLabel() { return header; }
    String sightingsText() { return sightings.getText(); }
    String messageText() { return message.getText(); }
    String traceText() { return trace.getText(); }
    Font traceFont() { return trace.getFont(); }
    boolean showingEmpty() { return detail.getComponent(0).isVisible(); }
    JButton copyButton() { return copy; }
    JButton disableButton() { return disable; }
    JButton logFolderButton() { return logFolder; }
    JButton clearButton() { return clear; }
    String rowSummary(int index) { return rendered(index).summaryLabel.getText(); }
    String rowDetail(int index) { return rendered(index).detailLabel.getText(); }
    List<JLabel> rowLabels(int index) {
        Row row = rendered(index);
        return List.of(row.summaryLabel, row.detailLabel);
    }

    private Row rendered(int index) {
        return (Row) list.getCellRenderer().getListCellRendererComponent(list, model.get(index), index, false, false);
    }

    /** One list row: the error icon and summary, then the source, count and last sighting. */
    private final class Row extends JPanel implements ListCellRenderer<Incident> {
        private final JLabel summaryLabel = plain();
        private final JLabel detailLabel = plain();

        Row() {
            super(new BorderLayout(0, UIScale.scale(2)));
            setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
            summaryLabel.setIcon(AppIcons.bundled("status/error"));
            detailLabel.setBorder(BorderFactory.createEmptyBorder(0, UIScale.scale(20), 0, 0));
            add(summaryLabel, BorderLayout.NORTH);
            add(detailLabel, BorderLayout.SOUTH);
        }

        @Override public Component getListCellRendererComponent(JList<? extends Incident> owner, Incident incident, int index,
                                                                boolean selected, boolean focused) {
            summaryLabel.setText(incident.summary());
            detailLabel.setText(detailLine(incident));
            Color foreground = selected ? owner.getSelectionForeground() : owner.getForeground();
            setOpaque(true);
            setBackground(selected ? owner.getSelectionBackground() : owner.getBackground());
            summaryLabel.setFont(owner.getFont());
            detailLabel.setFont(owner.getFont());
            summaryLabel.setForeground(foreground);
            detailLabel.setForeground(selected ? foreground : NotificationStyle.color("Jasper.mutedForeground", Color.GRAY));
            getAccessibleContext().setAccessibleName(incident.summary() + ", " + detailLine(incident));
            return this;
        }
    }

    /** Read-only text in the label font, or a monospaced one, kept through a look-and-feel change. */
    private static final class ReadOnlyText extends JTextArea {
        private final boolean monospaced;

        ReadOnlyText(boolean monospaced) {
            this.monospaced = monospaced;
            setEditable(false);
            restyle();
        }

        @Override public void updateUI() {
            super.updateUI();
            // JTextArea's constructor calls this before the field is set; the constructor restyles again.
            restyle();
        }

        private void restyle() {
            Font label = UIManager.getFont("Label.font");
            int size = label == null ? 12 : label.getSize();
            if (monospaced) setFont(new Font(Font.MONOSPACED, Font.PLAIN, size));
            else if (label != null) setFont(label.deriveFont(Font.PLAIN));
        }
    }
}
```

- [ ] **Step 7: Write `ErrorsWindow`**

Create `jasper-app/src/main/java/dev/jasper/app/application/ErrorsWindow.java`:

```java
package dev.jasper.app.application;

import dev.jasper.app.contributions.Contributions;
import dev.jasper.app.notifications.Incidents;
import dev.jasper.app.notificationui.ErrorsView;
import dev.jasper.app.platform.AppIcons;
import dev.jasper.app.windows.AuxiliarySurface;
import dev.jasper.app.windows.AuxiliaryWindows;
import java.awt.Dimension;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The Errors window (spec section 5): one auxiliary window over this session's incidents, opened by an incident's
 * Show more… link with that incident selected, and by Notifications: Show Errors. The auxiliary windows remember its
 * bounds and close it at shutdown; closing it stops its view. EDT only.
 */
final class ErrorsWindow {
    static final String ID = "jasper.errors";
    static final String SHOW_ERRORS = "notifications.errors";
    static final Dimension SIZE = new Dimension(900, 560);

    private final AuxiliaryWindows windows;
    private final Incidents incidents;
    private final ErrorsView.Hooks hooks;
    private final ZoneId zone;
    private AuxiliarySurface surface;
    private ErrorsView view;

    ErrorsWindow(Contributions contributions, AuxiliaryWindows windows, Incidents incidents, ErrorsView.Hooks hooks, ZoneId zone) {
        this.windows = windows;
        this.incidents = incidents;
        this.hooks = hooks;
        this.zone = zone;
        contributions.addAction(SHOW_ERRORS, "Notifications: Show Errors", AppIcons.bundled("status/error"),
            List.of("notifications", "errors", "exceptions", "stack trace", "incidents", "problems"), Optional.empty(),
            invocation -> show(Optional.empty()));
    }

    /** Opens the window, or brings it forward, and selects {@code incident} when it is still listed. */
    void show(Optional<UUID> incident) {
        if (surface == null || surface.closed()) {
            surface = windows.window(ID, "Errors", SIZE, true);
            ErrorsView opened = new ErrorsView(incidents, hooks, zone);
            surface.setContent(opened);
            surface.onClosed(opened::close);
            view = opened;
        }
        incident.ifPresent(view::select);
        surface.show();
        surface.toFront();
    }

    /** Test access: the open window's view. */
    Optional<ErrorsView> view() { return surface == null || surface.closed() ? Optional.empty() : Optional.of(view); }

    /** Test access: the open window. */
    Optional<AuxiliarySurface> surface() { return surface == null || surface.closed() ? Optional.empty() : Optional.of(surface); }
}
```

- [ ] **Step 8: Run the tests to see them pass**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.notifications.IncidentReportTest' --tests 'dev.jasper.app.notificationui.ErrorsViewTest' --tests 'dev.jasper.app.application.ErrorsWindowTest'`
Expected: PASS (16 tests).

- [ ] **Step 9: Commit**

```bash
git add jasper-app/src/main/java/dev/jasper/app/notifications/IncidentReport.java jasper-app/src/main/java/dev/jasper/app/notificationui/ErrorsView.java jasper-app/src/main/java/dev/jasper/app/application/ErrorsWindow.java jasper-app/src/test/java/dev/jasper/app/notifications/IncidentReportTest.java jasper-app/src/test/java/dev/jasper/app/notificationui/ErrorsViewTest.java jasper-app/src/test/java/dev/jasper/app/notificationui/ErrorsViewTestSupport.java jasper-app/src/test/java/dev/jasper/app/application/ErrorsWindowTest.java
git commit -m "feat(notifications): add the Errors window" -m "One auxiliary window lists the session's incidents beside the selected one's source, sightings, message and monospaced trace, with Copy, Disable Plugin, Open Log Folder and Clear; Notifications: Show Errors opens it." -m "🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 4: Incident notices

**Files:**
- Create: `jasper-app/src/main/java/dev/jasper/app/application/IncidentNotices.java`
- Test: `jasper-app/src/test/java/dev/jasper/app/application/IncidentNoticesTest.java`

**Interfaces:**
- Consumes: Task 1's `Capture.of`, `Incident`, `Incidents` (`record`, `onChange`, `Change.Removed`, `Change.Cleared`);
  Task 2's `NotificationCenter.repeat(UUID, boolean)`; plan 1's `NotificationCenter` (`register`, `post`, `submit`,
  `expire`, `onChange`, `clock`), `NotificationsFeature.GENERAL`; `ErrorsWindow` (Task 3, named in the own-class
  list).
- Produces (package-private, `dev.jasper.app.application`):
  - `IncidentNotices(NotificationCenter center, Incidents incidents, Executor ui, Function<String, String> pluginName,
    BiConsumer<String, Consumer<Optional<String>>> disable, Consumer<UUID> openErrors)`
  - groups `PLUGINS` (`jasper.plugins`, "Plugin problems", `CARD`) and `ERRORS` (`jasper.errors`, "Internal errors",
    `CARD`); constants `CARD_INTERVAL` (1 minute), `SHOW_MORE`, `DISABLE_PLUGIN`, `LAUNCH`, `LAUNCH_TITLE`
  - any thread: `void jasperFailure(String operation, Throwable failure)`,
    `void pluginFailure(String pluginId, String operation, Throwable failure)`,
    `void launchFailure(UUID window, String details, Throwable failure)`
  - EDT: `void disablePlugin(String pluginId)`, `void close()`
  - `static String pluginTitle(String name, String operation)`, `static boolean ownFailure(Throwable failure)`

- [ ] **Step 1: Write the failing tests**

Create `jasper-app/src/test/java/dev/jasper/app/application/IncidentNoticesTest.java`:

```java
package dev.jasper.app.application;

import dev.jasper.app.notifications.Capture;
import dev.jasper.app.notifications.Delivery;
import dev.jasper.app.notifications.DisplayMode;
import dev.jasper.app.notifications.Incident;
import dev.jasper.app.notifications.Incidents;
import dev.jasper.app.notifications.Notification;
import dev.jasper.app.notifications.NotificationAction;
import dev.jasper.app.notifications.NotificationCenter;
import dev.jasper.app.notifications.NotificationGroup;
import dev.jasper.app.notifications.Severity;
import dev.jasper.app.testsupport.EdtTestExtension;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(EdtTestExtension.class)
class IncidentNoticesTest {
    /** A clock a test moves by hand. */
    private static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-10-02T14:05:00Z");
        void advance(Duration by) { now = now.plus(by); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }

    /** A failure whose message, and so its toString and trace, cannot be read. */
    private static final class Unreadable extends RuntimeException {
        @Override public String getMessage() { throw new IllegalStateException("unreadable"); }
    }

    private final MutableClock clock = new MutableClock();
    private final NotificationCenter center = new NotificationCenter(Runnable::run, clock, () -> Delivery.Presence.FOCUSED);
    private final Incidents incidents = new Incidents(clock);
    private final List<NotificationCenter.Change> changes = new ArrayList<>();
    private final List<UUID> opened = new ArrayList<>();
    private final List<String> disabled = new ArrayList<>();
    private Optional<String> disableProblem = Optional.empty();
    private Executor ui = Runnable::run;

    private IncidentNotices notices() {
        center.register(NotificationsFeature.GENERAL);
        center.onChange(changes::add);
        return new IncidentNotices(center, incidents, task -> ui.execute(task),
            id -> id.equals("dev.jasper.remote") ? "Remote" : id,
            (id, done) -> { disabled.add(id); done.accept(disableProblem); }, opened::add);
    }

    private static List<String> labels(Notification notification) {
        return notification.request().actions().stream().map(NotificationAction::label).toList();
    }

    private long cards() { return changes.stream().filter(NotificationCenter.Change.CardRequested.class::isInstance).count(); }

    /** Failures made on one line, so they share a signature. */
    private static List<IllegalStateException> sameSite(int count) {
        var failures = new ArrayList<IllegalStateException>();
        for (int i = 0; i < count; i++) failures.add(new IllegalStateException("broken " + i));
        return failures;
    }

    /** A failure whose stack starts in Swing, then runs through each of {@code jasperClasses}. */
    private static IllegalStateException thrownIn(String... jasperClasses) {
        var frames = new ArrayList<StackTraceElement>();
        frames.add(new StackTraceElement("javax.swing.JComponent", "paint", "JComponent.java", 1));
        for (int i = 0; i < jasperClasses.length; i++) frames.add(new StackTraceElement(jasperClasses[i], "run", "Source.java", 10 + i));
        var failure = new IllegalStateException("from " + jasperClasses[0]);
        failure.setStackTrace(frames.toArray(StackTraceElement[]::new));
        return failure;
    }

    @Test void declaresThePluginProblemsAndInternalErrorsGroups() {
        notices();
        assertThat(center.group("jasper.plugins")).contains(new NotificationGroup("jasper.plugins", "Plugin problems", "jasper", DisplayMode.CARD));
        assertThat(center.group("jasper.errors")).contains(new NotificationGroup("jasper.errors", "Internal errors", "jasper", DisplayMode.CARD));
    }

    @Test void aJasperFailureIsAnIncidentAndAnInternalError() {
        var notices = notices();
        notices.jasperFailure("thread worker", new IllegalStateException("broken\nsecond line"));
        Incident incident = incidents.list().getFirst();
        assertThat(incident.source()).isEqualTo("jasper");
        assertThat(incident.operation()).isEqualTo("thread worker");
        assertThat(incident.thread()).isEqualTo(Thread.currentThread().getName());
        Notification notice = center.history().getFirst();
        assertThat(notice.request().group()).isEqualTo("jasper.errors");
        assertThat(notice.request().severity()).isEqualTo(Severity.ERROR);
        assertThat(notice.request().title()).isEqualTo("Unexpected error in Jasper");
        assertThat(notice.request().content()).isEqualTo("IllegalStateException: broken");
        assertThat(labels(notice)).containsExactly("Show more…");
        assertThat(cards()).isEqualTo(1);
        center.runAction(notice.id(), 0);
        assertThat(opened).containsExactly(incident.id());
        assertThat(center.find(notice.id()).orElseThrow().expired()).as("Show more… keeps working").isFalse();
    }

    @Test void aPluginFailureNamesThePluginAndOffersDisablePlugin() {
        var notices = notices();
        notices.pluginFailure("dev.jasper.remote", "panel remote.sftp",
            new java.io.UncheckedIOException("no route", new java.io.IOException("no route")));
        Notification notice = center.history().getFirst();
        assertThat(notice.request().group()).isEqualTo("jasper.plugins");
        assertThat(notice.request().title()).isEqualTo("Remote failed in panel remote.sftp");
        assertThat(notice.request().content()).isEqualTo("UncheckedIOException: no route");
        assertThat(notice.request().source()).as("Jasper posts it, not the plugin").isEqualTo("jasper");
        assertThat(labels(notice)).containsExactly("Show more…", "Disable Plugin");
        center.runAction(notice.id(), 1);
        assertThat(disabled).containsExactly("dev.jasper.remote");
        Notification outcome = center.history().getFirst();
        assertThat(outcome.request().title()).isEqualTo("Remote will be disabled when Jasper restarts");
        assertThat(outcome.request().severity()).isEqualTo(Severity.INFO);
        assertThat(outcome.request().group()).isEqualTo("jasper.plugins");
        assertThat(center.find(notice.id()).orElseThrow().expired()).isFalse();
    }

    @Test void startAndStopFailuresReadNaturallyAndAFailedDisableIsAnError() {
        assertThat(IncidentNotices.pluginTitle("Remote", "start")).isEqualTo("Remote failed to start");
        assertThat(IncidentNotices.pluginTitle("Remote", "stop")).isEqualTo("Remote failed to stop");
        assertThat(IncidentNotices.pluginTitle("Remote", "action remote.connect")).isEqualTo("Remote failed in action remote.connect");
        var notices = notices();
        disableProblem = Optional.of("The plugin state is locked");
        notices.disablePlugin("dev.jasper.remote");
        Notification outcome = center.history().getFirst();
        assertThat(outcome.request().title()).isEqualTo("Could not disable Remote");
        assertThat(outcome.request().content()).isEqualTo("The plugin state is locked");
        assertThat(outcome.request().group()).isEqualTo("jasper.plugins");
        assertThat(outcome.request().severity()).isEqualTo(Severity.ERROR);
    }

    @Test void aRepeatUpdatesTheSameNotificationAndShowsItsCardAtMostOnceAMinute() {
        var notices = notices();
        List<IllegalStateException> burst = sameSite(4);
        notices.jasperFailure("thread worker", burst.get(0));
        clock.advance(Duration.ofSeconds(10));
        notices.jasperFailure("thread worker", burst.get(1));
        clock.advance(Duration.ofSeconds(10));
        notices.jasperFailure("thread worker", burst.get(2));
        assertThat(center.history()).singleElement().satisfies(entry -> {
            assertThat(entry.count()).isEqualTo(3);
            assertThat(entry.read()).isFalse();
        });
        assertThat(incidents.list()).singleElement().satisfies(incident -> assertThat(incident.count()).isEqualTo(3));
        assertThat(cards()).as("one card in the first minute").isEqualTo(1);
        clock.advance(Duration.ofSeconds(41));
        notices.jasperFailure("thread worker", burst.get(3));
        assertThat(cards()).as("a minute after the last card").isEqualTo(2);
        assertThat(center.history().getFirst().count()).isEqualTo(4);
    }

    @Test void aBurstFromAnotherThreadIsOneIncidentAndOneCard() throws Exception {
        var queued = new ConcurrentLinkedQueue<Runnable>();
        ui = queued::add;
        var notices = notices();
        List<IllegalStateException> burst = sameSite(50);
        Thread worker = Thread.ofPlatform().name("jasper-test-burst")
            .start(() -> burst.forEach(failure -> notices.pluginFailure("dev.jasper.remote", "background task", failure)));
        worker.join();
        for (Runnable task = queued.poll(); task != null; task = queued.poll()) task.run();
        assertThat(incidents.list()).singleElement().satisfies(incident -> {
            assertThat(incident.count()).isEqualTo(50);
            assertThat(incident.thread()).isEqualTo("jasper-test-burst");
        });
        assertThat(center.history()).singleElement().satisfies(entry -> assertThat(entry.count()).isEqualTo(50));
        assertThat(cards()).isEqualTo(1);
    }

    @Test void theTraceIsTakenWhenTheFailureIsCaughtNotWhenItIsRecorded() {
        var queued = new ArrayDeque<Runnable>();
        ui = queued::add;
        var notices = notices();
        var failure = new IllegalStateException("as caught");
        notices.jasperFailure("thread worker", failure);
        failure.setStackTrace(new StackTraceElement[0]);
        failure.addSuppressed(new IllegalStateException("added later"));
        assertThat(incidents.list()).as("recording waits for the EDT").isEmpty();
        queued.remove().run();
        assertThat(incidents.list().getFirst().trace())
            .contains("at dev.jasper.app.application.IncidentNoticesTest").doesNotContain("added later");
    }

    @Test void aClearedIncidentExpiresItsNotificationAndTheNextFailurePostsANewOne() {
        var notices = notices();
        List<IllegalStateException> twice = sameSite(2);
        notices.jasperFailure("thread worker", twice.get(0));
        UUID first = center.history().getFirst().id();
        incidents.clear();
        assertThat(center.find(first).orElseThrow().expired()).isTrue();
        notices.jasperFailure("thread worker", twice.get(1));
        assertThat(center.history()).hasSize(2);
        assertThat(center.history().getFirst()).satisfies(entry -> {
            assertThat(entry.id()).isNotEqualTo(first);
            assertThat(entry.expired()).isFalse();
            assertThat(entry.count()).isEqualTo(1);
        });
    }

    @Test void anIncidentDroppedForNewerOnesExpiresItsNotification() {
        var notices = notices();
        notices.jasperFailure("thread worker", new IllegalStateException("oldest"));
        UUID oldest = center.history().getFirst().id();
        for (int i = 0; i < Incidents.LIMIT; i++) incidents.record("jasper", "filler", "t", new Capture("x.Filler" + i, "", "x.Filler" + i, ""));
        assertThat(center.find(oldest).orElseThrow().expired()).isTrue();
    }

    @Test void aRepeatAfterItsNotificationWasRemovedPostsANewOne() {
        var notices = notices();
        List<IllegalStateException> twice = sameSite(2);
        notices.jasperFailure("thread worker", twice.get(0));
        center.remove(center.history().getFirst().id());
        notices.jasperFailure("thread worker", twice.get(1));
        assertThat(center.history()).singleElement()
            .satisfies(entry -> assertThat(entry.request().content()).isEqualTo("IllegalStateException: broken 0"));
        assertThat(incidents.list().getFirst().count()).isEqualTo(2);
    }

    @Test void aFailureOfTheNotificationSystemItselfIsOnlyLogged() {
        var notices = notices();
        notices.jasperFailure("thread AWT-EventQueue-0", thrownIn("dev.jasper.app.notificationui.CardLayer"));
        notices.jasperFailure("thread AWT-EventQueue-0", thrownIn("dev.jasper.app.notifications.NotificationCenter"));
        notices.jasperFailure("thread AWT-EventQueue-0", thrownIn("dev.jasper.app.application.IncidentNotices"));
        notices.jasperFailure("thread AWT-EventQueue-0", thrownIn("dev.jasper.app.application.ErrorsWindow$1"));
        notices.jasperFailure("a notification action",
            new IllegalStateException("wrapped", thrownIn("dev.jasper.app.application.NotificationsFeature")));
        assertThat(incidents.list()).isEmpty();
        assertThat(center.history()).isEmpty();
        notices.jasperFailure("thread AWT-EventQueue-0",
            thrownIn("dev.jasper.app.workspace.WindowContent", "dev.jasper.app.notifications.NotificationCenter"));
        notices.jasperFailure("thread AWT-EventQueue-0", thrownIn("dev.jasper.app.application.IncidentNoticesTest"));
        assertThat(incidents.list()).as("the first Jasper frame decides, and only the named classes count").hasSize(2);
    }

    @Test void aFailureRaisedWhileAnIncidentIsAnnouncedIsNotRecordedAgain() {
        var notices = notices();
        center.onChange(change -> notices.jasperFailure("nested", new IllegalStateException("raised while announcing")));
        notices.jasperFailure("thread worker", new IllegalStateException("outer"));
        assertThat(incidents.list()).singleElement().satisfies(incident -> assertThat(incident.message()).isEqualTo("outer"));
    }

    @Test void aFailureThatCannotDescribeItselfIsStillAnIncident() {
        var notices = notices();
        notices.pluginFailure("dev.jasper.remote", "background task", new Unreadable());
        assertThat(incidents.list().getFirst().trace()).isEqualTo(Unreadable.class.getName());
        assertThat(center.history().getFirst().request().content()).isEqualTo("IncidentNoticesTest$Unreadable");
    }

    @Test void aShellThatCannotStartIsAnIncidentAnnouncedInItsWindow() {
        var notices = notices();
        UUID window = UUID.randomUUID();
        notices.launchFailure(window, "Could not start terminal in /tmp:\nshell executable unavailable",
            new IllegalStateException("shell executable unavailable"));
        Incident incident = incidents.list().getFirst();
        assertThat(incident.source()).isEqualTo("jasper");
        assertThat(incident.operation()).isEqualTo("starting a shell");
        Notification notice = center.history().getFirst();
        assertThat(notice.request().group()).isEqualTo("jasper.general");
        assertThat(notice.request().title()).isEqualTo("Could not start the terminal");
        assertThat(notice.request().content()).isEqualTo("Could not start terminal in /tmp:\nshell executable unavailable");
        assertThat(notice.request().target()).contains(window);
        assertThat(labels(notice)).containsExactly("Show more…");
    }

    @Test void nothingIsRecordedAfterClose() {
        var notices = notices();
        notices.close();
        notices.jasperFailure("thread worker", new IllegalStateException("late"));
        notices.launchFailure(UUID.randomUUID(), "details", new IllegalStateException("late"));
        assertThat(incidents.list()).isEmpty();
        assertThat(center.history()).isEmpty();
    }
}
```

- [ ] **Step 2: Run them to see them fail**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.application.IncidentNoticesTest'`
Expected: compilation fails: `IncidentNotices` does not exist.

- [ ] **Step 3: Implement**

Create `jasper-app/src/main/java/dev/jasper/app/application/IncidentNotices.java`:

```java
package dev.jasper.app.application;

import dev.jasper.app.lifecycle.Subscription;
import dev.jasper.app.notifications.Capture;
import dev.jasper.app.notifications.DisplayMode;
import dev.jasper.app.notifications.Incident;
import dev.jasper.app.notifications.Incidents;
import dev.jasper.app.notifications.NotificationAction;
import dev.jasper.app.notifications.NotificationCenter;
import dev.jasper.app.notifications.NotificationGroup;
import dev.jasper.app.notifications.NotificationRequest;
import dev.jasper.app.notifications.Severity;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Failures as incidents (spec sections 5 and 7): Jasper's own (the uncaught handler, a notification action), a
 * plugin's (a contained callback) and a shell that could not start. Each is captured on the thread that caught it,
 * recorded on the EDT and announced by one ERROR notification. A repeat updates that notification and shows its card
 * again at most once a minute. A failure of the notification system itself is left to the log, and capture never
 * throws or re-enters. The three failure entry points are safe from any thread; everything else is EDT only.
 */
final class IncidentNotices implements AutoCloseable {
    static final NotificationGroup PLUGINS = new NotificationGroup("jasper.plugins", "Plugin problems", NotificationRequest.JASPER, DisplayMode.CARD);
    static final NotificationGroup ERRORS = new NotificationGroup("jasper.errors", "Internal errors", NotificationRequest.JASPER, DisplayMode.CARD);
    /** A repeating incident shows its card again at most this often. */
    static final Duration CARD_INTERVAL = Duration.ofMinutes(1);
    static final String SHOW_MORE = "Show more…";
    static final String DISABLE_PLUGIN = "Disable Plugin";
    /** What Jasper was doing when a shell could not start. */
    static final String LAUNCH = "starting a shell";
    static final String LAUNCH_TITLE = "Could not start the terminal";
    private static final System.Logger LOG = System.getLogger(IncidentNotices.class.getName());
    /** The notification system: a failure whose first Jasper frame is in one of these is only logged. */
    private static final List<String> OWN_PACKAGES = List.of("dev.jasper.app.notifications.", "dev.jasper.app.notificationui.");
    private static final List<String> OWN_CLASSES = List.of(IncidentNotices.class.getName(), NotificationsFeature.class.getName(),
        ErrorsWindow.class.getName(), ConfigProblemNotices.class.getName());

    private final NotificationCenter center;
    private final Incidents incidents;
    private final Executor ui;
    private final Function<String, String> pluginName;
    private final BiConsumer<String, Consumer<Optional<String>>> disable;
    private final Consumer<UUID> openErrors;
    private final ThreadLocal<Boolean> capturing = new ThreadLocal<>();
    /** Each incident's notification. */
    private final Map<UUID, UUID> notices = new HashMap<>();
    /** When each incident's card last showed. */
    private final Map<UUID, Instant> carded = new HashMap<>();
    private final Subscription centerChanges;
    private final Subscription incidentChanges;
    private volatile boolean closed;

    /**
     * @param ui runs recording on the EDT ({@code SwingUtilities::invokeLater} in the application)
     * @param pluginName a plugin's display name by id
     * @param disable disables a plugin from the next launch, then reports on the EDT: empty, or why it could not
     * @param openErrors opens the Errors window at an incident
     */
    IncidentNotices(NotificationCenter center, Incidents incidents, Executor ui, Function<String, String> pluginName,
                    BiConsumer<String, Consumer<Optional<String>>> disable, Consumer<UUID> openErrors) {
        this.center = Objects.requireNonNull(center, "center");
        this.incidents = Objects.requireNonNull(incidents, "incidents");
        this.ui = Objects.requireNonNull(ui, "ui");
        this.pluginName = Objects.requireNonNull(pluginName, "pluginName");
        this.disable = Objects.requireNonNull(disable, "disable");
        this.openErrors = Objects.requireNonNull(openErrors, "openErrors");
        center.register(PLUGINS);
        center.register(ERRORS);
        centerChanges = center.onChange(this::centerChanged);
        incidentChanges = incidents.onChange(this::incidentChanged);
    }

    /** A failure of Jasper's own; {@code operation} says what it was doing, such as "thread main". Any thread. */
    void jasperFailure(String operation, Throwable failure) {
        capture(NotificationRequest.JASPER, operation, failure, this::announcement);
    }

    /** A plugin callback failed while the host asked it to do {@code operation}. Any thread. */
    void pluginFailure(String pluginId, String operation, Throwable failure) {
        capture(pluginId, operation, failure, this::announcement);
    }

    /**
     * A shell could not start in {@code window}: an incident, announced in that window as a Jasper notification whose
     * content is {@code details}, the directory and the cause (spec section 7, producer 1). Any thread.
     */
    void launchFailure(UUID window, String details, Throwable failure) {
        if (closed) return;
        String text = details == null ? "" : details;
        NotificationRequest plain = NotificationRequest.of(NotificationsFeature.GENERAL.id(), Severity.ERROR, LAUNCH_TITLE)
            .withContent(text).withTarget(window);
        if (failure == null) {
            center.submit(plain);
            return;
        }
        capture(NotificationRequest.JASPER, LAUNCH, failure, incident -> plain.withAction(showMore(incident)));
    }

    /** Disable Plugin on an incident: the plugin is disabled from the next launch and the outcome is posted. EDT. */
    void disablePlugin(String pluginId) {
        if (closed) return;
        String name = nameOf(pluginId);
        disable.accept(pluginId, problem -> center.submit(problem.isEmpty()
            ? NotificationRequest.of(PLUGINS.id(), Severity.INFO, name + " will be disabled when Jasper restarts")
            : NotificationRequest.of(PLUGINS.id(), Severity.ERROR, "Could not disable " + name).withContent(problem.get())));
    }

    @Override public void close() {
        if (closed) return;
        closed = true;
        centerChanges.close();
        incidentChanges.close();
    }

    /** "Remote failed to start", "Remote failed to stop", otherwise "Remote failed in panel remote.sftp". */
    static String pluginTitle(String name, String operation) {
        return switch (operation) {
            case "start" -> name + " failed to start";
            case "stop" -> name + " failed to stop";
            default -> name + " failed in " + operation;
        };
    }

    /**
     * Whether the notification system itself failed: the first {@code dev.jasper.} frame of the failure, or of any
     * cause, lies in it. Whoever caught such a failure has logged it; recording it could fail the same way again.
     */
    static boolean ownFailure(Throwable failure) {
        Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Throwable next = failure; next != null && seen.add(next); next = next.getCause()) {
            for (StackTraceElement frame : next.getStackTrace()) {
                String type = frame.getClassName();
                if (!type.startsWith("dev.jasper.")) continue;
                if (own(type)) return true;
                break;
            }
        }
        return false;
    }

    private static boolean own(String type) {
        for (String name : OWN_PACKAGES) if (type.startsWith(name)) return true;
        for (String name : OWN_CLASSES) if (type.equals(name) || type.startsWith(name + "$")) return true;
        return false;
    }

    /** Takes the capture here, on the thread that caught the failure, then records it on the EDT. Never throws. */
    private void capture(String source, String operation, Throwable failure, Function<Incident, NotificationRequest> announce) {
        if (closed || failure == null || Boolean.TRUE.equals(capturing.get())) return;
        Capture capture;
        String thread;
        capturing.set(Boolean.TRUE);
        try {
            if (ownFailure(failure)) return;
            capture = Capture.of(failure);
            thread = Thread.currentThread().getName();
        } catch (RuntimeException | Error unrecorded) {
            LOG.log(System.Logger.Level.WARNING, "A failure could not be recorded as an incident", unrecorded);
            return;
        } finally {
            capturing.remove();
        }
        try { ui.execute(() -> record(source, operation, thread, capture, announce)); }
        catch (RuntimeException rejected) {
            LOG.log(System.Logger.Level.WARNING, "A failure could not be recorded as an incident", rejected);
        }
    }

    private void record(String source, String operation, String thread, Capture capture,
                        Function<Incident, NotificationRequest> announce) {
        if (closed || Boolean.TRUE.equals(capturing.get())) return;
        capturing.set(Boolean.TRUE);
        try {
            Incident incident = incidents.record(source, operation, thread, capture);
            Instant now = center.clock().instant();
            UUID notice = notices.get(incident.id());
            if (notice != null) {
                Instant shown = carded.get(incident.id());
                boolean card = shown == null || !now.isBefore(shown.plus(CARD_INTERVAL));
                if (center.repeat(notice, card)) {
                    if (card) carded.put(incident.id(), now);
                    return;
                }
            }
            center.post(announce.apply(incident)).ifPresent(id -> {
                notices.put(incident.id(), id);
                carded.put(incident.id(), now);
            });
        } catch (RuntimeException failure) {
            LOG.log(System.Logger.Level.WARNING, "An incident could not be announced", failure);
        } finally {
            capturing.remove();
        }
    }

    /** An internal error, or a plugin problem with Disable Plugin. */
    private NotificationRequest announcement(Incident incident) {
        if (!incident.fromPlugin())
            return NotificationRequest.of(ERRORS.id(), Severity.ERROR, "Unexpected error in Jasper")
                .withContent(incident.summary()).withAction(showMore(incident));
        String plugin = incident.source();
        return NotificationRequest.of(PLUGINS.id(), Severity.ERROR, pluginTitle(nameOf(plugin), incident.operation()))
            .withContent(incident.summary())
            .withAction(showMore(incident))
            .withAction(new NotificationAction(DISABLE_PLUGIN, () -> disablePlugin(plugin), false));
    }

    private NotificationAction showMore(Incident incident) {
        UUID id = incident.id();
        return new NotificationAction(SHOW_MORE, () -> openErrors.accept(id), false);
    }

    private String nameOf(String pluginId) {
        return Objects.requireNonNullElse(pluginName.apply(pluginId), pluginId);
    }

    /** The user removed an incident's notification, or cleared them all: a repeat posts a new one. */
    private void centerChanged(NotificationCenter.Change change) {
        if (change instanceof NotificationCenter.Change.Removed removed) {
            for (Iterator<Map.Entry<UUID, UUID>> links = notices.entrySet().iterator(); links.hasNext(); ) {
                Map.Entry<UUID, UUID> link = links.next();
                if (!link.getValue().equals(removed.id())) continue;
                carded.remove(link.getKey());
                links.remove();
            }
        } else if (change instanceof NotificationCenter.Change.Cleared) {
            notices.clear();
            carded.clear();
        }
    }

    /** An incident was cleared or dropped: its notification expires, so its links stop working. */
    private void incidentChanged(Incidents.Change change) {
        if (change instanceof Incidents.Change.Removed removed) {
            carded.remove(removed.id());
            UUID notice = notices.remove(removed.id());
            if (notice != null) center.expire(notice);
        } else if (change instanceof Incidents.Change.Cleared) {
            List<UUID> expiring = List.copyOf(notices.values());
            notices.clear();
            carded.clear();
            expiring.forEach(center::expire);
        }
    }
}
```

- [ ] **Step 4: Run the tests to see them pass**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.application.IncidentNoticesTest'`
Expected: PASS (15 tests).

- [ ] **Step 5: Commit**

```bash
git add jasper-app/src/main/java/dev/jasper/app/application/IncidentNotices.java jasper-app/src/test/java/dev/jasper/app/application/IncidentNoticesTest.java
git commit -m "feat(notifications): announce incidents as notifications" -m "Jasper, plugin and shell launch failures are captured on the catching thread, recorded on the EDT and announced once; repeats update the same notification with a card at most once a minute, and the notification system's own failures are only logged." -m "🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 5: Capture hooks

**Files:**
- Modify: `jasper-app/src/main/java/dev/jasper/app/bootstrap/ApplicationBootstrap.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/plugins/Containment.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/plugins/PluginHost.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/plugins/PluginRuntime.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/workspace/TerminalPane.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/workspace/TerminalTab.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/workspace/WindowContent.java`
- Test: `jasper-app/src/test/java/dev/jasper/app/bootstrap/UnexpectedExceptionsTest.java`
- Test: `jasper-app/src/test/java/dev/jasper/app/plugins/ContainmentTest.java`
- Test: `jasper-app/src/test/java/dev/jasper/app/plugins/PluginRuntimeTest.java`
- Test: `jasper-app/src/test/java/dev/jasper/app/workspace/TerminalTabTest.java`

**Interfaces:**
- Consumes: nothing from earlier tasks. These are the producers' hooks; Task 6 connects them.
- Produces:
  - `ApplicationBootstrap.UnexpectedExceptions.forwardTo(BiConsumer<Thread, Throwable> sink)` (package-private) and its
    package-private field `installed`.
  - `public record PluginRuntime.Failure(String pluginId, String operation, Throwable cause)`,
    `public void PluginRuntime.reportFailuresTo(Consumer<PluginRuntime.Failure> sink)` (any thread),
    `public Map<String, String> PluginRuntime.pluginVersions()`.
  - `Containment(BooleanSupplier onUi, Consumer<PluginRuntime.Failure> reported)`, and the one-argument constructor
    kept; `PluginHost.Environment` gains an 18th component `Consumer<PluginRuntime.Failure> failures`, with its 17- and
    15-argument constructors reporting nowhere.
  - `public BiConsumer<String, Throwable> WindowContent.onLaunchFailure` (default: `onError("Could not start the
    terminal", details)`); `TerminalPane.onFailure` and `TerminalTab.onError` become `BiConsumer<String, Throwable>`.

- [ ] **Step 1: Write the failing tests**

Create `jasper-app/src/test/java/dev/jasper/app/bootstrap/UnexpectedExceptionsTest.java`:

```java
package dev.jasper.app.bootstrap;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class UnexpectedExceptionsTest {
    @Test void anUncaughtFailureIsForwardedWithItsThread() throws Exception {
        Thread.UncaughtExceptionHandler original = Thread.getDefaultUncaughtExceptionHandler();
        List<String> forwarded = new CopyOnWriteArrayList<>();
        try (var exceptions = ApplicationBootstrap.installUnexpectedExceptionHandler()) {
            exceptions.forwardTo((thread, failure) -> forwarded.add(thread.getName() + ": " + failure.getMessage()));
            Thread failing = Thread.ofPlatform().name("jasper-test-failing").unstarted(() -> { throw new IllegalStateException("thread broke"); });
            failing.start();
            failing.join();
        } finally {
            Thread.setDefaultUncaughtExceptionHandler(original);
        }
        assertThat(forwarded).containsExactly("jasper-test-failing: thread broke");
    }

    @Test void aForwardThatFailsIsContained() {
        Thread.UncaughtExceptionHandler original = Thread.getDefaultUncaughtExceptionHandler();
        try (var exceptions = ApplicationBootstrap.installUnexpectedExceptionHandler()) {
            exceptions.forwardTo((thread, failure) -> { throw new IllegalStateException("recording broke"); });
            assertThatCode(() -> exceptions.installed.uncaughtException(Thread.currentThread(), new IllegalStateException("x")))
                .doesNotThrowAnyException();
        } finally {
            Thread.setDefaultUncaughtExceptionHandler(original);
        }
    }

    @Test void nothingIsForwardedAfterClose() {
        Thread.UncaughtExceptionHandler original = Thread.getDefaultUncaughtExceptionHandler();
        List<Throwable> forwarded = new CopyOnWriteArrayList<>();
        try {
            var exceptions = ApplicationBootstrap.installUnexpectedExceptionHandler();
            exceptions.forwardTo((thread, failure) -> forwarded.add(failure));
            exceptions.close();
            exceptions.installed.uncaughtException(Thread.currentThread(), new IllegalStateException("late"));
        } finally {
            Thread.setDefaultUncaughtExceptionHandler(original);
        }
        assertThat(forwarded).isEmpty();
    }
}
```

Create `jasper-app/src/test/java/dev/jasper/app/plugins/ContainmentTest.java`:

```java
package dev.jasper.app.plugins;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class ContainmentTest {
    private final List<PluginRuntime.Failure> reported = new ArrayList<>();

    @Test void aContainedFailureIsCountedAndReportedWithWhatThePluginWasDoing() {
        var containment = new Containment(() -> false, reported::add);
        var failure = new IllegalStateException("plugin broke");
        assertThat(containment.attempt("dev.example.tool", "action tool.run", () -> { throw failure; })).isSameAs(failure);
        assertThat(containment.failures("dev.example.tool")).isEqualTo(1);
        assertThat(reported).containsExactly(new PluginRuntime.Failure("dev.example.tool", "action tool.run", failure));
    }

    @Test void aLinkageErrorIsReportedToo() {
        var containment = new Containment(() -> false, reported::add);
        var failure = new NoClassDefFoundError("dev/example/Missing");
        assertThat(containment.run("dev.example.tool", "panel tool.files", () -> { throw failure; })).isFalse();
        assertThat(reported).extracting(PluginRuntime.Failure::cause).containsExactly(failure);
    }

    @Test void aCallThatSucceedsReportsNothing() {
        var containment = new Containment(() -> false, reported::add);
        assertThat(containment.run("dev.example.tool", "stop", () -> { })).isTrue();
        assertThat(reported).isEmpty();
    }

    @Test void aReportThatFailsIsContained() {
        var containment = new Containment(() -> false, failure -> { throw new IllegalStateException("sink broke"); });
        assertThatCode(() -> containment.run("dev.example.tool", "stop", () -> { throw new IllegalStateException("x"); }))
            .doesNotThrowAnyException();
        assertThat(containment.failures("dev.example.tool")).isEqualTo(1);
    }
}
```

Add to `PluginRuntimeTest` (it already imports `PluginJars`, `Map`, `List`, `ArrayList`, `Path` and statically imports
`AppContractTest.onEdt`): the import `java.util.concurrent.CopyOnWriteArrayList`, this fixture beside `FIXTURE`, and
this test:

```java
    static final String BOOM = """
        package fix.boom;
        public final class Main implements dev.jasper.sdk.plugin.Plugin {
            @Override public void start(dev.jasper.sdk.plugin.PluginContext context) { throw new IllegalStateException("boom at start"); }
        }
        """;

    @Test void aPluginFailureIsReportedWithThePluginAndWhatItWasDoing() throws Exception {
        Path bundled = root.resolve("bundled");
        PluginJars.build(bundled.resolve("dev.example.boom"), "boom.jar", PluginJars.descriptor("dev.example.boom", "1.2.3", "fix.boom.Main"),
            Map.of("fix.boom.Main", BOOM), List.of());
        PluginRuntime runtime = runtime(bundled, root.resolve("absent"), null, false, new ArrayList<>());
        var failures = new CopyOnWriteArrayList<PluginRuntime.Failure>();
        runtime.reportFailuresTo(failures::add);
        onEdt(() -> runtime.start(Map.of(), true));
        assertThat(failures).singleElement().satisfies(failure -> {
            assertThat(failure.pluginId()).isEqualTo("dev.example.boom");
            assertThat(failure.operation()).isEqualTo("start");
            assertThat(failure.cause()).isInstanceOf(IllegalStateException.class).hasMessage("boom at start");
        });
        assertThat(runtime.pluginVersions()).containsEntry("dev.example.boom", "1.2.3");
        onEdt(runtime::stop);
    }
```

Add to `TerminalTabTest` (it imports `java.util.*`, `ShellLauncher`, `ActionId` and `DesktopTestSupport.*`):

```java
    @Test void aFailedLaunchReportsItsDetailsAndWhatWasThrown() throws Exception {
        Queue<Runnable> pending = new ArrayDeque<>();
        var thrown = new IllegalStateException("shell executable unavailable");
        int[] launches = {0};
        ShellLauncher launcher = new ShellLauncher(pending::add, path -> {
            if (++launches[0] > 1) throw thrown;
            return shell(path);
        }, "sh");
        WindowContent[] owner = new WindowContent[1];
        List<String> details = new ArrayList<>();
        List<Throwable> causes = new ArrayList<>();
        edt(() -> {
            owner[0] = content(launcher);
            owner[0].onLaunchFailure = (text, cause) -> { details.add(text); causes.add(cause); };
        });
        pending.remove().run();
        edt(() -> owner[0].invoke(ActionId.SPLIT_DOWN));
        pending.remove().run();
        edt(() -> {
            assertThat(details).singleElement().asString().contains("shell executable unavailable");
            assertThat(causes).containsExactly(thrown);
            owner[0].close();
        });
    }
```

- [ ] **Step 2: Run them to see them fail**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.bootstrap.UnexpectedExceptionsTest' --tests 'dev.jasper.app.plugins.ContainmentTest' --tests 'dev.jasper.app.plugins.PluginRuntimeTest' --tests 'dev.jasper.app.workspace.TerminalTabTest'`
Expected: compilation fails: `forwardTo`, `installed`, `PluginRuntime.Failure`, the two-argument `Containment`,
`reportFailuresTo`, `pluginVersions` and `onLaunchFailure` do not exist.

- [ ] **Step 3: Forward uncaught failures in the bootstrap**

In `ApplicationBootstrap.java`, add the imports `java.util.Objects` and `java.util.function.BiConsumer`, then replace
`installUnexpectedExceptionHandler()` and the whole `UnexpectedExceptions` class with:

```java
    static UnexpectedExceptions installUnexpectedExceptionHandler() {
        synchronized (ApplicationBootstrap.class) {
            var exceptions = new UnexpectedExceptions(Thread.getDefaultUncaughtExceptionHandler());
            Thread.setDefaultUncaughtExceptionHandler(exceptions.installed);
            return exceptions;
        }
    }
```

```java
    static final class UnexpectedExceptions implements AutoCloseable {
        private final Thread.UncaughtExceptionHandler previous;
        /** The handler this installed; it logs each failure, then forwards it. */
        final Thread.UncaughtExceptionHandler installed = this::uncaught;
        private volatile BiConsumer<Thread, Throwable> forward = (thread, failure) -> { };
        private boolean closed;

        private UnexpectedExceptions(Thread.UncaughtExceptionHandler previous) { this.previous = previous; }

        /** Where each failure goes after it is logged: the application records it as an incident. Any thread. */
        void forwardTo(BiConsumer<Thread, Throwable> sink) { forward = Objects.requireNonNull(sink, "sink"); }

        private void uncaught(Thread thread, Throwable failure) {
            LOG.log(System.Logger.Level.ERROR, "Unexpected application failure", failure);
            try { forward.accept(thread, failure); }
            catch (RuntimeException | Error unrecorded) {
                // Never back into this handler: a failure while recording is only logged.
                LOG.log(System.Logger.Level.ERROR, "An unexpected failure could not be recorded", unrecorded);
            }
        }

        @Override public void close() {
            synchronized (ApplicationBootstrap.class) {
                if (closed) return;
                closed = true;
                forward = (thread, failure) -> { };
                if (Thread.getDefaultUncaughtExceptionHandler() == installed) {
                    Thread.setDefaultUncaughtExceptionHandler(previous);
                }
            }
        }
    }
```

- [ ] **Step 4: Report contained plugin failures**

Replace `jasper-app/src/main/java/dev/jasper/app/plugins/Containment.java` with:

```java
package dev.jasper.app.plugins;

import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Every call from the application into plugin code goes through here: {@link Exception} and
 * {@link LinkageError} are caught, logged against the plugin, counted and reported, so the
 * application records an incident; other errors propagate. While a callback runs on the UI thread
 * it is remembered, so an exit deadline that fires can say whose callback blocked. Safe from any thread.
 */
final class Containment {
    private static final System.Logger LOG = System.getLogger(Containment.class.getName());
    private static final long SLOW_NANOS = TimeUnit.MILLISECONDS.toNanos(100);
    private final ConcurrentHashMap<String, AtomicInteger> failures = new ConcurrentHashMap<>();
    private final BooleanSupplier onUi;
    private final Consumer<PluginRuntime.Failure> reported;
    private volatile String executing;

    Containment(BooleanSupplier onUi) { this(onUi, failure -> { }); }

    /** {@code reported} hears each failure after it is logged, on the thread it happened on. */
    Containment(BooleanSupplier onUi, Consumer<PluginRuntime.Failure> reported) {
        this.onUi = onUi;
        this.reported = Objects.requireNonNull(reported, "reported");
    }

    /** Returns the contained failure, or null when the action completed. */
    Throwable attempt(String pluginId, String what, Callable<?> action) {
        boolean ui = onUi.getAsBoolean();
        String previous = executing;
        if (ui) executing = pluginId + " (" + what + ")";
        long began = System.nanoTime();
        try {
            action.call();
            return null;
        } catch (Exception | LinkageError failure) {
            record(pluginId, what, failure);
            return failure;
        } finally {
            if (ui) executing = previous;
            long took = System.nanoTime() - began;
            if (ui && took > SLOW_NANOS)
                logger(pluginId).log(System.Logger.Level.INFO, "Slow plugin callback on the UI thread: " + what
                    + " took " + TimeUnit.NANOSECONDS.toMillis(took) + " ms");
        }
    }

    boolean run(String pluginId, String what, Runnable action) {
        return attempt(pluginId, what, () -> { action.run(); return null; }) == null;
    }

    void record(String pluginId, String what, Throwable failure) {
        failures.computeIfAbsent(pluginId, id -> new AtomicInteger()).incrementAndGet();
        logger(pluginId).log(System.Logger.Level.WARNING, "Plugin " + pluginId + " failed in " + what, failure);
        try { reported.accept(new PluginRuntime.Failure(pluginId, what, failure)); }
        catch (RuntimeException unreported) { LOG.log(System.Logger.Level.WARNING, "A plugin failure could not be reported", unreported); }
    }

    int failures(String pluginId) {
        AtomicInteger count = failures.get(pluginId);
        return count == null ? 0 : count.get();
    }

    String executing() { return executing; }

    static System.Logger logger(String pluginId) { return System.getLogger("dev.jasper.plugins." + pluginId); }
}
```

In `PluginHost.java`, replace the `Environment` record (lines 29–46) with:

```java
    record Environment(Consumer<Runnable> ui, BooleanSupplier onUi, Function<String, Path> dataDirectory,
                       Consumer<dev.jasper.app.config.ConfigDiagnostic> configReport,
                       Duration drainGrace, Contributions contributions, BooleanSupplier dark,
                       AuxiliaryWindows windows, TerminalRegistry terminals, Consumer<String> notice, Consumer<Path> editor,
                       PluginSettingsFiles settingsFiles, Consumer<Path> clearReports, PluginRuntime.ShellProfiles profiles,
                       Consumer<java.net.URI> browser,
                       java.util.function.Supplier<dev.jasper.app.appearance.ResolvedTheme> appearance,
                       java.util.function.Function<Consumer<dev.jasper.app.appearance.ResolvedTheme>, dev.jasper.app.lifecycle.Subscription> appearanceSubscriber,
                       Consumer<PluginRuntime.Failure> failures) {
        /** Contained failures are only logged and counted. */
        Environment(Consumer<Runnable> ui, BooleanSupplier onUi, Function<String, Path> dataDirectory,
                       Consumer<dev.jasper.app.config.ConfigDiagnostic> configReport,
                       Duration drainGrace, Contributions contributions, BooleanSupplier dark,
                       AuxiliaryWindows windows, TerminalRegistry terminals, Consumer<String> notice, Consumer<Path> editor,
                       PluginSettingsFiles settingsFiles, Consumer<Path> clearReports, PluginRuntime.ShellProfiles profiles,
                       Consumer<java.net.URI> browser,
                       java.util.function.Supplier<dev.jasper.app.appearance.ResolvedTheme> appearance,
                       java.util.function.Function<Consumer<dev.jasper.app.appearance.ResolvedTheme>, dev.jasper.app.lifecycle.Subscription> appearanceSubscriber) {
            this(ui, onUi, dataDirectory, configReport, drainGrace, contributions, dark, windows, terminals, notice,
                editor, settingsFiles, clearReports, profiles, browser, appearance, appearanceSubscriber, failure -> { });
        }

        Environment(Consumer<Runnable> ui, BooleanSupplier onUi, Function<String, Path> dataDirectory,
                       Consumer<dev.jasper.app.config.ConfigDiagnostic> configReport,
                       Duration drainGrace, Contributions contributions, BooleanSupplier dark,
                       AuxiliaryWindows windows, TerminalRegistry terminals, Consumer<String> notice, Consumer<Path> editor,
                       PluginSettingsFiles settingsFiles, Consumer<Path> clearReports, PluginRuntime.ShellProfiles profiles,
                       Consumer<java.net.URI> browser) {
            this(ui, onUi, dataDirectory, configReport, drainGrace, contributions, dark, windows, terminals, notice,
                editor, settingsFiles, clearReports, profiles, browser, null, null, failure -> { });
        }
    }
```

and in the `PluginHost` constructor replace `this.containment = new Containment(environment.onUi());` with:

```java
        this.containment = new Containment(environment.onUi(), environment.failures());
```

In `PluginRuntime.java`:

1. After `public record Outcome(boolean ok, String message, Snapshot snapshot) { }` add:

```java

    /**
     * A plugin callback that failed and was contained.
     *
     * @param pluginId the plugin
     * @param operation what the host asked it to do, such as {@code start} or {@code panel <id>}
     * @param cause what it threw
     */
    public record Failure(String pluginId, String operation, Throwable cause) {
        public Failure {
            Objects.requireNonNull(pluginId, "pluginId");
            Objects.requireNonNull(operation, "operation");
            Objects.requireNonNull(cause, "cause");
        }
    }
```

2. After `private volatile Map<String, PluginCandidate> launched = Map.of();` add:

```java
    private volatile Consumer<Failure> failureSink = failure -> { };
```

3. In `start`, change the end of the `PluginHost.Environment` construction from

```java
            dev.jasper.app.platform.Browser::open, appearance, appearanceSubscriber));
```

to

```java
            dev.jasper.app.platform.Browser::open, appearance, appearanceSubscriber, failure -> failureSink.accept(failure)));
```

4. After `pluginNames()` add:

```java
    /**
     * Where contained plugin failures go after they are logged; the application records each as an incident.
     *
     * @param sink hears each failure on the thread it happened on
     */
    public void reportFailuresTo(Consumer<Failure> sink) { failureSink = Objects.requireNonNull(sink, "sink"); }

    /**
     * Discovered plugin versions, without performing file I/O.
     *
     * @return each plugin's version by id
     */
    public Map<String, String> pluginVersions() {
        var versions = new java.util.LinkedHashMap<String, String>();
        launched.forEach((id, candidate) -> versions.put(id, candidate.descriptor().version().toString()));
        return Map.copyOf(versions);
    }
```

(`PluginRuntime` already imports `Objects`, `Consumer` and `Map`.)

- [ ] **Step 5: Carry the launch failure's throwable to the window**

In `TerminalPane.java`:

- replace `Consumer<String> onFailure = message -> {};` with
  `BiConsumer<String, Throwable> onFailure = (message, cause) -> {};`
- replace
  `onFailure.accept("Could not start terminal in " + launchDirectory + ":\n" + cause.getMessage());` with
  `onFailure.accept("Could not start terminal in " + launchDirectory + ":\n" + cause.getMessage(), failure);`
- in the method that resets callbacks, replace `onReady = terminal -> {}; onFailure = message -> {};` with
  `onReady = terminal -> {}; onFailure = (message, cause) -> {};`

In `TerminalTab.java`:

- replace `Consumer<String> onError = message -> {};` with
  `java.util.function.BiConsumer<String, Throwable> onError = (message, cause) -> {};`
- replace the pane failure callback

```java
        pane.onFailure = message -> {
            onError.accept(message);
```

with

```java
        pane.onFailure = (message, cause) -> {
            onError.accept(message, cause);
```

- in `close()`, replace `onPaneCreated = pane -> {}; onError = message -> {};` with
  `onPaneCreated = pane -> {}; onError = (message, cause) -> {};`

In `WindowContent.java`:

- after the `onInfo` field add:

```java
    /**
     * A shell could not start in this window: the directory and cause to show, and what was thrown. By default an
     * error with a fixed title; the application records an incident instead.
     */
    public java.util.function.BiConsumer<String, Throwable> onLaunchFailure =
        (details, cause) -> onError.accept("Could not start the terminal", details);
```

- replace `tab.onError = message -> onError.accept("Could not start the terminal", message);` with
  `tab.onError = (message, cause) -> onLaunchFailure.accept(message, cause);`
- in `close()`, replace `onError = (title, details) -> {}; onInfo = (title, details) -> {};` with
  `onError = (title, details) -> {}; onInfo = (title, details) -> {}; onLaunchFailure = (details, cause) -> {};`

- [ ] **Step 6: Run the tests to see them pass**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.bootstrap.*' --tests 'dev.jasper.app.plugins.*' --tests 'dev.jasper.app.workspace.*' --tests 'dev.jasper.app.platform.AppLogTest'`
Expected: PASS, including `TerminalTabTest.failedSplitShowsErrorAndKeepsExistingSession` (the default
`onLaunchFailure` still reaches `onError`) and `AppLogTest`'s handler tests.

- [ ] **Step 7: Commit**

```bash
git add jasper-app/src/main/java/dev/jasper/app/bootstrap/ApplicationBootstrap.java jasper-app/src/main/java/dev/jasper/app/plugins/Containment.java jasper-app/src/main/java/dev/jasper/app/plugins/PluginHost.java jasper-app/src/main/java/dev/jasper/app/plugins/PluginRuntime.java jasper-app/src/main/java/dev/jasper/app/workspace/TerminalPane.java jasper-app/src/main/java/dev/jasper/app/workspace/TerminalTab.java jasper-app/src/main/java/dev/jasper/app/workspace/WindowContent.java jasper-app/src/test/java/dev/jasper/app/bootstrap/UnexpectedExceptionsTest.java jasper-app/src/test/java/dev/jasper/app/plugins/ContainmentTest.java jasper-app/src/test/java/dev/jasper/app/plugins/PluginRuntimeTest.java jasper-app/src/test/java/dev/jasper/app/workspace/TerminalTabTest.java
git commit -m "feat(notifications): hand failures out of the uncaught handler, plugins and shell launch" -m "The uncaught handler forwards after logging, Containment reports each contained plugin failure, and a failed launch carries its throwable to WindowContent.onLaunchFailure." -m "🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 6: Application wiring

**Files:**
- Modify: `jasper-app/src/main/java/dev/jasper/app/application/JasperApplication.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/bootstrap/ApplicationBootstrap.java`
- Test: `jasper-app/src/test/java/dev/jasper/app/application/JasperApplicationIncidentsTest.java`

**Interfaces:**
- Consumes: Task 1 `Incidents`; Task 2 `reportActionFailuresTo`; Task 3 `ErrorsWindow`, `ErrorsView.Hooks`,
  `IncidentReport.Environment.current()`; Task 4 `IncidentNotices`; Task 5 `forwardTo`, `reportFailuresTo`,
  `pluginVersions`, `WindowContent.onLaunchFailure`; existing `PluginRuntime.setEnabled(String, boolean,
  Consumer<Outcome>)`, `ConfigEditor.reveal(Path)` (never on the EDT), `AppDirs.logs()`.
- Produces: `public void JasperApplication.unexpected(Thread thread, Throwable failure)` (any thread); package-private
  `NotificationCenter notificationCenter()` and `Incidents incidents()` for tests.

- [ ] **Step 1: Write the failing tests**

Create `jasper-app/src/test/java/dev/jasper/app/application/JasperApplicationIncidentsTest.java`:

```java
package dev.jasper.app.application;

import dev.jasper.app.commands.CommandHistory;
import dev.jasper.app.contributions.PanelBadge;
import dev.jasper.app.notifications.NotificationAction;
import dev.jasper.app.notifications.NotificationRequest;
import dev.jasper.app.notifications.Severity;
import dev.jasper.app.platform.AppDirs;
import dev.jasper.app.testsupport.PluginJars;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import static dev.jasper.app.workspace.DesktopTestSupport.edt;
import static dev.jasper.app.workspace.DesktopTestSupport.launcher;
import static org.assertj.core.api.Assertions.assertThat;

class JasperApplicationIncidentsTest {
    private static final String BOOM = """
        package fix.boom;
        public final class Main implements dev.jasper.sdk.plugin.Plugin {
            @Override public void start(dev.jasper.sdk.plugin.PluginContext context) { throw new IllegalStateException("boom at start"); }
        }
        """;

    @TempDir Path home;

    private static Optional<PanelBadge> badge(JasperApplication application) {
        return application.contributions().panels().stream()
            .filter(entry -> entry.id().equals(NotificationsFeature.PANEL_ID)).findFirst().orElseThrow().badge();
    }

    private static JasperApplication application(CountDownLatch terminated) {
        return new JasperApplication(null, launcher(new ArrayDeque<>()), new CommandHistory(), null, terminated::countDown);
    }

    @Test void anUncaughtFailureOnAnyThreadIsAnInternalError() throws Exception {
        var terminated = new CountDownLatch(1);
        JasperApplication[] application = new JasperApplication[1];
        edt(() -> application[0] = application(terminated));
        Thread worker = Thread.ofPlatform().name("jasper-test-worker")
            .start(() -> application[0].unexpected(Thread.currentThread(), new IllegalStateException("worker broke")));
        worker.join();
        edt(() -> {
            assertThat(application[0].incidents().list()).singleElement().satisfies(incident -> {
                assertThat(incident.source()).isEqualTo("jasper");
                assertThat(incident.operation()).isEqualTo("thread jasper-test-worker");
                assertThat(incident.thread()).isEqualTo("jasper-test-worker");
            });
            assertThat(application[0].notificationCenter().history()).singleElement().satisfies(entry -> {
                assertThat(entry.request().group()).isEqualTo("jasper.errors");
                assertThat(entry.request().title()).isEqualTo("Unexpected error in Jasper");
                assertThat(entry.request().content()).isEqualTo("IllegalStateException: worker broke");
            });
            assertThat(badge(application[0])).contains(new PanelBadge(1, PanelBadge.Tone.ERROR, "1 unread notification, 1 error"));
        });
        edt(application[0]::quit);
        assertThat(terminated.await(5, TimeUnit.SECONDS)).isTrue();
    }

    @Test void aNotificationActionThatThrowsIsAJasperIncident() throws Exception {
        var terminated = new CountDownLatch(1);
        JasperApplication[] application = new JasperApplication[1];
        edt(() -> {
            application[0] = application(terminated);
            var center = application[0].notificationCenter();
            UUID id = center.post(NotificationRequest.of(NotificationsFeature.GENERAL.id(), Severity.INFO, "Has a broken action")
                .withAction(NotificationAction.of("Break", () -> { throw new IllegalStateException("action broke"); }))).orElseThrow();
            center.runAction(id, 0);
        });
        edt(() -> assertThat(application[0].incidents().list()).singleElement().satisfies(incident -> {
            assertThat(incident.operation()).isEqualTo("a notification action");
            assertThat(incident.summary()).isEqualTo("IllegalStateException: action broke");
        }));
        edt(application[0]::quit);
        assertThat(terminated.await(5, TimeUnit.SECONDS)).isTrue();
    }

    @DisabledOnOs(OS.WINDOWS)
    @Test void aPluginThatFailsToStartIsAPluginProblemWithShowMoreAndDisablePlugin() throws Exception {
        AppDirs dirs = new AppDirs(home, home.resolve("config.toml"), home.resolve("logs"));
        Path dev = home.resolve("dev-plugin");
        PluginJars.build(dev, "boom.jar", PluginJars.descriptor("dev.example.boom", "1.0.0", "fix.boom.Main"),
            Map.of("fix.boom.Main", BOOM), List.of());
        var terminated = new CountDownLatch(1);
        JasperApplication[] application = new JasperApplication[1];
        edt(() -> {
            application[0] = application(terminated);
            application[0].startPlugins(null, dev, false, dirs);
        });
        edt(() -> {
            assertThat(application[0].incidents().list()).singleElement().satisfies(incident -> {
                assertThat(incident.source()).isEqualTo("dev.example.boom");
                assertThat(incident.operation()).isEqualTo("start");
                assertThat(incident.summary()).isEqualTo("IllegalStateException: boom at start");
            });
            var notice = application[0].notificationCenter().history().stream()
                .filter(entry -> entry.request().group().equals("jasper.plugins")).findFirst().orElseThrow();
            assertThat(notice.request().title()).isEqualTo("dev.example.boom failed to start");
            assertThat(notice.request().actions()).extracting(NotificationAction::label).containsExactly("Show more…", "Disable Plugin");
            assertThat(application[0].contributions().action(ErrorsWindow.SHOW_ERRORS)).isPresent();
        });
        edt(application[0]::quit);
        assertThat(terminated.await(5, TimeUnit.SECONDS)).isTrue();
    }
}
```

- [ ] **Step 2: Run them to see them fail**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.application.JasperApplicationIncidentsTest'`
Expected: compilation fails: `unexpected`, `incidents()` and `notificationCenter()` do not exist.

- [ ] **Step 3: Compose incidents in `JasperApplication`**

1. Add the imports:

```java
import dev.jasper.app.notifications.IncidentReport;
import dev.jasper.app.notifications.Incidents;
import dev.jasper.app.notificationui.ErrorsView;
```

2. After `private final NotificationsFeature notificationViews;` add:

```java
    private final Incidents incidents;
    private final IncidentNotices incidentNotices;
    /** Built once plugins start, with the auxiliary windows it opens in. */
    private ErrorsWindow errorsWindow;
    /** Where Open Log Folder points, known once plugins start. */
    private Path logFolder;
```

3. In the constructor, after `notificationViews = new NotificationsFeature(notificationCenter, contributions);` add:

```java
        incidents = new Incidents(notificationCenter.clock());
        incidentNotices = new IncidentNotices(notificationCenter, incidents, SwingUtilities::invokeLater, this::pluginName,
            this::disablePlugin, this::showErrors);
        notificationCenter.reportActionFailuresTo(failure -> incidentNotices.jasperFailure("a notification action", failure));
```

4. In `openWindow`, after `window.content().onInfo = (title, details) -> notificationViews.info(title, details, windowId);` add:

```java
        window.content().onLaunchFailure = (details, cause) -> incidentNotices.launchFailure(windowId, details, cause);
```

5. In `startPlugins`, after `auxiliary.onAllClosed = () -> { if (windows.isEmpty() && !resident && !quitting) requestShutdown(); };` add:

```java
        logFolder = dirs.logs();
        errorsWindow = new ErrorsWindow(contributions, auxiliary, incidents, new ErrorsView.Hooks(this::pluginName,
            id -> plugins == null ? "" : plugins.pluginVersions().getOrDefault(id, ""), IncidentReport.Environment.current(),
            this::copyReport, incidentNotices::disablePlugin, this::openLogFolder), notificationCenter.clock().getZone());
```

   and right after the `plugins = new PluginRuntime(...);` statement add:

```java
        plugins.reportFailuresTo(failure -> incidentNotices.pluginFailure(failure.pluginId(), failure.operation(), failure.cause()));
```

6. After the static `presence(boolean, boolean, boolean)` method add:

```java
    /** An uncaught failure, after the default handler logged it: an incident (spec section 7, producer 6). Any thread. */
    public void unexpected(Thread thread, Throwable failure) {
        incidentNotices.jasperFailure(thread == null ? "an unknown thread" : "thread " + thread.getName(), failure);
    }

    private String pluginName(String id) {
        PluginRuntime running = plugins;
        return running == null ? id : running.pluginNames().getOrDefault(id, id);
    }

    /** Disable Plugin on an incident: the Plugins page's operation, effective from the next launch. */
    private void disablePlugin(String id, Consumer<Optional<String>> done) {
        if (plugins == null || stopped) {
            done.accept(Optional.of("Plugins are not running."));
            return;
        }
        plugins.setEnabled(id, false, outcome -> done.accept(outcome.ok() ? Optional.empty()
            : Optional.of(outcome.message() == null ? "" : outcome.message())));
    }

    /** Show more… on an incident's notification: the Errors window, with that incident selected. */
    private void showErrors(UUID incident) {
        if (errorsWindow != null && !quitting && !stopped) errorsWindow.show(Optional.of(incident));
    }

    private void copyReport(String text) {
        try {
            java.awt.Toolkit.getDefaultToolkit().getSystemClipboard()
                .setContents(new java.awt.datatransfer.StringSelection(text), null);
        } catch (RuntimeException failure) {
            notificationViews.error("Could not copy the report", failure.getMessage());
        }
    }

    private void openLogFolder() {
        Path folder = logFolder;
        if (folder == null) return;
        CompletableFuture.runAsync(() -> new dev.jasper.app.platform.ConfigEditor().reveal(folder))
            .whenComplete((ignored, failure) -> {
                if (failure != null) SwingUtilities.invokeLater(() -> notificationViews.error("Could not open the log folder", folder.toString()));
            });
    }
```

7. After `dev.jasper.app.contributions.Contributions contributions() { return contributions; }` add:

```java
    NotificationCenter notificationCenter() { return notificationCenter; }

    Incidents incidents() { return incidents; }
```

8. In `shutdown()`, replace `notificationViews.close();` with:

```java
        incidentNotices.close();
        notificationViews.close();
```

- [ ] **Step 4: Forward the bootstrap's uncaught failures to the application**

In `ApplicationBootstrap.startDesktop`, replace

```java
                history -> createApplication(service, history, dirs, options, startup.themeProblems()), application -> {
```

with

```java
                history -> {
                    JasperApplication application = createApplication(service, history, dirs, options, startup.themeProblems());
                    exceptions.forwardTo(application::unexpected);
                    return application;
                }, application -> {
```

- [ ] **Step 5: Run the tests to see them pass**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.application.*' --tests 'dev.jasper.app.bootstrap.*'`
Expected: PASS, including `JasperApplicationNotificationsTest` and `JasperApplicationPluginsTest`.

- [ ] **Step 6: Commit**

```bash
git add jasper-app/src/main/java/dev/jasper/app/application/JasperApplication.java jasper-app/src/main/java/dev/jasper/app/bootstrap/ApplicationBootstrap.java jasper-app/src/test/java/dev/jasper/app/application/JasperApplicationIncidentsTest.java
git commit -m "feat(notifications): record uncaught, plugin, launch and action failures as incidents" -m "The application composes the incident store, its notices and the Errors window, and wires the uncaught handler, plugin containment, each window's launch failure and failing notification actions to them." -m "🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 7: Documentation and full verification

**Files:**
- Modify: `docs/notifications.md`, `docs/app-maintenance.md`, `docs/app-architecture.md`, `docs/STATUS.md`
- Modify: `docs/superpowers/specs/2026-10-02-jasper-notifications-design.md` (status banner)
- Modify: `docs/superpowers/plans/2026-10-02-notifications-2-incidents.md` (status banner)
- Modify: `jasper-app/src/main/java/dev/jasper/app/notifications/package-info.java`,
  `jasper-app/src/main/java/dev/jasper/app/notificationui/package-info.java`

**Interfaces:**
- Consumes: everything above.
- Produces: user and contributor documentation; a green `./gradlew check`.

- [ ] **Step 1: Update the user guide**

In `docs/notifications.md`, add two rows to the Groups table after the Configuration row:

```markdown
| Plugin problems | a plugin that failed while Jasper asked it to do something | card |
| Internal errors | unexpected errors in Jasper itself | card |
```

Insert this section after the paragraph that follows the Groups table (the one about **Show Problems**), before
`## Shortcuts`:

```markdown
## The Errors window

When something fails unexpectedly, in Jasper or in a plugin, Jasper records it as an incident and tells you with an
error notification: "Unexpected error in Jasper", or, for a plugin, "Remote failed in …" or "Remote failed to start".
**Show more…** opens the Errors window at that incident, and **Notifications: Show Errors** in the command palette opens
it too. A shell that could not start is recorded the same way; its notification also offers **Show more…**.

The list on the left holds this session's incidents, newest first. When the same failure happens again, its row counts
up instead of a new row appearing, and its card shows again at most once a minute. The right side shows the
selected incident: who failed and what it was doing, when it was first and last seen, its message and its stack trace.

- **Copy** puts a report on the clipboard: Jasper's version, your OS and Java runtime, the plugin and its version for a
  plugin's incident, and the stack trace. Paste it into a bug report.
- **Disable Plugin** (only for a plugin's incident) disables the plugin from the next launch, as the checkbox on the
  Plugins page does.
- **Open Log Folder** shows Jasper's log files.
- **Clear** empties the list. Notifications of cleared incidents stay in the Notifications panel with their links
  disabled.

Incidents are kept in memory only, at most the latest 100, and are gone when Jasper quits. Messages and stack traces
never reach the log, which keeps the same short, scrubbed records as before.
```

Replace the Shortcuts paragraph

```markdown
**Notifications: Show History** and **Notifications: Dismiss Cards** (closes every card in the window) have no
default shortcut; assign one in Settings › Keymap.
```

with

```markdown
**Notifications: Show History**, **Notifications: Show Errors** and **Notifications: Dismiss Cards** (closes every card
in the window) have no default shortcut; assign one in Settings › Keymap.
```

- [ ] **Step 2: Update the contributor guides**

In `docs/app-maintenance.md`, insert this section after the "Posting a notification" section, before
`## Platform integration`:

```markdown
## Recording an incident

An unexpected failure is an incident: it is kept for the Errors window and announced by one error notification
([guide](notifications.md#the-errors-window), [design](superpowers/specs/2026-10-02-jasper-notifications-design.md)).
`application.IncidentNotices` is the only producer, and its entry points are safe from any thread:

- `jasperFailure(operation, failure)` is for Jasper's own failures. The default uncaught-exception handler (every
  thread, the EDT included) forwards through `JasperApplication.unexpected`, and a notification action that throws
  arrives through `NotificationCenter.reportActionFailuresTo`.
- `pluginFailure(pluginId, operation, failure)` receives every failure `plugins.Containment` catches, through
  `PluginRuntime.reportFailuresTo`. Give each contained call a short operation ("panel <id>", "action <id>"): it is
  the incident's header and the notification's title.
- `launchFailure(window, details, failure)` is for a shell that could not start, from `WindowContent.onLaunchFailure`.

Expected failures the user can act on, such as a file that cannot be read or a host that cannot be reached, are not
incidents: post a notification with the details instead.

A `Capture` takes the trace once, on the thread that caught the failure (trace at most 256 KiB, message at most
16 KiB), so later changes to the throwable never reach the incident. Incidents coalesce by source, exception class
and the root cause's top five frames; at most 100 are kept. Capture never throws and does not re-enter on the same
thread. A failure whose first Jasper frame lies in `dev.jasper.app.notifications`, `dev.jasper.app.notificationui`,
`IncidentNotices`, `NotificationsFeature`, `ErrorsWindow` or `ConfigProblemNotices` is left to the log, so the
notification system can never report its own failures in a loop. The log is unchanged: incidents add no messages or
traces to it.
```

In `docs/app-architecture.md`, replace the `notifications` and `notificationui` rows with:

```markdown
| `notifications` | EDT notification center (groups, the in-memory history, read and expired state, delivery routing) and its values, the incident store with its captures and reports, plus terminal and activity notice production for Buddy. Application owns the center and the incidents and closes notifiers before companion. |
| `notificationui` | EDT Swing views of the center: each window's CardLayer, the history panel, the Errors window's ErrorsView, and their text, style and focus helpers. Application mounts each layer through WindowContent and closes it with its window; the Errors window closes its view when it closes. |
```

- [ ] **Step 3: Update the package-info files**

Replace the `notifications` package-info comment's first paragraph (everything before `<p>Allowed outgoing`) with:

```java
 * EDT notification center (groups, the in-memory history, read and expired state, delivery routing) and its
 * values, the incident store with its captures and clipboard reports, plus terminal and activity notice production
 * for Buddy, active producer identities, attention and visibility policy. The application owns the center and the
 * incidents and closes notifiers before the companion.
```

Replace the `notificationui` package-info comment's first paragraph (everything before `<p>Allowed outgoing`) with:

```java
 * EDT Swing views of the notification center: each window's card stack, the history panel, the Errors window's
 * view, and their text, style and focus helpers. Views subscribe to the center or the incidents and close their
 * subscriptions when closed; the application owns the center, mounts each window's card layer and closes it with the
 * window, and the Errors window closes its view.
```

Keep both files' "Allowed outgoing" lines unchanged: this plan adds no package edges.

- [ ] **Step 4: Run the full check**

Run: `./gradlew check`
Expected: BUILD SUCCESSFUL: all tests, the four architecture gates, doclint and the documentation tests.

Then run the source hygiene check from AGENTS.md over the changed Java files, and `git diff --check`:

```bash
python3 - $(git diff --name-only 63e3b95c -- '*.java') <<'PY'
import pathlib, sys
for name in sys.argv[1:]:
    text = pathlib.Path(name).read_text(encoding="utf-8")
    bad = sum(1 for c in text if 0xD800 <= ord(c) <= 0xDFFF or 0xE000 <= ord(c) <= 0xF8FF or (ord(c) < 0x20 and c not in "\n\t\r") or ord(c) == 0x7f)
    if bad: print(name, "bad chars:", bad)
PY
git diff --check 63e3b95c
```

Expected: no output from either.

Count the tests:

```bash
python3 - <<'PY'
import glob, re
totals = {"tests": 0, "failures": 0, "errors": 0, "skipped": 0}
for path in glob.glob("**/build/test-results/test/*.xml", recursive=True):
    with open(path, encoding="utf-8") as report:
        suite = re.search(r"<testsuite [^>]*>", report.read(4096)).group(0)
    for key in totals: totals[key] += int(re.search(r'\b' + key + r'="(\d+)"', suite).group(1))
print(f"{totals['tests']:,} tests, {totals['failures']} failures, {totals['errors']} errors, {totals['skipped']} skips")
PY
```

- [ ] **Step 5: Record the status**

In `docs/STATUS.md`, add this entry directly after the "Notifications plan 1" entry, with the counts from Step 4:

```markdown
- **Notifications plan 2: incidents and the Errors window, reviewed on branch `claude/notifications-2`; awaiting the
  user's GUI checks and merge decision** (2026-10-02; plan `docs/superpowers/plans/2026-10-02-notifications-2-incidents.md`,
  [guide](notifications.md#the-errors-window)): uncaught exceptions on any thread, contained plugin failures, failed
  shell launches and failing notification actions are in-memory incidents (at most 100, coalesced by source, class
  and root-cause frames, traces taken on the catching thread and capped at 256 KiB). Each is announced by one ERROR
  notification in `jasper.errors`, `jasper.plugins` or, for a shell, `jasper.general`, with Show more… (and Disable
  Plugin for plugins); a repeat updates it, with a card at most once a minute. The Errors window lists them with
  Copy, Disable Plugin, Open Log Folder and Clear. The log is unchanged. Recorded interpretations are in the plan's
  "Spec interpretations" section. `./gradlew check`: **N tests, 0 failures, 0 errors, N skips**. No GUI was launched
  and nothing was pushed. GUI checks for the user:
  - an uncaught exception (for example from a plugin under development) shows an "Unexpected error in Jasper" or
    plugin card; Show more… opens the Errors window at it, in light and dark themes;
  - the Errors window: list selection with the mouse and the arrow keys, the monospaced trace scrolls, Copy pastes a
    report, Open Log Folder opens the folder, Clear empties the list and disables the cleared notifications' links;
  - a plugin that keeps failing shows one card, counts up `×N`, and shows its card again no sooner than a minute later;
  - Disable Plugin posts "… will be disabled when Jasper restarts", and the Plugins page agrees after a restart;
  - a broken shell profile shows "Could not start the terminal" with Show more…, and the window's only pane stays
    open showing the failure;
  - Notifications: Show Errors from the palette; the window remembers its size and position.
```

Replace `N` with the counted numbers.

In the spec's status banner, after the sentence about plan 1, add: "Plan 1 is merged to main (`63e3b95c`). [Plan 2
(incidents)](../plans/2026-10-02-notifications-2-incidents.md) is implemented on branch `claude/notifications-2`."

Replace this plan's **Status** line with what happened: each task's commits, any deviation from the plan text (with
the commit that made it), the final review's fixes and the counts from Step 4.

- [ ] **Step 6: Commit**

```bash
git add docs/notifications.md docs/app-maintenance.md docs/app-architecture.md docs/STATUS.md docs/superpowers/specs/2026-10-02-jasper-notifications-design.md docs/superpowers/plans/2026-10-02-notifications-2-incidents.md jasper-app/src/main/java/dev/jasper/app/notifications/package-info.java jasper-app/src/main/java/dev/jasper/app/notificationui/package-info.java
git commit -m "docs(notifications): document incidents and the Errors window" -m "The guide gains the Errors window and the two new groups, maintenance gains Recording an incident, and the architecture table, package contracts, status and spec banner follow." -m "🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>"
```
