# Moray Plan 3.5a — Coordinated Themes and macOS Title Bar

Date: 2026-09-11. Execution design for the user's Plan 3.5 go-ahead. Authority: Phase 1 spec and the Plan 3.5 roadmap. This is the first runnable deliverable; toolbar artwork/layout remains a separate discussion in Plan 3.5b.

## Outcome and visual direction

Use FlatLaf with Moray dark/light defaults and update the terminal alongside the controls. Add a custom-colored macOS title surface around native traffic lights, with the shell/tab title centered and clipped. Initially keep the toolbar and tabs in their existing separate rows. The user has an open optional choice about moving tabs into the title bar; incorporate a reply before title-bar implementation if it arrives.

First-pass design: dark terminal/panels #282c34, title/toolbar/tab-strip surface #21252b, text #abb2bf, muted #8b929f, accent #61afef; light terminal/panels #fafafa, title/toolbar/tab-strip surface #eaeaeb, text #383a42, muted #696c77, accent #4078f2. Selected tabs use the terminal background; subtle borders separate surfaces. Use focus/hover/disabled colors from theme defaults. These are Atom/One Dark- and One Light-inspired initial values, open to visual refinement after the user tries them. Retain built-in identifiers moray-dark and moray-light; default dark.

Dark cursor #abb2bf and selection #3e4451. Light cursor #526fff and selection #d5def5. Dark ANSI: 282c34,e06c75,98c379,e5c07b,61afef,c678dd,56b6c2,abb2bf,5c6370,ef7b85,a9d48a,f0cc8c,74bff8,d68bee,67c7d3,e6e9ef. Light ANSI: 383a42,e45649,50a14f,c18401,4078f2,a626a4,0184bc,a0a1a7,696c77,ca4035,3d7d3b,986801,315fc4,87218b,006b96,ffffff. ANSI foregrounds remain application-controlled; don't rewrite explicit truecolor to force contrast. Theme defaults, labels and states must stay readable.

## Terminal palette contract

TerminalView owns a mutable current Palette independent of immutable startup options. Public palette() and setPalette(Palette) are EDT-owned; setting null fails without partial mutation. Palette changes rebuild the painter and derived search colors and set the component background, then repaint. Every palette-dependent path (font rebuild, cursor, selection, dim overlay, search highlights, run cache) uses the current palette. Do not recreate fonts, resize the PTY, invalidate selection/search/viewport, restart workers, or clear content simply for a color change. Explicit RGB cells retain RGB; indexed/default colors recolor, including existing scrollback. Keep emulator/style data untouched.

Palette retains public morayDark() and adds morayLight(). Validate all required colors and all 16 ANSI entries; immutable copy remains. No app or JediTerm types in public APIs.

## App theme ownership

A concrete ThemeController owned by MorayApplication tracks BuiltinTheme and registered WindowContent owners. All operations run on EDT. It installs the selected FlatLaf plus packaged custom defaults before windows are created. WindowContent registers once and unregisters on close. Tests can share a concrete controller across real headless owners; no fake window interface or static global owner registry.

Theme selection is application-wide: install LAF, update real component trees and all owned hidden/detached pane trees, apply terminal palettes, refresh focus borders and native window appearance, update menu selection, and repaint/revalidate. Preserve session identities, tab selection, split ratios/zoom, search and fonts. Avoid reparenting as part of a theme change and suppress divider writes caused by UI-delegate replacement; restore model ratios after layout. New owners and delayed shell completions use the controller's current theme, not whichever theme was active when launch began. Close removes callbacks/registration. Existing constructor helpers may delegate to a private controller for compatibility, but production passes one shared app controller.

Store FlatLaf overrides in application resources (FlatLaf.properties, FlatDarkLaf.properties, FlatLightLaf.properties) registered before setup. BuiltinTheme supplies identifier, label and terminal palette. Use theme-specific defaults, not accumulated UIManager.put overrides. Theme loading failure must leave the previous selection usable and report an error; don't silently mark a failed switch selected. Existing Settings/Reload remain disabled and status stays Built-in defaults.

## macOS window integration

Use supported macOS full-window-content client properties on JRootPane before pack: apple.awt.fullWindowContent=true, apple.awt.transparentTitleBar=true, apple.awt.windowTitleVisible=false. Keep JFrame decorated; native traffic lights, dragging, resizing and full-screen behavior remain owned by macOS. Do not implement synthetic window-control buttons or manual mouse-drag relocation. Other platforms retain their existing native decorations and no extra title row.

MacTitleBar is a lightweight panel only on supported macOS. It contains noninteractive centered/clipped title text and native-button safe spacing driven by FlatLaf.fullWindowContent.buttonsBounds (with the pinned fallback of 68×28 until native bounds arrive). Reserve symmetric leading/trailing space for a centered title; height at least 28 logical pixels and accommodates reported bounds. Bounds changes/full-screen transitions revalidate the header and window minimum. Long titles must not grow minimum width. Match active/inactive theme colors. Do not add mouse listeners to the title surface that steal native gestures.

Set apple.awt.application.appearance=system before AWT initialization for native support; app theme selection is manual. The pinned JBR source exposes apple.awt.windowAppearance on JRootPane; set NSAppearanceNameAqua/NSAppearanceNameDarkAqua per window to match the manual theme. No new native dependency. Keep frame title metadata for Dock/window menus/accessibility while hiding its native drawing; custom label follows current tab title. Detach installed root/listener callbacks on close.

## Sources and verified local contracts

- [FlatLaf macOS](https://www.formdev.com/flatlaf/macos/) documents full content, transparent title, hidden native title and startup appearance ordering.
- [FlatLaf customization](https://www.formdev.com/flatlaf/how-to-customize/) documents registered theme-specific properties.
- FlatLaf 3.7 source jar: FlatClientProperties.FULL_WINDOW_CONTENT_BUTTONS_BOUNDS and FullWindowContentSupport provide live native bounds and 68×28 fallback. Use the public property, not internal native helper classes.
- Installed JBR25 src.zip, java.desktop/sun/lwawt/macosx/CPlatformWindow.java: mac client properties above and WINDOW_APPEARANCE dynamically call nativeSetNSWindowAppearance. Do not depend on com.jetbrains APIs absent from this runtime's source distribution.

## Verification and limits

TDD for live palette rendering/retained state; multi-owner theme changes including hidden, zoomed and pending panes; close registration cleanup; theme resources/contrast; custom title panel sizing/metadata/active state and mac property configuration. Headless buffered-image previews of actual Swing components are allowed. No agent GUI/benchmark launch under AGENTS.md. User native acceptance must cover traffic lights, drag/double-click, full screen, safe spacing, scaling, light/dark system combinations, menu integration and multiple windows. No headless assertion proves those native behaviors.

Toolbar remains functional with its existing icons during this deliverable. Next show colored icon/layout alternatives in context and record the user's choice before implementing Plan 3.5b. Config files, persistence, automatic appearance and packaging remain Plan 4. Terminal hardening backlog stays recorded.
