# Browser 9: the Chromium Markdown preview — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or
> superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** The Editor's Markdown preview drawn by Chromium, through the Browser's exported API:
- Mermaid diagrams, highlighted code and GitHub's layout in Jasper's theme colours, following light and dark;
- GitHub's alerts, footnotes and autolinks, and heading ids;
- everything today's preview does: Source, Split and Preview, scroll sync, Find, keyboard movement between links,
  external images only after consent, the size limits, pausing while hidden, and a preview per view;
- today's Swing preview kept as **Markdown preview: Basic**, which screen readers can read, and used whenever the
  Browser is off or Chromium cannot start.

**Architecture:**
- **The Browser exports `dev.jasper.browser.api`** (JDK and SDK types only): the service `MarkdownPreviews` hands out
  `MarkdownPreview`s, each a canvas that shows HTML the Editor wrote.
- **Behind it, in the Browser:**
  - a second Chromium client made only for previews, so none of the browser's page handlers, bridge or page scripts
    reach a preview;
  - a `jasper-preview:` scheme with a random origin per preview. It serves a fixed shell, Jasper's `preview.js` and
    `preview.css`, the bundled Mermaid, the current document and its images, every answer under one content security
    policy;
  - a request filter that refuses anything outside the preview's origin, and a navigation rule that cancels every
    navigation.
- **In the Editor:**
  - commonmark gains the alert, footnote and autolink extensions;
  - the renderer writes a WEB dialect beside today's SWING one;
  - a sealed `PreviewSurface` (`BasicPreview` over today's `PreviewPane`, `ChromiumPreview` over the API) carries
    install, images, scroll sync, Find, fragments and Copy;
  - the setting `markdown_preview` chooses between them.

**Tech Stack:** Java 25 and Swing on JBR 25.0.4.1 b635.75 with JCEF (Chromium 150, in process, sandbox off), Jasper's
SDK and testkit, commonmark-java 0.30.0 with its gfm-alerts, footnotes and autolink extensions, Mermaid 11.12.0, JUnit 5.

**Spec:** [docs/superpowers/specs/2026-10-26-jasper-browser-plugin-design.md](../specs/2026-10-26-jasper-browser-plugin-design.md):
section 18, section 3 (constraints), section 9 (links in the preview), section 19 (start-up and memory) and section 23,
item 9. It builds on [4a](2026-10-28-browser-4a-browser-core.md), [4b](2026-10-29-browser-4b-page-features.md) and
[4c](2026-10-29-browser-4c-default-browser.md). Research (2026-10-30, through git at `main` `5c7e97279` and
`claude/browser-core` `fc3d60270`, JBR's `javap`, and the commonmark-parent 0.30.0 POM in the local Gradle cache) is
summarised below, with paths and lines as they were then.

## Global Constraints

- **Branch:** from `main` once plans 4b and 4c have merged, as `claude/browser-markdown-preview`, in its own worktree.
  Plan 4a's rulings R0-R48 bind, with 4a's 2026-10-30 sandbox ruling (`no_sandbox = true`), and so do the banners of
  4b and 4c. Plans 5 to 8 may or may not have merged first; where this plan meets their work, it says so.
- **Versions:**
  - **No SDK additions are planned.** If a task finds it needs one, it takes the next minor after `JasperSdk.VERSION`
    on the branch it starts from, with that `@since`, every bundled manifest's range moves, and the banner records it.
  - **The Browser** moves to its next minor and declares `exports = ["dev.jasper.browser.api"]` (4a deferred the export
    to this plan). The exported API carries no `@since`, as the Vault's does not.
  - **The Editor** moves to its next minor (0.18.0 on `main` at research time) and declares
    `requires = [{ id = "dev.jasper.browser", version = ">=<the Browser's new version>", optional = true }]`.
- **Libraries** (Task 1 fetches them; the user approved downloads for this series):
  - `org.commonmark:commonmark-ext-gfm-alerts`, `commonmark-ext-footnotes` and `commonmark-ext-autolink`, all 0.30.0,
    BSD-2-Clause, the version Jasper already uses. The 0.30.0 parent POM lists all three modules. The autolink
    extension brings `org.nibor.autolink:autolink` (MIT) at the version its POM names.
  - **Mermaid 11.12.0** (MIT): the npm tarball's `package/dist/mermaid.min.js`, unmodified, one self-contained file.
  - **No code highlighter is bundled** *(call)*: the Editor already highlights fenced code with RSyntaxTextArea's
    tokens in the workspace syntax palette (below), so the WEB dialect writes those tokens as classes. The fallback,
    if the user wants more languages, is highlight.js 11.11.1 (BSD-3-Clause).
  - **No heading-anchor extension** *(call)*: `commonmark-ext-heading-anchor` hooks commonmark's `HtmlRenderer`, which
    Jasper does not use. Heading ids are the ones `HeadingIds` makes today, as `docs/editor.md` documents them.
- **The safety model** (spec section 18: the file is untrusted):
  1. **The renderer's vocabulary.** Raw HTML stays text. Every link is `jasper-link:<id>` and every image a
     `data-image` id. No attribute but the ones Task 2 lists is ever written.
  2. **One content security policy** on every `jasper-preview:` answer, exactly:
     `default-src 'none'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self'; connect-src 'self'; base-uri 'none'; form-action 'none'; frame-ancestors 'none'`.
     The origin serves exactly two scripts, `preview.js` and `mermaid.min.js`. Inline styles are allowed because
     Mermaid writes `<style>` into its SVG. With `img-src`, `font-src` and `connect-src` closed to the network, CSS
     cannot leak anything.
  3. **The preview client refuses** every request outside its own origin and every navigation but the shell's first
     load. It opens no popup and no dialog.

  Images reach Chromium only from the Editor's loader, after its consent, limits and budget, as PNGs Jasper encodes.
  So Chromium itself fetches nothing from the network *(call)*.
- **Engine facts** (`javap --module jcef`, JBR 25.0.4.1 b635.75, 2026-10-30; do not re-derive):
  - **Settings:** `CefBrowserSettings` has only `windowless_frame_rate` and `sharedTexturesEnabled`.
    `CefSettings.background_color` is app-wide, so a transparent preview would make every web page without its own
    background see-through. The Chromium preview is therefore opaque in translucent windows *(call)*.
  - **Schemes:** `CefSchemeRegistrar.addCustomScheme(name, standard, local, displayIsolated, secure, corsEnabled,
    cspBypassing, fetchEnabled)` and `CefApp.registerSchemeHandlerFactory(scheme, domain, factory)`.
  - **Clients:** `CefApp.createClient()` can be called again for a second client. JCEF keeps the first handler of each
    kind a client is given (`engine/Chromium.java:113-117` on `claude/browser-core`).
  - **Frames:** `CefFrame` has `copy()`, `selectAll()` and `executeJavaScript(code, url, line)`.
- **Research facts** (`main` unless marked):
  - **The renderer:**
    - `plugins/editor/build.gradle.kts:10-13`: commonmark 0.30.0 with gfm-tables, gfm-strikethrough and
      task-list-items;
    - `markdown/MarkdownRenderer.java`: the parser at `:54-55`, the tag vocabulary at `:126-187`, raw HTML escaped at
      `:148-153`, `jasper-link:`/`jasper-image:` ids at `:161-170`, block anchors `b<n>` at `:227-239`, one anchor per
      code line at `:241-274`;
    - `markdown/CodeHighlighter.java:17-44`: RSyntaxTextArea tokens to `<font color>` per `SyntaxRole`, with aliases;
    - `markdown/HeadingIds.java:14-36`: the heading names;
    - `markdown/MarkdownLimits.java:7-9`: 2,097,152 UTF-16 units, 16 MiB of HTML, 50,000 blocks.
  - **The controller and views:**
    - `ui/MarkdownController.java`: the 250 ms debounce at `:87`; rendering only while showing at `:89-98` and
      `:165-170`; the render worker at `:195-210`; install, then scroll link, images and fragment at `:214-249`;
    - `ui/PreviewPane.java`: Tab, Shift+Tab and Enter at `:56-59`, leaving the preview after the last link at
      `:84-98`, the Swing tag allowlist at `:162-206`, and the translucent background at `:277-283`;
    - `ui/DocumentArea.java`: `PreviewStyle` at `:117-125` and the modes at `:130-140`. The preview takes
      `appearance.workspacePalette()` and `appearance.syntaxPalette()`, not the `color_scheme` choice (`:241`); the
      Chromium preview keeps that;
    - `ui/ScrollLink.java`: viewport-only knots and geometry at `:42-72`, 4 ms rebuild slices at `:93-158`, and
      leadership at `:166-182`;
    - `ui/FindBar.java`: selection seeding at `:35-47`, counts at `:144-161`, Java's pattern check at `:174-188`, with
      `FindTarget` and `PreviewSearch`.
  - **Images:**
    - `markdown/resources/ResourceSession.java:17-22,74-93`: `ImageState`, `Update`, consent, and the release while
      hidden;
    - `ImageLoader.java:17,75,78`: 8 MiB, PNG, JPEG and GIF, 16 million pixels;
    - `EditorPlugin.java:101`: a 64 MiB budget.
  - **Links:** `ui/MarkdownNavigation.java:20-23` with `EditorWindow.java:194`: web links go through
    `Platform.openInBrowser`, which follows spec section 9's decision since plan 3b. The preview's link ids resolve at
    `EditorWindow.java:1065-1071`.
  - **Settings and toolbar:** `ui/EditorSettings.java:122-127` (`markdown_view_mode`), `ui/EditorSettingsPanel.java:139`
    (the Markdown section) and `ui/MarkdownToolbar.java:30-35` (Load external images, Retry images).
  - **The Browser** (`claude/browser-core`):
    - its manifest is 0.1.0 with no `exports`, and its build has no dependencies;
    - `engine/ChromiumApp.java`: the start and `jasper-about`'s factory at `:32-46`, the one client at `:56-68`, and
      custom schemes at `:91-93`;
    - `engine/AboutScheme.java` answers HTML only, with no headers;
    - `page/Page.java:25-26` is the only maker of `PageView` (R7), and `PageView.java:128` gives Tab to the page;
    - `links/OpenerBridge.java:80,130-132`: router `jasperBridgeQuery` on the client;
    - `links/BridgeJson.java`: strict flat JSON.
  - **Build rules:**
    - `gradle/plugin-architecture.gradle.kts:13`: `declaredImports`;
    - `gradle/architecture/PluginPresentationCheck.java:17-20`: `PAGE` and `PAGE_OWNER`;
    - `gradle/third-party-notices.gradle.kts:15,49-58`: jars and fonts only. Plan 7 adds files under `third_party/`;
    - `tools/sdk-reference/SdkReference.java:164-187` reads `exports` from the bundled manifests, so the export puts the
      API in `docs/sdk-reference.html`.
  - **Services:** `jasper-sdk/.../services/Services.java`: `publish` takes an interface. An absent optional provider's
    API classes throw `NoClassDefFoundError` on first use.
  - **Migration rows:** `jasper-app/src/test/.../appearance/ThemeMigrationCoverageTest.java:17-18,61-76`: every source,
    test or main, that imports Swing, AWT or `dev.jasper.sdk.ui`, or names `Appearance`, needs a
    `docs/design/theme-engine/migration.csv` row. Each task adds the rows for its new files.
- **Boundaries:**
  - JCEF only in `engine`, `page`, `links` and `dialogs` (and 4b's packages), per `ChromiumBoundaryTest`;
  - `api`, `preview` and `json` name no JCEF type;
  - painting only in `PageView`, which `Page` and now `PreviewPage` make (R7 widened);
  - no engine fake (R29); starting the Browser, and making or feeding an unshown preview, loads no Chromium class;
  - in the Editor, only `ChromiumPreview` and `ChromiumPreviews` (and their nested classes) name
    `dev.jasper.browser.api`.
- **Start-up** (spec section 19): the Browser's start only publishes the service. Chromium starts when the first
  browser item or Chromium preview shows. A tab makes its Chromium surface only once it shows Markdown with the setting
  on Chromium. AOT training never shows a preview, so it loads no JCEF.
- **No GUI from agents:**
  - tests are headless and start no Chromium, and the user's GUI check is Task 9;
  - never run `:jasper-app:run`, benchmarks or AOT training without the user's OK;
  - never run `./gradlew --stop`;
  - no benchmarks until the whole browser feature is built (the user's choice).
- **Copy:** the strings below, exactly. **No animations:** a preview loading shows its background, statically.
  **Every platform:** macOS, Windows, and Linux on x64 and aarch64.
- **Commits** end with:

  ```
  🤖 Generated with Claude Code at The Home Depot

  Co-Authored-By: Claude <noreply@anthropic.com>
  ```

## Review Focus

1. **A hostile Markdown file:**
   - raw `<script>`, `<img onerror>`, `<iframe>`, `<meta http-equiv="refresh">`, `<base>`, `<form>` and `<style>`;
   - a `javascript:` link;
   - a Mermaid `%%{init: {"securityLevel": "loose"}}%%` with a `click` callback;
   - a diagram or image pointing at the network.

   No script of the file runs, no request leaves the preview's origin, and nothing navigates. Pinned by Task 2
   (`WebMarkupTest.hostileHtmlStaysText`), Task 4 (`PreviewSchemeTest.allowsOnlyItsOwnOrigin`) and Task 5
   (`PreviewHandlersTest.cancelsEveryNavigationButTheShellsFirstLoad`, `PreviewScriptTest.mermaidIsStrict`).
2. **Typing fast, a document replaced mid-fetch, and a tab closed while a render or a Find waits.** A stale revision
   never shows or installs, and nothing is told after close. Pinned by Task 5
   (`PagePreviewTest.onlyTheLatestRevisionIsShownOrAnnounced`, `closeCancelsWaitingAnswersAndTellsNothingMore`) and
   Task 6 (`ChromiumPreviewTest.aStaleShownDoesNotInstall`).
3. **No Chromium:** the Browser off, its classes absent, its start failed, or Chromium stopped. The tab previews in
   Basic, keeping its view, divider and text. Pinned by Task 6 (`ChromiumPreviewsAbsentTest`,
   `MarkdownEngineTest.unavailableFallsBackToBasicKeepingTheView`).
4. **Heights that change after install** (diagrams, images, fonts), and Java's own scroll echoing back from the page.
   Scroll sync keeps the passage, and an echo never takes the lead. Pinned by Task 7 (`ScrollLinkChromiumTest`).
5. **Switching Chromium and Basic, or light and dark, while a preview shows with Find open.** The mode, the divider
   and Synchronize scrolling stay, Find closes on the old preview, and the theme reaches the page before the next
   document. Pinned by Task 6 (`MarkdownEngineTest.switchingEnginesKeepsModeDividerAndSync`,
   `switchingClosesFindOnTheOldPreview`) and Task 5 (`PagePreviewTest.themeGoesBeforeTheFirstDocument`).

---

### Task 1: The bundled libraries and their notices

**Files:**
- Modify: `plugins/editor/build.gradle.kts:10-13`: add the three commonmark extensions at 0.30.0.
- Create: `plugins/browser/src/main/resources/dev/jasper/browser/third_party/mermaid.min.js`, unmodified.
- Modify: `jasper-app/src/main/resources/dev/jasper/app/about/licenses/notices.toml`:
  - the CommonMark Java component lists the three new modules;
  - a new "autolink-java" component (MIT, `https://github.com/robinst/autolink-java`, `text = "autolink.LICENSE.txt"`);
  - a new "Mermaid" component.
- Create, beside `notices.toml`: `autolink.LICENSE.txt`, `mermaid.LICENSE.txt` (the tarball's `package/LICENSE`) and
  `mermaid.NOTICE.txt`.
- Modify, unless plan 7 has merged: `gradle/third-party-notices.gradle.kts:15,49-58`. A bundled plugin's files under
  `third_party/` must be listed in a component's `files`, as its fonts are. Plan 7 Task 5 defines this rule, so take
  its wording if it has merged. Also AGENTS.md's notices sentence and
  `docs/app-maintenance.md#adding-or-bumping-a-dependency`.
- Test: `plugins/browser/src/test/java/dev/jasper/browser/preview/MermaidBundleTest.java` and `ThirdPartyNoticesTest`.

**Interfaces:**
- **The Mermaid notice:**

  ```toml
  [[component]]
  name = "Mermaid"
  version = "11.12.0"
  license = "MIT"
  homepage = "https://mermaid.js.org/"
  files = ["dev.jasper.browser/dev/jasper/browser/third_party/mermaid.min.js"]
  text = "mermaid.LICENSE.txt"
  notice = "mermaid.NOTICE.txt"
  ```
- **`mermaid.NOTICE.txt`** lists every package that Mermaid 11.12.0's `package.json` `dependencies` names, with the
  version its lockfile or published metadata gives and its licence from its npm metadata. The minified file bundles
  them.
- **`MermaidBundleTest.MERMAID_SHA256`:** the file's SHA-256, recorded in Step 1.

- [ ] **Step 1: Fetch** (the user approved downloads for this series):
  - resolve the three extensions through Gradle;
  - download `https://registry.npmjs.org/mermaid/-/mermaid-11.12.0.tgz` into a new, empty scratch folder, and read it
    as untrusted data;
  - copy `package/dist/mermaid.min.js` and `package/LICENSE`;
  - record the file's SHA-256, and autolink-java's version and licence from the extension's POM.

  **Stop and ask** if any of these holds:
  - the file loads other files: it contains `import(` of a relative chunk, or a `.mjs` reference;
  - it is over 8 MiB;
  - 11.12.0 is not on the registry, or the registry has a newer 11.x published more than two weeks ago.

  With `javap`, record in the banner the extensions' node types: the alert node and its type accessor,
  `FootnoteReference` and `FootnoteDefinition` with their labels, and autolink's link types.
- [ ] **Step 2: Write the failing tests:**
  - `MermaidBundleTest`: the resource exists, its SHA-256 equals `MERMAID_SHA256`, and its size is between 1 and
    8 MiB;
  - `ThirdPartyNoticesTest`: the Mermaid component's version, licence and file, and the three commonmark modules on
    the CommonMark Java component.
- [ ] **Step 3: Run** `./gradlew :jasper-plugin-browser:test --tests '*MermaidBundleTest' :jasper-app:test --tests
  '*ThirdPartyNoticesTest'`. Expected: FAIL.
- [ ] **Step 4: Add** the files, the notices and the `third_party/` rule. Check the rule refuses: with the Mermaid
  component's `files` line removed, `./gradlew verifyThirdPartyNotices` fails with "… ships but no component in
  notices.toml lists it in files". Restore the line.
- [ ] **Step 5: Run** the tests of Step 3 and `./gradlew verifyThirdPartyNotices :jasper-plugin-editor:compileJava`.
  Expected: PASS.
- [ ] **Step 6: Commit** "Editor and Browser: bundle commonmark's alerts, footnotes and autolinks, and Mermaid 11.12.0".

### Task 2: The renderer's extensions and its WEB dialect

**Files:**
- Modify: `plugins/editor/src/main/java/dev/jasper/editor/markdown/MarkdownRenderer.java`: the parser (`:54-55`) gains
  the extensions; the dialect; the shared bookkeeping (anchors, headings, links, images, limits, interruption) stays
  here.
- Create, under `.../editor/markdown/`: `WebMarkup.java` (the WEB vocabulary) and `Footnotes.java` (numbering and
  collection, shared by both dialects).
- Modify: `markdown/CodeHighlighter.java` (the dialect's openings) and `ui/MarkdownController.java:201`, which passes
  `Dialect.SWING` until Task 3.
- Test: `markdown/MarkdownRendererTest.java`, `markdown/CodeHighlighterTest.java` and new `markdown/WebMarkupTest.java`,
  with a fixture `src/test/resources/markdown/github-features.md` (every construct below) that Task 9 reuses.

**Interfaces:**
- **The dialect and the renderer:**
  - `enum MarkdownRenderer.Dialect { SWING, WEB }`;
  - `MarkdownResult render(String source, SyntaxPalette palette, Dialect dialect)` replaces the two-argument form, and
    every caller passes a dialect.
- **The parser:** tables, strikethrough and task lists, plus `AlertsExtension`, `FootnotesExtension` (no inline
  footnotes) and `AutolinkExtension`, in both dialects.
- **Shared rules:**
  - **Anchors:** both dialects anchor the same nodes in the same order, except Mermaid blocks, which WEB anchors once
    per diagram.
  - **Footnotes** are numbered by first reference. A definition that is never referenced is left out. The definitions
    follow the document, in that order.
  - **Footnote ids:** `fn-<n>` and `fnref-<n>`, taken from the same `HeadingIds`, so they never clash with a heading.
    They go into `headings()`: the definition's source start, and the start of the block that holds the first
    reference. So `#fn-1` navigates as `#heading` does.
  - **The limits:** `MarkdownLimits` bound both dialects.
- **The SWING additions,** in tags `PreviewPane.prepare` already allows:
  - an alert is a `<blockquote>` whose first paragraph is `<b>Note</b>` (or Tip, Important, Warning or Caution);
  - a reference is `<a href="jasper-link:K">[n]</a>`;
  - the definitions are an `<hr>`, then an `<ol>` whose `<li>` each end with `<a href="jasper-link:K">↩</a>`.
- **The WEB vocabulary** is a body fragment. Block anchors are `data-b="<n>"` on the element, `<n>` being `BlockAnchor`
  `b<n>`'s index:
  - **headings:** `<h1 id="<heading id>" data-b>`;
  - **blocks:** `<p>`, `<blockquote>`, `<ul>`, `<ol start>`, `<li>`, `<hr>`, `<br>`, `<em>`, `<strong>`, `<del>` and
    `<code>`;
  - **task items:** `<li class="task-list-item"><input type="checkbox" disabled checked>`;
  - **tables:** `<table>`, rows `<tr data-b>`, and cells `<th>`/`<td>` with `class="align-left|align-center|align-right"`;
  - **code:** `<pre><code>`, each line a `<span class="line" data-b>`, with tokens `<span class="tok-<role>">`;
  - **Mermaid:** a fence whose info string's first word is `mermaid`, in any case, is `<pre class="mermaid" data-b>`
    holding the escaped source;
  - **alerts:** `<div class="markdown-alert markdown-alert-<type>" data-b><p class="markdown-alert-title">Note</p>…</div>`;
  - **footnotes:** a reference is `<sup class="footnote-ref"><a href="jasper-link:K" data-link="K" id="fnref-n">n</a></sup>`.
    The definitions are `<section class="footnotes"><ol><li id="fn-n" data-b>…`, each ending with
    `<a href="jasper-link:K" data-link="K" class="footnote-backref" aria-label="Back to reference n">↩</a>`;
  - **links:** `<a href="jasper-link:K" data-link="K">`;
  - **images:** `<img data-image="K" alt="…">`, with no `src`;
  - **raw HTML** is text, and HTML comments are left out, as in SWING.

  Only the attributes named here are written: `id`, `class`, `data-b`, `data-link`, `data-image`, `href`, `alt`,
  `start`, `type`, `disabled`, `checked` and `aria-label`.
- **`CodeHighlighter(String language, SyntaxPalette palette, Dialect dialect)`:**
  - WEB writes `<span class="tok-<role>">`, where `<role>` is `role.name().toLowerCase(Locale.ROOT).replace('_', '-')`,
    and leaves colour, weight and style to CSS;
  - `static boolean mermaid(String info)`.

- [ ] **Step 1: Write the failing tests.**
  - **`WebMarkupTest`:**
    - `headingsCarryTheirIds`: `# Getting started` gives `<h1 id="getting-started" data-b="0">Getting started</h1>`.
    - `hostileHtmlStaysText`: the Review Focus 1 inputs. Every tag in the output is in the WEB vocabulary, every
      attribute in the list above, and every `href` matches `jasper-link:\d+`. `<script` appears only escaped, as
      `&lt;script`.
    - `linksAreIds`: `[x](javascript:alert(1))` gives `href="jasper-link:0"`, and `links().get(0).destination()` is
      `javascript:alert(1)`, which `Destinations` refuses on a click.
    - `alertsUseGithubsMarkup`: `> [!WARNING]\n> Careful` holds `class="markdown-alert markdown-alert-warning"` and
      `<p class="markdown-alert-title">Warning</p>`.
    - `footnotesAreNumberedByFirstReference`: `b[^y] a[^x]\n\n[^x]: X\n[^y]: Y\n[^z]: Z`. The references read 1
      (`y`) then 2 (`x`). The section lists Y then X, and Z is absent. `headings()` holds `fn-1`, `fn-2`, `fnref-1`
      and `fnref-2`.
    - `autolinksBecomeLinks`: `see https://example.com and www.example.org` gives two links, with destinations
      `https://example.com` and `http://www.example.org`.
    - `mermaidBlocksStayText`: "```mermaid\ngraph TD; A-->B\n```" gives
      `<pre class="mermaid" data-b="0">graph TD; A--&gt;B</pre>`, with one block.
    - `codeIsHighlightedWithClasses`: "```java\nreturn 1;\n```" holds `<span class="tok-keyword">return</span>` and
      `<span class="tok-number">1</span>`.
    - `tablesAlignWithClasses` and `taskItemsAreDisabledBoxes`.
    - `bothDialectsAnchorTheSameBlocks`: the fixture without its Mermaid block gives equal `blocks()` in both dialects.
    - `theLimitsHold`: 50,001 paragraphs throw `PreviewLimitException`.
  - **`MarkdownRendererTest`:** the SWING alert, references and definitions, and the fixture's SWING output passing
    `PreviewPane.prepare`.
  - **`CodeHighlighterTest`:** `mermaid("Mermaid title")` is true and `mermaid("mermaidx")` false.
- [ ] **Step 2: Run** `./gradlew :jasper-plugin-editor:test --tests '*WebMarkupTest' --tests '*MarkdownRendererTest'
  --tests '*CodeHighlighterTest'`. Expected: FAIL.
- [ ] **Step 3: Implement** the dialect, `WebMarkup`, `Footnotes` and the highlighter's openings.
- [ ] **Step 4: Run** `./gradlew :jasper-plugin-editor:test`. Expected: PASS, the existing preview tests included.
- [ ] **Step 5: Commit** "Editor: alerts, footnotes and autolinks, and a WEB dialect for the Markdown renderer".

### Task 3: The preview surface (Basic only)

A refactor: Basic is the only surface, and the Editor behaves as before. `PreviewSurface` and `ScrollLink.Scroller`
have one implementation each until Tasks 6 and 7 add the second; the branch never merges in that state.

**Files:**
- Create, under `plugins/editor/src/main/java/dev/jasper/editor/ui/`: `PreviewSurface.java` and `BasicPreview.java`
  (over `PreviewPane`, `previewScroll` and `PreviewSearch`).
- Modify:
  - `ui/ScrollLink.java`: a `Scroller` per pane and block-id knots;
  - `ui/MarkdownController.java`: prepare and install through the surface;
  - `ui/FindTarget.java` and `ui/FindBar.java`;
  - `ui/DocumentArea.java`: `surface()`, the variant in `PreviewStyle`, and `previewFocused()`;
  - `ui/EditorTab.java:52-56,119-121,238,380`: Find target, Copy, Select All, focus, and the failure line.
- Test: the existing `ScrollLinkTest`, `FindBarTest`, `MarkdownControllerTest`, `MarkdownModesTest`,
  `MarkdownAcceptanceTest` and `PreviewPaneTest`, changed only mechanically; and new `BasicPreviewTest`.

**Interfaces:**
- **`sealed interface PreviewSurface extends AutoCloseable permits BasicPreview`** (Task 6 adds `ChromiumPreview`):
  - **Preparing:**
    - `record Prepared(MarkdownResult result, PreviewPane.Prepared swing)`, where `swing` is null for WEB;
    - `MarkdownRenderer.Dialect dialect()`;
    - `Prepared prepare(String source, SyntaxPalette syntax)`, on the render worker and touching no Swing;
  - **Installing:**
    - `JComponent component()`, what `DocumentArea` lays out (Basic: `previewScroll`);
    - `void install(Prepared prepared, DocumentArea.PreviewStyle style, Font body, double opacity)`, plus
      `restyle(style, body, opacity)` and `bodyFont(Font)`;
    - `boolean installing()`, `Optional<RuntimeException> installFailure()`, `Optional<MarkdownResult>
      installedResult()` and `void cancelInstall()`;
    - `void onInstalled(Runnable)`, `void removeInstalled(Runnable)` and `void beforeReplace(Runnable)`;
  - **Content:**
    - `void images(ResourceSession.Update update)`;
    - `boolean reveal(String name)`: false when the installed document has no such anchor;
  - **Scroll sync:** `OptionalDouble blockY(String blockId)`, `double endY()` and `ScrollLink.Scroller scroller()`;
  - **Find:**
    - `CompletableFuture<Integer> markAll(SearchContext context)`;
    - `CompletableFuture<Boolean> find(SearchContext context)`;
    - `void clearFind()`;
    - `CompletableFuture<String> selectedText()`.

    A refused pattern fails the future with a `PatternSyntaxException`. Basic's futures are already complete;
  - **Commands:** `void copy()`, `void selectAll()`, `void focus()` and `boolean focused()`.
- **`interface ScrollLink.Scroller`:** `int y()`, `int max()`, `Dimension extent()`, `Dimension size()`,
  `void move(int y)` and `Subscription onMoved(Consumer<Boolean> byUser)`.
  - `ScrollLink.ViewportScroller` serves the source and Basic, and always reports `true`; the existing `expected`
    guard still screens their echoes. Task 7 adds the second implementation.
  - **Knots** become `(Position source, String blockId)` plus the end. The rebuild reads `surface.blockY` and
    `surface.endY()` in place of `modelToView2D`.
  - `ScrollLink(DocumentArea area, PreviewSurface surface, boolean linked)`. `DocumentArea.scrollLink` stops being
    final, because Task 6 replaces it.
- **The controller:**
  - a job keeps the surface it was made for, and a job whose surface is no longer the tab's is dropped;
  - `DocumentArea.PreviewStyle(Variant variant, TerminalTypography typography, WorkspacePalette palette, SyntaxPalette
    syntax)`, with the variant taken from `Appearance.workspaceVariant()`.
- **`FindTarget`:**
  - `record Source(RSyntaxTextArea area)` and `record Preview(PreviewSurface surface)`;
  - `boolean available()`, `void focus()`, `CompletableFuture<String> selectedText()` and `Object owner()`, which
    replace `content()`;
  - `FindBar.paneHidden(Object owner)`.
- **`FindBar`:**
  - each search carries a sequence number, and a completion for an older one is dropped;
  - the selection seeds the query when `selectedText()` completes, if the bar still shows the same target and the
    query is unchanged since `open`.
- **`DocumentArea.preview()`** still returns Basic's `PreviewPane`, so tests keep reaching it.

- [ ] **Step 1: Write the failing tests:**
  - `BasicPreviewTest.revealMovesTheCaretToTheHeading`, `blockYIsTheAnchorsTop` (the anchor offset's
    `modelToView2D` y), `markAllCompletesAtOnce`, and `aRefusedPatternFailsTheFuture`;
  - `FindBarTest.aRefusedPatternShowsInvalidPattern`, through the surface.
- [ ] **Step 2: Run** `./gradlew :jasper-plugin-editor:test --tests '*BasicPreviewTest' --tests '*FindBarTest'`.
  Expected: FAIL.
- [ ] **Step 3: Implement** the seam, moving behaviour without changing it.
- [ ] **Step 4: Run** `./gradlew :jasper-plugin-editor:check verifyPluginArchitecture`. Expected: PASS, every existing
  Editor test included.
- [ ] **Step 5: Commit** "Editor: the Markdown preview behind a preview surface".

### Task 4: The Browser's preview API, scheme and client

**Files:**
- Create, under `plugins/browser/src/main/java/dev/jasper/browser/api/`: `MarkdownPreviews.java`,
  `MarkdownPreview.java`, `PreviewEvent.java`, `PreviewTheme.java`, `PreviewFind.java` and `package-info.java` ("May
  depend on: the JDK and the SDK").
- Modify: `plugins/browser/src/main/resources/plugin.toml`: the next minor and `exports`.
- Create: `engine/PreviewScheme.java` (no JCEF) and `engine/PreviewSchemeFactory.java` (JCEF).
- Modify:
  - `engine/ChromiumApp.java`: register `jasper-preview` and its factory, and make a second client;
  - `engine/Chromium.java`: the constructor takes the `PreviewScheme`; add `previewClient()` and `onPreviewClient`;
    the close disposes both clients;
  - `BrowserPlugin.java`: makes the `PreviewScheme`.
- Modify: `docs/sdk-reference.html`, regenerated by `./gradlew sdkReference`, because the export adds the API.
- Test: `engine/PreviewSchemeTest`, `api/PreviewApiValuesTest`, `engine/ChromiumLifeTest` and `ChromiumStateTest`
  (the preview client), and `BrowserManifestTest`.

**Interfaces:**
- **`interface MarkdownPreviews`,** published with `Services.publish` (Task 5). UI thread only:
  - `boolean available()`: false once Chromium has failed to start or has stopped;
  - `MarkdownPreview create(Consumer<PreviewEvent> events)`: a preview showing nothing. It asks nothing of Chromium
    until its component first shows. Events arrive on the UI thread.
- **`interface MarkdownPreview extends AutoCloseable`.** UI thread. After `close()`, every call does nothing, every
  waiting future is cancelled, and no event follows:
  - **The canvas:** `JComponent component()`. The Javadoc writes `{@code JComponent}`, not `{@link}`.
  - **What it shows:**
    - `void theme(PreviewTheme theme)`;
    - `void show(long revision, String html)`: `html` is the Editor's WEB markup, at most 16 MiB of UTF-8, and
      `revision` is greater than the last (`IllegalArgumentException` otherwise);
    - `void image(int id, BufferedImage image)`, `void imageReason(int id, String reason)` and `void clearImages()`;
  - **Moving:** `void scrollTo(double y)` and `void reveal(String elementId)`;
  - **Find:**
    - `CompletableFuture<Integer> markAll(PreviewFind find)`;
    - `CompletableFuture<Boolean> select(PreviewFind find, boolean forward)`, which wraps;
    - `void clearFind()`.

    A pattern JavaScript refuses fails the future with a `PatternSyntaxException` carrying the page's message;
  - **Commands:** `CompletableFuture<String> selectedText()`, `void copy()`, `void selectAll()` and `void focus()`.
- **`sealed interface PreviewEvent`:**
  - `record Shown(long revision)`: that document is in the page and laid out;
  - `final class Geometry`: `long revision()`, `int blocks()`, `OptionalDouble top(int block)`, `double height()` and
    `double viewport()`, in CSS pixels. It copies its array, and NaN entries are empty;
  - `record Scrolled(double y, boolean user)`;
  - `record LinkActivated(int id)`;
  - `record Unavailable(String reason)`.
- **The values:**
  - `record PreviewTheme(Variant variant, WorkspacePalette palette, SyntaxPalette syntax, String textFamily,
    TerminalTypography typography)`: no nulls, and `textFamily` not blank;
  - `record PreviewFind(String query, boolean matchCase, boolean wholeWord, boolean regex)`: `query` not empty.
- **`final class PreviewScheme`.** Plain Java; Chromium asks `answer` on its IO thread:
  - `NAME = "jasper-preview"`, and `POLICY` is the Global Constraints' policy, exactly;
  - **`Origin open()`:** a new origin `jasper-preview://<token>/`, whose token is 32 lowercase hex characters from
    `SecureRandom`;
  - **`Origin`:** `token()`, `URI shell()`, `void document(long revision, String html)`, `void image(int key, byte[]
    png)`, `void clearImages()` and `close()`;
  - **`Answer answer(String url)`,** with `record Answer(int status, String mimeType, byte[] body)`. It serves:
    - `/`: the shell, `text/html`;
    - `/preview.css` and `/preview.js`: from `dev/jasper/browser/preview/`;
    - `/mermaid.min.js`: from `third_party/`, read once and then held;
    - `/document/<revision>`: the latest revision only;
    - `/image/<key>.png`: current keys only.

    Anything else, another token, a closed origin or a method other than GET gets 404 with an empty body;
  - **Headers:** the factory sends `Content-Security-Policy: POLICY`, `X-Content-Type-Options: nosniff`,
    `Cache-Control: no-store` and `Referrer-Policy: no-referrer` with every answer, 404s too;
  - **`static boolean allows(String url, String token)`:** true only for `jasper-preview://<token>/…`, with no user info
    and any case of the scheme;
  - **The shell:**

    ```html
    <!doctype html><html><head><meta charset="utf-8"><meta name="color-scheme" content="light dark"><link rel="stylesheet" href="/preview.css"><script src="/preview.js"></script></head><body><main id="content" class="markdown-body"></main></body></html>
    ```
  - **Registration:** standard, local, display-isolated, secure and fetch-enabled; not CORS-enabled and not
    CSP-bypassing.
- **The preview client:**
  - `CompletableFuture<CefClient> Chromium.previewClient()`: made on the UI thread once `client()` has completed, with
    only the installers given to `onPreviewClient(Consumer<CefClient>)`;
  - it fails as `client()` fails, never starts a second Chromium (R28), and is disposed with it.

- [ ] **Step 1: Write the failing tests:**
  - **`PreviewSchemeTest`:**
    - the shell is 200 `text/html`;
    - each path above is served with its MIME type;
    - an unknown path, another token and a closed origin give 404;
    - an older revision gives 404 once a newer one is set;
    - `allowsOnlyItsOwnOrigin`: false for `http:`, `https:`, `file:`, `data:`, `blob:`, `jasper-about:`, another
      token and `jasper-preview://<token>@x/`; true for `JASPER-PREVIEW://<token>/a`;
    - 1,000 opens give 1,000 distinct 32-hex tokens.
  - **`PreviewApiValuesTest`:** the nulls and blanks refused, and `Geometry` copying its array and reading NaN as empty.
  - **`ChromiumLifeTest`:** after a failed start, `previewClient()` fails with the same reason, and after `close()` it
    fails without starting.
  - **`BrowserManifestTest`:** the export and the version.
- [ ] **Step 2: Run** `./gradlew :jasper-plugin-browser:test`. Expected: FAIL.
- [ ] **Step 3: Implement.** Then run `./gradlew sdkReference` and keep the regenerated page.
- [ ] **Step 4: Run** `./gradlew :jasper-plugin-browser:check :jasper-sdk:check verifyPluginArchitecture`. Expected:
  PASS.
- [ ] **Step 5: Commit** "Browser: the Markdown preview API, the jasper-preview scheme and the preview client".

### Task 5: The preview page and the published service

**Files:**
- Create, in the `page` package: `PreviewPage.java` (the canvas and a browser on the preview client, with plain-typed
  methods as `Page` has) and `PreviewHandlers.java` (the preview client's handlers and its router).
- Modify, in `page`:
  - `PageBrowser.java`: tells a `PreviewPage` owner that its native browser exists, as it tells `Page`;
  - `PageView.java`: an accessible context with a settable name and description; a background colour painted until
    the first frame; drops refusable.
- Move, unless plan 5 has: `links/BridgeJson.java` to `dev/jasper/browser/json/Json.java`, with `object` and `quote`
  public and the same rules, as plan 5 Task 3 moves it. Follow any move 4b made.
- Create, under `plugins/browser/src/main/java/dev/jasper/browser/preview/`: `PreviewService.java`,
  `PagePreview.java`, `PreviewCss.java`, `PreviewMessages.java` and `package-info.java`.
- Create: `plugins/browser/src/main/resources/dev/jasper/browser/preview/preview.js` and `preview.css`.
- Modify:
  - `BrowserPlugin.java`: publish `PreviewService`;
  - `gradle/architecture/PluginPresentationCheck.java`: `PreviewPage` may construct `PageView`, with a negative
    fixture for a third class.
- Test, under `plugins/browser/src/test/java/dev/jasper/browser/`: `preview/PreviewMessagesTest`, `preview/PreviewCssTest`,
  `preview/PagePreviewTest`, `preview/PreviewScriptTest`, `page/PreviewHandlersTest` and `StartLoadsNoChromiumTest`.

**Interfaces:**
- **Consumes:** Task 4's API, `PreviewScheme`, `Chromium.previewClient()` and `onPreviewClient`.
- **`PagePreview implements MarkdownPreview`:**
  - `PagePreview(PreviewScheme.Origin origin, Hooks hooks, Executor background, Consumer<PreviewEvent> events)`, with
    `record Hooks(JComponent view, Consumer<String> run, Consumer<Color> background, Runnable copy, Runnable selectAll,
    Runnable focus, Runnable close)`; `background` hears the theme's background on each `theme`. Production builds
    the hooks from a `PreviewPage`; tests record them (4a's hooks style, no interface);
  - **Before the page's `ready`,** calls wait. On `ready` it sends the theme, then only the latest `show`, its
    images, then the last scroll;
  - **Images:** each `BufferedImage` is encoded once, as PNG on `background`, and cached by identity with weak keys.
    It is served as `/image/<key>.png` and set in the page only if its id still wants that image;
  - **Answers:** each `markAll`, `select` and `selectedText` carries a request id that the page's `reply` answers. A
    new `markAll` or `select` cancels the one waiting before it;
  - **Inbound,** package-private: `void received(String request)` takes each router request of its page, and
    `void unavailable(String reason)` emits `Unavailable(reason)` once and cancels what waits.
- **`PreviewService implements MarkdownPreviews`:** `PreviewService(PreviewScheme scheme, Chromium chromium, Executor
  background)`.
  - `available()` is `chromium.unavailable().isEmpty()`.
  - `create` opens an origin, makes a `PreviewPage` and its `PagePreview`, and wires them: the page's requests go to
    `received`, and its client's failure to `unavailable`.
  - It calls `unavailable` on every live preview when Chromium's start fails or Chromium stops (from
    `BrowserPlugin.startFailed` and `stop`).
- **`PreviewPage(Chromium chromium, URI shell, Consumer<String> requests, Consumer<Throwable> failed)`:**
  - it makes its `PageView` at once, and its browser the first time the view shows, from `previewClient()`;
  - the router hands each request of its main frame to `requests`, and a failed client goes to `failed`, both on the
    UI thread;
  - **the canvas:** its accessible name is "Markdown preview" and its description "Screen readers cannot read this
    preview. Set Markdown preview to Basic in the Editor's settings to read it." It paints the theme's background
    until the first frame, and refuses drops, as Basic does;
  - **commands:** `run(String script)` runs in the main frame once the browser exists, and `copy()`, `selectAll()`,
    `focus()` and `close()` act on the page.
- **`PreviewHandlers`,** the preview client's own handlers, one of each kind:
  - **navigation:** `static boolean navigates(String url, String shell, boolean firstLoad)`. Only the shell's first
    load goes ahead. `static OptionalInt link(String url)` reports a `jasper-link:<n>` the script did not catch;
  - **requests:** every resource request outside `PreviewScheme.allows` is cancelled;
  - **refused:** popups are cancelled, JavaScript dialogs suppressed, and the right-click menu emptied, as Basic has
    none;
  - **focus:** `onTakeFocus(next)` moves Swing's focus to the next or previous component, so Tab leaves the preview
    after the last link and Shift+Tab before the first;
  - **keyboard:** 4a's macOS rule (R35) for Select All only;
  - **the router:** `jasperPreviewQuery` / `jasperPreviewCancel`. A request counts only from the main frame of a
    preview whose URL is on that preview's origin, up to 4 MiB. Each is answered `success("")`.
- **`PreviewMessages`** reads each request strictly with `Json.object`, every value a string or boolean.
  `OPS = Set.of("ready", "shown", "geometry", "scrolled", "link", "reply")`:
  - `{"op":"ready"}`;
  - `{"op":"shown","rev":"<n>"}`;
  - `{"op":"geometry","rev":"<n>","tops":"<y0>,<y1>,…","height":"<h>","viewport":"<v>"}`, where an empty entry means a
    block has no box;
  - `{"op":"scrolled","y":"<y>","user":true|false}`;
  - `{"op":"link","id":"<n>"}`;
  - `{"op":"reply","request":"<n>","value":"…"}`, where a value of `invalid:<message>` is a refused pattern.

  Anything else, an older revision, or numbers that don't parse are dropped.
- **Java calls the page** as `jasperPreview.<fn>(…)`, with arguments written by `Json.quote`. `vars` and `mermaid`
  are JSON texts passed as string literals, which the page parses:
  - `theme(vars, mermaid, dark)`, `show(rev)`, `image(id, url)`, `imageReason(id, text)` and `clearImages()`;
  - `scrollTo(y)` and `reveal(id)`;
  - `markAll(req, query, matchCase, wholeWord, regex)`, `select(req, forward)` and `clearFind()`;
  - `selection(req)`.
- **`preview.js`:**
  - **ready:** it posts `ready` once loaded.
  - **show:** `show(rev)` aborts an earlier fetch and fetches `/document/<rev>`. It replaces `#content`'s children
    through a `<template>`, keeping `scrollY`, sets the known images again, and posts `shown`. A running Find marks
    again because `FindBar` re-runs on each install.
  - **Mermaid:** after a `show`, it renders the diagrams. It loads `/mermaid.min.js` the first time a document has one,
    and caches each SVG by its source and theme. A failed diagram shows "Mermaid could not draw this diagram: <message>"
    above its source. Mermaid initialises with `{startOnLoad: false, securityLevel: "strict", theme: "base",
    themeVariables, deterministicIds: true, fontFamily}`, and Mermaid's `secure` list stays its default, so a
    diagram's `%%{init}%%` cannot change `securityLevel`.
  - **Geometry:** it posts `geometry` after `shown`, and whenever layout changes (a `ResizeObserver`, an image load, a
    diagram, fonts), at most once an animation frame.
  - **Scrolling:** it posts `scrolled` at most once a frame. `user` is false from a `scrollTo` until the first scroll
    more than 1 px from its target, which clears the target, and true otherwise.
  - **Links:** a click or auxclick on `[data-link]` (Enter on a focused link fires one) posts `link`, and its default is
    prevented.
  - **Images:** `image` sets `src`. `imageReason` shows a box with the alt text (or "Image"), then the reason, as Basic
    does.
  - **reveal:** scrolls the element to the top and collapses the selection at its start, so Tab goes to the next link
    after it, as Basic's caret does.
  - **Find:** it searches the text nodes under `#content` and marks every match with the CSS highlight `jasper-find`:
    - literal queries: Match Case, and Whole Word as letter-or-digit boundaries;
    - Regular Expression: JavaScript's `RegExp` with `gm`, plus `i` without Match Case *(call)*;
    - `select` puts the selection on the next or previous match from the selection, wrapping, and scrolls it into view
      (nearest).
  - **Selection:** `selection(req)` replies with at most 1,001 characters.
  - **Theme:** `theme` sets the variables on `:root` with `style.setProperty`, and draws the diagrams again when the
    Mermaid variables changed.
  - **Never used:** `eval`, `new Function`, string timers, `document.write`, `insertAdjacentHTML` and `outerHTML`.
    `innerHTML` is used only on the `<template>` and for Mermaid's SVG.
- **`preview.css`** is Jasper's own CSS in GitHub's layout, with no stylesheet copied:
  - **text:** `var(--text-font)` at `var(--font-size)`, line height 1.5, and padding 12px 16px;
  - **headings:** h1 2em and h2 1.5em, each with a 1px `--border` underline; h3 1.25em, h4 1em, h5 .875em and h6 .85em
    in `--muted`; 24px above and 16px below;
  - **blocks:** 16px below each block; a quote is `--muted` with a .25em `--border` rule;
  - **code:** inline code is 85% on a tint of `--muted`; `pre` is 16px padding, 85%, line height 1.45, `--sunken`,
    radius 6px, `white-space: pre` and `overflow: auto`, so wide code scrolls sideways;
  - **tables:** `display: block; overflow: auto`, cells 6px 13px with a `--border`, even rows `--raised`;
  - **other blocks:** `hr` is .25em of `--border`; `img` has `max-width: 100%`; alerts have a .25em left rule and a title
    in `--alert-<type>`;
  - **links:** `--accent`, underlined on hover, with a 2px `--accent` focus ring;
  - **selection:** `::selection` and `::highlight(jasper-find)` in `--selection` / `--selection-fg`;
  - **scrollbars:** a `--border` thumb on `--bg`.
- **`static Map<String, String> PreviewCss.variables(PreviewTheme theme)`:**
  - **colours:** `--bg`, `--fg`, `--muted`, `--border`, `--raised`, `--sunken`, `--accent`, `--selection`,
    `--selection-fg`, `--success`, `--warning` and `--error`, as `#rrggbb`;
  - **fonts:**
    - `--text-font` and `--code-font`: CSS strings with quotes and backslashes escaped; the code font is the
      typography's family and fallbacks, then `monospace`;
    - `--font-size`: the typography's size in `px`, because Java's points are its logical pixels;
    - `--line-height`, and `--code-ligatures` (`normal` or `none`);
  - **tokens:** per `SyntaxRole`, `--tok-<role>`, `--tok-<role>-weight` (700 or 400) and `--tok-<role>-style`;
  - **alerts:** `--alert-note` is accent, `--alert-tip` success, `--alert-important` the syntax palette's `KEYWORD`
    colour *(call: the workspace palette has no purple)*, `--alert-warning` warning and `--alert-caution` error.
- **`static String PreviewCss.mermaid(PreviewTheme theme)`:** the JSON of Mermaid's `themeVariables`:
  - `darkMode`, `background` (`--bg`), `primaryColor` (raised), `primaryTextColor` (text), `primaryBorderColor`
    (border), `lineColor` (muted), `secondaryColor` (sunken) and `tertiaryColor` (background);
  - `noteBkgColor` (sunken), `noteTextColor` (text), `fontFamily` and `fontSize`.

- [ ] **Step 1: Write the failing tests:**
  - **`PreviewMessagesTest`:** each op parses; an unknown op, a nested value, a repeated name, more than 4 MiB, a
    non-numeric `rev` and a negative link id are dropped.
  - **`PreviewCssTest`:**
    - `--font-size` is `13.0px` for size 13, with no exponent, whatever the locale;
    - a family `Fira "Code"` is escaped;
    - every role has its three variables;
    - `--alert-important` is `KEYWORD`'s colour;
    - `darkMode` is true for `DARK`.
  - **`PagePreviewTest`** (recorded hooks and a recording origin):
    - `themeGoesBeforeTheFirstDocument`;
    - `onlyTheLatestRevisionIsShownOrAnnounced`: three shows before `ready` run one `show(3)`, and a `shown` for 2
      after 3 is dropped;
    - an older revision's `geometry` is dropped;
    - the same image twice is encoded once;
    - `clearImages` clears the origin and the page;
    - a `reply` completes its own request only, and an `invalid:` reply fails with `PatternSyntaxException`;
    - `closeCancelsWaitingAnswersAndTellsNothingMore`;
    - a failed preview client gives one `Unavailable`.
  - **`PreviewHandlersTest`:** `cancelsEveryNavigationButTheShellsFirstLoad` (a second load of the shell, a reload,
    `jasper-about:`, `https:` and `file:` are all cancelled), and `link("jasper-link:7")` is 7.
  - **`PreviewScriptTest`** reads `preview.js` as text:
    - `mermaidIsStrict`: the initialise call holds `securityLevel: "strict"` and `startOnLoad: false`, and the script
      never sets Mermaid's `secure`;
    - none of the forbidden calls appears;
    - every op the script posts is in `PreviewMessages.OPS`;
    - every `jasperPreview.<fn>` that `PagePreview` calls is defined in the script.
  - **`StartLoadsNoChromiumTest.makingAndFeedingAnUnshownPreviewLoadsNoChromiumClass`:** start the plugin, find
    `MarkdownPreviews` in the fake host's services, then create, theme, show, image and close a preview. No `org.cef`
    class loads.
- [ ] **Step 2: Run** `./gradlew :jasper-plugin-browser:test verifyPluginArchitecture`. Expected: FAIL.
- [ ] **Step 3: Implement** the page, the handlers, the service, the script and the stylesheet. Add the
  `migration.csv` rows: `PreviewPage` and `PagePreview` are `nonvisual-helper`, and `PageView` is unchanged.
- [ ] **Step 4: Run** `./gradlew :jasper-plugin-browser:check verifyPluginArchitecture :jasper-app:test --tests
  '*ThemeMigrationCoverageTest'`. Expected: PASS.
- [ ] **Step 5: Commit** "Browser: the Markdown preview page and its service".

### Task 6: The Editor's Chromium preview and its setting

**Files:**
- Modify:
  - `plugins/editor/build.gradle.kts`: `compileOnly(project(":jasper-plugin-browser"))` and
    `testImplementation(project(":jasper-plugin-browser"))`;
  - `plugins/editor/src/main/resources/plugin.toml`: the version and the `requires`;
  - `gradle/plugin-architecture.gradle.kts:13`: `":jasper-plugin-editor" to listOf("dev.jasper.browser.api")`.
- Create: `ui/ChromiumPreview.java`, `ui/ChromiumPreviews.java` and `markdown/PreviewEngine.java`.
- Modify:
  - `ui/EditorSettings.java` (the setting, and `EditorSettings`'s new `markdownPreview` component), `settings.toml` and
    `ui/EditorSettingsPanel.java:139`;
  - `ui/EditorEnvironment.java`: a last component `Optional<ChromiumPreviews> chromium`, which the shorter constructors
    leave empty and `production` looks up;
  - `ui/DocumentArea.java` (the engine swap), `ui/EditorTab.java` (choosing and falling back), `ui/EditorWindow.java`
    and `ui/PreviewSurface.java` (permits `ChromiumPreview`).
- Test:
  - `testsupport/FakeMarkdownPreviews.java`, holding a recording `MarkdownPreview` that fires events;
  - `ui/ChromiumPreviewTest`, `ui/MarkdownEngineTest` and `ui/ChromiumPreviewsAbsentTest`;
  - `EditorBrowserBoundaryTest`, `EditorManifestTest`, `ui/EditorSettingsTest` and `ui/EditorSettingsPanelTest`.

**Interfaces:**
- **Consumes:** Task 3's `PreviewSurface`, `ScrollLink` and `FindTarget`; Task 4's API; and Task 2's WEB dialect.
- **`enum PreviewEngine { CHROMIUM, BASIC }`**, with `key()`.
- **The setting:**

  ```java
  SettingSpec.of("markdown_preview", SettingKind.choice(List.of(
          Choice.of("chromium", "Chromium", PreviewEngine.CHROMIUM),
          Choice.of("basic", "Basic", PreviewEngine.BASIC)), PreviewEngine.CHROMIUM))
      .label("Markdown preview")
      .description("Chromium draws GitHub's look with Mermaid diagrams and needs the Browser plugin; Basic is the "
          + "Editor's own preview, which screen readers can read. Without the Browser plugin, the preview is Basic.")
      .section("Markdown").keywords("editor", "markdown", "preview", "chromium", "mermaid", "screen reader")
  ```

  The `settings.toml` lines:

  ```toml
  # The Markdown preview: chromium (GitHub's look with Mermaid diagrams, drawn by the Browser plugin) or basic (the
  # Editor's own preview, which screen readers can read). Without the Browser plugin the preview is basic.
  # markdown_preview = "chromium"
  ```
- **`ChromiumPreviews`:**
  - `static Optional<ChromiumPreviews> find(Services services)` catches `NoClassDefFoundError`.
    `EditorEnvironment.production` calls it inside its own `catch (LinkageError absent)`;
  - `boolean available()`;
  - `ChromiumPreview create(DocumentArea area)`.
- **The engine of a tab:**
  - it is Chromium when the setting says so, the factory is present and `available()` is true; otherwise Basic;
  - the tab makes its Chromium surface the first time it shows Markdown;
  - after `Unavailable`, the tab is Basic for the rest of the run, since Chromium cannot start again (R28);
  - each view's `DocumentArea` has its own surface, so each view of a file has its own preview;
  - a hidden preview pauses as Basic does: the controller renders only while showing
    (`MarkdownController.java:165-170`), `ResourceSession.active(false)` releases its images, and its page draws at
    1 fps (R32).
- **`DocumentArea.previewEngine(PreviewSurface next)`:**
  - it swaps the laid-out component and replaces `ScrollLink`, keeping `linked` and the split;
  - the tab dismisses a Find on the old surface first;
  - leaving Chromium closes that surface, while Basic's `PreviewPane` lives as long as the area;
  - then it re-renders.
- **`ChromiumPreview implements PreviewSurface`:**
  - **dialect:** WEB, and `prepare` only renders;
  - **install:** sends `theme(…)` when the style changed, then `show(++revision, html)`. `restyle` and `bodyFont`
    send the theme only;
  - **installed:** a `Shown(revision)` for the newest install makes that result installed, runs the installed
    listeners and the area's geometry hook. An older `Shown` does nothing;
  - **images:** an `Update` with `clear` calls `clearImages()`, and each image goes to `image` or `imageReason`;
  - **fragments:** `reveal(name)` is false unless the installed result's `headings()` has the name, and true after
    `preview.reveal(name)`;
  - **links:** a `LinkActivated(id)` reaches the area's link callback, as Basic's click does;
  - **commands:** `copy`, `selectAll` and `focus` delegate, and `focused()` is the component's `isFocusOwner()`;
  - **opacity:** ignored, as the preview is opaque *(call)*;
  - **the theme:** `new PreviewTheme(style.variant(), style.palette(), style.syntax(), body.getFamily(),
    style.typography())`.

- [ ] **Step 1: Write the failing tests:**
  - **`ChromiumPreviewTest`** (the fake):
    - the first install sends the theme, then `show(1, html)` holding `data-b`;
    - a second install with the same style sends `show(2)` only;
    - `aStaleShownDoesNotInstall`: `Shown(1)` after `show(2)` installs nothing, and `Shown(2)` installs;
    - the three kinds of image update;
    - `reveal` of an unknown and of a known name;
    - `LinkActivated(3)` reaches the callback with 3.
  - **`MarkdownEngineTest`:**
    - `switchingEnginesKeepsModeDividerAndSync`: in Split, Basic to Chromium and back keeps the mode, the divider ratio
      and Synchronize scrolling;
    - `switchingClosesFindOnTheOldPreview`;
    - `unavailableFallsBackToBasicKeepingTheView`: the preview renders on Basic and the source text is untouched;
    - no factory, or `available()` false, gives Basic whatever the setting says.
  - **`ChromiumPreviewsAbsentTest`:** a class loader that loads `dev.jasper.editor.` and `dev.jasper.sdk.` child-first
    and refuses `dev.jasper.browser.`. Calling `find` through it, with a proxy `Services`, answers empty and throws
    nothing.
  - **`EditorBrowserBoundaryTest`:** a `java.lang.classfile` scan of the Editor's classes. Only `ChromiumPreview*` and
    `ChromiumPreviews*` refer to `dev/jasper/browser/`.
  - **`EditorManifestTest`:** the optional `requires`.
  - **`EditorSettingsTest`:** the default is chromium, and `"webkit"` is refused.
  - **`EditorSettingsPanelTest`:** the Markdown section's two rows.
- [ ] **Step 2: Run** `./gradlew :jasper-plugin-editor:test`. Expected: FAIL.
- [ ] **Step 3: Implement.** Add the `migration.csv` rows for the new sources.
- [ ] **Step 4: Run** `./gradlew :jasper-plugin-editor:check verifyPluginArchitecture :jasper-app:test --tests
  '*PluginZipsTest' --tests '*BundledSamplePluginTest' --tests '*ThemeMigrationCoverageTest'`. Expected: PASS.
  If a test pins the bundled plugins' start order, it changes: the Browser now starts before the Editor.
- [ ] **Step 5: Commit** "Editor: the Chromium Markdown preview, with Basic kept as a setting".

### Task 7: Scroll sync and Find on the Chromium preview

**Files:**
- Modify: `ui/ChromiumPreview.java` (its scroller, geometry, Find and selection), `ui/FindBar.java` and
  `ui/ScrollLink.java`, if the second scroller needs anything there.
- Test: `ui/ScrollLinkChromiumTest` (new) and `ui/FindBarTest`.

**Interfaces:**
- **Consumes:** `ScrollLink.Scroller` (Task 3), `PreviewEvent.Geometry` and `Scrolled` (Task 4), and the fake
  (Task 6).
- **`ChromiumPreview.scroller()`:**
  - `y()` is the last `Scrolled.y`, rounded;
  - `max()` is `height − viewport` from the last `Geometry`;
  - `extent()` is `(0, viewport)` and `size()` is `(0, height)`;
  - `move(y)` calls `scrollTo(y)`;
  - `onMoved` hears each `Scrolled` with its `user` flag. ScrollLink treats `user == false` like its own adjustment:
    it never takes the lead.
- **Geometry:**
  - `blockY("b<n>")` is `geometry.top(n)`, and `endY()` is `height`;
  - a `Geometry` for the installed revision runs the area's geometry hook. One for another revision is dropped.
- **Find:**
  - `markAll` and `find` turn the `SearchContext` into `PreviewFind(query, matchCase, wholeWord, regex)`. `find` calls
    `select(find, context.getSearchForward())`;
  - for a Chromium target, `FindBar` skips Java's `Pattern.compile`, and the page's `RegExp` decides validity;
  - the status reads "N matches", "1 match", "No matches" or "Invalid pattern", as today.
- **`selectedText()`** delegates.

- [ ] **Step 1: Write the failing tests:**
  - **`ScrollLinkChromiumTest`** (the fake and a real source area, in Split with sync on):
    - a `Geometry` before install changes nothing;
    - after install, a user `Scrolled` to a block's top moves the source to that block's line;
    - an echo (`user == false`) never moves the source;
    - a later `Geometry` with a taller diagram re-aligns the follower;
    - a block with no top is skipped;
    - the preview's bottom brings the source to its bottom.
  - **`FindBarTest`:**
    - `aStaleCountNeverReplacesANewerOne`: two queries, the first completing last; the status shows the second's count;
    - an `invalid:` reply shows "Invalid pattern";
    - `theChromiumSelectionSeedsTheQueryWhenItArrives`;
    - `aLateSelectionDoesNotOverwriteTyping`.
- [ ] **Step 2: Run** `./gradlew :jasper-plugin-editor:test --tests '*ScrollLinkChromiumTest' --tests '*FindBarTest'`.
  Expected: FAIL.
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run** `./gradlew :jasper-plugin-editor:test`. Expected: PASS.
- [ ] **Step 5: Commit** "Editor: scroll sync and Find in the Chromium preview".

### Task 8: Docs and the check

**Files:**
- Modify:
  - **`docs/editor.md`:**
    - "Markdown preview": the Markdown preview setting, and Basic for screen readers and without the Browser plugin;
    - "What the preview shows": GitHub's layout, Mermaid diagrams, alerts, footnotes and autolinks; Basic shows alerts
      as quotes and footnotes as `[n]`;
    - Chromium's preview stays opaque in a translucent window;
    - "Finding in the preview": Regular Expression uses JavaScript's syntax in Chromium's preview;
    - the settings table: `markdown_preview`.
  - **`docs/browser.md`:** a "Markdown preview" section. The Editor's preview draws with the Browser; it loads nothing
    from the network, runs none of the file's scripts, and is Basic while the plugin is off.
  - **`docs/plugin-apis.md`:** five plugins export; the Browser API's row (`publish`, consumer: the Editor); a "Browser
    API" section.
  - **AGENTS.md and `docs/sdk-architecture.md`:**
    - the Browser exports `dev.jasper.browser.api`;
    - the Editor optionally consumes it, named only in `ChromiumPreview` and `ChromiumPreviews`;
    - `PreviewPage` makes `PageView` too.
  - **`README.md`:** the Editor row's preview: "a Markdown preview with Mermaid diagrams".
  - **`docs/STATUS.md`,** and this plan's banner: deviations, rulings, the `javap` notes from Task 1, and the counts.
- Modify: `docs/sdk-reference.html`, regenerated.

- [ ] **Step 1: Write the docs,** documenting only what the code does.
- [ ] **Step 2: Run** the source hygiene script from AGENTS.md, `./gradlew sdkReference`, then `./gradlew check
  --continue`. Expected: PASS. Record the test counts from `*/build/test-results/test/`.
- [ ] **Step 3: Commit** "Docs: the Chromium Markdown preview".

### Task 9: The user's GUI check, with their OK (pending; it never blocks the merge)

**Files:**
- Modify: `docs/browser-verification.md`: a "Plan 9: the Chromium Markdown preview" section.

- [ ] **Step 1: Check on macOS** (`:jasper-app:run` from the worktree, with the user's OK), recording each line as
  pass or fail:
  1. **The look:** `github-features.md` (Task 2's fixture) in Preview shows GitHub's layout in the theme's colours.
     Switching light and dark changes it in place and keeps the scroll. Cmd+= and Cmd+- change its size.
  2. **Mermaid:** flowchart, sequence, class, state, ER, gantt, pie, gitGraph and mindmap draw. A broken diagram
     shows its error and its source. If a type needs `'unsafe-eval'`, stop and ask; don't add it.
  3. **Code:** Java, Python and YAML blocks take the colours the source pane gives them.
  4. **GitHub's features:**
     - the five alert kinds;
     - a footnote reference goes to its definition, and ↩ comes back;
     - autolinks, task lists and aligned tables;
     - wide code scrolls sideways.
  5. **Scroll sync:** both ways in Split, including after diagrams and images load. Turning it off works.
  6. **Find:**
     - Match Case, Whole Word and Regular Expression work, and the count follows edits;
     - Enter and Shift+Enter move between matches;
     - Escape gives the focus back.
  7. **Links:**
     - a web link follows the routing: the "Open in Jasper?" prompt, or the setting's choice;
     - `#heading` and a relative file work;
     - Tab and Shift+Tab leave the preview at its ends, and Enter follows the focused link.
  8. **Images:** a relative image shows. A web image shows only after **Load external images**. A failure shows its
     reason, and **Retry images** works.
  9. **A hostile file** (Review Focus 1, with a raw `<img src="http://127.0.0.1:8000/x">` and a Mermaid image node
     pointing at that address), watched by `python3 -m http.server 8000`:
     - nothing runs or navigates;
     - neither address is ever requested, before **Load external images** or after;
     - a Markdown image `![](http://127.0.0.1:8000/y)` is requested only after **Load external images**, once, by
       Jasper's own loader (Java's user agent in the log).
  10. **Basic:**
      - **Markdown preview › Basic** and back switches live;
      - with the Browser plugin off, after a relaunch, the preview is Basic;
      - VoiceOver reads Basic, and reads the Chromium canvas's description.
  11. **Hidden previews:** a preview in a tab you leave stays idle. Closing the tab ends its page. Note the memory with
      five previews open, for the later benchmark; it is not a benchmark.
  12. **Copy and Select All** in the preview, including Copy of a Find match.
- [ ] **Step 2: Commit** "Browser: the Chromium Markdown preview's GUI check on macOS". Windows and Linux are recorded
  as not run until the user has those hosts.
