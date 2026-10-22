# Jasper Browser plugin

Status: **Superseded on 2026-10-26** by the [iTerm2-parity design](2026-10-26-jasper-browser-plugin-design.md): the user
narrowed the scope to "mirror the behavior of iTerms built-in browser. Nothing more nothing less." Kept as the record of
plans 1 and 3. Was: **Approved by the user on 2026-10-21** ("Looks good to me - lets go ahead and write out the plan"). **Refreshed
against main on 2026-10-22**, after the tab rework and its pane flexibility amendment landed. Decisions marked *(user)* are the
user's own. Decisions marked *(call)* are the designer's calls. Most of them were shown to the user in the conversation;
the ones made after the builder check (in sections 4, 5, 7, 8 and 9) are listed in the hand-over for the user to check.
Anything not marked is a constraint or a fact checked on 2026-10-21.

This design follows the [tabs and panes spec](2026-10-20-jasper-tabs-and-panes-design.md) and its
[pane flexibility amendment](2026-10-21-jasper-pane-flexibility-design.md), both on main.

## 1. Intent

The user wants a basic, built-in web browser so people can stay inside Jasper: "The goal is to keep the user in the
app as much as possible." The user is not after full Chrome. They want "a basic browser like you'd see on a linux desktop
like SeaMonkey".

What it is for, in the user's words and order:

1. A better Markdown previewer.
2. Opening links the terminal presents.
3. When a program in the terminal opens a browser (for sign-in, say), offering Jasper's browser instead.
4. Browsing on purpose: a menu bar item and a toolbar item open a new browser tab.
5. Perhaps, later, a base that other plugins use to show web apps such as Jira or GitHub. The user is unsure about this one.

## 2. Goals, non-goals, anti-goals

**Goals**

- A Chromium browser as a bundled plugin, with pages as workspace tabs and panes.
- Routing of terminal link clicks and program-opened URLs, with the user's choice remembered.
- Basic browser features: downloads, developer tools, find in page, zoom, saved logins, bookmarks (all *(user)*), and
  history *(call)*.
- A Markdown preview drawn by Chromium, with Mermaid diagrams, code highlighting and GitHub-style rendering in Jasper's
  theme colours *(user)*.
- macOS, Windows and Linux, on x64 and aarch64, with per-platform shortcuts *(user)*.
- An exported browser API that other plugins can use *(call)*.

**Non-goals** *(call)*: extensions, sync, multiple profiles, private windows, printing, page permission prompts (camera,
microphone, location and page notifications are denied), and Jira or GitHub plugins.

**Anti-goals** *(user)*: "slowing down Jasper's start-up or eating memory when unused." Both are acceptance criteria
(section 9). A browser that meets every other goal but slows start-up, or holds memory while unused, has failed.

## 3. Constraints

- **Tabs and panes.** A window holds tabs and a tab holds panes. A pane holds exactly one item and has no tab strip or
  title bar. A tab is named after its focused pane's item. Every command is a bindable keymap action.
- **The SDK surface from the tab rework** (SDK 0.21.0, on main; checked on 2026-10-22):
  - `TabPlacement` has `NEW_TAB`, `SPLIT_RIGHT`, `SPLIT_DOWN` and `NEW_WINDOW`.
  - `Windows.splitItem(SplitItemSpec.of(id, label).withIcon(..), (window, placement) -> ...)` registers a split item.
    The Editor's split item, at `EditorPlugin.java:55` and `:126`, is the model.
  - `ContentTab.isActive()` and `onActive` mean focus, not visibility. To pause a hidden page, use Swing's showing state
    (`isShowing()`, `HierarchyEvent.SHOWING_CHANGED`).
  - A tab opens with `Windows.openTab(ContentSpec, WindowOwner, UiView, TabPlacement)`. A plugin's own Swing component
    goes in as `CustomContent`, as the Editor's `EditorTab` registers its document.
  - Restore works through `ContentSpec.withRestore(new RestoreState(kind, values))` (or `RestoreState.of(kind, key,
    value)`) and `context.windows().restorer(kind, ...)`.
    The state is limited to 32 entries and 4,096 characters, and holds no secrets. The restorer runs on the EDT, must
    not block or ask anything, and opens the item at once. A kind without a restorer is not saved.
  - The host gives the active item these, and draws no per-pane toolbar:
    - the header search field's title and host;
    - the status bar's location and the item's own status items;
    - `ContentCommand`s in the Tab menu and the right-click menu;
    - Find, Find Next and Find Previous, Copy and Paste, and Increase, Decrease and Reset Font Size: Jasper runs the
      item's enabled command with the id `find`, `find-next`, `find-previous`, `copy`, `paste`, `font-bigger`,
      `font-smaller` or `font-reset`;
    - Close Pane, the Split submenu and Move Pane to Tab ▸ (pane flexibility section 8), through the host's pane items.
  - While a tab is active, a content command's shortcut runs ahead of the Jasper actions bound to the same key, except
    the palette's shortcuts (`ContentTab.setCommands`).
- **Heavyweight children are out.** A native child window would cover everything Jasper paints over a pane, and would
  swallow the ⌘⌥ or Ctrl+Alt press that starts a pane drag. The things it would cover:
  - the pane-drag and tab-drag landing overlay;
  - inactive-pane dimming;
  - the command palette;
  - notification cards;
  - the kit's modal overlay and scrim;
  - popovers;
  - the dock's drag guides.

  Chromium must therefore use off-screen rendering (OSR) into a lightweight Swing component.
- **Architecture rules** (AGENTS.md):
  - SDK types appear only in `dev.jasper.app.plugins`.
  - Plugins compile against the SDK and the `api` packages they declare.
  - `PluginPresentationCheck` refuses presentation code in plugin classes, apart from named exceptions. The browser's
    rendering component needs one, as the Editor's `PreviewPane` has.
  - No interface without two real implementations.
  - Every shipped jar, font and bundled third-party asset needs a `notices.toml` entry.
- **Tests stay headless.** CI cannot show Chromium. GUI launches and benchmarks are the user's (AGENTS.md). The user
  approves each batch of measurement launches.
- **Jasper is an MVP.** No compatibility shims and no migration paths.
- **No animations.** The document bar's loading indication is static or a plain progress fill, with no motion effects.

## 4. Engine and packaging

- **Engine:** JCEF, the Java binding to Chromium Embedded Framework, in **JetBrains' runtime build that includes it**
  (`jbr_jcef` / `jbrsdk_jcef`). This is what IntelliJ ships *(user: "carried into the app", and "Use JetBrains runtime build")*.
- **Chromium is part of every Jasper package** *(user)*. On 2026-10-21, for 25.0.4.1 b635.75, the `.tar.gz` sizes were:

  | Platform | `jbr` | `jbr_jcef` |
  |---|---|---|
  | macOS aarch64 | 98 MB | 246 MB |
  | Windows x64 | 100 MB | 287 MB |
  | Linux x64 | 104 MB | 260 MB |

  `jbr_jcef` builds exist for macOS (aarch64 and x64), Windows (x64 and aarch64) and Linux (x64 and aarch64).
- **What moves to the JCEF build:** the Gradle toolchain, development machines, CI runners and packaging. The toolchain
  check must pick the JCEF build, not just any JetBrains vendor JDK; the dev Mac's current JBR has no JCEF.
- **Packages carry Chromium's native parts.** On macOS that means the framework and its helper apps inside `Jasper.app`,
  signed. On Windows and Linux it means the CEF libraries and the helper executable.
- **The Chromium it ships.** JBR 25.0.4.1 b635.75 carries CEF 150.0.14, which is Chromium 150.0.7871.129. This was read
  from the macOS aarch64 build's `release` file on 2026-10-21 (`JCEF_VERSION_DETAILED=150.0.14-g7c1aa68-chromium-150.0.7871.129`).
  - **How far behind that is:** Chrome stable was 154 on 2026-09-22, and Chrome has moved to a two-week cadence since
    153. JBR moved from CEF 144 (b329.111, 2026-04-06) to 150 by October. jcefmaven is at 152 (2026-09-30), and it
    released 143, 146 and 152 in 2026.
  - **The user's rule:** "switch if needed".
  - **No switch** *(call)*. Both Java distributions lag Chrome by months, so switching would gain about two milestones
    today. It would cost JetBrains' maintained OSR integration, Chromium signed inside the runtime, and update cadence.
  - **Mitigations:**
    - each Jasper release takes the newest `jbr_jcef` build;
    - Settings › Browser and About show the Chromium version;
    - Open in Default Browser is always one click away.
- **Chromium's sandbox.** The spike checks whether Chromium's sandbox runs on each platform. Where it cannot, bring it
  back to the user before plan 2, because switching distributions does not fix that.
- **JBR also ships `cef_server.app`,** its out-of-process CEF server (section 9).
- **Rejected alternatives:**
  - jcefmaven, a plugin-carried Chromium, which was Chromium 152 on 2026-09-30;
  - JxBrowser, which is commercial;
  - JavaFX WebView, an old WebKit;
  - pure-Java renderers, which have no usable JavaScript.

## 5. The browser item

- **One page is one item in one pane.** New pages open with `NEW_TAB` *(user: "a new workspace tab next to the
  terminals")*. The Browser registers a split item, so every Split submenu lists **Browser Right** and **Browser Down**.
- **The tab title** is the page title. The tab shows the Browser's own themed globe icon, not the site's favicon
  *(call)*. SDK icons are names that Jasper draws in theme colours (`docs/sdk-icons.md`), and a web bitmap would break
  that rule in both looks.
- **The document bar** is layout B from the mockups *(user)*. It matches the Editor's document bar.
  - **Left:** a lock, the host in bold, then the path as breadcrumbs. Clicking it, or Focus Address, turns it into an
    editable address field.
  - **What the field accepts:** Enter loads an address. Text that isn't an address becomes a search with the engine set
    in Settings *(call)*. The field suggests matches from history and bookmarks *(call)*.
  - **Right:** icon buttons for Back, Forward, Reload (Stop while loading), Bookmark (☆/★), Open in Default Browser,
    and ⋮. The ⋮ menu holds Zoom, Find, Copy Address, Developer Tools, Downloads and Clear Browsing Data.
  - **Construction:** it is built from SDK nodes, which may include `Breadcrumbs`, `Field` and `Button`, so the Enterprise
    look restyles it like every other plugin view. Any missing node capability is an SDK addition.
- **Links that ask for a new window** (`target=_blank`, `window.open`) open a new Jasper tab beside the current one
  *(call)*. A sign-in popup must keep its opener so it can post back to it. JetBrains' `CefLifeSpanHandler.onBeforePopup`
  returns only cancel-or-allow, so the spike settles how, in this order of preference:
  1. a Jasper tab that keeps the opener;
  2. CEF's own native top-level popup window, for `window.open` popups only. It is a separate window, so it covers none
     of Jasper's overlays.

  Links with no opener use (`target=_blank` with `noopener`, or no script) always become Jasper tabs.
- **A page's right-click menu:**
  - Back, Forward and Reload;
  - Open Link in New Browser Tab, Open Link in Default Browser and Copy Link;
  - Copy and Paste;
  - Inspect;
  - the host's Split submenu and pane items.
- **New Browser Tab** is in the Tab menu, the palette and the toolbar (a `ToolbarItem`) *(user: "a menu bar item and
  toolbar item")*. It opens a start page listing bookmarks, with the address field focused *(call)*. It has no default
  shortcut until the clash check finds a free one.
- **Hidden pages are throttled, not frozen** *(call)*. A page that is not showing stops painting and gets Chrome's
  hidden-tab throttling, using Swing's showing state. Background uploads, websockets and sign-in polling keep running.

### Shortcuts *(call, through the tab rework's per-platform clash check)*

A browser tab is the plugin's own keymap place, and the most specific place wins. Inside a browser tab, two kinds of
shortcut behave differently:

- **The window's own shortcuts keep their keys:** New Tab, Split Right and Down, Close Pane, Close Tab, tab switching,
  Rename Tab and the palette. The browser's defaults avoid those keys, because an active tab's command would otherwise
  take them (section 3).
- **Other actions bound anywhere yield to the browser's standard key.** For example, ⌘R reloads the page in a browser
  tab, while Shell History's Search Shell History keeps ⌘R everywhere else.

Ordinary editing keys (Ctrl+C, Ctrl+V, Ctrl+X, Ctrl+A, Ctrl+Z outside macOS) go to the page.

| Action | macOS | Windows and Linux |
|---|---|---|
| Focus Address | ⌘L | Ctrl+L, Alt+D, F6 |
| Back / Forward | ⌘[ / ⌘] | Alt+Left / Alt+Right, mouse back and forward buttons |
| Reload | ⌘R | Ctrl+R, F5 |
| Find | ⌘F (Jasper's `find`) | Ctrl+F, plus Jasper's Ctrl+Shift+F |
| Zoom in / out / reset | ⌘= / ⌘− / ⌘0 (Jasper's `font_bigger` and its pair) | Ctrl+= / Ctrl+− / Ctrl+0 and Ctrl+wheel, plus Jasper's Ctrl+Shift+= / − / 0 |
| Developer Tools | ⌥⌘I | F12 (Ctrl+Shift+I is Rename Tab) |
| Bookmark Page | none, because ⌘D is Split Right | Ctrl+D |
| Open in Default Browser | none | none |
| New Browser Tab | none | none |

Every row is a bindable action listed in Settings › Keymap. Find, Find Next and Find Previous reach the page through
the host's routing. Zoom uses the same actions Editor tabs answer.

## 6. Link routing

- **Where URLs come from:**
  - **Links the user clicks:** http and https links in a terminal, links other plugins hand over, and Jasper's own web
    links: Settings' Help button and the Licenses window's and the plugin manager's homepages *(user)*.
  - **URLs a program opens**, such as `gh auth login` and `aws sso login`, through `BROWSER`.

  Only http and https are routed. File paths keep opening in the Editor, as the tabs and panes spec decides.
- **Defaults** *(user)*:
  - **Link clicks:** the first one asks; the answer is remembered.
  - **Program-opened URLs:** the system browser, with no prompt. Sign-in in an embedded browser often fails: Google
    blocks it, and single sign-on that checks the device usually passes only in the system browser. RFC 8252 recommends
    the system browser for this.
- **The first-click prompt** *(call)* is Jasper's usual in-window prompt: "Open this link in Jasper's browser or your
  default browser?" It has a button for each, and "Remember my choice" is ticked.
- **A way out** *(user: "so the user isn't stuck")*. Open in Default Browser, on every page, sends the current URL to
  the system browser.
- **New SDK surface:**
  - **Today:**
    - terminal link clicks go straight to the desktop browser through jasper-terminal's own default opener
      (`DesktopServices.openBrowser`). `TerminalView.setLinkOpener` is package-private and only tests use it;
    - the SDK has only `Platform.browse(URI)`;
    - `jasper <url>` ends as `jasper: unknown command <url>` (exit 2), or before the plugins start as "Jasper's plugins
      are not running" (exit 1).
  - **The design adds** the web browser role (plan 3): `Platform.browser(WebBrowser)`, which needs `browser.provide`,
    one plugin at a time. It hears each http(s) address with who asked (`LinkOrigin.CLICK` or `PROGRAM`), and the host
    asks it before falling back to the desktop browser. Terminal link clicks, plugins' `Platform.openInBrowser` and
    `jasper` CLI URLs go through it.
  - **jasper-terminal also needs** a supported way for the app to set a view's link opener: `TerminalView.setLinkOpener`
    becomes public. The app-facing allowlist is type-level and already lists `TerminalView`. With the Browser
    plugin off, nothing is claimed and everything goes to the system browser, as today.
- **`BROWSER`** (plan 4). Jasper sets `BROWSER` in its local shells to `jasper open-url`, so a program's URL reaches the
  running app as `LinkOrigin.PROGRAM`. The app applies the program-opened rule.
  - Python's `webbrowser` takes a `BROWSER` entry without `%s` as a single program name, and `gh` splits it with
    shlex, so plan 4 settles the value's form against the real `gh` and `aws`.
  - `gh` reads `GH_BROWSER`, then `BROWSER`; the AWS CLI goes through Python's `webbrowser`, which reads `BROWSER`.
    Confirm both on all three platforms, including how a Windows `BROWSER` value with spaces in its path is quoted.
  - Programs that call macOS `open` or Windows' `start` directly are not caught, nor are addresses opened outside
    Jasper: it registers no URL scheme handler (the launcher's second-launch path, `setOpenURIHandler`). That is
    accepted.
  - Remote (SSH) shells are not affected.
- **The `jasper` command:**
  - `jasper browser <url>` is a plugin subcommand (`[[subcommands]]` in `plugin.toml`, as `jasper editor` is). It opens
    the URL in Jasper's browser.
  - Plain `jasper <url>` recognises http(s) and goes through the web browser role.
  - `jasper open-url <url>` (plan 3) is the route programs use: one address, as `LinkOrigin.PROGRAM`. It is reserved,
    so no plugin can declare it, and `jasper --help` does not list it.

## 7. Features

- **Find in page:** through the host's Find routing (section 3), with match count and next and previous.
- **Zoom:** per site and remembered *(call)*. Ctrl+wheel works outside macOS, and pinch on a macOS trackpad.
- **Developer tools** open as a pane below the page (`SPLIT_DOWN`) *(call)*. They close like any pane.
- **Downloads** *(call)*:
  - files go to the system's Downloads folder without asking: `~/Downloads` on macOS, the Downloads known folder on
    Windows, the XDG download directory on Linux;
  - a finished download raises a Jasper notification with Open and Show in Finder (macOS), Show in Explorer (Windows)
    or Show in Folder (Linux);
  - recent downloads are listed in the ⋮ menu.
- **Saved logins** are stored in the Credential Vault *(call)*.
  - **Saving:** submitting a login form offers to save it. The next visit to the same site fills it in once the user
    confirms or chooses, and the Vault unlocks first if it is locked.
  - **No Vault, no saving:** with the Vault plugin off, logins aren't saved.
  - **New Vault API.** Today the Vault API reads only: `credentials()`, `credential(id)`, `pick`, `ensureUnlocked`, and
    the SSH key import. Saving needs Vault API additions under the Vault's per-plugin grants: create an account login
    tied to a site origin, and list the logins for an origin.
  - **A Vault format change.** The Vault's `Account` record (`id, name, username, auth, created, updated`) has no site,
    so this changes the encrypted format, with no migration path (MVP). My calls:
    - **The site is a Website field** on account entries, visible and editable in the Vault manager.
    - **Hand-added logins count too.** A login added by hand with a website is offered for filling, like one the
      browser saved.
    - **Grants.** The browser goes through the Vault's normal per-plugin grant (allow once or always) the first time.
      After that, each fill still needs the browser's own confirmation.
  - **Built, not borrowed.** As far as is known, Chrome's password manager is not part of CEF's OSR mode, so the browser
    builds form detection and filling itself. It must never put a password into a page whose origin differs from the
    saved one.
- **Bookmarks:**
  - ☆ in the document bar *(user's list; placement is a call)*;
  - a Bookmarks section in the palette, as Shell History and Snippets work there;
  - shown on the start page.
- **History:** a History section in the palette, address-field suggestions, and Clear Browsing Data *(call)*.
- **Certificate errors** follow Chrome's behaviour *(user)*. When a site's certificate isn't trusted (self-signed, an
  unknown CA, expired, or the wrong host), Jasper shows its own error page. The page names the problem and offers Open in
  Default Browser. Under Details, "Continue to this site (unsafe)" allows that host for the current session only, and
  the document bar's lock shows the page as not secure. Certificates are checked against the system's trust store, so
  corporate CAs installed on the machine work.
- **One persistent profile.** Cookies, cache, bookmarks and history live in the plugin's data folder,
  `<home>/plugins/<id>/data/` *(call)*. The cache size is capped (the builder picks the cap).

## 8. Markdown preview

- **With the Browser plugin on**, the Editor's preview is drawn by Chromium through the exported browser API. With it
  off, today's Swing preview stays *(call)*. The Editor declares the Browser API as an optional requirement, as Remote
  declares the Editor's.
- **The Editor still turns Markdown into HTML** (commonmark). It adds the GitHub features commonmark lacks: alerts
  (`> [!NOTE]` and the rest), footnotes, autolinks and heading anchors *(call)*.
- **Mermaid** fenced blocks are drawn as diagrams *(user)*. Mermaid ships with Jasper; nothing is fetched.
- **Code highlighting** colours fenced blocks by language *(user)*, using the Editor's syntax colours *(call)*.
- **The look** is GitHub's layout and features in **Jasper's theme colours**, following the light or dark theme *(user)*.
- **Everything the preview does today stays:**
  - Source, Split and Preview;
  - scroll sync;
  - Find;
  - keyboard movement between links;
  - external images only after consent;
  - the size limits;
  - a preview whose pane isn't showing pausing its work.
- **Every view has its own preview.** Each view of a file has its own Markdown mode and preview (pane flexibility
  section 6), so each view showing a preview has its own Chromium browser.
- **Safety** *(call)*. A Markdown file is untrusted. Scripts and event handlers in its HTML never run. A content security
  policy allows only Jasper's bundled Mermaid and highlighter, and no network except images the user allowed.
- **Links clicked in the preview** follow section 6.
- **Screen readers.** Off-screen Chromium may expose no page content to Java accessibility, which today's Swing preview
  does. The spike checks JBR's OSR accessibility. If it is missing, the Editor keeps today's preview as a choice in its
  settings (Markdown preview: Chromium or Basic) *(call)*.
- **The Editor's document bar.** The session "Editor markdown preview toolbar logic" was investigating why the Markdown
  toolbar appears only after an Editor setting changes, and had no commits on 2026-10-21. Plans 4 and 7 take the
  Editor's document bar as it is on `main` when they start, including that session's fix if it has landed.

## 9. Start-up and memory (the anti-goals)

- **Plugin start does registration only.** That means the link handler, the split item, actions, the toolbar item, the
  restorer, the settings page and the subcommand. No JCEF class is loaded and no Chromium process starts.
- **Chromium starts on first use.** First use is the first browser item or Chromium preview shown, and Chromium starts
  only after the window is up.
- **A restored browser tab** holds only its URL. Its page loads the first time it is shown.
- **Measured against today's Jasper.** The baseline is Jasper before this work, on the plain `jbr` runtime, because the
  user's anti-goal is "slowing down Jasper's start-up". Two comparisons, both with the existing start-up and memory
  benchmarks:
  - **Plan 2**, the runtime switch, measures before and after on each platform. The larger bundle and the `jcef` module
    must cost nothing measurable.
  - **Plan 4 onward** compares the plugin on and unused with that baseline.

  Either must match within run-to-run noise. The benchmarks open windows, so each batch runs only with the user's OK.
- **Memory goes back when unused.** After the last browser item and Chromium preview closes, Chromium's processes end
  after a short idle delay (the builder picks it), and memory returns near the baseline. CEF may not restart inside the
  same JVM after shutdown. If so, use JetBrains JCEF's out-of-process mode: JBR ships its server as `cef_server.app` on
  macOS. If neither works, bring it back to the user before building further.
- **AOT cache.** Its training run must not load JCEF.

## 10. Saving and restoring

- A browser item saves its URL and zoom in its `RestoreState`. It never saves form contents, credentials or cookies.
- The restorer opens a placeholder item at once, and that item loads the page when first shown, as the Editor defers a
  remote file.

## 11. Settings *(call)*

Settings › Browser, in the plugin's own settings file:

- **Link clicks:** Ask (default), Jasper Browser, or Default Browser.
- **Links programs open:** Default Browser (default), Ask, or Jasper Browser.
- **Search engine.**
- **Saved logins:** on or off. It is unavailable while the Vault is off.
- **Clear Browsing Data:** cookies, history and cache.

## 12. Platforms

- **Everywhere:** every feature and every shortcut in section 5 works on macOS, Windows and Linux, each on x64 and
  aarch64 *(user)*.
- **Platform wording:** Show in Finder, Explorer or Folder, and each system's Downloads folder, as in section 7.
- **Risks to settle in the first spike:**
  - **Linux under Wayland.** JBR has its own Wayland toolkit; OSR needs testing there and under X11.
  - **Windows on ARM.** jcefmaven does not support OSR on win-arm64. JBR ships a win-arm64 JCEF build, but OSR there is
    unconfirmed.
  - **Display scaling** on Windows and Linux, including fractional values (125%, 150%) and moving a window between
    monitors with different scales.
  - **macOS signing.** The CEF framework and helpers must be signed inside `Jasper.app`. Today packaging re-signs ad hoc
    with `--preserve-metadata=entitlements,requirements,flags`.
  - **Chromium's sandbox** on each platform (section 4).

## 13. Delivery

The tab rework is on main (SDK 0.21.0's split items, tab placements and restorers), so the work starts from main.
Proposed plans, in order:

1. **Spike (throwaway).** JCEF OSR in a Swing window on every platform, which the user runs, covering:
   - the section 12 risks;
   - shutdown and restart inside one JVM;
   - cost to start-up and memory before first use;
   - keeping a popup's opener (section 5);
   - OSR accessibility (section 8);
   - whether Chromium's sandbox runs on each platform.
2. **Runtime switch.** The `jbrsdk_jcef` toolchain for development, CI and packaging, plus Chromium's native parts in
   every package and the licence notices.
3. **Link routing.** The SDK's web browser role, jasper-terminal's link-opener API, terminal link clicks through them,
   `jasper <url>`, `jasper open-url`, and the fallback to the system browser.
4. **The browser plugin core.** `plugins/browser` (`:jasper-plugin-browser`, exporting `dev.jasper.browser.api`), with:
   - `BROWSER` in local shells, checked against the real `gh` and `aws` on all three platforms;
   - the item, the document bar, navigation and the shortcuts;
   - the split item, the toolbar item and New Browser Tab;
   - the first-click prompt and the settings;
   - find, zoom and developer tools;
   - restore and the lazy start of section 9.
5. **Downloads, bookmarks, history and the start page.**
6. **Saved logins.** The Vault API additions and their browser use.
7. **The Chromium Markdown preview** in the Editor.
8. **Documentation:**
   - a new `docs/browser.md`;
   - `README.md`'s bundled plugin list;
   - `docs/README.md`, `docs/keyboard-shortcuts.md`, `docs/configuration.md`, `docs/plugin-apis.md` and
     `docs/packaging.md`, which says "nine bundled plugins".

## 14. Testing

- **Headless tests cover the logic:**
  - routing decisions and the remembered choice;
  - settings;
  - the document bar's nodes;
  - shortcut defaults and per-platform clash checks;
  - restore state;
  - bookmark and history stores;
  - login origin matching;
  - Markdown-to-HTML output, with its sanitising and content security policy.
- **The Chromium layer stays thin and concrete.** It has no engine interface with a test fake, per AGENTS.md's
  two-implementations rule. The user checks it by hand on each platform.
- **The SDK link-handler contribution** is covered by the testkit and the contract cases on both runtimes.
- **The start-up and memory criteria** of section 9 are measured with the existing benchmarks, run with the user's OK.

## 15. Left to the builder

- Exact SDK names for the link-handler contribution and the browser API.
- Where bookmarks and history are stored, and in what format.
- The cache cap and the idle delay before Chromium shuts down.
- Whether code highlighting reuses the Editor's highlighter or a bundled script themed with the Editor's colours.
- The start page's content beyond bookmarks.
- Icon artwork, following `docs/sdk-icons.md`.
