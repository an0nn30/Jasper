# Linux Desktop colors

Status: **Approved for implementation planning, 2026-10-17.** The user approved this written specification.
[Implementation plan](../plans/2026-10-17-jasper-linux-desktop-colors.md) is drafted for review; implementation has not started.

This extends the current [theme import design](2026-10-13-jasper-theme-import-design.md) and
[theme engine](2026-10-04-jasper-theme-engine-design.md). It uses the current `ui.appearance`,
`ui.theme.dark`, `ui.theme.light` and `terminal.scheme` settings. The older name/source/follow-system
policy was superseded by the [Slate Steel design](2026-10-12-jasper-slate-steel-themes-design.md).
Slate Cobalt is the current bundled fallback. Ubuntu Yaru remains a separate authored theme.

## 1. Intent and agreed scope

Offer an optional Linux theme that makes Jasper's interface fit the desktop through colors, while retaining
its kit, layout, typography and interaction behavior. Desktop changes must remain responsive, readable and
recoverable even when native services fail or return incomplete information.

The user selected interface-only adaptation, both GTK 3 and GTK 4/libadwaita, and a source selector defaulting
to Automatic. Explicit terminal schemes remain independent. Desktop colors supplies the corresponding Slate
Cobalt terminal scheme for `match`, and the Slate Cobalt editor colors and syntax for its appearance. GTK or
accent changes alone never recolor terminal text or editor content; changing Light/Dark can change these
bundled variants. An independent editor-theme setting is outside this deliverable.

## 2. Settings and identity

Reserve theme id `desktop-colors`. On Linux, offer **Desktop colors** in both existing theme dropdowns.
It is a runtime source, not an imported JSON file; it cannot be removed, overwritten by import or shadowed
by a plugin. The file format remains unchanged. Existing theme picks and defaults remain unchanged.

```toml
[ui]
appearance = "system"

[ui.theme]
dark = "desktop-colors"
light = "desktop-colors"
desktop_source = "auto" # auto, gtk3, gtk4
```

Show one **Desktop color source** dropdown when either staged theme pick is Desktop colors:
**Automatic**, **GTK 3**, **GTK 4 / libadwaita**. It is shared by both variants, defaults to `auto`, and uses
normal Apply/OK/Cancel and configuration validation. Changing the source does not change appearance policy.
Invalid source values receive the ordinary configuration diagnostic and default to `auto`.

An explicit source remains requested when unavailable; it falls back to bundled colors and available portal
preferences instead of silently choosing the other toolkit. On other operating systems retain the saved id
and source, resolve Desktop colors to the corresponding bundled variant, and show it as unavailable if already
selected; omit it from new choices. Reserve its id on every platform so shared configurations are unambiguous.

One passive status line describes the applied source, for example “GTK 4 / libadwaita”, “GTK 3 · partial palette”,
“Using last available GTK 3 colors”, or “Using bundled colors · GTK 4 unavailable”. When a draft source differs,
label it as pending Apply rather than attributing the current palette to that draft. Capability changes never
write config, dirty or rebase a Settings draft, move focus, or produce repeated alerts.

## 3. Ownership and data flow

Use three small Linux executables under `packaging/desktop-colors`: a GLib/GIO portal monitor, a GTK 3 palette
probe, and a GTK 4/libadwaita palette probe. GTK majors never load into the same process or the JVM. Splitting
portal observation from GTK permits preferences to work when neither toolkit probe can start.

`dev.jasper.app.platform` owns concrete closeable process supervision and immutable JDK-only observations:
portal preference/accent/contrast availability, toolkit identity/version, palette pairs, generation and reason.
It has no dependency on the theme engine or other app packages. The application lifetime owns the monitors.
Extend `SystemAppearance` to consume the same Linux portal observation used by Desktop colors; do not create
two conflicting dark-mode observers. Ordinary Match System on Linux benefits from that input too.

`dev.jasper.app.appearance` owns a pure desktop-palette resolver and feeds a complete `GeneratedTheme` into
the existing `ResolvedTheme` / `ThemeController` transaction. Extend selection resolution explicitly to handle
the reserved dynamic id alongside authored `JasperTheme` values; do not manufacture a temporary theme file or
mutate an authored theme. The library offers stable metadata and bundled matching schemes for the dynamic id.
The generic `jasper-ui` value model remains free of OS access. Plugins receive existing theme bindings; no SDK
extension or plugin-specific recoloring is needed.

Keep new types concrete or records unless two actual implementations justify an interface. Existing package
DAG checks remain binding. Native I/O, decoding and palette calculation run off the EDT; only final installation
and publication run on the EDT through the existing rollback path.

## 4. Portal and source policy

Read `org.freedesktop.portal.Settings` on the session bus. Use `ReadAll` for the appearance namespace and
subscribe to `SettingChanged`, with subscribe-before-read and serialized processing so initial reads cannot
overwrite newer signals. Observe service-owner changes, invalidate the departed owner's reads, reconnect and
resnapshot. Each key is independently optional; unknown values mean unavailable/no preference, never Light.

Recognize color-scheme (dark/light/no preference), finite sRGB accent components in [0,1], and high contrast.
Missing or malformed keys do not discard other valid keys. Forced Jasper Dark/Light wins over portal appearance;
Match System uses portal preference, retaining Jasper's current Dark fallback when no preference is available.
Portal accent and contrast apply only to Desktop colors, leaving authored themes untouched.

Automatic detects GNOME by a case-insensitive `GNOME` token in colon-separated `XDG_CURRENT_DESKTOP`:
prefer GTK 4/libadwaita there, GTK 3 elsewhere (including unknown desktops). Try the other source when the preferred
source cannot supply a usable palette for the effective appearance. A usable palette requires at least the
window background/text pair and compatible polarity. Missing optional pairs do not trigger source switching.
Do not combine GTK 3 and GTK 4 pairs in one snapshot. A recovered preferred source replaces an automatic fallback.

An explicit source tries only that toolkit. GTK 4 runs with libadwaita when available; otherwise it can report
plain GTK 4 named colors, clearly identified in status. The selector names a toolkit family, not a guarantee that
libadwaita is installed. Portal preferences remain authoritative for Jasper even when toolkit preferences differ.

## 5. GTK extraction

Probes initialize with display availability checks and create rooted, unmapped probe widgets. They never present
a window, acquire focus, start a shell, or change desktop settings. Toolkit appearance overrides are process-local.
Request the effective appearance using supported toolkit facilities; verify the resulting pair rather than trusting
the request. A theme without a compatible variant is unavailable for that appearance, not automatically inverted.
Classify the opaque window pair as Dark when its background relative luminance is below its foreground, Light
otherwise; reject equal or insufficient-contrast pairs. This is a documented heuristic, not a theme-name suffix test.

GTK 3 resolves named colors through its style context. GTK 4 before CSS-variable support uses available named
colors; modern GTK 4/libadwaita resolves documented variables in the widget cascade. Use dedicated CSS probe
classes that assign each requested variable to `color`, then read the resolved foreground through public GTK APIs.
Use two distinct fallback sentinel colors to distinguish a missing variable from a real color equal to one sentinel.
Do not parse installed CSS text or treat old compatibility names as equivalent to overridden modern variables.

Minimum build targets are GTK 3.24 and GTK 4.10, with libadwaita optional at runtime. Detect GTK 4.16+ variable
support and available libadwaita features at runtime. Older GTK 4 systems use the named-color path; failure to
initialize libadwaita produces an identified plain-GTK result or unavailability. Native integration tests must
establish that unmapped rooted probes resolve both theme and user overrides on the supported versions.

The normalized input vocabulary is fixed; missing entries are explicit:

| Pair | GTK 3 named candidates, in order | Modern libadwaita variables |
|---|---|---|
| Window | `theme_bg_color` / `theme_fg_color` | `--window-bg-color` / `--window-fg-color` |
| View/input | `theme_base_color` / `theme_text_color` | `--view-bg-color` / `--view-fg-color` |
| Header | Window fallback | `--headerbar-bg-color` / `--headerbar-fg-color` |
| Sidebar | Window fallback | `--sidebar-bg-color` / `--sidebar-fg-color` |
| Dialog | Window fallback | `--dialog-bg-color` / `--dialog-fg-color` |
| Popover | Window fallback | `--popover-bg-color` / `--popover-fg-color` |
| Selection/accent | `theme_selected_bg_color` / `theme_selected_fg_color` | `--accent-bg-color` / `--accent-fg-color` |

For older libadwaita, use the corresponding available underscore names without the `--` prefix. Plain GTK 4 may
also supply the GTK 3-style names. Candidates are paired: never choose the background from one naming family and
the foreground from another. Optional unavailable modern variables remain missing instead of relying on version
guesses. Imported semantic success/error/hue colors are deferred; existing bundled roles are contrast-validated.

Observe toolkit theme and dark-preference notifications and libadwaita style-manager changes. Resample after
changes and every 30 seconds while active to catch style changes lacking notifications. Reload config restarts
probes, allowing CSS edits not reloaded by GTK itself to take effect. Each probe emits a full snapshot, not patches.

## 6. Mapping and readability

Start from the effective Light/Dark Slate Cobalt `GeneratedTheme`, retaining its editor, syntax, terminal and
glass values. Replace interface roles from the selected source, deriving state colors through Jasper's existing
rules. Header maps to titlebar and its text; Window to app/status and base panel surfaces; View to input/field
surfaces; Sidebar to sidebar; Dialog to dialog; Popover to menu. Related raised/card/control surfaces derive from
their parent and primary foreground with existing hover/state recipes. Workspace chrome, including terminal
surrounds and tabs, adapts; the terminal text area's own background stays with its scheme.

Jasper shares foreground roles across some surfaces. Validate the resulting palette against every actual paint
pair, not just the native pairs: where distinct native foregrounds cannot be represented, retain the shared ink
and reject incompatible surface overrides. If per-family fallback still conflicts, fall back the whole neutral
surface/text family to bundled values. This avoids readable native pairs becoming unreadable Jasper controls.

Reject non-finite/out-of-range components. Composite translucent backgrounds only over a known accepted parent;
otherwise reject that pair. Composite translucent ink over its final background before evaluating contrast.
Normal enabled text requires 4.5:1; high-contrast mode requires 7:1. Selection and accent text follow the same
threshold. Focus and necessary control boundaries require 3:1 against adjacent surfaces. Disabled text remains
distinguishable but is excluded from the enabled-text threshold. These are design acceptance targets, not a claim
of comprehensive accessibility conformance.

A valid portal accent replaces the toolkit accent. Choose black or white on-accent ink by highest contrast.
If required contrast still fails, move the accent toward black or white in deterministic 5% sRGB steps, selecting
the least RGB-distance passing candidate (black direction breaks ties). Apply the same foreground-repair rule to
links and focus indicators where possible. Preserve selected-source selection colors when valid; otherwise derive
selection from the accepted accent. Revalidate hover/pressed states and all derived roles after overrides.

High contrast raises thresholds and uses opaque fills and stronger borders in the generated UI palette; it does
not alter layout or terminal/editor colors. If repair cannot satisfy all shared-role paint pairs, use a dedicated
validated high-contrast neutral UI fallback (black/white by appearance), with only safe accent overrides. Every
semantic role and overridable derived role must have an enumerated mapping or bundled-fallback entry in tests.

## 7. Process lifecycle and protocol

Launch packaged helpers directly by absolute path with fixed arguments using `ProcessBuilder`. Use a versioned
UTF-8 newline-delimited JSON protocol on standard output; stderr is bounded diagnostic text. Messages contain
protocol version, request generation, source, observed appearance, optional pairs/preferences and capability
reason. Accept only the expected types and bounded keys; limit each message to 64 KiB, drain stderr separately
with a 16 KiB retained tail, and reject malformed output without publishing a partial theme.

The application assigns a generation to each source/appearance request and process incarnation. Reject old
generations, late replies and callbacks after close. Portal state has an independent owner generation. Source
changes invalidate the old source immediately; cached GTK 3 colors cannot masquerade as an explicit GTK 4 pick.
Bound startup/response waits to two seconds on workers, never on the EDT. Initial windows use the resolved bundled
fallback if native data is not ready; startup is not blocked waiting for the desktop.

Coalesce events over 100 ms and suppress content-equal palettes. Cache last valid palettes in memory by source,
appearance and contrast policy. Temporary failure retains a compatible cached palette and updates status; a new
appearance without a compatible snapshot uses its bundled fallback. Reapply current portal preferences and
contrast validation to any cached toolkit palette. Explicit source changes can use only that source's cache.

Restart failed helpers after 1, 2, 4, 8, 16, then 30 seconds, capped at one attempt per 30 seconds; reset after a
successful snapshot. Monitor exit and bound heartbeat/request response waits so a live but stuck process is
terminated and restarted. Automatic may use the alternate source while the preferred source recovers.

Start GTK helpers only while an applied theme pick or an open Appearance preview requires Desktop colors; at most
one process per toolkit, with serialized requests for variants. Staged previews may request data but never install
it into application windows. Keep the portal monitor application-owned for Match System. Close cancels retries,
removes subscriptions, closes streams and terminates children; allow 500 ms grace before forced termination.
Helpers exit on parent input EOF. Installation failure retains the existing committed theme and reports through
the existing failure route; a later update or Reload config retries.

## 8. Packaging and verification

Ship all three helpers with Linux development/installDist and packaged routes; use optional runtime loading for
toolkit libraries so absent GTK 3, GTK 4 or libadwaita does not prevent portal-only fallback. The implementation
plan must name and test the exported symbols used for each runtime branch and the distribution ABI baseline.
Use system GTK libraries and themes. macOS/Windows packaging omits Linux executables. Build native tests on Linux;
cross-platform Java tests consume recorded protocol fixtures. Native dependency discovery belongs to Linux build
tasks and must not break headless checks on other hosts.

Required automated coverage:

- Configuration defaults, mixed authored/Desktop picks, reserved ids, non-Linux retained selection, Apply/Cancel,
  source draft rebasing, and status labeling. All existing authored theme resolutions remain unchanged.
- Portal absent keys, no preference, bad types/accent ranges, initial-read races, owner replacement and reconnect.
- Automatic GNOME token detection, explicit-source isolation, partial palettes, missing variants and recovery.
- GTK 3 named colors, GTK 4 old names, modern libadwaita variable overrides, missing-variable sentinels,
  translucent inputs, and no presented native windows. Record native library versions with evidence.
- Complete role mapping and actual paint-pair contrast in both appearances and contrast modes, including shared
  foregrounds, accent states, menus, selection and disabled controls. Terminal/editor values remain byte-identical
  under GTK-only changes. Fixed terminal schemes survive appearance changes.
- Timeout, stalled process, malformed/oversized output, stderr pressure, retry bounds, stale generations,
  close-before-start and close-during-delivery. Equal palettes cause no reinstall.
- Transaction rollback, multiple windows, open Settings with dirty draft, focused input, selection and scroll.
  Gallery renders cover default and Enterprise kits with full, partial and fallback palettes.

Run `./gradlew check` and headless package verification without AOT training. Native acceptance is user-run:
GNOME GTK 4/libadwaita, a GTK 3 desktop, and KDE; Wayland and X11 where available; switching sources, accents,
Light/Dark and high contrast; restarting the portal; missing toolkits; and preserving focused editor/terminal state.
Measure cold startup and live switching for stalls/flicker. Headless success does not establish native acceptance.

## 9. Implementation sequence and review boundary

One runnable deliverable: the optional Desktop colors theme including all source choices and fallbacks. The plan
starts with native extraction/protocol tests, then the pure resolver, application lifecycle integration, Settings,
packaging and full verification. It must establish the GTK 4 CSS-variable probe on supported runtimes before UI
integration, and record any inability to resolve unmapped widget colors as a design issue rather than silently
shipping a named-color-only modern backend. Implementation starts after written-spec and plan review.

## References

- [XDG Settings portal](https://flatpak.github.io/xdg-desktop-portal/docs/doc-org.freedesktop.portal.Settings.html):
  standardized preferences and change notifications.
- [GTK 3 named colors](https://docs.gtk.org/gtk3/method.StyleContext.lookup_color.html).
- [GTK 4 CSS properties](https://docs.gtk.org/gtk4/css-properties.html): custom properties since 4.16.
- [GTK 4 foreground lookup](https://docs.gtk.org/gtk4/method.Widget.get_color.html): public resolved-color API since 4.10.
- [GTK 4 legacy lookup](https://docs.gtk.org/gtk4/method.StyleContext.lookup_color.html): deprecated in 4.10,
  used only by the older named-color compatibility path.
- [Libadwaita CSS variables](https://gnome.pages.gitlab.gnome.org/libadwaita/doc/main/css-variables.html): paired
  colors and modern overrides; compatibility named colors do not reflect all variable overrides.
