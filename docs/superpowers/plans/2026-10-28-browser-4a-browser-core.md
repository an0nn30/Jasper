# Browser 4a: the browser core — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or
> superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A bundled Browser plugin that shows Chromium pages as Jasper items, with:
- the navigation toolbar, the Web menu and its keys;
- iTerm2's link gestures, with popups that keep a working `window.opener`;
- the "Open in Jasper?" routing for links, OSC 8 targets and local web files;
- restore;
- a start that costs nothing until first use.

**Architecture:**
- **The plugin** is `plugins/browser`, id `dev.jasper.browser`. It uses the runtime's `jcef` module, in process, off screen.
- **Packages:**
  - `engine` owns Chromium's one process-wide life;
  - `page` holds the off-screen canvas and input, adapted from JetBrains' JCEF test classes, as the spike did;
  - `item` holds the item and its toolbar, built from SDK nodes;
  - `links` holds where a clicked link goes, and the opener bridge;
  - `routing` holds the `WebBrowser` role and its prompts;
  - `restore`, `settings` and `dialogs` are as named.
- **No `org.cef` outside `engine`, `page` and `links`,** so starting the plugin loads no Chromium class.
- **The new SDK pieces** this needs are a next-tab placement, a Web menu that shows only for browser items, a "window shows a modal" query and `Platform.openFile`.

**Tech Stack:** Java 25 and Swing on JBR 25.0.4.1 b635.75 with JCEF (Chromium 150.0.7871.129), Jasper's SDK 0.25.0 and testkit, JUnit 5.

**Spec:** [docs/superpowers/specs/2026-10-26-jasper-browser-plugin-design.md](../specs/2026-10-26-jasper-browser-plugin-design.md),
sections 3 to 9, 15, 16, 17 (the link settings), 19 and 23. Research for this plan (2026-10-28, through git at `main`
a6f4259de and JBR's `javap`) is summarised inline where a step depends on it.

## Global Constraints

- **Branch and baseline.** Work on branch `claude/browser-core` from `main` (0849270a3 or later), in its own worktree.
  `main` already has full windows and plans 2, 3 and 3b.
- **SDK version:** `JasperSdk.VERSION` is 0.24.0 on `main`. This plan's SDK additions take 0.25.0 (Task 2), and every
  bundled manifest moves to `sdk = ">=0.25.0, <0.26"`. New public SDK members carry `@since 0.25.0`.
- **Engine facts** (from JCEF's API; do not re-derive):
  - **Process:** Chromium runs in process (`CefApp.getInstance(args, settings)` after `CefApp.startup`). It cannot
    start again after `dispose()`, so it is disposed only when Jasper quits or the plugin stops.
  - **Settings:** `windowless_rendering_enabled = true`. Chromium's sandbox is on (`no_sandbox = false`) *(call: as
    Chrome)*.
  - **Popups:** `onBeforePopup(browser, frame, url, name)` only allows or cancels. No JCEF hook hosts a popup with its
    opener, so the opener bridge of Task 8 replaces it *(user, 2026-10-28: "Build an opener bridge")*.
  - **Closing:** `CefBrowserOsrWithHandler.doClose()` must return false, or closing a page closes Jasper's window.
  - **Classes:** JetBrains ships no off-screen Swing component, so the plugin carries adapted copies of the spike's
    `osr/` classes. The canvas extends `JComponent`, because `KitDiagnostics` refuses a `JPanel` as `CUSTOM` content.
  - **Compiling:** plugins compile on the class path against the toolchain's system modules, so `org.cef` is visible.
    Never set `options.release` (the JBR's `ct.sym` has no `jcef`).
- **No GUI from agents:**
  - Tests are headless and never start Chromium. Everything that needs a page is in the user's GUI check (Task 13).
  - Never run `:jasper-app:run`, benchmarks or AOT training without the user's OK, one batch at a time. Never run
    `./gradlew --stop`.
- **Architecture rules:**
  - SDK types appear in the app only in `dev.jasper.app.plugins`.
  - `PluginPresentationCheck` allows presentation code only in the named exceptions.
  - No interface without two real implementations, and no engine interface or test fake.
  - Every shipped jar and every copied third-party source file needs a `notices.toml` entry.
- **Copy:** the spec's strings, with "iTerm2" read as "Jasper". No animations; loading shows as a static state.
- **Platforms:** macOS, Windows and Linux, on x64 and aarch64. The keys are the spec's section 7 table.
- **Commits** end with:

  ```
  🤖 Generated with Claude Code at The Home Depot

  Co-Authored-By: Claude <noreply@anthropic.com>
  ```

## Review Focus

1. **Closing a browser item while it loads, or while a page dialog is open,** closes only that item. No Jasper window
   closes (`doClose`), and the dialog goes away. Task 4 and Task 9 pin this.
2. **The Web menu's keys inside a page text field.** Cmd+L (Ctrl+L) and Cmd+R (Ctrl+R) reach the Web menu, not the field.
   Ordinary editing keys reach the page. The keys don't act in a terminal pane next to a browser item. Task 6 pins this.
3. **Popups:**
   - a `window.open` without a user gesture is blocked;
   - one with a gesture opens next to the opener;
   - when the opener closes first, the popup's `window.opener` reports closed and its `postMessage` does nothing;
   - when the popup closes, the opener's handle reports `closed`.

   Task 8 pins this.
4. **Restore of awkward URLs:** a URL over the restore budget, `data:` and `blob:` URLs, a `jasper-about:` page, and a
   window with many browser items. Task 11 pins this.
5. **The plugin turned off, or Jasper quitting, with pages open.** Items close, Chromium is disposed once with a bounded
   wait, and quitting never hangs on it. Task 3 pins this.

---

### Task 1: The plugin skeleton and its build rules

**Files:**
- Create: `plugins/browser/build.gradle.kts`, modelled on `plugins/snippets/build.gradle.kts:3-10`.
- Create: `plugins/browser/src/main/resources/plugin.toml` and `settings.toml`.
- Create: `plugins/browser/src/main/java/dev/jasper/browser/BrowserPlugin.java`.
- Modify: `jasper-app/build.gradle.kts:43-54` (`stagePlugins`): add the browser.
- Modify: `PluginZipsTest.java:42` and `BundledSamplePluginTest.java:80-88`: the bundled list.
- Modify: `gradle/plugin-architecture.gradle.kts:34`: allow `org.cef.` and `com.jetbrains.cef.` for
  `dev.jasper.browser` only.
- Modify: `gradle/architecture/PluginPresentationCheck.java`:
  - add the `PAGE` constants (exact class and source) for `dev/jasper/browser/page/PageView`;
  - add `org/cef/` to `visible()` (line 125);
  - add a negative self-test (the page view building a `JButton`) and a positive one.
- Modify: `jasper-app/build.gradle.kts:34-39` (`applicationDefaultJvmArgs`) and the packaged `Jasper.cfg` options in
  `gradle/packaging.gradle` (checked at :554): add `--enable-native-access=jcef`.
- Modify: `docs/sdk-architecture.md:33-39` and AGENTS.md's architecture paragraph: the browser plugin and its page
  exception.
- Test: `plugins/browser/src/test/java/dev/jasper/browser/BrowserManifestTest.java` and `ChromiumBoundaryTest.java`;
  `jasper-app/src/test/java/dev/jasper/app/plugins/PluginJarsTest.java` (a JCEF case).

**Interfaces:**
- **`plugin.toml`:**
  - `id = "dev.jasper.browser"`, `name = "Browser"`, `version = "0.1.0"`;
  - `entry = "dev.jasper.browser.BrowserPlugin"`, `sdk = ">=0.25.0, <0.26"`, `enabled_by_default = true`;
  - `capabilities`: `browser.provide`, plus the capabilities the calls in this plan need. `DescriptorParser` and
    `Capabilities` list the names.
  - No `exports` yet: `dev.jasper.browser.api` arrives with the Markdown preview (plan 9). Record this deviation from
    spec section 23 in the banner.
- **`BrowserPlugin implements JasperPlugin`** (the SDK's entry type): `start(PluginContext)` registers everything the
  later tasks add. It loads no `org.cef` class.

- [ ] **Step 1: Write the failing tests.**
  - **`BrowserManifestTest`:** the manifest's id, version, sdk range, `enabled_by_default` and `browser.provide`, as in
    `EditorManifestTest`.
  - **`ChromiumBoundaryTest`:** reads every `.class` under the plugin's `build/classes` with
    `java.lang.classfile.ClassFile` (JDK 25). It asserts that no class outside `dev/jasper/browser/engine/`,
    `dev/jasper/browser/page/` and `dev/jasper/browser/links/` refers to `org/cef/` or `com/jetbrains/cef/`.
  - **`PluginJarsTest.aBundledPluginSeesTheRuntimesJcefModule`:** a plugin loader over a fixture jar answers
    `Class.forName("org.cef.CefApp", false, loader)` without starting anything.

- [ ] **Step 2: Run** `./gradlew :jasper-plugin-browser:test :jasper-app:test --tests '*PluginJarsTest'
  verifyPluginArchitecture`. Expected: FAIL.

- [ ] **Step 3: Implement** the module, the manifest, the empty plugin, the staging entry, the architecture rules and
  the JVM option. Add `docs/README.md`'s plugin row and `README.md`'s bundled plugin line only in Task 12.

- [ ] **Step 4: Run** `./gradlew :jasper-plugin-browser:check verifyPluginArchitecture :jasper-app:test
  --tests '*PluginZipsTest' --tests '*BundledSamplePluginTest' --tests '*PluginJarsTest'`. Expected: PASS.

- [ ] **Step 5: Commit** "Browser: the plugin skeleton and the rules for Chromium in a plugin".

### Task 2: The SDK pieces the browser needs (SDK 0.25.0)

**Files:**
- Modify: `jasper-sdk/src/main/java/dev/jasper/sdk/JasperSdk.java:9` (`VERSION = "0.25.0"`), every bundled
  `plugin.toml`'s `sdk` range, `docs/examples/external-plugin`, the SDK README's compatibility table and the guide
  pages, as `main`'s 0.24.0 bump did.
- Modify: `jasper-sdk/src/main/java/dev/jasper/sdk/ui/TabPlacement.java`: add `NEXT_TAB`.
- Modify: `jasper-sdk/src/main/java/dev/jasper/sdk/ui/Menus.java`: add `createForContent`.
- Modify: `jasper-sdk/src/main/java/dev/jasper/sdk/ui/Windows.java`: add `hasModal`.
- Modify: `jasper-sdk/src/main/java/dev/jasper/sdk/ui/Platform.java`: add `openFile`.
- Modify, in the app:
  - `jasper-app/.../contributions/TabRegistry.java` (around 282-306) and `WorkspaceHost.java:37`: attach after the
    current tab;
  - `workspace/WindowChrome.java` `rebuildContributedMenus` (around 430): the content-only menu, per window, rebuilt
    when the selected item changes;
  - `plugins/HostedUi.java` and `HostedContext.java`: the host side;
  - `application/JasperApplication.java`: `openFile` reaches `openRequestedFile` (never the browser).
- Modify: `jasper-sdk-testkit/.../FakePluginHost.java`, `FakeUi.java` and the contract cases.
- Test: `PluginContractTest` (both runtimes), and `jasper-sdk/src/test/.../UiValuesTest.java`.

**Interfaces** (all `@since 0.25.0`):
- `TabPlacement.NEXT_TAB`: a new tab right after the window's current tab, or at the end when there is none.
- `PluginMenu Menus.createForContent(String id, String title)`: a top-level menu, in the place the look gives plugin
  menus, shown in a window only while that window's current item is one of this plugin's content tabs.
- `boolean Windows.hasModal(WindowOwner owner)`: true while that window shows a modal dialog; false for a closed owner.
- `void Platform.openFile(Path file)`: opens a local file the way Jasper opens files, in the Default editor and never in
  Jasper's browser. UI thread; returns at once; a failure goes to the plugin's notifications.

- [ ] **Step 1: Write the failing contract cases:**
  - `aNextTabOpensRightAfterTheCurrentTab`: three tabs, the second current; the new tab is third, and the old third is
    fourth.
  - `aContentMenuShowsOnlyWhileThePluginsItemIsCurrentInThatWindow`: two windows, a content tab current in one; the menu
    is in that window's menu bar only. Selecting a terminal tab removes it, and selecting the content tab again brings
    it back.
  - `hasModalIsTrueWhileADialogShows`, and false for a closed owner.
  - `openFileUsesTheDefaultEditorEvenWithABrowserProvided`.

- [ ] **Step 2: Run** `./gradlew :jasper-sdk:test :jasper-sdk-testkit:test :jasper-app:test --tests '*AppContractTest*'`.
  Expected: FAIL.

- [ ] **Step 3: Implement** the four members, the app's host side, the fakes, and the version bump.

- [ ] **Step 4: Run** `./gradlew check sdkReference`, then
  `./gradlew -p docs/examples/external-plugin -PjasperSdkDir="$PWD/build/sdk-release" check pluginZip`. Expected:
  PASS. Commit the regenerated `docs/sdk-reference.html`.

- [ ] **Step 5: Commit** "SDK 0.25.0: next-tab placement, content menus, hasModal and openFile".

### Task 3: Chromium's life

**Files:**
- Create: `plugins/browser/src/main/java/dev/jasper/browser/engine/Chromium.java`, `ChromiumOptions.java` and
  `AboutScheme.java`.
- Test: `plugins/browser/src/test/java/dev/jasper/browser/engine/ChromiumOptionsTest.java` and `ChromiumStateTest.java`.

**Interfaces:**
- **`final class Chromium implements AutoCloseable`**, one per plugin run:
  - `Chromium(Path dataDir, Executor worker, AboutScheme about)`;
  - `CompletableFuture<CefClient> client()`: the first call starts Chromium on `worker`, never on the EDT. Later calls
    return the same future;
  - `State state()`, where `enum State { NOT_STARTED, STARTING, RUNNING, FAILED, STOPPED }`;
  - `close()`: closes every browser, then `CefApp.dispose()` once, waiting at most 10 s for `TERMINATED`, then returns
    whatever the state is.
- **`final class ChromiumOptions`**:
  - `static void apply(CefSettings settings, Path cacheDir)` sets `windowless_rendering_enabled = true`,
    `no_sandbox = false`, `cache_path` = `<dataDir>/chromium`, `persist_session_cookies = true`,
    `log_severity = LOGSEVERITY_WARNING` and `log_file` = `<dataDir>/chromium.log`;
  - `static List<String> arguments(List<String> appArgs)` returns `appArgs` plus `--disable-component-update`.
- **`final class AboutScheme`**:
  - `register(String page, Supplier<String> html)` adds a `jasper-about:<page>`;
  - `onRegisterCustomSchemes` registers `jasper-about` as standard, local, secure and CORS-enabled;
  - a request for an unknown page answers 404 with an empty body;
  - Task 5 registers `welcome`.

- [ ] **Step 1: Write the failing tests.**
  - **`ChromiumOptionsTest`:** a fresh `CefSettings` (constructing it starts nothing) gets the values above.
  - **`ChromiumStateTest`:**
    - a `Chromium` whose start is never asked for stays `NOT_STARTED`;
    - `close()` then answers at once and starts nothing;
    - a second `close()` does nothing.

- [ ] **Step 2: Run** `./gradlew :jasper-plugin-browser:test`. Expected: FAIL.

- [ ] **Step 3: Implement.**
  - **The start** follows the spike's `CefSession.start` (read it with `git show
    claude/browser-spike:tools/spikes/browser/src/spike/CefSession.java`, lines 91-148):
    - `JCefAppConfig.getInstance()`;
    - `SystemBootstrap.setLoader(config.getLoader())`;
    - `CefApp.startup(config.getAppArgs())`;
    - settings from `config.getCefSettings()`, then `ChromiumOptions.apply`;
    - `CefApp.addAppHandler(...)` with the scheme registration;
    - `CefApp.getInstance(args, settings)`, then `onInitialization`;
    - `createClient()`, completed on the EDT.
  - **Failure** completes the future exceptionally, with the reason in the plugin's log and one notification: "The
    browser could not start" / the reason.
  - **The plugin's `stop`, and Jasper's quit,** call `close()`.

- [ ] **Step 4: Run** `./gradlew :jasper-plugin-browser:test`. Expected: PASS.

- [ ] **Step 5: Commit** "Browser: Chromium starts on first use and stops once".

### Task 4: The page canvas and its input

**Files:**
- Create, under `plugins/browser/src/main/java/dev/jasper/browser/page/`:
  - `PageView.java`, the `JComponent` canvas, adapted from the spike's `osr/JBCefOsrComponent`;
  - `PageRenderer.java`, adapted from `osr/JBCefOsrHandler`;
  - `ImeAdapter.java`, adapted from `osr/CefInputMethodAdapter`;
  - `PageBrowser.java`, a `CefBrowserOsrWithHandler` with `doClose()` returning false;
  - `LastPress.java`.
- Modify: `notices.toml`: one component for the adapted JetBrains JCEF test classes (Apache-2.0, from
  JetBrains/jcef `java_tests` at `9df0879`). Keep the Apache header in each adapted file.
- Test: `plugins/browser/src/test/java/dev/jasper/browser/page/LastPressTest.java` and `PageViewHiddenTest.java`.

**Interfaces:**
- **`final class PageView extends JComponent`:** paints the last frame (the `PAGE` exception) and the link-hover URL
  (Task 7); forwards mouse, wheel and keys to the browser.
  - `void setBrowser(PageBrowser browser)`;
  - `LastPress lastPress()`;
  - `void setHoverUrl(String url)`.
  - **When the view is hidden** (`HierarchyEvent.SHOWING_CHANGED`) it calls `setWindowVisibility(false)` and
    `setWindowlessFrameRate(1)`; when shown again, `true` and `60`.
- **`record LastPress(int button, int modifiersEx, long whenMillis)`:** `static LastPress NONE`, and
  `boolean withinActivation(long nowMillis)`, true within 5,000 ms, Chromium's transient user activation.
- **`final class PageBrowser extends CefBrowserOsrWithHandler`:** `PageBrowser(CefClient, String url,
  CefRequestContext context, PageRenderer renderer, PageView view)`, with `doClose()` returning false.

- [ ] **Step 1: Write the failing tests.**
  - **`LastPressTest`:** activation within 5 s, outside it, and `NONE`.
  - **`PageViewHiddenTest`:** with a recording browser stand-in (package-private constructor seam), adding the view to
    a shown parent and then removing it records visibility `true` then `false`, and frame rates `60` then `1`. Tests
    are headless: show nothing, and fire the hierarchy events directly.

- [ ] **Step 2: Run** the tests. Expected: FAIL.

- [ ] **Step 3: Implement.**
  - **Drop from the spike's classes:** `JBCefFpsMeter`, `setBackground` and `setPreferredSize`. The host sizes and
    colours `CUSTOM` content.
  - **Keep:** the paint paths and shared memory, the resize debounce (100 ms, as the spike) and IME.
  - **Each mouse press** records a `LastPress` before it is forwarded.
  - **Drops onto the page** (files, text) are forwarded to Chromium through JCEF's `CefDropTargetListener`. Tab and pane
    drags stay the host's, as over any pane (spec section 5).

- [ ] **Step 4: Run** `./gradlew :jasper-plugin-browser:check verifyPluginArchitecture`. Expected: PASS.

- [ ] **Step 5: Commit** "Browser: the off-screen page canvas".

### Task 5: The browser item and its toolbar

**Files:**
- Create, under `plugins/browser/src/main/java/dev/jasper/browser/item/`:
  - `BrowserItem.java` and `BrowserItems.java`;
  - `BrowserToolbar.java`;
  - `AddressInput.java` and `TabTitle.java`.
- Modify: `BrowserPlugin.java`: register **New Browser Tab** and **New Browser Window** (File menu, no default keys),
  the split item `SplitItemSpec.of("browser", "Browser")`, the `globe` icon (`UiKit.registerIcon`, the SDK's icon
  rules), and `AboutScheme.register("welcome", …)`.
- Modify: `settings.toml`: `start_page = "jasper-about:welcome"`.
- Test: `AddressInputTest.java`, `TabTitleTest.java` and `BrowserToolbarTest.java` (FakeUi nodes).

**Interfaces:**
- **`static Optional<URI> AddressInput.resolve(String typed)`:**
  - trimmed text with a scheme, one of `http`, `https`, `file`, `about` or `jasper-about`, loads as typed;
  - a domain, an IP address, `localhost` or `host:port` gets `https://`;
  - other non-empty text becomes `https://google.com/search?q=` plus the URL-encoded text (plan 4b adds the engine
    setting);
  - `chrome:` and empty text give empty.
- **`static String TabTitle.of(String pageTitle, Optional<String> host, String previous, boolean loading)`:**
  - the trimmed page title;
  - else, while loading, `previous`;
  - else the host;
  - else `"Web Browser"`.
- **`final class BrowserItem implements AutoCloseable`:** one page in one pane, a `CustomContent` of kind `PREVIEW`
  with the toolbar's `UiView` above the `PageView`.
  - `static BrowserItem open(BrowserItems items, URI url, TabPlacement placement, Optional<String> target,
    Optional<WindowOwner> window)`;
  - `URI url()`, `Optional<String> target()`, `ContentTab tab()`;
  - `void load(URI url)`, `goBack()`, `goForward()`, `reload()`, `stop()` and `focusAddress()`.
  - **It sets presentation** (title from `TabTitle`, icon `globe`, location the URL) and status (the URL).
- **`final class BrowserItems`** keeps the open items, finds an item by OSC 8 target in any window, and knows the
  focused item of each window.
- **`BrowserToolbar`** has Back and Forward (disabled when they can't go), Reload, which becomes Stop while loading, the
  address `Field` (placeholder "Search or enter website name"), and a ☰ `MenuButton` holding **Settings** for now.
  - Gaining focus selects the field's text.
  - Enter loads `AddressInput.resolve`, then moves focus to the page.
  - Escape restores the page's URL and selects it.
  - Long-press history, the field's favicon, suggestions and the rest of ☰ come in plan 4b.

- [ ] **Step 1: Write the failing tests:**
  - **`AddressInputTest`**, including:
    - `example.com`, which gives `https://example.com`;
    - `localhost:8080`, which gives `https://localhost:8080`;
    - `192.168.1.10`, which gets `https://`;
    - `what is jcef`, which becomes a search;
    - `chrome://version`, which gives empty;
    - text with surrounding spaces, which is trimmed.
  - **`TabTitleTest`**, every branch.
  - **`BrowserToolbarTest`:** Back is disabled on a new item, Reload swaps to Stop while loading, and Escape restores the
    URL.

- [ ] **Step 2: Run** the tests. Expected: FAIL.

- [ ] **Step 3: Implement.**
  - **The welcome page** is a plain page in the theme's colours (built from `Appearance` colours into CSS variables):
    the heading "Welcome", with Quick Actions to Settings. Plan 5 completes it.
  - **New Browser Window** opens a full window with `TabPlacement.NEW_WINDOW`.

- [ ] **Step 4: Run** the tests and `./gradlew :jasper-plugin-browser:check`. Expected: PASS.

- [ ] **Step 5: Commit** "Browser: the browser item, its toolbar and New Browser Tab".

### Task 6: The Web menu and its keys

**Files:**
- Create: `plugins/browser/src/main/java/dev/jasper/browser/item/WebMenu.java`.
- Modify: `BrowserItem.java`: the content commands `copy` and `paste` (`CefFrame.copy` and `paste`).
- Test: `WebMenuTest.java`.

**Interfaces:**
- **`WebMenu.register(PluginContext)`** creates `Menus.createForContent("dev.jasper.browser.web", "Web")` with these
  items:
  - **Open Location…**, `dev.jasper.browser.open_location`: Cmd+L, or Ctrl+L on Linux and Windows;
  - **Back**, `dev.jasper.browser.back`: Cmd+[, or Alt+Left;
  - **Forward**, `dev.jasper.browser.forward`: Cmd+], or Alt+Right;
  - **Reload Page**, `dev.jasper.browser.reload`: Cmd+R, or Ctrl+R.

  Separators go between Open Location… and Back, and between Forward and Reload Page.
  - Every item is an `ActionSpec` with `withContext(ShortcutContext.content())` and
    `withDefaultBindings(macOs, other)`, acting on the window's focused browser item.
  - **History** (Cmd+Y, Ctrl+H) joins in plan 5, with the history page.
- **Gestures:**
  - macOS two-finger swipe left and right runs Back and Forward, where Java delivers the swipe. Otherwise it is
    recorded as not run in the banner;
  - on Windows and Linux, mouse buttons 4 and 5 run Back and Forward.

- [ ] **Step 1: Write the failing tests:**
  - **The defaults:** each action's macOS and other default bindings.
  - **Clash checks:** `KeyBindings.conflicting` finds none for the browser's content context on either platform.
  - **Placement:** the menu appears only while a browser item is current.
  - **The terminal rule:** in a terminal pane, Cmd+R still runs Search Shell History, using `WindowContent.shortcutTarget`
    through the app's test seam, as `main`'s key tests do.

- [ ] **Step 2: Run** the tests. Expected: FAIL.

- [ ] **Step 3: Implement** the menu and the copy and paste commands.

- [ ] **Step 4: Run** the browser's and the app's key tests. Expected: PASS.

- [ ] **Step 5: Commit** "Browser: the Web menu and its keys".

### Task 7: Where a clicked link goes, and the hover URL

**Files:**
- Create: `plugins/browser/src/main/java/dev/jasper/browser/links/LinkGesture.java` and `PageNavigation.java`, the
  `CefRequestHandler`, `CefLifeSpanHandler` and `CefDisplayHandler` wiring.
- Test: `LinkGestureTest.java`.

**Interfaces:**
- **`enum Where { SAME_ITEM, NEXT_TAB, SPLIT }`** and **`static Where LinkGesture.of(LastPress press, boolean macOs)`:**
  - Cmd-click (Ctrl-click) is `NEXT_TAB`;
  - Cmd+Shift-click (Ctrl+Shift-click) is `SPLIT`;
  - a middle click is `NEXT_TAB`;
  - Shift-click, Alt-click and an unmodified click are `SAME_ITEM`.
- **The split direction:** `SPLIT` opens to the right when the item's pane is wider than tall, otherwise below
  (`PageView`'s size).
- **`PageNavigation` handles:**
  - **`onOpenURLFromTab`:** `NEXT_TAB` or `SPLIT` opens a new item and cancels, and `SAME_ITEM` loads in place;
  - **`onBeforePopup`:**
    - a `target=_blank` link, or a `window.open` inside the user-activation window, opens a `NEXT_TAB` item. A
      script `window.open` goes through Task 8's bridge instead;
    - anything else is cancelled, with no indicator;
    - it always returns true, so Chromium never makes its own popup;
  - **`onStatusMessage`:** `PageView.setHoverUrl` at the bottom left.

- [ ] **Step 1: Write the failing tests:** `LinkGestureTest`, covering every modifier combination on both platforms,
  with a `LastPress` whose button is 2 for the middle click.

- [ ] **Step 2: Run** the tests. Expected: FAIL.

- [ ] **Step 3: Implement.**

- [ ] **Step 4: Run** the tests. Expected: PASS.

- [ ] **Step 5: Commit** "Browser: link gestures, popups blocked without a gesture, and the hover URL".

### Task 8: The opener bridge

**Files:**
- Create: `plugins/browser/src/main/java/dev/jasper/browser/links/OpenerBridge.java`.
- Create: `plugins/browser/src/main/resources/dev/jasper/browser/links/opener-bridge.js`.
- Test: `OpenerBridgeTest.java` (the Java side).

**Interfaces:**
- **`final class OpenerBridge`:**
  - `OpenerBridge(CefClient client, BrowserItems items)` adds a `CefMessageRouter`, with `jsQueryFunction =
    "jasperBridgeQuery"` and `jsCancelFunction = "jasperBridgeCancel"`;
  - `void inject(PageBrowser browser, boolean asPopup)`;
  - it holds one `Link(long id, BrowserItem opener, BrowserItem popup)` per popup.
- **`opener-bridge.js`** is run with `executeJavaScript` at each main-frame `onLoadStart`, in the top frame only, so it
  stays out of challenge frames (Cloudflare Turnstile, hCaptcha), as spec section 5 requires.
  - **In every page,** it replaces `window.open(url, name, features)`.
    - With a user activation, it asks Java to open `url` as a `NEXT_TAB` item linked to this page, and returns a
      stand-in with `closed`, `close()`, `focus()` and `postMessage(message, targetOrigin)`.
    - Without one, it returns `null`, as a blocked popup does.
  - **In a popup,** it sets `window.opener` to a stand-in with `closed`, `postMessage(message, targetOrigin)` and
    `focus()`.
- **The Java side relays messages:**
  - **Delivery:** it calls `postMessage` in the other page as `window.dispatchEvent(new MessageEvent("message", {data,
    origin: <sender origin>, source: <the stand-in>}))`.
  - **Origins:** only when `targetOrigin` is `"*"` or equals the receiver's origin.
  - **Data:** JSON-serialisable values only. Anything else is dropped, with a console warning in the sender.
  - **Closing:** `close()` closes the popup item. Closing either item sets the other side's `closed` to true, after
    which messages are dropped.
- **Not supported, as recorded in the docs:** reading the popup's `location`, and non-JSON messages.

- [ ] **Step 1: Write the failing Java tests** with a recording page stand-in (a package-private seam taking `String`
  scripts):
  - **open:** a request makes a link and opens a `NEXT_TAB` item;
  - **delivery:** `postMessage` with a matching origin delivers, and one with a non-matching origin is dropped;
  - **closing:** closing the popup marks the opener's stand-in closed, and closing the opener marks the popup's
    `window.opener` closed;
  - **bad input:** a malformed request (not JSON, an unknown op, a missing id) gets a failure, never an exception.

- [ ] **Step 2: Run** the tests. Expected: FAIL.

- [ ] **Step 3: Implement.** Keep the script small and free of dependencies. The GUI check (Task 13) proves it in a
  page.

- [ ] **Step 4: Run** the tests. Expected: PASS.

- [ ] **Step 5: Commit** "Browser: popups keep a working window.opener through Jasper's bridge".

### Task 9: Page dialogs, file inputs and fullscreen

**Files:**
- Create: `plugins/browser/src/main/java/dev/jasper/browser/dialogs/PageDialogs.java`, the `CefJSDialogHandler`,
  `CefDialogHandler` and fullscreen wiring.
- Test: `PageDialogsTest.java`.

**Interfaces:**
- **`alert`** shows a `Windows.dialog` overlay headed "Web Page Alert", with the message and **OK**.
- **`confirm`** shows "Web Page Confirmation", with **OK** and **Cancel**.
- **Both** are shown with `invokeLater`, and the callback is continued when the dialog closes. If the item closes
  first, they are dismissed.
- **`prompt()`** shows nothing and is continued with `false` and `""`.
- **`onBeforeUnloadDialog`** is continued with `true`, so the page closes without asking.
- **`onFileDialog`:**
  - `OPEN` uses `chooseFiles(single)`, `OPEN_MULTIPLE` uses `chooseFiles(multiple)`, and `OPEN_FOLDER` uses
    `chooseDirectory`, all local;
  - `SAVE` uses `chooseSaveFile`;
  - cancelling calls `Cancel()`.
- **A page that enters fullscreen** is told to leave it at once (`document.exitFullscreen()`).

- [ ] **Step 1: Write the failing tests:**
  - the dialog content for each type, built with FakeUi;
  - `prompt` returns false;
  - beforeunload continues;
  - an item that closes while an alert shows dismisses it, and continues it once.

- [ ] **Step 2: Run** the tests. Expected: FAIL.

- [ ] **Step 3: Implement.**

- [ ] **Step 4: Run** the tests. Expected: PASS.

- [ ] **Step 5: Commit** "Browser: page dialogs, file inputs and fullscreen".

### Task 10: The routing role, its prompts and settings

**Files:**
- Create, under `plugins/browser/src/main/java/dev/jasper/browser/`:
  - `routing/BrowserRole.java` (`implements WebBrowser`);
  - `routing/LinkDecision.java`;
  - `routing/LinkPrompt.java`;
  - `routing/WebAddressItems.java`;
  - `settings/BrowserSettings.java`.
- Modify: `settings.toml`: `open_links_from_terminal = "ask"` and `open_local_web_files = "ask"`.
- Test: `LinkDecisionTest.java`, `BrowserRoleTest.java` (with `FakePluginHost.openLink`) and `WebAddressItemsTest.java`.

**Interfaces:**
- **`record BrowserSettings(String startPage, LinkOpening links, LocalFileOpening localFiles)`:**
  - `enum LinkOpening { ASK, BUILT_IN, DEFAULT_BROWSER }`, stored as `ask`, `builtin` and `default`;
  - `enum LocalFileOpening { ASK, BUILT_IN, DEFAULT_EDITOR }`, stored as `ask`, `builtin` and `editor`;
  - `static void declare(PluginSettings)` declares the Settings › Browser rows "Start page", "Open Links From Terminal"
    and "Open Local Web Files", with the spec's labels;
  - `static BrowserSettings read(PluginSettings)`.
- **`enum Decision { OPEN, ASK, HAND_BACK }`** and **`static Decision LinkDecision.decide(WebLink link, BrowserSettings
  settings)`:**
  - **`EXTERNAL`** is `OPEN`;
  - **a `file:` link** goes by `localFiles`: `ASK` is `ASK`, `BUILT_IN` is `OPEN`, and `DEFAULT_EDITOR` is `HAND_BACK`;
  - **a split placement** is `OPEN` unless `links` is `DEFAULT_BROWSER`, in which case it is `HAND_BACK`;
  - **otherwise `links`:** `ASK` is `ASK`, `BUILT_IN` is `OPEN`, and `DEFAULT_BROWSER` is `HAND_BACK`.
- **`BrowserRole.open(WebLink)`:**
  - **`HAND_BACK`** returns false;
  - **`OPEN`** opens an item and returns true. The link's placement decides where: `TAB` becomes
    `TabPlacement.NEXT_TAB`, and `SPLIT_RIGHT` or `SPLIT_DOWN` becomes the same `TabPlacement`, in `link.window()`. A
    link with an OSC 8 target instead loads into the item that target names, in any window, without bringing it
    forward;
  - **`ASK`**, when `Windows.hasModal(window)` is true, returns false (the system browser, or the Default editor, this
    once). Otherwise it returns true and shows the prompt with `invokeLater`.
- **`LinkPrompt`:**
  - **For a link:** "Open in Jasper?" / "Jasper can display web pages! Would you like to open this link in Jasper?",
    with **Use Default Browser**, which calls `Platform.browse`, **Open in Jasper** (primary), and **Cancel**.
  - **For a local file:** "Open in Jasper?" / "Jasper can display files like this in its built-in web browser! Would
    you like to open this link in Jasper?", with **Use Default Editor**, which calls `Platform.openFile`, **Open in
    Jasper** and **Cancel**.
  - **Both** have the toggle "Remember my choice", off. With it on, the choice writes that prompt's setting; Cancel
    writes nothing.
- **`WebAddressItems`** adds **Open URL in Split Right** and **Open URL in Split Down** through
  `menus().terminalContext().addForWebAddress`. They are present only while `links` is not `DEFAULT_BROWSER`: add them,
  and close their subscriptions, when the setting changes. They open the `webAddress()` as `SPLIT_RIGHT` or
  `SPLIT_DOWN` from the invoked pane.

- [ ] **Step 1: Write the failing tests.**
  - **`LinkDecisionTest`:** the full table, every origin by placement by file-or-web by setting.
  - **`BrowserRoleTest`:**
    - a split link opens with no prompt;
    - `EXTERNAL` opens with no prompt;
    - `ASK` with a modal returns false;
    - a target reuses the same item across two windows;
    - "Remember my choice" plus Open in Jasper writes `builtin`;
    - Cancel writes nothing.
  - **`WebAddressItemsTest`:** the items appear and disappear with the setting.

- [ ] **Step 2: Run** the tests. Expected: FAIL.

- [ ] **Step 3: Implement,** and register the role with `context.platform().browser(new BrowserRole(...))` in
  `BrowserPlugin`.

- [ ] **Step 4: Run** `./gradlew :jasper-plugin-browser:check`. Expected: PASS.

- [ ] **Step 5: Commit** "Browser: the routing role, its prompts and the link settings".

### Task 11: Restore

**Files:**
- Create: `plugins/browser/src/main/java/dev/jasper/browser/restore/BrowserRestore.java`.
- Modify: `BrowserItem.java` (`setRestore` on each committed navigation) and `BrowserPlugin.java` (the restorer).
- Test: `BrowserRestoreTest.java`.

**Interfaces:**
- **`static final String KIND = "dev.jasper.browser.page"`.**
- **`static Optional<RestoreState> state(URI url, Optional<String> target)`:**
  - the keys are `url` and `target`, when present;
  - a URL whose entries would pass `RestoreState.MAX_CHARACTERS` (4,096, counted over keys and values) is saved without
    its query and fragment. If that still doesn't fit, no `url` is saved;
  - a `data:` or `blob:` URL saves no `url`;
  - `jasper-about:` pages save their URL.
- **`static Optional<URI> url(RestoreState)`.**
- **The restorer** opens an item at once with no page. The page loads, from the saved URL or else the start page, the
  first time the item is shown. That first showing also starts Chromium.
- **/dev/null items** arrive in plan 6, which adds a `devnull` key.

- [ ] **Step 1: Write the failing tests:**
  - a 5,000-character query is stripped;
  - a 5,000-character path saves no URL;
  - `data:text/html,…` saves no URL;
  - a target round-trips;
  - a restored item doesn't load until it is shown, checked through the item's test seam.

- [ ] **Step 2: Run** the tests. Expected: FAIL.

- [ ] **Step 3: Implement.**

- [ ] **Step 4: Run** the tests. Expected: PASS.

- [ ] **Step 5: Commit** "Browser: browser items restore, loading when first shown".

### Task 12: Docs and the check

**Files:**
- Create: `docs/browser.md`, the user's guide to what this plan built:
  - opening pages;
  - the toolbar;
  - the Web menu and keys (the spec's section 7 table, with the parts this plan built);
  - link gestures;
  - popups and the opener bridge's limits;
  - the link and local-file settings and their prompts;
  - restore.
- Modify:
  - `README.md`'s bundled plugin list;
  - `docs/README.md`'s index (Using Jasper);
  - `docs/keyboard-shortcuts.md` (the Browser rows);
  - `docs/configuration.md` (Settings › Browser's three settings, with headings that Help can link to);
  - `docs/terminal.md` (what Cmd+Shift-click does when the Browser plugin is on);
  - `docs/command-line.md` (`jasper URL...` opens in Jasper's browser);
  - `docs/packaging.md` (the bundled plugin count);
  - `docs/STATUS.md`, with the plan's entry.
- Modify: this plan's status banner.

- [ ] **Step 1: Write the docs,** following AGENTS.md's documentation rules: document what the code does, and nothing
  of plans 4b to 9.

- [ ] **Step 2: Run the source hygiene script,** then `./gradlew check`, then
  `./gradlew -p docs/examples/external-plugin -PjasperSdkDir="$PWD/build/sdk-release" check pluginZip`. Expected:
  PASS. Record the counts from the XML.

- [ ] **Step 3: Commit** "Docs: the Browser plugin's core".

### Task 13: The user's checks, with their OK for each batch

**Files:**
- Create: `docs/browser-verification.md`, the checklist and its results.

- [ ] **Step 1: The GUI check on macOS** (`:jasper-app:run` from the worktree, with the user's OK). Each line is
  recorded pass or fail:
  1. **The start:** start-up shows no Chromium process until the first browser item, and New Browser Tab opens the
     welcome page.
  2. **Navigation:** typing an address, a word and a search loads each. Back, Forward, Reload and Stop work, and so do
     Cmd+L, Cmd+[, Cmd+] and Cmd+R, including inside a page's text field.
  3. **Terminal clicks:**
     - in a terminal, Cmd-click on a URL shows "Open in Jasper?" the first time;
     - "Remember my choice" sticks;
     - Cmd+Shift-click opens a split without asking;
     - the right-click items on a selected URL work.
  4. **OSC 8 targets:** `printf '\e]8;target=docs;https://example.com\e\\docs\e]8;;\e\\\n'`, clicked twice from two
     panes, loads one item twice.
  5. **Page gestures:** in a page, Cmd-click opens the next tab, Cmd+Shift-click opens a split, and a script
     `window.open` without a click is blocked.
  6. **The opener bridge:** a sign-in popup that posts back to its opener works. Use a test page served from
     `python3 -m http.server` with a `window.open` and `postMessage` pair, and one real site the user picks.
  7. **Page dialogs:** `alert` and `confirm` show Jasper's dialogs; `<input type=file>` opens Jasper's chooser.
  8. **Closing:** closing a loading item, and one showing an alert, closes only that item.
  9. **Restore:** quit and relaunch, and the browser items come back and load when shown.
  10. **Local web files:** Cmd-click on a local `.html` file shows the local-file prompt.
- [ ] **Step 2: Memory** (the user's OK). With `run-macos.sh memory`, measure:
  - RSS with no browser item used;
  - RSS after one page;
  - RSS 2 minutes after closing every browser item.

  With no browser item ever used, memory must match today's within noise (spec section 19). Chromium can't restart in
  process, so its browser process stays after first use.
  - **If RSS after closing doesn't return near the baseline,** stop and bring the numbers to the user. The alternative,
    out-of-process `cef_server`, can stop and restart, but has no downloads.
- [ ] **Step 3: AOT** (the user's OK). A trained `packageDist` cache holds no `org.cef` class, with the class-list
  method `verifyAotCache` uses.
- [ ] **Step 4: Commit** "Browser: the core's GUI check and memory on macOS". Windows and Linux are recorded as not run
  until the user has those hosts.

## What follows

- **4b, page features:**
  - long-press history;
  - the favicon in the tab and the field (an SDK image icon);
  - address suggestions and search engines;
  - find;
  - zoom;
  - the right-click menu (an SDK menu at a point);
  - error pages, and certificate errors with Continue;
  - View Source;
  - the rest of the ☰ menu.
- **4c, registering as a browser:**
  - `CFBundleURLTypes` and the open-URI handler;
  - the launcher's URL arguments;
  - Windows `RegisteredApplications` and Linux `x-scheme-handler`;
  - the default-browser check;
  - the plugin-off notification (moved from 3b).

Both are written once this plan's names are reviewed.
