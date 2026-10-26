# Qt / KDE sources for Desktop colors

Status: **Approved for implementation planning, 2026-10-25.** The user approved this design. Builder review resolved the lifecycle question below. Implementation has not started. Plans: [native helpers](../plans/2026-10-25-qt-kde-native-helpers.md), then [application integration](../plans/2026-10-25-qt-kde-application-integration.md).

Extends [Linux Desktop colors](2026-10-17-jasper-linux-desktop-colors-design.md) and the
[Linux default change](../plans/2026-10-25-linux-default-colors.md). Current guides take precedence over
historical statements about defaults and editor independence in the original design.

## Intent and scope

Desktop colors should fit KDE Plasma and other Qt desktops as well as GTK desktops. The user requested
Plasma 5 / Qt 5 and Plasma 6 / Qt 6 support, with Automatic choosing the appropriate source. Retain the
Linux default, interface-only adaptation, independent Editor choice, terminal scheme behavior, portal
preferences, contrast repair, and existing Settings Apply/OK/Cancel semantics.

Import colors, not Qt widget styling, fonts, metrics, icons, window decorations, or animations. Custom
styles can paint outside QPalette; matching their complete appearance is outside this deliverable.

## Approaches and decision

1. **Isolated native APIs (approved).** Read the
   Qt application palette and, on KDE, KColorScheme. Separate processes isolate toolkit majors and
   failures. This follows the actual toolkit environment and supports live updates, at the cost of
   optional native binaries and a larger Linux test matrix.
2. **Parse kdeglobals and Qt configuration files.** Fewer native dependencies, but would need to recreate
   platform integration, configuration precedence, derived state colors, and different theme engines.
   It cannot be the general Qt source and is not selected.
3. **Continue reading only GTK on KDE.** Simple, but GTK settings may differ from the user's Qt palette.
   Retain GTK as an Automatic fallback, rather than treating it as the KDE source.

The user approved the concrete policy below on 2026-10-25. There is no application-code work in this design phase.

## Settings and presentation

Extend `ui.theme.desktop_source` with `qt`. The selector is **Automatic**, **Qt / KDE**, **GTK 3**,
**GTK 4 / libadwaita**. `auto` remains the default. One Qt family option covers both majors and KDE's
richer implementation; users need not understand toolkit versions. Version-specific selectors are
outside this scope. Existing auto/gtk3/gtk4 files retain their meaning; invalid values still diagnose
and fall back to auto. Saved qt values survive use on non-Linux platforms.

Status distinguishes actual implementation and versions, for example:
- `KDE Frameworks 6 / Qt 6 · desktop colors`
- `Qt 5 application palette`
- `GTK 3 · Qt / KDE unavailable`
- `Using last available KDE colors`
- `Using bundled colors · desktop palette is Light; Jasper requests Dark`

Pending selections remain visibly pending Apply. Capability updates do not dirty settings, steal focus,
write config, or trigger repeated notifications. Explicit Qt / KDE permits fallback within its Qt family,
but never silently chooses GTK. Explicit GTK selections remain strictly GTK-only.

## Automatic selection

Capture desktop hints once per service lifetime; Reload configuration recreates that detection context.
Parse `XDG_CURRENT_DESKTOP` as trimmed, case-insensitive colon-separated tokens. Recognize KDE, GNOME,
and LXQt; if conflicting recognized families occur, the first recognized token wins. If none is present,
`KDE_FULL_SESSION=true` may establish KDE. Do not infer a Qt desktop merely because Qt libraries exist.

| Context | Candidate order |
|---|---|
| KDE, `KDE_SESSION_VERSION=5` | KDE5, Qt5, KDE6, Qt6, GTK3, GTK4 |
| KDE, version 6 or absent/unknown | KDE6, Qt6, KDE5, Qt5, GTK3, GTK4 |
| LXQt | Qt6, Qt5, GTK3, GTK4 |
| GNOME | GTK4, GTK3, Qt6, Qt5 |
| Other/unknown | GTK3, GTK4, Qt6, Qt5 |

For LXQt and unknown desktops, an explicit `QT_QPA_PLATFORMTHEME` containing a supported `qt5ct`
or `qt6ct` key (including Qt's parameter suffix) establishes the preferred Qt major. On an otherwise
unknown desktop it moves Qt ahead of GTK. A known GNOME or KDE desktop outranks this hint. Unknown
platform-theme values and `QT_STYLE_OVERRIDE` do not change the order. No session variable is rewritten.

Explicit Qt / KDE uses the same Qt-only ordering, with KDE candidates included only in a KDE session;
else it tries the preferred Qt major and then the other. This avoids reading stale KDE configuration
on another desktop. Qt platform integration may itself use KDE or qt5ct/qt6ct: let Qt choose it.

Each candidate must provide the existing usable Window pair with the requested polarity. Missing
optional roles do not trigger a switch. Choose one whole candidate snapshot: do not mix Qt5/Qt6,
KDE/Qt, or GTK pairs. Generic Qt is a complete fallback source, not a role-by-role patch for KDE.
A compatible last-good snapshot retains its existing priority during transient failure; fallback begins
when that candidate has no compatible snapshot. Recovery of a higher-priority candidate replaces fallback.

The candidate list supplies both resolver ranking and application/preview demand, from one shared policy.
Extend existing source identities to GTK3, GTK4, QT5, QT6, KDE5 and KDE6. Retain source identity in every
cache, protocol message, capability record and generation check. No hard-coded GTK-only ternaries remain.

## Native helpers and optional dependencies

Ship four additional fixed executables beside the existing portal and GTK helpers:
`jasper-desktop-qt5`, `jasper-desktop-qt6`, `jasper-desktop-kde5`, `jasper-desktop-kde6`.
The Qt pair uses QApplication/QPalette; the KDE pair also links its matching framework's KColorScheme.
KDE5 uses KF5 ConfigWidgets; KDE6 uses KF6 ColorScheme. Share source code where appropriate, with
compile-time differences isolated at the extraction boundary. Each process loads exactly one Qt major.
No Qt/KDE library enters the JVM or either GTK helper.

Separate KDE binaries keep KDE libraries optional even when generic Qt is installed. Missing ELF libraries,
platform plugins or display access are normal capability failures; the supervisor captures bounded stderr
and continues with the next permitted candidate. Prefer ordinary public C++ APIs and linkage, not hand-written
C++ ABI declarations or loading one toolkit's plugins into another major.

Proposed build/runtime floors: Qt5 5.15, generic Qt6 6.2; KF5 5.92; KDE6 KF6 6.0 with Qt6 6.5.
These are compatibility targets to prove with a compile-and-run spike before UI integration, not assumptions
that every distribution ships that combination. Use the existing Ubuntu 22.04 glibc baseline for packaged
binaries; build the newer Qt/KF helper against private baseline-compatible development prefixes as needed.
Do not raise Jasper's OS floor to obtain Qt6. Do not bundle a replacement Qt runtime/platform-theme plugin:
use the system runtime so user integration remains effective. Package metadata must not turn optional Qt/KF
libraries into mandatory Jasper dependencies. Record build dependencies and applicable notices.

Verification distinguishes required baseline helper dependencies from optional Qt/KF dependencies. It checks
that every new helper has the intended major, architecture and glibc floor, that installed dependency sets
resolve correctly, and that absent optional libraries leave Jasper usable. Helpers ship in both portable
`lib/desktop-colors` and jpackage `lib/app/desktop-colors` layouts on x86_64 and aarch64.

## Initialization, appearance and change observation

Create QApplication and unshown probe widgets only where needed to receive palette/style events. Never show,
activate or map a window, enter session restoration, or modify desktop settings. Use the real session display
and its platform integration; offscreen/minimal platform plugins are test-only and not desktop detection.
Honor the inherited Qt theme/style environment. Report the Qt runtime version and actual QStyle identity when
available; a requested environment plugin name is only a hint, never proof that that plugin loaded.

Read QApplication's effective application palette, not QStyle::standardPalette or a palette overwritten with
Jasper colors. KDE probes use the current system configuration through KConfig/KColorScheme APIs. Reparse the
system configuration at resampling boundaries and then construct fresh schemes; do not parse files manually.
Do not activate an arbitrary named KDE scheme or use autosaving scheme-manager behavior.

Keep the existing appearance policy: explicit Jasper Dark/Light wins; Match System uses the portal and its
existing Dark fallback when no preference is available. Toolkit polarity is palette evidence, not a second
system-appearance authority. In this deliverable Qt probes read the current desktop palette; they do not force
an opposite theme, even on newer Qt versions that expose a color-scheme setter. A mismatched palette is
unavailable for the request. Automatic can try another source; explicit Qt uses a compatible cached Qt snapshot
or bundled fallback. No inversion, theme-name suffix guessing, or Breeze substitution.

A Qt/KDE helper that initializes and replies successfully remains alive when its palette has the wrong polarity
or temporarily lacks a usable pair. These are availability observations, not process failures. Every sample reply
carries the request generation and actual observed polarity; it satisfies the response deadline. The supervisor
retains capability information and passes valid observed colors onward without relabeling them as the requested
appearance. The application may cache a valid observation under its actual source/polarity key, but never install
it for an incompatible request. A prior compatible cache remains visibly last-good. Incompatible/missing-pair
observations do not reset the source's identity or cause exponential restarts; events and periodic samples can
recover immediately. Crashes, timeouts, malformed protocol and failed initialization retain bounded restart
backoff. This changes the existing supervisor's close-on-mismatch behavior for the new Qt/KDE sources only.
Tests must cover repeated alternate light/dark preview requests while one healthy Qt process exposes a single
palette, followed by a live desktop change that makes the previously unavailable request usable.

Observe ApplicationPaletteChange, PaletteChange and StyleChange through public event handling; coalesce before
sampling. Retain the existing 30-second resample, which also refreshes KConfig data. Generic Qt's periodic read
cannot guarantee reload of an external theme engine that ignores changes; Reload configuration restarts probes
and is the supported recovery in that case. Validate actual live update behavior for KDE and qt5ct/qt6ct.
The initial sample occurs after toolkit initialization and event subscriptions, on the helper's GUI thread.
All sampling stays on that thread; Java I/O and resolution stay off the EDT.

## Palette mapping

Keep the existing seven normalized pairs. Read Active state for ordinary controls; inactive/disabled native
roles do not introduce a second state model in this deliverable. Jasper keeps deriving and validating its own
interaction states. ToolTip colors follow Qt's documented Inactive group convention.

| Normalized pair | Qt application palette | KDE scheme |
|---|---|---|
| Window | Window / WindowText | Window NormalBackground / NormalText |
| View | Base / Text | View NormalBackground / NormalText |
| Header | missing | Header pair if the built API and theme support it; otherwise missing |
| Sidebar | missing | missing |
| Dialog | Window / WindowText | Window pair |
| Popover | ToolTipBase / ToolTipText, Inactive | Tooltip NormalBackground / NormalText |
| Selection | Highlight / HighlightedText | Selection NormalBackground / NormalText |

Qt has no distinct portable sidebar or header pair; absence uses the existing single-source derivation/fallback.
Qt Button, AlternateBase, link and KDE focus/hover/semantic roles are deferred because current normalized roles
cannot represent all of them independently. This extension does not expand Jasper's semantic or SDK model.
Portal accent continues to override the derived accent; existing valid native selection preservation remains.
Qt6's newer Accent role is not required, keeping the Qt5 and Qt6 baseline mapping equivalent.

Accept only solid finite sRGB brushes; convert QColor through supported color conversion, reject invalid colors
and gradients/textures rather than pretending a representative pixel is a complete color. Apply existing alpha,
Window admission, polarity, 4.5:1 normal / 7:1 high-contrast text and 3:1 boundary checks. Keep foreground/background
pairs together. Existing last-good, neutral-family and Slate Cobalt fallbacks remain authoritative.

## Lifecycle, protocol and packaging integration

Reuse DesktopProcess framing, size limits, generation/close guards, two-second asynchronous response deadlines,
bounded diagnostics, retry backoff and 100 ms coalescing. Portal lifetime remains independent. Extend the coordinated
helper protocol's accepted source identifiers; producer/consumer fixtures must reject unknown or mismatched sources.
GTK message shape and existing protocol version stay unchanged if the change is limited to new source enum values;
any structural incompatibility requires an explicit protocol version bump before implementation.

Demand can include ranked candidates for active appearance and retained Settings previews. Preserve the current
aggregate-demand/caching model first; no blocking sequential discovery on the EDT. Additional helpers stop when not
demanded and all stop on application disposal. Measure the expanded candidate set's idle CPU and memory in headless
process tests; do not turn periodic sampling into repeated toolkit startups. Main-window startup never waits for a
native palette. Settings status must identify Qt-family fallback and polarity mismatch without mislabeling a cached
KDE6 palette as Qt5 or GTK.

## Verification and delivery gates

Before UI integration, demonstrate all four native helpers independently with deterministic palette fixtures:
Qt5/KF5, Qt6 baseline, and Qt6/KF6; prove unmapped initialization, event delivery, palette roles, high contrast, live
KDE reconfiguration, solid-brush rejection and absent dependencies. A private Xvfb/session-bus harness covers
headless extraction, but does not replace native desktop acceptance. Add real Plasma5 and Plasma6 Wayland/X11
checks, plus Qt5 and Qt6 configuration tools on a non-KDE desktop. Run GUI acceptance only with user authorization.

Pure Java tests cover every table row, mixed/cased desktop tokens, missing/bogus session versions, qt5ct/qt6ct hints,
explicit sources, opposite polarity, per-source cache isolation, recovery/preemption, malformed protocol, stale
replies, preview cancellation, saved settings, and non-Linux retention. Existing GTK fixtures and authored render
captures must remain unchanged. Reuse actual-paint contrast coverage for representative Qt/KDE light/dark/high-
contrast palettes. Verify configuration templates, architecture, packaging paths and optional dependency behavior.

Acceptance: changing desktop colors updates Jasper without focus changes, mapped helper windows, partial theme
frames, or config writes; terminal/editor color choices remain independent. An unavailable source leaves a readable
interface and truthful status. Compare screenshots against native Qt/KDE controls for color fidelity, not geometry.

## Implementation investigations

The compatibility spike must establish minimum-header availability for KDE Header roles, packaged baseline build
prefixes, optional package dependency handling, Qt event behavior with no visible windows, and reliable KDE refresh.
If these fail, revise the design explicitly rather than silently reducing Plasma5/6 coverage. The exact C++ file split,
build tooling and test fixture helpers are left to the implementation plan.

The approved choices are one Qt / KDE selector, desktop-aware major preference, Qt family before GTK on Qt desktops,
and current-palette-only Qt extraction with the existing opposite-appearance fallback. The implementation plans
retain the compatibility gate before source/UI activation.

## Primary references

- [Qt5 QPalette](https://doc.qt.io/archives/qt-5.15/qpalette.html) and
  [Qt6 QPalette](https://doc.qt.io/qt-6/qpalette.html): palette roles and style limitations.
- [QGuiApplication](https://doc.qt.io/qt-6/qguiapplication.html): desktop settings and application palette.
- [QEvent](https://doc.qt.io/qt-6/qevent.html): palette-change events.
- [QStyleHints](https://doc.qt.io/qt-6/qstylehints.html): versioned appearance APIs; setter only from 6.8.
- [KColorScheme](https://api.kde.org/kcolorscheme.html): paired role sets and state groups.
- [KColorSchemeManager](https://api.kde.org/kcolorschememanager.html): scheme activation and autosave behavior.
- [Portal settings](https://flatpak.github.io/xdg-desktop-portal/docs/doc-org.freedesktop.impl.portal.Settings.html): shared appearance preferences.
