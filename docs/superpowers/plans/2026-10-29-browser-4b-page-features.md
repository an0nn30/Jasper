# Browser 4b: page features — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or
> superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Execution status (2026-10-31): Tasks 1-11 executed; Task 12, the user's GUI check on macOS, is pending** (branch
`claude/browser-page-features`, worktree `.claude/worktrees/browser-4b-4c-e6deab`, cut from local `main` `bcc77624d`,
which has plan 4a merged; commits `c48158883` to `67c9d131a`, then the docs; not merged or pushed; see
`docs/STATUS.md`). Executed with per-task subagents and reviews; the controller's rulings are numbered P1-P44, and
plan 4a's R0-R48 bind. The task reviews recorded these deviations from the plan's text, each with its ruling:
- **SDK:** the additions are `@since 0.27.0`, and every bundled range is `>=0.27.0, <0.28`.
- **A plan-text error (P11):** Task 9's fallback reads `CefBrowser.getSource(CefStringVisitor)`; `CefFrame.getSource`,
  which the plan names, does not exist in JBR's JCEF (checked with `javap`).
- **Task 1 ran as three slices (P9: one dispatch was too large to review well),** each with its SDK values, testkit
  fake, kit drawing under the default and Enterprise (classic) kits, host side, contract cases, guides and
  `sdkReference`: 1a images (`c48158883`, fix `584a09a64`), 1b field suggestions (`19ee6a220`), 1c hold menus, submenus
  and `showContextMenu` (`7413cff0c`, fix `923b8c1f8`):
  - `PixelImage` checks its length on the argument and its PNG signature on its own copy, and the kit decodes PNGs
    without their metadata (P15: favicons are untrusted input);
  - `Nodes.Field` also gained `leadingIcon`, shown while there is no image, so the Browser shows its registered globe
    in the kit's icon colours (P14), and `leadingName`, the slot's accessible name and tooltip, because spec section 6
    names the favicon "Site icon - drag to copy URL" (P13); the leading icon alone can be dragged;
  - a TEXTAREA, STEPPER or SLIDER takes no leading image or icon and no `selectOnClick`, and a TEXTAREA or NUMBER field
    no suggestions (P15: the kit would have ignored them);
  - `selectOnClick` selects all only on the click that brings the focus into the field, and a click in a focused field
    places the caret, as Chrome's and Safari's address bars do (P12);
  - a click chooses a suggestion, as a drawn list must take clicks, and hovering selects one, which P16 rules should
    only highlight (left to the final fix wave);
  - `showContextMenu(Point at, List<MenuItem> leading, List<MenuItem> trailing, Consumer<Optional<String>> chosen)`:
    the host's pane items go between the two lists, as spec section 11 orders them (P2); `at` is on screen, as a plugin
    holds no reference to its tab's component (P17); `chosen` hears `Optional.empty()` for a host item, a dismissal or
    a tab not on screen; `PluginPresentationCheck` allows `java.awt.Point` in `ContentTab` only; the menu is the
    terminal's themed native popup, with exactly Split ▸, Move Pane to Tab ▸ and Close Pane;
  - a hold menu also opens at once on a right-click (Control-click on macOS), Shift+F10 and the context-menu key, as
    Chrome opens its history on a right-click (P20); a press that leaves the button before 500 ms opens nothing, as
    leaving cancels a click (P19); a submenu has a blank action and no shortcut;
  - `MenuItem` has no tooltip, so Task 3's history entries have none (P18);
  - testkit additions: `FakeKitView.clickInto`, `press`, `suggestions`, `selectedSuggestion` and `choose`, and
    `FakePluginHost.contentMenu`, `chooseFromContentMenu` and `dismissContentMenu`.
- **Task 2:**
  - the bridge router dispatches by op with per-op trust (P3, as find must reach frames): `favicon` from the main frame
    only, `find` (Task 5) from any frame of the item's own browser, the opener ops as 4a's R38;
  - favicons are fetched only for `http` and `https` pages and icons, without cookies, within 5 s and 64 KiB, since a
    page can forge the request (P10), so `data:` and SVG favicons show the globe; `jasper-about:` pages show Jasper's
    icon;
  - `Favicons(decoder, executor)` takes the decoder, which lives in `item` (a package cycle otherwise), and an
    executor that keeps the first `HttpClient` off the UI thread; the first favicon a document declares wins; the
    address check ignores the fragment.
- **Task 3:**
  - the history is read after each main-frame load end, commit and title change, one read at a time, not when a press
    begins (P1: the SDK's hold menu is a list the plugin sets in advance);
  - each menu lists at most 12 entries, nearest first, as Chrome's do, since Swing popups do not scroll (P23); "Show
    Full History" comes with plan 5's history page;
  - a label is the title, or the URL without user info, cut at 80 characters (P18);
  - one strict JSON reader, the new JDK-only `dev.jasper.browser.json.StrictJson`, serves the history and the bridge;
    `Page` takes a history seam.
- **Task 4:**
  - `SearchEngine.queryOf`, `Suggestions.rank`, `BrowserToolbar.node` and `BrowserItems` take the settings, which a
    Custom engine's results page is recognised by;
  - the field shows a results page's terms whenever nothing is typed, not only while focused (P25: the SDK has no focus
    event; Safari shows them so); the tab, the saved URL and the drag keep the URL;
  - no suggestion request for an absolute local path, a `file:`, `about:` or `jasper-about:` address, user info (P24:
    local paths and passwords must not reach the engine), blank text or text over 1,000 characters;
  - `localhost`, a host with a port and IP literals rank their Navigate row first, while a bare word keeps the spec's
    search-first order (P27: Chrome navigates them, and searching them sends intranet names to the engine);
  - a single word is ASCII with a letter or `_`, and `c#` and `why?` are searches;
  - a Custom URL must be an absolute `http` or `https` template with `%s` after its host and no user info, refused with
    one message for both, "The URL must start with https:// or http:// and contain %s", replacing the plan's "The URL
    must contain %s" (P28, amended: the SDK's text rule has one message per setting); only an empty Custom search URL
    searches with Google;
  - Search with names a Custom engine by its search URL's host, "Custom" without one (P26);
  - the plugin's own fetches share the new JDK-only `dev.jasper.browser.http.WebFetch` and go direct, with the JDK's
    default proxy selector, until plan 6 brings the proxy setting (P22: routing them through Chromium is a redesign);
  - suggestion ids say what a row loads (`navigate:…`, `search:…`); `item` and `settings` depend on each other.
- **Task 5:**
  - the real main-frame flag now reaches the bridge's `request()` (P21), before the `find` op arrived;
  - `find.js` highlights with the CSS Custom Highlight API instead of wrapping text in elements, so the page's DOM and
    scripts are untouched and `clear()` is exact (P29);
  - matching runs a block at a time across inline elements, each run of white space as one space, within 250 ms a pass,
    answering a partial count; re-runs are throttled (300 ms, or ten times the last pass) rather than debounced;
  - seeding from the page's selection crosses the bridge as a bounded one-line string, as JCEF cannot read the
    selection (P30);
  - no V8 regular-expression flag, so a catastrophic `exec` of the user's own pattern can hang the page's renderer (P31:
    changing every page's engine for find is out of proportion);
  - only the main frame, listed first, says a pattern is invalid; a page's main frame and its sub-frames wait in
    separate bridge queues; at most 10,000 matches a frame, 100 frames and a 1 s wait; `pagehide` ends a document's
    search; Find Next with the bar closed searches again from the first match; the toggles' tooltips are the Editor's
    "Match Case" and "Regular Expression";
  - `find.js` has no committed tests, as the build has no JavaScript engine; Node smoke tests in the scratchpad and the
    GUI check carry it.
- **Task 6:**
  - zoom runs 25-500 %, not 5-500 % (P33: Chromium's zoom factors stop at 0.25 and 5.0), so Review Focus 5's 5 % floor
    is 25 %;
  - Ctrl+wheel zooms on Windows and Linux only, as spec section 7's key table says, and reaches the page on macOS,
    where momentum scrolling can carry Ctrl (P32); it steps by Chromium's rounded notch count;
  - no pinch on macOS (P35: `com.apple.eawt.event` is not exported to plugins);
  - Chromium keeps one zoom per host and JCEF has no zoom mode, so same-host items that show at once share the zoom set
    last; each item re-applies its own at main-frame load start and when it shows (P34);
  - Reset leaves the item no zoom of its own, so it comes back at Page zoom; `BrowserRestore.state(url, target, zoom)`
    replaced the two-argument form, and `BrowserRestore` keeps its own copy of the range.
- **Task 7:**
  - `PageMenuItems.of` returns leading and trailing lists (P2), and its `Context` gained `frame` and `titled`; new
    `page.MenuPlace`; the handler is installed from `Page.start`; extra `PageMenuTest`;
  - Save Link As… and Save Image As… are left out until plan 7 brings downloads (P6: they would do nothing), and Copy
    Image is Copy Image Address, as JCEF offers no copy-image command and no SDK image clipboard exists (P36);
  - Chromium's callback is never continued: it is cancelled once, and Jasper runs the commands CEF's defaults run,
    independent of JCEF's `MenuId` numbering (P37);
  - Back, Forward and Reload show only over a bare page, as Chrome's (P38), and Copy Page Title is disabled on an
    untitled page (P39);
  - Open Link and Open Image in New Tab follow 4a's frame scheme rule (`page.LinkSchemes.opensFrom`, now the one copy
    of 4a's scheme set) when enabled and when run, and Search with's terms are cut at 1,000 characters;
  - View Source and Inspect Element were listed here and filled in by Tasks 9 and 10 (P4);
  - Copy Link copies the link as the page wrote it, user info included, as Chrome copies its unfiltered link.
- **Task 8:**
  - `ErrorPage.title` takes Chromium's error name, not `CefLoadHandler.ErrorCode`, as `item` names no JCEF type; the
    address is `jasper-about:error?token=…&code=…&url=…`; `render` and `html(URI, palette, dark)` replace the plan's
    `html(...)`; the certificate is `page.ServerCertificate`, beside `page.LoadFailure`;
  - Continue carries a one-time random token, and the allowance is the host and SHA-256 the item kept from Chromium,
    never values from the address, which a `file:` page could forge (P7); one in-memory `CertificateAllowance` per
    plugin run is shared by every item (P8);
  - `ERR_CERT_REVOKED`, `ERR_CERT_INVALID` and `ERR_CERT_CONTAINS_ERRORS` offer no Continue, as Chrome treats them as
    fatal;
  - Back skips Chromium's failed entry through the history read (Chromium's own Back when the read is stale), and a
    successful Try Again leaves the error page as a Forward entry, as JCEF offers no history replacement (P40);
  - whether CEF asks about certificates on HSTS-pinned hosts is unverified, and Jasper keeps no HSTS list (P41);
  - "Not secure" stands before the field, whose leading slot is then empty, for `https` pages only, following the
    address last committed; `MenuPlace` gained `canGoBack`, `back` and `reload`; `CertificateAllowance.allowsAny`;
    `AboutScheme.register(String, Function<URI, String>)` and `registerAction(String, Consumer<URI>)`;
  - a failed `jasper-about:` load shows no error page; a forged or forgotten error address is never saved; per-item
    `ErrorPages` and `item.PageHtml`; extra `BrowserItemErrorTest`, `ServerCertificateTest`, `PageNavigationTest` and
    `PageEventsTest`.
- **Task 9:**
  - View Source loads `jasper-about:source?url=…[&token=…]` in the same item (P42): an `http` or `https` page's source
    is fetched fresh (no cookies, 10 s, 5 MB, a 200 only) whenever the source page loads; otherwise, and when that
    fails, it is the HTML captured with `CefBrowser.getSource` (P11) before the item navigates, kept under a 128-bit
    token, so a forged address reads no cookies or local content;
  - the token is looked up in every open item, as the scheme handler cannot tell which browser asked (P43);
  - `AboutScheme.registerLater` and an asynchronous resource handler keep the fetch off CEF's IO thread (P5);
  - `SourcePage.fetch` gives `CompletableFuture<Optional<String>>`, and the renderer is `html(String, String,
    WorkspacePalette, SyntaxPalette, boolean)` with `Appearance.syntaxPalette()`; per-item `SourcePages` keep 20
    captures within two of the longest; at most 5,242,880 characters show and the first 1,000,000 are highlighted; the
    restore state drops the token.
- **Task 10:**
  - the `createDevToolsBrowser` override is the one path, as the plan asked to record: an off-screen browser has no
    `openDevTools(point)` fallback (`CefBrowserOsrWithHandler`'s own returns `null`, and the windowed constructor is
    private);
  - `Page` makes the DevTools' `PageView`, as only `Page` may, and `DevToolsItem` holds a `page.DevTools` handle, as
    `item` names no JCEF type;
  - a second Inspect Element closes and reopens DevTools at the new point in the same item, each DevTools waiting for
    every earlier one to end, as JCEF pairs pending browsers first in, first out (P44); DevTools' state resets;
  - links DevTools open in a new tab open as `NEXT_TAB` under the inspected page's scheme rule; the DevTools browser
    gets no page scripts; DevTools have Copy and Paste and no find, zoom or right-click menu.
- **Task 11:**
  - `docs/browser.md` documents every feature, with the settings in its Settings table, which configuration's plugin
    table lists; `docs/keyboard-shortcuts.md` has Find and zoom in browser tabs;
  - `docs/sdk-icons.md` (`PixelImage`), `docs/plugin-authoring.md` (`withImage`, `showContextMenu`) and
    `docs/plugin-ui.md` (the new node components, where the SDK's nodes are documented) were written with slices 1a-1c;
  - `./gradlew check --continue` passed: 14,459 tests, 0 failures, 0 errors, 102 skipped (Browser 506; the 16 build
    folders without the example plugin's); the example external plugin's `check pluginZip` passed (1 test).

Known gaps and follow-ups:
- **Zoom:** no pinch on macOS (P35; a follow-up exports `com.apple.eawt.event` to the plugin); Ctrl+wheel only on
  Windows and Linux (P32); same-host items that show at once share one zoom (P34); the floor is 25 % (P33).
- **The right-click menu:** no Copy Image (P36), and no Save Link As… or Save Image As… until plan 7 (P6).
- **History menus:** 12 entries, no "Show Full History" until plan 5 (P23), and no per-entry icons or tooltips (P18).
- **Find:** shadow roots and form fields are not searched; a catastrophic regular expression can hang the page's
  renderer (P31); the page's scripts see the query and can forge their frames' counts, which are only shown; a partial
  count shows no sign of it; a sub-frame that navigates keeps its count until the next search.
- **Jasper's own requests:** favicons, suggestions and View Source's fetch go direct and send Java's User-Agent until
  plan 6's proxy setting (P22); SVG, WebP and `data:` favicons show the globe, and a page cannot change its favicon
  later.
- **Address field:** a scheme-less `name:secret@host` typed character by character can reach the engine before the
  `@`; a click in the field closes its suggestion list until the next keystroke (Chrome keeps it).
- **Error pages and certificates:** a successful Try Again leaves a Forward entry (P40); HSTS hosts (P41); a host with
  an underscore gets no Continue, failing safe; a plain `mailto:` click may show "Page Can’t Be Loaded" (unverified).
- **View Source:** a non-web page's source does not survive a restore (P42); the address field shows the token.
- **DevTools:** their state resets on each Inspect Element (P44); no context menus; their file dialogs are cancelled;
  Cmd+[ and Cmd+] (Alt+Left and Alt+Right) navigate the inspected page.

Deferred minors, for the final review and its fix wave:
- 1a: the testkit README's `clickInto` row; `HostedKit.image(EncodedArtwork)` no longer catches the >512 px refusal;
  `TextFields` revalidates the field container on every refresh (pre-existing).
- 1b: hovering moves the keyboard selection (P16); the temporary-focus-loss comment and test wording; a field with both
  a `listId` and suggestions takes Up and Down for both; `KitOwnership.SUGGESTIONS`' Javadoc;
  `HostedKitContractTest.press` bypasses the focus manager; a press inside the field closes the list.
- 1c: a press that leaves and comes back within 500 ms still opens the hold menu, while the docs say leaving opens
  nothing; `FakePluginHost.dismissContentMenus` iterates live values; a stray blank line.
- 2: a `replaceState` or `pushState` during a favicon fetch drops the icon for the document; the UI thread can wait on
  `Favicons`' monitor while the first client builds; addresses with `{}|^` show the globe; an untested BMP case;
  `OpenerBridgeTest`'s stderr warning (4a).
- 3: an unasserted counter in `NavigationHistoryTest`; the `json` package-info's wording.
- 4: templates refuse underscore and IDN hosts without saying why; `AddressInput.resolve`'s Javadoc; a fetcher test
  that cannot tell `close()` from the timeout.
- 5: `find.js`'s final run has no deadline in its empty-match loop; a run collected at the deadline can be dropped; a
  block inside a `display: none` ancestor ends the run; `getComputedStyle` on every visited element; ruby and math
  count as blocks; collapsing dense white space is one uninterruptible replace.
- 6: a zero-rotation Ctrl+wheel can step on a carried remainder; a stale "5% to 500%" test comment; a theoretical NaN;
  a field's placement in `PageView`.
- 7: a long `page/package-info.java` line; no direct `LinkSchemesTest`; `BrowserRestore`'s copy of the scheme set and
  `AddressInput.SCHEME`.
- 8: no error page for any failed `jasper-about:` address also hides real failures of Jasper's own pages.
- 9: a local page can ask for many source pages at once (memory); captures are bounded per item, not plugin-wide;
  charset labels; untested branches; the class doc on the IO thread; `SourcePage.java`'s size; `NOT_FOUND` twice; a
  1 s negative wait in a test; the raw-text set lacks `plaintext` and `noscript`.
- 10: `PageTestAccess`'s serialization-constructor helper (JEP 500); security: the page-browser branch of
  `onOpenURLFromTab` follows `file:` and `jasper-about:` in the same item without a gesture, to be guarded with
  `LinkSchemes.opensFrom`.

Task 12 (the user's GUI check on macOS, with their OK): pending, the plan's ten lines plus those the task reviews added
([checklist](../../browser-verification.md#plan-4b-page-features)); Windows and Linux are not run; memory and
benchmarks are deferred.

**Goal:** Give plan 4a's browser item iTerm2's page features:
- the favicon in the tab and the address field;
- long-press Back and Forward history;
- address suggestions and the search-engine setting;
- find in page;
- zoom;
- the page's right-click menu;
- error pages, and certificate errors with Chrome's way past them;
- View Source;
- Inspect Element.

**Architecture:**
- **The SDK** gets the presentation pieces a plugin cannot draw itself (spec section 3): an image icon for tabs and
  fields, field suggestions, a hold menu on buttons, submenus, and a right-click menu at a point over content that the
  host completes with its pane items.
- **Everything else stays in `plugins/browser`,** on 4a's classes:
  - new handlers slot into `PageEvents`, `PageNavigation` and a new context-menu handler, one per type per client (4a's
    R31);
  - page scripts are injected at main-frame load start (R39);
  - new `jasper-about:` pages get the request's address.

**Tech Stack:** Java 25 and Swing on JBR 25.0.4.1 b635.75 with JCEF, Jasper's SDK and testkit, JUnit 5, and
`java.net.http` for search suggestions, favicons and View Source.

**Spec:** [docs/superpowers/specs/2026-10-26-jasper-browser-plugin-design.md](../specs/2026-10-26-jasper-browser-plugin-design.md),
sections 3, 6, 7 (Find and zoom keys), 10 (Find, Zoom, View Source, Inspect Element), 11, 14 (error pages and
certificates), 16 and 17. It builds on
[plan 4a](2026-10-28-browser-4a-browser-core.md), whose banner's rulings R0-R48 bind this plan too.

## Global Constraints

- **Branch:** from `claude/browser-core` (plan 4a, rebased onto `origin/main`) once 4a's Task 13 has passed, or from
  `main` if 4a has merged by then, in its own worktree.
- **SDK version:** this plan's SDK additions take the next minor after `JasperSdk.VERSION` on the branch it starts
  from. That is 0.27.0 if 4a's 0.26.0 is there. They carry that `@since`, and every bundled manifest's range moves
  with them.
- **4a's names** (at `claude/browser-core-88616a`; follow any renames the rebase made):
  - `Page` (`page/Page.java`), `PageView`, `PageBrowser`, `PageEvents.install` (Display, Load and Keyboard),
    `PageNavigation.install` (Links, Popups, the bridge) and `PageDialogs`;
  - `BrowserItem` (`act`, `event`, `spec()`, `present()`, `restore(URI)`), `BrowserToolbar.node(...)`, `BrowserItems`,
    `AddressInput`, `TabTitle` and `BrowserSettings.Declared`;
  - `BrowserRestore` (`KIND`, keys `url` and `target`), `AboutScheme`, `WelcomePage.render(WorkspacePalette, boolean)`
    and `Chromium.onClient`.
- **Boundaries:** `org.cef` only in `engine`, `page`, `links` and `dialogs` (`ChromiumBoundaryTest`); add `find` and
  `menu` to its allowed list if those new packages need JCEF. Painting only in `PageView` (R7). No engine fake (R29).
  The first Chromium start happens only when an item shows (R18).
- **JCEF facts** (`javap`, 2026-10-28):
  - **Favicons:** there is no favicon callback.
  - **History:** there is no navigation-entries call; DevTools' `Page.getNavigationHistory` and
    `Page.navigateToHistoryEntry` through `CefBrowser.getDevToolsClient()` give the list and the jump.
  - **Menus:** `CefContextMenuHandler.runContextMenu(..., CefRunContextMenuCallback)` lets Jasper draw the menu.
  - **Certificates:** `onCertificateError` goes in `PageNavigation.Links`, the client's only request handler.
  - **DevTools:** `CefBrowserOsrWithHandler.createDevToolsBrowser(CefClient, String, CefRequestContext, CefBrowser,
    Point)` is protected and overridable.
  - **Find:** `CefBrowser.find` has no regular expressions.
- **No GUI from agents:**
  - Tests are headless and start no Chromium. The user's GUI check is Task 12.
  - Never run `:jasper-app:run`, benchmarks or AOT training without the user's OK, batch by batch. Never run
    `./gradlew --stop`.
- **Copy:** the spec's strings with "iTerm2" read as "Jasper". **No animations.** Every platform, with the spec's keys.
- **No user info** (R46): a URL shown in a history tooltip, View Source or an error page drops its user info.
- **Commits** end with:

  ```
  🤖 Generated with Claude Code at The Home Depot

  Co-Authored-By: Claude <noreply@anthropic.com>
  ```

## Review Focus

1. **Find on hostile pages:**
   - a 1,000-character query; an invalid regular expression ("Invalid pattern");
   - a page with cross-origin frames, and one that replaces its DOM while the bar is open;
   - closing the bar clears every highlight.

   Task 5 pins this.
2. **Address suggestions while the network is slow or down:**
   - the field stays responsive;
   - a stale answer never replaces a newer one;
   - "Loading suggestions…" goes away;
   - Enter before suggestions arrive uses `AddressInput`.

   Task 4 pins this.
3. **Certificates:**
   - "Continue to this site (unsafe)" allows only that host with that certificate;
   - a different certificate for the same host asks again;
   - the allowance ends when Jasper quits;
   - "Not secure" shows while such a page is current, and goes away on a secure page.

   Task 8 pins this.
4. **Favicons that are large, broken, ICO with BMP entries, SVG, or slow:**
   - nothing over 64 KiB is decoded;
   - a failure falls back to the globe;
   - a favicon never arrives on a different page than the one it belongs to.

   Task 2 pins this.
5. **Zoom limits:** 5% and 500% stop the steps, Reset goes to the Page zoom setting, and the zoom survives restore
   within the restore budget. Task 6 pins this.

---

### Task 1: The SDK's presentation pieces

**Files:**
- Create: `jasper-sdk/src/main/java/dev/jasper/sdk/ui/PixelImage.java`.
- Modify: `jasper-sdk/src/main/java/dev/jasper/sdk/ui/ContentPresentation.java`: add `withImage`.
- Modify: `jasper-sdk/src/main/java/dev/jasper/sdk/ui/Nodes.java`:
  - `Field`'s leading image, drag value, suggestions and select-on-click;
  - `Button`'s hold menu.
- Modify: `jasper-sdk/src/main/java/dev/jasper/sdk/ui/MenuItem.java`: submenus.
- Modify: `jasper-sdk/src/main/java/dev/jasper/sdk/ui/ContentTab.java`: add `showContextMenu`.
- Modify: `jasper-sdk/src/main/java/dev/jasper/sdk/ui/UiEvent.java`: add `SuggestionChosen`.
- Modify: the kit and host (`jasper-ui` kit field and button views, `jasper-app/.../plugins/HostedUi.java`, the
  content right-click path through `WindowChrome`'s pane items), the testkit fakes, the contract cases, the guides and
  `docs/sdk-icons.md`.
- Test: `UiValuesTest`, `PluginContractTest` (both runtimes) and the kit's view tests.

**Interfaces** (all `@since` the version above):
- **`record PixelImage(byte[] png)`.** It copies the bytes. It refuses more than 65,536 bytes, and data that isn't a
  PNG (signature check). The host decodes it once and draws it scaled into the icon's box, so it is the only bitmap
  the SDK takes. `docs/sdk-icons.md` says so.
- **`ContentPresentation.withImage(PixelImage image)`:** the tab shows the image instead of the icon name, which
  remains the fallback.
- **`Nodes.Field` gains:**
  - `leadingImage` (`Optional<PixelImage>`);
  - `leadingDrag` (`Optional<String>`): text dragged out of the leading image, offered as a URL and as text, plus a
    file when it is a `file:` URL;
  - `suggestions` (`List<Suggestion>`) and `suggestionsLoading` (boolean);
  - `selectOnClick` (boolean): a click into the field selects all, as focusing does.
- **`record Nodes.Suggestion(String id, String title, String detail)`.**
  - The kit draws the suggestions under the field, with the first selected, and "Loading suggestions…" while
    `suggestionsLoading` is true.
  - Up and Down move the selection.
  - Tab and Enter emit `UiEvent.SuggestionChosen(fieldId, suggestionId, boolean byTab)`.
  - Escape hides them and emits the field's cancel action.
- **`Nodes.Button` gains `holdMenu` (`List<MenuItem>`).** A press held for at least 500 ms opens it under the button.
  A shorter press is the button's ordinary action.
- **`MenuItem` gains `children` (`List<MenuItem>`):** a non-empty list makes the item a submenu.
- **`void ContentTab.showContextMenu(Point at, List<MenuItem> items, Consumer<String> chosen)`:**
  - it shows `items` at `at`, in the tab's component's coordinates;
  - then a separator and the host's pane items for that tab: Split ▸ (with every split item), Move Pane to Tab ▸ and
    Close Pane;
  - `chosen` hears the plugin's item ids, and the host runs its own items;
  - dismissing calls `chosen` with nothing.

- [ ] **Step 1: Write the failing tests:**
  - `PixelImage` refuses a non-PNG and a 65,537-byte PNG.
  - **The contract cases:**
    - a content tab's presentation image reaches the fake and the app;
    - a field's suggestions render, and Tab emits `SuggestionChosen(byTab = true)`;
    - a hold menu opens after 500 ms and not before (a fake clock in the kit test);
    - `showContextMenu` lists the plugin's items, a separator, then the host's pane items, and choosing a plugin item
      reaches `chosen`;
    - a submenu's children are reachable.

- [ ] **Step 2: Run** the SDK, testkit, kit and app tests. Expected: FAIL.

- [ ] **Step 3: Implement** the values, the kit's drawing (the default and Classic looks), the host side, the fakes,
  the version bump, the guides and `sdkReference`.

- [ ] **Step 4: Run** `./gradlew check`, then the external-plugin check. Expected: PASS. Commit the regenerated
  `docs/sdk-reference.html`.

- [ ] **Step 5: Commit** "SDK: images, field suggestions, hold menus, submenus and content right-click menus".

### Task 2: Favicons

**Files:**
- Create: `plugins/browser/src/main/java/dev/jasper/browser/page/Favicons.java` and
  `plugins/browser/src/main/java/dev/jasper/browser/item/FaviconDecoder.java`.
- Create: `plugins/browser/src/main/resources/dev/jasper/browser/page/detect-favicon.js`.
- Modify: `Page.java` (a `favicon` property, cleared on each main-frame load start), `BrowserItem.present()` and
  `BrowserToolbar.node(...)` (the field's leading image and drag value).
- Test: `FaviconDecoderTest.java` (fixtures under `src/test/resources/favicons/`) and `FaviconsTest.java`.

**Interfaces:**
- **`detect-favicon.js`** returns, through the opener bridge's message router (`jasperBridgeQuery`, op `favicon`),
  the absolute URL of the first `<link rel~="icon">` or `rel="mask-icon"`, else `<origin>/favicon.ico`.
- **`Favicons.fetch(URI icon, URI page) -> CompletableFuture<Optional<PixelImage>>`:**
  - it uses `HttpClient` with a 5 s timeout and a 65,536-byte cap, and no cookies;
  - the result applies only if `page` is still the item's address when it arrives.
- **`static Optional<PixelImage> FaviconDecoder.toPng(byte[] data)`:**
  - PNG, GIF and JPEG go through `ImageIO`;
  - ICO picks the entry closest to 32×32, whether a PNG entry or a BMP DIB;
  - the result is scaled to at most 32×32 and re-encoded as PNG;
  - SVG and anything else give empty.
- **Internal `jasper-about:` pages** use Jasper's app icon, a bundled PNG.
- **Fallback:** a globe shows while loading, and whenever there is no favicon.

- [ ] **Step 1: Write the failing tests:**
  - **Decoding:** a PNG, a GIF, an ICO with PNG entries (16, 32, 48), an ICO with BMP entries, a truncated ICO, an SVG
    and 70 KiB of noise.
  - **`FaviconsTest`:** with a local `com.sun.net.httpserver` fixture, the cap and the timeout hold, and an answer that
    arrives after the address changed is dropped.

- [ ] **Step 2: Run** the tests. Expected: FAIL.

- [ ] **Step 3: Implement.**

- [ ] **Step 4: Run** the tests. Expected: PASS.

- [ ] **Step 5: Commit** "Browser: favicons in the tab and the address field".

### Task 3: Long-press history

**Files:**
- Create: `plugins/browser/src/main/java/dev/jasper/browser/page/NavigationHistory.java`.
- Modify: `BrowserToolbar.node(...)` (Back and Forward hold menus) and `BrowserItem.act(...)` (a chosen entry).
- Test: `NavigationHistoryTest.java`.

**Interfaces:**
- **`NavigationHistory.read(PageBrowser) -> CompletableFuture<History>`** uses DevTools' `Page.getNavigationHistory`.
- **`record History(int current, List<Entry> entries)`** and **`record Entry(int id, String url, String title)`.**
- **`static List<MenuItem> NavigationHistory.backMenu(History h)`** and **`forwardMenu(History h)`:**
  - nearest first;
  - each label is the title, or the URL without user info when the title is empty;
  - the tooltip is the URL.
- **Choosing an entry** calls `Page.navigateToHistoryEntry(entryId)` and fires the page's `onNavigate` (4a's R42).
- **The list is read when a press begins,** so it is current when the hold menu opens.

- [ ] **Step 1: Write the failing tests:** parsing DevTools' JSON, both menu orders, the title and URL fallback, user
  info dropped, and an empty forward list giving an empty menu.

- [ ] **Step 2: Run** the tests. Expected: FAIL.

- [ ] **Step 3: Implement.**

- [ ] **Step 4: Run** the tests. Expected: PASS.

- [ ] **Step 5: Commit** "Browser: hold Back or Forward for the page's history".

### Task 4: Search engines and address suggestions

**Files:**
- Create, under `plugins/browser/src/main/java/dev/jasper/browser/item/`: `SearchEngine.java`, `Suggestions.java` and
  `SuggestionFetcher.java`.
- Modify: `AddressInput.java` (the engine, and 4a's open gaps), `BrowserToolbar.node(...)`, `BrowserItem.event(...)`
  and `act(...)`.
- Modify: `settings/BrowserSettings.java` and `settings.toml`: `search_engine = "google"`, `custom_search_url = ""`
  and `custom_suggestions_url = ""`, in a "Search Engine" section.
- Test: `SearchEngineTest.java`, `SuggestionsTest.java`, `SuggestionFetcherTest.java` and `AddressInputTest.java`.

**Interfaces:**
- **`enum SearchEngine { GOOGLE, DUCKDUCKGO, BING, KAGI, CUSTOM }`,** with the spec's section 6 URLs and `%s`:
  - `URI search(String query, BrowserSettings s)`;
  - `Optional<URI> suggestions(String query, BrowserSettings s)`;
  - `static Optional<String> queryOf(URI page)`: the search terms when `page` is any engine's results page, so the
    focused field shows them;
  - the custom URLs must contain `%s`. Settings refuses them otherwise, with "The URL must contain %s".
- **`AddressInput.resolve(String typed, BrowserSettings s)`**, which closes 4a's gaps:
  - a scheme in the address-field schemes loads as typed;
  - an absolute local path (`/…`, `~/…`, or `C:\…` on Windows) becomes a `file:` URL;
  - a domain (a trailing dot allowed), an IP address, `localhost`, `host:port`, or a single word without spaces
    (underscores allowed) gets `https://`;
  - anything else becomes `SearchEngine.search`;
  - `chrome:` and empty text give empty.
- **`static List<Nodes.Suggestion> Suggestions.rank(String typed, List<String> engineSuggestions,
  List<Nodes.Suggestion> stores)`:**
  1. the typed address, "Navigate to “https://…”" with detail "URL", when the text has a scheme or contains a dot;
  2. "Search for “…”", detail "Web Search";
  3. up to 5 engine suggestions, detail "Search Suggestion";
  4. single-word hosts;
  5. then `stores`, which is empty until plan 5 adds bookmarks and history.

  When the typed text is itself a results URL, its query ranks first. Ids are stable per row.
- **`SuggestionFetcher.fetch(URI suggestUrl) -> CompletableFuture<List<String>>`:**
  - OpenSearch JSON `[query, [s1, s2, …]]`;
  - a 150 ms debounce in `BrowserItem`, and a 3 s timeout;
  - each keystroke supersedes the previous request, so a stale answer is dropped;
  - offline gives empty.
- **The field:**
  - `selectOnClick = true` (4a's R15 debt);
  - Tab or Enter on a suggestion loads it, then focuses the page;
  - Enter with no suggestion shown loads `AddressInput.resolve`;
  - on focus, a results page shows its query.
- **"Search with ⟨engine⟩"** (Task 7) uses the same engine.

- [ ] **Step 1: Write the failing tests:**
  - **`SearchEngineTest`:** every engine's search and suggestion URLs, and `queryOf` for each results URL.
  - **`AddressInputTest`:** the new rows, including `my_host`, `example.com.`, `/Users/me/a.html`, `C:\a.html` (Windows
    only) and `what is jcef`.
  - **`SuggestionsTest`:** the ranking.
  - **`SuggestionFetcherTest`:** with an `httpserver` fixture: parsing, the timeout, a stale answer dropped, and a 500
    response giving empty.

- [ ] **Step 2: Run** the tests. Expected: FAIL.

- [ ] **Step 3: Implement.**

- [ ] **Step 4: Run** the tests. Expected: PASS.

- [ ] **Step 5: Commit** "Browser: search engines and address suggestions".

### Task 5: Find in page

**Files:**
- Create: `plugins/browser/src/main/java/dev/jasper/browser/item/BrowserFindBar.java`, modelled on
  `plugins/editor/.../ui/FindBar.java` without Match word.
- Create: `plugins/browser/src/main/java/dev/jasper/browser/page/PageFind.java`.
- Create: `plugins/browser/src/main/resources/dev/jasper/browser/page/find.js`.
- Modify: `BrowserItem.java` (the bar appended to `spec()`'s split, as `EditorTab` does; content commands `find`,
  `find-next` and `find-previous`) and `WebMenu.java`'s keys table, if needed.
- Test: `BrowserFindBarTest.java` and `PageFindTest.java`.

**Interfaces:**
- **`BrowserFindBar(Supplier<Optional<PageFind>> target, Runnable changed, Consumer<String> focus)`**, with `node()`,
  `event(UiEvent)`, `open()`, `next()`, `previous()` and `close()`.
  - **It holds** a SEARCH field (Enter is next, Shift+Enter previous, Escape closes), the toggles Match case
    (`match.case`) and Regular expression (`regex`), the status ("N matches", "No matches" or "Invalid pattern"), and
    the up, down and close buttons.
  - **Keys:** the content commands use Jasper's Find keys on macOS (Cmd+F, Cmd+G, Cmd+Shift+G), and Ctrl+F, F3 and
    Shift+F3 on Linux and Windows, as in Editor tabs.
- **`PageFind.find(String query, boolean matchCase, boolean regex) -> CompletableFuture<Result>`,** plus `next()`,
  `previous()` and `clear()`.
  - **The result:** `record Result(int count, int current, boolean invalid)`.
  - **Where it searches:** it runs `find.js` in every frame (`CefBrowser.getFrameIdentifiers()` and
    `executeJavaScript` per frame), with results through `jasperBridgeQuery` op `find`.
- **`find.js`:**
  - **Matching:** it searches visible text nodes; the query is at most 1,000 characters.
  - **Highlights:** every match gets `#FFFF00`, the current one `#FF9632`.
  - **Revealing matches:** it opens `details`, `aria-expanded="false"` and `hidden="until-found"` that hide a match.
  - **Cleaning up:** `clear()` removes every highlight.
  - **A changing page:** a DOM change while open re-runs the search.

- [ ] **Step 1: Write the failing tests:**
  - **`BrowserFindBarTest`** (FakeUi): open seeds from a one-line selection; the status texts; Escape closes; the toggles
    re-run the search.
  - **`PageFindTest`** (a recording page seam): the query and options reach each frame's script; counts sum across
    frames; an invalid pattern reports `invalid`; a 1,001-character query is cut to 1,000.

- [ ] **Step 2: Run** the tests. Expected: FAIL.

- [ ] **Step 3: Implement.**

- [ ] **Step 4: Run** the tests. Expected: PASS.

- [ ] **Step 5: Commit** "Browser: find in page, with regular expressions".

### Task 6: Zoom

**Files:**
- Create: `plugins/browser/src/main/java/dev/jasper/browser/item/Zoom.java`.
- Modify: `BrowserItem.java` (the content commands `font-bigger`, `font-smaller` and `font-reset`, Ctrl+wheel and
  pinch, and restore) and `restore/BrowserRestore.java` (key `zoom`).
- Modify: `settings/BrowserSettings.java` and `settings.toml`: `page_zoom = 100`, an integer from 5 to 500 with unit
  `%`, in "General".
- Test: `ZoomTest.java` and `BrowserRestoreTest.java`.

**Interfaces:**
- **`static int Zoom.step(int percent, int direction)`** moves ±5, clamped to [5, 500].
- **`static double Zoom.cefLevel(int percent)`** is `Math.log(percent / 100.0) / Math.log(1.2)`. Chromium's zoom level
  is logarithmic, base 1.2.
- **Reset** returns to `page_zoom`.
- **Ctrl+wheel, and pinch where Java delivers it,** step the zoom without saving it.
- **Saving:** a changed zoom saves into the item's `RestoreState` as `zoom` (an integer percent), inside the 4,096
  budget. `url` is dropped first if both can't fit.

- [ ] **Step 1: Write the failing tests:**
  - **Steps:** 100 up gives 105, 7 down gives 5, 498 up gives 500, and the levels for 100 and 120.
  - **Restore:** `zoom` round-trips, and a long URL with a zoom keeps the zoom.

- [ ] **Step 2: Run** the tests. Expected: FAIL.

- [ ] **Step 3: Implement.**

- [ ] **Step 4: Run** the tests. Expected: PASS.

- [ ] **Step 5: Commit** "Browser: zoom in 5% steps, saved with the item".

### Task 7: The page's right-click menu

**Files:**
- Create: `plugins/browser/src/main/java/dev/jasper/browser/page/PageMenu.java`, the `CefContextMenuHandler`.
- Create: `plugins/browser/src/main/java/dev/jasper/browser/page/PageMenuItems.java`.
- Modify: `PageView.java` (right press positions) and `BrowserPlugin`/`Chromium.onClient` wiring.
- Test: `PageMenuItemsTest.java`.

**Interfaces:**
- **`PageMenu.runContextMenu`** cancels Chromium's own menu. It builds Jasper's `MenuItem`s and shows them with
  `ContentTab.showContextMenu(point, items, chosen)`. `chosen` continues the callback with Chromium's command id, or
  runs a Jasper item.
- **`static List<MenuItem> PageMenuItems.of(Context c)`**, with `record Context(boolean canBack, boolean canForward,
  Optional<String> link, Optional<String> image, Optional<String> selection, boolean editable, String engineName)`.
  The items, in order:
  1. Back, Forward and Reload;
  2. with a link: **Open Link in New Tab** (a `NEXT_TAB` item), **Copy Link** and **Save Link As…** (Chromium's
     download);
  3. with an image: **Open Image in New Tab**, **Copy Image** and **Save Image As…**;
  4. in an editable field: **Cut**, **Copy** and **Paste**. Otherwise, with a selection, **Copy** and **Search with
     ⟨engine⟩** (a `NEXT_TAB` item with `SearchEngine.search`). **Select All** always comes last in this group;
  5. **Print…** (`CefBrowser.print()`) and **Copy Page Title**;
  6. **View Source** (Task 9) and **Inspect Element** (Task 10).

  Then the host appends its pane items. Plan 7 adds Save Page As… and Remove Element.
- **On macOS, Ctrl-click** opens the menu as a right-click does.

- [ ] **Step 1: Write the failing tests:** `PageMenuItemsTest`, covering every context combination, the order,
  separators between groups, and the engine's name in "Search with".

- [ ] **Step 2: Run** the tests. Expected: FAIL.

- [ ] **Step 3: Implement.**

- [ ] **Step 4: Run** the tests. Expected: PASS.

- [ ] **Step 5: Commit** "Browser: the page's right-click menu".

### Task 8: Error pages and certificate errors

**Files:**
- Modify: `engine/AboutScheme.java`: `register(String page, Function<URI, String> html)` hands the request's address,
  with its query, to the page. The `Supplier` overload stays for pages that don't need it.
- Create, under `plugins/browser/src/main/java/dev/jasper/browser/item/`: `ErrorPage.java` and
  `CertificateAllowance.java`.
- Modify: `PageEvents.Load` (`onLoadError`), `PageNavigation.Links` (`onCertificateError`) and `BrowserToolbar.node(...)`
  ("Not secure").
- Test: `ErrorPageTest.java` and `CertificateAllowanceTest.java`.

**Interfaces:**
- **`static String ErrorPage.title(CefLoadHandler.ErrorCode code)`** maps to the spec's section 14 titles:
  - "No Internet Connection" for `ERR_INTERNET_DISCONNECTED`;
  - "Server Not Found" for `ERR_NAME_NOT_RESOLVED` and `ERR_NAME_RESOLUTION_FAILED`;
  - "The Connection Timed Out" for `ERR_TIMED_OUT` and `ERR_CONNECTION_TIMED_OUT`;
  - "Can’t Connect to Server" for `ERR_CONNECTION_REFUSED`, `ERR_CONNECTION_FAILED` and `ERR_ADDRESS_UNREACHABLE`;
  - "Network Connection Lost" for `ERR_CONNECTION_RESET`, `ERR_CONNECTION_CLOSED` and `ERR_NETWORK_CHANGED`;
  - "Too Many Redirects" for `ERR_TOO_MANY_REDIRECTS`;
  - "File Not Found" for `ERR_FILE_NOT_FOUND`;
  - "Secure Connection Failed" for `ERR_SSL_*` and `ERR_CERT_*`;
  - "Page Unavailable" for `ERR_BLOCKED_BY_CLIENT`, `ERR_BLOCKED_BY_RESPONSE` and `ERR_ACCESS_DENIED`;
  - "Page Can’t Be Loaded" for everything else.

  `ERR_ABORTED` shows no page: navigations that a download or the user cancelled.
- **`static String ErrorPage.html(URI failed, String title, String message, Optional<Certificate> cert,
  WorkspacePalette p, boolean dark)`:**
  - the title, the message, **Try Again** (Enter and Space press it too), and "Check your internet connection and try
    reloading the page.";
  - certificate errors add "Jasper can’t verify the identity of the website…", the reason and the certificate chain's
    subjects, and a **Details** link that reveals **Continue to this site (unsafe)**;
  - colours as `WelcomePage`, which a shared CSS helper now feeds.
- **`onLoadError`** (main frame) loads `jasper-about:error?url=<failed>&code=<n>` in the item. The address field keeps
  showing the failed URL.
- **`onCertificateError`:**
  - if `CertificateAllowance` allows the host and fingerprint, it continues;
  - otherwise it cancels, and shows the error page with that certificate;
  - Continue adds the allowance and reloads the URL.
- **`CertificateAllowance`:**
  - `allow(String host, String sha256)` and `allows(String host, String sha256)`;
  - it is in memory only, so it ends when Jasper quits; /dev/null items share it.
  - While the current page's host is allowed this way, the toolbar shows a "Not secure" `Text` (warning tone) where the
    field's leading image goes.
- **Trust:** certificates are checked against the system's trust store, which is Chromium's default. `docs/browser.md`
  says that Linux users add corporate authorities to NSS's store (`certutil -d sql:$HOME/.pki/nssdb …`).

- [ ] **Step 1: Write the failing tests:**
  - **`ErrorPageTest`:** each title mapping, `ERR_ABORTED` giving no page, the HTML escaping its URL and message, user
    info dropped, and the certificate section.
  - **`CertificateAllowanceTest`:** the same host with another fingerprint is not allowed, and a fresh instance allows
    nothing.

- [ ] **Step 2: Run** the tests. Expected: FAIL.

- [ ] **Step 3: Implement.**

- [ ] **Step 4: Run** the tests. Expected: PASS.

- [ ] **Step 5: Commit** "Browser: error pages, and Chrome's way past a certificate error".

### Task 9: View Source

**Files:**
- Create: `plugins/browser/src/main/java/dev/jasper/browser/item/SourcePage.java`.
- Modify: `BrowserPlugin.java` (register `jasper-about:source`) and `PageMenu` (the item).
- Test: `SourcePageTest.java`.

**Interfaces:**
- **View Source** loads `jasper-about:source?url=<page>` in the same item.
- **`SourcePage.fetch(URI page) -> CompletableFuture<String>`:**
  - http(s) is fetched again with `HttpClient` (no cookies, a 10 s timeout, at most 5 MB), using the body of a 200
    response only;
  - otherwise it uses the page's current HTML (`CefFrame.getSource`).
- **`static String SourcePage.html(URI page, String source, WorkspacePalette p, boolean dark)`:**
  - the heading "Page Source" and the URL, without user info;
  - the HTML escaped, with tags, attributes, strings and comments in the theme's syntax colours;
  - `http(s)` URLs in attribute values as links.

- [ ] **Step 1: Write the failing tests:** escaping (`<script>` shows as text), the highlighting classes, links,
  user info dropped, and a non-200 response falling back.

- [ ] **Step 2: Run** the tests. Expected: FAIL.

- [ ] **Step 3: Implement.**

- [ ] **Step 4: Run** the tests. Expected: PASS.

- [ ] **Step 5: Commit** "Browser: View Source".

### Task 10: Inspect Element

**Files:**
- Modify: `page/PageBrowser.java`: override `createDevToolsBrowser`.
- Create: `plugins/browser/src/main/java/dev/jasper/browser/item/DevToolsItem.java`.
- Modify: `PageMenu` (Inspect Element, with the click point).
- Test: `DevToolsItemTest.java`.

**Interfaces:**
- **`PageBrowser.createDevToolsBrowser(...)`** builds a new `PageView` and `PageRenderer` for DevTools. It hands them to
  `DevToolsItem`, which opens a `CustomContent` item `SPLIT_DOWN` from the page's pane:
  - its title is "DevTools — ⟨page title⟩";
  - it has no restore, so it is never saved;
  - it closes like any pane;
  - a page that closes closes its DevTools.
- **The fallback:** if the override can't be used, Inspect Element calls `openDevTools(point)`, Chromium's own window,
  which is a top-level window and not a child. Record which one ran in the banner.
- **No menu item and no default shortcut,** as in iTerm2. The Web menu's keys win in the DevTools item too, since it is
  the plugin's content (4a).

- [ ] **Step 1: Write the failing tests:** the title, no restore state, and closing the page closing the DevTools item
  (through the item seam).

- [ ] **Step 2: Run** the tests. Expected: FAIL.

- [ ] **Step 3: Implement.**

- [ ] **Step 4: Run** the tests. Expected: PASS.

- [ ] **Step 5: Commit** "Browser: Inspect Element opens DevTools in a pane below".

### Task 11: Docs and the check

**Files:**
- Modify:
  - `docs/browser.md`: the toolbar's history, favicons, suggestions and search engines, find, zoom, the right-click
    menu, error pages, certificates (with the Linux NSS note), View Source and Inspect Element;
  - `docs/keyboard-shortcuts.md` (Find and zoom in browser items);
  - `docs/configuration.md` (Page zoom and the Search Engine settings);
  - `docs/sdk-icons.md` (`PixelImage`);
  - `docs/plugin-authoring.md` (the new nodes and `showContextMenu`);
  - `docs/STATUS.md`, and this plan's banner.

- [ ] **Step 1: Write the docs,** documenting only what the code does.

- [ ] **Step 2: Run the source hygiene script,** `./gradlew sdkReference`, `./gradlew check` and the external-plugin
  check. Expected: PASS. Record the counts.

- [ ] **Step 3: Commit** "Docs: the browser's page features".

### Task 12: The user's GUI check, with their OK

**Files:**
- Modify: `docs/browser-verification.md`: a 4b section.

- [ ] **Step 1: Check on macOS** (`:jasper-app:run` from the worktree, with the user's OK). Each line is recorded pass
  or fail:
  1. **Favicons:** they show in the tab and the field. Dragging the field's favicon into a terminal pastes the URL.
  2. **History:** holding Back or Forward lists the history, and choosing an entry goes there.
  3. **Suggestions:**
     - typing shows suggestions, with "Loading suggestions…" first;
     - Tab takes one, and Escape restores;
     - on a results page, the field shows the query;
     - the engine setting changes all of this.
  4. **Find:** it highlights, counts, works with a regular expression and Match case, reaches frames, and clears on
     close.
  5. **Zoom:** Cmd+= and Cmd+- step it, Cmd+0 resets it, and it survives a relaunch.
  6. **The right-click menu:** it has the right items for a link, an image, a selection and a field. Search with Google
     opens a new tab, and the host's Split items work.
  7. **An error page:** an unknown host shows "Server Not Found", and Try Again reloads.
  8. **A certificate error:** `https://self-signed.badssl.com` shows the error page. Details, then Continue, loads it,
     with "Not secure" shown; after a relaunch it asks again.
  9. **View Source** shows the source with colours and links.
  10. **Inspect Element** opens DevTools below the page.

- [ ] **Step 2: Commit** "Browser: the page features' GUI check on macOS". Windows and Linux are recorded as not run
  until the user has those hosts.
