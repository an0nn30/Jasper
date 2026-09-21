# Jasper terminal

A Swing terminal component backed by JediTerm and a local PTY. Applications own
windows, configuration files, shortcuts and session placement. This library owns
emulation, rendering, input, selection, search and shell events.

## Start here

1. Install the **JetBrains Runtime SDK 25**, including development tools. The
   Gradle toolchain requires vendor JetBrains; another Java 25 distribution is
   insufficient. See the [root setup guide](../README.md#requirements).
2. From the repository root run `./gradlew :jasper-terminal:test`.
3. Read [architecture and ownership](../docs/terminal-architecture.md), then the
   session and view entry points below.
4. Read the [compiled examples](src/test/java/dev/jasper/terminal/examples/TerminalExamplesTest.java).
5. Choose a [maintenance recipe](../docs/terminal-maintenance.md).

`./gradlew check` runs all three modules' headless tests, JavaDoc doclint and package/API
checks. `./gradlew verifyTerminalArchitecture` checks compiled dependencies alone.
Generate API documentation with `./gradlew :jasper-terminal:javadoc`; open
`jasper-terminal/build/docs/javadoc/index.html` afterward. Tests do not open GUI
windows. Native visual and throughput checks remain user-run; see
[verification](../docs/terminal-refactor-verification.md).

## Supported API

All package names below begin with `dev.jasper.terminal`.

| Package | Supported types | Role |
| --- | --- | --- |
| `session` | [TerminalSession](src/main/java/dev/jasper/terminal/session/TerminalSession.java), [SessionLaunchOptions](src/main/java/dev/jasper/terminal/session/SessionLaunchOptions.java), [TerminalSessionListener](src/main/java/dev/jasper/terminal/session/TerminalSessionListener.java) | Launch, input, metadata, lifecycle and events |
| `view` | [TerminalView](src/main/java/dev/jasper/terminal/view/TerminalView.java), [TerminalAction](src/main/java/dev/jasper/terminal/view/TerminalAction.java) | Swing component and reusable operations |
| `config` | [TerminalOptions](src/main/java/dev/jasper/terminal/config/TerminalOptions.java), [Palette](src/main/java/dev/jasper/terminal/config/Palette.java), [CursorStyle](src/main/java/dev/jasper/terminal/config/CursorStyle.java), [BellMode](src/main/java/dev/jasper/terminal/config/BellMode.java), [OptionAsMeta](src/main/java/dev/jasper/terminal/config/OptionAsMeta.java), [GridSize](src/main/java/dev/jasper/terminal/config/GridSize.java) | Immutable settings and geometry |
| `search` | [SearchQuery](src/main/java/dev/jasper/terminal/search/SearchQuery.java), [FindResult](src/main/java/dev/jasper/terminal/search/FindResult.java) | Typed search request and outcome |
| `rendering` | [FontSet](src/main/java/dev/jasper/terminal/rendering/FontSet.java) | EDT-owned font selection and metrics |

Builders and documented nested values are included. `internal` packages and
`TerminalSession.internalAccess()` are unsupported module collaboration details,
even where Java requires public visibility. They are not plugin extension points.
Package names are architectural boundaries, not a security sandbox or JPMS modules.

## Configure without starting a process

The following blocks are checked against the compiled example source by the test
suite. Imports are in that source. Builders do no I/O and copy collections.
Launch command, environment and working directory must be explicit. Supply an
actual executable instead of the illustrative `example-shell`; environment is
not inherited implicitly. The process factory forces `TERM=xterm-256color` and
`COLORTERM=truecolor` in its copied environment.

<!-- example:configuration -->
```java
TerminalOptions options = TerminalOptions.defaults().toBuilder()
    .fontSize(16f).copyOnSelect(true).bell(BellMode.NONE).build();
SessionLaunchOptions launch = SessionLaunchOptions.builder()
    .command(List.of("example-shell", "-l"))
    .environment(Map.of("LANG", "C.UTF-8"))
    .workingDirectory(Path.of("."))
    .grid(new GridSize(80,24)).scrollback(options.scrollback()).build();
```

## Embed and own the lifecycle

Start the session off the Event Dispatch Thread (EDT), then create and attach the
view on the EDT. This example transfers the session to its caller. If the pane
closes while launch is in flight, the owner must close that unneeded session
instead of attaching it. Jasper's [ShellLauncher](../jasper-app/src/main/java/dev/jasper/app/launch/ShellLauncher.java)
already handles this application concern. This method is compiled, but never
invoked by the headless example test.

<!-- example:embedding -->
```java
static TerminalSession startForEmbedding(SessionLaunchOptions launch,
        TerminalOptions options, java.util.function.Consumer<TerminalView> attach)
        throws IOException {
    if (SwingUtilities.isEventDispatchThread())
        throw new IllegalStateException("Start processes off the EDT");
    TerminalSession session = TerminalSession.start(launch);
    SwingUtilities.invokeLater(() -> {
        try {
            TerminalView view = new TerminalView(session, options);
            attach.accept(view);
        } catch (RuntimeException | Error failure) {
            session.close();
            throw failure;
        }
    });
    return session;
}
```

Listener delivery follows the event's origin: protocol callbacks normally run
on the reader thread; explicit resize/history operations may notify on their
calling thread. Screen/reset callbacks can hold the buffer lock, so never wait
for the EDT inside one. Post UI work and reject stale pane/attachment callbacks
in the owning application. Exit continuations must not assume EDT delivery.
Registering a listener does not replay earlier events; read current metadata
where initial state matters. Listener exceptions propagate on the source thread, so
callbacks must return promptly without throwing. Removing a listener does not cancel
an already in-flight notification.

<!-- example:events -->
```java
static TerminalSessionListener observe(TerminalSession session,
        java.util.function.Consumer<String> showTitle, Runnable showExit) {
    TerminalSessionListener listener = new TerminalSessionListener() {
        @Override public void titleChanged(String title) {
            SwingUtilities.invokeLater(() -> showTitle.accept(title));
        }
    };
    session.addListener(listener);
    session.exitFuture().thenRun(() -> SwingUtilities.invokeLater(showExit));
    return listener;
}
```

Use these operations on the EDT. `execute` is synchronous and rejects off-EDT
calls. Async search publishes only the latest accepted generation on the EDT;
invalid regex is returned as `FindResult.error()`. `find` is synchronous and can
block the EDT on a large history, so prefer `findAsync` for interactive search.

<!-- example:actions -->
```java
static void updateView(TerminalView view) {
    view.applyOptions(view.options().toBuilder().fontSize(18f).build());
    view.execute(TerminalAction.COPY_SELECTION);
    view.findAsync(new SearchQuery("error", false, false), result -> {
        if (result.error() != null) System.err.println(result.error());
    });
}
```

A removed view cancels presentation work; it does not close the session. The exit
future completes after output ends, the reader waits for the child, and the exit
message is added to the buffer; it does not dispose the Swing pane. Applications
decide whether an exited pane remains visible. The owner removes listeners and closes the session when
its pane is disposed. `close` is safe to repeat.

<!-- example:teardown -->
```java
static void closePane(javax.swing.JPanel pane, TerminalView view,
        TerminalSession session, TerminalSessionListener listener) {
    // EDT: removing a displayable view invokes removeNotify and stops its workers/timers.
    pane.remove(view);
    session.removeListener(listener);
    session.close(); // Native cleanup is bounded and asynchronous; never wait on the EDT.
    pane.revalidate();
    pane.repaint();
}
```

## Migrate from the flat package

Replace old `dev.jasper.terminal.*` imports with the supported packages above.
Replace scalar `TerminalSession.start(...)` arguments with `SessionLaunchOptions`;
replace the nested session listener with `TerminalSessionListener`. Search takes
`new SearchQuery(text, regex, caseSensitive)`. For one-setting changes use
`options.toBuilder()` to retain unrelated values. The old flat classes and
vendor-based session constructors are removed; this is an intentional source
and binary API break. Use test-source `internal.emulation.EmulationFixture` for
headless module tests, never as a production embedding API.

## Next feature

The [maintenance guide](../docs/terminal-maintenance.md) gives edit/test routes for
actions, keys, live settings, shell events, rendering, search, mouse and process
metadata. For application-side ownership, see the completed
[app/Buddy architecture guide](../docs/app-architecture.md). The plugin SDK remains
future work. Keep application policy in `jasper-app`; don't expose an internal
buffer or controller as a shortcut to implementing a plugin.
