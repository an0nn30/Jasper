# Browser verification

Hand checks of the Browser plugin that headless tests cannot make, because they need Chromium on screen. Each section
is a dated record of one batch the user ran.

## Plan 4a, Task 13: the browser core on macOS

### Batch 1: the GUI check (2026-10-30, macOS aarch64)

- **How it was run:** `./gradlew :jasper-app:run` from `claude/browser-core`, on JBR 25.0.4.1 b635.75 with JCEF.
- **First attempt: failed.**
  - New Browser Tab ended Jasper. Chromium's sandboxed GPU and network helpers could not open the Chromium framework
    under the JBR ("file system sandbox blocked open()"), and Chromium stopped on `FATAL` "GPU process isn't usable".
  - A relaunch ended the same way, because the restored browser tab started Chromium again.
- **The fix:** the user chose "Turn the sandbox off now". `ChromiumOptions` sets `no_sandbox = true`
  (`bc6af7635`).
- **Second attempt: passed.** The user reported "it seems to be working" after going through the checklist (pages,
  keys, terminal link clicks and the "Open in Jasper?" prompt, OSC 8 targets, link gestures and popups, the opener
  bridge, page dialogs, closing, restore and local web files). The user did not report the ten lines one by one.

### Pending GUI checks

- After Jasper ends while a page is open (for example force-quit), the next launch shows restored browser tabs waiting
  with Load Page; Load Page loads the page; the launch after that loads them normally.

### Batch 2: memory (not run)

### Batch 3: the AOT cache (not run)

### Windows and Linux (not run)

The user has no Windows or Linux host yet.

## Plan 4b: page features

### Batch 1: the GUI check on macOS (pending)

- **How to run it:** with the user's OK, `./gradlew :jasper-app:run` from `claude/browser-page-features` (worktree
  `.claude/worktrees/browser-4b-4c-e6deab`), on JBR 25.0.4.1 b635.75 with JCEF.
- Each line is recorded pass or fail. Every line is pending.

#### The plan's checklist

1. **Favicons:** they show in the tab and the field. Dragging the field's favicon into a terminal pastes the URL.
   Pending.
2. **History:** holding Back or Forward lists the history, and choosing an entry goes there. Pending.
3. **Suggestions:** pending.
   - typing shows suggestions, with "Loading suggestions…" first;
   - Tab takes one, and Escape restores;
   - on a results page, the field shows the query;
   - the engine setting changes all of this.
4. **Find:** it highlights, counts, works with a regular expression and Match case, reaches frames, and clears on
   close. Pending.
5. **Zoom:** Cmd+= and Cmd+- step it, Cmd+0 resets it, and it survives a relaunch. Pending.
6. **The right-click menu:** it has the right items for a link, an image, a selection and a field. Search with Google
   opens a new tab, and the host's Split items work. Pending.
7. **An error page:** an unknown host shows "Server Not Found", and Try Again reloads. Pending.
8. **A certificate error:** `https://self-signed.badssl.com` shows the error page. Details, then Continue, loads it,
   with "Not secure" shown; after a relaunch it asks again. Pending.
9. **View Source** shows the source with colours and links. Pending.
10. **Inspect Element** opens DevTools below the page. Pending.

#### Lines the task reviews added

Each is pending.

- **Favicons and the site icon:**
  - the globe while a page loads, then its favicon, on a site with `<link rel="icon">`, one with only `/favicon.ico`,
    and one whose icon redirects (http to https, a CDN);
  - `jasper-about:welcome` shows Jasper's icon;
  - a page that changes its favicon later keeps its first;
  - Back to a back-forward-cached page, and a failed load, each show their own icon or the globe;
  - the favicon is sharp at 1x and 2x in the tab and the field;
  - dragging the site icon out to the file manager (Finder, Explorer, Nautilus) and to another browser drops the
    address.
- **History hold menus:**
  - after choosing an entry, Back, Forward and both menus update;
  - a right-click, Control-click, Shift+F10 and the context-menu key on Back and Forward open the menu at once;
  - a press held, dragged onto an entry and released there chooses it;
  - a page that changes its title rapidly keeps the menus current without lag;
  - a popup opened by `window.open` loses its opener link when one of its entries is chosen;
  - Back's menu around an error page (P40).
- **Address suggestions and search engines:**
  - the list's placement, width, flip above the field when there is no room below, and no flicker as the engine's
    rows arrive;
  - typing goes on in the field while the list shows; Tab takes a suggestion and the focus stays put; clicking a row
    loads it;
  - the list hides on a click elsewhere, and after the window is deactivated and reactivated; a click in the field
    with the list open closes it until the next keystroke;
  - Enter right after typing loads the top row: `localhost:3000` navigates, `jcef` searches;
  - real engines' suggestions without cookies and with Java's User-Agent: Google, DuckDuckGo, Bing and Kagi;
  - `about:version` and `about:settings` in the field do not open `chrome://` pages.
- **Find:**
  - the bar's row lays out under the page when it first shows, and its field takes the focus with its text selected;
  - a 1,000-character query; an invalid regular expression shows "Invalid pattern";
  - cross-origin frames are searched: the counts add up, and stepping crosses frames;
  - a closed `<details>`, a `hidden="until-found"` element and an `aria-expanded` accordion open on a match;
  - phrases across links and line breaks are found;
  - a page that replaces its DOM while the bar is open: the count follows; a churning page, such as a ticker, keeps the
    bar responsive;
  - closing clears every highlight, also after Back to a back-forward-cached page;
  - seeding from a one-line selection; Cmd+F, Cmd+G and Cmd+Shift+G on macOS, Ctrl+F, F3 and Shift+F3 on Linux and
    Windows.
- **Zoom:**
  - the zoom applies before the first paint, and after a navigation to another host;
  - split panes on one host share a zoom (a known gap, P34);
  - Ctrl+wheel zooms on Windows and Linux, and not on macOS; a Windows precision touchpad's pinch zooms.
- **The right-click menu:**
  - it opens at the pointer on the page, a link, an image, a selection and a text field: on HiDPI, at 200 % zoom, on a
    scrolled page, inside an iframe and on a second monitor;
  - the context-menu key and Shift+F10 open it at the focused element (Windows and Linux);
  - Control-click on macOS opens it and does not follow a link; a right-click on Windows;
  - Cut, Copy, Paste and Select All from it act in a text field and on a selection, also inside an iframe, and the page
    keeps the focus afterwards;
  - Print… prints on macOS, Windows and Linux;
  - Search with and Open Link and Open Image in New Tab open the next tab; on an `http` page, a `jasper-about:` or
    `file:` link's Open Link in New Tab is disabled;
  - after one menu is dismissed, a second right-click opens a new one; closing the tab while the menu shows closes the
    menu;
  - submenu placement and its hover delay, and the native menu beside the kit's menus.
- **Error pages:**
  - from a page, an unknown host, then Back returns to the page; Back is disabled in a new tab opened straight at an
    unknown host;
  - Enter and Space press Try Again as soon as the page shows, also after Enter in the address field;
  - Try Again after a success leaves the error page as a Forward entry (P40);
  - a declined certificate reports `ERR_CERT_*`, not `ERR_ABORTED`; a `jasper-about:` action answered 204 ends as
    `ERR_ABORTED`; a failed load commits no address;
  - a plain click on a `mailto:` link.
- **Certificate errors:**
  - the self-signed page shows the reason "self-signed" and the chain; another host shows no "Not secure";
  - "Not secure" in the toolbar: its placement, and when it appears and goes;
  - an HSTS-preloaded badssl host (P41);
  - a sub-resource's certificate error does not ask.
- **View Source:**
  - on an `http` page, a slow server (up to 10 seconds) and a `file:` page. Jasper answers the source page later, with
    CEF's `open` and `handleRequest=false`; if JBR's JCEF does not keep that callback, every `http` and `https` View
    Source fails;
  - navigating away while the source page loads.
- **Inspect Element:**
  - DevTools open below the page on the element under the pointer, on HiDPI and at a page zoom other than 100 %; the
    tab reads "DevTools — *title*";
  - Inspect Element three times quickly, then a new tab and a popup: both load normally, and DevTools end on the last
    element (P44);
  - closing the DevTools pane and choosing Inspect Element again brings them back without a blank pane;
  - closing the page closes its DevTools pane; closing the DevTools pane leaves the page;
  - typing in the console, and Cmd+A, C, V, X and Z (Ctrl on Windows and Linux) in DevTools;
  - Cmd+R (Ctrl+R) reloads the page; Cmd+F (Ctrl+F) and Cmd+= and Cmd+- reach DevTools;
  - a right-click in DevTools shows no menu (a known gap);
  - DevTools do not come back after a relaunch, and keep working when their pane moves to a new tab or window;
  - a console link opens a browser tab next to the page's for `http` and `https`, and nothing for `file:` or
    `jasper-about:` from an `http` page;
  - Jasper quits with DevTools open.
- **A proxied network** (the user's corporate network, P22): favicons, engine suggestions and View Source's fetch.

#### Lines the final fix wave added

Each is pending.

- **Suggestions:** the pointer over a row highlights it and leaves the selected row; Enter then loads the selected one,
  and a click loads the row clicked (P16).
- **Hold menus:** a press on Back that leaves the button and comes back within half a second opens no menu.
- **Favicons:** a site that calls `history.replaceState` or `pushState` while it loads, such as a single-page app that
  rewrites its address, keeps its favicon.
- **History menus:** a page that animates its title, such as a mail inbox's unread count, keeps the menus current.
- **Find:** typing a word quickly searches once it pauses, without the count flickering; Enter right after typing finds
  what was typed.
- **Links (P48):**
  - a Cmd-click (Ctrl-click) on a `file:` link of a local page opens it in the next tab, and one on an `http` page's
    `file:` or `jasper-about:` link opens nothing;
  - a file dropped on an `https` page that does not take drops: whether it still opens (it may now open nothing);
  - a DevTools console link: whether it opens in the next tab, or loads in the inspected page.
- **Jasper's pages (P47):** a local `file:` page with `<img src="jasper-about:settings">`,
  `<iframe src="jasper-about:welcome">` or many `<img src="jasper-about:source?url=…">` opens no Settings, shows no
  welcome page in the frame and builds no source pages; the welcome page's Settings link, an error page's Try Again
  and Continue, and View Source still work.
- **View Source of an error page:** after a relaunch, the source page comes back for the address that failed.
- **DevTools zoom:** on Windows and Linux, Ctrl+wheel over the DevTools pane zooms DevTools, not the page.

### Plan 4b on Windows and Linux (not run)

The user has no Windows or Linux host yet. The plan's ten lines and every line above wait for each of them too.

## Plan 4c: registering Jasper as the default browser

### Batch 1: the user's check on macOS (pending)

- **How to run it:** with the user's OK, from `claude/browser-default-browser` (worktree
  `.claude/worktrees/browser-4b-4c-e6deab`), on JBR 25.0.4.1 b635.75 with JCEF: `./gradlew :jasper-app:packageApp`
  (headless; no `-Pjasper.aot`), then install the built `jasper-app/build/packaging/image/Jasper.app`. The check changes
  the user's default browser, so the user makes the change and changes it back.
- Each line is recorded pass or fail. Every line is pending.

#### The plan's checklist

1. **Choosing Jasper:** System Settings › Desktop & Dock › Default web browser lists Jasper, and Jasper can be chosen
   there. Pending.
2. **A link from another app** (Mail, Notes, Slack) opens in a new browser tab of Jasper's most recently active window.
   Pending.
3. **`open -a Jasper https://example.com`** opens a tab. Pending.
4. **A terminal Cmd-click** on an `https` link opens without asking, with Open Links From Terminal at Always Ask.
   Pending.
5. **The Browser off:** with the Browser plugin turned off and Jasper relaunched, a link from another app shows "Web
   links can’t open in Jasper"; Turn On Browser and then Restart Now bring the Browser back, and the next link opens
   in it. Pending.
6. **Choosing the previous default browser again:** after coming back to a Jasper window (more than 30 seconds after
   the last check), a terminal Cmd-click asks "Open in Jasper?" again. Pending.

#### Lines the task reviews added

Each is pending.

- **Listing (Task 4):** whether macOS lists Jasper as a web browser with only `CFBundleURLTypes`, no document types
  for HTML.
- **A cold launch by a link (Task 4):** with Jasper quit, a link from another app starts Jasper and opens once, in the
  first or restored window, with no extra window: the event that starts the launch reaches the open-URI handler.
- **No window open (Q12):** with a resident Jasper and no window, a link from another app restores a window and opens
  there; the log has a `Window (open-uri)` line.
- **An address a URI parser refuses (Task 4):** a link with a raw space (for example from `open -a Jasper` with a
  quoted `https://example.com/a b`): whether AWT hands it to Jasper at all.
- **While Jasper starts (Q6):** a link from another app while Jasper is still starting waits and opens once the
  Browser has started.
- **The split items (Q7):** with Open Links From Terminal at Default Browser while Jasper is the default, a terminal's
  right-click menu on a selected address has Open URL in Split Right and Down, which open a browser pane, and a
  Cmd+Shift-click opens a split; after choosing another default browser and coming back to a Jasper window, the items
  go.
- **Copy Link** in "Web links can’t open in Jasper" puts the address on the clipboard.
- **`jasper URL...` with the Browser off (Task 3):** `jasper https://example.com` from a Jasper terminal posts the
  notice and the command exits 0; with no Jasper running, whether the notice of the `--no-window` Jasper it starts is
  seen at all.
- **`ftp` and `mailto`** links in a terminal still open the system's handler while Jasper is the default.
- **No loop (Q2, Q10):** with the Browser off, each link from another app posts one notice, and nothing opens over and
  over: no other browser, no new Jasper window, no Jasper process starting again in Activity Monitor.

### Plan 4c on Windows (not run)

The user has no Windows host yet. Each line is not run.

- `packageInstaller` builds the setup wizard; `Verify-Installer.ps1` accepts the MSI's web browser registration;
  `InstallerAcceptanceTest` passes; light's ICE validation passes (ICE33 may warn about the `HKCU\Software\Classes`
  keys).
- After the setup, Settings › Apps › Default apps lists Jasper; choosing it for `HTTP` and `HTTPS` makes it the
  default, and Jasper counts itself the default (the `UserChoiceLatest` layout, Q5): a terminal Ctrl-click opens
  without asking.
- A link from another app opens in Jasper through `Jasper.exe --url "%1"`, with Jasper running (a handoff) and quit (a
  first launch).
- A link with a double quote in it: it opens without the quote, or nothing opens when the quote splits it.
- An upgrade keeps the registration; the uninstall removes `RegisteredApplications\Jasper`,
  `Software\Jasper\Capabilities` and `Software\Classes\JasperURL`; what Windows does with its own choice of Jasper
  afterwards.
- The portable ZIP and `installWindowsUser` are not offered as browsers.
- The plan's lines 2 to 6, on Windows.

### Plan 4c on Linux (not run)

The user has no Linux host yet; CI's `linux-packages` job has not run (the Actions budget). Each line is not run.

- `packageDeb`, `packageRpm` and `check-install.sh`, with real xdg-utils and dash.
- After a package install, GNOME's default applications settings list Jasper as a web browser; KDE's (Q15: whether it
  needs `WebBrowser` in Categories).
- `xdg-settings set default-web-browser jasper-workstation-Jasper.desktop` makes Jasper the default, Jasper counts
  itself the default, and a link from another app opens in Jasper.
- The install leaves every system `defaults.list` byte for byte as it was.
- Removing the package leaves no `mimeinfo.cache` line naming Jasper.
- Without `xdg-settings`, with Jasper the default through `~/.config/mimeapps.list` (Q10): a terminal click that Jasper
  hands to the system comes back and opens in Jasper's browser, once.
- The plan's lines 2 to 6, on Linux.
