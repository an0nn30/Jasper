# Linux X11 tray fallback amendment

**Status: APPROVED by the user — 2026-10-28.** This scoped amendment replaces the public-AWT fallback after [Plan 2 Task 2 failed its native gate](../plans/2026-10-27-linux-residency-2-verification.md#task-2-native-result--2026-10-28-public-awt-gate-failed). The user approved the concrete XCB/JNA architecture and continuing the existing per-task workflow. [Revised Plan 2](../plans/2026-10-27-linux-residency-2-tray.md) specifies remaining Tasks 6–10. Implementation remains gated on native cancellation and characterization; approval is not evidence those gates passed. Authorized isolated Fedora probes and the CI-only x86_64/Ubuntu 22.04 split remain unchanged.

## Reason and boundary

On pinned JBR 25.0.4.1-b635.75, LXDE panel destruction queues `XTrayIconPeer` cleanup that calls
`SystemTray.getSystemTray().remove` after support disappears. It throws before the public availability callback;
Jasper's normal unexpected-exception handler produces an incident. Cached-owner removal in public callbacks did
not prevent it. Normal 1x/2x drawing, actions, late hosts and live applet re-add mostly passed, but do not override
that failure. The linked verification preserves the exact exception, source provenance and excluded observations.

Replace only Linux's proposed public-AWT legacy adapter and its production wiring. Preserve Plan 1 residency,
autostart and session lifetime; Plan 2 Tasks 1, 3, 4 and 5's dependencies, D-Bus protocol, shared menu, policy and
controller contracts; and Windows/macOS tray and notification behavior. No SDK change is intended. Linux never
constructs `SystemTray`/`TrayIcon` peers through this fallback. No exception suppression, JDK internals, GTK/Qt,
helper service, root installation, extra process or alternate residency mechanism is introduced.

## Approaches

| Approach | Assessment |
| --- | --- |
| Public AWT plus recovery callbacks | Rejected by recorded native evidence. Catching Jasper's own add/remove calls cannot catch the earlier JBR event. |
| Xlib through existing JNA | Familiar API, but protocol error handling is process-wide and default fatal I/O handling can terminate the process. Sharing or replacing JBR's handlers is unsuitable; an owned display alone does not isolate them. |
| **Owned XCB connection through existing JNA** | Approved. Scoped replies/errors and connection failure state fit the controller. Costs are explicit native bindings, image/XEmbed work, cancellable setup/output and native acceptance. |

Xlib's newer per-display I/O-exit hook does not remove the protocol-handler integration problem; this design does
not rely on it. See the [upstream Xlib manual distributed by Debian](https://manpages.debian.org/bookworm/libx11-doc/XSetIOErrorHandler.3.en.html).
XCB exposes connection errors and polling, but `xcb_flush` blocks: selecting XCB alone is not proof of bounded I/O.
The [XCB core API](https://xcb.freedesktop.org/manual/group__XCB__Core__API.html) and
[public reply-poll declarations](https://xcb.freedesktop.org/manual/xcbext_8h_source.html) establish the API surface.

## Parts and contracts

All new classes are package-private in `dev.jasper.app.platform`; native pointers and XIDs stay there. Use concrete
classes and the existing function-record test seams, not another backend interface.

| Owner | Responsibility and contract |
| --- | --- |
| `ResidentTray` | Retain Task 6's injected Linux installer and headless routing design. Return idempotent cleanup even with no icon. Linux balloons continue through `LinuxNotifications`. |
| `LinuxResidentTray` | Retain `Modern(publish, withdraw, close, reconnect)`, `Legacy(install, remove, close)`, `hostChanged`, `legacyChanged(boolean)`, bus observations and action generations. Modern withdrawal stays `LinuxStatusNotifier.withdrawAndAwait`. |
| `LinuxLegacyTray` | Own one XCB connection, one native icon, host identity, adapter epoch, bounded command slot and native event worker. Constructor captures launch environment and availability callback without native loading; `start()` admits discovery, `install(IntConsumer)` returns actual embedding success, `removeAndAwait()` confirms withdrawal, `close()` invalidates immediately and requests bounded cleanup. |
| `LinuxTrayXcb` | Lazy JNA bindings and ABI layouts for libxcb, libxcb-render, required libc calls and authority handling. It owns no application actions and never borrows JBR's display, event queue or native windows. |
| `LinuxTrayPopup` | EDT-owned, short-lived public Swing menu. Owns only its popup window/listeners and immutable shared rows; adapter/controller epochs guard display and action execution. |

Bind `Legacy(adapter::install, adapter::removeAndAwait, adapter::close)`. `install` and confirmed removal are
controller-worker calls with a two-second caller bound, including queue admission; EDT never waits for them.
Authority timeout invalidates its attempt before returning unavailable; authority-only pending work cannot later
connect or publish and does not block modern publication. Its daemon worker retains the attempt slot until the
filesystem returns, under the approved limitation below.
`false` from install means no live or pending publication remains. If teardown is still owed, throw a scoped
runtime failure so the existing controller retains its cleanup obligation and refuses replacement. Removal returns
only after server-confirmed destruction, or after connection termination has been established; a timeout is a
cleanup failure, never permission to publish modern concurrently. Close itself returns without waiting.

Keep host identity inside the adapter as `(connection epoch, screen, selection-owner XID, host epoch)`. A replacement
owner or lost embedding first invalidates native actions, reports `legacyChanged(false)`, then reports true only
for a validated new host. These admissions are ordered even when the controller coalesces reconciliation. This
preserves Task 5's boolean API while forcing its tested legacy-generation refresh. Test same-XID reuse on a new
connection and rapid false/true observations. No native action may bypass both adapter and controller guards.

## Connection, threading and cancellation

Use the existing JNA 5.14.0 dependency. Runtime libraries are system `libxcb.so.1`, `libxcb-render.so.0`,
`libXau.so.6` and libc; no native library is bundled by this amendment. Missing libraries/symbols or unsupported
formats mean unavailable fallback, logged once per state. Native loading happens only after non-headless Linux
installation and settled startup. Classes remain safe to load in headless and other-platform tests.

The approved first transport scope is a local Unix X11 display, including local XWayland when actually verified.
Capture `DISPLAY`, screen and authority location from launch; support the normal filesystem and Linux abstract
socket forms. Remote TCP/SSH-forwarded displays are outside this fallback scope and leave modern/no-icon
residency available. This approved scope leaves modern/no-icon residency available on other display transports.

Use a nonblocking owned Unix socket and finite `poll` for connection establishment, then `xcb_connect_to_fd`
with the launch session's authority. Authority acquisition takes a size-bounded regular-file snapshot and checks
cancellation and the shared two-second deadline before and after each filesystem syscall and between 16 KiB reads.
Decode the bounded snapshot through `XauReadAuth` on an in-memory stream, with explicit lifetime for native records
and matching cookie memory. Reject final-component symlinks, special files, malformed/excessive records and changed
snapshots; never log cookies or modify access controls. Normal local regular files are the supported operational
scope. Filesystem acquisition is cooperative: a stalled pathname lookup, open, stat or read may retain the sole
worker and its file descriptor until the kernel returns. At most one authority/connection attempt may remain
outstanding. Close invalidates admissions immediately and never waits on the filesystem; canceled acquisition
cannot subsequently connect or publish. Cleanup runs on the owning worker once the syscall returns. No replacement
authority/connection attempt is admitted before that worker finishes cleanup. Authority discovery creates no native
icon; a stalled authority attempt therefore does not block modern publication. The sole native worker is a daemon,
and application exit never waits for it. Socket setup and every XCB operation that can flush retain the
protected-duplicate shutdown deadline and native cancellation gate. Validate authority matching, malformed input
and missing-cookie behavior with fixture files.

One daemon native worker owns every XCB call and resource free; one bounded/coalesced command slot gives cleanup
priority. It drains event/reply batches with count and byte limits, then yields to commands. Poll the connection FD
plus a private wakeup FD for commands; use `xcb_poll_for_event` and `xcb_poll_for_reply`, not blocking event/reply
waits. All returned native buffers are freed once. Use a reply barrier and scoped error sequence tracking to confirm
unchecked void request batches without an unbounded `xcb_request_check` (Ruling 24: checked void errors require that blocking check; unchecked errors reach the scoped event queue). Bound outstanding cookies and image buffers.

Setup, explicit flush **and requests that may flush internally** share an operation deadline. A separate daemon
deadline scheduler cancels by `shutdown(SHUT_RDWR)` on an owned duplicate of that operation's socket. A generation
lock protects the duplicate's identity and close; never retain an unprotected integer FD for a delayed callback.
The duplicate is closed after cancellation disarms, so it cannot keep the connection alive. Only the native worker
calls `xcb_disconnect` or frees the connection, after its native call returns. Neither concurrent disconnect/free
nor Java interruption is the cancellation mechanism. Each failed attempt must finish cleanup before another starts.

This is a **proof-gated cancellation design**, not a claim that a daemon thread or polling solves every native wait.
Before production, verify setup stalls, backpressured implicit/explicit writes, close during native calls, descriptor
reuse and recovery against the supported libxcb versions. If shutdown cannot bound them, stop and revise the design;
do not accumulate stuck workers or silently introduce a helper process. An adapter failure must not change Xlib
handlers or exit the JVM. Losing the entire desktop may independently end JBR's own display connection; acceptance
must distinguish that existing session behavior from an adapter-induced process exit.

Production composition retains one controller scheduler, its coalesced reconcile and capped retry sequence. Add
one native event worker and its independently runnable deadline cancellation. There is at most one queued popup
request and one outstanding snapshot per purpose. Physically cancel timers on success/close and remove canceled
tasks. While modern publication is healthy, remove the X11 publication and suspend its five-second fallback probe;
the native worker may remain blocked on its wakeup/event FDs without polling. Resuming fallback takes a fresh
snapshot before installation. Modern registration, transport and retry behavior remain as reviewed in Tasks 4–5.

## Discovery, embedding and removal

Observe the selected screen's root `MANAGER` messages before taking the initial `_NET_SYSTEM_TRAY_Sn` owner snapshot.
Watch the owner and icon for destruction, property, reparent and configuration events. A five-second owner check
while legacy discovery is needed covers selection loss without owner destruction; it does not reinstall a healthy
icon. Revalidate the selection owner before and after docking. No owner, refused docking or lost connection is an
ordinary unavailable backend, with no incident, modal warning or automatic application exit.

Create an initially unmapped native child with `_XEMBED_INFO` version 0/mapped intent, Jasper name/class/icon hints
and size hints, then send `SYSTEM_TRAY_REQUEST_DOCK` to that exact owner. Accept only the current icon/epoch's
XEmbed handshake and verified reparenting into its announced embedder, followed by a still-current owner snapshot.
`install` succeeds only after embedding is confirmed within its deadline. Never show an undocked desktop icon.
Handle XEmbed activation/focus messages, mapped state and focus traversal for this single actionable control;
request focus through the embedder and return traversal rather than grabbing focus globally. Detailed packet layout
and event dispatch belong to the revised plan, pinned to the [XEmbed specification](https://specifications.freedesktop.org/xembed/latest-single/).

On owner loss, icon destruction, reparent-to-root or stale handshake, invalidate actions immediately, cancel the
popup, withdraw old resources and re-observe. Never reuse an old XID as proof of host identity. Removal destroys the
owned icon/pixmaps/GC/pictures/colormap with confirmation; discovery may retain the connection. Failed removal
terminates that owned connection before replacement is permitted. Final close disables admissions, cancels timers,
wakes the worker, detaches observations and disposes the popup on EDT, then closes socket/native resources in bounded
cleanup. Authority-file cleanup waits for any pending filesystem syscall to return; Quit never awaits that daemon
worker. No library/global desktop state is modified. Repeated close and callbacks queued before close are harmless.

The [system tray specification](https://specifications.freedesktop.org/systemtray/latest-single/) defines selection
discovery, docking, preferred visual, hints and arbitrary host-assigned icon sizes. The adapter implements that
protocol only; legacy balloon messages remain outside its scope.

## Drawing, scaling and interaction

Rasterize the existing Linux artwork offscreen. Treat native ConfigureNotify dimensions as device pixels; fit the
complete icon with preserved aspect ratio into the actual slot and center it. Do not multiply by JBR's UI transform.
Coalesce resize/expose events and redraw only when dirty. Bound accepted slot sizes and allocations (maximum
512 pixels per dimension); an unreasonable slot fails this publication rather than allocating without limit.

Honor a valid advertised `_NET_SYSTEM_TRAY_VISUAL`, otherwise the screen default. Resolve its depth, byte order,
scanline padding and RGB/alpha masks instead of assuming 32-bit BGRA. Keep the existing D-Bus `LinuxTrayMenu.argb`
encoding unchanged: native upload needs a separate converter. Use RENDER formats and premultiplied source pixels.
With an alpha visual, clear and composite into the transparent destination. With an opaque visual, use the tray's
ParentRelative background at matching depth, clear it on repaint and composite the source over it. Never set
ParentRelative across mismatched depths; reject an unsupported visual/parent combination. See the
[RENDER protocol](https://www.x.org/releases/current/doc/renderproto/renderproto.txt) for format and composition semantics.

An alpha visual alone does not prove correct visible transparency. Verify host compositing both with and without
a desktop compositor; if the host advertises an unusable path, fail that publication rather than paint black
corners or guess the panel background. Refresh visual resources when the owner changes; a changed visual on the
same owner invalidates and redocks once. Expose/resize/panel-background changes must repaint without trails.

Primary button release inside the icon invokes shared action 1 once; secondary release opens the full shared menu.
Keyboard Enter/Space activates, Menu/Shift-F10 opens the menu, and Tab traversal follows XEmbed. Resolve keysyms
through a bounded public X protocol keymap snapshot and refresh on mapping changes. Middle click and wheel have
no action. No pointer or keyboard grab is held by the native icon. Unknown/old events cannot dispatch actions.

The popup is ordinary Swing created only on a user menu request: one short-lived undecorated focusable
`JDialog` of `Window.Type.POPUP` containing a `JPopupMenu`, with standard menu selection and keyboard handling.
Use `LinuxTrayMenu.rows()` and the existing ID dispatcher. Dispose it on selection, Escape, outside interaction,
focus loss, host invalidation or close; remove global listeners immediately. Menu actions execute through the
controller's EDT guard. A canceled Quit leaves discovery alive. No hidden permanent frame or popup keeps the
application resident, and opening the menu must not create a Jasper workspace window or taskbar entry.

For pointer invocation, sample public `MouseInfo` on EDT and place/clamp the popup in that device's AWT user-space
bounds/insets; do not pass raw X device coordinates directly to Swing. If the pointer has moved or for keyboard
invocation, translate the icon's native root rectangle to the matching public GraphicsConfiguration using its
transform and verified monitor origin. The revised plan must specify and test that mapping; mixed-DPI monitor
identity is a native gate, not permission to guess a global scale. Popup focus/outside-click behavior and placement
must pass with no other Jasper windows open. If this public Swing composition fails, revise it before shipping.

## Decisive gates and delivery

The revised plan must put an owned native characterization gate before production fallback wiring. Preserve the
user-directed validation split: execute on the authorized Fedora aarch64 host; x86_64 and Ubuntu 22.04 remain CI
release gates, not claims of locally executed coverage. Passing the failed AWT probe's positive rows is insufficient.
Required evidence:

1. **ABI and cancellation:** x86_64 and aarch64 layouts/symbols, bounded socket setup, cooperative authority
   acquisition, unanswered requests,
   backpressured output including implicit flushes, BadWindow/BadMatch, adapter socket loss, FD reuse, close during
   each phase and repeated retries. JVM survives adapter failure; no concurrent native free, stuck worker growth,
   leaked descriptors/buffers or stale callbacks after cleanup. A deterministic held-authority test proves one
   attempt, immediate invalidation, modern availability, discarded late results and cleanup after release; it
   does not claim syscall interruption. Exercise an isolated adapter connection so JBR display loss cannot
   mask the result. Packaged JBR must repeat the gate on Fedora and the Ubuntu 22.04 baseline.
2. **Real legacy hosts:** authorized isolated LXDE and XFCE, initially no host, late host, repeated slow/fast panel
   restart, actual live applet removal/re-add, selection replacement without process death, host loss during docking
   and menu display. Zero unexpected exceptions/incidents. A protocol fixture supplements real panels for races;
   it cannot replace them. Confirm old publication removal before every modern/legacy replacement.
3. **Visual and input:** complete icon and transparent corners on light/dark/patterned panels; opaque and alpha
   visuals with compositor on/off; resizing and reparenting; normal 1x, matched 2x and deliberately mixed host/JBR
   scales. All shared actions exactly once, keyboard focus/traversal, popup dismissal and menu placement at screen
   edges and mixed-DPI monitors, including no Jasper windows. Virtual rasters leave physical-monitor acceptance open.
4. **Integration and lifetime:** modern preferred, modern registration failure falls back, no bus/host stays resident,
   returning hosts recover, canceled Quit stays live, accepted Quit closes native/menu workers and subscriptions,
   and logout does not leave an orphan. Keep Plan 2's wider desktop matrix and CI rows pending until individually
   executed; protocol support is not a distribution-wide compatibility claim.

Headless tests cover packet/ABI fixtures, pixel conversion, owner and action epochs, coalescing, cancellation,
partial installation, cleanup refusal and routing through the existing seams. Rerun the reviewed controller and
D-Bus regressions, architecture/source inventory/notices, full `check` and headless package verification as required
by the revised plan. Update only affected guide statements once implemented; this amendment changes no behavior guide until implementation.

Read-only inventory on authorized Fedora 44 aarch64 on 2026-10-28 found libxcb 1.17.0-7.fc44, libXau 1.0.12-4.fc44,
libXrender 0.9.12-4.fc44 and libX11 1.8.13-3.fc44. The three proposed XCB/Xau sonames exist; exported symbols include
connect-to-FD, poll event/reply, connection error, disconnect/flush, RENDER query/create/composite and Xau auth APIs.
This proves installed availability only. No display, desktop bus, authority cookie, GUI or native implementation
was accessed for this amendment. Runtime baseline/library packaging and every new native behavior remain unproven.

Approval selected this scoped architecture and its gates. The revised Plan 2 preserves completed Tasks 1–5
and records the failed public-AWT approach; remaining Tasks 6–10 implement this amendment. No fallback
production code is committed until the new cancellation and legacy characterization gates pass.


## Authority-I/O correction — approved 2026-10-28

The user approved the narrow authority-I/O correction after Linux [open(2)](https://man7.org/linux/man-pages/man2/open.2.html)
and [close(2)](https://man7.org/linux/man-pages/man2/close.2.html) established that regular-file `O_NONBLOCK` and concurrent
close cannot guarantee syscall cancellation. The contract above supersedes the original strict authority-acquisition
bound. A filesystem stall may retain one daemon worker and one authority FD, in addition to its owned wake pipe,
until the kernel returns. Legacy fallback remains unavailable during that stall; modern publication and nonblocking
app exit remain available. No additional worker/retry accumulates and a canceled late result is discarded.

Task 6 separates the strict socket cancellation gate from held-authority admission/cleanup evidence; Tasks 7/9 retain
one authority-attempt slot and distinguish it from an actual icon/socket cleanup obligation; Task 10 records the
residual limit in verification and maintenance. The native socket gate, Fedora execution and required x86_64/Ubuntu
22.04 CI release gates are unchanged. Approval is not native cancellation evidence.
