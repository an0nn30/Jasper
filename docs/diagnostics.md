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
