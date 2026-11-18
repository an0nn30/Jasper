# Optional Linux desktop integrations

Approved by the user in chat: automatic detection, explicit disable options, warnings for missing dependencies, and strict CI checks.

Jasper must build and run on Linux without requiring GTK or Qt/KDE integration SDKs. Detect each integration independently by default. Explicit off omits it even when installed; explicit required fails clearly when unavailable. Missing optional prerequisites warn and skip affected features/tests. Real compiler errors still fail.

Stage/package exactly the selected helpers and remove stale outputs after feature changes. Preserve Jasper's built-in theme fallback. Strict CI requires all integrations and release ABI floors; ordinary local builds use the host ABI. GLib/GIO and core X11/JBR requirements are distinct from optional toolkit SDKs and remain documented.

Approaches: automatic detection was approved because users can build without toolkit setup; default-off discards available integration; mandatory SDKs contradict the request. Test missing SDKs, explicit disable, strict failure, compiler failure, and stale-output transitions. Build and launch on Gentoo ARM64 for user acceptance before ebuild work.
