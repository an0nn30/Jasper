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
  `SwingUtilities.invokeAndWait`. `Appearance` calls accept the thread that made the host or the event dispatch
  thread.

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
| `menu(target)` | One menu's contributed entries (`FILE`, `EDIT`, `VIEW`, `PANE`, `TAB`, `TOOLS`, `SESSION`, `TRANSFER`, `context` or `top:<menuId>`) as `item:<id>`, `---` and `submenu:<title>` lines, children indented, sections separated by `===` |
| `status()`, `progress()` | Visible status items as `id\|side\|text\|tooltip\|actionId`; visible progress states by id |
| `rail()` | Rail buttons' action ids |
| `panels()` | Registered panels as `id\|title\|ANCHOR`, with `+split` for a panel that starts in the second half and `\|<action id>` for one that names its own toggle |
| `openPanel(id, windowId)`, `panelView(id, windowId)`, `panelHeader(id, windowId)`, `closePanel(id, windowId)` | Builds a panel as a window showing it would; its mounted view; its published header; discards it as its window would |
| `windows()`, `windowView(pluginId, title)`, `windowContent(pluginId, title)` | Open plugin windows, dialogs and overlays as `id\|title\|shown` (a dialog's id is `dialog`, an overlay's `overlay`); the view or component mounted on one |
| `dialogPresentation(pluginId, title)`, `dialogSwatch(pluginId, title)`, `popoverViews(pluginId)` | How an open dialog is presented and the hue of its title square; shown popovers' views |
| `requestClose(windowId)`, `closeCommand(windowId)` | Closes a window through its closing guards; runs its Close Window command (close-command handlers newest first, then the guards) |
| `requestQuit()` | Asks the content tabs' close guards, then every plugin window's closing guards, then every quit guard, and closes nothing |
| `contentTabs()`, `contentPresentation(tab)`, `contentStatus(tab)`, `contentOwner(tab)`, `contentComponent(tab)` | Content tabs and what each shows |

### Terminals and sessions

| Method | Returns or does |
| --- | --- |
| `addTerminalWindow()`, `addTerminalTab(windowId, title)`, `addTerminalPane(tabId, info)` | Scripts the workspace; each announces itself on the terminal topics, delivered by `flush()` |
| `activateTerminalWindow(id)`, `focusTerminalPane(id)`, `closeTerminalPane(id)` | The user turns to a window, focuses a pane or closes one (the last pane takes its tab, the last tab its window) |
| `setPaneInfo(id, info)`, `setSelection(id, text)`, `setForegroundJob(id, name)` | Changes what a pane reports, without an event |
| `commandStarted`, `commandFinished`, `titleChanged`, `cwdChanged`, `remoteCwdChanged`, `sessionExited`, `bell` | Publish terminal events for a pane |
| `terminalPanes()` | Every open pane, in creation order |
| `sent(paneId)` | What plugins sent to a pane, as `write:<text>` and `paste:<text>` |
| `openRequests()` | What plugins opened or raised: `tab\|<window>\|<directory or ->` and `split\|<pane>\|<RIGHT or DOWN>\|<directory or ->`, each followed by `\|<profile id>` and `\|run:<command>` when the request named them; `front\|<window>`; for a provided session `session-tab\|<window>\|<title>` and `session-split\|<pane>\|<RIGHT or DOWN>\|<title>` |
| `setShellProfiles(list)` | Replaces the detected shell profiles and runs every `onProfilesChanged` listener |
| `shellEnvironment(jasperEnvironment)` | What the plugins' `Terminals.environment` contributors add to a new shell, merged as Jasper merges them |
| `sessionState(paneId)` | A provided session's state: `CONNECTING\|<status>`, `RUNNING\|`, `EXITED\|<how it ended>`, `LOCAL\|` or `CLOSED\|` |
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
| `settingsPanel(id)`, `settingsPanelView(id)`, `highlightSettingsPanel(id, query)`, `pendingSettings(id)`, `settingsPanelChanges(id)`, `applySettingsPanel(id)`, `closeSettingsPanel(id)` | Builds and drives a plugin's own Settings page as Settings would |
| `setVariant(variant)`, `setSemanticColors(map)`, `setWorkspace(variant, palette, editor, syntax)`, `setWorkspaceOpacity(opacity)` | Change the chrome variant (which also publishes `AppEvents.THEME_CHANGED`), the chrome colours, the workspace appearance and the workspace opacity; on the next `flush()`, a variant, chrome-colour or workspace change reaches `onChanged` handlers, and a workspace or opacity change reaches `onWorkspaceChanged` handlers (a variant change does too while the workspace follows the chrome) |
| `notifications()` | The `FakeNotifications` that records what plugins posted |

## The other public fakes

| Type | Use |
| --- | --- |
| [`FakePluginContext`](../docs/sdk-reference.html#dev.jasper.sdk.testing.FakePluginContext) | The context `start` returns; its `kit()` is the plugin's `FakeKit` |
| [`FakeKit`](../docs/sdk-reference.html#dev.jasper.sdk.testing.FakeKit) | `new FakeKit(pluginId)` makes views without a host. `create(spec, handler)` returns a `FakeKitView`; `views()`, `icons()` and `failures()` show the live views, registered artwork and contained handler failures. Icon names are checked as Jasper checks them. |
| [`FakeKitView`](../docs/sdk-reference.html#dev.jasper.sdk.testing.FakeKitView) | `spec()` is the current tree. `emit(event)` delivers user input (an edit of a read-only field is dropped); `type(fieldId, text)` types as a person would, and a read-only, hidden, disabled or busy field takes nothing; `setSecret(id, chars)` fills a secret field; `focused()`, `revealed()`, `fieldSelection(id)` and `setComposing(id, composing)` show and script focus, scrolling, selection and input-method composition. |
| [`FakeNotifications`](../docs/sdk-reference.html#dev.jasper.sdk.testing.FakeNotifications) | `lines()` lists posts as `group\|SEVERITY\|title\|content`, with `\|` and the cause's simple class name when there is one; `posts()` gives each post's spec and whether it expired; `groups()` lists `id\|name\|MODE`; `runAction(title, label)` and `click(title)` act as the user would. `new FakeNotifications().group(id)` records for a component that takes a `NotificationGroup`; its actions then run on the caller and what they throw reaches the test. |
| [`FakeClipboard`](../docs/sdk-reference.html#dev.jasper.sdk.testing.FakeClipboard) | `copies()` lists `copy\|<plugin id>\|<text>` and `secret\|<plugin id>\|<text>`; `text()` and `setText(value)` read and set it as the user would; `failWrites(true)` makes writes fail as when another program holds it; `expireSecrets()` runs the 30-second clear now |
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
  them as `float`, so 0.1 reads back as 0.10000000149011612.
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
  belongs to a plugin window or a content tab.
- `Platform.openInEditor` asks the provided editor on the caller's thread.
- Panels have no `<panel id>.toggle` action and no View › Tool Windows entry.
- `requestQuit()` counts a quit guard whose decision is still pending as a refusal, because the fake does not pump
  events.
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
