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
its previous name, and once loaded it shows the address's host, else "Web Browser". The tab's icon is a globe. The
status bar shows the page's address, as the tab's tooltip and the address field do, without any user name and password
in it.

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

- **Back** and **Forward**, each disabled when the page has nowhere to go;
- **Reload**, which is **Stop** while the page loads;
- the address field;
- the menu button (☰), whose **Settings** opens Settings › Tools › Browser.

The address field shows the page's address until you type in it. **Enter** loads what it holds and moves the keyboard
to the page; **Escape** gives up what you typed, shows the page's address again and selects it. **Open Location…**
moves the keyboard to the field and selects its text. The field reads what you type, without its surrounding spaces:

- an address that starts with `http:`, `https:`, `file:`, `about:` or `jasper-about:` loads as typed, with the
  characters an address cannot hold, such as spaces, escaped;
- a domain (`example.com`), an IP address (`192.168.1.10`, `[::1]`), `localhost`, or a name with a port
  (`devbox:8080`), each with or without a path, loads with `https://` before it;
- an address that starts with `chrome:` loads nothing, and the text stays in the field;
- anything else is a Google search.

While the pointer is over a link, its address shows at the bottom left of the page.

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
- Find, zoom and a right-click menu on the page do nothing in a browser tab.

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
addresses. A `mailto:` or `tel:` link opens nothing, however it is clicked.

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
3. A split click, and Open URL in Split Right and Down, open the link in a browser pane split from the clicked pane,
   without asking. When **Open Links From Terminal** is **Default Browser**, they go to the system browser instead.
4. Every other link follows **Open Links From Terminal**: **Always Ask** (the default) asks; **Built-in Browser**
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
selection is one `http` or `https` address and Open Links From Terminal is not Default Browser. They open the selected
address in a browser pane to the right of the pane or below it, without asking. Select the address by dragging: a
double-click selects only part of it. They are also listed in the command palette and Settings › Keymap, where they
have no address and do nothing.

## Restore

When Jasper restores a window ([Saving and restoring the windows](workspace.md#saving-and-restoring-the-windows)), each
browser page comes back in its tab and pane, at the address it last showed and for the OSC 8 target it was opened for.
It loads the first time it shows, so restoring starts no Chromium; until then its tab is named after the address's host.
Chromium runs inside Jasper from the first time a page shows until Jasper quits. If Jasper ends while it runs, for
example in a Chromium crash or a force-quit, the pages the next launch restores wait instead: each says "Jasper closed
while this page was open." and loads when you click **Load Page**, press Enter in its address field, click **Reload** or
open a link in it.

- An address too long for the saved state (4,096 characters, counted with the target) is saved without its query and
  fragment, and when it is still too long, not at all.
- A `data:` or `blob:` page saves no address. A `jasper-about:` page saves its address.
- An address is saved without any user name and password in it.
- A page that saved no address, or one the address field would not load, comes back at the start page.

A page's back and forward history, form contents and scroll position do not come back.

## Settings

The settings live in `plugins/dev.jasper.browser/dev.jasper.browser.toml` in
[Jasper home](getting-started.md#jasper-home) and on Settings › Tools › Browser, in the sections General and Link
Opening. Jasper creates the file, with every setting commented out, when it is missing; changes in the file or on the
page apply without a restart. A bad value is reported and its default used
([Plugin settings](plugins.md#plugin-settings)).

| Key | Default | Values | On the page | Applies to |
|---|---|---|---|---|
| `start_page` | `"jasper-about:welcome"` | What a new browser tab, window or pane loads, read as the address field reads typed text; not empty and not a `chrome:` address ("Enter a web address.") | Start page | Browser pages opened afterwards |
| `open_links_from_terminal` | `"ask"` | `ask` (Always Ask), `builtin` (Built-in Browser), `default` (Default Browser) | Open Links From Terminal | The next link; the right-click items at once |
| `open_local_web_files` | `"ask"` | `ask` (Always Ask), `builtin` (Built-in Browser), `editor` (Default Editor) | Open Local Web Files | The next local web file |

## Files

Under [Jasper home](getting-started.md#jasper-home):

| Path | Holds |
|---|---|
| `plugins/dev.jasper.browser/dev.jasper.browser.toml` | The [settings](#settings) |
| `plugins/dev.jasper.browser/data/chromium/` | Chromium's profile: cookies, the cache and other site data |
| `plugins/dev.jasper.browser/data/chromium.log` | Chromium's warnings and errors |
