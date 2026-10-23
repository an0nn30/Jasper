# Jasper terminal tab names: a pattern setting and plugin pieces

Status: **Approved by the user on 2026-10-23** ("approved, write the plans"). Planned as two plans:
- **A:** the pattern, the setting and its live preview (sections 3 to 5);
- **B:** the plugin API and Remote's `{host}` (sections 6 and 7).

## 1. Intent

The user, 2026-10-23:

> I'd like to create a small scripting type thing in the settings box where I can type how I want tabs to appear.
> Kind of like the thing you do on linux on kde when you're setting the clock format [...] some way to override the
> tab names from settings

> it just says zsh, I want the folder name - and sometimes maybe I want only the last dir of hte path, maybe I want the
> whole path - maybe I want the last dir in the path + the process running, or a way to show the path + process + allow
> the program in the tab to append their own tab name (like how claude cli sometimes sets the tab name to the session
> name [...]

Later answers, all the user's:
- Pieces are written in braces, and everything between them is free text the user types: "just do {dir} {process}
  {title} you see, use spaces and we add those spaces or whatever freetext we want [...] could be | if that's what the
  user wants". Jasper adds no separator of its own.
- The default is `{dir} {process} {title}`. "if title isn't set, we don't show it - I don't want a blank space if the
  program isn't setting the title".
- `{title}` is any title the terminal receives, from the shell or the program, "the behavior today". `{process}` "is
  just the actual running process".
- Settings shows a live preview. No mockups were wanted.
- Plugins get an SDK API to read these values from a tab and to add their own pieces. The first one is Remote's
  `{host}`, the SSH host name. The Editor may add pieces some day; it is not part of this work.

**Prior art.** KDE's Konsole has a tab title format with letter codes (`%d` folder, `%D` full path, `%n` program, `%w`
title the program set, `%h` host). kitty has `tab_title_template` with `{title}`-style tokens, and iTerm2 lets scripts
provide pieces of a session's title. Jasper already fills `{context}`, `{session}` and `{directory}` in a look's
`[chrome].window_title` (`WindowTitle.format`), so this design uses braces the same way.

## 2. What happens today

`TerminalContent.title()` takes the first of these that is present:

1. the name given with Rename Tab (Cmd+I / Ctrl+Shift+I), saved in `workspace.toml`;
2. the title the shell or program set (OSC 0/1/2), which the pane clears when a command starts and when it finishes;
3. the running command that shell integration reports (OSC 133);
4. the foreground process, polled every 500 ms (Unix only; nothing for provided sessions);
5. the shell's label (the profile name or the program's file name, or a provided session's name).

`TabState.title`'s folder-name fallback is never reached, because step 5 always gives a name. The name shows in the
tab strip entry, its tooltip, `TabHandle.title()`, the Move Pane to Tab and Move Tab Into lists and the title of a
child window.

## 3. The pattern

A new setting, `terminal.tab_name`, holds the pattern. Default `"{dir} {process} {title}"`; 1 to 200 characters; it
applies live to every open Terminal tab, in every window, on macOS, Windows and Linux.

**Built-in pieces:**

| Piece | Value | Example |
|---|---|---|
| `{dir}` | The last name of the pane's directory: locally, the one the shell last reported, else where the pane started (`TerminalPane.directory()`); `~` for the home folder; the root itself at a root (`/`, `C:\`); on another host, the last name of the reported remote path, and nothing until one is reported. | `moray` |
| `{full}` | The whole directory, as the window title's `{directory}` writes it (`WindowTitle.directory`): locally with the home folder as `~`; on another host, the reported path. | `~/projects/moray` |
| `{process}` | The foreground process, without a login shell's leading `-`. Where Jasper cannot see it (Windows, a provided session such as an SSH tab, before the first poll) it is the shell's label from step 5 of section 2. It is never the command text shell integration reports. | `claude` |
| `{title}` | The title the shell or program set, exactly as step 2 of section 2 keeps it today, on one line. | `Fix tab names` |

**Rules:**
- **Free text.** Everything outside braces shows as typed. `{{` and `}}` type a literal brace.
- **Empty pieces take their separator with them.** The text between two pieces shows only when the pieces on both
  sides of it have a value. This is `WindowTitle.format`'s rule: an empty piece goes with the text before it, or with
  the text after it when it comes first. The two formatters share one implementation. With
  `{dir} | {process} | {title}` and no title: `moray | zsh`.
- **Text around the ends stays.** Text before the first piece and after the last always shows, so brackets around a
  piece that can be empty stay behind: `{dir} ({host})` in a local tab shows `moray)`. The user chose this
  (2026-10-23: "keep it simple, leave it as is"); there is no syntax for text that goes with a piece.
- **Values are one line.** Line breaks and control characters become spaces and each value is trimmed, as
  `WindowHeaderContext.clean` does.
- **The result is trimmed.** A result that is empty after trimming, such as `{title}` before any title is set, falls
  back to the shell's label. Fixed text the user typed counts: `Tab {title}` with no title shows `Tab`.
- **Rename Tab still wins.** A tab with a name the user gave shows that name. Clearing it brings back the pattern.
- **Unknown pieces show nothing.** A well-formed name that no built-in or running plugin provides is not an error when
  `config.toml` loads, so uninstalling a plugin never invalidates the setting. Settings explains it (section 5).
- **Malformed patterns are invalid.** An unclosed `{`, a lone `}`, or an empty or badly formed name (pieces are
  lowercase letters, digits and `_`, starting with a letter) is rejected like any other invalid setting value.

`TabState`'s folder-name fallback goes. `{dir}` replaces it (Jasper is an MVP; no compatibility path).

**Where the new name shows:** everywhere the tab name shows today (section 2). The main window's title does not
change: it comes from the look's `window_title` template and the header's title field (`WindowHeaderContext`), which
keep their own rules. Notification wording from `TerminalTitle` does not change.

## 4. Examples

| Pattern | Local tab, Claude running and titled | Local tab at the prompt, no title | SSH tab from Remote |
|---|---|---|---|
| `{dir} {process} {title}` (default) | `moray claude Fix tab names` | `moray zsh` | `prod db` (its saved name, as `{process}`), then the remote shell's title if it sets one |
| `{full}` | `~/projects/moray` | `~/projects/moray` | the remote path once reported, else the shell's label |
| `{dir} \| {process}` | `moray \| claude` | `moray \| zsh` | `prod db` |
| `{dir} {host}` | `moray` | `moray` | `db1.example.com`, or `app db1.example.com` once the remote shell reports `/srv/app` |

## 5. Settings

- **Where.** A **Tab name** row on the Terminal page (`CorePages`), with the setting's description and keywords
  (tab, title, name, format, pattern) so Search settings finds it. Keep a `docs/configuration.md` heading for it, since
  the Help button opens anchors there.
- **The field** is a single-line text field (`Editors.Line`, technical style).
- **The live preview.** Under the field, a supporting-text line reads `Preview: ` and the name the pattern gives,
  recomputed on every edit. It uses the values of the most recently focused Terminal tab, ignoring that tab's manual
  rename. With no Terminal tab open it uses sample values: `projects`, `~/projects`, the configured shell's label, and
  no title. Nothing in
  Settings has a preview today, so this is a custom `ValueEditor`: a vertical `Stack` of the `Line` field and
  `Nodes.Text` rows with `TextRole.SUPPORTING`, re-rendered by `node()` as `SchemaPage` already does on each edit.
- **The piece list.** Under the preview, the available pieces with a short description each: the four built-ins, then
  every piece a running plugin provides, labelled with its plugin's name.
- **Messages.** A malformed pattern shows the setting's invalid message, as other fields do. A well-formed name that
  nothing provides shows "Nothing provides {name} right now, so it shows nothing" under the field. It does not stop the
  value being saved, because the plugin may just be turned off.

## 6. Plugin API (SDK)

Additive, so a patch bump (0.21.0 to 0.21.1 at the time of writing; use the next patch of whatever is current), with
`@since` on every new member. Names below are a sketch the builder may refine. The shapes are decided.

**Reading.** A plugin with `terminal.observe` can ask a terminal tab for its piece values:

```java
// TabHandle
/** Every piece with a value for a terminal tab: the built-ins and plugin pieces, by name, without braces.
    Empty for a content tab and once the tab has closed. Needs terminal.observe. */
Map<String, String> namePieces();
```

`TabHandle.title()` keeps returning the name the strip shows, which is now the pattern's result.

**Adding a piece.** A plugin registers a piece once and pushes a value per terminal tab, like `StatusBar.add` and its
`StatusItem` handle:

```java
// Terminals
/** Adds a piece to Terminal tab names. Needs terminal.observe. */
TabNamePiece addTabNamePiece(TabNamePieceSpec spec);

/** name: lowercase letters, digits and '_', starting with a letter, at most 32 characters; description: one line for Settings. */
record TabNamePieceSpec(String name, String description) { }

interface TabNamePiece extends Subscription {
    String name();
    /** Sets this piece's value for a terminal tab; blank clears it. Ignored for a content tab and once the tab or the piece has closed. */
    void set(TabHandle tab, String value);
}
```

- **Names.** A plugin piece may not use a built-in name (`dir`, `full`, `process`, `title`): registration throws
  `IllegalArgumentException`. Two plugins may not share a name: a registration while another plugin holds the name
  throws `IllegalStateException`. Names are not namespaced, so users can type `{host}`.
- **Values.** Each value is cleaned as section 3 says and stays with its terminal session when the tab moves to
  another window or into another tab.
- **Lifetime.** Closing the handle removes the piece from every tab and from Settings' list. A plugin that stops has
  its pieces closed with its other contributions (as `HostedTerminals.closeAll()` closes environment contributors). A
  closed tab's values are dropped.
- **Capability.** No new capability. `terminal.observe` covers it, because a plugin needs to observe tabs to know what
  to put in their names. Remote already declares it.
- **Threading.** `set` may be called from any thread. The app applies it on the Event Dispatch Thread (unlike
  `StatusItem`'s setters, which are UI-thread only).
- **App side.** `HostedTerminals` implements both parts, gated through `CapabilityGate`. The registry of plugin
  pieces lives in the app and feeds both the formatter and Settings.
- **Testkit.** `FakeTerminals` implements both. `FakePluginHost` exposes the registered pieces and the values set per
  tab for plugin tests. `PluginContractTest` gains contracts for both, run by `FakeContractTest` and
  `AppContractTest` (the environment contract is the template).

## 7. Remote's `{host}`

- Remote registers `{host}` ("The SSH host this tab is connected to") when it starts.
- When it attaches a session (`RemotePlugin.attach`, which knows `pending.pane().tab()`), it sets `{host}` for that
  tab to the saved host's address, `RemoteHost.hostname()`, such as `db1.example.com`. For a jump route this is the
  destination host, not the jump host. Restored SSH tabs get it the same way when they reconnect.
- `{host}` stays while the tab shows that host, including after the connection drops and while it reconnects.
- Local tabs have no `{host}`. An `ssh` typed in a local shell does not fill it.
- Remote's `plugin.toml` SDK range starts at the new SDK version.

## 8. Goals, non-goals, anti-goals

**Goals:** the user types a pattern and every Terminal tab follows it at once; the preview shows the result before
leaving Settings; plugins can read the values and add pieces; Remote provides `{host}`.

**Not in this work:** Editor and other content tabs, which keep naming themselves; a pattern per profile or per host;
colours or styling inside a tab name; a grouping syntax for text that disappears with its piece; more built-ins such as `{user}` or the tab number; filling `{host}` from an `ssh`
typed in a local shell.

**Anti-goals (a failure even if it works):**
- a stray separator or trailing space when a piece is empty;
- removing or turning off a plugin making `config.toml` invalid or showing errors at start-up;
- a program's title stops showing for people who leave the default alone;
- tab names that flicker or cost noticeable time: formatting runs on every 500 ms foreground poll that changes and
  must stay cheap.

## 9. Documentation

- `docs/configuration.md`: the settings table row for `terminal.tab_name`, and a section on the pattern (pieces,
  rules, examples) under the Terminal prose, with a stable heading for the Help button.
- `docs/terminal.md` (`## Titles`) and `docs/workspace.md`: the new naming order.
- `docs/plugin-authoring.md` (`## Terminals`) and `docs/sdk-architecture.md` (`## Terminal API`): reading and adding
  pieces.
- `docs/remote.md` (`## SSH sessions`): `{host}`.
- `jasper-sdk/README.md`: the version. Run `./gradlew sdkReference` and commit `docs/sdk-reference.html`.
- `docs/STATUS.md`: the entry for this work.

## 10. Left to the builder

- Exact SDK type and method names, and their Javadoc.
- Where the shared formatter lives so that `WindowTitle` and the tab name both use it within the package rules
  (`verifyApplicationArchitecture`).
- How the app finds "the most recently focused Terminal tab" for the preview.
- The wording of the setting's description, the piece descriptions and the invalid message.
- Test layout, following the repo's headless conventions. The GUI check goes to the user.
