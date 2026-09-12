# Application diagnostics

Moray writes diagnostics for application and terminal failures. These files are intended for troubleshooting startup,
shell, emulator, clipboard and desktop integration problems. They are not terminal transcripts: Moray does not record
terminal input or output.

Logs are stored beside Moray's other application data:

- macOS: `~/.config/moray/logs`
- Linux: `$XDG_CONFIG_HOME/moray/logs`, or `~/.config/moray/logs` when `XDG_CONFIG_HOME` is unset
- Windows: `%APPDATA%\moray\logs`, or the user's `AppData\Roaming\moray\logs` directory when `APPDATA` is unavailable

Each running Moray process obtains its own numbered log files and locks. It keeps three UTF-8 files of approximately
1 MiB each. Older records rotate out automatically. Diagnostics are queued so reporting from the Swing event thread
does not wait for disk I/O; if the bounded queue fills, Moray drops records and writes one count summary when the writer
catches up.

Records contain a timestamp, severity, fixed operation description, exception type and a bounded stack summary. They do
not contain exception messages or parameters. Moray-owned diagnostic call sites do not log terminal content, clipboard
content, URLs, shell arguments, environment values, raw configuration content or user configuration paths.

Failure to create or write the log files does not prevent startup. Moray prints one short fallback diagnostic and
continues without file logging. Normal shutdown drains accepted records for at most two seconds and releases the process
file lock.
