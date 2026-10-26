# jasper-sdk-testkit

A headless stand-in for Jasper's plugin runtime, for plugin unit tests. `FakePluginHost` starts a plugin as Jasper
would, hands it a working `PluginContext`, and lets a test script the workspace (windows, tabs, panes, sessions, the
clipboard, settings files) and inspect and drive everything the plugin contributed, with no window, shell or
application. The package is `dev.jasper.sdk.testing`; it depends only on [the SDK](../jasper-sdk/README.md), and its
version is the SDK's. Every type and method is in
[the SDK reference](../docs/sdk-reference.html#package-dev.jasper.sdk.testing).

## Depending on it

In this repository a plugin compiles against the SDK and tests against the testkit:

```kotlin
dependencies {
    compileOnly(project(":jasper-sdk"))
    testImplementation(project(":jasper-sdk"))
    testImplementation(project(":jasper-sdk-testkit"))
}
```

Outside it, put the release's `jasper-sdk-testkit-<version>.jar` beside `jasper-sdk-<version>.jar` on the test
classpath, as [the example external plugin](../docs/examples/external-plugin/README.md) does. `./gradlew sdkRelease`
builds both into `build/sdk-release/`.

## A first test

```java
@Test void theToolsCommandCopiesTheGreeting() {
    try (var host = new FakePluginHost()) {
        host.start(new PluginInfo("dev.example.hello", "Hello", "1.0.0", Set.of()), Set.of(), Set.of(), new HelloPlugin());
        assertThat(host.menu("TOOLS")).containsExactly("item:" + HelloPlugin.COPY);
        var window = host.addTerminalWindow();
        assertThat(host.invoke(HelloPlugin.COPY, window, null)).isTrue();
        assertThat(host.clipboard().copies()).containsExactly("copy|dev.example.hello|" + HelloPlugin.GREETING);
        assertThat(host.failures()).isEmpty();
    }
}
```

Give the `PluginInfo` the capabilities your `plugin.toml` declares: the fake throws `MissingCapabilityException` for a
gated call without one, as Jasper does.

## Threads and time

The thread that calls the host plays the UI thread. Nothing happens behind the test's back:

- Published events wait in a queue until `flush()`, which delivers them, including events the deliveries publish.
- Tasks given to `background()` wait until `runBackground()`, which runs them on the calling thread one after another
  and returns how many ran.
- Kit views need the real Swing event dispatch thread: `FakeKit` and `FakeKitView` throw `IllegalStateException`
  ("Kit requires EDT") elsewhere, so a test of a plugin that builds views runs it inside
  `SwingUtilities.invokeAndWait`. `Appearance`'s colour, palette and opacity calls and its `onChanged` and
  `onWorkspaceChanged` subscriptions accept the thread that made the host or the event dispatch thread,
  `onTerminalTypographyChanged` only the event dispatch thread, and its variants, presentation and terminal
  typography answer any thread.

## FakePluginHost

[`FakePluginHost`](../docs/sdk-reference.html#dev.jasper.sdk.testing.FakePluginHost) is the fake runtime.
`new FakePluginHost()` keeps plugins' data directories in a temporary folder that `close()` deletes;
`new FakePluginHost(Path)` uses a folder the caller owns. A plugin's `dataDirectory()` is `<root>/<id>/data`, as
Jasper's is `<home>/plugins/<id>/data`.

### Lifecycle

| Method | What it does |
| --- | --- |
| `start(info, requires, optional, plugin)` | Starts a plugin and returns its `FakePluginContext`. A plugin whose hard requirement is not active is not started; a `start` that throws is rolled back, its context closed and the failure recorded. Services published by the plugins in `requires` and `optional` reach it. |
| `active(id)` | Whether the plugin started and has not stopped |
| `stopAll()` | Stops every active plugin in reverse start order |
| `close()` | Stops every plugin, flushes, and deletes a temporary data folder |
| `failures()` | Every contained failure (throwing starts, stops, handlers, factories and background tasks) and every start skipped for an inactive hard requirement, as `<id> skipped: requires <id>` |
| `flush()`, `runBackground()` | Deliver queued events; run queued background tasks |
| `publishApp(topic, payload)` | Publishes as the application, for example `AppEvents.THEME_CHANGED` |
| `activityLog()`, `connectionLog()` | Activity and connection events delivered so far |

### Contributions

| Method | Returns or does |
| --- | --- |
| `actions()` | Registered actions as `id\|title\|enabled` |
| `actionSpec(id)` | The `ActionSpec` an action was registered with |
| `invoke(actionId, windowId, paneIdOrNull)` | Runs an action as a window would; false when it is disabled or unknown |
| `toolbar()` | The plugin toolbar section as `button:<id>`, `menu:<title>:<id>,<id>` and `view` |
| `toolbarViews(windowId)`, `closeToolbarViews(windowId)` | Builds a window's toolbar views, as the window does when it opens; closes them, as closing the window does |
| `menu(target)` | One menu's contributed entries (`FILE`, `EDIT`, `VIEW`, `TAB`, `TOOLS`, `SESSION`, `TRANSFER`, `context` or `top:<menuId>`) as `item:<id>`, `---` and `submenu:<title>` lines, children indented, sections separated by `===` |
| `status()`, `progress()` | Visible status items as `id\|side\|text\|tooltip\|actionId`; visible progress states by id |
| `rail()` | Rail buttons' action ids |
| `panels()` | Registered panels as `id\|title\|ANCHOR`, with `+split` for a panel that starts in the second half and `\|<action id>` for one that names its own toggle |
| `openPanel(id)`, `panelView(id)`, `panelHeader(id)`, `placePanel(id, placement)`, `closePanel(id)` | Builds a panel as the main window showing it would, at its placement; its mounted view; its published header; moves the panel as a drag or View Mode would, open or not: an open panel's `PanelHost.placement()` becomes the `PanelPlacement` and a different one reaches each `onPlacement` handler once (a throwing one is recorded in `failures()`), and a panel that is not open is built there, also after `closePanel`; discards it as the main window would |
| `windows()`, `windowView(pluginId, title)`, `windowContent(pluginId, title)` | Open dialogs and overlays as `id\|title\|shown` (a dialog's id is `dialog`, an overlay's `overlay`); the view or component mounted on one |
| `dialogPresentation(pluginId, title)`, `dialogSwatch(pluginId, title)`, `popoverViews(pluginId)` | How an open dialog is presented and the hue of its title square; shown popovers' views |
| `requestClose(windowId)` | Closes a dialog through its closing guards |
| `requestQuit()` | Asks the content tabs' close guards, then every quit guard, and closes nothing |
| `contentTabs()`, `contentPresentation(tab)`, `contentStatus(tab)`, `contentCommands(tab)`, `contentOwner(tab)`, `contentComponent(tab)` | Content tabs and what each shows, including the status items and commands it last published |
| `contentTab(pluginId, title)`, `contentView(pluginId, title)` | A plugin's open content tab, found by its title, and the view it shows |
| `restoreState(tab)`, `restoreTab(pluginId, windowId, title, state)`, `declinedRestores()` | The restore state a tab gives; runs a plugin's restorer as Jasper does when it restores its windows (the tab is placed in that window, not shown, and a session's tab waits, `NOT_STARTED`, until it is shown); the reasons restorers gave when no tab came back |

### Terminals and sessions

| Method | Returns or does |
| --- | --- |
| `addTerminalWindow()`, `addTerminalTab(windowId, title)`, `addTerminalPane(tabId, info)`, `addContentTab(windowId, title)` | Scripts the workspace (the first window added while no main window is open is the main window; a terminal tab has one pane); each announces itself on the terminal topics, delivered by `flush()` |
| `moveTab(tabId, windowId)`, `closeTab(tabId)`, `windowTabs(windowId)`, `holdPane(paneId)` | Moves a tab the test added to another window, or closes it, as the user would (the last tab of a child window takes the window); a window's tabs, each its items pane by pane; holds a local pane `NOT_STARTED` until its tab is shown, as Jasper holds a restored one |
| `activateTerminalWindow(id)`, `focusTerminalPane(id)`, `closeTerminalPane(id)` | The user turns to a window, focuses a pane or closes one (the last pane takes its tab, the last tab a child window) |
| `setPaneInfo(id, info)`, `setSelection(id, text)`, `setForegroundJob(id, name)` | Changes what a pane reports, without an event |
| `setTabNamePieces(tabId, builtIns)`, `tabNamePieces(tabId)`, `tabNamePieceSpecs()` | Scripts the built-in pieces (`dir`, `full`, `process`, `title`) a terminal tab reports through `TabHandle.namePieces()`; the values plugins' pieces hold for a tab; every plugin's piece |
| `commandStarted`, `commandFinished`, `titleChanged`, `cwdChanged`, `remoteCwdChanged`, `sessionExited`, `bell` | Publish terminal events for a pane |
| `terminalPanes()` | Every open pane, in creation order |
| `sent(paneId)` | What plugins sent to a pane, as `write:<text>` and `paste:<text>` |
| `openRequests()` | What plugins opened or raised: `tab\|<window>\|<directory or ->` and `split\|<pane>\|<RIGHT or DOWN>\|<directory or ->`, each followed by `\|<profile id>` and `\|run:<command>` when the request named them; `beside\|<content tab>\|<RIGHT or DOWN>` for a content tab's Terminal Right or Down; `front\|<window>`; for a provided session `session-tab\|<window>\|<title>` and `session-split\|<pane>\|<RIGHT or DOWN>\|<title>` |
| `setShellProfiles(list)` | Replaces the detected shell profiles and runs every `onProfilesChanged` listener |
| `shellEnvironment(jasperEnvironment)` | What the plugins' `Terminals.environment` contributors add to a new shell, merged as Jasper merges them |
| `sessionState(paneId)` | A provided session's state: `NOT_STARTED\|<status>` (a restored session whose tab has not shown), `CONNECTING\|<status>`, `RUNNING\|`, `EXITED\|<how it ended>`, `LOCAL\|` or `CLOSED\|` |
| `cancelSession`, `reconnectSession`, `typeIntoSession`, `sessionOutput`, `pumpSessions` | The user presses Cancel or Reconnect, types into a session or reads its output; notice ended sessions (`flush()` does this first) |

### Palette, files, platform and command line

| Method | Returns or does |
| --- | --- |
| `scopes()`, `scopePresentation(scopeId)`, `scopesInAll()`, `scopesInRecents()` | Registered scopes as `id\|label\|verbIds`; a scope's `HUE\|noun\|ROLE,ROLE`; the scopes the All view searches; the scopes whose spec sets `inRecents` |
| `searchScope`, `availableInScope`, `stepInScope`, `completeStep`, `executeInScope` | Call a scope's `search`, `available`, `step`, a step's completion and `execute` with the query the palette would build, on rows of the last `searchScope`; `completeStep` answers `done`, `error:<message>` or `reopen:<scope>:<row or ->:<query or ->` |
| `paletteOpens()` | Every `Palette.open` as `<window> <scope> <query or -> <row or ->` |
| `fileSources()`, `fileRoots(sourceId)`, `locateFile(sourceId, paneId)`, `connectFileSource(sourceId, rootId, windowId)` | Registered file sources and their roots; ask one where a pane is; connect to a root, running background work until it connects (at most 30 seconds) |
| `queueFileChoice(list)`, `fileChoices()` | What the next `Windows.choose` returns; every choice asked for |
| `queueFileSelection(path)`, `queuePathSelection(list)`, `duringPathSelection(action)` | What the next file, folder or save chooser returns (an empty value cancels); something to run while it is open |
| `clipboard()` | The `FakeClipboard` every plugin's `Platform` writes to |
| `browsed()`, `openedInEditor()` | Addresses plugins opened in the browser; files opened in an editor that no provided editor took |
| `edit(file, line, windowId)`, `editorPlugin()`, `declareBundled(id)` | Opens a file as Jasper's own opens do, through the editor a plugin provides; which plugin provides it; marks a plugin as bundled before it starts, so its editor replaces another's |
| `setCommandClient(path)` | What `CommandLine.client()` returns |
| `declareSubcommand(pluginId, name, summary)`, `call(name, arguments, workingDirectory[, paneId])` | Declares a subcommand as `[[subcommands]]` would, before the plugin starts; calls it as `jasper <name> …` would and returns a `FakeCommandCall` |

### Settings and appearance

| Method | Returns or does |
| --- | --- |
| `setSetting(id, key, raw)` | Sets a raw value (`String`, `Long`, `Double`, `Boolean` or `List<String>`) as a user editing the file would: before the plugin starts it is part of the file it starts with, afterwards the settings reload and listeners run |
| `savedSettings(id)`, `settingsReports(id)`, `declared(id)` | The raw values, including what the plugin saved; what the last read reported, as `key: message`; the declared keys |
| `settingsPanel(id)`, `settingsPanelView(id)`, `highlightSettingsPanel(id, query)`, `pendingSettings(id)`, `settingsPanelChanges(id)`, `applySettingsPanel(id)`, `closeSettingsPanel(id)` | Builds and drives a plugin's own Settings page as Settings would; an event a standard row handles, emitted on the page's view, then calls the page's `pendingChanged()` |
| `setKitDefault(id, key, raw)`, `lookDefaultProblems(id)` | Gives a plugin's setting the look's default, as a kit file's `[defaults."<plugin id>"]` table does, before any plugin starts: when the plugin declares the key, a raw value its kind accepts replaces the declared default, and a saved value still wins. The look defaults the plugin could not use, as `<id>: the look's default for <key> is not used: <reason>` and `<id>: the look gives a default for <key>, which the plugin does not declare` |
| `setPresentation(presentation)`, `presentation()` | Sets what every plugin's `Appearance.presentation()` returns, before any plugin starts (`IllegalStateException` once one has, even if it stopped since); the presentation set, `FakePresentations.STANDARD` until then |
| `setVariant(variant)`, `setSemanticColors(map)`, `setWorkspace(variant, palette, editor, syntax)`, `setWorkspaceOpacity(opacity)`, `setEditorSchemes(schemes)` | Change the chrome variant (which also publishes `AppEvents.THEME_CHANGED`), the chrome colours, the workspace appearance, the workspace opacity and the installed editor schemes (none at first); on the next `flush()`, a variant, chrome-colour or workspace change reaches `onChanged` handlers, and a workspace or opacity change reaches `onWorkspaceChanged` handlers (a variant change does too while the workspace follows the chrome) |
| `setTerminalTypography(value)` | Saves a terminal font, as a change in Settings › Terminal › Font & Colors would, from the thread that made the host or the event dispatch thread: on the next `flush()` every plugin's `Appearance.terminalTypography()` returns it, and then its `onTerminalTypographyChanged` handlers run on the event dispatch thread; an equal value notifies nobody. Until then the plugins read Jasper's defaults (Cousine Nerd Font Mono, 12 points, Symbols Nerd Font Mono and Apple Color Emoji as fallbacks, ligatures on, line height 1) |
| `notifications()` | The `FakeNotifications` that records what plugins posted |

## The other public fakes

| Type | Use |
| --- | --- |
| [`FakePluginContext`](../docs/sdk-reference.html#dev.jasper.sdk.testing.FakePluginContext) | The context `start` returns; its `kit()` is the plugin's `FakeKit` |
| [`FakeKit`](../docs/sdk-reference.html#dev.jasper.sdk.testing.FakeKit) | `new FakeKit(pluginId)` makes views without a host. `create(spec, handler)` returns a `FakeKitView`; `views()`, `icons()` and `failures()` show the live views, registered artwork and contained handler failures. Icon names are checked as Jasper checks them. Registered custom content gets the kit's content font, plain 13-point `SansSerif` until `contentFont(font)` replaces it and sets it on the content already registered, as a change of the user's interface font would. |
| [`FakeKitView`](../docs/sdk-reference.html#dev.jasper.sdk.testing.FakeKitView) | `spec()` is the current tree. `emit(event)` delivers user input (an edit of a read-only field is dropped); `type(fieldId, text)` types as a person would, and a read-only, hidden, disabled or busy field takes nothing; `setSecret(id, chars)` fills a secret field; `focused()`, `revealed()`, `fieldSelection(id)` and `setComposing(id, composing)` show and script focus, scrolling, selection and input-method composition. |
| [`FakeNotifications`](../docs/sdk-reference.html#dev.jasper.sdk.testing.FakeNotifications) | `lines()` lists posts as `group\|SEVERITY\|title\|content`, with `\|` and the cause's simple class name when there is one; `posts()` gives each post's spec and whether it expired; `groups()` lists `id\|name\|MODE`; `runAction(title, label)` and `click(title)` act as the user would. `new FakeNotifications().group(id)` records for a component that takes a `NotificationGroup`; its actions then run on the caller and what they throw reaches the test. |
| [`FakeClipboard`](../docs/sdk-reference.html#dev.jasper.sdk.testing.FakeClipboard) | `copies()` lists `copy\|<plugin id>\|<text>` and `secret\|<plugin id>\|<text>`; `text()` and `setText(value)` read and set it as the user would; `failWrites(true)` makes writes fail as when another program holds it; `expireSecrets()` runs the 30-second clear now |
| [`FakePresentations`](../docs/sdk-reference.html#dev.jasper.sdk.testing.FakePresentations) | Values for `setPresentation`: `STANDARD`, the default look as on Linux and Windows, and `CLASSIC`, the Enterprise look as on Linux (classic icons, labelled stripes, the native title bar, a menu row in the window, the Search Everywhere icon, IBM Plex Sans and Mono, 12-point body text, a 28-pixel toolbar) |
| [`FakeCommandCall`](../docs/sdk-reference.html#dev.jasper.sdk.testing.FakeCommandCall) | `exit()` completes with the exit code; `out()` and `err()` hold the output; `cancel()` acts as if the caller went away, and `cancelled()` says whether it did |

## Differences from Jasper

- The fake checks the UI thread only for kit views and appearance. Jasper also throws `IllegalStateException` when
  registrations, `Events.subscribe`, `Services.publish`, `CommandLine.handle`, `FileSources.register` and the
  clipboard methods are called off the UI thread.
- Background tasks run one at a time, when the test calls `runBackground()`; Jasper runs each task on its own virtual
  thread, so a plugin's tasks may run at the same time.
- Settings live in memory: `settings().file()` names `<root>/<id>/<id>.toml`, but nothing is written there. A value of
  the wrong TOML type keeps that one setting's last good value, where Jasper rejects the whole file and keeps every
  setting's last good value. `Setting.set` completes at once. `SettingKind.number` values stay `double`; Jasper keeps
  them as `float`, so 0.1 reads back as 0.10000000149011612. A look default the plugin cannot use is listed by
  `lookDefaultProblems`, where Jasper logs it as a warning, and the reason may be worded differently.
- Services: the fake does not check that a service interface is in one of the provider's exported packages. Class
  visibility is not modelled: an absent optional provider's API classes are on the test classpath, so the
  `NoClassDefFoundError` Jasper raises for them cannot happen.
- Palette queries always carry `maxResults` 5 and `macOs` true. `scopePresentation` reads a scope's `Hue.NONE` as
  `NEUTRAL`, because the fake reads no manifest `icon_hue`.
- `scopesInRecents()` lists every scope whose spec sets `inRecents`; Jasper offers a scope's rows in the empty palette
  only when it is in All too.
- `executeInScope` calls `execute` at once. Jasper rechecks `available` just before a verb runs, refreshing the list
  instead when it is false, and shows the verb's step instead when it has one.
- Provided sessions report 80 × 24, and cancellation handlers and connection closes run inline, where Jasper runs them
  on its cleanup thread.
- A failed attempt and a cancelled reconnect publish `SESSION_STATE_CHANGED` with `EXITED`; Jasper publishes `EXITED`
  only when an attached session ends.
- `FileSource.connect` always receives the window `connectFileSource` names; Jasper passes `null` when the chooser
  belongs to no Jasper window.
- `Platform.openInEditor` asks the provided editor on the caller's thread.
- Panels have no `<panel id>.toggle` action and no View › Tool Windows entry.
- The fake has no active tool window: `Panels.toggle` and `PanelHost.show()` from a panel's own toggle action only
  toggle or show it, also under `FakePresentations.CLASSIC`, where Jasper activates a showing panel that is not the
  active tool window instead of hiding it.
- A panel's window of its own exists only as its placement: a child window's `Panels.toggle` of a panel placed in one
  shows it and records no `front|` request, where Jasper brings that window to the front.
- Custom content's font is set only when it is registered and by `FakeKit.contentFont`; Jasper also sets it at every
  update of the view and every theme change, so a font a plugin sets on its registered component lasts in the fake but
  not in Jasper.
- `requestQuit()` counts a quit guard whose decision is still pending as a refusal, because the fake does not pump
  events.
- `restoreTab` puts the restored tab in a new tab at the end of the window's strip; Jasper places it where it was
  saved, in its tab and pane.
- `toolbarViews` builds a window's toolbar views for any open window, a child window too; Jasper builds them only in
  the main window, the only window with a toolbar.
- A declared subcommand with no handler fails with a message naming the plugin's id; Jasper names the plugin.

## The contract suites

Shared contract tests state the SDK's semantics once and hold the fake and Jasper to them: `PluginContractTest` (with
`ContractHarness` and `ContractCall`, in `dev.jasper.sdk.testing.contract`) for the runtime, `KitContractTest` for
declarative views and `AnchorContractTest` for anchored popovers and dialogs. They are the testkit's test fixtures, not
part of `jasper-sdk-testkit-<version>.jar`. The testkit runs them against the fake (`FakeContractTest`,
`FakeKitContractTest`, `FakeAnchorContractTest`); `jasper-app` runs them against the real runtime on the event dispatch
thread (`AppContractTest`, `HostedKitContractTest`, `ClassicHostedKitContractTest`, which repeats the kit cases under
the Enterprise look's kit, and `HostedAnchorContractTest`). When the two disagree, the implementation is wrong, not the
contract, so a behaviour the two share belongs in a contract test.
