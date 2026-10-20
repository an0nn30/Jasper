# Editor fonts and Markdown preview

**Date:** 2026-10-04; amended 2026-10-20

**Status:** Conversational design and October 20 font/toolbar additions approved; amended written specification awaiting user review. Implementation has not started.

**Baseline:** Original design at `d82efd6a`, on `codex/editor-markdown-preview`. Implementation planning must refresh against current main, including SDK 0.18.2 presentation queries and kit-provided Editor font defaults.

**Delivery:** One runnable deliverable and one subsequent implementation plan.

## 1. Intent and authority

Let a user customize Editor typography or follow the saved terminal font settings, and read Markdown as a formatted
document beside its source or filling the editor. A small group of icons at the top switches views, in the style of
the familiar IDE Markdown workflow.
Success means readable documents, immediate feedback while writing, and switching views without losing editing
state. The existing Editor remains responsible for files, saving, conflicts, undo and Vim mode.

This extends the [Editor design](2026-09-27-jasper-editor-plugin-design.md) and its
[workspace-tab amendment](2026-09-27-hosted-workspace-tabs-design.md). It follows the
[application architecture](2026-09-20-jasper-app-buddy-refactor-design.md), the
[SDK architecture](../../sdk-architecture.md), and the existing workspace-glass contract. Historical separate-window
descriptions do not override current app-hosted document tabs. Terminal font settings are consumed through the SDK;
terminal rendering and Buddy behavior remain outside this change.

The user approved these decisions during brainstorming:

- Editor font family, size, ordered fallback fonts, ligatures and line height, with an opt-in Follow terminal font
  settings choice that updates live and preserves independent Editor values.
- A document toolbar below the tab strip, ready for later Editor actions, with classic/modern IntelliJ artwork
  selected by the active presentation.
- Source, Split and Preview modes, with source left and preview right in Split.
- Remember the last explicitly selected mode across Markdown files and restarts; initially Source. Existing tabs
  retain their individual modes.
- Bidirectional synchronized scrolling in Split, enabled initially, with an independent-scrolling toggle.
- CommonMark parsing and a native Swing preview, rather than an embedded browser.
- Live preview of unsaved text; the preview is read-only, with selection, copying and links.
- Everyday Markdown, tables, task lists, strikethrough and highlighted fenced code. PNG, JPEG and GIF images.
- Relative images from local files or the same SFTP host load automatically. Web-hosted images load only after
  the user chooses Load external images for that document. This was the recommended option in the approved section.
- Relative Markdown links open in the Editor; heading links navigate within a preview; web links open in the browser.
- Preview-focused Find searches rendered text. Replace and Go to Line reveal the source in Split without changing
  the saved default mode.
- Background parsing/loading, stale-result rejection, cancellation, theme integration, headless verification,
  and user-run native desktop acceptance.

The concrete limits, ownership rules, URI handling and API hook below make those decisions implementable. They are
part of this written-spec review, not claims of completed implementation or separately approved UI changes.

## 2. Scope and alternatives

Use CommonMark Java plus a controlled Swing HTML presentation. Keep the existing RSyntaxTextArea instance as the
editable source. This fits Jasper's runtime, theming and headless Swing tests. Swing's HTML support is older than
a browser's; the implementation must generate a deliberately supported subset and inspect the rendered result.
Do not equate successful HTML generation with correct presentation.

An embedded Chromium/JCEF renderer was considered and not selected. It would support modern browser layout but
would add native runtime packaging and browser lifecycle work. Jasper's trimmed packaged runtime currently omits
JCEF. The chosen design does not add a browser, JavaFX, a local HTTP server, or runtime downloads.

Not included: rich-text editing, interactive task checkboxes, SVG/WebP, animated GIF playback, arbitrary embedded
HTML layouts, JavaScript, CSS supplied by documents, Mermaid, math, export, printing, a table-of-contents sidebar,
or restoring open tabs. GIF displays its first frame. Raw HTML is shown as escaped literal text; HTML comments
are omitted. Unsupported diagram/math syntax remains ordinary Markdown text or code.

## 3. Interaction and state

### 3.1 Eligibility and toolbar

A document is eligible whenever its selected syntax language is Markdown, including an untitled document selected
as Markdown manually. Existing `.md` and `.markdown` detection is retained. Non-Markdown documents have no preview
controls or preview work; an empty document toolbar collapses until it has actions to show. Changing away from
Markdown shows Source and releases preview resources;
switching back in the same tab restores that tab's previous Markdown mode.

Place a compact document toolbar inside the Editor tab directly below the outer tab strip, above any file-change
banner and the editor content. Its first action group is the right-aligned Markdown view controls; later Editor
actions can occupy the same toolbar without changing the content layout. It remains reachable in all three modes.
Use the existing SDK icon catalog roles below, selecting their classic or modern IntelliJ artwork according to
the active presentation. Planning must verify both catalog variants and the supported SDK icon access path:

| Action | Catalog icon | Tooltip / accessible name |
| --- | --- | --- |
| Source | `GENERAL_EDITOR_ONLY` | Source |
| Split | `GENERAL_EDITOR_PREVIEW` | Source and preview |
| Preview | `GENERAL_PREVIEW_ONLY` | Preview |
| Linked scrolling | `ACTIONS_SYNCHRONIZE_SCROLLING` | Synchronize scrolling |

Mode buttons are mutually exclusive toggles with a visible selected state, keyboard focus and accessible names.
The scroll toggle is shown only in Split. Tooltips spell out the meaning rather than relying on icons alone.
The same three mode actions are available through the Editor's existing command presentation; no default keyboard
shortcuts are added. Controls are unavailable while initial document loading has failed or is still pending.

Split uses a draggable vertical divider, starting at 50/50. Each tab remembers its divider ratio while open,
including mode changes and moves between windows; it is not persisted across restarts. Each side has its own
scroll pane. Resizing preserves the ratio within available space. Narrow widths may require horizontal scrolling;
they do not silently change the mode.

### 3.2 Mode ownership and persistence

- Each eligible tab owns its actual mode. On first becoming eligible, it takes the plugin's saved default.
- An explicit toolbar/menu mode action updates both that tab and the saved default, even if the requested mode
  is already active. Other open tabs do not change.
- Command-driven source reveals, link navigation and changing syntax language do not update the saved default.
- The last explicit mode is a plugin-wide preference, shared across Jasper windows and saved using declared plugin
  settings as `markdown_view_mode`, with values `source`, `split`, `preview` and default `source`. It lives in the
  Editor's existing settings file and appears in Settings > Tools > Editor under Markdown as Default view.
- Changing this setting affects newly eligible tabs, not existing tabs. Writes are asynchronous and serialized
  so rapid clicks cannot persist an older choice over the latest. A failed write leaves the session choice active
  and reports the existing plugin settings error path.
- Linked scrolling is per-tab, defaults to on for each new tab, and survives mode changes and window moves only.
  Toggling it does not introduce another saved preference.

Mode changes reparent or reveal the existing editor component; they do not replace its document or reset Vim,
undo, caret, selection, folds or source viewport. Independently scrolled panes retain their positions. When sync
is enabled, entering Split aligns the preview to the retained source position; subsequent linked scrolling may
move the other viewport. Leaving Preview for Source restores the stored source viewport rather than treating the
preview's selection as an edit cursor.

### 3.3 Focus and commands

Source and Split focus the source when explicitly selected; Preview focuses the preview. Window activation or
moving a tab restores its last valid focused component, not a hidden source editor.

Preview supports standard selection, Copy, Select All, scrolling and keyboard link activation. Cut, Paste, Delete,
Undo and Redo do not mutate the hidden source while focus is in the preview. Vim captures only source-editor input.
Save, Save As and closing continue through the same `DocumentSession`, including existing dirty prompts.

Find targets whichever content pane last had focus before the find field received focus. Its target remains stable
while typing in the find bar. Preview search uses the rendered Swing document's visible text, including code but
excluding HTML tags and synthetic mapping markers. Preserve the existing case, whole-word, regex, next/previous,
match count and Escape semantics. Invalid patterns produce the existing inline error behavior. Refreshing a preview
recomputes search matches for the new rendered document.

Replace and Go to Line, when invoked from Preview, switch this tab to Split, focus the source, and then run the
existing command. Source-mutating Editor commands must never silently operate on a hidden source. Explicit such
commands reveal it first; ordinary typing/paste in the read-only preview remains inert. These temporary reveals do
not change `markdown_view_mode`. The preview's status must not imply that its selection is a source caret; the
existing line/column fields remain the source caret's position, labeled as source while Preview is active.

## 4. Rendering and appearance

Bundle `org.commonmark:commonmark:0.30.0` with matching `commonmark-ext-gfm-tables`,
`commonmark-ext-gfm-strikethrough` and `commonmark-ext-task-list-items` artifacts, scoped to the Editor plugin's
runtime classpath. These coordinates/version are documented by the upstream project at design time. The plan must
verify resolution and license inclusion before coding against them; any version change must be recorded. Core
angle-bracket autolinks work; automatic linkification of arbitrary bare URLs is not an additional extension in v1.

Render headings, paragraphs, emphasis, strong emphasis, inline code, fenced/indented code, ordered/unordered lists,
quotes, horizontal rules, links, images, tables and task lists. Task marks are static visuals rather than form
controls. Table alignment is respected. Long tables and preformatted code remain readable via horizontal scrolling;
ordinary prose wraps to the viewport. Images scale down to fit while retaining aspect ratio and never force the
whole document wider. Unknown fenced-code languages display as plain monospace text.

Reuse RSyntaxTextArea's bundled tokenizers and Jasper's syntax-color mapping for code highlighting, using isolated
tokenizer instances over snapshot strings. Do not run a live source text area's tokenizer or touch Swing components
from a worker. Extract only the small shared token-role mapping if needed; this is not a general editor refactor.

Use a non-editable `JEditorPane` with a controlled `HTMLEditorKit`/view factory. Generated markup contains mapping
anchors and application-owned resource identifiers, never live network or file URLs in image `src` attributes.
Custom image views paint already-loaded images or placeholders; Swing's default image loader must not bypass the
resource policy. Raw document HTML never reaches the HTML parser unescaped. Generated attributes and text are
escaped independently. Links dispatch through application-owned destination metadata rather than evaluating markup.

The preview uses the current workspace background, foreground, accent, selection and syntax palette. Body text uses
the current proportional UI font family at the effective Editor font size; code uses the effective Editor font,
fallback order and ligature setting. Body and code line spacing use the effective line-height multiplier.
Headings and spacing scale from that base. Theme, font and workspace-opacity changes apply live. Preserve the
existing glass behavior: one background fill per surface, translucent when appropriate, with readable text, images,
selection and code backgrounds. No hard-coded white page or unrelated CSS theme.

Source references: [CommonMark Java](https://github.com/commonmark/commonmark-java) documents the parser, extensions,
source spans and HTML-escaping options; [Swing HTML
kit](https://docs.oracle.com/en/java/javase/25/docs/api/java.desktop/javax/swing/text/html/HTMLEditorKit.html)
documents the native HTML presentation model. The layout and lifecycle policy here are Jasper's design decisions.

### 4.1 Editor typography and following the terminal

Settings > Tools > Editor > Font exposes **Follow terminal font settings** (`follow_terminal_font`, default false),
Font (`font_family`), Font size (`font_size`), Fallback fonts (`font_fallback`), Ligatures (`font_ligatures`) and
Line height (`font_line_height`). Size accepts finite fractional points from 6–72 and line height a finite
multiplier from 0.5–3.0, matching terminal settings. Fallback is an ordered list of nonblank family names without
NUL; an empty list is valid. Family selection retains the Editor's installed-font picker. New independent defaults
are an empty explicit fallback list, ligatures off and line height 1.0, preserving existing rendering; existing
family/size values and kit defaults remain effective. Existing integer size values load as the same point size.

When following is off, all five Editor values are editable and apply live to every open Editor document. When on,
the controls display the effective saved terminal values and are disabled; the Editor's own values remain stored
unchanged. Turning following off restores those values, including kit defaults for fields without a saved override.
Following is plugin-wide across windows. It follows saved terminal configuration, not a terminal pane's temporary
zoom or whichever terminal currently has focus. New documents take the current effective settings immediately.

Expose an immutable terminal typography value and a change subscription through the supported SDK appearance
surface, implemented by the application and testkit. The value carries family, fractional size, ordered fallback,
ligatures and line height. Notifications arrive on the UI thread after the current value is updated. The Editor
reads this surface and owns/disposes its subscription with the plugin; it does not read app configuration files or
import app/terminal internals. Independent mode ignores terminal font changes. Final API names, SDK release and
consumer compatibility are determined against current main in the implementation plan.

Apply typography consistently to source text, gutter, caret geometry, selection, hit testing, wrapping, folds and
scroll mapping. A multiplier of 1.0 uses natural font metrics; lower values tighten rows, with the same potential
for overlapping or clipped tall glyphs as the terminal. Font changes preserve the source document, undo, selection
and Vim state and rebuild preview geometry. Ordered fallback must affect actual missing-glyph rendering, and
ligatures must preserve correct caret and selection positions. Verify these behaviors against RSyntaxTextArea's
layout before selecting the implementation; changing painted text alone is insufficient.

## 5. Data flow, ownership and threading

Keep focused concrete collaborators rather than enlarging `EditorTab` with parsing, resource loading and navigation.
Suggested owners, finalized into exact classes by the implementation plan:

| Owner under `dev.jasper.editor` | Responsibility and allowed direction |
| --- | --- |
| `markdown` | Parse immutable source, produce controlled HTML plus source spans, headings and resource/link descriptors; may use `syntax`, CommonMark and RSyntax tokenizers; no UI/session/provider dependency |
| `markdown.resources` | Resolve destination values and load/cache images; may use `api`, JDK HTTP/ImageIO and immutable `markdown` values; no UI or Remote imports |
| `ui` preview controller/view | Timers, latest-generation scheduling, mode/focus, Swing presentation and scroll mapping; may depend on the above packages and existing session/SDK collaborators |
| `ui.EditorWindow` | Wire commands, open linked documents through existing model/hosting, supply current owner and settings |
| `api.EditorDocument` and providers | Resolve a relative file on the original document's storage context; existing asynchronous bounded read contract |

Update affected package-info dependency descriptions. No new renderer interface with one implementation, exported
CommonMark types, application imports, or Editor dependency on Remote. Terminal typography adds the SDK appearance
value/subscription and host/testkit implementations described in section 4.1; Markdown rendering remains owned by the
Editor.

Live update sequence:

1. An EDT document listener marks the preview dirty and restarts a one-shot 250 ms timer. This is separate from
   the existing save/dirty notification timer; preview rendering neither saves nor changes dirty state.
2. With a visible Split/Preview in a ready tab, take a source snapshot and immutable style inputs on the EDT.
   Initial preview activation and explicit Retry render immediately rather than waiting for a keystroke.
3. Use the plugin background executor for parsing, code tokenization and markup preparation. Keep at most one
   render running plus one replaceable pending snapshot per tab; old keystrokes must not create an unbounded queue.
4. Results contain the revision/generation, controlled markup, source maps, headings and resource descriptors.
   Install a result on the EDT only if text, theme, document identity and tab lifetime still match. Swing document
   creation/mutation and layout stay on the EDT; no I/O occurs there.
5. Resource completion updates only the matching active generation. Restore the reading anchor after layout and
   after image-size changes. Coalesce layout/scroll work rather than adjusting after each individual event.

Typing, undo, redo, reload, Save As identity changes and syntax changes all invalidate the appropriate generation.
An external-file reload refreshes the preview even though the source replacement suppresses ordinary edit callbacks.
Saving unchanged text alone does not need a new parse. Source-only or inactive/hidden tabs suspend preview refresh
and cancel image work; reactivation renders current text if dirty. Moving a live tab is not final disposal and
retains its modes/editor state, while obsolete callbacks tied to the previous presentation owner are invalidated.

Disposal stops timers, removes listeners, cancels futures/HTTP requests, clears pending snapshots and releases image
and rendered-document references. Cancellation is cooperative; every late callback also checks lifetime/generation.
Failure or executor rejection never disables Source, Save or Close.

### 5.1 Bounded work

The source editor retains its existing `max_file_mib` opening limit. Preview additionally caps a snapshot at
2,097,152 UTF-16 code units, generated HTML at 16 MiB of UTF-8, and mapped block entries at 50,000. These are internal
v1 constants, not new settings. Exceeding any preview bound shows "This document is too large to preview" with a
Source action; it does not truncate the displayed document silently or refuse source editing. The plan must test
these guards before allocating a Swing HTML document. Rapid edits coalesce as above, and resource concurrency/cache
bounds below apply across the plugin. Timing acceptance is responsiveness under representative documents, not a
machine-dependent millisecond assertion in unit tests.

## 6. Synchronized scrolling and reading position

Ask CommonMark for block and inline source spans. Build ordered anchors that associate source ranges with rendered
element offsets; lists, quotes, table rows and code lines should supply useful interior anchors. Keep headings in
the same result for fragment navigation. Mapping markers occupy no visible text and do not pollute Copy or Find.

With linked scrolling enabled, the actively scrolled pane leads. Map the top visible position to its source range
and interpolate within that range into the other pane. Use actual source model-to-view coordinates, accounting for
soft wrapping, folds and fonts; do not equate a physical screen row with a Markdown line. A source line hidden by
a fold maps to the visible fold start. Empty documents and unmapped gaps use the nearest valid anchor and clamp
within viewport bounds.

Programmatic adjustments are guarded against feedback, including deferred viewport events. Source edits invalidate
the old mapping immediately; synchronization pauses until a matching result is installed, so stale source ranges
cannot drive scrolling. Divider resizing, fonts and image completion rebuild geometry, not source parsing unless
the render inputs also changed.

When replacing the preview, retain its top visible source anchor and within-block position, using Swing positions
or equivalent edit-adjusted offsets to track insertions/deletions ahead of that anchor. If the anchor is removed,
choose the nearest surviving source location. Preserve the bottom when the user was at the end. With synchronization
on, the currently leading pane decides alignment; with it off, neither pane is dragged to the other's position.
User scrolling takes precedence over a pending restoration. The guarantee is corresponding passage visibility,
not pixel-identical scrolling between differently laid-out content.

## 7. Images, links and storage context

### 7.1 Relative document resolution

The current `Locations` resolver can rebuild a remote document from saved host/root settings. That is suitable for
the chooser, but not sufficient to guarantee the exact route/login of an already-open SFTP document. Do not derive
resource locations by parsing display labels or remote keys, and never reinterpret a remote path as a local Path.

Add one backwards-compatible default method to the existing Editor API:

```java
default Optional<EditorDocument> resolveRelative(String path) {
    return Optional.empty();
}
```

Its contract: UI thread, no I/O; input is a nonempty decoded relative file path using `/`, with no URI scheme,
authority, query, fragment, NUL or backslash. `.` and `..` segments are permitted. Empty means unsupported; malformed
input is rejected consistently before providers run. Absolute paths/drive paths are not relative resources in v1.
The result is a document handle, not an open tab, and must preserve the original storage/authentication context.

`LocalDocument` resolves against its file's parent and retains its executor. `RemoteDocument` resolves against its
remote file's parent and retains its exact `ConnectionIdentity`, `EditChannels` and owner supplier. These are two
real implementations on the existing interface, with local and Remote tests. Other document providers can retain
the default and still render Markdown text and web images. Untitled documents have no base; show a save-first
placeholder for relative images and a notice for relative file links.

Relative resolution is an Editor API addition; terminal typography uses the SDK addition in section 4.1. Choose the
next compatible Editor version from current main and document the hook and the required SDK typography version.
Remote's implementation names only `dev.jasper.editor.api` as before. Its additive provider method
does not require invoking the new method on an older Editor; preserve its existing optional dependency compatibility
and bump its own patch version for the change. Verify that older-Editor loading still works rather than tightening
the dependency gratuitously. No application imports of Editor internals are introduced.

### 7.2 Destination interpretation

Classify the original link destination before resolving it. Support relative file paths, a fragment alone, and
absolute HTTP/HTTPS URLs. Percent-decode relative paths exactly once as UTF-8, preserving `+` literally; split off
the fragment first. Refuse malformed encodings, decoded control characters, schemes/absolute paths, and unsupported
query-bearing file destinations. Network-path references beginning `//`, `file:`, `data:`, `javascript:`, `mailto:`,
custom protocols and raw absolute filesystem paths receive an unsupported-destination notice/placeholder, not an
OS handoff. The renderer must not invent a base URL from the process working directory.

- A heading-only link scrolls in the current document without opening a tab.
- Relative Markdown links resolve through the document hook and use the existing open/de-duplicate/recents flow.
  Reusing an unsaved open tab never reloads it. A fragment is applied after the target has loaded and been parsed;
  in Source mode it positions the source at that heading, otherwise it positions the visible preview.
- Other relative text-file links may use the same Editor open flow, with existing binary/size rejection. No new
  external-file association launcher is added. An image link is not implicitly an image-viewer feature.
- HTTP/HTTPS links, even ones ending in `.md`, use `context.platform().browse` on an explicit click.
- Heading identifiers derive from visible heading text: lowercase using `Locale.ROOT`, retain Unicode letters,
  digits, marks, `_` and `-`, replace whitespace runs with `-`, omit other punctuation; use `section` if empty.
  Resolve collisions in document order with `-1`, `-2`, etc. against the full set of already allocated identifiers.
  Decode a clicked fragment once and match exactly. A missing heading reports a small notice without jumping to a
  random position or changing the file.

### 7.3 Loading and limits

Local/SFTP-relative images resolve through the hook and call the returned handle's `read(limitBytes)`; image bytes
bypass the Editor's text decoder. They never create editor tabs or recent-file entries. Normal provider sign-in and
connection failure behavior applies, with repeated requests coalesced rather than producing one prompt per image.

Web images begin as placeholders. A visible Load external images control grants loading for the current document
identity during the lifetime of its tab. This includes web images added by later edits to that document. It survives
mode/window moves and reload of the same file; it resets on Save As, close/reopen or replacement with another file.
It is never persisted or shared with another document. Loading a local/SFTP image that redirects or refers to a
website cannot bypass this choice.

Use Java's HTTP client on background work with no browser cookies, stored credentials, Referer or URL user-info.
Follow at most three HTTP/HTTPS redirects after checking each destination; no HTTPS-to-HTTP downgrade. Use a 10-second
connect timeout and a 20-second overall request/body deadline, and enforce streamed byte limits even when length is
missing or false. Canceling/hiding/disposal closes the request/body. No arbitrary URLs reach Swing's own loader.

Each image is limited to 8 MiB encoded and 16 million decoded pixels, with dimensions checked before decoding.
Support PNG/JPEG/GIF by decoded format rather than filename alone. Limit active image loads to four across the plugin.
Keep at most 64 MiB of decoded image pixels across all preview tabs, including currently visible retained images;
budget exhaustion leaves an explanatory placeholder instead of silently retaining an unbounded cache. Release
hidden-tab images first. Reserve decoded-pixel budget before decoding, including in-flight images, and avoid retaining
both an original and a scaled copy outside that budget. Cache keys include document/storage context and resource
identity; identical local paths
on different remote hosts must not collide. No persistent image cache. Repeated URLs within a document coalesce.
Reload/Retry clears failed entries; document reload also invalidates its local/SFTP image cache. No filesystem watcher
is added for individual image files.

Loading placeholders reserve space; failure/unsupported/oversized placeholders retain alternative text, or the file
name when alt text is empty, and a compact reason. The document continues rendering. Retry acts on failed resources
without requiring a source edit. Preview-wide failure displays Retry and Source actions and keeps any old content
explicitly labeled out of date rather than presenting it as the latest text.

## 8. Verification and delivery

Follow the repository's implementation-plan workflow after written-spec approval: one plan for this runnable
deliverable, complete implementation detail per task, meaningful TDD, a commit per task and independent reviews.
Use the subagent-driven execution workflow when selected at that handoff. Record deviations in both the plan status
banner and `docs/STATUS.md`. This specification does not authorize GUI launch, AOT training, merge or push.

Required automated evidence:

| Area | Cases |
| --- | --- |
| Markdown | All supported blocks/inlines/extensions, escaping, Unicode, unknown code language, deterministic heading IDs, unsupported HTML/SVG, invisible mapping markers |
| Real Swing layout | Headless rendered dark/light output, normal and large fonts, narrow widths, tables, long code, images, workspace glass; inspect images, not only HTML strings |
| Editing and commands | Same document/undo/Vim/caret across modes, source retained, preview typing inert, Save/Close semantics, search target and options, Replace/Go to Line temporary reveal |
| Typography | Independent and followed settings, fractional size/range validation, old integer-size migration, kit defaults, live cross-window updates, follow off/on/off restoration, fallback glyphs, ligatures, gutter/caret/hit testing, wraps/folds, tight rows and subscription disposal |
| Toolbar | Placement below tabs and above banners, all three modes, classic/modern artwork, selected states, keyboard/accessibility, non-Markdown empty collapse |
| Preferences | First-run Source, remembered explicit mode after reload, per-tab isolation, cross-window behavior, setting changes, rapid asynchronous saves/failure, no accidental persistence from navigation |
| Updates | Debounce/coalescing, reload, Save As, rapid typing, old text/theme/image completions, failure/Retry, hidden tabs, reactivation, move, disposal, executor rejection, all resource bounds |
| Scroll mapping | Both directions, nested lists/quotes/tables/code, source wraps/folds, empty/gap ranges, resize/image/font changes, deletion before anchor, bottom retention, disabled sync, no deferred feedback loop |
| Resources | Real local temporary files and fake remote providers; preserve exact route/login; missing/unsupported/oversized/corrupt images; relative paths with spaces, Unicode, `..`, percent encodings and `+` |
| Web policy | Local HTTP test server only; no request before consent, consent isolation/reset, redirects/timeouts/byte limits, cancellation, no automatic Swing fetch, unsupported URI schemes |
| Navigation | Same-file/cross-file fragments, already-open dirty targets, asynchronous target load/close, missing anchors, browser handoff via fake SDK platform, no image recents |
| Integration | Editor and Remote manifests/dependencies, source hygiene, architecture gates, public API docs, dependency licenses and packaging inventory |

Run focused Editor/Remote tests during implementation, then `./gradlew check` with JBR 25. Read XML for exact counts.
Run `git diff --check`, source hygiene and documentation checks. No GUI is needed for these. If packaging validation
is needed, only the headless `packageApp`/`verifyPackage` path without AOT is within unattended scope.

Update `docs/editor.md`, `docs/editor-verification.md`, the relevant Editor API/provider documentation, and the
Editor settings template, SDK appearance documentation and testkit contracts when implemented. Keep design acceptance
separate from claims of implementation success.

User-run native acceptance: independent/followed fonts and restoration, fractional sizes, fallback glyphs, ligatures,
line height and pointer/caret alignment, classic/modern toolbar placement, the three modes and tooltips, dragging the
divider, physical keyboard/trackpad scroll
sync, preview selection/search, long-document typing, Vim/source focus, dark/light/glass, moving tabs, SFTP sign-in
and resources, browser links, mode persistence across restart, and close while image work is in flight.

## 9. Spec self-review

Reviewed inline for scope, contradictory mode persistence, source/preview focus, reload behavior, asynchronous
ownership, render bounds, unsupported destinations and missing test evidence. The file-chooser route mismatch is
addressed by provider-relative document resolution. Dependency and visual-rendering feasibility remain validations
for the implementation plan, not unverified claims in this design. There are no unresolved product choices; the
amended written specification awaits user review before planning. The October 20 self-review checked font precedence,
SDK boundaries, toolbar/banner ordering and removal of stale fixed-version assumptions. Synchronized scrolling and
remembered view mode retain their original semantics.
