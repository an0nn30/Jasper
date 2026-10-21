# Jasper terminal

The terminal library behind Jasper's terminal panes: a Swing component and a session that runs
a local program in a pseudo-terminal (pty4j; ConPTY on Windows) or attaches to a connection that
is not a local process, such as an SSH channel. JediTerm (`jediterm-core` 3.76) is the only
emulator model; this library owns rendering, input, selection, search, links and shell
integration. Embedding applications own windows, configuration files, shortcuts and session
placement. The library reads and writes no files and no configuration.

`jasper-app` is the only module that depends on it. Plugins never use it: they reach terminals
through the SDK package [`dev.jasper.sdk.terminal`](../docs/sdk-reference.html#package-dev.jasper.sdk.terminal),
which the app maps onto this library. The
[`TerminalConnection`](../docs/sdk-reference.html#dev.jasper.sdk.terminal.TerminalConnection) a plugin hands
to [`PendingSession.attach`](../docs/sdk-reference.html#dev.jasper.sdk.terminal.PendingSession), for example,
becomes an `AttachedConnection`. What a user sees in a pane is described in
[Terminal panes](../docs/terminal.md).

## Start here

1. Install the **JetBrains Runtime SDK 25**, including development tools. The
   Gradle toolchain requires vendor JetBrains; another Java 25 distribution is
   insufficient. See the [root setup guide](../README.md#requirements).
2. From the repository root run `./gradlew :jasper-terminal:test`.
3. Read [architecture and ownership](../docs/terminal-architecture.md), then the
   session and view entry points below.
4. Read the [compiled examples](src/test/java/dev/jasper/terminal/examples/TerminalExamplesTest.java).
5. Choose a [maintenance recipe](../docs/terminal-maintenance.md).

`./gradlew :jasper-terminal:check` runs this module's headless tests and JavaDoc doclint.
`./gradlew verifyTerminalArchitecture` checks the package graph and the app's use of the
supported API from bytecode; `:jasper-app:check`, and so the repository-wide `./gradlew check`,
runs it. Generate API documentation with `./gradlew :jasper-terminal:javadoc`; open
`jasper-terminal/build/docs/javadoc/index.html` afterward. Tests do not open GUI windows. Module
tests use the test-source `internal.emulation.EmulationFixture`, never a production embedding API.
Native visual and throughput checks are run by hand with the [benchmarks](../docs/benchmarks.md).

## Supported API

All package names below begin with `dev.jasper.terminal`.

| Package | Supported types | Role |
| --- | --- | --- |
| `session` | [TerminalSession](src/main/java/dev/jasper/terminal/session/TerminalSession.java), [SessionLaunchOptions](src/main/java/dev/jasper/terminal/session/SessionLaunchOptions.java), [TerminalSessionListener](src/main/java/dev/jasper/terminal/session/TerminalSessionListener.java), [AttachedConnection](src/main/java/dev/jasper/terminal/session/AttachedConnection.java), [RemoteDirectory](src/main/java/dev/jasper/terminal/session/RemoteDirectory.java) | Launch or attach, input, metadata, lifecycle and events |
| `view` | [TerminalView](src/main/java/dev/jasper/terminal/view/TerminalView.java), [TerminalAction](src/main/java/dev/jasper/terminal/view/TerminalAction.java), [FileLink](src/main/java/dev/jasper/terminal/view/FileLink.java) | Swing component, its commands and the file a Cmd-click or Ctrl-click names |
| `config` | [TerminalOptions](src/main/java/dev/jasper/terminal/config/TerminalOptions.java), [Palette](src/main/java/dev/jasper/terminal/config/Palette.java), [CursorStyle](src/main/java/dev/jasper/terminal/config/CursorStyle.java), [BellMode](src/main/java/dev/jasper/terminal/config/BellMode.java), [OptionAsMeta](src/main/java/dev/jasper/terminal/config/OptionAsMeta.java), [GridSize](src/main/java/dev/jasper/terminal/config/GridSize.java) | Immutable settings and geometry |
| `search` | [SearchQuery](src/main/java/dev/jasper/terminal/search/SearchQuery.java), [FindResult](src/main/java/dev/jasper/terminal/search/FindResult.java) | Typed search request and outcome |
| `rendering` | [FontSet](src/main/java/dev/jasper/terminal/rendering/FontSet.java) | EDT-owned font selection and metrics |

Builders and documented nested values are included. `internal` packages and
`TerminalSession.internalAccess()` are unsupported module collaboration details,
even where Java requires public visibility; `verifyTerminalArchitecture` rejects any app use of them.
Package names are architectural boundaries, not a security sandbox or JPMS modules. Keep
application policy in `jasper-app`, and give plugins terminal features through the SDK, never
through an internal buffer or controller.

## Configure without starting a process

The following blocks are checked against the compiled example source by the test
suite. Imports are in that source. Builders do no I/O and copy collections.
Launch command, environment and working directory must be explicit. Supply an
actual executable instead of the illustrative `example-shell`; environment is
not inherited implicitly. The process factory forces `TERM=xterm-256color` and
`COLORTERM=truecolor` in its copied environment. Pass `localHostNames`, the names this machine
is known by, so a directory reported under this machine's own name counts as local; without them
only hostless and `localhost` reports are local. Pass `localPathMapper` when a local report's path
means a different directory on this machine (a WSL distribution's paths).

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

`TerminalOptions.defaults()` and `TerminalOptions.builder()` start from the standalone defaults
below. Jasper's panes override them with the `config.toml` settings and the chosen terminal colour
scheme (see the [configuration guide](../docs/configuration.md)).

| Option | Standalone default | Accepted values | Applies |
| --- | --- | --- | --- |
| `fontFamily` | `JetBrains Mono` | nonblank | live |
| `fontSize` | 14 | 6–72 points | live |
| `fallbackFonts` | `Symbols Nerd Font Mono`, `Apple Color Emoji` | nonblank families, tried in order for a character the primary font lacks | live |
| `ligatures` | `true` | | live |
| `palette` | `Palette.jasperDark()` | default foreground, background, cursor, selection and bold colours plus 16 ANSI colours; `Palette.jasperLight()` is the light counterpart | live |
| `cursorStyle` | `BLOCK` | `BLOCK`, `BEAM`, `UNDERLINE` | live; a program's cursor request wins until it resets the cursor |
| `cursorBlink` | `true` | | live; as `cursorStyle` |
| `optionAsMeta` | `LEFT` | `LEFT`, `RIGHT`, `BOTH`, `NONE` | live; macOS only (on Linux and Windows Alt sends Meta unless Ctrl is also held) |
| `scrollback` | 10,000 | 0–1,000,000 lines | new sessions only |
| `copyOnSelect` | `false` | | live |
| `lineHeight` | 1.0 | 0.5–3; below 1 glyphs may overlap neighbouring rows | live |
| `bell` | `VISUAL` | `VISUAL`, `SOUND`, `NONE` | live |

| `SessionLaunchOptions` | Builder default | Rule |
| --- | --- | --- |
| `command` | required | executable, then literal arguments; no shell parsing |
| `environment` | required | the complete child environment |
| `workingDirectory` | required | checked when the process starts |
| `grid` | 80 × 24 | `GridSize` clamps to at least 5 columns and 2 rows |
| `scrollback` | 10,000 | 0–1,000,000 lines, fixed for the session's lifetime |
| `localHostNames` | empty | names this machine is known by; resolve them off the EDT |
| `localPathMapper` | `DEFAULT_LOCAL_PATHS` | turns the path of a local OSC 7 report or clicked `file:` link into a path on this machine; the default reads it as a `file` URI path |

## Embed and own the lifecycle

Start the session off the Event Dispatch Thread (EDT), then create and attach the
view on the EDT. This example transfers the session to its caller. If the pane
closes while launch is in flight, the owner must close that unneeded session
instead of attaching it. In Jasper, [ShellLauncher](../jasper-app/src/main/java/dev/jasper/app/launch/ShellLauncher.java)
starts sessions off the EDT and delivers them on it, and
[`TerminalPane.start`](../jasper-app/src/main/java/dev/jasper/app/workspace/TerminalPane.java)
closes a session that arrives after its pane closed. This method is compiled, but never
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

`TerminalSession.attach(connection, grid, scrollback)` runs an `AttachedConnection` instead
of a child process and may be called from any thread. Ownership of the connection transfers at
the call, whatever the outcome: its `close` runs exactly once, at the latest when the session
closes. The session reads `output` (UTF-8) on its reader thread and calls `input`, `flush` and
`resize` only on its own writer thread, so `write` and `resize` only enqueue; a write that does
not fit the 4 MiB queue is dropped whole and reported through `inputDropped`. The connection's
`close` must be idempotent, must unblock a pending read of `output` and must return promptly; the
session's reader depends on it to end. See
[attached sessions](../docs/terminal-architecture.md#attached-sessions).

Listener delivery follows the event's origin: protocol callbacks normally run
on the reader thread; explicit resize/history operations may notify on their
calling thread. Screen/reset callbacks can hold the buffer lock, so never wait
for the EDT inside one. Post UI work and reject stale pane/attachment callbacks
in the owning application. Exit continuations must not assume EDT delivery.
Registering a listener does not replay earlier events; read current metadata
where initial state matters. Listener exceptions propagate on the source thread (an
`inputDropped` exception is logged instead), so callbacks must return promptly without throwing. Removing a listener does not cancel
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

| `TerminalSessionListener` | Fires when |
| --- | --- |
| `screenChanged()` | visible buffer contents changed; may hold the buffer lock |
| `titleChanged(String)` | a program set the title (OSC 0, 1 or 2) |
| `bell()` | the program sent BEL |
| `workingDirectoryChanged(Path)` | the shell reported a local working directory (OSC 7) |
| `remoteDirectoryChanged(RemoteDirectory)` | the shell reported a directory that is not on this machine |
| `scrollbackReset()` | history was erased, so absolute rows held from before name other lines |
| `alternateBufferChanged(boolean)` | a program entered or left the alternate screen |
| `commandStarted(String)` | the shell marked a command it is about to run (OSC 133 C) and its text is known |
| `commandExecuted(String, OptionalInt, Optional<Path>, Duration)` | the shell finished that command (OSC 133 D, or the next prompt) |
| `inputDropped()` | an attached session's outbound queue was full; called on the writing thread |

| `TerminalSession` | Returns |
| --- | --- |
| `title()` | the last program-set title; empty until a program sets one |
| `workingDirectory()` | the last local OSC 7 directory; empty while the program reports a remote one, and always for an attached session |
| `remoteDirectory()` | the reported directory when it is not on this machine, as host and path text; once the shell reported a directory, exactly one of this and `workingDirectory()` is present |
| `shellIntegrationDetected()` | `true` once the shell sent its first OSC 133 A prompt mark |
| `foregroundJob()` | the executable name of the pseudo-terminal's foreground process group, prefixed with `-` when it is the session's own login shell; empty on Windows, for an attached session and after close. Query off the EDT |
| `usingAlternateBuffer()` | whether a full-screen program switched to the alternate screen |
| `columns()`, `rows()` | the last requested grid, clamped |
| `exitFuture()` | the exit status, as described under [Tear down](#tear-down) |

Send input and read the selection with these; the `TerminalView` methods run on the EDT:

| Method | Effect |
| --- | --- |
| `TerminalSession.write(byte[])`, `write(String)` | sends raw bytes, or text as UTF-8, with no key or paste translation; an attached session only enqueues them |
| `TerminalView.paste(String)` | sends text as the user's paste: newlines become carriage returns, wrapped in bracketed-paste markers when the program enabled them; returns the view to the live screen |
| `TerminalView.hasSelection()` | whether a cell range is selected; reads no terminal text |
| `TerminalView.selectedText()` | validates the selection and extracts its text together under the buffer lock; empty when output has overwritten the selection |
| `TerminalView.clearFind()` | cancels pending search publication and clears the highlights |

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

| `TerminalAction` | Effect |
| --- | --- |
| `COPY_SELECTION` | copies the selection, unless output has overwritten it |
| `PASTE_CLIPBOARD` | pastes clipboard text: newlines become carriage returns, wrapped in bracketed-paste markers when the program enabled them |
| `CLEAR_SCROLLBACK` | clears history and keeps the live screen; sends nothing to the program |
| `CLEAR_TERMINAL` | clears history and the screen above the cursor's line, which moves to the top; sends nothing; does nothing on the alternate screen |
| `FIND_NEXT`, `FIND_PREVIOUS` | moves to the next newer or older match, wrapping |
| `PREVIOUS_PROMPT`, `NEXT_PROMPT` | puts the nearest OSC 133 prompt above, or the next one below, the top of the view at the top; with no next prompt, returns to the live screen |

The view's hooks, all on the EDT:

| `TerminalView` | Effect |
| --- | --- |
| `setShortcutHandler(Predicate<KeyEvent>)` | sees each key press before the program; `true` consumes it and the character it would type. `null` restores the standalone shortcuts |
| `setContextMenuHandler(Consumer<MouseEvent>)` | called once per right-click gesture the view keeps local: when the program has not enabled mouse reporting, or with Shift held |
| `setFileLinkHandler(Consumer<FileLink>)` | receives the path, or local `file:` link, a Cmd-click (macOS) or Ctrl-click (Linux and Windows) names when no URL is under the pointer; without a handler that click starts a selection. A Ctrl-click without Shift goes to a program that enabled mouse reporting |
| `setFindResultListener(Consumer<FindResult>)` | receives `FindResult(0, 0, null)` when a width reflow, history reset or alternate-screen switch drops the matches |
| `setOnCloseRequest(Runnable)` | called on a key press other than a modifier once the exit future has completed normally; the view then consumes every key |
| `afterNextPaint(Runnable)` | runs once after the next completed paint |
| `setPalette`, `setFontSize`, `resetFontSize` | recolour or resize this view only; `setFontSize` clamps to 6–72 points and refits the session, `resetFontSize` sets 14 points |
| `setInactiveDim(float)` | overlays the palette background from 0 (clear) to 1 (fully dimmed) |
| `setBackgroundOpacity(float)` | makes the default background translucent from 1 (opaque, the default) to 0; below 1 the view is not opaque, while explicit cell colours, the selection and the cursor stay solid |

Without a shortcut handler the view handles these keys itself:

| macOS | Linux and Windows | Effect |
| --- | --- | --- |
| Cmd+C | Ctrl+Shift+C | copy the selection |
| Cmd+V | Ctrl+Shift+V | paste the clipboard |
| Cmd+Up | Ctrl+Shift+Up | previous prompt |
| Cmd+Down | Ctrl+Shift+Down | next prompt |

Shift+Page Up and Shift+Page Down scroll the view by its height less one line before any
shortcut handler sees them, and never reach the program.

## Tear down

A removed view cancels presentation work; it does not close the session. A local session's exit
future completes with the child's exit code after output ends and the child has been waited for,
once `[process exited with code N]` is written into the buffer. An attached session writes no such
line: its future completes with the connection's exit status, or exceptionally with an
`IOException` naming the first transport failure or reporting that the connection ended without
an exit status, and `thenRun` in the events example does not run then. Neither disposes the Swing
pane. Applications decide whether an exited pane remains visible. The owner removes listeners and
closes the session when its pane is disposed. `close` is safe to repeat.

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

Closing a local session hangs up the child (Windows: destroys it), force-kills it if it is still
running after 500 ms and then closes its streams, on a separate thread.
