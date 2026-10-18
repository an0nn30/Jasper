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

`src/gtk3.c` and `src/gtk4.c` build separate `jasper-desktop-gtk3 --protocol=1` and
`jasper-desktop-gtk4 --protocol=1` processes. `gtk_api.c` opens GTK with `RTLD_LOCAL`, checks GTK 3.24 or
GTK 4.10 at runtime, and resolves optional libadwaita. Only GLib/GIO, dl and the C runtime are linked.
`dc_sample_gtk3` / `dc_sample_gtk4` return complete paired observations, with the request generation and
observed polarity. Process-local forced appearances are checked against the opaque WINDOW pair and its
4.5:1 contrast. Missing display/library and unsupported variants produce palette capability envelopes with a
reason and absent WINDOW, and the process continues answering pings. A failed display initialization remains
unavailable until process restart. The parent owns recovery. Roots are replaced and destroyed without mapping.

GTK 3 uses paired theme background/foreground, base/text and selected background/foreground names; secondary
surfaces use Window. GTK 4 before 4.16 uses paired underscore names, followed by GTK 3-style base-pair names.
GTK 4.16+ creates dedicated labels beneath an unmapped root, assigning each documented variable to `color`
with magenta and cyan fallbacks. An idle follows invalidation before foreground lookup. Matching results identify
resolved variables, preserving real magenta/cyan. The instrumentation provider outranks user label colors while
variables inherit the theme/user cascade. Missing modern libadwaita variables remain absent; plain GTK 4 can
supply complete named pairs when its variables are absent. Theme/dark-preference and
optional StyleManager notifications coalesce into an idle resample of the last requested appearance/generation;
a 30-second refresh catches CSS changes without notifications. Requests arriving during a notification/timer
sample are queued until its extraction and publication finish, preserving widget lifetimes and generations.
EOF destroys roots and ends the helper.

Runtime symbols (all are public; GTK-major alternatives are resolved only in that process):

| Library | Resolved symbols | Minimum symbol version |
|---|---|---|
| GTK 3 / 4 | `gtk_get_major_version`, `gtk_get_minor_version`, `gtk_get_micro_version`, `gtk_init_check`, `gtk_settings_get_default`, `gtk_window_new`, `gtk_label_new`, `gtk_box_new`, `gtk_widget_get_mapped`, `gtk_widget_get_style_context`, `gtk_style_context_lookup_color`, `gtk_css_provider_new`, `gtk_css_provider_load_from_data`, `gdk_display_get_default` | GTK 3.0 / 4.0 |
| GTK 3 | `gtk_container_add`, `gtk_widget_destroy`, `gtk_style_context_add_provider_for_screen`, `gtk_style_context_remove_provider_for_screen` | GTK 3.0 |
| GTK 4 | `gtk_window_set_child`, `gtk_box_append`, `gtk_window_destroy`, `gtk_widget_add_css_class`, `gtk_style_context_add_provider_for_display`, `gtk_style_context_remove_provider_for_display` | GTK 4.0 |
| GTK 4 | `gtk_widget_get_color` | GTK 4.10 |
| libadwaita (optional) | `adw_init`, `adw_style_manager_get_default`, `adw_style_manager_set_color_scheme`, `adw_get_major_version`, `adw_get_minor_version`, `adw_get_micro_version` | libadwaita 1.0 |

CSS-variable capability requires GTK 4.16. Library versions identify libadwaita plus GTK when present, and GTK
otherwise. GTK 3 RGBA uses doubles; GTK 4 uses floats. `verifyDesktopGtkAbi` checks layouts and public function
signatures against installed GTK/libadwaita headers and verifies that toolkit libraries are absent from ELF
`DT_NEEDED`. Test-only root enumeration additionally resolves `gtk_window_list_toplevels` and
`gdk_screen_get_default` on GTK 3.0, or `gtk_window_get_toplevels` on GTK 4.0.

Run `./gradlew :jasper-app:test --tests '*DesktopGtkProtocolTest'` on any host and
`./gradlew :jasper-app:testDesktopGtk` on Linux. The latter prints toolkit versions and TAP test counts,
compiles ABI checks, and uses `xvfb-run -a` and a private D-Bus configuration without service activation.
It requires GTK 3/4 and libadwaita development headers, GIO development headers, `pkg-config`, Xvfb, xauth and
D-Bus. Production compilation needs only GIO headers. Fixture CSS is loaded into the real toolkit in-process;
subprocess fault injection covers absent libraries and unsupported runtimes. Ubuntu 24.04 CI covers named colors;
a digest-pinned Ubuntu 26.04 container requires GTK 4.16+ and covers overridden variables. Linux `check` includes
this gate; other hosts skip it without discovering native dependencies.
