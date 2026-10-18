# Linux desktop colors protocol

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

The distribution ABI baseline is glibc 2.35 and GLib 2.72 (Ubuntu 22.04). This protocol target uses only C/POSIX
and has no GLib dependency. Runtime helpers must retain that baseline when adding their Linux libraries.
