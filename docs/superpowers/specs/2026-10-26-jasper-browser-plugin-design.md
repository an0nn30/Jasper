# Jasper Browser plugin (iTerm2 parity)

Status: **Draft, 2026-10-26, awaiting the user's approval.** This spec replaces the
[2026-10-21 browser design](2026-10-21-jasper-browser-plugin-design.md). The user narrowed the scope on 2026-10-26: "it
should mirror the behavior of iTerms built-in browser. Nothing more nothing less. Oh and also using that browser engine
to rework the markdown previewer." They also approved two proposals: mapping iTerm2's integrations onto Jasper features
that have a counterpart, and keeping plan 3's link routing ("yes to both, rewrite the spec").

**Reading the marks.** Unmarked behaviour mirrors iTerm2 3.7.6, the current stable release. Other marks:
- *(user)*: the user's own decision.
- *(call)*: the designer's mapping where Jasper has no exact counterpart. Every call is listed for the user in
  section 22.
- *(left out)*: an iTerm2 behaviour Jasper has no counterpart for (section 20).

The iTerm2 facts come from its source (gnachman/iTerm2 at `62f916dd`, read on 2026-10-26), its documentation and its
release notes. The notes are in the git-ignored `~/projects/moray/.superpowers/iterm2-browser-inventory.md`,
`iterm2-browser-features-A.md` and `iterm2-browser-integrations-B.md`, with file and line citations. In the user-visible
strings below, "iTerm2" becomes "Jasper".

This design follows the [tabs and panes spec](2026-10-20-jasper-tabs-and-panes-design.md) and its
[pane flexibility amendment](2026-10-21-jasper-pane-flexibility-design.md), both on main.

## 1. Intent

- **A built-in web browser** that does what iTerm2's does. iTerm2 describes its browser like this: "While not intended
  as a primary browser, iTerm2's web capabilities provide a useful tool for integrated terminal and web workflows."
- **A Markdown preview drawn by the same engine** *(user)*, with Mermaid diagrams, code highlighting and GitHub-style
  rendering in Jasper's theme colours.

## 2. How the scope is decided

- **Browser behaviour** is copied as iTerm2 has it: its chrome, menus, keys, pages, dialogs and their wording.
- **Integrations** with other iTerm2 features are copied only where Jasper has the counterpart:
  - Open Quickly becomes the command palette;
  - key bindings become the keymap;
  - the find bar becomes a find bar like the Editor's, with Match case and Regular expression;
  - Snippets become Jasper's Snippets plugin;
  - the password manager becomes the Credential Vault;
  - copy on selection becomes `terminal.copy_on_select`;
  - window, tab and split-pane hosting become Jasper's windows, tabs and panes;
  - window arrangements and restoration become workspace restore.
- **The window model.** The design targets the
  [full windows design](https://github.com/an0nn30/Jasper/blob/claude/full-windows/docs/superpowers/specs/2026-10-26-jasper-full-windows-design.md)
  (2026-10-26, on `claude/full-windows`), in which every window has the menus and a status bar. Plan 4 starts once it
  is on main *(call)*.
- **One addition beyond iTerm2 stays** *(user)*: Chrome's way past a certificate error (section 14).
- **iTerm2-only integrations are left out** (section 20). Examples are AI chat, Instant Replay, triggers, the Python API,
  Smart Selection, copy mode and named marks.
- **iTerm2 is macOS-only, and Jasper runs everywhere** *(user)*. On Windows and Linux, each iTerm2 behaviour gets that
  platform's counterpart: keys, file-manager wording, the downloads folder and how Jasper registers as a browser.
- **Items only in iTerm2's `master` or 3.7.7 beta are left out** *(call)*: Show Toolbar, floating panes, the reworked
  View Source page, and the tmux split rule.

## 3. Constraints

- **Tabs and panes.** A window holds tabs and a tab holds panes. A pane holds exactly one item and has no tab strip or
  title bar. A tab is named after its focused pane's item. Every command is a bindable keymap action.
- **The SDK surface** (SDK 0.22.0 on main, checked 2026-10-26):
  - `TabPlacement` has `NEW_TAB`, `SPLIT_RIGHT`, `SPLIT_DOWN` and `NEW_WINDOW`.
  - `Windows.splitItem(SplitItemSpec.of(id, label).withIcon(..), (window, placement) -> ...)` registers a split item.
    The Editor's split item is the model.
  - `Windows.openTab(ContentSpec, WindowOwner, UiView, TabPlacement)` opens a tab. A plugin's own Swing component goes
    in as `CustomContent`. `NEW_TAB` opens at the end of the tab strip, so "next to this tab" (sections 8 and 9) is an
    SDK addition.
  - `ContentSpec.withRestore(new RestoreState(kind, values))` and `context.windows().restorer(kind, ...)` handle
    restore. The state is limited to 32 entries and 4,096 characters and holds no secrets. A restorer runs on the
    EDT, must not block or ask anything, and opens the item at once.
  - `ContentTab.isActive()` and `onActive` mean focus, not visibility. To pause a hidden page, use Swing's showing state.
  - **Keys.**
    - A plugin action whose default is in `ShortcutContext.content()` may reuse a global key, and wins in that plugin's
      tabs (`KeyContext`, `WindowContent`). It stays bindable in Settings › Keymap. The browser's keyed commands are
      such actions.
    - Content commands (`ContentTab.setCommands`) have fixed keys outside the keymap. The browser uses them only for
      routing: Jasper's Copy, Paste, Find, Find Next, Find Previous and Increase, Decrease and Reset Font Size run the
      item's commands `copy`, `paste`, `find`, `find-next`, `find-previous`, `font-bigger`, `font-smaller` and
      `font-reset`.
    - While the palette is closed, it takes only Command Palette's own key, not the scope openers.
  - **Menus.**
    - `menus().create(id, title)` is a plugin's top-level menu, in the place the look gives plugin menus.
    - `menus().standard(..)` adds to a built-in menu.
    - `menus().terminalContext()` adds to the terminal's right-click menu.
    - `PluginMenu.add` takes only registered actions, and every action is listed in the palette and the keymap.
  - `windows().chooseSaveFile(owner, title, initial)` is Jasper's save chooser, for local files only.
  - Content items have no host find bar; the Editor builds its own.
- **Heavyweight children are out.** A native child window would cover what Jasper paints over a pane: the drag landing
  overlay, inactive-pane dimming, the palette, notification cards, the modal overlay, popovers and the dock's drag
  guides. It would also swallow the Cmd+Option (Ctrl+Alt) press that starts a pane drag. Chromium therefore draws
  off-screen (OSR) into a lightweight Swing component.
- **Cmd+Option (Ctrl+Alt) belongs to the pane drag** everywhere in a pane, so no browser gesture can use it (sections 8
  and 9).
- **Architecture rules** (AGENTS.md):
  - SDK types appear in the app only inside `dev.jasper.app.plugins`.
  - Plugins compile against the SDK and the `api` packages they declare.
  - `PluginPresentationCheck` refuses presentation code in plugin classes. The page's rendering component needs a
    named exception, as the Editor's `PreviewPane` has. The exception covers only what that component paints: the page,
    the link-hover URL and the favicon's drag image. The toolbar, menus and dialogs come from SDK nodes and the SDK
    additions this spec names.
  - No interface without two real implementations.
  - Every shipped jar, font and bundled script needs a `notices.toml` entry. That covers Readability.js, Mermaid, the
    highlighter and the Browser's own copy of sqlite-jdbc (today only Remote bundles it).
- **The plugin's capabilities:**
  - `browser.provide`;
  - `palette.contribute`;
  - `terminal.open`, for the Split submenu's terminal entries;
  - `clipboard.read`, to put the clipboard back after a Snippets paste;
  - `terminal.observe`;
  - the notification capability;
  - any capabilities the SDK additions bring.
- **Tests stay headless.** CI cannot show Chromium. GUI launches and benchmarks are the user's, one approved batch at a
  time.
- **Jasper is an MVP:** no compatibility shims or migrations. **No animations:** loading shows as a static indicator.

## 4. Engine and packaging

This section is unchanged from the 2026-10-21 design.

- **The runtime.** JCEF in JetBrains' runtime build that includes it (`jbr_jcef` / `jbrsdk_jcef`), carried in every
  package *(user)*.
- **The Chromium it ships.** JBR 25.0.4.1 b635.75 carries CEF 150.0.14, which is Chromium 150.0.7871.129.
- **Why no switch:** the user said "switch if needed", and the call was no switch. Each Jasper release takes the
  newest `jbr_jcef` build.
- **What moves to the JCEF build:** the Gradle toolchain, development machines, CI and packaging. The toolchain check
  must pick the JCEF build. Packages carry Chromium's native parts, signed inside `Jasper.app` on macOS.
- **Package sizes** (`.tar.gz`, `jbr` vs `jbr_jcef`):
  - macOS aarch64: 98 MB vs 246 MB;
  - Windows x64: 100 MB vs 287 MB;
  - Linux x64: 104 MB vs 260 MB.
- **Checked from JBR's `jcef` module on 2026-10-26** (`javap`):
  - `CefBrowser` has `setZoomLevel`, `find` (plain text only), `print`, `printToPDF`, `startDownload`, DevTools and a
    DevTools protocol client.
  - `CefRequestContext` has `createContext(handler)` and `setPreference`.
  - `CefPermissionHandler` has only `onRequestMediaAccessPermission` (camera and microphone).
  - `CefDisplayHandler` has `onStatusMessage`.
  - `CefJSDialogHandler` and `CefDialogHandler` exist.
  - `CefSettings.user_agent` exists.
  - There is no audio-mute call and no favicon callback. Find with regular expressions, page notifications, audio muting
    and favicons are therefore done in page scripts, as iTerm2 does them.
  - **Navigation and popups:**
    - `onOpenURLFromTab(browser, frame, url, userGesture)` reports modifier-clicked links, with no disposition.
    - `onBeforePopup(browser, frame, url, name)` has no user-gesture argument.
    - So Jasper decides where a link or popup goes from its own record of the last mouse press and its modifiers,
      which it forwards to Chromium itself.
  - `getAuthCredentials(browser, originUrl, isProxy, host, port, realm, scheme, callback)` has no frame argument.
  - `print()` takes no settings, so Chromium's print dialog decides the layout.
  - **Hidden pages:** there is no `WasHidden`. A hidden page is quietened with `SetWindowVisibility` and
    `SetWindowlessFrameRate`.
  - **DevTools:** `openDevTools()` opens a native top-level window. Off screen, `createDevToolsBrowser` is protected.
- **Earlier findings from JCEF's source** (2026-10-21):
  - In-process CEF cannot restart after shutdown; the out-of-process `cef_server` can, but has no downloads.
  - Off-screen rendering exposes no accessibility.
  - `onBeforePopup` only allows or cancels a popup.
- **Still unsettled:** the spike (plan 1) checks Chromium's sandbox on each platform, and keeping a popup's opener.
- **Rejected alternatives:**
  - jcefmaven;
  - JxBrowser, which is commercial;
  - JavaFX WebView, an old WebKit;
  - pure-Java renderers, which have no usable JavaScript.

## 5. Browser items

- **One page is one item in one pane.** A browser item opens in a new tab, a split or a new window, as iTerm2 opens a
  browser profile in a window, tab or split pane.
- **The commands:**
  - **New Browser Tab** and **New Browser Window** (a full window), in the File menu and the palette, with no default
    shortcut *(call: iTerm2 opens browser profiles from its Profiles menu, and Jasper has no profiles)*;
  - the **Browser** split item, so the host's Split submenu offers Browser Right and Browser Down.
- **A new item** loads the **Start page** setting, `jasper-about:welcome` by default.
- **The browser is a bundled plugin, on by default** *(call)*. Its on/off switch in Settings › Plugins is the
  counterpart of iTerm2's "Enable browser-style profiles". With it off:
  - no browser items, Web menu or browser palette scopes;
  - links go to the system browser without a prompt.
- **iTerm2's plugin gate is left out.** iTerm2 needs a separately downloaded "Browser Plugin" app; Jasper bundles the
  browser. Its "Plugin Required" upsell becomes one notification: when the plugin is off and Jasper, as the system's
  default browser, receives a URL, the notification offers to turn the plugin on (section 9).
- **Tab title:**
  - the page title, trimmed;
  - a blank title while loading keeps the previous name;
  - once loading finishes with a blank title, the host;
  - otherwise "Web Browser".
- **Tab icon:** the page's favicon. The favicon comes from `<link rel=icon>` or `mask-icon`, falling back to
  `/favicon.ico`. It is fetched each time, not saved. A globe shows while loading or when there is none, and internal
  pages show Jasper's icon.
  - **SDK addition.** SDK icons are names drawn in theme colours (`docs/sdk-icons.md`), so the SDK needs an image tab
    icon for this.
- **Closing.** A browser item closes like any pane (Close Pane), stops its audio and cannot be restarted. It asks
  nothing on close: pages' `beforeunload` handlers are not honoured.
- **Hidden pages** stop painting when Swing's showing state says they are hidden (`SetWindowVisibility`, frame rate).
  Network work keeps running.
- **Page dialogs:**
  - `alert()` shows a Jasper modal headed "Web Page Alert", with the message and **OK**;
  - `confirm()` shows "Web Page Confirmation", with **OK** and **Cancel**;
  - `prompt()` shows nothing and returns nothing, because iTerm2 has no handler for it;
  - `<input type=file>` opens Jasper's file chooser, local files only, choosing folders when the page asks for them;
  - fullscreen requests are refused.
- **Drag and drop.** Ordinary drags reach the page. Tab and pane drags land over a page as over any pane.
- **Captcha frames.** The page scripts stay out of challenge frames (Cloudflare Turnstile, hCaptcha and the like), as
  iTerm2's do since 3.7.0.
- **User agent:** Chromium's own *(call: iTerm2 identifies as Safari because it is WebKit)*.

## 6. The toolbar

The item's own toolbar runs along the top of the page, as iTerm2's does. It is built from SDK nodes, so the Enterprise
look restyles it. Any node capability it lacks, such as a long-press menu, a suggestion popup, an image icon or a drag
source, is an SDK addition. Sizes follow the kit's toolbar and field metrics, not iTerm2's points *(call)*.

From left to right:

1. **Back** and **Forward.**
   - Each is disabled when there is nowhere to go.
   - Pressing one for at least 0.5 s pops up its history list: each entry is the page title (the URL when the title is
     empty), with the URL as its tooltip.
2. **Reload**, which becomes **Stop** while the page loads.
3. **The address field**, centred in 60% of the remaining width with a minimum width (the kit's equivalent of 250 pt).
   - **The favicon** sits at its left (a globe while loading). Dragging it drags the URL, as a URL and as text, plus
     the file for a `file:` URL. Accessible name: "Site icon - drag to copy URL".
   - **Loading** shows as a small static indicator at its right.
4. **The /dev/null indicator** (`hidden` icon), only in /dev/null mode (section 12). Clicking it shows: "**/dev/null
   Mode** — Your browsing activity is not being saved. No history, bookmarks, or other data will be stored. This mode
   is set in Settings › Browser › Privacy & Data."
5. **The menu button** (☰). Its items, in order:
   - **Reader Mode** / **Exit Reader Mode**, **Remove Distractions** / **Exit Distraction Removal**, then a separator.
     These show only when the page has more than 10 characters of reader-mode text.
   - **Add Bookmark** / **Remove Bookmark**, a separator, **Manage Bookmarks** and **History**. These show only outside
     /dev/null mode.
   - A separator, then one item per permission stored for the current site, "Reset ⟨Permission⟩ Permission
     (Allowed|Blocked)", then a separator.
   - **Unmute Current Page**, only while the page is muted, then a separator.
   - **Settings**, which opens Settings › Browser.

iTerm2's toolbar also has an indicators view that mirrors the session's indicators. Jasper has no session indicators,
so it is left out.

### The address field

- **Placeholder:** "Search or enter website name".
- **Focus:** gaining focus selects all its text, and clicking again selects all. When the page is a search engine's
  results page, the field shows the search terms instead of the URL.
- **Suggestions** appear as you type, with "Loading suggestions…" while they load. The top row is selected. In order:
  1. **The typed address**, when the text has a scheme or contains a dot: "Navigate to “https://…”", detail "URL";
  2. **A web search:** "Search for “…”", detail "Web Search";
  3. **Search suggestions**, up to 5 from the engine's suggestion URL, detail "Search Suggestion";
  4. **Single-word hosts** such as `localhost` and `foo:8080`;
  5. **Bookmarks**, up to 10, by prefix, detail "Bookmark";
  6. **History**, detail "Visited N times".

  When the typed text is itself a search-results URL, its query ranks first.
- **Keys:**
  - Up and Down move the selection;
  - Tab takes the selected suggestion and loads it;
  - Escape restores the page's URL, selects it and hides the suggestions;
  - Enter loads the selected suggestion, or the typed text when none is selected, then moves focus to the page;
  - the platform's Undo and Redo work in the field.
- **Typed text:**
  - with a scheme, it loads as typed;
  - a domain, an IP address or a single word without spaces gets `https://`;
  - anything else becomes a web search *(call: iTerm2 drops such text when Enter beats its suggestions, and the code
    marks that as a TODO)*.
- **Schemes:** `http`, `https`, `file`, `about:` and `jasper-about:`. Chromium's `chrome:` pages are not offered
  *(call)*.
- **Search engines:**

  | Engine | Search URL | Suggestion URL |
  |---|---|---|
  | Google (default) | `https://google.com/search?q=%s` | `https://suggestqueries.google.com/complete/search?client=firefox&q=%s` |
  | DuckDuckGo | `https://duckduckgo.com/?q=%s` | `https://duckduckgo.com/ac/?q=%s&type=list` |
  | Bing | `https://www.bing.com/search?q=%s` | `https://www.bing.com/osjson.aspx?query=%s` |
  | Kagi | `https://kagi.com/search?q=%s` | `https://kagi.com/api/autosuggest?q=%s` |
  | Custom | user's URL, must contain `%s` | user's URL, must contain `%s` |

  The placeholder is `%s`, where iTerm2 uses `%@` *(call: `%s` is Chrome's convention for custom search engines)*.
- **Link hovering.** While the pointer is over a link, its URL shows at the bottom left of the page
  (`onStatusMessage`).

## 7. The Web menu and keys

- **The Web menu** is a plugin top-level menu, in the place the look gives plugin menus *(call: iTerm2 puts it right
  after Session)*. It shows only while the window's current item is a browser, which needs an SDK addition: a plugin
  menu shown only while one of the plugin's items is current in that window. Its items:
  - **Open Location…**, which focuses the address field;
  - a separator, then **Back** and **Forward**;
  - a separator, then **Reload Page** and **History**, which opens the history page (in /dev/null mode too, as iTerm2's
    Cmd+Y does).
- **Its keys win.** The items are plugin actions with `ShortcutContext.content()` defaults. They are bindable in
  Settings › Keymap and win over the same keys bound elsewhere while a Browser plugin item is current. That covers the
  DevTools pane too. iTerm2 strips those keys from other menu items for that time.
  - In Jasper on macOS this takes Cmd+R from Search Shell History inside browser items.
  - Menus go on showing each action's own key, so Search Shell History still shows Cmd+R *(call)*.

| Action | macOS (iTerm2's keys) | Windows and Linux *(call)* |
|---|---|---|
| Open Location… | Cmd+L | Ctrl+L |
| Back | Cmd+[ | Alt+Left |
| Forward | Cmd+] | Alt+Right |
| Reload Page | Cmd+R | Ctrl+R |
| History | Cmd+Y | Ctrl+H |
| Find, Find Next, Find Previous | Cmd+F, Cmd+G, Cmd+Shift+G | Ctrl+F, F3, Shift+F3, as in Editor tabs |
| Zoom in, out, reset | Jasper's Increase, Decrease and Reset Font Size: Cmd+=, Cmd+-, Cmd+0 | the same actions: Ctrl+Shift+=, Ctrl+Shift+-, Ctrl+Shift+0, as in Editor tabs |
| Copy, Paste | Jasper's | Jasper's; Ctrl+C and Ctrl+V also reach the page |
| Back, Forward gestures | two-finger swipe | the mouse's back and forward buttons |
| Magnify | trackpad pinch | Ctrl+wheel, which touchpad pinch sends |

- **The Windows and Linux keys** are those platforms' browser conventions for the Web menu, and the Editor's content
  keys for Find and zoom. Each has one key, as Jasper's built-in actions have. In a browser item, Ctrl+letter keys are
  free: they don't need to reach a program as they must in a terminal.
- **The rest of the keymap applies** in a browser item as everywhere else. Every browser command is a bindable action
  listed in Settings › Keymap.

## 8. Links and popups inside a page

| Gesture | macOS | Windows and Linux | Opens the link |
|---|---|---|---|
| Click | click | click | in the same item |
| New tab | Cmd-click | Ctrl-click | in a new tab next to this one |
| Split *(call)* | Cmd+Shift-click | Ctrl+Shift-click | in a split, to the right when the pane is wider than tall, otherwise below |

- **Why the split modifier is a call.** iTerm2's documentation and code disagree:
  - the documentation: Cmd+Shift-click gives a vertical split and Cmd+Shift+Option-click a horizontal one;
  - the code: Cmd+Option-click gives a vertical split, Cmd+Option+Shift-click a horizontal one, and Cmd+Shift-click
    a new tab.

  Both use Option, which starts Jasper's pane drag. Cmd+Shift-click therefore takes the direction rule iTerm2 uses for
  Cmd+Shift-click in a terminal.
- **A middle click** opens a new tab too *(call: Chromium reports it as a new-tab click)*.
- **Other modified clicks** load the link in the same item, because iTerm2 checks only Cmd and Option. That covers
  Shift-click (a new window in Chromium) and Alt-click (a download in Chromium) *(call)*. On macOS, Ctrl-click opens
  the right-click menu.
- **How Jasper tells the gestures apart.** It forwards every mouse press to Chromium itself, so it records the last
  press and its modifiers. `onOpenURLFromTab` and `onBeforePopup` give no disposition, so Jasper uses that record
  (section 4).
- **`target=_blank` links and `window.open`** from a user's click open a new tab next to this one. The new tab shares
  the opener's data store and keeps its `window.opener`. How JCEF can keep the opener is the spike's question.
- **Popups without a user gesture are blocked**, with no indicator. A popup counts as the user's when it follows a press
  or key that Jasper forwarded, within Chromium's user-activation window.

## 9. Links from the terminal and elsewhere

### The decision

Links the user opens inside Jasper (origin `CLICK`) take this decision, in order. URLs from outside (origin `EXTERNAL`)
skip it (below).

1. **The system browser** when the Browser plugin is off, or when the scheme is not `http` or `https`. `ftp` and
   `mailto` therefore always go to the desktop's handler.
2. **Jasper's browser, with no prompt,** when Jasper is the system's default handler for the scheme.
3. **Otherwise, the Open Links From Terminal setting:**
   - **Always Ask** (the default) shows the prompt below;
   - **Built-in Browser** opens a new tab next to the current one;
   - **Default Browser** opens the system browser.

**The prompt** is Jasper's in-window modal:
- heading "Open in Jasper?";
- text "Jasper can display web pages! Would you like to open this link in Jasper?";
- buttons **Use Default Browser**, **Open in Jasper** and **Cancel**;
- a "Remember my choice" checkbox, unticked. Ticking it and choosing writes the setting. Cancel opens nothing.
- If the window already shows a modal, such as Settings or About, the prompt can't show. The link then opens in the
  system browser that one time *(call)*.

### Where links come from

- **Clicks in the terminal.** A Cmd-click (Ctrl-click; on Windows and Linux, Ctrl+Shift-click while a program asks for
  mouse reports) on a written URL or an OSC 8 link follows the decision.
- **Local web files** *(user, 2026-10-27: "Mirror iTerm2")*. A Cmd-click (Ctrl-click) on an existing local file with
  one of these extensions can open it in Jasper's browser: HTML, XML, SVG, CSS, JavaScript, PDF, PNG, JPEG, GIF, WebP
  or HEIC.
  - **With the Browser plugin on,** Jasper asks with a modal:
    - heading "Open in Jasper?";
    - text "Jasper can display files like this in its built-in web browser! Would you like to open this link in
      Jasper?";
    - buttons **Use Default Editor** (iTerm2's Use Default App, here the file rule in `docs/terminal.md`),
      **Open in Jasper** (a new browser tab with its `file:` URL) and **Cancel**;
    - a "Remember my choice" checkbox, unticked.
  - **The remembered answer** is kept apart from the link setting, as the Settings › Browser choice "Open Local Web
    Files": Always Ask (default), Built-in Browser, Default Editor.
  - **Other files,** and every file while the plugin is off, open as they do today.
- **Split clicks.** A Cmd+Shift-click (Ctrl+Shift-click) opens the link in Jasper's browser in a split, to the right
  when the pane is wider than tall and otherwise below. There is no prompt: iTerm2 calls this "implied consent".
  - **The direction** is the host's to choose, since it knows the pane's size in pixels. It passes `SPLIT_RIGHT` or
    `SPLIT_DOWN` with the link.
  - **When it applies:** only when rule 3 would not send the link to the system browser. Otherwise the gesture opens
    the link as a Cmd-click does.
  - **Windows and Linux under mouse reports.** While a program asks for mouse reports, Ctrl+Shift-click keeps its
    present meaning there: it is the click that opens a link, and it follows the decision. The split click therefore
    works only while no program asks for mouse reports *(call: nothing that works today stops working)*.
- **The terminal's right-click menu.** When the selection is a URL, it adds **Open URL in Split Right** and **Open URL
  in Split Down** (iTerm2's Vertical and Horizontal Split Pane), but only when the URL would open in Jasper's browser.
  As in iTerm2, these follow the selection, not the pointer; double-clicking a URL selects it whole.
- **OSC 8 `target=NAME`.**
  - If a browser item with that target exists in any window, the URL loads there; that item is not brought to the
    front.
  - Otherwise a new browser tab opens with that target, and later clicks reuse it.
  - The target is saved with the item.
- **Links from plugins and from Jasper itself** follow the decision. That covers Jasper's own Help, About and Settings
  Help links and plugin homepages, as iTerm2 routes its own help links. It also covers the Editor's and Remote's
  links, and links in the Markdown preview.
- **URLs from outside** open in Jasper's browser, with no prompt. Examples are `jasper <url>`, the system handing
  Jasper a URL because it is the default browser, and `open -a Jasper <url>`.
  - They open in a new tab in the most recently active window, or in a new window when none is open.
  - With the plugin off, `jasper <url>` opens the system browser.
  - If Jasper is itself the system's default, the app shows a notification that offers to turn the Browser plugin on,
    so the URL never loops back to Jasper.
  - `file:` URLs from outside keep opening as files do today, in the Default editor *(call)*.
- **URLs programs open** (`gh auth login`, `aws sso login`) are not caught. They reach whichever browser the system
  uses, which is Jasper only when the user made it the default. Jasper does not set `BROWSER`.

### Registering as a browser

Jasper declares that it can handle `http` and `https`, so the user can make it the default browser as iTerm2 allows. It
leaves the choice to the user:
- **macOS:** `CFBundleURLTypes` for `http` and `https`. The app installs an open-URI handler; today only the open-file
  handler is installed.
- **Windows:** the installer registers the URL associations under `RegisteredApplications`.
- **Linux:** the `.desktop` file lists `x-scheme-handler/http` and `x-scheme-handler/https`, with `%u`.
- **The launcher** accepts an `http` or `https` URL as an argument, beside paths. Today it accepts only paths.

Plan 4 owns all of these.

### Changes to plan 3's routing (built, not merged)

- **`LinkOrigin`** becomes `CLICK` and `EXTERNAL`. `PROGRAM`, `openProgramLink` and the reserved `jasper open-url` go,
  and `jasper <url>` becomes `EXTERNAL`.
- **`WebBrowser.open`** also receives the placement (a new tab next to the current one, or `SPLIT_RIGHT` or
  `SPLIT_DOWN`) and the OSC 8 target.
- **jasper-terminal's link opener.** The public `setLinkOpener(Consumer<String>)` becomes a consumer of a value that
  carries the URL, the gesture (open or split) and the OSC 8 target. The terminal's documented app-facing allowlist
  changes with it.
  - **The split click:** today the mouse routing ignores Shift on links, so it must tell the split click apart.
  - **OSC 8 targets:** JediTerm's hyperlink filter sees only the URI. `ShellIntegrationFilter` therefore keeps each
    link's target, and the cells must reach it. `HyperlinkStyle` equality ignores the link (AGENTS.md), so the target
    can't ride on the style alone.
- **The terminal's context-menu section** gets the selection, so plugin items can show only for a URL.
- **The plugin-off notification** for URLs from outside is app code.
- **New SDK versions.** Plan 3's types say `@since 0.21.0`, but main is at 0.22.0, so they take the next version.

## 10. Page features

### Find

- **The find bar is the Browser's own,** as the Editor's is, and built from SDK nodes. Jasper's Find, Find Next and Find
  Previous reach it through the host's routing. It has Match case and Regular expression, as Jasper's terminal does.
  Enter searches forward, and closing the bar clears the highlights.
- **The search runs in page scripts**, as iTerm2's does, because Chromium's own find has no regular expressions.
  - It reaches into frames, and opens collapsed content such as `details`, `aria-expanded=false` and
    `hidden=until-found`.
  - Queries are at most 1,000 characters.
  - The match count shows in the find bar.
- **Match colours** are iTerm2's: every match yellow (`#FFFF00`), the current one orange (`#FF9632`).

### Zoom

- **The default** comes from Settings › Browser › Page zoom: 5% to 500%, 100% by default.
- **Changing it.** Increase, Decrease and Reset Font Size change the item's zoom in steps of 5%, kept between 5% and
  500%. The zoom is saved with the item (iTerm2 saves it in the session's own copy of the profile).
- **Magnifying.** Pinch (Ctrl+wheel) magnifies the page and is not saved.

### Downloads

- **What downloads.** Chromium decides whether a response is shown or downloaded *(call: iTerm2 lists WebKit's
  displayable types itself)*.
- **Where files go.** Files go, without asking, to the Downloads folder setting. That defaults to the system's own
  folder: `~/Downloads` on macOS, the Downloads known folder on Windows and the XDG download directory on Linux.
  - A clashing name becomes "name (1).ext".
  - The file gets the system's downloaded-file mark: quarantine on macOS, Mark of the Web on Windows, nothing on Linux.
    If the mark fails, a warning says so.
- **The Downloads menu.** A top-level **Downloads** menu appears with the first download, in the place the look gives
  plugin menus. It has **Clear All**, then one submenu per download, titled with the name and its progress or result.
  Its entries are not actions, so they need an SDK addition: menu entries that carry their own title and callback and
  are not listed in the palette or the keymap. Each submenu has:
  - **Stop**, while running;
  - **Open**;
  - **Show in Finder** (Show in Explorer, Show in Folder);
  - **Remove from List**.

  iTerm2's Get Info is left out.
- **Notifications:** "Download finished for “NAME”." and "Download failed for “NAME”.".
- **No resume.** A navigation that turns into a download shows no error page.

### History

- **What is recorded.** One entry per top-level page load, apart from `jasper-about:` pages. Each entry has the URL,
  title, time and how the page was reached: typed, link, reload, back or forward, redirect, form submission or other.
  A per-host-and-path table keeps visit counts and the first and last visits.
- **Nothing expires.**
- **The history page,** `jasper-about:history`, opens from Web › History and the menu button's **History**.
  - Heading "Browsing History", subtitle "View and manage your browsing history".
  - **Clear All History**, which confirms "This will delete all browsing history. This action cannot be undone.
    Continue?".
  - A search field, "Search your browsing history…", searching the URL and title as you type.
  - Entries grouped under "Today", "Yesterday" or the date; each shows the time, the title (or "Untitled") and the URL
    as a link.
  - × deletes an entry after "Delete this history entry?".
  - **Load More** pages in steps of 50, and an empty list reads "No browsing history".

### Bookmarks

- **Adding.** **Add Bookmark** saves at once, then opens the **Edit Bookmark** panel. The panel:
  - shows **Title** and **URL** (read-only) and a **Tags** token field ("Enter tags…");
  - has **Delete Bookmark** and **Save**;
  - is the only place tags are edited, and closes when the page navigates.
- **Deleting and removing.** **Delete Bookmark** confirms "Are you sure you want to delete this bookmark?", then shows
  a card, "Bookmark Deleted". **Remove Bookmark** shows "Bookmark Removed".
- **The bookmarks page,** `jasper-about:bookmarks`, opens from **Manage Bookmarks**.
  - Heading "Bookmarks", subtitle "Manage and organize your saved websites".
  - **Clear All**, which confirms "This will delete all bookmarks. This action cannot be undone. Continue?".
  - A search field, "Search bookmarks...".
  - "Sort by:" Date Added, Title or URL, and "Filter by tags:" chips.
  - Each row shows "Added DATE"; × deletes it after "Delete this bookmark?".
  - **Load More**, and an empty list reads "No bookmarks found".

### Page tools

- **Reader mode.** Readability.js lays the article over the page, titled "Article" when it has no title. Escape or
  **Exit Reader Mode** removes it.
- **Distraction removal.** It gives a crosshair pointer and outlines overlays in red as the pointer passes. A click
  hides that overlay and its backdrops. Escape or **Exit Distraction Removal** ends it, and hidden elements stay hidden.
- **Remove Element** (right-click menu) hides the overlay under the pointer.
- **Resetting.** All three reset when the page navigates.
- **View Source** (right-click menu) shows the page's source in the same tab at `jasper-about:source`.
  - The heading is "Page Source" and the URL, with the HTML highlighted and its URLs clickable.
  - The source is fetched again over http(s) without cookies, and only a 200 response is used. Otherwise it is the
    page's current HTML.
- **Save Page As…** (right-click menu) asks for a location with Jasper's save chooser and saves a folder named after
  the host. The folder holds `index.html` and `resources/`, with the links rewritten.
  - The chooser saves locally only; iTerm2's SSH destinations are left out.
  - The saved folder is shown in the file manager.
  - A failure says "Save Failed" / "Could not save the page: …".
- **Print…** (right-click menu) opens Chromium's print dialog. iTerm2 fits the page to the paper width, but JCEF's
  `print()` takes no settings.
- **Inspect Element** (right-click menu) opens Chromium's DevTools in a pane below the page *(call: WebKit's inspector
  docks at the bottom of the page by default)*.
  - **How:** this needs an off-screen DevTools browser, through the `createDevToolsBrowser` override found on
    2026-10-21. If plan 4 cannot make that work, DevTools opens in its own top-level window. That is a separate window,
    not a child one, so it covers none of Jasper's overlays.
  - **No menu item or default shortcut,** as in iTerm2.

## 11. The page's right-click menu

Jasper draws the menu, because Chromium shows no native menu off screen. `PluginPresentationCheck` forbids a plugin's
own popup menu, so this needs an SDK addition: a menu shown at a point over custom content, from a list of entries. In
order:

1. **Chromium's own items** for what is under the pointer:
   - Back, Forward and Reload;
   - **Open Link in New Tab**, Copy Link and Save Link As, which downloads;
   - the image items;
   - Cut, Copy, Paste and Select All;
   - **Search with ⟨engine⟩** in place of Chromium's own search item, which opens a new tab.
2. **The host's pane items**, as the host offers them for any content item: the Split submenu (which includes Browser
   Right and Down), Move Pane to Tab and Close Pane. They are the counterpart of iTerm2's Split Pane Vertically and
   Horizontally and its Move Browser to Split Pane, Tab and Window items.
3. **Page items:**
   - **Save Page As…**, **Print…** and **Copy Page Title**;
   - a separator;
   - **View Source**, **Remove Element** and **Inspect Element**.

*(left out)* iTerm2 has more items that Jasper has no counterpart for:
- Swap With Session…;
- Add Named Mark…;
- Smart Selection actions;
- the synonym, colour, base64 and number conversions of a selection.

## 12. Privacy, permissions and data

- **One data store.** Every browser item outside /dev/null mode shares one persistent store for cookies and cache, and
  one database for history, bookmarks and permissions. They live in the plugin's `data/` folder.
- **/dev/null mode** is the Settings › Browser › Privacy & Data checkbox "Use /dev/null mode (no history, cookies, or other
  data saved)". It is off by default.
  - It applies to browser items opened after it is set *(call: iTerm2 sets it per profile, and Jasper has no
    profiles)*.
  - Such an item gets an in-memory store, shared with the tabs it opens. The store is discarded when the last of those
    tabs closes.
  - Its history, bookmarks and permissions stay in memory. HTTP sign-ins are never remembered.
  - The bookmark and history menu items are hidden, and the /dev/null indicator shows.
- **Permissions:** camera, microphone, both together, notifications and audio playback.
  - **The prompt** is a modal with a heading such as "Allow Camera" and text such as "The website ORIGIN wants to use
    your camera." or "…wants to send you notifications.", with **Allow** and **Block**.
  - **What is remembered:** only an explicit Allow or Block is stored, per origin. A denial by the system is not
    stored.
  - **Where they come from:** camera and microphone through `onRequestMediaAccessPermission`.
  - **Page notifications** go through a script that replaces `window.Notification`. Each shows as a Jasper notification
    with its title and body, without its icon, and closing it from the page removes it.
  - **Location** is left out (section 21).
- **The permissions page,** `jasper-about:permissions`, is "Site Permissions".
  - **Clear All Permissions.**
  - A search field, "Search by website or permission type…", plus type and status filters (All Statuses, Allowed,
    Blocked).
  - **Clear** per site, and × per permission after "Revoke X permission for ORIGIN?".
  - An empty list reads "No permissions found".
  - Revoking reloads the tabs that show that origin.
- **Audio.** The first time an origin with no stored choice plays sound, the page is muted at once, and a bar across
  the top of the item asks "Audio was muted. Allow playback by ORIGIN?". It offers **Allow Once**, **Allow Always**,
  **Deny Once** and **Deny Always**; the Always answers are stored. **Unmute Current Page** in the menu button unmutes.
- **Clearing data.** Settings › Browser › Privacy & Data has:
  - **Clear Cookies**, which confirms "This will remove all cookies from all websites. Continue?" (the text iTerm2's
    settings handler injects);
  - **Clear Website Data**, which confirms "This will remove all browsing data including cookies, cache, and local
    storage. Continue?". It also erases the history, bookmarks and permissions database, then reports "All website
    data has been cleared successfully!".
- **Ad blocking** is off by default.
  - **Settings:** Enable Ad Blocking, Filter List URL (by default
    `https://easylist-downloads.adblockplus.org/easylist_content_blocker.json`), **Update Now** and the rule count.
  - **How it applies:** the list is in WebKit's content-blocker format. Jasper applies its rules to Chromium's resource
    requests and injects its element-hiding rules as CSS.
  - **Updates:** an hourly check downloads the list again once 24 hours have passed, and the compiled list is cached in
    `data/`. After 7 days without an update, Settings shows that the list "haven't been updated for N days".
- **HTTP proxy** is off by default. Settings: **Enable HTTP Proxy** and **Proxy Server**, host `127.0.0.1` and port
  `8118`. It is applied through the request context's proxy preference.

## 13. Logins

The Credential Vault plays iTerm2's password manager. Without the Vault plugin, none of this appears.

- **Field buttons.** Focusing a password field shows a 🔑 button inside the page, and a username field shows 🎫. Both
  are titled "Open Password Manager".
  - **The picker:** the button opens the Vault's picker, with its search set to the page's host.
  - **Filling:** choosing a login fills the field. From a username field, it fills the username, then focuses the
    password field that follows and fills it.
  - **Grants:** choosing in the picker lets the Browser read that login once, by the Vault's rule.
- **No form logins are saved.** iTerm2 saves nothing from web forms.
- **Browser logins are ordinary Vault logins** *(call: iTerm2 keeps them apart from terminal passwords, and Jasper has
  one vault for every plugin)*.
- **HTTP authentication** (Basic, Digest, NTLM) shows a modal.
  - **Heading:** "The website “HOST” requires a user name and password for “REALM”.", without the realm part when
    there is none.
  - **Fields** User name and Password; **buttons** OK, Cancel and **Password Manager** (the Vault picker).
  - **"Remember this password"** appears, like Password Manager, only for Basic outside /dev/null mode. The login is
    saved only after the top-level response succeeds (2xx), as the Vault login "scheme://host[:port] (realm)". The
    Browser gets a standing grant for it.
  - **Silent fill:** a remembered login fills without the dialog only while the Vault is unlocked and the grant stands.
    Otherwise the dialog shows.
  - **Frames:** a challenge whose origin differs from the top page's is asked but never remembered. JCEF reports no
    frame, so the origin stands in for it.
  - **A failed save** shows "Could not save the password for HOST".
- **Vault API additions:**
  - the picker with a starting search, anchored to a point on the page. `UiAnchor` covers only a mounted control today,
    so this is an SDK addition too;
  - creating a login that comes with a grant for the plugin that created it;
  - reading a granted login without any prompt, which answers empty when the Vault is locked or the grant is gone, for
    the silent fill;
  - opening the Vault manager.
- **Manage Saved Passwords** in Settings › Browser opens the Vault manager.

## 14. Internal pages

- **The scheme.** `jasper-about:` pages are bundled HTML, CSS and scripts in Jasper's theme colours, following the
  light or dark theme. They load nothing from the network and are not recorded in history.
- **`welcome`**, the default start page. Until onboarding is finished it sends the user to the onboarding pages.
  Afterwards it shows:
  - "Welcome Back";
  - "Top Sites" from visit counts, with **Refresh**;
  - "Quick Actions": Bookmarks, History, Settings and Permissions.
- **The onboarding pages:**
  - `onboarding-intro`, "Meet the Jasper Browser";
  - `onboarding-features`, "What Can It Do?", which lists only what this spec includes;
  - `onboarding-setup`, "Let’s Get You Set Up", with the Ad Blocker switch.

  iTerm2's Instant Replay switch and its "Create Your Browser Profile" page are left out.
- **The other pages:** `history`, `bookmarks` and `permissions` (section 10 and 12), `source` (View Source) and `error`.
- **The error page** shows a title and message, a **Try Again** button (Enter and Space retry too) and "Check your
  internet connection and try reloading the page."
  - **Titles:** "No Internet Connection", "Server Not Found", "The Connection Timed Out", "Can’t Connect to Server",
    "Network Connection Lost", "Too Many Redirects", "Page Unavailable", "File Not Found" and "Page Can’t Be Loaded".
  - **Certificate errors** show "Secure Connection Failed" with "Jasper can’t verify the identity of the website…", the
    reason and the certificate chain. Examples are a self-signed certificate, an unknown authority, an expiry or the
    wrong host. This much is iTerm2's.
  - **Continuing past it** follows Chrome's behaviour, beyond iTerm2 *(user, 2026-10-27: "keep 3", standing by the
    2026-10-21 "Chrome behaviour for certs")*.
    - A **Details** link on the error page reveals **Continue to this site (unsafe)**.
    - It allows that host with that certificate until Jasper quits, through `onCertificateError`'s callback.
    - While the page shows such a host, the address field shows "Not secure" in place of the favicon.
    - A /dev/null item's allowance ends with its store.
  - **Trust.** Certificates are checked against the system's trust store, so corporate authorities installed on the
    machine work. Chromium on Linux reads NSS's store rather than `/etc/ssl`, so plan 4 confirms what Linux users must
    do.
- **Settings** is not a page: the menu button's Settings opens Settings › Browser *(call)*.
- **`file:` URLs** use Chromium's own folder listing and file display *(call: iTerm2 draws its own because WebKit has
  none)*.

## 15. Other Jasper integrations

- **Command palette** (Open Quickly):
  - a **Bookmarks** scope ("Open Bookmark in Browser") and a **Browser History** scope ("Open Visited Site in Browser"),
    both searched in All;
  - an **Open URL in Browser** row when the query is an `https://` URL.

  A row opens in the current item when it is a browser, and in a new tab otherwise. iTerm2 also lists open sessions by
  title; Jasper's palette lists no tabs, so that is left out.
- **Snippets.** With a browser item current, **Insert** puts the snippet into the page's focused field (input, text
  area or editable element). With none focused, it pastes it, then puts the clipboard back. **Paste and run** also
  presses Return.
  - **SDK addition:** a content item may accept inserted text. Snippets uses that when the current item is not a
    terminal pane, so Snippets needs no Browser API.
- **Copy on select.** `terminal.copy_on_select` also copies page selections, when the mouse is released or the selection
  changes. The SDK has no read access to that setting yet: an SDK addition.
- **Status bar.** The host shows the item's location (the URL) as it does for every item, in every full window.
- **Notifications.** Download, bookmark, permission and page notifications are Jasper notifications in the plugin's
  groups.

## 16. Saving and restoring

- **What an item saves:** its URL, zoom, OSC 8 target and whether it is in /dev/null mode. It never saves form
  contents, credentials or cookies.
  - **The URL is saved whole,** as iTerm2 saves it *(call)*. `workspace.toml` is plain text, so a URL that carries a
    token keeps it there, as iTerm2's saved state does.
  - **A URL over the 4,096-character budget** is saved without its query and fragment. If it is still too long, the
    item saves no URL.
  - **`data:` and `blob:` pages** save no URL.
  - **Internal pages:** `jasper-about:error` saves the URL that failed, and `jasper-about:source` the page whose source
    it shows.
  - **/dev/null items** save no URL: they restore at the start page, in /dev/null mode *(call)*.
  - **No back and forward list.** iTerm2 restores WebKit's back and forward list, but CEF cannot rebuild one (section
    21).
- **Loading waits.** The restorer opens the item at once, and the page loads the first time it is shown. An item with
  no saved URL loads the start page.

## 17. Settings

Settings › Browser, stored in the plugin's own settings file:

| Group | Setting | Default |
|---|---|---|
| General | Start page | `jasper-about:welcome` |
| General | Page zoom | 100% (5%–500%) |
| General | Downloads folder | the system's Downloads folder |
| Link Opening | Open Links From Terminal: Always Ask, Built-in Browser, Default Browser | Always Ask |
| Link Opening | Open Local Web Files: Always Ask, Built-in Browser, Default Editor | Always Ask |
| Search Engine | Default Search Engine: Google, DuckDuckGo, Bing, Kagi, Custom | Google |
| Search Engine | Custom Search URL, Custom Search Suggestions URL (each must contain `%s`) | empty |
| Privacy & Data | Use /dev/null mode | off |
| Privacy & Data | Clear Cookies, Clear Website Data (buttons) | — |
| Privacy & Data | Manage Site Permissions (opens the permissions page), Manage Saved Passwords (opens the Vault; needs the Vault plugin) | — |
| Ad Blocking | Enable Ad Blocking; Filter List URL; Update Now; rule count | off; EasyList |
| Proxy | Enable HTTP Proxy; Proxy Server host and port | off; `127.0.0.1`, `8118` |

## 18. Markdown preview *(user)*

This section is unchanged from the 2026-10-21 design, except where marked.

- **Which preview draws.** With the Browser plugin on, Chromium draws the Editor's preview through the Browser's
  exported API (`dev.jasper.browser.api`). With it off, today's Swing preview stays. The Editor declares the Browser API
  as an optional requirement, and the API offers only what the preview needs *(call)*.
- **Markdown to HTML.** The Editor still converts with commonmark. It adds the GitHub features commonmark lacks:
  alerts, footnotes, autolinks and heading anchors.
- **Mermaid** blocks are drawn as diagrams, from a bundled Mermaid; nothing is fetched.
- **Code blocks** are highlighted by language in the Editor's syntax colours.
- **The look** is GitHub's layout in Jasper's theme colours, following light or dark.
- **Everything today's preview does stays:**
  - Source, Split and Preview;
  - scroll sync;
  - Find;
  - keyboard movement between links;
  - external images only after consent;
  - the size limits;
  - pausing while hidden.
- **Each view of a file** has its own preview.
- **Safety.** A Markdown file is untrusted: its scripts and event handlers never run. A content security policy allows
  only the bundled Mermaid and highlighter, and no network except images the user allowed.
- **Links** in the preview follow section 9.
- **Screen readers.** Settled: off-screen Chromium exposes nothing to Java accessibility. The Editor therefore keeps
  today's preview as a setting, "Markdown preview: Chromium or Basic".

## 19. Start-up and memory (the anti-goals) *(user)*

This section is unchanged from the 2026-10-21 design.

- **Plugin start only registers.** No JCEF class loads and no Chromium process starts.
- **Chromium starts on first use.** First use is the first browser item or Chromium preview shown, after the window is
  up.
- **Restored items** hold only their saved state until shown.
- **Measured against today's Jasper on plain `jbr`,** with the existing start-up and memory benchmarks:
  - plan 2 measures before and after the runtime switch;
  - plan 4 onward measures the plugin on and unused.

  Each must match within run-to-run noise, and each batch runs only with the user's OK.
- **Memory goes back.** After the last browser item and preview close and a short idle delay, Chromium's processes end
  and memory returns near the baseline.
  - In-process CEF cannot restart after it shuts down. Out-of-process `cef_server` can, but has no downloads, which
    this spec needs.
  - If the spike finds no way to give memory back and keep downloads, bring it to the user before plan 4.
- **AOT training** must not load JCEF.

## 20. Left out: iTerm2 behaviour Jasper has no counterpart for

| iTerm2 | Why it is left out |
|---|---|
| Browser profiles: the profile type, Initial URL, the per-profile Web tab, the default browser profile | Jasper has no profiles; the settings cover them (section 17) |
| The Browser Plugin gate, its upsell, locate button and code-signature check | Jasper bundles the browser |
| Instant Replay | No Jasper counterpart |
| AI chat: Ask AI…, "Act in Web Browser" tools, browser chat prompts | No Jasper counterpart |
| Python API: `async_load_url`, `async_eval_javascript`, toolbar hiding, per-domain prompts | Left out with the user's agreement on 2026-10-26 |
| Triggers and workgroups | No Jasper counterpart |
| Smart Selection: Cmd-click actions, quadruple-click select, right-click actions and conversions | No Jasper counterpart |
| Copy mode | No Jasper counterpart |
| Named marks, the Named Marks tool, Set Named Mark | No Jasper counterpart |
| Paste History, Advanced Paste, Composer, Broadcast Input | No Jasper counterpart |
| Global Search, Pick Result to Open, Find URLs, Use Selection for Find, Clear Find, Jump to Selection | No Jasper counterpart |
| Contact-card autofill and Open Autocomplete… | Needs macOS Contacts; no Jasper counterpart |
| The toolbar's indicators view and the Minimal theme's tab tint | No Jasper counterpart |
| iTerm2 Buddy streaming | Jasper's desk buddy is a different feature |
| `iterm2-ssh:` URLs and "Download or View File?" over SSH integration | Remote opens remote files in the Editor; no counterpart flow |
| Cmd+Option-click to open in the background | Cmd+Option starts Jasper's pane drag |
| Swap With Session…, Bury Session, hotkey windows, tab groups, locked layouts, tmux | No Jasper counterpart |
| Shell › Save Contents… and Save Selected Text… | Jasper's terminal has no such commands; Save Page As… stays |
| Saving pages to SSH locations | Jasper's save chooser saves locally only |
| Get Info on a download | Finder-only, and Jasper has no file-information view |
| Window › Password Manager (Option+Cmd+F) in a browser | Jasper's Search Vault Logins only copies; the field buttons fill |
| The terminal's Copy URL and Open Selection as URL | iTerm2 terminal features, not the browser's |
| The `urlHandlerCommand` and `browserBundleID` advanced settings | Jasper always uses the desktop's own handler |
| Show Toolbar, floating panes | Not in a stable iTerm2 yet |
| Web extensions | Hidden in iTerm2's release builds |

## 21. Known gaps the engine forces

- **Screen readers** get nothing from browser pages. Off-screen Chromium exposes no accessibility, whereas WebKit does
  in iTerm2.
- **The back and forward list is not restored,** because CEF has no way to rebuild one.
- **Location requests are refused.** JBR's JCEF has no geolocation permission hook, and Chromium without a location
  provider has none to offer.
- **Passkeys are unsupported,** as in iTerm2.
- **Print layout is Chromium's.** iTerm2 fits the page to the paper width, but JCEF's `print()` takes no settings.
- **Sign-ins inside frames** are told apart by origin only, because JCEF reports no frame for them.

## 22. Changes from the 2026-10-21 design

**Kept beyond iTerm2** *(user, 2026-10-27)*: Chrome's "Continue to this site (unsafe)" for certificate errors (section
14).

**Removed, because iTerm2 has none of them.** The user confirmed the marked ones on 2026-10-27 ("drop 1, 2 and 4"):
- the window toolbar's New Browser Tab button *(the user's 2026-10-21 ask)*;
- the Open in Default Browser button and the "way out" for failed sign-ins *(the user's ask)*;
- `BROWSER` in local shells, with the rule and setting for program-opened URLs and `jasper open-url` *(the user's
  original intent 3, offering Jasper's browser for `gh` and `aws` sign-ins)*;
- the Editor-style document bar, replaced by iTerm2's toolbar;
- the themed globe tab icon, replaced by the favicon;
- per-site zoom, replaced by per-item zoom with a default;
- saving form logins, with the Vault's new Website field and its format change;
- Developer Tools' shortcuts and the ⋮ menu;
- the start page that listed bookmarks;
- "Clear Browsing Data", replaced by Clear Cookies and Clear Website Data;
- the general exported browser API, now only what the Markdown preview needs.

**Added, because iTerm2 has them:**
- navigation: long-press history, address suggestions with search engines, the Web menu with its key takeover, and
  Cmd+Shift-click splits;
- link routing: the terminal's split menu items, OSC 8 targets, and registering as a browser;
- privacy and data: /dev/null mode, site permissions with the permissions page, page notifications, audio muting, ad
  blocking, the HTTP proxy and the HTTP sign-in dialog;
- page tools: reader mode, distraction removal, Remove Element, View Source, Save Page As, Print, Copy Page Title and
  Search with;
- pages and menus: the welcome and onboarding pages, the history and bookmarks pages, tags and the Downloads menu;
- integrations: the Vault field buttons, the palette scopes, Snippets in page fields, copy on select and the link-hover
  URL.

**The designer's calls,** for the user to check:
- **Windows:** building on the full windows design (section 2).
- **Menus and keys:**
  - where New Browser Tab and New Browser Window sit (section 5);
  - the Web menu's place (section 7);
  - menus showing Cmd+R on both Reload Page and Search Shell History (section 7);
  - the Windows and Linux keys (section 7).
- **Terminal links:**
  - Ctrl+Shift-click keeping its present meaning under mouse reports on Windows and Linux (section 9);
  - the link prompt falling back to the system browser over a modal (section 9).
- **Page behaviour:**
  - the split modifier inside pages, middle clicks, and other modified clicks (section 8);
  - Chromium deciding what downloads (section 10);
  - Chromium's `file:` pages (section 14);
  - DevTools in a pane below (section 10);
  - Chromium's user agent (section 5).
- **Search:**
  - typed text that isn't an address becomes a search (section 6);
  - `%s` as the search placeholder (section 6).
- **Privacy and data:**
  - one /dev/null setting instead of per profile (section 12);
  - /dev/null items restoring at the start page (section 16);
  - saving URLs whole, tokens included, as iTerm2 does (section 16);
  - one Vault for browser and terminal logins (section 13).
- **Settings:** the plugin on by default (section 5).

## 23. Delivery

The plans, in order:

1. **Spike (throwaway, built, not yet run by the user):** JCEF off screen on every platform. The user runs it and
   reports back.
2. **Runtime switch:** the `jbrsdk_jcef` toolchain, CI and packaging, Chromium's native parts and the licence notices.
3. **Link routing:** built on `claude/browser-link-routing`.
   - It is rebased onto main after this spec is approved.
   - An amendment makes the section 9 changes:
     - the `CLICK` and `EXTERNAL` origins, with `EXTERNAL` skipping the decision;
     - the placement and target for `WebBrowser.open`;
     - the link opener's value;
     - the selection in the terminal's menu section;
     - the plugin-off notification;
     - the new `@since` versions.
4. **The browser core** (`plugins/browser`, `:jasper-plugin-browser`, exporting `dev.jasper.browser.api`). It starts
   once full windows are on main.
   - the item, the toolbar, the address field with suggestions and search engines;
   - the Web menu and keys, links and popups in pages, and page dialogs;
   - the tab title and favicon, link hovering, find, zoom, the right-click menu and the error pages;
   - the routing setting and prompt, split clicks, the terminal's menu items and OSC 8 targets;
   - registering as a browser: the open-URI handler, the launcher's URL argument and each platform's registration;
   - the settings, restore, and the lazy start of section 19;
   - the SDK additions these need.
5. **History, bookmarks and Jasper integrations:**
   - the history and bookmarks pages and the Edit Bookmark panel;
   - the palette scopes, the welcome and onboarding pages;
   - Snippets in page fields and copy on select.
6. **Privacy:** /dev/null mode, permissions and the permissions page, page notifications, audio muting, clearing data,
   ad blocking and the proxy.
7. **Page tools:** downloads and the Downloads menu, Save Page As, View Source, reader mode, distraction removal and
   Remove Element.
8. **Logins:** the Vault API additions, the field buttons and the HTTP sign-in dialog.
9. **The Chromium Markdown preview** in the Editor.
10. **Documentation:**
    - a new `docs/browser.md`;
    - `README.md`'s bundled plugin list;
    - `docs/README.md`, `docs/keyboard-shortcuts.md`, `docs/configuration.md`, `docs/plugin-apis.md`,
      `docs/terminal.md` (link clicks) and `docs/packaging.md` (the plugin count and browser registration).

## 24. Testing

- **Headless tests** cover:
  - the link decision and the prompt's remembered choice, split clicks and OSC 8 targets;
  - address-field parsing, suggestion order and search URLs;
  - the Web menu's visibility and key takeover, and the per-platform default keys and their clash checks;
  - restore state;
  - the history, bookmark and permission stores, and /dev/null isolation;
  - the ad-block rule compiler;
  - Vault login names and when to fill silently;
  - Markdown-to-HTML output, its sanitising and its content security policy.
- **The page scripts** (find, reader mode, field buttons, notifications, audio, Snippets insert) are plain files with
  their own tests where the build can run them headless. Otherwise the user checks them by hand.
- **The Chromium layer** stays thin and concrete, with no engine interface or test fake. The user checks it by hand
  on each platform.
- **SDK additions** are covered by the testkit and the contract cases.
- **Start-up and memory** use the existing benchmarks, run with the user's OK.

## 25. Left to the builder

- Exact SDK names for the additions:
  - the image tab icon and a new tab next to the current one;
  - the Web menu's visibility, menu entries that are not actions, and a menu at a point over content;
  - the toolbar's node capabilities and a page-point anchor;
  - inserted text for Snippets, and read access to `terminal.copy_on_select`;
  - the terminal menu's selection.
- Where the database lives and its format. SQLite is the likely choice, with the Browser's own copy.
- How the content-blocker rules are matched, the idle delay before Chromium stops, and the cache cap.
- How Jasper tells that it is the system's default browser on each platform.
- The page scripts, and how pinch and swipe gestures reach the page on each platform.
- Icon artwork, following `docs/sdk-icons.md`.

## 26. Open questions for the user

Both questions are answered.

1. **The 2026-10-21 requests iTerm2 lacks.** The user, 2026-10-27: "drop 1, 2 and 4, keep 3".
   - Dropped: the toolbar's New Browser Tab button, Open in Default Browser on every page, and `BROWSER` in local shells.
   - Kept: Chrome's "Continue to this site (unsafe)" (section 14).
2. **Local web files.** The user, 2026-10-27: "Mirror iTerm2" (section 9).
