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
6. **Choosing the previous default browser again:** after coming back to a Jasper window (more than 5 seconds after
   the last check, ruling Q19), a terminal Cmd-click asks "Open in Jasper?" again. Pending.

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
  notice while Jasper is the default, and the command exits 0; with no Jasper running, whether the notice of the
  `--no-window` Jasper it starts is seen at all.
- **`ftp` and `mailto`** links in a terminal still open the system's handler while Jasper is the default.
- **No loop (Q2, Q10):** with the Browser off, each link from another app posts one notice, and nothing opens over and
  over: no other browser, no new Jasper window, no Jasper process starting again in Activity Monitor.
- **Restart Now opens the link (final review):** with the Browser off, Turn On Browser and then Restart Now in the
  notice of a link from another app: the restarted Jasper opens that link in the Browser.
- **Hosts the browser takes or cannot (Q17):** while Jasper is the default, a link from another app (for example
  `open https://bücher.de/`) with an internationalized host opens in Jasper's browser; one with `_` in its host
  (`open http://my_service:8080/`), and one with `ß` (`open https://faß.de/`, Q22), show "Jasper’s browser can’t open
  this address" with the address and Copy Link.

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
- A file opened with Jasper from the file manager (Open With), and one dropped on Jasper's launcher, open as files
  (`--url` with a `file:` URL, Q18).
- The plan's lines 2 to 6, on Linux.

## Plan 9: the Chromium Markdown preview

### Batch 1: the user's check on macOS (pending)

- **How to run it:** with the user's OK, `./gradlew :jasper-app:run` from `claude/browser-markdown-preview` (worktree
  `.claude/worktrees/browser-4b-4c-e6deab`), on JBR 25.0.4.1 b635.75 with JCEF, with **Markdown preview** at Chromium,
  the default. Most lines use `plugins/editor/src/test/resources/markdown/github-features.md`, which holds every
  construct the preview draws.
- Mermaid 11.17.2 is bundled by slice 1b, on `claude/browser-mermaid` (worktree `.claude/worktrees/browser-mermaid`)
  until it merges: the Mermaid lines (2, 5's diagrams, 9's Mermaid node, and the slice 1b lines) run from there.
- Each line is recorded pass or fail. Every line is pending.

#### First: what only the GUI can show (ruling N-F38)

1. **A document shows, edits update it, and Find counts** in the Chromium preview. This is where the risks no headless
   test reaches would show first: `jasper-preview:` registered as not local still fetches `/document/<revision>`
   (N-F3); the content security policy, as a header and as the shell's first `<meta>` (N-F4), lets `preview.js` and
   `preview.css` load and run; `executeJavaScript` runs under that policy (the theme, each document, Find); and the
   scheme answers a preview's browser on its own origin (`Chromium.answersPreview`, whose true side no headless test
   can make). **If this line fails, `markdown_preview`'s default goes to `basic` before anything is pushed.** Pending.

#### The plan's checklist

1. **The look:** the fixture in Preview shows GitHub's layout in the theme's colours. Switching light and dark changes
   it in place and keeps the scroll. Cmd+= and Cmd+- change its size. Pending.
2. **Mermaid:** flowchart, sequence, class, state, ER, gantt, pie, gitGraph and mindmap draw; a broken diagram shows
   its error and its source; if a type needs `'unsafe-eval'`, stop and ask, and do not add it. In Basic, each `mermaid`
   fence shows as a code block. Pending.
3. **Code:** Java, Python and YAML blocks take the colours the source pane gives them. Pending.
4. **GitHub's features:** pending.
   - the five alert kinds;
   - a footnote reference goes to its note, and ↩ comes back;
   - autolinks, task lists and aligned tables;
   - wide code scrolls sideways.
5. **Scroll sync:** both ways in Split, including after images and diagrams load. Turning it off works. Pending.
6. **Find:** pending.
   - Match Case, Whole Word and Regular Expression work, and the count follows edits;
   - Enter and Shift+Enter move between matches;
   - Escape gives the focus back.
7. **Links:** pending.
   - a web link follows the routing: the "Open in Jasper?" prompt, or the setting's choice;
   - `#heading` and a relative file work;
   - Tab and Shift+Tab leave the preview at its ends, and Enter follows the focused link.
8. **Images:** a relative image shows. A web image shows only after **Load external images**. A failure shows its
   reason, and **Retry images** works. Pending.
9. **A hostile file** (the plan's Review Focus 1, with a raw `<img src="http://127.0.0.1:8000/x">` and a Mermaid image
   node pointing at that address), watched by `python3 -m http.server 8000`: pending.
   - nothing runs or navigates;
   - neither address is ever requested, before **Load external images** or after;
   - a Markdown image `![](http://127.0.0.1:8000/y)` is requested only after **Load external images**, once, by
     Jasper's own loader (Java's user agent in the log).
10. **Basic:** pending.
    - **Markdown preview › Basic** and back switches live;
    - with the Browser plugin off, after a relaunch, the preview is Basic;
    - VoiceOver reads Basic, and reads the Chromium canvas's name and description.
11. **Hidden previews:** a preview in a tab you leave stays idle. Closing the tab ends its page. Note the memory with
    five previews open, for the later benchmark; it is not a benchmark. Pending.
12. **Copy and Select All** in the preview, including Copy of a Find match. Pending.

#### Lines the task reviews added

Each is pending.

- **The first frame (Task 5):** what shows while a preview's page loads, before it paints its themed background: no
  flash of another colour, and no animation.
- **Find's colours (Task 5):** the current match keeps `find.js`'s orange beside the selection colour the other
  matches take; whether that reads well in light and dark.
- **Find's steps (Task 5):** Enter and Shift+Enter step through matches that span inline markup (bold, links, code),
  in order, wrapping at the ends.
- **Focus (Tasks 5 and 6):** Tab past the last link moves the focus to the next control, and Shift+Tab before the first
  to the previous one, once each (JCEF moves it, and Jasper only when the canvas still holds it); after a `#heading`
  link, Tab goes on from the heading; a click in the preview sends Copy, Select All and Find to it.
- **Scroll sync timing (Task 7):** the page's echo of Jasper's own scroll never moves the source (its 1 px rule);
  after an edit the passage stays aligned, the page saying shown before it reports the layout; typing in Split with
  Synchronize scrolling on stays smooth, though each rebuild sends one scroll to the page.
- **Start-up (ruling N9):** a restored window whose Markdown tabs show only their source starts no Chromium: no
  Chromium helper process appears until a Split, a Preview or a browser page shows.
- **Browser pages (Task 5):** every browser page's canvas now has the accessible role of a canvas; what VoiceOver says
  on a browser page.

#### Lines the final review's fix wave added

Each is pending.

- **Typing near the end (W1, ruling N15):** type near the end of a Markdown document with images in Split with
  Synchronize scrolling on; the source does not move, and the images return promptly after each edit (a brief gap is accepted, N22).
- **The first preview (W3, ruling N16):** the first Markdown preview after a fresh install, or after a restart of the
  Mac, shows the document, not Basic.
- **Fonts (W4, ruling N17):** the Chromium preview's text is the system font and its code a monospace font with default
  settings; digits, `#` and `*` in a code block are drawn in the code font, not as emoji.
- **Switching tabs near the end (R1, ruling N23):** with Synchronize scrolling on in Split, switch to another tab and
  back while the preview is near the end of a document with images; the source does not move.

#### Lines slice 1b added

Each is pending.

- **A loosening diagram (Review Focus 1):** a flowchart whose first line is
  `%%{init: {"securityLevel": "loose"}}%%`, with `click A call alert(1)` and `click B "javascript:alert(2)"`, draws,
  and clicking A and B runs no script and goes nowhere: no alert, and the preview stays on its document.
- **A diagram's link to the network (Review Focus 1):** with `python3 -m http.server 8000` running, a flowchart with
  `click C href "http://127.0.0.1:8000/z"` draws, and neither its drawing nor a click on C makes any request to that
  server.
- **Mermaid's first load:** the first diagram of a run draws (Mermaid's file is read off Chromium's IO thread, and its
  request goes on once read), and browser pages stay responsive meanwhile.

### Plan 9 on Windows and Linux (not run)

The user has no Windows or Linux host yet. Every line above waits for each of them too, with Ctrl in place of Cmd, and
Narrator or NVDA, and Orca, in place of VoiceOver.

## Plan 5: history, bookmarks and Jasper integrations

### Batch 1: the user's check on macOS (pending)

- **How to run it:** with the user's OK, `./gradlew :jasper-app:run` from `claude/browser-history-bookmarks` (worktree
  `.claude/worktrees/browser-4b-4c-e6deab`), on JBR 25.0.4.1 b635.75 with JCEF. The onboarding lines need the
  Browser's data folder (`plugins/dev.jasper.browser/data/` in Jasper home) without `onboarding-finished`. Where a line
  reads how a visit was reached, `sqlite3 plugins/dev.jasper.browser/data/browser.sqlite "SELECT url, reached FROM
  history ORDER BY id DESC LIMIT 5"` shows it; history and bookmarks show on their pages.
- Each line is recorded pass or fail. Every line is pending.

#### First: what only the GUI can show

1. **Transition flags (H-F1):** Back, then a link, records `back_forward` and then `link`: JCEF ORs a load's flags
   into the shared transition constants, and Jasper clears them at each load start. If the second reads
   `back_forward`, every later visit's `reached` is wrong for the run. Pending.
2. **Jasper's world in the page (H30, H33):** Insert puts a snippet into a page's text input, and Copy on select copies
   a drag in a page. Both need CEF to deliver `Runtime.executionContextCreated` through JBR's out-of-process bridge
   within 5 seconds of `Runtime.enable`; if it does not, nothing is inserted and nothing is copied. Pending.
3. **A failed navigation (H-F19):** `https://nonexistent.invalid/` shows "Server Not Found" and leaves no history
   entry, whether or not CEF reports a load start for it; Stop on a slow page keeps that page's entry; a link clicked
   on a page that is still loading keeps that page's entry. Pending.

#### The plan's checklist

1. **Recording:** three sites visited show newest first on Cmd+Y's "Browsing History", and Jasper's pages don't.
   Search, ×, Load More and Clear All History work. Pending.
2. **Bookmarking:** pending.
   - ☰ › Add Bookmark opens Edit Bookmark, tags are added with Enter and a comma, and Save keeps them;
   - Remove Bookmark shows "Bookmark Removed";
   - Delete Bookmark asks, then shows "Bookmark Deleted";
   - the panel closes when the page navigates.
3. **The bookmarks page:** search, the sorts, the tag chips, ×, Load More and Clear All. Pending.
4. **Suggestions:** typing shows "Bookmark" and "Visited N times" rows. Pending.
5. **The palette:** pending.
   - Bookmarks and Browser History rows show in All, and "Open URL in Browser" for an `https://` URL;
   - a row opens in the current browser item, or in a new tab from a terminal.
6. **Welcome and onboarding:** with a fresh `data/`, the start page goes through the three onboarding pages to
   "Welcome Back". Top Sites, Refresh and the Quick Actions work. Pending.
7. **Snippets (H30):** pending.
   - Insert reaches a text input, a text area, an editable element (a GitHub comment box) and a field in a
     cross-origin frame, which gets it through DevTools' `Input.insertText`;
   - Paste and run submits a search form, in the main frame and in a cross-origin frame;
   - the clipboard is untouched after each, and a Vault password copied first is still cleared after 30 seconds.
8. **Copy on select:** pending.
   - on, a drag, a double-click, a triple click, a Shift+click (H32), a Shift+arrow selection, Cmd+A and the page's
     right-click Select All (H32) copy, and a password field's selection doesn't;
   - a selection inside a cross-origin frame copies nothing, and Cmd+C copies it (H-F2);
   - after Copy in a terminal, a drag of the page's scrollbar does not copy the page's old selection over it (H35);
   - off, nothing copies.
9. **Keeping:** a relaunch keeps history and bookmarks. Pending.
10. **Windows and Linux:** Ctrl+H opens History, from the address field too. Not run until the user has those hosts.

#### Lines the task reviews added

Each is pending.

- **Redirects (Task 2):** a typed `http://` address that the server redirects to `https://` records one entry, at the
  `https://` address, as `typed` (expected) or `redirect`.
- **Jasper's pages (Task 3):** three quick Deletes on the history page all ask, as CEF's dialog origin for a
  `jasper-about:` page skips the dialog flood; Find, the site icon and the opener bridge work on a `jasper-about:`
  page, and its DevTools console shows no content security policy error from Jasper's injected scripts under the
  pages' nonce policy; the welcome page's redirect to the onboarding (a 302) is followed; the pages' channel answers
  only the pages' client; a page that navigates or closes with a request waiting cancels it (CEF's `onQueryCanceled`).
- **History (Task 4):** Show Full History at the end of a held Back's or Forward's list; a `file:` row loads on a
  click and a Cmd-click on it opens nothing (H3); the time column aligns ("09:41 AM" in en-US); the search field has
  the keyboard after History.
- **The token field (Task 6b):** the chips and their remove button's hover in both looks; the popover grows when the
  chips wrap onto a second line; Save clicked with text still typed adds it; the selected chip's focus ring; VoiceOver
  announces the selected chip (on Windows, the Java Access Bridge); the caret hides while a chip is selected.
- **The Edit Bookmark panel (Task 7):** Escape and a click outside close it; under ☰ at a window's right edge it
  right-aligns to the button (H21); Tags has the keyboard when it opens; the Vault's credential picker near a window's
  right edge, or in a narrow dialog, right-aligns to its picker, as H21 applies to every popover; text typed in Tags
  but not yet a tag is lost on a click outside (H25); after Delete Bookmark's question the keyboard goes back to the
  page.
- **The onboarding (Task 9):** a fresh data folder shows the intro, then Get Started, the features page, the setup page
  and Start Browsing, then Welcome Back; the next launch shows Welcome Back; the Top Sites tiles and Refresh; the Quick
  Actions open Bookmarks, History and Settings.
- **Snippets (Task 11):** a page script that overrides `document.execCommand` or `HTMLInputElement.prototype.value`,
  pasted into the console first, neither changes nor sees the inserted text, except through input events; Insert with
  the palette open over a page whose input has the focus lands in that input; an insertion while the page navigates
  inserts nothing, anywhere.
- **Copy on select (Task 12):** a password field in a closed shadow root never copies; on macOS, Cmd+A copies whether
  A or Cmd is let go first; Chromium's own Copy beside Copy on select on `user-select: none` text and on `&nbsp;`, and
  a command copied with `&nbsp;` pastes into a shell with plain spaces; a scrollbar drag, a map pan or a slider drag
  does not copy the old selection again; Shift+click extends the selection and copies it; the page menu's Select All
  copies the page; with the page's DevTools open, the "Jasper" world appears once per document; a selection made just
  after a navigation starts may copy nothing once, and the next gesture copies; a Markdown preview's selection copies
  nothing (H-F34).

#### Lines the final review's fix wave added

Each is pending.

- **Paste and run (H38):** Paste and run with the focus on a page button or a read-only field does nothing: no
  snippet, and no Return, so the button is not pressed and the read-only field's form is not submitted. With nothing
  focused, on a link or on a checkbox it does nothing either; in a number field it types the snippet's digits; a page
  that navigates while the snippet goes in gets nothing more.
- **Copy on select on a slow page (H36):** a selection finished while the page's script is busy (a loop of a few
  seconds started from its DevTools console), then a second one, copies the second once the page is free.
- **The data folder (H37, H41):** once a page has loaded, `ls -ld` shows `drwx------` for
  `plugins/dev.jasper.browser/data` and `ls -l` shows `-rw-------` for `browser.sqlite` and its `-wal` and `-shm`, in a
  fresh data folder and when they had wider permissions before the launch.
- **Quitting with Edit Bookmark open:** tags typed in the open panel are kept after Cmd+Q, and after turning the
  Browser off in Settings › Plugins.
- **The bookmarks page:** with a tag chosen, removing it from its last bookmark in Edit Bookmark, then a new search or
  sort on the page, shows every bookmark and no chip pressed.

### Plan 5 on Windows and Linux (not run)

The user has no Windows or Linux host yet. Every line above waits for each of them too, with Ctrl in place of Cmd, and
Narrator or NVDA, and Orca, in place of VoiceOver. On both, Ctrl+H deletes nothing in Settings' text fields or in Edit
Bookmark's Tags (H17), and in a terminal pane's find bar it deletes the character before the caret. On Linux, the data
folder line holds too; on Windows the folder keeps its permissions.

## Plan 6: privacy

### Batch 1: the user's check on macOS (pending)

- **How to run it:** with the user's OK for each batch, `./gradlew :jasper-app:run` from `claude/browser-privacy`
  (worktree `.claude/worktrees/browser-4b-4c-e6deab`), on JBR 25.0.4.1 b635.75 with JCEF. The camera and microphone
  lines need the packaged `Jasper.app` (`./gradlew :jasper-app:packageApp`, without `-Pjasper.aot=true`), whose
  `Info.plist` has the usage descriptions ([macOS camera and microphone](packaging.md#macos-camera-and-microphone)).
  The notification lines need a page served by `python3 -m http.server`, and the proxy lines a local proxy on port 8118
  (`privoxy`, or `mitmproxy -p 8118`). Where a line reads Jasper's data, it is in `plugins/dev.jasper.browser/data/` in
  Jasper home.
- Each line is recorded pass or fail. Every line is pending.

#### First: what only the GUI can show

1. **Jasper's pages in a /dev/null tab (V5, V-F1):** with Use /dev/null mode on, a new tab's welcome page, and in it
   `jasper-about:history`, `jasper-about:bookmarks`, `jasper-about:permissions`, View Source's page and an error page,
   load and answer their queries. Chromium's scheme handler serves only its global context, so Jasper answers an
   in-memory context's main-frame `jasper-about:` requests itself; if that fails, no /dev/null tab can show Jasper's
   pages. Pending.

#### The plan's checklist

1. **/dev/null:** pending.
   - with the setting on, a new tab shows the indicator and its popover;
   - a site signed into there is signed out in a new /dev/null tab and in a persistent tab;
   - a popup shares its opener's sign-in;
   - Web › History shows only that group's visits, and ☰ has no bookmark items;
   - after a relaunch, the tab comes back at the start page with the indicator.

   From the task reviews:
   - **Task 1b:** after a /dev/null session, `~/Library/Saved Application State/` holds no /dev/null page's title (the
     macOS window title may reach it).
   - **Task 2:** popup B from /dev/null tab A; close A; a link from B opened in a new tab C stays in the group: signed in
     to the group's sites, signed out of the persistent ones, and `data/chromium` unchanged. The same after Inspect
     Element and closing DevTools. Open a /dev/null tab and close it at once; open one and quit at once (no crash);
     open DevTools in a /dev/null tab, then close both. Open any tab and close it at once: no page keeps loading, the
     life span trace shows it closed, and quitting shows no 10 s stop warning.
   - **Task 3:** the popover's copy; Escape, a click outside and a second click on the indicator each close the popover
     without opening it again; a slow click on an open anchored popover's anchor (the /dev/null indicator, the Edit
     Bookmark panel, the Vault's credential picker) closes it and does not reopen it, and after Escape a quick click
     opens it (V14, Swing's dispatch order); a restored /dev/null tab comes back empty at the start page, with
     `workspace.toml`'s title "Web Browser", `devnull = "true"` and no `url`; turning the setting off changes no open
     tab, and the next new tab is persistent.
2. **Camera and microphone** (the packaged app): pending.
   - [the WebRTC sample](https://webrtc.github.io/samples/src/content/getusermedia/gum/) shows
     "Allow Camera";
   - Block is remembered;
   - ☰ shows "Reset Camera Permission (Blocked)";
   - the permissions page lists the grant, and × reloads the tab;
   - screen sharing is refused without a prompt;
   - `navigator.geolocation` fails without a prompt.

   From the task reviews:
   - **Task 4:** the permissions page lists the grants by site; a site's Clear, a permission's × (after "Revoke X
     permission for ORIGIN?") and Clear All Permissions remove them, and the group's tabs that show the site reload.
     ☰'s Reset items show only for a site with a permission, between History (at the top in a /dev/null tab) and
     Settings, with one separator each side, and choosing one removes it and reloads the site's tabs. A /dev/null tab's
     permissions page shows only its group's, and its changes do not reach the persistent tabs (V-F1). The Quick
     Actions read Bookmarks, History, Settings and Permissions, and Permissions opens the page; the onboarding's
     features page lists "Site permissions".
   - **Task 5:**
     - under `:jasper-app:run`, with the user's OK, what TCC does when a page opens the microphone, then the camera: a
       prompt, a silent denial or the process ending, as JBR's `java` has no usage descriptions (V-F44);
     - on the packaged `Jasper.app`, macOS's first prompts show the two descriptions, and an allowed `getUserMedia`
       captures: JBR's `jcef Helper.app` is hardened without camera and audio entitlements, so a `NotReadableError`
       means a packaging follow-up;
     - `getUserMedia({audio: true})` shows "Allow Microphone" and "The website ORIGIN wants to use your microphone.":
       Allow starts the microphone and ☰ shows "Reset Microphone Permission (Allowed)"; Block refuses
       (`NotAllowedError`) and ☰ shows "Reset Microphone Permission (Blocked)"; Escape refuses and keeps nothing;
       `{video: true, audio: true}` shows "Allow Camera and Microphone";
     - Enter allows nothing, nor does Space with nothing focused; a quick double-click lure and an alert stacked over
       the prompt cannot allow the camera, and a deliberate click after a moment can (V19);
     - a dismissed prompt does not come back on a repeated `getUserMedia` until a reload, after which it asks again; a
       third quick modal is refused (an alert then two prompts, or three prompts each closed at once); a prompt in a
       tab that is not shown waits until its tab is; leaving or closing the page while a prompt shows takes it away,
       and the page's promise rejects; two `getUserMedia` calls at once show one prompt;
     - ☰'s Reset revokes a live camera: the page reloads and the camera's light goes off; a cross-origin frame with
       `allow="camera"` is asked in the top-level site's name, and the grant is that site's (V16), while without
       `allow=` Chromium's permissions policy refuses the frame before Jasper is asked;
     - `getDisplayMedia` and a `getUserMedia` from DevTools' console are refused without a prompt; a /dev/null tab's
       Allow is gone once its group closes, and never on the persistent permissions page;
     - quitting while a prompt shows: no crash, no 10 s stop warning, and the page is not answered once Chromium has
       stopped.
3. **Notifications:** a `python3 -m http.server` page calls `Notification.requestPermission()`, then
   `new Notification("Hi", {body: "There"})`. That gives a card, and `close()` takes it out of history. Pending.

   From the task reviews (**Task 6**):
   - the card reads "localhost — There" under the title "Hi" (V2), and its click shows its tab;
   - Allow, Block and Escape on `requestPermission()`; the 11th notification in a minute is dropped; closing one of two
     identical notifications keeps the other clickable;
   - a challenge frame gets no stand-in; a service worker's notification does not appear; a /dev/null tab is denied;
     an `http` site other than this computer is denied, while `localhost` asks; a sandboxed document shows nothing; a
     Cloudflare-managed site passes its interstitial;
   - a fenced frame does not count as the main frame (`isMain()`), and the stand-in is in place when the page's own
     scripts run (its injection at `OnLoadStart`).
4. **Audio:** pending.
   - a page with `<audio autoplay>`, and a YouTube embed, are muted with the bar;
   - each of the four answers works;
   - Unmute Current Page works.

   From the task reviews (**Task 7**):
   - the bar shows at the first sound; an embedded player is muted with its page; an early `<audio autoplay>` is
     muted; a /dev/null tab's choice stays out of the persistent store;
   - in the JBR console, `webkitAudioContext === AudioContext`, and a page that uses `new webkitAudioContext()` is
     muted under a kept Block and gets the bar otherwise;
   - a web component's `<audio>` autoplaying in a shadow root it attaches at run time is muted, with the bar;
   - with Deny Always kept for a site, a page whose embedded player starts at once is muted from its first sound, even
     after answering another tab's bar or a quick reload.
5. **Clearing:** pending.
   - with pages open, Clear Cookies signs out;
   - Clear All Data also clears a test page's `localStorage`, the history, the bookmarks and the permissions;
   - after a relaunch, nothing comes back.

   From the task reviews (**Task 8**):
   - Clear Cookies and Clear All Data show their sheet over the active window, in front of Settings; Cancel, Escape and
     closing the sheet run nothing; Clear Website Data's confirmation names history, bookmarks, site permissions,
     cookies, cache and local storage, with the sign-out warning (V1, V24);
   - Clear Cookies signs you out of a site live; clicked before any page has shown, the next start's first page is
     signed out too, and `chromium-clear` is gone afterwards;
   - Clear Website Data clears a site's local storage live, and "All website data has been cleared successfully!"
     shows only once it is done; after a restart, `data/chromium` is new, neither `chromium-clear` nor a
     `chromium-cleared-` folder remains, and `chromium.log` is empty; a /dev/null tab's session survives both
     clearings;
   - a clearing while Chromium is starting waits, then clears live, its success within 60 s; the Clear All Data button
     stays busy while it runs; the helper browser shows nothing; quitting right after a clearing ends without the 10 s
     stop warning or a crash; Chromium's log shows no `Storage.clearDataForOrigin` error for the cleared sites;
   - after a Clear Website Data while `browser.sqlite` could not be used, the next start opens a fresh
     `browser.sqlite`; on a network home where chmod fails, a marked database is still deleted.
6. **Ad blocking:** pending.
   - Update Now shows the rule count;
   - an ad test page shows blocked requests and hidden elements;
   - turning it off and reloading brings them back.

   From the task reviews (**Task 10**):
   - with EasyList on, a news page's ad requests fail (DevTools' Network shows them as failed) and its ad slots are
     hidden, in a /dev/null tab too; a first-party resource on `www.cnn.com` is not blocked (V-F7);
   - Update Now is busy and reads "Updating…", then "*n* rules · Updated *date*"; a bad or unreachable address reads
     "Could not update the filter list: …"; a second press while it updates does nothing;
   - after a restart, Filter Statistics reads "Loads when ad blocking is on and a browser page opens" (V52), then, once
     a page shows, the cached count without a download; `data/adblock/rules.json` and `state.json` are `rw-------` in a
     `rwx------` data folder;
   - the onboarding's setup page shows the Ad Blocker and its Settings note; the switch saves `ad_blocking`, which
     Settings › Tools › Browser shows, and stays as set (V35);
   - a hostile list served from `http://127.0.0.1`, whose selectors try `input[value^="a"]` with a `background:url(…)`
     and a `}` injection: those lines are dropped, the attacker's host gets no request, and the well-formed lines still
     hide;
   - a page with `style-src 'self'` still hides elements, with no content security policy error from Jasper in its
     console; changing Filter List URL downloads the new list at once; with `state.json`'s `updated` set 8 days back, a
     restart shows "Ad block rules haven't been updated for 8 days";
   - Jasper's own pages (welcome, history, permissions) are unaffected; quitting during a download from a slow local
     server neither hangs nor shows the 10 s stop warning; a page with many iframes loads without visible stalls;
   - a list at a local `http` address that redirects to a LAN host reads "…redirected to an address that is not https",
     and the LAN host gets no request (V38);
   - while the list is on, `jstack` shows one daemon thread, `jasper-browser-filter-list`, which is gone once the
     Browser is turned off (V37).
7. **The proxy:** pending.
   - with a local proxy on 8118 (`privoxy` or `mitmproxy -p 8118`), pages load through it, /dev/null tabs included;
   - with the proxy stopped, they fail;
   - turning the setting off loads them directly;
   - Chromium's log shows no `setPreference` error, and Jasper posts no "The proxy could not be set".

   From the task reviews (**Task 11**):
   - favicons and search suggestions go through the proxy too, while a `localhost` dev server's page and favicon go
     directly (V43); turning the proxy off returns to the system's settings;
   - a refused preference shows "The proxy could not be set" and the /dev/null tab stays blank, and once the proxy is
     off a new tab in that group loads (V44);
   - `proxy_host = "::1"` with a proxy on `[::1]:8118` works, and the log reads `server=[::1]:8118`;
   - with DEBUG logging for `dev.jasper.browser.engine.NetworkPreferences`, "Chromium's proxy preference reads
     {mode=fixed_servers, server=127.0.0.1:8118}" (or `{mode=system}` when off), for the global context and each
     /dev/null context (V-F38);
   - a change closes the kept-alive connections (`CloseAllConnections`): the next request opens a new one through the
     new route;
   - with the proxy on, quit, set `http_proxy = false` in the plugin's settings file and start again: the first page
     goes directly, as the start overrides what the profile kept.
8. **Settings:** Manage Permissions opens the permissions page in a new tab behind Settings, which stays in front.
   **A question for the user:** is that acceptable? Pending.

   From the task reviews:
   - **Task 8:** Settings › Tools › Browser lists General, Link Opening, Search Engine, Privacy & Data, Ad Blocking and
     Proxy; Privacy & Data ends with Clear All Cookies, Clear Website Data and Manage Site Permissions, each declared
     row edits and applies as before, and a search marks the three rows; Manage Permissions opens the persistent
     store's page, with /dev/null mode on too.
   - **Task 11:** the Proxy section shows Enable HTTP Proxy, Proxy Server and Proxy Port, and `http://x` or `a b` in
     Proxy Server shows "Enter a host name or IP address.".

#### Lines the final review's fix wave added

Each is pending.

- **Quitting during a clearing (V54):** quit within a second of Clear Website Data's OK: Jasper ends without the 10 s
  stop warning or a crash.
- **The proxy off (V54):** on a run with Enable HTTP Proxy off, Jasper posts no "The proxy could not be set".
- **An http filter list on this computer (V51):** with `-Dhttp.proxyHost` and `-Dhttp.proxyPort` naming a proxy in
  Jasper's [custom VM options](configuration.md#custom-vm-options) and Enable HTTP Proxy off, a filter list at
  `http://127.0.0.1:PORT/` or `http://[::ffff:127.0.0.1]:PORT/` downloads directly: that proxy gets no request.

### Batch 2: start-up and memory (pending, V4)

At the benchmark sitting, with the user's OK: the start-up and memory batch (`tools/benchmarks/run-macos.sh`), once
with ad blocking off and once with it on (V-F35), a page with many iframes among the pages (V-F34). Pending.

### Plan 6 on Windows and Linux (not run)

The user has no Windows or Linux host yet. Every line above waits for each of them too, with Ctrl in place of Cmd.

- **Windows:** with the camera privacy switch (Settings › Privacy › Camera) off, Allow gives the page a
  `NotAllowedError` or `NotReadableError` and Jasper stays up; Clear Website Data's wipe moves `chromium` aside as
  `chromium-cleared-` and a number and deletes it, and while a profile file is held open, the profile stays whole with
  its marker until a later start; while another program holds `browser.sqlite` after a marked clear, the next start
  runs history in memory with the "unavailable" notice, and the start after that deletes the file.
- **Linux:** the camera through PipeWire or V4L2: Allow opens it.

## Plan 7: downloads and page tools

### Batch 1: the user's check on macOS (pending)

- **How to run it:** with the user's OK for each batch, `./gradlew :jasper-app:run` from
  `claude/browser-downloads-page-tools` (worktree `.claude/worktrees/browser-4b-4c-e6deab`), on JBR 25.0.4.1 b635.75
  with JCEF. The lines about macOS's folder prompts need the packaged `Jasper.app` (`./gradlew :jasper-app:packageApp`,
  without `-Pjasper.aot=true`), whose `Info.plist` has the usage descriptions
  ([macOS Downloads, Desktop and Documents folders](packaging.md#macos-downloads-desktop-and-documents-folders)). A
  killed transfer uses a local `python3 -m http.server`, and the proxy line a local proxy on port 8118 (`privoxy`, or
  `mitmproxy -p 8118`). The network the lines use, which the user has accepted: a large file from the web, article
  pages, sites with cookie banners, `github.com`, a site behind Cloudflare and a Save Page As… of a public page. Marks
  are read with `xattr -p` only: this Mac's endpoint agent ends the whole process tree of an `xattr -w` that adds a
  quarantine attribute (D17).
- Each line is recorded pass or fail. Every line is pending.

#### First: what only the GUI can show

1. **A closed browser's download (F1, D25):** a page that starts a blob download and calls `window.close()` in the same
   task ends with either a marked file in Downloads and its entry in the list, or no file and no entry; never an
   unmarked file. CEF lets a closed browser's download finish. Pending.
2. **The title during progress (F2, D11):** a running download's title reads "name — 42%", without `.crdownload` or
   "Unconfirmed". Pending.
3. **The open menu (F9, D13, D14):** with Downloads open on macOS's screen menu bar during a download, the title
   counts up in place and Stop stays clickable; a download that finishes while it is open changes its entries once it
   closes. Record whether Chromium's progress pauses while AppKit tracks the menu. Pending.
4. **Quitting mid-download (F29):** no `.crdownload` file remains in the Downloads folder, and a download that finished
   keeps its mark through Chromium's rename (`xattr -p com.apple.quarantine` on the file). Pending.

#### The plan's checklist

1. **The first download:** a large file goes to `~/Downloads` without asking. The Downloads menu appears with it, and
   its title shows the progress. Pending.

   From the task reviews:
   - **Task 3 (F8, D4), the packaged app:** macOS's prompt for the Downloads folder comes at the first download, not at
     the first tab, with "Jasper saves the files you download in the Browser here."; Allow lets downloads go; Deny makes
     them fail with the notification (record the reason); `plutil -extract NSDownloadsFolderUsageDescription raw` on
     the bundle shows the string. With the Downloads folder set to a folder inside `~/Documents`, and once inside
     `~/Desktop`, the prompt comes at the first browser tab, with "Jasper saves files from the Browser here when you
     choose this folder.".
   - **D1, D20 and D46:** a page that downloads twice without a click downloads once and posts one "This site tried to
     download multiple files" with its host; a click downloads again; a page that reloads itself without a click (a
     meta refresh or `location.reload()`) and downloads at each load downloads once in all, with one notice; a link
     clicked in the page to such a page allows one more; two file addresses typed into the address field one after the
     other both download.
   - **D22 (known gaps; record):** one click on a page that starts three downloads within 5 seconds; a click whose
     download the server answers after more than 5 seconds, which counts as the page's one download without a click;
     a click on a link whose page the server answers after more than 5 seconds, which allows no new download (D49).
   - **D19 (known gap; record):** whether any download stays running with no progress, as one Chromium never updates
     after it starts would.
2. **The menu:** with the menu open during progress, Stop works. Then Open, Show in Finder, Remove from List and Clear
   All. Pending.

   From the task reviews (**Task 1b**):
   - JBR shows an in-place text or enabled change on an open native menu;
   - a menu opened from the keyboard (Ctrl+F2, then the arrows) holds structural changes too;
   - while any Jasper menu is open, a plugin that registers or removes a placed action, such as Remote's Connect
     appearing, leaves the open menu as it is, and the change shows once it closes; a placed action's enabled change
     shows at once, the screen menu included.
3. **Names:** the same file again gives `name (1).ext`. Pending.
4. **The mark:** `xattr -p com.apple.quarantine` on the file shows Jasper's mark, `0081;<hex>;Jasper;<UUID>`, or
   Chromium's own; record which (F35). A downloaded app is checked by Gatekeeper when opened. Pending.

   From the task reviews:
   - **Task 1c:** opening a downloaded `.command`, `.app` or `.jar` from Finder brings Gatekeeper's "downloaded from the
     Internet" question; Jasper writes no QuarantineEventsV2 entry, so record whether it names the site.
   - **Task 3 (F35, F7):** a /dev/null tab's download is marked, and its entry leaves the menu once the group's last tab
     closes, while the file stays; a DevTools download (the Network panel's "Save all as HAR") lands in the list and
     holds no page.
5. **Notifications:** finished, and failed, by killing a local `python3 -m http.server` mid-transfer. A navigation
   that turns into a download shows no error page. Pending.

   From the task reviews (**Task 3**): Stop posts nothing.
6. **Alt-click and Save Link As…:** Alt-click on a link loads it. Save Link As… asks in Jasper's chooser, and Cancel
   downloads nothing. Pending.

   From the task reviews (**Task 4**):
   - Save Image As… asks in Jasper's chooser, and a `data:` image saves (D9); each chooser starts at the Downloads
     folder and the file's name;
   - Option-click loads in place; Cmd+Option-click still opens a new tab, Shift+Option-click does what Shift-click
     does, and an Option-drag of a link still drags it; an Option-click made as the pointer reaches a link (D23, the
     hover address's lag; record);
   - Save Link As… on a link that redirects (an `http://` link to its `https://` file) asks once (F32), and a page that
     starts its own download a few seconds after Save Link As… does not take the chooser;
   - a tab closed after a cancelled Save Link As… chooser closes at once, and so does a tab closed while the chooser
     shows, which cancels it (F1);
   - D24: Save Link As… whose chooser is cancelled while another Save Link As… download waits for its chooser: the
     other, once saved, is listed and marked;
   - F28: a `target=_blank` link to a file, and a `window.open` of one, leave no empty tab, and the file downloads; a
     file address typed into a new tab's address field keeps its tab; with a Downloads folder that cannot be used, the
     empty tab closes and "Download failed" says why.
7. **Closing and quitting:** closing the tab mid-download lets the file finish, with the page silent. Quitting
   mid-download leaves no file under its name. Pending.

   From the task reviews (**Task 4**):
   - closing a tab mid-download keeps the entry, the page goes to `about:blank` at once, the file is marked, and the
     held browser then closes; the same for a /dev/null tab that is its group's last;
   - quitting with a closed tab's download still running stops it, leaves no partial file and does not hang.
8. **The Downloads folder:** a custom folder works. One that cannot be created fails with the notification. Pending.

   From the task reviews:
   - **Task 2:** the Downloads folder row shows in Settings › Tools › Browser › General after Page zoom, with its
     description, and `relative/dir` shows "Enter an absolute folder path, or leave it empty.";
   - **Task 3:** a folder that is a file fails with "“PATH” is a file, not a folder.";
   - a folder on another volume works.
9. **Reader mode:** on an article, ☰ offers both items, and Reader Mode shows the article in Jasper's colours. Escape
   and Exit Reader Mode end it, a navigation resets it, and `about:blank` offers nothing. Pending.

   From the task reviews (**Task 5**):
   - `jasper-about:` pages offer nothing; a single-page app's route change withdraws the offer and offers again about a
     second after it settles; a background tab that loaded an article is offered once it shows; a /dev/null tab offers
     and shows reader mode too;
   - Trusted Types (D30): on a site that enforces them, reader mode is offered on most articles; only one that needs
     Readability's second pass, or whose images sit in `noscript`, gives nothing;
   - a site behind Cloudflare still passes its check (R7), and D10's known gap: a site's own "Just a moment…" page is
     checked after its load ends (record);
   - F11: reader mode on `github.com`, whose `style-src` is strict, shows styled;
   - the article in a light and a dark theme, its scroll bars following; an RTL article shows right to left; none of
     the page's scripts, frames or forms run or show in it; a site built of custom elements shows their text, and none
     of its components run in the overlay;
   - links: a link loads in the item and ends reader mode; a table-of-contents link scrolls the article to its heading,
     the address and the page behind unchanged and the offer kept; after following a link out, Back shows the page
     without the overlay;
   - scrolling and keys: the wheel, the trackpad, the arrows, Space and Page Down scroll the overlay and not the page;
     a site's own shortcuts (GitHub's `s`, `/`, `j` and `k`) do nothing behind it; Tab moves only among the overlay's
     links, and clicks and typing reach nothing of the page; after the reader closes, the page is where it was and
     scrolls again;
   - focus: after choosing Reader Mode from ☰, the arrows and Space scroll the article at once; after Escape or Exit
     Reader Mode, what had the focus has it again, and the page did not scroll;
   - Escape: holding it, then releasing it, leaves nothing for the page to hear; pressing another key before releasing
     it gives that key to the page; a page that stops Escape in a window capture listener of its own keeps the reader
     open, and Exit Reader Mode closes it;
   - Find (D31): with the reader showing, a query counts and highlights matches in the article only, yellow with the
     current one orange, Next and Previous scroll the overlay and never the page, and a page's iframes count nothing;
     a find open when Reader Mode opens is cleared, and Enter searches the article; after Escape or Exit Reader Mode it
     is cleared again, and Enter searches the page;
   - clicks (D32): on a page with a "click anywhere" popunder or document-level click analytics, clicks, double clicks
     and right-clicks in the article do nothing on the page, while links, text selection and Jasper's right-click menu
     still work;
   - zoom (F25): the overlay follows the item's zoom and a 150% Page zoom;
   - hash routing (D30, known gap): on a hash-routed app (`#/inbox` to `#/sent`), the first check's offer stays across
     route changes, and Reader Mode reads the current view.
10. **Distraction removal:** the crosshair and the red outline show. A click hides a cookie banner and its backdrop.
    Escape ends it, and what was hidden stays hidden. Pending.

    From the task reviews (**Task 6**):
    - Exit Distraction Removal ends it too, and ☰'s label follows; a link, a reload or Back ends it, and ☰ shows
      Remove Distractions again; after choosing it from ☰, Escape works without a click in the page;
    - F11: on `github.com` the crosshair and the outline show;
    - while it runs, a click on a link neither follows it nor runs the page's handler, a middle click opens no tab, a
      double click selects no word, and a right-click shows Jasper's menu, whose Remove Element works;
    - D34: Tab to a link and Enter hides nothing and follows nothing; a double click on a banner hides it once, and
      nothing the second click lands on;
    - Escape held: removal ends once, and the page hears neither the repeats nor the release; a later Escape reaches
      the page;
    - frames: on a site whose consent banner is an iframe, the pointer outlines the banner's holder, the crosshair shows
      over the frame, and a click hides it; record what the outline and a click pick on a page with a full-width
      embedded video;
    - a cookie banner whose dimmer is appended at the end of the document is hidden with it;
    - D35: after hiding a cookie wall that locked scrolling, the page scrolls, also after Escape, and hiding a small
      banner leaves the page's overflow alone; record a wall whose page locks the body with `position: fixed`, a modal
      `<dialog>` hidden by a click, and a page that locks `overflow-y` for its own layout, which scrolls after a hide
      that covers the viewport (known gaps);
    - Remove Distractions while Reader Mode shows closes the reader first, and Reader Mode while removal runs ends
      removal and the crosshair;
    - capture listeners (D29, known gap; record): on a test page whose capture listeners on `window`, added before
      Jasper's scripts, log keys and clicks, they log both in reader mode and in distraction removal; in reader mode one
      on `document` logs the article's clicks too.
11. **Remove Element** hides a banner under the pointer, at 100% and 150% zoom. Pending.

    From the task reviews (**Task 6**): also on an item reset to a 150% Page zoom (Page zoom 150, then Reset Font
    Size, F25); on a `jasper-about:` page, such as the welcome page, the item is dimmed; over reader mode's article it
    hides nothing.
12. **Save Page As…:** it saves a folder named after the host, shown in Finder, whose `index.html` opens offline with
    its images and styles. A read-only location shows "Save Failed". Pending.

    From the task reviews (**Task 7**):
    - offline, `srcset` and `<picture>` images, `@import`ed style sheets and web fonts show;
    - F26 and D7: a second save of the same host offers `host (1)`; a page that navigates while the chooser is open
      fails with "the page navigated away"; Save Page As… is dimmed on `jasper-about:welcome`;
    - F13: after the save, `xattr -p com.apple.quarantine` on the folder's `index.html` and on a file in its
      `resources/` shows the mark, which each took before its rename;
    - F27: a page with a `Content-Security-Policy` meta element that limits images opens offline with its images, and a
      page of more than 1 MiB of HTML saves whole;
    - F12: a public page whose image is at a `192.168.x.x` address keeps that image's web address;
    - D5 and D6: with the proxy on 8118, the save's fetches go through it; switching the proxy during a long save leaves
      the resources not yet fetched at their web addresses, and the save finishes;
    - the copy loads nothing: during a save, DevTools' Network panel shows no request to a `jasper-save-` address;
    - a page with `<base href>` and with `&` in its addresses saves with working absolute links, and `#` links scroll
      within the saved page;
    - D36 and D42: a save while reader mode shows, and one while distraction removal outlines a banner, open without
      the overlay, inert or locked scrolling, and without the red outline;
    - D41: quitting during a long save ends without the 10 s stop warning and leaves no unmarked file.

#### Lines the final review's fix wave added

Each is pending.

- **The folder prompt (D48):** in the packaged app, while macOS's prompt for the Downloads folder shows, record whether
  Jasper's windows still answer the pointer and keys, as Jasper reads the folder for the download's name on Chromium's
  thread.
- **A held Escape on a page with its own handler (D47, D48):** on a page whose own Escape handler shows that it ran,
  such as a test page that logs its keys, holding Escape closes reader mode, and in another run ends distraction
  removal, and the page's handler hears no Escape until the key is released; the next Escape reaches it. Record, in
  both, whether releasing Escape after Cmd+Tab has left Jasper keeps the page's next Escape from it (D49: until a
  stop, the next start or the page going).
- **A modal `<dialog>` over the reader (D48):** a page that opens a modal `<dialog>` with `showModal()` from a timer
  while reader mode shows: record whether it shows over the article and takes keys and clicks, as only the page's body
  is inert.
- **Back to a cached page (D48):** Reader Mode on an article, a link out, then Back to the page restored from the
  back-forward cache: record whether ☰ offers Reader Mode again.

### Batch 2: start-up and memory (pending, F35)

At the benchmark sitting, with the user's OK: the start-up and memory batch (`tools/benchmarks/run-macos.sh`), checking
that the downloads made at the plugin's start cost nothing (F35, as plan 6's V4), with the reader check's parse at each
load end on an article page (D30) and a large Save Page As… (a maximal save completes in a 320 MiB heap, or 512 MiB for
HTML held as UTF-16, D43). Pending.

### Plan 7 on Windows and Linux (not run)

The user has no Windows or Linux host yet. Every line above waits for each of them too, with Ctrl in place of Cmd and
Alt in place of Option; a window manager may take Alt with a drag.

- **Windows:**
  - `Get-Content <file> -Stream Zone.Identifier` shows `[ZoneTransfer]`, `ZoneId=3`, `ReferrerUrl` and `HostUrl`,
    without user names and passwords, on a download and on a Save Page As… file; a /dev/null tab's download shows only
    `ZoneId=3` and `HostUrl=about:internet` (F7);
  - Show in Explorer selects the file (F31), also for a name with a comma and a space under a user folder with a space
    (D21);
  - a Downloads folder on a FAT32 or exFAT stick fails the mark with "This volume cannot keep a downloaded-file mark",
    and the download has no Open (F33, F6);
  - with Downloads moved to another drive (Downloads › Properties › Location), downloads go there after a restart;
  - a deep custom folder keeps the path under `MAX_PATH` (F4), and a Downloads folder value ending in a space is
    refused;
  - a title growing while its popup is open ("9%" to "10%") may clip (F18); record it;
  - a narrow window's More menu, opened and closed without entering a folded menu, lets a held change through
    (Task 1b);
  - distraction removal's crosshair shows.
- **Linux:**
  - with `XDG_DOWNLOAD_DIR="$HOME/Téléchargements"` in `~/.config/user-dirs.dirs`, and once with `XDG_CONFIG_HOME`
    pointing elsewhere, downloads go to that folder after a restart;
  - Show in Folder opens the file's folder, through `xdg-open` where AWT has no `Desktop`, and Open uses `xdg-open`
    there (F31);
  - Open still opens a file, as Linux has no mark (F6), and `getfattr -d <file>` shows no attribute Jasper added;
  - the F18 clip line, the More menu line and the crosshair, as on Windows.

## Plan 8: logins

### Batch 1: the user's check on macOS (pending)

- **How to run it:** with the user's OK for each batch, `./gradlew :jasper-app:run` from `claude/browser-logins`
  (worktree `.claude/worktrees/browser-4b-4c-e6deab`), on JBR 25.0.4.1 b635.75 with JCEF. Its Jasper home,
  `jasper-app/build/dev-home`, holds its own vault: use a test vault there, never the user's everyday one, and line 5's
  relaunch keeps it. The network the lines use, which the user has accepted:
  `https://httpbin.org/basic-auth/user/passwd` and `https://httpbin.org/digest-auth/auth/user/passwd`, a public sign-in
  page whose user name field has `autocomplete=username` (github.com's), a small local server (a few lines of Python's
  `http.server`) or an intranet host for F6's 301 and 401 and for every line that needs a page of its own (F5, the
  realms, a 401 loop, a timer, a meta refresh, a 204, a redirect, a tall field), and for F22 a local proxy on port 8118
  that asks for a password. F12's line needs the logger `dev.jasper.browser.links.PageNavigation` at FINE (a
  `java.util.logging` configuration file with `dev.jasper.browser.links.PageNavigation.level = FINE`, named by its
  absolute path in `-Djava.util.logging.config.file=`, for `:jasper-app:run` in `JAVA_TOOL_OPTIONS`); its line then
  lands in `jasper-app/build/dev-home/logs`.
- Each line is recorded pass or fail. Every line is pending.

#### First: what only the GUI can show

1. **The sign-in's thread and address (F12):** the log's one line "Chromium asked for a sign-in on the thread
   "*name*", naming a full address" or "… naming an origin alone": record which thread calls `getAuthCredentials`
   (Chromium's IO thread, or AppKit's main thread) and which form `originUrl` takes. F5's top-level rule depends on the
   second: with an origin alone, every sign-in of the tab's site counts as the tab's own. Pending.
2. **The router's cancel (F14):** navigating, reloading or closing the tab with the picker open closes the picker and
   fills nothing. Pending.
3. **A redirect before the sign-in (F6):** an `http://` address that answers 301 to `https://`, which answers 401 (an
   intranet host, or a local server), offers Remember this password. AGENTS.md's Verified JCEF behaviour states this
   redirect from CEF's documented behaviour, which this line confirms. Pending.
4. **The field script runs first (F13):** as a rule `login-fields.js` runs before the page's own scripts: a page that
   replaces `HTMLInputElement.prototype.value`'s setter early still gets the native fill. Pending.
5. **Settings and the manager (F7, L2):** Manage Saved Passwords closes Settings first, and the manager and its unlock
   prompt then take clicks and keys. Pending.

#### The plan's checklist

1. **Buttons:** on a sign-in page, the password field shows 🔑 and an `autocomplete=username` field shows 🎫, each
   titled "Open Password Manager". Pending.

   From the task reviews (**Task 4b**):
   - L-F13 (plan 7's F11): on github.com's sign-in page, with its strict `style-src`, the buttons show styled;
   - the button sits inside the field's right edge and follows scrolling and resizing; a press on it keeps the focus in
     the field; once the picker opens the field loses the focus and its button hides, and it comes back when the page
     has the focus again; after a 🎫 fill the password field shows 🔑;
   - a user name field without `autocomplete=username` shows no 🎫, and a login form in a cross-origin iframe, or any
     iframe, shows no buttons (the user's call);
   - F24: a /dev/null item shows the buttons and fills;
   - a page that removes or covers the button only loses its own button;
   - L19 (known gap; record): on a page whose root element has a `transform` or a `filter`, as some dark-mode pages
     have, the button sits off its field.
2. **Filling:** 🔑 opens the Vault's picker under the field, searched for the host, and the choice fills the password;
   🎫 fills the username, then focuses and fills the password; a locked Vault asks to unlock first. Pending.

   From the task reviews:
   - **Task 1b:** the picker opens under the field, or above it near the screen's bottom, at HiDPI too, and a window
     resize closes it;
   - **Task 2:** the picker opened with a search lists only the matching logins and no None row, and clearing the search
     lists every one;
   - **Task 3 (L12, L14):** choosing an SSH key, an account key or a key-and-password login, in a field's picker or in
     the sign-in dialog's Password Manager, fills nothing: the field is unchanged, the dialog stays open, and no prompt
     follows;
   - **Task 4b:**
     - F26: on a `www.` host the search drops `www.`;
     - F25: on a pinch-zoomed page (trackpad) the picker opens under the field;
     - F16: a click elsewhere on the page closes the picker and reaches the page, and a second click on the field's 🔑
       closes the picker without reopening it;
     - L6 and L19: with the picker open, scrolling the page closes it, which rests on `jasperBridgeCancel` reaching
       Jasper as `onQueryCanceled`; a scroll of an unrelated scroller, such as a carousel, leaves it open; Cmd+= and
       Cmd+- close it; resizing the window or opening the find bar closes it;
     - F3, L20 and L21: typing never opens the picker; a page's own `login.pick` after a click elsewhere is refused
       ("The password manager opens only after a click in the page.") and spends that click, so a second ask after the
       same click is refused too; a double-click whose second press lands where a page-placed field's picker opens
       chooses nothing;
     - L20: in every anchored picker (the Browser's, Remote's host editor and File Server's server editor), a row
       pressed or entered within half a second of the picker opening does nothing, and works after it (in the
       Browser's, Enter once Down or Up has highlighted a row, L36); Cancel and typing in the search work at once;
     - L20 (per-host zoom): with two items on one host at different zooms, the picker either opens under the field or
       the pick is refused; it never opens elsewhere;
     - L21 and L36: the picker's starting search is not selected as it opens, so typing adds to it (the fix wave's
       lines below).
3. **Leaving:** navigating away with the picker open fills nothing. Pending.

   From the task reviews: F14's line above; F15, closing the tab with the picker open closes it and fills nothing; L19,
   a page's `pushState` while the picker is open closes it.
4. **Basic:** `https://httpbin.org/basic-auth/user/passwd` shows "The website “httpbin.org” requires a user name and
   password for “Fake Realm”."; OK signs in, Cancel shows the 401 page, and Password Manager signs in with a chosen
   login. Pending.

   From the task reviews (**Task 5b**):
   - the dialog is titled "Sign In", the heading wraps under it (a long realm from the local server), and the switch
     reads "Remember this password", off;
   - Enter in the password field signs in, and so does Enter in the user name field; Escape cancels to the 401 page;
   - F30: a page of several images behind one realm asks once;
   - F8: navigating away with the dialog open closes it, and the new page's own sign-in shows;
   - Password Manager opens the Vault's picker under the button, searched for the host without `www.`; cancelling the
     picker keeps the dialog; closing the tab while the picker shows closes both;
   - with the Vault locked, Password Manager shows the Vault's unlock prompt over the sign-in overlay, then the picker,
     which takes clicks and typing inside the modal overlay;
   - (record) a navigation's own sign-in dialog stays up until it is answered, with nothing, such as Chromium's reset of
     the page's dialogs, closing it early;
   - quitting Jasper with a sign-in dialog open and another waiting quits promptly, with nothing hanging on the
     requests;
   - **Task 5a:** a Basic-protected download (`Content-Disposition: attachment` behind a 401) still gets the sign-in.
5. **Remember:** ticked, the Vault then holds "https://httpbin.org (Fake Realm)"; after a relaunch with the Vault
   unlocked, the address typed in signs in with no dialog, while the restored tab's first page shows the dialog (L44);
   locked, the dialog shows; a remembered wrong password shows the dialog on the second try, with no loop. Pending.

   From the task reviews:
   - **Task 2:** the saved login shows in the manager under its name, and in Vault Plugin Grants as the Browser's
     (`dev.jasper.browser — https://httpbin.org (Fake Realm)`); with the manager open, the save adds its row at once and
     Plugin Grants lists the grant without the manager reopening; with the Vault locked, saving asks to unlock first,
     and cancelling the unlock saves nothing and posts nothing;
   - **Task 2 (F11):** saving again for the site with a new password replaces the Browser's own login, one row; a login
     of that name that another plugin also has a grant for is left alone, and a second entry appears;
   - **Task 6:**
     - a site that redirects after the sign-in (`/basic-auth` to another path, or the local server) still saves, and
       its next visit in the same tab, from the address field, fills silently again, as a redirect is the same
       navigation;
     - F10: closing the tab while the save's unlock prompt shows saves nothing, and the prompt closes;
     - L24 (record): whether Chromium reports the main-frame 401 before it challenges again after a wrong remembered
       password; either way the second try shows the dialog;
     - two quick navigations to a remembered site (Enter twice in the address field) give at most one dialog, and no
       loop;
     - a remembered password changed on the server: one silent try, then the dialog; OK with Remember replaces the
       Browser's login, and the next visit from the address field fills silently again;
     - L30: after a wrong remembered password, each later sign-in shows the dialog until the site answers a page and
       that page commits; after a correct one, the next visit from the address field fills silently again; Remember
       saving on httpbin shows the response is told before its commit, which the save and the re-arm rely on;
     - L31: after a silent fill, Stop (or a navigation at once) before the page loads makes the next sign-in to that
       site in that tab show the dialog, and in a new tab the address typed in fills silently again;
   - **Task 5a (L23, record):** after a sign-in, Back to a page of another origin and Forward again: whether CEF
     restores the page from its back/forward cache without a request; either way the restored page's commit is the top
     address.
6. **Plain dialogs:** `https://httpbin.org/digest-auth/auth/user/passwd` and a /dev/null item get the dialog without
   Vault controls; a local page framing the Basic URL gets it without Remember (with Password Manager, L27). Pending.

   From the task reviews:
   - **Task 5a (F5):** a page that fetches another origin's Basic address gets the dialog without Remember, and no
     silent fill;
   - **Task 5b:**
     - F4: a page that fetches a 401 in a loop gets two dialogs, then none until it loads again; a sign-in waits behind
       a camera prompt and shows once the prompt is answered;
     - L26 (known gap): a page whose requests three different realms challenge at once shows two dialogs, and the third
       request fails;
     - L27 and L28: trip the guard on a page (three realms, or a 401 retry page cancelled twice), then type a protected
       address in the address field: its sign-in dialog shows; a link clicked inside the tripped page shows the 401
       page instead (the known gap); cancel the typed address's sign-in: the 401 page shows, and a second protected
       address typed in asks again;
     - L29: a first navigation's late `ERR_ABORTED` leaves a quick second navigation's bypass, as only a failure of
       the address its sign-in is for ends it (the fix wave's lines below);
     - F22: a proxy on port 8118 that asks for a password shows "The proxy “127.0.0.1:8118” requires a user name and
       password…", and each tab it challenges asks on its own (the known gap);
   - **Task 5a (L13):** a host whose name has an underscore, such as an intranet name, gets the dialog without either
     Vault control;
   - **Task 5a (L25, record):** a page that rewrites its address on a timer during a navigation, and a 204 followed by
     the page's own fetch of that address: neither gets Remember;
   - **Task 6 (L32, record):** whether a prerendered page's activation brings a sign-in Jasper treats as the top-level
     navigation's.
7. **Manage Saved Passwords** opens the Vault manager, usable from Settings. Pending.

   From the task reviews (**Tasks 1b and 3**):
   - F7: it closes Settings first, asking about unsaved changes, where Keep Editing leaves Settings open and opens
     nothing; then the manager opens over the window Settings was over, in front of it and owned by it;
   - F21: the row, Manage Saved Passwords with the button Manage Passwords and no description, follows Manage Site
     Permissions in Settings › Tools › Browser › Privacy & Data, and searching Settings for "passwords" finds and
     highlights it;
   - locked, the unlock prompt comes first, in the manager; with no vault, the create flow runs, as for Open Vault....
8. **No Vault:** with the Vault plugin off, there are no buttons and no Manage Saved Passwords, and the dialog has
   neither Vault control. Pending.

   From the task reviews (**Task 3**): the dialog still signs in (the user's call); the Browser starts normally; and
   "passwords" does not lead to the Browser's Settings page.

#### The known gaps' lines

Each is pending.

- **The HTTP auth cache (F23):** after a sign-in, Clear Website Data leaves the site signed in until Jasper quits; a
  /dev/null tab's sign-in ends when its group's last tab closes, so a new /dev/null tab asks again.
- **The realm (F9):** a realm with a right-to-left override (U+202E), a line break or more than 100 characters, sent by
  the local server, shows cleaned and cut, and the saved login's name has the same realm.

#### Lines the final review's fix wave added

Each is pending.

- **The picker's rows (L36, L40):** on a sign-in page, press 🎫: the picker opens with the host in its search, the
  caret after it and the text not selected, and no login highlighted; typing adds to the search, and Enter does
  nothing, the picker staying open; Down highlights the first match and Enter fills it; Up from no highlight goes to
  New Credential…; a click on a login, with no arrow key first, after the half second, fills it. The sign-in dialog's
  Password Manager picker does the same.
- **A page's picker under your typing (L36):** a page that opens the picker from its own user name field's handler as
  you type: your keys go into the search, and Enter chooses nothing.
- **Remote's and File Server's pickers (L39):** the credential pickers of Remote's host editor and File Server's server
  editor open with the first row highlighted, and Enter chooses it; after Down, typing a character that keeps the same
  rows moves the highlight back to the first row; their placement, the clamp on a short screen included, is as
  before.
- **Beside the field (L37, L40):** a field near the bottom of the screen opens the picker above it; a field in the
  middle of a short window or screen, or a field nearly 200 CSS pixels tall, shrinks the picker to the larger room
  above or below the field, its rows scrolling, never over the field, at HiDPI too; with no room above or below for the search box, the picker does not
  open and the page is told nothing was chosen.
- **A tall field (L37):** a user name `input` styled taller than 200 CSS pixels: a click on its 🎫 opens no picker; a
  normal field's opens it.
- **A key after the click (L38):** press 🎫 and type a key before the picker opens, or a page that sends `login.pick`
  from a key handler after your click: no picker.
- **The silent fill's navigations (L35, L42-L44),** with a remembered Basic login for the local server's protected
  address and the vault unlocked:
  - it signs in without the dialog, redirects included, from the address field, from a bookmark opened in the same
    tab, from Back to it, and from a link you click or a form you submit with Enter;
  - the dialog shows, with Password Manager and Remember, and nothing is sent: for a page's script that sends the tab
    there from a timer, or posts a form there, with no click or key in the page for more than 5 seconds; for a link to
    a page whose meta refresh goes there within 5 seconds of its load, though a link clicked in that page then signs
    in without it; and for a new tab's first page: a bookmark or link opened in a new tab, a link from a terminal, a
    tab restored after a relaunch;
  - Back to an entry within the same page, Stop before a page arrives, or a 204, then the page's script sending the
    tab to the protected address a few seconds or minutes later: the dialog shows;
  - from the address field, a page with two realms, one of them remembered: cancel the first dialog, and the
    remembered realm's sign-in then shows the dialog;
  - a link clicked on a slow page, then the protected address typed before the link's page answers: the typed
    navigation signs in without the dialog;
  - Back's hold menu, an entry chosen while the page removes entries from its history, so that Chromium refuses it:
    a script's navigation right after shows the dialog; record how DevTools refuses the entry;
  - (record) the protected address typed while Chromium still starts, which can take more than 10 seconds at a first
    start: whether the first sign-in shows the dialog.
- **Remember and its navigation (L34):** sign in with Remember on, then press Stop, or click another link, before the
  page arrives: nothing is saved; a site that redirects after the sign-in still saves.
- **The bypass's end (L29):** from the address field, go somewhere while the current page has a slow navigation of its
  own pending: the new navigation's sign-in still shows past a tripped guard; Stop a sign-in's load: its bypass ends;
  record the address Chromium reports for the failure of a stopped load that had redirected, as only a failure of the
  sign-in's own address ends the bypass.
- **From the docs review (L33):** the sign-in dialog and the vault's Unlock Vault prompt dim the whole window; an
  international host's heading shows its punycode form (`xn--…`); a login field inside a web component's shadow root
  shows no button; Reload in a page whose dialog guard has tripped shows the 401 page, with no dialog; with the HTTP
  proxy asking for a password (407), pages get the sign-in dialog, and Jasper's own requests (a favicon, the search
  suggestions, View Source's copy, the filter list) fail.

### Batch 2: start-up and memory

None new: the plugin's start finds the Vault and makes `HttpSignIns`, and loads no Chromium class.

### Plan 8 on Windows and Linux (not run)

The user has no Windows or Linux host yet. Every line above waits for each of them too, with Ctrl in place of Cmd.

- **Windows:** an intranet host's NTLM or Negotiate sign-in may sign in with the system's credentials and no dialog;
  record it (F27).
- **Linux:** a Kerberos sign-in with a ticket may sign in with no dialog; without an emoji font such as Noto Color
  Emoji, the button shows a box, still titled "Open Password Manager" (F27).
- **Both:** the field buttons and the dialog as on macOS; and F7's Settings line: Manage Saved Passwords closes
  Settings first, and the manager is in front of and owned by the window Settings was over, on x64 and aarch64.
