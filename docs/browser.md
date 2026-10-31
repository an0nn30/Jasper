# Browser

The bundled Browser plugin (`dev.jasper.browser`) shows web pages in workspace tabs and panes, beside terminals. Jasper
draws them with Chromium, which comes with Jasper's runtime, so nothing else needs installing. It is also the web
browser Jasper opens links with: a link you click in a terminal can open here, after asking you first
([Links from terminals and Jasper](#links-from-terminals-and-jasper)). It is on by default and can be turned off on the
Plugins page ([Plugins](plugins.md#browser)); without it, links open in the system browser and local files in the
Default editor.

## Opening pages

- **New Browser Tab** (File menu and command palette) opens the [start page](#settings) in a new tab at the end of the
  strip; **New Browser Window** opens it in a new window. Neither has a default shortcut.
- The **Split** submenus list **Browser Right** and **Browser Down**, which open the start page in a new pane beside the
  focused one. The command palette calls them **Split Right with Browser** and **Split Down with Browser**; they have no
  default shortcut.
- Links you click in a terminal, Jasper's own web links and `jasper URL...` can open here, as
  [below](#links-from-terminals-and-jasper).

The start page is `jasper-about:welcome` by default: Jasper's welcome page, in the workspace theme's colours, whose
**Settings** opens Settings › Tools › Browser. An open welcome page shows a theme change the next time it loads.

Each page is the item of one pane. Its tab shows the page's title; while a page without a title loads, the tab keeps
its previous name, and once loaded it shows the address's host, else "Web Browser". The tab's icon is the page's
[site icon](#the-toolbar). The status bar shows the page's address, as the tab's tooltip and the address field do,
without any user name and password in it.

- **Close Pane** closes a browser pane at once. A page's own `window.close()` closes its pane too, as Chrome closes the
  tab; Chromium lets a page's script do so only while the page's history has a single entry, and a linked popup can
  always close itself ([Popups](#popups)).
- A page in a tab that is not shown draws one frame a second; its network requests go on.
- Chromium starts the first time a browser page shows, not when Jasper starts, and runs until Jasper quits. When it
  cannot start, Jasper posts "The browser could not start" with the reason, such as "Chromium did not start within 90
  seconds", and the plugin's log has it too. From then on the links Jasper hands the Browser open in the system browser
  (a local web file in the Default editor), and New Browser Tab, New Browser Window and the Browser split item post the
  notification again instead of opening a page.
- All pages share one Chromium profile, kept across runs: cookies, session cookies included, the cache and other site
  data ([Files](#files)).

## The toolbar

Along the top of each page, from left to right:

- **Back** and **Forward**, each disabled when the page has nowhere to go. Holding either for half a second lists the
  page's history that way, up to 12 entries, nearest first, each by its title or else its address; choosing one goes
  there. A right-click (Control-click on macOS), Shift+F10 or the context-menu key opens the list at once.
- **Reload**, which is **Stop** while the page loads;
- **Not secure**, in the warning colour, while the page is one you went on to past a certificate error
  ([Certificate errors](#certificate-errors));
- the address field, with the site icon at its left;
- the menu button (☰), whose **Settings** opens Settings › Tools › Browser.

The site icon, which the page's tab shows too, is the page's favicon once the page has loaded, Jasper's icon on
Jasper's own `jasper-about:` pages, and a globe while the page loads, on an [error page](#error-pages) and whenever the
page has no favicon Jasper can show ([Jasper's own requests](#jaspers-own-requests)). Its tooltip reads "Site icon -
drag to copy URL": dragging it drags the page's address, as a link and as text, and for a `file:` page as the file.

The address field shows the page's address until you type in it, or on a search engine's results page the search
terms ([Search and suggestions](#search-and-suggestions)). A click that brings the keyboard into the field selects its
text, as **Open Location…** does; a click while the field has the keyboard places the caret. While you type, a list
under the field suggests what to load, the first suggestion selected: **Enter** or **Tab** loads the selected one and
moves the keyboard to the page, and **Escape** gives up what you typed, shows what the field showed before and selects
it. With no list showing, Enter loads what the field holds. The field reads its text, without its surrounding spaces:

- an address that starts with `http:`, `https:`, `file:`, `about:` or `jasper-about:` loads as typed, with the
  characters an address cannot hold, such as spaces, escaped;
- an absolute local path, `/…`, `~/…` (in your home folder) or on Windows `C:\…`, loads its `file:` address;
- a domain (`example.com`, also with a trailing dot), an IP address (`192.168.1.10`, `[::1]`), `localhost`, or a name
  with a port (`devbox:8080`), each with or without a path, query or fragment, loads with `https://` before it;
- so does a single word such as `devbox` or `my_host`, alone or with a path (`devbox/app`): ASCII letters, digits, `_`
  and inner `-`, with a letter or `_` among them;
- an address that starts with `chrome:` loads nothing, and the text stays in the field;
- anything else is a search with the Default Search Engine, Google unless you [choose another](#settings).

While the pointer is over a link, its address shows at the bottom left of the page.

## Search and suggestions

The list under the address field holds, in order:

1. **Navigate to “…”** (URL): the address the text names, when it names one other than a single word;
2. **Search for “…”** (Web Search);
3. up to five of the search engine's suggestions (Search Suggestion), asked for once you pause typing for 150 ms, with
   "Loading suggestions…" after the rows until they arrive;
4. a single word's **Navigate to “https://…”**.

So Enter right after typing loads `example.com`, `localhost:3000` or `10.0.0.1`, and searches for a single word such
as `jcef`, whose address is further down. When the text is itself a results page's address, a search for its terms
comes first; a `chrome:` address lists nothing. **Up** and **Down** move the selection, and a click on a suggestion
loads it.

| Engine | `search_engine` | Searches with | Suggestions from |
|---|---|---|---|
| Google, the default | `google` | `https://google.com/search?q=%s` | `https://suggestqueries.google.com/complete/search?client=firefox&q=%s` |
| DuckDuckGo | `duckduckgo` | `https://duckduckgo.com/?q=%s` | `https://duckduckgo.com/ac/?q=%s&type=list` |
| Bing | `bing` | `https://www.bing.com/search?q=%s` | `https://www.bing.com/osjson.aspx?query=%s` |
| Kagi | `kagi` | `https://kagi.com/search?q=%s` | `https://kagi.com/api/autosuggest?q=%s` |
| Custom | `custom` | Custom Search URL; Google's while it is empty | Custom Search Suggestions URL, which answers OpenSearch JSON; none while it is empty |

`%s` stands for the search terms, or the typed text. The engine is asked about what you type, except text that is an
absolute local path, starts with `file:`, `about:`, `jasper-about:` or `chrome:`, holds a user name or password
(`name:secret@`), names an address with a port, a query or a fragment, or with a path it loads over `https`
(`devbox:8080`, `example.com?id=7`, `example.com/docs`), or is longer than 1,000 characters
([Jasper's own requests](#jaspers-own-requests)); a suggestion of the engine's that is a `chrome:` address is left out.

While nothing is typed, the field of a results page, of any built-in engine or of Custom while it is chosen, shows the
search terms instead of the address; the tab, the status bar, a drag of the site icon and the saved workspace keep the
address.

## The Web menu and keys

The **Web** menu shows in a window's menu bar only while that window's current item (the focused pane's item in the
shown tab) is a browser page. Its items act on that page; run from the command palette in any other pane, they do
nothing.

| Action | ID | macOS | Linux and Windows |
|---|---|---|---|
| Open Location… | `"dev.jasper.browser.open_location"` | Cmd+L | Ctrl+L |
| Back | `"dev.jasper.browser.back"` | Cmd+[ | Alt+Left |
| Forward | `"dev.jasper.browser.forward"` | Cmd+] | Alt+Right |
| Reload Page | `"dev.jasper.browser.reload"` | Cmd+R | Ctrl+R |

These keys apply only in browser tabs, where they win over the same keys bound elsewhere, also inside a page's text
field: on macOS, Cmd+R in a browser tab reloads the page, and in a terminal it still opens Search Shell History. Rebind
them in Settings › Keymap, under Browser ([Keyboard shortcuts](keyboard-shortcuts.md)).

- **Copy** and **Paste**, Jasper's actions (Cmd+C and Cmd+V; Ctrl+Shift+C and Ctrl+Shift+V), copy the page's selection
  and paste into the page. On Linux and Windows, Ctrl+C and Ctrl+V reach the page too.
- Every other key that no Jasper shortcut takes reaches the page.
- On macOS, Cmd+A, Cmd+X, Cmd+Z and Cmd+Shift+Z select all, cut, undo and redo in the page when the page itself does
  nothing with them.
- On Linux and Windows, the mouse's back and forward buttons go back and forward, when released over the page; the page
  does not get those clicks. On macOS the trackpad's two-finger swipe does not go back or forward.
- A Cmd+Option drag (Ctrl+Alt on Linux and Windows) moves the pane, as over any pane, and the page does not get it.
- Files and text dropped on a page reach it. A drag that starts in the page, such as a link, an image, selected text or
  a page's own draggable item, is cancelled.
- **Find**, **Find Next** and **Find Previous** search the page ([Find in page](#find-in-page)), and **Increase**,
  **Decrease** and **Reset Font Size** zoom it ([Zoom](#zoom)).

## Find in page

**Find** opens the find bar below the page and searches, with the page's selection as its query when it is one line,
else the last query. Its keys are the window's (Cmd+F; Ctrl+Shift+F on Linux and Windows) and, as in Editor tabs,
Ctrl+F on Linux and Windows. The bar has the query field, the **Match Case** and **Regular Expression** toggles
(JavaScript's regular expressions), the match count ("No matches", "1 match", "*n* matches" or "Invalid pattern"),
**Previous Match**, **Next Match** and **Close**.

| Key | In the find bar |
|---|---|
| Enter | Next match |
| Shift+Enter | Previous match |
| Escape | Close the bar |

**Find Next** and **Find Previous** (Cmd+G and Cmd+Shift+G; F3 and Shift+F3, and the window's Ctrl+Shift+G and
Ctrl+Alt+Shift+G, on Linux and Windows) go to the next and previous match, round the end of the page; with the bar
closed they open it and search again from the first match.

- Every match is highlighted in yellow, and the current one in orange, scrolled into view. A closed `<details>`, a
  `hidden="until-found"` element or a section whose control has `aria-expanded="false"` opens to show it.
- The search reads the page's visible text a block at a time, each run of white space as one space, so it finds words
  across links, other inline elements and line breaks in the page's source; a space in a plain query matches any run
  of white space. Text in form fields and in shadow roots is not searched.
- It searches every frame of the page, cross-origin frames included, up to 100, and adds up their counts. A frame
  counts at most 10,000 matches, and on a very large page those in the text it read within a quarter of a second.
- While the bar is open, the count follows changes to the page, until a search runs out of time. The page's next
  document ends the search, and Enter searches again.
- The bar searches once you pause typing for a tenth of a second; Enter and the toggles search at once.
- A query is cut to 1,000 characters.
- Closing the bar removes every highlight and gives the keyboard back to the page; the query stays for the next Find.

Find runs as a script in each of the page's frames, in the page's own JavaScript world, so the page's scripts, those of
third-party frames included, can read what you search for and change what the bar counts. A regular expression that
takes very long on a page's text can stop that page responding.

## Zoom

**Increase Font Size** and **Decrease Font Size** (View › Font Size; Cmd+= and Cmd+-, Ctrl+Shift+= and Ctrl+Shift+- on
Linux and Windows) zoom the page in or out by 5%, from 25% to 500%; **Reset Font Size** (Cmd+0; Ctrl+Shift+0) returns
it to **Page zoom**, 100% unless you [set another](#settings). They take the keys Settings › Keymap gives those
actions.

- A page opens at Page zoom and keeps the zoom you give it from page to page. That zoom is saved with the workspace
  ([Restore](#restore)); after Reset the page saves none, and comes back at Page zoom.
- On Linux and Windows, Ctrl with the mouse wheel over a page zooms it too, and that zoom is not saved. On macOS,
  Ctrl+wheel goes to the page, and a pinch on the trackpad does not zoom.
- Chromium keeps one zoom for each host: two pages of the same host that show at once, side by side, both show the zoom
  set last, and each takes its own again when it next loads or shows.

## The right-click menu

A right-click on a page (Control-click on macOS) opens Jasper's menu for what is under the pointer, in groups:

- over the page itself, **Back**, **Forward** and **Reload**;
- over a link, **Open Link in New Tab** and **Copy Link**;
- over an image, **Open Image in New Tab** and **Copy Image Address**;
- in a text field, **Cut**, **Copy** and **Paste**, Cut and Copy dimmed without a selection; elsewhere, with a
  selection, **Copy** and **Search with** *engine*;
- **Select All**, always;
- the pane items of a terminal's right-click menu: **Split ▸**, **Move Pane to Tab ▸** and **Close Pane**;
- **Print…**, and **Copy Page Title**, which is dimmed while the page has no title;
- **View Source** and **Inspect Element** ([View Source](#view-source), [Inspect Element](#inspect-element)).

**Open Link in New Tab** and **Open Image in New Tab** open the address in a new browser tab right after this one. As
the page could open it itself, they open an `http`, `https` or `about:blank` address, or a `file:` or `jasper-about:`
address from a page of the same kind, and are dimmed for any other. **Copy Link** copies the link as the page wrote
it. **Search with** *engine* searches for the selection, each run of white space as one space and at most 1,000
characters of it, with the Default Search Engine, in a new tab right after this one; it names the engine, a Custom
engine by its search URL's host, and does not show for a selection of only white space. **Print…** prints the page.

## View Source

**View Source**, in the page's right-click menu, shows the page's source in its pane, at
`jasper-about:source?url=…`, in a tab titled "Page Source – *host*": the heading "Page Source", the page's address
without any user name and password, and the HTML, its tags, attribute names, attribute values and comments in the
workspace theme's syntax colours, with the `http` and `https` addresses in its attribute values as links. **Back**
returns to the page.

- For an `http` or `https` page, Jasper fetches the page again each time the source page loads, after a restore too
  ([Jasper's own requests](#jaspers-own-requests)): the HTML the server sends without your cookies, which can differ
  from what you see, for example on a page you are signed in to. Only a successful answer (200) of at most 5 MB within
  10 seconds is shown.
- For any other page, and when that fetch fails, it shows the page's HTML as Chromium held it when you chose View
  Source. The pane keeps that HTML while it is open, for up to its last 20 View Sources. Without it, as after a restore
  or once the pane keeps it no longer, another page's source page says "The source of this page is no longer
  available.", and a web page's whose fetch failed says "Jasper can’t load the source of this page."
- It shows at most a source's first 5,242,880 characters, and colours its first 1,000,000; past either, it says so.

## Inspect Element

**Inspect Element**, in the page's right-click menu, opens Chromium's DevTools in a pane below the page's, inspecting
the element under the pointer. The pane's tab is titled "DevTools — " and the page's tab title. The pane closes with the
page, and is not saved with the workspace. Inspect Element again shows the same pane, with DevTools opened afresh at the
new element, so their state, such as the console's history, starts over. Inspect Element has no menu-bar item and no
default shortcut.

In the DevTools pane:

- **Copy** and **Paste** act in DevTools, and on macOS so do Cmd+A, Cmd+X, Cmd+Z and Cmd+Shift+Z;
- the Web menu and its keys act on the inspected page: Reload Page reloads it, Back and Forward move it through its
  history, and Open Location… goes to its address field;
- Jasper's Find and font size actions are not offered, so their keys reach DevTools, and so does Ctrl+wheel;
- a right-click shows no menu, and DevTools' file dialogs, such as for loading a profile, open nothing;
- a link DevTools open in a new tab, such as one in the console, opens in a new browser tab right after the page's,
  when the page could open it itself, as for [Open Link in New Tab](#the-right-click-menu).

## Links in a page

| Gesture | macOS | Linux and Windows | Opens the link |
|---|---|---|---|
| Click | click | click | in the same page |
| New tab | Cmd-click | Ctrl-click | in a new tab right after this one |
| Split | Cmd+Shift-click | Ctrl+Shift-click | in a new pane beside this one: to the right when the page is wider than tall, otherwise below |
| Middle click | middle click | middle click | in a new tab right after this one |

A Shift-click loads the link in the same page. A link or page that downloads a file does nothing, and so does an
Alt-click, which Chromium makes a download.

Links that open a new tab or pane, and popups, open only at `http`, `https`, `file`, `about` and `jasper-about`
addresses. A `mailto:` or `tel:` link opens nothing, however it is clicked. A `jasper-about:` address loads only as a
page's whole document: in a frame, or as an image, a script or a style sheet, it loads nothing.

## Popups

A `target=_blank` link, or a page's `window.open`, opens a new browser tab right after the page's, or a split for a
Cmd+Shift-clicked (Ctrl+Shift-clicked) `target=_blank` link. It opens only within 5 seconds of a click or key press in
the page (Escape and modifier keys alone do not count), and each click or key press opens one. Other popups are blocked,
without an indicator.

A `window.open` from the page's top frame keeps the two pages linked, as Chromium would, so sign-in popups can answer
their opener:

- `window.open` returns a stand-in for the popup's window with `closed`, `close()`, `focus()` and `postMessage()`.
- In the popup, `window.opener` is a stand-in for the opener's window with `closed`, `focus()` and `postMessage()`, and
  `window.close()` closes the popup's tab.
- A message is copied as JSON, up to 1 MiB of it: a value JSON cannot hold, such as a function, is not delivered (the
  sender's console says why), and a value such as a `Date` or a `Map` arrives as JSON writes it. The other page gets
  it only when the target origin is `"*"` or that page's origin at that moment, as a `message` event from the sender's
  origin whose `source` is the stand-in for the sender. A message sent to a popup before it starts loading is lost.
- `focus()` brings the other page's tab forward, raising its window, only while the calling page shows and within 5
  seconds of a click or key press in it that has not opened a popup.
- With `noopener` or `noreferrer` among its features, `window.open` opens the tab without a link and returns `null`.
  The targets `_self`, `_parent` and `_top` work as usual.
- A page opens `http`, `https` and `about:blank` addresses this way; `file:` and `jasper-about:` addresses only from a
  page of the same kind.

The link ends, and both stand-ins report `closed`, when the popup closes; when you send either page elsewhere from
Jasper, such as from the address field, Back, Forward or an OSC 8 link that loads there; and when a page's new document
has a `Cross-Origin-Opener-Policy` that would put it in a window group of its own, as Chromium would. When the opener
closes, the popup's `window.opener.closed` is `true`.

A page whose `Content-Security-Policy` sandboxes it without `allow-popups` cannot open popups. A page whose origin is
opaque, a `file:` page or one sandboxed without `allow-same-origin`, sends its messages from `"null"` and receives only
those sent to `"*"`.

Not supported:

- reading or setting the popup's `location`, or reading its `document`, through its stand-in;
- messages that are not JSON, such as a `Blob`, an `ArrayBuffer` or transferred objects;
- message handlers that require `event.isTrusted`;
- reusing a named window: a `window.open` with a name opens a new tab each time, and does not target a named frame;
- popups of a frame inside the page: a `window.open` there opens a new tab but returns `null`, and the new page has no
  `window.opener`.

## Page dialogs

- `alert()` shows a dialog in the window headed "Web Page Alert", with the message and **OK**; `confirm()` shows "Web
  Page Confirmation", with **Cancel** and **OK**. **OK** answers `true`; **Cancel**, Escape and closing the dialog
  answer `false`. A message longer than 10,000 characters is cut.
- Both wait until the page is its window's current item (the focused pane of the shown tab), so a page you are not
  looking at cannot cover the window.
- When a page opens dialogs one straight after another, each within a second of the previous one closing, the third
  and every dialog after it are answered as cancelled without showing, until the page next loads.
- `prompt()` shows nothing and returns `null`.
- Leaving or closing a page never asks, whatever its `beforeunload` handler says.
- A file input opens Jasper's [file chooser](workspace.md#the-file-chooser) over the page, for files on this computer:
  several when the input allows it, a folder when it asks for one. The input's accepted types do not narrow the
  listing. A page that asks where to save a file gets the save chooser.
- A page that enters fullscreen leaves it at once.

Closing a page's pane closes its dialog or chooser too.

## Error pages

When a page cannot load, its pane shows Jasper's error page in its place: a title, what went wrong, the address,
**Try Again**, "Check your internet connection and try reloading the page." and Chromium's name for the error, such as
`ERR_NAME_NOT_RESOLVED`.

| Title | Errors |
|---|---|
| No Internet Connection | `ERR_INTERNET_DISCONNECTED` |
| Server Not Found | `ERR_NAME_NOT_RESOLVED`, `ERR_NAME_RESOLUTION_FAILED` |
| The Connection Timed Out | `ERR_TIMED_OUT`, `ERR_CONNECTION_TIMED_OUT` |
| Can’t Connect to Server | `ERR_CONNECTION_REFUSED`, `ERR_CONNECTION_FAILED`, `ERR_ADDRESS_UNREACHABLE` |
| Network Connection Lost | `ERR_CONNECTION_RESET`, `ERR_CONNECTION_CLOSED`, `ERR_NETWORK_CHANGED` |
| Too Many Redirects | `ERR_TOO_MANY_REDIRECTS` |
| File Not Found | `ERR_FILE_NOT_FOUND` |
| Secure Connection Failed | Every `ERR_SSL_` and `ERR_CERT` error ([Certificate errors](#certificate-errors)) |
| Page Unavailable | `ERR_BLOCKED_BY_CLIENT`, `ERR_BLOCKED_BY_RESPONSE`, `ERR_ACCESS_DENIED` |
| Page Can’t Be Loaded | Any other |

A load you stop, or one that would download a file (`ERR_ABORTED`), shows no error page, and neither does a
`jasper-about:` address that fails.

While the error page shows, the address field, the tab's tooltip, the status bar and the saved workspace keep the
address that failed, and the tab is named by the error's title. **Try Again** and **Reload** load that address again,
as Enter in the address field does; Try Again has the keyboard when the page shows, so Enter and Space press it.
**Back** goes to the page before the failed load, and is disabled when there is none. After a Try Again that loads the
page, **Forward** leads back to the error page.

## Certificate errors

Chromium checks a site's certificate against the certificate authorities your system trusts. On Linux it reads the
authorities you add from NSS's database in your home folder, so add a corporate authority there with `certutil` (in
the `libnss3-tools` package on Debian and Ubuntu, `nss-tools` on Fedora):

```
certutil -d sql:$HOME/.pki/nssdb -A -t "C,," -n "Example Corp Root CA" -i example-corp-root.crt
```

A certificate that fails shows the "Secure Connection Failed" error page with "Jasper can’t verify the identity of the
website “*host*”.", the reason, such as "The certificate for this website is self-signed.", and the subjects of the
certificate chain, the site's first. **Details** then reveals "Someone may be impersonating “*host*”. If you continue,
Jasper accepts this certificate for it until Jasper quits." and **Continue to this site (unsafe)**, which loads the
site.

- From then until Jasper quits, every browser page loads that host while it presents that same certificate; another
  certificate for the host shows the error page again.
- While a page is at an `https` address of such a host, the toolbar shows **Not secure** before the address field, in
  place of the site icon.
- A revoked certificate (`ERR_CERT_REVOKED`), `ERR_CERT_INVALID` and `ERR_CERT_CONTAINS_ERRORS` show no Details and
  no way past.

## Links from terminals and Jasper

These links come to the Browser:

- an `http` or `https` link you Cmd-click (Ctrl-click on Linux and Windows) in a terminal, a URL written in the output
  or an OSC 8 link; a Cmd+Shift-click (Ctrl+Shift-click) asks for a split
  ([Terminal](terminal.md#links-and-file-paths));
- a local web file you Cmd-click in a terminal: HTML, XML, SVG, CSS, JavaScript, PDF, PNG, JPEG, GIF, WebP or HEIC;
- Jasper's own web links, such as Settings' Help button and the homepages in About › Licenses… and Settings › Plugins,
  and the links plugins open, such as the Editor's Markdown preview links;
- the addresses of `jasper URL...` ([Command line](command-line.md#the-jasper-command)).

`ftp` and `mailto` links in a terminal go to the system's handler, as without the Browser.

Each link goes where the first of these says:

1. An address from `jasper URL...` opens in a new browser tab, without asking, right after the shown tab of the window
   of the pane `jasper` ran in, else of the most recently active window, opening one when none is open.
2. A local web file follows **Open Local Web Files**: **Always Ask** (the default) asks; **Built-in Browser** opens
   its `file:` address in a new browser tab right after the current one; **Default Editor** opens it as a Cmd-click on
   any other file does.
3. While Jasper is the system's default web browser, every other link opens in Jasper's browser without asking,
   whatever **Open Links From Terminal** says: in a new browser tab right after the current one, or, for a split
   click, in a browser pane split from the clicked pane. Jasper checks this when it starts and again when you come
   back to one of its windows, at most every 30 seconds.
4. A split click, and Open URL in Split Right and Down, open the link in a browser pane split from the clicked pane,
   without asking. When **Open Links From Terminal** is **Default Browser**, they go to the system browser instead.
5. Every other link follows **Open Links From Terminal**: **Always Ask** (the default) asks; **Built-in Browser**
   opens it in a new browser tab right after the current one; **Default Browser** opens the system browser.

An OSC 8 link can name a target (`target=NAME` among its parameters). When a browser page opened for that name is open,
in any window, the link loads there, and that page is not brought forward; otherwise the link opens a new browser page
for the name, and later links that name it load there.

**Open in Jasper?** Asking shows a dialog in the link's window. For a link it reads "Jasper can display web pages!
Would you like to open this link in Jasper?" with **Use Default Browser**, **Cancel** and **Open in Jasper**; for a
local file, "Jasper can display files like this in its built-in web browser! Would you like to open this link in
Jasper?" with **Use Default Editor**, **Cancel** and **Open in Jasper**. **Open in Jasper** is the default button.
**Use Default Editor** opens the file in the [Default editor](editor.md#default-editor) (without one, with the
desktop's edit action, else in the file manager), never running it. **Cancel** and Escape open nothing. With **Remember
my choice** on, choosing **Open in Jasper** or **Use Default Browser** (**Use Default Editor**) saves that choice as the
setting, Built-in Browser or Default Browser (Default Editor); Cancel saves nothing. While the window shows another
dialog, such as Settings, the prompt cannot show, and the link opens in the system browser (the file in the
Default editor) that once.

**Open URL in Split Right** and **Open URL in Split Down** are in a terminal's right-click menu while the pane's
selection is one `http` or `https` address and either Open Links From Terminal is not Default Browser or Jasper is the
system's default web browser. They open the selected address in a browser pane to the right of the pane or below it,
without asking. Select the address by dragging: a double-click selects only part of it. They are also listed in the
command palette and Settings › Keymap, where they have no address and do nothing.

## Restore

When Jasper restores a window ([Saving and restoring the windows](workspace.md#saving-and-restoring-the-windows)), each
browser page comes back in its tab and pane, at the address it last showed, at its own [zoom](#zoom) and for the OSC 8
target it was opened for.
It loads the first time it shows, so restoring starts no Chromium; until then its tab is named after the address's host.
Chromium starts inside Jasper the first time a page shows and runs until Jasper quits. If Jasper ends while Chromium
starts or runs, for example in a Chromium crash or a force-quit, the pages the next launch restores wait instead: each
says "Jasper closed while this page was open." and loads when you click **Load Page**, press Enter in its address field,
click **Reload** or open a link in it.

- An address too long for the saved state (4,096 characters, counted with the target and the zoom) is saved without
  its query and fragment, and when it is still too long, not at all.
- A `data:` or `blob:` page saves no address. A `jasper-about:` page saves its address.
- A page that shows an [error page](#error-pages) saves the address that failed, and one that shows an error page's
  [source](#view-source) saves the source page of that address.
- An address is saved without any user name and password in it.
- A page that saved no address, or one the address field would not load, comes back at the start page.

A page's back and forward history, form contents and scroll position do not come back.

## Jasper's own requests

Jasper fetches these itself, apart from Chromium, without your pages' cookies or any credentials, and directly rather
than through a proxy your system's settings name:

- a page's favicon, once the page has loaded: for an `http` or `https` page, the icon its first `<link rel="icon">`
  or `<link rel="mask-icon">` names, else `/favicon.ico` at its server, when that is an `http` or `https` address. A
  PNG, GIF, JPEG or ICO favicon of at most 64 KiB that arrives within 5 seconds shows; any other, such as an SVG or a
  `data:` icon, leaves the globe. A page keeps the first favicon it names;
- the search engine's suggestions for what you type ([Search and suggestions](#search-and-suggestions)): at most 64
  KiB, within 3 seconds;
- View Source's copy of an `http` or `https` page ([View Source](#view-source)).

On a network that reaches the web only through a proxy, these fail: pages show the globe, the address field lists no
engine suggestions, and View Source shows the HTML Chromium holds.

## Settings

The settings live in `plugins/dev.jasper.browser/dev.jasper.browser.toml` in
[Jasper home](getting-started.md#jasper-home) and on Settings › Tools › Browser, in the sections General, Link Opening
and Search Engine. Jasper creates the file, with every setting commented out, when it is missing; changes in the file
or on the page apply without a restart. A bad value is reported and its default used
([Plugin settings](plugins.md#plugin-settings)).

| Key | Default | Values | On the page | Applies to |
|---|---|---|---|---|
| `start_page` | `"jasper-about:welcome"` | What a new browser tab, window or pane loads, read as the address field reads typed text; not empty and not a `chrome:` address ("Enter a web address.") | Start page | Browser pages opened afterwards |
| `page_zoom` | `100` | The [zoom](#zoom) pages open at and Reset Font Size returns to: a whole percent from 25 to 500 | Page zoom | Browser pages opened afterwards without a zoom of their own; Reset Font Size |
| `open_links_from_terminal` | `"ask"` | `ask` (Always Ask), `builtin` (Built-in Browser), `default` (Default Browser) | Open Links From Terminal | The next link; the right-click items at once |
| `open_local_web_files` | `"ask"` | `ask` (Always Ask), `builtin` (Built-in Browser), `editor` (Default Editor) | Open Local Web Files | The next local web file |
| `search_engine` | `"google"` | `google` (Google), `duckduckgo` (DuckDuckGo), `bing` (Bing), `kagi` (Kagi), `custom` (Custom): what the address field and the right-click menu's Search with search with, and where the field's suggestions come from ([Search and suggestions](#search-and-suggestions)) | Default Search Engine | The next keystroke in the address field, search and right-click menu |
| `custom_search_url` | `""` | With `custom`: the address of a search's results, an `http://` or `https://` address without a user name or password and with `%s` in its path, query or fragment where the terms go, such as `"https://search.example.com/?q=%s"`; empty searches with Google ("The URL must start with https:// or http:// and contain %s") | Custom Search URL | The next search |
| `custom_suggestions_url` | `""` | With `custom`: an address of the same form that answers OpenSearch JSON, with `%s` where the typed text goes; empty shows no engine suggestions ("The URL must start with https:// or http:// and contain %s") | Custom Search Suggestions URL | The next keystroke in the address field |

## Files

Under [Jasper home](getting-started.md#jasper-home):

| Path | Holds |
|---|---|
| `plugins/dev.jasper.browser/dev.jasper.browser.toml` | The [settings](#settings) |
| `plugins/dev.jasper.browser/data/chromium/` | Chromium's profile: cookies, the cache and other site data |
| `plugins/dev.jasper.browser/data/chromium.log` | Chromium's warnings and errors |
