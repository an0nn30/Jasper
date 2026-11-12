# Browser

The bundled Browser plugin (`dev.jasper.browser`) shows web pages in workspace tabs and panes, beside terminals. Jasper
draws them with Chromium, which comes with Jasper's runtime, so nothing else needs installing. It is also the web
browser Jasper opens links with: a link you click in a terminal can open here, after asking you first
([Links from terminals and Jasper](#links-from-terminals-and-jasper)), and it draws the Editor's
[Markdown preview](#markdown-preview). It is on by default and can be turned off on the Plugins page
([Plugins](plugins.md#browser)); without it, links open in the system browser and local files in the Default editor,
the Markdown preview is the Editor's Basic one, and while Jasper is itself the system's
[default browser](#making-jasper-your-default-browser), web links open nothing.

## Opening pages

- **New Browser Tab** (File menu and command palette) opens the [start page](#settings) in a new tab at the end of the
  strip; **New Browser Window** opens it in a new window. Neither has a default shortcut.
- The **Split** submenus list **Browser Right** and **Browser Down**, which open the start page in a new pane beside the
  focused one. The command palette calls them **Split Right with Browser** and **Split Down with Browser**; they have no
  default shortcut.
- Links you click in a terminal, Jasper's own web links, `jasper URL...`, a launch's web addresses and, while Jasper is
  your [default browser](#making-jasper-your-default-browser), links from other apps can open here, as
  [below](#links-from-terminals-and-jasper).

The start page is `jasper-about:welcome` by default: Jasper's welcome page, "Welcome Back", in the workspace theme's
colours. It shows **Top Sites**, the eight sites you visit most ([History](#history)), with **Refresh**, and Quick
Actions: **Bookmarks** and **History**, which open the [bookmarks page](#bookmarks) and the [history page](#history),
**Settings**, which opens Settings › Tools › Browser, and **Permissions**, which opens the
[permissions page](#site-permissions). An open welcome page shows a theme change the next time it loads.

Until you finish the onboarding, the welcome page shows three onboarding pages instead: Meet the Jasper Browser, What
Can It Do? and Let’s Get You Set Up. **Start Browsing** on the last one finishes the onboarding, which is remembered in
`onboarding-finished` in the plugin's data folder, and shows the welcome page.

Each page is the item of one pane. Its tab shows the page's title; while a page without a title loads, the tab keeps
its previous name, and once loaded it shows the address's host, else "Web Browser". The tab's icon is the page's
[site icon](#the-toolbar). The status bar shows the page's address, as the tab's tooltip and the address field do,
without any user name and password in it.

- **Close Pane** closes a browser pane at once. A page's own `window.close()` closes its pane too, as Chrome closes the
  tab; Chromium lets a page's script do so only while the page's history has a single entry, and a linked popup can
  always close itself ([Popups](#popups)).
- A page in a tab that is not shown draws one frame a second; its network requests go on.
- Chromium starts the first time a browser page or a [Markdown preview](#markdown-preview) shows, not when Jasper
  starts, and runs until Jasper quits. When it cannot start, Jasper posts "The browser could not start" with the
  reason, such as "Chromium did not start within 90 seconds", and the plugin's log has it too. From then on the links
  Jasper hands the Browser open in the system browser (a local web file in the Default editor; while Jasper is itself
  the system's default browser, a web link opens nothing), and New Browser Tab, New Browser Window and the Browser split
  item post the notification again instead of opening a page.
- All pages but those in [/dev/null mode](#devnull-mode) share one Chromium profile, kept across runs: cookies, session
  cookies included, the cache and other site data ([Files](#files)).

## The toolbar

Along the top of each page, from left to right:

- **Back** and **Forward**, each disabled when the page has nowhere to go. Holding either for half a second lists the
  page's history that way, up to 12 entries, nearest first, each by its title or else its address; choosing one goes
  there. A list that has entries ends with **Show Full History**, which loads the [history page](#history). A
  right-click (Control-click on macOS), Shift+F10 or the context-menu key opens the list at once.
- **Reload**, which is **Stop** while the page loads;
- **Not secure**, in the warning colour, while the page is one you went on to past a certificate error
  ([Certificate errors](#certificate-errors));
- the address field, with the site icon at its left;
- in a [/dev/null](#devnull-mode) tab, the /dev/null indicator;
- the menu button (☰): **Reader Mode** and **Remove Distractions** while the page reads as an article
  ([Page tools](#page-tools)); **Add Bookmark**, or **Remove Bookmark** while the page is bookmarked
  ([Bookmarks](#bookmarks)); **Manage Bookmarks** and **History**, which load the bookmarks and [history](#history)
  pages; the Reset items of the page's [site permissions](#site-permissions) and, while the page is muted, **Unmute
  Current Page** ([Audio](#audio)); and **Settings**, which opens Settings › Tools › Browser.

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
4. a single word's **Navigate to “https://…”**;
5. up to ten of your [bookmarks](#bookmarks), newest first (Bookmark), then up to five of the sites you visited
   ([History](#history)), the most visited first ("Visited 3 times").

A bookmark or a visited site is listed when the text, ignoring case, begins its address after the scheme
(`github.com/x` for `https://github.com/x`), or after the scheme and a leading `www.`, or begins its title or one of
the title's words. Each shows its title, or its address when it has none. A row that loads an address the list already
holds is left out, so an address you bookmarked and visited lists once, and a stored `https://example.com/` does not
list again under the typed `example.com`.

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
| History | `"dev.jasper.browser.history"` | Cmd+Y | Ctrl+H |

These keys apply only in browser tabs, where they win over the same keys bound elsewhere, also inside a page's text
field: on macOS, Cmd+R in a browser tab reloads the page, and in a terminal it still opens Search Shell History. Rebind
them in Settings › Keymap, under Browser ([Keyboard shortcuts](keyboard-shortcuts.md)). **History** loads the
[history page](#history). On Linux and Windows, Ctrl+H opens it from the address field too, as Jasper's text fields
leave Ctrl+H to the keymap there ([Keyboard shortcuts](keyboard-shortcuts.md#bundled-plugins)).

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

## History

Every page loaded in a browser pane is recorded: its address and, once the page has one, its title, which follows the
page's title at most once a second. Only `http`, `https` and `file` addresses of at most 8,192 characters are recorded,
without any user name and password in them; Jasper's own `jasper-about:` pages, and `about:`, `data:` and `blob:`
pages, are not. A load that ends on an [error page](#error-pages) leaves no entry. Nothing expires. A
[/dev/null](#devnull-mode) tab's pages are recorded in its group's memory, and go with the group.

Jasper also counts the visits of each site: an address's host, with its port, and its path, without the scheme, the
query and the fragment, so `http://example.com/docs` and `https://example.com/docs?page=2` are one site and
`example.com:8080` another. A site shows its latest address and that address's title. The welcome page's Top Sites,
the address field's [suggestions](#search-and-suggestions) and the [command palette](#palette-scopes) list sites; the
suggestions and the palette see only the 10,000 sites visited most recently.

History, [bookmarks](#bookmarks) and [site permissions](#site-permissions) are kept in `browser.sqlite` in the plugin's
data folder ([Files](#files)), which Jasper opens the first time it needs it, such as when a browser page first shows,
and never as it starts. When the file cannot be used, because Jasper cannot read or write it, it is damaged, a later
Jasper wrote it, or another program holds it for more than 2 seconds, Jasper leaves it as it was, posts "Browser
history, bookmarks and site permissions are unavailable" with the reason, and keeps them in memory until it quits. When
SQLite itself cannot load, there are none for the run. Browsing works as usual either way.

**The history page**, `jasper-about:history`, opens from Web › History, ☰ › History, Show Full History and the welcome
page's Quick Actions, with the keyboard in its search field. "Browsing History" lists the entries newest first, under
Today, Yesterday and each earlier day, each with its time, its title ("Untitled" without one) and its address as a
link, 50 at a time: **Load More** shows the next. The search field ("Search your browsing history…") searches once you
pause typing for 300 ms, and lists the entries whose title or address holds any word of the text.

- × deletes an entry after "Delete this history entry?"; its site then counts one visit fewer and shows its newest
  address left, and a site with no entries left goes.
- **Clear All History** deletes every entry and every site after "This will delete all browsing history. This action
  cannot be undone. Continue?".
- A search, here and on the bookmarks page, reads the first 256 characters of the text and, of those, the first 8
  words, and ignores the case of ASCII letters only: `Ä` does not find `ä`.

## Bookmarks

**Add Bookmark**, in the menu button (☰), bookmarks the page's address, without any user name and password, with the
page's title, and opens **Edit Bookmark** below the button, unless the page began another load meanwhile. It is dimmed
for an address that history does not record ([History](#history)), such as a `jasper-about:` page. On an
[error page](#error-pages) it bookmarks the address that failed, without a title. While the page's address is
bookmarked, the item reads **Remove Bookmark**, which removes the bookmark and posts "Bookmark Removed".

**Edit Bookmark** shows the bookmark's **Title** and **URL**, which cannot be changed, and its **Tags**, which take the
keyboard:

- Enter adds the text typed as a tag, and so does a comma, a line break or a tab, typed or pasted, for the text before
  it. Backspace with nothing typed removes the last tag, and a tag's × removes it.
- Left with the caret at the start of the text selects the last tag; Left, Right, Home and End move between the tags;
  Backspace or Delete removes the selected tag; Right past the last tag, Escape and typing go back to the text.
- A tag has at most 64 characters and no control characters, and a bookmark at most 100 tags. A tag that differs from
  an earlier one only in the case of ASCII letters is dropped: `Work` and `work` are one tag, `Äpfel` and `äpfel` two.
  Tags keep the case you type them in, in every look.

**Save** keeps the tags and closes the panel, and Escape closes it, dropping the changes. However else it closes, by a
click outside, the page's next load, closing the page, quitting Jasper, turning the Browser off or **Delete Bookmark**,
the bookmark keeps the tags the panel shows.
**Delete Bookmark** asks "Are you sure you want to delete this bookmark?": **Delete** deletes the bookmark and posts
"Bookmark Deleted", and **Cancel** keeps it.

**The bookmarks page**, `jasper-about:bookmarks`, opens from ☰ › Manage Bookmarks and the welcome page's Quick Actions,
with the keyboard in its search field. "Bookmarks" lists them 50 at a time, with **Load More**, each with its title,
else its address, its address as a link, its tags and the day it was added.

- **Sort by:** Date Added, newest first (the default); Title, by the title or else the address; or URL. Title and URL
  ignore the case of ASCII letters.
- **Filter by tags:** a chip for each tag; with chips chosen, only the bookmarks that have every chosen tag show.
- The search field ("Search bookmarks...") lists, once you pause typing for 300 ms, the bookmarks whose title, address
  or one of whose tags holds the text.
- × deletes a bookmark after "Delete this bookmark?", and **Clear All** deletes every bookmark after "This will delete
  all bookmarks. This action cannot be undone. Continue?".

## Palette scopes

The Browser adds two scopes to the [command palette](command-palette.md#bundled-scopes), both searched in All:

- **Bookmarks** ("Search bookmarks") lists the bookmarks whose title, address or tags hold every word typed, those whose
  title begins with the text first, then the newest. Its chip with nothing typed lists the 200 newest.
  Bookmarks you open appear among the empty query's rows.
- **Browser History** ("Search visited sites") lists the sites whose title or address holds every word typed, the most
  visited first, then the most recent. Its chip with nothing typed lists the 50 visited most recently, under "Recent".
  When the text is an `https://` address with a host, **Open URL in Browser** comes first and opens it. Visited sites
  never appear among the empty query's rows.

**Open** (Enter) loads the row's address in the window's focused browser page, the page itself when the keyboard is in
its DevTools, or, with none, in a new browser tab at the end of the window's tab strip. While Chromium cannot start, it
posts "The browser could not start" again.

## Snippets in page fields

The [Snippets](history-and-snippets.md#using-snippets) scope's **Insert** and **Paste and run** put a snippet into a
browser page too, when the palette opened over the page, or the [Run window](command-palette.md#the-run-window)'s
current item is the page: the snippet goes into the field you are typing in, and Paste and run presses Return after it.

Jasper looks for the focused field from the page's top document, through frames of the same origin and open shadow
roots: an input that takes text (text, search, URL, telephone, email, number, or password, as a snippet may be a user
name or a code), a text area or an editable element, not disabled or read-only. The snippet replaces the field's
selection, as typing would. When the focus is where the search cannot follow it, in a frame of another origin or a
closed shadow root, Jasper types the snippet there with Chromium's DevTools (`Input.insertText`). Jasper tells a closed
shadow root by its host, which cannot hold the focus itself, so a closed root whose host has a tabindex or scrolls gets
no snippet. The clipboard is never used.

When the focus takes no text, Jasper does nothing: nothing is typed and Paste and run presses no Return, which would
press the page's control. That is so for a button, a link, a checkbox, a disabled or read-only field, the page itself
with nothing focused, and a number field or an editable element that refuses the snippet. Nor is anything typed or
pressed once the page has started loading another page.

- Return, and a snippet typed with DevTools, are trusted input, as your own keys are: the page gets user activation
  from them. The page sees the input events of every insertion.
- Jasper finds the field with a script that runs, through DevTools, in a JavaScript world of its own in the page, which
  the page's scripts cannot reach. The first time it runs a script in a document, for a snippet or for
  [Copy on select](#copy-on-select), it turns on DevTools' Runtime for a moment, which replays the page's console
  messages to Jasper, so a page can notice that DevTools attached.
- When the page does not answer within 5 seconds, or its answer fails, nothing is inserted.

## Copy on select

With **Copy on select** on (Settings › Terminal, `terminal.copy_on_select`), a selection you make in a browser page,
though not in a Markdown preview, is copied to the clipboard about a tenth of a second after you finish it. The setting
applies from the next selection.

- **What finishes a selection:** releasing the mouse button after a drag, a double or triple click, or a Shift+click;
  releasing the keys after Shift with an arrow, Home, End, Page Up or Page Down, or after Cmd+A (Ctrl+A on Linux and
  Windows); and the right-click menu's **Select All**. A plain click copies nothing, and nor does typing, Shift+Tab
  included, or a selection a page's script makes on its own.
- **Only a changed selection is copied:** a gesture that leaves the selection as it was, such as a drag of a
  scrollbar, a map or a slider, copies nothing over what you copied since. On a page that is slow to answer, a
  selection you finish while Jasper still reads an earlier one is copied once the page answers; one whose read Jasper
  gave up on, after 5 seconds, is copied only once you change the selection.
- **Never copied:** the text of a password field or of a field the page masks with CSS; the selection of an email or a
  number field; a selection longer than 1,048,576 characters; and a selection inside a frame of another origin, which
  **Copy** copies.
- Non-breaking spaces copy as spaces, so a command copied from a page runs in a shell.
- What is copied is what is selected when Jasper reads it: the page can change the selection between your gesture and
  the read.
- Jasper reads the selection in its own JavaScript world ([Snippets in page fields](#snippets-in-page-fields)), so the
  page's scripts cannot change what the read sees.

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
  of white space. Text in form fields and in shadow roots is not searched, except [reader mode](#page-tools)'s
  article, which is all Find searches while it shows.
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
- over a link, **Open Link in New Tab**, **Copy Link** and **Save Link As…**;
- over an image, **Open Image in New Tab**, **Save Image As…** and **Copy Image Address**;
- in a text field, **Cut**, **Copy** and **Paste**, Cut and Copy dimmed without a selection; elsewhere, with a
  selection, **Copy** and **Search with** *engine*;
- **Select All**, always;
- the pane items of a terminal's right-click menu: **Split ▸**, **Move Pane to Tab ▸** and **Close Pane**;
- **Save Page As…**, **Print…**, and **Copy Page Title**, which is dimmed while the page has no title;
- **View Source**, **Remove Element** and **Inspect Element** ([View Source](#view-source), [Page tools](#page-tools),
  [Inspect Element](#inspect-element)).

**Open Link in New Tab** and **Open Image in New Tab** open the address in a new browser tab right after this one. As
the page could open it itself, they open an `http`, `https` or `about:blank` address, or a `file:` or `jasper-about:`
address from a page of the same kind, and are dimmed for any other. **Copy Link** copies the link as the page wrote
it. **Search with** *engine* searches for the selection, each run of white space as one space and at most 1,000
characters of it, with the Default Search Engine, in a new tab right after this one; it names the engine, a Custom
engine by its search URL's host, and does not show for a selection of only white space. **Print…** prints the page.
**Save Link As…** and **Save Image As…** download the address after asking where ([Downloads](#downloads)); they are
dimmed for any address but an `http` or `https` one the page could open itself and a `data:` one. **Save Page As…** and
**Remove Element** ([Page tools](#page-tools)) are dimmed on Jasper's own `jasper-about:` pages and on a challenge
provider's, such as Cloudflare Turnstile's.

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

## Page tools

Once a page's document has loaded, Jasper checks in its top frame whether the page reads as an article, with Mozilla's
Readability.js, which the Browser bundles. When it finds more than 10 characters of article text, ☰ offers **Reader
Mode** and **Remove Distractions** at its top.

- The check runs again a second after the page last changed its address without loading, as a single-page app does
  between its views, unless only the fragment changed; until then ☰ offers neither. A page that is not showing, such
  as one in a tab behind another, is checked when it shows.
- Jasper's own `jasper-about:` pages, a challenge provider's page (Cloudflare Turnstile, hCaptcha, reCAPTCHA, Arkose
  Labs) and a page of more than 50,000 elements offer neither. On a page that enforces Trusted Types, Readability may
  find no article when it needs a second pass over a page whose first found too little text, or the page has an image
  inside `noscript`.

**Reader Mode** lays the article over the page in the workspace theme's colours: its title, or "Article", then its
text, images and links, without the page's scripts, frames, forms and styles. The article scrolls by itself and has
the keyboard; behind it the page does not scroll, Tab and clicks do not reach it, and its own shortcuts do not act. A
link loads in the page, and a link to a heading of the article scrolls the article there. [Find](#find-in-page)
searches the article. **Exit Reader Mode** in ☰, Escape, or the page's next load ends it, and gives the page its
scrolling and its focus back.

**Remove Distractions** makes the pointer a crosshair over the page and outlines in red what it is over: the element,
or the outermost fixed or sticky element holding it, such as a banner or a cookie notice. A click hides that, and with
it every other fixed element covering at least 90% of the visible page, such as the dimmed backdrop behind a dialog;
when what it hides covers 90% of it, a page that had locked its scrolling scrolls again. Only a single click of the
main button hides: a double click hides once, and Enter on a link hides nothing. While it runs, clicks reach neither
the page's handlers nor its links, the pointer over a frame is over the element holding the frame, and a right-click
still opens Jasper's menu. **Exit Distraction Removal** in ☰, Escape, or the page's next load ends it; what it hid
stays hidden until the page loads again. Starting reader mode ends distraction removal, and the other way round.

**Remove Element**, in the page's [right-click menu](#the-right-click-menu), hides at once what is under the pointer:
the element, or the outermost fixed or sticky element holding it, whatever the page's zoom; when that covers 90% of the
visible page, a page that had locked its scrolling scrolls again. It works on any web page, whether or not ☰ offers the
tools; over reader mode's article it hides nothing.

**Save Page As…**, in the right-click menu, saves the page as it is now in a folder: `index.html`, and in `resources/`
the images, style sheets, fonts, scripts and media it uses, so that it opens offline. Jasper's
[save chooser](workspace.md#the-file-chooser) asks where, starting at a folder in the [Downloads folder](#downloads)
named after the page's host, such as `example.com`, or `example.com (1)` and on when that name is taken (`page` for a
page without a host); Cancel saves nothing. Once saved, the folder shows in Finder (Explorer on Windows, the file
manager on Linux).

- **The copy:** Jasper reads a copy of the page's document, so the page does not change. Each image (those of a `srcset`
  too), video, audio, script, style sheet and icon, and what the page's styles and its style sheets' `url()` and
  `@import` name, is saved, and the copy points to it; every other link, frame and form address is made absolute, so
  links work and frames load from the web. The page's `Content-Security-Policy` meta elements go, so that they do not
  block the saved files. Scripts are kept and run when the saved page opens, from its `file:` address, where they can
  reach the network. What a shadow root holds is not saved. The saved page has neither reader mode's overlay nor
  distraction removal's outline.
- **Fetching:** Jasper fetches each resource again itself ([Jasper's own requests](#jaspers-own-requests)), without
  your cookies, so what a site shows only to you may differ or fail. Only a successful answer (200) is kept, within 15
  seconds a resource, its name lookups, redirects (at most 5) and body included: at most 20 MiB a resource and 2 MiB a
  style sheet, 500 resources and 200 MiB in all, and 5 minutes for the save's fetches. A resource that fails or passes
  a bound keeps its web address in the saved page, and the save goes on; so do those not yet fetched when the HTTP
  proxy changes.
- **Private networks:** for a page on the web, Jasper fetches nothing from this computer, a link-local or private
  network address, such as `192.168.1.1`, `10.0.0.1` or `100.64.0.1`, or an unspecified one: neither from a host
  written as one, such as `localhost`, nor, while the HTTP proxy is off, from a name that resolves to one. Such a
  resource keeps its web address. A page whose own address is written as such a host, such as `http://192.168.1.10/`,
  saves from any host; a page reached by a name counts as on the web. A name whose answer changes between Jasper's
  check and its request (DNS rebinding) can still reach such an address.
- **The files:** each file gets the [downloaded-file mark](#downloads), a /dev/null page's naming no address; when a
  mark cannot be written, the file is still saved, and Jasper warns once, in the Downloads group, "“FOLDER” has
  files without a downloaded-file mark". A style sheet or script whose name does not end in `.css` or `.js` gets it
  added, and any other resource keeps its extension only when it is a web resource's, such as an image's, a font's, a
  media file's or a data file's; otherwise it gets `.bin` added. Saving into a folder that exists replaces its files of
  the same names; a symbolic link at `resources` or `index.html` there fails the save.
- **Failures:** "Save Failed" says "Could not save the page: …" with the reason, such as a folder that cannot be
  written, "the page navigated away" when the page went to another document first, "the page did not answer" after 30
  seconds, or "the page is too large to save" for a copy of more than 64 MiB. Once the page's tab has closed, the
  failure is a notification in the Downloads group instead. Quitting, or turning the Browser off, cancels a save under
  way: Jasper writes no more of it and deletes its unfinished files.

Reader mode, distraction removal, Remove Element and Save Page As… run as scripts in the page's own JavaScript world,
as [Find](#find-in-page) does, so the page's scripts can see and undo what they do.

## Links in a page

| Gesture | macOS | Linux and Windows | Opens the link |
|---|---|---|---|
| Click | click | click | in the same page |
| New tab | Cmd-click | Ctrl-click | in a new tab right after this one |
| Split | Cmd+Shift-click | Ctrl+Shift-click | in a new pane beside this one: to the right when the page is wider than tall, otherwise below |
| Middle click | middle click | middle click | in a new tab right after this one |

A Shift-click loads the link in the same page, and so does an Alt-click (Option-click on macOS), which Chromium would
make a download. A link or page that downloads a file saves it in the [Downloads folder](#downloads).

Links that open a new tab or pane, and popups, open only at `http`, `https`, `file`, `about` and `jasper-about`
addresses. A `mailto:` or `tel:` link opens nothing, however it is clicked. A `jasper-about:` address loads only as a
page's whole document: in a frame, or as an image, a script or a style sheet, it loads nothing. A `file:` link on one of
Jasper's own pages, such as a row of the [history page](#history), loads on a click, and a click that would open it in
a new tab or a split opens nothing.

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

## Downloads

A file a page downloads goes, without asking, into the Downloads folder: the system's own, unless the **Downloads
folder** setting names another ([Settings](#settings)). The system's is `~/Downloads` on macOS; on Windows the
Downloads known folder, else `%USERPROFILE%\Downloads`; on Linux `XDG_DOWNLOAD_DIR` from `user-dirs.dirs` (in
`$XDG_CONFIG_HOME`, `~/.config` by default), else `~/Downloads`. Jasper makes the folder when it is missing, the first
time a browser page starts and after each change of the setting; while it is a file, or cannot be made, each download
fails with the reason. **Save Link As…** and **Save Image As…** ([The right-click menu](#the-right-click-menu)) ask
where instead, in Jasper's [save chooser](workspace.md#the-file-chooser), starting in the Downloads folder; Cancel
downloads nothing.

- **Names:** a file takes the name its server or page suggests, after its last `/` or `\`, with each control or format
  character (bidi controls and zero-width characters among them), line or paragraph separator and each of `<>:"|?*`
  made `_`, its leading and trailing dots and spaces trimmed, so that `.bashrc` saves as `bashrc`, a `_` before a
  Windows device name such as `CON`, and a name over 200 UTF-8 bytes cut, its extension kept; a name that leaves
  nothing is `download`. A name already taken in the folder,
  or by another download under way, is numbered before its extension, `report (1).pdf`, `report (2).pdf` and on, and
  when 1,000 are taken the download fails. On Windows a long name is shortened, its extension kept, so that the path
  stays under `MAX_PATH`.
- **The mark:** each finished file gets the system's downloaded-file mark, which the system checks before the file is
  opened or run: on macOS the quarantine attribute (`com.apple.quarantine`) that Gatekeeper reads; on Windows the
  `Zone.Identifier` stream (the mark of the web), `ZoneId=3` with the page's address as `ReferrerUrl`, when it is a web
  page, and the file's as `HostUrl`, without user names and passwords. Linux has no such mark. A mark the file already
  has is kept. Jasper writes the stream itself. When the mark cannot be written, as on a Windows volume without streams
  (FAT32 or exFAT: "This volume cannot keep a downloaded-file mark"), Jasper warns "“NAME” has no downloaded-file
  mark", and on macOS and Windows the download has no Open: open it from the file manager only if you trust it.
- **The Downloads menu** joins the menu bar with the first download. It lists the 20 newest downloads, and any older one
  still running, oldest first, each a submenu titled with its name and its progress, such as "report.pdf — 42%", or
  what has arrived while its size is unknown, such as "1.2 MB"; then Finished, Failed or Stopped. A running download's
  submenu has **Stop**; a finished one's **Open**, with the application the system opens it with, and **Show in
  Finder**, which shows it selected in its folder (**Show in Explorer** on Windows; **Show in Folder** on Linux, which
  opens its folder); one that no longer runs, **Remove from List**. **Clear All** removes every download that no
  longer runs. While the menu is open, a title changes in place, and a change to its entries waits until it closes.
  The list lasts until Jasper quits.
- **Notifications,** in the Downloads group ([Notifications](notifications.md#groups)): "Download finished for “NAME”."
  and "Download failed for “NAME”.", when a download fails or Jasper refuses it, with the reason when Jasper has one; a
  download you stop posts none.
- **Downloads without a click:** as in Chrome, a download that does not come within 5 seconds of a click or key press
  in the page goes once; each later one is refused, and the first refusal posts "This site tried to download multiple
  files" with the page's host. A document that starts loading within 5 seconds of a click or key press in the page,
  such as the one a clicked link opens, and each page you open from Jasper, such as from the address field, a bookmark
  or History, allow one again; a document the page loads by itself, through a meta refresh, `location.reload()` or a
  change of `location`, does not. A download within 5 seconds of a click or key press, and Save Link As… and Save
  Image As…, always go.
- **Closing a tab** while one of its page's downloads runs lets the download finish: the page goes blank at once, and
  its browser closes once the download ends. A tab a page opened, through a `target=_blank` link or `window.open`, whose
  first page becomes a download closes, as in Chrome, and the download goes on.
- **Quitting,** or turning the Browser off, stops the running downloads, and once Chromium has closed, Jasper deletes
  the partial files (`.crdownload`) they left in their folders.
- **On macOS,** in the packaged Jasper.app, macOS asks whether Jasper may use the Downloads folder, or a Downloads
  folder setting inside Documents or Desktop, when Jasper first uses it
  ([macOS Downloads, Desktop and Documents folders](packaging.md#macos-downloads-desktop-and-documents-folders)); while
  it is denied, downloads there fail. System Settings › Privacy & Security › Files and Folders changes the choice.

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

## Logins

With the [Credential Vault](credential-vault.md) plugin on, a page's login fields get buttons that fill them from the
vault, and Jasper's dialog for a site's HTTP sign-in can sign in with a login from the vault and save the one you type.
Without that plugin the fields have no buttons, Settings has no Manage Saved Passwords, and the sign-in dialog shows
without Password Manager and Remember this password, so pages can still sign in.

In a [/dev/null](#devnull-mode) tab the field buttons fill as elsewhere, but the sign-in dialog has neither Password
Manager nor Remember this password, and no sign-in is made without it.

### Field buttons

When you focus an enabled, editable field of a page, Jasper shows a button inside its right edge, titled "Open Password
Manager": 🔑 in a password field, and 🎫 in a field whose `autocomplete` attribute includes `username`. The button
follows the field as the page scrolls or resizes, and goes when the field loses the focus.

- **Choosing a login:** the button opens the vault's picker under the field, or above it when there is not room enough
  below, and never over it, unlocking the vault first if needed, with the page's host, less a leading `www.`, in its
  search ([Other plugins and grants](credential-vault.md#other-plugins-and-grants)). On a short screen the picker
  shortens, its logins scrolling, and with too little room above and below it does not open. No login is highlighted
  until you move to one with Down or Up, so Enter after typing chooses nothing; a click on a login chooses it. From 🔑
  the chosen login fills the password; from 🎫 it fills the user name, then the first password field after it, in its
  form when it has one, which takes the focus and the password. Only a login that signs in with a password alone
  fills: an SSH key, and a login with a key ("Account key" or "Key and password"), fill nothing. The page hears each
  fill as an `input` and a `change` event.
- **The picker closes,** filling nothing, at a click outside it, which reaches the page unless it was on the field; when
  the page scrolls, you zoom it, or the window or the page's pane is resized; when the page loads or goes to another
  address, one within the page included; and when its tab closes. A login fills only while the page still shows the
  site that opened the picker.
- **Only in a page's top frame,** on `http` and `https` pages: a login form inside a frame, such as an embedded
  sign-in, has no button, and nor do a field inside a web component's shadow root, a challenge provider's page
  ([Page tools](#page-tools)) and a document a sandbox gave an opaque origin.
- **What a page can do:** the buttons and their script live in the page's own JavaScript world, so the page's scripts
  can see, move or remove them, and can ask for the picker themselves. Jasper decides: the picker opens one at a time,
  never for a key, only after a mouse click in the page with no key pressed since, and only at a place no taller than
  200 CSS pixels that holds the click. An ask for a place that does not hold the click, or for a taller one, spends
  the click, so each click tells a page at most whether it lay inside a place the page names; an ask from a page the
  tab has left for another site is refused. The page can read what a login fills, as it can anything typed into its
  fields.

### The sign-in dialog

When a site, or the [HTTP proxy](#the-http-proxy), asks for a user name and password for one of a page's requests (an
HTTP sign-in, such as Basic, Digest or NTLM), Jasper asks over the page in a dialog titled "Sign In": "The website
“intranet.example” requires a user name and password for “Corp”.", without the "for" part when the site names no
realm, then **User name** and **Password**, **Cancel** and **OK**. A proxy's reads "The proxy “proxy.example:8118”
requires a user name and password for “Corp”.". **OK**, or Enter in either field, signs in; **Cancel**, Escape and
closing the dialog do not, and the request gets the server's refusal, which a page shows.

- **The realm is the site's own text,** so Jasper cleans it before showing it: control and format characters, such as
  bidi controls and zero-width spaces, and line and paragraph separators become spaces, runs of white space one space,
  and a realm of more than 100 characters shows its first 99 and "…". The host shows as Chromium gives it, an
  international name in its punycode form (`xn--…`).
- **Password Manager** shows with the Credential Vault plugin for a site's Basic sign-in outside /dev/null mode, and
  opens the vault's picker under the button, with the host, less a leading `www.`, in its search and no login
  highlighted, as a field button's does: a login chosen there signs in, and closing the picker keeps the dialog.
  **Remember this password**, a switch that starts off, shows as well only for the tab's own sign-in
  ([Frames and the page's requests](#frames-and-the-pages-requests)), and OK with it on saves the login
  ([Remembered sign-ins](#remembered-sign-ins)). A Digest, NTLM or other sign-in, a proxy's, one in /dev/null mode and
  one for a host whose name has an underscore get the dialog without either.
- **When it shows:** as an alert does ([Page dialogs](#page-dialogs)), once the page is its window's current item, and
  after the page's permission prompt is answered. A page has one sign-in dialog at a time; its other sign-ins wait, in
  order, and while 8 wait the next one is refused at once. When the page starts loading another page, its open sign-in
  dialog closes and its waiting sign-ins are refused, and closing its tab or quitting Jasper refuses them all.
- **One answer, one realm:** the user name and password you give, or choose, also answer the page's waiting sign-ins
  for the same server, port, realm and scheme, so a page of many images behind one realm asks once.
- **A flood is refused:** sign-in dialogs count among the page's [dialogs](#page-dialogs), so when they open one
  straight after another, the third and every later one is refused until the page loads again. The sign-in of a page you
  go to from Jasper, such as from the address field, a bookmark, history, Back or Forward, still shows, until you cancel
  one of its dialogs or the address it signs in to fails to load; Reload and a link in the page get no such exception.
- **A sign-in lasts until Jasper quits,** or a /dev/null tab's until its group's last tab closes: Chromium keeps it in
  memory, and [Clear Website Data](#clearing-data) does not end it.
- **Jasper's own prompts dim the window:** the sign-in dialog and the vault's Unlock Vault prompt dim the whole window,
  so a password prompt drawn inside the page, with the window around it undimmed, is the page's own.

### Frames and the page's requests

Chromium names neither the frame nor the request a sign-in is for, only the address that asked, so Jasper tells them
apart by that address alone. A sign-in whose address is of another site than the tab's, by scheme, host and port,
counts as a frame's; one of the tab's site for another address than the one the tab is loading, after its redirects,
or else showing, counts as one of the page's requests, such as an image or a script's `fetch`. Either offers Password
Manager without Remember this password, and neither is made without the dialog. A request the page makes for the
tab's address itself, or for its site's root (`/`), counts as the tab's own, and when Chromium names only the site
that asked, so does every sign-in of the tab's site.

### Remembered sign-ins

OK with **Remember this password** on signs in, and once the navigation it signed in to arrives as a success (2xx)
from the same site, after any redirect, Jasper saves the user name and password in the vault, unlocking it first if
needed, as a login named after the site and the realm, such as `https://intranet.example (Corp)`: the scheme, the host,
the port when it is not the scheme's usual one, and the cleaned realm in parentheses when there is one. Any other
response, one to a later navigation, another sign-in of the page, a failed load, the page starting to load another
page or its tab closing before that response saves nothing, and so does cancelling the unlock. When the vault cannot
save it, Jasper posts "Could not save the password for *host*" with the reason.

- **The Browser's own login:** the saved login comes with a saved grant for the Browser, which Vault Plugin Grants
  lists ([Other plugins and grants](credential-vault.md#other-plugins-and-grants)). Saving again under that name
  replaces the user name and password of the Browser's own login of the name, the one whose only grant is the
  Browser's; a login of that name that another plugin may read, or that the Browser has no grant for, is left alone
  and a new one added beside it. A login chosen with Password Manager, and the other sign-ins one answer serves, are not
  saved.
- **Signing in without the dialog:** while the vault is unlocked, the tab's own Basic sign-in, outside /dev/null mode,
  is made with no dialog when the Browser has a saved grant for a password login of exactly its name, the newest of
  them, and you began its navigation: from Jasper in that tab, such as from the address field, a bookmark, history,
  Back or Forward, when Chromium makes its request within 10 seconds, until you cancel one of its dialogs; or from the
  page, within 5 seconds of a click or key press of yours in it, as a link you click or a form you submit does, its
  redirects included. Any other, such as one a page's script makes on its own or the page a new tab opens with, shows
  the dialog, Password Manager included. Jasper does this once for that name in the tab, until a page of that site, in
  answer to that sign-in, begins to load there; until then the site's next sign-in for the name shows the dialog, so a
  remembered password the site refuses, and asks for again, is not sent again. When Stop or another navigation
  interrupts such a sign-in, the dialog shows for that name in that tab until the tab closes. While the vault is locked
  the dialog shows, and its Password Manager unlocks the vault.

### Manage Saved Passwords

With the Credential Vault plugin, Settings › Tools › Browser › Privacy & Data has **Manage Saved Passwords** after
Manage Site Permissions, which a Settings search for "passwords" finds. Its **Manage Passwords** closes Settings, asking
first whether to discard any unsaved changes, then opens the Credential Vault manager over the window, creating or
unlocking the vault first as **Open Vault...** does ([The manager](credential-vault.md#the-manager)).

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
`jasper-about:` address that fails; a file Chromium downloads goes to [Downloads](#downloads).

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
  certificate for the host shows the error page again. A choice made in a [/dev/null](#devnull-mode) tab holds only for
  its group's pages, until the group's last tab closes, and one made elsewhere for every page outside /dev/null mode.
- While a page is at an `https` address of such a host, the toolbar shows **Not secure** before the address field, in
  place of the site icon.
- A revoked certificate (`ERR_CERT_REVOKED`), `ERR_CERT_INVALID` and `ERR_CERT_CONTAINS_ERRORS` show no Details and
  no way past.

## /dev/null mode

With **Use /dev/null mode** on ([Settings](#settings), `devnull_mode`), each browser tab, window or pane that opens
afresh, from New Browser Tab, New Browser Window, the Browser split items, a palette row that opens a tab or a link
Jasper hands the Browser, opens in /dev/null mode, in a browsing group of its own. The tabs and panes its page opens,
through its links, popups, right-click menu or DevTools, join its group. A group keeps its cookies, cache, local storage
and other site data, its [history](#history), its [site permissions](#site-permissions) and [audio](#audio) choices and
the [certificates](#certificate-errors) you went on past in memory only, shared by its tabs and apart from every other
group and from your other pages, and loses them when its last tab closes, or when Jasper quits. Turning the setting on
or off changes no open tab.

- The toolbar shows the /dev/null indicator between the address field and ☰. A click on it shows "/dev/null Mode":
  "Your browsing activity is not being saved. No history, bookmarks, or other data will be stored. This mode is set in
  Settings › Tools › Browser › Privacy & Data.", which Escape, a click outside or another click on the indicator closes.
- ☰ has no Add Bookmark, Manage Bookmarks or History. Web › History, the history page, the welcome page's Top Sites and
  the address field's suggestions show the group's own visits, and the command palette's [scopes](#palette-scopes)
  never do.
- A /dev/null tab's pages show no notifications ([Page notifications](#page-notifications)).
  [Ad blocking](#ad-blocking) and the [HTTP proxy](#the-http-proxy) apply to them as to any page, and Settings'
  [clearing](#clearing-data) leaves them alone.
- A /dev/null tab's login fields get the Credential Vault's buttons, and its sign-in dialog neither offers the vault
  nor signs in without asking ([Logins](#logins)).
- A /dev/null tab's [downloads](#downloads) go to the Downloads folder too, and the files stay. Its group's finished
  downloads leave the Downloads menu when the group's last tab closes, and a running one once it ends. On Windows their
  mark names no address: `ZoneId=3`, `HostUrl=about:internet` and no `ReferrerUrl`.
- The saved workspace keeps no address of a /dev/null tab, and "Web Browser" as its title. After a relaunch the tab
  comes back at the start page, in /dev/null mode, in a group of its own ([Restore](#restore)).

## Site permissions

A page can ask for the camera, the microphone, both, or to show notifications
([Page notifications](#page-notifications)). Jasper asks you over the page, once the page is its window's current item:
"Allow Camera", "Allow Microphone", "Allow Camera and Microphone" or "Allow Notifications", such as "The website
https://example.com wants to use your camera.", with **Block** and **Allow**.

- **Allow** and **Block** are remembered for the site, Allowed or Blocked, and answer its later requests without asking.
  Escape and closing the prompt choose neither and remember nothing; the page's same request is then refused, without
  asking, until the page next loads.
- Allow and Block count only once the system's double-click interval (half a second where it names none) has passed
  since the prompt showed or since another of the page's dialogs closed, so that a page cannot time your click onto
  Allow. Enter presses neither.
- A page shows one prompt at a time, and its `alert()` and `confirm()` wait while it does. Prompts count among the
  page's [dialogs](#page-dialogs): the third to open straight after another, and every later one, is refused without
  showing, until the page next loads. Leaving or closing the page takes its prompt away, refused.
- A permission is kept for the site of the page's address, its scheme, host and port: a frame inside the page asks in
  the page's site's name, and gets what that site was allowed. Every `file:` page is one site, `file://`.
- A page's request to share the screen or a window is refused without asking, and so is a request for your location.
- In the packaged Jasper.app on macOS, the first page you allow the camera or the microphone also brings macOS's own
  question for Jasper ([macOS camera and microphone](packaging.md#macos-camera-and-microphone)).

Permissions are kept in `browser.sqlite` ([Files](#files)), and a [/dev/null](#devnull-mode) group's in memory. When one
cannot be saved, Jasper posts "Site permissions could not be saved" with the reason, and keeps it until it quits.

**The permissions page**, `jasper-about:permissions` ("Site Permissions"), shows the permissions of its tab's browsing
group. It opens from the welcome page's Quick Actions, and from **Manage Permissions** in Settings' Privacy & Data
([Settings](#settings)), which opens it in a new tab outside /dev/null mode, whatever the setting. It lists
them by site: Camera, Microphone, Camera and Microphone, Notifications and Audio Playback ([Audio](#audio)), each
Allowed or Blocked. The search field ("Search by website or permission type…"), All Types and All Statuses narrow the
list. A site's **Clear** forgets its permissions, a permission's × forgets it after "Revoke Camera permission for
https://example.com?", and **Clear All Permissions** forgets every one; the pages that show those sites then load
again, so a page that has the camera open loses it.

**☰'s Reset items:** while the page's site has permissions kept, ☰ lists a Reset item for each, such as "Reset Camera
Permission (Blocked)", which forgets it and loads again the pages of the group that show the site. An item acts on the
site the menu showed it for, even when the page has gone elsewhere since.

## Page notifications

A page's notifications show as Jasper's, in the **Page notifications** group ([Notifications](notifications.md#groups)).
The page asks with `Notification.requestPermission()`, which shows the "Allow Notifications" prompt
([Site permissions](#site-permissions)); while its site is allowed, `new Notification(title, {body})` posts a
notification in the page's window, and a click on it shows the page's tab.

- **The title** is the page's, on one line and cut to 200 characters, or the site's host when it shows nothing.
- **The text** begins with the host, as in "example.com — There", so that a page cannot post one worded as Jasper's
  own; a host longer than 60 characters is shortened from the left, keeping its last labels. The body follows, with at
  most two blank lines in a row, and the whole text is cut to 4,000 characters. Icons and the other options are
  ignored, and the page's `click` handler never runs.
- **At most 10 a minute:** a page's later notifications are refused, and its script gets `error`. The same text posted
  again within a minute, as by two tabs of one site, adds to the first notification.
- **`close()`** takes the notification out of Jasper's panel, until the page loads another document or closes; from
  then on the notifications it posted stay.
- **Only a secure page** may ask: an `https` page, or an `http` one on this computer (`localhost` and the names under
  it, `127.0.0.0/8` and `[::1]`). Any other `http` page, a `file:` page, a page in a [/dev/null](#devnull-mode) tab and
  a document a sandbox gave an opaque origin are denied, without a prompt.
- **Only the page's top frame** gets Jasper's notifications. A frame inside the page, a page script that ran before
  Jasper's and a service worker get Chromium's own, which Jasper does not show. `navigator.permissions.query()` reports
  Chromium's answer, which can differ from `Notification.permission`, and a page can tell that it runs in Jasper.

## Audio

The first time a page whose site has no choice kept plays sound, Jasper mutes every frame of the page and asks, in the
bar between the toolbar and the page, "Audio was muted. Allow playback by https://example.com?", with

- **Allow Once**, which plays the page's sound until the page next loads;
- **Allow Always**, which plays it and keeps Allowed for the site;
- **Deny Once**, which keeps it muted until the page next loads;
- **Deny Always**, which keeps it muted and keeps Blocked for the site.

A site kept as Allowed plays without the bar, and one kept as Blocked is muted without it. While a page is muted, ☰
offers **Unmute Current Page**, which plays its sound until the page next loads and keeps nothing. The bar goes as the
page loads another document.

- The choice is the site of the page's address ([Site permissions](#site-permissions)), so a player embedded in the
  page is muted with it. It shows on the permissions page and in ☰'s Reset items as Audio Playback.
- `http` and `file:` pages are asked as `https` ones are. A page without a site, such as `about:blank` or a `data:`
  page, is muted without the bar, and Unmute Current Page plays it.
- Muting is the page's to undo: Jasper's script mutes the page's media elements and suspends its audio contexts in the
  page's own JavaScript world, so the page's scripts can unmute themselves, and an audio context, or an autoplay in a
  shadow root, that the page set up before the script ran is not muted. A page can tell that it runs in Jasper.

## Clearing data

Settings › Tools › Browser › Privacy & Data clears what your pages keep. Each asks first, in a sheet over the active
window with **Cancel** and **OK**, and its button is busy while it runs. Neither touches a [/dev/null](#devnull-mode)
group or the [filter list](#ad-blocking).

- **Clear Cookies** (Clear All Cookies) asks "This will remove all cookies from all websites. Continue?", then deletes
  every cookie, which signs you out of every site.
- **Clear All Data** (Clear Website Data) asks "This will remove all browsing data including history, bookmarks, site
  permissions, cookies, cache and local storage. The next time Jasper starts, it also signs you out of any site you sign
  in to before then. Continue?". It deletes the history, the bookmarks and the site permissions, loads again the pages
  of a site whose permissions went, and compacts `browser.sqlite`, so that nothing deleted stays in the file. Chromium
  then deletes every cookie, the cache, and the local storage and other site data of the 1,000 sites visited most
  recently, of the open pages' sites and of the sites the cookies were for. The next time Chromium starts, in a later
  run, Jasper deletes Chromium's whole profile, `data/chromium`, before Chromium opens it, with what was left and every
  sign-in since, and empties `chromium.log`, whose lines can name sites. "All website data has been cleared
  successfully!" says when it is done.
- Until Chromium next starts, open pages keep their Back and Forward lists, and Chromium still shows links you visited
  as visited.
- A site you signed in to with an [HTTP sign-in](#the-sign-in-dialog) stays signed in until Jasper quits, as Chromium
  keeps the sign-in in memory.

Before Chromium has started in the run, Clear Cookies leaves the cookies for Chromium to delete as it starts, before any
page, and Clear All Data deletes the profile at once. While Chromium starts, each waits for it, and when Chromium could
not start, its next start does the work. A clearing that does not end within 60 seconds, or fails, posts "Cookies could
not be cleared" or "Website data could not be cleared" with the reason; history, bookmarks and site permissions are
deleted even when Chromium's part fails, and a site Chromium could not clear waits for the profile's deletion. When
`browser.sqlite` could not be used, so that history ran in memory, Clear All Data fails with "The browser's database
file could not be used, so it was not cleared; Jasper deletes it the next time it starts", and the next run deletes the
file before it opens it. Jasper moves the profile aside before deleting it; a profile it cannot move, as when Windows
has one of its files open, stays as it is for that run, and a later start deletes it.

## Ad blocking

With **Enable Ad Blocking** on (`ad_blocking`, off by default; the onboarding's Let’s Get You Set Up page has it as the
**Ad Blocker** switch), Jasper blocks the requests and hides the parts of pages that its filter list names, in every
browser page, /dev/null ones included. Jasper's own `jasper-about:` pages and DevTools are left alone.

- **Blocked:** a page's requests for frames, images, style sheets, scripts, fonts, media, `fetch()` and
  `XMLHttpRequest`, WebSockets, pings and its other sub-resources, at `http`, `https`, `ws` and `wss` addresses, fail.
  The page's own load, the address you open, is never blocked.
- **Hidden:** the elements a rule's selectors select, by a style sheet of Jasper's that the page adopts, so that a
  page's content security policy does not stop it. A frame inside a page hides only what the list hides on every page.
  The page's scripts can take the sheet away, and elements in shadow roots are not hidden.
- Turning ad blocking off stops the blocking at once; what was hidden shows again as each page next loads.

**The filter list** is a content-blocker list in WebKit's JSON format, from **Filter List URL** (`filter_list_url`),
by default EasyList's, `https://easylist-downloads.adblockplus.org/easylist_content_blocker.json`.

- **Its address** is an `https` address, or an `http` one on this computer (`localhost`, `127.0.0.0/8` or `[::1]`), as
  TLS is all that vouches for the list. A download follows at most 5 redirects, each only to such an address and never
  from `https` to `http`; it connects within 30 seconds, takes at most 120 seconds in all and reads at most 32 MiB.
- **When:** Jasper reads its copy of the list, and checks for an update, only once the first browser page shows or you
  turn ad blocking on, and from then on every hour while ad blocking is on. It downloads the list when 24 hours have
  passed since the last update, when it has none, or when its copy came from another address, so a new Filter List URL
  downloads at once, or as soon as a download under way ends. **Update Now** downloads it at once, with ad blocking on
  or off.
- A list that cannot be downloaded or read leaves the one Jasper had in place. The copy is kept in `data/adblock/`
  ([Files](#files)).

**Filter Statistics**, after the two settings, says how many rules the list has and when it was updated ("*n* rules ·
Updated *date and time*"), "Updating…" while it downloads, why its last update failed ("Could not update the filter
list: …"), or, before Jasper has read the list in the run, "Loads when ad blocking is on and a browser page opens".
From the seventh day without an update it adds "Ad block rules haven't been updated for *n* days".

**The rules Jasper applies** are those whose trigger has a `url-filter` and no keys but `url-filter`,
`url-filter-is-case-sensitive`, `resource-type`, `load-type` and one of `if-domain` and `unless-domain`, and whose
action is `block`, `css-display-none` with its `selector`, or `ignore-previous-rules`. Every other rule, such as one
with `if-top-url` or `load-context`, or a `block-cookies` or `make-https` action, is skipped, and so is one past these
bounds:

- A `url-filter` is WebKit's regular expression: characters, `.`, a character class, a group, `*`, `+` and `?`, `^`
  first, `$` last and `\` before punctuation; anything else, such as `|`, `{2}` or `\d`, is refused. So that no match
  can take long, a filter has at most 2,048 characters, 256 repeats and 4 of them `*` or `+`, and groups at most 8
  deep, and has neither a group under `*` or `+` with a repeat inside it (`(a+)+`) nor a repeated group that can match
  nothing (`(x?)?`). A match reads at most an address's first 8 KiB.
- `first-party` and `third-party` compare the request's site with the page's: a host's last two labels, or its last
  three under a two-letter country code with a common second level, such as `co.uk`, `com.au` or `gob.mx`, so
  `www.cnn.com` and `edition.cnn.com` are one site. Jasper has no public suffix list: all of `github.io` is one site
  too.
- A list has at most 32 MiB, 8 levels of nesting and 2,000,000 values: its objects, arrays, strings, numbers, `true`,
  `false` and `null`, not counting its members' names. Jasper keeps its first 250,000 rules, at most 1,024 hosts in a
  rule's `if-domain` or `unless-domain` and 500,000 in all, and at most 5,000 selectors in a rule, each of at most 1,024
  characters, with no `{`, `}`, `;`, `@` or `<`. A page hides at most 2 MiB of selectors.
- Each request, and each page's hiding, has a bound on the matching work its rules take; past it, the request goes
  through and the page hides less.

A list can only block requests and hide elements: its rules add no style of their own and load nothing. Hiding still
tells something, as an element it hides no longer loads its lazy images, so a list whose selectors test a field, such
as `:has(input[value^="a"])`, could let a server learn from the requests that do not come what the field holds, as
with WebKit's content blockers.

## The HTTP proxy

With **Enable HTTP Proxy** on (`http_proxy`), every browser page's requests, /dev/null ones' included, go through the
HTTP proxy at **Proxy Server** and **Proxy Port** (`proxy_host`, by default `127.0.0.1`, and `proxy_port`, by default
8118), which Chromium asks with `CONNECT` for `https`. So do [Jasper's own requests](#jaspers-own-requests), the
favicons, the search suggestions, View Source's copies and the filter list. Requests to this computer and to link-local
addresses go directly, as Chromium sends them whatever the proxy: `localhost` and the names under it, `127.0.0.0/8`,
`[::1]`, `169.254.0.0/16` and `[fe80::]/10`. A change applies at once: Chromium closes its open connections, and
Jasper's own requests take the new route from the next one.

- **Proxy Server** takes a host name, an IPv4 address, or an IPv6 address without brackets, such as `::1`, and nothing
  else: no scheme, port, user name or space ("Enter a host name or IP address."). Write an international host name in
  its punycode form (`xn--…`); a name with `_`, or one that ends in a dot, is refused.
- **When Chromium refuses the proxy,** Jasper posts "The proxy could not be set" with Chromium's reason, and the pages
  that are open keep the route they had. A /dev/null tab opened then stays blank rather than load without the proxy.
- **Turned off,** Chromium uses the system's proxy settings, and Jasper's own requests go directly, unless Jasper runs
  with `-Djava.net.useSystemProxies=true`, a line you can add to its
  [custom VM options](configuration.md#custom-vm-options), with which they follow the system's settings too.
- **Around the proxy,** as in Chrome: Chromium looks up some host names ahead of time with the system's resolver (DNS
  prediction), and WebRTC's STUN requests go out directly, so a page can learn your address. A proxy that asks for
  credentials (407) gets the [sign-in dialog](#the-sign-in-dialog) for a page's requests, and fails Jasper's own.

## Links from terminals and Jasper

These links come to the Browser:

- an `http` or `https` link you Cmd-click (Ctrl-click on Linux and Windows) in a terminal, a URL written in the output
  or an OSC 8 link; a Cmd+Shift-click (Ctrl+Shift-click) asks for a split
  ([Terminal](terminal.md#links-and-file-paths));
- a local web file you Cmd-click in a terminal: HTML, XML, SVG, CSS, JavaScript, PDF, PNG, JPEG, GIF, WebP or HEIC;
- Jasper's own web links, such as Settings' Help button and the homepages in About › Licenses… and Settings › Plugins,
  and the links plugins open, such as the Editor's Markdown preview links;
- the addresses from outside Jasper: those of `jasper URL...` ([Command line](command-line.md#the-jasper-command)), a
  launch's web addresses ([launch options](command-line.md#launching-jasper-with-options)), `open -a Jasper URL` on
  macOS, and the links other apps open while Jasper is your
  [default browser](#making-jasper-your-default-browser).

`ftp` and `mailto` links in a terminal go to the system's handler, as without the Browser.

Each link goes where the first of these says:

1. An address from outside Jasper opens in a new browser tab, without asking, right after the shown tab of a window:
   for `jasper URL...`, the window of the pane `jasper` ran in, else the most recently active window, opening one when
   none is open; for those of the launch that starts Jasper, its first window; for one the system or a second launch
   hands over, the most recently active window, which comes to the front, Jasper's windows being restored first when
   none is open. Until Jasper's plugins have started and Jasper has checked whether it is the default browser (at most
   3 seconds), these addresses wait, in order; at most 32 wait, and a later one opens nothing.
2. A local web file follows **Open Local Web Files**: **Always Ask** (the default) asks; **Built-in Browser** opens
   its `file:` address in a new browser tab right after the current one; **Default Editor** opens it as a Cmd-click on
   any other file does.
3. While Jasper is the system's [default web browser](#making-jasper-your-default-browser), every other link opens in
   Jasper's browser without asking, whatever **Open Links From Terminal** says: in a new browser tab right after the
   current one, or, for a split click, in a browser pane split from the clicked pane. Jasper checks this when it starts
   and again when you come back to one of its windows, at most every 5 seconds.
4. A split click, and Open URL in Split Right and Down, open the link in a browser pane split from the clicked pane,
   without asking. When **Open Links From Terminal** is **Default Browser**, a split click goes to the system browser
   instead, and the two items are not in the menu.
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

## Making Jasper your default browser

Jasper can be the system's default web browser, the app that opens the `http` and `https` links of every other app.
While it is, the links other apps open come to Jasper's browser as addresses from outside, without asking (rule 1
above), and the links you click in a terminal and Jasper's own links open there without asking too (rule 3).

Jasper never hands a web link to the system browser while it is the default, since the system would hand it back: a
web link that no browser of Jasper's opens then opens nothing ([When the Browser is off](#when-the-browser-is-off)). An
address the system hands Jasper within 10 seconds of Jasper handing it to the system, as when Jasper is the default but
cannot tell, is never handed to the system again: Jasper's browser opens it, or it opens nothing.

### On macOS

Install `Jasper.app`, whose `Info.plist` names the `http` and `https` schemes
([macOS web links](packaging.md#macos-web-links)), and choose Jasper in System Settings › Desktop & Dock › Default web
browser; choose another browser there to stop. Jasper asks Launch Services which app opens `https` links; a Jasper run
from a source checkout cannot ask, and never counts itself the default.

### On Windows

The [setup wizard](packaging.md#windows-setup-wizard) registers Jasper, for your account only, as a web browser for
`http` and `https` links, so Settings › Apps › Default apps lists it; choose Jasper there for `HTTP` and `HTTPS`.
Windows then starts Jasper as `Jasper.exe --url "<link>"` ([`--url`](command-line.md#launching-jasper-with-options)).
A link with a double quote in it reaches Jasper without the quote, or, when the quote splits it into several
arguments, opens nothing. An upgrade keeps the registration and uninstalling removes it. The portable ZIP and the
[user installation from source](packaging.md#user-installation-without-a-compiler) register nothing, so Windows does
not offer them. Jasper counts itself the default while your choice for `https` links is Jasper's (`JasperURL`).

### On Linux

The menu entry of the `.deb` and `.rpm` [packages](packaging.md#linux-packages) takes `http` and `https` links
(`x-scheme-handler/http` and `x-scheme-handler/https`), so desktops that list web browsers by the links they take, such
as GNOME, offer Jasper in their default applications settings. Choose it there, or run:

```sh
xdg-settings set default-web-browser jasper-workstation-Jasper.desktop
```

The desktop then starts Jasper with the link after `--url`; a file opened with Jasper from the file manager, or
dropped on its launcher, comes the same way, as a `file:` URL, and opens as that file. Jasper counts itself the
default while `xdg-settings get default-web-browser` names `jasper-workstation-Jasper.desktop`; without `xdg-settings`
(xdg-utils), it never does.

The packages make no app the default: installing leaves the system's default applications (each `defaults.list`) as
they were. Where neither you nor the system has chosen a default web browser, apps that ask GIO, as GNOME's do, open a
link with the first app that takes it, which can be Jasper. Removing the package leaves your own choice
(`~/.config/mimeapps.list`) as it is: one that named Jasper then names an app that is gone.

### When the Browser is off

While Jasper is the default and no plugin provides a web browser, as when the Browser plugin is off or does not run, a
web link opens nothing: one from another app, a terminal, Jasper's own links or `jasper URL...`. Jasper posts
"Web links can’t open in Jasper", "The Browser plugin is off. Turn it on to open web links in Jasper, or choose another
default browser in your system settings.", with:

- **Turn On Browser**, which turns the Browser on from the next launch, as Settings › Plugins does, and posts "Restart
  Jasper to turn on the Browser" with **Restart Now**, which restarts Jasper as it was started and opens the link, the
  newest when the notice stands for several, in the Browser. When Jasper cannot work out how it was started, "Jasper
  could not restart" says to quit and reopen it; when the Browser cannot be turned on, "Could not turn on the Browser"
  says why.
- **Copy Link**, which copies the link's address.

`ftp` and `mailto` links still go to the system's handler. While the Browser runs but Chromium could not start, a web
link opens nothing either; "The browser could not start" has said why. A web link whose host Jasper's browser cannot
take, such as `http://my_service:8080/` (a `_` in a host), opens nothing too, while Jasper is the default: Jasper posts
"Jasper’s browser can’t open this address" with the address, without any user name and password in it, and **Copy
Link**.

## Markdown preview

The Editor's [Markdown preview](editor.md#markdown-preview) draws with the Browser while its **Markdown preview** setting
is Chromium, the default. Each preview is a page of its own, apart from your browser pages, in GitHub's layout and the
theme's colours, its diagrams drawn by the Mermaid the Browser bundles: it loads nothing from the network, runs none of
the file's scripts and goes to no other address. Its links and images are the Editor's: a click on a link, or Enter on
one, goes to the Editor, which follows it as [Links in the preview](editor.md#links-in-the-preview) says, and its images
are those the Editor loads, web images only after **Load external images**. It has no right-click menu and opens no
popups or page dialogs. While the Browser plugin is off, and once Chromium could not start or has stopped, the Editor's
previews are Basic, the Editor's own, which shows Mermaid diagrams as code.

## Restore

When Jasper restores a window ([Saving and restoring the windows](workspace.md#saving-and-restoring-the-windows)), each
browser page comes back in its tab and pane, at the address it last showed, at its own [zoom](#zoom) and for the OSC 8
target it was opened for.
It loads the first time it shows, so restoring a page starts no Chromium; until then its tab is named after the
address's host. Chromium starts inside Jasper the first time a page or a Markdown preview shows and runs until Jasper
quits. If Jasper ends while Chromium starts or runs, for example in a Chromium crash or a force-quit, the pages the next
launch restores wait instead: each says "Jasper closed while this page was open." and loads when you click **Load
Page**, press Enter in its address field, click **Reload** or open a link in it.

- An address too long for the saved state (4,096 characters, counted with the target and the zoom) is saved without
  its query and fragment, and when it is still too long, not at all.
- A `data:` or `blob:` page saves no address. A `jasper-about:` page saves its address.
- A page that shows an [error page](#error-pages) saves the address that failed, and one that shows an error page's
  [source](#view-source) saves the source page of that address.
- An address is saved without any user name and password in it.
- A [/dev/null](#devnull-mode) tab saves no address, and "Web Browser" as its title. It comes back in /dev/null mode, in
  a group of its own.
- A page that saved no address, or one the address field would not load, comes back at the start page.

A page's back and forward history, form contents and scroll position do not come back.

## Jasper's own requests

Jasper fetches these itself, apart from Chromium, without your pages' cookies or any credentials:

- a page's favicon, once the page has loaded: for an `http` or `https` page, the icon its first `<link rel="icon">`
  or `<link rel="mask-icon">` names, else `/favicon.ico` at its server, when that is an `http` or `https` address. A
  PNG, GIF, JPEG or ICO favicon of at most 64 KiB that arrives within 5 seconds shows; any other, such as an SVG or a
  `data:` icon, leaves the globe. A page keeps the first favicon it names;
- the search engine's suggestions for what you type ([Search and suggestions](#search-and-suggestions)): at most 64
  KiB, within 3 seconds;
- View Source's copy of an `http` or `https` page ([View Source](#view-source));
- the [filter list](#ad-blocking);
- Save Page As…'s copies of a page's resources, within its bounds ([Page tools](#page-tools)).

While the [HTTP proxy](#the-http-proxy) is on, they go through it, except those to this computer and to link-local
addresses. While it is off, they go directly rather than through a proxy your system's settings name, unless Jasper
runs with `-Djava.net.useSystemProxies=true`. On a network that reaches the web only through a proxy, they then fail:
pages show the globe, the address field lists no engine suggestions, View Source shows the HTML Chromium holds, and the
filter list does not update.

## Settings

The settings live in `plugins/dev.jasper.browser/dev.jasper.browser.toml` in
[Jasper home](getting-started.md#jasper-home) and on Settings › Tools › Browser, in the sections General, Link Opening,
Search Engine, Privacy & Data, Ad Blocking and Proxy. Privacy & Data ends with Clear All Cookies, Clear Website Data,
Manage Site Permissions ([Clearing data](#clearing-data)) and, with the Credential Vault plugin, Manage Saved Passwords
([Logins](#manage-saved-passwords)); Ad Blocking ends with Filter Statistics and Update Now
([Ad blocking](#ad-blocking)). Jasper creates the file, with every setting commented out, when it is missing; changes
in the file or on the page apply without a restart. A bad value is reported and its default used
([Plugin settings](plugins.md#plugin-settings)).

| Key | Default | Values | On the page | Applies to |
|---|---|---|---|---|
| `start_page` | `"jasper-about:welcome"` | What a new browser tab, window or pane loads, read as the address field reads typed text; not empty and not a `chrome:` address ("Enter a web address.") | Start page | Browser pages opened afterwards |
| `page_zoom` | `100` | The [zoom](#zoom) pages open at and Reset Font Size returns to: a whole percent from 25 to 500 | Page zoom | Browser pages opened afterwards without a zoom of their own; Reset Font Size |
| `downloads_folder` | `""` | Where [downloads](#downloads) go, and where Save Page As… starts: empty for the system's Downloads folder; `~` or `~/folder` under your home folder (`~\folder` too on Windows); or an absolute folder, on Windows a drive's (`C:\…`) or a share's (`\\server\share\…`) ("Enter an absolute folder path, or leave it empty.") | Downloads folder | The next download, and the next Save Page As… |
| `open_links_from_terminal` | `"ask"` | `ask` (Always Ask), `builtin` (Built-in Browser), `default` (Default Browser) | Open Links From Terminal | The next link; the right-click items at once |
| `open_local_web_files` | `"ask"` | `ask` (Always Ask), `builtin` (Built-in Browser), `editor` (Default Editor) | Open Local Web Files | The next local web file |
| `search_engine` | `"google"` | `google` (Google), `duckduckgo` (DuckDuckGo), `bing` (Bing), `kagi` (Kagi), `custom` (Custom): what the address field and the right-click menu's Search with search with, and where the field's suggestions come from ([Search and suggestions](#search-and-suggestions)) | Default Search Engine | The next keystroke in the address field, search and right-click menu |
| `custom_search_url` | `""` | With `custom`: the address of a search's results, an `http://` or `https://` address without a user name or password and with `%s` in its path, query or fragment where the terms go, such as `"https://search.example.com/?q=%s"`; empty searches with Google ("The URL must start with https:// or http:// and contain %s") | Custom Search URL | The next search |
| `custom_suggestions_url` | `""` | With `custom`: an address of the same form that answers OpenSearch JSON, with `%s` where the typed text goes; empty shows no engine suggestions ("The URL must start with https:// or http:// and contain %s") | Custom Search Suggestions URL | The next keystroke in the address field |
| `devnull_mode` | `false` | `true` opens browser tabs, windows and panes in [/dev/null mode](#devnull-mode) | Use /dev/null mode (no history, cookies, or other data saved) | Browser tabs, windows and panes opened afterwards |
| `ad_blocking` | `false` | `true` blocks and hides what the filter list names ([Ad blocking](#ad-blocking)) | Enable Ad Blocking | Every page's requests, once the list is read, and what each page hides from its next load |
| `filter_list_url` | `"https://easylist-downloads.adblockplus.org/easylist_content_blocker.json"` | A WebKit content-blocker list: an `https` address, or an `http` one on this computer ("Enter an https address, or an http address on this computer.") | Filter List URL | The list's next check, at once |
| `http_proxy` | `false` | `true` sends the pages' requests and Jasper's own through the proxy below ([The HTTP proxy](#the-http-proxy)) | Enable HTTP Proxy | Every page, at once |
| `proxy_host` | `"127.0.0.1"` | A host name or an IP address, an IPv6 address without brackets ("Enter a host name or IP address.") | Proxy Server | Every page, at once, while the proxy is on |
| `proxy_port` | `8118` | 1 to 65535 | Proxy Port | Every page, at once, while the proxy is on |

## Files

Under [Jasper home](getting-started.md#jasper-home):

| Path | Holds |
|---|---|
| `plugins/dev.jasper.browser/dev.jasper.browser.toml` | The [settings](#settings) |
| `plugins/dev.jasper.browser/data/chromium/` | Chromium's profile: cookies, the cache and other site data |
| `plugins/dev.jasper.browser/data/chromium.log` | Chromium's warnings and errors |
| `plugins/dev.jasper.browser/data/browser.sqlite` | [History](#history), [bookmarks](#bookmarks) and [site permissions](#site-permissions), in SQLite |
| `plugins/dev.jasper.browser/data/onboarding-finished` | That the [onboarding](#opening-pages) is finished |
| `plugins/dev.jasper.browser/data/adblock/` | The [filter list](#ad-blocking)'s copy: `rules.json`, the rules Jasper applies, and `state.json`, its address, when it was downloaded and how many rules it kept and skipped |
| `plugins/dev.jasper.browser/data/chromium-clear` | What a [clearing](#clearing-data) left for the next start |
| `plugins/dev.jasper.browser/data/chromium-cleared-*` | A profile a clearing moved aside, until it is deleted |

SQLite's native library loads from the copy in `jasper-sqlite` (on Linux `jasper-sqlite-<user>`) in the system's
temporary folder that [Remote](remote.md#files) describes, which the Browser writes there when Remote has not.

On macOS and Linux, before Jasper first opens `browser.sqlite`, starts Chromium or reads the filter list, it takes every
permission of other users away from the `data` folder, and from `browser.sqlite` and its `-wal` and `-shm` files, so no
other user of the computer can read your history, your bookmarks or Chromium's cookies.
