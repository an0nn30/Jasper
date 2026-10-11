# Settings composition and system appearance amendment

Approved controller rulings, 2026-10-11. This amends Theme Engine Plan 3 Task 9; original reference and behavior obligations remain binding.

## Deliverables and ownership

Task 9a delivers the shared Settings shell/navigation/header/footer, reusable controls, and actual Appearance, Window and Notifications pages. Task 9b delivers Keymap recording and Plugins bodies, consuming9a contracts. Task 9c delivers palette, choosers, theme/restart/notices and remaining explicitly inventoried dialogs. These three deliverables precede Tasks6–8; each is independently reviewed. Root owns native GUI verification. Headless success is neither native nor pixel acceptance. Native Vault title-pointer/footer/traversal debt remains separate.

Shared geometry, typography, artwork, control painting and layout belong to jasper-ui. Settings-specific composition must not change established DEFAULT/DETAIL/hero/SHEET/FULL/panel semantics. App owners retain draft/domain/navigation/lifetime behavior. SDK extensions, when necessary, are mirrored through host and testkit. Existing Apply/Cancel/error/rebase/search/back-forward/page-scroll and focused native editor attachment survive; no reconstruction solely for restyling. Notification thresholds remain independent of OS delivery, unknown and explicit-default entries retain their semantics, and mock numeric values are fixtures rather than new defaults.

## Persisted theme policy

Add opt-in `ui.theme.follow_system=false`, retaining `ui.theme.name` and aliases. Add `ui.theme.source=catalog|bundled`, default catalog: catalog means exactly the existing case-insensitive installed-name precedence; bundled resolves only actual authored bundled sources. Unknown bundled names retain their requested value with honest missing-source diagnostic/fallback, never a same-name installed substitute. Existing absent/default source configuration and arbitrary names retain their meaning.

Dark/Light tiles exit system policy. They retain the resolving manual name and origin when its authored polarity matches; otherwise they stage the corresponding actual Jasper Dark/Jasper Light bundled name and bundled origin. Match System retains both remembered manual fields, chooses the real bundled source directly from ongoing OS preference, and never guesses custom pairs or inverts colors. A named chooser selection stages its explicit name/origin and exits system mode. Disambiguate actual bundled choices when a catalog name shadows them; imported themes use catalog. Origin is part of the coupled editor, never an implementation-facing Source form row. Only meaningful duplicate-choice labels and missing-source diagnostics expose it.

External custom-name edits set source=catalog (or remove it) and follow_system=false. Explicit bundled plus unknown name remains a missing bundled request. Coupled reset restores schema defaults. Independent workspace names retain catalog semantics; workspace match follows effective chrome. OS changes never write configuration, mark Settings dirty or rebase its draft. An unavailable observer preserves intent and uses the remembered manual source with diagnostic; recover when valid observations return.

## Native observation and lifetime

A concrete platform-owned closeable monitor exposes initial DARK/LIGHT/UNAVAILABLE preference and ongoing changes. On macOS observe application effectiveAppearance through public KVO and classify Aqua/DarkAqua with best-match; never observe Jasper's explicitly themed windows. Application lifetime owns the monitor and EDT-coalesced/stale-guarded publication. Startup, config/catalog refresh and OS updates share source resolution; theme installation remains transactional with existing rollback and retained sources. Failed startup/close-before-registration releases native observers and JNI references.

Packaged and development/installDist routes must support observation without weakening notification/Dock bundle identity checks. Deterministic native/Java tests do not establish real KVO delivery. Root verifies actual ongoing dark→light→dark with multiple windows, open Settings/unapplied edit, focused editor/selection/scroll and independent workspace, restoring original OS preference afterward. All unresolved native/pixel evidence remains explicit.

## Verification

Use real SettingsDialog/page owners and synthetic registries/config. Compare the three untouched reference crops at equal device scale with ICC treatment retained; capture default/light/custom/enlarged/narrow/short/popup/error states. Check affected established families after shared changes. Preserve raw full-gate XML and frozen input hashes immediately after check/installDist/themeSurfacePreview/gallery2x; record independent review and remaining native debt separately.

## Task9a review corrections

A modified theme selection retains its complete name/origin/follow-system tuple through external rebase and save expectations, including companions that originally matched the baseline. The tuple becomes clean only when all requested saved values match; an unchanged selection stays clean. Coupled reset protects absent values in the same way. Other settings retain independent per-key semantics. Import remains install/reload only.

Full-span editors expose stable reveal targets and propagate search highlighting to their actual rows, including empty notification registries. Explicit search reveal supersedes pending page-position restoration after layout, while ordinary Back/Forward preserves remembered positions and retained editors. Shared segmented choices add rows when their full labels cannot fit the available width, preserving button identity and keyboard selection; ordinary single-row allocation remains unchanged.

The package restores the signed appearance bridge from staging after jpackage, then verifies byte equality, architecture, signature and both JNI entry points alongside the existing bridges. Headless package verification does not establish live observation.
