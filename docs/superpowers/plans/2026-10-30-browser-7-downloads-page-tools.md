# Browser 7: downloads and page tools — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or
> superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

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
