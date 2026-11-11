# Browser 7: downloads and page tools — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or
> superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Execution status (2026-11-10): Tasks 1-7 executed and reviewed (Task 1 as 1a, 1b and 1c, ruling D-F17), and Task 8,
the docs; Task 9, the user's GUI check on macOS, is pending, and so are the final reviews** (branch
`claude/browser-downloads-page-tools`, worktree `.claude/worktrees/browser-4b-4c-e6deab`, cut from local `main`
`417e2270f`, which has plans 4a, 4b, 4c, 9, 5 and 6 merged; the plan `a52181595`, brought in from `5c42e4a82`, then
commits `371a94680` to `87e4fb7c3`, and the docs, `2a8b0a782` and `5949bdfba`, and their counts; not merged or pushed;
see `docs/STATUS.md`). Executed with per-task subagents and reviews, each task's fix rounds until a re-review found
every finding addressed. The pre-flight scan's findings F1-F38 were adopted as rulings (D0, cited D-F*n*) except where
D1-D3 decide otherwise, and the controller's further rulings are D4-D44; plan 4a's R0-R48 and the banners of 4b, 4c, 9,
5 and 6 bind. On 2026-11-09 the user chose D1 ("Match Chrome"), D2 ("Yes, download it") and D3 ("No"), and asked for no
change to the plan's own calls. 4b's P6, no Save Link As… or Save Image As… until this plan, is closed by Task 4's items
(D-F10). The task reviews recorded these deviations from the plan's text, each with its ruling:
- **Versions:** `JasperSdk.VERSION` 0.30.0 to 0.31.0, every bundled range `>=0.31.0, <0.32` (Task 1a, D-F17), not the
  plan's 0.30.0. New SDK members, each `@since 0.31.0`: `PluginMenu.addEntries(List<MenuItem>, Consumer<String>)` and
  `MenuEntries` (`set(List<MenuItem>)`), and `Platform.downloadsFolder()` and `Platform.markDownloaded(Path, URI,
  Optional<URI>)`; the testkit's `FakePluginHost.chooseMenuEntry`, `setDownloadsFolder`, `markedDownloads`,
  `failMarks` and `MarkedDownload`. **The Browser stays 0.2.0** (D-F17): `dev.jasper.browser.api` is unchanged, and the
  plugin asks for no new capability. Readability.js 0.6.0 ships unmodified in the Browser's `third_party/` (D2), with
  its `notices.toml` component and `ReadabilityBundleTest` (D-F23).
- **Task 1a** (`371a94680` "SDK: 0.31.0"): `827ac12ba`'s 39 files; `15-versions.html`'s `<=0.30.2` example became
  `<=0.31.2`.
- **Task 1b** (`7b8258f8c` "SDK 0.31.0: menu entries that are not actions"; fix round `ce4a8dd13` "SDK 0.31.0: menu
  entries' review fixes"): the app's `MenuEntry.Command(key, label, enabled, chosen)` and a keyed
  `Submenu(key, title, enabled, entries)`, whose 11 constructor calls are updated (D-F18, D12), and blank keys refused;
  keys `<plugin id>/<group>/<item id>`, one group per `addEntries`; `WindowChrome` keeps each keyed item by key, runs
  the newest snapshot's `chosen`, sets labels and enabled states in place, an open menu's included, and disables a
  vanished entry at once; a structural change of any contributed section, or of placed actions (D13), waits while a
  menu is open (D-F9: the selection path rooted in the window's bar, or a top-level `JMenu` selected), and applies at a
  menu's close or, without one, at the next change or render (D14), the folded bar's More and hamburger menus
  observed; the fake's `entry:` and `entries:` lines, and `chooseMenuEntry`, which skips disabled leaves and returns
  whether one ran; choosing needs the window to take input; a test-only `plugins.MenuEntriesTestBridge`; a sentence in
  `docs/sdk-architecture.md`.
- **Task 1c** (`f28fc63fd` "SDK 0.31.0: the Downloads folder and the downloaded-file mark"; fix round `e4d709ac1` "SDK
  0.31.0: the Downloads folder's review fixes", and `e3deff65c` "Test: set the earlier quarantine mark through
  setxattr", made outside the session and cherry-picked with its own message): `platform.DownloadsFolder`, found once
  per run (D-F3), Linux's `user-dirs.dirs` read as `xdg_user_dir_lookup` reads it, at most 64 KiB, with D15's `"$HOME"`
  and D16, Windows' known folder through JNA in `platform` itself, else `%USERPROFILE%\Downloads` (D-F37); and
  `platform.DownloadMark`: on macOS one `setxattr` with `XATTR_NOFOLLOW | XATTR_CREATE`, `EEXIST` kept, the value
  `0081;<hex>;Jasper;<UUID>` without a NUL and the reason from `strerror_r` (D15); on Windows `Zone.Identifier` after a
  `FileStore` check, so FAT32 and exFAT get "This volume cannot keep a downloaded-file mark", an existing non-empty
  stream kept, a referrer that is not `http` or `https` written `about:internet` (D15); a link marked itself, never its
  target. The fake refuses a mark on the EDT except inside `runBackground()`'s tasks, `failMarks` is sticky and
  `dataRoot()` synchronized; two contract cases beyond the brief's. The controller finished the fix round after two
  restarts of the app, which were the endpoint agent killing the process tree when a test ran `/usr/bin/xattr -w`
  (D17); `e3deff65c` sets that earlier mark through `DownloadMark`'s own `setxattr`.
- **Task 2** (`5143079dd` "Browser: download names, the Downloads folder setting and the list"; fix round `fa4c45586`
  "Browser: the downloads core's review fixes"): `downloads` (new): `DownloadNames` (D-F4's `safeName` with D18's
  extras, iTerm2's numbering, reservations keyed by the whole path in NFC and `Locale.ROOT` lower case (D-F5), at most
  1,000 numbered names, Windows' `MAX_PATH` with " (1000)" and `.crdownload`, each reservation a
  `DownloadNames.Reservation` token that frees only itself); `DownloadFolder` (`""`, `~`, `~/x`, `~\x` on Windows, or
  absolute; on Windows drives and shares, the `\\?\` and `\\.\` namespaces and a name ending in a space refused; a
  refused or unparsable value resolves to the system's folder); `DownloadList` (one outcome per download, D-F2's and
  D11's names, 100% at completion, the 20 newest kept, D-F19); `DownloadsMenu` (oldest first, "1 byte" and TB); the
  `downloads_folder` setting after `page_zoom`, through `Declared.downloadsFolder()`.
- **Task 3** (`39f098db6` "Browser: downloads, the Downloads menu and the downloaded-file mark"; fix round `98a11775c`
  "Browser: downloads' review fixes"): `page.PageDownloads`, the plan's one new JCEF class, one handler per Chromium
  through a `PREPARED` set (D-F20), with CEF's browser identifier in `DownloadKey` (D-F21), and `DownloadStart`
  carrying `activated`, `document` and `inMemory`; `PageContext.onClosed` (D-F7); `downloads.Downloads`, made at start
  with no I/O and no menu, with `FolderResolution` on the background executor (D-F3, D4), `DownloadAddresses` (D11's
  host, "this site" when there is none) and `FileManager` (D-F31, D21), whose failures are errors in the Downloads
  group; `Downloads.drained(Page)` built here for Task 4 to wire (D19); D1, and D20's counter (`Page.load`'s loads plus
  the documents); a start whose answer throws is abandoned (`Downloads.abandon`); before the first resolution a
  download is refused with "Jasper is still preparing the Downloads folder."; an ask goes even while the folder is
  unusable; `stop()` runs `downloads.stopAll()` first and `deleteIntermediates()` after `chromium.close()` (D-F29,
  D-F30); the three folder usage strings, which `packageApp` and `verifyPackage`, without AOT, read back, and
  `docs/packaging.md`'s section; `Downloads` split into `FolderResolution` and `DownloadAddresses` (627 to 571 lines).
- **Task 4** (`6a895753f` "Browser: Alt-click loads in place, Save Link As asks, and closing a tab keeps its downloads";
  fix round `f98750727` "Browser: a download and its page, review fixes"): Alt-click through a new `mouse` component of
  `PageView.Hooks`, Alt cleared from the left button's events while `lastPress()` keeps it; Save Link As… and Save
  Image As… added (D-F10, D9, `LinkSchemes.saves`); `DownloadAsk.arm(url, now)` and `take(url, now)`, D-F32's rule
  with fragments ignored, `Page.askNextDownload(String)` and `Page.startDownload(String)`; the close hold (D-F1, D19):
  the closing page's browser shows `about:blank` and closes once its downloads end, on the UI thread, and not once
  Chromium has stopped; `Page.onSaveCancelled` (D-F1, D24, with `unupdated` and `uncounted` under one lock) and
  `Page.onFirstNavigationDownload` (D-F28, every refusal included, so no gap is left); D23; `PageDownloadState` moved
  out of `Page` (1,652 to 1,602 lines).
- **Task 5** (`ef2483295` "Browser: reader mode with Readability.js"; fix rounds `54c6aeac1` "Browser: reader mode's
  review fixes" and `dda17f9e7` "Browser: reader mode's find and input"): Readability.js copied byte for byte from the
  staging (D2); `tools` (new): `PageTools` with D27's signature (each script with its URL, D-F25's zoom, D-F24's
  showing and timer, and a focus hook), `ToolScripts` (`readerCheck(token)` and `readerShow(css, token)`, each with
  Readability.js) and `reader.js`; `Page.run(script, url)`, main frame only and nothing before ready, after close, at a
  challenge address or (D30) on `jasper-about:`, `Page.onLoadEnd` and `Page.onToolReport`, and the ops `READER` and
  `DISTRACTIONS`; `item.PageToolsHost` (D-F38); `PageHtml.colours(palette, selector)` for the shadow root's `.reader`;
  D-F24's dropped elements widened (`frame`, `frameset`, `style`, `link`, `meta`, `base`), a trusted Escape only, and
  `pagehide` closing the overlay; D26, D28, D29, D30, D31 (4b's `find.js` searches the reader's root, with
  `PageFind.searchTopFrameOnly` and `restart`) and D32.
- **Task 6** (`37abec264` "Browser: distraction removal and Remove Element"; fix round `ca8b60016` "Browser: distraction
  removal's review fixes"): `distractions.js` (`start`, `stop`, `removeAt`); Remove Element at the menu's point at once
  (D33), `page-menu.remove-element` and `MenuPlace.removeElement`; D33's two additions; the secondary button's events
  left to the page's menu; D34; Escape's keyup and repeats swallowed until that keyup; backdrops read from the
  document's last 50,000 elements, a fixed child of `<html>` counted, never the body or the root; Reader Mode enabled
  while offered or reading; Remove Element disabled where Jasper's page scripts do not run, through
  `PageMenuItems.Context`'s new `page`; D35.
- **Task 7** (`8b3d5375a` "Browser: Save Page As saves a folder with the page and its resources"; fix rounds `de91e3216`
  "Browser: Save Page As's review fixes" and `87e4fb7c3` "Browser: Save Page As's last fixes"), D38's deviations:
  `SavePage.start(Page, ContentTab, PluginContext, Path, FileManager, Network, later)`, with `Network(proxy, current,
  running, clients)` for D-F14, D5, D6 and D41; `Snapshot`'s prefix; `PageSaver` an instance returning `Saved`;
  `SaveAddresses` and `PrivateHosts`; the item dimmed on challenge pages too; the document cloned whole; fragment-only
  links kept, `formaction` made absolute, SVG `<style>` left as written; the style elements' and attributes'
  references fetched and rewritten; an answer served as `text/css` named `.css`, a script staying a script (D43); D-F12
  refused more widely (`0.0.0.0/8`, `fec0::/10`, IPv4 inside IPv4-compatible and NAT64 addresses, BSD spellings,
  registry hosts never fetched); `@charset` rewritten to `utf-8`; a busy bridge retried 20 times, 100 ms apart; a
  `RuntimeException` in one fetch leaves that resource absolute; the save's failures and unmarked files told in the
  Downloads group; `BrowserPlugin`'s seam constructor gains `saveClients`, re-pinned in `OfflinePluginsTest`; D36 as
  D42 built it, `<html style>` tokenized too; D39-D41, D43 (`DownloadNames.reserveFolder`) and D44 (each file through
  one `place`: written, checked, marked, moved).
- **Task 8** (`2a8b0a782` "Docs: downloads and page tools", and "Docs: plan 7's counts after the docs"), the docs: the
  Browser guide (Downloads, Page tools, the toolbar's ☰, Find in reader mode, the right-click menu, Links in a page,
  error pages, /dev/null mode, Jasper's own requests and the `downloads_folder` row), `docs/packaging.md`'s timing of
  macOS's folder prompts, the configuration guide's Browser row, the notifications guide's Downloads group, the
  README's, Plugins' and the docs index's Browser descriptions, the SDK README's `MenuEntries`, AGENTS.md's Verified
  JCEF behaviour (the download handler, and `CefBrowser_N`'s calls before the native browser exists), this banner and
  STATUS (D8). The SDK guides, the testkit README and `docs/plugin-authoring.md` came with Tasks 1b and 1c. After both
  docs commits, at `5949bdfba`, `./gradlew check --continue` (JBR 25.0.4.1 b635.75 jcef, 10m 1s, 31 of 172 tasks run)
  gave **15,891 tests, 0 failures, 0 errors, 105 skipped** (1,413 classes in the 16 build folders, without the example
  plugin's: jasper-app 5,352 with 92 skipped, Editor 5,387 with 4 skipped, Browser 1,621 with 1 skipped, jasper-ui
  1,020, Remote 659, jasper-terminal 512 with 7 skipped, jasper-sdk 136, jasper-sdk-testkit 324), as at `87e4fb7c3`
  before the docs (the controller's check, 9m 57s): plan 6's 15,546 and plan 7's 345, the 2 more skipped
  `DownloadMarkTest`'s Windows and Linux rows. The example external plugin's `check pluginZip` against
  `build/sdk-release` passes (1 test, up to date: no SDK change since Task 1c); `sdkReference` changed nothing; source
  hygiene is clean over 3,512 Java files, and no file the docs touched holds a raw U+00A0, U+2028 or U+2029. The full
  checks after each task: 15,546 (Task 1a), 15,567 and 15,573 (1b), 15,615 (1c, whose run lost its Gradle daemon in a
  packaging step) and 15,620 (1c's round), 15,669 (2), 15,709 and 15,723 (3), 15,748 (4), 15,786 and 15,797 (5), 15,813
  (6), 15,872, 15,886 and 15,891 (7), each with 0 failures and 0 errors, 103 skipped through Task 1b and 105 from Task
  1c on; the example external plugin's `check pluginZip` against `build/sdk-release` passed after each SDK task. No GUI
  was launched.
- **Task 9** (`5949bdfba` "Docs: plan 7's GUI check, pending"), the checklist:
  [Plan 7: downloads and page tools](../../browser-verification.md#plan-7-downloads-and-page-tools), every line
  pending, as nothing was run: no GUI, no packaging with AOT, no benchmark.

The controller's rulings, one line each:
- D0: every ruling the pre-flight scan proposed (F1-F38) is adopted and cited as D-F*n*, except where D1-D3 decide
  otherwise; its provisional items are re-checked against the merged plan 6 before the task that uses them.
- D1: F16, the user's choice ("Match Chrome (Recommended)", 2026-11-09): after a page's first download without a user
  activation in the current document, later ones without one are refused, with one "This site tried to download
  multiple files" notice per document; gestures and Save Link As always download.
- D2: the user's choice ("Yes, download it", 2026-11-09): Task 5 bundles the staged Readability.js 0.6.0 unmodified
  (sha512 verified; SHA-256 `34dcab3d…f174`, 89,980 bytes; Apache-2.0, no NOTICE), and no implementer downloads it.
- D3: the user's choice ("No (Recommended)", 2026-11-09): no jsdom; `reader.js`, `distractions.js` and `save-page.js`
  get `node --check`, stub harnesses (D-F36) and pending GUI lines.
- D4: R1 (F3, F8): the early folder resolution only creates and checks the folder itself, never reads or writes inside
  it; the Desktop and Documents usage strings read "Jasper saves files from the Browser here when you choose this
  folder."
- D5: R2 (F12): with the HTTP proxy on, Save Page As checks the literal host only, and resolves names only while the
  proxy is off.
- D6: R3 (F14): a proxy change during a save retires that save's client, and unfetched resources stay absolute.
- D7: R4 (F26): a suggested save folder is numbered at its end, "example.com (1)".
- D8: R5: Task 8 also updates the Browser lines of `README.md`, `docs/plugins.md` and `docs/README.md`, the
  notifications guide's Groups table, the configuration guide's Browser row and AGENTS.md's Verified JCEF behaviour.
- D9: R6 (F10): Save Link As and Save Image As are enabled for the `http` and `https` addresses that
  `LinkSchemes.opensFrom` allows, and for `data:` addresses on their own.
- D10: R7 (F24): a site's own challenge page (Cloudflare's "Just a moment…" on the site's host) is a known gap, with a
  Task 9 line.
- D11: R8: D1's notice carries the page's host as its content, and a running "Unconfirmed *n*.crdownload" keeps the
  shown name.
- D12: `MenuEntry.Submenu` carries `enabled` beside its key (`""` and `true` for action submenus).
- D13: placed actions' text and enabled changes also update in place, instead of rebuilding the menu bar.
- D14: a change held while a menu is open also applies at the next model change or menu-bar render when no
  menu-closed event came, so a window is never left with a held change after its menus close; Task 9 checks JBR's
  signals however a menu opens.
- D15: Task 1c's calls are accepted: `strerror_r`; `XDG_DOWNLOAD_DIR="$HOME"` alone means the home folder; a referrer
  that is not `http` or `https` is written as `about:internet`; the short `docs/sdk-architecture.md` paragraph stays.
- D16: `$HOME` in `user-dirs.dirs` expands to an absolute `HOME` from the environment, else `user.home`.
- D17: no test, probe or script on this Mac runs `/usr/bin/xattr -w`, or any tool that adds a quarantine or Mark of
  the Web attribute; marks are set only through the code under test and read with `xattr -p`, as the endpoint agent
  kills the whole process tree when it sees one.
- D18: Task 2's extras are accepted: U+2028, U+2029 and unpaired surrogates replaced, the device check ignoring spaces
  before the first dot, `MAX_PATH` counted with " (1000)", downloads listed oldest first, the dispatch's device list.
- D19: the close hold (F1) is wired in Task 4 through `Downloads.drained(Page)`, and Task 4's review checks F1 end to
  end; a DevTools download from a /dev/null page staying listed, and a download Chromium never updates after
  `before()`, are known gaps, the second with a GUI line.
- D20: a load Jasper starts for the user (`Page.load`: the address bar, a bookmark, the palette, history, a terminal
  link) gives the page a fresh download allowance, as a new document does.
- D21: Windows' Show runs `explorer.exe` with two arguments, `/select,` and the path in quotes; Task 9's Windows line
  uses a name with a comma and a space under a user folder with a space.
- D22: D1 keeps the user's wording, "clicks always download": an activation is not consumed by a download, and a
  click whose answer comes after Chromium's 5 s window counts as automatic; both are known gaps with Task 9 lines.
- D23: Task 4's deviations and choices are accepted (F32's address-taking signatures, the new hook names,
  fragment-blind matching, F1.3's oldest ask start, the mouse hook), and the hover lag is a known gap with a Task 9
  line.
- D24: F1.3 ends only the page's count and keeps the download's start, so a late update still lists and marks the
  file.
- D25: a page that downloads and calls `window.close()` in the same task ends with a marked file in the list, or with
  no file and no entry; Task 9's line expects either.
- D26: while the reader shows, key events stop at the overlay, and Escape closes the reader (as D29 amends it).
- D27: Task 5's deviations are accepted (`PageTools`' signature, the token-carrying ops, `PageHtml.colours` for the
  shadow root); Remove Distractions stays visible and inert until Task 6.
- D28: a reader check's answer counts only while `answerable > 0` and its token equals `answerable`, which is then 0,
  so one check gets one answer; reading turns true only after the show's script is built.
- D29: D26 as the review amended it: the capture acts only while the reader shows, on the window in the capture phase,
  stops propagation only (Escape alone is prevented), is removed at `end()` and `pagehide` and swallows Escape's
  keyup; the page's body is inert while the reader shows, the page takes the focus after a show and the earlier focus
  comes back (`preventScroll`) at close.
- D30: Task 5's minors in the fix round: custom elements in the article unwrapped and `animate` and `set` dropped; the
  Trusted Types comment and Task 9 line corrected; address changes that differ only in the fragment re-check nothing,
  and a `#` link in the overlay scrolls the overlay; `Page.run` refuses `jasper-about:` main frames; the forward
  references to Task 6 removed; the full parse at each load end stays, for the benchmark sitting.
- D31: while the reader shows, Jasper's Find searches the article inside the overlay's shadow root and nothing behind
  it, and after close the page again.
- D32: pointer, mouse, click and touch events inside the overlay stop at the reader's host, and the wheel goes on;
  Escape's keyup swallower is removed at that keyup or at `end()`; a hash-routed app not re-checked on a route change
  is a known gap (D30).
- D33: Remove Element hides the element at the menu's point at once, as the brief, F25 and the spec say; Task 6's two
  additions (frames take no pointer while removal runs, a page that replaced `innerWidth` still gets removal) are
  accepted.
- D34: distraction removal hides only on a click whose `detail` is 1; every click still stops and is prevented, and
  keys pass, Escape alone handled.
- D35: when a click or Remove Element hides a fixed element covering at least 90% of the viewport, each of `html` and
  `body` whose computed `overflow-y` is `hidden` or `clip` gets `overflow-y: visible !important`, kept after stop; a
  body locked with `position: fixed` and a modal `<dialog>` hidden with `display: none` are known gaps.
- D36: the snapshot strips Jasper's own state from the clone in `save-page.js` (the reader's host, body's `inert`,
  distraction removal's outline) and never ends the page's mode.
- D37: Jasper's own fetches (favicons, suggestions, View Source, the filter list, Save Page As) keep the JDK's
  default User-Agent; a resource a CDN refuses stays absolute, a known gap.
- D38: Task 7's deviations are accepted as recorded.
- D39: a save counts as a private page's only when the page's literal host is private; a page reached by name is
  judged public, its private resources left absolute.
- D40: the per-save memo keeps literals and refusals only, and a resolved name is judged right before each of its
  requests; DNS rebinding remains a documented gap.
- D41: a plugin stop or quit cancels running saves: `PageSaver` takes the plugin's running flag, apart from D6's
  current one, shuts each running save's client down at once, skips the write and deletes its temporary files.
- D42: D36 is built with the reader's overflow hold added: `reader.js` and `distractions.js` give what they hold
  through read-only accessors on their kept objects (`held()`, `outlined()`), and `save-page.js` restores it in the
  clone.
- D43: Task 7's minors in the fix round: `198.18.0.0/15` and `64:ff9b:1::/48` refused; saved files made as ordinary
  new files (the umask's mode); `index.html`'s mark source through `DownloadAddresses.source`; buffered style sheets
  capped at 32 MiB in all; kind `script` over a `text/css` type; the save folder reserved through a `Reservation`;
  user info stripped from registry authorities by the last `@`; a 5-minute deadline for a save's fetches; the docs say
  kept scripts run from `file://` and can reach the network, as in Chrome.
- D44: Save Page As's round 2: `index.html` written through an encoder that replaces malformed input, as
  `String.getBytes` did; a scheme-relative reference keeps its form without user info, redirects included; `live()`
  checked again before each move, the temporary file marked before it moves and deleted when its mark is refused
  after a stop; `255.255.255.255` and 6to4 addresses (`2002::/16`) embedding a private IPv4 refused.

Known gaps:
- D19: a download Chromium never updates after `before()` keeps its name and its page's browser until Jasper quits,
  and a DevTools download from a /dev/null page stays in the Downloads menu after its group closes, as it holds no
  page.
- D22: a click allows any number of downloads within Chromium's 5 seconds, as no download consumes it, and a click
  whose server answers after 5 seconds counts as a download without one.
- D23: the hover address lags the pointer, so an Alt-click made as the pointer reaches a link may still download.
- D25: a page that downloads and calls `window.close()` in the same task may land no file at all, when its close comes
  before Chromium asks where the file goes.
- D-F33: on Windows Jasper writes `Zone.Identifier` itself, not through the attachment service, so an antivirus hooked
  there does not scan a download on arrival.
- D10 and D-F24: a site's own challenge page, such as Cloudflare's "Just a moment…" on the site's host, is checked for
  reader mode; on a page that enforces Trusted Types, Readability finds no article where it parses HTML text (a second
  pass, an image inside `noscript`).
- The reader check's full Readability parse at each load end and each settled route change, which the benchmark
  sitting measures (D30); a hash-routed app is not checked again on a route change (D30, D32).
- A capture listener a page put on its window before Jasper's scripts still hears keys and clicks first, in reader
  mode and distraction removal, and one that stops Escape keeps the reader open, which Exit Reader Mode still closes
  (D29).
- D35: hiding a wall leaves a body locked with `position: fixed`, and a modal `<dialog>` hidden with `display: none`
  may keep its top layer; a page that locks `overflow-y` for its own layout scrolls after a full-viewport hide.
- D-F12 and D40: DNS rebinding, narrowed to the time between the check and the connect; D5: with the HTTP proxy on,
  only the host as written is checked, so a name that resolves to a private address goes through the proxy; D39: a
  LAN page reached by a name saves with its private resources left absolute.
- D37: Jasper's fetches send the JDK's User-Agent, and a resource a CDN refuses for it stays absolute.
- Save Page As does not save shadow DOM content, `<template>` content's references, SVG `image` and `use` references
  (made absolute) or CSS `image-set()` strings; lazy-loaded `data-src` images stay as written.
- The measured heap of a maximal save (64 parts of 1 MiB, D43): it completes in `-Xmx320m` when the HTML is ASCII
  and in `-Xmx512m` when it is held as UTF-16, against the app's default 2 GiB.
- Recorded minors: no test for a symbolic-link `user-dirs.dirs` or an escaped quote in it (the code is right), and the
  Linux fallback stays `home/Downloads` (Task 1c); no test drives a page's own `location.href` through the real loads
  counter (Task 3); `stopAll` leaves the `unupdated` and `uncounted` sets, at quit only (Task 4); no `PageSaverTest`
  row with a valid supplementary character, and `localhost.localdomain` is refused only by its resolved address while
  the proxy is off (Task 7).

Deferred minors, by task, for the final reviews:
- Task 1b: the rebuild may run in the one turn between More closing and its pending `restoreMenus` (handled,
  untested); a blank `MenuItem` id gives a non-blank key (prefix and `""`), so whether `MenuItem` refuses a blank id
  wants checking; the folded bar's restore order is checked by reading only.
- Task 5: while the reader closes, Escape's auto-repeat keydowns reach the page; only `body` is inert, so a replaced
  body, nodes under `<html>` outside it and the top layer escape it; a script-made customized built-in (an `is` value
  without the attribute) still upgrades; `reader.js`'s `report()` does not retry a busy bridge, so an answer or
  Escape's "off" can be lost; copy, cut, `dragstart` and `focusin` from the overlay reach the page's listeners.
- Task 6: `distractions.js`'s `report()` has no retry either.
- Task 7: an IPv4-translated address (`::ffff:0:0/96`) embedding a private IPv4 is not refused; a stop during the
  write keeps the files already in place, each marked. `255.255.255.255` and 6to4, deferred after round 1, were folded
  into D44.

Not run:
- **Task 9**, the user's GUI check on macOS, with their OK for each batch: pending
  ([checklist](../../browser-verification.md#plan-7-downloads-and-page-tools)), F1, F2, F9 and F29 first, then the
  plan's twelve lines with the task reviews' lines.
- **Benchmarks:** the start-up and memory batch (D-F35, as plan 6's V4), with the reader's parse cost and the save heap,
  waits for the benchmark sitting.
- **Windows and Linux** (no hosts): the known folder, `Zone.Identifier` on NTFS and FAT32, Explorer's `/select,`, the
  XDG folder and `xdg-open`; and **packaging with AOT**, whose training needs the user's OK.
- **No JavaScript runs in a test** (D3): `reader.js`, `distractions.js`, `save-page.js` and 4b's `find.js` are checked
  as text, with `node --check` and with stub harnesses (in `.superpowers/sdd/probes/` and the scratchpad); no test
  starts Chromium.

**Goal:** Give the Browser iTerm2's downloads and page tools:
- downloads that go to the Downloads folder without asking, with the system's downloaded-file mark;
- the Downloads menu and the download notifications;
- reader mode and distraction removal in the ☰ menu;
- Remove Element and Save Page As… in the page's right-click menu;
- the Downloads folder setting.

**Architecture:**
- **The SDK** gets what a plugin cannot do itself: menu entries that are not actions, the system's Downloads folder,
  and the downloaded-file mark. The app does the native work: JNA for the macOS mark and Windows' known folder.
- **`page`** gains the one JCEF class this plan adds, `PageDownloads`, which turns Chromium's download handler into
  plain values.
- **`downloads`** (new) holds the list, its names, the Downloads menu and its notifications. **`tools`** (new) holds
  reader mode, distraction removal, Remove Element and Save Page As. Neither names a JCEF type.
- **Page scripts** run in the top frame and answer through the opener bridge's router, as 4b's `favicon` and `find` ops
  do. Readability.js 0.6.0 is bundled unmodified.

**Tech Stack:** Java 25 and Swing on JBR 25.0.4.1 b635.75 with JCEF (Chromium 150, in process), Jasper's SDK and
testkit, JNA Platform 5.14.0 (app only), `java.net.http`, Mozilla Readability.js 0.6.0, JUnit 5.

**Spec:** [docs/superpowers/specs/2026-10-26-jasper-browser-plugin-design.md](../specs/2026-10-26-jasper-browser-plugin-design.md),
sections 6 (the ☰ menu's reader items), 10 (Downloads; Page tools: reader mode, distraction removal, Remove Element,
Save Page As…), 11 (Save Page As… and Remove Element), 17 (Downloads folder), 20 (Get Info and SSH locations left out)
and 23 item 7. View Source, also in item 7, is 4b's Task 9. This plan builds on
[4a](2026-10-28-browser-4a-browser-core.md) and [4b](2026-10-29-browser-4b-page-features.md), whose rulings bind:
4a's R0-R48, 4a's 2026-10-30 sandbox ruling (`no_sandbox = true`), and 4b's banner.

## Global Constraints

- **Branch:** from `main` once plans 4b, 5 and 6 have merged, as `claude/browser-downloads-page-tools`, in its own
  worktree.
- **SDK version:** this plan's additions take the next minor after `JasperSdk.VERSION` on that `main`, written `0.N.0`
  below. That is 0.30.0 if 4b, 5 and 6 each took one after 4a's 0.26.0. New members carry `@since 0.N.0`, and every
  bundled manifest's range moves to `>=0.N.0, <0.(N+1)`.
- **Earlier plans' names** (follow any renames their builds made):
  - **4a:** `Page`, `PageView`, `PageBrowser`, `PageEvents`, `Chromium.onClient`, `OpenerBridge` and `BridgeJson`,
    `BrowserItem` (`act`, `event`, `render`), `BrowserItems`, `BrowserToolbar.node(...)`, `BrowserSettings.Declared`,
    and `PageDialogs`, whose `onFileDialog` sends `FILE_DIALOG_SAVE` to `chooseSaveFile`;
  - **4b:** `PageMenu`, `PageMenuItems.of(Context)`, `Zoom`, `SourcePage`, and the CSS helper shared by `WelcomePage`
    and `ErrorPage`;
  - **4b, the page ops:** the bridge's per-op routing that `detect-favicon.js` (op `favicon`) and `find.js` (op
    `find`) use, written `ops.on(op, (page, json) -> answer)` below, and its way of running a script in a page's top
    frame, written `page.run(source, scriptUrl)`;
  - **5 and 6:** the ☰ items they add, and plan 6's in-memory data stores.
- **Engine facts** (`javap --module jcef`, JBR 25.0.4.1 b635.75, 2026-10-30; do not re-derive):
  - **The handler:** `CefDownloadHandler` has `boolean onBeforeDownload(CefBrowser, CefDownloadItem, String
    suggestedName, CefBeforeDownloadCallback)` and `void onDownloadUpdated(CefBrowser, CefDownloadItem,
    CefDownloadItemCallback)`. `CefClient.addDownloadHandler` adds one per client.
  - **Continuing:** `CefBeforeDownloadCallback` has only `Continue(String path, boolean showDialog)`.
    `CefBeforeDownloadCallback_N.finalize()` calls `Continue("", false)`, so an unanswered download goes on into
    Chromium's temp folder. Jasper therefore never returns true without continuing. Returning false is CEF's default,
    which cancels for Alloy-style (off-screen) browsers, and is why 4a downloads nothing.
  - **The item:** `CefDownloadItem` has `isValid`, `isInProgress`, `isComplete`, `isCanceled`, `getReceivedBytes`,
    `getTotalBytes`, `getPercentComplete`, `getFullPath`, `getId`, `getURL`, `getSuggestedFileName` and
    `getMimeType`. It has no interrupted state, interrupt reason or original URL: Jasper reads "not in progress, not
    complete, not cancelled" as interrupted.
  - **Updates:** `CefDownloadItemCallback` has `cancel()`, `pause()` and `resume()`, and may be kept. Updates name the
    browser that started the download.
  - **Other calls:** `CefBrowser` has `startDownload(String)`, `getRequestContext()` and `getSource(CefStringVisitor)`.
    `CefFrame` has no `getSource`. `CefContextMenuParams` has `getXCoord()` and `getYCoord()`.
- **Platform facts** (2026-10-30):
  - **No mark from Java's view on macOS.** Writing `com.apple.quarantine` through `UserDefinedFileAttributeView` on
    this JBR stores `user.com.apple.quarantine`, so the real attribute needs `setxattr`. Chrome's files in
    `~/Downloads` carry `0081;<hex seconds>;Chrome;<UUID>`, and Firefox's `0081;…;Firefox;…`.
  - **The mac bridge can't do it.** It loads only inside a packaged `Jasper.app`
    (`jasper-app/src/main/java/dev/jasper/app/platform/MacNative.java:31-37`). JNA Platform 5.14.0 is an app dependency
    (`jasper-app/build.gradle.kts:15`), with `--enable-native-access=ALL-UNNAMED` (`:48`).
  - **Windows' known folders** already come from JNA: `Shell32Util.getKnownFolderPath`
    (`jasper-app/src/main/java/dev/jasper/app/cygwin/CygwinLayout.java:20`).
  - **Windows' mark.** On NTFS, Java's `UserDefinedFileAttributeView` uses alternate data streams, so its
    `Zone.Identifier` is Mark of the Web. Not run on Windows this session.
  - **Downloads folders today:** the app knows only `home/Downloads` (`jasper-app/src/main/java/dev/jasper/app/files/Places.java:46`).
  - **The SDK's menus and Platform:**
    - `PluginMenu` takes only action ids, separators and fixed-title submenus;
    - `HostedMenu.push()` sends a whole snapshot on each change (`jasper-app/.../plugins/HostedMenu.java:43-46`);
    - `WindowChrome.build` renders it with `PlainText` (`jasper-app/.../workspace/WindowChrome.java:379-414`);
    - `Platform` can neither reveal nor open a file. The File Server reveals with `Desktop.browseFileDirectory`, else
      `Desktop.open` (`plugins/fileserver/.../FileServerPlugin.java:659-669`).
  - **Folder settings:** `EditorHint.directory()` gives a text field with no chooser (`EditorHint.java:13`). The Vault
    uses `""` for "the default folder" (`plugins/vault/.../VaultSettings.java:30-33`).
  - **Notices:** `verifyThirdPartyNotices` checks jars and fonts only (`gradle/third-party-notices.gradle.kts:49-58`).
  - **Readability.js:** 0.6.0 is the latest `@mozilla/readability`, published 2025-03-03, Apache-2.0.
- **What downloads is Chromium's decision** (spec section 10's call). A navigation that becomes a download ends in
  `ERR_ABORTED`, for which 4b's error pages show nothing.
- **Left out** (spec section 20): Get Info, and saving to SSH locations. `chooseSaveFile` is local.
- **No new actions.** As in 4b, the ☰ and right-click items are menu items, and the Downloads entries are menu entries.
- **Boundaries:**
  - JCEF only in `engine`, `page`, `links`, `dialogs` and 4b's packages (`ChromiumBoundaryTest`);
  - painting only in `PageView`;
  - no engine fake;
  - starting the plugin loads no Chromium class (`StartLoadsNoChromiumTest`);
  - no interface without two real implementations: the SDK's are implemented by the app and the testkit.
- **No GUI from agents:**
  - tests are headless and start no Chromium, and the user's GUI check is Task 9;
  - never run `:jasper-app:run`, benchmarks or AOT training without the user's OK;
  - never run `./gradlew --stop`.
- **Copy:** the spec's strings, with "iTerm2" read as "Jasper". **No animations.** Every platform: macOS, Windows, and
  Linux on x64 and aarch64.
- **No user info** (R46): no URL a download, a mark or a saved page writes keeps its `user:password@`.
- **Commits** end with:

  ```
  🤖 Generated with Claude Code at The Home Depot

  Co-Authored-By: Claude <noreply@anthropic.com>
  ```

## Review Focus

1. **The Downloads menu, open while downloads progress.** Stop stays clickable, and the open menu neither closes nor
   jumps. A title change updates the shown item in place, and a structural change waits until the menu closes. Task 1
   pins this.
2. **Names and folders a server or the user controls:**
   - suggested names `../../.ssh/authorized_keys`, `a\b.txt`, `""`, `..`, `CON.txt`, `name. ` and 300 characters;
   - two downloads of one name at once;
   - a Downloads folder that is missing, is a file, or cannot be written.

   Each file lands inside the folder under a free name, or the download fails with its notification. Tasks 2 and 3 pin
   this.
3. **Lifetimes:**
   - closing a browser item mid-download lets the download finish, with its page silenced;
   - quitting stops every running download;
   - an interrupted download is reported once and never resumed;
   - Stop gives "Stopped" without a notification.

   Tasks 3 and 4 pin this.
4. **Save Page As on awkward pages:**
   - a page larger than one bridge request;
   - resources that fail, time out or pass the caps (they stay absolute, and the save finishes);
   - one URL used twice, CSS `url()` and `@import`, `srcset`, `data:` URLs, `<base href>` and `&` in a URL;
   - a chosen folder that exists, or is a file.

   Task 7 pins this.
5. **Page tools across navigation and keys:**
   - a load start resets reader mode, distraction removal and the reader offer;
   - Escape in the page ends either mode, and the ☰ label follows;
   - a page with 10 characters of reader text, a `jasper-about:` page, or a malformed report offers neither item.

   Tasks 5 and 6 pin this.

---

### Task 1: SDK 0.N.0: menu entries, the Downloads folder and the downloaded-file mark

**Files:**
- Modify: `jasper-sdk/src/main/java/dev/jasper/sdk/JasperSdk.java` (`VERSION`), every bundled `plugin.toml`'s `sdk`
  range, `docs/examples/external-plugin`, the SDK README's compatibility table, and the guide pages that describe menus
  and `Platform` (`tools/sdk-reference/guide/`), as 4a's bump did.
- Modify: `jasper-sdk/src/main/java/dev/jasper/sdk/ui/PluginMenu.java` and `Platform.java`. Create `MenuEntries.java`.
- Modify, in the app:
  - `jasper-app/.../contributions/MenuEntry.java`: add `Command(String key, String label, boolean enabled, Runnable
    chosen)`, and give `Submenu` a `key`, `""` for action submenus;
  - `plugins/HostedMenu.java`: an entries child, snapshotted as `Command`s and keyed `Submenu`s;
  - `workspace/WindowChrome.java` (`build`, `renderContributedMenus`): commands through `PlainText`, in-place updates,
    and waiting while a menu is open;
  - `plugins/HostedContext.java` (`platform()`, :205): the two `Platform` calls.
- Create: `jasper-app/src/main/java/dev/jasper/app/platform/DownloadsFolder.java` and `DownloadMark.java`.
- Modify: `jasper-sdk-testkit/.../FakeUi.java` (`Menu`), `FakePluginContext.java` (`platform()`, :120),
  `FakePluginHost.java`, and the contract suite.
- Modify: `docs/plugin-authoring.md` (menus) and `docs/sdk-reference.html` (regenerated).
- Test: `PluginContractTest` (both runtimes), `jasper-app/src/test/java/dev/jasper/app/workspace/WindowChromeMenuEntriesTest.java`,
  and `platform/DownloadsFolderTest.java` and `DownloadMarkTest.java`.

**Interfaces** (all `@since 0.N.0`):
- **`MenuEntries PluginMenu.addEntries(List<MenuItem> items, Consumer<String> chosen)`** appends entries that are not
  actions. The default throws `UnsupportedOperationException`.
  - **What shows:** each item's label and enabled state, separators, and 4b's `children` as submenus titled by the
    label. Icons, hues and `action` are not shown.
  - **Choosing:** a leaf calls `chosen` with its `id`, on the UI thread, inside the plugin's containment.
  - **What they are not:** never in the palette or the keymap, and they show no key.
  - **Refused** with `IllegalArgumentException`: a repeated id anywhere in the tree, and a non-empty `shortcut`. A
    `Menus.terminalContext()` section, or a submenu of one, throws `IllegalStateException`.
  - **Labels are plain text:** one starting with `<html>` shows as typed.
- **`interface MenuEntries extends Subscription`,** with `void set(List<MenuItem> items)`: it replaces the entries where
  they stand, with the same checks. Closing it removes them. UI thread.
- **The host's rule:**
  - a `set` that changes only labels and enabled states, with the same ids, separators and nesting in the same order,
    updates each window's shown items in place;
  - any other change waits while that window has a menu open (`MenuSelectionManager`), then applies once it closes.
- **`Path Platform.downloadsFolder()`** returns an absolute path, which may not exist yet. Any thread. The default
  throws.
  - **macOS:** `~/Downloads`.
  - **Windows:** `FOLDERID_Downloads`, else `%USERPROFILE%\Downloads`.
  - **Linux:** `XDG_DOWNLOAD_DIR` from `$XDG_CONFIG_HOME/user-dirs.dirs` (default `~/.config/user-dirs.dirs`), with
    `$HOME` expanded, else `~/Downloads`.
- **`void Platform.markDownloaded(Path file, URI source, Optional<URI> referrer) throws IOException`** gives a
  downloaded file the system's downloaded-file mark. It does file work, so plugins call it off the UI thread.
  - **macOS:** `com.apple.quarantine`, written with libc's `setxattr` through JNA (`XATTR_NOFOLLOW`), value
    `DownloadMark.quarantine(epochSeconds, uuid)` = `"0081;%08x;Jasper;%s"`, with the UUID upper-case.
  - **Windows:** the `Zone.Identifier` stream, through `UserDefinedFileAttributeView`. Its value is
    `DownloadMark.zoneIdentifier(source, referrer)` = `"[ZoneTransfer]\r\nZoneId=3\r\n"`, then `ReferrerUrl=…\r\n`
    when there is a referrer, then `HostUrl=…\r\n`.
  - **URLs:** without user info; a URL that is not http(s) is written as `about:internet`.
  - **Linux:** nothing.
  - **Every platform:** a mark already there is kept. A missing file throws `NoSuchFileException`. A failure throws
    `IOException` with the system's reason.
- **The testkit:**
  - `FakePluginHost.chooseMenuEntry(String entryId)`;
  - `setDownloadsFolder(Path)`, whose default is `<fake home>/Downloads`;
  - `markedDownloads()`, a `List<MarkedDownload(Path file, URI source, Optional<URI> referrer)>`;
  - `failMarks(IOException)`.
- **The app's seams:**
  - `DownloadsFolder.of(String osName, Path home, Map<String, String> env, Function<Path, Optional<String>> read,
    Supplier<Optional<Path>> knownFolder)`;
  - `DownloadMark.apply(Path file, URI source, Optional<URI> referrer, String osName)`.

- [ ] **Step 1: Write the failing tests.**
  - **Contract cases:**
    - `menuEntriesShowTheirLabelsAndReportTheirIds`: Clear All, then a submenu `a.zip — 40%` holding Stop and a
      disabled Open; choosing Stop reaches `chosen` with its id;
    - `menuEntriesAreNotActions`: they are in neither the palette nor the keymap;
    - `settingEntriesKeepsTheirPlace`: action A, the entries, action B; after `set`, the order is still A, the new
      entries, B;
    - `aRepeatedIdOrAShortcutIsRefused`, `aTerminalContextSectionRefusesEntries` and `closingEntriesRemovesThem`;
    - `theDownloadsFolderIsAbsolute` and `markingAMissingFileThrowsNoSuchFile`.
  - **`WindowChromeMenuEntriesTest`:**
    - `aLabelChangeUpdatesTheShownItemInPlace`: the same `JMenuItem` instance, with the new text;
    - `aStructureChangeWaitsWhileAMenuIsOpen`: no rebuild while the selection path is set, and one after it clears;
    - `aLabelThatStartsWithHtmlShowsAsText`;
    - `anEntryThatThrowsBecomesThePluginsIncident`.
  - **`DownloadsFolderTest`:**
    - macOS gives `home/Downloads`;
    - Linux reads `XDG_DOWNLOAD_DIR="$HOME/Téléchargements"`, an absolute `"/data/dl"`, a commented line, a missing
      file, a malformed value and `XDG_CONFIG_HOME`;
    - Windows gives the known folder, and `home\Downloads` when it fails.
  - **`DownloadMarkTest`:**
    - the value formats, with user info dropped and `data:` written as `about:internet`;
    - `@EnabledOnOs(MAC)`: `/usr/bin/xattr -p com.apple.quarantine` reads `0081;<8 hex>;Jasper;<UUID>`, an existing
      value is kept, and a file without write permission throws;
    - `@EnabledOnOs(WINDOWS)`: the stream's exact text;
    - `@EnabledOnOs(LINUX)`: the file is unchanged.

- [ ] **Step 2: Run** `./gradlew :jasper-sdk:test :jasper-sdk-testkit:test :jasper-app:test --tests '*ContractTest*'
  --tests '*WindowChromeMenuEntriesTest' --tests '*DownloadsFolderTest' --tests '*DownloadMarkTest'`. Expected: FAIL.

- [ ] **Step 3: Implement** the members, the host side, the fakes and the version bump. Add the `migration.csv` rows
  that the theme migration coverage test asks for.

- [ ] **Step 4: Run** `./gradlew sdkReference`, then `./gradlew check`, then
  `./gradlew -p docs/examples/external-plugin -PjasperSdkDir="$PWD/build/sdk-release" check pluginZip`. Expected: PASS.

- [ ] **Step 5: Commit** "SDK 0.N.0: menu entries, the Downloads folder and the downloaded-file mark".

### Task 2: The downloads core: names, the folder setting, the list and its menu

**Files:**
- Create, under `plugins/browser/src/main/java/dev/jasper/browser/downloads/`: `DownloadNames.java`,
  `DownloadFolder.java`, `DownloadKey.java`, `Download.java`, `DownloadList.java`, `DownloadsMenu.java` and
  `package-info.java`.
- Create: `plugins/browser/src/main/java/dev/jasper/browser/page/DownloadProgress.java`, a plain record that Task 3's
  handler fills.
- Modify: `settings/BrowserSettings.java` and `settings.toml`: `downloads_folder`, seeded commented out.
- Test: `DownloadNamesTest`, `DownloadFolderTest`, `DownloadListTest`, `DownloadsMenuTest` and `BrowserSettingsTest`.

**Interfaces:**
- **`DownloadNames`:**
  - `static String safeName(String suggested)`:
    - keeps the part after the last `/` or `\`, and removes control characters;
    - trims spaces and trailing dots;
    - empty, `.` or `..` become `download`;
    - a Windows device name before the first dot (`CON`, `NUL`, `COM1`…) gets a leading `_`;
    - over 200 characters, it is cut, keeping the extension.
  - `static String numbered(String name, int n)` follows iTerm2, numbering before the last extension:
    - `report.pdf` and 1 give `report (1).pdf`;
    - `archive.tar.gz` and 2 give `archive.tar (2).gz`;
    - `README` gives `README (1)`, and `.bashrc` gives `.bashrc (1)`.
  - `synchronized Path reserve(Path folder, String name)` returns the first free name: free on disk
    (`NOFOLLOW_LINKS`) and not reserved. The result's parent is always `folder`.
  - `synchronized void release(Path file)`.
- **`DownloadFolder`:**
  - `static Path resolve(String setting, Path system, Path home)`: `""` is `system`, `~` and `~/x` are home-relative,
    and an absolute path is as typed;
  - `static boolean valid(String setting)`: one of those forms.
- **The setting** `downloads_folder`, text, default `""`:
  - in General, after Page zoom;
  - label "Downloads folder", hint `EditorHint.directory()`;
  - description "Where downloaded files go, without asking. Empty uses the system's Downloads folder.";
  - refusal "Enter an absolute folder path, or leave it empty.";
  - `Declared.downloadsFolder()` is the `Setting<String>`.
- **The values:**
  - `record DownloadKey(Object store, int id)`: Chromium's id, within the data store that made it (plan 6's
    in-memory stores have their own ids);
  - `record page.DownloadProgress(Object store, int id, boolean inProgress, boolean complete, boolean canceled, long
    received, long total, String fullPath)`;
  - `record Download(long serial, DownloadKey key, String name, Path file, URI source, State state, long received,
    long total)`, with `enum State { RUNNING, FINISHED, FAILED, STOPPED }`.
- **`DownloadList`** (UI thread) keeps downloads in the order they started:
  - `Download begin(DownloadKey key, String name, Path file, URI source)`;
  - `Optional<Outcome> update(DownloadKey key, DownloadProgress p)`, with `enum Outcome { COMPLETED, FAILED,
    STOPPED }`:
    - each download reports one outcome, and later updates are ignored;
    - a completed one stays `RUNNING` at 100% until `finish(key)`, which Task 3 calls once it is marked;
    - an interrupted one is `FAILED`, and a cancelled one is `STOPPED`;
    - a non-empty `fullPath` replaces `file` and `name`;
    - an unknown key is ignored;
  - `void finish(DownloadKey key)`, `boolean remove(long serial)` (false while running), `void clearAll()` (keeps the
    running ones), and `List<Download> all()`.
- **`DownloadsMenu`:**
  - **`static List<MenuItem> items(List<Download> all, String showLabel)`:**
    - **Clear All** (`downloads.clear-all`), enabled when a download is not running;
    - a separator when there are downloads;
    - one submenu per download (`downloads.<serial>`), titled `title(d)`. Its entries are **Stop** (only while
      running), **Open**, `showLabel` and **Remove from List**: Open and Show only when finished, and Remove not while
      running. Their ids are `downloads.<serial>.stop`, `.open`, `.show` and `.remove`.
  - **`static String title(Download d)`:**
    - while running, `name — 42%`, or `name — 1.2 MB` when the size is unknown;
    - then `name — Finished`, `name — Failed` or `name — Stopped`;
    - a name over 60 characters keeps its first and last 28, joined by "…".
  - **`static String size(long bytes)`**, in decimal units: `999 bytes`, `2 KB` for 1,500, `1.2 MB` for 1,234,567 and
    `5.0 GB`.
  - **`static String showLabel(String osName)`:** "Show in Finder", "Show in Explorer" or "Show in Folder".

- [ ] **Step 1: Write the failing tests:**
  - `DownloadNamesTest`: Review Focus 2's names; the `numbered` rows; a clash on disk and two reservations at once give
    `(1)` and `(2)`; release frees a name;
  - `DownloadFolderTest`: each form, and `relative/dir` refused;
  - `DownloadListTest`: every outcome, one each; `finish`; remove and Clear All with a running download;
  - `DownloadsMenuTest`: every state's submenu, the order, the ids, the titles and sizes above, and eliding;
  - `BrowserSettingsTest`: the row.

- [ ] **Step 2: Run** `./gradlew :jasper-plugin-browser:test`. Expected: FAIL.

- [ ] **Step 3: Implement.**

- [ ] **Step 4: Run** `./gradlew :jasper-plugin-browser:test`. Expected: PASS.

- [ ] **Step 5: Commit** "Browser: download names, the Downloads folder setting and the list".

### Task 3: Chromium's downloads, the Downloads menu, notifications and the mark

**Files:**
- Create: `plugins/browser/src/main/java/dev/jasper/browser/page/PageDownloads.java` (the `CefDownloadHandler`),
  `DownloadStart.java` and `DownloadTarget.java`.
- Create: `plugins/browser/src/main/java/dev/jasper/browser/downloads/Downloads.java` and `FileManager.java`.
- Modify: `BrowserPlugin.java`:
  - declare the group and make `Downloads` at start;
  - call `PageDownloads.prepare(...)` in the page starter, beside `PageNavigation.prepare`;
  - in `stop()`, call `downloads.stopAll()` before `chromium.close()`.
- Test: `PageDownloadsTest.java` and `DownloadsTest.java`.

**Interfaces:**
- **`page`:**
  - `record DownloadStart(Object store, int id, String suggestedName, String url, String pageUrl, Optional<Page>
    page, boolean ask)`, where `ask` is false until Task 4 arms it;
  - `record DownloadTarget(Path file, boolean ask)`;
  - `static void PageDownloads.prepare(Chromium chromium, Function<DownloadStart, Optional<DownloadTarget>> before,
    BiConsumer<DownloadProgress, Runnable> updated)` adds one handler per client. Both run on Chromium's thread.
    - `store` is `browser.getRequestContext()`, or a shared constant for the global one, and `pageUrl` is
      `browser.getURL()`.
    - An empty answer returns false and never continues. Otherwise the handler calls `Continue(file, ask)` once and
      returns true.
    - `updated` gets the update and the item callback's `cancel`.
- **`Downloads`** (cef-free):
  - `Downloads(PluginContext context, Setting<String> folder, FileManager files)`;
  - **`Optional<DownloadTarget> before(DownloadStart s)`,** on Chromium's thread. It creates the folder
    (`DownloadFolder.resolve` over `platform().downloadsFolder()`) and reserves `safeName(suggested)`. When the folder
    cannot be made, it posts "Download failed for “NAME”." with the reason and answers empty. An `ask` start reserves
    nothing and answers `<folder>/<safe name>` with `ask = true`; the name the user chooses arrives as `fullPath`.
  - **`Path folder()`:** the resolved Downloads folder, from any thread.
  - **`void updated(DownloadProgress p, Runnable cancel)`** keeps `cancel` and posts the update to the UI thread:
    - a download joins the list, and so the menu, at its first update;
    - each change sets the menu's entries.
  - **The menu:** the first download creates `menus().create("dev.jasper.browser.downloads", "Downloads")` and adds
    `DownloadsMenu.items(...)` with `addEntries`. Later changes call `set`.
  - **The outcomes:**
    - **`COMPLETED`:** `markDownloaded(file, source, referrer = pageUrl)` runs on `context.background()`. Then, on the
      UI thread, `finish`, the info "Download finished for “NAME”.", and, if the mark failed, the warning "“NAME” has
      no downloaded-file mark" / "Jasper could not mark it as downloaded from the internet, so the system may open it
      without checking it first: REASON".
    - **`FAILED`:** `cancel` runs, so Chromium never resumes it, and the warning "Download failed for “NAME”." is
      posted.
    - **`STOPPED`:** nothing is posted.
    - **Every outcome** releases the reserved name.
  - **The entries:**
    - Stop runs the kept `cancel`;
    - Open is `files.open(file)`, and Show is `files.reveal(file)`;
    - Remove from List and Clear All change the list.
  - **`void stopAll()`** cancels every running download.
  - **Notifications** go to `GroupSpec("dev.jasper.browser.downloads", "Downloads", DisplayMode.CARD)`, declared at
    start. NAME is the file's name, elided as in titles.
- **`FileManager(Executor background, BiConsumer<String, String> failed)`,** with `reveal(Path)`
  (`Desktop.browseFileDirectory` if supported, else `Desktop.open` of the parent) and `open(Path)` (`Desktop.open`).
  `failed` hears a title and a reason.
  - Both run on `background`.
  - A failure posts "Could not open “NAME”" or "Could not show “NAME”", with the reason.
  - A package-private constructor takes recording seams for tests.

- [ ] **Step 1: Write the failing tests.**
  - **`PageDownloadsTest`,** with test implementations of `CefDownloadItem` and `CefBeforeDownloadCallback`, which are
    interfaces, so nothing starts:
    - a refused start returns false and never continues;
    - an accepted one continues once, with the path and `ask`, and returns true.
  - **`DownloadsTest`** (FakePluginContext):
    - the file goes into the folder without asking, and a missing folder is created;
    - a folder that is a file refuses the download and posts the failure with its reason;
    - an `ask` start reserves nothing and continues with `ask`;
    - no menu exists before the first update, and one does after it;
    - progress retitles the entry;
    - a completion is marked with the page's URL as referrer, then notified, and Open is enabled;
    - a mark failure (`failMarks`) warns;
    - an interrupted download is cancelled once and posts one warning;
    - Stop gives Stopped with no notification;
    - Open and Show reach the seams;
    - Remove and Clear All work;
    - `stopAll` cancels only the running downloads.

- [ ] **Step 2: Run** `./gradlew :jasper-plugin-browser:test`. Expected: FAIL.

- [ ] **Step 3: Implement.**

- [ ] **Step 4: Run** `./gradlew :jasper-plugin-browser:check verifyPluginArchitecture`. Expected: PASS, including
  `ChromiumBoundaryTest` and `StartLoadsNoChromiumTest`.

- [ ] **Step 5: Commit** "Browser: downloads, the Downloads menu and the downloaded-file mark".

### Task 4: A download and its page: Alt-click, Save Link As… and closing

**Files:**
- Modify: `page/PageView.java` (`processMouseEvent`, :285-302) and `page/Page.java` (`close()`, :405-423).
- Create: `page/DownloadAsk.java`.
- Modify: `page/PageDownloads.java` (`ask` from the page), and 4b's `page/PageMenu.java`: Save Link As… and Save Image
  As… call `page.askNextDownload()` before `startDownload(url)`.
- Modify: `downloads/Downloads.java` and the page starter in `BrowserPlugin.java`, which calls
  `page.setCloseHold(downloads::drained)` before `page.start`.
- Test: `PageViewMouseTest`, `PageCloseTest`, `DownloadAskTest` and `DownloadsTest`.

**Interfaces:**
- **Alt-click loads in place** (spec section 8). Chromium makes an Alt-click a download, so `PageView` sends the click
  without Alt: the press, its drags and its release, with `ALT_DOWN_MASK` and `ALT_MASK` cleared. That applies to a
  button-1 click with Alt held, without Cmd (macOS) or Ctrl (elsewhere), while the hover URL is not empty. `LastPress`
  keeps the real modifiers, so 4a's gestures still read Alt-click as `SAME_ITEM`. This replaces the one-shot guard in
  the handler that 4a's banner suggested: a download cancelled after `Continue` can already have written a small file,
  and one never continued goes on into the temp folder.
- **Save Link As… and Save Image As… ask.** `Page.askNextDownload()` (UI thread) arms the page's `DownloadAsk`, and
  `PageDownloads` takes it on Chromium's thread into `DownloadStart.ask`. The asking lives in `page` because 4b's
  `PageMenu` is there, and `downloads` already depends on `page`.
  - **`DownloadAsk`:** `synchronized void arm(long nowMillis)` and `synchronized boolean take(long nowMillis)`. It
    is true once, for the first start within 10 s of arming.
  - **The chooser:** 4a's `onFileDialog` (`FILE_DIALOG_SAVE`) asks with Jasper's save chooser, starting at
    `<folder>/<safe name>`. Cancelling it cancels the download in Chromium.
  - *(call: Chromium's Save Link As asks; every other download goes without asking.)*
- **`CompletableFuture<Void> Downloads.drained(Page page)`** is complete when the page has no running download. A
  download counts for its page from its first update to its outcome, on any thread, so one cancelled in the chooser
  never holds a browser.
- **`void Page.setCloseHold(Function<Page, CompletableFuture<Void>> hold)`:** `close()` still does everything at once,
  except closing the browser.
  - If the hold is not complete, the browser first loads `about:blank`, which ends the page's scripts and sound but not
    its downloads.
  - It then closes when the hold completes, on the UI thread, and once.
  - Jasper's quit (`stopAll`, then `Chromium.close()`) ends held browsers too.

- [ ] **Step 1: Write the failing tests:**
  - `PageViewMouseTest.anAltClickOnALinkReachesThePageWithoutAlt`,
    `anAltClickWithNothingUnderThePointerKeepsAlt` and `theLastPressKeepsAlt`;
  - `PageCloseTest.aPageWithRunningDownloadsKeepsItsBrowserUntilTheyEnd`: the recording browser loads `about:blank`,
    stays open, then closes once when the hold completes;
  - `DownloadAskTest.itAsksOnceWithinTenSeconds`: the first `take` after `arm` is true and the second false, and a
    `take` 10,001 ms after `arm` is false;
  - `DownloadsTest.drainedCompletesWhenThePagesLastDownloadEnds`, and is already complete for a page with none.

- [ ] **Step 2: Run** the tests. Expected: FAIL.

- [ ] **Step 3: Implement.**

- [ ] **Step 4: Run** `./gradlew :jasper-plugin-browser:test`. Expected: PASS.

- [ ] **Step 5: Commit** "Browser: Alt-click loads in place, Save Link As asks, and closing a tab keeps its downloads".

### Task 5: Readability.js and reader mode

**Files:**
- Create: `plugins/browser/src/main/resources/dev/jasper/browser/third_party/Readability.js`, the unmodified
  `Readability.js` of `@mozilla/readability` 0.6.0 (the npm tarball's `package/Readability.js`), with its licence header.
- Create: `plugins/browser/src/main/resources/dev/jasper/browser/tools/reader.js`.
- Create, under `plugins/browser/src/main/java/dev/jasper/browser/tools/`: `PageTools.java`, `ToolScripts.java` and
  `package-info.java`.
- Modify: `item/BrowserItem.java` (a `PageTools` per item; `act` for the two ids; the load start, load end and address
  hooks) and `item/BrowserToolbar.java` (the tool items first in ☰).
- Modify: the bridge's op routing: ops `reader` and `distractions` go to the page's item's `PageTools.reported`.
- Modify: `notices.toml`, `gradle/third-party-notices.gradle.kts` (:15, :49-58), AGENTS.md's notices sentence and
  `docs/app-maintenance.md#adding-or-bumping-a-dependency`. A bundled plugin's files under `third_party/` must be
  listed in a component's `files`, as its fonts are.
- Test: `PageToolsTest`, `ToolScriptsTest`, `BrowserToolbarTest` and `ThirdPartyNoticesTest`.

**Interfaces:**
- **The notice:**

  ```toml
  [[component]]
  name = "Readability.js"
  version = "0.6.0"
  license = "Apache-2.0"
  homepage = "https://github.com/mozilla/readability"
  files = ["dev.jasper.browser/dev/jasper/browser/third_party/Readability.js"]
  text = "apache-2.0.txt"
  ```
- **`PageTools(Consumer<String> scripts, Supplier<String> readerCss, Runnable changed)`** (UI thread). `scripts` runs
  a script in the page's top frame (`page.run`), and `readerCss` is 4b's theme CSS helper's output:
  - `void loadStarted(String url)` ends both modes, withdraws the offer and notes whether the page is a
    `jasper-about:` page;
  - `void loaded()`, when the page stops loading, and again on each later main-frame address change, runs
    `ToolScripts.readerCheck()`, but not on `jasper-about:` pages;
  - `void reported(String op, String json)`:
    - `reader {"length": n}` offers the items when `n > 10`;
    - `reader {"state": "off"}` ends reader mode, and `distractions {"state": "off"}` ends distraction removal;
    - anything else is ignored, because page requests are untrusted (R38);
  - `void toggleReader()` runs `readerShow(css)` or `readerHide()`;
  - `List<MenuItem> menuItems()` gives **Reader Mode** or **Exit Reader Mode** (`browser.reader`), **Remove
    Distractions** or **Exit Distraction Removal** (`browser.distractions`), and a separator.
    - They show while the offer stands or a mode is on, and otherwise there are none.
    - They come first in ☰, before plans 5 and 6's items.
  - `boolean offered()` and `reading()`.
- **`ToolScripts`:**
  - **`readerCheck()`:** Readability.js inside `(function (module) { … })(undefined)`, so a page's own `module`
    stays untouched, kept under `window[Symbol.for("dev.jasper.browser.readability")]`, then `reader.js`'s check.
  - **`readerShow(String css)`:** CSS goes in as an escaped JS string literal. `readerHide()` hides the overlay.
  - **Script URLs:** `jasper-about:reader.js`.
- **`reader.js`:**
  - **The check:** `new Readability(document.cloneNode(true), {maxElemsToParse: 50000}).parse()` in
    `requestIdleCallback`, reporting the length of its `textContent` with whitespace collapsed (0 on failure).
  - **The overlay:**
    - a fixed, full-viewport element on `document.documentElement` with an open shadow root and the topmost z-index;
    - its own scrolling, with the page's scrolling held until it closes;
    - the article's title, or "Article", then its content, in Jasper's theme colours *(call: as Jasper's internal
      pages)*.
  - **Escape:** a capture-phase `keydown` for Escape closes it, stops the event and reports `reader {"state":"off"}`.

- [ ] **Step 1: Write the failing tests:**
  - **`PageToolsTest`:**
    - 11 characters offer the items, and 10 do not;
    - a `jasper-about:` page runs no check;
    - a load start resets everything;
    - the toggle runs show, then hide;
    - a reported exit ends reader mode;
    - the labels and their order, and none while nothing is offered;
    - a malformed report is ignored.
  - **`ToolScriptsTest`:**
    - the check holds Readability.js verbatim (its first licence line) inside the `module` shadow, and the Symbol;
    - CSS containing `"`, `\` and `</script>` stays one string literal.
  - **`BrowserToolbarTest`:** the tool items come first in ☰.
  - **`ThirdPartyNoticesTest`:** the Readability.js component's version and licence.

- [ ] **Step 2: Run** the tests. Expected: FAIL.

- [ ] **Step 3: Implement.** Download Readability.js 0.6.0 (the user approved downloads for this series).

- [ ] **Step 4: Run** `./gradlew :jasper-plugin-browser:test :jasper-app:test --tests '*ThirdPartyNoticesTest'
  verifyThirdPartyNotices`. Expected: PASS. Then remove the `files` line, run `verifyThirdPartyNotices` and see it fail
  naming the script, and restore the line.

- [ ] **Step 5: Commit** "Browser: reader mode with Readability.js".

### Task 6: Distraction removal and Remove Element

**Files:**
- Create: `plugins/browser/src/main/resources/dev/jasper/browser/tools/distractions.js`.
- Modify: `tools/PageTools.java` and `ToolScripts.java`, plus 4b's `PageMenuItems.java` and `PageMenu.java` (the item
  `page.remove-element`, which the page's item runs, as it runs 4b's View Source).
- Test: `PageToolsTest` and `PageMenuItemsTest`.

**Interfaces:**
- **`PageTools.toggleDistractions()`** runs `distractionsStart()` or `distractionsStop()`. Starting either mode ends
  the other *(call)*.
- **`void PageTools.removeElementAt(int x, int y, int zoomPercent)`** runs `removeAt(x * 100.0 / zoomPercent, y *
  100.0 / zoomPercent)`. The point is where 4b's `PageMenu` showed the menu, in the page view's coordinates, and
  `zoomPercent` is the item's 4b zoom; CSS pixels are view pixels divided by the zoom.
- **`distractions.js`,** under `window[Symbol.for("dev.jasper.browser.distractions")]`:
  - **The overlay of an element:** its outermost ancestor-or-self below `body` whose computed `position` is `fixed` or
    `sticky`, else the element itself unless it is `html` or `body`.
  - **`start()`:**
    - adds a style with `cursor: crosshair !important` on everything, and `outline: 2px solid #FF0000 !important` on
      the hovered element's overlay;
    - a capture-phase pointer press, release and click are stopped, and the click hides that overlay and its
      backdrops: every other fixed element that covers at least 90% of the viewport and is neither inside the overlay
      nor around it;
    - Escape (capture phase, stopped) ends it and reports `distractions {"state":"off"}`.
  - **`stop()`** removes the style and listeners. Hidden elements stay hidden (`display: none !important`).
  - **`removeAt(x, y)`** hides the overlay of `document.elementFromPoint(x, y)`, or nothing.
- **The right-click menu:** **Remove Element** (`page.remove-element`) sits between View Source and Inspect Element.

- [ ] **Step 1: Write the failing tests:**
  - **`PageToolsTest`:**
    - starting one mode ends the other;
    - a reported exit ends distraction removal;
    - (300, 150) at 150% runs `removeAt(200.0, 100.0)`;
    - the labels follow the state.
  - **`PageMenuItemsTest`:** the group order is View Source, Remove Element, Inspect Element.

- [ ] **Step 2: Run** the tests. Expected: FAIL.

- [ ] **Step 3: Implement.**

- [ ] **Step 4: Run** `./gradlew :jasper-plugin-browser:test`. Expected: PASS.

- [ ] **Step 5: Commit** "Browser: distraction removal and Remove Element".

### Task 7: Save Page As…

**Files:**
- Create: `plugins/browser/src/main/resources/dev/jasper/browser/tools/save-page.js`.
- Create, under `tools/`: `SavePage.java`, `SavePayload.java`, `PageSaver.java`, `CssLinks.java` and
  `ResourceNames.java`.
- Modify: `tools/ToolScripts.java` (`savePage`), `item/BrowserItem.java` (the pending save), 4b's
  `PageMenuItems.java` and `PageMenu.java` (the item `page.save`, first in the group before Print…, which the page's
  item runs), and the bridge's op routing (op `savePage` goes to the item's pending save).
- Test: `ResourceNamesTest`, `CssLinksTest`, `SavePayloadTest`, `PageSaverTest` (a `com.sun.net.httpserver` fixture)
  and `SavePageTest` (FakeUi).

**Interfaces:**
- **`static SavePage SavePage.start(Consumer<String> scripts, URI page, ContentTab tab, PluginContext context, Path
  startFolder, FileManager files)`,** with `startFolder` being `Downloads.folder()`. The item keeps the pending save
  and hands it `void part(String json)` and `void loadStarted()`. UI thread.
- **The flow:**
  1. `chooseSaveFile(tab, "Save Page As", Optional.of(startFolder.resolve(ResourceNames.folderFor(page))))`.
     Cancel does nothing.
  2. `ToolScripts.savePage(prefix)` runs in the top frame. `prefix` is `jasper-save-` plus 16 random hex digits plus
     `-`.
  3. `PageSaver.save` runs on `context.background()`.
  4. Then `FileManager.reveal(folder)`.
  5. A failure shows the dialog "Save Failed" / "Could not save the page: REASON", with OK, over the item, or as a
     warning notification if the tab has closed.
  - **Failures before saving:** a load start while collecting fails with "the page navigated away", and 30 s without
    the last part fails with "the page did not answer".
- **`save-page.js`** works on a clone of `document.documentElement`:
  - **Tokens:** every http(s) reference becomes `<prefix>r<N>`, where N indexes `resources`. That covers `img` `src`
    and `srcset` candidates, `source` `src` and `srcset`, `video` `src` and `poster`, `audio`, `track`,
    `input[type=image]`, `script[src]`, and `link[href]` whose `rel` has `stylesheet` or an icon.
  - **Styles:** each `<style>` text becomes `<prefix>s<K>` and each `style` attribute `<prefix>a<K>`.
  - **Rewritten elements** lose `integrity` and `crossorigin`. Other `href`, `src` and `action` values become
    absolute, `data:` stays, and frames stay remote.
  - **The document:** `<base>` and charset metas are removed, `<meta charset="utf-8">` comes first in `head`, and the
    page's doctype is kept.
  - **The answer** is `{prefix, html, resources: [{url, kind}], styles: [...], attributes: [...], base}`, with `kind`
    one of `style`, `script` or `other`. It goes in parts of at most 524,288 characters, op `savePage` `{save: prefix,
    part, parts, text}`, sent one at a time.
- **`SavePayload`:** `SavePayload(String prefix)`, `Optional<Snapshot> add(String json)`.
  - It ignores another prefix, refuses more than 64 parts or an index out of order, and parses with `BridgeJson`.
  - `record Snapshot(String html, List<Resource> resources, List<String> styles, List<String> attributes, URI base)`,
    with `record Resource(URI url, Kind kind)` and `enum Kind { STYLE, SCRIPT, OTHER }`.
- **`PageSaver.save(Path folder, Snapshot s, HttpClient http) throws IOException`:**
  - **The folder:** a regular file at `folder` throws "`folder` is a file". An existing folder is saved into, replacing
    same-named files.
  - **Fetching:**
    - only http(s), with no cookies (`HttpClient` without a cookie handler) *(call: as View Source refetches)*,
      redirects followed, the page's origin as `Referer`, 4 at a time and 15 s each;
    - only a 200 response is used;
    - caps: 20 MiB each, 500 resources and 200 MiB in all;
    - one URL is fetched once.
  - **Stylesheets** (kind `style`, or `text/css`) have their `url()` and `@import` references queued.
  - **Writing:**
    1. every outcome is settled first;
    2. stylesheets are rewritten with bare names in `resources/`;
    3. styles and attributes are rewritten with `resources/` names, attribute text escaped (`&`, `"`, `<`, `>`);
    4. tokens are replaced: `r` with `resources/<name>`, or the escaped absolute URL when it failed or passed a cap;
    5. `index.html` is written last, in UTF-8.
- **`static String CssLinks.rewrite(String css, URI base, Function<URI, Optional<String>> local)`** and
  **`static List<URI> CssLinks.references(String css, URI base)`:**
  - they handle quoted and bare `url()`, `@import "…"` and `@import url(…)`;
  - `data:` and `#fragment` references are left alone;
  - relative references resolve against `base`.
- **`ResourceNames`:**
  - **`String name(URI url, Kind kind)`,** per save:
    - the last path segment, percent-decoded, with characters outside `[A-Za-z0-9._-]` made `_`;
    - at most 80 characters with the extension kept, and `resource` when empty;
    - `.css` or `.js` added to a style or script that lacks it;
    - Windows device names get `_`;
    - unique without regard to case as `name.ext`, `name-1.ext`, and so on.
  - **`static String folderFor(URI page)`:** the host by the same rule (IPv6 without brackets), else `page`.

- [ ] **Step 1: Write the failing tests:**
  - `ResourceNamesTest`: the rules above, `logo.PNG` against `logo.png`, `folderFor` for `https://user:pw@example.com/a`
    (giving `example.com`), `file:///x` and `[::1]`;
  - `CssLinksTest`: each reference form, both left-alone kinds, relative resolution, and a failed reference kept
    absolute;
  - `SavePayloadTest`: parts in order, a foreign prefix ignored, 65 parts refused, malformed JSON failing;
  - `PageSaverTest`: Review Focus 4's cases against the fixture; `index.html`'s text; one file for a URL used twice; a
    404 and a 20 s stall left absolute; a `url()` inside a fetched stylesheet saved and named bare; `ftp:` not fetched;
    `?a=1&b=2` written as `&amp;`;
  - `SavePageTest`: the chooser's title and starting path; Cancel; success revealing the folder; the failure dialog's
    text; "the page navigated away".

- [ ] **Step 2: Run** the tests. Expected: FAIL.

- [ ] **Step 3: Implement.**

- [ ] **Step 4: Run** `./gradlew :jasper-plugin-browser:check`. Expected: PASS.

- [ ] **Step 5: Commit** "Browser: Save Page As saves a folder with the page and its resources".

### Task 8: Docs and the check

**Files:**
- Modify:
  - `docs/browser.md`:
    - a Downloads section: where files go, names, the mark per platform, the menu, notifications, Alt-click, Save Link
      As…, closing a tab and quitting;
    - a Page tools section: reader mode, distraction removal, Remove Element, and Save Page As… with its limits (no
      cookies for resources, frames stay remote, local folders only);
    - the Downloads folder row in Settings;
  - `docs/STATUS.md`, with the plan's entry;
  - this plan's status banner.

- [ ] **Step 1: Write the docs,** documenting only what the code does (AGENTS.md's documentation rules).

- [ ] **Step 2: Run** the source hygiene script, `./gradlew sdkReference`, `./gradlew check` and the external-plugin
  check. Expected: PASS. Record the counts from the XML.

- [ ] **Step 3: Commit** "Docs: the Browser's downloads and page tools".

### Task 9: The user's GUI check, with their OK (pending; it blocks nothing)

**Files:**
- Modify: `docs/browser-verification.md`, a plan 7 section.

- [ ] **Step 1: Check on macOS** (`:jasper-app:run` from the worktree, with the user's OK for the batch). Each line is
  recorded pass or fail:
  1. **The first download:** a large file goes to `~/Downloads` without asking. The Downloads menu appears with it,
     and its title shows the progress.
  2. **The menu:** with the menu open during progress, Stop works. Then Open, Show in Finder, Remove from List and
     Clear All.
  3. **Names:** the same file again gives `name (1).ext`.
  4. **The mark:** `xattr -p com.apple.quarantine` on the file shows Jasper's or Chromium's mark. A downloaded app is
     checked by Gatekeeper when opened.
  5. **Notifications:** finished, and failed, by killing a local `python3 -m http.server` mid-transfer. A navigation
     that turns into a download shows no error page.
  6. **Alt-click and Save Link As…:** Alt-click on a link loads it. Save Link As… asks in Jasper's chooser, and Cancel
     downloads nothing.
  7. **Closing and quitting:** closing the tab mid-download lets the file finish, with the page silent. Quitting
     mid-download leaves no file under its name.
  8. **The Downloads folder:** a custom folder works. One that cannot be created fails with the notification.
  9. **Reader mode:** on an article, ☰ offers both items, and Reader Mode shows the article in Jasper's colours.
     Escape and Exit Reader Mode end it, a navigation resets it, and `about:blank` offers nothing.
  10. **Distraction removal:** the crosshair and the red outline show. A click hides a cookie banner and its backdrop.
      Escape ends it, and what was hidden stays hidden.
  11. **Remove Element** hides a banner under the pointer, at 100% and 150% zoom.
  12. **Save Page As…:** it saves a folder named after the host, shown in Finder, whose `index.html` opens offline with
      its images and styles. A read-only location shows "Save Failed".
- [ ] **Step 2: Commit** "Browser: downloads and page tools' GUI check on macOS". Windows (the `Zone.Identifier` stream,
  `Get-Content <file> -Stream Zone.Identifier`, and Show in Explorer) and Linux (the XDG folder and Show in Folder) are
  recorded as not run until the user has those hosts.
