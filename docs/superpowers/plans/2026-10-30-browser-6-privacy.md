# Browser 6: privacy — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or
> superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Execution status (2026-11-09): Tasks 1-11 executed and reviewed (Task 1 as 1a and 1b, ruling V6), and Task 12, the
docs; the final reviews passed the branch "With fixes" and the docs "Approved with fixes", and the fix wave that
followed is done; Task 13, the user's GUI check on macOS, is pending** (branch `claude/browser-privacy`, worktree
`.claude/worktrees/browser-4b-4c-e6deab`, cut from local `main` `ef3347601`, which has plans 4a, 4b, 4c, 9 and 5
merged; the plan `daa19c574`, then commits `827ac12ba` to `03af74d75`, local `main` `67653bbf6` (Mermaid, plan 9's
slice 1b) merged in as `965c11432`, the docs, `7ad704159`, `51b74b627` and `1092ada75`, and the fix wave, `c7be4d2ed`
and `5125f2ba0`, and its docs; not merged or pushed; see `docs/STATUS.md`). Executed with per-task subagents and
reviews, each task's fix rounds until a re-review found every finding addressed. The pre-flight scan's findings F1-F44
were adopted as rulings (V0, cited V-F*n*) except where V1-V6 decide otherwise, and the controller's further rulings
are V7-V56; plan 4a's R0-R48 and the banners of 4b, 4c, 9 and 5 bind. On 2026-11-06 the user chose V1 ("Clear now +
wipe") and V3 ("Match Chrome"), which are no longer interim, and asked for no change to V2. The task reviews recorded
these deviations from the plan's text, each with its ruling:
- **Versions:** `JasperSdk.VERSION` 0.29.0 to 0.30.0, every bundled range `>=0.30.0, <0.31` (Task 1a). New SDK
  members, each `@since 0.30.0`: `NotificationHandle.remove()`, `ContentTab.setSavedTitle(Optional<String>)` (V-F2),
  and the testkit's `FakeNotifications.Post.count()` and `removed()` and `FakePluginHost.savedTitle`. **The Browser
  stays 0.2.0** (V-F30): `dev.jasper.browser.api` is unchanged. Nothing new ships, so `notices.toml` is unchanged; the
  filter list is downloaded at run time.
- **Task 1a** (`827ac12ba` "SDK: 0.30.0"): `0ddd79162`'s 39 files (V6).
- **Task 1b** (`85ea0c386` "SDK: a plugin can remove its notification, and a tab can choose the title it is saved
  with"; fix round `bed21d9d1` "SDK: a removed notification's links stop working"): the fake folds same-text posts
  within 60 s, as the host does (V-F24), so `BrowserPluginTest` and `VaultServiceTest` assert one post with a count
  (V7); a plain post removed before it lands is never made, and `remove()` after the plugin stops still takes the entry
  out of history; V8 (`NotificationCenter.restate`); V9, one hold count per `PluginHost`, and a stopping plugin's entry
  that another running plugin holds stays live with that plugin's links; the fake folds at exactly 60 s, with a
  package-private clock; "less than a minute" reads "at most a minute".
- **Task 2** (`16d02c130` "Browser: one data store per browsing group"; fix rounds `8fb14e137` "Browser: each browser
  has a request context of its own", `baf7b8f2a` "Browser: a group's context outlives its last browser" and `2b0b55240`
  "Browser: nothing native after the sweep, and a page closed before its browser exists closes it"):
  - `DataStore` and `DataStores` in `item` (V-F12); `PageContext.memory()` for the brief's static `inMemory()`;
    `Page.makeAfter(ready)` as V-F15's gate; the browser id filled by a new life span handler, `PageEvents.Lives`, as
    `onAfterCreated` is `CefLifeSpanHandler`'s (V-F13); `ChromiumThread` hands work over only after both
    `onContextInitialized` and JCEF's `onInitialization`, under `close()`'s lock; `DataStores.answer` routes Jasper's
    pages' queries; `live()` lists the persistent store only once its database opened;
  - V10: JBR's `jcef` has no share-storage creation (javap: only `createContext(handler)`), so `engine/BrowserContext`
    carries the kept context's native handle with a no-op dispose; V11; V12: `engine/KeptContext` counts the objects
    handed out, and DevTools browsers get counted objects of their own (`Chromium.anotherContext`); after the sweep only
    JCEF's `onBeforeClose` disposes natively (`Chromium.handBack`), and a disposed record refuses to hand out; V13:
    `page/BrowserClosing`.
- **Task 3** (`7b56fd68d` "Browser: /dev/null mode"; fix round `e8007b96c` "Kit: a click on an open popover's anchor
  closes it"): one public `DataStores(Stores, BooleanSupplier)`; the 4-argument open uses `forNewItem`;
  `BrowserRestore.state`'s fourth parameter, `devNull`, saves `devnull = "true"` and no `url`, and the item calls
  `setSavedTitle(TabTitle.UNTITLED)` in the turn `openTab` ran (V-F2); each restored /dev/null item comes back in a
  store of its own (V-F42); `workspace.toml`'s writes are debounced
  by 1 s, so none falls between the two; the popover's and `devnull_mode`'s copy name Settings › Tools › Browser ›
  Privacy & Data (V-F28); V14: `HostedPopover.dismissed` consumes a press on the popover's own anchor, for every
  anchored popover (the Vault's credential picker and the Edit Bookmark panel too), and the plugin's 300 ms guard goes.
- **Task 4** (`fe1d07f4e` "Browser: site permissions, their page and the menu's resets"; fix round `9052f678a` "Browser:
  site permissions' review fixes"): `PermissionsPage.answer(PermissionStore, Consumer<Set<String>>, String)` (V-F12);
  the `permissions` table in `BrowserDatabase`'s schema, `user_version` still 1 (V-F32); Permissions after Settings in
  the Quick Actions (V-F29); a write its closed store refuses is dropped at DEBUG (V-F33,
  `store/DatabaseClosedException`); `DataStores.onPermissionsUnsaved`; `revoke`, `clearSite` and `clearAll` always
  queue their `DELETE`; the `Page.useReloader` test seam; test-only `store/DatabaseFixtures`; `PrivacyMenu` an instance
  per item; the fix round also escapes Task 3's raw `&` in `BrowserSettings`' Javadoc; V15; V17, amended by V18.
- **Task 5** (`9c0b54be0` "Browser: camera and microphone permissions"; fix round `42e5ed553` "Browser: a page cannot
  time a click onto Allow"): a browser that is no page's gets `Cancel()` and true (V-F26); V16's top-level origin is
  read from `getMainFrame().getURL()` on Chromium's thread, as the committed address lags on Swing's; unknown media
  flags are refused, as screen capture is; Enter presses nothing on the prompt; one `DialogFlood` per page, shared by
  prompts and script dialogs (V-F25); the plugin's stop dismisses the prompts (V-F43); `packageApp` and
  `verifyPackage`, without AOT and headless, read back the two usage strings; V19: `PageModal` takes a gate, the
  prompt's waiting out the interval from its show and from another of the page's modals closing
  (`DialogFlood.lastClosed()`), `ScriptDialog`'s holding alerts and confirms while a prompt shows or waits; a
  main-frame request whose origin is not the main frame's is cancelled; `MediaAccess` on the preview client too; a
  volatile static test clock (`PermissionPrompt.useClock`, through `DialogsTestAccess`).
- **Task 6** (`d78230539` "Browser: page notifications as Jasper notifications"; fix round `15c125a04` "Browser: page
  notifications only from secure, persistent pages"): a challenge address gets no polyfill, even as a main frame; at
  most 16 router queries wait per page; "default" and "denied" are read from the store after the boolean answer; the
  content is cut to 4,000 characters; V20, V21 and V22 as one rule (no site that may: denied, no prompt, nothing
  shown), `Origins.secure` shared with `Isolation.secure`; the router installed from `PageNavigation`'s installer with
  the client's `OpenerBridge`, reading the bridge's sender in `onQuery` (`PrivacyQueries.prepare` gone); titles on one
  line, bodies with at most two blank lines in a row; V22's polyfill state is the router's alone, so a sandboxed
  document's polyfill may read "granted" while each show fires `error`.
- **Task 7** (`7ccd85ca6` "Browser: audio muting, its bar and Unmute Current Page"; fix round `9d14ea65d` "Browser:
  audio muting's review fixes"): V23; one wrapper per prototype (a `WeakMap`) serves `AudioContext` and
  `webkitAudioContext`; a stored Block runs `mute(true)`; `PagePrivacy` keeps an atomic (document, mode) pair, whose
  compare-and-set refuses a `mute()` once another document started; `attachShadow` wrapped; `PrivacyMenu` registers in
  `follows()`; a `StartLoadsNoChromiumTest` regression (`Page` loaded `CefBrowser`) found and fixed.
- **Task 8** (`00b7ea4e6` "Browser: its settings page, and clearing data"; fix rounds `4a89ad081` "Browser: clearing
  data's review fixes" and `807b7eb86` "Browser: a cleared database never comes back"): `WebsiteData` in `item` (a
  store and permissions cycle otherwise); `HistoryStore.origins(limit)`; the live set as V-F20 has it; the start-time
  wipe in `Chromium.enter()`, on the worker after the restriction; cookies left for the start deleted in a
  `beforeClient` gate, as the cookie manager works only after `onInitialization`; the profile renamed, then deleted, on
  every platform (V-F18); Clear Cookies also waits for a starting Chromium (V-F16); `persistent()` made at the plugin's
  start, opening nothing; V24; V25: the data folder's first use deletes a marked database (`DataFolder` hands it to
  `ProfileWipe.wipeMarkedDatabase`, public for it), the marker reading `all database` then `database`, and
  `PermissionStore.clearAll()` returns `Cleared(sites, written)`; V26 (`Chromium.onClearFailed`); V27: the hook before
  any chmod, `DataFolder.databaseUsable()`, and the database erased first with the `database` mark whenever the erase
  or the compaction does not complete.
- **Task 9** (`d3cba7f8f` "Browser: compile WebKit content-blocker lists"; fix rounds `9daf9123a` "Browser: the
  content-blocker compiler's review fixes" and `9348006d4` "Browser: the content blocker charges what a read costs"):
  `BlockerJson` reuses 4b's `StrictJson` with 32 MiB and depth 8; at most 8 nested groups, as `Pattern.compile`
  overflowed a cold stack; a `StackOverflowError` catch in `matches`, a last resort reported through the list's one
  log line; strict keys on rule and action objects; a `null` anywhere refuses the list; `if-domain` rules keyed by host
  ahead of the 4-gram; `genericStylesheet(pageUrl)` for sub-frames (V-F34); V28 and its detail, V29-V33; `(xy)?`
  repeated 400 times is 401 quantified atoms, so the test takes 255 (256 atoms) and 256 is refused; a case-sensitive
  `^https?://` stays generic, in a shared part of its own for pages starting exactly `http://` or `https://`, as
  EasyList's generic rules are case-sensitive; V34 recorded.
- **Task 10** (`2612259e9` "Browser: ad blocking from a content-blocker list"; fix round `5b3407b83` "Browser: ad
  blocking's review fixes"): the validator's text, "Enter an https address, or an http address on this computer.", as
  V-F10 makes the brief's untrue; the constructor's `Runnable restrict` and `Supplier<HttpClient>` (V-F11, V-F8); V35;
  `insertRule` on an adopted sheet for the plan's `<style>` (V-F5); a package-private `BrowserPlugin` seam for the
  list's client and address (V-F9), which the tests share through `OfflinePlugins`; http loopback is `localhost`,
  `127.0.0.0/8` and `[::1]`, not `*.localhost`; DevTools browsers get no ad blocking; `checkNow()` at a new address;
  V36; V37; V38; V39 (`StrictJson.quotedLength`); V40; V41: `FilterList`'s 751 lines split into `ListDownload` (315),
  `ListCache` (128) and `FilterList` (565); a failed check logs its stack trace once per reason.
- **Task 11** (`9871d6617` "Browser: the HTTP proxy"; fix round `03af74d75` "Browser: the proxy's review fixes"):
  V-F38 by javap and otool (AGENTS.md's Verified JCEF behaviour); a DEBUG read-back of `getPreference("proxy")`; a
  refused /dev/null context leaves its tab blank, with "The proxy could not be set" (V-F4, V44); strict hosts refuse
  underscores, Unicode names and a trailing dot (V45); Jasper's fetches under way end at a change, while the filter
  list's download finishes on its old client; the settings' unresolved `Optional<InetSocketAddress>` for the JDK side;
  `onProxyChanged` fires only on a real change; V43: `http/ProxyRoute` (102 lines) matches Chromium's implicit bypass
  as text, and `ListDownload`'s http rule uses its stricter `loopback()`; V46: `NetworkPreferences` 55 to 140 lines,
  `Chromium` 1,014 to 984; `EngineTestAccess` plays Chromium's initialization for tests; `setProxy` given right after
  `Chromium` is made; "The proxy could not be set" is one constant.
- **The merge** (`965c11432`): local `main` `67653bbf6`; one conflict, in `BrowserPlugin.java`, both sides kept; four
  test calls to `PreviewScheme()` given `Runnable::run`.
- **Task 12** (`7ad704159` "Docs: the browser's privacy features", and `1092ada75` "Docs: plan 6's counts after the
  docs"), the docs: the Browser guide (/dev/null mode, site
  permissions, page notifications, audio, clearing data, ad blocking, the HTTP proxy, and the toolbar, the welcome
  page, History, certificate errors, Restore, Jasper's own requests, Settings and Files), the configuration guide's
  plugin table, AGENTS.md's Verified JCEF behaviour (V-F38 and V10), this banner and STATUS. `docs/packaging.md`'s
  camera and microphone section came with Task 5. After both docs commits, at `51b74b627`, `./gradlew check --continue`
  (JBR 25.0.4.1 b635.75 jcef, 10m 13s, 31 of 172 tasks run) gave **15,518 tests, 0 failures, 0 errors, 103 skipped**
  (1,388 classes in the 16 build folders, without the example plugin's: jasper-app 5,291 with 90 skipped, Editor 5,387
  with 4 skipped, Browser 1,324 with 1 skipped, jasper-ui 1,020, Remote 659, jasper-sdk 135, jasper-sdk-testkit 310),
  as at `965c11432` before the docs: the 15,086 of plan 5 and slice 1b and plan 6's 432. The example external plugin's
  `check pluginZip` against `build/sdk-release` passes (1 test, up to date: no SDK change); `sdkReference` changed
  nothing; source hygiene is clean over 3,452 Java files, and no file the docs touched holds a raw U+00A0, U+2028 or
  U+2029. The full checks after each task: 15,078 (Task 1a), 15,088 (1b), 15,139 (2), 15,181 (4), 15,208 (5), 15,279
  (7), 15,328 (8), 15,421 and 15,424 (9), 15,454 and 15,477 (10), 15,494 and 15,510 (11), each with 0 failures, 0
  errors and 103 skipped. No GUI was launched.
- **Task 13** (`51b74b627` "Docs: plan 6's GUI check, pending"), the checklist:
  [Plan 6: privacy](../../browser-verification.md#plan-6-privacy), every line pending.
- **The final fix wave** (after the final reviews of `67653bbf6..1092ada75`: A, Tasks 1-8 and the engine and page
  wiring of 10 and 11, and B, Tasks 9-11, each "With fixes", and C, the docs, "Approved with fixes"; rulings V47-V56):
  - `c7be4d2ed` "Browser: the content blocker's final fixes" (W1, V48-V53, V56): compiled patterns live in one
    `PatternCache` per `RuleSet`, least recently used out first, within an estimated 32 MiB (256 bytes a pattern and
    its text, 48 a node, 320 a class, and 576 plus 4 a character for the table of a case-sensitive leading run of 4 or
    more), and none compiles under its lock; a compile is charged 50 plus 8 a character of the longer of its source
    and expression, plus such a run's square over 8, so a full budget of compiles takes at most 8.8 ms median (9.4 ms
    at most) on this Mac, warm, and one the budget cannot pay is not started; a one-member class is written as its
    character; the review's probe at 32 MiB under `-Xmx2048m`, which ran out of memory, retains 1 MB, its first calls
    10 to 16 ms (V48). `BlockerJson` counts the values outside strings, members' names left out, and refuses a list of
    more than 2,000,000 before it parses, "Not a content blocker list: more than 2,000,000 values" (V49). Each rule a
    call examines is charged 1, those that do not fit too, and an ignore rule whose match overruns counts as matched
    (V50). The filter list's client always has a selector, `ProxyRoute.byDefault()` while the proxy is off (this
    computer directly, the rest as the JDK's default selector routes it); a retired client follows no redirect ("the
    HTTP proxy changed during the download"), and an address changed during a download is checked once it ends (V51).
    Filter Statistics' "Loads when ad blocking is on and a browser page opens" (V52). The `updating()` assertions hold
    the test server on a gate, successes get 30 s and only `/hold` 700 ms, the daemon-thread test reads the list's own
    scheduler's threads, and an unparsable `Location` reads "the filter list was redirected to an address that cannot
    be read" (V53);
  - `5125f2ba0` "Browser: the final review's fixes" (W2, V54): a clearing helper Chromium makes once it no longer runs
    closes as it is made (`ProfileClearing.HelperClose`); a refusal of the system's proxy settings while the proxy is
    off is logged, not posted; one `Origins.host` serves `ChallengeFrames` and `PageNotifications`; one constant,
    `AboutScheme.UNANSWERED`, holds "Jasper could not answer this request.", for all six of its copies, the opener
    bridge's and `AboutQueries`' among them; `DataFolder`'s Javadoc says only the permissions are left alone on
    Windows; Task 1b's direct `restate` test and the identity comment in `HostedNotifications`, Task 5's clock restored
    in a `finally`, and Task 6's script comments softened, in `notifications.js` and `audio.js`; Chromium's clearing
    moved, as a pure move, into the package-private `ChromiumClearing` (282 lines; `Chromium` 984 to 792; V46), whose
    seams the tests reach through `Chromium.clearing()`. The `CompletionException` unwrap is not shared: its copies
    are in `engine`, `item` and `permissions`, and `engine` and `permissions` share no package but `json`;
  - the docs (V47, V55 and this banner): the Browser guide (blocked requests "fail", the four softened sentences, the
    list's 2,000,000 values, an address changed during a download, Filter Statistics' new text), the checklist (its
    blocked-requests and Filter Statistics lines, and three lines the wave added), the notifications guide's Bookmarks
    and Page notifications groups, the README's, Plugins' and the docs index's Browser descriptions, and STATUS;
  - Verification: W1's RED and GREEN are in its commit. W2's tests came first (the compile failed on the missing
    `HelperClose`, `Origins.host`, `AboutScheme.UNANSWERED` and two-argument `refused`); reverting each change alone
    then failed its tests: the stopped Chromium's helper 1, the refused system settings 3, `restate`'s expired entry 1
    and its other text 1. The move kept `ChromiumStateTest`'s 57 tests passing, changed only in reaching the clearing's
    seams; `:jasper-plugin-browser:check`, the Javadoc and `verifyPluginArchitecture` pass; `ThemeMigrationCoverageTest`
    asked for `ChromiumClearing`'s row in `migration.csv`; `sdkReference` changed nothing; source hygiene is clean, and
    no file the wave touched holds a raw U+00A0, U+2028 or U+2029. No GUI was launched.

The controller's rulings, one line each:
- V0: every ruling the pre-flight scan proposed (F1-F44) is adopted and cited as V-F*n*, except where V1-V6 decide
  otherwise.
- V1: F18, the user's choice ("Clear now + wipe", 2026-11-06): Clear Website Data clears live and wipes
  `<data>/chromium` at the next start, and the confirmation and the docs say that sign-ins made before the next start
  are lost too.
- V2: F23: a page notification's content begins with the page's host ("example.com — BODY"), as Chrome and macOS name
  the site.
- V3: F39, the user's choice ("Match Chrome", 2026-11-06): no extra WebRTC or DNS-prediction preferences while the
  proxy is on, as Chrome; the Browser guide records the 407, DNS-prediction and WebRTC STUN leaks.
- V4: F35: no benchmarks now; Task 13 records a start-up and memory batch, ad blocking off and on, as pending for the
  benchmark sitting.
- V5: F1: no GUI launch; F1's `jasper-about:` in /dev/null tabs is the first pending GUI line.
- V6: F30: Task 1 splits into 1a (the SDK 0.30.0 bump alone, over `0ddd79162`'s file list) and 1b
  (`NotificationHandle.remove()` and F2's saved-title member), each reviewed.
- V7: Task 1b's additions are accepted (`Post.count()`, a plain post removed before it lands never made, `remove()`
  after the stop still removing); Task 6's "11th post in 60 s" case uses distinct texts or `count()`.
- V8: each handle keeps its own request; a partial removal puts the newest remaining handle's request back on the
  entry (the host's center call swaps the request, keeping count and read state; the fake keeps handles per post).
- V9: the host counts handles per center entry across plugins, as the fake does; the group-id overlap itself is
  recorded.
- V10: each browser gets a disposable `CefRequestContext` object of its own over the group's shared storage: CEF's
  share-storage creation if JCEF exposes it, else an engine subclass with a no-op dispose over the kept native handle;
  the kept context is Jasper's alone, released once.
- V11: in-memory databases set `PRAGMA temp_store = MEMORY`.
- V12: the kept context is disposed only when its group has closed and every `BrowserContext` handed out over it has
  been disposed by JCEF; each handed-out dispose decrements and zeroes its own ref; DevTools browsers get counted
  objects of their own; the sweep follows the same rule after JCEF's browsers close.
- V13: Task 2 fixes the pre-existing deferred-close gap: Jasper's own `onAfterCreated` closes a browser whose page
  closed first, or a never-created `PageBrowser` hands back at close.
- V14: the host consumes a press that dismisses an anchored popover and lands on its own anchor, for every anchored
  popover; the plugin's timer guard goes.
- V15: the unavailable notice reads "Browser history, bookmarks and site permissions are unavailable".
- V16: camera and microphone grants are keyed by the page's top-level origin, not CEF's requesting frame origin, and
  the prompt names it.
- V17: ☰'s Reset revokes the origin the menu was rendered for, even after a navigation.
- V18: amends V17: the item remembers, per permission, the site the menu last offered that Reset for, and revokes it
  there.
- V19: the prompt ignores both answers until the double-click interval (`awt.multiClickInterval`, else 500 ms) has
  passed since it showed or since another modal of the page closed, and a page's script dialogs wait while its prompt
  shows.
- V20: as Chrome's Incognito, a /dev/null item denies page notifications: the polyfill reports "denied",
  `requestPermission` resolves "denied" without a prompt, and the router refuses a /dev/null browser's posts.
- V21: notifications need a secure context, as in Chromium: https or loopback http; `file:` is denied.
- V22: a sandboxed (opaque-origin) main-frame document is denied, judged by the opener bridge's current
  `Document.origin()`.
- V23: Task 7's calls are accepted: a document with no site is muted with no bar, ☰'s Unmute plays it and nothing is
  stored; one `audio.playing` per page per main-frame document; the bar's answers count only while it shows;
  `audio.js` wraps the `muted` accessor, so a player's own mute survives Allow Once, and leaves the volume alone.
- V24: Clear All Data keeps deleting bookmarks, and the confirmation names everything it deletes (history, bookmarks,
  site permissions, cookies, cache and local storage) with V1's warning.
- V25: a file-backed database running in memory makes Clear Website Data fail with a cause, and marks the
  `browser.sqlite*` files for deletion at the next start.
- V26: a failed or late per-origin storage call is logged and left to the wipe; the cache, the cookies and the marker
  still fail the run; the database rows are erased even when the live part fails, and the failure is reported.
- V27: the marked-database hook runs before, and regardless of, the chmod; a marked file that cannot be deleted makes
  the database run in memory for that run and keeps the mark; the marker gains `database` whenever the erase does not
  complete, and the database is erased first.
- V28: a `css-display-none` selector list is split at top-level commas (outside `()`, `[]` and quotes), each part at
  most 1,024 characters and refused on `{`, `}`, `;`, `@` or `<`, at most 5,000 parts a rule, the page's sheet still
  2 MiB; a `url-filter` of `.*` or `^https?://` (with or without a trailing `.*`) counts as generic for the
  precomputed sheet.
- V28's detail: a `\`-escaped character never splits a part; `Rule` keeps its selector as written, and one splitter
  serves `ContentRules`' check and `RuleSet`'s lines; the generic place search is a lower bound; `^https?://` generic
  rules form an http(s)-only part of the generic sheet, chosen by the page's scheme, and the index agrees with
  `scanStylesheet` on `file:`, `data:`, `about:` and upper-case schemes.
- V29: a quantified group whose content can match the empty string is refused, and a filter has at most 256
  quantified atoms.
- V30: each `blocks` and each `stylesheet` call has one budget of 2,000,000 reads shared across the rules it runs (the
  per-match 100,000 stays); past it, `blocks` answers not blocked and `stylesheet` keeps only what the walk has
  proved; an overrun logs once per list, and no filter is disabled.
- V31: a class is written out as its member characters (printable ASCII only, so at most 95), which Java compiles into
  one bitmap.
- V32: a list holds at most 500,000 domain entries in all (rules past that are skipped and counted), and a literal's
  rarity is counted and chosen over its first 64 4-grams only.
- V33: a read is charged 1 plus the filter's count of `*`- and `?`-quantified atoms, and a literal pre-check 1 plus the
  address length / 32 (of the capped address), both against the match budget and the call's.
- V34: a list's compile (`ContentRules.compile`, `RuleSet.of`) stays on the background executor; a hostile 30 MiB list
  may take seconds and a transient heap peak there, recorded as a known gap. Amended by V49 and V56: at the cap of
  2,000,000 values a list's parse and compile peak at about 404 MB for 2,000,000 empty objects (286 ms; it completes
  in a 256 MiB heap) and about 600 MB for 250,000 real-looking rules (436 ms; it needs about 400 MiB), both under half
  of `-Xmx2048m`, and a list one value past it is refused in 12 to 26 ms.
- V35: `SetupSwitch.set` returning the save's future is accepted, a plan 5 signature change.
- V36: before the list has been read, the status reads "Loads with the first browser page" instead of "0 rules".
- V37: the hourly check runs on a `ScheduledExecutorService` the list owns (one named daemon thread), shut down in
  `close()`, and the tick reads its own future under the lock.
- V38: redirects are followed by hand (`Redirect.NEVER`, at most 5, each target checked by `fetches` before it is
  requested, within http and https), so a refused target is never asked.
- V39: a frame's quoted script is capped at 4 MiB, the sheet cut at a line boundary to fit, and a main frame whose
  sheet equals the cached generic sheet reuses the cached quoted script; `StrictJson.quote` stays as it is.
- V40: ws and wss requests get `websocket` and `raw`, and `RT_NAVIGATION_PRELOAD_SUB_FRAME` gets what `RT_SUB_FRAME`
  gets.
- V41: `FilterList` is split along the reviewer's seams: `ListDownload`, `ListCache` and `FilterList`.
- V42: with the proxy off, Chromium uses the system's settings and Jasper's own fetches the JDK's default selector,
  which follows the system only when the JVM runs with `-Djava.net.useSystemProxies=true`; the docs say so, naming
  Settings' custom VM options as the way, and the Browser sets no process-wide property.
- V43: Jasper's own fetches go direct for Chromium's implicit bypass hosts (`localhost`, `*.localhost`,
  `127.0.0.0/8`, `[::1]`, `169.254.0.0/16`, `[fe80::]/10`) and through the proxy otherwise, by one selector in `http`
  whose loopback rule `ListDownload` shares.
- V44: a refused /dev/null context keeps failing closed, with a blank item and the notification, which names the
  cause; `PageContext` drops a failed future, so the group's next item asks again.
- V45: a kept context that refuses a change keeps its old route, with the error posted, recorded as a known gap;
  underscores stay refused.
- V46: the proxy state moves into `NetworkPreferences` now, `Chromium` keeping the `proxyLive` hand-off and a delegate;
  splitting the clearing out of `Chromium` goes to the final wave.
- V47: the README's and `docs/plugins.md`'s Browser descriptions name plan 6's features (/dev/null mode, site
  permissions, page notifications, audio muting, clearing data, ad blocking, an HTTP proxy) in their own style, in the
  final fix wave.
- V48: compiled patterns live in one cache per `RuleSet`, least recently used out first, within an estimated weight of
  32 MiB; a filter's compile is charged to the call's budget, calibrated so the 2,000,000 budget bounds a call's
  compiles to about 10 ms on this Mac; a compile the budget cannot pay is not started; a one-member class is written as
  its character.
- V49: `BlockerJson` counts the JSON values outside strings in one linear pre-scan and refuses a list of more than
  2,000,000 before it parses; V34 is amended with the peak measured at that cap.
- V50: each candidate a call examines is charged 1 against the call's budget, and an ignore rule whose match overruns
  counts as matched, so both budgets err toward allowing the request and hiding less.
- V51: the filter list's client always has a selector (`ProxyRoute.loopback` hosts directly, everything else through
  the proxy when it is on and `ProxySelector.getDefault()` when it is off); a retired client refuses further redirect
  hops; a `filter_list_url` change during a download checks again once it ends.
- V52: amends V36: the status reads "Loads when ad blocking is on and a browser page opens".
- V53: the fix wave fixes the timing-sensitive tests (the `updating()` assertions hold the server on a latch, only
  `/hold` has the short deadline, the daemon-thread test reads the list's own scheduler's threads) and the unparsable
  `Location`'s reason.
- V54: part A's fixes in the wave: a clearing helper made after Chromium stopped running closes at once, with a GUI
  line; a refused `{mode: system}` while the proxy is off is logged, not posted; one `Origins.host`; one constant for
  "Jasper could not answer this request."; `DataFolder`'s Windows Javadoc; Task 1b's `restate` test and identity
  comment, Task 5's clock in `try/finally`, Task 6's softened script comments; Chromium's clearing moved, as a pure
  move, into a package-private engine class.
- V55: part C's docs fixes and V47 in the wave: "fail" for `ERR_BLOCKED_BY_CLIENT` in the guide and the checklist; the
  notifications guide's Page notifications and Bookmarks groups; the four softened sentences; `1092ada75` and
  `BrowserItem`'s size in the banner and STATUS; the README's, Plugins' and the docs index's Browser lines.
- V56: W1's calls are accepted: the compile charge counts the source and a case-sensitive leading run as well as the
  expression, as the 10 ms bound needs; a hostile list's first call may take about 30 ms while the JIT is cold;
  gathering the always bucket's candidates stays uncharged (1 to 7 ms, bounded by the 250,000 rules); V34 is amended
  with the measured 404 MB parse peak at the cap.

Known gaps:
- V34, as V49 and V56 amend it: a list's parse and compile, on the background executor once per list change, peak at
  about 404 MB for 2,000,000 empty objects and about 600 MB for 250,000 real-looking rules.
- V56: a hostile list's first call may take about 30 ms while the JIT is cold (5 to 9 ms once warm); gathering the
  always bucket's candidates is not charged (1 to 7 ms at 250,000 rules); a filter of long literals is estimated at 5
  to 10 times its weight, so the cache holds fewer of them than 32 MiB would.
- On an 8 KiB address the literal-check charge reaches the call budget after about 7,800 checks, so only a list that
  large is cut short there, in the safe direction (a 200-character address allows about 285,000).
- Task 10's M9: a hidden element's suppressed lazy load is a side channel a list author could use with
  `:has(input[value^=…])`, as with WebKit's content blockers, which softens V-F5's "neither load nor exfiltrate".
- V42: with the proxy off, Jasper's own fetches ignore a proxy the system alone names, unless the JVM runs with
  `-Djava.net.useSystemProxies=true`.
- V3's leaks: a proxy that answers 407 fails the page with no prompt until plan 8; DNS prediction and WebRTC STUN go
  around an HTTP proxy.
- Task 11: a Jasper fetch of a `*.localhost` name goes direct but may be looked up by the system resolver; a
  non-decimal IPv4 spelling (`0177.0.0.1`), a trailing-dot IP (`127.0.0.1.`) and a zone-id link-local address go
  through the proxy where Chromium bypasses them (the safe direction); V45: a kept context that refuses a change keeps
  its old route, with the error posted.
- Recorded minors: `PageBrowser.close`, `created()` and `Lives`' wiring is untested headless (a GUI line); `contexts`
  keeps every record for the run (negligible); a creation CEF never completes keeps its group's in-memory context until
  quit (RAM only) (Task 2); a stop while the erase is pending plus a failed `DELETE` may leave no database mark (it
  needs both); `BEFORE_START` ignores `StartOnce`; a clearing timed out before the worker writes no marker (Task 8); the
  fake attributes a failing link on a shared post to its first poster (Task 1b).

Deferred minors, by task, less those the fix wave fixed; the final reviews kept the rest:
- Task 5: a held-then-dismissed script dialog restarts a prompt's interval (errs safe).
- Task 6: `SitePermissions.ask` applies only V20, not V21 or V22, on Chromium's native path, where Chromium refuses
  insecure contexts itself.
- Task 7: the method-shorthand wrappers report `length` 0 where the natives report 1 (fingerprintable; `audio.js:335`);
  `PrivacyMenu.close()` leaves a no-op handler.
- Task 10: an unreadable cache shows "0 rules", as `read` is set.
- `BrowserItem` grew from 907 to 1,030 lines in this plan; final review A kept it whole.
- Final review B's minor 8: `EditorPluginTest` and `BundledSamplePluginTest` start the real plugin (acceptable).
- Final review A declined to judge: an OSC 8 link's target and zoom for a /dev/null tab; `Holds` keeping stopped
  handles (it could carry null in `Holds.stopped`); Chromium's UI thread on Linux; `chromium.log`'s console lines;
  plans 4a's and 4b's own items; the Web menu's History in a /dev/null tab showing the group's own.

Not run:
- **Task 13**, the user's GUI check on macOS, with their OK for each batch: pending
  ([checklist](../../browser-verification.md#plan-6-privacy)), V-F1's `jasper-about:` pages in a /dev/null tab first
  (V5), then the plan's eight groups with the task reviews' lines and the fix wave's, and the question for the user
  whether Manage Permissions may open its tab behind Settings.
- **Benchmarks** (V4): the start-up and memory batch, with ad blocking off and on, waits for the benchmark sitting.
- **Windows and Linux** (no hosts), and **packaging with AOT**, whose training needs the user's OK.
- **No JavaScript runs in a test:** `notifications.js`, `audio.js`, `hide-elements.js` and `permissions.js` are checked
  as text, with `node --check` and with throwaway node harnesses in the scratchpad (V-F41); no test starts Chromium.

**Goal:** Give the Browser iTerm2's privacy features:
- /dev/null mode, with its toolbar indicator and its restore at the start page;
- site permissions for the camera, the microphone, notifications and audio playback, with the prompt, the
  `jasper-about:permissions` page and the ☰ menu's reset items;
- page notifications as Jasper notifications;
- audio muting, with its bar and Unmute Current Page;
- Clear Cookies and Clear Website Data;
- ad blocking from a WebKit content-blocker list;
- the HTTP proxy.

**Architecture:**
- **A data store per browsing group.** `store.DataStore` bundles what a group of browser items shares: a Chromium
  request context, plan 5's `Stores` (its database and the history and bookmark stores on it), the site permissions
  and 4b's certificate allowances. The
  persistent store is Chromium's global context and `data/`. Each /dev/null item gets a new store: an in-memory
  context and an in-memory database, which the items it opens share. It ends when the last of them closes.
- **Plain types outside Chromium's packages.** Permissions, stores, the ad-block compiler and the item-side logic name
  no Chromium type. Chromium's side stays in `engine`, `page`, `links` and `dialogs`:
  - the media permission handler;
  - the frame scripts;
  - the router ops;
  - the ad-block request handler;
  - the proxy preference;
  - profile clearing.
- **Page scripts** (`notifications.js`, `audio.js`, `hide-elements.js`) are injected at load start, as 4a's R39 does.
  They talk to Java through a message router of their own, `jasperPrivacyQuery`, as plan 5's internal pages have
  `jasperAboutQuery`. The router is separate because `audio.js` reports from sub-frames, and the opener bridge refuses
  every sub-frame. Java treats every request as untrusted (R38). The permissions page uses plan 5's channel, which
  this plan makes answer for the asking item's store.
- **Settings › Browser becomes a plugin page** (`SettingsPanel`). It keeps every declared row and adds the data
  buttons, Update Now and the filter statistics.
- **One SDK addition:** `NotificationHandle.remove()`. A page's `close()` uses it to take its notification out of
  history.

**Tech Stack:** Java 25 and Swing on JBR 25.0.4.1 b635.75 with JCEF (Chromium 150.0.7871.129), Jasper's SDK and
testkit, plan 5's SQLite database, `java.net.http` for the filter list, JUnit 5.

**Spec:** [docs/superpowers/specs/2026-10-26-jasper-browser-plugin-design.md](../specs/2026-10-26-jasper-browser-plugin-design.md),
sections 12, 6 (the /dev/null indicator, the ☰ menu's reset items and Unmute Current Page), 14
(`jasper-about:permissions`), 16 (/dev/null items), 17 (Privacy & Data, Ad Blocking, Proxy), 21 (location) and 23
item 6. iTerm2's behaviour is in `.superpowers/iterm2-browser-features-A.md` items 10, 12, 15, 16 and 20. This plan
builds on [plan 4a](2026-10-28-browser-4a-browser-core.md), whose banner's rulings R0-R48 bind it, and on
[4b](2026-10-29-browser-4b-page-features.md), 4c and plan 5.

## Global Constraints

- **Branch:** from `main` once plans 4b, 4c and 5 have merged, in its own worktree.
- **SDK version:** Task 1's addition takes the next minor after `JasperSdk.VERSION` on that branch: 0.30.0 if plan 5
  took 0.29.0. It carries that `@since`, and every bundled manifest's range moves with it.
- **4a's names** (read at `claude/browser-core` `fc3d60270`; follow any renames since):
  - **Browsers and contexts:** `Page.make` (`page/Page.java:445-448`) makes every browser with a `null` request
    context, which is Chromium's global context, so every item shares one store today.
    `ChromiumOptions.apply` (`engine/ChromiumOptions.java:24-25`) keeps that context at `<data>/chromium`, with session
    cookies.
  - **Requests:** `PageNavigation.Links.getResourceRequestHandler` (`links/PageNavigation.java:68-72`) answers only
    main-frame navigations, with the opener bridge's response reader, and null otherwise.
  - **The router:** the opener bridge's router (`links/OpenerBridge.java:132`) answers top frames only (`:101`), and
    dispatches ops at `:327`.
  - **Load starts:** `PageEvents.Load.onLoadStart` drops sub-frames (`page/PageEvents.java:141-142`).
  - **Dialogs:** `ScriptDialog` (`dialogs/ScriptDialog.java:119-150`) is the model for a page's modal. It waits until
    its item is active, shows a `DialogSpec(..., OVERLAY)` over the item's tab, and is answered once.
  - **Items:** a page's links and popups open through `BrowserItem.openItem` (`item/BrowserItem.java:238-244`), then
    `BrowserItems.open` (`item/BrowserItems.java:54`). Saved items come back through `BrowserItems.restore` (`:69`).
  - **The ☰ menu:** `BrowserToolbar.node` (`item/BrowserToolbar.java:40-50`) holds the ☰ `MenuButton`, with
    Settings only.
  - **Restore:** `BrowserRestore` has the keys `url` and `target` (`restore/BrowserRestore.java:20`); `state` is at
    `:48`.
  - **Settings:** Settings › Browser is the page generated from `BrowserSettings.declare`
    (`settings/BrowserSettings.java:77`).
  - **Internal pages:** `WelcomePage` builds its HTML in the theme's colours when the plugin starts and after each
    theme change.
- **4b's names** (from its plan; follow its banner): `CertificateAllowance`, the ☰ structure, the shared CSS helper
  for internal pages, the `zoom` restore key, and the DevTools JSON reader of `NavigationHistory`.
- **Plan 5's names** (from `2026-10-30-browser-5-history-bookmarks.md`; follow its banner):
  - **The database** (`store` package, Task 1): `BrowserDatabase.file(Path, Consumer<Throwable> unavailable)` and
    `BrowserDatabase.memory()`.
    - `<T> CompletableFuture<T> call(Work<T>)` runs on its own thread, `jasper-browser-store`.
    - `close()` and `closed()`.
    - Every table is `IF NOT EXISTS`, so `permissions` joins the schema the same way.
  - **The stores:**
    - `HistoryStore` has `clear()`, `visits(int)` and `topSites(int)`;
    - `BookmarkStore` has `clear()` and `all()`;
    - `Stores(BrowserDatabase db, Executor ui)` (Task 2) holds `history()`, `bookmarks()`, `bookmarkIndex()` and
      `visitIndex()`. `BrowserPlugin` makes one over `<data>/browser.sqlite`, and `BrowserItems` holds it. Plan 5
      leaves "/dev/null items get `new Stores(BrowserDatabase.memory(), ui)`" to this plan.
  - **The internal pages** (`pages` package, Task 3):
    - `AboutPages.render(name, slots)`, with its CSP meta;
    - `about.js` (`jasper.ask`, `jasper.el`);
    - `AboutScheme.registerQueries(page, Function<String, CompletableFuture<String>>)` and
      `AboutScheme.query(frameAddress, mainFrame, request)`, served by `engine/AboutQueries`' router, `jasperAboutQuery`.

    They answer without knowing which page asked.
  - **JSON and dialogs:**
    - `json/Json` (4a's `BridgeJson`, moved), with `object` and `quote`;
    - `ScriptDialog.ask` takes the dialog's origin, and a `jasper-about:` origin skips the flood guard.
  - **The ☰ bookmark group** (Task 7): `browser.bookmark.add` or `.remove`, `browser.bookmarks` and `browser.history`.
  - **The palette** (Task 8): `BookmarksScope(BookmarkIndex, …)` and `VisitedScope(VisitIndex, …)`.
  - **The welcome and onboarding pages** (Task 9):
    - the welcome page's Quick Actions, which this plan joins with Permissions;
    - `record SetupSwitch(String id, String title, String description, BooleanSupplier on, Consumer<Boolean> set)`,
      where this plan passes the Ad Blocker's;
    - `OnboardingPages.FEATURES` rows, `record Feature(String title, String macOs, String other)`, which plans 6 to 8
      add to.
- **JCEF facts** (`javap --module jcef`, JBR b635.75, 2026-10-30; do not re-derive):
  - **Request contexts:** `CefRequestContext.createContext(CefRequestContextHandler)` is the only way to make a
    context.
    - The module has no `CefRequestContextSettings`, so JCEF passes CEF's defaults. CEF makes a context with an empty
      `cache_path` in memory ("incognito").
    - `CefRequestContext_N.createNative` accepts a null handler.
    - `setPreference(String, Object)` returns an error message, or null when the preference is set.
    - `getAllPreferences(boolean)` returns `Map<String, Object>`, so dictionaries cross as maps.
    - It also has `CloseAllConnections(CefCompletionCallback)` and `ClearCertificateExceptions`. It has no cookie
      manager of its own and no HTTP-auth clearing.
  - **Cookies:** `CefCookieManager` has only `getGlobalManager()`, with these members:
    - `deleteCookies(String url, String name)`: both empty deletes every cookie;
    - `visitAllCookies(CefCookieVisitor)`;
    - `flushStore(CefCompletionCallback)`.
  - **Camera and microphone:** `CefPermissionHandler.onRequestMediaAccessPermission(CefBrowser, CefFrame, String
    requestingOrigin, int requestedPermissions, CefMediaAccessCallback)`, added with `CefClient.addPermissionHandler`.
    - `CefMediaAccessCallback` has `Continue(int)` and `Cancel()`.
    - `CefMediaAccessCallback$MediaPermissionFlags`: `NONE` 0, `DEVICE_AUDIO_CAPTURE` 1, `DEVICE_VIDEO_CAPTURE` 2,
      `DESKTOP_AUDIO_CAPTURE` 4, `DESKTOP_VIDEO_CAPTURE` 8.
    - The module has no geolocation, notification or generic permission-prompt hook. It also has no audio handler and
      no mute call.
  - **Requests:** `CefResourceRequestHandler.onBeforeResourceLoad(CefBrowser, CefFrame, CefRequest)` returns true to
    cancel.
    - CEF asks a context's handler only when the client's `getResourceRequestHandler` returns null. 4a's client
      handler therefore covers every context.
    - `CefRequest$ResourceType` runs from `RT_MAIN_FRAME` to `RT_NAVIGATION_PRELOAD_SUB_FRAME` (20 values).
  - **The UI thread:** `CefApp.onContextInitialized()` hands over to the app handler's `onContextInitialized()`, as its
    bytecode shows. That runs on Chromium's UI thread.
  - **DevTools:** `CefBrowser.getDevToolsClient()` gives `CefDevToolsClient.executeDevToolsMethod(String, String)`,
    which returns `CompletableFuture<String>`.
  - **Frames:** `CefBrowser` has `getFrameIdentifiers()`, `getFrameByIdentifier(String)` and `getRequestContext()`.
  - **Helper browsers:** `CefBrowserOsrWithHandler(CefClient, String, CefRequestContext, CefRenderHandler, Component)`
    and `createImmediately()` make a browser with no view.
- **CEF rules not visible through `javap`** (CEF's `cef_request_context.h` and `cef_cookie.h`; Task 13 confirms):
  - **Preferences:** `SetPreference` must run on Chromium's UI thread, which is not Swing's (AppKit's main thread on
    macOS).
  - **Making contexts:** a context made on that thread is initialized at once.
  - **Hopping onto the thread:** `FlushStore`'s callback runs on that thread. Jasper gets onto it with the global
    manager's `flushStore`, which has no other effect.
  - **The proxy preference** is the dictionary `proxy`, either `{mode: "fixed_servers", server: "host:port"}` or
    `{mode: "system"}`.
  - **DevTools methods:** `Network.clearBrowserCache` and `Storage.clearDataForOrigin {origin, storageTypes: "all"}`
    act on the asking browser's context.
- **Boundaries and threads:**
  - **Chromium types** stay in `engine`, `page`, `links` and `dialogs` (`ChromiumBoundaryTest`). Plan 5's `store` and
    `pages`, and this plan's new `permissions` and `adblock`, name none.
  - **No Chromium at start** (`StartLoadsNoChromiumTest`): nothing in this plan loads a Chromium class at plugin
    start. Hooks that name one are given when a page first starts (4a's `Chromium.onClient` rule).
  - **No native calls after stop:** nothing touches a native object after `Chromium` has stopped.
  - **Threads:**
    - resource handlers run on Chromium's IO thread and must be fast and lock-free;
    - load-start hooks run on Chromium's UI thread;
    - item logic runs on Swing's.
- **No GUI from agents:**
  - Tests are headless and start no Chromium. The user's GUI check is Task 13.
  - Never run `:jasper-app:run`, benchmarks or AOT training without the user's OK, batch by batch. Never run
    `./gradlew --stop`.
- **Copy:** the spec's strings, with "iTerm2" read as "Jasper". **No animations.** Every platform.
- **Data hygiene:**
  - no user info in shown or saved URLs (R46);
  - nothing new ships, so `notices.toml` is unchanged (the filter list is downloaded at run time);
  - `ThemeMigrationCoverageTest` wants a `docs/design/theme-engine/migration.csv` row for each new class it flags,
    like 4a's rows.
- **Commits** end with:

  ```
  🤖 Generated with Claude Code at The Home Depot

  Co-Authored-By: Claude <noreply@anthropic.com>
  ```

## Review Focus

1. **/dev/null isolation on every path into a store.**
   - A /dev/null item's links, popups (with `window.opener`), OSC 8 reuse and restore stay in /dev/null.
   - Nothing it records reaches the persistent database or `<data>/chromium`.
   - Its context, database and certificate allowances end with the group's last item.
   - Turning the setting off leaves open /dev/null items as they are.
   - Closing stores after Chromium stopped touches no native object.

   Tasks 2 and 3 pin this.
2. **Web pages calling the new channels themselves:**
   - the permissions page's queries from a web page, or from a frame inside the permissions page;
   - `notification.show` without a stored Allow, whatever the script claims;
   - `Notification.requestPermission()` in a loop;
   - `audio.playing` from a challenge frame;
   - malformed or oversized requests.

   Each is refused, or answered without a prompt flood, and never throws. Tasks 4, 6 and 7 pin this.
3. **Prompts and the audio bar across an item's life:**
   - a prompt while the item isn't active;
   - the page navigating, or its item closing;
   - two requests at once;
   - the bar when the page navigates.

   Nothing is stored without an explicit Allow, Block or Always, and every Chromium callback is answered exactly
   once. Tasks 5 and 7 pin this.
4. **Clearing data in each of Chromium's states:** not started, starting, running with pages open, and failed.
   - Nothing cleared comes back after a restart.
   - The success message shows only once everything has finished.
   - /dev/null stores are untouched.

   Task 8 pins this.
5. **Hostile or broken filter lists:**
   - not JSON, over 32 MiB, or truncated;
   - unsupported rules, nested quantifiers that backtrack, or a 2,049-character filter.

   The old list keeps working. A lookup never runs a rule's expression on a URL that lacks the rule's literal. Tasks 9
   and 10 pin this.

---

### Task 1: The SDK takes back a notification

**Files:**
- Modify: `jasper-sdk/src/main/java/dev/jasper/sdk/JasperSdk.java` (`VERSION`). Move every bundled `plugin.toml`'s
  range, `docs/examples/external-plugin`, the SDK README's compatibility table and the guide pages, as 4a's Task 2
  did.
- Modify: `jasper-sdk/src/main/java/dev/jasper/sdk/notifications/NotificationHandle.java`: add `remove()`.
- Modify: `jasper-app/src/main/java/dev/jasper/app/plugins/HostedNotifications.java` (`Handle`, :178). It reaches
  `NotificationCenter.remove(UUID)` (`notifications/NotificationCenter.java:238`) on the UI thread. A handle removed
  before its post lands removes the post as it lands.
- Modify: `jasper-sdk-testkit/src/main/java/dev/jasper/sdk/testing/FakeNotifications.java` (handle at :241).
  - `Post` gains `removed()`.
  - The harness's notification list leaves removed posts out.
- Modify: `docs/plugin-authoring.md` (notifications), where `expire()` is described.
- Test: `PluginContractTest` (testFixtures, both runtimes) and `HostedNotificationsTest`.

**Interfaces:**
- **`void NotificationHandle.remove()`**, `@since` the version above. Javadoc: "Removes the notification: its card
  closes, its links stop working and it leaves history. A removed notification also reads as expired. Idempotent;
  safe from any thread."

- [ ] **Step 1: Write the failing tests:**
  - `aRemovedNotificationLeavesHistory`:
    - after `post` then `remove()`, the harness lists no such notification;
    - `expired()` is true;
    - removing twice, and removing after `expire()`, both work.
  - `HostedNotificationsTest.aRemoveBeforeThePostLandsRemovesItWhenItLands`.

- [ ] **Step 2: Run** `./gradlew :jasper-sdk-testkit:test :jasper-app:test --tests '*HostedNotificationsTest'
  --tests '*AppContractTest*'`. Expected: FAIL.

- [ ] **Step 3: Implement** the member, the host side, the fake, the version bump and the guide line.

- [ ] **Step 4: Run** `./gradlew sdkReference`, then `./gradlew check`, then
  `./gradlew -p docs/examples/external-plugin -PjasperSdkDir="$PWD/build/sdk-release" check pluginZip`. Expected: PASS.
  Commit the regenerated `docs/sdk-reference.html`.

- [ ] **Step 5: Commit** "SDK: a plugin can remove its notification".

### Task 2: One data store per browsing group

**Files:**
- Create: `plugins/browser/src/main/java/dev/jasper/browser/store/DataStore.java` and `DataStores.java`, in plan 5's
  `store` package, whose `package-info.java` says so.
- Create: `plugins/browser/src/main/java/dev/jasper/browser/page/PageContext.java`.
- Modify: `engine/Chromium.java` and `engine/ChromiumApp.java`. The app handler overrides `onContextInitialized`, and
  `Chromium` gains `onUiThread`, `inMemoryContext` and `release`.
- Modify: `page/Page.java`. Its constructor takes a `PageContext`, and `make` (:445-448) makes the browser with it once
  it is ready.
- Modify: `item/BrowserItem.java` and `item/BrowserItems.java`.
  - An item holds its store.
  - `openItem` passes it on.
  - `open` and `restore` take a store.
- Modify plan 5's wiring:
  - the persistent store holds the `Stores` that `BrowserPlugin` already makes over `<data>/browser.sqlite`. A
    /dev/null store holds `new Stores(BrowserDatabase.memory(), EventQueue::invokeLater)`;
  - these take the item's store's `Stores` instead of `BrowserItems`' one:
    - `BrowserItem`'s history recording;
    - `StoreSuggestions`' indexes;
    - the ☰ bookmark group;
    - `BookmarkPanel`;
  - the palette scopes keep the persistent `Stores`.
- Modify: `engine/AboutScheme.java` and `engine/AboutQueries.java` (plan 5's channel). The asker travels with each
  query:
  - `registerQueries(String page, BiFunction<Integer, String, CompletableFuture<String>> answer)` and
    `query(String frameAddress, boolean mainFrame, int browser, String request)`, where `browser` is the asking
    browser's `CefBrowser.getIdentifier()`;
  - plan 5's history, bookmarks and welcome queries answer from that browser's store;
  - a browser with no store gets a failed query.
- Modify: 4b's `CertificateAllowance` becomes one per store, reached from the page as 4b reaches the single one (spec
  14: "A /dev/null item's allowance ends with its store").
- Modify: `BrowserPlugin.java`.
  - `DataStores` is made at start and opens nothing.
  - `stop()` closes it before `chromium.close()`.
- Test: `store/DataStoresTest.java`, and additions to `item/BrowserItemTest.java` and `engine/ChromiumStateTest.java`.

**Interfaces:**
- **`final class DataStore`** (UI thread unless noted):
  - `boolean devNull()`, `PageContext context()`, `Stores stores()` and `CertificateAllowance allowance()`;
  - `CompletableFuture<Void> ready()`: complete at once here; Task 4 makes it wait until the store's permissions have
    loaded;
  - `void retain()` and `void release()`, which count its open items;
  - `Subscription onClosed(Runnable)` and `boolean closed()`.
  - **Closing:** a /dev/null store closes when released to zero:
    - its `Stores` close;
    - its allowances go;
    - its context closes.

    The persistent store closes only with `DataStores.close()`.
- **`final class DataStores implements AutoCloseable`:**
  - `DataStore persistent()` wraps the plugin's `Stores`, which still open on first use;
  - `DataStore newDevNull()`;
  - `List<DataStore> live()`: the persistent store once opened, and every open /dev/null store;
  - `Optional<DataStore> forBrowser(int browser)` (any thread). It is filled as each page's browser is made
    (`Page.browserId()`, an `OptionalInt`) and emptied as the browser closes. Plan 5's channel uses it;
  - `close()`.
  - A package-private constructor takes a context factory, for tests.
- **`final class PageContext`** (page package; its public methods name no Chromium type):
  - `static PageContext persistent()` and `static PageContext inMemory()`, plus `boolean inMemory()`;
  - package-private `CompletableFuture<CefRequestContext> context(Chromium)`:
    - persistent completes at once with `null`, as 4a passes;
    - in-memory asks `Chromium.inMemoryContext()` once;
  - `void close()`: an in-memory context is released once; nothing happens for the persistent one.
- **`Chromium`:**
  - **`CompletableFuture<Void> onUiThread(Runnable task)`** runs `task` on Chromium's UI thread:
    - before `onContextInitialized`, inside it;
    - afterwards, in the callback of `CefCookieManager.getGlobalManager().flushStore`.

    It fails once Chromium has failed or stopped.
  - **`CompletableFuture<CefRequestContext> inMemoryContext()`** makes `CefRequestContext.createContext(null)` on that
    thread and keeps track of it.
  - **`void release(CefRequestContext)`** disposes it, once, while Chromium runs. After Chromium has stopped, it does
    nothing.
- **`Page(URI url, Runnable changed, PageContext context)`.** `start` makes the browser once both the client and the
  context are ready.
- **`BrowserItems.open(URI, TabPlacement, Optional<String> target, Optional<WindowOwner> window, DataStore store)`.**
  The four-argument form uses the persistent store; Task 3 makes it use `forNewItem()`.
- **`BrowserItem.store()`.** An item retains its store as it opens and releases it as it closes, or when its tab fails
  to open. `openItem` opens links and popups in its own store. An item asks for its page, on its first show, only
  once its store is `ready()`, so the first load start already reads its permissions.

- [ ] **Step 1: Write the failing tests:**
  - **`DataStoresTest`**, with the recording context factory:
    - two `newDevNull()` calls give two stores;
    - released to zero, a /dev/null store closes its context once and its `Stores`;
    - the persistent store survives release to zero;
    - `close()` closes every store;
    - a store that closes after Chromium stopped calls no release.
  - **`BrowserItemTest`:**
    - a link opened from an item, through `ItemTestAccess`, shares its store;
    - two items opened separately in /dev/null have different stores;
    - closing the opener keeps the popup's store open until the popup closes.
  - **Isolation:**
    - a visit in a /dev/null item (`PageTestAccess.loadStarted`) records history in its in-memory `Stores` only;
    - the persistent database has no row, and no `browser.sqlite` appears under the `@TempDir` data directory;
    - the history page's `list` query from a /dev/null item's browser lists its visits, and from a persistent item's
      browser lists none of them;
    - an allowance added in a /dev/null store is absent from the persistent store, and gone once the store closes.
  - **`ChromiumStateTest`:** `onUiThread` before any start queues and starts nothing, and after `close()` it fails.

- [ ] **Step 2: Run** `./gradlew :jasper-plugin-browser:test`. Expected: FAIL.

- [ ] **Step 3: Implement.**

- [ ] **Step 4: Run** `./gradlew :jasper-plugin-browser:check`. Expected: PASS, with `StartLoadsNoChromiumTest`
  among the tests.

- [ ] **Step 5: Commit** "Browser: one data store per browsing group".

### Task 3: /dev/null mode

**Files:**
- Modify: `settings/BrowserSettings.java` and `settings.toml`: `devnull_mode`, seeded commented out as 4a's keys are.
- Modify: `store/DataStores.java` (`forNewItem`) and `item/BrowserItems.java` (new items and `restore`).
- Create: `item/DevNullPopover.java`.
- Modify: `item/BrowserToolbar.java` (:40-50): the indicator, and the ☰ bookmark group left out.
- Modify: `restore/BrowserRestore.java` (:20, :48) and `item/BrowserItem.java`.
- Test: `BrowserSettingsTest`, `BrowserToolbarTest`, `DevNullPopoverTest`, `BrowserRestoreTest` and `BrowserItemTest`.

**Interfaces:**
- **The setting `devnull_mode`:**
  - boolean, `false`, `Applies.LIVE`, section "Privacy & Data";
  - label "Use /dev/null mode (no history, cookies, or other data saved)";
  - description "Browser tabs, windows and panes opened after you turn this on keep cookies, history and all other
    browser data in memory, and lose them when the last of them closes."
- **`DataStore DataStores.forNewItem()`** is `newDevNull()` while the setting is on, and `persistent()` otherwise.
  - **Uses it:** New Browser Tab, New Browser Window, the split item, `BrowserRole`'s links, the terminal's Open URL in
    Split items and plan 5's palette rows.
  - **Does not:**
    - a page's links and popups (the opener's store);
    - a link to an OSC 8 target (that item);
    - a restore (the saved mode).
  - Turning the setting on or off changes no open item.
- **`boolean BrowserItem.devNull()`** is `store().devNull()`. Plans 7 and 8 read it.
- **The indicator:** `Nodes.Button("browser.devnull", READY, "", "hidden", GHOST, "browser.devnull", "/dev/null Mode")`.
  It goes between the address field and ☰, in /dev/null items only.
- **`DevNullPopover.show(PluginContext context, UiView toolbar)`** opens `windows().popover(toolbar.anchor(
  "browser.devnull"))`.
  - **Its content:** a VERTICAL `Stack` of `Text("/dev/null Mode", BODY_STRONG)` and `Text("Your browsing activity is
    not being saved. No history, bookmarks, or other data will be stored. This mode is set in Settings › Browser ›
    Privacy & Data.", BODY)`.
  - **Closing:** Escape, an outside click, or a second click on the indicator.
- **The ☰ menu:** a /dev/null item leaves out plan 5's bookmark group (`browser.bookmark.add` or `.remove`,
  `browser.bookmarks` and `browser.history`). Web › History stays.
- **Onboarding:** an `OnboardingPages.FEATURES` row, "/dev/null mode": "Browse without keeping history, cookies or
  other data: turn it on in Settings › Browser › Privacy & Data." *(plan's copy)*.
- **Restore:**
  - **`BrowserRestore.state(URI url, Optional<String> target, <4b's zoom>, boolean devNull)`:** a /dev/null item saves
    `devnull = "true"` and no `url`. The target and zoom are saved as for any item.
  - **`static boolean BrowserRestore.devNull(RestoreState)`** is true only for the exact value `"true"`.
  - **`BrowserItems.restore`:** a /dev/null state opens in `newDevNull()` at the start page. Any other state opens in
    the persistent store, whatever the setting.

- [ ] **Step 1: Write the failing tests:**
  - **The toolbar:** the indicator shows in /dev/null items only, between the field and ☰, and their ☰ has no
    bookmark group.
  - **`DevNullPopoverTest`** (FakeUi): its two texts, opening on the indicator's action, and closing on Escape.
  - **`BrowserRestoreTest`:**
    - a /dev/null item at a short URL saves no `url`, but keeps its target and zoom;
    - `"TRUE"` and `"1"` are not /dev/null;
    - a saved /dev/null item comes back in a new /dev/null store at the start page;
    - with the setting on, a saved ordinary item comes back persistent.
  - **`BrowserItemTest`:**
    - with the setting on, New Browser Tab opens in /dev/null;
    - after the setting goes off, the open /dev/null item stays /dev/null and a new item is persistent.

- [ ] **Step 2: Run** the tests. Expected: FAIL.

- [ ] **Step 3: Implement.**

- [ ] **Step 4: Run** `./gradlew :jasper-plugin-browser:check`. Expected: PASS.

- [ ] **Step 5: Commit** "Browser: /dev/null mode".

### Task 4: Site permissions, their page and the ☰ resets

**Files:**
- Create, under `plugins/browser/src/main/java/dev/jasper/browser/permissions/`: `Permission.java`, `Decision.java`,
  `Grant.java`, `PermissionRequest.java`, `AudioMode.java`, `Origins.java`, `PermissionStore.java` and
  `package-info.java`.
- Create: `pages/PermissionsPage.java`, on plan 5's `AboutPages`, with the resources `pages/permissions.html` and
  `permissions.js`.
- Create: `item/PrivacyMenu.java`.
- Modify:
  - `store/DataStore.java` (`permissions()`; `ready()` waits for them to load);
  - `item/BrowserItems.java` (`reloadShowing`);
  - `item/BrowserToolbar.java` (the ☰ composition);
  - `BrowserPlugin.java` (registers `jasper-about:permissions` and its queries);
  - plan 5's `WelcomePage` (Permissions joins the Quick Actions) and `OnboardingPages.FEATURES`.
- Test: `OriginsTest`, `PermissionStoreTest`, `PermissionsPageTest`, `AboutSchemeTest`, `PrivacyMenuTest`,
  `WelcomePageTest` and `BrowserItemTest`.

**Interfaces:**
- **`enum Permission`:**

  | Value | `token()` | `label()` | `heading()` | `asks(origin)` |
  |---|---|---|---|---|
  | `CAMERA` | `camera` | Camera | Allow Camera | The website ORIGIN wants to use your camera. |
  | `MICROPHONE` | `microphone` | Microphone | Allow Microphone | The website ORIGIN wants to use your microphone. |
  | `CAMERA_AND_MICROPHONE` | `camera-microphone` | Camera and Microphone | Allow Camera and Microphone | The website ORIGIN wants to use your camera and microphone. |
  | `NOTIFICATIONS` | `notifications` | Notifications | Allow Notifications | The website ORIGIN wants to send you notifications. |
  | `AUDIO_PLAYBACK` | `audio` | Audio Playback | — | — (Task 7's bar asks) |

- **`enum Decision`:** `ALLOWED` (`allowed`, "Allowed") and `BLOCKED` (`blocked`, "Blocked").
- **Records:** `Grant(String origin, Permission permission, Decision decision, Instant created, Instant updated)` and
  `PermissionRequest(String origin, Permission permission)`.
- **`enum AudioMode { ALLOW, BLOCK, ASK }`.**
- **`static Optional<String> Origins.of(String address)`:**
  - http and https give `scheme://host[:port]`: the host lowercased, IPv6 in brackets, no default port and no user
    info;
  - `file:` gives `file://`;
  - anything else gives empty.
- **`PermissionStore`**, over a store's `BrowserDatabase`, through `call`:
  - **The table:** `CREATE TABLE IF NOT EXISTS permissions(origin TEXT NOT NULL, type TEXT NOT NULL, decision TEXT
    NOT NULL, created INTEGER NOT NULL, updated INTEGER NOT NULL, PRIMARY KEY(origin, type))`.
  - **Loading:** `CompletableFuture<Void> load()` reads every row into a concurrent cache.
  - **Reading** (any thread, from the cache):
    - `Optional<Decision> decision(String origin, Permission p)`;
    - `AudioMode audioMode(String origin)`;
    - `List<Grant> all()`, by origin, then in `Permission`'s order;
    - `List<Grant> of(String origin)`.
  - **Writing:** the cache changes at once, and the row is written on the database's executor.
    - `CompletableFuture<Void> set(String origin, Permission p, Decision d)` keeps `created` and moves `updated`.
    - `Set<String> revoke(String origin, Permission p)`, `Set<String> clearSite(String origin)` and
      `Set<String> clearAll()` return the origins affected.
    - **A failed write** is logged and posts one error, "Site permissions could not be saved". The cache keeps the
      new value.
  - `Subscription onChanged(Runnable)`, told on the UI thread.
- **`jasper-about:permissions`**, "Site Permissions": `AboutPages.render("permissions", Map.of())`, with plan 5's CSP
  and `about.js`.
  - **Controls:**
    - Clear All Permissions;
    - a search field, "Search by website or permission type…", matching the origin or the label, ignoring case;
    - a type filter: All Types, then each label;
    - a status filter: All Statuses, Allowed, Blocked.
  - **Rows:** grouped by origin, each group with **Clear**, then each permission's label, its status and ×.
    - **×** asks `confirm("Revoke " + label + " permission for " + origin + "?")`.
    - **A list with no rows** reads "No permissions found".
  - **Data:** it comes from the queries below, never written into the HTML. Rows are built with `jasper.el`.
- **`CompletableFuture<String> PermissionsPage.answer(DataStore store, String request)`**, registered with
  `registerQueries("permissions", …)`, which Task 2 gives the asker's store. Plan 5's channel already refuses every
  asker but the page's main frame. Requests are flat (`Json.object`), and a malformed one fails the future:
  - **`{"op":"list"}`** answers `{"grants":[{origin, type, label, decision}]}`;
  - **`{"op":"revoke","origin","type"}`**, **`{"op":"clear-site","origin"}`** and **`{"op":"clear-all"}`** change that
    store, then reload its items that show an affected origin (`reloadShowing`, posted to the UI thread).
- **The welcome page's Quick Actions** gain Permissions (`jasper-about:permissions`), after History.
- **Onboarding:** a `FEATURES` row, "Site permissions": "Allow or block each site's camera, microphone, notifications
  and sound, and review them on one page." *(plan's copy)*.
- **`void BrowserItems.reloadShowing(DataStore store, Set<String> origins)`.**
- **`static List<MenuItem> PrivacyMenu.items(List<Grant> site, boolean muted)`:**
  - a separator;
  - one item per grant, with id and action `browser.permission.reset.<token>` and the label "Reset " + label +
    " Permission (" + decision label + ")";
  - a separator.

  Task 7 adds Unmute. Choosing a reset revokes that grant and reloads the items that show its origin.
- **The ☰ composition**, in 4b's ☰ structure in `BrowserToolbar`:
  1. plan 7's reader group;
  2. plan 5's bookmark group, except in /dev/null;
  3. `PrivacyMenu`;
  4. Settings.

  No separator leads, and no two separators follow each other.

- [ ] **Step 1: Write the failing tests:**
  - **`OriginsTest`:**
    - `https://Example.com:443/a` gives `https://example.com`;
    - `http://a.test:8080/` gives `http://a.test:8080`;
    - `http://[::1]:3000/` gives `http://[::1]:3000`;
    - `https://u:p@h.test/` gives `https://h.test`;
    - `file:///x` gives `file://`;
    - `jasper-about:history` and `data:,x` give empty.
  - **`PermissionStoreTest`:**
    - `set` then `decision`, and a second `set` that keeps `created`;
    - the origins each removal returns;
    - the rows reread after reopening a file database in `@TempDir`;
    - the table added to an existing plan 5 database;
    - a write to a closed database posts the error and keeps the cache.
  - **`PermissionsPageTest`** (in-memory stores):
    - every string above, and no stored data in the HTML;
    - `list` answers the asking store's grants only, and an origin holding `</script>` reads back exactly;
    - `revoke`, `clear-site` and `clear-all` change only that store and reload the items showing those origins;
    - malformed JSON, an unknown op and a missing origin each fail the future.
  - **`AboutSchemeTest`:** the permissions queries refuse `https://evil.example/`, a sub-frame of the page, and
    65,537 characters.
  - **`WelcomePageTest`:** the four Quick Actions.
  - **`PrivacyMenuTest`:** the order, the labels, and separators collapsing when there is no grant.

- [ ] **Step 2: Run** the tests. Expected: FAIL.

- [ ] **Step 3: Implement.**

- [ ] **Step 4: Run** `./gradlew :jasper-plugin-browser:check`. Expected: PASS.

- [ ] **Step 5: Commit** "Browser: site permissions, their page and the menu's resets".

### Task 5: Camera and microphone

**Files:**
- Create: `page/MediaAccess.java`, the client's one `CefPermissionHandler`, installed through `Chromium.onClient`.
- Create: `dialogs/PageModal.java`, holding `ScriptDialog`'s showing:
  - it waits until the item is active;
  - the page's close and a dialogs reset dismiss it;
  - it is answered once.

  Modify `dialogs/ScriptDialog.java` to use it.
- Create: `dialogs/PermissionPrompt.java` and `item/SitePermissions.java`.
- Create: `page/PagePrivacy.java` (`Page.privacy()`), the page's privacy hooks in plain types, which Tasks 6 and 7
  add to.
- Modify: `page/Page.java` and `item/BrowserItem.java`.
- Modify: `gradle/packaging.gradle`, beside `NSLocalNetworkUsageDescription` (:34, :324-325, and `verifyPackage`'s
  check at :680-681), and `docs/packaging.md`.
- Test: `MediaAccessTest`, `PermissionPromptTest`, `SitePermissionsTest` and `PageDialogsTest` (unchanged, still
  passing).

**Interfaces:**
- **`static Optional<Permission> MediaAccess.permission(int requested)`:**
  - video gives `CAMERA`;
  - audio gives `MICROPHONE`;
  - both give `CAMERA_AND_MICROPHONE`;
  - any `DESKTOP_*` flag, or none, gives empty.
- **`onRequestMediaAccessPermission`:**
  - **Not a page's browser:** returns false.
  - **No origin from `Origins.of`, or no permission:** `Cancel()`, with nothing asked or stored.
  - **Otherwise:**
    - `page.privacy().request(new PermissionRequest(origin, p), allowed -> …)`;
    - an allowed answer calls `Continue(requested)`, and any other calls `Cancel()`;
    - it returns true.
- **`PagePrivacy`:** `onPermissionRequest(BiConsumer<PermissionRequest, Consumer<Boolean>>)`, and `request(...)`
  (any thread). With no handler, or a closed page, the answer is false.
- **`SitePermissions`**, one per item, answers from its store:
  - a stored decision answers at once;
  - otherwise `PermissionPrompt` asks:
    - Allow stores `ALLOWED` and answers true;
    - Block stores `BLOCKED` and answers false;
    - a dismissed prompt answers false and stores nothing.
- **`PermissionPrompt.ask(DialogPlace place, Page page, PermissionRequest request, Consumer<Optional<Decision>>
  answer)`:**
  - the heading `p.heading()`, the text `p.asks(origin)`, and **Block** (SECONDARY) and **Allow** (PRIMARY,
    right-most);
  - Escape dismisses it;
  - a page shows one prompt at a time. An equal request joins the one showing, and others wait in order;
  - the page's close, a dialogs reset and a main-frame load start dismiss the shown and waiting prompts.
- **Info.plist:**
  - `NSCameraUsageDescription` = "Web pages in Jasper's browser use your camera only when you allow it.";
  - `NSMicrophoneUsageDescription` = "Web pages in Jasper's browser use your microphone only when you allow it."

  Without these, macOS ends a process that opens the camera.

- [ ] **Step 1: Write the failing tests:**
  - **`MediaAccessTest`:** the flag table, including `DESKTOP_VIDEO | DEVICE_AUDIO`, which gives empty.
  - **`PermissionPromptTest`** (FakeUi):
    - each prompt's heading and text;
    - Allow, Block and Escape;
    - waiting while the item isn't active;
    - an item that closes while its prompt waits is answered once;
    - a main-frame load start dismisses the shown prompt and the waiting ones, each answered once;
    - equal requests share one prompt, and a different one comes after.
  - **`SitePermissionsTest`:**
    - stored `ALLOWED` answers without a prompt;
    - Allow stores;
    - a dismissal stores nothing;
    - a /dev/null item's answer is absent from the persistent store.

- [ ] **Step 2: Run** the tests. Expected: FAIL.

- [ ] **Step 3: Implement.**

- [ ] **Step 4: Run** `./gradlew :jasper-plugin-browser:check`, then on macOS `./gradlew :jasper-app:packageApp
  :jasper-app:verifyPackage` without `-Pjasper.aot`, which stays headless. Expected: PASS.

- [ ] **Step 5: Commit** "Browser: camera and microphone permissions".

### Task 6: Page notifications

**Files:**
- Create: `page/FrameScripts.java`, which injects at load start, and `page/ChallengeFrames.java`.
- Create: the resource `dev/jasper/browser/page/notifications.js`.
- Modify: `page/PageEvents.java` (:141-142): sub-frame load-start hooks beside the main-frame ones.
- Create: `links/PrivacyQueries.java`, the client's privacy router: `jasperPrivacyQuery` and `jasperPrivacyCancel`,
  installed through `Chromium.onClient` as plan 5's `AboutQueries` is.
- Modify: `page/PagePrivacy.java` and `item/BrowserItem.java`.
- Create: `item/PageNotifications.java`.
- Modify: `BrowserPlugin.java`: the group.
- Test: `ChallengeFramesTest`, `FrameScriptsTest`, `PageNotificationsTest` and `PrivacyQueriesTest`.

**Interfaces:**
- **`static boolean ChallengeFrames.is(String address)`** is true for:
  - `challenges.cloudflare.com`;
  - `hcaptcha.com` and its subdomains;
  - `www.google.com` and `www.recaptcha.net` paths under `/recaptcha/`;
  - subdomains of `arkoselabs.com` and `funcaptcha.com`.
- **`FrameScripts`, at a main-frame load start,** injects `notifications.js` with the new document's state for
  `Origins.of(url)`:
  - `granted` when `ALLOWED`;
  - `denied` when `BLOCKED`, or when there is no origin;
  - otherwise `default`.

  `static String FrameScripts.notifications(String state)` builds the script text. `FrameScripts.prepare(Chromium)`
  hooks the client's main-frame and sub-frame load starts once, when a page first starts, as 4a's `PageEvents.prepare`
  does.
- **`notifications.js`**, top frame only:
  - it keeps the router function it found when injected;
  - it replaces `window.Notification` with:
    - `permission`;
    - `requestPermission(callback?)`, which returns a promise and calls the callback;
    - the constructor `(title, {body})`;
    - `close()`;
    - `onshow`, `onclose` and `onerror`, plus `addEventListener` and `removeEventListener`;
  - without `granted`, the constructor fires `error` and shows nothing;
  - `onclick` never fires, as in iTerm2;
  - icons are ignored.
- **The router's requests** are untrusted (R38):
  - each is a flat `Json.object` of at most 65,536 characters, `{"op", …}`, and persistent queries are refused;
  - an unknown op or member fails with code 1;
  - a refusal fails with code 2;
  - the router never throws.
- **The ops** (top frame only):
  - **`notification.request`:** stored decisions answer at once. Otherwise `PagePrivacy.request` asks for
    `NOTIFICATIONS`. The answer is `granted` or `denied`, or `default` when the prompt was dismissed.
  - **`notification.show {id, title, body}`:** shown only when the asking item's store holds `ALLOWED` for the page's
    origin; otherwise `REFUSED`.
  - **`notification.close {id}`.**
- **`PageNotifications(NotificationGroup group, LongSupplier clock)`**, with the group
  `GroupSpec("dev.jasper.browser.pages", "Page notifications", DisplayMode.CARD)`:
  - **`boolean post(Page page, WindowHandle window, String origin, String id, String title, String body)`** posts
    `INFO` with no icon, to `window`:
    - the title trimmed and cut to 200 characters, or the origin's host when it is blank;
    - the body cut to 4,000 characters;
    - at most 10 per page in 60 s. Beyond that it returns false, and the script fires `error`.
  - **`void close(Page page, String id)`** calls `handle.remove()` (Task 1).
  - **`void forget(Page page)`**, at each main-frame load start and as the page closes, drops the page's handles. What
    it posted stays.

- [ ] **Step 1: Write the failing tests:**
  - **`ChallengeFramesTest`:** each host above, plus `https://www.google.com/search` and `https://example.com`, which
    are not challenge frames.
  - **`FrameScriptsTest`:** the state is quoted into the script, and a state that is none of the three becomes
    `denied`.
  - **`PageNotificationsTest`** (FakeNotifications):
    - the title and body;
    - a blank title giving the host;
    - cutting;
    - the 11th post within 60 s refused, and allowed again after 60 s;
    - `close` makes `Post.removed()` true;
    - `close` after `forget` does nothing.
  - **`PrivacyQueriesTest`**, with a recording page seam, as `OpenerBridgeTest` has:
    - `notification.show` without a stored Allow is refused;
    - so is any notification op from a sub-frame;
    - malformed requests, a persistent query and one of 65,537 characters fail and never throw;
    - `request` with `BLOCKED` answers `denied` without a prompt;
    - a dismissed prompt answers `default`.

- [ ] **Step 2: Run** the tests. Expected: FAIL.

- [ ] **Step 3: Implement.**

- [ ] **Step 4: Run** `./gradlew :jasper-plugin-browser:check`. Expected: PASS.

- [ ] **Step 5: Commit** "Browser: page notifications as Jasper notifications".

### Task 7: Audio muting

**Files:**
- Create: the resource `dev/jasper/browser/page/audio.js`.
- Modify:
  - `page/FrameScripts.java`: `audio.js` in every frame except challenge frames;
  - `page/PagePrivacy.java`;
  - `links/PrivacyQueries.java`: `audio.playing`, from any frame but a challenge frame;
  - `item/PrivacyMenu.java`.
- Create: `item/AudioGate.java` and `item/AudioBar.java`.
- Modify: `item/BrowserItem.java` (`spec()`, :320-323): the bar goes between the toolbar and the page, with its
  actions.
- Test: `AudioGateTest`, `AudioBarTest`, `PrivacyMenuTest` and `FrameScriptsTest`.

**Interfaces:**
- **Which mode a frame gets:**
  - **A main-frame load start** injects the mode `PermissionStore.audioMode(origin)` of the new page's top-level
    origin. `PagePrivacy` keeps it as the document's current mode (a volatile).
  - **A sub-frame** gets the current mode.
- **`audio.js`**, under `Symbol.for("dev.jasper.browser.audio")`, has `setMuted(boolean)`:
  - **What it watches:** it patches `HTMLMediaElement.prototype.play`, listens for `play` and `playing` in the capture
    phase, and tracks every `AudioContext` and `webkitAudioContext` it constructs.
  - **Muting:**
    - every media element is muted, and the page's own `muted` values are kept to put back;
    - contexts are suspended, and their `resume()` waits until unmuted;
    - unmuting resumes what it suspended.
  - **The first audible playback** of the document reports `audio.playing`, once. Audible means an element not muted,
    with a volume above 0, or a context running.
    - **In `ASK`,** the frame mutes itself first.
    - **In `BLOCK`,** the frame starts muted.
- **`PagePrivacy`:**
  - `onAudioPlaying(Runnable)`;
  - `void mute(boolean)`, which runs `setMuted` in every frame (`getFrameIdentifiers()`) and sets the current mode.
- **`AudioGate`**, one per item and pure:
  - **`void document(String origin, AudioMode stored)`**, at each main-frame load start, resets the Once answers.
  - **`Effect playing()`:**
    - `SHOW_BAR` the first time in `ASK`;
    - `MUTED` in `BLOCK`;
    - `NONE` otherwise, and on later calls.
  - **`Optional<Decision> answer(AudioAnswer a)`** returns the decision to store, with
    `enum AudioAnswer { ALLOW_ONCE, ALLOW_ALWAYS, DENY_ONCE, DENY_ALWAYS }`.
    - **Allow** answers unmute and make the document's mode `ALLOW`.
    - **Deny** answers keep the page muted and make it `BLOCK`.
    - **The Always answers** return `ALLOWED` or `BLOCKED` for `AUDIO_PLAYBACK`.
  - **`void unmute()`.**
  - **State:** `boolean muted()` (playback was muted), `boolean barShown()`, `String origin()` and
    `AudioMode mode()`.
- **`AudioBar.node(String origin, boolean shown)`** is
  `Stack("browser.audio", visible(shown), INLINE, [...])`, holding:
  - the text "Audio was muted. Allow playback by ORIGIN?";
  - SECONDARY buttons **Allow Once**, **Allow Always**, **Deny Once** and **Deny Always**, with the actions
    `browser.audio.allow-once`, `.allow-always`, `.deny-once` and `.deny-always`.

  It is built as the Editor's on-disk banner is (`plugins/editor/.../ui/EditorTab.java:383-386` on `main`).
- **The ☰ menu:** **Unmute Current Page** (`browser.unmute`), then a separator, while `muted()`. It acts as Allow
  Once.

- [ ] **Step 1: Write the failing tests:**
  - **`AudioGateTest`**, the full table:
    - the first play in `ASK` shows the bar and mutes, and a second play shows nothing new;
    - each of the four answers;
    - `BLOCK` mutes with no bar;
    - `ALLOW` does nothing;
    - a new document drops the Once answers and hides the bar;
    - Unmute makes the mode `ALLOW`.
  - **`AudioBarTest`:** the text, ids, order and visibility.
  - **`PrivacyQueriesTest`:** `audio.playing` from a challenge frame is refused, and from any other frame reaches the
    item once per document.
  - **`PrivacyMenuTest`:** Unmute appears while muted only.
  - **`FrameScriptsTest`:**
    - a challenge sub-frame gets no audio script;
    - the main frame's mode comes from the store;
    - a sub-frame's mode is the current one.

- [ ] **Step 2: Run** the tests. Expected: FAIL.

- [ ] **Step 3: Implement.**

- [ ] **Step 4: Run** `./gradlew :jasper-plugin-browser:check`. Expected: PASS.

- [ ] **Step 5: Commit** "Browser: audio muting, its bar and Unmute Current Page".

### Task 8: Settings › Browser's own page, and clearing data

**Files:**
- Create: `settings/BrowserSettingsPanel.java`.
- Modify: `settings/BrowserSettings.java`. Declarations go through a helper that records each section, and
  `Declared` gains `sections()`.
- Create: `engine/ProfileClearing.java` and `engine/ProfileWipe.java`.
- Modify:
  - `engine/Chromium.java`: `clearCookies` and `clearWebsiteData`;
  - `engine/ChromiumApp.java`: `ProfileWipe.runIfMarked` before `CefApp.getInstance` (:41), and pending cookie clearing
    in `onContextInitialized`.
- Create: `store/WebsiteData.java`. Modify: plan 5's `store/HistoryStore.java` (`origins()`).
- Modify: `BrowserPlugin.java`:
  - `context.settings().page(...)`;
  - its prompts show as Indexer's do (`plugins/indexer/.../IndexerPlugin.java:184-209`).
- Test: `BrowserSettingsPanelTest` (as `EditorSettingsPanelTest` drives its panel), `BrowserSettingsTest`,
  `WebsiteDataTest`, `ProfileWipeTest` and `ChromiumStateTest`.

**Interfaces:**
- **`record Section(String title, List<Setting<?>> settings)`** and **`List<Section> Declared.sections()`.** The
  sections run General, Link Opening, Search Engine, Privacy & Data, Ad Blocking, Proxy. Empty ones are left out.
- **`BrowserSettingsPanel(UiKit kit, BrowserSettings.Declared settings, Actions actions)`**, with
  `record Actions(Supplier<CompletableFuture<Void>> clearCookies, Supplier<CompletableFuture<Void>> clearWebsiteData,
  Runnable managePermissions, Consumer<Prompt> show)` and
  `record Prompt(String title, String message, boolean question, Runnable accepted)`.
  - **The page:** every declared setting as `host.row`, by section.
  - **Privacy & Data** then adds these rows, with iTerm2's labels:
    - "Clear All Cookies", with the button **Clear Cookies**. It asks "Clear Cookies" / "This will remove all cookies
      from all websites. Continue?" (OK and Cancel), then clears.
    - "Clear Website Data", with the button **Clear All Data**. It asks "Clear Website Data" / "This will remove all
      browsing data including cookies, cache, and local storage. Continue?". Once the clearing has completed, it
      tells "All website data has been cleared successfully!".
    - "Manage Site Permissions", with the description "View and revoke permissions granted to websites (camera,
      microphone, notifications, audio playback)." and the button **Manage Permissions**. It opens
      `jasper-about:permissions` in a new tab of the active window, in the persistent store *(call)*.
  - **`keywords()`:** cookies, website data, cache, permissions, privacy, /dev/null, ad blocking, filter, proxy.
- **`WebsiteData`** (cef-free):
  - **`CompletableFuture<Void> clearCookies()`** calls `chromium.clearCookies()`.
  - **`CompletableFuture<Void> clearAll()`:**
    1. it gathers the origins first, through `Origins.of`, from:
       - `HistoryStore.origins()`, which this plan adds: the distinct origins of the `visits` rows' URLs;
       - `BookmarkStore.all()`;
       - `PermissionStore.all()`;
       - the persistent store's open items;
    2. then `chromium.clearWebsiteData(origins)`;
    3. then the persistent store's `history().clear()`, `bookmarks().clear()` and `permissions().clearAll()`;
    4. then `VACUUM`, through `BrowserDatabase.call`.

    /dev/null stores are untouched. A failure posts "Website data could not be cleared" with its cause, and no success.
- **`Chromium.clearCookies()`:**
  - **While running:** `deleteCookies("", "")`, then `flushStore`, which completes it.
  - **Otherwise:** the marker `cookies`, applied in `onContextInitialized` before any page.
- **`Chromium.clearWebsiteData(Set<String> origins)`:**
  - **Always:** the marker `all`.
  - **While running, also live:**
    1. the cookies' domains are read with `visitAllCookies`, as https and http origins;
    2. the cookies are deleted;
    3. a helper browser runs DevTools: `Network.clearBrowserCache`, then `Storage.clearDataForOrigin {origin,
       storageTypes: "all"}` for each origin.

    The helper is a `CefBrowserOsrWithHandler` at `about:blank` in the global context, with a 1×1 render handler and
    `doClose()` false, made with `createImmediately()` and closed after.
  - **Before any start:** the wipe also runs now, on the Chromium worker, which runs the start afterwards.
- **`ProfileWipe`:**
  - the marker is `<data>/chromium-clear`, holding `cookies` or `all`, and `all` wins;
  - `static void runIfMarked(Path dataDir)`: with `all`, it deletes `<data>/chromium` and the marker. A `cookies`
    marker waits for `onContextInitialized`, which removes it.

- [ ] **Step 1: Write the failing tests:**
  - **`BrowserSettingsPanelTest`:**
    - the declared rows by section, then the three rows;
    - each prompt's exact texts;
    - Cancel runs nothing;
    - the success text comes only after the future completes, and never on failure;
    - Manage Permissions runs its action.
  - **`WebsiteDataTest`** (in-memory `Stores`):
    - `HistoryStore.origins()` gives each origin once;
    - the origins are gathered before the erase;
    - only the persistent store is emptied;
    - `VACUUM` runs;
    - Chromium is cleared before the database.
  - **`ProfileWipeTest`:**
    - `all` deletes the folder and the marker;
    - `cookies` keeps both;
    - no marker does nothing;
    - `all` replaces `cookies`.
  - **`ChromiumStateTest`:** `clearCookies` and `clearWebsiteData`, before any start and after a failed one, write the
    marker and start no Chromium.

- [ ] **Step 2: Run** the tests. Expected: FAIL.

- [ ] **Step 3: Implement.**

- [ ] **Step 4: Run** `./gradlew :jasper-plugin-browser:check`. Expected: PASS.

- [ ] **Step 5: Commit** "Browser: Settings › Browser's own page, Clear Cookies and Clear Website Data".

### Task 9: The content-blocker compiler

**Files:**
- Create, under `plugins/browser/src/main/java/dev/jasper/browser/adblock/`: `BlockerJson.java`, `ContentRules.java`,
  `Rule.java`, `ResourceKind.java`, `UrlFilter.java`, `BaseDomain.java`, `RuleSet.java` and `package-info.java`.
- Test: `BlockerJsonTest`, `ContentRulesTest`, `UrlFilterTest`, `BaseDomainTest`, `RuleSetTest` and
  `RuleSetIndexTest`, with fixtures under `src/test/resources/adblock/`.

**Interfaces:**
- **`BlockerJson`:**
  - `static List<Map<String, Object>> read(String text)` reads an array of objects whose values are strings,
    booleans, numbers, arrays and objects. It allows a depth of 8 and at most 32 MiB of text. Anything else throws
    `IllegalArgumentException("Not a content blocker list: …")`.
  - `static String write(List<Map<String, Object>>)`.
  - Plan 5's `json.Json` reads only flat objects. If 4b's DevTools JSON reader is general, `BlockerJson` adds these
    limits to it; otherwise `BlockerJson` is its own reader. Record which in the banner.
- **`ContentRules`:** `static Compiled compile(List<Map<String, Object>>)`, with
  `record Compiled(List<Rule> rules, int skipped)`, and `static List<Map<String, Object>> encode(List<Rule>)`.
  - **Supported:**
    - the trigger keys `url-filter` (required), `url-filter-is-case-sensitive`, `resource-type`, `load-type`,
      `if-domain` and `unless-domain`;
    - the actions `block`, `css-display-none` (with `selector`) and `ignore-previous-rules`.
  - **Skipped and counted:**
    - any other trigger key, such as `if-top-url`, `unless-top-url`, `load-context` or `if-frame-url`;
    - any other action;
    - a rule with both `if-domain` and `unless-domain`;
    - a `url-filter` that does not compile, or is longer than 2,048 characters;
    - a quantified group that holds a quantifier (`(a+)+`), which Java would backtrack on;
    - rules past the first 250,000.
- **`record Rule(int index, UrlFilter filter, Set<ResourceKind> types, LoadType load, List<String> ifDomain,
  List<String> unlessDomain, Action action, String selector)`**, with `enum LoadType { ANY, FIRST_PARTY, THIRD_PARTY }`
  and `enum Action { BLOCK, HIDE, IGNORE_PREVIOUS }`. An empty `types` means every type.
- **`enum ResourceKind`** uses WebKit's names: `document`, `image`, `style-sheet`, `script`, `font`, `raw`,
  `svg-document`, `media`, `popup`, `ping`, `fetch`, `websocket`, `other`.
- **`UrlFilter`:**
  - `static Optional<UrlFilter> of(String source, boolean caseSensitive)`;
  - `Optional<String> literal()`;
  - `boolean matches(String url)`, which compiles the `Pattern` on first use and calls `find()`, case-insensitively
    unless the rule says otherwise.
  - **The literal** is the longest run of top-level literal atoms:
    - an atom is a plain character or an escaped one;
    - an atom followed by `+` ends its run and stays in it;
    - an atom followed by `*` or `?` ends the run and is left out;
    - `.`, a class, a group, `^` and `$` end runs.

    It is lowercased unless the rule is case-sensitive.
- **`static String BaseDomain.of(String host)`:** the last two labels, or the last three when the last two are each
  at most 3 characters (`co.uk`, `com.au`). An IP address is itself. Jasper has no public suffix list *(call)*.
- **`RuleSet`:**
  - `static RuleSet of(List<Rule>)`, `static final RuleSet EMPTY` and `int size()`.
  - **`boolean blocks(String url, Set<ResourceKind> kinds, String pageUrl)`:**
    - the request is blocked when its last matching `BLOCK` rule comes after its last matching `IGNORE_PREVIOUS` rule;
    - a rule matches when its filter matches `url`, its types meet `kinds`, its load type fits, and its domain lists
      fit `pageUrl`'s host;
    - **the load type:** first-party means the same base domain;
    - **the domain lists:** a `*` prefix also covers subdomains.
  - **`String stylesheet(String pageUrl)`** gives one `selector{display:none!important}` line per `HIDE` rule that
    matches `pageUrl`, as a `document` load, and comes after the last such `IGNORE_PREVIOUS`. The rules with filter
    `.*` and no domains are joined once and kept.
  - **The index:** each rule is keyed by the rarest 4-gram of its literal, counted over every rule's literal. Rules
    with no 4-character literal are checked for every URL, and a URL looks up each of its 4-grams.

- [ ] **Step 1: Write the failing tests:**
  - **`UrlFilterTest`:**
    - `^https?://([^/]+\.)?doubleclick\.net` gives `doubleclick.net`;
    - `ads\.js` gives `ads.js`;
    - `ab+c` gives `ab`;
    - `.*` gives none;
    - `(a+)+` is refused.
  - **`ContentRulesTest`:**
    - each field;
    - each skip reason, counted, including a 2,049-character filter;
    - `encode`, then `compile`, gives the same rules.
  - **`BaseDomainTest`:** `a.b.example.com`, `x.co.uk`, `localhost` and `10.0.0.1`.
  - **`RuleSetTest`:**
    - types, load types, both domain lists, and case sensitivity;
    - block then ignore allows, and ignore then block blocks;
    - `stylesheet` with generic and domain rules, and after an ignore.
  - **`RuleSetIndexTest`:** over a fixture of about 60 rules and 150 URLs, plus 2,000 URLs built from the fixtures'
    literals, `blocks` equals a brute-force scan (package-private `RuleSet.scan`) for every pair. A counting seam shows
    that no rule's `matches` runs on a URL without its literal.
  - **`BlockerJsonTest`:** nesting deeper than 8, a truncated array and a non-array are each refused.

- [ ] **Step 2: Run** the tests. Expected: FAIL.

- [ ] **Step 3: Implement.**

- [ ] **Step 4: Run** the tests. Expected: PASS.

- [ ] **Step 5: Commit** "Browser: compile WebKit content-blocker lists".

### Task 10: The filter list, applied

**Files:**
- Create: `adblock/FilterList.java`.
- Create: `links/AdBlockRequests.java`.
- Modify: `links/PageNavigation.java` (:68-72). Requests other than main-frame navigations get `AdBlockRequests` while a
  rule set is active. `PageNavigation.prepare` and `FrameScripts.prepare` take the plugin's `FilterList`, with one
  installer per plugin run (`Chromium.onClient` runs an installer given twice once).
- Modify: `page/FrameScripts.java`, adding the resource `page/hide-elements.js`.
- Modify: `settings/BrowserSettings.java`, `settings.toml` and `settings/BrowserSettingsPanel.java`.
- Modify: `BrowserPlugin.java`:
  - the list starts from the items' starter the first time a page starts, or when the user turns ad blocking on;
  - onboarding-setup gets the Ad Blocker:
    - `new SetupSwitch("ad-blocker", "Ad Blocker", "Block ads with a filter list that updates every day.", …)`, which
      reads and sets `ad_blocking` *(plan's copy for the description)*;
    - a `FEATURES` row, "Ad blocking": "Block ads with a content-blocker filter list, updated every day."
- Test: `FilterListTest` (a `com.sun.net.httpserver` fixture), `AdBlockRequestsTest`, `FrameScriptsTest`,
  `BrowserSettingsPanelTest` and `OnboardingPagesTest`.

**Interfaces:**
- **The settings**, in section "Ad Blocking":
  - `ad_blocking`: boolean, `false`, "Enable Ad Blocking";
  - `filter_list_url`: text, an http or https address ("Enter an http or https address."), defaulting to
    `https://easylist-downloads.adblockplus.org/easylist_content_blocker.json`;
    - label "Filter List URL";
    - description "A content blocker list in WebKit's format, downloaded automatically every 24 hours."
- **`FilterList(Path dataDir, Executor background, HttpClient http, Clock clock, Supplier<URI> url)`:**
  - **`Optional<RuleSet> active()`** (any thread, volatile) is empty while off or before a list loads.
  - **`void enabled(boolean on)`:** turning it on loads the cache in the background, then checks; turning it off
    empties `active()`.
  - **`void start()`:** an hourly check while on, the first one now.
  - **`CompletableFuture<Status> updateNow()`**, with
    `record Status(int rules, Optional<Instant> updated, boolean updating, Optional<String> error)`.
  - **`OptionalInt staleDays(Instant now)`:** the whole days since the update, from 7 on.
  - **A download:**
    - a GET with a 30 s connect timeout and a 120 s request timeout, read up to 32 MiB;
    - a 200 response only;
    - `BlockerJson.read`, then `ContentRules.compile`;
    - written to `data/adblock/rules.json` (the compiled list, from `encode`) and `data/adblock/state.json` (`url`,
      `updated`, `rules`, `skipped`), each through a temporary file and an atomic move;
    - then the new rule set replaces the old.

    A failure keeps the old list and records the error.
  - **When an update is due:**
    - 24 hours have passed;
    - or nothing is cached;
    - or the cache came from another URL. Changing `filter_list_url` therefore makes one due at once.
  - Clear Website Data leaves the list.
- **The panel's Ad Blocking section:** the two rows, then "Filter Statistics" with **Update Now**. Its text is one of:
  - "N rules" (`%,d`) and "Updated DATE";
  - "Updating…";
  - "Could not update the filter list: REASON".

  When stale, a WARNING text adds "Ad block rules haven't been updated for N days" (iTerm2's fragment, completed).
- **`static Set<ResourceKind> AdBlockRequests.kinds(CefRequest.ResourceType)`:**

  | Chromium's types | WebKit's kinds |
  |---|---|
  | `RT_SUB_FRAME` | `document` |
  | `RT_IMAGE`, `RT_FAVICON` | `image` |
  | `RT_STYLESHEET` | `style-sheet` |
  | `RT_SCRIPT` and the three worker types | `script` |
  | `RT_FONT_RESOURCE` | `font` |
  | `RT_XHR` | `raw` and `fetch` |
  | `RT_MEDIA` | `media` |
  | `RT_PING`, `RT_CSP_REPORT` | `ping` |
  | every other sub-resource type | `raw` and `other` |
  | `RT_MAIN_FRAME`, `RT_NAVIGATION_PRELOAD_MAIN_FRAME` | none: never blocked *(call)* |

- **`onBeforeResourceLoad`** returns `ruleSet.blocks(request.getURL(), kinds, browser.getURL())`. It skips a page at a
  `jasper-about:` address, and any request that isn't http, https, ws or wss.
- **`hide-elements.js`** adds one `<style>` holding `RuleSet.stylesheet(url)` as soon as the document has a root. It is
  not injected when the stylesheet is empty.

- [ ] **Step 1: Write the failing tests:**
  - **`FilterListTest`:**
    - the fixture list gives the status's rule count and writes the cache;
    - a new `FilterList` loads the cache without the network;
    - a 500, a timeout, text that isn't JSON, and a streamed body over 32 MiB each keep the old list and record the
      error;
    - an update is not due at 23 h, and is due at 24 h or at another URL;
    - `staleDays` gives empty at 6 days and 7 at 7;
    - turned off, `active()` is empty.
  - **`AdBlockRequestsTest`:** the table above.
  - **`FrameScriptsTest`:** an empty stylesheet injects nothing, and `</style>` and quotes arrive escaped in the script
    string.
  - **`BrowserSettingsPanelTest`:** the status texts, the stale text, and Update Now.
  - **`OnboardingPagesTest`:** the Ad Blocker switch shows `ad_blocking`'s value and sets it, and the three plan 6
    feature rows are listed.

- [ ] **Step 2: Run** the tests. Expected: FAIL.

- [ ] **Step 3: Implement.**

- [ ] **Step 4: Run** `./gradlew :jasper-plugin-browser:check`. Expected: PASS.

- [ ] **Step 5: Commit** "Browser: ad blocking from a content-blocker list".

### Task 11: The HTTP proxy

**Files:**
- Create: `engine/NetworkPreferences.java`.
- Modify: `engine/Chromium.java`: `setProxy`, applied in `onContextInitialized`, in `inMemoryContext()` and on
  change.
- Modify: `settings/BrowserSettings.java` and `settings.toml`. `Declared` gains `proxyServer()` and
  `onProxyChanged`.
- Modify: `BrowserPlugin.java`.
- Test: `NetworkPreferencesTest`, `BrowserSettingsTest` and `ChromiumStateTest`.

**Interfaces:**
- **The settings**, in section "Proxy":
  - `http_proxy`: boolean, `false`, "Enable HTTP Proxy", with the description "Route browser traffic through a
    CONNECT proxy server.";
  - `proxy_host`: text, `127.0.0.1`, "Proxy Server". It takes a host name or an IP address, an IPv6 one without
    brackets, and refuses others with "Enter a host name or IP address.";
  - `proxy_port`: an integer from 1 to 65535, `8118`, "Proxy Port".
- **`Optional<String> Declared.proxyServer()`** is empty while off. Otherwise it is `host:port`, with IPv6 written
  `[::1]:8118`.
- **`static Map<String, Object> NetworkPreferences.proxy(Optional<String> server)`** gives
  `{"mode": "fixed_servers", "server": server}`, or `{"mode": "system"}` while off *(call: off means the system's
  proxy settings, as WebKit uses)*.
- **`static Optional<String> NetworkPreferences.apply(CefRequestContext context, Map<String, Object> value)`** runs on
  Chromium's UI thread.
  - It calls `setPreference("proxy", value)`. A null answer means applied, and `CloseAllConnections(null)` follows.
  - Otherwise it returns the error.
- **`void Chromium.setProxy(Optional<String> server)`** (any thread) remembers the value:
  - **Before the start,** the start applies it to the global context in `onContextInitialized`.
  - **While running,** `onUiThread` applies it to the global context and to every in-memory context.
  - **A new in-memory context** gets it before `inMemoryContext()` completes.
  - **A failure** goes to the plugin's log and to one error, "The proxy could not be set", with Chromium's reason.

- [ ] **Step 1: Write the failing tests:**
  - **`NetworkPreferencesTest`:** both maps, and IPv6.
  - **`BrowserSettingsTest`:**
    - `127.0.0.1`, `proxy.local` and `::1` are allowed;
    - `http://x`, `a b` and empty are refused;
    - `proxyServer()` gives what is described above.
  - **`ChromiumStateTest`:** `setProxy` before any start is remembered and starts nothing.

- [ ] **Step 2: Run** the tests. Expected: FAIL.

- [ ] **Step 3: Implement.**

- [ ] **Step 4: Run** `./gradlew :jasper-plugin-browser:check`. Expected: PASS.

- [ ] **Step 5: Commit** "Browser: the HTTP proxy".

### Task 12: Docs and the check

**Files:**
- Modify: `docs/browser.md`:
  - /dev/null mode;
  - site permissions: the prompts, the permissions page, the ☰ resets, the refused screen capture, and location
    refused (spec 21);
  - page notifications;
  - audio;
  - clearing data;
  - ad blocking and its limits: supported rules, no main-frame blocking, the base-domain rule;
  - the proxy;
  - the new rows of `#settings`.
- Modify: `docs/packaging.md` (the camera and microphone descriptions), `docs/STATUS.md` (the plan's entry) and this
  plan's status banner.

- [ ] **Step 1: Write the docs,** following AGENTS.md's documentation rules, with nothing of plans 7 to 10.

- [ ] **Step 2: Run the source hygiene script,** then `./gradlew sdkReference`, then `./gradlew check`, then the
  external-plugin check. Expected: PASS. Record the counts from the XML.

- [ ] **Step 3: Commit** "Docs: the browser's privacy features".

### Task 13: The user's GUI check (pending; it never blocks)

**Files:**
- Modify: `docs/browser-verification.md`: a plan 6 section, each line pass, fail or not run.

- [ ] **Step 1: On macOS, with the user's OK for each batch.** Use `:jasper-app:run` from the worktree, except for the
  camera, which needs the packaged `Jasper.app`'s usage descriptions.
  1. **/dev/null:**
     - with the setting on, a new tab shows the indicator and its popover;
     - a site signed into there is signed out in a new /dev/null tab and in a persistent tab;
     - a popup shares its opener's sign-in;
     - Web › History shows only that group's visits, and ☰ has no bookmark items;
     - after a relaunch, the tab comes back at the start page with the indicator.
  2. **Camera and microphone** (packaged app):
     - [the WebRTC sample](https://webrtc.github.io/samples/src/content/getusermedia/gum/) shows "Allow Camera";
     - Block is remembered;
     - ☰ shows "Reset Camera Permission (Blocked)";
     - the permissions page lists the grant, and × reloads the tab;
     - screen sharing is refused without a prompt;
     - `navigator.geolocation` fails without a prompt.
  3. **Notifications:** a `python3 -m http.server` page calls `Notification.requestPermission()`, then
     `new Notification("Hi", {body: "There"})`. That gives a card, and `close()` takes it out of history.
  4. **Audio:**
     - a page with `<audio autoplay>`, and a YouTube embed, are muted with the bar;
     - each of the four answers works;
     - Unmute Current Page works.
  5. **Clearing:**
     - with pages open, Clear Cookies signs out;
     - Clear All Data also clears a test page's `localStorage`, the history, the bookmarks and the permissions;
     - after a relaunch, nothing comes back.
  6. **Ad blocking:**
     - Update Now shows the rule count;
     - an ad test page shows blocked requests and hidden elements;
     - turning it off and reloading brings them back.
  7. **The proxy:**
     - with a local proxy on 8118 (`privoxy` or `mitmproxy -p 8118`), pages load through it, /dev/null tabs included;
     - with the proxy stopped, they fail;
     - turning the setting off loads them directly;
     - Chromium's log shows no `setPreference` error, and Jasper posts no "The proxy could not be set".
  8. **Settings:** Manage Permissions opens the page behind Settings. Ask the user whether that is acceptable.
- [ ] **Step 2: Commit** "Browser: the privacy features' GUI check on macOS". Record Windows and Linux as not run until
  the user has those hosts.

## The plan's calls, for the user

- Settings' Manage Permissions opens the persistent store's page, whatever the /dev/null setting (Task 8).
- Audio decisions follow the page's top-level origin. `audio.js` runs in sub-frames (not challenge frames), so
  embedded players are muted too. The notification polyfill stays in the top frame (Tasks 6 and 7).
- Allow Once and Deny Once last until the page's next main-frame load (Task 7).
- Screen-capture requests are refused without a prompt (Task 5).
- A page posts at most 10 notifications a minute, and a blank title becomes the host (Task 6).
- Clear Website Data clears the known origins live and wipes `<data>/chromium` at the next start (Task 8).
- Ad blocking never blocks a main-frame load. First and third party use a base-domain rule with no public suffix
  list. Unsupported rules are skipped and counted (Tasks 9 and 10).
- The proxy switched off means the system's proxy settings (Task 11).
- The filter list downloads only once a browser item has shown, or when the user turns it on or presses Update Now
  (Task 10).

## What follows

- **Plan 7 (page tools)** puts its reader group first in the ☰ composition (Task 4).
- **Plan 8 (logins):**
  - adds "Manage Saved Passwords" (button **Manage Passwords**) to `BrowserSettingsPanel`'s Privacy & Data;
  - reads `DataStore.devNull()`, so /dev/null items never remember an HTTP sign-in.
