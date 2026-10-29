# Protean startup splash design

**Status:** Composition, unified theme behavior and upward queue motion approved in conversation on 2026-10-29; implementation pending. Dimensions, timings and font choices below are implementation defaults. This amendment replaces the standard theme-dependent splash while retaining explicit presentation-plugin branding.

The user supplied `/Users/dustin/Downloads/Protean Terminal Startup Screen.png` and approved one Protean splash across every interface theme, including light, dark, imported and Desktop themes. The approved composition places a large, unboxed chevron/underscore mark on the left and **Protean** with **The terminal, transformed.** on the right. A live four-row startup queue sits below the tagline, with the actual build line beneath it and a red progress bar along the bottom. Jasper remains the application's identity outside this splash. Explicit plugin branding retains its supplied splash; an interface theme alone never changes the default splash.

## Appearance

Use an 840 × 480 logical-pixel canvas (1.75 aspect ratio), with the reference's near-black background, restrained red glow, white title and silver/red dimensional mark. Reconstruct the mark from the real shape elements of `packaging/icons/protean-source/Protean.svg`, excluding the rounded app-tile background and embedded C2PA metadata. Keep vector geometry so the mark remains sharp at desktop scales. No screenshot is used as a flattened UI asset.

Scale the reference to the 840 × 480 canvas: mark bounds approximately x=80–292 and y=134–302, right column starts at x=348, title baseline 215, tagline baseline 255, queue baselines 294/314/334/354, build baseline 379. Title is approximately 96 logical pixels (fit to the 450-pixel column), tagline 32, queue 13 and build 12; bottom bar runs x=29–812 around y=442. Preserve the reference’s generous upper whitespace and large title rather than centering a smaller heading near the top. Use platform sans-serif for title/tagline and the already bundled Cousine Nerd Mono for queue/build; add no font dependency. Match the reference through headless render review, retaining clear space between mark and text. Clip/ellipsize long queue labels and build metadata at code-point boundaries, with platform fallback for missing glyphs.

The first row is active, with bright white text and a red dot; the next three rows are dim upcoming steps. There are no fake sample plugin names, checkmarks, or empty placeholder labels. Near startup's end, render only remaining rows. The bottom bar is red, approximately six logical pixels high, and reflects the actual current step fraction. Use `BuildInfo.current().buildLine()` rather than sample build values.

## Queue and motion

The complete queue is available before opening from `PluginSelection.loading()`: Starting Java runtime…, Reading configuration…, each Loading plugin: <name> in resolved startup order, Restoring workspace…, and Starting Protean Workstation…. Existing startup notifications drive the active index; this display does not perform or invent startup work. Explicitly branded splashes retain their product name and existing single-line content.

When the active step completes, it slides upward and fades out while the next step rises into its place. Use a 240 ms eased transition with a monotonic elapsed-time clock, sampled near 60 Hz. Transition frames are visual only. A new update during motion immediately retargets to the newest real step, discarding intermediate animation debt. No minimum display duration, wait, startup sleep, queued animation backlog, or delayed close is introduced. Completion/failure closes immediately, even mid-transition. With animations disabled, the new active row appears immediately and no animation worker runs.

## Ownership and compatibility

Retain the existing plain AWT window, native direct painting and paint lock, avoiding Swing look-and-feel initialization before composition. Reuse the splash-owned daemon for motion while the EDT is busy. All state read by daemon painting is under the same lock. Stop scheduling before native disposal outside that lock; repeated close and failed open remain harmless. Startup excludes headless, warm-up and other existing suppressed launches before decoding artwork or starting workers.

New standard geometry is per painter, not a replacement of legacy branded constants: branded splashes retain 720 × 440. Buffer allocation, clipping, centering and native bounds use the selected painter's dimensions. Preserve fractional/device-scale behavior and target transforms. Default selection needs no theme library scan, toolkit appearance observation or theme-specific image decode. Remove obsolete default selection/artwork only after checking references; preserve branded resources and tests.

## Acceptance

Headless tests prove queue order and truthful progress, rapid retargeting, disabled motion, close/failure cleanup, branding precedence and launch suppression. Inspect exported static and transition frames at 1×, 2× and 4×, plus fractional-scale buffer parity. User-run desktop acceptance covers ordinary and very fast startup, slow plugin loading, animations disabled, themes and an explicit branded look. No unattended GUI launch or AOT training is authorized. Local headless packaging remains blocked by the installed non-JCEF JBR; retain the required JCEF runtime gate rather than installing or weakening it.
