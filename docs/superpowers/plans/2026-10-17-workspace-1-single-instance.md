# Workspace tabs 1: the single instance Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** One Jasper process per home, by construction: a launch takes `<home>/daemon/instance.lock` before AWT or hands
its request (RAISE or OPEN_PATHS, its working directory, its paths and the start-up flags it cannot apply) to the owner
and exits; the owner binds its endpoints at once and runs early requests in order once its application is ready; an
owner that does not answer produces an error, never a second instance; restart's replacement waits for the lock; macOS
open-file events, command-line paths and `jasper <path>...` open files and folders.

**Architecture:** `residency.InstanceLock` is the lock; `bootstrap.LaunchAction` is spec 5.2's table as a pure function;
`bootstrap.SingleInstance` claims the home before AWT (lock, decision, handoff with a 10 s retry, replacement wait,
`LaunchFailure` for the error) and hands an owner `bootstrap.Instance`, which binds the handoff endpoint and the command
socket at once and holds requests in `EarlyRequests` until `compose` hands it the application, then answers a handoff
only once the application's EDT has taken it. `residency.LaunchRequest` speaks protocol 2; `HandoffSocket.answering`
answers UPDATED for another build and PROTOCOL for a request the owner did not take. The application side takes plain
values: `JasperApplication.open(List<Path>, String)` routes RAISE and OPEN_PATHS through today's windows (`OpenPlan`; plan
4 retargets this one method to the main window) and `handedOff(...)` adds the UPDATED and ignored-flags behaviour. Restart
appends `--replacing <pid>`; `--standalone`, RETIRE, `ResidentControl`, the bind-time locks and the private
`commands-<pid>` sockets go.

**Tech Stack:** Java 25 on JetBrains Runtime 25, Swing, Unix domain sockets (`java.nio.channels`), `FileChannel.tryLock`,
Gradle 9 (Kotlin DSL and `gradle/packaging.gradle`), JUnit 6 and AssertJ, C11 for the `jasper` command, Python 3
standard library for the edit scripts (always `python3 -I`).

**Spec:** [`docs/superpowers/specs/2026-10-16-jasper-workspace-tabs-design.md`](../specs/2026-10-16-jasper-workspace-tabs-design.md)
sections 1, 2.5, 4, 5 (all of it), 15, 16, 17 and 18; plan 1 of the six in section 18.

**Workspace:** worktree `/Users/RQ7RQVF/projects/moray/.claude/worktrees/workspace-tabs`, branch `claude/workspace-tabs`
(on `main` at `4ed94fb78`). Run every command from the worktree root. Line numbers are from `c92cd3afb` ("Spec:
workspace tabs approved"); the commits after it on this branch change only the spec and the plans. Every edit quotes the
text it replaces, and the edit scripts stop with a message when that text
is not found the stated number of times: re-find the quoted text, adjust the anchor and re-run; never edit around a
missing anchor. Later tasks anchor on text earlier tasks wrote; the plan quotes that text as those tasks leave it.

## Spec issues

None open. The spec was amended while this plan was written, and the plan follows the amended text: `c6d11f711` dropped
`LSMultipleInstancesProhibited` from 5.4 (Apple's key decides whether more than one *user* may run the app at once and
does not guard `open -n`), so Task 10 adds only `CFBundleDocumentTypes`; `a92166bb2` made 5.3 say that a ready owner
answers a handoff OK only after its EDT has taken it, within one second, and PROTOCOL otherwise, and that a quitting
owner answers PROTOCOL so the launch's retry takes the lock once it has exited (Task 6, Rulings 22 and 23).

## Global Constraints

- Toolchain: `./gradlew` only (never a system `gradle`, never `--stop`); Java 25 on JBR 25. No GUI launches, no AOT
  training, no `:jasper-app:run`, no `:jasper-app:bench` (AGENTS.md). `./gradlew :jasper-app:verifyPackage` without
  `-Pjasper.aot=true` is headless and allowed (Task 10, macOS only).
- One commit per task, ending with exactly the two lines `🤖 Generated with Claude Code at The Home Depot` and
  `Co-Authored-By: Claude <noreply@anthropic.com>` (a blank line between). No push, no merge.
- Clean break (spec 4, 16): `--standalone`, RETIRE, `ResidentControl`, `RestartFlow`'s probe/retire/standalone states,
  `RestartMode.STANDALONE`, `daemon/lock`, `daemon/commands.lock`, `commands-<pid>`, `AppDirs.daemonLock()` and
  `AppDirs.commandSocket(long)` go outright, with their tests and docs; no migration, shim or "was removed" text.
- Package edges (package-info allowed lists, acyclic graph checked by `verifyApplicationArchitecture`): `residency` uses
  only `platform`; `application` never imports `residency` or `bootstrap` (it takes paths, flags and booleans);
  `bootstrap` already lists `application`, `commandline`, `platform`, `residency` and `restart`. `restart` uses no other
  Jasper package.
- Child-JVM tests load only the test classes and jasper-app's main classes: `Instance`, `SingleInstance`, `LaunchAction`,
  `EarlyRequests`, `AppArguments` and everything in `residency` must not reference `ApplicationBootstrap`,
  `JasperApplication` or any type outside the JDK, `platform`, `commandline`, `residency` and `bootstrap`'s own small
  classes.
- The migration ledger: `ThemeMigrationCoverageTest` (`jasper-app/src/test/java/dev/jasper/app/appearance/ThemeMigrationCoverageTest.java:17-18`)
  requires a `docs/design/theme-engine/migration.csv` row for every Java file under `jasper-app/src` that matches its
  `CANDIDATE` pattern (`import java.awt`, `import javax.swing`, the word appearance, `new J…`, or `new …List`,
  `…Panel`, `…View`, `…Window`, …). The code in this plan avoids those in files that have no row (it uses `Stream`,
  `List.copyOf`, `ArrayDeque`, `ConcurrentLinkedQueue` and fully qualified `java.awt.HeadlessException`); the one new
  file that needs Swing, `LaunchFailure`, gets its row in Task 6. Deleting a file never leaves its row behind.
- Javadoc: jasper-app runs `javadoc` with `-Xdoclint:all` and fails on errors: write `{@code --replacing <pid>}`, never a
  bare `<` or `&` in a comment.
- Source hygiene (AGENTS.md): no raw control, private-use or unpaired surrogate characters; `\t` and `\n` escapes only.
- Tests are headless (`java.awt.headless=true` for every test task); a test that needs a window proves it went for one
  with `hasRootCauseInstanceOf(java.awt.HeadlessException.class)`, as `JasperApplicationBackgroundTest` does.
- No test may take the user's real instance lock: every `ApplicationBootstrap.start` a test calls passes a temporary
  `AppDirs` (Ruling 19), and every child JVM gets a temporary home.
- Documentation edits are the smallest that make each doc accurate (AGENTS.md "Documentation edits").

## Review Focus

1. A second launch while the owner is still starting (lock taken, plugins starting, no window yet): it is answered at
   once and exits 0; its request runs once the application is composed, after any request that arrived before it, and
   a `jasper` call made meanwhile returns only when it has run. Once the owner is ready, a handoff is answered only after
   its EDT has taken it, so a hung owner ends the launch with the not-responding error, never a silent exit 0. Task 6:
   `SingleInstanceTest.aSecondLaunchIsAnsweredOnReceiptAndItsRequestRunsOnceTheOwnerIsReady`,
   `aCallWaitsForTheOwnerAndIsAnsweredWhenItHasRun`, `aReadyOwnerWhoseEventThreadNeverTakesTheRequestIsNotResponding`,
   `anOwnerThatTakesTheConnectionButNeverRepliesIsNotResponding`;
   `EarlyRequestsTest.aRequestThatArrivesWhileTheHeldOnesRunWaitsItsTurn`.
2. A launch that meets an owner on its way out (Quit pressed a moment earlier: quitting, endpoints closing, process not
   yet gone): it neither errors nor hands off into the void; the quitting owner answers PROTOCOL and the launch becomes
   the owner once the lock is free. Task 6: `SingleInstanceTest.aLaunchThatOutlastsItsOwnerBecomesTheOwner`,
   `aQuittingOwnerTurnsHandoffsAwayAndTheLaunchTakesTheLockOnceItHasGone`; Task 7: `acceptsRequests`.
3. `Jasper.exe notes.txt` for a file that does not exist, or a stray argument such as `-psn_0_42`: the owner skips what
   is not a file or folder and still raises (or opens) a window, and a path named twice opens once. Task 2:
   `AppArgumentsTest.anArgumentThatIsNotAnOptionIsAPathResolvedAgainstTheWorkingDirectory`; Task 4:
   `OpenPlanTest.aMissingPathIsSkippedAndARepeatOpensOnce`.
4. `jasper editor` in a folder that holds a file named `editor`: the subcommand runs; `jasper ./editor` opens the file;
   `jasper editr` is the unknown command it was, not a silent no-op. Task 9:
   `ApplicationCommandsTest.commandArgumentsArePathsOnlyWhenEveryOneExistsAndNoSubcommandClaimsTheFirst`,
   `aWordThatNamesNothingIsStillACommand`.
5. Restart from a process started as `Jasper notes.txt` or itself a replacement (`--replacing 4242`): the new
   replacement neither reopens `notes.txt` nor waits for the long-gone 4242. Task 3:
   `RestartCommandTest.theLaunchsPathsAndAnEarlierReplacingAreNotRepeated`,
   `aJavaCommandLineIsReplayedWholeWithItsOwnArgumentsRewrittenAfterIt`. A replacement whose predecessor has gone while
   another launch took the home hands off to that owner. Task 6:
   `SingleInstanceTest.aReplacementWhosePredecessorHasGoneHandsOffToTheOwnerThatTookItsPlace`.
6. The lock is never released early. `InstanceLock.tryAcquire` in a process that already holds it (every in-process
   retry) opens no second channel, whose close would drop the process's POSIX lock; a start that fails after the claim
   closes only its endpoints and exits. Task 1: `InstanceLockTest.askingAgainHereNeverDropsTheLockForAnotherProcess`;
   Task 7: the failure paths of `prepareDesktop` and `startDesktop` (`exitAfterFailedStart`), which no test reaches.
7. A file from outside (a handoff, `jasper notes.txt`, Finder's Open With) never goes to the desktop's edit or open
   action: with no plugin's editor it is revealed. Task 4:
   `JasperApplicationPluginsTest.aRequestedFilesDesktopFallbackOnlyReveals`, `ConfigEditorTest.revealOnlyNeverEditsOrOpensAndRefusesTheEdt`.

## Rulings

Decisions the spec leaves open; record them in `docs/STATUS.md` (Task 11). "What — why".

1. **`InstanceLock` lives in `dev.jasper.app.residency`** beside the handoff endpoint, using only `platform` — it is the
   other half of the single-instance contract and must load in a child JVM with nothing but the JDK.
2. **An I/O failure while taking the lock ends the launch with "Jasper could not lock its home folder (path): reason"
   and exit code 1**; only a lock another process holds leads to a handoff — spec 1 asks for duplicates to be
   impossible by construction, and IntelliJ's `DirectoryLock` refuses to start the same way. A home on a filesystem
   without working file locks (some NFS or SMB mounts) therefore cannot start; STATUS says so.
3. **The 10 s handoff retry tries the lock again on every round** (100 ms apart): a launch that meets an owner on its way
   out becomes the owner; the not-responding error is shown only when the lock is still held at the deadline.
4. **"Ready" is the point where `compose` used to bind the endpoint**: the application exists and its plugins have
   started. Held requests then run in arrival order on a virtual thread; a handoff's work is posted to the EDT, so it
   runs after the first window is composed; a call runs through `JasperApplication.commands()` as before. Later
   handoffs follow Ruling 22.
5. **The not-responding error goes to standard error, the log and, with a display, a system-look `JOptionPane`**
   (`bootstrap.LaunchFailure`), only after the launch has decided not to own its home. The pid comes from
   `daemon/instance.pid`, which `InstanceLock` writes right after taking the lock and removes on close (the handoff
   endpoint's `token.pid` goes in Task 8); with none, or one that cannot be read, the message reads "…Quit it and try
   again." A replacement's message names the pid it replaces.
6. **UPDATED is answered whenever the launch's code location or time differs (spec 5.3); the owner acts on it only when
   its own jar changed on disk** (`staleBuild`, the existing reopen-after-update test): with no window it restarts into
   the new build, otherwise it opens the request and posts "Jasper was updated". A launch from another install is
   carried out as an ordinary request — restart replays the owner's own command line and could not reach that build.
7. **Notices**: both in the `jasper.general` group (`NotificationsFeature.GENERAL`), severity INFO. "Jasper was updated"
   with content "Restart Jasper to run the new build." and a Restart action (a restart that cannot be planned posts
   "Jasper could not restart"); "Jasper is already running: quit it first to start with --safe-mode", naming the flags
   in the order `--safe-mode`, `--config`, `--plugin-dir`, joined with ", " and " and ".
8. **Restart keeps today's order** — the plugin and quit guards first, the replacement spawned from the exit thread
   after the bounded cleanup — and always appends `--replacing <pid>`; the replacement drops the launch's paths, an
   earlier `--replacing`, `--background` and `--no-window`. The standalone fallback goes: the replacement waits for the
   lock instead.
9. **`RestartFlow` stays as the Plugins page's two-state banner** (IDLE, UNAVAILABLE): Restart Now and Restart Normally
   restart at once. `PluginManagerPanel`'s notice line goes with its only caller (the standalone notice): its field,
   `notice(String)`, `noticeText()` and the header's hidden `notice` node. The node was hidden in every state the 8b and
   9b comparisons render and the kit's stacks lay out only visible children, so the renders do not move; no render of
   the page is stored, and Task 3 records the `settings-plugins` pair before and after.
10. **Command-line paths**: an argument that does not start with `--` is a path, resolved against the working directory
    and normalized (a single dash is a path). `--replacing <pid>` takes a positive process id and is not in the usage.
11. **The Java dispatcher, not the C client, tells a path from a subcommand**: no arguments raise; a first argument some
    plugin declares as a subcommand runs it (even when a file has that name: `jasper ./editor` opens the file);
    otherwise the call opens paths when every argument names an existing file or folder resolved against the caller's
    folder, and is the unknown command it was when one does not — except that a call whose first argument names an
    existing file or folder says "jasper: no such file or folder: a, b" for the others and exits 1. The C client changes
    only its usage text — only the running Jasper knows its subcommands (`daemon/subcommands` can be stale), and the
    client already sends its folder.
12. **OPEN_PATHS through today's windows** (until plan 4): the target is the last active window, else the first open
    one; with none, a new window opens, in the first directory when there is one. Every other directory opens as a
    terminal tab there (`WindowContent.newTab`), every file in the editor a plugin provides, else revealed (Ruling 26);
    anything else is skipped and a repeated path opens once. `JasperApplication.open(List<Path>, String)` is the single
    method plan 4 retargets to the main window; RAISE is `open(List.of(), trigger)`.
13. **A cold start with paths** gives its first window the first directory the launch names (one tab there) and opens
    the rest of the paths in it (`JasperApplication.openFirstWindow`); start-up timing still says "cold", as for
    `newWindow()`.
14. **The owner binds whatever is at the socket paths**, deleting a file left there: under the instance lock no other
    process of this build binds. An older build's resident left running from before an update is not reachable and
    keeps running until it quits (a one-time transition; Jasper is an unpublished MVP); STATUS tells the user to quit it
    completely before the first launch of this build.
15. **An owner whose endpoint cannot bind** (a home whose socket path is over 100 bytes) runs unreachable and logs
    "Jasper's home folder path is too long for its socket: <path>"; a second launch says the same at once, exits 1 and
    never starts a second instance (`HandoffSocket.pathFits`, `tooLong`).
16. **Child JVMs start with `java.home/bin/java`** and the code sources of the test main and of `InstanceLock`, as
    `EndpointLockChild` and `ControlledSessionChild` do — `ProcessHandle.current().info().command()` may be empty, and
    the test worker's full class path is not needed.
17. **A request longer than the handoff line (1 MiB)** ends the launch with "Jasper cannot open that many paths at
    once." and exit code 2, before anything is sent.
18. **macOS document types**: one entry "Text" (`public.text`, `public.source-code`) with role Editor and one "Folder"
    (`public.folder`) with role Viewer, both `LSHandlerRank` Alternate; `verifyPackage` compares them as JSON.
19. **`ApplicationBootstrap.start` takes the home (`AppDirs`) as a parameter**; `main` resolves it from the environment.
    The test entries require one — today they resolve the user's real home (`ApplicationBootstrap.java:483`, and the
    test task sets no `jasper.home`), which would now take the real instance lock or hand off to the user's Jasper.
20. **A `--background` launch that turns out not to be resident** (the setting is off) exits from `startDesktop` after
    reading the configuration, as today, once its queue holds no request (`Instance.declineIfIdle`): later handoffs then
    hear PROTOCOL and take the lock once it has exited. When a launch already handed it a request, which it answered OK,
    it continues as an ordinary start and carries the request out; on macOS its first window brings it back into the
    Dock, as a resident's does.
21. **`jasper` with no arguments and `jasper <path>…` are answered once the window was raised or the paths opened**
    (spec 5.3: "a CLI call is answered when it has run"); before, the no-argument call answered at once.
22. **Once ready, the owner answers a handoff only after its EDT has taken it, within one second** (`Instance.TAKE`,
    spec 5.3 as amended), else PROTOCOL: the acceptor posts the request and waits; a request the EDT reaches too late is
    withdrawn, never carried out. Before ready, a handoff is answered OK on receipt and held. The launch's wait for an
    answer grows from 2 s to 3 s (`HANDOFF_TIMEOUT_MILLIS`) to cover the owner's read and its EDT's second, and the
    owner's read ends after 500 ms without progress, not 500 ms in all, so a request near the 1 MiB line arrives whole.
23. **A quitting owner takes no handoff** (`JasperApplication.acceptsRequests()`, false from the moment quit begins,
    read on the EDT): the launch hears PROTOCOL, keeps trying, and takes the lock once the owner has exited (spec 5.3).
24. **A start that fails after claiming the home closes its endpoints and exits** (`prepareDesktop`'s and
    `startDesktop`'s failure paths, `exitAfterFailedStart` once the splash has closed): the lock is released with the
    process, never while it still shuts down. `start`'s own rollback, before AWT, still closes the whole `Instance`; the
    tests rely on it, and in `main` the process ends with that exception.
25. **`InstanceLock.tryAcquire` never opens a second channel on a file this process holds** (it is synchronized and
    compares the real path): on POSIX systems closing any descriptor of a file drops the process's locks on it, so an
    in-process retry (the handoff loop, the replacement's wait, a test) would otherwise release the owner's lock.
26. **A file from outside** (a handoff, `jasper <path>`, the macOS open-file event, a cold start's paths) **opens in the
    editor a plugin provides, else is revealed in the file manager** (`openRequestedFile`, `ConfigEditor.revealOnly`),
    never through the desktop's edit or open action: with Jasper among the text editors, the edit action could hand the
    file straight back to Jasper, and the open action runs scripts. Cmd-click keeps edit-or-reveal.
27. **A replacement whose predecessor has gone while the lock is still held hands off**: another launch took the home in
    between, or Windows has not yet released the dead process's lock; the handoff loop then delivers its request or
    takes the lock. While the predecessor lives the replacement waits up to 30 s, then names it as not responding.

## Verified facts

At `c92cd3afb` unless a task is named.

1. `HandoffSocket.bind(socketPath, tokenPath, lockPath, handler)` (`residency/HandoffSocket.java:74-111`) takes
   `daemon/lock` while binding (`:85-87`), declines when something answers on the path (`:88-91`, `owned` `:270-276`),
   writes the token and `token.pid` (`:94-95`); `close` takes the same lock and deletes the files unless something
   answers (`:197-220`); `serve` closes the endpoint after a STALE reply (`:266`); `handOff`/`retire`/`live`
   (`:118-131`) send `LaunchRequest.Kind.OPEN`/`RETIRE`; `ownerPid` reads `token.pid` (`:168-175`).
2. `LaunchRequest(token, codeLocation, codeLocationModified, kind)` is protocol 1 with five tab-separated fields and an
   optional `retire` sixth (`residency/LaunchRequest.java:15-63`); replies OK, TOKEN, STALE, PROTOCOL (`:74-88`). Its
   class comment says the request carries no working directory by design (`:11-14`).
3. `ApplicationBootstrap.start` (`bootstrap/ApplicationBootstrap.java:474-512`) parses, resolves the home from the
   environment (`:483`), then `handsOff` (`:521-529`, `WindowsForeground.allow` at `:525`), `alreadyResident`
   (`:536-538`), `alreadyServing` (`:585-587`), and only then the pre-pass and the configuration. `startDesktop`
   (`:250-312`) exits a `--background` launch with residency off (`:260-265`); `compose` (`:388-411`) binds the endpoint
   after `createApplication` started the plugins (`:400-406`) and sets residency from the bind (`:406`);
   `createApplication` (`:314-371`) wires `ResidentControl` (`:334-338`), skips the subcommand list for a private launch
   (`:362`) and binds the command socket after the plugins (`:365-366`, `serveCommands`/`bindCommands`/`commandSocket`
   `:551-582`). `handoffHandler` (`:646-661`) retires on RETIRE, releases the endpoint on a stale build, otherwise posts
   `openOrRaise(HANDOFF)`. `replacementHandsOff` and `standaloneNotice` (`:616-623`) feed the restart conversation.
4. `AppArguments` (`bootstrap/AppArguments.java`) has `--standalone`, `standaloneLaunch()` (`:43-45`, including a home
   override) and `privateCommands()` (`:51-53`); any argument it does not know is "Unknown argument." (`:89`).
5. `CommandListener.bind` takes `<socket>.lock` and declines when the socket is live (`commandline/CommandListener.java:87-122`);
   `close` takes it again and deletes a private socket's lock (`:360-388`). `AppDirs.daemonLock()` and
   `commandSocket(long)` (`platform/AppDirs.java:75-90`) name `daemon/lock` and `commands-<pid>`.
6. `RestartFlow` (`restart/RestartFlow.java`) probes `ResidentControl.live`, retires, waits 30 s and offers
   `RestartMode.STANDALONE`; `RestartCommand.plan` filters flags by spelling and `standalone()` appends `--standalone`
   (`restart/RestartCommand.java:20-56`). `JasperApplication.restart` (`application/JasperApplication.java:1671-1681`)
   asks the guards, records the relaunch and quits; `relaunchIfRequested` (`:1684-1695`) spawns it from the exit thread,
   `--standalone` when cleanup did not finish. Callers: Restart Now/Normally (`pluginManager()` `:1745-1757`), VM
   options (`:1055`), Try Again for a skipped look (`:1027`), `reopened()` (`:722-731`).
7. `PluginManager.Hooks` carries `ResidentControl resident` and `boolean standaloneNotice` (`pluginmanager/PluginManager.java:36-46`);
   `refresh` probes for the standalone notice (`:112-122`); `renderBanner` has six states (`:136-158`). Test callers of
   `RestartFlow`/`Hooks`: `PluginManagerTest`, `PluginManagerStatesPreview`, `EnterpriseSettings`,
   `PluginSettingsPagesEvidenceTest`, `Settings9bPreview`, `SettingsDialogPreview`.
8. `JasperApplication.openOrRaise(String)` and `openOrRaise(Path, String)` (`:819-834`) raise the last active window or
   open one; callers are `commands()` (`:660`), `reopened()` (`:730`), the resident indicator (`:736`) and the handoff
   handler. `openFileAt(file, line, window)` (`:622-638`) is the Cmd-click path. `endpointReleased` (`:803-806`) exists
   for the STALE path only. The quit, reopen, preferences and about handlers are installed in the constructor
   (`:392-411`) and removed in `shutdown` (`:1936-1939`); no open-file handler exists.
9. `PluginCommands.dispatch` answers "jasper: unknown command NAME; run jasper --help for the list" with exit 2
   (`plugins/PluginCommands.java:88-93`); `Subcommands.owner(name)` (`plugins/Subcommands.java:74`) also knows names of
   plugins that do not run; `PluginRuntime.commands()` (`plugins/PluginRuntime.java:568-575`).
10. The `jasper` client sends every call, arguments as given plus its working directory, to the command socket, and
    runs the launcher for a first argument starting with `--` (`packaging/cli/src/jasper_cli.c:766-776`); its usage
    names `--standalone` (`:63-69`).
11. `packageApp` edits the plist with `plutil -replace` (`gradle/packaging.gradle:304-307`) and lists its inputs
    (`:260-272`); `verifyPackage` reads plist keys with `plutil -extract … raw` (`:552-558`). `check` builds neither.
12. Tests run headless (`build.gradle.kts:31`); the test task sets no `jasper.home` (`jasper-app/build.gradle.kts:84-115`).
    Child-JVM helpers use `java.home/bin/java` and code-source class paths
    (`jasper-app/src/test/java/dev/jasper/app/testsupport/EndpointLockChild.java:11-14`).
13. `JasperApplication.notificationCenter()`, `restartPlanner` and `spawner` are package-private test seams
    (`:169-173`, `:1712`); `NotificationCenter.history()` records a notice posted with no window open
    (`PresentationAttemptTest:179-181` reads one).

## File structure

- `residency`: new `InstanceLock`; `LaunchRequest` (protocol 2), `HandoffSocket` (no bind lock, `handOff` returns the
  answer, `answering`), `package-info`.
- `bootstrap`: new `LaunchAction`, `EarlyRequests`, `Instance`, `SingleInstance`, `LaunchFailure`; `AppArguments` (paths,
  `--replacing`, no `--standalone`), `ApplicationBootstrap` (claims the home before AWT), `package-info`.
- `application`: new `OpenPlan`; `JasperApplication` (`open`, `handedOff`, `filesOpened`, `launchArguments`,
  `restart(mode, paths)`, path calls).
- `restart`: `RestartCommand`, `RestartMode`, `RestartFlow`, `package-info`; `ResidentControl` deleted.
- `pluginmanager.PluginManager`, `pluginmanager.PluginManagerPanel`, `plugins.PluginCommands`, `plugins.PluginRuntime`,
  `commandline.CommandListener`, `platform.AppDirs`, `platform.ConfigEditor` (`revealOnly`).
- `packaging/cli/src/jasper_cli.c` (usage), `gradle/packaging.gradle` (plist keys and their check).
- Tests: new `residency/InstanceLockTest`, `residency/InstanceLockProbe`, `testsupport/ChildJvm`,
  `bootstrap/LaunchActionTest`, `bootstrap/EarlyRequestsTest`, `bootstrap/SingleInstanceTest`,
  `bootstrap/SingleInstanceProcessTest`, `bootstrap/LaunchProbe`, `application/OpenPlanTest`; changed `AppArgumentsTest`,
  `ApplicationBootstrapTest`, `ApplicationBootstrapRollbackTest`, `BootstrapTestSupport`, `LaunchRequestTest`,
  `HandoffSocketTest`, `CommandListenerTest`, `CommandClientTest`, `AppDirsTest`, `RestartCommandTest`, `RestartFlowTest`,
  `PluginManagerTest`, `PluginManagerPanelTest`, `PluginRuntimeTest`, `ApplicationCommandsTest`,
  `JasperApplicationResidencyTest`, `JasperApplicationBackgroundTest`, `JasperApplicationPluginsTest`,
  `PresentationAttemptTest`, `ConfigEditorTest` and the five Settings and Plugins previews;
  `testsupport/EndpointLockChild` deleted.
- Docs (Task 11): `docs/configuration.md`, `config.example.toml`, `docs/app-architecture.md`, `docs/app-maintenance.md`,
  `docs/packaging.md`, `docs/sdk-architecture.md`, `docs/plugin-authoring.md`, `packaging/cli/README.md`,
  `docs/STATUS.md`.

## Before you start

```bash
git diff --stat c92cd3afb HEAD -- jasper-app jasper-sdk gradle packaging config.example.toml docs/*.md   # nothing: only the spec and plans moved
git status --short            # nothing
grep -c 'public void openOrRaise(String trigger)' jasper-app/src/main/java/dev/jasper/app/application/JasperApplication.java   # 1
grep -c 'boolean standaloneLaunch()' jasper-app/src/main/java/dev/jasper/app/bootstrap/AppArguments.java                      # 1
grep -c 'RETIRE' jasper-app/src/main/java/dev/jasper/app/residency/LaunchRequest.java                                        # 3
ls jasper-app/src/main/java/dev/jasper/app/restart/ResidentControl.java
```

Every command prints its count or file. If a count differs, `main` moved under this branch: re-verify the facts above
before Task 1 and record what moved in the STATUS entry (Task 11).

---

### Task 1: The instance lock

Spec 5.1: `<home>/daemon/instance.lock`, `FileChannel.tryLock()`, held for the life of the process; the owner records
its process id in `daemon/instance.pid` right after taking it. Rulings 1, 5, 16, 25.

**Files:**
- Create: `jasper-app/src/main/java/dev/jasper/app/residency/InstanceLock.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/platform/AppDirs.java:71-73`, `jasper-app/src/main/java/dev/jasper/app/residency/package-info.java:2`
- Create (test): `jasper-app/src/test/java/dev/jasper/app/testsupport/ChildJvm.java`,
  `jasper-app/src/test/java/dev/jasper/app/residency/InstanceLockProbe.java`,
  `jasper-app/src/test/java/dev/jasper/app/residency/InstanceLockTest.java`
- Modify (test): `jasper-app/src/test/java/dev/jasper/app/platform/AppDirsTest.java:71`

**Interfaces:**
- Consumes: `platform.OwnerOnlyFiles.restrict(Path, String)`, `writeProcessId(Path)`.
- Produces: `public final class InstanceLock implements AutoCloseable` with `public static Optional<InstanceLock>
  tryAcquire(Path file) throws IOException` (synchronized; never opens a second channel on a file this process holds;
  writes `instance.pid` beside the lock), `public static long ownerPid(Path file)` (-1 when absent or unparsable),
  `public Path file()`, `public void close()`; `public Path AppDirs.instanceLock()` (`daemon/instance.lock`); the test
  helper `testsupport.ChildJvm` (`start(Class<?> main, Class<?> anchor, String... arguments)`, `line(long seconds)`,
  `pid()`, `alive()`, `finish()`, `exit(long seconds)`, `kill()`, `close()`); the test main `residency.InstanceLockProbe`
  (`hold <lock>`, `await <lock> <millis>`). Tasks 6, 7 and 8 use all of these.

- [ ] **Step 1: Write the failing tests**

Create `jasper-app/src/test/java/dev/jasper/app/testsupport/ChildJvm.java`:

```java
package dev.jasper.app.testsupport;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A test {@code main} in a child JVM, for the single-instance tests (workspace-tabs spec 17): headless, with the test
 * classes and the application's classes on its class path. What it prints arrives line by line through {@link #line};
 * closing its standard input ({@link #finish}) asks it to end, and {@link #close} makes sure it has.
 */
public final class ChildJvm implements AutoCloseable {
    private final Process process;
    private final BlockingQueue<String> lines = new LinkedBlockingQueue<>();

    private ChildJvm(Process process) {
        this.process = process;
        Thread.ofVirtual().name("child-jvm-output").start(() -> {
            try (BufferedReader reader = process.inputReader()) {
                for (String line = reader.readLine(); line != null; line = reader.readLine()) lines.add(line);
            } catch (IOException ended) {
                // The child went away; its lines so far are kept.
            }
        });
    }

    /**
     * Starts {@code main} with {@code arguments}; {@code anchor} is a production class whose classes join the class path.
     *
     * @param main a test class with a {@code main} method
     * @param anchor a class from the application's main classes
     * @param arguments the program's arguments
     * @return the running child
     * @throws IOException when the JVM cannot be started
     */
    public static ChildJvm start(Class<?> main, Class<?> anchor, String... arguments) throws IOException {
        String java = Path.of(System.getProperty("java.home"), "bin",
            System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java").toString();
        String classPath = Stream.of(main, anchor).map(ChildJvm::codeSource).distinct().collect(Collectors.joining(File.pathSeparator));
        var command = Stream.concat(Stream.of(java, "-Djava.awt.headless=true", "-cp", classPath, main.getName()),
            Stream.of(arguments)).toList();
        return new ChildJvm(new ProcessBuilder(command).redirectError(ProcessBuilder.Redirect.INHERIT).start());
    }

    private static String codeSource(Class<?> type) {
        try {
            return Path.of(type.getProtectionDomain().getCodeSource().getLocation().toURI()).toString();
        } catch (URISyntaxException failure) {
            throw new UncheckedIOException(new IOException(failure));
        }
    }

    /** The next line it printed, waiting up to {@code seconds}; null when none came. */
    public String line(long seconds) throws InterruptedException { return lines.poll(seconds, TimeUnit.SECONDS); }

    /** Its process id. */
    public long pid() { return process.pid(); }

    /** Whether it still runs. */
    public boolean alive() { return process.isAlive(); }

    /** Closes its standard input, which asks it to end. */
    public void finish() throws IOException { process.getOutputStream().close(); }

    /** Waits up to {@code seconds} for it to exit: its exit code, or -1 when it still runs. */
    public int exit(long seconds) throws InterruptedException {
        return process.waitFor(seconds, TimeUnit.SECONDS) ? process.exitValue() : -1;
    }

    /** Kills it at once, as a crash would. */
    public void kill() throws InterruptedException {
        process.destroyForcibly();
        process.waitFor(10, TimeUnit.SECONDS);
    }

    @Override public void close() throws Exception {
        try { finish(); } catch (IOException gone) { }
        if (!process.waitFor(5, TimeUnit.SECONDS)) kill();
    }
}
```

Create `jasper-app/src/test/java/dev/jasper/app/residency/InstanceLockProbe.java`:

```java
package dev.jasper.app.residency;

import java.nio.file.Path;
import java.util.Optional;

/**
 * A child JVM for the single-instance tests. {@code hold <lock>} takes the lock (which records this process's id in
 * {@code instance.pid}), prints "owner" or "held", and keeps it until its standard input closes. {@code await <lock>
 * <millis>} tries every 20 ms until it takes the lock, then prints "owner" and holds it the same way, or prints
 * "timeout": a lock a dead process held is released asynchronously on Windows.
 */
public final class InstanceLockProbe {
    private InstanceLockProbe() { }

    public static void main(String[] args) throws Exception {
        boolean await = args[0].equals("await");
        Path file = Path.of(args[1]);
        long deadline = System.nanoTime() + (await ? Long.parseLong(args[2]) : 0) * 1_000_000L;
        Optional<InstanceLock> lock = InstanceLock.tryAcquire(file);
        while (await && lock.isEmpty() && System.nanoTime() < deadline) {
            Thread.sleep(20);
            lock = InstanceLock.tryAcquire(file);
        }
        System.out.println(lock.isPresent() ? "owner" : await ? "timeout" : "held");
        System.out.flush();
        if (lock.isEmpty()) return;
        while (System.in.read() >= 0) { }
        lock.get().close();
    }
}
```

Create `jasper-app/src/test/java/dev/jasper/app/residency/InstanceLockTest.java`:

```java
package dev.jasper.app.residency;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.app.testsupport.ChildJvm;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/** Spec 5.1 and 17: the instance lock in this JVM and across real child JVMs. */
@Timeout(120)
class InstanceLockTest {
    @TempDir Path dir;

    private Path file() { return dir.resolve("daemon").resolve("instance.lock"); }

    private ChildJvm probe(String... arguments) throws Exception {
        return ChildJvm.start(InstanceLockProbe.class, InstanceLock.class, arguments);
    }

    @Test void theLockIsTakenOnceInAProcessInAnOwnerOnlyFolderAndReleasedByClose() throws Exception {
        try (InstanceLock lock = InstanceLock.tryAcquire(file()).orElseThrow()) {
            assertThat(lock.file()).isEqualTo(file());
            assertThat(InstanceLock.tryAcquire(file())).as("held here already").isEmpty();
            assertThat(InstanceLock.tryAcquire(dir.resolve("daemon/../daemon/instance.lock"))).as("by any spelling").isEmpty();
            if (dir.getFileSystem().supportedFileAttributeViews().contains("posix"))
                assertThat(PosixFilePermissions.toString(Files.getPosixFilePermissions(file().getParent()))).isEqualTo("rwx------");
        }
        try (InstanceLock again = InstanceLock.tryAcquire(file()).orElseThrow()) {
            assertThat(again.file()).as("released by close").isEqualTo(file());
            again.close();
            again.close();
        }
    }

    @Test void askingAgainHereNeverDropsTheLockForAnotherProcess() throws Exception {
        // Opening and closing a second channel on the file would release this process's POSIX lock (plan Ruling 25).
        try (InstanceLock lock = InstanceLock.tryAcquire(file()).orElseThrow()) {
            assertThat(InstanceLock.tryAcquire(file())).isEmpty();
            try (ChildJvm other = probe("hold", file().toString())) {
                assertThat(other.line(60)).as("still held by this process").isEqualTo("held");
            }
        }
    }

    @Test void theOwnerRecordsItsProcessIdBesideTheLock() throws Exception {
        assertThat(InstanceLock.ownerPid(file())).as("no owner yet").isEqualTo(-1);
        try (InstanceLock lock = InstanceLock.tryAcquire(file()).orElseThrow()) {
            assertThat(file().resolveSibling("instance.pid")).exists();
            assertThat(InstanceLock.ownerPid(file())).isEqualTo(ProcessHandle.current().pid());
        }
        assertThat(InstanceLock.ownerPid(file())).as("removed by close").isEqualTo(-1);
        Files.writeString(file().resolveSibling("instance.pid"), "not a number\n");
        assertThat(InstanceLock.ownerPid(file())).isEqualTo(-1);
    }

    @Test void aSecondProcessCannotTakeTheLockAndReadsItsOwner() throws Exception {
        try (ChildJvm owner = probe("hold", file().toString())) {
            assertThat(owner.line(60)).isEqualTo("owner");
            assertThat(InstanceLock.ownerPid(file())).isEqualTo(owner.pid());
            try (ChildJvm second = probe("hold", file().toString())) {
                assertThat(second.line(60)).isEqualTo("held");
                assertThat(second.exit(60)).isZero();
            }
            assertThat(InstanceLock.tryAcquire(file())).as("nor can this process").isEmpty();
        }
    }

    @Test void aKilledOwnerReleasesTheLock() throws Exception {
        try (ChildJvm owner = probe("hold", file().toString())) {
            assertThat(owner.line(60)).isEqualTo("owner");
            owner.kill();
            assertThat(owner.alive()).isFalse();
        }
        // Windows releases a dead process's locks asynchronously, so the next process waits a moment for it.
        try (ChildJvm next = probe("await", file().toString(), "10000")) {
            assertThat(next.line(60)).as("the operating system released it").isEqualTo("owner");
        }
    }

    @Test void aWaiterTakesTheLockOnceItsOwnerExits() throws Exception {
        try (ChildJvm owner = probe("hold", file().toString())) {
            assertThat(owner.line(60)).isEqualTo("owner");
            try (ChildJvm waiter = probe("await", file().toString(), "60000")) {
                assertThat(waiter.line(2)).as("still waiting").isNull();
                owner.finish();
                assertThat(owner.exit(60)).isZero();
                assertThat(waiter.line(60)).isEqualTo("owner");
            }
        }
    }
}
```

In `jasper-app/src/test/java/dev/jasper/app/platform/AppDirsTest.java` add, before the line
`        assertThat(dirs.daemonLock()).isEqualTo(daemon.resolve("lock"));`:

```java
        assertThat(dirs.instanceLock()).isEqualTo(daemon.resolve("instance.lock"));
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.residency.InstanceLockTest' --tests 'dev.jasper.app.platform.AppDirsTest'`
Expected: FAIL to compile: "cannot find symbol … InstanceLock", "… instanceLock()".

- [ ] **Step 3: The lock and its path**

Create `jasper-app/src/main/java/dev/jasper/app/residency/InstanceLock.java`:

```java
package dev.jasper.app.residency;

import dev.jasper.app.platform.OwnerOnlyFiles;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * One process per Jasper home (workspace-tabs spec 5.1): an exclusive lock on {@code <home>/daemon/instance.lock},
 * taken before AWT starts and held for the life of the process. The operating system releases it however the process
 * ends, so it is never stale. The process that holds it is the home's owner, and records its process id beside it in
 * {@code instance.pid}.
 */
public final class InstanceLock implements AutoCloseable {
    /** Every lock this process holds, strongly reachable: a collected channel would release its lock. */
    private static final Set<InstanceLock> HELD = ConcurrentHashMap.newKeySet();

    private final Path file;
    private final Path key;
    private final FileChannel channel;
    private final FileLock lock;

    private InstanceLock(Path file, Path key, FileChannel channel, FileLock lock) {
        this.file = file;
        this.key = key;
        this.channel = channel;
        this.lock = lock;
    }

    /**
     * Takes the lock, creating the {@code daemon} folder owner-only, and records this process's id beside it. When this
     * process already holds the file, by any spelling of its path, it returns empty without opening it again: closing a
     * second channel on the file would release the process's lock on POSIX systems.
     *
     * @param file the home's lock file
     * @return the lock, held until {@link #close} or the end of the process; empty when another process holds it, or
     *     this one does
     * @throws IOException when the file cannot be opened or locked for any other reason
     */
    public static synchronized Optional<InstanceLock> tryAcquire(Path file) throws IOException {
        Objects.requireNonNull(file, "file");
        Path given = file.toAbsolutePath().normalize();
        Files.createDirectories(given.getParent());
        Path key = given.getParent().toRealPath().resolve(given.getFileName());
        for (InstanceLock held : HELD) if (held.key.equals(key)) return Optional.empty();
        OwnerOnlyFiles.restrict(given.getParent(), "rwx------");
        FileChannel channel = FileChannel.open(key, StandardOpenOption.CREATE, StandardOpenOption.READ, StandardOpenOption.WRITE);
        FileLock lock;
        try {
            lock = channel.tryLock();
        } catch (IOException | RuntimeException failure) {
            channel.close();
            throw failure;
        }
        if (lock == null) {
            channel.close();
            return Optional.empty();
        }
        var held = new InstanceLock(given, key, channel, lock);
        HELD.add(held);
        try {
            OwnerOnlyFiles.writeProcessId(pidFile(given));
        } catch (IOException unwritten) {
            // Only a hint for the not-responding message; the lock is what counts.
        }
        return Optional.of(held);
    }

    /**
     * The process id the home's owner recorded beside its lock right after taking it. A hint, not proof: an owner that
     * died leaves its id behind until the next owner replaces it.
     *
     * @param file the home's lock file
     * @return the id, or -1 when there is none or it cannot be read
     */
    public static long ownerPid(Path file) {
        try {
            long pid = Long.parseLong(Files.readString(pidFile(file), StandardCharsets.UTF_8).strip());
            return pid > 0 ? pid : -1;
        } catch (IOException | RuntimeException unreadable) {
            return -1;
        }
    }

    private static Path pidFile(Path file) { return file.resolveSibling("instance.pid"); }

    /**
     * The lock file, as it was asked for.
     *
     * @return its absolute path
     */
    public Path file() { return file; }

    /**
     * Releases the lock and removes the process id beside it: tests and {@code start}'s own rollback; an owner keeps it
     * until its process ends. Idempotent.
     */
    @Override public void close() {
        if (!HELD.remove(this)) return;
        try { Files.deleteIfExists(pidFile(file)); } catch (IOException ignored) { }
        try { lock.release(); } catch (IOException ignored) { }
        try { channel.close(); } catch (IOException ignored) { }
    }
}
```

In `jasper-app/src/main/java/dev/jasper/app/platform/AppDirs.java` add, after the `daemonToken()` method (the three lines
`    public Path daemonToken() {` / `        return daemonDir().resolve("token");` / `    }`):

```java

    /** The instance lock: the process that holds it owns this home (workspace-tabs spec 5.1); its id is in {@code instance.pid} beside it. */
    public Path instanceLock() {
        return daemonDir().resolve("instance.lock");
    }
```

In `jasper-app/src/main/java/dev/jasper/app/residency/package-info.java` replace
` * Bounded interprocess protocol and endpoint workers. Bootstrap transfers endpoint close to application shutdown and process-hook backup.`
with
` * The instance lock, the bounded handoff protocol and its endpoint worker. The lock lives as long as the process; bootstrap transfers endpoint close to application shutdown and process-hook backup.`

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.residency.InstanceLockTest' --tests 'dev.jasper.app.platform.AppDirsTest' --tests 'dev.jasper.app.appearance.ThemeMigrationCoverageTest'`
Expected: PASS (`InstanceLockTest` 6, `AppDirsTest` all, the ledger unchanged). A child that prints nothing within 60 s
fails the test with `null`: read the child's standard error in the test output (it is inherited) before changing a
timeout.

- [ ] **Step 5: Commit**

```bash
git add jasper-app/src/main/java/dev/jasper/app/residency jasper-app/src/main/java/dev/jasper/app/platform/AppDirs.java \
  jasper-app/src/test/java/dev/jasper/app/testsupport/ChildJvm.java jasper-app/src/test/java/dev/jasper/app/residency \
  jasper-app/src/test/java/dev/jasper/app/platform/AppDirsTest.java
git commit -F - <<'EOF'
Single instance: the instance lock

InstanceLock takes <home>/daemon/instance.lock with FileChannel.tryLock
in an owner-only folder, holds it until close or the end of the process
and records the owner's process id in instance.pid. It never opens a
second channel on a file this process holds, since closing one would
drop the process's lock. Child-JVM tests prove a second process cannot
take it, a killed owner releases it and a waiter takes it once its owner
exits.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

---

### Task 2: Paths, `--replacing` and the launch table

Spec 5.2's table as a pure function, and the arguments it reads. `--standalone` stays until Task 7 removes the old flow
that still reads it. Rulings 10, 19.

**Files:**
- Modify: `jasper-app/src/main/java/dev/jasper/app/bootstrap/AppArguments.java` (whole file)
- Create: `jasper-app/src/main/java/dev/jasper/app/bootstrap/LaunchAction.java`
- Modify (test): `jasper-app/src/test/java/dev/jasper/app/bootstrap/AppArgumentsTest.java` (whole file)
- Create (test): `jasper-app/src/test/java/dev/jasper/app/bootstrap/LaunchActionTest.java`

**Interfaces:**
- Produces: `AppArguments` record components `(Path configOverride, boolean help, boolean background, boolean safeMode,
  Path pluginDir, boolean standalone, boolean homeOverride, boolean noWindow, OptionalLong replacing, Path workingDirectory,
  List<Path> paths, List<String> given)`; `List<String> startupFlags()` (present flags in the order `--safe-mode`,
  `--config`, `--plugin-dir`); `boolean residencyAllowed()` (`!homeOverride && startupFlags().isEmpty()`);
  `enum LaunchAction { OWN, HAND_OFF, EXIT, AWAIT_LOCK }` with `static LaunchAction of(boolean acquired, AppArguments options)`.
  Task 3 reads `given()`, Task 5 `workingDirectory()` and `paths()`, Task 6 everything, Task 7 removes `standalone`.

- [ ] **Step 1: Write the failing tests**

Replace `jasper-app/src/test/java/dev/jasper/app/bootstrap/AppArgumentsTest.java` with:

```java
package dev.jasper.app.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.nio.file.Path;
import java.util.OptionalLong;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AppArgumentsTest {
    @TempDir Path cwd;

    private AppArguments parse(String... args) { return AppArguments.parse(args, cwd); }

    private void rejected(String[]... cases) {
        for (String[] args : cases)
            assertThatIllegalArgumentException().as(java.util.Arrays.toString(args))
                .isThrownBy(() -> AppArguments.parse(args, cwd)).withMessageContaining("Usage:");
    }

    @Test void absentOverrideAndHelpAreParsedWithoutCreatingFiles() {
        AppArguments plain = parse();
        assertThat(plain.configOverride()).isNull();
        assertThat(plain.help()).isFalse();
        assertThat(plain.paths()).isEmpty();
        assertThat(plain.replacing()).isEmpty();
        assertThat(plain.workingDirectory()).isEqualTo(cwd);
        assertThat(plain.given()).isEmpty();
        assertThat(parse("--help").help()).isTrue();
        assertThat(cwd.resolve("config.toml")).doesNotExist();
    }

    @Test void relativeAndAbsoluteOverridesNormalizeAgainstWorkingDirectory() {
        assertThat(parse("--config", "nested/../config.toml").configOverride()).isEqualTo(cwd.resolve("config.toml"));
        AppArguments absolute = parse("--config", cwd.resolve("a/../other.toml").toString(), "--help");
        assertThat(absolute.configOverride()).isEqualTo(cwd.resolve("other.toml"));
        assertThat(absolute.help()).isTrue();
        assertThat(cwd.resolve("other.toml")).doesNotExist();
    }

    @Test void invalidMissingAndDuplicateArgumentsFailClearlyEvenWithHelp() {
        rejected(new String[]{"--wat"}, new String[]{"--config"}, new String[]{"--config", "--help"}, new String[]{"--config", ""},
            new String[]{"--config", "a", "--config", "b"}, new String[]{"--help", "--help"}, new String[]{"--help", "--wat"},
            new String[]{""}, new String[]{" "}, new String[]{"--replacing"}, new String[]{"--replacing", "x"},
            new String[]{"--replacing", "0"}, new String[]{"--replacing", "-3"}, new String[]{"--replacing", "--help"},
            new String[]{"--replacing", "1", "--replacing", "2"});
    }

    @Test void anArgumentThatIsNotAnOptionIsAPathResolvedAgainstTheWorkingDirectory() {
        String absolute = cwd.resolve("x.md").toString();
        AppArguments launch = parse("notes.txt", "--safe-mode", "src/../docs", absolute);
        assertThat(launch.paths()).containsExactly(cwd.resolve("notes.txt"), cwd.resolve("docs"), cwd.resolve("x.md"));
        assertThat(launch.safeMode()).isTrue();
        assertThat(launch.given()).containsExactly("notes.txt", "--safe-mode", "src/../docs", absolute);
        assertThat(parse("-psn_0_42").paths()).as("one dash is a path, not an option").containsExactly(cwd.resolve("-psn_0_42"));
        assertThat(AppArguments.USAGE).endsWith("[<path>...]");
    }

    @Test void replacingNamesTheProcessARestartReplacesAndStaysOutOfTheUsage() {
        assertThat(parse("--replacing", "4242", "notes.txt").replacing()).isEqualTo(OptionalLong.of(4242));
        assertThat(AppArguments.USAGE).doesNotContain("--replacing");
    }

    @Test void backgroundIsOffByDefaultAndParsesOnceAlongsideOtherOptions() {
        assertThat(parse().background()).isFalse();
        AppArguments login = parse("--config", "c.toml", "--background");
        assertThat(login.background()).isTrue();
        assertThat(login.configOverride()).isEqualTo(cwd.resolve("c.toml"));
        rejected(new String[]{"--background", "--background"}, new String[]{"--background", "--wat"});
        assertThat(AppArguments.USAGE).contains("--background");
    }

    @Test void pluginFlagsParseOnce() {
        AppArguments plain = parse();
        assertThat(plain.safeMode()).isFalse();
        assertThat(plain.pluginDir()).isNull();
        AppArguments dev = parse("--plugin-dir", "build/../out", "--safe-mode");
        assertThat(dev.pluginDir()).isEqualTo(cwd.resolve("out"));
        assertThat(dev.safeMode()).isTrue();
        assertThat(parse("--standalone").standalone()).isTrue();
        rejected(new String[]{"--safe-mode", "--safe-mode"}, new String[]{"--standalone", "--standalone"}, new String[]{"--plugin-dir"},
            new String[]{"--plugin-dir", "--help"}, new String[]{"--plugin-dir", ""}, new String[]{"--plugin-dir", "a", "--plugin-dir", "b"});
        assertThat(AppArguments.USAGE).contains("--safe-mode", "--plugin-dir <path>");
    }

    @Test void theStartUpFlagsAreWhatASecondLaunchReportsAndResidencyWantsNone() {
        assertThat(parse().startupFlags()).isEmpty();
        assertThat(parse("notes.txt", "--background").startupFlags()).isEmpty();
        assertThat(parse("--plugin-dir", "p", "--config", "c.toml", "--safe-mode").startupFlags())
            .containsExactly("--safe-mode", "--config", "--plugin-dir");
        assertThat(parse().residencyAllowed()).isTrue();
        assertThat(parse("notes.txt").residencyAllowed()).as("paths are no start-up flag").isTrue();
        assertThat(parse("--safe-mode").residencyAllowed()).isFalse();
        assertThat(parse().withHomeOverride(true).residencyAllowed()).as("another home").isFalse();
        AppArguments overridden = parse("notes.txt", "--replacing", "7").withHomeOverride(true);
        assertThat(overridden.homeOverride()).isTrue();
        assertThat(overridden.paths()).containsExactly(cwd.resolve("notes.txt"));
        assertThat(overridden.replacing()).isEqualTo(OptionalLong.of(7));
        assertThat(parse().withHomeOverride(false)).isEqualTo(parse());
    }

    @Test void noWindowParsesOnceAndNeverWithBackground() {
        assertThat(parse().noWindow()).isFalse();
        assertThat(parse("--no-window").noWindow()).isTrue();
        rejected(new String[]{"--no-window", "--no-window"}, new String[]{"--no-window", "--background"});
        assertThat(AppArguments.USAGE).contains("--no-window");
        assertThat(parse("--no-window").withHomeOverride(true).noWindow()).isTrue();
    }
}
```

Create `jasper-app/src/test/java/dev/jasper/app/bootstrap/LaunchActionTest.java`:

```java
package dev.jasper.app.bootstrap;

import static dev.jasper.app.bootstrap.LaunchAction.AWAIT_LOCK;
import static dev.jasper.app.bootstrap.LaunchAction.EXIT;
import static dev.jasper.app.bootstrap.LaunchAction.HAND_OFF;
import static dev.jasper.app.bootstrap.LaunchAction.OWN;
import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Spec 5.2: what a launch does, by whether it took the lock. */
class LaunchActionTest {
    @TempDir Path cwd;

    private record Row(List<String> args, LaunchAction free, LaunchAction held) { }

    @Test void theLaunchTable() {
        for (Row row : List.of(
                new Row(List.of(), OWN, HAND_OFF),
                new Row(List.of("notes.txt", "src"), OWN, HAND_OFF),
                new Row(List.of("--safe-mode"), OWN, HAND_OFF),
                new Row(List.of("--config", "c.toml"), OWN, HAND_OFF),
                new Row(List.of("--plugin-dir", "p", "notes.txt"), OWN, HAND_OFF),
                new Row(List.of("--background"), OWN, EXIT),
                new Row(List.of("--no-window"), OWN, EXIT),
                new Row(List.of("--replacing", "4242"), OWN, AWAIT_LOCK),
                new Row(List.of("--replacing", "4242", "--safe-mode", "notes.txt"), OWN, AWAIT_LOCK))) {
            AppArguments options = AppArguments.parse(row.args().toArray(String[]::new), cwd);
            assertThat(LaunchAction.of(true, options)).as("lock free: %s", row.args()).isEqualTo(row.free());
            assertThat(LaunchAction.of(false, options)).as("lock held: %s", row.args()).isEqualTo(row.held());
        }
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.bootstrap.AppArgumentsTest' --tests 'dev.jasper.app.bootstrap.LaunchActionTest'`
Expected: FAIL to compile: "cannot find symbol … paths()", "… replacing()", "… LaunchAction".

- [ ] **Step 3: The arguments and the table**

Replace `jasper-app/src/main/java/dev/jasper/app/bootstrap/AppArguments.java` with:

```java
package dev.jasper.app.bootstrap;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.OptionalLong;
import java.util.stream.Stream;

/**
 * Startup options parsed before any desktop initialization. An argument that does not start with {@code --} is a path
 * to open (workspace-tabs spec 5.4), resolved against the working directory.
 *
 * @param configOverride {@code --config}, or null
 * @param help {@code --help}
 * @param background {@code --background}: the login item's windowless start
 * @param safeMode {@code --safe-mode}
 * @param pluginDir {@code --plugin-dir}, or null
 * @param standalone {@code --standalone}
 * @param homeOverride whether the home came from {@code jasper.home} or {@code JASPER_HOME}
 * @param noWindow {@code --no-window}: the {@code jasper} command's windowless start
 * @param replacing {@code --replacing}: the process a restart's replacement waits for; not in the usage
 * @param workingDirectory the directory the launch ran in
 * @param paths the paths to open, absolute and normalized, in order
 * @param given the arguments as {@code main} received them, which a restart replays
 */
record AppArguments(Path configOverride, boolean help, boolean background, boolean safeMode, Path pluginDir,
                    boolean standalone, boolean homeOverride, boolean noWindow, OptionalLong replacing,
                    Path workingDirectory, List<Path> paths, List<String> given) {
    static final String USAGE = "Usage: jasper [--config <path>] [--background] [--no-window] [--safe-mode] "
        + "[--plugin-dir <path>] [--standalone] [--help] [<path>...]";

    AppArguments {
        Objects.requireNonNull(replacing, "replacing");
        Objects.requireNonNull(workingDirectory, "workingDirectory");
        paths = List.copyOf(paths);
        given = List.copyOf(given);
    }

    /** The form that predates residency: an ordinary foreground launch. */
    AppArguments(Path configOverride, boolean help) {
        this(configOverride, help, false);
    }

    /** The form that predates plugins: every plugin option off. */
    AppArguments(Path configOverride, boolean help, boolean background) {
        this(configOverride, help, background, false, null, false);
    }

    /** The form that predates the home override: the OS-default home. */
    AppArguments(Path configOverride, boolean help, boolean background, boolean safeMode, Path pluginDir, boolean standalone) {
        this(configOverride, help, background, safeMode, pluginDir, standalone, false);
    }

    /** The form that predates {@code --no-window}. */
    AppArguments(Path configOverride, boolean help, boolean background, boolean safeMode, Path pluginDir, boolean standalone,
                 boolean homeOverride) {
        this(configOverride, help, background, safeMode, pluginDir, standalone, homeOverride, false);
    }

    /** The form that predates paths: a launch in the current directory that names none. */
    AppArguments(Path configOverride, boolean help, boolean background, boolean safeMode, Path pluginDir, boolean standalone,
                 boolean homeOverride, boolean noWindow) {
        this(configOverride, help, background, safeMode, pluginDir, standalone, homeOverride, noWindow, OptionalLong.empty(),
            Path.of("").toAbsolutePath(), List.of(), List.of());
    }

    /** Records whether the application home was overridden ({@code jasper.home} or {@code JASPER_HOME}). */
    AppArguments withHomeOverride(boolean value) {
        return new AppArguments(configOverride, help, background, safeMode, pluginDir, standalone, value, noWindow, replacing,
            workingDirectory, paths, given);
    }

    /**
     * A standalone launch never hands off to a resident process, never binds the shared endpoint and
     * is never resident: it is a different Jasper (another home, config or plugin set) or a recovery
     * launch that must not reopen the process it is escaping.
     */
    boolean standaloneLaunch() {
        return configOverride != null || safeMode || pluginDir != null || standalone || homeOverride;
    }

    /**
     * A launch whose configuration or plugin set is not the installed app's serves its own command socket, which only
     * its own shells reach. A home override alone does not: its socket already lives in its own home.
     */
    boolean privateCommands() {
        return configOverride != null || safeMode || pluginDir != null || standalone;
    }

    /**
     * The start-up flags this launch carries: what a second launch reports to the owner as ignored (spec 5.2).
     *
     * @return {@code --safe-mode}, {@code --config} and {@code --plugin-dir}, those present, in that order
     */
    List<String> startupFlags() {
        return Stream.of(safeMode ? "--safe-mode" : null, configOverride != null ? "--config" : null,
            pluginDir != null ? "--plugin-dir" : null).filter(Objects::nonNull).toList();
    }

    /** Residency applies only to the default home launched without start-up flags (spec 5.2). */
    boolean residencyAllowed() {
        return !homeOverride && startupFlags().isEmpty();
    }

    static AppArguments parse(String[] args, Path workingDirectory) {
        Path override = null;
        Path pluginDir = null;
        boolean help = false, background = false, safeMode = false, standalone = false, noWindow = false;
        OptionalLong replacing = OptionalLong.empty();
        Stream.Builder<Path> paths = Stream.builder();
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--help" -> {
                    if (help) throw invalid("Duplicate --help option.");
                    help = true;
                }
                case "--background" -> {
                    if (background) throw invalid("Duplicate --background option.");
                    background = true;
                }
                case "--no-window" -> {
                    if (noWindow) throw invalid("Duplicate --no-window option.");
                    noWindow = true;
                }
                case "--safe-mode" -> {
                    if (safeMode) throw invalid("Duplicate --safe-mode option.");
                    safeMode = true;
                }
                case "--standalone" -> {
                    if (standalone) throw invalid("Duplicate --standalone option.");
                    standalone = true;
                }
                case "--config" -> {
                    if (override != null) throw invalid("Duplicate --config option.");
                    override = path(args, ++i, "--config", "a file path", workingDirectory);
                }
                case "--plugin-dir" -> {
                    if (pluginDir != null) throw invalid("Duplicate --plugin-dir option.");
                    pluginDir = path(args, ++i, "--plugin-dir", "a directory path", workingDirectory);
                }
                case "--replacing" -> {
                    if (replacing.isPresent()) throw invalid("Duplicate --replacing option.");
                    replacing = OptionalLong.of(processId(args, ++i));
                }
                default -> {
                    if (args[i].startsWith("--")) throw invalid("Unknown argument.");
                    paths.add(positional(args[i], workingDirectory));
                }
            }
        }
        if (noWindow && background) throw invalid("--no-window and --background cannot be combined.");
        return new AppArguments(override, help, background, safeMode, pluginDir, standalone, false, noWindow, replacing,
            workingDirectory, paths.build().toList(), List.of(args));
    }

    private static Path path(String[] args, int index, String option, String what, Path workingDirectory) {
        if (index >= args.length || args[index].isBlank() || args[index].startsWith("--"))
            throw invalid(option + " requires " + what + ".");
        try { return workingDirectory.resolve(Path.of(args[index])).toAbsolutePath().normalize(); }
        catch (InvalidPathException ignored) { throw invalid(option + " requires a valid path."); }
    }

    /** A path to open, as the launch names it (spec 5.4). */
    private static Path positional(String text, Path workingDirectory) {
        if (text.isBlank()) throw invalid("A path cannot be blank.");
        try { return workingDirectory.resolve(text).toAbsolutePath().normalize(); }
        catch (InvalidPathException ignored) { throw invalid("Invalid path: " + text); }
    }

    private static long processId(String[] args, int index) {
        if (index < args.length) {
            try {
                long pid = Long.parseLong(args[index]);
                if (pid > 0) return pid;
            } catch (NumberFormatException ignored) {
                // Reported below.
            }
        }
        throw invalid("--replacing requires a process id.");
    }

    private static IllegalArgumentException invalid(String message) {
        return new IllegalArgumentException(message + "\n" + USAGE);
    }
}
```

Create `jasper-app/src/main/java/dev/jasper/app/bootstrap/LaunchAction.java`:

```java
package dev.jasper.app.bootstrap;

/** What a launch does once it knows whether it took its home's instance lock (workspace-tabs spec 5.2). Pure. */
enum LaunchAction {
    /** This process owns the home and starts: a window, its paths, its flags, a login warm-up or a windowless server. */
    OWN,
    /** Hands its request (RAISE or OPEN_PATHS, with the start-up flags it cannot apply) to the owner and exits 0. */
    HAND_OFF,
    /** Exits 0: a login launch, or the {@code jasper} command's windowless start, while an owner runs. */
    EXIT,
    /** Restart's replacement: waits for the lock up to 30 s. */
    AWAIT_LOCK;

    /**
     * Spec 5.2's table.
     *
     * @param acquired whether this launch took the lock
     * @param options its arguments
     * @return what it does
     */
    static LaunchAction of(boolean acquired, AppArguments options) {
        if (acquired) return OWN;
        if (options.replacing().isPresent()) return AWAIT_LOCK;
        if (options.background() || options.noWindow()) return EXIT;
        return HAND_OFF;
    }
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.bootstrap.*' --tests 'dev.jasper.app.appearance.ThemeMigrationCoverageTest'`
Expected: PASS: `AppArgumentsTest` (9), `LaunchActionTest` (1) and the rest of the package unchanged (the legacy forms
still compile for `ApplicationBootstrapTest`).

- [ ] **Step 5: Commit**

```bash
git add jasper-app/src/main/java/dev/jasper/app/bootstrap/AppArguments.java jasper-app/src/main/java/dev/jasper/app/bootstrap/LaunchAction.java \
  jasper-app/src/test/java/dev/jasper/app/bootstrap/AppArgumentsTest.java jasper-app/src/test/java/dev/jasper/app/bootstrap/LaunchActionTest.java
git commit -F - <<'EOF'
Single instance: launch paths, --replacing and the launch table

An argument that is not an option is a path, resolved against the
working directory; --replacing <pid> names the process a restart's
replacement waits for. AppArguments keeps the arguments main received,
reports its start-up flags and says whether residency applies.
LaunchAction is spec 5.2's table as a pure function.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

---

### Task 3: Restart is a replacement that waits

Spec 5.3 "Restart" and 16: the replacement gets `--replacing <pid>`; nothing probes, retires or goes standalone.
`ResidentControl`, RETIRE and `RestartMode.STANDALONE` go. Rulings 8, 9.

**Files:**
- Modify: `jasper-app/src/main/java/dev/jasper/app/restart/RestartCommand.java`, `RestartFlow.java`, `RestartMode.java` (whole files), `package-info.java:2-5`
- Delete: `jasper-app/src/main/java/dev/jasper/app/restart/ResidentControl.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/residency/LaunchRequest.java` (whole file), `HandoffSocket.java:113-161`
- Modify: `jasper-app/src/main/java/dev/jasper/app/pluginmanager/PluginManager.java:4,25-46,111-122,136-158`,
  `PluginManagerPanel.java:23,50,142-143,203-204,233,288`
- Modify: `jasper-app/src/main/java/dev/jasper/app/application/JasperApplication.java:67-70,159-176,1663-1708,1745-1757,1947`
- Modify: `jasper-app/src/main/java/dev/jasper/app/bootstrap/ApplicationBootstrap.java:41,334-338,611-623,640-661`
- Modify: `jasper-sdk/src/main/java/dev/jasper/sdk/ui/Windows.java:41` (the `onQuitting` Javadoc: what really quits)
- Modify (test): `restart/RestartCommandTest.java`, `restart/RestartFlowTest.java` (whole files);
  `residency/LaunchRequestTest.java:53-64`, `residency/HandoffSocketTest.java:244-256`;
  `pluginmanager/PluginManagerTest.java`, `pluginmanager/PluginManagerPanelTest.java:313-337`,
  `pluginmanager/PluginManagerStatesPreview.java`,
  `settingsui/EnterpriseSettings.java`, `settingsui/PluginSettingsPagesEvidenceTest.java`, `settingsui/Settings9bPreview.java`,
  `settingsui/SettingsDialogPreview.java`; `application/JasperApplicationPluginsTest.java:215-248`,
  `application/JasperApplicationBackgroundTest.java:644`, `application/PresentationAttemptTest.java:184`,
  `application/JasperApplicationResidencyTest.java:194-202`; `bootstrap/ApplicationBootstrapTest.java:211-222`

**Interfaces:**
- Consumes: `AppArguments.given()` (Task 2).
- Produces: `enum RestartMode { SAME, NORMAL }`; `RestartCommand.plan(Optional<String> command, Optional<String[]> arguments,
  List<String> launch, RestartMode mode): Optional<List<String>>`, `current(RestartMode mode, List<String> launch)`,
  `replacing(List<String> planned, long pid, List<Path> paths): List<String>`, `spawn(List<String>)`;
  `RestartFlow(Predicate<RestartMode> restart)` with `State { IDLE, UNAVAILABLE }`, `state()`, `onChanged(Runnable)`,
  `restartNow()`, `restartNormally()`, `cancel()`; `PluginManager.Hooks(chooseZip, restarts, quit, worker, openInEditor,
  reveal, browse)`; `JasperApplication.restart(RestartMode mode, List<Path> paths): boolean` (and `restart(mode)` = no
  paths), `public void launchArguments(List<String> arguments)`; `LaunchRequest(String token, Path codeLocation, long
  codeLocationModified)` until Task 5 replaces it. Task 5 calls `restart(RestartMode.SAME, paths)`.

- [ ] **Step 1: Write the failing tests**

First, while the tree still compiles, record the Plugins page's 9b comparison (headless; Step 5 repeats it once the
notice line has gone):

```bash
./gradlew :jasper-app:settings9bPreview
python3 -I tools/visual/compare_all.py build/visual-check settings-plugins
```

Keep the `settings-plugins:` line it prints.

Replace `jasper-app/src/test/java/dev/jasper/app/restart/RestartCommandTest.java` with:

```java
package dev.jasper.app.restart;

import static dev.jasper.app.restart.RestartMode.NORMAL;
import static dev.jasper.app.restart.RestartMode.SAME;
import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

class RestartCommandTest {
    private static final String JASPER = "/Applications/Jasper.app/Contents/MacOS/Jasper";

    /** A packaged launcher: the operating system reports exactly the arguments main received. */
    private static Optional<List<String>> plan(RestartMode mode, String... arguments) {
        return RestartCommand.plan(Optional.of(JASPER), Optional.of(arguments), List.of(arguments), mode);
    }

    @Test void theSameLaunchKeepsItsFlagsButNeverComesBackAsABackgroundProcess() {
        assertThat(plan(SAME, "--config", "/tmp/my config.toml", "--background", "--plugin-dir", "/dev/plugin")).contains(
            List.of(JASPER, "--config", "/tmp/my config.toml", "--plugin-dir", "/dev/plugin"));
        assertThat(plan(SAME, "--safe-mode")).contains(List.of(JASPER, "--safe-mode"));
        assertThat(plan(SAME)).contains(List.of(JASPER));
    }

    @Test void restartingNormallyLeavesSafeMode() {
        assertThat(plan(NORMAL, "--safe-mode", "--background")).contains(List.of(JASPER));
    }

    @Test void theLaunchsPathsAndAnEarlierReplacingAreNotRepeated() {
        assertThat(plan(SAME, "--config", "notes.txt", "/tmp/a.txt", "src", "--replacing", "4242"))
            .as("a config file may be named like a path; the paths after it are the launch's").contains(List.of(JASPER, "--config", "notes.txt"));
    }

    @Test void aJavaCommandLineIsReplayedWholeWithItsOwnArgumentsRewrittenAfterIt() {
        var java = RestartCommand.plan(Optional.of("/jbr/bin/java"), Optional.of(new String[]{"-Xmx1g", "-classpath", "/lib/*",
            "dev.jasper.app.Main", "notes.txt", "--safe-mode"}), List.of("notes.txt", "--safe-mode"), NORMAL);
        assertThat(java).contains(List.of("/jbr/bin/java", "-Xmx1g", "-classpath", "/lib/*", "dev.jasper.app.Main"));
    }

    @Test void argumentsThatDoNotEndWithTheLaunchsLoseOnlyTheFlagsBySpelling() {
        var other = RestartCommand.plan(Optional.of("/app/Jasper"), Optional.of(new String[]{"-Dx=1", "Main", "--background",
            "--replacing", "7", "--safe-mode", "kept"}), List.of("missing"), NORMAL);
        assertThat(other).contains(List.of("/app/Jasper", "-Dx=1", "Main", "kept"));
    }

    @Test void theReplacementWaitsForThisProcessAndOpensThePaths() {
        Path notes = Path.of("/tmp/notes.txt").toAbsolutePath();
        assertThat(RestartCommand.replacing(List.of(JASPER, "--safe-mode"), 4242, List.of(notes)))
            .containsExactly(JASPER, "--safe-mode", "--replacing", "4242", notes.toString());
        assertThat(RestartCommand.replacing(List.of(JASPER), 7, List.of())).containsExactly(JASPER, "--replacing", "7");
    }

    @DisabledOnOs(OS.WINDOWS)
    @Test void theRunningJvmReportsItsOwnCommandLine() {
        assertThat(RestartCommand.current(SAME, List.of())).as("macOS and Linux report a process's own arguments")
            .hasValueSatisfying(line -> assertThat(line).hasSizeGreaterThan(1).doesNotContain("--background"));
    }

    @Test void anUnknownCommandLineCannotBeRestarted() {
        assertThat(RestartCommand.plan(Optional.empty(), Optional.of(new String[0]), List.of(), SAME)).isEmpty();
        assertThat(RestartCommand.plan(Optional.of("/bin/jasper"), Optional.empty(), List.of(), SAME)).isEmpty();
        assertThat(RestartCommand.plan(Optional.of(" "), Optional.of(new String[0]), List.of(), SAME)).isEmpty();
    }

    @Test void aRestartComesBackWithAWindow() {
        assertThat(plan(SAME, "--no-window")).contains(List.of(JASPER));
    }
}
```

Replace `jasper-app/src/test/java/dev/jasper/app/restart/RestartFlowTest.java` with:

```java
package dev.jasper.app.restart;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class RestartFlowTest {
    private final List<RestartMode> restarts = new ArrayList<>();
    private final List<RestartFlow.State> states = new ArrayList<>();
    private boolean restartable = true;

    private RestartFlow flow() {
        var flow = new RestartFlow(mode -> { restarts.add(mode); return restartable; });
        flow.onChanged(() -> states.add(flow.state()));
        return flow;
    }

    @Test void restartNowKeepsTheLaunchAndAnUnknownCommandLineIsSaidSo() {
        RestartFlow flow = flow();
        flow.restartNow();
        assertThat(restarts).containsExactly(RestartMode.SAME);
        assertThat(flow.state()).isEqualTo(RestartFlow.State.IDLE);
        restartable = false;
        flow.restartNow();
        assertThat(flow.state()).isEqualTo(RestartFlow.State.UNAVAILABLE);
    }

    @Test void restartingNormallyLeavesSafeModeAtOnce() {
        RestartFlow flow = flow();
        flow.restartNormally();
        assertThat(restarts).containsExactly(RestartMode.NORMAL);
        assertThat(states).containsExactly(RestartFlow.State.IDLE);
    }

    @Test void cancelForgetsAnUnavailableRestart() {
        restartable = false;
        RestartFlow flow = flow();
        flow.restartNormally();
        flow.cancel();
        assertThat(states).containsExactly(RestartFlow.State.UNAVAILABLE, RestartFlow.State.IDLE);
    }
}
```

Then run from the worktree root (the tests that pin today's resident conversation and `--standalone` replacement are
replaced by the ones above and below):

```bash
python3 -I - <<'PY'
"""Workspace tabs 1, Task 3 step 1: tests of a restart whose replacement waits for the lock."""
import pathlib
def edit(name, edits):
    path = pathlib.Path(name); text = path.read_text(encoding="utf-8")
    for old, new, count in edits:
        if text.count(old) != count: raise SystemExit(f"{name}: expected {count} of {old!r}, found {text.count(old)}")
        text = text.replace(old, new)
    path.write_text(text, encoding="utf-8")
def cut(name, start, stop, replacement=""):
    path = pathlib.Path(name); text = path.read_text(encoding="utf-8")
    if text.count(start) != 1 or text.count(stop) != 1: raise SystemExit(f"{name}: anchors {start!r} / {stop!r} not unique")
    a = text.index(start); b = text.index(stop)
    if b < a: raise SystemExit(f"{name}: {stop!r} comes before {start!r}")
    path.write_text(text[:a] + replacement + text[b:], encoding="utf-8")
T = "jasper-app/src/test/java/dev/jasper/app/"
edit(T + "residency/LaunchRequestTest.java", [
    ('''    @org.junit.jupiter.api.condition.DisabledOnOs(org.junit.jupiter.api.condition.OS.WINDOWS)
    @Test void anOpenRequestIsByteIdenticalToTheOldProtocolAndARetireAddsOneField() {
        var open = new LaunchRequest("t0ken", Path.of("/app.jar"), 7L);
        assertThat(open.kind()).isEqualTo(LaunchRequest.Kind.OPEN);
        assertThat(open.encode()).isEqualTo("jasper\\t1\\tt0ken\\tL2FwcC5qYXI=\\t7\\n");
        var retire = new LaunchRequest("t0ken", Path.of("/app.jar"), 7L, LaunchRequest.Kind.RETIRE);
        assertThat(retire.encode()).isEqualTo("jasper\\t1\\tt0ken\\tL2FwcC5qYXI=\\t7\\tretire\\n");
        assertThat(LaunchRequest.decode(retire.encode())).isEqualTo(retire);
        assertThat(LaunchRequest.decode(open.encode())).isEqualTo(open);
        assertThat(LaunchRequest.decode("jasper\\t1\\tt0ken\\tL2FwcC5qYXI=\\t7\\tdance\\n")).as("an unknown kind is not an open request").isNull();
        assertThat(LaunchRequest.decode("jasper\\t1\\tt0ken\\tL2FwcC5qYXI=\\t7\\tretire\\textra\\n")).isNull();
    }''',
     '''    @org.junit.jupiter.api.condition.DisabledOnOs(org.junit.jupiter.api.condition.OS.WINDOWS)
    @Test void aRequestIsFiveFieldsAndASixthIsNotUnderstood() {
        var open = new LaunchRequest("t0ken", Path.of("/app.jar"), 7L);
        assertThat(open.encode()).isEqualTo("jasper\\t1\\tt0ken\\tL2FwcC5qYXI=\\t7\\n");
        assertThat(LaunchRequest.decode(open.encode())).isEqualTo(open);
        assertThat(LaunchRequest.decode("jasper\\t1\\tt0ken\\tL2FwcC5qYXI=\\t7\\tretire\\n")).isNull();
    }''', 1)])
edit(T + "residency/HandoffSocketTest.java", [
    ('''    @Test void aRetireReachesTheHandlerAsARetireAndLivenessFollowsTheEndpoint() throws Exception {
        List<LaunchRequest> seen = new CopyOnWriteArrayList<>();
        assertThat(HandoffSocket.live(socket())).as("nothing bound yet").isFalse();
        try (HandoffSocket endpoint = bind(request -> { seen.add(request); return LaunchRequest.Response.OK; })) {
            assertThat(HandoffSocket.live(socket())).isTrue();
            assertThat(HandoffSocket.retire(socket(), token(), Path.of("/app.jar"), 7L)).isTrue();
            assertThat(seen).singleElement().satisfies(request -> assertThat(request.kind()).isEqualTo(LaunchRequest.Kind.RETIRE));
            assertThat(HandoffSocket.handOff(socket(), token(), Path.of("/app.jar"), 7L)).isTrue();
            assertThat(seen.get(1).kind()).isEqualTo(LaunchRequest.Kind.OPEN);
        }
        assertThat(HandoffSocket.live(socket())).isFalse();
        assertThat(HandoffSocket.retire(socket(), token(), Path.of("/app.jar"), 7L)).as("nobody to ask").isFalse();
    }''',
     '''    @Test void livenessFollowsTheEndpoint() throws Exception {
        assertThat(HandoffSocket.live(socket())).as("nothing bound yet").isFalse();
        try (HandoffSocket endpoint = bind(request -> LaunchRequest.Response.OK)) {
            assertThat(HandoffSocket.live(socket())).isTrue();
        }
        assertThat(HandoffSocket.live(socket())).isFalse();
    }''', 1)])
cut(T + "application/JasperApplicationResidencyTest.java",
    '''    @Test void aRetireRequestRunsTheNormalQuitPathEvenFromAnotherBuild() throws Exception {''', '''\n}\n''')
edit(T + "application/JasperApplicationResidencyTest.java", [('''    }\n\n\n}\n''', '''    }\n}\n''', 1)])
cut(T + "bootstrap/ApplicationBootstrapTest.java",
    '''    @Test void onlyAPlainReplacementCanHandOffAndOnlyAPureStandaloneLaunchExplainsItself() {''',
    '''    @Test void onlyAPackagedMacLoginLaunchStartsHiddenFromTheDock() throws Exception {''')
PID = '''Long.toString(ProcessHandle.current().pid())'''
edit(T + "application/JasperApplicationBackgroundTest.java", [
    ('''        assertThat(spawned).as("and starts the new one").containsExactly(List.of("/Applications/Jasper.app/Contents/MacOS/Jasper"));''',
     '''        assertThat(spawned).as("and starts the new one, waiting for this process's lock")
            .containsExactly(List.of("/Applications/Jasper.app/Contents/MacOS/Jasper", "--replacing", ''' + PID + '''));''', 1)])
edit(T + "application/PresentationAttemptTest.java", [
    ('''            assertThat(spawned).containsExactly("jasper SAME");''',
     '''            assertThat(spawned).containsExactly("jasper SAME --replacing " + ProcessHandle.current().pid());''', 1)])
edit(T + "application/JasperApplicationPluginsTest.java", [
    ('''        assertThat(order).containsExactly("endpoint released", "spawn [jasper, SAME]", "terminate");''',
     '''        assertThat(order).containsExactly("endpoint released", "spawn [jasper, SAME, --replacing, " + ProcessHandle.current().pid() + "]",
            "terminate");''', 1),
    ('''    @Test void aReplacementForAnEndpointOwnerWhoseCleanupHangsCanNeverHandOff() throws Exception {''',
     '''    @Test void aReplacementStartsEvenWhenCleanupHangsAndWaitsForTheLock() throws Exception {''', 1),
    ('''            assertThat(spawned).containsExactly("jasper --standalone");''',
     '''            assertThat(spawned).containsExactly("jasper --replacing " + ProcessHandle.current().pid());''', 1)])
edit(T + "pluginmanager/PluginManagerTest.java", [
    ('''import dev.jasper.app.restart.ResidentControl;\n''', '', 1),
    ('''    private SettingsPage page;\n    private AuxiliarySurface host;\n    private boolean residentLive;\n''',
     '''    private SettingsPage page;\n    private AuxiliarySurface host;\n''', 1),
    ('''    private void start(boolean safeMode, boolean standaloneNotice) throws Exception {''',
     '''    private void start(boolean safeMode) throws Exception {''', 1),
    ('''            var control = new ResidentControl(() -> residentLive, () -> false);
            var flow = new RestartFlow(control, mode -> { restarts.add(mode); return true; }, Runnable::run, Runnable::run,
                Duration.ofMillis(20), Duration.ofMillis(1));
            manager = new PluginManager(runtime, windows, new PluginManager.Hooks(surface -> Optional.ofNullable(chosenZip), flow,
                () -> quits.add("quit"), control, standaloneNotice, Runnable::run, edited::add, revealed::add, uri -> { }));''',
     '''            var flow = new RestartFlow(mode -> { restarts.add(mode); return true; });
            manager = new PluginManager(runtime, windows, new PluginManager.Hooks(surface -> Optional.ofNullable(chosenZip), flow,
                () -> quits.add("quit"), Runnable::run, edited::add, revealed::add, uri -> { }));''', 1),
    ('''start(false, false);''', '''start(false);''', 8)])
edit(T + "pluginmanager/PluginManagerPanelTest.java", [
    ('''    @Test void theBannerTheNoticeAndTheMessageAreIndependent() {''',
     '''    @Test void theBannerAndTheMessageAreIndependentAndTheHeaderHasNoNoticeLine() {''', 1),
    ('''        panel.notice("Another Jasper process is still running.");
        panel.message("The zip holds no jar files", true);
        assertThat(panel.noticeText()).isEqualTo("Another Jasper process is still running.");
''', '''        panel.message("The zip holds no jar files", true);
''', 1),
    ('''        assertThat(KitTestSupport.require(PluginKit.laidOut(panel.headerView()), "banner", javax.swing.JComponent.class).isVisible()).isFalse();
        assertThat(panel.noticeText()).isNotEmpty();
    }''',
     '''        assertThat(KitTestSupport.require(PluginKit.laidOut(panel.headerView()), "banner", javax.swing.JComponent.class).isVisible()).isFalse();
        assertThat(KitTestSupport.find(PluginKit.laidOut(panel.headerView()), "notice", java.awt.Component.class)).isNull();
    }''', 1)])
cut(T + "pluginmanager/PluginManagerTest.java",
    '''    @Test void safeModeOffersRestartNormallyAndWalksTheResidentConversation() throws Exception {''',
    '''        @Test void removingAsksFirstAndTheOpenersGoThroughTheHooks() throws Exception {''',
    '''    @Test void safeModeOffersRestartNormallyWhichRestartsWithoutSafeMode() throws Exception {
        start(true);
        open();
        until(() -> manager.panel().bannerText().startsWith("Safe mode"));
        edt(() -> {
            assertThat(bannerButtons()).containsExactly("Restart Normally");
            pressBanner(0);
        });
        assertThat(restarts).containsExactly(RestartMode.NORMAL);
    }

''')
S = T + "settingsui/"
edit(S + "EnterpriseSettings.java", [
    ('''import dev.jasper.app.restart.ResidentControl;\n''', '', 1),
    ('''                var flow = new RestartFlow(ResidentControl.NONE, mode -> true, Runnable::run, Runnable::run, Duration.ofMillis(20), Duration.ofMillis(1));
                var manager = new PluginManager(runtime[0], windows, new PluginManager.Hooks(surface -> Optional.empty(), flow, () -> { },
                    ResidentControl.NONE, false, Runnable::run, path -> { }, path -> { }, uri -> { }));''',
     '''                var flow = new RestartFlow(mode -> true);
                var manager = new PluginManager(runtime[0], windows, new PluginManager.Hooks(surface -> Optional.empty(), flow, () -> { },
                    Runnable::run, path -> { }, path -> { }, uri -> { }));''', 1)])
edit(S + "PluginSettingsPagesEvidenceTest.java", [
    ('''import dev.jasper.app.restart.ResidentControl;\n''', '', 1),
    ('''                var restarts = new RestartFlow(ResidentControl.NONE, mode -> true, Runnable::run, Runnable::run, Duration.ofMillis(20), Duration.ofMillis(1));
                manager[0] = new PluginManager(runtime[0], windows, new PluginManager.Hooks(surface -> Optional.empty(), restarts, () -> { }, ResidentControl.NONE,
                    false, Runnable::run, path -> { }, path -> { }, uri -> { }));''',
     '''                var restarts = new RestartFlow(mode -> true);
                manager[0] = new PluginManager(runtime[0], windows, new PluginManager.Hooks(surface -> Optional.empty(), restarts, () -> { },
                    Runnable::run, path -> { }, path -> { }, uri -> { }));''', 1)])
edit(S + "Settings9bPreview.java", [
    ('''                var restarts=new RestartFlow(ResidentControl.NONE,mode->true,Runnable::run,Runnable::run,Duration.ofMillis(20),Duration.ofMillis(1));
                manager[0]=new PluginManager(runtime[0],windows,new PluginManager.Hooks(surface->Optional.empty(),restarts,()->{},ResidentControl.NONE,false,Runnable::run,path->{},path->{},uri->{}));''',
     '''                var restarts=new RestartFlow(mode->true);
                manager[0]=new PluginManager(runtime[0],windows,new PluginManager.Hooks(surface->Optional.empty(),restarts,()->{},Runnable::run,path->{},path->{},uri->{}));''', 1)])
edit(S + "SettingsDialogPreview.java", [
    ('''import dev.jasper.app.restart.ResidentControl;\n''', '', 1),
    ('''            var flow = new RestartFlow(ResidentControl.NONE, mode -> true, Runnable::run, Runnable::run,
                Duration.ofMillis(20), Duration.ofMillis(1));
            manager[0] = new PluginManager(runtime[0], windows, new PluginManager.Hooks(surface -> Optional.empty(), flow, () -> { },
                ResidentControl.NONE, false, Runnable::run, path -> { }, path -> { }, uri -> { }));''',
     '''            var flow = new RestartFlow(mode -> true);
            manager[0] = new PluginManager(runtime[0], windows, new PluginManager.Hooks(surface -> Optional.empty(), flow, () -> { },
                Runnable::run, path -> { }, path -> { }, uri -> { }));''', 1)])
edit(T + "pluginmanager/PluginManagerStatesPreview.java", [
    ('''var flow=new RestartFlow[1];var workers=new ArrayDeque<Runnable>();''', '''var flow=new RestartFlow[1];''', 1),
    ('''                var resident=new ResidentControl(()->true,()->false);
                flow[0]=new RestartFlow(resident,mode->false,workers::add,Runnable::run,Duration.ofMillis(20),Duration.ofMillis(1));
                manager[0]=new PluginManager(runtime[0],windows[0],new PluginManager.Hooks(surface->Optional.empty(),flow[0],()->{},resident,true,Runnable::run,path->{},path->{},uri->{}));''',
     '''                flow[0]=new RestartFlow(mode->false);
                manager[0]=new PluginManager(runtime[0],windows[0],new PluginManager.Hooks(surface->Optional.empty(),flow[0],()->{},Runnable::run,path->{},path->{},uri->{}));''', 1),
    ('''                    flow[0].restartNormally();save(panel,dir.resolve("restart-probing.png"),744,600);
                    workers.remove().run();save(panel,dir.resolve("restart-resident-found.png"),744,600);
                    flow[0].askResidentToQuit();save(panel,dir.resolve("restart-waiting.png"),744,600);
                    workers.remove().run();save(panel,dir.resolve("restart-stuck.png"),744,600);save(panel,dir.resolve("restart-stuck-narrow.png"),440,400);
                    flow[0].launchAnyway();save(panel,dir.resolve("restart-unavailable.png"),744,600);flow[0].cancel();''',
     '''                    flow[0].restartNormally();save(panel,dir.resolve("restart-unavailable.png"),744,600);flow[0].cancel();''', 1)])
PY
```

`JasperApplicationResidencyTest`'s retire test is its last; the script cuts it up to the class's closing brace and tidies
the blank lines it leaves. The unused `java.time.Duration` and `ArrayDeque` imports the edits leave are harmless.
`PluginManagerPanelTest`'s banner test loses its notice lines and proves the header has no notice node (Ruling 9).

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :jasper-app:compileTestJava`
Expected: FAIL to compile: "cannot find symbol … replacing(…)", "constructor RestartFlow … cannot be applied", "constructor
Hooks … cannot be applied".

- [ ] **Step 3: The restart package**

Replace `jasper-app/src/main/java/dev/jasper/app/restart/RestartMode.java` with:

```java
package dev.jasper.app.restart;

/** How the replacement process is launched. */
public enum RestartMode {
    /** The same launch again: its flags kept, except {@code --background} and {@code --no-window}, without its paths. */
    SAME,
    /** Leaving safe mode: {@code --safe-mode} is dropped too. */
    NORMAL
}
```

Replace `jasper-app/src/main/java/dev/jasper/app/restart/RestartCommand.java` with:

```java
package dev.jasper.app.restart;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** The replacement's command line, derived from this process's own. Pure except {@link #current} and {@link #spawn}. */
public final class RestartCommand {
    private RestartCommand() { }

    /**
     * Plans the replacement from a command and its arguments as the operating system reports them. When they end with
     * {@code launch}, the arguments Jasper's {@code main} received, those are replayed: {@code --config} and
     * {@code --plugin-dir} with their values, and {@code --safe-mode} when {@code mode} keeps it; {@code --background},
     * {@code --no-window}, an earlier {@code --replacing} and the launch's paths are dropped. Otherwise (a platform that
     * reports something else) the flags are dropped by their spelling alone, since nothing there tells a path from a JVM
     * option.
     *
     * @param command the executable, when known
     * @param arguments everything after it, when known; for a Java launch that includes JVM options and the main class
     * @param launch the arguments this process's {@code main} received
     * @param mode which flags the replacement keeps
     * @return the full command line, or empty when it cannot be determined
     */
    public static Optional<List<String>> plan(Optional<String> command, Optional<String[]> arguments, List<String> launch,
                                              RestartMode mode) {
        if (command.isEmpty() || command.get().isBlank() || arguments.isEmpty()) return Optional.empty();
        List<String> reported = List.of(arguments.get());
        int split = reported.size() - launch.size();
        boolean tail = !launch.isEmpty() && split >= 0 && reported.subList(split, reported.size()).equals(launch);
        List<String> line = new ArrayList<>();
        line.add(command.get());
        if (tail) {
            line.addAll(reported.subList(0, split));
            line.addAll(kept(launch, mode, true));
        } else {
            line.addAll(kept(reported, mode, false));
        }
        return Optional.of(List.copyOf(line));
    }

    /** {@code arguments} without what a replacement must not repeat; {@code own}: they are all Jasper's, so the rest are paths. */
    private static List<String> kept(List<String> arguments, RestartMode mode, boolean own) {
        List<String> kept = new ArrayList<>();
        for (int i = 0; i < arguments.size(); i++) {
            String argument = arguments.get(i);
            switch (argument) {
                case "--config", "--plugin-dir" -> {
                    kept.add(argument);
                    if (i + 1 < arguments.size()) kept.add(arguments.get(++i));
                }
                case "--replacing" -> i++;
                case "--background", "--no-window", "--help" -> { }
                case "--safe-mode" -> {
                    if (mode == RestartMode.SAME) kept.add(argument);
                }
                default -> {
                    if (!own) kept.add(argument);
                }
            }
        }
        return kept;
    }

    /**
     * The replacement for the running process.
     *
     * @param mode which flags the replacement keeps
     * @param launch the arguments this process's {@code main} received
     * @return the full command line, or empty when the platform does not report it
     */
    public static Optional<List<String>> current(RestartMode mode, List<String> launch) {
        ProcessHandle.Info info = ProcessHandle.current().info();
        return plan(info.command(), info.arguments(), launch, mode);
    }

    /**
     * A planned command line as the replacement for process {@code pid} (workspace-tabs spec 5.3): it waits for that
     * process's instance lock, then opens {@code paths}.
     *
     * @param planned a planned command line
     * @param pid the process it replaces
     * @param paths absolute paths the replacement opens
     * @return the command line to start
     */
    public static List<String> replacing(List<String> planned, long pid, List<Path> paths) {
        List<String> line = new ArrayList<>(planned);
        line.add("--replacing");
        line.add(Long.toString(pid));
        for (Path path : paths) line.add(path.toString());
        return List.copyOf(line);
    }

    /**
     * Starts the replacement, detached from this process's streams. Never the EDT.
     *
     * @param command a planned command line
     * @throws IOException when the process cannot be started
     */
    public static void spawn(List<String> command) throws IOException {
        new ProcessBuilder(command).redirectOutput(ProcessBuilder.Redirect.DISCARD)
            .redirectError(ProcessBuilder.Redirect.DISCARD).start();
    }
}
```

Replace `jasper-app/src/main/java/dev/jasper/app/restart/RestartFlow.java` with:

```java
package dev.jasper.app.restart;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * The Plugins page's restart banner: Restart Now and Restart Normally restart at once, the replacement waiting for this
 * process's instance lock (workspace-tabs spec 5.3); a command line that cannot be determined leaves the banner saying
 * so. UI thread only.
 */
public final class RestartFlow {
    /** Where the banner is. */
    public enum State {
        /** Nothing in progress. */
        IDLE,
        /** The command line cannot be determined; the user must quit and reopen Jasper. */
        UNAVAILABLE
    }

    private final Predicate<RestartMode> restart;
    private final List<Runnable> listeners = new ArrayList<>();
    private State state = State.IDLE;

    /**
     * Creates an idle flow.
     *
     * @param restart quits and relaunches in the given mode; false when the command line is unknown
     */
    public RestartFlow(Predicate<RestartMode> restart) { this.restart = Objects.requireNonNull(restart); }

    /**
     * The current state.
     *
     * @return the state
     */
    public State state() { return state; }

    /**
     * Registers a listener for state changes.
     *
     * @param listener run after each change
     */
    public void onChanged(Runnable listener) { listeners.add(Objects.requireNonNull(listener)); }

    /** Restarts the same launch: for a process that is not in safe mode. */
    public void restartNow() { relaunch(RestartMode.SAME); }

    /** Leaves safe mode. */
    public void restartNormally() { relaunch(RestartMode.NORMAL); }

    /** Forgets an unavailable restart. */
    public void cancel() { enter(State.IDLE); }

    private void relaunch(RestartMode mode) { enter(restart.test(mode) ? State.IDLE : State.UNAVAILABLE); }

    private void enter(State next) {
        state = next;
        for (Runnable listener : List.copyOf(listeners)) listener.run();
    }
}
```

Delete `jasper-app/src/main/java/dev/jasper/app/restart/ResidentControl.java`. In `restart/package-info.java` replace the
four lines
```
 * Restarting Jasper to apply a plugin change: planning the replacement's command line from this process's own,
 * a seam over the resident process, and the "Restart normally" conversation that never starts a
 * handoff-capable replacement while a resident still holds the endpoint. No owner: values and one
 * state machine whose worker threads are daemons.
```
with
```
 * Restarting Jasper: planning the replacement's command line from this process's own (the replacement waits for this
 * process's instance lock with {@code --replacing}) and the Plugins page's restart banner state. No owner: values and
 * one state machine on the UI thread.
```

- [ ] **Step 4: RETIRE goes; the manager, the application and the bootstrap follow**

Replace `jasper-app/src/main/java/dev/jasper/app/residency/LaunchRequest.java` with (Task 5 replaces it again, with
protocol 2):

```java
package dev.jasper.app.residency;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Objects;

/**
 * One line of the handoff protocol: what a launching process asks a resident one to do.
 *
 * <p>There is deliberately no working directory. A resident process opens a window exactly where a
 * cold launch would, so that {@code jasper} from a shell does not behave differently depending on
 * whether a daemon happened to be running.
 */
public record LaunchRequest(String token, Path codeLocation, long codeLocationModified) {
    private static final String MAGIC = "jasper";
    private static final int PROTOCOL = 1;
    private static final int FIELDS = 5;

    public LaunchRequest {
        Objects.requireNonNull(token, "token");
        Objects.requireNonNull(codeLocation, "codeLocation");
    }

    /** One newline-terminated line. The path is base64 so a tab or newline in it survives. */
    String encode() {
        return String.join("\t", MAGIC, Integer.toString(PROTOCOL), token, encode(codeLocation),
            Long.toString(codeLocationModified)) + "\n";
    }

    /** Null for anything this build cannot act on, including a future protocol. Never throws. */
    static LaunchRequest decode(String line) {
        String[] parts = line.strip().split("\t", -1);
        if (parts.length != FIELDS || !MAGIC.equals(parts[0])) return null;
        try {
            if (Integer.parseInt(parts[1]) != PROTOCOL) return null;
            return new LaunchRequest(parts[2], decodePath(parts[3]), Long.parseLong(parts[4]));
        } catch (IllegalArgumentException malformed) {
            // Covers NumberFormatException, a bad base64 body and an unusable path alike.
            return null;
        }
    }

    private static String encode(Path path) {
        return Base64.getEncoder().encodeToString(path.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static Path decodePath(String encoded) {
        return Path.of(new String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8));
    }

    /** What a resident process says back. Anything unrecognised is a protocol error, never an accept. */
    public enum Response {
        OK, TOKEN, STALE, PROTOCOL;

        String line() {
            return this == OK ? "ok\n" : "refused\t" + name().toLowerCase(java.util.Locale.ROOT) + "\n";
        }

        static Response of(String line) {
            String text = line.strip();
            if (text.equals("ok")) return OK;
            if (text.equals("refused\tstale")) return STALE;
            if (text.equals("refused\ttoken")) return TOKEN;
            return PROTOCOL;
        }
    }
}
```

Then run from the worktree root:

```bash
python3 -I - <<'PY'
"""Workspace tabs 1, Task 3 step 4: RETIRE, ResidentControl and the standalone replacement go."""
import pathlib
def edit(name, edits):
    path = pathlib.Path(name); text = path.read_text(encoding="utf-8")
    for old, new, count in edits:
        if text.count(old) != count: raise SystemExit(f"{name}: expected {count} of {old!r}, found {text.count(old)}")
        text = text.replace(old, new)
    path.write_text(text, encoding="utf-8")
M = "jasper-app/src/main/java/dev/jasper/app/"
edit(M + "residency/HandoffSocket.java", [
    ('''        return exchange(socketPath, tokenPath, codeLocation, modified, LaunchRequest.Kind.OPEN) == LaunchRequest.Response.OK;
    }

    /**
     * Asks a resident process to quit through its normal quit path. True only when it accepted; it then
     * releases the endpoint as part of its shutdown, which {@link #live} observes. Blocks, bounded.
     */
    public static boolean retire(Path socketPath, Path tokenPath, Path codeLocation, long modified) {
        return exchange(socketPath, tokenPath, codeLocation, modified, LaunchRequest.Kind.RETIRE) == LaunchRequest.Response.OK;
    }''',
     '''        return exchange(socketPath, tokenPath, codeLocation, modified) == LaunchRequest.Response.OK;
    }''', 1),
    ('''    private static LaunchRequest.Response exchange(Path socketPath, Path tokenPath, Path codeLocation, long modified,
                                                   LaunchRequest.Kind kind) {''',
     '''    private static LaunchRequest.Response exchange(Path socketPath, Path tokenPath, Path codeLocation, long modified) {''', 1),
    ('''        var request = new LaunchRequest(token, codeLocation == null ? Path.of("") : codeLocation, modified, kind);''',
     '''        var request = new LaunchRequest(token, codeLocation == null ? Path.of("") : codeLocation, modified);''', 1)])
edit(M + "pluginmanager/PluginManager.java", [
    ('''import dev.jasper.app.restart.ResidentControl;\n''', '', 1),
    ('''     * @param restarts the restart conversation
     * @param quit quits Jasper, for when it cannot restart itself
     * @param resident the resident process, probed for the standalone notice
     * @param standaloneNotice whether this process is a {@code --standalone} replacement that should say when a resident still runs
     * @param worker runs the blocking resident probe
     * @param browse opens a plugin's homepage in the browser
     */
    public record Hooks(Function<AuxiliarySurface, Optional<Path>> chooseZip, RestartFlow restarts, Runnable quit,
                        ResidentControl resident, boolean standaloneNotice, Executor worker,''',
     '''     * @param restarts the restart banner's state
     * @param quit quits Jasper, for when it cannot restart itself
     * @param worker runs blocking file work: preparing a settings file, creating a folder to reveal
     * @param openInEditor opens a file for editing
     * @param reveal shows a folder in the file manager
     * @param browse opens a plugin's homepage in the browser
     */
    public record Hooks(Function<AuxiliarySurface, Optional<Path>> chooseZip, RestartFlow restarts, Runnable quit, Executor worker,''', 1),
    ('''            Objects.requireNonNull(resident); Objects.requireNonNull(worker);''', '''            Objects.requireNonNull(worker);''', 1),
    ('''    /** Lists the plugins again: another process may have changed plugins.toml since the page was last shown. */
    void refresh() {
        runtime.snapshot(this::accept);
        if (panel == null || !hooks.standaloneNotice()) return;
        hooks.worker().execute(() -> {
            boolean live = hooks.resident().live().getAsBoolean();
            SwingUtilities.invokeLater(() -> {
                if (panel != null) panel.notice(live
                    ? "Another Jasper process is still running with the plugin set it started with. Quit it for your changes to apply there." : "");
            });
        });
    }''',
     '''    /** Lists the plugins again: plugins.toml may have changed since the page was last shown. */
    void refresh() { runtime.snapshot(this::accept); }''', 1),
    ('''        RestartFlow flow = hooks.restarts();
        var launchAnyway = new PluginManagerPanel.BannerAction("Launch Anyway", flow::launchAnyway);
        var cancel = new PluginManagerPanel.BannerAction("Cancel", flow::cancel);
        switch (flow.state()) {
            case UNAVAILABLE -> panel.banner("Jasper could not work out how it was started. Quit and reopen it to apply your changes.",
                List.of(new PluginManagerPanel.BannerAction("Quit Jasper", hooks.quit())));
            case PROBING -> panel.banner("Looking for another Jasper process…", List.of());
            case RESIDENT_FOUND -> panel.banner("Another Jasper process is running with the previous plugin set. It has to quit before a normal restart can apply your changes.",
                List.of(new PluginManagerPanel.BannerAction("Quit It and Restart", flow::askResidentToQuit), launchAnyway, cancel));
            case WAITING -> panel.banner("Waiting for the other Jasper process to quit…", List.of(launchAnyway, cancel));
            case RESIDENT_STUCK -> panel.banner("The other Jasper process did not quit. Launch Anyway starts a separate Jasper with your changes; the other one keeps the previous plugin set until you quit it.",
                List.of(new PluginManagerPanel.BannerAction("Try Again", flow::askResidentToQuit), launchAnyway, cancel));
            case IDLE -> {''',
     '''        RestartFlow flow = hooks.restarts();
        switch (flow.state()) {
            case UNAVAILABLE -> panel.banner("Jasper could not work out how it was started. Quit and reopen it to apply your changes.",
                List.of(new PluginManagerPanel.BannerAction("Quit Jasper", hooks.quit())));
            case IDLE -> {''', 1)])
edit(M + "pluginmanager/PluginManagerPanel.java", [
    (''' * The Plugins page's passive view: a search field and gear menu, the restart banner and notice, the plugin rows and the''',
     ''' * The Plugins page's passive view: a search field and gear menu, the restart banner, the plugin rows and the''', 1),
    ('''    private String notice = "", message = "";''', '''    private String message = "";''', 1),
    ('''        top.setVisible(!bannerText.isEmpty() || !notice.isEmpty());
        headerView.setBorder(BorderFactory.createEmptyBorder(bannerText.isEmpty() && notice.isEmpty() ? 0 : pad, 0, 0, 0));''',
     '''        top.setVisible(!bannerText.isEmpty());
        headerView.setBorder(BorderFactory.createEmptyBorder(bannerText.isEmpty() ? 0 : pad, 0, 0, 0));''', 1),
    ('''        return new ViewSpec(new Nodes.Stack("plugins-header", ControlState.READY, Nodes.LayoutRole.VERTICAL, List.of(banner,
            new Nodes.Text("notice", notice.isEmpty() ? hidden : ControlState.READY, notice.isEmpty() ? " " : notice, Nodes.TextRole.DESCRIPTION))));''',
     '''        return new ViewSpec(new Nodes.Stack("plugins-header", ControlState.READY, Nodes.LayoutRole.VERTICAL, List.of(banner)));''', 1),
    ('''    void notice(String text) { notice = text; render(headerView, headerSpec()); restyle(); }\n\n''', '', 1),
    ('''    String noticeText() { return notice; }\n''', '', 1)])
edit(M + "application/JasperApplication.java", [
    ('''import dev.jasper.app.restart.ResidentControl;\n''', '', 1),
    ('''    private PluginManager pluginManager;
    private ResidentControl residentControl = ResidentControl.NONE;
    private boolean replacementHandsOff;
    private boolean standaloneNotice;
''', '''    private PluginManager pluginManager;
''', 1),
    ('''    Function<RestartMode, Optional<List<String>>> restartPlanner = RestartCommand::current;''',
     '''    Function<RestartMode, Optional<List<String>>> restartPlanner = mode -> RestartCommand.current(mode, List.of());''', 1),
    ('''    private volatile List<String> relaunch;
    private volatile boolean endpointOwner;
    private volatile List<CompletableFuture<?>> processCleanups;
''', '''    private volatile List<String> relaunch;
''', 1),
    ('''        processCleanups = List.copyOf(cleanups);\n''', '', 1),
    ('''    /**
     * Quits through the normal quit path and starts a replacement process once process cleanup, which
     * releases the handoff endpoint, has finished. EDT.
     *
     * @param mode which launch flags the replacement keeps
     * @return false, and nothing happens, when this process's command line cannot be determined; otherwise
     *     true. It also returns true, with nothing changed, when a plugin window's closing guard refuses the quit.
     */
    public boolean restart(RestartMode mode) {
        if (quitting || stopped) return true;
        Optional<List<String>> planned = restartPlanner.apply(mode);
        if (planned.isEmpty()) return false;
        // A refusal leaves no relaunch behind: a later ordinary quit must not start a replacement.
        if (!pluginWindowsMayClose()) return true;
        relaunch = planned.get();
        endpointOwner = resident;
        quitNow();
        return true;
    }

    /** Exit thread. A replacement that could hand off must not start while this process may still answer the endpoint. */
    private void relaunchIfRequested() {
        List<String> command = relaunch;
        if (command == null) return;
        List<CompletableFuture<?>> cleanups = processCleanups;
        boolean released = cleanups != null && cleanups.stream().allMatch(done -> done.isDone() && !done.isCompletedExceptionally());
        if (endpointOwner && !released) {
            LOG.log(System.Logger.Level.WARNING, "Process cleanup did not finish; the replacement starts standalone so it cannot hand off to this process");
            command = RestartCommand.standalone(command);
        }
        try { spawner.accept(command); }
        catch (RuntimeException failure) { LOG.log(System.Logger.Level.ERROR, "Could not start the replacement process", failure); }
    }

    /**
     * Tells the Plugins manager about the resident process. EDT, before the manager is first opened.
     *
     * @param control probes and retires the process that holds the handoff endpoint
     * @param replacementHandsOff whether leaving safe mode produces a plain launch, which a resident would swallow
     * @param standaloneNotice whether this process is a {@code --standalone} replacement that should say when a resident still runs
     */
    public void residentControl(ResidentControl control, boolean replacementHandsOff, boolean standaloneNotice) {
        this.residentControl = java.util.Objects.requireNonNull(control);
        this.replacementHandsOff = replacementHandsOff;
        this.standaloneNotice = standaloneNotice;
    }''',
     '''    /** As {@link #restart(RestartMode, List)}, the replacement opening nothing. */
    public boolean restart(RestartMode mode) { return restart(mode, List.of()); }

    /**
     * Quits through the normal quit path and, from the exit thread once process cleanup is over, starts a replacement
     * that waits for this process's instance lock ({@code --replacing}) and then opens {@code paths}
     * (workspace-tabs spec 5.3). EDT.
     *
     * @param mode which launch flags the replacement keeps
     * @param paths absolute paths the replacement opens
     * @return false, and nothing happens, when this process's command line cannot be determined; otherwise
     *     true. It also returns true, with nothing changed, when a plugin window's closing guard refuses the quit.
     */
    public boolean restart(RestartMode mode, List<Path> paths) {
        if (quitting || stopped) return true;
        Optional<List<String>> planned = restartPlanner.apply(mode);
        if (planned.isEmpty()) return false;
        // A refusal leaves no relaunch behind: a later ordinary quit must not start a replacement.
        if (!pluginWindowsMayClose()) return true;
        relaunch = RestartCommand.replacing(planned.get(), ProcessHandle.current().pid(), paths);
        quitNow();
        return true;
    }

    /** Exit thread. The replacement waits up to 30 s for this process's instance lock, which the end of the process releases. */
    private void relaunchIfRequested() {
        List<String> command = relaunch;
        if (command == null) return;
        try { spawner.accept(command); }
        catch (RuntimeException failure) { LOG.log(System.Logger.Level.ERROR, "Could not start the replacement process", failure); }
    }

    /**
     * The arguments this process's {@code main} received, which a restart replays without its paths. Before the first
     * restart.
     *
     * @param arguments as {@code main} received them
     */
    public void launchArguments(List<String> arguments) {
        List<String> launch = List.copyOf(arguments);
        restartPlanner = mode -> RestartCommand.current(mode, launch);
    }''', 1),
    ('''            java.util.concurrent.Executor worker = work -> Thread.ofPlatform().daemon().name("jasper-restart").start(work);
            // Only a plain replacement can be swallowed by a resident; any other is standalone and needs no conversation.
            var flow = new RestartFlow(replacementHandsOff ? residentControl : ResidentControl.NONE, this::restart, worker,
                SwingUtilities::invokeLater, Duration.ofSeconds(30), Duration.ofMillis(250));
            var editor = new dev.jasper.app.platform.ConfigEditor();
            pluginManager = new PluginManager(plugins, auxiliary, new PluginManager.Hooks(
                surface -> shells.chooseFile(surface, "Install Plugin", ".zip"), flow, this::quit, residentControl, standaloneNotice, worker,''',
     '''            java.util.concurrent.Executor worker = work -> Thread.ofPlatform().daemon().name("jasper-plugin-files").start(work);
            RestartFlow flow = new RestartFlow(this::restart);
            var editor = new dev.jasper.app.platform.ConfigEditor();
            pluginManager = new PluginManager(plugins, auxiliary, new PluginManager.Hooks(
                surface -> shells.chooseFile(surface, "Install Plugin", ".zip"), flow, this::quit, worker,''', 1)])
edit(M + "bootstrap/ApplicationBootstrap.java", [
    ('''import dev.jasper.app.restart.ResidentControl;\n''', '', 1),
    ('''            Path source = HandoffSocket.codeLocation();
            application.residentControl(new ResidentControl(() -> HandoffSocket.live(dirs.daemonSocket()),
                () -> HandoffSocket.retire(dirs.daemonSocket(), dirs.daemonToken(), source, HandoffSocket.lastModified(source))),
                replacementHandsOff(options), standaloneNotice(options));
            application.staleBuild(''',
     '''            Path source = HandoffSocket.codeLocation();
            // A restart replays this launch's own arguments, without its paths (workspace-tabs spec 5.3).
            application.launchArguments(options.given());
            application.staleBuild(''', 1),
    ('''    /**
     * Whether leaving safe mode yields a plain launch. A replacement that keeps {@code --config},
     * {@code --plugin-dir} or {@code --standalone} is standalone anyway: it never hands off, so there is no
     * resident to ask, and a resident with another configuration must not be retired on its account.
     */
    static boolean replacementHandsOff(AppArguments options) {
        return options.configOverride() == null && options.pluginDir() == null && !options.standalone() && !options.homeOverride();
    }

    /** A launch that is standalone only because of {@code --standalone} is a "Launch anyway" replacement. */
    static boolean standaloneNotice(AppArguments options) {
        return options.standalone() && options.configOverride() == null && options.pluginDir() == null && !options.safeMode();
    }

''', '', 1),
    ('''    /**
     * The resident process's side of a handoff. A stale request also ends residency: the endpoint
     * goes away with the reply, so the last window closing must run the normal bounded shutdown
     * rather than leaving a process with nothing to reach it and no cleanup on the way out. A retire
     * request runs the normal quit path; the endpoint is released by the application's shutdown, not here.
     */
    static java.util.function.Function<LaunchRequest, LaunchRequest.Response> handoffHandler(
            JasperApplication application, Path source, long modified) {
        return request -> {
            // Before the staleness check: a recovery launch may well come from another build.
            if (request.kind() == LaunchRequest.Kind.RETIRE) {
                SwingUtilities.invokeLater(application::quit);
                return LaunchRequest.Response.OK;
            }
            if (stale(request, source, modified)) {''',
     '''    /**
     * The resident process's side of a handoff. A stale request also ends residency: the endpoint
     * goes away with the reply, so the last window closing must run the normal bounded shutdown
     * rather than leaving a process with nothing to reach it and no cleanup on the way out.
     */
    static java.util.function.Function<LaunchRequest, LaunchRequest.Response> handoffHandler(
            JasperApplication application, Path source, long modified) {
        return request -> {
            if (stale(request, source, modified)) {''', 1)])
edit("jasper-sdk/src/main/java/dev/jasper/sdk/ui/Windows.java", [
    ('''     * really quits (Quit Jasper Completely, a log-out or restart, or a request from another launch to retire).''',
     '''     * really quits (Quit Jasper Completely, a log-out or a restart).''', 1)])
PY
```

`JasperApplication`'s `Duration` import stays in use elsewhere in the file; `PluginManager` keeps its `SwingUtilities`
import (it still posts a failure message, `PluginManager.java:212`). `PluginManagerPanel`'s notice line goes with its only
caller (Ruling 9): its field, `notice(String)`, `noticeText()` and the header's hidden `notice` node. The SDK's
`Windows.onQuitting` Javadoc names what really quits: Quit Jasper Completely, a log-out or a restart.

- [ ] **Step 5: Run the tests to verify they pass**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.restart.*' --tests 'dev.jasper.app.residency.*' --tests 'dev.jasper.app.pluginmanager.*' --tests 'dev.jasper.app.settingsui.PluginSettingsPagesEvidenceTest' --tests 'dev.jasper.app.settingsui.SettingsPagesMockTest' --tests 'dev.jasper.app.application.JasperApplicationPluginsTest' --tests 'dev.jasper.app.application.JasperApplicationBackgroundTest' --tests 'dev.jasper.app.application.JasperApplicationResidencyTest' --tests 'dev.jasper.app.application.PresentationAttemptTest' --tests 'dev.jasper.app.bootstrap.*' --tests 'dev.jasper.app.appearance.ThemeMigrationCoverageTest' --continue`
Expected: PASS. Then `./gradlew :jasper-app:check :jasper-sdk:javadoc` (architecture, doclint, every app test, the SDK's
Javadoc): PASS.

The notice node was hidden in every state the 8b and 9b comparisons render, and the kit's stacks lay out only visible
children (`jasper-ui/src/main/java/dev/jasper/ui/swing/Stacks.java`), so the Plugins page's geometry does not move and
nothing needs re-pinning: no render of the page is stored. The gates are `SettingsPagesMockTest.pluginsMatchesItsMock`
(the live render against its fixed mock) and `PluginSettingsPagesEvidenceTest`, run above, and the 9b pair:

```bash
./gradlew :jasper-app:settings9bPreview
python3 -I tools/visual/compare_all.py build/visual-check settings-plugins
```

Expected: the `settings-plugins:` line recorded before Step 1, unchanged. A different figure means the header moved: stop
and report both lines; do not change a mock or `pairs.json`.

- [ ] **Step 6: Commit**

```bash
git add -A jasper-app/src jasper-sdk/src/main/java/dev/jasper/sdk/ui/Windows.java
git commit -F - <<'EOF'
Single instance: restart's replacement waits for the lock

A restart spawns the replacement from the exit thread with --replacing
<pid>, replaying this launch's own arguments without its paths, an
earlier --replacing, --background or --no-window. RestartFlow keeps the
Plugins page's two-state banner; ResidentControl, the retire request,
RestartMode.STANDALONE and the standalone fallback go.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

---

### Task 4: Every outside request opens through one method

Spec 5.3/5.4 RAISE and OPEN_PATHS through today's window model (Ruling 12). `openOrRaise` becomes
`open(List<Path>, String)`, the one method plan 4 retargets to the main window; a launch's first window takes its first
directory (Ruling 13); a file from outside opens in a plugin's editor or is revealed, never edited by the desktop
(Ruling 26).

**Files:**
- Create: `jasper-app/src/main/java/dev/jasper/app/application/OpenPlan.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/application/JasperApplication.java:448-452,625-637,660,730,736,819-834`
- Modify: `jasper-app/src/main/java/dev/jasper/app/platform/ConfigEditor.java:90-91`
- Modify: `jasper-app/src/main/java/dev/jasper/app/bootstrap/ApplicationBootstrap.java` (the handoff handler's raise)
- Create (test): `jasper-app/src/test/java/dev/jasper/app/application/OpenPlanTest.java`
- Modify (test): `application/JasperApplicationBackgroundTest.java:138,157,223,706-707`,
  `application/JasperApplicationResidencyTest.java:14,155-165`, `application/JasperApplicationPluginsTest.java:560`,
  `platform/ConfigEditorTest.java:89-90`

**Interfaces:**
- Consumes: `JasperApplication.openWindow(Path, boolean, String)`, `windowTrigger(int, boolean)`, `TerminalWindow.toFront()`,
  `WindowContent.newTab(Path)`, `PluginRuntime.open(Path, OptionalInt, Optional<UUID>, Consumer<Path>)`.
- Produces: `record OpenPlan(boolean newWindow, Path windowDirectory, List<Path> tabs, List<Path> files)` with
  `static OpenPlan of(List<Path> paths, boolean windowOpen, Predicate<Path> directory, Predicate<Path> file)`;
  `public void JasperApplication.open(List<Path> paths, String trigger)` and `public TerminalWindow
  openFirstWindow(List<Path> paths)` (EDT); package-private `openRequestedFile(Path file, UUID window)` and
  `openRequestedFile(Path, UUID, ConfigEditor)`; `public void ConfigEditor.revealOnly(Path)`. Task 5's `handedOff`,
  Task 9's path calls and Task 10's open-file event call `open`; Task 7's cold start calls `openFirstWindow`; plan 4
  changes their bodies.

- [ ] **Step 1: Write the failing tests**

Create `jasper-app/src/test/java/dev/jasper/app/application/OpenPlanTest.java`:

```java
package dev.jasper.app.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Spec 5.3 and 5.4 through today's windows (plan Ruling 12). */
class OpenPlanTest {
    private static final Path A = Path.of("/work/a"), B = Path.of("/work/b"), NOTES = Path.of("/work/notes.txt"),
        GONE = Path.of("/work/gone");
    private static final Set<Path> DIRECTORIES = Set.of(A, B), FILES = Set.of(NOTES);

    private static OpenPlan plan(boolean windowOpen, Path... paths) {
        return OpenPlan.of(List.of(paths), windowOpen, DIRECTORIES::contains, FILES::contains);
    }

    @Test void aRaiseOpensAWindowOnlyWhenNoneIsOpen() {
        assertThat(plan(true)).isEqualTo(new OpenPlan(false, null, List.of(), List.of()));
        assertThat(plan(false)).isEqualTo(new OpenPlan(true, null, List.of(), List.of()));
    }

    @Test void directoriesBecomeTerminalTabsAndFilesOpenInTheSameWindow() {
        assertThat(plan(true, NOTES, A, B)).isEqualTo(new OpenPlan(false, null, List.of(A, B), List.of(NOTES)));
    }

    @Test void withNoWindowTheFirstDirectoryIsTheNewWindowsTab() {
        assertThat(plan(false, NOTES, A, B)).isEqualTo(new OpenPlan(true, A, List.of(B), List.of(NOTES)));
        assertThat(plan(false, NOTES)).isEqualTo(new OpenPlan(true, null, List.of(), List.of(NOTES)));
    }

    @Test void aMissingPathIsSkippedAndARepeatOpensOnce() {
        assertThat(plan(true, GONE, A, A, NOTES, NOTES)).isEqualTo(new OpenPlan(false, null, List.of(A), List.of(NOTES)));
        assertThat(plan(false, GONE)).as("nothing to open: a window still opens").isEqualTo(new OpenPlan(true, null, List.of(), List.of()));
    }
}
```

Then run from the worktree root:

```bash
python3 -I - <<'PY'
"""Workspace tabs 1, Task 4 step 1: the tests call open(List, String)."""
import pathlib
def edit(name, edits):
    path = pathlib.Path(name); text = path.read_text(encoding="utf-8")
    for old, new, count in edits:
        if text.count(old) != count: raise SystemExit(f"{name}: expected {count} of {old!r}, found {text.count(old)}")
        text = text.replace(old, new)
    path.write_text(text, encoding="utf-8")
T = "jasper-app/src/test/java/dev/jasper/app/application/"
edit(T + "JasperApplicationBackgroundTest.java", [
    ('''application.openOrRaise("reopen")''', '''application.open(List.of(), "reopen")''', 2),
    ('''application.openOrRaise("menu")''', '''application.open(List.of(), "menu")''', 1),
    ('''            // stopped, because openOrRaise returns first thing in either state.''',
     '''            // stopped, because open returns first thing in either state.''', 1)])
edit(T + "JasperApplicationResidencyTest.java", [
    ('''import java.util.ArrayDeque;\n''', '''import java.util.ArrayDeque;\nimport java.util.List;\n''', 1),
    ('''    @Test void openOrRaiseDoesNotThrowOnceTheApplicationHasQuit() throws Exception {
        JasperApplication application = application(() -> { });
        edt(() -> application.residency(true));
        edt(application::quit);
        // Only that a late request is harmless: with no windows open this reaches openWindow, which
        // has its own quitting guard. The branch that openOrRaise's own guard protects -- raising an
        // existing window during shutdown -- needs a live window and cannot be covered headlessly.
        // It is on the manual checklist instead.
        edt(() -> application.openOrRaise(DesktopTestSupport.HOME, "handoff"));
        edt(() -> { });
    }''',
     '''    @Test void anOpenDoesNotThrowOnceTheApplicationHasQuit() throws Exception {
        JasperApplication application = application(() -> { });
        edt(() -> application.residency(true));
        edt(application::quit);
        // Only that a late request is harmless. The branch open's own guard protects -- raising an existing window
        // during shutdown -- needs a live window and cannot be covered headlessly; it is on the manual checklist.
        edt(() -> application.open(List.of(DesktopTestSupport.HOME), "handoff"));
        edt(() -> { });
    }

    @Test void anOpenWithNoWindowGoesForOne() throws Exception {
        JasperApplication application = application(() -> { });
        try {
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> edt(() -> application.open(List.of(DesktopTestSupport.HOME), "handoff")))
                .as("composing the window is where a headless JVM fails").hasRootCauseInstanceOf(java.awt.HeadlessException.class);
        } finally {
            edt(application::quitNow);
        }
    }

    @Test void aLaunchsFirstWindowWithPathsIsStillAColdStart() throws Exception {
        var trace = StartupTrace.detached();
        JasperApplication application = application(() -> { });
        try {
            edt(() -> application.trace(trace));
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> edt(() -> application.openFirstWindow(List.of(DesktopTestSupport.HOME))))
                .hasRootCauseInstanceOf(java.awt.HeadlessException.class);
            assertThat(trace.windows()).as("timed as the cold start it is (plan Ruling 13)")
                .extracting(dev.jasper.app.startup.WindowTrace::trigger).containsExactly("cold");
        } finally {
            edt(application::quitNow);
        }
    }''', 1)])
edit(T + "JasperApplicationPluginsTest.java", [
    ('''    private static void awaitCalls(List<String> calls, int count) throws InterruptedException {''',
     '''    @Test void aRequestedFilesDesktopFallbackOnlyReveals() throws Exception {
        Path notes = java.nio.file.Files.writeString(home.resolve("notes.txt"), "notes\\n");
        var calls = new CopyOnWriteArrayList<String>();
        var desktop = new dev.jasper.app.platform.ConfigEditor(path -> calls.add("edit:" + path.getFileName()),
            path -> calls.add("open:" + path.getFileName()), path -> calls.add("reveal:" + path.getFileName()));
        java.util.UUID window = java.util.UUID.randomUUID();
        var terminated = new CountDownLatch(1);
        JasperApplication[] application = new JasperApplication[1];
        edt(() -> {
            application[0] = new JasperApplication(null, launcher(new ArrayDeque<>()), new CommandHistory(), null, terminated::countDown);
            application[0].openRequestedFile(notes, window, desktop);
        });
        awaitCalls(calls, 1);
        assertThat(calls).as("plugins not started: the desktop's edit action could hand the file back to Jasper")
            .containsExactly("reveal:notes.txt");

        calls.clear();
        AppDirs dirs = new AppDirs(home, home.resolve("config.toml"), home.resolve("logs"));
        Path dev = home.resolve("life-plugin");
        PluginJars.build(dev, "life.jar", PluginJars.descriptor("dev.example.life", "1.0.0", "fix.life.Main"),
            Map.of("fix.life.Main", FIXTURE), List.of());
        edt(() -> {
            application[0].startPlugins(null, dev, false, dirs);
            application[0].openRequestedFile(notes, window, desktop);
        });
        awaitCalls(calls, 1);
        assertThat(calls).as("plugins running, none provides an editor").containsExactly("reveal:notes.txt");
        edt(application[0]::quit);
        assertThat(terminated.await(5, TimeUnit.SECONDS)).isTrue();
    }

    private static void awaitCalls(List<String> calls, int count) throws InterruptedException {''', 1)])
edit("jasper-app/src/test/java/dev/jasper/app/platform/ConfigEditorTest.java", [
    ('''        assertThat(calls).isEmpty();
    }
}
''', '''        assertThat(calls).isEmpty();
    }

    /** Workspace-tabs plan 1, Ruling 26: a file another program asked Jasper to open is revealed, never edited or opened. */
    @Test void revealOnlyNeverEditsOrOpensAndRefusesTheEdt() throws Exception {
        Path file = Path.of("/tmp/notes.txt");
        var calls = new ArrayList<String>();
        new ConfigEditor(path -> calls.add("edit:" + path), path -> calls.add("open:" + path), path -> calls.add("reveal:" + path))
            .revealOnly(file);
        assertThat(calls).containsExactly("reveal:" + file);
        var broken = new ConfigEditor(path -> calls.add("edit"), path -> calls.add("open"),
            path -> { throw new UnsupportedOperationException("reveal"); });
        assertThatThrownBy(() -> broken.revealOnly(file)).isInstanceOf(IllegalStateException.class)
            .hasMessageContaining(file.toString())
            .satisfies(failure -> assertThat(failure.getSuppressed()).extracting(Throwable::getMessage).containsExactly("reveal"));
        assertThat(calls).as("nothing else was tried").containsExactly("reveal:" + file);
        var thrown = new AtomicReference<Throwable>();
        SwingUtilities.invokeAndWait(() -> {
            try { broken.revealOnly(file); } catch (Throwable failure) { thrown.set(failure); }
        });
        assertThat(thrown.get()).isInstanceOf(IllegalStateException.class).hasMessageContaining("worker thread");
    }
}
''', 1)])
PY
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :jasper-app:compileTestJava`
Expected: FAIL to compile: "cannot find symbol … OpenPlan", "… method open(List<Path>,String)", "… openFirstWindow",
"… openRequestedFile", "… revealOnly".

- [ ] **Step 3: The plan and the one method**

Create `jasper-app/src/main/java/dev/jasper/app/application/OpenPlan.java`:

```java
package dev.jasper.app.application;

import java.nio.file.Path;
import java.util.List;
import java.util.function.Predicate;

/**
 * Where an outside request's paths open (workspace-tabs spec 5.3 and 5.4, through today's windows until the main window
 * exists): each directory as a terminal tab, each file in the editor a plugin provides; anything else is skipped and a
 * repeated path opens once. With no window open a window opens first, in the first directory when there is one. Pure.
 *
 * @param newWindow whether a window opens first
 * @param windowDirectory where the new window's tab starts; null for where a new shell starts
 * @param tabs the directories that open as terminal tabs in the target window, in order
 * @param files the files that open in the target window, in order
 */
record OpenPlan(boolean newWindow, Path windowDirectory, List<Path> tabs, List<Path> files) {
    OpenPlan {
        tabs = List.copyOf(tabs);
        files = List.copyOf(files);
    }

    /**
     * Plans {@code paths}.
     *
     * @param paths absolute paths, in the order the request names them
     * @param windowOpen whether a terminal window is open
     * @param directory whether a path is a directory
     * @param file whether a path is a regular file
     * @return the plan
     */
    static OpenPlan of(List<Path> paths, boolean windowOpen, Predicate<Path> directory, Predicate<Path> file) {
        List<Path> directories = paths.stream().distinct().filter(directory).toList();
        List<Path> files = paths.stream().distinct().filter(directory.negate()).filter(file).toList();
        if (windowOpen) return new OpenPlan(false, null, directories, files);
        if (directories.isEmpty()) return new OpenPlan(true, null, List.of(), files);
        return new OpenPlan(true, directories.getFirst(), directories.subList(1, directories.size()), files);
    }
}
```

Then run from the worktree root:

```bash
python3 -I - <<'PY'
"""Workspace tabs 1, Task 4 step 3: open(List, String) replaces openOrRaise."""
import pathlib
def edit(name, edits):
    path = pathlib.Path(name); text = path.read_text(encoding="utf-8")
    for old, new, count in edits:
        if text.count(old) != count: raise SystemExit(f"{name}: expected {count} of {old!r}, found {text.count(old)}")
        text = text.replace(old, new)
    path.write_text(text, encoding="utf-8")
M = "jasper-app/src/main/java/dev/jasper/app/"
edit(M + "application/JasperApplication.java", [
    ('''                SwingUtilities.invokeLater(() -> openOrRaise(HANDOFF));''', '''                SwingUtilities.invokeLater(() -> open(List.of(), HANDOFF));''', 1),
    ('''        openOrRaise("reopen");''', '''        open(List.of(), "reopen");''', 1),
    ('''                () -> openOrRaise("menu"), this::newWindowFromMenuBar''', '''                () -> open(List.of(), "menu"), this::newWindowFromMenuBar''', 1),
    ('''    /** As {@link #openFileAt(Path, int, UUID)}, falling back to {@code desktop}; tests inject its actions. */
    void openFileAt(Path file, int line, UUID window, dev.jasper.app.platform.ConfigEditor desktop) {
        Thread.ofVirtual().name("jasper-open-file").start(() -> {
            try {
                if (!java.nio.file.Files.isRegularFile(file)) return;
                PluginRuntime runtime = plugins;
                if (runtime == null) desktop.editOrReveal(file);
                else runtime.open(file, line > 0 ? OptionalInt.of(line) : OptionalInt.empty(), Optional.of(window), desktop::editOrReveal);
            } catch (RuntimeException failure) {
                LOG.log(System.Logger.Level.WARNING, "Could not open a clicked file", failure);
            }
        });
    }''',
     '''    /** As {@link #openFileAt(Path, int, UUID)}, falling back to {@code desktop}; tests inject its actions. */
    void openFileAt(Path file, int line, UUID window, dev.jasper.app.platform.ConfigEditor desktop) {
        openFile(file, line, window, desktop::editOrReveal, "Could not open a clicked file");
    }

    /**
     * Opens a file another program asked Jasper to open (a second launch, the {@code jasper} command, the macOS open-file
     * event) in window {@code window}, when it is an existing regular file: on a virtual thread, through the editor a
     * plugin provides, else by revealing it in the file manager. Never the desktop's edit or open action: the edit action
     * could hand the file straight back to Jasper, and the open action runs programs and scripts.
     */
    void openRequestedFile(Path file, UUID window) {
        openRequestedFile(file, window, new dev.jasper.app.platform.ConfigEditor());
    }

    /** As {@link #openRequestedFile(Path, UUID)}, falling back to {@code desktop}; tests inject its actions. */
    void openRequestedFile(Path file, UUID window, dev.jasper.app.platform.ConfigEditor desktop) {
        openFile(file, 0, window, desktop::revealOnly, "Could not open a requested file");
    }

    private void openFile(Path file, int line, UUID window, Consumer<Path> fallback, String failed) {
        Thread.ofVirtual().name("jasper-open-file").start(() -> {
            try {
                if (!java.nio.file.Files.isRegularFile(file)) return;
                PluginRuntime runtime = plugins;
                if (runtime == null) fallback.accept(file);
                else runtime.open(file, line > 0 ? OptionalInt.of(line) : OptionalInt.empty(), Optional.of(window), fallback);
            } catch (RuntimeException failure) {
                LOG.log(System.Logger.Level.WARNING, failed, failure);
            }
        });
    }''', 1),
    ('''    /**
     * The handoff, the macOS reopen event and the resident indicator share this: raise what is open, or open the
     * first window where a new shell starts. {@code trigger} names the caller for start-up timing.
     */
    public void openOrRaise(String trigger) {
        if (quitting || stopped) return;
        if (windows.isEmpty()) openWindow(null, false, trigger);
        else raiseTerminal();
    }

    /** As {@link #openOrRaise(String)}, opening the first window in {@code directory}. */
    public void openOrRaise(Path directory, String trigger) {
        if (quitting || stopped) return;
        if (windows.isEmpty()) openWindow(directory, directory == null, trigger);
        else raiseTerminal();
    }''',
     '''    /**
     * Every outside request to show Jasper ends here: a second launch's handoff, the {@code jasper} command, the macOS
     * reopen and open-file events and the menu bar item (workspace-tabs spec 5.3 and 5.4). With no paths it raises the
     * last active window, or opens one where a new shell starts; each directory opens as a terminal tab and each file
     * in the editor a plugin provides, else revealed ({@code openRequestedFile}), in the last active
     * window, or in a new window (in the first directory) when none is open ({@code OpenPlan}). Plan 4 of the
     * workspace-tabs series retargets this method to the main window. {@code trigger} names the caller for start-up
     * timing. EDT.
     *
     * @param paths absolute paths to open; none raises
     * @param trigger what asked, for the window's timing line
     */
    public void open(List<Path> paths, String trigger) {
        if (quitting || stopped) return;
        TerminalWindow target = lastActive != null && windows.contains(lastActive) ? lastActive
            : windows.isEmpty() ? null : windows.iterator().next();
        OpenPlan plan = OpenPlan.of(paths, target != null, java.nio.file.Files::isDirectory, java.nio.file.Files::isRegularFile);
        if (plan.newWindow()) target = openWindow(plan.windowDirectory(), false, trigger);
        else target.toFront();
        openRest(target, plan);
    }

    /**
     * A launch's first window, timed as {@link #newWindow()} times it (a cold start unless a {@code --background} launch
     * warmed up): its tab starts in the first directory {@code paths} names, else where a new shell starts, and the rest
     * of the paths open in it as {@link #open} opens them (plan Ruling 13). EDT.
     *
     * @param paths the launch's absolute paths
     * @return the window; null while the application is quitting
     */
    public TerminalWindow openFirstWindow(List<Path> paths) {
        OpenPlan plan = OpenPlan.of(paths, false, java.nio.file.Files::isDirectory, java.nio.file.Files::isRegularFile);
        TerminalWindow window = openWindow(plan.windowDirectory(), false,
            windowTrigger(windowsOpened, trace.millis(StartupTrace.Milestone.WARMED_UP).isPresent()));
        openRest(window, plan);
        return window;
    }

    private void openRest(TerminalWindow target, OpenPlan plan) {
        if (target == null) return;
        for (Path directory : plan.tabs()) target.content().newTab(directory);
        for (Path file : plan.files()) openRequestedFile(file, target.content().id());
    }''', 1)])
edit(M + "platform/ConfigEditor.java", [
    ('''        throw failure;
    }
}
''', '''        throw failure;
    }

    /**
     * Reveals a file in the file manager and does nothing else: for a file another program asked Jasper to open, which
     * the desktop's edit action could hand straight back to Jasper and its open action could run. Never on the EDT.
     *
     * @param path the file
     * @throws IllegalStateException on the EDT, or when revealing did not work (its failure is suppressed in it)
     */
    public void revealOnly(Path path) {
        if (SwingUtilities.isEventDispatchThread()) throw new IllegalStateException("Editor operations require a worker thread");
        try {
            attempts.get(2).accept(path);
        } catch (RuntimeException exception) {
            var failure = new IllegalStateException("Could not reveal: " + path);
            failure.addSuppressed(exception);
            throw failure;
        }
    }
}
''', 1)])
edit(M + "bootstrap/ApplicationBootstrap.java", [
    ('''            SwingUtilities.invokeLater(() -> application.openOrRaise(JasperApplication.HANDOFF));''',
     '''            SwingUtilities.invokeLater(() -> application.open(java.util.List.of(), JasperApplication.HANDOFF));''', 1)])
PY
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.application.OpenPlanTest' --tests 'dev.jasper.app.application.JasperApplicationResidencyTest' --tests 'dev.jasper.app.application.JasperApplicationBackgroundTest' --tests 'dev.jasper.app.application.ApplicationCommandsTest' --tests 'dev.jasper.app.bootstrap.*' --continue`
Expected: PASS (`OpenPlanTest` 4; the background and residency tests reach the window exactly as before). Then
`./gradlew :jasper-app:test --tests 'dev.jasper.app.application.JasperApplicationPluginsTest' --tests 'dev.jasper.app.platform.ConfigEditorTest'`
and `./gradlew :jasper-app:check`: PASS.

- [ ] **Step 5: Commit**

```bash
git add jasper-app/src
git commit -F - <<'EOF'
Single instance: every outside request opens through one method

JasperApplication.open(paths, trigger) replaces openOrRaise: with no
paths it raises the last active window or opens one; directories open
as terminal tabs and files in a plugin's editor, in the last active
window or a new one (in the first directory). A launch's first window
takes its first directory and is still timed as a cold start. A file
from outside is revealed when no plugin provides an editor, never
edited by the desktop. OpenPlan is the pure routing; plan 4 retargets
open to the main window.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

---

### Task 5: Handoff protocol 2, UPDATED and the ignored flags

Spec 5.3: the request carries the intent (RAISE, OPEN_PATHS), the launch's working directory, its absolute paths and the
start-up flags it ignored; replies OK, TOKEN, UPDATED, PROTOCOL; an UPDATED owner restarts or says so, and never
releases its endpoint; an owner that did not take a request answers PROTOCOL. The launch side still runs today's flow
(Task 7 replaces it), so in this task only a plain launch hands off, it reports no ignored flags, and the owner takes
every request on receipt (Task 6's `Instance` waits for the event thread, Ruling 22). Rulings 6, 7, 15, 17.

**Files:**
- Modify: `jasper-app/src/main/java/dev/jasper/app/residency/LaunchRequest.java`, `HandoffSocket.java` (whole files)
- Modify: `jasper-app/src/main/java/dev/jasper/app/application/JasperApplication.java` (`endpointReleased` goes; `handedOff` after `open`)
- Modify: `jasper-app/src/main/java/dev/jasper/app/bootstrap/ApplicationBootstrap.java` (`handsOff`, `stale`, `handoffHandler`, the bind in `startDesktop`)
- Modify (test): `residency/LaunchRequestTest.java`, `residency/HandoffSocketTest.java` (whole files);
  `bootstrap/BootstrapTestSupport.java:12`, `bootstrap/ApplicationBootstrapTest.java` (the stale test),
  `application/JasperApplicationResidencyTest.java` (its last two tests), `application/JasperApplicationBackgroundTest.java`,
  `application/ResidentIndicatorTest.java:224-235` (`losingTheEndpointRemovesTheIndicator`: an owner never loses its
  endpoint any more)

**Interfaces:**
- Consumes: `JasperApplication.open(List<Path>, String)` (Task 4), `restart(RestartMode, List<Path>)` (Task 3),
  `AppArguments.workingDirectory()`, `paths()` (Task 2).
- Produces: `record LaunchRequest(String token, Path codeLocation, long codeLocationModified, Intent intent, Path
  workingDirectory, List<Path> paths, List<String> ignoredFlags)` with `enum Intent { RAISE, OPEN_PATHS }`,
  `MAX_LINE_BYTES = 1 << 20`, `STARTUP_FLAGS`, `static LaunchRequest of(Path codeLocation, long modified, Path
  workingDirectory, List<Path> paths, List<String> ignoredFlags)`, `LaunchRequest withToken(String)` (package),
  `boolean fits()`, `boolean anotherBuild(Path source, long modified)`, `enum Response { OK, TOKEN, UPDATED, PROTOCOL }`;
  `static LaunchRequest.Response HandoffSocket.handOff(Path socketPath, Path tokenPath, LaunchRequest request)`,
  `static Function<LaunchRequest, LaunchRequest.Response> HandoffSocket.answering(BiPredicate<LaunchRequest, Boolean>
  taker, Path source, long modified)` (PROTOCOL when the taker declines), `static boolean HandoffSocket.pathFits(Path)`,
  `static String HandoffSocket.tooLong(Path)`; `public void JasperApplication.handedOff(List<Path> paths, List<String>
  ignoredFlags, boolean updated)`, `static String JasperApplication.ignoredFlagsTitle(List<String>)`;
  `static BiConsumer<LaunchRequest, Boolean> ApplicationBootstrap.handoffHandler(JasperApplication)` (runs on the EDT
  it is called on). Task 6 builds on `handOff`, `answering`, `pathFits`, `tooLong`, `of` and `fits`; Task 7 on
  `handoffHandler`.

- [ ] **Step 1: Write the failing tests**

Replace `jasper-app/src/test/java/dev/jasper/app/residency/LaunchRequestTest.java` with:

```java
package dev.jasper.app.residency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

/** Spec 5.3: handoff protocol 2. */
class LaunchRequestTest {
    @TempDir Path home;

    private static String b64(String text) { return Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8)); }

    private static String line(String... fields) { return String.join("\t", fields) + "\n"; }

    /** Windows paths cannot hold a tab or a newline, so there is nothing to carry. */
    @Test @DisabledOnOs(OS.WINDOWS) void aRequestSurvivesEncodingWithSeparatorsAndCommasInItsPaths() {
        var request = new LaunchRequest("tok-en_123", Path.of("/Users/a b\tc/Jasper.app/Contents/app/jasper-app.jar"),
            1_726_500_000_000L, LaunchRequest.Intent.OPEN_PATHS, Path.of("/work/dir\nx"),
            List.of(Path.of("/work/notes ü.txt"), Path.of("/work/a,b"), Path.of("/work/t\tab")), List.of("--safe-mode", "--config"));
        String encoded = request.encode();
        assertThat(encoded).startsWith("jasper\t2\ttok-en_123\t").endsWith("\n");
        assertThat(encoded.chars().filter(c -> c == '\t').count()).isEqualTo(8);
        assertThat(encoded.chars().filter(c -> c == '\n').count()).isEqualTo(1);
        assertThat(LaunchRequest.decode(encoded)).isEqualTo(request);
    }

    @Test void aRaiseCarriesNoPathsOrFlagsAndAPathsRequestIsOpenPaths() {
        var raise = LaunchRequest.of(Path.of("/app.jar").toAbsolutePath(), 7L, home, List.of(), List.of());
        assertThat(raise.intent()).isEqualTo(LaunchRequest.Intent.RAISE);
        assertThat(raise.token()).as("filled in when it is sent").isEmpty();
        assertThat(raise.encode()).endsWith("\traise\t" + b64(home.toString()) + "\t\t\n");
        assertThat(LaunchRequest.decode(raise.withToken("t").encode())).isEqualTo(raise.withToken("t"));
        var open = LaunchRequest.of(null, 0L, home, List.of(home.resolve("a.txt")), List.of("--plugin-dir"));
        assertThat(open.intent()).isEqualTo(LaunchRequest.Intent.OPEN_PATHS);
        assertThat(open.codeLocation()).as("an unknown code location").isEqualTo(Path.of(""));
        assertThat(LaunchRequest.decode(open.withToken("t").encode())).isEqualTo(open.withToken("t"));
    }

    @Test void anythingThisBuildCannotUnderstandDecodesToNullRatherThanThrowing() {
        String jar = b64("/x.jar"), folder = b64(home.toString()), path = b64(home.resolve("a.txt").toString()), flag = b64("--safe-mode");
        assertThat(LaunchRequest.decode(line("jasper", "2", "t", jar, "1", "open_paths", folder, path, flag))).isNotNull();
        for (String malformed : new String[]{
                "", "\n", "garbage\n", "jasper\t2\tt\n",
                line("jasper", "2", "t", jar, "1", "open_paths", folder, path, flag, "extra"),
                line("notjasper", "2", "t", jar, "1", "open_paths", folder, path, flag),
                line("jasper", "1", "t", jar, "1", "open_paths", folder, path, flag),
                line("jasper", "3", "t", jar, "1", "open_paths", folder, path, flag),
                line("jasper", "x", "t", jar, "1", "open_paths", folder, path, flag),
                line("jasper", "2", "t", jar, "x", "open_paths", folder, path, flag),
                line("jasper", "2", "t", "!not base64!", "1", "open_paths", folder, path, flag),
                line("jasper", "2", "t", "AA==", "1", "open_paths", folder, path, flag),
                line("jasper", "2", "t", jar, "1", "dance", folder, path, flag),
                line("jasper", "2", "t", jar, "1", "raise", folder, path, flag),
                line("jasper", "2", "t", jar, "1", "open_paths", folder, "", flag),
                line("jasper", "2", "t", jar, "1", "open_paths", folder, b64("notes.txt"), flag),
                line("jasper", "2", "t", jar, "1", "open_paths", b64("relative"), path, flag),
                line("jasper", "2", "t", jar, "1", "open_paths", folder, path + ",", flag),
                line("jasper", "2", "t", jar, "1", "open_paths", folder, path, b64("--background")),
                line("jasper", "2", "t", jar, "1", "open_paths", folder, path, ",")}) {
            assertThat(LaunchRequest.decode(malformed)).as(malformed.strip()).isNull();
        }
    }

    @Test void theRecordRejectsWhatTheWireRejects() {
        Path jar = Path.of("/x.jar");
        assertThatIllegalArgumentException().as("a raise names no paths").isThrownBy(() ->
            new LaunchRequest("t", jar, 1, LaunchRequest.Intent.RAISE, home, List.of(home), List.of()));
        assertThatIllegalArgumentException().as("open paths names some").isThrownBy(() ->
            new LaunchRequest("t", jar, 1, LaunchRequest.Intent.OPEN_PATHS, home, List.of(), List.of()));
        assertThatIllegalArgumentException().isThrownBy(() ->
            new LaunchRequest("t", jar, 1, LaunchRequest.Intent.OPEN_PATHS, home, List.of(Path.of("notes.txt")), List.of()));
        assertThatIllegalArgumentException().isThrownBy(() ->
            new LaunchRequest("t", jar, 1, LaunchRequest.Intent.RAISE, Path.of("relative"), List.of(), List.of()));
        assertThatIllegalArgumentException().isThrownBy(() ->
            new LaunchRequest("t", jar, 1, LaunchRequest.Intent.RAISE, home, List.of(), List.of("--background")));
    }

    @Test void anotherBuildIsAnotherCodeLocationOrTimeButAMatchingUnknownLocationIsNot() {
        Path source = Path.of("/app.jar");
        assertThat(LaunchRequest.of(source, 100L, home, List.of(), List.of()).anotherBuild(source, 100L)).isFalse();
        assertThat(LaunchRequest.of(Path.of("/other.jar"), 100L, home, List.of(), List.of()).anotherBuild(source, 100L)).isTrue();
        assertThat(LaunchRequest.of(source, 999L, home, List.of(), List.of()).anotherBuild(source, 100L)).isTrue();
        assertThat(LaunchRequest.of(null, 0L, home, List.of(), List.of()).anotherBuild(null, 0L)).isFalse();
    }

    @Test void aRequestLongerThanTheLineDoesNotFit() {
        var many = Stream.iterate(0, i -> i + 1).limit(20_000).map(i -> home.resolve("a-long-file-name-number-" + i + ".txt")).toList();
        assertThat(LaunchRequest.of(null, 0L, home, List.of(home), List.of()).fits()).isTrue();
        assertThat(LaunchRequest.of(null, 0L, home, many, List.of()).fits()).isFalse();
    }

    @Test void responsesRoundTripAndAnythingUnrecognisedIsAProtocolError() {
        assertThat(LaunchRequest.Response.OK.line()).isEqualTo("ok\n");
        assertThat(LaunchRequest.Response.UPDATED.line()).isEqualTo("updated\n");
        assertThat(LaunchRequest.Response.TOKEN.line()).isEqualTo("refused\ttoken\n");
        assertThat(LaunchRequest.Response.PROTOCOL.line()).isEqualTo("refused\tprotocol\n");
        for (var response : LaunchRequest.Response.values())
            assertThat(LaunchRequest.Response.of(response.line())).isEqualTo(response);
        for (String reply : new String[]{"", "yes\n", "refused\n", "refused\tok\n", "ok extra\n", "refused\tstale\n", "UPDATED\n"})
            assertThat(LaunchRequest.Response.of(reply)).as(reply.strip()).isEqualTo(LaunchRequest.Response.PROTOCOL);
    }
}
```

Replace `jasper-app/src/test/java/dev/jasper/app/residency/HandoffSocketTest.java` with:

```java
package dev.jasper.app.residency;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class HandoffSocketTest {
    @TempDir Path dir;

    private Path socket() { return dir.resolve("socket"); }
    private Path token() { return dir.resolve("token"); }
    private Path lock() { return dir.resolve("lock"); }

    private HandoffSocket bind(Function<LaunchRequest, LaunchRequest.Response> handler) {
        HandoffSocket endpoint = HandoffSocket.bind(socket(), token(), lock(), handler);
        assertThat(endpoint).as("bound the endpoint").isNotNull();
        return endpoint;
    }

    /** A plain launch of build {@code /app.jar} at {@code modified}, run in this test's folder. */
    private LaunchRequest launch(long modified) { return LaunchRequest.of(Path.of("/app.jar"), modified, dir, List.of(), List.of()); }

    private LaunchRequest.Response handOff(LaunchRequest request) { return HandoffSocket.handOff(socket(), token(), request); }

    @Test void anAcceptedRequestReachesTheHandlerAndTheTokenFileIsOwnerOnly() throws Exception {
        var seen = new ConcurrentLinkedQueue<LaunchRequest>();
        try (HandoffSocket endpoint = bind(request -> { seen.add(request); return LaunchRequest.Response.OK; })) {
            assertThat(handOff(launch(7L))).isEqualTo(LaunchRequest.Response.OK);
            assertThat(seen).singleElement().satisfies(request -> {
                assertThat(request.codeLocation()).isEqualTo(Path.of("/app.jar"));
                assertThat(request.codeLocationModified()).isEqualTo(7L);
                assertThat(request.intent()).isEqualTo(LaunchRequest.Intent.RAISE);
                assertThat(request.workingDirectory()).isEqualTo(dir);
            });
            if (token().getFileSystem().supportedFileAttributeViews().contains("posix")) {
                assertThat(java.nio.file.attribute.PosixFilePermissions
                    .toString(Files.getPosixFilePermissions(token()))).isEqualTo("rw-------");
            }
        }
        assertThat(socket()).doesNotExist();
        assertThat(token()).doesNotExist();
    }

    @Test void aPathsRequestArrivesWhole() throws Exception {
        var seen = new ConcurrentLinkedQueue<LaunchRequest>();
        var request = LaunchRequest.of(Path.of("/app.jar"), 7L, dir, List.of(dir.resolve("notes ü.txt"), dir), List.of("--safe-mode"));
        try (HandoffSocket endpoint = bind(received -> { seen.add(received); return LaunchRequest.Response.OK; })) {
            assertThat(handOff(request)).isEqualTo(LaunchRequest.Response.OK);
            assertThat(seen).containsExactly(request.withToken(Files.readString(token()).strip()));
        }
    }

    @Test void theOwnerRecordsItsProcessIdBesideTheTokenUntilItCloses() throws Exception {
        assertThat(HandoffSocket.ownerPid(token())).as("nothing bound yet").isEqualTo(-1);
        try (HandoffSocket endpoint = bind(request -> LaunchRequest.Response.OK)) {
            assertThat(HandoffSocket.ownerPid(token())).isEqualTo(ProcessHandle.current().pid());
            assertThat(Files.readString(token()).strip()).doesNotContain("\n").hasSizeGreaterThan(20);
            assertThat(handOff(launch(7L))).isEqualTo(LaunchRequest.Response.OK);
        }
        assertThat(HandoffSocket.ownerPid(token())).as("released with the endpoint").isEqualTo(-1);
        assertThat(dir.resolve("token.pid")).doesNotExist();
        Files.writeString(dir.resolve("token.pid"), "not a number\n");
        assertThat(HandoffSocket.ownerPid(token())).isEqualTo(-1);
    }

    @Test void aWrongTokenIsRefusedWithoutReachingTheHandler() throws Exception {
        var seen = new ConcurrentLinkedQueue<LaunchRequest>();
        try (HandoffSocket endpoint = bind(request -> { seen.add(request); return LaunchRequest.Response.OK; })) {
            Files.writeString(token(), "not-the-real-token\n");
            assertThat(handOff(launch(7L))).isEqualTo(LaunchRequest.Response.TOKEN);
            assertThat(seen).as("the handler never saw it").isEmpty();
        }
    }

    @Test void anUpdatedAnswerReachesTheLaunchAndTheEndpointStays() throws Exception {
        try (HandoffSocket endpoint = bind(request -> LaunchRequest.Response.UPDATED)) {
            assertThat(handOff(launch(9L))).isEqualTo(LaunchRequest.Response.UPDATED);
            assertThat(handOff(launch(9L))).as("an update never releases the endpoint").isEqualTo(LaunchRequest.Response.UPDATED);
            assertThat(socket()).exists();
        }
    }

    @Test void theOwnersAnswerIsUpdatedForAnotherBuildAndProtocolWhenItDidNotTakeTheRequest() {
        var seen = new ConcurrentLinkedQueue<String>();
        var answer = HandoffSocket.answering((request, updated) -> seen.add(request.intent() + " " + updated), Path.of("/app.jar"), 7L);
        assertThat(answer.apply(LaunchRequest.of(Path.of("/app.jar"), 7L, dir, List.of(dir), List.of())))
            .isEqualTo(LaunchRequest.Response.OK);
        assertThat(answer.apply(LaunchRequest.of(Path.of("/new.jar"), 9L, dir, List.of(), List.of("--safe-mode"))))
            .isEqualTo(LaunchRequest.Response.UPDATED);
        assertThat(seen).containsExactly("OPEN_PATHS false", "RAISE true");
        var declined = HandoffSocket.answering((request, updated) -> false, Path.of("/app.jar"), 7L);
        assertThat(declined.apply(LaunchRequest.of(Path.of("/new.jar"), 9L, dir, List.of(), List.of())))
            .as("an owner that did not take the request has not answered (spec 5.3)").isEqualTo(LaunchRequest.Response.PROTOCOL);
    }

    @Test void aRequestNearTheLineLimitArrivesWhole() throws Exception {
        // The owner reads in chunks as they come: only 500 ms without progress ends a read, never its total time.
        var paths = new java.util.ArrayList<Path>();
        int encoded = 0;
        for (int i = 0; encoded < LaunchRequest.MAX_LINE_BYTES - 64 * 1024; i++) {
            Path path = dir.resolve("a-file-with-a-rather-long-name-to-fill-the-line-" + i + ".txt");
            paths.add(path);
            encoded += java.util.Base64.getEncoder().encodeToString(path.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)).length() + 1;
        }
        var request = LaunchRequest.of(Path.of("/app.jar"), 7L, dir, paths, List.of());
        assertThat(request.encode().length()).isBetween(LaunchRequest.MAX_LINE_BYTES - 64 * 1024, LaunchRequest.MAX_LINE_BYTES);
        assertThat(request.fits()).isTrue();
        var seen = new ConcurrentLinkedQueue<LaunchRequest>();
        try (HandoffSocket endpoint = bind(received -> { seen.add(received); return LaunchRequest.Response.OK; })) {
            assertThat(handOff(request)).isEqualTo(LaunchRequest.Response.OK);
            assertThat(seen).hasSize(1);
            assertThat(seen.peek().paths()).isEqualTo(paths);
        }
    }

    @Test void handingOffToNothingFailsQuietly() {
        // No socket, no token, nothing was ever bound here. Contrast with the killed owner below.
        assertThat(handOff(launch(1L))).isEqualTo(LaunchRequest.Response.PROTOCOL);
    }

    @Test void aTokenWhoseOwnerWasKilledFailsAtTheConnectRatherThanTheToken() throws Exception {
        // The post-SIGKILL state: the token and the socket file both survived, nothing is listening.
        Files.createFile(socket());
        Files.writeString(token(), "left-over-token\n");
        assertThat(handOff(launch(1L))).isEqualTo(LaunchRequest.Response.PROTOCOL);
    }

    @Test void anUnknownCodeLocationHandsOffInsteadOfCrashingTheLauncher() throws Exception {
        var seen = new ConcurrentLinkedQueue<LaunchRequest>();
        try (HandoffSocket endpoint = bind(request -> { seen.add(request); return LaunchRequest.Response.OK; })) {
            assertThat(handOff(LaunchRequest.of(null, 0L, dir, List.of(), List.of()))).isEqualTo(LaunchRequest.Response.OK);
            assertThat(seen).singleElement().extracting(LaunchRequest::codeLocation).isEqualTo(Path.of(""));
        }
    }

    @Test void aTokenThatCannotBeWrittenDeclinesAndLeavesNoSocketBehind() {
        // The token gates every request, so an endpoint without one must not exist at all.
        assertThat(HandoffSocket.bind(socket(), dir.resolve("absent/directory/token"), lock(),
            request -> LaunchRequest.Response.OK)).isNull();
        assertThat(socket()).as("a half-built endpoint is cleaned up, not left to confuse the next bind").doesNotExist();
    }

    @Test void aSocketFileLeftByACrashIsReplacedButALiveOwnerIsNot() throws Exception {
        Files.createFile(socket());
        Files.writeString(token(), "left-over\n");
        try (HandoffSocket endpoint = bind(request -> LaunchRequest.Response.OK)) {
            assertThat(handOff(launch(1L))).as("the stale file was replaced by a working endpoint").isEqualTo(LaunchRequest.Response.OK);
            assertThat(HandoffSocket.bind(socket(), token(), lock(), request -> LaunchRequest.Response.OK)).isNull();
        }
    }

    @Test void aLivenessProbeLogsQuietlyRatherThanAlarmingly() throws Exception {
        java.util.logging.Logger logger = java.util.logging.Logger.getLogger(HandoffSocket.class.getName());
        java.util.logging.Level previousLevel = logger.getLevel();
        boolean previousUseParentHandlers = logger.getUseParentHandlers();
        var records = new ConcurrentLinkedQueue<java.util.logging.LogRecord>();
        java.util.logging.Handler handler = new java.util.logging.Handler() {
            @Override public void publish(java.util.logging.LogRecord record) { records.add(record); }
            @Override public void flush() { }
            @Override public void close() { }
        };
        logger.setLevel(java.util.logging.Level.ALL);
        logger.setUseParentHandlers(false);
        logger.addHandler(handler);
        try {
            try (HandoffSocket endpoint = bind(request -> LaunchRequest.Response.OK)) {
                assertThat(HandoffSocket.bind(socket(), token(), lock(), request -> LaunchRequest.Response.OK)).isNull();
                // One acceptor serves in order: a real reply proves the probe was served.
                assertThat(handOff(launch(1L))).isEqualTo(LaunchRequest.Response.OK);
            }
        } finally {
            logger.removeHandler(handler);
            logger.setLevel(previousLevel);
            logger.setUseParentHandlers(previousUseParentHandlers);
        }
        assertThat(records).as("a normal liveness probe must not log an alarming, stack-trace-bearing WARNING")
            .allSatisfy(record -> {
                assertThat(record.getLevel()).isEqualTo(java.util.logging.Level.FINE);
                assertThat(record.getThrown()).as("no stack trace for ordinary traffic").isNull();
            });
    }

    @Test void aPersistentHandlerFailureBacksOffButTheLoopKeepsServing() throws Exception {
        var callCount = new java.util.concurrent.atomic.AtomicInteger();
        int failuresBeforeSuccess = 2;
        try (HandoffSocket endpoint = bind(request -> {
            if (callCount.incrementAndGet() <= failuresBeforeSuccess) throw new RuntimeException("simulated failure");
            return LaunchRequest.Response.OK;
        })) {
            long started = System.nanoTime();
            for (int i = 0; i < failuresBeforeSuccess; i++)
                assertThat(handOff(launch(1L))).isEqualTo(LaunchRequest.Response.PROTOCOL);
            long elapsedMillis = (System.nanoTime() - started) / 1_000_000;
            assertThat(elapsedMillis).as("the loop must back off between failures, not spin").isGreaterThanOrEqualTo(40);
            assertThat(handOff(launch(1L))).isEqualTo(LaunchRequest.Response.OK);
            assertThat(callCount.get()).isEqualTo(failuresBeforeSuccess + 1);
        }
    }

    @Test void aPathTooLongForTheOperatingSystemDeclinesInsteadOfThrowing() throws Exception {
        Path deep = dir;
        for (int i = 0; i < 12; i++) deep = deep.resolve("a-directory-with-a-long-name");
        Files.createDirectories(deep);
        assertThat(deep.toString().length()).isGreaterThan(104);
        assertThat(HandoffSocket.bind(deep.resolve("socket"), deep.resolve("token"), deep.resolve("lock"),
            request -> LaunchRequest.Response.OK)).isNull();
        assertThat(HandoffSocket.pathFits(deep.resolve("socket"))).isFalse();
        assertThat(HandoffSocket.pathFits(socket())).isTrue();
        assertThat(HandoffSocket.tooLong(deep.resolve("socket")))
            .isEqualTo("Jasper's home folder path is too long for its socket: " + deep.resolve("socket"));
    }

    @Test void theCodeLocationOfThisBuildIsResolvableAndItsTimestampIsReadable() {
        Path source = HandoffSocket.codeLocation();
        assertThat(source).as("tests run from a jar or a classes directory").isNotNull();
        assertThat(source.isAbsolute()).isTrue();
        assertThat(HandoffSocket.lastModified(source)).isPositive();
        assertThat(HandoffSocket.lastModified(dir.resolve("absent"))).isZero();
    }

    @Test void theAcceptLoopSurvivesAConnectionThatSendsNothingUsable() throws Exception {
        try (HandoffSocket endpoint = bind(request -> LaunchRequest.Response.OK)) {
            try (var junk = SocketChannel.open(UnixDomainSocketAddress.of(socket()))) {
                junk.write(java.nio.ByteBuffer.wrap("nonsense\n".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            }
            assertThat(handOff(launch(1L))).as("still serving after junk").isEqualTo(LaunchRequest.Response.OK);
        }
    }

    @Test void aClientThatConnectsAndSaysNothingCannotHoldTheEndpoint() throws Exception {
        try (HandoffSocket endpoint = bind(request -> LaunchRequest.Response.OK);
             SocketChannel silent = SocketChannel.open(UnixDomainSocketAddress.of(socket()))) {
            // The silent peer occupies the accept thread until its read deadline expires.
            assertThat(handOff(launch(1L))).isEqualTo(LaunchRequest.Response.OK);
        }
        try (HandoffSocket endpoint = bind(request -> LaunchRequest.Response.OK)) {
            assertThat(handOff(launch(1L))).isEqualTo(LaunchRequest.Response.OK);
        }
    }

    @Test void afterClosingNoFurtherRequestIsAccepted() throws Exception {
        HandoffSocket endpoint = bind(request -> LaunchRequest.Response.OK);
        assertThat(handOff(launch(1L))).isEqualTo(LaunchRequest.Response.OK);
        endpoint.close();
        endpoint.close(); // Idempotent.
        // Re-create the token, so the refusal comes from the dead listener rather than a missing file.
        Files.writeString(token(), "left-over-token\n");
        assertThat(handOff(launch(1L))).isEqualTo(LaunchRequest.Response.PROTOCOL);
        try (ServerSocketChannel proof = ServerSocketChannel.open(StandardProtocolFamily.UNIX)) {
            proof.bind(UnixDomainSocketAddress.of(socket()));  // The path was released.
        }
        Files.deleteIfExists(socket());
    }

    @Test void livenessFollowsTheEndpoint() throws Exception {
        assertThat(HandoffSocket.live(socket())).as("nothing bound yet").isFalse();
        try (HandoffSocket endpoint = bind(request -> LaunchRequest.Response.OK)) {
            assertThat(HandoffSocket.live(socket())).isTrue();
        }
        assertThat(HandoffSocket.live(socket())).isFalse();
    }
}
```

Then run from the worktree root:

```bash
python3 -I - <<'PY'
"""Workspace tabs 1, Task 5 step 1: the application's and the bootstrap's tests of protocol 2."""
import pathlib
def edit(name, edits):
    path = pathlib.Path(name); text = path.read_text(encoding="utf-8")
    for old, new, count in edits:
        if text.count(old) != count: raise SystemExit(f"{name}: expected {count} of {old!r}, found {text.count(old)}")
        text = text.replace(old, new)
    path.write_text(text, encoding="utf-8")
def cut(name, start, stop, replacement=""):
    path = pathlib.Path(name); text = path.read_text(encoding="utf-8")
    if text.count(start) != 1 or text.count(stop) != 1: raise SystemExit(f"{name}: anchors {start!r} / {stop!r} not unique")
    a = text.index(start); b = text.index(stop)
    if b < a: raise SystemExit(f"{name}: {stop!r} comes before {start!r}")
    path.write_text(text[:a] + replacement + text[b:], encoding="utf-8")
T = "jasper-app/src/test/java/dev/jasper/app/"
cut(T + "application/ResidentIndicatorTest.java",
    '''    @Test void losingTheEndpointRemovesTheIndicator() throws Exception {''',
    '''    @Test void openAfterQuitIsHarmless() throws Exception {''')
edit(T + "bootstrap/BootstrapTestSupport.java", [
    ('''{ return ApplicationBootstrap.handoffHandler(app, source, modified); }''',
     '''{ return dev.jasper.app.residency.HandoffSocket.answering((request, updated) -> {
        javax.swing.SwingUtilities.invokeLater(() -> ApplicationBootstrap.handoffHandler(app).accept(request, updated));
        return true;
    }, source, modified); }''', 1)])
cut(T + "bootstrap/ApplicationBootstrapTest.java",
    '''    @Test void staleFlagsADifferentPathOrModificationTimeButNotAMatchingUnresolvedSource() {''',
    '''    @Test void loginItemReconcilerDedupesAndRunsOffTheCallingThread() throws Exception {''')
cut(T + "application/JasperApplicationResidencyTest.java",
    '''    @Test void aStaleRequestEndsResidencyAndShutsDownWhenNoWindowsRemain() throws Exception {''', '''\n}\n''',
    '''    @Test void anUpdatedLaunchWithNoWindowRestartsIntoTheNewBuildWithItsPaths(@org.junit.jupiter.api.io.TempDir Path dir) throws Exception {
        var terminated = new CountDownLatch(1);
        var spawned = new java.util.concurrent.ConcurrentLinkedQueue<List<String>>();
        JasperApplication application = application(terminated::countDown);
        edt(() -> {
            application.residency(true);
            application.restartPlanner = mode -> java.util.Optional.of(List.of("/opt/jasper/bin/Jasper"));
            application.spawner = spawned::add;
            application.staleBuild(() -> true);
        });
        Path notes = dir.resolve("notes.txt");
        var handler = BootstrapTestSupport.handoffHandler(application, Path.of("/old.jar"), 1L);
        assertThat(handler.apply(LaunchRequest.of(Path.of("/new.jar"), 2L, dir, List.of(notes), List.of())))
            .isEqualTo(LaunchRequest.Response.UPDATED);
        assertThat(terminated.await(5, TimeUnit.SECONDS)).as("the old build exits").isTrue();
        assertThat(spawned).as("its replacement waits for it, then opens the launch's paths").containsExactly(
            List.of("/opt/jasper/bin/Jasper", "--replacing", Long.toString(ProcessHandle.current().pid()), notes.toString()));
    }

    @Test void anUpdatedLaunchFromAnotherInstallOpensAsAnOrdinaryOne() throws Exception {
        var spawned = new java.util.concurrent.ConcurrentLinkedQueue<List<String>>();
        JasperApplication application = application(() -> { });
        try {
            edt(() -> {
                application.residency(true);
                application.restartPlanner = mode -> java.util.Optional.of(List.of("/opt/jasper/bin/Jasper"));
                application.spawner = spawned::add;
                application.staleBuild(() -> false);
            });
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> edt(() -> application.handedOff(List.of(), List.of(), true)))
                .as("this build's own jar is unchanged: the request opens a window here")
                .hasRootCauseInstanceOf(java.awt.HeadlessException.class);
            assertThat(spawned).isEmpty();
        } finally {
            edt(application::quitNow);
        }
    }

    @Test void theStartUpFlagsALaunchCouldNotApplyAreNamed() throws Exception {
        JasperApplication application = application(() -> { });
        try {
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> edt(() ->
                    application.handedOff(List.of(), List.of("--safe-mode", "--config"), false)))
                .hasRootCauseInstanceOf(java.awt.HeadlessException.class);
            edt(() -> assertThat(application.notificationCenter().history()).extracting(entry -> entry.request().title())
                .contains("Jasper is already running: quit it first to start with --safe-mode and --config"));
        } finally {
            edt(application::quitNow);
        }
        assertThat(JasperApplication.ignoredFlagsTitle(List.of("--plugin-dir")))
            .isEqualTo("Jasper is already running: quit it first to start with --plugin-dir");
        assertThat(JasperApplication.ignoredFlagsTitle(List.of("--safe-mode", "--config", "--plugin-dir")))
            .isEqualTo("Jasper is already running: quit it first to start with --safe-mode, --config and --plugin-dir");
    }

    @Test void aMatchingRequestIsAcceptedAndLeavesResidencyAlone() throws Exception {
        JasperApplication application = application(() -> { });
        edt(() -> application.residency(true));
        edt(application::quit);   // So the queued open is inert and no window is created.
        var handler = BootstrapTestSupport.handoffHandler(application, Path.of("/same.jar"), 7L);
        assertThat(handler.apply(LaunchRequest.of(Path.of("/same.jar"), 7L, DesktopTestSupport.HOME, List.of(), List.of())))
            .isEqualTo(LaunchRequest.Response.OK);
        edt(() -> { });
        assertThat(application.resident()).as("a matching request does not touch residency").isTrue();
    }''')
edit(T + "application/JasperApplicationBackgroundTest.java", [
    ('''    @Test void anOrdinaryNativeQuitRequestIsCancelledAndGoesToTheBackground() throws Exception {''',
     '''    @Test @DisabledOnOs(OS.WINDOWS) void anUpdatedLaunchWithAWindowOpenSaysSoAndRelaunchesNothing() throws Exception {
        // Spec 5.3: with windows open the owner carries the request out itself and says it was updated.
        AppDirs dirs = new AppDirs(home, home.resolve("config.toml"), home.resolve("logs"));
        Path dev = home.resolve("dev-plugin");
        PluginJars.build(dev, "guard.jar", PluginJars.descriptor("dev.example.guard", "1.0.0", "fix.guard.Main"),
            Map.of("fix.guard.Main", GUARD_FIXTURE), List.of());
        var spawned = new CopyOnWriteArrayList<List<String>>();
        var planned = new AtomicInteger();
        var terminated = new CountDownLatch(1);
        JasperApplication application = settled(terminated::countDown, true);
        try {
            edt(() -> {
                application.startPlugins(null, dev, false, dirs);
                application.restartPlanner = mode -> {
                    planned.incrementAndGet();
                    return Optional.of(List.of("/Applications/Jasper.app/Contents/MacOS/Jasper"));
                };
                application.spawner = spawned::add;
                application.staleBuild(() -> true);
            });
            // The plugin's window is open; the request then opens a terminal window, which cannot be composed headlessly.
            assertThatThrownBy(() -> edt(() -> application.handedOff(List.of(), List.of(), true))).hasRootCauseInstanceOf(HeadlessException.class);
            assertThat(planned).as("an update never takes an open window down").hasValue(0);
            assertThat(spawned).isEmpty();
            edt(() -> assertThat(application.notificationCenter().history()).extracting(entry -> entry.request().title())
                .contains("Jasper was updated"));
        } finally {
            edt(application::quitNow);
            assertThat(terminated.await(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test void anOrdinaryNativeQuitRequestIsCancelledAndGoesToTheBackground() throws Exception {''', 1)])
PY
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :jasper-app:compileTestJava`
Expected: FAIL to compile: "cannot find symbol … LaunchRequest.of", "… Intent", "… answering", "… handedOff",
"… ignoredFlagsTitle".

- [ ] **Step 3: Protocol 2 and the endpoint**

Replace `jasper-app/src/main/java/dev/jasper/app/residency/LaunchRequest.java` with:

```java
package dev.jasper.app.residency;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * One line of the handoff protocol (workspace-tabs spec 5.3): what a second launch asks the home's owner to do.
 * Protocol 2 carries the launch's intent, its working directory, its paths (absolute: resolved against that directory)
 * and the start-up flags it could not apply.
 *
 * @param token the token beside the owner's socket; empty until the request is sent
 * @param codeLocation the launch's jar or classes directory; empty when unknown
 * @param codeLocationModified its modification time
 * @param intent what the launch asks
 * @param workingDirectory the directory the launch ran in, absolute
 * @param paths the paths to open, absolute; some exactly when the intent is OPEN_PATHS
 * @param ignoredFlags the start-up flags the launch could not apply, from {@link #STARTUP_FLAGS}
 */
public record LaunchRequest(String token, Path codeLocation, long codeLocationModified, Intent intent, Path workingDirectory,
                            List<Path> paths, List<String> ignoredFlags) {
    /** The longest request line, newline included; a longer one is not one of ours. */
    public static final int MAX_LINE_BYTES = 1 << 20;
    /** The start-up flags a second launch can report as ignored (spec 5.2). */
    public static final Set<String> STARTUP_FLAGS = Set.of("--safe-mode", "--config", "--plugin-dir");
    private static final String MAGIC = "jasper";
    private static final int PROTOCOL = 2;
    private static final int FIELDS = 9;

    /** Rejects nulls, a relative path, an intent its paths contradict and a flag that is not a start-up flag. */
    public LaunchRequest {
        Objects.requireNonNull(token, "token");
        Objects.requireNonNull(codeLocation, "codeLocation");
        Objects.requireNonNull(intent, "intent");
        Objects.requireNonNull(workingDirectory, "workingDirectory");
        paths = List.copyOf(paths);
        ignoredFlags = List.copyOf(ignoredFlags);
        if (!workingDirectory.isAbsolute()) throw new IllegalArgumentException("The working directory is not absolute: " + workingDirectory);
        for (Path path : paths) if (!path.isAbsolute()) throw new IllegalArgumentException("A path is not absolute: " + path);
        if ((intent == Intent.OPEN_PATHS) == paths.isEmpty())
            throw new IllegalArgumentException(intent + " with " + paths.size() + " paths");
        for (String flag : ignoredFlags)
            if (!STARTUP_FLAGS.contains(flag)) throw new IllegalArgumentException("Not a start-up flag: " + flag);
    }

    /**
     * A launch's request: OPEN_PATHS when it names paths, RAISE otherwise. The token is filled in when it is sent.
     *
     * @param codeLocation the launch's jar or classes directory, or null when unknown
     * @param codeLocationModified its modification time
     * @param workingDirectory the directory the launch ran in, absolute
     * @param paths the paths to open, absolute
     * @param ignoredFlags the start-up flags the launch could not apply
     * @return the request
     */
    public static LaunchRequest of(Path codeLocation, long codeLocationModified, Path workingDirectory, List<Path> paths,
                                   List<String> ignoredFlags) {
        return new LaunchRequest("", codeLocation == null ? Path.of("") : codeLocation, codeLocationModified,
            paths.isEmpty() ? Intent.RAISE : Intent.OPEN_PATHS, workingDirectory, paths, ignoredFlags);
    }

    /** What the launch asks. */
    public enum Intent {
        /** Raise or restore Jasper's window. */
        RAISE,
        /** Open the paths: each file in the editor, each directory as a terminal tab. */
        OPEN_PATHS
    }

    /** This request signed with {@code value}. */
    LaunchRequest withToken(String value) {
        return new LaunchRequest(value, codeLocation, codeLocationModified, intent, workingDirectory, paths, ignoredFlags);
    }

    /**
     * Whether this request, signed with a token, stays within {@link #MAX_LINE_BYTES}.
     *
     * @return true when it can be sent
     */
    public boolean fits() {
        return withToken("t".repeat(64)).encode().getBytes(StandardCharsets.UTF_8).length <= MAX_LINE_BYTES;
    }

    /**
     * Whether the launch ran another build than {@code source} at {@code modified}: its code location or its time
     * differs. An unknown location compares equal to itself and to nothing else.
     *
     * @param source the owner's code location, or null when unknown
     * @param modified its modification time
     * @return true for another build
     */
    public boolean anotherBuild(Path source, long modified) {
        return !codeLocation.equals(source == null ? Path.of("") : source) || codeLocationModified != modified;
    }

    /**
     * One newline-terminated line of nine tab-separated fields. Text is base64, so a tab or newline in a path survives;
     * each list is its items' base64, comma-separated.
     */
    String encode() {
        return String.join("\t", MAGIC, Integer.toString(PROTOCOL), token, text(codeLocation.toString()),
            Long.toString(codeLocationModified), intent.name().toLowerCase(Locale.ROOT), text(workingDirectory.toString()),
            list(paths.stream().map(Path::toString)), list(ignoredFlags.stream())) + "\n";
    }

    /** Null for anything this build cannot act on, including another protocol. Never throws. */
    static LaunchRequest decode(String line) {
        String body = line.endsWith("\n") ? line.substring(0, line.length() - 1) : line;
        String[] parts = body.split("\t", -1);
        if (parts.length != FIELDS || !MAGIC.equals(parts[0])) return null;
        try {
            if (Integer.parseInt(parts[1]) != PROTOCOL) return null;
            Intent intent = switch (parts[5]) {
                case "raise" -> Intent.RAISE;
                case "open_paths" -> Intent.OPEN_PATHS;
                default -> null;
            };
            if (intent == null) return null;
            return new LaunchRequest(parts[2], Path.of(untext(parts[3])), Long.parseLong(parts[4]), intent,
                Path.of(untext(parts[6])), items(parts[7]).map(Path::of).toList(), items(parts[8]).toList());
        } catch (IllegalArgumentException malformed) {
            // A bad number, bad base64, an unusable or relative path, a contradicted intent, an unknown flag.
            return null;
        }
    }

    private static String text(String value) {
        return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String untext(String field) {
        return new String(Base64.getDecoder().decode(field), StandardCharsets.UTF_8);
    }

    private static String list(Stream<String> items) {
        return items.map(LaunchRequest::text).collect(Collectors.joining(","));
    }

    private static Stream<String> items(String field) {
        return field.isEmpty() ? Stream.empty() : Arrays.stream(field.split(",", -1)).map(LaunchRequest::untext);
    }

    /** What the owner says back. Anything unrecognised is a protocol error, never an accept. */
    public enum Response {
        /** Taken: the owner carries it out. */
        OK,
        /** Refused: the token was not the owner's. */
        TOKEN,
        /** Taken from another build: the owner carries it out, or restarts into the new build (spec 5.3). */
        UPDATED,
        /** Not understood, not answered, or nothing to send it to. */
        PROTOCOL;

        String line() {
            return switch (this) {
                case OK -> "ok\n";
                case UPDATED -> "updated\n";
                case TOKEN -> "refused\ttoken\n";
                case PROTOCOL -> "refused\tprotocol\n";
            };
        }

        static Response of(String line) {
            return switch (line.strip()) {
                case "ok" -> OK;
                case "updated" -> UPDATED;
                case "refused\ttoken" -> TOKEN;
                default -> PROTOCOL;
            };
        }
    }
}
```

Replace `jasper-app/src/main/java/dev/jasper/app/residency/HandoffSocket.java` with (Task 8 removes the bind lock and the
liveness probe):

```java
package dev.jasper.app.residency;

import dev.jasper.app.platform.CodeLocation;
import dev.jasper.app.platform.OwnerOnlyFiles;
import java.io.IOException;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BiPredicate;
import java.util.function.Function;

/**
 * The handoff endpoint (workspace-tabs spec 5.3). The home's owner serves it; every other launch hands its request to it
 * and exits.
 *
 * <p>The accept loop runs on a <strong>non-daemon</strong> platform thread, and that is deliberate:
 * AWT shuts its event dispatch thread down once nothing is displayable, so this thread is what keeps
 * a windowless resident JVM alive. Closing the endpoint ends the loop, which is how a resident
 * process with no windows exits.
 */
public final class HandoffSocket implements AutoCloseable {
    private static final System.Logger LOG = System.getLogger(HandoffSocket.class.getName());
    /** macOS caps sun_path at 104 bytes; a long home directory can reach it. */
    private static final int MAX_SOCKET_PATH_BYTES = 100;
    /** An answer is a word or two. */
    private static final int MAX_REPLY_BYTES = 256;
    /** The launch's wait for an answer: the owner reads until its peer has been silent for READ_TIMEOUT_MILLIS, then
     *  gives its event thread up to a second to take the request (spec 5.3), so a request queued behind one stalled
     *  peer is still answered inside it. */
    private static final long HANDOFF_TIMEOUT_MILLIS = 3_000;
    /** How long a peer may stay silent while the accept thread reads from it, so one stalled peer holds the endpoint
     *  for at most this long. A real client writes its line at once; a long line arrives in chunks, each resetting it. */
    private static final long READ_TIMEOUT_MILLIS = 500;
    /** Backoff after an accept-loop failure, so a persistent error (e.g. exhausted file
     *  descriptors) cannot spin the loop at 100% CPU retrying instantly. */
    private static final long ACCEPT_FAILURE_BACKOFF_MILLIS = 50;

    private final ServerSocketChannel channel;
    private final Path socketPath;
    private final Path tokenPath;
    private final Path lockPath;
    private final String token;
    private final Function<LaunchRequest, LaunchRequest.Response> handler;
    private final Thread acceptor;
    private volatile boolean closed;

    private HandoffSocket(ServerSocketChannel channel, Path socketPath, Path tokenPath, Path lockPath,
                          String token, Function<LaunchRequest, LaunchRequest.Response> handler) {
        this.channel = channel;
        this.socketPath = socketPath;
        this.tokenPath = tokenPath;
        this.lockPath = lockPath;
        this.token = token;
        this.handler = handler;
        this.acceptor = Thread.ofPlatform().name("jasper-handoff-accept").unstarted(this::acceptLoop);
    }

    /**
     * Becomes the owner, or returns null. Null means another process already owns the endpoint, or
     * it is unavailable here; residency is an optimization, so failing to get it is never fatal.
     */
    public static HandoffSocket bind(Path socketPath, Path tokenPath, Path lockPath,
                              Function<LaunchRequest, LaunchRequest.Response> handler) {
        if (!pathFits(socketPath)) {
            LOG.log(System.Logger.Level.WARNING, tooLong(socketPath));
            return null;
        }
        ServerSocketChannel opened = null;
        try {
            Files.createDirectories(socketPath.getParent());
            OwnerOnlyFiles.restrict(socketPath.getParent(), "rwx------");
            try (FileChannel lockChannel = FileChannel.open(lockPath, StandardOpenOption.CREATE,
                    StandardOpenOption.READ, StandardOpenOption.WRITE);
                 FileLock ignored = lockChannel.lock()) {
                if (Files.exists(socketPath)) {
                    if (owned(socketPath)) return null;
                    Files.deleteIfExists(socketPath);
                }
                opened = ServerSocketChannel.open(StandardProtocolFamily.UNIX);
                opened.bind(UnixDomainSocketAddress.of(socketPath));
                String token = OwnerOnlyFiles.writeToken(tokenPath);
                OwnerOnlyFiles.writeProcessId(ownerPath(tokenPath));
                var endpoint = new HandoffSocket(opened, socketPath, tokenPath, lockPath, token, handler);
                endpoint.acceptor.start();
                return endpoint;
            }
        } catch (IOException | RuntimeException failure) {
            // A bind that got as far as the socket file but no further must not leave it: the next
            // start would find a path nothing is listening on and have to recover from it.
            if (opened != null) {
                try { opened.close(); } catch (IOException ignored) { }
                try { Files.deleteIfExists(socketPath); } catch (IOException ignored) { }
            }
            LOG.log(System.Logger.Level.WARNING,
                "Background residency is unavailable; running as an ordinary application", failure);
            return null;
        }
    }

    /**
     * Hands {@code request} to the home's owner, signed with the token beside its socket, and returns the owner's
     * answer: OK or UPDATED when it took the request; TOKEN or PROTOCOL otherwise, including when nothing answers or no
     * token can be read. Blocks, bounded.
     *
     * @param socketPath the owner's endpoint
     * @param tokenPath the token beside it
     * @param request the launch's request; its token is replaced
     * @return the answer
     */
    public static LaunchRequest.Response handOff(Path socketPath, Path tokenPath, LaunchRequest request) {
        String token = readToken(tokenPath);
        if (token == null) return LaunchRequest.Response.PROTOCOL;
        LaunchRequest signed = request.withToken(token);
        var answer = new CompletableFuture<LaunchRequest.Response>();
        // A wedged owner must not hang the caller, so the exchange is bounded from outside it.
        Thread worker = Thread.ofPlatform().daemon().name("jasper-handoff").start(() -> {
            try (SocketChannel client = SocketChannel.open(UnixDomainSocketAddress.of(socketPath))) {
                write(client, signed.encode());
                // The launcher's own budget, not the server's silent-peer tolerance: this reply may
                // legitimately be queued behind a stalled peer, and must not give up just as it
                // is about to be served. The future below is the hard outer bound either way.
                answer.complete(LaunchRequest.Response.of(readLine(client, HANDOFF_TIMEOUT_MILLIS, MAX_REPLY_BYTES)));
            } catch (IOException | RuntimeException failure) {
                answer.complete(LaunchRequest.Response.PROTOCOL);
            }
        });
        try {
            return answer.get(HANDOFF_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        } catch (TimeoutException | ExecutionException failure) {
            worker.interrupt();
            return LaunchRequest.Response.PROTOCOL;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return LaunchRequest.Response.PROTOCOL;
        }
    }

    /**
     * The owner's answer (spec 5.3): {@code taker} is offered each request and whether the launch ran another build than
     * {@code source} at {@code modified}. When it takes the request the answer is UPDATED for another build and OK
     * otherwise; when it does not, PROTOCOL, which the launch treats as no answer and tries again.
     *
     * @param taker takes a request, or declines it; bounded, since the accept thread waits for it
     * @param source this build's code location, or null when unknown
     * @param modified its modification time
     * @return the endpoint's handler
     */
    public static Function<LaunchRequest, LaunchRequest.Response> answering(BiPredicate<LaunchRequest, Boolean> taker,
                                                                            Path source, long modified) {
        Objects.requireNonNull(taker, "taker");
        return request -> {
            boolean updated = request.anotherBuild(source, modified);
            if (!taker.test(request, updated)) return LaunchRequest.Response.PROTOCOL;
            return updated ? LaunchRequest.Response.UPDATED : LaunchRequest.Response.OK;
        };
    }

    /**
     * Whether {@code socketPath} fits a Unix socket address: macOS allows 104 bytes and Jasper keeps to 100.
     *
     * @param socketPath an endpoint's path
     * @return true when it can be bound
     */
    public static boolean pathFits(Path socketPath) {
        return socketPath.toString().getBytes(StandardCharsets.UTF_8).length <= MAX_SOCKET_PATH_BYTES;
    }

    /**
     * What a launch says, and an owner logs, when its home's socket path is too long to bind (plan Ruling 15).
     *
     * @param socketPath the endpoint's path
     * @return the message
     */
    public static String tooLong(Path socketPath) {
        return "Jasper's home folder path is too long for its socket: " + socketPath;
    }

    /** True while some process accepts connections on the endpoint: a plain launch would hand off to it. */
    public static boolean live(Path socketPath) { return owned(socketPath); }

    /**
     * The process id the current owner recorded beside its token, or -1 when there is none or it cannot be read. A
     * hint, not proof: an owner that died without closing leaves its id behind, so only a successful
     * {@link #handOff} shows the owner was live. Windows needs it before the handoff, to pass on the foreground right,
     * and the not-responding error names it.
     */
    public static long ownerPid(Path tokenPath) {
        try {
            long pid = Long.parseLong(Files.readString(ownerPath(tokenPath), StandardCharsets.UTF_8).strip());
            return pid > 0 ? pid : -1;
        } catch (IOException | RuntimeException unavailable) {
            return -1;
        }
    }

    /** Beside the token rather than in it, so a launcher from another build still reads the token it expects. */
    static Path ownerPath(Path tokenPath) {
        return tokenPath.resolveSibling(tokenPath.getFileName() + ".pid");
    }

    /** The jar or classes directory this build was loaded from; null when it cannot be determined. */
    public static Path codeLocation() {
        return CodeLocation.of(HandoffSocket.class);
    }

    /** Epoch milliseconds, or zero when the file cannot be read; zero never equals a real stamp. */
    public static long lastModified(Path path) {
        if (path == null) return 0;
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (IOException | RuntimeException unavailable) {
            return 0;
        }
    }

    @Override public void close() {
        if (closed) return;
        closed = true;
        try { channel.close(); } catch (IOException ignored) { }
        // On Linux a thread blocked in accept() keeps the listening socket alive until it wakes, so our
        // own dying listener could answer the probe below and pass for a successor. Serving is bounded
        // by READ_TIMEOUT_MILLIS, so the acceptor is always gone well within this.
        if (Thread.currentThread() != acceptor) {
            try { acceptor.join(Duration.ofMillis(2 * READ_TIMEOUT_MILLIS)); }
            catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
        }
        // Under the same lock bind uses: if anything answers on our path now, a successor owns it
        // and these files are its, not ours.
        try (FileChannel lockChannel = FileChannel.open(lockPath, StandardOpenOption.CREATE,
                StandardOpenOption.READ, StandardOpenOption.WRITE);
             FileLock ignored = lockChannel.lock()) {
            if (owned(socketPath)) return;
            Files.deleteIfExists(socketPath);
            Files.deleteIfExists(tokenPath);
            Files.deleteIfExists(ownerPath(tokenPath));
        } catch (IOException | RuntimeException unavailable) {
            // Including OverlappingFileLockException: leave the files rather than risk a successor's.
        }
    }

    private void acceptLoop() {
        while (!closed) {
            try (SocketChannel client = channel.accept()) {
                serve(client);
            } catch (ClosedChannelException stopped) {
                return;
            } catch (IOException | RuntimeException failure) {
                if (closed) return;
                LOG.log(System.Logger.Level.WARNING, "A handoff request failed", failure);
                // A persistent failure -- accept() itself throwing, e.g. because file descriptors
                // are exhausted -- must not spin this loop at 100% CPU: a long-lived resident
                // process has no window on screen for anyone to notice from. A close() during the
                // sleep is still picked up by the loop guard above, at most this much later.
                try {
                    Thread.sleep(ACCEPT_FAILURE_BACKOFF_MILLIS);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    private void serve(SocketChannel client) throws IOException {
        LaunchRequest request = LaunchRequest.decode(readLine(client, READ_TIMEOUT_MILLIS, LaunchRequest.MAX_LINE_BYTES));
        LaunchRequest.Response response;
        if (request == null) response = LaunchRequest.Response.PROTOCOL;
        else if (!MessageDigest.isEqual(token.getBytes(StandardCharsets.UTF_8),
                request.token().getBytes(StandardCharsets.UTF_8))) {
            LOG.log(System.Logger.Level.WARNING, "A handoff request presented the wrong token; refused");
            response = LaunchRequest.Response.TOKEN;
        } else response = handler.apply(request);
        try {
            write(client, response.line());
        } catch (IOException peerGone) {
            // A launch that gave up waiting, or a liveness probe that connects and disconnects without writing: from
            // here both look like a peer that hung up before the reply arrived. Ordinary traffic, not a
            // stack-trace-worthy failure; a genuine failure earlier in this method (reading the request) still
            // propagates and is logged as one by acceptLoop.
            LOG.log(System.Logger.Level.DEBUG, "Could not reply to a handoff request; the peer had already gone");
        }
    }

    /** True when something is listening: a connect that succeeds proves a live owner. */
    private static boolean owned(Path socketPath) {
        try (SocketChannel probe = SocketChannel.open(UnixDomainSocketAddress.of(socketPath))) {
            return true;
        } catch (IOException | RuntimeException gone) {
            return false;
        }
    }

    private static String readToken(Path tokenPath) {
        try {
            String token = Files.readString(tokenPath, StandardCharsets.UTF_8).strip();
            return token.isEmpty() ? null : token;
        } catch (IOException | RuntimeException unavailable) {
            return null;
        }
    }

    /**
     * Bounded read of one line of at most {@code limit} bytes: it ends when the peer has sent nothing for
     * {@code timeoutMillis}, so a peer that connects and says nothing cannot hold the endpoint, while a long line that
     * keeps arriving is read whole.
     */
    private static String readLine(SocketChannel channel, long timeoutMillis, int limit) throws IOException {
        channel.configureBlocking(false);
        ByteBuffer buffer = ByteBuffer.allocate(limit);
        long silence = TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        long deadline = System.nanoTime() + silence;
        int scanned = 0;
        while (buffer.hasRemaining()) {
            int read = channel.read(buffer);
            if (read < 0) break;
            if (read > 0) deadline = System.nanoTime() + silence;
            for (; scanned < buffer.position(); scanned++) {
                if (buffer.get(scanned) == '\n') {
                    return new String(buffer.array(), 0, scanned, StandardCharsets.UTF_8);
                }
            }
            if (read == 0) {
                if (System.nanoTime() >= deadline) break;
                try { Thread.sleep(5); }
                catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); break; }
            }
        }
        return new String(buffer.array(), 0, buffer.position(), StandardCharsets.UTF_8);
    }

    private static void write(SocketChannel channel, String text) throws IOException {
        ByteBuffer buffer = ByteBuffer.wrap(text.getBytes(StandardCharsets.UTF_8));
        while (buffer.hasRemaining()) channel.write(buffer);
    }
}
```

- [ ] **Step 4: The owner answers, restarts or says so; the launch side speaks protocol 2**

Run from the worktree root:

```bash
python3 -I - <<'PY'
"""Workspace tabs 1, Task 5 step 4: handedOff, the handoff handler and the plain launch's request."""
import pathlib
def edit(name, edits):
    path = pathlib.Path(name); text = path.read_text(encoding="utf-8")
    for old, new, count in edits:
        if text.count(old) != count: raise SystemExit(f"{name}: expected {count} of {old!r}, found {text.count(old)}")
        text = text.replace(old, new)
    path.write_text(text, encoding="utf-8")
def cut(name, start, stop, replacement=""):
    path = pathlib.Path(name); text = path.read_text(encoding="utf-8")
    if text.count(start) != 1 or text.count(stop) != 1: raise SystemExit(f"{name}: anchors {start!r} / {stop!r} not unique")
    a = text.index(start); b = text.index(stop)
    if b < a: raise SystemExit(f"{name}: {stop!r} comes before {start!r}")
    path.write_text(text[:a] + replacement + text[b:], encoding="utf-8")
M = "jasper-app/src/main/java/dev/jasper/app/"
A = M + "application/JasperApplication.java"
cut(A, '''    /**\n     * The handoff endpoint is gone, so nothing can reach this process any more.''',
    '''    /**\n     * Who reconciles the user's login items with the setting.''')
edit(A, [
    ('''        for (Path file : plan.files()) openRequestedFile(file, target.content().id());
    }
''', '''        for (Path file : plan.files()) openRequestedFile(file, target.content().id());
    }

    /**
     * A second launch handed its request to this process (workspace-tabs spec 5.3). The start-up flags it could not
     * apply are named in a notice. When it ran another build ({@code updated}) and this process's own jar has changed on
     * disk since it started, a process with no window open restarts into the new build, the replacement opening
     * {@code paths}; one with windows open opens them itself and says "Jasper was updated" with a Restart action.
     * Otherwise, or when the restart cannot happen, the paths open as {@link #open} opens them. EDT.
     *
     * @param paths the launch's absolute paths; none raises
     * @param ignoredFlags the start-up flags the launch could not apply
     * @param updated whether the launch ran another build
     */
    public void handedOff(List<Path> paths, List<String> ignoredFlags, boolean updated) {
        if (quitting || stopped) return;
        if (!ignoredFlags.isEmpty())
            notificationCenter.post(NotificationRequest.of(NotificationsFeature.GENERAL.id(), Severity.INFO, ignoredFlagsTitle(ignoredFlags)));
        boolean newBuild = updated && staleBuild.getAsBoolean();
        boolean windowsWereOpen = windowsOpen();
        if (newBuild && !windowsWereOpen) {
            restart(RestartMode.SAME, paths);
            if (quitting || stopped) return;
            LOG.log(System.Logger.Level.WARNING,
                "Jasper was updated on disk but could not relaunch as the new build; the request opens in the running build");
        }
        if (newBuild && windowsWereOpen)
            notificationCenter.post(NotificationRequest.of(NotificationsFeature.GENERAL.id(), Severity.INFO, "Jasper was updated")
                .withContent("Restart Jasper to run the new build.")
                .withAction(dev.jasper.app.notifications.NotificationAction.of("Restart", this::restartForUpdate)));
        open(paths, HANDOFF);
    }

    /** The notice for start-up flags a second launch could not apply (spec 5.2), naming them in the order given. */
    static String ignoredFlagsTitle(List<String> flags) {
        String named = flags.size() == 1 ? flags.getFirst()
            : String.join(", ", flags.subList(0, flags.size() - 1)) + " and " + flags.getLast();
        return "Jasper is already running: quit it first to start with " + named;
    }

    /** The "Jasper was updated" notice's Restart. EDT. */
    private void restartForUpdate() {
        if (!restart(RestartMode.SAME))
            notificationCenter.post(NotificationRequest.of(NotificationsFeature.GENERAL.id(), Severity.ERROR, "Jasper could not restart")
                .withContent("Jasper could not work out how it was started. Quit and reopen it to run the new build."));
    }
''', 1)])
B = M + "bootstrap/ApplicationBootstrap.java"
edit(B, [
    ('''        Path source = HandoffSocket.codeLocation();
        boolean handed = HandoffSocket.handOff(dirs.daemonSocket(), dirs.daemonToken(), source, HandoffSocket.lastModified(source));
        return handed;''',
     '''        Path source = HandoffSocket.codeLocation();
        var answer = HandoffSocket.handOff(dirs.daemonSocket(), dirs.daemonToken(), LaunchRequest.of(source,
            HandoffSocket.lastModified(source), options.workingDirectory(), options.paths(), java.util.List.of()));
        return answer == LaunchRequest.Response.OK || answer == LaunchRequest.Response.UPDATED;''', 1),
    ('''    /** True when a request comes from a different build than this one. A resident process must not
     *  serve windows built from code it is not running. */
    static boolean stale(LaunchRequest request, Path source, long modified) {
        return !request.codeLocation().equals(source == null ? Path.of("") : source)
            || request.codeLocationModified() != modified;
    }

''', '', 1),
    ('''    /**
     * The resident process's side of a handoff. A stale request also ends residency: the endpoint
     * goes away with the reply, so the last window closing must run the normal bounded shutdown
     * rather than leaving a process with nothing to reach it and no cleanup on the way out.
     */
    static java.util.function.Function<LaunchRequest, LaunchRequest.Response> handoffHandler(
            JasperApplication application, Path source, long modified) {
        return request -> {
            if (stale(request, source, modified)) {
                SwingUtilities.invokeLater(() -> application.endpointReleased());
                return LaunchRequest.Response.STALE;
            }
            SwingUtilities.invokeLater(() -> application.open(java.util.List.of(), JasperApplication.HANDOFF));
            return LaunchRequest.Response.OK;
        };
    }''',
     '''    /** The owner's side of a handoff (workspace-tabs spec 5.3): carries the request out. EDT. */
    static BiConsumer<LaunchRequest, Boolean> handoffHandler(JasperApplication application) {
        return (request, updated) -> application.handedOff(request.paths(), request.ignoredFlags(), updated);
    }''', 1),
    ('''                        handoffHandler(application, source, HandoffSocket.lastModified(source)));''',
     '''                        HandoffSocket.answering((request, updated) -> {
                            SwingUtilities.invokeLater(() -> handoffHandler(application).accept(request, updated));
                            return true;
                        }, source, HandoffSocket.lastModified(source)));''', 1)])
PY
```

- [ ] **Step 5: Run the tests to verify they pass**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.residency.*' --tests 'dev.jasper.app.bootstrap.*' --tests 'dev.jasper.app.application.JasperApplicationResidencyTest' --tests 'dev.jasper.app.application.JasperApplicationBackgroundTest' --tests 'dev.jasper.app.application.OpenPlanTest' --tests 'dev.jasper.app.appearance.ThemeMigrationCoverageTest' --continue`
Expected: PASS: `LaunchRequestTest` (7), `HandoffSocketTest` (20), the residency test's four handoff tests, the
background test's updated launch with a window. Then `./gradlew :jasper-app:check`: PASS.

- [ ] **Step 6: Commit**

```bash
git add jasper-app/src
git commit -F - <<'EOF'
Single instance: handoff protocol 2 and the owner's answer

A launch's request carries its intent (RAISE or OPEN_PATHS), its
working directory, its absolute paths and the start-up flags it could
not apply; the owner answers OK, TOKEN, UPDATED, or PROTOCOL for a
request it did not take, and never releases its endpoint. The owner reads
a request until its peer falls silent, so a long one arrives whole.
handedOff names ignored flags in a notice, restarts into a new build
when no window is open (passing the paths on) and otherwise says "Jasper
was updated" with a Restart action.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

---

### Task 6: Claiming the home

Spec 5.1-5.3 as a unit the bootstrap can call before AWT: the lock, the table, the handoff with its 10 s retry, the
replacement's 30 s wait, the not-responding error, and the owner's early endpoints with their queue. Nothing calls it
yet (Task 7 does). Rulings 2-5, 15-17, 22, 23, 27.

**Files:**
- Create: `jasper-app/src/main/java/dev/jasper/app/bootstrap/EarlyRequests.java`, `Instance.java`, `SingleInstance.java`, `LaunchFailure.java`
- Modify: `docs/design/theme-engine/migration.csv` (one row, for `LaunchFailure`)
- Create (test): `jasper-app/src/test/java/dev/jasper/app/bootstrap/EarlyRequestsTest.java`, `SingleInstanceTest.java`,
  `SingleInstanceProcessTest.java`, `LaunchProbe.java`

**Interfaces:**
- Consumes: `InstanceLock` and `ChildJvm`/`InstanceLockProbe` (Task 1); `AppArguments`, `LaunchAction` (Task 2);
  `InstanceLock.ownerPid` (Task 1); `LaunchRequest.of`, `fits`, `HandoffSocket.handOff`, `answering`, `pathFits`,
  `tooLong`, `codeLocation`, `lastModified` (Task 5); `CommandListener.bind(Path, CommandDispatcher)`;
  `WindowsForeground.allow(String, long)`.
- Produces: `final class EarlyRequests` (`enum Outcome { HELD, RAN, DECLINED }`, `Outcome submit(Runnable)`, `Outcome
  submit(Runnable later, Runnable now)`, `void open()`, `boolean declineIfIdle()`, `boolean holding()`);
  `final class Instance implements AutoCloseable` (`static final Duration TAKE` = 1 s, `static Instance open(InstanceLock,
  AppDirs, Path source, long modified)`, `void serve(BiConsumer<LaunchRequest, Boolean> handoffs, BooleanSupplier
  accepting, Executor edt, CommandDispatcher commands)`, `boolean holding()`, `boolean declineIfIdle()`, `Path
  commandSocket()`, `CommandListener commands()`, `void closeEndpoints()`, `void close()`);
  `final class SingleInstance` (`record Timing(Duration handoff, Duration replacing, Duration poll)` with `STANDARD` =
  10 s, 30 s, 100 ms; `sealed interface Claim` with `record Owner(Instance instance)` and `record Done(int code)`;
  `static Claim claim(AppArguments, AppDirs, Timing, Consumer<String> failure)`; `static String unresponsive(long pid)`);
  `final class LaunchFailure` (`static Consumer<String> reporter(PrintStream error)`). Task 7 wires all of them; Task 8
  drops `Instance`'s bind lock.

- [ ] **Step 1: Write the failing tests**

Create `jasper-app/src/test/java/dev/jasper/app/bootstrap/EarlyRequestsTest.java`:

```java
package dev.jasper.app.bootstrap;

import static dev.jasper.app.workspace.DesktopTestSupport.until;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/** Spec 5.3: requests that reach an owner before it is ready run in order once it is. */
@Timeout(30)
class EarlyRequestsTest {
    private final ConcurrentLinkedQueue<String> ran = new ConcurrentLinkedQueue<>();

    @Test void heldRequestsRunInArrivalOrderOnceOpenedAndLaterOnesAtOnce() throws Exception {
        var requests = new EarlyRequests();
        requests.submit(() -> ran.add("first"));
        requests.submit(() -> ran.add("second"));
        Thread.sleep(100);
        assertThat(ran).as("held until the application is ready").isEmpty();
        requests.open();
        until(() -> !requests.holding());
        assertThat(ran).containsExactly("first", "second");
        String caller = Thread.currentThread().getName();
        requests.submit(() -> ran.add("later on " + Thread.currentThread().getName()));
        assertThat(ran).as("run at once, on the submitting thread").containsExactly("first", "second", "later on " + caller);
    }

    @Test void aRequestThatArrivesWhileTheHeldOnesRunWaitsItsTurn() throws Exception {
        var requests = new EarlyRequests();
        var release = new CountDownLatch(1);
        requests.submit(() -> {
            try { release.await(10, TimeUnit.SECONDS); } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
            ran.add("held");
        });
        requests.open();
        Thread.sleep(100);
        requests.submit(() -> ran.add("arrived while the held one ran"));
        release.countDown();
        until(() -> ran.size() == 2);
        assertThat(ran).containsExactly("held", "arrived while the held one ran");
    }

    @Test void aFailingRequestDoesNotStopTheOthersAndOpeningTwiceRunsEachOnce() throws Exception {
        var requests = new EarlyRequests();
        requests.submit(() -> { throw new IllegalStateException("a broken request"); });
        requests.submit(() -> ran.add("after the failure"));
        requests.open();
        requests.open();
        until(() -> !requests.holding());
        assertThat(ran).containsExactly("after the failure");
    }

    @Test void eachRequestSaysWhatBecameOfItAndOnlyAnIdleQueueCanBeDeclined() throws Exception {
        var requests = new EarlyRequests();
        assertThat(requests.submit(() -> ran.add("held"), () -> ran.add("not run"))).isEqualTo(EarlyRequests.Outcome.HELD);
        assertThat(requests.declineIfIdle()).as("a request is held").isFalse();
        requests.open();
        until(() -> !requests.holding());
        assertThat(requests.submit(() -> ran.add("not run"), () -> ran.add("now"))).isEqualTo(EarlyRequests.Outcome.RAN);
        assertThat(ran).containsExactly("held", "now");
        assertThat(requests.declineIfIdle()).as("opened").isFalse();

        var idle = new EarlyRequests();
        assertThat(idle.declineIfIdle()).isTrue();
        assertThat(idle.submit(() -> ran.add("too late"))).isEqualTo(EarlyRequests.Outcome.DECLINED);
        assertThat(idle.holding()).isFalse();
        idle.open();
        Thread.sleep(100);
        assertThat(ran).as("a declined queue runs nothing").containsExactly("held", "now");
    }
}
```

Create `jasper-app/src/test/java/dev/jasper/app/bootstrap/SingleInstanceTest.java`:

```java
package dev.jasper.app.bootstrap;

import static dev.jasper.app.workspace.DesktopTestSupport.until;
import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.app.commandline.CommandDispatcher;
import dev.jasper.app.commandline.CommandListener;
import dev.jasper.app.commandline.CommandProtocol;
import dev.jasper.app.platform.AppDirs;
import dev.jasper.app.residency.HandoffSocket;
import dev.jasper.app.residency.InstanceLock;
import dev.jasper.app.residency.LaunchRequest;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/**
 * Spec 5.1 to 5.3 in one JVM. A file lock belongs to the process, so a second claim here meets this JVM's lock exactly
 * as another process's; {@code SingleInstanceProcessTest} repeats the essentials across real processes.
 */
@Timeout(60)
class SingleInstanceTest {
    private static final SingleInstance.Timing QUICK =
        new SingleInstance.Timing(Duration.ofMillis(600), Duration.ofMillis(300), Duration.ofMillis(20));
    private static final String NOT_RESPONDING = "Jasper is already running but not responding. Quit it (process 4242) and try again.";
    private static final CommandDispatcher NO_CALLS = invocation -> CompletableFuture.completedFuture(0);

    @TempDir Path home;
    private final ConcurrentLinkedQueue<String> failures = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<AutoCloseable> opened = new ConcurrentLinkedQueue<>();

    @AfterEach void closeAll() throws Exception {
        for (AutoCloseable each : opened) each.close();
    }

    private AppDirs dirs() { return new AppDirs(home, home.resolve("config.toml"), home.resolve("logs")); }

    private SingleInstance.Claim claim(SingleInstance.Timing timing, String... args) {
        var claim = SingleInstance.claim(AppArguments.parse(args, home), dirs(), timing, failures::add);
        if (claim instanceof SingleInstance.Claim.Owner owner) opened.add(owner.instance());
        return claim;
    }

    private Instance owner() {
        var claim = claim(SingleInstance.Timing.STANDARD);
        assertThat(claim).isInstanceOf(SingleInstance.Claim.Owner.class);
        return ((SingleInstance.Claim.Owner) claim).instance();
    }

    /** An owner whose application is ready, with {@code edt} as its event thread and {@code accepting} as its state. */
    private Instance ready(ConcurrentLinkedQueue<LaunchRequest> seen, boolean accepting, java.util.concurrent.Executor edt) {
        Instance instance = owner();
        instance.serve((request, updated) -> seen.add(request), () -> accepting, edt, NO_CALLS);
        until(() -> !instance.holding());
        return instance;
    }

    private InstanceLock heldElsewhere() throws IOException {
        InstanceLock lock = InstanceLock.tryAcquire(dirs().instanceLock()).orElseThrow();
        opened.add(lock);
        return lock;
    }

    /** The id of a process that has certainly ended: a JVM that ran {@code -version}. */
    private static long finishedProcess() throws Exception {
        String java = Path.of(System.getProperty("java.home"), "bin",
            System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java").toString();
        Process process = new ProcessBuilder(java, "-version").redirectErrorStream(true)
            .redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
        assertThat(process.waitFor(60, TimeUnit.SECONDS)).isTrue();
        return process.pid();
    }

    @Test void aFreeHomeMakesTheLaunchItsOwnerWithBothEndpointsBound() {
        Instance instance = owner();
        assertThat(instance.commandSocket()).isEqualTo(dirs().commandSocket());
        assertThat(instance.commands()).isNotNull();
        assertThat(dirs().daemonSocket()).exists();
        assertThat(dirs().instanceLock()).exists();
        assertThat(InstanceLock.ownerPid(dirs().instanceLock())).isEqualTo(ProcessHandle.current().pid());
        assertThat(failures).isEmpty();
    }

    @Test void aSecondLaunchIsAnsweredOnReceiptAndItsRequestRunsOnceTheOwnerIsReady() throws Exception {
        Instance instance = owner();
        Files.writeString(home.resolve("notes.txt"), "");
        assertThat(claim(SingleInstance.Timing.STANDARD, "--safe-mode", "notes.txt")).as("answered before the owner is ready")
            .isEqualTo(new SingleInstance.Claim.Done(0));
        assertThat(claim(SingleInstance.Timing.STANDARD)).isEqualTo(new SingleInstance.Claim.Done(0));
        var seen = new ConcurrentLinkedQueue<LaunchRequest>();
        instance.serve((request, updated) -> seen.add(request), () -> true, Runnable::run, NO_CALLS);
        until(() -> seen.size() == 2);
        assertThat(seen).extracting(LaunchRequest::intent).containsExactly(LaunchRequest.Intent.OPEN_PATHS, LaunchRequest.Intent.RAISE);
        LaunchRequest first = seen.peek();
        assertThat(first.paths()).containsExactly(home.resolve("notes.txt"));
        assertThat(first.workingDirectory()).isEqualTo(home);
        assertThat(first.ignoredFlags()).containsExactly("--safe-mode");
        assertThat(failures).isEmpty();
    }

    @Test void aCallWaitsForTheOwnerAndIsAnsweredWhenItHasRun() throws Exception {
        Instance instance = owner();
        String token = Files.readString(CommandListener.tokenFile(dirs().commandSocket())).strip();
        var reply = CompletableFuture.supplyAsync(() -> call(token, List.of("editor")));
        Thread.sleep(300);
        assertThat(reply).as("held until the owner is ready").isNotDone();
        instance.serve((request, updated) -> { }, () -> true, Runnable::run, invocation -> {
            invocation.out("ran " + invocation.arguments() + "\n");
            return CompletableFuture.completedFuture(7);
        });
        assertThat(reply.get(10, TimeUnit.SECONDS)).isEqualTo(CommandProtocol.out("ran [editor]\n") + CommandProtocol.exit(7));
    }

    @Test void aLoginOrWindowlessLaunchExitsWhileAnOwnerRuns() throws Exception {
        var seen = new ConcurrentLinkedQueue<LaunchRequest>();
        ready(seen, true, Runnable::run);
        assertThat(claim(SingleInstance.Timing.STANDARD, "--background")).isEqualTo(new SingleInstance.Claim.Done(0));
        assertThat(claim(SingleInstance.Timing.STANDARD, "--no-window")).isEqualTo(new SingleInstance.Claim.Done(0));
        Thread.sleep(200);
        assertThat(seen).as("neither handed anything off").isEmpty();
    }

    @Test void anOwnerThatNeverAnswersEndsTheLaunchWithTheErrorAndNoSecondInstance() throws Exception {
        heldElsewhere();
        Files.writeString(dirs().instanceLock().resolveSibling("instance.pid"), "4242\n");
        long began = System.nanoTime();
        assertThat(claim(QUICK)).isEqualTo(new SingleInstance.Claim.Done(1));
        assertThat(Duration.ofNanos(System.nanoTime() - began)).as("it kept trying").isGreaterThanOrEqualTo(Duration.ofMillis(600));
        assertThat(failures).containsExactly(NOT_RESPONDING);
        assertThat(dirs().daemonSocket()).as("it never bound an endpoint").doesNotExist();
        assertThat(SingleInstance.unresponsive(-1)).isEqualTo("Jasper is already running but not responding. Quit it and try again.");
    }

    @Test void aReadyOwnerWhoseEventThreadNeverTakesTheRequestIsNotResponding() throws Exception {
        var seen = new ConcurrentLinkedQueue<LaunchRequest>();
        ready(seen, true, task -> { });
        assertThat(claim(QUICK)).as("spec 5.3: answered only once the event thread took it").isEqualTo(new SingleInstance.Claim.Done(1));
        assertThat(failures).containsExactly(SingleInstance.unresponsive(ProcessHandle.current().pid()));
        assertThat(seen).as("withdrawn, never carried out").isEmpty();
    }

    @Test void anOwnerThatTakesTheConnectionButNeverRepliesIsNotResponding() throws Exception {
        heldElsewhere();
        Files.writeString(dirs().daemonToken(), "a-token\n");
        // Bound and listening, never served: what a stopped owner (kill -STOP) looks like from outside.
        try (ServerSocketChannel stopped = ServerSocketChannel.open(StandardProtocolFamily.UNIX)) {
            stopped.bind(UnixDomainSocketAddress.of(dirs().daemonSocket()));
            assertThat(claim(QUICK)).isEqualTo(new SingleInstance.Claim.Done(1));
        } finally {
            Files.deleteIfExists(dirs().daemonSocket());
        }
        assertThat(failures).containsExactly(SingleInstance.unresponsive(ProcessHandle.current().pid()));
    }

    @Test void aQuittingOwnerTurnsHandoffsAwayAndTheLaunchTakesTheLockOnceItHasGone() throws Exception {
        var seen = new ConcurrentLinkedQueue<LaunchRequest>();
        Instance quitting = ready(seen, false, Runnable::run);
        var second = CompletableFuture.supplyAsync(() -> claim(SingleInstance.Timing.STANDARD));
        Thread.sleep(500);
        assertThat(second).as("it keeps trying while the owner quits").isNotDone();
        quitting.close();
        assertThat(second.get(10, TimeUnit.SECONDS)).isInstanceOf(SingleInstance.Claim.Owner.class);
        assertThat(seen).as("the quitting owner took nothing").isEmpty();
        assertThat(failures).isEmpty();
    }

    @Test void aLaunchThatOutlastsItsOwnerBecomesTheOwner() throws Exception {
        InstanceLock lock = heldElsewhere();
        Thread.ofVirtual().start(() -> {
            try { Thread.sleep(300); } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
            lock.close();
        });
        assertThat(claim(SingleInstance.Timing.STANDARD, "notes.txt")).isInstanceOf(SingleInstance.Claim.Owner.class);
        assertThat(failures).isEmpty();
    }

    @Test void aReplacementWhosePredecessorNeverQuitsReportsIt() throws Exception {
        heldElsewhere();
        long alive = ProcessHandle.current().pid();
        assertThat(claim(QUICK, "--replacing", Long.toString(alive))).isEqualTo(new SingleInstance.Claim.Done(1));
        assertThat(failures).containsExactly(SingleInstance.unresponsive(alive));
    }

    @Test void aReplacementWhosePredecessorHasGoneHandsOffToTheOwnerThatTookItsPlace() throws Exception {
        var seen = new ConcurrentLinkedQueue<LaunchRequest>();
        ready(seen, true, Runnable::run);
        Files.writeString(home.resolve("notes.txt"), "");
        assertThat(claim(QUICK, "--replacing", Long.toString(finishedProcess()), "notes.txt"))
            .as("plan Ruling 27").isEqualTo(new SingleInstance.Claim.Done(0));
        assertThat(seen).singleElement().satisfies(request -> assertThat(request.paths()).containsExactly(home.resolve("notes.txt")));
        assertThat(failures).isEmpty();
    }

    @Test void aLaunchWithMorePathsThanTheLineHoldsSaysSo() {
        owner();
        String[] many = IntStream.range(0, 20_000).mapToObj(i -> "a-long-file-name-number-" + i + ".txt").toArray(String[]::new);
        assertThat(claim(SingleInstance.Timing.STANDARD, many)).isEqualTo(new SingleInstance.Claim.Done(2));
        assertThat(failures).containsExactly("Jasper cannot open that many paths at once.");
    }

    @Test void aHomeThatCannotBeLockedEndsTheLaunchWithTheReason() throws Exception {
        Files.writeString(home.resolve("daemon"), "not a folder");
        assertThat(claim(SingleInstance.Timing.STANDARD)).isEqualTo(new SingleInstance.Claim.Done(1));
        assertThat(failures).singleElement().asString().startsWith("Jasper could not lock its home folder (" + dirs().instanceLock() + "): ");
    }

    @Test void aHomeTooDeepForItsSocketIsSaidSoInsteadOfWaitingForAnAnswer() throws Exception {
        Path deep = home;
        for (int i = 0; i < 4; i++) deep = deep.resolve("a-directory-with-a-long-name");
        var deepDirs = new AppDirs(deep, deep.resolve("config.toml"), deep.resolve("logs"));
        assertThat(deepDirs.daemonSocket().toString().getBytes(StandardCharsets.UTF_8).length).isGreaterThan(100);
        var first = SingleInstance.claim(AppArguments.parse(new String[0], home), deepDirs, QUICK, failures::add);
        assertThat(first).as("it owns the home, unreachable (plan Ruling 15)").isInstanceOf(SingleInstance.Claim.Owner.class);
        opened.add(((SingleInstance.Claim.Owner) first).instance());
        long began = System.nanoTime();
        assertThat(SingleInstance.claim(AppArguments.parse(new String[0], home), deepDirs, SingleInstance.Timing.STANDARD, failures::add))
            .isEqualTo(new SingleInstance.Claim.Done(1));
        assertThat(Duration.ofNanos(System.nanoTime() - began)).as("at once, not after 10 s").isLessThan(Duration.ofSeconds(5));
        assertThat(failures).containsExactly(HandoffSocket.tooLong(deepDirs.daemonSocket()));
        assertThat(failures.peek()).startsWith("Jasper's home folder path is too long for its socket: ");
    }

    @Test void theFailureReporterWritesTheMessageOnStandardError() {
        var error = new ByteArrayOutputStream();
        LaunchFailure.reporter(new PrintStream(error, true, StandardCharsets.UTF_8)).accept(NOT_RESPONDING);
        assertThat(error.toString(StandardCharsets.UTF_8)).as("headless: no dialog").isEqualTo(NOT_RESPONDING + System.lineSeparator());
    }

    private String call(String token, List<String> arguments) {
        try (SocketChannel client = SocketChannel.open(UnixDomainSocketAddress.of(dirs().commandSocket()))) {
            ByteBuffer request = ByteBuffer.wrap(new CommandProtocol.Request(token, home, Optional.empty(), arguments).encode()
                .getBytes(StandardCharsets.UTF_8));
            while (request.hasRemaining()) client.write(request);
            var bytes = new ByteArrayOutputStream();
            ByteBuffer buffer = ByteBuffer.allocate(4096);
            while (client.read(buffer.clear()) >= 0) bytes.write(buffer.array(), 0, buffer.position());
            return bytes.toString(StandardCharsets.UTF_8);
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }
}
```

Create `jasper-app/src/test/java/dev/jasper/app/bootstrap/LaunchProbe.java`:

```java
package dev.jasper.app.bootstrap;

import dev.jasper.app.platform.AppDirs;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;

/**
 * A launch in a child JVM for {@code SingleInstanceProcessTest}: {@code <home> <handoff millis> [jasper arguments...]},
 * claiming the home with those arguments (its working directory is the home). An owner prints "owner", then each request
 * it is handed as "handoff INTENT PATHS", until its standard input closes. Any other launch prints "failed MESSAGE" for a
 * failure, then "done CODE", and exits with that code.
 */
public final class LaunchProbe {
    private LaunchProbe() { }

    public static void main(String[] args) throws Exception {
        Path home = Path.of(args[0]);
        var dirs = new AppDirs(home, home.resolve("config.toml"), home.resolve("logs"));
        var options = AppArguments.parse(Arrays.copyOfRange(args, 2, args.length), home);
        var timing = new SingleInstance.Timing(Duration.ofMillis(Long.parseLong(args[1])), Duration.ofSeconds(30), Duration.ofMillis(50));
        var claim = SingleInstance.claim(options, dirs, timing, message -> say("failed " + message));
        if (claim instanceof SingleInstance.Claim.Done done) {
            say("done " + done.code());
            System.exit(done.code());
        }
        Instance instance = ((SingleInstance.Claim.Owner) claim).instance();
        say("owner");
        instance.serve((request, updated) -> say("handoff " + request.intent() + " " + request.paths().size()), () -> true,
            Runnable::run, invocation -> CompletableFuture.completedFuture(0));
        while (System.in.read() >= 0) { }
        instance.close();
    }

    private static synchronized void say(String line) {
        System.out.println(line);
        System.out.flush();
    }
}
```

Create `jasper-app/src/test/java/dev/jasper/app/bootstrap/SingleInstanceProcessTest.java`:

```java
package dev.jasper.app.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.app.residency.InstanceLock;
import dev.jasper.app.residency.InstanceLockProbe;
import dev.jasper.app.testsupport.ChildJvm;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/** Spec 17, single instance, with real child JVMs. */
@Timeout(240)
class SingleInstanceProcessTest {
    @TempDir Path home;

    private ChildJvm launch(String handoffMillis, String... arguments) throws Exception {
        return ChildJvm.start(LaunchProbe.class, InstanceLock.class,
            Stream.concat(Stream.of(home.toString(), handoffMillis), Stream.of(arguments)).toArray(String[]::new));
    }

    /** Takes the lock, which records its process id, but binds nothing: an owner that never answers. */
    private ChildJvm silentOwner() throws Exception {
        return ChildJvm.start(InstanceLockProbe.class, InstanceLock.class, "hold", home.resolve("daemon").resolve("instance.lock").toString());
    }

    @Test void racingColdStartsEndWithOneOwnerAndEveryOtherLaunchHandingOff() throws Exception {
        int launches = 6;
        var children = new ChildJvm[launches];
        try {
            for (int i = 0; i < launches; i++) children[i] = launch("10000");
            ChildJvm owner = null;
            int owners = 0;
            for (ChildJvm child : children) {
                String first = child.line(90);
                if ("owner".equals(first)) {
                    owners++;
                    owner = child;
                } else {
                    assertThat(first).as("every other launch hands off").isEqualTo("done 0");
                    assertThat(child.exit(30)).isZero();
                }
            }
            assertThat(owners).as("exactly one owner").isEqualTo(1);
            for (int i = 1; i < launches; i++) assertThat(owner.line(30)).isEqualTo("handoff RAISE 0");
            assertThat(owner.alive()).isTrue();
        } finally {
            for (ChildJvm child : children) if (child != null) child.close();
        }
    }

    @Test void aKilledOwnerLeavesNothingThatStopsTheNextLaunch() throws Exception {
        try (ChildJvm owner = launch("10000")) {
            assertThat(owner.line(90)).isEqualTo("owner");
            owner.kill();
        }
        try (ChildJvm next = launch("10000")) {
            assertThat(next.line(90)).as("the lock was released and the stale socket replaced").isEqualTo("owner");
        }
    }

    @Test void anUnresponsiveOwnerYieldsTheErrorAndNeverASecondInstance() throws Exception {
        try (ChildJvm silent = silentOwner()) {
            assertThat(silent.line(90)).isEqualTo("owner");
            try (ChildJvm second = launch("1500")) {
                assertThat(second.line(90)).isEqualTo("failed Jasper is already running but not responding. Quit it (process "
                    + silent.pid() + ") and try again.");
                assertThat(second.line(30)).isEqualTo("done 1");
                assertThat(second.exit(30)).isEqualTo(1);
            }
            assertThat(home.resolve("daemon").resolve("socket")).as("the second launch never bound an endpoint").doesNotExist();
        }
    }

    @Test void theReplacementWaitsForTheLock() throws Exception {
        try (ChildJvm old = silentOwner()) {
            assertThat(old.line(90)).isEqualTo("owner");
            try (ChildJvm replacement = launch("10000", "--replacing", Long.toString(old.pid()))) {
                assertThat(replacement.line(3)).as("it waits while the old process lives").isNull();
                old.finish();
                assertThat(old.exit(30)).isZero();
                assertThat(replacement.line(60)).isEqualTo("owner");
            }
        }
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :jasper-app:compileTestJava`
Expected: FAIL to compile: "cannot find symbol … EarlyRequests", "… SingleInstance", "… Instance", "… LaunchFailure".

- [ ] **Step 3: The queue, the owner, the claim and the failure report**

Create `jasper-app/src/main/java/dev/jasper/app/bootstrap/EarlyRequests.java`:

```java
package dev.jasper.app.bootstrap;

import java.util.ArrayDeque;
import java.util.Objects;

/**
 * Requests that reach the home's owner before its application can take them (workspace-tabs spec 5.3): held in arrival
 * order and run in that order once {@link #open} is called, on a virtual thread; from then on each runs at once on the
 * thread that submits it. A request that throws is logged and the next runs. Any thread.
 */
final class EarlyRequests {
    private static final System.Logger LOG = System.getLogger(EarlyRequests.class.getName());

    /** What became of a submitted request. */
    enum Outcome {
        /** Held: it runs once the queue opens, after the requests before it. */
        HELD,
        /** Run at once, on the submitting thread. */
        RAN,
        /** Turned away: the queue was declined ({@link #declineIfIdle}). */
        DECLINED
    }

    private final Object lock = new Object();
    /** Null once every held request has run, or once the queue was declined. */
    private ArrayDeque<Runnable> held = new ArrayDeque<>();
    private boolean opened;
    private boolean declined;

    /**
     * As {@link #submit(Runnable, Runnable)}, the same request either way.
     *
     * @param request the request
     * @return what became of it
     */
    Outcome submit(Runnable request) { return submit(request, request); }

    /**
     * Holds {@code later} while the queue is not open or still runs the requests before it; otherwise runs {@code now}
     * on this thread. A declined queue runs neither.
     *
     * @param later what runs when the request is held
     * @param now what runs when the queue is open and empty
     * @return what became of the request
     */
    Outcome submit(Runnable later, Runnable now) {
        Objects.requireNonNull(later, "later");
        Objects.requireNonNull(now, "now");
        synchronized (lock) {
            if (declined) return Outcome.DECLINED;
            if (held != null) {
                held.add(later);
                return Outcome.HELD;
            }
        }
        run(now);
        return Outcome.RAN;
    }

    /** The application can take requests: the held ones run in order on a virtual thread. Once; later calls do nothing. */
    void open() {
        synchronized (lock) {
            if (opened || declined) return;
            opened = true;
        }
        Thread.ofVirtual().name("jasper-early-requests").start(this::drain);
    }

    /**
     * Turns every later request away when the queue was never opened and holds none: a launch that turns out not to stay
     * can then exit without dropping a request it already answered. Once declined, it stays declined.
     *
     * @return whether the queue is declined; false when it was opened or holds a request
     */
    boolean declineIfIdle() {
        synchronized (lock) {
            if (declined) return true;
            if (opened || !held.isEmpty()) return false;
            declined = true;
            held = null;
            return true;
        }
    }

    /** Whether requests are still held: before {@link #open}, and while the held ones run. */
    boolean holding() {
        synchronized (lock) { return held != null; }
    }

    private void drain() {
        while (true) {
            Runnable next;
            synchronized (lock) {
                next = held.poll();
                if (next == null) {
                    held = null;
                    return;
                }
            }
            run(next);
        }
    }

    private static void run(Runnable request) {
        try {
            request.run();
        } catch (RuntimeException failure) {
            LOG.log(System.Logger.Level.WARNING, "A launch request failed", failure);
        }
    }
}
```

Create `jasper-app/src/main/java/dev/jasper/app/bootstrap/Instance.java`:

```java
package dev.jasper.app.bootstrap;

import dev.jasper.app.commandline.CommandDispatcher;
import dev.jasper.app.commandline.CommandListener;
import dev.jasper.app.commandline.Invocation;
import dev.jasper.app.platform.AppDirs;
import dev.jasper.app.residency.HandoffSocket;
import dev.jasper.app.residency.InstanceLock;
import dev.jasper.app.residency.LaunchRequest;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;

/**
 * The home's owner (workspace-tabs spec 5.1 and 5.3): its instance lock, and the handoff endpoint and the command socket
 * it binds right after taking it, before AWT and whatever its residency. Requests wait in {@link EarlyRequests} until
 * {@link #serve} names the application. A handoff that arrives before then is answered on receipt and held; once the
 * application is ready, it is answered only when the application's event thread has taken it, within {@link #TAKE}, and
 * an application that is quitting takes none, so the launch tries again and takes the lock once this process has gone.
 * A {@code jasper} call is answered once it has run. Production never closes the lock: the operating system releases it
 * when the process ends.
 */
final class Instance implements AutoCloseable {
    /** How long a ready owner's event thread may take to accept a handoff before the launch hears PROTOCOL (spec 5.3). */
    static final Duration TAKE = Duration.ofSeconds(1);

    private final InstanceLock lock;
    private final EarlyRequests early = new EarlyRequests();
    private final HandoffSocket handoff;
    private final CommandListener commands;
    private volatile BiConsumer<LaunchRequest, Boolean> handoffs;
    private volatile BooleanSupplier accepting;
    private volatile Executor edt;
    private volatile CommandDispatcher dispatcher;

    private Instance(InstanceLock lock, AppDirs dirs, Path source, long modified) {
        this.lock = Objects.requireNonNull(lock, "lock");
        // Either may be null (logged by its bind): an owner whose endpoint cannot bind runs unreachable (plan Ruling 15).
        this.handoff = HandoffSocket.bind(dirs.daemonSocket(), dirs.daemonToken(), dirs.daemonLock(),
            HandoffSocket.answering(this::take, source, modified));
        this.commands = CommandListener.bind(dirs.commandSocket(), this::dispatch);
    }

    /**
     * Binds the owner's endpoints.
     *
     * @param lock the home's instance lock, which this owner now holds
     * @param dirs the home
     * @param source this build's code location, or null when unknown
     * @param modified its modification time
     * @return the owner
     */
    static Instance open(InstanceLock lock, AppDirs dirs, Path source, long modified) {
        return new Instance(lock, dirs, source, modified);
    }

    /** Whether this owner took a handoff. On the endpoint's accept thread, which waits here at most {@link #TAKE}. */
    private boolean take(LaunchRequest request, boolean updated) {
        var taken = new CompletableFuture<Boolean>();
        return switch (early.submit(() -> edt.execute(() -> handoffs.accept(request, updated)),
                () -> handOver(request, updated, taken))) {
            case HELD -> true;
            case DECLINED -> false;
            case RAN -> awaitTaken(taken);
        };
    }

    /** Offers a request to the ready application's event thread, which takes it unless the application is quitting. */
    private void handOver(LaunchRequest request, boolean updated, CompletableFuture<Boolean> taken) {
        edt.execute(() -> {
            if (!accepting.getAsBoolean()) taken.complete(false);
            else if (taken.complete(true)) handoffs.accept(request, updated);
        });
    }

    /** True once the event thread took the request; false when it declined it or did not get to it in time. */
    private static boolean awaitTaken(CompletableFuture<Boolean> taken) {
        try {
            return taken.get(TAKE.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException late) {
            return withdraw(taken);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return withdraw(taken);
        } catch (ExecutionException impossible) {
            return false;
        }
    }

    /** Withdraws an offer: false, unless the event thread took it at this very moment. */
    private static boolean withdraw(CompletableFuture<Boolean> taken) {
        return !taken.complete(false) && taken.getNow(false);
    }

    private CompletionStage<Integer> dispatch(Invocation invocation) {
        var result = new CompletableFuture<Integer>();
        EarlyRequests.Outcome outcome = early.submit(() -> {
            CompletionStage<Integer> stage;
            try {
                stage = dispatcher.dispatch(invocation);
            } catch (RuntimeException failure) {
                result.completeExceptionally(failure);
                return;
            }
            stage.whenComplete((code, failure) -> {
                if (failure != null) result.completeExceptionally(failure);
                else result.complete(code);
            });
        });
        if (outcome == EarlyRequests.Outcome.DECLINED)
            result.completeExceptionally(new IllegalStateException("Jasper is quitting; run the command again"));
        return result;
    }

    /**
     * The application can take requests: those held since the lock was taken run in arrival order, later ones as they
     * come. Once.
     *
     * @param handoffs carries out a handoff, given whether it came from another build; called on {@code edt}
     * @param accepting whether the application still takes handoffs, false once it quits; called on {@code edt}
     * @param edt the application's event thread
     * @param commands runs the {@code jasper} command's calls
     */
    void serve(BiConsumer<LaunchRequest, Boolean> handoffs, BooleanSupplier accepting, Executor edt, CommandDispatcher commands) {
        this.handoffs = Objects.requireNonNull(handoffs, "handoffs");
        this.accepting = Objects.requireNonNull(accepting, "accepting");
        this.edt = Objects.requireNonNull(edt, "edt");
        this.dispatcher = Objects.requireNonNull(commands, "commands");
        early.open();
    }

    /** Whether requests are still held: before {@link #serve}, and while the held ones are passed on. */
    boolean holding() { return early.holding(); }

    /**
     * Turns every later request away when none is held and {@link #serve} was never called: a {@code --background}
     * start that will not stay can then exit without dropping a request it already answered (plan Ruling 20).
     *
     * @return whether it did; false when a request is held or the application is served
     */
    boolean declineIfIdle() { return early.declineIfIdle(); }

    /** The command socket this owner serves, which its shells name; null when it could not bind. */
    Path commandSocket() { return commands == null ? null : commands.socket(); }

    /** The command listener, for a windowless launch that quits once its calls are served; null when unbound. */
    CommandListener commands() { return commands; }

    /** Closes both endpoints; the lock stays until the process ends. Any thread; idempotent. */
    void closeEndpoints() {
        if (handoff != null) handoff.close();
        if (commands != null) commands.close();
    }

    /**
     * Closes the endpoints and releases the lock: tests, and {@code start}'s rollback in a process that goes on. A start
     * that fails in this process closes only the endpoints and exits (plan Ruling 24). Idempotent.
     */
    @Override public void close() {
        try { closeEndpoints(); }
        finally { lock.close(); }
    }
}
```

Create `jasper-app/src/main/java/dev/jasper/app/bootstrap/SingleInstance.java`:

```java
package dev.jasper.app.bootstrap;

import dev.jasper.app.platform.AppDirs;
import dev.jasper.app.platform.WindowsForeground;
import dev.jasper.app.residency.HandoffSocket;
import dev.jasper.app.residency.InstanceLock;
import dev.jasper.app.residency.LaunchRequest;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Workspace-tabs spec 5.1 to 5.3, before AWT: a launch takes its home's instance lock and becomes the owner, hands its
 * request to the owner, exits, or waits for the lock as restart's replacement ({@link LaunchAction}). It never becomes a
 * second instance: an owner that does not answer, or a lock that cannot be taken, ends the launch with a message.
 */
final class SingleInstance {
    private static final System.Logger LOG = System.getLogger(SingleInstance.class.getName());

    private SingleInstance() { }

    /**
     * How long a launch keeps trying an owner that does not answer, how long a replacement waits, and how often both
     * try.
     *
     * @param handoff spec 5.3's 10 s
     * @param replacing spec 5.2's 30 s
     * @param poll the pause between tries
     */
    record Timing(Duration handoff, Duration replacing, Duration poll) {
        static final Timing STANDARD = new Timing(Duration.ofSeconds(10), Duration.ofSeconds(30), Duration.ofMillis(100));
    }

    /** What became of the launch. */
    sealed interface Claim {
        /** This process owns the home; its endpoints are bound. */
        record Owner(Instance instance) implements Claim {
            public Owner { Objects.requireNonNull(instance, "instance"); }
        }

        /** Nothing more to do: exit with {@code code}. */
        record Done(int code) implements Claim { }
    }

    /**
     * Claims the home for this launch.
     *
     * @param options its arguments
     * @param dirs its home
     * @param timing its patience
     * @param failure shows the message a failed launch ends with
     * @return the claim
     */
    static Claim claim(AppArguments options, AppDirs dirs, Timing timing, Consumer<String> failure) {
        Path source = HandoffSocket.codeLocation();
        long modified = HandoffSocket.lastModified(source);
        try {
            Optional<InstanceLock> lock = InstanceLock.tryAcquire(dirs.instanceLock());
            return switch (LaunchAction.of(lock.isPresent(), options)) {
                case OWN -> new Claim.Owner(Instance.open(lock.orElseThrow(), dirs, source, modified));
                case EXIT -> {
                    LOG.log(System.Logger.Level.INFO, "Started with " + (options.background() ? "--background" : "--no-window")
                        + " while a Jasper owns this home; exiting");
                    yield new Claim.Done(0);
                }
                case AWAIT_LOCK -> awaitLock(options, dirs, timing, failure, source, modified);
                case HAND_OFF -> handOff(options, dirs, timing, failure, source, modified);
            };
        } catch (IOException unavailable) {
            failure.accept("Jasper could not lock its home folder (" + dirs.instanceLock() + "): " + unavailable.getMessage());
            return new Claim.Done(1);
        }
    }

    /**
     * Restart's replacement (spec 5.2) waits for the lock while the process it replaces lives. When that process has gone
     * and the lock is still held, another launch took the home in between, or the operating system has not yet released
     * the lock, and the replacement hands its request on as any launch would (plan Ruling 27).
     */
    private static Claim awaitLock(AppArguments options, AppDirs dirs, Timing timing, Consumer<String> failure, Path source,
                                   long modified) throws IOException {
        long replaced = options.replacing().getAsLong();
        long deadline = System.nanoTime() + timing.replacing().toNanos();
        while (true) {
            Optional<InstanceLock> lock = InstanceLock.tryAcquire(dirs.instanceLock());
            if (lock.isPresent()) return new Claim.Owner(Instance.open(lock.get(), dirs, source, modified));
            if (!ProcessHandle.of(replaced).map(ProcessHandle::isAlive).orElse(false))
                return handOff(options, dirs, timing, failure, source, modified);
            if (System.nanoTime() >= deadline) {
                failure.accept(unresponsive(replaced));
                return new Claim.Done(1);
            }
            if (!pause(timing)) return new Claim.Done(1);
        }
    }

    private static Claim handOff(AppArguments options, AppDirs dirs, Timing timing, Consumer<String> failure, Path source,
                                 long modified) throws IOException {
        if (!HandoffSocket.pathFits(dirs.daemonSocket())) {
            // The owner could not bind either and runs unreachable (plan Ruling 15): say why at once.
            failure.accept(HandoffSocket.tooLong(dirs.daemonSocket()));
            return new Claim.Done(1);
        }
        LaunchRequest request = LaunchRequest.of(source, modified, options.workingDirectory(), options.paths(), options.startupFlags());
        if (!request.fits()) {
            failure.accept("Jasper cannot open that many paths at once.");
            return new Claim.Done(2);
        }
        long deadline = System.nanoTime() + timing.handoff().toNanos();
        while (true) {
            // Windows lets only this freshly launched process bring a window forward; the owner needs that right first.
            WindowsForeground.allow(System.getProperty("os.name"), InstanceLock.ownerPid(dirs.instanceLock()));
            LaunchRequest.Response answer = HandoffSocket.handOff(dirs.daemonSocket(), dirs.daemonToken(), request);
            if (answer == LaunchRequest.Response.OK || answer == LaunchRequest.Response.UPDATED) return new Claim.Done(0);
            // The owner may be between taking the lock and binding, quitting, or gone (plan Rulings 3 and 23).
            Optional<InstanceLock> lock = InstanceLock.tryAcquire(dirs.instanceLock());
            if (lock.isPresent()) return new Claim.Owner(Instance.open(lock.get(), dirs, source, modified));
            if (System.nanoTime() >= deadline) {
                failure.accept(unresponsive(InstanceLock.ownerPid(dirs.instanceLock())));
                return new Claim.Done(1);
            }
            if (!pause(timing)) return new Claim.Done(1);
        }
    }

    /** Waits one poll: false when interrupted. */
    private static boolean pause(Timing timing) {
        try {
            Thread.sleep(Math.max(1, timing.poll().toMillis()));
            return true;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /**
     * Spec 5.3's message for an owner that does not answer.
     *
     * @param pid its process id, or -1 when none is known
     * @return the message
     */
    static String unresponsive(long pid) {
        return "Jasper is already running but not responding. Quit it" + (pid > 0 ? " (process " + pid + ")" : "") + " and try again.";
    }
}
```

Create `jasper-app/src/main/java/dev/jasper/app/bootstrap/LaunchFailure.java`:

```java
package dev.jasper.app.bootstrap;

import java.awt.GraphicsEnvironment;
import java.io.PrintStream;
import java.util.Objects;
import java.util.function.Consumer;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;

/**
 * How a launch that ends without starting says why (workspace-tabs spec 5.3): an owner that never answered, a
 * replacement whose predecessor never quit, a home that cannot be locked. On standard error and in the log and, with a
 * display, in a dialog in the system look: no Jasper theme exists yet. Only a launch that will not own its home calls
 * it, so the toolkit it starts serves this dialog alone.
 */
final class LaunchFailure {
    private static final System.Logger LOG = System.getLogger(LaunchFailure.class.getName());

    private LaunchFailure() { }

    /**
     * A reporter for {@link SingleInstance#claim}.
     *
     * @param error where the message is printed
     * @return reports a message to {@code error}, the log and, unless the JVM is headless, a dialog
     */
    static Consumer<String> reporter(PrintStream error) {
        Objects.requireNonNull(error, "error");
        return message -> {
            LOG.log(System.Logger.Level.ERROR, message);
            error.println(message);
            if (!GraphicsEnvironment.isHeadless()) show(message);
        };
    }

    private static void show(String message) {
        try {
            SwingUtilities.invokeAndWait(() -> {
                try {
                    UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
                } catch (ReflectiveOperationException | UnsupportedLookAndFeelException unavailable) {
                    // The toolkit's own look shows the message just as well.
                }
                JOptionPane.showMessageDialog(null, message, "Jasper", JOptionPane.ERROR_MESSAGE);
            });
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        } catch (java.lang.reflect.InvocationTargetException | RuntimeException unavailable) {
            LOG.log(System.Logger.Level.WARNING, "Could not show why the launch ended", unavailable);
        }
    }
}
```

Append this line to `docs/design/theme-engine/migration.csv` (the file ends with a newline; keep one after the new line):

```text
jasper-app/src/main/java/dev/jasper/app/bootstrap/LaunchFailure.java,app.bootstrap,LaunchFailure,native-platform,none,none,jasper-app/src/test/java/dev/jasper/app/bootstrap/SingleInstanceTest.java,New in workspace-tabs plan 1 Task 6; the system look's error dialog of a launch that ends before any Jasper theme exists
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.bootstrap.EarlyRequestsTest' --tests 'dev.jasper.app.bootstrap.SingleInstanceTest' --tests 'dev.jasper.app.bootstrap.SingleInstanceProcessTest' --tests 'dev.jasper.app.appearance.ThemeMigrationCoverageTest' --continue`
Expected: PASS (`EarlyRequestsTest` 4, `SingleInstanceTest` 15, `SingleInstanceProcessTest` 4, the ledger with its new row).
If a child prints `null` for its first line, read its inherited standard error in the test report: a
`NoClassDefFoundError` means a class it loads reaches outside the two code sources (Global Constraints), which the
fix removes rather than widening the class path. Then `./gradlew :jasper-app:check`: PASS.

- [ ] **Step 5: Commit**

```bash
git add jasper-app/src docs/design/theme-engine/migration.csv
git commit -F - <<'EOF'
Single instance: claiming the home

SingleInstance takes the instance lock before AWT and follows spec
5.2's table: an owner binds its handoff endpoint and command socket at
once and holds early requests until its application is ready; once
ready it answers a handoff only after its event thread took it, and a
quitting owner takes none. Any other launch hands off (retrying for
10 s, taking the lock if the owner has gone), exits, or waits 30 s as
restart's replacement, handing off once the process it replaces has
gone. An owner that never answers ends the launch with spec 5.3's
message (LaunchFailure), never a second instance. Child-JVM tests cover
racing starts, a killed owner, an unresponsive owner and the
replacement's wait.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

---

### Task 7: Every launch claims its home before AWT

The switch-over: `start` claims the home with `SingleInstance` (Task 6) instead of today's handoff, resident and serving
probes; the owner's endpoints exist before the configuration is read; `compose` names the application to the owner
where it used to bind the endpoint, with its event thread and whether it still takes requests; residency follows the
setting and `residencyAllowed()`, not a bind; a cold start's first window takes its first directory; a start that fails
after the claim closes its endpoints and exits, keeping the lock to the end. `--standalone`, `standaloneLaunch()`,
`privateCommands()` and the legacy `AppArguments` forms go. Rulings 4, 13, 19, 20, 22, 23, 24.

**Files:**
- Modify: `jasper-app/src/main/java/dev/jasper/app/bootstrap/ApplicationBootstrap.java` (`main`, `Startup`,
  `prepareDesktop`, `startDesktop`, `createApplication`, `compose`, `start`, the old probes)
- Modify: `jasper-app/src/main/java/dev/jasper/app/bootstrap/AppArguments.java` (whole file),
  `bootstrap/package-info.java:2`, `commandline/package-info.java:4-5`
- Modify: `jasper-app/src/main/java/dev/jasper/app/application/JasperApplication.java` (`acceptsRequests` before `handedOff`)
- Modify (test): `bootstrap/ApplicationBootstrapTest.java`, `bootstrap/ApplicationBootstrapRollbackTest.java:28,44-45,87,113`,
  `bootstrap/AppArgumentsTest.java` (Task 2's text), `application/JasperApplicationBackgroundTest.java:471`,
  `application/JasperApplicationResidencyTest.java` (one test)

**Interfaces:**
- Consumes: `SingleInstance.claim`, `Timing.STANDARD`, `Claim.Owner`/`Done`, `Instance.serve`, `declineIfIdle()`,
  `commandSocket()`, `commands()`, `closeEndpoints()`, `close()`, `LaunchFailure.reporter` (Task 6);
  `handoffHandler(JasperApplication)` (Task 5); `JasperApplication.openFirstWindow` (Task 4);
  `AppArguments.residencyAllowed()`, `paths()` (Task 2).
- Produces: `record Startup(ConfigService service, AppArguments options, AppDirs dirs, AppLog log, PluginSelection
  selection, Instance instance)`; `static AppDirs ApplicationBootstrap.environmentDirs()`; `start(String[], PrintStream,
  PrintStream, AppDirs, Preparation, Consumer<Startup>)` (main), with test forms `start(…, AppDirs, BiConsumer<ConfigService,
  AppArguments>)` (releases the owner after the callback) and `start(…, AppDirs, Consumer<Startup>)`;
  `compose(ConfigService, Supplier<CommandHistory>, Function<CommandHistory, JasperApplication>, Consumer<JasperApplication>
  ready, Consumer<JasperApplication> present)`; `residentRole(options, enabled) = enabled && options.residencyAllowed()`;
  `AppArguments(Path configOverride, boolean help, boolean background, boolean safeMode, Path pluginDir, boolean
  homeOverride, boolean noWindow, OptionalLong replacing, Path workingDirectory, List<Path> paths, List<String> given)`;
  `public boolean JasperApplication.acceptsRequests()` (EDT). Plan 6 replaces `startDesktop`'s `openFirstWindow` with
  the restored layout.

- [ ] **Step 1: Write the failing tests**

Run from the worktree root:

```bash
python3 -I - <<'PY'
"""Workspace tabs 1, Task 7 step 1: the bootstrap's tests of a launch that claims its home."""
import pathlib
def edit(name, edits):
    path = pathlib.Path(name); text = path.read_text(encoding="utf-8")
    for old, new, count in edits:
        if text.count(old) != count: raise SystemExit(f"{name}: expected {count} of {old!r}, found {text.count(old)}")
        text = text.replace(old, new)
    path.write_text(text, encoding="utf-8")
def cut(name, start, stop, replacement=""):
    path = pathlib.Path(name); text = path.read_text(encoding="utf-8")
    if text.count(start) != 1 or text.count(stop) != 1: raise SystemExit(f"{name}: anchors {start!r} / {stop!r} not unique")
    a = text.index(start); b = text.index(stop)
    if b < a: raise SystemExit(f"{name}: {stop!r} comes before {start!r}")
    path.write_text(text[:a] + replacement + text[b:], encoding="utf-8")
T = "jasper-app/src/test/java/dev/jasper/app/"
B = T + "bootstrap/ApplicationBootstrapTest.java"
cut(B, '''    @Test void helpAndErrorsExitBeforeConfigurationOrDesktopStartup() {''',
    '''    @Test void explicitConfigurationIsReadBeforeDispatchToSwing() throws Exception {''',
    '''    /** A home of this test's own: no test may take the user's instance lock (plan Ruling 19). */
    private AppDirs dirs() { return new AppDirs(directory, directory.resolve("config.toml"), directory.resolve("logs")); }

    @Test void helpAndErrorsExitBeforeConfigurationOrDesktopStartup() {
        var out = new ByteArrayOutputStream(); var error = new ByteArrayOutputStream();
        var stdout = new PrintStream(out); var stderr = new PrintStream(error);
        assertThat(ApplicationBootstrap.start(new String[]{"--help"}, stdout, stderr, dirs(),
            (service, options) -> { throw new AssertionError("Unexpected startup"); })).isZero();
        assertThat(out.toString()).contains("--config", "--help");
        assertThat(ApplicationBootstrap.start(new String[]{"--config"}, stdout, stderr, dirs(),
            (service, options) -> { throw new AssertionError("Unexpected startup"); })).isEqualTo(2);
        assertThat(error.toString()).contains("requires a file path");
        assertThat(dirs().instanceLock()).as("both end before the lock").doesNotExist();
    }

    /** Spec 5.2: a second launch in an owned home hands its request to the owner before reading anything. */
    @Test void aSecondLaunchInAnOwnedHomeHandsOffAndALoginLaunchExits() throws Exception {
        Files.writeString(directory.resolve("config.toml"), "");
        var owner = new AtomicReference<ApplicationBootstrap.Startup>();
        assertThat(ApplicationBootstrap.start(new String[0], System.out, System.err, dirs(), owner::set)).isZero();
        try {
            var seen = new java.util.concurrent.ConcurrentLinkedQueue<LaunchRequest>();
            owner.get().instance().serve((request, updated) -> seen.add(request), () -> true, Runnable::run,
                invocation -> CompletableFuture.completedFuture(0));
            Path notes = Files.writeString(directory.resolve("notes.txt"), "");
            assertThat(ApplicationBootstrap.start(new String[]{"--safe-mode", notes.toString()}, System.out, System.err, dirs(),
                startup -> { throw new AssertionError("a second launch never starts"); })).isZero();
            DesktopTestSupport.until(() -> seen.size() == 1);
            assertThat(seen.peek().paths()).containsExactly(notes);
            assertThat(seen.peek().ignoredFlags()).containsExactly("--safe-mode");
            assertThat(ApplicationBootstrap.start(new String[]{"--background"}, System.out, System.err, dirs(),
                startup -> { throw new AssertionError("a login launch never starts while an owner runs"); })).isZero();
            assertThat(seen).as("the login launch handed nothing off").hasSize(1);
        } finally {
            owner.get().service().close();
            owner.get().instance().close();
        }
    }

''')
edit(B, [
    ('''            int result = ApplicationBootstrap.start(new String[]{"--config", config.toString()}, System.out, System.err, (service, options) -> {''',
     '''            int result = ApplicationBootstrap.start(new String[]{"--config", config.toString()}, System.out, System.err, dirs(), (service, options) -> {''', 1),
    ('''            assertThat(ApplicationBootstrap.start(new String[]{"--config", config.toString()}, System.out, System.err,
                (service, options) -> received.set(service))).isZero();''',
     '''            assertThat(ApplicationBootstrap.start(new String[]{"--config", config.toString()}, System.out, System.err, dirs(),
                (service, options) -> received.set(service))).isZero();''', 1),
    ('''        var plain = new AppArguments(null, false, false, false, null, false, false, false);
        var background = new AppArguments(null, false, true, false, null, false, false, false);
        var noWindow = new AppArguments(null, false, false, false, null, false, false, true);''',
     '''        var plain = AppArguments.parse(new String[0], directory);
        var background = AppArguments.parse(new String[]{"--background"}, directory);
        var noWindow = AppArguments.parse(new String[]{"--no-window"}, directory);''', 1),
    ('''dirs, null, branded);''', '''dirs, null, branded, null);''', 3),
    ('''dirs, null,
                dev.jasper.app.plugins.PluginSelection.none());''', '''dirs, null,
                dev.jasper.app.plugins.PluginSelection.none(), null);''', 1)])
cut(B, '''    @Test void neitherAnotherConfigurationNorAResidentProcessHandsOff(@TempDir Path dir) throws Exception {''',
    '''    @Test void loginItemReconcilerDedupesAndRunsOffTheCallingThread() throws Exception {''',
    '''    @Test void residencyIsForTheDefaultHomeLaunchedWithoutStartUpFlags() {
        assertThat(ApplicationBootstrap.residentRole(AppArguments.parse(new String[0], directory), true)).isTrue();
        assertThat(ApplicationBootstrap.residentRole(AppArguments.parse(new String[0], directory), false)).isFalse();
        assertThat(ApplicationBootstrap.residentRole(AppArguments.parse(new String[]{"notes.txt"}, directory), true))
            .as("paths are no start-up flag").isTrue();
        for (String[] flags : new String[][]{{"--config", "other.toml"}, {"--safe-mode"}, {"--plugin-dir", "p"}})
            assertThat(ApplicationBootstrap.residentRole(AppArguments.parse(flags, directory), true)).as(flags[0]).isFalse();
        assertThat(ApplicationBootstrap.residentRole(AppArguments.parse(new String[0], directory).withHomeOverride(true), true))
            .as("another home").isFalse();
    }

''')
cut(B, '''    @Test void failedDesktopDispatchClosesConfigurationAndPreservesError() {''',
    '''    @Test void onlyAPackagedMacLoginLaunchStartsHiddenFromTheDock() throws Exception {''',
    '''    @Test void failedDesktopDispatchClosesConfigurationAndPreservesError() throws Exception {
        var received = new AtomicReference<ConfigService>();
        var original = new AssertionError("desktop dispatch");
        try {
            assertThatThrownBy(() -> ApplicationBootstrap.start(new String[]{"--config", directory.resolve("config.toml").toString()},
                System.out, System.err, dirs(), (service, options) -> { received.set(service); throw original; })).isSameAs(original);
            assertThat(received.get().reload()).isCompletedExceptionally();
            try (var again = dev.jasper.app.residency.InstanceLock.tryAcquire(dirs().instanceLock()).orElseThrow()) {
                assertThat(again.file()).as("the rollback released the home").isEqualTo(dirs().instanceLock());
            }
        } finally { if (received.get() != null) received.get().close(); }
    }

''')
cut(B, '''    @Test void aNoWindowLaunchNeverHandsOffAndStepsAsideWhileAJasperServes(@TempDir Path dir) throws Exception {''',
    '''    private static JasperApplication application(Path dir, Runnable terminated) throws Exception {''')
cut(B, '''    @Test void aLaunchThatFindsAnotherJasperServingTheSharedSocketServesItsOwnShellsPrivately(@TempDir Path dir) throws Exception {''',
    '''    @Test void aNoWindowJasperThatIsNotResidentQuitsOnceItsCallIsServed(@TempDir Path dir) throws Exception {''')
cut(B, '''    /** Spec 4.8: the pre-pass runs before the configuration is read, and its selection reaches the launch. */''',
    '''    /** Spec 4.6: pending only on the paths about to show a window or rehearse. */''',
    '''    /** Spec 4.8: the pre-pass runs before the configuration is read, and its selection reaches the launch. */
    @Test void thePrePassFeedsTheConfigurationsDefaultsAndTheLaunch() throws Exception {
        Path config = directory.resolve("config.toml");
        Files.writeString(config, "");
        var selection = dev.jasper.app.plugins.PluginSelection.of(new AppDirs(directory, config, directory.resolve("logs")), null, "",
            dev.jasper.app.testsupport.LookFixtures.plugin(directory.resolve("dev"), "dev.example.look",
                dev.jasper.app.testsupport.LookFixtures.descriptor("dev.example.look", "Example")
                    + dev.jasper.app.testsupport.LookFixtures.provider("look.kit.toml"),
                java.util.Map.of("look.kit.toml", "kit = 1\\nbase = \\"default\\"\\n[defaults]\\n\\"palette.visible_rows\\" = 7\\n")), false);
        var received = new AtomicReference<ApplicationBootstrap.Startup>();
        try {
            assertThat(ApplicationBootstrap.start(new String[]{"--config", config.toString()}, System.out, System.err, dirs(),
                (dirs, options) -> new ApplicationBootstrap.Prepared(null, selection), received::set)).isZero();
            assertThat(received.get().selection()).isSameAs(selection);
            assertThat(received.get().service().initialState().snapshot().visibleRows()).isEqualTo(7);
            assertThat(received.get().service().defaults()).isSameAs(selection.presentation().defaults());
        } finally {
            if (received.get() != null) { received.get().service().close(); received.get().instance().close(); }
        }
        var plain = new AtomicReference<ApplicationBootstrap.Startup>();
        try {
            assertThat(ApplicationBootstrap.start(new String[]{"--config", config.toString()}, System.out, System.err, dirs(), plain::set)).isZero();
            assertThat(plain.get().selection().isNone()).as("the test entry scans no home").isTrue();
            assertThat(plain.get().log()).isNull();
        } finally {
            if (plain.get() != null) { plain.get().service().close(); plain.get().instance().close(); }
        }
    }

''')
edit(T + "application/JasperApplicationResidencyTest.java", [
    ('''    @Test void anOpenDoesNotThrowOnceTheApplicationHasQuit() throws Exception {''',
     '''    @Test void aQuittingApplicationTakesNoMoreRequests() throws Exception {
        JasperApplication application = application(() -> { });
        edt(() -> assertThat(application.acceptsRequests()).isTrue());
        edt(application::quit);
        edt(() -> assertThat(application.acceptsRequests()).as("plan Ruling 23").isFalse());
    }

    @Test void anOpenDoesNotThrowOnceTheApplicationHasQuit() throws Exception {''', 1)])
R = T + "bootstrap/ApplicationBootstrapRollbackTest.java"
edit(R, [
    ('''            app -> endpoint[0] = HandoffSocket.bind(dir.resolve("socket"), dir.resolve("token"), dir.resolve("lock"),
                request -> LaunchRequest.Response.OK),''',
     '''            app -> {
                endpoint[0] = HandoffSocket.bind(dir.resolve("socket"), dir.resolve("token"), dir.resolve("lock"),
                    request -> LaunchRequest.Response.OK);
                app.onShutdown(endpoint[0]::close);
            },''', 1),
    ('''                        app -> endpoint, app -> { if (presentationFails) throw original; });''',
     '''                        app -> app.onShutdown(endpoint::close), app -> { if (presentationFails) throw original; });''', 1),
    ('''            app -> null, app -> { }).quit());''', '''            app -> { }, app -> { }).quit());''', 1)])
A = T + "bootstrap/AppArgumentsTest.java"
edit(A, [
    ('''        assertThat(parse("--standalone").standalone()).isTrue();\n''', '', 1),
    ('''new String[]{"--standalone", "--standalone"}, ''', '', 1)])
edit(T + "application/JasperApplicationBackgroundTest.java", [
    ('''        // --standalone, --safe-mode, run_in_background = false or a failed bind: a presence is installed, nothing prepares it.''',
     '''        // --safe-mode, --config, another home or run_in_background = false: a presence is installed, nothing prepares it.''', 1)])
PY
```

The `java.util.Map.of(...)` line in the replacement keeps the test's original escapes (`\n`, `\"`) in the Java source.

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :jasper-app:compileTestJava`
Expected: FAIL to compile: "no suitable method found for start(String[],PrintStream,PrintStream,AppDirs,…)", "constructor
Startup … cannot be applied", "incompatible types … HandoffSocket" (the rollback test's `compose` calls), "cannot find
symbol … acceptsRequests()".

- [ ] **Step 3: The arguments lose `--standalone`**

Replace `jasper-app/src/main/java/dev/jasper/app/bootstrap/AppArguments.java` with:

```java
package dev.jasper.app.bootstrap;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.OptionalLong;
import java.util.stream.Stream;

/**
 * Startup options parsed before any desktop initialization. An argument that does not start with {@code --} is a path
 * to open (workspace-tabs spec 5.4), resolved against the working directory.
 *
 * @param configOverride {@code --config}, or null
 * @param help {@code --help}
 * @param background {@code --background}: the login item's windowless start
 * @param safeMode {@code --safe-mode}
 * @param pluginDir {@code --plugin-dir}, or null
 * @param homeOverride whether the home came from {@code jasper.home} or {@code JASPER_HOME}
 * @param noWindow {@code --no-window}: the {@code jasper} command's windowless start
 * @param replacing {@code --replacing}: the process a restart's replacement waits for; not in the usage
 * @param workingDirectory the directory the launch ran in
 * @param paths the paths to open, absolute and normalized, in order
 * @param given the arguments as {@code main} received them, which a restart replays
 */
record AppArguments(Path configOverride, boolean help, boolean background, boolean safeMode, Path pluginDir,
                    boolean homeOverride, boolean noWindow, OptionalLong replacing, Path workingDirectory,
                    List<Path> paths, List<String> given) {
    static final String USAGE = "Usage: jasper [--config <path>] [--background] [--no-window] [--safe-mode] "
        + "[--plugin-dir <path>] [--help] [<path>...]";

    AppArguments {
        Objects.requireNonNull(replacing, "replacing");
        Objects.requireNonNull(workingDirectory, "workingDirectory");
        paths = List.copyOf(paths);
        given = List.copyOf(given);
    }

    /** Records whether the application home was overridden ({@code jasper.home} or {@code JASPER_HOME}). */
    AppArguments withHomeOverride(boolean value) {
        return new AppArguments(configOverride, help, background, safeMode, pluginDir, value, noWindow, replacing,
            workingDirectory, paths, given);
    }

    /**
     * The start-up flags this launch carries: what a second launch reports to the owner as ignored (spec 5.2).
     *
     * @return {@code --safe-mode}, {@code --config} and {@code --plugin-dir}, those present, in that order
     */
    List<String> startupFlags() {
        return Stream.of(safeMode ? "--safe-mode" : null, configOverride != null ? "--config" : null,
            pluginDir != null ? "--plugin-dir" : null).filter(Objects::nonNull).toList();
    }

    /** Residency applies only to the default home launched without start-up flags (spec 5.2). */
    boolean residencyAllowed() {
        return !homeOverride && startupFlags().isEmpty();
    }

    static AppArguments parse(String[] args, Path workingDirectory) {
        Path override = null;
        Path pluginDir = null;
        boolean help = false, background = false, safeMode = false, noWindow = false;
        OptionalLong replacing = OptionalLong.empty();
        Stream.Builder<Path> paths = Stream.builder();
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--help" -> {
                    if (help) throw invalid("Duplicate --help option.");
                    help = true;
                }
                case "--background" -> {
                    if (background) throw invalid("Duplicate --background option.");
                    background = true;
                }
                case "--no-window" -> {
                    if (noWindow) throw invalid("Duplicate --no-window option.");
                    noWindow = true;
                }
                case "--safe-mode" -> {
                    if (safeMode) throw invalid("Duplicate --safe-mode option.");
                    safeMode = true;
                }
                case "--config" -> {
                    if (override != null) throw invalid("Duplicate --config option.");
                    override = path(args, ++i, "--config", "a file path", workingDirectory);
                }
                case "--plugin-dir" -> {
                    if (pluginDir != null) throw invalid("Duplicate --plugin-dir option.");
                    pluginDir = path(args, ++i, "--plugin-dir", "a directory path", workingDirectory);
                }
                case "--replacing" -> {
                    if (replacing.isPresent()) throw invalid("Duplicate --replacing option.");
                    replacing = OptionalLong.of(processId(args, ++i));
                }
                default -> {
                    if (args[i].startsWith("--")) throw invalid("Unknown argument.");
                    paths.add(positional(args[i], workingDirectory));
                }
            }
        }
        if (noWindow && background) throw invalid("--no-window and --background cannot be combined.");
        return new AppArguments(override, help, background, safeMode, pluginDir, false, noWindow, replacing,
            workingDirectory, paths.build().toList(), List.of(args));
    }

    private static Path path(String[] args, int index, String option, String what, Path workingDirectory) {
        if (index >= args.length || args[index].isBlank() || args[index].startsWith("--"))
            throw invalid(option + " requires " + what + ".");
        try { return workingDirectory.resolve(Path.of(args[index])).toAbsolutePath().normalize(); }
        catch (InvalidPathException ignored) { throw invalid(option + " requires a valid path."); }
    }

    /** A path to open, as the launch names it (spec 5.4). */
    private static Path positional(String text, Path workingDirectory) {
        if (text.isBlank()) throw invalid("A path cannot be blank.");
        try { return workingDirectory.resolve(text).toAbsolutePath().normalize(); }
        catch (InvalidPathException ignored) { throw invalid("Invalid path: " + text); }
    }

    private static long processId(String[] args, int index) {
        if (index < args.length) {
            try {
                long pid = Long.parseLong(args[index]);
                if (pid > 0) return pid;
            } catch (NumberFormatException ignored) {
                // Reported below.
            }
        }
        throw invalid("--replacing requires a process id.");
    }

    private static IllegalArgumentException invalid(String message) {
        return new IllegalArgumentException(message + "\n" + USAGE);
    }
}
```

- [ ] **Step 4: The bootstrap claims the home**

Run from the worktree root:

```bash
python3 -I - <<'PY'
"""Workspace tabs 1, Task 7 step 4: start claims the home; compose hands the owner its application."""
import pathlib
def edit(name, edits):
    path = pathlib.Path(name); text = path.read_text(encoding="utf-8")
    for old, new, count in edits:
        if text.count(old) != count: raise SystemExit(f"{name}: expected {count} of {old!r}, found {text.count(old)}")
        text = text.replace(old, new)
    path.write_text(text, encoding="utf-8")
def cut(name, start, stop, replacement=""):
    path = pathlib.Path(name); text = path.read_text(encoding="utf-8")
    if text.count(start) != 1 or text.count(stop) != 1: raise SystemExit(f"{name}: anchors {start!r} / {stop!r} not unique")
    a = text.index(start); b = text.index(stop)
    if b < a: raise SystemExit(f"{name}: {stop!r} comes before {start!r}")
    path.write_text(text[:a] + replacement + text[b:], encoding="utf-8")
M = "jasper-app/src/main/java/dev/jasper/app/"
B = M + "bootstrap/ApplicationBootstrap.java"
edit(B, [
    ('''        int result = start(args, System.out, System.err, ApplicationBootstrap::prepare, ApplicationBootstrap::prepareDesktop);''',
     '''        int result = start(args, System.out, System.err, environmentDirs(), ApplicationBootstrap::prepare, ApplicationBootstrap::prepareDesktop);''', 1),
    ('''    /** What the boundary hands the desktop: the service, the arguments, the home, the log (null without the pre-pass) and the selection. */
    record Startup(ConfigService service, AppArguments options, AppDirs dirs, AppLog log,
                   dev.jasper.app.plugins.PluginSelection selection) { }''',
     '''    /**
     * What the boundary hands the desktop: the service, the arguments, the home, the log (null without the pre-pass), the
     * selection and the home's owner, whose lock and endpoints this process holds (null only where a test builds one).
     */
    record Startup(ConfigService service, AppArguments options, AppDirs dirs, AppLog log,
                   dev.jasper.app.plugins.PluginSelection selection, Instance instance) { }''', 1),
    ('''            var endpoint = new AtomicReference<HandoffSocket>();
            var commands = new AtomicReference<CommandListener>();
            var attempt = startup.selection().attempt();''',
     '''            Instance instance = Objects.requireNonNull(startup.instance(), "the desktop needs the home's owner");
            // A failed start closes the endpoints and exits; the lock goes with the process, never before it (plan Ruling 24).
            acquired.own((AutoCloseable) instance::closeEndpoints);
            var attempt = startup.selection().attempt();''', 1),
    ('''            abandon(opened.get(), SwingUtilities::invokeLater);
            LOG.log(System.Logger.Level.ERROR, "Application startup failed", failure);
        }''',
     '''            abandon(opened.get(), SwingUtilities::invokeLater);
            LOG.log(System.Logger.Level.ERROR, "Application startup failed", failure);
            exitAfterFailedStart();
        }''', 1),
    ('''                    if (endpoint.get() != null) endpoint.get().close();
                    if (commands.get() != null) commands.get().close();''',
     '''                    instance.closeEndpoints();''', 1),
    ('''            SwingUtilities.invokeLater(() -> startDesktop(startup, dirs, log, exceptions, shutdown, endpoint, commands, steps));
            // The posted EDT composition owns service; the process hook owns logging, the endpoint and the command
            // listener.''',
     '''            SwingUtilities.invokeLater(() -> startDesktop(startup, dirs, log, exceptions, shutdown, steps));
            // The posted EDT composition owns service; the process hook owns logging and the endpoints; the instance lock
            // lasts as long as the process.''', 1),
    ('''                                                       AppArguments options, AtomicReference<CommandListener> commands,''',
     '''                                                       AppArguments options, Instance instance,''', 1),
    ('''            if (!options.privateCommands()) application.subcommandList(dirs.subcommandList());
            application.startPlugins(dirs);
            // After the plugins started, so a call that arrives first finds its handler.
            CommandListener listener = serveCommands(application, options, dirs, acquired);
            if (listener != null) commands.set(listener);''',
     '''            application.subcommandList(dirs.subcommandList());
            // New local shells name the socket this owner serves (JASPER_COMMAND_SOCKET).
            if (instance.commandSocket() != null) application.commandSocket(instance.commandSocket());
            application.startPlugins(dirs);''', 1),
    ('''    /** EDT acquisition transaction; factories keep resource creation explicit and rollback testable. */
    static JasperApplication compose(ConfigService service, Supplier<CommandHistory> openHistory,
                                     Function<CommandHistory, JasperApplication> openApplication,
                                     Function<JasperApplication, HandoffSocket> bindEndpoint,
                                     Consumer<JasperApplication> present) {''',
     '''    /**
     * EDT acquisition transaction; factories keep resource creation explicit and rollback testable. {@code ready} runs
     * once the application exists, before {@code present}: residency, endpoint cleanup and the requests the owner has
     * held since it took the lock.
     */
    static JasperApplication compose(ConfigService service, Supplier<CommandHistory> openHistory,
                                     Function<CommandHistory, JasperApplication> openApplication,
                                     Consumer<JasperApplication> ready,
                                     Consumer<JasperApplication> present) {''', 1),
    ('''            HandoffSocket endpoint = bindEndpoint.apply(application);
            if (endpoint != null) {
                // The application owns endpoint cleanup even if presentation fails below.
                // Rollback queues quit; it must never take the endpoint's process lock on EDT.
                application.onShutdown(endpoint::close);
            }
            application.residency(endpoint != null);
            present.accept(application);''',
     '''            ready.accept(application);
            present.accept(application);''', 1)])
cut(B, '''    private static void startDesktop(Startup startup, AppDirs dirs, AppLog log,''',
    '''    private static JasperApplication createApplication(''',
    '''    private static void startDesktop(Startup startup, AppDirs dirs, AppLog log, UnexpectedExceptions exceptions,
                                     Thread shutdown, StartupSteps steps) {
        ConfigService service = startup.service(); AppArguments options = startup.options(); Instance instance = startup.instance();
        try {
            StartupTrace.current().mark(StartupTrace.Milestone.TOOLKIT_READY);
            boolean resident = residentRole(options, service.initialState().snapshot().backgroundEnabled());
            Path home = Path.of(System.getProperty("user.home"));
            String appPath = System.getProperty("jpackage.app-path");
            if (options.background() && !resident) {
                if (options.residencyAllowed()) reconcileLoginItem(false, appPath, home);
                if (instance.declineIfIdle()) {
                    LOG.log(System.Logger.Level.INFO, "Started with --background, but this launch is not resident; exiting");
                    service.close(); System.exit(0); return;
                }
                // A launch already handed this process a request it answered: it starts as an ordinary launch and
                // carries the request out (plan Ruling 20). On macOS it began without a Dock icon; its first window
                // brings it back, as a resident's does.
                LOG.log(System.Logger.Level.INFO, "Started with --background, not resident, holding a request; starting as usual");
            }
            boolean warmUp = options.background() && resident;
            presenting(steps, () -> compose(service, () -> new CommandHistory(dirs.commandHistory()),
                history -> {
                    JasperApplication application = createApplication(service, history, dirs, options, instance, startup.selection(), steps);
                    exceptions.forwardTo(application::unexpected);
                    return application;
                }, application -> {
                    if (options.residencyAllowed())
                        application.loginItems(loginItemReconciler(enabled -> reconcileLoginItem(enabled, appPath, home)));
                    application.residency(resident);
                    // From here the application closes the endpoints at quit, as the process hook does at exit.
                    application.onShutdown(instance::closeEndpoints);
                    // The requests held since the lock was taken run now, in arrival order; later handoffs are answered
                    // once the event thread has taken them, and none once the application quits (spec 5.3).
                    instance.serve(handoffHandler(application), application::acceptsRequests, SwingUtilities::invokeLater,
                        application.commands());
                }, application -> {
                    if (options.noWindow()) {
                        serveWithoutWindow(application, instance.commands());
                        return;
                    }
                    // A --background start that is not resident gets here only to carry out a request: it presents as usual.
                    if (attemptBeforePresenting(options, application.resident() || !warmUp, GraphicsEnvironment.isHeadless()))
                        application.presentationAttempt().pending();
                    if (warmUp) {
                        application.warmUp(() -> StartupTrace.current().mark(StartupTrace.Milestone.WARMED_UP));
                        return;
                    }
                    steps.workspace();
                    long window = System.nanoTime();
                    // The first window takes the first directory the launch names; its other paths open in it (plan Ruling 13).
                    application.openFirstWindow(options.paths());
                    StartupTrace.current().phase("first window", window);
                }));
        } catch (RuntimeException | Error failure) {
            abandon(steps, Runnable::run);
            // Also covers failure before composition took ownership (for example initial role resolution). The lock goes
            // with the process, never before it (plan Ruling 24).
            var remaining = new StartupResources(); remaining.own(service); remaining.own((AutoCloseable) instance::closeEndpoints);
            remaining.rollback(failure);
            LOG.log(System.Logger.Level.ERROR, "Application startup failed", failure);
            closeLogAfterStartupFailure(log, () -> { exceptions.close(); removeShutdownHook(shutdown); exitAfterFailedStart(); });
        }
    }

    /**
     * Ends a process whose start failed after it claimed its home, once the event thread has run what was posted before
     * (the splash closing; at most 2 s): the instance lock is released with the process, so no later launch owns the home
     * while this one still shuts down (plan Ruling 24).
     */
    private static void exitAfterFailedStart() {
        Thread.ofPlatform().name("jasper-failed-start").start(() -> {
            var drained = new java.util.concurrent.CountDownLatch(1);
            SwingUtilities.invokeLater(drained::countDown);
            try { drained.await(2, java.util.concurrent.TimeUnit.SECONDS); }
            catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
            System.exit(1);
        });
    }

''')
cut(B, '''    /** Startup boundary: parsing, the handoff attempt and the first read finish before the desktop callback runs. */''',
    '''    /**\n     * True when a resident process accepted this launch and there is nothing left to do.''',
    '''    /** The home this process's environment names: {@code jasper.home}, else {@code JASPER_HOME}, else the OS default. */
    static AppDirs environmentDirs() {
        return AppDirs.resolve(System.getProperty("os.name"), System.getenv(), Path.of(System.getProperty("user.home")),
            System.getProperty("jasper.home"));
    }

    /** The test entry without the pre-pass: the owner's lock and endpoints are released once {@code launch} returns. */
    static int start(String[] args, java.io.PrintStream out, java.io.PrintStream error, AppDirs dirs,
                     java.util.function.BiConsumer<ConfigService, AppArguments> launch) {
        return start(args, out, error, dirs, startup -> {
            try { launch.accept(startup.service(), startup.options()); }
            finally { startup.instance().close(); }
        });
    }

    /** The test entry without the pre-pass; the caller closes the startup's instance. */
    static int start(String[] args, java.io.PrintStream out, java.io.PrintStream error, AppDirs dirs, Consumer<Startup> launch) {
        return start(args, out, error, dirs, Preparation.NONE, launch);
    }

    /**
     * Startup boundary: parsing, claiming {@code dirs} (workspace-tabs spec 5.1 to 5.3: the instance lock or a handoff,
     * before any toolkit initialization) and the first read finish before the desktop callback runs. Returns the exit code.
     */
    static int start(String[] args, java.io.PrintStream out, java.io.PrintStream error, AppDirs dirs, Preparation prepare,
                     Consumer<Startup> launch) {
        AppArguments options;
        try { options = AppArguments.parse(args, Path.of(System.getProperty("user.dir"))); }
        catch (IllegalArgumentException failure) { error.println(failure.getMessage()); return 2; }
        if (options.help()) { out.println(AppArguments.USAGE); return 0; }
        String os = System.getProperty("os.name");
        // AppKit reads this as the toolkit initialises.
        launchProperties(options.background(), os, System.getProperty("jpackage.app-path")).forEach(System::setProperty);
        options = options.withHomeOverride(dirs.overridden());
        // Before any toolkit initialization: a handed-off launch must cost almost nothing and must
        // not settle a second icon into the Dock on its way out.
        var claim = SingleInstance.claim(options, dirs, SingleInstance.Timing.STANDARD, LaunchFailure.reporter(error));
        if (claim instanceof SingleInstance.Claim.Done done) return done.code();
        Instance instance = ((SingleInstance.Claim.Owner) claim).instance();
        Path file = options.configOverride() == null ? dirs.configFile() : options.configOverride();
        var acquired = new StartupResources();
        acquired.own(instance);
        Prepared prepared;
        try { prepared = prepare.prepare(dirs, options); }
        catch (RuntimeException | Error failure) { acquired.rollback(failure); throw failure; }
        if (prepared.log() != null) acquired.own(prepared.log());
        StartupTrace.current().look(prepared.selection().presentation().look());
        long read = System.nanoTime();
        ConfigService service;
        try { service = acquired.own(new ConfigService(file, os.startsWith("Mac"), prepared.selection().presentation().defaults())); }
        catch (RuntimeException | Error failure) { acquired.rollback(failure); throw failure; }
        StartupTrace.current().phase("config", read);
        StartupTrace.current().mark(StartupTrace.Milestone.CONFIG_LOADED);
        try {
            launch.accept(new Startup(service, options, dirs, prepared.log(), prepared.selection(), instance));
            acquired.transfer();
            return 0;
        } catch (RuntimeException | Error failure) { acquired.rollback(failure); throw failure; }
    }

''')
cut(B, '''    /**\n     * True when a resident process accepted this launch and there is nothing left to do.''',
    '''    /** How long a --no-window Jasper that is not resident waits for its first call. */''',
    '''    /** Residency applies only to the default home launched without start-up flags, and only when it is on (spec 5.2). */
    static boolean residentRole(AppArguments options, boolean enabled) {
        return enabled && options.residencyAllowed();
    }

''')
cut(B, '''    /** The command socket a launch serves: its own for a private launch, otherwise its home's shared one. */''',
    '''    /**\n     * A {@code --no-window} launch, which the {@code jasper} command makes to serve a call: no window.''')
edit(M + "application/JasperApplication.java", [
    ('''    /**
     * A second launch handed its request to this process (workspace-tabs spec 5.3).''',
     '''    /**
     * Whether this process still takes a second launch's request: false once it quits, so the launch tries again and
     * takes the home once this process has gone (plan Ruling 23). EDT.
     *
     * @return true until the application quits
     */
    public boolean acceptsRequests() { return !quitting && !stopped; }

    /**
     * A second launch handed its request to this process (workspace-tabs spec 5.3).''', 1)])
edit(M + "bootstrap/package-info.java", [
    (''' * Pre-AWT argument/handoff flow and EDT composition. StartupResources owns rollback until explicit transfer.''',
     ''' * Pre-AWT arguments, the home's instance lock and the handoff, and EDT composition. StartupResources owns rollback until explicit transfer; the owner's Instance keeps its lock for the life of the process.''', 1)])
edit(M + "commandline/package-info.java", [
    (''' * the command on {@code PATH}. Bootstrap binds the listener after plugins start; the application closes it at
 * shutdown and the process hook is its backup.''',
     ''' * the command on {@code PATH}. The home's owner binds the listener before AWT and holds its calls until the
 * application is composed; the application closes it at shutdown and the process hook is its backup.''', 1)])
PY
```

`ApplicationBootstrap` keeps importing `CommandDispatcher`, `WindowsForeground` and `AtomicReference` (the last is still
used for the splash); an unused import is harmless, so the script leaves them.

- [ ] **Step 5: Run the tests to verify they pass**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.bootstrap.*' --tests 'dev.jasper.app.residency.*' --tests 'dev.jasper.app.application.JasperApplicationBackgroundTest' --tests 'dev.jasper.app.application.JasperApplicationResidencyTest' --tests 'dev.jasper.app.appearance.ThemeMigrationCoverageTest' --continue`
Expected: PASS (`ApplicationBootstrapTest` with its new handoff test, `AppArgumentsTest`, the rollback tests unchanged in
what they prove). Then `./gradlew :jasper-app:check`: PASS. Confirm no test touched the real home:
`ls ~/.config/jasper/daemon/instance.lock` prints "No such file or directory" on a machine where the installed Jasper
has not run this build.

- [ ] **Step 6: Commit**

```bash
git add jasper-app/src
git commit -F - <<'EOF'
Single instance: every launch claims its home before AWT

start claims the home (lock, handoff, wait or exit) before the
configuration is read and takes the home as a parameter, so tests pass
their own. The owner's endpoints exist from the claim; compose hands the
application to the owner, which runs the requests it held and answers
later handoffs once the event thread took them, none while quitting.
Residency follows the setting for the default home without start-up
flags, a cold start's first window takes its first directory, a start
that fails after the claim closes its endpoints and exits, and
--standalone with the standalone and private launch rules goes.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

---

### Task 8: The bind-time locks, the liveness probes and the private sockets go

Spec 5.3 and 16: the instance lock already excludes every other binder, so `daemon/lock`, `<socket>.lock`, the liveness
probes and `commands-<pid>` go, and so does the handoff endpoint's `token.pid`: the owner's process id is
`daemon/instance.pid` (Task 1). Rulings 5, 14, 15.

**Files:**
- Modify: `jasper-app/src/main/java/dev/jasper/app/residency/HandoffSocket.java` (whole file)
- Modify: `jasper-app/src/main/java/dev/jasper/app/commandline/CommandListener.java:10-17,82-139,360-388`
- Modify: `jasper-app/src/main/java/dev/jasper/app/platform/AppDirs.java:75-90`, `bootstrap/Instance.java` (Task 6's bind)
- Delete: `jasper-app/src/test/java/dev/jasper/app/testsupport/EndpointLockChild.java`
- Modify (test): `residency/HandoffSocketTest.java` (Task 5's text), `commandline/CommandListenerTest.java:123-148`,
  `platform/AppDirsTest.java:71,80`, `bootstrap/ApplicationBootstrapRollbackTest.java` (Task 7's text and `:68-115`)

**Interfaces:**
- Consumes: Task 5's `HandoffSocket`, Task 6's `Instance`.
- Produces: `HandoffSocket.bind(Path socketPath, Path tokenPath, Function<LaunchRequest, LaunchRequest.Response> handler)`
  (no lock path; replaces whatever is at the path; writes no `token.pid`); `CommandListener.bind(Path, CommandDispatcher)`
  likewise (its `.token.pid`, which the `jasper` client reads, stays); no `HandoffSocket.live`, `HandoffSocket.ownerPid`,
  `CommandListener.live`, `CommandListener.lockFile`, `AppDirs.daemonLock()` or `AppDirs.commandSocket(long)`.

- [ ] **Step 1: Write the failing tests**

Run from the worktree root:

```bash
python3 -I - <<'PY'
"""Workspace tabs 1, Task 8 step 1: tests of endpoints that only the owner binds."""
import pathlib
def edit(name, edits):
    path = pathlib.Path(name); text = path.read_text(encoding="utf-8")
    for old, new, count in edits:
        if text.count(old) != count: raise SystemExit(f"{name}: expected {count} of {old!r}, found {text.count(old)}")
        text = text.replace(old, new)
    path.write_text(text, encoding="utf-8")
def cut(name, start, stop, replacement=""):
    path = pathlib.Path(name); text = path.read_text(encoding="utf-8")
    if text.count(start) != 1 or text.count(stop) != 1: raise SystemExit(f"{name}: anchors {start!r} / {stop!r} not unique")
    a = text.index(start); b = text.index(stop)
    if b < a: raise SystemExit(f"{name}: {stop!r} comes before {start!r}")
    path.write_text(text[:a] + replacement + text[b:], encoding="utf-8")
T = "jasper-app/src/test/java/dev/jasper/app/"
H = T + "residency/HandoffSocketTest.java"
edit(H, [
    ('''    private Path lock() { return dir.resolve("lock"); }\n''', '', 1),
    ('''        HandoffSocket endpoint = HandoffSocket.bind(socket(), token(), lock(), handler);''',
     '''        HandoffSocket endpoint = HandoffSocket.bind(socket(), token(), handler);''', 1),
    ('''        assertThat(HandoffSocket.bind(socket(), dir.resolve("absent/directory/token"), lock(),
            request -> LaunchRequest.Response.OK)).isNull();''',
     '''        assertThat(HandoffSocket.bind(socket(), dir.resolve("absent/directory/token"), request -> LaunchRequest.Response.OK)).isNull();''', 1),
    ('''    @Test void aSocketFileLeftByACrashIsReplacedButALiveOwnerIsNot() throws Exception {
        Files.createFile(socket());
        Files.writeString(token(), "left-over\\n");
        try (HandoffSocket endpoint = bind(request -> LaunchRequest.Response.OK)) {
            assertThat(handOff(launch(1L))).as("the stale file was replaced by a working endpoint").isEqualTo(LaunchRequest.Response.OK);
            assertThat(HandoffSocket.bind(socket(), token(), lock(), request -> LaunchRequest.Response.OK)).isNull();
        }
    }''',
     '''    @Test void aSocketFileLeftByACrashIsReplaced() throws Exception {
        Files.createFile(socket());
        Files.writeString(token(), "left-over\\n");
        try (HandoffSocket endpoint = bind(request -> LaunchRequest.Response.OK)) {
            assertThat(handOff(launch(1L))).as("the stale file was replaced by a working endpoint").isEqualTo(LaunchRequest.Response.OK);
        }
    }''', 1),
    ('''    @Test void aLivenessProbeLogsQuietlyRatherThanAlarmingly() throws Exception {''',
     '''    @Test void aPeerThatHangsUpBeforeTheReplyLogsQuietlyRatherThanAlarmingly() throws Exception {''', 1),
    ('''                assertThat(HandoffSocket.bind(socket(), token(), lock(), request -> LaunchRequest.Response.OK)).isNull();
                // One acceptor serves in order: a real reply proves the probe was served.''',
     '''                SocketChannel.open(UnixDomainSocketAddress.of(socket())).close();
                // One acceptor serves in order: a real reply proves the peer that hung up was served.''', 1),
    ('''        assertThat(records).as("a normal liveness probe must not log an alarming, stack-trace-bearing WARNING")''',
     '''        assertThat(records).as("a peer that hung up must not log an alarming, stack-trace-bearing WARNING")''', 1),
    ('''        assertThat(HandoffSocket.bind(deep.resolve("socket"), deep.resolve("token"), deep.resolve("lock"),
            request -> LaunchRequest.Response.OK)).isNull();''',
     '''        assertThat(HandoffSocket.bind(deep.resolve("socket"), deep.resolve("token"), request -> LaunchRequest.Response.OK)).isNull();''', 1)])
cut(H, '''    @Test void theOwnerRecordsItsProcessIdBesideTheTokenUntilItCloses() throws Exception {''',
    '''    @Test void aWrongTokenIsRefusedWithoutReachingTheHandler() throws Exception {''')
cut(H, '''    @Test void livenessFollowsTheEndpoint() throws Exception {''', '''\n}\n''')
edit(H, [('''    }\n\n\n}\n''', '''    }\n}\n''', 1)])
C = T + "commandline/CommandListenerTest.java"
edit(C, [
    ('''    @Test void onlyOneProcessServesTheSocketAndClosingRemovesIt() throws Exception {
        CommandListener first = bind(invocation -> CompletableFuture.completedFuture(0));
        assertThat(CommandListener.live(socket())).isTrue();
        assertThat(CommandListener.bind(socket(), invocation -> CompletableFuture.completedFuture(0))).isNull();
        first.close();
        assertThat(CommandListener.live(socket())).isFalse();
        assertThat(socket()).doesNotExist();
        assertThat(CommandListener.tokenFile(socket())).doesNotExist();
        assertThat(CommandListener.ownerFile(socket())).doesNotExist();
        bind(invocation -> CompletableFuture.completedFuture(0));
    }

    @Test void aSocketFileNobodyServesIsReplaced() throws Exception {
        Files.createDirectories(socket().getParent());
        Files.writeString(socket(), "left behind by a crash");
        bind(invocation -> CompletableFuture.completedFuture(0));
        assertThat(CommandListener.live(socket())).isTrue();
    }

    @Test void aPrivateSocketTakesItsLockFileAlongWhenItCloses() throws Exception {
        Path socket = dir.resolve("daemon").resolve("commands-42");
        CommandListener listener = CommandListener.bind(socket, invocation -> CompletableFuture.completedFuture(0));
        assertThat(listener).isNotNull();
        listener.close();
        try (var listing = Files.list(socket.getParent())) { assertThat(listing).isEmpty(); }
    }''',
     '''    @Test void closingRemovesTheSocketAndItsFilesAndLeavesNothingInTheFolder() throws Exception {
        CommandListener first = bind(invocation -> CompletableFuture.completedFuture(0));
        first.close();
        assertThat(socket()).doesNotExist();
        assertThat(CommandListener.tokenFile(socket())).doesNotExist();
        assertThat(CommandListener.ownerFile(socket())).doesNotExist();
        try (var listing = Files.list(socket().getParent())) { assertThat(listing).as("no lock file either").isEmpty(); }
        bind(invocation -> CompletableFuture.completedFuture(0));
    }

    @Test void aSocketFileLeftByACrashIsReplaced() throws Exception {
        Files.createDirectories(socket().getParent());
        Files.writeString(socket(), "left behind by a crash");
        bind(invocation -> CompletableFuture.completedFuture(0));
        assertThat(exchange(request(token(), List.of()))).as("served").isEqualTo(CommandProtocol.exit(0));
    }''', 1)])
edit(T + "platform/AppDirsTest.java", [
    ('''        assertThat(dirs.daemonLock()).isEqualTo(daemon.resolve("lock"));\n''', '', 1),
    ('''        assertThat(dirs.commandSocket(42)).isEqualTo(daemon.resolve("commands-42"));\n''', '', 1)])
R = T + "bootstrap/ApplicationBootstrapRollbackTest.java"
edit(R, [
    ('''                endpoint[0] = HandoffSocket.bind(dir.resolve("socket"), dir.resolve("token"), dir.resolve("lock"),
                    request -> LaunchRequest.Response.OK);''',
     '''                endpoint[0] = HandoffSocket.bind(dir.resolve("socket"), dir.resolve("token"), request -> LaunchRequest.Response.OK);''', 1),
    ('''        try (var replacement = HandoffSocket.bind(dir.resolve("socket"), dir.resolve("token"), dir.resolve("lock"),
                request -> LaunchRequest.Response.OK)) {''',
     '''        try (var replacement = HandoffSocket.bind(dir.resolve("socket"), dir.resolve("token"), request -> LaunchRequest.Response.OK)) {''', 1)])
cut(R, '''    @org.junit.jupiter.params.ParameterizedTest''', '''\n}\n''')
edit(R, [('''    }\n\n}\n''', '''    }\n}\n''', 1)])
PY
```

The removed rollback test proved that `daemon/lock`, held by another process, could not block the EDT or the bounded exit
(`EndpointLockChild` held it); that lock no longer exists. The handoff endpoint's process-id test goes with its
`token.pid`; `InstanceLockTest.theOwnerRecordsItsProcessIdBesideTheLock` covers the owner's id. Delete
`jasper-app/src/test/java/dev/jasper/app/testsupport/EndpointLockChild.java`.

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :jasper-app:compileTestJava`
Expected: FAIL to compile: "method bind in class HandoffSocket cannot be applied to given types" (the three-argument
calls).

- [ ] **Step 3: Endpoints only the owner binds**

Replace `jasper-app/src/main/java/dev/jasper/app/residency/HandoffSocket.java` with:

```java
package dev.jasper.app.residency;

import dev.jasper.app.platform.CodeLocation;
import dev.jasper.app.platform.OwnerOnlyFiles;
import java.io.IOException;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BiPredicate;
import java.util.function.Function;

/**
 * The handoff endpoint (workspace-tabs spec 5.3). The home's owner serves it; every other launch hands its request to it
 * and exits. Only the owner binds, under its instance lock, so nothing else ever competes for the path.
 *
 * <p>The accept loop runs on a <strong>non-daemon</strong> platform thread, and that is deliberate:
 * AWT shuts its event dispatch thread down once nothing is displayable, so this thread is what keeps
 * a windowless resident JVM alive. Closing the endpoint ends the loop, which is how a resident
 * process with no windows exits.
 */
public final class HandoffSocket implements AutoCloseable {
    private static final System.Logger LOG = System.getLogger(HandoffSocket.class.getName());
    /** macOS caps sun_path at 104 bytes; a long home directory can reach it. */
    private static final int MAX_SOCKET_PATH_BYTES = 100;
    /** An answer is a word or two. */
    private static final int MAX_REPLY_BYTES = 256;
    /** The launch's wait for an answer: the owner reads until its peer has been silent for READ_TIMEOUT_MILLIS, then
     *  gives its event thread up to a second to take the request (spec 5.3), so a request queued behind one stalled
     *  peer is still answered inside it. */
    private static final long HANDOFF_TIMEOUT_MILLIS = 3_000;
    /** How long a peer may stay silent while the accept thread reads from it, so one stalled peer holds the endpoint
     *  for at most this long. A real client writes its line at once; a long line arrives in chunks, each resetting it. */
    private static final long READ_TIMEOUT_MILLIS = 500;
    /** Backoff after an accept-loop failure, so a persistent error (e.g. exhausted file
     *  descriptors) cannot spin the loop at 100% CPU retrying instantly. */
    private static final long ACCEPT_FAILURE_BACKOFF_MILLIS = 50;

    private final ServerSocketChannel channel;
    private final Path socketPath;
    private final Path tokenPath;
    private final String token;
    private final Function<LaunchRequest, LaunchRequest.Response> handler;
    private final Thread acceptor;
    private volatile boolean closed;

    private HandoffSocket(ServerSocketChannel channel, Path socketPath, Path tokenPath, String token,
                          Function<LaunchRequest, LaunchRequest.Response> handler) {
        this.channel = channel;
        this.socketPath = socketPath;
        this.tokenPath = tokenPath;
        this.token = token;
        this.handler = handler;
        this.acceptor = Thread.ofPlatform().name("jasper-handoff-accept").unstarted(this::acceptLoop);
    }

    /**
     * Binds the owner's endpoint, replacing whatever a process that has gone left at the path, or returns null when it is
     * unavailable here. An owner without one runs, unreachable: a second launch reports it as not responding.
     *
     * @param socketPath the endpoint
     * @param tokenPath the token every request must present
     * @param handler answers each request with a valid token
     * @return the endpoint, or null
     */
    public static HandoffSocket bind(Path socketPath, Path tokenPath, Function<LaunchRequest, LaunchRequest.Response> handler) {
        if (!pathFits(socketPath)) {
            LOG.log(System.Logger.Level.WARNING, tooLong(socketPath));
            return null;
        }
        ServerSocketChannel opened = null;
        try {
            Files.createDirectories(socketPath.getParent());
            OwnerOnlyFiles.restrict(socketPath.getParent(), "rwx------");
            Files.deleteIfExists(socketPath);
            opened = ServerSocketChannel.open(StandardProtocolFamily.UNIX);
            opened.bind(UnixDomainSocketAddress.of(socketPath));
            String token = OwnerOnlyFiles.writeToken(tokenPath);
            var endpoint = new HandoffSocket(opened, socketPath, tokenPath, token, handler);
            endpoint.acceptor.start();
            return endpoint;
        } catch (IOException | RuntimeException failure) {
            // A bind that got as far as the socket file but no further must not leave it behind.
            if (opened != null) {
                try { opened.close(); } catch (IOException ignored) { }
                try { Files.deleteIfExists(socketPath); } catch (IOException ignored) { }
            }
            LOG.log(System.Logger.Level.WARNING, "The handoff endpoint is unavailable", failure);
            return null;
        }
    }

    /**
     * Hands {@code request} to the home's owner, signed with the token beside its socket, and returns the owner's
     * answer: OK or UPDATED when it took the request; TOKEN or PROTOCOL otherwise, including when nothing answers or no
     * token can be read. Blocks, bounded.
     *
     * @param socketPath the owner's endpoint
     * @param tokenPath the token beside it
     * @param request the launch's request; its token is replaced
     * @return the answer
     */
    public static LaunchRequest.Response handOff(Path socketPath, Path tokenPath, LaunchRequest request) {
        String token = readToken(tokenPath);
        if (token == null) return LaunchRequest.Response.PROTOCOL;
        LaunchRequest signed = request.withToken(token);
        var answer = new CompletableFuture<LaunchRequest.Response>();
        // A wedged owner must not hang the caller, so the exchange is bounded from outside it.
        Thread worker = Thread.ofPlatform().daemon().name("jasper-handoff").start(() -> {
            try (SocketChannel client = SocketChannel.open(UnixDomainSocketAddress.of(socketPath))) {
                write(client, signed.encode());
                // The launcher's own budget, not the server's silent-peer tolerance: this reply may
                // legitimately be queued behind a stalled peer, and must not give up just as it
                // is about to be served. The future below is the hard outer bound either way.
                answer.complete(LaunchRequest.Response.of(readLine(client, HANDOFF_TIMEOUT_MILLIS, MAX_REPLY_BYTES)));
            } catch (IOException | RuntimeException failure) {
                answer.complete(LaunchRequest.Response.PROTOCOL);
            }
        });
        try {
            return answer.get(HANDOFF_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        } catch (TimeoutException | ExecutionException failure) {
            worker.interrupt();
            return LaunchRequest.Response.PROTOCOL;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return LaunchRequest.Response.PROTOCOL;
        }
    }

    /**
     * The owner's answer (spec 5.3): {@code taker} is offered each request and whether the launch ran another build than
     * {@code source} at {@code modified}. When it takes the request the answer is UPDATED for another build and OK
     * otherwise; when it does not, PROTOCOL, which the launch treats as no answer and tries again.
     *
     * @param taker takes a request, or declines it; bounded, since the accept thread waits for it
     * @param source this build's code location, or null when unknown
     * @param modified its modification time
     * @return the endpoint's handler
     */
    public static Function<LaunchRequest, LaunchRequest.Response> answering(BiPredicate<LaunchRequest, Boolean> taker,
                                                                            Path source, long modified) {
        Objects.requireNonNull(taker, "taker");
        return request -> {
            boolean updated = request.anotherBuild(source, modified);
            if (!taker.test(request, updated)) return LaunchRequest.Response.PROTOCOL;
            return updated ? LaunchRequest.Response.UPDATED : LaunchRequest.Response.OK;
        };
    }

    /**
     * Whether {@code socketPath} fits a Unix socket address: macOS allows 104 bytes and Jasper keeps to 100.
     *
     * @param socketPath an endpoint's path
     * @return true when it can be bound
     */
    public static boolean pathFits(Path socketPath) {
        return socketPath.toString().getBytes(StandardCharsets.UTF_8).length <= MAX_SOCKET_PATH_BYTES;
    }

    /**
     * What a launch says, and an owner logs, when its home's socket path is too long to bind (plan Ruling 15).
     *
     * @param socketPath the endpoint's path
     * @return the message
     */
    public static String tooLong(Path socketPath) {
        return "Jasper's home folder path is too long for its socket: " + socketPath;
    }

    /** The jar or classes directory this build was loaded from; null when it cannot be determined. */
    public static Path codeLocation() {
        return CodeLocation.of(HandoffSocket.class);
    }

    /** Epoch milliseconds, or zero when the file cannot be read; zero never equals a real stamp. */
    public static long lastModified(Path path) {
        if (path == null) return 0;
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (IOException | RuntimeException unavailable) {
            return 0;
        }
    }

    @Override public void close() {
        if (closed) return;
        closed = true;
        try { channel.close(); } catch (IOException ignored) { }
        // Serving is bounded (a silent peer by READ_TIMEOUT_MILLIS, the answer by its taker); an acceptor still serving
        // after this finishes its reply on its own.
        if (Thread.currentThread() != acceptor) {
            try { acceptor.join(Duration.ofMillis(2 * READ_TIMEOUT_MILLIS)); }
            catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
        }
        // The owner holds the instance lock until it exits, so these files are still its own.
        try {
            Files.deleteIfExists(socketPath);
            Files.deleteIfExists(tokenPath);
        } catch (IOException | RuntimeException unavailable) {
            // Left behind; the next owner replaces them.
        }
    }

    private void acceptLoop() {
        while (!closed) {
            try (SocketChannel client = channel.accept()) {
                serve(client);
            } catch (ClosedChannelException stopped) {
                return;
            } catch (IOException | RuntimeException failure) {
                if (closed) return;
                LOG.log(System.Logger.Level.WARNING, "A handoff request failed", failure);
                // A persistent failure -- accept() itself throwing, e.g. because file descriptors
                // are exhausted -- must not spin this loop at 100% CPU: a long-lived resident
                // process has no window on screen for anyone to notice from. A close() during the
                // sleep is still picked up by the loop guard above, at most this much later.
                try {
                    Thread.sleep(ACCEPT_FAILURE_BACKOFF_MILLIS);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    private void serve(SocketChannel client) throws IOException {
        LaunchRequest request = LaunchRequest.decode(readLine(client, READ_TIMEOUT_MILLIS, LaunchRequest.MAX_LINE_BYTES));
        LaunchRequest.Response response;
        if (request == null) response = LaunchRequest.Response.PROTOCOL;
        else if (!MessageDigest.isEqual(token.getBytes(StandardCharsets.UTF_8),
                request.token().getBytes(StandardCharsets.UTF_8))) {
            LOG.log(System.Logger.Level.WARNING, "A handoff request presented the wrong token; refused");
            response = LaunchRequest.Response.TOKEN;
        } else response = handler.apply(request);
        try {
            write(client, response.line());
        } catch (IOException peerGone) {
            // A launch that gave up waiting looks, from here, like a peer that hung up before the reply arrived:
            // ordinary traffic, not a stack-trace-worthy failure. A genuine failure earlier in this method (reading the
            // request) still propagates and is logged as one by acceptLoop.
            LOG.log(System.Logger.Level.DEBUG, "Could not reply to a handoff request; the peer had already gone");
        }
    }

    private static String readToken(Path tokenPath) {
        try {
            String token = Files.readString(tokenPath, StandardCharsets.UTF_8).strip();
            return token.isEmpty() ? null : token;
        } catch (IOException | RuntimeException unavailable) {
            return null;
        }
    }

    /**
     * Bounded read of one line of at most {@code limit} bytes: it ends when the peer has sent nothing for
     * {@code timeoutMillis}, so a peer that connects and says nothing cannot hold the endpoint, while a long line that
     * keeps arriving is read whole.
     */
    private static String readLine(SocketChannel channel, long timeoutMillis, int limit) throws IOException {
        channel.configureBlocking(false);
        ByteBuffer buffer = ByteBuffer.allocate(limit);
        long silence = TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        long deadline = System.nanoTime() + silence;
        int scanned = 0;
        while (buffer.hasRemaining()) {
            int read = channel.read(buffer);
            if (read < 0) break;
            if (read > 0) deadline = System.nanoTime() + silence;
            for (; scanned < buffer.position(); scanned++) {
                if (buffer.get(scanned) == '\n') {
                    return new String(buffer.array(), 0, scanned, StandardCharsets.UTF_8);
                }
            }
            if (read == 0) {
                if (System.nanoTime() >= deadline) break;
                try { Thread.sleep(5); }
                catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); break; }
            }
        }
        return new String(buffer.array(), 0, buffer.position(), StandardCharsets.UTF_8);
    }

    private static void write(SocketChannel channel, String text) throws IOException {
        ByteBuffer buffer = ByteBuffer.wrap(text.getBytes(StandardCharsets.UTF_8));
        while (buffer.hasRemaining()) channel.write(buffer);
    }
}
```

Then run from the worktree root:

```bash
python3 -I - <<'PY'
"""Workspace tabs 1, Task 8 step 3: the command socket, AppDirs and the owner bind without locks."""
import pathlib
def edit(name, edits):
    path = pathlib.Path(name); text = path.read_text(encoding="utf-8")
    for old, new, count in edits:
        if text.count(old) != count: raise SystemExit(f"{name}: expected {count} of {old!r}, found {text.count(old)}")
        text = text.replace(old, new)
    path.write_text(text, encoding="utf-8")
M = "jasper-app/src/main/java/dev/jasper/app/"
edit(M + "commandline/CommandListener.java", [
    ('''import java.nio.channels.FileChannel;\nimport java.nio.channels.FileLock;\n''', '', 1),
    ('''import java.nio.file.StandardOpenOption;\n''', '', 1),
    ('''    /**
     * Binds the socket, or returns null when another process already serves it or it is unavailable here: the
     * {@code jasper} command is a convenience, so failing to bind is never fatal. The token is written before the
     * socket appears, so a client that finds the socket can read it.
     */''',
     '''    /**
     * Binds the socket, or returns null when it is unavailable here: the {@code jasper} command is a convenience, so
     * failing to bind is never fatal. Only the home's owner binds (workspace-tabs spec 5.3), so whatever is at the path
     * was left by a process that has gone, and is replaced. The token is written before the socket appears, so a client
     * that finds the socket can read it.
     */''', 1),
    ('''            try (FileChannel lockChannel = FileChannel.open(lockFile(socket), StandardOpenOption.CREATE,
                    StandardOpenOption.READ, StandardOpenOption.WRITE);
                 FileLock ignored = lockChannel.lock()) {
                if (Files.exists(socket)) {
                    if (live(socket)) return null;
                    Files.deleteIfExists(socket);
                }
                String token = OwnerOnlyFiles.writeToken(tokenFile(socket));
                OwnerOnlyFiles.writeProcessId(ownerFile(socket));
                opened = ServerSocketChannel.open(StandardProtocolFamily.UNIX);
                opened.bind(UnixDomainSocketAddress.of(socket));
                var listener = new CommandListener(opened, socket, token, dispatcher);
                listener.acceptor.start();
                return listener;
            }''',
     '''            Files.deleteIfExists(socket);
            String token = OwnerOnlyFiles.writeToken(tokenFile(socket));
            OwnerOnlyFiles.writeProcessId(ownerFile(socket));
            opened = ServerSocketChannel.open(StandardProtocolFamily.UNIX);
            opened.bind(UnixDomainSocketAddress.of(socket));
            var listener = new CommandListener(opened, socket, token, dispatcher);
            listener.acceptor.start();
            return listener;''', 1),
    ('''    /** True while some process accepts connections on the socket. */
    public static boolean live(Path socket) {
        try (SocketChannel probe = SocketChannel.open(UnixDomainSocketAddress.of(socket))) {
            return true;
        } catch (IOException | RuntimeException gone) {
            return false;
        }
    }

''', '', 1),
    ('''    static Path lockFile(Path socket) { return socket.resolveSibling(socket.getFileName() + ".lock"); }

''', '', 1),
    ('''        try (FileChannel lockChannel = FileChannel.open(lockFile(socket), StandardOpenOption.CREATE,
                StandardOpenOption.READ, StandardOpenOption.WRITE);
             FileLock ignored = lockChannel.lock()) {
            if (live(socket)) return;
            Files.deleteIfExists(socket);
            Files.deleteIfExists(tokenFile(socket));
            Files.deleteIfExists(ownerFile(socket));
        } catch (IOException | RuntimeException unavailable) {
            // Including OverlappingFileLockException: leave the files rather than risk a successor's.
            return;
        }
        // A private launch's socket is named for its process, so nothing else ever takes its lock.
        if (socket.getFileName().toString().startsWith("commands-")) {
            try { Files.deleteIfExists(lockFile(socket)); } catch (IOException ignored) { }
        }
    }''',
     '''        // Only the owner binds here, and it holds the instance lock until it exits: these files are this listener's.
        try {
            Files.deleteIfExists(socket);
            Files.deleteIfExists(tokenFile(socket));
            Files.deleteIfExists(ownerFile(socket));
        } catch (IOException unavailable) {
            // Left behind; the next owner replaces them.
        }
    }''', 1)])
edit(M + "platform/AppDirs.java", [
    ('''    public Path daemonLock() {
        return daemonDir().resolve("lock");
    }

''', '', 1),
    ('''    /** The command socket of every launch but a private one; the {@code jasper} command looks for it here. */''',
     '''    /** The command socket the home's owner serves; the {@code jasper} command looks for it here. */''', 1),
    ('''
    /**
     * A private launch's own command socket ({@code --config}, {@code --safe-mode}, {@code --plugin-dir},
     * {@code --standalone}), named for its process.
     */
    public Path commandSocket(long pid) {
        return daemonDir().resolve("commands-" + pid);
    }
''', '', 1)])
edit(M + "bootstrap/Instance.java", [
    ('''        this.handoff = HandoffSocket.bind(dirs.daemonSocket(), dirs.daemonToken(), dirs.daemonLock(),''',
     '''        this.handoff = HandoffSocket.bind(dirs.daemonSocket(), dirs.daemonToken(),''', 1)])
PY
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.residency.*' --tests 'dev.jasper.app.commandline.*' --tests 'dev.jasper.app.platform.AppDirsTest' --tests 'dev.jasper.app.bootstrap.*' --tests 'dev.jasper.app.appearance.ThemeMigrationCoverageTest' --continue`
Expected: PASS (`SingleInstanceProcessTest.aKilledOwnerLeavesNothingThatStopsTheNextLaunch` still finds the killed
owner's socket file replaced, now without a probe). Then `./gradlew :jasper-app:check`: PASS.

- [ ] **Step 5: Commit**

```bash
git add -A jasper-app/src
git commit -F - <<'EOF'
Single instance: the bind-time locks and private sockets go

Only the home's owner binds, under its instance lock, so the handoff
endpoint and the command socket replace whatever a process that has gone
left and delete their files on close without a lock or a liveness
probe. daemon/lock, <socket>.lock, the commands-<pid> sockets and their
AppDirs entries go, and so does the handoff endpoint's token.pid: the
owner's process id is daemon/instance.pid.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

---

### Task 9: `jasper <path>...`

Spec 5.4: `jasper` with no arguments raises; `jasper <path>…` opens the paths as OPEN_PATHS does (today "unknown
command"). The running Jasper decides, so the C client only documents it. Rulings 11, 21.

**Files:**
- Modify: `jasper-app/src/main/java/dev/jasper/app/application/JasperApplication.java` (`commands()`, Task 4's text)
- Modify: `jasper-app/src/main/java/dev/jasper/app/plugins/PluginCommands.java:53-54`, `PluginRuntime.java:567`
- Modify: `packaging/cli/src/jasper_cli.c:63-69`
- Modify (test): `application/ApplicationCommandsTest.java`, `plugins/PluginRuntimeTest.java:300-307`,
  `commandline/CommandClientTest.java:121-127,298-306`

**Interfaces:**
- Consumes: `JasperApplication.open(List<Path>, String)` (Task 4).
- Produces: `static Optional<List<Path>> JasperApplication.commandPaths(Path workingDirectory, List<String> arguments,
  Predicate<String> subcommand)`, `static List<String> JasperApplication.missingPaths(Path workingDirectory, List<String>
  arguments, Predicate<String> subcommand)`; `boolean PluginCommands.declares(String)`; `public boolean
  PluginRuntime.declaresSubcommand(String name)`.

- [ ] **Step 1: Write the failing tests**

Run from the worktree root:

```bash
python3 -I - <<'PY'
"""Workspace tabs 1, Task 9 step 1: tests of path calls."""
import pathlib
def edit(name, edits):
    path = pathlib.Path(name); text = path.read_text(encoding="utf-8")
    for old, new, count in edits:
        if text.count(old) != count: raise SystemExit(f"{name}: expected {count} of {old!r}, found {text.count(old)}")
        text = text.replace(old, new)
    path.write_text(text, encoding="utf-8")
T = "jasper-app/src/test/java/dev/jasper/app/"
edit(T + "application/ApplicationCommandsTest.java", [
    ('''import java.nio.file.Path;\n''', '''import java.nio.file.Files;\nimport java.nio.file.Path;\n''', 1),
    ('''    @Test void aJasperThatIsNotResidentQuitsOnceNothingKeepsIt() throws Exception {''',
     '''    @Test void commandArgumentsArePathsOnlyWhenEveryOneExistsAndNoSubcommandClaimsTheFirst() throws Exception {
        Path notes = Files.writeString(dir.resolve("notes.txt"), "");
        Path src = Files.createDirectory(dir.resolve("src"));
        Path editor = Files.writeString(dir.resolve("editor"), "");
        assertThat(JasperApplication.commandPaths(dir, List.of(), name -> false)).as("no arguments: a raise").contains(List.of());
        assertThat(JasperApplication.commandPaths(dir, List.of("notes.txt", "./src", src.toString()), name -> false))
            .contains(List.of(notes, src, src));
        assertThat(JasperApplication.commandPaths(dir, List.of("notes.txt", "missing.txt"), name -> false)).isEmpty();
        assertThat(JasperApplication.commandPaths(dir, List.of("editor"), "editor"::equals)).as("a subcommand wins").isEmpty();
        assertThat(JasperApplication.commandPaths(dir, List.of("./editor"), "editor"::equals)).contains(List.of(editor));
        assertThat(JasperApplication.commandPaths(dir, List.of("editr"), "editor"::equals)).as("a word that names nothing").isEmpty();
    }

    @Test void aCallNamingFilesOpensThemAndIsAnsweredOnceTheyAreOpen() throws Exception {
        JasperApplication application = application(() -> { });
        Files.writeString(dir.resolve("notes.txt"), "");
        DesktopTestSupport.edt(application::quitNow);   // open is then inert: no window can be composed headlessly
        var err = new StringBuilder();
        var exit = application.commands().dispatch(new Invocation(List.of("notes.txt"), dir, Optional.empty(), text -> { }, err::append));
        assertThat(exit.toCompletableFuture().get(5, TimeUnit.SECONDS)).isZero();
        assertThat(err).as("a path call, not a plugin's subcommand").isEmpty();
    }

    @Test void aWordThatNamesNothingIsStillACommand() throws Exception {
        JasperApplication application = application(() -> { });
        var err = new StringBuilder();
        var exit = application.commands().dispatch(new Invocation(List.of("editr"), dir, Optional.empty(), text -> { }, err::append));
        assertThat(exit.toCompletableFuture().get(5, TimeUnit.SECONDS)).isEqualTo(1);
        assertThat(err).hasToString("jasper: Jasper's plugins are not running\\n");
        DesktopTestSupport.edt(application::quitNow);
    }

    @Test void aCallThatStartsWithAPathSaysWhichOthersAreMissing() throws Exception {
        Files.writeString(dir.resolve("notes.txt"), "");
        assertThat(JasperApplication.missingPaths(dir, List.of("notes.txt", "gone.txt", "src/x"), name -> false))
            .containsExactly("gone.txt", "src/x");
        assertThat(JasperApplication.missingPaths(dir, List.of("gone.txt", "notes.txt"), name -> false)).as("a command, as before").isEmpty();
        assertThat(JasperApplication.missingPaths(dir, List.of("notes.txt", "gone.txt"), "notes.txt"::equals)).as("a subcommand").isEmpty();
        JasperApplication application = application(() -> { });
        var err = new StringBuilder();
        var exit = application.commands().dispatch(new Invocation(List.of("notes.txt", "gone.txt"), dir, Optional.empty(), text -> { }, err::append));
        assertThat(exit.toCompletableFuture().get(5, TimeUnit.SECONDS)).isEqualTo(1);
        assertThat(err).hasToString("jasper: no such file or folder: gone.txt\\n");
        DesktopTestSupport.edt(application::quitNow);
    }

    @Test void aJasperThatIsNotResidentQuitsOnceNothingKeepsIt() throws Exception {''', 1)])
edit(T + "plugins/PluginRuntimeTest.java", [
    ('''        onEdt(() -> runtime.subcommandList(list));
        onEdt(() -> runtime.start(Map.of(), true));''',
     '''        onEdt(() -> runtime.subcommandList(list));
        assertThat(runtime.declaresSubcommand("alpha")).as("before the plugins start").isFalse();
        onEdt(() -> runtime.start(Map.of(), true));''', 1),
    ('''        assertThat(run(runtime, "gamma", 2)).isEqualTo("jasper: unknown command gamma; run jasper --help for the list\\n");''',
     '''        assertThat(run(runtime, "gamma", 2)).isEqualTo("jasper: unknown command gamma; run jasper --help for the list\\n");
        assertThat(runtime.declaresSubcommand("alpha")).isTrue();
        assertThat(runtime.declaresSubcommand("beta")).as("declared, though its plugin waits for consent").isTrue();
        assertThat(runtime.declaresSubcommand("gamma")).isFalse();''', 1)])
edit(T + "commandline/CommandClientTest.java", [
    ('''    @Test void theHomeComesFromJasperHomeWhenNoSocketIsNamed() throws Exception {''',
     '''    @Test void pathsAreACallThatNamesTheCallersFolder() throws Exception {
        var seen = new AtomicReference<Invocation>();
        bind(invocation -> { seen.set(invocation); return CompletableFuture.completedFuture(0); });
        String absolute = dir.resolve("notes.txt").toString();
        assertThat(run(atSocket(), List.of("./notes.txt", "src", absolute)).exit()).isZero();
        assertThat(seen.get().arguments()).as("sent as given: Jasper tells a path from a subcommand")
            .containsExactly("./notes.txt", "src", absolute);
        assertThat(seen.get().workingDirectory().toRealPath()).isEqualTo(dir.toRealPath());
    }

    @Test void theHomeComesFromJasperHomeWhenNoSocketIsNamed() throws Exception {''', 1),
    ('''        assertThat(bare.out()).startsWith("Usage: jasper").contains("Start Jasper once to list the plugin commands.");''',
     '''        assertThat(bare.out()).startsWith("Usage: jasper [PATH...]").contains("Start Jasper once to list the plugin commands.");''', 1)])
PY
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :jasper-app:compileTestJava`
Expected: FAIL to compile: "cannot find symbol … commandPaths", "… missingPaths", "… declaresSubcommand".

- [ ] **Step 3: Path calls, the subcommand table's answer and the usage**

Run from the worktree root:

```bash
python3 -I - <<'PY'
"""Workspace tabs 1, Task 9 step 3: the command socket opens paths."""
import pathlib
def edit(name, edits):
    path = pathlib.Path(name); text = path.read_text(encoding="utf-8")
    for old, new, count in edits:
        if text.count(old) != count: raise SystemExit(f"{name}: expected {count} of {old!r}, found {text.count(old)}")
        text = text.replace(old, new)
    path.write_text(text, encoding="utf-8")
M = "jasper-app/src/main/java/dev/jasper/app/"
edit(M + "application/JasperApplication.java", [
    ('''    /**
     * What the command socket runs: no arguments opens a window or raises one, as a launch would; anything else is a
     * plugin's subcommand. Called on listener threads.
     */
    public CommandDispatcher commands() {
        return invocation -> {
            if (invocation.arguments().isEmpty()) {
                SwingUtilities.invokeLater(() -> open(List.of(), HANDOFF));
                return CompletableFuture.completedFuture(0);
            }
            PluginRuntime runtime = plugins;
            if (runtime == null) {''',
     '''    /**
     * What the command socket runs (workspace-tabs spec 5.4): no arguments raises or opens a window, as a launch would;
     * a subcommand a plugin declares runs in that plugin; otherwise arguments that all name existing files or folders
     * open as a launch's paths do ({@code commandPaths}), and a call whose first argument names one while others name
     * nothing says which ({@code missingPaths}). A raise or a path call is answered once it has run. Called on listener
     * threads.
     */
    public CommandDispatcher commands() {
        return invocation -> {
            PluginRuntime runtime = plugins;
            java.util.function.Predicate<String> subcommand = name -> runtime != null && runtime.declaresSubcommand(name);
            Optional<List<Path>> paths = commandPaths(invocation.workingDirectory(), invocation.arguments(), subcommand);
            if (paths.isPresent()) {
                var opened = new CompletableFuture<Integer>();
                SwingUtilities.invokeLater(() -> {
                    try {
                        open(paths.get(), HANDOFF);
                        opened.complete(0);
                    } catch (RuntimeException failure) {
                        opened.completeExceptionally(failure);
                    }
                });
                return opened;
            }
            List<String> missing = missingPaths(invocation.workingDirectory(), invocation.arguments(), subcommand);
            if (!missing.isEmpty()) {
                invocation.err("jasper: no such file or folder: " + String.join(", ", missing) + "\\n");
                return CompletableFuture.completedFuture(1);
            }
            if (runtime == null) {''', 1),
    ('''    /** Quits a Jasper that is not resident once nothing keeps it: no terminal or plugin window, and {@code busy} false. EDT. */''',
     '''    /**
     * The {@code jasper} command's arguments as a launch's paths (workspace-tabs plan 1, Ruling 11): none is a raise; a
     * first argument {@code subcommand} accepts is a plugin's call; otherwise every argument, resolved against the
     * caller's folder, must name an existing file or folder. Any thread.
     *
     * @param workingDirectory the caller's folder
     * @param arguments everything after {@code jasper}
     * @param subcommand whether a plugin declares a name as its subcommand
     * @return the absolute paths to open (none to raise), or empty when the call is not about paths
     */
    static Optional<List<Path>> commandPaths(Path workingDirectory, List<String> arguments,
                                             java.util.function.Predicate<String> subcommand) {
        if (arguments.isEmpty()) return Optional.of(List.of());
        if (subcommand.test(arguments.getFirst())) return Optional.empty();
        try {
            List<Path> paths = arguments.stream().map(argument -> workingDirectory.resolve(argument).toAbsolutePath().normalize()).toList();
            return paths.stream().allMatch(java.nio.file.Files::exists) ? Optional.of(paths) : Optional.empty();
        } catch (java.nio.file.InvalidPathException notAPath) {
            return Optional.empty();
        }
    }

    /**
     * The arguments that name nothing, when the first names an existing file or folder and no plugin declares it as a
     * subcommand (workspace-tabs plan 1, Ruling 11): such a call meant paths, so it is told which are missing rather than
     * that it is an unknown command. Any thread.
     *
     * @param workingDirectory the caller's folder
     * @param arguments everything after {@code jasper}
     * @param subcommand whether a plugin declares a name as its subcommand
     * @return the missing arguments as given, in order; empty when the call is not about paths or all exist
     */
    static List<String> missingPaths(Path workingDirectory, List<String> arguments, java.util.function.Predicate<String> subcommand) {
        if (arguments.isEmpty() || subcommand.test(arguments.getFirst()) || !exists(workingDirectory, arguments.getFirst()))
            return List.of();
        return arguments.stream().filter(argument -> !exists(workingDirectory, argument)).toList();
    }

    private static boolean exists(Path workingDirectory, String argument) {
        try {
            return java.nio.file.Files.exists(workingDirectory.resolve(argument));
        } catch (java.nio.file.InvalidPathException notAPath) {
            return false;
        }
    }

    /** Quits a Jasper that is not resident once nothing keeps it: no terminal or plugin window, and {@code busy} false. EDT. */''', 1)])
edit(M + "plugins/PluginCommands.java", [
    ('''    /** The owners, by name. */
    List<Subcommands.Owner> owners() { return table.all(); }
''', '''    /** The owners, by name. */
    List<Subcommands.Owner> owners() { return table.all(); }

    /** Whether a plugin declares {@code name} as a subcommand, whether or not it runs. */
    boolean declares(String name) { return table.owner(name).isPresent(); }
''', 1)])
edit(M + "plugins/PluginRuntime.java", [
    ('''    /** Runs {@code jasper <subcommand>} calls in the plugins that answer them. */''',
     '''    /**
     * Whether a plugin declares {@code name} as a {@code jasper} subcommand, whether or not it runs; false before the
     * plugins start. Any thread.
     *
     * @param name the first argument of a call
     * @return true when the call is a plugin's
     */
    public boolean declaresSubcommand(String name) {
        PluginHost current = host;
        return current != null && current.commands.declares(name);
    }

    /** Runs {@code jasper <subcommand>} calls in the plugins that answer them. */''', 1)])
edit("packaging/cli/src/jasper_cli.c", [
    ('''    "Usage: jasper [--config <path>] [--safe-mode] [--plugin-dir <path>] [--standalone]\\n"
    "       jasper COMMAND [ARGUMENTS...]\\n"
    "       jasper --help\\n"
    "\\n"
    "With no arguments, opens a Jasper window; with options, starts Jasper with them. With a COMMAND, runs it in\\n"
    "the running Jasper, starting Jasper first when it is not running.\\n";''',
     '''    "Usage: jasper [PATH...]\\n"
    "       jasper [--config <path>] [--safe-mode] [--plugin-dir <path>] [PATH...]\\n"
    "       jasper COMMAND [ARGUMENTS...]\\n"
    "       jasper --help\\n"
    "\\n"
    "With no arguments, brings Jasper forward. With PATH arguments, opens each file in the editor and each folder as\\n"
    "a terminal tab; a COMMAND of the same name wins, so write ./NAME for such a file. With options, starts Jasper\\n"
    "with them; a Jasper that is already running keeps its own and says so. With a COMMAND, runs it in the running\\n"
    "Jasper. Jasper is started first when it is not running.\\n";''', 1)])
PY
```

The `{@code jasper <subcommand>}` in `PluginRuntime`'s existing comment is inside `{@code}`, which doclint accepts.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.application.ApplicationCommandsTest' --tests 'dev.jasper.app.plugins.PluginRuntimeTest' --tests 'dev.jasper.app.commandline.*' --continue`
Expected: PASS (`ApplicationCommandsTest` 7, `CommandClientTest` with the built client: the path call and the new
usage). Then `./gradlew :jasper-app:check` (it rebuilds the client and runs its native core tests): PASS.

- [ ] **Step 5: Commit**

```bash
git add jasper-app/src packaging/cli/src/jasper_cli.c
git commit -F - <<'EOF'
Single instance: jasper <path>... opens files and folders

The command socket treats a call whose arguments all name existing
files or folders, resolved against the caller's folder, as a launch's
paths, unless a plugin declares its first argument as a subcommand;
no arguments raise. Both are answered once they have run. A call whose
first argument names a file or folder but others do not lists the
missing ones. The client's usage says so.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

---

### Task 10: macOS open-file events and the plist

Spec 5.4: `Desktop.setOpenFileHandler` at start-up, files and folders through `open` (a file without a plugin's editor
is revealed, Ruling 26); the plist gains `CFBundleDocumentTypes` at `Alternate`, and `verifyPackage` checks it. Ruling 18.

**Files:**
- Modify: `jasper-app/src/main/java/dev/jasper/app/application/JasperApplication.java:90,155,399-402,1938` and after
  `restartForUpdate` (Task 5)
- Modify: `gradle/packaging.gradle:34,267,307,558`
- Modify (test): `application/JasperApplicationResidencyTest.java`

**Interfaces:**
- Consumes: `JasperApplication.open` (Task 4).
- Produces: `public static final String JasperApplication.OPEN_FILE = "open-file"`;
  `void JasperApplication.filesOpened(List<java.io.File> files)` (EDT).

- [ ] **Step 1: Write the failing test**

Run from the worktree root:

```bash
python3 -I - <<'PY'
"""Workspace tabs 1, Task 10 step 1: the open-file event's routing, headless."""
import pathlib
def edit(name, edits):
    path = pathlib.Path(name); text = path.read_text(encoding="utf-8")
    for old, new, count in edits:
        if text.count(old) != count: raise SystemExit(f"{name}: expected {count} of {old!r}, found {text.count(old)}")
        text = text.replace(old, new)
    path.write_text(text, encoding="utf-8")
edit("jasper-app/src/test/java/dev/jasper/app/application/JasperApplicationResidencyTest.java", [
    ('''    @Test void anOpenWithNoWindowGoesForOne() throws Exception {''',
     '''    @Test void theOpenFileEventOpensItsFilesAsAnyOpenDoes(@org.junit.jupiter.api.io.TempDir Path dir) throws Exception {
        Path notes = java.nio.file.Files.writeString(dir.resolve("notes.txt"), "");
        JasperApplication application = application(() -> { });
        try {
            // What the handler runs on the EDT (an OpenFilesEvent cannot be built headlessly).
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> edt(() -> application.filesOpened(List.of(notes.toFile()))))
                .as("no window is open, so one opens first").hasRootCauseInstanceOf(java.awt.HeadlessException.class);
        } finally {
            edt(application::quitNow);
        }
        edt(() -> application.filesOpened(List.of(notes.toFile())));   // once quitting: nothing, and no failure
    }

    @Test void anOpenWithNoWindowGoesForOne() throws Exception {''', 1)])
PY
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :jasper-app:compileTestJava`
Expected: FAIL to compile: "cannot find symbol … filesOpened".

- [ ] **Step 3: The handler and the plist**

Run from the worktree root:

```bash
python3 -I - <<'PY'
"""Workspace tabs 1, Task 10 step 3: the open-file handler, the plist keys and their check."""
import pathlib
def edit(name, edits):
    path = pathlib.Path(name); text = path.read_text(encoding="utf-8")
    for old, new, count in edits:
        if text.count(old) != count: raise SystemExit(f"{name}: expected {count} of {old!r}, found {text.count(old)}")
        text = text.replace(old, new)
    path.write_text(text, encoding="utf-8")
A = "jasper-app/src/main/java/dev/jasper/app/application/JasperApplication.java"
edit(A, [
    ('''    public static final String HANDOFF = "handoff";\n''',
     '''    public static final String HANDOFF = "handoff";
    /** The trigger of a window the macOS open-file event asked for. */
    public static final String OPEN_FILE = "open-file";\n''', 1),
    ('''    private boolean aboutHandlerInstalled;\n''', '''    private boolean aboutHandlerInstalled;\n    private boolean openFileHandlerInstalled;\n''', 1),
    ('''        if (supports(Desktop.Action.APP_EVENT_REOPENED)) {
            reopenListener = event -> SwingUtilities.invokeLater(this::reopened);
            Desktop.getDesktop().addAppEventListener(reopenListener);
        }
''', '''        if (supports(Desktop.Action.APP_EVENT_REOPENED)) {
            reopenListener = event -> SwingUtilities.invokeLater(this::reopened);
            Desktop.getDesktop().addAppEventListener(reopenListener);
        }
        // Finder's Open With, a drop on the Dock icon, open -a Jasper FILE (workspace-tabs spec 5.4). Installed before the
        // first window, so the event that starts a cold launch finds it.
        if (supports(Desktop.Action.APP_OPEN_FILE)) {
            Desktop.getDesktop().setOpenFileHandler(event -> {
                List<java.io.File> files = List.copyOf(event.getFiles());
                SwingUtilities.invokeLater(() -> filesOpened(files));
            });
            openFileHandlerInstalled = true;
        }
''', 1),
    ('''        if (aboutHandlerInstalled) { Desktop.getDesktop().setAboutHandler(null); aboutHandlerInstalled = false; }\n''',
     '''        if (aboutHandlerInstalled) { Desktop.getDesktop().setAboutHandler(null); aboutHandlerInstalled = false; }
        if (openFileHandlerInstalled) { Desktop.getDesktop().setOpenFileHandler(null); openFileHandlerInstalled = false; }\n''', 1),
    ('''                .withContent("Jasper could not work out how it was started. Quit and reopen it to run the new build."));
    }
''', '''                .withContent("Jasper could not work out how it was started. Quit and reopen it to run the new build."));
    }

    /**
     * The macOS open-file event (spec 5.4): each file opens in the editor a plugin provides, else is revealed, and each
     * folder as a terminal tab, as {@link #open} opens a launch's paths. EDT.
     *
     * @param files what Finder, the Dock or {@code open -a} handed over
     */
    void filesOpened(List<java.io.File> files) {
        open(files.stream().map(file -> file.toPath().toAbsolutePath()).toList(), OPEN_FILE);
    }
''', 1)])
G = "gradle/packaging.gradle"
edit(G, [
    ('''def localNetworkUsage = 'Jasper connects to SSH hosts on your local network.'\n''',
     '''def localNetworkUsage = 'Jasper connects to SSH hosts on your local network.'
// Workspace-tabs spec 5.4: Jasper appears in Open With and takes drops on its Dock icon for text, source code and
// folders, without claiming any of them as their default application.
def macDocumentTypes = '[{"CFBundleTypeName":"Text","CFBundleTypeRole":"Editor","LSHandlerRank":"Alternate","LSItemContentTypes":["public.text","public.source-code"]},{"CFBundleTypeName":"Folder","CFBundleTypeRole":"Viewer","LSHandlerRank":"Alternate","LSItemContentTypes":["public.folder"]}]'\n''', 1),
    ('''    inputs.property('localNetworkUsage', localNetworkUsage)\n''',
     '''    inputs.property('localNetworkUsage', localNetworkUsage)\n    inputs.property('documentTypes', macDocumentTypes)\n''', 1),
    ('''                    new File(image, 'Contents/Info.plist').absolutePath], 'plutil local network usage')\n''',
     '''                    new File(image, 'Contents/Info.plist').absolutePath], 'plutil local network usage')
                executeNative(['/usr/bin/plutil', '-replace', 'CFBundleDocumentTypes', '-json', macDocumentTypes,
                    new File(image, 'Contents/Info.plist').absolutePath], 'plutil document types')\n''', 1),
    ('''            if (localNetwork != localNetworkUsage) throw new GradleException("Package verification failed: plist local network usage description is '${localNetwork}'")\n''',
     '''            if (localNetwork != localNetworkUsage) throw new GradleException("Package verification failed: plist local network usage description is '${localNetwork}'")
            def documentTypes = new groovy.json.JsonSlurper().parseText(executeNative(['/usr/bin/plutil', '-extract', 'CFBundleDocumentTypes', 'json', '-o', '-', plist.absolutePath], 'plutil document types').stdout)
            if (documentTypes != new groovy.json.JsonSlurper().parseText(macDocumentTypes)) throw new GradleException("Package verification failed: plist document types are ${documentTypes}")\n''', 1)])
PY
```


- [ ] **Step 4: Run the tests to verify they pass, and the package on macOS**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.application.JasperApplicationResidencyTest' --tests 'dev.jasper.app.application.OpenPlanTest'`
Expected: PASS. Then `./gradlew :jasper-app:check`: PASS. On macOS also run `./gradlew :jasper-app:verifyPackage`
(headless without `-Pjasper.aot=true`; it builds the app image and reads the plist): PASS. Read the keys back once:
`plutil -p jasper-app/build/jpackage/image/Jasper.app/Contents/Info.plist | grep -A16 CFBundleDocumentTypes`
(adjust the image path to the one `verifyPackage` names if it differs).

- [ ] **Step 5: Commit**

```bash
git add jasper-app/src gradle/packaging.gradle
git commit -F - <<'EOF'
Single instance: macOS open-file events and document types

The open-file handler, installed with the other desktop handlers before
the first window, opens files in the editor and folders as terminal tabs
through JasperApplication.open. The packaged plist gains
CFBundleDocumentTypes for text, source code and folders at rank
Alternate; verifyPackage checks it.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

---

### Task 11: Documentation, STATUS and the whole build

The smallest edits that make each document describe the single instance (AGENTS.md "Documentation edits"), including the
standing inaccuracy that a `./gradlew run` session "stays resident" (a dev run has its own home, so it never was).

**Files:**
- Modify: `docs/configuration.md:15,17,24,37-45,279-280,294-316`, `config.example.toml:59`, `docs/app-architecture.md:51,69-73,86-88,102-103,108,159,178-179,186`,
  `docs/app-maintenance.md:220`, `docs/packaging.md:305,341,362,366`, `docs/sdk-architecture.md:18,71,267-274,391-393,398`,
  `docs/plugin-authoring.md:774-775`, `packaging/cli/README.md:4,39`, `docs/STATUS.md:2`

**Interfaces:**
- Consumes: everything above. Produces: nothing new.

- [ ] **Step 1: The documents**

Run from the worktree root:

````bash
python3 -I - <<'PY'
"""Workspace tabs 1, Task 11 step 1: the documents describe one Jasper per home."""
import pathlib
def edit(name, edits):
    path = pathlib.Path(name); text = path.read_text(encoding="utf-8")
    for old, new, count in edits:
        if text.count(old) != count: raise SystemExit(f"{name}: expected {count} of {old!r}, found {text.count(old)}")
        text = text.replace(old, new)
    path.write_text(text, encoding="utf-8")
edit("docs/configuration.md", [
    ('''logs and the resident process's socket. The `jasper.home` system property, or else the `JASPER_HOME` environment variable, moves the whole home somewhere else; a launch with either is standalone, exactly like `--config`, so it never hands off to or becomes the installed app's resident process.''',
     '''logs and the running Jasper's sockets and instance lock. The `jasper.home` system property, or else the `JASPER_HOME` environment variable, moves the whole home somewhere else, and with it the lock: a Jasper in another home runs beside the installed app's and is never resident.''', 1),
    ('''The override changes the configuration file location and makes the launch standalone (no residency or login-item changes; see [background residency](#background-residency)).''',
     '''The override changes the configuration file location; such a launch is never resident and never changes the login item (see [background residency](#background-residency)).''', 1),
    ('''`--safe-mode` starts without user-installed plugins, `--plugin-dir <path>` additionally loads one development plugin directory, and `--standalone` changes nothing else. All three, like `--config`, make the launch standalone: it never hands off to a resident Jasper and never becomes resident. In safe mode, File → Manage Plugins… shows that safe mode is on and offers Restart Normally. If another Jasper is resident, it is asked to quit first, because a plain launch would otherwise be handed to it.''',
     '''`--safe-mode` starts without user-installed plugins and `--plugin-dir <path>` additionally loads one development plugin directory; like `--config`, they make a launch that is never resident. In safe mode, File → Manage Plugins… shows that safe mode is on and offers Restart Normally.

One Jasper runs per home. A launch while Jasper runs hands its request to that Jasper and exits: it brings Jasper forward, or opens the files and folders named on its command line (`Jasper notes.txt src`), each file in the editor and each folder as a terminal tab. `--config`, `--safe-mode` and `--plugin-dir` apply only to a Jasper that starts: on a second launch they are ignored, and the running Jasper says to quit it first. A running Jasper that does not answer within 10 seconds is reported with its process id, and the launch exits without starting another.''', 1),
    ('''- `jasper` opens a window, or brings one forward.
- `jasper --safe-mode`, `--config <path>`, `--plugin-dir <path>` and `--standalone` start Jasper with those options.''',
     '''- `jasper` opens a window, or brings one forward.
- `jasper PATH...` opens each file in the [Editor](editor.md#opening-files) and each folder as a terminal tab; a command
  of the same name wins, so `jasper ./editor` opens a file named `editor`.
- `jasper --safe-mode`, `--config <path>` and `--plugin-dir <path>` start Jasper with those options when it is not running.''', 1),
    ('''under the default home. A launch with `--config`, `--safe-mode`, `--plugin-dir` or `--standalone` serves a socket of its
own, which only its own shells reach, and so does a Jasper started while another serves its home.''',
     '''under the default home.''', 1),
    ('''A development run (`./gradlew run`) stays resident but
never hides from the Dock, and Quit there still exits.''',
     '''A development run (`./gradlew run`) uses its own home,
so it is never resident and Quit there exits.''', 1),
    ('''Autostart needs an installed Jasper: a
`./gradlew run` development session stays resident but never registers a login item.''',
     '''Autostart needs an installed Jasper: a
`./gradlew run` development session is never resident and never registers a login item.''', 1),
    ('''whether a process owns the
handoff endpoint, shows the menu bar item and quits into the background is settled when it starts.''',
     '''whether a process outlives its
windows, shows the menu bar item and quits into the background is settled when it starts.''', 1),
    ('''Jasper keeps a socket, a token and a lock in a `daemon` directory beside `config.toml`, owner-only. A second launch
hands its request to the resident process over that socket and exits; on macOS a Dock click never creates a process.''',
     '''Jasper keeps its instance lock, its two sockets and their tokens in a `daemon` directory beside `config.toml`,
owner-only. The running Jasper holds the lock whether or not it is resident; a second launch hands its request over the
socket and exits, and on macOS a Dock click never creates a process.''', 1),
    ('''the next Dock click with no
window open relaunches Jasper as the new build; with windows open it raises them, and the relaunch waits until they are
closed. A plain launch of the new build likewise retires the old resident process, which exits if it has no windows
open or otherwise keeps running, no longer resident, until those windows are closed.''',
     '''the next Dock click or launch with no
window open relaunches Jasper as the new build, opening what the launch named; with windows open it raises them, a
launch of the new build also says that Jasper was updated, with a Restart action, and the relaunch waits for the next
Dock click or launch with no window open.''', 1),
    ('''**A launch with `--config` is fully standalone.** It never hands off to a resident process, never
binds the shared endpoint itself, is never itself resident, and never touches the login item. Any
resident process was itself started without `--config`, so it is always holding the default
configuration, never the one named on the command line; and the login item always points at the
installed app with no `--config`, so an override's `system.run_in_background` could never arm autostart
for the default configuration. `system.run_in_background` inside a `--config` file is therefore never
acted on.''',
     '''**A launch with `--config` is never resident** and never touches the login item. The login item always points at
the installed app with no `--config`, so an override's `system.run_in_background` could never arm autostart for the
default configuration; `system.run_in_background` inside a `--config` file is therefore never acted on.''', 1)])
edit("config.example.toml", [
    ('''# ./gradlew run development session stays resident but never registers a login item.''',
     '''# ./gradlew run development session uses its own home, is never resident and never registers a login item.''', 1)])
edit("docs/app-architecture.md", [
    ('''  Bootstrap->>Bootstrap: Parse arguments / attempt resident handoff before AWT''',
     '''  Bootstrap->>Bootstrap: Parse arguments / take the instance lock or hand off before AWT''', 1),
    ('''A successful resident handoff creates no window. Endpoint bind failure cannot silently
leave a background process. A `--background` (login) launch exits before reading the
configuration when another process already owns the endpoint. On Windows the owner
records its process id beside the token (`token.pid`), and a handing-off launcher lends''',
     '''One process owns each home: `bootstrap.SingleInstance` takes `residency.InstanceLock` (`daemon/instance.lock`) before
AWT and follows spec 5.2's table (`LaunchAction`). The owner binds the handoff endpoint and the command socket at once
(`bootstrap.Instance`) and holds requests in `EarlyRequests` until `compose` hands it the application; from then on it
answers a handoff only once the EDT has taken it (within a second), and none while it quits. Any other launch hands its
request (protocol 2: RAISE or OPEN_PATHS, its working directory, paths and ignored start-up flags) to the owner and
exits without creating a window; a `--background` or `--no-window` launch exits at once; restart's replacement
(`--replacing <pid>`) waits up to 30 s for the lock; an owner that does not answer within 10 s is reported
(`bootstrap.LaunchFailure`) and no second instance starts. A start that fails after taking the lock closes its
endpoints and exits, so the lock goes with the process. The owner records its process id in `daemon/instance.pid`
when it takes the lock, and on Windows a handing-off launcher lends''', 1),
    ('''Endpoint
lock/probe cleanup runs on daemon workers;''', '''Endpoint
cleanup runs on daemon workers;''', 1),
    ('''a plugin window's File › Quit, a retire request from another
launch, and macOS logging out''', '''a plugin window's File › Quit, and macOS logging out''', 1),
    ('''A reopen with no window open relaunches the process when its application jar changed on
disk.''', '''A reopen, or a launch of another build, with no window open relaunches the process when its
application jar changed on disk; the replacement waits for the lock.''', 1),
    ('''| `bootstrap` | Pre-AWT argument/handoff flow and EDT composition. StartupResources owns rollback until explicit transfer. |''',
     '''| `bootstrap` | Pre-AWT arguments, the home's instance lock and the handoff, and EDT composition. StartupResources owns rollback until explicit transfer; the owner's Instance keeps its lock for the life of the process. |''', 1),
    ('''| `residency` | Bounded interprocess protocol and endpoint workers. Bootstrap transfers endpoint close to application shutdown and process-hook backup. |''',
     '''| `residency` | The instance lock, the bounded handoff protocol and its endpoint worker. The lock lives as long as the process; bootstrap transfers endpoint close to application shutdown and process-hook backup. |''', 1),
    ('''Bootstrap binds the listener after plugins start; the application closes it at shutdown. |''',
     '''The home's owner binds the listener before AWT and holds its calls until the application is composed; the application closes it at shutdown. |''', 1),
    ('''| `restart` | Pure restart planning, the ResidentControl seam and the RestartFlow state machine. No owner; worker threads are daemons. |''',
     '''| `restart` | Pure restart planning (the replacement waits for the lock with `--replacing`) and the Plugins page's restart banner state (`RestartFlow`). No owner. |''', 1)])
edit("docs/app-maintenance.md", [
    ('''./gradlew :jasper-app:test --tests "*StartupResourcesTest" --tests "*ApplicationBootstrapRollbackTest" --tests "*MacTitleBarTest"
```

### Workspace glass''',
     '''./gradlew :jasper-app:test --tests "*StartupResourcesTest" --tests "*ApplicationBootstrapRollbackTest" --tests "*MacTitleBarTest"
```

### Single instance

One Jasper process owns each home (workspace-tabs spec section 5). `bootstrap.SingleInstance` takes
`residency.InstanceLock` before AWT and decides with `LaunchAction`; the owner's `bootstrap.Instance` binds both sockets
at once and holds requests in `EarlyRequests` until `compose` hands it the application. Change the handoff's wire format
in `residency.LaunchRequest` (with its protocol number), the owner's answer in `HandoffSocket.answering`, when the owner
takes a request in `bootstrap.Instance`, and what a request opens in `JasperApplication.open`. The process-level tests
start child JVMs through `testsupport.ChildJvm` with only the test classes and jasper-app's classes, so those classes
must not reach the toolkit or the other modules.

```sh
./gradlew :jasper-app:test --tests "*InstanceLockTest" --tests "*SingleInstance*" --tests "*EarlyRequestsTest" --tests "*LaunchRequestTest" --tests "*HandoffSocketTest"
```

### Workspace glass''', 1)])
edit("docs/packaging.md", [
    ('''## Native acceptance checklist''',
     '''## macOS Open With

`packageApp` declares `CFBundleDocumentTypes` for text, source code and folders at `LSHandlerRank` Alternate, so Jasper
appears in Finder's Open With and takes drops on its Dock icon without becoming any type's default application.
`verifyPackage` checks the key.

## Native acceptance checklist''', 1),
    ('''(the guard lives in `openOrRaise`; it needs a live window in flight and cannot be exercised headlessly).''',
     '''(the guard lives in `open`; it needs a live window in flight and cannot be exercised headlessly).
- [ ] With Jasper running, launch it again from Finder, Spotlight and `open -n -a Jasper`: `pgrep -fl Jasper.app` lists one process and its window comes forward. Run `jasper --safe-mode`: the running Jasper says to quit it first.
- [ ] Open a text file and a folder with Jasper from Finder's Open With, and drop each on the Dock icon, once with Jasper running and once with it quit: the file opens in the editor and the folder as a terminal tab, in one process.
- [ ] Stop the running Jasper with `kill -STOP <pid>` and launch it again: after about 10 s a dialog says Jasper is not responding and names the pid, and no second process starts; then `kill -CONT <pid>`.
- [ ] Quit Jasper with a window open and launch it again at once: the new launch opens a window once the old process has exited, and only one process remains.
- [ ] Restart from the Plugins page (Restart Now) and from Custom VM options: the old process exits and the new one opens a window.''', 1),
    ('''- [ ] Confirm `AF_UNIX` sockets work: with the default configuration, confirm a second `Jasper.exe` launch reveals a window and the second process exits rather than opening its own window.''',
     '''- [ ] Confirm `AF_UNIX` sockets work: with the default configuration, confirm a second `Jasper.exe` launch reveals a window and the second process exits rather than opening its own window.
- [ ] With Jasper running, run `Jasper.exe notes.txt` from a command prompt: the file opens in the running Jasper, whose window comes to the front, and the second process exits.''', 1),
    ('''(`Started with --background while a resident Jasper is running; exiting`)''',
     '''(`Started with --background while a Jasper owns this home; exiting`)''', 1)])
edit("docs/sdk-architecture.md", [
    ('''| `dev.jasper.app.restart` | Restart planning from the process's own command line, `ResidentControl`, and the Restart normally conversation | none |''',
     '''| `dev.jasper.app.restart` | Restart planning from the process's own command line and the Plugins page's restart banner state | none |''', 1),
    ('''  and never at shutdown, because standalone launches mean several processes may edit it.''', '''  and never at shutdown.''', 1),
    ('''Restart now replays the process's own command line (`ProcessHandle.info()`) without `--background`.
The replacement is spawned from the exit thread, after process cleanup has released the handoff
endpoint; if that cleanup did not finish, the replacement gets `--standalone`. Restart normally,
from safe mode, drops `--safe-mode`, and therefore first asks `ResidentControl` whether a resident
process holds the endpoint: if so it sends the authenticated `retire` request (protocol 1 with a
sixth field, so an ordinary open request is unchanged), waits up to 30 seconds for the socket to go
quiet, and otherwise offers only a `--standalone` launch. A handoff-capable replacement is never
started while the old resident holds the endpoint.''',
     '''Restart now replays the process's own command line (`ProcessHandle.info()`) without `--background`,
`--no-window` or the launch's paths, adding `--replacing <pid>`. The replacement is spawned from the exit thread, after
process cleanup, and waits up to 30 seconds for this process's instance lock. Restart normally, from safe mode, also
drops `--safe-mode`.''', 1),
    ('''Start-up rollback, a `--background` launch that cannot stay resident and the
benchmark exit through `quitNow()`, which does not ask; a resident asked to retire by another
launch goes through `quit()` and does. Since SDK 0.11.0''',
     '''Start-up rollback and the
benchmark exit through `quitNow()`, which does not ask. Since SDK 0.11.0''', 1),
    ('''Completely, a log-out or a retire request).''', '''Completely or a log-out).''', 1)])
edit("docs/plugin-authoring.md", [
    ('''`--plugin-dir` loads one plugin directory with consent pre-granted. Like `--safe-mode`
(no user plugins) and `--standalone`, it never hands off to or becomes a resident process.''',
     '''`--plugin-dir` loads one plugin directory with consent pre-granted. Like `--safe-mode`
(no user plugins), it applies only when Jasper starts: a launch while Jasper runs hands off and ignores it, so quit
Jasper first. Such a Jasper is never resident.''', 1)])
edit("packaging/cli/README.md", [
    ('''call reaches the JVM that is already running (the resident one on macOS and Windows), so it returns quickly.''',
     '''call reaches the Jasper that owns the home, so it returns quickly.''', 1),
    ('''arguments in the caller's working directory and environment, and exits.''',
     '''arguments in the caller's working directory and environment, and exits.

Other arguments go to Jasper as given, with the caller's working directory, and Jasper decides: a subcommand a plugin
declares runs; otherwise, when every argument names an existing file or folder, each file opens in the editor and each
folder as a terminal tab. No arguments bring Jasper forward.''', 1)])
PY
````

- [ ] **Step 2: The STATUS entry**

Insert this entry in `docs/STATUS.md` directly under the `# Jasper — Status and Handoff` heading and its blank line,
above the Enterprise presentation series entry. Fill in the date, the commit range (`git log --oneline` from Task 1's
commit to this task's), any deviation from this plan, the counts from Step 3, and for FIGURES the `settings-plugins:`
line Task 3 recorded before and after:

```markdown
**Workspace tabs, plan 1: the single instance — 2026-10-DD** (`claude/workspace-tabs`, worktree
`.claude/worktrees/workspace-tabs`, `FIRST..LAST`; [plan](superpowers/plans/2026-10-17-workspace-1-single-instance.md),
[spec](superpowers/specs/2026-10-16-jasper-workspace-tabs-design.md)). One Jasper process per home: a launch takes
`<home>/daemon/instance.lock` (`residency.InstanceLock`, `FileChannel.tryLock`, held for the life of the process; the
owner's process id is in `daemon/instance.pid`) before AWT, or hands its request to the owner and exits
(`bootstrap.SingleInstance`; spec 5.2's table is `LaunchAction`). The owner binds the handoff endpoint and the command
socket at once (`bootstrap.Instance`) and holds early requests (`EarlyRequests`) until `compose` hands it the
application; until then a handoff is answered on receipt, afterwards only once the EDT has taken it (within 1 s), and a
quitting owner takes none; a `jasper` call is answered when it has run. Handoff protocol 2 (`LaunchRequest`): RAISE or
OPEN_PATHS, the launch's working directory, its paths and the start-up flags it ignored; replies OK, TOKEN, UPDATED,
PROTOCOL. A launch retries an owner that does not answer for 10 s (taking the lock if the owner has gone), then says
"Jasper is already running but not responding. Quit it (process N) and try again." and exits 1 (`LaunchFailure`), never
starting a second instance. An owner answering UPDATED whose own jar changed restarts into the new build when no window
is open (the replacement opens the launch's paths), otherwise posts "Jasper was updated" with Restart; ignored start-up
flags get "Jasper is already running: quit it first to start with …". Restart (Plugins page, VM options, updates, the
look's Try Again) spawns the replacement with `--replacing <pid>`, which waits up to 30 s for the lock, or hands off once
that process has gone. A start that fails after the claim closes its endpoints and exits. `JasperApplication.open(List<Path>,
String)` routes RAISE, OPEN_PATHS, `jasper <path>…`, the macOS open-file event (`Desktop.setOpenFileHandler`) and the
reopen event through today's windows (`OpenPlan`); a file from outside opens in a plugin's editor or is revealed, never
edited by the desktop; a cold start's first window takes its first directory (`openFirstWindow`); plan 4 retargets
these to the main window. Command-line paths are positional `AppArguments`; the macOS plist gains
`CFBundleDocumentTypes` (text, source code and folders at Alternate), which `verifyPackage` checks. Removed:
`--standalone`, the private `commands-<pid>` sockets, `daemon/lock` and `<socket>.lock`, the handoff endpoint's
`token.pid`, the liveness probes, RETIRE and the STALE release, `RestartFlow`'s probe/retire/standalone states,
`RestartMode.STANDALONE`, `ResidentControl`, `endpointReleased`, the Plugins page's notice line (it was hidden in every
state the 8b and 9b comparisons render; `SettingsPagesMockTest.pluginsMatchesItsMock` and the `settings-plugins` pair
are unchanged: FIGURES). Rulings (plan, "Rulings"): (1) the lock lives in `residency`; (2) an I/O failure taking it ends
the launch with an error; (3) the 10 s retry retries the lock too; (4) early requests run once the application is
composed; (5) the not-responding error goes to stderr, the log and a system-look dialog and names the pid in
`instance.pid`; (6) UPDATED acts only when the owner's own jar changed; (7) both notices are INFO in `jasper.general`;
(8) restart keeps its order and always adds `--replacing`; (9) `RestartFlow` keeps two states and the Plugins page's
notice line goes; (10) a non-option argument is a path; (11) Jasper, not the client, tells a path from a subcommand, a
declared subcommand winning, and a call that starts with a path lists the missing ones; (12) OPEN_PATHS opens in the
last active window or a new one; (13) a cold start's first window takes the first directory and is still timed cold;
(14) the owner binds over whatever is at the socket path; (15) a home whose socket path is too long runs unreachable and
says so; (16) child JVMs run `java.home/bin/java` on two code sources; (17) a request over 1 MiB ends the launch with
exit 2; (18) the document types are Text (Editor) and Folder (Viewer); (19) `start` takes the home, so no test takes
the real lock; (20) a non-resident `--background` launch exits from `startDesktop` unless it already holds a request,
which it then carries out as an ordinary start; (21) raise and path calls answer once they have run; (22) a ready owner
answers a handoff once its EDT took it, within 1 s; (23) a quitting owner takes no handoff; (24) a failed start keeps
the lock until its process exits; (25) the lock is never opened twice in one process; (26) a file from outside is
revealed, never desktop-edited, without a plugin's editor; (27) a replacement whose predecessor has gone hands off. The
spec was amended while the plan was written (`c6d11f711`: no `LSMultipleInstancesProhibited`; `a92166bb2`: the EDT
take and the quitting owner); the plan follows the amended text. `./gradlew check --continue`: **N tests, 0 failures,
0 errors, M skipped**. Before the first launch of this build, quit any older resident Jasper completely (its menu bar
item's Quit Jasper Completely): it holds no instance lock and is unreachable from the new build, so it would keep
running beside it (a one-time transition, Ruling 14). A home on a filesystem without working file locks (some NFS or
SMB mounts) cannot start: the launch reports that it could not lock its home folder (Ruling 2). GUI checks for the user:
a second launch from Finder, Spotlight, `open -n -a Jasper` and `Jasper.exe notes.txt` (one process, the window comes
forward); `jasper --safe-mode` while Jasper runs (the notice); Open With and Dock drops of a text file and a folder,
with Jasper running and quit; `jasper notes.txt src` from a shell; a stopped owner (`kill -STOP`) gives the
not-responding dialog after 10 s; Quit and an immediate relaunch end with one process; Restart Now and the VM options
restart. Not merged, not pushed.
```

- [ ] **Step 3: The whole build**

Run:

```bash
python3 - <<'PY'
import pathlib
for root in ("jasper-app/src", "packaging/cli/src"):
    for p in pathlib.Path(root).rglob("*"):
        if p.suffix not in (".java", ".c", ".h"): continue
        text = p.read_text(encoding="utf-8")
        bad = sum(1 for c in text if 0xD800 <= ord(c) <= 0xDFFF or 0xE000 <= ord(c) <= 0xF8FF or (ord(c) < 0x20 and c not in "\n\t\r") or ord(c) == 0x7f)
        if bad: print(p, "bad chars:", bad)
PY
git grep -n -e '--standalone' -e 'ResidentControl' -e 'commands-<pid>' -e 'daemonLock' -e 'openOrRaise' -e 'endpointReleased' -e 'retire request' -e 'to retire' \
  -- ':!docs/superpowers' ':!docs/STATUS.md' ':!docs/design' ':!docs/benchmarks'
./gradlew check --continue
```

Expected: the hygiene script prints nothing; the `git grep` prints nothing; `check` passes. Read the totals from
`*/build/test-results/test/*.xml` (sum `tests`, `failures`, `errors`, `skipped`) and write them into the STATUS entry.

- [ ] **Step 4: Commit**

```bash
git add docs config.example.toml packaging/cli/README.md
git commit -F - <<'EOF'
Single instance: documentation and status

Configuration, architecture, maintenance, packaging, SDK and plugin
authoring docs and the jasper command's README describe one Jasper per
home: the instance lock, handoff protocol 2, paths on the command line
and through the jasper command, restart's replacement and the macOS
document types. A ./gradlew run session was never resident; the docs no
longer say it is. STATUS records the plan's rulings, the spec's
amendments, the check totals, the one-time transition and the GUI
checks.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

---

## Self-review

- **Spec coverage.** 5.1 the lock: Task 1 (and its life in Tasks 6-7). 5.2 the table: Task 2 (`LaunchAction`), Task 6
  (each row's behaviour), Task 7 (wired; residency "only for the default home launched without start-up flags").
  5.3 endpoints before AWT and the queue: Tasks 6-7; bind-time locks gone: Task 8; protocol 2 and replies: Task 5; the
  10 s retry and its message: Task 6; UPDATED: Task 5; restart with `--replacing`, `RestartFlow`'s path and
  `ResidentControl` gone: Task 3. 5.4 macOS handler and plist: Task 10; positional paths: Task 2; Windows foreground
  right before each handoff: Task 6; `jasper` and `jasper <path>…`: Tasks 4 and 9. 15: the spec's edge cases belong to
  later plans; this plan's are in Review Focus. 16: every item of the single-instance line is removed (Tasks 3, 5, 7, 8).
  17 single instance: child-JVM tests for the lock (Task 1) and the flow (Task 6), the table (Task 2), protocol 2
  (Task 5), UPDATED (Task 5), macOS file-open routing (Tasks 4, 10), CLI paths (Task 9). 18: plan 1's scope; plan 4
  retargets `JasperApplication.open`.
- **Type consistency.** `InstanceLock.tryAcquire(Path)` and `ownerPid(Path)`, `LaunchRequest.of(Path, long, Path,
  List<Path>, List<String>)`, `HandoffSocket.handOff(Path, Path, LaunchRequest)`, `HandoffSocket.answering(
  BiPredicate<LaunchRequest, Boolean>, Path, long)`, `pathFits(Path)`, `tooLong(Path)`, `EarlyRequests.submit(Runnable,
  Runnable)` returning `Outcome`, `Instance.serve(BiConsumer<LaunchRequest, Boolean>, BooleanSupplier, Executor,
  CommandDispatcher)`, `Instance.declineIfIdle()`, `ApplicationBootstrap.handoffHandler(JasperApplication)` returning
  `BiConsumer<LaunchRequest, Boolean>` (run on the EDT), `JasperApplication.open(List<Path>, String)`,
  `openFirstWindow(List<Path>)`, `openRequestedFile(Path, UUID)`, `acceptsRequests()`, `handedOff(List<Path>,
  List<String>, boolean)`, `restart(RestartMode, List<Path>)`, `commandPaths` and `missingPaths(Path, List<String>,
  Predicate<String>)`, `ConfigEditor.revealOnly(Path)` and `RestartCommand.replacing(List<String>, long, List<Path>)` are
  spelled the same in every task that names them.
- **Spec amendments.** 5.3's EDT take and quitting owner (`a92166bb2`): Task 6's `Instance` (Rulings 22, 23) and Task 7's
  `serve` call; 5.4 without `LSMultipleInstancesProhibited` (`c6d11f711`): Task 10 and the docs in Task 11.
- **No retire left.** RETIRE goes in Task 3 (with the SDK's `Windows.onQuitting` Javadoc) and Task 5; the documents
  that describe it (`sdk-architecture.md`'s restart paragraph, quit-guard sentence and background-work list,
  `app-architecture.md`'s quit list and reopen paragraph, `configuration.md`'s Updates paragraph) change in Task 11.
- **Anchors.** Every edit script was replayed in order against a copy of the branch on `main` `4ed94fb78` with
  the whole-file writes of the tasks before it; each anchor matched its stated count.
- **Proportion.** Most of the plan's length is complete test and source code, as AGENTS.md asks; whole files are given
  where a file changes throughout (`LaunchRequest`, `HandoffSocket`, `AppArguments`, the restart package) and anchored
  edits elsewhere.
