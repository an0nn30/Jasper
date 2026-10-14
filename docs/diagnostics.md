# Application diagnostics

Jasper writes diagnostics for application and terminal failures. These files are intended for troubleshooting startup,
shell, emulator, clipboard and desktop integration problems. They are not terminal transcripts: Jasper does not record
terminal input or output.

Logs are stored beside Jasper's other application data:

- macOS: `~/.config/jasper/logs`
- Linux: `$XDG_CONFIG_HOME/jasper/logs`, or `~/.config/jasper/logs` when `XDG_CONFIG_HOME` is unset, blank, invalid or relative
- Windows: `%APPDATA%\jasper\logs`, or the user's `AppData\Roaming\jasper\logs` directory when `APPDATA` is unavailable, invalid or relative

Each running Jasper process obtains its own numbered log files and locks. It keeps three UTF-8 files of approximately
1 MiB each. Older records rotate out automatically. Diagnostics are queued so reporting from the Swing event thread
does not wait for disk I/O; if the bounded queue fills, Jasper drops records and writes one count summary when the writer
catches up.

Records contain a timestamp, severity, fixed operation description, exception type and a bounded stack summary. They do
not contain exception messages or parameters. Jasper-owned diagnostic call sites do not log terminal content, clipboard
content, URLs, shell arguments, environment values, raw configuration content or user configuration paths.

Failure to create or write the log files does not prevent startup. Jasper prints one short fallback diagnostic and
continues without file logging. Normal shutdown drains accepted records for at most two seconds and releases the process
file lock.

## Start-up timing

A launch logs one INFO line from `dev.jasper.app.startup` once start-up settles: for a window launch when its first
shell output is painted and every plugin has started, for a `--background` launch when its warm-up has finished and
every plugin has started (that line reads `Startup (background, …)`), and otherwise 10 seconds after the process
started. A launch that hands off to an already running Jasper, or quits before settling, logs none. For example:

    Startup (cold, AOT cache used, CDS on): main entered 71 ms, config loaded 90 ms, … output painted 512 ms, plugins bound 980 ms | application 210 ms, plugins 280 ms

Times before the `|` are milliseconds since the operating system started the process; after it, each phase is how long
that piece of work took. Phases overlap (`application` includes `plugins`), so do not add them up. `AOT cache stale`
means the cache in the package no longer matches its jars (for example after a copy that did not keep modification
times) and costs more than having none. `AOT cache skipped: <reason>` means Jasper's launcher started the JVM without
it: `launch-failed` (it made Java fail at this launch), `remembered` (it did at an earlier one) or `environment`
(`JASPER_AOT_CACHE_SKIP` is set). Each window a running Jasper opens logs `Window (<trigger>): shown …, first paint …,
output painted … after the request`. With `-Djasper.startupTrace=<file>` the same data is also written as JSON; the
start-up benchmark uses it.

## Start-up cache (packaged macOS app)

The packaged macOS app starts with an AOT cache (`Contents/app/jasper.aot`) trained when it was built, and the start-up
line in the log says whether the JVM used it. If it reports the cache as stale, a copy changed the application's file
modification times (for example `cp -R`): Jasper works, but starts slower than an app without a cache, because the JVM
then also skips the runtime's default CDS archive. Reinstall from the DMG with Finder. A stale or refused cache can
also come from JVM options in the environment, not only from a copy that lost modification times: `JAVA_TOOL_OPTIONS`
or `JDK_JAVA_OPTIONS` with a heap above about 32 GB, which turns compressed object pointers off, or a different garbage
collector. The default heap is safe at any RAM size, because the JVM caps it to keep compressed pointers. See
[keeping the cache valid](packaging.md#keeping-the-cache-valid).

If the cache makes Java fail at launch, Jasper starts again without it, says so once in a **Memory & VM options**
notification and writes `jasper-aot.failed` in Jasper's home (next to `config.toml`). Jasper starts without the cache
while that file matches it, until a new Jasper build replaces the cache; delete the file to try the cache again.
Setting `JASPER_AOT_CACHE_SKIP=1` in the environment also starts Jasper without the cache.
