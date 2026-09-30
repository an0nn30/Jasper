# IntelliJ themes: install from disk and from the Marketplace

Status: approved design, 2026-09-30. Adds a top-level **Themes** group to Settings with an **IntelliJ Themes** page
that manages the theme plugins in the `themes` folder and browses the JetBrains Marketplace for more, plus a
**Get More Themes…** entry in the Appearance page's theme dropdown. Built on the existing theme catalog
(`appearance.ThemeCatalog`, `ThemeArchive`) and the vendored IntelliJ parent themes (2026-09-29).

## Outcome

From Settings › Appearance's theme dropdown, **Get More Themes…** opens Settings › Themes › IntelliJ Themes on its
Marketplace tab. There the user searches the Marketplace's Theme-tagged plugins, reads a plugin's description and
installs it; its themes appear in the dropdown at once and a banner offers to use one. The Installed tab lists every
theme archive in the `themes` folder, offers Use, Uninstall and (for Marketplace installs with a newer release)
Update, and installs `.jar`/`.zip` files from disk. A setting turns the Marketplace off for users who want Jasper to
stay offline.

## Decisions (user, 2026-09-30)

1. **Placement:** a top-level Themes group; IntelliJ Themes is its first child. A later Alacritty Themes page (terminal
   colors only) becomes its sibling.
2. **Scope:** search, a list (icon, name, vendor, downloads, rating), a details pane (description, version, Install,
   View on Marketplace), Uninstall, and update availability on the Installed tab. No screenshots in this version.
3. **Network:** Jasper contacts the Marketplace only while the user looks: searching on the Marketplace tab and one
   update check each time the Installed tab opens. Nothing at startup or in the background.
   `themes.marketplace.enabled` (default true) turns both off and hides the Marketplace tab.
4. **Code lives in the app** (new package `dev.jasper.app.themestore`), not in a plugin and not on the Plugins page.

## Alternatives considered

- **A bundled plugin** isolating the network code: needs SDK surface for installing themes and contributing pages to
  the Themes group, used by one plugin only (against "no interface without two real implementations"). Rejected.
- **A Themes filter on the Plugins page**, as in IntelliJ: mixes Jasper plugins (code, capabilities, restart) with
  theme archives (data, applied live) and leaves no home for Alacritty themes. Rejected.
- **Screenshots in the details pane:** valuable for themes but needs image fetching and a gallery; a follow-up.
- **A daily background update check** with a notification: rejected; nothing runs unasked.

## Design

### Settings tree and pages

- `CorePages` adds a top-level `GroupPage` **Themes** (id `themes`) between Appearance & Behavior and Terminal, and a
  child page **IntelliJ Themes** (id `themes.intellij`, help anchor `intellij-themes`).
- The page has a header row (the `themes.marketplace.enabled` checkbox, labelled "Browse the JetBrains Marketplace")
  and two tabs:
  - **Installed** — one card per archive in the `themes` folder: plugin name, the theme names it provides, version,
    and source ("Marketplace" or "From disk"). Selecting a card shows its details: **Use** (per provided theme, sets
    `ui.theme.name` through the draft and applies it), **Uninstall**, and **Update to X** when a newer Marketplace
    release exists. The tab header has **Install from Disk…** and **Reveal Folder**. The catalog's problems for a file
    show on its card.
  - **Marketplace** — a search field (debounced, 300 ms) over a card list (icon, name, vendor, downloads, rating,
    a "Freemium" badge where it applies, "Installed" when its xmlId is installed) with **Load more** paging.
    Selecting a card shows its details pane: icon, name, vendor, version, the description, **Install** (or
    **Update**/**Installed**), and **View on Marketplace** (opens the browser). Hidden when the setting is off.
- Like the Plugins page, the page's actions apply at once: `modified()` is always false and Apply has nothing to do
  for it. The checkbox is the exception: it is a normal draft-backed setting and follows Apply/Cancel.
- After an install the catalog rescans; the new themes appear in the dropdown immediately and the page shows a banner
  **Use "&lt;first theme&gt;"**.
- The card and details look follows `pluginmanager` (PluginCard, PluginDetails). `themestore` must not import
  `pluginmanager`; any painting helper both need moves to `settingsui`.

### Get More Themes… in the dropdown

- `Editors.theme` appends a separator and a final item: **Get More Themes…** when the Marketplace is enabled,
  **Manage Themes…** otherwise. Choosing it restores the combo's previous value, never writes the sentinel to the
  draft, and calls `PageContext.navigate` with `themes.intellij` and the tab (Marketplace or Installed).
- The page takes a tab hint on navigation: `navigate` accepts `themes.intellij#marketplace` /
  `themes.intellij#installed`, the dialog selecting the page by the part before `#` and passing the fragment to the
  page (a default no-op `SettingsPage.reveal(String)`).
- The combo is editable; typing a name never matches the sentinel (it is not a theme name, and the item is rendered
  as a link-styled action).

### Marketplace client

`MarketplaceClient`, over a JDK `HttpClient` (HTTP/2, 10 s connect timeout, 30 s request timeout), base URL
`https://plugins.jetbrains.com` (injectable for tests). Every call runs off the EDT and returns a `CompletableFuture`.

- `search(String query, int offset)` → `GET /api/searchPlugins?tags=Theme&max=20&offset=N&search=Q` → `Page(List<Listing>, int total)`.
  `Listing(int id, String xmlId, String name, String preview, long downloads, double rating, String pricingModel,
  String iconPath, String vendor, String link)`. Listings with `pricingModel` `PAID` are dropped; `FREEMIUM` shows a
  badge.
- `details(int id)` → `GET /api/plugins/{id}` → `Details(id, xmlId, name, String descriptionHtml, vendor, link)`.
- `latest(int id)` → `GET /api/plugins/{id}/updates?size=1` → `Release(long updateId, String version, String file)`.
- `icon(String path)` → the SVG bytes, rendered with FlatLaf-extras' `FlatSVGIcon`; cached in memory per session.
- `download(Release, Path target)` → `GET /files/{file}`, following redirects only to `https` hosts equal to or ending
  in `.jetbrains.com`; the body is streamed to `target` and aborted past 50 MB.
- JSON is parsed with `com.formdev.flatlaf.json.Json`, already used by `ThemeArchive`. Missing or mistyped fields
  make that listing (or call) fail, not the page.
- The description HTML is shown in a non-editable `JEditorPane` with images and scripts not loaded; links open in the
  browser through the dialog's existing browse hook.
- No request carries user data beyond the search text; no cookies, no identifiers.

### Installing, updating and uninstalling

`ThemeInstaller` owns writes to the `themes` folder (the folder `ThemeCatalog.beside(config)` scans).

- **Marketplace install/update:** download to `themes/.downloads/<file-name>.part` → `ThemeArchive.read` it → if it
  provides at least one theme, move it atomically to `themes/<xmlId>-<version>.zip`, record it in provenance, delete
  any other file recorded with the same xmlId, and rescan the catalog. If it provides none (for example a plugin whose
  theme needs IntelliJ code), delete the download and report "&lt;name&gt; has no themes Jasper can use." The
  `.downloads` folder is emptied on every page open.
- **Install from Disk…:** the dialog's `chooseFile` (filtered to `.jar`/`.zip`) → the same validation → copy into
  `themes/` under its own file name; an existing file of that name asks before replacing. Disk installs are not
  recorded in provenance.
- **Provenance:** `themes/.sources.toml`, one table per installed file:
  `["dev.x.theme-1.2.0.zip"] marketplace_id = 12275, xml_id = "com.vermouthx.idea", version = "2.3.1"`. It starts
  with a dot, so the catalog scan skips it. Entries whose file is gone are dropped on the next write.
- **Uninstall:** confirm ("Uninstall &lt;name&gt;? Its themes: …"), delete the file and its provenance entry, rescan.
  If the active theme came from it, the existing missing-theme fallback applies (the default theme, with the usual
  warning) and the draft's `ui.theme.name` is left for the user to change.
- **Update check:** when the Installed tab opens (and the Marketplace is enabled), `latest` for each
  Marketplace-sourced file, concurrently; a newer version (compared as IntelliJ-style dotted versions, falling back to
  inequality) shows **Update to X**. Failures only hide the button.
- All file work runs off the EDT; the page disables a card's buttons while its operation runs.

### Errors

- Offline, timeouts, HTTP errors and malformed responses show an inline message with **Retry** in the Marketplace tab
  (or the details pane for `details`/install). Nothing is modal except the uninstall and replace confirmations.
- A download that fails midway leaves no file in `themes/` (only the `.part`, deleted).

### Setting

`themes.marketplace.enabled` (bool, default true) in `CoreSettings`, applies live: turning it off hides the Marketplace
tab, stops update checks and relabels the dropdown entry.

### Packages and architecture

- New package `dev.jasper.app.themestore`; its `package-info` names allowed dependencies on `appearance`, `config`,
  `settings` and `settingsui`, and owns the lifetime of the client (one per application, closed at shutdown).
  `settingsui` does not depend on `themestore`: `CorePages` receives the page factory the way it receives the Plugins
  page. `verifyApplicationArchitecture` stays green.
- Units: `MarketplaceClient` (HTTP and JSON → records), `ThemeInstaller` (downloads, validation, disk installs,
  uninstall, provenance), `InstalledThemes` (joins a `ThemeCatalog.Snapshot` with provenance and known updates),
  `IntelliJThemesPage` with `InstalledTab` and `MarketplaceTab`.

## Testing

Headless and TDD, following the Plugins page tests.

- **Client** against a local `com.sun.net.httpserver.HttpServer` serving recorded Marketplace JSON: search parsing and
  paging, PAID filtering, details and latest, a redirect to a non-JetBrains host refused, a body over the size cap
  aborted, malformed JSON failing only its listing, HTTP errors.
- **Installer** with temp folders and the existing theme-archive fixtures: a valid archive lands and is recorded; an
  archive with no themes is refused and leaves nothing; an update replaces the older file of the same xmlId; disk
  install with and without a name conflict; uninstall removes file and provenance; a stale provenance entry is dropped.
- **Pages:** Marketplace tab hidden when the setting is off; the dropdown sentinel is never stored and navigates to the
  right tab; the Installed tab shows Update only for newer releases; banner after install.
- No test contacts the real Marketplace.

## Documentation

`docs/STATUS.md`, `docs/app-architecture.md` (the new package and Jasper's first network client), and the user docs'
theme section (the Themes page, the offline setting, what the Marketplace sees).

## GUI checks (for the user)

Get More Themes… from the dropdown; search "dracula", install, use the theme; an Islands-based theme; a theme needing
IntelliJ code (e.g. Material Theme UI) is refused cleanly; Install from Disk…; Uninstall of the active theme; the
offline setting; dark and light Jasper themes on the page.
