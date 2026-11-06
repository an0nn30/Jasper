# Browser 5: history, bookmarks and Jasper integrations — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or
> superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Execution status (2026-11-06): Tasks 1-12 executed and reviewed (Task 6 as 6a and 6b, ruling H5), and Task 13, the
docs; the final review passed the branch "With fixes" and the docs review "Approved with fixes", and the fix wave that
followed is done; Task 14, the user's GUI check on macOS, is pending** (branch `claude/browser-history-bookmarks`,
worktree `.claude/worktrees/browser-4b-4c-e6deab`, cut from local `main` `68265a983`, which has plans 4a, 4b, 4c and 9
merged; the plan `4930f00b6`, then commits `6240e693d` to `986462e5c`, the docs `01dae94a0` and `4659c642e`, and the fix
wave `1e9f2f717` to `032fbdcc3` and its docs; not merged or pushed; see `docs/STATUS.md`). Executed with per-task
subagents and reviews. The pre-flight scan's findings F1-F39 were adopted as rulings (H0, cited H-F*n*) except where
H1-H6 decide otherwise, and the controller's further rulings are H1-H41; plan 4a's R0-R48, its sandbox ruling and the
banners of 4b, 4c and 9 bind. The task reviews recorded these deviations from the plan's text, each with its ruling:
- **Versions:** `JasperSdk.VERSION` 0.28.0 to 0.29.0, every bundled range `>=0.29.0, <0.30` (Task 6a). New SDK members,
  each `@since 0.29.0`: 6b's token field (`Nodes.TokenField`, `TokenField.kept`, `UiEvent.TokensChanged`, and the
  fake's `tokens`, `tokenText`, `removeToken` and `selectedToken`), H21's menu-button anchor (`UiView.anchor` takes a
  `Nodes.MenuButton`), and Task 10's `InsertedText`, `TextTarget`, `ContentTab.setTextInsertion`,
  `PaletteQuery.textTarget` and `Platform.copyOnSelect()`, with the testkit's `setCopyOnSelect` (H39), `addTextTarget`,
  `insertedText` and `insertText`. **The Browser stays 0.2.0** (H6, H-F36): `dev.jasper.browser.api` is unchanged; its
  capabilities are `browser.provide` and `palette.contribute`, and it bundles sqlite-jdbc 3.53.4.0, Remote's
  coordinates, which the "SQLite JDBC" notices component already covers. Snippets is 0.5.0 (H-F15).
- **4b's names, as built** (the Global Constraints asked for them): `Suggestions.rank(String, BrowserSettings,
  List<String>, List<Nodes.Suggestion>)`, whose store rows take 4b's `navigate:` ids (H-F5); the ☰ menu is
  `BrowserToolbar`'s flat `List<MenuItem>`; the page CSS helper `item.PageHtml` moved to `pages.PageHtml` (H-F7);
  `NavigationHistory` calls `getDevToolsClient().executeDevToolsMethod` and reads with `json.StrictJson`, which gained
  `write` (H-F6): there is no `Json.java` and no `BridgeJson` move.
- **Task 1** (`6240e693d` "Browser: the browsing database", the plan's subject "Browser: one SQLite database for history
  and bookmarks"; fix round `ddaae3aff` "Browser: the database review's fixes"; `5b52d16b7` "Remote: a native-folder
  failure does not stop the start"):
  - H-F3's open order (busy timeout, quick check, version, write probe, then WAL), with writability checks before the
    probe, as SQLite opens an unwritable file read-only and its reader leaves a `-wal`; H-F13: a native library that
    does not load fails every call; H-F20: only `SqliteOpener` names `org.sqlite`; `Changes` and `Sql` beyond the
    brief; `PluginZipsTest` (jasper-app, outside the plan's files) expects the sqlite-jdbc jar;
  - H7: the Browser's `SqliteNatives` copy makes its folder owner-only (`rwx------`) besides per-user on Linux; H8:
    user information is cut after the raw authority's last `@` (`BrowserRestore.withoutUserInfo`, so restore too), and
    a delete points its site at the newest entry left; H9: Remote's `SqliteNatives` takes the same rule, as
    `org.sqlite.tmpdir` is one for the JVM; H10: tags fold ASCII letters only, as SQLite's `NOCASE`; H11: Remote's
    start logs a native folder it cannot use and goes on;
  - H-F29's facts: the visit key's host includes the port; `LIKE` and `NOCASE` fold ASCII only; `quick_check` and the
    searches are linear in a file that never shrinks.
- **Task 2** (`ac7bf5e30`, fix round `c6e9deb20` "Browser: the recording review's fixes"):
  - a new `item/HistoryRecorder`, not in the plan's files; `Page.documentLoaded()`; `PreviewHandlers.Loads`, so the
    preview client clears the transition flags too (H-F1); `Suggestions.key`, so a bare domain lists once; AGENTS.md's
    "Verified JCEF behaviour" section with H-F1's rule;
  - H12: `BrowserToolbar.node`'s `Optional<Boolean>` waited for Task 7, and the store rows come through the toolbar's
    constructor; H13: item tests drive stand-in items over `MemoryStores`, never the real plugin's; H-F19 deletes a
    failed load's entry only before its document loaded and only for a failure that shows an error page, never
    `ERR_ABORTED`; H14: each retitle queues behind its record on the store's thread, so the last title lands, at the
    plugin's stop too;
  - the notice "Browser history and bookmarks are unavailable" is a WARNING; the suggestions' history rows are capped at
    5 (the plan's call); the visit index holds the newest 10,000 sites, which bounds the suggestions and the palette
    (H-F29).
- **Task 3** (`524b0e306`), H15: `AboutQueries.prepare(Chromium, AboutScheme, Executor)` installs once per Chromium
  (the root package cannot name `CefClient`) and answers on the background executor; `AboutPages(Appearance,
  AboutScheme)`; each page's 128-bit secret is minted on first need and sent as a 32-character prefix within the
  65,536-character limit; a page that throws answers 404; `answer` and `Answer` are public (H-F27); `jasper.el`
  refuses `form`, `svg` and `template`; the pages' policy is nonce-only (`script-src 'nonce-…'`, `base-uri 'none'`,
  `form-action 'none'`).
- **Task 4** (`c182cbb8b`, fix round `2d8857df1` "Browser: a search is bounded"):
  - H2 (H-F23): Back's and Forward's hold menus end with a separator and Show Full History, but only while they list
    entries, as Chrome shows no hold menu without them, which departs from H2's wording;
  - H16: a search reads the first 256 characters and, of those, the first 8 words (`Sql.searched`), in both stores;
  - H17 (H-F37): every kit text field (`Nodes.Field`), and from 6b the token field, releases Ctrl+H on Windows and
    Linux; secret fields, picker menus' searches, searchable choices and breadcrumbs keep it; H3 (H-F24): a `file:` row
    on Jasper's pages loads on a plain click and a modifier-click opens nothing, with no `open` op;
  - beyond the brief: the page's title "History", the search field autofocusing and `showHistory` focusing the page, a
    failed request's reason in a `role="alert"` line, `about.css`'s `.time` and `.failure`.
- **Task 5** (`1b4c3ab68`), H-F22: a request's tags are a JSON list (depth 2) through the tag rule; `offset` is 1-9
  digits; `sort` is `date`, `title` or `url`.
- **Task 6a** (`0ddd79162` "SDK: 0.29.0", a commit the plan did not have, H5): every manifest range, the external
  plugin, the README table and `VersionRangeTest`'s files.
- **Task 6b** (`8b4bdd4d7`, fix round `9309fb525` "SDK: the token field's review fixes"):
  - the contract case is in `KitContractTest` (the fake, the hosted kit and the classic hosted kit), as
    `PluginContractTest`'s harness cannot type; `Nodes.TokenField.kept(List)` is public, beyond the brief; the label
    shows as a STANDARD field's does;
  - H18: any token can be removed from the keyboard (one Tab stop; Left at the caret's start selects the last chip;
    arrows, Home and End; Backspace and Delete; Escape), with the focus ring, `KitOwnership.SELECTED_TOKEN` and the
    active descendant, and `FakeKitView.selectedToken`; a line break or a tab separates tokens as a comma does; H19:
    tokens the user enters keep their case in every look, the classic tag face included (status chips stay upper
    case); H20: `BookmarkStore.keptTags` calls the SDK's rule; `KitKeyBindingAuditTest` runs with the Ctrl+H release
    off and on, which had failed on Linux and Windows.
- **Task 7** (`3fc7c9106` "SDK: a menu button anchors a popover", a commit the plan did not have; `a8c870fd8`; fix
  round `5d695073a` "Browser: the bookmark panel keeps its tags when it closes"):
  - H21: `KitView.anchorTarget` and the fake take a `MenuButton` (its button), with `AnchorContractTest` cases and a
    new `ClassicHostedAnchorContractTest` (H24); `HostedPopover.fit` keeps every popover inside its anchor's window
    across, right-aligned to the control when there is no room to its right, which moves other plugins' popovers too,
    such as the Vault's credential picker near a right edge;
  - H22: the ☰ order (H-F35), Add Bookmark dimmed for an address that cannot be stored; the notification group
    `dev.jasper.browser.bookmarks` ("Bookmarks", cards), declared at start; Add Bookmark stores the shown address and
    the page's title, none on an error page; the Delete question a modal OVERLAY dialog owned by the item's tab
    (Cancel SECONDARY, Delete PRIMARY and the default); "Bookmark Removed" and "Bookmark Deleted" are INFO titles with
    no content (R46);
  - H23, H25: every close but Escape keeps the panel's tags, Delete Bookmark's included, so its Cancel keeps them, and
    a close with the tags unchanged writes nothing.
- **Task 8** (`12cfe8b34`): `Words` and `PaletteHarness` beyond the files; the opener runs a row's address through
  `AddressInput.address`; a row run from DevTools loads in the inspected page; five stand-in `PluginInfo`s gain
  `palette.contribute`; the title-prefix rule compares the whole text, its words joined by single spaces.
- **Task 9** (`3ad109aad`, fix round `ee6649156` "Browser: the onboarding pages' copy is true on every path"), H26:
  `Onboarding(Path, Executor)` reads its marker once on the background executor when the first page starts (H-F30), and
  the welcome page counts the onboarding unfinished until then; two setup scripts (`onboarding-features.js`,
  `onboarding-setup.js`), as `AboutPages` loads `<page>.js`; the features listed through a query; the setup note hidden
  while no switch shows (plan 5 passes none); `WelcomePage` moved to `pages` with its tests.
  - **Copy beyond the spec (H-F31, H27):** the "Links from the terminal" row reads "Cmd-click a link in a terminal to
    open it in a browser tab, or Cmd+Shift-click to open it in a split. Open Links From Terminal, in Settings › Tools ›
    Browser, chooses whether Jasper asks first or sends links to your default browser." (Ctrl on Windows and Linux),
    true on every routing path, where the plan's "Cmd-click a link to open it here" skipped spec 9's prompt; the setup
    note names Settings › Tools › Browser; Tasks 11 and 12 add the rows "Snippets in page fields" ("Insert a snippet
    into the page field you are typing in.") and "Copy on select" ("With Copy on select on, selecting text in a page
    copies it.").
- **Task 10** (`6c31d861e`): H28: `PaletteQuery` has no old-arity constructor (no compatibility shims), so external
  plugins recompile; a query refuses both a target pane and a text target; a stopped plugin's `TextTarget` throws
  `IllegalStateException`, as its other UI-thread calls do; the card's text target is the content item it opened over,
  and the Run window reads its current item again before each search and run.
- **Task 11** (`d3463fcf2` "Browser: snippets go into the page field you are typing in", the plan's subject "Snippets:
  Insert puts a snippet into a browser page's field"; fix round `6b032118a` "Browser: snippets go in without the
  clipboard"):
  - H1 built the spec's paste and restore, with a 500 ms restore; H29: every stand-in Browser `PluginInfo` outside the
    plugin (`EditorPluginTest`'s) declares the Browser's capabilities;
  - **H30 supersedes H1:** the clipboard path, `ClipboardPaste` and `clipboard.read` are gone, as the paste broke the
    Vault's 30-second password clear and let a page read the previous clipboard. The isolated world's script comes
    first; on its answered false, DevTools `Input.insertText`; anything else (a timeout, an error, no browser yet) does
    nothing. Spec 15's "pastes it, then puts the clipboard back" is not followed. On 2026-11-06 the user chose "Type it
    in" (`Input.insertText`), so H30 is the user's answer, no longer an interim choice, and this departure from spec
    15's wording stands;
  - `PageText` (an instance over a DevTools seam), `insert-text.js`, `Page.insertIntoField`, `Page.insertText` and
    `Page.pressReturn`; every DevTools call times out after 5 s; the world's `uniqueContextId` (CEF 150.0.14.0) comes
    from `Runtime.executionContextCreated`; Return and `Input.insertText` are trusted input, which gives the page user
    activation, while `Runtime.evaluate` runs with `userGesture: false`.
- **Task 12** (`903bc7001` "Browser: copy on select in pages", the plan's subject "Browser: Copy on select copies page
  selections"; fix round `986462e5c` "Browser: copy on select reads once per change"):
  - H-F2: a selection in a frame of another origin is not copied on select, and nothing calls Chromium's copy; H-F12:
    only gestures copy, read in Jasper's isolated world; H31: Shift counts only with a key that moves the caret;
    `PageView`'s hooks constructor takes a sixth parameter, as `PluginPresentationCheck` refuses `this(...)`; one
    "Jasper" world for both scripts, its runs one at a time; email and number fields, and selections over 1,048,576
    characters, copy nothing;
  - H32: a Shift+click's release and the page menu's Select All are gestures; H33: the world's `uniqueContextId` is
    kept per document, dropped at the next main-frame load start or when an evaluation fails, so `Runtime.enable` and
    its console replay run once per document, and `Runtime.releaseObjectGroup` ("console") follows each
    `Runtime.disable`; H34: one read at a time per item, a gesture during it reads once more after it, and only the
    latest read copies; H35: `selection.js` answers only a selection changed since its last read (a `selectionchange`
    flag in Jasper's world);
  - beyond the brief: CSS-masked fields are never copied, non-breaking spaces copy as spaces, `<object>` frames are
    followed, and the evaluation's answer was read on `ForkJoinPool.commonPool()` (the fix wave's W7 moved it);
  - H-F31: spec 15's "or the selection changes" is dropped: a selection a page makes is no gesture.
- **Task 13**: the docs. Beyond the brief's files: `docs/plugins.md` (the Browser's capabilities `browser.provide` and
  `palette.contribute`, and its paragraph, H-F26), `tools/sdk-reference/guide/01-overview.html` (the same capabilities)
  with the SDK reference regenerated, `docs/remote.md` and the Browser guide's Files (the per-user, owner-only SQLite
  folder, H9 and H11), the docs index, the README's Browser row and the root `package-info` (sqlite-jdbc in `store`);
  `plugins/browser/build.gradle.kts`'s comment was already fixed in Task 1. The SDK guides the brief lists
  (`docs/plugin-ui.md`, `docs/plugin-authoring.md`) came with Tasks 6b, 7 and 10, and needed no change. The brief's
  "with the text-only clipboard" is obsolete (H30): the guide says the clipboard is never used. `./gradlew check
  --continue` (JBR 25.0.4.1 b635.75 jcef, 12m 19s, after the docs): **15,063 tests, 0 failures, 0 errors, 103 skipped**
  (1,352 classes in the 16 build folders, without the example plugin's: jasper-app 5,280 with 90 skipped, Editor 5,387
  with 4 skipped, Browser 886 with 1 skipped, jasper-ui 1,020, Remote 659, jasper-sdk 135, jasper-sdk-testkit 304; the
  modules the docs did not change were up to date from the check at `986462e5c`), that is, the 15,017 of the check at
  `d3463fcf2` and the 46 Browser tests of Task 11's fix round and Task 12; the example external plugin's `check
  pluginZip` against `build/sdk-release` passes (1 test, up to date: the docs changed no SDK source); `sdkReference` was
  rerun; source hygiene is clean over 3,360 Java files, and no file the docs touched holds a raw U+00A0, U+2028 or
  U+2029. No GUI was launched.
- **The final fix wave** (after the final review of `68265a983..4659c642e` and the docs review; rulings H36-H39):
  - `1e9f2f717` "Browser: the data folder is its owner's alone" (W1, H37): a new `store.DataFolder` takes the group's
    and others' permissions from the data folder, keeping the owner's (`rwx------` for the folder the host made, and a
    folder its owner made read-only stays so), and from `browser.sqlite` and its `-wal` and `-shm` where they exist, the
    first time either the database's thread opens the file or Chromium's worker enters the start, before the marker
    and Chromium's profile; one failure is logged as a warning and stops nothing; Windows is left alone. `Chromium`
    takes the restriction as a `Runnable` (`engine` names no `store` type), and `BrowserDatabase.file` takes the
    `DataFolder`;
  - `691b62cee` "Browser: Paste and Run presses Return only where text went" (W2, H38): `insert-text.js` answers
    `inserted`, `unreachable` or `not-editable` (`Page.Insertion`): `inserted` gets Return when asked, and only
    `unreachable` falls back to typing with `Input.insertText`, then Return when asked; `not-editable` gets neither,
    and a main-frame load start after the insertion began skips both. A closed
    shadow root is told by the focused element: one a shadow root can be attached to, with no open root, no tabindex,
    not editable and not scrolling, so it cannot hold the focus itself. Number joins the text-entry types; a number
    field or an editable element whose document refuses `execCommand` takes nothing (text that is no number would
    empty a number field's value);
  - `37912487e` "Browser: a dropped selection read keeps its text" (W3, H36): `selection.js` answers `unchanged`
    (`Page.Selection.Unchanged`) for no change since the last read, where it answered `none`; a password or masked
    field, and a field with no selection range, stay `none`. The item keeps a dropped read's non-empty text and copies
    it when the next read answers `unchanged`, or copies the text that read finds; a read that ran but failed keeps
    nothing, so the next `unchanged` read copies nothing;
  - `5bc787e98` "SDK: the testkit's setCopyOnSelect, and its insertText refuses a foreign tab" (W8, W9, W12, H39):
    `FakePluginHost.setCopyOnSelect(boolean)`, the testkit README, the plugin authoring guide and the SDK reference's
    testing page, the reference regenerated; `insertText` throws `IllegalArgumentException` for a tab no fake host made;
    `TextFields.ctrlHReleased` is `volatile`. The contract harness keeps `copyOnSelect(boolean)`, beside its
    `defaultBrowser(boolean)`;
  - `032fbdcc3` "Browser: the final review's minor fixes" (W4-W7, W10): `AboutQueries` refuses a sub-frame's query and
    one from a frame showing no `jasper-about:` address on Chromium's thread; each item closes its open Edit Bookmark
    panel as the items close at the plugin's stop, before the stores close and before the host closes popovers, so the
    tags are kept (`BrowserItem.flush`, was `flushHistory`); `bookmarks.js` asks again when a chosen tag is let go;
    `PageText` reads DevTools' answers on the plugin's background executor, which `Page` now takes and passes to
    `PageText.of` (the answer's length bound, checked before the parse, bounds the parse); raw U+2028, U+00A0 and emoji
    in `PageTextTest`, `SqlTest` and `PageMenuItemsTest` are escapes, and two Javadoc blocks are rewrapped;
  - the docs (W11, W13 and this banner): plan 6's handoff in [What follows](#what-follows); the shortcuts guide scopes
    Ctrl+H to the text and tag fields of Jasper's settings and plugin panels and names the terminal find bar's
    Backspace; the Browser guide's Snippets in page fields, Copy on select (never in a Markdown preview, and H36), the
    Bookmarks scope's 200 newest, the Edit Bookmark panel at quit, the Files section's `jasper-sqlite-<user>` and the
    data folder's permissions; the command palette's "no opener"; the checklist's new lines; STATUS.
  - Verification: `node --check` of `insert-text.js`, `selection.js` and `bookmarks.js`, and throwaway node harnesses
    in the scratchpad, each run first against the script before the change: `insert-text.js` 43 cases (all 43 failed
    before), `selection.js` 57 (7 failed), `bookmarks.js` 9 (2 failed). `./gradlew check --continue` (JBR 25.0.4.1
    b635.75 jcef, 14m 32s, after the code and the docs but for these counts): **15,076 tests, 0 failures, 0 errors,
    103 skipped** (1,352 classes in the 16 build folders, without the example plugin's: jasper-app 5,280 with 90
    skipped, Editor 5,387 with 4 skipped, Browser 899 with 1 skipped, jasper-ui 1,020, Remote 659 with
    `PublicationCrashTest` passing, jasper-sdk 135, jasper-sdk-testkit 304), that is, Task 13's 15,063 and the wave's
    13 new Browser tests; the example external plugin's `check pluginZip` against `build/sdk-release` passes (1 test);
    `sdkReference` was rerun; source hygiene is clean over 3,361 Java files, and a byte scan of every file the wave
    touched finds no raw U+00A0, U+2028 or U+2029 (the 8 raw characters the review named are escapes). No GUI was
    launched.
- **The re-review's round** (the re-review found W1-W13 addressed; rulings H40 and H41):
  - H40 accepted the wave's four calls: W2's closed-shadow-root heuristic, so a closed root whose host has a tabindex or
    scrolls gets no snippet; an editable element or a number field whose document refuses the text takes nothing; W1
    takes the group's and others' permissions instead of setting `rwx------`, so a folder its owner made read-only
    stays so; and W7's hand-off of the background executor through `Page` to `PageText.of` has no headless test;
  - `802d0f327` "Browser: a new browser.sqlite is its owner's alone, and a refused parse ends its run" (H41): a missing
    `browser.sqlite` is made `rw-------` after the writability checks and before SQLite opens it, where POSIX applies,
    so the `-wal` and `-shm` SQLite makes take 0600 too, and an open that cannot use the file it made deletes it; and
    `PageText` hands the parse to the background executor itself, a refusal, as after the plugin stops, failing the run.
    JBR 25's `CompletableFuture` already failed an async stage whose executor refuses it (`uniApply` catches what
    `claim()` throws), so the test of a refusing executor passed before the change too; the change makes explicit what
    no specification promises;
  - Verification: the new `BrowserDatabaseTest` case failed before the change (`expected: "rw-------" but was:
    "rw-r--r--"` for a fresh `browser.sqlite`) and passes; `PageTextTest`'s refusing-executor case passes before and
    after, as said. `./gradlew check --continue` (10m 41s, after the code and the docs but for these counts): **15,078
    tests, 0 failures, 0 errors, 103 skipped** (1,352 classes; Browser 901 with 1 skipped, the wave's 899 and the
    round's 2; jasper-app 5,280, Editor 5,387, Remote 659, up to date from the wave's check); the example plugin's
    `check pluginZip` passes (1 test, up to date: no SDK change); source hygiene is clean over 3,361 Java files, and no
    file the round touched holds a raw U+00A0, U+2028 or U+2029.

Not run:
- **Task 14**, the user's GUI check on macOS, with their OK: pending
  ([checklist](../../browser-verification.md#plan-5-history-bookmarks-and-jasper-integrations)), the GUI-only risks
  first: H-F1's transition flags, CEF's delivery of `Runtime.executionContextCreated` through JBR's out-of-process
  bridge, and whether CEF reports a load start for a failed navigation (H-F19); then the plan's ten lines, line 7 on
  H30's path and line 8 with H32's gestures, H-F2's frame and H35's no-copy, the task reviews' lines and the fix wave's.
- **Windows and Linux** (no hosts): every GUI line, Ctrl+H's History in kit fields included; benchmarks and the memory
  run: deferred. H4: the second sqlite-jdbc's first open, about 0.1 s in tests (3.1 s once, probably macOS scanning
  the freshly extracted library), at the first browser page, waits for the start-up benchmark.
- **No JavaScript runs in a test:** the pages' scripts, `insert-text.js` and `selection.js` are checked as text, with
  `node --check` and with throwaway node harnesses in the scratchpad; no test starts Chromium.

Known gaps and follow-ups:
- **Stale world ids** (Task 12's record, corrected): CEF's `OnLoadStart` fires after the commit, and `Page.loadStarted`
  one `invokeLater` later; a run in between uses the old document's id, which fails once, or asks for the new
  document's id under the old generation, which is not kept (one extra `Runtime.enable`).
- **A page can notice Jasper's DevTools:** Runtime's console replay, once per document; the user's DevTools context
  selector shows the "Jasper" world; Jasper's `Runtime.disable` may clear Blink's page-wide
  `ForceMainWorldInitialization` that the user's open DevTools set (cosmetic).
- **What H38 leaves to the page:** a focus in a frame of another origin or a closed shadow root still gets the
  snippet through `Input.insertText` and Return, so a page that puts the focus on a control in one of them gets Return
  there; a focused element that could host a shadow root and cannot hold the focus itself is taken for a closed root's
  host, and a scrolling one or one with a tabindex for a control that takes no text (H40); and an inserted field's
  Return goes wherever the page has moved the focus by then, as the user's own Return would.
- **What H36 leaves:** a read that ran in the page but answered too late, or failed, consumed the change and keeps no
  text, so the selection it read is copied only once the user changes the selection.
- **Kept from the final review** (its items 12-15): a bookmark's title outlives the bookmark in the palette's recents,
  as the Bookmarks scope is in recents; `VisitedScope` lowercases the 10,000 rows' text on each keystroke and
  `VisitIndex` is bounded by count only, for the benchmarks to measure; `insert-text.js`'s `setRangeText` fallback can
  put text past a page that cancels `beforeinput`; the host's card palette binds the item it opened over as the text
  target, where the fake follows the window's current item at each query.
- **Outside this plan, or for the GUI check** (the final review declined to judge them): Clear All History leaves
  Chromium's back and forward lists, `:visited`, the cache and favicons (plan 6's Clear Website Data); a plugin with
  `terminal.inject` can keep a `TextTarget` past the palette session, as with `PaneHandle`; a page can choose what is
  selected at the user's gesture (H-F12); history grows without bound and deleted rows stay in SQLite's free pages, as
  the spec's "Nothing expires" and Chrome's; web pages can detect Jasper through `window.jasperAboutQuery`; Ctrl+H is
  released in kit fields on Windows and Linux with or without the Browser (H17); H21 moves other plugins' popovers; the
  dropped "or the selection changes" (H-F31); and CEF's event order, `Runtime.executionContextCreated`'s delivery, the
  dialog origin and the 302 redirect (Task 14).
- **What the rulings cost:** a chmod of the data folder at the first open (H37); a third answer kind each for the
  insertion (H38) and the selection (H36); a long search matches on its start only (H16); `Äpfel` and `äpfel` are two
  tags (H10); a modifier-click on a `file:` row opens nothing (H3); re-selecting the identical range with no
  `selectionchange` copies nothing (H35); the page can change its selection between the gesture and the read
  (H-F12); a typed `https://user:pw@host` shows its credentials in the palette's Open URL row, as in the address field;
  history has no full-text index, and Top Sites sorts every site (H-F29).
- **Spec deviations** (H-F31): copy on select drops "or the selection changes"; Add Bookmark is dimmed for an address
  that cannot be stored; history suggestions are capped at 5; the features row's copy (H27); Snippets type through
  DevTools instead of the clipboard (H30, the user's choice on 2026-11-06).
- **Remote** (H-F13's follow-up, done here): Remote's `SqliteNatives` now uses the Browser's per-user, owner-only folder
  (H9), and a folder it cannot use is logged and skipped (H11).

Deferred minors, as the task reviews triaged them, less those the fix wave fixed; the final review kept the rest
recorded:
- 1: `HistoryStore.entries()` finds an entry's siblings with an ASCII-folding `LIKE` while the visit key lowers the
  host with `Locale.ROOT`, so a delete drops the site of a verbatim non-ASCII upper-case host (Chromium reports
  punycode); `RemotePlugin.extractSqliteNatives`'s arguments are evaluated outside its `try`.
- 2: `retitle(CompletableFuture, String)` trusts that its future came from this store's `record`; a retitle
  after a closing or unavailable record is silently empty.
- 3: `about.js`'s refused attributes lack `srcset`, `poster`, `background` and `attributionsrc`;
  `AboutPagesScriptsTest` misses `parseHTMLUnsafe`, `insertHTML`, a bare `Function(` and `<style` or `style=` in
  templates; `StrictJson.quote` in `render`'s script is safe only for Jasper's own values; the first render reads
  templates and mints the secret on Chromium's IO thread, and H15's "no `SecureRandom` at start" does not hold, as
  `PageHtml` makes one; the `AboutSchemeTest` "DevTools" case is a proxy; `AboutPagesScriptsTest`'s `Path.of(toURI())`
  needs a classpath folder; older WARNING traces in `AboutSchemeTest`'s output.
- 4: `WebMenuKeysTest` uses a plain `JTextField`, so Windows and Linux end to end is a GUI line.
- 5: no answer-level test of more than 100 tags or a 65-character tag; a chosen tag the rule drops narrows the filter
  silently, and with all dropped every bookmark shows (crafted requests only, as they need the page's secret).
- 6b: the fake selects a chip only with nothing typed, where the host's Left at the caret's start does with text typed
  (documented).
- 8: `BookmarksScopeTest` discards a call whose context is null (`:541-543`) and has a convoluted assertion (`:580`);
  no test of the Visited scope's order on ties or of the `maxResults` cap; `execute` duplicated in both scopes; the
  title-prefix rule compares the whole text.
- 10: no contract case where an insertion throws; the contract test cannot tell a text target read again for the run
  from one kept.

**Goal:** Give the browser what iTerm2 keeps of the pages a user visits, and the Jasper integrations around it:
- history, with the history page and Web › History;
- bookmarks with tags, the ☰ menu's bookmark items, the Edit Bookmark panel and the bookmarks page;
- both in the address suggestions and the command palette;
- the welcome and onboarding pages;
- Snippets into page fields, and `terminal.copy_on_select` in pages.

**Architecture:**
- **One SQLite database,** `<data>/browser.sqlite`, behind a `store` package with its own thread. It opens on first
  use, never at plugin start. In-memory indexes of the bookmarks and the recent visits serve the UI thread: the ☰ menu,
  the address suggestions and the palette scopes.
- **Internal pages** are bundled HTML, CSS and scripts in a new `pages` package. They reach Java through their own
  message router, `jasperAboutQuery`, which answers only the main frame of the page that registered it.
- **The SDK** gets a token field node, text a content item takes from another plugin, and read access to
  `terminal.copy_on_select`.

**Tech Stack:** Java 25 and Swing on JBR 25.0.4.1 b635.75 with JCEF, sqlite-jdbc 3.53.4.0, Jasper's SDK and testkit,
JUnit 5.

**Spec:** [docs/superpowers/specs/2026-10-26-jasper-browser-plugin-design.md](../specs/2026-10-26-jasper-browser-plugin-design.md),
sections 6 (the ☰ menu's bookmark and history items, and suggestions from them), 7 (Web › History), 10 (History,
Bookmarks), 12 (one database in `data/`), 14 (`welcome` and the onboarding pages), 15 (the palette, Snippets, copy on
select) and 23 item 5. It builds on [plan 4a](2026-10-28-browser-4a-browser-core.md), whose banner's rulings R0-R48 bind
this plan, and on [4b](2026-10-29-browser-4b-page-features.md) and [4c](2026-10-29-browser-4c-default-browser.md).
Research for it (2026-10-30, through git at `main` `5c7e97279`, `claude/browser-core` `fc3d60270` and JBR's `javap`)
is in the constraints.

## Global Constraints

- **Branch:** `claude/browser-history-bookmarks`, from `main` once 4b and 4c have merged, in its own worktree.
- **SDK version:** the next minor after `JasperSdk.VERSION` on `main` when this plan starts (0.29.0 if 4b took 0.27.0
  and 4c 0.28.0). Task 6 bumps it with every bundled manifest range; Task 10 adds to it. New members carry that
  `@since`.
- **4a's names** (at `claude/browser-core`; check them on `main` first):
  - `BrowserPlugin.start` registers the welcome page and its `settings` action (`:63-64`) and the Web menu (`:77`).
    `stop()` (`:95`) closes Chromium at once and waits off the UI thread (R11).
  - `AboutScheme.register(page, Supplier<String>)` (`:39`), `registerAction` (`:55`), `answer` (`:66`) and
    `record Answer(int status, String html)` (`:108`). `AboutSchemeFactory`'s `getResponseHeaders(…, StringRef
    redirectUrl)` (`:52`) never redirects.
  - `WelcomePage(Appearance)` (`:22`) rebuilds its HTML on the UI thread at start and on theme changes.
  - The ☰ `MenuButton` holds only Settings (`BrowserToolbar.java:46-47`). `WebMenu.ITEMS` (`:27`) are
    `ShortcutContext.content()` actions with per-platform keys. `BrowserItems.focused(WindowHandle)` (`:102`) is the
    window's focused-pane browser item.
  - `Page.onLoadStart(Consumer<String>)` (`:227`), `copy()` (`:137`) and `paste()` (`:148`).
    `PageEvents.Load.onLoadStart(browser, frame, TransitionType)` (`:141`) drops the transition.
    `PageView.processMouseEvent` (`:285`) and `processKeyEvent` (`:321`) forward input.
  - `ScriptDialog.ask` (`:83`) refuses a page's third dialog within 1,000 ms of the last closing (`DialogFlood`), and
    `PageDialogs.onJSDialog` (`:56`) has the dialog's `origin`.
  - `links/BridgeJson` reads flat objects of strings and booleans only. `OpenerBridge` refuses sub-frames (`:323`).
  - The test seams are `PageTestAccess`, `ItemTestAccess` and `StartLoadsNoChromiumTest` (a forked JVM's class loads).
  - Open item: Ctrl+H deletes a character in the address field (`docs/STATUS.md:172`).
- **4b's pieces, used by role:**
  - `Suggestions.rank(typed, engineSuggestions, stores)`, with `stores` empty until now;
  - the ☰ menu's structure;
  - the page CSS helper that `WelcomePage` and `ErrorPage` share;
  - `NavigationHistory`'s DevTools calls and JSON reader.

  Record 4b's actual names in this plan's banner.
- **Boundaries:**
  - `org.cef` only in `engine`, `page`, `links` and `dialogs`. `ChromiumBoundaryTest` keeps the new `store`, `pages`,
    `palette` and `json` packages free of it.
  - **Spec section 19:** the plugin's start, an unshown item and a restore open no database and load no `org.sqlite`
    class.
  - UI-thread code does no I/O (the `PaletteScope` rule).
- **SQLite:**
  - Remote bundles `org.xerial:sqlite-jdbc:3.53.4.0` (`plugins/remote/build.gradle.kts:13`); the Browser bundles the
    same coordinates.
  - `notices.toml` lists modules by coordinates (`gradle/third-party-notices.gradle.kts:16-21`), so its "SQLite JDBC"
    component (`:279`) already covers the Browser's copy. `verifyThirdPartyNotices` proves it.
  - Copy Remote's `transfer/store/SqliteNatives.java`, which extracts the native library into
    `<java.io.tmpdir>/jasper-sqlite`, not the shared temp folder (about 2 s there).
  - Connect as `TransferStore.java:29-36` does: `new org.sqlite.JDBC().connect(…)`, then deregister the driver.
- **Palette and Snippets:**
  - `PaletteQuery(window, Optional<PaneHandle> target, maxResults, macOs)` (`:22`) gets `target` from the app's
    `PaletteTarget.paneId` (`HostedPalette.java:72`).
  - `WindowCommandPalette.target` (`:128`) and `currentTarget` (`:137`) know only terminal panes, so a browser item
    current gives no target. `WindowContent.selectedContent()` (`:1565`) is the hosted tab.
  - `SnippetsScope.live` (`:94`) and `paste` (`:121`) use `PaneHandle.paste` and `sendText("\r")` under
    `terminal.inject` (`Capabilities.java:20`).
- **Copy on select:**
  - `terminal.copy_on_select` (`CoreSettings.java:145`) has no SDK reader.
  - The model is `Appearance.terminalTypography`: `HostedTypography`, published through
    `PluginRuntime.terminalTypography` (`:395-407`) from `JasperApplication` (`:454-459`, `:1532`).
  - `Platform.copy` (`:55`) and `clipboardText()` (`:78`, which needs `clipboard.read`).
- **The Edit Bookmark panel:**
  - `Nodes` has no token field (`:19`), and `Chip` (`:686`) has no remove control. Chips are drawn by each look's
    `TagFace`.
  - `UiView.anchor(nodeId)` with `Windows.popover` (`:24`) gives a popover from the ☰ button, and
    `FakePluginHost.popoverViews` (`:1434`) shows it to tests. `Field` can be `readOnly`. SDK nodes reach the kit
    through `KitValues` (`:18`).
- **JCEF** (`javap`):
  - `TransitionType` has `getSource()`, `getQualifiers()` and `isRedirect()`, and `TransitionFlags` has the
    forward-back and both redirect flags.
  - `getDevToolsClient().executeDevToolsMethod(method, params)` returns `CompletableFuture<String>`.
  - `getFocusedFrame()`; `CefFrame.copy()` and `paste()`.
  - A client takes several `CefMessageRouter`s with distinct function names.
- **Tests and the GUI:**
  - Headless. SQLite runs in memory or in `@TempDir`, and every database closes in `@AfterEach`, because Windows
    cannot delete an open file.
  - Agents never run `:jasper-app:run`, benchmarks, AOT training or `./gradlew --stop`. Task 14 is the user's.
- **Copy:** the spec's strings, with Jasper for iTerm2; the plan's own copy is marked. No animations. Cmd on macOS is
  Ctrl on Windows and Linux.
- **No user info** (R46): stored addresses drop `user:password@`.
- **Commits** end with:

  ```
  🤖 Generated with Claude Code at The Home Depot

  Co-Authored-By: Claude <noreply@anthropic.com>
  ```

## Review Focus

1. **Hostile titles and addresses on Jasper's own pages.** A title such as `</script><img src=x onerror=alert(1)>`, or
   a URL with quotes and U+2028, shows as text on the history, bookmarks and welcome pages, which can clear the user's
   data. Task 3 pins this with its script check and CSP, and Tasks 4, 5 and 9 with their answers.
2. **Other pages calling `jasperAboutQuery`.** A web page, a `file:` page, a sub-frame of `jasper-about:history`, and
   the bookmarks page asking for history's operations are refused, and nothing changes. Task 3 pins this.
3. **A database that can't be used:** corrupt, a newer schema, a read-only `data/`, or locked by another Jasper.
   Browsing works, the file is left as it was, one notification says so, and quitting with writes queued never hangs.
   Task 1 pins this.
4. **Copy on select:**
   - a plain click, with an empty selection, keeps the clipboard;
   - a selection a page script makes isn't copied;
   - the setting applies from the next gesture;
   - a password field's selection is never copied.

   Task 12 pins the first three. The last lives in `selection.js`, which no headless test runs, so Task 14 checks it.
5. **Snippets with no focused field:**
   - the clipboard comes back only if it still holds the snippet;
   - Paste and run presses Return once;
   - an item closed mid-insert does nothing.

   Task 11 pins this.

---

### Task 1: The browsing database

**Files:**
- Modify: `plugins/browser/build.gradle.kts`: `implementation("org.xerial:sqlite-jdbc:3.53.4.0")`.
- Create, under `plugins/browser/src/main/java/dev/jasper/browser/store/`:
  - `package-info.java`;
  - `SqliteNatives.java`, copied from Remote;
  - `BrowserDatabase.java`, `Addresses.java` and `Reached.java`;
  - `HistoryStore.java` and `BookmarkStore.java`.
- Modify: the root `package-info.java`, which lists the Chromium-free packages.
- Test: `store/BrowserDatabaseTest.java`, `AddressesTest.java`, `HistoryStoreTest.java` and `BookmarkStoreTest.java`.

**Interfaces:**
- **`final class BrowserDatabase implements AutoCloseable`:** `static file(Path, Consumer<Throwable> unavailable)` and
  `static memory()`.
  - **Opening:** on the first `call`, on its own platform daemon thread `jasper-browser-store`, made only then. It runs
    `SqliteNatives.extractInto(SqliteNatives.folder())`, then:
    - sets `journal_mode=WAL`, `synchronous=NORMAL`, `foreign_keys=ON` and `busy_timeout=2000`;
    - runs `quick_check`;
    - creates the schema below and sets `user_version` 1.
  - **A failed open:** the file is unreadable or unwritable, fails `quick_check`, has `user_version` above 1, or stays
    locked. The file is left untouched, the database runs in memory for the rest of the run, and `unavailable` hears
    the cause once.
  - **Calls:** `<T> CompletableFuture<T> call(Work<T> work)`, with
    `@FunctionalInterface interface Work<T> { T run(Connection c) throws SQLException; }`, run in order.
  - **Closing:** `close()` refuses later calls (`IllegalStateException`) and returns at once. `closed()` completes once
    queued work has run and the connection has closed.
- **The schema:** each table is `IF NOT EXISTS`, so plan 6 adds `permissions` the same way.

  ```sql
  CREATE TABLE IF NOT EXISTS history (id INTEGER PRIMARY KEY, url TEXT NOT NULL, title TEXT NOT NULL DEFAULT '',
    visited_at INTEGER NOT NULL, reached TEXT NOT NULL);
  CREATE INDEX IF NOT EXISTS history_by_time ON history (visited_at DESC, id DESC);
  CREATE TABLE IF NOT EXISTS visits (id INTEGER PRIMARY KEY, host TEXT NOT NULL, path TEXT NOT NULL, url TEXT NOT NULL,
    title TEXT NOT NULL DEFAULT '', count INTEGER NOT NULL, first_visit INTEGER NOT NULL, last_visit INTEGER NOT NULL,
    UNIQUE (host, path));
  CREATE TABLE IF NOT EXISTS bookmarks (id INTEGER PRIMARY KEY, url TEXT NOT NULL UNIQUE, title TEXT NOT NULL DEFAULT '',
    added_at INTEGER NOT NULL);
  CREATE TABLE IF NOT EXISTS bookmark_tags (bookmark INTEGER NOT NULL REFERENCES bookmarks (id) ON DELETE CASCADE,
    tag TEXT NOT NULL COLLATE NOCASE, PRIMARY KEY (bookmark, tag));
  ```
- **`static Optional<String> Addresses.storable(String address)`:** an `http`, `https` or `file` address of at most
  8,192 characters, without its user info. Everything else gives empty *(call: the spec excludes only
  `jasper-about:`; `about:`, `data:`, `blob:` and `javascript:` can't be revisited, and longer addresses bloat the
  database)*.
- **`enum Reached { TYPED, LINK, RELOAD, BACK_FORWARD, REDIRECT, FORM_SUBMIT, OTHER }`,** stored as the lowercase name.
- **`final class HistoryStore`,** `HistoryStore(BrowserDatabase)`. Each method returns a future:
  - **`record(String address, Reached reached, long when) -> Optional<Recorded>`:**
    - nothing for an address `storable` refuses;
    - otherwise an entry, and an upsert of the visit row by host and path: `count + 1`, the new `last_visit` and `url`;
    - `record Recorded(long entry, Visit visit)`.
  - **`retitle(long entry, String title) -> Optional<Visit>`:** the entry's and its row's title, trimmed to at most
    1,024 characters.
  - **`list(String query, Optional<Cursor> before, int limit) -> HistoryPage`:**
    - `record HistoryPage(List<HistoryEntry> entries, Optional<Cursor> next)`;
    - `record HistoryEntry(long id, String url, String title, long visitedAt, Reached reached)`;
    - `record Cursor(long visitedAt, long id)`;
    - newest first, paged by cursor, so entries recorded between pages never shift a page;
    - the query's words are OR-matched against the URL and title, case-insensitively, with `LIKE`, where `%`, `_` and
      `\` match themselves (iTerm2's rule).
  - **`delete(long id)`:** the entry, plus one visit off its row, removing the row at zero *(call: a site whose entries
    are all deleted leaves Top Sites, the suggestions and the palette)*.
  - **`clear()`:** both tables.
  - **`visits(int limit)`** (most recent first) and **`topSites(int limit)`** (most visited, then most recent), each a
    `List<Visit>`, with `record Visit(long id, String url, String title, int count, long firstVisit, long lastVisit)`.
  - **`Subscription onChanged(Runnable)`:** told on the store's thread after `delete` and `clear`.
- **`final class BookmarkStore`,** `BookmarkStore(BrowserDatabase)`:
  - `record Bookmark(long id, String url, String title, long addedAt, List<String> tags)`;
  - **`add(String address, String title, long when) -> Bookmark`:** returns the existing one for that URL. An address
    `storable` refuses throws `IllegalArgumentException`;
  - **`remove(String address)`** and **`delete(long id)`,** each `-> Boolean`; **`clear()`**;
  - **`tags(long id, List<String> tags) -> Bookmark`:** each tag trimmed, nonblank and at most 64 characters, and
    case-insensitive duplicates dropped;
  - **`all()`** (newest first) and **`allTags()`** (sorted case-insensitively);
  - **`list(String query, Sort sort, Set<String> tags, int offset, int limit) -> BookmarkPage`:**
    - `enum Sort { DATE_ADDED, TITLE, URL }`;
    - `record BookmarkPage(List<Bookmark> bookmarks, boolean more)`;
    - the trimmed query is a case-insensitive substring of the title, the URL or a tag;
    - a bookmark must have every chosen tag *(call)*.
  - **`Subscription onChanged(Runnable)`:** after each write.

- [ ] **Step 1: Write the failing tests.**
  - **`BrowserDatabaseTest`:**
    - a new file gets the four tables and `user_version` 1;
    - there is no `jasper-browser-store` thread before the first call;
    - each of these runs in memory, leaves the file's bytes unchanged and tells `unavailable` once: a file of random
      bytes, `user_version` 2, a read-only folder, and a file another connection holds under `BEGIN EXCLUSIVE` past
      the busy timeout;
    - after ten queued writes, `close()` lets all ten land, then `closed()` completes, and a later `call` throws.
  - **`AddressesTest`:**
    - `https://user:pw@example.com/a?b#c` gives `https://example.com/a?b#c`, and `file:///tmp/a.html` is kept;
    - `jasper-about:history`, `about:blank`, `data:text/html,x`, `blob:https://a/1` and `javascript:1` give empty;
    - 8,192 characters pass, and 8,193 don't.
  - **`HistoryStoreTest`:**
    - `https://a.example/p?x=1`, then `?x=2`, make two entries and one row with count 2 and the second URL;
    - `retitle` changes the entry and the row;
    - 120 entries page as 50, 50 and 20, and one recorded after the first page doesn't show in the second;
    - `"alpha beta"` finds either word, and `%` matches only itself;
    - deleting one of two entries leaves count 1, and deleting the other removes the row;
    - `clear`, the `topSites` order, and `onChanged`.
  - **`BookmarkStoreTest`:**
    - one bookmark per URL, and a non-storable address refused;
    - `remove`, `delete` and `clear`;
    - `tags` turns `[" Work ", "work", "", 65 x's]` into `["Work"]`;
    - the three sorts, a `{a, b}` filter needing both, and the query matching a tag;
    - `more`, and `onChanged`.

- [ ] **Step 2: Run** `./gradlew :jasper-plugin-browser:test --tests 'dev.jasper.browser.store.*'`. Expected: FAIL.

- [ ] **Step 3: Implement.**

- [ ] **Step 4: Run** `./gradlew :jasper-plugin-browser:test verifyThirdPartyNotices`. Expected: PASS.

- [ ] **Step 5: Commit** "Browser: one SQLite database for history and bookmarks".

### Task 2: Recording history, the indexes and address suggestions

**Files:**
- Modify:
  - `page/PageEvents.java`: the load start's transition;
  - `page/Page.java`: `onVisit`;
  - `page/PageTestAccess.java`: `loadStarted(Page, String, Reached)`.
- Create: `store/Stores.java`, `store/BookmarkIndex.java`, `store/VisitIndex.java` and `item/StoreSuggestions.java`.
- Modify:
  - `item/BrowserItem.java`: recording, and 4b's `stores`;
  - `item/BrowserItems.java`: holds the `Stores`, and re-renders every item when the bookmarks change;
  - `BrowserPlugin.java`: makes and closes the `Stores`.
- Test: `page/PageEventsReachedTest.java`, `store/BookmarkIndexTest.java`, `store/VisitIndexTest.java`,
  `item/StoreSuggestionsTest.java`, `item/HistoryRecordingTest.java` and `StartLoadsNoChromiumTest.java`.

**Interfaces:**
- **`static Reached PageEvents.reached(int source, int qualifiers)`,** fed `getSource()` and `getQualifiers()`:
  - the forward-back flag gives `BACK_FORWARD`, and a client or server redirect flag gives `REDIRECT`;
  - otherwise the source decides: `TT_RELOAD`, `TT_FORM_SUBMIT`, `TT_LINK`, and `TT_EXPLICIT` (`TYPED`);
  - anything else is `OTHER`.

  CEF reports the address field's loads, and Jasper's own `loadURL` calls, as `TT_EXPLICIT`.
- **`Subscription Page.onVisit(BiConsumer<String, Reached>)`:** each main-frame load start, on the UI thread.
- **`final class Stores implements AutoCloseable`:**
  - `Stores(BrowserDatabase db, Executor ui)`;
  - `history()`, `bookmarks()`, `bookmarkIndex()` and `visitIndex()`;
  - `close()` and `closed()`, as the database's;
  - plan 6 gives /dev/null items `new Stores(BrowserDatabase.memory(), ui)`.
- **`BookmarkIndex`** (UI thread):
  - `load()`: the first call reads `all()` in the background;
  - `has(String address)`, `get(String address)` and `all()`;
  - it reloads after each store change and tells `onChanged(Runnable)`.
- **`VisitIndex`** (UI thread):
  - `load()` reads `visits(10_000)`, and `all()` lists them most recent first;
  - `recorded(Visit)` upserts within the cap;
  - it reloads after `delete` and `clear`, and tells `onChanged`.
- **`static List<Match> StoreSuggestions.of(String typed, List<Bookmark> bookmarks, List<Visit> visits)`:**
  - `record Match(Nodes.Suggestion suggestion, URI address)`;
  - **the prefix rule:** with `t` the trimmed, lowercased text (empty gives nothing), an address matches when `t` begins
    it without its scheme, or without its scheme and a leading `www.`. A title matches when `t` begins it or any of
    its words;
  - **bookmarks:** up to 10, newest first, detail "Bookmark", id `bookmark:<id>`;
  - **history:** up to 5 more, most visited first, detail "Visited N times" ("Visited 1 time"), id `visit:<id>`
    *(call: the spec gives no count)*;
  - **titles:** the stored title, or the address when it is blank.
- **`BrowserItem`:**
  - **Visits:** each `onVisit` goes to `history().record`, then `visitIndex().recorded`, and the item keeps the entry
    id.
  - **Titles:** a title change goes to `retitle`, and the visit it answers to `visitIndex().recorded`. Changes within
    1 s coalesce, and the last always lands.
  - **Suggestions:** it passes `StoreSuggestions.of` to `Suggestions.rank` as `stores`. A chosen `bookmark:` or
    `visit:` row loads its address.
  - **Loading:** the indexes load when an item first shows, or its address field is first edited.
- **`BrowserPlugin`:**
  - its start makes `new Stores(BrowserDatabase.file(<dataDir>/browser.sqlite, …), EventQueue::invokeLater)`, with no
    I/O;
  - `stop()` closes them as Chromium: at once, waiting for `closed()` on `context.background()`;
  - `unavailable` posts one notification, "Browser history and bookmarks are unavailable", with the reason *(plan's
    copy)*.

- [ ] **Step 1: Write the failing tests.**
  - **`PageEventsReachedTest`:**
    - `TT_LINK` with forward-back gives `BACK_FORWARD`, and `TT_EXPLICIT` with a server redirect gives `REDIRECT`;
    - the four sources map as above;
    - `TT_AUTO_SUBFRAME` gives `OTHER`.
  - **`HistoryRecordingTest`** (item seam, in-memory `Stores`):
    - a `LINK` load of `https://a.example/` records one entry, and a title change retitles it;
    - `jasper-about:welcome` records nothing;
    - two items record separately.
  - **`BookmarkIndexTest` and `VisitIndexTest`:**
    - `has` follows `add` and `remove`;
    - 10,001 visits keep the newest 10,000;
    - `clear` empties the index.
  - **`StoreSuggestionsTest`:**
    - `git` matches `https://github.com/x` and the title "GitHub", skipping `www.`;
    - `hub` matches the title word "Hub" but not `github.com`;
    - the caps, a URL that is both bookmarked and visited listed once, and the details;
    - empty text gives nothing.
  - **`StartLoadsNoChromiumTest`:** its three cases also load no `org.sqlite.` class and leave no `browser.sqlite`.

- [ ] **Step 2: Run** `./gradlew :jasper-plugin-browser:test`. Expected: FAIL.

- [ ] **Step 3: Implement.**

- [ ] **Step 4: Run** `./gradlew :jasper-plugin-browser:check`. Expected: PASS.

- [ ] **Step 5: Commit** "Browser: pages are recorded, and bookmarks and history feed the address field".

### Task 3: The internal pages' channel to Java

**Files:**
- Modify:
  - `engine/AboutScheme.java`: queries and redirects;
  - `engine/AboutSchemeFactory.java`: a redirect sets `redirectUrl`;
  - `engine/Chromium.java`: installs `AboutQueries` with the client.
- Create: `engine/AboutQueries.java`.
- Move: `links/BridgeJson.java` to `json/Json.java`, with `object` and `quote` public. Follow any move 4b made.
- Modify: `dialogs/ScriptDialog.java` and `PageDialogs.java`: the dialog's origin.
- Create: `pages/package-info.java` and `pages/AboutPages.java`; resources `dev/jasper/browser/pages/about.css` and
  `about.js`.
- Test: `engine/AboutSchemeTest.java`, `pages/AboutPagesTest.java`, `pages/AboutPagesScriptsTest.java` and
  `dialogs/PageDialogsTest.java`. `BridgeJson`'s tests move with it.

**Interfaces:**
- **`void AboutScheme.registerQueries(String page, Function<String, CompletableFuture<String>> answer)`:**
  - a script on that page calls `jasperAboutQuery({request, onSuccess, onFailure})`;
  - `answer` gets the request on Chromium's thread, and completes with JSON on any thread;
  - the page must already be registered.
- **`Optional<CompletableFuture<String>> AboutScheme.query(String frameAddress, boolean mainFrame, String request)`:**
  it answers only when all of these hold, and is empty otherwise:
  - the main frame asked;
  - `frameAddress` is that page, written either way (`jasper-about:history` or `jasper-about://history/`);
  - the page has queries;
  - the request is at most 65,536 characters.
- **`void AboutScheme.redirect(String page, Supplier<Optional<String>> to)`:** before `page` is answered, `to` is asked
  on Chromium's IO thread. A present address answers 302 to it, with no body.
- **`static void AboutQueries.install(CefClient, AboutScheme)`:**
  - one router, with `jasperAboutQuery` and `jasperAboutCancel`;
  - a persistent query, or one `query` refuses, fails with code 2, "Jasper's pages answer only themselves." *(plan's
    copy)*;
  - a failed answer fails with code 1 and its reason.
- **Requests stay flat,** as `Json.object` reads them: numbers and lists go as strings, a list joined with `\n`.
- **`final class AboutPages`:**
  - `AboutPages(Appearance)` builds the theme's CSS variables on the UI thread, at start and after each theme change,
    with 4b's page CSS helper. Templates are read from resources on first use and cached.
  - `String render(String name, Map<String, String> slots)` fills `pages/<name>.html`:
    - `{{style}}` with the variables and `about.css`;
    - `{{script}}` with `about.js` and `<name>.js`, if there is one;
    - each `{{slot}}` with its value, HTML-escaped.
  - **Every page's head** has `<meta http-equiv="Content-Security-Policy" content="default-src 'none'; style-src
    'unsafe-inline'; script-src 'unsafe-inline'; img-src data:">`, so internal pages load nothing from the network
    (spec section 14).
- **`about.js`:**
  - `jasper.ask(request)` returns a `Promise` of the parsed answer;
  - `jasper.el(tag, attributes, ...children)` sets text only with `textContent`, and `href` only for `http:`, `https:`,
    `file:` and `jasper-about:`;
  - `jasper.debounce(fn, ms)`.
- **`ScriptDialog.ask` takes the dialog's origin.** A `jasper-about:` origin skips `DialogFlood`, so the pages'
  `confirm()`s, as iTerm2's pages use them, keep working while a user deletes rows quickly.

- [ ] **Step 1: Write the failing tests.**
  - **`AboutSchemeTest`:**
    - history's queries answer the main frame of `jasper-about:history` and of `jasper-about://history/?q=1`;
    - they refuse `https://evil.example/`, `file:///tmp/x.html`, `jasper-about:bookmarks`, a sub-frame, a page with no
      queries, and 65,537 characters;
    - a redirect answers 302 with no body, and an empty `to` gives the page.
  - **`AboutPagesTest`:**
    - `<b>&"'` in a slot comes out escaped;
    - the style holds the theme's background, and the CSP meta is there;
    - an unknown name throws;
    - a theme change rebuilds the variables.
  - **`AboutPagesScriptsTest`:** no resource under `dev/jasper/browser/pages/` contains `innerHTML`, `outerHTML`,
    `insertAdjacentHTML`, `document.write`, `eval(` or `new Function`.
  - **`PageDialogsTest`:** three confirms 100 ms apart all show from `jasper-about://history/`, while from
    `https://a.example/` the third is refused.

- [ ] **Step 2: Run** `./gradlew :jasper-plugin-browser:test`. Expected: FAIL.

- [ ] **Step 3: Implement.**

- [ ] **Step 4: Run** `./gradlew :jasper-plugin-browser:check`. Expected: PASS.

- [ ] **Step 5: Commit** "Browser: Jasper's pages ask Java through a channel only they can use".

### Task 4: The history page and Web › History

**Files:**
- Create: `pages/HistoryPage.java`; resources `pages/history.html` and `history.js`.
- Modify:
  - `BrowserPlugin.java`: registers the page and its queries;
  - `item/WebMenu.java`: History;
  - `jasper-ui/src/main/java/dev/jasper/ui/swing/TextFields.java`: Ctrl+H.
- Test: `pages/HistoryPageTest.java`, `item/WebMenuTest.java`, jasper-app's `WebMenuKeysTest.java` and jasper-ui's
  `TextFieldsTest.java`.

**Interfaces:**
- **`HistoryPage`:** `NAME = "history"` and `ADDRESS = jasper-about:history`.
  `CompletableFuture<String> answer(String request)` answers:
  - `{"op":"list","query":q,"before":"<visitedAt>:<id>"}`, with `before` optional:
    `{"entries":[{"id","url","title","time"}],"next":"<visitedAt>:<id>"|null}`, 50 at a time;
  - `{"op":"delete","id":"<n>"}` and `{"op":"clear"}`: `{}`;
  - anything else fails the future with "Jasper does not understand this request.". It never throws.
- **The page:**
  - the heading "Browsing History" and the subtitle "View and manage your browsing history";
  - **Clear All History**, which confirms "This will delete all browsing history. This action cannot be undone.
    Continue?";
  - the search field "Search your browsing history…", with iTerm2's 300 ms debounce;
  - entries grouped under "Today", "Yesterday" or the date (`toLocaleDateString` with weekday, day, month and year);
  - each entry shows its time (hours and minutes), its title or "Untitled", and its URL as a link;
  - × (named "Delete") confirms "Delete this history entry?";
  - **Load More**, while `next` is present, continues the current search;
  - an empty list reads "No browsing history".
- **`WebMenu`:**
  - adds `HISTORY = "dev.jasper.browser.history"`, "History", with `cmd+y` on macOS and `ctrl+h` elsewhere, and the
    keywords "history" and "visited";
  - it goes right after Reload Page;
  - it loads `HistoryPage.ADDRESS` in the window's focused browser item.
- **4a's Ctrl+H item:**
  - on Windows and Linux, kit text fields bind `ctrl H` to `"none"`, so the key reaches the window's keymap and History
    runs from the address field too. Backspace still deletes;
  - the rule is the package-private `static boolean TextFields.releasesCtrlH(String osName)` *(call: Chrome's address
    bar opens History on Ctrl+H; this frees Ctrl+H in every kit text field)*.

- [ ] **Step 1: Write the failing tests.**
  - **`HistoryPageTest`** (in-memory stores):
    - three records list newest first, and 60 list as 50, then 10 with `next` null;
    - `delete` and `clear`;
    - malformed JSON, an unknown op, a missing id and `"id":"x"` each fail the future;
    - the title `</script><img src=x onerror=alert(1)>` with U+2028 reads back exactly.
  - **`WebMenuTest`:**
    - History's defaults;
    - the order Open Location…, separator, Back, Forward, separator, Reload Page, History;
    - History loads `jasper-about:history` in a browser item.
  - **`WebMenuKeysTest`:** still no clash on either keymap.
  - **`TextFieldsTest`:**
    - `releasesCtrlH` is false for "Mac OS X", and true for "Linux" and "Windows 11";
    - a field made on this host follows it.

- [ ] **Step 2: Run** `./gradlew :jasper-plugin-browser:test :jasper-ui:test --tests '*TextFieldsTest' :jasper-app:test
  --tests '*WebMenuKeysTest'`. Expected: FAIL.

- [ ] **Step 3: Implement.**

- [ ] **Step 4: Run** the same. Expected: PASS.

- [ ] **Step 5: Commit** "Browser: the history page, and Web › History".

### Task 5: The bookmarks page

**Files:**
- Create: `pages/BookmarksPage.java`; resources `pages/bookmarks.html` and `bookmarks.js`.
- Modify: `BrowserPlugin.java`: registers the page and its queries.
- Test: `pages/BookmarksPageTest.java`.

**Interfaces:**
- **`BookmarksPage`:** `NAME = "bookmarks"` and `ADDRESS = jasper-about:bookmarks`. `answer` answers:
  - `{"op":"list","query","sort":"date|title|url","tags":"a\nb","offset":"<n>"}`:
    `{"bookmarks":[{"id","url","title","added","tags":[…]}],"more":bool,"tags":[every tag]}`, 50 at a time;
  - `delete` and `clear`, as the history page's;
  - anything else fails, as there.
- **The page:**
  - the heading "Bookmarks" and the subtitle "Manage and organize your saved websites";
  - **Clear All**, which confirms "This will delete all bookmarks. This action cannot be undone. Continue?";
  - the search field "Search bookmarks...";
  - "Sort by:" Date Added, Title or URL;
  - "Filter by tags:", with a toggle chip per tag;
  - each row shows its title (or URL), its URL as a link, its tags, and "Added DATE";
  - × confirms "Delete this bookmark?";
  - **Load More**, and an empty list reads "No bookmarks found".

- [ ] **Step 1: Write the failing tests:**
  - each sort, a two-tag filter, the paging and `more`, and every tag listed;
  - `delete` and `clear`;
  - malformed requests;
  - a hostile title reads back exactly.

- [ ] **Step 2: Run** `./gradlew :jasper-plugin-browser:test --tests '*BookmarksPageTest'`. Expected: FAIL.

- [ ] **Step 3: Implement.**

- [ ] **Step 4: Run** the same. Expected: PASS.

- [ ] **Step 5: Commit** "Browser: the bookmarks page".

### Task 6: The SDK's token field

**Files:**
- Modify: `JasperSdk.VERSION`, every bundled `plugin.toml`'s `sdk` range, `docs/examples/external-plugin`, the SDK
  README's compatibility table and the guide pages, as 4a's Task 2 did.
- Modify, in the SDK: `ui/Nodes.java` (`TokenField`, and the permits list) and `ui/UiEvent.java` (`TokensChanged`).
- Modify, in the kit, app and testkit:
  - jasper-ui's `model/Nodes.java` and `model/UiEvent.java`;
  - a new `swing/TokenFields.java`, registered beside `TextFields`;
  - jasper-app's `plugins/KitValues.java`, both ways;
  - the testkit's `FakeUi` and `FakeKitView`;
  - `docs/design/theme-engine/migration.csv`: the rows `ThemeMigrationCoverageTest` asks for.
- Modify: `docs/plugin-ui.md`, and `docs/sdk-reference.html` through `sdkReference`.
- Test: `UiValuesTest.java`, jasper-ui's `swing/TokenFieldsTest.java` and `PluginContractTest` (both runtimes).

**Interfaces** (the new version):
- **`record Nodes.TokenField(String id, ControlState state, String label, String hint, List<String> tokens)`:**
  - `label` is the accessible name, and `hint` the placeholder;
  - each token is nonblank after trimming, at most 64 characters, and free of control characters;
  - no case-insensitive duplicates, and at most 100 tokens.
- **`record UiEvent.TokensChanged(String id, List<String> tokens)`:** the whole list, after each change.
- **The kit:**
  - each token is a chip drawn by the look's `TagFace`, with a remove button named "Remove ⟨token⟩", and the typed text
    follows;
  - Enter or a comma commits the typed text. Enter with nothing typed passes on to the form's primary button. Losing
    focus commits too;
  - Backspace in empty text removes the last token;
  - text that is blank, too long or a duplicate is dropped;
  - an update replaces the tokens and keeps the typed text.

- [ ] **Step 1: Write the failing tests.**
  - **`UiValuesTest`:** the token rules, and a 101st token refused.
  - **`TokenFieldsTest`:**
    - Enter, a comma and focus loss commit, and Backspace and × remove;
    - a duplicate in another case is dropped;
    - each change reports the whole list once.
  - **The contract case `aTokenFieldReportsItsTokens`:** in both runtimes, the field renders and its events reach the
    plugin.

- [ ] **Step 2: Run** `./gradlew :jasper-sdk:test :jasper-sdk-testkit:test :jasper-ui:test :jasper-app:test --tests
  '*AppContractTest*'`. Expected: FAIL.

- [ ] **Step 3: Implement** the node, the kit's view in every look, the host, the fakes, the bump and the guides.

- [ ] **Step 4: Run** `./gradlew sdkReference`, then `./gradlew check`, then
  `./gradlew -p docs/examples/external-plugin -PjasperSdkDir="$PWD/build/sdk-release" check pluginZip`. Expected:
  PASS. Commit the regenerated `docs/sdk-reference.html`.

- [ ] **Step 5: Commit** "SDK: a token field".

### Task 7: The ☰ menu's bookmark items and the Edit Bookmark panel

**Files:**
- Modify: `item/BrowserToolbar.java` (the group, in 4b's ☰ structure) and `item/BrowserItem.java` (its actions).
- Create: `item/BookmarkPanel.java`.
- Modify: `BrowserPlugin.java`: the notification group.
- Test: `item/BrowserToolbarTest.java` and `item/BookmarkPanelTest.java`.

**Interfaces:**
- **The ☰ group,** in the spec's place. It shows outside /dev/null mode, which plan 6 brings, so here it always shows:
  - **Add Bookmark** (`browser.bookmark.add`) or **Remove Bookmark** (`browser.bookmark.remove`), by
    `BookmarkIndex.has`. Add Bookmark is disabled for an address `storable` refuses;
  - a separator;
  - **Manage Bookmarks** (`browser.bookmarks`), which loads `jasper-about:bookmarks`;
  - **History** (`browser.history`), which loads `jasper-about:history`.
- **The group** is `GroupSpec("dev.jasper.browser.bookmarks", "Bookmarks", DisplayMode.CARD)`.
- **Add Bookmark** calls `bookmarks().add(address, page title)`, then `BookmarkPanel.open`.
- **Remove Bookmark** removes the bookmark and posts "Bookmark Removed" (INFO, title only).
- **`static BookmarkPanel BookmarkPanel.open(PluginContext context, UiAnchor anchor, Stores stores, Bookmark bookmark,
  NotificationGroup group, Page page, ContentTab owner)`:**
  - **Where:** a popover from `view.anchor(BrowserToolbar.MENU)`.
  - **What it holds:** the form "Edit Bookmark", with:
    - read-only **Title** and **URL** fields;
    - **Tags**, a `TokenField` with the hint "Enter tags…";
    - the buttons **Delete Bookmark** (`DESTRUCTIVE_GHOST`) and **Save** (`PRIMARY`).
  - **Save** writes `bookmarks().tags(id, tokens)` and closes the panel.
  - **Delete Bookmark** closes the panel, then shows a modal owned by `owner`, "Are you sure you want to delete this
    bookmark?", with **Cancel** and **Delete** *(plan's copy for the buttons)*. Delete removes the bookmark and posts
    "Bookmark Deleted".
  - **It closes** at the page's next main-frame load start, when its item closes, and on Escape or a click outside.
  - It is the only place tags are edited.

- [ ] **Step 1: Write the failing tests.**
  - **`BrowserToolbarTest`:** the group for an address that is bookmarked, one that isn't, and one that can't be
    stored.
  - **`BookmarkPanelTest`** (`FakePluginHost.popoverViews`):
    - Add Bookmark stores the bookmark and shows the panel with its title and URL;
    - Save writes the tokens;
    - Delete Bookmark asks, then deletes and posts "Bookmark Deleted", and Cancel keeps it;
    - a load start closes the panel;
    - Remove Bookmark posts "Bookmark Removed", and the menu offers Add Bookmark again.

- [ ] **Step 2: Run** `./gradlew :jasper-plugin-browser:test`. Expected: FAIL.

- [ ] **Step 3: Implement.**

- [ ] **Step 4: Run** the same. Expected: PASS.

- [ ] **Step 5: Commit** "Browser: bookmarks from the menu button, with the Edit Bookmark panel".

### Task 8: The palette scopes

**Files:**
- Create: `palette/package-info.java`, `palette/BookmarksScope.java` and `palette/VisitedScope.java`.
- Modify: `BrowserPlugin.java` (both scopes and the opener), `plugin.toml` (`palette.contribute`) and
  `BrowserManifestTest`.
- Test: `palette/BookmarksScopeTest.java` and `palette/VisitedScopeTest.java`.

**Interfaces:**
- **`BookmarksScope(BookmarkIndex index, BiConsumer<URI, WindowHandle> open)`:**
  - the id `dev.jasper.browser.scope.bookmarks`, the label "Bookmarks", the placeholder "Search bookmarks" and the
    description "Open Bookmark in Browser";
  - the verb `open`, "Open"; the icon `bookmark`; the item noun "bookmark"; in All and among recents;
  - **search:** the bookmarks whose title, URL or tags contain every word, title-prefix matches first, then newest. An
    empty query lists them all, newest first, under "Bookmarks";
  - **rows:** the id `bookmark:<id>`, the title (or URL), and the URL as the detail;
  - `activated` calls `index.load()`, and `onChanged` is the index's.
- **`VisitedScope(VisitIndex index, BiConsumer<URI, WindowHandle> open)`:**
  - the id `dev.jasper.browser.scope.history`, the label "Browser History", the placeholder "Search visited sites" and
    the description "Open Visited Site in Browser";
  - the verb `open`, "Open"; the icon `history`; the item noun "site"; in All, but `withInRecents(false)`, as shell
    history;
  - **the URL row:** when the trimmed query is an `https://` URL with a host, the first row is "Open URL in Browser",
    with the id `open-url` and the URL as the detail;
  - **search:** then the visits whose title or URL contain every word, most visited first, then most recent. An empty
    query lists the 50 most recent under "Recent".
- **The opener:**
  - it loads into `items.focused(window)` when there is one, and otherwise opens a `NEW_TAB` item there;
  - once Chromium can't serve pages, it posts the start-failure notification, as `openNew` does.

- [ ] **Step 1: Write the failing tests** (`searchScope` and `executeInScope`):
  - each scope's rows and order, and each spec's flags;
  - the URL row for `https://example.com/a`, and none for `http://example.com`, `https://` or `example.com`;
  - a row run where the window's focused item is a browser loads there and opens no tab; elsewhere it opens a new tab.

- [ ] **Step 2: Run** `./gradlew :jasper-plugin-browser:test --tests 'dev.jasper.browser.palette.*'`. Expected: FAIL.

- [ ] **Step 3: Implement.**

- [ ] **Step 4: Run** `./gradlew :jasper-plugin-browser:check`. Expected: PASS.

- [ ] **Step 5: Commit** "Browser: bookmarks and visited sites in the command palette".

### Task 9: The welcome and onboarding pages

**Files:**
- Move: `WelcomePage.java` to `pages/WelcomePage.java`, now on `AboutPages`.
- Create: `pages/Onboarding.java` and `pages/OnboardingPages.java`; resources `pages/welcome.html`, `welcome.js`,
  `onboarding-intro.html`, `onboarding-features.html`, `onboarding-setup.html` and `onboarding.js`.
- Modify: `BrowserPlugin.java`: the four pages, the redirect and the queries.
- Test: `pages/WelcomePageTest.java`, `pages/OnboardingTest.java` and `pages/OnboardingPagesTest.java`.

**Interfaces:**
- **`Onboarding(Path marker)`,** with the marker `<dataDir>/onboarding-finished`:
  - `finished()` reads the marker once, then answers from memory;
  - `finish()` writes it. A failed write is logged, and the run still counts as finished.
- **The redirect:** `about.redirect("welcome", …)` gives `jasper-about:onboarding-intro` until `finished()`.
- **`welcome`:**
  - the heading "Welcome Back";
  - "Top Sites", with **Refresh**. `{"op":"top-sites"}` gives `{"sites":[{"url","title","host"}]}`, up to 8 *(call)*,
    each a link tile of the title (or host) over the host;
  - "Quick Actions": Bookmarks, History and Settings (4a's `jasper-about:settings`). Plan 6 adds Permissions.
- **`onboarding-intro`:**
  - "Meet the Jasper Browser";
  - "While not intended as a primary browser, Jasper's web capabilities provide a useful tool for integrated terminal
    and web workflows." (iTerm2's line, from spec section 1);
  - **Get Started**, to the features page.
- **`onboarding-features`:** "What Can It Do?", a row per `OnboardingPages.FEATURES` entry, then **Back** and
  **Continue**. An entry is `record Feature(String title, String macOs, String other)`, its text in each platform's
  keys. This task's rows, in the plan's copy:
  1. "Pages beside your terminals": "Open a page in a tab, a split or a window of its own, and move it like any
     pane."
  2. "Links from the terminal": "Cmd-click a link to open it here, or Cmd+Shift-click to open it in a split." (Ctrl
     elsewhere)
  3. "An address field that searches": "Type an address or a search, with suggestions from your search engine,
     bookmarks and history."
  4. "Find in page": "Search the page with Match case and regular expressions."
  5. "Bookmarks and history": "Bookmark and tag pages, and open them or any site you visited from the command
     palette."

  Tasks 11 and 12, and plans 6 to 8, add their own rows, so the page lists only what is built.
- **`onboarding-setup`:**
  - "Let’s Get You Set Up";
  - "You can change these at any time in Settings › Browser." *(plan's copy)*;
  - a switch per `record SetupSwitch(String id, String title, String description, BooleanSupplier on, Consumer<Boolean>
    set)`, which sends `{"op":"switch","id","on":"true|false"}`. Plan 6 passes the Ad Blocker's; this plan passes none;
  - **Back** and **Start Browsing**, which sends `{"op":"finish"}`, then runs
    `location.replace("jasper-about:welcome")`.

- [ ] **Step 1: Write the failing tests.**
  - **`OnboardingTest`:**
    - a fresh folder isn't finished;
    - `finish` writes the marker, and a new instance reads it;
    - an unwritable folder still finishes the instance.
  - **`WelcomePageTest`:**
    - the welcome page redirects until onboarding is finished, then serves "Welcome Back";
    - `top-sites` gives at most 8, in `topSites` order;
    - the three Quick Actions;
    - a hostile title reads back exactly.
  - **`OnboardingPagesTest`:**
    - every feature row, with Cmd on macOS and Ctrl elsewhere;
    - a fixture switch: `switch` calls `set`, and the page shows its state;
    - `finish` works from `onboarding-setup` and is refused from `onboarding-intro`.

- [ ] **Step 2: Run** `./gradlew :jasper-plugin-browser:test --tests 'dev.jasper.browser.pages.*'`. Expected: FAIL.

- [ ] **Step 3: Implement.**

- [ ] **Step 4: Run** `./gradlew :jasper-plugin-browser:check`. Expected: PASS.

- [ ] **Step 5: Commit** "Browser: the welcome page and the onboarding pages".

### Task 10: The SDK's inserted text and copy on select

**Files:**
- Create: `jasper-sdk/.../ui/InsertedText.java` and `ui/TextTarget.java`.
- Modify, in the SDK: `ui/ContentTab.java`, `palette/PaletteQuery.java`, `ui/Platform.java`, and `Capabilities.java`
  (`terminal.inject`'s Javadoc).
- Modify, in the app:
  - `contributions/Tab.java`: the insertion, in plain types;
  - `plugins/HostedUi.java` (`OwnedContent`);
  - `palette/PaletteTarget.java`: `Optional<BiConsumer<String, Boolean>> insertText`;
  - `workspace/WindowCommandPalette.java`: `target` and `currentTarget` fill it from `selectedContent()` when no terminal
    pane is current;
  - `plugins/HostedPalette.java`: `query` makes the `TextTarget`;
  - `plugins/HostedContext.java` and `PluginRuntime.java`: `copyOnSelect(boolean)`, published as `terminalTypography`
    is;
  - `application/JasperApplication.java`: publishes it at start and on each snapshot.
- Modify, in the testkit: `FakeContent`, `FakePluginHost` and `FakePluginContext`, the contract cases, and every
  `new PaletteQuery(`: `FakePluginHost`, `PaletteValuesTest`, and the File Server, Remote, Tunnels and Vault scope
  tests.
- Modify: `docs/plugin-authoring.md`, the testkit README and `docs/sdk-reference.html`.
- Test: `PluginContractTest` (both runtimes), `PaletteValuesTest` and `UiValuesTest`.

**Interfaces** (Task 6's version):
- **`record InsertedText(String text, boolean submit)`:** `submit` presses Return afterwards, as Paste and run does.
- **`void ContentTab.setTextInsertion(Optional<Consumer<InsertedText>> insertion)`:** while present, the tab takes text
  that other plugins insert, such as a snippet. UI thread; ignored once closed.
- **`interface TextTarget`,** implemented by the app and the testkit:
  - `void insert(InsertedText text)`: needs `terminal.inject`, on the UI thread, and does nothing once closed;
  - `boolean isOpen()`.
- **`PaletteQuery` gains `Optional<TextTarget> textTarget`,** last:
  - the window's current item, when it is a content tab with an insertion and no terminal pane is the origin;
  - read again before each search and run.
- **`boolean Platform.copyOnSelect()`:** whether `terminal.copy_on_select` is on. Safe from any thread *(call: a reader
  with no listener, as the Browser reads it at each gesture; on `Platform`, beside the clipboard)*.
- **The testkit:**
  - `UUID FakePluginHost.addTextTarget(UUID windowId)`: a host content tab that takes text and becomes the window's
    current item. A query for that window with no pane carries its target;
  - `List<InsertedText> insertedText(UUID tabId)`;
  - `boolean insertText(ContentTab tab, InsertedText text)`: runs a plugin tab's insertion, false with none;
  - `void copyOnSelect(boolean)`.

- [ ] **Step 1: Write the failing contract cases:**
  - **`aContentTabThatTakesTextIsThePalettesTextTarget`:**
    - a scope's query for an active content tab's window has a target;
    - `insert(new InsertedText("ls", true))` reaches the handler once;
    - clearing the insertion empties the target;
  - `insertingTextNeedsTerminalInject`;
  - `aTerminalPaneOriginGivesNoTextTarget`;
  - `copyOnSelectFollowsTheTerminalSetting`: false by default, and true once published.

- [ ] **Step 2: Run** `./gradlew :jasper-sdk:test :jasper-sdk-testkit:test :jasper-app:test --tests '*AppContractTest*'`.
  Expected: FAIL.

- [ ] **Step 3: Implement** the members, the host, the fakes and the guides.

- [ ] **Step 4: Run** `./gradlew sdkReference`, then `./gradlew check`, then the external-plugin check. Expected: PASS.
  Commit the regenerated `docs/sdk-reference.html`.

- [ ] **Step 5: Commit** "SDK: text a content item takes, and copy on select".

### Task 11: Snippets in page fields

**Files:**
- Create: `page/PageText.java`, resource `page/insert-text.js`, and `item/ClipboardPaste.java`.
- Modify:
  - `page/Page.java`: `insertIntoField` and `pressReturn`;
  - `item/BrowserItem.java`: `setTextInsertion`;
  - `plugin.toml`: `clipboard.read`;
  - `pages/OnboardingPages.java`: a row.
- Modify: `plugins/snippets/.../SnippetsScope.java` (`live`, `paste`), and the Snippets manifest's `version` to 0.5.0,
  with any test that lists it.
- Test: `item/ClipboardPasteTest.java`, `item/BrowserItemInsertTest.java`, `SnippetsScopeTest.java` and
  `BrowserManifestTest`.

**Interfaces:**
- **`CompletableFuture<Boolean> Page.insertIntoField(String text)`:**
  - **How:** `insert-text.js` runs in the main frame through DevTools' `Runtime.evaluate` (`returnByValue`,
    `userGesture`), with the text as a `Json.quote` literal. The answer is read with 4b's DevTools JSON reader.
  - **True** when the focused element takes the text. The element is followed through same-origin frames and open
    shadow roots, and is an `INPUT` that takes text, a `TEXTAREA` or an editable element. The script tries
    `execCommand('insertText')`, then sets the value at the caret and fires `input`, as iTerm2's does.
  - **False** with nothing focused, with focus in a cross-origin frame, or before the page has its browser.
- **`void Page.pressReturn()`:** Return's `keyDown` and `keyUp` through DevTools' `Input.dispatchKeyEvent`.
- **`ClipboardPaste(Platform platform, BiConsumer<Long, Runnable> after)`,** with
  `paste(String text, Runnable intoPage, Runnable then)`:
  - it saves `clipboardText()`, copies `text`, and runs `intoPage`;
  - 100 ms later (iTerm2's delay), it puts the saved text back if the clipboard still holds `text`, then runs `then`;
  - only text comes back: with no text before, the snippet stays, as `docs/browser.md` will say.
- **`BrowserItem`'s insertion:**
  - `insertIntoField` first, and on true with `submit`, `pressReturn`;
  - on false, `ClipboardPaste.paste(text, page::paste, submit ? page::pressReturn : nothing)`;
  - a closed item does nothing.
- **`SnippetsScope`:**
  - `live` is an open pane, or an open `context.textTarget()`;
  - `paste` keeps the pane path. With no pane, it calls `insert(new InsertedText(text, verb == PASTE_RUN))` on the
    text target, if there is one.
- **The feature row:** "Snippets in page fields": "Insert a snippet into the page field you are typing in."

- [ ] **Step 1: Write the failing tests.**
  - **`ClipboardPasteTest`** (a fake clock):
    - the old text comes back after 100 ms, but not when the clipboard changed meanwhile;
    - a clipboard with no text keeps the snippet;
    - `then` runs once.
  - **`BrowserItemInsertTest`** (a page seam answering true or false):
    - true with `submit` presses Return once;
    - false pastes, restores, then presses Return;
    - a closed item does nothing.
  - **`SnippetsScopeTest`** (`addTextTarget`, no pane):
    - Insert inserts with `submit` false, and Paste and run with true, after placeholders fill;
    - the verbs are available with an open target, and not with a closed one.
  - **`BrowserManifestTest`:** `clipboard.read` and `palette.contribute`.

- [ ] **Step 2: Run** `./gradlew :jasper-plugin-browser:test :jasper-plugin-snippets:test`. Expected: FAIL.

- [ ] **Step 3: Implement.**

- [ ] **Step 4: Run** `./gradlew :jasper-plugin-browser:check :jasper-plugin-snippets:check`. Expected: PASS.

- [ ] **Step 5: Commit** "Snippets: Insert puts a snippet into a browser page's field".

### Task 12: Copy on select in pages

**Files:**
- Modify: `page/PageView.java` (selection gestures) and `page/Page.java` (`onSelectionGesture`, `selectedText`).
- Create: resource `page/selection.js`.
- Modify: `item/BrowserItem.java` (the copy) and `pages/OnboardingPages.java` (a row).
- Test: `page/PageViewSelectionTest.java` and `item/SelectionCopyTest.java`.

**Interfaces:**
- **`PageView`'s gestures:**
  - a forwarded left-button release, a forwarded key release while Shift is held, or the release of the platform's
    Select All (Cmd+A, Ctrl+A);
  - `Page`'s selection listeners hear once, 100 ms after the last gesture, through one restartable timer;
  - a page script's own selection changes aren't gestures, so they copy nothing *(call: iTerm2 listens to
    `selectionchange`, but a reader driven by the user's gestures can't be used by a page to fill the clipboard)*.
- **`Subscription Page.onSelectionGesture(Runnable)`.**
- **`CompletableFuture<Selection> Page.selectedText()`:**
  - `sealed interface Selection permits Text, InFrame, None`, with `record Text(String text)`;
  - `selection.js`, through `Runtime.evaluate`, reads the selected text of the deepest same-origin focused document.
    That includes the selected part of a focused text `INPUT` or `TEXTAREA`, but never of an `input type=password`;
  - focus in a cross-origin frame gives `InFrame`.
- **`BrowserItem`,** on a gesture while `context.platform().copyOnSelect()` is on:
  - a nonempty `Text` goes to `platform().copy`;
  - `InFrame` runs `page.copy()`, Chromium's copy in the focused frame;
  - an empty `Text`, or `None`, does nothing.
- **The feature row:** "Copy on select": "With Copy on select on, selecting text in a page copies it."

- [ ] **Step 1: Write the failing tests.**
  - **`PageViewSelectionTest`** (the hooks constructor):
    - a left release fires once after 100 ms, and a right release doesn't;
    - three Shift+Right releases 30 ms apart fire once, and a release without Shift doesn't;
    - Select All fires.
  - **`SelectionCopyTest`** (a page seam answering `Selection`):
    - off, `selectedText` is never asked;
    - on, `Text("abc")` copies "abc", `Text("")` leaves the clipboard, and `InFrame` runs the page's copy;
    - `copyOnSelect(false)` between two gestures stops the second copy;
    - with no gesture, nothing copies.

- [ ] **Step 2: Run** `./gradlew :jasper-plugin-browser:test`. Expected: FAIL.

- [ ] **Step 3: Implement.**

- [ ] **Step 4: Run** `./gradlew :jasper-plugin-browser:check`. Expected: PASS.

- [ ] **Step 5: Commit** "Browser: Copy on select copies page selections".

### Task 13: Docs and the check

**Files:**
- Modify:
  - `docs/browser.md`:
    - history and its page; bookmarks, the Edit Bookmark panel and their page;
    - the suggestions, the welcome and onboarding pages, and the palette scopes;
    - Snippets in page fields, with the text-only clipboard, and copy on select;
    - the Files table's `data/browser.sqlite` and `data/onboarding-finished`;
  - `docs/keyboard-shortcuts.md` (History);
  - `docs/command-palette.md` (the two scopes);
  - `docs/history-and-snippets.md` (Insert in pages);
  - `docs/terminal.md` and `docs/configuration.md`: `terminal.copy_on_select` also copies page selections;
  - `docs/plugin-ui.md` and `docs/plugin-authoring.md`: the token field, `setTextInsertion` and `TextTarget`;
  - `docs/STATUS.md`, and this plan's banner, with the calls and deviations.

- [ ] **Step 1: Write the docs,** documenting only what the code does, and nothing of plans 6 to 10.

- [ ] **Step 2: Run the source hygiene script,** then `./gradlew sdkReference`, `./gradlew check` and the
  external-plugin check. Expected: PASS. Record the counts from the XML.

- [ ] **Step 3: Commit** "Docs: browser history, bookmarks and the Jasper integrations".

### Task 14: The user's GUI check (pending, never blocking)

**Files:**
- Modify: `docs/browser-verification.md`: a "Plan 5" section with the list below, each line "not run". The branch may
  merge while it is pending.

- [ ] **Step 1: Write the section.** The user runs it on macOS (`:jasper-app:run` from the worktree, with their OK).
  Each line is recorded pass or fail:
  1. **Recording:** three sites visited show newest first on Cmd+Y's "Browsing History", and Jasper's pages don't.
     Search, ×, Load More and Clear All History work.
  2. **Bookmarking:**
     - ☰ › Add Bookmark opens Edit Bookmark, tags are added with Enter and a comma, and Save keeps them;
     - Remove Bookmark shows "Bookmark Removed";
     - Delete Bookmark asks, then shows "Bookmark Deleted";
     - the panel closes when the page navigates.
  3. **The bookmarks page:** search, the sorts, the tag chips, ×, Load More and Clear All.
  4. **Suggestions:** typing shows "Bookmark" and "Visited N times" rows.
  5. **The palette:**
     - Bookmarks and Browser History rows show in All, and "Open URL in Browser" for an `https://` URL;
     - a row opens in the current browser item, or in a new tab from a terminal.
  6. **Welcome and onboarding:** with a fresh `data/`, the start page goes through the three onboarding pages to
     "Welcome Back". Top Sites, Refresh and the Quick Actions work.
  7. **Snippets:**
     - Insert reaches a text input, a text area, an editable element (a GitHub comment box) and a field in a
       cross-origin frame (the clipboard path), and the clipboard comes back;
     - Paste and run submits a search form.
  8. **Copy on select:**
     - on, a drag, a double-click and a Shift+arrow selection copy, and a password field's selection doesn't;
     - off, nothing copies.
  9. **Keeping:** a relaunch keeps history and bookmarks.
  10. **Windows and Linux:** Ctrl+H opens History, from the address field too. This stays "not run" until the user
      has those hosts.

- [ ] **Step 2: Commit** "Docs: plan 5's GUI check, pending".

## What follows

- **Plan 6, privacy:**
  - /dev/null items get `new Stores(BrowserDatabase.memory(), ui)`, and the ☰ bookmark group hides for them;
  - `permissions` joins the schema the same way, and Permissions the Quick Actions;
  - the Ad Blocker becomes a `SetupSwitch`;
  - Clear Website Data empties the database;
  - Jasper's pages answer from the asking item's stores: their queries are routed by the browser identifier the
    handlers already receive (the `(browser, request)` of the history and bookmarks pages' `registerQueries` in
    `BrowserPlugin.start`, and of `WelcomePage.register`'s), so a /dev/null item's history page shows its own;
  - that routing goes through a concurrent map from browser identifier to `Stores` and `CertificateAllowance`, filled
    on the UI thread as an item's page gets its browser, emptied as it closes, and read on the background executor that
    answers the queries, because `BrowserItems` and `Page` are UI-thread only and only `engine` and `page` can read the
    identifier;
  - pre-flight F10's note: `CertificateAllowance` is package-private in `item`, so it moves beside the per-store data
    (into `store`, or the per-store holder into `item`).
- **Plans 6 to 8** each add their `FEATURES` rows.
