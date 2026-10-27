# Diagnostics

Jasper keeps a bounded log for troubleshooting start-up, shells, plugins and desktop integration, an in-memory list of
this session's failures in the [Errors tab](notifications.md#the-errors-tab), and a few start-up safeguards. The
log is not a terminal transcript: Jasper records no terminal input or output.

## Logs

Logs live in the `logs` folder of [Jasper home](getting-started.md#jasper-home), the folder that holds `config.toml`:

| Platform | Folder |
|---|---|
| macOS | `~/.config/jasper/logs` |
| Linux | `$XDG_CONFIG_HOME/jasper/logs`, or `~/.config/jasper/logs` when `XDG_CONFIG_HOME` is unset, blank, invalid or relative |
| Windows | `%APPDATA%\jasper\logs`, or `AppData\Roaming\jasper\logs` in your user folder when `APPDATA` is unset, blank, invalid or relative |

The `jasper.home` system property, or else the `JASPER_HOME` environment variable, moves Jasper home and its logs;
`--config` does not. `./gradlew :jasper-app:run` logs to `jasper-app/build/dev-home/logs`. **Open Log Folder** in the
Errors tab (**Notifications: Show Errors** in the command palette) shows the folder; when that fails, a notification
reads "Could not open the log folder".

The files are named `jasper-<u>-<g>.log`, UTF-8; while a Jasper writes them, a `jasper-<u>-0.log.lck` lock file sits
beside them. `<u>` is 0: only the Jasper that owns the home writes its logs. `<g>` is the generation: Jasper appends
to generation 0 across launches and, once it reaches about 1 MiB, renames it to 1 (and 1 to 2) and starts a new 0.
Three generations are kept, so one Jasper's log never exceeds about 3 MiB. A launch that ends early writes no log: one
that handed its request to the Jasper that owns the home, a `--background` or `--no-window` launch that found that
Jasper running, one that printed `--help` or rejected its arguments, and one that could neither hand off nor start,
which prints why to standard error ([one Jasper per home](command-line.md#one-jasper-per-home)).

Only records from Jasper's own loggers are written: names under `dev.jasper`, which include the terminal library,
Buddy and each plugin's logger, `dev.jasper.plugins.<plugin id>` (what a plugin's `PluginContext.log()` returns).
Records at INFO and above reach the file. A normal launch logs one line per plugin with its state, "Plugins started in
… ms", the [start-up line](#start-up-timing) and, on quitting, "Shutdown finished in … ms".

Each record starts with a line holding an ISO-8601 UTC timestamp, the level (SEVERE, WARNING or INFO), the logger's
name and the message, followed, for each exception in the cause chain (at most eight), by its class name and its stack
frames, at most 24 frames in all. A message keeps at most 512 characters, with line breaks and other control
characters turned into spaces; a record keeps at most 8 KiB.

Records are queued, so logging never waits for the disk; one thread, `jasper-log-writer`, writes them. When the queue of
256 records is full, Jasper drops records and later writes one line, "N log records dropped". Quitting writes what is
queued for at most two seconds and releases the lock. When the log folder or file cannot be created, Jasper prints
"Jasper diagnostics are unavailable." to standard error and runs without a log file; the first failed write prints
"Jasper could not write diagnostics."

### What a log contains

Of an exception, the log writes only its class name and stack frames, never its message. A record's own message is the
text its caller wrote, which can name a file or folder, a plugin, a host or an address, or repeat an error's text. Its
parameters are never written: a record logged as `"hosts.toml: {0}"` keeps `{0}` as it is, so put any value you want
in the log into the message text. A plugin's records say whatever the plugin writes. Read a log before you share it.

An uncaught exception is logged at SEVERE as "Unexpected application failure", and a plugin's failure as a WARNING
"Plugin <plugin id> failed in <operation>"; both are also recorded as incidents. An incident's full message and stack
trace stay in memory, in the Errors tab, whose **Copy** puts a report on the clipboard for a bug report.

## Memory

**Show memory indicator** (`window.memory_indicator`, off by default) in Settings › Appearance & Behavior › Window adds
the heap in use and its limit to the status bar while the status bar is shown, for example `312 of 2048M` (MiB). Its
tooltip reads "Heap: … MiB used, … MiB committed, … MiB maximum. Non-heap: … MiB used. Click to run garbage
collection." It refreshes every three seconds once start-up has settled. A click runs **Run Garbage Collection**, which
the command palette also offers; it has no default shortcut.

After start-up settles, Jasper watches the old generation of the heap. Each time a collection leaves it at 95% of its
maximum or more counts once, at most once every 20 seconds; more than five counts within 15 minutes, or an
`OutOfMemoryError`, posts **Jasper is running low on memory** in the Memory & VM options group: "Raise the maximum heap
size so Jasper keeps up; the new limit applies after a restart." Its **Adjust Memory Settings…** opens Settings ›
Appearance & Behavior › System Settings at **Maximum heap size**, with the running limit plus 512 MiB filled in (left as
saved when a larger limit is already saved). The warning shows once, and again only after you change the heap limit.

Help › **Change Memory Settings…** opens the same field. The heap limit and other JVM options live in
[custom VM options](configuration.md#custom-vm-options).

## Start-up timing

A launch logs one INFO line from `dev.jasper.app.startup` once start-up settles: for a window launch when its first
shell output is painted and every plugin has started, for a `--background` launch, or a `--no-window` launch of a
resident Jasper, when its warm-up has finished and every plugin has started (that line reads
`Startup (background, …)`), and otherwise 10 seconds after the process started. A launch that hands off to an already
running Jasper, or quits before settling, logs none. For example:

    Startup (cold, AOT cache used, CDS on, look=default): main entered 71 ms, config loaded 90 ms, … output painted 980 ms, plugins bound 512 ms | application 410 ms, … plugins 280 ms

Times before the `|` are milliseconds since the operating system started the process; after it, each phase is how long
that piece of work took. Phases overlap (`application` includes `plugins`), so do not add them up.

- `AOT cache used` means the JVM mapped the [start-up cache](#start-up-cache-packaged-macos-app), `AOT cache none`
  that this Jasper has none.
- `AOT cache stale` means the cache no longer matches its jars (for example after a copy that did not keep
  modification times) and costs more than having none.
- `AOT cache skipped: <reason>` means Jasper's launcher started the JVM without it: `launch-failed` (Java failed to
  start with it at this launch), `remembered` (at an earlier one) or `environment` (`JASPER_AOT_CACHE_SKIP` is set).
- `look=` names the look: `default`, or the id of the plugin that provides it.

Each window a running Jasper opens logs `Window (<trigger>): shown …, first paint …, output painted … after the
request`. `<trigger>` is `cold` (a launch's first window), `menu`, `reopen` (a Dock click, Spotlight or `open -a` on
macOS), `handoff` (a second launch or a `jasper` call), `open-file` (a macOS open-file event), `notification` (a click
on a system notification), `plugin` (a plugin's view opening with no window open) or `restore` (a saved window coming
back with the first). A window opened after the last one had closed adds `; no window for … before`, and on
Windows also `… page faults until shown, working set … to … MB`. With `-Djasper.startupTrace=<file>` the same data is
also written as JSON; the start-up benchmark uses it.

## Start-up recovery

### When Java does not start

The installed app, except a Windows [user installation](packaging.md#user-installation-without-a-compiler), starts
through Jasper's own launcher, which also starts `jasper-watch` beside it. When Java exits
before its virtual machine has started, the watcher starts Jasper again, leaving out one thing at a time:

1. the start-up cache, when it was in use;
2. then your [custom VM options](configuration.md#custom-vm-options), when they applied;
3. when Java still does not start, the watcher runs it once more to see what it reports (for up to 10 seconds) and
   shows **Jasper could not start**: "Jasper could not start, even without its start-up cache and your custom VM
   options. Reinstalling Jasper may help.", followed by the last 1,500 characters Java printed. macOS shows an alert,
   Windows a message box, and Linux writes it to standard error.

A Jasper started without your options posts **Jasper started without your custom VM options** in the Memory & VM
options group, with **Edit Custom VM Options…**. Setting `JASPER_VM_OPTIONS_SKIP=1` or `JASPER_AOT_CACHE_SKIP=1` in the
environment starts Jasper without the options or without the cache. `./gradlew :jasper-app:run` does not use the
launcher.

### Start-up cache (packaged macOS app)

The packaged macOS app starts with an AOT cache (`Contents/app/jasper.aot`) trained when it was built, and the start-up
line in the log says whether the JVM used it. If it reports the cache as stale, a copy changed the application's file
modification times (for example `cp -R`): Jasper works, but starts slower than an app without a cache, because the JVM
then also skips the runtime's default CDS archive. Reinstall from the DMG with Finder. A stale or refused cache can
also come from JVM options in the environment: `JAVA_TOOL_OPTIONS` or `JDK_JAVA_OPTIONS` with a heap above about
32 GB, which turns compressed object pointers off, or a different garbage collector. The default heap is safe at any
RAM size, because the JVM caps it to keep compressed pointers. See
[keeping the cache valid](packaging.md#keeping-the-cache-valid).

After a start the watcher made without the cache, Jasper checks the cache once start-up has settled. If the cache fails
the check, Jasper writes `jasper-aot.failed` in Jasper home and posts **Jasper started without its start-up cache** in
the Memory & VM options group. The launcher then starts without the cache while that file matches it, until a new
Jasper build replaces the cache; delete the file to try the cache again.

### When a look does not start

When the last start with a plugin's look, such as [Enterprise](enterprise.md), did not finish, or the look failed, the
next start uses the standard look and posts **Jasper started with its standard look** in the Plugin problems group,
with **Try Again**. [When a look does not start](looks.md#when-a-look-does-not-start) describes the record in
`presentation-attempt.toml`. [Safe mode](getting-started.md#safe-mode) starts without look plugins.

## Cygwin on Windows

Installing the [Cygwin environment](terminal.md#cygwin-bash) and managing its packages write their own records in
`%LOCALAPPDATA%\Jasper\cygwin`, not in Jasper home. In its `logs` folder:

| File | Written by |
|---|---|
| `provision-result.json` | Each installation (the setup wizard, `installWindowsUser` or `provision-cygwin.cmd`): its outcome, `CREATED`, `REUSED`, `FAILED` or `RESTART_REQUIRED`, and its message |
| `setup-<milliseconds>.log` | Cygwin Setup during the first installation |
| `bash.exe.log`, `git.exe.log`, `ssh.exe.log`, `curl.exe.log` | The checks that the installed tools start |

Each **Manage Cygwin packages…** run keeps a folder in `operations` with Setup's output (`setup.log`), the
supervisor's (`supervisor.log`) and the result (`result.json`). The installation helper also prints its message and
exits with status 0 when it succeeds, 2 when Windows must restart before Cygwin Setup can finish, and 1 otherwise.
Jasper's own log records a detection that fails as "Cygwin detection failed".

| Message | Meaning |
|---|---|
| `Cygwin Setup requires a Windows restart; initialization remains incomplete` | Restart Windows and install again; the installation goes on with the seed it kept. |
| `Insufficient space for Cygwin: requires N bytes` | The drive holding `%LOCALAPPDATA%` needs that much free space. |
| `Foreign Cygwin environment: <folder>` | Jasper did not create that folder for this Windows account, so it neither uses nor changes it; move it away for Jasper to install its own. |
| `Cygwin Bash or its package database is missing; use Manage Cygwin packages` | The environment is damaged; repair it with **Manage Cygwin packages…**. |
| `Close Jasper Cygwin sessions before managing packages`, `Close processes using this Cygwin environment: …` | Close them and choose **Manage Cygwin packages…** again. |
| `Cannot verify whether these processes use Cygwin; close them before package changes: …` | Windows did not let Jasper inspect those processes; close them and try again. |
| The path of `owner.json` in `%LOCALAPPDATA%\Jasper\cygwin`, alone | **Manage Cygwin packages…** found no Cygwin environment; install it first with the setup wizard, `installWindowsUser` or the ZIP's `provision-cygwin.cmd`. |
| `Initialization was not completed; provision Cygwin through the Jasper installer first` | **Manage Cygwin packages…** found an installation that did not finish; install again, which goes on with the seed it kept. |
| `Cygwin management helper is unavailable; use a packaged Jasper installation` | **Manage Cygwin packages…** needs a packaged Jasper with `app\cygwin-tool.jar`. |
| `Cygwin operation is already active` | An installation or an earlier **Manage Cygwin packages…** run is using the environment; wait for it to finish. |
| `Cygwin management was interrupted; reopen Manage Cygwin packages before starting Bash`, `Cygwin manager is starting or was interrupted; reopen Manage Cygwin packages` | A Setup run ended without a result; Cygwin Bash starts again once **Manage Cygwin packages…** has been opened. |
| `Cygwin bootstrap scripts are unavailable` | Shown in a Cygwin Bash pane when Jasper could not write its shell integration folder in this run. |
| `Jasper PTY relay: <operation> (Windows error N)` | Printed in a Cygwin Bash pane when the relay fails; it ends the pane's shell and every process started in the pane. |
