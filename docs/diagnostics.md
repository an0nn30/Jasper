# Diagnostics

Jasper keeps a bounded log for troubleshooting start-up, shells, plugins and desktop integration, an in-memory list of
this session's failures in the [Errors window](notifications.md#the-errors-window), and a few start-up safeguards. The
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
Errors window (**Notifications: Show Errors** in the command palette) shows the folder; when that fails, a notification
reads "Could not open the log folder".

The files are named `jasper-<u>-<g>.log`, UTF-8; while a Jasper writes them, a `jasper-<u>-0.log.lck` lock file sits
beside them. `<u>` is 0 for the first Jasper of that home running at the time, and another number for each Jasper
running beside it. `<g>` is the generation: Jasper appends to generation 0 across launches and, once it reaches about
1 MiB, renames it to 1 (and 1 to 2) and starts a new 0. Three generations are kept, so one Jasper's log never exceeds
about 3 MiB. A launch that ends early writes no log: one that handed its request to a running Jasper, a `--background`
launch that found a resident Jasper, a `--no-window` launch that found a Jasper serving the `jasper` command, and one
that printed `--help` or rejected its arguments.

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
trace stay in memory, in the Errors window, whose **Copy** puts a report on the clipboard for a bug report.

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
request`. `<trigger>` is `cold`, `menu`, `reopen` or `handoff`. A window opened after the last one had closed adds
`; no window for … before`, and on Windows also `… page faults until shown, working set … to … MB`. With
`-Djasper.startupTrace=<file>` the same data is also written as JSON; the start-up benchmark uses it.

## Start-up recovery

### When Java does not start

The installed app starts through Jasper's own launcher, which also starts `jasper-watch` beside it. When Java exits
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
