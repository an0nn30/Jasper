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

Every launch logs one INFO line from `dev.jasper.app.startup` once start-up settles (the first shell output is painted
and every plugin has started, or 10 seconds after the process started), for example:

    Startup (cold, AOT cache used, CDS on): main entered 71 ms, config loaded 90 ms, … output painted 512 ms, plugins bound 980 ms | application 210 ms, plugins 280 ms

Times are milliseconds since the operating system started the process. `AOT cache stale` means the cache in the
package no longer matches its jars (for example after a copy that did not keep modification times) and costs more than
having none. Each window a running Jasper opens logs `Window (<trigger>): shown …, first paint …, output painted …
after the request`. With `-Djasper.startupTrace=<file>` the same data is also written as JSON; the start-up benchmark
uses it.
