# Editor Fonts and Markdown Preview Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Give Editor documents configurable typography, live following of saved terminal fonts, and Source/Split/Preview Markdown views in a document toolbar.

**Architecture:** Keep the existing document session, RSyntaxTextArea document and Vim controller. Add an SDK typography snapshot/event, Editor-owned text layout, immutable Markdown rendering results, bounded resource loading, and an EDT preview controller. Standard controls remain kit descriptors; specialized source/preview surfaces remain narrowly admitted document content.

**Tech Stack:** JBR 25, Gradle wrapper, Swing, RSyntaxTextArea 3.6.0, CommonMark Java 0.30.0 and its matching tables/strikethrough/task-list extensions, JDK HTTP/ImageIO, JUnit 5.

**Spec:** [Approved Editor fonts and Markdown preview design](../specs/2026-10-04-editor-markdown-preview-design.md).

**Execution ruling (Task 13):** Integrated measurements demonstrated repeated source-width invalidation and quadratic native break/attribute scans. Indexed native source breaks and immutable attribute runs preserve shaping semantics; ASCII preview leaves share paragraph break indices and bound flow candidates through supported Swing APIs; anchor geometry publishes generation-guarded batches. Canonical source width prevents preferred-size probes from reshaping unchanged lines. Measured cold exact layout remains a documented limitation (about 1.28 s for a 2 MiB source line and 2.12 s for 140,000 dense units, recurring on invalidation); no reduced content bound or new asynchronous layout subsystem. The vendor tokenizer's bulk call may delay worker cancellation about 27 s on the pathological Java fixture. Maximum-size responsiveness and native acceptance are not claimed. Native Unicode flow remains intact, and completed-row height invalidation prevents wrapped headings overlapping following prose. Task 13 updates stale integer-font/settings-section test expectations and adds migration-ledger classifications without lowering visual thresholds.

**Execution ruling (Task 9):** Stale hosted menu commands resolve by stable ID against the host current selection; Editor callbacks guard inactive/closed handles. This avoids adding a host ownership query to the SDK.

**Execution ruling (Task 7):** Prepare immutable HTML events on the existing render worker, then install in cancellable EDT slices. This replaces the original direct-result install signature; completion exposes only the successfully attached result for scroll mapping. No pane-owned executor.

**Execution ruling (Task 4):** PreviewLimitException is public final with a package-private constructor so the UI controller can catch it across packages; the exported Editor API is unchanged. ThirdPartyNotices recognizes the new BSD-2-Clause dependency.

**Execution ruling (Task 3):** Task 3 uses a uniform line box from maximum natural metrics across the configured resolved font/style set, multiplied once; EditorTextView extends SyntaxView and Vim physical viewport adapters consume shared geometry. Keep stock gutter with logical-line child allocations. This replaces the plan's per-row varying height algorithm, not the approved configurable line-height behavior. Public RSTA contracts require SyntaxView and uniform gutter metrics; no vendor patch or new gutter subsystem. Cost if wrong: fallback font metrics may produce more spacing than expected, requiring tuning; physical Vim integration needs additional regression coverage.

**Execution ruling (Task 2):** Task 2 adds default SettingsPanel.pendingChanged() called by real/fake hosts after standard-row draft events/reset, and Editor rerenders from it — preserves standard Reset and kit defaults without a custom follow toggle or default-query API — cost if wrong: an additive SDK callback and host ordering contract to maintain.

**Execution ruling (Task 1):** JasperApplication passes FontConfig to a PluginRuntime overload; SDK conversion stays inside app.plugins to preserve the architecture gate.

**Status:** Approved October 20 for subagent-driven implementation and per-task review. All 13 tasks implemented and independently reviewed; whole-branch review and scoped re-review passed through `59e790a6c`. Full repository verification before review fixes passed (11,843 tests, zero failures/errors, 29 skips; SDK release passed). After the final fixes, all 5,115 Editor tests passed with four opt-in skips, architecture gates passed, and the headless package was rebuilt and verified. Native acceptance remains pending; the branch is unmerged and unpushed. Task 3 also adapts ViewCommands to physical wrapped rows and supplies the vendor classpath to architecture self-tests; its first review fixed syntax-event invalidation, bidi caret mapping and layout/indexing costs. Written-spec approval received October 20. Local `main` at `34aa868fe` was merged into `codex/editor-markdown-preview` as `c74465f5d` before planning. No remote-main freshness claim. One runnable deliverable, one commit per task, focused red/green checks and independent review during execution. No product code changed during planning.

## Global Constraints

- Java 25 on JetBrains Runtime; use `./gradlew`. No GUI, benchmark, AOT training, merge into main or push in unattended execution.
- Editor compiles against the SDK only; Remote may name only `dev.jasper.editor.api`. No app/terminal implementation imports in Editor.
- SDK remains JDK-only. CommonMark dependencies belong only to Editor's runtime classpath. No browser, JavaFX, local HTTP server or runtime downloads.
- Source/Split/Preview share one source document and undo history. Split is source-left/preview-right, initially 50/50; divider ratio survives mode changes and moves.
- `markdown_view_mode`: `source`, `split`, `preview`; initially `source`. Only explicit mode actions update the plugin-wide saved default. Existing tabs retain their modes.
- Linked scrolling is bidirectional, initially enabled, per tab, and not persisted. It maps passages, not a common scrollbar percentage.
- `follow_terminal_font=false`; independent family/size values survive. Font size 6–72 finite fractional points; line height 0.5–3.0; ordered fallback names nonblank and NUL-free; ligatures boolean.
- Follow saved terminal typography across windows, never a pane's temporary zoom. Notifications run on EDT after publication.
- Preview debounce 250 ms; source bound 2,097,152 UTF-16 units; HTML bound 16 MiB UTF-8; map bound 50,000 entries. At most one active render and one replaceable pending snapshot per tab.
- Images: 8 MiB encoded, 16 million decoded pixels, four active loads plugin-wide, 64 MiB retained/in-flight decoded pixels plugin-wide; PNG/JPEG/first-frame GIF. Web consent is per document identity and tab lifetime.
- HTTP: 10-second connect timeout, 20-second overall body deadline, at most three redirects, HTTP(S) only, no HTTPS downgrade, user-info, cookies or Referer. Tests use local servers only.
- Raw HTML is escaped, comments omitted. No document script/CSS, SVG/WebP, Mermaid, math, export or interactive checkboxes.
- Standard controls use descriptors and semantic icon names. Preserve the architecture gates with exact source-owner exceptions only for specialized content, never a package-wide exemption.
- Read current `AGENTS.md`, `docs/STATUS.md`, SDK architecture and plugin authoring docs before execution. Record deviations in this banner and STATUS. Every commit ends in `Co-Authored-By: Codex <noreply@openai.com>`.

## Review Focus

1. A terminal font changes while the Editor settings dialog has unapplied independent edits: displayed followed values update without overwriting the staged values (Task 2).
2. Combining marks, supplementary characters and ligatures at a wrapped line boundary: selection and mouse hit testing stay on valid source offsets (Task 3).
3. A hidden tab moves to another window while render/resource work completes: no stale install, disposed owner prompt or unexpected focus transfer (Tasks 8 and 10).
4. Two unsaved Markdown documents with identical image names on different SFTP routes: image bytes and external-image consent remain isolated (Tasks 6 and 10).
5. A source edit deletes the heading currently anchoring the preview while a deferred linked-scroll event is queued: the old mapping cannot move either viewport (Task 11).

---

## Refresh findings and decisions

| Current-main evidence | Implementation decision |
| --- | --- |
| SDK `JasperSdk.VERSION` is 0.18.2; Editor 0.10.0; Remote 0.15.0 | SDK 0.18.3, Editor 0.11.0, Remote 0.15.1. Editor requires `>=0.18.3, <0.19`; other existing ranges remain valid. |
| `Appearance.presentation()` and kit setting defaults already exist | Use the presentation query for artwork and preserve kit font defaults, including Enterprise's IBM Plex Mono. |
| `IconName`/`BundledIcon` were removed; ordinary controls take scoped strings | Add eight explicit built-in names, four modern and four classic, and select them in Editor. Do not restore the removed icon API. |
| `EditorTab.spec()` is a DOCUMENT split; `FindBar` already uses descriptor toggle buttons | Insert the toolbar as its first child, before BANNER; use the same descriptor toggle/accessible-tooltip pattern. |
| `DocumentArea` owns the specialized Swing surface; presentation checking admits exact owners | Put the split and preview surface under DocumentArea; add exact view/constructor permissions with negative checker tests. |
| RSTA `getTokenPainter()` is package-private; widths use both FontMetrics and Utilities | A font attribute alone cannot deliver ordered fallback and accurate shaping. Use Editor-owned text views sharing one TextLayout geometry model for painting, wrapping and hit testing. Do not use reflection or vendor-package injection. |
| `ContentTab.onActive` exists, but Editor currently only handles true | Forward both activation and deactivation to the preview controller. Keep move distinct from close. |
| `DocumentSession.document()` exists; RemoteDocument holds exact identity/channels/owner | Resolve relative resources through the existing provider handle; never reconstruct a route from its display label/key. |

Read-only planning inspected the cached RSTA 3.6.0 sources and the installed IntelliJ icon jar.
The original icon resources are `general/layoutEditorOnly.svg`, `general/layoutEditorPreview.svg`,
`general/layoutPreviewOnly.svg`, `actions/synchronizeScrolling.svg` and their dark siblings;
modern counterparts are `expui/general/editorOnly.svg`, `expui/general/editorPreview.svg`,
`expui/general/previewOnly.svg`, `expui/actions/synchronizeScrolling.svg` and dark siblings.
They are present in `/Applications/IntelliJ IDEA.app/Contents/lib/intellij.platform.ide.jar`.
Execution must vendor the original bytes with upstream revision and SHA-256 provenance, not depend on an installed IDE.

[CommonMark upstream](https://github.com/commonmark/commonmark-java) currently documents 0.30.1;
retain the approved 0.30.0 pin for this plan. Dependency resolution and license inventory are an execution check,
not a completed planning check. Any version change is recorded in the status banner and spec.

## File and ownership map

Paths below are repository-relative. In task file lists, `E/` means
`plugins/editor/src/main/java/dev/jasper/editor/`, `ET/` means its `src/test/java/dev/jasper/editor/`
counterpart; `R/` and `RT/` mean the equivalent Remote paths. These prefixes expand literally.
New production packages get `package-info.java` naming the allowed dependencies below.

| Owner | Files and responsibility |
| --- | --- |
| SDK/host typography | `jasper-sdk/.../ui/TerminalTypography.java`, Appearance; `jasper-app/.../plugins/HostedTypography.java`, HostedUi, PluginHost, PluginRuntime; JasperApplication publication; fake host/context and shared contract harness |
| Editor settings | `E/ui/EditorSettings.java`, EditorSettingsPanel, EditorEnvironment, EditorPlugin; `E/ui/EditorTypography.java` resolves effective values without writing settings |
| Source layout | `E/textview/EditorFonts.java`, `LineLayout.java`, `EditorTextView.java`, `EditorTextAreaUI.java`; VimTextArea selects the UI; DocumentArea supplies effective settings. `textview` depends on SDK values, JDK, RSTA, not ui/vim/session. |
| Parser/output | `E/markdown/MarkdownMode.java`, `MarkdownLimits.java`, `MarkdownResult.java`, `MarkdownRenderer.java`, `HeadingIds.java`, `CodeHighlighter.java`; only syntax/SDK immutable palettes/JDK/vendor dependencies |
| Destinations/resources | `E/markdown/resources/Destination.java`, `Destinations.java`, `ImageBudget.java`, `ImageLoader.java`, `ResourceSession.java`; depends on api, markdown and JDK, never Remote |
| Specialized preview | `E/ui/PreviewPane.java`, `PreviewKit.java`, `PreviewImageView.java`, `PreviewSearch.java`, `ScrollLink.java`; DocumentArea creates/owns these and its split container |
| State/controller | `E/ui/MarkdownController.java`, `MarkdownPreferences.java`, `MarkdownToolbar.java`, `MarkdownNavigation.java`; EditorWindow wires host/session operations |
| Provider hook | `E/api/EditorDocument.java`, `E/api/RelativePaths.java`, `E/local/LocalDocument.java`, `R/edit/RemoteDocument.java` |
| Tests | Owner-package tests; shared manual executor/clock/layout fixtures only in `ET/testsupport/MarkdownFixture.java` and `ET/testsupport/PreviewFixture.java`. No production test bridges. |

Each task uses its stated test command for both red and green unless a wider green command is specified.
Compilation failures naming the newly specified API count as the initial red; runtime regressions must reproduce
as failing assertions before their fixes. Test code uses standard JUnit 5 imports and actual production classes.
New enum/record helper methods named by another task must be implemented with that exact signature.

No renderer abstraction or generic provider framework is introduced. Find uses one concrete `FindTarget` value
with source/preview variants rather than a new interface. Standard toolbar, retry, external-image and failure
controls stay in descriptors; specialized views do not create their own buttons.

## Task 1: Publish terminal typography through the SDK

**Files:** Create `jasper-sdk/src/main/java/dev/jasper/sdk/ui/TerminalTypography.java`,
`jasper-app/src/main/java/dev/jasper/app/plugins/HostedTypography.java`.
Modify Appearance, JasperSdk, HostedUi, PluginHost, PluginRuntime, JasperApplication, FakePluginHost,
FakePluginContext, `jasper-sdk-testkit/src/testFixtures/java/dev/jasper/sdk/testing/contract/ContractHarness.java`
and PluginContractTest. Add owner-package TerminalTypographyTest and HostedTypographyTest.

**Interfaces:** `Appearance.terminalTypography(): TerminalTypography` (safe from any thread);
`Appearance.onTerminalTypographyChanged(Consumer<TerminalTypography>): Subscription` (register/deliver on EDT).
`PluginRuntime.terminalTypography(TerminalTypography): void` publishes to a runtime-owned HostedTypography,
shared by every HostedUi, before plugin startup and on configuration snapshots.
FakePluginHost exposes `setTerminalTypography(TerminalTypography)` and delivers on `flush()`.

- [ ] **Write the failing value and shared contract tests.** Test immutable fallback copying, NaN/infinity,
  5.99/72.01 size, 0.49/3.01 height, blank/NUL names, unchanged values not emitting, updated getter during
  callback, close/unsubscribe inside callbacks, handler failure containment and plugin shutdown.

```java
@Test void copiesFallbackAndRejectsNonFiniteSize() {
    var names = new ArrayList<>(List.of("Monospaced"));
    var value = new TerminalTypography("DialogInput", 13.5, names, true, 1.25);
    names.clear();
    assertEquals(List.of("Monospaced"), value.fallback());
    assertThrows(IllegalArgumentException.class,
        () -> new TerminalTypography("DialogInput", Double.NaN, List.of(), false, 1));
    assertThrows(UnsupportedOperationException.class, () -> value.fallback().clear());
}
```

- [ ] **Run red:** `./gradlew :jasper-sdk:test :jasper-sdk-testkit:test :jasper-app:test --tests '*Typography*' --tests '*ContractTest'`.
  Before the type exists, compilation fails naming TerminalTypography; after adding the value, the shared
  contract must fail on missing publication, not an unrelated fixture setup error.
- [ ] **Implement the value and publication.** The value implementation is:

```java
public record TerminalTypography(String family, double size, List<String> fallback,
                                 boolean ligatures, double lineHeight) {
    public TerminalTypography {
        requireName(family);
        fallback = List.copyOf(fallback);
        fallback.forEach(TerminalTypography::requireName);
        if (!Double.isFinite(size) || size < 6 || size > 72)
            throw new IllegalArgumentException("Font size must be between 6 and 72.");
        if (!Double.isFinite(lineHeight) || lineHeight < .5 || lineHeight > 3)
            throw new IllegalArgumentException("Line height must be between 0.5 and 3.");
    }
    private static void requireName(String name) {
        if (name == null || name.isBlank() || name.indexOf('\0') >= 0)
            throw new IllegalArgumentException("A font family must be nonblank and contain no NUL.");
    }
}
```

  HostedTypography holds a volatile snapshot and an EDT-owned list of guarded registrations. `publish` compares
  equality, replaces the snapshot, then delivers to a snapshot of registrations, checking each is still open.
  HostedUi tracks subscriptions through its existing cleanup and containment. Pass this concrete owner through
  PluginHost; preserve test constructors by delegating to a default instance. Add abstract Appearance methods and
  update all in-repo implementations; existing compiled plugin consumers remain compatible. Document implementer
  source changes. Use `ConfigSnapshot.font()` to convert at the application boundary; seed before `plugins.start`.
  On each `configuration.onSnapshot`, publish typography before plugin settings notifications. No configuration
  service uses `ConfigSnapshot.defaults().font()`. A plugin reload reads the latest snapshot.
- [ ] **Run green:** SDK, testkit and HostedUi/contract tests; `./gradlew verifySdkArchitecture verifyApplicationArchitecture`.
  Bump SDK to 0.18.3 and update SDK README/version fixtures in the same commit.
- [ ] **Commit:** `feat(sdk): expose live saved terminal typography` with the required trailer.

## Task 2: Declare independent fonts and follow-terminal settings

**Files:** Modify `E/ui/EditorSettings.java`, EditorSettingsPanel, EditorEnvironment, EditorWindow,
`E/EditorPlugin.java`; create `E/ui/EditorTypography.java`; tests EditorSettingsTest,
EditorSettingsPanelTest and EditorTypographyTest; Editor `src/main/resources/plugin.toml`.

**Interfaces:** Keep EditorSettings' current editing fields, change `fontSize` to double and add
`List<String> fontFallback`, `boolean fontLigatures`, `double fontLineHeight`, `boolean followTerminalFont`.
`EditorTypography.effective(EditorSettings, TerminalTypography): TerminalTypography`.
`EditorSettings.Declared.current()` remains the independent values. Resolve effective typography only for display/rendering.

- [ ] **Write red tests** for old integer TOML size 13, fractional 13.5, defaults and kit defaults, all validation
  boundaries, staged follow on/off/cancel/reset and live terminal updates while independent values are staged.
  The pure resolution test uses actual settings, not a duplicated resolver in a fixture:

```java
@Test void followDoesNotOverwriteIndependentFont() {
    var own = new EditorSettings("Monospaced", 13.5, List.of("Dialog"), false, 1.2,
        false, 4, false, 10, false);
    var terminal = new TerminalTypography("DialogInput", 17, List.of(), true, 1.6);
    assertEquals(13.5, EditorTypography.effective(own, terminal).size());
    var followed = new EditorSettings(own.fontFamily(), own.fontSize(), own.fontFallback(),
        own.fontLigatures(), own.fontLineHeight(), true, 4, false, 10, false);
    assertSame(terminal, EditorTypography.effective(followed, terminal));
    assertEquals(13.5, own.fontSize());
}
```

- [ ] **Run red:** `./gradlew :jasper-plugin-editor:test --tests '*EditorSettings*' --tests '*EditorTypography*'`.
- [ ] **Implement declarations and effective resolution.** Use SDK number(6,72), number(.5,3), stringList
  validation and bool kinds. Keep family/size defaults; new defaults are empty fallbacks, false ligatures,
  1.0 height, false following. `font()` derives a fractional-size Font, never rounds to an int.

```java
static TerminalTypography effective(EditorSettings own, TerminalTypography terminal) {
    return own.followTerminalFont() ? terminal : new TerminalTypography(own.fontFamily(), own.fontSize(),
        own.fontFallback(), own.fontLigatures(), own.fontLineHeight());
}
```

  Followed settings rows are read-only descriptor copies showing terminal values, without calling `host.stage`.
  Independent rows continue using `host.row` and pending values; toggling follow only stages the boolean.
  Subscribe while the settings panel is mounted; dispose on panel disposal. Plugin lifetime owns one font
  subscription to apply effective settings to every tab; close it explicitly on stop. Add `EditorTab.typography`
  forwarding to DocumentArea. Independent mode ignores terminal changes. Update all EditorSettings construction
  sites, including tests, with the order shown above. Editor becomes 0.11.0, SDK range `>=0.18.3, <0.19`.
- [ ] **Run green:** focused tests plus `./gradlew :jasper-plugin-editor:test`; assert no config writes from a
  terminal-font event and no callbacks after plugin stop.
- [ ] **Commit:** `feat(editor): add independent typography and terminal following`.

## Task 3: Make source geometry honor fallback, shaping and line height

**Files:** Create `E/textview/EditorFonts.java`, LineLayout, EditorTextView, EditorTextAreaUI and package-info;
modify VimTextArea, DocumentArea and EditorTheme. Add `ET/textview/LineLayoutTest.java`,
EditorTextViewTest and `ET/ui/EditorTypographyRenderTest.java`. Update
`gradle/architecture/PluginPresentationCheck.java` with exact owner permissions and its self-tests.

**Interfaces:** `EditorFonts(TerminalTypography, FontRenderContext)`;
`LineLayout.layout(String text, int startOffset, List<Span> styles, EditorFonts fonts, float width,
boolean wrap, boolean wordWrap, int tabSize): List<Row>`; `Span(int start,int end,int fontStyle,Color foreground)`.
`Row(int start, int end, List<Segment> segments, float width, int height, float baseline)`;
`Segment(int start, int end, float x, TextLayout layout)`; all offsets are document UTF-16 offsets.
`LineLayout.caret(Row,int): Shape` and `LineLayout.hit(Row,float): int` are the sole horizontal geometry paths.
They accept absolute offsets; wrapping returns rows in logical source order. The UI handles visual bidi movement
using TextLayout hit information rather than incrementing a UTF-16 index. Row lists and style lists are immutable.
`EditorTextAreaUI(RSyntaxTextArea, Supplier<TerminalTypography>)` creates one EditorTextView.
VimTextArea exposes `typography(TerminalTypography): void`; no dependency from textview back to vim/ui.

- [ ] **Write failing geometry and rendered tests.** Assert exact row advance `max(1,ceil(naturalHeight*multiplier))`
  at .5/1/1.5/3, fractional size preservation, explicit fallback chosen before system fallback, same geometry
  for paint/modelToView/viewToModel, bold/italic syntax, tabs, selection across a ligature, surrogate/combining
  boundaries, folds, long unbroken lines, RTL runs, word/character wrapping and Vim's scroll-past-end.
  Use bundled fonts from test resources for deterministic fallback/ligature evidence, with license provenance.
  Tests on the real view run on EDT, sized/layouted without opening a window.
  Include this real layout contract (on EDT, with a FontRenderContext from the test BufferedImage):

```java
@Test void wrappedRowsRetainEverySourceOffset() {
    var graphics = new BufferedImage(300, 200, BufferedImage.TYPE_INT_ARGB).createGraphics();
    try {
        var fonts = new EditorFonts(new TerminalTypography("Monospaced", 13.5, List.of(), true, 1.5),
            graphics.getFontRenderContext());
        String source = "alpha beta gamma delta";
        var rows = LineLayout.layout(source, 0,
            List.of(new LineLayout.Span(0, source.length(), Font.PLAIN, Color.BLACK)), fonts, 50, true, true, 4);
        assertTrue(rows.size() > 1);
        assertEquals(0, rows.getFirst().start());
        assertEquals(source.length(), rows.getLast().end());
        for (int i = 1; i < rows.size(); i++) assertEquals(rows.get(i - 1).end(), rows.get(i).start());
    } finally { graphics.dispose(); }
}
```

- [ ] **Run red:** `./gradlew :jasper-plugin-editor:test --tests '*LineLayoutTest' --tests '*EditorTextViewTest' --tests '*EditorTypographyRenderTest'`.
- [ ] **Implement shared geometry, not a paint-only override.** Segment by grapheme boundaries, style and explicit
  font support; prefer a font displaying the entire grapheme, then the first capable font per code point without
  separating combining/variation-selector units. Apply FONT, FOREGROUND, LIGATURES and KERNING to an AttributedString.
  Use TextLayout and LineBreakMeasurer for shaped advances and line breaking; split tabs at calculated tab stops.
  Row heights use the maximum natural metrics across runs, centered in the multiplied height. Cache only the
  current document revision, width, style and font generation, with invalidation on changes. Do not tokenize
  the whole document under the Swing document lock on every paint; invalidate/rebuild changed visible lines.

```java
static int rowHeight(float naturalHeight, double multiplier) {
    return Math.max(1, (int) Math.ceil(naturalHeight * multiplier));
}
static float baseline(float naturalAscent, float naturalHeight, int rowHeight) {
    return naturalAscent + (rowHeight - naturalHeight) / 2f;
}
```

  EditorTextView implements paint, preferred spans, modelToView, viewToModel, visual next position and document
  updates from the same Row list. Draw each full shaped segment once; selection uses TextLayout highlight shapes
  and clipping, preserving shaping at selection edges. Translate hitTestChar insertion offsets back to source and
  clamp to valid boundaries. Consult RSTA FoldManager to omit hidden lines. Preserve the existing character-wrap
  behavior selected by `getWrapStyleWord`; keep RSTA's document, parser, actions, caret and undo manager.
  Override VimTextArea line height/ascent consistently and update gutter row metrics; line numbers must align
  with wrapped/folded view positions. Preserve current-line/bracket/occurrence highlights and hyperlink clicks.
  Avoid vendor package injection, private reflection and a patched RSTA artifact.
  Admit only the exact new document-view owners/operations in the architecture checker, retaining rejection of
  ordinary control construction/styling elsewhere. This task must pass before preview work builds on its geometry.
- [ ] **Run green:** geometry tests, entire Vim suite and headless source images in light/dark/glass. Inspect
  rendered PNGs; font property assertions alone do not verify glyph fallback or ligatures. Run presentation gate.
- [ ] **Commit:** `feat(editor): unify shaped source layout and typography geometry`.

## Task 4: Parse Markdown into bounded controlled output

**Files:** Create `E/markdown/MarkdownMode.java`, MarkdownLimits, MarkdownResult, MarkdownRenderer,
HeadingIds, CodeHighlighter and package-info; modify Editor build.gradle.kts; tests in `ET/markdown/`;
add CommonMark license and notice entries under `jasper-app/src/main/resources/dev/jasper/app/about/licenses/`.

**Interfaces:** MarkdownMode enum SOURCE/SPLIT/PREVIEW with lower-case `key()` and strict `parse(String)`.
`MarkdownRenderer.render(String source, SyntaxPalette palette): MarkdownResult`.
MarkdownResult contains HTML, immutable `List<BlockAnchor>`, `Map<String,Integer> headings`, links and images.
`BlockAnchor(String id,int sourceStart,int sourceEnd)` uses UTF-16 offsets; link/image IDs are generated integers.
HTML attributes refer only to IDs, never original destinations. Complete metadata shapes:
`MarkdownResult(String html, List<BlockAnchor> blocks, Map<String,Integer> headings,
List<Link> links, List<Image> images)`; `Link(int id,String destination)`;
`Image(int id,String destination,String alt)`; copy every collection in the compact constructor.
`MarkdownLimits.requireSource(String): void` throws `PreviewLimitException` (a public final runtime
exception with package-private constructor for the cross-package controller catch) before parsing; HTML and anchor builders throw the same exception at their bounds. The controller
maps it to "This document is too large to preview" with a Source action.

- [ ] **Write parser tests** using a golden fixture covering headings, nested lists/quotes, tables and alignment,
  task lists, strikethrough, escaped/raw HTML/comments, fenced/indented code, autolinks, image alt text,
  supplementary characters, repeated headings and heading suffix collisions. Assert source bounds, UTF-8 HTML
  bounds and map bounds; no Swing document is allocated on a rejected result.

```java
@Test void headingNamesCannotCollideWithEarlierSuffixes() {
    var ids = new HeadingIds();
    assertEquals("a", ids.next("A"));
    assertEquals("a-1", ids.next("A-1"));
    assertEquals("a-2", ids.next("A"));
    assertEquals("section", ids.next("!!!"));
}
```

- [ ] **Run red:** `./gradlew :jasper-plugin-editor:test --tests 'dev.jasper.editor.markdown.*'`.
- [ ] **Implement** with CommonMark source spans for blocks and inlines and the three approved extensions.
  Add four `implementation("org.commonmark:<artifact>:0.30.0")` entries. Resolve the actual artifacts; retain
  pinned versions and include BSD-2-Clause text/notice inventory. Use a controlled visitor with an explicit tag
  allowlist supported by PreviewKit. Omit comments, escape other HTML as visible text, escape attributes
  separately. Emit static checkbox glyphs, table alignment, per-code-line anchors and safe metadata IDs.
  Unknown code languages stay plain. CodeHighlighter creates isolated tokenizers per render and carries token
  state across lines; share only EditorTheme's token-role mapping, not a live area's tokenizer.
  HeadingIds follows spec section 7.2 exactly and checks every allocated suffix. Bound output during emission
  using UTF-8 byte accounting, not after building an unbounded String. Check interruption between blocks.
  Anchors span nested list/quote interiors, table rows and code lines; marker text is empty.
- [ ] **Run green:** parser tests and dependency/license verification. Record resolved coordinates; no runtime download.
- [ ] **Commit:** `feat(editor): render bounded Markdown with source mappings`.

## Task 5: Add theme-aware document toolbar artwork

**Files:** Modify `jasper-ui/src/main/java/dev/jasper/ui/icons/BuiltInArtwork.java`, IconRegistry,
`jasper-ui/src/main/resources/dev/jasper/ui/icons/`; FakeKit's built-in set; create `E/ui/MarkdownToolbar.java`.
Tests BuiltInArtworkTest, IconRegistryTest, shared KitContractTest, `ET/ui/MarkdownToolbarTest.java`.

**Interfaces:** Built-ins `editor.source`, `editor.split`, `editor.preview`, `editor.scroll-sync` and
`editor.source.classic`, `editor.split.classic`, `editor.preview.classic`, `editor.scroll-sync.classic`.
`MarkdownToolbar.node(MarkdownMode mode, boolean linked, boolean ready, boolean eligible,
Presentation presentation): Nodes.Stack`; action IDs `markdown.source`, `markdown.split`, `markdown.preview`,
`markdown.scroll-sync`. The component returns an invisible stack when ineligible.

- [ ] **Write failing tests** for all eight names in host/fake, light/dark authored rendering, selected-state
  descriptors, accessible names and Source/Split/Preview exclusivity. Existing six classic chrome meanings
  and unrelated body icons must keep their routing.
- [ ] **Run red:** `./gradlew :jasper-ui:test --tests '*ArtworkTest' --tests '*IconRegistryTest' :jasper-plugin-editor:test --tests '*MarkdownToolbarTest'`.
- [ ] **Vendor the 16 verified upstream resources** listed in Refresh findings, with pinned source revision,
  license and `assets-editor.tsv` containing destination, upstream path and SHA-256. Add a separate lazily
  loaded editor catalog to avoid changing existing startup prewarm. Include catalog names in validation and
  retain original authored colors through the standard control route. Use Map.ofEntries for more than ten
  entries; do not grow the current Map.of beyond its limit. Add fake parity and missing/tampered-resource tests.
  MarkdownToolbar chooses the `.classic` suffix exactly when `presentation.icons()==Presentation.Icons.CLASSIC`
 . Use descriptor GHOST buttons with
  Optional selected state and exact spec tooltips; linked-scroll button appears only in Split. Group aligns
  right by placing a nonfocusable empty CAPTION Text first (the DOCUMENT inline row's fill), followed by the
  toggles. At widths below the group minimum, put the action row in the document bar's horizontal overflow
  treatment rather than clipping inaccessible buttons; test keyboard reachability and native minimum-size
  behavior. If a shared layout fix is required, constrain it to action-only document rows and add tests in
  `jasper-ui/src/test/java/dev/jasper/ui/swing/DocumentBarLayoutTest.java` for existing Find rows.
- [ ] **Run green:** icon/descriptor tests and actual kit renders for both presentations/variants. Document new
  names in `docs/sdk-icons.md`; preserve prior icon route pixel checks.
- [ ] **Commit:** `feat(ui): add classic and modern Editor preview actions`.

## Task 6: Resolve relative destinations through the original provider

**Files:** Modify EditorDocument, LocalDocument, RemoteDocument; create `E/api/RelativePaths.java`,
`E/markdown/resources/Destination.java`, Destinations and package-info; tests corresponding local,
remote and resource packages; Remote manifest becomes 0.15.1.

**Interfaces:** `EditorDocument.resolveRelative(String): Optional<EditorDocument>` default empty;
`RelativePaths.requireValid(String): String` validates the already-decoded API argument;
`Destinations.parse(String): Destination`. Destination is a sealed value with Web(URI),
Relative(String path, Optional<String> fragment), Fragment(String id), Unsupported(String reason).

- [ ] **Write failing tests** for spaces, Unicode, literal `+`, percent decoding exactly once, `..`, malformed UTF-8,
  NUL/control characters, URI authority, drive/absolute paths, backslashes, queries, dangerous schemes and
  unsupported providers. Assert Remote retains the same ConnectionIdentity, EditChannels and owner supplier,
  even after saved host/login configuration changes; same resource name on two routes yields distinct handles.

```java
@Test void keepsPlusAndDecodesExactlyOnce() {
    assertEquals(new Destination.Relative("a+b c.md", Optional.of("intro")),
        Destinations.parse("a+b%20c.md#intro"));
    assertEquals(new Destination.Relative("a%20b.md", Optional.empty()),
        Destinations.parse("a%2520b.md"));
    assertInstanceOf(Destination.Unsupported.class, Destinations.parse("//host/file.md"));
    assertInstanceOf(Destination.Unsupported.class, Destinations.parse("bad%FF.md"));
}
```

- [ ] **Run red:** `./gradlew :jasper-plugin-editor:test --tests '*DestinationsTest' --tests '*LocalDocumentTest' :jasper-plugin-remote:test --tests '*RemoteDocumentTest'`.
- [ ] **Implement** validation before provider calls, fragment splitting before percent decoding, strict UTF-8
  decoder REPORT behavior, no URLDecoder (it changes plus). LocalDocument resolves against its parent using
  its existing executor. RemoteDocument joins normalized remote path segments and retains its original fields;
  never use local Path for remote normalization. `..` may navigate above the source directory; filesystem root
  clamps according to the provider's path rules. Default method validates before returning unsupported.
  Remote adds only an override and existing-editor API references. Test its class against the original 0.10.0
  Editor API jar to prove optional older-Editor linkage without executing a new API call. Preserve dependency range.
- [ ] **Run green:** provider tests and `./gradlew verifyPluginArchitecture`; no network connections.
- [ ] **Commit:** `feat(editor): resolve Markdown resources in the original storage context`.

## Task 7: Present controlled HTML as specialized document content

**Files:** Create `E/ui/PreviewPane.java`, PreviewKit, PreviewImageView; modify DocumentArea and architecture
checker; tests `ET/ui/PreviewPaneTest.java`, PreviewRenderTest and checker negative fixtures.

**Interfaces:** `PreviewPane.prepare(MarkdownResult): Prepared` runs on the existing render worker; immutable events feed
`PreviewPane.install(Prepared, TerminalTypography, WorkspacePalette, SyntaxPalette,
Font bodyFont, double opacity): void`; `PreviewPane.editor(): JEditorPane`;
`PreviewPane.cancelInstall(): void`; `installedResult(): Optional<MarkdownResult>` is updated only after successful attachment, announced through geometryChanged. `installFailure(): Optional<RuntimeException>` reports async failure with the old installed result retained; reset on install/cancel.
`PreviewPane.anchor(String): OptionalInt`; `PreviewPane.image(int, BufferedImage): void`; `close()`.
`DocumentArea.preview(): PreviewPane`, `mode(MarkdownMode): void`, `dividerRatio(): double`.
PreviewPane supplies link ID callbacks; it never navigates or loads resources itself.
Construct it as `PreviewPane(IntConsumer activateLink, Runnable geometryChanged)` on EDT. `anchor` returns a
rendered-document offset. The body font comes from the mounted kit content component's inherited font; read it
on EDT and snapshot it for styling, never use UIManager or a look's immutable default as the user's current font.
Listen for mounted content font changes and refresh preview styles; test a live user UI-font family change while
preview is open. If the host does not propagate its current font into custom content, update CustomContents to
supply the current kit typography and announce changes through the component's normal font property, with
host/fake specialized-content tests; do not add a second public typography API for this.

- [ ] **Write failing real Swing tests** for all blocks, long code/table horizontal scroll, prose wrapping,
  image downscaling, empty marker text, proportional body/effective code typography, light/dark/glass,
  copy/selection and no HTTP/file access by HTMLEditorKit. A loopback request counter remains zero on install.
- [ ] **Run red:** `./gradlew :jasper-plugin-editor:test --tests '*PreviewPaneTest' --tests '*PreviewRenderTest'`.
- [ ] **Implement** a read-only JEditorPane with a fresh controlled HTMLDocument, stylesheet derived only from
  immutable host palettes/fonts and a custom view factory. PreviewImageView paints supplied bytes or inert
  placeholders; it does not extend behavior that calls Swing's ImageView loader. Resolve all image/link IDs
  against result metadata; ignore unknown IDs. Customize paragraph wrapping, code/table minimum spans and
  image maximum width. Use TextLayout-based code views sharing EditorFonts for ordered fallback/ligatures;
  HTML font-family CSS alone cannot guarantee those semantics. Empty markers are zero-size view anchors.
  Build/attach Swing documents on EDT. Source snapshot parsing remains on workers; keep EDT installation bounded
  and cancel an obsolete installation before attaching it. Preserve old installed content until replacement succeeds.
  DocumentArea owns a JSplitPane and both scroll panes; mode changes only reparent the existing source component.
  Keep ratio independently from divider pixel position and ignore resize callbacks during programmatic restore.
  Paint one background per surface with existing opacity semantics. Admit exact specialized owners and constructors
  in the gate with negative tests rejecting a toolbar button or arbitrary Swing control inside helpers.
- [ ] **Run green:** inspect the generated PNG matrix, assert text is readable at narrow widths/large fonts and
  layout bounds remain finite; run presentation architecture checks. Do not accept HTML-string tests as visual proof.
- [ ] **Commit:** `feat(editor): add native themed Markdown document surface`.

## Task 8: Own preview scheduling and lifetime per tab

**Files:** Create `E/ui/MarkdownController.java`; modify EditorTab, DocumentArea, EditorWindow and EditorPlugin;
add `ET/ui/MarkdownControllerTest.java` and test-only manual executors/clock fixtures.

**Interfaces:** `MarkdownController(EditorTab, Executor worker, Executor ui, Runnable changed)`;
`sourceChanged()`, `styleChanged()`, `active(boolean)`, `mode(MarkdownMode)`, `retry()`, `close()`.
Controller owns dirty state, monotonically increasing generation, latest pending snapshot and current result.
It queries ready/language/source/settings only on EDT. Package-private `flushDebounce()` drives deterministic tests.

- [ ] **Write failing tests** with manually controlled completions: 100 edits schedule one latest pending render,
  old text/theme generation cannot install, source-only/hidden cancels, initial Preview renders immediately,
  activation renders dirty text, moves retain state, close drops every callback, executor rejection leaves Source/Save
  working, oversized previews show the spec message and Retry works after returning under the bound.
- [ ] **Run red:** `./gradlew :jasper-plugin-editor:test --tests '*MarkdownControllerTest'`.
- [ ] **Implement** a separate nonrepeating 250 ms timer; keep existing 300 ms dirty notification unchanged.
  Check source document length before allocating its text snapshot. Snapshot on EDT; submit only immutable strings/palettes/settings to worker. Use one running job plus one
  replaceable pending slot; cancellation invalidates the generation even if the worker ignores interruption.
  Worker completion posts to EDT, checks closed/generation/visibility/identity, then installs. Always release
  running ownership in completion/failure so pending work can start. Theme changes can reuse parsed structure
  but must invalidate styled output; geometry-only resize does not parse. Register both true/false ContentTab
  activation; component showing/hierarchy changes cover hidden windows without treating moves as disposal.
  Do not tie all preview activity to EditorWindow.selectedTab: more than one host window may have an active tab.
  Disposing closes the timer/listeners/resources; moving changes the current host owner only.
- [ ] **Run green:** scheduler tests plus EditorTab/EditorWindow lifecycle tests. Assert no post-close writes,
  no worker reads of Swing and no source/session replacement when switching views.
- [ ] **Commit:** `feat(editor): schedule live previews with tab-owned cancellation`.

## Task 9: Wire modes, persistence, focus and commands

**Files:** Create `E/ui/MarkdownPreferences.java`; modify EditorSettings, EditorSettingsPanel, EditorEnvironment,
EditorTab, EditorWindow, StatusLine and MarkdownToolbar; tests MarkdownModesTest and MarkdownPreferencesTest.

**Interfaces:** `MarkdownPreferences(Supplier<MarkdownMode> saved, Function<MarkdownMode,CompletableFuture<Void>> write)`;
`MarkdownPreferences.current(): MarkdownMode`,
`select(MarkdownMode): CompletableFuture<Void>` serializes saved choices; plugin-wide owner.
`EditorTab.markdownMode(MarkdownMode mode, boolean explicit)`, `focusContent()`, `revealSource()`.
`revealSource` enters Split from Preview without changing preferences. `markdown_view_mode` is a declared choice.

- [ ] **Write failing tests** for first-use Source, saved default across restart, existing-tab isolation, repeat
  explicit clicks, ordered saves/failure recovery, syntax away/back, untitled Markdown, divider retention,
  focus after mode switch/window activation/move, Vim/undo/caret/selection preservation and pending-load disabling.
- [ ] **Run red:** `./gradlew :jasper-plugin-editor:test --tests '*MarkdownModesTest' --tests '*MarkdownPreferencesTest'`.
- [ ] **Implement** toolbar as first DOCUMENT child before BANNER. Mode controls remain visible in all Markdown
  modes; ineligible empty toolbar collapses. Source/Split focus source; Preview focuses preview; later host
  activation restores last valid content focus. Store source viewport before hiding, restore after layout.
  Add three no-default-shortcut commands through existing Editor command registration and hosted commands.
  Preferences update the in-memory default immediately, serialize Setting.set calls in click order and recover
  the tail after errors. Configuration edits affect only newly eligible tabs. Explicit same-mode click still saves.
  Cut/Paste/Delete/Undo/Redo key handling in preview never edits source. Explicit source-mutating menu/command
  actions first reveal source; preserve ordinary preview Copy/Select All. Fix hosted paste/copy callbacks currently
  hardwired to tab.area(). Ensure stale command lists do not route to the previously selected tab.
  Status in Preview labels line/column as source coordinates; save/close continue using the original session.
- [ ] **Run green:** mode tests, Vim suite and hosted content command contracts; exercise moves while Preview focused.
- [ ] **Commit:** `feat(editor): add persistent Markdown modes and focus-safe commands`.

## Task 10: Load images with consent and shared resource bounds

**Files:** Create `E/markdown/resources/ImageBudget.java`, ImageLoader, ResourceSession;
modify MarkdownController, EditorPlugin, MarkdownToolbar and PreviewPane; tests ImageBudgetTest,
ImageLoaderTest, ResourceSessionTest using temporary files/fake providers/local HttpServer.

**Interfaces:** `ImageBudget.reserve(int width,int height): Optional<Lease>`; Lease implements AutoCloseable
and releases exactly once. `ImageLoader.load(EditorDocument): CompletableFuture<LoadedImage>` and
`load(URI): CompletableFuture<LoadedImage>`; LoadedImage owns BufferedImage plus Lease.
`ResourceSession` is per tab/document identity and consumes the plugin-wide loader/budget;
`allowExternal()`, `refresh(MarkdownResult)`, `active(boolean)`, `retryFailed()`, `identityChanged()`, `close()`.
It invokes EDT `BiConsumer<Integer,BufferedImage>` installs only after checking generation/identity.
Construct `ImageBudget(long maximumBytes)` with `64L*1024*1024` in production and smaller test budgets.
`ImageBudget.usedBytes(): long` is package-private. LoadedImage implements AutoCloseable and owns exactly one
lease; a shared cache entry owns LoadedImage, individual image views borrow it and do not close it.
ResourceSession owns a stable opaque context key; resolved handles do not derive cache identity from labels.
Decoded images are installed on EDT only; budget accounting is synchronized because decode workers reserve.

- [ ] **Write failing tests** for all exact limits, duplicate coalescing, consent off/on/new URL/Save As/reset,
  same file reload, two routes, corrupt/oversized/decompression-bomb headers, first GIF frame, absent/false Content-Length,
  redirects including downgrade/user-info, body timeout, queued cancellation, hidden/closed tab late completion,
  all image failures leaving text usable, shared budget under concurrent decodes and retry after failed load.
  Include exact shared-budget accounting, not only per-request limit checks:

```java
@Test void reserveCountsInflightPixelsAndReleaseIsIdempotent() {
    var budget = new ImageBudget(64L * 1024 * 1024);
    var lease = budget.reserve(4000, 4000).orElseThrow();
    assertEquals(64_000_000L, budget.usedBytes());
    assertTrue(budget.reserve(1000, 1000).isEmpty());
    lease.close();
    lease.close();
    assertEquals(0, budget.usedBytes());
    assertTrue(budget.reserve(1000, 1000).isPresent());
}
```

- [ ] **Run red:** `./gradlew :jasper-plugin-editor:test --tests 'dev.jasper.editor.markdown.resources.*'`.
- [ ] **Implement** a plugin-wide four-permit queue without blocking EDT. Provider-relative handles are created
  on EDT and their `read(8*1024*1024)` futures supply bytes; decode in worker. Decode format by ImageReader,
  check dimensions before allocation, reserve `(long)width*height*4` before decoding; release on every failure,
  cancellation or eviction. Keep one decoded image, scale while painting, no duplicate scaled cache. Hidden images
  release first; visible-budget exhaustion shows a reason placeholder. Budget includes in-flight allocations.
  HTTP uses Redirect.NEVER, checked redirects, streamed byte counting, 10s connect timeout and a 20s deadline
  spanning redirects/body; cancel closes stream/request. Do not inherit cookies/authentication. No unsupported
  destination reaches HttpClient or Swing. Cache keys include the owning provider context and document identity;
  repeated IDs within the same document share a loaded result. Keep resource consent in ResourceSession only.
  Reload invalidates relative cache; Retry clears failed entries; Save As replaces identity/consent. Alt text or
  file name plus reason stays visible on failures. Descriptor actions expose Load external images and Retry.
- [ ] **Run green:** resource suite under controlled concurrent futures, assert permits/leases return to zero after
  shutdown and no requests before consent. Run RemoteDocument tests to check cancellation propagation.
- [ ] **Commit:** `feat(editor): load preview images with bounded document-scoped resources`.

## Task 11: Link scrolling through current source anchors

**Files:** Create `E/ui/ScrollLink.java`; modify MarkdownController, PreviewPane and DocumentArea;
add `ET/ui/ScrollLinkTest.java` and real-layout mapping fixtures.

**Interfaces:** `ScrollLink(DocumentArea, PreviewPane)`, `install(long generation, MarkdownResult)`,
`invalidate()`, `linked(boolean)`, `geometryChanged()`, `close()`.
Track leading pane, current map generation, programmatic adjustment token and preview reading anchor.
Reading anchors use Swing Position plus within-block fraction and bottom flag.

- [ ] **Write failing tests** for both scroll directions, wraps/folds, nested blocks, table/code interiors,
  gaps/empty documents, image completion, resize/font change, large edits before the anchor, deleted anchor,
  bottom retention, disabled sync, repeated deferred viewport events and a user scroll superseding restoration.
- [ ] **Run red:** `./gradlew :jasper-plugin-editor:test --tests '*ScrollLinkTest'`.
- [ ] **Implement** sorted source-offset/preview-element correspondence. Source y coordinates come from the
  actual text view; folded offsets map to the visible fold start. Locate the top visible source/preview position,
  bracket it by anchor spans, interpolate inside that span, clamp to scroll bounds. Never equate scrollbar ratios.
  Record the leading pane on user motion, not on the programmatic follower event. Retain expected target and
  adjustment token across the EDT event turn; ignore the matching deferred event, but a differing user position
  takes leadership immediately. Edits invalidate mappings before scheduling a render. Capture Position-based
  reading anchor before install; restore only if generation and user-scroll sequence still match. Deleted anchors
  select the nearest surviving location. Linked mode aligns from the leader; independent mode preserves each
  position. Geometry changes recompute bounds without reparsing. Entering Split with sync enabled follows source.
- [ ] **Run green:** deterministic event-order tests and real-layout fixtures; verify no oscillating event loop.
- [ ] **Commit:** `feat(editor): synchronize Markdown panes by source passage`.

## Task 12: Search rendered content and navigate links

**Files:** Create `E/ui/FindTarget.java`, PreviewSearch, MarkdownNavigation; modify FindBar, EditorWindow,
EditorTab, StatusLine; tests PreviewSearchTest, MarkdownNavigationTest and existing FindBarTest.

**Interfaces:** FindTarget is a concrete sealed value Source(RSyntaxTextArea)/Preview(PreviewPane).
FindBar constructor takes `Supplier<Optional<FindTarget>>`; captures target when opened, retaining it while
query gets focus. PreviewSearch provides mark/find over rendered document text with the current SearchContext.
`MarkdownNavigation.follow(EditorTab origin, Destination destination): void`; uses EditorWindow's existing
open/de-duplicate flow and returns pending fragment handling to the target tab's controller.

- [ ] **Write failing tests** for rendered-text-only matching, code text included/markers excluded, invalid regex,
  zero-length patterns, Unicode whole words, wrap/next/previous/count, live preview refresh, target stability,
  Replace/Go to Line temporary source reveal, same-file/cross-file fragments, dirty target reuse, delayed target
  close, unsupported/missing destinations, external browser handoff and no image-only recent-file entries.
- [ ] **Run red:** `./gradlew :jasper-plugin-editor:test --tests '*PreviewSearchTest' --tests '*MarkdownNavigationTest' --tests '*FindBarTest'`.
- [ ] **Implement** source branch with existing SearchEngine. Preview branch searches `HTMLDocument.getText`,
  applies Highlighter tags and scrolls matches in the preview; matches refresh when a new result is installed.
  Use the existing regex validation/case/word semantics and guard zero-length match progress. Closing Find
  restores its captured content target; if that target was disposed, select the current valid target safely.
  Replace and Go to Line call revealSource before opening; they do not save mode preferences.
  Link activation gets only metadata IDs and calls Destinations. Fragment navigation waits for target load/render,
  validates destination identity/generation and focuses only after the user's explicit navigation. Source mode
  moves its source caret to the heading; other modes scroll preview. Missing heading posts a small notice.
  HTTP(S) invokes `context.platform().browse` on click; relative text opens through existing Editor API/model,
  preserves unsaved target text and recents rules. Untitled relative links report save-first. Keyboard link
  activation follows the same path; copy/select-all use preview selection.
- [ ] **Run green:** focused tests plus EditorWindow/FindBar full tests; no external browser is launched by tests.
- [ ] **Commit:** `feat(editor): search previews and navigate Markdown links`.

## Task 13: Verify the deliverable and update user/developer docs

**Files:** Update `docs/editor.md`, `docs/editor-verification.md`, `docs/plugin-authoring.md`,
`docs/sdk-architecture.md`, `docs/sdk-icons.md`, `jasper-sdk/README.md`, relevant package-info files,
Editor settings template if one is present, `docs/STATUS.md` and this plan banner.
Add `ET/ui/MarkdownAcceptanceTest.java` and headless render fixtures; update packaging/notice fixtures as needed.

**Interfaces:** No new API. Tests exercise the shipped plugin through FakePluginHost and staged host contracts.

- [x] **Write the failing integrated acceptance test**: open local and fake-SFTP Markdown in two host windows,
  edit unsaved content, follow fonts, change theme, select Split/Preview, search, navigate into an already dirty
  tab, load an allowed image, move/hide/reactivate, save/reload/Save As, then close during pending work.
  Assert source identity/undo, no cross-tab preference changes, consent reset, route preservation and zero retained
  subscriptions/leases/work after shutdown. Include classic and modern toolbar rendering at narrow widths.
- [x] **Run red** before any missing integration fixes, then fix only demonstrated failures in the owning class.
  If all cases already pass, record the first observed passing run; do not manufacture a red test by breaking code.
- [x] **Run headless final checks:**

```sh
./gradlew check
./gradlew sdkRelease
./gradlew :jasper-app:packageApp :jasper-app:verifyPackage
git diff --check
```

  No `jasper.aot` property, packageDist, trainAotCache, run or bench. If the known Python 3.14 theme JSON depth
  failure recurs, use the installed Python 3.13 through `-Pjasper.python=<verified absolute path>` and record it;
  do not weaken the test. Read exact counts from all `*/build/test-results/test/TEST-*.xml`, not console estimates.
  Inspect all new PNGs for source/preview light/dark, classic/modern, 0.5/1/3 line height, 6/13.5/72 point size,
  narrow panes, tables/code/images, glass and selection. Compare established default/Enterprise screenshots;
  intended changes are Editor font rows/document toolbar only. No lowered visual thresholds to hide regressions.
  Run the AGENTS.md source-hygiene Python check and validate all changed local Markdown links.
- [x] **Update documentation** with actual behavior/version/dependency evidence and user-run GUI acceptance.
  Keep native acceptance explicitly pending: trackpad sync, IME, font fallback/ligatures, physical keyboard focus,
  window moves, SFTP sign-in and late cancellation. Do not claim these from headless images.
- [x] **Commit:** `docs(editor): document typography and Markdown preview verification`.
- [ ] **Independent whole-branch review:** audit every spec section, source geometry, async ownership, URI handling,
  architecture exceptions and rendered evidence. Fix findings in focused commits and rerun affected checks.
  Keep branch unmerged/unpushed until the user authorizes integration.

## Coverage and self-review

| Spec section | Tasks |
| --- | --- |
| 1–2 intent, scope, native renderer | 1–13; no extra rendering engine/features |
| 3.1 eligibility/toolbar | 5, 8, 9 |
| 3.2 modes/persistence | 9 |
| 3.3 focus/commands | 9, 12 |
| 4 rendering/appearance | 3, 4, 7 |
| 4.1 typography | 1–3 |
| 5 ownership/lifetime/bounds | 4, 7, 8, 10 |
| 6 scroll mapping | 3, 4, 11 |
| 7 providers/destinations/resources | 6, 10, 12 |
| 8 verification/delivery | each task plus 13 |

Self-review also checked Gradle project names against settings.gradle.kts (`jasper-plugin-editor`,
`jasper-plugin-remote`), the actual Presentation.Icons enum and the document-row fill behavior.
Self-review checked the five Review Focus cases against their owning tasks, every spec section, current-main
version/API drift, metadata-ID flow, document identity boundaries, and source/preview lifetime separation.
The most substantial implementation is Task 3: replacing source view geometry while retaining RSTA/Vim behavior.
It is deliberately early so preview mapping cannot hide a source regression. No font or rendering feasibility
success is claimed until its actual layout/render/Vim tests pass. Execution should stop and report a concrete
incompatibility if the public RSTA view extension points cannot meet these contracts; do not silently drop
fallbacks, ligatures or exact hit testing.

Execution approved October 20: subagent-driven implementation, independent per-task review, then whole-branch review.
