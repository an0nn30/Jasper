# Linux desktop colors helpers

`src/protocol.h` carries C11 values shared by the independent desktop helpers. It has no toolkit types.
`dc_read_command` accepts only the fixed version 1 `sample` and `ping` schemas and publishes a command only on
success. `dc_write_palette` validates and buffers a complete palette before writing its newline-terminated JSON;
invalid optional color pairs are omitted. Both functions return zero on success and minus one on invalid input
or an I/O error.

Messages use UTF-8 JSON lines, at most 64 KiB per line. The Java decoder validates the whole flat scalar object
before interpreting fields, with at most 32 keys and 1024 Unicode characters in `reason`. Duplicate keys,
nesting, malformed UTF-8, trailing content and unknown versions or kinds reject the envelope. Unknown scalar
keys are ignored. A malformed color or missing pair half removes that pair; portal accent and contrast are
independently optional. Generations are nonnegative signed 64-bit integers. RGBA values are comma-separated
dot-decimal strings with finite components in `[0,1]`. Native output uses a thread-local C numeric locale and
17 significant digits, preserving the caller's locale.

Run `./gradlew :jasper-app:test --tests '*DesktopProtocolTest'` on any host and
`./gradlew :jasper-app:testDesktopProtocol` on Linux. Linux `check` includes the native protocol suite. The
native tasks skip on other hosts without discovering native dependencies. Native tests need a C11 compiler
and an installed comma-decimal UTF-8 locale (`de_DE`, `fr_FR` or `en_DK`); a private locale can be supplied with
`LOCPATH`. `test/vectors.jsonl` is shared by Java and native tests; each adjacent `vectors.tsv` row contains its
id, expected Java message result, expected native command result, generation and appearance. Commands are
intentionally rejected by the Java message decoder, and messages by the native command reader.

The distribution ABI baseline is glibc 2.35 and GLib 2.72 (Ubuntu 22.04). The protocol target uses only C/POSIX.

`src/portal.c` builds `jasper-desktop-portal --protocol=1` against GIO (GLib 2.72+). It watches the
session-bus appearance settings from `org.freedesktop.portal.Desktop`, subscribing to changes before its
two-second asynchronous `ReadAll`. Each owner receives an increasing generation; retired replies are ignored,
and a baseline only fills keys unchanged since that read began. Appearance, opaque accent and contrast are
validated independently, and every event publishes a complete snapshot. Missing services report unavailable;
registration and replacement are observed live. The helper answers version 1 pings and stops on stdin EOF.
`dc_portal_start(GDBusConnection *, FILE *)` and `dc_portal_stop()` own one observer on the calling thread's
GLib main context; stop cancels pending work, and its callbacks cannot write afterward. `dc_write_portal` and
`dc_write_pong` share the protocol's bounded envelope writers.

Run `./gradlew :jasper-app:test --tests '*DesktopPortalTest'` on any host and
`./gradlew :jasper-app:testDesktopPortal` on Linux. `compileDesktopPortal` builds the helper; Linux `check`
includes the fake-portal suite under `dbus-run-session`, with a private test bus and no visible windows.
The native targets require GIO development headers, `pkg-config` and `dbus-run-session`. Tests cover signal/read
ordering, owner replacement, partial and malformed preferences, late registration, the response timeout,
pending-read shutdown, and helper ping/EOF behavior.
