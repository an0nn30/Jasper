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
  until it merges: the Mermaid lines (2, 5's diagrams, 9's Mermaid node, and the two slice 1b lines) run from there.
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
