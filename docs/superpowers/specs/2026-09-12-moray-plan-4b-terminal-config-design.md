# Plan 4b — full terminal configuration

The user approved the next scope: typography, terminal input/behavior, session defaults and initial window dimensions. This is an architectural extension of the reviewed Plan 4a snapshot/service/controller, with implementation continuing under the established Superpowers workflow. Base: main `ab672fa`; isolated branch `codex/plan-4b-terminal-config`, `.worktrees/plan-4b`. Baseline: 394 tests, 393 passed and one known font skip.

## Scope and approach

Extend existing terminal options and the existing configuration controller. Do not add a second settings or rendering engine. Compared with exposing only construction-time options, live application makes saved changes useful immediately. Recreating sessions on reload would discard user work; retain sessions and use new launch defaults only for future requests. This deliverable covers all four approved groups. Custom theme files/system appearance, logging/environment scrubbing, app packaging, SSH and plugins remain subsequent work.

## Settings and validation

Existing Plan 4a settings and per-field override policy remain. App font size stays 16; the terminal library standalone default/reset stays 14.

| Key | Default | Accepted values / effect |
|---|---|---|
| window.columns | 150 | integer 5–500; desired initial grid of new windows |
| window.lines | 45 | integer 2–200; desired initial grid of new windows |
| font.family | JetBrains Mono | nonblank string without NUL; live |
| font.size | 16 | existing finite 6–72; live |
| font.fallback | [Symbols Nerd Font Mono, Apple Color Emoji] | array of nonblank strings without NUL; empty allowed; live |
| font.ligatures | true | boolean; live |
| font.line_height | 1.0 | finite 1.0–3.0 multiplier; live |
| terminal.shell.program | empty string | empty uses current DefaultShell resolution; otherwise one executable string without NUL, not whitespace-only |
| terminal.shell.args | [] | string array without NUL; exact argument boundaries, no shell parsing |
| terminal.env | {} | string values without NUL; portable names [A-Za-z_][A-Za-z0-9_]*; merge into inherited environment |
| terminal.scrollback | 10000 | integer 0–1000000; new panes only |
| terminal.option_as_meta | left | left/right/both/none; live |
| terminal.cursor.shape | block | block/beam/underline; live fallback when no program override |
| terminal.cursor.blink | true | boolean; live fallback when no program override |
| terminal.dim_inactive_panes | 0.3 | finite 0–1; live |
| terminal.copy_on_select | false | boolean; live |
| terminal.bell | visual | visual/sound/none; live |

TOML supports both inline and nested shell/cursor/env tables. Unknown nested keys warn with positions, including empty unknown tables and quoted dotted keys. Known type errors, including wrong array element types, reject the whole candidate and preserve last-good. Correctly typed invalid values default only the affected field. An invalid fallback/args list defaults that list as a unit; invalid environment entries are omitted individually while valid entries apply. TERM and COLORTERM entries warn and are omitted: Moray enforces xterm-256color/truecolor. Diagnostics never echo environment values or shell arguments. Preserve the existing binding-map policy. All records defensively copy collections and validate direct construction. Missing font families use JBR fallback behavior; no installed-font enumeration is required during parsing.

## Live terminal API and geometry

Extend TerminalOptions with lineHeight and BellMode, retaining the existing ten-argument constructor. Add a public BellMode enum (VISUAL, SOUND, NONE), TerminalView.options() and TerminalView.applyOptions(TerminalOptions). TerminalOptions remains free of JediTerm types. Live application updates typography, palette and behavior on EDT; it never replaces the session or changes the session's scrollback capacity. Existing setFontSize/setPalette keep the exposed options coherent, and size changes reuse the latest family/fallback/ligature/line-height choices.

FontSet keeps its existing constructor and adds a line-height overload. Compute the natural height exactly as before, then cellHeight = max(naturalHeight, ceil(naturalHeight * lineHeight)); ascent = naturalAscent + floor((cellHeight - naturalHeight) / 2). Width is unchanged. Every coordinate consumer must use these shared metrics: painting, cursor, hit testing, selection/search highlights, mouse reports, minimum/preferred size and PTY resize. A 1.0 multiplier preserves current metrics. Applying equal options avoids unnecessary rebuilding/resizing. Normal width reflow may invalidate row-based selections as documented; unrelated behavior or config changes must not clear terminal state.

Changing Option-as-Meta refreshes the existing KeyEncoder, preserving application shortcut routing. Configured cursor defaults must not override program DECSCUSR choices; RIS restores configured fallback behavior. No renderer/backend abstraction or new custom interface is introduced.

## Bell lifecycle

Consume existing TerminalSession.Listener.bell. Visual bell paints a short foreground-color overlay at 15% opacity for 150ms; SOUND invokes an injectable Runnable whose production default is Toolkit.beep; NONE does nothing. Events come from the reader thread, are coalesced before EDT delivery and only affect attached views. Keep one nonrepeating timer per view, restart it for repeated visual bells, and clear/stop it on detach or mode change. Queued callbacks must reject an old attachment generation so close/reparent cannot produce late flashes or sound. Tests use injected sound and controlled timer completion, never real audio or sleeps. Existing blink/frame timer hardening outside this feature remains deferred.

## Saved fields and runtime overrides

ConfigSnapshot groups font fields in FontConfig and terminal fields in TerminalConfig, retaining the old six-argument constructor and fontSize() convenience accessor for existing fixtures. A snapshot builds TerminalOptions from a supplied effective size and palette. WindowContent compares saved fields; font-family/line-height/behavior changes retain a pane's manually adjusted size when font.size did not change, and use its current palette so manual global theme choices survive. Changed font.size applies across every retained/hidden/zoomed view; FONT_RESET uses the saved size. Pending/new views use the latest live settings when ready. Store configured dimming on TerminalPane so focus/theme changes do not restore the old hard-coded 0.3.

## Launch and initial window sizing

LaunchSettings is an immutable app-owned command/environment/grid/scrollback snapshot. Capture it on EDT at the launch request, before scheduling the worker, including exact shell label. A subsequent reload cannot change a queued request. Existing panes retain their shell, environment, scrollback and label. New requests get current session settings. A new window captures its requested columns/lines once; later changes do not resize existing windows or change that window's captured initial grid.

For an empty shell program, use DefaultShell.command(osName, inheritedEnvironment) and append configured args. For a custom executable, use [program] + args with no implicit login flag or string splitting. terminal.env affects the child environment, not the choice of the default login shell. TerminalSession.start still enforces TERM/COLORTERM. Failures use existing pane launch error handling; no executable probe or process launch runs on EDT.

Preserve ShellLauncher's legacy constructor/callback behavior. Its configured constructor takes a standard Supplier<LaunchSettings> and BiFunction<Path,LaunchSettings,TerminalSession>. launch returns the captured shell label, while completion remains EDT-delivered. TerminalPane stores that returned label.

InitialWindowSize computes the first terminal pane's preferred pixel area from captured font metrics and desired grid plus the current 24px padding per side. Set it before frame.pack; chrome/decorations are added by Swing layout. Clamp the packed frame to the current usable display bounds and existing minimum constraints; do not repack when a delayed shell arrives or on subsequent reload. Native frame behavior remains user-run; headless tests cover arithmetic, actual content/root layout and lifecycle. Existing unconfigured preview/test constructors keep their prior geometry unless initial size is explicitly supplied.

## Verification and limits

Four sequential tasks with TDD and separate reviews: terminal live API/metrics/bell; immutable config/parser/template; launch capture/initial sizing; live owner integration/documentation. Exact cross-task APIs are defined in the plan. Tests exercise real TerminalView/FakeConnector behavior, actual TOML files and WindowContent/JRootPane integration, queued launches, no session replacement, override preservation, native text editing and shutdown. Full checks, source hygiene and final whole-branch review follow. No GUI/benchmark/editor, actual audio, real user config writes, merge or push without existing authorization. Native and cross-platform acceptance remain explicitly unverified until run. Do not commit directly on main; include coauthor trailers.

Implementation clarification: array validation points to the containing key’s exact source position. TomlJ element positions include preceding whitespace/comments, so they are not used as token locations. No custom source scanner is introduced.
